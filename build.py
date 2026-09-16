#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
记账助手 - 构建脚本（无需 Gradle / Android Studio）

流程: aapt2 compile -> aapt2 link -> javac -> d8 -> 组装 APK -> zipalign -> apksigner
用法: python build.py [clean]
"""
import os
import re
import shutil
import subprocess
import sys
import zipfile

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

ROOT = os.path.dirname(os.path.abspath(__file__))
APP = os.path.join(ROOT, "app")
BUILD = os.path.join(ROOT, "build")
DIST = os.path.join(ROOT, "dist")

SDK = os.environ.get("ANDROID_SDK_ROOT") or r"C:\Program Files (x86)\Android\android-sdk"
BT = os.path.join(SDK, "build-tools", "32.0.0")
PLATFORM = os.path.join(SDK, "platforms", "android-33")
ANDROID_JAR = os.path.join(PLATFORM, "android.jar")

JDK = os.environ.get("JAVA_HOME") or r"C:\Program Files\Microsoft\jdk-11.0.16.101-hotspot"
JAVA = os.path.join(JDK, "bin", "java.exe")
JAVAC = os.path.join(JDK, "bin", "javac.exe")
KEYTOOL = os.path.join(JDK, "bin", "keytool.exe")

AAPT2 = os.path.join(BT, "aapt2.exe")
ZIPALIGN = os.path.join(BT, "zipalign.exe")
D8_JAR = os.path.join(BT, "lib", "d8.jar")
APKSIGNER_JAR = os.path.join(BT, "lib", "apksigner.jar")

MIN_SDK = "23"
TARGET_SDK = "33"
VERSION_CODE = "7"
VERSION_NAME = "1.3.1"

KEYSTORE = os.path.join(ROOT, "debug.keystore")
KS_PASS = "android"
KS_ALIAS = "androiddebugkey"

APP_NAME = "jizhang-assistant"


def log(msg):
    print("[build] " + msg, flush=True)


def run(cmd, **kw):
    printable = " ".join('"%s"' % c if " " in c else c for c in cmd)
    log(printable)
    p = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, **kw)
    out = p.stdout.decode("utf-8", "replace").strip()
    if out:
        print(out, flush=True)
    if p.returncode != 0:
        raise SystemExit("命令失败 (exit %d): %s" % (p.returncode, printable))
    return out


def check_env():
    missing = [p for p in [ANDROID_JAR, AAPT2, D8_JAR, APKSIGNER_JAR, ZIPALIGN, JAVA, JAVAC] if not os.path.exists(p)]
    if missing:
        for m in missing:
            log("缺少: " + m)
        raise SystemExit("环境不完整，请检查 Android SDK / JDK 路径")
    log("SDK  : " + SDK)
    log("JDK  : " + JDK)


def collect_sources():
    srcs = []
    for base, _dirs, files in os.walk(os.path.join(APP, "java")):
        for f in files:
            if f.endswith(".java"):
                srcs.append(os.path.join(base, f))
    return srcs


def ensure_keystore():
    if os.path.exists(KEYSTORE):
        return
    log("生成调试签名 %s" % KEYSTORE)
    run([KEYTOOL, "-genkeypair", "-keystore", KEYSTORE,
         "-storepass", KS_PASS, "-keypass", KS_PASS,
         "-alias", KS_ALIAS, "-keyalg", "RSA", "-keysize", "2048",
         "-validity", "10000", "-dname", "CN=Android Debug,O=Android,C=US"])


def build(clean=False):
    check_env()
    if clean and os.path.isdir(BUILD):
        shutil.rmtree(BUILD)
    for d in [BUILD, DIST]:
        os.makedirs(d, exist_ok=True)
    res_zip = os.path.join(BUILD, "res.zip")
    gen_dir = os.path.join(BUILD, "gen")
    classes_dir = os.path.join(BUILD, "classes")
    dex_dir = os.path.join(BUILD, "dex")
    for d in [gen_dir, classes_dir, dex_dir]:
        os.makedirs(d, exist_ok=True)

    # 1. 编译资源
    log("== 1/6 编译资源 ==")
    run([AAPT2, "compile", "--dir", os.path.join(APP, "res"), "-o", res_zip])

    # 2. 链接资源 + 生成 R.java
    log("== 2/6 链接资源 ==")
    base_apk = os.path.join(BUILD, "base.apk")
    run([AAPT2, "link",
         "-o", base_apk,
         "-I", ANDROID_JAR,
         "--manifest", os.path.join(APP, "AndroidManifest.xml"),
         "-R", res_zip,
         "--java", gen_dir,
         "--min-sdk-version", MIN_SDK,
         "--target-sdk-version", TARGET_SDK,
         "--version-code", VERSION_CODE,
         "--version-name", VERSION_NAME,
         "--auto-add-overlay"])

    # 3. 编译 Java
    log("== 3/6 编译 Java ==")
    srcs = collect_sources()
    for base, _dirs, files in os.walk(gen_dir):
        for f in files:
            if f.endswith(".java"):
                srcs.append(os.path.join(base, f))
    if not srcs:
        raise SystemExit("没有找到 Java 源文件")
    log("源文件 %d 个" % len(srcs))
    args_file = os.path.join(BUILD, "sources.txt")
    with open(args_file, "w", encoding="utf-8") as fh:
        for s in srcs:
            fh.write(s.replace("\\", "/") + "\n")
    run([JAVAC, "-encoding", "UTF-8", "-source", "8", "-target", "8",
         "-bootclasspath", ANDROID_JAR,
         "-classpath", ANDROID_JAR,
         "-nowarn", "-d", classes_dir, "@" + args_file])

    # 4. dex
    log("== 4/6 生成 dex ==")
    class_files = []
    for base, _dirs, files in os.walk(classes_dir):
        for f in files:
            if f.endswith(".class"):
                class_files.append(os.path.join(base, f))
    run([JAVA, "-Xmx1024M", "-cp", D8_JAR, "com.android.tools.r8.D8",
         "--lib", ANDROID_JAR, "--min-api", MIN_SDK,
         "--output", dex_dir] + class_files)
    if not os.path.exists(os.path.join(dex_dir, "classes.dex")):
        raise SystemExit("未生成 classes.dex")

    # 5. 组装 APK
    log("== 5/6 组装 APK ==")
    unsigned = os.path.join(BUILD, "unsigned.apk")
    shutil.copyfile(base_apk, unsigned)
    with zipfile.ZipFile(unsigned, "a", zipfile.ZIP_DEFLATED) as z:
        z.write(os.path.join(dex_dir, "classes.dex"), "classes.dex")
    aligned = os.path.join(BUILD, "aligned.apk")
    if os.path.exists(aligned):
        os.remove(aligned)
    run([ZIPALIGN, "-f", "-p", "4", unsigned, aligned])

    # 6. 签名
    log("== 6/6 签名 APK ==")
    ensure_keystore()
    out_apk = os.path.join(DIST, APP_NAME + ".apk")
    if os.path.exists(out_apk):
        os.remove(out_apk)
    run([JAVA, "-jar", APKSIGNER_JAR, "sign",
         "--ks", KEYSTORE, "--ks-pass", "pass:" + KS_PASS,
         "--key-pass", "pass:" + KS_PASS, "--ks-key-alias", KS_ALIAS,
         "--out", out_apk, aligned])
    run([JAVA, "-jar", APKSIGNER_JAR, "verify", "--print-certs", out_apk])
    size = os.path.getsize(out_apk) / 1024.0
    log("构建成功 -> %s (%.1f KB)" % (out_apk, size))
    return out_apk


if __name__ == "__main__":
    build(clean="clean" in sys.argv)

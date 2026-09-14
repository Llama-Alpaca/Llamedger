#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""电脑端核心逻辑测试：编译 core + tests 并运行（不需要 Android 设备）。"""
import os, subprocess, sys

ROOT = os.path.dirname(os.path.abspath(__file__))
JDK = os.environ.get("JAVA_HOME") or r"C:\Program Files\Microsoft\jdk-11.0.16.101-hotspot"
JAVAC = os.path.join(JDK, "bin", "javac.exe")
JAVA = os.path.join(JDK, "bin", "java.exe")
OUT = os.path.join(ROOT, "build-test")

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass


def collect(*dirs):
    out = []
    for d in dirs:
        for base, _s, files in os.walk(os.path.join(ROOT, d)):
            for f in files:
                if f.endswith(".java"):
                    out.append(os.path.join(base, f))
    return out


# 目前没有需要额外纳入的界面文件（横滑删除已移除）
EXTRA_SOURCES = []

TEST_CLASSES = ["CoreTest"]


def main():
    os.makedirs(OUT, exist_ok=True)
    srcs = collect(os.path.join("app", "java", "com", "jizhang", "assistant", "core"), "tests")
    for e in EXTRA_SOURCES:
        p = os.path.join(ROOT, e)
        if os.path.exists(p) and p not in srcs:
            srcs.append(p)

    print("编译 %d 个源文件..." % len(srcs))
    r = subprocess.run([JAVAC, "-encoding", "UTF-8", "-nowarn", "-d", OUT] + srcs)
    if r.returncode != 0:
        return r.returncode

    rc = 0
    for cls in TEST_CLASSES:
        print()
        print("运行 %s ..." % cls)
        r = subprocess.run([JAVA, "-Dfile.encoding=UTF-8", "-cp", OUT, cls])
        if r.returncode != 0:
            rc = r.returncode
    return rc


if __name__ == "__main__":
    sys.exit(main())

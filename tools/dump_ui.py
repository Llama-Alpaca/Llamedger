# -*- coding: utf-8 -*-
"""从布局 XML 里提取真实结构（含样式解析），用于和用户对齐 UI"""
import io, os, re, sys
import xml.etree.ElementTree as ET
sys.stdout.reconfigure(encoding="utf-8")

ROOT = r"D:\Download\jizhang\app\res"
A = "{http://schemas.android.com/apk/res/android}"

# 解析 styles.xml，便于把 style="@style/Xxx" 展开成真实字号
styles = {}
st = ET.parse(os.path.join(ROOT, "values", "styles.xml")).getroot()
for stl in st.findall("style"):
    styles[stl.get("name")] = {k.get("name"): (k.text or "") for k in stl}

def attr(e, name):
    return e.get(A + name)

def resolve(e):
    """返回该 View 的关键属性（含 style 展开）"""
    out = {}
    sty = attr(e, "style")
    if sty:
        key = sty.split("/")[-1]
        out.update(styles.get(key, {}))
    for k in ("textSize", "textColor", "background", "gravity", "textStyle"):
        v = attr(e, k)
        if v: out[k] = v
    return out

def dump(e, depth=0, max_depth=6):
    if depth > max_depth: return
    tag = e.tag
    if tag == "include":
        print("  " * depth + "[include] " + e.get("layout", "").split("/")[-1])
        return
    if not isinstance(tag, str): return
    short = tag.split(".")[-1]
    vid = attr(e, "id")
    txt = attr(e, "text")
    w = attr(e, "layout_width"); h = attr(e, "layout_height")
    at = resolve(e)

    line = "  " * depth + "<" + short
    if vid: line += " id=" + vid.replace("@+id/", "")
    line += ">"
    if w or h:
        line += "  [%s x %s]" % (w or "?", h or "?")
    if txt:
        line += "  文字=\"" + txt.replace("\\n", " / ") + "\""
    if at.get("textSize"): line += "  字号=" + at["textSize"]
    if at.get("textColor"): line += "  色=" + at["textColor"].split("/")[-1]
    if at.get("textStyle"): line += "  " + at["textStyle"]
    print(line)
    for c in e:
        dump(c, depth + 1, max_depth)

targets = [
    ("activity_main.xml",       "① 首页"),
    ("activity_txn_list.xml",   "② 流水明细"),
    ("activity_add_txn.xml",    "③ 记一笔 / 编辑"),
    ("activity_stats.xml",      "④ 收支统计"),
    ("activity_settings.xml",   "⑤ 设置"),
    ("activity_guide.xml",      "⑥ 开启自动记账（权限引导）"),
    ("activity_diagnose.xml",   "⑦ 识别诊断"),
    ("item_txn.xml",            "⑧ 流水条目"),
]
for fn, title in targets:
    print("=" * 70)
    print("  " + title + "   (" + fn + ")")
    print("=" * 70)
    dump(ET.parse(os.path.join(ROOT, "layout", fn)).getroot(), 0)
    print()

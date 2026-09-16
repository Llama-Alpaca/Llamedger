# -*- coding: utf-8 -*-
"""静态检查：Java 里用到的 R.id / R.layout / R.drawable 是否与资源一致，
并核对每个界面用到的控件 id 是否真的存在于它加载的布局中。"""
import io, os, re, sys, collections

ROOT = r"D:\Download\jizhang"
APP = os.path.join(ROOT, "app")
RES = os.path.join(APP, "res")
JAVA = os.path.join(APP, "java")

# ---- 收集布局里的 id ----
layout_ids = {}
for fn in os.listdir(os.path.join(RES, "layout")):
    if not fn.endswith(".xml"): continue
    name = fn[:-4]
    txt = io.open(os.path.join(RES, "layout", fn), encoding="utf-8").read()
    layout_ids[name] = set(re.findall(r'android:id="@\+id/(\w+)"', txt))

def collect_res(prefix):
    """收集指定前缀资源目录里的资源名（含带密度/版本后缀的目录，如 drawable-mdpi、mipmap-anydpi-v26）"""
    names = set()
    for d in os.listdir(RES):
        full = os.path.join(RES, d)
        if not os.path.isdir(full):
            continue
        if d != prefix and not d.startswith(prefix + "-"):
            continue
        for fn in os.listdir(full):
            names.add(os.path.splitext(fn)[0])
    return names

drawables = collect_res("drawable")
mipmaps = collect_res("mipmap")

strings = set()
sp = os.path.join(RES, "values", "strings.xml")
if os.path.exists(sp):
    strings = set(re.findall(r'name="(\w+)"', io.open(sp, encoding="utf-8").read()))

all_ids = set()
for v in layout_ids.values(): all_ids |= v

problems = []
checked = 0

for base, _d, files in os.walk(JAVA):
    for f in files:
        if not f.endswith(".java"): continue
        path = os.path.join(base, f)
        src = io.open(path, encoding="utf-8").read()
        rel = os.path.relpath(path, ROOT)

        # 排除 android.R.*（系统框架资源，不是本应用的）
        def own(pattern):
            return set(re.findall(r'(?<!android\.)(?<![\w.])' + pattern, src))
        used_layouts = own(r'R\.layout\.(\w+)')
        used_draw = own(r'R\.drawable\.(\w+)')
        used_str = own(r'R\.string\.(\w+)')
        used_ids = own(r'R\.id\.(\w+)')

        for l in used_layouts:
            if l not in layout_ids:
                problems.append("%s: R.layout.%s 不存在" % (rel, l))
        for d in used_draw:
            if d not in drawables:
                problems.append("%s: R.drawable.%s 不存在" % (rel, d))
        for s in used_str:
            if s not in strings:
                problems.append("%s: R.string.%s 不存在" % (rel, s))

        # 该文件可能涉及的布局 = 它引用的布局 + 它 inflate 的 item 布局
        pool = set()
        for l in used_layouts:
            pool |= layout_ids.get(l, set())
        # 从其它布局里借 id 的情况（如 adapter 渲染 item）视为可接受：
        missing = used_ids - pool
        # 只报「既不在本文件布局、也不在任何布局」的
        for m in sorted(missing):
            if m not in all_ids:
                problems.append("%s: R.id.%s 在任何布局中都不存在" % (rel, m))
        checked += 1

# ---- 自定义 View：布局里写的类名必须真实存在，否则运行时才崩 ----
custom_views = set()
for fn in os.listdir(os.path.join(RES, "layout")):
    if not fn.endswith(".xml"): continue
    txt = io.open(os.path.join(RES, "layout", fn), encoding="utf-8").read()
    for tag in re.findall(r'<([A-Za-z_][\w.]*\.[A-Za-z_]\w*)', txt):
        custom_views.add(tag)

for fqcn in sorted(custom_views):
    cls = fqcn.split(".")[-1]
    pkg = ".".join(fqcn.split(".")[:-1])
    rel = os.path.join(*pkg.split("."))
    path = os.path.join(JAVA, rel, cls + ".java")
    if not os.path.exists(path):
        problems.append("布局引用了自定义 View %s，但源文件不存在: %s" % (fqcn, path))
    else:
        src = io.open(path, encoding="utf-8").read()
        if ("package " + pkg + ";") not in src:
            problems.append("%s 的 package 声明与布局中的类名不一致" % fqcn)
        if ("class " + cls) not in src:
            problems.append("%s 中没有找到 class %s" % (fqcn, cls))

print("检查了 %d 个 Java 文件，%d 个布局，%d 个自定义 View"
      % (checked, len(layout_ids), len(custom_views)))
print()
# ---- 清单里的图标引用必须能解析到 ----
man = io.open(os.path.join(APP, "AndroidManifest.xml"), encoding="utf-8").read()
for kind, pool in (("mipmap", mipmaps), ("drawable", drawables)):
    for ref in re.findall(r'@%s/(\w+)' % kind, man):
        if ref not in pool:
            problems.append("清单引用了 @%s/%s，但对应资源不存在" % (kind, ref))

print("图标资源：%d 个 drawable，%d 个 mipmap" % (len(drawables), len(mipmaps)))

# ---- 交互入口守卫：删除这类高风险操作，必须有多条可达路径 ----
UI = os.path.join(JAVA, "com", "jizhang", "assistant", "ui")

WIRING = [
    ("ui/TxnListActivity.java", "setOnItemClickListener",
     "流水页缺少「点按编辑」监听"),
    ("ui/TxnListActivity.java", "setOnItemLongClickListener",
     "流水页缺少「长按操作菜单」监听"),
    ("ui/AddTxnActivity.java", "R.id.delete",
     "编辑页没有删除入口"),
    ("ui/TxnActions.java", "confirmDelete",
     "缺少统一的删除确认逻辑"),
]

# 反向断言：这些是「刻意不做」的事，被加回来同样算问题
FORBIDDEN = [
    ("ui/MainActivity.java", "setOnLongClickListener",
     "首页「最近流水」应当保持只读（按设计不加长按）"),
    ("ui/TxnActions.java", 'actions.add("编辑")',
     "长按菜单不应再出现「编辑」（点按条目即可编辑）"),
    ("ui/TxnListActivity.java", "SwipeItemLayout",
     "横滑删除已停用，不应再出现引用"),
]

for rel, needle, msg in WIRING:
    path = os.path.join(JAVA, "com", "jizhang", "assistant", rel)
    if not os.path.exists(path):
        problems.append(msg + "（文件不存在: %s）" % rel)
        continue
    if needle not in io.open(path, encoding="utf-8").read():
        problems.append(msg + "（%s 中找不到 %s）" % (rel, needle))

for rel, needle, msg in FORBIDDEN:
    path = os.path.join(JAVA, "com", "jizhang", "assistant", rel)
    if not os.path.exists(path):
        continue
    if needle in io.open(path, encoding="utf-8").read():
        problems.append(msg + "（%s 中出现了 %s）" % (rel, needle))

print("交互入口守卫：%d 项必须存在，%d 项必须不存在"
      % (len(WIRING), len(FORBIDDEN)))

if problems:
    print("发现问题：")
    for p in problems: print("  [!] " + p)
    sys.exit(1)
else:
    print("资源引用检查通过：所有 R.id / R.layout / R.drawable / R.string 均存在")

# ---- 逐界面核对 id 是否在同一布局内 ----
print()
print("逐界面控件核对：")
scenes = {
    "ui/MainActivity.java": ["activity_main", "item_txn"],
    "ui/TxnListActivity.java": ["activity_txn_list", "item_txn"],
    "ui/AddTxnActivity.java": ["activity_add_txn"],
    "ui/StatsActivity.java": ["activity_stats", "item_stat"],
    "ui/SettingsActivity.java": ["activity_settings"],
    "ui/PermissionGuideActivity.java": ["activity_guide"],
    "ui/DiagnoseActivity.java": ["activity_diagnose", "item_source"],
}
bad = 0
for rel, layouts in scenes.items():
    src = io.open(os.path.join(JAVA, "com/jizhang/assistant", rel), encoding="utf-8").read()
    used = set(re.findall(r'(?<!android\.)(?<![\w.])R\.id\.(\w+)', src))
    pool = set()
    for l in layouts: pool |= layout_ids.get(l, set())
    missing = sorted(used - pool)
    if missing:
        bad += 1
        print("  [!] %s 用到但布局中没有: %s" % (rel, ", ".join(missing)))
    else:
        print("  [OK] %s (%d 个控件)" % (rel, len(used)))
if bad:
    sys.exit(1)
print()
print("全部核对通过")

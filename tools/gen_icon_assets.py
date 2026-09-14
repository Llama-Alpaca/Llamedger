# -*- coding: utf-8 -*-
"""从 D 方案生成完整的 Android 图标资源

产出：
  mipmap-*/ic_launcher.png            传统图标（API 23-25 用），自带圆角方底
  mipmap-*/ic_launcher_round.png      圆形版（部分启动器会取这个）
  mipmap-*/ic_launcher_foreground.png 自适应图标前景（透明底，只放羊驼）
  mipmap-anydpi-v26/ic_launcher.xml   自适应图标声明
  drawable/ic_launcher_background.xml 自适应图标背景（纯色）
  drawable-*/ic_stat_jz.png           状态栏通知图标（白色剪影）
"""
import sys, os
sys.path.insert(0, r"D:\Download\jizhang\tools")
from icon_renderer import Canvas, hx
import make_icons as M

ROOT = r"D:\Download\jizhang"
RES = os.path.join(ROOT, "app", "res")

# 密度名 -> 倍数
DENS = [("mdpi", 1.0), ("hdpi", 1.5), ("xhdpi", 2.0),
        ("xxhdpi", 3.0), ("xxxhdpi", 4.0)]

def ensure(*parts):
    p = os.path.join(RES, *parts)
    os.makedirs(p, exist_ok=True)
    return p

# ---------- 1) 画一次源图，后续复用 ----------
print("绘制源图 (768px)...")
legacy_src = M.icon_d()                       # 带圆角方底
alpaca_src = Canvas(768); M.alpaca_d(alpaca_src)          # 透明底，只有羊驼
silh_src   = Canvas(768); M.alpaca_d_silhouette(silh_src, (255, 255, 255, 255))

# ---------- 2) 传统图标 ----------
print("生成传统图标 48/72/96/144/192 ...")
for name, f in DENS:
    d = ensure("mipmap-" + name)
    px = int(48 * f)
    legacy_src.downsample(px).save_png(os.path.join(d, "ic_launcher.png"))
    legacy_src.downsample(px).save_png(os.path.join(d, "ic_launcher_round.png"))

# ---------- 3) 自适应图标前景 ----------
# 自适应图标视口 108dp，安全区是中间 66dp。
# 让羊驼头落在中心、耳朵刚好在安全区内、脖子自然溢出下边缘。
K, OX, OY = 0.155, -7.7, -0.1        # 以 108 视口为单位
print("生成自适应前景 108/162/216/324/432 ...")
for name, f in DENS:
    d = ensure("mipmap-" + name)
    S = int(108 * f)
    c = Canvas(S)
    c.blit_into(alpaca_src, OX * f, OY * f, 768 * K * f, 768 * K * f)
    c.save_png(os.path.join(d, "ic_launcher_foreground.png"))

# ---------- 4) 状态栏通知图标（只取头部，白色剪影）----------
print("生成通知图标 24/36/48/72/96 ...")
SRC_X, SRC_Y, SRC_W, SRC_H = 206, 104, 328, 366
for name, f in DENS:
    d = ensure("drawable-" + name)
    S = int(24 * f)
    sc = S / float(SRC_H)
    x0 = -(SRC_X) * sc + (S - SRC_W * sc) / 2.0
    y0 = -(SRC_Y) * sc
    c = Canvas(S)
    c.blit_into(silh_src, x0, y0, 768 * sc, 768 * sc)
    c.save_png(os.path.join(d, "ic_stat_jz.png"))

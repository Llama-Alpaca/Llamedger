# -*- coding: utf-8 -*-
"""模拟自适应图标在手机启动器里的显示效果（圆形/圆角方形遮罩）"""
import sys, os
sys.path.insert(0, r"D:\Download\jizhang\tools")
from icon_renderer import Canvas, hx
import make_icons as M

OUT = r"D:\Download\jizhang\preview\icons"
os.makedirs(OUT, exist_ok=True)

S = 432
K, OX, OY = 0.155, -7.7, -0.1    # 与 gen_icon_assets.py 保持一致

# 背景 + 前景，组成完整的 108 视口内容
alpaca = Canvas(768)
M.alpaca_d(alpaca)

def compose():
    c = Canvas(S)
    c.round_rect(-10, -10, S + 20, S + 20, 0, hx("2E7D5B"))   # 纯色背景铺满
    c.blit_into(alpaca, OX * (S/108.0), OY * (S/108.0),
                768 * K * (S/108.0), 768 * K * (S/108.0))
    return c

def apply_mask(c, shape):
    """遮罩区域 = 居中 72/108 的范围内（这是系统保证可见的安全区）"""
    n = c.n
    side = n * 72.0 / 108.0
    cx = cy = n / 2.0
    r = side / 2.0
    for y in range(n):
        v = (y + 0.5 - cy) / r
        base = y * n
        for x in range(n):
            u = (x + 0.5 - cx) / r
            if shape == "circle":
                inside = (u*u + v*v) <= 1.0
            elif shape == "squircle":
                inside = (abs(u)**4.0 + abs(v)**4.0) <= 1.0
            else:
                inside = max(abs(u), abs(v)) <= 1.0
            if not inside:
                c.px[(base + x) * 4 + 3] = 0

for shape in ("circle", "squircle", "square"):
    c = compose()
    apply_mask(c, shape)
    p = os.path.join(OUT, "sim_" + shape + ".png")
    c.downsample(432).save_png(p)
    print("生成", os.path.basename(p), os.path.getsize(p), "bytes")

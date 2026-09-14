# -*- coding: utf-8 -*-
"""羊驼图标候选方案（纯 Python 光栅化绘制）"""
import sys, math, os
sys.path.insert(0, r"D:\Download\jizhang\tools")
from icon_renderer import Canvas, hx, hxa
from icon_preview import ascii_preview

S = 768
OUT = r"D:\Download\jizhang\preview\icons"
os.makedirs(OUT, exist_ok=True)

GREEN       = hx("2E7D5B")
GREEN_DARK  = hx("1F5C42")
GREEN_LIGHT = hx("E8F3EE")
GREEN_MID   = hx("3E8C68")
CREAM       = hx("F7E9D3")
WOOL        = hx("FFF8EC")
NEARWHITE   = hx("FFFDF8")
WHITE       = hx("FFFFFF")
DARK        = hx("33302E")
PINK        = hx("F3A9A9")
GOLD        = hx("E8B23A")

# ---------------------------------------------------------------- 部件

def thick_line(c, x1, y1, x2, y2, w, color):
    dx, dy = x2 - x1, y2 - y1
    L = math.hypot(dx, dy) or 1.0
    nx, ny = -dy / L * w / 2.0, dx / L * w / 2.0
    c.polygon([(x1 + nx, y1 + ny), (x2 + nx, y2 + ny),
               (x2 - nx, y2 - ny), (x1 - nx, y1 - ny)], color)

def yuan_coin(c, cx, cy, r, base=hx("C9922A"), face=GOLD, ink=hx("8A6115")):
    c.circle(cx, cy, r, base)
    c.circle(cx, cy, r - 9, face)
    c.circle(cx, cy, r - 21, ink)
    w = r * 0.14
    thick_line(c, cx - r*0.36, cy - r*0.44, cx, cy - r*0.06, w, face)
    thick_line(c, cx + r*0.36, cy - r*0.44, cx, cy - r*0.06, w, face)
    thick_line(c, cx, cy - r*0.08, cx, cy + r*0.46, w, face)
    thick_line(c, cx - r*0.32, cy + r*0.06, cx + r*0.32, cy + r*0.06, w*0.85, face)
    thick_line(c, cx - r*0.32, cy + r*0.28, cx + r*0.32, cy + r*0.28, w*0.85, face)

def alpaca_face(c, cx=384, cy=448, scale=1.0, wool=WOOL, head=CREAM,
                eye=DARK, eye_r=29, nose=DARK, muzzle=NEARWHITE,
                ear_len=1.0, inner_ear=PINK, blush=True, detail=True):
    """正面羊驼头。坐标以 768 画布为基准，整体按 scale 缩放并平移到 (cx,cy)"""
    def E(x, y, rx, ry, col, rot=0.0):
        c.ellipse(cx + (x-384)*scale, cy + (y-448)*scale,
                  rx*scale, ry*scale, col, rot)

    E(286, 220, 42, 122*ear_len, wool, -0.30)
    E(482, 220, 42, 122*ear_len, wool,  0.30)
    if detail and inner_ear:
        E(290, 236, 18, 76*ear_len, inner_ear, -0.30)
        E(478, 236, 18, 76*ear_len, inner_ear,  0.30)

    for (x, y, r) in [(300,320,68),(384,288,82),(468,320,68),
                      (342,332,64),(426,332,64),(384,356,70)]:
        E(x, y, r, r, wool)

    E(384, 452, 170, 166, head)
    if not detail:
        return

    E(316, 408, eye_r, eye_r, eye)
    E(452, 408, eye_r, eye_r, eye)
    E(325, 398, eye_r*0.34, eye_r*0.34, WHITE)
    E(461, 398, eye_r*0.34, eye_r*0.34, WHITE)

    E(384, 526, 100, 66, muzzle)
    E(384, 500, 39, 27, nose)
    rx, ry = 27*scale, 7.5*scale
    c.round_rect(cx - rx, cy + (542-448)*scale, rx*2, ry*2, ry, nose)

    if blush:
        E(280, 486, 28, 28, hxa("F3A9A9", 115))
        E(488, 486, 28, 28, hxa("F3A9A9", 115))

def rounded_bg(color, r=170):
    c = Canvas(S)
    c.round_rect(0, 0, S, S, r, color)
    return c

# ---------------------------------------------------------------- 四个方案

def icon_a():
    """A 经典款：深绿底 + 米白羊驼，五官最清晰"""
    c = rounded_bg(GREEN)
    c.circle(384, 406, 256, GREEN_MID)
    alpaca_face(c)
    return c

def icon_b():
    """B 薄荷款：浅底 + 薄荷绿羊驼，清爽柔和"""
    c = rounded_bg(hx("D8EDE2"))
    c.circle(384, 406, 250, hx("C3E2D5"))
    alpaca_face(c, wool=hx("6FC5A2"), head=hx("5BB894"),
                eye=hx("14322A"), nose=hx("14322A"),
                muzzle=hx("EAF7F1"), inner_ear=hxa("F3A9A9", 215))
    return c

def icon_c():
    """C 金币款：深绿底 + 羊驼 + ¥ 金币，记账感最强"""
    c = rounded_bg(GREEN_DARK)
    c.circle(352, 378, 244, hx("2A6E51"))
    alpaca_face(c, cx=340, cy=418, scale=0.92)
    yuan_coin(c, 570, 570, 108)
    return c

def alpaca_d(c):
    """D 方案的羊驼本体（不画背景），供各种尺寸复用"""
    # 细长脖子
    c.round_rect(366, 396, 104, 400, 52, CREAM)
    for (x, y, r) in [(470, 512, 50), (474, 600, 46), (462, 676, 42)]:
        c.circle(x, y, r, WOOL)

    # 头
    c.ellipse(398, 336, 152, 124, CREAM)
    # 耳朵
    c.ellipse(474, 208, 35, 94, CREAM, rot=0.20)
    c.ellipse(476, 226, 15, 58, PINK, rot=0.20)
    # 头顶羊毛
    for (x, y, r) in [(352, 252, 58), (430, 240, 54), (296, 270, 46)]:
        c.circle(x, y, r, WOOL)

    # 口鼻朝左
    c.ellipse(272, 360, 64, 50, NEARWHITE)
    c.ellipse(238, 344, 20, 16, DARK)

    # 眼睛
    c.circle(388, 314, 26, DARK)
    c.circle(397, 305, 9, WHITE)
    c.circle(338, 390, 24, hxa("F3A9A9", 110))


def alpaca_d_silhouette(c, color):
    """D 方案的纯色剪影（用于状态栏通知图标）"""
    c.round_rect(366, 396, 104, 400, 52, color)
    for (x, y, r) in [(470, 512, 50), (474, 600, 46), (462, 676, 42)]:
        c.circle(x, y, r, color)
    c.ellipse(398, 336, 152, 124, color)
    c.ellipse(474, 208, 35, 94, color, rot=0.20)
    for (x, y, r) in [(352, 252, 58), (430, 240, 54), (296, 270, 46)]:
        c.circle(x, y, r, color)
    c.ellipse(272, 360, 64, 50, color)


def icon_d():
    """D 侧脸款（旧版：自带圆角方底，用于 API 23-25 的传统图标）"""
    c = rounded_bg(GREEN)
    c.circle(392, 400, 250, GREEN_MID)
    alpaca_d(c)
    return c

# ---------------------------------------------------------------- 渲染

def render(name, fn, cols=54):
    c = fn()
    c.downsample(S).save_png(os.path.join(OUT, name + ".png"))
    c.downsample(192).save_png(os.path.join(OUT, name + "_192.png"))
    print("=" * 58)
    print("  " + name)
    print("=" * 58)
    print(ascii_preview(c, cols))
    print()

if __name__ == "__main__":
    # 文件名用英文，方便在文档和对话里引用
    plans = [
        ("A_classic", icon_a),
        ("B_mint",    icon_b),
        ("C_coin",    icon_c),
        ("D_profile", icon_d),
    ]
    want = sys.argv[1:]
    for name, fn in plans:
        if want and name[0] not in want:
            continue
        render(name, fn)

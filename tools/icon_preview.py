# -*- coding: utf-8 -*-
"""把渲染结果转成字符画，用于在没有图形界面的情况下自检构图"""
import sys, math
sys.path.insert(0, r"D:\Download\jizhang\tools")
from icon_renderer import Canvas

PALETTE = [
    (" ", None),
    (".", (0x2E, 0x7D, 0x5B)),   # 主绿背景
    (",", (0x1F, 0x5C, 0x42)),   # 深绿
    ("~", (0x4E, 0x9B, 0x78)),   # 浅绿
    ("#", (0xFF, 0xF6, 0xE9)),   # 米白（羊驼毛）
    ("=", (0xE8, 0xDD, 0xC8)),   # 暗一档的米色
    ("@", (0x33, 0x33, 0x33)),   # 深色（眼睛/鼻子）
    ("o", (0xF3, 0xA9, 0xA9)),   # 粉（腮红/内耳）
    ("+", (0xD9, 0xA8, 0x7C)),   # 驼色
    ("*", (0xE8, 0xB2, 0x3A)),   # 金色（硬币）
]

def ascii_preview(canvas, cols=58):
    img = canvas.downsample(cols)
    n = img.n
    lines = []
    for y in range(n):
        row = []
        for x in range(n):
            i = (y * n + x) * 4
            a = img.px[i+3]
            if a < 110:
                row.append(" "); continue
            r, g, b = img.px[i], img.px[i+1], img.px[i+2]
            best, bestd = "?", 1e18
            for ch, col in PALETTE:
                if col is None: continue
                d = (r-col[0])**2 + (g-col[1])**2 + (b-col[2])**2
                if d < bestd: bestd, best = d, ch
            row.append(best)
        lines.append("".join(row))
    return "\n".join(lines)

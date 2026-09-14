# -*- coding: utf-8 -*-
"""纯 Python 图标绘制器：自带矢量光栅化 + PNG 编码（不依赖任何第三方库）

做法：在 3 倍尺寸上按几何方程填充形状，再按面积平均降采样，
      从而得到平滑的抗锯齿边缘。
"""
import math, struct, zlib, os

class Canvas:
    def __init__(self, size):
        self.n = size
        self.px = bytearray(size * size * 4)   # RGBA

    # ---------- 基础 ----------
    def blend(self, x, y, color):
        r, g, b, a = color
        if a >= 255:
            i = (y * self.n + x) * 4
            self.px[i] = r; self.px[i+1] = g; self.px[i+2] = b; self.px[i+3] = 255
            return
        if a <= 0: return
        i = (y * self.n + x) * 4
        da = self.px[i+3]
        na = a + da * (255 - a) // 255
        if na == 0: return
        self.px[i]   = (r * a + self.px[i]   * da * (255 - a) // 255) // na
        self.px[i+1] = (g * a + self.px[i+1] * da * (255 - a) // 255) // na
        self.px[i+2] = (b * a + self.px[i+2] * da * (255 - a) // 255) // na
        self.px[i+3] = na

    def _box(self, cx, cy, rx, ry, rot, scale):
        m = max(rx, ry) + 2
        x0 = max(0, int(cx - m)); x1 = min(self.n, int(cx + m) + 1)
        y0 = max(0, int(cy - m)); y1 = min(self.n, int(cy + m) + 1)
        return x0, x1, y0, y1

    # ---------- 形状 ----------
    def ellipse(self, cx, cy, rx, ry, color, rot=0.0):
        if rx <= 0 or ry <= 0: return
        x0, x1, y0, y1 = self._box(cx, cy, rx, ry, rot, 1)
        cr, sr = math.cos(-rot), math.sin(-rot)
        irx, iry = 1.0 / rx, 1.0 / ry
        for y in range(y0, y1):
            dy0 = y + 0.5 - cy
            for x in range(x0, x1):
                dx0 = x + 0.5 - cx
                dx = dx0 * cr - dy0 * sr
                dy = dx0 * sr + dy0 * cr
                if (dx * irx) ** 2 + (dy * iry) ** 2 <= 1.0:
                    self.blend(x, y, color)

    def circle(self, cx, cy, r, color):
        self.ellipse(cx, cy, r, r, color)

    def round_rect(self, x, y, w, h, r, color):
        r = min(r, w / 2.0, h / 2.0)
        self.rect(x + r, y, w - 2 * r, h, color)
        self.rect(x, y + r, w, h - 2 * r, color)
        self.circle(x + r, y + r, r, color)
        self.circle(x + w - r, y + r, r, color)
        self.circle(x + r, y + h - r, r, color)
        self.circle(x + w - r, y + h - r, r, color)

    def rect(self, x, y, w, h, color):
        x0 = max(0, int(x)); x1 = min(self.n, int(x + w) + 1)
        y0 = max(0, int(y)); y1 = min(self.n, int(y + h) + 1)
        for yy in range(y0, y1):
            for xx in range(x0, x1):
                self.blend(xx, yy, color)

    def polygon(self, pts, color):
        if len(pts) < 3: return
        ys = [p[1] for p in pts]; xs = [p[0] for p in pts]
        y0 = max(0, int(min(ys))); y1 = min(self.n, int(max(ys)) + 1)
        x0 = max(0, int(min(xs))); x1 = min(self.n, int(max(xs)) + 1)
        n = len(pts)
        for y in range(y0, y1):
            py = y + 0.5
            for x in range(x0, x1):
                px = x + 0.5
                inside = False
                j = n - 1
                for i in range(n):
                    xi, yi = pts[i]; xj, yj = pts[j]
                    if (yi > py) != (yj > py):
                        xint = (xj - xi) * (py - yi) / (yj - yi) + xi
                        if px < xint: inside = not inside
                    j = i
                if inside: self.blend(x, y, color)

    # ---------- 输出 ----------
    def downsample(self, target):
        k = self.n // target
        out = Canvas(target)
        area = k * k
        for y in range(target):
            for x in range(target):
                r = g = b = a = 0
                for dy in range(k):
                    base = ((y * k + dy) * self.n + x * k) * 4
                    for dx in range(k):
                        i = base + dx * 4
                        al = self.px[i+3]
                        r += self.px[i] * al; g += self.px[i+1] * al; b += self.px[i+2] * al
                        a += al
                o = (y * target + x) * 4
                if a > 0:
                    out.px[o] = r // a; out.px[o+1] = g // a; out.px[o+2] = b // a
                out.px[o+3] = a // area
        return out

    def save_png(self, path):
        n = self.n
        raw = bytearray()
        for y in range(n):
            raw.append(0)
            raw += self.px[y * n * 4:(y + 1) * n * 4]
        def chunk(tag, data):
            return (struct.pack(">I", len(data)) + tag + data
                    + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))
        png = b"\x89PNG\r\n\x1a\n"
        png += chunk(b"IHDR", struct.pack(">IIBBBBB", n, n, 8, 6, 0, 0, 0))
        png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        png += chunk(b"IEND", b"")
        with open(path, "wb") as f:
            f.write(png)

def hx(s):
    s = s.lstrip("#")
    return (int(s[0:2],16), int(s[2:4],16), int(s[4:6],16), 255)

def hxa(s, a):
    c = hx(s); return (c[0], c[1], c[2], a)

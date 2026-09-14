import sys, struct, zlib
sys.stdout.reconfigure(encoding="utf-8")

def read_png(path):
    d = open(path, "rb").read()
    pos = 8; w = h = None; idat = b""
    while pos < len(d):
        ln = struct.unpack(">I", d[pos:pos+4])[0]
        tag = d[pos+4:pos+8]; data = d[pos+8:pos+8+ln]
        if tag == b"IHDR": w, h = struct.unpack(">II", data[:8])
        elif tag == b"IDAT": idat += data
        pos += 12 + ln
    raw = zlib.decompress(idat)
    stride = w * 4
    px = bytearray(w * h * 4)
    for y in range(h):
        px[y*stride:(y+1)*stride] = raw[y*(stride+1)+1:(y+1)*(stride+1)]
    return w, h, px

PAL = [(" ", None), (".", (46,125,91)), (",", (31,92,66)), ("~", (62,140,104)),
       ("#", (255,246,233)), ("=", (232,221,200)), ("@", (51,48,46)),
       ("o", (243,169,169))]

def show(path, cols=44):
    w, h, px = read_png(path)
    k = max(1, w // cols)
    print("--- " + path.split("\\")[-1] + " ---")
    for yy in range(cols):
        row = []
        for xx in range(cols):
            r = g = b = a = 0
            for dy in range(k):
                base = ((yy*k+dy)*w + xx*k)*4
                for dx in range(k):
                    i = base + dx*4
                    al = px[i+3]
                    r += px[i]*al; g += px[i+1]*al; b += px[i+2]*al; a += al
            if a == 0:
                row.append(" "); continue
            r, g, b = r//a, g//a, b//a
            best, bd = "?", 1e18
            for ch, col in PAL:
                if col is None: continue
                dd = (r-col[0])**2 + (g-col[1])**2 + (b-col[2])**2
                if dd < bd: bd, best = dd, ch
            row.append(best)
        print("".join(row))
    print()

show(r"D:\Download\jizhang\preview\icons\sim_circle.png")

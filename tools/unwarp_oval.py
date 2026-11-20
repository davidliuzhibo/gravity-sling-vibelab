"""Stretches an oval world map (as Seedream sometimes draws) into a 2:1 equirectangular texture.

Usage: python3 unwarp_oval.py in.rgb out.rgb W H   (raw rgb24, convert with ffmpeg; see README)
The background color is taken from the top-left corner pixel.
"""
import sys
W, H = int(sys.argv[3]), int(sys.argv[4])
src = open(sys.argv[1], 'rb').read()
def px(x, y):
    i = (y * W + x) * 3
    return src[i], src[i+1], src[i+2]
BG = None
def bg(p): return abs(p[0]-BG[0]) + abs(p[1]-BG[1]) + abs(p[2]-BG[2]) < 45
BG = px(0, 0)
# Find the oval's horizontal extent on each row and its vertical extent.
rows = []
for y in range(H):
    l = next((x for x in range(W) if not bg(px(x, y))), None)
    r = next((x for x in range(W-1, -1, -1) if not bg(px(x, y))), None)
    rows.append((l, r))
valid = [y for y, (l, r) in enumerate(rows) if l is not None and r - l > W * 0.05]
top, bot = valid[0], valid[-1]
# Smooth the extents: fit the half-width of each row from the widest row center (ellipse-like).
cx = sum((rows[y][0] + rows[y][1]) / 2 for y in valid) / len(valid)
out = bytearray(W * H * 3)
for y in range(H):
    sy = top + (bot - top) * y / (H - 1)
    syi = min(max(int(round(sy)), top), bot)
    l, r = rows[syi]
    if l is None or r - l < 8:
        # near the poles: use the nearest valid row
        syi = min(valid, key=lambda v: abs(v - syi)); l, r = rows[syi]
    l += 2; r -= 2  # stay off the anti-aliased rim
    for x in range(W):
        sx = l + (r - l) * x / (W - 1)
        p = px(int(sx), syi)
        i = (y * W + x) * 3
        out[i:i+3] = bytes(p)
open(sys.argv[2], 'wb').write(out)
print('oval rows', top, bot, 'center', round(cx))

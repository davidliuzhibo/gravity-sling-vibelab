"""Derives a tangent-space normal map from a planet texture's brightness (crater rims are bright).

blender -b --factory-startup --python normal_from_height.py -- in.jpg out.png STRENGTH [SIZE]
Good enough for lunar and Martian relief seen from arm's length; the result is a guess at
height, not real elevation data. The sphere's u runs around the equator, so x wraps.
"""
import sys
import bpy
import numpy as np

src, out, strength = sys.argv[sys.argv.index("--") + 1:][:3]
size = int(sys.argv[sys.argv.index("--") + 4]) if len(sys.argv) > sys.argv.index("--") + 4 else 1024
strength = float(strength)

im = bpy.data.images.load(src)
im.scale(size, size // 2)
w, h = im.size
px = np.array(im.pixels[:], dtype=np.float32).reshape(h, w, 4)
lum = px[..., :3] @ np.array([0.299, 0.587, 0.114], dtype=np.float32)

# Remove the large-scale albedo (maria, dust) so only local relief remains.
def blur(a, r):
    k = np.ones(2 * r + 1, dtype=np.float32) / (2 * r + 1)
    a = np.apply_along_axis(lambda row: np.convolve(np.concatenate([row[-r:], row, row[:r]]), k, "valid"), 1, a)
    pad = np.pad(a, ((r, r), (0, 0)), mode="edge")
    return np.apply_along_axis(lambda col: np.convolve(col, k, "valid"), 0, pad)

height = lum - blur(lum, max(4, w // 64))
height = blur(height, 1)

dx = (np.roll(height, -1, axis=1) - np.roll(height, 1, axis=1)) * strength
dy = (np.vstack([height[1:], height[-1:]]) - np.vstack([height[:1], height[:-1]])) * strength
n = np.dstack([-dx, -dy, np.ones_like(dx)])
n /= np.linalg.norm(n, axis=2, keepdims=True)

o = np.ones((h, w, 4), dtype=np.float32)
o[..., :3] = n * 0.5 + 0.5
img = bpy.data.images.new("n", w, h)
img.colorspace_settings.name = "Non-Color"
img.pixels = o.ravel()
img.filepath_raw = out
img.file_format = "PNG"
img.save()
print("wrote", out, w, h)

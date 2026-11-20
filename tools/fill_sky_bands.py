"""Fills the black bands at the top and bottom of a panorama with stars from another one, warmed to match.
uv run -q --with pillow --with numpy python -I tools/fill_sky_bands.py <panorama> <stars panorama> <out>"""
import sys
import numpy as np
from PIL import Image
a = np.asarray(Image.open(sys.argv[1]).convert("RGB"), dtype=np.float32)
b = np.asarray(Image.open(sys.argv[2]).convert("RGB").resize((a.shape[1], a.shape[0]), Image.LANCZOS), dtype=np.float32)
rows = a.mean(axis=(1, 2))
lit = np.where(rows > 6)[0]
top, bottom = int(lit[0]), int(lit[-1])
print("bands: rows 0 to", top, "and", bottom, "to", a.shape[0])
# The stars of the other sky, grey then warm, kept dim away from its bright band.
g = b.mean(axis=2, keepdims=True)
stars = g * np.array([1.0, 0.86, 0.68], dtype=np.float32) * 1.25
h = a.shape[0]
y = np.arange(h, dtype=np.float32)
feather = 150.0
w = np.clip((top + feather - y) / feather, 0, 1) + np.clip((y - bottom + feather) / feather, 0, 1)
w = w[:, None, None] ** 1.2
out = a * (1 - w) + stars * w
Image.fromarray(np.clip(out, 0, 255).astype(np.uint8)).save(sys.argv[3], quality=86)
print("mean by row band", [round(float(out[i:i + 256].mean()), 1) for i in range(0, h, 256)])

"""Makes a generated 2:1 panorama wrap around without a visible seam.

blender -b --factory-startup --python wrap_seam.py -- in.jpg out.jpg [BAND]
Image generators don't make the left and right edges meet. The last BAND columns (default 1/32
of the width) are cross-faded into the first ones and then dropped, so the new right edge runs
straight into the left edge; the result is stretched back to the original width (about 3%).
"""
import sys
import bpy
import numpy as np

args = sys.argv[sys.argv.index("--") + 1:]
src, out = args[:2]
im = bpy.data.images.load(src)
w, h = im.size
k = int(args[2]) if len(args) > 2 else w // 32
px = np.array(im.pixels[:], dtype=np.float32).reshape(h, w, 4)
t = (np.arange(k, dtype=np.float32) / k)[None, :, None]
px[:, :k] = px[:, :k] * t + px[:, w - k:] * (1 - t)
o = bpy.data.images.new("o", w - k, h)
o.pixels = np.ascontiguousarray(px[:, :w - k]).ravel()
o.scale(w, h)
o.filepath_raw = out
o.file_format = "JPEG"
o.save()
print("wrote", out, w, h, "band", k)

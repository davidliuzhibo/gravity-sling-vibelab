"""Hides the pole pinch of a 2:1 planet texture by smoothing detail toward the poles.

blender -b --factory-startup --python polar_blend.py -- in out [color|normal] [START_DEG]
On a UV sphere a whole texture row shrinks to a point at the pole, so crater detail there
turns into a starburst. Each row past START_DEG (default 60) is blurred around the sphere
with a radius that grows like 1/cos(latitude), i.e. by how much the row is squeezed (from
zero at START_DEG, so there is no visible seam), and
fades into one cap color (color) or a flat normal (normal) at the pole itself. The cap color
is taken from 70-82 degrees, because the outermost rows of generated maps are often darker.
"""
import sys
import bpy
import numpy as np

args = sys.argv[sys.argv.index("--") + 1:]
src, out = args[:2]
mode = args[2] if len(args) > 2 else "color"
start = np.radians(float(args[3]) if len(args) > 3 else 60.0)

im = bpy.data.images.load(src)
w, h = im.size
px = np.array(im.pixels[:], dtype=np.float32).reshape(h, w, 4)
flat = np.array([0.5, 0.5, 1.0], dtype=np.float32)
lats = np.abs((np.arange(h) + 0.5) / h - 0.5) * np.pi
band = (lats > np.radians(70)) & (lats < np.radians(82))
# Row 0 is the bottom (south) in Blender's pixel order.
caps = {south: px[band & ((np.arange(h) < h / 2) == south), :, :3].mean((0, 1)) for south in (True, False)}


def blur_row(row, r):
    """Box blur that wraps around, since u runs all the way around the equator."""
    if r < 1:
        return row
    if 2 * r + 1 >= w:
        return np.broadcast_to(row.mean(0), row.shape)
    c = np.cumsum(np.concatenate([row[-r - 1:], row, row[:r]]), 0)
    return (c[2 * r + 1:] - c[:-2 * r - 1]) / (2 * r + 1)


for y in range(h):
    lat = lats[y]  # 0 at the equator, pi/2 at the poles
    if lat <= start:
        continue
    t = (lat - start) / (np.pi / 2 - start)
    t = t * t * (3 - 2 * t)
    rgb = px[y, :, :3]
    smooth = blur_row(rgb, int(2 * (1 / max(np.cos(lat), 1e-3) - 1 / np.cos(start))))
    # Close to the pole only the cap color (or no relief) is left.
    pole = flat if mode == "normal" else caps[y < h / 2]
    px[y, :, :3] = smooth * (1 - t) + pole * t

im.pixels = px.ravel()
im.filepath_raw = out
im.file_format = "PNG" if out.lower().endswith(".png") else "JPEG"
if im.file_format == "JPEG":
    bpy.context.scene.render.image_settings.quality = 92
im.save()
print("wrote", out, w, h)

"""Builds the app icon from the game's own assets: the probe swinging around Earth on a golden
slingshot orbit, in front of a disc cut from the Milky Way sky.

blender -b --factory-startup --python make_icon.py -- <res_dir> [preview_size preview.png]
Writes the PICO 3D icon layers (drawable/icon_3d_layer_{0,1}.png: 0 = sky disc, 1 = probe, orbit
and planet), their SDF masks (icon_3d_sdf_*.png) and the flat icons (mipmap-xxxhdpi/
ic_spatial_launcher.png, drawable/ic_launcher_foreground.png). An SDF here is the
distance inside the shape, ramping from 0 at the edge to 1 at 32 px in (1024 px canvas), in all
four channels, matching the SDK template. With preview_size, only the composite is written,
to preview.png.
"""
import math
import os
import sys

import bpy
import numpy as np
from mathutils import Vector

args = sys.argv[sys.argv.index("--") + 1:]
res = args[0]
preview = int(args[1]) if len(args) > 1 else 0
assets = os.path.join(res, "..", "assets")
N = preview or 1024

# --- Foreground render ---------------------------------------------------------------------
bpy.ops.wm.read_factory_settings(use_empty=True)
sc = bpy.context.scene
sc.render.engine = "BLENDER_EEVEE_NEXT"
sc.render.resolution_x = sc.render.resolution_y = N
sc.render.film_transparent = True
sc.view_settings.view_transform = "Standard"
world = bpy.data.worlds.new("w")
world.use_nodes = True
world.node_tree.nodes["Background"].inputs[0].default_value = (0.05, 0.08, 0.16, 1)
world.node_tree.nodes["Background"].inputs[1].default_value = 1.0
sc.world = world

# Camera looks along +Y; screen x = world x, screen up = world z, canvas spans -1..1.
cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
cam.data.type = "ORTHO"
cam.data.ortho_scale = 2.0
cam.location = (0, -10, 0)
cam.rotation_euler = (math.radians(90), 0, 0)
sc.collection.objects.link(cam)
sc.camera = cam

sun = bpy.data.objects.new("sun", bpy.data.lights.new("sun", "SUN"))
sun.data.energy = 4.5
sun.data.color = (1.0, 0.93, 0.82)
sun.rotation_euler = (math.radians(55), math.radians(-35), math.radians(-20))
sc.collection.objects.link(sun)


def material(name, color=None, image=None, emission=None, strength=1.0, rough=0.5):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    bsdf = nt.nodes["Principled BSDF"]
    bsdf.inputs["Roughness"].default_value = rough
    if image:
        tex = nt.nodes.new("ShaderNodeTexImage")
        tex.image = bpy.data.images.load(image)
        nt.links.new(tex.outputs[0], bsdf.inputs["Base Color"])
    if color:
        bsdf.inputs["Base Color"].default_value = (*color, 1)
    if emission:
        bsdf.inputs["Emission Color"].default_value = (*emission, 1)
        bsdf.inputs["Emission Strength"].default_value = strength
    return m


# Earth, just left of and below the center.
center = Vector((-0.15, 0.6, -0.12))
radius = 0.26
bpy.ops.mesh.primitive_uv_sphere_add(segments=96, ring_count=48, radius=radius, location=center)
earth = bpy.context.object
bpy.ops.object.shade_smooth()
earth.rotation_euler = (math.radians(20), 0, math.radians(200))
earth.data.materials.append(material("earth", image=os.path.join(assets, "textures/earth.jpg"), rough=0.6))

# Slingshot orbit, drawn in front of Earth: in from the lower right, around Earth's far side, out
# to the upper right. A hand-shaped Bezier: a true hyperbola can't turn this far inside the icon.
ctrl = [(0.35, -0.78), (-0.95, -0.85), (-0.80, 0.62), (0.45, 0.45)]


def bezier(t):
    u = 1 - t
    x, z = (u ** 3 * ctrl[0][k] + 3 * u * u * t * ctrl[1][k] + 3 * u * t * t * ctrl[2][k] + t ** 3 * ctrl[3][k] for k in (0, 1))
    return Vector((x, -0.2, z))


pts = [bezier(i / 120) for i in range(121)]
curve = bpy.data.curves.new("orbit", "CURVE")
curve.dimensions = "3D"
curve.bevel_depth = 0.02
curve.bevel_resolution = 4
spline = curve.splines.new("POLY")
spline.points.add(len(pts) - 1)
# Thin where the probe came from, full width behind it.
for i, (sp, q) in enumerate(zip(spline.points, pts)):
    sp.co = (*q, 1)
    sp.radius = 0.2 + 0.8 * (i / (len(pts) - 1)) ** 1.5
orbit = bpy.data.objects.new("orbit", curve)
sc.collection.objects.link(orbit)
orbit.data.materials.append(material("gold", color=(1.0, 0.55, 0.12), emission=(1.0, 0.45, 0.08), strength=0.8))

# Probe at the end of the orbit, its window turned toward the viewer, sized by its world bounds
# (the glTF import nests the mesh under scaled parents).
bpy.ops.import_scene.gltf(filepath=os.path.join(assets, "models/probe.glb"))
probe = bpy.data.objects.new("probe", None)
sc.collection.objects.link(probe)
for o in [o for o in sc.objects if o.parent is None and o not in (earth, orbit, cam, sun, probe)]:
    o.parent = probe
bpy.context.view_layer.update()
corners = [o.matrix_world @ Vector(c) for o in sc.objects if o.type == "MESH" and o is not earth for c in o.bound_box]
lo = Vector([min(c[k] for c in corners) for k in range(3)])
hi = Vector([max(c[k] for c in corners) for k in range(3)])
for o in probe.children:
    o.location -= (lo + hi) / 2
probe.scale = Vector([0.42 / max(hi - lo)] * 3)
probe.location = pts[-1] + Vector((0.12, -0.6, 0.08))
probe.rotation_euler = (math.radians(8), math.radians(-18), math.radians(-60))

sc.render.filepath = os.path.join(bpy.app.tempdir, "fg.png")
bpy.ops.render.render(write_still=True)
fg = np.array(bpy.data.images.load(sc.render.filepath).pixels[:], dtype=np.float32).reshape(N, N, 4)

# --- Background disc from the game's sky ----------------------------------------------------
sky = bpy.data.images.load(os.path.join(assets, "textures/sky.jpg"))
sw, sh = sky.size
skypx = np.array(sky.pixels[:], dtype=np.float32).reshape(sh, sw, 4)
# A square from the Milky Way band, resampled to N.
side = sh // 2
x0, y0 = int(sw * 0.30), (sh - side) // 2
idx = (np.arange(N) * side / N).astype(int)
crop = skypx[y0 + idx][:, x0 + idx]
yy, xx = np.mgrid[0:N, 0:N]
rr = np.hypot(xx - (N - 1) / 2, yy - (N - 1) / 2) / (N / 2)
# Darken it, more toward the rim, so the foreground reads at icon size.
bg = crop.copy()
bg[..., :3] *= np.clip(0.95 - 0.5 * rr ** 2, 0, 1)[..., None]
bg[..., :3] = np.clip(bg[..., :3] * 0.9 + np.array([0.0, 0.01, 0.04]), 0, 1)
disc_alpha = np.clip((1 - rr) * N / 2 + 0.5, 0, 1)  # one-pixel antialiased edge
bg[..., 3] = disc_alpha


def over(top, base):
    a = top[..., 3:4]
    out = base.copy()
    out[..., 3:4] = a + base[..., 3:4] * (1 - a)
    out[..., :3] = (top[..., :3] * a + base[..., :3] * base[..., 3:4] * (1 - a)) / np.maximum(out[..., 3:4], 1e-6)
    return out


def save(px, path):
    h, w = px.shape[:2]
    im = bpy.data.images.new(os.path.basename(path), w, h, alpha=True)
    im.pixels = np.ascontiguousarray(px, dtype=np.float32).ravel()
    im.filepath_raw = path
    im.file_format = "PNG"
    im.save()
    print("wrote", path)


composite = over(fg, bg)
if preview:
    save(composite, args[2])
    sys.exit(0)


def sdf(alpha):
    """Inside distance / 32 px by repeated erosion, alternating square and cross neighborhoods
    so the ramp is close to round."""
    m = alpha > 0.5
    total = m.astype(np.float32)
    for k in range(31):
        p = np.pad(m, 1)
        nb = [p[:-2, 1:-1], p[2:, 1:-1], p[1:-1, :-2], p[1:-1, 2:]]
        if k % 2:
            nb += [p[:-2, :-2], p[:-2, 2:], p[2:, :-2], p[2:, 2:]]
        for n in nb:
            m = m & n
        total += m
    v = np.clip(total / 32, 0, 1)
    return np.repeat(v[..., None], 4, axis=2)


save(bg, os.path.join(res, "drawable/icon_3d_layer_0.png"))
save(fg, os.path.join(res, "drawable/icon_3d_layer_1.png"))
save(sdf(bg[..., 3]), os.path.join(res, "drawable/icon_3d_sdf_0.png"))
save(sdf(fg[..., 3]), os.path.join(res, "drawable/icon_3d_sdf_1.png"))
save(composite, os.path.join(res, "mipmap-xxxhdpi/ic_spatial_launcher.png"))

# Adaptive-icon foreground (1424, full bleed): the sky as a square, the artwork inside the safe zone.
M = 1424
full = np.zeros((M, M, 4), dtype=np.float32)
idx = (np.arange(M) * side / M).astype(int)
full[...] = skypx[y0 + idx][:, x0 + idx]
full[..., :3] *= 0.85
full[..., 3] = 1
off = (M - N) // 2
full[off:off + N, off:off + N] = over(fg, full[off:off + N, off:off + N])
save(full, os.path.join(res, "drawable/ic_launcher_foreground.png"))

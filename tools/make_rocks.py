"""Procedural asteroids: lumpy, cratered rocks with UVs, one GLB per variant.

blender -b --factory-startup --python make_rocks.py -- <out_dir> [count]
Each rock is a UV sphere pushed around by noise (overall lumps) and Voronoi (craters), about
1k triangles, radius ~1 (the app scales it). Materials are set by the app, so only geometry and
UVs matter here.
"""
import sys
import bpy

args = sys.argv[sys.argv.index("--") + 1:]
out_dir = args[0]
count = int(args[1]) if len(args) > 1 else 3

for i in range(count):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.mesh.primitive_uv_sphere_add(segments=40, ring_count=20, radius=1)
    rock = bpy.context.object
    rock.scale = (1.0 + 0.18 * i, 0.82 - 0.06 * i, 0.92)
    bpy.ops.object.transform_apply(scale=True)

    lumps = bpy.data.textures.new(f"lumps{i}", "CLOUDS")
    lumps.noise_scale = 0.9
    lumps.noise_depth = 2
    d = rock.modifiers.new("lumps", "DISPLACE")
    d.texture = lumps
    d.strength = 0.35
    d.texture_coords = "GLOBAL"
    d.mid_level = 0.5

    craters = bpy.data.textures.new(f"craters{i}", "VORONOI")
    craters.noise_scale = 0.45
    craters.distance_metric = "DISTANCE"
    craters.color_mode = "INTENSITY"
    c = rock.modifiers.new("craters", "DISPLACE")
    c.texture = craters
    c.strength = 0.12
    c.texture_coords = "GLOBAL"
    # Sampling the noise at a different place gives each variant its own shape.
    rock.location = (7.3 * i, 3.1 * i, 1.7 * i)
    bpy.ops.object.modifier_apply(modifier="lumps")
    bpy.ops.object.modifier_apply(modifier="craters")
    rock.location = (0, 0, 0)
    bpy.ops.object.shade_smooth()
    path = f"{out_dir}/rock{i}.glb"
    bpy.ops.export_scene.gltf(filepath=path, export_format="GLB", use_selection=True,
                              export_materials="NONE", export_yup=True)
    print("wrote", path, len(rock.data.polygons), "faces")

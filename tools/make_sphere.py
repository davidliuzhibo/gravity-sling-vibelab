"""A UV sphere with tangents, for bodies that use a normal map.

blender -b --factory-startup --python make_sphere.py -- <out.glb>
MeshResource.createSphere carries no tangents, so a normal map on it has nothing to be
oriented by. This sphere (radius 1, like the app's unit sphere) is exported with tangents;
the app swaps its material for the body's own.
"""
import sys
import bpy

out = sys.argv[sys.argv.index("--") + 1]
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.mesh.primitive_uv_sphere_add(segments=64, ring_count=32, radius=1)
bpy.ops.object.shade_smooth()
bpy.ops.export_scene.gltf(filepath=out, export_format="GLB", use_selection=True,
                          export_materials="NONE", export_tangents=True, export_yup=True)
print("wrote", out, len(bpy.context.object.data.polygons), "faces")

"""Render the Master Yoda GLB to transparent PNGs for the app and README.

Run headless:
    blender -b -P art/render_yoda.py -- <glb> <out_dir>
"""

import math
import sys
from pathlib import Path

import bpy
from mathutils import Vector

argv = sys.argv[sys.argv.index("--") + 1 :]
glb, out_dir = Path(argv[0]), Path(argv[1])
out_dir.mkdir(parents=True, exist_ok=True)

bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=str(glb))

meshes = [o for o in bpy.context.scene.objects if o.type == "MESH"]
corners = [o.matrix_world @ Vector(c) for o in meshes for c in o.bound_box]
lo = Vector((min(c.x for c in corners), min(c.y for c in corners), min(c.z for c in corners)))
hi = Vector((max(c.x for c in corners), max(c.y for c in corners), max(c.z for c in corners)))
print("BOUNDS", lo, hi)

scene = bpy.context.scene
scene.render.engine = "CYCLES"
scene.cycles.samples = 256
scene.cycles.use_denoising = True
try:
    prefs = bpy.context.preferences.addons["cycles"].preferences
    prefs.compute_device_type = "CUDA"
    prefs.get_devices()
    for d in prefs.devices:
        d.use = True
    scene.cycles.device = "GPU"
except Exception as exc:  # CPU render still works, only slower
    print("GPU setup failed:", exc)
scene.render.film_transparent = True
scene.render.image_settings.file_format = "PNG"
scene.render.image_settings.color_mode = "RGBA"
scene.view_settings.view_transform = "AgX"

world = bpy.data.worlds.new("World")
scene.world = world
world.use_nodes = True
bg = next(n for n in world.node_tree.nodes if n.type == "BACKGROUND")
bg.inputs[0].default_value = (0.55, 0.62, 0.5, 1)
bg.inputs[1].default_value = 0.5

center = (lo + hi) / 2
size = hi - lo
height = max(size.x, size.y, size.z)
up_axis = max(range(3), key=lambda i: size[i])
print("UP AXIS", up_axis, "SIZE", size)


def add_light(name, loc, energy, color=(1, 1, 1), radius=1.0):
    data = bpy.data.lights.new(name, "AREA")
    data.energy = energy
    data.color = color
    data.size = radius * height
    obj = bpy.data.objects.new(name, data)
    obj.location = loc
    scene.collection.objects.link(obj)
    direction = center - obj.location
    obj.rotation_euler = direction.to_track_quat("-Z", "Y").to_euler()


def render(name, res, frame_top, frame_bottom, yaw_deg, dist_mult):
    """frame_* are fractions of model height (0 = feet, 1 = top of ears/staff)."""
    scene.render.resolution_x, scene.render.resolution_y = res
    for o in [o for o in scene.objects if o.type in {"CAMERA", "LIGHT"}]:
        bpy.data.objects.remove(o)
    z0 = lo.z + frame_bottom * size.z
    z1 = lo.z + frame_top * size.z
    target = Vector((center.x, center.y, (z0 + z1) / 2))
    cam_data = bpy.data.cameras.new("cam")
    cam_data.lens = 70
    cam = bpy.data.objects.new("cam", cam_data)
    scene.collection.objects.link(cam)
    # The model faces +Y, so yaw 180 puts the camera in front of him.
    yaw = math.radians(yaw_deg)
    span = z1 - z0
    fov = 2 * math.atan(cam_data.sensor_width / 2 / cam_data.lens)
    aspect = res[0] / res[1]
    # Blender's AUTO sensor fit spans the longer side, so a portrait frame's
    # vertical field of view is the full lens angle.
    vfov = fov if aspect < 1 else 2 * math.atan(math.tan(fov / 2) / aspect)
    dist = span / 2 / math.tan(vfov / 2) * dist_mult
    cam.location = target + Vector((math.sin(yaw) * -dist, -math.cos(yaw) * dist, span * 0.08))
    cam.rotation_euler = (target - cam.location).to_track_quat("-Z", "Y").to_euler()
    scene.camera = cam
    front = Vector((math.sin(yaw) * -1, -math.cos(yaw), 0))
    side = Vector((front.y, -front.x, 0))
    add_light(
        "key",
        target + front * height * 1.5 + side * height * 1.2 + Vector((0, 0, height)),
        900,
        (1.0, 0.95, 0.85),
    )
    add_light("fill", target + front * height * 1.8 - side * height * 1.5, 250, (0.8, 0.9, 1.0))
    add_light(
        "rim",
        target - front * height * 1.5 + Vector((0, 0, height * 1.2)),
        700,
        (0.75, 1.0, 0.6),
        0.5,
    )
    scene.render.filepath = str(out_dir / f"{name}.png")
    bpy.ops.render.render(write_still=True)


for o in meshes:
    print("MESH", o.name, [round(v, 2) for v in o.dimensions])

mode = argv[2] if len(argv) > 2 else "all"
if mode in {"all", "preview"}:
    render("preview_front", (512, 640), 1.0, 0.0, 180, 1.25)
    render("preview_bust", (512, 512), 1.0, 0.5, 200, 1.2)
if mode == "all":
    render("yoda_bust", (900, 900), 1.0, 0.45, 200, 1.4)
    render("yoda_full", (1000, 1400), 1.0, 0.0, 200, 1.12)

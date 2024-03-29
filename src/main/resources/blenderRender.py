
# This script is used to render the data from the CSV file as a 3D animation in Blender.
# The log data created by the io.example.LogEvent class is used as input for this script.
# The .log file is converted to a .csv file using the following command:
# src/main/resources/log-event-from-log.sh > /tmp/earth-ship-events-unsorted.csv
# sort /tmp/earth-ship-events-unsorted.csv > /tmp/earth-ship-events.csv
# change the data_file_path variable to point to the .csv file

# You can run the blenderRender.py script from the Blender Python console using the following command:
# exec(compile(open("/tmp/blenderRender.py").read(), "/tmp/blenderRender.py", 'exec'))

# You can run the blenderRender.py script from the command line using the following command:
# blender -b -P /tmp/blenderRender.py

# In VSCode, you can run the blenderRender.py script using the Blender extension.
# First, start Blender from the Command Palette (Ctrl+Shift+P) and select the "Blender: Start" command.
# Open the Command Palette (Ctrl+Shift+P) and select the "Blender: Run Script" command.
# To stop the script, open the Command Palette (Ctrl+Shift+P) and select the "Blender: Stop" command.


import bpy
import bmesh
import csv
import random
import math
import os
import time
import uuid
from math import radians
from mathutils import Vector, Quaternion, Euler
from dataclasses import dataclass

# Define the path to your data file
data_file_path = '/tmp/earth-ship-events-203-orders.csv'
video_file_path = '~/Downloads/earth-ship-events-203-orders-'

# Define the positions and radii for the event types
event_positions = {
    'Generator': (0, 0.5, 0),
    'Region': (3, 4, 0),
    'GeoOrder': (3, 0.5, 0),
    'Order': (3, -2, 0),
    'ShippingOrder': (5.5, -2, 0),
    'ShippingOrderItem': (9, -2, 0),
    'OrderSkuItem': (14.5, -2, 0),
    'StockSkuItem': (21.5, -2, 0),
    'StockOrderLot': (21.5, 3.5, 0),
    'StockOrder': (18, 1.5, 0),
    'BackOrderedLot': (14.5, 3.5, 0),
    'Product': (18, 4, 0),
    'ShoppingCart': (0, -2, 0),
}

event_radii = {
    'Generator': 0.25,
    'Region': 2,
    'GeoOrder': 1,
    'Order': 1,
    'ShippingOrder': 1,
    'ShippingOrderItem': 2,
    'OrderSkuItem': 3,
    'StockSkuItem': 3,
    'StockOrderLot': 2,
    'StockOrder': 1,
    'BackOrderedLot': 2,
    'Product': 0.5,
    'ShoppingCart': 0.25,
}

material_settings = {
    'Generator': ('#FFFFFF', 20),
    'StockOrderLot': ('#0697FF', 15),
    'StockOrder':   ('#FF4F08', 20),
    'Product': ('#63FF07', 15),
    'ShoppingCart': ('#FF2308', 10),
    'Path': ('#656565', 1),
    # 'Path-1': ('#005EFF', 5),
    # 'Path-2': ('#0DFF5F', 5),
    'Path-1': ('#7609BF', 5),  # ('#005EFF', 5),
    'Path-2': ('#4CA607', 5),  # ('#0DFF5F', 5),
    'Path-3': ('#884F00', 5),
    'Text': ('#FFFFFF', 2),
}

multi_color_materials = [
    'OrderSkuItem',
    'StockSkuItem',
    'ShippingOrderItem',
    'ShippingOrder',
    'Order',
    'GeoOrder',
    'Region',
    'BackOrderedLot',
]

# path_materials = {
#     'Path': ('#656565', 1),
#     'Path-1': ('#7609BF', 5),  # ('#005EFF', 5),
#     'Path-2': ('#4CA607', 5),  # ('#0DFF5F', 5),
#     'Path-3': ('#884F00', 5),
# }


# Dictionaries to keep track of created points and paths
created_points = {}
created_paths = {}

red_yellow_green_material_name = 'RedYellowGreen'
red_yellow_green_material_red_value = 0.0
red_yellow_green_material_yellow_value = 0.5
red_yellow_green_material_green_value = 1.0
stock_sku_item_material_name = 'StockSkuItem'

video_fps = 60
# wait N seconds before starting the data animation
video_first_frame = 3 * video_fps
video_file_path = os.path.expanduser(video_file_path)
video_playback_realtime = 1000
video_playback_half_speed = 500
video_playback_quarter_speed = 250
video_playback_tenth_speed = 100
video_playback_twenth_speed = 50
video_playback_speed = video_playback_quarter_speed


def scene_setup():
    desired_fps = 30

    # Set the render engine to Cycles
    bpy.context.scene.render.engine = 'BLENDER_EEVEE'

    # Set the render resolution
    bpy.context.scene.render.resolution_x = 1920
    bpy.context.scene.render.resolution_y = 1080

    # Set the number of samples for Cycles
    # bpy.context.scene.cycles.samples = 500
    # Set the numner of samples for Eevee
    bpy.context.scene.eevee.taa_render_samples = 32

    # Set the frame rate
    bpy.context.scene.render.fps = desired_fps

    # Set the output format
    bpy.context.scene.render.image_settings.file_format = 'FFMPEG'

    # Set the video container to MPEG-4
    bpy.context.scene.render.ffmpeg.format = 'MPEG4'

    # Set the output file path
    bpy.context.scene.render.filepath = video_file_path

    # Set the output codec
    bpy.context.scene.render.ffmpeg.codec = 'H264'

    # Set the output quality
    bpy.context.scene.render.ffmpeg.constant_rate_factor = 'PERC_LOSSLESS'

    # Set the output resolution
    bpy.context.scene.render.resolution_percentage = 100

    # Access the world properties
    world = bpy.context.scene.world

    # Set the background color (RGBA)
    world.use_nodes = True
    bg_node = world.node_tree.nodes['Background']
    bg_node.inputs['Color'].default_value = (0.0, 0.0, 0.0, 1)

    # Access the render properties
    eevee_settings = bpy.context.scene.eevee

    # Enable bloom
    eevee_settings.use_bloom = True

    # Deselect all objects
    bpy.ops.object.select_all(action='DESELECT')

    # Select all objects in the scene
    bpy.ops.object.select_all(action='SELECT')

    # Delete all selected objects
    bpy.ops.object.delete()

    # Setup the scene
    add_scene_objects()

    create_red_yellow_green_material(red_yellow_green_material_name)
    create_stock_sku_green_material(stock_sku_item_material_name)
    create_path_material('path')


def add_scene_objects():
    # Add a camera
    add_camera()
    add_labels()


def add_camera():
    # Add a camera
    camera_data = bpy.data.cameras.new('Camera')
    camera = bpy.data.objects.new('Camera', camera_data)
    camera.location = (0, 0, 37)
    bpy.context.collection.objects.link(camera)

    # Add an empty object
    empty_location = (12.25, 0.25, 0)
    bpy.ops.object.empty_add(type='PLAIN_AXES', align='WORLD', location=empty_location)
    empty = bpy.context.active_object

    # Add a track to constraint to the camera to empty
    track_to = camera.constraints.new(type='TRACK_TO')
    track_to.target = empty
    track_to.track_axis = 'TRACK_NEGATIVE_Z'
    track_to.up_axis = 'UP_Z'

    camera.parent = empty

    # Set the camera to active
    bpy.context.scene.camera = camera


def add_labels():
    add_label('Generator', (0, 1, 0))
    add_label('ShoppingCart', (0, -2.5, 0))
    add_label('Region', (5.75, 4, 0))
    add_label('GeoOrder', (4.75, 0.5, 0))
    add_label('Order', (3, -3.5, 0))
    add_label('ShippingOrder', (5.5, -3.5, 0))
    add_label('ShippingOrderItem', (9, -4.5, 0))
    add_label('OrderSkuItem', (14.5, -5.5, 0))
    add_label('StockSkuItem', (21.5, -5.5, 0))
    add_label('StockOrder', (18, 3, 0))
    add_label('Product', (18, 5, 0))
    add_label('BackOrderedLot', (14.5, 6, 0))
    add_label('StockOrderedLot', (21.5, 6, 0))


def add_label(text, location):
    bpy.ops.object.text_add(location=location)
    text_object = bpy.context.object
    text_object.data.body = text

    # Set the text material
    assign_material('Text', text_object)

    # Set the X and Y scale
    text_object.scale = (0.25, 0.25, 1)

    # Set the extrude
    text_object.data.extrude = 0.01

    # Center vertically and horizontally
    text_object.data.align_x = 'CENTER'
    text_object.data.align_y = 'CENTER'


def random_point_in_sphere(sphere_location, radius):
    while True:
        point = (
            sphere_location[0] + random.uniform(-radius, radius),
            sphere_location[1] + random.uniform(-radius, radius),
            sphere_location[2] + random.uniform(-radius, radius),
        )
        if ((point[0] - sphere_location[0]) ** 2 + (point[1] - sphere_location[1]) ** 2 + (point[2] - sphere_location[2]) ** 2) <= radius ** 2:
            return point


def create_point(location, name, material_name):
    mesh = bpy.data.meshes.new(name)
    point = bpy.data.objects.new(name, mesh)

    bpy.context.collection.objects.link(point)

    # Create the point geometry (cube)
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=0.05)
    bm.to_mesh(mesh)
    bm.free()

    # Assign the material to the point
    # assign_material(material_name, point)
    if material_name in multi_color_materials:
        new_material_name = f'{material_name}:{uuid.uuid4()}'
        if (material_name == stock_sku_item_material_name):
            copy_material(stock_sku_item_material_name, new_material_name)
        else:
            copy_material(red_yellow_green_material_name, new_material_name)
        assign_material(new_material_name, point)
        set_material_value(new_material_name, red_yellow_green_material_yellow_value)
    else:
        assign_material(material_name, point)

    point.location = location
    return point


def create_path(from_point_in, to_point_in, name):
    from_point = Vector((from_point_in[0], from_point_in[1], from_point_in[2]))
    to_point = Vector((to_point_in[0], to_point_in[1], to_point_in[2]))

    vec = from_point - to_point
    distance = vec.length

    # Create a new mesh and object for the path
    mesh = bpy.data.meshes.new(name)
    path = bpy.data.objects.new(name, mesh)

    # Link the path object to the scene
    bpy.context.collection.objects.link(path)

    # Create the path geometry (cube)
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1)
    bm.to_mesh(mesh)
    bm.free()

    # Set the path's location and rotation
    # path.location = from_point
    path.location = ((from_point[0] + to_point[0]) / 2, (from_point[1] +
                     to_point[1]) / 2, (from_point[2] + to_point[2]) / 2)
    quat = vec.to_track_quat('Z', 'Y')
    path.rotation_mode = 'QUATERNION'
    path.rotation_quaternion = quat

    # Scale the path cube to the required dimensions
    path.scale.x = 0.002
    path.scale.y = 0.002
    path.scale.z = distance

    return path


def create_emission_material(name, color, strength):
    # Create a new material
    material = bpy.data.materials.new(name)
    material.use_nodes = True

    # Clear all existing nodes
    nodes = material.node_tree.nodes
    nodes.clear()

    # Add nodes
    output_node = nodes.new('ShaderNodeOutputMaterial')
    emission_node = nodes.new('ShaderNodeEmission')
    emission_node.inputs['Color'].default_value = color
    emission_node.inputs['Strength'].default_value = strength

    # Position nodes (optional)
    output_node.location = (400, 0)
    emission_node.location = (0, 0)

    # Connect nodes
    links = material.node_tree.links
    links.new(emission_node.outputs['Emission'], output_node.inputs['Surface'])

    return material


def srgb_to_linear(srgb_color):
    linear_color = []
    for channel in srgb_color:
        if channel <= 0.04045:
            linear_channel = channel / 12.92
        else:
            linear_channel = ((channel + 0.055) / 1.055) ** 2.4
        linear_color.append(linear_channel)
    return tuple(linear_color)


def hex_to_rgba(hex_color, alpha=1):
    if hex_color.startswith('#'):
        hex_color = hex_color[1:]

    srgb = tuple(int(hex_color[i:i + 2], 16) / 255 for i in (0, 2, 4))
    linear_rgb = srgb_to_linear(srgb)

    return (*linear_rgb, alpha)


def create_red_yellow_green_material(material_name: str):
    red = (1, 0, 0, 1)
    green = (0, 1, 0, 1)
    red_strength = 15
    green_strength = 15

    return create_emission_mixed_material(material_name, red, red_strength, green, green_strength)


def create_stock_sku_green_material(material_name: str):
    # Create a new material
    material = bpy.data.materials.new(name=material_name)
    material.use_nodes = True
    nodes = material.node_tree.nodes
    links = material.node_tree.links

    # Clear all nodes to start clean
    for node in nodes:
        nodes.remove(node)

    # Add a Value node and Math node with 'Less Than' operation
    value_node = nodes.new(type='ShaderNodeValue')
    value_node.outputs['Value'].default_value = 0.5
    value_node.location = -400, 100

    math_node = nodes.new(type='ShaderNodeMath')
    math_node.operation = 'GREATER_THAN'
    math_node.inputs[1].default_value = 0.6
    math_node.location = -200, 100

    # Add two emission nodes
    emission_node1 = nodes.new(type='ShaderNodeEmission')
    emission_node1.inputs['Color'].default_value = (0.0, 0.01, 1.0, 1.0)
    emission_node1.inputs['Strength'].default_value = 20.0
    emission_node1.location = -200, -100

    emission_node2 = nodes.new(type='ShaderNodeEmission')
    emission_node2.inputs['Color'].default_value = (0.0, 1.0, 0.05, 1.0)
    emission_node2.inputs['Strength'].default_value = 20.0
    emission_node2.location = -200, -250

    # Add a Mix Shader node
    mix_shader = nodes.new(type='ShaderNodeMixShader')
    mix_shader.location = 100, 0

    # Add an Output node
    output_node = nodes.new(type='ShaderNodeOutputMaterial')
    output_node.location = 300, 0

    # Link the nodes
    links.new(emission_node1.outputs[0], mix_shader.inputs[1])
    links.new(emission_node2.outputs[0], mix_shader.inputs[2])
    links.new(mix_shader.outputs[0], output_node.inputs[0])
    links.new(value_node.outputs[0], math_node.inputs[0])
    links.new(math_node.outputs[0], mix_shader.inputs[0])

    return material


def create_path_material(material_name: str):
    normal = (0.13, 0.13, 0.13, 1)
    highlighted = (1, 1, 1, 1)
    normal_strength = 1
    highlighted_strength = 5

    return create_emission_mixed_material(material_name, normal, normal_strength, highlighted, highlighted_strength)


def create_emission_mixed_material(name: str, emission_1_color, emission_1_strength, emission_2_color, emission_2_strength) -> bpy.types.Material:
    if name in bpy.data.materials:
        return bpy.data.materials[name]

    # Create a new material and assign the name
    material = bpy.data.materials.new(name)
    material.use_nodes = True

    # Get the node tree and create a shortcut to the nodes
    node_tree = material.node_tree
    nodes = node_tree.nodes

    # Clear default nodes
    nodes.clear()

    # Create Output, Mix Shader, and Value nodes
    output_node = nodes.new('ShaderNodeOutputMaterial')
    mix_shader_node = nodes.new('ShaderNodeMixShader')
    value_node = nodes.new('ShaderNodeValue')

    # Set the value node to 0.5
    value_node.outputs[0].default_value = red_yellow_green_material_yellow_value

    # Create Emission nodes
    emission_node1 = nodes.new('ShaderNodeEmission')
    emission_node2 = nodes.new('ShaderNodeEmission')

    # Set emission node colors and strengths
    emission_node1.inputs[0].default_value = emission_1_color
    emission_node1.inputs[1].default_value = emission_1_strength
    emission_node2.inputs[0].default_value = emission_2_color
    emission_node2.inputs[1].default_value = emission_2_strength

    # Position nodes (optional)
    output_node.location = (400, 0)
    mix_shader_node.location = (200, 0)
    value_node.location = (0, 150)
    emission_node1.location = (0, 0)
    emission_node2.location = (0, -150)

    # Connect the nodes
    node_tree.links.new(mix_shader_node.outputs[0], output_node.inputs[0])
    node_tree.links.new(value_node.outputs[0], mix_shader_node.inputs[0])
    node_tree.links.new(emission_node1.outputs[0], mix_shader_node.inputs[1])
    node_tree.links.new(emission_node2.outputs[0], mix_shader_node.inputs[2])

    return material


def copy_material(material_name_from: str, material_name_to: str) -> bpy.types.Material:
    # Check if the source material exists
    if material_name_from not in bpy.data.materials:
        print(f'Material "{material_name_from}" not found.')
        return None

    # Get the source material
    source_material = bpy.data.materials[material_name_from]

    # Copy the source material
    new_material = source_material.copy()
    new_material.name = material_name_to

    return new_material


def set_material_value(material_name: str, kf_value: float):
    # Check if the material exists
    if material_name not in bpy.data.materials:
        print(f'Material "{material_name}" not found.')
        return

    # Get the material
    material = bpy.data.materials[material_name]
    node_tree = material.node_tree
    nodes = node_tree.nodes

    # Set value node
    value_node = nodes.get('Value')
    if value_node:
        value_node.outputs[0].default_value = kf_value
    else:
        print(f'Value node not found in "{material_name}".')


def set_material_value_keyframe(material_name: str, kf_value: float, frame: int):
    # Check if the material exists
    if material_name not in bpy.data.materials:
        print(f'Material "{material_name}" not found.')
        return

    # Get the material
    material = bpy.data.materials[material_name]
    node_tree = material.node_tree
    nodes = node_tree.nodes

    # Set value node
    value_node = nodes.get('Value')
    if value_node:
        # Add a keyframe to the Value node output
        value_node.outputs[0].default_value = red_yellow_green_material_yellow_value
        value_node.outputs[0].keyframe_insert(data_path='default_value', frame=frame - 1)
        value_node.outputs[0].default_value = kf_value
        value_node.outputs[0].keyframe_insert(data_path='default_value', frame=frame)

        # Set interpolation to Constant
        action = node_tree.animation_data.action
        fcurves = action.fcurves
        for fcurve in fcurves:
            for kf in fcurve.keyframe_points:
                if kf.co.x == frame:
                    kf.interpolation = 'CONSTANT'
                    break
    else:
        print(f'Value node not found in "{material_name}".')


def assign_material(material_name, shape):
    # Create the material if it doesn't already exist
    if material_name not in bpy.data.materials:
        # mat = bpy.data.materials.new(name=material_name)
        material_setting = material_settings[material_name]
        color = hex_to_rgba(material_setting[0])
        strength = material_setting[1]
        mat = create_emission_material(name=material_name, color=color, strength=strength)
    else:
        mat = bpy.data.materials[material_name]

    # Assign the material to the shape
    if len(shape.data.materials) == 0:
        shape.data.materials.append(mat)
    else:
        shape.data.materials[0] = mat


def point_key_for(event_type, event_id):
    return f'{event_type}_{event_id}'


# Create path keys using event_from_type and event_to_type ordered alphabetically
def path_key_for(event_from_type, event_from_id, event_to_type, event_to_id):
    if event_from_type > event_to_type:
        return f'{event_to_type}_{event_to_id}_{event_from_type}_{event_from_id}'
    return f'{event_from_type}_{event_from_id}_{event_to_type}_{event_to_id}'


# Animate the object to appear at the specified frame
def insert_visibility_key_frame(frame, obj):
    if frame > 1:
        obj.hide_render = True
        obj.hide_viewport = True
        obj.keyframe_insert(data_path='hide_render', frame=frame - 1)
        obj.keyframe_insert(data_path='hide_viewport', frame=frame - 1)
        obj.hide_render = False
        obj.hide_viewport = False
        obj.keyframe_insert(data_path='hide_render', frame=frame)
        obj.keyframe_insert(data_path='hide_viewport', frame=frame)
        obj['frame'] = frame


# def location_and_rotation_of_object(obj_name):
#     obj = bpy.data.objects[obj_name]
#     return obj.location, obj.rotation_euler


def view_360(frame_from, frames):
    frame_to = frame_from + frames

    empty = bpy.data.objects['Empty']

    bpy.context.scene.frame_start = frame_from
    bpy.context.scene.frame_end = frame_to

    empty.rotation_mode = 'XYZ'
    empty.rotation_euler = (0, 0, 0)
    empty.keyframe_insert(data_path='rotation_euler', frame=frame_from)

    empty.rotation_euler.y = radians(360)
    empty.keyframe_insert(data_path='rotation_euler', frame=frame_to + 1)

    fcurves = empty.animation_data.action.fcurves
    for fcurve in fcurves:
        for kf in fcurve.keyframe_points:
            kf.interpolation = 'BEZIER'


def create_from_point(frame, event_from_type, event_from_id):
    # Check if the from_point already exists, otherwise create it
    from_key = point_key_for(event_from_type, event_from_id)
    if from_key in created_points:
        return created_points[from_key].location

    from_location = random_point_in_sphere(event_positions[event_from_type], event_radii[event_from_type])
    from_point_obj = create_point(from_location, from_key, event_from_type)
    created_points[from_key] = from_point_obj

    insert_visibility_key_frame(frame, from_point_obj)

    return from_location


def create_to_point(frame, event_to_type, event_to_id):
    # Check if the to_point already exists, otherwise create it
    to_key = point_key_for(event_to_type, event_to_id)
    if to_key in created_points:
        return created_points[to_key].location

    to_location = random_point_in_sphere(
        event_positions[event_to_type], event_radii[event_to_type])
    to_point_obj = create_point(to_location, to_key, event_to_type)
    created_points[to_key] = to_point_obj

    insert_visibility_key_frame(frame, to_point_obj)

    return to_location


# These path colors are used to highlight different shopping cart orders
# The frames will need to be adjusted to coincide with the frames of the
# shopping cart orders
@dataclass
class PathAltMaterial:
    frame: int
    material_name: str


path_alt_material = [
    PathAltMaterial(248, 'Path-1'),
    PathAltMaterial(1762, 'Path-2'),
    PathAltMaterial(3715, 'Path-3'),
]


def assign_path_material(frame, path):
    for path_material in path_alt_material:
        if frame <= path_material.frame:
            assign_material(path_material.material_name, path)
            return

    # assign_material('Path', path)
    new_material_name = f'path:{uuid.uuid4()}'
    new_material = copy_material('path', new_material_name)
    assign_material(new_material_name, path)
    set_material_value(new_material_name, 0.0)

    node_tree = new_material.node_tree
    nodes = node_tree.nodes
    value_node = nodes['Value']
    if value_node:
        value_node.outputs[0].default_value = 0.0
        value_node.outputs[0].keyframe_insert(data_path='default_value', frame=frame - 1)
        value_node.outputs[0].default_value = 1.0
        value_node.outputs[0].keyframe_insert(data_path='default_value', frame=frame)
        value_node.outputs[0].default_value = 0.0
        value_node.outputs[0].keyframe_insert(data_path='default_value', frame=frame + 5)


def create_from_to_path(frame, event_from_type, event_from_id, event_to_type, event_to_id):
    # Create the path (line) between the points
    path_key = path_key_for(
        event_from_type, event_from_id, event_to_type, event_to_id)
    if path_key not in created_paths and not event_from_type + event_from_id == event_to_type + event_to_id:
        # created_paths[path_key] = [from_point, to_point]
        path = create_path(from_point, to_point, path_key)
        created_paths[path_key] = path
        assign_path_material(frame, path)
        insert_visibility_key_frame(frame, path)


def highlight_path(frame, event_from_type, event_from_id, event_to_type, event_to_id):
    path_key = path_key_for(
        event_from_type, event_from_id, event_to_type, event_to_id)
    if path_key in created_paths:
        path = created_paths[path_key]
        material = path.data.materials[0]
        if material.name.startswith('path'):
            highlight_path_keyframes(material, frame)


def highlight_path_keyframes(path_material, frame):
    node_tree = path_material.node_tree
    nodes = node_tree.nodes
    value_node = nodes['Value']
    if value_node:
        value_node.outputs[0].default_value = 0.0
        value_node.outputs[0].keyframe_insert(data_path='default_value', frame=frame - 1)
        value_node.outputs[0].default_value = 1.0
        value_node.outputs[0].keyframe_insert(data_path='default_value', frame=frame)
        value_node.outputs[0].default_value = 0.0
        value_node.outputs[0].keyframe_insert(data_path='default_value', frame=frame + 5)


color_map = {
    'color yellow': red_yellow_green_material_yellow_value,
    'color red': red_yellow_green_material_red_value,
    'color green': red_yellow_green_material_green_value
}


def adjust_point_color(event_type, event_id, message, frame):
    if message in color_map:
        point_key = point_key_for(event_type, event_id)
        point_obj = created_points[point_key]
        material_name = point_obj.material_slots[0].material.name

        color_value = color_map[message]
        set_material_value_keyframe(material_name, color_value, frame)


# Setup the scene
scene_setup()

# Read data from file, the file must be sorted by column 1 (time_in_ms)
with open(data_file_path, 'r') as file:
    reader = csv.reader(file)
    row_count = 0
    first_frame_time = 0
    start_time = time.time()
    frame_last_activity = video_first_frame

    for row in reader:
        time_in_ms, event_from_type, event_from_id, event_to_type, event_to_id, message = [
            cell.strip() for cell in row]
        if row_count == 0:
            first_frame_time = int(time_in_ms)
            print(f'First frame time: {first_frame_time}')
        row_count += 1
        frame = video_first_frame + (int(time_in_ms) - first_frame_time) * \
            bpy.context.scene.render.fps // video_playback_speed

        if frame_last_activity + 100 < frame:
            print(f'Frame activity gap, from: {frame_last_activity}, to: {frame}')
        frame_last_activity = frame

        from_point = create_from_point(frame, event_from_type, event_from_id)
        if not event_to_type == 'NA':
            to_point = create_to_point(frame, event_to_type, event_to_id)
            create_from_to_path(frame, event_from_type, event_from_id, event_to_type, event_to_id)

        adjust_point_color(event_from_type, event_from_id, message, frame)
        if message in color_map:
            highlight_path(frame, event_from_type, event_from_id, event_to_type, event_to_id)

    print(f'Use the above from activity gap from values for path_alt_material settings.')

    # Set the number of frames
    frame_end = math.ceil(frame / 100) * 100
    bpy.context.scene.frame_end = frame_end

    frames_for_360 = 15 * video_fps - 1
    view_360(frame_end + 1, frames_for_360)
    print(f'Frames for 360: {frame_end + 1} - {frame_end + 1 + frames_for_360}')

    end_time = time.time()
    print(f'Created {len(created_points)} points')
    print(f'Created {len(created_paths)} paths')
    print(f'Processed {row_count} rows')
    print(f'Frames: {frame}')
    print(f'Finished in {end_time - start_time} seconds')

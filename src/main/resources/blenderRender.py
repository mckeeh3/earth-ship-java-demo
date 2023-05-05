
# This script is used to render the data from the CSV file as a 3D animation in Blender.
# The log data created by the io.example.LogEvent class is used as input for this script.
# The .log file is converted to a .csv file using the following command:
# src/main/resources/log-event-from-log.sh > /tmp/earth-ship-events-unsorted.csv
# sort /tmp/earth-ship-events-unsorted.csv > /tmp/earth-ship-events.csv
# change the data_file_path variable to point to the .csv file

import bpy
import bmesh
import csv
import random
import math
import os
import time
from mathutils import Vector, Quaternion

# Define the path to your data file
data_file_path = '/tmp/earth-ship-events-201-orders.csv'

# Define the positions and radii for the event types
event_positions = {
    'Generator': (0, 0, 0),
    'Region': (3, 3, 0),
    'GeoOrder': (3, 0, 0),
    'Order': (3, -2, 0),
    'ShippingOrder': (5, -2, 0),
    'ShippingOrderItem': (8, -2, 0),
    'OrderSkuItem': (13, -2, 0),
    'StockSkuItem': (20, -2, 0),
    'StockOrderLot': (20, 3.5, 0),
    'StockOrder': (16.5, 1.5, 0),
    'BackOrderedLot': (13, 3.5, 0),
    'Product': (16.5, 4, 0),
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
    'Generator': ("#FFFFFF", 20),
    'Region': ("#20FF11", 10),
    'GeoOrder': ("#FFFA21", 10),
    'Order': ("#10FFA1", 10),
    'ShippingOrder': ("#05E8FF", 10),
    'ShippingOrderItem': ("#1662FF", 10),
    'OrderSkuItem': ("#009CFF", 20),
    'StockSkuItem': ("#FF491C", 15),
    'StockOrderLot': ("#FF9E34", 15),
    'StockOrder':   ("#FFBF00", 15),
    'BackOrderedLot': ("#FF2110", 10),
    'Product': ("#63FF07", 15),
    'ShoppingCart': ("#FF2308", 10),
    "Path": ("#656565", 1),
    "Path highlight": ("#FFF117", 10),
}


# Dictionaries to keep track of created points and paths
created_points = {}
created_paths = {}


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
    print(f"Creating material: {name}, {color}, {strength}")

    # Create a new material
    material = bpy.data.materials.new(name)
    material.use_nodes = True

    # Clear all existing nodes
    nodes = material.node_tree.nodes
    nodes.clear()

    # Add nodes
    output_node = nodes.new("ShaderNodeOutputMaterial")
    emission_node = nodes.new("ShaderNodeEmission")
    emission_node.inputs["Color"].default_value = color
    emission_node.inputs["Strength"].default_value = strength

    # Connect nodes
    links = material.node_tree.links
    links.new(emission_node.outputs["Emission"], output_node.inputs["Surface"])

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
    if hex_color.startswith("#"):
        hex_color = hex_color[1:]

    srgb = tuple(int(hex_color[i:i + 2], 16) / 255 for i in (0, 2, 4))
    linear_rgb = srgb_to_linear(srgb)

    return (*linear_rgb, alpha)


def assign_material(material_name, shape):
    # Create the material if it doesn't already exist
    if material_name not in bpy.data.materials:
        # mat = bpy.data.materials.new(name=material_name)
        material_setting = material_settings[material_name]
        color = hex_to_rgba(material_setting[0])
        strength = material_setting[1]
        mat = create_emission_material(
            name=material_name, color=color, strength=strength)
    else:
        mat = bpy.data.materials[material_name]

    # Assign the material to the shape
    if len(shape.data.materials) == 0:
        shape.data.materials.append(mat)
    else:
        shape.data.materials[0] = mat


# Create path keys using event_from_type and event_to_type ordered alphabetically
def create_path_key(event_from_type, event_from_id, event_to_type, event_to_id):
    if event_from_type > event_to_type:
        return f"{event_to_type}_{event_to_id}_{event_from_type}_{event_from_id}"
    return f"{event_from_type}_{event_from_id}_{event_to_type}_{event_to_id}"


# Animate the path to appear at the specified time
def insert_key_frame(frame, obj):
    if frame > 1:
        obj.hide_render = True
        obj.hide_viewport = True
        obj.keyframe_insert(data_path="hide_render", frame=frame - 1)
        obj.keyframe_insert(data_path="hide_viewport", frame=frame - 1)
        obj.hide_render = False
        obj.hide_viewport = False
        obj.keyframe_insert(data_path="hide_render", frame=frame)
        obj.keyframe_insert(data_path="hide_viewport", frame=frame)
        obj["frame"] = frame


# Read data from file
with open(data_file_path, 'r') as file:
    reader = csv.reader(file)
    row_count = 0
    first_frame_time = 0
    start_time = time.time()

    for row in reader:
        time_in_ms, event_from_type, event_from_id, event_to_type, event_to_id = [
            cell.strip() for cell in row]
        if row_count == 0:
            first_frame_time = int(time_in_ms)
        row_count += 1
        frame = 1 + (int(time_in_ms) - first_frame_time) * \
            bpy.context.scene.render.fps // 500

        # Check if the from_point already exists, otherwise create it
        from_key = f"{event_from_type}_{event_from_id}"
        if from_key in created_points:
            from_point = created_points[from_key].location
        else:
            from_location = random_point_in_sphere(
                event_positions[event_from_type], event_radii[event_from_type])
            from_point_obj = create_point(
                from_location, from_key, event_from_type)
            created_points[from_key] = from_point_obj
            from_point = from_location
            insert_key_frame(frame, from_point_obj)

        # Check if the to_point already exists, otherwise create it
        to_key = f"{event_to_type}_{event_to_id}"
        if to_key in created_points:
            to_point = created_points[to_key].location
        else:
            to_location = random_point_in_sphere(
                event_positions[event_to_type], event_radii[event_to_type])
            to_point_obj = create_point(to_location, to_key, event_to_type)
            created_points[to_key] = to_point_obj
            to_point = to_location
            insert_key_frame(frame, to_point_obj)

        # Create the path (line) between the points
        path_key = create_path_key(
            event_from_type, event_from_id, event_to_type, event_to_id)
        if path_key not in created_paths:
            created_paths[path_key] = [from_point, to_point]
            path = create_path(from_point, to_point, path_key)
            assign_material("Path", path)
            insert_key_frame(frame, path)

    end_time = time.time()
    print(f"Processed {row_count} rows")
    print(f"Frames: {frame}")
    print(f"Finished in {end_time - start_time} seconds")

#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = [
#   "resvg-py==0.5.0",
#   "numpy==2.5.4",
#   "scipy",
#   "pillow==13.0.0",
#   "scikit-image",
#   "matplotlib",
# ]
# ///
"""Builds the banana that stands beside a device's 3D model, and beside it in AR, for scale.

    uv run scripts/models/banana_for_scale.py

The drawing, drawings/banana_icon.svg ("Banana icon" by Keistutis on Openclipart, CC0,
https://openclipart.org/detail/189457/banana), is rendered without its drop shadow and stood on
end, stem up. Its outline is inflated into a round banana at real size, about 100 x 185 x 44 mm,
and the rendered drawing is laid on its front (+Z) and back. The front carries an oval white
label with the Meshtastic logo, like a grocery store sticker. Y is up, units are meters and the
banana stands on y = 0, like the flasher's device models. The credit goes in the file's
asset.copyright, which the viewer shows. It writes
core/resources/src/commonMain/composeResources/files/models/banana_for_scale.glb.

The same banana as the Apple app's (Meshtastic-Apple scripts/cinema/banana_for_scale.py), written
as a GLB instead of a USDZ.
"""

import io
import json
import os
import struct

import numpy as np
import resvg_py
from matplotlib.path import Path as Outline
from PIL import Image, ImageDraw
from scipy import ndimage
from scipy.spatial import Delaunay
from skimage import measure

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
DRAWING = os.path.join(HERE, "drawings", "banana_icon.svg")
LOGO = os.path.join(HERE, "drawings", "meshtastic_logo_black.svg")
OUT = os.path.join(
    ROOT, "core", "resources", "src", "commonMain", "composeResources", "files", "models", "banana_for_scale.glb"
)

# Tip to the top of the stem, standing up: a medium banana.
HEIGHT_M = 0.185
# Pixels between the mesh's inside points, about a millimeter.
GRID = 10
# The label, about the size of a real banana sticker: its long side runs along the banana.
LABEL_LENGTH_M = 0.026
LABEL_WIDTH_M = 0.017
# The texture is drawn at the mesh's resolution and stored at this fraction of it, as a JPEG.
TEXTURE_SCALE = 0.5
TEXTURE_QUALITY = 88
COPYRIGHT = (
    'Banana for scale, made for Meshtastic from "Banana icon" by Keistutis, Openclipart, '
    "dedicated to the public domain (CC0)."
)


def render():
    """The drawing as RGBA, without the shadow under it, turned so the stem is at the top."""
    with open(DRAWING) as drawing:
        svg = drawing.read()
    shadow = 'style="fill:url(#radialGradient3907)"'
    if shadow not in svg:
        raise SystemExit("the drawing's shadow (radialGradient3907) wasn't found; check the drawing")
    svg = svg.replace(shadow, 'style="display:none;fill:url(#radialGradient3907)"')
    png = bytes(resvg_py.svg_to_bytes(svg_string=svg, width=2048))
    image = np.asarray(Image.open(io.BytesIO(png)).convert("RGBA"))
    # It lies with the stem at the top left; a quarter turn clockwise stands it up, stem on top.
    return np.rot90(image, k=-1).copy()


def silhouette(image):
    """The banana's shape: the solid part of the drawing, its soft blurred edges left out."""
    mask = image[:, :, 3] > 170
    labels, count = ndimage.label(mask)
    sizes = ndimage.sum(mask, labels, range(1, count + 1))
    mask = labels == (int(np.argmax(sizes)) + 1)
    return ndimage.binary_fill_holes(mask)


def labeled(texture, mask, meters_per_px):
    """The texture with an oval white label halfway up the banana, in the middle of its width,
    turned to run along it, with the Meshtastic logo upright on it."""
    inside = ndimage.distance_transform_edt(mask)
    rows, cols = np.nonzero(mask)
    row = (rows.min() + rows.max()) // 2
    col = int(np.argmax(inside[row]))
    # The banana's direction there: the long axis of the banana within a few centimeters.
    reach = 0.03 / meters_per_px
    near = (rows - row) ** 2 + (cols - col) ** 2 < reach**2
    spread = np.cov(np.vstack([cols[near], rows[near]]))
    values, vectors = np.linalg.eigh(spread)
    along = vectors[:, np.argmax(values)]
    # Pointing up the banana, so the logo reads from the tip toward the stem.
    if along[1] > 0:
        along = -along
    angle = np.degrees(np.arctan2(-along[1], along[0]))

    # Drawn four times larger and scaled down, for smooth edges.
    scale = 4
    length = int(round(LABEL_LENGTH_M / meters_per_px)) * scale
    width = int(round(LABEL_WIDTH_M / meters_per_px)) * scale
    label = Image.new("RGBA", (length, width), (0, 0, 0, 0))
    ImageDraw.Draw(label).ellipse([0, 0, length - 1, width - 1], fill=(250, 250, 247, 255))
    with open(LOGO) as drawing:
        logo_svg = drawing.read()
    # The label runs up the standing banana, so the logo is drawn across it and turned to stand
    # upright, as wide as fits across the oval.
    logo_width = int(width * 0.74)
    logo = Image.open(io.BytesIO(bytes(resvg_py.svg_to_bytes(svg_string=logo_svg, width=logo_width)))).convert("RGBA")
    logo = logo.rotate(-90, expand=True)
    label.alpha_composite(logo, ((length - logo.width) // 2, (width - logo.height) // 2))
    label = label.resize((length // scale, width // scale), Image.LANCZOS)
    label = label.rotate(angle, resample=Image.BICUBIC, expand=True)

    out = texture.convert("RGBA")
    out.alpha_composite(label, (int(col - label.width / 2), int(row - label.height / 2)))
    return out.convert("RGB")


def build():
    image = render()
    mask = silhouette(image)
    rows, cols = np.nonzero(mask)
    meters_per_px = HEIGHT_M / (rows.max() - rows.min())
    # Cropped to the banana with a margin, so the texture is all banana.
    pad = 8
    top, left = max(rows.min() - pad, 0), max(cols.min() - pad, 0)
    bottom, right = min(rows.max() + pad, mask.shape[0] - 1), min(cols.max() + pad, mask.shape[1] - 1)
    image, mask = image[top : bottom + 1, left : right + 1], mask[top : bottom + 1, left : right + 1]
    h, w = mask.shape

    # Round in section: at each point, the height of a circle as wide as the banana is there.
    inside = ndimage.distance_transform_edt(mask)
    half_width = ndimage.maximum_filter(inside, size=int(inside.max() * 2.2))
    depth = np.sqrt(np.clip(2 * inside * half_width - inside**2, 0, None))

    # The outline, and points on a grid inside it, kept clear of the outline so no triangle is
    # a sliver; triangles between them.
    contour = max(measure.find_contours(mask.astype(float), 0.5), key=len)
    contour = measure.approximate_polygon(contour, tolerance=0.6)[:-1]
    gy, gx = np.mgrid[GRID / 2 : h : GRID, GRID / 2 : w : GRID]
    grid = np.column_stack([gy.ravel(), gx.ravel()])
    cells = grid.astype(int)
    grid = grid[inside[cells[:, 0], cells[:, 1]] > GRID * 0.6]
    points = np.vstack([contour, grid])  # (row, column)
    edge = len(contour)
    triangles = Delaunay(points[:, ::-1]).simplices
    centers = points[triangles].mean(axis=1)
    faces = triangles[Outline(contour[:, ::-1]).contains_points(centers[:, ::-1])]

    # The front and back meet at the outline, where the banana has no depth.
    z = ndimage.map_coordinates(depth, points.T, order=1) * meters_per_px
    z[:edge] = 0
    count = len(points)
    x = (points[:, 1] - w / 2) * meters_per_px
    y = (h - 1 - points[:, 0]) * meters_per_px
    vertices = np.vstack([np.column_stack([x, y, z]), np.column_stack([x, y, -z])[edge:]])
    back = np.concatenate([np.arange(edge), np.arange(count, 2 * count - edge)])
    faces = np.vstack([faces, back[faces][:, ::-1]])
    # The texture is two pictures side by side: the front with the label on the left, the back
    # without it on the right. glTF's texture coordinates start at the top left.
    uv = np.column_stack([points[:, 1] / (w - 1) / 2, points[:, 0] / (h - 1)])
    uvs = np.vstack([uv, uv[edge:] + [0.5, 0]])
    # Stood on its lowest point, centered across and front to back.
    vertices[:, 1] -= vertices[:, 1].min()
    vertices[:, 0] -= (vertices[:, 0].min() + vertices[:, 0].max()) / 2

    normals = smooth_normals(vertices, faces)
    # glTF draws counterclockwise faces; ones wound so the front's normals point back would be
    # culled from the front: turn them all.
    if normals[edge:count, 2].mean() < 0:
        faces = faces[:, ::-1]
        normals = -normals

    # The back's faces get their own copy of the outline's points, carrying the back's half of
    # the texture; the normals were worked out with the two sides joined, so the rim stays smooth.
    front_faces = len(faces) // 2
    rim = np.arange(edge)
    rim_copy = len(vertices) + rim
    vertices = np.vstack([vertices, vertices[rim]])
    normals = np.vstack([normals, normals[rim]])
    uvs = np.vstack([uvs, uv[rim] + [0.5, 0]])
    back_faces = faces[front_faces:]
    faces[front_faces:] = np.where(back_faces < edge, rim_copy[np.minimum(back_faces, edge - 1)], back_faces)

    # The drawing as the texture, its see-through edges filled with the nearest banana color so
    # the rim doesn't pick up black.
    rgb = image[:, :, :3]
    solid = image[:, :, 3] > 40
    nearest = ndimage.distance_transform_edt(~solid, return_distances=False, return_indices=True)
    peel = Image.fromarray(rgb[nearest[0], nearest[1]].astype(np.uint8), "RGB")
    texture = Image.new("RGB", (w * 2, h))
    texture.paste(labeled(peel, mask, meters_per_px), (0, 0))
    texture.paste(peel, (w, 0))
    return vertices, normals, uvs, faces, texture


def smooth_normals(vertices, faces):
    corners = vertices[faces]
    face_normals = np.cross(corners[:, 1] - corners[:, 0], corners[:, 2] - corners[:, 0])
    normals = np.zeros_like(vertices)
    for k in range(3):
        np.add.at(normals, faces[:, k], face_normals)
    lengths = np.linalg.norm(normals, axis=1, keepdims=True)
    return normals / np.where(lengths > 0, lengths, 1)


def padded(data, fill=b"\0"):
    return data + fill * (-len(data) % 4)


def write(vertices, normals, uvs, faces, texture, out):
    """One mesh, one material and its texture, all in the GLB's binary chunk."""
    size = (round(texture.width * TEXTURE_SCALE), round(texture.height * TEXTURE_SCALE))
    picture = io.BytesIO()
    texture.resize(size, Image.LANCZOS).save(picture, "JPEG", quality=TEXTURE_QUALITY, optimize=True)
    index_type = np.uint16 if len(vertices) < 65536 else np.uint32
    blobs = [
        vertices.astype(np.float32).tobytes(),
        normals.astype(np.float32).tobytes(),
        uvs.astype(np.float32).tobytes(),
        faces.astype(index_type).tobytes(),
        picture.getvalue(),
    ]
    views, binary = [], b""
    for blob in blobs:
        views.append({"buffer": 0, "byteOffset": len(binary), "byteLength": len(blob)})
        binary += padded(blob)
    for view, target in zip(views, [34962, 34962, 34962, 34963]):
        view["target"] = target
    count = len(vertices)
    gltf = {
        "asset": {"version": "2.0", "generator": "banana_for_scale.py", "copyright": COPYRIGHT},
        "scene": 0,
        "scenes": [{"nodes": [0]}],
        "nodes": [{"name": "BananaForScale", "mesh": 0}],
        "meshes": [
            {
                "name": "Banana",
                "primitives": [
                    {"attributes": {"POSITION": 0, "NORMAL": 1, "TEXCOORD_0": 2}, "indices": 3, "material": 0}
                ],
            }
        ],
        "materials": [
            {
                "name": "Peel",
                "pbrMetallicRoughness": {
                    "baseColorTexture": {"index": 0},
                    "metallicFactor": 0.0,
                    "roughnessFactor": 0.5,
                },
            }
        ],
        "textures": [{"source": 0, "sampler": 0}],
        "samplers": [{"magFilter": 9729, "minFilter": 9987, "wrapS": 33071, "wrapT": 33071}],
        "images": [{"bufferView": 4, "mimeType": "image/jpeg"}],
        "accessors": [
            {
                "bufferView": 0,
                "componentType": 5126,
                "count": count,
                "type": "VEC3",
                "min": vertices.min(axis=0).astype(np.float32).tolist(),
                "max": vertices.max(axis=0).astype(np.float32).tolist(),
            },
            {"bufferView": 1, "componentType": 5126, "count": count, "type": "VEC3"},
            {"bufferView": 2, "componentType": 5126, "count": count, "type": "VEC2"},
            {
                "bufferView": 3,
                "componentType": 5123 if index_type is np.uint16 else 5125,
                "count": faces.size,
                "type": "SCALAR",
            },
        ],
        "bufferViews": views,
        "buffers": [{"byteLength": len(binary)}],
    }
    text = padded(json.dumps(gltf, separators=(",", ":"), ensure_ascii=False).encode("utf-8"), b" ")
    chunks = struct.pack("<II", len(text), 0x4E4F534A) + text + struct.pack("<II", len(binary), 0x004E4942) + binary
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "wb") as glb:
        glb.write(struct.pack("<III", 0x46546C67, 2, 12 + len(chunks)) + chunks)


def main():
    vertices, normals, uvs, faces, texture = build()
    write(vertices, normals, uvs, faces, texture, OUT)
    size = (vertices.max(axis=0) - vertices.min(axis=0)) * 1000
    print(
        f"{os.path.relpath(OUT, ROOT)}: {size[0]:.0f} x {size[1]:.0f} x {size[2]:.0f} mm, {len(vertices)} points, "
        f"{len(faces)} triangles, {os.path.getsize(OUT) // 1024} KB"
    )


if __name__ == "__main__":
    main()

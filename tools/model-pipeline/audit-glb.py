#!/usr/bin/env python3
"""Emit deterministic structural metrics for a binary glTF asset."""

import argparse
import hashlib
import json
import struct
from pathlib import Path


COMPONENT_BYTES = {5120: 1, 5121: 1, 5122: 2, 5123: 2, 5125: 4, 5126: 4}
TYPE_WIDTH = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}


def read_document(path: Path) -> tuple[bytes, dict]:
    payload = path.read_bytes()
    magic, version, total = struct.unpack_from("<4sII", payload)
    if magic != b"glTF" or version != 2 or total != len(payload):
        raise SystemExit(f"Invalid GLB: {path}")
    json_size, json_kind = struct.unpack_from("<I4s", payload, 12)
    if json_kind != b"JSON":
        raise SystemExit(f"Missing JSON chunk: {path}")
    return payload, json.loads(payload[20 : 20 + json_size])


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("glb", type=Path)
    parser.add_argument("--require-clips", default="")
    args = parser.parse_args()
    payload, document = read_document(args.glb)
    accessors = document.get("accessors", [])
    primitives = [primitive for mesh in document.get("meshes", []) for primitive in mesh.get("primitives", [])]
    animations = [animation.get("name", "") for animation in document.get("animations", [])]
    required = {name for name in args.require_clips.split(",") if name}
    missing = sorted(required.difference(animations))
    if missing:
        raise SystemExit("Missing clips: " + ", ".join(missing))
    triangles = 0
    vertices = 0
    for primitive in primitives:
        position = primitive.get("attributes", {}).get("POSITION")
        if position is not None:
            vertices += accessors[position]["count"]
        indices = primitive.get("indices")
        if indices is not None and primitive.get("mode", 4) == 4:
            triangles += accessors[indices]["count"] // 3
    report = {
        "file": str(args.glb),
        "sha256": hashlib.sha256(payload).hexdigest(),
        "bytes": len(payload),
        "meshes": len(document.get("meshes", [])),
        "primitives": len(primitives),
        "vertices": vertices,
        "triangles": triangles,
        "skins": len(document.get("skins", [])),
        "joints": [len(skin.get("joints", [])) for skin in document.get("skins", [])],
        "animations": animations,
        "textures": len(document.get("textures", [])),
        "extensionsRequired": document.get("extensionsRequired", []),
    }
    print(json.dumps(report, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()

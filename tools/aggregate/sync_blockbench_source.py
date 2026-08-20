#!/usr/bin/env python3
"""Rebuild the editable Blockbench source from the shipped GeckoLib geometry."""

import json
from pathlib import Path
import uuid

ROOT = Path(__file__).resolve().parents[2]
GEO = ROOT / "src/main/resources/assets/frozendawn/geo/aggregate.geo.json"
OUT = ROOT / "tools/aggregate/aggregate.bbmodel"
NAMESPACE = uuid.UUID("e6883c6e-77c8-4cf0-92c9-24759506fbec")


def stable_id(name):
    return str(uuid.uuid5(NAMESPACE, name))


def main():
    geometry = json.loads(GEO.read_text())["minecraft:geometry"][0]
    elements = []
    groups = {}
    for bone in geometry["bones"]:
        children = []
        pivot = bone.get("pivot", [0, 0, 0])
        rotation = bone.get("rotation", [0, 0, 0])
        for index, cube in enumerate(bone.get("cubes", [])):
            cube_id = stable_id(f"cube:{bone['name']}:{index}")
            start = cube["origin"]
            size = cube["size"]
            element = {
                "name": f"{bone['name']}_{index}",
                "box_uv": True,
                "rescale": False,
                "locked": False,
                "from": start,
                "to": [start[i] + size[i] for i in range(3)],
                "autouv": 0,
                "color": index % 8,
                "origin": cube.get("pivot", pivot),
                "rotation": cube.get("rotation", [0, 0, 0]),
                "uv_offset": cube.get("uv", [0, 0]),
                "inflate": cube.get("inflate", 0),
                "uuid": cube_id,
            }
            elements.append(element)
            children.append(cube_id)
        groups[bone["name"]] = {
            "name": bone["name"],
            "origin": pivot,
            "rotation": rotation,
            "color": 0,
            "uuid": stable_id(f"bone:{bone['name']}"),
            "export": True,
            "isOpen": True,
            "locked": False,
            "visibility": True,
            "autouv": 0,
            "children": children,
            "parent": bone.get("parent"),
        }
    roots = []
    for group in groups.values():
        parent = group.pop("parent")
        if parent and parent in groups:
            groups[parent]["children"].append(group)
        else:
            roots.append(group)
    model = {
        "meta": {
            "format_version": "4.10",
            "model_format": "bedrock",
            "box_uv": True,
        },
        "name": "aggregate",
        "model_identifier": geometry["description"]["identifier"],
        "visible_box": [7.5, 4.5, 0, 1.5, 0],
        "resolution": {"width": 256, "height": 256},
        "elements": elements,
        "outliner": roots,
        "textures": [{
            "path": "../../src/main/resources/assets/frozendawn/textures/entity/aggregate.png",
            "name": "aggregate.png",
            "folder": "entity",
            "namespace": "frozendawn",
            "id": "0",
            "uuid": stable_id("texture:aggregate"),
            "particle": False,
            "render_mode": "default",
            "visible": True,
        }],
    }
    OUT.write_text(json.dumps(model, indent=2) + "\n")
    print(f"Wrote {OUT.relative_to(ROOT)} with {len(elements)} editable cubes")


if __name__ == "__main__":
    main()

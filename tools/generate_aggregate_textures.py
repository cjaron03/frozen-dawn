#!/usr/bin/env python3
"""Generate and validate the Aggregate's deterministic hand-authored atlas."""

from pathlib import Path
import json
import random
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ENTITY = ROOT / "src/main/resources/assets/frozendawn/textures/entity"
BLOCK = ROOT / "src/main/resources/assets/frozendawn/textures/block"
ATLAS = ENTITY / "aggregate.png"
FRAGMENT = ENTITY / "aggregate_fragment.png"

REGIONS = {
    "tissue": (0, 0, 128, 148),
    "bone": (128, 0, 176, 180),
    "lineage": (176, 0, 252, 204),
    "debris": (0, 180, 176, 252),
    "core": (208, 208, 252, 252),
}


def shade(base, spread, rng):
    value = rng.randint(-spread, spread)
    return tuple(max(0, min(255, channel + value)) for channel in base) + (255,)


def generate_atlas():
    rng = random.Random(0xA66E6A7E)
    image = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    pixels = image.load()
    palettes = {
        "tissue": ((38, 40, 39), 15),
        "bone": ((183, 181, 165), 22),
        "lineage": ((158, 171, 165), 24),
        "debris": ((101, 79, 64), 23),
        "core": ((166, 186, 163), 20),
    }
    for name, (x0, y0, x1, y1) in REGIONS.items():
        base, spread = palettes[name]
        for y in range(y0, y1):
            for x in range(x0, x1):
                value = shade(base, spread, rng)
                if ((x * 17 + y * 31 + rng.randrange(29)) % 47) < 3:
                    value = shade((24, 27, 27), 8, rng)
                pixels[x, y] = value
    # Bone pitting, embedded charcoal, and rust inclusions keep the broad shape legible.
    for _ in range(620):
        x = rng.randrange(128, 252)
        y = rng.randrange(0, 204)
        radius = rng.choice((1, 1, 1, 2))
        color = shade((52, 56, 54), 12, rng)
        for yy in range(max(0, y - radius), min(256, y + radius + 1)):
            for xx in range(max(0, x - radius), min(256, x + radius + 1)):
                pixels[xx, yy] = color
    for _ in range(230):
        x = rng.randrange(0, 176)
        y = rng.randrange(180, 252)
        pixels[x, y] = shade((141, 100, 69), 18, rng)
    # The convergence core is muted and warm-white, never cyan or Thae Iven blue.
    for y in range(208, 252):
        for x in range(208, 252):
            distance = ((x - 232) ** 2 + (y - 232) ** 2) ** 0.5
            glow = max(0.0, 1.0 - distance / 34.0)
            pixels[x, y] = (
                int(112 + 112 * glow), int(126 + 106 * glow),
                int(108 + 88 * glow), 255)
    image.save(ATLAS)


def generate_fragment():
    source = Image.open(ENTITY / "frostmite.png").convert("RGBA")
    output = Image.new("RGBA", source.size)
    for y in range(source.height):
        for x in range(source.width):
            r, g, b, a = source.getpixel((x, y))
            if a == 0:
                output.putpixel((x, y), (0, 0, 0, 0))
                continue
            luminance = (r * 3 + g * 4 + b) // 8
            output.putpixel((x, y), (
                max(24, min(208, int(luminance * 0.86))),
                max(25, min(205, int(luminance * 0.84))),
                max(24, min(190, int(luminance * 0.78))), a))
    output.save(FRAGMENT)


def generate_block_texture(name, base, bone_weight=0.0, core=False):
    rng = random.Random(0xA66E6A7E ^ sum(ord(char) for char in name))
    image = Image.new("RGBA", (16, 16), base + (255,))
    pixels = image.load()
    for y in range(16):
        for x in range(16):
            color = shade(base, 16, rng)
            if (x * 11 + y * 17 + rng.randrange(19)) % 23 < 3:
                color = shade((38, 41, 40), 8, rng)
            if bone_weight > 0.0 and rng.random() < bone_weight:
                color = shade((184, 181, 164), 17, rng)
            pixels[x, y] = color
    if core:
        for y in range(3, 13):
            for x in range(3, 13):
                distance = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
                if distance < 5.2:
                    strength = max(0.0, 1.0 - distance / 5.2)
                    pixels[x, y] = (
                        int(111 + 112 * strength), int(122 + 105 * strength),
                        int(105 + 88 * strength), 255)
    image.save(BLOCK / f"{name}.png")


def generate_blocks():
    BLOCK.mkdir(parents=True, exist_ok=True)
    generate_block_texture("aggregate_residue", (66, 65, 61), 0.08)
    generate_block_texture("aggregate_mass", (72, 72, 67), 0.28)
    generate_block_texture("aggregate_rib", (151, 148, 135), 0.55)
    generate_block_texture("aggregate_rib_top", (94, 92, 84), 0.32)
    generate_block_texture("aggregate_temporary_mass", (54, 55, 52), 0.18)
    generate_block_texture("inert_convergence_core", (45, 47, 45), 0.12, True)


def validate():
    image = Image.open(ATLAS)
    assert image.size == (256, 256), image.size
    names = list(REGIONS)
    for index, name in enumerate(names):
        x0, y0, x1, y1 = REGIONS[name]
        assert 0 <= x0 < x1 <= 256 and 0 <= y0 < y1 <= 256
        for other_name in names[index + 1:]:
            ox0, oy0, ox1, oy1 = REGIONS[other_name]
            overlap = x0 < ox1 and x1 > ox0 and y0 < oy1 and y1 > oy0
            assert not overlap, f"atlas regions overlap: {name}/{other_name}"
    # Outer corners remain transparent padding for accidental out-of-bounds detection.
    for point in ((255, 0), (0, 255), (255, 255)):
        assert image.getpixel(point)[3] == 0, point

    geometry_path = ROOT / "src/main/resources/assets/frozendawn/geo/aggregate.geo.json"
    geometry = json.loads(geometry_path.read_text())["minecraft:geometry"][0]
    cube_count = 0
    for bone in geometry["bones"]:
        for cube in bone.get("cubes", []):
            cube_count += 1
            u, v = cube.get("uv", (0, 0))
            width, height, depth = cube["size"]
            # Bedrock box UV uses a 2(w+d) by h+d rectangle. Different cubes
            # deliberately reuse material regions, but no island may wrap.
            assert u >= 0 and v >= 0, (bone["name"], u, v)
            assert u + 2 * (width + depth) <= 256, (bone["name"], u, width, depth)
            assert v + height + depth <= 256, (bone["name"], v, height, depth)
    assert cube_count <= 180, cube_count
    assert len(geometry["bones"]) <= 60, len(geometry["bones"])


if __name__ == "__main__":
    ENTITY.mkdir(parents=True, exist_ok=True)
    generate_atlas()
    generate_fragment()
    generate_blocks()
    validate()
    print(f"Wrote {ATLAS.relative_to(ROOT)} and {FRAGMENT.relative_to(ROOT)}")

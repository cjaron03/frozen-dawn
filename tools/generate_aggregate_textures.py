#!/usr/bin/env python3
"""Generate and validate the Aggregate's deterministic hand-authored atlas."""

from pathlib import Path
import json
import random
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ENTITY = ROOT / "src/main/resources/assets/frozendawn/textures/entity"
BLOCK = ROOT / "src/main/resources/assets/frozendawn/textures/block"
ATLAS = ENTITY / "aggregate.png"
FRAGMENT = ENTITY / "aggregate_fragment.png"

REGIONS = {
    "tissue": (0, 0, 128, 148),
    "bone": (128, 0, 176, 180),
    "lineage": (176, 0, 252, 204),
    "face": (0, 148, 128, 180),
    "debris": (0, 180, 176, 252),
    "void": (176, 204, 208, 252),
    "core": (208, 208, 252, 252),
}


def shade(base, spread, rng):
    value = rng.randint(-spread, spread)
    return tuple(max(0, min(255, channel + value)) for channel in base) + (255,)


def generate_atlas():
    rng = random.Random(0xA66E6A7E)
    # Large Minecraft creatures read through broad value groups, not per-pixel noise.
    # Start with a mid-value connective material so every box UV remains visible.
    image = Image.new("RGBA", (256, 256), (78, 82, 80, 255))
    pixels = image.load()
    palettes = {
        "tissue": ((73, 77, 75), (104, 110, 105), (45, 48, 47)),
        "bone": ((194, 195, 181), (222, 220, 201), (121, 124, 117)),
        "lineage": ((151, 169, 160), (190, 204, 188), (89, 104, 99)),
        "face": ((163, 160, 147), (211, 207, 187), (83, 83, 78)),
        "debris": ((126, 91, 68), (164, 122, 87), (70, 58, 51)),
        "void": ((18, 20, 20), (37, 40, 39), (7, 8, 8)),
        "core": ((179, 193, 161), (236, 231, 187), (101, 116, 96)),
    }
    draw = ImageDraw.Draw(image)
    for name, (x0, y0, x1, y1) in REGIONS.items():
        base, highlight, shadow = palettes[name]
        draw.rectangle((x0, y0, x1 - 1, y1 - 1), fill=base + (255,))

        # Four-to-eight-pixel plates give the material a Minecraft-scale grain.
        cell = 8 if name in ("tissue", "debris") else 6
        for y in range(y0, y1, cell):
            for x in range(x0, x1, cell):
                tone = rng.choice((base, base, base, highlight, shadow))
                inset = rng.choice((0, 1, 1, 2))
                draw.rectangle((min(x1 - 1, x + inset), min(y1 - 1, y + inset),
                                min(x1 - 1, x + cell - 1),
                                min(y1 - 1, y + cell - 1)),
                               fill=shade(tone, 7, rng))

        # Material planes get a light top/left edge and a dark lower edge.
        draw.line((x0, y0, x1 - 1, y0), fill=shade(highlight, 5, rng), width=2)
        draw.line((x0, y0, x0, y1 - 1), fill=shade(highlight, 5, rng), width=1)
        draw.line((x0, y1 - 2, x1 - 1, y1 - 2), fill=shade(shadow, 5, rng), width=2)

        # A few irregular stepped fissures, deliberately thin and non-uniform.
        fissures = 7 if name == "tissue" else 4
        for _ in range(fissures):
            x = rng.randrange(x0 + 3, max(x0 + 4, x1 - 8))
            y = rng.randrange(y0 + 2, max(y0 + 3, y1 - 8))
            points = [(x, y)]
            for _ in range(rng.randint(2, 5)):
                x += rng.choice((-3, -2, 2, 3, 4))
                y += rng.choice((2, 3, 4, 5))
                points.append((max(x0, min(x1 - 1, x)), max(y0, min(y1 - 1, y))))
            draw.line(points, fill=shade(shadow, 4, rng), width=1)

    # Pale ossified islands keep the body readable behind the separate mask assembly.
    for _ in range(18):
        x = rng.randrange(7, 112)
        y = rng.randrange(5, 138)
        width = rng.randrange(5, 15)
        height = rng.randrange(3, 9)
        draw.rectangle((x, y, min(126, x + width), min(146, y + height)),
                       fill=shade((160, 162, 151), 12, rng))
        draw.line((x, y, min(126, x + width), y),
                  fill=shade((206, 204, 187), 8, rng), width=1)

    # The face is carved material rather than an image of a face. Broad stepped
    # planes and thin fractures survive the box UV wrapping at Minecraft scale.
    draw.rectangle((0, 148, 127, 179), fill=(158, 157, 146, 255))
    for y in range(148, 180, 8):
        offset = 4 if (y // 8) % 2 else 0
        for x in range(-offset, 128, 12):
            tone = rng.choice(((180, 178, 164), (137, 139, 133), (199, 195, 176)))
            draw.rectangle((max(0, x), y, min(127, x + 10), min(179, y + 6)),
                           fill=shade(tone, 6, rng))
    for points in (
            [(7, 150), (18, 156), (15, 164), (29, 173)],
            [(45, 148), (41, 155), (53, 160), (49, 178)],
            [(91, 151), (84, 160), (96, 166), (88, 179)],
            [(116, 149), (107, 157), (113, 166), (103, 176)]):
        draw.line(points, fill=(72, 73, 70, 255), width=1)

    # Socket and mouth backing must remain nearly black under the renderer's
    # minimum light so the recesses read from the boss-fight distance.
    draw.rectangle((176, 204, 207, 251), fill=(12, 14, 14, 255))
    for _ in range(12):
        x = rng.randrange(178, 205)
        y = rng.randrange(206, 249)
        draw.rectangle((x, y, min(207, x + rng.randrange(1, 4)),
                        min(251, y + rng.randrange(1, 3))),
                       fill=shade((31, 34, 33), 4, rng))

    # Rusted architecture remains in coherent slabs rather than orange static.
    for _ in range(13):
        x = rng.randrange(4, 158)
        y = rng.randrange(184, 239)
        width = rng.randrange(8, 24)
        height = rng.randrange(4, 11)
        draw.rectangle((x, y, min(174, x + width), min(250, y + height)),
                       fill=shade((143, 101, 72), 10, rng))
        draw.line((x, y, min(174, x + width), y), fill=(190, 145, 101, 255))
    # The convergence core is muted and warm-white, never cyan or Thae Iven blue.
    for y in range(208, 252):
        for x in range(208, 252):
            distance = ((x - 232) ** 2 + (y - 232) ** 2) ** 0.5
            glow = max(0.0, 1.0 - distance / 34.0)
            pixels[x, y] = (
                int(132 + 108 * glow), int(145 + 96 * glow),
                int(119 + 91 * glow), 255)
    # Padding markers stay transparent and no authored UV may touch them.
    for point in ((255, 0), (0, 255), (255, 255)):
        pixels[point[0], point[1]] = (0, 0, 0, 0)
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
            for yy in range(int(v), int(v + height + depth)):
                for xx in range(int(u), int(u + 2 * (width + depth))):
                    assert image.getpixel((xx, yy))[3] > 0, (bone["name"], xx, yy)
    assert cube_count <= 180, cube_count
    assert len(geometry["bones"]) <= 60, len(geometry["bones"])


if __name__ == "__main__":
    ENTITY.mkdir(parents=True, exist_ok=True)
    generate_atlas()
    generate_fragment()
    generate_blocks()
    validate()
    print(f"Wrote {ATLAS.relative_to(ROOT)} and {FRAGMENT.relative_to(ROOT)}")

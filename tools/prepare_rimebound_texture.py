#!/usr/bin/env python3
"""Generate the original 64x64 dirty-rime texture used by the Rimebound model."""

from pathlib import Path
from random import Random

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/frozendawn/textures/entity/rimebound.png"
EFFECT_OUT = ROOT / "src/main/resources/assets/frozendawn/textures/mob_effect/rimebound_encasement.png"
rng = Random(0x52494D45)

image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
pixels = image.load()

# Vanilla humanoid base UV islands. The paint is original; only the standard layout is used.
base_islands = [
    (8, 0, 16, 8), (16, 0, 24, 8), (0, 8, 32, 16),
    (20, 16, 36, 20), (16, 20, 40, 32),
    (44, 16, 52, 20), (40, 20, 56, 32),
    (4, 16, 12, 20), (0, 20, 16, 32),
    (36, 48, 44, 52), (32, 52, 48, 64),
    (20, 48, 28, 52), (16, 52, 32, 64),
]

for left, top, right, bottom in base_islands:
    for y in range(top, bottom):
        for x in range(left, right):
            grain = rng.randrange(-12, 13)
            shade = int((y - top) / max(1, bottom - top) * 16)
            base = 126 + grain - shade
            pixels[x, y] = (base - 9, base - 5, min(255, base + 2), 255)

# Dirty ice staining and trapped grit confined to the actual body islands.
painted = [(x, y) for y in range(64) for x in range(64) if pixels[x, y][3] > 0]
for x, y in rng.sample(painted, min(190, len(painted))):
    if rng.random() < 0.72:
        value = rng.randrange(45, 89)
        pixels[x, y] = (value, value + 2, value + 5, 255)

# Head-front sockets in vanilla humanoid UV space, with pin-prick pupils.
for x in range(9, 12):
    for y in range(10, 13):
        pixels[x, y] = (29, 31, 34, 255)
for x in range(13, 16):
    for y in range(10, 13):
        pixels[x, y] = (25, 27, 30, 255)
pixels[10, 11] = (230, 232, 216, 255)
pixels[14, 11] = (224, 228, 216, 255)

# Custom model UV islands: brow ridge, frozen wedges, and dorsal plates.
custom_islands = [(0, 32, 18, 37), (18, 32, 42, 52), (32, 32, 64, 60)]
plate_colors = [
    (194, 197, 194, 255),
    (211, 213, 207, 255),
    (174, 180, 180, 255),
    (145, 151, 153, 255),
]
for left, top, right, bottom in custom_islands:
    for y in range(top, bottom):
        for x in range(left, right):
            color = rng.choice(plate_colors)
            if rng.random() < 0.16:
                color = (73, 76, 78, 255)
            pixels[x, y] = color

# Irregular charcoal fracture seams stay pixel-thin and never become blue-black filaments.
for start_x, start_y in [(22, 21), (7, 23), (45, 23), (38, 54), (23, 55)]:
    x, y = start_x, start_y
    for _ in range(rng.randrange(4, 8)):
        if 0 <= x < 64 and 0 <= y < 64 and pixels[x, y][3] > 0:
            pixels[x, y] = (47, 49, 51, 255)
        x += rng.choice([-1, 0, 1])
        y += 1

OUT.parent.mkdir(parents=True, exist_ok=True)
image.save(OUT)
print(OUT)

# Inventory/status icon: a trapped silhouette inside a cracked two-block ice shell.
icon = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
icon_pixels = icon.load()
shell = {
    (6, 1), (7, 1), (8, 1), (9, 1), (10, 1), (11, 1),
    (4, 2), (5, 2), (12, 2), (13, 2),
    (3, 3), (14, 3), (2, 4), (15, 4),
    (2, 5), (15, 5), (1, 6), (16, 6),
    (1, 7), (16, 7), (1, 8), (16, 8),
    (1, 9), (16, 9), (1, 10), (16, 10),
    (2, 11), (15, 11), (2, 12), (15, 12),
    (3, 13), (14, 13), (4, 14), (13, 14),
    (5, 15), (6, 16), (7, 16), (8, 16), (9, 16), (10, 16), (11, 15), (12, 15),
}
for x, y in shell:
    icon_pixels[x, y] = (196, 232, 240, 255)
for x, y in [(4, 3), (13, 3), (3, 5), (14, 5), (3, 11), (14, 11),
             (5, 14), (12, 14), (7, 15), (10, 15)]:
    icon_pixels[x, y] = (241, 251, 248, 255)

# The dark center reads as the immobilized player, while pale fractures cross the shell.
for y in range(5, 13):
    for x in range(6, 12):
        icon_pixels[x, y] = (55, 67, 70, 255)
for x, y in [(8, 4), (8, 5), (8, 6), (7, 7), (7, 8),
             (11, 7), (10, 8), (10, 9), (9, 10), (9, 11), (8, 12), (8, 13)]:
    icon_pixels[x, y] = (222, 245, 246, 255)

EFFECT_OUT.parent.mkdir(parents=True, exist_ok=True)
icon.save(EFFECT_OUT)
print(EFFECT_OUT)

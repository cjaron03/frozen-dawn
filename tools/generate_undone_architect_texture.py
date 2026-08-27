#!/usr/bin/env python3
"""Derive the Undone Architect skin without changing the Architect UV layout."""

from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/resources/assets/frozendawn/textures/entity/architect.png"
TARGET = ROOT / "src/main/resources/assets/frozendawn/textures/entity/undone_architect.png"


def blend(color: tuple[int, int, int, int], target: tuple[int, int, int], amount: float):
    red, green, blue, alpha = color
    return (
        round(red + (target[0] - red) * amount),
        round(green + (target[1] - green) * amount),
        round(blue + (target[2] - blue) * amount),
        alpha,
    )


image = Image.open(SOURCE).convert("RGBA")
pixels = image.load()

# Preserve the exact source UVs while cooling and slightly lifting its dark cloth.
for y in range(image.height):
    for x in range(image.width):
        color = pixels[x, y]
        if color[3] == 0:
            continue
        gray = round((color[0] + color[1] + color[2]) / 3)
        desaturated = tuple(
            max(0, min(255, round(channel * 0.68 + gray * 0.32 + 3)))
            for channel in color[:3]
        )
        pixels[x, y] = (*desaturated, color[3])

# The face is still the Architect's mask-like head, but drained and corpse-pale.
for y in range(8, 16):
    for x in range(8, 16):
        if pixels[x, y][3] != 0:
            pixels[x, y] = blend(pixels[x, y], (82, 91, 102), 0.48)

# Replace the live cyan eyes with deep sockets and a faint dead-frost rim.
for x in (10, 11, 13, 14):
    pixels[x, 10] = (21, 27, 34, 255)
    pixels[x, 11] = (1, 2, 4, 255)
    pixels[x, 12] = (8, 11, 15, 255)

# Sparse crusting on shoulders, forearms, and lower legs. Coordinates follow the
# vanilla humanoid UV sheet and only touch already-opaque source pixels.
frost = {
    (20, 20), (21, 20), (27, 20), (26, 21),
    (44, 20), (45, 21), (51, 20), (50, 22),
    (40, 28), (41, 29), (47, 30), (55, 28), (54, 30),
    (4, 29), (5, 30), (10, 31), (20, 29), (21, 31), (26, 30),
}
for x, y in frost:
    if pixels[x, y][3] != 0:
        pixels[x, y] = blend(pixels[x, y], (156, 174, 184), 0.76)

image.save(TARGET)
print(TARGET)

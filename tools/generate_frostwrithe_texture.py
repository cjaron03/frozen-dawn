#!/usr/bin/env python3
"""Generate the original Frostwrithe UV atlas used by its connected model."""

from pathlib import Path
import random

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/assets/frozendawn/textures/entity/frostwrithe.png"
RNG = random.Random(0xF2057)


def speckled_region(image, bounds, palette, accent=None, accent_chance=0.0):
    x0, y0, x1, y1 = bounds
    pixels = image.load()
    for y in range(y0, y1):
        for x in range(x0, x1):
            edge = x in (x0, x1 - 1) or y in (y0, y1 - 1)
            color = palette[0] if edge else RNG.choices(
                palette, weights=[52, 27, 14, 7][:len(palette)])[0]
            if accent and RNG.random() < accent_chance:
                color = accent[RNG.randrange(len(accent))]
            pixels[x, y] = color


def broken_vein(image, start, length, palette):
    pixels = image.load()
    x, y = start
    for step in range(length):
        if not (1 <= x < image.width - 1 and 1 <= y < image.height - 1):
            break
        if step % 5 != 3:
            pixels[x, y] = palette[min(len(palette) - 1, step % 3)]
            if step % 7 == 0:
                pixels[x, y + 1] = palette[0]
        x += 1
        y += RNG.choice((-1, 0, 0, 0, 1))


def main():
    image = Image.new("RGBA", (128, 64), (0, 0, 0, 0))
    body = [
        (7, 17, 21, 255),
        (10, 29, 35, 255),
        (14, 43, 50, 255),
        (20, 58, 64, 255),
    ]
    cyan = [(20, 139, 151, 255), (43, 183, 190, 255), (91, 218, 213, 255)]
    rime = [
        (123, 137, 136, 255),
        (158, 174, 169, 255),
        (198, 210, 201, 255),
        (231, 237, 226, 255),
    ]
    dirty_rime = [(75, 91, 92, 255), (98, 114, 113, 255)]

    # Repeated dark body mapping. Cyan appears as interrupted colony seams,
    # never as a face or a single organism's eye line.
    speckled_region(image, (0, 0, 50, 30), body, cyan, 0.028)
    speckled_region(image, (0, 32, 48, 50), body, cyan, 0.024)
    speckled_region(image, (0, 50, 48, 64), body, cyan, 0.020)
    broken_vein(image, (4, 8), 21, cyan)
    broken_vein(image, (17, 21), 25, cyan)
    broken_vein(image, (3, 39), 17, cyan)
    broken_vein(image, (22, 55), 19, cyan)

    # Rime plates and the forked tail use a dirty near-white family.
    speckled_region(image, (52, 0, 88, 22), rime, dirty_rime, 0.065)
    speckled_region(image, (88, 0, 128, 30), rime, dirty_rime, 0.075)
    speckled_region(image, (52, 24, 128, 64), rime, dirty_rime, 0.050)

    # Hand-place a few broken seams so repeated geometry does not read as a
    # perfectly manufactured tube.
    pixels = image.load()
    for x, y in ((9, 6), (15, 13), (27, 8), (35, 18), (7, 39),
                 (25, 44), (60, 7), (71, 15), (98, 9), (108, 18)):
        for dx in range(RNG.randint(1, 3)):
            if 0 <= x + dx < 128 and 0 <= y < 64:
                pixels[x + dx, y] = (40, 65, 68, 255)

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUTPUT, optimize=True)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Build the Archivist skin from a Returned-compatible 64x64 UV atlas.

The palette follows the approved ash-canvas concept while retaining the exact
Minecraft humanoid UV islands used by the in-game zombie model.
"""

from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/resources/assets/frozendawn/textures/entity/undone.png"
OUTPUT = ROOT / "src/main/resources/assets/frozendawn/textures/entity/archivist.png"


def recolor(image: Image.Image, boxes: list[tuple[int, int, int, int]],
            base: tuple[int, int, int]) -> None:
    source = Image.open(SOURCE).convert("RGBA")
    for left, top, right, bottom in boxes:
        for y in range(top, bottom):
            for x in range(left, right):
                red, green, blue, alpha = source.getpixel((x, y))
                if alpha == 0:
                    continue
                luminance = (red * 3 + green * 4 + blue) // 8
                variation = max(-24, min(24, luminance - 48))
                image.putpixel((x, y), (
                    max(0, min(255, base[0] + variation)),
                    max(0, min(255, base[1] + variation)),
                    max(0, min(255, base[2] + variation)),
                    alpha,
                ))


def paint(image: Image.Image, points: list[tuple[int, int]],
          color: tuple[int, int, int, int]) -> None:
    for x, y in points:
        image.putpixel((x, y), color)


def line(image: Image.Image, start: tuple[int, int], length: int,
         dx: int, dy: int, color: tuple[int, int, int, int]) -> None:
    paint(image, [(start[0] + index * dx, start[1] + index * dy)
                  for index in range(length)], color)


def main() -> None:
    image = Image.open(SOURCE).convert("RGBA")

    # Weathered skin, cold canvas coat, and charcoal lower layers. These are UV
    # islands, not screen-space regions.
    recolor(image, [(8, 0, 24, 8), (0, 8, 32, 16)], (125, 137, 137))
    recolor(image, [(20, 16, 36, 20), (16, 20, 40, 32)], (178, 188, 184))
    recolor(image, [(44, 16, 52, 20), (40, 20, 56, 32),
                    (36, 48, 44, 52), (32, 52, 48, 64)], (170, 181, 177))
    recolor(image, [(4, 16, 12, 20), (0, 20, 16, 32),
                    (20, 48, 28, 52), (16, 52, 32, 64)], (28, 36, 39))

    socket = (2, 4, 5, 255)
    # Wide, empty sockets remain legible when the smaller head is viewed at range.
    paint(image, [(x, y) for y in range(10, 13)
                  for x in (9, 10, 12, 13)], socket)
    paint(image, [(11, 11), (14, 11)], (53, 65, 65, 255))

    charcoal = (30, 38, 40, 255)
    strap = (91, 61, 39, 255)
    strap_light = (139, 94, 52, 255)
    frost = (220, 235, 231, 255)
    frost_shadow = (154, 181, 181, 255)
    amber = (183, 122, 55, 255)

    # High collar and asymmetrical archival harness on front and back torso.
    paint(image, [(x, 20) for x in range(21, 27)], charcoal)
    line(image, (20, 21), 8, 1, 1, strap)
    line(image, (27, 21), 8, -1, 1, strap_light)
    line(image, (32, 21), 8, 1, 1, strap)
    line(image, (39, 21), 8, -1, 1, strap_light)
    paint(image, [(23, 25), (24, 26), (35, 25), (36, 26)], amber)

    # Sleeve ties and tiny repair stitches imply years of carrying rather than armor.
    paint(image, [(42, 24), (43, 24), (46, 27), (47, 27),
                  (34, 56), (35, 56), (42, 59), (43, 59)], strap)
    paint(image, [(44, 22), (44, 24), (44, 26),
                  (36, 54), (36, 56), (36, 58)], amber)

    # Frost catches the extremities and shoulder seams without becoming hive glow.
    paint(image, [(20, 20), (21, 20), (26, 20), (27, 20),
                  (40, 20), (41, 20), (46, 20), (47, 20),
                  (32, 52), (33, 52), (46, 52), (47, 52)], frost)
    paint(image, [(21, 21), (26, 21), (41, 21), (46, 21),
                  (33, 53), (46, 53)], frost_shadow)
    paint(image, [(4, 29), (5, 30), (6, 31),
                  (12, 29), (13, 30), (14, 31),
                  (20, 61), (21, 62), (22, 63),
                  (28, 61), (29, 62), (30, 63)], frost)

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUTPUT)


if __name__ == "__main__":
    main()

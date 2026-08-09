#!/usr/bin/env python3
"""Build the original 18x18 Marked status icon with a transparent background."""

from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/assets/frozendawn/textures/mob_effect/marked.png"
ITEM_OUTPUT = ROOT / "src/main/resources/assets/frozendawn/textures/item/marked_eye.png"


def rect(draw: ImageDraw.ImageDraw, color: str, x: int, y: int, w: int, h: int) -> None:
    draw.rectangle((x, y, x + w - 1, y + h - 1), fill=color)


def main() -> None:
    image = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)

    # Chunky enclosing silhouette, intentionally close to a classic status-eye read.
    for box in ((7, 2, 4, 1), (5, 3, 8, 1), (3, 4, 12, 1),
                (1, 5, 16, 3), (2, 8, 14, 1), (3, 9, 12, 1),
                (4, 10, 3, 4), (5, 14, 2, 2),
                (11, 10, 3, 3), (12, 13, 2, 2)):
        rect(draw, "#55595d", *box)

    # The swollen blue-black lid surrounds a single empty aperture.
    for box in ((7, 3, 4, 1), (5, 4, 8, 1), (3, 5, 12, 1),
                (2, 6, 14, 2), (4, 8, 10, 1), (6, 9, 6, 1)):
        rect(draw, "#1769ad", *box)
    for box in ((6, 4, 3, 1), (4, 5, 4, 1), (3, 6, 4, 2),
                (11, 5, 2, 1), (11, 6, 4, 2), (5, 8, 3, 1), (10, 8, 3, 1)):
        rect(draw, "#2ba8e8", *box)
    rect(draw, "#05080b", 7, 6, 4, 3)

    # Two uneven icicles grow directly from the lower lid.
    for box in ((2, 7, 2, 1), (3, 8, 2, 1), (4, 9, 3, 2),
                (5, 11, 2, 3), (6, 14, 1, 1),
                (14, 7, 2, 1), (13, 8, 2, 1), (11, 9, 3, 2),
                (12, 11, 2, 2), (13, 13, 1, 1)):
        rect(draw, "#8fd4eb", *box)
    for box in ((2, 7, 1, 1), (4, 8, 1, 1), (5, 9, 2, 1),
                (6, 11, 1, 3), (14, 7, 1, 1), (13, 8, 1, 1),
                (12, 9, 2, 1), (13, 11, 1, 2)):
        rect(draw, "#e9fcff", *box)
    for box in ((5, 10, 1, 3), (6, 14, 1, 1), (11, 10, 1, 2), (12, 12, 1, 2)):
        rect(draw, "#378bb2", *box)

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUTPUT, optimize=True)
    ITEM_OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    image.resize((32, 32), Image.Resampling.NEAREST).save(ITEM_OUTPUT, optimize=True)


if __name__ == "__main__":
    main()

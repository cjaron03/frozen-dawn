#!/usr/bin/env python3
"""Generate the 16x16 Scribe Record item texture (MACS section 9.4b).

A salvaged ORSA slate on a bent clip, scored with short pale Thaeven lines.
Deterministic: rerunning produces the same PNG.
"""
import argparse
from pathlib import Path

from PIL import Image

OUTLINE = (30, 35, 41, 255)
BODY = (60, 70, 80, 255)
BODY_LIGHT = (86, 98, 109, 255)
BODY_DARK = (44, 52, 60, 255)
CLIP = (141, 151, 159, 255)
CLIP_LIGHT = (185, 194, 200, 255)
SCORE = (201, 209, 214, 255)
SCORE_DIM = (139, 150, 158, 255)
ORSA = (200, 120, 46, 255)
RUST = (138, 74, 34, 255)

# Short lines, verb last: the scratches are deliberately uneven in length.
SCORES = [(5, 5, 9), (5, 7, 7), (5, 9, 10), (5, 11, 6)]


def draw() -> Image.Image:
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = image.load()
    for x in range(3, 13):
        for y in range(2, 15):
            edge = x in (3, 12) or y in (2, 14)
            px[x, y] = OUTLINE if edge else BODY
    for y in range(3, 14):
        px[4, y] = BODY_LIGHT
        px[11, y] = BODY_DARK
    for x in range(4, 12):
        px[x, 13] = BODY_DARK
    for x in range(6, 10):
        px[x, 1] = OUTLINE
        px[x, 2] = CLIP
    px[7, 2] = CLIP_LIGHT
    px[8, 2] = CLIP_LIGHT
    px[6, 3] = CLIP
    px[9, 3] = CLIP
    for start, row, end in SCORES:
        for x in range(start, end + 1):
            px[x, row] = SCORE if (x + row) % 4 else SCORE_DIM
    # Salvage: an ORSA hazard stripe and a rusted corner.
    px[4, 12] = ORSA
    px[5, 12] = ORSA
    px[6, 12] = RUST
    px[11, 3] = RUST
    return image


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="src/main/resources/assets/frozendawn/textures/item/scribe_record.png")
    output = Path(parser.parse_args().output)
    output.parent.mkdir(parents=True, exist_ok=True)
    draw().save(output)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Original deterministic condensation film; generated offline, never during a frame."""
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[3]
SIZE = (512, 256)
OUT = ROOT / "src/main/resources/assets/frozendawn/textures/gui/emergency_visor_mist.png"
rng = random.Random(0xC0171)


def cloud(grid, blur):
    noise = Image.new("L", grid)
    noise.putdata([rng.randrange(256) for _ in range(grid[0] * grid[1])])
    return noise.resize(SIZE, Image.Resampling.BICUBIC).filter(ImageFilter.GaussianBlur(blur))


def samples(image):
    values = image.load()
    return [values[x, y] for y in range(SIZE[1]) for x in range(SIZE[0])]


broad = samples(cloud((9, 5), 12))
fine = samples(cloud((29, 15), 3))
beads = Image.new("L", SIZE)
draw = ImageDraw.Draw(beads)
for _ in range(54):
    x, y = rng.randrange(SIZE[0]), rng.randrange(SIZE[1])
    # Most wet trails gather near the seal; a few cross the central film.
    if 100 < x < 412 and 45 < y < 211 and rng.random() < 0.8:
        continue
    rx, ry = rng.uniform(1.3, 3.4), rng.uniform(2, 7)
    draw.ellipse((x - rx, y - ry, x + rx, y + ry), fill=rng.randrange(20, 44))
    draw.line((x, y + ry, x - 1, y + ry + rng.randrange(4, 14)), fill=14, width=1)
beads = samples(beads.filter(ImageFilter.GaussianBlur(1.5)))

pixels = []
for y in range(SIZE[1]):
    for x in range(SIZE[0]):
        i = y * SIZE[0] + x
        u, v = (x + 0.5) / SIZE[0], (y + 0.5) / SIZE[1]
        # Continuous falloff: no mask boundary or reserved UI rectangle.
        edges = math.exp(-u / 0.14) + math.exp(-(1 - u) / 0.14)
        edges += 0.65 * math.exp(-v / 0.12) + 1.1 * math.exp(-(1 - v) / 0.20)
        clouds = 0.7 * broad[i] / 255 + 0.3 * fine[i] / 255
        alpha = round(min(158, 20 + 30 * clouds + 65 * edges * (0.65 + 0.5 * clouds) + beads[i]))
        shade = round(6 * clouds)
        pixels.append((176 + shade, 192 + shade, 196 + shade, alpha))
image = Image.new("RGBA", SIZE)
image.putdata(pixels)
image.save(OUT)
print(OUT)

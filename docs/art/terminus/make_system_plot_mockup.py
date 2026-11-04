"""ORSA SYSTEM PLOT (SOL): the map they were all looking at when the vault was sealed (owner, 2026-11-03). Earth clearly knocked
out of orbit, Mars clearly the centre of attention, coded stations from Zeus out that nothing else mentions. Plain SOL numbers
with the Greek names, a flat chart. The cot room is narrower than the chart, so it fills the vault's east wall behind the cradle:
seven blocks by four, 16 pixels a block, drawn x5. Run from anywhere: python3 docs/art/terminus/make_system_plot_mockup.py
"""
import math
import random
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from make_vault_warning_mockup import HAND

HERE = Path(__file__).resolve().parent
OUT = HERE / 'system-plot-mockup.png'
K = 5
F = lambda s: ImageFont.truetype('/System/Library/Fonts/Menlo.ttc', s)
BG, INK, DIM = (24, 28, 33), (226, 232, 236), (150, 160, 167)
STEEL, FRAME, ORBIT = (14, 18, 22), (44, 50, 56), (48, 62, 70)
PALE, FAINT, RED, ROUTE, PENCIL, SUN = (159, 214, 232), (92, 116, 128), (232, 104, 92), (120, 48, 42), (176, 170, 150), (214, 200, 150)
FONT = {'A': '111/101/111/101/101', 'B': '110/101/110/101/110', 'C': '111/100/100/100/111', 'D': '110/101/101/101/110', 'E': '111/100/110/100/111',
        'G': '111/100/101/101/111', 'H': '101/101/111/101/101', 'I': '111/010/010/010/111', 'K': '101/101/110/101/101', 'L': '100/100/100/100/111',
        'M': '101/111/111/101/101', 'N': '110/101/101/101/101', 'O': '111/101/101/101/111', 'P': '111/101/111/100/100', 'R': '110/101/110/101/101',
        'S': '111/100/111/001/111', 'T': '111/010/010/010/010', 'U': '101/101/101/101/111', 'X': '101/101/010/101/101', 'Y': '101/101/010/010/010',
        'Z': '111/001/010/100/111', '1': '010/110/010/010/111', '2': '111/001/111/100/111', '3': '111/001/011/001/111', '4': '101/101/111/001/001',
        '5': '111/100/111/001/111', '6': '111/100/111/101/111', '7': '111/001/010/010/010', '8': '111/101/111/101/111', '-': '000/000/111/000/000',
        ' ': '000/000/000/000/000'}
CX, CY = -8, 27
# Name, orbit radius, the height it sits at on the plot.
BODIES = [('SOL-1 HERMES', 21, 22), ('SOL-2 APHRODITE', 29, 32), ('SOL-3 GAIA', 38, 24), ('SOL-4 ARES', 54, 27),
          ('SOL-5 ZEUS', 72, 30), ('SOL-6 KRONOS', 86, 20), ('SOL-7 OURANOS', 98, 22), ('SOL-8 POSEIDON', 112, 34)]


def text(im, x, y, words, colour, font=FONT):
    for ch in words:
        for j, row in enumerate(font[ch].split('/')):
            for i, c in enumerate(row):
                if c == '1' and 0 <= x + i < im.width and 0 <= y + j < im.height:
                    im.putpixel((x + i, y + j), colour)
        x += 4


def at(r, y):
    return round(CX + math.sqrt(r * r - (y - CY) ** 2)), y


def plot():
    im = Image.new('RGB', (112, 64), STEEL)
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 111, 63), outline=FRAME)
    for n, (_, r, _) in enumerate(BODIES):                  # the orbits; Gaia's is dashed and broken where it left
        for a in range(-900, 900):
            t = math.radians(a / 10)
            x, y = round(CX + r * math.cos(t)), round(CY + r * math.sin(t))
            if 1 <= x < 111 and 15 <= y < 38 and not (n == 2 and ((a // 50) % 2 or -200 < a < 60)):
                im.putpixel((x, y), ORBIT)
    d.ellipse((CX - 13, CY - 13, CX + 13, CY + 13), fill=SUN)
    gaia, ares = at(38, 24), at(54, 27)
    for r, y in ((21, 22), (29, 32), (72, 30), (86, 20), (98, 22), (112, 34)):   # every route ends at Ares
        d.line((*at(r, y), *ares), ROUTE)
    r = random.Random(4)
    for _ in range(12):                                     # the pins
        im.putpixel((ares[0] + r.choice((-7, -6, 6, 7, -5, 5)), ares[1] + r.randint(-6, 6)), RED)
    x, y = gaia                                             # where it was, crossed out, and the track it took
    d.line((x - 2, y - 2, x + 2, y + 2), RED); d.line((x - 2, y + 2, x + 2, y - 2), RED)
    for i in range(0, 60, 4):
        px, py = x + 3 + i, y - 3 - i * 5 // 60
        d.line((px, py, px + 1, py), PENCIL)
    d.rectangle((x + 63, y - 9, x + 65, y - 7), outline=PALE)
    d.rectangle((ares[0] - 1, ares[1] - 1, ares[0] + 1, ares[1] + 1), fill=RED)
    d.rectangle((ares[0] - 4, ares[1] - 4, ares[0] + 4, ares[1] + 4), outline=RED)
    text(im, ares[0] - 11, 33, 'EXODUS', RED)
    for n, (_, radius, y) in enumerate(BODIES):
        if n in (2, 3):
            continue
        bx, by = at(radius, y)
        d.rectangle((bx, by, bx + 1, by + 1), fill=PALE if n < 5 else FAINT)
        for dx, dy in {4: ((3, -1), (3, 2), (-2, -1), (-2, 2)), 5: ((3, 0), (-2, 1)), 6: ((3, 1),)}.get(n, ()):   # the stations: ticks round the planet
            im.putpixel((bx + dx, by + dy), PALE)
    text(im, 3, 2, 'ORSA SYSTEM PLOT', PALE)
    text(im, 2, 14, 'SOL HELIOS', SUN)
    text(im, 38, 8, 'STOP UPDATING THIS', PENCIL, {**FONT, **HAND})
    text(im, at(72, 30)[0] - 2, 33, 'Z-STN 1-4', PALE)
    d.line((2, 39, 109, 39), FRAME)
    for n, (name, _, _) in enumerate(BODIES):
        row, colour = 41 + n % 4 * 5 + n % 4, (RED if n == 3 else PALE if n < 5 else FAINT)
        text(im, 2 if n < 4 else 111 - 4 * len(name), row, name, colour)
    return im


def main():
    pic = plot()
    im = Image.new('RGB', (pic.width * K + 48, 470), BG)
    d = ImageDraw.Draw(im)
    d.text((24, 16), 'ORSA SYSTEM PLOT (SOL)', INK, font=F(24))
    d.text((24, 50), "Proposed. 16 px a block, x5. The vault's east wall, seven by four, behind the cradle.", DIM, font=F(12))
    im.paste(pic.resize((pic.width * K, pic.height * K), Image.NEAREST), (24, 78))
    d.text((24, 412), 'Gaia: orbit broken, old place crossed out, track outward. Ares: every route ends there.', DIM, font=F(12))
    d.text((24, 432), 'Zeus and beyond: coded stations, never explained.', DIM, font=F(12))
    im.save(OUT)


if __name__ == '__main__':
    main()

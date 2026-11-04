"""ORSA SYSTEM PLOT (SOL): the map they were all looking at when the vault was sealed (owner, 2026-11-03). Earth clearly knocked
out of orbit, Mars clearly the centre of attention, coded stations from Zeus out that nothing else mentions. Plain SOL numbers
with the Greek names. A wall of small lamps glowing in the dark (the owner's reference for the form; layout and marks are ours),
mostly empty: seven blocks by four read as clutter, so this is its own quiet room, a wall 24 blocks by 8. 16 pixels a block,
drawn x2. Run from anywhere: python3 docs/art/terminus/make_system_plot_mockup.py
"""
import math
import random
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from make_vault_warning_mockup import HAND

HERE = Path(__file__).resolve().parent
OUT = HERE / 'system-plot-mockup.png'
K = 2
F = lambda s: ImageFont.truetype('/System/Library/Fonts/Menlo.ttc', s)
BG, INK, DIM = (24, 28, 33), (226, 232, 236), (150, 160, 167)
STEEL, FRAME_TEXT, LAMP_DEAD = (10, 12, 15), (92, 116, 128), (34, 40, 45)
PALE, FAINT, RED, ROUTE, PENCIL, SUN_LAMP = (190, 230, 244), (92, 116, 128), (232, 84, 72), (140, 52, 44), (176, 170, 150), (206, 232, 240)
FONT = {'A': '111/101/111/101/101', 'B': '110/101/110/101/110', 'C': '111/100/100/100/111', 'D': '110/101/101/101/110', 'E': '111/100/110/100/111',
        'G': '111/100/101/101/111', 'H': '101/101/111/101/101', 'I': '111/010/010/010/111', 'K': '101/101/110/101/101', 'L': '100/100/100/100/111',
        'M': '101/111/111/101/101', 'N': '110/101/101/101/101', 'O': '111/101/101/101/111', 'P': '111/101/111/100/100', 'R': '110/101/110/101/101',
        'S': '111/100/111/001/111', 'T': '111/010/010/010/010', 'U': '101/101/101/101/111', 'X': '101/101/010/101/101', 'Y': '101/101/010/010/010',
        'Z': '111/001/010/100/111', '1': '010/110/010/010/111', '2': '111/001/111/100/111', '3': '111/001/011/001/111', '4': '101/101/111/001/001',
        '5': '111/100/111/001/111', '6': '111/100/111/101/111', '7': '111/001/010/010/010', '8': '111/101/111/101/111', '-': '000/000/111/000/000',
        ' ': '000/000/000/000/000'}
W, H, LINE, PITCH = 384, 128, 60, 4
# Name, x on the line, lamps of radius, and whether its label sits above the line.
BODIES = [('SOL-1', 'HERMES', 92, 0, True), ('SOL-2', 'APHRODITE', 124, 1, False), ('SOL-4', 'ARES', 206, 1, True),
          ('SOL-5', 'ZEUS', 270, 3, False), ('SOL-6', 'KRONOS', 312, 2, True), ('SOL-7', 'OURANOS', 342, 1, False), ('SOL-8', 'POSEIDON', 364, 0, True)]


def text(im, x, y, words, colour, font=FONT):
    for ch in words:
        for j, row in enumerate(font[ch].split('/')):
            for i, c in enumerate(row):
                if c == '1' and 0 <= x + i < im.width and 0 <= y + j < im.height:
                    im.putpixel((x + i, y + j), colour)
        x += 4


def centred(im, x, y, words, colour):
    text(im, x - 2 * len(words) + 1, y, words, colour)


def lamp(im, x, y, colour, r, dead=.05):
    """One lamp of the wall, two pixels square; some have failed."""
    if r.random() < dead:
        colour = LAMP_DEAD
    ImageDraw.Draw(im).rectangle((x, y, x + 1, y + 1), fill=colour)


def disc(im, cx, cy, lamps, colour, r):
    for i in range(-lamps, lamps + 1):
        for j in range(-lamps, lamps + 1):
            if i * i + j * j <= lamps * lamps + 1:
                lamp(im, cx + i * PITCH, cy + j * PITCH, colour, r)


def dotted(im, a, b, colour, pitch):
    steps = int(math.dist(a, b) / pitch)
    for i in range(steps + 1):
        im.putpixel((round(a[0] + (b[0] - a[0]) * i / steps), round(a[1] + (b[1] - a[1]) * i / steps)), colour)


def plot():
    r = random.Random(4)
    im = Image.new('RGB', (W, H), STEEL)
    for i in range(0, 17):                                  # the Sun, cut off by the wall's edge
        for j in range(-14, 15):
            if (i + 2) ** 2 + j * j <= 15 * 15:
                lamp(im, i * PITCH, LINE + j * PITCH, SUN_LAMP, r)
    ares = (206, LINE)
    for _, _, x, _, _ in BODIES:                            # every route ends at Ares
        if x != ares[0]:
            dotted(im, (x + (6 if x < 206 else -14), LINE + 1), (ares[0], LINE + 1), ROUTE, 6)
    for b in range(-11, 12):                                # the belt
        lamp(im, 236 + round(b * b / 9), LINE + b * 5, FAINT, r, .2)
    for first, second, x, lamps, above in BODIES:
        colour = RED if second == 'ARES' else PALE if x < 300 else FAINT
        disc(im, x, LINE, lamps, colour, r)
        y = LINE - 34 if above else LINE + 22
        centred(im, x, y, first, colour)
        centred(im, x, y + 7, second, colour)
    # Gaia: where it was, a dark ring with a cross, and the track it left going out of the plane.
    d = ImageDraw.Draw(im)
    d.ellipse((152, LINE - 5, 162, LINE + 5), outline=LAMP_DEAD)
    d.line((154, LINE - 3, 160, LINE + 3), RED); d.line((154, LINE + 3, 160, LINE - 3), RED)
    for i in range(1, 16):
        t = i / 15
        lamp(im, round(160 + 66 * t), round(LINE + 6 + 44 * t * t), PENCIL, r, .1)
    lamp(im, 230, 112, PALE, r, 0)
    text(im, 238, 110, 'SOL-3 GAIA', PALE)
    text(im, 150, 118, 'STOP UPDATING THIS', PENCIL, {**FONT, **HAND})
    d.rectangle((ares[0] - 9, LINE - 8, ares[0] + 10, LINE + 9), outline=RED)
    for _ in range(9):                                      # the pins
        im.putpixel((ares[0] + r.choice((-13, -12, 13, 14)), LINE + r.randint(-11, 11)), RED)
    centred(im, ares[0], LINE + 14, 'EXODUS', RED)
    for dx, dy in ((-20, -18), (18, -20), (22, 14), (-22, 12)):      # the stations: lamps off the planet, nothing said
        lamp(im, 270 + dx, LINE + dy, PALE, r, 0)
    centred(im, 270, LINE - 30, 'Z-STN 1-4', PALE)
    lamp(im, 324, LINE + 12, FAINT, r, 0)
    centred(im, 312, LINE + 20, 'K-STN 2', FAINT)
    text(im, 3, H - 8, 'ORSA SYSTEM PLOT', FRAME_TEXT)
    return im


def main():
    pic = plot()
    im = Image.new('RGB', (W * K + 48, H * K + 150), BG)
    d = ImageDraw.Draw(im)
    d.text((24, 16), 'ORSA SYSTEM PLOT (SOL)', INK, font=F(24))
    d.text((24, 50), 'Proposed. 16 px a block, x2. A wall 24 blocks by 8, in a quiet room of its own off the cot room.', DIM, font=F(13))
    im.paste(pic.resize((W * K, H * K), Image.NEAREST), (24, 78))
    y = 78 + H * K + 12
    d.text((24, y), 'Lamps glowing in the dark, some dead, mostly empty wall. Gaia has left the line.', DIM, font=F(13))
    d.text((24, y + 20), 'Ares: red, boxed, every route ends there. Zeus and beyond: coded stations.', DIM, font=F(13))
    im.save(OUT)


if __name__ == '__main__':
    main()

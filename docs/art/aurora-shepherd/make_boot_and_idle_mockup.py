"""Aurora's first boot, showing the ORSA logo, and her idle expressions, at Minecraft scale. Approved 2026-11-04: logo B in her green, all idle expressions.

The logo is redrawn in hard pixels from textures/gui/orsa_logo_boot_sheet.png, where a small moon rides the orbit.
Nothing here is in the game yet.
Run from anywhere: python3 docs/art/aurora-shepherd/make_boot_and_idle_mockup.py
"""
import math
from pathlib import Path
from PIL import Image, ImageDraw
from make_body_directions import C, font
from make_face_and_talk_mockup import display

HERE = Path(__file__).resolve().parent
SHEET = HERE.parents[2] / 'src/main/resources/assets/frozendawn/textures/gui/orsa_logo_boot_sheet.png'
BG = (12, 16, 20)
GLOW, GLOW2, DARK = (125, 242, 192), (58, 169, 132), (29, 92, 72)
RING, SPACE = GLOW, (11, 16, 20)
BLUE, SHADE = GLOW2, DARK
GOLD, MOON, MOONBACK = GLOW, (228, 255, 244), GLOW2
TILT = math.radians(-27)


def orbit(t, r):
    """A point on the orbit at phase t: 0 upper right, through the front, to lower left and round the back."""
    a = 2 * math.pi * t + math.radians(25)
    x, y = 0.76 * r * math.cos(a), 0.24 * r * math.sin(a)
    return x * math.cos(TILT) - y * math.sin(TILT), x * math.sin(TILT) + y * math.cos(TILT), math.sin(a) > 0


def logo(n, t):
    """The logo as an n by n grid of colors, no blending."""
    r = n / 2
    px, py, front = orbit(t, r)
    grid = [[None] * n for _ in range(n)]
    for j in range(n):
        for i in range(n):
            x, y = i + .5 - r, j + .5 - r
            d = math.hypot(x, y) / r
            if d > 1:
                continue
            c = RING if d > .8 else SPACE
            u = x * math.cos(TILT) + y * math.sin(TILT)
            v = -x * math.sin(TILT) + y * math.cos(TILT)
            e = math.hypot(u / (.76 * r), v / (.24 * r))
            planet = math.hypot(x, y) < .42 * r
            if planet:
                c = SHADE if x + y > .28 * r else BLUE
            if d <= .8 and abs(e - 1) < max(.16, 1.1 / n * 2.6) and (v > 0 or not planet):
                c = GOLD
            grid[j][i] = c
    size = max(.13 * r, .9)
    for j in range(n):
        for i in range(n):
            if math.hypot(i + .5 - r - px, j + .5 - r - py) < size and (front or math.hypot(px, py) > .42 * r):
                grid[j][i] = MOON if front else MOONBACK
    return grid


TINY = ['...##...', '..####.+', '..##+++.', '.+++##..', '+..##...', '........']
TINY_MOON = [(7, 1, '#'), (5, 2, '#'), (3, 3, '#'), (1, 3, '#'), (0, 4, '#'), (1, 1, '+'), (2, 0, '+'), (6, 0, '+')]
TINY_COLORS = {'#': GLOW, '+': GLOW2, '.': None}


def tiny(frame):
    rows = [list(r) for r in TINY]
    x, y, c = TINY_MOON[frame]
    rows[y][x] = c
    return [[TINY_COLORS[c] for c in r] for r in rows]


def pixels(d, grid, x, y, s, back=SPACE):
    d.rectangle([x, y, x + len(grid[0]) * s - 1, y + len(grid) * s - 1], fill=back)
    for j, row in enumerate(grid):
        for i, c in enumerate(row):
            if c:
                d.rectangle([x + i * s, y + j * s, x + (i + 1) * s - 1, y + (j + 1) * s - 1], fill=c)


def face(*rows):
    return [(x, y, 'glow' if c == '#' else 'glow2') for y, row in enumerate(rows) for x, c in enumerate(row) if c != '.']


NEUTRAL = face('........', '.##..##.', '.##..##.', '........', '..++++..', '........')
IDLE = [
    ('1 Heavy lids', 'Half awake, lids dim', face('........', '.++..++.', '.##..##.', '........', '..++++..', '........')),
    ('2 Brow up', 'One eye taller: really?', face('.##.....', '.##..##.', '.##..##.', '........', '...++...', '........')),
    ('3 Looks up', 'Checking the ceiling', face('.##..##.', '.##..##.', '........', '........', '..++++..', '........')),
    ('4 Wink', 'One eye shuts', face('........', '.##.....', '.##.+++.', '........', '..++++..', '........')),
    ('5 Smirk', 'Mouth pulls aside', face('........', '.##..##.', '.##..##.', '......+.', '...+++..', '........')),
]
ROLL = [
    face('.##..##.', '.##..##.', '........', '........', '...++...', '........'),
    face('##..##..', '##..##..', '........', '........', '...++...', '........'),
    face('..##..##', '..##..##', '........', '........', '...++...', '........'),
    face('........', '.##..##.', '.##..##.', '........', '.++++++.', '........'),
]


def main():
    w, h = 1320, 1010
    img = Image.new('RGB', (w, h), BG)
    d = ImageDraw.Draw(img)
    d.text((24, 16), 'AURORA: FIRST BOOT AND IDLE EXPRESSIONS', font=font(22), fill=C['glow'])
    d.text((24, 46), 'Approved. The logo is redrawn from the terminal boot sheet in hard pixels, with the moon riding its orbit.', font=font(13), fill=C['dim'])

    d.text((24, 84), 'TERMINAL TODAY', font=font(14), fill=C['text'])
    sheet = Image.open(SHEET).convert('RGBA')
    frame = sheet.crop((0, 0, 208, 208)).resize((104, 104), Image.LANCZOS)
    img.paste(frame, (24, 108), frame)

    options = [('APPROVED: 30 PIXELS, 2x, IN HER GREEN', 30, 2)]
    for row, (name, n, s) in enumerate(options):
        y = 84 + row * 112
        d.text((170, y), name, font=font(14), fill=C['text'])
        for f in range(8):
            pixels(d, logo(n, f / 8), 170 + f * 84, y + 24, s, BG)
    d.text((860, 84), 'Talk screen. Fills the 80 by 60 face', font=font(13), fill=C['dim'])
    d.text((860, 104), 'panel. 8 moon frames, about 3 seconds,', font=font(13), fill=C['dim'])
    d.text((860, 124), 'while the terminal "ORSA" sound plays.', font=font(13), fill=C['dim'])

    y = 318
    d.text((24, y), 'HER BODY DISPLAY, 8 BY 6', font=font(14), fill=C['text'])
    for f in range(8):
        pixels(d, tiny(f), 24 + f * 100, y + 26, 10)
    d.text((24, y + 96), 'At play size:', font=font(13), fill=C['dim'])
    for f in range(8):
        pixels(d, tiny(f), 130 + f * 56, y + 92, 4)
    d.text((860, y + 26), 'Her own two greens. A planet', font=font(13), fill=C['dim'])
    d.text((860, y + 46), 'and orbit is all 48 pixels can hold; the', font=font(13), fill=C['dim'])
    d.text((860, y + 66), 'white ring does not fit.', font=font(13), fill=C['dim'])

    y = 462
    d.text((24, y), 'FIRST BOOT, IN ORDER', font=font(14), fill=C['text'])
    steps = ['Cell in, dock\nlight goes green', 'She lifts off.\nLogo spins, "ORSA"', 'Logo stops. Her\nRecording line types', 'Eyes open:\nher own voice starts']
    for i, text in enumerate(steps):
        x = 24 + i * 310
        d.rectangle([x, y + 26, x + 280, y + 88], fill=(24, 30, 36))
        d.text((x + 12, y + 34), f'{i + 1}', font=font(18), fill=C['glow'])
        d.text((x + 40, y + 34), text, font=font(13), fill=C['text'])
    d.text((24, y + 98), 'First wake only, never a reboot. This also settles the review note: during the Recording line her body now matches the screen.', font=font(13), fill=C['dim'])

    y = 596
    d.text((24, y), 'IDLE EXPRESSIONS: NEUTRAL ONLY, NOT WHILE TALKING, A SECOND OR TWO EACH', font=font(14), fill=C['text'])
    s = 14
    display(d, NEUTRAL, 24, y + 30, s)
    d.text((24, y + 30 + 6 * s + 8), 'Neutral today', font=font(13), fill=C['dim'])
    for i, (name, note, cells) in enumerate(IDLE):
        x = 190 + i * 160
        display(d, cells, x, y + 30, s)
        d.text((x, y + 30 + 6 * s + 8), name, font=font(13), fill=C['text'])
        d.text((x, y + 30 + 6 * s + 28), note.split(': ')[0], font=font(11), fill=C['dim'])
    d.text((24, y + 176), 'At play size:', font=font(13), fill=C['dim'])
    for i, cells in enumerate([NEUTRAL] + [c for _, _, c in IDLE]):
        display(d, cells, 130 + i * 60, y + 172, 5)

    y = 816
    d.text((24, y), '6  RARE: THE EYE ROLL, ABOUT ONCE IN TEN MINUTES', font=font(14), fill=C['text'])
    for i, cells in enumerate(ROLL):
        display(d, cells, 24 + i * 150, y + 30, s)
    d.text((24, y + 30 + 6 * s + 10), 'Up, left, across to the right, then back down with a flat, unimpressed mouth. Four frames, under a second.', font=font(13), fill=C['dim'])
    d.text((660, y + 30), 'Talk screen idle now picks its own seed each', font=font(13), fill=C['dim'])
    d.text((660, y + 50), 'conversation, so she stops opening the same way.', font=font(13), fill=C['dim'])
    img.save(HERE / 'boot-and-idle-mockup.png')
    sheet = Image.new('RGBA', (30 * 8, 30), (0, 0, 0, 0))
    for f in range(8):
        for j, row in enumerate(logo(30, f / 8)):
            for i, c in enumerate(row):
                if c and c != SPACE:
                    sheet.putpixel((f * 30 + i, j), c + (255,))
    sheet.save(HERE.parents[2] / 'src/main/resources/assets/frozendawn/textures/gui/shepherd_boot_logo.png')


if __name__ == '__main__':
    main()

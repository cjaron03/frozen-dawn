"""The Watch Floor: the vault's big encounter, between the bridge and the cot room, at Minecraft scale (16 pixels a block).

Owner, 2026-11-03: "MUCH BIGGER and harder", "like destiny 2 style", a boss or close to it, after that game's two-floor
Security encounter: read upstairs, act downstairs, against a clock. Its mechanics only; the names, glyphs and art are ours.
Run from anywhere: python3 docs/art/terminus/make_watch_floor_mockup.py
"""
import random
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
OUT = HERE / 'watch-floor-mockup.png'
B = 16
F = lambda s: ImageFont.truetype('/System/Library/Fonts/Menlo.ttc', s)
BG, INK, DIM, GOLD, RED, PALE = (24, 28, 33), (226, 232, 236), (150, 160, 167), (255, 214, 92), (232, 104, 92), (159, 214, 232)
hexc = lambda h: tuple(bytes.fromhex(h))
PAL = dict(rock=['4a4f54', '42474c', '52575c'], shell=['5b6064', '555a5e'], floor=['2f3438', '2a2f33'], walk=['8d9296', '83888c'],
           air=['0f1317'], pit=['07090b'], lower=['23282c', '1f2428'])

GLYPHS = ['..##..../.#..#.../.#..#.../..##..../...#..../...#..../..###.../........',
          '#......#/.#....#./..#..#../...##.../...##.../..#..#../.#....#./#......#',
          '...##.../..####../.##..##./##....##/##....##/.##..##./..####../...##...',
          '########/#......#/#.####.#/#.#..#.#/#.#..#.#/#.####.#/#......#/########',
          '#.#.#.#./.#.#.#.#/#.#.#.#./.#.#.#.#/#.#.#.#./.#.#.#.#/#.#.#.#./.#.#.#.#',
          '...#..../..###.../.#####../#######./...#..../...#..../...#..../...#....']


def tile(name, x, y):
    r = random.Random(hash((name, x, y)) & 0xffffff)
    pal = PAL[name]
    im = Image.new('RGB', (B, B), hexc(pal[0]))
    if len(pal) > 1:
        px = im.load()
        for i in range(B):
            for j in range(B):
                px[i, j] = hexc(r.choice(pal))
    return im


def glyph(i, on=True, size=16):
    im = Image.new('RGB', (10, 10), (14, 18, 22))
    for y, row in enumerate(GLYPHS[i].split('/')):
        for x, c in enumerate(row):
            if c == '#':
                im.putpixel((x + 1, y + 1), PALE if on else (58, 66, 72))
    return im.resize((size, size), Image.NEAREST)


X0, X1, Z0, Z1 = 14, 52, -13, 13


def plan():
    g = {(x, z): 'rock' for x in range(X0, X1 + 1) for z in range(Z0, Z1 + 1)}

    def box(a, b, c, e, m):
        for x in range(a, b + 1):
            for z in range(c, e + 1):
                g[x, z] = m
    box(15, 52, -12, 12, 'shell')
    box(17, 39, -11, 11, 'walk')            # the catwalk ring, upper level
    box(19, 37, -9, 9, 'lower')             # the open floor, seen from above
    box(15, 16, -2, 2, 'floor')             # the far ledge of the bridge
    box(40, 44, -3, 3, 'floor')             # the cot room, moved east
    box(46, 50, -3, 3, 'floor')             # the vault
    im = Image.new('RGB', ((X1 - X0 + 1) * B, (Z1 - Z0 + 1) * B))
    for (x, z), m in g.items():
        im.paste(tile(m, x, z), ((x - X0) * B, (z - Z0) * B))
    d = ImageDraw.Draw(im)
    P = lambda x, z: ((x - X0) * B, (z - Z0) * B)
    for i, (x, z) in enumerate([(23, -10), (28, -10), (33, -10), (23, 10), (28, 10), (33, 10)]):
        a, b = P(x, z)
        im.paste(glyph(i), (a, b))
    for i in range(6):                     # the reader board: three glyphs each side of the door, upper west
        a, b = P(16, (-8 + i * 2) if i < 3 else (3 + (i - 3) * 2))
        d.rectangle((a, b - 8, a + 15, b + 23), (44, 50, 56))
        im.paste(glyph(i, i in (0, 3, 4)), (a, b))
    for x, z in ((38, -10), (18, 10)):     # ladders, lower floor to catwalk
        a, b = P(x, z)
        d.rectangle((a + 2, b + 2, a + 13, b + 13), hexc('8a6a3c'))
    a, b = P(28, 0)                        # the Custodian's cradle
    d.rectangle((a - 8, b - 8, a + 23, b + 23), RED)
    for z in (-1, 0, 1):                   # the doors: in behind you, out to the cot room
        for x in (17, 40):
            a, b = P(x, z)
            d.rectangle((a, b, a + 3, b + 15), (168, 150, 84))
    return im, P


Y0, Y1 = -26, -8


def section():
    g = {(x, y): 'rock' for x in range(X0, X1 + 1) for y in range(Y0, Y1 + 1)}

    def box(a, b, c, e, m):
        for x in range(a, b + 1):
            for y in range(c, e + 1):
                g[x, y] = m
    box(15, 52, -16, -10, 'shell')
    box(17, 39, -25, -10, 'shell')
    box(18, 38, -23, -11, 'air')
    box(15, 16, -14, -11, 'air')
    box(40, 44, -14, -11, 'air')
    box(46, 50, -14, -11, 'air')
    for x in (18, 19, 37, 38):
        g[x, -15] = 'walk'
    box(18, 38, -24, -24, 'floor')
    im = Image.new('RGB', ((X1 - X0 + 1) * B, (Y1 - Y0 + 1) * B))
    for (x, y), m in g.items():
        im.paste(tile(m, x, y), ((x - X0) * B, (Y1 - y) * B))
    return im, lambda x, y: ((x - X0) * B, (Y1 - y) * B)


def custodian(open_=False, eye=True):
    """Three blocks tall, two wide: a security frame under frost, one blank lens, no face."""
    im = Image.new('RGBA', (32, 48), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    steel, dark, frost = (112, 120, 126, 255), (58, 64, 69, 255), (214, 226, 232, 255)
    d.rectangle((10, 0, 21, 9), steel)                  # head
    if eye:
        d.rectangle((13, 3, 18, 6), (10, 12, 14, 255)); d.rectangle((15, 4, 16, 5), (214, 240, 250, 255))
    d.rectangle((6, 10, 25, 28), steel)                 # the chest
    d.rectangle((0, 10, 5, 34), dark); d.rectangle((26, 10, 31, 34), dark)  # arms, too long
    d.rectangle((8, 29, 13, 47), dark); d.rectangle((18, 29, 23, 47), dark)  # legs
    if open_:
        d.rectangle((9, 13, 22, 26), (6, 8, 10, 255)); d.rectangle((13, 16, 18, 23), (244, 251, 255, 255))
        d.rectangle((4, 12, 8, 26), steel); d.rectangle((23, 12, 27, 26), steel)
    else:
        d.line((15, 11, 15, 27), dark)
    r = random.Random(3)
    for _ in range(26):
        x, y = r.randint(0, 31), r.randint(0, 47)
        if im.getpixel((x, y))[3]:
            im.putpixel((x, y), frost)
    return im


def clock(text, colour=(214, 240, 250)):
    im = Image.new('RGB', (64, 16), (44, 48, 52))
    d = ImageDraw.Draw(im)
    d.rectangle((2, 3, 61, 12), (10, 14, 16))
    d.text((8, 2), text, colour, font=F(11))
    return im


def caption(d, x, y, lines, size=13, col=INK):
    for l in lines:
        d.text((x, y), l, col, font=F(size))
        y += size + 6
    return y


pl, PP = plan()
se, SP = section()
W, H = 1120, 1660
im = Image.new('RGB', (W, H), BG)
d = ImageDraw.Draw(im)
d.text((32, 16), 'The Watch Floor', INK, font=F(24))
d.text((32, 48), 'A two-floor encounter between the bridge and the cot room. 16 px a block. Door 1, the bridge, the cot and the clock all stay.', DIM, font=F(13))
y = 88
d.text((32, y - 16), 'plan: catwalk ring above, open floor below', DIM, font=F(12))
im.paste(pl, (32, y))
for x, z, t in [(15, -5, 'bridge'), (41, -5, 'cot'), (46, -5, 'vault'), (22, -4, 'lower floor')]:
    a, b = PP(x, z)
    d.text((32 + a + 2, y + b + 2), t, INK, font=F(11))
caption(d, 32 + pl.width + 20, y, ['West wall: the board,', 'six glyphs either side', 'of the door, upper level.', '', 'Top and bottom: six fuse', 'panels, lower level, one', 'glyph each. They shuffle', 'every round.', '', 'Red: the Custodian.', 'Brown: two ladders, the', 'only way back up.', '', 'Yellow: the doors. West', 'seals behind you. East', 'opens when it falls.'], size=12)
y += pl.height + 34
d.text((32, y - 16), 'section through the middle, looking north', DIM, font=F(12))
im.paste(se, (32, y))
sp = custodian().resize((32, 48), Image.NEAREST)
a, b = SP(27, -21)
im.paste(sp, (32 + a, y + b), sp)
caption(d, 32 + se.width + 20, y, ['Upper level to lower', 'floor: eight blocks, a', 'drop that costs hearts.', 'Hall reach grows from 28', 'to 52 blocks east.', '', 'Same depth limit as', 'the pit.'], size=12)
y += se.height + 34
d.text((32, y - 16), 'the Custodian, x3: shut, and with the shell open', DIM, font=F(12))
for i, o in enumerate((False, True)):
    v = custodian(o).resize((96, 144), Image.NEAREST)
    im.paste(v, (32 + i * 124, y), v)
y2 = caption(d, 300, y, ['The vault\'s own rule, turned on you: it moves only while no mind faces it.',
                         'Look at it and it stops dead. Look at a fuse and it comes for you.',
                         'Its touch takes hearts. Nothing hurts it while the shell is shut.', '',
                         'Three rounds; each one you win opens the shell for 15 seconds, and whatever',
                         'you carry does the damage. A third of its health a round. A boss bar shows it.',
                         'It never faces a door: it counts as no mind, so it cannot freeze one.'])
y += 170
d.text((32, y - 16), 'one round', DIM, font=F(12))
steps = [('1  READ', ['From the upper level', 'the board lights the', 'round\'s glyphs for a', 'few seconds, then', 'goes dark.'], [0, 3, 4]),
         ('2  DROP', ['Down to the floor.', 'The fuses have', 'shuffled. Find those', 'glyphs among the six.'], None),
         ('3  PULL', ['Hold use two seconds', 'on each fuse, facing', 'it, so facing away', 'from the Custodian.'], None),
         ('4  BURN', ['All pulled: the shell', 'opens, 15 seconds of', 'damage. Then climb', 'and read again.'], None)]
for i, (t, lines, on) in enumerate(steps):
    x = 32 + i * 266
    d.text((x, y), t, GOLD, font=F(14))
    yy = y + 22
    if on:
        for k in range(6):
            im.paste(glyph(k, k in on, 20), (x + k * 24, yy))
        yy += 28
    caption(d, x, yy, lines, size=12)
y += 170
d.text((32, y - 16), 'how it gets harder', DIM, font=F(12))
caption(d, 32, y, ['Round 1: three glyphs, shown 8 seconds.     Round 2: four glyphs, shown 6.     Round 3: four glyphs, shown 5, and pulled in the order shown.',
                   'The clock: 4:00 for the whole fight, on both levels, counting back. A wrong fuse takes 20 seconds off it.',
                   'At zero, the purge: a white-out, everyone thrown back to the bridge\'s far ledge with half their hearts, and the fight starts over from round 1.',
                   'Purge does not kill (my call): a death is a long walk from a far-off respawn, and the fight needs quick retries.',
                   'With friends it plays as it does in that raid: one reads and calls out, the others pull, someone stares at the Custodian to hold it.',
                   'The clock over the last door stays as it is: it keeps running back and is never explained.'], size=12)
y += 140
for i, t in enumerate(('04:00', '01:12', '00:00')):
    v = clock(t, (232, 104, 92) if t == '00:00' else (214, 240, 250)).resize((256, 64), Image.NEAREST)
    im.paste(v, (32 + i * 280, y))
im = im.crop((0, 0, W, y + 90))
im.save(OUT)
print(im.size)

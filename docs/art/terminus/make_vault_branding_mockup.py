"""ORSA branding for the Terminus Vault (owner, 2026-11-03): sealed as everyone was escaping, and named the way ORSA names
its reactors, an acronym and a nickname in quotes. The Watch Floor's dressing at 16 pixels a block, drawn x2.
Run from anywhere: python3 docs/art/terminus/make_vault_branding_mockup.py
"""
import random
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
BLOCK = HERE.parents[2] / 'src/main/resources/assets/frozendawn/textures/block'
OUT = HERE / 'vault-branding-mockup.png'
B = 16
F = lambda s: ImageFont.truetype('/System/Library/Fonts/Menlo.ttc', s)
BG, INK, DIM, GOLD = (24, 28, 33), (226, 232, 236), (150, 160, 167), (255, 214, 92)
STENCIL = (214, 200, 150)
FONT = {'A': '111/101/111/101/101', 'C': '111/100/100/100/111', 'E': '111/100/110/100/111', 'F': '111/100/110/100/100',
        'G': '111/100/101/101/111', 'I': '111/010/010/010/111', 'L': '100/100/100/100/111', 'N': '101/111/111/111/101',
        'O': '111/101/101/101/111', 'P': '111/101/111/100/100', 'R': '110/101/110/101/101', 'S': '111/100/111/001/111',
        'T': '111/010/010/010/010', 'U': '101/101/101/101/111', 'V': '101/101/101/101/010', 'X': '101/101/010/101/101',
        'D': '110/101/101/101/110', '0': '111/101/101/101/111', '1': '010/110/010/010/111', '3': '111/001/011/001/111',
        '4': '101/101/111/001/001', '-': '000/000/111/000/000', ' ': '000/000/000/000/000', '>': '100/010/001/010/100'}


def stencil(im, x, y, text, colour=STENCIL):
    for ch in text:
        for j, row in enumerate(FONT[ch].split('/')):
            for i, c in enumerate(row):
                if c == '1':
                    im.putpixel((x + i, y + j), colour)
        x += 4


def noise(colours, seed, w=B, h=B):
    r = random.Random(seed)
    im = Image.new('RGB', (w, h))
    px = im.load()
    for x in range(w):
        for y in range(h):
            px[x, y] = tuple(bytes.fromhex(r.choice(colours)))
    return im


def door():
    """Door 1, as branded: the chevrons kept, the facility stencilled over them, and the seal welded in a hurry."""
    im = Image.new('RGB', (48, 48), (58, 64, 69))
    d = ImageDraw.Draw(im)
    for x in (0, 15, 16, 31, 32, 47):
        d.line((x, 0, x, 47), (44, 48, 52))
    for y in range(34, 42):
        for x in range(48):
            if ((x + y) // 4) % 2 == 0:
                d.point((x, y), (168, 150, 84))
    stencil(im, 15, 8, 'ORSA')
    stencil(im, 13, 16, 'CVF-1')
    stencil(im, 7, 24, 'SEALED')
    r = random.Random(5)
    for y in range(1, 47, 3):                       # the weld bead down the seam, rough
        d.point((47 - r.randint(0, 1), y), (196, 150, 96))
    return im


def wall():
    """A stretch of the Watch Floor's upper level, as it was left."""
    w, h = 16, 7
    im = Image.new('RGB', (w * B, h * B))
    for i in range(w):
        for j in range(h):
            im.paste(noise(['5b6064', '555a5e'], i * 31 + j), (i * B, j * B))
    d = ImageDraw.Draw(im)
    for i in range(w):
        im.paste(noise(['8d9296', '83888c'], i), (i * B, 6 * B))
    # A hazard band along the wall, and the way out stencilled on it.
    for x in range(w * B):
        for y in range(3 * B + 12, 3 * B + 16):
            if (x // 6) % 2 == 0:
                im.putpixel((x, y), (168, 150, 84))
    stencil(im, 8, 3 * B + 4, 'EXO > > >')
    flag = Image.open(BLOCK / 'orsa_flag_weathered.png').convert('RGB')
    fw = 2 * B
    im.paste(flag.resize((fw, int(fw * flag.height / flag.width)), Image.NEAREST), (6 * B, B // 2))
    # Emergency lights still on their batteries.
    for x in (3, 12):
        d.rectangle((x * B + 4, B, x * B + 11, B + 5), (150, 34, 30))
        d.rectangle((x * B + 2, B - 2, x * B + 13, B - 1), (70, 76, 82))
    # What was dropped on the way out: an open crate, a case on its side, a hard hat.
    crate = noise(['4f5a48', '48524a'], 9)
    im.paste(crate, (2 * B, 5 * B))
    d.rectangle((2 * B, 5 * B, 3 * B - 1, 5 * B + 2), (24, 28, 30))
    im.paste(noise(['4f5a48', '48524a'], 10), (3 * B, 5 * B))
    d.rectangle((10 * B + 2, 5 * B + 8, 11 * B + 8, 6 * B - 1), (46, 40, 34))
    d.rectangle((13 * B + 3, 5 * B + 10, 13 * B + 12, 6 * B - 1), (214, 190, 80))
    d.rectangle((13 * B + 1, 6 * B - 2, 13 * B + 14, 6 * B - 1), (190, 168, 70))
    for x, y in ((6 * B + 3, 6 * B - 1), (7 * B + 9, 6 * B - 2), (8 * B + 5, 6 * B - 1)):   # loose paper
        d.rectangle((x, y, x + 5, y), (222, 214, 190))
    return im


def custodian():
    im = Image.new('RGB', (32, 48), BG)
    d = ImageDraw.Draw(im)
    steel, dark = (112, 120, 126), (58, 64, 69)
    d.rectangle((10, 0, 21, 9), steel); d.rectangle((13, 3, 18, 6), (10, 12, 14)); d.rectangle((15, 4, 16, 5), (214, 240, 250))
    d.rectangle((6, 10, 25, 28), steel); d.rectangle((0, 10, 5, 34), dark); d.rectangle((26, 10, 31, 34), dark)
    d.rectangle((8, 29, 13, 47), dark); d.rectangle((18, 29, 23, 47), dark)
    d.rectangle((9, 13, 22, 20), (168, 150, 84))
    stencil(im, 10, 14, 'CSU', (30, 32, 34))
    stencil(im, 14, 21, '3', (40, 44, 48))
    return im


NAMES = [('ORSA CVF-1 "Vigil"', 'Containment Vault Facility, Mark 1: the whole sublevel. Stencilled on every door.'),
         ('ORSA ISS-4 "Argus"', 'Integrated Security System: the board and the six fuses. The name on the Watch Floor\'s board.'),
         ('ORSA CSU-3 "Custodian"', 'Containment Security Unit, Mark 3: the boss. Its boss bar reads this.'),
         ('ORSA TR-0 "Terminus"', 'The wand, as it already is. Its item name gains the quotes to match.')]
SEAL = ['New document, on the Watch Floor: Seal Order. "All CVF-1 staff to Exodus by 2200. ISS-4 armed on last badge-out.',
        'CSU-3 remains on duty. Nobody goes back for personal effects." Okoye\'s log is then the one who stayed to see it shut.']

im = Image.new('RGB', (1120, 740), BG)
d = ImageDraw.Draw(im)
d.text((32, 16), 'CVF-1 "Vigil": ORSA, sealed in a hurry', INK, font=F(24))
d.text((32, 48), '16 px a block, drawn x2 unless noted. Same rooms as the Watch Floor mockup; this is what covers them.', DIM, font=F(13))
y = 88
d.text((32, y - 16), 'the Watch Floor\'s upper level, as it was left', DIM, font=F(12))
v = wall().resize((512, 224), Image.NEAREST)
im.paste(v, (32, y))
d.text((576, y - 16), 'door 1, x3', DIM, font=F(12))
im.paste(door().resize((144, 144), Image.NEAREST), (576, y))
d.text((744, y - 16), 'the Custodian, x3', DIM, font=F(12))
im.paste(custodian().resize((96, 144), Image.NEAREST), (744, y))
yy = y + 154
for line in ('Weld bead down the door\'s seam.', 'Stencil on the chest.'):
    d.text((576, yy), line, DIM, font=F(12)); yy += 18
y += 250
d.text((32, y), 'the names', DIM, font=F(12))
y += 20
for name, what in NAMES:
    d.text((32, y), name, GOLD, font=F(14))
    d.text((270, y + 1), what, INK, font=F(12))
    y += 26
y += 16
d.text((32, y), 'the evacuation', DIM, font=F(12))
y += 20
for line in ['Dressing throughout: hazard bands, EXO arrows pointing back the way you came, dim red emergency lights still on their batteries, ORSA flags,',
             'open crates, dropped cases, hard hats, loose paper. All set dressing with no loot, since the spec keeps the wand as the vault\'s only prize.'] + SEAL:
    d.text((32, y), line, INK, font=F(12)); y += 19
im.crop((0, 0, 1120, y + 20)).save(OUT)

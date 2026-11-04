"""How the Watch Floor teaches itself (owner, 2026-11-03: "i did NOT know what i was doing"): a stencilled orders card on the
west wall, seen before the fight starts, and ISS-4's status line during it. 16 pixels a block, drawn x3.
Run from anywhere: python3 docs/art/terminus/make_vault_orders_mockup.py
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
BLOCK = HERE.parents[2] / 'src/main/resources/assets/frozendawn/textures/block'
OUT = HERE / 'vault-orders-mockup.png'
B, K = 16, 3
F = lambda s: ImageFont.truetype('/System/Library/Fonts/Menlo.ttc', s)
BG, INK, DIM, GOLD, PALE = (24, 28, 33), (226, 232, 236), (150, 160, 167), (255, 214, 92), (159, 214, 232)
STENCIL = (214, 200, 150)
FONT = {'A': '111/101/111/101/101', 'B': '110/101/110/101/110', 'C': '111/100/100/100/111', 'D': '110/101/101/101/110', 'E': '111/100/110/100/111',
        'H': '101/101/111/101/101', 'I': '111/010/010/010/111', 'L': '100/100/100/100/111', 'M': '101/111/111/101/101', 'N': '110/101/101/101/101',
        'O': '111/101/101/101/111', 'P': '111/101/111/100/100', 'R': '110/101/110/101/101', 'S': '111/100/111/001/111', 'T': '111/010/010/010/010',
        'U': '101/101/101/101/111', 'W': '101/101/111/111/101', 'Y': '101/101/010/010/010', '1': '010/110/010/010/111', '2': '111/001/111/100/111',
        '3': '111/001/011/001/111', '4': '101/101/111/001/001', '-': '000/000/111/000/000', ' ': '000/000/000/000/000'}


def stencil(im, x, y, text, colour=STENCIL):
    for ch in text:
        for j, row in enumerate(FONT[ch].split('/')):
            for i, c in enumerate(row):
                if c == '1':
                    im.putpixel((x + i, y + j), colour)
        x += 4


def tile(name):
    return Image.open(BLOCK / f'{name}.png').convert('RGB')


def wall():
    """The west wall from the catwalk, z 11 at the left to z -3 at the right: the card, the board's south half, the door."""
    w, h = 15, 5
    im = Image.new('RGB', (w * B, h * B))
    for i in range(w):
        for j in range(h):
            im.paste(tile('vault_shell'), (i * B, j * B))
        im.paste(tile('vault_walkway'), (i * B, (h - 1) * B))
        if i < 9 or i > 13:
            im.paste(tile('vault_mark_hazard'), (i * B, B))
    d = ImageDraw.Draw(im)
    for glyph, z in ((5, 8), (4, 6), (3, 4)):
        im.paste(tile(f'vault_glyph_{glyph}_dark'), ((11 - z) * B, 2 * B))
    door = tile('vault_door_panel').resize((3 * B, 3 * B))
    im.paste(door, (10 * B, B))
    d.rectangle((0, 2 * B, 3 * B - 1, 4 * B - 1), (44, 48, 52))
    d.rectangle((1, 2 * B + 1, 3 * B - 2, 4 * B - 2), (30, 34, 38))
    for n, line in enumerate(('ISS-4 ORDERS', '1 READ BOARD', '2 PULL SAME', '3 BURN SHELL', 'WATCH CSU-3')):
        stencil(im, 1, 2 * B + 2 + n * 6, line)
    return im


def main():
    im = Image.new('RGB', (900, 520), BG)
    d = ImageDraw.Draw(im)
    d.text((24, 16), 'Teaching the Watch Floor', INK, font=F(24))
    d.text((24, 50), 'Proposed. 16 px a block, drawn x3. Nothing here is built.', DIM, font=F(13))
    d.text((24, 80), 'the orders card, on the west wall by the board (3 by 2 blocks), read from the safe catwalk', DIM, font=F(13))
    pic = wall()
    im.paste(pic.resize((pic.width * K, pic.height * K), Image.NEAREST), (24, 102))
    y = 356
    d.text((24, y), "ISS-4's status line, over the hotbar, through the fight", DIM, font=F(13))
    for n, (phase, line) in enumerate((('nobody up top', 'ISS-4: READER TO UPPER LEVEL'), ('board lit', 'ISS-4: READ THE BOARD'),
                                       ('fuses live', 'ISS-4: PULL THE SAME GLYPHS 1/3'), ('round 3', 'ISS-4: PULL IN ORDER SHOWN 2/4'), ('opened', 'CSU-3 SHELL OPEN: 12'))):
        d.text((24, y + 24 + n * 22), phase, DIM, font=F(13))
        d.text((190, y + 24 + n * 22), line, PALE, font=F(14))
    im.save(OUT)


main()

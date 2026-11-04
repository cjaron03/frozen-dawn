"""The two puzzle rooms as places with tools in them, at Minecraft scale (16 pixels a block), shown double size.

Owner, 2026-11-03, after solving both: "these rooms, the puzzles are meant to get around the doors by using the tools in the
environment", the recorder and the A/C each there for a reason of their own, and you improvise.
Run from anywhere: python3 docs/art/terminus/make_vault_puzzle_tools_mockup.py
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from make_drill_hall_mockup import console_block, key_block, tile, STEEL, DARK, TRIM, RIVET

HERE = Path(__file__).resolve().parent
OUT = HERE / 'vault-puzzle-tools-mockup.png'
B = 16
F = lambda s: ImageFont.truetype('/System/Library/Fonts/Menlo.ttc', s)
BG, INK, DIM = (24, 28, 33), (226, 232, 236), (150, 160, 167)
PALE, GOLD, RED, GREEN, GHOST = (159, 214, 232), (255, 214, 92), (232, 104, 92), (120, 200, 130), (205, 235, 245)
SUIT = (196, 150, 70)
GLYPHS = {'A': '010 101 111 101 101', 'B': '110 101 110 101 110', 'C': '011 100 100 100 011', 'D': '110 101 101 101 110', 'E': '111 100 110 100 111',
          'F': '111 100 110 100 100', 'G': '011 100 101 101 011', 'H': '101 101 111 101 101', 'I': '111 010 010 010 111', 'K': '101 101 110 101 101',
          'L': '100 100 100 100 111', 'M': '101 111 111 101 101', 'N': '110 101 101 101 101', 'O': '010 101 101 101 010', 'P': '110 101 110 100 100',
          'R': '110 101 110 101 101', 'S': '011 100 010 001 110', 'T': '111 010 010 010 010', 'U': '101 101 101 101 111', 'V': '101 101 101 101 010',
          'W': '101 101 111 111 101', 'X': '101 101 010 101 101', 'Y': '101 101 010 010 010', '0': '111 101 101 101 111', '1': '010 110 010 010 111',
          '2': '110 001 010 100 111', '3': '110 001 010 001 110', '4': '101 101 111 001 001', '5': '111 100 110 001 110', '6': '011 100 111 101 111',
          '7': '111 001 010 010 010', '8': '111 101 111 101 111', '9': '111 101 111 001 110', '-': '000 000 111 000 000', '.': '000 000 000 000 010', ' ': '000 000 000 000 000'}


def text(im, x, y, words, colour):
    for letter in words:
        for j, row in enumerate(GLYPHS[letter].split()):
            for i, bit in enumerate(row):
                if bit == '1':
                    im.putpixel((x + i, y + j), colour)
        x += 4


def wall(w, h):
    im = Image.new('RGB', (w * B, h * B))
    for x in range(w):
        for y in range(h):
            im.paste(tile('vault_shell'), (x * B, y * B))
        im.paste(tile('vault_floor'), (x * B, (h - 1) * B))
        im.paste(tile('vault_mark_hazard'), (x * B, B))
    return im


DRILL_PLATE = ['ORSA DRILL HALL', 'TWO-PERSON RULE', 'RECORD PARTNER', 'REHEARSE BESIDE', 'NO LIVE DOORS']
MORGUE_PLATE = ['CVF-1 MORGUE', 'STORE KEPT COLD', 'DECEASED ONLY', 'SCANNER READS', 'PULSE AND TEMP']


def placard(im, x, y, lines, colour=(214, 218, 220)):
    """A printed ORSA plate, four blocks by two: fifteen letters a line, five lines."""
    d = ImageDraw.Draw(im)
    d.rectangle((x, y, x + 4 * B - 1, y + 2 * B - 1), (30, 34, 38), (120, 124, 128))
    for i, line in enumerate(lines):
        text(im, x + 2, y + 2 + i * 6, line, colour)


def figure(d, x, y, colour):
    """A body at block scale, two blocks tall, from the feet up."""
    d.rectangle((x + 2, y - 8, x + 9, y - 1), colour)
    d.rectangle((x, y - 20, x + 11, y - 9), colour)
    d.rectangle((x + 2, y - 28, x + 9, y - 21), colour)


def projector(mode):
    """The housing's two faces, sixteen texels each: its slit, and the lens that is the state of the take."""
    body = Image.new('RGB', (B, B), STEEL)
    d = ImageDraw.Draw(body)
    d.rectangle((0, 0, 15, 15), None, TRIM)
    d.rectangle((3, 3, 10, 5), DARK)
    face = Image.new('RGB', (B, B), STEEL)
    d = ImageDraw.Draw(face)
    d.rectangle((0, 0, 15, 15), None, TRIM)
    d.ellipse((2, 2, 13, 13), DARK)
    d.ellipse((4, 4, 11, 11), {'idle': (70, 96, 106), 'recording': RED, 'playing': PALE}[mode])
    d.rectangle((6, 6, 7, 7), (240, 248, 252))
    return body, face


def arch():
    """A post of the scanner's arch: steel, with a dark slot the light bar runs in."""
    im = Image.new('RGB', (B, B), STEEL)
    d = ImageDraw.Draw(im)
    d.line((0, 0, 0, 15), TRIM)
    d.line((15, 0, 15, 15), TRIM)
    d.rectangle((5, 0, 10, 15), DARK)
    return im


def drill(state):
    """Looking west at the way in: the projector over the door, the console before it, the plate on the wall."""
    im = wall(13, 6)
    d = ImageDraw.Draw(im)
    dx = 5 * B
    d.rectangle((dx, 2 * B, dx + 3 * B - 1, 5 * B - 1), (12, 14, 16))
    # The projector, on the wall over the door: a housing two blocks wide, a lens that is the state of the take.
    px, py = dx + 8, B - 4
    d.rectangle((px, py, px + 2 * B - 1, py + B - 1), STEEL, TRIM)
    d.rectangle((px + 3, py + 3, px + 10, py + 5), DARK)
    lens = {'idle': (70, 96, 106), 'recording': RED, 'playing': PALE}[state]
    d.ellipse((px + 16, py + 2, px + 27, py + 13), DARK)
    d.ellipse((px + 18, py + 4, px + 25, py + 11), lens)
    d.rectangle((px + 20, py + 6, px + 21, py + 7), (240, 248, 252))
    lamp = {'idle': (52, 66, 72), 'recording': RED, 'playing': GREEN}[state]
    for i in range(3):
        im.paste(console_block(lamp), (dx + i * B, 4 * B))
    placard(im, B // 2, 2 * B, DRILL_PLATE)
    if state == 'recording':
        figure(d, 9 * B + 4, 5 * B, SUIT)
        for i in range(0, 60, 6):
            d.point((px + 22 + i * 2, py + 9 + i), RED)
    if state == 'playing':
        gx, gy = 10 * B + 2, 5 * B
        figure(d, gx, gy, GHOST)
        for i in range(0, 100, 5):
            t = i / 100
            x, y = px + 22 + (gx + 6 - px - 22) * t, py + 9 + (gy - 18 - py - 9) * t
            d.rectangle((x, y, x + 1, y + 1), PALE)
        for x, y in ((gx - 3, gy - 24), (gx + 14, gy - 12), (gx - 2, gy - 6), (gx + 13, gy - 26)):
            d.point((x, y), PALE)
    return im


def dial(setting):
    """The dial's box, two blocks tall, on the block east of the unit: three lamps, the word, and a pointer."""
    im = Image.new('RGB', (B, 2 * B))
    im.paste(tile('vault_shell'), (0, 0))
    im.paste(tile('vault_shell'), (0, B))
    d = ImageDraw.Draw(im)
    bx, by = 1, 2
    d.rectangle((bx, by, bx + 13, by + 27), STEEL, TRIM)
    lit = {'low': 0, 'mid': 1, 'max': 2}[setting]
    for i, colour in enumerate((GREEN, GOLD, RED)):
        d.rectangle((bx + 3 + i * 3, by + 3, bx + 4 + i * 3, by + 4), colour if i <= lit else (40, 46, 50))
    text(im, bx + 1, by + 7, setting.upper(), {'low': GREEN, 'mid': GOLD, 'max': RED}[setting])
    cx, cy = bx + 6, by + 19
    d.ellipse((cx - 5, cy - 5, cx + 5, cy + 5), (30, 34, 38), RIVET)
    d.line((cx, cy, *{'low': (cx - 4, cy + 2), 'mid': (cx, cy - 4), 'max': (cx + 4, cy + 2)}[setting]), GOLD)
    return im


def grille():
    """The A/C's face, three blocks by two."""
    im = Image.new('RGB', (3 * B, 2 * B), (150, 158, 164))
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 3 * B - 1, 2 * B - 1), None, (90, 96, 100))
    for y in range(4, 2 * B - 4, 3):
        d.line((3, y, 3 * B - 4, y), (64, 70, 76))
    return im


def ac(setting):
    """Looking north: the cold store's A/C on the wall, three blocks by two, and its dial. Only MAX blows hard enough."""
    im = wall(11, 6)
    d = ImageDraw.Draw(im)
    x0, y0 = 3 * B, 2 * B
    im.paste(grille(), (x0, y0))
    im.paste(dial(setting), (x0 + 3 * B, y0))
    if setting == 'max':
        for i, (x, y) in enumerate(((3.2, 4.2), (3.8, 4.5), (4.4, 4.1), (5.0, 4.4), (5.6, 4.2), (3.5, 4.8), (4.2, 4.9), (4.9, 4.7), (5.4, 4.9))):
            d.rectangle((int(x * B), int(y * B), int(x * B) + 1, int(y * B) + 1), PALE)
        figure(d, 4 * B + 2, 5 * B, (130, 170, 190))
    elif setting == 'mid':
        d.rectangle((4 * B, 4 * B + 3, 4 * B + 1, 4 * B + 4), PALE)
    return im


def panel(state, reading=True):
    """The scanner's screen, two blocks square: a pulse trace and a temperature; flat, in green, for the dead."""
    im = Image.new('RGB', (2 * B, 2 * B), (10, 13, 16))
    d = ImageDraw.Draw(im)
    colour = {'alive': RED, 'cold': GOLD, 'none': GREEN}[state]
    d.rectangle((0, 0, 31, 31), None, colour)
    text(im, 3, 3, 'NONE' if state == 'none' else 'VITALS', colour)
    y = 15
    if state == 'none':
        d.line((2, y, 29, y), colour)
    else:
        pts = [(2, y), (8, y), (10, y - 4), (12, y + 3), (14, y), (20, y), (22, y - 4), (24, y + 3), (26, y), (29, y)]
        d.line(pts, colour)
    if reading:
        text(im, 3, 22, {'alive': '36.8 C', 'cold': '21.4 C', 'none': '11.2 C'}[state], colour)
    return im


def scanner(state):
    """Looking east: a body-scanner arch over the plate, a bar of light going up and down it, the door behind."""
    im = wall(11, 6)
    d = ImageDraw.Draw(im)
    dx = 4 * B
    d.rectangle((dx, 2 * B, dx + 3 * B - 1, 5 * B - 1), (26, 30, 34) if state != 'none' else (8, 10, 12))
    if state != 'none':
        d.line((dx + 3 * B // 2, 2 * B, dx + 3 * B // 2, 5 * B - 1), (70, 76, 80))
    d.rectangle((dx - 6, 2 * B - 6, dx + 3 * B + 5, 2 * B - 1), GREEN if state == 'none' else PALE)
    # The arch: two steel posts and a head, a block clear of the door all round.
    for x in (dx - B, dx + 3 * B):
        d.rectangle((x, 2 * B, x + B - 1, 5 * B - 1), STEEL, TRIM)
        d.rectangle((x + 5, 2 * B + 4, x + 10, 5 * B - 5), DARK)
    d.rectangle((dx - B, B, dx + 4 * B - 1, 2 * B - 7), STEEL, TRIM)
    y = {'alive': 3 * B + 10, 'cold': 4 * B + 2, 'none': 4 * B + 12}[state]
    d.line((dx - B + 8, y, dx + 4 * B - 9, y), GREEN if state == 'none' else RED)
    x = dx + 3 * B // 2 - 6
    figure(d, x, 5 * B + 4, {'alive': SUIT, 'cold': (150, 170, 150), 'none': (130, 170, 190)}[state])
    im.paste(panel(state), (B // 2 - 4, 2 * B))
    return im


def main():
    out = Image.new('RGB', (1160, 1040), BG)
    d = ImageDraw.Draw(out)
    y = 12
    d.text((24, y), 'The drill hall, looking west: ORSA rehearsed the two-key turn here against a recorded partner. Double size.', INK, F(14))
    y += 24
    for i, (state, label) in enumerate((('idle', 'the projector on the wall, the console, the plate'), ('recording', 'press: it records you. press again when you are ready'), ('playing', 'it plays: particles stream to the figure'))):
        im = drill(state)
        out.paste(im.resize((im.width * 2, im.height * 2), Image.NEAREST).crop((0, 0, 370, 192)), (24 + i * 380, y))
        d.text((24 + i * 380, y + 196), label, DIM, F(11))
    y += 226
    d.text((24, y), 'The Morgue, looking north: the store\'s A/C on the wall. It runs on LOW. Crank it to MAX and stand in the blast.', INK, F(14))
    y += 24
    for i, (setting, label) in enumerate((('low', 'as they left it: keeps the store cold'), ('mid', 'colder, nothing more'), ('max', 'MAX: your body temperature falls, and hearts with it'))):
        im = ac(setting)
        out.paste(im.resize((im.width * 2, im.height * 2), Image.NEAREST), (24 + i * 380, y))
        d.text((24 + i * 380, y + 196), label, DIM, F(11))
    y += 226
    d.text((24, y), 'The scanner, looking east: it passes the dead only, by pulse and temperature.', INK, F(14))
    y += 24
    for i, (state, label) in enumerate((('alive', 'warm: VITALS, the door stays shut'), ('cold', 'cooling: still alive to it'), ('none', 'cold enough: NONE, flat line, it opens'))):
        im = scanner(state)
        out.paste(im.resize((im.width * 2, im.height * 2), Image.NEAREST), (24 + i * 380, y))
        d.text((24 + i * 380, y + 196), label, DIM, F(11))
    y += 226
    d.text((24, y), 'Plates: the drill hall\'s as drawn. The Morgue\'s, by its west door:', INK, F(14))
    y += 24
    m = Image.new('RGB', (4 * B, 2 * B))
    placard(m, 0, 0, MORGUE_PLATE)
    out.paste(m.resize((m.width * 4, m.height * 4), Image.NEAREST), (24, y))
    out.crop((0, 0, 1160, y + 2 * B * 4 + 16)).save(OUT)


if __name__ == '__main__':
    main()

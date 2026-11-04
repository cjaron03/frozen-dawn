"""The Morgue's A/C as a machine and its scanner's screen over the door, at Minecraft scale (16 pixels a block), shown double size.

Owner, 2026-11-04, after Check 8: "the AC unit looks like a flat object on the wall, and the scanner need to have its readings
visible from the door or something".
Run from anywhere: python3 docs/art/terminus/make_morgue_tools_mockup.py
"""
from pathlib import Path
from PIL import Image, ImageDraw
from make_drill_hall_mockup import tile, STEEL, DARK, TRIM, RIVET
from make_vault_puzzle_tools_mockup import B, F, BG, INK, DIM, PALE, GOLD, RED, GREEN, SUIT, GLYPHS, text, wall, figure

HERE = Path(__file__).resolve().parent
OUT = HERE / 'morgue-tools-mockup.png'
CASE, CASE_LIT, CASE_DARK, SIDE = (132, 140, 146), (168, 176, 182), (84, 90, 96), (96, 104, 110)
RECESS, FROST, ICE, PIPE, PIPE_DARK = (18, 22, 26), (214, 234, 242), (170, 214, 232), (120, 150, 166), (78, 102, 116)
LIT = {'low': GREEN, 'mid': GOLD, 'max': RED}


def big(im, x, y, words, colour):
    """Letters at twice the size, so the screen reads from across the room."""
    for letter in words:
        for j, row in enumerate(GLYPHS[letter].split()):
            for i, bit in enumerate(row):
                if bit == '1':
                    for a in (0, 1):
                        for b in (0, 1):
                            im.putpixel((x + 2 * i + a, y + 2 * j + b), colour)
        x += 8


def cabinet(setting):
    """The unit's front, three blocks by two: a case, a dark throat, six slats standing proud of it, and a tray under them."""
    im = Image.new('RGB', (3 * B, 2 * B), CASE)
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 3 * B - 1, 2 * B - 1), None, CASE_DARK)
    d.line((1, 1, 3 * B - 2, 1), CASE_LIT)
    d.rectangle((0, 2 * B - 6, 3 * B - 1, 2 * B - 1), CASE_DARK)
    d.line((1, 2 * B - 5, 3 * B - 2, 2 * B - 5), CASE)
    for x in (2, 3 * B - 3):
        for y in (3, 2 * B - 9):
            im.putpixel((x, y), RIVET)
    d.rectangle((4, 4, 3 * B - 5, 2 * B - 9), RECESS)
    for y in range(5, 2 * B - 10, 3):
        d.line((5, y, 3 * B - 6, y), CASE_LIT)
        d.line((5, y + 1, 3 * B - 6, y + 1), SIDE)
    if setting != 'low':
        for y in range(5, 2 * B - 10, 3):
            for x in range(6, 3 * B - 6, 5 if setting == 'max' else 11):
                im.putpixel((x, y), FROST)
    return im


def box(setting):
    """The dial's box, on the unit's east, proud of the wall by a third of a block: three lamps, the word, and a knob that stands out."""
    im = Image.new('RGB', (B, 2 * B), CASE)
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, B - 1, 2 * B - 1), None, CASE_DARK)
    d.line((1, 1, B - 2, 1), CASE_LIT)
    lit = ['low', 'mid', 'max'].index(setting)
    for i, colour in enumerate((GREEN, GOLD, RED)):
        d.rectangle((3 + i * 4, 3, 4 + i * 4, 4), colour if i <= lit else (40, 46, 50))
    d.rectangle((1, 7, B - 2, 13), (20, 24, 28))
    text(im, 2, 8, setting.upper(), LIT[setting])
    cx, cy = 7, 22
    d.ellipse((cx - 5, cy - 5, cx + 5, cy + 5), (30, 34, 38), CASE_LIT)
    d.line((cx, cy, *{'low': (cx - 4, cy + 2), 'mid': (cx, cy - 4), 'max': (cx + 4, cy + 2)}[setting]), GOLD)
    return im


def pipes(d, x, top, bottom):
    """Two lagged pipes, six texels wide with a gap, frost on the bands."""
    for px in (x, x + 10):
        d.rectangle((px, top, px + 5, bottom), PIPE)
        d.line((px + 5, top, px + 5, bottom), PIPE_DARK)
        d.line((px + 1, top, px + 1, bottom), FROST)
        for y in range(top + 4, bottom, 8):
            d.line((px, y, px + 5, y), PIPE_DARK)


def ac(setting):
    """Looking north: the A/C, standing out from the wall, pipes up through the band into the roof, frost on the tray at MAX."""
    im = wall(9, 6)
    d = ImageDraw.Draw(im)
    x0, y0 = 2 * B, 2 * B
    pipes(d, x0 + B + 2, 0, y0 - 1)
    im.paste(cabinet(setting), (x0, y0))
    im.paste(box(setting), (x0 + 3 * B, y0))
    # The case's shadow on the wall and floor: it stands out a block.
    d.rectangle((x0 + 3 * B + B, y0 + 2, x0 + 4 * B + 2, y0 + 2 * B + 1), (24, 28, 32))
    d.rectangle((x0 + 2, y0 + 2 * B, x0 + 4 * B + 2, y0 + 2 * B + 3), (24, 28, 32))
    if setting == 'max':
        for x in range(x0 + 3, x0 + 3 * B - 3, 6):
            d.line((x, y0 + 2 * B, x, y0 + 2 * B + 2 + x % 3), ICE)
        for x, y in ((2.3, 4.3), (3.0, 4.6), (3.7, 4.2), (4.2, 4.7), (2.7, 4.9), (3.4, 5.0), (4.5, 4.4)):
            d.rectangle((int(x * B), int(y * B), int(x * B) + 1, int(y * B) + 1), PALE)
        figure(d, 3 * B + 2, 5 * B, (130, 170, 190))
    return im


def oblique():
    """From the south-east: the same unit as a solid thing, eleven texels deep, the side and top in view."""
    w, h = 6 * B, 5 * B
    im = Image.new('RGB', (w, h), (0, 0, 0))
    for x in range(6):
        for y in range(5):
            im.paste(tile('vault_shell'), (x * B, y * B))
    d = ImageDraw.Draw(im)
    fx, fy, depth = B // 2 + 12, 2 * B + 2, 11
    sx, sy = -depth, depth // 2  # A point out from the wall moves left and down on the sheet.
    pipes(d, fx + B + 2 + sx, 0, fy + sy - 1)
    W, H = 3 * B, 2 * B
    # The side, in slats of shade, then the top; the box the same, shallower.
    for i in range(depth):
        d.line((fx + W + sx + i, fy + sy - i // 2, fx + W + sx + i, fy + H + sy - i // 2), SIDE if i % 4 else CASE_DARK)
    for j in range(depth // 2 + 1):
        d.line((fx + sx + 2 * j, fy + sy - j, fx + W + sx + 2 * j, fy + sy - j), CASE_LIT)
    im.paste(cabinet('max'), (fx + sx, fy + sy))
    return im


def oblique_view():
    """A three-quarter view drawn block by block."""
    im = oblique()
    d = ImageDraw.Draw(im)
    fx, fy, depth = B // 2 + 12, 2 * B + 2, 11
    W, H = 3 * B, 2 * B
    # The dial's box on the wall to the east, a third of the unit's depth.
    bx, by = fx + W + 1, fy
    for i in range(4):
        d.line((bx + B - 4 + i, by + 2 - i // 2, bx + B - 4 + i, by + H + 2 - i // 2), SIDE)
    im.paste(box('max'), (bx - 4, by + 2))
    return im


def screen(state, reading=True):
    """The scanner's screen, seven blocks by one, over the door: its word, a trace, and the reading in figures twice the size."""
    im = Image.new('RGB', (7 * B, B), (10, 13, 16))
    d = ImageDraw.Draw(im)
    colour = {'idle': (70, 76, 80), 'alive': RED, 'cold': GOLD, 'none': GREEN}[state]
    d.rectangle((0, 0, 7 * B - 1, B - 1), None, colour)
    text(im, 3, 2, 'NONE' if state == 'none' else 'VITALS', colour)
    y = 11
    if state in ('none', 'idle'):
        d.line((3, y, 48, y), colour)
    else:
        d.line([(3, y), (10, y), (12, y - 3), (14, y + 2), (16, y), (26, y), (28, y - 3), (30, y + 2), (32, y), (42, y), (44, y - 3), (46, y + 2), (48, y)], colour)
    if reading and state != 'idle':
        big(im, 54, 3, {'alive': '36.8', 'cold': '21.4', 'none': '11.2'}[state], colour)
        big(im, 54 + 34, 3, ' C', colour)
    return im


def door(state):
    """Looking east, from the way in: the door, its lamp, the screen over it in the top row, and the arch two blocks this side of it."""
    im = wall(11, 6)
    d = ImageDraw.Draw(im)
    dx = 4 * B
    opening = state == 'none'
    d.rectangle((dx, 2 * B, dx + 3 * B - 1, 5 * B - 1), (8, 10, 12) if opening else (26, 30, 34))
    if not opening:
        d.line((dx + 3 * B // 2, 2 * B, dx + 3 * B // 2, 5 * B - 1), (70, 76, 80))
    d.rectangle((dx, B + 4, dx + 3 * B - 1, 2 * B - 1), GREEN if opening else (64, 70, 74))
    # The screen's housing, and the screen in the top row.
    d.rectangle((2 * B - 2, 0, 9 * B + 1, B + 1), CASE_DARK)
    im.paste(screen(state), (2 * B, 0))
    # The arch, nearer, its head now up under the roof, so it hides nothing.
    for x in (3 * B, 7 * B):
        d.rectangle((x, B, x + B - 1, 5 * B - 1), STEEL, TRIM)
        d.rectangle((x + 5, B + 4, x + 10, 5 * B - 5), DARK)
    d.rectangle((3 * B, B - 1, 8 * B - 1, B + 3), STEEL, TRIM)
    y = {'idle': None, 'alive': 3 * B + 10, 'cold': 4 * B + 2, 'none': 4 * B + 12}[state]
    if y:
        d.line((3 * B + 8, y, 8 * B - 9, y), GREEN if opening else RED)
        figure(d, dx + 3 * B // 2 - 6, 5 * B + 4, {'alive': SUIT, 'cold': (150, 170, 150), 'none': (130, 170, 190)}[state])
    return im


def plan():
    """From above, one cell a block: where everything stands now."""
    w, h = 13, 11
    im = Image.new('RGB', (w * B, h * B), (40, 44, 48))
    d = ImageDraw.Draw(im)
    cell = lambda x, z, colour: d.rectangle(((x - 83) * B + 1, (z + 5) * B + 1, (x - 83) * B + B - 2, (z + 5) * B + B - 2), colour)
    for x in range(83, 96):
        for z in range(-5, 6):
            cell(x, z, (70, 76, 80) if x in (83, 95) or abs(z) == 5 else (30, 34, 38))
    for z in (-1, 0, 1):
        cell(83, z, (20, 24, 28))
        cell(95, z, GOLD)
    for x in (87, 88, 89):
        cell(x, -4, CASE_LIT)
        for z in (-3, -2):
            cell(x, z, (50, 80, 100))
    cell(90, -4, CASE)
    for z in (-2, 2):
        cell(92, z, STEEL)
    for z in (-1, 0, 1):
        cell(92, z, RED)
    for z in range(-3, 4):
        cell(94, z, GREEN)
    return im


def main():
    out = Image.new('RGB', (1160, 1000), BG)
    d = ImageDraw.Draw(out)
    y = 12
    d.text((24, y), 'The A/C, looking north: a cabinet standing eleven texels out from the wall, not a picture on it. Double size.', INK, F(14))
    y += 24
    for i, (setting, label) in enumerate((('low', 'LOW: pipes to the roof, slats, a tray'), ('mid', 'MID: frost starts on the slats'), ('max', 'MAX: frost, ice off the tray, the blast'))):
        im = ac(setting)
        out.paste(im.resize((im.width * 2, im.height * 2), Image.NEAREST), (24 + i * 300, y))
        d.text((24 + i * 300, y + 196), label, DIM, F(11))
    v = oblique_view()
    out.paste(v.resize((v.width * 2, v.height * 2), Image.NEAREST), (930, y))
    d.text((930, y + 166), 'as a solid, from the south-east', DIM, F(11))
    y += 226
    d.text((24, y), 'The screen, looking east: over the door, lit in the dark. The arch moves two blocks west, its head up under the roof.', INK, F(14))
    y += 24
    states = (('idle', 'nobody on the plate'), ('alive', 'warm: red, and the door stays shut'), ('cold', 'cooling: gold'), ('none', 'at the line: NONE, flat, green, it opens'))
    for i, (state, label) in enumerate(states[:3]):
        im = door(state)
        out.paste(im.resize((im.width * 2, im.height * 2), Image.NEAREST).crop((0, 0, 352, 192)), (24 + i * 370, y))
        d.text((24 + i * 370, y + 196), label, DIM, F(11))
    y += 226
    im = door('none')
    out.paste(im.resize((im.width * 2, im.height * 2), Image.NEAREST).crop((0, 0, 352, 192)), (24, y))
    d.text((24, y + 196), states[3][1], DIM, F(11))
    s = screen('cold')
    out.paste(s.resize((s.width * 4, s.height * 4), Image.NEAREST), (400, y))
    d.text((400, y + 70), 'the screen at four times, seven blocks wide', DIM, F(11))
    p = plan()
    out.paste(p, (870, y))
    d.text((870, y + p.height + 4), 'from above, west on the left:', DIM, F(11))
    d.text((870, y + p.height + 18), 'A/C pale, blast blue, plate red,', DIM, F(11))
    d.text((870, y + p.height + 32), 'door gold, screen green', DIM, F(11))
    y += 250
    d.text((24, y), 'Sightlines: from the plate you look up 49 degrees at it. From the way in and from the blast it clears the arch head.', INK, F(14))
    out.crop((0, 0, 1160, y + 34)).save(OUT)


if __name__ == '__main__':
    main()

"""The vault's last rooms, between the east gate and the cot room, at Minecraft scale (16 pixels a block).

Owner, 2026-11-03: "one room actually for the solar map, thats a mess hall, then two other rooms with puzzles", "UNIQUE
puzzles", and of the three offered, the Two-Person Rule and the Morgue Gate.
Run from anywhere: python3 docs/art/terminus/make_vault_last_rooms_mockup.py
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from make_system_plot_mockup import plot

HERE = Path(__file__).resolve().parent
OUT = HERE / 'vault-last-rooms-mockup.png'
TEX = HERE.parents[2] / 'src/main/resources/assets/frozendawn/textures/block'
B = 16
F = lambda s: ImageFont.truetype('/System/Library/Fonts/Menlo.ttc', s)
BG, INK, DIM, GOLD, RED, PALE, GREEN = (24, 28, 33), (226, 232, 236), (150, 160, 167), (255, 214, 92), (232, 104, 92), (159, 214, 232), (120, 200, 130)
ROCK, FLOOR, WOOD, EDGE, GREY = (46, 50, 54), (47, 52, 56), (92, 74, 54), (90, 96, 100), (120, 124, 128)
SUIT, GHOST = (196, 150, 70), (170, 225, 245)


def wall(w, h):
    shell = Image.open(TEX / 'vault_shell.png').convert('RGB').crop((0, 0, B, B))
    im = Image.new('RGB', (w * B, h * B))
    for x in range(w):
        for y in range(h):
            im.paste(shell, (x * B, y * B))
    return im


def rect(d, x0, z0, x1, z1, colour, outline=None):
    d.rectangle((x0 * B, z0 * B, (x1 + 1) * B - 1, (z1 + 1) * B - 1), colour, outline)


def plan():
    """Top down, north up, x 38 to 108 and z -12 to 12."""
    im = Image.new('RGB', (71 * B, 25 * B), ROCK)
    d = ImageDraw.Draw(im)
    X, Z = lambda x: x - 38, lambda z: z + 12
    room = lambda a, b, c, e: rect(d, X(a), Z(c), X(b), Z(e), FLOOR, EDGE)
    rect(d, 0, Z(-11), X(39), Z(11), (30, 34, 38))
    room(41, 64, -10, 10)
    room(66, 82, -7, 7)
    room(84, 94, -4, 4)
    room(96, 99, -3, 3)
    room(101, 105, -3, 3)
    for x in (40, 65, 83, 95, 100):
        rect(d, X(x), Z(-1), X(x), Z(1), GOLD if x in (83, 95) else GREY)
    rect(d, X(41), Z(-10), X(64), Z(-10), PALE)
    for x in range(44, 62, 6):
        for z in (-4, 3):
            rect(d, X(x), Z(z), X(x + 3), Z(z + 1), WOOD)
    rect(d, X(67), Z(-1), X(67), Z(1), PALE)
    rect(d, X(72), Z(-7), X(72), Z(-7), GOLD)
    rect(d, X(79), Z(7), X(79), Z(7), GOLD)
    rect(d, X(88), Z(-1), X(90), Z(1), PALE)
    rect(d, X(94), Z(-1), X(94), Z(1), RED)
    for label, x, z in (('Mess hall', 49, -1), ('Drill hall', 71, 0), ('Morgue', 85, -3), ('cot', 96, 0), ('vault', 101, 0)):
        d.text((X(x) * B, Z(z) * B), label, INK, F(13))
    d.text((X(45) * B, Z(-9) * B), 'the System Plot, north wall', DIM, F(11))
    d.text((X(68) * B, Z(-1) * B - 14), 'recorder', DIM, F(10))
    d.text((X(73) * B, Z(-6) * B), 'key A', GOLD, F(11))
    d.text((X(79) * B - 16, Z(6) * B), 'key B', GOLD, F(11))
    d.text((X(87) * B, Z(2) * B), 'cold vent', PALE, F(11))
    return im


def figure(d, x, z, colour):
    d.rectangle((x * B + 3, z * B + 3, x * B + 12, z * B + 12), colour)


def drill(step):
    """The drill hall from above: x 66 to 82, z -7 to 7, with a dotted walk for what has been recorded."""
    im = Image.new('RGB', (19 * B, 17 * B), ROCK)
    d = ImageDraw.Draw(im)
    rect(d, 1, 1, 17, 15, FLOOR, EDGE)
    rect(d, 2, 7, 2, 9, PALE)
    rect(d, 7, 1, 7, 1, GOLD)
    rect(d, 14, 15, 14, 15, GOLD)
    rect(d, 18, 7, 18, 9, GREEN if step == 2 else GREY)
    if step == 0:
        for i in range(0, 5):
            d.rectangle(((3 + i) * B + 7, (8 - i * 3 // 2) * B + 7, (3 + i) * B + 8, (8 - i * 3 // 2) * B + 8), SUIT)
        figure(d, 7, 2, SUIT)
    if step == 1:
        figure(d, 3, 8, GHOST)
        figure(d, 14, 14, SUIT)
    if step == 2:
        figure(d, 7, 2, GHOST)
        figure(d, 14, 14, SUIT)
    return im


def morgue(alive):
    """Looking east at the gate: the scanner's arch, its plate, and what it reads."""
    im = wall(11, 6)
    d = ImageDraw.Draw(im)
    for x in range(11):
        d.rectangle((x * B, 5 * B, x * B + 15, 6 * B - 1), (56, 60, 64))
    dx, top = 4 * B, 2 * B
    d.rectangle((dx, top, dx + 3 * B - 1, 5 * B - 1), (26, 30, 34) if alive else (8, 10, 12))
    if alive:
        d.line((dx + 3 * B // 2, top, dx + 3 * B // 2, 5 * B - 1), (70, 76, 80))
    d.rectangle((dx - 6, top - 6, dx + 3 * B + 5, top - 1), PALE if alive else GREEN)
    panel = (B, 2 * B)
    d.rectangle((panel[0], panel[1], panel[0] + 2 * B - 1, panel[1] + 2 * B - 1), (14, 17, 20), RED if alive else GREEN)
    d.text((panel[0] + 3, panel[1] + 4), 'VITALS' if alive else 'NONE', RED if alive else GREEN, F(9))
    for i in range(10 if alive else 1):
        x, y = panel[0] + 4 + (i % 5) * 5, panel[1] + 18 + (i // 5) * 5
        d.rectangle((x, y, x + 3, y + 3), RED)
    x = dx + 3 * B // 2 - 7
    d.rectangle((x, 3 * B, x + 13, 5 * B + 6), SUIT if alive else (130, 150, 160))
    return im


def main():
    p = plan()
    out = Image.new('RGB', (p.width + 48, 1300), BG)
    d = ImageDraw.Draw(out)
    y = 12
    d.text((24, y), 'Plan: east gate, mess hall, drill hall, Morgue, then the cot room and vault moved east. Gold doors are the puzzles.', INK, F(14))
    y += 24
    out.paste(p, (24, y))
    y += p.height + 14
    d.text((24, y), 'The Two-Person Rule, from above. Gold you, pale your recording.', INK, F(14))
    y += 24
    labels = ('1: press record, walk to key A, hold it', '2: replay starts, you run for key B', '3: both held at once, the door opens')
    for i, label in enumerate(labels):
        x = 24 + i * (19 * B + 24)
        out.paste(drill(i), (x, y))
        d.text((x, y + 17 * B + 6), label, DIM, F(12))
    y += 17 * B + 34
    d.text((24, y), 'The Morgue Gate, facing east. Shown double size.', INK, F(14))
    y += 24
    for i, (alive, label) in enumerate(((True, 'alive: the scanner reads you and stays shut'), (False, 'at one heart, after the vent: it reads nobody'))):
        x = 24 + i * (11 * B * 2 + 24)
        out.paste(morgue(alive).resize((11 * B * 2, 6 * B * 2), Image.NEAREST), (x, y))
        d.text((x, y + 12 * B + 6), label, DIM, F(12))
    y += 12 * B + 34
    d.text((24, y), 'Mess hall, north wall: the System Plot, as approved.', INK, F(14))
    y += 24
    m = wall(26, 10)
    m.paste(plot(), (B, B))
    out.paste(m, (24, y))
    out.crop((0, 0, out.width, y + m.height + 16)).save(OUT)


if __name__ == '__main__':
    main()

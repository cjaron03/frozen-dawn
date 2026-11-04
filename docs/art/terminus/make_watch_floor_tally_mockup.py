"""The Watch Floor's tally over the east gate and the Custodian's shell opening, at Minecraft scale (16 pixels a block),
shown three times over. Owner, 2026-11-03: lights over the door "that go out for every successful fuse break", and "a
animation for when the custodians core opens". Built from the textures in the game.
Run from anywhere: python3 docs/art/terminus/make_watch_floor_tally_mockup.py
"""
import random
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
OUT = HERE / 'watch-floor-tally-mockup.png'
TEX = HERE.parents[2] / 'src/main/resources/assets/frozendawn/textures'
B, S = 16, 3
F = lambda s: ImageFont.truetype('/System/Library/Fonts/Menlo.ttc', s)
BG, INK, DIM = (24, 28, 33), (226, 232, 236), (150, 160, 167)
PALE, GLOW, SOCKET, RIM = (214, 240, 250), (244, 251, 255), (20, 24, 27), (58, 64, 69)


def lamp(lit):
    im = Image.new('RGBA', (B, B), RIM)
    d = ImageDraw.Draw(im)
    d.rectangle((3, 3, 12, 12), SOCKET)
    if lit:
        d.rectangle((4, 4, 11, 11), PALE)
        d.rectangle((6, 6, 9, 9), GLOW)
    else:
        d.rectangle((4, 4, 11, 11), (34, 38, 42))
        d.point((5, 5), (70, 76, 80))
    return im


def clock():
    im = Image.new('RGBA', (B, B), (14, 17, 20))
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 15, 15), outline=RIM)
    for x in (2, 6, 10):
        d.rectangle((x, 6, x + 2, 10), outline=PALE)
    return im


def wall(out):
    """Looking east along the upper level: z -9 to 9, the ring underfoot, ceiling above. North is on the left."""
    shell = Image.open(TEX / 'block/vault_shell.png').convert('RGBA').crop((0, 0, B, B))
    walk = Image.open(TEX / 'block/vault_walkway.png').convert('RGBA').crop((0, 0, B, B))
    door = Image.open(TEX / 'block/vault_door_panel.png').convert('RGBA').crop((0, 0, B, B))
    w, h = 19, 5
    im = Image.new('RGBA', (w * B, h * B))
    at = lambda z, y: ((z + 9) * B, (-11 - y) * B)
    for z in range(-9, 10):
        for y in range(-14, -10):
            im.paste(shell, at(z, y))
        im.paste(walk, at(z, -15))
    for z in (-1, 0, 1):
        for y in (-14, -13, -12):
            im.paste(door, at(z, y))
    for z in (-3, 3):
        im.paste(clock(), at(z, -13))
    for i, z in enumerate(range(-5, 6)):
        im.paste(lamp(i >= out), at(z, -11))
    return im


def fit(part, width):
    return part.resize((max(1, width), part.height), Image.NEAREST)


def custodian(swing, core, steam):
    """Front on. {swing}: each half of the chest plate's width as seen, 8 shut, 1 edge-on. {core}: how far out, 0 to 2."""
    tex = Image.open(TEX / 'entity/custodian.png').convert('RGBA')
    cavity = Image.open(TEX / 'entity/custodian_open.png').convert('RGBA')
    im = Image.new('RGBA', (32 + 16, 48), (0, 0, 0, 0))
    ox = 8
    im.alpha_composite(tex.crop((10, 10, 22, 20)), (ox + 10, 0))
    im.alpha_composite(cavity.crop((10, 30, 30, 49)), (ox + 6, 10))
    im.alpha_composite(tex.crop((70, 6, 76, 31)), (ox + 0, 10))
    im.alpha_composite(tex.crop((70, 6, 76, 31)), (ox + 26, 10))
    for x in (11, 17):
        im.alpha_composite(tex.crop((72, 40, 78, 59)), (ox + x, 29))
    d = ImageDraw.Draw(im)
    if core:
        half = 3 + core
        d.rectangle((ox + 16 - half, 15 - core, ox + 15 + half, 24 + core), GLOW)
        if core == 2:
            d.rectangle((ox + 16 - half, 15 - core, ox + 15 + half, 24 + core), outline=PALE)
    plate = tex.crop((12, 32, 28, 48))
    left, right = plate.crop((0, 0, 8, 16)), plate.crop((8, 0, 16, 16))
    im.alpha_composite(fit(left, swing), (ox + 8, 12))
    im.alpha_composite(fit(right, swing), (ox + 24 - swing, 12))
    r = random.Random(swing)
    for _ in range(steam):
        side = r.choice((-1, 1))
        d.point((ox + 16 + side * r.randint(3, 22), r.randint(8, 30)), (214, 226, 232, 200))
    return im


def main():
    frames = [('shut', 8, 0, 0), ('0.2 s: seals go, cold air', 6, 0, 26), ('0.4 s: halves swing out', 3, 1, 40),
              ('0.7 s: core pushes out', 1, 2, 18), ('open: core throbs', 1, 1, 0)]
    width = 19 * B * S + 2 * 24
    wall_h = 5 * B * S
    out = Image.new('RGB', (width, 3 * 34 + 2 * wall_h + 48 * S + 90), BG)
    d = ImageDraw.Draw(out)
    y = 12
    for label, n in (('Fight start: eleven lamps, one a fuse. A clock either side of the gate.', 0), ('Round 1 done, one fuse into round 2: four out.', 4)):
        d.text((24, y), label, INK, F(14))
        y += 24
        out.paste(wall(n).resize((19 * B * S, wall_h), Image.NEAREST), (24, y))
        y += wall_h + 14
    d.text((24, y), 'The shell opening, about a second, then shutting the same way backwards.', INK, F(14))
    y += 28
    step = (width - 48) // len(frames)
    for i, (label, swing, core, steam) in enumerate(frames):
        im = custodian(swing, core, steam)
        im = im.resize((im.width * S, im.height * S), Image.NEAREST)
        x = 24 + i * step
        out.paste(im, (x, y), im)
        d.text((x, y + im.height + 6), label, DIM, F(11))
    out.save(OUT)


if __name__ == '__main__':
    main()

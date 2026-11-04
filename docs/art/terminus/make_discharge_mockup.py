"""Mockup of the ORSA TR-0 Terminus wind-up, beam and target effect, for owner approval (2026-11-03).

The owner asked for really unique, cool animations; the discharge itself stays as the spec has it, with no beam and
no impact sound. Side view at Minecraft scale:
16 pixels a block, a player 32 pixels tall. The cone is 12 blocks long and 30 degrees wide. Left to right: a husk at
5 blocks, a stone wall at 7 with a husk behind it at 9 (the cone passes through blocks), a pig at 10 (untouched), and
a husk at 14 (out of reach). Run from anywhere: python3 docs/art/terminus/make_discharge_mockup.py
"""
import math
from pathlib import Path
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
OUT = HERE / 'discharge-mockup.png'
SCALE = 3
W, H = 16 * 16, 64
GROUND = 56
EYE = (22, GROUND - 26)
REACH = 12 * 16
HALF = math.radians(15)

C = {k: tuple(bytes.fromhex(v)) + (255,) for k, v in dict(
    sky='1f2830', ground='3b4a52', snow='c9d6dc', stone='6b7176', stone2='565b60',
    skin='c99a76', sleeve='3f5a6e', trouser='2c3440', hair='3b2a1e',
    husk='7d7350', husk2='5f5a3e', pig='e59a9a', pig2='c27b7b',
    core='ffffff', beam='d7f2ff', ring='a9e6f5', speck='e6f6ff', mote='f4fbff', mote2='9fd6e8', glass='9fb4bc',
    text='8c969c', bg='20252b').items()}

TARGETS = [('husk', 5, True), ('husk', 9, True), ('pig', 10, False), ('husk', 14, False)]
WALL_X = 7


def grey(c):
    r, g, b, a = c
    y = int(.3 * r + .59 * g + .11 * b)
    return (y, y, y, a)


def in_cone(x, y):
    dx, dy = x - EYE[0], y - EYE[1]
    return 0 < dx <= REACH and abs(math.atan2(dy, dx)) <= HALF and math.hypot(dx, dy) <= REACH


def figure(d, kind, bx, fallen=False, gone=False):
    x = EYE[0] + bx * 16
    if gone:
        return
    if kind == 'pig':
        d.rectangle((x - 7, GROUND - 9, x + 7, GROUND - 3), C['pig']); d.rectangle((x + 5, GROUND - 11, x + 10, GROUND - 5), C['pig'])
        d.rectangle((x - 6, GROUND - 3, x - 4, GROUND - 1), C['pig2']); d.rectangle((x + 3, GROUND - 3, x + 5, GROUND - 1), C['pig2'])
        return
    if fallen:
        d.rectangle((x - 14, GROUND - 4, x + 15, GROUND - 1), C['husk2']); d.rectangle((x - 18, GROUND - 7, x - 11, GROUND - 1), C['husk'])
        return
    d.rectangle((x - 2, GROUND - 12, x + 1, GROUND - 1), C['husk2'])
    d.rectangle((x - 2, GROUND - 24, x + 1, GROUND - 13), C['husk'])
    d.rectangle((x - 4, GROUND - 32, x + 3, GROUND - 25), C['husk'])
    d.rectangle((x - 9, GROUND - 22, x - 2, GROUND - 20), C['husk'])   # arms reaching toward the player


def player(d, phase):
    x = EYE[0]
    d.rectangle((x - 2, GROUND - 12, x + 1, GROUND - 1), C['trouser'])
    d.rectangle((x - 2, GROUND - 24, x + 1, GROUND - 13), C['sleeve'])
    d.rectangle((x - 4, GROUND - 32, x + 3, GROUND - 25), C['skin']); d.rectangle((x - 4, GROUND - 32, x + 3, GROUND - 31), C['hair'])
    d.rectangle((x - 4, GROUND - 32, x - 3, GROUND - 27), C['hair'])
    d.rectangle((x, GROUND - 23, x + 9, GROUND - 21), C['sleeve']); d.rectangle((x + 10, GROUND - 23, x + 11, GROUND - 21), C['skin'])
    # the wand, arm straight out: grip in the fist, glass shaft, emitter
    d.rectangle((x + 9, GROUND - 23, x + 13, GROUND - 22), (27, 29, 33, 255))
    d.rectangle((x + 14, GROUND - 24, x + 20, GROUND - 22), C['glass'])
    motes = {'idle': [16, 18, 20], 'gather': [18, 19, 20], 'charged': [20, 20, 20], 'spent': [16, 18]}[phase]
    for m in motes:
        d.point((x + min(m, 19), GROUND - 23), C['mote'])
    d.rectangle((x + 21, GROUND - 24, x + 21, GROUND - 22), C['core'] if phase == 'charged' else C['mote2'])


def scene(frame):
    im = Image.new('RGBA', (W, H), C['sky'])
    d = ImageDraw.Draw(im)
    d.rectangle((0, GROUND, W, H), C['ground']); d.line((0, GROUND, W, GROUND), C['snow'])
    wx = EYE[0] + WALL_X * 16
    for by in range(3):
        d.rectangle((wx - 8, GROUND - 16 * (by + 1), wx + 7, GROUND - 16 * by - 1), C['stone'] if by % 2 else C['stone2'])
    t = frame['t']
    for kind, bx, hit in TARGETS:
        figure(d, kind, bx, fallen=hit and frame.get('fallen'), gone=hit and frame.get('gone'))
    player(d, frame['wand'])
    ex, ey = EYE[0] + 22, GROUND - 23
    if frame.get('specks'):
        # Wind-up: pale specks are drawn in toward the emitter from inside the cone, thickest along its edge.
        k = frame['specks']
        for i in range(70 * k):
            a = (i * 0.618) % 1 * 2 - 1
            r = ((i * 0.754877) % 1) ** 0.6 * REACH * (1 - 0.25 * k)
            if abs(a) < 0.75 and i % 3:
                continue
            x, y = int(ex + r * math.cos(a * HALF)), int(ey + r * math.sin(a * HALF))
            if 0 <= x < W and 0 <= y < GROUND:
                d.point((x, y), C['speck'])
    if frame.get('beam'):
        b = frame['beam']
        # Beam: a hard white core the length of the reach, through the wall, with rings rolling out to the cone's edge.
        length = REACH if b != 'fade' else REACH
        col = C['core'] if b != 'fade' else C['ring']
        if b != 'gone':
            d.line((ex, ey, ex + length, ey), col)
            if b == 'fire':
                d.line((ex, ey - 1, ex + length, ey - 1), C['beam']); d.line((ex, ey + 1, ex + length, ey + 1), C['beam'])
        for rx in frame.get('rings', []):
            r = int(rx * math.tan(HALF))
            for yy in range(-r, r + 1, 2):
                if ey + yy < GROUND:
                    d.point((ex + rx, ey + yy), C['ring'])
            d.point((ex + rx, ey - r), C['core']); d.point((ex + rx, min(GROUND - 1, ey + r)), C['core'])
    if frame.get('grey'):
        px = im.load()
        for y in range(H):
            for x in range(W):
                if in_cone(x, y) or not frame.get('cone_only'):
                    px[x, y] = grey(px[x, y])
    if frame.get('motes'):
        lift = frame['motes']
        for kind, bx, hit in TARGETS:
            if hit:
                x = EYE[0] + bx * 16
                y = GROUND - 30 - lift
                d.rectangle((x - 1, y - 1, x, y), C['mote'])
                if lift < 10:
                    d.line((x - 1, y + 1, x - 1, GROUND - 26), C['mote2'])  # a thread still tying it to the head
    return im


FRAMES = [
    ('wind-up, 0.5 s: motes slide to the emitter, specks drawn in', dict(t=10, wand='gather', specks=1)),
    ('wind-up, 1.9 s: glass frosted, the cone traced by inward specks', dict(t=38, wand='charged', specks=2)),
    ('discharge, one frame: no beam, the view goes grey', dict(t=40, wand='spent', grey=True)),
    ('tick 2: targets stop; a pale mote lifts from each mind', dict(t=42, wand='spent', motes=4, gone=True)),
    ('tick 10: the motes hang, then wink out', dict(t=50, wand='spent', motes=12, gone=True)),
    ('tick 20: silence. One mote gone from the glass; pig and far husk untouched', dict(t=60, wand='spent', gone=True)),
]


def main():
    cw, ch = W * SCALE, H * SCALE
    sheet = Image.new('RGBA', (2 * cw + 30, 3 * (ch + 26) + 10), C['bg'])
    d = ImageDraw.Draw(sheet)
    for i, (label, frame) in enumerate(FRAMES):
        x, y = 10 + (i % 2) * (cw + 10), 10 + (i // 2) * (ch + 26)
        d.text((x, y), label, fill=C['text'])
        sheet.alpha_composite(scene(frame).resize((cw, ch), Image.NEAREST), (x, y + 14))
    sheet.save(OUT)
    print(OUT)


if __name__ == '__main__':
    main()

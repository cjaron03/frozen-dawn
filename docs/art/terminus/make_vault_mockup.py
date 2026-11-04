"""The Terminus Vault under the Core, its Unwatched Door rooms and contents, at Minecraft scale (16 pixels a block).

Owner's pick, 2026-11-03: hidden under the Confinement Core. Writes vault-rooms-mockup.png (plan, section, the door, the
backward bridge) and vault-contents-mockup.png (the cot, the one change on waking, the cradle, the clock).
Run from anywhere: python3 make_vault_mockup.py
"""
import random
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
B = 16
F = lambda s: ImageFont.truetype('/System/Library/Fonts/Menlo.ttc', s)
BG, INK, DIM, ROUTE, RED = (24, 28, 33), (226, 232, 236), (150, 160, 167), (255, 214, 92), (232, 104, 92)
hexc = lambda h: tuple(bytes.fromhex(h))
PAL = dict(rock=['4a4f54', '42474c', '52575c'], shell=['5b6064', '555a5e'], floor=['2f3438', '2a2f33'], air=['0f1317'],
           pit=['07090b'], snow=['d6e2e8', 'c9d6dc'], walk=['8d9296', '83888c'], ledge=['3c4246', '363c40'],
           steel=['6e767c', '666e74'], dark=['2a2f34', '24292e'], glass=['9fc4d8', '8fb8d0'], cot=['4e5f6e', '465664'])


def tile(name, x, y):
    r = random.Random(hash((name, x, y)) & 0xffffff)
    pal = PAL[name]
    im = Image.new('RGB', (B, B), hexc(pal[0]))
    px = im.load()
    if len(pal) > 1:
        for i in range(B):
            for j in range(B):
                px[i, j] = hexc(r.choice(pal))
    return im


def door_col(i):
    """One column of the heavy door: dark steel, riveted, with a chevron band across all three columns."""
    im = Image.new('RGB', (B, 3 * B), (58, 64, 69))
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 15, 47), (58, 64, 69)); d.rectangle((0, 0, 0, 47), (44, 48, 52)); d.rectangle((15, 0, 15, 47), (44, 48, 52))
    for y in (3, 44):
        for x in (3, 12):
            d.point((x, y), (120, 128, 134))
    for y in range(20, 28):
        for x in range(16):
            if ((x + i * 16 + y) // 4) % 2 == 0:
                d.point((x, y), (168, 150, 84))
    return im


def door_view(slid, label=None):
    """From the landing, the door in its frame. slid: pixels moved into the pocket on the right."""
    w, h = 9, 5
    im = Image.new('RGB', (w * B, h * B))
    for i in range(w):
        for j in range(h):
            im.paste(tile('shell', i, j), (i * B, j * B))
    d = ImageDraw.Draw(im)
    x0, y0 = 3 * B, B + B // 2
    x0 = 3 * B
    d.rectangle((x0, B, x0 + 3 * B - 1, 4 * B - 1), (6, 8, 10))
    d.rectangle((x0 - 2, B - 2, x0 + 3 * B + 1, B - 1), (120, 126, 130))
    d.rectangle((x0 - 2, B, x0 - 1, 4 * B - 1), (120, 126, 130))
    d.rectangle((x0 + 3 * B, B, x0 + 3 * B + 1, 4 * B - 1), (120, 126, 130))
    panel = Image.new('RGB', (3 * B, 3 * B))
    for i in range(3):
        panel.paste(door_col(i), (i * B, 0))
    visible = 3 * B - slid
    if visible > 0:
        im.paste(panel.crop((0, 0, visible, 3 * B)), (x0 + slid, B))
    d.rectangle((0, 4 * B, w * B, 5 * B), hexc('2f3438'))
    return im


def person(pose='side', look=1, h=32):
    """A plain player figure. side: profile, look +1 faces right, -1 left."""
    im = Image.new('RGBA', (16, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    skin, shirt, leg = (196, 160, 130, 255), (68, 110, 128, 255), (58, 62, 94, 255)
    if pose == 'side':
        d.rectangle((6, 20, 9, 31), leg); d.rectangle((6, 8, 9, 19), shirt); d.rectangle((4, 0, 11, 7), skin)
        d.rectangle((4, 0, 11, 1), (70, 52, 36, 255))
        ex = 10 if look > 0 else 5
        d.point((ex, 3), (20, 20, 20, 255))
    else:
        d.rectangle((4, 20, 7, 31), leg); d.rectangle((8, 20, 11, 31), leg); d.rectangle((4, 8, 11, 19), shirt)
        d.rectangle((0, 8, 3, 19), skin); d.rectangle((12, 8, 15, 19), skin); d.rectangle((4, 0, 11, 7), skin)
        d.rectangle((4, 0, 11, 1), (70, 52, 36, 255))
        d.point((6, 4), (20, 20, 20, 255)); d.point((9, 4), (20, 20, 20, 255))
    return im


# ---- Page 1 geometry, hall-relative (x east, z south, y up). Floor blocks at y=-15, four blocks of air above.
X0, X1, Z0, Z1 = -4, 29, -6, 6


def plan():
    g = {}
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            g[x, z] = 'rock'

    def box(a, b, c, e, m):
        for x in range(a, b + 1):
            for z in range(c, e + 1):
                g[x, z] = m
    box(-3, 28, -5, 5, 'shell')
    box(-2, 2, -2, 2, 'floor')                                   # the landing
    box(4, 16, -2, 2, 'ledge'); box(6, 14, -2, 2, 'pit')         # the bridge hall
    for x in range(6, 15):
        g[x, 0] = 'walk'
    box(17, 21, -3, 3, 'floor')                                  # the cot room
    box(23, 27, -3, 3, 'floor')                                  # the vault
    im = Image.new('RGB', ((X1 - X0 + 1) * B, (Z1 - Z0 + 1) * B))
    for (x, z), m in g.items():
        im.paste(tile(m, x, z), ((x - X0) * B, (z - Z0) * B))
    d = ImageDraw.Draw(im)
    P = lambda x, z: ((x - X0) * B, (z - Z0) * B)
    # Doors, pocket, bore, cot, cradle, body.
    for z in (-1, 0, 1):
        x, y = P(3, z); d.rectangle((x, y, x + 15, y + 15), (168, 150, 84))
        x, y = P(22, z); d.rectangle((x, y, x + 15, y + 15), (168, 150, 84))
    x, y = P(3, 2); d.rectangle((x + 4, y, x + 11, y + 47), (20, 24, 28))
    x, y = P(0, 0); d.rectangle((x + 2, y + 2, x + 13, y + 13), ROUTE)
    x, y = P(19, 3); d.rectangle((x, y, x + 31, y + 15), hexc('4e5f6e')); d.rectangle((x, y, x + 7, y + 15), (200, 204, 200))
    x, y = P(27, 0); d.rectangle((x, y, x + 15, y + 15), (159, 214, 232))
    x, y = P(24, -3); d.rectangle((x + 2, y + 2, x + 13, y + 13), (68, 110, 128))
    x, y = P(25, 3); d.rectangle((x, y, x + 15, y + 15), hexc('7a5a34'))
    x, y = P(23, 3); d.rectangle((x, y, x + 15, y + 15), hexc('6e767c'))
    x, y = P(17, 0); d.rectangle((x, y, x + 1, y + 15), (120, 126, 130))
    return im, P


Y0, Y1 = -27, -6


def section():
    g = {}
    for x in range(X0, X1 + 1):
        for y in range(Y0, Y1 + 1):
            g[x, y] = 'rock'

    def box(a, b, c, e, m):
        for x in range(a, b + 1):
            for y in range(c, e + 1):
                g[x, y] = m
    box(-3, 28, -16, -10, 'shell'); box(4, 16, -26, -10, 'shell')
    box(-2, 2, -14, -11, 'air'); box(4, 16, -14, -11, 'air'); box(17, 21, -14, -11, 'air'); box(23, 27, -14, -11, 'air')
    box(6, 14, -25, -15, 'pit'); box(6, 14, -25, -25, 'snow')
    for x in range(6, 15):
        g[x, -15] = 'walk'
    for x in (-2, -1, 0, 1, 2, 4, 5, 15, 16, 17, 18, 19, 20, 21, 23, 24, 25, 26, 27):
        g[x, -15] = 'floor'
    im = Image.new('RGB', ((X1 - X0 + 1) * B, (Y1 - Y0 + 1) * B))
    for (x, y), m in g.items():
        im.paste(tile(m, x, y), ((x - X0) * B, (Y1 - y) * B))
    d = ImageDraw.Draw(im)
    P = lambda x, y: ((x - X0) * B, (Y1 - y) * B)
    # The bore's ladder, landing to the top of the frame.
    for y in range(-14, Y1 + 1):
        x, py = P(0, y)
        d.rectangle((x, py, x + 15, py + 15), (15, 19, 23))
        for k in (2, 7, 12):
            d.rectangle((x + 3, py + k, x + 12, py + k + 1), hexc('8a6a3c'))
    for y in (-14, -13, -12):
        x, py = P(3, y); d.rectangle((x, py, x + 15, py + 15), (168, 150, 84))
        x, py = P(22, y); d.rectangle((x, py, x + 15, py + 15), (168, 150, 84))
    x, py = P(22, -12); d.rectangle((x + 6, py + 4, x + 9, py + 11), (159, 196, 216))
    # Ladder up out of the pit.
    for y in range(-24, -14):
        x, py = P(5, y)
        for k in (2, 7, 12):
            d.rectangle((x + 12, py + k, x + 15, py + k + 1), hexc('8a6a3c'))
    return im, P


def bridge(tip, px, look, mark=None):
    """Side elevation of the bridge hall. tip: cells of walkway out. px: player's column. look: +1 faces far side."""
    xs, xe, ys, ye = 3, 17, -18, -10
    im = Image.new('RGB', ((xe - xs + 1) * B, (ye - ys + 1) * B))
    for x in range(xs, xe + 1):
        for y in range(ys, ye + 1):
            pit = 6 <= x <= 14 and y <= -15
            hall = y >= -14 and y <= -11
            m = 'pit' if pit else 'air' if hall else 'ledge' if y == -15 else 'shell'
            im.paste(tile(m, x, y), ((x - xs) * B, (ye - y) * B))
    for i in range(tip):
        im.paste(tile('walk', 6 + i, 0), ((6 + i - xs) * B, (ye + 15) * B))
    sp = person('side', look)
    im.paste(sp, (int((px - xs) * B), (ye + 14) * B - 16), sp)
    d = ImageDraw.Draw(im)
    # The look, as a short ray from the eyes.
    ex, ey = int((px - xs) * B) + 8, (ye + 14) * B - 13
    for i in range(1, 7):
        d.rectangle((ex + look * i * 5, ey, ex + look * i * 5 + 1, ey + 1), ROUTE if look < 0 else RED)
    if mark:
        tx = (6 + tip - xs) * B
        d.rectangle((tx - 1, (ye + 15) * B - 1, tx + 1, (ye + 16) * B), RED)
    return im


def f5_view():
    """Third-person front view: the camera faces the player's face and sees where their back is going."""
    w, h = 12 * B, 8 * B
    im = Image.new('RGB', (w, h), hexc('07090b'))
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, w, 2 * B), hexc('0f1317'))
    d.rectangle((4 * B, B, 8 * B - 1, 2 * B + 4), hexc('3c4246'))         # the far ledge
    d.rectangle((5 * B, B - 8, 7 * B - 1, B + 8), hexc('1a1f24'))         # the far arch
    # The walkway running away from the camera, stepped to stay blocky; its tip stops short of the ledge.
    for i, (y, half) in enumerate([(h - 1 - k * 8, 26 - k * 3) for k in range(8)]):
        if y < 3 * B + 2:
            break
        d.rectangle((w // 2 - half, y - 7, w // 2 + half, y), (141, 146, 150) if i % 2 else (131, 136, 140))
    sp = person('front').resize((48, 96), Image.NEAREST)
    im.paste(sp, (w // 2 - 24, h - 96), sp)
    return im


def caption(d, x, y, lines, col=INK, size=13):
    for l in lines:
        d.text((x, y), l, col, font=F(size))
        y += size + 6
    return y


# ---- Page 1
pl, PP = plan()
se, SP = section()
W = 1440
doors = [(0, 'closed'), (19, 'turned too soon: frozen where it is'), (48, 'open, about 4 s of your back')]
frames = [(1, 4.5, -1, None, '1 back to the far side: a cell slides out behind you'),
          (4, 7.5, -1, None, '2 step back on: a new cell comes only while you stand on the tip'),
          (5, 9.5, 1, True, '3 turn to look at the gap: it stops dead, and nothing more comes'),
          (9, 13.5, -1, None, '4 turn round again and back across; one wide, no rail')]
H = 90 + pl.height + 40 + se.height + 40 + 5 * B * 2 + 90 + (9 * B * 2 + 40) * 2 + 20
im = Image.new('RGB', (W, H), BG)
d = ImageDraw.Draw(im)
d.text((32, 16), 'Terminus Vault: the rooms under the Core', INK, font=F(24))
d.text((32, 48), '16 px a block. Hall-relative: the bore comes down at the Core, the floor sits 15 blocks under the hall.', DIM, font=F(14))
y = 86
d.text((32, y - 16), 'plan', DIM, font=F(12))
im.paste(pl, (32, y))
labels = [(0, -2, 'landing'), (8, -2, 'bridge hall'), (18, -3, 'cot room'), (24, -1, 'vault')]
for x, z, t in labels:
    a, b = PP(x, z)
    d.text((32 + a + 2, y + b + 2), t, INK, font=F(11))
ax = 32 + pl.width + 24
caption(d, ax, y, ['Gold square: the bore under the Core.', 'Yellow bars: door 1 and the last door.',
                   'Blue-grey: the cot. Pale blue: the cradle.', 'Blue square: the technician.',
                   'Brown: the tale logs. Steel: the manifest desk.', '',
                   'Rule, per the spec: a door moves only while', 'no player or susceptible mob faces it with',
                   'a clear line. Head facing, 35 degree cone,', 'one ray. Architects or Returned who follow',
                   'you in freeze it too.'])
y += pl.height + 34
d.text((32, y - 16), 'section through the middle, looking north', DIM, font=F(12))
im.paste(se, (32, y))
caption(d, ax, y, ['Fall off the bridge: powder snow ten blocks', 'down, no damage, a ladder back up, the', 'walkway stays as far out as it got.', '',
                   'Shell: cannot be mined or blasted, so the', 'only way in is the bore and the only way', 'on is the doors.', '',
                   'Needs a site deep enough for the pit: at', 'least 28 blocks above the world floor. The', 'first site that fits gets it, not the very', 'first if that one is too shallow.'])
y += se.height + 34
d.text((32, y - 16), 'room 1, turn your back: door 1 from the landing, slides right into a pocket', DIM, font=F(12))
x = 32
for slid, t in doors:
    v = door_view(slid).resize((9 * B * 2, 5 * B * 2), Image.NEAREST)
    im.paste(v, (x, y))
    d.text((x, y + v.height + 6), t, INK, font=F(12))
    x += v.width + 20
caption(d, x + 6, y, ['The grind is the', 'only cue. You hear', 'it; you never see it.'], size=12)
y += 5 * B * 2 + 54
d.text((32, y - 16), 'room 2, the backward bridge: side view. Gold dots: the look, facing back. Red: facing the gap, frozen.', DIM, font=F(12))
for i, (tip, px, look, mark, t) in enumerate(frames):
    v = bridge(tip, px, look, mark).resize((15 * B * 2, 9 * B * 2), Image.NEAREST)
    fx = 32 + (i % 2) * (v.width + 24)
    fy = y + (i // 2) * (v.height + 40)
    im.paste(v, (fx, fy))
    d.text((fx, fy + v.height + 6), t, INK, font=F(11))
f5 = f5_view().resize((12 * B * 2 // 1, 8 * B * 2 // 1), Image.NEAREST).crop((0, 0, 384, 256))
fx = 32 + 2 * (15 * B * 2 + 24)
if fx + 300 > W:
    f5 = None
y2 = y
im.save(HERE / 'tmp.png')
W2 = max(W, 32 + 2 * (480 + 24) + 400)
page = Image.new('RGB', (W2, H), BG)
page.paste(im, (0, 0))
d = ImageDraw.Draw(page)
v = f5_view().resize((384, 256), Image.NEAREST)
fx = 32 + 2 * (480 + 24)
page.paste(v, (fx, y2))
caption(d, fx, y2 + 266, ['F5 front view: the camera watches the gap,', 'the body faces away. The door judges the',
                          'body, so the bridge keeps coming. Nothing', 'tells you this; crossing blind also works.'])
page.save(HERE / 'vault-rooms-mockup.png')
(HERE / 'tmp.png').unlink()
print('rooms', page.size)


# ---- Page 2
def wand(sc=1):
    """The Reliquary lying in the cradle, one block long."""
    im = Image.new('RGBA', (16, 5), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle((0, 1, 4, 3), (24, 24, 28, 255)); d.rectangle((5, 0, 5, 4), (150, 158, 164, 255))
    d.rectangle((6, 1, 13, 3), (150, 196, 216, 255)); d.rectangle((6, 0, 13, 0), (120, 128, 134, 255)); d.rectangle((6, 4, 13, 4), (120, 128, 134, 255))
    for x in (7, 9, 12):
        d.point((x, 2), (244, 251, 255, 255))
    d.rectangle((14, 0, 14, 4), (150, 158, 164, 255)); d.rectangle((15, 1, 15, 3), (214, 226, 232, 255))
    return im


def tech(turn):
    """The technician, seated against the north wall facing the viewer, 24 px tall. turn: -1 head to the door, +1 cradle."""
    im = Image.new('RGBA', (24, 24), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    suit, suit2, skin, frost = (84, 96, 106, 255), (70, 82, 92, 255), (150, 158, 160, 255), (214, 226, 232, 255)
    d.rectangle((8, 8, 15, 18), suit)                         # torso
    d.rectangle((4, 9, 7, 17), suit2); d.rectangle((16, 9, 19, 17), suit2)   # arms hanging
    d.rectangle((6, 19, 17, 23), suit2)                       # legs out toward the viewer
    d.rectangle((16, 15, 18, 18), (40, 34, 28, 255))          # the empty holster
    d.rectangle((16, 15, 18, 15), (28, 24, 20, 255))
    d.rectangle((8, 0, 15, 7), skin)                          # head
    d.rectangle((8, 0, 15, 1), (60, 62, 64, 255))
    ex = 9 if turn < 0 else 12
    d.point((ex, 4), (24, 24, 24, 255)); d.point((ex + 2, 4), (24, 24, 24, 255))
    d.rectangle((ex, 6, ex + 2, 6), (84, 88, 90, 255))
    if turn < 0:
        d.rectangle((14, 2, 15, 7), (118, 124, 126, 255))
    else:
        d.rectangle((8, 2, 9, 7), (118, 124, 126, 255))
    for p in [(9, 8), (15, 10), (5, 12), (18, 13), (10, 20), (14, 22), (8, 1), (13, 0)]:
        d.point(p, frost)
    return im


def seg(d, ch, x, y, col, off):
    S = {'0': 'abcdef', '1': 'bc', '2': 'abged', '3': 'abgcd', '4': 'fgbc', '5': 'afgcd', '6': 'afgedc', '7': 'abc', '8': 'abcdefg', '9': 'abcfgd'}
    on = S[ch]
    parts = dict(a=(0, 0, 2, 0), b=(2, 0, 2, 2), c=(2, 2, 2, 4), d=(0, 4, 2, 4), e=(0, 2, 0, 4), f=(0, 0, 0, 2), g=(0, 2, 2, 2))
    for k, (a, b, c, e) in parts.items():
        d.rectangle((x + a, y + b, x + c, y + e), col if k in on else off) if k in on else None


def clock(text):
    im = Image.new('RGB', (3 * B, B), (34, 38, 42))
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 47, 15), (44, 48, 52)); d.rectangle((2, 3, 45, 12), (10, 14, 16))
    x = 6
    for ch in text:
        if ch == ':':
            d.point((x, 6), (159, 214, 232)); d.point((x, 9), (159, 214, 232)); x += 2
        else:
            seg(d, ch, x, 5, (214, 240, 250), (24, 30, 34)); x += 4
    return im


def cutaway(after):
    xs, xe, ys, ye = 16, 29, -16, -9
    im = Image.new('RGB', ((xe - xs + 1) * B, (ye - ys + 1) * B))
    for x in range(xs, xe + 1):
        for y in range(ys, ye + 1):
            room = (17 <= x <= 21 or 23 <= x <= 27) and -14 <= y <= -11
            m = 'air' if room else 'floor' if y == -15 and x < 28 else 'shell'
            im.paste(tile(m, x, y), ((x - xs) * B, (ye - y) * B))
    d = ImageDraw.Draw(im)
    P = lambda x, y: ((x - xs) * B, (ye - y) * B)
    # Dim emergency light in the vault so the viewport has something to show.
    x, y = P(23, -11)
    d.rectangle((x, y, x + 5 * B - 1, y + 4 * B - 1), (20, 27, 33))
    # The cot, along the south wall, projected in front.
    x, y = P(19, -14)
    d.rectangle((x, y + 8, x + 31, y + 11), hexc('4e5f6e')); d.rectangle((x, y + 8, x + 7, y + 11), (200, 204, 200))
    d.rectangle((x + 2, y + 12, x + 3, y + 15), (106, 112, 118)); d.rectangle((x + 28, y + 12, x + 29, y + 15), (106, 112, 118))
    # The last door and its viewport.
    if not after:
        for yy in (-14, -13, -12):
            x, y = P(22, yy); d.rectangle((x, y, x + 15, y + 15), (58, 64, 69)); d.rectangle((x, y, x, y + 15), (44, 48, 52))
        x, y = P(22, -13); d.rectangle((x + 5, y + 4, x + 10, y + 11), (159, 196, 216))
    else:
        x, y = P(22, -12); d.rectangle((x, y, x + 15, y + 47), (20, 27, 33))
    # The clock over it, on the room side.
    c = clock('03:17:42' if not after else '03:17:37')
    x, y = P(21, -11)
    im.paste(c.crop((0, 0, 48, 16)), (x - 16, y))
    # The technician, the desk, the logs and the cradle.
    t = tech(+1 if after else -1)
    x, y = P(24, -14)
    im.paste(t, (x - 4, y + 16 - 24), t)
    x, y = P(25, -14); d.rectangle((x + 8, y + 4, x + 15, y + 15), (110, 118, 124)); d.rectangle((x + 8, y + 3, x + 15, y + 3), (222, 214, 190))
    x, y = P(26, -14); d.rectangle((x + 1, y + 1, x + 7, y + 15), hexc('7a5a34'))
    x, y = P(27, -14)
    d.rectangle((x + 2, y + 8, x + 13, y + 15), (84, 92, 98)); d.rectangle((x, y + 4, x + 1, y + 8), (120, 128, 134)); d.rectangle((x + 14, y + 4, x + 15, y + 8), (120, 128, 134))
    w = wand()
    im.paste(w, (x, y + 2), w)
    return im


K = 3
cw = 14 * B * K
page = Image.new('RGB', (32 + cw * 2 + 28 + 32, 1040), BG)
d = ImageDraw.Draw(page)
d.text((32, 16), 'Terminus Vault: room 3 and what is in it', INK, font=F(24))
d.text((32, 48), 'Cutaway looking north, 16 px a block, drawn x3. The cot stands in front on the south wall.', DIM, font=F(14))
y = 88
for i, (after, t) in enumerate(((False, 'before: door frozen whatever you do. Wand and technician show through the viewport.'),
                                (True, 'on waking, about 5 s of black later: door open. One change: the head.'))):
    v = cutaway(after).resize((cw, 8 * B * K), Image.NEAREST)
    page.paste(v, (32 + i * (cw + 28), y))
    d.text((32 + i * (cw + 28), y + v.height + 6), t, INK, font=F(12))
y += 8 * B * K + 44
d.text((32, y), 'the head, x8', DIM, font=F(12))
y += 18
for i, turn in enumerate((-1, 1)):
    v = tech(turn).resize((192, 192), Image.NEAREST)
    page.paste(v, (32 + i * 212, y), v)
caption(d, 32, y + 200, ['Left, turned to the door. Right, turned to the cradle.', 'Empty holster on the right hip.', 'Frost on the suit. No blood, no wound.'], size=12)
x = 32 + 2 * 212 + 20
d.text((x, y - 18), 'the wand in its cradle, x8', DIM, font=F(12))
cr = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
cd = ImageDraw.Draw(cr)
cd.rectangle((2, 8, 13, 15), (84, 92, 98, 255)); cd.rectangle((0, 4, 1, 8), (120, 128, 134, 255)); cd.rectangle((14, 4, 15, 8), (120, 128, 134, 255))
w = wand(); cr.paste(w, (0, 2), w)
page.paste(cr.resize((128, 128), Image.NEAREST), (x, y), cr.resize((128, 128), Image.NEAREST))
caption(d, x, y + 136, ['A steel block with two', 'forks. Take the wand and', 'the forks stay empty.', 'Found: 3 discharges,', 'Transfers logged: 1.'], size=12)
x += 200
d.text((x, y - 18), 'the backward clock, x6, one second apart', DIM, font=F(12))
for i, t in enumerate(('03:17:42', '03:17:41', '03:17:40')):
    v = clock(t).resize((288, 96), Image.NEAREST)
    page.paste(v, (x, y + i * 104))
caption(d, x + 300, y, ['Over the last door.', 'Runs back one a second,', 'always, from a number', 'set by the seed.', 'Never explained.'], size=12)
y += 330
d.line((32, y, page.width - 32, y), (60, 66, 72))
y += 14
d.text((32, y), 'the documents', DIM, font=F(12))
caption(d, 32, y + 20, [
    'Hall desk barrel, this site only: Drain Schedule. "Floor drains drawn: 5. Floor drains in hall: 4." The one clue.',
    'Vault desk: Vault Manifest, opening with the Keats lines (checked word for word against Project Gutenberg #23684, Poems Published in 1820).',
    '  Issuing division blank. Requisition reason: Containment of RELIQUUS COGNITIVE ECHO. Approver signature absent.',
    'Vault desk: Procurement Review memo on the name "deathwand", same joke as the hellwhip memo; name review denied.',
    'Logs locker: tale logs, each a different technician saying why they were posted here. My draft: four voices plus the technician\'s own, last.',
], size=13)
page.save(HERE / 'vault-contents-mockup.png')
print('contents', page.size)

"""Three ways to hide the Terminus Vault (owner, 2026-11-03: "very...very hidden").

Each column is a true cross-section of a Helion site as HelionSiteBuilder lays it out, 16 pixels a block, on one shared
height scale, with the secret route outlined. Below each is what the player actually sees at the entry, same scale x2.
Run from anywhere: python3 make_hiding_mockup.py
"""
import random
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
OUT = HERE / 'hiding-options.png'
B = 16
TOP, BOTTOM = 29, -17            # hall-relative block heights drawn
GROUND = 25
MONO = '/System/Library/Fonts/Menlo.ttc'
F = lambda s: ImageFont.truetype(MONO, s)
BG, INK, DIM, ROUTE = (24, 28, 33), (226, 232, 236), (150, 160, 167), (255, 214, 92)

PAL = dict(sky=['1f2830'], snow=['d6e2e8', 'c9d6dc'], rock=['4a4f54', '42474c', '52575c'], dirt=['3f454a', '383e43'],
           conc=['a9adaf', 'a2a6a8'], conc2=['767b7e', '6e7376'], ice=['8db4dc', '83aad4', '97bee4'], air=['12171b'],
           blue=['6f9fe8', '6596e2'], copper=['8a7a56', '7f7050'], ox=['5f9c86', '579280'], calcite=['d8d8cf', 'cfcfc6'],
           tile=['34383d', '2c3035'], core=['f4fbff', '9fd6e8'], andesite=['8d9091', '86898a'], barrel=['7a5a34', '6c4f2c'],
           lamp=['5a3f26'], ladder=['8a6a3c'], ladderbg=['12171b'], grate=['3a3f44', '1b2024'], shell=['5b6064', '555a5e'],
           vault=['0e1215'], panel=['a9adaf', 'a2a6a8'], duct=['181d21'], hatch=['9aa0a4'], mast=['6e7376'])


def tile(name, x, y):
    r = random.Random(hash((name, x, y)) & 0xffffff)
    pal = PAL[name]
    im = Image.new('RGB', (B, B), tuple(bytes.fromhex(pal[0])))
    px = im.load()
    if len(pal) > 1:
        for i in range(B):
            for j in range(B):
                px[i, j] = tuple(bytes.fromhex(r.choice(pal)))
    d = ImageDraw.Draw(im)
    if name in ('ladder',):
        im.paste(tuple(bytes.fromhex(PAL['air'][0])), (0, 0, B, B))
        d.rectangle((3, 0, 4, 15), tuple(bytes.fromhex(pal[0]))); d.rectangle((11, 0, 12, 15), tuple(bytes.fromhex(pal[0])))
        for k in (2, 7, 12):
            d.rectangle((3, k, 12, k + 1), tuple(bytes.fromhex(pal[0])))
    if name == 'grate':
        im.paste((16, 20, 24), (0, 0, B, B))
        for k in range(1, 16, 4):
            d.rectangle((k, 0, k + 1, 15), (84, 90, 96))
        d.rectangle((0, 0, 15, 1), (84, 90, 96)); d.rectangle((0, 14, 15, 15), (84, 90, 96))
    if name == 'barrel':
        d.rectangle((0, 3, 15, 4), (58, 60, 62)); d.rectangle((0, 11, 15, 12), (58, 60, 62))
    return im


class Section:
    def __init__(self, u0, u1, seed):
        self.u0, self.u1, self.g, self.seed = u0, u1, {}, seed
        r = random.Random(seed)
        for u in range(u0, u1 + 1):
            for y in range(BOTTOM, TOP + 1):
                self.g[u, y] = 'sky' if y > GROUND else 'snow' if y == GROUND else 'dirt' if y > GROUND - 4 else 'rock'
        self.route = set()

    def box(self, a0, a1, y0, y1, m):
        for u in range(a0, a1 + 1):
            for y in range(y0, y1 + 1):
                self.g[u, y] = m

    def hall(self, a0=-9, a1=9):
        r = random.Random(self.seed)
        self.box(a0, a1, -2, 8, 'ice')
        for u in range(a0 + 1, a1):
            for y in range(-1, 8):
                self.g[u, y] = 'conc' if r.random() < .7 else 'conc2'
        self.box(a0 + 2, a1 - 2, 0, 6, 'air')
        for u in range(a0 + 1, a1):
            if u % 4 == 0 and abs(u) < 8:
                self.g[u, 7] = 'lamp'

    def vault(self, a0, a1):
        self.box(a0, a1, -15, -8, 'shell')
        self.box(a0 + 1, a1 - 1, -14, -9, 'vault')

    def path(self, cells, m=None):
        for c in cells:
            if m:
                self.g[c] = m
            self.route.add(c)

    def render(self):
        w = (self.u1 - self.u0 + 1) * B
        h = (TOP - BOTTOM + 1) * B
        im = Image.new('RGB', (w, h))
        for (u, y), m in self.g.items():
            im.paste(tile(m, u, y), ((u - self.u0) * B, (TOP - y) * B))
        d = ImageDraw.Draw(im)
        for (u, y) in self.route:
            x0, y0 = (u - self.u0) * B, (TOP - y) * B
            for nu, ny, seg in (((u - 1), y, (x0, y0, x0 + 1, y0 + 15)), ((u + 1), y, (x0 + 14, y0, x0 + 15, y0 + 15)),
                                (u, y + 1, (x0, y0, x0 + 15, y0 + 1)), (u, y - 1, (x0, y0 + 14, x0 + 15, y0 + 15))):
                if (nu, ny) not in self.route:
                    d.rectangle(seg, ROUTE)
        return im

    def xy(self, u, y):
        return (u - self.u0) * B, (TOP - y) * B


def shaft(s, x):
    for y in range(0, GROUND - 1):
        s.g[x, y] = 'ladder'
        if y > 7:
            s.g[x - 1, y] = 'conc'; s.g[x + 1, y] = 'conc'
    s.g[x, GROUND - 1] = 'ice'; s.g[x, GROUND - 2] = 'ice'
    s.g[x, GROUND] = 'hatch'
    for y in range(GROUND + 1, GROUND + 4):
        s.g[x + 2, y] = 'mast'


def opt_a():
    s = Section(-10, 17, 11)                     # north-south cut through the bench, x=6
    s.hall()
    s.g[7, 0] = 'andesite'
    s.g[6, 0] = 'barrel'
    s.path([(8, 1), (8, 2)], 'panel')
    s.path([(u, y) for u in range(9, 13) for y in (1, 2)], 'duct')
    s.path([(13, y) for y in range(-9, 3)], 'ladder')
    s.vault(-4, 16)
    return s


def opt_b():
    s = Section(-10, 10, 12)                     # east-west cut through the Core, z=0
    s.hall()
    for x in range(-4, 5):
        for y in range(0, 5):
            d = abs(x)
            m = 'copper' if y in (0, 4) or d <= 1 else 'tile' if d == 2 else 'blue' if d == 3 else ('ox' if (x % 2 == 0) else 'calcite')
            s.g[x, y] = m
    s.g[0, 2] = 'core'
    for c in [(1, 2), (2, 2), (3, 2), (4, 2), (2, 3), (3, 3), (4, 1)]:
        s.g[c] = 'air'
    s.path([(0, 2)])
    s.path([(0, 1)], 'grate')
    s.path([(0, y) for y in range(-9, 1)], 'ladder')
    s.vault(-9, 9)
    return s


def opt_c():
    s = Section(-15, 10, 13)                     # east-west cut through the ladder shaft, z=7
    s.hall()
    shaft(s, -6)
    s.g[5, 0] = 'barrel'
    for x in (4, 6, 7):
        s.g[x, 0] = 'andesite'
    s.path([(-6, 17)])
    s.path([(-7, 17)], 'panel')
    s.path([(x, 17) for x in range(-12, -7)], 'duct')
    s.path([(-13, y) for y in range(-9, 18)], 'ladder')
    s.vault(-14, 6)
    return s


def wall(w, h, name, seed):
    im = Image.new('RGB', (w * B, h * B))
    r = random.Random(seed)
    for i in range(w):
        for j in range(h):
            im.paste(tile(name if name != 'conc' else ('conc' if r.random() < .7 else 'conc2'), i + seed, j), (i * B, j * B))
    return im


def view_a():
    im = wall(12, 7, 'conc', 3)
    for i in range(0, 12):
        im.paste(tile('andesite', i, 99), (i * B, 6 * B))
    im.paste(tile('barrel', 1, 1), (3 * B, 5 * B))
    d = ImageDraw.Draw(im)
    # A mining crack on the panel: the only change is the sound of it.
    for p in [(8, 3), (9, 4), (10, 5), (9, 6), (11, 8), (12, 7), (7, 9), (6, 11), (13, 11)]:
        d.point((6 * B + p[0], 4 * B + p[1]), (40, 44, 48))
    return im


def view_b():
    im = Image.new('RGB', (12 * B, 7 * B), (10, 13, 16))
    for i in range(12):
        for j in range(7):
            if not (4 <= i <= 7 and 2 <= j <= 4):
                im.paste(tile('copper', i, j), (i * B, j * B))
    d = ImageDraw.Draw(im)
    d.rectangle((4 * B, 2 * B, 8 * B - 1, 5 * B - 1), (18, 22, 26))
    g = tile('grate', 0, 0).resize((48, 32), Image.NEAREST)
    im.paste(g, (5 * B - 8, 3 * B - 8))
    for p in [(78, 42), (86, 50), (98, 44), (104, 58)]:
        d.rectangle((p[0], p[1], p[0] + 1, p[1] + 1), (230, 244, 250))
    return im


def view_c():
    im = wall(12, 7, 'conc', 8)
    d = ImageDraw.Draw(im)
    for j in range(7):
        im.paste(tile('ladder', 0, j), (1 * B, j * B))
    d.rectangle((0, 0, B - 1, 7 * B), (20, 24, 28))
    # The vent: a concrete panel whose only tell is a one-pixel seam along its top.
    d.rectangle((7 * B, 3 * B, 8 * B - 1, 3 * B), (56, 60, 64))
    return im


COLS = [
    dict(sec=opt_a(), view=view_a(), title='A  Hollow wall panel', cut='north-south cut through the bench',
         notes=['Behind the diagnostics bench one wall block is a',
                'panel. It looks like the concrete round it; only',
                'the sound of hitting it differs: hollow, not stone.',
                'Crawl through the ice, ladder down.',
                'Hidden by: nobody knocks on walls. Weakest:',
                'anyone mining that wall walks in.']),
    dict(sec=opt_b(), view=view_b(), title='B  Under the Core (my pick)', cut='east-west cut through the Core',
         notes=['The prize is the lid. A drain grate sits under the',
                'Confinement Core, so nothing shows until the Core',
                'is out, and then only from inside the crack.',
                'Once the Core is gone the Uplink stops marking the',
                'site too: every player leaves thinking it is done.',
                'Hidden by: you already finished this place.']),
    dict(sec=opt_c(), view=view_c(), title='C  Vent in the ladder shaft', cut='east-west cut through the ladder',
         notes=['Seventeen rungs up the shaft everyone climbs, a',
                'side panel opens on a duct: crouch through, then a',
                'long ladder down past the hall. Only tell is a',
                'one-pixel seam, off to the side of the climb.',
                'Hidden by: nobody looks sideways on a ladder.',
                'Weakest: pure chance if nothing points to it.']),
]

M, GAP, HEAD = 24, 28, 74
secs = [c['sec'].render() for c in COLS]
colw = [max(s.width, 12 * B * 2) for s in secs]
W = M * 2 + sum(colw) + GAP * 2
SH = secs[0].height
VH = 7 * B * 2
H = HEAD + 44 + SH + 40 + VH + 134 + 96
im = Image.new('RGB', (W, H), BG)
d = ImageDraw.Draw(im)
d.text((M, 16), 'Terminus Vault: three ways to hide it', INK, font=F(24))
d.text((M, 48), 'True sections of a Helion site, 16 px a block, one height scale. Gold outline is the secret route.', DIM, font=F(14))
x = M
for c, s, w in zip(COLS, secs, colw):
    y = HEAD
    d.text((x, y), c['title'], INK, font=F(18))
    d.text((x, y + 24), c['cut'], DIM, font=F(12))
    y += 44
    im.paste(s, (x, y))
    sec = c['sec']
    gx, gy = sec.xy(sec.u0, GROUND)
    d.text((x + 6, y + gy - 18), 'surface', INK, font=F(12))
    hx, hy = sec.xy(sec.u0 + 3, 5)
    d.text((x + hx + 4, y + hy), 'hall', INK, font=F(12))
    vx, vy = sec.xy(sec.u0 + 2 if sec.u0 > -12 else -12, -11)
    d.text((x + (w if False else 0) + (sec.xy(-3 if sec.u0 > -12 else -10, 0)[0]), y + vy), 'vault rooms', DIM, font=F(12))
    y += SH + 12
    d.text((x, y), 'what you see', DIM, font=F(12))
    y += 18
    v = c['view'].resize((12 * B * 2, VH), Image.NEAREST)
    im.paste(v, (x, y))
    y += VH + 10
    for line in c['notes']:
        d.text((x, y), line, INK, font=F(13))
        y += 19
    x += w + GAP
y = H - 90
d.line((M, y, W - M, y), (60, 66, 72))
d.text((M, y + 10), 'Any pick also gets one clue, the only one in the game: a sheet in that site\'s desk barrel, e.g. B: "Floor drains drawn: 5. Floor drains in hall: 4."', INK, font=F(13))
d.text((M, y + 30), 'A Plumb Line scan set to All should also show a void below the hall (untested), so suspicion plus the right tool can find it without the sheet.', INK, font=F(13))
d.text((M, y + 50), 'The vault rooms get a shell that cannot be mined or blasted, so nobody tunnels around the Unwatched Door. Hall walls stay as they are.', INK, font=F(13))
im.save(OUT)
print(im.size, OUT)

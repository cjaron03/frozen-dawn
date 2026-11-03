"""Mockup of three candidate Sever Lash beams, seen from above during a fast sweep, for owner approval.

Draft 2026-11-03; nothing here is in the game yet. Drawn at Minecraft scale: 16 pixels to the block, the beam's full
12-block reach is 192 pixels. Each panel shows one tick of a sweep from left to right, with the previous ticks as the
effect leaves them. Run from anywhere: python3 docs/art/sever-lash/make_beam_mockup.py
"""
import math
import random
from pathlib import Path
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
OUT = HERE / 'beam-mockup.png'
BLOCK, REACH, SCALE = 16, 12, 3
W, H = 2 * REACH * BLOCK + 32, REACH * BLOCK + 40
C = {k: tuple(bytes.fromhex(v)) for k, v in dict(
    floor='3a4048', grid='434a53', snow='c9d3db', white='fff6e6', hot='ffd2a0', red='ff3b2a', deep='9e1a14',
    sheath='ff6a3a', violet='c43a7a', player='4a6a8a', label='b8c2c8', bg='20252b').items()}
EYE = (W // 2, H - 24)


def floor():
    im = Image.new('RGB', (W, H), C['floor'])
    d = ImageDraw.Draw(im)
    for x in range(0, W, BLOCK): d.line([(x, 0), (x, H)], C['grid'])
    for y in range(0, H, BLOCK): d.line([(0, y), (W, y)], C['grid'])
    d.rectangle((EYE[0] - 4, EYE[1] - 4, EYE[0] + 3, EYE[1] + 3), C['player'])
    return im, d


def tip(angle, length=REACH * BLOCK):
    return EYE[0] + math.sin(angle) * length, EYE[1] - math.cos(angle) * length


def thick(d, a, b, core, sheath, w_core=1, w_sheath=3):
    d.line([a, b], sheath, w_sheath * 2 + 1)
    d.line([a, b], core, w_core * 2 + 1)


def whip():
    """A: the far end lags the aim, so a fast sweep bends into a curve that snaps straight when it stops."""
    im, d = floor()
    aim, lag = math.radians(25), math.radians(28)
    # Fading afterimage of the last three ticks' beams.
    for k, colour in ((3, 'deep'), (2, 'deep'), (1, 'red')):
        a = aim - math.radians(14) * k
        pts = curve(a, lag)
        d.line(pts, C[colour], 1)
    thick_curve(d, curve(aim, lag))
    sparks(d, curve(aim, lag)[-1])
    return im


def curve(aim, lag, steps=24):
    pts = []
    for i in range(steps + 1):
        t = i / steps
        # Near the emitter the beam follows the aim at once; along its length it trails the sweep more and more.
        a = aim - lag * t * t
        pts.append(tip(a, REACH * BLOCK * t))
    return pts


def thick_curve(d, pts):
    d.line(pts, C['violet'], 9)
    d.line(pts, C['sheath'], 5)
    d.line(pts, C['white'], 2)


def sparks(d, at, n=14):
    r = random.Random(3)
    for _ in range(n):
        a, s = r.uniform(0, 2 * math.pi), r.uniform(3, 14)
        d.point((at[0] + math.cos(a) * s, at[1] + math.sin(a) * s), C[r.choice(['white', 'hot', 'red'])])


def plane():
    """B: a rigid straight beam; the arc it swept glows as a fading fan for a few ticks."""
    im, d = floor()
    aim = math.radians(25)
    for k, colour in ((3, (110, 20, 16)), (2, (160, 30, 20)), (1, (215, 50, 30))):
        a0, a1 = aim - math.radians(14) * k, aim - math.radians(14) * (k - 1)
        d.polygon([EYE, tip(a0), tip(a1)], colour)
    thick(d, EYE, tip(aim), C['white'], C['sheath'], 1, 3)
    sparks(d, tip(aim))
    return im


def arc():
    """C: a crackling plasma filament: jagged, re-drawn every frame, with short side forks."""
    im, d = floor()
    aim = math.radians(25)
    r = random.Random(7)
    pts = []
    for i in range(25):
        t = i / 24
        x, y = tip(aim, REACH * BLOCK * t)
        jitter = 0 if i in (0, 24) else r.uniform(-4, 4)
        pts.append((x + jitter * math.cos(aim), y + jitter * math.sin(aim)))
    d.line(pts, C['violet'], 5)
    d.line(pts, C['white'], 1)
    for i in (6, 13, 19):
        x, y = pts[i]
        d.line([(x, y), (x + r.uniform(-12, 12), y + r.uniform(-12, 12))], C['sheath'], 1)
    sparks(d, pts[-1])
    return im


def section():
    """The beam's cross-section at 16 px to the block: a 2 px white core inside a sheath about a third of a block."""
    im = Image.new('RGB', (BLOCK * 2, BLOCK * 2), C['floor'])
    d = ImageDraw.Draw(im)
    c = BLOCK
    d.ellipse((c - 5, c - 5, c + 5, c + 5), C['violet'])
    d.ellipse((c - 3, c - 3, c + 3, c + 3), C['sheath'])
    d.rectangle((c - 1, c - 1, c, c), C['white'])
    d.rectangle((0, 0, BLOCK - 1, BLOCK - 1), outline=C['grid'])
    return im


def main():
    panels = [('A  Whip: the far end trails the sweep', whip()), ('B  Cutting plane: the swept arc glows', plane()),
              ('C  Plasma arc: jagged and crackling', arc())]
    sheet = Image.new('RGB', (3 * (W * SCALE // 2 + 20) + 20, H * SCALE // 2 + 160), C['bg'])
    d = ImageDraw.Draw(sheet)
    for i, (name, im) in enumerate(panels):
        x = 20 + i * (W * SCALE // 2 + 20)
        d.text((x, 10), name, C['label'])
        sheet.paste(im.resize((W * SCALE // 2, H * SCALE // 2), Image.NEAREST), (x, 28))
    y = H * SCALE // 2 + 40
    d.text((20, y), 'Cross-section, 16 px = 1 block (shown 3x): white core, orange sheath, violet halo.', C['label'])
    sheet.paste(section().resize((BLOCK * 2 * SCALE, BLOCK * 2 * SCALE), Image.NEAREST), (20, y + 16))
    d.text((140, y + 30), 'Top-down, one 16 px grid square per block; the beam reaches 12 blocks.', C['label'])
    d.text((140, y + 46), 'In vacuum, only the core and the impact show; the sheath is light scattered by air.', C['label'])
    sheet.save(OUT)
    print(OUT)


if __name__ == '__main__':
    main()

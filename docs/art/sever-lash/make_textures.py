"""Draw the ORSA LC-4 Sever Lash item texture, the Seared effect icon and its ember particle.

Original pixel art authored in code for Frozen Dawn on 2026-11-02. No third-party textures or source images.
A gunmetal diagnostic emitter held diagonally like a tool: lead-grey grip, copper coil, acheronite lens and a
red-orange emitter tip. Seared (2026-11-03): a tapered diagonal burn, white-hot at the core and cooling to
deep red at the edges, with two embers lifting off it; the ember sprites are white so the particle tints them as
they cool. Lash Emitter (2026-11-03): the bare salvaged emitter, a short gunmetal tube in two copper coils with the
dead lens at one end and torn leads at the other. Heat glow (2026-11-03): overlays drawn over the Lash sprite as heat
nears overheat (warm, hot) and through the lockout (vented white-hot, then cooling red); the game renders all but
warm at full brightness. Run from anywhere: python3 docs/art/sever-lash/make_textures.py
"""
from pathlib import Path
from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
TEXTURES = ROOT / 'src/main/resources/assets/frozendawn/textures'
OUT = TEXTURES / 'item/sever_lash.png'

P = {k: tuple(bytes.fromhex(v)) + (255,) for k, v in dict(
    outline='0b0f12', dark='171c21', steel='414950', light='667077', pale='8c969c',
    lead='3a3d4a', lead2='545869', copper='976344', copper2='c18054',
    lens='5ad1e0', lens2='a8f0f5', tip='e5583a', tip2='ffb08a').items()}

# Grip at the bottom left, emitter at the top right; one pixel per body cell along the diagonal.
BODY = [(2, 13, 'lead'), (3, 12, 'lead2'), (4, 11, 'lead'), (5, 10, 'steel'), (6, 9, 'light'),
        (7, 8, 'copper'), (8, 7, 'copper2'), (9, 6, 'steel'), (10, 5, 'light'), (11, 4, 'lens'),
        (12, 3, 'lens2'), (13, 2, 'tip')]


SEAR = [(d, tuple(bytes.fromhex(v)) + (255,)) for d, v in (
    (.55, 'fff3d6'), (1.15, 'ffb35a'), (1.75, 'e5502a'), (2.3, '7a1d14'), (2.85, '24100e'))]


def seared_icon():
    """18x18: the burn runs from bottom left to top right and tapers at both ends."""
    im = Image.new('RGBA', (18, 18), (0, 0, 0, 0))
    ax, ay, bx, by = 3.5, 14.5, 14.5, 3.5
    for y in range(18):
        for x in range(18):
            px, py = x + .5, y + .5
            t = max(0., min(1., ((px - ax) * (bx - ax) + (py - ay) * (by - ay)) / ((bx - ax) ** 2 + (by - ay) ** 2)))
            d = ((px - ax - t * (bx - ax)) ** 2 + (py - ay - t * (by - ay)) ** 2) ** .5
            d /= .45 + .55 * (1 - abs(2 * t - 1))  # taper
            for edge, color in SEAR:
                if d < edge:
                    im.putpixel((x, y), color)
                    break
    for (x, y), color in (((12, 9), SEAR[1][1]), ((14, 7), SEAR[0][1]), ((8, 5), SEAR[2][1])):
        im.putpixel((x, y), color)
    return im


def ember(radius):
    """8x8 soft white disc, opaque at the centre."""
    im = Image.new('RGBA', (8, 8), (0, 0, 0, 0))
    for y in range(8):
        for x in range(8):
            d = ((x + .5 - 4) ** 2 + (y + .5 - 4) ** 2) ** .5
            if d < radius:
                im.putpixel((x, y), (255, 255, 255, int(255 * min(1., 1.6 * (1 - d / radius)))))
    return im


def emitter():
    """16x16: the tube lies level, lens to the right, leads trailing off the back."""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for x in range(3, 13):
        for y in range(6, 10):
            im.putpixel((x, y), P['light' if y == 6 else 'dark' if y == 9 else 'steel'])
    for x in (6, 9):
        for y in range(5, 11):
            im.putpixel((x, y), P['copper2' if y < 8 else 'copper'])
    for y in (7, 8):
        im.putpixel((13, y), P['lens2' if y == 7 else 'lens'])
    for x, y, c in ((2, 7, 'copper'), (1, 6, 'copper2'), (2, 9, 'tip'), (1, 10, 'tip')):
        im.putpixel((x, y), P[c])
    out = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            if im.getpixel((x, y))[3] == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and im.getpixel((x + dx, y + dy))[3]
                                                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out.putpixel((x, y), P['outline'])
    out.alpha_composite(im)
    return out


# Per overlay: (first body cell it covers, colour by body cell kind, colour of the shadow pixel under it).
GLOW = {
    'warm': (6, {'copper': 'a5523a', 'copper2': 'c0603e', 'steel': '7a2a1e', 'light': '9b3a26'}, None),
    'hot': (6, {'copper': 'ff8a3a', 'copper2': 'ffb35a', 'steel': 'c03a26', 'light': 'e5583a', 'lens': 'ffd9b0',
                'lens2': 'fff3d6', 'tip': 'ffb35a'}, '7a2a1e'),
    'vented': (4, {'lead': 'ff8a3a', 'lead2': 'ffb35a', 'steel': 'ffb35a', 'light': 'fff3d6', 'copper': 'ffb35a',
                   'copper2': 'fff3d6', 'lens': 'fff3d6', 'lens2': 'ffffff', 'tip': 'fff3d6'}, 'c03a26'),
    'cooling': (6, {'copper': 'c03a26', 'copper2': 'e5583a', 'steel': '9b2a1e', 'light': 'c03a26', 'lens': 'e5583a',
                    'lens2': 'ff8a3a', 'tip': 'e5583a'}, '5a1a14'),
}


def glow(name):
    first, colours, shadow = GLOW[name]
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for i, (x, y, kind) in enumerate(BODY):
        c = colours.get(kind) if i >= first else None
        if c:
            im.putpixel((x, y), tuple(bytes.fromhex(c)) + (255,))
            if shadow and kind in ('steel', 'light', 'copper', 'copper2'):
                im.putpixel((x + 1, y + 1), tuple(bytes.fromhex(shadow)) + (255,))
    if name in ('hot', 'vented'):
        im.putpixel((14, 1), tuple(bytes.fromhex('fff3d6')) + (255,))
    return im


def save(im, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)
    print(path)


def main():
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for x, y, color in BODY:
        for dx, dy in ((-1, 0), (0, 1), (-1, 1), (1, 0), (0, -1), (1, -1)):
            if im.getpixel((x + dx, y + dy))[3] == 0:
                im.putpixel((x + dx, y + dy), P['outline'])
    for x, y, color in BODY:
        im.putpixel((x, y), P[color])
        if color in ('steel', 'light', 'copper', 'copper2'):
            im.putpixel((x + 1, y + 1), P['dark'] if color != 'copper2' else P['copper'])
    im.putpixel((14, 1), P['tip2'])
    im.putpixel((5, 12), P['pale'])  # trigger
    save(im, OUT)
    save(emitter(), TEXTURES / 'item/lash_emitter.png')
    for name in GLOW:
        save(glow(name), TEXTURES / f'item/sever_lash_glow_{name}.png')
    save(seared_icon(), TEXTURES / 'mob_effect/seared.png')
    save(ember(3.6), TEXTURES / 'particle/sear_ember_0.png')
    save(ember(2.6), TEXTURES / 'particle/sear_ember_1.png')


if __name__ == '__main__':
    main()

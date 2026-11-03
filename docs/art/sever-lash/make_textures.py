"""Draw the ORSA LC-4 Sever Lash item texture, the Seared effect icon and its ember particle.

Original pixel art authored in code for Frozen Dawn on 2026-11-02. No third-party textures or source images.
A gunmetal diagnostic emitter held diagonally like a tool: lead-grey grip, copper coil, acheronite lens and a
red-orange emitter tip. Seared (2026-11-03): a tapered diagonal burn, white-hot at the core and cooling to
deep red at the edges, with two embers lifting off it; the ember sprites are white so the particle tints them as
they cool. Run from anywhere: python3 docs/art/sever-lash/make_textures.py
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
    save(seared_icon(), TEXTURES / 'mob_effect/seared.png')
    save(ember(3.6), TEXTURES / 'particle/sear_ember_0.png')
    save(ember(2.6), TEXTURES / 'particle/sear_ember_1.png')


if __name__ == '__main__':
    main()

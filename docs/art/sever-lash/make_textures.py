"""Draw the ORSA LC-4 Sever Lash model textures, the Lash Emitter, the Seared effect icon and its ember particle.

Original pixel art authored in code for Frozen Dawn on 2026-11-02. No third-party textures or source images.
A gunmetal diagnostic emitter held diagonally like a tool: lead-grey grip, copper coil, acheronite lens and a
red-orange emitter tip. Seared (2026-11-03): a tapered diagonal burn, white-hot at the core and cooling to
deep red at the edges, with two embers lifting off it; the ember sprites are white so the particle tints them as
they cool. Lash Emitter (2026-11-03): the bare salvaged emitter, a short gunmetal tube in two copper coils with the
dead lens at one end and torn leads at the other. Heat glow (2026-11-03): overlays drawn over the Lash sprite as heat
nears overheat (warm, hot) and through the lockout (vented white-hot, then cooling red); the game renders all but
warm at full brightness. 3D model (slice 2, 2026-11-03): the flat sprite and its overlays gave way to 16x16 material
tiles under item/sever_lash/, one texel a model unit, for the model in tools/build_sever_lash_models.py. Gunmetal with
a top-edge highlight, a dark lower row, panel seams and rivets; ribbed lead grip; copper windings; the cell port's
copper contacts; one tile per fin and barrel colour from cool steel to white-hot; the lens; and the vent slots, two
flickering frames each in white, orange and red. Run from anywhere: python3 docs/art/sever-lash/make_textures.py
"""
from pathlib import Path
from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
TEXTURES = ROOT / 'src/main/resources/assets/frozendawn/textures'
MODEL = TEXTURES / 'item/sever_lash'

P = {k: tuple(bytes.fromhex(v)) + (255,) for k, v in dict(
    outline='0b0f12', dark='171c21', steel='414950', light='667077', pale='8c969c',
    lead='3a3d4a', lead2='545869', copper='976344', copper2='c18054',
    lens='5ad1e0', lens2='a8f0f5', tip='e5583a', tip2='ffb08a').items()}

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


def hexc(v):
    return tuple(bytes.fromhex(v)) + (255,)


def tile(base, top=None, bottom=None, speckle=None, seed=0):
    """16x16 material: a base colour with a lighter top row, a darker bottom row and a fixed scatter of speckles."""
    im = Image.new('RGBA', (16, 16), hexc(base))
    for x in range(16):
        if top: im.putpixel((x, 0), hexc(top))
        if bottom: im.putpixel((x, 2), hexc(bottom))
    if speckle:
        state = seed * 7919 + 17
        for _ in range(14):
            state = (state * 1103515245 + 12345) % 2 ** 31
            x, y = state % 16, (state >> 8) % 16
            if y not in (0, 2) or not (top or bottom): im.putpixel((x, y), hexc(speckle))
    return im


def steel():
    """Gunmetal: highlight along the top edge, dark underside row, a panel seam every five texels, two rivets."""
    im = tile('414950', top='667077', bottom='171c21', speckle='4a535a', seed=1)
    for y in range(16):
        for x in (4, 9, 14):
            if y != 0: im.putpixel((x, y), hexc('2c3238'))
    for x in (1, 7):
        im.putpixel((x, 1), hexc('8c969c'))
    return im


def lead(base, rib):
    im = tile(base, speckle=rib, seed=2)
    for y in range(1, 16, 2):
        for x in range(16):
            im.putpixel((x, y), hexc(rib))
    return im


def copper():
    """Windings: alternating bright and dark copper rows, a shadow on every fourth."""
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        c = 'c18054' if y % 2 else '976344'
        for x in range(16):
            im.putpixel((x, y), hexc('6e4630' if y % 4 == 3 and x % 3 == 0 else c))
    return im


def cap():
    im = tile('545869', top='6a6e80', speckle='3a3d4a', seed=3)
    for x, y in ((1, 1), (1, 4), (1, 5)):
        im.putpixel((x, y), hexc('c18054'))
    return im


def lens(base, glint):
    im = tile(base, speckle=glint, seed=4)
    im.putpixel((0, 0), hexc('ffffff'))
    return im


FIN_COLOURS = dict(steel=('667077', '8c969c'), pale=('8c969c', 'b4bcc0'), dull=('7a2a1e', '9b3a26'),
                   red=('c03a26', 'e5583a'), orange=('ff8a3a', 'ffb35a'), amber=('ffb35a', 'ffd9a0'),
                   white=('fff3d6', 'ffffff'))


def slot(a, b):
    """Two frames, 16x32, for the flickering vent slot."""
    im = Image.new('RGBA', (16, 32))
    im.paste(tile(a, speckle=b, seed=5), (0, 0))
    im.paste(tile(b, speckle=a, seed=6), (0, 16))
    return im


def model_textures():
    out = dict(steel=steel(), underside=tile('171c21', speckle='0b0f12', seed=7), lead=lead('3a3d4a', '30333e'),
               guard=lead('545869', '4a4e5e'), copper=copper(), cap=cap(), lens=lens('5ad1e0', 'a8f0f5'),
               lens_hot=lens('a8f0f5', 'fff3d6'), lens_white=lens('fff3d6', 'ffffff'))
    for name, (base, top) in FIN_COLOURS.items():
        out['fin_' + name] = tile(base, top=top, speckle=top, seed=8)
    for name, (a, b) in dict(white=('fff3d6', 'ffb35a'), orange=('ff8a3a', 'ffd9a0'), red=('c03a26', 'ff6a3a')).items():
        out['slot_' + name] = slot(a, b)
    return out


def save(im, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)
    print(path)


def main():
    for name, im in model_textures().items():
        save(im, MODEL / f'{name}.png')
        if name.startswith('slot_'):
            (MODEL / f'{name}.png.mcmeta').write_text('{"animation": {"frametime": 2}}\n')
    save(emitter(), TEXTURES / 'item/lash_emitter.png')
    save(seared_icon(), TEXTURES / 'mob_effect/seared.png')
    save(ember(3.6), TEXTURES / 'particle/sear_ember_0.png')
    save(ember(2.6), TEXTURES / 'particle/sear_ember_1.png')


if __name__ == '__main__':
    main()

"""Three texture directions for the ORSA TR-0 Terminus, for the owner to pick from (2026-11-03).

The owner asked for a really unique texture. Each direction is drawn at Minecraft scale (one texel a model unit, 16 to
the block) on the same blocky geometry, in four animation frames of the idle loop plus the wind-up and the spent
trophy. Nothing here is in the game yet. Run from anywhere: python3 docs/art/terminus/make_texture_directions.py
"""
from pathlib import Path
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
OUT = HERE / 'texture-directions.png'
SCALE = 9

C = {k: tuple(bytes.fromhex(v)) + (255,) for k, v in dict(
    grip='1b1d21', knurl='30343a', steel='5d666d', steel2='79838a', plate='474c52',
    bone='e2dccd', bone2='c4bdab', vein='3b3a3f', light='a9f0ff', light2='5fb4c8', white='f6fbff',
    void='050607', seam='7d8b93', seam2='d9f4ff', speck='cfe8ff',
    frame='8c969c', mote='eaf8ff', mote2='9fd6e8',
    bg='20252b', panel='2a3037', text='8c969c').items()}
GLASS = (196, 226, 236, 70)
FROST = (226, 240, 246, 150)

# x runs back (0) to front (16), y up, z across.
GEOMETRY = [
    (0, 5, 6, 1, 8, 9, 'pommel'),
    (1, 6, 7, 6, 8, 9, 'grip'),
    (2, 5, 6, 3, 9, 10, 'knurl'), (4, 5, 6, 5, 9, 10, 'knurl'),
    (6, 4, 5, 7, 9, 10, 'collar'),
    (7, 5, 6, 14, 8, 9, 'shaft'),
    (14, 4, 5, 15, 9, 10, 'rim'),
    (15, 5, 6, 16, 8, 9, 'face'),
]


def base(part):
    return dict(pommel='plate', grip='grip', knurl='knurl', collar='steel', rim='steel').get(part)


# ---- Direction A, "Dendrite": bone ceramic with an inlaid branching filigree; light runs it toward the emitter.
DENDRITE = {(8, 6), (9, 6), (9, 7), (10, 5), (10, 7), (11, 6), (11, 5), (12, 7), (12, 6), (13, 5), (13, 7)}


def dendrite(x, y, z, part, frame, state):
    if part == 'shaft':
        face_pixel = (x, y) in DENDRITE or (x, z) in {(8, 7), (10, 6), (11, 8), (12, 7), (13, 6)} and y == 7
        if not face_pixel:
            return 'bone2' if y == 5 else 'bone'
        if state == 'spent':
            return 'vein'
        if state == 'wind':
            return 'white' if x <= 8 + frame * 2 else 'light'
        return 'light' if (x - 8) % 4 == frame else ('light2' if (x - 8) % 4 == (frame + 3) % 4 else 'vein')
    if part == 'face':
        return {'wind': 'white', 'spent': 'vein'}.get(state, 'light2')
    if part == 'pommel' and y == 6 and z == 8 and state != 'spent':
        return 'light'
    return base(part)


# ---- Direction B, "Null": a shaft so black it reads as a hole in the picture, one hairline seam, specks in the aperture.
def null(x, y, z, part, frame, state):
    if part == 'shaft':
        if y == 7 and z == 8:
            if state == 'spent':
                return 'void'
            if state == 'wind':
                return 'seam2' if x <= 8 + frame * 2 else 'seam'
            return 'seam'
        return 'void'
    if part in ('collar', 'rim'):
        return 'void' if y in (4, 8) else 'steel'
    if part == 'face':
        if state == 'spent':
            return 'void'
        specks = [(6, 7), (7, 6), (6, 6), (7, 7)]
        if state == 'wind':
            return 'white'
        return 'speck' if (y, z) == specks[frame] else 'void'
    return base(part)


# ---- Direction C, "Reliquary": a glass shaft with one suspended mote per discharge left, drifting.
def reliquary(x, y, z, part, frame, state):
    if part == 'shaft':
        if y in (5, 7) and z in (6, 8) or x in (7, 13):
            return 'frame'                                   # steel cage at the corners and ends
        lit = {'found': 3, 'wind': 3, 'one': 1, 'spent': 0}[state]
        motes = [(8, (0, 1, 1, 0)), (10, (1, 1, 0, 0)), (12, (1, 0, 0, 1))][:lit]
        for mx, bob in motes:
            if x == mx + bob[frame] and y == 6 and z == 7:
                return 'white' if state == 'wind' else 'mote'
        return 'frost' if state == 'wind' else 'glass'
    if part == 'face':
        return 'white' if state == 'wind' else ('plate' if state == 'spent' else 'mote2')
    return base(part)


DIRECTIONS = [
    ('A  Dendrite', dendrite, 'Bone ceramic with a dark inlaid filigree, branching like nerves. Pale light walks the '
                              'branches toward the emitter; on wind-up it floods them white. Spent, the inlay goes dead.'),
    ('B  Null', null, 'The shaft is unlit, flat black, so it reads as a cut-out in the picture. One hairline seam on '
                      'top; a single speck drifts in the aperture. Wind-up runs the seam white.'),
    ('C  Reliquary', reliquary, 'A caged glass shaft holding one pale mote per discharge left; the motes drift. The count '
                                'on the tooltip is visible in the hand. Wind-up frosts the glass.'),
]
STATES = [('idle 1', 'found', 0), ('idle 2', 'found', 1), ('idle 3', 'found', 2), ('idle 4', 'found', 3),
          ('wind-up', 'wind', 3), ('1 left', 'one', 0), ('spent', 'spent', 0)]


def voxels(paint, frame, state):
    v = {}
    for x0, y0, z0, x1, y1, z1, part in GEOMETRY:
        for x in range(x0, x1):
            for y in range(y0, y1):
                for z in range(z0, z1):
                    v[(x, y, z)] = paint(x, y, z, part, frame, state)
    return v


def colour(c):
    return GLASS if c == 'glass' else FROST if c == 'frost' else C[c]


def side(v):
    """Orthographic view from +z, far to near, so glass shows what it holds."""
    im = Image.new('RGBA', (18, 14), C['panel'])
    for (x, y, z), c in sorted(v.items(), key=lambda kv: kv[0][2]):
        px = Image.new('RGBA', (1, 1), colour(c))
        im.alpha_composite(px, (x + 1, 13 - y))
    return im


def iso(v):
    w = 4
    im = Image.new('RGBA', (110, 80), C['panel'])

    def shade(c, k):
        r, g, b, a = colour(c)
        if c in ('void',):
            k = 1
        if c in ('white', 'light', 'mote', 'speck', 'seam2'):
            k = 1
        return (min(255, int(r * k)), min(255, int(g * k)), min(255, int(b * k)), a)

    for (x, y, z), c in sorted(v.items(), key=lambda kv: (kv[0][0] + kv[0][2], kv[0][1])):
        sx, sy = 36 + (x - z) * w, 26 + (x + z) * w // 2 - y * w
        layer = Image.new('RGBA', im.size)
        d = ImageDraw.Draw(layer)
        d.polygon([(sx - w, sy + w // 2), (sx, sy + w), (sx, sy + 2 * w), (sx - w, sy + w * 3 // 2)], shade(c, .8))
        d.polygon([(sx, sy + w), (sx + w, sy + w // 2), (sx + w, sy + w * 3 // 2), (sx, sy + 2 * w)], shade(c, .6))
        d.polygon([(sx, sy), (sx + w, sy + w // 2), (sx, sy + w), (sx - w, sy + w // 2)], shade(c, 1.1))
        im.alpha_composite(layer)
    return im


def main():
    cell_w, row_h = 18 * SCALE + 8, 14 * SCALE + 22
    iso_w = 110 * 2
    width = 10 + len(STATES) * cell_w + iso_w + 20
    sheet = Image.new('RGBA', (width, 20 + len(DIRECTIONS) * (row_h + 60)), C['bg'])
    d = ImageDraw.Draw(sheet)
    for r, (name, paint, blurb) in enumerate(DIRECTIONS):
        y = 10 + r * (row_h + 60)
        d.text((10, y), name, fill=C['white'])
        d.text((10, y + 14), blurb, fill=C['text'])
        for i, (label, state, frame) in enumerate(STATES):
            x = 10 + i * cell_w
            s = side(voxels(paint, frame, state))
            d.text((x, y + 34), label, fill=C['text'])
            sheet.alpha_composite(s.resize((s.width * SCALE, s.height * SCALE), Image.NEAREST), (x, y + 48))
        i3 = iso(voxels(paint, 0, 'found'))
        sheet.alpha_composite(i3.resize((iso_w, 160), Image.NEAREST), (10 + len(STATES) * cell_w, y + 30))
    sheet.save(OUT)
    print(OUT)


if __name__ == '__main__':
    main()

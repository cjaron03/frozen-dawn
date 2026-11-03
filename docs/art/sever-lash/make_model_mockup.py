"""Mockup of the proposed LC-4 Sever Lash 3D item model, its heat states and how it is held.

Draft for owner approval, 2026-11-03; nothing here is in the game yet. Drawn at Minecraft scale: one model unit is one
pixel and a block is 16, so the Lash is one block long. Each box below becomes one model element. Run from anywhere:
python3 docs/art/sever-lash/make_model_mockup.py
"""
from pathlib import Path
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
OUT = HERE / 'model-mockup.png'
SCALE = 10

C = {k: tuple(bytes.fromhex(v)) + (255,) for k, v in dict(
    outline='0b0f12', dark='171c21', steel='414950', light='667077', pale='8c969c',
    lead='3a3d4a', lead2='545869', copper='976344', copper2='c18054',
    lens='5ad1e0', lens2='a8f0f5', dull='7a2a1e', red='c03a26', orange='ff8a3a', amber='ffb35a', white='fff3d6',
    smoke='9aa3a8', skin='c99a76', sleeve='3f5a6e', trouser='2c3440', hair='3b2a1e', bg='20252b', panel='2a3037').items()}

# x runs back (0) to front (16), y up, z across. (x0, y0, z0, x1, y1, z1, part)
BOXES = [
    (0, 7, 6, 2, 10, 9, 'cap'),        # Power Cell port, copper contacts at the back
    (2, 7, 6, 11, 10, 9, 'body'),
    (3, 5, 7, 5, 7, 9, 'grip'), (2, 2, 7, 4, 5, 9, 'grip'),
    (5, 3, 6, 6, 7, 9, 'guard'), (2, 1, 6, 6, 2, 9, 'guard'),  # lead hand shield
    (8, 6, 5, 9, 11, 10, 'coil'), (10, 6, 5, 11, 11, 10, 'coil'),
    (11, 8, 7, 15, 10, 9, 'barrel'),
    (15, 8, 7, 16, 10, 9, 'lens'),
]
FINS = [3, 5, 7]

STATES = {
    # name: fin, fin tip, barrel tip, lens, vent glow (None = closed), fin opening (0, 1, 2 = 0, 22.5, 45 degrees), smoke
    'cool': ('light', 'pale', 'steel', 'lens', None, 0, False),
    'warm': ('light', 'dull', 'dull', 'lens', None, 0, False),
    'hot': ('red', 'orange', 'orange', 'lens2', None, 0, False),
    'overheat, air': ('amber', 'white', 'orange', 'white', 'white', 2, True),
    'cooling, air': ('red', 'orange', 'dull', 'lens2', 'orange', 1, True),
    'overheat, vacuum': ('amber', 'white', 'orange', 'white', 'white', 2, False),
}
BASE = dict(cap='lead2', body='steel', grip='lead', guard='lead2', coil='copper', barrel='steel', lens='lens')


def voxels(state):
    fin, tip, barrel_tip, lens, vent, opening, _ = STATES[state]
    v = {}
    for x0, y0, z0, x1, y1, z1, part in BOXES:
        for x in range(x0, x1):
            for y in range(y0, y1):
                for z in range(z0, z1):
                    c = BASE[part]
                    if part == 'coil' and (y + z) % 2: c = 'copper2'
                    if part == 'cap' and x == 0 and y == 8: c = 'copper2'
                    if part == 'barrel' and x >= 13: c = barrel_tip
                    if part == 'lens': c = lens
                    if part == 'body' and y == 7: c = 'dark'
                    v[(x, y, z)] = c
    for fx in FINS:
        for z in range(6, 9):
            # Closed fins stand straight; venting fins lean back at 22.5, then lie back at 45 degrees.
            v[(fx, 10, z)] = fin
            v[{0: (fx, 11, z), 1: (fx - 1, 11, z), 2: (fx - 1, 10, z)}[opening]] = tip
            if vent:
                v[(fx + 1, 9, z)] = vent  # the slot under each fin glows once it lifts
    return v


def side(v):
    """Orthographic view from +z: nearest voxel wins."""
    im = Image.new('RGBA', (18, 16), C['panel'])
    for (x, y, z), c in sorted(v.items(), key=lambda kv: kv[0][2]):
        im.putpixel((x + 1, 15 - y), C[c])
    return im


def iso(v):
    """Blocky isometric render: every voxel is a small cube, painter's order back to front."""
    w = 4
    im = Image.new('RGBA', (120, 90), C['panel'])
    d = ImageDraw.Draw(im)

    def shade(c, k):
        r, g, b, a = C[c]
        return (int(r * k), int(g * k), int(b * k), a)

    # Viewed from +x +z: x runs down-right, z down-left, so larger x + z is nearer and drawn later.
    for (x, y, z), c in sorted(v.items(), key=lambda kv: (kv[0][0] + kv[0][2], kv[0][1])):
        sx, sy = 40 + (x - z) * w, 30 + (x + z) * w // 2 - y * w
        top = [(sx, sy), (sx + w, sy + w // 2), (sx, sy + w), (sx - w, sy + w // 2)]
        left = [(sx - w, sy + w // 2), (sx, sy + w), (sx, sy + 2 * w), (sx - w, sy + w * 3 // 2)]
        right = [(sx, sy + w), (sx + w, sy + w // 2), (sx + w, sy + w * 3 // 2), (sx, sy + 2 * w)]
        d.polygon(left, shade(c, .8))
        d.polygon(right, shade(c, .6))
        d.polygon(top, shade(c, 1.1 if c not in ('white', 'amber', 'orange') else 1))
    return im


def smoke(im, state):
    if not STATES[state][6]:
        return
    for x, y in ((3, 1), (5, 0), (7, 1), (4, 2), (8, 0)):
        im.putpixel((x, y), C['smoke'])


def hold(firing):
    """Third person, side view, a Steve-sized player facing right (32 px tall), Lash in the right hand."""
    im = Image.new('RGBA', (40, 34), C['panel'])
    d = ImageDraw.Draw(im)
    d.rectangle((14, 21, 17, 32), C['trouser'])                 # leg
    d.rectangle((14, 9, 17, 20), C['sleeve'])                   # torso, 4 deep
    d.rectangle((12, 1, 19, 8), C['skin']); d.rectangle((12, 1, 19, 2), C['hair']); d.rectangle((12, 1, 13, 6), C['hair'])
    lash = side(voxels('hot' if firing else 'cool')).crop((1, 5, 17, 15))
    if firing:
        # Main arm raised level to the eye line, off hand reaching under the coil to brace it.
        d.rectangle((16, 12, 32, 14), C['sleeve']); d.rectangle((31, 12, 33, 14), C['skin'])
        im.alpha_composite(lash, (24, 5))
        d.rectangle((16, 9, 26, 12), C['sleeve']); d.rectangle((25, 9, 27, 12), C['skin'])
    else:
        # Idle: arm down, Lash carried one-handed at the hip, pointing forward.
        im.alpha_composite(lash, (12, 15))
        d.rectangle((14, 9, 17, 19), C['sleeve']); d.rectangle((14, 18, 17, 20), C['skin'])
    return im


def label(sheet, x, y, text):
    ImageDraw.Draw(sheet).text((x, y), text, fill=C['pale'])


def main():
    cells = []
    for name in STATES:
        v = voxels(name)
        s = side(v); smoke(s, name)
        cells.append((name, s.resize((s.width * SCALE, s.height * SCALE), Image.NEAREST),
                      iso(v).resize((240, 180), Image.NEAREST)))
    holds = [('third person, idle', hold(False)), ('third person, firing', hold(True))]
    width = 3 * (180 + 240 + 30) + 20
    sheet = Image.new('RGBA', (width, 2 * 210 + 400), C['bg'])
    for i, (name, s, i3) in enumerate(cells):
        x, y = 10 + (i % 3) * 450, 10 + (i // 3) * 210
        label(sheet, x, y, name)
        sheet.alpha_composite(s, (x, y + 16))
        sheet.alpha_composite(i3, (x + 190, y + 16))
    for i, (name, im) in enumerate(holds):
        x, y = 10 + i * 450, 10 + 2 * 210
        label(sheet, x, y, name)
        sheet.alpha_composite(im.resize((im.width * SCALE, im.height * SCALE), Image.NEAREST), (x, y + 16))
    label(sheet, 910, 440, 'Side views: 1 px = 1 model unit; 16 = 1 block.')
    label(sheet, 910, 456, 'Grip and guard are lead; coil copper;')
    label(sheet, 910, 472, 'lens acheronite. Three top fins splay')
    label(sheet, 910, 488, 'open to vent on overheat.')
    sheet.save(OUT)
    print(OUT)


if __name__ == '__main__':
    main()

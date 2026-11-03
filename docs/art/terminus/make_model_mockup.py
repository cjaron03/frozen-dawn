"""Mockup of the proposed ORSA TR-0 Terminus 3D item model, its states and how it is held.

Draft for owner approval, 2026-11-03; nothing here is in the game yet. Drawn at Minecraft scale: one model unit is one
pixel and a block is 16, so the wand is one block long. Each box below becomes one model element. Run from anywhere:
python3 docs/art/terminus/make_model_mockup.py
"""
from pathlib import Path
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
OUT = HERE / 'model-mockup.png'
SCALE = 10

C = {k: tuple(bytes.fromhex(v)) + (255,) for k, v in dict(
    outline='0b0f12', grip='1b1d21', knurl='30343a', steel='5d666d', pale='8c969c',
    ceramic='d8d3c6', ceramic2='b9b3a5', seam='8f8a7e', plate='474c52',
    pip='9fe3ee', pip_dark='2a2f35', face='6f777d', face_dead='3a3f44', glow='eef6ff', halo='bcd6e4',
    skin='c99a76', sleeve='3f5a6e', trouser='2c3440', hair='3b2a1e', bg='20252b', panel='2a3037').items()}

# x runs back (0) to front (16), y up, z across. (x0, y0, z0, x1, y1, z1, part)
BOXES = [
    (0, 6, 6, 2, 9, 9, 'cap'),          # pommel, serial plate on the side
    (2, 6, 7, 7, 8, 9, 'grip'),         # 2x2 matte black grip
    (3, 5, 6, 4, 9, 9, 'knurl'), (5, 5, 6, 6, 9, 9, 'knurl'),
    (7, 5, 6, 8, 9, 9, 'collar'),
    (8, 6, 7, 14, 8, 9, 'shaft'),       # bone-white ceramic, no seams but one
    (14, 5, 6, 15, 9, 9, 'rim'),        # emitter rim
    (15, 6, 7, 16, 8, 9, 'face'),       # the emitter: a flat grey face, no lens
]
PIPS = [9, 11, 13]  # discharge pips on top of the shaft, front ones go dark first

STATES = {
    # name: pips lit, emitter face, halo around the face
    'found (3 left)': (3, 'face', False),
    'winding up': (3, 'glow', True),
    '1 left': (1, 'face', False),
    'spent (trophy)': (0, 'face_dead', False),
}
BASE = dict(cap='plate', grip='grip', knurl='knurl', collar='steel', shaft='ceramic', rim='steel', face='face')


def voxels(state):
    lit, face, halo = STATES[state]
    v = {}
    for x0, y0, z0, x1, y1, z1, part in BOXES:
        for x in range(x0, x1):
            for y in range(y0, y1):
                for z in range(z0, z1):
                    c = BASE[part]
                    if part == 'shaft' and y == 6: c = 'ceramic2'
                    if part == 'shaft' and x == 10: c = 'seam'
                    if part == 'face': c = face
                    v[(x, y, z)] = c
    for i, px in enumerate(PIPS):
        v[(px, 8, 7)] = 'pip' if i < lit else 'pip_dark'
    if halo:
        for y in range(5, 9):
            for z in range(6, 9):
                if (y, z) not in ((6, 7), (6, 8), (7, 7), (7, 8)):
                    v[(15, y, z)] = 'halo'
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
        return (min(255, int(r * k)), min(255, int(g * k)), min(255, int(b * k)), a)

    for (x, y, z), c in sorted(v.items(), key=lambda kv: (kv[0][0] + kv[0][2], kv[0][1])):
        sx, sy = 40 + (x - z) * w, 30 + (x + z) * w // 2 - y * w
        top = [(sx, sy), (sx + w, sy + w // 2), (sx, sy + w), (sx - w, sy + w // 2)]
        left = [(sx - w, sy + w // 2), (sx, sy + w), (sx, sy + 2 * w), (sx - w, sy + w * 3 // 2)]
        right = [(sx, sy + w), (sx + w, sy + w // 2), (sx + w, sy + w * 3 // 2), (sx, sy + 2 * w)]
        bright = c in ('glow', 'halo', 'pip')
        d.polygon(left, shade(c, 1 if bright else .8))
        d.polygon(right, shade(c, 1 if bright else .6))
        d.polygon(top, shade(c, 1 if bright else 1.1))
    return im


def hold(charging):
    """Third person, side view, a Steve-sized player facing right (32 px tall), wand in the right hand."""
    im = Image.new('RGBA', (40, 34), C['panel'])
    d = ImageDraw.Draw(im)
    d.rectangle((14, 21, 17, 32), C['trouser'])
    d.rectangle((14, 9, 17, 20), C['sleeve'])
    d.rectangle((12, 1, 19, 8), C['skin']); d.rectangle((12, 1, 19, 2), C['hair']); d.rectangle((12, 1, 13, 6), C['hair'])
    wand = side(voxels('winding up' if charging else 'found (3 left)')).crop((1, 6, 17, 12))
    if charging:
        # One arm held straight out at the eye line, the wand pointing along it; nothing braces it.
        d.rectangle((16, 9, 28, 12), C['sleeve'])
        im.alpha_composite(wand, (25, 8))
        d.rectangle((28, 9, 30, 12), C['skin'])
    else:
        # Idle: arm down, wand held in the fist, pointing forward.
        im.alpha_composite(wand, (11, 16))
        d.rectangle((14, 9, 17, 19), C['sleeve']); d.rectangle((14, 18, 17, 20), C['skin'])
    return im


def label(sheet, x, y, text):
    ImageDraw.Draw(sheet).text((x, y), text, fill=C['pale'])


def main():
    cells = []
    for name in STATES:
        v = voxels(name)
        s = side(v)
        cells.append((name, s.resize((s.width * SCALE, s.height * SCALE), Image.NEAREST),
                      iso(v).resize((240, 180), Image.NEAREST)))
    holds = [('third person, idle', hold(False)), ('third person, winding up', hold(True))]
    width = 2 * (180 + 240 + 30) + 20
    sheet = Image.new('RGBA', (width, 2 * 210 + 400), C['bg'])
    for i, (name, s, i3) in enumerate(cells):
        x, y = 10 + (i % 2) * 450, 10 + (i // 2) * 210
        label(sheet, x, y, name)
        sheet.alpha_composite(s, (x, y + 16))
        sheet.alpha_composite(i3, (x + 190, y + 16))
    for i, (name, im) in enumerate(holds):
        x, y = 10 + i * 450, 10 + 2 * 210
        label(sheet, x, y, name)
        sheet.alpha_composite(im.resize((im.width * SCALE, im.height * SCALE), Image.NEAREST), (x, y + 16))
    sheet.save(OUT)
    print(OUT)


if __name__ == '__main__':
    main()

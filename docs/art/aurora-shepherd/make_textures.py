"""The game textures for Aurora, her dock and Nine, drawn from the same voxels and colors as the approved mockups.

Writes into src/main/resources. Run from anywhere: python3 docs/art/aurora-shepherd/make_textures.py
"""
import json
from pathlib import Path
from PIL import Image
from make_body_directions import C, lantern
from make_flame_and_faces_mockup import flame
import make_dock_mockup  # noqa: F401, adds the dock's colors to C
from make_nine_husk_mockup import upright
from make_nine_lamp_mockup import ICON, ICON_COLORS, carried

HERE = Path(__file__).resolve().parent
TEXTURES = HERE.parents[2] / 'src/main/resources/assets/frozendawn/textures'
BLOCK = TEXTURES / 'block'
MODELS = TEXTURES.parent / 'models/block'
C['led_off'] = (30, 34, 38)


def scuffed(base, x, y):
    """A faint texel-sized wear pattern, so steel does not read as flat fill."""
    n = (x * 7 + y * 13) % 11
    return tuple(max(0, v - 8) for v in C[base]) if n == 0 else C[base]


def flat(name, base):
    img = Image.new('RGB', (16, 16))
    for x in range(16):
        for y in range(16):
            img.putpixel((x, y), scuffed(base, x, y))
    img.save(BLOCK / f'{name}.png')


def side():
    """Only the bottom six rows show: the base is six texels tall."""
    img = Image.new('RGB', (16, 16), C['dark'])
    for x in range(16):
        for y in range(10, 16):
            img.putpixel((x, y), scuffed('steel' if y == 10 else 'dark', x, y))
    img.save(BLOCK / 'cs1_dock_side.png')


def front(name, led, cell):
    img = Image.new('RGB', (16, 16), C['dark'])
    px = lambda x, y, color: img.putpixel((x, 15 - y), color)
    for x in range(16):
        for y in range(6):
            px(x, y, scuffed('steel' if y == 5 else 'dark', x, y))
        px(x, 1, C['stripe' if x % 4 < 2 else 'stripe2'])
    for x in range(5, 11):
        for y in range(2, 5):
            px(x, y, C['slot'])
    if cell:
        for x in range(6, 10):
            for y in range(2, 5):
                px(x, y, C['cell' if y > 2 else 'cell2'])
    px(12, 3, C[led])
    px(13, 3, C[led])
    img.save(BLOCK / f'cs1_dock_front_{name}.png')


# The Lantern's boxes, as ShepherdModel builds them: texture u, v, then the voxel corners from make_body_directions.lantern().
BOXES = [
    (0, 0, 0, 2, 0, 12, 12, 10),      # body
    (0, 20, 3, 0, 2, 9, 2, 8),        # heater
    (24, 20, -3, 5, 2, 0, 9, 8),      # left duct
    (42, 20, 12, 5, 2, 15, 9, 8),     # right duct
    (0, 30, -3, 3, 5, 0, 4, 6),       # left stripe
    (8, 30, 12, 3, 5, 15, 4, 6),      # right stripe
    (16, 30, 2, 12, 4, 3, 15, 5),     # handle posts
    (20, 30, 9, 12, 4, 10, 15, 5),
    (24, 30, 2, 15, 4, 10, 16, 5),    # handle bar
    (44, 0, 3, 12, 3, 9, 15, 6),      # Nine's lamp, in the handle; drawn only once she has it
    (0, 34, 4, -1, 3, 8, 0, 7),       # flame, from the grille down; pilot blue, the owner's pick
    (16, 34, 4, -2, 4, 8, -1, 6),
    (28, 34, 5, -2, 3, 7, -1, 7),
    (40, 34, 5, -3, 4, 7, -2, 6),
    (48, 34, 5, -4, 4, 6, -3, 5),
]


def unit():
    """Her skin, on Minecraft's unfolded cube layout. The display stays dark: the face is drawn over it, full bright."""
    body = carried()
    for x in range(2, 10):
        for y in range(4, 10):
            body.v[(x, y, 9)] = 'screen'
    flame(body, 'b', 3)
    for x, y, z in [(x, -1 - n, z) for n, (x0, x1, z0, z1) in enumerate(((4, 8, 3, 7), (4, 8, 3, 7), (5, 7, 4, 6), (5, 6, 4, 5)))
                    for x in range(x0, x1) for z in range(z0, z1)]:
        body.v.setdefault((x, y, z), 'b1')
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))

    def paint(px, py, x, y, z):
        img.putpixel((px, py), C[body.v[(x, y, z)]] + (255,))

    for u, v, x0, y0, z0, x1, y1, z1 in BOXES:
        dx, dy, dz = x1 - x0, y1 - y0, z1 - z0
        for c in range(dx):
            for r in range(dz):
                paint(u + dz + c, v + r, x0 + c, y1 - 1, z0 + r)
                paint(u + dz + dx + c, v + r, x0 + c, y0, z0 + r)
            for r in range(dy):
                paint(u + dz + c, v + dz + r, x0 + c, y1 - 1 - r, z1 - 1)
                paint(u + 2 * dz + dx + c, v + dz + r, x1 - 1 - c, y1 - 1 - r, z0)
        for c in range(dz):
            for r in range(dy):
                paint(u + c, v + dz + r, x0, y1 - 1 - r, z0 + c)
                paint(u + dz + dx + c, v + dz + r, x1 - 1, y1 - 1 - r, z1 - 1 - c)
    # The face's two colors, for ShepherdFaceRenderer to point its quads at.
    for x in range(60, 62):
        for y in range(60, 62):
            img.putpixel((x, y), C['glow'] + (255,))
            img.putpixel((x + 2, y), C['glow2'] + (255,))
    img.save(TEXTURES / 'entity/cs1_unit.png')


def cuboids(cells):
    """The fewest boxes a plain sweep finds that fill these voxels exactly."""
    left, boxes = set(cells), []
    for x, y, z in sorted(cells, key=lambda c: (c[1], c[2], c[0])):
        if (x, y, z) not in left:
            continue
        x1, z1, y1 = x + 1, z + 1, y + 1
        while (x1, y, z) in left:
            x1 += 1
        while all((i, y, z1) in left for i in range(x, x1)):
            z1 += 1
        while all((i, y1, k) in left for i in range(x, x1) for k in range(z, z1)):
            y1 += 1
        left -= {(i, j, k) for i in range(x, x1) for j in range(y, y1) for k in range(z, z1)}
        boxes.append((x, y, z, x1, y1, z1))
    return boxes


def face_cells(box, face):
    """Each face as Minecraft maps it: rows of voxels, left to right as seen from outside."""
    x0, y0, z0, x1, y1, z1 = box
    xs, zs, ys = range(x0, x1), range(z0, z1), range(y1 - 1, y0 - 1, -1)
    return {
        'north': [[(x, y, z0) for x in reversed(xs)] for y in ys],
        'south': [[(x, y, z1 - 1) for x in xs] for y in ys],
        'west': [[(x0, y, z) for z in zs] for y in ys],
        'east': [[(x1 - 1, y, z) for z in reversed(zs)] for y in ys],
        'up': [[(x, y1 - 1, z) for x in xs] for z in zs],
        'down': [[(x, y0, z) for x in xs] for z in reversed(zs)],
    }[face]


FACES = ('north', 'south', 'west', 'east', 'up', 'down')


def layout(boxes, size=64):
    """Every face a place on one sheet, in rows, tallest first. Both skins share it, so the out-model only swaps the texture."""
    faces = sorted(((box, face) for box in boxes for face in FACES), key=lambda f: -len(face_cells(*f)))
    spots, u, v, row = {}, 0, 0, 0
    for box, face in faces:
        rows = face_cells(box, face)
        w, h = len(rows[0]), len(rows)
        if u + w > size:
            u, v, row = 0, v + row, 0
        spots[(box, face)] = (u, v, w, h)
        u, row = u + w, max(row, h)
    assert v + row <= size, 'the husk no longer fits its sheet'
    return spots


def husk_voxels(lamp):
    """The approved pose A, without the patch of floor it was drawn on."""
    return {k: v for k, v in upright(lamp).v.items() if k[1] >= 0}


def husk(size=64):
    """Nine, as a block model: its broken handle and missing duct are not boxes ShepherdModel has, and it never moves."""
    boxes = cuboids(husk_voxels(True))
    spots = layout(boxes, size)
    for name, lamp in (('cs1_husk', True), ('cs1_husk_out', False)):
        voxels = husk_voxels(lamp)
        img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        for (box, face), (u, v, _, _) in spots.items():
            for r, row in enumerate(face_cells(box, face)):
                for c, cell in enumerate(row):
                    img.putpixel((u + c, v + r), C[voxels[cell]] + (255,))
        img.save(BLOCK / f'{name}.png')
    scale = 16 / size
    elements = []
    for box in boxes:
        x0, y0, z0, x1, y1, z1 = box
        faces = {}
        for face in FACES:
            u, v, w, h = spots[(box, face)]
            faces[face] = {'uv': [u * scale, v * scale, (u + w) * scale, (v + h) * scale], 'texture': '#skin'}
        elements.append({'from': [x0, y0, z0], 'to': [x1, y1, z1], 'faces': faces})
    model = {'parent': 'minecraft:block/block', 'ambientocclusion': False,
             'textures': {'particle': 'frozendawn:block/cs1_husk', 'skin': 'frozendawn:block/cs1_husk'}, 'elements': elements}
    (MODELS / 'cs1_husk.json').write_text(json.dumps(model, separators=(',', ':')) + '\n')
    out = {'parent': 'frozendawn:block/cs1_husk', 'textures': {'particle': 'frozendawn:block/cs1_husk_out', 'skin': 'frozendawn:block/cs1_husk_out'}}
    (MODELS / 'cs1_husk_out.json').write_text(json.dumps(out, indent=2) + '\n')


def nine_lamp():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(ICON.split()):
        for x, ch in enumerate(row):
            if ch != '.':
                img.putpixel((x, y), C[ICON_COLORS[ch]] + (255,))
    img.save(TEXTURES / 'item/nine_lamp.png')


def main():
    unit()
    husk()
    nine_lamp()
    flat('cs1_dock_top', 'steel')
    flat('cs1_dock_rail', 'steel2')
    side()
    front('found', 'led_red', False)
    front('seated', 'led_red', True)
    front('boot', 'led_green', True)
    front('empty', 'led_green', False)
    front('dark', 'led_off', False)


if __name__ == '__main__':
    main()

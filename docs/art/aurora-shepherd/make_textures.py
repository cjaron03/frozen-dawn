"""The game textures for Aurora and her dock, drawn from the same voxels and colors as the approved mockups.

Writes into src/main/resources. Run from anywhere: python3 docs/art/aurora-shepherd/make_textures.py
"""
from pathlib import Path
from PIL import Image
from make_body_directions import C, lantern
from make_flame_and_faces_mockup import flame
import make_dock_mockup  # noqa: F401, adds the dock's colors to C

HERE = Path(__file__).resolve().parent
TEXTURES = HERE.parents[2] / 'src/main/resources/assets/frozendawn/textures'
BLOCK = TEXTURES / 'block'
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
    (0, 34, 4, -1, 3, 8, 0, 7),       # flame, from the grille down; pilot blue, the owner's pick
    (16, 34, 4, -2, 4, 8, -1, 6),
    (28, 34, 5, -2, 3, 7, -1, 7),
    (40, 34, 5, -3, 4, 7, -2, 6),
    (48, 34, 5, -4, 4, 6, -3, 5),
]


def unit():
    """Her skin, on Minecraft's unfolded cube layout. The display stays dark: the face is drawn over it, full bright."""
    body = lantern()
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


def main():
    unit()
    flat('cs1_dock_top', 'steel')
    flat('cs1_dock_rail', 'steel2')
    side()
    front('found', 'led_red', False)
    front('boot', 'led_green', True)
    front('empty', 'led_green', False)
    front('dark', 'led_off', False)


if __name__ == '__main__':
    main()

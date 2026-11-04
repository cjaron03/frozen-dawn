"""The game textures for Aurora and her dock, drawn from the same voxels and colors as the approved mockups.

Writes into src/main/resources. Run from anywhere: python3 docs/art/aurora-shepherd/make_textures.py
"""
from pathlib import Path
from PIL import Image
from make_body_directions import C
import make_dock_mockup  # noqa: F401, adds the dock's colors to C

HERE = Path(__file__).resolve().parent
BLOCK = HERE.parents[2] / 'src/main/resources/assets/frozendawn/textures/block'
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


def main():
    flat('cs1_dock_top', 'steel')
    flat('cs1_dock_rail', 'steel2')
    side()
    front('found', 'led_red', False)
    front('boot', 'led_green', True)
    front('empty', 'led_green', False)
    front('dark', 'led_off', False)


if __name__ == '__main__':
    main()

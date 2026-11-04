"""Three body directions for Aurora, ORSA CS-1 "Shepherd", unit four, for the owner to pick from (2026-11-04).

Everything is built from one-texel voxels at Minecraft scale, 16 texels to the block, and shown beside a player for size.
Each body carries the spec's parts: a front display with the two-dot face, a lamp and a heater. Nothing here is in the
game yet. Run from anywhere: python3 docs/art/aurora-shepherd/make_body_directions.py
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
OUT = HERE / 'body-directions.png'

C = {k: tuple(bytes.fromhex(v)) for k, v in dict(
    shell='cfd2cc', shell2='aeb2ad', scuff='8c918e', dark='3a4046', steel='5d666d', steel2='79838a',
    screen='0b1014', glow='7df2c0', glow2='3aa984', lamp='f6f4d8', lamp2='c9c58f',
    heat='ff8a3c', heat2='b4501e', stripe='e0a526', stripe2='24272b', frost='e9f3f6',
    bg='20252b', panel='2a3037', text='c6ccd0', dim='8c969c', grid='3a4149', player='59626a', player2='454c53').items()}
FONT = '/System/Library/Fonts/Menlo.ttc'


def font(size):
    try:
        return ImageFont.truetype(FONT, size)
    except OSError:
        return ImageFont.load_default(size)


class Body:
    """A dict of voxels. x runs left to right as you face her, y up, z toward you: the display is on the +z side."""

    def __init__(self):
        self.v = {}

    def box(self, x0, y0, z0, x1, y1, z1, color):
        for x in range(x0, x1):
            for y in range(y0, y1):
                for z in range(z0, z1):
                    self.v[(x, y, z)] = color(x, y, z) if callable(color) else color

    def cut(self, cells):
        for cell in cells:
            self.v.pop(cell, None)

    def face(self, left, bottom, z):
        """The neutral face on a display whose lower left texel is (left, bottom)."""
        for dx, dy in ((1, 3), (2, 3), (1, 4), (2, 4), (5, 3), (6, 3), (5, 4), (6, 4)):
            self.v[(left + dx, bottom + dy, z)] = 'glow'
        for dx in (2, 3, 4, 5):
            self.v[(left + dx, bottom + 1, z)] = 'glow2'

    def bounds(self):
        xs, ys, zs = zip(*self.v)
        return min(xs), max(xs) + 1, min(ys), max(ys) + 1, min(zs), max(zs) + 1


def shell(x, y, z):
    h = (x * 73856093 ^ y * 19349663 ^ z * 83492791) % 23
    return 'scuff' if h == 0 else 'shell2' if h < 5 else 'shell'


def lantern():
    b = Body()
    b.box(0, 2, 0, 12, 12, 10, shell)
    b.box(0, 2, 0, 12, 3, 10, 'steel')
    b.box(0, 11, 0, 12, 12, 10, 'shell2')
    b.box(2, 4, 9, 10, 10, 10, 'screen')
    b.face(2, 4, 9)
    b.box(3, 11, 9, 9, 12, 10, 'lamp')
    b.box(2, 12, 4, 3, 15, 5, 'steel')
    b.box(9, 12, 4, 10, 15, 5, 'steel')
    b.box(2, 15, 4, 10, 16, 5, 'steel2')
    b.box(3, 0, 2, 9, 2, 8, lambda x, y, z: 'heat' if (x + z) % 2 == 0 and y == 0 else 'heat2')
    b.box(-3, 5, 2, 0, 9, 8, 'dark')
    b.box(12, 5, 2, 15, 9, 8, 'dark')
    b.box(12, 3, 5, 15, 4, 6, 'stripe')
    b.box(-3, 3, 5, 0, 4, 6, 'stripe')
    return b


def beacon():
    b = Body()

    def col(x, y, z):
        if y in (1, 3):
            return 'heat' if (x + z) % 2 else 'heat2'
        return 'dark' if y < 5 else shell(x, y, z)
    b.box(1, 0, 1, 9, 16, 9, col)
    b.box(2, 0, 0, 8, 16, 10, col)
    b.box(0, 0, 2, 10, 16, 8, col)
    b.box(3, 16, 3, 7, 19, 7, 'lamp')
    b.box(2, 19, 2, 8, 20, 8, 'steel2')
    b.box(2, 15, 2, 8, 16, 8, 'steel')
    b.box(-2, 6, 2, -0, 8, 8, 'dark')
    b.box(10, 6, 2, 12, 8, 8, 'dark')
    b.box(2, -2, 2, 8, 0, 8, 'dark')
    for x in range(-1, 11):
        b.v[(x, 6, 0)] = 'stripe' if x % 2 else 'stripe2'
        b.v[(x, 6, 9)] = 'stripe' if x % 2 else 'stripe2'
    b.box(1, 8, 9, 9, 14, 10, 'screen')
    b.face(1, 8, 9)
    return b


def stray():
    b = Body()
    b.box(5, 2, 4, 13, 8, 12, shell)
    b.box(5, 7, 4, 13, 8, 12, 'shell2')
    b.box(5, 2, 11, 13, 8, 12, 'screen')
    b.face(5, 2, 11)
    b.box(7, 0, 6, 11, 2, 10, lambda x, y, z: 'lamp' if y == 0 else 'lamp2')
    b.box(6, 8, 6, 12, 9, 10, lambda x, y, z: 'heat' if x % 2 == 0 else 'heat2')
    for x0, z0 in ((-3, -1), (16, -1), (-3, 8), (16, 8)):
        b.box(x0, 4, z0, x0 + 5, 6, z0 + 5, 'dark')
        b.box(x0 + 1, 4, z0 + 1, x0 + 4, 6, z0 + 4, 'steel')
        b.box(x0 + 2, 6, z0 + 2, x0 + 3, 7, z0 + 3, 'steel2')
    b.box(-1, 4, 1, 19, 5, 2, 'steel')
    b.box(-1, 4, 9, 19, 5, 10, 'steel')
    b.box(13, 5, 7, 14, 7, 8, 'stripe')
    return b


def shade(color, f):
    return tuple(max(0, min(255, int(c * f))) for c in C[color])


def lit(color, f):
    """The display and the emitters are not lit by the room, so they keep their colour on every side."""
    return C[color] if color in ('glow', 'glow2', 'lamp', 'heat') else shade(color, f)


def iso(draw, body, ox, oy, a):
    """Three quarter view from front right and above: +z faces lower left, +x faces lower right."""
    def p(x, y, z):
        return ox + (x - z) * a, oy + (x + z) * a // 2 - y * a

    for (x, y, z), color in sorted(body.v.items(), key=lambda i: i[0][0] + i[0][1] + i[0][2]):
        if (x, y + 1, z) not in body.v:
            draw.polygon([p(x, y + 1, z), p(x + 1, y + 1, z), p(x + 1, y + 1, z + 1), p(x, y + 1, z + 1)], fill=lit(color, 1.0))
        if (x, y, z + 1) not in body.v:
            draw.polygon([p(x, y + 1, z + 1), p(x + 1, y + 1, z + 1), p(x + 1, y, z + 1), p(x, y, z + 1)], fill=lit(color, .86))
        if (x + 1, y, z) not in body.v:
            draw.polygon([p(x + 1, y + 1, z), p(x + 1, y + 1, z + 1), p(x + 1, y, z + 1), p(x + 1, y, z)], fill=lit(color, .66))


def flat(draw, body, ox, base, s, view):
    """Orthographic front or side, standing its lowest texel on `base`."""
    x0, x1, y0, _, z0, z1 = body.bounds()
    near = {}
    for (x, y, z), color in body.v.items():
        u = x - x0 if view == 'front' else z1 - 1 - z
        depth = z if view == 'front' else x
        if (u, y) not in near or depth > near[(u, y)][0]:
            near[(u, y)] = (depth, color)
    for (u, y), (_, color) in near.items():
        px, py = ox + u * s, base - (y - y0 + 1) * s
        draw.rectangle([px, py, px + s - 1, py + s - 1], fill=lit(color, 1.0 if view == 'front' else .8))


def player(draw, ox, base, s):
    """A 32 texel player from the front."""
    for x0, y0, x1, y1, c in ((4, 0, 8, 12, 'player2'), (8, 0, 12, 12, 'player2'), (4, 12, 12, 24, 'player'),
                              (0, 12, 4, 24, 'player2'), (12, 12, 16, 24, 'player2'), (4, 24, 12, 32, 'player')):
        draw.rectangle([ox + x0 * s, base - y1 * s, ox + x1 * s - 1, base - y0 * s - 1], fill=C[c])


DIRECTIONS = [
    ('A  LANTERN', lantern(), 'A boxy hand lantern that lost its hand.', 'Display on the front, lamp strip over it, carry',
     'handle on top, heater under, a duct each side.'),
    ('B  BEACON', beacon(), 'A standing evacuation marker that flies.', 'Tall, lamp in the crown, heater coil in the',
     'lower half, a hazard band. You follow it.'),
    ('C  STRAY', stray(), 'A low four-duct drone, the most machine.', 'Display on a small pod, lamp under it, heater',
     'on top of the pod.'),
]


def main():
    w, h, s = 1500, 800, 5
    img = Image.new('RGB', (w, h), C['bg'])
    d = ImageDraw.Draw(img)
    d.text((24, 16), 'AURORA, ORSA CS-1 "SHEPHERD": BODY DIRECTIONS', font=font(22), fill=C['text'])
    d.text((24, 46), '16 texels to the block. Same face on all three. Grey lines are block edges; she hovers about one block off the ground.',
           font=font(14), fill=C['dim'])
    col = w // 3
    for i, (name, body, *caption) in enumerate(DIRECTIONS):
        left = i * col + 12
        d.rectangle([left, 80, left + col - 24, h - 16], fill=C['panel'])
        d.text((left + 16, 92), name, font=font(20), fill=C['glow'])
        x0, x1, y0, y1, z0, z1 = body.bounds()
        a = 8
        sx0, sx1 = (x0 - z1) * a, (x1 - z0) * a
        sy0, sy1 = (x0 + z0) * a // 2 - y1 * a, (x1 + z1) * a // 2 - y0 * a
        iso(d, body, left + (col - 24) // 2 - (sx0 + sx1) // 2, 300 - (sy0 + sy1) // 2, a)

        base = h - 104
        px = left + 40
        for block in range(4):
            y = base - block * 16 * s
            d.line([left + 16, y, left + col - 40, y], fill=C['grid'])
        for block in range(4):
            x = px + block * 16 * s
            d.line([x, base - 48 * s, x, base], fill=C['grid'])
        player(d, px, base, s)
        hover = 16 * s
        flat(d, body, px + 18 * s, base - hover, s, 'front')
        flat(d, body, px + 18 * s + (x1 - x0 + 3) * s, base - hover, s, 'side')
        for n, line in enumerate(caption):
            d.text((left + 16, h - 92 + n * 20), line, font=font(14), fill=C['text'] if n == 0 else C['dim'])
    img.save(OUT)


if __name__ == '__main__':
    main()

"""Aurora's Returned hologram for M6, in the world, at Minecraft scale, for the owner's approval.

Built from one-texel voxels, 16 to the block, beside her Lantern body. The figures are half size, 16 texels tall, in her two greens.
The Architect takes its look from the manual's failed captures: a dark shape, clean eyes, horizontal tears and static.
No line of hers is drawn here. Nothing here is in the game yet.
Run from anywhere: python3 docs/art/aurora-shepherd/make_hologram_mockup.py
"""
from pathlib import Path
from PIL import Image, ImageDraw
from make_body_directions import C, flat, font, lantern
from make_face_and_talk_mockup import display

HERE = Path(__file__).resolve().parent
C['holo'] = (31, 92, 72)
C['holo2'] = (17, 48, 40)
FX, FZ, FY = -30, 5, 4  # The figure's feet: to her left, so she can look at it while she faces you.
FRAME_MS = 150
FRAMES = 24


def hash3(a, b, c):
    return (a * 73856093 ^ b * 19349663 ^ c * 83492791) & 0xFFFF


def stage(b, tick):
    """Thin rays from her side, and the ring the figure stands in, seen edge on from the front."""
    for ray, top in enumerate((1, 8, 15)):
        for x in range(-4, FX + 3, -1):
            t = (-4 - x) / (-4 - FX - 3)
            if (x + tick + ray) % 3:
                b.v[(x, round(7 + (FY + top - 7) * t), FZ)] = 'holo'
    for x in range(-8, 9):
        b.v[(FX + x, 2, FZ)] = 'glow' if (x + tick) % 6 == 0 else 'glow2'
    b.v[(-4, 7, FZ)] = 'glow'


def part(cells, x0, y0, z0, x1, y1, z1, color):
    for x in range(x0, x1):
        for y in range(y0, y1):
            for z in range(z0, z1):
                cells[(x, y, z)] = color


def humanoid(body='holo', chin=False):
    """A half size figure about its own middle, x across, y up from 0, z forward. Keys are texels, values colors."""
    cells = {}
    part(cells, -2, 0, -1, 0, 6, 1, body)
    part(cells, 0, 0, -1, 2, 6, 1, body)
    part(cells, -2, 6, -1, 2, 12, 1, body)
    part(cells, -2, 12, -2, 2, 16, 2, body)
    if chin:
        part(cells, -4, 6, -1, -2, 12, 1, body)
        part(cells, 2, 8, -1, 4, 12, 1, body)
        part(cells, 1, 10, 1, 3, 13, 3, body)
    else:
        part(cells, -4, 6, -1, -2, 12, 1, body)
        part(cells, 2, 6, -1, 4, 12, 1, body)
    return cells


def eyes(cells, look=0, color='glow'):
    for x in (-2 + look, 1 + look - 1):
        if -2 <= x < 2:
            cells[(x, 14, 1)] = color
    return cells


def scan(cells, tick, bright='glow2'):
    """Every third row gone dim and a brighter band running up the figure, so it reads as light, not a statue."""
    band = tick % 20
    out = {}
    for (x, y, z), color in cells.items():
        if color == 'glow':
            out[(x, y, z)] = color
        elif y == band:
            out[(x, y, z)] = bright
        elif y % 3 == 2:
            out[(x, y, z)] = 'holo'
        else:
            out[(x, y, z)] = color
    return out


def turned(cells, quarter):
    """A quarter turn about the figure's middle. The game turns the model smoothly; voxels only go in steps."""
    for _ in range(quarter % 4):
        cells = {(z, y, -x - 1): c for (x, y, z), c in cells.items()}
    return cells


def hunter(tick):
    return scan(turned(eyes(humanoid('glow2')), [0, 0, 1, 1, 2, 2, 3, 3][tick // 3 % 8]), tick, 'glow')


def mimic(tick):
    look = [0, 0, 1, 1, 1, 0, 0, -1, -1, -1, 0, 0][tick // 2 % 12]
    cells = scan(humanoid('holo'), tick, 'glow2')
    return {**cells, **eyes({}, look)}


def architect(tick):
    chin = 10 <= tick < 19
    cells = eyes(humanoid('holo2', chin=chin))
    out = {}
    tear = hash3(tick // 2, 1, 7) % 16
    width = 1 + hash3(tick, 2, 3) % 3
    shift = (hash3(tick // 2, 4, 9) % 9) - 4
    gone = hash3(tick // 3, 8, 8) % 12
    for (x, y, z), color in cells.items():
        if y in (gone, gone + 1) and color != 'glow':
            continue
        out[(x + shift if tear <= y < tear + width else x, y, z)] = color
    for i in range(3):
        y = hash3(tick, i, 5) % 18
        length = 4 + hash3(tick, i, 6) % 8
        left = -6 + hash3(i, tick // 2, 2) % 6
        for x in range(left, left + length):
            out[(x, y, 2)] = 'glow'
    for i in range(14):
        h = hash3(tick, i, 11)
        out[(h % 15 - 7, h // 16 % 19, (h // 256) % 5 - 2)] = 'glow2' if h % 3 else 'holo'
    return out


FIGURES = [('HUNTER', 'Clear, turning slowly. An ORSA file.', hunter),
           ('MIMIC', 'A shadow. Its head copies yours.', mimic),
           ('ARCHITECT', 'Tears, static, gaps. Stops to think, chin in hand.', architect)]


def scene(fn, tick):
    b = lantern()
    stage(b, tick)
    for (x, y, z), color in fn(tick).items():
        b.v[(FX + x, FY + y, FZ + z)] = color
    return b


def draw_scene(d, fn, tick, ox, base, s):
    flat(d, scene(fn, tick), ox, base, s, 'front')


def gif():
    w, h, s = 340, 270, 6
    frames = []
    for tick in range(FRAMES):
        img = Image.new('RGB', (w * 3, h), C['bg'])
        d = ImageDraw.Draw(img)
        for i, (name, _, fn) in enumerate(FIGURES):
            d.text((i * w + 16, 12), name, font=font(14), fill=C['glow'])
            draw_scene(d, fn, tick, i * w + 28, h - 24, s)
        frames.append(img)
    frames[0].save(HERE / 'hologram-mockup.gif', save_all=True, append_images=frames[1:], duration=FRAME_MS, loop=0)


def strip(d, x, y, w):
    """The talk panel, dropped to her strip over the hotbar. The words are a placeholder: no line is written yet."""
    d.rectangle([x, y, x + w, y + 46], fill=C['panel'], outline=C['glow2'])
    display(d, [(1, 1, 'glow'), (2, 1, 'glow'), (1, 2, 'glow'), (2, 2, 'glow'), (5, 1, 'glow'), (6, 1, 'glow'), (5, 2, 'glow'), (6, 2, 'glow'),
                (2, 4, 'glow2'), (3, 4, 'glow2'), (4, 4, 'glow2'), (5, 4, 'glow2')], x + 8, y + 8, 5)
    d.text((x + 60, y + 15), '[ her line types here, the world stays clear ]', font=font(13), fill=C['dim'])


def sheet():
    img = Image.new('RGB', (1240, 1010), C['bg'])
    d = ImageDraw.Draw(img)
    d.text((24, 16), 'AURORA M6: THE RETURNED, PROJECTED IN THE WORLD', font=font(22), fill=C['text'])
    d.text((24, 46), 'Seen from the front. Half size figures, 16 texels tall, about two blocks to her left. Motion is in hologram-mockup.gif.',
           font=font(14), fill=C['dim'])
    for i, (name, note, fn) in enumerate(FIGURES):
        x = 24 + i * 405
        d.text((x, 88), name, font=font(16), fill=C['glow'])
        d.text((x, 110), note, font=font(12), fill=C['dim'])
        draw_scene(d, fn, 12 if fn is architect else 0, x, 330, 7)
    d.text((24, 360), 'THE ARCHITECT, FRAME BY FRAME', font=font(15), fill=C['glow'])
    d.text((24, 382), 'After the manual\'s failed captures: only the eyes stay clean. Frames 3 and 4 are the chin pose.', font=font(12), fill=C['dim'])
    for i, tick in enumerate((2, 6, 12, 16, 20, 23)):
        figure = scene(architect, tick)
        figure.v = {key: c for key, c in figure.v.items() if key[0] < -20 and key[1] > 2}
        flat(d, figure, 30 + i * 200, 580, 7, 'front')
    d.text((24, 600), 'WHAT YOU SEE', font=font(15), fill=C['glow'])
    d.text((24, 622), 'The veil lifts and the talk panel drops to her strip, so you can walk round it. Walk off and it stops.',
           font=font(12), fill=C['dim'])
    d.rectangle([24, 650, 1216, 986], fill=(120, 140, 154))
    d.rectangle([24, 880, 1216, 986], fill=C['frost'])
    draw_scene(d, hunter, 0, 300, 896, 12)
    strip(d, 310, 930, 620)
    img.save(HERE / 'hologram-mockup.png')


def you_and_her(tick):
    """What she's caught projecting: a small you, with your skin in her greens, and a small her beside you."""
    cells = {}
    for (x, y, z), color in humanoid('glow2').items():
        shirt = 6 <= y < 12
        cells[(x - 5, y, z)] = 'holo' if y < 6 and y % 2 else 'glow' if shirt and x in (-2, 1) else color
    for (x, y, z), color in eyes({}, 0).items():
        cells[(x - 5, y, z)] = color
    for x in range(1, 9):
        for y in range(1, 8):
            cells[(x, y, 0)] = 'glow2' if x in (1, 8) or y in (1, 7) else 'holo2'
    for x, y in ((3, 5), (6, 5), (4, 3), (5, 3)):
        cells[(x, y, 1)] = 'glow'
    return scan(cells, tick, 'glow')


def caught_sheet():
    img = Image.new('RGB', (640, 300), C['bg'])
    d = ImageDraw.Draw(img)
    d.text((24, 16), 'AURORA M6: WHAT SHE IS CAUGHT PROJECTING', font=font(18), fill=C['text'])
    d.text((24, 44), 'A small you in your own skin, tinted green, beside a small her.', font=font(12), fill=C['dim'])
    draw_scene(d, you_and_her, 0, 60, 280, 8)
    img.save(HERE / 'caught-projection-mockup.png')


def hooded():
    """The egg: a small hooded figure on one knee, facing +z, holding out a small light. Half size, like the rest."""
    cells = {}
    part(cells, -2, 0, -5, 0, 2, 1, 'holo')  # Left shin, flat behind it.
    part(cells, -2, 2, -1, 0, 6, 1, 'holo')  # Left thigh, down to the knee.
    part(cells, 0, 4, -1, 2, 6, 4, 'holo')  # Right thigh, forward.
    part(cells, 0, 0, 2, 2, 4, 4, 'holo')  # Right shin, foot down.
    part(cells, -3, 3, -2, 3, 6, 2, 'glow2')  # The cloak's hem.
    part(cells, -3, 6, -2, 3, 12, 2, 'glow2')  # The cloak.
    part(cells, -3, 8, 2, -1, 10, 5, 'glow2')  # Left arm, held out.
    part(cells, 1, 8, 2, 3, 10, 5, 'glow2')  # Right arm, held out.
    part(cells, -3, 12, -3, 3, 16, 2, 'glow2')  # The hood.
    part(cells, -2, 16, -2, 2, 17, 1, 'glow2')
    part(cells, -2, 12, 2, 2, 15, 3, 'holo2')  # The dark inside the hood. No face.
    part(cells, -1, 8, 5, 1, 10, 7, 'glow')  # What it holds out.
    return cells


def egg(tick):
    """Hunter, a tear, the hooded figure for a moment, a tear, Hunter."""
    if tick < 8 or tick >= 20:
        return hunter(tick)
    shape = scan(hooded(), tick, 'glow')
    if 10 <= tick < 18:
        return shape
    other = hunter(tick)
    return {key: c for key, c in {**other, **shape}.items() if (key[1] + tick) % 3 != 1 or c == 'glow'}


def figure_only(fn, tick):
    b = scene(fn, tick)
    b.v = {key: c for key, c in b.v.items() if key[0] < FX + 4 and key[1] > 2}
    return b


def egg_sheet():
    img = Image.new('RGB', (1000, 640), C['bg'])
    d = ImageDraw.Draw(img)
    d.text((24, 16), 'AURORA M6: THE EGG', font=font(22), fill=C['text'])
    d.text((24, 46), 'On the second projection, the figure flickers to this for a moment. Half size, on one knee, in her greens. Motion is in egg-mockup.gif.',
           font=font(12), fill=C['dim'])
    d.text((24, 88), 'IN THE WORLD, FROM THE FRONT', font=font(14), fill=C['glow'])
    draw_scene(d, lambda tick: scan(hooded(), tick, 'glow'), 0, 24, 320, 7)
    d.text((500, 88), 'THE FIGURE: FRONT, THEN SIDE', font=font(14), fill=C['glow'])
    still = lambda tick: scan(hooded(), tick, 'glow')
    flat(d, figure_only(still, 3), 500, 320, 10, 'front')
    flat(d, figure_only(still, 3), 730, 320, 10, 'side')
    d.text((24, 350), 'THE FLICKER, FRAME BY FRAME', font=font(14), fill=C['glow'])
    d.text((24, 372), 'Hunter, a tear, the figure, a tear, Hunter. Under two seconds in all at this speed.', font=font(12), fill=C['dim'])
    for i, tick in enumerate((6, 9, 12, 16, 19, 21)):
        flat(d, figure_only(egg, tick), 40 + i * 160, 600, 7, 'front')
    img.save(HERE / 'egg-mockup.png')
    w, h, s = 340, 270, 6
    frames = []
    for tick in range(FRAMES):
        frame = Image.new('RGB', (w, h), C['bg'])
        draw_scene(ImageDraw.Draw(frame), egg, tick, 28, h - 24, s)
        frames.append(frame)
    frames[0].save(HERE / 'egg-mockup.gif', save_all=True, append_images=frames[1:], duration=FRAME_MS, loop=0)


if __name__ == '__main__':
    gif()
    sheet()
    caught_sheet()
    egg_sheet()

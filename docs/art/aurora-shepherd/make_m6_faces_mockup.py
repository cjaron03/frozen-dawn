"""Miller Pattern M6 faces, for the owner's pick: a new face for ORSA-scripted answers, new expressions, and the moods already built.

Every face is 8 by 6 texels at 16 to the block, in her two greens: '#' lit, '+' dim, as ShepherdFace.Mood draws them.
Existing rows are copied from ShepherdFace.java on feat/miller-m5. Nothing new here is in the game yet.
Run from anywhere: python3 docs/art/aurora-shepherd/make_m6_faces_mockup.py
"""
import math
from pathlib import Path
from PIL import Image, ImageDraw
from make_body_directions import C, font, iso, lit
from make_face_and_talk_mockup import body_with, display

HERE = Path(__file__).resolve().parent
W, H = 8, 6
FRAME_MS = 200  # Four ticks a frame, the pace of her logo and her thinking dots.


def cells(rows):
    """Rows of '#', '+' and '.' as the texel list display() draws."""
    return [(x, y, 'glow' if c == '#' else 'glow2') for y, row in enumerate(rows) for x, c in enumerate(row) if c != '.']


def grid(test):
    return [''.join(test(x, y) for x in range(W)) for y in range(H)]


RECORDING_TODAY = ['........', '.++..++.', '.++..++.', '........', '........', '........']


def wave(heights):
    """A voice trace: thin bars round a line, the tallest tipped lit."""
    def at(x, y):
        half = heights[x] // 2
        if abs(y - 3) > half:
            return '.'
        return '#' if heights[x] == 5 and abs(y - 3) == 2 else '+'
    return grid(at)


WAVE = [wave(h) for h in ([1, 1, 3, 1, 1, 3, 1, 1], [1, 3, 1, 5, 1, 3, 1, 1], [1, 1, 3, 1, 5, 1, 3, 1],
                          [1, 3, 1, 3, 1, 5, 1, 1], [1, 1, 5, 1, 3, 1, 1, 1], [1, 3, 1, 1, 3, 1, 3, 1])]
WAVE_REST = wave([1] * W)

LOGO = ['...##...', '..####.+', '..##+++.', '.+++##..', '+..##...', '........']
LOGO_MOON = [(7, 1, '#'), (5, 2, '#'), (3, 3, '#'), (1, 3, '#'), (0, 4, '#'), (1, 1, '+'), (2, 0, '+'), (6, 0, '+')]


def mark(moon):
    """Her boot logo turned over: the planet goes dim and the orbit lit, so it can't be taken for her waking up."""
    swap = {'#': '+', '+': '#', '.': '.'}
    rows = [[swap[c] for c in row] for row in LOGO]
    if moon:
        x, y, c = moon
        rows[y][x] = '#' if c == '#' else '+'
    return [''.join(row) for row in rows]


MARK = [mark(m) for m in LOGO_MOON]
MARK_REST = mark(None)


def slats(ring):
    """A PA speaker's slats. On each frame a lit pulse runs out from the middle, as if the sound is pushing through."""
    def at(x, y):
        if y % 2 or y == 5 or y != 2 and x in (0, 7):
            return '.'
        return '#' if ring is not None and abs(abs(x - 3.5) - ring) < .6 else '+'
    return grid(at)


SLATS = [slats(r) for r in (.5, 1.5, 2.5, 3.5, None, None)]
SLATS_REST = slats(None)

LETTERS = {'O': ['###', '#.#', '#.#', '#.#', '###'], 'R': ['##.', '#.#', '##.', '#.#', '#.#'],
           'S': ['###', '#..', '###', '..#', '###'], 'A': ['###', '#.#', '###', '#.#', '#.#']}
STRIP = [''.join(LETTERS[c][y].replace('#', '+') + '.' for c in 'ORSA') + '....' for y in range(5)] + ['.' * 20]


def ticker(at):
    return [(row * 2)[at:at + W] for row in STRIP]


TICKER = [ticker(i) for i in range(len(STRIP[0]))]
TICKER_REST = ticker(0)

CANDIDATES = [
    ('A', 'Voice trace', 'A flat line at rest. It moves only while the recording plays.', WAVE_REST, WAVE),
    ('B', 'ORSA mark', 'Her boot logo, turned over. The company wears her display.', MARK_REST, MARK),
    ('C', 'PA slats', 'Her display becomes a speaker. Sound pushes out through it.', SLATS_REST, SLATS),
    ('D', 'Ticker', 'ORSA scrolls by like a station sign. Never still.', TICKER_REST, TICKER),
]

NEW = [
    ('Beaming', 'Close greeting', ['........', '.#....#.', '#.#..#.#', '........', '.++++++.', '..++++..']),
    ('Flustered', 'Dont make it weird', ['........', '.#....#.', '..#..#..', '.#....#.', '........', '...++...']),
    ('Grim', 'The mobs', ['#......#', '.##..##.', '.##..##.', '........', '..++++..', '........']),
    ('Appraising', 'Item advice', ['....###.', '.++.#.#.', '.##.###.', '........', '...++...', '........']),
    ('Side-eye', 'Spare', ['........', '.++..++.', '..#...#.', '........', '...++...', '........']),
]

MOODS = [
    ('Neutral', ['........', '.##..##.', '.##..##.', '........', '..++++..', '........']),
    ('Amused', ['........', '.##.....', '.##..##.', '......+.', '..++++..', '........']),
    ('Unimpressed', ['........', '........', '.##..##.', '........', '...++...', '........']),
    ('Happy', ['........', '.##..##.', '.##..##.', '.+....+.', '..++++..', '........']),
    ('Sad', ['........', '..#..#..', '.##..##.', '........', '...++...', '..+..+..']),
    ('Alarmed', ['.##..##.', '.##..##.', '.##..##.', '........', '..++++..', '..++++..']),
    ('Thinking', ['...##.##', '...##.##', '........', '........', '....++..', '........']),
]


def framed(d, rows, x, y, s):
    d.rectangle([x - 10, y - 10, x + W * s + 9, y + H * s + 9], fill=C['shell2'])
    display(d, cells(rows), x, y, s)


def orsa_sheet():
    img = Image.new('RGB', (1500, 1010), C['bg'])
    d = ImageDraw.Draw(img)
    d.text((24, 16), 'AURORA M6: THE ORSA FACE, FOUR CANDIDATES', font=font(22), fill=C['text'])
    d.text((24, 46), 'Replaces Recording. Two greens, 8 by 6 texels, 16 to the block. Motion is in orsa-face-mockup.gif.',
           font=font(14), fill=C['dim'])
    d.text((30, 88), 'TODAY', font=font(15), fill=C['glow'])
    framed(d, RECORDING_TODAY, 40, 122, 12)
    d.text((30, 208), 'Recording', font=font(13), fill=C['dim'])
    for i, (key, name, note, rest, frames) in enumerate(CANDIDATES):
        y = 250 + i * 182
        d.text((30, y), f'{key}  {name}', font=font(17), fill=C['glow'])
        d.text((30, y + 24), note, font=font(13), fill=C['dim'])
        framed(d, rest, 40, y + 62, 12)
        d.text((30, y + 146), 'At rest', font=font(12), fill=C['dim'])
        step = max(1, len(frames) // 6)
        for j, rows in enumerate(frames[::step][:6]):
            display(d, cells(rows), 180 + j * 84, y + 62, 9)
        d.text((180, y + 122), 'While it plays', font=font(12), fill=C['dim'])
        for j, rows in enumerate((rest, frames[len(frames) // 2])):
            display(d, cells(rows), 700 + j * 50, y + 74, 5)
        d.text((700, y + 122), 'Play size', font=font(12), fill=C['dim'])
        iso(d, body_with(cells(frames[len(frames) // 2])), 1000 + (i % 2) * 300, 200 + (i // 2) * 400, 8)
        d.text((920 + (i % 2) * 300, 335 + (i // 2) * 400), f'{key}  {name}', font=font(13), fill=C['dim'])
    img.save(HERE / 'orsa-face-mockup.png')


def orsa_gif():
    s, pad, top = 16, 28, 44
    width = pad + len(CANDIDATES) * (W * s + pad)
    frames = []
    for tick in range(60):
        img = Image.new('RGB', (width, top + H * s + 58), C['bg'])
        d = ImageDraw.Draw(img)
        d.text((pad, 12), 'WHILE THE RECORDING PLAYS', font=font(14), fill=C['glow'])
        for i, (key, name, _, _, seq) in enumerate(CANDIDATES):
            x = pad + i * (W * s + pad)
            display(d, cells(seq[tick % len(seq)]), x, top, s)
            d.text((x, top + H * s + 12), f'{key}  {name}', font=font(14), fill=C['text'])
        frames.append(img)
    frames[0].save(HERE / 'orsa-face-mockup.gif', save_all=True, append_images=frames[1:], duration=FRAME_MS, loop=0)


def expressions_sheet():
    img = Image.new('RGB', (1500, 900), C['bg'])
    d = ImageDraw.Draw(img)
    d.text((24, 16), 'AURORA M6: NEW EXPRESSIONS, AND THE MOODS SHE ALREADY HAS', font=font(22), fill=C['text'])
    d.text((24, 46), 'Pick two or three of the new ones. 8 by 6 texels, 16 to the block.', font=font(14), fill=C['dim'])
    d.text((30, 88), 'NEW', font=font(15), fill=C['glow'])
    s = 16
    for i, (name, use, rows) in enumerate(NEW):
        x = 40 + i * 180
        framed(d, rows, x, 126, s)
        d.text((x - 10, 240), name, font=font(15), fill=C['text'])
        d.text((x - 10, 262), use, font=font(12), fill=C['dim'])
        display(d, cells(rows), x, 290, 5)
    d.text((940, 298), 'Play size, at left.', font=font(12), fill=C['dim'])
    for i, (name, _, rows) in enumerate(NEW[:4]):
        ox = 150 + i * 300
        iso(d, body_with(cells(rows)), ox, 450, 7)
        d.text((ox - 90, 560), name, font=font(13), fill=C['dim'])
    d.text((30, 610), 'ALREADY BUILT, AS THE CODE DRAWS THEM', font=font(15), fill=C['glow'])
    s = 12
    for i, (name, rows) in enumerate(MOODS):
        x = 40 + i * 140
        framed(d, rows, x, 650, s)
        d.text((x - 10, 734), name, font=font(13), fill=C['text'])
        display(d, cells(rows), x, 760, 5)
    d.text((30, 806), 'Play size, above.', font=font(12), fill=C['dim'])
    img.save(HERE / 'new-expressions-mockup.png')


LEVEL = {'.': 0, '+': 1, '#': 2}
GLYPH = '.+#'
NEUTRAL = MOODS[0][1]
FACES = {name: rows for name, _, rows in NEW}


def texels(rows):
    return {(x, y): LEVEL[c] for y, row in enumerate(rows) for x, c in enumerate(row) if c != '.'}


def picture(levels):
    return [''.join(GLYPH[levels.get((x, y), 0)] for x in range(W)) for y in range(H)]


def today(a, b):
    """The beat as built: the new face at once, with her eyes shut for three ticks."""
    shut = [''.join('.' if c == '#' and y + 1 < H and b[y + 1][x] == '#' else c for x, c in enumerate(row)) for y, row in enumerate(b)]
    return [shut, shut]


def fade(a, b):
    """Every texel takes one step a frame towards the new face: lit, dim, dark, and back up."""
    now, goal, out = texels(a), texels(b), []
    while now != goal:
        step = {}
        for key in set(now) | set(goal):
            have, want = now.get(key, 0), goal.get(key, 0)
            level = have + (want > have) - (want < have)
            if level:
                step[key] = level
        now = step
        if now != goal:
            out.append(picture(now))
    return out


def flow(a, b):
    """Each texel slides to the nearest place in the new face, one texel a frame; any left over fade."""
    src, dst = texels(a), texels(b)
    pairs = sorted((max(abs(p[0] - q[0]), abs(p[1] - q[1])), p, q) for p in src for q in dst)
    moves, used = {}, set()
    for _, p, q in pairs:
        if p not in moves and q not in used:
            moves[p] = q
            used.add(q)
    walkers = [[p, moves[p], src[p]] for p in moves]
    leaving = {p: v for p, v in src.items() if p not in moves}
    arriving = {q: 0 for q in dst if q not in used}
    out = []
    for _ in range(W):
        done = True
        levels = {}
        for w in walkers:
            (x, y), (tx, ty) = w[0], w[1]
            w[0] = (x + (tx > x) - (tx < x), y + (ty > y) - (ty < y))
            w[2] += (dst[w[1]] > w[2]) - (dst[w[1]] < w[2])
            done &= w[0] == w[1] and w[2] == dst[w[1]]
            levels[w[0]] = max(levels.get(w[0], 0), w[2])
        for key in list(leaving):
            leaving[key] -= 1
            if leaving[key]:
                levels[key] = max(levels.get(key, 0), leaving[key])
                done = False
            else:
                del leaving[key]
        for key in arriving:
            arriving[key] = min(arriving[key] + 1, dst[key])
            done &= arriving[key] == dst[key]
            levels[key] = max(levels.get(key, 0), arriving[key])
        if done:
            break
        out.append(picture(levels))
    return out


METHODS = [('Today: blink', today), ('Fade', fade), ('Flow', flow)]
CHANGES = [('Beaming', NEUTRAL, FACES['Beaming'], None), ('Flustered', NEUTRAL, FACES['Flustered'], None),
           ('Grim', NEUTRAL, FACES['Grim'], None), ('Voice trace', NEUTRAL, WAVE_REST, WAVE)]
TRANSITION_MS = 100  # Two ticks a frame.


def loop(a, b, method, during, length):
    """One there-and-back, padded to length so every cell of the GIF stays in step."""
    there, back = method(a, b), method(b, a)
    held = (length - len(there) - len(back)) // 2
    second = [during[i % len(during)] if during and 3 <= i < held - 3 else b for i in range(held)]
    return [a] * held + there + second + back + [a] * (length - 2 * held - len(there) - len(back))


def transitions_gif():
    s, pad, top, label = 12, 30, 64, 130
    width = label + len(METHODS) * (W * s + pad) + pad
    height = top + len(CHANGES) * (H * s + 26) + 10
    length = 64
    cols = [[loop(a, b, m, during, length) for _, m in METHODS] for _, a, b, during in CHANGES]
    frames = []
    for t in range(length):
        img = Image.new('RGB', (width, height), C['bg'])
        d = ImageDraw.Draw(img)
        d.text((24, 12), 'FROM NEUTRAL AND BACK', font=font(14), fill=C['glow'])
        for j, (name, _) in enumerate(METHODS):
            d.text((label + j * (W * s + pad), 40), name, font=font(13), fill=C['text'])
        for i, (name, *_) in enumerate(CHANGES):
            y = top + i * (H * s + 26)
            d.text((24, y + 26), name, font=font(13), fill=C['dim'])
            for j in range(len(METHODS)):
                display(d, cells(cols[i][j][t]), label + j * (W * s + pad), y, s)
        frames.append(img)
    frames[0].save(HERE / 'face-transitions-mockup.gif', save_all=True, append_images=frames[1:], duration=TRANSITION_MS, loop=0)


def transitions_sheet():
    img = Image.new('RGB', (1500, 960), C['bg'])
    d = ImageDraw.Draw(img)
    d.text((24, 16), 'AURORA M6: CHANGING FACE, THREE WAYS', font=font(22), fill=C['text'])
    d.text((24, 46), 'Each strip runs from the old face to the new one, two ticks a frame. Motion is in face-transitions-mockup.gif.',
           font=font(14), fill=C['dim'])
    s = 7
    for i, (name, a, b, _) in enumerate(CHANGES):
        y = 88 + i * 216
        d.text((30, y), f'NEUTRAL TO {name.upper()}', font=font(15), fill=C['glow'])
        for j, (method, fn) in enumerate(METHODS):
            row = y + 28 + j * 60
            d.text((30, row + 12), method, font=font(13), fill=C['dim'])
            strip = [a] + fn(a, b) + [b]
            for k, rows in enumerate(strip):
                display(d, cells(rows), 170 + k * 68, row, s)
            d.text((170 + len(strip) * 68, row + 12), f'{(len(strip) - 2) * 2} ticks' if fn is not today else '3 ticks', font=font(12), fill=C['dim'])
    img.save(HERE / 'face-transitions-mockup.png')


TALK_OPEN = ['........', '.##..##.', '.##..##.', '........', '..++++..', '...++...']
TALK_WIDE = ['........', '.##..##.', '.##..##.', '........', '..++++..', '..++++..']
LIDS = ['........', '.++..++.', '.##..##.', '........', '..++++..', '........']
SMALL = ['........', '.++..++.', '.##..##.', '........', '...++...', '........']
AWAY = ['........', '++..++..', '##..##..', '........', '...++...', '........']
# What each beat shows, how many frames it holds, and its name on the sheet.
WORRY = [(TALK_OPEN, 2, 'Talking'), (TALK_WIDE, 2, None), (NEUTRAL, 1, None), (TALK_OPEN, 2, None), (TALK_WIDE, 2, None),
         (NEUTRAL, 7, 'She stops'), (LIDS, 5, 'Her eyes dim'), (SMALL, 3, 'Her mouth shrinks'), (AWAY, 16, 'She looks at it'),
         (SMALL, 4, 'Back to you'), (LIDS, 6, None), (NEUTRAL, 8, 'She carries on')]


def worry_gif():
    s, pad = 20, 26
    frames = []
    for rows, hold, _ in WORRY:
        img = Image.new('RGB', (W * s + 2 * pad, H * s + 2 * pad), C['bg'])
        display(ImageDraw.Draw(img), cells(rows), pad, pad, s)
        frames += [img] * hold
    frames[0].save(HERE / 'worry-motion-mockup.gif', save_all=True, append_images=frames[1:], duration=TRANSITION_MS, loop=0)


def worry_sheet():
    beats = [(rows, name) for rows, _, name in WORRY if name]
    img = Image.new('RGB', (60 + len(beats) * 170, 250), C['bg'])
    d = ImageDraw.Draw(img)
    d.text((24, 16), 'AURORA M6: WORRY AS MOTION, NO NEW FACE', font=font(22), fill=C['text'])
    d.text((24, 46), 'Mid-line she stops, dims and looks at the hologram. About three seconds. Motion is in worry-motion-mockup.gif.',
           font=font(14), fill=C['dim'])
    for i, (rows, name) in enumerate(beats):
        x = 34 + i * 170
        display(d, cells(rows), x, 96, 16)
        d.text((x, 206), f'{i + 1}  {name}', font=font(13), fill=C['text'])
    img.save(HERE / 'worry-motion-mockup.png')


WINK = ['........', '.##.....', '.##.+++.', '........', '..++++..', '........']
FLUSTERED = FACES['Flustered']
SIDES = (((0, 1, 0), ((0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1))), ((0, 0, 1), ((0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1))),
         ((0, 0, -1), ((0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0))), ((1, 0, 0), ((1, 0, 0), (1, 0, 1), (1, 1, 1), (1, 1, 0))),
         ((-1, 0, 0), ((0, 0, 0), (0, 0, 1), (0, 1, 1), (0, 1, 0))))
TILT = .3


def spun(d, rows, ox, oy, a, yaw):
    """Her body turned by yaw degrees, seen from in front and a little above: 0 faces you, 180 shows you her back."""
    body = body_with(cells(rows))
    c, s = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cx, cz = 6, 5

    def turn(x, z):
        return (x - cx) * c + (z - cz) * s, -(x - cx) * s + (z - cz) * c

    quads = []
    for (x, y, z), color in body.v.items():
        for normal, corners in SIDES:
            nx, ny, nz = normal
            if (x + nx, y + ny, z + nz) in body.v:
                continue
            tx, tz = turn(nx + cx, nz + cz)
            if tz + ny * TILT <= 0:
                continue
            points = []
            depth = 0
            for px, py, pz in corners:
                rx, rz = turn(x + px, z + pz)
                points.append((ox + rx * a, oy - (y + py) * a + rz * a * TILT))
                depth += rz + (y + py) * TILT
            quads.append((depth, points, lit(color, 1 if ny else .62 + .3 * max(0, tz))))
    for _, points, color in sorted(quads, key=lambda q: q[0]):
        d.polygon(points, fill=color)


# Face, her turn in degrees, frames held, the sheet's name for the beat, and whether her line shows.
CAUGHT = [(NEUTRAL, 0, 8, None, False), (FLUSTERED, 0, 4, 'You catch her', False), (FLUSTERED, 45, 1, None, False), (FLUSTERED, 100, 1, None, False),
          (FLUSTERED, 150, 1, None, False), (FLUSTERED, 180, 14, 'She spins away', True), (NEUTRAL, 150, 1, None, False), (WINK, 105, 1, None, False),
          (WINK, 62, 9, 'She peeks', False), (WINK, 110, 1, None, False), (FLUSTERED, 180, 8, 'Still looking', False), (NEUTRAL, 120, 2, None, False),
          (NEUTRAL, 60, 2, None, False), (NEUTRAL, 0, 8, 'She carries on', False)]


def caught_line(d, x, y):
    d.rectangle([x, y, x + 214, y + 28], fill=C['panel'], outline=C['glow2'])
    d.text((x + 10, y + 7), "You didn't see that!", font=font(13), fill=C['text'])


def caught_mockups():
    w, h, a = 300, 250, 9
    frames = []
    for rows, yaw, hold, _, line in CAUGHT:
        img = Image.new('RGB', (w, h), C['bg'])
        d = ImageDraw.Draw(img)
        spun(d, rows, w // 2, 170, a, yaw)
        if line:
            caught_line(d, 42, 208)
        frames += [img] * hold
    frames[0].save(HERE / 'flustered-motion-mockup.gif', save_all=True, append_images=frames[1:], duration=TRANSITION_MS, loop=0)
    beats = [beat for beat in CAUGHT if beat[3]]
    img = Image.new('RGB', (60 + len(beats) * 236, 330), C['bg'])
    d = ImageDraw.Draw(img)
    d.text((24, 16), 'AURORA M6: CAUGHT', font=font(22), fill=C['text'])
    d.text((24, 46), 'No new face: Flustered, her Wink, and her whole body. Motion is in flustered-motion-mockup.gif.', font=font(14), fill=C['dim'])
    for i, (rows, yaw, _, name, line) in enumerate(beats):
        x = 30 + i * 236
        spun(d, rows, x + 104, 220, 8, yaw)
        d.text((x, 262), f'{i + 1}  {name}', font=font(14), fill=C['text'])
        if line:
            caught_line(d, x, 286)
    img.save(HERE / 'flustered-motion-mockup.png')


if __name__ == '__main__':
    orsa_sheet()
    orsa_gif()
    expressions_sheet()
    transitions_gif()
    transitions_sheet()
    worry_gif()
    worry_sheet()
    caught_mockups()

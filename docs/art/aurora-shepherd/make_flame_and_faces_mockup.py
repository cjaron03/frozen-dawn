"""Aurora's lift flame and a second pass at her faces, at Minecraft scale (16 texels to the block), approved 2026-11-04.

The flame sits under the heater grille of the Lantern body. Nothing here is in the game yet.
Run from anywhere: python3 docs/art/aurora-shepherd/make_flame_and_faces_mockup.py
"""
from pathlib import Path
from PIL import Image, ImageDraw
from make_body_directions import C, flat, font, lantern
from make_face_and_talk_mockup import MOODS, display

HERE = Path(__file__).resolve().parent
C.update(a1=(255, 138, 60), a2=(255, 193, 90), a3=(255, 240, 184),
         b1=(86, 160, 255), b2=(169, 220, 255), b3=(242, 251, 255),
         c1=(58, 169, 132), c2=(125, 242, 192), c3=(228, 255, 244))
OPTIONS = [('A  HEATER ORANGE', 'a'), ('B  PILOT BLUE', 'b'), ('C  AURORA GREEN', 'c')]


def face(*rows):
    return [(x, y, 'glow' if c == '#' else 'glow2') for y, row in enumerate(rows) for x, c in enumerate(row) if c in '#+']


NEW = [
    ('Neutral', face('........', '.##..##.', '.##..##.', '........', '..++++..', '........')),
    ('Amused', face('........', '.##.....', '.##..##.', '......+.', '..++++..', '........')),
    ('Unimpressed', face('........', '........', '.##..##.', '........', '...++...', '........')),
    ('Happy', face('........', '.##..##.', '.##..##.', '.+....+.', '..++++..', '........')),
    ('Sad', face('........', '..#..#..', '.##..##.', '........', '...++...', '..+..+..')),
    ('Alarmed', face('.##..##.', '.##..##.', '.##..##.', '........', '..++++..', '..++++..')),
    ('Thinking', face('...##.##', '...##.##', '........', '........', '....++..', '........')),
    ('Offline', []),
]
LOOK_LEFT = face('........', '##..##..', '##..##..', '........', '..++++..', '........')
LOOK_RIGHT = face('........', '..##..##', '..##..##', '........', '..++++..', '........')
SHUT = face('........', '........', '.##..##.', '........', '..++++..', '........')


def flame(body, kind, height):
    """A blocky cone under the grille, y below zero. Taller frames add a tip."""
    outer, mid, core = kind + '1', kind + '2', kind + '3'
    for x in range(4, 8):
        for z in range(3, 7):
            body.v[(x, -1, z)] = core if 5 <= x <= 6 and 4 <= z <= 5 else mid
    for x in range(4, 8):
        for z in range(3, 7):
            if (x in (4, 7)) != (z in (3, 6)) or (x in (5, 6) and z in (4, 5)):
                body.v[(x, -2, z)] = mid if x in (5, 6) and z in (4, 5) else outer
    for x in (5, 6):
        for z in (4, 5):
            if height >= 2:
                body.v[(x, -3, z)] = outer
    if height >= 3:
        body.v[(5, -4, 4)] = outer
    return body


def unit(face_cells, kind=None, height=2):
    body = lantern()
    for x in range(2, 10):
        for y in range(4, 10):
            body.v[(x, y, 9)] = 'screen'
    for x, y, color in face_cells:
        body.v[(2 + x, 9 - y, 9)] = color
    return flame(body, kind, height) if kind else body


def main():
    w, h = 1500, 1160
    img = Image.new('RGB', (w, h), C['bg'])
    d = ImageDraw.Draw(img)
    d.text((24, 16), 'AURORA: LIFT FLAME AND FACES, SECOND PASS', font=font(22), fill=C['text'])
    d.text((24, 46), 'Flame under the heater grille, in blocky texels. Faces stay on the same 8 by 6 display.', font=font(14), fill=C['dim'])

    d.rectangle([12, 76, 860, 450], fill=C['panel'])
    d.text((26, 86), 'FLAME: THE OWNER PICKED B', font=font(18), fill=C['glow'])
    d.text((26, 110), 'Front and side, at eye level. From above her body hides it, as in the game.', font=font(13), fill=C['dim'])
    for i, (name, kind) in enumerate(OPTIONS):
        ox = 30 + i * 275
        body = unit(NEW[0][1], kind)
        flat(d, body, ox, 370, 8, 'front')
        flat(d, body, ox + 160, 370, 8, 'side')
        d.text((ox, 400), name, font=font(14), fill=C['text'])

    d.rectangle([872, 76, w - 12, 450], fill=C['panel'])
    d.text((886, 86), 'IT FLICKERS, SPUTTERS, AND GOES OUT OFFLINE', font=font(18), fill=C['glow'])
    for i, height in enumerate((1, 2, 3)):
        flat(d, unit(NEW[0][1], 'b', height), 890 + i * 150, 270, 6, 'front')
        d.text((890 + i * 150, 280), 'frame %d' % (i + 1), font=font(12), fill=C['dim'])
    flat(d, unit([]), 890, 410, 4, 'front')
    d.text((890, 420), 'Offline', font=font(12), fill=C['dim'])
    flat(d, unit(NEW[0][1], 'b'), 1040, 410, 2, 'front')
    d.text((1040, 420), 'At play size', font=font(12), fill=C['dim'])

    d.rectangle([12, 462, w - 12, 846], fill=C['panel'])
    d.text((26, 472), 'FACES: NOW, THEN PROPOSED', font=font(18), fill=C['glow'])
    s = 14
    for i, ((name, old), (_, new)) in enumerate(zip(MOODS, NEW)):
        x = 30 + i * 180
        d.text((x, 502), name, font=font(13), fill=C['text'])
        display(d, old, x, 524, s)
        display(d, new, x, 524 + 6 * s + 14, s)
    d.text((30, 716), 'Top row is the first pass. Bottom row is what the owner approved on 2026-11-04: Unimpressed and Happy stay as they were;', font=font(13), fill=C['dim'])
    d.text((30, 736), 'Amused gets a crooked grin, Sad heavier eyes and a real frown, Alarmed a wide open mouth, Thinking a mouth pulled to one side.', font=font(13), fill=C['dim'])
    d.text((30, 770), 'At play size:', font=font(13), fill=C['dim'])
    for i, (_, new) in enumerate(NEW):
        display(d, new, 130 + i * 60, 766, 5)

    d.rectangle([12, 858, w - 12, h - 12], fill=C['panel'])
    d.text((26, 868), 'REACTIONS: SHE MOVES BETWEEN LINES', font=font(18), fill=C['glow'])
    beats = [('Eyes shut a beat', SHUT, 'Between two moods, so a change reads as a reaction'),
             ('Glances left', LOOK_LEFT, 'Eyes follow the reply you hover, left column'),
             ('Glances right', LOOK_RIGHT, 'And the right column')]
    for i, (name, cells, note) in enumerate(beats):
        x = 30 + i * 300
        note = note.split(', ')
        display(d, cells, x, 902, 12)
        d.text((x, 986), name, font=font(13), fill=C['text'])
        for n, part in enumerate(note):
            d.text((x, 1006 + n * 18), part, font=font(12), fill=C['dim'])
    d.text((930, 902), 'On her body too', font=font(13), fill=C['text'])
    d.text((930, 924), 'Her real display shows the same mood,', font=font(12), fill=C['dim'])
    d.text((930, 942), 'mouth and blink as the talk screen, so a', font=font(12), fill=C['dim'])
    d.text((930, 960), 'player watching from outside sees her talk.', font=font(12), fill=C['dim'])
    img.save(HERE / 'flame-and-faces-mockup.png')


if __name__ == '__main__':
    main()

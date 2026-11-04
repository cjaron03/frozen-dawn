"""Aurora's face and talk screen, on the Lantern body the owner picked (2026-11-04), for approval before anything is built.

The face lives on the Lantern's display, 8 by 6 texels at 16 to the block; the talk screen shows the same 48 texels large.
The talk screen is drawn at GUI scale: one pixel here is one GUI pixel, enlarged three times, in a bitmap font.
Nothing here is in the game yet. Run from anywhere: python3 docs/art/aurora-shepherd/make_face_and_talk_mockup.py
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from make_body_directions import C, font, iso, lantern

HERE = Path(__file__).resolve().parent
PIXEL = ImageFont.load_default_imagefont()
ON, DIM = 'glow', 'glow2'


def eyes(left, right):
    return [(x, y, ON) for x, y in left + right]


def rows(*cells, color=DIM):
    return [(x, y, color) for x, y in cells]


L2 = [(1, 1), (2, 1), (1, 2), (2, 2)]
R2 = [(5, 1), (6, 1), (5, 2), (6, 2)]
MOUTH = rows((2, 4), (3, 4), (4, 4), (5, 4))

# Every face is a list of lit texels on the 8 by 6 display, x left to right, y top to bottom.
MOODS = [
    ('Neutral', eyes(L2, R2) + MOUTH),
    ('Amused', eyes(L2, [(5, 2), (6, 2)]) + rows((2, 4), (3, 4), (4, 4), (5, 3))),
    ('Unimpressed', eyes([(1, 2), (2, 2)], [(5, 2), (6, 2)]) + rows((3, 4), (4, 4))),
    ('Happy', eyes(L2, R2) + rows((1, 3), (2, 4), (3, 4), (4, 4), (5, 4), (6, 3))),
    ('Sad', eyes([(2, 1), (1, 2)], [(5, 1), (6, 2)]) + rows((2, 4), (3, 4), (4, 4), (5, 4), (1, 5), (6, 5))),
    ('Alarmed', eyes(L2 + [(1, 0), (2, 0)], R2 + [(5, 0), (6, 0)]) + rows((3, 4), (4, 4), (3, 5), (4, 5))),
    ('Thinking', eyes([(3, 0), (4, 0), (3, 1), (4, 1)], [(6, 0), (7, 0), (6, 1), (7, 1)]) + rows((3, 4), (4, 4))),
    ('Offline', []),
]
TALK = [
    ('mouth shut', eyes(L2, R2) + MOUTH),
    ('mouth open', eyes(L2, R2) + rows((2, 4), (3, 4), (4, 4), (5, 4), (3, 5), (4, 5))),
    ('mouth wide', eyes(L2, R2) + rows((2, 4), (3, 4), (4, 4), (5, 4), (2, 5), (3, 5), (4, 5), (5, 5))),
    ('blink', eyes([(1, 2), (2, 2)], [(5, 2), (6, 2)]) + MOUTH),
    ('recording', [(x, y, DIM) for x, y in L2 + R2]),
]


def display(draw, face, ox, oy, s):
    draw.rectangle([ox, oy, ox + 8 * s - 1, oy + 6 * s - 1], fill=C['screen'])
    for x, y, color in face:
        draw.rectangle([ox + x * s, oy + y * s, ox + (x + 1) * s - 1, oy + (y + 1) * s - 1], fill=C[color])


def body_with(face):
    body = lantern()
    for x in range(2, 10):
        for y in range(4, 10):
            body.v[(x, y, 9)] = 'screen'
    for x, y, color in face:
        body.v[(2 + x, 9 - y, 9)] = color
    return body


def moods():
    w, h = 1500, 860
    img = Image.new('RGB', (w, h), C['bg'])
    d = ImageDraw.Draw(img)
    d.text((24, 16), 'AURORA: FACE ON THE LANTERN DISPLAY', font=font(22), fill=C['text'])
    d.text((24, 46), 'Display 8 by 6 texels. The same 48 texels show on her body and, large, on the talk screen.',
           font=font(14), fill=C['dim'])
    s = 16
    for i, (name, face) in enumerate(MOODS):
        x, y = 30 + (i % 4) * 190, 90 + (i // 4) * 170
        d.rectangle([x - 12, y - 12, x + 8 * s + 12, y + 6 * s + 12], fill=C['shell2'])
        display(d, face, x, y, s)
        d.text((x - 10, y + 6 * s + 18), name, font=font(15), fill=C['text'])
    d.text((30, 438), 'WHILE SHE SPEAKS', font=font(15), fill=C['glow'])
    for i, (name, face) in enumerate(TALK):
        x = 30 + i * 150
        display(d, face, x, 470, 12)
        d.text((x, 546), name, font=font(13), fill=C['dim'])
    d.text((30, 580), 'The mouth steps through these as the text types, and shuts on punctuation.', font=font(13), fill=C['dim'])
    d.text((30, 600), 'Recording voice: eyes dimmed and fixed, no mouth. It plays on its own, and at Wary tier she falls back on it.',
           font=font(13), fill=C['dim'])

    d.text((810, 90), 'IN THE WORLD, 3/4 VIEW', font=font(15), fill=C['glow'])
    for i, index in enumerate((0, 1, 4, 7)):
        name, face = MOODS[index]
        ox, oy = 950 + (i % 2) * 300, 240 + (i // 2) * 270
        iso(d, body_with(face), ox, oy, 8)
        d.text((ox - 100, oy + 130), name, font=font(13), fill=C['dim'])
    d.text((30, 650), 'SEEN FROM PLAY', font=font(15), fill=C['glow'])
    d.text((30, 676), 'At true size, one texel to a pixel, enlarged twice: what her mood looks like a few blocks away.',
           font=font(13), fill=C['dim'])
    for i, (name, face) in enumerate(MOODS):
        display(d, face, 30 + i * 60, 706, 5)
    img.save(HERE / 'face-moods-mockup.png')


def text(d, xy, value, fill):
    d.text(xy, value, font=PIXEL, fill=fill)


def wrap(value, width):
    out, line = [], ''
    for word in value.split():
        trial = (line + ' ' + word).strip()
        if PIXEL.getlength(trial) > width:
            out.append(line)
            line = word
        else:
            line = trial
    return out + [line]


def panel(d, box, fill=(14, 18, 22), edge=None):
    d.rectangle(box, fill=fill, outline=edge or C['steel'])


def talk():
    w, h = 480, 270
    img = Image.new('RGB', (w, h), (26, 28, 32))
    d = ImageDraw.Draw(img)
    for by in range(0, h, 16):
        for bx in range(0, w, 16):
            n = (bx * 31 + by * 17) % 7
            d.rectangle([bx, by, bx + 15, by + 15], fill=(44 + n, 46 + n, 50 + n))
            d.rectangle([bx, by, bx + 15, by], fill=(36 + n, 38 + n, 42 + n))
    d.rectangle([0, 214, w, h], fill=(32, 34, 38))
    for x in (60, 420):
        d.rectangle([x, 34, x + 12, 40], fill=(148, 44, 38))
    veil = Image.new('RGB', (w, h), (6, 8, 10))
    img = Image.blend(img, veil, .45)
    d = ImageDraw.Draw(img)

    face = MOODS[2][1]
    d.rectangle([194, 12, 285, 81], fill=C['shell2'], outline=C['scuff'])
    d.rectangle([202, 8, 277, 11], fill=C['lamp'])
    display(d, face, 200, 18, 10)

    panel(d, [84, 88, 395, 136])
    text(d, (92, 92), 'AURORA', C['glow'])
    line = "...That's a recording. It plays on its own. Nobody's coming. Hi."
    for i, row in enumerate(wrap(line, 292)):
        text(d, (92, 106 + i * 12), row, (225, 230, 228))
    d.rectangle([92 + int(PIXEL.getlength(wrap(line, 292)[-1])) + 2, 106 + (len(wrap(line, 292)) - 1) * 12, 97 + int(PIXEL.getlength(wrap(line, 292)[-1])), 115 + (len(wrap(line, 292)) - 1) * 12], fill=C['glow'])

    slots = [('1', 'ASK', 'Who are you?'), ('2', 'KIND', 'Are you okay?'),
             ('3', 'BLUNT', 'Can you be useful?'), ('4', 'SARCASTIC', 'A talking flashlight.')]
    for i, (key, tone, label) in enumerate(slots):
        x, y = 84 + (i % 2) * 158, 144 + (i // 2) * 28
        hover = i == 3
        panel(d, [x, y, x + 153, y + 23], fill=(30, 44, 42) if hover else (18, 22, 27), edge=C['glow'] if hover else C['steel'])
        text(d, (x + 5, y + 6), key, C['glow'])
        text(d, (x + 16, y + 1), tone, C['dim'])
        text(d, (x + 16, y + 11), label, (225, 230, 228))
    panel(d, [242, 202, 438, 232], fill=(10, 13, 16), edge=C['glow2'])
    text(d, (248, 205), 'You say:', C['dim'])
    text(d, (248, 218), '"Great. A talking flashlight."', (225, 230, 228))
    text(d, (84, 240), 'Keys 1-4 or click. Hover for the full line. Esc walks away.', C['dim'])
    img.resize((w * 3, h * 3), Image.NEAREST).save(HERE / 'talk-screen-mockup.png')


if __name__ == '__main__':
    moods()
    talk()

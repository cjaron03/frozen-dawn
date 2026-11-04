"""How Aurora's ambient lines, callouts and "Got a minute?" appear during play, 2026-11-04.

Drawn at GUI scale, one pixel a GUI pixel, enlarged three times, in a bitmap font standing in for the game's. The strip
borrows the colours of the ORSA HUD panel (`OrsaHudPanel`) and carries her face in place of the badge. It sits above the
action bar, because the temperature and air panels hold the top left and status effects the top right. Nothing here is in
the game yet. Run from anywhere: python3 docs/art/aurora-shepherd/make_ambient_lines_mockup.py
"""
from pathlib import Path
from PIL import Image, ImageDraw
from make_body_directions import C, font, iso
from make_face_and_talk_mockup import MOODS, PIXEL, TALK, body_with, display, wrap

HERE = Path(__file__).resolve().parent
PANEL = (11, 18, 23)
VALUE = (205, 239, 255)
MUTED = (111, 127, 137)
WARNING = (255, 176, 96)
FACES = dict(MOODS)

LINES = [
    ('Ambient line', FACES['Neutral'], C['glow2'], "Your oxygen is a number I'd like to be bigger.", VALUE, None),
    ('Callout', FACES['Alarmed'], WARNING, "Mimic. Keep your distance.", VALUE, None),
    ('She wants to talk', FACES['Thinking'], C['glow'], "Got a minute?", VALUE, 'Right-click her'),
    ('Wary: recording voice', TALK[4][1], MUTED, "Please remain calm.", MUTED, None),
]


def strip(d, cx, bottom, face, accent, line, color, hint):
    width = 214
    rows = wrap(line, width - 44)
    height = max(26, 8 + 11 * (len(rows) + (1 if hint else 0)))
    x, y = cx - width // 2, bottom - height
    d.rectangle([x, y, x + width - 1, y + height - 1], fill=PANEL)
    d.rectangle([x, y, x + 1, y + height - 1], fill=accent)
    display(d, face, x + 7, y + (height - 18) // 2, 3)
    for i, row in enumerate(rows):
        d.text((x + 38, y + 3 + i * 11), row, font=PIXEL, fill=color)
    if hint:
        d.text((x + 38, y + 3 + len(rows) * 11), hint, font=PIXEL, fill=MUTED)


def scene():
    w, h = 480, 270
    img = Image.new('RGB', (w, h), (10, 13, 22))
    d = ImageDraw.Draw(img)
    for i in range(120):
        d.point(((i * 97) % w, (i * 53) % 120), fill=(70, 78, 92))
    for by in range(150, h, 16):
        for bx in range(0, w, 16):
            n = (bx * 13 + by * 7) % 6
            d.rectangle([bx, by, bx + 15, by + 15], fill=(150 + n, 158 + n, 168 + n))
    for bx, top in ((0, 118), (16, 126), (32, 134), (400, 128), (416, 120), (432, 120), (448, 112), (464, 112)):
        d.rectangle([bx, top, bx + 15, 150], fill=(112, 120, 130))
    iso(d, body_with(FACES['Neutral']), 340, 112, 2)
    d.line([238, 135, 242, 135], fill=(230, 230, 230))
    d.line([240, 133, 240, 137], fill=(230, 230, 230))

    d.rectangle([4, 4, 96, 22], fill=PANEL)
    d.rectangle([4, 4, 5, 22], fill=C['heat'])
    d.text((12, 8), 'TEMP -31 C', font=PIXEL, fill=VALUE)
    d.rectangle([4, 25, 96, 43], fill=PANEL)
    d.rectangle([4, 25, 5, 43], fill=C['glow2'])
    d.text((12, 29), 'AIR 62%', font=PIXEL, fill=VALUE)

    d.rectangle([149, 248, 330, 269], fill=(40, 40, 44), outline=(16, 16, 18))
    for i in range(9):
        d.rectangle([151 + i * 20, 250, 168 + i * 20, 267], outline=(90, 90, 96))
    for i in range(10):
        d.rectangle([149 + i * 8, 236, 155 + i * 8, 242], fill=(190, 40, 40))
    return img


def main():
    img = scene()
    d = ImageDraw.Draw(img)
    strip(d, 240, 216, *LINES[0][1:])
    screen = img.resize((1440, 810), Image.NEAREST)

    sheet = Image.new('RGB', (1440, 1270), C['bg'])
    s = ImageDraw.Draw(sheet)
    s.text((24, 16), 'AURORA: LINES DURING PLAY', font=font(22), fill=C['text'])
    s.text((24, 46), 'GUI scale, enlarged three times. A strip above the action bar, in the ORSA HUD colours, her face where the badge goes.',
           font=font(14), fill=C['dim'])
    sheet.paste(screen, (0, 80))

    row = Image.new('RGB', (480, 62), C['panel'])
    r = ImageDraw.Draw(row)
    for i, (_, *args) in enumerate(LINES[:2]):
        strip(r, 120 + i * 240, 50, *args)
    sheet.paste(row.resize((1440, 186), Image.NEAREST), (0, 900))
    row = Image.new('RGB', (480, 62), C['panel'])
    r = ImageDraw.Draw(row)
    for i, (_, *args) in enumerate(LINES[2:]):
        strip(r, 120 + i * 240, 50, *args)
    sheet.paste(row.resize((1440, 186), Image.NEAREST), (0, 1076))
    for i, (name, *_) in enumerate(LINES):
        s.text((30 + (i % 2) * 720, 906 + (i // 2) * 176), name, font=font(14), fill=C['glow'])
    sheet.save(HERE / 'ambient-lines-mockup.png')


if __name__ == '__main__':
    main()

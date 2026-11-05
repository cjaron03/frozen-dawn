"""How Aurora asks for a minute during play: she floats up in front of the player, 2026-11-04.

Three first-person frames at GUI scale, enlarged twice, in the scene of `ambient-lines-mockup.png`. She is drawn at the
size her distance gives her: about a block wide at three blocks away. Run from anywhere:
python3 docs/art/aurora-shepherd/make_float_up_mockup.py
"""
from pathlib import Path
from PIL import Image, ImageDraw
from make_body_directions import C, font, iso
from make_ambient_lines_mockup import FACES, LINES, strip
from make_face_and_talk_mockup import body_with

HERE = Path(__file__).resolve().parent
FRAMES = [
    ('1. Following', 'At the edge of your view, a block off the ground. Nothing has changed yet.', 'Neutral', 430, 122, 1, False),
    ('2. Floating up', 'She drifts round to your front, rising to eye level, about a second and a half.', 'Thinking', 340, 100, 2, False),
    ('3. Asking', 'Just above the crosshair, turned to you, with the strip. She holds there until it fades.', 'Thinking', 262, 88, 3, True),
]


def frame(mood, x, y, scale, asking):
    w, h = 480, 270
    img = Image.new('RGB', (w, h), (10, 13, 22))
    d = ImageDraw.Draw(img)
    for i in range(120):
        d.point(((i * 97) % w, (i * 53) % 120), fill=(70, 78, 92))
    for by in range(150, h, 16):
        for bx in range(0, w, 16):
            n = (bx * 13 + by * 7) % 6
            d.rectangle([bx, by, bx + 15, by + 15], fill=(150 + n, 158 + n, 168 + n))
    iso(d, body_with(FACES[mood]), x, y, scale)
    d.line([238, 135, 242, 135], fill=(230, 230, 230))
    d.line([240, 133, 240, 137], fill=(230, 230, 230))
    d.rectangle([149, 248, 330, 269], fill=(40, 40, 44), outline=(16, 16, 18))
    for i in range(9):
        d.rectangle([151 + i * 20, 250, 168 + i * 20, 267], outline=(90, 90, 96))
    if asking:
        strip(d, 240, 216, *LINES[2][1:])
    return img.resize((960, 540), Image.NEAREST)


def main():
    sheet = Image.new('RGB', (960, 80 + 3 * 590), C['bg'])
    s = ImageDraw.Draw(sheet)
    s.text((24, 16), 'AURORA: FLOATING UP TO ASK', font=font(22), fill=C['text'])
    s.text((24, 46), 'First person, GUI scale, enlarged twice. Her body at the size her distance gives her.', font=font(14), fill=C['dim'])
    for i, (title, note, *args) in enumerate(FRAMES):
        y = 80 + i * 590
        s.text((24, y + 6), title, font=font(16), fill=C['text'])
        s.text((190, y + 9), note, font=font(13), fill=C['dim'])
        sheet.paste(frame(*args), (0, y + 40))
    sheet.save(HERE / 'float-up-mockup.png')


if __name__ == '__main__':
    main()

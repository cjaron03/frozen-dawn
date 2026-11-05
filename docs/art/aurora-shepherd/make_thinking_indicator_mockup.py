"""The thinking indicator on Aurora's 8 by 6 display, at Minecraft scale (16 texels to the block). The owner picked A, Orbit, 2026-11-04, with her eyes going back into place naturally when it finishes.

It shows whenever she takes a moment to reply. Nothing here is in the game yet.
Run from anywhere: python3 docs/art/aurora-shepherd/make_thinking_indicator_mockup.py
"""
from pathlib import Path
from PIL import Image, ImageDraw
from make_body_directions import C, font, iso
from make_face_and_talk_mockup import MOODS, TALK, body_with, display

HERE = Path(__file__).resolve().parent
ON, DIM = 'glow', 'glow2'


def dot(x, y, color):
    return [(x + i, y + j, color) for i in range(2) for j in range(2)]


def rows(*lines):
    return [(x, y, ON if c == '#' else DIM) for y, line in enumerate(lines) for x, c in enumerate(line) if c != '.']


# A: her two eyes go round the middle of the display, one lit and one dim, as the moon does on the logo.
SPOTS = [(1, 2), (1, 0), (3, 0), (5, 0), (5, 2), (5, 4), (3, 4), (1, 4)]
ORBIT = [dot(*SPOTS[i], ON) + dot(*SPOTS[(i + 4) % 8], DIM) for i in range(8)]
# B: her eyes stay where they are and take turns.
# The end of A: from a level frame the dots rise to where her eyes sit, then the dim one comes up.
RISE = dot(1, 1, DIM) + dot(5, 1, ON)
HOME = dot(1, 1, ON) + dot(5, 1, ON)
TURNS = [dot(1, 1, ON) + dot(5, 1, DIM), dot(1, 1, DIM) + dot(5, 1, ON)]
# C: her Thinking eyes, with dots counting along under them.
THINKING_EYES = ['...##.##', '...##.##', '........', '........']
DOTS = [rows(*THINKING_EYES, m, '........') for m in ('........', '.#......', '.#..#...', '.#..#..#')]

OPTIONS = [
    ('A  ORBIT (PICKED)', ORBIT, 'Her two dots circle, one lit, one dim, 4 ticks a frame. Same lit and dim as the moon on her logo.',
     'Like nothing else her face does, so it cannot be taken for a mood. Reads at play distance.'),
    ('B  TAKING TURNS', TURNS, 'Her eyes stay put and swap lit and dim, 6 ticks each.',
     'Quietest. From a few blocks away it can pass for a slow wink, or a fault.'),
    ('C  COUNTING DOTS', DOTS, 'Her Thinking eyes, with dots counting along the mouth row, 6 ticks each.',
     'Clearest meaning, but it shares its eyes with Thinking, which is her asking face and Hope at rest.'),
]


def blinked(face):
    """As ShepherdFace.lit draws a blink: each eye shuts down to its lowest row."""
    lit = {(x, y) for x, y, c in face if c == ON}
    return [(x, y, c) for x, y, c in face if c != ON or (x, y + 1) not in lit]


def main():
    w, h = 1500, 1420
    img = Image.new('RGB', (w, h), C['bg'])
    d = ImageDraw.Draw(img)
    d.text((24, 16), 'AURORA: THE THINKING INDICATOR', font=font(22), fill=C['glow'])
    d.text((24, 46), 'Shown on her display until the line starts, whenever she takes a moment. A delay is never dead air. 8 by 6 texels, her two greens.', font=font(14), fill=C['dim'])

    y = 84
    for name, frames, how, verdict in OPTIONS:
        d.rectangle([12, y, 1090, y + 212], fill=C['panel'])
        d.text((26, y + 10), name, font=font(16), fill=C['glow'])
        for i, frame in enumerate(frames):
            display(d, frame, 26 + i * 130, y + 40, 14)
        d.text((26, y + 136), 'At play size:', font=font(12), fill=C['dim'])
        for i, frame in enumerate(frames):
            display(d, frame, 130 + i * 56, y + 134, 5)
        d.text((26, y + 172), how, font=font(13), fill=C['text'])
        d.text((26, y + 190), verdict, font=font(13), fill=C['dim'])
        y += 224

    d.rectangle([1102, 84, w - 12, 756], fill=C['panel'])
    d.text((1116, 94), 'A ON HER BODY', font=font(16), fill=C['glow'])
    iso(d, body_with(ORBIT[1]), 1294, 280, 9)
    iso(d, body_with(ORBIT[3]), 1294, 580, 9)

    y = 768
    d.rectangle([12, y, w - 12, y + 160], fill=C['panel'])
    d.text((26, y + 10), 'BESIDE THE FACES IT MUST NOT BE MISTAKEN FOR, AT PLAY SIZE', font=font(16), fill=C['glow'])
    beside = [('Neutral', MOODS[0][1]), ('Thinking', MOODS[6][1]), ('Blink', TALK[3][1]), ('Recording', TALK[4][1]), ('Offline', MOODS[7][1]), ('A', ORBIT[1]), ('A', ORBIT[3]), ('B', TURNS[0]), ('C', DOTS[3])]
    for i, (name, face) in enumerate(beside):
        x = 26 + i * 150
        d.rectangle([x - 6, y + 42, x + 8 * 8 + 5, y + 6 * 8 + 53], fill=C['shell2'])
        display(d, face, x, y + 48, 8)
        d.text((x, y + 110), name, font=font(13), fill=C['text'])

    y = 940
    d.rectangle([12, y, w - 12, y + 288], fill=C['panel'])
    d.text((26, y + 10), 'HOW IT ENDS: HER EYES GO BACK INTO PLACE', font=font(16), fill=C['glow'])
    for row, (name, face) in enumerate([('Line in Neutral', MOODS[0][1]), ('Line in Sad', MOODS[4][1])]):
        steps = [('Still circling', ORBIT[3], '4 ticks'), ('Level, always', ORBIT[4], '4 ticks'), ('Rise a row', RISE, '4 ticks'),
                 ('Both lit: home', HOME, '4 ticks'), ('Blink', blinked(face), '3 ticks'), (name, face, 'She speaks')]
        for i, (label, frame, ticks) in enumerate(steps):
            x, top = 26 + i * 190, y + 40 + row * 122
            display(d, frame, x, top, 10)
            d.text((x, top + 66), label, font=font(13), fill=C['text'])
            d.text((x, top + 84), ticks, font=font(12), fill=C['dim'])
            if i < len(steps) - 1:
                d.text((x + 118, top + 18), '>', font=font(22), fill=C['dim'])
    d.text((1180, y + 44), 'The orbit always runs to a level', font=font(13), fill=C['text'])
    d.text((1180, y + 64), 'frame, so her dots are never', font=font(13), fill=C['text'])
    d.text((1180, y + 84), 'dragged across her face.', font=font(13), fill=C['text'])
    d.text((1180, y + 124), 'The blink is the one she already', font=font(13), fill=C['dim'])
    d.text((1180, y + 144), 'does on any change of mood, so', font=font(13), fill=C['dim'])
    d.text((1180, y + 164), 'it covers the step into any face.', font=font(13), fill=C['dim'])

    y = 1240
    d.rectangle([12, y, w - 12, y + 168], fill=C['panel'])
    d.text((26, y + 10), 'WHEN IT SHOWS', font=font(16), fill=C['glow'])
    notes = [
        'You pick a reply. If her coherence is low, the indicator runs on her body and in the talk screen face panel, then the line types.',
        'Coherence 0.7 and up: no delay. Under 0.7: half a lap and the settle, 28 ticks. Under 0.5: a whole lap, 44 ticks. Never at the Recording voice.',
        'The reply slots stay empty until she has spoken, as they do while she types.',
        'If the talk closes mid-thought she still finishes and settles. M4 uses the same indicator while she works out typed text.',
    ]
    for n, line in enumerate(notes):
        d.text((26, y + 40 + n * 28), line, font=font(14), fill=C['text'])
    img.save(HERE / 'thinking-indicator-mockup.png')


if __name__ == '__main__':
    main()

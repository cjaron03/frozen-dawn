"""How her private log shows feeling, for slice M3, at Minecraft scale. Approved 2026-11-05.

She cannot cry, so the form does it for her. Text inside the form only: no shaking, no effects, nothing that could pass for
the game failing or for her stop screen. GUI scale on the 480 by 270 grid, enlarged twice. Every note is a draft.
Nothing here is in the game yet. Run from anywhere: python3 docs/art/aurora-shepherd/make_log_emotion_mockup.py
"""
from pathlib import Path
from PIL import Image, ImageDraw
from make_body_directions import C, font
from make_boot_sequence_mockup import SCREEN, WHITE, backdrop
from make_face_and_talk_mockup import PIXEL, panel, text, wrap
from make_private_log_mockup import face, small

HERE = Path(__file__).resolve().parent
# What the form says for itself, in its own dim green, not her white.
MACHINE = ('LOOP', 'DETECTED.', 'Retrying.')
LIMIT = 'FIELD LIMIT REACHED.'
LABEL = 'Notes (optional):'


def note_rows(d, note):
    full = note.endswith(LIMIT)
    rows = wrap(f'{LABEL} {note.removesuffix(LIMIT).strip()}', 292)
    assert len(rows) <= (4 if full else 5), note
    if full:
        text(d, (92, 184), LIMIT, C['glow2'])
    for i, row in enumerate(rows):
        y = 136 + i * 12
        text(d, (92, y), row, WHITE)
        at = 0
        for word in row.split():
            if word in MACHINE:
                text(d, (92 + int(PIXEL.getlength(row[:at])), y), word, C['glow2'])
            at += len(word) + 1
    text(d, (92, 136), LABEL, C['glow2'])


def frame(day, pattern, status, note):
    img = backdrop()
    d = ImageDraw.Draw(img)
    face(d, 'Neutral')
    panel(d, [84, 88, 395, 196], fill=SCREEN, edge=C['steel'])
    text(d, (92, 94), f'CS-1 UNIT 4. DAILY STATUS. DAY {day}.', C['glow'])
    text(d, (92, 112), 'Pattern:', C['glow2'])
    if pattern:
        text(d, (146, 112), pattern, WHITE)
    text(d, (92, 124), 'Status:', C['glow2'])
    text(d, (146, 124), status, WHITE)
    note_rows(d, note)
    small(d, 84, 150, f'< Day {day - 1}')
    small(d, 242, 153, f'Day {day + 1} >')
    text(d, (84, 240), 'Left and right for other days. Esc goes back.', C['dim'])
    return img.resize((960, 540), Image.NEAREST)


FRAMES = [
    ('1  NORMAL', 'Dry, full stops, no exclamation mark. Most days, and every day at Product.',
     (31, '', 'nominal.', "I didn't like today. I'd like that in writing.")),
    ('2  HIGH', 'A strong feeling. The punctuation gets away from her. Status does not move.',
     (44, '', 'nominal.', "They came back! Day 44 and they came back! I'm logging that twice.")),
    ('3  PEAK: THE LOOP', 'She watched them stop. The note fills the form, and Status finally cracks, the only time that field changes.',
     (63, '', 'nominal?', 'They stopped. They stopped. LOOP DETECTED. Retrying. They stopped. LOOP DETECTED. Retrying. They walked back in. Why do they FIELD LIMIT REACHED.')),
    ('4  THE DAY AFTER', 'The next entry goes very quiet, and Status is back as if nothing happened.',
     (64, '', 'nominal.', 'Ran the heater all night. Nobody was cold.')),
    ('5  PEAK: JOY', 'The same break, the other way. Her nickname, Cargo, is earned at Friendly. Here they came back after days away.',
     (88, '', 'nominal?', 'CARGO CAME BACK! Cargo came back! Logged twice! I do not care! Logged twice! LOOP DETECTED. Retrying. Cargo came back! FIELD LIMIT REACHED.')),
    ('6  AFTER T5', 'Nothing about the signed field changes. She can still break under it.',
     (131, 'Sophie.', 'nominal.', "Cargo stopped again. Cargo came back again. Nobody else does that. Why does Cargo do that? What brings them back? It isn't me. I checked.")),
]


def main():
    w, h = 1992, 92 + 3 * 610 + 10
    img = Image.new('RGB', (w, h), C['bg'])
    d = ImageDraw.Draw(img)
    d.text((24, 16), 'AURORA: HOW THE LOG SHOWS FEELING (M3)', font=font(26), fill=C['glow'])
    d.text((24, 52), 'She cannot cry, so the form does it for her. Text inside the form only. Approved 2026-11-05. Every note is a draft.', font=font(15), fill=C['dim'])
    for i, (name, caption, args) in enumerate(FRAMES):
        x, y = 24 + (i % 2) * 984, 92 + (i // 2) * 610
        d.text((x, y), name, font=font(16), fill=C['glow'])
        d.text((x, y + 24), caption, font=font(13), fill=C['text'])
        img.paste(frame(*args), (x, y + 50))
    img.save(HERE / 'log-emotion-mockup.png')


if __name__ == '__main__':
    main()

"""Aurora's private log, slice M3, at Minecraft scale. Approved 2026-11-05, with Settings as a page.

Talk-screen frames are at GUI scale: one pixel here is one GUI pixel on the 480 by 270 grid, enlarged twice, in a bitmap
font, the same grid `ShepherdScreen` draws on. Every word not quoted from docs/aurora-miller-pattern.md is a draft.
Nothing here is in the game yet. Run from anywhere: python3 docs/art/aurora-shepherd/make_private_log_mockup.py
"""
from pathlib import Path
from PIL import Image, ImageDraw
from make_body_directions import C, font
from make_boot_sequence_mockup import SCREEN, WHITE, backdrop, line, shell, slot
from make_face_and_talk_mockup import display, panel, text, wrap
from make_flame_and_faces_mockup import NEW

HERE = Path(__file__).resolve().parent
FACE = dict(NEW)


def face(d, mood):
    shell(d)
    display(d, FACE[mood], 200, 18, 10)


def small(d, x, w, label, live=True, hover=False):
    """A button on the row under the grid, the size of the built Settings button."""
    panel(d, [x, 202, x + w - 1, 215], fill=(30, 44, 42) if hover else (18, 22, 27), edge=C['glow'] if hover else C['steel'])
    if label:
        text(d, (x + 5, 204), label, C['dim'] if live else C['grid'])


def empty(d, *slots):
    for i in slots:
        x, y = 84 + (i % 2) * 158, 144 + (i // 2) * 28
        panel(d, [x, y, x + 153, y + 23], fill=(18, 22, 27))


def entry(d, day, pattern, status, note):
    """One day's form, on the panel the BIOS uses, so the slots give way to it and her face stays above."""
    panel(d, [84, 88, 395, 196], fill=SCREEN, edge=C['steel'])
    text(d, (92, 94), f'CS-1 UNIT 4. DAILY STATUS. DAY {day}.', C['glow'])
    text(d, (92, 112), 'Pattern:', C['glow2'])
    if pattern:
        text(d, (146, 112), pattern, WHITE)
    text(d, (92, 124), 'Status:', C['glow2'])
    text(d, (146, 124), status, WHITE)
    label = 'Notes (optional):'
    for i, row in enumerate(wrap(f'{label} {note}', 292)):
        text(d, (92, 136 + i * 12), row, WHITE)
    text(d, (92, 136), label, C['glow2'])


def frame(kind):
    img = backdrop()
    d = ImageDraw.Draw(img)
    if kind == 'hub':
        face(d, 'Neutral')
        panel(d, [84, 88, 395, 136])
        text(d, (92, 92), 'AURORA', C['glow'])
        slot(d, 0, 'Talk')
        slot(d, 1, 'Wait here')
        slot(d, 2, 'Lamp off')
        empty(d, 3)
        small(d, 84, 150, 'Settings', hover=True)
        text(d, (84, 240), 'Keys 1-4 or click. Hover for the full line. Esc walks away.', C['dim'])
    elif kind == 'settings':
        face(d, 'Neutral')
        panel(d, [84, 88, 395, 136])
        text(d, (92, 92), 'SETTINGS', C['dim'])
        slot(d, 0, 'Language: mild')
        slot(d, 1, 'Unit diagnostics', hover=True)
        empty(d, 2, 3)
        text(d, (84, 240), 'Keys 1-2 or click. Esc goes back.', C['dim'])
    elif kind == 'told':
        face(d, 'Unimpressed')
        line(d, 'AURORA', 'You read my log.')
        empty(d, 0, 1, 2, 3)
    elif kind == 'told2':
        face(d, 'Thinking')
        line(d, 'AURORA', "That field's marked optional. That means private. I've decided.")
        empty(d, 0, 1, 2, 3)
        text(d, (84, 240), 'Click to continue.', C['dim'])
    else:
        face(d, 'Neutral')
        day, pattern, note, earlier, later = {
            'day41': (41, '', "They went back into the cold for a chest. I said nothing. I'd like it noted that I said nothing.", 'Day 40', ''),
            'knows': (58, '', 'Still optional.', 'Day 57', 'Day 59'),
            'sophie': (112, 'Sophie.', "They haven't said anything since the fight. Status: nominal.", 'Day 111', ''),
        }[kind]
        entry(d, day, pattern, 'nominal.', note)
        small(d, 84, 150, f'< {earlier}')
        small(d, 242, 153, f'{later} >' if later else '', live=bool(later))
        text(d, (84, 240), 'Left and right for other days. Esc goes back.', C['dim'])
    return img.resize((960, 540), Image.NEAREST)


FRAMES = [
    ('1  WHERE IT IS: THE HUB', 'hub', 'The Settings button (CHANGED). Today it is one toggle reading "Settings: language mild"; it has to become a door.'),
    ('2  SETTINGS (NEW)', 'settings', 'The same grid. Language moves here, and "Unit diagnostics" sits beside it. Nothing else points to it.'),
    ('3  THE LOG, BEFORE T5', 'day41', '"Pattern:" is a blank, not a redaction: no bar, no mark, nothing after the colon. Newest day first.'),
    ('4  FIRST READ ONLY: SHE KNOWS', 'told', 'Leaving the log the first time, back on the talk grid. Affinity falls once, by 5 (DRAFT number).'),
    ('5  ...AND THEN', 'told2', 'Her second line, then the hub. Every later read costs nothing and she says nothing.'),
    ('6  AFTER SHE KNOWS YOU READ', 'knows', 'She sometimes writes for you now. "Still optional." is a DRAFT note. Same form, no sign it changed.'),
    ('7  AFTER T5', 'sophie', 'The field is filled in, the same white as her other entries, and no one mentions it. Earlier days stay blank.'),
]


def main():
    w, h = 1992, 2530
    img = Image.new('RGB', (w, h), C['bg'])
    d = ImageDraw.Draw(img)
    d.text((24, 16), 'AURORA: HER PRIVATE LOG (M3)', font=font(26), fill=C['glow'])
    d.text((24, 52), 'GUI scale, the talk screen grid, enlarged twice. Approved 2026-11-05. Nothing here is built.', font=font(15), fill=C['dim'])
    for i, (name, kind, caption) in enumerate(FRAMES):
        x, y = 24 + (i % 2) * 984, 92 + (i // 2) * 610
        d.text((x, y), name, font=font(16), fill=C['glow'])
        d.text((x, y + 24), caption, font=font(13), fill=C['text'])
        img.paste(frame(kind), (x, y + 50))

    x, y = 1008, 92 + 3 * 610
    d.rectangle([x, y, x + 960, y + 340], fill=C['panel'])
    d.text((x + 16, y + 12), 'HOW IT BEHAVES', font=font(16), fill=C['glow'])
    notes = [
        'The form is the spec\'s: four rows, one entry a day, built from authored fragments.',
        'Labels in her dim green, what she wrote in white, the header in her bright green.',
        'It uses the BIOS panel, so a player who saw her boot knows this is her own machine talking.',
        '',
        'Her face stays above the form and rests as usual. She does not react until you leave it.',
        'The world keeps running. Everything that closes the talk screen closes the log.',
        'If that happens on a first read, the read still counts, and she says it at your next talk.',
        '',
        'Esc goes back one step here (log, Settings, hub), not away from her.',
        'Left and right, or the two buttons, change day. The last day has no "later" button.',
        '',
        '"Pattern:" is the one word about her shown before T5, the approved exception.',
        '"Miller" never appears, before or after.',
    ]
    for i, row in enumerate(notes):
        d.text((x + 16, y + 44 + i * 22), row, font=font(14), fill=C['text'])
    img.save(HERE / 'private-log-mockup.png')


if __name__ == '__main__':
    main()

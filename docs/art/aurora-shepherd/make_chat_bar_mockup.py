"""Aurora's chat bar, slice M4, at Minecraft scale, for the owner's approval. 2026-11-05.

GUI scale: one pixel here is one GUI pixel on the 480 by 270 grid `ShepherdScreen` draws on, enlarged twice, in a bitmap font.
The buttons stay the complete game: the bar is added under the four slots and nothing is taken away. Her lines are quoted from
docs/aurora-miller-pattern.md and docs/aurora-orsa-topics.md; the bar's own words are drafts. Nothing here is in the game yet.
Run from anywhere: python3 docs/art/aurora-shepherd/make_chat_bar_mockup.py
"""
from pathlib import Path
from PIL import Image, ImageDraw
from make_body_directions import C, font
from make_boot_sequence_mockup import WHITE, backdrop, line, shell, slot
from make_face_and_talk_mockup import PIXEL, display, panel, text
from make_flame_and_faces_mockup import NEW
from make_private_log_mockup import empty
from make_thinking_indicator_mockup import ORBIT

HERE = Path(__file__).resolve().parent
FACE = dict(NEW)
BAR_X, BAR_Y, BAR_W, BAR_H = 84, 202, 312, 14
HINT = 'Keys 1-4, T to type. Hover for the full line. Esc walks away.'
TYPING = 'Enter says it. Esc closes the bar. Slots still click.'
PLACEHOLDER = 'Say something...'
REPLIES = [('ASK', 'Who are you?'), ('KIND', 'Are you okay?'), ('BLUNT', 'Can you be useful?'), ('SARCASTIC', 'A talking flashlight.')]


def bar(d, value='', state='closed'):
    """closed, hover, open (it has the keys), sent (dim, waiting on her) or off (she cannot hear it)."""
    edge = {'hover': C['glow'], 'open': C['glow']}.get(state, C['steel'])
    panel(d, [BAR_X, BAR_Y, BAR_X + BAR_W - 1, BAR_Y + BAR_H - 1], fill=(10, 13, 16) if state == 'open' else (18, 22, 27), edge=edge)
    color = WHITE if state == 'open' else C['dim'] if value or state == 'hover' else C['steel2']
    if state == 'off':
        color = C['grid']
    text(d, (BAR_X + 5, BAR_Y + 1), value or PLACEHOLDER, color)
    if state == 'open':
        x = BAR_X + 6 + int(PIXEL.getlength(value))
        d.rectangle([x, BAR_Y + 3, x + 3, BAR_Y + 10], fill=C['glow'])


def face(d, cells):
    shell(d)
    display(d, cells, 200, 18, 10)


def blank_line(d):
    panel(d, [84, 88, 395, 136])
    text(d, (92, 92), 'AURORA', C['glow'])


def replies(d, hover=None):
    for i, (tone, label) in enumerate(REPLIES):
        slot(d, i, label, tone=tone, hover=i == hover)


def frame(kind):
    img = backdrop()
    d = ImageDraw.Draw(img)
    opening = "...That's a recording. It plays on its own. Nobody's coming. Hi."
    if kind == 'closed':
        face(d, FACE['Unimpressed'])
        line(d, 'AURORA', opening)
        replies(d)
        bar(d, state='hover')
        text(d, (84, 240), HINT, C['dim'])
    elif kind == 'hover':
        face(d, FACE['Unimpressed'])
        line(d, 'AURORA', opening)
        replies(d, hover=3)
        bar(d)
        panel(d, [242, 219, 438, 249], fill=(10, 13, 16), edge=C['glow2'])
        text(d, (248, 222), 'You say:', C['dim'])
        text(d, (248, 235), '"Great. A talking flashlight."', WHITE)
        text(d, (84, 254), HINT, C['dim'])
    elif kind == 'typing':
        face(d, FACE['Unimpressed'])
        line(d, 'AURORA', opening)
        replies(d)
        bar(d, 'whats orsa', 'open')
        text(d, (84, 240), TYPING, C['dim'])
    elif kind == 'thinking':
        face(d, ORBIT[1])
        blank_line(d)
        empty(d, 0, 1, 2, 3)
        bar(d, 'whats orsa', 'sent')
    elif kind == 'strong':
        face(d, FACE['Unimpressed'])
        line(d, 'AURORA', "That's the brochure. Glass building, lots of windows, a gift shop. You could buy a mug.")
        empty(d, 0, 1, 2, 3)
        bar(d)
        text(d, (84, 240), 'Click to continue.', C['dim'])
    elif kind == 'close':
        face(d, FACE['Thinking'])
        line(d, 'AURORA', 'You mean Kevin, or are we just saying things now?')
        slot(d, 0, 'Who is Kevin?')
        slot(d, 1, 'What happened?')
        empty(d, 2, 3)
        bar(d)
        text(d, (84, 240), HINT, C['dim'])
    elif kind == 'none':
        face(d, FACE['Thinking'])
        line(d, 'AURORA', "That's not in my registry. Try smaller words.")
        for i, label in enumerate(['What was Exodus?', 'Who is Kevin?', 'What is ORSA?', 'Is anyone out there?']):
            slot(d, i, label)
        bar(d)
        text(d, (84, 240), HINT, C['dim'])
    else:
        face(d, FACE['Neutral'])
        blank_line(d)
        slot(d, 0, 'Talk')
        slot(d, 1, 'Wait here')
        slot(d, 2, 'Lamp off')
        empty(d, 3)
        bar(d)
        panel(d, [84, 220, 233, 233], fill=(18, 22, 27))
        text(d, (89, 222), 'Settings', C['dim'])
        text(d, (84, 240), HINT, C['dim'])
    return img.resize((960, 540), Image.NEAREST)


FRAMES = [
    ('1  THE BAR, CLOSED', 'closed', 'One row under the grid, the height of the Settings button, the grid\'s full width. Click it or press T. Drawn hovered.'),
    ('2  HOVERING A REPLY (CHANGED)', 'hover', '"You say:" moves down 17 pixels to clear the bar, and the hint with it, only while it shows. Everything else as built.'),
    ('3  TYPING', 'typing', 'Bright edge and a cursor. The keys now go to the bar, so 1 to 4 type. The buttons stay live under the mouse.'),
    ('4  SHE WORKS IT OUT', 'thinking', 'Orbit on her display, as approved. Slots empty, her panel empty, your words dim in the bar until she starts.'),
    ('5  STRONG', 'strong', 'What the button opens, the same node. Typed "whats orsa": the recording line, then this one. The bar clears.'),
    ('6  CLOSE', 'close', 'She checks, and the slots hold her two best guesses, no tone labels, as a menu. A locked topic is never a guess.'),
    ('7  NONE', 'none', 'A dry miss line, and the slots show what is on her mind. A locked topic gets this same reply.'),
    ('8  THE HUB (CHANGED)', 'hub', 'The bar sits at the same height on every screen, so Settings moves down one row, 18 pixels.'),
]


def main():
    w, h = 1992, 2910
    img = Image.new('RGB', (w, h), C['bg'])
    d = ImageDraw.Draw(img)
    d.text((24, 16), 'AURORA: THE CHAT BAR (M4)', font=font(26), fill=C['glow'])
    d.text((24, 52), 'GUI scale, the talk screen grid, enlarged twice. Buttons stay the complete game; the bar is an addition. Awaiting the owner\'s approval.', font=font(15), fill=C['dim'])
    for i, (name, kind, caption) in enumerate(FRAMES):
        x, y = 24 + (i % 2) * 984, 92 + (i // 2) * 610
        d.text((x, y), name, font=font(16), fill=C['glow'])
        d.text((x, y + 24), caption, font=font(13), fill=C['text'])
        img.paste(frame(kind), (x, y + 50))

    y = 92 + 4 * 610 + 8
    d.rectangle([24, y, 960, y + 350], fill=C['panel'])
    d.text((40, y + 12), 'THE INDICATOR IN PLACE', font=font(16), fill=C['glow'])
    d.text((40, y + 36), 'The same Orbit frames, on her body and the talk face panel, 4 ticks each, then her eyes settle as approved.', font=font(13), fill=C['text'])
    for i, cells in enumerate(ORBIT):
        display(d, cells, 40 + i * 110, y + 66, 12)
    rows = [
        'Typed text always shows it, so nothing comes back instantly: at least half a lap and the settle,',
        '28 ticks, about 1.4 seconds (DRAFT). A whole lap, 44 ticks, under coherence 0.5, as buttons do.',
        '',
        'Never at the Recording voice, as approved. At Wary, and below it where she will not talk,',
        'typed text gets its recording straight away. A recording does not think.',
        '',
        'A topic that opens on a recording line still shows Orbit first: she is looking it up.',
    ]
    for i, row in enumerate(rows):
        d.text((40, y + 158 + i * 22), row, font=font(14), fill=C['text'])

    d.rectangle([984, y, 1968, y + 350], fill=C['panel'])
    d.text((1000, y + 12), 'HOW THE BAR BEHAVES', font=font(16), fill=C['glow'])
    rows = [
        'It is there whenever the slots are: on the hub and on any line she has finished. While she',
        'types or thinks it stays shut, as the slots do.',
        '',
        'T opens it, the player\'s own chat key if they moved it. Enter sends. Esc closes the bar first',
        'and a second Esc walks away. An empty Enter closes it.',
        '',
        '120 characters, about 630 pixels of text in a 300 pixel bar, so the text slides left as',
        'Minecraft\'s own text boxes do, and it stops taking keys at the cap.',
        '',
        'Your text shows in your bar only. She never repeats it: red line 5.',
        'Only the speaker sees it, and it stays on the server that runs the world.',
    ]
    for i, row in enumerate(rows):
        d.text((1000, y + 44 + i * 22), row, font=font(14), fill=C['text'])
    img.save(HERE / 'chat-bar-mockup.png')


if __name__ == '__main__':
    main()

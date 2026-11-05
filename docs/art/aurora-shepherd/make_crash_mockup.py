"""Aurora's crash, slice M4, frame by frame, at Minecraft scale. Approved 2026-11-05: display B, talk panel A.

Her body is voxels at 16 texels to the block; her display is the 8 by 6 texel grid. Talk-screen frames are at GUI scale on the
480 by 270 grid, enlarged twice. The stop screen's copy is fixed by docs/aurora-miller-pattern.md. Measured against Minecraft's
own font: the longest line, "CS-1 UNIT 4 HAS STOPPED.", is 130 GUI pixels, and the face panel is 80 by 60. It stays on her,
never on the player's screen, and it explains nothing. Nothing here is in the game yet.
Run from anywhere: python3 docs/art/aurora-shepherd/make_crash_mockup.py
"""
from pathlib import Path
from PIL import Image, ImageDraw
from make_body_directions import C, font, iso, lantern
from make_boot_and_idle_mockup import logo, pixels, tiny
from make_boot_sequence_mockup import SCREEN, WHITE, backdrop, cells, line, shell, slot
from make_chat_bar_mockup import bar
from make_face_and_talk_mockup import display, panel, text
from make_flame_and_faces_mockup import NEW, flame
from make_private_log_mockup import empty

HERE = Path(__file__).resolve().parent
# A blue of her own, with ORSA's HUD value color for the text. New to her display, and shown nowhere but here.
C.update(stop=(30, 84, 168), stopdim=(112, 160, 226), stoptext=(205, 239, 255))
FACE = dict(NEW)
PANEL_X, PANEL_Y = 200, 18

# A 3 by 5 hand font, for the panel only: one cell is one GUI pixel, so it stays sharp at every GUI scale.
GLYPHS = {
    'A': '.#. #.# ### #.# #.#', 'C': '### #.. #.. #.. ###', 'D': '##. #.# #.# #.# ##.', 'E': '### #.. ##. #.. ###',
    'H': '#.# #.# ### #.# #.#', 'I': '### .#. .#. .#. ###', 'L': '#.. #.. #.. #.. ###', 'M': '#.# ### ### #.# #.#',
    'N': '##. #.# #.# #.# #.#', 'O': '### #.# #.# #.# ###', 'P': '### #.# ### #.. #..', 'R': '##. #.# ##. #.# #.#',
    'S': '### #.. ### ..# ###', 'T': '### .#. .#. .#. .#.', 'U': '#.# #.# #.# #.# ###', '0': '.#. #.# #.# #.# .#.',
    '1': '.#. ##. .#. .#. ###', '4': '#.# #.# ### ..# ..#', '-': '... ... ### ... ...', '.': '... ... ... ... .#.',
    'x': '... ... #.# .#. #.#', ' ': '... ... ... ... ...',
}


def glyph_rows(ch):
    return GLYPHS[ch].split()


def tiny_text(d, x, y, value, color, s=1):
    for i, ch in enumerate(value):
        for j, row in enumerate(glyph_rows(ch)):
            for k, c in enumerate(row):
                if c == '#':
                    d.rectangle([x + (i * 4 + k) * s, y + j * s, x + (i * 4 + k + 1) * s - 1, y + (j + 1) * s - 1], fill=color)


def sad(d, x, y, s, color):
    """The panel's sad face, drawn in blocks: a colon and a bracket."""
    for cx, cy in ((0, 1), (0, 3)):
        d.rectangle([x + cx * s, y + cy * s, x + (cx + 1) * s - 1, y + (cy + 1) * s - 1], fill=color)
    for cx, cy in ((3, 0), (2, 1), (2, 2), (2, 3), (3, 4)):
        d.rectangle([x + cx * s, y + cy * s, x + (cx + 1) * s - 1, y + (cy + 1) * s - 1], fill=color)


def field(*rows):
    """A whole display in blue: # is bright, + is the mid blue."""
    out = [(x, y, 'stop') for y in range(6) for x in range(8)]
    colors = {'#': 'stoptext', '+': 'stopdim'}
    return out + [(x, y, colors[c]) for y, row in enumerate(rows) for x, c in enumerate(row) if c in colors]


A_FACE = field('.##...#.', '.##..#..', '.....#..', '.....#..', '.##..#..', '.##...#.')
B_GREEK = field('.#...#..', '....#...', '....#...', '.#...#..', '........', '.++++++.')
# C: her sad face, then the stop code crawling across, 5 texels tall.
CODE = '0x0000000A'


def crawl(offset):
    wide = [[] for _ in range(5)]
    for ch in CODE:
        for j, row in enumerate(glyph_rows(ch)):
            wide[j] += list(row) + ['.']
    rows = [''.join((['.'] * 8 + r + ['.'] * 8)[offset:offset + 8]) for r in wide]
    return field(*rows, '........')


def blue_panel(d):
    d.rectangle([PANEL_X, PANEL_Y, PANEL_X + 79, PANEL_Y + 59], fill=C['stop'])


def stop_dense(d):
    """Panel option A: all four lines at once, in the 3 by 5 hand font. Five rows, because STOP CODE and its number need two."""
    blue_panel(d)
    ink = C['stoptext']
    sad(d, PANEL_X + 4, PANEL_Y + 4, 4, ink)
    for i, row in enumerate(['CS-1 UNIT 4', 'HAS STOPPED.', 'PLEASE REMAIN CALM.', 'STOP CODE', '0x0000000A']):
        tiny_text(d, PANEL_X + 3, PANEL_Y + 27 + i * 6, row, ink)


def stop_page(d, page):
    """Panel option B: Minecraft's own font at its real size, on two pages that swap every 2 seconds."""
    blue_panel(d)
    ink = C['stoptext']
    if page == 0:
        sad(d, PANEL_X + 4, PANEL_Y + 5, 5, ink)
        text(d, (PANEL_X + 3, PANEL_Y + 34), 'CS-1 UNIT 4', ink)
        text(d, (PANEL_X + 3, PANEL_Y + 45), 'HAS STOPPED.', ink)
    else:
        for i, row in enumerate(['PLEASE REMAIN', 'CALM.', 'STOP CODE', '0x0000000A']):
            text(d, (PANEL_X + 2, PANEL_Y + 3 + i * 14), row, ink)


def unit(face, lamp=True, heat=True, hover=16):
    """Her body over a patch of snow. `hover` is how many texels of air are under her."""
    b = lantern()
    for x in range(2, 10):
        for y in range(4, 10):
            b.v[(x, y, 9)] = 'screen'
    for x, y, color in face:
        b.v[(2 + x, 9 - y, 9)] = color
    if not lamp:
        b.box(3, 11, 9, 9, 12, 10, 'steel')
    if heat:
        flame(b, 'b', 2)
    else:
        b.box(3, 0, 2, 9, 2, 8, 'dark')
    # Bare floor and her shadow, so the height she loses can be read.
    b.box(-2, -hover - 1, -1, 14, -hover, 11, 'steel')
    b.box(2, -hover - 1, 2, 10, -hover, 8, 'dark')
    return b


def stage(kind):
    img = backdrop()
    d = ImageDraw.Draw(img)
    shell(d)
    if kind == 'word':
        display(d, FACE['Neutral'], PANEL_X, PANEL_Y, 10)
        panel(d, [84, 88, 395, 136])
        text(d, (92, 92), 'AURORA', C['glow'])
        slot(d, 0, 'Talk')
        slot(d, 1, 'Wait here')
        slot(d, 2, 'Lamp off')
        empty(d, 3)
        bar(d, 'continuity', 'open')
        text(d, (84, 240), 'Enter says it. Esc closes the bar. Slots still click.', C['dim'])
        return img.resize((960, 540), Image.NEAREST)
    if kind in ('dense', 'page0', 'page1'):
        {'dense': stop_dense, 'page0': lambda g: stop_page(g, 0), 'page1': lambda g: stop_page(g, 1)}[kind](d)
        panel(d, [84, 88, 395, 136])
        empty(d, 0, 1, 2, 3)
        bar(d, state='off')
        text(d, (84, 240), 'Esc walks away.', C['dim'])
    elif kind == 'logo':
        d.rectangle([200, 18, 279, 77], fill=SCREEN)
        pixels(d, logo(30, 3 / 8), 210, 18, 2, SCREEN)
        panel(d, [84, 88, 395, 136])
        text(d, (92, 92), 'AURORA', C['glow'])
        empty(d, 0, 1, 2, 3)
        bar(d, state='off')
    else:
        mood, value, hint = {
            'sorry': ('Neutral', 'Sorry. You cut out. Say that again?', True),
            'again': ('Thinking', 'You cut out again. Probably the cold.', True),
            'clock': ('Thinking', 'My clock just jumped eight seconds. Did something happen?', False),
        }[kind]
        display(d, FACE[mood], PANEL_X, PANEL_Y, 10)
        line(d, 'AURORA', value)
        if hint:
            slot(d, 0, 'Talk')
            slot(d, 1, 'Wait here')
            slot(d, 2, 'Lamp off')
            empty(d, 3)
            bar(d)
            text(d, (84, 240), 'Keys 1-4, T to type. Hover for the full line. Esc walks away.', C['dim'])
        else:
            empty(d, 0, 1, 2, 3)
            bar(d, state='off')
            text(d, (84, 240), 'Click to continue.', C['dim'])
    return img.resize((960, 540), Image.NEAREST)


BODY_OPTIONS = [
    ('A  SAD FACE ONLY', [A_FACE], 'The whole 8 by 6 is the mark. Nothing else fits, so nothing else shows.',
     'Strongest at play distance. Plainest. Only the talk panel carries the words.'),
    ('B  SAD FACE AND TEXT SHAPE (PICKED)', [B_GREEK], 'A smaller mark over a row of mid blue: the shape of writing, none of the words.',
     'Reads as a stop screen, not a mood, and does not pretend to be legible. Mark loses size.'),
    ('C  THE CODE CRAWLS', [A_FACE, crawl(5), crawl(17), crawl(33)], 'The sad face, then 0x0000000A crawls by once, a texel every 2 ticks: 4.8 seconds.',
     'The only real copy that can fit. All four lines would need 26 seconds, so only the code. Readable up close at best.'),
]
WORLD = [
    ('TALKING', unit(FACE['Neutral']), 'Lamp on, pilot flame, a block of air.'),
    ('0 s  SNAP', unit(B_GREEK, False, False), 'Blue at once, no blink. Lamp, heater, flame out.'),
    ('0.5 s  DOWN', unit(B_GREEK, False, False, 8), 'Half a block lower (DRAFT), and holds.'),
    ('8 s  LOGO', unit(cells(tiny(2)), True, True, 8), 'Logo alone. Lamp and flame back.'),
    ('11.2 s  UP', unit(FACE['Neutral']), 'Risen through the logo. She speaks.'),
]
FRAMES = [
    ('1  THE WORD', 'word', 'Checked before matching, on Enter. No Orbit: she does not hear it. The bar clears in the same tick.'),
    ('2  STOP SCREEN, PANEL OPTION A (PICKED)', 'dense', 'All the copy at once, in a small hand font, 3 by 5 cells. Her panel only. Her line panel and slots are empty, the bar dead.'),
    ('3  PANEL OPTION B, FIRST PAGE', 'page0', "Minecraft's own font at real size. The longest line is 130 pixels and the panel 80, so the copy wraps to more rows than one page holds."),
    ('4  PANEL OPTION B, SECOND PAGE', 'page1', 'Pages swap every 2 seconds for the eight. Easier to read, less like a stop screen.'),
    ('5  THE LOGO', 'logo', 'After eight seconds. The logo alone, 64 ticks, as M2a left room for. No BIOS, nothing typed.'),
    ('6  BACK: "Sorry. You cut out. Say that again?"', 'sorry', 'Neutral, first time. No memory of it. Bar live and empty, slots back. No cost of any kind.'),
    ('7  ANY LATER CRASH', 'again', 'Thinking. Same frame, the second line from the spec.'),
    ('8  AFTER T4', 'clock', 'Thinking, then (Neutral) "Never mind. I don\'t want to know." It replaces both lines above from then on.'),
]


def main():
    w, h = 1992, 3820
    img = Image.new('RGB', (w, h), C['bg'])
    d = ImageDraw.Draw(img)
    d.text((24, 16), 'AURORA: THE CRASH (M4)', font=font(26), fill=C['glow'])
    d.text((24, 52), 'On her display and the talk face panel only. The world keeps running and the player\'s screen never changes. Approved 2026-11-05.', font=font(15), fill=C['dim'])

    d.text((24, 92), 'WHAT FITS ON HER 8 BY 6 DISPLAY: THE COPY DOES NOT. THE OWNER PICKED B', font=font(17), fill=C['text'])
    for i, (name, faces, how, verdict) in enumerate(BODY_OPTIONS):
        y = 122 + i * 268
        d.rectangle([24, y, 1968, y + 256], fill=C['panel'])
        d.text((38, y + 10), name, font=font(16), fill=C['glow'])
        for j, face in enumerate(faces):
            display(d, face, 38 + j * 148, y + 40, 16)
            display(d, face, 38 + j * 148, y + 150, 4)
        d.text((38, y + 182), 'Above: 16x. Below it: at play size.', font=font(12), fill=C['dim'])
        d.text((38, y + 206), how, font=font(14), fill=C['text'])
        d.text((38, y + 228), verdict, font=font(13), fill=C['dim'])
        iso(d, unit(faces[0], False, False, 8), 1700, y + 110, 6)

    y = 934
    d.text((24, y), 'IN THE WORLD, 16 TEXELS TO THE BLOCK', font=font(17), fill=C['text'])
    for i, (name, body, caption) in enumerate(WORLD):
        x = 24 + i * 392
        d.rectangle([x, y + 30, x + 380, y + 370], fill=C['panel'])
        d.text((x + 12, y + 40), name, font=font(15), fill=C['glow'])
        iso(d, body, x + 190, y + 150, 6)
        d.text((x + 12, y + 342), caption, font=font(12), fill=C['text'])

    y = 1324
    d.text((24, y), 'ON THE TALK SCREEN, IN ORDER', font=font(17), fill=C['text'])
    for i, (name, kind, caption) in enumerate(FRAMES):
        x, fy = 24 + (i % 2) * 984, y + 30 + (i // 2) * 600
        d.text((x, fy), name, font=font(16), fill=C['glow'])
        d.text((x, fy + 24), caption, font=font(12), fill=C['text'])
        img.paste(stage(kind), (x, fy + 44))

    y = 3744
    bars = [(160, 'Stop screen, 8 s. Lamp and heater out', C['stop']), (64, 'Logo, 3.2 s', C['glow']), (100, 'Her line types', C['steel2'])]
    x = 24
    for ticks, name, color in bars:
        d.rectangle([x, y, x + ticks * 3, y + 26], fill=color)
        d.text((x + 8, y + 6), name, font=font(13), fill=WHITE if color == C['stop'] else (10, 12, 14))
        x += ticks * 3 + 3
    d.text((x + 16, y + 6), 'If the talk screen is closed by then, her line plays on the ambient strip.', font=font(14), fill=C['text'])
    d.text((24, y + 40), 'At Wary she has no voice of her own, so these three lines cannot play. Instead: the logo, then the recording "ORSA Civilian Support Unit CS-1, online."', font=font(14), fill=C['text'])
    img.save(HERE / 'crash-mockup.png')


if __name__ == '__main__':
    main()

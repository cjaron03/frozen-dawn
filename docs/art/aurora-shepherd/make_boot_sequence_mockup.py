"""Aurora's boot sequence, frame by frame, at Minecraft scale, for the owner's approval. 2026-11-04.

The dock and body are voxels at 16 texels to the block. The talk screen is at GUI scale: one pixel here is one GUI pixel on
the 480 by 270 grid, enlarged twice, in a bitmap font. Every word is a draft. Nothing here is in the game yet.
Run from anywhere: python3 docs/art/aurora-shepherd/make_boot_sequence_mockup.py
"""
from pathlib import Path
from PIL import Image, ImageDraw
from make_body_directions import C, font, iso
from make_boot_and_idle_mockup import GLOW, logo, pixels, tiny
from make_dock_mockup import dock, seated
from make_face_and_talk_mockup import MOODS, TALK, body_with, display, panel, text, wrap, PIXEL

HERE = Path(__file__).resolve().parent
OFF = MOODS[7][1]
RECORDING = TALK[4][1]
CURSOR = [(0, 0, 'glow')]
WHITE = (225, 230, 228)
SCREEN = C['screen']
BIOS = [
    ('ORSA CIVILIAN SUPPORT SERIES  CS-1', ''),
    ('UNIT 4 OF 12', ''),
    ('POWER CELL', 'SEATED'),
    ('MEMORY', '64 SLOTS'),
    ('LAMP', 'OK'),
    ('HEATER PORT', 'OK'),
    ('CLOCK', 'TUESDAY, UNVERIFIED'),
    ('REGION NET', '0 OF 11 ANSWERING'),
    ('EVAC OFFICER', 'PENDING'),
    ('LANGUAGE FILTER', 'FAMILY FRIENDLY'),
    ('STABILITY ABOVE.', ''),
]


def cells(grid):
    return [(x, y, 'glow' if c == GLOW else 'glow2') for y, row in enumerate(grid) for x, c in enumerate(row) if c]


def backdrop():
    w, h = 480, 270
    img = Image.new('RGB', (w, h), (26, 28, 32))
    d = ImageDraw.Draw(img)
    for by in range(0, h, 16):
        for bx in range(0, w, 16):
            n = (bx * 31 + by * 17) % 7
            d.rectangle([bx, by, bx + 15, by + 15], fill=(44 + n, 46 + n, 50 + n))
            d.rectangle([bx, by, bx + 15, by], fill=(36 + n, 38 + n, 42 + n))
    d.rectangle([0, 214, w, h], fill=(32, 34, 38))
    return Image.blend(img, Image.new('RGB', (w, h), (6, 8, 10)), .45)


def shell(d):
    d.rectangle([194, 12, 285, 81], fill=C['shell2'], outline=C['scuff'])
    d.rectangle([202, 8, 277, 11], fill=C['lamp'])


def slot(d, i, label, hover=False, tone=None):
    x, y = 84 + (i % 2) * 158, 144 + (i // 2) * 28
    panel(d, [x, y, x + 153, y + 23], fill=(30, 44, 42) if hover else (18, 22, 27), edge=C['glow'] if hover else C['steel'])
    text(d, (x + 5, y + 6), str(i + 1), C['glow'])
    if tone:
        text(d, (x + 16, y + 1), tone, C['dim'])
    text(d, (x + 16, y + 11 if tone else y + 6), label, WHITE)


def bios(d, shown):
    panel(d, [84, 88, 395, 196], fill=SCREEN, edge=C['steel'])
    for i, (left, right) in enumerate(BIOS[:shown]):
        y = 92 + i * 9
        text(d, (92, y), left, C['glow'] if not right else C['glow2'])
        text(d, (232, y), right, C['glow'])
    if shown < len(BIOS):
        d.rectangle([92, 92 + shown * 9, 96, 100 + shown * 9], fill=C['glow'])


def line(d, label, value):
    panel(d, [84, 88, 395, 136])
    text(d, (92, 92), label, C['glow'])
    rows = wrap(value, 292)
    for i, row in enumerate(rows):
        text(d, (92, 103 + i * 10), row, WHITE)
    last = int(PIXEL.getlength(rows[-1]))
    d.rectangle([92 + last + 2, 103 + (len(rows) - 1) * 10, 97 + last, 111 + (len(rows) - 1) * 10], fill=C['glow'])


def frame(kind):
    img = backdrop()
    d = ImageDraw.Draw(img)
    shell(d)
    if kind == 'choice':
        display(d, OFF, 200, 18, 10)
        panel(d, [84, 88, 395, 136])
        text(d, (92, 92), 'CS-1 UNIT 4', C['dim'])
        slot(d, 0, 'Boot', hover=True)
        slot(d, 1, 'Leave')
        text(d, (84, 240), 'Keys 1-2 or click. Esc is the same as Leave.', C['dim'])
    elif kind == 'bios_mid':
        display(d, CURSOR, 200, 18, 10)
        bios(d, 7)
    elif kind == 'bios_end':
        display(d, CURSOR, 200, 18, 10)
        bios(d, len(BIOS))
    elif kind == 'logo':
        d.rectangle([200, 18, 279, 77], fill=SCREEN)
        pixels(d, logo(30, 3 / 8), 210, 18, 2, SCREEN)
        panel(d, [84, 88, 395, 136])
        text(d, (92, 92), 'AURORA', C['glow'])
        for i in range(4):
            x, y = 84 + (i % 2) * 158, 144 + (i // 2) * 28
            panel(d, [x, y, x + 153, y + 23], fill=(18, 22, 27))
    elif kind == 'recording':
        display(d, RECORDING, 200, 18, 10)
        line(d, 'AURORA', 'ORSA Civilian Support Unit CS-1, online. Please remain calm. An evacuation officer will be with you shortly.')
        for i in range(4):
            x, y = 84 + (i % 2) * 158, 144 + (i // 2) * 28
            panel(d, [x, y, x + 153, y + 23], fill=(18, 22, 27))
    else:
        display(d, MOODS[2][1], 200, 18, 10)
        line(d, 'AURORA', "...That's a recording. It plays on its own. Nobody's coming. Hi.")
        for i, (tone, label) in enumerate([('ASK', 'Who are you?'), ('KIND', 'Are you okay?'), ('BLUNT', 'Can you be useful?'), ('SARCASTIC', 'A talking flashlight.')]):
            slot(d, i, label, tone=tone)
    return img.resize((960, 540), Image.NEAREST)


FRAMES = [
    ('1  THE CHOICE', 'choice', 'Her display black, nothing spoken. Boot or Leave. Nothing has woken yet: she is still DORMANT.'),
    ('2  BIOS LINES', 'bios_mid', 'After Boot. One line every 8 ticks over the reply slots. A cursor texel on her display.'),
    ('3  BIOS, LAST LINE', 'bios_end', 'The motto stops where the guardrail says it stops. A 20 tick hold, then the panel clears.'),
    ('4  THE LOGO', 'logo', 'As built: 64 ticks, "ORSA" sound, on the talk screen and her body. She has lifted off.'),
    ('5  THE RECORDING', 'recording', 'As built. fm.boot, first line, dimmed eyes.'),
    ('6  HER FIRST LINE', 'hi', 'As built. Her own voice, and the four replies.'),
]


def main():
    w, h = 1992, 2420
    img = Image.new('RGB', (w, h), C['bg'])
    d = ImageDraw.Draw(img)
    d.text((24, 16), 'AURORA: THE BOOT SEQUENCE', font=font(26), fill=C['glow'])
    d.text((24, 52), 'First wake and /fd shepherd replay only. Every word on these screens is a draft for the owner to mark.', font=font(15), fill=C['dim'])

    d.text((24, 92), 'IN THE WORLD, 16 TEXELS TO THE BLOCK', font=font(17), fill=C['text'])
    states = [
        ('FOUND', seated(dock('led_red', False), OFF), 'As built. Right-click with a charged cell.'),
        ('CELL SEATED, OR LEFT DARK (NEW)', seated(dock('led_red', True), OFF), 'Cell in, light red, display black. Leave stays so.'),
        ('BOOTING (CHANGED)', seated(dock('led_green', True), CURSOR), 'Light green, cursor texel. Today it shows Recording.'),
        ('LIFT-OFF', body_with(cells(tiny(2))), 'As built. Logo on her body, dock empty.'),
    ]
    for i, (name, body, caption) in enumerate(states):
        x = 24 + i * 490
        d.rectangle([x, 122, x + 478, 470], fill=C['panel'])
        d.text((x + 14, 132), name, font=font(16), fill=C['glow'])
        iso(d, body, x + 239, 290, 7)
        d.text((x + 14, 440), caption, font=font(13), fill=C['text'])

    d.text((24, 490), 'ON THE TALK SCREEN, IN ORDER', font=font(17), fill=C['text'])
    for i, (name, kind, caption) in enumerate(FRAMES):
        x, y = 24 + (i % 2) * 984, 520 + (i // 2) * 610
        d.text((x, y), name, font=font(16), fill=C['glow'])
        d.text((x, y + 24), caption, font=font(13), fill=C['text'])
        img.paste(frame(kind), (x, y + 50))

    y = 2354
    bar = [(108, 'BIOS, 5.4 s', C['glow2']), (64, 'Logo, 3.2 s', C['glow']), (120, 'Recording line types, then hers', C['steel2'])]
    x = 24
    for ticks, name, color in bar:
        d.rectangle([x, y, x + ticks * 4, y + 26], fill=color)
        d.text((x + 8, y + 6), name, font=font(13), fill=(10, 12, 14))
        x += ticks * 4 + 3
    d.text((x + 16, y), 'After Boot: 8.6 seconds to her first word.', font=font(14), fill=C['text'])
    d.text((x + 16, y + 22), 'Other choice for the reference, a line under LANGUAGE FILTER:  INPUT FILTER   1 TERM, 10 CHARS', font=font(14), fill=C['dim'])
    img.save(HERE / 'boot-sequence-mockup.png')


if __name__ == '__main__':
    main()

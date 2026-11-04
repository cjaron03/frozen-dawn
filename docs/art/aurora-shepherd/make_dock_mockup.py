"""The CS-1 dock and Aurora's power-on, on the Lantern body, at Minecraft scale (16 texels to the block). 2026-11-04.

Three states of one block, then where it stands in the Terminus Vault beside the technician. Nothing here is in the game
yet. Run from anywhere: python3 docs/art/aurora-shepherd/make_dock_mockup.py
"""
from pathlib import Path
from PIL import Image, ImageDraw
from make_body_directions import Body, C, font, iso, lantern
from make_face_and_talk_mockup import MOODS, TALK

HERE = Path(__file__).resolve().parent
C.update(led_red=(214, 60, 48), led_green=C['glow'], slot=(12, 14, 17), cell=(216, 170, 54), cell2=(120, 96, 40), concrete=(120, 124, 126))


def dock(led, cell):
    b = Body()
    b.box(0, 0, 0, 16, 6, 16, lambda x, y, z: 'steel' if y == 5 else 'dark')
    b.box(1, 6, 1, 3, 9, 15, 'steel2')
    b.box(13, 6, 1, 15, 9, 15, 'steel2')
    for x in range(0, 16):
        b.v[(x, 1, 15)] = 'stripe' if x % 4 < 2 else 'stripe2'
    b.box(5, 2, 15, 11, 5, 16, 'slot')
    if cell:
        b.box(6, 2, 15, 10, 5, 16, lambda x, y, z: 'cell' if y > 2 else 'cell2')
    b.v[(12, 3, 15)] = led
    b.v[(13, 3, 15)] = led
    return b


def seated(body, face):
    unit = lantern()
    for x in range(2, 10):
        for y in range(4, 10):
            unit.v[(x, y, 9)] = 'screen'
    for x, y, color in face:
        unit.v[(2 + x, 9 - y, 9)] = color
    for (x, y, z), color in unit.v.items():
        body.v[(x + 2, y + 5, z + 3)] = color
    return body


def plan(d, ox, oy, s):
    """Vault room, x 101..105 across, z -3..3 down, north at the top."""
    d.rectangle([ox - s, oy - s, ox + 6 * s - 1, oy + 8 * s - 1], fill=C['dark'])
    d.rectangle([ox, oy, ox + 5 * s - 1, oy + 7 * s - 1], fill=C['concrete'])
    for i in range(1, 5):
        d.line([ox + i * s, oy, ox + i * s, oy + 7 * s], fill=(104, 108, 110))
    for i in range(1, 7):
        d.line([ox, oy + i * s, ox + 5 * s, oy + i * s], fill=(104, 108, 110))
    d.rectangle([ox - s, oy + 2 * s, ox - 1, oy + 5 * s - 1], fill=C['player2'])

    def block(x, z, color, label):
        px, py = ox + (x - 101) * s, oy + (z + 3) * s
        d.rectangle([px + 3, py + 3, px + s - 4, py + s - 4], fill=C[color])
        return px + s // 2, py + s // 2, label
    marks = [block(102, -3, 'player', 'Tech'), block(103, -3, 'glow2', 'Dock'), block(105, 0, 'steel2', 'Cradle'),
             block(101, 3, 'cell2', 'Desk'), block(103, 3, 'cell2', 'Locker')]
    for x, y, label in marks:
        d.text((x, y), label, font=font(11), fill=(10, 12, 14), anchor='mm')
    d.text((ox - s, oy + 8 * s + 10), 'West, left: the last door.', font=font(13), fill=C['dim'])
    d.text((ox - s, oy + 8 * s + 30), 'He faces south, the dock at his left hand.', font=font(13), fill=C['dim'])


def main():
    w, h = 1500, 820
    img = Image.new('RGB', (w, h), C['bg'])
    d = ImageDraw.Draw(img)
    d.text((24, 16), 'AURORA: THE CS-1 DOCK AND POWER-ON', font=font(22), fill=C['text'])
    d.text((24, 46), 'One block, 16 texels wide. She rests on it as the block until she wakes, then lifts off as herself.',
           font=font(14), fill=C['dim'])
    offline = MOODS[7][1]
    recording = TALK[4][1]
    states = [
        ('1  FOUND', seated(dock('led_red', False), offline), 'Slot empty, red light, eyes off.', 'Right-click with a Power Cell.'),
        ('2  CELL IN', seated(dock('led_green', True), recording), 'Light goes green, recording plays.', 'The talk screen opens on fm.boot.'),
        ('3  AWAKE', dock('led_green', False), 'She lifts off and follows.', 'The dock stays, empty.'),
    ]
    for i, (name, body, *caption) in enumerate(states):
        left = 12 + i * 320
        d.rectangle([left, 80, left + 308, 470], fill=C['panel'])
        d.text((left + 14, 90), name, font=font(18), fill=C['glow'])
        iso(d, body, left + 154, 250, 7)
        for n, line in enumerate(caption):
            d.text((left + 14, 416 + n * 20), line, font=font(13), fill=C['text'] if n == 0 else C['dim'])
    d.rectangle([972, 80, w - 12, 800], fill=C['panel'])
    d.text((986, 90), 'WHERE IT STANDS', font=font(18), fill=C['glow'])
    plan(d, 1080, 190, 54)
    d.rectangle([12, 482, 948, 800], fill=C['panel'])
    d.text((26, 494), 'HOW IT WORKS', font=font(18), fill=C['glow'])
    notes = [
        'The cell is spent: it goes into her and she never asks for another. No charge bar, no upkeep.',
        'Any Power Cell with charge in it wakes her. An empty one is refused with a dry click.',
        'No cell on you? One charged spare lies in the locker with the logs, the one he never got to fit.',
        'One Aurora a world. In the vault she is the one; a creative dock while she is awake stays dark.',
        'First slice: the dock is creative tab and /give only. The vault placement comes in a later slice.',
    ]
    for n, line in enumerate(notes):
        d.text((26, 530 + n * 28), line, font=font(14), fill=C['text'])
    img.save(HERE / 'dock-mockup.png')


if __name__ == '__main__':
    main()

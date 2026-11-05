"""Nine's lamp, three ways, for the owner to pick from. 2026-11-05.

The script calls it "a keepsake item" and says nothing else, and nothing in the code names it, so what it looks like and
what it does are open. It is unit 9's lamp strip in its housing, at 16 texels to the block. Nothing here is in the game
yet. Run from anywhere: python3 docs/art/aurora-shepherd/make_nine_lamp_mockup.py
"""
from pathlib import Path
from PIL import Image, ImageDraw
from make_body_directions import Body, C, flat, font, iso, lantern, player
import make_nine_husk_mockup  # noqa: F401, adds the husk's colors to C

HERE = Path(__file__).resolve().parent
C.update(wall=(96, 100, 102), wall2=(84, 88, 90), halo=(92, 90, 70), item_bg=(139, 139, 139), slot_edge=(55, 55, 55))

# The item, 16 by 16, drawn as it sits in a slot. '.' is clear.
ICON = """
................
................
................
................
..ssssssssssss..
.sSSSSSSSSSSSSs.
.sDddDdDddDdDDs.
.sDddDdDddDdDDs.
.sSSSSSSSSSSSSs.
..ssssssssssss..
....r......b....
....r......b....
...r........b...
...r........b...
................
................
"""
ICON_COLORS = dict(s='steel', S='steel2', D='dead2', d='dead', r='wire', b='wire2')


def module(lit=False):
    """Her lamp strip in its housing, with the two wires it came out on. x runs along the strip, z toward you."""
    b = Body()
    b.box(0, 0, 0, 10, 3, 3, 'steel')
    b.box(0, 2, 0, 10, 3, 3, 'steel2')
    b.box(2, 1, 2, 8, 2, 3, 'lamp' if lit else 'dead')
    for y in (-1, -2):
        b.v[(3, y, 1)] = 'wire'
        b.v[(6, y, 1)] = 'wire2'
    return b


def placed(lit):
    """On a wall, one block of it, with a cell clip under the housing."""
    b = Body()
    b.box(0, 0, -1, 16, 16, 0, lambda x, y, z: 'wall2' if (x // 4 + y // 4) % 2 else 'wall')
    for (x, y, z), color in module(lit).v.items():
        if y >= 0:
            b.v[(x + 3, y + 8, z)] = color
    b.box(6, 5, 0, 10, 8, 2, lambda x, y, z: 'cell' if lit and y > 5 else 'cell2' if lit else 'slot')
    return b


def carried():
    """In her handle, where a hand would go. It stays dark: it is riding with her, not working for her."""
    b = lantern()
    b.box(3, 12, 3, 9, 15, 6, 'steel')
    b.box(3, 14, 3, 9, 15, 6, 'steel2')
    b.box(4, 13, 5, 8, 14, 6, 'dead')
    return b


def panel(d, box, title, *caption):
    d.rectangle(box, fill=C['panel'])
    d.text((box[0] + 14, box[1] + 10), title, font=font(18), fill=C['glow'])
    for n, line in enumerate(caption):
        d.text((box[0] + 14, box[3] - 22 * len(caption) - 6 + n * 22), line, font=font(13), fill=C['text'] if n == 0 else C['dim'])


def icon(d, ox, oy, s):
    d.rectangle([ox, oy, ox + 16 * s - 1, oy + 16 * s - 1], fill=C['item_bg'])
    for y, row in enumerate(ICON.split()):
        for x, ch in enumerate(row):
            if ch != '.':
                d.rectangle([ox + x * s, oy + y * s, ox + (x + 1) * s - 1, oy + (y + 1) * s - 1], fill=C[ICON_COLORS[ch]])


def main():
    w, h = 1500, 860
    img = Image.new('RGB', (w, h), C['bg'])
    d = ImageDraw.Draw(img)
    d.text((24, 16), "NINE'S LAMP: THREE WAYS", font=font(22), fill=C['text'])
    d.text((24, 46), "The script says only \"a keepsake item\", and the code has nothing, so this is yours to pick. 16 texels to the block.",
           font=font(14), fill=C['dim'])
    col = 488
    for i, (title, lines) in enumerate((
            ('A  KEEPSAKE', ('An item, and nothing more. It never lights.', 'No use and no recipe. It cannot burn, and it', 'does not despawn.',
                             'Cost: one item. Risk: it sits in a chest.')),
            ('B  ON THE WALL', ('A small block. Fit a Power Cell and it lights,', 'as the Battery Lantern does, drawing the same 50 W.', 'She drifts over to look at it sometimes.',
                                'Cost: one block, reusing the lantern. Answers', '"I don\'t want to leave it in the dark".')),
            ('C  IN HER HANDLE', ('Right-click her with it and she takes it. It rides', 'in her carry handle, dark, for the rest of the game.', 'Item as in A until you hand it over.',
                                  'Recommended: you see it every day, and she', 'carries Nine up to the stars in T3.')))):
        panel(d, (12 + i * col + (6 if i else 0), 80, 12 + (i + 1) * col - 6, 560), title, *lines)
    # A
    icon(d, 60, 140, 12)
    icon(d, 300, 200, 2)
    d.rectangle([296, 196, 336, 236], outline=C['slot_edge'])
    d.text((300, 244), 'In a slot', font=font(12), fill=C['dim'])
    d.rectangle([60, 350, 470, 440], fill=(16, 8, 22))
    d.text((70, 358), "Nine's Lamp", font=font(14), fill=(255, 255, 255))
    d.text((70, 382), 'CS-1 unit 9. Off.', font=font(13), fill=(170, 170, 170))
    d.text((70, 404), 'Tooltip, draft', font=font(11), fill=(110, 110, 130))
    # B
    iso(d, placed(False), 640, 270, 7)
    iso(d, placed(True), 840, 270, 7)
    d.text((625, 410), 'No cell', font=font(12), fill=C['dim'])
    d.text((815, 410), 'Cell in, lit', font=font(12), fill=C['dim'])
    # C
    iso(d, carried(), 1300, 270, 8)
    s = 3
    base = 400
    player(d, 1020, base, s)
    flat(d, carried(), 1090, base - 16 * s, s, 'front')
    d.text((1020, base + 6), 'At play size', font=font(12), fill=C['dim'])

    d.rectangle((12, 572, w - 12, h - 12), fill=C['panel'])
    d.text((26, 584), 'THE PIECE ITSELF', font=font(18), fill=C['glow'])
    a = module()
    iso(d, a, 150, 660, 9)
    d.text((26, 780), 'Her 6 by 1 lamp strip in a 10 by 3 by 3 housing, two wires', font=font(13), fill=C['text'])
    d.text((26, 802), 'hanging. Dead strip, not her warm white, in A and C.', font=font(13), fill=C['dim'])
    notes = ('Comes from the husk: the Kind reply hands it over; otherwise right-click the husk once she has seen it.',
             'Not a light you carry, in any of the three: it is not a Work Lamp, and it does not stack.',
             'Nothing here changes her lamp, her warmth or her score. The reply gives +20; the item gives nothing.',
             "One a world. In A and C it cannot be lost. In B a broken block drops it, with its cell.",
             'First slice: creative tab and /give too, so the owner can try it without the walk.')
    for n, line in enumerate(notes):
        d.text((560, 616 + n * 30), line, font=font(14), fill=C['text'])
    img.save(HERE / 'nine-lamp-mockup.png')


if __name__ == '__main__':
    main()

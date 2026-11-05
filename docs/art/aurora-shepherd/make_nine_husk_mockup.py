"""Nine's husk: another CS-1 Shepherd unit, the same Lantern body as Aurora, left at the Blast Pit. 2026-11-05.

At Minecraft scale, 16 texels to the block, built from the approved Lantern voxels. Two poses for the owner to pick from,
the state after the lamp is out, the husk beside Aurora and a player, and where it lies in the pit, drawn from
BlastPitPlacement. Nothing here is in the game yet. Run from anywhere: python3 docs/art/aurora-shepherd/make_nine_husk_mockup.py
"""
from pathlib import Path
from PIL import Image, ImageDraw
from make_body_directions import Body, C, flat, font, iso, lantern, player
import make_dock_mockup  # noqa: F401, adds the cell's colors to C

HERE = Path(__file__).resolve().parent
C.update(soot=(58, 54, 50), soot2=(84, 78, 70), ash=(70, 66, 62), ash2=(50, 48, 46), crack=(120, 134, 140),
         dead=(112, 110, 92), dead2=(88, 86, 74), wire=(170, 60, 44), wire2=(60, 110, 150), floor=(52, 46, 42), floor2=(66, 58, 50),
         paper=(222, 218, 200))


def burnt(cells, x, y, z):
    """The side that faced the pad took the heat: soot in blocks of texels, never a smooth fade."""
    h = ((x // 2) * 73856093 ^ (y // 2) * 19349663 ^ (z // 2) * 83492791) % 5
    return 'soot' if h == 0 else 'soot2' if h < 3 else cells


def husk(lamp=True):
    """Aurora's body, dead. It sits on what is left of the grille, so it is two texels lower than the voxels it came from."""
    b = lantern()
    b.v = {k: ('dead' if v == 'lamp' else 'screen' if v in ('glow', 'glow2') else v) for k, v in b.v.items()}
    b.cut([k for k in b.v if k[1] < 2])
    for k, v in list(b.v.items()):
        if k[0] >= 8 and v in ('shell', 'shell2', 'scuff'):
            b.v[k] = burnt(v, *k)
    # The crack across her kind of display: dead glass, no face.
    for x, y in ((3, 8), (4, 7), (5, 7), (6, 6), (7, 5)):
        b.v[(x, y, 9)] = 'crack'
    # The handle: one post gone, the bar hanging from the other.
    b.cut([(9, y, 4) for y in range(12, 16)] + [(x, 15, 4) for x in range(6, 10)])
    for x, y in ((6, 14), (7, 14), (8, 13), (9, 12)):
        b.v[(x, y, 4)] = 'steel2'
    # The right duct is off, and lies beside it. Its socket shows the cell.
    b.cut([k for k in b.v if k[0] >= 12])
    b.cut([(x, y, z) for x in (10, 11) for y in range(5, 9) for z in range(3, 7)])
    b.box(9, 5, 3, 10, 9, 7, 'slot')
    b.box(10, 5, 3, 11, 9, 7, lambda x, y, z: 'cell' if y > 5 else 'cell2')
    for y, z in ((8, 5), (7, 4), (6, 5), (5, 4)):
        b.v[(10, y, z)] = 'slot'
    if not lamp:
        b.box(3, 11, 9, 9, 12, 10, 'slot')
        b.v[(4, 10, 9)] = 'wire'
        b.v[(7, 10, 9)] = 'wire2'
        b.v[(7, 9, 9)] = 'wire2'
    return Body.__new__(Body), b


def shifted(body, dx, dy, dz):
    out = Body()
    out.v = {(x + dx, y + dy, z + dz): c for (x, y, z), c in body.v.items()}
    return out


def on_ground(unit, duct=True):
    """A patch of scorched floor, one block and a bit, with the torn duct lying on it."""
    g = Body()
    g.box(-3, -1, -2, 19, 0, 15, lambda x, y, z: 'floor2' if (x // 2 + z // 3) % 3 == 0 else 'floor')
    g.v.update(unit.v)
    if duct:
        g.box(14, 0, 8, 18, 3, 14, 'dark')
        g.box(14, 3, 11, 15, 4, 12, 'stripe')
    return g


def upright(lamp=True):
    return on_ground(shifted(husk(lamp)[1], 1, -2, 1))


def on_its_side(lamp=True):
    """Tipped over onto its left, so the display looks along the floor and the handle lies out flat."""
    unit = Body()
    unit.v = {(y - 2, 3 - x, z): c for (x, y, z), c in husk(lamp)[1].v.items()}
    return on_ground(shifted(unit, 1, 3, 1), duct=False)


def plan(d, ox, oy, s):
    """The pit from above, one square a block, north at the top, from BlastPitPlacement.placeStructure and placeInfrastructure."""
    def cell(x, z, color):
        px, py = ox + (x + 21) * s, oy + (z + 21) * s
        d.rectangle([px, py, px + s - 1, py + s - 1], fill=color)
    for x in range(-21, 22):
        for z in range(-21, 22):
            r = (x * x + z * z) ** .5
            depth = 5 if r <= 4.5 else 4 if r <= 9.5 else 3 if r <= 14 else 2 if r <= 17.5 else 1 if r <= 19.5 else 0
            cell(x, z, (58 + 15 * (5 - depth), 54 + 15 * (5 - depth), 52 + 15 * (5 - depth)) if r <= 20.45 else C['bg'])
    for x in range(-4, 5):
        for z in range(-4, 5):
            cell(x, z, (46, 42, 40))
    for x in range(-2, 3):
        for z in range(-2, 3):
            cell(x, z, (78, 84, 88))
    for z in range(-5, 6):
        cell(0, z, (30, 30, 34))
    for x in range(-11, -6):
        for z in range(-3, 4):
            cell(x, z, (100, 110, 118))
    cell(0, -2, C['cell'])
    for x, z in ((-6, 3), (6, -3), (4, 5), (-4, 4), (-2, -4)):
        cell(x, z, (120, 104, 84))
    cell(9, 7, C['glow'])
    d.text((ox + (-9 + 21) * s + s // 2, oy + (21) * s + s // 2), 'TOWER', font=font(11), fill=(16, 18, 20), anchor='mm')
    d.text((ox + 21 * s + s // 2, oy + 14 * s), 'MOUNT', font=font(11), fill=C['text'], anchor='mm')
    d.line([ox + 30 * s + s // 2, oy + 28 * s + s // 2, ox + 37 * s, oy + 35 * s], fill=C['glow'])
    d.text((ox + 37 * s + 2, oy + 35 * s + 4), 'NINE', font=font(13), fill=C['glow'])
    r = 18 * s
    cx, cy = ox + 21 * s + s // 2, oy + 21 * s + s // 2
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(200, 110, 60))


def panel(d, box, title, *caption):
    d.rectangle(box, fill=C['panel'])
    d.text((box[0] + 14, box[1] + 10), title, font=font(18), fill=C['glow'])
    for n, line in enumerate(caption):
        d.text((box[0] + 14, box[3] - 22 * len(caption) - 6 + n * 22), line, font=font(13), fill=C['text'] if n == 0 else C['dim'])


def main():
    w, h = 1500, 900
    img = Image.new('RGB', (w, h), C['bg'])
    d = ImageDraw.Draw(img)
    d.text((24, 16), "NINE'S HUSK: CS-1 UNIT 9, LEFT AT THE BLAST PIT", font=font(22), fill=C['text'])
    d.text((24, 46), "16 texels to the block, the same Lantern voxels as Aurora. No cell charge, no face, no lamp lit. Pick a pose.",
           font=font(14), fill=C['dim'])
    panel(d, (12, 80, 320, 480), 'A  SAT DOWN', 'It came down where it stood.', 'Grille crushed, looking at the pad.',
          'Recommended: it reads as a stop,', 'and it sits as Enoch sits.')
    iso(d, upright(), 165, 230, 7)
    panel(d, (332, 80, 640, 480), 'B  ON ITS SIDE', 'Knocked over, display along the floor.', 'Reads as wreckage, not as somebody.',
          'The display is harder to see.')
    iso(d, on_its_side(), 480, 240, 7)
    panel(d, (652, 80, 960, 480), 'A, LAMP OUT', 'Lamp gone, wires left in the socket.', 'It stays as dressing, and gives no',
          'more. Behind the lamp housing was', 'the folded sheet.')
    iso(d, upright(lamp=False), 805, 230, 7)

    s = 4
    d.rectangle((12, 492, 640, 888), fill=C['panel'])
    d.text((26, 502), 'AT SCALE', font=font(18), fill=C['glow'])
    base = 790
    for block in range(4):
        y = base - block * 16 * s
        d.line([30, y, 620, y], fill=C['grid'])
    player(d, 60, base, s)
    aurora = lantern()
    flat(d, aurora, 200, base - 16 * s, s, 'front')
    flat(d, shifted(husk()[1], 0, -2, 0), 350, base, s, 'front')
    flat(d, shifted(husk()[1], 0, -2, 0), 460, base, s, 'side')
    d.text((60, base + 8), 'You', font=font(13), fill=C['dim'])
    d.text((200, base + 8), 'Aurora', font=font(13), fill=C['dim'])
    d.text((350, base + 8), 'Nine, front', font=font(13), fill=C['dim'])
    d.text((460, base + 8), 'Nine, side', font=font(13), fill=C['dim'])
    d.text((26, 830), 'One block, on the ground. Same body, so the player sees what she is at once.', font=font(13), fill=C['text'])
    d.text((26, 852), 'Soot on the side toward the pad, not frost: the pit is warm and melts snow.', font=font(13), fill=C['dim'])

    d.rectangle((652, 492, 960, 888), fill=C['panel'])
    d.text((666, 502), 'WHAT IT DOES', font=font(18), fill=C['glow'])
    for n, line in enumerate(("A block, not an entity, like Enoch.", 'Sealed: it cannot be broken.', '',
                              'Placed when T2 starts or when that', 'area next loads, never in sight.', '',
                              'She must be there for the scene.', 'Until then right-click does nothing.', '',
                              "A charged cell gets the dock's dry", 'click, and she says why.', '',
                              'Memory empty, not wiped: nothing on', 'the model says that. She does.')):
        d.text((666, 536 + n * 24), line, font=font(13), fill=C['text'])

    d.rectangle((972, 80, w - 12, 888), fill=C['panel'])
    d.text((986, 90), 'WHERE IT LIES', font=font(18), fill=C['glow'])
    plan(d, 1016, 136, 10)
    for n, line in enumerate(('One square a block, north at the top. Darker is deeper.', 'Three blocks down, 9 east and 7 south of the mount,',
                              'clear of the pad, tower and flame trench.', 'Orange ring: the warm zone, 18 blocks.',
                              'Facing west, toward the mount.', 'If a player has built there, the next free floor',
                              'on the same ring.')):
        d.text((986, 590 + n * 22), line, font=font(13), fill=C['text'] if n in (0, 1, 2) else C['dim'])
    img.save(HERE / 'nine-husk-mockup.png')


if __name__ == '__main__':
    main()

"""Textures of the Terminus Vault, from the approved mockups (vault-rooms-mockup.png, vault-contents-mockup.png,
watch-floor-mockup.png, vault-branding-mockup.png, vault-last-rooms-mockup.png).
Writes into the mod's assets. Run from anywhere: python3 docs/art/terminus/make_vault_textures.py
"""
import random
from pathlib import Path
from PIL import Image, ImageDraw
from make_system_plot_mockup import plot
from make_vault_warning_mockup import scrawl
from make_watch_floor_tally_mockup import lamp as tally_lamp

ROOT = Path(__file__).resolve().parents[3]
BLOCK = ROOT / 'src/main/resources/assets/frozendawn/textures/block'
ENTITY = ROOT / 'src/main/resources/assets/frozendawn/textures/entity'
hexc = lambda h: tuple(bytes.fromhex(h)) + (255,)


def noise(name, colours, seed):
    r = random.Random(seed)
    im = Image.new('RGBA', (16, 16))
    for x in range(16):
        for y in range(16):
            im.putpixel((x, y), hexc(r.choice(colours)))
    return im


def walkway():
    im = noise('w', ['8d9296', '83888c'], 3)
    d = ImageDraw.Draw(im)
    for y in (0, 15):
        d.line((0, y, 15, y), hexc('5e6468'))
    for x in (3, 8, 12):
        for y in (3, 8, 12):
            d.point((x, y), hexc('5e6468'))
    return im


def panel(viewport):
    """The whole door, three blocks square: dark steel, riveted, a chevron band; the last door has a viewport instead."""
    im = Image.new('RGBA', (48, 48), hexc('3a4045'))
    d = ImageDraw.Draw(im)
    for x in (0, 15, 16, 31, 32, 47):
        d.line((x, 0, x, 47), hexc('2c3034'))
    for cx in (0, 16, 32):
        for y in (3, 44):
            for x in (3, 12):
                d.point((cx + x, y), hexc('788086'))
    if viewport:
        d.rectangle((20, 14, 27, 25), hexc('788086'))
        for x in range(21, 27):
            for y in range(15, 25):
                im.putpixel((x, y), (159, 196, 216, 70))
    else:
        for y in range(20, 28):
            for x in range(48):
                if ((x + y) // 4) % 2 == 0:
                    d.point((x, y), hexc('a89654'))
    return im


def clock_face():
    im = Image.new('RGBA', (48, 16), hexc('2c3034'))
    ImageDraw.Draw(im).rectangle((2, 3, 45, 12), hexc('0a0e10'))
    return im


FONT = {'A': '111/101/111/101/101', 'C': '111/100/100/100/111', 'D': '110/101/101/101/110', 'E': '111/100/110/100/111', 'F': '111/100/110/100/100',
        'G': '111/100/101/101/111', 'I': '111/010/010/010/111', 'L': '100/100/100/100/111', 'O': '111/101/101/101/111', 'P': '111/101/111/100/100',
        'R': '110/101/110/101/101', 'S': '111/100/111/001/111', 'U': '101/101/101/101/111', 'V': '101/101/101/101/010', 'X': '101/101/010/101/101',
        '1': '010/110/010/010/111', '3': '111/001/011/001/111', '4': '101/101/111/001/001', '-': '000/000/111/000/000', ' ': '000/000/000/000/000',
        '>': '100/010/001/010/100', '<': '001/010/100/010/001'}
STENCIL = hexc('d6c896')
# The approved glyphs, from make_watch_floor_mockup.py.
GLYPHS = ['..##..../.#..#.../.#..#.../..##..../...#..../...#..../..###.../........',
          '#......#/.#....#./..#..#../...##.../...##.../..#..#../.#....#./#......#',
          '...##.../..####../.##..##./##....##/##....##/.##..##./..####../...##...',
          '########/#......#/#.####.#/#.#..#.#/#.#..#.#/#.####.#/#......#/########',
          '#.#.#.#./.#.#.#.#/#.#.#.#./.#.#.#.#/#.#.#.#./.#.#.#.#/#.#.#.#./.#.#.#.#',
          '...#..../..###.../.#####../#######./...#..../...#..../...#..../...#....']


def stencil(im, x, y, text, colour=STENCIL):
    for ch in text:
        for j, row in enumerate(FONT[ch].split('/')):
            for i, c in enumerate(row):
                if c == '1' and 0 <= x + i < im.width:
                    im.putpixel((x + i, y + j), colour)
        x += 4


def branded(viewport):
    """The door as branded: door 1's chevrons kept, the facility stencilled over them and the seal welded in a hurry."""
    im = panel(viewport)
    d = ImageDraw.Draw(im)
    if viewport:
        stencil(im, 15, 3, 'ORSA')
        stencil(im, 13, 30, 'CVF-1')
        return im
    d.rectangle((0, 4, 47, 33), hexc('3a4045'))
    for x in (0, 15, 16, 31, 32, 47):
        d.line((x, 4, x, 33), hexc('2c3034'))
    for y in range(34, 42):
        for x in range(48):
            d.point((x, y), hexc('a89654') if ((x + y) // 4) % 2 == 0 else hexc('3a4045'))
    stencil(im, 15, 8, 'ORSA')
    stencil(im, 13, 16, 'CVF-1')
    stencil(im, 7, 24, 'SEALED')
    r = random.Random(5)
    for y in range(1, 47, 3):                       # the weld bead down the seam, rough
        d.point((47 - r.randint(0, 1), y), hexc('c49660'))
    return im


def shell(seed):
    return noise('s', ['5b6064', '555a5e'], seed)


def hazard(seed, x0):
    """A shell tile with the band along its foot."""
    im = shell(seed)
    for x in range(16):
        for y in range(12, 16):
            if ((x + x0) // 6) % 2 == 0:
                im.putpixel((x, y), hexc('a89654'))
    return im


def word(text, tiles, seed):
    """Stencil across several tiles, over the band."""
    wide = Image.new('RGBA', (16 * tiles, 16))
    for i in range(tiles):
        wide.paste(hazard(seed + i, i * 16), (i * 16, 0))
    stencil(wide, 2, 4, text)
    return [wide.crop((i * 16, 0, i * 16 + 16, 16)) for i in range(tiles)]


def plain_word(text, tiles, seed):
    wide = Image.new('RGBA', (16 * tiles, 16))
    for i in range(tiles):
        wide.paste(shell(seed + i), (i * 16, 0))
    stencil(wide, 2, 5, text)
    return [wide.crop((i * 16, 0, i * 16 + 16, 16)) for i in range(tiles)]


def warning():
    """The marker on the wall by the far ledge, five blocks by three, the top row over the hazard band."""
    wide = Image.new('RGBA', (80, 48))
    for i in range(5):
        wide.paste(hazard(21, 0), (i * 16, 0))
        for j in (1, 2):
            wide.paste(shell(1), (i * 16, j * 16))
    scrawl(wide, 0, 1)
    return [wide.crop((x * 16, y * 16, x * 16 + 16, y * 16 + 16)) for y in range(3) for x in range(5)]


def flag():
    """The weathered flag laid flat on the wall, as the branding mockup: two tiles."""
    source = Image.open(BLOCK / 'orsa_flag_weathered.png').convert('RGBA').resize((32, 16), Image.NEAREST)
    return [source.crop((i * 16, 0, i * 16 + 16, 16)) for i in range(2)]


def glyph(index, lamp):
    """A panel of the board or a fuse: dark steel, the 8x8 glyph, pale lit, grey dark, gold once pulled."""
    colour = {'lit': hexc('9fd6e8'), 'dark': hexc('3a4248'), 'pulled': hexc('d6b45c')}[lamp]
    im = Image.new('RGBA', (16, 16), hexc('2c3034'))
    d = ImageDraw.Draw(im)
    d.rectangle((1, 1, 14, 14), hexc('0e1216'))
    for y, row in enumerate(GLYPHS[index].split('/')):
        for x, c in enumerate(row):
            if c == '#':
                im.putpixel((x + 4, y + 4), colour)
    return im


def crate():
    side = noise('c', ['4f5a48', '48524a'], 9)
    d = ImageDraw.Draw(side)
    d.rectangle((0, 0, 15, 15), outline=hexc('3c4438'))
    top = side.copy()
    ImageDraw.Draw(top).rectangle((2, 2, 13, 13), hexc('181c1e'))
    return side, top


def seal_order():
    im = noise('a', ['ded6be', 'd6cdb4'], 8)
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 15, 2), hexc('963428'))
    for y in (5, 7, 9, 11):
        d.line((2, y, 13, y), hexc('6a665c'))
    return im


# Custodian: the mockup's shut and open frames on a 128x64 sheet, faces laid out as CustodianModel's boxes.
STEEL, DARK, FROST = hexc('70787e'), hexc('3a4045'), hexc('d6e2e8')


def faces(d, u, v, w, h, depth, colour):
    d.rectangle((u + depth, v, u + depth + 2 * w - 1, v + depth - 1), colour)
    d.rectangle((u, v + depth, u + 2 * depth + 2 * w - 1, v + depth + h - 1), colour)


def custodian():
    """The chest as it stands open behind two plates, which carry the paint: each 8 by 16, at (88, 0) and (88, 20), and the
    core at (88, 40). The plates, and the core while the shell is shut, cover the cavity."""
    im = Image.new('RGBA', (128, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    faces(d, 0, 0, 12, 10, 10, STEEL)
    faces(d, 0, 20, 20, 19, 10, STEEL)
    faces(d, 64, 0, 6, 25, 6, DARK)
    faces(d, 64, 32, 6, 19, 8, DARK)
    hx, hy = 10, 10                                     # the head's front face
    d.rectangle((hx + 3, hy + 3, hx + 8, hy + 6), hexc('0a0c0e'))
    bx, by = 10, 30                                     # the chest's front face
    d.rectangle((bx + 3, by + 3, bx + 16, by + 16), hexc('06080a'))
    d.rectangle((bx + 1, by + 2, bx + 2, by + 16), hexc('8a9298'))
    d.rectangle((bx + 17, by + 2, bx + 18, by + 16), hexc('8a9298'))
    faces(d, 88, 0, 8, 16, 1, DARK)
    faces(d, 88, 20, 8, 16, 1, DARK)
    faces(d, 88, 40, 6, 8, 3, hexc('2a3034'))
    paint = Image.new('RGBA', (16, 16), STEEL)
    p = ImageDraw.Draw(paint)
    p.line((7, 0, 7, 15), DARK)
    p.rectangle((1, 1, 14, 8), hexc('a89654'))
    stencil(paint, 2, 2, 'CSU', hexc('1e2022'))
    stencil(paint, 6, 10, '3', hexc('282c30'))
    im.paste(paint.crop((0, 0, 8, 16)), (89, 1))
    im.paste(paint.crop((8, 0, 16, 16)), (89, 21))
    r = random.Random(3)
    for _ in range(170):
        x, y = r.randint(0, 127), r.randint(0, 63)
        inside_plate = bx + 2 <= x <= bx + 17 and by + 2 <= y <= by + 17
        if im.getpixel((x, y))[3] and not inside_plate:
            im.putpixel((x, y), FROST)
    return im


def custodian_lens():
    """Drawn through the eyes render type."""
    im = Image.new('RGBA', (128, 64), (0, 0, 0, 0))
    ImageDraw.Draw(im).rectangle((15, 14, 16, 15), hexc('d6f0fa'))
    return im


def custodian_core():
    """Drawn through the eyes render type too, at whatever light the core has."""
    im = Image.new('RGBA', (128, 64), (0, 0, 0, 0))
    faces(ImageDraw.Draw(im), 88, 40, 6, 8, 3, hexc('f4fbff'))
    return im


SEGMENTS = {'0': 'abcdef', '1': 'bc', '2': 'abged', '3': 'abgcd', '4': 'fgbc', '5': 'afgcd', '6': 'afgedc', '7': 'abc', '8': 'abcdefg', '9': 'abcfgd'}
PARTS = dict(a=(0, 0, 2, 0), b=(2, 0, 2, 2), c=(2, 2, 2, 4), d=(0, 4, 2, 4), e=(0, 2, 0, 4), f=(0, 0, 0, 2), g=(0, 2, 2, 2))


def clock_digits():
    im = Image.new('RGBA', (44, 5), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for digit, on in SEGMENTS.items():
        x = int(digit) * 4
        for s in on:
            a, b, c, e = PARTS[s]
            d.rectangle((x + a, b, x + c, e), hexc('d6f0fa'))
    d.point((40, 1), hexc('9fd6e8')); d.point((40, 3), hexc('9fd6e8'))
    return im


def technician():
    """A player-layout skin: a grey ORSA suit under frost, pale still face, an empty holster on the right hip."""
    r = random.Random(7)
    im = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    skin, hair, suit, suit2, frost = hexc('969ea0'), hexc('3c3e40'), hexc('54606a'), hexc('46525c'), hexc('d6e2e8')
    # Head: top, bottom, then the faces right, front, left, back.
    d.rectangle((8, 0, 15, 7), hair); d.rectangle((16, 0, 23, 7), skin)
    d.rectangle((0, 8, 31, 15), skin)
    d.rectangle((0, 8, 31, 9), hair); d.rectangle((24, 8, 31, 15), hair)
    d.point((10, 12), hexc('181818')); d.point((13, 12), hexc('181818'))
    d.rectangle((10, 14, 13, 14), hexc('54585a'))
    # Body, and each arm and leg: the player layout's boxes.
    d.rectangle((16, 16, 39, 31), suit)
    d.rectangle((40, 16, 55, 31), suit2)
    d.rectangle((0, 16, 15, 31), suit2)
    d.rectangle((32, 48, 47, 63), suit2)
    d.rectangle((16, 48, 31, 63), suit2)
    # The holster, worn on the body's right side, open and empty.
    d.rectangle((20, 26, 22, 30), hexc('28221c')); d.rectangle((20, 26, 22, 26), hexc('1c1814'))
    for box in ((16, 16, 39, 31), (40, 16, 55, 31), (0, 16, 15, 31), (32, 48, 47, 63), (16, 48, 31, 63), (0, 8, 31, 15)):
        for _ in range(14):
            im.putpixel((r.randint(box[0], box[2]), r.randint(box[1], box[3])), frost)
    return im


PALE, GOLD, RED, GREEN, GHOST = (159, 214, 232), (255, 214, 92), (232, 104, 92), (120, 200, 130), (170, 225, 245)
LETTERS = {'V': '101 101 101 101 010', 'I': '111 010 010 010 111', 'T': '111 010 010 010 010', 'A': '010 101 111 101 101',
           'L': '100 100 100 100 111', 'S': '011 100 010 001 110', 'N': '1001 1101 1011 1001 1001', 'O': '010 101 101 101 010',
           'E': '111 100 110 100 111'}


def lintel(colour):
    """A block of the wall with the lamp along its foot, six texels tall, as the Morgue's section draws it."""
    im = Image.open(BLOCK / 'vault_shell.png').convert('RGBA').crop((0, 0, 16, 16))
    ImageDraw.Draw(im).rectangle((0, 10, 15, 15), colour + (255,))
    return im


def plate(colour):
    im = Image.new('RGBA', (16, 16), colour + (255,))
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 15, 15), outline=(70, 40, 38, 255))
    for x in (5, 10):
        d.line((x, 1, x, 14), (70, 40, 38, 255))
    return im


def vent():
    im = Image.new('RGBA', (16, 16), PALE + (255,))
    d = ImageDraw.Draw(im)
    for y in range(2, 16, 4):
        d.rectangle((1, y, 14, y + 1), (40, 60, 70, 255))
    return im


def recorder(lamp):
    """The console: its pale housing, a dark screen and a lamp, red while it takes and pale green while it plays."""
    im = Image.new('RGBA', (16, 16), PALE + (255,))
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 15, 15), outline=(96, 134, 148, 255))
    d.rectangle((3, 3, 12, 8), (24, 32, 38, 255))
    d.rectangle((6, 11, 9, 13), lamp + (255,))
    return im


def key(bright):
    colour = GOLD if bright else tuple(c * 3 // 5 for c in GOLD)
    im = Image.new('RGBA', (16, 16), colour + (255,))
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 15, 15), outline=(90, 72, 26, 255))
    d.rectangle((6, 4, 9, 11), (30, 26, 16, 255))
    return im


def vitals():
    """The panel's sheet: VITALS, NONE and a pip, each thirty-two texels, drawn directly like the clock."""
    im = Image.new('RGBA', (96, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for x0, word, colour in ((0, 'VITALS', RED), (32, 'NONE', GREEN)):
        d.rectangle((x0, 0, x0 + 31, 31), (14, 17, 20, 255), colour + (255,))
        wide = sum(len(LETTERS[c].split()[0]) + 1 for c in word) - 1
        x = x0 + (32 - wide) // 2
        for letter in word:
            for y, row in enumerate(LETTERS[letter].split()):
                for i, bit in enumerate(row):
                    if bit == '1':
                        im.putpixel((x + i, 6 + y), colour + (255,))
            x += len(row) + 1
    d.rectangle((64, 0, 67, 3), RED + (255,))
    return im


def recording():
    """The technician's suit washed out to the pale of the mockup's recording, and see-through."""
    im = Image.open(ENTITY / 'vault_technician.png').convert('RGBA')
    for x in range(im.width):
        for y in range(im.height):
            r, g, b, a = im.getpixel((x, y))
            grey = (r + g + b) / 765 * .25 + .8
            im.putpixel((x, y), tuple(min(255, int(c * grey)) for c in GHOST) + (150 if a else 0,))
    return im


def main():
    noise('s', ['5b6064', '555a5e'], 1).save(BLOCK / 'vault_shell.png')
    noise('f', ['2f3438', '2a2f33'], 2).save(BLOCK / 'vault_floor.png')
    walkway().save(BLOCK / 'vault_walkway.png')
    noise('t', ['6e767c', '666e74'], 4).save(BLOCK / 'vault_steel.png')
    noise('m', ['4e5f6e', '465664'], 5).save(BLOCK / 'vault_cot_blanket.png')
    noise('p', ['c8ccc8', 'bec2be'], 6).save(BLOCK / 'vault_cot_pillow.png')
    noise('a', ['ded6be', 'd6cdb4'], 8).save(BLOCK / 'vault_paper.png')
    branded(False).save(BLOCK / 'vault_door_panel.png')
    branded(True).save(BLOCK / 'vault_last_door_panel.png')
    hazard(21, 0).save(BLOCK / 'vault_mark_hazard.png')
    for i, tile in enumerate(word('EXO > > >', 3, 22)):
        tile.save(BLOCK / f'vault_mark_exo_right_{i}.png')
    for i, tile in enumerate(word('< < < EXO', 3, 25)):
        tile.save(BLOCK / f'vault_mark_exo_left_{i}.png')
    for i, tile in enumerate(plain_word('ISS-4 ARGUS', 3, 28)):
        tile.save(BLOCK / f'vault_mark_argus_{i}.png')
    for i, tile in enumerate(warning()):
        tile.save(BLOCK / f'vault_mark_warning_{i}.png')
    for i, tile in enumerate(flag()):
        tile.save(BLOCK / f'vault_mark_flag_{i}.png')
    for index in range(6):
        for lamp in ('dark', 'lit', 'pulled'):
            glyph(index, lamp).save(BLOCK / f'vault_glyph_{index}_{lamp}.png')
    side, top = crate()
    side.save(BLOCK / 'vault_crate_side.png')
    top.save(BLOCK / 'vault_crate_top.png')
    noise('e', ['2e2822', '26211c'], 11).save(BLOCK / 'vault_case.png')
    noise('h', ['d6be50', 'cfb648'], 12).save(BLOCK / 'vault_hard_hat.png')
    seal_order().save(BLOCK / 'vault_seal_order.png')
    # Drawn directly, like the clock digits: 384 wide is no shape for the block atlas.
    plot().save(ENTITY / 'vault_system_plot.png')
    custodian().save(ENTITY / 'custodian.png')
    custodian_lens().save(ENTITY / 'custodian_lens.png')
    custodian_core().save(ENTITY / 'custodian_core.png')
    tally_lamp(True).save(BLOCK / 'vault_tally_lit.png')
    tally_lamp(False).save(BLOCK / 'vault_tally_dark.png')
    clock_face().save(ENTITY / 'vault_clock_face.png')
    # Drawn directly, not from the block atlas: a 44x5 sheet there would turn every block's mipmaps off.
    clock_digits().save(ENTITY / 'vault_clock_digits.png')
    technician().save(ENTITY / 'vault_technician.png')
    lintel(PALE).save(BLOCK / 'vault_lintel_shut.png')
    lintel(GREEN).save(BLOCK / 'vault_lintel_open.png')
    plate((120, 56, 50)).save(BLOCK / 'vault_plate_dark.png')
    plate(RED).save(BLOCK / 'vault_plate_lit.png')
    vent().save(BLOCK / 'vault_vent.png')
    recorder((52, 66, 72)).save(BLOCK / 'vault_recorder_idle.png')
    recorder(RED).save(BLOCK / 'vault_recorder_recording.png')
    recorder(GREEN).save(BLOCK / 'vault_recorder_playing.png')
    key(False).save(BLOCK / 'vault_key_idle.png')
    key(True).save(BLOCK / 'vault_key_held.png')
    noise('t', ['5c4a36', '544330', '60503a'], 21).save(BLOCK / 'vault_table.png')
    vitals().save(ENTITY / 'vault_vitals.png')
    recording().save(ENTITY / 'vault_recording.png')


main()

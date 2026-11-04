"""Textures of the Terminus Vault, from the approved mockups (vault-rooms-mockup.png, vault-contents-mockup.png).
Writes into the mod's assets. Run from anywhere: python3 docs/art/terminus/make_vault_textures.py
"""
import random
from pathlib import Path
from PIL import Image, ImageDraw

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


def main():
    noise('s', ['5b6064', '555a5e'], 1).save(BLOCK / 'vault_shell.png')
    noise('f', ['2f3438', '2a2f33'], 2).save(BLOCK / 'vault_floor.png')
    walkway().save(BLOCK / 'vault_walkway.png')
    noise('t', ['6e767c', '666e74'], 4).save(BLOCK / 'vault_steel.png')
    noise('m', ['4e5f6e', '465664'], 5).save(BLOCK / 'vault_cot_blanket.png')
    noise('p', ['c8ccc8', 'bec2be'], 6).save(BLOCK / 'vault_cot_pillow.png')
    noise('a', ['ded6be', 'd6cdb4'], 8).save(BLOCK / 'vault_paper.png')
    panel(False).save(BLOCK / 'vault_door_panel.png')
    panel(True).save(BLOCK / 'vault_last_door_panel.png')
    clock_face().save(BLOCK / 'vault_clock_face.png')
    clock_digits().save(BLOCK / 'vault_clock_digits.png')
    technician().save(ENTITY / 'vault_technician.png')


main()

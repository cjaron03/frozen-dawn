"""The drill hall redressed, at Minecraft scale (16 pixels a block), shown double size.

Owner, 2026-11-03, mid-pass: "the two door room could use better textures, maybe a little emitter on the top or something".
Run from anywhere: python3 docs/art/terminus/make_drill_hall_mockup.py
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
OUT = HERE / 'drill-hall-mockup.png'
TEX = HERE.parents[2] / 'src/main/resources/assets/frozendawn/textures/block'
B = 16
F = lambda s: ImageFont.truetype('/System/Library/Fonts/Menlo.ttc', s)
BG, INK, DIM = (24, 28, 33), (226, 232, 236), (150, 160, 167)
PALE, GOLD, RED, GREEN = (159, 214, 232), (255, 214, 92), (232, 104, 92), (120, 200, 130)
STEEL, DARK, TRIM, RIVET = (58, 64, 69), (24, 32, 38), (96, 134, 148), (120, 128, 134)


def tile(name):
    return Image.open(TEX / f'{name}.png').convert('RGB').crop((0, 0, B, B))


def wall(w, h, band=False):
    """Shell, the floor's row at the foot; with the red-lit rooms' hazard band at door height if asked."""
    im = Image.new('RGB', (w * B, h * B))
    for x in range(w):
        for y in range(h):
            im.paste(tile('vault_shell'), (x * B, y * B))
        im.paste(tile('vault_floor'), (x * B, (h - 1) * B))
        if band:
            im.paste(tile('vault_mark_hazard'), (x * B, 1 * B))
    return im


def console_block(lamp):
    """Steel housing, pale trim, a dark screen with a trace on it, and a row of switches."""
    im = Image.new('RGB', (B, B), STEEL)
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 15, 15), outline=TRIM)
    d.rectangle((2, 2, 13, 8), DARK)
    d.line((3, 6, 5, 6), PALE)
    d.line((5, 6, 6, 4), PALE)
    d.line((6, 4, 8, 7), PALE)
    d.line((8, 7, 12, 5), PALE)
    for x in (3, 6, 9):
        d.rectangle((x, 11, x + 1, 12), RIVET)
    d.rectangle((12, 11, 13, 12), lamp)
    return im


def emitter(d, x, y, lens):
    """On the middle block's top: a steel collar and a stubby lens, eight texels wide and five tall."""
    d.rectangle((x + 4, y - 2, x + 11, y - 1), STEEL)
    d.line((x + 4, y - 2, x + 11, y - 2), TRIM)
    d.rectangle((x + 6, y - 5, x + 9, y - 3), lens)
    d.point((x + 7, y - 5), (235, 245, 250))


def console(lens, lamp, beam=None):
    """Looking west at the console: three blocks long, one tall, on the floor before the wall."""
    im = wall(5, 5, band=True)
    d = ImageDraw.Draw(im)
    y = 3 * B
    for i in range(3):
        im.paste(console_block(lamp), ((i + 1) * B, y))
    x = 2 * B
    emitter(d, x, y, lens)
    if beam:
        for i in range(1, 22):
            if i % 3:
                d.point((x + 7 + (i % 2), y - 5 - i), beam)
    return im


def console_now():
    im = wall(5, 5)
    for i in range(3):
        im.paste(Image.open(TEX / 'vault_recorder_idle.png').convert('RGB'), ((i + 1) * B, 3 * B))
    return im


def key_block(held):
    """A lock box on the wall: steel, a gold plate, and the key itself, upright, turned a quarter when held."""
    im = Image.new('RGB', (B, B), STEEL)
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 15, 15), outline=(40, 44, 48))
    gold = GOLD if held else tuple(c * 3 // 5 for c in GOLD)
    d.rectangle((3, 3, 12, 12), gold)
    d.rectangle((4, 4, 11, 11), (30, 26, 16))
    if held:
        d.rectangle((4, 7, 11, 8), GOLD)
        d.rectangle((10, 6, 12, 9), GOLD)
    else:
        d.rectangle((7, 4, 8, 11), gold)
        d.rectangle((6, 3, 9, 5), gold)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        d.point((x, y), RIVET)
    return im


def key(held):
    im = wall(3, 4, band=True)
    im.paste(key_block(held), (B, 2 * B))
    return im


def key_now():
    im = wall(3, 4)
    im.paste(Image.open(TEX / 'vault_key_idle.png').convert('RGB'), (B, 2 * B))
    return im


def main():
    out = Image.new('RGB', (1000, 620), BG)
    d = ImageDraw.Draw(out)
    d.text((24, 12), 'Drill hall, redressed. Shown double size; 16 pixels a block.', INK, F(14))
    y = 40
    d.text((24, y), 'The console, looking west. Emitter on the middle block, its lens the state of the take.', INK, F(13))
    y += 22
    x = 24
    for im, label in ((console_now(), 'now'), (console(PALE, (52, 66, 72)), 'idle'), (console(RED, RED), 'recording'), (console(GREEN, GREEN, PALE), 'playing back')):
        out.paste(im.resize((im.width * 2, im.height * 2), Image.NEAREST), (x, y))
        d.text((x, y + 5 * B * 2 + 4), label, DIM, F(12))
        x += 5 * B * 2 + 20
    y += 5 * B * 2 + 34
    d.text((24, y), 'A key, on its wall. Steel lock box, gold plate; the key turns a quarter while held.', INK, F(13))
    y += 22
    x = 24
    for im, label in ((key_now(), 'now'), (key(False), 'waiting'), (key(True), 'held')):
        out.paste(im.resize((im.width * 2, im.height * 2), Image.NEAREST), (x, y))
        d.text((x, y + 4 * B * 2 + 4), label, DIM, F(12))
        x += 3 * B * 2 + 20
    d.text((x + 10, y + 10), 'Hazard band along the walls at door height,', DIM, F(12))
    d.text((x + 10, y + 28), 'as in the bridge hall and Watch Floor.', DIM, F(12))
    out.crop((0, 0, 1000, y + 4 * B * 2 + 30)).save(OUT)


if __name__ == '__main__':
    main()

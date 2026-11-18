"""Mockup of the ORSA Thermal Blanket item texture (CH-38), in hand, in the hotbar and in its tooltip.

Draft for owner approval, 2026-11-14; nothing here is in the game yet. The texture is 16 by 16, one pixel per model
unit, drawn as Minecraft item art: a folded emergency blanket, silver face out and its safety orange reverse showing at
the turned-down corner and along the folds. No letter or mark is drawn on it. Run from anywhere:
python3 docs/art/kevins-blanket/make_blanket_mockup.py
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
TEXTURE = HERE / 'orsa_thermal_blanket.png'
OUT = HERE / 'blanket-mockup.png'

C = {k: tuple(bytes.fromhex(v)) + (255,) for k, v in dict(
    o='1a1d21',   # outline
    d='6b747a',   # silver, shadowed
    s='9ea7ad',   # silver
    l='c9d0d3',   # silver, lit
    w='eef3f4',   # mylar glint
    r='a8481b',   # orange, shadowed
    O='e2702a',   # safety orange
    y='ff9b4e',   # orange, lit
).items()}

# Top right corner is dog-eared: folded down along the diagonal, its orange reverse lies on the silver face and
# shadows it. The front shows the stacked folds, orange peeking between them.
ROWS = [
    '................',
    '................',
    '..oooooooo......',
    '.olwllwllyo.....',
    '.ollslllwryo....',
    '.oslwllslrOyo...',
    '.ollslwlsrOOyo..',
    '.osllsllwrOOOyo.',
    '.osslsllsdddddo.',
    '.oddddddddddddo.',
    '.osllslllwllslo.',
    '.orOOyOOOOyOOro.',
    '.odsdsssdssdsdo.',
    '..oooooooooooo..',
    '................',
    '................',
]


def texture():
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(ROWS):
        assert len(row) == 16, y
        for x, ch in enumerate(row):
            if ch != '.':
                im.putpixel((x, y), C[ch])
    return im


def text(draw_target, xy, s, colour, scale=2, italic=False):
    """Pixel text: the bitmap default font drawn at 1x, sheared for italic, scaled up nearest-neighbour."""
    font = ImageFont.load_default_imagefont()
    w, h = font.getbbox(s)[2] + 3, 12
    layer = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(layer).text((1, 0), s, font=font, fill=colour)
    if italic:
        layer = layer.transform(layer.size, Image.AFFINE, (1, 0.25, -2, 0, 1, 0), Image.NEAREST)
    shadow = Image.new('RGBA', layer.size, (0, 0, 0, 0))
    shadow.paste((0, 0, 0, 255), mask=layer.split()[3].point(lambda a: 255 if a else 0))
    shadow = Image.blend(Image.new('RGBA', layer.size, (0, 0, 0, 0)), shadow, 0.45)
    for img, off in ((shadow, scale), (layer, 0)):
        draw_target.alpha_composite(img.resize((img.width * scale, img.height * scale), Image.NEAREST),
                                    (xy[0] + off, xy[1] + off))


def hotbar(tex):
    """Nine slots at GUI scale 2 (each slot 20 GUI px = 40 screen px), the blanket in slot 4, slot 4 selected."""
    s = 2
    bar = Image.new('RGBA', (182 * s, 22 * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(bar)
    d.rectangle((0, 0, bar.width - 1, bar.height - 1), fill=(24, 24, 24, 255))
    for i in range(9):
        x0 = (1 + i * 20) * s
        d.rectangle((x0, 1 * s, x0 + 20 * s - 1, 21 * s - 1), fill=(139, 139, 139, 255))
        d.rectangle((x0 + s, 2 * s, x0 + 19 * s - 1, 20 * s - 1), fill=(55, 55, 55, 200))
    sel = (3 * 20) * s
    d.rectangle((sel, 0, sel + 22 * s - 1, 22 * s - 1), outline=(240, 240, 240, 255), width=s)
    bar.alpha_composite(tex.resize((16 * s, 16 * s), Image.NEAREST), ((3 + 3 * 20) * s, 3 * s))
    return bar


def tooltip():
    s = 2
    font = ImageFont.load_default_imagefont()
    w, h = (font.getbbox('ORSA Thermal Blanket')[2] + 10) * s, 25 * s
    tip = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(tip)
    d.rectangle((s, 0, w - s - 1, h - 1), fill=(16, 0, 16, 240))
    d.rectangle((0, s, w - 1, h - s - 1), fill=(16, 0, 16, 240))
    d.rectangle((s, s, w - s - 1, h - s - 1), outline=(80, 0, 255, 120), width=s)
    text(tip, (4 * s, 2 * s), 'ORSA Thermal Blanket', (255, 255, 255, 255), s)
    text(tip, (4 * s, 13 * s), 'K.', (85, 85, 85, 255), s, italic=True)
    return tip


def main():
    tex = texture()
    tex.save(TEXTURE)
    big = tex.resize((128, 128), Image.NEAREST)
    canvas = Image.new('RGBA', (820, 300), (32, 37, 43, 255))
    d = ImageDraw.Draw(canvas)
    # Texture alone at 8x, on a checker so the transparent edge reads.
    for cy in range(16):
        for cx in range(16):
            d.rectangle((24 + cx * 8, 40 + cy * 8, 31 + cx * 8, 47 + cy * 8),
                        fill=(58, 64, 70, 255) if (cx + cy) % 2 else (46, 52, 58, 255))
    canvas.alpha_composite(big, (24, 40))
    # Grass-and-snow strip behind the hotbar, as in game.
    d.rectangle((180, 40, 800, 150), fill=(196, 214, 224, 255))
    d.rectangle((180, 150, 800, 200), fill=(120, 140, 150, 255))
    canvas.alpha_composite(hotbar(tex), (185 + 120, 120))
    canvas.alpha_composite(tooltip(), (430, 220))
    canvas.alpha_composite(tex.resize((32, 32), Image.NEAREST), (390, 228))
    text(canvas, (24, 12), '8x texture', (220, 220, 220, 255), 2)
    text(canvas, (190, 12), 'hotbar, GUI scale 2', (220, 220, 220, 255), 2)
    text(canvas, (390, 196), 'tooltip, GUI scale 2', (220, 220, 220, 255), 2)
    canvas.convert('RGB').save(OUT)
    print(OUT)


if __name__ == '__main__':
    main()

"""Mockup of the UN Emergency Evacuation Notice as a sterile corporate notice: its icon and its reading screen.

Draft for owner approval, 2026-11-17; nothing here is in the game yet. One loose portrait sheet, not the manual's
book: clean white paper, thin rules, the ORSA masthead and the issuing council, label and value fields, and a single
red bar for the alert. The manual's ink colours and blocky font carry over; the column is 180 pixels against the
vanilla book's 114, so the text wraps less. ORSA is everywhere (masthead, watermark, filing code) and the council
appears once, as a small seal that is a stand-in, not the real UN emblem. The paperwork shows it was scripted: an
unfilled auto-assign field, and a text approved in 2071, six years before the day. Kevin's page is the back of the
last sheet, with nothing on it but his pen. Drawn at GUI scale 2 with the game's own ascii font, read from the
Gradle cache's client jar (never copied into the repository). Each run also writes the approved icon, masthead,
watermark and seal into the game's assets. Run from anywhere:
python3 docs/art/kevins-blanket/make_notice_mockup.py
"""
import io
import zipfile
from pathlib import Path
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
ASSETS = ROOT / 'src/main/resources/assets/frozendawn/textures'
CLIENT = Path.home() / '.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar'
OUT = HERE / 'notice-mockup.png'
S = 2  # GUI scale

PAPER, BORDER, SHADOW = (247, 248, 246, 255), (196, 202, 205, 255), (0, 0, 0, 90)
INK, LABEL, RULE = (0x1F, 0x2A, 0x2C, 255), (0x7A, 0x84, 0x88, 255), (0xC9, 0xCF, 0xD2, 255)
ALERT, ALERT_DARK, WHITE = (200, 51, 43, 255), (143, 35, 29, 255), (255, 255, 255, 255)
PEN = (38, 52, 104, 255)
W, H = 208, 240      # one portrait sheet, in GUI pixels
M = 14               # margin
TEXT_W = W - 2 * M   # 180, against the vanilla book's 114


SMALL_PRINT = {}


class Font:
    """The game's ascii.png: 8x8 cells, each glyph as wide as its last lit column, one pixel between glyphs."""
    def __init__(self):
        with zipfile.ZipFile(CLIENT) as jar:
            self.sheet = Image.open(io.BytesIO(jar.read('assets/minecraft/textures/font/ascii.png'))).convert('RGBA')

    def glyph(self, ch):
        code = ord(ch) if ord(ch) < 256 else ord('?')
        cell = self.sheet.crop(((code % 16) * 8, (code // 16) * 8, (code % 16) * 8 + 8, (code // 16) * 8 + 8))
        if ch == ' ':
            return cell, 4
        lit = [x for x in range(8) for y in range(8) if cell.getpixel((x, y))[3]]
        return cell, (max(lit) + 2) if lit else 4

    def width(self, text, bold=False, small=False):
        full = sum(self.glyph(c)[1] + (1 if bold else 0) for c in text)
        return (full + 1) // 2 if small else full

    def draw(self, target, x, y, text, colour, bold=False, italic=False, small=False):
        if small:
            # Half-size print, as the game draws a 0.5-scaled string: at GUI scale 2 each font pixel is one screen
            # pixel, so it is drawn later onto the scaled sheet, at screen resolution.
            SMALL_PRINT.setdefault(id(target), []).append((x * S, y * S, text, colour))
            return
        for ch in text:
            cell, advance = self.glyph(ch)
            mask = cell.split()[3]
            for dx in ([0, 1] if bold else [0]):
                for row in range(8):
                    shift = (1 if row < 4 else 0) if italic else 0
                    strip = mask.crop((0, row, 8, row + 1))
                    target.paste(colour, (x + dx + shift, y + row), strip)
            x += advance + (1 if bold else 0)


def wrap(font, text, width, bold=False):
    lines = []
    for para in text.split('\n'):
        line = ''
        for word in para.split(' '):
            trial = word if not line else line + ' ' + word
            if font.width(trial, bold) <= width:
                line = trial
            else:
                lines.append(line)
                line = word
        lines.append(line)
    return lines


def sheet():
    im = Image.new('RGBA', (W + 4, H + 4), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle((3, 3, W + 3, H + 3), fill=SHADOW)
    d.rectangle((0, 0, W - 1, H - 1), fill=PAPER, outline=BORDER)
    return im, d


CODE = 'EVN-2042-0001 // ISSUED: ORSA // CONCUR: UNEC'
REVISION = 'REV. 9 // TEXT APPROVED 2071 // COPY 3,118,402,117'
UN_BLUE, UN_PALE = (92, 146, 210, 255), (214, 228, 243, 255)


def masthead(width, alpha=1.0):
    logo = Image.open(ASSETS / 'gui/patchouli/orsa_masthead.png').convert('RGBA')
    logo = logo.resize((width, round(width * logo.height / logo.width)), Image.LANCZOS)
    if alpha < 1.0:
        logo.putalpha(logo.split()[3].point(lambda v: int(v * alpha)))
    return logo


def council_seal(im, x, y):
    """A stand-in, not the real UN emblem: a pale blue disc with a plain white ring and one meridian."""
    d = ImageDraw.Draw(im)
    d.ellipse((x, y, x + 13, y + 13), fill=UN_BLUE)
    d.ellipse((x + 3, y + 3, x + 10, y + 10), outline=WHITE)
    d.line((x + 6, y + 3, x + 6, y + 10), fill=WHITE)
    d.line((x + 3, y + 6, x + 10, y + 6), fill=WHITE)


def control_strip(font, im, d):
    """Every printed page: the filing code, the revision and the copy number, small and grey, with a barcode."""
    y = H - 30
    d.line((M, y, W - M, y), fill=RULE)
    font.draw(im, M, y + 4, CODE, LABEL, small=True)
    font.draw(im, M, y + 13, REVISION, LABEL, small=True)
    bx = W - M - 30
    for i, bar in enumerate('1101001110100110110101'):
        if bar == '1':
            d.line((bx + i, y + 4, bx + i, y + 20), fill=INK)


def printed(font):
    """Page one: ORSA's masthead and watermark, the council's small seal, title, fields, the one red bar, the order."""
    im, d = sheet()
    mark = masthead(150, 0.07)
    im.alpha_composite(mark, ((W - mark.width) // 2, 112))
    logo = masthead(64)
    im.alpha_composite(logo, (M - 4, 8))
    council_seal(im, W - M - 13, 10)
    for i, line in enumerate(('UNITED NATIONS', 'EMERGENCY COUNCIL')):
        font.draw(im, W - M - 17 - font.width(line, small=True), 11 + i * 6, line, LABEL, small=True)
    y = 8 + logo.height
    d.line((M, y, W - M, y), fill=INK)
    y += 6
    font.draw(im, M, y, 'EVACUATION NOTICE', INK, bold=True)
    y += 14
    for label, value in (('Classification', 'PUBLIC'), ('Date', '[SYSTEM DATE ERROR]'),
                         ('Evac. point', '[AUTO-ASSIGN PENDING]')):
        font.draw(im, M, y, label, LABEL)
        font.draw(im, M + 70, y, value, INK)
        y += 10
    y += 4
    d.rectangle((M, y, W - M, y + 14), fill=ALERT)
    d.line((M, y + 14, W - M, y + 14), fill=ALERT_DARK)
    text = 'THIS IS NOT A DRILL.'
    font.draw(im, M + (TEXT_W - font.width(text, True)) // 2, y + 3, text, WHITE, bold=True)
    y += 22
    for line in wrap(font, 'By order of the United Nations Emergency Council, all civilians are to report to their '
                           'nearest designated evacuation point IMMEDIATELY.', TEXT_W):
        font.draw(im, M, y, line, INK)
        y += 10
    control_strip(font, im, d)
    return im


def handwritten(font):
    """Kevin's page: the back of the last sheet. No watermark, no code, no marks: only his pen."""
    im, d = sheet()
    y = 18
    jitter = [0, 1, 0, -1, 1, 0, 0, 1, -1, 0]
    paragraphs = ["If you're reading this, we missed you.", '',
                  'I went back. Three times. I checked every shelter, every basement, every locked door I could find.',
                  '', "I'm sorry.", '', '- Kevin, Logistics Division']
    for para in paragraphs:
        for n, line in enumerate(wrap(font, para, TEXT_W - 6) if para else ['']):
            x = M + 2 + jitter[(y // 10) % len(jitter)]
            for k, word in enumerate(line.split(' ')):
                font.draw(im, x, y + (jitter[(k + y) % len(jitter)] if k % 3 == 2 else 0), word, PEN, italic=True)
                x += font.width(word + ' ')
            y += 11
    return im


def icon():
    rows = [
        '................',
        '...oooooooooo...',
        '...owwwwwwwwoS..',
        '...owTTwKKKwoS..',
        '...owTTwwwwwoS..',
        '...oggggggggoS..',
        '...owwwwwwwwoS..',
        '...oRRRRRRRRoS..',
        '...orrrrrrrroS..',
        '...owwwwwwwwoS..',
        '...owkkkkkkwoS..',
        '...owwwwwwwwoS..',
        '...owkkkkkwwoS..',
        '...owwwwwwwwoS..',
        '...ooooooooooS..',
        '....SSSSSSSSSS..',
    ]
    colours = {k: tuple(bytes.fromhex(v)) + (255,) for k, v in dict(
        o='2b3236', w='f6f7f5', g='c9cfd2', k='4a5258', K='1f2a2c', T='4d93a6', R='c8332b', r='8f231d').items()}
    colours['S'] = (20, 24, 26, 110)
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        assert len(row) == 16, (y, row)
        for x, ch in enumerate(row):
            if ch != '.':
                im.putpixel((x, y), colours[ch])
    return im


def pager(font, sheet_image, number, total):
    """Screen chrome under the sheet, not print on it: arrows and the page count."""
    chrome = Image.new('RGBA', (W + 4, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(chrome)
    label = f'{number} / {total}'
    cx, y = W // 2, 6
    font.draw(chrome, cx - font.width(label) // 2, y, label, (220, 224, 226, 255))
    d.polygon([(cx - 30, y + 4), (cx - 24, y), (cx - 24, y + 8)], fill=(220, 224, 226, 255))
    d.polygon([(cx + 30, y + 4), (cx + 24, y), (cx + 24, y + 8)], fill=(220, 224, 226, 255))
    out = Image.new('RGBA', (sheet_image.width, sheet_image.height + 16 * S), (0, 0, 0, 0))
    out.alpha_composite(sheet_image, (0, 0))
    out.alpha_composite(chrome.resize((chrome.width * S, chrome.height * S), Image.NEAREST), (0, sheet_image.height))
    return out


def scaled(font, im):
    out = im.resize((im.width * S, im.height * S), Image.NEAREST)
    for x, y, text, colour in SMALL_PRINT.pop(id(im), []):
        font.draw(out, x, y, text, colour)
    return out


def export_assets():
    """The approved art, written into the game's assets: icon, masthead, watermark and the council's seal."""
    gui = ASSETS / 'gui/evacuation_notice'
    gui.mkdir(parents=True, exist_ok=True)
    icon().save(ASSETS / 'item/evacuation_notice.png')
    masthead(128).save(gui / 'masthead.png')        # drawn at 64 wide: two texels per GUI pixel
    masthead(150, 0.07).save(gui / 'watermark.png')  # drawn 1:1, already faint
    seal = Image.new('RGBA', (14, 14), (0, 0, 0, 0))
    council_seal(seal, 0, 0)
    seal.save(gui / 'seal.png')


def main():
    export_assets()
    font = Font()
    one = scaled(font, printed(font))
    last = scaled(font, handwritten(font))
    one = pager(font, one, 1, 4)
    last = pager(font, last, 4, 4)
    ico = icon()
    canvas = Image.new('RGBA', (one.width * 2 + 72 + 180, one.height + 48), (32, 37, 43, 255))
    canvas.alpha_composite(one, (24, 24))
    canvas.alpha_composite(last, (one.width + 48, 24))
    canvas.alpha_composite(ico.resize((128, 128), Image.NEAREST), (one.width * 2 + 80, 24))
    canvas.convert('RGB').save(OUT)
    print(OUT)


if __name__ == '__main__':
    main()

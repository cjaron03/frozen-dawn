"""Mockups for the Heart's last memory node (docs/heart-memory-node-plan.md, Task 0).

Drawn at GUI scale 4 on the client's default 854x480 window (a 1708x960 framebuffer on a
Retina screen, so a 427x240 GUI), with the vanilla font: ascii.png from the 1.21.1 client
jar. The panel keeps HeartMemoryNodeClient's geometry: 328 GUI px wide, the title at
startY, rows 13 px apart from startY + 17, the backing at alpha 122 of 0x030A11, the body
in 0xC8EAF3 and the closing line in the dimmer 0x9CCEDC. Each frame is the end of the
reveal, every line shown, before the 35-tick fade-out.

A line wider than 300 GUI px wraps onto a second row (the arrival line is 417 px); the
wrapped rows reveal together as one line.

  node5-main-run.png       the spec's run: arrived, came back, crossed, lost, you did
  node5-peaceful-run.png   no violation, no casualty: the deaths line instead
  node5-massacre-run.png   a permanent Orsathae: none of them came back
  node5-reveal-strip.png   the main run at 70, 210 and 420 ticks, one line every 70

Run: python3 docs/art/heart-memory-node/make_node5_mockups.py
"""
import io
import os
import zipfile

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
JAR = os.path.expanduser('~/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar')
S = 4
GW, GH = 427, 240
WRAP = 300

_jar = zipfile.ZipFile(JAR)
ASCII = Image.open(io.BytesIO(_jar.read('assets/minecraft/textures/font/ascii.png'))).convert('RGBA')
HOTBAR = Image.open(io.BytesIO(_jar.read(
    'assets/minecraft/textures/gui/sprites/hud/hotbar.png'))).convert('RGBA')

TITLE = 'MEMORY OF YOU'
ERASED = 'Now they do not remember you.'

MAIN = [
    'Day 4. You came down out of the cold. They felt your warmth before they saw you.',
    'You came back 12 times. They waited every time.',
    'Day 31. You crossed the line. They wrote it down.',
    'Day 38. Three of them did not come back.',
    'You did.',
    ERASED,
]
PEACEFUL = [
    'Day 4. You came down out of the cold. They felt your warmth before they saw you.',
    'You came back 12 times. They waited every time.',
    'They watched you die 7 times. They watched you return 7 times.',
    ERASED,
]
MASSACRE = [
    'Day 4. You came down out of the cold. They felt your warmth before they saw you.',
    'You came back 12 times. They waited every time.',
    'Day 31. You crossed the line. They wrote it down.',
    'Day 40. None of them came back.',
    'You did.',
    ERASED,
]


def glyph(ch):
    if ch == ' ':
        return [], 4
    i = ord(ch)
    ox, oy = (i % 16) * 8, (i // 16) * 8
    pix = [(x, y) for y in range(8) for x in range(8) if ASCII.getpixel((ox + x, oy + y))[3]]
    return pix, max((x for x, _ in pix), default=-1) + 2


def width_of(s):
    return sum(glyph(c)[1] for c in s)


def wrap(s):
    """Word wrap at WRAP GUI px, as Font.split does."""
    rows, row = [], ''
    for word in s.split(' '):
        trial = word if not row else row + ' ' + word
        if row and width_of(trial) > WRAP:
            rows.append(row)
            row = word
        else:
            row = trial
    rows.append(row)
    return rows


def draw_text(im, s, gx, gy, rgba):
    d = ImageDraw.Draw(im)
    x = gx
    for ch in s:
        pix, adv = glyph(ch)
        for px, py in pix:
            d.rectangle([(x + px) * S, (gy + py) * S, (x + px + 1) * S - 1, (gy + py + 1) * S - 1],
                        fill=rgba)
        x += adv


def centered(frame, s, cx, gy, rgb, alpha=1.0):
    a = round(255 * alpha)
    x = cx - width_of(s) // 2
    layer = Image.new('RGBA', frame.size, (0, 0, 0, 0))
    sc = (rgb & 0xFCFCFC) >> 2
    draw_text(layer, s, x + 1, gy + 1, ((sc >> 16) & 255, (sc >> 8) & 255, sc & 255, a))
    draw_text(layer, s, x, gy, ((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, a))
    frame.alpha_composite(layer)


def fill(im, x0, y0, x1, y1, argb):
    a, r, g, b = (argb >> 24) & 255, (argb >> 16) & 255, (argb >> 8) & 255, argb & 255
    im.alpha_composite(Image.new('RGBA', ((x1 - x0) * S, (y1 - y0) * S), (r, g, b, a)),
                       (x0 * S, y0 * S))


def backdrop():
    """A dark, blocky Heart chamber: 16 px stone blocks in the cold blue the Heart lights."""
    im = Image.new('RGBA', (GW * S, GH * S), (0, 0, 0, 255))
    d = ImageDraw.Draw(im)
    for by in range(0, GH, 16):
        for bx in range(0, GW, 16):
            shade = 18 + ((bx * 7 + by * 13) // 16 % 5) * 3
            d.rectangle([bx * S, by * S, (bx + 16) * S - 1, (by + 16) * S - 1],
                        fill=(shade - 6, shade, shade + 10, 255))
    hb = HOTBAR.resize((HOTBAR.width * S, HOTBAR.height * S), Image.NEAREST)
    im.alpha_composite(hb, ((GW // 2 - HOTBAR.width // 2) * S, (GH - HOTBAR.height) * S))
    return im


def panel(lines, shown=None):
    """The node 5 panel with the first `shown` lines (all by default) fully faded in."""
    frame = backdrop()
    lines = lines[:shown] if shown else lines
    rows = [(row, line == ERASED) for line in lines for row in wrap(line)]
    cx = GW // 2
    start_y = max(44, GH // 2 - 56)
    fill(frame, cx - 164, start_y - 13, cx + 164, start_y + 18 + len(rows) * 13,
         122 << 24 | 0x030A11)
    centered(frame, TITLE, cx, start_y, 0x7DEBFF)
    for index, (row, last) in enumerate(rows):
        centered(frame, row, cx, start_y + 17 + index * 13,
                 0x9CCEDC if last else 0xC8EAF3)
    return frame


def main():
    for name, lines in (('node5-main-run.png', MAIN), ('node5-peaceful-run.png', PEACEFUL),
                        ('node5-massacre-run.png', MASSACRE)):
        panel(lines).convert('RGB').save(os.path.join(HERE, name))
    shots = [panel(MAIN, 1), panel(MAIN, 3), panel(MAIN, 6)]
    gap = 8 * S
    strip = Image.new('RGB', (shots[0].width, len(shots) * shots[0].height + gap * 2), (60, 60, 60))
    for n, shot in enumerate(shots):
        strip.paste(shot.convert('RGB'), (0, n * (shot.height + gap)))
    strip.save(os.path.join(HERE, 'node5-reveal-strip.png'))


if __name__ == '__main__':
    main()

"""Mockups for the Thaeven loop (docs/thaeven-loop-plan.md, Task 0).

Draws two frames at GUI scale 3 on a 1278x720 window (a 426x240 GUI), with the
vanilla font and block textures read from the 1.21.1 client jar:

  opening-flicker-mockup.png  the opening's black lead-in with the raw lines (Ruling 1)
  heart-beat-mockup.png       a Heart takeover with the beat band (Ruling 5)

Run: python3 docs/art/thaeven-loop/make_loop_mockups.py
"""
import io
import os
import zipfile

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..'))
JAR = os.path.expanduser('~/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar')
MOD = os.path.join(ROOT, 'src/main/resources/assets/frozendawn/textures')
S = 3
GW, GH = 426, 240

_jar = zipfile.ZipFile(JAR)


def jar_png(path):
    return Image.open(io.BytesIO(_jar.read(path))).convert('RGBA')


ASCII = jar_png('assets/minecraft/textures/font/ascii.png')


def glyph(ch):
    if ch == ' ':
        return None, 4
    i = ord(ch)
    cell = ASCII.crop(((i % 16) * 8, (i // 16) * 8, (i % 16) * 8 + 8, (i // 16) * 8 + 8))
    width = 0
    for x in range(8):
        if any(cell.getpixel((x, y))[3] for y in range(8)):
            width = x + 1
    return cell, width + 1


def text_width(s):
    return sum(glyph(c)[1] for c in s)


def draw_text(im, s, gx, gy, rgb, alpha=1.0, italic=False, shadow=True):
    """Draws as Font does: a shadow at +1,+1 in (rgb & 0xFCFCFC) >> 2, italic as a 0.25 px/row shear."""
    passes = []
    if shadow:
        passes.append((1, ((rgb & 0xFCFCFC) >> 2)))
    passes.append((0, rgb))
    for off, color in passes:
        r, g, b = (color >> 16) & 255, (color >> 8) & 255, color & 255
        a = round(255 * alpha)
        x = gx + off
        for ch in s:
            cell, adv = glyph(ch)
            if cell is not None:
                for py in range(8):
                    for px in range(8):
                        if not cell.getpixel((px, py))[3]:
                            continue
                        for sy in range(S):
                            row = py + sy / S
                            shear = (1.0 - 0.25 * row) if italic else 0.0
                            dx = round((x + px + shear) * S)
                            dy = (gy + off + py) * S + sy
                            layer = Image.new('RGBA', (S, 1), (r, g, b, a))
                            im.alpha_composite(layer, (dx, dy))
            x += adv


def centered(im, s, cx, gy, rgb, alpha=1.0, italic=False):
    draw_text(im, s, cx - text_width(s) // 2, gy, rgb, alpha, italic)


def fill(im, x0, y0, x1, y1, argb):
    a, r, g, b = (argb >> 24) & 255, (argb >> 16) & 255, (argb >> 8) & 255, argb & 255
    im.alpha_composite(Image.new('RGBA', ((x1 - x0) * S, (y1 - y0) * S), (r, g, b, a)), (x0 * S, y0 * S))


def opening_frame(dropout=False):
    im = Image.new('RGBA', (GW * S, GH * S), (0, 0, 0, 255))
    if not dropout:
        y = GH // 2 - 9
        centered(im, 'Vel-thae thaeven.', GW // 2, y, 0xE7F7F7, italic=True)
        centered(im, 'Vel-thae thaeven.', GW // 2, y + 10, 0xE7F7F7, italic=True)
    return im


def tile(name, mod=False):
    if mod:
        return Image.open(os.path.join(MOD, 'block', name + '.png')).convert('RGBA').crop((0, 0, 16, 16))
    return jar_png('assets/minecraft/textures/block/%s.png' % name).crop((0, 0, 16, 16))


def heart_backdrop():
    """A blocky stand-in for the arena: 16 px per block at 1:1 GUI pixels, so a block is 48 screen px."""
    bg = Image.new('RGBA', (GW, GH), (8, 12, 18, 255))
    mass, rib, residue = tile('aggregate_mass', True), tile('aggregate_rib', True), tile('aggregate_residue', True)
    ice, deep = tile('packed_ice'), tile('deepslate_tiles')
    for bx in range(0, GW, 16):
        for by in range(0, GH, 16):
            col, row = bx // 16, by // 16
            if row >= 11:
                t = ice if (col + row) % 3 else deep
            elif col in (6, 7, 18, 19) or (row in (2, 3) and 6 <= col <= 19):
                t = rib
            elif 8 <= col <= 17 and 4 <= row <= 10:
                t = mass if (col * 7 + row) % 5 else residue
            else:
                t = deep
            bg.alpha_composite(t, (bx, by))
    shade = Image.new('RGBA', (GW, GH), (4, 8, 14, 150))
    bg.alpha_composite(shade)
    return bg.resize((GW * S, GH * S), Image.NEAREST)


def heart_frame():
    im = heart_backdrop()
    # Cognitive Load at 100% with the resistance bar, as CognitiveLoadClientState.render draws them.
    bw, x, y = 112, (GW - 112) // 2, 18
    icon = Image.open(os.path.join(MOD, 'gui', 'cognitive_load_4.png')).convert('RGBA').resize((22 * S, 22 * S), Image.NEAREST)
    icon.putalpha(icon.getchannel('A').point(lambda v: round(v * 0.9)))
    im.alpha_composite(icon, ((x - 29) * S, (y - 8) * S))
    draw_text(im, 'COGNITIVE LOAD', x, y - 10, 0x76A8C5, shadow=False)
    fill(im, x - 1, y - 1, x + bw + 1, y + 7, 0xD9070B12)
    fill(im, x, y, x + bw, y + 6, 0xE31A3854)
    fill(im, x, y, x + bw, y + 1, 0xE353CDE3)
    fill(im, x + bw - 1, y, x + bw, y + 6, 0xFF79EDFF)
    draw_text(im, '100%', x + bw + 6, y - 1, 0x9BB7C8, shadow=False)
    ry, rf = y + 17, 37
    draw_text(im, 'RESISTANCE', x, ry - 9, 0xC5DDE8, shadow=False)
    fill(im, x - 1, ry - 1, x + bw + 1, ry + 4, 0xD9070B12)
    fill(im, x, ry, x + rf, ry + 3, 0xE345A7BA)
    fill(im, x, ry, x + rf, ry + 1, 0xF09CF5FF)
    # Crosshair and hotbar, for scale.
    cross = jar_png('assets/minecraft/textures/gui/sprites/hud/crosshair.png').resize((15 * S, 15 * S), Image.NEAREST)
    im.alpha_composite(cross, ((GW // 2 - 7) * S, (GH // 2 - 7) * S))
    bar = jar_png('assets/minecraft/textures/gui/sprites/hud/hotbar.png')
    im.alpha_composite(bar.resize((bar.width * S, bar.height * S), Image.NEAREST), ((GW // 2 - 91) * S, (GH - 22) * S))
    # The beat band (Ruling 5): MasterArchitectFourthWallMoment's band, at full hold.
    cy, half = round(GH * 0.68), max(22, GH // 28)
    fill(im, 0, cy - half, GW, cy + half, (132 << 24) | (3 << 16) | (8 << 8) | 12)
    centered(im, 'THAE IVEN', GW // 2, cy - 13, 0x00AAAA, alpha=0.72)
    centered(im, 'The warm-one crossed here.', GW // 2, cy + 4, 0xE7F7F7, italic=True)
    return im


def main():
    opening_frame().convert('RGB').save(os.path.join(HERE, 'opening-flicker-mockup.png'))
    heart_frame().convert('RGB').save(os.path.join(HERE, 'heart-beat-mockup.png'))


if __name__ == '__main__':
    main()

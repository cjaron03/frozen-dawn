"""Mockups for the Thaeven loop (docs/thaeven-loop-plan.md, Task 0), revised 2026-11-09.

The owner asked for the books' ink animation, made to read alien. Both frames reuse the
archive's two pieces exactly: ThaevenInk.morph (raw words fracture through the glyphs
"⟟⟐ϟ·/" into English, word by word in a stable scattered order) and ThaevenLoreScreen's
ghost warp (a cold and a warm copy of each line drifting apart, settling as the ink
resolves). On the dark overlays the ghosts take the band's aqua and an ember, and they
never fully settle.

Drawn at GUI scale 4 on a 1280x720 window (a 320x180 GUI), 20 frames a second, one
frame per tick, with the vanilla font: ascii.png from the 1.21.1 client jar, and the
fracture glyphs from the unifont asset, at half size as the game draws it.

  opening-flicker-mockup.gif  the opening's black lead-in with the raw lines (Ruling 1)
  heart-beat-mockup.gif       a Heart takeover with the beat band (Ruling 5)

Run: python3 docs/art/thaeven-loop/make_loop_mockups.py
"""
import io
import json
import math
import os
import zipfile

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..'))
JAR = os.path.expanduser('~/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar')
ASSETS = os.path.expanduser('~/.gradle/caches/neoformruntime/assets')
MOD = os.path.join(ROOT, 'src/main/resources/assets/frozendawn/textures')
S = 4
GW, GH = 320, 180
FRACTURE = '⟟⟐ϟ·/'

_jar = zipfile.ZipFile(JAR)


def jar_png(path):
    return Image.open(io.BytesIO(_jar.read(path))).convert('RGBA')


def _unifont():
    index = json.load(open(os.path.join(ASSETS, 'indexes', '17.json')))['objects']
    sha = index['minecraft/font/unifont.zip']['hash']
    with zipfile.ZipFile(os.path.join(ASSETS, 'objects', sha[:2], sha)) as z:
        name = next(n for n in z.namelist() if n.endswith('.hex'))
        wanted = {'%04X' % ord(c) for c in FRACTURE}
        glyphs = {}
        for line in z.read(name).decode().splitlines():
            code, bits = line.split(':')
            if code in wanted:
                glyphs[chr(int(code, 16))] = bits
        return glyphs


ASCII = jar_png('assets/minecraft/textures/font/ascii.png')
UNIFONT = _unifont()


def glyph(ch):
    """(pixels, advance, pixel size in GUI px): pixels are (x, y) with y in 0..7 rows of GUI pixels."""
    if ch == ' ':
        return [], 4, 1.0
    if ord(ch) < 128:
        i = ord(ch)
        ox, oy = (i % 16) * 8, (i // 16) * 8
        pix = [(x, y) for y in range(8) for x in range(8) if ASCII.getpixel((ox + x, oy + y))[3]]
        width = max((x for x, _ in pix), default=-1) + 1
        return pix, width + 1, 1.0
    bits = UNIFONT[ch]
    cols = len(bits) * 4 // 16
    rows = [int(bits[r * cols // 4:(r + 1) * cols // 4], 16) for r in range(16)]
    pix = [(x, y) for y in range(16) for x in range(cols) if rows[y] >> (cols - 1 - x) & 1]
    left = min(x for x, _ in pix)
    right = max(x for x, _ in pix)
    pix = [((x - left) / 2.0, (y - 1) / 2.0) for x, y in pix]
    return pix, (right - left + 1) / 2.0 + 1, 0.5


def width_of(s):
    return sum(glyph(c)[1] for c in s)


def draw_text(layer, s, gx, gy, rgba, italic=False, jitter=None):
    """Font-style drawing onto one layer: italic is a 0.25 px/row shear, as BakedGlyph does."""
    d = ImageDraw.Draw(layer)
    x = gx
    for n, ch in enumerate(s):
        pix, adv, size = glyph(ch)
        jy = jitter(n) if jitter else 0
        for px, py in pix:
            for sub in range(int(S * size)):
                row = py + sub / S
                shear = (1.0 - 0.25 * row) if italic else 0.0
                dx = round((x + px + shear) * S)
                dy = round((gy + jy + py) * S) + sub
                d.rectangle([dx, dy, dx + int(S * size) - 1, dy], fill=rgba)
        x += adv


def comp(frame, s, cx, gy, rgb, alpha, italic=False, shadow=True, dx=0, dy=0, jitter=None):
    """Centered string with its drop shadow, composited at alpha."""
    if alpha <= 0.01:
        return
    a = round(255 * min(1.0, alpha))
    x = cx - round(width_of(s) / 2) + dx
    layer = Image.new('RGBA', frame.size, (0, 0, 0, 0))
    if shadow:
        sc = (rgb & 0xFCFCFC) >> 2
        draw_text(layer, s, x + 1, gy + dy + 1, ((sc >> 16) & 255, (sc >> 8) & 255, sc & 255, a), italic, jitter)
    draw_text(layer, s, x, gy + dy, ((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, a), italic, jitter)
    frame.alpha_composite(layer)


def fill(im, x0, y0, x1, y1, argb):
    a, r, g, b = (argb >> 24) & 255, (argb >> 16) & 255, (argb >> 8) & 255, argb & 255
    im.alpha_composite(Image.new('RGBA', ((x1 - x0) * S, (y1 - y0) * S), (r, g, b, a)), (x0 * S, y0 * S))


# --- ThaevenInk.morph, ported exactly ---------------------------------------------------------

def _i32(v):
    v &= 0xFFFFFFFF
    return v - (1 << 32) if v & 0x80000000 else v


def _rotl(v, n):
    v &= 0xFFFFFFFF
    return _i32((v << n) | (v >> (32 - n))) if n else _i32(v)


def _clamp(v, lo, hi):
    return max(lo, min(hi, v))


def _smooth(v):
    return v * v * v * (v * (v * 6 - 15) + 10)


def morph(raw, target, progress, page):
    src = raw.split()
    out, wi, cur = [], 0, 0
    while cur < len(target):
        if target[cur].isspace():
            out.append(target[cur])
            cur += 1
            continue
        end = cur + 1
        while end < len(target) and not target[end].isspace():
            end += 1
        out.append(_morph_word(src[wi % len(src)], target[cur:end], progress, page, wi))
        wi += 1
        cur = end
    return ''.join(out)


def _morph_word(source, target, progress, page, wi):
    h = _i32(_rotl(_i32((wi + 1) * 0x45D9F3B), wi & 15) ^ _i32(page * 0x9E3779B9))
    start = 0.10 + (h % 1000) / 1000.0 * 0.58
    local = _clamp((progress - start) / 0.30, 0.0, 1.0)
    if local <= 0:
        return source
    if local >= 1:
        return target
    length = max(1, round(len(source) + (len(target) - len(source)) * _smooth(local)))
    res = []
    for i in range(length):
        cs = i / max(1, length) * 0.42
        cp = _clamp((local - cs) / 0.58, 0.0, 1.0)
        sg = source[min(len(source) - 1, round(i * (len(source) - 1.0) / max(1.0, length - 1.0)))]
        tg = target[min(len(target) - 1, round(i * (len(target) - 1.0) / max(1.0, length - 1.0)))]
        if cp < 0.38:
            res.append(sg)
        elif cp < 0.72:
            res.append(FRACTURE[(h + i * 7) % len(FRACTURE)])
        else:
            res.append(tg)
    return ''.join(res)


def fractured(raw, seed):
    """Every letter caught mid-fracture: the morph's middle glyphs, in its own stable order."""
    return ''.join(c if c.isspace() else FRACTURE[(seed * 31 + i * 7) % len(FRACTURE)] for i, c in enumerate(raw))


# --- The archive's ghost warp, with the overlay's colors -------------------------------------

AQUA, EMBER, WHITE = 0x60D8DF, 0xE08A4E, 0xE7F7F7


def warp(age, line, unrest):
    wx = round((math.sin(age * 0.105 + line * 1.37) * 3.0 + math.sin(age * 0.31 + line * 1.73) * 1.5) * unrest)
    wy = round(math.sin(age * 0.16 + line * 0.81) * 1.25 * unrest)
    return wx, wy


def inked_line(frame, text, cx, gy, age, line, unrest, alpha):
    """ThaevenLoreScreen.renderPage's three draws: cold ghost, warm ghost, then the line."""
    wx, wy = warp(age, line, unrest)
    ghost = alpha * (0.22 + 0.40 * unrest)
    comp(frame, text, cx, gy, AQUA, ghost, italic=True, shadow=False, dx=wx - 1, dy=wy)
    comp(frame, text, cx, gy, EMBER, ghost, italic=True, shadow=False, dx=-wx + 1, dy=-wy)
    comp(frame, text, cx, gy, WHITE, alpha, italic=True, dx=round(wx / 3))


# --- Frames -----------------------------------------------------------------------------------

RAW = 'Vel-thae thaeven.'


def opening_frames():
    """The 40-tick lead-in: lines on pending-delay ticks 30..11, dropping to fracture on 2, 4 and 17."""
    frames = []
    for pending in range(40, -4, -1):
        im = Image.new('RGBA', (GW * S, GH * S), (0, 0, 0, 255))
        shown = 30 - pending
        if 10 < pending <= 30:
            y = GH // 2 - 9
            for line in range(2):
                text = fractured(RAW, line + shown) if shown in (2, 4, 17) else RAW
                inked_line(im, text, GW // 2, y + line * 10, shown + 40, line, 1.0, 1.0)
        frames.append(im.convert('RGB'))
    return frames


def tile(name, mod=False):
    if mod:
        return Image.open(os.path.join(MOD, 'block', name + '.png')).convert('RGBA').crop((0, 0, 16, 16))
    return jar_png('assets/minecraft/textures/block/%s.png' % name).crop((0, 0, 16, 16))


def heart_base():
    """A blocky stand-in for the arena, 16 px per block, with the load HUD at 100%."""
    bg = Image.new('RGBA', (GW, GH), (8, 12, 18, 255))
    mass, rib, residue = tile('aggregate_mass', True), tile('aggregate_rib', True), tile('aggregate_residue', True)
    ice, deep = tile('packed_ice'), tile('deepslate_tiles')
    for bx in range(0, GW, 16):
        for by in range(0, GH, 16):
            col, row = bx // 16, by // 16
            if row >= 8:
                t = ice if (col + row) % 3 else deep
            elif col in (5, 6, 13, 14) or (row in (2, 3) and 5 <= col <= 14):
                t = rib
            elif 7 <= col <= 12 and 4 <= row <= 7:
                t = mass if (col * 7 + row) % 5 else residue
            else:
                t = deep
            bg.alpha_composite(t, (bx, by))
    bg.alpha_composite(Image.new('RGBA', (GW, GH), (4, 8, 14, 150)))
    im = bg.resize((GW * S, GH * S), Image.NEAREST)
    bw, x, y = 112, (GW - 112) // 2, 18
    icon = Image.open(os.path.join(MOD, 'gui', 'cognitive_load_4.png')).convert('RGBA').resize((22 * S, 22 * S), Image.NEAREST)
    icon.putalpha(icon.getchannel('A').point(lambda v: round(v * 0.9)))
    im.alpha_composite(icon, ((x - 29) * S, (y - 8) * S))
    comp(im, 'COGNITIVE LOAD', x + width_of('COGNITIVE LOAD') / 2, y - 10, 0x76A8C5, 1.0, shadow=False)
    fill(im, x - 1, y - 1, x + bw + 1, y + 7, 0xD9070B12)
    fill(im, x, y, x + bw, y + 6, 0xE31A3854)
    fill(im, x, y, x + bw, y + 1, 0xE353CDE3)
    fill(im, x + bw - 1, y, x + bw, y + 6, 0xFF79EDFF)
    comp(im, '100%', x + bw + 6 + width_of('100%') / 2, y - 1, 0x9BB7C8, 1.0, shadow=False)
    ry, rf = y + 17, 37
    comp(im, 'RESISTANCE', x + width_of('RESISTANCE') / 2, ry - 9, 0xC5DDE8, 1.0, shadow=False)
    fill(im, x - 1, ry - 1, x + bw + 1, ry + 4, 0xD9070B12)
    fill(im, x, ry, x + rf, ry + 3, 0xE345A7BA)
    fill(im, x, ry, x + rf, ry + 1, 0xF09CF5FF)
    cross = jar_png('assets/minecraft/textures/gui/sprites/hud/crosshair.png').resize((15 * S, 15 * S), Image.NEAREST)
    im.alpha_composite(cross, ((GW // 2 - 7) * S, (GH // 2 - 7) * S))
    bar = jar_png('assets/minecraft/textures/gui/sprites/hud/hotbar.png')
    im.alpha_composite(bar.resize((bar.width * S, bar.height * S), Image.NEAREST), ((GW // 2 - 91) * S, (GH - 22) * S))
    return im


HOLD, FADE, INK = 80, 10, 50


def heart_frames():
    """One beat: 10-tick fade in, 80-tick hold, 10-tick fade out. The ink resolves over the first 50 ticks."""
    base = heart_base()
    cy, half = round(GH * 0.68), max(22, GH // 28)
    frames = []
    for t in range(HOLD + 2 * FADE + 6):
        im = base.copy()
        alpha = _clamp(min(t / FADE, (HOLD + 2 * FADE - t) / FADE), 0.0, 1.0)
        if alpha > 0:
            fill(im, 0, cy - half, GW, cy + half, (round(132 * alpha) << 24) | (3 << 16) | (8 << 8) | 12)
            label = 'THAE IVEN' if t % 23 not in (5, 6) else fractured('THAE IVEN', t)
            comp(im, label, GW // 2, cy - 13, 0x00AAAA, alpha * 0.72)
            progress = _clamp(t / INK, 0.0, 1.0)
            text = morph(RAW, 'The warm-one crossed here.', progress, 0)
            unrest = max(0.30, 1.0 - _smooth(progress))
            inked_line(im, text, GW // 2, cy + 4, t, 0, unrest, alpha)
        frames.append(im.convert('RGB'))
    return frames


def save_gif(frames, name):
    """One shared palette, no dithering, so the pixels stay as drawn."""
    sheet = Image.new('RGB', (frames[0].width, frames[0].height * 3))
    for i, f in enumerate((frames[0], frames[len(frames) // 3], frames[len(frames) // 2])):
        sheet.paste(f, (0, i * f.height))
    palette = sheet.quantize(colors=255, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    out = [f.quantize(palette=palette, dither=Image.Dither.NONE) for f in frames]
    out[0].save(os.path.join(HERE, name), save_all=True, append_images=out[1:], duration=50, loop=0,
                disposal=1)


def main():
    save_gif(opening_frames(), 'opening-flicker-mockup.gif')
    save_gif(heart_frames(), 'heart-beat-mockup.gif')


if __name__ == '__main__':
    main()

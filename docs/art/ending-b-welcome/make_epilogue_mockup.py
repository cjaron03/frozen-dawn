"""Mockup for the Ending B welcome (docs/ending-b-welcome-plan.md, Task 0).

A strip of epilogue frames at GUI scale 2 on Minecraft's default 854x480 window (a 427x240
GUI), with the vanilla font from the 1.21.1 client jar. The panels port
FrozenDawnEndingScreen.drawEndingTerminalPanel and drawComplianceStatic exactly.

Top row: Ending A's INCOMING SIGNAL frame beside Ending B's, which now match.
Bottom row: what follows in Ending B only: the compliance static, the ACCESS CONTROL
panel, the held black (Ruling 2) and the The End? card (Ruling 3).

Run: python3 docs/art/ending-b-welcome/make_epilogue_mockup.py
"""
import io
import os
import zipfile

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
JAR = os.path.expanduser('~/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar')
S = 2
GW, GH = 427, 240

ASCII = Image.open(io.BytesIO(zipfile.ZipFile(JAR).read('assets/minecraft/textures/font/ascii.png'))).convert('RGBA')


def glyph(ch):
    if ch == ' ':
        return [], 4
    i = ord(ch)
    ox, oy = (i % 16) * 8, (i // 16) * 8
    pix = [(x, y) for y in range(8) for x in range(8) if ASCII.getpixel((ox + x, oy + y))[3]]
    return pix, max((x for x, _ in pix), default=-1) + 2


def width_of(s):
    return sum(glyph(c)[1] for c in s)


def argb_rgba(argb):
    return (argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >> 24) & 255


def text(frame, s, gx, gy, argb, shadow=False):
    layer = Image.new('RGBA', frame.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    passes = []
    if shadow:
        sc = (argb & 0xFF000000) | ((argb & 0xFCFCFC) >> 2)
        passes.append((1, sc))
    passes.append((0, argb))
    for off, color in passes:
        x = gx + off
        for ch in s:
            pix, adv = glyph(ch)
            for px, py in pix:
                d.rectangle([(x + px) * S, (gy + off + py) * S, (x + px + 1) * S - 1, (gy + off + py + 1) * S - 1],
                            fill=argb_rgba(color))
            x += adv
    frame.alpha_composite(layer)


def wrap(s, width):
    lines, cur = [], ''
    for word in s.split(' '):
        trial = word if not cur else cur + ' ' + word
        if width_of(trial) <= width:
            cur = trial
        else:
            lines.append(cur)
            cur = word
    lines.append(cur)
    return lines


def fill(frame, x0, y0, x1, y1, argb):
    frame.alpha_composite(Image.new('RGBA', ((x1 - x0) * S, (y1 - y0) * S), argb_rgba(argb)), (x0 * S, y0 * S))


def apply_alpha(color, scale):
    a = max(0, min(255, round(((color >> 24) & 255) * scale)))
    return (color & 0xFFFFFF) | (a << 24)


def hash01(seed):
    x = (seed * 1664525 + 1013904223) & 0xFFFFFFFF
    x ^= x >> 16
    return (x & 0x00FFFFFF) / float(0x01000000)


def black():
    return Image.new('RGBA', (GW * S, GH * S), (0, 0, 0, 255))


def terminal_panel(title, body, title_c, body_c, accent_c, age=30):
    f = black()
    pw, ph = min(520, GW - 32), 64
    px, py = (GW - pw) // 2, GH // 2 - ph // 2
    fill(f, px, py, px + pw, py + ph, 0xA008120F)
    fill(f, px + 2, py + 2, px + pw - 2, py + ph - 2, 0x7A10231D)
    fill(f, px + 6, py + 6, px + 8, py + ph - 6, accent_c)
    fill(f, px + 14, py + 8, px + pw - 10, py + 9, 0x5526F6B0)
    scan = py + 11 + age % max(1, ph - 18)
    fill(f, px + 14, scan, px + pw - 12, scan + 1, 0x3344E6A8)
    text(f, '[ORSA] ' + title, px + 16, py + 8, title_c)
    for n, line in enumerate(wrap(body, pw - 32)):
        text(f, line, px + 16, py + 28 + n * 9, body_c)
    return f


def compliance_static(local=11):
    f = black()
    pw, ph = min(520, GW - 32), 82
    px, py = (GW - pw) // 2, GH // 2 - ph // 2
    fill(f, px, py, px + pw, py + ph, 0xA0180708)
    fill(f, px + 2, py + 2, px + pw - 2, py + ph - 2, 0x80100508)
    fill(f, px + 6, py + 6, px + 8, py + ph - 6, 0xCCFF5C5C)
    seed = local * 97
    for i in range(44):
        roll = hash01(seed + i * 31)
        y = py + 8 + round(hash01(seed + i * 47) * (ph - 16))
        x = px + 14 + round(hash01(seed + i * 71) * (pw - 44))
        ln = 8 + round(hash01(seed + i * 83) * 82)
        c = 0xFFFFC0C0 if roll < 0.35 else 0xFFFF5C5C if roll < 0.72 else 0xFF241014
        fill(f, x, y, min(px + pw - 14, x + ln), y + 1, c)
    for i in range(18):
        y = py + 12 + i * 4
        fill(f, px + 12, y, px + pw - 12, y + 1, 0x2220F0FF if i % 2 == 0 else 0x22100000)
    text(f, '[ORSA] ACCESS CONTROL // SIGNAL CORRUPT', px + 16, py + 8, 0xFFFF8B8B)
    text(f, '//// DECRYPTING COMPLIANCE PACKET ////', px + 16, py + 58, apply_alpha(0xFFFFC0C0, 0.82))
    return f


def end_card(with_return):
    f = black()
    s = 'The End?'
    text(f, s, GW // 2 - width_of(s) // 2, GH // 2 - 4, 0xFFFFF0B0, shadow=True)
    if with_return:
        hint = 'Press Space, Enter, or click to return.'
        text(f, hint, GW // 2 - width_of(hint) // 2, GH - 58, 0xFFB8EFFF, shadow=True)
        bw = min(220, max(150, GW // 4))
        bx, by = (GW - bw) // 2, GH - 38
        fill(f, bx, by, bx + bw, by + 20, 0xFF000000)
        fill(f, bx, by, bx + bw, by + 1, 0xFFFFFFFF)
        fill(f, bx, by + 19, bx + bw, by + 20, 0xFFFFFFFF)
        fill(f, bx, by, bx + 1, by + 20, 0xFFFFFFFF)
        fill(f, bx + bw - 1, by, bx + bw, by + 20, 0xFFFFFFFF)
        label = 'Return to World'
        text(f, label, GW // 2 - width_of(label) // 2, by + 6, 0xFFFFFFFF, shadow=True)
    return f


TEAL = (0xFFA7FFEA, 0xFFD9FFF6, 0xCC44E6A8)
FRAMES = [
    ('Ending A  SIGNAL 140 t  (unchanged)', terminal_panel('INCOMING SIGNAL', 'Welcome home.', *TEAL)),
    ('Ending B  SIGNAL 140 t  (same keys, teal)', terminal_panel('INCOMING SIGNAL', 'Welcome home.', *TEAL)),
    ('Ending B  COMPLIANCE static, first 34 t', compliance_static()),
    ('Ending B  COMPLIANCE line, to 320 t', terminal_panel(
        'ACCESS CONTROL',
        'Civilian #39102: Unauthorized document access detected. Landing clearance suspended. '
        'Remain seated. Escort en route.',
        0xFFFF8B8B, 0xFFFFC0C0, 0xCCFF5C5C)),
    ('Ending B  HOLD 40 t  (black, silent)', black()),
    ('Ending B  END_CARD 120 t, then held', end_card(False)),
    ('Ending B  last frame, after the timeline', end_card(True)),
]


def main():
    fw, fh = GW * S, GH * S
    label_h, pad, cols = 28, 16, 3
    rows = [FRAMES[:2], FRAMES[2:5], FRAMES[5:]]
    out = Image.new('RGBA', (cols * fw + (cols + 1) * pad, len(rows) * (fh + label_h + pad) + pad), (40, 40, 44, 255))
    for r, row in enumerate(rows):
        for c, (label, frame) in enumerate(row):
            x = pad + c * (fw + pad)
            y = pad + r * (fh + label_h + pad)
            big = Image.new('RGBA', (fw, label_h), (0, 0, 0, 0))
            text(big, label, 0, 2, 0xFFE0E0E0)
            out.alpha_composite(big, (x, y))
            out.alpha_composite(frame, (x, y + label_h))
    path = os.path.join(HERE, 'epilogue-mockup.png')
    out.convert('RGB').save(path)
    print(path)


if __name__ == '__main__':
    main()

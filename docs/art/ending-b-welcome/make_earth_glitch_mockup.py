"""Mockup for the ending's Earth orbit prelude and Ending B's glitched welcome (owner's requests, 2026-11-11).

GUI scale 2 on Minecraft's default 854x480 window (a 427x240 GUI), vanilla font, the glass cockpit's styles. Low
orbit: a blocky globe whose limb arcs across the window, sampled in 2 px cells from
tools/generate_frozen_earth_textures.py's map, turning west to east, with airglow on the limb.

Run: python3 tools/generate_frozen_earth_textures.py && python3 docs/art/ending-b-welcome/make_earth_glitch_mockup.py
"""
import math
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..'))
sys.path.insert(0, HERE)
import make_epilogue_mockup as m  # noqa: E402
import make_glass_cockpit_mockup as g  # noqa: E402

S, GW, GH = m.S, m.GW, m.GH
text, fill, width_of, apply_alpha, hash01 = m.text, m.fill, m.width_of, m.apply_alpha, m.hash01
EARTH = Image.open(os.path.join(ROOT, 'src/main/resources/assets/frozendawn/textures/gui/ending/earth_surface.png')).convert('RGB')
BLOCK = 2                   # GUI px a surface cell
LIMB_GLOW = 3.0             # GUI px of airglow outside the limb


def earth_globe(frame, lon0, radius=280, cx=GW * 0.5, cy=GH + 90, lat0=-20.0, roll=-8.0, dim=0.85):
    """A blocky globe seen from low orbit: its limb arcs across the window, the camera's sub-point below the frame.
    Orthographic, sampled once per BLOCK cell, darkened toward the limb, with a thin airglow outside it."""
    la, ro = math.radians(lat0), math.radians(roll)
    for by in range(0, GH, BLOCK):
        for bx in range(0, GW, BLOCK):
            dx, dy = (bx + BLOCK / 2 - cx) / radius, (cy - by - BLOCK / 2) / radius
            dx, dy = dx * math.cos(ro) - dy * math.sin(ro), dx * math.sin(ro) + dy * math.cos(ro)
            rr = dx * dx + dy * dy
            if rr > 1:
                glow = (math.sqrt(rr) - 1) * radius / LIMB_GLOW
                if glow < 1:
                    fill(frame, bx, by, bx + BLOCK, by + BLOCK, (int(0x99 * (1 - glow)) << 24) | 0x44E6A8)
                continue
            z = math.sqrt(1 - rr)
            y1 = dy * math.cos(la) + z * math.sin(la)
            z1 = -dy * math.sin(la) + z * math.cos(la)
            lat = math.degrees(math.asin(max(-1.0, min(1.0, y1))))
            lon = math.degrees(math.atan2(dx, z1)) + lon0
            tx = int(((lon + 180) % 360) / 360 * EARTH.width) % EARTH.width
            ty = int(min(179.99, max(0.0, 90 - lat)) / 180 * EARTH.height)
            r, g2, b = EARTH.getpixel((tx, ty))
            k = dim * (0.55 + 0.45 * z ** 0.6)
            fill(frame, bx, by, bx + BLOCK, by + BLOCK,
                 0xFF000000 | (round(r * k) << 16) | (round(g2 * k) << 8) | round(b * k))


def starfield():
    f = Image.new('RGBA', (GW * S, GH * S), (0, 0, 0, 255))
    g.grad_v(f, 0, 0, GW, GH, 0xFF02040A, 0xFF090D17)
    for i in range(170):
        x, y = round(hash01(i * 71 + 5) * GW), round(hash01(i * 113 + 19) * GH)
        a = round(80 + hash01(i * 31) * 150)
        fill(f, x, y, x + (2 if i % 17 == 0 else 1), y + (2 if i % 17 == 0 else 1), (a << 24) | 0xFFFFFF)
    return f


def frame_chrome(f, clock):
    g.grad_v(f, 0, 0, GW, 26, 0xC0050A10, 0x00050A10)
    g.grad_v(f, 0, GH - 30, GW, GH, 0x00050A10, 0xD0050A10)
    g.grad_h(f, 0, 0, 22, GH, 0xA0050A10, 0x00050A10)
    g.grad_h(f, GW - 22, 0, GW, GH, 0x00050A10, 0xA0050A10)
    i = 10
    for box in ((i, i, GW - i, i + 1), (i, GH - i - 1, GW - i, GH - i), (i, i, i + 1, GH - i), (GW - i - 1, i, GW - i, GH - i)):
        fill(f, *box, 0x444FC7DA)
    g.ticks(f, i, i, GW - i - 1, GH - i - 1, 6, 0xDD8DEAFF)
    fill(f, 18, 18, 21, 21, 0xFF44E6A8)
    text(f, 'ORSA VIEWPORT', 25, 16, 0xFF8DEAFF)
    g.right(f, clock, GW - 18, 16, 0xAA8DEAFF)


def life_support(f, burn):
    pw, ph = 150, 78
    px, py = 18, GH - ph - 18
    fill(f, px, py, px + pw, py + ph, 0x7006121A)
    fill(f, px, py, px + pw, py + 1, 0xCC4FC7DA)
    fill(f, px, py + ph - 1, px + pw, py + ph, 0x334FC7DA)
    text(f, 'LIFE SUPPORT', px + 8, py + 6, 0xFF8DEAFF)
    fill(f, px + pw - 12, py + 8, px + pw - 9, py + 11, 0xFF44E6A8)
    rows = [('O2 SUPPLY', '98 %'), ('CABIN TEMP', '19 C'), ('CO2 SCRUBBER', 'NOMINAL'), ('HULL', 'SEALED')]
    for n, (k, v) in enumerate(rows):
        text(f, k, px + 8, py + 19 + n * 10, 0xFF6FA3AC)
        g.right(f, v, px + pw - 9, py + 19 + n * 10, 0xFFD9FFF6)
    for x in range(px + 8, px + pw - 8, 3):
        fill(f, x, py + 61, x + 1, py + 62, 0x5555C6D8)
    text(f, 'TMI BURN', px + 8, py + 66, 0xFF6FA3AC)
    g.right(f, burn, px + pw - 9, py + 66, 0xFFFFD27A)


def earth_frame(lon0, burn, clock, fade=1.0):
    f = starfield()
    earth_globe(f, lon0)
    frame_chrome(f, clock)
    life_support(f, burn)
    text(f, 'Hold Q to fast-forward.', GW - width_of('Hold Q to fast-forward.') - 18, GH - 22, 0xCCB8EFFF)
    if fade < 1.0:
        f.alpha_composite(Image.new('RGBA', f.size, (0, 0, 0, round(255 * (1 - fade)))))
    return f


GLYPHS = '#%&@$/\\<>?!*'


def glitch_frame(progress, local):
    """Ending B: the typed welcome tears, corrupts and reddens into the compliance cut."""
    f = Image.new('RGBA', (GW * S, GH * S), (0, 0, 0, 255))
    if progress > 0.85 and local % 3 == 0:
        return f
    body = 'Welcome home.'
    chars = ''.join(GLYPHS[int(hash01(local * 31 + i) * len(GLYPHS))] if hash01(local * 17 + i * 7) < progress * 0.9 else ch
                    for i, ch in enumerate(body))
    title = ('ACCESS CONTROL // SIGNAL CORRUPT' if hash01(local * 13) < progress else 'INCOMING SIGNAL')

    def mix(a, b, t):
        return 0xFF000000 | sum(round(((a >> s) & 255) + (((b >> s) & 255) - ((a >> s) & 255)) * t) << s for s in (16, 8, 0))

    accent = mix(0xFF44E6A8, 0xFFFF5C5C, progress) & 0xFFFFFF | 0xCC000000
    body_c, title_c = mix(0xFFD9FFF6, 0xFFFFC0C0, progress), mix(0xFFA7FFEA, 0xFFFF8B8B, progress)
    panel = g.glass_panel(title, chars, title_c, body_c, accent, cursor=False)
    out = Image.new('RGBA', f.size, (0, 0, 0, 255))
    for y in range(0, GH, 3):
        dx = round((hash01(local * 97 + y) - 0.5) * progress * 26) if hash01(local * 53 + y) < 0.5 + progress / 2 else 0
        out.alpha_composite(panel.crop((0, y * S, GW * S, (y + 3) * S)), (dx * S, y * S))
    if progress > 0.2:
        ghost = g.glass_panel(title, chars, 0xFFFF5C5C, 0xFFFF5C5C, 0xCCFF5C5C, cursor=False)
        ghost.putalpha(ghost.getchannel('A').point(lambda a: round(a * 0.35 * progress)))
        out.alpha_composite(ghost, (2 * S, 0))
    for i in range(int(progress * 10)):
        y = round(hash01(local * 41 + i) * GH)
        fill(out, 0, y, GW, y + 1, 0x88FF5C5C if i % 2 else 0x66FFC0C0)
    return out


def main():
    tiles = [
        ('Low orbit: frozen Africa and Europe under the window', earth_frame(8.0, 'T- 0:14', 'T+ 212:13:52')),
        ('Low orbit: later, Arabia coming round, the burn nears', earth_frame(22.0, 'T- 0:02', 'T+ 212:14:04')),
        ('Fade to black on the burn, then Mars', earth_frame(23.0, 'T- 0:00', 'T+ 212:14:06', fade=0.4)),
        ('Ending B: Welcome home., held', g.glass_panel('INCOMING SIGNAL', 'Welcome home.', *g.TEAL, cursor=False)),
        ('Ending B: glitch, a third in', glitch_frame(0.35, 11)),
        ('Ending B: glitch, near the cut', glitch_frame(0.75, 25)),
        ('Ending B: ACCESS CONTROL', g.glass_panel('ACCESS CONTROL', g.COMPLIANCE, *g.RED, cursor=False)),
    ]
    fw, fh, pad, label_h, cols = GW * S, GH * S, 16, 28, 2
    rows = [tiles[0:2], tiles[2:4], tiles[4:6], tiles[6:7]]
    out = Image.new('RGBA', (cols * fw + (cols + 1) * pad, len(rows) * (fh + label_h + pad) + pad), (40, 40, 44, 255))
    for r, row in enumerate(rows):
        for c, (label, tile) in enumerate(row):
            x, y = pad + c * (fw + pad), pad + r * (fh + label_h + pad)
            lab = Image.new('RGBA', (fw, label_h), (0, 0, 0, 0))
            text(lab, label, 0, 2, 0xFFE0E0E0)
            out.alpha_composite(lab, (x, y))
            out.alpha_composite(tile, (x, y + label_h))
    path = os.path.join(HERE, 'earth-glitch-mockup.png')
    out.convert('RGB').save(path)
    print(path)


if __name__ == '__main__':
    main()

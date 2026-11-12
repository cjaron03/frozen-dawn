"""Mockup for the ending screen's glass cockpit restyle (owner's request during branch 7's visual pass, 2026-11-11).

GUI scale 2 on Minecraft's default 854x480 window (a 427x240 GUI), vanilla font. Left column ports today's
renderApproachViewport, renderApproachVectorTelemetry and drawEndingTerminalPanel; right column is the proposal.
Mars is simplified to its front texture, turned by the screen's roll; both columns draw it the same way.

  glass-cockpit-mockup.png   viewport, HUD (zoomed) and terminal panels, today beside the proposal

Run: python3 docs/art/ending-b-welcome/make_glass_cockpit_mockup.py
"""
import math
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..'))
sys.path.insert(0, HERE)
import make_epilogue_mockup as m  # noqa: E402  (font, fills and the hash, at the same scale)

S, GW, GH = m.S, m.GW, m.GH
text, fill, width_of, apply_alpha, hash01, wrap = m.text, m.fill, m.width_of, m.apply_alpha, m.hash01, m.wrap
MARS = Image.open(os.path.join(ROOT, 'src/main/resources/assets/frozendawn/textures/gui/ending/mars_front.png')).convert('RGBA')


def grad_v(f, x0, y0, x1, y1, top, bottom):
    for y in range(y0, y1):
        t = (y - y0) / max(1, y1 - y0 - 1)
        c = 0
        for shift in (24, 16, 8, 0):
            a, b = (top >> shift) & 255, (bottom >> shift) & 255
            c |= round(a + (b - a) * t) << shift
        fill(f, x0, y, x1, y + 1, c)


def grad_h(f, x0, y0, x1, y1, left, right):
    for x in range(x0, x1):
        t = (x - x0) / max(1, x1 - x0 - 1)
        c = 0
        for shift in (24, 16, 8, 0):
            a, b = (left >> shift) & 255, (right >> shift) & 255
            c |= round(a + (b - a) * t) << shift
        fill(f, x, y0, x + 1, y1, c)


def line(f, x0, y0, x1, y1, c):
    steps = max(1, abs(x1 - x0), abs(y1 - y0))
    for i in range(steps + 1):
        t = i / steps
        x, y = round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t)
        fill(f, x, y, x + 1, y + 1, c)


def disk(f, cx, cy, r, alpha, rgb):
    ir = math.ceil(r)
    for y in range(-ir, ir + 1, 2):
        ny = y / r
        if -1 <= ny <= 1:
            hw = math.floor(math.sqrt(1 - ny * ny) * r)
            fill(f, cx - hw, cy + y, cx + hw, cy + y + 2, (alpha << 24) | rgb)


def space():
    f = Image.new('RGBA', (GW * S, GH * S), (0, 0, 0, 255))
    grad_v(f, 0, 0, GW, GH, 0xFF02040A, 0xFF090D17)
    grad_v(f, 0, 0, GW, GH // 2, 0x22264668, 0x00264668)
    for i in range(170):
        x, y = round(hash01(i * 71 + 5) * GW), round(hash01(i * 113 + 19) * GH)
        size = 2 if i % 17 == 0 else 1
        a = round(80 + hash01(i * 31) * 150)
        fill(f, x, y, x + size, y + size, (a << 24) | (0xD8F6FF if i % 11 == 0 else 0xFFFFFF))
    face = round(max(48, min(GW, GH) * 0.38))
    cx, cy = round(GW / 2 + GW * 0.04), round(GH * 0.46)
    disk(f, cx, cy, face * 1.58, 22, 0x9E341C)
    disk(f, cx, cy, face * 1.18, 28, 0xE65C28)
    disk(f, cx, cy, face * 0.76, 18, 0xFF8444)
    tex = MARS.resize((face * S, face * S), Image.NEAREST).rotate(math.degrees(0.22), Image.NEAREST, expand=True)
    f.alpha_composite(tex, (cx * S - tex.width // 2, cy * S - tex.height // 2))
    return f, (cx, cy, face)


# --- today -------------------------------------------------------------------------------------------------

def bracket(f, x, y, size, left, top):
    c = 0xDD8DEAFF
    hs, he = (x, x + size) if left else (x - size, x)
    vs, ve = (y, y + size) if top else (y - size, y)
    fill(f, hs, y, he, y + 2, c)
    fill(f, x, vs, x + 2, ve, c)
    fill(f, hs, y + (2 if top else -2), hs + 6, y + (4 if top else 0), 0xAA8DEAFF)


def today_hud(f, vl, vr, vt, vb):
    pw, ph = min(210, max(142, (vr - vl) // 4)), 88
    px, py = vr - pw - 18, vb - ph - 18
    if px < vl + 16 or py < vt + 18:
        return None
    fill(f, px, py, px + pw, py + ph, 0xA0061218)
    fill(f, px + 2, py + 2, px + pw - 2, py + ph - 2, 0x66102931)
    fill(f, px + 7, py + 7, px + 9, py + ph - 7, 0xCC4FC7DA)
    fill(f, px + 15, py + 10, px + pw - 12, py + 11, 0x554FC7DA)
    text(f, 'APPROACH VECTOR', px + 15, py + 16, 0xFF8DEAFF)
    text(f, 'Mars gravity well: captured', px + 15, py + 30, 0xFFC9FFF8)
    text(f, 'Entry corridor: locked', px + 15, py + 42, 0xFFA7FFEA)
    gx, gy, gw = px + 18, py + 61, pw - 42
    cy = gy + 8
    craft, mars = gx + round(0.22 * gw), gx + gw - 8
    fill(f, gx, cy, gx + gw, cy + 1, 0x6655C6D8)
    line(f, craft, cy + 9, mars, cy - 5, 0xDD8DEAFF)
    line(f, mars - 10, cy - 2, mars - 1, cy - 5, 0xDD8DEAFF)
    line(f, mars - 1, cy - 5, mars - 6, cy - 7, 0xDD8DEAFF)
    line(f, mars - 1, cy - 5, mars - 4, cy, 0xDD8DEAFF)
    x, y, o = mars, cy - 5, 0xFFE06D3C
    fill(f, x - 2, y - 2, x + 3, y + 3, 0x55E06D3C)
    fill(f, x - 7, y, x - 3, y + 1, o); fill(f, x + 4, y, x + 8, y + 1, o)
    fill(f, x, y - 7, x + 1, y - 3, o); fill(f, x, y + 4, x + 1, y + 8, o)
    fill(f, craft - 1, cy + 8, craft + 2, cy + 11, 0xFFE7F5FF)
    text(f, '0.38g capture', gx, py + 75, 0xFFB8EFFF)
    return px, py, pw, ph


def today_viewport():
    f, _ = space()
    top, bottom, side = max(34, GH // 12), max(58, GH // 6), max(44, GW // 10)
    vl, vr, vt, vb = side, GW - side, top, GH - bottom
    fill(f, 0, 0, GW, top, 0xEA10141A)
    fill(f, 0, vb, GW, GH, 0xF010141A)
    fill(f, 0, top, side, vb, 0xDE10141A)
    fill(f, GW - side, top, GW, vb, 0xDE10141A)
    fill(f, vl - 8, vt - 8, vr + 8, vt + 5, 0xCC303844)
    fill(f, vl - 8, vb - 5, vr + 8, vb + 10, 0xCC303844)
    fill(f, vl - 10, vt - 4, vl + 5, vb + 6, 0xCC252C36)
    fill(f, vr - 5, vt - 4, vr + 10, vb + 6, 0xCC252C36)
    fill(f, vl, vt, vr, vt + 2, 0xCC4FC7DA)
    fill(f, vl, vb - 2, vr, vb, 0x994FC7DA)
    fill(f, vl, vt, vl + 2, vb, 0x884FC7DA)
    fill(f, vr - 2, vt, vr, vb, 0x884FC7DA)
    c = max(18, min(GW, GH) // 18)
    bracket(f, vl + 4, vt + 4, c, True, True); bracket(f, vr - 4, vt + 4, c, False, True)
    bracket(f, vl + 4, vb - 4, c, True, False); bracket(f, vr - 4, vb - 4, c, False, False)
    grad_v(f, vl + 4, vt + 3, vr - 4, vt + max(34, GH // 11), 0x244FC7DA, 0x004FC7DA)
    grad_v(f, vl + 4, vb - max(24, GH // 14), vr - 4, vb - 3, 0x00283440, 0x18283440)
    gy = vt + max(14, (vb - vt) // 5)
    fill(f, vl + 24, gy, vr - 38, gy + 1, 0x558EEAFF)
    fill(f, vl + 44, gy + 16, vr - 92, gy + 17, 0x2255C6D8)
    sy = max(6, vt - 25)
    fill(f, vl + 10, sy - 3, vl + 142, sy + 11, 0x6610141A)
    text(f, 'ORSA VIEWPORT', vl + 15, sy, 0xFF8DEAFF)
    hud = today_hud(f, vl, vr, vt, vb)
    text(f, 'Hold Q to fast-forward.', GW - width_of('Hold Q to fast-forward.') - 12, GH - 18, 0xFFB8EFFF)
    return f, hud


# --- proposal ----------------------------------------------------------------------------------------------

def ticks(f, x0, y0, x1, y1, n, c):
    for x, y, dx, dy in ((x0, y0, 1, 1), (x1, y0, -1, 1), (x0, y1, 1, -1), (x1, y1, -1, -1)):
        fill(f, min(x, x + dx * n), y, max(x, x + dx * n) + 1, y + 1, c)
        fill(f, x, min(y, y + dy * n), x + 1, max(y, y + dy * n) + 1, c)


def right(f, s, xr, y, c):
    text(f, s, xr - width_of(s) + 1, y, c)


def glass_hud(f, range_km):
    pw, ph = 150, 78
    px, py = 18, GH - ph - 18  # bottom left, clear of Mars (which sits right of center)
    fill(f, px, py, px + pw, py + ph, 0x7006121A)
    fill(f, px, py, px + pw, py + 1, 0xCC4FC7DA)
    fill(f, px, py + ph - 1, px + pw, py + ph, 0x334FC7DA)
    label, value = 0xFF6FA3AC, 0xFFD9FFF6
    text(f, 'APPROACH VECTOR', px + 8, py + 6, 0xFF8DEAFF)
    fill(f, px + pw - 12, py + 8, px + pw - 9, py + 11, 0xFF44E6A8)
    rows = [('GRAVITY WELL', 'CAPTURED'), ('ENTRY CORRIDOR', 'LOCKED'), ('RANGE', range_km)]
    for n, (k, v) in enumerate(rows):
        text(f, k, px + 8, py + 19 + n * 10, label)
        right(f, v, px + pw - 9, py + 19 + n * 10, value)
    gx, gw, gy = px + 8, pw - 17, py + 56
    for x in range(gx, gx + gw, 3):
        fill(f, x, gy, x + 1, gy + 1, 0x5555C6D8)
    pts = [(gx + round(t * gw), gy + 4 - round(math.sin(t * math.pi * 0.55) * 7)) for t in [i / 40 for i in range(41)]]
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        line(f, x0, y0, x1, y1, 0xDD8DEAFF)
    cx, cy = pts[9]
    fill(f, cx - 1, cy - 1, cx + 2, cy + 2, 0xFFE7F5FF)
    mx, my = pts[-1]
    fill(f, mx - 2, my - 2, mx + 3, my + 3, 0xFFE06D3C)
    text(f, 'CAPTURE', px + 8, py + 66, label)
    right(f, '0.38 G', px + pw - 9, py + 66, value)
    return px, py, pw, ph


def glass_viewport(range_km='48 210 KM'):
    f, (mcx, mcy, face) = space()
    grad_v(f, 0, 0, GW, 26, 0xC0050A10, 0x00050A10)
    grad_v(f, 0, GH - 30, GW, GH, 0x00050A10, 0xD0050A10)
    grad_h(f, 0, 0, 22, GH, 0xA0050A10, 0x00050A10)
    grad_h(f, GW - 22, 0, GW, GH, 0x00050A10, 0xA0050A10)
    i = 10
    fill(f, i, i, GW - i, i + 1, 0x444FC7DA); fill(f, i, GH - i - 1, GW - i, GH - i, 0x444FC7DA)
    fill(f, i, i, i + 1, GH - i, 0x444FC7DA); fill(f, GW - i - 1, i, GW - i, GH - i, 0x444FC7DA)
    ticks(f, i, i, GW - i - 1, GH - i - 1, 6, 0xDD8DEAFF)
    fill(f, 18, 18, 21, 21, 0xFF44E6A8)
    text(f, 'ORSA VIEWPORT', 25, 16, 0xFF8DEAFF)
    right(f, 'T+ 212:14:06', GW - 18, 16, 0xAA8DEAFF)
    half = round(face * 0.62)
    ticks(f, mcx - half, mcy - half, mcx + half, mcy + half, 5, 0xCCE06D3C)
    text(f, 'MARS', mcx - half, mcy + half + 4, 0xFFE06D3C)
    hud = glass_hud(f, range_km)
    hint = 'Hold Q to fast-forward.'
    text(f, hint, GW - width_of(hint) - 18, GH - 22, 0x99B8EFFF)
    return f, hud


def glass_panel(title, body, title_c, body_c, accent_c, typed=None, cursor=True):
    f = Image.new('RGBA', (GW * S, GH * S), (0, 0, 0, 255))
    shown = body if typed is None else body[:typed]
    pw = min(GW - 32, max(200, width_of(body) + 40))
    lines = wrap(body, pw - 32)
    ph = 36 + len(lines) * 9
    px, py = (GW - pw) // 2, GH // 2 - ph // 2
    fill(f, px, py, px + pw, py + ph, 0x9008120F)
    fill(f, px, py, px + pw, py + 1, accent_c)
    fill(f, px, py + ph - 1, px + pw, py + ph, apply_alpha(accent_c, 0.3))
    fill(f, px + 16, py + 9, px + 19, py + 12, accent_c)
    text(f, '[ORSA] ' + title, px + 23, py + 8, apply_alpha(title_c, 0.8))
    x, y, left = px + 16, py + 24, len(shown)
    for ln in lines:
        part = ln[:left]
        text(f, part, x, y, body_c)
        left -= len(ln) + 1
        end_x, end_y = x + width_of(part), y
        if left <= 0:
            break
        y += 9
    if cursor:
        fill(f, end_x + 1, end_y, end_x + 6, end_y + 8, apply_alpha(body_c, 0.85))
    return f


def crop(frame, box, zoom=2):
    x, y, w, h = box
    pad = 6
    c = frame.crop(((x - pad) * S, (y - pad) * S, (x + w + pad) * S, (y + h + pad) * S))
    return c.resize((c.width * zoom, c.height * zoom), Image.NEAREST)


TEAL = (0xFFA7FFEA, 0xFFD9FFF6, 0xCC44E6A8)
RED = (0xFFFF8B8B, 0xFFFFC0C0, 0xCCFF5C5C)
COMPLIANCE = ('Civilian #39102: Unauthorized document access detected. Landing clearance suspended. '
              'Remain seated. Escort en route.')


def main():
    today_v, today_h = today_viewport()
    glass_v, glass_h = glass_viewport()
    tiles = [
        ('Today: viewport', today_v),
        ('Glass cockpit: viewport', glass_v),
        ('Today: HUD, zoomed 2x', crop(today_v, today_h)),
        ('Glass cockpit: HUD, zoomed 2x (RANGE ticks down)', crop(glass_v, glass_h)),
        ('Today: INCOMING SIGNAL', m.terminal_panel('INCOMING SIGNAL', 'Welcome home.', *TEAL)),
        ('Glass cockpit: typing in, 2 ticks a letter', glass_panel('INCOMING SIGNAL', 'Welcome home.', *TEAL, typed=6)),
        ('Glass cockpit: INCOMING SIGNAL, typed', glass_panel('INCOMING SIGNAL', 'Welcome home.', *TEAL)),
        ('Glass cockpit: ARRIVAL TELEMETRY', glass_panel('ARRIVAL TELEMETRY', 'ORSA Mars Colony -- Active', *TEAL, cursor=False)),
        ('Glass cockpit: ACCESS CONTROL (Ending B)', glass_panel('ACCESS CONTROL', COMPLIANCE, *RED, cursor=False)),
    ]
    fw, fh, pad, label_h, cols = GW * S, GH * S, 16, 28, 2
    rows = [tiles[0:2], tiles[2:4], tiles[4:6], tiles[6:8], tiles[8:9]]
    heights = [max(t[1].height for t in row) for row in rows]
    out = Image.new('RGBA', (cols * fw + (cols + 1) * pad, sum(h + label_h + pad for h in heights) + pad),
                    (40, 40, 44, 255))
    y = pad
    for row, h in zip(rows, heights):
        for c, (label, tile) in enumerate(row):
            x = pad + c * (fw + pad)
            lab = Image.new('RGBA', (fw, label_h), (0, 0, 0, 0))
            text(lab, label, 0, 2, 0xFFE0E0E0)
            out.alpha_composite(lab, (x, y))
            out.alpha_composite(tile, (x, y + label_h))
        y += h + label_h + pad
    path = os.path.join(HERE, 'glass-cockpit-mockup.png')
    out.convert('RGB').save(path)
    print(path)


if __name__ == '__main__':
    main()

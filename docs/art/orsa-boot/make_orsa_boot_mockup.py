"""Mockup for the first boot's ORSA beat (owner request, 2026-11-09).

After the opening's black lead-in, a point of light at the centre grows into the spinning
ORSA boot icon as the ORSA sting starts; then the icon glides into the masthead's O while
the rest of the logo spreads out from it, and its spin eases to a stop on the masthead's
pose before the O blends into the masthead. Drawn from the shipped boot
sheet (32 frames, one orbit per 22 ticks as the terminals spin it) at GUI scale 4 on a
1280x720 window, 20 frames a second. The icon is filtered as the game filters it.

The spinner is the regenerated sheet (tools/GenerateOrsaBootSheet.java: 64 frames, 32 across
and 2 down, the moon passing behind the planet); orsa-spinner-compare.gif loops the shipped
sheet beside it.

Run: python3 docs/art/orsa-boot/make_orsa_boot_mockup.py [sheet] [old sheet for the comparison]
The sheet defaults to the shipped one; the comparison was drawn against the 32-frame sheet
the spinner replaced (git show 7f018a4a:src/main/resources/assets/frozendawn/textures/gui/orsa_logo_boot_sheet.png).
"""
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..'))
GUI = os.path.join(ROOT, 'src/main/resources/assets/frozendawn/textures/gui')
S, GW, GH = 4, 320, 180
SHEET = Image.open(sys.argv[1] if len(sys.argv) > 1 else os.path.join(GUI, 'orsa_logo_boot_sheet.png')).convert('RGBA')
OLD_SHEET = Image.open(sys.argv[2]).convert('RGBA') if len(sys.argv) > 2 else None
MASTHEAD = Image.open(os.path.join(GUI, 'orsa_awakening_base.png')).convert('RGBA')

GLINT, GROW, EXPAND, BLEND, HOLD = 4, 30, 32, 8, 26
OMEGA = 1 / 22.0          # one orbit per 22 ticks, as the terminals spin it
STOP_FRAME = 8            # the moon at the upper right, where the masthead's O shows it
MAST_O = (180, 127, 90)   # the masthead's O: centre and outer radius, in its 817 px texture


def ease_out(t):
    t = max(0.0, min(1.0, t))
    return 1 - (1 - t) ** 3


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def phase_at(t):
    """Constant spin, then a quadratic ease to a stop on STOP_FRAME as the expand ends."""
    stop = GLINT + GROW + EXPAND
    decel = GLINT + GROW
    if t >= stop:
        remaining = 0.0
    elif t >= decel:
        remaining = OMEGA * EXPAND * (1 - (t - decel) / EXPAND) ** 3 / 3
    else:
        remaining = OMEGA * (decel - t) + OMEGA * EXPAND / 3
    return STOP_FRAME / 64.0 + 3 - remaining


def frame_of(tick, sheet=None, phase=None):
    sheet = sheet or SHEET
    count = (sheet.width // 208) * (sheet.height // 208)
    p = (tick / 22.0) if phase is None else phase
    i = int(round((p % 1.0) * count)) % count
    col, row = i % (sheet.width // 208), i // (sheet.width // 208)
    return sheet.crop((col * 208, row * 208, col * 208 + 208, row * 208 + 208))


def overlay(im):
    layer = Image.new('RGBA', im.size, (0, 0, 0, 0))
    return layer, ImageDraw.Draw(layer)


def scanlines(im, tick, alpha):
    layer, d = overlay(im)
    for y in range(tick % 6, GH, 6):
        d.rectangle([0, y * S, GW * S, y * S + S - 1], fill=(0x6E, 0xA4, 0xAF, alpha))
    im.alpha_composite(layer)


def masthead_layout():
    """Today's large masthead, in device pixels, and its O's centre and spinner size there."""
    w = min(int(GW * 0.56), 480) * S
    h = int(w / (817 / 362))
    x, y = GW * S // 2 - w // 2, int(GH * S * 0.37 - h / 2)
    k = w / 817
    ocx, ocy, orr = x + MAST_O[0] * k, y + MAST_O[1] * k, MAST_O[2] * k
    return x, y, w, h, ocx, ocy, orr, orr * 208 / 74


def beat_frame(t):
    im = Image.new('RGBA', (GW * S, GH * S), (0, 0, 0, 255))
    scanlines(im, t, 18)
    cx, cy = GW * S / 2, GH * S / 2
    full = int(GH * 0.42) * S
    mx, my, mw, mh, ocx, ocy, orr, o_size = masthead_layout()
    layer, d = overlay(im)
    if t < GLINT:
        a = round(255 * smooth(t / 2.0))
        r = S if t < 2 else S * 2
        d.rectangle([cx - r, cy - S // 2, cx + r, cy + S // 2], fill=(0xE9, 0xFB, 0xFF, a))
        d.rectangle([cx - S // 2, cy - r, cx + S // 2, cy + r], fill=(0xE9, 0xFB, 0xFF, a))
        im.alpha_composite(layer)
        return im
    g = t - GLINT
    icon_alpha = 1.0
    if g < GROW:
        size, px, py = max(S, full * ease_out(g / GROW)), cx, cy
        halo = round(26 * smooth(g / 8.0))
    else:
        e = smooth((g - GROW) / EXPAND)
        size = full + (o_size - full) * e
        px, py = cx + (ocx - cx) * e, cy + (ocy - cy) * e
        halo = round(26 * (1 - e))
        # The rest of the logo spreads out from the O, travelling and scaling with the icon.
        k = size / o_size
        sw, sh = max(1, round(mw * k)), max(1, round(mh * k))
        sx, sy = px - (ocx - mx) * k, py - (ocy - my) * k
        sr = orr * k
        reach = (sr + 2 * S) + e * (sw * 1.05)
        mask = Image.new('L', im.size, 0)
        md = ImageDraw.Draw(mask)
        md.ellipse([px - reach, py - reach, px + reach, py + reach], fill=255)
        blend = smooth((g - GROW - EXPAND) / BLEND)
        if g < GROW + EXPAND:
            md.ellipse([px - sr - S, py - sr - S, px + sr + S, py + sr + S], fill=0)
        mast = Image.new('RGBA', im.size, (0, 0, 0, 0))
        mast.alpha_composite(MASTHEAD.resize((sw, sh), Image.BILINEAR), (round(sx), round(sy)))
        mast.putalpha(Image.composite(mast.getchannel('A'), Image.new('L', im.size, 0), mask))
        im.alpha_composite(mast)
        icon_alpha = 1 - blend
    for ring in range(3):
        hr = size * 74 / 208 + (2 + ring * 2) * S
        if halo > 0:
            d.ellipse([px - hr, py - hr, px + hr, py + hr], fill=(0x3D, 0xE1, 0xF6, halo // (ring + 1)))
    im.alpha_composite(layer)
    if icon_alpha > 0.01:
        isz = max(1, round(size))
        icon = frame_of(t, phase=phase_at(t)).resize((isz, isz), Image.BILINEAR)
        if icon_alpha < 1:
            icon.putalpha(icon.getchannel('A').point(lambda v: round(v * icon_alpha)))
        im.alpha_composite(icon, (round(px - isz / 2), round(py - isz / 2)))
    return im


def compare():
    """The shipped spinner beside the new one, at terminal size and at the boot beat's size."""
    frames = []
    for t in range(44):
        im = Image.new('RGBA', (GW * S, GH * S // 2), (8, 12, 18, 255))
        for i, sheet in enumerate((OLD_SHEET, SHEET)):
            big = frame_of(t, sheet).resize((76 * S, 76 * S), Image.BILINEAR)
            im.alpha_composite(big, ((40 + i * 160) * S - 38 * S + 40 * S, 7 * S))
        frames.append(im.convert('RGB'))
    palette = frames[0].quantize(colors=255, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    out = [f.quantize(palette=palette, dither=Image.Dither.NONE) for f in frames]
    out[0].save(os.path.join(HERE, 'orsa-spinner-compare.gif'), save_all=True, append_images=out[1:],
                duration=50, loop=0, disposal=1)


def main():
    if OLD_SHEET is not None:
        compare()
    frames = [beat_frame(t) for t in range(GLINT + GROW + EXPAND + BLEND + HOLD)]
    rgb = [f.convert('RGB') for f in frames]
    sheet = Image.new('RGB', (rgb[0].width, rgb[0].height * 3))
    for i, f in enumerate((rgb[20], rgb[50], rgb[-1])):
        sheet.paste(f, (0, i * f.height))
    palette = sheet.quantize(colors=255, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    out = [f.quantize(palette=palette, dither=Image.Dither.NONE) for f in rgb]
    out[0].save(os.path.join(HERE, 'orsa-boot-mockup.gif'), save_all=True, append_images=out[1:],
                duration=50, loop=0, disposal=1)


if __name__ == '__main__':
    main()

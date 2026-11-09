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

orsa-boot-replay-mockup.gif is the replay's beat (owner previews, 2026-11-09): drained to grey on a
long-neglected screen, cold-starting with a sputtering point of light, then stalling twice, skipping back
once and tearing, with worn textures.

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
MUTED_MASTHEAD = Image.open(os.path.join(GUI, 'orsa_awakening_base_muted.png')).convert('RGBA')

# The replay's glitches on the beat: held frames, one skip back, and torn rows, on fixed ticks.
STALLS = {13: 3, 41: 4}          # tick -> how many ticks it holds
SKIP_BACK = (22, 5)              # at tick 22 the beat jumps back 5 ticks
TEARS = {30, 31, 49, 63}


def muted(image):
    """The replay's drain: 0.76 of the luminance, a little cool, as the _muted textures are."""
    r, g, b, a = image.split()
    lum = Image.merge('RGB', (r, g, b)).convert('L').point(lambda v: round(v * 0.755))
    return Image.merge('RGBA', (lum.point(lambda v: max(0, v - 3)), lum.point(lambda v: min(255, v + 1)),
                                lum.point(lambda v: min(255, v + 6)), a))


def neglect_layers():
    """The replay's screen, left untouched for a long time, at GUI resolution: an under layer (uneven backlight, the
    burned-in ghost of the readout panel and the lifted masthead, a stuck column and row) and an over layer (dust,
    smudges and dead pixels), both fixed, so the same marks sit in the same places every frame."""
    import random
    rnd = random.Random(0x0D057)
    under = Image.new('RGBA', (GW, GH), (0, 0, 0, 0))
    d = ImageDraw.Draw(under)
    # Burn-in: the readout panel and the compact masthead, years on the same screen.
    d.rectangle([18, 68, 301, 157], fill=(150, 158, 164, 9))
    d.rectangle([18, 68, 301, 69], fill=(170, 178, 184, 20))
    d.rectangle([31, 100, 210, 100], fill=(160, 168, 174, 12))
    d.rectangle([31, 113, 180, 113], fill=(160, 168, 174, 12))
    ghost = MUTED_MASTHEAD.resize((108, round(108 * 362 / 817)), Image.BILINEAR)
    ghost.putalpha(ghost.getchannel('A').point(lambda v: v * 14 // 255))
    under.alpha_composite(ghost, (GW // 2 - 54, 18))
    # A stuck column and a stuck row.
    d.line([(233, 0), (233, GH)], fill=(140, 148, 154, 34))
    d.line([(0, 41), (GW, 41)], fill=(140, 148, 154, 18))
    # Uneven backlight: blotches and dark corners.
    mura = Image.new('L', (8, 5))
    mura.putdata([rnd.randrange(0, 70) for _ in range(40)])
    mura = mura.resize((GW, GH), Image.BICUBIC)
    corner = Image.radial_gradient('L').resize((GW, GH))
    shade = Image.merge('RGBA', (Image.new('L', (GW, GH), 0),) * 3 +
                        (Image.eval(Image.blend(mura, corner.point(lambda v: max(0, v - 120)), 0.5), lambda v: min(150, v * 2)),))
    over = Image.new('RGBA', (GW, GH), (0, 0, 0, 0))
    o = ImageDraw.Draw(over)
    for _ in range(70):
        o.point((rnd.randrange(GW), rnd.randrange(GH)), fill=(0, 0, 0, rnd.choice((90, 140, 200))))
    for _ in range(6):
        x, y = rnd.randrange(GW), rnd.randrange(GH)
        o.ellipse([x, y, x + rnd.randrange(3, 9), y + rnd.randrange(2, 5)], fill=(30, 28, 24, 45))
    for _ in range(26):
        o.point((rnd.randrange(GW), rnd.randrange(GH)), fill=(0, 0, 0, 255))
    for _ in range(5):
        o.point((rnd.randrange(GW), rnd.randrange(GH)), fill=(176, 186, 196, 255))
    return under, shade, over


FLICKER = {18, 19, 52, 77}
# Before the beat, an old device warming up: the point of light catches, dies, and catches again.
SPUTTER = 12
SPUTTER_ON = {0: 0.5, 1: 0.9, 6: 0.4, 9: 0.7, 10: 1.0, 11: 0.6}


def glitch_time(t):
    """The replay's beat clock: it stalls, and once skips back."""
    te = t
    for start, hold in STALLS.items():
        if te >= start:
            te -= min(hold, te - start)
    if t >= SKIP_BACK[0]:
        te -= SKIP_BACK[1] if t < SKIP_BACK[0] + SKIP_BACK[1] else 0
    return te

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
    sheet = sheet if sheet is not None else SHEET
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


def beat_frame(t, glitch=False):
    im = Image.new('RGBA', (GW * S, GH * S), (0, 0, 0, 255))
    scanlines(im, t, 18)
    if glitch:
        im.alpha_composite(NEGLECT[0].resize(im.size, Image.NEAREST))
        snow = Image.new('RGBA', (GW, GH), (0, 0, 0, 0))
        import random
        r = random.Random(t)
        for _ in range(GW * GH // 220):
            v = r.randrange(70, 150)
            snow.putpixel((r.randrange(GW), r.randrange(GH)), (v, v + 3, v + 6, r.randrange(40, 110)))
        im.alpha_composite(snow.resize(im.size, Image.NEAREST))
        clock = t
        t = glitch_time(t)
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
        mast.alpha_composite(worn((MUTED_MASTHEAD if glitch else MASTHEAD).resize((sw, sh), Image.BILINEAR), sw)
                             if glitch else MASTHEAD.resize((sw, sh), Image.BILINEAR), (round(sx), round(sy)))
        mast.putalpha(Image.composite(mast.getchannel('A'), Image.new('L', im.size, 0), mask))
        im.alpha_composite(mast)
        icon_alpha = 1 - blend
    for ring in range(3):
        hr = size * 74 / 208 + (2 + ring * 2) * S
        if halo > 0:
            d.ellipse([px - hr, py - hr, px + hr, py + hr], fill=((0x8A, 0x8F, 0x96) if glitch else (0x3D, 0xE1, 0xF6)) + (halo // (ring + 1),))
    im.alpha_composite(layer)
    if icon_alpha > 0.01:
        isz = max(1, round(size))
        icon = frame_of(t, sheet=MUTED_SHEET if glitch else None, phase=phase_at(t)).resize((isz, isz), Image.BILINEAR)
        if glitch:
            # Worn textures: rows of the icon gone dead, and a faint misregistered copy one pixel over.
            icon = worn(icon, isz)
            echo = icon.copy()
            echo.putalpha(echo.getchannel('A').point(lambda v: v * 50 // 255))
            im.alpha_composite(echo, (round(px - isz / 2) + S, round(py - isz / 2)))
        if icon_alpha < 1:
            icon.putalpha(icon.getchannel('A').point(lambda v: round(v * icon_alpha)))
        im.alpha_composite(icon, (round(px - isz / 2), round(py - isz / 2)))
    if glitch and clock in TEARS:
        band = (0, round(py - size * 0.15), im.width, round(py + size * 0.05))
        torn = im.crop(band)
        im.paste(torn, (3 * S if clock % 2 else -3 * S, band[1]))
    if glitch:
        im.alpha_composite(NEGLECT[1].resize(im.size, Image.BILINEAR))
        im.alpha_composite(NEGLECT[2].resize(im.size, Image.NEAREST))
        if clock in FLICKER:
            im = Image.blend(im, Image.new('RGBA', im.size, (0, 0, 0, 255)), 0.35)
    return im


MUTED_SHEET = muted(SHEET)
NEGLECT = neglect_layers()


def worn(image, size):
    """A texture that has sat too long: a few thin rows dropped dark, and blocks of it faded, at fixed places."""
    import random
    r = random.Random(41)
    out = image.copy()
    d = ImageDraw.Draw(out)
    h = out.height
    for _ in range(4):
        y = round(r.random() * h)
        d.rectangle([0, y, out.width, y + max(1, h // 60)], fill=(0, 0, 0, 0))
    a = out.getchannel('A')
    fade = Image.new('L', out.size, 255)
    fd = ImageDraw.Draw(fade)
    for _ in range(5):
        x, y = r.random() * out.width, r.random() * h
        fd.rectangle([x, y, x + out.width * 0.12, y + h * 0.08], fill=120)
    # Inside the faded blocks the texture keeps under half its alpha.
    out.putalpha(Image.composite(a, a.point(lambda v: v * 120 // 255), fade.point(lambda v: 255 if v == 255 else 0)))
    return out


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


def replay():
    frames = [beat_frame(t, glitch=True).convert('RGB') for t in range(SPUTTER + GLINT + GROW + EXPAND + BLEND + 7 + HOLD)]
    palette = frames[50].quantize(colors=255, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    out = [f.quantize(palette=palette, dither=Image.Dither.NONE) for f in frames]
    out[0].save(os.path.join(HERE, 'orsa-boot-replay-mockup.gif'), save_all=True, append_images=out[1:],
                duration=50, loop=0, disposal=1)


def main():
    replay()
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

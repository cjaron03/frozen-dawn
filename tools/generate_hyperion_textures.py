#!/usr/bin/env python3
"""SMS-1 Hyperion block textures, 16x16, drawn from code so they can be redrawn.

Look (docs/art/hyperion-storage/cell-look-sketch.html): matte charcoal ceramic cells with fine scratches, held in a
gunmetal frame. The cells never show charge. Cold cells frost along their upper edges; heat stress opens dull orange
cracks that pulse; the final seconds turn the cracks white-hot and flickering; a quench leaves dark scars. Lamps on
the Controller and Ports blink amber during a warning. Noise is seeded per texture, so output is reproducible.

Run: python3 -I tools/generate_hyperion_textures.py
"""
import math
import random
from pathlib import Path
from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/frozendawn/textures/block"

FRAME = (74, 80, 89)
FRAME_LIGHT = (110, 118, 128)
FRAME_DARK = (44, 48, 54)
RIVET = (140, 148, 158)
CHARCOAL = (30, 32, 37)
CELL_LIGHT = (60, 64, 70)
CELL_DARK = (10, 11, 13)
SCRATCH = (90, 96, 104)
FROST = (205, 220, 235)
SCAR = (48, 30, 26)
WINDOW = [(7, 8, 10), (13, 15, 18), (22, 24, 27)]
WINDOW_WALL = [(26, 30, 35), (44, 50, 57)]
CYAN = (43, 111, 128)
LAMP_OFF = (86, 52, 26)
LAMP_ON = (255, 176, 72)
COPPER = (184, 108, 62)

# Crack and scratch paths from the sketch, in texture pixels.
CRACK_A = [(3, 5), (4, 5), (5, 6), (6, 6), (7, 7), (8, 7), (8, 8), (9, 9), (10, 9), (11, 10), (12, 11), (8, 9), (7, 10),
           (7, 11), (10, 6), (11, 5), (12, 4)]
CRACK_B = [(2, 12), (3, 12), (4, 13), (13, 7), (13, 8), (5, 3), (4, 2), (6, 2), (6, 12), (5, 13), (14, 9), (12, 3)]
SCRATCHES = [(2, 9), (3, 8), (4, 8), (5, 7), (11, 13), (12, 12), (13, 12), (9, 3), (10, 3)]


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def save(im, name, frametime=None, interpolate=False):
    im.save(OUT / f"{name}.png")
    meta = OUT / f"{name}.png.mcmeta"
    if frametime is None:
        if meta.exists():
            meta.unlink()
        return
    meta.write_text('{\n  "animation": {\n    "frametime": %d,\n    "interpolate": %s\n  }\n}\n'
                    % (frametime, "true" if interpolate else "false"))


def strip(frames):
    im = Image.new("RGBA", (16, 16 * len(frames)))
    for i, f in enumerate(frames):
        im.paste(f, (0, 16 * i))
    return im


def cell(seed, frost=0.0, frost_top=False, heat=0.0, crack=None, scars=False):
    """One charcoal cell face. frost 0..1 along the upper edge (the whole face when frost_top); heat darkens the
    ceramic; crack is a colour per crack pixel or None."""
    rng = random.Random(seed)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            n = rng.uniform(-5, 5)
            c = tuple(round(v + n) for v in CHARCOAL)
            if x == 0 or y == 0:
                c = mix(c, CELL_LIGHT, 0.5)
            if x == 15 or y == 15:
                c = mix(c, CELL_DARK, 0.6)
            if (x, y) in SCRATCHES:
                c = mix(c, SCRATCH, 0.45)
            if frost > 0:
                if frost_top:
                    edge = 0.9 * max(0.0, 1 - min(x, 15 - x, y, 15 - y) / 4)
                else:
                    edge = max(0.0, 1 - y / 6) * 0.9 + max(0.0, 1 - min(x, 15 - x) / 2) * 0.35
                if rng.random() < frost * edge * 0.7:
                    c = mix(c, FROST, 0.35 + 0.35 * rng.random())
            if heat > 0:
                c = mix(c, (18, 16, 16), heat * 0.4)
            im.putpixel((x, y), c + (255,))
    if scars:
        for x, y in CRACK_A + CRACK_B:
            im.putpixel((x, y), SCAR + (255,))
    if crack:
        for (x, y), c in crack.items():
            im.putpixel((x, y), c + (255,))
    return im


def glow(s):
    """Crack colour by heat 0..1: dull red, orange, then white-hot."""
    return mix((80, 20, 8), (255, 110, 30), s / 0.6) if s < 0.6 else mix((255, 110, 30), (255, 240, 190), (s - 0.6) / 0.4)


def frame_base(seed, rivets=True):
    rng = random.Random(seed)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            n = rng.randint(-4, 4)
            im.putpixel((x, y), tuple(v + n for v in FRAME) + (255,))
    for i in range(16):
        im.putpixel((i, 0), FRAME_LIGHT + (255,)); im.putpixel((0, i), FRAME_LIGHT + (255,))
        im.putpixel((i, 15), FRAME_DARK + (255,)); im.putpixel((15, i), FRAME_DARK + (255,))
    if rivets:
        for x, y in [(3, 3), (12, 3), (3, 12), (12, 12)]:
            im.putpixel((x, y), RIVET + (255,)); im.putpixel((x + 1, y + 1), FRAME_DARK + (255,))
    return im


def lamp(im, x, y, on):
    c = LAMP_ON if on else LAMP_OFF
    for dx in range(2):
        for dy in range(2):
            im.putpixel((x + dx, y + dy), (mix(c, (255, 240, 200), 0.35) if on and dx == 0 and dy == 0 else c) + (255,))


def port(on):
    im = frame_base(41, rivets=False)
    for y in range(4, 12):
        for x in range(4, 12):
            ring = x in (4, 11) or y in (4, 11)
            im.putpixel((x, y), (FRAME_DARK if ring else WINDOW[1]) + (255,))
    for y in range(6, 10):
        for x in range(6, 10):
            im.putpixel((x, y), mix(COPPER, (238, 182, 130), 0.4 if (x + y) % 3 == 0 else 0) + (255,))
    lamp(im, 7, 1, on)
    return im


def controller_front(on):
    # Only a 1 px bezel shows at the sides and 2 px top and bottom; the model cuts the window out of the rest.
    im = frame_base(61, rivets=False)
    lamp(im, 13, 0, on)
    return im


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    save(cell(11), "superconducting_cell_plain")
    save(cell(11, frost=1.0), "superconducting_cell_frosted")
    save(cell(11, frost=1.0, frost_top=True), "superconducting_cell_frosted_top")
    save(cell(11, scars=True), "superconducting_cell_scarred")
    frames = []
    for k in range(16):
        s = 0.3 + 0.28 * (0.5 + 0.5 * math.sin(2 * math.pi * k / 16))
        frames.append(cell(11, heat=0.5, crack={p: glow(s) for p in CRACK_A}))
    save(strip(frames), "superconducting_cell_stressed", frametime=3, interpolate=True)
    rng = random.Random(23)
    frames = []
    for k in range(12):
        flick = 0.75 + 0.25 * rng.random()
        s = min(1.0, (0.85 + 0.15 * math.sin(2 * math.pi * k / 12)) * flick)
        cracks = {p: glow(s) for p in CRACK_A}
        cracks.update({p: glow(s * 0.85) for p in CRACK_B})
        frames.append(cell(11, heat=1.0, crack=cracks))
    save(strip(frames), "superconducting_cell_critical", frametime=1)

    save(frame_base(31), "hyperion_frame")
    save(port(False), "hyperion_port")
    save(strip([port(True), port(False)]), "hyperion_port_alert", frametime=8)
    save(controller_front(False), "hyperion_controller")
    save(strip([controller_front(True), controller_front(False)]), "hyperion_controller_alert", frametime=8)
    window = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            # Lit from the magnets below: brighter low down, with a faint cyan glow along the floor and sides.
            c = mix(WINDOW_WALL[0], WINDOW_WALL[1], y / 15)
            c = mix(c, CYAN, 0.12 + 0.3 * max(0.0, (y - 9) / 6) + (0.15 if min(x, 15 - x) <= 1 else 0))
            window.putpixel((x, y), c + (255,))
    save(window, "hyperion_controller_window")
    inner = Image.new("RGBA", (16, 16))
    rng = random.Random(71)
    for y in range(16):
        for x in range(16):
            inner.putpixel((x, y), tuple(v + rng.randint(-3, 3) for v in FRAME_DARK) + (255,))
    save(inner, "hyperion_controller_inner")

    im = frame_base(81)
    for y in range(3, 13):
        for x in range(3, 13):
            im.putpixel((x, y), (FRAME_LIGHT if y % 2 == 0 else FRAME_DARK) + (255,))
    for x, y in [(7, 7), (8, 7), (7, 8), (8, 8)]:
        im.putpixel((x, y), CYAN + (255,))
    save(im, "cryocooler")
    im = frame_base(91)
    for y in range(3, 13):
        for x in range(3, 13):
            im.putpixel((x, y), (WINDOW[0] if x % 3 != 0 else FRAME_DARK) + (255,))
    for i in range(3):
        for x, y in [(i, 0), (0, i), (15 - i, 15), (15, 15 - i)]:
            im.putpixel((x, y), (214, 170, 52, 255))
    save(im, "quench_vent")


if __name__ == "__main__":
    main()

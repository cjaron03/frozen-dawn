#!/usr/bin/env python3
"""SMS-1 Hyperion silo textures, 16 px per block, drawn from code so they can be redrawn.

Look (docs/hyperion-silo-design.md): black cells that join seamlessly once formed, threaded with two dark blue
superconductor veins; a loose cell keeps a bevelled, bolted edge so a half-built silo still reads as blocks (owner pass 4
texture pass, 2026-11-02). After a quench, burnt streaks run down the cells. The state shows on the
renderer's bars, pipes, lamps and rings, not on the cells. Fittings sit on the same formed charcoal so the silo reads
as one object. Noise is seeded per texture, so output is reproducible.

Run: python3 tools/generate_hyperion_textures.py
"""
import math
import os
import random
from pathlib import Path
from PIL import Image

OUT = Path(os.environ.get("HYPERION_TEX_OUT", Path(__file__).resolve().parent.parent / "src/main/resources/assets/frozendawn/textures/block"))
GONE = ["superconducting_cell_plain", "superconducting_cell_scarred", "superconducting_cell_frosted", "superconducting_cell_frosted_top",
        "superconducting_cell_stressed", "superconducting_cell_critical", "hyperion_controller_window", "hyperion_controller_inner",
        "hyperion_frame", "hyperion_window_swirl"]

CHARCOAL = (30, 32, 37)
CELL_LIGHT = (52, 56, 62)
CELL_DARK = (8, 9, 11)
SCRATCH = (52, 56, 63)
VEIN = (26, 44, 66)
VEIN_GLINT = (60, 120, 170)
GLASS = (12, 17, 26)
GLASS_BAR = (20, 28, 40)
PIN = (214, 140, 84)
SCORCH = [(10, 8, 7), (34, 22, 16), (58, 34, 20)]
METAL = (38, 40, 46)
METAL_LIGHT = (62, 66, 74)
METAL_DARK = (20, 21, 24)
OPENING = (6, 7, 9)
SILL = (48, 52, 58)
FROST = (206, 222, 238)
FROST_SHADE = (150, 172, 196)
STEEL = (96, 101, 110)
STEEL_DARK = (64, 68, 75)
LAMP_OFF = (86, 52, 26)
LAMP_ON = (255, 176, 72)
COPPER = (184, 108, 62)
HAZARD = (214, 170, 52)
SCRATCHES = [(3, 9), (4, 8), (5, 8), (11, 12), (12, 12), (9, 4), (10, 4)]


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


def formed(seed=11, veins=True):
    """Brushed charcoal: a vertical grain (each column a shade off) under a slow swell down the face, with no edge, so
    neighbouring formed cells join without a seam. The outer columns borrow inner columns' shades, so no border stands
    out of the face's own range. Two dark blue superconductor veins run straight through, top to bottom, so a column
    of cells reads as one wired hull; each carries a faint glint that repeats every block."""
    rng = random.Random(seed)
    shade = [rng.choice((-5, -3, -2, 0, 0, 2, 3, 5)) for _ in range(16)]
    shade[0], shade[1], shade[14], shade[15] = shade[6], shade[9], shade[4], shade[11]
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        swell = round(2 * math.sin(2 * math.pi * y / 16))
        for x in range(16):
            c = tuple(v + shade[x] + swell for v in CHARCOAL)
            if x in (6, 9):
                c = mix(c, SCRATCH, 0.45)
            if veins and x in (3, 12):
                c = mix(VEIN, VEIN_GLINT, 0.6 if (y - (2 if x == 3 else 10)) % 16 == 0 else 0)
            im.putpixel((x, y), c + (255,))
    return im


def loose():
    """A formed face with a light top-left and dark bottom-right edge: a single block."""
    im = formed()
    for i in range(16):
        for p, c, t in [((i, 0), CELL_LIGHT, 0.85), ((0, i), CELL_LIGHT, 0.85), ((i, 15), CELL_DARK, 0.9), ((15, i), CELL_DARK, 0.9),
                        ((i, 1), CELL_LIGHT, 0.3), ((1, i), CELL_LIGHT, 0.3), ((i, 14), CELL_DARK, 0.4), ((14, i), CELL_DARK, 0.4)]:
            im.putpixel(p, mix(im.getpixel(p)[:3], c, t) + (255,))
    for x, y in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        im.putpixel((x, y), METAL_LIGHT + (255,))
        im.putpixel((x + 1, y + 1) if x < 8 else (x - 1, y + 1), METAL_DARK + (255,))
    return im


def scorched():
    """Formed plus two burnt streaks that wander but meet themselves top to bottom, so a column of cells tiles."""
    im = formed()
    for centre, width, phase in [(4, 2, 0.0), (11, 1, 1.7)]:
        for y in range(16):
            x0 = centre + round(0.8 * math.sin(2 * math.pi * y / 16 + phase))
            depth = 0.5 + 0.5 * math.sin(2 * math.pi * y / 8 + phase)
            for dx in range(-1, width + 1):
                x = x0 + dx
                if 0 <= x < 16:
                    core = 0 <= dx < width
                    c = mix(SCORCH[1], SCORCH[0], depth) if core else mix(im.getpixel((x, y))[:3], SCORCH[2], 0.35 * depth)
                    im.putpixel((x, y), c + (255,))
    return im


def lamp(im, x, y, on):
    c = LAMP_ON if on else LAMP_OFF
    for dx in range(2):
        for dy in range(2):
            im.putpixel((x + dx, y + dy), (mix(c, (255, 240, 200), 0.35) if on and dx == 0 and dy == 0 else c) + (255,))


def port(on):
    """A bevelled socket, lit from the top left, holding four copper pins."""
    im = formed(41, veins=False)
    for y in range(3, 13):
        for x in range(3, 13):
            outer = x in (3, 12) or y in (3, 12)
            inner = x in (4, 11) or y in (4, 11)
            lit = x <= 4 or y <= 4
            c = (METAL_LIGHT if lit else METAL_DARK) if outer else (METAL if inner else OPENING)
            im.putpixel((x, y), c + (255,))
    for px, py in [(6, 6), (8, 6), (6, 8), (8, 8)]:
        for dx in range(2):
            for dy in range(2):
                im.putpixel((px + dx, py + dy), (mix(PIN, (255, 220, 180), 0.5) if dx == 0 and dy == 0 else PIN) + (255,))
    lamp(im, 7, 0, on)
    return im


def controller_front(on):
    """The bottom of the window: a dark opening 14 px wide, which the renderer fills, above a 2 px sill with its lamp."""
    im = formed(61, veins=False)
    for y in range(16):
        for x in range(1, 15):
            if y >= 14:
                c = mix(SILL, METAL_DARK, 0.6 if y == 15 else 0)
            else:
                c = GLASS_BAR if 2 <= x <= 13 and 5 <= y <= 10 else GLASS
                if x + y in (5, 6) or x + y in (9,):
                    c = mix(c, (70, 90, 110), 0.35)
            im.putpixel((x, y), c + (255,))
    lamp(im, 12, 14, on)
    return im


def cryocooler():
    """A frosted pipe flange 8 px across, centred on the pipe 5 px above the block's floor."""
    rng = random.Random(81)
    im = formed(81)
    for y in range(1, 6):
        for x in range(2, 14):
            if x % 2 == 0:
                im.putpixel((x, y), mix(METAL_LIGHT, FROST, 0.5 if y == 1 else 0.15 * rng.random()) + (255,))
            else:
                im.putpixel((x, y), METAL_DARK + (255,))
    for y in range(7, 15):
        for x in range(4, 12):
            rim = x in (4, 11) or y in (7, 14)
            c = mix(FROST_SHADE, FROST, rng.random()) if rim else mix(OPENING, FROST_SHADE, 0.15 if rng.random() < 0.2 else 0)
            im.putpixel((x, y), c + (255,))
    return im


def vent():
    im = formed(91)
    for y in range(2, 14):
        for x in range(2, 14):
            k = x % 3
            c = OPENING if k == 0 else (METAL_LIGHT if k == 1 and y == 2 else METAL if k == 1 else METAL_DARK)
            im.putpixel((x, y), c + (255,))
    for i in range(3):
        for x, y in [(i, 0), (0, i), (15 - i, 15), (15, 15 - i)]:
            im.putpixel((x, y), HAZARD + (255,))
    return im


def ring():
    """Dark ring metal: a faint lit top row, a shadowed bottom."""
    rng = random.Random(101)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = tuple(round(v + rng.uniform(-3, 3)) for v in METAL)
            c = mix(c, METAL_LIGHT, 0.6) if y < 2 else mix(c, METAL_DARK, 0.6) if y >= 14 else c
            im.putpixel((x, y), c + (255,))
    return im


def pipe(frost):
    rng = random.Random(111 if frost else 121)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            if frost:
                c = mix(FROST_SHADE, FROST, rng.random() * 0.8 + (0.2 if x < 8 else 0))
                if rng.random() < 0.08:
                    c = (240, 248, 255)
            else:
                c = tuple(round(v + rng.uniform(-4, 4)) for v in STEEL)
                if y % 8 == 0:
                    c = STEEL_DARK
            im.putpixel((x, y), c + (255,))
    return im


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name in GONE:
        for path in (OUT / f"{name}.png", OUT / f"{name}.png.mcmeta"):
            if path.exists():
                path.unlink()
    save(loose(), "superconducting_cell_loose")
    save(formed(), "superconducting_cell_formed")
    save(scorched(), "superconducting_cell_scorched")
    save(controller_front(False), "hyperion_controller")
    save(strip([controller_front(True), controller_front(False)]), "hyperion_controller_alert", frametime=8)
    save(port(False), "hyperion_port")
    save(strip([port(True), port(False)]), "hyperion_port_alert", frametime=8)
    save(cryocooler(), "cryocooler")
    save(vent(), "quench_vent")
    save(ring(), "hyperion_ring")
    save(pipe(True), "hyperion_pipe_frost")
    save(pipe(False), "hyperion_pipe_bare")


if __name__ == "__main__":
    main()

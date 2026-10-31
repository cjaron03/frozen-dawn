#!/usr/bin/env python3
"""ORSA AMR-1 Icarus block and capsule textures, 16x16, drawn from code so they can be redrawn.

Look: heavy containment hardware. Dark graphite plate with gold-anodized trim, unlike the SFR-1's pale ceramic, lit by
violet where antimatter lives and cyan where the shield is made. Every block has its own motif: a caged core node,
stabilizer emitters with a lens, a matter injector nozzle, an open truss frame, banded injector casing, a finned
stabilizer housing, a cell-gauge reserve bank, a console Controller, a power port with bus bars, a matter port with a
hopper throat, a capsule dock with cradle slots and an Antiparticle Foundry with a ring accelerator. Emissive faces
are separate animated overlays with .mcmeta timing; the block models mark them full-bright. Noise is seeded per texture, so output is reproducible.
The running reactor's plasma, shield lattice and corona sprite go to textures/entity/icarus.

Run: python3 -I tools/generate_icarus_textures.py
"""
import json
import math
import random
from pathlib import Path
from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/frozendawn/textures/block"
ITEM_OUT = OUT.parent / "item"

GRAPHITE = [(92, 96, 106), (70, 74, 83), (54, 57, 65), (40, 42, 49), (27, 29, 34)]
GOLD = [(120, 88, 36), (170, 128, 52), (212, 168, 76), (240, 206, 120)]
VIOLET = [(54, 20, 92), (110, 44, 190), (170, 100, 245), (226, 190, 255)]
CYAN = [(14, 70, 88), (30, 150, 180), (90, 215, 235), (200, 248, 255)]
AMBER = [(120, 60, 10), (210, 120, 30), (250, 180, 80), (255, 236, 190)]
RED = (190, 40, 36)
GREEN = (70, 200, 110)
OUTLINE = (20, 21, 25)
SCREEN = (8, 10, 16)
CLEAR = (0, 0, 0, 0)


def new(h=16):
    return Image.new("RGBA", (16, h), CLEAR)


def put(im, x, y, c, oy=0):
    if 0 <= x < 16 and 0 <= y < 16:
        im.putpixel((x, y + oy), c + (255,) if len(c) == 3 else c)


def rect(im, x0, y0, x1, y1, c, oy=0):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            put(im, x, y, c, oy)


def jitter(c, rng, amount=4):
    d = rng.randint(-amount, amount)
    return tuple(max(0, min(255, v + d)) for v in c)


def plate(seed, base=GRAPHITE, grain=4):
    """A bevelled graphite plate: black outline, lit top-left edge, shaded bottom-right, brushed grain."""
    rng = random.Random(seed)
    im = new()
    for y in range(16):
        row = rng.randint(-2, 2)
        for x in range(16):
            c = jitter(base[2], rng, grain)
            put(im, x, y, tuple(max(0, min(255, v + row)) for v in c))
    for i in range(16):
        put(im, i, 0, OUTLINE); put(im, i, 15, OUTLINE); put(im, 0, i, OUTLINE); put(im, 15, i, OUTLINE)
    for i in range(1, 15):
        put(im, i, 1, base[1]); put(im, 1, i, base[1])
        put(im, i, 14, base[3]); put(im, 14, i, base[3])
    return im, rng


def bolts(im, at=((2, 2), (13, 2), (2, 13), (13, 13))):
    for x, y in at:
        put(im, x, y, GOLD[2]); put(im, x + (1 if x < 8 else -1), y + (1 if y < 8 else -1), GOLD[0])


def gold_band(im, y0, y1):
    for y in range(y0, y1 + 1):
        for x in range(1, 15):
            put(im, x, y, GOLD[1] if y not in (y0, y1) else (GOLD[2] if y == y0 else GOLD[0]))


def hazard(im, y0, y1):
    for y in range(y0, y1 + 1):
        for x in range(1, 15):
            put(im, x, y, GOLD[2] if (x + y) % 4 < 2 else GRAPHITE[4])


def save(im, name, out=OUT):
    im.save(out / f"{name}.png")


def animate(name, frames, frametime, out=OUT):
    """Stacks frame images into one strip with its .mcmeta."""
    strip = new(16 * len(frames))
    for i, f in enumerate(frames):
        strip.paste(f, (0, 16 * i))
    strip.save(out / f"{name}.png")
    (out / f"{name}.png.mcmeta").write_text(json.dumps({"animation": {"frametime": frametime, "interpolate": True}}, indent=2) + "\n")


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def glow_frames(n, draw):
    """n frames of a pulse from 0 to 1 and back; draw(im, phase) paints the lit pixels."""
    frames = []
    for i in range(n):
        im = new()
        draw(im, 0.5 - 0.5 * math.cos(2 * math.pi * i / n), i)
        frames.append(im)
    return frames


# Halsey Core: the node at the reactor's heart, a gold cage around a violet singularity port.
def halsey_core():
    im, rng = plate(71001, base=GRAPHITE)
    rect(im, 3, 3, 12, 12, GRAPHITE[4])
    for i in range(3, 13):
        put(im, i, 3, GOLD[2]); put(im, i, 12, GOLD[0]); put(im, 3, i, GOLD[2]); put(im, 12, i, GOLD[0])
    for d in range(4, 12):
        put(im, d, d, GOLD[1]); put(im, 15 - d, d, GOLD[1])
    rect(im, 6, 6, 9, 9, VIOLET[0])
    bolts(im)
    save(im, "halsey_core")

    def draw(im, p, i):
        c = lerp(VIOLET[1], VIOLET[3], p)
        rect(im, 6, 6, 9, 9, lerp(VIOLET[0], VIOLET[2], p))
        put(im, 7, 7, c); put(im, 8, 8, c); put(im, 7, 8, VIOLET[3]); put(im, 8, 7, c)
        for d in (4, 5, 10, 11):
            if (d + i) % 4 == 0:
                put(im, d, d, lerp(VIOLET[1], VIOLET[2], p)); put(im, 15 - d, d, lerp(VIOLET[1], VIOLET[2], p))
    animate("halsey_core_glow", glow_frames(8, draw), 3)


# Field Stabilizer: the emitter face with a ringed lens, plated flanks with cooling slots, and capped ends.
def icarus_stabilizer():
    im, rng = plate(71002)
    for r, c in ((6.5, GOLD[1]), (5.2, GRAPHITE[4]), (3.6, GOLD[2])):
        for x in range(16):
            for y in range(16):
                if abs(math.hypot(x - 7.5, y - 7.5) - r) < 0.6:
                    put(im, x, y, c)
    rect(im, 6, 6, 9, 9, CYAN[0])
    bolts(im)
    save(im, "icarus_stabilizer_emitter")

    im, rng = plate(71003)
    for y in range(3, 13, 2):
        rect(im, 3, y, 12, y, GRAPHITE[4])
    gold_band(im, 1, 2)
    gold_band(im, 13, 14)
    save(im, "icarus_stabilizer_side")

    im, rng = plate(71004)
    rect(im, 4, 4, 11, 11, GRAPHITE[3])
    for x in range(4, 12):
        put(im, x, 4, GOLD[2]); put(im, x, 11, GOLD[0])
    rect(im, 7, 1, 8, 14, GRAPHITE[4])
    bolts(im)
    save(im, "icarus_stabilizer_end")

    def draw(im, p, i):
        rect(im, 6, 6, 9, 9, lerp(CYAN[1], CYAN[3], p))
        put(im, 7, 7, CYAN[3]); put(im, 8, 8, CYAN[3])
        a = 2 * math.pi * i / 8
        for k in range(3):
            ang = a + k * 2 * math.pi / 3
            put(im, round(7.5 + 3.6 * math.cos(ang)), round(7.5 + 3.6 * math.sin(ang)), CYAN[2])
    animate("icarus_stabilizer_emitter_glow", glow_frames(8, draw), 2)


# Matter Injector: a hot nozzle face on top, ribbed flanks with feed pipes, a bolted base.
def icarus_injector():
    im, rng = plate(71005)
    for x in range(16):
        for y in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 6.2:
                put(im, x, y, GOLD[1] if d > 5.2 else GRAPHITE[4] if d > 2.6 else AMBER[0])
    bolts(im)
    save(im, "icarus_injector_nozzle")

    im, rng = plate(71006)
    for x in (4, 11):
        rect(im, x, 1, x + 1, 14, GOLD[1]); put(im, x, 1, GOLD[3])
    for y in range(3, 14, 3):
        rect(im, 2, y, 13, y, GRAPHITE[4])
    save(im, "icarus_injector_side")

    im, rng = plate(71007)
    hazard(im, 2, 3)
    hazard(im, 12, 13)
    rect(im, 5, 6, 10, 9, GRAPHITE[4])
    save(im, "icarus_injector_bottom")

    def draw(im, p, i):
        for x in range(16):
            for y in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if d < 2.6:
                    put(im, x, y, lerp(AMBER[1], AMBER[3], p * (1 - d / 3) + 0.3))
    animate("icarus_injector_nozzle_glow", glow_frames(6, draw), 2)


# Frame: an open gold-edged truss over dark plate, the cheap structural block.
def icarus_frame():
    im, rng = plate(71008, grain=3)
    rect(im, 2, 2, 13, 13, GRAPHITE[4])
    for i in range(2, 14):
        put(im, i, 2, GOLD[1]); put(im, i, 13, GOLD[0]); put(im, 2, i, GOLD[1]); put(im, 13, i, GOLD[0])
        put(im, i, i, GRAPHITE[1]); put(im, 15 - i, i, GRAPHITE[1])
    save(im, "icarus_frame")


# Injector casing: the column around the matter line, banded in gold with hazard stripes on its ends.
def icarus_injector_casing():
    im, rng = plate(71009)
    rect(im, 5, 1, 10, 14, GRAPHITE[3])
    for y in (4, 11):
        gold_band(im, y, y + 1)
    for y in range(1, 15):
        put(im, 5, y, GRAPHITE[1]); put(im, 10, y, GRAPHITE[4])
    save(im, "icarus_injector_casing")

    im, rng = plate(71010)
    hazard(im, 1, 14)
    rect(im, 4, 4, 11, 11, GRAPHITE[3])
    rect(im, 6, 6, 9, 9, GRAPHITE[4])
    save(im, "icarus_injector_casing_end")


# Stabilizer housing: the bulk of each stabilizer, finned like a heat sink.
def icarus_stabilizer_housing():
    im, rng = plate(71011)
    for x in range(3, 13, 2):
        for y in range(2, 14):
            put(im, x, y, GRAPHITE[1] if y < 8 else GRAPHITE[0])
            put(im, x + 1, y, GRAPHITE[4])
    gold_band(im, 14, 14)
    bolts(im, ((2, 2), (13, 2)))
    save(im, "icarus_stabilizer_housing")


# Reserve bank: a column of charge cells behind a window on each side, gauges on top.
def icarus_reserve_bank():
    im, rng = plate(71012)
    rect(im, 3, 2, 12, 13, SCREEN)
    for y in range(3, 13, 2):
        rect(im, 4, y, 11, y, GRAPHITE[3])
    for x in range(3, 13):
        put(im, x, 2, GOLD[2]); put(im, x, 13, GOLD[0])
    save(im, "icarus_reserve_bank")

    im, rng = plate(71013)
    for cx in (4, 11):
        for x in range(16):
            for y in range(16):
                if abs(math.hypot(x - cx, y - 7.5) - 2.6) < 0.6:
                    put(im, x, y, GOLD[1])
        put(im, cx, 7, RED); put(im, cx, 8, GRAPHITE[4])
    save(im, "icarus_reserve_bank_top")

    frames = []
    for i in range(10):
        f = new()
        level = i if i < 6 else 10 - i
        for k in range(5):
            y = 11 - 2 * k
            c = CYAN[2] if k < level else CYAN[0]
            if k < level or k == level:
                rect(f, 4, y, 11, y, c if k < level else lerp(CYAN[0], CYAN[1], 0.5))
        frames.append(f)
    animate("icarus_reserve_bank_glow", frames, 6)


# Controller: the console, a dark screen with a mimic of the reactor cross and an alarm lamp row.
def icarus_controller():
    im, rng = plate(71014)
    rect(im, 2, 2, 13, 9, SCREEN)
    for x in range(2, 14):
        put(im, x, 2, GOLD[2]); put(im, x, 9, GOLD[0])
    for x, c in ((3, GREEN), (6, GOLD[2]), (9, RED)):
        rect(im, x, 11, x + 1, 12, GRAPHITE[4]); put(im, x, 11, c)
    rect(im, 12, 11, 13, 13, GRAPHITE[4])
    save(im, "icarus_controller")

    im, rng = plate(71015)
    rect(im, 2, 6, 13, 9, GRAPHITE[3])
    for x in range(3, 13, 3):
        rect(im, x, 7, x + 1, 8, GOLD[1])
    save(im, "icarus_controller_side")

    im, rng = plate(71016)
    gold_band(im, 7, 8)
    bolts(im)
    save(im, "icarus_controller_top")

    frames = []
    for i in range(8):
        f = new()
        # The mimic: core in the middle, four stabilizers, beams lit in turn, a scan line sweeping down.
        put(f, 7, 5, VIOLET[3]); put(f, 8, 5, VIOLET[3]); put(f, 7, 6, VIOLET[2]); put(f, 8, 6, VIOLET[2])
        for x, y in ((4, 5), (11, 5), (7, 3), (8, 8)):
            put(f, x, y, CYAN[2])
        lit = i % 4
        beams = [((5, 5), (6, 5)), ((9, 5), (10, 5)), ((7, 4), (8, 4)), ((8, 7), (7, 7))]
        for k, (a, b) in enumerate(beams):
            if k <= lit:
                put(f, *a, CYAN[1]); put(f, *b, CYAN[1])
        y = 3 + i % 6
        for x in range(3, 13):
            if f.getpixel((x, y))[3] == 0:
                put(f, x, y, (24, 60, 52, 150))
        put(f, 3, 11, GREEN if i % 2 == 0 else lerp(GREEN, (0, 0, 0), 0.5))
        frames.append(f)
    animate("icarus_controller_glow", frames, 4)


# Power port: heavy bus bars and a lightning plate, wired to the grid.
def icarus_power_port():
    im, rng = plate(71017)
    rect(im, 3, 3, 12, 12, GRAPHITE[4])
    for x in (4, 7, 10):
        rect(im, x, 3, x + 1, 12, GOLD[1]); put(im, x, 3, GOLD[3]); put(im, x + 1, 12, GOLD[0])
    for x, y in ((8, 5), (7, 6), (6, 7), (7, 7), (8, 7), (9, 7), (8, 8), (7, 9), (6, 10)):
        put(im, x, y, AMBER[2])
    bolts(im)
    save(im, "icarus_power_port")


# Matter port: a funnel throat that takes ordinary matter, framed in hazard stripes.
def icarus_matter_port():
    im, rng = plate(71018)
    hazard(im, 1, 2)
    hazard(im, 13, 14)
    for r in range(5, 0, -1):
        rect(im, 8 - r, 8 - r, 7 + r, 7 + r, lerp(GRAPHITE[1], GRAPHITE[4], (5 - r) / 4))
    rect(im, 7, 7, 8, 8, OUTLINE)
    save(im, "icarus_matter_port")


# Capsule dock: four cradle slots on top, padded flanks, a plain base.
def icarus_capsule_dock():
    im, rng = plate(71019)
    for x, y in ((3, 3), (9, 3), (3, 9), (9, 9)):
        rect(im, x, y, x + 3, y + 3, GOLD[0])
        rect(im, x + 1, y + 1, x + 2, y + 2, GRAPHITE[4])
    save(im, "icarus_capsule_dock_top")

    im, rng = plate(71020)
    for y in range(3, 13, 3):
        rect(im, 2, y, 13, y + 1, GRAPHITE[3]); rect(im, 2, y, 13, y, GRAPHITE[1])
    put(im, 12, 2, VIOLET[1])
    save(im, "icarus_capsule_dock_side")

    im, rng = plate(71021)
    rect(im, 3, 3, 12, 12, GRAPHITE[3])
    bolts(im)
    save(im, "icarus_capsule_dock_bottom")

    def draw(im, p, i):
        for k, (x, y) in enumerate(((3, 3), (9, 3), (3, 9), (9, 9))):
            q = 0.5 - 0.5 * math.cos(2 * math.pi * (i / 8 + k / 4))
            rect(im, x + 1, y + 1, x + 2, y + 2, lerp(VIOLET[1], VIOLET[3], q))
    animate("icarus_capsule_dock_glow", glow_frames(8, draw), 3)


# Capsules: a gold-capped magnetic bottle, violet and pulsing when full, dark glass when empty.
def capsule(full):
    frames = []
    for i in range(8 if full else 1):
        f = new()
        p = 0.5 - 0.5 * math.cos(2 * math.pi * i / 8)
        rect(f, 5, 2, 10, 3, GOLD[2]); rect(f, 5, 12, 10, 13, GOLD[0])
        rect(f, 6, 1, 9, 1, GOLD[1]); rect(f, 6, 14, 9, 14, GOLD[1])
        rect(f, 5, 4, 10, 11, OUTLINE)
        rect(f, 6, 4, 9, 11, lerp(VIOLET[1], VIOLET[2], p) if full else (36, 40, 52))
        if full:
            rect(f, 7, 6, 8, 9, lerp(VIOLET[2], VIOLET[3], p))
        put(f, 6, 5, (220, 230, 245)); put(f, 6, 6, (170, 180, 200))
        for y in (4, 11):
            put(f, 5, y, GOLD[1]); put(f, 10, y, GOLD[1])
        frames.append(f)
    if full:
        animate("antimatter_capsule", frames, 3, ITEM_OUT)
    else:
        save(frames[0], "empty_antimatter_capsule", ITEM_OUT)


# Antiparticle Foundry: a ring accelerator on the front, magnet coils down the sides, a capsule port on top. While it
# runs, a violet bunch circles the ring and the trap at its centre fills.
RING = [(x, y) for x, y in ((5, 3), (6, 3), (7, 3), (8, 3), (9, 3), (10, 3), (11, 4), (12, 5), (12, 6), (12, 7), (12, 8), (12, 9),
                            (11, 10), (10, 11), (9, 11), (8, 11), (7, 11), (6, 11), (5, 11), (4, 10), (3, 9), (3, 8), (3, 7),
                            (3, 6), (3, 5), (4, 4))]


def antiparticle_foundry():
    im, rng = plate(71030)
    for x, y in RING:
        put(im, x, y, GRAPHITE[4])
    for x, y in ((7, 2), (12, 7), (7, 12), (2, 7)):
        rect(im, x, y, x + 1, y, GOLD[1]) if y in (2, 12) else rect(im, x, y - 1, x, y, GOLD[1])
    rect(im, 6, 6, 9, 8, SCREEN)
    for x in range(6, 10):
        put(im, x, 6, GOLD[0])
    hazard(im, 14, 15)
    save(im, "antiparticle_foundry")

    im, rng = plate(71031)
    for y0 in (2, 6, 10):
        rect(im, 3, y0, 12, y0 + 2, GOLD[0])
        for x in range(3, 13):
            put(im, x, y0 + 1, GOLD[1] if x % 2 else GOLD[2])
    bolts(im)
    save(im, "antiparticle_foundry_side")

    im, rng = plate(71032)
    rect(im, 5, 5, 10, 10, GRAPHITE[4])
    rect(im, 6, 6, 9, 9, SCREEN)
    for x in range(5, 11):
        put(im, x, 5, GOLD[2]); put(im, x, 10, GOLD[0])
    bolts(im)
    save(im, "antiparticle_foundry_top")

    frames = []
    for i in range(len(RING) // 2):
        f = new()
        for k in range(3):
            put(f, *RING[(2 * i - k) % len(RING)], VIOLET[3 - k])
        fill = i * 4 // (len(RING) // 2)
        for x in range(6, 10):
            for y in range(8 - fill, 9):
                if y >= 7:
                    put(f, x, y, VIOLET[1] if (x + y + i) % 3 else VIOLET[2])
        frames.append(f)
    animate("antiparticle_foundry_glow", frames, 2)


# Render textures for the running reactor, tiled over spheres by HalseyCoreRenderer and tinted by vertex colour, so they
# are greyscale. Every one tiles seamlessly: noise lattices wrap at the tile edge.
FX_OUT = OUT.parent / "entity/icarus"


def wrapped_noise(size, cells, rng):
    """Smooth value noise on a cells x cells lattice that wraps, sampled at size x size."""
    grid = [[rng.random() for _ in range(cells)] for _ in range(cells)]
    out = []
    for y in range(size):
        row = []
        for x in range(size):
            fx, fy = x * cells / size, y * cells / size
            x0, y0 = int(fx), int(fy)
            tx, ty = fx - x0, fy - y0
            tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
            a, b = grid[y0 % cells][x0 % cells], grid[y0 % cells][(x0 + 1) % cells]
            c, d = grid[(y0 + 1) % cells][x0 % cells], grid[(y0 + 1) % cells][(x0 + 1) % cells]
            row.append((a + (b - a) * tx) * (1 - ty) + (c + (d - c) * tx) * ty)
        out.append(row)
    return out


def icarus_plasma():
    """Plasma surface, 128 px: bright granules parted by dark lanes (wrapped Worley cells) over rolling fBm turbulence."""
    size, rng = 128, random.Random(62001)
    octaves = [(wrapped_noise(size, cells, rng), weight) for cells, weight in ((4, 0.5), (8, 0.28), (16, 0.15), (32, 0.07))]
    seeds = [(rng.random() * size, rng.random() * size) for _ in range(90)]
    im = Image.new("RGBA", (size, size))
    for y in range(size):
        for x in range(size):
            near = []
            for sx, sy in seeds:
                dx = min(abs(x - sx), size - abs(x - sx))
                dy = min(abs(y - sy), size - abs(y - sy))
                near.append(dx * dx + dy * dy)
            near.sort()
            lane = min(1.0, (math.sqrt(near[1]) - math.sqrt(near[0])) / 4)
            turb = sum(n[y][x] * w for n, w in octaves)
            v = 0.3 + 0.28 * lane ** 0.5 + 1.1 * (turb - 0.5) + 0.12
            g = max(0, min(255, round(255 * v)))
            im.putpixel((x, y), (g, g, g, 255))
    save(im, "plasma", FX_OUT)


def icarus_shield():
    """Shield lattice, 128 px: a wrapping 8 x 4 hexagon grid, bright edges with a soft halo, each cell faintly tinted so the
    lattice shimmers as it scrolls. Premultiplied grey, so it works in the additive layer as well as the translucent one."""
    size, cols, rows = 128, 8, 4
    rng = random.Random(62002)
    shade = {}
    im = Image.new("RGBA", (size, size))
    sq3 = math.sqrt(3)
    for y in range(size):
        for x in range(size):
            # Lattice units: one tile is cols hexes across and rows hex row-pairs down, so it wraps exactly.
            px, py = x / size * cols * sq3, y / size * rows * 3
            best = None
            for cy in range(-1, rows * 2 + 2):
                for cx in range(-1, cols + 2):
                    hx, hy = cx * sq3 + (sq3 / 2 if cy % 2 else 0), cy * 1.5
                    d = (px - hx) ** 2 + (py - hy) ** 2
                    if best is None or d < best[0]:
                        best = (d, cx, cy, hx, hy)
            _, cx, cy, hx, hy = best
            # Hexagonal distance from the centre: 1 at the edge of a pointy-top hexagon of circumradius 1.
            ax, ay = abs(px - hx), abs(py - hy)
            edge = max(ax / (sq3 / 2), (ax * 0.5 + ay * sq3 / 2) / (sq3 / 2))
            key = (cx % cols, cy % (rows * 2))
            if key not in shade:
                shade[key] = 0.05 + 0.1 * rng.random()
            line = max(0.0, 1 - abs(1 - edge) / 0.09)
            halo = max(0.0, 1 - abs(1 - edge) / 0.32) ** 2
            a = min(1.0, line + 0.45 * halo + shade[key])
            g = round(255 * a)
            im.putpixel((x, y), (g, g, g, g))
    save(im, "shield", FX_OUT)


def icarus_glow():
    """Corona sprite, 64 px: a soft white disc falling off to nothing, for camera-facing halos."""
    size = 64
    im = Image.new("RGBA", (size, size))
    for y in range(size):
        for x in range(size):
            r = math.hypot(x + 0.5 - size / 2, y + 0.5 - size / 2) / (size / 2)
            v = max(0.0, 1 - r) ** 2.2
            g = round(255 * v)
            im.putpixel((x, y), (g, g, g, g))
    save(im, "glow", FX_OUT)


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    ITEM_OUT.mkdir(parents=True, exist_ok=True)
    for f in (halsey_core, icarus_stabilizer, icarus_injector, icarus_frame, icarus_injector_casing, icarus_stabilizer_housing,
              icarus_reserve_bank, icarus_controller, icarus_power_port, icarus_matter_port, icarus_capsule_dock,
              antiparticle_foundry):
        f()
    capsule(True)
    capsule(False)
    FX_OUT.mkdir(parents=True, exist_ok=True)
    for f in (icarus_plasma, icarus_shield, icarus_glow):
        f()

#!/usr/bin/env python3
"""SFR-1 Solstice Reactor block textures, 16x16, drawn from code so they can be redrawn.

Look: a clean fusion lab. Pale ceramic and brushed-steel panels, unlike the CFU-7's dark casing, with the ORSA
blue and orange the rest of the base uses as accents. Every block has its own motif so the torus reads at a glance:
copper coil windings, silver lithium tiles, dark tungsten First Wall bricks, a blue plasma core, a console
Controller, and ports colour-coded by fitting. Noise is seeded per texture, so output is reproducible.

Run: python3 -I tools/generate_sfr_textures.py
"""
import random
from pathlib import Path
from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/frozendawn/textures/block"

# Ceramic panel, light to dark.
CERAMIC = [(226, 230, 233), (205, 210, 215), (184, 190, 197), (156, 164, 172), (118, 126, 136)]
STEEL = [(176, 184, 192), (146, 154, 163), (112, 120, 130), (82, 90, 100)]
OUTLINE = (74, 82, 92)
# ORSA accents, sampled from the CFU-7 and base textures.
BLUE = [(30, 60, 120), (60, 120, 230), (130, 190, 255), (205, 232, 255)]
ORANGE = [(150, 82, 48), (184, 108, 62), (214, 140, 86), (229, 160, 100)]
COPPER = [(104, 54, 34), (150, 82, 48), (184, 108, 62), (214, 140, 86), (238, 182, 130)]
TUNGSTEN = [(34, 37, 42), (48, 52, 58), (62, 67, 74), (80, 86, 94)]
SCREEN = (16, 22, 30)


def new():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def put(im, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        im.putpixel((x, y), c + (255,) if len(c) == 3 else c)


def rect(im, x0, y0, x1, y1, c):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            put(im, x, y, c)


def jitter(c, rng, amount=5):
    d = rng.randint(-amount, amount)
    return tuple(max(0, min(255, v + d)) for v in c)


def panel(seed, base=CERAMIC, grain=4):
    """A bevelled ceramic tile: dark outline, light top-left edge, shaded bottom-right, faint grain."""
    rng = random.Random(seed)
    im = new()
    for x in range(16):
        for y in range(16):
            put(im, x, y, jitter(base[1], rng, grain))
    for i in range(16):
        put(im, i, 0, OUTLINE); put(im, i, 15, OUTLINE); put(im, 0, i, OUTLINE); put(im, 15, i, OUTLINE)
    for i in range(1, 15):
        put(im, i, 1, base[0]); put(im, 1, i, base[0])
        put(im, i, 14, base[3]); put(im, 14, i, base[3])
    return im, rng


def rivets(im, at=((2, 2), (13, 2), (2, 13), (13, 13))):
    for x, y in at:
        put(im, x, y, CERAMIC[4]); put(im, x + (1 if x < 8 else -1), y + (1 if y < 8 else -1), CERAMIC[0])


def bevel_box(im, x0, y0, x1, y1, fill, light, dark):
    rect(im, x0, y0, x1, y1, fill)
    for x in range(x0, x1 + 1):
        put(im, x, y0, dark); put(im, x, y1, light)
    for y in range(y0, y1 + 1):
        put(im, x0, y, dark); put(im, x1, y, light)


def shell():
    im, rng = panel(51101)
    rivets(im)
    # A horizontal service seam with two blue status lamps.
    for x in range(2, 14):
        put(im, x, 7, CERAMIC[3]); put(im, x, 8, CERAMIC[0])
    put(im, 6, 7, BLUE[1]); put(im, 9, 7, BLUE[2])
    # Orange ORSA stripe along the bottom.
    for x in range(3, 13):
        put(im, x, 12, ORANGE[2] if x % 3 else ORANGE[1])
    return im


def controller():
    im, rng = panel(51102)
    rivets(im, ((2, 13), (13, 13)))
    # Recessed console screen with blue readout lines.
    bevel_box(im, 2, 2, 13, 9, SCREEN, CERAMIC[0], CERAMIC[4])
    for y, length in ((4, 7), (6, 5), (8, 8)):
        for x in range(4, 4 + length):
            put(im, x, y, BLUE[1] if x % 4 else BLUE[2])
    put(im, 12, 4, ORANGE[3])
    # Start button (orange) and two blue keys.
    bevel_box(im, 3, 11, 6, 13, ORANGE[2], ORANGE[3], ORANGE[0])
    bevel_box(im, 8, 11, 9, 12, BLUE[1], BLUE[2], BLUE[0])
    bevel_box(im, 11, 11, 12, 12, BLUE[1], BLUE[2], BLUE[0])
    return im


def magnet_coil():
    im, rng = panel(51103, STEEL, 3)
    # Copper windings: horizontal turns, each lit on top and split from the next by a dark gap.
    for y in range(2, 14):
        tone = (COPPER[4], COPPER[2], COPPER[0])[(y - 2) % 3]
        for x in range(2, 14):
            put(im, x, y, jitter(tone, rng, 6))
    # Two steel clamp straps and a blue superconductor lead between them.
    for y in range(2, 14):
        for x in (4, 11):
            put(im, x, y, STEEL[2]); put(im, x + 1, y, STEEL[0])
        put(im, 7, y, BLUE[1] if y % 2 else BLUE[2]); put(im, 8, y, BLUE[0])
    return im


def lithium_blanket():
    im, rng = panel(51104)
    # Four silver lithium tiles in a blue-grouted frame.
    rect(im, 2, 2, 13, 13, BLUE[0])
    silver = [(236, 232, 240), (214, 210, 222), (188, 184, 200)]
    for x0, y0 in ((3, 3), (9, 3), (3, 9), (9, 9)):
        for x in range(x0, x0 + 4):
            for y in range(y0, y0 + 4):
                put(im, x, y, jitter(silver[1], rng, 4))
        for i in range(4):
            put(im, x0 + i, y0, silver[0]); put(im, x0, y0 + i, silver[0])
            put(im, x0 + i, y0 + 3, silver[2]); put(im, x0 + 3, y0 + i, silver[2])
    put(im, 4, 4, BLUE[3]); put(im, 10, 10, BLUE[3])
    return im


def first_wall(worn=False):
    rng = random.Random(51105 + worn)
    im = new()
    # Plasma-facing tungsten bricks, staggered, with a blue-steel edge.
    for x in range(16):
        for y in range(16):
            put(im, x, y, jitter(TUNGSTEN[2], rng, 4))
    for y in range(16):
        if y % 4 == 3:
            for x in range(16):
                put(im, x, y, TUNGSTEN[0])
        else:
            offset = 0 if (y // 4) % 2 == 0 else 4
            for x in range(16):
                if (x + offset) % 8 == 7:
                    put(im, x, y, TUNGSTEN[0])
                elif y % 4 == 0:
                    put(im, x, y, TUNGSTEN[3])
    for i in range(16):
        put(im, i, 0, STEEL[2]); put(im, 0, i, STEEL[2]); put(im, i, 15, STEEL[3]); put(im, 15, i, STEEL[3])
    if worn:
        # Heat-scorched orange glints and cracks.
        for x, y in ((3, 5), (4, 5), (10, 2), (11, 9), (12, 9), (6, 13), (2, 10)):
            put(im, x, y, ORANGE[1])
        for x, y in ((4, 6), (5, 7), (5, 8), (6, 9), (10, 3), (9, 4), (12, 10), (13, 11)):
            put(im, x, y, (20, 20, 22))
        for x, y in ((3, 6), (11, 10), (6, 12)):
            put(im, x, y, ORANGE[3])
    return im


def confinement_core():
    im, rng = panel(51106, STEEL, 3)
    # A blue plasma knot glowing out of a dark well, held by four orange magnet clamps.
    rect(im, 3, 3, 12, 12, SCREEN)
    for x in range(3, 13):
        for y in range(3, 13):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 1.6:
                put(im, x, y, BLUE[3])
            elif d < 2.7:
                put(im, x, y, BLUE[2])
            elif d < 3.8:
                put(im, x, y, BLUE[1])
            elif d < 4.6:
                put(im, x, y, BLUE[0])
    for x0, y0 in ((7, 2), (7, 12), (2, 7), (12, 7)):
        rect(im, x0, y0, x0 + 1, y0 + 1, ORANGE[2])
        put(im, x0, y0, ORANGE[3])
    return im


def port(seed, fitting):
    im, rng = panel(seed)
    rivets(im)
    bevel_box(im, 3, 3, 12, 12, CERAMIC[2], CERAMIC[0], CERAMIC[4])
    fitting(im)
    return im


def fuel_fitting(im):
    # Dark inlet bore behind an orange cross handwheel: the deuterium valve.
    rect(im, 4, 4, 11, 11, SCREEN)
    rect(im, 7, 4, 8, 11, ORANGE[1]); rect(im, 4, 7, 11, 8, ORANGE[1])
    for i in range(4, 12):
        put(im, 7, i, ORANGE[2]); put(im, i, 7, ORANGE[2])
    rect(im, 6, 6, 9, 9, ORANGE[2]); rect(im, 7, 7, 8, 8, ORANGE[3])


def power_fitting(im):
    # A blue lightning bolt on a dark plate: the grid tie.
    rect(im, 4, 4, 11, 11, SCREEN)
    for x, y in ((8, 4), (9, 4), (7, 5), (8, 5), (6, 6), (7, 6), (5, 7), (6, 7), (7, 7), (8, 7), (9, 7), (10, 7),
                 (8, 8), (9, 8), (7, 9), (8, 9), (6, 10), (7, 10), (6, 11)):
        put(im, x, y, BLUE[2])
    for x, y in ((9, 4), (8, 5), (7, 6), (10, 7), (9, 8), (8, 9)):
        put(im, x, y, BLUE[3])


def vacuum_fitting(lamp):
    def draw(im):
        # A round pump grille with spokes; the lamp shows the mode.
        for x in range(4, 12):
            for y in range(4, 12):
                d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
                if d < 3.9:
                    put(im, x, y, STEEL[3] if d > 3 else SCREEN)
        for x, y in ((7, 5), (8, 5), (7, 10), (8, 10), (5, 7), (5, 8), (10, 7), (10, 8), (6, 6), (9, 9), (9, 6), (6, 9)):
            put(im, x, y, STEEL[1])
        rect(im, 7, 7, 8, 8, lamp)
    return draw


def hatch_top():
    im, rng = panel(51110)
    # Porthole into the channel.
    for x in range(3, 13):
        for y in range(3, 13):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 4.6:
                put(im, x, y, STEEL[2] if d > 3.6 else (BLUE[0] if d > 2 else BLUE[1]))
    put(im, 6, 6, BLUE[3]); put(im, 7, 6, BLUE[2])
    rivets(im)
    return im


def hatch_bottom():
    im, rng = panel(51111)
    # Handle wheel and an orange hazard band.
    for x in range(2, 14):
        for y in (11, 12):
            put(im, x, y, ORANGE[2] if (x + y) % 4 < 2 else (52, 56, 62))
    for x, y in ((6, 4), (7, 4), (8, 4), (9, 4), (5, 5), (10, 5), (5, 6), (10, 6), (5, 7), (10, 7), (6, 8), (7, 8), (8, 8), (9, 8)):
        put(im, x, y, STEEL[3])
    put(im, 7, 6, STEEL[1]); put(im, 8, 6, STEEL[1]); put(im, 7, 5, STEEL[2]); put(im, 7, 7, STEEL[2])
    rivets(im, ((2, 2), (13, 2)))
    return im


def radiator(hot):
    rng = random.Random(51120 + hot)
    im = new()
    # Horizontal steel fins on a ceramic frame, a blue coolant header down the left; hot fins glow orange.
    for x in range(16):
        for y in range(16):
            put(im, x, y, CERAMIC[2])
    for y in range(1, 15):
        fin = (ORANGE[3] if y % 2 else ORANGE[1]) if hot else (STEEL[0] if y % 2 else STEEL[2])
        for x in range(4, 15):
            put(im, x, y, jitter(fin, rng, 3))
    for y in range(16):
        put(im, 1, y, BLUE[0]); put(im, 2, y, BLUE[2] if hot else BLUE[1]); put(im, 3, y, BLUE[0])
    for i in range(16):
        put(im, i, 0, OUTLINE); put(im, i, 15, OUTLINE); put(im, 0, i, OUTLINE); put(im, 15, i, OUTLINE)
    return im


TEXTURES = {
    "sfr_shell": shell,
    "sfr_controller": controller,
    "magnet_coil": magnet_coil,
    "lithium_blanket": lithium_blanket,
    "first_wall": lambda: first_wall(False),
    "first_wall_worn": lambda: first_wall(True),
    "confinement_core": confinement_core,
    "sfr_fuel_port": lambda: port(51107, fuel_fitting),
    "sfr_power_port": lambda: port(51108, power_fitting),
    "sfr_vacuum_port_pump": lambda: port(51109, vacuum_fitting(BLUE[2])),
    "sfr_vacuum_port_vent": lambda: port(51109, vacuum_fitting(ORANGE[3])),
    "sfr_maintenance_hatch_top": hatch_top,
    "sfr_maintenance_hatch_bottom": hatch_bottom,
    "high_temperature_radiator": lambda: radiator(False),
    "high_temperature_radiator_hot": lambda: radiator(True),
}

if __name__ == "__main__":
    for name, draw in TEXTURES.items():
        draw().save(OUT / f"{name}.png")
        print(name)

"""Original 16x16 textures for the ORSA CFU-7 Hearthstone Reactor. Deterministic; run from the repo root:
python3 docs/art/hearthstone-reactor/generate_textures.py
Palette follows the existing ORSA power blocks (slate plate, copper accents)."""
import random
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[3] / "src/main/resources/assets/frozendawn/textures"
SLATE = [(30, 34, 37), (46, 52, 56), (58, 66, 71), (72, 82, 88), (95, 106, 112)]
COPPER = [(110, 58, 34), (150, 82, 48), (184, 108, 62), (214, 140, 86)]
URANIUM = [(40, 90, 36), (70, 170, 60), (120, 220, 90), (190, 255, 150)]
CHERENKOV = [(30, 60, 120), (60, 120, 230), (130, 190, 255)]
AMBER, RED, GREEN, DARK = (229, 160, 100), (229, 83, 75), (156, 213, 165), (16, 22, 26)


def img():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def plate(seed, base=SLATE, rivets=True):
    r = random.Random(seed)
    im = img()
    for x in range(16):
        for y in range(16):
            im.putpixel((x, y), base[2] if r.random() > .25 else base[1])
    for i in range(16):
        im.putpixel((i, 0), base[4]); im.putpixel((0, i), base[4])
        im.putpixel((i, 15), base[0]); im.putpixel((15, i), base[0])
    if rivets:
        for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
            im.putpixel((x, y), base[4]); im.putpixel((x + 1, y + 1), base[0])
    return im


def save(im, name):
    path = ROOT / name
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)


def casing(scorched=False):
    im = plate(7)
    for x in range(1, 15):
        im.putpixel((x, 5), SLATE[0]); im.putpixel((x, 10), SLATE[0])
        im.putpixel((x, 6), SLATE[3]); im.putpixel((x, 11), SLATE[3])
    for x in range(4, 12):  # hazard band
        im.putpixel((x, 8), AMBER if x % 4 < 2 else DARK)
    if scorched:
        r = random.Random(11)
        for x in range(16):
            for y in range(16):
                p = im.getpixel((x, y))
                k = .45 + .35 * r.random()
                tint = (int(p[0] * k + 40 * (1 - k)), int(p[1] * k * .8), int(p[2] * k * .6), 255)
                im.putpixel((x, y), tint)
        for _ in range(14):
            im.putpixel((r.randrange(16), r.randrange(16)), (12, 10, 9, 255))
    return im


def core():
    im = plate(3)
    for x in range(3, 13):
        for y in range(3, 13):
            im.putpixel((x, y), DARK)
    for x in range(4, 12, 2):
        for y in range(4, 12, 2):
            im.putpixel((x, y), CHERENKOV[2] if (x + y) % 4 == 0 else CHERENKOV[1])
            im.putpixel((x + 1, y), CHERENKOV[0])
    return im


def rod_drive_top():
    im = plate(5)
    for cx, cy in ((5, 5), (10, 5), (5, 10), (10, 10)):
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            im.putpixel((cx + dx, cy + dy), COPPER[2] if dx + dy < 2 else COPPER[1])
        im.putpixel((cx - 1, cy - 1), SLATE[0])
    return im


def rod_drive_side():
    im = plate(6, rivets=False)
    for x in (3, 7, 11):
        for y in range(1, 15):
            im.putpixel((x, y), SLATE[4]); im.putpixel((x + 1, y), SLATE[1])
    for y in (3, 12):
        for x in range(1, 15):
            im.putpixel((x, y), COPPER[1])
    return im


def coolant_port():
    im = plate(9)
    for x in range(16):
        for y in range(16):
            d = (x - 7.5) ** 2 + (y - 7.5) ** 2
            if d <= 30: im.putpixel((x, y), COPPER[2] if d > 20 else COPPER[1] if d > 12 else DARK)
            if d <= 6: im.putpixel((x, y), CHERENKOV[1] if d > 2 else CHERENKOV[2])
    for x, y in ((7, 2), (2, 7), (13, 8), (8, 13)):
        im.putpixel((x, y), COPPER[3])
    return im


def controller(state):
    im = plate(13)
    for x in range(3, 13):
        for y in range(3, 9):
            im.putpixel((x, y), DARK)
    screen = [None, CHERENKOV[1], AMBER, RED][state]
    if screen:
        for x in range(4, 12):
            im.putpixel((x, 5 if x % 3 else 6), screen)
        for x in range(4, 4 + (2, 5, 7, 8)[state]):
            im.putpixel((x, 7), screen)
    lights = [(SLATE[0],) * 3, (GREEN, SLATE[0], SLATE[0]), (SLATE[0], AMBER, SLATE[0]), (SLATE[0], AMBER, RED)][state]
    for i, c in enumerate(lights):
        im.putpixel((4 + i * 3, 11), c); im.putpixel((5 + i * 3, 11), c)
    return im


def radiator(hot):
    im = img()
    fins = [(255, 120, 50), (255, 170, 80), (255, 220, 140)] if hot else COPPER[1:]
    for x in range(16):
        for y in range(16):
            if y in (0, 15): c = SLATE[3] if y == 0 else SLATE[0]
            elif x % 3 == 0: c = SLATE[1]
            else: c = fins[0] if x % 3 == 1 else fins[1 if y % 4 else 2]
            im.putpixel((x, y), c)
    return im


def corium():
    r = random.Random(17)
    im = img()
    for x in range(16):
        for y in range(16):
            v = r.random()
            im.putpixel((x, y), (28, 22, 20) if v < .5 else (44, 34, 28) if v < .85 else (70, 52, 40))
    for _ in range(3):
        x, y = r.randrange(16), r.randrange(16)
        for _ in range(9):
            im.putpixel((x % 16, y % 16), (255, 140, 40) if r.random() > .3 else (255, 210, 110))
            x += r.choice((-1, 0, 1)); y += r.choice((0, 1))
    return im


def molten_corium():
    """Three-frame animated strip (16x48): dark crust over bright flowing cracks."""
    strip = Image.new("RGBA", (16, 48))
    for frame in range(3):
        r = random.Random(31 + frame)
        for x in range(16):
            for y in range(16):
                v = r.random()
                strip.putpixel((x, y + 16 * frame), (255, 170, 60) if v < .35 else (240, 110, 30) if v < .7 else (120, 50, 25))
        for _ in range(5):
            x, y = r.randrange(16), r.randrange(16)
            for _ in range(6):
                strip.putpixel((x % 16, y % 16 + 16 * frame), (255, 235, 150))
                x += r.choice((-1, 0, 1)); y += r.choice((-1, 0, 1))
    return strip


def pixel_map(rows, palette):
    im = img()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in palette: im.putpixel((x, y), palette[ch] + (255,) if len(palette[ch]) == 3 else palette[ch])
    return im


def ore_overlay():
    """Pitchblende nodules with bright autunite crystals; drawn over vanilla deepslate by the block model."""
    return pixel_map([
        "................", "..kk............", ".kgGk.......kk..", "..kgk......kGgk.", "...k........kgk.",
        ".......kk....k..", "......kgGk......", "......kggk......", ".......kk.....k.", ".kk..........kGk",
        "kGgk..........k.", ".kgk.....kk.....", "..k.....kgGk....", "........kgk.....", ".........k......", "................"],
        {"k": (21, 24, 15), "g": (111, 196, 46), "G": (214, 255, 106)})


def raw_uranium():
    """Raw-ore lump: dark pitchblende with yellow-green mineral flecks."""
    return pixel_map([
        "................", "................", ".....oooo.......", "...oollmmoo.....", "..olllmGgmdo....",
        "..ollmmgmmmdo...", ".olmmmmmmmdddo..", ".olmgGmmmlldddo.", ".oolmgmmlllmmddo", "..odmmmmllmGgmdo",
        "..odddmmmmmgmddo", "...oddddmmmmddo.", "....ooddddddoo..", "......oooooo....", "................", "................"],
        {"o": (28, 31, 24), "d": (46, 52, 40), "m": (68, 76, 58), "l": (94, 105, 80), "g": (155, 212, 58), "G": (221, 247, 122)})


def concentrate():
    """Yellowcake: a small mound of yellow uranium concentrate."""
    return pixel_map([
        "................", "................", "................", "................", "................",
        ".......yy.......", ".....yyYYy......", "....yYYYYyy.....", "...yYYwYYYyy....", "..yyYYYYYYYyy...",
        ".ybyyYYYYyyyby..", ".bbbyyyyyyybbb..", "..bbbbbbbbbbb...", "................", "................", "................"],
        {"y": (224, 185, 46), "Y": (245, 218, 92), "w": (255, 243, 168), "b": (156, 122, 22)})


def pellet():
    im = img()
    for y in range(4, 13):
        for x in range(5, 11):
            edge = x in (5, 10) or y in (4, 12)
            im.putpixel((x, y), URANIUM[0] if edge else URANIUM[2] if x < 8 else URANIUM[1])
    for x in range(6, 10):
        im.putpixel((x, 5), URANIUM[3])
    return im


def rod(spent):
    """Diagonal clad rod, copper end caps, three fuel windows (green fresh, dim violet spent)."""
    im = img()
    clad = [(70, 76, 82), (107, 117, 124), (160, 170, 176)] if spent else [(62, 70, 76), (110, 122, 130), (172, 182, 188)]
    window = [(70, 58, 112), (104, 88, 160)] if spent else [(60, 170, 50), (150, 255, 110)]
    cap = [(120, 62, 32), (180, 100, 56)] if not spent else [(92, 50, 30), (130, 74, 44)]
    for i in range(12):
        cx, cy = 2 + i, 13 - i
        part = "cap" if i < 2 or i > 9 else "window" if i in (3, 4, 6, 7) else "clad"
        for (x, y), shade in (((cx, cy - 1), 2), ((cx, cy), 1), ((cx + 1, cy), 0)):
            if part == "cap": c = cap[1] if shade else cap[0]
            elif part == "window": c = window[1] if shade == 2 or (shade == 1 and i % 3 == 0) else window[0]
            else: c = clad[shade]
            im.putpixel((x, y), c + (255,))
    outline = (24, 28, 30, 255)
    filled = {(x, y) for x in range(16) for y in range(16) if im.getpixel((x, y))[3]}
    for x, y in list(filled):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, y + dy)
            if 0 <= n[0] < 16 and 0 <= n[1] < 16 and n not in filled: im.putpixel(n, outline)
    return im


def trefoil_icon():
    """18x18 status-effect icon: black radiation trefoil on a yellow disc."""
    im = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    import math as m
    for x in range(18):
        for y in range(18):
            hits = {"disc": 0, "black": 0, "rim": 0}
            for sx in range(4):
                for sy in range(4):
                    px, py = x + (sx + .5) / 4 - 9, 9 - (y + (sy + .5) / 4)
                    r = m.hypot(px, py); a = m.degrees(m.atan2(py, px)) % 360
                    if r > 8.6: continue
                    if r > 7.6: hits["rim"] += 1; continue
                    blade = any(abs(((a - c + 180) % 360) - 180) <= 30 for c in (90, 210, 330)) and 2.5 <= r <= 6.6
                    if r <= 1.5 or blade: hits["black"] += 1
                    else: hits["disc"] += 1
            best = max(hits, key=hits.get)
            if hits[best] >= 6:
                im.putpixel((x, y), {"disc": (240, 196, 40, 255), "black": (20, 18, 14, 255), "rim": (20, 18, 14, 255)}[best])
    return im


def contaminated_icon():
    """18x18 status icon: a grey-black soot smear with ash flecks inside a pale ring."""
    r = random.Random(53); im = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    import math as m
    for x in range(18):
        for y in range(18):
            d = m.hypot(x - 8.5, y - 8.5)
            if 7.2 < d <= 8.4: im.putpixel((x, y), (150, 146, 138, 255))
            elif d <= 7.2:
                v = r.random()
                im.putpixel((x, y), (40, 38, 36, 255) if v < .45 else (66, 64, 60, 255) if v < .8 else (110, 106, 98, 255) if v < .95 else (130, 84, 56, 255))
    return im


def shielded_cask():
    """Squat lead-grey cask with bolted lid, yellow-black hazard band and trefoil dot."""
    return pixel_map([
        "................", "....oooooooo....", "...oLLLLLLLLo...", "...oBlBlBlBlo...", "..oooooooooooo..",
        "..oMMMMMMMMMMo..", "..oMHHHHHHHHMo..", "..oYKYKYKYKYKo..", "..oMHHHttHHHMo..", "..oMHHHttHHHMo..",
        "..oMHHHHHHHHMo..", "..oKYKYKYKYKYo..", "..oMMMMMMMMMMo..", "..oddddddddddo..", "...oooooooooo...", "................"],
        {"o": (24, 26, 30), "L": (150, 156, 160), "B": (90, 96, 100), "l": (180, 186, 190), "M": (92, 98, 104),
         "H": (120, 128, 134), "Y": (232, 190, 40), "K": (30, 28, 24), "t": (232, 190, 40), "d": (60, 64, 68)})


def radioactive_ash():
    """A handful of grey-black fallout ash with pale and rust flecks. No glow: contamination looks like dirt."""
    return pixel_map([
        "................", "................", "................", "................", "................",
        "................", "......aa........", "....aAbaAa......", "...abAaabbaa....", "..aAbaawaAbaa...",
        ".abaaAbaarbAaba.", ".aaAbbaaabaaAba.", "..bbaabbaabbbb..", "................", "................", "................"],
        {"a": (70, 70, 68), "A": (98, 96, 92), "b": (42, 42, 40), "w": (170, 168, 160), "r": (120, 76, 50)})


def rad_aid():
    """ORSA RadAid: orange IV drip bag with a hanger tab, fluid line and a small yellow trefoil label."""
    return pixel_map([
        ".......hh.......", "......h..h......", "....oooooooo....", "...oOOOOOOOOo...", "...oOooOOOOOo...",
        "...oOOOOOOOOo...", "...oOOYYYYOOo...", "...oOOYKKYOOo...", "...oOOYYYYOOo...", "...oDOOOOOODo...",
        "....oDDDDDDo....", ".....oooooo.....", ".......tt.......", ".......tt.......", "........t.......", "........t......."],
        {"h": (150, 156, 160), "o": (110, 50, 16), "O": (240, 132, 40), "D": (200, 96, 24), "Y": (240, 200, 50),
         "K": (30, 28, 24), "t": (210, 200, 180)})


def geiger(reading):
    """ORSA Geiger Counter: grey body, probe on a cable, analog dial. reading 0-4 sweeps the needle; 4 pegs it."""
    im = pixel_map([
        "................", "..oooooooooo....", ".oBBBBBBBBBBo...", ".oBwwwwwwwwBo...", ".oBwwwwwwwwBo...",
        ".oBwwwwwwwwBo...", ".oBwwwwwwwwBo...", ".oBBBBBBBBBBo.c.", ".oBGGrrGGyyBo.c.", ".oBBBBBBBBBBoc..",
        ".oBBkkBBkkBBc...", "..oooooooooc....", "...........ppp..", "..........pPPPp.", "...........ppp..", "................"],
        {"o": (30, 32, 34), "B": (120, 126, 130), "w": (230, 222, 196), "G": (90, 160, 70), "r": (200, 60, 50),
         "y": (230, 190, 50), "k": (40, 42, 44), "c": (40, 40, 40), "p": (60, 64, 68), "P": (170, 176, 180)})
    # Needle pivots at the bottom centre of the dial and sweeps left to right.
    import math as m
    angle = m.radians(160 - reading * 35)
    for t in range(1, 6):
        x = 6.5 + m.cos(angle) * t * 0.9; y = 6.5 - m.sin(angle) * t * 0.8
        if 3 <= round(x) <= 10 and 3 <= round(y) <= 6: im.putpixel((round(x), round(y)), (190, 30, 30, 255))
    return im


def fallout():
    """Settled fallout: grey-black ash and grit with pale flecks and a little rust, tiling like a carpet layer."""
    r = random.Random(41)
    im = img()
    for x in range(16):
        for y in range(16):
            v = r.random()
            c = (56, 56, 54) if v < .4 else (76, 75, 72) if v < .75 else (40, 40, 39) if v < .9 else (110, 108, 102) if v < .97 else (118, 80, 56)
            im.putpixel((x, y), c + (255,))
    return im


def fallout_severe():
    """Heavy fallout from a blast: darker, coarser grit with scorched fuel fragments and rust."""
    r = random.Random(43)
    im = img()
    for x in range(16):
        for y in range(16):
            v = r.random()
            c = (34, 33, 32) if v < .45 else (50, 48, 46) if v < .75 else (22, 22, 22) if v < .88 else (96, 92, 86) if v < .95 else (128, 74, 44)
            im.putpixel((x, y), c + (255,))
    for _ in range(4):
        x, y = r.randrange(15), r.randrange(15)
        im.putpixel((x, y), (70, 82, 60, 255)); im.putpixel((x + 1, y), (52, 60, 46, 255))
    return im


def reactor_debris():
    """Torn scorched casing plate and graphite, with fuel fragments glowing through the cracks."""
    r = random.Random(53)
    im = casing(True)
    for x in range(16):
        for y in range(16):
            if r.random() < .3:
                im.putpixel((x, y), (24, 22, 21, 255) if r.random() < .6 else (40, 36, 33, 255))
    for _ in range(3):
        x, y = r.randrange(16), r.randrange(16)
        for _ in range(6):
            im.putpixel((x % 16, y % 16), (255, 140, 40, 255) if r.random() > .4 else (190, 255, 150, 255))
            x += r.choice((-1, 0, 1)); y += r.choice((-1, 0, 1))
    return im


if __name__ == "__main__":
    save(casing(), "block/reactor_casing.png")
    save(casing(True), "block/reactor_casing_scorched.png")
    save(core(), "block/reactor_core.png")
    save(rod_drive_top(), "block/control_rod_drive_top.png")
    save(rod_drive_side(), "block/control_rod_drive_side.png")
    save(coolant_port(), "block/coolant_port.png")
    for s in range(4):
        save(controller(s), f"block/reactor_controller_{s}.png")
    save(radiator(False), "block/reactor_radiator.png")
    save(radiator(True), "block/reactor_radiator_hot.png")
    save(corium(), "block/corium.png")
    save(molten_corium(), "block/molten_corium.png")
    (ROOT / "block/molten_corium.png.mcmeta").write_text('{"animation": {"frametime": 6, "interpolate": true}}\n')
    save(ore_overlay(), "block/deepslate_uranium_ore_overlay.png")
    save(raw_uranium(), "item/raw_uranium.png")
    save(pellet(), "item/uranium_pellet.png")
    save(concentrate(), "item/uranium_concentrate.png")
    save(trefoil_icon(), "mob_effect/radiation_sickness.png")
    save(contaminated_icon(), "mob_effect/contaminated.png")
    save(shielded_cask(), "item/shielded_cask.png")
    save(radioactive_ash(), "item/radioactive_ash.png")
    save(rad_aid(), "item/rad_aid.png")
    for n in range(5):
        save(geiger(n), f"item/geiger_counter_{n}.png")
    save(fallout(), "block/fallout.png")
    save(fallout_severe(), "block/fallout_severe.png")
    save(reactor_debris(), "block/reactor_debris.png")
    save(rod(False), "item/fuel_rod.png")
    save(rod(True), "item/spent_fuel_rod.png")

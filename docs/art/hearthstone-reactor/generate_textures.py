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
    save(rod(False), "item/fuel_rod.png")
    save(rod(True), "item/spent_fuel_rod.png")

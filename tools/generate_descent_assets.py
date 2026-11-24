#!/usr/bin/env python3
"""Draw the descent's slice 1 art: instrument models and textures, mark
textures, the portrait, their blockstates and item models, and the mockup
sheets that show them. The motif paintings are the owner's wall art.

Everything is drawn at 16 pixels per block from fixed seeds, so a rerun
writes the same bytes. No texture carries a glyph (CH-10, CH-11).

    python3 tools/generate_descent_assets.py --resources src/main/resources
    python3 tools/generate_descent_assets.py --mockups <dir>
"""
import argparse
import json
import random
from pathlib import Path

from PIL import Image, ImageDraw

MOD = "frozendawn"

# --- palette helpers -------------------------------------------------------


def hexrgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def shade(c, d):
    return tuple(max(0, min(255, v + d)) for v in c[:3]) + (c[3],)


def noisy(name, base, spread=10, size=(16, 16), grain=None):
    """A flat material with seeded per-pixel noise; grain adds horizontal streaks."""
    rnd = random.Random(name)
    img = Image.new("RGBA", size)
    px = img.load()
    base = hexrgb(base)
    rows = [rnd.randint(-spread // 2, spread // 2) for _ in range(size[1])]
    for y in range(size[1]):
        for x in range(size[0]):
            d = rnd.randint(-spread, spread) // 2
            if grain:
                d += rows[y] * grain
            px[x, y] = shade(base, d)
    return img


# --- materials (16x16, shared by the instrument models) ---------------------

def materials():
    m = {}
    m["walnut"] = noisy("walnut", "#5a3a22", 10, grain=1)
    m["oak"] = noisy("oak", "#7a5634", 12, grain=1)
    m["dark_oak"] = noisy("dark_oak", "#4a3020", 10, grain=1)
    m["brass"] = noisy("brass", "#b8913a", 14)
    m["bronze"] = noisy("bronze", "#9c6a34", 14)
    m["dark_bronze"] = noisy("dark_bronze", "#6e4a28", 12)
    m["iron"] = noisy("iron", "#4e4e54", 10)
    m["frame_stone"] = noisy("frame_stone", "#8a8a86", 16)
    m["gut"] = noisy("gut", "#d8cfa8", 6)
    m["gold"] = noisy("gold", "#d6aa3c", 16)
    m["lapis"] = noisy("lapis", "#2a3c8c", 12)
    m["hide"] = noisy("hide", "#c9ae84", 10)
    m["clay"] = noisy("clay", "#a0583a", 12)
    m["grey_stone"] = noisy("grey_stone", "#6f6d68", 18)
    m["bone"] = noisy("bone", "#e4dcc4", 8)
    m["ochre_stone"] = noisy("ochre_stone", "#7a6a5a", 16)
    m["rope"] = noisy("rope", "#9a8458", 10)
    # Finger holes and the whistle's mouth: one dark pixel column.
    flute = m["bone"].copy()
    for x in (5, 8, 11):
        flute.putpixel((x, 7), hexrgb("#3a3026"))
        flute.putpixel((x, 8), hexrgb("#3a3026"))
    m["bone_holes"] = flute
    whistle = m["grey_stone"].copy()
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8), (7, 13), (8, 13)):
        whistle.putpixel((x, y), hexrgb("#262422"))
    m["stone_holed"] = whistle
    return m


# --- instrument models (front faces north, as vanilla orientables do) ------
# Each element: (from, to, material). Auto-UV, one material per element.

INSTRUMENTS = {
    "music_box": [
        ([3, 0, 4], [13, 5, 12], "walnut"),
        ([3, 5, 4], [13, 6, 12], "dark_oak"),
        ([13, 2, 7], [15, 3, 9], "brass"),
        ([14, 3, 7], [15, 5, 8], "brass"),
    ],
    "church_bell": [
        ([1, 0, 7], [3, 15, 9], "oak"),
        ([13, 0, 7], [15, 15, 9], "oak"),
        ([1, 15, 7], [15, 16, 9], "oak"),
        ([7, 13, 7], [9, 15, 9], "iron"),
        ([6, 10, 6], [10, 13, 10], "bronze"),
        ([5, 6, 5], [11, 10, 11], "bronze"),
        ([4, 4, 4], [12, 6, 12], "bronze"),
    ],
    "monastery_bell": [
        ([2, 0, 6], [5, 14, 10], "frame_stone"),
        ([11, 0, 6], [14, 14, 10], "frame_stone"),
        ([2, 14, 6], [14, 16, 10], "frame_stone"),
        ([7, 11, 7], [9, 14, 9], "rope"),
        ([6, 8, 6], [10, 11, 10], "iron"),
        ([5, 5, 5], [11, 8, 11], "iron"),
    ],
    "lyre": [
        ([3, 0, 6], [13, 4, 10], "oak"),
        ([3, 4, 7], [5, 15, 9], "oak"),
        ([11, 4, 7], [13, 15, 9], "oak"),
        ([3, 13, 7], [13, 15, 9], "dark_oak"),
        ([6, 4, 8], [7, 13, 8.5], "gut"),
        ([8, 4, 8], [9, 13, 8.5], "gut"),
        ([10, 4, 8], [10.5, 13, 8.5], "gut"),
    ],
    "bronze_lur": [
        ([4, 0, 4], [12, 1, 12], "grey_stone"),
        ([7, 1, 10], [9, 12, 12], "bronze"),
        ([7, 12, 8], [9, 14, 12], "bronze"),
        ([7, 13, 5], [9, 15, 8], "bronze"),
        ([5, 11, 3], [11, 16, 5], "bronze"),
    ],
    "bull_lyre": [
        ([2, 0, 6], [12, 6, 10], "dark_oak"),
        ([2, 6, 7], [3, 13, 9], "dark_oak"),
        ([10, 6, 7], [12, 16, 9], "dark_oak"),
        ([2, 13, 7], [12, 15, 9], "lapis"),
        ([4, 6, 8], [5, 13, 8.5], "gut"),
        ([6, 6, 8], [7, 14, 8.5], "gut"),
        ([8, 6, 8], [9, 15, 8.5], "gut"),
        ([12, 3, 7], [15, 7, 9], "gold"),
        ([12, 7, 7], [13, 9, 9], "gold"),
        ([12, 1, 7], [14, 3, 9], "lapis"),
    ],
    "drum_and_whistle": [
        ([2, 0, 3], [10, 7, 11], "dark_oak"),
        ([2, 7, 3], [10, 8, 11], "hide"),
        ([11, 0, 10], [15, 2, 13], "clay"),
        ([12, 2, 11], [13, 3, 12], "clay"),
    ],
    "stone_whistle": [
        ([3, 0, 3], [13, 1, 13], "grey_stone"),
        ([5, 1, 6], [11, 4, 10], "stone_holed"),
    ],
    "bone_flute": [
        ([2, 0, 4], [14, 2, 12], "ochre_stone"),
        ([3, 2, 7], [13, 3, 9], "bone_holes"),
    ],
}

# --- eras ------------------------------------------------------------------
# id, instrument, standing, mark ground, mark bar, motif ground, motif figure

ERAS = [
    ("house_1816", "music_box", 2),
    ("maunder", "church_bell", 3),
    ("famine", "monastery_bell", 4),
    ("late_antique", "lyre", 5),
    ("bronze_age", "bronze_lur", 6),
    ("four_kiloyear", "bull_lyre", 7),
    ("eight_kiloyear", "drum_and_whistle", 8),
    ("younger_dryas", "stone_whistle", 9),
    ("glacial_maximum", "bone_flute", 10),
]


def mark_ground(era):
    if era == "house_1816":
        return noisy("m1816", "#e6dcc4", 6)          # whitewashed wall paper
    if era == "maunder":
        return noisy("mmaunder", "#7a5634", 10, grain=1)  # oak panel
    if era == "famine":
        img = noisy("mfamine", "#3e5a6a", 10)       # leaded glass quarries
        d = ImageDraw.Draw(img)
        for i in range(0, 16, 5):
            d.line([(i, 0), (i, 15)], fill=hexrgb("#2a2a2e"))
            d.line([(0, i), (15, i)], fill=hexrgb("#2a2a2e"))
        return img
    if era == "late_antique":
        img = Image.new("RGBA", (16, 16))           # mosaic tesserae
        rnd = random.Random("mlate")
        cols = [hexrgb(c) for c in ("#c8b48a", "#bca880", "#d2c096")]
        for ty in range(0, 16, 2):
            for tx in range(0, 16, 2):
                c = rnd.choice(cols)
                for y in range(ty, ty + 2):
                    for x in range(tx, tx + 2):
                        img.putpixel((x, y), shade(c, -30) if (x == tx + 1 and y == ty + 1) else c)
        return img
    if era == "bronze_age":
        return noisy("mbronze", "#4a3a2e", 10)       # dark wood for inlay
    if era == "four_kiloyear":
        return noisy("mfourk", "#b07850", 12)        # fired clay
    if era == "eight_kiloyear":
        return noisy("meightk", "#d8ccb0", 8)        # plaster
    if era == "younger_dryas":
        return noisy("myd", "#7a7870", 18)           # grey stone
    return noisy("mlgm", "#6a5e54", 16)              # cave stone


MARK_BAR = {
    "house_1816": "#141418",      # ink
    "maunder": "#2a1e18",         # paint
    "famine": "#14181c",          # dark pane
    "late_antique": "#3a2a24",    # dark tesserae
    "bronze_age": "#b8834a",      # bronze inlay
    "four_kiloyear": "#5a3020",   # pressed clay
    "eight_kiloyear": "#8a3a2a",  # red paint
    "younger_dryas": "#3a3834",   # carved groove
    "glacial_maximum": "#b0542c",  # ochre
}

MARK_NAMES = {
    "house_1816": "Ink Mark",
    "maunder": "Panel Mark",
    "famine": "Glass Mark",
    "late_antique": "Mosaic Mark",
    "bronze_age": "Bronze Mark",
    "four_kiloyear": "Clay Mark",
    "eight_kiloyear": "Plaster Mark",
    "younger_dryas": "Carved Mark",
    "glacial_maximum": "Ochre Mark",
}

BAR_WIDTH = 10  # the redaction is exactly 10 characters wide, one pixel each


def mark_front(era):
    img = mark_ground(era).copy()
    bar = hexrgb(MARK_BAR[era])
    x0 = (16 - BAR_WIDTH) // 2
    rnd = random.Random("bar" + era)
    for x in range(x0, x0 + BAR_WIDTH):
        for y in (7, 8):
            img.putpixel((x, y), shade(bar, rnd.randint(-6, 6)))
    if era == "younger_dryas":  # a carved groove catches light on its lower lip
        for x in range(x0, x0 + BAR_WIDTH):
            img.putpixel((x, 9), shade(hexrgb("#7a7870"), 24))
    return img


# --- the portrait (the motifs themselves are the owner's wall art) ------------

def portrait():
    """A woman in 1816 winter clothes: bonnet, shawl, dark dress. No features."""
    w, h = 16, 32
    img = noisy("portrait", "#2a2420", 8, size=(w, h))
    rnd = random.Random("prt")
    frame = hexrgb("#8a6a2a")
    for x in range(w):
        img.putpixel((x, 0), frame)
        img.putpixel((x, h - 1), frame)
    for y in range(h):
        img.putpixel((0, y), frame)
        img.putpixel((w - 1, y), frame)
    bonnet, skin = hexrgb("#4a3a30"), hexrgb("#c4a088")
    shawl, dress = hexrgb("#6a2e2a"), hexrgb("#1e1c22")

    def fill(x0, y0, x1, y1, c):
        for y in range(y0, y1):
            for x in range(x0, x1):
                img.putpixel((x, y), shade(c, rnd.randint(-5, 5)))
    fill(5, 5, 11, 7, bonnet)       # bonnet crown
    fill(4, 7, 12, 13, bonnet)      # brim framing the face
    fill(6, 8, 10, 13, skin)        # face, left plain
    fill(7, 13, 9, 14, skin)        # neck
    fill(4, 14, 12, 16, shawl)      # shawl over the shoulders
    fill(3, 16, 13, 21, shawl)
    fill(3, 21, 13, 31, dress)
    fill(6, 21, 10, 23, shawl)      # shawl ends crossed at the front
    return img


# --- model json --------------------------------------------------------------

def model_json(name):
    els = INSTRUMENTS[name]
    used = []
    for _, _, mat in els:
        if mat not in used:
            used.append(mat)
    textures = {"particle": f"{MOD}:block/descent/{used[0]}"}
    for mat in used:
        textures[mat] = f"{MOD}:block/descent/{mat}"
    elements = []
    for frm, to, mat in els:
        faces = {d: {"texture": "#" + mat} for d in ("north", "east", "south", "west", "up", "down")}
        elements.append({"from": frm, "to": to, "faces": faces})
    return {"parent": "minecraft:block/block", "textures": textures, "elements": elements}


# --- mockup rendering ----------------------------------------------------------

def tex_at(img, u, v):
    return img.getpixel((min(15, int(u)), min(15, int(v))))


def render_iso(name, mats, k=6):
    """Isometric view of the model as placed facing the viewer's left-front."""
    size = 32 * k + 2 * k
    out = Image.new("RGBA", (size, size + 8 * k), (0, 0, 0, 0))
    d = ImageDraw.Draw(out)
    ox, oy = size // 2, 17 * k + 4 * k

    def proj(x, y, z):
        return (ox + (x - z) * k, oy + (x + z) * k * 0.5 - y * k)

    els = []
    for frm, to, mat in INSTRUMENTS[name]:
        # turn 180 degrees so the north (front) face points at the viewer
        f = [16 - to[0], frm[1], 16 - to[2]]
        t = [16 - frm[0], to[1], 16 - frm[2]]
        els.append((f, t, mats[mat]))
    els.sort(key=lambda e: (e[0][0] + e[1][0]) + (e[0][2] + e[1][2]) + 0.01 * (e[0][1] + e[1][1]))
    for f, t, tex in els:
        def quad(corners, colour):
            d.polygon([proj(*c) for c in corners], fill=colour)
        # east face (+x)
        for zi in range(int(f[2]), int(-(-t[2] // 1))):
            for yi in range(int(f[1]), int(-(-t[1] // 1))):
                z0, z1 = max(zi, f[2]), min(zi + 1, t[2])
                y0, y1 = max(yi, f[1]), min(yi + 1, t[1])
                c = shade(tex_at(tex, 16 - z1, 16 - y1), -28)
                quad([(t[0], y0, z0), (t[0], y0, z1), (t[0], y1, z1), (t[0], y1, z0)], c)
        # south face (+z)
        for xi in range(int(f[0]), int(-(-t[0] // 1))):
            for yi in range(int(f[1]), int(-(-t[1] // 1))):
                x0, x1 = max(xi, f[0]), min(xi + 1, t[0])
                y0, y1 = max(yi, f[1]), min(yi + 1, t[1])
                c = shade(tex_at(tex, x0, 16 - y1), -12)
                quad([(x0, y0, t[2]), (x1, y0, t[2]), (x1, y1, t[2]), (x0, y1, t[2])], c)
        # top face (+y)
        for xi in range(int(f[0]), int(-(-t[0] // 1))):
            for zi in range(int(f[2]), int(-(-t[2] // 1))):
                x0, x1 = max(xi, f[0]), min(xi + 1, t[0])
                z0, z1 = max(zi, f[2]), min(zi + 1, t[2])
                c = tex_at(tex, x0, z0)
                quad([(x0, t[1], z0), (x1, t[1], z0), (x1, t[1], z1), (x0, t[1], z1)], c)
    return out


def render_front(name, mats):
    """Front view, one pixel per model unit: exactly 16 pixels per block."""
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    els = sorted(INSTRUMENTS[name], key=lambda e: -e[0][2])
    for frm, to, mat in els:
        tex = mats[mat]
        for x in range(int(frm[0]), int(-(-to[0] // 1))):
            for y in range(int(frm[1]), int(-(-to[1] // 1))):
                # north face auto-UV: u = 16 - x; seen from the front, flip x
                out.putpixel((15 - x, 15 - y), shade(tex_at(tex, 16 - x - 1, 16 - y - 1), -6))
    return out


def iso_cube(top, side, front, k=6):
    size = 32 * k
    out = Image.new("RGBA", (size + 2 * k, size + 2 * k), (0, 0, 0, 0))
    d = ImageDraw.Draw(out)
    ox, oy = (size + 2 * k) // 2, 17 * k

    def proj(x, y, z):
        return (ox + (x - z) * k, oy + (x + z) * k * 0.5 - y * k)
    for z in range(16):
        for y in range(16):
            c = shade(side.getpixel((15 - z, 15 - y)), -28)
            d.polygon([proj(16, y, z), proj(16, y, z + 1), proj(16, y + 1, z + 1), proj(16, y + 1, z)], fill=c)
    for x in range(16):
        for y in range(16):
            c = shade(front.getpixel((x, 15 - y)), -10)
            d.polygon([proj(x, y, 16), proj(x + 1, y, 16), proj(x + 1, y + 1, 16), proj(x, y + 1, 16)], fill=c)
    for x in range(16):
        for z in range(16):
            c = top.getpixel((x, z))
            d.polygon([proj(x, 16, z), proj(x + 1, 16, z), proj(x + 1, 16, z + 1), proj(x, 16, z + 1)], fill=c)
    return out


def up(img, k):
    return img.resize((img.width * k, img.height * k), Image.NEAREST)


def label(d, xy, text):
    d.text(xy, text, fill=(220, 220, 220, 255))


INSTRUMENT_NAMES = {
    "music_box": "Music Box",
    "church_bell": "Church Bell",
    "monastery_bell": "Monastery Bell",
    "lyre": "Lyre",
    "bronze_lur": "Bronze Lur",
    "bull_lyre": "Bull-Headed Lyre",
    "drum_and_whistle": "Drum and Whistle",
    "stone_whistle": "Stone Whistle",
    "bone_flute": "Bone Flute",
}


def sheet_instruments(mats, out):
    cell_w, cell_h = 360, 300
    sheet = Image.new("RGBA", (cell_w * 3, cell_h * 3 + 40), (34, 34, 38, 255))
    d = ImageDraw.Draw(sheet)
    label(d, (12, 12), "Sheet 1: instruments, top to bottom of the descent. Left: item/front at 16 px per block (x6). Right: placed, isometric.")
    for i, (era, inst, _) in enumerate(ERAS):
        cx, cy = (i % 3) * cell_w, (i // 3) * cell_h + 40
        sheet.alpha_composite(up(render_front(inst, mats), 6), (cx + 16, cy + 40))
        iso = render_iso(inst, mats, k=5)
        sheet.alpha_composite(iso, (cx + 130, cy + 20))
        label(d, (cx + 16, cy + 250), f"{INSTRUMENT_NAMES[inst]}  ({era})")
    sheet.save(out / "descent_sheet1_instruments.png")


def sheet_marks(out):
    cell_w, cell_h = 360, 290
    sheet = Image.new("RGBA", (cell_w * 3, cell_h * 3 + 40), (34, 34, 38, 255))
    d = ImageDraw.Draw(sheet)
    label(d, (12, 12), "Sheet 2: marks. Left: front face at 16 px per block (x8); bar is 10 px wide. Right: placed block.")
    for i, (era, _, _) in enumerate(ERAS):
        cx, cy = (i % 3) * cell_w, (i // 3) * cell_h + 40
        ground, front = mark_ground(era), mark_front(era)
        sheet.alpha_composite(up(front, 8), (cx + 16, cy + 20))
        sheet.alpha_composite(iso_cube(ground, ground, front, k=5), (cx + 160, cy + 30))
        label(d, (cx + 16, cy + 250), f"{MARK_NAMES[era]}  ({era})")
    sheet.save(out / "descent_sheet2_marks.png")


def sheet_portrait(out):
    sheet = Image.new("RGBA", (360, 300), (34, 34, 38, 255))
    d = ImageDraw.Draw(sheet)
    label(d, (12, 12), "Portrait, 1x2 blocks at 16 px per block (x8).")
    sheet.alpha_composite(up(portrait(), 8), (20, 36))
    sheet.save(out / "descent_sheet3_portrait.png")


# --- main ----------------------------------------------------------------------

def write_resources(root):
    mats = materials()
    a = root / "assets" / MOD
    tex = a / "textures" / "block" / "descent"
    tex.mkdir(parents=True, exist_ok=True)
    used = {m for els in INSTRUMENTS.values() for _, _, m in els}
    for name in sorted(used):
        mats[name].save(tex / f"{name}.png")
    for era, _, _ in ERAS:
        mark_ground(era).save(tex / f"{era}_mark_ground.png")
        mark_front(era).save(tex / f"{era}_mark.png")
    models = a / "models" / "block" / "descent"
    models.mkdir(parents=True, exist_ok=True)
    states = a / "blockstates"
    items = a / "models" / "item"
    for name in INSTRUMENTS:
        write_json(models / f"{name}.json", model_json(name))
    for era, _, _ in ERAS:
        write_json(models / f"{era}_mark.json", {"parent": "minecraft:block/orientable", "textures": {
            "front": f"{MOD}:block/descent/{era}_mark",
            "side": f"{MOD}:block/descent/{era}_mark_ground",
            "top": f"{MOD}:block/descent/{era}_mark_ground"}})
    blocks = [(f"descent_{inst}", f"{MOD}:block/descent/{inst}") for _, inst, _ in ERAS]
    blocks += [(f"descent_{MARK_NAMES[era].split()[0].lower()}_mark", f"{MOD}:block/descent/{era}_mark") for era, _, _ in ERAS]
    for block, model in blocks:
        variants = {f"facing={f}": ({"model": model, "y": y} if y else {"model": model})
                    for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}
        write_json(states / f"{block}.json", {"variants": variants})
        write_json(items / f"{block}.json", {"parent": model})
    paint = a / "textures" / "painting" / "descent"
    paint.mkdir(parents=True, exist_ok=True)
    portrait().save(paint / "portrait_1816.png")


def write_json(path, data):
    path.write_text(json.dumps(data, indent=2) + "\n")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--resources", type=Path)
    p.add_argument("--mockups", type=Path)
    args = p.parse_args()
    if args.resources:
        write_resources(args.resources)
    if args.mockups:
        args.mockups.mkdir(parents=True, exist_ok=True)
        mats = materials()
        sheet_instruments(mats, args.mockups)
        sheet_marks(args.mockups)
        sheet_portrait(args.mockups)


if __name__ == "__main__":
    main()

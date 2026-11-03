#!/usr/bin/env python3
"""Generate the Seafood Boil item, block and Warm Meal textures from pixel grids.

Run from the repository root: python3 tools/generate_seafood_boil_textures.py
"""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
TEXTURES = ROOT / "src/main/resources/assets/frozendawn/textures"

PALETTE = {
    # Bowl wood, matching frostcap_stew.
    "a": "#3b281a", "g": "#a37a52", "h": "#8a6340", "i": "#6b4a2f",
    # Cooked broth.
    "B": "#c9652c", "b": "#9e4721", "L": "#e8913f",
    # Cooked shrimp.
    "P": "#f4a582", "p": "#d96a4f", "w": "#fbe0cf",
    # Crab claw.
    "R": "#c8382a", "r": "#8a2418",
    # Corn.
    "Y": "#f5d34c", "y": "#c99a26",
    # Potato.
    "T": "#e9d6a3", "t": "#c4a96e",
    # Herbs.
    "G": "#5f9a3e", "H": "#3f6e2a",
    # Cast iron.
    "k": "#1c1f24", "m": "#363b43", "n": "#4b525c", "o": "#626a75", "q": "#848d99", "Q": "#a9b2bd",
    # Raw contents: cold water, grey shrimp, pale corn.
    "W": "#7fa3b3", "V": "#5e8596", "s": "#b9c3c6", "S": "#8f9ca1", "U": "#ece6b8", "u": "#c9c08a",
    # Warm Meal steam.
    "O": "#ffb35c", "o2": "#ffd9a0",
}

BOWL = [
    "................",
    "................",
    "................",
    "..........rr....",
    "...wPP...rRRr...",
    "..aPLpPaaRrRaa..",
    ".aYyLBTtLBGrBba.",
    ".abBBBpPBBYBBba.",
    ".agggghhhhhhhga.",
    ".agghhhhhhhhhha.",
    "..agghhhhhhhha..",
    "..ahhhhhhhhhha..",
    "...aiiiiiiiia...",
    "....aiiiiiia....",
    ".....aaaaaa.....",
    "................",
]

POT_BODY = [
    ".kkkkkkkkkkkkkk.",
    "k.kQooooonnnmk.k",
    "kkkqooooonnnmkkk",
    "..kqooooonnnmk..",
    "..kqooooonnnmk..",
    "..knooooonnmmk..",
    "...kmmmmmmmmk...",
    "....kkkkkkkk....",
    "................",
]

POT_TOP = [
    "................",
    "................",
    "................",
    "...kkkkkkkkkk...",
    "..kQqqqqqqqqqk..",
]

POT_COOKED = POT_TOP + [
    "..kYyBwPLBRrBk..",
    "..kBGBpPBTtRbk..",
] + POT_BODY

POT_RAW = POT_TOP + [
    "..kWsSWUuWsSWk..",
    "..kVWWtTWVWsVk..",
] + POT_BODY

# Walls sample rows 8-15 (cols 2-14 front, 3-13 sides); the models map the
# wall tops to row 0 and column 0.
SIDE = [
    "QQQQQQQQQQQQQQQQ",
    "Qnnnnnnnnnnnnnnn",
    "Qnnnnnnnnnnnnnnn",
    "Qnnnnnnnnnnnnnnn",
    "Qnnnnnnnnnnnnnnn",
    "Qnnnnnnnnnnnnnnn",
    "Qnnnnnnnnnnnnnnn",
    "Qnnnnnnnnnnnnnnn",
    "QQQQQQQQQQQQQQQQ",
    "kmmmmmmmmmmmmmmk",
    "nnqoQooooooQonmm",
    "nnqooooooooooonm",
    "nnqooooooooooonm",
    "Qnqooooooooonnmm",
    "nnnnnnnnnnnnnmmm",
    "kkkkkkkkkkkkkkkk",
]

BOTTOM = [
    "nnnnnnnnnnnnnnnn",
    "nmmmmmmmmmmmmmmn",
    "nmmmmmmnmmmmmmmn",
    "nmmmmmmmmmmmnmmn",
    "nmmnmmmmmmmmmmmn",
    "nmmmmmmmmmmmmmmn",
    "nmmmmmmmmnmmmmmn",
    "nmmmmmmmmmmmmmmn",
    "nmmmmnmmmmmmmmmn",
    "nmmmmmmmmmmmmnmn",
    "nmmmmmmmmmmmmmmn",
    "nmmmmmmmnmmmmmmn",
    "nmnmmmmmmmmmmmmn",
    "nmmmmmmmmmmmmmmn",
    "nmmmmmmmmmmmmmmn",
    "kkkkkkkkkkkkkkkk",
]

# The stew top only shows columns and rows 3-12.
STEW_CENTER = [
    "BLBBwPBBYB",
    "BBBpPPBYyB",
    "TtBBBBBBBG",
    "ttBRRrBLBB",
    "BBRRrBBBTt",
    "BLBrBGBBtt",
    "YBBBBBPwBB",
    "yYBTtBpPPB",
    "BBBttBBBpB",
    "BGBBBLBBBB",
]
STEW = ["BbBLBBbBBBLBBbBB"] * 3 + [
    "BbB" + row + "BLB" for row in STEW_CENTER
] + ["BBbBBLBBbBBBLBbB"] * 3

WARM_MEAL = [
    "..................",
    ".....O...O...O....",
    "....o2...o2...o2.....",
    "....O...O...O.....",
    ".....O...O...O....",
    "......o2...o2...o2...",
    "......O...O...O...",
    ".....O...O...O....",
    "..................",
    "..aaaaaaaaaaaaaa..",
    ".aLBBBBBBBBBBBBba.",
    ".agggghhhhhhhhhha.",
    ".agghhhhhhhhhhhha.",
    "..aghhhhhhhhhhha..",
    "...ahhhhhhhhhha...",
    "....aiiiiiiiia....",
    ".....aaaaaaaa.....",
    "..................",
]


def tokens(row):
    out, i = [], 0
    while i < len(row):
        if row.startswith("o2", i):
            out.append("o2")
            i += 2
        else:
            out.append(row[i])
            i += 1
    return out


def draw(name, grid, size=16):
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    assert len(grid) == size, f"{name}: {len(grid)} rows"
    for y, row in enumerate(grid):
        cells = tokens(row)
        assert len(cells) == size, f"{name} row {y}: {len(cells)} cells"
        for x, cell in enumerate(cells):
            if cell != ".":
                image.putpixel((x, y), Image.new("RGBA", (1, 1), PALETTE[cell]).getpixel((0, 0)))
    path = TEXTURES / f"{name}.png"
    image.save(path)
    print(path.relative_to(ROOT))


def main():
    draw("item/seafood_boil_bowl", BOWL)
    draw("item/seafood_boil", POT_COOKED)
    draw("item/raw_seafood_boil", POT_RAW)
    draw("block/seafood_boil_side", SIDE)
    draw("block/seafood_boil_bottom", BOTTOM)
    draw("block/seafood_boil_stew", STEW)
    draw("mob_effect/warm_meal", WARM_MEAL, 18)


if __name__ == "__main__":
    main()

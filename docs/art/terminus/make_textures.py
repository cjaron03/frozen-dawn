"""Textures for the ORSA TR-0 Terminus 3D model, direction C "Reliquary" (owner pick, 2026-11-03).

16x16 tiles, one texel a model unit; the model (tools/build_terminus_models.py) maps each face to a region of its tile:
- glass: rows 0-2 are the long sides (steel bars on rows 0 and 2), rows 4-10 the top and bottom (bars on columns 0 and
  2), rows 12-14 the ends;
- core: row 0 the sides and column 0, rows 2-7, the top and bottom, back (index 0) to front (index 5).
The core is the channel inside the glass that holds one mote per discharge left; its idle textures are animated.
Run from anywhere: python3 docs/art/terminus/make_textures.py
"""
import json
import random
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
ITEM = ROOT / 'src/main/resources/assets/frozendawn/textures/item/terminus'
PARTICLE = ROOT / 'src/main/resources/assets/frozendawn/textures/particle'

STEEL, STEEL_HI, STEEL_LO = (93, 102, 109, 255), (121, 131, 138, 255), (63, 69, 74, 255)
PLATE, PLATE_HI = (71, 76, 82, 255), (140, 150, 156, 255)
GRIP, GRIP_HI = (27, 29, 33, 255), (40, 43, 48, 255)
KNURL, KNURL_HI = (48, 52, 58, 255), (70, 75, 82, 255)
BAR = (140, 150, 156, 255)
MOTE, HALO, WHITE = (244, 251, 255, 255), (159, 214, 232, 150), (255, 255, 255, 255)
FACE = [(159, 214, 232, 255), (190, 228, 240, 255), (214, 238, 246, 255), (236, 247, 252, 255), (255, 255, 255, 255)]
FACE_SPENT = (58, 63, 68, 255)


def tile(fill):
    return Image.new('RGBA', (16, 16), fill)


def save(im, name, frames=None):
    ITEM.mkdir(parents=True, exist_ok=True)
    im.save(ITEM / f'{name}.png')
    meta = ITEM / f'{name}.png.mcmeta'
    if frames:
        meta.write_text(json.dumps({'animation': {'frametime': frames, 'interpolate': True}}, indent=2) + '\n')
    elif meta.exists():
        meta.unlink()


def metal(base, hi, lo):
    """A face that catches the light along its top edge and darkens toward its foot."""
    im = tile(base)
    for x in range(16):
        im.putpixel((x, 0), hi)
        im.putpixel((x, 15), lo)
    return im


def glass(frost):
    """The caged glass shaft, clear at 0 and fully frosted at 4. Frost crystals come from a fixed seed."""
    rng = random.Random(4100 + frost)
    alpha = 60 + frost * 36
    body = (196 + frost * 12, 226 + frost * 6, 236 + frost * 4, alpha)
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    regions = [(0, 0, 16, 3), (0, 4, 3, 11), (0, 12, 3, 15)]
    for x0, y0, x1, y1 in regions:
        for x in range(x0, x1):
            for y in range(y0, y1):
                c = body
                if frost and rng.random() < .12 * frost:
                    c = (250, 253, 255, min(255, alpha + 60))
                im.putpixel((x, y), c)
    for x in range(16):
        im.putpixel((x, 0), BAR); im.putpixel((x, 2), BAR)
    for y in range(4, 11):
        im.putpixel((0, y), BAR); im.putpixel((2, y), BAR)
    for x in range(3):
        im.putpixel((x, 12), BAR); im.putpixel((x, 14), BAR)
    im.putpixel((0, 13), BAR); im.putpixel((2, 13), BAR)
    return im


def core(positions, colour=MOTE):
    """One frame of the mote channel: a bright texel per mote, a soft glow either side."""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    lit = {q: HALO for p in positions for q in (p - 1, p + 1) if 0 <= q <= 5}
    lit.update({p: colour for p in positions})
    for i, c in lit.items():
        im.putpixel((i, 0), c)
        im.putpixel((0, 2 + i), c)
    im.putpixel((0, 9), lit.get(5, (0, 0, 0, 0)))
    return im


def animated(frames):
    sheet = Image.new('RGBA', (16, 16 * len(frames)))
    for i, f in enumerate(frames):
        sheet.alpha_composite(f, (0, 16 * i))
    return sheet


# Each mote's idle drift, one texel back and forth on its own phase.
BASE = [0, 2, 4]
DRIFT = [(0, 1, 1, 0), (1, 1, 0, 0), (1, 0, 0, 1)]
# Winding up, the motes slide to the front of the channel, and at the last stage sit together on it, white.
WIND = {1: [1, 3, 5], 2: [3, 4, 5], 3: [4, 5, 5], 4: [5, 5, 5]}


def main():
    save(metal(STEEL, STEEL_HI, STEEL_LO), 'steel')
    plate = metal(PLATE, PLATE_HI, STEEL_LO)
    for x in (5, 6, 8, 10, 11):
        plate.putpixel((x, 7), PLATE_HI)  # a scratched serial on the pommel
    save(plate, 'plate')
    grip = tile(GRIP)
    for x in range(16):
        for y in range(16):
            if (x + y) % 3 == 0:
                grip.putpixel((x, y), GRIP_HI)
    save(grip, 'grip')
    knurl = tile(KNURL)
    for x in range(16):
        for y in range(16):
            if (x + 2 * y) % 4 == 0:
                knurl.putpixel((x, y), KNURL_HI)
    save(knurl, 'knurl')
    for frost in range(5):
        save(glass(frost), f'glass_{frost}')
    for m in (1, 2, 3):
        frames = [core([BASE[i] + DRIFT[i][f] for i in range(m)]) for f in range(4)]
        save(animated(frames), f'core_{m}', frames=10)
        for stage, front in WIND.items():
            save(core(front[3 - m:] if m < 3 else front, WHITE if stage == 4 else MOTE), f'core_{m}_w{stage}')
    for stage, c in enumerate(FACE):
        save(tile(c), 'face' if stage == 0 else f'face_w{stage}')
    save(tile(FACE_SPENT), 'face_spent')

    PARTICLE.mkdir(parents=True, exist_ok=True)
    speck = Image.new('RGBA', (8, 8), (0, 0, 0, 0))
    for x in (3, 4):
        for y in (3, 4):
            speck.putpixel((x, y), WHITE)
    speck.save(PARTICLE / 'terminus_speck.png')
    mote = Image.new('RGBA', (8, 8), (0, 0, 0, 0))
    for x in range(2, 6):
        for y in range(2, 6):
            mote.putpixel((x, y), WHITE if 3 <= x <= 4 and 3 <= y <= 4 else (214, 238, 246, 170))
    mote.save(PARTICLE / 'terminus_mote.png')
    print(ITEM)


if __name__ == '__main__':
    main()

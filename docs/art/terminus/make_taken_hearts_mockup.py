"""Mockup of the hearts a Terminus discharge takes, shown in the health bar like Fallout's radiation (owner, 2026-11-03).

The owner found a permanent max-health loss through death too steep, and asked for "a unique heart debuff, like how
radiation acts in fallout": the taken hearts stay in the bar as their own kind of heart, are not health, and come back
slowly. Each discharge takes two hearts; one heart comes back per Minecraft day (20 minutes), half a heart at a time.
Drawn at the GUI's own scale, 9-pixel hearts, with vanilla's sprites around them, then enlarged with no smoothing.
Run from the repository root: python3 docs/art/terminus/make_taken_hearts_mockup.py <vanilla client resources dir>
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
OUT = HERE / 'taken-hearts-mockup.png'
SCALE = 4

OUTLINE, MOTE = (0, 0, 0, 255), (244, 251, 255, 255)
ASH, ASH_DARK, ASH_LIGHT = (168, 176, 180, 255), (124, 132, 137, 255), (214, 222, 226, 255)
HEART = ['..##.##..',
         '.#######.',
         '#########',
         '#########',
         '#########',
         '.#######.',
         '..#####..',
         '...###...',
         '....#....']


def taken(half=False):
    """A taken heart: the container's black outline round ash grey, shaded as vanilla's full heart is, with one pale
    mote at its centre, the Terminus's own. Half: only the right half is still taken; the left is left clear, so the
    game draws over vanilla's last heart, a half heart of maximum, and its left half shows empty or healed into."""
    im = Image.new('RGBA', (9, 9), (0, 0, 0, 0))
    for y, row in enumerate(HEART):
        for x, c in enumerate(row):
            if c != '#':
                continue
            edge = any(not (0 <= x + dx < 9 and 0 <= y + dy < 9) or HEART[y + dy][x + dx] != '#'
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if half and x < 5:
                continue
            if edge:
                im.putpixel((x, y), OUTLINE)
            else:
                im.putpixel((x, y), ASH_DARK if y >= 5 or (x == 1 and y >= 3) else ASH)
    if not half:
        im.putpixel((2, 2), ASH_LIGHT)
    im.putpixel((4, 4) if not half else (5, 4), MOTE)
    return im


def sprite(root, name):
    return Image.open(root / f'assets/minecraft/textures/gui/sprites/hud/{name}.png').convert('RGBA')


def bar(root, health, taken_points, absorbing=0):
    """The left half of the HUD above the hotbar: hearts on the left, food on the right, at 1 pixel per GUI pixel.
    Vanilla's ten heart slots are always there; the taken ones fill the end of the bar."""
    im = Image.new('RGBA', (182, 32), (0, 0, 0, 0))
    container, full, half = sprite(root, 'heart/container'), sprite(root, 'heart/full'), sprite(root, 'heart/half')
    slots = 10
    free = slots - (taken_points + 1) // 2
    for i in range(slots):
        x = i * 8
        if i < free:
            im.alpha_composite(container, (x, 0))
            if health >= 2 * i + 2:
                im.alpha_composite(full, (x, 0))
            elif health == 2 * i + 1:
                im.alpha_composite(half, (x, 0))
        else:
            half_slot = taken_points % 2 == 1 and i == free
            if half_slot:
                im.alpha_composite(container, (x, 0))
                if health == 2 * i + 1:
                    im.alpha_composite(half, (x, 0))
            im.alpha_composite(taken(half=half_slot), (x, 0))
    food = sprite(root, 'food_full')
    food_bg = sprite(root, 'food_empty')
    for i in range(10):
        x = 182 - 9 - i * 8
        im.alpha_composite(food_bg, (x, 0)); im.alpha_composite(food, (x, 0))
    hotbar = sprite(root, 'hotbar')
    im.alpha_composite(hotbar.crop((0, 0, 182, 22)), (0, 10))
    return im


def main(root):
    rows = [
        (20, 0, 'No toll: vanilla'),
        (20, 4, 'One discharge: two hearts taken. Health tops out at 16'),
        (12, 12, 'Three discharges: six taken. Full health is four hearts'),
        (5, 12, 'Hurt: empty hearts can heal; taken hearts cannot'),
        (8, 11, 'Half a day later: half a heart is back, empty, to heal into'),
        (12, 8, 'Two days on: two hearts back. Death keeps the toll and its clock'),
    ]
    line = 32 * SCALE + 26
    sheet = Image.new('RGBA', (182 * SCALE + 40, 50 + line * len(rows) + 16 + 72 + 20), (32, 37, 43, 255))
    d = ImageDraw.Draw(sheet)
    d.text((20, 16), 'Terminus toll as taken hearts (GUI scale, 9 px hearts, enlarged x4)', fill=(200, 210, 216, 255))
    y = 50
    for health, points, label in rows:
        d.text((20, y), label, fill=(140, 150, 156, 255))
        sheet.alpha_composite(bar(root, health, points).resize((182 * SCALE, 32 * SCALE), Image.NEAREST), (20, y + 16))
        y += line
    d.text((20, y), 'The sprites, x8: taken, half taken, vanilla full and container', fill=(140, 150, 156, 255))
    for i, im in enumerate([taken(), taken(half=True), sprite(root, 'heart/full'), sprite(root, 'heart/container')]):
        backed = Image.new('RGBA', (9, 9), (0, 0, 0, 0))
        if i in (1, 2):
            backed.alpha_composite(sprite(root, 'heart/container'))
        backed.alpha_composite(im)
        sheet.alpha_composite(backed.resize((72, 72), Image.NEAREST), (20 + i * 96, y + 16))
    sheet.save(OUT)
    print(OUT)


if __name__ == '__main__':
    main(Path(sys.argv[1]))

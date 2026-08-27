#!/usr/bin/env python3
"""Generate deterministic Hearthrot GUI and status-effect pixel art."""

from pathlib import Path
import random

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
GUI = ROOT / "src/main/resources/assets/frozendawn/textures/gui"
EFFECT = ROOT / "src/main/resources/assets/frozendawn/textures/mob_effect"
PLAYER = ROOT / "src/main/resources/assets/frozendawn/textures/entity/player"


def crystal(draw, x, y, scale, bright=False):
    base = (210, 235, 232, 205 if bright else 170)
    cold = (150, 205, 210, 185 if bright else 145)
    dark = (67, 94, 98, 130)
    green = (188, 224, 184, 175)
    points = [
        (x, y - scale),
        (x + scale // 2, y - scale // 4),
        (x + scale // 3, y + scale),
        (x - scale // 3, y + scale),
        (x - scale // 2, y - scale // 4),
    ]
    draw.polygon(points, fill=base)
    draw.line(points + [points[0]], fill=dark, width=max(1, scale // 8))
    draw.line((x, y - scale + 2, x - scale // 5, y + scale - 2),
              fill=cold, width=max(1, scale // 7))
    if bright:
        draw.point((x + scale // 5, y - scale // 3), fill=green)


def visor(stage):
    rng = random.Random(0x48454152 + stage)
    image = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    border = 10 + stage * 5
    edge_alpha = 20 + stage * 12
    for inset in range(border):
        alpha = max(0, edge_alpha - inset * 2)
        color = (188, 218, 218, alpha)
        draw.rectangle((inset, inset, 255 - inset, 255 - inset), outline=color)

    count = 10 + stage * 11
    for index in range(count):
        side = rng.randrange(4)
        scale = rng.randrange(4, 8 + stage * 2)
        if side == 0:
            x, y = rng.randrange(0, 42 + stage * 5), rng.randrange(10, 246)
        elif side == 1:
            x, y = rng.randrange(214 - stage * 5, 256), rng.randrange(10, 246)
        elif side == 2:
            x, y = rng.randrange(10, 246), rng.randrange(0, 32 + stage * 4)
        else:
            x, y = rng.randrange(10, 246), rng.randrange(224 - stage * 5, 256)
        crystal(draw, x, y, scale, index % max(2, 6 - stage) == 0)

    # Intake-like speckling gathers most heavily along the lower-right edge.
    for _ in range(20 * stage):
        x = rng.randrange(184 - stage * 8, 253)
        y = rng.randrange(168 - stage * 5, 253)
        shade = rng.choice([(55, 78, 81, 110), (205, 231, 229, 145),
                            (170, 215, 206, 125)])
        size = rng.choice((1, 1, 2))
        draw.rectangle((x, y, x + size, y + size), fill=shade)
    return image


def effect_icon():
    image = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    dark = (44, 63, 67, 255)
    pale = (200, 231, 229, 255)
    cold = (128, 190, 199, 255)
    green = (184, 220, 177, 255)
    draw.polygon([(3, 5), (5, 3), (8, 4), (9, 7), (10, 4), (13, 3),
                  (15, 5), (14, 10), (9, 15), (4, 10)], fill=dark)
    draw.line([(4, 5), (6, 4), (9, 8), (12, 4), (14, 5), (13, 9),
               (9, 14), (5, 9), (4, 5)], fill=pale, width=1)
    draw.line((9, 7, 7, 11, 10, 10, 9, 14), fill=cold, width=1)
    draw.point((12, 5), fill=green)
    return image


def internal_growth(stage):
    """Sparse subdermal branches on the base player-skin UV islands."""
    rng = random.Random(0x524F5400 + stage)
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    regions = [
        (0, 0, 31, 15),       # head faces
        (16, 16, 39, 31),     # torso
        (0, 16, 15, 31),      # right leg
        (40, 16, 55, 31),     # right arm
        (16, 48, 31, 63),     # left leg
        (32, 48, 47, 63),     # left arm
    ]
    dark = (66, 91, 96, 175)
    cold = (154, 207, 211, 205)
    pale = (218, 239, 236, 225)
    for region_index, (left, top, right, bottom) in enumerate(regions):
        branch_count = max(1, stage - (region_index % 2))
        for _ in range(branch_count):
            x = rng.randint(left + 1, max(left + 1, right - 1))
            y = rng.randint(max(top + 2, bottom - 5), bottom - 1)
            points = [(x, y)]
            for _ in range(2 + stage):
                x = max(left + 1, min(right - 1, x + rng.choice((-2, -1, 0, 1, 2))))
                y = max(top + 1, y - rng.choice((1, 1, 2)))
                points.append((x, y))
            draw.line(points, fill=dark, width=2)
            draw.line(points, fill=cold if stage < 3 else pale, width=1)
            if stage >= 3 and len(points) >= 3:
                bx, by = points[-2]
                draw.line((bx, by, max(left, bx - 2), max(top, by - 2)),
                          fill=cold, width=1)
        if stage >= 4:
            for _ in range(2):
                x = rng.randint(left + 1, right - 1)
                y = rng.randint(top + 1, bottom - 1)
                draw.point((x, y), fill=pale)
    return image


def cough_residue():
    """Pale visor condensation that leaves the center readable."""
    rng = random.Random(0x434F5547)
    image = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    for inset in range(26):
        alpha = max(0, 34 - inset)
        draw.rounded_rectangle(
            (inset, inset, 255 - inset, 255 - inset),
            radius=34,
            outline=(205, 229, 230, alpha),
            width=2,
        )
    for _ in range(185):
        side = rng.randrange(4)
        if side == 0:
            x, y = rng.randrange(4, 58), rng.randrange(8, 248)
        elif side == 1:
            x, y = rng.randrange(198, 252), rng.randrange(8, 248)
        elif side == 2:
            x, y = rng.randrange(8, 248), rng.randrange(4, 48)
        else:
            x, y = rng.randrange(8, 248), rng.randrange(208, 252)
        radius = rng.choice((1, 1, 2, 3))
        alpha = rng.randrange(35, 100)
        draw.ellipse((x - radius, y - radius, x + radius, y + radius),
                     fill=(220, 239, 238, alpha))
    return image


def main():
    GUI.mkdir(parents=True, exist_ok=True)
    EFFECT.mkdir(parents=True, exist_ok=True)
    PLAYER.mkdir(parents=True, exist_ok=True)
    for stage in range(1, 5):
        visor(stage).save(GUI / f"hearthrot_visor_{stage}.png")
        internal_growth(stage).save(PLAYER / f"hearthrot_growth_{stage}.png")
    cough_residue().save(GUI / "hearthrot_cough_residue.png")
    effect_icon().save(EFFECT / "hearthrot.png")


if __name__ == "__main__":
    main()

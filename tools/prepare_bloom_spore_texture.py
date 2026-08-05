#!/usr/bin/env python3
"""Normalize the approved Spore concept atlas into Minecraft's 64x64 zombie UV."""

from pathlib import Path
from PIL import Image, ImageEnhance

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "tools/art_sources/bloom_spore_concept.png"
OUTPUT = ROOT / "src/main/resources/assets/frozendawn/textures/entity/bloom_spore.png"

source = Image.open(SOURCE).convert("RGB")
target = Image.new("RGBA", (64, 64), (0, 0, 0, 0))


def tile(crop, box, *, brightness=1.0, mirror=False):
    image = source.crop(crop)
    if mirror:
        image = image.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    if brightness != 1.0:
        image = ImageEnhance.Brightness(image).enhance(brightness)
    width = box[2] - box[0]
    height = box[3] - box[1]
    image = image.resize((width, height), Image.Resampling.BOX)
    # Keep the generated concept's pixel-art character after downsampling.
    image = image.quantize(colors=20, method=Image.Quantize.MEDIANCUT).convert("RGBA")
    target.alpha_composite(image, (box[0], box[1]))


# Head: preserve the absent eyes and skull crystals from the approved face.
head = (186, 12, 520, 317)
tile(head, (8, 8, 16, 16))
tile(head, (0, 8, 8, 16), brightness=0.78, mirror=True)
tile(head, (16, 8, 24, 16), brightness=0.84)
tile((334, 12, 520, 156), (8, 0, 16, 8), brightness=1.05)
tile((186, 156, 340, 317), (16, 0, 24, 8), brightness=0.72)
tile((260, 30, 520, 296), (24, 8, 32, 16), brightness=0.68, mirror=True)

# Torso: chest node and shoulder mass stay on the front; the quieter half becomes back.
torso_front = (18, 318, 480, 638)
torso_back = (675, 377, 1222, 638)
tile(torso_front, (20, 20, 28, 32))
tile(torso_back, (32, 20, 40, 32), brightness=0.82)
tile((18, 318, 180, 638), (16, 20, 20, 32), brightness=0.78)
tile((318, 318, 480, 638), (28, 20, 32, 32), brightness=0.88)
tile((18, 318, 480, 430), (20, 16, 28, 20), brightness=1.02)
tile((18, 526, 480, 638), (28, 16, 36, 20), brightness=0.66)

# Right arm, using the crystal-rich right side of the concept strip.
arm = (848, 318, 1222, 638)
tile(arm, (44, 20, 48, 32), brightness=0.94)
tile(arm, (40, 20, 44, 32), brightness=0.76, mirror=True)
tile(arm, (48, 20, 52, 32), brightness=0.82)
tile(arm, (52, 20, 56, 32), brightness=0.70, mirror=True)
tile((848, 318, 1222, 420), (44, 16, 48, 20), brightness=1.05)
tile((848, 536, 1222, 638), (48, 16, 52, 20), brightness=0.66)

# Leg UV. The concept's lower islands supply both front and back faces.
leg_left = (447, 715, 781, 1122)
leg_right = (781, 786, 1041, 1122)
tile(leg_left, (4, 20, 8, 32), brightness=0.88)
tile(leg_right, (12, 20, 16, 32), brightness=0.72)
tile(leg_left, (0, 20, 4, 32), brightness=0.76, mirror=True)
tile(leg_right, (8, 20, 12, 32), brightness=0.82)
tile((447, 715, 781, 840), (4, 16, 8, 20), brightness=1.02)
tile((447, 997, 781, 1122), (8, 16, 12, 20), brightness=0.64)

OUTPUT.parent.mkdir(parents=True, exist_ok=True)
target.save(OUTPUT, optimize=True)

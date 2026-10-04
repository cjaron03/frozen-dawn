#!/usr/bin/env python3
"""Original neutral pixel weave for tinted emergency gear geometry."""
from pathlib import Path
from PIL import Image
ROOT = Path(__file__).resolve().parents[3]
image = Image.new("RGBA", (32, 32))
image.putdata([(v, v, v, 255) for y in range(32) for x in range(32)
               for v in [248 if (x * 17 + y * 7) % 11 == 0 else 238 if (x + y) % 4 == 0 else 255]])
image.save(ROOT / "src/main/resources/assets/frozendawn/textures/entity/player/emergency_eva_material.png")

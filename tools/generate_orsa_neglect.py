#!/usr/bin/env python3
"""The replay's neglected screen (owner mockup, 2026-11-09), as two 320 x 180 layers drawn over the whole view, one
texture pixel to a GUI pixel at GUI scale 4 on 1280 x 720, so the marks stay square blocks.

Under: the burned-in ghost of the readout panel and the compact masthead, a stuck column and a stuck row.
Over: uneven backlight (blotches and dark corners), dust, smudges and dead pixels, black and stuck lit.
The same seed as docs/art/orsa-boot/make_orsa_boot_mockup.py, so the marks sit where the mockup put them.

Run: python3 tools/generate_orsa_neglect.py
"""
import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
GUI = ROOT / 'src/main/resources/assets/frozendawn/textures/gui'
GW, GH = 320, 180


def layers():
    rnd = random.Random(0x0D057)
    under = Image.new('RGBA', (GW, GH), (0, 0, 0, 0))
    d = ImageDraw.Draw(under)
    d.rectangle([18, 68, 301, 157], fill=(150, 158, 164, 9))
    d.rectangle([18, 68, 301, 69], fill=(170, 178, 184, 20))
    d.rectangle([31, 100, 210, 100], fill=(160, 168, 174, 12))
    d.rectangle([31, 113, 180, 113], fill=(160, 168, 174, 12))
    masthead = Image.open(GUI / 'orsa_awakening_base_muted.png').convert('RGBA')
    ghost = masthead.resize((108, round(108 * 362 / 817)), Image.BILINEAR)
    ghost.putalpha(ghost.getchannel('A').point(lambda v: v * 14 // 255))
    under.alpha_composite(ghost, (GW // 2 - 54, 18))
    d.line([(233, 0), (233, GH)], fill=(140, 148, 154, 34))
    d.line([(0, 41), (GW, 41)], fill=(140, 148, 154, 18))

    mura = Image.new('L', (8, 5))
    mura.putdata([rnd.randrange(0, 70) for _ in range(40)])
    mura = mura.resize((GW, GH), Image.BICUBIC)
    corner = Image.radial_gradient('L').resize((GW, GH))
    shade = Image.eval(Image.blend(mura, corner.point(lambda v: max(0, v - 120)), 0.5), lambda v: min(150, v * 2))
    over = Image.merge('RGBA', (Image.new('L', (GW, GH), 0),) * 3 + (shade,))
    marks = Image.new('RGBA', (GW, GH), (0, 0, 0, 0))
    o = ImageDraw.Draw(marks)
    for _ in range(70):
        o.point((rnd.randrange(GW), rnd.randrange(GH)), fill=(0, 0, 0, rnd.choice((90, 140, 200))))
    for _ in range(6):
        x, y = rnd.randrange(GW), rnd.randrange(GH)
        o.ellipse([x, y, x + rnd.randrange(3, 9), y + rnd.randrange(2, 5)], fill=(30, 28, 24, 45))
    for _ in range(26):
        o.point((rnd.randrange(GW), rnd.randrange(GH)), fill=(0, 0, 0, 255))
    for _ in range(5):
        o.point((rnd.randrange(GW), rnd.randrange(GH)), fill=(176, 186, 196, 255))
    over.alpha_composite(marks)
    return under, over


if __name__ == '__main__':
    under, over = layers()
    under.save(GUI / 'orsa_replay_neglect_under.png', optimize=True)
    over.save(GUI / 'orsa_replay_neglect_over.png', optimize=True)
    print(GUI / 'orsa_replay_neglect_under.png')
    print(GUI / 'orsa_replay_neglect_over.png')

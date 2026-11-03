"""Draw the ORSA LC-4 Sever Lash item texture.

Original pixel art authored in code for Frozen Dawn on 2026-11-02. No third-party textures or source images.
A gunmetal diagnostic emitter held diagonally like a tool: lead-grey grip, copper coil, acheronite lens and a
red-orange emitter tip. Run from anywhere: python3 docs/art/sever-lash/make_textures.py
"""
from pathlib import Path
from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
OUT = ROOT / 'src/main/resources/assets/frozendawn/textures/item/sever_lash.png'

P = {k: tuple(bytes.fromhex(v)) + (255,) for k, v in dict(
    outline='0b0f12', dark='171c21', steel='414950', light='667077', pale='8c969c',
    lead='3a3d4a', lead2='545869', copper='976344', copper2='c18054',
    lens='5ad1e0', lens2='a8f0f5', tip='e5583a', tip2='ffb08a').items()}

# Grip at the bottom left, emitter at the top right; one pixel per body cell along the diagonal.
BODY = [(2, 13, 'lead'), (3, 12, 'lead2'), (4, 11, 'lead'), (5, 10, 'steel'), (6, 9, 'light'),
        (7, 8, 'copper'), (8, 7, 'copper2'), (9, 6, 'steel'), (10, 5, 'light'), (11, 4, 'lens'),
        (12, 3, 'lens2'), (13, 2, 'tip')]


def main():
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for x, y, color in BODY:
        for dx, dy in ((-1, 0), (0, 1), (-1, 1), (1, 0), (0, -1), (1, -1)):
            if im.getpixel((x + dx, y + dy))[3] == 0:
                im.putpixel((x + dx, y + dy), P['outline'])
    for x, y, color in BODY:
        im.putpixel((x, y), P[color])
        if color in ('steel', 'light', 'copper', 'copper2'):
            im.putpixel((x + 1, y + 1), P['dark'] if color != 'copper2' else P['copper'])
    im.putpixel((14, 1), P['tip2'])
    im.putpixel((5, 12), P['pale'])  # trigger
    OUT.parent.mkdir(parents=True, exist_ok=True)
    im.save(OUT)
    print(OUT)


if __name__ == '__main__':
    main()

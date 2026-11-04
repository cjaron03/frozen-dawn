"""The warning outside the Watch Floor (owner, 2026-11-03: "written quickly in marker or something outside the door before you
enter the room, like someone warning the player"). Someone who stayed, in a hurry, on the bridge hall's north wall by the far
ledge, x 12..16. 16 pixels a block, drawn x3. Run from anywhere: python3 docs/art/terminus/make_vault_warning_mockup.py
"""
import random
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
BLOCK = HERE.parents[2] / 'src/main/resources/assets/frozendawn/textures/block'
OUT = HERE / 'vault-warning-mockup.png'
B, K = 16, 3
F = lambda s: ImageFont.truetype('/System/Library/Fonts/Menlo.ttc', s)
BG, INK, DIM = (24, 28, 33), (226, 232, 236), (150, 160, 167)
MARKER = (16, 17, 19)
LINES = ('IT MOVES IF UNSEEN', 'READ BOARD UP TOP', 'HOLD SAME FUSES', 'BELOW. THEY SHUFFLE', 'SHELL OPENS. HIT IT', 'LAST ROUND IN ORDER', 'DONT STOP LOOKING')
HAND = {'A': '010/101/111/101/101', 'B': '110/101/110/101/110', 'D': '110/101/101/101/110', 'E': '111/100/110/100/111', 'F': '111/100/110/100/100',
        'G': '011/100/101/101/011', 'H': '101/101/111/101/101', 'I': '010/010/010/010/010', 'K': '101/101/110/101/101', 'L': '100/100/100/100/111',
        'M': '101/111/111/101/101', 'N': '110/101/101/101/101', 'O': '010/101/101/101/010', 'P': '110/101/110/100/100', 'R': '110/101/110/101/101',
        'S': '011/100/010/001/110', 'T': '111/010/010/010/010', 'U': '101/101/101/101/011', 'V': '101/101/101/010/010', 'W': '101/101/111/111/101',
        'Y': '101/101/010/010/010', '.': '000/000/000/000/010', ' ': '000/000/000/000/000'}


def scrawl(im, x0, y0):
    """Hasty capitals: each line runs downhill a little, each letter sits where the hand put it."""
    r = random.Random(11)
    for n, line in enumerate(LINES):
        x, slope = x0 + r.randint(0, 1), r.choice((0, 0, 1))
        for i, ch in enumerate(line):
            y = y0 + n * 6 + (i * slope) // 9 + (1 if r.random() < .18 else 0)
            for j, row in enumerate(HAND[ch].split('/')):
                for k, c in enumerate(row):
                    if c == '1' and 0 <= x + k < im.width and 0 <= y + j < im.height:
                        im.putpixel((x + k, y + j), MARKER)
            x += 4
    d = ImageDraw.Draw(im)
    y = y0 + 6 * (len(LINES) - 1) + 6
    d.line((x0 + 3, y, x0 + 62, y + 1), MARKER)        # the last line, underlined twice
    d.line((x0 + 8, y + 2, x0 + 60, y + 2), MARKER)


def tile(name):
    return Image.open(BLOCK / f'{name}.png').convert('RGB')


def wall():
    """The north wall from the ledge, x 8 at the left to x 16 at the right."""
    w, h = 9, 5
    im = Image.new('RGB', (w * B, h * B))
    for i in range(w):
        for j in range(h):
            im.paste(tile('vault_shell'), (i * B, j * B))
        im.paste(tile('vault_mark_hazard'), (i * B, B))
        im.paste(tile('vault_floor'), (i * B, 4 * B))
    for i in range(3):
        im.paste(tile(f'vault_mark_exo_left_{i}'), (i * B, B))
    scrawl(im, 4 * B, B + 1)
    return im


def main():
    pic = wall()
    im = Image.new('RGB', (760, 400), BG)
    d = ImageDraw.Draw(im)
    d.text((24, 16), 'The warning', INK, font=F(24))
    d.text((24, 50), 'Proposed. 16 px a block, drawn x3. Marker on the wall by the far ledge, over the hazard band.', DIM, font=F(12))
    im.paste(pic.resize((pic.width * K, pic.height * K), Image.NEAREST), (24, 78))
    d.text((24, 330), 'Five blocks wide, three tall, x 12..16 of the north wall. Door to the Watch Floor is just right of this.', DIM, font=F(12))
    d.text((24, 350), 'Replaces the orders card and the status line. No hints inside the room.', DIM, font=F(12))
    im.save(OUT)


if __name__ == '__main__':
    main()

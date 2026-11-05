"""What Aurora does when the player stands still, seen from above, 2026-11-04.

Sixteen pixels a block, enlarged three times: she is 12 by 10 texels from above and the room is Minecraft blocks.
Run from anywhere: python3 docs/art/aurora-shepherd/make_idle_curiosity_mockup.py
"""
from pathlib import Path
from PIL import Image, ImageDraw
from make_body_directions import C, font

HERE = Path(__file__).resolve().parent
B = 16
W, H = 14, 9
FLOOR, WALL = (112, 120, 130), (64, 70, 80)
PLAYER, CHEST, HEATER = (88, 120, 170), (150, 104, 54), (210, 110, 50)
STEPS = [(10.0, 5.6), (8.8, 4.6), (7.4, 3.6), (5.9, 2.9), (4.6, 2.6), (3.6, 2.6)]


def her(d, x, y, face=C['glow']):
    px, py = int(x * B), int(y * B)
    d.rectangle([px - 6, py - 5, px + 5, py + 4], fill=(176, 180, 170), outline=(90, 94, 92))
    d.rectangle([px - 8, py - 2, px - 7, py + 1], fill=(50, 54, 60))
    d.rectangle([px + 6, py - 2, px + 7, py + 1], fill=(50, 54, 60))
    d.rectangle([px - 4, py - 5, px + 3, py - 4], fill=face)


def room():
    img = Image.new('RGB', (W * B, H * B), FLOOR)
    d = ImageDraw.Draw(img)
    for x in range(W):
        for y in range(H):
            n = (x * 13 + y * 7) % 6
            edge = x in (0, W - 1) or y in (0, H - 1)
            d.rectangle([x * B, y * B, x * B + B - 1, y * B + B - 1], fill=WALL if edge else tuple(c + n for c in FLOOR))
    d.rectangle([2 * B, 1 * B, 3 * B - 1, 2 * B - 1], fill=CHEST, outline=(90, 60, 30))
    d.rectangle([2 * B + 6, 1 * B + 7, 2 * B + 9, 1 * B + 9], fill=(214, 190, 90))
    d.rectangle([12 * B, 1 * B, 13 * B - 1, 2 * B - 1], fill=HEATER, outline=(120, 60, 30))
    d.rectangle([9 * B + 4, 6 * B + 4, 9 * B + 11, 6 * B + 11], fill=PLAYER)
    for x, y in STEPS[:-1]:
        d.rectangle([int(x * B) - 1, int(y * B) - 1, int(x * B), int(y * B)], fill=C['glow2'])
    her(d, *STEPS[0])
    her(d, *STEPS[-1])
    d.line([int(STEPS[-1][0] * B) - 6, int(STEPS[-1][1] * B) - 8, 2 * B + 10, 2 * B + 2], fill=C['glow'])
    return img.resize((W * B * 3, H * B * 3), Image.NEAREST)


def main():
    sheet = Image.new('RGB', (W * B * 3, H * B * 3 + 190), C['bg'])
    s = ImageDraw.Draw(sheet)
    s.text((24, 16), 'AURORA: IDLE CURIOSITY', font=font(22), fill=C['text'])
    s.text((24, 46), 'From above, 16 pixels a block, enlarged three times.', font=font(14), fill=C['dim'])
    sheet.paste(room(), (0, 80))
    y = H * B * 3 + 96
    for i, note in enumerate(('Blue: you, still for 15 seconds. She leaves your side, drifting.',
                              'She stops a block short of something near you and looks for 5 seconds.',
                              'Move, or a hostile comes near, and she is back at your side at once.')):
        s.text((24, y + i * 26), note, font=font(14), fill=C['text'])
    sheet.save(HERE / 'idle-curiosity-mockup.png')


if __name__ == '__main__':
    main()

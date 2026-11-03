"""Mockup of a mind the ORSA TR-0 Terminus stops: still, grey, then it crumbles away (owner, 2026-11-03).

The owner's first playtest: "the enemies just disappear". They picked "still, grey, then fall", then asked for it to go
"like how the thanos snap is": so the body crumbles from the head down into grey flakes that drift off and pale, instead
of falling. Front view at Minecraft scale, 16 pixels a block, a husk 32 pixels tall, frame by frame in game ticks.
Nothing drops before the Heart falls. Run from anywhere: python3 docs/art/terminus/make_stop_mockup.py
"""
import random
from pathlib import Path
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
OUT = HERE / 'stop-mockup.png'
SCALE = 5
W, H = 56, 44
GROUND = 40

C = {k: tuple(bytes.fromhex(v)) + (255,) for k, v in dict(
    sky='1f2830', ground='3b4a52', snow='c9d6dc', husk='7d7350', husk2='5f5a3e', shirt='4f6a6a', eye='1a1a1a',
    mote='f4fbff', mote2='9fd6e8', speck='e6f6ff', grey='a8b0b4', grey2='8a9296', text='8c969c', bg='20252b').items()}


def body(grey):
    """A husk seen from the front, arms reaching at the viewer, as its own sprite so it can tip over. grey: 0 to 1."""
    im = Image.new('RGBA', (16, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)

    def c(name, alt):
        a, b = C[name], C[alt]
        return tuple(int(a[i] + (b[i] - a[i]) * grey) for i in range(3)) + (255,)
    d.rectangle((4, 20, 7, 31), c('husk2', 'grey2')); d.rectangle((8, 20, 11, 31), c('husk2', 'grey2'))  # legs
    d.rectangle((4, 8, 11, 19), c('shirt', 'grey2'))                                                     # torso
    d.rectangle((0, 8, 3, 13), c('husk', 'grey')); d.rectangle((12, 8, 15, 13), c('husk', 'grey'))      # arms, foreshortened
    d.rectangle((4, 0, 11, 7), c('husk', 'grey'))                                                        # head
    d.point((6, 4), c('eye', 'grey2')); d.point((9, 4), c('eye', 'grey2'))
    return im


CRUMBLE_START, CRUMBLE_TICKS = 10, 20
# Each texel's crumble time: the head goes first, with ragged noise so the edge is never a straight line.
_rng = random.Random(4207)
ORDER = {(x, y): y / 32 + _rng.random() * .25 for x in range(16) for y in range(32)}


def frame(t):
    im = Image.new('RGBA', (W, H), C['sky'])
    d = ImageDraw.Draw(im)
    d.rectangle((0, GROUND, W, H), C['ground']); d.line((0, GROUND, W, GROUND), C['snow'])
    x0, top = 20, GROUND - 32
    sprite = body(min(1, t / 10))
    # Crumbled so far: 0 before the crumble, 1.25 when the last texel has gone.
    k = 0 if t < CRUMBLE_START else (t - CRUMBLE_START) / CRUMBLE_TICKS * 1.25
    for (x, y), when in ORDER.items():
        c = sprite.getpixel((x, y))
        if c[3] == 0:
            continue
        if when >= k:
            im.putpixel((x0 + x, top + y), c)
            continue
        # A flake: it leaves where it was, drifting up and off to the right, paling as it goes, gone after 14 ticks.
        age = (k - when) / 1.25 * CRUMBLE_TICKS
        if age > 14 or (x + y) % 2:
            continue
        drift = _rng.random() * .4
        fx, fy = x0 + x + int(age * (1.1 + drift)), top + y - int(age * (.6 + drift))
        pale = min(1, age / 10)
        col = tuple(int(c[i] + (C['speck'][i] - c[i]) * pale) for i in range(3)) + (255,)
        if 0 <= fx < W and 0 <= fy < GROUND:
            im.putpixel((fx, fy), col)
    if 1 <= t <= 22:
        # The mote lifts from the head, hangs, flickers and winks out.
        y = GROUND - 34 - min(8, t * 1.2)
        if t not in (17, 19):
            d.rectangle((x0 + 7, y, x0 + 8, y + 1), C['mote'])
    return im


FRAMES = [(0, 'tick 0: discharge'), (4, 'tick 4: frozen, greying, mote lifts'),
          (10, 'tick 10: fully grey, still'), (14, 'tick 14: the head crumbles'),
          (18, 'tick 18: flakes drift off and pale'), (23, 'tick 23'), (28, 'tick 28: the last of it'),
          (34, 'tick 34: a few pale flakes, then nothing')]


def main():
    cw, ch = W * SCALE, H * SCALE
    sheet = Image.new('RGBA', (4 * (cw + 10) + 10, 2 * (ch + 26) + 10), C['bg'])
    d = ImageDraw.Draw(sheet)
    for i, (t, label) in enumerate(FRAMES):
        x, y = 10 + (i % 4) * (cw + 10), 10 + (i // 4) * (ch + 26)
        d.text((x, y), label, fill=C['text'])
        sheet.alpha_composite(frame(t).resize((cw, ch), Image.NEAREST), (x, y + 14))
    sheet.save(OUT)
    print(OUT)


if __name__ == '__main__':
    main()

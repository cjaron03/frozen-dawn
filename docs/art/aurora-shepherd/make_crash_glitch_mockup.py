"""Aurora's crash, the owner's revision at the M4 pass: she comes apart before the stop screen. DRAFT for approval.

Same scale as make_crash_mockup.py: 16 texels to the block, her 8 by 6 display, and talk frames on the 480 by 270 GUI grid, enlarged
twice. Three seconds are new, in front of the approved stop screen, which does not change: two of noise, one of stillness with a
line that is not hers. The tearing stays inside the talk screen's own panels: the world behind it is untouched, so it still cannot
read as the game failing. She does not say the word. Nothing here is in the game yet.
Writes crash-glitch-mockup.png, and crash-glitch-mockup.gif at real speed.
Run from anywhere: python3 docs/art/aurora-shepherd/make_crash_glitch_mockup.py
"""
import random
import textwrap
from PIL import Image, ImageDraw
from make_body_directions import C, font, iso
from make_boot_sequence_mockup import WHITE, backdrop, shell, slot
from make_chat_bar_mockup import bar
from make_crash_mockup import B_GREEK, FACE, HERE, PANEL_X, PANEL_Y, stop_dense, unit
from make_face_and_talk_mockup import display, panel, text
from make_private_log_mockup import empty

GLITCH_TICKS, HOLD_TICKS = 40, 20
STOP_AT = GLITCH_TICKS + HOLD_TICKS
WORD = 'PLEASE REMAIN CA'
VERDICT = 'pattern transfer only, subject not recovered'
NOISE = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'
ROW = 44
# Only the talk screen's own furniture tears, from her panel down to the bar.
TEAR_TOP, TEAR_BOTTOM, TEAR_LEFT, TEAR_RIGHT = 8, 218, 84, 396


def strength(tick):
    """Nothing to everything over the two seconds."""
    return min(1, tick / GLITCH_TICKS)


def face(tick):
    """Her neutral face, with more of it wrong every tick, until none of it is hers."""
    rng = random.Random(tick // 2)
    out = [c for c in FACE['Neutral'] if rng.random() > strength(tick)]
    shift = rng.randint(-2, 2) if tick > 12 else 0
    out = [((x + shift) % 8, y, color) for x, y, color in out]
    colors = ['glow', 'glow2', 'stoptext', 'stopdim', 'stop', 'lamp']
    for y in range(6):
        if rng.random() < strength(tick):
            start = rng.randint(0, 6)
            out += [(x, y, rng.choice(colors)) for x in range(start, min(8, start + rng.randint(2, 7)))]
    return out


def said(tick):
    """Her holding phrase, up to where it breaks, then letters and numbers until she stops. Five a tick, once it goes."""
    if tick < len(WORD):
        return WORD[:tick + 1]
    rng = random.Random(7)
    return WORD + ''.join(rng.choice(NOISE) for _ in range((tick - len(WORD)) * 5))


def shake(tick):
    rng = random.Random(tick * 31 + 1)
    reach = 1 + round(3 * strength(tick))
    return rng.randint(-reach, reach), rng.randint(-reach, reach)


def tear(img, tick):
    """Bands of the talk screen slide sideways and a few go to blocks of colour. Nothing outside its panels moves."""
    rng = random.Random(tick // 2 + 99)
    for _ in range(int(14 * strength(tick))):
        top = rng.randint(TEAR_TOP, TEAR_BOTTOM - 8)
        bottom = top + rng.randint(2, 12)
        band = img.crop((TEAR_LEFT, top, TEAR_RIGHT, bottom))
        img.paste(band.transform(band.size, Image.AFFINE, (1, 0, rng.randint(-40, 40), 0, 1, 0)), (TEAR_LEFT, top))
    d = ImageDraw.Draw(img)
    for _ in range(int(7 * strength(tick))):
        x, y = rng.randint(TEAR_LEFT, TEAR_RIGHT - 40), rng.randint(TEAR_TOP, TEAR_BOTTOM - 6)
        d.rectangle([x, y, x + rng.randint(8, 40), y + rng.randint(1, 4)], fill=C[rng.choice(['stop', 'stopdim', 'glow', 'screen'])])


def talk(tick):
    img = backdrop()
    d = ImageDraw.Draw(img)
    shell(d)
    if tick >= STOP_AT:
        stop_dense(d)
        panel(d, [84, 88, 395, 136])
        empty(d, 0, 1, 2, 3)
        bar(d, state='off')
        text(d, (84, 240), 'Esc walks away.', C['dim'])
        return img.resize((960, 540), Image.NEAREST)
    if tick >= GLITCH_TICKS:
        # Everything stops. No name over it, because it is not her talking.
        display(d, [], PANEL_X, PANEL_Y, 10)
        panel(d, [84, 88, 395, 136])
        text(d, (92, 108), VERDICT, C['dim'])
        empty(d, 0, 1, 2, 3)
        bar(d, state='off')
        return img.resize((960, 540), Image.NEAREST)
    display(d, face(tick), PANEL_X, PANEL_Y, 10)
    panel(d, [84, 88, 395, 136])
    text(d, (92, 92), 'AURORA', C['glow'])
    words = said(tick)
    for i in range(3):
        text(d, (92, 103 + i * 10), words[i * ROW:(i + 1) * ROW], WHITE)
    empty(d, 0, 1, 2, 3)
    bar(d, state='off')
    tear(img, tick)
    return img.resize((960, 540), Image.NEAREST)


def body(tick):
    if tick >= STOP_AT:
        return unit(B_GREEK, False, False, 16)
    return unit([], False, False, 16) if tick >= GLITCH_TICKS else unit(face(tick))


BEATS = [(1, '0 s  THE WORD', 'Enter. No think. Her face slips and she starts her holding phrase.'),
         (16, '0.8 s  IT BREAKS', 'PLEASE REMAIN CA, and it stops there. Letters and numbers pour in.'),
         (28, '1.4 s  COMING APART', 'Her body shakes hard. Her panels tear. Nothing behind the panels moves.'),
         (39, '1.95 s  WORST', 'Her display is noise. The line panel is full.'),
         (40, '2 s  STILL', 'All of it stops at once. Dark display, no name, one line, one second.'),
         (60, '3 s  SNAP', 'One frame to blue. The approved stop screen, drop and eight seconds.')]


def main():
    w, h = 2400, 2410
    img = Image.new('RGB', (w, h), C['bg'])
    d = ImageDraw.Draw(img)
    d.text((24, 16), 'AURORA: THE CRASH, COMING APART (M4 PASS REVISION, DRAFT 2)', font=font(26), fill=C['glow'])
    d.text((24, 52), 'Three new seconds before the approved stop screen. Everything after the snap is unchanged.', font=font(15), fill=C['text'])
    d.text((24, 92), 'IN THE WORLD, 16 TEXELS TO THE BLOCK', font=font(17), fill=C['text'])
    for i, (tick, name, caption) in enumerate(BEATS):
        x = 24 + i * 396
        d.rectangle([x, 122, x + 384, 462], fill=C['panel'])
        d.text((x + 12, 132), name, font=font(15), fill=C['glow'])
        dx, dy = shake(tick) if tick < GLITCH_TICKS and tick > 15 else (0, 0)
        iso(d, body(tick), x + 192 + dx * 6, 242 + dy * 6, 6)
        for j, row in enumerate(textwrap.wrap(caption, 48)):
            d.text((x + 12, 412 + j * 18), row, font=font(12), fill=C['text'])
    d.text((24, 482), 'ON THE TALK SCREEN', font=font(17), fill=C['text'])
    for i, (tick, name, _) in enumerate(BEATS[1:]):
        x, y = 24 + (i % 2) * 984, 512 + (i // 2) * 590
        d.text((x, y), name, font=font(16), fill=C['glow'])
        img.paste(talk(tick), (x, y + 26))
    y = 2290
    bars = [(GLITCH_TICKS, 'Noise', C['heat2']), (HOLD_TICKS, 'Still', C['steel']), (160, 'Stop screen, 8 s, as approved', C['stop']), (64, 'Logo, 3.2 s', C['glow'])]
    x = 24
    d.text((24, y - 26), 'TICKS', font=font(15), fill=C['text'])
    for ticks, name, color in bars:
        d.rectangle([x, y, x + ticks * 5, y + 26], fill=color)
        d.text((x + 8, y + 6), name, font=font(13), fill=WHITE if color != C['glow'] else (10, 12, 14))
        x += ticks * 5 + 3
    d.text((24, y + 44), 'With the talk screen closed, the same three seconds play on her body, and the line goes to the ambient strip.', font=font(14), fill=C['text'])
    img.crop((0, 0, w, y + 80)).save(HERE / 'crash-glitch-mockup.png')

    frames = []
    for tick in list(range(0, STOP_AT + 30)):
        frame = Image.new('RGB', (1320, 540), C['bg'])
        frame.paste(talk(tick), (360, 0))
        fd = ImageDraw.Draw(frame)
        dx, dy = shake(tick) if 15 < tick < GLITCH_TICKS else (0, 0)
        iso(fd, body(tick), 180 + dx * 5, 200 + dy * 5, 5)
        frames.append(frame.convert('P', palette=Image.ADAPTIVE, colors=64))
    frames[0].save(HERE / 'crash-glitch-mockup.gif', save_all=True, append_images=frames[1:], duration=50, loop=0)


if __name__ == '__main__':
    main()

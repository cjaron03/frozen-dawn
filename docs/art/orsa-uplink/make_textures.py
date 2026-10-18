"""Draw the ORSA Uplink textures, models and blockstates.

Original pixel art authored in code for Frozen Dawn on 2026-10-17. No third-party textures or source images.
Palette follows the ORSA machine set (power monitor, induction furnace): gunmetal, copper trim, cyan/amber
status light. Run from anywhere: python3 docs/art/orsa-uplink/make_textures.py
"""
from pathlib import Path
import hashlib, json
from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
ASSETS = ROOT / 'src/main/resources/assets/frozendawn'

P = {k: tuple(bytes.fromhex(v)) for k, v in dict(
    black='0b0f12', dark='171c21', steel='283039', steel2='2f373f', mid='414950', light='667077', pale='8c969c',
    white='c9d1d4', copper='976344', copper2='c18054', screen='07141c', screen2='153843',
    cyan='2dd4d2', cyan2='1c7584', amber='e5a064', red='e58b83', pcb='1f3a2c', pcb2='2c5640', gold='d9b45a').items()}


def noise(x, y, salt):
    return hashlib.sha256(f'{salt}:{x}:{y}'.encode()).digest()[0]


def plate(salt, base=('steel', 'steel2', 'mid'), bias=(150, 225)):
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            n = noise(x, y, salt)
            im.putpixel((x, y), P[base[0]] if n < bias[0] else P[base[1]] if n < bias[1] else P[base[2]])
    return im


def rect(im, x0, y0, x1, y1, color):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            im.putpixel((x, y), P[color])


def frame(im, x0, y0, x1, y1, color):
    rect(im, x0, y0, x1, y0, color); rect(im, x0, y1, x1, y1, color)
    rect(im, x0, y0, x0, y1, color); rect(im, x1, y0, x1, y1, color)


def bolts(im):
    for x, y in [(1, 1), (14, 1), (1, 14), (14, 14)]:
        im.putpixel((x, y), P['light'])


def console_front(state):
    im = plate('front')
    frame(im, 0, 0, 15, 15, 'dark')
    bolts(im)
    rect(im, 2, 2, 13, 2, 'copper'); rect(im, 2, 10, 13, 10, 'copper')
    rect(im, 2, 3, 13, 9, 'dark')
    rect(im, 3, 3, 12, 9, 'screen')
    if state == 1:  # listening: a calm cyan carrier trace
        trace = [6, 5, 6, 7, 6, 4, 6, 8, 6, 5]
        for i, y in enumerate(trace):
            im.putpixel((3 + i, y), P['cyan'])
            if y != 6: im.putpixel((3 + i, 6), P['cyan2'])
    elif state == 2:  # transmitting: separated amber level bars, peaks in red
        for x, h in zip(range(3, 13, 2), [3, 5, 6, 4, 2]):
            for dy in range(h):
                im.putpixel((x, 9 - dy), P['red' if dy >= 5 else 'amber'])
                im.putpixel((x + 1, 9 - dy), P['copper2' if dy < 5 else 'red'])
    else:
        for x in range(3, 13): im.putpixel((x, 6), P['screen2'])
    # control strip: three keys and a status lamp
    rect(im, 2, 11, 13, 13, 'steel')
    for x in (3, 6, 9):
        rect(im, x, 12, x + 1, 12, 'light')
    lamp = {0: 'mid', 1: 'cyan', 2: 'red'}[state]
    im.putpixel((12, 12), P[lamp])
    return im


def console_side():
    im = plate('side')
    frame(im, 0, 0, 15, 15, 'dark')
    rect(im, 1, 1, 14, 1, 'copper'); rect(im, 1, 14, 14, 14, 'copper')
    for y in (4, 6, 8, 10):  # cooling louvres
        rect(im, 3, y, 12, y, 'black'); rect(im, 3, y + 1, 12, y + 1, 'light')
    return im


def console_top():
    im = plate('top')
    frame(im, 0, 0, 15, 15, 'copper')
    rect(im, 6, 6, 9, 9, 'dark'); rect(im, 7, 7, 8, 8, 'copper2')  # coax port
    bolts(im)
    return im


def console_back():
    im = plate('back')
    frame(im, 0, 0, 15, 15, 'dark')
    rect(im, 3, 3, 12, 12, 'steel'); frame(im, 3, 3, 12, 12, 'mid')
    for x, y in [(5, 5), (10, 5), (5, 10), (10, 10)]: im.putpixel((x, y), P['copper2'])
    rect(im, 7, 12, 8, 15, 'black')  # cable run
    return im


def dish_face():
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            c = 'white' if d < 2 else 'pale' if d < 4 else 'white' if d < 5 else 'pale' if d < 6.5 else 'light' if d < 7.5 else 'mid'
            if c in ('pale', 'white') and noise(x, y, 'dish') > 230: c = 'light'
            im.putpixel((x, y), P[c])
    for i in range(16):  # support ribs
        for x, y in [(i, i), (15 - i, i)]:
            if 2 < abs(x - 7.5) < 7: im.putpixel((x, y), P['light'])
    rect(im, 7, 7, 8, 8, 'copper2')  # feed point
    return im


def dish_back():
    im = plate('dishback', base=('mid', 'steel2', 'light'))
    frame(im, 0, 0, 15, 15, 'steel')
    rect(im, 7, 0, 8, 15, 'steel'); rect(im, 0, 7, 15, 8, 'steel')
    return im


def mast():
    im = plate('mast')
    rect(im, 0, 0, 15, 0, 'copper'); rect(im, 0, 15, 15, 15, 'copper')
    for y in range(2, 14, 3): rect(im, 0, y, 15, y, 'dark')
    return im


def signal_processor():
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    rect(im, 2, 3, 13, 12, 'pcb'); frame(im, 2, 3, 13, 12, 'pcb2')
    for x in range(3, 13, 2):  # edge pins
        im.putpixel((x, 2), P['gold']); im.putpixel((x, 13), P['gold'])
    rect(im, 5, 5, 10, 10, 'dark'); frame(im, 5, 5, 10, 10, 'mid')  # processor die
    im.putpixel((6, 6), P['light'])
    for x, y in [(3, 5), (3, 8), (12, 6), (12, 9)]: im.putpixel((x, y), P['copper2'])
    im.putpixel((11, 11), P['cyan']); im.putpixel((4, 11), P['amber'])
    return im


def feed_horn():
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    # side view of a pyramidal horn: waveguide flange on the left, flaring to the mouth on the right
    rect(im, 1, 6, 2, 10, 'mid'); frame(im, 1, 6, 2, 10, 'steel'); im.putpixel((1, 8), P['light'])
    rect(im, 3, 7, 4, 9, 'copper')
    for x in range(5, 14):
        half = 1 + (x - 5) * 5 // 8
        rect(im, x, 8 - half, x, 8 + half, 'copper2')
        im.putpixel((x, 8 - half), P['copper']); im.putpixel((x, 8 + half), P['copper'])
    rect(im, 14, 2, 14, 14, 'copper'); rect(im, 13, 3, 13, 13, 'dark')  # rim and dark aperture
    for x in range(6, 13, 3): im.putpixel((x, 8), P['gold'])  # seam rivets
    return im


def save(im, rel):
    out = ASSETS / 'textures' / rel
    out.parent.mkdir(parents=True, exist_ok=True)
    im.save(out)
    preview = HERE / (rel.replace('/', '_'))
    im.save(preview)
    return dict(path=str(out.relative_to(ROOT)), width=16, height=16, sha256=hashlib.sha256(out.read_bytes()).hexdigest())


def write(rel, data):
    out = ASSETS / rel
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(data, indent=2) + '\n')


manifest = []
for s in range(3):
    manifest.append(save(console_front(s), f'block/uplink_console_front_{s}.png'))
manifest.append(save(console_side(), 'block/uplink_console_side.png'))
manifest.append(save(console_top(), 'block/uplink_console_top.png'))
manifest.append(save(console_back(), 'block/uplink_console_back.png'))
manifest.append(save(dish_face(), 'block/uplink_dish_face.png'))
manifest.append(save(dish_back(), 'block/uplink_dish_back.png'))
manifest.append(save(mast(), 'block/uplink_dish_mast.png'))
manifest.append(save(signal_processor(), 'item/signal_processor.png'))
manifest.append(save(feed_horn(), 'item/feed_horn.png'))

for s in range(3):
    write(f'models/block/uplink_console_{s}.json', dict(parent='minecraft:block/cube', textures=dict(
        north=f'frozendawn:block/uplink_console_front_{s}', south='frozendawn:block/uplink_console_back',
        east='frozendawn:block/uplink_console_side', west='frozendawn:block/uplink_console_side',
        up='frozendawn:block/uplink_console_top', down='frozendawn:block/induction_furnace_bottom',
        particle='frozendawn:block/uplink_console_side')))
rotations = [('north', 0), ('east', 90), ('south', 180), ('west', 270)]
write('blockstates/uplink_console.json', dict(variants={f'active={s},facing={f}': dict(model=f'frozendawn:block/uplink_console_{s}', y=r)
                                                         for f, r in rotations for s in range(3)}))
write('models/item/uplink_console.json', dict(parent='frozendawn:block/uplink_console_1'))

def faces(up='#back', down='#back', side='#back'):
    return dict(north=dict(texture=side), south=dict(texture=side), east=dict(texture=side), west=dict(texture=side),
                up=dict(texture=up), down=dict(texture=down))
tilt = dict(origin=[8, 8, 8], axis='x', angle=-22.5)
dish_textures = dict(face='frozendawn:block/uplink_dish_face', back='frozendawn:block/uplink_dish_back',
                     mast='frozendawn:block/uplink_dish_mast', particle='frozendawn:block/uplink_dish_back')
base = [{'from': [3, 0, 3], 'to': [13, 1, 13], 'faces': faces('#mast', '#mast', '#mast')},
        {'from': [6.5, 1, 6.5], 'to': [9.5, 16, 9.5], 'faces': faces('#mast', '#mast', '#mast')}]
# Reflector, centred on (8, 8, 8) so the renderer can pivot it; the renderer draws it at twice block scale.
reflector = [{'from': [6, 5, 6], 'to': [10, 7, 10], 'faces': faces('#back', '#back', '#back')},
             {'from': [1, 7, 1], 'to': [15, 9, 15], 'rotation': tilt, 'faces': faces('#face', '#back', '#back')},
             {'from': [7.5, 9, 7.5], 'to': [8.5, 14, 8.5], 'rotation': tilt, 'faces': faces('#mast', '#mast', '#mast')},
             {'from': [7, 13.5, 7], 'to': [9, 15.5, 9], 'rotation': tilt, 'faces': faces('#back', '#back', '#back')}]
write('models/block/uplink_dish.json', dict(parent='minecraft:block/block', textures=dish_textures, elements=base))
write('models/block/uplink_dish_reflector.json', dict(textures=dish_textures, elements=reflector))
# Inventory icon: the old one-block composition, reflector at block scale on a short mast.
icon = [{'from': [4, 0, 4], 'to': [12, 1, 12], 'faces': faces('#mast', '#mast', '#mast')},
        {'from': [7, 1, 7], 'to': [9, 7, 9], 'faces': faces('#mast', '#mast', '#mast')},
        {'from': [1, 7, 1], 'to': [15, 9, 15], 'rotation': dict(origin=[8, 9, 8], axis='x', angle=-22.5), 'faces': faces('#face', '#back', '#back')},
        {'from': [7.5, 9, 7.5], 'to': [8.5, 14, 8.5], 'rotation': dict(origin=[8, 9, 8], axis='x', angle=-22.5), 'faces': faces('#mast', '#mast', '#mast')},
        {'from': [7, 13, 7], 'to': [9, 15, 9], 'rotation': dict(origin=[8, 9, 8], axis='x', angle=-22.5), 'faces': faces('#back', '#back', '#back')}]
write('models/block/uplink_dish_item.json', dict(parent='minecraft:block/block', textures=dish_textures, elements=icon))
write('blockstates/uplink_dish.json', dict(variants={f'facing={f},mode={m}': dict(model='frozendawn:block/uplink_dish', y=r)
                                                      for f, r in rotations for m in range(4)}))
write('models/item/uplink_dish.json', dict(parent='frozendawn:block/uplink_dish_item'))
for item in ('signal_processor', 'feed_horn'):
    write(f'models/item/{item}.json', dict(parent='minecraft:item/generated', textures=dict(layer0=f'frozendawn:item/{item}')))

(HERE / 'manifest.json').write_text(json.dumps(dict(created='2026-10-17', creator='Frozen Dawn; authored in code', script='make_textures.py',
                                                    third_party_pixels=False, palette={k: '%02x%02x%02x' % v for k, v in P.items()},
                                                    textures=manifest), indent=2) + '\n')

sheet = Image.new('RGBA', (len(manifest) * 104, 96), (40, 44, 48, 255))
for i, entry in enumerate(manifest):
    sheet.paste(Image.open(ROOT / entry['path']).convert('RGBA').resize((96, 96), Image.NEAREST), (i * 104, 0))
sheet.save(HERE / 'contact-sheet.png')
print(f'{len(manifest)} textures written')

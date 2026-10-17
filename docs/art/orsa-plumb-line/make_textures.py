"""Draw the ORSA GPR-4 Plumb Line textures, models and blockstates.

Original pixel art authored in code for Frozen Dawn on 2026-10-17. No third-party textures or source images.
Palette follows the ORSA machine set. Run from anywhere: python3 docs/art/orsa-plumb-line/make_textures.py
"""
from pathlib import Path
import hashlib, json
from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
ASSETS = ROOT / 'src/main/resources/assets/frozendawn'
P = {k: tuple(bytes.fromhex(v)) for k, v in dict(
    black='0b0f12', dark='171c21', steel='283039', steel2='2f373f', mid='414950', light='667077', pale='8c969c',
    copper='976344', copper2='c18054', screen='07141c', screen2='153843', cyan='2dd4d2', cyan2='1c7584',
    amber='e5a064', amber2='8a5a2e').items()}


def noise(x, y, salt):
    return hashlib.sha256(f'{salt}:{x}:{y}'.encode()).digest()[0]


def plate(salt):
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            n = noise(x, y, salt)
            im.putpixel((x, y), P['steel'] if n < 150 else P['steel2'] if n < 225 else P['mid'])
    return im


def rect(im, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            im.putpixel((x, y), P[c])


def frame(im, x0, y0, x1, y1, c):
    rect(im, x0, y0, x1, y0, c); rect(im, x0, y1, x1, y1, c); rect(im, x0, y0, x0, y1, c); rect(im, x1, y0, x1, y1, c)


def front(pulsing):
    im = plate('gpr-front')
    frame(im, 0, 0, 15, 15, 'dark')
    rect(im, 2, 2, 13, 2, 'copper'); rect(im, 2, 13, 13, 13, 'copper')
    rect(im, 2, 3, 13, 12, 'screen')
    # Radar return display: a buried scan section, with ground line and echo arcs below it.
    rect(im, 3, 5, 12, 5, 'cyan2' if not pulsing else 'cyan')
    arcs = 'amber' if pulsing else 'screen2'
    for r, c in ((2, arcs), (4, arcs), (6, 'cyan2' if pulsing else 'screen2')):
        for x in range(3, 13):
            dx = x - 7.5
            y = int(round(6 + r - (dx * dx) / (r * 3.2)))
            if 6 <= y <= 11: im.putpixel((x, y), P[c])
    if pulsing:
        im.putpixel((9, 9), P['amber']); im.putpixel((5, 10), P['amber'])
    im.putpixel((13, 14), P['amber' if pulsing else 'mid'])
    return im


def side():
    im = plate('gpr-side')
    frame(im, 0, 0, 15, 15, 'dark')
    for y in (3, 6, 9, 12):
        rect(im, 3, y, 12, y, 'black'); rect(im, 3, y + 1, 12, y + 1, 'light')
    return im


def top():
    im = plate('gpr-top')
    frame(im, 0, 0, 15, 15, 'copper')
    for i in range(2, 14, 2):  # antenna grille
        rect(im, 2, i, 13, i, 'dark')
    rect(im, 6, 6, 9, 9, 'mid'); frame(im, 6, 6, 9, 9, 'copper2')
    return im


def bottom():
    im = plate('gpr-bottom')
    frame(im, 0, 0, 15, 15, 'dark')
    for r in (2, 4, 6):  # emitter rings facing the ground
        for x in range(16):
            for y in range(16):
                d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** .5
                if abs(d - r) < 0.5: im.putpixel((x, y), P['copper' if r != 4 else 'copper2'])
    return im


def save(im, rel):
    out = ASSETS / 'textures' / rel
    out.parent.mkdir(parents=True, exist_ok=True)
    im.save(out); im.save(HERE / rel.replace('/', '_'))
    return dict(path=str(out.relative_to(ROOT)), width=16, height=16, sha256=hashlib.sha256(out.read_bytes()).hexdigest())


def write(rel, data):
    out = ASSETS / rel
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(data, indent=2) + '\n')


manifest = [save(front(False), 'block/plumb_line_front.png'), save(front(True), 'block/plumb_line_front_on.png'),
            save(side(), 'block/plumb_line_side.png'), save(top(), 'block/plumb_line_top.png'), save(bottom(), 'block/plumb_line_bottom.png')]
for name, f in (('plumb_line', 'plumb_line_front'), ('plumb_line_on', 'plumb_line_front_on')):
    write(f'models/block/{name}.json', dict(parent='minecraft:block/cube', textures=dict(
        north=f'frozendawn:block/{f}', south='frozendawn:block/plumb_line_side', east='frozendawn:block/plumb_line_side',
        west='frozendawn:block/plumb_line_side', up='frozendawn:block/plumb_line_top', down='frozendawn:block/plumb_line_bottom',
        particle='frozendawn:block/plumb_line_side')))
rotations = [('north', 0), ('east', 90), ('south', 180), ('west', 270)]
write('blockstates/plumb_line.json', dict(variants={f'facing={f},pulsing={str(p).lower()}': dict(model='frozendawn:block/plumb_line' + ('_on' if p else ''), y=r)
                                                    for f, r in rotations for p in (False, True)}))
write('models/item/plumb_line.json', dict(parent='frozendawn:block/plumb_line'))
(HERE / 'manifest.json').write_text(json.dumps(dict(created='2026-10-17', creator='Frozen Dawn; authored in code', script='make_textures.py',
                                                    third_party_pixels=False, textures=manifest), indent=2) + '\n')
sheet = Image.new('RGBA', (len(manifest) * 104, 96), (40, 44, 48, 255))
for i, e in enumerate(manifest):
    sheet.paste(Image.open(ROOT / e['path']).convert('RGBA').resize((96, 96), Image.NEAREST), (i * 104, 0))
sheet.save(HERE / 'contact-sheet.png')
print(f'{len(manifest)} textures written')

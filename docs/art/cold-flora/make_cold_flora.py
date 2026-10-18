"""Draw the Cold Flora textures and write their models, blockstates, loot, recipes, tags and worldgen.

Original pixel art authored in code for Frozen Dawn on 2026-10-18. No third-party textures or source images.
Rimewood follows the Blind Playtest (Oct 7) appearance table: charcoal bark with icy-blue seams, silver rings around a
blue heart, desaturated grey-blue planks, midnight-teal needles with frosted tips. Frostcap is a pale foxfire fungus.
Run from anywhere: python3 docs/art/cold-flora/make_cold_flora.py
"""
from pathlib import Path
import hashlib, json, math
from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
ASSETS = ROOT / 'src/main/resources/assets/frozendawn'
DATA = ROOT / 'src/main/resources/data'
NS = 'frozendawn'
P = {k: tuple(bytes.fromhex(v)) for k, v in dict(
    bark0='2c2f33', bark1='3a3d42', bark2='474b51', crack='1c1e21', seam='6fa6c2', seam2='9fd0e4',
    ring0='aab3ba', ring1='c3cbd1', ring2='8e989f', heart='5f7f99', heart2='4a6782',
    plank0='76858f', plank1='6a7883', plank2='83929c', grain='59656f', nail='3d454c',
    teal0='0f2f37', teal1='174450', teal2='1f5664', frost='e3f2f6', frost2='b9dde7',
    shoot='2a3438', cap0='dcedf4', cap1='bfe2ee', cap2='9fd3e4', glow='eefaff', gill='8db6c9', stem='c8d4da', stem2='a9b7be',
    spot='f6fdff', mush0='8e9a7a', mush1='6f7b5e', mush2='a7b08f', resin0='d9a441', resin1='b07a24', resin2='f1cb6d',
    sulf0='e3d64a', sulf1='b8a92d', coal='26282b', red='c0503a', bowl0='6b4a2f', bowl1='8a6340', stew0='a9c7d3', stew1='88a9b6',
    metal='7b8389', metal2='a4acb2', frame='4f5b64', glass='a9cfe0').items()}
CLEAR = (0, 0, 0, 0)


def noise(x, y, salt):
    return hashlib.sha256(f'{salt}:{x}:{y}'.encode()).digest()[0]


def img(fill=CLEAR, w=16, h=16):
    return Image.new('RGBA', (w, h), fill)


def put(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height: im.putpixel((x, y), P[c] if isinstance(c, str) else c)


def rect(im, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1): put(im, x, y, c)


# ---------- Rimewood ----------

def bark(salt='bark'):
    im = img()
    for x in range(16):
        col = 'bark1' if noise(x, 0, salt) < 150 else 'bark2' if noise(x, 0, salt) < 210 else 'bark0'
        for y in range(16):
            n = noise(x, y, salt)
            put(im, x, y, col if n < 200 else 'bark0')
    for x in (2, 6, 11, 14):  # deep vertical cracks with icy seams
        for y in range(16):
            if noise(x, y, salt + 'c') < 215: put(im, x, y, 'crack')
            if noise(x, y, salt + 's') < 40: put(im, x, y, 'seam' if noise(x, y, 'q') < 160 else 'seam2')
    return im


def rings(stripped=False):
    im = img()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d > 7.2 and not stripped: put(im, x, y, 'bark1' if noise(x, y, 'rb') < 170 else 'bark0'); continue
            if d < 2.2: put(im, x, y, 'heart' if noise(x, y, 'h') < 170 else 'heart2'); continue
            put(im, x, y, 'ring1' if int(d) % 2 else ('ring0' if noise(x, y, 'r') < 200 else 'ring2'))
    return im


def stripped_side():
    im = img()
    for x in range(16):
        for y in range(16):
            n = noise(x, y, 'ss')
            put(im, x, y, 'ring1' if n < 110 else 'ring0' if n < 220 else 'ring2')
    for x in (3, 9, 13):
        for y in range(16):
            if noise(x, y, 'sg') < 120: put(im, x, y, 'ring2')
    for y in range(16):
        if noise(0, y, 'sh') < 30: put(im, 7, y, 'heart')
    return im


def planks():
    im = img()
    for y in range(16):
        row = y // 4
        for x in range(16):
            n = noise(x, y, 'pl')
            put(im, x, y, 'plank0' if n < 120 else 'plank1' if n < 210 else 'plank2')
        if y % 4 == 3: rect(im, 0, y, 15, y, 'grain')
        for x in range(16):
            if noise(x, row, 'grain') < 30: put(im, x, y, 'grain')
    for y, x in ((0, 4), (4, 11), (8, 2), (12, 9)):  # board ends
        rect(im, x, y, x, y + 2, 'grain')
    return im


def leaves():
    im = img()
    for y in range(16):
        for x in range(16):
            n = noise(x, y, 'lv')
            if n < 36: continue  # gaps reveal the branches
            put(im, x, y, 'teal0' if n < 120 else 'teal1' if n < 200 else 'teal2')
            if noise(x, y, 'tip') < 26 and y < 15: put(im, x, y, 'frost' if n % 2 else 'frost2')
    return im


def sapling():
    im = img()
    rect(im, 7, 7, 8, 15, 'shoot')
    for i in range(4):  # two frosted branches
        put(im, 6 - i, 9 - i, 'teal1'); put(im, 9 + i, 8 - i, 'teal1')
        put(im, 6 - i, 8 - i, 'frost' if i == 3 else 'teal2'); put(im, 9 + i, 7 - i, 'frost' if i == 3 else 'teal2')
    rect(im, 7, 3, 8, 6, 'teal2'); put(im, 7, 2, 'frost'); put(im, 8, 2, 'frost2')
    return im


def door(top):
    im = planks()
    rect(im, 0, 0, 15, 0, 'frame'); rect(im, 0, 0, 0, 15, 'frame'); rect(im, 15, 0, 15, 15, 'frame')
    if top:
        rect(im, 3, 3, 12, 11, 'frame')
        for y in range(4, 11):
            for x in range(4, 12):
                put(im, x, y, 'glass' if noise(x, y, 'dw') < 200 else 'frost')
        rect(im, 4, 7, 11, 7, 'frame'); rect(im, 7, 4, 8, 10, 'frame')
    else:
        rect(im, 0, 15, 15, 15, 'frame'); rect(im, 2, 6, 13, 6, 'grain'); rect(im, 12, 8, 13, 9, 'metal2')
    return im


def trapdoor():
    im = planks()
    rect(im, 0, 0, 15, 0, 'frame'); rect(im, 0, 15, 15, 15, 'frame'); rect(im, 0, 0, 0, 15, 'frame'); rect(im, 15, 0, 15, 15, 'frame')
    for i in range(1, 15): put(im, i, i, 'frame'); put(im, 15 - i, i, 'grain')
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)): rect(im, x, y, x, y, CLEAR)
    return im


def sign_sheet(hanging=False):
    """Sign entity sheet: wood grain everywhere the board samples, bark for the post or iron for the chains."""
    im = img(w=64, h=32)
    tile = planks()
    for y in range(32):
        for x in range(64): im.putpixel((x, y), tile.getpixel((x % 16, y % 16)))
    if hanging:
        for y in range(0, 6):
            for x in range(0, 16): put(im, x, y, 'metal' if (x + y) % 3 else 'metal2')
    else:
        b = bark()
        for y in range(14, 32):
            for x in range(0, 8): im.putpixel((x, y), b.getpixel((x % 16, y % 16)))
    return im


def door_item():
    im = img()
    rect(im, 4, 1, 11, 14, 'plank0')
    for y in range(1, 15):
        for x in range(4, 12):
            if noise(x, y, 'di') < 70: put(im, x, y, 'plank1')
    rect(im, 4, 1, 11, 1, 'frame'); rect(im, 4, 14, 11, 14, 'frame'); rect(im, 4, 1, 4, 14, 'frame'); rect(im, 11, 1, 11, 14, 'frame')
    rect(im, 6, 3, 9, 6, 'glass'); put(im, 6, 3, 'frost'); rect(im, 9, 9, 10, 9, 'metal2')
    return im


def sign_item(hanging=False):
    im = img()
    if hanging:
        rect(im, 4, 1, 4, 4, 'metal'); rect(im, 11, 1, 11, 4, 'metal'); rect(im, 2, 0, 13, 1, 'ring2')
        rect(im, 2, 5, 13, 12, 'plank0'); rect(im, 2, 5, 13, 5, 'frame'); rect(im, 2, 12, 13, 12, 'frame')
        rect(im, 4, 8, 11, 8, 'grain'); rect(im, 4, 10, 9, 10, 'grain')
    else:
        rect(im, 1, 3, 14, 10, 'plank0'); rect(im, 1, 3, 14, 3, 'plank2'); rect(im, 1, 10, 14, 10, 'grain')
        rect(im, 3, 6, 12, 6, 'grain'); rect(im, 3, 8, 10, 8, 'grain'); rect(im, 7, 11, 8, 15, 'bark1')
    return im


def gui_hanging():
    im = planks()
    rect(im, 0, 0, 15, 0, 'frame'); rect(im, 0, 15, 15, 15, 'frame')
    return im


# ---------- Frostcap ----------

def cap_top():
    im = img()
    for y in range(16):
        for x in range(16):
            n = noise(x, y, 'ct')
            put(im, x, y, 'cap0' if n < 140 else 'cap1' if n < 220 else 'cap2')
            if noise(x, y, 'sp') < 14: put(im, x, y, 'spot')
    return im


def cap_side():
    im = img()
    for y in range(16):
        for x in range(16):
            put(im, x, y, 'cap1' if y < 6 else 'gill' if y < 10 else 'cap2')
            if y < 6 and noise(x, y, 'cs') < 24: put(im, x, y, 'glow')
    return im


def stem():
    im = img()
    for y in range(16):
        for x in range(16): put(im, x, y, 'stem' if noise(x, y, 'st') < 190 else 'stem2')
    return im


def frostcap_item(kind):
    im = img()
    if kind == 'mush':
        for y in range(8, 14):
            for x in range(3, 13):
                if math.hypot((x - 7.5) / 5.5, (y - 11) / 3) < 1: put(im, x, y, 'mush0' if noise(x, y, 'm') < 150 else 'mush1')
        for x, y in ((5, 10), (9, 11), (7, 12)): put(im, x, y, 'mush2')
        return im
    if kind == 'dried':
        for y in range(4, 12):
            for x in range(3, 13):
                if math.hypot((x - 7.5) / 5, (y - 8) / 4) < 1: put(im, x, y, 'ring0' if noise(x, y, 'd') < 150 else 'ring2')
        rect(im, 4, 8, 11, 8, 'gill'); rect(im, 7, 11, 8, 13, 'stem2')
        return im
    cap, cap_dark = ('cap0', 'cap2') if kind == 'frozen' else ('gill', 'stem2')
    rect(im, 7, 9, 8, 14, 'stem'); put(im, 8, 14, 'stem2')
    for y in range(3, 10):
        for x in range(2, 14):
            if math.hypot((x - 7.5) / 6, (y - 8.5) / 5.5) < 1 and y < 9: put(im, x, y, cap if noise(x, y, kind) < 170 else cap_dark)
    rect(im, 3, 8, 12, 8, 'gill')
    if kind == 'frozen':
        for x, y in ((4, 5), (7, 4), (10, 6), (6, 7)): put(im, x, y, 'spot')
        for x in (3, 6, 9, 12): put(im, x, 9, 'frost')  # rime on the gills
    else:
        for x, y in ((5, 6), (9, 5)): put(im, x, y, 'cap1')
        put(im, 4, 10, 'stew1')  # a melt drip
    return im


def stew():
    im = img()
    for y in range(6, 14):
        for x in range(1, 15):
            if math.hypot((x - 7.5) / 7, (y - 7) / 6.5) < 1 and y >= 8: put(im, x, y, 'bowl0' if (x + y) % 5 else 'bowl1')
    for x in range(2, 14): put(im, x, 7, 'stew0' if noise(x, 7, 'sw') < 160 else 'stew1')
    for x in range(3, 13): put(im, x, 8, 'stew1')
    for x, y in ((4, 6), (8, 6), (10, 7), (6, 7)): put(im, x, y, 'cap0')
    return im


def resin():
    im = img()
    for y in range(3, 14):
        for x in range(4, 12):
            if math.hypot((x - 7.5) / 3.5, (y - 9) / 5) < 1: put(im, x, y, 'resin0' if noise(x, y, 'rs') < 160 else 'resin1')
    put(im, 6, 6, 'resin2'); put(im, 6, 7, 'resin2'); put(im, 7, 5, 'resin2'); rect(im, 7, 2, 8, 3, 'resin1')
    return im


def igniter():
    im = img()
    rect(im, 5, 2, 10, 13, 'sulf0'); rect(im, 5, 2, 5, 13, 'sulf1'); rect(im, 10, 2, 10, 13, 'sulf1')
    rect(im, 6, 0, 9, 2, 'red'); put(im, 7, 0, 'resin2'); rect(im, 6, 13, 9, 15, 'coal')
    for y in (5, 8, 11): rect(im, 6, y, 9, y, 'sulf1')
    return im


def binder():
    im = img()
    for y in range(3, 14):
        for x in range(2, 14):
            d = math.hypot((x - 7.5) / 5.5, (y - 8.5) / 5)
            if d < 1: put(im, x, y, ('sulf0' if (x + y) % 3 == 0 else 'resin0') if d < .7 else 'resin1')
    for x, y in ((5, 6), (9, 9), (7, 11)): put(im, x, y, 'resin2')
    return im


# ---------- output ----------

manifest = []


def save(im, rel):
    out = ASSETS / 'textures' / rel
    out.parent.mkdir(parents=True, exist_ok=True)
    im.save(out)
    manifest.append(dict(path=str(out.relative_to(ROOT)), width=im.width, height=im.height, sha256=hashlib.sha256(out.read_bytes()).hexdigest()))


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n')


def asset(rel, data): write(ASSETS / rel, data)
def data(rel, data_): write(DATA / NS / rel, data_)


def rl(name, kind='block'): return f'{NS}:{kind}/{name}'


for name, im in dict(rimewood_log=bark(), rimewood_log_top=rings(), stripped_rimewood_log=stripped_side(),
                     stripped_rimewood_log_top=rings(True), rimewood_planks=planks(), rimewood_leaves=leaves(),
                     rimewood_sapling=sapling(), rimewood_door_top=door(True), rimewood_door_bottom=door(False),
                     rimewood_trapdoor=trapdoor(), frostcap_top=cap_top(), frostcap_side=cap_side(), frostcap_stem=stem()).items():
    save(im, f'block/{name}.png')
for name, im in dict(rimewood_door=door_item(), rimewood_sign=sign_item(), rimewood_hanging_sign=sign_item(True),
                     frozen_frostcap=frostcap_item('frozen'), thawed_frostcap=frostcap_item('thawed'), frostcap_mush=frostcap_item('mush'),
                     dried_frostcap=frostcap_item('dried'), frostcap_stew=stew(), rimewood_resin=resin(), sulfur_igniter=igniter(),
                     thermal_binder=binder()).items():
    save(im, f'item/{name}.png')
save(sign_sheet(), 'entity/signs/rimewood.png')
save(sign_sheet(True), 'entity/signs/hanging/rimewood.png')
save(gui_hanging(), 'gui/hanging_signs/rimewood.png')

# Block models and blockstates, following the vanilla wood family templates.
P_ = rl('rimewood_planks')
models = {
    'rimewood_log': dict(parent='minecraft:block/cube_column', textures=dict(end=rl('rimewood_log_top'), side=rl('rimewood_log'))),
    'rimewood_log_horizontal': dict(parent='minecraft:block/cube_column_horizontal', textures=dict(end=rl('rimewood_log_top'), side=rl('rimewood_log'))),
    'rimewood_wood': dict(parent='minecraft:block/cube_column', textures=dict(end=rl('rimewood_log'), side=rl('rimewood_log'))),
    'stripped_rimewood_log': dict(parent='minecraft:block/cube_column', textures=dict(end=rl('stripped_rimewood_log_top'), side=rl('stripped_rimewood_log'))),
    'stripped_rimewood_log_horizontal': dict(parent='minecraft:block/cube_column_horizontal', textures=dict(end=rl('stripped_rimewood_log_top'), side=rl('stripped_rimewood_log'))),
    'stripped_rimewood_wood': dict(parent='minecraft:block/cube_column', textures=dict(end=rl('stripped_rimewood_log'), side=rl('stripped_rimewood_log'))),
    'rimewood_planks': dict(parent='minecraft:block/cube_all', textures=dict(all=P_)),
    'rimewood_leaves': dict(parent='minecraft:block/leaves', render_type='minecraft:cutout_mipped', textures=dict(all=rl('rimewood_leaves'))),
    'rimewood_sapling': dict(parent='minecraft:block/cross', render_type='minecraft:cutout', textures=dict(cross=rl('rimewood_sapling'))),
    'potted_rimewood_sapling': dict(parent='minecraft:block/flower_pot_cross', render_type='minecraft:cutout', textures=dict(plant=rl('rimewood_sapling'))),
    'rimewood_sign': dict(textures=dict(particle=P_)),
    'rimewood_hanging_sign': dict(textures=dict(particle=rl('stripped_rimewood_log'))),
}
for suffix, parent in (('', 'stairs'), ('_inner', 'inner_stairs'), ('_outer', 'outer_stairs')):
    models[f'rimewood_stairs{suffix}'] = dict(parent=f'minecraft:block/{parent}', textures=dict(bottom=P_, top=P_, side=P_))
models['rimewood_slab'] = dict(parent='minecraft:block/slab', textures=dict(bottom=P_, top=P_, side=P_))
models['rimewood_slab_top'] = dict(parent='minecraft:block/slab_top', textures=dict(bottom=P_, top=P_, side=P_))
for part in ('post', 'side', 'inventory'):
    models[f'rimewood_fence_{part}'] = dict(parent=f'minecraft:block/fence_{part}', textures=dict(texture=P_))
for part in ('', '_open', '_wall', '_wall_open'):
    models[f'rimewood_fence_gate{part}'] = dict(parent=f'minecraft:block/template_fence_gate{part}', textures=dict(texture=P_))
for half in ('bottom', 'top'):
    for hinge in ('left', 'right'):
        for opened in ('', '_open'):
            models[f'rimewood_door_{half}_{hinge}{opened}'] = dict(parent=f'minecraft:block/door_{half}_{hinge}{opened}', render_type='minecraft:cutout',
                                                                    textures=dict(bottom=rl('rimewood_door_bottom'), top=rl('rimewood_door_top')))
for part in ('bottom', 'top', 'open'):
    models[f'rimewood_trapdoor_{part}'] = dict(parent=f'minecraft:block/template_orientable_trapdoor_{part}', render_type='minecraft:cutout', textures=dict(texture=rl('rimewood_trapdoor')))
for part, parent in (('', 'button'), ('_pressed', 'button_pressed'), ('_inventory', 'button_inventory')):
    models[f'rimewood_button{part}'] = dict(parent=f'minecraft:block/{parent}', textures=dict(texture=P_))
models['rimewood_pressure_plate'] = dict(parent='minecraft:block/pressure_plate_up', textures=dict(texture=P_))
models['rimewood_pressure_plate_down'] = dict(parent='minecraft:block/pressure_plate_down', textures=dict(texture=P_))


def cap(x, z, w, h, y0=0):
    """One mushroom: a stem and a domed cap of width w centred at (x, z)."""
    s = max(1, w // 4)
    cx0, cz0 = x - s / 2, z - s / 2
    top = y0 + h
    return [dict(from_=[cx0, y0, cz0], to=[cx0 + s, top - 1, cz0 + s], faces={f: dict(texture='#stem') for f in ('north', 'south', 'east', 'west')}),
            dict(from_=[x - w / 2, top - 1, z - w / 2], to=[x + w / 2, top + 1, z + w / 2],
                 faces=dict(up=dict(texture='#top'), down=dict(texture='#side', uv=[0, 8, 16, 10]),
                            **{f: dict(texture='#side', uv=[0, 0, 16, 2]) for f in ('north', 'south', 'east', 'west')}))]


def fix(elements):
    for e in elements: e['from'] = e.pop('from_')
    return elements


cap_textures = dict(top=rl('frostcap_top'), side=rl('frostcap_side'), stem=rl('frostcap_stem'), particle=rl('frostcap_top'))
clusters = {0: [(8, 8, 4, 3)], 1: [(7, 7, 5, 4), (11, 10, 3, 2)], 2: [(7, 7, 6, 5), (11, 11, 4, 3), (4, 11, 3, 2)]}
for age, caps_ in clusters.items():
    models[f'frostcap_{age}'] = dict(parent='minecraft:block/block', render_type='minecraft:cutout', ambientocclusion=False, textures=cap_textures,
                                     elements=fix([e for c in caps_ for e in cap(*c)]))
    # Shelf: flat brackets stacked up the side of a log, drawn facing north (attached to the block to the south).
    shelves = [(8, 6, (6, 8, 10)[age])] + ([(5, 10, 5)] if age >= 1 else []) + ([(11, 2, 4)] if age >= 2 else [])
    els = []
    for x, y, w in shelves:
        depth = w // 2 + 1
        els.append(dict(from_=[x - w / 2, y, 16 - depth], to=[x + w / 2, y + 2, 16],
                        faces=dict(up=dict(texture='#top'), down=dict(texture='#side', uv=[0, 8, 16, 10]),
                                   **{f: dict(texture='#side', uv=[0, 0, 16, 2]) for f in ('north', 'east', 'west')})))
    models[f'frostcap_shelf_{age}'] = dict(parent='minecraft:block/block', render_type='minecraft:cutout', ambientocclusion=False, textures=cap_textures, elements=fix(els))
for name, m in models.items(): asset(f'models/block/{name}.json', m)


def axis_variants(base, horizontal):
    return {'axis=y': dict(model=rl(base)), 'axis=z': dict(model=rl(horizontal), x=90), 'axis=x': dict(model=rl(horizontal), x=90, y=90)}


states = {
    'rimewood_log': dict(variants=axis_variants('rimewood_log', 'rimewood_log_horizontal')),
    'stripped_rimewood_log': dict(variants=axis_variants('stripped_rimewood_log', 'stripped_rimewood_log_horizontal')),
    'rimewood_wood': dict(variants=axis_variants('rimewood_wood', 'rimewood_wood')),
    'stripped_rimewood_wood': dict(variants=axis_variants('stripped_rimewood_wood', 'stripped_rimewood_wood')),
}
for simple in ('rimewood_planks', 'rimewood_leaves', 'rimewood_sapling', 'potted_rimewood_sapling'):
    states[simple] = dict(variants={'': dict(model=rl(simple))})
for sign in ('rimewood_sign', 'rimewood_wall_sign'):
    states[sign] = dict(variants={'': dict(model=rl('rimewood_sign'))})
for sign in ('rimewood_hanging_sign', 'rimewood_wall_hanging_sign'):
    states[sign] = dict(variants={'': dict(model=rl('rimewood_hanging_sign'))})
states['rimewood_slab'] = dict(variants={'type=bottom': dict(model=rl('rimewood_slab')), 'type=top': dict(model=rl('rimewood_slab_top')),
                                         'type=double': dict(model=rl('rimewood_planks'))})
states['rimewood_pressure_plate'] = dict(variants={'powered=false': dict(model=rl('rimewood_pressure_plate')),
                                                   'powered=true': dict(model=rl('rimewood_pressure_plate_down'))})
# Stairs: vanilla rotation table.
stairs = {}
yrot = dict(east=0, south=90, west=180, north=270)
for facing in ('east', 'west', 'south', 'north'):
    for half in ('bottom', 'top'):
        for shape in ('straight', 'inner_left', 'inner_right', 'outer_left', 'outer_right'):
            model = 'rimewood_stairs' + ('_inner' if shape.startswith('inner') else '_outer' if shape.startswith('outer') else '')
            y = yrot[facing]
            if half == 'bottom' and shape.endswith('left'): y -= 90
            if half == 'top' and shape.endswith('right'): y += 90
            v = dict(model=rl(model))
            if half == 'top': v['x'] = 180
            if y % 360: v['y'] = y % 360
            if half == 'top' or shape != 'straight' or y % 360: v['uvlock'] = True
            stairs[f'facing={facing},half={half},shape={shape}'] = v
states['rimewood_stairs'] = dict(variants=stairs)
states['rimewood_fence'] = dict(multipart=[dict(apply=dict(model=rl('rimewood_fence_post')))] + [
    dict(when={d: 'true'}, apply=dict(model=rl('rimewood_fence_side'), uvlock=True, **({'y': r} if r else {}))) for d, r in (('north', 0), ('east', 90), ('south', 180), ('west', 270))])
gate = {}
for facing, y in (('south', 0), ('west', 90), ('north', 180), ('east', 270)):
    for in_wall in ('false', 'true'):
        for opened in ('false', 'true'):
            model = 'rimewood_fence_gate' + ('_wall' if in_wall == 'true' else '') + ('_open' if opened == 'true' else '')
            gate[f'facing={facing},in_wall={in_wall},open={opened}'] = dict(model=rl(model), uvlock=True, **({'y': y} if y else {}))
states['rimewood_fence_gate'] = dict(variants=gate)
door_states = {}
dy = dict(east=0, south=90, west=180, north=270)
for facing in ('east', 'south', 'west', 'north'):
    for half in ('lower', 'upper'):
        for hinge in ('left', 'right'):
            for opened in ('false', 'true'):
                # Vanilla door rule: an open door swings a quarter turn toward its hinge.
                y = (dy[facing] + (0 if opened == 'false' else 90 if hinge == 'left' else 270)) % 360
                model = f"rimewood_door_{'bottom' if half == 'lower' else 'top'}_{hinge}{'_open' if opened == 'true' else ''}"
                door_states[f'facing={facing},half={half},hinge={hinge},open={opened}'] = dict(model=rl(model), **({'y': y} if y else {}))
states['rimewood_door'] = dict(variants=door_states)
trap = {}
for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
    for half in ('bottom', 'top'):
        for opened in ('false', 'true'):
            model = 'rimewood_trapdoor_' + ('open' if opened == 'true' else half)
            v = dict(model=rl(model))
            if opened == 'true' and half == 'top': v['x'] = 180; y2 = (y + 180) % 360
            else: y2 = y
            if y2: v['y'] = y2
            trap[f'facing={facing},half={half},open={opened}'] = v
states['rimewood_trapdoor'] = dict(variants=trap)
button = {}
for face, x in (('floor', 0), ('wall', 90), ('ceiling', 180)):
    for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for powered in ('false', 'true'):
            yy = (y + 180) % 360 if face == 'ceiling' else y
            v = dict(model=rl('rimewood_button' + ('_pressed' if powered == 'true' else '')))
            if x: v['x'] = x
            if yy: v['y'] = yy
            if face == 'wall': v['uvlock'] = True
            button[f'face={face},facing={facing},powered={powered}'] = v
states['rimewood_button'] = dict(variants=button)
cap_states = {}
for age in range(3):
    cap_states[f'age={age},facing=up'] = dict(model=rl(f'frostcap_{age}'))
    for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        cap_states[f'age={age},facing={facing}'] = dict(model=rl(f'frostcap_shelf_{age}'), **({'y': y} if y else {}))
states['frostcap'] = dict(variants=cap_states)
for name, st in states.items(): asset(f'blockstates/{name}.json', st)

# Item models.
block_items = {'rimewood_log': 'rimewood_log', 'rimewood_wood': 'rimewood_wood', 'stripped_rimewood_log': 'stripped_rimewood_log',
               'stripped_rimewood_wood': 'stripped_rimewood_wood', 'rimewood_planks': 'rimewood_planks', 'rimewood_stairs': 'rimewood_stairs',
               'rimewood_slab': 'rimewood_slab', 'rimewood_fence': 'rimewood_fence_inventory', 'rimewood_fence_gate': 'rimewood_fence_gate',
               'rimewood_trapdoor': 'rimewood_trapdoor_bottom', 'rimewood_button': 'rimewood_button_inventory',
               'rimewood_pressure_plate': 'rimewood_pressure_plate', 'rimewood_leaves': 'rimewood_leaves'}
for item, model in block_items.items(): asset(f'models/item/{item}.json', dict(parent=rl(model)))
for item, tex in dict(rimewood_sapling='block/rimewood_sapling', rimewood_door='item/rimewood_door', rimewood_sign='item/rimewood_sign',
                      rimewood_hanging_sign='item/rimewood_hanging_sign', frozen_frostcap='item/frozen_frostcap', thawed_frostcap='item/thawed_frostcap',
                      frostcap_mush='item/frostcap_mush', frostcap_stew='item/frostcap_stew', dried_frostcap='item/dried_frostcap',
                      rimewood_resin='item/rimewood_resin', sulfur_igniter='item/sulfur_igniter', thermal_binder='item/thermal_binder').items():
    asset(f'models/item/{item}.json', dict(parent='minecraft:item/generated', textures=dict(layer0=f'{NS}:{tex}')))

# Loot tables.
SURVIVES = {'condition': 'minecraft:survives_explosion'}


def drop(name, item=None, extra=None):
    entry = dict(type='minecraft:item', name=f'{NS}:{item or name}')
    if extra: entry.update(extra)
    data(f'loot_table/blocks/{name}.json', dict(type='minecraft:block', pools=[dict(rolls=1, entries=[entry], conditions=[SURVIVES])], random_sequence=f'{NS}:blocks/{name}'))


for b in ('rimewood_log', 'rimewood_wood', 'stripped_rimewood_log', 'stripped_rimewood_wood', 'rimewood_planks', 'rimewood_stairs', 'rimewood_fence',
          'rimewood_fence_gate', 'rimewood_trapdoor', 'rimewood_button', 'rimewood_pressure_plate', 'rimewood_sign', 'rimewood_hanging_sign', 'rimewood_sapling'):
    drop(b)
drop('rimewood_slab', extra=dict(functions=[dict(function='minecraft:set_count', count=2, add=False,
                                                 conditions=[dict(condition='minecraft:block_state_property', block=f'{NS}:rimewood_slab', properties=dict(type='double'))]),
                                            dict(function='minecraft:explosion_decay')]))
data('loot_table/blocks/rimewood_door.json', dict(type='minecraft:block', random_sequence=f'{NS}:blocks/rimewood_door', pools=[dict(rolls=1, conditions=[SURVIVES], entries=[dict(
    type='minecraft:item', name=f'{NS}:rimewood_door', conditions=[dict(condition='minecraft:block_state_property', block=f'{NS}:rimewood_door', properties=dict(half='lower'))])])]))
data('loot_table/blocks/potted_rimewood_sapling.json', dict(type='minecraft:block', random_sequence=f'{NS}:blocks/potted_rimewood_sapling', pools=[
    dict(rolls=1, conditions=[SURVIVES], entries=[dict(type='minecraft:item', name='minecraft:flower_pot')]),
    dict(rolls=1, conditions=[SURVIVES], entries=[dict(type='minecraft:item', name=f'{NS}:rimewood_sapling')])]))
SHEARS_OR_SILK = {'condition': 'minecraft:any_of', 'terms': [
    {'condition': 'minecraft:match_tool', 'predicate': {'items': 'minecraft:shears'}},
    {'condition': 'minecraft:match_tool', 'predicate': {'predicates': {'minecraft:enchantments': [{'enchantments': 'minecraft:silk_touch', 'levels': {'min': 1}}]}}}]}
# About 48 leaves per tree: these chances average roughly two saplings a tree, and Fortune raises them.
data('loot_table/blocks/rimewood_leaves.json', dict(type='minecraft:block', random_sequence=f'{NS}:blocks/rimewood_leaves', pools=[
    dict(rolls=1, entries=[dict(type='minecraft:alternatives', children=[
        dict(type='minecraft:item', name=f'{NS}:rimewood_leaves', conditions=[SHEARS_OR_SILK]),
        dict(type='minecraft:item', name=f'{NS}:rimewood_sapling', conditions=[SURVIVES, dict(condition='minecraft:table_bonus', enchantment='minecraft:fortune', chances=[0.04, 0.05, 0.0625, 0.083])])])]),
    dict(rolls=1, conditions=[{'condition': 'minecraft:inverted', 'term': SHEARS_OR_SILK}], entries=[dict(type='minecraft:item', name='minecraft:stick', conditions=[
        dict(condition='minecraft:table_bonus', enchantment='minecraft:fortune', chances=[0.02, 0.022222223, 0.025, 0.033333335, 0.1])],
        functions=[dict(function='minecraft:set_count', count=dict(type='minecraft:uniform', min=1, max=2), add=False), dict(function='minecraft:explosion_decay')])])]))
# Ripe Frostcap gives two or three caps; younger ones give one.
data('loot_table/blocks/frostcap.json', dict(type='minecraft:block', random_sequence=f'{NS}:blocks/frostcap', pools=[dict(rolls=1, conditions=[SURVIVES], entries=[dict(
    type='minecraft:item', name=f'{NS}:frozen_frostcap', functions=[
        dict(function='minecraft:set_count', count=dict(type='minecraft:uniform', min=2, max=3), add=False,
             conditions=[dict(condition='minecraft:block_state_property', block=f'{NS}:frostcap', properties=dict(age='2'))])])])]))

# Recipes.
def item(i): return {'item': i if ':' in i else f'{NS}:{i}'}
def tag(t): return {'tag': t}
def shaped(name, pattern, key, result, count=1, group=None):
    r = dict(type='minecraft:crafting_shaped', category='building', pattern=pattern, key=key, result=dict(id=f'{NS}:{result}', count=count))
    if group: r['group'] = group
    data(f'recipe/{name}.json', r)
def shapeless(name, ingredients, result, count=1, category='misc'):
    data(f'recipe/{name}.json', dict(type='minecraft:crafting_shapeless', category=category, ingredients=ingredients, result=dict(id=f'{NS}:{result}', count=count)))


PL = item('rimewood_planks')
shapeless('rimewood_planks', [tag(f'{NS}:rimewood_logs')], 'rimewood_planks', 4, 'building')
shaped('rimewood_wood', ['##', '##'], {'#': item('rimewood_log')}, 'rimewood_wood', 3)
shaped('stripped_rimewood_wood', ['##', '##'], {'#': item('stripped_rimewood_log')}, 'stripped_rimewood_wood', 3)
shaped('rimewood_stairs', ['#  ', '## ', '###'], {'#': PL}, 'rimewood_stairs', 4, 'wooden_stairs')
shaped('rimewood_slab', ['###'], {'#': PL}, 'rimewood_slab', 6, 'wooden_slab')
shaped('rimewood_fence', ['W#W', 'W#W'], {'W': PL, '#': item('minecraft:stick')}, 'rimewood_fence', 3, 'wooden_fence')
shaped('rimewood_fence_gate', ['#W#', '#W#'], {'W': PL, '#': item('minecraft:stick')}, 'rimewood_fence_gate', 1, 'wooden_fence_gate')
shaped('rimewood_door', ['##', '##', '##'], {'#': PL}, 'rimewood_door', 3, 'wooden_door')
shaped('rimewood_trapdoor', ['###', '###'], {'#': PL}, 'rimewood_trapdoor', 2, 'wooden_trapdoor')
shapeless('rimewood_button', [PL], 'rimewood_button', 1, 'redstone')
shaped('rimewood_pressure_plate', ['##'], {'#': PL}, 'rimewood_pressure_plate', 1, 'wooden_pressure_plate')
shaped('rimewood_sign', ['###', '###', ' X '], {'#': PL, 'X': item('minecraft:stick')}, 'rimewood_sign', 3, 'wooden_sign')
shaped('rimewood_hanging_sign', ['X X', '###', '###'], {'#': item('stripped_rimewood_log'), 'X': item('minecraft:chain')}, 'rimewood_hanging_sign', 6, 'hanging_sign')
shapeless('frostcap_stew', [tag(f'{NS}:frostcaps')] * 3 + [item('minecraft:bowl')], 'frostcap_stew', 1, 'misc')
for kind, t in (('smelting', 200), ('smoking', 100), ('campfire_cooking', 600)):
    data(f'recipe/dried_frostcap_from_{kind}.json', dict(type=f'minecraft:{kind}', category='food', ingredient=tag(f'{NS}:frostcaps'),
                                                         result=dict(id=f'{NS}:dried_frostcap'), experience=0.1, cookingtime=t))
shapeless('sulfur_igniter', [item('sulfur')] * 4 + [item('minecraft:coal')], 'sulfur_igniter')
shapeless('thermal_binder', [item('sulfur')] * 2 + [item('rimewood_resin')] * 2, 'thermal_binder')

# Tags.
def tagfile(ns, kind, name, values):
    """Adds values to a tag file, keeping whatever other features already put there."""
    path = DATA / ns / 'tags' / kind / f'{name}.json'
    existing = json.loads(path.read_text())['values'] if path.exists() else []
    write(path, dict(replace=False, values=existing + [v for v in values if v not in existing]))


LOGS = [f'{NS}:{b}' for b in ('rimewood_log', 'rimewood_wood', 'stripped_rimewood_log', 'stripped_rimewood_wood')]
for kind in ('block', 'item'):
    tagfile(NS, kind, 'rimewood_logs', LOGS)
    for t, v in dict(logs_that_burn=[f'#{NS}:rimewood_logs'], planks=[f'{NS}:rimewood_planks'], wooden_stairs=[f'{NS}:rimewood_stairs'],
                     wooden_slabs=[f'{NS}:rimewood_slab'], wooden_fences=[f'{NS}:rimewood_fence'], fence_gates=[f'{NS}:rimewood_fence_gate'],
                     wooden_doors=[f'{NS}:rimewood_door'], wooden_trapdoors=[f'{NS}:rimewood_trapdoor'], wooden_buttons=[f'{NS}:rimewood_button'],
                     wooden_pressure_plates=[f'{NS}:rimewood_pressure_plate'], leaves=[f'{NS}:rimewood_leaves'], saplings=[f'{NS}:rimewood_sapling']).items():
        tagfile('minecraft', kind, t, v)
tagfile('minecraft', 'item', 'signs', [f'{NS}:rimewood_sign'])
tagfile('minecraft', 'item', 'hanging_signs', [f'{NS}:rimewood_hanging_sign'])
for t, v in dict(standing_signs=[f'{NS}:rimewood_sign'], wall_signs=[f'{NS}:rimewood_wall_sign'], ceiling_hanging_signs=[f'{NS}:rimewood_hanging_sign'],
                 wall_hanging_signs=[f'{NS}:rimewood_wall_hanging_sign'], flower_pots=[f'{NS}:potted_rimewood_sapling'],
                 overworld_natural_logs=[f'{NS}:rimewood_log']).items():
    tagfile('minecraft', 'block', t, v)
tagfile('minecraft', 'block', 'mineable/axe', LOGS + [f'{NS}:{b}' for b in ('rimewood_planks', 'rimewood_stairs', 'rimewood_slab', 'rimewood_fence', 'rimewood_fence_gate',
        'rimewood_door', 'rimewood_trapdoor', 'rimewood_button', 'rimewood_pressure_plate', 'rimewood_sign', 'rimewood_wall_sign', 'rimewood_hanging_sign',
        'rimewood_wall_hanging_sign')])
tagfile('minecraft', 'block', 'mineable/hoe', [f'{NS}:rimewood_leaves'])
tagfile(NS, 'block', 'cold_flora', [f'#{NS}:rimewood_logs', f'{NS}:rimewood_leaves', f'{NS}:rimewood_sapling', f'{NS}:frostcap'])
tagfile(NS, 'block', 'frostcap_grows_on', ['minecraft:snow_block', 'minecraft:ice', 'minecraft:packed_ice', 'minecraft:blue_ice', f'#{NS}:rimewood_logs'])
tagfile(NS, 'item', 'frostcaps', [f'{NS}:frozen_frostcap', f'{NS}:thawed_frostcap'])
# Frostcaps spoil by their own thaw chain, and dried caps keep: the general food frost leaves them alone.
tagfile(NS, 'item', 'frost_resistant', [f'{NS}:frozen_frostcap', f'{NS}:thawed_frostcap', f'{NS}:frostcap_mush', f'{NS}:dried_frostcap'])
tagfile(NS, 'item', 'igniter', ['minecraft:blaze_powder', f'{NS}:sulfur_igniter'])
tagfile(NS, 'item', 'thermal_binder', ['minecraft:magma_cream', f'{NS}:thermal_binder'])
tagfile(NS, 'worldgen/biome', 'has_rimewood_grove', ['minecraft:snowy_plains', 'minecraft:snowy_taiga', 'minecraft:ice_spikes', 'minecraft:grove', 'minecraft:snowy_slopes'])

# Worldgen: configured features, the placed grove (about one chunk in 32 of the cold biomes) and the biome modifier.
for f in ('rimewood_tree', 'rimewood_grove'):
    data(f'worldgen/configured_feature/{f}.json', dict(type=f'{NS}:{f}', config={}))
data('worldgen/placed_feature/rimewood_grove.json', dict(feature=f'{NS}:rimewood_grove', placement=[
    dict(type='minecraft:rarity_filter', chance=32), dict(type='minecraft:in_square'),
    dict(type='minecraft:heightmap', heightmap='WORLD_SURFACE_WG'), dict(type='minecraft:biome')]))
data('neoforge/biome_modifier/rimewood_grove.json', {'type': 'neoforge:add_features', 'biomes': f'#{NS}:has_rimewood_grove',
                                                     'features': f'{NS}:rimewood_grove', 'step': 'vegetal_decoration'})

# Hidden advancement: eat Frostcap Mush.
data('advancement/fungus_among_us.json', dict(parent=f'{NS}:root', display=dict(
    icon=dict(id=f'{NS}:frostcap_mush'), title=dict(translate=f'advancement.{NS}.fungus_among_us.title'),
    description=dict(translate=f'advancement.{NS}.fungus_among_us.description'), frame='task', show_toast=True, announce_to_chat=False, hidden=True),
    criteria=dict(ate_mush=dict(trigger='minecraft:consume_item', conditions=dict(item=dict(items=f'{NS}:frostcap_mush'))))))

(HERE / 'manifest.json').write_text(json.dumps(dict(created='2026-10-18', creator='Frozen Dawn; authored in code', script='make_cold_flora.py',
                                                    third_party_pixels=False, textures=manifest), indent=2) + '\n')
sheet = Image.new('RGBA', (8 * 104, ((len(manifest) + 7) // 8) * 104), (40, 44, 48, 255))
for i, e in enumerate(manifest):
    t = Image.open(ROOT / e['path']).convert('RGBA')
    t = t.resize((96, 96 * t.height // t.width), Image.NEAREST)
    sheet.alpha_composite(t, ((i % 8) * 104, (i // 8) * 104))
sheet.save(HERE / 'contact-sheet.png')
print(f'{len(manifest)} textures, {len(models)} block models, {len(states)} blockstates written')

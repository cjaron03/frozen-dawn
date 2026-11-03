#!/usr/bin/env python3
"""The ORSA LC-4 Sever Lash 3D item models, from the approved mockup (docs/art/sever-lash/make_model_mockup.py).

One model unit is one mockup pixel, so the Lash is one block long. Model space: the lens faces north (z 0), the Power
Cell port is at the back (z 16), y is up and x runs across. Each heat, firing and vent state is its own model, chosen by
the overrides in sever_lash.json; the textures come from docs/art/sever-lash/make_textures.py. Run from anywhere:
python3 tools/build_sever_lash_models.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODELS = ROOT / 'src/main/resources/assets/frozendawn/models/item'
TEX = 'frozendawn:item/sever_lash/'
FULLBRIGHT = {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}


def mockup(x0, y0, z0, x1, y1, z1):
    """A mockup box (x back to front, z across) in model space."""
    return [z0 + .5, y0, 16 - x1], [z1 + .5, y1, 16 - x0]


def faces(lo, hi, texture, down=None, only=None):
    w, h, d = hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2]
    # Sides show the tile's top rows, so the highlight runs along each top edge; tops read its middle and bottoms its
    # foot. Vanilla does not clamp UVs, so every face stays inside its 16x16 tile.
    uv = dict(north=[0, 0, w, h], south=[0, 0, w, h], east=[0, 0, d, h], west=[0, 0, d, h],
              up=[0, 4, w, 4 + d], down=[0, 16 - d, w, 16])
    return {f: {'texture': '#' + (down if f == 'down' and down else texture), 'uv': uv[f]}
            for f in (only or uv)}


def box(name, lo, hi, texture, down=None, glow=False, rotation=None, only=None):
    element = {'name': name, 'from': lo, 'to': hi, 'faces': faces(lo, hi, texture, down, only)}
    if rotation:
        element['rotation'] = rotation
    if glow:
        element['shade'] = False
        element['neoforge_data'] = dict(FULLBRIGHT)
    return element


FINS = (3, 5, 7)  # mockup x of each fin

# name: fin, fin tip, barrel tip, lens, vent slot (None = closed), fin angle, hot (fullbright fins and barrel tip)
STATES = {
    'cool': ('fin_steel', 'fin_pale', 'steel', 'lens', None, 0, False),
    'warm': ('fin_steel', 'fin_dull', 'fin_dull', 'lens', None, 0, False),
    'hot': ('fin_red', 'fin_orange', 'fin_orange', 'lens_hot', None, 0, True),
    'vent_1': ('fin_amber', 'fin_white', 'fin_orange', 'lens_white', 'slot_white', 22.5, True),
    'vent_2': ('fin_amber', 'fin_white', 'fin_orange', 'lens_white', 'slot_white', 45, True),
    'vent_3': ('fin_red', 'fin_orange', 'fin_dull', 'lens_hot', 'slot_orange', 45, True),
    'vent_4': ('fin_dull', 'fin_red', 'fin_dull', 'lens', 'slot_red', 45, True),
    'vent_5': ('fin_dull', 'fin_red', 'fin_dull', 'lens', 'slot_red', 22.5, True),
    'vent_6': ('fin_dull', 'fin_red', 'fin_dull', 'lens', None, 0, True),
}

# Display transforms, solved from the third-person chain (ItemInHandLayer, then the arm's ModelPart rotation, then
# this transform) rather than read off screenshots. Held, the vanilla item pose tilts the arm 18 degrees up, so the
# Lash turns 72 degrees about x to sit level and upright with its grip in the fist, the body just above the hand and
# the lens forward. Firing, the arm points along the aim but is yawed 0.3 rad in toward the off hand (SeverLashHold),
# so the Lash turns back by that much to point where the player looks. First person sits big and low right with the
# lens toward the crosshair, as the owner liked it.
DISPLAY = {
    'thirdperson_righthand': {'rotation': [72, 0, 0], 'translation': [-.4, 2.4, 2.9], 'scale': [.8, .8, .8]},
    'firstperson_righthand': {'rotation': [5, 5, 0], 'translation': [-1, 3, 0], 'scale': [.6, .6, .6]},
    'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [.8, .8, .8]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [.5, .5, .5]},
    'fixed': {'rotation': [0, -90, 0], 'translation': [0, 0, 0], 'scale': [.75, .75, .75]},
}
FIRING = {'rotation': [0, -17.2, 0], 'translation': [.7, 1.2, -2.6], 'scale': [.8, .8, .8]}


def elements(state):
    fin, tip, barrel_tip, lens, slot, angle, hot = STATES[state]
    out = [
        box('cap', *mockup(0, 7, 6, 2, 10, 9), 'cap'),
        box('body', *mockup(2, 7, 6, 11, 10, 9), 'steel', down='underside'),
        box('grip_upper', *mockup(3, 5, 7, 5, 7, 9), 'lead'),
        box('grip', *mockup(2, 2, 7, 4, 5, 9), 'lead'),
        box('guard', *mockup(5, 3, 6, 6, 7, 9), 'guard'),
        box('guard_foot', *mockup(2, 1, 6, 6, 2, 9), 'guard'),
        box('coil', *mockup(8, 6, 5, 9, 11, 10), 'copper'),
        box('coil_front', *mockup(10, 6, 5, 11, 11, 10), 'copper'),
        box('barrel', *mockup(11, 8, 7, 13, 10, 9), 'steel'),
        box('barrel_tip', *mockup(13, 8, 7, 15, 10, 9), 'barrel_tip', glow=hot),
        box('lens', *mockup(15, 8, 7, 16, 10, 9), 'lens', glow=True),
    ]
    for fx in FINS:
        # Each fin leans back about its foot: a positive turn about x tips its top toward the cell port.
        rotation = {'angle': angle, 'axis': 'x', 'origin': [8, 10, 16 - fx - .5]} if angle else None
        out.append(box('fin', *mockup(fx, 10, 6, fx + 1, 11, 9), 'fin', glow=hot, rotation=rotation))
        out.append(box('fin_tip', *mockup(fx, 11, 6, fx + 1, 12, 9), 'fin_tip', glow=hot, rotation=rotation))
        if slot:
            lo, hi = mockup(fx + 1, 10.01, 6, fx + 2, 10.01, 9)
            out.append(box('vent_slot', lo, hi, 'slot', glow=True, only=('up',)))
    return out


def model(state, firing=False):
    fin, tip, barrel_tip, lens, slot, angle, hot = STATES[state]
    textures = dict(particle=TEX + 'steel', steel=TEX + 'steel', underside=TEX + 'underside', cap=TEX + 'cap',
                    lead=TEX + 'lead', guard=TEX + 'guard', copper=TEX + 'copper', barrel_tip=TEX + barrel_tip,
                    lens=TEX + lens, fin=TEX + fin, fin_tip=TEX + tip)
    if slot:
        textures['slot'] = TEX + slot
    display = dict(DISPLAY)
    if firing:
        display['thirdperson_righthand'] = FIRING
    return {'textures': textures, 'display': display, 'elements': elements(state)}


def ref(name):
    return 'frozendawn:item/' + name


def main():
    written = {}
    for state in STATES:
        written['sever_lash' + ('' if state == 'cool' else '_' + state)] = model(state)
    for state in ('cool', 'warm', 'hot'):
        written['sever_lash_firing' + ('' if state == 'cool' else '_' + state)] = model(state, firing=True)
    # The last matching override wins, and a locked-out Lash is also at full heat: the vent states come last.
    written['sever_lash']['overrides'] = [
        {'predicate': {'frozendawn:heat': .5}, 'model': ref('sever_lash_warm')},
        {'predicate': {'frozendawn:heat': .8}, 'model': ref('sever_lash_hot')},
        {'predicate': {'frozendawn:firing': 1}, 'model': ref('sever_lash_firing')},
        {'predicate': {'frozendawn:firing': 1, 'frozendawn:heat': .5}, 'model': ref('sever_lash_firing_warm')},
        {'predicate': {'frozendawn:firing': 1, 'frozendawn:heat': .8}, 'model': ref('sever_lash_firing_hot')},
    ] + [{'predicate': {'frozendawn:vent': stage / 8}, 'model': ref(f'sever_lash_vent_{stage}')} for stage in range(1, 7)]
    for name, body in written.items():
        path = MODELS / f'{name}.json'
        path.write_text(json.dumps(body, indent=2) + '\n')
        print(path)


if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""The ORSA TR-0 Terminus 3D item models, direction C "Reliquary" (docs/art/terminus/make_texture_directions.py).

One model unit is one mockup pixel, so the wand is one block long. Model space: the emitter faces north (z 0), the
pommel is at the back (z 16), y is up and x runs across, the wand's axis at x 8, y 6.5. One model per discharges left
(motes in the glass) and, while winding up, per wind-up stage, chosen by the overrides in terminus.json; the textures
come from docs/art/terminus/make_textures.py. Run from anywhere: python3 tools/build_terminus_models.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODELS = ROOT / 'src/main/resources/assets/frozendawn/models/item'
TEX = 'frozendawn:item/terminus/'
FULLBRIGHT = {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}


def span(x0, x1, size):
    """A part from x0 to x1 along the wand (back to front), size units square about the axis."""
    h = size / 2
    return [8 - h, 6.5 - h, 16 - x1], [8 + h, 6.5 + h, 16 - x0]


def plain(lo, hi, texture):
    w, h, d = hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2]
    uv = dict(north=[0, 0, w, h], south=[0, 0, w, h], east=[0, 0, d, h], west=[0, 0, d, h],
              up=[0, 4, w, 4 + d], down=[0, 16 - d, w, 16])
    return {f: {'texture': '#' + texture, 'uv': uv[f]} for f in uv}


def glass_faces(lo, hi):
    """The caged shaft: each face reads its own region of the glass tile (see make_textures.py)."""
    d = hi[2] - lo[2]
    return {f: {'texture': '#glass', 'uv': uv} for f, uv in dict(
        east=[0, 0, d, 3], west=[0, 0, d, 3], up=[0, 4, 3, 4 + d], down=[0, 4, 3, 4 + d],
        north=[0, 12, 3, 15], south=[0, 12, 3, 15]).items()}


def core_faces():
    """The mote channel, indexed back (0) to front (5) on every long face, so a mote reads the same from either side."""
    return {f: {'texture': '#core', 'uv': uv} for f, uv in dict(
        east=[0, 0, 6, 1], west=[6, 0, 0, 1], up=[0, 8, 1, 2], down=[0, 2, 1, 8],
        north=[0, 9, 1, 10], south=[0, 9, 1, 10]).items()}


def element(name, lo, hi, faces, glow=False):
    out = {'name': name, 'from': lo, 'to': hi, 'faces': faces}
    if glow:
        out['shade'] = False
        out['neoforge_data'] = dict(FULLBRIGHT)
    return out


def elements(motes, stage):
    out = [
        element('pommel', *span(0, 1, 3), plain(*span(0, 1, 3), 'plate')),
        element('grip', *span(1, 6, 2), plain(*span(1, 6, 2), 'grip')),
        element('knurl', *span(2, 3, 3), plain(*span(2, 3, 3), 'knurl')),
        element('knurl', *span(4, 5, 3), plain(*span(4, 5, 3), 'knurl')),
        element('collar', *span(6, 7, 5), plain(*span(6, 7, 5), 'steel')),
        element('rim', *span(14, 15, 5), plain(*span(14, 15, 5), 'steel')),
        element('face', *span(15, 16, 3), plain(*span(15, 16, 3), 'face'), glow=motes > 0),
    ]
    # The motes are drawn before the glass, so the glass blends over them.
    if motes:
        out.append(element('core', *span(7.5, 13.5, 1), core_faces(), glow=True))
    out.append(element('glass', *span(7, 14, 3), glass_faces(*span(7, 14, 3))))
    return out


# Display transforms, from the Sever Lash's solved ones (tools/build_sever_lash_models.py): same rotations, so the
# wand sits level in the fist with its emitter forward, and winding up it points along the outstretched arm, which
# TerminusHold turns in as the Lash's grip arm. The translations move the wand's grip centre, (8, 6.5, 12.5), onto
# the Lash's, (8.5, 4, 13), through each transform's rotation and its 0.8 scale.
DISPLAY = {
    'thirdperson_righthand': {'rotation': [72, 0, 0], 'translation': [0, 1.4, 1.12], 'scale': [.8, .8, .8]},
    'firstperson_righthand': {'rotation': [5, 5, 0], 'translation': [-1, 3, 0], 'scale': [.65, .65, .65]},
    'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [.8, .8, .8]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [.5, .5, .5]},
    'fixed': {'rotation': [0, -90, 0], 'translation': [0, 0, 0], 'scale': [.75, .75, .75]},
}
WINDING = {'rotation': [0, -17.2, 0], 'translation': [.96, -.8, -2.1], 'scale': [.8, .8, .8]}


def model(motes, stage):
    textures = dict(particle=TEX + 'steel', steel=TEX + 'steel', plate=TEX + 'plate', grip=TEX + 'grip',
                    knurl=TEX + 'knurl', glass=TEX + f'glass_{stage}',
                    face=TEX + ('face_spent' if not motes else 'face' if not stage else f'face_w{stage}'))
    if motes:
        textures['core'] = TEX + (f'core_{motes}' if not stage else f'core_{motes}_w{stage}')
    display = dict(DISPLAY)
    if stage:
        display['thirdperson_righthand'] = WINDING
    return {'render_type': 'minecraft:translucent', 'textures': textures, 'display': display,
            'elements': elements(motes, stage)}


def name(motes, stage):
    return 'terminus' if motes == 0 else f'terminus_{motes}' + (f'_w{stage}' if stage else '')


def ref(n):
    return 'frozendawn:item/' + n


def main():
    written = {name(0, 0): model(0, 0)}
    for m in (1, 2, 3):
        written[name(m, 0)] = model(m, 0)
        for s in (1, 2, 3, 4):
            written[name(m, s)] = model(m, s)
    # The base model is the spent trophy. The last matching override wins, so the discharge counts come first and the
    # wind-up stages after, ordered by count and then stage.
    written['terminus']['overrides'] = [{'predicate': {'frozendawn:motes': m}, 'model': ref(name(m, 0))} for m in (1, 2, 3)] + [
        {'predicate': {'frozendawn:motes': m, 'frozendawn:windup': s}, 'model': ref(name(m, s))}
        for m in (1, 2, 3) for s in (1, 2, 3, 4)]
    for n, body in written.items():
        path = MODELS / f'{n}.json'
        path.write_text(json.dumps(body, indent=2) + '\n')
    print(f'{len(written)} models in {MODELS}')


if __name__ == '__main__':
    main()

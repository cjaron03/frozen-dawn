#!/usr/bin/env python3
"""Fresh, prelinked forge mode and heat lab. Never overwrite a save or copy gameplay."""
import argparse
import fcntl
import gzip
import io
import json
from pathlib import Path

import prepare_blind_architect_playtest as nbt
from prepare_hearthstone_playtest import as_long, reactor, RODS
from prepare_power_network_playtest import prepare, pack_only

TITLE = 'Acheron Forge Modes and Heat'
NS = 'acheron_forge_check'
PACK = 'acheron-forge-check'
GUARD = 'execute unless score #built forgecheck matches 1 run return 0'
ROOMS = [(0, 'Control - empty', None), (16, 'Induction - 0.5 kW', 'induction_furnace'),
         (32, 'Forge - 6.25 kW', 'acheron_forge'), (48, 'Forge with cooling', 'acheron_forge')]
REACTORS = [(-24, 66, -16), (-24, 66, 32)]


def tell(text, function=None, color='aqua'):
    value = {'text': text, 'color': color}
    if function:
        value['clickEvent'] = {'action': 'run_command', 'value': '/function ' + NS + ':' + function}
    return 'tellraw @s ' + json.dumps(value)


def block(pos, name, data=''):
    return 'setblock ' + ' '.join(map(str, pos)) + ' ' + name + data


def sign(x, y, z, lines):
    messages = ','.join("'" + json.dumps({'text': line}) + "'" for line in lines)
    return block((x, y, z), 'minecraft:oak_sign[rotation=8]',
                 '{front_text:{messages:[' + messages + ']},is_waxed:1b}')


def links():
    edges = []
    for rx, ry, rz in REACTORS:
        for row, dz in enumerate((-7, -4)):
            port = (rx - 1 if row == 0 else rx + 1, ry, rz)
            edges += [(port, (x, 65, rz + dz)) for x in range(rx - 6, rx + 6)]
    # Reactor A supplies the surface bench and underground mode station.
    edges += [((-24, 66, -15), (-16, 65, -8)), ((-16, 65, -8), (0, 65, 8)),
              ((0, 65, 8), (0, 65, 0)), ((0, 65, 8), (2, 66, 1))]
    heights = [65, 41, 17, -7, -31, -48]
    edges += [((0, a, 8), (0, b, 8)) for a, b in zip(heights, heights[1:])]
    edges += [((0, -48, 8), (0, -48, 0)), ((16, 65, 0), (20, 65, 0))]
    # Reactor B is a separate circuit; its casing/radiators cannot warm any sealed test room.
    edges += [((-24, 66, 33), (-12, 65, 24)), ((-12, 65, 24), (0, 65, 24))]
    edges += [((x, 65, 24), (x + 16, 65, 24)) for x in (0, 16, 32)]
    for x, _, machine in ROOMS:
        edges += [((x, 65, 24), (x + 2, 66, 31))]
        if machine:
            edges += [((x, 65, 24), (x, 65, 32))]
    for a, b in edges:
        assert sum((u - v) ** 2 for u, v in zip(a, b)) <= 32 ** 2
    return edges


def scripts():
    setup = ['execute unless score #built forgecheck matches -1 run return 0',
             'scoreboard players set #built forgecheck -2', 'gamemode creative @s',
             'gamerule doMobSpawning false', 'gamerule doDaylightCycle false',
             'gamerule doWeatherCycle false', 'gamerule doFireTick false',
             'gamerule keepInventory true', 'gamerule announceAdvancements false',
             'gamerule sendCommandFeedback false', 'gamerule commandBlockOutput false',
             'time set noon', 'weather clear']
    for x in range(-40, 57, 16):
        setup += [f'fill {x} 64 -28 {x+15} 64 46 minecraft:smooth_stone']
    for rx, ry, rz in REACTORS:
        # A warm, fuelled fixture avoids making the owner wait through a cold start.
        # All subsequent output and rod use are ordinary reactor physics.
        setup += reactor((rx, ry, rz), RODS[:-1] + ',Core:{CoreC:280d,Insertion:1d,AutoScram:1b,LoadFollow:0b}}')
        for dz in (-7, -4):
            setup += [f'fill {rx-6} 65 {rz+dz} {rx+5} 65 {rz+dz} frozendawn:reactor_radiator']
        setup += [sign(rx, 65, rz+5, ['CFU-7 ' + ('A - modes' if rz < 0 else 'B - heat'),
                                    '24 radiators', 'Warm test fixture', 'Auto SCRAM on'])]
    setup += [block((-16, 65, -8), 'frozendawn:large_power_relay')]
    for y in (65, 41, 17, -7, -31, -48):
        setup += [block((0, y, 8), 'frozendawn:large_power_relay')]
    setup += [block((0, 65, 0), 'frozendawn:acheron_forge'),
              block((2, 66, 2), 'minecraft:iron_block'),
              block((2, 66, 1), 'frozendawn:power_monitor[facing=north]'),
              block((16, 65, 0), 'frozendawn:cell_dock',
                    '{Items:[{Slot:0b,id:"frozendawn:power_cell",count:1,components:{"frozendawn:cell_joules":500000}}]}'),
              block((20, 65, 0), 'frozendawn:acheron_forge'),
              sign(-2, 65, 3, ['Surface electric', '25 kW or pause', 'Heat cannot run', 'Y = 65']),
              sign(17, 65, 3, ['Isolated Cell Dock', '500 kJ = 20 sec', 'No reactor link', 'Input starts work']),
              'fill -7 -49 -4 8 -49 12 minecraft:smooth_stone',
              'fill -7 -48 -4 8 -40 12 minecraft:air',
              block((0, -48, 8), 'frozendawn:large_power_relay'),
              block((0, -48, 0), 'frozendawn:acheron_forge'),
              block((4, -48, 0), 'frozendawn:geothermal_core', '{HeatShare:100,PowerShare:0,HeatBalance:1d}'),
              sign(-2, -48, 3, ['Underground modes', 'Core 4 blocks away', 'Grid from reactor A', 'Y = -48'])]
    for p in [(-12, 65, 24), *((x, 65, 24) for x in (0, 16, 32, 48))]:
        setup += [block(p, 'frozendawn:large_power_relay')]
    for x, title, machine in ROOMS:
        setup += [f'fill {x-4} 64 32 {x+4} 70 40 frozendawn:insulated_glass hollow',
                  # Panels are INSIDE a solid wall, so the thin panel does not create a leak.
                  block((x-2, 66, 33), 'frozendawn:thermostat[facing=south]', '{Target:20}'),
                  block((x+2, 66, 32), 'minecraft:iron_block'),
                  block((x+2, 66, 31), 'frozendawn:power_monitor[facing=north]'),
                  sign(x-2, 65, 30, [title, 'Matching sealed room', 'Thermostat inside', 'Start heat from chat'])]
        if machine:
            state = '[facing=north]' if machine == 'induction_furnace' else ''
            data = '{Mode:1}' if machine == 'acheron_forge' else ''
            setup += [block((x, 65, 32), 'frozendawn:' + machine + state, data)]
        if x == 48:
            setup += [block((x, 66, 40), 'frozendawn:heat_vent[facing=south]')]
    setup += ['item replace entity @s hotbar.0 with frozendawn:conduit_binder',
              'item replace entity @s hotbar.1 with frozendawn:acheronite_shard 64',
              'item replace entity @s hotbar.2 with frozendawn:spent_lattice 64',
              'item replace entity @s hotbar.3 with minecraft:raw_iron 64',
              'item replace entity @s hotbar.4 with frozendawn:power_cell[frozendawn:cell_joules=500000]',
              'item replace entity @s hotbar.5 with frozendawn:power_cell[frozendawn:cell_joules=50000]',
              'item replace entity @s hotbar.6 with frozendawn:power_cell[frozendawn:cell_joules=25000]',
              'effect give @s minecraft:night_vision infinite 0 true',
              'setworldspawn 4 65 8', 'spawnpoint @s 4 65 8', 'tp @s 4.5 65 8.5 180 12',
              'scoreboard players set #heat forgecheck 0', 'scoreboard players set #built forgecheck 1',
              'function '+NS+':controls', 'return 1']
    result = {
        'load': 'scoreboard objectives add forgecheck dummy\nexecute unless score #built forgecheck matches -2..1 run scoreboard players set #built forgecheck 0\nexecute if score #built forgecheck matches 1 as @a run function '+NS+':controls',
        'bootstrap': '\n'.join(['execute unless score #built forgecheck matches 0 run return 0',
                               'forceload add -48 -32 63 47', 'scoreboard players set #built forgecheck -1', 'return 1']),
        'tick': '\n'.join(['execute if score #built forgecheck matches 0 as @a[limit=1] run function '+NS+':bootstrap',
                          'execute if score #built forgecheck matches -1 if loaded -40 65 -28 if loaded 63 65 46 as @a[limit=1] run function '+NS+':setup']),
        'setup': '\n'.join(setup),
        'controls': '\n'.join([GUARD,
            tell('FORGE LAB - Creative / Default. Two real, prewarmed reactors; fixed early atmosphere for clear room-heat comparisons. Nothing is smelting yet.'),
            tell('[Surface forge + Cell Dock]', 'surface'), tell('[Underground Auto / Electric / Heat]', 'underground'),
            tell('[Heat room overview]', 'heat'), tell('[START heat comparison once]', 'start_heat', 'gold'),
            tell('[Empty control room]', 'room_control'), tell('[Induction room]', 'room_induction'),
            tell('[Forge room]', 'room_forge'), tell('[Cooled forge room]', 'room_cooled'),
            tell('[Reactor A: modes]', 'reactor_a'), tell('[Reactor B: heat]', 'reactor_b'),
            tell('[Read diagnostics]', 'read'), 'return 1']),
        'start_heat': '\n'.join([GUARD, 'execute unless score #heat forgecheck matches 0 run return 0',
            'scoreboard players set #heat forgecheck 1',
            'item replace block 16 65 32 container.0 with minecraft:raw_iron 64',
            'item replace block 32 65 32 container.0 with frozendawn:acheronite_shard 64',
            'item replace block 48 65 32 container.0 with frozendawn:acheronite_shard 64',
            tell('Heat comparison started together. Watch the thermostats over 1-3 minutes. Each forge has 32 operations; this is one use, not a reset. The cooling vent follows its room thermostat.'), 'return 1']),
        'read': '\n'.join([GUARD, 'fd world thermal', 'fd world oxygen',
            'data get block 0 65 0', 'data get block 0 -48 0', 'data get block 20 65 0',
            'data get block 16 65 0', 'data get block 16 65 32', 'data get block 32 65 32',
            'data get block 48 65 32', 'data get block -24 66 -15 Core', 'data get block -24 66 33 Core', 'return 1'])}
    views = {
        'surface': ((4.5,65,5.5), ['Surface forge uses reactor A. Insert 2 Shards or 4 Spent Lattice. Electric/Auto: 25 kW, about 6.7 sec; Heat pauses here.',
                    'The separate Cell Dock forge uses no reactor. Try the installed full cell, then the supplied 50 kJ and 25 kJ cells. Swap after depletion: partial work resumes.',
                    'Idle/full output should draw zero. Save/reopen to check mode, inventory, links and partial work.']),
        'underground': ((2.5,-48,4.5), ['Core at x4 provides nearby heat; it supplies no electricity. The tall relay chain brings reactor A power.',
                    'Auto prefers electric; Heat takes 10 sec without grid draw; Electric needs all 25 kW.',
                    'Shift-use Binder on the forge to clear its one grid link: Auto falls back, Electric pauses. Rebind nearby large relay at 0,-48,8 -> forge at 0,-48,0. Progress should stay.']),
        'heat': ((24.5,65,29.5), ['Four matching insulated-glass rooms, left to right: empty, Induction, Forge, Forge with automatic vent. Reactor B supplies only this circuit.',
                    'Use START once when ready. Enter rooms using the chat links; open the thermostat on the inside front wall. Teleports preserve the seal.',
                    'Compare temperature trends, room comfort and cooling. The start uses equal forge batches and ordinary 0.5 / 6.25 kW machine heat.']),
        'room_control': ((0.5,65,36.5), ['Empty control. Thermostat is inside front-left; no machine heat. Use chat links to leave without breaking glass.']),
        'room_induction': ((16.5,65,36.5), ['Induction reference: 0.5 kW waste heat while processing. Thermostat inside front-left; furnace in front wall.']),
        'room_forge': ((32.5,65,36.5), ['Forge heat room. Thermostat inside front-left; forge in front wall. Tell me how hot it feels before diagnostics.']),
        'room_cooled': ((48.5,65,36.5), ['Same forge, one outward-facing automatic vent in rear wall. Thermostat target starts at 20C; adjust normally.']),
        'reactor_a': ((-23.5,65,-12.5), ['Reactor A supplies the mode stations. Four rods, 24 real coolant-linked radiators, manual 100%, auto-SCRAM enabled. Prewarmed only for fixture readiness.',
                        'Use ordinary controller controls. Follow mode responds to smelting demand; output changes with thermal inertia. SCRAM also leaves real decay heat.']),
        'reactor_b': ((-23.5,65,35.5), ['Reactor B supplies the three heat-comparison machines. Its open platform and radiators are separate from every sealed test room. Leave it running during the temperature comparison.'])}
    for name, (position, lines) in views.items():
        result[name] = '\n'.join([GUARD, 'tp @s '+' '.join(map(str,position))+' 180 10',
                                  *(tell(line) for line in lines), tell('[Lab menu]', 'controls', 'gray'), 'return 1'])
    return result


def prepare_world(source, destination):
    with (source.parent/'session.lock').open('rb') as lock:
        fcntl.lockf(lock, fcntl.LOCK_SH | fcntl.LOCK_NB)
        prepare(source, destination, title=TITLE, namespace=NS, pack=PACK, build_scripts=scripts,
                surface=16, structures=False)
    stream = io.BytesIO(gzip.decompress((destination/'level.dat').read_bytes()))
    assert stream.read(1) == b'\x0a'
    root_name = nbt.string(stream)
    root = nbt.read(stream,10)
    data = root['Data'][1]
    data['DifficultyLocked'] = (1,1)
    data['confirmedExperimentalSettings'] = (1,1)
    data['DragonFight'] = (10,{})
    data['SpawnX'],data['SpawnY'],data['SpawnZ'] = (3,4),(3,65),(3,8)
    (destination/'level.dat').write_bytes(gzip.compress(b'\x0a'+nbt.utf(root_name)+nbt.write(10,root)))
    entries = [{'a':(4,as_long(*a)),'b':(4,as_long(*b))} for a,b in links()]
    network = {'data':(10,{'version':(3,1),'links':(9,(10,entries))}),'DataVersion':data['DataVersion']}
    (destination/'data/frozendawn_power_network.dat').write_bytes(gzip.compress(b'\x0a'+nbt.utf('')+nbt.write(10,network)))
    meta = json.loads((destination/'preparation.json').read_text())
    meta.update({'prelinked':len(entries),'reactors':'two real CFU-7; four rods and 24 radiators each; prewarmed 280C; manual full insertion; auto-SCRAM enabled',
                 'heat_comparison':'matching sealed geometry; one-use owner Start; room heat initially idle',
                 'phase':'fresh early atmosphere; fixed by this isolated lab config','DifficultyLocked':True})
    (destination/'preparation.json').write_text(json.dumps(meta,indent=2)+'\n')


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',type=Path);parser.add_argument('--destination',type=Path)
    parser.add_argument('--pack-only',type=Path)
    args=parser.parse_args()
    if args.pack_only:
        pack_only(args.pack_only,title=TITLE,namespace=NS,build_scripts=scripts)
    elif args.source and args.destination:
        prepare_world(args.source,args.destination)
    else:
        parser.error('Use --source/--destination or --pack-only')

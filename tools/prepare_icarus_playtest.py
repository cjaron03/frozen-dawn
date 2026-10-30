#!/usr/bin/env python3
"""Prepare a new save for the AMR-1 Icarus playtest.

The default check world is a creative superflat. The first player to join
faces north, and a complete Icarus is built ahead of them: cold,
uncommissioned, with a block of charged banks and matter in a chest, and no
capsules loaded. A sealed glass room stands beside the reactor and another
about 300 blocks south, with an open vacuum spot next to it; once both rooms
hold air the world moves to late phase 6, so everything outside is vacuum.
The witness spots suit a 2-capsule load (blast radius 126). A clickable chat
menu gives the kit, a battery recharge, teleports, an air reading, day and night, and game modes.

--terrain writes a check world on the template's own overworld terrain at phase 0, so the air is breathable: the
reactor is built on the surface near the origin on a cleared stone pad, and its chat menu offers witness spots out on
the land 180 and 300 blocks south.

--perf writes the disposable "Icarus Perf" world instead: the template's own
terrain at phase 0, for the lab-only full-load blast timed by LabIcarusPerf.
"""
import argparse
import copy
import fcntl
import gzip
import io
import json
from pathlib import Path
import time
from prepare_blind_architect_playtest import read, string, utf, write, lab_state, pack_metadata, write_sync_advancement

ROOT = Path(__file__).resolve().parents[1]
CHECK_TITLE, CHECK_NS = 'Icarus Check', 'icarus_lab'
PERF_TITLE, PERF_NS = 'Icarus Perf', 'icarus_perf'
NS = CHECK_NS


def button(label, function, color='aqua'):
    return {'text': '['+label+'] ', 'color': color,
            'clickEvent': {'action': 'run_command', 'value': '/function '+NS+':'+function}}


MENU = json.dumps(['', {'text': 'Icarus: ', 'color': 'gray'},
                   button('Kit', 'kit', 'green'), button('Recharge', 'recharge', 'green'), button('Reactor', 'reactor'), button('Near room', 'near_room'),
                   button('Air room', 'air_room'), button('Vacuum spot', 'vacuum_spot'), button('Air?', 'air', 'light_purple'),
                   button('Day', 'day'), button('Night', 'night'),
                   button('Survival', 'survival', 'yellow'), button('Creative', 'creative', 'yellow'),
                   {'text': '(far spots suit a 2-capsule load)', 'color': 'dark_gray'}])

# Yaw 180 faces north. The core lands at (0, -55, -16), the Controller at (-1, -60, -8) facing the player, the banks
# at x 10..15, z -14..-19, the matter chest at (-5, -60, -6). The near room's interior is x -14..-10, z -4..0; the
# far room's is x -8..-4, z 280..284, about 300 blocks from the core: past twice a 2-capsule radius, inside three times.
NEAR_ROOM = 'fill -15 -61 -5 -9 -56 1 minecraft:glass hollow'
FAR_ROOM = 'fill -9 -61 279 -3 -56 285 minecraft:glass hollow'
FAR_CHUNKS = '-9 279 4 285'

SCRIPTS = {
    'load': f'scoreboard objectives add {NS} dummy\nexecute unless score #stage {NS} matches 0.. run scoreboard players set #stage {NS} 0',
    'tick': f'execute if score #stage {NS} matches 0 as @a[limit=1] run function {NS}:prepare',
    'prepare': '\n'.join([
        f'scoreboard players set #stage {NS} 1',
        'frozendawn debug world set phase 0',
        'tp @s 0 -60 0 180 0',
        'spawnpoint @s 0 -60 0',
        'execute positioned 0 -60 0 rotated 180 0 run frozendawn debug world icaruslab',
        'execute if block -1 -60 -8 frozendawn:icarus_controller run say Icarus lab: reactor built',
        NEAR_ROOM,
        f'forceload add {FAR_CHUNKS}',
        f'schedule function {NS}:far_room 60t',
        f'function {NS}:kit',
        'datapack list enabled',
    ]),
    # Rooms seal while there is still air to trap; the phase moves to vacuum only after both stand.
    'far_room': '\n'.join([
        FAR_ROOM,
        'setblock 4 -61 282 minecraft:sea_lantern',
        f'forceload remove {FAR_CHUNKS}',
        'execute if block -3 -58 282 minecraft:glass if block -9 -58 282 minecraft:glass run say Icarus lab: far room built',
        'frozendawn debug world set phase 6 late',
        f'scoreboard players set #stage {NS} 2',
    ]),
    'kit': '\n'.join([
        'clear @s',
        'item replace entity @s armor.head with frozendawn:eva_helmet',
        'item replace entity @s armor.chest with frozendawn:eva_chestplate',
        'item replace entity @s armor.legs with frozendawn:eva_leggings',
        'item replace entity @s armor.feet with frozendawn:eva_boots',
        'item replace entity @s hotbar.0 with frozendawn:antimatter_capsule 16',
        'item replace entity @s hotbar.1 with minecraft:cobblestone 64',
        'item replace entity @s hotbar.2 with frozendawn:icarus_controller',
        'item replace entity @s hotbar.8 with frozendawn:o2_tank_mk3',
        f'function {NS}:menu',
    ]),
    # Refills the lab's banks, which a commissioning start and three cold starts would empty.
    'recharge': 'execute positioned -1 -60 -8 run frozendawn debug world icaruslab recharge\n' + f'function {NS}:menu',
    'reactor': 'tp @s 0 -60 0 180 0\n' + f'function {NS}:menu',
    'near_room': 'tp @s -12 -60 -2 180 0\n' + f'function {NS}:menu',
    'air_room': 'tp @s -6 -60 282 180 0\n' + f'function {NS}:menu',
    'vacuum_spot': 'tp @s 4 -60 282 180 0\n' + f'function {NS}:menu',
    'air': 'frozendawn debug world oxygen here',
    'day': 'time set 6000\n' + f'function {NS}:menu',
    'night': 'time set 18000\n' + f'function {NS}:menu',
    'survival': 'gamemode survival @s\n' + f'function {NS}:menu',
    'creative': 'gamemode creative @s\n' + f'function {NS}:menu',
    'menu': 'tellraw @s ' + MENU,
}

# On real terrain the pad's position is found at the first join and kept in storage; the reactor is built relative to it
# as in the flat world (Controller one west and eight north of the pad's origin).
ANCHOR, BACK = NS + ':anchor', NS + ':back'
PAD_CHUNKS = '~-18 ~-30 ~20 ~4'
# Before a spot teleport the player's block is kept, so Back returns them there (or to the reactor before any spot).
REMEMBER = '\n'.join(f'execute store result storage {BACK} {axis} int 1 run data get entity @s Pos[{i}]' for i, axis in enumerate('xyz'))
TERRAIN_MENU = json.dumps(['', {'text': 'Icarus: ', 'color': 'gray'},
                   button('Kit', 'kit', 'green'), button('Recharge', 'recharge', 'green'), button('Reactor', 'reactor'),
                   button('Edge 180', 'edge_spot'), button('Witness 300', 'witness_spot'), button('Back', 'back', 'gold'), button('Air?', 'air', 'light_purple'),
                   button('Day', 'day'), button('Night', 'night'),
                   button('Survival', 'survival', 'yellow'), button('Creative', 'creative', 'yellow'),
                   {'text': '(spots suit a 2-capsule load)', 'color': 'dark_gray'}])
TERRAIN_SCRIPTS = {
    'load': SCRIPTS['load'],
    'tick': SCRIPTS['tick'],
    'prepare': '\n'.join([
        f'scoreboard players set #stage {NS} 1',
        'frozendawn debug world set phase 0',
        'spreadplayers 0 0 0 48 false @s',
        *[f'execute store result storage {ANCHOR} {axis} int 1 run data get entity @s Pos[{i}]' for i, axis in enumerate('xyz')],
        # The player may land beyond the loaded spawn chunks: hold the pad's chunks and build once they have loaded.
        f'function {NS}:hold with storage {ANCHOR}',
        f'schedule function {NS}:build 40t',
        f'function {NS}:kit',
        'datapack list enabled',
    ]),
    'hold': f'$execute positioned $(x) $(y) $(z) run forceload add {PAD_CHUNKS}',
    'build': f'function {NS}:build_at with storage {ANCHOR}',
    'build_at': '\n'.join([f'$execute positioned $(x) $(y) $(z) rotated 180 0 run function {NS}:build_here',
                           f'$execute positioned $(x) $(y) $(z) run forceload remove {PAD_CHUNKS}']),
    'build_here': '\n'.join([
        # A level stone pad with the air above it cleared, wide enough for the frame, the banks and the chest.
        'fill ~-18 ~ ~-30 ~20 ~20 ~4 minecraft:air',
        'fill ~-18 ~21 ~-30 ~20 ~40 ~4 minecraft:air',
        'fill ~-18 ~-3 ~-30 ~20 ~-1 ~4 minecraft:stone',
        'tp @a[limit=1] ~ ~ ~ 180 0',
        'spawnpoint @a[limit=1] ~ ~ ~',
        'frozendawn debug world icaruslab',
        'execute if block ~-1 ~ ~-8 frozendawn:icarus_controller run say Icarus lab: reactor built on terrain',
        'execute unless block ~-1 ~ ~-8 frozendawn:icarus_controller run say Icarus lab: reactor build FAILED; run /function icarus_lab:prepare to try again',
        f'scoreboard players set #stage {NS} 2',
    ]),
    'kit': SCRIPTS['kit'],
    'recharge': f'function {NS}:recharge_at with storage {ANCHOR}\n' + f'function {NS}:menu',
    'recharge_at': '$execute positioned $(x) $(y) $(z) positioned ~-1 ~ ~-8 run frozendawn debug world icaruslab recharge',
    'reactor': f'function {NS}:reactor_at with storage {ANCHOR}\n' + f'function {NS}:menu',
    'reactor_at': '$tp @s $(x) $(y) $(z) 180 0',
    'edge_spot': REMEMBER + '\n' + f'function {NS}:edge_at with storage {ANCHOR}\n' + f'function {NS}:menu',
    # Over the spot first, high enough to clear the land, then down onto the nearest dry ground within 64 blocks; if
    # none is found the player still hangs over the spot instead of staying put.
    'edge_at': '$execute positioned $(x) $(y) $(z) run tp @s ~ ~60 ~180 180 0\n' + f'execute at @s run function {NS}:land',
    'witness_spot': REMEMBER + '\n' + f'function {NS}:witness_at with storage {ANCHOR}\n' + f'function {NS}:menu',
    'back': '\n'.join([f'execute if data storage {BACK} x run function {NS}:back_at with storage {BACK}',
                       f'execute unless data storage {BACK} x run function {NS}:reactor_at with storage {ANCHOR}', f'function {NS}:menu']),
    'back_at': '$tp @s $(x) $(y) $(z)',
    'witness_at': '$execute positioned $(x) $(y) $(z) run tp @s ~ ~60 ~300 180 0\n' + f'execute at @s run function {NS}:land',
    'land': 'spreadplayers ~ ~ 0 64 false @s\nexecute at @s run tp @s ~ ~ ~ 180 0',
    'air': SCRIPTS['air'],
    'day': SCRIPTS['day'],
    'night': SCRIPTS['night'],
    'survival': SCRIPTS['survival'],
    'creative': SCRIPTS['creative'],
    'menu': 'tellraw @s ' + TERRAIN_MENU,
}

PERF_SCRIPTS = {
    'load': f'scoreboard objectives add {PERF_NS} dummy\nexecute unless score #stage {PERF_NS} matches 0.. run scoreboard players set #stage {PERF_NS} 0',
    'tick': f'execute if score #stage {PERF_NS} matches 0 as @a[limit=1] run function {PERF_NS}:prepare',
    'prepare': '\n'.join([
        f'scoreboard players set #stage {PERF_NS} 1',
        'frozendawn debug world set phase 0',
        'gamemode creative @s',
        'spreadplayers 0 0 0 4 false @s',
        'datapack list enabled',
    ]),
    # Runs once: the allowlist wants stage 1, and LabIcarusPerf refuses a second blast by its receipt.
    'full_blast': '\n'.join([
        f'scoreboard players set #stage {PERF_NS} 2',
        f'execute store result score #blast {PERF_NS} run fdlab icarus_full_blast',
    ]),
}


def pack(destination, namespace, scripts, description):
    destination.mkdir(parents=True, exist_ok=False)
    (destination/'pack.mcmeta').write_text(json.dumps(pack_metadata(description))+'\n')
    write_sync_advancement(destination)
    functions = destination/('data/'+namespace+'/function')
    functions.mkdir(parents=True)
    for name, commands in scripts.items():
        (functions/(name+'.mcfunction')).write_text(commands+'\n')
    tags = destination/'data/minecraft/tags/function'
    tags.mkdir(parents=True)
    for name in ('load', 'tick'):
        (tags/(name+'.json')).write_text(json.dumps({'values': [namespace+':'+name]})+'\n')


def layer(block, height):
    return {'block': (8, block), 'height': (3, height)}


def prepare(template, title, perf, terrain=False):
    destination = ROOT/'run-lab/saves'/title
    if destination.exists():
        raise SystemExit(f'Refusing to overwrite {destination}')
    # The template's world lock proves it is closed; only version schema and world generation are read from it.
    with (template.parent/'session.lock').open('r+b') as lock:
        fcntl.lockf(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        source = io.BytesIO(gzip.decompress(template.read_bytes()))
        assert source.read(1) == b'\x0a'
        string(source)
        original = read(source, 10)['Data'][1]
        data = {name: copy.deepcopy(original[name]) for name in ('version', 'DataVersion', 'Version', 'WorldGenSettings')}
    worldgen = data['WorldGenSettings'][1]
    worldgen['bonus_chest'] = (1, 0)
    if perf or terrain:
        overworld = worldgen['dimensions'][1]['minecraft:overworld'][1]['generator'][1]
        if overworld['type'][1] != 'minecraft:noise':
            raise SystemExit(f'--perf and --terrain need a template with normal terrain, not {overworld["type"][1]}')
        spawn_y, angle, pack_name = 100, 0.0, 'icarus-perf' if perf else 'icarus-overworld'
    else:
        worldgen['generate_features'] = (1, 0)
        # Vanilla's classic superflat: grass at y -61, so feet stand at -60.
        worldgen['dimensions'][1]['minecraft:overworld'] = (10, {
            'type': (8, 'minecraft:overworld'),
            'generator': (10, {'type': (8, 'minecraft:flat'), 'settings': (10, {
                'biome': (8, 'minecraft:plains'), 'features': (1, 0), 'lakes': (1, 0),
                'structure_overrides': (9, (8, [])),
                'layers': (9, (10, [layer('minecraft:bedrock', 1), layer('minecraft:dirt', 2), layer('minecraft:grass_block', 1)]))})})})
        spawn_y, angle, pack_name = -60, 180.0, 'icarus-lab'
    data.update({'LevelName': (8, title), 'GameType': (3, 1), 'allowCommands': (1, 1), 'Difficulty': (1, 2),
        'DifficultyLocked': (1, 1), 'hardcore': (1, 0), 'initialized': (1, 1), 'Time': (4, 0), 'DayTime': (4, 6000),
        'LastPlayed': (4, int(time.time()*1000)), 'SpawnX': (3, 0), 'SpawnY': (3, spawn_y), 'SpawnZ': (3, 0),
        'SpawnAngle': (5, angle), 'WasModded': (1, 1),
        'GameRules': (10, {'doMobSpawning': (8, 'false'), 'keepInventory': (8, 'true'),
                           'announceAdvancements': (8, 'false'), 'doDaylightCycle': (8, 'false'),
                           'doWeatherCycle': (8, 'false')}),
        'DataPacks': (10, {'Enabled': (9, (8, ['vanilla', 'mod_data', 'file/'+pack_name])), 'Disabled': (9, (8, []))}),
        'enabled_features': (9, (8, ['minecraft:vanilla']))})
    root = {'Data': (10, data)}
    encoded = b'\x0a'+utf('')+write(10, root)
    check = io.BytesIO(encoded); assert check.read(1) == b'\x0a'; string(check)
    assert read(check, 10) == root and check.read() == b''
    destination.mkdir(parents=True)
    (destination/'level.dat').write_bytes(gzip.compress(encoded))
    (destination/'data').mkdir()
    (destination/'data/frozendawn_apocalypse.dat').write_bytes(gzip.compress(lab_state(data['DataVersion'][1])))
    if perf:
        pack(destination/'datapacks'/pack_name, PERF_NS, PERF_SCRIPTS, 'Icarus full-load perf; advancements hidden')
    elif terrain:
        pack(destination/'datapacks'/pack_name, CHECK_NS, TERRAIN_SCRIPTS, 'AMR-1 Icarus playtest on terrain; advancements hidden')
    else:
        pack(destination/'datapacks'/pack_name, CHECK_NS, SCRIPTS, 'AMR-1 Icarus playtest; advancements hidden')
    print(f'Prepared {destination}; not opened.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--template', type=Path, required=True)
    parser.add_argument('--perf', action='store_true', help='write the disposable full-load perf world')
    parser.add_argument('--terrain', action='store_true', help='build the check reactor on the template\'s own overworld terrain')
    parser.add_argument('--title', help='save name; the perf world must keep its own, which LabIcarusPerf requires')
    args = parser.parse_args()
    if args.perf and args.title not in (None, PERF_TITLE):
        raise SystemExit(f'--perf writes "{PERF_TITLE}"; LabIcarusPerf refuses any other world name')
    if args.perf and args.terrain:
        raise SystemExit('--perf and --terrain are separate worlds')
    prepare(args.template, args.title or (PERF_TITLE if args.perf else CHECK_TITLE), args.perf, args.terrain)

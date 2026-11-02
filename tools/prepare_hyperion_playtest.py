#!/usr/bin/env python3
"""Prepare a new superflat save for the SMS-1 Hyperion playtest.

The first player to join stands at (0, -60, 0) facing north, and the Hyperion
lab is built ahead of them: an off, fuelled SFR-1 with no Battery Banks whose
Power Port is linked to a half-charged 3x3x6 Hyperion silo outdoors, and a
half-charged 3x3x4 silo in a campfire-warmed stone room with a glass cup
against its east face. The world starts at phase 0. A clickable chat menu
gives the kit, day and night, phase steps, a lava source in the cup to start a
heat warning (and one to clear it), and game modes.
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
TITLE = 'Hyperion Check'
NS = 'hyperion_lab'
# HyperionLabPlacement puts the lava cup at origin + (9, 1, -5); the origin is the player's feet at (0, -60, 0).
LAVA = '9 -59 -5'


def button(label, function, color='aqua'):
    return {'text': '['+label+'] ', 'color': color,
            'clickEvent': {'action': 'run_command', 'value': '/function '+NS+':'+function}}


MENU = json.dumps(['', {'text': 'Hyperion: ', 'color': 'gray'},
                   button('Kit', 'kit', 'green'), button('Day', 'day'), button('Night', 'night'),
                   button('Phase 0', 'phase0'), button('Phase 3', 'phase3'), button('Phase 5', 'phase5'),
                   button('Lava', 'lava', 'red'), button('No lava', 'no_lava', 'red'),
                   button('Survival', 'survival', 'yellow'), button('Creative', 'creative', 'yellow')])

SCRIPTS = {
    'load': f'scoreboard objectives add {NS} dummy\nexecute unless score #stage {NS} matches 0.. run scoreboard players set #stage {NS} 0',
    'tick': f'execute if score #stage {NS} matches 0 as @a[limit=1] run function {NS}:prepare',
    'prepare': '\n'.join([
        f'scoreboard players set #stage {NS} 1',
        'frozendawn debug world set phase 0',
        'tp @s 0 -60 0 180 0',
        'spawnpoint @s 0 -60 0',
        '# North of the feet: the SFR-1 front left, the heated room front right, the outdoor battery behind them.',
        'execute positioned 0 -60 0 run frozendawn debug world hyperionlab',
        f'function {NS}:kit',
        'datapack list enabled',
    ]),
    'kit': '\n'.join([
        'clear @s',
        'item replace entity @s hotbar.0 with frozendawn:conduit_binder',
        'item replace entity @s hotbar.1 with frozendawn:superconducting_cell 64',
        'item replace entity @s hotbar.2 with frozendawn:hyperion_frame 64',
        'item replace entity @s hotbar.3 with frozendawn:hyperion_controller',
        'item replace entity @s hotbar.4 with frozendawn:hyperion_port 4',
        'item replace entity @s hotbar.5 with frozendawn:cryocooler 4',
        'item replace entity @s hotbar.6 with frozendawn:quench_vent',
        'item replace entity @s hotbar.7 with frozendawn:power_monitor',
        'item replace entity @s hotbar.8 with frozendawn:deuterium_canister 8',
        f'function {NS}:menu',
    ]),
    'day': 'time set 6000\n' + f'function {NS}:menu',
    'night': 'time set 18000\n' + f'function {NS}:menu',
    'phase0': 'frozendawn debug world set phase 0\n' + f'function {NS}:menu',
    'phase3': 'frozendawn debug world set phase 3\n' + f'function {NS}:menu',
    'phase5': 'frozendawn debug world set phase 5\n' + f'function {NS}:menu',
    'lava': f'setblock {LAVA} minecraft:lava\n' + f'function {NS}:menu',
    'no_lava': f'setblock {LAVA} minecraft:air\n' + f'function {NS}:menu',
    'survival': 'gamemode survival @s\n' + f'function {NS}:menu',
    'creative': 'gamemode creative @s\n' + f'function {NS}:menu',
    'menu': 'tellraw @s ' + MENU,
}


def pack(destination):
    destination.mkdir(parents=True, exist_ok=False)
    (destination/'pack.mcmeta').write_text(json.dumps(pack_metadata('Hyperion playtest; advancements hidden'))+'\n')
    write_sync_advancement(destination)
    functions = destination/('data/'+NS+'/function')
    functions.mkdir(parents=True)
    for name, commands in SCRIPTS.items():
        (functions/(name+'.mcfunction')).write_text(commands+'\n')
    tags = destination/'data/minecraft/tags/function'
    tags.mkdir(parents=True)
    for name in ('load', 'tick'):
        (tags/(name+'.json')).write_text(json.dumps({'values': [NS+':'+name]})+'\n')


def layer(block, height):
    return {'block': (8, block), 'height': (3, height)}


def prepare(template):
    destination = ROOT/'run-lab/saves'/TITLE
    if destination.exists():
        raise SystemExit(f'Refusing to overwrite {destination}')
    # The template's world lock proves it is closed; only version schema is read from it.
    with (template.parent/'session.lock').open('r+b') as lock:
        fcntl.lockf(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        source = io.BytesIO(gzip.decompress(template.read_bytes()))
        assert source.read(1) == b'\x0a'
        string(source)
        original = read(source, 10)['Data'][1]
        data = {name: copy.deepcopy(original[name]) for name in ('version', 'DataVersion', 'Version', 'WorldGenSettings')}
    worldgen = data['WorldGenSettings'][1]
    worldgen['generate_features'] = (1, 0)
    worldgen['bonus_chest'] = (1, 0)
    # Vanilla's classic superflat: grass at y -61, so feet stand at -60.
    worldgen['dimensions'][1]['minecraft:overworld'] = (10, {
        'type': (8, 'minecraft:overworld'),
        'generator': (10, {'type': (8, 'minecraft:flat'), 'settings': (10, {
            'biome': (8, 'minecraft:plains'), 'features': (1, 0), 'lakes': (1, 0),
            'structure_overrides': (9, (8, [])),
            'layers': (9, (10, [layer('minecraft:bedrock', 1), layer('minecraft:dirt', 2), layer('minecraft:grass_block', 1)]))})})})
    data.update({'LevelName': (8, TITLE), 'GameType': (3, 1), 'allowCommands': (1, 1), 'Difficulty': (1, 2),
        'DifficultyLocked': (1, 1), 'hardcore': (1, 0), 'initialized': (1, 1), 'Time': (4, 0), 'DayTime': (4, 6000),
        'LastPlayed': (4, int(time.time()*1000)), 'SpawnX': (3, 0), 'SpawnY': (3, -60), 'SpawnZ': (3, 0),
        'SpawnAngle': (5, 180.0), 'WasModded': (1, 1),
        'GameRules': (10, {'doMobSpawning': (8, 'false'), 'keepInventory': (8, 'true'),
                           'announceAdvancements': (8, 'false'), 'doDaylightCycle': (8, 'false'),
                           'doWeatherCycle': (8, 'false')}),
        'DataPacks': (10, {'Enabled': (9, (8, ['vanilla', 'mod_data', 'file/hyperion-lab'])), 'Disabled': (9, (8, []))}),
        'enabled_features': (9, (8, ['minecraft:vanilla']))})
    root = {'Data': (10, data)}
    encoded = b'\x0a'+utf('')+write(10, root)
    check = io.BytesIO(encoded); assert check.read(1) == b'\x0a'; string(check)
    assert read(check, 10) == root and check.read() == b''
    destination.mkdir(parents=True)
    (destination/'level.dat').write_bytes(gzip.compress(encoded))
    (destination/'data').mkdir()
    (destination/'data/frozendawn_apocalypse.dat').write_bytes(gzip.compress(lab_state(data['DataVersion'][1])))
    pack(destination/'datapacks/hyperion-lab')
    print(f'Prepared {destination}; not opened.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--template', type=Path, required=True)
    prepare(parser.parse_args().template)

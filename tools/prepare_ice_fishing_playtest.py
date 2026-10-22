#!/usr/bin/env python3
"""Prepare a new natural-terrain save for the ice fishing playtest.

The first player to join gets a test station built beside them (a thick ice
pond, a thin ice patch, a pre-cured Longsleeper barrel and a fresh barrel),
the full kit, and a clickable chat menu for switching phases.
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
TITLE = 'Ice Fishing Check'
SEED = 20261021
NS = 'ice_fish'


def button(label, function, color='aqua'):
    return {'text': '['+label+'] ', 'color': color,
            'clickEvent': {'action': 'run_command', 'value': '/function '+NS+':'+function}}


MENU = json.dumps(['', {'text': 'Ice fishing: ', 'color': 'gray'},
                   button('Phase 3', 'phase3'), button('Phase 5', 'phase5'),
                   button('Phase 6 early', 'phase6_early'), button('Phase 6 late', 'phase6_late'),
                   button('Kit', 'kit', 'green'), button('Survival', 'survival', 'yellow'),
                   button('Creative', 'creative', 'yellow')])


def sign(offset, facing, *lines):
    messages = ','.join("'"+json.dumps(line)+"'" for line in (list(lines)+['', '', '', ''])[:4])
    return f'setblock {offset} minecraft:oak_sign[rotation={facing}]{{front_text:{{messages:[{messages}]}}}}'


SCRIPTS = {
    'load': f'scoreboard objectives add {NS} dummy\nexecute unless score #stage {NS} matches 0.. run scoreboard players set #stage {NS} 0',
    'tick': f'execute if score #stage {NS} matches 0 as @a[limit=1] at @s run function {NS}:prepare',
    'prepare': '\n'.join([
        f'scoreboard players set #stage {NS} 1',
        '# Thick pond east of the player: 5 deep, so the auger works anywhere on top.',
        'fill ~2 ~ ~-3 ~8 ~6 ~3 minecraft:air',
        'fill ~2 ~-5 ~-3 ~8 ~-1 ~3 minecraft:ice',
        '# Thin patch west: 2 deep over stone, so the auger refuses.',
        'fill ~-8 ~ ~-3 ~-4 ~6 ~3 minecraft:air',
        'fill ~-8 ~-3 ~-3 ~-4 ~-3 ~3 minecraft:stone',
        'fill ~-8 ~-2 ~-3 ~-4 ~-1 ~3 minecraft:ice',
        '# Barrels north: one already cured (open it), one fresh for the real timer.',
        'fill ~-2 ~ ~-6 ~2 ~2 ~-4 minecraft:air',
        'fill ~-2 ~-1 ~-6 ~2 ~-1 ~-4 minecraft:stone',
        'setblock ~-1 ~ ~-5 minecraft:barrel[facing=up]{Items:[{Slot:0b,id:"frozendawn:longsleeper",count:4},'
        '{Slot:1b,id:"minecraft:gravel",count:16}],'
        'NeoForgeData:{frozendawn_longsleeper_cure_start:-100000L,frozendawn_longsleeper_cure_count:4}}',
        'setblock ~1 ~ ~-5 minecraft:barrel[facing=up]',
        sign('~2 ~ ~0', 4, 'Thick ice', 'Drill here'),
        sign('~-2 ~ ~0', 12, 'Thin ice', 'Auger refuses'),
        sign('~-1 ~1 ~-5', 8, 'Pre-cured', 'Open me'),
        sign('~1 ~1 ~-5', 8, 'Fresh barrel', '3 day cure'),
        'spawnpoint @s ~ ~ ~',
        f'function {NS}:kit',
        f'function {NS}:phase6_early',
        'datapack list enabled',
    ]),
    'kit': '\n'.join([
        'clear @s',
        'item replace entity @s armor.head with frozendawn:eva_helmet',
        'item replace entity @s armor.chest with frozendawn:eva_chestplate',
        'item replace entity @s armor.legs with frozendawn:eva_leggings',
        'item replace entity @s armor.feet with frozendawn:eva_boots',
        'item replace entity @s hotbar.0 with frozendawn:ice_auger',
        'item replace entity @s hotbar.1 with minecraft:fishing_rod',
        'item replace entity @s hotbar.2 with minecraft:fishing_rod[enchantments={levels:{"minecraft:lure":3}},custom_name=\'"Lure III rod"\']',
        'item replace entity @s hotbar.3 with frozendawn:longsleeper 4',
        'item replace entity @s hotbar.4 with minecraft:gravel 16',
        'item replace entity @s hotbar.5 with frozendawn:glassblood 8',
        'item replace entity @s hotbar.6 with frozendawn:rime_hagfish 8',
        'item replace entity @s hotbar.7 with minecraft:smoker',
        'item replace entity @s hotbar.8 with minecraft:coal 16',
        'item replace entity @s inventory.0 with frozendawn:o2_tank_mk3',
        'item replace entity @s inventory.1 with frozendawn:o2_tank_mk3',
        'item replace entity @s inventory.2 with minecraft:barrel 2',
        'item replace entity @s inventory.3 with minecraft:snow_block 16',
        'item replace entity @s inventory.4 with minecraft:ice 32',
        'item replace entity @s inventory.5 with minecraft:cooked_beef 32',
        'item replace entity @s inventory.6 with minecraft:iron_pickaxe',
        'item replace entity @s inventory.7 with minecraft:stick 2',
        'item replace entity @s inventory.8 with minecraft:iron_ingot 3',
        f'function {NS}:menu',
    ]),
    'phase3': 'frozendawn debug world set phase 3\n' + f'function {NS}:menu',
    'phase5': 'frozendawn debug world set phase 5\n' + f'function {NS}:menu',
    'phase6_early': 'frozendawn debug world set phase 6 early\n' + f'function {NS}:menu',
    'phase6_late': 'frozendawn debug world set phase 6 late\n' + f'function {NS}:menu',
    'survival': 'gamemode survival @s\n' + f'function {NS}:menu',
    'creative': 'gamemode creative @s\n' + f'function {NS}:menu',
    'menu': 'tellraw @s ' + MENU,
}


def pack(destination):
    destination.mkdir(parents=True, exist_ok=False)
    (destination/'pack.mcmeta').write_text(json.dumps(pack_metadata('Ice fishing playtest; advancements hidden'))+'\n')
    write_sync_advancement(destination)
    functions = destination/('data/'+NS+'/function')
    functions.mkdir(parents=True)
    for name, commands in SCRIPTS.items():
        (functions/(name+'.mcfunction')).write_text(commands+'\n')
    tags = destination/'data/minecraft/tags/function'
    tags.mkdir(parents=True)
    for name in ('load', 'tick'):
        (tags/(name+'.json')).write_text(json.dumps({'values': [NS+':'+name]})+'\n')


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
    worldgen['seed'] = (4, SEED)
    worldgen['generate_features'] = (1, 1)
    worldgen['bonus_chest'] = (1, 0)
    worldgen['dimensions'][1]['minecraft:overworld'] = (10, {
        'type': (8, 'minecraft:overworld'),
        'generator': (10, {'type': (8, 'minecraft:noise'), 'settings': (8, 'minecraft:overworld'),
                           'biome_source': (10, {'type': (8, 'minecraft:multi_noise'), 'preset': (8, 'minecraft:overworld')})})})
    data.update({'LevelName': (8, TITLE), 'GameType': (3, 1), 'allowCommands': (1, 1), 'Difficulty': (1, 2),
        'DifficultyLocked': (1, 1), 'hardcore': (1, 0), 'initialized': (1, 1), 'Time': (4, 0), 'DayTime': (4, 6000),
        'LastPlayed': (4, int(time.time()*1000)), 'SpawnX': (3, 0), 'SpawnY': (3, 80), 'SpawnZ': (3, 0),
        'SpawnAngle': (5, 0.0), 'WasModded': (1, 1),
        'GameRules': (10, {'doMobSpawning': (8, 'false'), 'keepInventory': (8, 'true'),
                           'announceAdvancements': (8, 'false'), 'doDaylightCycle': (8, 'false')}),
        'DataPacks': (10, {'Enabled': (9, (8, ['vanilla', 'mod_data', 'file/ice-fishing'])), 'Disabled': (9, (8, []))}),
        'enabled_features': (9, (8, ['minecraft:vanilla']))})
    root = {'Data': (10, data)}
    encoded = b'\x0a'+utf('')+write(10, root)
    check = io.BytesIO(encoded); assert check.read(1) == b'\x0a'; string(check)
    assert read(check, 10) == root and check.read() == b''
    destination.mkdir(parents=True)
    (destination/'level.dat').write_bytes(gzip.compress(encoded))
    (destination/'data').mkdir()
    (destination/'data/frozendawn_apocalypse.dat').write_bytes(gzip.compress(lab_state(data['DataVersion'][1])))
    pack(destination/'datapacks/ice-fishing')
    print(f'Prepared {destination}; not opened.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--template', type=Path, required=True)
    prepare(parser.parse_args().template)

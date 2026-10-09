#!/usr/bin/env python3
"""Prepare a fresh outdoor Ration Warmer check from seed metadata, never an existing save."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
from prepare_emergency_eva_playtest import fresh_metadata, named, payload_end, string_end

TITLE = 'ORSA Ration Warmer Check'
NAMESPACE = 'ration_warmer_check'
PACK = 'ration-warmer-check'


def tell(text, function=None):
    value = {'text': text, 'color': 'aqua'}
    if function:
        value['clickEvent'] = {'action': 'run_command', 'value': f'/function {NAMESPACE}:{function}'}
    return 'tellraw @s ' + json.dumps(value)


def fixture(item, count, frost=0, ruined=False):
    components = []
    if frost:
        components.append(f'frozendawn:frost_ticks={frost}')
    if ruined:
        components.append('minecraft:custom_data={frost_ruined:true}')
    suffix = '[' + ','.join(components) + ']' if components else ''
    return '\n'.join([
        'execute unless score #built rwcheck matches 1 run return 0',
        'item replace entity @s weapon.mainhand with frozendawn:ration_warmer[frozendawn:ration_warmer_charges=4]',
        f'item replace entity @s weapon.offhand with {item}{suffix} {count}',
        tell('QA fixture equipped: four charges. Right-click once; keep food in offhand. Walk or put the warmer away while thawing.')])


def scripts():
    return {
        'load': 'scoreboard objectives add rwcheck dummy\nexecute unless score #built rwcheck matches 1 run scoreboard players set #built rwcheck 0\nexecute unless score #handbook rwcheck matches 1 run scoreboard players set #handbook rwcheck 0',
        'tick': f'execute if score #built rwcheck matches 0 as @a[limit=1] at @s run function {NAMESPACE}:setup',
        'setup': '\n'.join([
            'execute unless score #built rwcheck matches 0 run return 0',
            'scoreboard players set #built rwcheck -1',
            'gamemode creative @s', 'gamerule doMobSpawning false', 'gamerule doDaylightCycle false',
            'gamerule doWeatherCycle false', 'gamerule keepInventory true', 'weather clear', 'time set noon',
            'fd world preset default', 'fd world set phase 6 late', 'fd world pause',
            'fill -8 64 -8 8 64 8 minecraft:bedrock', 'fill -8 65 -8 8 128 8 minecraft:air',
            'setblock 2 65 2 minecraft:crafting_table', 'setblock -2 64 0 minecraft:orange_concrete',
            'setworldspawn 0 65 6', 'spawnpoint @s 0 65 6', 'tp @s 0.5 65 6.5 180 10',
            'give @s minecraft:iron_ingot 4', 'give @s minecraft:copper_ingot 4', 'give @s minecraft:redstone 16',
            'give @s frozendawn:ration_warmer',
            'give @s patchouli:guide_book[patchouli:book="frozendawn:frozen_dawn_guide"]',
            'item replace entity @s armor.head with frozendawn:eva_helmet',
            'item replace entity @s armor.chest with frozendawn:eva_chestplate',
            'item replace entity @s armor.legs with frozendawn:eva_leggings',
            'item replace entity @s armor.feet with frozendawn:eva_boots',
            'give @s frozendawn:o2_tank_mk3 4',
            'effect give @s minecraft:night_vision infinite 0 true',
            'effect give @s minecraft:resistance infinite 4 true',
            'effect give @s minecraft:regeneration infinite 4 true',
            'gamemode survival @s', 'scoreboard players set #built rwcheck 1',
            f'function {NAMESPACE}:frozen', f'function {NAMESPACE}:controls',
            tell('READY: outdoor late Phase 6, no heater. This is an injected QA fixture, not a balance trial.')]),
        'handbook': '\n'.join([
            'execute unless score #built rwcheck matches 1 run return 0',
            'execute unless score #handbook rwcheck matches 0 run return 0',
            'give @s patchouli:guide_book[patchouli:book="frozendawn:frozen_dawn_guide"]',
            'scoreboard players set #handbook rwcheck 1',
            tell('ORSA Field Manual supplied. Getting Started -> A Fun Guide to Food Spoilage! -> ORSA Ration Warmer.')]),
        'frozen': fixture('minecraft:bread', 64, 2400),
        'chilled': fixture('minecraft:carrot', 32, 600),
        'ruined': fixture('minecraft:cooked_beef', 16, 6000, True),
        'fresh': fixture('minecraft:bread', 32),
        'resistant': fixture('minecraft:dried_kelp', 32),
        'controls': '\n'.join([
            'execute unless score #built rwcheck matches 1 run return 0',
            tell('[Frozen stack: 64 bread]', 'frozen'), tell('[Chilled stack: 32 carrots]', 'chilled'),
            tell('[Ruined stack: 16 steak]', 'ruined'), tell('[Fresh: click immediately]', 'fresh'),
            tell('[Resistant: dried kelp]', 'resistant'),
            tell('You can put the warmer away. Removing, replacing or splitting the offhand food cancels; one charge remains spent.'),
            tell('Crafting table at (2,65,2): JEI recipe R -> + transfer -> shift-click output. One redstone per charge: stack four in one slot or spread dust across slots. Excess dust stays.'),
            tell('Guide: Getting Started -> Food Spoilage -> Ration Warmer. Thaw progress is above the hotbar; food tooltip shows the 30-second window.')])
    }


def pack_only(destination, hooks=False):
    destination.mkdir(parents=True, exist_ok=True)
    for tag in ('load', 'tick'):
        path = destination / f'data/minecraft/tags/function/{tag}.json'
        if hooks:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps({'values': [f'{NAMESPACE}:{tag}']}) + '\n')
        else:
            path.unlink(missing_ok=True)
    for name, contents in scripts().items():
        path = destination / f'data/{NAMESPACE}/function/{name}.mcfunction'
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(contents + '\n')
    if hooks:
        (destination / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 48, 'description': TITLE}}) + '\n')
    else:
        (destination / 'pack.mcmeta').unlink(missing_ok=True)


def metadata(source):
    raw = fresh_metadata(gzip.decompress(source.read_bytes()), TITLE)
    replacements = {'SpawnX': (3, (0).to_bytes(4, 'big', signed=True)),
                    'SpawnY': (3, (65).to_bytes(4, 'big', signed=True)),
                    'SpawnZ': (3, (6).to_bytes(4, 'big', signed=True))}
    result = bytearray(raw[:10])
    offset = 10
    while raw[offset]:
        start = offset
        kind = raw[offset]
        end_name = string_end(raw, offset + 1)
        key = raw[offset + 3:end_name].decode()
        offset = payload_end(raw, kind, end_name)
        if key == 'DataPacks':
            continue
        if key in replacements:
            new_kind, value = replacements.pop(key)
            result.extend(named(new_kind, key, value))
        else:
            result.extend(raw[start:offset])
    for key, (kind, value) in replacements.items():
        result.extend(named(kind, key, value))
    def strings(values):
        payload = b'\x08' + len(values).to_bytes(4, 'big')
        for value in values:
            encoded = value.encode()
            payload += len(encoded).to_bytes(2, 'big') + encoded
        return payload
    packs = named(9, 'Enabled', strings(['vanilla', 'mod_data', f'file/{PACK}']))
    packs += named(9, 'Disabled', strings([])) + b'\x00'
    result.extend(named(10, 'DataPacks', packs))
    result.extend(b'\x00\x00')
    if payload_end(result, 10, 3) != len(result):
        raise ValueError('Invalid Ration Warmer world metadata')
    return bytes(result)


def prepare(source, destination):
    if destination.exists():
        raise SystemExit('Test world already exists; refusing to overwrite it.')
    source_file = source / 'level.dat'
    source_hash = hashlib.sha256(source_file.read_bytes()).hexdigest()
    raw = metadata(source_file)
    destination.mkdir(parents=True)
    (destination / 'level.dat').write_bytes(gzip.compress(raw))
    pack_only(destination / 'datapacks' / PACK, hooks=True)
    if hashlib.sha256(source_file.read_bytes()).hexdigest() != source_hash:
        raise SystemExit('Source metadata changed during preparation; inspect source save.')
    (destination / 'ration-warmer-preparation.json').write_text(json.dumps({
        'source_metadata': str(source_file.resolve()), 'source_sha256': source_hash,
        'copied': 'fresh seed metadata only; no chunks, players, inventories or saved attachments',
        'destination': str(destination.resolve()), 'world': TITLE}, indent=2) + '\n')
    print(destination.resolve())


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pack-only', type=Path)
    parser.add_argument('--source', type=Path)
    parser.add_argument('--destination', type=Path)
    args = parser.parse_args()
    if args.pack_only:
        pack_only(args.pack_only)
    elif args.source and args.destination:
        prepare(args.source, args.destination)
    else:
        parser.error('Supply --pack-only or both --source and --destination')

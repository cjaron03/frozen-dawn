#!/usr/bin/env python3
"""Separate archer trial: five real sword encounters, production learning and an accelerated empty gap."""
import argparse
import copy
import gzip
import json
import shutil
from pathlib import Path
from prepare_macs_shield_playtest import SCRIPTS as GUARD, ADVANCEMENT as SWORD_HIT, tell
from prepare_macs_integrated_playtest import SCRIPTS as CAMP

SCRIPTS = {name: content.replace('macs_guard', 'macs_archer').replace(' mg', ' ma').replace('mg_deaths', 'ma_deaths').replace('"mg"', '"ma"')
           .replace('..3', '..4').replace('matches 4..', 'matches 5..').replace('/4', '/5')
           for name, content in GUARD.items()}
SCRIPTS['initialize'] = SCRIPTS['initialize'].replace('fd world set phase 6 late',
    'fd postmaeve set-erased\nfd postmaeve reset-erased confirm\nfd world preset default\nfd world set phase 6 late')
SCRIPTS['initialize'] = SCRIPTS['initialize'].replace('separate sword arena', 'separate archer arena')
SCRIPTS['kit'] = CAMP['kit'].replace('hotbar.0 with minecraft:bow', 'hotbar.0 with minecraft:iron_sword')\
    .replace('hotbar.1 with minecraft:iron_sword', 'hotbar.1 with minecraft:bow')
SCRIPTS['build'] += '''\nfill 2202 101 2202 2230 101 2230 minecraft:snow[layers=1]
fill 2203 101 2204 2207 101 2228 minecraft:snow[layers=3]
fill 2210 101 2205 2218 101 2208 minecraft:snow[layers=5]
fill 2214 101 2210 2215 102 2211 minecraft:stone
fill 2214 101 2221 2215 102 2222 minecraft:stone'''
SCRIPTS['ready'] = SCRIPTS['ready'].replace('Training complete. Run /fd maeve dump and reply done before starting the real encounter.',
    'Five sword encounters complete. Run /function macs_archer:start for the real fight. Shields, closing distance and existing cover are available.')
SCRIPTS['start_ready'] = '''function macs_archer:kit
effect clear @s minecraft:resistance
scoreboard players set @s ma_deaths 0
scoreboard players set #stage ma 3
scoreboard players set #timer ma 0
tp @s 2226.5 101 2216.5 90 0
summon frozendawn:architect 2210.5 101 2216.5 {Tags:["macs_archer_actor"],PersistenceRequired:1b}
execute as @e[tag=macs_archer_actor] run fd architect record @s 1337
''' + tell('Archer encounter: move normally, use your shield or rush into sword range. Finish records the actor trace. Describe what happened before reading the dump.', '/function macs_archer:finish')
SCRIPTS['end'] = SCRIPTS['end'].replace('use /function macs_archer:again.',
    'use /function macs_archer:again. Results and contradictions can legitimately change the next selection; warmup adds a real sword encounter.')
SCRIPTS['snapshot'] = 'execute as @e[tag=macs_archer_actor] run fd architect dump @s'
SCRIPTS['tick'] += '''\nexecute if score #stage ma matches 3 run scoreboard players add #timer ma 1
execute if score #stage ma matches 3 if score #timer ma matches 200.. run function macs_archer:snapshot
execute if score #stage ma matches 3 if score #timer ma matches 200.. run scoreboard players set #timer ma 0
execute if score #stage ma matches 3 unless entity @e[tag=macs_archer_actor] as @a[tag=macs_archer,limit=1] run function macs_archer:end'''
SCRIPTS['abort'] = 'function macs_archer:end'
SCRIPTS['reset'] = 'function macs_archer:remove_actor\nfunction macs_archer:initialize'
ADVANCEMENT = copy.deepcopy(SWORD_HIT)
ADVANCEMENT['criteria']['sword']['conditions']['entity']['nbt'] = '{Tags:["macs_archer_training"]}'
ADVANCEMENT['rewards']['function'] = 'macs_archer:hit'


def write_pack(path, game_test=False):
    functions = path / 'data/macs_archer/function'; functions.mkdir(parents=True, exist_ok=True)
    for name, content in SCRIPTS.items():
        (functions / f'{name}.mcfunction').write_text(content + '\n')
    advancements = path / 'data/macs_archer/advancement'; advancements.mkdir(parents=True, exist_ok=True)
    (advancements / 'sword_hit.json').write_text(json.dumps(ADVANCEMENT, indent=2) + '\n')
    if not game_test:
        (path / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 48, 'description': 'MACS keep-away archer ordinary-action trial'}}))
        tags = path / 'data/minecraft/tags/function'; tags.mkdir(parents=True, exist_ok=True)
        for name in ('load', 'tick'):
            (tags / f'{name}.json').write_text(json.dumps({'values': [f'macs_archer:{name}']}))


def prepare(source, destination):
    if destination.exists(): raise SystemExit(f'Refusing to overwrite {destination}')
    raw = gzip.decompress((source / 'level.dat').read_bytes()); marker = b'\x08\x00\x09LevelName'
    if raw.count(marker) != 1: raise SystemExit('Expected one LevelName in the closed source world')
    at = raw.index(marker) + len(marker); length = int.from_bytes(raw[at:at + 2], 'big')
    title = b'MACS Keep-away Archer - Normal'
    shutil.copytree(source, destination, ignore=shutil.ignore_patterns('session.lock'))
    (destination / 'level.dat').write_bytes(gzip.compress(raw[:at] + len(title).to_bytes(2, 'big') + title + raw[at + 2 + length:]))
    if (destination / 'datapacks').exists(): shutil.rmtree(destination / 'datapacks')
    write_pack(destination / 'datapacks/macs-archer')
    print(f'Prepared {destination}; /function macs_archer:setup initializes only this disposable copy.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pack-only', type=Path); parser.add_argument('--source', type=Path); parser.add_argument('--destination', type=Path)
    args = parser.parse_args()
    if args.pack_only: write_pack(args.pack_only, True)
    elif args.source and args.destination: prepare(args.source, args.destination)
    else: parser.error('Use --pack-only or --source/--destination')

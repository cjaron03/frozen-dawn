#!/usr/bin/env python3
"""Retire the obsolete Master-based attention fixture; preserve safe cleanup commands."""
import argparse
import gzip
import json
import shutil
from pathlib import Path
from prepare_maeve_attention_coop import write_pack as write_coop_pack


SCRIPTS = {
    'load': 'tellraw @a {"text":"The old Master-based attention comparison is retired. Run /function maeve_attention:cleanup to remove its test actors.","color":"yellow"}',
    'cleanup': '''schedule clear maeve_attention:prompt
schedule clear maeve_attention:finish_all
execute as @e[tag=maeve_attention_actor] run data remove entity @s HearthMasterArchitectId
execute as @e[tag=maeve_attention_actor] run data remove entity @s MasterMindCopyRealId
execute as @e[tag=maeve_attention_actor] run data merge entity @s {NoAI:1b}
kill @e[tag=maeve_attention_actor]
fill 399 101 407 401 104 409 minecraft:air
fill 399 101 395 401 104 398 minecraft:air
effect clear @s
tellraw @s {"text":"Old attention test cleared. Masters remain independent guardians. This comparison has been retired; no new round was started.","color":"aqua"}''',
    'setup': 'function maeve_attention:cleanup',
    'control': 'function maeve_attention:cleanup',
    'tactic': 'function maeve_attention:cleanup',
    'start': 'function maeve_attention:cleanup',
    'prompt': '',
    'finish': '',
    'finish_all': '',
}


def write_pack(pack, game_test=False):
    pack.mkdir(parents=True, exist_ok=True)
    if not game_test:
        (pack / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 48, 'description': 'Maeve retired attention comparison cleanup'}}))
    functions = pack / 'data/maeve_attention/function'
    functions.mkdir(parents=True, exist_ok=True)
    for name, text in SCRIPTS.items():
        (functions / f'{name}.mcfunction').write_text(text + '\n')
    if not game_test:
        tags = pack / 'data/minecraft/tags/function'
        tags.mkdir(parents=True, exist_ok=True)
        (tags / 'load.json').write_text(json.dumps({'values': ['maeve_attention:load']}))
    else:
        for stale in (pack / 'pack.mcmeta', pack / 'data/minecraft/tags/function/load.json'):
            stale.unlink(missing_ok=True)
    write_coop_pack(pack, game_test)


def prepare(source, destination):
    if destination.exists():
        raise SystemExit(f'Refusing to overwrite {destination}')
    level = gzip.decompress((source / 'level.dat').read_bytes())
    marker = b'\x08\x00\x09LevelName'
    if level.count(marker) != 1:
        raise SystemExit('Expected exactly one LevelName in the closed QA world')
    at = level.index(marker) + len(marker)
    old_length = int.from_bytes(level[at:at + 2], 'big')
    name = b'Maeve Attention Encounter'
    renamed = level[:at] + len(name).to_bytes(2, 'big') + name + level[at + 2 + old_length:]
    shutil.copytree(source, destination)
    (destination / 'level.dat').write_bytes(gzip.compress(renamed))
    pack = destination / 'datapacks/maeve-slice1'
    if pack.exists():
        shutil.rmtree(pack)
    write_pack(pack)
    print(f'Prepared {destination}. Run /function maeve_attention:setup.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path)
    parser.add_argument('--destination', type=Path)
    parser.add_argument('--pack-only', type=Path)
    args = parser.parse_args()
    if args.pack_only:
        write_pack(args.pack_only, game_test=True)
    elif args.source and args.destination:
        prepare(args.source, args.destination)
    else:
        parser.error('Use --source and --destination, or --pack-only')

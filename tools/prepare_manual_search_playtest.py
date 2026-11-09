#!/usr/bin/env python3
"""Fresh Manual Search Check: the field manual's search bar finds entries by item name.

A flat Creative lab, with the world clock paused. One button gives the manual; another unlocks Rocket Assembly,
which is locked behind the transponder broadcast and so hidden from search until then.
Advancements are filtered as in every generated playtest world, and the difficulty is locked. Never overwrites a save.
Usage (lab client, run-lab/ directory):
  python3 tools/prepare_manual_search_playtest.py --source <any closed level.dat> --destination "run-lab/saves/Manual Search Check"
"""
import argparse
import fcntl
import gzip
import io
import json
from pathlib import Path

import prepare_blind_architect_playtest as nbt
from prepare_power_network_playtest import prepare, pack_only
from prepare_terminus_vault_playtest import ADVANCEMENTS

TITLE = 'Manual Search Check'
NS = 'manual_search_check'
PACK = 'manual-search-check'
OBJECTIVE = 'manualsearch'
GUARD = f'execute unless score #built {OBJECTIVE} matches 1 run return 0'
MANUAL = 'give @s patchouli:guide_book[patchouli:book="frozendawn:frozen_dawn_guide"]'
UNLOCKS = ('broadcast_complete',)


def tell(text, command=None, color='aqua'):
    value = {'text': text, 'color': color}
    if command:
        value['clickEvent'] = {'action': 'run_command', 'value': command}
    return 'tellraw @s '+json.dumps(value)


def scripts():
    setup = [f'execute unless score #built {OBJECTIVE} matches -1 run return 0',
             f'scoreboard players set #built {OBJECTIVE} -2', 'gamemode creative @s',
             'gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false',
             'gamerule doFireTick false', 'gamerule keepInventory true',
             'gamerule commandBlockOutput false', 'gamerule announceAdvancements false',
             'time set 6000', 'weather clear', 'clear @s', 'fd world pause',
             'fill -16 64 -16 16 64 16 minecraft:polished_deepslate',
             'setworldspawn 0 65 0', 'spawnpoint @s 0 65 0', 'tp @s 0.5 65 0.5 180 0',
             f'scoreboard players set #built {OBJECTIVE} 1', 'function '+NS+':controls', 'return 1']
    controls = [GUARD, tell('MANUAL SEARCH CHECK: read Start Here, then search item names on the front page.'),
                tell('[Give manual]', '/function '+NS+':manual', 'green'),
                tell('[Unlock Rocket Assembly]', '/function '+NS+':unlock', 'yellow'),
                tell('[Controls]', '/function '+NS+':controls', 'gray'), 'return 1']
    return {
        'load': f'scoreboard objectives add {OBJECTIVE} dummy\nexecute unless score #built {OBJECTIVE} matches -2..1 run scoreboard players set #built {OBJECTIVE} 0\nexecute if score #built {OBJECTIVE} matches -2..-1 run scoreboard players set #built {OBJECTIVE} 0',
        'bootstrap': '\n'.join([f'execute unless score #built {OBJECTIVE} matches 0 run return 0',
                               'forceload add -32 -32 32 32', f'scoreboard players set #built {OBJECTIVE} -1', 'return 1']),
        'tick': '\n'.join([f'execute if score #built {OBJECTIVE} matches 0 as @a[limit=1] run function '+NS+':bootstrap',
                          f'execute if score #built {OBJECTIVE} matches -1 if loaded -32 65 -32 if loaded 32 65 32 as @a[limit=1] run function '+NS+':setup']),
        'setup': '\n'.join(setup),
        'controls': '\n'.join(controls),
        'manual': '\n'.join([GUARD, MANUAL, 'return 1']),
        'unlock': '\n'.join([GUARD, *[f'advancement grant @s only frozendawn:{name}' for name in UNLOCKS], 'return 1'])}


def unlock_advancements(pack):
    """The lock advancements as the mod defines them, rootless and silent: the filter hides the originals."""
    out = pack/'data/frozendawn/advancement'
    out.mkdir(parents=True, exist_ok=True)
    for name in UNLOCKS:
        advancement = json.loads((ADVANCEMENTS/f'{name}.json').read_text())
        del advancement['parent']
        advancement['display'].update({'background': 'minecraft:textures/block/smooth_stone.png',
                                       'show_toast': False, 'announce_to_chat': False})
        (out/f'{name}.json').write_text(json.dumps(advancement, indent=2)+'\n')


def prepare_world(source, destination):
    with (source.parent/'session.lock').open('rb') as lock:
        fcntl.lockf(lock, fcntl.LOCK_SH | fcntl.LOCK_NB)
        prepare(source, destination, title=TITLE, namespace=NS, pack=PACK, build_scripts=scripts, surface=128, structures=False)
    unlock_advancements(destination/'datapacks'/PACK)
    level = destination/'level.dat'
    stream = io.BytesIO(gzip.decompress(level.read_bytes()))
    assert stream.read(1) == b'\x0a'
    nbt.string(stream)
    root = nbt.read(stream, 10)
    root['Data'][1].update({'DifficultyLocked': (1, 1), 'DragonFight': (10, {})})
    level.write_bytes(gzip.compress(b'\x0a'+nbt.utf('')+nbt.write(10, root)))
    report = destination/'preparation.json'
    info = json.loads(report.read_text())
    info.update({'mode': 'Creative', 'phase': 'paused',
                 'fixtures': 'buttons for the manual and the Rocket Assembly unlock'})
    report.write_text(json.dumps(info, indent=2)+'\n')
    print(destination.resolve())


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path)
    parser.add_argument('--destination', type=Path)
    parser.add_argument('--pack-only', type=Path)
    args = parser.parse_args()
    if args.pack_only:
        pack_only(args.pack_only, title=TITLE, namespace=NS, build_scripts=scripts)
    elif args.source and args.destination:
        prepare_world(args.source, args.destination)
    else:
        parser.error('Use --source/--destination or --pack-only')

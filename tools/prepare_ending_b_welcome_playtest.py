#!/usr/bin/env python3
"""Fresh Ending Welcome Check: both endings' screens on demand, to compare their epilogues (CH-34).

A flat Creative lab with the world clock paused. Buttons play Ending A (plain) or Ending B (truth) to the caller through
/fd debug ending play, which launches nothing, moves no one and saves nothing, so either can be replayed. Hold Q to
fast-forward the credits. Difficulty is locked and advancement popups are off. Never overwrites a save.
Usage (lab client, run-lab/ directory):
  python3 tools/prepare_ending_b_welcome_playtest.py --source <any closed level.dat> --destination "run-lab/saves/Ending Welcome Check"
"""
import argparse
import fcntl
import gzip
import io
import json
from pathlib import Path

import prepare_blind_architect_playtest as nbt
from prepare_power_network_playtest import prepare, pack_only

TITLE = 'Ending Welcome Check'
NS = 'ending_welcome_check'
PACK = 'ending-welcome-check'
OBJECTIVE = 'endingwelcome'
GUARD = f'execute unless score #built {OBJECTIVE} matches 1 run return 0'


def tell(text, command=None, color='aqua'):
    value = {'text': text, 'color': color}
    if command:
        value['clickEvent'] = {'action': 'run_command', 'value': command}
    return 'tellraw @s '+json.dumps(value)


def scripts():
    setup = [f'execute unless score #built {OBJECTIVE} matches -1 run return 0',
             f'scoreboard players set #built {OBJECTIVE} -2', 'gamemode creative @s',
             'gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false',
             'gamerule doFireTick false', 'gamerule commandBlockOutput false', 'gamerule announceAdvancements false',
             'time set 6000', 'weather clear', 'fd world pause',
             'fill -8 64 -8 8 64 8 minecraft:polished_deepslate',
             'setworldspawn 0 65 0', 'spawnpoint @s 0 65 0', 'tp @s 0.5 65 0.5 180 0',
             f'scoreboard players set #built {OBJECTIVE} 1', 'function '+NS+':controls', 'return 1']
    controls = [GUARD, tell('ENDING WELCOME CHECK: hold Q through the credits to reach the epilogue.'),
                tell('[Play Ending A (plain)]', '/fd debug ending play plain', 'aqua'),
                tell('[Play Ending B (truth)]', '/fd debug ending play truth', 'gold'),
                tell('[Controls]', '/function '+NS+':controls', 'gray'), 'return 1']
    return {
        'load': f'scoreboard objectives add {OBJECTIVE} dummy\nexecute unless score #built {OBJECTIVE} matches -2..1 run scoreboard players set #built {OBJECTIVE} 0\nexecute if score #built {OBJECTIVE} matches -2..-1 run scoreboard players set #built {OBJECTIVE} 0',
        'bootstrap': '\n'.join([f'execute unless score #built {OBJECTIVE} matches 0 run return 0',
                               'forceload add -16 -16 16 16', f'scoreboard players set #built {OBJECTIVE} -1', 'return 1']),
        'tick': '\n'.join([f'execute if score #built {OBJECTIVE} matches 0 as @a[limit=1] run function '+NS+':bootstrap',
                          f'execute if score #built {OBJECTIVE} matches -1 if loaded -16 65 -16 if loaded 16 65 16 as @a[limit=1] run function '+NS+':setup',
                          f'execute if score #built {OBJECTIVE} matches 1 run forceload remove -16 -16 16 16']),
        'setup': '\n'.join(setup),
        'controls': '\n'.join(controls)}


def prepare_world(source, destination):
    with (source.parent/'session.lock').open('rb') as lock:
        fcntl.lockf(lock, fcntl.LOCK_SH | fcntl.LOCK_NB)
        prepare(source, destination, title=TITLE, namespace=NS, pack=PACK, build_scripts=scripts, surface=64, structures=False)
    level = destination/'level.dat'
    stream = io.BytesIO(gzip.decompress(level.read_bytes()))
    assert stream.read(1) == b'\x0a'
    nbt.string(stream)
    root = nbt.read(stream, 10)
    root['Data'][1].update({'DifficultyLocked': (1, 1), 'GameType': (3, 1), 'LevelName': (8, destination.name)})
    level.write_bytes(gzip.compress(b'\x0a'+nbt.utf('')+nbt.write(10, root)))
    report = destination/'preparation.json'
    info = json.loads(report.read_text())
    info.update({'mode': 'Creative', 'clock': 'paused',
                 'fixtures': 'buttons that play Ending A or Ending B through /fd debug ending play'})
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

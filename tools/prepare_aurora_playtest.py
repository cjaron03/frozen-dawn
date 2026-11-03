#!/usr/bin/env python3
"""Prepare a new Aurora Sky Check from closed generator metadata; never copy a replay."""
import argparse
import fcntl
import gzip
import io
import json
from pathlib import Path
import prepare_blind_architect_playtest as nbt
from prepare_power_network_playtest import prepare, pack_only

TITLE = 'Aurora Sky Check'
NS = 'aurora_check'
GUARD = 'execute unless score #built aurora matches 1 run return 0'


def tell(text, function=None):
    value = {'text': text, 'color': 'aqua'}
    if function:
        value['clickEvent'] = {'action': 'run_command', 'value': '/function ' + NS + ':' + function}
    return 'tellraw @s ' + json.dumps(value)


def scripts():
    setup = ['execute unless score #built aurora matches -1 run return 0',
             'scoreboard players set #built aurora -2', 'tag @s add aurora_lab',
             'gamemode creative @s', 'gamerule doMobSpawning false', 'gamerule doDaylightCycle false',
             'gamerule doWeatherCycle false', 'gamerule doFireTick false', 'gamerule keepInventory true',
             'gamerule announceAdvancements false', 'gamerule sendCommandFeedback false',
             'gamerule commandBlockOutput false', 'fd world set phase 0',
             'time set midnight', 'weather clear', 'effect clear @s minecraft:night_vision']
    for x in range(-32, 49, 16):
        setup += [f'fill {x} 64 -32 {x+15} 64 31 minecraft:stone_bricks']
    # North-facing view: silhouetted ridge, solid shelter, and glass-window shelter.
    for x, top in [(-30, 83), (-22, 94), (-14, 88), (-6, 101), (2, 92), (10, 85)]:
        setup.append(f'fill {x} 65 -31 {x+7} {top} -28 minecraft:deepslate')
    setup += ['fill 15 64 -2 25 72 10 minecraft:deepslate_bricks hollow',
              'fill 16 66 -2 24 70 -2 minecraft:glass',
              'fill 32 64 -2 42 72 10 minecraft:deepslate_bricks hollow',
              'setblock 20 65 10 minecraft:air', 'setblock 20 66 10 minecraft:air',
              'setblock 37 65 10 minecraft:air', 'setblock 37 66 10 minecraft:air',
              'setworldspawn 0 65 10', 'spawnpoint @s 0 65 10', 'tp @s 0.5 65 10.5 180 -30',
              'scoreboard players set #built aurora 1', 'fdlab aurora preview',
              'function ' + NS + ':controls', 'return 1']
    result = {
        'load': 'scoreboard objectives add aurora dummy\nexecute unless score #built aurora matches -2..1 run scoreboard players set #built aurora 0',
        'bootstrap': '\n'.join(['execute unless score #built aurora matches 0 run return 0',
                              'forceload add -32 -32 63 31', 'scoreboard players set #built aurora -1', 'return 1']),
        'tick': '\n'.join(['execute if score #built aurora matches 0 as @a[limit=1] run function ' + NS + ':bootstrap',
                           'execute if score #built aurora matches -1 if loaded -32 65 -32 if loaded 63 65 31 as @a[limit=1] run function ' + NS + ':setup']),
        'setup': '\n'.join(setup),
        'controls': '\n'.join([GUARD, tell('AURORA SKY CHECK: Creative, paused apocalypse. This is a forced display for visual review.'),
            tell('[Phase 0 / clear]', 'clear'), tell('[Phase 0 / rain]', 'rain'), tell('[Phase 0 / thunder]', 'thunder'),
            tell('[Phase 1]', 'phase1'), tell('[Phase 2]', 'phase2'), tell('[Phase 3]', 'phase3'), tell('[Phase 4 / absent]', 'phase4'),
            tell('[Daytime]', 'day'), tell('[Midnight]', 'night'), tell('[Open view]', 'open'),
            tell('[Glass window]', 'window'), tell('[Solid roof]', 'roof'), tell('[Above clouds]', 'above'),
            tell('[Preview again]', 'preview'), tell('[Natural schedule]', 'natural'), tell('[Read status]', 'read'),
            tell('Try Fast, Fancy, Fabulous and clouds OFF in Video Settings. Change one setting at a time. Clouds and terrain should cover the curtains.'), 'return 1']),
        'preview': '\n'.join([GUARD, 'fdlab aurora preview', 'return 1']),
        'natural': '\n'.join([GUARD, 'fdlab aurora natural', 'return 1']),
        'read': '\n'.join([GUARD, 'fdlab aurora status', 'return 1']),
    }
    for name, phase, weather in [('clear', 0, 'clear'), ('rain', 0, 'rain'), ('thunder', 0, 'thunder'),
                                  ('phase1', 1, 'clear'), ('phase2', 2, 'rain'), ('phase3', 3, 'thunder'), ('phase4', 4, 'thunder')]:
        result[name] = '\n'.join([GUARD, f'fd world set phase {phase}', 'time set midnight',
                                  f'weather {weather} 1000000', 'fdlab aurora preview',
                                  tell('Scene: ' + name + '. Allow several seconds for weather to settle.'), 'return 1'])
    for name, time in [('day', 'noon'), ('night', 'midnight')]:
        result[name] = '\n'.join([GUARD, 'time set ' + time, 'return 1'])
    for name, coords in [('open', '0.5 65 10.5 180 -30'), ('window', '20.5 66 7.5 180 -25'),
                          ('roof', '37.5 66 7.5 180 -30'), ('above', '0.5 220 10.5 180 -30')]:
        result[name] = '\n'.join([GUARD, 'tp @s ' + coords, 'return 1'])
    return result


def prepare_world(source, destination):
    lab = Path(__file__).resolve().parents[1] / 'run-lab'
    config = lab / 'config/frozendawn-common.toml'
    if config.exists():
        raise SystemExit('Use a fresh lab config; refusing to replace existing settings')
    with (source.parent / 'session.lock').open('rb') as lock:
        fcntl.lockf(lock, fcntl.LOCK_SH | fcntl.LOCK_NB)
        prepare(source, destination, title=TITLE, namespace=NS, pack='aurora-check',
                build_scripts=scripts, surface=65, structures=False)
    for filename in ('level.dat', 'data/frozendawn_apocalypse.dat'):
        path = destination / filename
        stream = io.BytesIO(gzip.decompress(path.read_bytes()))
        assert stream.read(1) == b'\x0a'
        name = nbt.string(stream)
        root = nbt.read(stream, 10)
        if filename == 'level.dat':
            root['Data'][1].update({'DayTime': (4, 18_000), 'DragonFight': (10, {}),
                                     'confirmedExperimentalSettings': (1, 1), 'DifficultyLocked': (1, 1)})
        else:
            root['data'][1]['apocalypseTicks'] = (4, 0)
        path.write_bytes(gzip.compress(b'\x0a' + nbt.utf(name) + nbt.write(10, root)))
    config.parent.mkdir(parents=True, exist_ok=True)
    config.write_text('[general]\npauseProgression = true\n')


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source', type=Path)
    p.add_argument('--destination', type=Path)
    p.add_argument('--pack-only', type=Path)
    a = p.parse_args()
    if a.pack_only:
        pack_only(a.pack_only, title=TITLE, namespace=NS, build_scripts=scripts)
    elif a.source and a.destination:
        prepare_world(a.source, a.destination)
    else:
        p.error('Use --source/--destination or --pack-only')

#!/usr/bin/env python3
"""Fresh normal-terrain aurora review; leaves the flat review and its evidence intact."""
import argparse
import fcntl
import gzip
import io
import json
import tomllib
from pathlib import Path
import prepare_blind_architect_playtest as nbt
from prepare_power_network_playtest import prepare, pack_only
from prepare_aurora_playtest import scripts as flat_scripts

TITLE = 'Aurora Terrain Check'
NS = 'aurora_terrain'
GUARD = 'execute unless score #built aurora matches 1 run return 0'


def tell(text, function=None):
    value = {'text': text, 'color': 'aqua'}
    if function:
        value['clickEvent'] = {'action': 'run_command', 'value': '/function ' + NS + ':' + function}
    return 'tellraw @s ' + json.dumps(value)


def scripts():
    result = {name: body.replace('aurora_check:', NS + ':') for name, body in flat_scripts().items()}
    # Restore the held night when returning from a naturally ticking event review.
    result['clear'] = result['clear'].replace(GUARD, GUARD + '\ngamerule doDaylightCycle false', 1)
    # Use only the shared phase/weather controls. Never build the flat arena in this world.
    for name in ['bootstrap', 'window', 'roof', 'open', 'above']:
        result.pop(name, None)
    result['tick'] = 'execute if score #built aurora matches 0 as @a[limit=1] at @s run function ' + NS + ':setup'
    result['setup'] = '\n'.join([
        'execute unless score #built aurora matches 0 run return 0',
        'scoreboard players set #built aurora -2', 'tag @s add aurora_lab',
        'gamemode creative @s', 'gamerule doMobSpawning false', 'gamerule doDaylightCycle false',
        'gamerule doWeatherCycle false', 'gamerule doFireTick false', 'gamerule keepInventory true',
        'gamerule announceAdvancements false', 'gamerule sendCommandFeedback false',
        'gamerule commandBlockOutput false', 'fd world set phase 0', 'time set midnight',
        'weather clear', 'effect clear @s minecraft:night_vision',
        'execute at @s positioned over motion_blocking_no_leaves run tp @s ~ ~2 ~ 180 -25',
        'scoreboard players set #built aurora 1', 'fdlab aurora preview',
        'function ' + NS + ':controls', 'return 1'])
    result['controls'] = '\n'.join([GUARD,
        tell('AURORA TERRAIN CHECK: normal world generation, Creative. Fly or walk anywhere; no arena or placed scenery.'),
        tell('Midnight and apocalypse progression are held for comparison. The display is forced; clouds and weather still obscure it.'),
        tell('[Clear night]', 'clear'), tell('[Rain]', 'rain'), tell('[Thunder]', 'thunder'),
        tell('[Phase 1]', 'phase1'), tell('[Phase 2]', 'phase2'), tell('[Phase 3]', 'phase3'),
        tell('[Phase 4 / absent]', 'phase4'), tell('[Daytime]', 'day'), tell('[Midnight]', 'night'),
        tell('[Preview again]', 'preview'), tell('[Natural schedule]', 'natural'), tell('[Read status]', 'read'),
        tell('Auroras occupy the northern sky. Compare clouds in Video Settings while looking across real terrain.'), 'return 1'])
    # Selected from this fixed-seed lab world's production schedule (day 6).
    # One-use control: never restart a live/unknown event merely for evidence.
    result['natural_cycle'] = '\n'.join([GUARD,
        'execute unless entity @s[tag=aurora_lab] run return 0',
        'execute if score #natural_cycle aurora matches 1.. run return 0',
        'scoreboard players set #natural_cycle aurora 1',
        'gamerule doDaylightCycle false', 'fd world set phase 1',
        'time set 158180', 'weather clear 1000000', 'fdlab aurora natural',
        'gamerule doDaylightCycle true',
        tell('Natural schedule review: normal clock, preview OFF. Watch the northern sky; no teleport.'), 'return 1'])
    # A distinct persistence attempt on day 7, after the day-6 view was interrupted.
    result['reload_cycle'] = '\n'.join([GUARD,
        'execute unless entity @s[tag=aurora_lab] run return 0',
        'execute unless score #natural_cycle aurora matches 1 run return 0',
        'scoreboard players set #natural_cycle aurora 2',
        'gamerule doDaylightCycle false', 'fd world set phase 1',
        'time set 183263', 'weather clear 1000000', 'fdlab aurora natural',
        'gamerule doDaylightCycle true',
        tell('Save/reload review: natural day-7 event in progress, normal clock, preview OFF. Look north.'), 'return 1'])
    return result


def prepare_world(source, destination):
    root = Path(__file__).resolve().parents[1]
    config = root / 'run-lab/config/frozendawn-common.toml'
    if not config.exists() or not tomllib.loads(config.read_text()).get('general', {}).get('pauseProgression', False):
        raise SystemExit('Requires the existing aurora checkout with paused progression; config is never replaced')
    with (source.parent / 'session.lock').open('rb') as lock:
        fcntl.lockf(lock, fcntl.LOCK_SH | fcntl.LOCK_NB)
        prepare(source, destination, title=TITLE, namespace=NS, pack='aurora-terrain',
                build_scripts=scripts, terrain=True, structures=True)
    path = destination / 'level.dat'
    stream = io.BytesIO(gzip.decompress(path.read_bytes()))
    assert stream.read(1) == b'\x0a'
    name = nbt.string(stream)
    root_tag = nbt.read(stream, 10)
    root_tag['Data'][1].update({'DayTime': (4, 18_000), 'initialized': (1, 0), 'DragonFight': (10, {}),
                              'confirmedExperimentalSettings': (1, 1), 'DifficultyLocked': (1, 1)})
    path.write_bytes(gzip.compress(b'\x0a' + nbt.utf(name) + nbt.write(10, root_tag)))
    report = destination / 'preparation.json'
    data = json.loads(report.read_text())
    data.update({'terrain': 'normal Overworld noise generator', 'placed_fixtures': 0,
                 'apocalypseTicks': 0, 'dayTime': 18_000, 'natural_aurora_occurrence': False})
    report.write_text(json.dumps(data, indent=2) + '\n')


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

#!/usr/bin/env python3
"""Fresh Payoff Reveal Check: the CH-18 line, the manual's changes, the opening replay and the respawn boot cue.

A flat Survival lab in Phase 0, with the world clock paused. The world is new, so the payoff can fire in it. Buttons
give the manual, fell the Heart (the saved erased flag), die, toggle immediate respawn for the flicker, and clear the
payoff so the sequence can run again. The payoff's advancement is carried in this world's pack, since the advancement
filter hides the mod's own. The difficulty is locked. Never overwrites a save.
Usage (lab client, run-lab/ directory):
  python3 tools/prepare_payoff_reveal_playtest.py --source <any closed level.dat> --destination "run-lab/saves/Payoff Reveal Check"
"""
import argparse
import fcntl
import gzip
import io
import json
import shutil
from pathlib import Path

import prepare_blind_architect_playtest as nbt
from prepare_power_network_playtest import prepare, pack_only
from prepare_terminus_vault_playtest import ADVANCEMENTS

TITLE = 'Payoff Reveal Check'
NS = 'payoff_reveal_check'
PACK = 'payoff-reveal-check'
OBJECTIVE = 'payoffreveal'
GUARD = f'execute unless score #built {OBJECTIVE} matches 1 run return 0'
MANUAL = 'give @s patchouli:guide_book[patchouli:book="frozendawn:frozen_dawn_guide"]'


def tell(text, command=None, color='aqua'):
    value = {'text': text, 'color': color}
    if command:
        value['clickEvent'] = {'action': 'run_command', 'value': command}
    return 'tellraw @s '+json.dumps(value)


def scripts():
    setup = [f'execute unless score #built {OBJECTIVE} matches -1 run return 0',
             f'scoreboard players set #built {OBJECTIVE} -2', 'gamemode survival @s',
             'gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false',
             'gamerule doFireTick false', 'gamerule keepInventory true', 'gamerule doImmediateRespawn false',
             'gamerule commandBlockOutput false', 'gamerule announceAdvancements false',
             'time set 6000', 'weather clear', 'fd world pause',
             'fill -16 64 -16 16 64 16 minecraft:polished_deepslate',
             'setworldspawn 0 65 0', 'spawnpoint @s 0 65 0', 'tp @s 0.5 65 0.5 180 0',
             f'scoreboard players set #built {OBJECTIVE} 1', 'function '+NS+':controls', 'return 1']
    controls = [GUARD, tell('PAYOFF REVEAL CHECK: read the manual first, fell the Heart, then die.'),
                tell('[Give manual]', '/function '+NS+':manual', 'green'),
                tell('[Heart fallen]', '/fd postmaeve set-erased', 'yellow'),
                tell('[Die]', '/function '+NS+':die', 'red'),
                tell('[Immediate respawn on]', '/gamerule doImmediateRespawn true', 'gold'),
                tell('[Immediate respawn off]', '/gamerule doImmediateRespawn false', 'gold'),
                tell('[Reset payoff]', '/fd payoff reset', 'light_purple'),
                tell('[Play the opening]', '/fd payoff opening', 'aqua'),
                tell('[Status]', '/fd payoff status', 'gray'),
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
        'die': '\n'.join([GUARD, 'kill @s', 'return 1'])}


def prepare_world(source, destination):
    with (source.parent/'session.lock').open('rb') as lock:
        fcntl.lockf(lock, fcntl.LOCK_SH | fcntl.LOCK_NB)
        prepare(source, destination, title=TITLE, namespace=NS, pack=PACK, build_scripts=scripts, surface=128, structures=False)
    # The filter hides the mod's advancements; the pages unlock on this one, so the world's pack carries it.
    out = destination/'datapacks'/PACK/'data/frozendawn/advancement'
    out.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(ADVANCEMENTS/'payoff_fired.json', out/'payoff_fired.json')
    level = destination/'level.dat'
    stream = io.BytesIO(gzip.decompress(level.read_bytes()))
    assert stream.read(1) == b'\x0a'
    nbt.string(stream)
    root = nbt.read(stream, 10)
    # Time stays 0: the payoff flag is created on first start, and only a world that young is eligible.
    assert root['Data'][1]['Time'] == (4, 0)
    root['Data'][1].update({'DifficultyLocked': (1, 1), 'GameType': (3, 0), 'DragonFight': (10, {})})
    level.write_bytes(gzip.compress(b'\x0a'+nbt.utf('')+nbt.write(10, root)))
    report = destination/'preparation.json'
    info = json.loads(report.read_text())
    info.update({'mode': 'Survival', 'phase': '0, paused', 'payoff': 'eligible (new world)',
                 'fixtures': 'buttons for the manual, the Heart, a death, immediate respawn, a payoff reset, the opening and status'})
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

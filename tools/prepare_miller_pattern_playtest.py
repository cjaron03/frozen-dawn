#!/usr/bin/env python3
"""Fresh Aurora Feelings Check: her resting face, and her idle life on every mood.

A flat lab in Survival with a dock and a charged cell in the hotbar. Wake her and finish the first meeting, then the
buttons send each deed through the real door, or hold one feeling so a face can be watched for as long as it takes.
Advancements are filtered as in every generated playtest world, and the difficulty is locked. Never overwrites a save.
Usage (lab client, run-lab/ directory):
  python3 tools/prepare_miller_pattern_playtest.py --source <any closed level.dat> --destination "run-lab/saves/Aurora Feelings Check"
"""
import argparse
import fcntl
import gzip
import io
import json
from pathlib import Path

import prepare_blind_architect_playtest as nbt
from prepare_power_network_playtest import prepare, pack_only
from prepare_terminus_vault_playtest import document_advancements

TITLE = 'Aurora Feelings Check'
NS = 'aurora_feelings_check'
PACK = 'aurora-feelings-check'
OBJECTIVE = 'aurorafeelings'
GUARD = f'execute unless score #built {OBJECTIVE} matches 1 run return 0'
CELL = 'item replace entity @s hotbar.1 with frozendawn:power_cell[frozendawn:cell_joules=500000]'
DEEDS = ['warm_shelter', 'read_log', 'clear_night', 'archivist_killed', 'shelter_froze', 'air_failing', 'left_behind', 'called_shepherd']
FEELINGS = ['joy', 'distress', 'hope', 'fear', 'gratitude', 'reproach', 'guilt']


def tell(text, function=None, color='aqua', command=None):
    value = {'text': text, 'color': color}
    if function:
        value['clickEvent'] = {'action': 'run_command', 'value': '/function '+NS+':'+function}
    if command:
        value['clickEvent'] = {'action': 'run_command', 'value': command}
    return 'tellraw @s '+json.dumps(value)


def scripts():
    setup = [f'execute unless score #built {OBJECTIVE} matches -1 run return 0',
             f'scoreboard players set #built {OBJECTIVE} -2', 'gamemode survival @s',
             'gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false',
             'gamerule doFireTick false', 'gamerule mobGriefing false', 'gamerule keepInventory true', 'gamerule sendCommandFeedback false',
             'gamerule commandBlockOutput false', 'gamerule announceAdvancements false',
             'difficulty normal', 'time set noon', 'weather clear', 'clear @s', 'fd world pause',
             'item replace entity @s hotbar.0 with minecraft:cooked_beef 32', CELL,
             'fill -16 64 -12 16 64 12 minecraft:polished_deepslate', 'setblock 0 65 -4 frozendawn:cs1_dock[facing=south]',
             'setworldspawn 0 65 3', 'spawnpoint @s 0 65 3', 'tp @s 0.5 65 3.5 180 0',
             f'scoreboard players set #built {OBJECTIVE} 1', 'function '+NS+':controls', 'return 1']
    controls = [GUARD, tell('AURORA FEELINGS CHECK: wake her with the cell and finish the first meeting. A deed plays her reaction, then she rests on what she feels.')]
    controls += [tell(f'[{deed}]', command='/fd shepherd saw '+deed) for deed in DEEDS]
    controls += [tell(f'[Hold {feeling}]', command='/fd shepherd feel '+feeling, color='green') for feeling in FEELINGS]
    controls += [tell('[Skip a day]', command='/fd shepherd skipday'), tell('[Score -300]', command='/fd shepherd affinity -300'), tell('[Score 0]', command='/fd shepherd affinity 0'),
                 tell('[Why]', command='/fd shepherd why', color='gray'), tell('[State]', command='/fd shepherd state', color='gray'),
                 tell('[Replay first meeting]', command='/fd shepherd replay', color='yellow'), 'return 1']
    return {
        'load': f'scoreboard objectives add {OBJECTIVE} dummy\nexecute unless score #built {OBJECTIVE} matches -2..1 run scoreboard players set #built {OBJECTIVE} 0\nexecute if score #built {OBJECTIVE} matches -2..-1 run scoreboard players set #built {OBJECTIVE} 0',
        'bootstrap': '\n'.join([f'execute unless score #built {OBJECTIVE} matches 0 run return 0',
                               'forceload add -32 -16 32 16', f'scoreboard players set #built {OBJECTIVE} -1', 'return 1']),
        'tick': '\n'.join([f'execute if score #built {OBJECTIVE} matches 0 as @a[limit=1] run function '+NS+':bootstrap',
                          f'execute if score #built {OBJECTIVE} matches -1 if loaded -32 65 -16 if loaded 32 65 16 as @a[limit=1] run function '+NS+':setup']),
        'setup': '\n'.join(setup),
        'controls': '\n'.join(controls)}


def prepare_world(source, destination):
    with (source.parent/'session.lock').open('rb') as lock:
        fcntl.lockf(lock, fcntl.LOCK_SH | fcntl.LOCK_NB)
        prepare(source, destination, title=TITLE, namespace=NS, pack=PACK, build_scripts=scripts, surface=128, structures=False)
    document_advancements(destination/'datapacks'/PACK)
    level = destination/'level.dat'
    stream = io.BytesIO(gzip.decompress(level.read_bytes()))
    assert stream.read(1) == b'\x0a'
    nbt.string(stream)
    root = nbt.read(stream, 10)
    root['Data'][1].update({'DifficultyLocked': (1, 1), 'DragonFight': (10, {})})
    level.write_bytes(gzip.compress(b'\x0a'+nbt.utf('')+nbt.write(10, root)))
    report = destination/'preparation.json'
    info = json.loads(report.read_text())
    info.update({'mode': 'Survival', 'fixtures': 'a dock, a charged cell, a button for each deed and each feeling'})
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

#!/usr/bin/env python3
"""Fresh Aurora Advice Check: her advice, the Returned's holograms, her new faces and her small moments.

A flat lab in Survival with a dock, cells in one chest and a fighting kit in another. Boot her, finish the first meeting, then talk.
The buttons set her tier, move her clocks, forget the advice and spawn each mob, so her strategies can be tried on a live one.
Nothing here names a line to type: what the owner types first is the blind column in lab-bridge/aurora-typed.jsonl.
Advancements are filtered as in every generated playtest world, and the difficulty is locked. Never overwrites a save.
Usage (lab client, run-lab/ directory):
  python3 tools/prepare_miller_m6_playtest.py --source <any closed level.dat> --destination "run-lab/saves/Aurora Advice Check"
"""
import argparse
import fcntl
import gzip
import io
import json
from pathlib import Path

import prepare_blind_architect_playtest as nbt
from prepare_power_network_playtest import prepare, pack_only

TITLE = 'Aurora Advice Check'
NS = 'aurora_advice_check'
PACK = 'aurora-advice-check'
OBJECTIVE = 'auroraadvice'
GUARD = f'execute unless score #built {OBJECTIVE} matches 1 run return 0'
CELLS = (3, 65, -4)
KIT = (-3, 65, -4)
CHARGED = 'frozendawn:power_cell[frozendawn:cell_joules=500000]'
EMPTY = 'frozendawn:power_cell'
KIT_ITEMS = ('frozendawn:thermal_heater 4', 'minecraft:coal_block 8', 'frozendawn:frost_ward_torch 16', 'frozendawn:miteaway 4', 'frozendawn:acheronite_sword',
             'minecraft:bow', 'minecraft:arrow 64', 'minecraft:flint_and_steel', 'minecraft:polished_deepslate 64', 'minecraft:polished_deepslate 64')
MOBS = ('frostbitten', 'frostmite', 'hollow', 'returned', 'mimic', 'architect')


def tell(text, command=None, color='aqua'):
    value = {'text': text, 'color': color}
    if command:
        value['clickEvent'] = {'action': 'run_command', 'value': command}
    return 'tellraw @s '+json.dumps(value)


def scripts():
    setup = [f'execute unless score #built {OBJECTIVE} matches -1 run return 0',
             f'scoreboard players set #built {OBJECTIVE} -2', 'gamemode survival @s',
             'gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false',
             'gamerule doFireTick false', 'gamerule keepInventory true',
             'gamerule commandBlockOutput false', 'gamerule announceAdvancements false',
             'difficulty normal', 'time set 6000', 'weather clear', 'clear @s', 'fd world pause',
             'item replace entity @s hotbar.0 with minecraft:cooked_beef 32',
             'fill -32 64 -32 32 64 32 minecraft:polished_deepslate', 'setblock 0 65 -4 frozendawn:cs1_dock[facing=south]',
             'setblock {} {} {} minecraft:chest[facing=south]'.format(*CELLS),
             *['item replace block {} {} {} container.{} with {}'.format(*CELLS, slot, cell) for slot, cell in enumerate([CHARGED, CHARGED, EMPTY])],
             'setblock {} {} {} minecraft:chest[facing=south]'.format(*KIT),
             *['item replace block {} {} {} container.{} with {}'.format(*KIT, slot, item) for slot, item in enumerate(KIT_ITEMS)],
             'setworldspawn 0 65 3', 'spawnpoint @s 0 65 3', 'tp @s 0.5 65 3.5 180 0',
             f'scoreboard players set #built {OBJECTIVE} 1', 'function '+NS+':controls', 'return 1']
    controls = [GUARD, tell('AURORA ADVICE CHECK: cells in the right chest, a fighting kit in the left. Boot her, finish the first meeting, then talk to her.'),
                tell('[Friendly]', '/fd shepherd affinity 260', 'green'), tell('[Trusted]', '/fd shepherd affinity 510', 'green'), tell('[Close]', '/fd shepherd affinity 860', 'green'),
                tell('[Wary]', '/fd shepherd affinity -300', 'red'),
                tell('[A day on]', '/fd shepherd skipday', 'yellow'), tell('[Long gone]', '/function '+NS+':long_gone', 'yellow'),
                tell('[Erased]', '/fdlab advice erased', 'yellow'), tell('[After T5]', '/fdlab advice aftert5', 'yellow'),
                tell('[Forget the advice]', '/fdlab advice forget', 'light_purple'),
                *[tell(f'[{mob.capitalize()}]', f'/summon frozendawn:{mob} ~6 ~ ~', 'gold') for mob in MOBS], 'return 1']
    return {
        'load': f'scoreboard objectives add {OBJECTIVE} dummy\nexecute unless score #built {OBJECTIVE} matches -2..1 run scoreboard players set #built {OBJECTIVE} 0\nexecute if score #built {OBJECTIVE} matches -2..-1 run scoreboard players set #built {OBJECTIVE} 0',
        'bootstrap': '\n'.join([f'execute unless score #built {OBJECTIVE} matches 0 run return 0',
                               'forceload add -48 -48 48 48', f'scoreboard players set #built {OBJECTIVE} -1', 'return 1']),
        'tick': '\n'.join([f'execute if score #built {OBJECTIVE} matches 0 as @a[limit=1] run function '+NS+':bootstrap',
                          f'execute if score #built {OBJECTIVE} matches -1 if loaded -48 65 -48 if loaded 48 65 48 as @a[limit=1] run function '+NS+':setup']),
        'setup': '\n'.join(setup),
        'controls': '\n'.join(controls),
        'long_gone': '\n'.join(['fd shepherd skipday']*3)}


def prepare_world(source, destination):
    with (source.parent/'session.lock').open('rb') as lock:
        fcntl.lockf(lock, fcntl.LOCK_SH | fcntl.LOCK_NB)
        prepare(source, destination, title=TITLE, namespace=NS, pack=PACK, build_scripts=scripts, surface=128, structures=False)
    level = destination/'level.dat'
    stream = io.BytesIO(gzip.decompress(level.read_bytes()))
    assert stream.read(1) == b'\x0a'
    nbt.string(stream)
    root = nbt.read(stream, 10)
    root['Data'][1].update({'DifficultyLocked': (1, 1), 'DragonFight': (10, {})})
    level.write_bytes(gzip.compress(b'\x0a'+nbt.utf('')+nbt.write(10, root)))
    report = destination/'preparation.json'
    info = json.loads(report.read_text())
    info.update({'mode': 'Survival', 'fixtures': 'a dock, cells and a fighting kit in chests, and buttons for her tier, her clocks, forgetting and each mob'})
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

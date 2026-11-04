#!/usr/bin/env python3
"""Fresh Shepherd Check: the owner wakes Aurora and walks her around a flat lab.
Three CS-1 docks stand north of the spawn, with a chest of charged cells and one empty cell beside them. A long strip
runs east, through a dark corridor and past the 24-block teleport line. Creative, mobs off, day fixed, world paused.
Never overwrites an existing save. Usage (lab client, run-lab/ directory):
  python3 tools/prepare_shepherd_playtest.py --source <any level.dat> --destination "run-lab/saves/Shepherd Check"
"""
import argparse
import json
from pathlib import Path

from prepare_power_network_playtest import prepare, pack_only

TITLE = 'Shepherd Check'
NS = 'shepherd_check'
PACK = 'shepherd-check'
GUARD = 'execute unless score #built shepherdcheck matches 1 run return 0'
BASE = 'tp @s 0.5 65 3.5 180 10'
UNIT = 'frozendawn:cs1_unit'
DOCKS = [(-4, 65, -3), (0, 65, -3), (4, 65, -3)]
CHEST = (8, 65, -3)
CELL = '500000'


def tell(text, function=None, color='aqua', command=None):
    value = {'text': text, 'color': color}
    if function:
        value['clickEvent'] = {'action': 'run_command', 'value': '/function ' + NS + ':' + function}
    if command:
        value['clickEvent'] = {'action': 'run_command', 'value': command}
    return 'tellraw @s ' + json.dumps(value)


def chest():
    charged = '{Slot:%db,id:"frozendawn:power_cell",count:1,components:{"frozendawn:cell_joules":' + CELL + '}}'
    empty = '{Slot:4b,id:"frozendawn:power_cell",count:1,components:{"frozendawn:cell_joules":0}}'
    items = ','.join([charged % slot for slot in range(3)] + [empty])
    return 'setblock %d %d %d minecraft:chest[facing=south]{Items:[%s]}' % (*CHEST, items)


def site():
    return (['fill -12 64 -10 72 64 12 minecraft:polished_deepslate',
             # The strip east: a stripe to pace out, and a corridor with a roof, dark at night and tight for her path.
             'fill 12 64 3 70 64 3 minecraft:cyan_terracotta',
             'fill 24 65 1 34 68 5 minecraft:stone_bricks', 'fill 24 65 2 34 67 4 minecraft:air',
             'fill 44 65 -1 44 67 7 minecraft:stone_bricks']
            + ['setblock %d %d %d frozendawn:cs1_dock[facing=south]' % dock for dock in DOCKS]
            + [chest()])


def scripts():
    setup = ['execute unless score #built shepherdcheck matches 0 run return 0',
             'scoreboard players set #built shepherdcheck -1', 'gamemode creative @s',
             'gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false',
             'gamerule doFireTick false', 'gamerule keepInventory true', 'gamerule announceAdvancements false',
             'time set noon', 'weather clear', 'fd world pause', 'forceload add -16 -16 80 16']
    setup += site()
    setup += ['setworldspawn 0 65 3', 'spawnpoint @s 0 65 3', BASE,
              'scoreboard players set #built shepherdcheck 1', 'function ' + NS + ':controls', 'return 1']
    return {
        'load': 'scoreboard objectives add shepherdcheck dummy\nexecute unless score #built shepherdcheck matches -1..1 run scoreboard players set #built shepherdcheck 0\nexecute if score #built shepherdcheck matches 1 as @a run function ' + NS + ':controls',
        'tick': 'execute if score #built shepherdcheck matches 0 as @a[limit=1] run function ' + NS + ':setup',
        'setup': '\n'.join(setup),
        'controls': '\n'.join([GUARD,
            tell('SHEPHERD CHECK - Creative. Take a cell from the chest at the right and use it on a dock. The fifth slot holds an empty cell.'),
            tell('[Offline: /kill her]', 'offline'), tell('[Drop her in the void]', 'void'), tell('[Night]', 'night'), tell('[Day]', 'day'),
            tell('[Survival]', 'survival'), tell('[Creative]', 'creative'), tell('[Spawn a zombie]', 'zombie'),
            tell('[Replay first meeting]', command='/fd shepherd replay', color='yellow'),
            tell('[Shepherd dump]', command='/fd shepherd dump', color='gray'), tell('[Base]', 'base', 'gray'), 'return 1']),
        'offline': '\n'.join([GUARD, 'kill @e[type=' + UNIT + ']', 'return 1']),
        'void': '\n'.join([GUARD, 'execute as @e[type=' + UNIT + '] at @s run tp @s ~ -200 ~', 'return 1']),
        'night': '\n'.join([GUARD, 'time set midnight', 'return 1']),
        'day': '\n'.join([GUARD, 'time set noon', 'return 1']),
        'survival': '\n'.join([GUARD, 'gamemode survival @s', 'return 1']),
        'creative': '\n'.join([GUARD, 'gamemode creative @s', 'return 1']),
        'zombie': '\n'.join([GUARD, 'execute at @s run summon minecraft:zombie ^ ^ ^10', 'return 1']),
        'base': '\n'.join([GUARD, BASE, 'return 1'])}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source', type=Path); p.add_argument('--destination', type=Path); p.add_argument('--pack-only', type=Path)
    a = p.parse_args()
    if a.pack_only:
        pack_only(a.pack_only, title=TITLE, namespace=NS, build_scripts=scripts); return
    if not (a.source and a.destination):
        p.error('Use --source/--destination or --pack-only')
    prepare(a.source, a.destination, title=TITLE, namespace=NS, pack=PACK, build_scripts=scripts, surface=128, structures=False)


if __name__ == '__main__':
    main()

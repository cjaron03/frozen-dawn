#!/usr/bin/env python3
"""Fresh Aurora Boot Check: the boot sequence, and how she reads when she is slow.

A flat lab in Survival with a dock, and charged and empty cells in a chest. The buttons replay the boot, set her
coherence, skip a day, read what she holds, and bring trouble while the choice or the lines are up. Advancements are
filtered as in every generated playtest world, and the difficulty is locked. Never overwrites a save.
Usage (lab client, run-lab/ directory):
  python3 tools/prepare_miller_m2_playtest.py --source <any closed level.dat> --destination "run-lab/saves/Aurora Boot Check"
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

TITLE = 'Aurora Boot Check'
NS = 'aurora_boot_check'
PACK = 'aurora-boot-check'
OBJECTIVE = 'auroraboot'
GUARD = f'execute unless score #built {OBJECTIVE} matches 1 run return 0'
CHEST = (3, 65, -4)
TROUBLE_DELAY = '60t'
CHARGED = 'frozendawn:power_cell[frozendawn:cell_joules=500000]'
EMPTY = 'frozendawn:power_cell'
# A helmet, so a zombie lasts long enough at noon to reach the player.
HELMET = '{ArmorItems:[{},{},{},{id:"minecraft:iron_helmet",count:1}]}'


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
             'gamerule doFireTick false', 'gamerule mobGriefing false', 'gamerule keepInventory true',
             'gamerule commandBlockOutput false', 'gamerule announceAdvancements false',
             'difficulty normal', 'time set noon', 'weather clear', 'clear @s', 'fd world pause',
             'item replace entity @s hotbar.0 with minecraft:cooked_beef 32',
             'fill -16 64 -12 16 64 12 minecraft:polished_deepslate', 'setblock 0 65 -4 frozendawn:cs1_dock[facing=south]',
             'setblock {} {} {} minecraft:chest[facing=south]'.format(*CHEST),
             *['item replace block {} {} {} container.{} with {}'.format(*CHEST, slot, cell) for slot, cell in enumerate([CHARGED, CHARGED, EMPTY])],
             'setworldspawn 0 65 3', 'spawnpoint @s 0 65 3', 'tp @s 0.5 65 3.5 180 0',
             f'scoreboard players set #built {OBJECTIVE} 1', 'function '+NS+':controls', 'return 1']
    controls = [GUARD, tell('AURORA BOOT CHECK: the cells are in the chest beside the dock. Hurt me and Hostile land 3 seconds after you click them.'),
                tell('[Replay]', command='/fd shepherd replay', color='yellow'),
                tell('[Coherence 1]', command='/fd shepherd coherence 1', color='green'), tell('[Coherence 0.3]', command='/fd shepherd coherence 0.3', color='green'),
                tell('[Skip a day]', command='/fd shepherd skipday'), tell('[Hurt me]', 'hurt', 'red'), tell('[Hostile]', 'hostile', 'red'),
                tell('[Mind]', command='/fd shepherd mind', color='gray'), tell('[State]', command='/fd shepherd state', color='gray'), 'return 1']
    return {
        'load': f'scoreboard objectives add {OBJECTIVE} dummy\nexecute unless score #built {OBJECTIVE} matches -2..1 run scoreboard players set #built {OBJECTIVE} 0\nexecute if score #built {OBJECTIVE} matches -2..-1 run scoreboard players set #built {OBJECTIVE} 0',
        'bootstrap': '\n'.join([f'execute unless score #built {OBJECTIVE} matches 0 run return 0',
                               'forceload add -32 -16 32 16', f'scoreboard players set #built {OBJECTIVE} -1', 'return 1']),
        'tick': '\n'.join([f'execute if score #built {OBJECTIVE} matches 0 as @a[limit=1] run function '+NS+':bootstrap',
                          f'execute if score #built {OBJECTIVE} matches -1 if loaded -32 65 -16 if loaded 32 65 16 as @a[limit=1] run function '+NS+':setup']),
        'setup': '\n'.join(setup),
        'controls': '\n'.join(controls),
        # The chat cannot be clicked with her screen up, so trouble is set going first and lands while it shows.
        'hurt': '\n'.join([GUARD, f'schedule function {NS}:hurt_now {TROUBLE_DELAY}', 'return 1']),
        'hurt_now': 'execute as @a run damage @s 2 minecraft:generic',
        'hostile': '\n'.join([GUARD, f'schedule function {NS}:hostile_now {TROUBLE_DELAY}', 'return 1']),
        'hostile_now': f'execute as @a at @s run summon minecraft:zombie ^ ^ ^6 {HELMET}'}


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
    info.update({'mode': 'Survival', 'fixtures': 'a dock, charged and empty cells in a chest, buttons for replay, coherence, a skipped day and trouble'})
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

#!/usr/bin/env python3
"""Fresh Aurora Chat Check: typing to her, the five ORSA topics, small talk, and the crash.

A flat lab in Survival with a dock, and charged and empty cells in a chest. Boot her, finish the first meeting, then talk.
The buttons set her tier, raise the scenes that typing has to survive, and forget the crash and the topics so each can be
seen new. Nothing here names a line to type: what the owner types first is the blind column in lab-bridge/aurora-typed.jsonl.
Advancements are filtered as in every generated playtest world, and the difficulty is locked. Never overwrites a save.
Usage (lab client, run-lab/ directory):
  python3 tools/prepare_miller_chat_playtest.py --source <any closed level.dat> --destination "run-lab/saves/Aurora Chat Check"
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

TITLE = 'Aurora Chat Check'
NS = 'aurora_chat_check'
PACK = 'aurora-chat-check'
OBJECTIVE = 'aurorachat'
GUARD = f'execute unless score #built {OBJECTIVE} matches 1 run return 0'
CHEST = (3, 65, -4)
CHARGED = 'frozendawn:power_cell[frozendawn:cell_joules=500000]'
EMPTY = 'frozendawn:power_cell'


def tell(text, command=None, color='aqua'):
    value = {'text': text, 'color': color}
    if command:
        value['clickEvent'] = {'action': 'run_command', 'value': command}
    return 'tellraw @s '+json.dumps(value)


def scripts():
    setup = [f'execute unless score #built {OBJECTIVE} matches -1 run return 0',
             f'scoreboard players set #built {OBJECTIVE} -2', 'gamemode survival @s',
             'gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false',
             'gamerule doFireTick false', 'gamerule mobGriefing false', 'gamerule keepInventory true',
             'gamerule commandBlockOutput false', 'gamerule announceAdvancements false',
             'difficulty normal', 'time set 6000', 'weather clear', 'clear @s', 'fd world pause',
             'item replace entity @s hotbar.0 with minecraft:cooked_beef 32',
             'fill -16 64 -12 16 64 12 minecraft:polished_deepslate', 'setblock 0 65 -4 frozendawn:cs1_dock[facing=south]',
             'setblock {} {} {} minecraft:chest[facing=south]'.format(*CHEST),
             *['item replace block {} {} {} container.{} with {}'.format(*CHEST, slot, cell) for slot, cell in enumerate([CHARGED, CHARGED, EMPTY])],
             'setworldspawn 0 65 3', 'spawnpoint @s 0 65 3', 'tp @s 0.5 65 3.5 180 0',
             f'scoreboard players set #built {OBJECTIVE} 1', 'function '+NS+':controls', 'return 1']
    controls = [GUARD, tell('AURORA CHAT CHECK: the cells are in the chest beside the dock. Boot her, finish the first meeting, then talk to her.'),
                tell('[Friendly]', '/fd shepherd affinity 260', 'green'), tell('[Trusted]', '/fd shepherd affinity 510', 'green'),
                tell('[Wary]', '/fd shepherd affinity -300', 'red'), tell('[Silent]', '/fd shepherd affinity -610', 'red'),
                tell('[Hear T2]', '/fdlab chat t2', 'yellow'), tell('[Hear T4]', '/fdlab chat t4', 'yellow'),
                tell('[Forget the crash]', '/fdlab chat forgetcrash', 'light_purple'), tell('[Forget the topics]', '/fdlab chat forgettopics', 'light_purple'), 'return 1']
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
    info.update({'mode': 'Survival', 'fixtures': 'a dock, charged and empty cells in a chest, and buttons for her tier, two scenes and forgetting'})
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

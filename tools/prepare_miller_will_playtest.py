#!/usr/bin/env python3
"""Fresh Aurora Will Check: what she says unasked, what she refuses, and the recording cutting in.

A flat lab in Survival with a dock, and charged and empty cells in a chest. Boot her, then the buttons want the sky,
make the night clear, bring an Archivist, take the score under the line, make a keepsake due, find each wall and call
the slip. Advancements are filtered as in every generated playtest world, and the difficulty is locked. Never
overwrites a save.
Usage (lab client, run-lab/ directory):
  python3 tools/prepare_miller_will_playtest.py --source <any closed level.dat> --destination "run-lab/saves/Aurora Will Check"
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

TITLE = 'Aurora Will Check'
NS = 'aurora_will_check'
PACK = 'aurora-will-check'
OBJECTIVE = 'aurorawill'
GUARD = f'execute unless score #built {OBJECTIVE} matches 1 run return 0'
CHEST = (3, 65, -4)
TROUBLE_DELAY = '60t'
CHARGED = 'frozendawn:power_cell[frozendawn:cell_joules=500000]'
EMPTY = 'frozendawn:power_cell'
WALLS = ['lagged', 'legible', 'exploitable', 'fallible', 'bounded', 'causal']
# With nothing else wanted, what is on her mind is what she acts on.
READY = ['fd shepherd want curiosity 0', 'fd shepherd want company 0', 'fd shepherd resetgap']
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
    controls = [GUARD, tell('AURORA WILL CHECK: the cells are in the chest beside the dock. Boot her, finish the first meeting, then try these. Tell her to Wait before asking her to Follow. Hurt me and Hostile land 3 seconds after you click them.'),
                tell('[Sky 1]', command='/fd shepherd want sky 1', color='yellow'), tell('[Clear night]', 'clear_night', 'yellow'), tell('[Noon]', 'noon', 'yellow'),
                tell('[Archivist]', 'archivist', 'yellow'), tell('[Score -601]', command='/fd shepherd affinity -601', color='red'), tell('[Score 0]', command='/fd shepherd affinity 0', color='green'),
                tell('[Slip now]', command='/fd shepherd slip', color='light_purple'), tell('[Keepsake due]', 'keepsake_due', 'light_purple'),
                *[tell('[Wall found: '+wall+']', 'wall_'+wall, 'light_purple') for wall in WALLS],
                tell('[Coherence 1]', command='/fd shepherd coherence 1', color='green'), tell('[Coherence 0.3]', command='/fd shepherd coherence 0.3', color='green'),
                tell('[Skip a day]', command='/fd shepherd skipday'), tell('[Replay]', command='/fd shepherd replay'), tell('[Hurt me]', 'hurt', 'red'), tell('[Hostile]', 'hostile', 'red'),
                tell('[Why]', command='/fd shepherd why', color='gray'), tell('[Mind]', command='/fd shepherd mind', color='gray'), tell('[State]', command='/fd shepherd state', color='gray'), 'return 1']
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
        'hostile_now': f'execute as @a at @s run summon minecraft:zombie ^ ^ ^6 {HELMET}',
        'clear_night': '\n'.join([GUARD, 'weather clear', 'time set 18000', 'return 1']),
        'noon': '\n'.join([GUARD, 'time set noon', 'return 1']),
        # The memory of killing one, and one standing there. It waits, so she has it in sight when Follow is pressed.
        'archivist': '\n'.join([GUARD, 'fd shepherd saw archivist_killed', 'execute at @s run summon frozendawn:archivist ^ ^ ^5 {NoAI:1b}', 'return 1']),
        'keepsake_due': '\n'.join([GUARD, 'fd shepherd skipday', *READY, 'return 1']),
        **{'wall_'+wall: '\n'.join([GUARD, 'fd shepherd witness '+wall, *READY, 'return 1']) for wall in WALLS}}


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
    info.update({'mode': 'Survival', 'fixtures': 'a dock, charged and empty cells in a chest, buttons for the sky, a clear night, an Archivist, the score, a due keepsake, each wall, the slip, coherence and trouble'})
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

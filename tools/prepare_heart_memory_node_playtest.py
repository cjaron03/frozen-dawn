#!/usr/bin/env python3
"""Fresh Heart Memory Check: the Heart's last memory node read from the player's ledger, and the Successor's Kevin.

A flat Survival lab in Phase 2 with the world clock paused, as Returned Echo Check builds it: on first join the setup
force-selects the Hearths, matures the Major Hearth to INTACT, sets its Heart LIVE so the Heart's music plays, and
stands the player 24 blocks south of it, inside contact range, so the hive records the arrival. Buttons show node five
from the caller's own ledger or from the spec's three sample runs, break nodes to raise a Successor, give a sword,
bring a Returned close to strike, and die, so the ledger can be filled by play. Difficulty is locked and advancement
popups are off. Never overwrites a save.
Usage (lab client, run-lab/ directory):
  python3 tools/prepare_heart_memory_node_playtest.py --source <any closed level.dat> --destination "run-lab/saves/Heart Memory Check"
"""
import argparse
import fcntl
import gzip
import io
import json
from pathlib import Path

import prepare_blind_architect_playtest as nbt
from prepare_power_network_playtest import prepare, pack_only
from prepare_returned_respawn_echo_playtest import SEED, ANCHOR, SURFACE_Y, INTACT_TICKS, major_center, \
    install_advancements

TITLE = 'Heart Memory Check'
NS = 'heart_memory_check'
PACK = 'heart-memory-check'
OBJECTIVE = 'heartmemory'
GUARD = f'execute unless score #built {OBJECTIVE} matches 1 run return 0'
SPAWN_OFFSET = 24        # blocks south of the Hearth center: in contact range, in sight of the Heart


def tell(text, command=None, color='aqua'):
    value = {'text': text, 'color': color}
    if command:
        value['clickEvent'] = {'action': 'run_command', 'value': command}
    return 'tellraw @s '+json.dumps(value)


def scripts():
    cx, cz = major_center()
    sx, sz = cx, cz + SPAWN_OFFSET
    setup = [f'execute unless score #built {OBJECTIVE} matches -1 run return 0',
             f'scoreboard players set #built {OBJECTIVE} -2', 'gamemode survival @s',
             'gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false',
             'gamerule doFireTick false', 'gamerule keepInventory true', 'gamerule doImmediateRespawn false',
             'gamerule commandBlockOutput false', 'gamerule announceAdvancements false',
             # A Hearth builds only on caught-up chunks, and chunk catch-up starts in Phase 2.
             'time set 6000', 'weather clear', 'fd world set phase 2', 'fd world pause',
             'tp @s 0.5 65 0.5 180 0',
             'execute at @s run fd hearth force-select',
             f'fd hearth advance ticks {INTACT_TICKS}',
             'fd heart set-stage live',
             f'setworldspawn {sx} {SURFACE_Y} {sz}', f'spawnpoint @s {sx} {SURFACE_Y} {sz}',
             f'tp @s {sx + 0.5} {SURFACE_Y} {sz + 0.5} 180 0',
             f'scoreboard players set #built {OBJECTIVE} 1', 'function '+NS+':controls', 'return 1']
    controls = [GUARD, tell('HEART MEMORY CHECK: the Heart is north. Its music should be playing.'),
                tell('[Memory of you: my ledger]', '/fd heart memory5', 'aqua'),
                tell('[Sample: main run]', '/fd heart memory5 sample main', 'aqua'),
                tell('[Sample: peaceful run]', '/fd heart memory5 sample peaceful', 'aqua'),
                tell('[Sample: massacre run]', '/fd heart memory5 sample massacre', 'aqua'),
                tell('[Break nodes 1-3: a Successor]', '/function '+NS+':successor', 'light_purple'),
                tell('[Break node 4: the next Successor]', '/fd heart nodes destroy 4', 'light_purple'),
                tell('[Reset nodes]', '/fd heart nodes reset confirm', 'gray'),
                tell('[Sword]', '/function '+NS+':sword', 'gold'),
                tell('[Bring a Returned]', '/function '+NS+':returned', 'red'),
                tell('[Die]', '/function '+NS+':die', 'red'),
                tell('[Calm the Hearth]', '/fd hearth relationship set neutral', 'green'),
                tell('[Heart status]', '/fd heart status', 'gray'),
                tell('[Controls]', '/function '+NS+':controls', 'gray'), 'return 1']
    return {
        'load': f'scoreboard objectives add {OBJECTIVE} dummy\nexecute unless score #built {OBJECTIVE} matches -2..1 run scoreboard players set #built {OBJECTIVE} 0\nexecute if score #built {OBJECTIVE} matches -2..-1 run scoreboard players set #built {OBJECTIVE} 0',
        'bootstrap': '\n'.join([f'execute unless score #built {OBJECTIVE} matches 0 run return 0',
                               'forceload add -16 -16 16 16', f'scoreboard players set #built {OBJECTIVE} -1', 'return 1']),
        'tick': '\n'.join([f'execute if score #built {OBJECTIVE} matches 0 as @a[limit=1] run function '+NS+':bootstrap',
                          f'execute if score #built {OBJECTIVE} matches -1 if loaded -16 65 -16 if loaded 16 65 16 as @a[limit=1] run function '+NS+':setup',
                          f'execute if score #built {OBJECTIVE} matches 1 run forceload remove -16 -16 16 16']),
        'setup': '\n'.join(setup),
        'controls': '\n'.join(controls),
        'successor': '\n'.join([GUARD, 'fd heart nodes destroy 1', 'fd heart nodes destroy 2',
                                'fd heart nodes destroy 3', 'return 1']),
        'sword': '\n'.join([GUARD, 'give @s minecraft:netherite_sword', 'return 1']),
        # The nearest population Returned, four blocks in front, for the player to strike: a violation and, if it
        # dies, a casualty on the player's ledger.
        'returned': '\n'.join([GUARD, 'execute at @s as @e[type=frozendawn:returned,nbt={HearthPopulationRole:"returned"},'
                               'sort=nearest,limit=1] run tp @s ^ ^ ^4', 'return 1']),
        'die': '\n'.join([GUARD, 'kill @s', 'return 1'])}


def prepare_world(source, destination):
    with (source.parent/'session.lock').open('rb') as lock:
        fcntl.lockf(lock, fcntl.LOCK_SH | fcntl.LOCK_NB)
        prepare(source, destination, title=TITLE, namespace=NS, pack=PACK, build_scripts=scripts, surface=128, structures=False)
    install_advancements(destination)
    level = destination/'level.dat'
    stream = io.BytesIO(gzip.decompress(level.read_bytes()))
    assert stream.read(1) == b'\x0a'
    nbt.string(stream)
    root = nbt.read(stream, 10)
    assert root['Data'][1]['WorldGenSettings'][1]['seed'] == (4, SEED), 'The Hearth center assumes the lab seed'
    root['Data'][1].update({'DifficultyLocked': (1, 1), 'GameType': (3, 0), 'LevelName': (8, destination.name)})
    level.write_bytes(gzip.compress(b'\x0a'+nbt.utf('')+nbt.write(10, root)))
    cx, cz = major_center()
    report = destination/'preparation.json'
    info = json.loads(report.read_text())
    info.update({'mode': 'Survival', 'phase': '2, paused', 'majorHearthCenter': [cx, cz],
                 'spawn': [cx, SURFACE_Y, cz + SPAWN_OFFSET], 'anchor': list(ANCHOR),
                 'fixtures': 'force-selected INTACT Major Hearth with a LIVE Heart; buttons for node five from the '
                             'ledger and three samples, a Successor, a sword, a Returned, and a death'})
    report.write_text(json.dumps(info, indent=2)+'\n')
    print(destination.resolve(), 'Major Hearth center', (cx, cz))


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

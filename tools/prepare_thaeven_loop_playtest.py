#!/usr/bin/env python3
"""Fresh Thaeven Loop Check: CH-37's raw lines in the opening, Record I's loop page and its re-ink, and the Heart beats.

A flat Survival lab in Phase 0, with the world clock paused. Buttons play the opening, grant Record I, Book 2 and a
Thaeven Translator, re-ink Record I's source, run Book 2's lid reveal, and preview each Heart beat on the band. The
lab has no live Heart, so the takeover and arena triggers are covered by the GameTests; [Owe the broken lines] then
[Die] runs the real respawn path, so the broken lines land after the boot cue. The difficulty is locked. Never
overwrites a save.
Usage (lab client, run-lab/ directory):
  python3 tools/prepare_thaeven_loop_playtest.py --source <any closed level.dat> --destination "run-lab/saves/Thaeven Loop Check"
"""
import argparse
import fcntl
import gzip
import io
import json
from pathlib import Path

import prepare_blind_architect_playtest as nbt
from prepare_power_network_playtest import prepare, pack_only

TITLE = 'Thaeven Loop Check'
NS = 'thaeven_loop_check'
PACK = 'thaeven-loop-check'
OBJECTIVE = 'thaevenloop'
GUARD = f'execute unless score #built {OBJECTIVE} matches 1 run return 0'


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
    controls = [GUARD, tell('THAEVEN LOOP CHECK: the opening first, then Record I, then the Heart lines.'),
                tell('[Play the opening]', '/fd payoff opening', 'aqua'),
                tell('[Records and Translator]', '/function '+NS+':records', 'green'),
                tell('[Re-ink Record I]', '/fd lore unlock-semantic payoff_source', 'yellow'),
                tell('[Book 2 lid reveal]', '/fd lore unlock-semantic architect_lid_reveal', 'yellow'),
                tell('[Unread the re-inks]', '/fd lore reset-world-semantic confirm', 'gold'),
                tell('[Crossed]', '/fd lore loop play crossed', 'light_purple'),
                tell('[Crossed again]', '/fd lore loop play crossed_again', 'light_purple'),
                tell('[Body broke]', '/fd lore loop play body_broke', 'light_purple'),
                tell('[Returning]', '/fd lore loop play returning', 'light_purple'),
                tell('[Owe the broken lines]', '/fd lore loop owe-broken', 'red'),
                tell('[Die]', '/function '+NS+':die', 'red'),
                tell('[Loop status]', '/fd lore loop status', 'gray'),
                tell('[Loop reset]', '/fd lore loop reset confirm', 'gray'),
                tell('[Controls]', '/function '+NS+':controls', 'gray'), 'return 1']
    return {
        'load': f'scoreboard objectives add {OBJECTIVE} dummy\nexecute unless score #built {OBJECTIVE} matches -2..1 run scoreboard players set #built {OBJECTIVE} 0\nexecute if score #built {OBJECTIVE} matches -2..-1 run scoreboard players set #built {OBJECTIVE} 0',
        'bootstrap': '\n'.join([f'execute unless score #built {OBJECTIVE} matches 0 run return 0',
                               'forceload add -32 -32 32 32', f'scoreboard players set #built {OBJECTIVE} -1', 'return 1']),
        'tick': '\n'.join([f'execute if score #built {OBJECTIVE} matches 0 as @a[limit=1] run function '+NS+':bootstrap',
                          f'execute if score #built {OBJECTIVE} matches -1 if loaded -32 65 -32 if loaded 32 65 32 as @a[limit=1] run function '+NS+':setup']),
        'setup': '\n'.join(setup),
        'controls': '\n'.join(controls),
        'records': '\n'.join([GUARD, 'fd lore grant vel_an', 'fd lore grant the_passage',
                              'give @s frozendawn:thaeven_translator', 'return 1']),
        'die': '\n'.join([GUARD, 'kill @s', 'return 1'])}


def prepare_world(source, destination):
    with (source.parent/'session.lock').open('rb') as lock:
        fcntl.lockf(lock, fcntl.LOCK_SH | fcntl.LOCK_NB)
        prepare(source, destination, title=TITLE, namespace=NS, pack=PACK, build_scripts=scripts, surface=128, structures=False)
    level = destination/'level.dat'
    stream = io.BytesIO(gzip.decompress(level.read_bytes()))
    assert stream.read(1) == b'\x0a'
    nbt.string(stream)
    root = nbt.read(stream, 10)
    root['Data'][1].update({'DifficultyLocked': (1, 1), 'GameType': (3, 0), 'DragonFight': (10, {})})
    level.write_bytes(gzip.compress(b'\x0a'+nbt.utf('')+nbt.write(10, root)))
    report = destination/'preparation.json'
    info = json.loads(report.read_text())
    info.update({'mode': 'Survival', 'phase': '0, paused', 'heart': 'none; beats previewed, broken lines owed for a real respawn',
                 'fixtures': 'buttons for the opening, the records and Translator, both re-inks, each beat, a death and loop status'})
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

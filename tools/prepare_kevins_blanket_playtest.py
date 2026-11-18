#!/usr/bin/env python3
"""Fresh Kevin's Blanket Check: the ORSA Thermal Blanket's look, its stitched K. and its +5 C (CH-38).

A flat Survival lab in Phase 1 cold, with the world clock paused. Buttons give the blanket, drop it a few blocks ahead
and put it in a chest beside the player, so the temperature readout can be watched rising by 5 with it in the bag and
falling without it. Difficulty is locked and advancement popups are off. Never overwrites a save.
The first join is never replayed in a save, so each check of the first-join grant (the notice and the blanket after
the field manual) takes a fresh destination, such as "Kevin's First Join Check".
Usage (lab client, run-lab/ directory):
  python3 tools/prepare_kevins_blanket_playtest.py --source <any closed level.dat> --destination "run-lab/saves/Kevin's Blanket Check"
  python3 tools/prepare_kevins_blanket_playtest.py --source <any closed level.dat> --destination "run-lab/saves/Kevin's First Join Check"
"""
import argparse
import fcntl
import gzip
import io
import json
from pathlib import Path

import prepare_blind_architect_playtest as nbt
from prepare_power_network_playtest import prepare, pack_only

TITLE = "Kevin's Blanket Check"
NS = 'kevins_blanket_check'
PACK = 'kevins-blanket-check'
OBJECTIVE = 'kevinsblanket'
GUARD = f'execute unless score #built {OBJECTIVE} matches 1 run return 0'
BLANKET = 'frozendawn:orsa_thermal_blanket'


def tell(text, command=None, color='aqua'):
    value = {'text': text, 'color': color}
    if command:
        value['clickEvent'] = {'action': 'run_command', 'value': command}
    return 'tellraw @s '+json.dumps(value)


def scripts():
    setup = [f'execute unless score #built {OBJECTIVE} matches -1 run return 0',
             f'scoreboard players set #built {OBJECTIVE} -2', 'gamemode survival @s',
             'gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false',
             'gamerule doFireTick false', 'gamerule keepInventory true', 'gamerule commandBlockOutput false',
             'gamerule announceAdvancements false',
             'time set 6000', 'weather clear', 'fd world set phase 1', 'fd world pause',
             'fill -8 64 -8 8 64 8 minecraft:snow_block',
             'setworldspawn 0 65 0', 'spawnpoint @s 0 65 0', 'tp @s 0.5 65 0.5 180 0',
             f'scoreboard players set #built {OBJECTIVE} 1', 'function '+NS+':controls', 'return 1']
    controls = [GUARD, tell("KEVIN'S BLANKET CHECK: watch the temperature readout with the blanket in the bag and without."),
                tell('[Give the blanket]', f'/give @s {BLANKET}', 'aqua'),
                tell('[Drop it ahead]', '/function '+NS+':drop', 'yellow'),
                tell('[Put it in a chest]', '/function '+NS+':chest', 'gold'),
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
        # Takes every blanket from the bag and lays one on the snow three blocks ahead, to walk back to.
        'drop': '\n'.join([GUARD, f'execute store result score #had {OBJECTIVE} run clear @s {BLANKET}',
                           f'execute if score #had {OBJECTIVE} matches 1.. at @s run summon minecraft:item ^ ^ ^3 '
                           f'{{Item:{{id:"{BLANKET}",count:1}},PickupDelay:40}}', 'return 1']),
        # Takes every blanket from the bag and puts one in a chest a block to the player's left.
        'chest': '\n'.join([GUARD, f'execute store result score #had {OBJECTIVE} run clear @s {BLANKET}',
                            f'execute if score #had {OBJECTIVE} matches 1.. at @s run setblock ^1 ^ ^ minecraft:chest'
                            f'{{Items:[{{Slot:0b,id:"{BLANKET}",count:1}}]}} replace', 'return 1'])}


def prepare_world(source, destination):
    with (source.parent/'session.lock').open('rb') as lock:
        fcntl.lockf(lock, fcntl.LOCK_SH | fcntl.LOCK_NB)
        prepare(source, destination, title=TITLE, namespace=NS, pack=PACK, build_scripts=scripts, surface=64, structures=False)
    level = destination/'level.dat'
    stream = io.BytesIO(gzip.decompress(level.read_bytes()))
    assert stream.read(1) == b'\x0a'
    nbt.string(stream)
    root = nbt.read(stream, 10)
    root['Data'][1].update({'DifficultyLocked': (1, 1), 'GameType': (3, 0), 'LevelName': (8, destination.name)})
    level.write_bytes(gzip.compress(b'\x0a'+nbt.utf('')+nbt.write(10, root)))
    report = destination/'preparation.json'
    info = json.loads(report.read_text())
    info.update({'mode': 'Survival', 'phase': '1, paused',
                 'fixtures': 'buttons that give the blanket, drop it ahead and put it in a chest'})
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

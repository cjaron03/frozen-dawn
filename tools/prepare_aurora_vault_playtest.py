#!/usr/bin/env python3
"""Fresh Aurora Vault Check: the owner finds Aurora where the story puts her, and walks her out.

One Helion site with the Terminus Vault under it, 32 blocks south of a flat spawn, placed by /fd terminus vault place. The
player starts in the cot room in Survival with no cell, so the first run opens the last door the way the game does, on the
cot, and finds the spare in the locker. Buttons jump to the rooms that matter, give cells, and reset. Advancements are
filtered as in every generated playtest world, with the vault documents' own kept so the manual opens on them. Never
overwrites a save. Usage (lab client, run-lab/ directory):
  python3 tools/prepare_aurora_vault_playtest.py --source <any closed level.dat> --destination "run-lab/saves/Aurora Vault Check"
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

TITLE = 'Aurora Vault Check'
NS = 'aurora_vault_check'
PACK = 'aurora-vault-check'
GUARD = 'execute unless score #built auroravault matches 1 run return 0'
# The site goes 32 blocks south of the spawn on flat ground at 64: its hall floor is 24 down.
HX, HY, HZ = 0, 40, 32
CELL = 'give @s frozendawn:power_cell[frozendawn:cell_joules=%d]'


def tell(text, function=None, color='aqua', command=None):
    value = {'text': text, 'color': color}
    if function:
        value['clickEvent'] = {'action': 'run_command', 'value': '/function '+NS+':'+function}
    if command:
        value['clickEvent'] = {'action': 'run_command', 'value': command}
    return 'tellraw @s '+json.dumps(value)


def tp(x, y, z, yaw):
    return f'tp @s {HX+x}.5 {HY+y} {HZ+z}.5 {yaw} 0'


def scripts():
    cot = tp(97, -14, 1, -90)
    setup = ['execute unless score #built auroravault matches -1 run return 0',
             'scoreboard players set #built auroravault -2', 'gamemode survival @s',
             'gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false',
             'gamerule keepInventory true', 'gamerule sendCommandFeedback false', 'gamerule commandBlockOutput false',
             'gamerule announceAdvancements false', 'time set noon', 'weather clear', 'clear @s', 'fd world pause',
             'item replace entity @s hotbar.0 with minecraft:cooked_beef 32',
             'tp @s 0.5 65 0.5 0 0', 'fd terminus vault place',
             'setworldspawn 0 65 0', 'spawnpoint @s 0 65 0', cot,
             'scoreboard players set #built auroravault 1', 'function '+NS+':controls', 'return 1']
    rooms = {'cot': (cot, 'The cot room. The last door is east, the cot along the south wall. Lie down, unwatched, to open it.'),
             'vault': (tp(103, -14, 0, 180), 'The vault. The technician and her dock are on the north wall, the locker south.'),
             'morgue': (tp(85, -14, 0, -90), 'The Morgue, west of the cot room.'),
             'landing': (tp(-1, -14, 0, 90), 'The landing, at the foot of the ladder up. She should catch up and ask here.')}
    result = {
        'load': 'scoreboard objectives add auroravault dummy\nexecute unless score #built auroravault matches -2..1 run scoreboard players set #built auroravault 0\nexecute if score #built auroravault matches -2..-1 run scoreboard players set #built auroravault 0',
        'bootstrap': '\n'.join(['execute unless score #built auroravault matches 0 run return 0',
                               'forceload add -32 -16 144 96', 'scoreboard players set #built auroravault -1', 'return 1']),
        'tick': '\n'.join(['execute if score #built auroravault matches 0 as @a[limit=1] run function '+NS+':bootstrap',
                          'execute if score #built auroravault matches -1 if loaded -32 65 -16 if loaded 144 65 96 as @a[limit=1] run function '+NS+':setup']),
        'setup': '\n'.join(setup),
        'controls': '\n'.join([GUARD,
            tell('AURORA VAULT CHECK: Survival. Open the last door from the cot, wake her with the locker\'s spare, then walk out through this door.'),
            tell('[Cot room]', 'cot'), tell('[The vault]', 'vault'), tell('[Morgue]', 'morgue'), tell('[Landing: the way up]', 'landing'),
            tell('[A charged cell]', 'charged'), tell('[An empty cell]', 'empty'), tell('[Night]', 'night'), tell('[Spawn a zombie]', 'zombie'),
            tell('[Creative]', 'creative'), tell('[Survival]', 'survival'), tell('[Reset the vault]', 'reset'),
            tell('[Replay first meeting]', command='/fd shepherd replay', color='yellow'),
            tell('[Shepherd dump]', command='/fd shepherd dump', color='gray'), tell('[Read state]', 'read', 'gray'), 'return 1']),
        'charged': '\n'.join([GUARD, CELL % 500000, 'return 1']),
        'empty': '\n'.join([GUARD, CELL % 0, 'return 1']),
        'night': '\n'.join([GUARD, 'time set midnight', 'return 1']),
        'zombie': '\n'.join([GUARD, 'execute at @s run summon minecraft:zombie ^ ^ ^6', 'return 1']),
        'reset': '\n'.join([GUARD, 'fd terminus vault reset', 'clear @s frozendawn:terminus', 'return 1']),
        'read': '\n'.join([GUARD, 'fd terminus vault status', 'return 1']),
        'creative': '\n'.join([GUARD, 'gamemode creative @s', 'return 1']),
        'survival': '\n'.join([GUARD, 'gamemode survival @s', 'return 1'])}
    for name, (move, line) in rooms.items():
        result[name] = '\n'.join([GUARD, move, tell(line), tell('[Lab menu]', 'controls'), 'return 1'])
    return result


def prepare_world(source, destination):
    with (source.parent/'session.lock').open('rb') as lock:
        fcntl.lockf(lock, fcntl.LOCK_SH | fcntl.LOCK_NB)
        prepare(source, destination, title=TITLE, namespace=NS, pack=PACK, build_scripts=scripts, surface=128, structures=False)
    document_advancements(destination/'datapacks'/PACK)
    # Lock the difficulty the shared lab chose, and give the save the empty dragon fight the client logs as missing.
    level = destination/'level.dat'
    stream = io.BytesIO(gzip.decompress(level.read_bytes()))
    assert stream.read(1) == b'\x0a'
    nbt.string(stream)
    root = nbt.read(stream, 10)
    root['Data'][1].update({'DifficultyLocked': (1, 1), 'DragonFight': (10, {})})
    level.write_bytes(gzip.compress(b'\x0a'+nbt.utf('')+nbt.write(10, root)))
    report = destination/'preparation.json'
    info = json.loads(report.read_text())
    info.update({'mode': 'Survival', 'fixtures': 'one Helion site with the Terminus Vault under it, room buttons, cells, a zombie, reset'})
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

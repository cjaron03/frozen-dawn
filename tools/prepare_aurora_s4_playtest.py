#!/usr/bin/env python3
"""Fresh Aurora Nine Check: T2, the husk at the pit, the lamp and the quiet day.

A flat lab in Survival with a dock, charged and empty cells in a chest, and a Blast Pit 130 blocks east. The save only
names where the pit goes: the real placement builds it on the first ticks, so the ring the husk stands on is the real
one. Boot her, finish the first meeting, then the buttons make her Friendly, skip the days between what she asks and
take you to the husk. Advancements are filtered as in every generated playtest world, and the difficulty is locked.
Never overwrites a save.
Usage (lab client, run-lab/ directory):
  python3 tools/prepare_aurora_s4_playtest.py --source <any closed level.dat> --destination "run-lab/saves/Aurora Nine Check"
"""
import argparse
import fcntl
import gzip
import io
import json
from pathlib import Path

import prepare_blind_architect_playtest as nbt
from prepare_power_network_playtest import prepare, pack_only
from prepare_terminus_vault_playtest import ADVANCEMENTS

TITLE = 'Aurora Nine Check'
NS = 'aurora_nine_check'
PACK = 'aurora-nine-check'
OBJECTIVE = 'auroranine'
GUARD = f'execute unless score #built {OBJECTIVE} matches 1 run return 0'
CHEST = (3, 65, -4)
CHARGED = 'frozendawn:power_cell[frozendawn:cell_joules=500000]'
EMPTY = 'frozendawn:power_cell'
# Past the distance she asks from, and ground level, as the planner would store it for this flat world.
PIT = (130, 64, 0)
FRIENDLY = 250
# Open crater floor, a few blocks from the first place on the ring.
BESIDE_THE_HUSK = (PIT[0]+10.5, PIT[1]-2, PIT[2]+3.5)
ABOVE_THE_PIT = (PIT[0]-24.5, PIT[1], PIT[2]+0.5)


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
    controls = [GUARD, tell('AURORA NINE CHECK: the cells are in the chest beside the dock. Boot her and finish the first meeting. [Friendly], then [Skip a day] after each thing she asks. Stay by the dock until she has told you where it is.'),
                tell('[Friendly]', command=f'/fd shepherd affinity {FRIENDLY}', color='green'), tell('[Skip a day]', command='/fd shepherd skipday'),
                tell('[The pit]', 'to_the_pit', 'yellow'), tell('[The husk]', 'to_the_husk', 'yellow'), tell('[The dock]', 'to_the_dock', 'yellow'),
                tell('[Why]', command='/fd shepherd why', color='gray'), tell('[Mind]', command='/fd shepherd mind', color='gray'), tell('[State]', command='/fd shepherd state', color='gray'), 'return 1']
    # Enough loaded around the target for the real placement to go ahead, with nobody near it.
    area = 'forceload add {} {} {} {}'.format(PIT[0]-48, PIT[2]-48, PIT[0]+48, PIT[2]+48)
    return {
        'load': f'scoreboard objectives add {OBJECTIVE} dummy\nexecute unless score #built {OBJECTIVE} matches -2..1 run scoreboard players set #built {OBJECTIVE} 0\nexecute if score #built {OBJECTIVE} matches -2..-1 run scoreboard players set #built {OBJECTIVE} 0',
        'bootstrap': '\n'.join([f'execute unless score #built {OBJECTIVE} matches 0 run return 0',
                               'forceload add -32 -16 32 16', area, f'scoreboard players set #built {OBJECTIVE} -1', 'return 1']),
        'tick': '\n'.join([f'execute if score #built {OBJECTIVE} matches 0 as @a[limit=1] run function '+NS+':bootstrap',
                          f'execute if score #built {OBJECTIVE} matches -1 if loaded -32 65 -16 if loaded 32 65 16 as @a[limit=1] run function '+NS+':setup']),
        'setup': '\n'.join(setup),
        'controls': '\n'.join(controls),
        'to_the_pit': '\n'.join([GUARD, 'tp @s {} {} {} -90 0'.format(*ABOVE_THE_PIT), 'return 1']),
        'to_the_husk': '\n'.join([GUARD, 'tp @s {} {} {} 0 0'.format(*BESIDE_THE_HUSK), 'return 1']),
        'to_the_dock': '\n'.join([GUARD, 'tp @s 0.5 65 3.5 180 0', 'return 1'])}


def the_register(pack):
    """The Frame Register's advancement, as the mod defines it but rootless and without a toast: its parent is filtered out."""
    out = pack/'data/frozendawn/advancement'
    out.mkdir(parents=True, exist_ok=True)
    source = ADVANCEMENTS/'found_tithonus_register.json'
    advancement = json.loads(source.read_text())
    del advancement['parent']
    advancement['display'].update({'background': 'minecraft:textures/block/smooth_stone.png', 'show_toast': False})
    (out/source.name).write_text(json.dumps(advancement, indent=2)+'\n')


def pit_planned(destination, data_version):
    """The planner's answer, already given: nothing is built here."""
    state = {'blastPitTargetX': (3, PIT[0]), 'blastPitTargetY': (3, PIT[1]), 'blastPitTargetZ': (3, PIT[2])}
    encoded = b'\x0a'+nbt.utf('')+nbt.write(10, {'data': (10, state), 'DataVersion': (3, data_version)})
    (destination/'data/frozendawn_orsa_structures.dat').write_bytes(gzip.compress(encoded))


def prepare_world(source, destination):
    with (source.parent/'session.lock').open('rb') as lock:
        fcntl.lockf(lock, fcntl.LOCK_SH | fcntl.LOCK_NB)
        prepare(source, destination, title=TITLE, namespace=NS, pack=PACK, build_scripts=scripts, surface=128, structures=False)
    the_register(destination/'datapacks'/PACK)
    level = destination/'level.dat'
    stream = io.BytesIO(gzip.decompress(level.read_bytes()))
    assert stream.read(1) == b'\x0a'
    nbt.string(stream)
    root = nbt.read(stream, 10)
    root['Data'][1].update({'DifficultyLocked': (1, 1), 'DragonFight': (10, {})})
    level.write_bytes(gzip.compress(b'\x0a'+nbt.utf('')+nbt.write(10, root)))
    pit_planned(destination, root['Data'][1]['DataVersion'][1])
    report = destination/'preparation.json'
    info = json.loads(report.read_text())
    info.update({'mode': 'Survival', 'fixtures': 'a dock, charged and empty cells in a chest, a Blast Pit planned at '+str(PIT)+' and built by the real placement, buttons for Friendly, a skipped day, the pit, the husk and the dock'})
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

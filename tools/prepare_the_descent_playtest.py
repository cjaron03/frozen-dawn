#!/usr/bin/env python3
"""Fresh Descent Pieces Check: the descent's slice 1 pieces laid out as the descent runs.

A flat Creative lab. Ten bays run east, each two blocks lower than the last: the 1816 house, the eight cold
periods from the Maunder Minimum down to the Last Glacial Maximum, then chamber zero. Each era's bay holds its
motif on the back wall, its mark set into the wall and its instrument on the floor; the house also hangs the
portrait, and chamber zero holds only the scratches. Chat buttons move between bays and play each clip through
/fd debug descent play. Difficulty is locked and advancement popups are off. Never overwrites a save.
Usage (lab client, run-lab/ directory):
  python3 tools/prepare_the_descent_playtest.py --source <any closed level.dat>
"""
import argparse
import fcntl
import gzip
import io
import json
from pathlib import Path

import prepare_blind_architect_playtest as nbt
from prepare_power_network_playtest import prepare, pack_only

TITLE = 'Descent Pieces Check'
NS = 'descent_pieces_check'
PACK = 'descent-pieces-check'
OBJECTIVE = 'descentpieces'
GUARD = f'execute unless score #built {OBJECTIVE} matches 1 run return 0'
DESTINATION = Path(__file__).resolve().parents[1]/'run-lab'/'saves'/TITLE

# era, clip, instrument, mark, standing (the count is for the owner's check, never shown in game)
BAYS = [
    ('house_1816', 'music_box', 'music_box', 'ink_mark', 2),
    ('maunder', 'maunder', 'church_bell', 'panel_mark', 3),
    ('famine', 'famine', 'monastery_bell', 'glass_mark', 4),
    ('late_antique', 'late_antique', 'lyre', 'mosaic_mark', 5),
    ('bronze_age', 'bronze_age', 'bronze_lur', 'bronze_mark', 6),
    ('four_kiloyear', 'four_kiloyear', 'bull_lyre', 'clay_mark', 7),
    ('eight_kiloyear', 'eight_kiloyear', 'drum_and_whistle', 'plaster_mark', 8),
    ('younger_dryas', 'younger_dryas', 'stone_whistle', 'carved_mark', 9),
    ('glacial_maximum', 'glacial_maximum', 'bone_flute', 'ochre_mark', 10),
]
WIDTH = 12          # each bay is 12 blocks wide along x
WALL_Z = -6         # the back wall; bays open to the south
TOP = 64            # the first bay's floor; each bay sits two lower
LAST = len(BAYS)    # chamber zero's bay index


def floor(i):
    return TOP - 2*i


def tell(text, command=None, color='aqua'):
    value = {'text': text, 'color': color}
    if command:
        value['clickEvent'] = {'action': 'run_command', 'value': command}
    return 'tellraw @s '+json.dumps(value)


def painting(x, y, variant):
    """A painting on the back wall facing south (2D value 0), anchored at x, y."""
    return (f'summon minecraft:painting {x} {y} {WALL_Z+1} '
            f'{{facing:0b,variant:"frozendawn:descent/{variant}",block_pos:[I;{x},{y},{WALL_Z+1}]}}')


def bay(i):
    x0, f = i*WIDTH, floor(i)
    out = [f'fill {x0} {f+1} {WALL_Z} {x0+WIDTH-1} 74 {WALL_Z+12} minecraft:air',
           f'fill {x0} {f} {WALL_Z} {x0+WIDTH-1} {f} {WALL_Z+12} minecraft:stone_bricks',
           f'fill {x0} {f+1} {WALL_Z} {x0+WIDTH-1} {f+8} {WALL_Z} minecraft:smooth_stone']
    if i > 0:  # one step up to the bay above, in the middle
        out.append(f'fill {x0} {f+1} {WALL_Z+5} {x0} {f+1} {WALL_Z+7} minecraft:stone_brick_stairs[facing=west]')
    if i == LAST:
        out.append(painting(x0+6, f+3, 'chamber_zero_scratches'))
        return out
    era, _, instrument, mark, _ = BAYS[i]
    out += [painting(x0+6, f+4, era),
            f'setblock {x0+1} {f+2} {WALL_Z} frozendawn:descent_{mark}[facing=south]',
            f'setblock {x0+6} {f+1} {WALL_Z+3} frozendawn:descent_{instrument}[facing=south]']
    if era == 'house_1816':
        out.append(painting(x0+10, f+3, 'portrait_1816'))
    return out


def scripts():
    setup = [f'execute unless score #built {OBJECTIVE} matches -1 run return 0',
             f'scoreboard players set #built {OBJECTIVE} -2', 'gamemode creative @s',
             'gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false',
             'gamerule doFireTick false', 'gamerule keepInventory true', 'gamerule commandBlockOutput false',
             'gamerule announceAdvancements false', 'gamerule sendCommandFeedback false',
             'time set 6000', 'weather clear', 'fd world pause', 'kill @e[type=minecraft:painting]']
    for i in range(LAST+1):
        setup += bay(i)
    setup += [f'setworldspawn 6 {TOP+1} 0', f'spawnpoint @s 6 {TOP+1} 0', f'tp @s 6.5 {TOP+1} 0.5 180 10',
              f'scoreboard players set #built {OBJECTIVE} 1', 'function '+NS+':controls', 'return 1']
    controls = [GUARD, tell('DESCENT PIECES CHECK: bays run east and down, the house first, chamber zero last. '
                            'Use each instrument, then use it again while it plays: it must not restart.')]
    result = {}
    for i in range(LAST+1):
        x0, f = i*WIDTH, floor(i)
        name = 'chamber_zero' if i == LAST else BAYS[i][0]
        label = name if i == LAST else f'{name} ({BAYS[i][4]} standing)'
        controls.append(tell(f'[{label}]', f'/function {NS}:bay_{name}', 'yellow'))
        clip = 'icicle' if i == LAST else BAYS[i][1]
        result[f'bay_{name}'] = '\n'.join([GUARD, f'tp @s {x0+6.5} {f+1} {WALL_Z+8.5} 180 10',
                                           tell(f'[Play the {clip} clip here]', f'/fd debug descent play {clip}', 'gold'),
                                           tell('[Bays]', f'/function {NS}:controls', 'gray'), 'return 1'])
    controls.append('return 1')
    result.update({
        'load': f'scoreboard objectives add {OBJECTIVE} dummy\nexecute unless score #built {OBJECTIVE} matches -2..1 run scoreboard players set #built {OBJECTIVE} 0\nexecute if score #built {OBJECTIVE} matches -2..-1 run scoreboard players set #built {OBJECTIVE} 0',
        'bootstrap': '\n'.join([f'execute unless score #built {OBJECTIVE} matches 0 run return 0',
                               f'forceload add -16 -16 {WIDTH*(LAST+1)+16} 16', f'scoreboard players set #built {OBJECTIVE} -1', 'return 1']),
        'tick': '\n'.join([f'execute if score #built {OBJECTIVE} matches 0 as @a[limit=1] run function '+NS+':bootstrap',
                          f'execute if score #built {OBJECTIVE} matches -1 if loaded -16 64 -16 if loaded {WIDTH*(LAST+1)+16} 64 16 as @a[limit=1] run function '+NS+':setup',
                          f'execute if score #built {OBJECTIVE} matches 1 run forceload remove -16 -16 {WIDTH*(LAST+1)+16} 16']),
        'setup': '\n'.join(setup),
        'controls': '\n'.join(controls)})
    return result


def prepare_world(source, destination):
    with (source.parent/'session.lock').open('rb') as lock:
        fcntl.lockf(lock, fcntl.LOCK_SH | fcntl.LOCK_NB)
        prepare(source, destination, title=TITLE, namespace=NS, pack=PACK, build_scripts=scripts, surface=64, structures=False)
    level = destination/'level.dat'
    stream = io.BytesIO(gzip.decompress(level.read_bytes()))
    assert stream.read(1) == b'\x0a'
    nbt.string(stream)
    root = nbt.read(stream, 10)
    root['Data'][1].update({'DifficultyLocked': (1, 1), 'GameType': (3, 1), 'LevelName': (8, destination.name),
                            'SpawnX': (3, 6), 'SpawnY': (3, TOP+1), 'SpawnZ': (3, 0)})
    level.write_bytes(gzip.compress(b'\x0a'+nbt.utf('')+nbt.write(10, root)))
    report = destination/'preparation.json'
    info = json.loads(report.read_text())
    info.update({'mode': 'Creative', 'phase': 'paused',
                 'fixtures': 'ten descending bays: each era\'s motif, mark and instrument; the portrait; chamber zero\'s scratches; clip buttons'})
    report.write_text(json.dumps(info, indent=2)+'\n')
    print(destination.resolve())


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path)
    parser.add_argument('--destination', type=Path, default=DESTINATION)
    parser.add_argument('--pack-only', type=Path)
    args = parser.parse_args()
    if args.pack_only:
        pack_only(args.pack_only, title=TITLE, namespace=NS, build_scripts=scripts)
    elif args.source:
        prepare_world(args.source, args.destination)
    else:
        parser.error('Use --source <closed level.dat> [--destination], or --pack-only')

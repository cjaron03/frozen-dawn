#!/usr/bin/env python3
"""Fresh Payoff Clues Check: the manual's new pages, the Thaeven Translator entry and the Emergency EVA wording.

A flat Creative lab in late Phase 6 (vacuum), with the world clock paused. Buttons give the manual, unlock the locked
entries, give a Translator, and die so the Emergency EVA is issued; a last pair shortens its service so a CP toast shows.
Give the Translator before Unlock entries to see the entry open from holding one.
Advancements are filtered as in every generated playtest world, and the difficulty is locked. Never overwrites a save.
Usage (lab client, run-lab/ directory):
  python3 tools/prepare_payoff_clues_playtest.py --source <any closed level.dat> --destination "run-lab/saves/Payoff Clues Check"
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

TITLE = 'Payoff Clues Check'
NS = 'payoff_clues_check'
PACK = 'payoff-clues-check'
OBJECTIVE = 'payoffclues'
GUARD = f'execute unless score #built {OBJECTIVE} matches 1 run return 0'
MANUAL = 'give @s patchouli:guide_book[patchouli:book="frozendawn:frozen_dawn_guide"]'
DOCUMENTS = ('found_orsa_bulletin', 'found_satellite_log')
UNLOCKS = (*DOCUMENTS, 'knows_thaeven_translator')


def tell(text, command=None, color='aqua'):
    value = {'text': text, 'color': color}
    if command:
        value['clickEvent'] = {'action': 'run_command', 'value': command}
    return 'tellraw @s '+json.dumps(value)


def scripts():
    setup = [f'execute unless score #built {OBJECTIVE} matches -1 run return 0',
             f'scoreboard players set #built {OBJECTIVE} -2', 'gamemode creative @s',
             'gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false',
             'gamerule doFireTick false', 'gamerule keepInventory true',
             'gamerule commandBlockOutput false', 'gamerule announceAdvancements false',
             'time set 6000', 'weather clear', 'clear @s', 'fd world set phase 6 late', 'fd world pause',
             'fill -16 64 -16 16 64 16 minecraft:polished_deepslate',
             'setworldspawn 0 65 0', 'spawnpoint @s 0 65 0', 'tp @s 0.5 65 0.5 180 0',
             f'scoreboard players set #built {OBJECTIVE} 1', 'function '+NS+':controls', 'return 1']
    controls = [GUARD, tell('PAYOFF CLUES CHECK: read the four pages and the Translator entry, then die for the Emergency EVA.'),
                tell('[Give manual]', '/function '+NS+':manual', 'green'),
                tell('[Give Translator]', '/function '+NS+':translator', 'green'),
                tell('[Unlock entries]', '/function '+NS+':unlock', 'yellow'),
                tell('[Respawn]', '/function '+NS+':respawn', 'red'),
                tell('[Service 5:10]', '/fdlab payoff_clues eva_service 310', 'gold'),
                tell('[Service 1:55]', '/fdlab payoff_clues eva_service 115', 'gold'),
                tell('[Controls]', '/function '+NS+':controls', 'gray'), 'return 1']
    return {
        'load': f'scoreboard objectives add {OBJECTIVE} dummy\nexecute unless score #built {OBJECTIVE} matches -2..1 run scoreboard players set #built {OBJECTIVE} 0\nexecute if score #built {OBJECTIVE} matches -2..-1 run scoreboard players set #built {OBJECTIVE} 0',
        'bootstrap': '\n'.join([f'execute unless score #built {OBJECTIVE} matches 0 run return 0',
                               'forceload add -32 -32 32 32', f'scoreboard players set #built {OBJECTIVE} -1', 'return 1']),
        'tick': '\n'.join([f'execute if score #built {OBJECTIVE} matches 0 as @a[limit=1] run function '+NS+':bootstrap',
                          f'execute if score #built {OBJECTIVE} matches -1 if loaded -32 65 -32 if loaded 32 65 32 as @a[limit=1] run function '+NS+':setup']),
        'setup': '\n'.join(setup),
        'controls': '\n'.join(controls),
        'manual': '\n'.join([GUARD, MANUAL, 'return 1']),
        'translator': '\n'.join([GUARD, 'give @s frozendawn:thaeven_translator', 'return 1']),
        'unlock': '\n'.join([GUARD, *[f'advancement grant @s only frozendawn:{name}' for name in UNLOCKS], 'return 1']),
        # The Emergency EVA is issued only to a Survival player who respawns in vacuum.
        'respawn': '\n'.join([GUARD, 'gamemode survival @s', 'kill @s', 'return 1'])}


def unlock_advancements(pack):
    """The entries' advancements as the mod defines them, with documents rootless and silent: the filter hides the originals."""
    out = pack/'data/frozendawn/advancement'
    out.mkdir(parents=True, exist_ok=True)
    for name in UNLOCKS:
        advancement = json.loads((ADVANCEMENTS/f'{name}.json').read_text())
        if name in DOCUMENTS:
            del advancement['parent']
            advancement['display'].update({'background': 'minecraft:textures/block/smooth_stone.png',
                                           'show_toast': False, 'announce_to_chat': False})
        (out/f'{name}.json').write_text(json.dumps(advancement, indent=2)+'\n')


def prepare_world(source, destination):
    with (source.parent/'session.lock').open('rb') as lock:
        fcntl.lockf(lock, fcntl.LOCK_SH | fcntl.LOCK_NB)
        prepare(source, destination, title=TITLE, namespace=NS, pack=PACK, build_scripts=scripts, surface=128, structures=False)
    unlock_advancements(destination/'datapacks'/PACK)
    level = destination/'level.dat'
    stream = io.BytesIO(gzip.decompress(level.read_bytes()))
    assert stream.read(1) == b'\x0a'
    nbt.string(stream)
    root = nbt.read(stream, 10)
    root['Data'][1].update({'DifficultyLocked': (1, 1), 'DragonFight': (10, {})})
    level.write_bytes(gzip.compress(b'\x0a'+nbt.utf('')+nbt.write(10, root)))
    report = destination/'preparation.json'
    info = json.loads(report.read_text())
    info.update({'mode': 'Creative, Survival after Respawn', 'phase': '6 late, paused',
                 'fixtures': 'buttons for the manual, the Translator, the locked entries, a respawn and the EVA service'})
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

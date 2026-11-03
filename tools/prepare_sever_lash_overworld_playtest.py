#!/usr/bin/env python3
"""Prepare a fresh normal-terrain Sever Lash field test from generator metadata only; never overwrite a save.

Phase 0, held: the Lash only behaves differently in vacuum (late Phase 6), so the earliest phase keeps the world
ordinary. Survival with natural mob spawning, so the Lash meets real hostiles with real AI. As in the Sever Lash
Check, the owner's ruling (2026-11-02) keeps advancement toasts and chat on in this world.
"""
import argparse
import fcntl
import gzip
import io
import json
from pathlib import Path

import prepare_blind_architect_playtest as nbt
from prepare_power_network_playtest import prepare, pack_only

TITLE = 'Sever Lash Overworld'
NS = 'sever_lash_field'
PACK = 'sever-lash-field'
GUARD = 'execute unless score #built slfield matches 1 run return 0'
FULL = 'frozendawn:power_cell[frozendawn:cell_joules=500000]'


def tell(text, function=None):
    value = {'text': text, 'color': 'aqua'}
    if function:
        value['clickEvent'] = {'action': 'run_command', 'value': '/function '+NS+':'+function}
    return 'tellraw @s '+json.dumps(value)


def scripts():
    return {
        'load': 'scoreboard objectives add slfield dummy\nexecute unless score #built slfield matches -2..1 run scoreboard players set #built slfield 0\nexecute if score #built slfield matches 1 as @a run function '+NS+':controls',
        'tick': 'execute if score #built slfield matches 0 as @a[limit=1] at @s run function '+NS+':setup',
        'setup': '\n'.join([
            'execute unless score #built slfield matches 0 run return 0', 'scoreboard players set #built slfield -2',
            'gamemode survival @s', 'gamerule doMobSpawning true', 'gamerule doDaylightCycle true',
            'gamerule keepInventory true', 'gamerule announceAdvancements true',
            'gamerule sendCommandFeedback false', 'gamerule commandBlockOutput false',
            'fd world set phase 0', 'fd world pause', 'time set day', 'weather clear',
            'execute at @s positioned over motion_blocking_no_leaves run tp @s ~ ~1 ~',
            'execute at @s run spawnpoint @s ~ ~ ~',
            'clear @s',
            'item replace entity @s hotbar.0 with frozendawn:sever_lash',
            'item replace entity @s hotbar.1 with '+FULL, 'item replace entity @s hotbar.2 with '+FULL,
            'item replace entity @s hotbar.3 with '+FULL,
            'item replace entity @s hotbar.7 with minecraft:cooked_beef 32',
            'item replace entity @s hotbar.8 with minecraft:torch 32',
            'scoreboard players set #built slfield 1', 'function '+NS+':controls', 'return 1']),
        'controls': '\n'.join([GUARD,
            tell('SEVER LASH OVERWORLD: normal terrain, Survival, Normal difficulty, Phase 0 held. Hostiles spawn naturally at night. Advancements are ON. Your other worlds are preserved.'),
            tell('[Night: let hostiles come]', 'night'), tell('[Day]', 'day'),
            tell('[Husk pack ahead: three with real AI]', 'pack'), tell('[Three full cells]', 'cells'),
            tell('[Creative]', 'creative'), tell('[Survival]', 'survival'), tell('[Read diagnostics]', 'read'), 'return 1']),
        'night': '\n'.join([GUARD, 'time set midnight', tell('Midnight. Hostiles spawn in the dark around you.'), 'return 1']),
        'day': '\n'.join([GUARD, 'time set day', 'return 1']),
        # Spread across the view ten blocks out, so one continuous beam can catch all three (Hellwhip).
        'pack': '\n'.join([GUARD,
            'execute at @s positioned ^-3 ^ ^10 positioned over motion_blocking_no_leaves run summon minecraft:husk ~ ~ ~',
            'execute at @s positioned ^ ^ ^11 positioned over motion_blocking_no_leaves run summon minecraft:husk ~ ~ ~',
            'execute at @s positioned ^3 ^ ^10 positioned over motion_blocking_no_leaves run summon minecraft:husk ~ ~ ~',
            tell('Three husks ahead. They come for you.'), 'return 1']),
        'cells': '\n'.join([GUARD, 'clear @s frozendawn:power_cell',
            'item replace entity @s hotbar.1 with '+FULL, 'item replace entity @s hotbar.2 with '+FULL,
            'item replace entity @s hotbar.3 with '+FULL, tell('Three full cells: a minute of beam.'), 'return 1']),
        'creative': '\n'.join([GUARD, 'gamemode creative @s', 'return 1']),
        'survival': '\n'.join([GUARD, 'gamemode survival @s', 'return 1']),
        # Command feedback is off in this world, so diagnostics print through tellraw, not data get.
        'read': '\n'.join([GUARD,
            'execute if entity @s[advancements={frozendawn:hellwhip=true}] run '+tell('Hellwhip: earned'),
            'execute unless entity @s[advancements={frozendawn:hellwhip=true}] run '+tell('Hellwhip: not yet'),
            'tellraw @s '+json.dumps([{'text':'Held Lash heat: ','color':'aqua'},{'entity':'@s','nbt':'SelectedItem.components."frozendawn:lash_heat"','color':'white'}]),
            'tellraw @s '+json.dumps([{'text':'Your health: ','color':'aqua'},{'entity':'@s','nbt':'Health','color':'white'}]),
            'return 1'])}


def prepare_world(source, destination):
    with (source.parent/'session.lock').open('rb') as lock:
        fcntl.lockf(lock, fcntl.LOCK_SH | fcntl.LOCK_NB)
        prepare(source, destination, title=TITLE, namespace=NS, pack=PACK, build_scripts=scripts, terrain=True, structures=True)
    # The shared helper hides every advancement in this world's pack and silences chat awards; this world shows them.
    meta_path = destination/'datapacks'/PACK/'pack.mcmeta'; meta = json.loads(meta_path.read_text())
    meta.pop('filter'); meta_path.write_text(json.dumps(meta)+'\n')
    target = destination/'level.dat'; stream = io.BytesIO(gzip.decompress(target.read_bytes()))
    assert stream.read(1) == b'\x0a'
    name = nbt.string(stream); root = nbt.read(stream, 10); data = root['Data'][1]
    rules = data['GameRules'][1]
    rules.update({'announceAdvancements': (8, 'true'), 'doMobSpawning': (8, 'true')})
    # Uninitialized, so the server finds a real spawn on the generated terrain.
    data.update({'GameType': (3, 0), 'initialized': (1, 0), 'DifficultyLocked': (1, 1),
                 'confirmedExperimentalSettings': (1, 1), 'DragonFight': (10, {})})
    target.write_bytes(gzip.compress(b'\x0a'+nbt.utf(name)+nbt.write(10, root)))
    report = destination/'preparation.json'; info = json.loads(report.read_text())
    info.update({'advancements': 'visible with toasts and chat in this world only (owner ruling 2026-11-02)',
                 'phase': 'Phase 0, paused by the setup function',
                 'terrain': 'normal Overworld generation with structures; natural mob spawning and daylight cycle on'})
    report.write_text(json.dumps(info, indent=2)+'\n')
    print(destination.resolve())


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path); parser.add_argument('--destination', type=Path); parser.add_argument('--pack-only', type=Path)
    args = parser.parse_args()
    if args.pack_only: pack_only(args.pack_only, title=TITLE, namespace=NS, build_scripts=scripts)
    elif args.source and args.destination: prepare_world(args.source, args.destination)
    else: parser.error('Use --source/--destination or --pack-only')

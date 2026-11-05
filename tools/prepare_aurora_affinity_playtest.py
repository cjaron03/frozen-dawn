#!/usr/bin/env python3
"""Fresh Aurora Affinity Check: what moves her score, and what her score does to her.

A flat lab in Survival with a dock and a charged cell in the hotbar, a sealed hut with a lit heater, a sealed hut with an
unlit one, open sky, and a chest, heater, terminal, bed and campfire for her to wander off to. Wake her and walk the first meeting, then the buttons set her score and stage each deed. The
huts are sealed, so only the buttons go in and out, and they bring her along. The cold hut moves the world to phase 5 so
the air is cold enough; the warm hut moves it back. Advancements are filtered as in every generated playtest world, with
the vault documents' own kept so the manual opens on them. Never overwrites a save. Usage (lab client, run-lab/ directory):
  python3 tools/prepare_aurora_affinity_playtest.py --source <any closed level.dat> --destination "run-lab/saves/Aurora Affinity Check"
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

TITLE = 'Aurora Affinity Check'
NS = 'aurora_affinity_check'
PACK = 'aurora-affinity-check'
GUARD = 'execute unless score #built auroraaffinity matches 1 run return 0'
UNIT = 'frozendawn:cs1_unit'
LOG = 'give @s frozendawn:orsa_document[minecraft:custom_data={doc_type:"terminus_manifest"}]'
MANUAL = 'give @s patchouli:guide_book[patchouli:book="frozendawn:frozen_dawn_guide"]'
CELL = 'item replace entity @s hotbar.1 with frozendawn:power_cell[frozendawn:cell_joules=500000]'
BASE = 'tp @s 0.5 65 3.5 180 0'
WARM_X, COLD_X = 15, -15


def tell(text, function=None, color='aqua', command=None):
    value = {'text': text, 'color': color}
    if function:
        value['clickEvent'] = {'action': 'run_command', 'value': '/function '+NS+':'+function}
    if command:
        value['clickEvent'] = {'action': 'run_command', 'value': command}
    return 'tellraw @s '+json.dumps(value)


def hut(x):
    return [f'fill {x-3} 65 -3 {x+3} 69 3 minecraft:stone_bricks', f'fill {x-2} 65 -2 {x+2} 68 2 minecraft:air']


def site():
    return (['fill -24 64 -12 24 64 12 minecraft:polished_deepslate',
             'setblock 0 65 -4 frozendawn:cs1_dock[facing=south]']
            + hut(WARM_X) + hut(COLD_X)
            + [f'setblock {WARM_X+2} 65 0 frozendawn:thermal_heater{{BurnTime:10000000}}',
               f'setblock {COLD_X-2} 65 0 frozendawn:thermal_heater',
               'setblock -5 65 0 minecraft:chest', 'setblock 5 65 0 frozendawn:thermal_heater',
               'setblock 7 65 -6 frozendawn:monitoring_station_terminal',
               'setblock -7 65 -6 minecraft:red_bed[facing=north,part=foot]', 'setblock -7 65 -7 minecraft:red_bed[facing=north,part=head]',
               'setblock 0 65 8 minecraft:campfire'])


def into(x):
    return [f'tp @s {x}.5 65 0.5 90 0', f'tp @e[type={UNIT},limit=1] {x}.5 66 2.5']


def scripts():
    setup = ['execute unless score #built auroraaffinity matches -1 run return 0',
             'scoreboard players set #built auroraaffinity -2', 'gamemode survival @s',
             'gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false',
             'gamerule doFireTick false', 'gamerule mobGriefing false', 'gamerule keepInventory true', 'gamerule sendCommandFeedback false',
             'gamerule commandBlockOutput false', 'gamerule announceAdvancements false',
             'difficulty normal', 'time set noon', 'weather clear', 'clear @s', 'fd world pause',
             'item replace entity @s hotbar.0 with minecraft:cooked_beef 32', CELL,
             'item replace entity @s hotbar.2 with minecraft:netherite_sword']
    setup += site()
    setup += ['setworldspawn 0 65 3', 'spawnpoint @s 0 65 3', BASE,
              'scoreboard players set #built auroraaffinity 1', 'function '+NS+':controls', 'return 1']
    moves = {'warm': ['fd world set phase 1 mid', *into(WARM_X), tell('The warm hut, heater lit. She reacts within five seconds, once a day.')],
             'cold': ['fd world set phase 5 mid', *into(COLD_X), tell('The cold hut, heater out, the world at phase 5. Back out by [Warm hut] or [Lab].')],
             'phase4': ['fd world set phase 4 mid', tell('Phase 4. The phase move sets the clock to morning, so press [Night] after it.')],
             'phase5': ['fd world set phase 5 mid', tell('Phase 5, the blizzard. The phase move sets the clock to morning, so press [Night] after it.')],
             'lab': ['fd world set phase 1 mid', BASE, f'tp @e[type={UNIT},limit=1] 3.5 66 3.5'],
             'far': ['tp @s 200.5 65 0.5 -90 0', tell('Tell her to wait here first, or she follows. [Skip a day], then [Lab] to come back to her.')]}
    result = {
        'load': 'scoreboard objectives add auroraaffinity dummy\nexecute unless score #built auroraaffinity matches -2..1 run scoreboard players set #built auroraaffinity 0\nexecute if score #built auroraaffinity matches -2..-1 run scoreboard players set #built auroraaffinity 0',
        'bootstrap': '\n'.join(['execute unless score #built auroraaffinity matches 0 run return 0',
                               'forceload add -32 -16 32 16', 'scoreboard players set #built auroraaffinity -1', 'return 1']),
        'tick': '\n'.join(['execute if score #built auroraaffinity matches 0 as @a[limit=1] run function '+NS+':bootstrap',
                          'execute if score #built auroraaffinity matches -1 if loaded -32 65 -16 if loaded 32 65 16 as @a[limit=1] run function '+NS+':setup']),
        'setup': '\n'.join(setup),
        'controls': '\n'.join([GUARD,
            tell('AURORA AFFINITY CHECK: Survival. Wake her on the dock with the cell in your hotbar and finish the first meeting, then try these.'),
            tell('[Score 240]', command='/fd shepherd affinity 240'), tell('[Score 260]', command='/fd shepherd affinity 260'),
            tell('[Score 500]', command='/fd shepherd affinity 500'), tell('[Score 850]', command='/fd shepherd affinity 850'),
            tell('[Score -300]', command='/fd shepherd affinity -300'), tell('[Score 0]', command='/fd shepherd affinity 0'),
            tell('[Reset ambient gap]', command='/fd shepherd resetgap'),
            tell('[An ORSA log]', 'log'), tell('[Summon an Archivist]', 'archivist'), tell('[Night]', 'night'), tell('[Day]', 'day'),
            tell('[Skip a day]', command='/fd shepherd skipday'), tell('[Spawn a zombie]', 'zombie'), tell('[Four zombies]', 'zombies'),
            tell('[Summon a skeleton]', 'skeleton'), tell('[Summon a Returned]', 'returned'), tell('[Summon an Architect]', 'architect'), tell('[Summon a sheep]', 'sheep'),
            tell('[Explosion]', 'explosion'), tell('[Hearth wall]', command='/fd shepherd hearthhere'),
            tell('[Raw cod]', 'cod'), tell('[Cake]', 'cake'), tell('[Wooden sword]', 'sword'), tell('[Field manual]', 'manual'),
            tell('[Phase 4]', 'phase4'), tell('[Phase 5]', 'phase5'),
            tell('[Cold hut]', 'cold'), tell('[Warm hut]', 'warm'), tell('[Far away]', 'far'), tell('[Lab]', 'lab'),
            tell('[Creative]', 'creative'), tell('[Survival]', 'survival'),
            tell('[Replay first meeting]', command='/fd shepherd replay', color='yellow'),
            tell('[Shepherd dump]', command='/fd shepherd dump', color='gray'), tell('[Read state]', command='/fd world status', color='gray'), 'return 1']),
        'log': '\n'.join([GUARD, LOG, 'return 1']),
        'archivist': '\n'.join([GUARD, 'execute at @s run summon frozendawn:archivist ^ ^ ^5', 'return 1']),
        'night': '\n'.join([GUARD, 'time set midnight', 'weather clear', 'return 1']),
        'day': '\n'.join([GUARD, 'time set noon', 'return 1']),
        'zombie': '\n'.join([GUARD, 'execute at @s run summon minecraft:zombie ^ ^ ^6', 'return 1']),
        'zombies': '\n'.join([GUARD, *[f'execute at @s run summon minecraft:zombie ^{x} ^ ^7' for x in (-3, -1, 1, 3)], 'return 1']),
        'skeleton': '\n'.join([GUARD, 'execute at @s run summon minecraft:skeleton ^ ^ ^8', 'return 1']),
        'returned': '\n'.join([GUARD, 'execute at @s run summon frozendawn:returned ^ ^ ^8', 'return 1']),
        'architect': '\n'.join([GUARD, 'execute at @s run summon frozendawn:architect ^ ^ ^8 {NoAI:1b}', 'return 1']),
        'sheep': '\n'.join([GUARD, 'execute at @s run summon minecraft:sheep ^ ^ ^4', 'return 1']),
        'explosion': '\n'.join([GUARD, 'execute at @s run summon minecraft:creeper ^ ^ ^6 {Fuse:0s,ignited:1b,ExplosionRadius:1b}', 'return 1']),
        'cod': '\n'.join([GUARD, 'give @s minecraft:cod 4', 'return 1']),
        'cake': '\n'.join([GUARD, 'give @s minecraft:cake', 'return 1']),
        'sword': '\n'.join([GUARD, 'give @s minecraft:wooden_sword', 'return 1']),
        'manual': '\n'.join([GUARD, MANUAL, 'return 1']),
        'creative': '\n'.join([GUARD, 'gamemode creative @s', 'return 1']),
        'survival': '\n'.join([GUARD, 'gamemode survival @s', 'return 1'])}
    for name, lines in moves.items():
        result[name] = '\n'.join([GUARD, *lines, tell('[Lab menu]', 'controls'), 'return 1'])
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
    info.update({'mode': 'Survival', 'fixtures': 'a dock and a charged cell, a warm hut, a cold hut, open sky, things for her to look at, score, deed and nod buttons'})
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

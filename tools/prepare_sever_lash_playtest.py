#!/usr/bin/env python3
"""Prepare a fresh Sever Lash firing range from generator metadata only; never overwrite a save.

By the owner's ruling (2026-11-02) this world keeps advancements visible, toasts and chat included, so the
Hellwhip award can be seen. Every other generated playtest world still filters them.
"""
import argparse
import fcntl
import gzip
import io
import json
from pathlib import Path

import prepare_blind_architect_playtest as nbt
from prepare_power_network_playtest import prepare, pack_only

TITLE = 'Sever Lash Check'
NS = 'sever_lash_check'
PACK = 'sever-lash-check'
GUARD = 'execute unless score #built slash matches 1 run return 0'
FULL = 'frozendawn:power_cell[frozendawn:cell_joules=500000]'
# Two seconds of beam, to watch it stop on an empty cell.
LOW = 'frozendawn:power_cell[frozendawn:cell_joules=50000]'
DIAMOND = ('ArmorItems:[{id:"minecraft:diamond_boots",count:1},{id:"minecraft:diamond_leggings",count:1},'
           '{id:"minecraft:diamond_chestplate",count:1},{id:"minecraft:diamond_helmet",count:1}]')


def tell(text, function=None):
    value = {'text': text, 'color': 'aqua'}
    if function:
        value['clickEvent'] = {'action': 'run_command', 'value': '/function '+NS+':'+function}
    return 'tellraw @s '+json.dumps(value)


def target(kind, x, z, extra=''):
    tags = 'Tags:["slash_target"],NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f]'
    return f'summon minecraft:{kind} {x} 65 {z} {{{tags}{","+extra if extra else ""}}}'


def targets():
    """Firing positions face south (+Z): range from (0.5, 65, 0.5), armor from (30.5, 65, 0.5), room from (60.5, 65, 0.5)."""
    return ['kill @e[tag=slash_target]',
            # Range: three husks across a 60-degree arc at six blocks for Hellwhip; reach markers; a walled pig.
            target('husk', -2.5, 5.7), target('husk', 0.5, 6.5), target('husk', 3.5, 5.7),
            target('pig', 0.5, 12), target('pig', 0.5, 15),
            target('pig', -6.5, 5.5),
            # Armor: bare and full diamond, side by side at five blocks.
            target('husk', 28.5, 5.5), target('husk', 32.5, 5.5, DIAMOND),
            # Room: one husk inside the glass room.
            target('husk', 60.5, 6.5)]


def scripts():
    setup = ['execute unless score #built slash matches -1 run return 0',
             'scoreboard players set #built slash -2', 'gamemode creative @s',
             'gamerule doMobSpawning false', 'gamerule doDaylightCycle false',
             'gamerule doWeatherCycle false', 'gamerule doFireTick false',
             'gamerule keepInventory true', 'gamerule announceAdvancements true',
             'gamerule sendCommandFeedback false', 'gamerule commandBlockOutput false',
             'time set noon', 'weather clear',
             'fill -16 64 -8 76 64 24 minecraft:smooth_stone',
             # Wall between the range and the walled pig: five blocks out at about -50 degrees.
             'fill -4 65 2 -3 67 4 minecraft:stone',
             # Reach markers on the floor: white at 12 blocks (inside), red at 15 (beyond).
             'fill -1 64 12 1 64 12 minecraft:white_concrete', 'fill -1 64 15 1 64 15 minecraft:red_concrete',
             # Sealed glass room around the third station; its roof gap opens on demand.
             'fill 55 64 -3 66 70 10 minecraft:glass hollow', 'fill 55 64 -3 66 64 10 minecraft:smooth_stone',
             'fd world set phase 6 mid', 'fd world pause',
             'clear @s',
             'item replace entity @s hotbar.0 with frozendawn:sever_lash',
             'item replace entity @s hotbar.1 with '+FULL,
             'item replace entity @s hotbar.8 with frozendawn:geiger_counter',
             *targets(),
             'setworldspawn 0 65 0', 'spawnpoint @s 0 65 0', 'tp @s 0.5 65 0.5 0 0',
             'scoreboard players set #built slash 1', 'function '+NS+':controls', 'return 1']
    result = {
        'load': 'scoreboard objectives add slash dummy\nexecute unless score #built slash matches -2..1 run scoreboard players set #built slash 0\nexecute if score #built slash matches 1 as @a run function '+NS+':controls',
        'bootstrap': '\n'.join(['execute unless score #built slash matches 0 run return 0',
                               'forceload add -16 -8 76 24', 'scoreboard players set #built slash -1', 'return 1']),
        'tick': '\n'.join(['execute if score #built slash matches 0 as @a[limit=1] run function '+NS+':bootstrap',
                          'execute if score #built slash matches -1 if loaded -16 65 -8 if loaded 76 65 24 as @a[limit=1] run function '+NS+':setup']),
        'setup': '\n'.join(setup),
        'controls': '\n'.join([GUARD,
            tell('SEVER LASH CHECK: Creative, Normal difficulty, paused mid Phase 6 (air everywhere). Hold use to fire. Advancements are ON in this world.'),
            tell('[Range: sweep, reach, wall, Hellwhip]', 'range'), tell('[Armor: bare vs diamond]', 'armor'),
            tell('[Room: air vs vacuum]', 'room'), tell('[Low cell: 2 s of beam]', 'low'), tell('[Full cell]', 'full'),
            tell('[Advance this world to VACUUM (one way)]', 'vacuum'), tell('[Respawn targets]', 'respawn'), tell('[Read diagnostics]', 'read'), 'return 1']),
        'respawn': '\n'.join([GUARD, *targets(), tell('Targets respawned at full health.'), 'return 1']),
        'low': '\n'.join([GUARD, 'clear @s frozendawn:power_cell', 'item replace entity @s hotbar.1 with '+LOW,
                          tell('One 50 kJ cell: about two seconds of beam, then it stops.'), 'return 1']),
        'full': '\n'.join([GUARD, 'clear @s frozendawn:power_cell', 'item replace entity @s hotbar.1 with '+FULL,
                           tell('One full cell: twenty seconds of beam.'), 'return 1']),
        'vacuum': '\n'.join([GUARD, 'fd world set phase 6 late', 'fd world pause', tell('Vacuum active outdoors. The sealed room keeps its air until you open the roof.'), 'return 1']),
        'breach': '\n'.join([GUARD, 'fill 60 70 3 61 70 4 minecraft:air', tell('Roof open: the room is vacuum now.'), 'return 1']),
        'seal': '\n'.join([GUARD, 'fill 60 70 3 61 70 4 minecraft:glass', tell('Roof sealed: give the room a moment to read as air again.'), 'return 1']),
        'read': '\n'.join([GUARD,
            'execute if entity @s[advancements={frozendawn:hellwhip=true}] run '+tell('Hellwhip: earned'),
            'execute unless entity @s[advancements={frozendawn:hellwhip=true}] run '+tell('Hellwhip: not yet'),
            # Command feedback is off in this world, so diagnostics print through tellraw, not data get.
            'tellraw @s '+json.dumps([{'text':'Held Lash heat: ','color':'aqua'},{'entity':'@s','nbt':'SelectedItem.components."frozendawn:lash_heat"','color':'white'}]),
            'execute as @e[tag=slash_target] run tellraw @p '+json.dumps([{'selector':'@s','color':'aqua'},{'text':' at ','color':'aqua'},
                {'entity':'@s','nbt':'Pos','color':'gray'},{'text':' health ','color':'aqua'},{'entity':'@s','nbt':'Health','color':'white'}]),
            'return 1'])}
    views = {
        'range': ((.5,65,.5), ['Three husks at six blocks: cut all three in one continuous beam, without letting go (Hellwhip).',
                               'Pigs over the white line (12, inside reach) and the red line (15, beyond). The pig left of the stone wall should be safe from straight shots.',
                               'Fire continuously for six seconds to overheat: four seconds of lockout in air.']),
        'armor': ((30.5,65,.5), ['Left husk bare, right husk in full diamond. Diamond should slow the cut, but less than it slows a sword.']),
        'room': ((60.5,65,.5), ['Air vs vacuum. Click VACUUM in the lab menu first: outdoors the beam then shows only its impact, and the overheat lockout doubles to eight seconds.',
                                'Inside the sealed glass room the air remains: the beam draws its red line and the lockout is four seconds. Open the roof to make the room vacuum too.']),
    }
    for name, (position, lines) in views.items():
        extra = [tell('[Open roof]', 'breach'), tell('[Seal roof]', 'seal')] if name == 'room' else []
        result[name] = '\n'.join([GUARD, 'tp @s '+' '.join(map(str,position))+' 0 0',
                                  *(tell(line) for line in lines), *extra, tell('[Lab menu]', 'controls'), 'return 1'])
    return result


def prepare_world(source, destination):
    with (source.parent/'session.lock').open('rb') as lock:
        fcntl.lockf(lock, fcntl.LOCK_SH | fcntl.LOCK_NB)
        prepare(source,destination,title=TITLE,namespace=NS,pack=PACK,build_scripts=scripts,surface=64,structures=False)
    # The shared helper hides every advancement in this world's pack and silences chat awards; this world shows them.
    meta_path=destination/'datapacks'/PACK/'pack.mcmeta';meta=json.loads(meta_path.read_text())
    meta.pop('filter');meta_path.write_text(json.dumps(meta)+'\n')
    target=destination/'level.dat';stream=io.BytesIO(gzip.decompress(target.read_bytes()))
    assert stream.read(1)==b'\x0a'
    name=nbt.string(stream);root=nbt.read(stream,10);data=root['Data'][1]
    data['GameRules'][1]['announceAdvancements']=(8,'true')
    data.update({'DifficultyLocked':(1,1),'confirmedExperimentalSettings':(1,1),'DragonFight':(10,{}),
                 'SpawnX':(3,0),'SpawnY':(3,65),'SpawnZ':(3,0)})
    target.write_bytes(gzip.compress(b'\x0a'+nbt.utf(name)+nbt.write(10,root)))
    report=destination/'preparation.json';info=json.loads(report.read_text())
    info.update({'advancements':'visible with toasts and chat in this world only (owner ruling 2026-11-02)',
                 'phase':'mid phase 6 (air), paused; a one-way button advances to late phase 6 vacuum',
                 'fixtures':'firing range with Hellwhip husks, 12/15 reach pigs and a walled pig; bare vs diamond husks; sealed glass room with an openable roof'})
    report.write_text(json.dumps(info,indent=2)+'\n')
    print(destination.resolve())


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',type=Path);parser.add_argument('--destination',type=Path);parser.add_argument('--pack-only',type=Path)
    args=parser.parse_args()
    if args.pack_only: pack_only(args.pack_only,title=TITLE,namespace=NS,build_scripts=scripts)
    elif args.source and args.destination: prepare_world(args.source,args.destination)
    else: parser.error('Use --source/--destination or --pack-only')

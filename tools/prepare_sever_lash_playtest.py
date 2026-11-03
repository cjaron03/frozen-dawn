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
import math
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


def packed(x, y, z):
    """BlockPos.asLong, as the Master Architect stores its Last Wall."""
    v = (x & 0x3FFFFFF) << 38 | (z & 0x3FFFFFF) << 12 | (y & 0xFFF)
    return v - (1 << 64) if v >= 1 << 63 else v


# Last Wall sample: five columns three high, the weak ice seam in the middle, as the Master Architect raises it.
LAST_WALL = [(x, y, 6) for x in range(118, 123) for y in range(65, 68)]
SEAM = [(120, y, 6) for y in range(65, 68)]
WALL_NBT = ('{MasterWallExpiresAt:-1L,MasterWallBlocks:[L;'+','.join(str(packed(*b))+'L' for b in LAST_WALL)+'],'
            'MasterWallSeams:[L;'+','.join(str(packed(*b))+'L' for b in SEAM)+']}')
MASTER = ('summon frozendawn:architect 120.5 65 9.5 {Tags:["slash_master"],NoAI:1b,Invulnerable:1b,Silent:1b,'
          'PersistenceRequired:1b,Rotation:[180f,0f],HearthMasterArchitectId:[I;1,2,3,4],'
          'HearthMasterArchitectHome:'+str(packed(120, 65, 9))+'L,'+WALL_NBT[1:])
MEMO = ('frozendawn:orsa_document[custom_name=\'"Procurement Review - Req. 0447"\','
        'custom_data={doc_type:"lc4_requisition_memo"}]')


def walls():
    """Rebuild every block the beam can change: the effects walls in the open and in the sealed room, both ice walls."""
    def column(x, z, block, y0=65, y1=67):
        return f'fill {x} {y0} {z} {x} {y1} {z} minecraft:{block}'
    open_wall = [column(x, 6, b) for x, b in ((84, 'snow_block'), (86, 'ice'), (88, 'packed_ice'), (90, 'blue_ice'),
                                              (92, 'stone'), (94, 'oak_planks'), (96, 'oak_log'))]
    room_wall = [column(x, 9, b) for x, b in ((56, 'ice'), (57, 'oak_planks'), (64, 'stone'), (65, 'packed_ice'))]
    return ['fill 83 65 3 97 69 5 minecraft:air', 'fill 84 65 3 96 65 3 minecraft:snow[layers=2]',
            *open_wall, 'setblock 98 65 6 minecraft:campfire[lit=false]', 'setblock 99 65 6 minecraft:candle[lit=false]',
            *room_wall,
            'fill 118 65 6 122 67 6 minecraft:packed_ice', 'fill 120 65 6 120 67 6 minecraft:ice',
            'fill 125 65 6 129 67 6 minecraft:packed_ice', 'fill 127 65 6 127 67 6 minecraft:ice',
            'fill 118 64 8 122 68 11 minecraft:glass hollow',
            # Reset the Master's wall in place: a kill would run its full death path.
            'execute if entity @e[tag=slash_master] run data merge entity @e[tag=slash_master,limit=1] '+WALL_NBT,
            'execute unless entity @e[tag=slash_master] run '+MASTER]


# Beam station, firing from (15.5, 65, 0.5) south: pillars every 20 degrees from -50 to 50 at ten blocks, alternating
# white and light blue concrete so a sweep can be counted. Concrete takes no heat, so the pillars never need rebuilding.
BEAM_ARC = [f'fill {x} 65 {z} {x} 68 {z} minecraft:{"white" if i % 2 == 0 else "light_blue"}_concrete'
            for i, (x, z) in enumerate((round(15.5 + 10 * math.sin(math.radians(a)) - .5), round(.5 + 10 * math.cos(math.radians(a)) - .5))
                                       for a in range(-50, 51, 20))]


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
             'fill -16 64 -8 136 64 24 minecraft:smooth_stone',
             # Wall between the range and the walled pig: five blocks out at about -50 degrees.
             'fill -4 65 2 -3 67 4 minecraft:stone',
             # Reach markers on the floor: white at 12 blocks (inside), red at 15 (beyond).
             'fill -1 64 12 1 64 12 minecraft:white_concrete', 'fill -1 64 15 1 64 15 minecraft:red_concrete',
             # Sealed glass room around the third station; its roof gap opens on demand.
             'fill 55 64 -3 66 70 10 minecraft:glass hollow', 'fill 55 64 -3 66 64 10 minecraft:smooth_stone',
             # Beam station: six pillars on a ten-block arc, 100 degrees wide, to sweep across slowly and fast.
             *BEAM_ARC,
             # Crafting bench for the Lash recipe.
             'setblock 105 65 4 minecraft:crafting_table',
             'fd world set phase 6 mid', 'fd world pause',
             'clear @s',
             'item replace entity @s hotbar.0 with frozendawn:sever_lash',
             'item replace entity @s hotbar.1 with '+FULL,
             'item replace entity @s hotbar.8 with frozendawn:geiger_counter',
             *targets(), *walls(),
             'setworldspawn 0 65 0', 'spawnpoint @s 0 65 0', 'tp @s 0.5 65 0.5 0 0',
             'scoreboard players set #built slash 1', 'function '+NS+':controls', 'return 1']
    result = {
        'load': 'scoreboard objectives add slash dummy\nexecute unless score #built slash matches -2..1 run scoreboard players set #built slash 0\nexecute if score #built slash matches 1 as @a run function '+NS+':controls',
        'bootstrap': '\n'.join(['execute unless score #built slash matches 0 run return 0',
                               'forceload add -16 -8 136 24', 'scoreboard players set #built slash -1', 'return 1']),
        'tick': '\n'.join(['execute if score #built slash matches 0 as @a[limit=1] run function '+NS+':bootstrap',
                          'execute if score #built slash matches -1 if loaded -16 65 -8 if loaded 136 65 24 as @a[limit=1] run function '+NS+':setup']),
        'setup': '\n'.join(setup),
        'controls': '\n'.join([GUARD,
            tell('SEVER LASH CHECK: Creative, Normal difficulty, paused mid Phase 6 (air everywhere). Hold use to fire. Advancements are ON in this world.'),
            tell('[Beam: slow vs fast sweeps, air vs vacuum]', 'beam'),
            tell('[Range: sweep, reach, wall, Hellwhip]', 'range'), tell('[Armor: bare vs diamond]', 'armor'),
            tell('[Room: air vs vacuum]', 'room'), tell('[World effects: melt, scorch, ignite]', 'effects'),
            tell('[Last Wall]', 'lastwall'), tell('[Craft a Lash]', 'craft'),
            tell('[Low cell: 2 s of beam]', 'low'), tell('[Full cell]', 'full'), tell('[Rebuild walls]', 'walls'),
            tell('[Advance this world to VACUUM (one way)]', 'vacuum'), tell('[Respawn targets]', 'respawn'), tell('[Read diagnostics]', 'read'), 'return 1']),
        'respawn': '\n'.join([GUARD, *targets(), tell('Targets respawned at full health.'), 'return 1']),
        'walls': '\n'.join([GUARD, *walls(), tell('Effects walls, both ice walls and the Master Architect rebuilt.'), 'return 1']),
        'ingredients': '\n'.join([GUARD, 'give @s frozendawn:lash_emitter', 'give @s frozendawn:acheronite_shard',
                                  'give @s '+FULL, 'give @s minecraft:iron_ingot 2', 'give @s frozendawn:lead_ingot',
                                  tell('Shaped: iron, emitter, shard on top; lead, cell, iron below.'), 'return 1']),
        'memo': '\n'.join([GUARD, 'give @s '+MEMO, tell('The memo found at about one Helion site in four. Right-click to read and archive.'), 'return 1']),
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
        'beam': ((15.5,65,.5), ['Six pillars on an arc ahead. Hold use and sweep slowly from end to end: the beam should stay straight, white core in an orange sheath with a violet halo.',
                                'Now sweep fast and stop dead: the far end trails into a whip, a faint red afterimage fades behind it, and it snaps straight with a crack.',
                                'Listen for the start-up, the hum rising as it heats, the sizzle where it lands, and the vent hiss at overheat.',
                                'Click VACUUM in the lab menu, then fire again here: in vacuum only the white core and the impact show. The Room station keeps air for a side-by-side.']),
        'range': ((.5,65,.5), ['Three husks at six blocks: cut all three in one continuous beam, without letting go (Hellwhip).',
                               'Pigs over the white line (12, inside reach) and the red line (15, beyond). The pig left of the stone wall should be safe from straight shots.',
                               'Fire continuously for six seconds to overheat: four seconds of lockout in air.']),
        'armor': ((30.5,65,.5), ['Left husk bare, right husk in full diamond. The Lash bypasses armor: both should take the same cut.']),
        'room': ((60.5,65,.5), ['Air vs vacuum. Click VACUUM in the lab menu first: outdoors the beam then shows only its impact, and the overheat lockout doubles to eight seconds.',
                                'Inside the sealed glass room the air remains: the full beam shows, sheath, halo and afterimage, and the lockout is four seconds. Open the roof to make the room vacuum too.',
                                'Against the back wall: ice, planks, stone and packed ice. After VACUUM, planks still catch in here and stone still scorches outdoors, but outdoor wood will not burn.']),
        'effects': ((90.5,65,.5), ['Left to right: snow, ice, packed ice, blue ice, stone, planks, log, then an unlit campfire and candle. Snow covers the floor.',
                                   'A sweep flashes snow and ice to steam. Hold the beam for 1 s on packed ice, 2 s on blue ice and 0.5 s on stone (it turns to Scorched Ground).',
                                   'Wood, the campfire and the candle catch at once while there is air. Fire tick is off here, so fire does not spread.',
                                   'Fire continuously to watch the held Lash heat: fin tips dull red from half heat, fins and barrel glowing orange from 80%.',
                                   'At overheat the fins lean back and the slots under them flicker white, then cool through orange to red and fold shut. In air the vents smoke; in vacuum they only glow.']),
        'lastwall': ((120.5,65,.5), ['Left: a Last Wall held by a frozen Master Architect (NoAI, invulnerable, in glass). It should not melt.',
                                     'Right: the same packed ice and ice with no Master. It melts like any other.',
                                     'The frozen Master runs no AI, so breaking the seam by hand does not drop its wall here; that is checked in a live fight.']),
        'craft': ((105.5,65,.5), ['Crafting table ahead. Take the ingredients, craft the Lash: it should come out cool and carry no charge.']),
    }
    for name, (position, lines) in views.items():
        extra = {'room': [tell('[Open roof]', 'breach'), tell('[Seal roof]', 'seal')],
                 'craft': [tell('[Ingredients]', 'ingredients'), tell('[Hellwhip memo]', 'memo')]}.get(name, [])
        result[name] = '\n'.join([GUARD, 'tp @s '+' '.join(map(str,position))+' 0 0',
                                  *(tell(line) for line in lines), *extra, tell('[Lab menu]', 'controls'), 'return 1'])
    return result


def prepare_world(source, destination, title=TITLE):
    with (source.parent/'session.lock').open('rb') as lock:
        fcntl.lockf(lock, fcntl.LOCK_SH | fcntl.LOCK_NB)
        prepare(source,destination,title=title,namespace=NS,pack=PACK,build_scripts=scripts,surface=64,structures=False)
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
                 'fixtures':'beam arc of six pillars for slow and fast sweeps; firing range with Hellwhip husks, 12/15 reach pigs and a walled pig; bare vs diamond husks; sealed glass room with an openable roof and an effects wall; '
                             'melt/scorch/ignite wall outdoors; Last Wall held by a NoAI Master Architect beside a plain twin; crafting bench with ingredient and memo buttons'})
    report.write_text(json.dumps(info,indent=2)+'\n')
    print(destination.resolve())


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',type=Path);parser.add_argument('--destination',type=Path);parser.add_argument('--pack-only',type=Path);parser.add_argument('--title',default=TITLE)
    args=parser.parse_args()
    if args.pack_only: pack_only(args.pack_only,title=TITLE,namespace=NS,build_scripts=scripts)
    elif args.source and args.destination: prepare_world(args.source,args.destination,args.title)
    else: parser.error('Use --source/--destination or --pack-only')

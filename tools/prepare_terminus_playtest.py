#!/usr/bin/env python3
"""Prepare a fresh Terminus test range from generator metadata only; never overwrite a save.

Every station fires south (+Z) from its marked spot. The player starts in Survival, so the hearts show what each
discharge costs; advancements are filtered as in every generated playtest world, so Deathwand is checked by GameTest.
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

TITLE = 'Terminus Check'
NS = 'terminus_check'
PACK = 'terminus-check'
GUARD = 'execute unless score #built terminus matches 1 run return 0'
SWORD = 'HandItems:[{id:"minecraft:iron_sword",count:1},{}],HandDropChances:[2f,0f]'


def tell(text, function=None):
    value = {'text': text, 'color': 'aqua'}
    if function:
        value['clickEvent'] = {'action': 'run_command', 'value': '/function '+NS+':'+function}
    return 'tellraw @s '+json.dumps(value)


def packed(x, y, z):
    """BlockPos.asLong, as the Master Architect stores its home."""
    v = (x & 0x3FFFFFF) << 38 | (z & 0x3FFFFFF) << 12 | (y & 0xFFF)
    return v - (1 << 64) if v >= 1 << 63 else v


def mob(kind, x, z, extra=''):
    tags = 'Tags:["terminus_target"],NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f]'
    return f'summon {kind} {x} 65 {z} {{{tags}{","+extra if extra else ""}}}'


def at(x0, yaw, distance):
    """A spot `distance` blocks out from (x0 + .5, 0.5) at `yaw` degrees off south, toward the west for positive yaw."""
    r = math.radians(yaw)
    return round(x0 + .5 - math.sin(r) * distance, 2), round(.5 + math.cos(r) * distance, 2)


MASTER = ('summon frozendawn:architect 120.5 65 7.5 {Tags:["terminus_target","terminus_master"],NoAI:1b,Invulnerable:1b,'
          'Silent:1b,PersistenceRequired:1b,Rotation:[90f,0f],HearthMasterArchitectId:[I;1,2,3,4],'
          'HearthMasterArchitectHome:'+str(packed(120, 65, 7))+'L}')


def targets():
    return ['kill @e[tag=terminus_target]',
            # Cone: dead ahead at 6, near the 15-degree edge at 10, outside it at 22 degrees, and beyond reach at 25.5.
            mob('minecraft:husk', *at(0, 0, 6)), mob('minecraft:husk', *at(0, 13, 10)),
            mob('minecraft:husk', *at(0, 22, 6)), mob('minecraft:husk', *at(0, 0, 25.5)),
            # Walls: a zombie and a villager behind obsidian.
            mob('minecraft:zombie', 30.5, 9.5), mob('minecraft:villager', 31.5, 10.5),
            # Table: a husk to show the cone landed, beside five that should be untouched.
            mob('minecraft:husk', 62, 11.5), mob('minecraft:pig', 60.5, 5.5), mob('minecraft:wolf', 59.5, 7.5),
            mob('minecraft:skeleton', 61.5, 7.5), mob('frozendawn:frostmite', 60.5, 9.5), mob('frozendawn:rimebound', 59, 11.5),
            # Boss: a frozen Wither loses 40% a discharge and is never killed.
            mob('minecraft:wither', 90.5, 9.5),
            # Master: a frozen Master Architect side-on, so its head turning to the wand shows.
            MASTER,
            # Drops: three zombies with a sword each that always drops if they die the ordinary way.
            *(mob('minecraft:zombie', x, 6.5, SWORD) for x in (149.5, 150.5, 151.5))]


def scripts():
    setup = ['execute unless score #built terminus matches -1 run return 0',
             'scoreboard players set #built terminus -2', 'gamemode survival @s',
             'gamerule doMobSpawning false', 'gamerule doDaylightCycle false',
             'gamerule doWeatherCycle false', 'gamerule keepInventory true',
             'gamerule sendCommandFeedback false', 'gamerule commandBlockOutput false',
             'time set noon', 'weather clear',
             'fill -16 64 -8 196 64 32 minecraft:smooth_stone',
             # Reach markers for the cone station: white at 24 blocks, red at 27.
             'fill -1 64 24 1 64 24 minecraft:white_concrete', 'fill -1 64 27 1 64 27 minecraft:red_concrete',
             # Walls station: obsidian four blocks out, taller and wider than the cone there.
             'fill 27 65 4 34 69 5 minecraft:obsidian',
             # Interrupt station: magma underfoot, one hurt every half second in Survival.
             'fill 179 64 -1 181 64 1 minecraft:magma_block',
             *(f'setblock {x} 64 0 minecraft:gold_block' for x in (0, 30, 60, 90, 120, 150, 210)),
             'clear @s', 'item replace entity @s hotbar.0 with frozendawn:terminus',
             *targets(),
             'setworldspawn 0 65 0', 'spawnpoint @s 0 65 0', 'tp @s 0.5 65 0.5 0 0',
             'scoreboard players set #built terminus 1', 'function '+NS+':controls', 'return 1']
    result = {
        'load': 'scoreboard objectives add terminus dummy\nexecute unless score #built terminus matches -2..1 run scoreboard players set #built terminus 0\nexecute if score #built terminus matches 1 as @a run function '+NS+':controls',
        'bootstrap': '\n'.join(['execute unless score #built terminus matches 0 run return 0',
                               'forceload add -16 -8 196 32', 'scoreboard players set #built terminus -1', 'return 1']),
        'tick': '\n'.join(['execute if score #built terminus matches 0 as @a[limit=1] run function '+NS+':bootstrap',
                          'execute if score #built terminus matches -1 if loaded -16 65 -8 if loaded 196 65 32 as @a[limit=1] run function '+NS+':setup']),
        'setup': '\n'.join(setup),
        'controls': '\n'.join([GUARD,
            tell('TERMINUS CHECK: Survival, Normal difficulty. Hold use for two seconds to discharge. Stand on each gold block; targets are south.'),
            tell('[Cone: inside, edge, outside, beyond]', 'cone'), tell('[Through walls]', 'walls'),
            tell('[Who is untouched]', 'table'), tell('[Boss: 40%, never a kill]', 'boss'),
            tell('[Master Architect]', 'master'), tell('[Drops before and after the Heart]', 'drops'),
            tell('[Interrupt: magma underfoot]', 'interrupt'), tell('[Wind-up: walk, look, listen]', 'windup'),
            tell('[Fresh wand]', 'wand'), tell('[Restore max health]', 'restore'), tell('[Respawn targets]', 'respawn'),
            tell('[Creative]', 'creative'), tell('[Survival]', 'survival'), tell('[Read diagnostics]', 'read'), 'return 1']),
        'wand': '\n'.join([GUARD, 'give @s frozendawn:terminus', tell('A wand as found: 3 discharges, Transfers logged: 1.'), 'return 1']),
        'restore': '\n'.join([GUARD, 'fd terminus restore', 'effect clear @s', 'return 1']),
        'respawn': '\n'.join([GUARD, *targets(), tell('Targets respawned.'), 'return 1']),
        'creative': '\n'.join([GUARD, 'gamemode creative @s', tell('Creative: no hearts shown, and no hits to interrupt you.'), 'return 1']),
        'survival': '\n'.join([GUARD, 'gamemode survival @s', 'return 1']),
        'before': '\n'.join([GUARD, 'fd terminus turned false', tell('Before the Heart: hover the wand, it reads Transfers logged.'), 'return 1']),
        'after': '\n'.join([GUARD, 'fd terminus turned true', tell('After the Heart: hover the wand, it reads Terminations logged with the same count.'), 'return 1']),
        'read': '\n'.join([GUARD, 'fd terminus status',
            'tellraw @s '+json.dumps([{'text':'Held wand: ','color':'aqua'},{'entity':'@s','nbt':'SelectedItem.components."frozendawn:terminus_log"','color':'white'}]),
            'execute as @e[tag=terminus_target] run tellraw @p '+json.dumps([{'selector':'@s','color':'aqua'},{'text':' health ','color':'aqua'},{'entity':'@s','nbt':'Health','color':'white'}]),
            'return 1'])}
    views = {
        'cone': (0, ['Four husks: dead ahead at 6, near the cone edge at 10, outside it to the right at 6, and past the red line at 25.5.',
                     'Hold use two seconds aimed straight down the white line. The near two should go, with no beam and no sound; the outside and far husks stay.',
                     'Watch the wand winding up: the motes slide to the emitter, the glass frosts, pale specks stream in along the cone edge, and you walk at 40%.',
                     'On discharge: one grey frame, a pale mote lifts from each husk and winks out, one mote is gone from the glass, two hearts turn ash grey in the health bar, taken, and ten seconds of Darkness and Nausea.',
                     'Taken hearts come back half a heart every half day. /fd terminus due brings the next half back now; /fd terminus status reads the toll.']),
        'walls': (30, ['A zombie and a villager behind obsidian. The cone needs no line of sight: discharge at the wall and both should be gone.']),
        'table': (60, ['A husk at the back, and in front of it a pig, a wolf, a skeleton, a Frostmite and a Rimebound.',
                       'Discharge: only the husk should go. Animals, the mindless and post-Maeve life are untouched.']),
        'boss': (90, ['A frozen Wither. Each discharge takes 40% of its maximum; the third leaves it at 1, never dead.',
                      'Frozen, its boss bar stays full (vanilla updates it from the Wither\'s AI): click [Wither health] after each discharge. 300, then 180, 60, 1.']),
        'master': (120, ['A frozen Master Architect, side-on. Discharge at it: it takes nothing, and its head turns to the wand in your hand, not your face, for three seconds.',
                         'Walk around while it watches: it follows the wand. Then it goes back to looking ahead.']),
        'drops': (150, ['Three zombies, each holding a sword that always drops on an ordinary death.',
                        'Before the Heart: they stand still, grey, and crumble away from the head down. Nothing drops, no experience. Hover the wand: Transfers logged.',
                        'Click AFTER, respawn targets, discharge again: they crumble the same way, but drop their swords and experience, and the wand reads Terminations logged with the same count.',
                        'Stopping all three at once earns the hidden Reduced to Atoms; this world filters advancements, so GameTest checks it.']),
        'interrupt': (180, ['Magma underfoot hurts you every half second in Survival. Start a wind-up here: the hit cancels it, and the discharge is spent anyway.',
                            'Letting go early anywhere else costs nothing. Interrupted, you lose no max health.']),
        'windup': (210, ['Open ground. Walk while winding up to feel the 40% slow; let go before two seconds to keep the discharge.',
                         'Press F5 to see the arm held straight out, and the specks drawn in from the cone.']),
    }
    result['wither_health'] = '\n'.join([GUARD, 'tellraw @s ' + json.dumps(['', {'text': 'Wither health: ', 'color': 'aqua'},
        {'entity': '@e[type=minecraft:wither,tag=terminus_target,limit=1]', 'nbt': 'Health', 'color': 'white'},
        {'text': ' of 300', 'color': 'aqua'}]), 'return 1'])
    for name, (x, lines) in views.items():
        extra = {'drops': [tell('[BEFORE the Heart]', 'before'), tell('[AFTER the Heart]', 'after'), tell('[Respawn targets]', 'respawn')],
                 'boss': [tell('[Wither health]', 'wither_health'), tell('[Respawn targets]', 'respawn')]}.get(name, [])
        result[name] = '\n'.join([GUARD, f'tp @s {x}.5 65 0.5 0 0',
                                  *(tell(line) for line in lines), *extra, tell('[Lab menu]', 'controls'), 'return 1'])
    return result


def prepare_world(source, destination, title=TITLE):
    with (source.parent/'session.lock').open('rb') as lock:
        fcntl.lockf(lock, fcntl.LOCK_SH | fcntl.LOCK_NB)
        prepare(source,destination,title=title,namespace=NS,pack=PACK,build_scripts=scripts,surface=64,structures=False)
    # Lock the difficulty the shared lab chose, and give the save the empty dragon fight the client logs as missing.
    level=destination/'level.dat';stream=io.BytesIO(gzip.decompress(level.read_bytes()))
    assert stream.read(1)==b'\x0a';nbt.string(stream);root=nbt.read(stream,10)
    root['Data'][1].update({'DifficultyLocked':(1,1),'DragonFight':(10,{})})
    level.write_bytes(gzip.compress(b'\x0a'+nbt.utf('')+nbt.write(10,root)))
    report=destination/'preparation.json';info=json.loads(report.read_text())
    info.update({'mode':'Survival, so the hearts show the toll; buttons switch to Creative',
                 'fixtures':'cone range with edge and reach husks; obsidian wall with a zombie and villager behind; untouched table '
                             '(pig, wolf, skeleton, Frostmite, Rimebound) beside a husk; frozen Wither; NoAI Master Architect side-on; '
                             'sword zombies with before/after-the-Heart buttons; magma interrupt pad; open wind-up ground'})
    report.write_text(json.dumps(info,indent=2)+'\n')
    print(destination.resolve())


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',type=Path);parser.add_argument('--destination',type=Path);parser.add_argument('--pack-only',type=Path);parser.add_argument('--title',default=TITLE)
    args=parser.parse_args()
    if args.pack_only: pack_only(args.pack_only,title=TITLE,namespace=NS,build_scripts=scripts)
    elif args.source and args.destination: prepare_world(args.source,args.destination,args.title)
    else: parser.error('Use --source/--destination or --pack-only')

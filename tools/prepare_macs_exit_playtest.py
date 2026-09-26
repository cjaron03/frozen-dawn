#!/usr/bin/env python3
"""Prepare a separate ordinary-action A/B exit replay from a closed QA world."""
import argparse
import gzip
import json
import shutil
from pathlib import Path


def tell(text, command=None, color='gold'):
    msg = {'text': text, 'color': color}
    if command:
        msg['clickEvent'] = {'action': 'run_command', 'value': command}
    return 'tellraw @s ' + json.dumps(msg)


SCRIPTS = {
    'load': 'scoreboard objectives add mx dummy',
    'cleanup': '''execute as @e[tag=macs_exit_actor] run fd architect stop @s
execute as @e[tag=macs_exit_actor] run fd architect dump @s
execute as @e[tag=macs_exit_actor] run data merge entity @s {NoAI:1b}
kill @e[tag=macs_exit_actor]''',
    'build': '''forceload add 2996 2996 3030 3024
forceload add 3058 3006 3062 3010
fill 2996 100 2996 3030 100 3024 minecraft:stone
fill 2996 101 2996 3030 109 3024 minecraft:air
fill 3058 100 3006 3062 100 3010 minecraft:stone
fill 3002 105 3002 3014 105 3014 minecraft:stone
setblock 3008 100 3008 minecraft:lapis_block
fill 3015 100 3007 3018 100 3009 minecraft:gold_block
fill 3007 100 2998 3009 100 3001 minecraft:emerald_block
fill 3014 101 3005 3014 104 3005 minecraft:oak_log
fill 3014 101 3011 3014 104 3011 minecraft:oak_log
fill 3005 101 3002 3005 104 3002 minecraft:oak_log
fill 3011 101 3002 3011 104 3002 minecraft:oak_log
fill 3023 101 3007 3025 104 3009 minecraft:bedrock
fill 3024 101 3008 3024 102 3008 minecraft:air
setblock 3023 102 3008 minecraft:air''',
    'setup': '''tick unfreeze
function macs_exit:load
function macs_exit:cleanup
tag @s add macs_exit
fd postmaeve set-erased
fd postmaeve reset-erased confirm
fd world preset default
fd world set phase 6 late
fd maeve status
fd world set phase 0
gamerule doMobSpawning false
gamerule doDaylightCycle false
gamerule doWeatherCycle false
gamerule keepInventory true
weather clear
time set noon
function macs_exit:build
gamemode survival @s
clear @s
effect give @s minecraft:resistance infinite 4 true
effect give @s minecraft:saturation infinite 0 true
effect give @s minecraft:night_vision infinite 0 true
scoreboard players set #stage mx 0
scoreboard players set #switches mx 0
scoreboard players set #practice mx 0
scoreboard players set #remaining mx 5
scoreboard players set #mode mx 1
scoreboard players set #hidden mx 0
function macs_exit:training''',
    'training': '''function macs_exit:cleanup
scoreboard players set #stage mx 1
scoreboard players set #timer mx 0
scoreboard players set #stood mx 0
tp @s 3008.5 101 3008.5 -90 0
summon frozendawn:architect 3024.5 101 3008.5 {Tags:["macs_exit_actor"],PersistenceRequired:1b}
fd architect approach @e[tag=macs_exit_actor,limit=1] @s
fd architect record @e[tag=macs_exit_actor,limit=1] 1337
''' + tell('Usual-exit practice: stay on blue until GO, then WALK east to GOLD and stop. The witness is safely in its booth. Do not attack.'),
    'training_go': '''scoreboard players set #stage mx 2
''' + tell('GO: walk straight east from blue to GOLD. Stop on gold until this crossing is recorded.'),
    'training_done': '''scoreboard players add #practice mx 1
scoreboard players remove #remaining mx 1
execute if score #remaining mx matches 0 run scoreboard players set #mode mx 2
''' + tell('Crossing exercise completed. Empty encounter gap next; use the skip button, then Next.') + '''
function macs_exit:gap''',
    'gap': '''function macs_exit:cleanup
scoreboard players set #stage mx 3
scoreboard players set #timer mx 0
scoreboard players set #stood mx 0
tp @s 3060.5 101 3008.5 90 0
''' + tell('Click to skip the EMPTY encounter gap. Wait for Sprint completed and Ready before clicking Next.', '/tick sprint 640t'),
    'ready': '''scoreboard players set #stage mx 4
''' + tell('Ready. Click NEXT to continue the exit exercise.', '/function macs_exit:next', 'green'),
    'next': '''execute unless score #stage mx matches 4 run function macs_exit:status
execute if score #stage mx matches 4 if score #mode mx matches 1 run function macs_exit:training
execute if score #stage mx matches 4 if score #mode mx matches 2 run function macs_exit:interception''',
    'interception': '''function macs_exit:cleanup
fill 3010 101 2998 3010 104 3005 minecraft:air
scoreboard players set #timer mx 0
scoreboard players set #stood mx 0
scoreboard players set #stage mx 5
execute if score #switches mx matches 5.. run scoreboard players set #stage mx 7
tp @s 3008.5 101 3008.5 -90 0
summon frozendawn:architect 3021.5 101 3008.5 {Tags:["macs_exit_actor"],PersistenceRequired:1b}
fd architect approach @e[tag=macs_exit_actor,limit=1] @s
fd architect record @e[tag=macs_exit_actor,limit=1] 1337
''' + tell('Stay on BLUE and watch which exit it chooses. Wait for the movement prompt. No attacks or items.'),
    'primary_ready': '''scoreboard players set #stage mx 6
scoreboard players set #stood mx 0
execute if score #hidden mx matches 1 run fill 3010 101 2998 3010 104 3005 minecraft:stone
''' + tell('It reached the usual EAST/GOLD exit. Now WALK north from blue to GREEN and stop. Leave it guarding the wrong exit.'),
    'switch_done': '''execute unless score #hidden mx matches 1 run scoreboard players add #switches mx 1
scoreboard players set #remaining mx 2
scoreboard players set #mode mx 1
execute if score #hidden mx matches 1 run scoreboard players set #mode mx 2
execute if score #switches mx matches 5.. run scoreboard players set #mode mx 2
scoreboard players set #hidden mx 0
fill 3010 101 2998 3010 104 3005 minecraft:air
''' + tell('Alternate-exit exercise recorded. Two ordinary GOLD crossings refresh the usual habit before the next interception. After five witnessed switches, we test the learned alternative.') + '''
function macs_exit:gap''',
    'alternative_ready': '''scoreboard players set #stage mx 8
scoreboard players set #stood mx 0
''' + tell('It reached NORTH/GREEN this time. Beat the prediction: WALK east to GOLD. Do not hit it. Watch whether it stays at the wrong exit.'),
    'finish': '''scoreboard players set #stage mx 9
execute as @e[tag=macs_exit_actor] run fd architect stop @s
execute as @e[tag=macs_exit_actor] run fd architect dump @s
execute as @e[tag=macs_exit_actor] run data merge entity @s {NoAI:1b,Motion:[0.0d,0.0d,0.0d]}
''' + tell('Replay paused and actor trace saved. Describe what happened, then run /fd maeve dump directly in chat. Leave this world intact.', '/fd maeve dump', 'aqua'),
    'timeout': '''function macs_exit:finish
''' + tell('The expected exit was not reached in time. This round is NOT a pass. Run /fd maeve dump and report it; do not reset the evidence.', '/fd maeve dump', 'red'),
    'hidden': '''execute if score #stage mx matches 4 if score #switches mx matches 0 run scoreboard players set #hidden mx 1
''' + tell('Hidden control armed only before the first interception. The screen blocks the witness at GREEN; this round will not count toward the five visible switches. Click Next when ready.', '/function macs_exit:next'),
    'status': 'tellraw @s [{"text":"Exit replay: stage="},{"score":{"name":"#stage","objective":"mx"}},{"text":" practice="},{"score":{"name":"#practice","objective":"mx"}},{"text":" witnessed-switch exercises="},{"score":{"name":"#switches","objective":"mx"}},{"text":". For actual beliefs, run /fd maeve dump."}]',
}
SCRIPTS['tick'] = '''execute if score #stage mx matches 1..8 run scoreboard players add #timer mx 1
execute if score #stage mx matches 1 if score #timer mx matches 40.. as @a[tag=macs_exit,limit=1] run function macs_exit:training_go
execute if score #stage mx matches 2 as @a[tag=macs_exit,x=3015,y=101,z=3007,dx=3,dy=2,dz=2] run scoreboard players add #stood mx 1
execute if score #stage mx matches 2 if score #stood mx matches 15.. as @a[tag=macs_exit,limit=1] run function macs_exit:training_done
execute if score #stage mx matches 3 if score #timer mx matches 620.. as @a[tag=macs_exit,limit=1] run function macs_exit:ready
execute if score #stage mx matches 5 if score #timer mx matches 80.. if entity @e[tag=macs_exit_actor,x=3015,y=101,z=3006,dx=3,dy=2,dz=4] as @a[tag=macs_exit,limit=1] run function macs_exit:primary_ready
execute if score #stage mx matches 6 as @a[tag=macs_exit,x=3007,y=101,z=2998,dx=2,dy=2,dz=3] run scoreboard players add #stood mx 1
execute if score #stage mx matches 6 if score #stood mx matches 15.. as @a[tag=macs_exit,limit=1] run function macs_exit:switch_done
execute if score #stage mx matches 7 if score #timer mx matches 80.. if entity @e[tag=macs_exit_actor,x=3006,y=101,z=2998,dx=4,dy=2,dz=3] as @a[tag=macs_exit,limit=1] run function macs_exit:alternative_ready
execute if score #stage mx matches 8 as @a[tag=macs_exit,x=3015,y=101,z=3007,dx=3,dy=2,dz=2] run scoreboard players add #stood mx 1
execute if score #stage mx matches 8 if score #stood mx matches 40.. as @a[tag=macs_exit,limit=1] run function macs_exit:finish
execute if score #stage mx matches 5 if score #timer mx matches 240.. as @a[tag=macs_exit,limit=1] run function macs_exit:timeout
execute if score #stage mx matches 7 if score #timer mx matches 240.. as @a[tag=macs_exit,limit=1] run function macs_exit:timeout'''


def write_pack(path, game_test=False):
    functions = path / 'data/macs_exit/function'
    functions.mkdir(parents=True, exist_ok=True)
    for name, content in SCRIPTS.items():
        (functions / f'{name}.mcfunction').write_text(content + '\n')
    if not game_test:
        (path / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 48, 'description': 'MACS ordinary-action exit interception replay'}}))
        tags = path / 'data/minecraft/tags/function'
        tags.mkdir(parents=True, exist_ok=True)
        for name in ('load', 'tick'):
            (tags / f'{name}.json').write_text(json.dumps({'values': [f'macs_exit:{name}']}))


def prepare(source, destination):
    if destination.exists():
        raise SystemExit(f'Refusing to overwrite {destination}')
    raw = gzip.decompress((source / 'level.dat').read_bytes())
    marker = b'\x08\x00\x09LevelName'
    if raw.count(marker) != 1:
        raise SystemExit('Expected one LevelName in the closed source world')
    at = raw.index(marker) + len(marker)
    length = int.from_bytes(raw[at:at + 2], 'big')
    title = b'MACS Second-favorite Exit'
    shutil.copytree(source, destination, ignore=shutil.ignore_patterns('session.lock'))
    (destination / 'level.dat').write_bytes(gzip.compress(raw[:at] + len(title).to_bytes(2, 'big') + title + raw[at + 2 + length:]))
    if (destination / 'datapacks').exists():
        shutil.rmtree(destination / 'datapacks')
    write_pack(destination / 'datapacks/macs-exit')
    print(f'Prepared {destination}; run /function macs_exit:setup in this disposable copy.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pack-only', type=Path)
    parser.add_argument('--source', type=Path)
    parser.add_argument('--destination', type=Path)
    args = parser.parse_args()
    if args.pack_only:
        write_pack(args.pack_only, True)
    elif args.source and args.destination:
        prepare(args.source, args.destination)
    else:
        parser.error('Use --pack-only or --source/--destination')

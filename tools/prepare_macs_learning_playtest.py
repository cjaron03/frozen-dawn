#!/usr/bin/env python3
"""Isolated Slice 7 replay. Observations come from player actions; only empty encounter gaps are sprinted."""
import argparse
import gzip
import json
import shutil
from pathlib import Path

def message(text, command=None, color='aqua'):
    value = {'text': text, 'color': color}
    if command: value['clickEvent'] = {'action': 'run_command', 'value': command}
    return 'tellraw @a[tag=macs_learning] ' + json.dumps(value)

def counter_arena(x, z, condition):
    # Keep each counter near its preceding scouts: inherited hints are local to 48 blocks.
    commands = [
        f'forceload add {x-12} {z-13} {x+20} {z+15}',
        f'fill {x-12} 100 {z-13} {x+20} 100 {z+15} minecraft:stone',
        f'fill {x-12} 101 {z-13} {x+20} 105 {z+15} minecraft:air',
        f'setblock {x} 100 {z} minecraft:green_concrete',
        f'setblock {x+5} 100 {z} minecraft:gold_block',
        f'tp @a[tag=macs_learning] {x}.5 101 {z}.5 -90 0',
        f'spawnpoint @s {x} 101 {z}',
        f'summon frozendawn:architect {x+6}.5 101 {z}.5 {{Tags:["macs_learning_actor"],PersistenceRequired:1b}}',
    ]
    return '\n'.join(f'execute {condition} score #topupdone ml matches 1 run {command}' for command in commands)

SCRIPTS = {
    'load': 'scoreboard objectives add ml dummy\nexecute unless score #last ml matches 3..5 run scoreboard players set #last ml 3',
    'cleanup': 'execute as @e[tag=macs_learning_actor] run fd architect dump @s\nexecute as @e[tag=macs_learning_actor] run data merge entity @s {NoAI:1b}\nkill @e[tag=macs_learning_actor]',
    'setup': '''function macs_learning:load
execute unless score #initialized ml matches 1 run function macs_learning:initialize
execute if score #initialized ml matches 1 run function macs_learning:status''',
    'initialize': '''scoreboard players set #initialized ml 1
scoreboard players set #round ml 0
scoreboard players set #stage ml 0
scoreboard players set #counter ml 0
scoreboard players set #last ml 3
scoreboard players set #topupdone ml 0
execute as @e[tag=maeve_focus_actor] run data merge entity @s {NoAI:1b}
kill @e[tag=maeve_focus_actor]
fd postmaeve set-erased
fd postmaeve reset-erased confirm
fd world set phase 6 late
fd maeve status
fd world set phase 0
gamerule doMobSpawning false
gamerule doDaylightCycle false
gamerule doWeatherCycle false
gamerule keepInventory true
weather clear
time set noon
tag @s add macs_learning
gamemode survival @s
clear @s
effect give @s minecraft:night_vision infinite 0 true
effect give @s minecraft:resistance infinite 4 true
forceload add 698 688 838 732
fill 700 100 688 834 100 732 minecraft:stone
fill 758 100 760 762 100 764 minecraft:stone
forceload add 758 760 762 764
function macs_learning:practice''',
    'practice': 'function macs_learning:cleanup\nscoreboard players set #timer ml 0\nscoreboard players set #settled ml 0\nscoreboard players set #stage ml 1',
    'restart': 'function macs_learning:cleanup\nfunction macs_learning:initialize',
    'crossed': '''function macs_learning:cleanup
scoreboard players set #stage ml 2
scoreboard players set #timer ml 0
tp @a[tag=macs_learning] 760.5 101 762.5
''' + message('Crossing recorded. Click to sprint the empty 620-tick gap; wait for Sprint completed and Ready.', '/tick sprint 620t', 'yellow'),
    'ready': 'scoreboard players set #stage ml 3\n' + message('Ready. Click Start. Stand on green until the scout finishes looking; when it turns away, follow at a comfortable distance for five seconds.', '/function macs_learning:start', 'green'),
    'start': 'execute if score #stage ml matches 3 run function macs_learning:dispatch',
    'dispatch': 'function macs_learning:cleanup\nscoreboard players set #stage ml 4\nscoreboard players set #timer ml 0',
    'finish': '''scoreboard players set #stage ml 5
execute as @e[tag=macs_learning_actor] run fd architect dump @s
execute as @e[tag=macs_learning_actor] run data merge entity @s {NoAI:1b}
''' + message('Paused after 23 seconds. Describe what happened, then click for /fd maeve dump. Keep this dump before Next.', '/fd maeve dump') + '\n' + message('Next continues practice or prepares the counter comparison.', '/function macs_learning:next', 'green'),
    'next': '''execute if score #stage ml matches 5 run function macs_learning:advance''',
    'advance': '''function macs_learning:cleanup
scoreboard players add #round ml 1
execute if score #round ml <= #last ml run function macs_learning:practice
execute if score #round ml > #last ml run function macs_learning:counter_gap''',
    'top_up': 'execute if score #stage ml matches 9..10 unless score #topupdone ml matches 1 run function macs_learning:top_up_start',
    'top_up_start': '''function macs_learning:cleanup
scoreboard players set #topupdone ml 1
scoreboard players set #last ml 5
scoreboard players set #round ml 4
effect give @a[tag=macs_learning] minecraft:resistance infinite 4 true
effect give @a[tag=macs_learning] minecraft:instant_health 1 4 true
forceload add 830 688 918 732
fill 830 100 688 914 100 732 minecraft:stone
function macs_learning:practice''',
    'counter_gap': '''function macs_learning:cleanup
scoreboard players set #stage ml 6
scoreboard players set #timer ml 0
tp @a[tag=macs_learning] 760.5 101 762.5
''' + message('Counter round: sprint only this empty gap and wait for Ready.', '/tick sprint 620t', 'yellow'),
    'counter_ready': 'scoreboard players set #stage ml 7\n' + message('Ready. Stay on green until it backs away, then follow onto gold. Stay there and land one bow hit while it holds. Keep it alive. Click Start.', '/function macs_learning:counter', 'green'),
    'counter': 'execute if score #stage ml matches 7 run function macs_learning:counter_start',
    'counter_start': 'function macs_learning:cleanup\n'
    + counter_arena(790, 705, 'unless') + '\n'
    + counter_arena(878, 719, 'if') + '''
effect clear @s minecraft:resistance
effect give @s minecraft:instant_health 1 4 true
clear @s
give @s minecraft:bow
give @s minecraft:arrow 32
give @s minecraft:golden_apple 4
fd architect record @e[tag=macs_learning_actor,limit=1] 1337
scoreboard players add #counter ml 1
scoreboard players set #stage ml 8
scoreboard players set #timer ml 0
''' + message('Counter round running for 26 seconds. Do not kill the Architect. Your safety command is /function macs_learning:stop.'),
    'counter_finish': '''scoreboard players set #stage ml 9
execute as @e[tag=macs_learning_actor] run fd architect dump @s
execute as @e[tag=macs_learning_actor] run data merge entity @s {NoAI:1b}
''' + message('Paused. Describe this round, then inspect the dump: conditional evidence and STRATEGY PERFORMANCE should explain it.', '/fd maeve dump') + '\n' + message('Repeat with the same memory. After two failed holds, compare the next encounter.', '/function macs_learning:repeat', 'green'),
    'repeat': 'execute if score #stage ml matches 9 run function macs_learning:counter_gap',
    'stop': '''execute as @e[tag=macs_learning_actor] run fd architect dump @s
execute as @e[tag=macs_learning_actor] run data merge entity @s {NoAI:1b}
scoreboard players set #stage ml 10
effect give @s minecraft:resistance infinite 4 true
''' + message('Stopped safely. This interrupted counter is inconclusive. Preserve the dump; /function macs_learning:counter_gap starts a later attempt.'),
    'status': '''scoreboard players list #round
scoreboard players list #stage
scoreboard players list #counter
scoreboard players list #last
''' + message('Stages: 1 crossing; 2 empty gap; 3 scout ready; 4 scouting; 5 describe/dump/Next; 6 counter gap; 7 counter ready; 8 counter running; 9 describe/dump/Repeat; 10 stopped.'),
    'tick': '''execute if score #stage ml matches 1..2 run scoreboard players add #timer ml 1
execute if score #stage ml matches 4 run scoreboard players add #timer ml 1
execute if score #stage ml matches 6 run scoreboard players add #timer ml 1
execute if score #stage ml matches 8 run scoreboard players add #timer ml 1
execute if score #stage ml matches 2 if score #timer ml matches 610.. run function macs_learning:ready
execute if score #stage ml matches 4 if score #timer ml matches 460.. run function macs_learning:finish
execute if score #stage ml matches 6 if score #timer ml matches 610.. run function macs_learning:counter_ready
execute if score #stage ml matches 8 if score #timer ml matches 520.. run function macs_learning:counter_finish''',
}

SCRIPTS['tick'] += '\nexecute if score #stage ml matches 1 if score #timer ml matches 60 run ' + message('Walk from blue through the doorway to gold now. Wait on gold for the crossing confirmation.', color='green')

for index in range(6):
    x = 704 + index * 32
    # Alternate entering/exiting so practice does not establish an unrelated four-exit bearing.
    start, end = (4, 8) if index % 2 == 0 else (8, 4)
    SCRIPTS['practice'] += f'\nexecute if score #round ml matches {index} run function macs_learning:practice_{index}'
    SCRIPTS['dispatch'] += f'\nexecute if score #round ml matches {index} run function macs_learning:scout_{index}'
    at_gold = f'@a[tag=macs_learning,x={x+end}.5,y=101,z=705.5,distance=..0.75]'
    active = f'execute if score #stage ml matches 1 if score #timer ml matches 60.. if score #round ml matches {index}'
    # Keep the real observer alive through two perception samples after arrival.
    SCRIPTS['tick'] += f'\n{active} unless entity {at_gold} run scoreboard players set #settled ml 0'
    SCRIPTS['tick'] += f'\n{active} if entity {at_gold} run scoreboard players add #settled ml 1'
    SCRIPTS['tick'] += f'\n{active} if score #settled ml matches 20.. run function macs_learning:crossed'
    SCRIPTS[f'practice_{index}'] = f'''fill {x} 101 697 {x+30} 108 722 minecraft:air
fill {x+2} 105 703 {x+5} 105 707 minecraft:stone
fill {x+2} 101 703 {x+5} 104 703 minecraft:oak_planks
fill {x+2} 101 707 {x+5} 104 707 minecraft:oak_planks
fill {x+2} 101 703 {x+2} 104 707 minecraft:oak_planks
fill {x+5} 101 703 {x+5} 104 707 minecraft:oak_planks
fill {x+5} 101 705 {x+5} 103 705 minecraft:air
setblock {x+start} 100 705 minecraft:lapis_block
setblock {x+end} 100 705 minecraft:gold_block
setblock {x+16} 100 715 minecraft:green_concrete
fill {x+11} 101 704 {x+13} 104 706 minecraft:bedrock
fill {x+12} 101 705 {x+12} 103 705 minecraft:air
setblock {x+11} 102 705 minecraft:air
summon frozendawn:architect {x+12}.5 101 705.5 {{Tags:["macs_learning_actor"],PersistenceRequired:1b}}
tp @a[tag=macs_learning] {x+start}.5 101 705.5
''' + message(f'{"Crossing " + str(index+1) + "/4" if index < 4 else "Follow-up " + str(index-3) + "/2"}: pause on blue for three seconds, then walk through the doorway to gold and wait for confirmation. The witness is contained; do not attack it.')
    SCRIPTS[f'scout_{index}'] = f'''fill {x+11} 101 704 {x+13} 104 706 minecraft:air
tp @a[tag=macs_learning] {x+16}.5 101 715.5
summon frozendawn:architect {x+22}.5 101 705.5 {{Tags:["macs_learning_actor"],PersistenceRequired:1b}}
fd architect record @e[tag=macs_learning_actor,limit=1] 1337
''' + message('Let it inspect first. Follow only when it turns away after thinking; stay in its open sightline for five seconds. Do not hit this scout.')

def write_pack(pack, game_test=False):
    functions = pack / 'data/macs_learning/function'; functions.mkdir(parents=True, exist_ok=True)
    for name, text in SCRIPTS.items(): (functions / f'{name}.mcfunction').write_text(text + '\n')
    if not game_test:
        (pack / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 48, 'description': 'MACS Slice 7 integrated learning replay'}}))
        tags = pack / 'data/minecraft/tags/function'; tags.mkdir(parents=True, exist_ok=True)
        for name in ('load', 'tick'): (tags / f'{name}.json').write_text(json.dumps({'values': [f'macs_learning:{name}']}))

def prepare(source, destination):
    if destination.exists(): raise SystemExit(f'Refusing to overwrite {destination}')
    level = gzip.decompress((source / 'level.dat').read_bytes()); marker = b'\x08\x00\x09LevelName'
    if level.count(marker) != 1: raise SystemExit('Expected one LevelName in the CLOSED source QA world')
    at = level.index(marker) + len(marker); length = int.from_bytes(level[at:at+2], 'big'); name = b'MACS Learning Encounter'
    shutil.copytree(source, destination)
    (destination / 'level.dat').write_bytes(gzip.compress(level[:at] + len(name).to_bytes(2, 'big') + name + level[at+2+length:]))
    pack = destination / 'datapacks/maeve-slice1'
    if pack.exists(): shutil.rmtree(pack)
    write_pack(pack)
    print(f'Prepared {destination}; /function macs_learning:setup resets only this disposable copy.')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path); parser.add_argument('--destination', type=Path)
    parser.add_argument('--pack-only', type=Path); parser.add_argument('--update-pack', type=Path)
    args = parser.parse_args()
    if args.pack_only: write_pack(args.pack_only, True)
    elif args.update_pack: write_pack(args.update_pack)
    elif args.source and args.destination: prepare(args.source, args.destination)
    else: parser.error('Use --source/--destination, --pack-only, or --update-pack')

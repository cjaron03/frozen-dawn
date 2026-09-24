#!/usr/bin/env python3
"""Create an isolated MACS replay from a CLOSED world; no injected observations."""
import argparse
import gzip
import json
import shutil
from pathlib import Path

SCRIPTS = {
    # Bundled QA functions are visible in every lab world. Only the dedicated
    # world's pack overrides this marker and supplies the progression tick tag.
    'fixture': 'return 0',
    'load': 'scoreboard objectives add mr dummy',
    'cleanup': '''execute as @e[tag=macs_recon_actor] run data merge entity @s {NoAI:1b}
kill @e[tag=macs_recon_actor]''',
    'setup': '''function macs_recon:load
execute if score #initialized mr matches 1 run function macs_recon:status
execute unless score #initialized mr matches 1 run function macs_recon:initialize''',
    'restart': '''function macs_recon:load
execute if score #initialized mr matches 1 run function macs_recon:retry
execute unless score #initialized mr matches 1 run function macs_recon:initialize
tellraw @s {"text":"If the game is frozen, run /tick unfreeze directly in chat to begin the ready prompt.","color":"yellow","clickEvent":{"action":"run_command","value":"/tick unfreeze"}}''',
    'retry': '''execute as @e[tag=macs_recon_actor] run fd architect dump @s
function macs_recon:cleanup
scoreboard players set #stage mr 0
scoreboard players set #wait mr 0
scoreboard players set #timer mr 0
tag @s add macs_recon
gamemode survival @s
effect give @s minecraft:resistance infinite 4 true
effect give @s minecraft:night_vision infinite 0 true
effect give @s minecraft:instant_health 1 4 true
tellraw @s {"text":"Restarting at the next unused site. Earlier observations and the failed actor trace are preserved.","color":"aqua"}
function macs_recon:next_site''',
    'initialize': '''scoreboard players set #initialized mr 1
scoreboard players set #site mr -1
scoreboard players set #stage mr 0
scoreboard players set #mode mr 0
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
tag @s add macs_recon
clear @s
gamemode survival @s
effect give @s minecraft:resistance infinite 4 true
effect give @s minecraft:night_vision infinite 0 true
function macs_recon:next_site''',
    'next_site': '''execute if score #stage mr matches 0 run function macs_recon:advance
execute if score #stage mr matches 5 run function macs_recon:advance''',
    'advance': '''function macs_recon:cleanup
scoreboard players add #site mr 1
scoreboard players set #wait mr 0
scoreboard players set #stage mr 1
execute if score #site mr matches 0 run function macs_recon:practice_0
execute if score #site mr matches 1 run function macs_recon:practice_1
execute if score #site mr matches 2 run function macs_recon:practice_2
execute if score #site mr matches 3.. run scoreboard players set #stage mr 6
execute if score #site mr matches 3.. run tellraw @s {"text":"All three sites used. Preserve this world and its dumps for review.","color":"aqua"}''',
    'crossed': '''function macs_recon:cleanup
scoreboard players set #stage mr 2
scoreboard players set #wait mr 0
tp @a[tag=macs_recon] 760.5 101 750.5 0 0
tellraw @a[tag=macs_recon] {"text":"Crossing exercise complete. Run /fd maeve dump directly to verify the observation. Click to skip the empty waiting gap, then wait for Sprint completed.","color":"yellow","clickEvent":{"action":"run_command","value":"/tick sprint 620t"}}''',
    'ready': '''scoreboard players set #stage mr 3
tellraw @a[tag=macs_recon] {"text":"Ready. After Sprint completed, click Start or run /function macs_recon:start. Watch the encounter and describe it before opening another dump.","color":"green","clickEvent":{"action":"run_command","value":"/function macs_recon:start"}}''',
    'start': '''execute unless score #stage mr matches 3 run return run function macs_recon:status
execute if score #stage mr matches 3 if score #site mr matches 0 run function macs_recon:encounter_0
execute if score #stage mr matches 3 if score #site mr matches 1 run function macs_recon:encounter_1
execute if score #stage mr matches 3 if score #site mr matches 2 run function macs_recon:encounter_2''',
    'tick': '''execute if score #stage mr matches 1 if score #site mr matches 0 run function macs_recon:crossing_0
execute if score #stage mr matches 1 if score #site mr matches 1 run function macs_recon:crossing_1
execute if score #stage mr matches 1 if score #site mr matches 2 run function macs_recon:crossing_2
execute if score #stage mr matches 1 if score #wait mr matches 20.. run function macs_recon:crossed
execute if score #stage mr matches 2 run scoreboard players add #wait mr 1
execute if score #stage mr matches 2 if score #wait mr matches 630.. run function macs_recon:ready
execute if score #stage mr matches 4 run scoreboard players add #timer mr 1
execute if score #stage mr matches 4 if score #timer mr matches 1100.. run function macs_recon:finish''',
    'walk_prompt': '''scoreboard players set #wait mr 0
scoreboard players set #stage mr 1
tellraw @a[tag=macs_recon] {"text":"Walk normally from blue through the doorway to gold now, then stop briefly.","color":"green"}''',
    'finish': '''scoreboard players set #stage mr 5
execute as @e[tag=macs_recon_actor] run fd architect dump @s
execute as @e[tag=macs_recon_actor] run data merge entity @s {NoAI:1b}
tellraw @a[tag=macs_recon] {"text":"Paused; actor trace exported. Describe what you saw, then run /fd maeve dump directly. The final pause is part of this test.","color":"aqua"}''',
    'sealed': '''execute if score #stage mr matches 5 run scoreboard players set #mode mr 1
execute if score #stage mr matches 5 run function macs_recon:next_site''',
    'status': '''scoreboard players list #site
scoreboard players list #stage
scoreboard players list #wait
tellraw @s {"text":"Stage 7: wait for the ready prompt. Stage 1: walk blue to gold. Stage 2: skip the empty gap and wait. Stage 3: /function macs_recon:start. Stage 4: running. Stage 5: preserve your dump; /function macs_recon:next_site uses a fresh site. If interrupted, /function macs_recon:restart preserves evidence and restarts at an unused site.","color":"yellow"}''',
}

for index in range(3):
    x = 700 + index * 100
    SCRIPTS[f'practice_{index}'] = f'''scoreboard players set #stage mr 7
forceload add {x-4} 698 {x+32} 722
forceload add 758 748 762 752
fill 758 100 748 762 100 752 minecraft:stone
fill {x-4} 100 698 {x+32} 100 722 minecraft:stone
fill {x-4} 101 698 {x+32} 110 722 minecraft:air
fill {x+2} 105 703 {x+5} 105 707 minecraft:stone
fill {x+2} 101 703 {x+5} 104 703 minecraft:oak_planks
fill {x+2} 101 707 {x+5} 104 707 minecraft:oak_planks
fill {x+2} 101 703 {x+2} 104 707 minecraft:oak_planks
fill {x+5} 101 703 {x+5} 104 707 minecraft:oak_planks
fill {x+5} 101 705 {x+5} 103 705 minecraft:air
setblock {x+4} 100 705 minecraft:lapis_block
setblock {x+8} 100 705 minecraft:gold_block
fill {x+14} 100 711 {x+18} 100 715 minecraft:green_concrete
fill {x+11} 101 704 {x+13} 104 706 minecraft:bedrock
fill {x+12} 101 705 {x+12} 103 705 minecraft:air
setblock {x+11} 102 705 minecraft:air
summon frozendawn:architect {x+12}.5 101 705.5 {{Tags:["macs_recon_actor"],PersistenceRequired:1b}}
tp @s {x+4}.5 101 705.5 -90 0
spawnpoint @s {x+16} 101 713
tellraw @s {{"text":"Stand on blue for the three-second ready prompt. Then walk through the doorway to gold. One crossing is enough; no potion practice needed.","color":"gold"}}'''
    SCRIPTS[f'crossing_{index}'] = f'execute if entity @a[tag=macs_recon,x={x+8},y=101,z=705,dx=1,dy=2,dz=1] run scoreboard players add #wait mr 1'
    SCRIPTS[f'encounter_{index}'] = f'''function macs_recon:cleanup
fill {x+11} 101 704 {x+13} 104 706 minecraft:air
execute if score #mode mr matches 1 run fill {x+5} 101 705 {x+5} 103 705 minecraft:stone
tp @a[tag=macs_recon] {x+16}.5 101 713.5 0 0
summon frozendawn:architect {x+22}.5 101 705.5 {{Tags:["macs_recon_actor"],PersistenceRequired:1b}}
fd architect record @e[tag=macs_recon_actor,limit=1] 1337
scoreboard players set #timer mr 0
scoreboard players set #stage mr 4
tellraw @a[tag=macs_recon] {{"text":"Encounter started. Stay near the green area initially, then move as you like. It pauses after 55 seconds, allowing the full withdrawal and return to finish.","color":"aqua"}}'''

SCRIPTS['tick'] += '''\nexecute if score #stage mr matches 7 run scoreboard players add #wait mr 1
execute if score #stage mr matches 7 if score #wait mr matches 60.. run function macs_recon:walk_prompt'''

def write_pack(pack, game_test=False):
    functions = pack / 'data/macs_recon/function'
    functions.mkdir(parents=True, exist_ok=True)
    for name, text in SCRIPTS.items():
        if name == 'fixture':
            text = 'return 0' if game_test else 'return 1'
        else:
            message = json.dumps({'text': 'Open MACS Recon Encounter from Singleplayer first. This world does not run the recon exercise; nothing was changed.', 'color': 'yellow'})
            text = f'execute unless function macs_recon:fixture run return run tellraw @s {message}\n' + text
        (functions / f'{name}.mcfunction').write_text(text + '\n')
    if not game_test:
        (pack / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 48, 'description': 'MACS Slice 6 isolated encounter'}}))
        tags = pack / 'data/minecraft/tags/function'; tags.mkdir(parents=True, exist_ok=True)
        for name in ('load', 'tick'):
            (tags / f'{name}.json').write_text(json.dumps({'values': [f'macs_recon:{name}']}))

def prepare(source, destination):
    if destination.exists(): raise SystemExit(f'Refusing to overwrite {destination}')
    level = gzip.decompress((source / 'level.dat').read_bytes()); marker = b'\x08\x00\x09LevelName'
    if level.count(marker) != 1: raise SystemExit('Expected one LevelName in the CLOSED source QA world')
    at = level.index(marker) + len(marker); length = int.from_bytes(level[at:at+2], 'big')
    name = b'MACS Recon Encounter'
    shutil.copytree(source, destination)
    (destination / 'level.dat').write_bytes(gzip.compress(level[:at] + len(name).to_bytes(2, 'big') + name + level[at+2+length:]))
    pack = destination / 'datapacks/maeve-slice1'
    if pack.exists(): shutil.rmtree(pack)
    write_pack(pack)
    print(f'Prepared {destination}. Initial /function macs_recon:setup resets only this disposable copy.')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path); parser.add_argument('--destination', type=Path)
    parser.add_argument('--pack-only', type=Path); parser.add_argument('--update-pack', type=Path)
    args = parser.parse_args()
    if args.pack_only: write_pack(args.pack_only, True)
    elif args.update_pack: write_pack(args.update_pack)
    elif args.source and args.destination: prepare(args.source, args.destination)
    else: parser.error('Use --source/--destination, --pack-only, or --update-pack')

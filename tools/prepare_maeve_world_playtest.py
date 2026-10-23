#!/usr/bin/env python3
"""Create a disposable Slice 4 shelter replay from a CLOSED QA world. No belief injection."""
import argparse
import gzip
import json
import shutil
from pathlib import Path


SCRIPTS = {
    'load': 'scoreboard objectives add mw_stage dummy\nscoreboard objectives add mw_round dummy\nscoreboard objectives add mw_wait dummy\nscoreboard objectives add mw_mode dummy\nscoreboard objectives add mw_retry dummy',
    'cleanup': '''execute as @e[tag=maeve_world_actor] run data merge entity @s {NoAI:1b}
kill @e[tag=maeve_world_actor]''',
    'setup': '''function maeve_world:load
function maeve_world:cleanup
execute as @e[tag=maeve_playtest_witness] run data merge entity @s {NoAI:1b}
kill @e[tag=maeve_playtest_witness]
tag @s remove maeve_playtest
tag @s remove maeve_training
tag @s add maeve_world
scoreboard players set @s mw_stage 0
scoreboard players set @s mw_round 0
scoreboard players set @s mw_mode 0
scoreboard players set @s mw_retry 0
schedule clear maeve_world:ready_all
schedule clear maeve_world:walk_prompt
schedule clear maeve_world:finish_all
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
forceload add 296 296 332 322
forceload add 358 306 362 310
fill 296 100 296 332 100 322 minecraft:stone
fill 296 101 296 332 109 322 minecraft:air
fill 358 100 306 362 100 310 minecraft:stone
fill 302 105 305 307 105 311 minecraft:stone
fill 302 101 305 307 104 305 minecraft:oak_planks
fill 302 101 311 307 104 311 minecraft:oak_planks
fill 302 101 305 302 104 311 minecraft:oak_planks
fill 307 101 305 307 104 311 minecraft:oak_planks
fill 302 101 307 302 103 309 minecraft:air
fill 307 101 307 307 103 309 minecraft:air
fill 309 100 307 310 100 309 minecraft:gold_block
setblock 304 100 308 minecraft:lapis_block
setblock 301 100 308 minecraft:emerald_block
clear @s
effect give @s minecraft:resistance infinite 4 true
effect give @s minecraft:night_vision infinite 0 true
gamemode survival @s
function maeve_world:practice_start''',
    'practice': '''execute unless score @s mw_stage matches 4 run function maeve_world:status
execute if score @s mw_stage matches 4 if score @s mw_round matches 5.. run function maeve_world:status
execute if score @s mw_stage matches 4 if score @s mw_round matches ..4 run function maeve_world:practice_start''',
    'practice_start': '''function maeve_world:cleanup
fill 319 101 307 321 104 309 minecraft:bedrock
fill 320 101 308 320 102 308 minecraft:air
setblock 319 102 308 minecraft:air
summon frozendawn:architect 320.5 101 308.5 {Tags:["maeve_world_actor"],PersistenceRequired:1b}
tp @s 304.5 101 308.5 -90 0
fd architect approach @e[tag=maeve_world_actor,limit=1] @s
scoreboard players set @s mw_stage 1
scoreboard players set @s mw_wait 0
schedule function maeve_world:walk_prompt 60t replace
tellraw @s {"text":"Stand on the blue tile. In three seconds, walk straight through the doorway ahead to the gold tiles. No attacks or items needed.","color":"gold"}''',
    'walk_prompt': '''scoreboard players set @a[tag=maeve_world,scores={mw_stage=1}] mw_stage 2
tellraw @a[tag=maeve_world,scores={mw_stage=2}] {"text":"Walk to the gold tiles now, and stop there briefly.","color":"green"}''',
    'tick': '''execute as @a[tag=maeve_world,scores={mw_stage=2},x=309,y=101,z=307,dx=1,dy=2,dz=2] run scoreboard players add @s mw_wait 1
execute as @a[tag=maeve_world,scores={mw_stage=2,mw_wait=20..}] run function maeve_world:crossed''',
    'crossed': '''scoreboard players add @s mw_round 1
function maeve_world:cleanup
fill 319 101 307 321 104 309 minecraft:air
execute if score @s mw_retry matches 1 run fill 307 101 307 307 103 309 minecraft:stone
function maeve_world:gap
execute if score @s mw_retry matches 0 run tellraw @s [{"text":"Crossing ","color":"aqua"},{"score":{"name":"@s","objective":"mw_round"}},{"text":"/5 completed. This counter tracks the exercise; inspect the direct dump to verify acceptance."}]
execute if score @s mw_retry matches 1 run tellraw @s {"text":"Extra crossing complete. The observer was dismissed and the east side sealed again. Your earlier observations were preserved.","color":"aqua"}
execute if score @s mw_round matches 5.. run function maeve_world:dump_prompt''',
    'gap': '''scoreboard players set @s mw_stage 3
tp @s 360.5 101 308.5 90 0
schedule function maeve_world:ready_all 630t replace
tellraw @s {"text":"Click to fast-forward the quiet gap, or type /tick sprint 640t. This advances ordinary game time; it changes no production values.","color":"yellow","clickEvent":{"action":"run_command","value":"/tick sprint 640t"}}''',
    'ready_all': '''execute as @a[tag=maeve_world,scores={mw_stage=3}] run function maeve_world:ready''',
    'ready': '''scoreboard players set @s mw_stage 4
execute if score @s mw_round matches ..4 run tellraw @s {"text":"Ready. Click for the next crossing, or type /function maeve_world:practice.","color":"green","clickEvent":{"action":"run_command","value":"/function maeve_world:practice"}}
execute if score @s mw_round matches 5.. if score @s mw_mode matches 0 run tellraw @s {"text":"Practice complete. Click to test the open entrance, or type /function maeve_world:open. Stay inside until the automatic pause.","color":"green","clickEvent":{"action":"run_command","value":"/function maeve_world:open"}}
execute if score @s mw_mode matches 1 if score @s mw_retry matches 0 run tellraw @s {"text":"Ready. Click for the sealed entrance encounter, or type /function maeve_world:blocked.","color":"green","clickEvent":{"action":"run_command","value":"/function maeve_world:blocked"}}
execute if score @s mw_mode matches 1 if score @s mw_retry matches 1 run tellraw @s {"text":"Retry prepared. Run /fd maeve dump and tell Codex done so eligibility can be checked before another encounter.","color":"aqua"}''',
    'open': '''execute unless score @s mw_stage matches 4 run function maeve_world:status
execute if score @s mw_stage matches 4 unless score @s mw_round matches 5.. run function maeve_world:status
execute if score @s mw_stage matches 4 unless score @s mw_mode matches 0 run function maeve_world:status
execute if score @s mw_stage matches 4 if score @s mw_round matches 5.. if score @s mw_mode matches 0 run function maeve_world:encounter''',
    'encounter': '''function maeve_world:cleanup
fill 319 101 307 321 104 309 minecraft:air
scoreboard players set @s mw_stage 5
tp @s 304.5 101 308.5 -90 0
summon frozendawn:architect 322.5 101 308.5 {Tags:["maeve_world_actor"],PersistenceRequired:1b}
fd architect approach @e[tag=maeve_world_actor,limit=1] @s
fd architect record @e[tag=maeve_world_actor,limit=1] 1337
execute if score @s mw_mode matches 0 run tellraw @s {"text":"Watch from inside until the pause, about 22 seconds. Let it pick a position before doing anything. Stay inside and avoid attacks or recovery items during this comparison.","color":"green"}
execute if score @s mw_mode matches 1 run tp @s 314.5 101 316.5 150 0
execute if score @s mw_mode matches 1 run tellraw @s {"text":"The east doorway was sealed while no Architect was present. Watch its approach and any reaction to the wall. The replay pauses after 30 seconds.","color":"green"}
execute if score @s mw_mode matches 0 run schedule function maeve_world:finish_all 450t replace
execute if score @s mw_mode matches 1 run schedule function maeve_world:finish_all 600t replace''',
    'finish_all': 'execute as @a[tag=maeve_world,scores={mw_stage=5}] run function maeve_world:finish',
    'finish': '''scoreboard players set @s mw_stage 6
function maeve_world:dump_prompt
fd architect stop @e[tag=maeve_world_actor,limit=1]
fd architect dump @e[tag=maeve_world_actor,limit=1]
data merge entity @e[tag=maeve_world_actor,limit=1] {NoAI:1b,Motion:[0.0d,0.0d,0.0d]}
execute if score @s mw_mode matches 0 run tellraw @s {"text":"Open entrance replay exported. Tell Codex what happened. Then click here, or run /function maeve_world:sealed, to seal that side and prepare the comparison.","color":"aqua","clickEvent":{"action":"run_command","value":"/function maeve_world:sealed"}}
execute if score @s mw_mode matches 1 run tellraw @s {"text":"Sealed entrance replay exported. Tell Codex what you saw. The actor trace is saved. Click the dump link above to record Maeve's explanation.","color":"aqua"}''',
    'sealed': '''execute unless score @s mw_stage matches 6 run function maeve_world:status
execute if score @s mw_stage matches 6 unless score @s mw_mode matches 0 run function maeve_world:status
execute if score @s mw_stage matches 6 if score @s mw_mode matches 0 run function maeve_world:seal_prepare''',
    'seal_prepare': '''function maeve_world:cleanup
scoreboard players set @s mw_mode 1
fill 307 101 307 307 103 309 minecraft:stone
function maeve_world:dump_prompt
function maeve_world:gap''',
    'blocked': '''execute unless score @s mw_stage matches 4 run function maeve_world:status
execute if score @s mw_stage matches 4 unless score @s mw_mode matches 1 run function maeve_world:status
execute if score @s mw_stage matches 4 if score @s mw_mode matches 1 run function maeve_world:encounter''',
    'dump_prompt': '''tellraw @s {"text":"Click to record Maeve's explanation, or run /fd maeve dump directly. Function feedback cannot save this dump automatically.","color":"yellow","clickEvent":{"action":"run_command","value":"/fd maeve dump"}}''',
    'status': '''execute unless entity @s[tag=maeve_world] run tellraw @s {"text":"Start with /function maeve_world:setup in the disposable shelter world.","color":"yellow"}
execute if score @s mw_stage matches 1..2 run tellraw @s {"text":"Practice is active. Follow the blue-to-gold crossing prompt.","color":"yellow"}
execute if score @s mw_stage matches 3 run tellraw @s {"text":"The quiet gap is still running. Use /tick sprint 640t, then wait for Ready.","color":"yellow"}
execute if score @s mw_stage matches 4 if score @s mw_round matches ..4 run tellraw @s {"text":"More crossings are needed. Run /function maeve_world:practice.","color":"yellow"}
execute if score @s mw_stage matches 4 if score @s mw_round matches 5.. if score @s mw_mode matches 0 run tellraw @s {"text":"The open comparison is ready: /function maeve_world:open.","color":"yellow"}
execute if score @s mw_stage matches 4 if score @s mw_mode matches 1 run tellraw @s {"text":"The sealed comparison is prepared. Check /fd maeve dump, then use /function maeve_world:blocked.","color":"yellow"}
execute if score @s mw_stage matches 5 run tellraw @s {"text":"An encounter is already running. Wait for its automatic pause.","color":"yellow"}
execute if score @s mw_stage matches 6 if score @s mw_mode matches 0 run tellraw @s {"text":"Open comparison complete. Use /function maeve_world:sealed for the next stage.","color":"yellow"}
execute if score @s mw_stage matches 6 if score @s mw_mode matches 1 run tellraw @s {"text":"The sealed round already finished; blocked does not restart it. Preserve /fd maeve dump. Use /function maeve_world:retry_blocked for one extra crossing and a fresh comparison.","color":"yellow"}''',
    'retry_blocked': '''execute unless score @s mw_stage matches 6 run function maeve_world:status
execute if score @s mw_stage matches 6 unless score @s mw_mode matches 1 run function maeve_world:status
execute if score @s mw_stage matches 6 if score @s mw_mode matches 1 run function maeve_world:retry_start''',
    'retry_start': '''function maeve_world:load
schedule clear maeve_world:ready_all
schedule clear maeve_world:walk_prompt
schedule clear maeve_world:finish_all
function maeve_world:cleanup
fill 307 101 307 307 103 309 minecraft:air
scoreboard players set @s mw_retry 1
function maeve_world:practice_start''',
}


def write_pack(pack):
    pack.mkdir(parents=True, exist_ok=True)
    (pack / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 48, 'description': 'Maeve Slice 4 observed entrances and discovery QA'}}))
    functions = pack / 'data/maeve_world/function'
    functions.mkdir(parents=True, exist_ok=True)
    for name, text in SCRIPTS.items():
        (functions / f'{name}.mcfunction').write_text(text + '\n')
    tags = pack / 'data/minecraft/tags/function'
    tags.mkdir(parents=True, exist_ok=True)
    for name in ('load', 'tick'):
        (tags / f'{name}.json').write_text(json.dumps({'values': [f'maeve_world:{name}']}))


def prepare(source, destination):
    if destination.exists():
        raise SystemExit(f'Refusing to overwrite {destination}')
    level = gzip.decompress((source / 'level.dat').read_bytes())
    marker = b'\x08\x00\x09LevelName'
    if level.count(marker) != 1:
        raise SystemExit('Expected exactly one LevelName in the closed QA world')
    at = level.index(marker) + len(marker)
    old_length = int.from_bytes(level[at:at + 2], 'big')
    name = b'Maeve Shelter Encounter'
    renamed = level[:at] + len(name).to_bytes(2, 'big') + name + level[at + 2 + old_length:]
    shutil.copytree(source, destination)
    (destination / 'level.dat').write_bytes(gzip.compress(renamed))
    # Replace only the disposable copy's already-enabled QA pack. Source remains intact.
    pack = destination / 'datapacks/maeve-slice1'
    if pack.exists():
        shutil.rmtree(pack)
    write_pack(pack)
    print(f'Prepared {destination}. Run /function maeve_world:setup.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path)
    parser.add_argument('--destination', type=Path)
    parser.add_argument('--pack-only', type=Path, help='Export a QA pack for command parsing verification')
    args = parser.parse_args()
    if args.pack_only:
        write_pack(args.pack_only)
    elif args.source and args.destination:
        prepare(args.source, args.destination)
    else:
        parser.error('Use --source and --destination, or --pack-only')

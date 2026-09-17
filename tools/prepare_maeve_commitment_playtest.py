#!/usr/bin/env python3
"""Prepare a disposable copy of a CLOSED Slice 2 QA world; never seed beliefs."""
import argparse
import gzip
import json
import shutil
from pathlib import Path


def prepare(source, destination):
    if destination.exists():
        raise SystemExit(f'Refusing to overwrite {destination}')
    old_name, new_name = b'Maeve Slice 2', b'Maeve Slice 3'
    level = gzip.decompress((source / 'level.dat').read_bytes())
    marker = b'\x08\x00\x09LevelName' + len(old_name).to_bytes(2, 'big') + old_name
    if level.count(marker) != 1:
        raise SystemExit('Expected the closed Maeve Slice 2 QA world')
    shutil.copytree(source, destination)
    (destination / 'level.dat').write_bytes(gzip.compress(level.replace(marker, marker.replace(old_name, new_name))))
    write_pack(destination)
    print(f'Prepared {destination}. Open Maeve Slice 3 and run /function maeve_playtest:setup.')


def write_pack(destination):
    """Write only QA datapack files; safe to reload in an already open test world."""
    pack = destination / 'datapacks' / 'maeve-slice1'  # Already enabled in the copied world.
    (pack / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 48, 'description': 'Maeve Slice 3 informed playtest'}}))
    functions = pack / 'data' / 'maeve_playtest' / 'function'
    functions.mkdir(parents=True, exist_ok=True)
    tags = pack / 'data' / 'minecraft' / 'tags' / 'function'
    tags.mkdir(parents=True, exist_ok=True)
    (tags / 'tick.json').write_text(json.dumps({'values': ['maeve_playtest:tick']}))
    scripts = {
        'setup': '''gamerule doMobSpawning false
gamerule doDaylightCycle false
gamerule doWeatherCycle false
gamerule keepInventory true
weather clear
time set noon
fd postmaeve set-erased
fd postmaeve reset-erased confirm
fd world set phase 6 late
fd maeve status
fd world set phase 0
schedule clear maeve_playtest:next
schedule clear maeve_playtest:deploy
schedule clear maeve_playtest:prompt
schedule clear maeve_playtest:checkpoint
schedule clear maeve_playtest:end
scoreboard objectives add maeve_round dummy
scoreboard objectives add maeve_uses minecraft.used:minecraft.potion
scoreboard objectives add maeve_done dummy
scoreboard objectives add maeve_goal dummy
scoreboard objectives add maeve_blind dummy
scoreboard players set @s maeve_round 0
scoreboard players set @s maeve_uses 0
scoreboard players set @s maeve_done 0
scoreboard players set @s maeve_goal 4
scoreboard players set @s maeve_blind 0
tag @s add maeve_playtest
forceload add 198 198 230 222
forceload add 278 206 282 210
fill 198 100 198 230 100 222 minecraft:stone
fill 198 101 198 230 108 222 minecraft:air
fill 278 100 206 282 100 210 minecraft:stone
kill @e[tag=maeve_slice1_witness]
kill @e[tag=maeve_playtest_witness]
function maeve_playtest:shelter
function maeve_playtest:training_observer
effect give @s minecraft:resistance 999999 4 true
gamemode survival @s
function maeve_playtest:training''',
        'setup_blind': '''function maeve_playtest:setup
scoreboard players set @s maeve_blind 1''',
        'shelter': '''fill 211 104 207 213 104 209 minecraft:stone
fill 213 101 207 213 103 207 minecraft:oak_log
fill 213 101 209 213 103 209 minecraft:oak_log''',
        'training_observer': '''fill 203 101 207 205 104 209 minecraft:bedrock
fill 204 101 208 204 102 208 minecraft:air
setblock 205 102 208 minecraft:air
summon frozendawn:architect 204.5 101 208.5 {Tags:["maeve_playtest_witness"],PersistenceRequired:1b,Health:200.0f,Attributes:[{Name:"minecraft:generic.movement_speed",Base:0.0},{Name:"minecraft:generic.max_health",Base:200.0},{Name:"minecraft:generic.knockback_resistance",Base:1.0}]}''',
        'training': '''tag @s add maeve_training
scoreboard players set @s maeve_uses 0
tp @s 212.5 101 208.5 90 0
item replace entity @s hotbar.0 with minecraft:potion[minecraft:potion_contents={potion:"minecraft:healing"}]
function maeve_playtest:progress''',
        'progress': '''tellraw @s [{"text":"Potion practice: ","color":"gold"},{"score":{"name":"@s","objective":"maeve_done"}},{"text":"/"},{"score":{"name":"@s","objective":"maeve_goal"}},{"text":" completed. Stand here and finish the potion in slot 1."}]''',
        'tick': '''execute as @a[tag=maeve_playtest,tag=maeve_training,scores={maeve_round=0..3,maeve_uses=1..}] run function maeve_playtest:consumed''',
        'consumed': '''tag @s remove maeve_training
scoreboard players set @s maeve_uses 0
scoreboard players add @s maeve_round 1
scoreboard players add @s maeve_done 1
tp @s 280.5 101 208.5 90 0
tellraw @s [{"text":"Completed ","color":"gray"},{"score":{"name":"@s","objective":"maeve_done"}},{"text":"/"},{"score":{"name":"@s","objective":"maeve_goal"}},{"text":". Wait here for 31 seconds. The next stage starts automatically."}]
schedule function maeve_playtest:next 630t replace''',
        'repair': '''forceload add 198 198 230 222
forceload add 278 206 282 210
fill 278 100 206 282 100 210 minecraft:stone
execute as @a[tag=maeve_playtest,scores={maeve_round=0..3},x=198,y=99,z=198,dx=32,dy=10,dz=24] run tag @s add maeve_training
execute as @a[tag=maeve_playtest,tag=maeve_training,scores={maeve_round=0..3}] run function maeve_playtest:progress
tellraw @s {"text":"Waiting platform repaired. Completed rounds and observed beliefs are preserved. Continue the current round.","color":"green"}''',
        'next': '''execute as @a[tag=maeve_playtest,scores={maeve_round=0..3}] run function maeve_playtest:training
execute as @a[tag=maeve_playtest,scores={maeve_round=4}] run function maeve_playtest:armed''',
        'armed': '''scoreboard players set @s maeve_round 5
tellraw @s {"text":"Practice complete. Click here to begin the encounter, or type /function maeve_playtest:encounter.","color":"green","clickEvent":{"action":"run_command","value":"/function maeve_playtest:encounter"}}''',
        'encounter': '''execute if score @s maeve_round matches 5 run function maeve_playtest:start''',
        'start': '''scoreboard players set @s maeve_round 6
kill @e[tag=maeve_playtest_witness]
fill 203 101 207 205 104 209 minecraft:air
function maeve_playtest:shelter
summon frozendawn:architect 207.5 101 208.5 {Tags:["maeve_playtest_witness"],PersistenceRequired:1b}
item replace entity @s hotbar.0 with minecraft:iron_sword
item replace entity @s hotbar.1 with minecraft:bow
item replace entity @s hotbar.2 with minecraft:potion[minecraft:potion_contents={potion:"minecraft:healing"}]
item replace entity @s hotbar.3 with minecraft:potion[minecraft:potion_contents={potion:"minecraft:healing"}]
item replace entity @s hotbar.4 with minecraft:golden_apple 4
item replace entity @s hotbar.8 with minecraft:arrow 64
fd architect record @e[tag=maeve_playtest_witness,limit=1] 1337
schedule function maeve_playtest:deploy 50t replace
tellraw @s {"text":"Preparing the encounter...","color":"gray"}''',
        'deploy': '''execute as @a[tag=maeve_playtest,scores={maeve_round=6}] run function maeve_playtest:ready''',
        'ready': '''scoreboard players set @s maeve_round 7
tp @s 214.5 101 213.5 135 0
execute if score @s maeve_blind matches 0 run schedule function maeve_playtest:prompt 100t replace
schedule function maeve_playtest:checkpoint 200t replace
schedule function maeve_playtest:end 600t replace
execute if score @s maeve_blind matches 0 run tellraw @s {"text":"Encounter ready. Move around and keep the Architect in view. Follow the potion prompt, and keep it alive. This controlled check pauses automatically after 30 seconds.","color":"green"}
execute if score @s maeve_blind matches 1 run tellraw @s {"text":"Use the space and supplies as you see fit. Keep the Architect alive for this 30-second encounter. After the automatic pause, describe what happened.","color":"green"}''',
        'prompt': '''tellraw @a[tag=maeve_playtest,scores={maeve_round=7}] {"text":"Finish the healing potion in slot 3 now, in view of the Architect. Then keep moving and watching it.","color":"gold"}''',
        'checkpoint': '''execute as @a[tag=maeve_playtest,scores={maeve_round=7}] if entity @e[tag=maeve_playtest_witness,limit=1] run fd architect dump @e[tag=maeve_playtest_witness,limit=1]''',
        'end': '''execute as @a[tag=maeve_playtest,scores={maeve_round=7}] run function maeve_playtest:finish''',
        'finish': '''scoreboard players set @s maeve_round 8
schedule clear maeve_playtest:prompt
schedule clear maeve_playtest:checkpoint
schedule clear maeve_playtest:end
scoreboard objectives add maeve_export dummy
scoreboard players set @s maeve_export 0
execute if entity @e[tag=maeve_playtest_witness,limit=1] run data merge entity @e[tag=maeve_playtest_witness,limit=1] {NoAI:1b,Motion:[0.0d,0.0d,0.0d]}
fd architect stop @e[tag=maeve_playtest_witness,limit=1]
execute store result score @s maeve_export run fd architect dump @e[tag=maeve_playtest_witness,limit=1]
forceload remove 198 198 230 222
forceload remove 278 206 282 210
execute if score @s maeve_export matches 1.. run tellraw @s {"text":"Paused and exported. Tell Codex what stood out before opening the explanation.","color":"aqua"}
execute if score @s maeve_export matches 0 run tellraw @s {"text":"No final actor trace was exported. The Architect may have died; any earlier checkpoint and Maeve's direct dump can still help. Tell Codex what happened.","color":"red"}''',
        'retry': '''schedule clear maeve_playtest:next
schedule clear maeve_playtest:deploy
schedule clear maeve_playtest:prompt
schedule clear maeve_playtest:checkpoint
schedule clear maeve_playtest:end
forceload add 198 198 230 222
forceload add 278 206 282 210
fill 278 100 206 282 100 210 minecraft:stone
tp @s 280.5 101 208.5 90 0
kill @e[tag=maeve_playtest_witness]
fill 198 100 198 230 100 222 minecraft:stone
fill 198 101 198 230 108 222 minecraft:air
function maeve_playtest:shelter
tag @s remove maeve_training
scoreboard players set @s maeve_round 4
schedule function maeve_playtest:next 630t replace
tellraw @s {"text":"Reusing observed history. Wait here for 31 seconds; the start prompt will appear automatically.","color":"gold"}''',
        'refresh': '''schedule clear maeve_playtest:next
schedule clear maeve_playtest:deploy
schedule clear maeve_playtest:prompt
schedule clear maeve_playtest:checkpoint
schedule clear maeve_playtest:end
scoreboard objectives add maeve_done dummy
scoreboard objectives add maeve_goal dummy
scoreboard objectives add maeve_blind dummy
tag @s remove maeve_training
tag @s add maeve_playtest
scoreboard players set @s maeve_round 2
scoreboard players set @s maeve_done 0
scoreboard players set @s maeve_goal 2
scoreboard players set @s maeve_blind 0
scoreboard players set @s maeve_uses 0
forceload add 198 198 230 222
forceload add 278 206 282 210
fill 278 100 206 282 100 210 minecraft:stone
tp @s 280.5 101 208.5 90 0
kill @e[tag=maeve_playtest_witness]
fill 198 100 198 230 100 222 minecraft:stone
fill 198 101 198 230 108 222 minecraft:air
function maeve_playtest:shelter
function maeve_playtest:training_observer
effect give @s minecraft:resistance 999999 4 true
gamemode survival @s
schedule function maeve_playtest:next 630t replace
tellraw @s {"text":"Two potion-practice rounds queued. Wait here for 31 seconds, then finish slot 1 when prompted. Your observed history is preserved.","color":"gold"}''',
        'inspect': '''tellraw @s {"text":"Click to run the direct Maeve dump (function feedback would be suppressed).","color":"aqua","clickEvent":{"action":"run_command","value":"/fd maeve dump"}}''',
    }
    for name, body in scripts.items():
        (functions / f'{name}.mcfunction').write_text(body + '\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path, nargs='?')
    parser.add_argument('destination', type=Path, nargs='?')
    parser.add_argument('--update-pack', type=Path, help='Update only datapack files in an existing Slice 3 QA world')
    args = parser.parse_args()
    if args.update_pack:
        if args.source or args.destination:
            parser.error('--update-pack cannot be combined with source/destination')
        world = args.update_pack.resolve()
        if not (world / 'datapacks/maeve-slice1/data/maeve_playtest/function/setup.mcfunction').is_file():
            parser.error('--update-pack requires an existing Maeve playtest datapack')
        write_pack(world)
        print(f'Updated only the QA datapack in {world}. Run /reload, then /function maeve_playtest:refresh.')
    else:
        if not args.source or not args.destination:
            parser.error('source and destination are required when creating a world')
        prepare(args.source.resolve(), args.destination.resolve())

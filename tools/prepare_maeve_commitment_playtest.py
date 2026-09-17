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
    pack = destination / 'datapacks' / 'maeve-slice1'  # Already enabled in the copied world.
    (pack / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 48, 'description': 'Maeve Slice 3 informed playtest'}}))
    functions = pack / 'data' / 'maeve_playtest' / 'function'
    functions.mkdir(parents=True)
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
scoreboard objectives add maeve_round dummy
scoreboard objectives add maeve_uses minecraft.used:minecraft.potion
scoreboard players set @s maeve_round 0
scoreboard players set @s maeve_uses 0
tag @s add maeve_playtest
forceload add 198 198 230 222
forceload add 278 206 282 210
fill 198 100 198 230 100 222 minecraft:stone
fill 198 101 198 230 108 222 minecraft:air
fill 278 100 206 282 100 210 minecraft:stone
fill 203 101 207 205 104 209 minecraft:bedrock
fill 204 101 208 204 102 208 minecraft:air
setblock 205 102 208 minecraft:air
fill 211 104 207 213 104 209 minecraft:stone
kill @e[tag=maeve_slice1_witness]
kill @e[tag=maeve_playtest_witness]
summon frozendawn:architect 204.5 101 208.5 {Tags:["maeve_playtest_witness"],PersistenceRequired:1b,Health:200.0f,Attributes:[{Name:"minecraft:generic.movement_speed",Base:0.0},{Name:"minecraft:generic.max_health",Base:200.0},{Name:"minecraft:generic.knockback_resistance",Base:1.0}]}
effect give @s minecraft:resistance 999999 4 true
gamemode survival @s
function maeve_playtest:training''',
        'training': '''tag @s add maeve_training
scoreboard players set @s maeve_uses 0
tp @s 212.5 101 208.5 90 0
item replace entity @s hotbar.0 with minecraft:potion[minecraft:potion_contents={potion:"minecraft:healing"}]
function maeve_playtest:progress''',
        'progress': '''tellraw @s [{"text":"Training progress: ","color":"gold"},{"score":{"name":"@s","objective":"maeve_round"}},{"text":"/4 completed. Stand here and finish the potion in slot 1."}]''',
        'tick': '''execute as @a[tag=maeve_playtest,tag=maeve_training,scores={maeve_round=0..3,maeve_uses=1..}] run function maeve_playtest:consumed''',
        'consumed': '''tag @s remove maeve_training
scoreboard players set @s maeve_uses 0
scoreboard players add @s maeve_round 1
tp @s 280.5 101 208.5 90 0
tellraw @s [{"text":"Completed ","color":"gray"},{"score":{"name":"@s","objective":"maeve_round"}},{"text":"/4. Next stage in 31 seconds; click here to fast-forward.","clickEvent":{"action":"run_command","value":"/tick sprint 640"}}]
schedule function maeve_playtest:next 630t replace''',
        'repair': '''forceload add 198 198 230 222
forceload add 278 206 282 210
fill 278 100 206 282 100 210 minecraft:stone
execute as @a[tag=maeve_playtest,scores={maeve_round=0..3},x=198,y=99,z=198,dx=32,dy=10,dz=24] run tag @s add maeve_training
execute as @a[tag=maeve_playtest,tag=maeve_training,scores={maeve_round=0..3}] run function maeve_playtest:progress
tellraw @s {"text":"Waiting platform repaired. Completed rounds and observed beliefs are preserved. Continue the current round.","color":"green"}''',
        'next': '''execute as @a[tag=maeve_playtest,scores={maeve_round=0..3}] run function maeve_playtest:training
execute as @a[tag=maeve_playtest,scores={maeve_round=4}] run function maeve_playtest:encounter''',
        'encounter': '''scoreboard players set @s maeve_round 5
kill @e[tag=maeve_playtest_witness]
fill 203 101 207 205 104 209 minecraft:air
fill 211 104 207 213 104 209 minecraft:air
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
        'deploy': '''execute as @a[tag=maeve_playtest,scores={maeve_round=5}] run function maeve_playtest:ready''',
        'ready': '''tp @s 214.5 101 213.5 135 0
tellraw @s {"text":"Encounter ready. Spend about half a minute moving and using your equipment as you normally would. Keep the Architect alive so we can inspect the encounter afterward. Then run /function maeve_playtest:finish and describe what you noticed.","color":"green"}''',
        'finish': '''data merge entity @e[tag=maeve_playtest_witness,limit=1] {NoAI:1b}
fd architect stop @e[tag=maeve_playtest_witness,limit=1]
fd architect dump @e[tag=maeve_playtest_witness,limit=1]
forceload remove 198 198 230 222
forceload remove 278 206 282 210
tellraw @s {"text":"Paused and recorded. Tell Codex what the Architect did that stood out, before looking at its explanation.","color":"aqua"}''',
        'inspect': '''fd maeve dump
fd maeve explain PLAYER_USES_RECOVERY_UNDER_COVER
time query gametime
data get entity @e[tag=maeve_playtest_witness,limit=1] UUID
data get entity @e[tag=maeve_playtest_witness,limit=1] Pos''',
    }
    for name, body in scripts.items():
        (functions / f'{name}.mcfunction').write_text(body + '\n')
    print(f'Prepared {destination}. Open Maeve Slice 3 and run /function maeve_playtest:setup.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('destination', type=Path)
    args = parser.parse_args()
    prepare(args.source.resolve(), args.destination.resolve())

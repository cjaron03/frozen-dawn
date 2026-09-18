#!/usr/bin/env python3
"""Build a disposable, 12-second attention comparison. No fabricated beliefs or slot injection."""
import argparse
import gzip
import json
import shutil
from pathlib import Path


def packed(x, y, z):
    return ((x & 0x3ffffff) << 38) | ((z & 0x3ffffff) << 12) | (y & 0xfff)


SCRIPTS = {
    'load': 'scoreboard objectives add ma_stage dummy\nscoreboard objectives add ma_mode dummy',
    'cleanup': '''schedule clear maeve_attention:prompt
schedule clear maeve_attention:finish_all
execute as @e[tag=maeve_attention_actor] run data merge entity @s {NoAI:1b}
kill @e[tag=maeve_attention_actor]''',
    'setup': '''function maeve_attention:load
function maeve_attention:cleanup
tag @s add maeve_attention
tag @s remove maeve_world
tag @s remove maeve_playtest
tag @s remove maeve_training
execute as @e[tag=maeve_world_actor] run data merge entity @s {NoAI:1b}
kill @e[tag=maeve_world_actor]
fd postmaeve set-erased
fd postmaeve reset-erased confirm
fd world preset cinematic
fd world set phase 6 late
fd maeve status
fd world set phase 0
fd hearth relationship set orsathae
gamerule doMobSpawning false
gamerule doDaylightCycle false
gamerule doWeatherCycle false
gamerule keepInventory true
gamerule mobGriefing false
weather clear
time set noon
forceload add 388 388 454 431
fill 388 100 388 454 100 431 minecraft:bedrock
fill 388 101 388 454 105 431 minecraft:air
setblock 400 100 400 minecraft:lapis_block
clear @s
effect give @s minecraft:resistance infinite 4 true
effect give @s minecraft:night_vision infinite 0 true
gamemode survival @s
tp @s 400.5 101 400.5 -90 0
scoreboard players set @s ma_stage 0
tellraw @s {"text":"Attention comparison ready. First run /function maeve_attention:control. Stay on blue and watch the ordinary Architect to your right. Each round pauses after 12 seconds.","color":"aqua"}''',
    'control': '''scoreboard players set @s ma_mode 0
function maeve_attention:start''',
    'tactic': '''scoreboard players set @s ma_mode 1
function maeve_attention:start''',
    'start': f'''function maeve_attention:cleanup
tp @s 400.5 101 400.5 -90 0
fill 399 101 407 401 104 409 minecraft:bedrock
fill 400 101 408 400 102 408 minecraft:air
setblock 400 102 407 minecraft:air
fill 399 101 395 401 104 398 minecraft:bedrock
fill 400 101 396 400 102 397 minecraft:air
setblock 400 101 397 minecraft:bedrock
setblock 400 101 398 minecraft:oak_door[facing=south,half=lower,hinge=left,open=false]
setblock 400 102 398 minecraft:oak_door[facing=south,half=upper,hinge=left,open=false]
summon frozendawn:architect 400.5 101 408.5 {{Tags:["maeve_attention_actor","maeve_attention_master_a"],PersistenceRequired:1b,HearthMasterArchitectId:[I;10,11,12,13],HearthMasterArchitectHome:{packed(400,101,408)}L}}
summon frozendawn:architect 400.5 101 396.5 {{Tags:["maeve_attention_actor","maeve_attention_master_b"],PersistenceRequired:1b,HearthMasterArchitectId:[I;20,21,22,23],HearthMasterArchitectHome:{packed(400,101,396)}L}}
summon frozendawn:architect 426.5 101 400.5 {{Tags:["maeve_attention_actor","maeve_attention_stalker"],PersistenceRequired:1b}}
fd architect record @e[tag=maeve_attention_stalker,limit=1] 1337
scoreboard players set @s ma_stage 1
schedule function maeve_attention:prompt 150t replace
schedule function maeve_attention:finish_all 240t replace
execute if score @s ma_mode matches 0 run tellraw @s {{"text":"CONTROL: stay on blue, leave the nearby wooden door closed, and watch the ordinary Architect. Ignore the two large ones.","color":"green"}}
execute if score @s ma_mode matches 1 run tellraw @s {{"text":"TACTIC: stay on blue. At the green prompt, right-click the nearby wooden door once, then look right at the ordinary Architect. Keep the door open. No attacks needed.","color":"green"}}''',
    'prompt': '''execute as @a[tag=maeve_attention,scores={ma_stage=1,ma_mode=0}] run tellraw @s {"text":"Keep the door CLOSED and keep watching.","color":"green"}
execute as @a[tag=maeve_attention,scores={ma_stage=1,ma_mode=1}] run tellraw @s {"text":"OPEN the wooden door now, then look right at the ordinary Architect.","color":"green"}
playsound minecraft:block.note_block.pling master @a[tag=maeve_attention,scores={ma_stage=1}] 400 102 400 1 1''',
    'finish_all': 'execute as @a[tag=maeve_attention,scores={ma_stage=1}] run function maeve_attention:finish',
    'finish': '''scoreboard players set @s ma_stage 2
fd architect stop @e[tag=maeve_attention_stalker,limit=1]
fd architect dump @e[tag=maeve_attention_stalker,limit=1]
execute as @e[tag=maeve_attention_actor] run data merge entity @s {NoAI:1b,Motion:[0.0d,0.0d,0.0d]}
tellraw @s {"text":"Round paused. Describe what changed before reading the explanation. Then run /fd maeve dump directly. Use /function maeve_attention:tactic for the door-open round, or repeat it to check reliability.","color":"aqua"}''',
}


def write_pack(pack, game_test=False):
    pack.mkdir(parents=True, exist_ok=True)
    if not game_test:
        (pack / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 48, 'description': 'Maeve Slice 5 attention comparison QA'}}))
    functions = pack / 'data/maeve_attention/function'
    functions.mkdir(parents=True, exist_ok=True)
    for name, text in SCRIPTS.items():
        (functions / f'{name}.mcfunction').write_text(text + '\n')
    if not game_test:
        tags = pack / 'data/minecraft/tags/function'
        tags.mkdir(parents=True, exist_ok=True)
        (tags / 'load.json').write_text(json.dumps({'values': ['maeve_attention:load']}))
    else:
        for stale in (pack / 'pack.mcmeta', pack / 'data/minecraft/tags/function/load.json'):
            stale.unlink(missing_ok=True)


def prepare(source, destination):
    if destination.exists():
        raise SystemExit(f'Refusing to overwrite {destination}')
    level = gzip.decompress((source / 'level.dat').read_bytes())
    marker = b'\x08\x00\x09LevelName'
    if level.count(marker) != 1:
        raise SystemExit('Expected exactly one LevelName in the closed QA world')
    at = level.index(marker) + len(marker)
    old_length = int.from_bytes(level[at:at + 2], 'big')
    name = b'Maeve Attention Encounter'
    renamed = level[:at] + len(name).to_bytes(2, 'big') + name + level[at + 2 + old_length:]
    shutil.copytree(source, destination)
    (destination / 'level.dat').write_bytes(gzip.compress(renamed))
    pack = destination / 'datapacks/maeve-slice1'
    if pack.exists():
        shutil.rmtree(pack)
    write_pack(pack)
    print(f'Prepared {destination}. Run /function maeve_attention:setup.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path)
    parser.add_argument('--destination', type=Path)
    parser.add_argument('--pack-only', type=Path)
    args = parser.parse_args()
    if args.pack_only:
        write_pack(args.pack_only, game_test=True)
    elif args.source and args.destination:
        prepare(args.source, args.destination)
    else:
        parser.error('Use --source and --destination, or --pack-only')

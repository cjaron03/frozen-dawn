#!/usr/bin/env python3
"""Two-client, ordinary-Architect QA. Player actions supply every observation."""
import json
from pathlib import Path


SCRIPTS = {
    'load': 'scoreboard objectives add mf dummy',
    'setup': '''execute unless entity @a[name=ArchitectGuest] run tellraw @s {"text":"Join ArchitectGuest through the LAN entry first, then run this command in the host window.","color":"yellow"}
execute if entity @a[name=ArchitectGuest] unless entity @s[name=ArchitectGuest] run function maeve_focus:setup_host''',
    'setup_host': '''function maeve_focus:load
function maeve_focus:cleanup
function maeve_world:cleanup
schedule clear maeve_world:ready_all
schedule clear maeve_world:walk_prompt
schedule clear maeve_world:finish_all
tag @a remove maeve_world
tag @a remove maeve_training
tag @a remove maeve_focus_host
tag @a remove maeve_focus_guest
tag @s add maeve_focus_host
tag @a[name=ArchitectGuest] add maeve_focus_guest
tag @s add maeve_focus
tag @a[name=ArchitectGuest] add maeve_focus
fd world preset cinematic
fd world set phase 6 late
fd maeve status
fd world set phase 0
gamerule doMobSpawning false
gamerule doDaylightCycle false
gamerule doWeatherCycle false
gamerule keepInventory true
weather clear
time set noon
forceload add 480 496 544 552
forceload add 596 500 604 508
fill 485 100 498 529 100 545 minecraft:bedrock
fill 485 101 498 529 109 545 minecraft:air
fill 596 100 500 604 100 508 minecraft:bedrock
spawnpoint @a[tag=maeve_focus] 600 101 504
fill 518 105 502 522 105 506 minecraft:stone
fill 512 101 500 512 104 508 minecraft:bedrock
fill 512 101 500 517 104 500 minecraft:bedrock
fill 512 101 508 517 104 508 minecraft:bedrock
setblock 490 100 504 minecraft:emerald_block
setblock 520 100 504 minecraft:lapis_block
setblock 491 101 504 minecraft:lever[face=floor,facing=south,powered=false]
clear @a[tag=maeve_focus]
gamemode survival @a[tag=maeve_focus]
effect give @a[tag=maeve_focus] minecraft:resistance infinite 4 true
effect give @a[tag=maeve_focus] minecraft:night_vision infinite 0 true
scoreboard players set #round mf 0
scoreboard players set #goal mf 5
scoreboard players set #purpose mf 0
tp @a[tag=maeve_focus_host] 600.5 101 504.5 0 0
function maeve_focus:training''',
    'cleanup': '''schedule clear maeve_focus:ready
execute as @e[tag=maeve_focus_actor] run data merge entity @s {NoAI:1b}
kill @e[tag=maeve_focus_actor]''',
    'training': '''scoreboard players set #stage mf 1
scoreboard players set #finished mf 0
fill 517 101 501 517 104 507 minecraft:air
fill 513 101 503 515 104 505 minecraft:bedrock
fill 514 101 504 514 102 504 minecraft:air
setblock 515 102 504 minecraft:air
summon frozendawn:architect 514.5 101 504.5 {Tags:["maeve_focus_actor"],PersistenceRequired:1b}
tp @a[tag=maeve_focus_guest] 520.5 101 504.5 90 0
item replace entity @a[tag=maeve_focus_guest] hotbar.0 with minecraft:potion[minecraft:potion_contents={potion:"minecraft:healing"}]
tellraw @a[tag=maeve_focus] [{"text":"Guest practice: ","color":"gold"},{"score":{"name":"#round","objective":"mf"}},{"text":"/"},{"score":{"name":"#goal","objective":"mf"}},{"text":" finished. In ArchitectGuest, drink the potion in slot 1 while standing on the blue tile."}]''',
    'potion_finished': '''advancement revoke @s only maeve_focus:practice_potion
execute if entity @s[tag=maeve_focus_guest] if score #stage mf matches 1 run scoreboard players set #finished mf 1''',
    'consumed': '''scoreboard players set #finished mf 0
scoreboard players add #round mf 1
tellraw @a[tag=maeve_focus] [{"text":"Drink completed: ","color":"aqua"},{"score":{"name":"#round","objective":"mf"}},{"text":"/"},{"score":{"name":"#goal","objective":"mf"}},{"text":". The direct dump verifies which observations Maeve accepted."}]
function maeve_focus:gap''',
    'gap': '''function maeve_focus:cleanup
scoreboard players set #stage mf 2
tp @a[tag=maeve_focus_host] 600.5 101 502.5 0 0
tp @a[tag=maeve_focus_guest] 600.5 101 506.5 180 0
schedule function maeve_focus:ready 630t replace
tellraw @a[tag=maeve_focus] {"text":"Click here to skip the empty waiting gap, or run /tick sprint 620t once. No encounter is active; all production timings stay unchanged.","color":"yellow","clickEvent":{"action":"run_command","value":"/tick sprint 620t"}}''',
    'ready': '''scoreboard players set #stage mf 3
execute if score #purpose mf matches 0 if score #round mf < #goal mf run tellraw @a[tag=maeve_focus] {"text":"After Sprint completed appears, click here for the next potion, or run /function maeve_focus:practice.","color":"green","clickEvent":{"action":"run_command","value":"/function maeve_focus:practice"}}
execute if score #purpose mf matches 0 if score #round mf >= #goal mf run tellraw @a[tag=maeve_focus] {"text":"Practice complete. In ArchitectGuest, run /fd maeve dump and tell Codex done. Stay here while the observations are checked.","color":"green"}
execute if score #purpose mf matches 1 run tellraw @a[tag=maeve_focus_host] {"text":"Ready. After Sprint completed appears, click to start the 16-second comparison, or run /function maeve_focus:start in this host window.","color":"green","clickEvent":{"action":"run_command","value":"/function maeve_focus:start"}}''',
    'practice': '''execute if score #stage mf matches 3 if score #purpose mf matches 0 if score #round mf < #goal mf run function maeve_focus:training''',
    'top_up': '''execute if score #stage mf matches 3 run function maeve_focus:prepare_top_up
execute if score #stage mf matches 5 run function maeve_focus:prepare_top_up''',
    'prepare_top_up': '''scoreboard players operation #goal mf = #round mf
scoreboard players add #goal mf 1
scoreboard players set #purpose mf 0
tellraw @a[tag=maeve_focus] {"text":"One extra observed drink is prepared. Existing evidence is preserved.","color":"aqua"}
function maeve_focus:gap''',
    'control': '''execute if entity @s[tag=maeve_focus_host] if score #stage mf matches 3 if score #round mf matches 5.. run function maeve_focus:prepare_control
execute if entity @s[tag=maeve_focus_host] if score #stage mf matches 5 run function maeve_focus:prepare_control''',
    'tactic': '''execute if entity @s[tag=maeve_focus_host] if score #stage mf matches 3 if score #round mf matches 5.. run function maeve_focus:prepare_tactic
execute if entity @s[tag=maeve_focus_host] if score #stage mf matches 5 run function maeve_focus:prepare_tactic''',
    'prepare_control': '''scoreboard players set #mode mf 0
scoreboard players set #purpose mf 1
function maeve_focus:gap''',
    'prepare_tactic': '''scoreboard players set #mode mf 1
scoreboard players set #purpose mf 1
function maeve_focus:gap''',
    'start': '''execute if entity @s[tag=maeve_focus_host] if entity @a[tag=maeve_focus_guest] if score #stage mf matches 3 if score #purpose mf matches 1 run function maeve_focus:start_round''',
    'start_round': '''scoreboard players set #stage mf 4
scoreboard players set #timer mf 0
scoreboard players set #opened mf 0
fill 517 101 501 517 104 507 minecraft:bedrock
fill 513 101 503 515 104 505 minecraft:air
setblock 491 101 504 minecraft:lever[face=floor,facing=south,powered=false]
tp @a[tag=maeve_focus_host] 490.5 101 504.5 0 0
tp @a[tag=maeve_focus_guest] 520.5 101 504.5 0 0
summon frozendawn:architect 490.5 101 530.5 {Tags:["maeve_focus_actor","maeve_focus_stalker"],PersistenceRequired:1b}
fd architect record @e[tag=maeve_focus_stalker,limit=1] 1337
execute if score #mode mf matches 0 run tellraw @a[tag=maeve_focus_host] {"text":"CONTROL: leave the lever OFF. Stay on the green tile and watch your Architect for 16 seconds. Leave the guest on its blue tile.","color":"gold"}
execute if score #mode mf matches 1 run tellraw @a[tag=maeve_focus_host] {"text":"PRESSURE: flip the lever beside you ON now, then watch your Architect. The shutter opens after eight seconds. Stay on the green tile; leave the guest on its blue tile.","color":"gold"}''',
    'others': '''summon frozendawn:architect 520.5 101 530.5 {Tags:["maeve_focus_actor","maeve_focus_tracker"],PersistenceRequired:1b}
summon frozendawn:architect 514.5 101 504.5 {Tags:["maeve_focus_actor","maeve_focus_committer"],PersistenceRequired:1b}
fd architect record @e[tag=maeve_focus_tracker,limit=1] 1337
fd architect record @e[tag=maeve_focus_committer,limit=1] 1337''',
    'open': '''scoreboard players set #opened mf 1
fill 517 101 501 517 104 507 minecraft:air
tellraw @a[tag=maeve_focus_host] {"text":"The other encounter is open. Keep watching your Architect.","color":"yellow"}''',
    'tick': '''execute if score #stage mf matches 1 if score #finished mf matches 1 run function maeve_focus:consumed
execute if score #stage mf matches 4 unless entity @a[tag=maeve_focus_host] run function maeve_focus:abort
execute if score #stage mf matches 4 unless entity @a[tag=maeve_focus_guest] run function maeve_focus:abort
execute if score #stage mf matches 4 run scoreboard players add #timer mf 1
execute if score #stage mf matches 4 if score #timer mf matches 20 run function maeve_focus:others
execute if score #stage mf matches 4 if score #mode mf matches 1 if score #opened mf matches 0 if score #timer mf matches 160..200 if block 491 101 504 minecraft:lever[powered=true] run function maeve_focus:open
execute if score #stage mf matches 4 if score #timer mf matches 201 if score #mode mf matches 1 if score #opened mf matches 0 run tellraw @a[tag=maeve_focus_host] {"text":"The lever was not opened in time. This round cannot count as a pressure test; use tactic again after the pause.","color":"red"}
execute if score #stage mf matches 4 if score #timer mf matches 320.. run function maeve_focus:finish''',
    'finish': '''scoreboard players set #stage mf 5
execute as @e[tag=maeve_focus_actor] run fd architect stop @s
execute as @e[tag=maeve_focus_actor] run fd architect dump @s
execute as @e[tag=maeve_focus_actor] run data merge entity @s {NoAI:1b,Motion:[0.0d,0.0d,0.0d]}
tellraw @a[tag=maeve_focus_host] {"text":"Paused and actor traces exported. Describe what your Architect did, then run /fd maeve dump in this host window. The final pause is part of the fixture.","color":"aqua"}''',
    'abort': '''function maeve_focus:finish
tellraw @a[tag=maeve_focus] {"text":"A player disconnected; this round is incomplete. Rejoin before preparing another round.","color":"red"}''',
}


def write_pack(pack, game_test=False):
    pack = Path(pack)
    functions = pack / 'data/maeve_focus/function'
    functions.mkdir(parents=True, exist_ok=True)
    for name, text in SCRIPTS.items():
        (functions / f'{name}.mcfunction').write_text(text + '\n')
    advancements = pack / 'data/maeve_focus/advancement'
    advancements.mkdir(parents=True, exist_ok=True)
    # Defer progression one tick: the vanilla consume trigger precedes NeoForge's Finish hook.
    (advancements / 'practice_potion.json').write_text(json.dumps({
        'criteria': {'drink': {'trigger': 'minecraft:consume_item',
                               'conditions': {'item': {'items': ['minecraft:potion']}}}},
        'rewards': {'function': 'maeve_focus:potion_finished'},
    }))
    if not game_test:
        tags = pack / 'data/minecraft/tags/function'
        tags.mkdir(parents=True, exist_ok=True)
        for name in ('load', 'tick'):
            path = tags / f'{name}.json'
            data = json.loads(path.read_text()) if path.exists() else {'values': []}
            if f'maeve_focus:{name}' not in data['values']:
                data['values'].append(f'maeve_focus:{name}')
            path.write_text(json.dumps(data))


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--update-pack', type=Path, required=True)
    write_pack(parser.parse_args().update_pack)

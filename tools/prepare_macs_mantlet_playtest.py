#!/usr/bin/env python3
"""Disposable mantlet trial: five real bow encounters; no synthetic belief edits."""
import argparse
import gzip
import json
import shutil
from pathlib import Path


def tell(message, command=None, color='aqua'):
    value = {'text': message, 'color': color}
    if command:
        value['clickEvent'] = {'action': 'run_command', 'value': command}
    return 'tellraw @s ' + json.dumps(value)


SCRIPTS = {
    'load': 'scoreboard objectives add mm dummy\nscoreboard objectives add mm_deaths deathCount',
    'setup': '''tag @s add macs_mantlet
execute unless score #initialized mm matches 1 run function macs_mantlet:initialize
function macs_mantlet:status''',
    'initialize': '''scoreboard players set #initialized mm 1
scoreboard players set #trained mm 0
scoreboard players set #stage mm 5
scoreboard players set #timer mm 0
fd postmaeve set-erased
fd postmaeve reset-erased confirm
fd world preset default
fd world set phase 6 late
fd maeve status
fd world set phase 0
gamerule doMobSpawning false
gamerule keepInventory true
gamerule doDaylightCycle false
gamerule doWeatherCycle false
difficulty normal
time set 1000
weather clear
forceload remove all
forceload add 2392 2392 2448 2448
forceload add 2416 2464 2432 2480
''' + tell('Preparing the separate frozen arena. Wait for Ready; Practice spawns the first observer.'),
    'build': '''fill 2400 100 2400 2440 100 2432 frozendawn:frozen_dirt
fill 2400 101 2400 2440 110 2432 minecraft:air
fill 2400 101 2400 2440 104 2400 minecraft:bedrock
fill 2400 101 2432 2440 104 2432 minecraft:bedrock
fill 2400 101 2400 2400 104 2432 minecraft:bedrock
fill 2440 101 2400 2440 104 2432 minecraft:bedrock
fill 2418 100 2466 2426 100 2474 minecraft:stone
fill 2418 101 2466 2426 105 2474 minecraft:air
function macs_mantlet:terrain
spawnpoint @s 2422 101 2470
tp @s 2422.5 101 2470.5 180 0
gamemode survival @s
scoreboard players set #stage mm 0
''' + tell('Ready. Click Practice, then shoot through the booth window ONCE.', '/function macs_mantlet:practice', 'green'),
    'terrain': '''fill 2401 101 2401 2439 105 2431 minecraft:air
fill 2401 101 2401 2439 101 2431 minecraft:snow[layers=2]
fill 2408 101 2410 2411 101 2413 minecraft:snow[layers=4]
fill 2412 101 2419 2415 101 2422 minecraft:snow[layers=6]
setblock 2420 100 2416 minecraft:lime_concrete
setblock 2404 100 2416 minecraft:blue_concrete''',
    'kit': '''clear @s
item replace entity @s armor.head with minecraft:iron_helmet
item replace entity @s armor.chest with minecraft:iron_chestplate
item replace entity @s armor.legs with minecraft:iron_leggings
item replace entity @s armor.feet with minecraft:iron_boots
item replace entity @s hotbar.0 with minecraft:bow
item replace entity @s hotbar.1 with minecraft:iron_sword
item replace entity @s hotbar.2 with minecraft:iron_pickaxe
item replace entity @s hotbar.3 with minecraft:shield
item replace entity @s hotbar.4 with minecraft:potion[minecraft:potion_contents="minecraft:strong_healing"] 1
item replace entity @s hotbar.5 with minecraft:cooked_beef 32
item replace entity @s hotbar.8 with minecraft:arrow 64
effect give @s minecraft:instant_health 1 4 true
effect give @s minecraft:saturation 1 4 true''',
    'practice': '''execute if score #stage mm matches 0 if score #trained mm matches ..4 run function macs_mantlet:practice_ready
execute unless score #stage mm matches 0 run function macs_mantlet:status''',
    'practice_ready': '''function macs_mantlet:terrain
function macs_mantlet:kit
effect give @s minecraft:resistance 9999 4 true
advancement revoke @s only macs_mantlet:bow_hit
scoreboard players set #stage mm 1
tp @s 2420.5 101.125 2416.5 90 0
fill 2403 101 2415 2405 104 2417 minecraft:bedrock
fill 2404 101 2416 2404 103 2416 minecraft:air
setblock 2405 102 2416 minecraft:air
summon frozendawn:architect 2404.5 101 2416.5 {Tags:["macs_mantlet_actor","macs_mantlet_training"],PersistenceRequired:1b}
execute as @e[tag=macs_mantlet_actor] run fd architect record @s 1337
''' + tell('One actual arrow hit through the window completes this practice round. The ordinary observer is contained.'),
    'hit': 'execute if score #stage mm matches 1 run function macs_mantlet:accepted',
    'accepted': '''scoreboard players set #stage mm 2
scoreboard players set #timer mm 0
scoreboard players add #trained mm 1
function macs_mantlet:remove_actor
tp @s 2422.5 101 2470.5 180 0
tellraw @s [{"text":"Bow encounters completed: "},{"score":{"name":"#trained","objective":"mm"}},{"text":"/5."}]
''' + tell('Skip only the empty gap. Wait for Ready AND Sprint completed.', '/tick sprint 620t', 'yellow'),
    'ready': '''scoreboard players set #stage mm 0
execute if score #trained mm matches ..4 run tellraw @s {"text":"Ready. Click for the next bow encounter.","color":"green","clickEvent":{"action":"run_command","value":"/function macs_mantlet:practice"}}
execute if score #trained mm matches 4 run tellraw @s {"text":"Optional pillar comparison at four hits: /function macs_mantlet:start. Otherwise complete the fifth practice hit.","color":"aqua"}
execute if score #trained mm matches 5.. run tellraw @s {"text":"Ready: five real bow encounters recorded. Run /fd maeve dump and reply done before the first mantlet encounter.","color":"green"}''',
    'start': '''execute if score #stage mm matches 0 if score #trained mm matches 4.. run function macs_mantlet:start_ready
execute unless score #stage mm matches 0 run function macs_mantlet:status''',
    'start_ready': '''function macs_mantlet:terrain
function macs_mantlet:kit
effect clear @s minecraft:resistance
scoreboard players set @s mm_deaths 0
scoreboard players set #stage mm 3
scoreboard players set #timer mm 0
tp @s 2420.5 101.125 2416.5 90 0
summon frozendawn:architect 2404.5 101.125 2416.5 {Tags:["macs_mantlet_actor"],PersistenceRequired:1b}
execute as @e[tag=macs_mantlet_actor] run fd architect record @s 1337
''' + tell('Real fight. Let it make its opening move before shooting, then try its front and flanks. No automatic freeze. Describe it before reading a dump. Finish exports and ends the round.', '/function macs_mantlet:finish'),
    'snapshot': 'execute as @e[tag=macs_mantlet_actor] run fd architect dump @s',
    'remove_actor': '''function macs_mantlet:snapshot
execute as @e[tag=macs_mantlet_actor] run data merge entity @s {NoAI:1b}
kill @e[tag=macs_mantlet_actor]''',
    'finish': 'execute if score #stage mm matches 3 run function macs_mantlet:end',
    'abort': '''execute if score #stage mm matches 1 run function macs_mantlet:end
execute if score #stage mm matches 3 run function macs_mantlet:end''',
    'end': '''scoreboard players set #stage mm 4
function macs_mantlet:remove_actor
tp @s 2422.5 101 2470.5 180 0
effect clear @s minecraft:resistance
''' + tell('Round closed; traces saved. Manual finish/abort is not a victory. Describe it, then /fd maeve dump. Again preserves learning.', '/function macs_mantlet:again'),
    'again': 'execute if score #stage mm matches 4 run function macs_mantlet:gap',
    'gap': '''scoreboard players set #stage mm 2
scoreboard players set #timer mm 0
''' + tell('Empty gap: sprint, wait for Ready, then /function macs_mantlet:start.', '/tick sprint 620t', 'yellow'),
    'status': '''tellraw @s [{"text":"Bow practice "},{"score":{"name":"#trained","objective":"mm"}},{"text":"/5; stage "},{"score":{"name":"#stage","objective":"mm"}},{"text":" (0 ready, 1 practice, 2 gap, 3 fight, 4 finished, 5 building)."}]''',
}
chunks = [(x, z) for x in (2384, 2400, 2416, 2432, 2448) for z in (2384, 2400, 2416, 2432, 2448)]
chunks += [(x, z) for x in (2416, 2432) for z in (2464, 2480)]
SCRIPTS['tick'] = '\n'.join([
    'execute if score #stage mm matches 5 ' + ' '.join(f'if loaded {x} 100 {z}' for x, z in chunks)
    + ' as @a[tag=macs_mantlet,limit=1] run function macs_mantlet:build',
    'execute if score #stage mm matches 2 run scoreboard players add #timer mm 1',
    'execute if score #stage mm matches 2 if score #timer mm matches 620.. as @a[tag=macs_mantlet,limit=1] run function macs_mantlet:ready',
    'execute if score #stage mm matches 3 run scoreboard players add #timer mm 1',
    'execute if score #stage mm matches 3 if score #timer mm matches 200.. run function macs_mantlet:snapshot',
    'execute if score #stage mm matches 3 if score #timer mm matches 200.. run scoreboard players set #timer mm 0',
    'execute if score #stage mm matches 3 as @a[tag=macs_mantlet,scores={mm_deaths=1..},limit=1] run function macs_mantlet:end',
    'execute if score #stage mm matches 3 unless entity @e[tag=macs_mantlet_actor] as @a[tag=macs_mantlet,limit=1] run function macs_mantlet:end',
])
ADVANCEMENT = {'criteria': {'bow': {'trigger': 'minecraft:player_hurt_entity', 'conditions': {
    'damage': {'dealt': {'min': .1}, 'blocked': False, 'type': {'tags': [{'id': 'minecraft:is_projectile', 'expected': True}]}},
    'entity': {'type': 'frozendawn:architect', 'nbt': '{Tags:["macs_mantlet_training"]}'},
}}}, 'rewards': {'function': 'macs_mantlet:hit'}}


def write_pack(path, game_test=False):
    functions = path / 'data/macs_mantlet/function'; functions.mkdir(parents=True, exist_ok=True)
    for name, content in SCRIPTS.items():
        (functions / f'{name}.mcfunction').write_text(content + '\n')
    advancements = path / 'data/macs_mantlet/advancement'; advancements.mkdir(parents=True, exist_ok=True)
    (advancements / 'bow_hit.json').write_text(json.dumps(ADVANCEMENT, indent=2) + '\n')
    if not game_test:
        (path / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 48, 'description': 'MACS mantlet ordinary-action acceptance'}}))
        tags = path / 'data/minecraft/tags/function'; tags.mkdir(parents=True, exist_ok=True)
        for name in ('load', 'tick'):
            (tags / f'{name}.json').write_text(json.dumps({'values': [f'macs_mantlet:{name}']}))


def prepare(source, destination):
    if destination.exists(): raise SystemExit(f'Refusing to overwrite {destination}')
    raw = gzip.decompress((source / 'level.dat').read_bytes()); marker = b'\x08\x00\x09LevelName'
    if raw.count(marker) != 1: raise SystemExit('Expected one LevelName in the closed QA world')
    at = raw.index(marker) + len(marker); length = int.from_bytes(raw[at:at+2], 'big')
    name = b'MACS Mantlet - Normal'
    shutil.copytree(source, destination, ignore=shutil.ignore_patterns('session.lock'))
    (destination / 'level.dat').write_bytes(gzip.compress(raw[:at] + len(name).to_bytes(2, 'big') + name + raw[at+2+length:]))
    shutil.rmtree(destination / 'datapacks')
    write_pack(destination / 'datapacks/macs-mantlet')
    print(f'Prepared {destination}; /function macs_mantlet:setup initializes only this disposable copy.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pack-only', type=Path); parser.add_argument('--source', type=Path); parser.add_argument('--destination', type=Path)
    args = parser.parse_args()
    if args.pack_only: write_pack(args.pack_only, True)
    elif args.source and args.destination: prepare(args.source, args.destination)
    else: parser.error('Use --pack-only or --source/--destination')


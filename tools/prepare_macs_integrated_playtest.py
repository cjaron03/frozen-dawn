#!/usr/bin/env python3
"""Paired, disposable MACS camp trials. Player actions provide all tactical evidence."""
import argparse
import gzip
import json
import shutil
from pathlib import Path


def tell(text, command=None, color='aqua'):
    value = {'text': text, 'color': color}
    if command:
        value['clickEvent'] = {'action': 'run_command', 'value': command}
    return 'tellraw @a[tag=macs_trial] ' + json.dumps(value)


def build_camp():
    lines = ['fill 2000 99 2000 2096 99 2096 minecraft:stone',
             'fill 2000 100 2000 2096 100 2096 minecraft:grass_block']
    # Each fill remains below vanilla's 32768-block command limit.
    for y in range(101, 119, 3):
        lines.append(f'fill 2000 {y} 2000 2096 {y+2} 2096 minecraft:air')
    for a, b in [('2000 101 2000', '2096 104 2000'), ('2000 101 2096', '2096 104 2096'),
                 ('2000 101 2000', '2000 104 2096'), ('2096 101 2000', '2096 104 2096')]:
        lines.append(f'fill {a} {b} minecraft:bedrock')
    lines += ['fill 2014 100 2047 2080 100 2049 minecraft:dirt_path',
              'fill 2047 100 2016 2049 100 2080 minecraft:dirt_path',
              'fill 2038 100 2038 2058 100 2058 minecraft:spruce_planks',
              'fill 2038 101 2038 2058 104 2038 minecraft:spruce_planks',
              'fill 2038 101 2058 2058 104 2058 minecraft:spruce_planks',
              'fill 2038 101 2038 2038 104 2058 minecraft:spruce_planks',
              'fill 2058 101 2038 2058 104 2058 minecraft:spruce_planks',
              'fill 2037 105 2037 2059 105 2059 minecraft:stone_bricks',
              'fill 2038 101 2047 2038 103 2049 minecraft:air',
              'fill 2058 101 2047 2058 103 2049 minecraft:air',
              'fill 2045 102 2038 2051 103 2038 minecraft:glass_pane',
              'fill 2045 102 2058 2051 103 2058 minecraft:glass_pane',
              'fill 2059 104 2045 2063 104 2051 minecraft:spruce_slab',
              'fill 2059 100 2045 2063 100 2051 minecraft:spruce_planks',
              'setblock 2063 101 2045 minecraft:spruce_fence',
              'setblock 2063 102 2045 minecraft:spruce_fence',
              'setblock 2063 103 2045 minecraft:spruce_fence',
              'setblock 2063 101 2051 minecraft:spruce_fence',
              'setblock 2063 102 2051 minecraft:spruce_fence',
              'setblock 2063 103 2051 minecraft:spruce_fence',
              'setblock 2040 101 2040 minecraft:crafting_table',
              'setblock 2041 101 2040 minecraft:furnace[facing=south]',
              'setblock 2040 101 2055 minecraft:barrel',
              'setblock 2048 104 2048 minecraft:lantern[hanging=true]',
              'setblock 2061 103 2046 minecraft:lantern[hanging=true]',
              'fill 2014 100 2020 2022 100 2028 minecraft:oak_planks',
              'fill 2014 101 2020 2022 103 2020 minecraft:oak_planks',
              'fill 2014 101 2020 2014 103 2028 minecraft:oak_planks',
              'fill 2014 101 2028 2022 103 2028 minecraft:oak_planks',
              'fill 2014 104 2020 2022 104 2028 minecraft:oak_slab',
              'setblock 2016 101 2023 minecraft:barrel',
              'item replace block 2016 101 2023 container.0 with minecraft:emerald 4',
              'setblock 2016 103 2024 minecraft:lantern[hanging=true]']
    # Clear sight lanes at the entrances; scattered obstacles elsewhere are real geometry.
    for x, z in [(2008, 2010), (2027, 2011), (2045, 2009), (2067, 2013), (2086, 2010),
                 (2008, 2037), (2087, 2035), (2009, 2067), (2086, 2066),
                 (2013, 2088), (2032, 2083), (2052, 2089), (2076, 2085),
                 (2029, 2065), (2067, 2030)]:
        lines += [f'fill {x-2} 106 {z-2} {x+2} 108 {z+2} minecraft:spruce_leaves[persistent=true]',
                  f'fill {x-1} 109 {z-1} {x+1} 110 {z+1} minecraft:spruce_leaves[persistent=true]',
                  f'fill {x} 101 {z} {x} 108 {z} minecraft:spruce_log']
    for x, z in [(2027, 2034), (2069, 2063), (2030, 2070), (2077, 2028)]:
        lines.append(f'fill {x} 101 {z} {x+3} 102 {z+1} minecraft:cobblestone')
    lines += ['fill 2042 100 2126 2054 100 2138 minecraft:stone_bricks',
              'fill 2042 101 2126 2054 105 2138 minecraft:air',
              'setblock 2044 101 2132 minecraft:lantern',
              'setblock 2052 101 2132 minecraft:lantern']
    return '\n'.join(lines)


SCRIPTS = {
    'load': '''scoreboard objectives add mt dummy
scoreboard objectives add mt_deaths deathCount
scoreboard objectives add mt_hp dummy
execute if score #stage mt matches 1 run scoreboard players set #resumed mt 1''',
    'setup': '''tag @s add macs_trial
execute unless score #initialized mt matches 1 run function macs_trial:initialize
function macs_trial:status''',
    'reset': '''tag @s add macs_trial
execute if score #stage mt matches 1 run function macs_trial:snapshot
execute as @e[tag=macs_trial_actor] run data merge entity @s {NoAI:1b}
kill @e[tag=macs_trial_actor]
execute if score #initialized mt matches 1 run tp @s 2048.5 101 2132.5 180 0
function macs_trial:initialize''',
    'initialize': '''scoreboard players set #initialized mt 1
scoreboard players set #stage mt 3
scoreboard players set #round mt 0
scoreboard players set #lane mt 0
scoreboard players set #timer mt 0
scoreboard players set #exports mt 0
scoreboard players set #resumed mt 0
execute as @e[tag=macs_learning_actor] run data merge entity @s {NoAI:1b}
kill @e[tag=macs_learning_actor]
fd postmaeve set-erased
fd postmaeve reset-erased confirm
function macs_trial:preset
fd world set phase 6 late
fd maeve status
fd world set phase 0
gamerule doMobSpawning false
gamerule doDaylightCycle false
gamerule doWeatherCycle false
gamerule keepInventory true
difficulty normal
time set 13000
weather clear
forceload remove all
forceload add 1992 1992 2104 2104
forceload add 2040 2120 2056 2140
''' + tell('Preparing the camp. Stay here until the Camp ready message; no visitor can start during construction.'),
    'build_complete': '''function macs_trial:build
kill @e[type=minecraft:arrow,x=2000,y=99,z=2000,dx=96,dy=40,dz=140]
kill @e[type=minecraft:item,x=2000,y=99,z=2000,dx=96,dy=40,dz=140]
kill @e[type=minecraft:experience_orb,x=2000,y=99,z=2000,dx=96,dy=40,dz=140]
gamemode survival @s
effect clear @s
clear @s
function macs_trial:kit
tp @s 2048.5 101 2132.5 180 0
spawnpoint @s 2048 101 2132
scoreboard players set @s mt_deaths 0
scoreboard players set #stage mt 0
''' + tell('Camp ready. This is a controlled MACS trial at dusk; the lethal atmosphere is held off. Enter to look around before starting an encounter.', '/function macs_trial:enter', 'green'),
    'build': build_camp(),
    'preset': 'fd world preset brutal',
    'kit': '''item replace entity @s armor.head with minecraft:iron_helmet
item replace entity @s armor.chest with minecraft:iron_chestplate
item replace entity @s armor.legs with minecraft:iron_leggings
item replace entity @s armor.feet with minecraft:iron_boots
item replace entity @s hotbar.0 with minecraft:bow
item replace entity @s hotbar.1 with minecraft:iron_sword
item replace entity @s hotbar.2 with minecraft:shield
item replace entity @s hotbar.3 with minecraft:cooked_beef 32
item replace entity @s hotbar.4 with minecraft:potion[minecraft:potion_contents={potion:"minecraft:strong_healing"}]
item replace entity @s hotbar.5 with minecraft:potion[minecraft:potion_contents={potion:"minecraft:strong_healing"}]
item replace entity @s hotbar.6 with minecraft:golden_apple 4
item replace entity @s hotbar.7 with minecraft:oak_planks 64
item replace entity @s hotbar.8 with minecraft:iron_pickaxe
item replace entity @s inventory.0 with minecraft:arrow 64
item replace entity @s inventory.1 with minecraft:potion[minecraft:potion_contents={potion:"minecraft:strong_healing"}]
item replace entity @s inventory.2 with minecraft:potion[minecraft:potion_contents={potion:"minecraft:strong_healing"}]
effect give @s minecraft:instant_health 1 4 true
effect give @s minecraft:saturation 1 4 true''',
    'enter': 'execute if score #stage mt matches 0 run function macs_trial:enter_ready',
    'enter_ready': '''tp @s 2048.5 101 2048.5 -90 0
''' + tell('No visitor has been summoned yet. The cabin has east/west openings, a roofed porch and a northwest supply shed. Retrieve an emerald and return it to the cabin barrel. Start when you are ready.', '/function macs_trial:start'),
    'start': '''execute if score #initialized mt matches 1 if score #stage mt matches 0 if entity @s[x=2001,y=100,z=2001,dx=94,dy=20,dz=94] run function macs_trial:start_round
execute unless entity @s[x=2001,y=100,z=2001,dx=94,dy=20,dz=94] run tellraw @s {"text":"Enter the camp before starting: /function macs_trial:enter","color":"yellow"}''',
    'start_round': '''scoreboard players set #stage mt 1
scoreboard players set #timer mt 0
scoreboard players set #exports mt 0
scoreboard players set #resumed mt 0
scoreboard players add #round mt 1
scoreboard players set @s mt_deaths 0
execute if score #lane mt matches 0 run summon frozendawn:architect 2072.5 101 2048.5 {Tags:["macs_trial_actor"],PersistenceRequired:1b}
execute if score #lane mt matches 1 run summon frozendawn:architect 2024.5 101 2048.5 {Tags:["macs_trial_actor"],PersistenceRequired:1b}
execute as @e[tag=macs_trial_actor] run fd architect record @s 1337
scoreboard players add #lane mt 1
execute if score #lane mt matches 2.. run scoreboard players set #lane mt 0
''' + tell('Encounter running. Move, fight, build or hide normally. There is no automatic freeze. When it is over, describe it and use Finish. Emergency stop: /function macs_trial:abort.', '/function macs_trial:finish'),
    'snapshot': '''execute as @e[tag=macs_trial_actor] run fd architect dump @s
scoreboard players add #exports mt 1''',
    'finish': 'execute if score #stage mt matches 1 run function macs_trial:end_round',
    'end_round': '''function macs_trial:snapshot
execute as @e[tag=macs_trial_actor] run data merge entity @s {NoAI:1b}
kill @e[tag=macs_trial_actor]
scoreboard players set #stage mt 2
scoreboard players set #timer mt 0
tp @a[tag=macs_trial] 2048.5 101 2132.5 180 0
''' + tell('Round ended; remaining QA actors removed and traces saved. Describe it before reading /fd maeve dump. Finishing during an active hold interrupts that result.', '/fd maeve dump') + '\n'
    + tell('Empty encounter gap only: click once, wait for Sprint completed and Ready, then enter the camp. No actor starts automatically.', '/tick sprint 620t', 'yellow'),
    'ready': '''scoreboard players set #stage mt 0
''' + tell('Ready. Previous learning and building changes are preserved. Enter the camp, resupply if needed, then start when you choose.', '/function macs_trial:enter', 'green'),
    'abort': 'execute if score #stage mt matches 1 run function macs_trial:abort_round',
    'abort_round': '''function macs_trial:snapshot
execute as @e[tag=macs_trial_actor] run data merge entity @s {NoAI:1b}
kill @e[tag=macs_trial_actor]
scoreboard players set #stage mt 2
scoreboard players set #timer mt 0
scoreboard players set @a[tag=macs_trial] mt_deaths 0
tp @a[tag=macs_trial] 2048.5 101 2132.5 180 0
''' + tell('Stopped. Treat interrupted execution as inconclusive. Keep your description and dump; learning is preserved.', '/fd maeve dump') + '\n'
    + tell('When ready, sprint only this empty gap, wait for Ready, and enter again.', '/tick sprint 620t', 'yellow'),
    'refill': '''execute if score #stage mt matches 0 run function macs_trial:kit
execute unless score #stage mt matches 0 run tellraw @s {"text":"Refill is available only between encounters.","color":"yellow"}''',
    'status': '''scoreboard players list #round
scoreboard players list #stage
fd world status
fd maeve status
''' + tell('Stage 0: enter/start; 1: playing; 2: empty gap; 3: preparing the camp. Finish ends an encounter; abort stops it immediately. Setup preserves progress. Use /function macs_trial:reset only for a fresh camp and empty tactical memory.'),
    'tick': '''execute if score #stage mt matches 1 run scoreboard players add #timer mt 1
execute if score #stage mt matches 2 run scoreboard players add #timer mt 1
execute if score #stage mt matches 3 run scoreboard players add #timer mt 1
execute if score #stage mt matches 3 if score #timer mt matches 20.. as @a[tag=macs_trial,limit=1] run function macs_trial:build_if_ready
execute if score #stage mt matches 2 if score #timer mt matches 610.. run function macs_trial:ready
execute if score #stage mt matches 1 if score #resumed mt matches 1 as @a[tag=macs_trial,limit=1] run function macs_trial:abort
execute if score #stage mt matches 1 as @a[tag=macs_trial,scores={mt_deaths=1..},limit=1] run function macs_trial:abort
execute if score #stage mt matches 1 if score #timer mt matches 200.. if score #exports mt matches ..47 run function macs_trial:periodic
execute if score #stage mt matches 1 as @e[tag=macs_trial_actor] store result score @s mt_hp run data get entity @s Health 100
execute if score #stage mt matches 1 as @e[tag=macs_trial_actor,scores={mt_hp=..0},tag=!macs_trial_death_saved] run function macs_trial:death_snapshot
execute if score #stage mt matches 1 unless entity @e[tag=macs_trial_actor] run function macs_trial:end_round''',
    'periodic': 'function macs_trial:snapshot\nscoreboard players set #timer mt 0',
    'death_snapshot': 'fd architect dump @s\ntag @s add macs_trial_death_saved',
}

# Force-loading requests may finish after the setup command returns. Test every needed
# chunk before issuing fills, while the player waits safely in the old QA location.
camp_chunks = [(x * 16, z * 16) for x in range(124, 132) for z in range(124, 132)]
camp_chunks += [(x * 16, z * 16) for x in range(127, 129) for z in range(132, 134)]
SCRIPTS['build_if_ready'] = 'scoreboard players set #timer mt 0\nexecute ' + ' '.join(
    f'if loaded {x} 100 {z}' for x, z in camp_chunks) + ' run function macs_trial:build_complete'


def write_pack(pack, preset, game_test=False):
    functions = pack / 'data/macs_trial/function'
    functions.mkdir(parents=True, exist_ok=True)
    for name, content in SCRIPTS.items():
        if name == 'preset':
            content = f'fd world preset {preset}'
        (functions / f'{name}.mcfunction').write_text(content + '\n')
    if not game_test:
        (pack / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 48, 'description': 'MACS integrated camp trial'}}))
        tags = pack / 'data/minecraft/tags/function'; tags.mkdir(parents=True, exist_ok=True)
        for name in ('load', 'tick'):
            (tags / f'{name}.json').write_text(json.dumps({'values': [f'macs_trial:{name}']}))


def prepare(source, destination, preset):
    if destination.exists():
        raise SystemExit(f'Refusing to overwrite {destination}')
    level = gzip.decompress((source / 'level.dat').read_bytes())
    marker = b'\x08\x00\x09LevelName'
    if level.count(marker) != 1:
        raise SystemExit('Expected one LevelName in the CLOSED source QA world')
    at = level.index(marker) + len(marker); length = int.from_bytes(level[at:at+2], 'big')
    label = 'Brutal' if preset == 'brutal' else 'Normal'
    name = f'MACS Camp - {label}'.encode()
    shutil.copytree(source, destination)
    (destination / 'level.dat').write_bytes(gzip.compress(level[:at] + len(name).to_bytes(2, 'big') + name + level[at+2+length:]))
    pack = destination / 'datapacks/maeve-slice1'
    if pack.exists():
        shutil.rmtree(pack)
    write_pack(pack, preset)
    print(f'Prepared {destination}; /function macs_trial:setup initializes only this disposable copy.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path)
    parser.add_argument('--destination', type=Path)
    parser.add_argument('--preset', choices=['brutal', 'default'], default='brutal')
    parser.add_argument('--pack-only', type=Path)
    args = parser.parse_args()
    if args.pack_only:
        write_pack(args.pack_only, args.preset, True)
    elif args.source and args.destination:
        prepare(args.source, args.destination, args.preset)
    else:
        parser.error('Use --source/--destination or --pack-only')

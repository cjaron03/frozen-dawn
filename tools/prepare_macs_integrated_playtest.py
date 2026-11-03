#!/usr/bin/env python3
"""Paired, disposable MACS camp trials. Player actions provide all tactical evidence."""
import argparse
import gzip
import json
import math
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


def frozen_camp_batches():
    """A fixed late-phase terrain sample; no phase, belief, kit or utility edits."""
    commands = []
    palette = {'minecraft:grass_block': 'frozendawn:frozen_dirt',
               'minecraft:dirt_path': 'frozendawn:frozen_dirt',
               'minecraft:spruce_planks': 'frozendawn:frozen_planks',
               'minecraft:oak_planks': 'frozendawn:frozen_planks',
               'minecraft:stone_bricks': 'frozendawn:frozen_stone_bricks',
               'minecraft:cobblestone': 'frozendawn:frozen_cobblestone',
               'minecraft:spruce_log': 'frozendawn:frozen_log'}
    for line in build_camp().splitlines():
        if 'spruce_leaves' in line:
            continue
        for before, after in palette.items():
            line = line.replace(before, after)
        commands.append(line)

    trees = {(2008, 2010), (2027, 2011), (2045, 2009), (2067, 2013), (2086, 2010),
             (2008, 2037), (2087, 2035), (2009, 2067), (2086, 2066),
             (2013, 2088), (2032, 2083), (2052, 2089), (2076, 2085),
             (2029, 2065), (2067, 2030)}
    rocks = [(2027, 2034), (2069, 2063), (2030, 2070), (2077, 2028)]
    surfaces = {}
    for x in range(2001, 2096):
        for z in range(2001, 2096):
            if (2038 <= x <= 2058 and 2038 <= z <= 2058
                    or 2059 <= x <= 2063 and 2045 <= z <= 2051
                    or 2014 <= x <= 2022 and 2020 <= z <= 2028
                    or (x, z) in trees
                    or any(rx <= x <= rx + 3 and rz <= z <= rz + 1 for rx, rz in rocks)):
                continue
            dx, dz = x - 2048, z - 2048
            wave = math.sin(dx * .16 + math.sin(dz * .11)) + .65 * math.cos(dz * .19 - dx * .06)
            depth = max(0, min(23, round(7 + 6 * wave + 3 * math.sin((dx + dz) * .33))))
            # Wind-scoured entrances keep the old openings accessible, with real
            # fractional snow transitions rather than perfectly level paths.
            if 2046 <= z <= 2050 and 2021 <= x <= 2076:
                depth = 1 + (x + z) % 6
            edge = min(x - 2000, 2096 - x, z - 2000, 2096 - z)
            ridge = max(0, 4 - edge + int(wave > .4)) if edge <= 4 else 0
            ice = math.sin(dx * .14 - dz * .07) + math.cos(dz * .12) > 1.2
            substrate = 'minecraft:blue_ice' if ice and wave < .2 else (
                'minecraft:packed_ice' if ice else 'frozendawn:frozen_dirt')
            if ice and wave < -.2:
                depth = 0
            elif ice and depth < 8:
                # Thin vanilla snow cannot survive directly on packed ice.
                substrate = 'frozendawn:frozen_dirt'
            commands.append(f'setblock {x} 100 {z} {substrate}')
            top = 100
            if ridge:
                top += ridge
                commands.append(f'fill {x} 101 {z} {x} {top} {z} frozendawn:frozen_cobblestone')
            full, layers = divmod(depth, 8)
            if full:
                commands.append(f'fill {x} {top+1} {z} {x} {top+full} {z} minecraft:snow_block')
                top += full
            if layers:
                commands.append(f'setblock {x} {top+1} {z} minecraft:snow[layers={layers}]')
            surfaces[x, z] = (top, layers)

    # Snow on solid roof and rock surfaces; bottom slabs are not snow supports.
    for x in range(2037, 2060):
        for z in range(2037, 2060):
            layers = 1 + ((x * 3 + z) % 7)
            commands.append(f'setblock {x} 106 {z} minecraft:snow[layers={layers}]')
    for x, z in rocks:
        commands.append(f'fill {x} 103 {z} {x+3} 103 {z+1} minecraft:snow[layers=4]')
    for x, z in sorted(trees):
        # Broken crowns and sparse frozen limbs replace leafy decorative trees.
        height = 5 + (x + z) % 3
        commands += [f'fill {x} {101+height} {z} {x} 108 {z} minecraft:air',
                     f'setblock {x-1} {99+height} {z} frozendawn:frozen_log[axis=x]',
                     f'setblock {x+1} {98+height} {z} frozendawn:frozen_log[axis=x]']

    clusters = [(2010, 2018), (2026, 2021), (2040, 2015), (2058, 2018), (2078, 2019),
                (2010, 2055), (2028, 2044), (2030, 2055), (2068, 2052), (2083, 2056),
                (2023, 2077), (2041, 2071), (2060, 2078), (2080, 2076)]
    for index, (cx, cz) in enumerate(clusters):
        for ox, oz in [(0, 0), (2, 1), (-1, 2)]:
            x, z = cx + ox, cz + oz
            if (x, z) not in surfaces:
                continue
            top, layers = surfaces[x, z]
            age = 2 + (index + ox) % 2
            buried = 'true' if layers >= 3 else 'false'
            commands.append(f'setblock {x} {top+1} {z} frozendawn:acheronite_crystal[age={age},buried={buried},dark=false]')

    # Bound both modified volume and command count per construction step. Large
    # legacy fills are split before batching; no entire arena rebuild in one tick.
    def split_fill(line):
        fields = line.split()
        if fields[0] != 'fill':
            return [line]
        lo = [int(v) for v in fields[1:4]]; hi = [int(v) for v in fields[4:7]]
        lo, hi = [min(a, b) for a, b in zip(lo, hi)], [max(a, b) for a, b in zip(lo, hi)]
        if math.prod(b - a + 1 for a, b in zip(lo, hi)) <= 4096:
            return ['fill ' + ' '.join(map(str, lo + hi)) + ' ' + ' '.join(fields[7:])]
        axis = max(range(3), key=lambda i: hi[i] - lo[i]); mid = (lo[axis] + hi[axis]) // 2
        left, right = hi.copy(), lo.copy(); left[axis] = mid; right[axis] = mid + 1
        suffix = ' ' + ' '.join(fields[7:])
        return split_fill('fill ' + ' '.join(map(str, lo + left)) + suffix) + split_fill('fill ' + ' '.join(map(str, right + hi)) + suffix)

    batches, batch, volume = [], [], 0
    for original in commands:
        for line in split_fill(original):
            fields = line.split()
            cost = math.prod(1 + abs(int(fields[i]) - int(fields[i+3])) for i in range(1, 4)) if fields[0] == 'fill' else 1
            if batch and (volume + cost > 4096 or len(batch) >= 256):
                batches.append(batch); batch, volume = [], 0
            batch.append(line); volume += cost
    if batch:
        batches.append(batch)
    return batches


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
    'survey_check': '''execute if score #initialized mt matches 1 if score #stage mt matches 0 if entity @s[tag=macs_trial] run function macs_trial:survey_check_ready
execute unless score #stage mt matches 0..1 run tellraw @s {"text":"Wait for Ready before starting the west-doorway check. During the empty gap only, use /tick sprint 620t.","color":"yellow"}''',
    'survey_check_ready': '''function macs_trial:kit
setblock 2037 100 2048 minecraft:dirt_path
fill 2038 101 2047 2038 103 2049 minecraft:air
tp @s 2024.5 100.9375 2048.5 -90 0
scoreboard players set #lane mt 1
function macs_trial:start_round
tp @e[tag=macs_trial_actor,limit=1] 2036.5 100.9375 2048.5 -90 0
''' + tell('West-doorway inspection check. Stay here without attacking. Once the scout finishes thinking at the doorway and starts withdrawing, use Finish, then /fd maeve dump. Learning and production survey rules are preserved.', '/function macs_trial:finish'),
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

FROZEN_BATCHES = frozen_camp_batches()
SCRIPTS['frozen'] = '''tag @s add macs_trial
execute if score #initialized mt matches 1 if score #stage mt matches 0 run function macs_trial:frozen_begin
execute if score #initialized mt matches 1 if score #stage mt matches 2 run function macs_trial:frozen_begin
execute unless score #initialized mt matches 1 run tellraw @s {"text":"Run /function macs_trial:setup first.","color":"yellow"}
execute if score #stage mt matches 1 run tellraw @s {"text":"Finish or abort the encounter before rebuilding terrain.","color":"yellow"}'''
SCRIPTS['frozen_begin'] = '''scoreboard players operation #before_stage mt = #stage mt
scoreboard players operation #before_timer mt = #timer mt
scoreboard players set #stage mt 4
scoreboard players set #terrain mt 0
scoreboard players set #timer mt 0
tp @a[tag=macs_trial] 2048.5 101 2132.5 180 0
forceload add 1992 1992 2104 2104
forceload add 2040 2120 2056 2140
''' + tell('Preparing frozen terrain. Wait on the platform; beliefs, round history and your kit are retained.')
SCRIPTS['frozen_build_if_ready'] = 'scoreboard players set #timer mt 0\nexecute ' + ' '.join(
    f'if loaded {x} 100 {z}' for x, z in camp_chunks) + ' run function macs_trial:frozen_dispatch'
SCRIPTS['frozen_dispatch'] = 'scoreboard players operation #build_step mt = #terrain mt\n' + '\n'.join(
    f'execute if score #build_step mt matches {i} run function macs_trial:frozen/step_{i:03}'
    for i in range(len(FROZEN_BATCHES)))
for i, batch in enumerate(FROZEN_BATCHES):
    SCRIPTS[f'frozen/step_{i:03}'] = '\n'.join(batch) + '\nscoreboard players add #terrain mt 1'
SCRIPTS[f'frozen/step_{len(FROZEN_BATCHES)-1:03}'] += '\nfunction macs_trial:frozen_finish'
SCRIPTS['frozen_finish'] = '''scoreboard players set #frozen_revision mt 1
scoreboard players operation #stage mt = #before_stage mt
scoreboard players operation #timer mt = #before_timer mt
weather clear
''' + tell('Frozen camp ready. Uneven snow, ice and crystals now replace the old terrain. Combat settings and learned history are retained.', '/function macs_trial:enter', 'green')
SCRIPTS['tick'] += '''\nexecute if score #stage mt matches 4 run scoreboard players add #timer mt 1
execute if score #stage mt matches 4 if score #timer mt matches 2.. run function macs_trial:frozen_build_if_ready'''
SCRIPTS['status'] += '\n' + tell('Stage 4: frozen terrain is rebuilding. /function macs_trial:frozen rebuilds terrain between rounds without erasing beliefs.')


def write_pack(pack, preset, game_test=False):
    functions = pack / 'data/macs_trial/function'
    functions.mkdir(parents=True, exist_ok=True)
    for name, content in SCRIPTS.items():
        if name == 'preset':
            content = f'fd world preset {preset}'
        destination = functions / f'{name}.mcfunction'
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(content + '\n')
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

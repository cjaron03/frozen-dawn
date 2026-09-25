"""Solo acceptance functions; no belief edits or production combat overrides."""
import json


CASES = {
    'sword': ('1 Sword', 'Use only the sword for damage. Bait blocks, punish openings and flank. Let it heal if the fight allows, then try to defeat it naturally.'),
    'axe_raised': ('2 Raised axe', 'First confirm a real frontal sword block. Then hit its raised shield with the axe. Continue the fight and check that guarding does not return.'),
    'axe_open': ('3 Exposed axe', 'First confirm guarding, then land an axe hit while its shield is lowered. Continue the fight and check that guarding does not return.'),
    'switch': ('4 Change tactics', 'Approach to bait a guard, then change to the bow and use distance or flanking. Finish naturally. Later repeats in this world retain the resulting learning.'),
}


def tell(message, command=None, color='aqua'):
    value = {'text': message, 'color': color}
    if command:
        value['clickEvent'] = {'action': 'run_command', 'value': command}
    return 'tellraw @s ' + json.dumps(value)


def scripts(preset='brutal', case='sword'):
    result = {
        'accept/load': 'scoreboard objectives add mga dummy\nscoreboard objectives add mga_deaths deathCount',
        'accept/setup': '''execute if score #stage mga matches 1 run return run tellraw @s {"text":"A fight is active. Finish naturally or use /function macs_guard:accept/abort.","color":"yellow"}
tag @s add macs_guard_accept
tag @s remove macs_guard
tag @s remove macs_trial
scoreboard players set #stage mg 4
execute unless score #initialized mga matches 1 run function macs_guard:accept/initialize
execute if score #initialized mga matches 1 unless score #stage mga matches 5 run function macs_guard:accept/status''',
        'accept/initialize': '''scoreboard players set #stage mga 5
scoreboard players set #round mga 0
function macs_guard:accept/preset
fd world set phase 0
gamerule doMobSpawning false
gamerule doDaylightCycle false
gamerule doWeatherCycle false
gamerule keepInventory true
difficulty normal
time set 13000
weather clear
forceload add 2192 2192 2240 2240
forceload add 2208 2256 2224 2272
''' + tell('Preparing the snow arena. No Architect spawns until you use Start; your real sword history is retained.'),
        'accept/preset': f'fd world preset {preset}',
        'accept/case': tell(CASES[case][1], color='gold'),
        'accept/kit': '''clear @s
function macs_guard:kit
item replace entity @s weapon.offhand with minecraft:air''',
        'accept/ready': '''scoreboard players set #stage mga 0
''' + tell('Ready. Use Start when you are ready; there is no automatic freeze or time limit.', '/function macs_guard:accept/start', 'green') + '\nfunction macs_guard:accept/case',
        'accept/start': '''execute unless score #stage mga matches 0 run return run tellraw @s {"text":"Run /function macs_guard:accept/setup and wait for Ready. After a finished round use /function macs_guard:accept/repeat.","color":"yellow"}
execute if entity @e[tag=macs_guard_accept_actor] run return run tellraw @s {"text":"The previous actor has not cleared yet. Wait briefly or use Abort.","color":"yellow"}
function macs_guard:accept/preset
gamemode survival @s
effect clear @s
function macs_guard:accept/kit
advancement revoke @s only macs_guard:accept_kill
scoreboard players set @s mga_deaths 0
scoreboard players set #outcome mga 0
scoreboard players set #timer mga 0
scoreboard players set #round_tick mga 0
scoreboard players add #round mga 1
tp @s 2220.5 101 2216.5 90 0
summon frozendawn:architect 2210.5 101 2216.5 {Tags:["macs_guard_accept_actor"],PersistenceRequired:1b}
execute as @e[tag=macs_guard_accept_actor] run fd architect record @s 1337
scoreboard players set #stage mga 1
function macs_guard:accept/case
''' + tell('Fight running. Describe what happened afterward, then run /fd maeve dump. Emergency stop: /function macs_guard:accept/abort.'),
        'accept/snapshot': 'execute as @e[tag=macs_guard_accept_actor] run fd architect dump @s',
        'accept/periodic': '''function macs_guard:accept/snapshot
scoreboard players set #timer mga 0''',
        'accept/victory': '''execute unless score #stage mga matches 1 run return 0
scoreboard players set #outcome mga 1
function macs_guard:accept/snapshot
scoreboard players set #stage mga 4
''' + tell('Player kill recorded. Describe the fight, then run /fd maeve dump. Finish returns you to the waiting platform.', '/function macs_guard:accept/finish', 'green'),
        'accept/loss': '''function macs_guard:accept/snapshot
scoreboard players set #outcome mga 2
scoreboard players set #stage mga 4
function macs_guard:accept/remove_actor
''' + tell('Player death recorded before cleanup. Respawn, describe the fight, then run /fd maeve dump. This is a loss, not a passed counterplay test.', color='yellow'),
        'accept/missing': '''scoreboard players set #outcome mga 3
scoreboard players set #stage mga 4
''' + tell('Actor disappeared without a recorded player kill. Treat the outcome as unverified; run /fd maeve dump.', color='yellow'),
        'accept/remove_actor': '''execute as @e[tag=macs_guard_accept_actor] run data merge entity @s {NoAI:1b}
kill @e[tag=macs_guard_accept_actor]''',
        'accept/abort': '''execute if score #stage mga matches 1 run function macs_guard:accept/snapshot
execute if score #stage mga matches 1 run scoreboard players set #outcome mga 4
scoreboard players set #stage mga 4
function macs_guard:accept/remove_actor
function macs_guard:accept/finish
''' + tell('Aborted. A cleanup kill is not a counterplay victory.', color='yellow'),
        'accept/finish': '''execute if score #stage mga matches 1 run return run tellraw @s {"text":"The fight is still active. Finish naturally, or use /function macs_guard:accept/abort for an unscored emergency stop.","color":"yellow"}
execute unless score #stage mga matches 4 run return 0
tp @s 2220.5 101 2264.5 180 0
''' + tell('Round preserved. For the next independent case, save and open its separate world. Repeat here only when following this world\'s evolving history.', '/function macs_guard:accept/repeat'),
        'accept/repeat': '''execute unless score #stage mga matches 4 run return 0
execute if entity @e[tag=macs_guard_accept_actor] run return run tellraw @s {"text":"Wait for the defeated actor to disappear before opening the empty gap.","color":"yellow"}
function macs_guard:accept/finish
scoreboard players set #timer mga 0
scoreboard players set #stage mga 2
''' + tell('Empty gap only. Click once, wait for Sprint completed and Ready, then Start. All resulting learning is retained.', '/tick sprint 620t', 'yellow'),
        'accept/status': '''tellraw @s [{"text":"Guard acceptance: stage "},{"score":{"name":"#stage","objective":"mga"}},{"text":" (0 ready, 1 fight, 2 empty gap, 4 ended, 5 preparing); outcome "},{"score":{"name":"#outcome","objective":"mga"}},{"text":" (0 running, 1 player kill, 2 player death, 3 unknown disappearance, 4 aborted)."}]
function macs_guard:accept/case''',
    }
    build = [
        'fill 2200 100 2200 2232 100 2232 frozendawn:frozen_dirt',
        'fill 2200 101 2200 2232 110 2232 minecraft:air',
        'fill 2200 101 2200 2232 104 2200 minecraft:bedrock',
        'fill 2200 101 2232 2232 104 2232 minecraft:bedrock',
        'fill 2200 101 2200 2200 104 2232 minecraft:bedrock',
        'fill 2232 101 2200 2232 104 2232 minecraft:bedrock',
    ]
    for x in range(2201, 2232):
        for z in range(2201, 2232):
            # Wind-scoured opening lane; mixed caps and deeper drifts away from it.
            full = int(z >= 2224 and x >= 2224)
            layers = 1 if 2214 <= z <= 2218 else 1 + (x * 3 + z) % 8
            if full:
                build.append(f'setblock {x} 101 {z} minecraft:snow_block')
            build.append(f'setblock {x} {101 + full} {z} minecraft:snow[layers={layers}]')
    for x, z in [(2206, 2207), (2225, 2208)]:
        build.append(f'fill {x} 101 {z} {x} 103 {z} frozendawn:frozen_log')
    for x, z in [(2206, 2226), (2227, 2225)]:
        build.append(f'setblock {x} {102 if x >= 2224 else 101} {z} frozendawn:acheronite_crystal[age=3,buried=true,dark=false]')
    build += [
        'fill 2214 100 2258 2226 100 2270 minecraft:stone',
        'fill 2214 101 2258 2226 106 2270 minecraft:air',
        'spawnpoint @s 2220 101 2264', 'gamemode survival @s', 'effect clear @s',
        'function macs_guard:accept/kit', 'tp @s 2220.5 101 2264.5 180 0',
        'scoreboard players set #initialized mga 1', 'function macs_guard:accept/ready',
    ]
    result['accept/build'] = '\n'.join(build)
    chunks = [(x, z) for x in (2192, 2208, 2224, 2240) for z in (2192, 2208, 2224, 2240)]
    chunks += [(x, z) for x in (2208, 2224) for z in (2256, 2272)]
    host = '@a[tag=macs_guard_accept,limit=1]'
    result['accept/tick'] = '\n'.join([
        'execute if score #stage mga matches 5 ' + ' '.join(f'if loaded {x} 100 {z}' for x, z in chunks)
        + f' as {host} run function macs_guard:accept/build',
        'execute if score #stage mga matches 1 run scoreboard players add #timer mga 1',
        'execute if score #stage mga matches 1 run scoreboard players add #round_tick mga 1',
        f'execute if score #stage mga matches 1 if score #timer mga matches 100.. as {host} run function macs_guard:accept/periodic',
        'execute if score #stage mga matches 1 as @a[tag=macs_guard_accept,scores={mga_deaths=1..},limit=1] run function macs_guard:accept/loss',
        f'execute if score #stage mga matches 1 unless entity @e[tag=macs_guard_accept_actor] as {host} run function macs_guard:accept/missing',
        'execute if score #stage mga matches 2 run scoreboard players add #timer mga 1',
        f'execute if score #stage mga matches 2 if score #timer mga matches 620.. as {host} run function macs_guard:accept/ready',
    ])
    return result


KILL_ADVANCEMENT = {
    'criteria': {'kill': {'trigger': 'minecraft:player_killed_entity', 'conditions': {
        'entity': {'type': 'frozendawn:architect', 'nbt': '{Tags:["macs_guard_accept_actor"]}'},
    }}}, 'rewards': {'function': 'macs_guard:accept/victory'},
}

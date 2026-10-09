#!/usr/bin/env python3
"""Disposable sword-guard replay. Four real player hits teach history; no belief edits."""
import argparse
import gzip
import json
import shutil
from pathlib import Path
from macs_guard_acceptance import CASES, KILL_ADVANCEMENT, scripts as acceptance_scripts


def tell(text, command=None, color='aqua'):
    row = {'text': text, 'color': color}
    if command:
        row['clickEvent'] = {'action': 'run_command', 'value': command}
    return 'tellraw @s ' + json.dumps(row)


SCRIPTS = {
    'load': 'scoreboard objectives add mg dummy\nscoreboard objectives add mg_deaths deathCount',
    'setup': '''tag @s add macs_guard
execute unless score #initialized mg matches 1 run function macs_guard:initialize
function macs_guard:status''',
    'initialize': '''scoreboard players set #initialized mg 1
scoreboard players set #trained mg 0
scoreboard players set #stage mg 5
scoreboard players set #timer mg 0
tag @s remove macs_trial
fd world set phase 6 late
fd maeve status
fd world set phase 0
gamerule doMobSpawning false
gamerule keepInventory true
forceload add 2192 2192 2240 2240
forceload add 2208 2256 2224 2272
''' + tell('Preparing the separate sword arena. Your saved camp and learned history are preserved.'),
    'build': '''fill 2200 100 2200 2232 100 2232 minecraft:stone
fill 2200 101 2200 2232 110 2232 minecraft:air
fill 2200 101 2200 2232 104 2200 minecraft:bedrock
fill 2200 101 2232 2232 104 2232 minecraft:bedrock
fill 2200 101 2200 2200 104 2232 minecraft:bedrock
fill 2232 101 2200 2232 104 2232 minecraft:bedrock
fill 2214 100 2258 2226 100 2270 minecraft:stone
fill 2214 101 2258 2226 106 2270 minecraft:air
setblock 2220 100 2216 minecraft:lime_concrete
setblock 2210 100 2216 minecraft:blue_concrete
spawnpoint @s 2220 101 2264
gamemode survival @s
function macs_guard:kit
tp @s 2220.5 101 2264.5 180 0
scoreboard players set #stage mg 0
''' + tell('Ready. Click Practice. Hit the training Architect ONCE with the iron sword in slot 1. Training protects you from damage.', '/function macs_guard:practice', 'green'),
    'kit': '''function macs_trial:kit
item replace entity @s hotbar.0 with minecraft:iron_sword
item replace entity @s hotbar.1 with minecraft:iron_axe
item replace entity @s hotbar.2 with minecraft:bow
item replace entity @s hotbar.3 with minecraft:shield
item replace entity @s hotbar.6 with minecraft:cooked_beef 32''',
    'practice': '''execute if score #stage mg matches 0 if score #trained mg matches ..3 run function macs_guard:practice_ready''',
    'practice_ready': '''function macs_guard:kit
scoreboard players set #refresh mg 0
effect give @s minecraft:resistance 9999 4 true
advancement revoke @s only macs_guard:sword_hit
scoreboard players set #stage mg 1
tp @s 2220.5 101 2216.5 90 0
summon frozendawn:architect 2217.5 101 2216.5 {Tags:["macs_guard_actor","macs_guard_training"],PersistenceRequired:1b}
execute as @e[tag=macs_guard_actor] run fd architect record @s 1337
''' + tell('Use the iron sword in SLOT 1. One actual hit completes this round; there is no waiting timer during practice.'),
    'warmup': '''execute if score #stage mg matches 0 if score #trained mg matches 4.. run function macs_guard:warmup_ready''',
    'warmup_ready': '''function macs_guard:practice_ready
scoreboard players set #refresh mg 1
''' + tell('Short ordinary encounter: land one sword hit. This preserves history and lets normal encounter cooldowns advance; it does not force a shield.'),
    'hit': '''execute if score #stage mg matches 1 run function macs_guard:accepted''',
    'accepted': '''scoreboard players set #stage mg 2
scoreboard players set #timer mg 0
execute unless score #refresh mg matches 1 run scoreboard players add #trained mg 1
function macs_guard:remove_actor
tp @s 2220.5 101 2264.5 180 0
execute unless score #refresh mg matches 1 run tellraw @s [{"text":"Sword practice completed: "},{"score":{"name":"#trained","objective":"mg"}},{"text":"/4."}]
execute if score #refresh mg matches 1 run tellraw @s {"text":"Warmup contact recorded. Skip the empty gap before the next encounter.","color":"aqua"}
''' + tell('Click to skip the EMPTY encounter gap. Wait for Ready and Sprint completed, then click the next practice prompt.', '/tick sprint 620t', 'yellow'),
    'ready': '''scoreboard players set #stage mg 0
execute if score #trained mg matches ..3 run tellraw @s {"text":"Ready for the next sword hit. Click Practice.","color":"green","clickEvent":{"action":"run_command","value":"/function macs_guard:practice"}}
execute if score #trained mg matches 4.. run tellraw @s {"text":"Training complete. Run /fd maeve dump and reply done before starting the real encounter.","color":"green"}''',
    'start': '''execute if score #stage mg matches 0 if score #trained mg matches 4.. run function macs_guard:start_ready''',
    'start_ready': '''function macs_guard:kit
effect clear @s minecraft:resistance
scoreboard players set @s mg_deaths 0
scoreboard players set #stage mg 3
tp @s 2220.5 101 2216.5 90 0
summon frozendawn:architect 2210.5 101 2216.5 {Tags:["macs_guard_actor"],PersistenceRequired:1b}
execute as @e[tag=macs_guard_actor] run fd architect record @s 1337
''' + tell('Real encounter: normal damage. Approach with the sword; watch guard and attack openings. A normal hit staggers it, then it can guard again. Save the axe for last: it ends the guard. Finish exports its trace.', '/function macs_guard:finish'),
    'remove_actor': '''execute as @e[tag=macs_guard_actor] run fd architect dump @s
execute as @e[tag=macs_guard_actor] run data merge entity @s {NoAI:1b}
kill @e[tag=macs_guard_actor]''',
    'finish': '''execute if score #stage mg matches 1..3 run function macs_guard:end''',
    'end': '''function macs_guard:remove_actor
scoreboard players set #stage mg 4
tp @s 2220.5 101 2264.5 180 0
effect clear @s minecraft:resistance
''' + tell('Encounter ended. Describe it before reading /fd maeve dump. For another encounter, use /function macs_guard:again.'),
    'again': '''execute if score #stage mg matches 4 run function macs_guard:gap''',
    'gap': '''scoreboard players set #stage mg 2
scoreboard players set #timer mg 0
''' + tell('No Architect is active. Click to skip the empty gap; then use /function macs_guard:start after Ready.', '/tick sprint 620t', 'yellow'),
    'status': '''tellraw @s [{"text":"Sword practice: "},{"score":{"name":"#trained","objective":"mg"}},{"text":"/4; stage "},{"score":{"name":"#stage","objective":"mg"}},{"text":" (0 ready, 1 practice, 2 gap, 3 encounter, 4 finished, 5 preparing)."}]''',
}
# Native loading checks: never fill an unloaded chunk or begin combat automatically.
chunks = [(x, z) for x in (2192, 2208, 2224, 2240) for z in (2192, 2208, 2224, 2240)]
chunks += [(x, z) for x in (2208, 2224) for z in (2256, 2272)]
SCRIPTS['tick'] = '\n'.join([
    'execute if score #stage mg matches 5 ' + ' '.join(f'if loaded {x} 100 {z}' for x, z in chunks)
    + ' as @a[tag=macs_guard,limit=1] run function macs_guard:build',
    'execute if score #stage mg matches 2 run scoreboard players add #timer mg 1',
    'execute if score #stage mg matches 2 if score #timer mg matches 620.. as @a[tag=macs_guard,limit=1] run function macs_guard:ready',
    'execute if score #stage mg matches 3 as @a[tag=macs_guard,scores={mg_deaths=1..},limit=1] run function macs_guard:end',
])
ADVANCEMENT = {'criteria': {'sword': {'trigger': 'minecraft:player_hurt_entity', 'conditions': {
    'player': {'equipment': {'mainhand': {'items': ['minecraft:iron_sword']}}},
    'entity': {'type': 'frozendawn:architect', 'nbt': '{Tags:["macs_guard_training"]}'},
    'damage': {'dealt': {'min': .1}, 'blocked': False},
}}}, 'rewards': {'function': 'macs_guard:hit'}}


def write_pack(pack, game_test=False, preset='brutal', case='sword'):
    functions = pack / 'data/macs_guard/function'; functions.mkdir(parents=True, exist_ok=True)
    for name, content in (SCRIPTS | acceptance_scripts(preset, case)).items():
        target = functions / f'{name}.mcfunction'
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content + '\n')
    advancements = pack / 'data/macs_guard/advancement'; advancements.mkdir(parents=True, exist_ok=True)
    (advancements / 'sword_hit.json').write_text(json.dumps(ADVANCEMENT, indent=2) + '\n')
    (advancements / 'accept_kill.json').write_text(json.dumps(KILL_ADVANCEMENT, indent=2) + '\n')
    if not game_test:
        (pack / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 48, 'description': 'MACS sword guard training and live counterplay'}}))
        tags = pack / 'data/minecraft/tags/function'; tags.mkdir(parents=True, exist_ok=True)
        for name in ('load', 'tick'):
            (tags / f'{name}.json').write_text(json.dumps({'values': [f'macs_guard:{name}', f'macs_guard:accept/{name}']}))


def prepare(source, destination, acceptance=False, preset='brutal', case='sword'):
    if destination.exists(): raise SystemExit(f'Refusing to overwrite {destination}')
    raw = gzip.decompress((source / 'level.dat').read_bytes()); marker = b'\x08\x00\x09LevelName'
    if raw.count(marker) != 1: raise SystemExit('Expected one LevelName in the CLOSED source world')
    at = raw.index(marker) + len(marker); length = int.from_bytes(raw[at:at+2], 'big')
    title = (f'MACS Guard {CASES[case][0]} - {"Brutal" if preset == "brutal" else "Normal"}'
             if acceptance else 'MACS Sword Guard Encounter')
    name = title.encode('utf-8')
    shutil.copytree(source, destination)
    (destination / 'level.dat').write_bytes(gzip.compress(raw[:at] + len(name).to_bytes(2, 'big') + name + raw[at+2+length:]))
    write_pack(destination / 'datapacks/macs-sword-guard', preset=preset, case=case)
    command = 'macs_guard:accept/setup' if acceptance else 'macs_guard:setup'
    print(f'Prepared {destination}; /function {command} starts the separate arena without erasing history.')


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--pack-only', type=Path); p.add_argument('--source', type=Path); p.add_argument('--destination', type=Path)
    p.add_argument('--acceptance', action='store_true')
    p.add_argument('--preset', choices=('brutal', 'default'), default='brutal')
    p.add_argument('--case', choices=CASES, default='sword')
    args = p.parse_args()
    if args.pack_only: write_pack(args.pack_only, True, args.preset, args.case)
    elif args.source and args.destination: prepare(args.source, args.destination, args.acceptance, args.preset, args.case)
    else: p.error('Use --pack-only or --source/--destination')

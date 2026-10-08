#!/usr/bin/env python3
"""Prepare the MACS Scribe Check world (section 9.4b) from a closed superflat world.

Every step uses real play. Beliefs are earned in four witnessed practice rounds (Causal law);
the Scribe is the next natural Architect spawn once the gate is met. Nothing is injected.
"""
import argparse
import gzip
import json
import shutil
from pathlib import Path

NS = 'macs_scribe'
TITLE = b'MACS Scribe Check'
ROUNDS = 4
SWORD = 'minecraft:netherite_sword[minecraft:enchantments={levels:{"minecraft:sharpness":5}}]'
POTION = 'minecraft:potion[minecraft:potion_contents={potion:"minecraft:healing"}]'
SCRIBE = 'type=frozendawn:architect,nbt={NeoForgeData:{macsScribe:1b}}'
EVA = '\n'.join(f'item replace entity @s armor.{slot} with frozendawn:eva_{piece}'
                for slot, piece in (('head', 'helmet'), ('chest', 'chestplate'), ('legs', 'leggings'), ('feet', 'boots')))
# The habits the practice rounds teach. The gate itself counts any three beliefs at 0.75 or more.
HABITS = (('#sword', 'PLAYER_PREFERS_SWORD', 'sword'),
          ('#cover', 'PLAYER_USES_RECOVERY_UNDER_COVER', 'recovery under cover'),
          ('#east', 'RETREAT_BEARING_E', 'east retreat'))


def tell(text, command=None, color='gold'):
    msg = {'text': text, 'color': color}
    if command:
        msg['clickEvent'] = {'action': 'run_command', 'value': command}
        msg['underlined'] = True
    return 'tellraw @s ' + json.dumps(msg)


def guard(stage):
    """Wrong-stage clicks only print status; they never advance or rebuild anything."""
    return (f'execute unless score #stage msc matches {stage} run function {NS}:status\n'
            f'execute unless score #stage msc matches {stage} run return 0')


def prompt(stage):
    return f'function {NS}:prompt_{stage}'


def habits():
    """Function output is silent, so each confidence comes back as a command result (whole percent, rounded down)."""
    parts = [{'text': "Maeve's habits: ", 'color': 'aqua'}]
    for i, (var, _, label) in enumerate(HABITS):
        parts += [{'text': ('' if i == 0 else ', ') + label + ' '},
                  {'score': {'name': var, 'objective': 'msc'}, 'color': 'yellow'}, {'text': '%'}]
    parts.append({'text': '. The Scribe needs all three at 75% or more.'})
    return '\n'.join(
        [f'execute store result score {var} msc run fd maeve confidence {pattern}' for var, pattern, _ in HABITS]
        + ['scoreboard players set #short msc 0']
        + [f'execute unless score {var} msc matches 75.. run scoreboard players add #short msc 1' for var, _, _ in HABITS]
        + ['tellraw @s ' + json.dumps(parts),
           'execute if score #short msc matches 1.. run ' + tell('Not there yet. Click ONE MORE ROUND.', f'/function {NS}:extra', 'yellow'),
           'execute if score #short msc matches 0 run ' + tell('All three are ready. Click AWAIT THE SCRIBE.', f'/function {NS}:await', 'green')])


# Each stage's instructions and links live in one prompt function, so a rejoin, /reload or wrong-stage
# click prints them again without repeating any state change.
PROMPTS = {
    10: 'tellraw @s [{"text":"Practice round ","color":"gold"},{"score":{"name":"#rounds","objective":"msc"},"color":"yellow"},'
        '{"text":" done of 4. The caged witness watches you.","color":"gold"}]\n'
        + tell('1) Under the roof, drink the potion (slot 2).  2) Walk up to the cage and hit the witness once through the slit with the sword (slot 1).  3) Walk out the EAST opening onto GOLD and stand there.'),
    20: tell('Round recorded. Encounters need a quiet gap: click to skip it, then wait for Ready.', '/tick sprint 640t'),
    21: tell('Ready. Click NEXT.', f'/function {NS}:next', 'green'),
    22: habits(),
    30: tell('Keep the EVA suit on and stay under the roof. Natural Architect spawns roll every 10 seconds near you. Click to sprint; repeat until a Scribe arrives.', '/tick sprint 6000t'),
    31: tell('A Scribe was designated and is held in place. If a sprint is still running, wait for it to finish, then click SHOW.', f'/function {NS}:show', 'green'),
    39: tell('An ordinary Architect spawned and is held: the gate was not met. This is NOT a pass. Run the dump and report the SCRIBE lines.', '/fd maeve dump', 'red'),
    40: tell('CHECK 1 - Look: white eyes and a slate in its hand (not a weapon). It walks out to stand about 20 blocks from the EAST opening.') + '\n'
        + tell('CHECK 2 - Watching: from under the roof, it should stand still and stare at you. You have about two minutes before it leaves.') + '\n'
        + tell('Click NEXT for the approach check.', f'/function {NS}:approach', 'green'),
    50: tell('CHECK 3 - Flee: walk toward it. Within about 12 blocks it should turn and run. It must never attack you. Do not hit it yet. If its two minutes ran out it is already walking away and will not run; stay within 16 blocks or it is gone.') + '\n'
        + tell('Click NEXT for the chase.', f'/function {NS}:hunt', 'green'),
    60: tell('CHECK 4 - Chase it down and kill it with the sword. Struck from range it keeps running; cornered and hit up close, it fights back while still holding the slate.'),
    69: tell('It left without dying, so nothing dropped. Expected only after its watch ended or after ERASED; otherwise report the dump. Click to start over.', f'/function {NS}:setup', 'red'),
    70: tell('It died. Pick up the Scribe Record and the Marked Map.', None, 'green') + '\n'
        + tell('CHECK 5 - Use the record (right-click) WITHOUT a translator: raw Thaeven headed Vel-thae. Then click NEXT.', f'/function {NS}:translate', 'green'),
    75: tell('CHECK 6 - Use the record again: each English line above its Thaeven, e.g. Carries a blade. / Mends beneath cover. / Leaves by the east opening. No numbers.') + '\n'
        + tell('CHECK 7 - Hold the Marked Map: locked, centered on the shelter. Blue pointer at the east opening facing out, red point at the heater, red cross at the cage. Legend in the tooltip.') + '\n'
        + tell('Click NEXT to erase Maeve and check the snapshot rule.', f'/function {NS}:erase', 'green'),
    80: tell('CHECK 8 - Maeve is ERASED. Read the record and look at the map again: both must be exactly as before. The dump should show no beliefs.', '/fd maeve dump', 'aqua') + '\n'
        + tell('Done. Click to restore an empty, awake Maeve.', f'/function {NS}:restore', 'green'),
    90: tell('Maeve restored with an empty memory. The Scribe Check is complete.', None, 'aqua'),
}

SCRIPTS = {
    # msc_seen restarts at every world load and /reload; each player's current step prints once, two seconds in.
    'load': '''scoreboard objectives add msc dummy
scoreboard objectives add msc_drink minecraft.used:minecraft.potion
scoreboard objectives add msc_hit minecraft.used:minecraft.netherite_sword
scoreboard objectives remove msc_seen
scoreboard objectives add msc_seen dummy''',
    'cleanup': '''execute as @e[tag=msc_witness] run data merge entity @s {NoAI:1b}
kill @e[tag=msc_witness]''',
    # Flat ground: grass at y=-61, standing height y=-60. Shelter roof 394..406, east opening at x=407.
    'build': '''forceload add 336 336 463 463
fill 386 -61 386 414 -61 414 minecraft:smooth_stone
fill 386 -60 386 414 -50 414 minecraft:air
fill 394 -56 394 406 -56 406 minecraft:stone
fill 394 -60 394 394 -57 394 minecraft:oak_log
fill 406 -60 394 406 -57 394 minecraft:oak_log
fill 394 -60 406 394 -57 406 minecraft:oak_log
fill 406 -60 406 406 -57 406 minecraft:oak_log
setblock 400 -61 400 minecraft:lapis_block
fill 407 -61 399 410 -61 401 minecraft:gold_block
setblock 390 -60 400 frozendawn:thermal_heater[lit=true]{BurnTime:1000000}
setblock 360 -61 400 minecraft:emerald_block
function macs_scribe:clear_snow
function macs_scribe:booth''',
    # Drifts left by an earlier wait: snow falls within 64 blocks of the player. Quarters stay below the fill limit.
    'clear_snow': '\n'.join(f'fill {x} -60 {z} {x + 63} -55 {z + 63} minecraft:air replace minecraft:{block}'
                             for x in (336, 400) for z in (336, 400) for block in ('snow', 'snow_block')),
    # Bedrock cage with a head-height slit: the witness sees the shelter, heater and east crossing.
    # Bedrock floor too: an attacked witness otherwise digs down and tunnels out of the cage.
    'booth': '''fill 403 -61 402 405 -61 404 minecraft:bedrock
fill 403 -60 402 405 -58 404 minecraft:bedrock
fill 403 -59 402 405 -59 404 minecraft:air
setblock 404 -60 403 minecraft:air''',
    'setup': '''function macs_scribe:load
function macs_scribe:cleanup
tag @s add msc
fd postmaeve set-erased
fd postmaeve reset-erased confirm
fd world preset default
fd world set phase 6 late
fd maeve status
fd world set phase 0
gamerule doMobSpawning false
gamerule doDaylightCycle false
gamerule doWeatherCycle false
gamerule keepInventory true
weather clear
time set noon
function macs_scribe:build
spawnpoint @s 360 -60 400
gamemode survival @s
clear @s
effect give @s minecraft:resistance infinite 4 true
effect give @s minecraft:saturation infinite 0 true
effect give @s minecraft:night_vision infinite 0 true
scoreboard players set #rounds msc 0
scoreboard players set @s msc_seen 41
''' + tell('MACS Scribe Check: Maeve is awake with an empty memory. Four practice rounds teach her three habits, then a Scribe can appear.', None, 'aqua') + f'''
function {NS}:practice''',
    'practice': '''function macs_scribe:cleanup
function macs_scribe:booth
scoreboard players set #stage msc 10
scoreboard players set #timer msc 0
scoreboard players set #stood msc 0
scoreboard players set @s msc_drink 0
scoreboard players set @s msc_hit 0
clear @s
item replace entity @s hotbar.0 with ''' + SWORD + '''
item replace entity @s hotbar.1 with ''' + POTION + '''
tp @s 400.5 -60 400.5 -90 0
summon frozendawn:architect 404.5 -60 403.5 {Tags:["msc_witness"],PersistenceRequired:1b}
fd architect approach @e[tag=msc_witness,limit=1] @s
''' + prompt(10),
    'round_done': '''scoreboard players add #rounds msc 1
function macs_scribe:cleanup
scoreboard players set #stage msc 20
scoreboard players set #timer msc 0
tp @s 360.5 -60 400.5 90 0
''' + prompt(20),
    'ready': 'scoreboard players set #stage msc 21\n' + prompt(21),
    'next': guard(21) + f'''
execute if score #rounds msc matches ..{ROUNDS - 1} run function {NS}:practice
execute if score #stage msc matches 21 if score #rounds msc matches {ROUNDS}.. run function {NS}:checkpoint''',
    'checkpoint': 'scoreboard players set #stage msc 22\n' + prompt(22),
    'extra': guard(22) + f'''
function {NS}:practice''',
    # Natural spawning needs phase 6. Mid phase 6 has no snowfall, so sprinting cannot bury the arena in
    # full-block drifts the Scribe's no-dig walk cannot cross. Mid phase 6 is not yet vacuum: a full EVA
    # suit is climate-controlled (no freezing, no wind chill) and needs no oxygen. The player stays in
    # Survival under the roof; the first natural Architect after the gate is the Scribe. It is held (NoAI)
    # on its first tick so a sprint cannot skip it.
    'await': guard(22) + '''
scoreboard players set #stage msc 30
scoreboard players set #timer msc 0
tp @s 400.5 -60 400.5 -90 0
clear @s
item replace entity @s hotbar.0 with ''' + SWORD + '\n' + EVA + '''
effect give @s minecraft:regeneration infinite 1 true
fd world set phase 6 mid
''' + prompt(30),
    'found': f'''tag @s add msc_scribe
data merge entity @s {{NoAI:1b,Motion:[0.0d,0.0d,0.0d]}}
fd world set phase 0
scoreboard players set #stage msc 31
execute as @a[tag=msc] run {prompt(31)}''',
    'ordinary': f'''tag @s add msc_ordinary
data merge entity @s {{NoAI:1b,Motion:[0.0d,0.0d,0.0d]}}
fd world set phase 0
scoreboard players set #stage msc 39
execute as @a[tag=msc] run {prompt(39)}''',
    'show': guard(31) + '''
scoreboard players set #stage msc 40
data merge entity @e[tag=msc_scribe,limit=1] {NoAI:0b}
''' + prompt(40),
    'approach': guard(40) + '\nscoreboard players set #stage msc 50\n' + prompt(50),
    'hunt': guard(50) + '''
scoreboard players set #stage msc 60
effect give @s minecraft:speed 300 1 true
''' + prompt(60),
    # A Scribe that left without dying drops nothing: stage 69 offers a restart instead of CHECK 5.
    'gone': f'''execute if entity @e[type=item,nbt={{Item:{{id:"frozendawn:scribe_record"}}}}] run scoreboard players set #stage msc 70
execute unless score #stage msc matches 70 run scoreboard players set #stage msc 69
function {NS}:prompt''',
    'translate': guard(70) + '''
scoreboard players set #stage msc 75
give @s frozendawn:thaeven_translator
''' + prompt(75),
    'erase': guard(75) + '''
scoreboard players set #stage msc 80
fd postmaeve set-erased
''' + prompt(80),
    'restore': guard(80) + '''
scoreboard players set #stage msc 90
fd postmaeve reset-erased confirm
''' + prompt(90),
    'status': 'tellraw @s [{"text":"Scribe Check: stage="},{"score":{"name":"#stage","objective":"msc"}},'
              '{"text":" rounds="},{"score":{"name":"#rounds","objective":"msc"}},'
              '{"text":". Beliefs and the Scribe claim: /fd maeve dump."}]\n'
              f'function {NS}:prompt',
    'prompt': '\n'.join(f'execute if score #stage msc matches {stage} run {prompt(stage)}' for stage in PROMPTS),
    'rejoin': f'''scoreboard players set @s msc_seen 41
execute if entity @s[tag=msc] run function {NS}:status
execute unless entity @s[tag=msc] run ''' + tell('MACS Scribe Check: click to set up (erases Maeve, builds the arena).', f'/function {NS}:setup', 'aqua'),
}
SCRIPTS.update({f'prompt_{stage}': text for stage, text in PROMPTS.items()})
SCRIPTS['tick'] = f'''execute as @a unless score @s msc_seen matches 40.. run scoreboard players add @s msc_seen 1
execute as @a if score @s msc_seen matches 40 run function {NS}:rejoin
execute if score #stage msc matches 10 as @a[tag=msc,x=407,y=-60,z=399,dx=3,dy=2,dz=2] if score @s msc_drink matches 1.. if score @s msc_hit matches 1.. run scoreboard players add #stood msc 1
execute if score #stage msc matches 10 if score #stood msc matches 20.. as @a[tag=msc,limit=1] run function {NS}:round_done
execute if score #stage msc matches 20 run scoreboard players add #timer msc 1
execute if score #stage msc matches 20 if score #timer msc matches 630.. as @a[tag=msc,limit=1] run function {NS}:ready
execute if score #stage msc matches 30 run scoreboard players add #timer msc 1
execute if score #stage msc matches 30 as @e[{SCRIBE},tag=!msc_scribe,limit=1] run function {NS}:found
execute if score #stage msc matches 30 as @e[type=frozendawn:architect,tag=!msc_scribe,tag=!msc_ordinary,limit=1] run function {NS}:ordinary
execute if score #stage msc matches 40..60 unless entity @e[tag=msc_scribe] as @a[tag=msc,limit=1] run function {NS}:gone'''


def write_pack(path, game_test=False):
    functions = path / f'data/{NS}/function'
    functions.mkdir(parents=True, exist_ok=True)
    for name, content in SCRIPTS.items():
        (functions / f'{name}.mcfunction').write_text(content + '\n')
    if not game_test:
        (path / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 48, 'description': 'MACS Scribe Check'}}))
        tags = path / 'data/minecraft/tags/function'
        tags.mkdir(parents=True, exist_ok=True)
        for name in ('load', 'tick'):
            (tags / f'{name}.json').write_text(json.dumps({'values': [f'{NS}:{name}']}))


def splice_string(raw, key, value):
    marker = b'\x08' + len(key).to_bytes(2, 'big') + key
    if raw.count(marker) != 1:
        raise SystemExit(f'Expected one {key.decode()} in the closed source world')
    at = raw.index(marker) + len(marker)
    length = int.from_bytes(raw[at:at + 2], 'big')
    return raw[:at] + len(value).to_bytes(2, 'big') + value + raw[at + 2 + length:]


def enable_commands(raw):
    marker = b'\x01\x00\x0dallowCommands'
    if raw.count(marker) != 1:
        raise SystemExit('Expected one allowCommands in the closed source world')
    at = raw.index(marker) + len(marker)
    return raw[:at] + b'\x01' + raw[at + 1:]


def prepare(source, destination):
    if destination.exists():
        raise SystemExit(f'Refusing to overwrite {destination}')
    if (source / 'session.lock').exists() and not (source / 'level.dat').exists():
        raise SystemExit('Source is not a closed world')
    raw = gzip.decompress((source / 'level.dat').read_bytes())
    if b'minecraft:flat' not in raw:
        raise SystemExit('Source must be a superflat world: the arena is built on flat ground at y=-61')
    raw = enable_commands(splice_string(raw, b'LevelName', TITLE))
    shutil.copytree(source, destination, ignore=shutil.ignore_patterns('session.lock'))
    (destination / 'level.dat').write_bytes(gzip.compress(raw))
    if (destination / 'datapacks').exists():
        shutil.rmtree(destination / 'datapacks')
    write_pack(destination / 'datapacks/macs-scribe')
    print(f'Prepared {destination}. Open "{TITLE.decode()}"; the setup link appears in chat (or run /function {NS}:setup)')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pack-only', type=Path, help='write functions only (GameTest parser input)')
    parser.add_argument('--update-pack', type=Path, help='rewrite the pack inside an existing world, then /reload')
    parser.add_argument('--source', type=Path, help='closed superflat world to copy')
    parser.add_argument('--destination', type=Path, help='new save folder, e.g. run-lab/saves/MACS Scribe Check')
    args = parser.parse_args()
    if args.pack_only:
        write_pack(args.pack_only, True)
    elif args.update_pack:
        if args.update_pack.exists():
            shutil.rmtree(args.update_pack)
        write_pack(args.update_pack)
    elif args.source and args.destination:
        prepare(args.source, args.destination)
    else:
        parser.error('Use --pack-only, --update-pack or --source/--destination')

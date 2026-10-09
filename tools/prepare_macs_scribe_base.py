#!/usr/bin/env python3
"""Prepare the MACS Scribe Base world (section 9.4b) from a closed, normally generated world.

A natural variant of the Scribe Check: a small cabin at spawn, late-world cold, no teleports, effects or
time skips. Each practice encounter is one ordinary Architect called from out of sight; the Scribe itself
is only ever a natural spawn once Maeve's gate is met. Clickable links stay as a short checklist.
"""
import argparse
import gzip
import json
import shutil
from pathlib import Path

NS = 'macs_scribe_base'
OBJ = 'msb'
TITLE = b'MACS Scribe Base'
SCRIBE = 'type=frozendawn:architect,nbt={NeoForgeData:{macsScribe:1b}}'
GAP = 640  # quiet ticks between encounters, so each round is its own encounter
HABITS = (('#sword', 'PLAYER_PREFERS_SWORD', 'sword'),
          ('#cover', 'PLAYER_USES_RECOVERY_UNDER_COVER', 'drinks under cover'),
          ('#east', 'RETREAT_BEARING_E', 'leaves east'))


def item(slot, name, count=1, components=None):
    entry = f'{{Slot:{slot}b,id:"{name}",count:{count}'
    return entry + (f',components:{{{components}}}}}' if components else '}')


HEALING = '"minecraft:potion_contents":{potion:"minecraft:healing"}'
KIT = ','.join([item(0, 'frozendawn:eva_helmet'), item(1, 'frozendawn:eva_chestplate'),
                item(2, 'frozendawn:eva_leggings'), item(3, 'frozendawn:eva_boots'),
                item(4, 'minecraft:netherite_sword', 1, '"minecraft:enchantments":{levels:{"minecraft:sharpness":5}}'),
                item(5, 'minecraft:shield'), item(6, 'minecraft:cooked_beef', 32), item(7, 'minecraft:torch', 32)]
               + [item(slot, 'minecraft:potion', 1, HEALING) for slot in range(9, 18)]
               + [item(18, 'minecraft:bow'), item(19, 'minecraft:arrow', 64)])


def tell(text, command=None, color='gold'):
    msg = {'text': text, 'color': color}
    if command:
        msg['clickEvent'] = {'action': 'run_command', 'value': command}
        msg['underlined'] = True
    return 'tellraw @s ' + json.dumps(msg)


def guard(*stages):
    """A stale click only reprints the current step; it never advances or rebuilds anything."""
    ok = ' '.join(f'unless score #stage {OBJ} matches {s}' for s in stages)
    return (f'execute {ok} run function {NS}:prompt\n'
            f'execute {ok} run return 0')


def prompt(stage):
    return f'function {NS}:prompt_{stage}'


def habits():
    """Function output is silent, so each confidence comes back as a command result (whole percent, rounded down)."""
    parts = [{'text': "Maeve's read on you: ", 'color': 'aqua'}]
    for i, (var, _, label) in enumerate(HABITS):
        parts += [{'text': ('' if i == 0 else ', ') + label + ' '},
                  {'score': {'name': var, 'objective': OBJ}, 'color': 'yellow'}, {'text': '%'}]
    return '\n'.join(
        [f'execute store result score {var} {OBJ} run fd maeve confidence {pattern}' for var, pattern, _ in HABITS]
        + [f'scoreboard players set #short {OBJ} 0']
        + [f'execute unless score {var} {OBJ} matches 75.. run scoreboard players add #short {OBJ} 1' for var, _, _ in HABITS]
        + ['tellraw @s ' + json.dumps(parts)])


CALL = tell('CALL AN ARCHITECT', f'/function {NS}:call', 'green')

# One line per step. A rejoin, /reload or stale click prints the current one again without changing state.
PROMPTS = {
    10: tell('Your cabin is at spawn. Take the EVA suit and kit from the chest and put the suit on. When ready:') + '\n' + CALL,
    20: tell('An Architect is coming from the east. While it can see you: fight with the sword, drink a healing potion '
             'inside the cabin, and step out through the east door. Stay on the east side; any other way out from under cover counts against it.'),
    21: f'title @s actionbar {{"text":"Quiet before the next encounter...","color":"gray"}}',
    22: habits() + '\n'
        + f'execute if score #short {OBJ} matches 1.. run ' + tell('Not all three at 75% yet. When ready:', None, 'yellow') + '\n'
        + f'execute if score #short {OBJ} matches 1.. run ' + CALL + '\n'
        + f'execute if score #short {OBJ} matches 1.. run ' + tell('Or stop here and live normally: while Maeve knows anything, each natural '
                                                                  'Architect that is not a Scribe is a miss, and the 8th miss is always a Scribe.',
                                                                  f'/function {NS}:wait', 'aqua') + '\n'
        + f'execute if score #short {OBJ} matches 0 run function {NS}:await',
    30: tell('Live at the cabin as usual; nothing is called. Natural Architects roll every 10 seconds. With three confident '
             'beliefs the next one is a Scribe; with fewer, each ordinary one is a miss until one is.', None, 'aqua'),
    39: tell('An ordinary Architect spawned; it glows so you can find it. Below three confident beliefs that is a miss (the dump '
             'shows misses=n/8), unless the 5-day cooldown is still running. Kill it: no new Architect spawns within 96 blocks while it lives.',
             '/fd maeve dump', 'red'),
    40: tell('A Scribe has arrived. Look for white eyes and a slate in its hand. It keeps its distance and stares. '
             'Walk toward it: it should run and never attack. Then chase it down and kill it with the sword.', None, 'green'),
    31: tell('Quick mode. Maeve holds a fixed read on you: sword (always), east exit, recovery (perhaps), west exit (unsettled), '
             'ranged never seen. Only two are confident, so each roll is bad luck protection: miss n of 8 gives n/8, the 8th is '
             'always a Scribe. A miss never enters the world. Her map has no marks here; she has not seen this cabin.', None, 'aqua')
        + '\n' + tell('ROLL A NATURAL ARCHITECT', f'/function {NS}:roll', 'green'),
    69: tell('It left without dying, so nothing dropped. Tell me what you saw; the next one needs 5 in-game days.', '/fd maeve dump', 'red') + '\n'
        + tell('Wait for the next Scribe (stop any /tick sprint when it arrives, or its watch passes in seconds).', f'/function {NS}:wait', 'aqua')
        + f'\nexecute if score #quick {OBJ} matches 1 run ' + tell('AGAIN: clear the 5-day cooldown and roll again.', f'/function {NS}:again', 'green'),
    70: tell('It died. Pick up the record and the map. Read the record without a translator, then:', None, 'green') + '\n'
        + tell('NEXT', f'/function {NS}:translate', 'green'),
    75: tell('Read the record again with the translator (English over each line, no numbers). Hold the map: '
             'locked, centered on the cabin, marks at your east door and the heater. Then:') + '\n'
        + tell('ERASE MAEVE', f'/function {NS}:erase', 'green'),
    80: tell('Maeve is erased. The record and the map must read exactly as before. Then:', None, 'aqua') + '\n'
        + tell('RESTORE AN EMPTY MAEVE', f'/function {NS}:restore', 'green'),
    90: tell('Scribe Base check complete.', None, 'aqua')
        + f'\nexecute if score #quick {OBJ} matches 1 run ' + tell('AGAIN: clear the 5-day cooldown and roll again.', f'/function {NS}:again', 'green'),
}

SCRIPTS = {
    'load': f'''scoreboard objectives add {OBJ} dummy
scoreboard objectives remove {OBJ}_seen
scoreboard objectives add {OBJ}_seen dummy''',
    # Built where spawn meets the surface; the pad levels a 15x15 patch and the cabin is 9x9 with one door, east.
    # Window strips sit at head height on the other three walls, so a watcher outside can see in.
    'build': f'''kill @e[type=marker,tag=msb_base]
execute positioned 0 0 0 positioned over motion_blocking_no_leaves run summon marker ~ ~ ~ {{Tags:["msb_base"]}}
execute at @e[type=marker,tag=msb_base,limit=1] run function {NS}:build_here''',
    'build_here': '''fill ~-7 ~ ~-7 ~7 ~8 ~7 minecraft:air
fill ~-7 ~-4 ~-7 ~7 ~-2 ~7 minecraft:dirt replace #minecraft:replaceable
fill ~-7 ~-1 ~-7 ~7 ~-1 ~7 minecraft:grass_block
fill ~-4 ~-1 ~-4 ~4 ~-1 ~4 minecraft:spruce_planks
fill ~-4 ~ ~-4 ~4 ~2 ~4 minecraft:oak_planks outline
fill ~-3 ~ ~-3 ~3 ~2 ~3 minecraft:air
fill ~-4 ~ ~-4 ~-4 ~2 ~-4 minecraft:spruce_log
fill ~4 ~ ~-4 ~4 ~2 ~-4 minecraft:spruce_log
fill ~-4 ~ ~4 ~-4 ~2 ~4 minecraft:spruce_log
fill ~4 ~ ~4 ~4 ~2 ~4 minecraft:spruce_log
function macs_scribe_base:trim
fill ~4 ~ ~-1 ~4 ~1 ~1 minecraft:air
fill ~-4 ~1 ~-2 ~-4 ~1 ~2 minecraft:air
fill ~-2 ~1 ~-4 ~2 ~1 ~-4 minecraft:air
fill ~-2 ~1 ~4 ~2 ~1 ~4 minecraft:air
setblock ~-3 ~ ~3 frozendawn:thermal_heater[lit=true]{BurnTime:1000000}
setblock ~-3 ~ ~-3 minecraft:chest[facing=south]{Items:[''' + KIT + ''']}
setblock ~2 ~ ~-3 minecraft:red_bed[facing=north,part=head]
setblock ~2 ~ ~-2 minecraft:red_bed[facing=north,part=foot]
setblock ~0 ~2 ~0 minecraft:lantern[hanging=true]''',
    # The roof ends flush with the walls and no leaves shade the yard, so the east door is the only
    # covered-to-open crossing at the cabin: stepping out from an eave or a tree reads as a retreat that way.
    # Worlds built before this run it once, the first tick the cabin's marker is loaded.
    'trim': f'''fill ~-5 ~3 ~-5 ~5 ~3 ~5 minecraft:air
fill ~-4 ~3 ~-4 ~4 ~3 ~4 minecraft:spruce_planks
fill ~-16 ~-2 ~-16 ~16 ~18 ~16 minecraft:air replace #minecraft:leaves
scoreboard players set #trimmed {OBJ} 1''',
    'setup': f'''function {NS}:load
scoreboard players set #quick {OBJ} 0
function {NS}:prepare
''' + prompt(10),
    # Skips the practice rounds: a fixed belief set and a roll link, for checking the Scribe by eye (lab client only).
    'quick': f'''function {NS}:load
scoreboard players set #quick {OBJ} 1
function {NS}:prepare
effect give @s minecraft:resistance infinite 4 true
fd maeve scribe seed
scoreboard players set #stage {OBJ} 30
tag @e[type=frozendawn:architect] add msb_noted
''' + prompt(31),
    'roll': guard(30, 39) + f'''
execute at @e[type=marker,tag=msb_base,limit=1] positioned ~50 ~ ~ positioned over motion_blocking_no_leaves run fd maeve scribe roll''',
    # After a Scribe leaves the beliefs stand; after RESTORE Maeve is empty and is seeded again.
    'again': guard(69, 90) + f'''
execute if score #stage {OBJ} matches 90 run fd maeve scribe seed
fd maeve scribe clear-cooldown
scoreboard players set #stage {OBJ} 30
tag @e[type=frozendawn:architect] add msb_noted
''' + prompt(31),
    'prepare': f'''tag @s add msb
fd postmaeve set-erased
fd postmaeve reset-erased confirm
fd world preset default
fd world set phase 6 late
fd maeve status
fd world set phase 6 mid
gamerule keepInventory true
function {NS}:build
execute at @e[type=marker,tag=msb_base,limit=1] run spawnpoint @s ~1 ~ ~0
gamemode survival @s
clear @s
scoreboard players set #rounds {OBJ} 0
scoreboard players set @s {OBJ}_seen 41
scoreboard players set #stage {OBJ} 10
''' + tell('MACS Scribe Base: Maeve is awake with an empty memory.', None, 'aqua'),
    # An ordinary Architect, never a Scribe: designation exists only on the natural spawn path. It comes from the
    # east so the fight stays on the door side, away from whatever shade the terrain leaves elsewhere.
    'call': guard(10, 22) + f'''
scoreboard players set #stage {OBJ} 20
execute at @e[type=marker,tag=msb_base,limit=1] positioned ~50 ~ ~ positioned over motion_blocking_no_leaves run summon frozendawn:architect ~ ~ ~ {{Tags:["msb_called"],PersistenceRequired:1b}}
fd architect approach @e[tag=msb_called,limit=1] @s
''' + prompt(20),
    'round_done': f'''scoreboard players add #rounds {OBJ} 1
scoreboard players set #stage {OBJ} 21
scoreboard players set #timer {OBJ} 0''',
    'ready': f'scoreboard players set #stage {OBJ} 22\n' + prompt(22),
    # Architects already about when the gate is met were judged before it: only later spawns count.
    'wait': guard(22, 69) + f'\nfunction {NS}:await',
    'await': f'''scoreboard players set #stage {OBJ} 30
tag @e[type=frozendawn:architect] add msb_noted
''' + prompt(30),
    # The Scribe is not held: its watch runs on its own clock, exactly as in play.
    'found': f'''tag @s add msb_scribe
scoreboard players set #stage {OBJ} 40
execute as @a[tag=msb] run {prompt(40)}''',
    'ordinary': f'''tag @s add msb_noted
effect give @s minecraft:glowing infinite 0 true
scoreboard players set #stage {OBJ} 39
execute as @a[tag=msb] run {prompt(39)}''',
    'gone': f'''execute if entity @e[type=item,nbt={{Item:{{id:"frozendawn:scribe_record"}}}}] run scoreboard players set #stage {OBJ} 70
execute unless score #stage {OBJ} matches 70 run scoreboard players set #stage {OBJ} 69
function {NS}:prompt''',
    'translate': guard(70) + f'''
scoreboard players set #stage {OBJ} 75
give @s frozendawn:thaeven_translator
''' + prompt(75),
    'erase': guard(75) + f'''
scoreboard players set #stage {OBJ} 80
fd postmaeve set-erased
''' + prompt(80),
    'restore': guard(80) + f'''
scoreboard players set #stage {OBJ} 90
fd postmaeve reset-erased confirm
''' + prompt(90),
    'prompt': '\n'.join(f'execute if score #stage {OBJ} matches {stage} run {prompt(stage)}' for stage in PROMPTS),
    'rejoin': f'''scoreboard players set @s {OBJ}_seen 41
execute if entity @s[tag=msb] run function {NS}:prompt
execute unless entity @s[tag=msb] run ''' + tell('MACS Scribe Base: click to begin (wakes an empty Maeve, builds a cabin at spawn).', f'/function {NS}:setup', 'aqua') + '''
execute unless entity @s[tag=msb] run ''' + tell('Or QUICK: fixed beliefs and a roll link, no practice rounds (lab client only).', f'/function {NS}:quick', 'green'),
}
SCRIPTS.update({f'prompt_{stage}': text for stage, text in PROMPTS.items()})
SCRIPTS['tick'] = f'''execute if score #stage {OBJ} matches 10.. unless score #trimmed {OBJ} matches 1 at @e[type=marker,tag=msb_base,limit=1] run function {NS}:trim
execute as @a unless score @s {OBJ}_seen matches 40.. run scoreboard players add @s {OBJ}_seen 1
execute as @a if score @s {OBJ}_seen matches 40 run function {NS}:rejoin
execute if score #stage {OBJ} matches 20 unless entity @e[tag=msb_called] run function {NS}:round_done
execute if score #stage {OBJ} matches 21 run scoreboard players add #timer {OBJ} 1
execute if score #stage {OBJ} matches 21 as @a[tag=msb] run {prompt(21)}
execute if score #stage {OBJ} matches 21 if score #timer {OBJ} matches {GAP}.. as @a[tag=msb,limit=1] run function {NS}:ready
execute if score #stage {OBJ} matches 10..39 as @e[{SCRIBE},tag=!msb_scribe,limit=1] run function {NS}:found
execute if score #stage {OBJ} matches 30..39 as @e[type=frozendawn:architect,tag=!msb_called,tag=!msb_scribe,tag=!msb_noted,limit=1] run function {NS}:ordinary
execute if score #stage {OBJ} matches 40 unless entity @e[tag=msb_scribe] as @a[tag=msb,limit=1] run function {NS}:gone'''


def write_pack(path, game_test=False):
    functions = path / f'data/{NS}/function'
    functions.mkdir(parents=True, exist_ok=True)
    for name, content in SCRIPTS.items():
        (functions / f'{name}.mcfunction').write_text(content + '\n')
    if not game_test:
        (path / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 48, 'description': 'MACS Scribe Base'}}))
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


def at_least_a_day(raw):
    """Quick mode seeds about half a day of past evidence, which a brand-new world cannot hold."""
    marker = b'\x04\x00\x04Time'
    if raw.count(marker) != 1:
        raise SystemExit('Expected one Time in the closed source world')
    at = raw.index(marker) + len(marker)
    time = max(int.from_bytes(raw[at:at + 8], 'big', signed=True), 24000)
    return raw[:at] + time.to_bytes(8, 'big', signed=True) + raw[at + 8:]


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
    if b'minecraft:noise' not in raw:
        raise SystemExit('Source must be a normally generated world')
    raw = at_least_a_day(enable_commands(splice_string(raw, b'LevelName', TITLE)))
    shutil.copytree(source, destination, ignore=shutil.ignore_patterns('session.lock'))
    (destination / 'level.dat').write_bytes(gzip.compress(raw))
    if (destination / 'datapacks').exists():
        shutil.rmtree(destination / 'datapacks')
    write_pack(destination / 'datapacks/macs-scribe-base')
    print(f'Prepared {destination}. Open "{TITLE.decode()}"; the begin link appears in chat (or run /function {NS}:setup)')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pack-only', type=Path, help='write functions only (GameTest parser input)')
    parser.add_argument('--update-pack', type=Path, help='rewrite the pack inside an existing world, then /reload')
    parser.add_argument('--source', type=Path, help='closed, normally generated world to copy')
    parser.add_argument('--destination', type=Path, help='new save folder, e.g. run-lab/saves/MACS Scribe Base')
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

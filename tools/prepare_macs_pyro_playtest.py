#!/usr/bin/env python3
"""Prepare a new natural-terrain save for the MACS Pyro check (section 9.4a).

After the first player joins, a BEGIN link wakes an empty Maeve, clears a flat snow field around them and
builds a heated test cabin there, then tells Maeve she suspects the player lives in it. Chat links switch
the cabin between the cases thermal sight must tell apart, call a Pyro from out of sight (lab client only),
and read the result back. Advancement popups stay hidden; Covered in Mud is checked with a link instead.
"""
import argparse
import copy
import fcntl
import gzip
import io
import json
from pathlib import Path
import time
from prepare_blind_architect_playtest import read, string, utf, write, lab_state, pack_metadata, write_sync_advancement

ROOT = Path(__file__).resolve().parents[1]
TITLE = 'MACS Pyro Check'
SEED = 20261022
NS = 'macs_pyro'
OBJ = 'mpy'
PACK = 'macs-pyro'
BASE = f'@e[type=marker,tag={OBJ}_base,limit=1]'
FIELD = 48  # flat, open ground out to here; the Pyro's post is 40 blocks from the cabin


def tell(text, command=None, color='gold'):
    msg = {'text': text, 'color': color}
    if command:
        msg['clickEvent'] = {'action': 'run_command', 'value': command}
        msg['underlined'] = True
    return 'tellraw @s ' + json.dumps(msg)


def button(label, function, color='aqua'):
    return {'text': '['+label+'] ', 'color': color,
            'clickEvent': {'action': 'run_command', 'value': '/function '+NS+':'+function}}


MENU = json.dumps(['', {'text': 'Cabin: ', 'color': 'gray'},
                   button('Thin wall', 'thin'), button('Open door', 'open'), button('Sealed', 'sealed'),
                   button('Decoy', 'decoy', 'yellow'), button('No decoy', 'no_decoy', 'yellow'),
                   {'text': '\nPyro: ', 'color': 'gray'},
                   button('CALL', 'call', 'green'), button('Vanish', 'vanish', 'green'), button('Dump', 'dump'), button('Mud?', 'mud'),
                   button('Fresh Maeve', 'fresh', 'red')])


def guarded(body):
    """Every link but BEGIN does nothing before setup, so a stale click never builds or calls anything."""
    return f'execute unless score #stage {OBJ} matches 10.. run return 0\n' + body


def field():
    """Air above, snow underfoot, solid below; strips keep each fill under the command block limit."""
    lines = []
    for x in range(-FIELD, FIELD+1, 8):
        lines.append(f'fill ~{x} ~ ~{-FIELD} ~{min(x+7, FIELD)} ~12 ~{FIELD} minecraft:air')
        lines.append(f'fill ~{x} ~-4 ~{-FIELD} ~{min(x+7, FIELD)} ~-2 ~{FIELD} minecraft:dirt replace #minecraft:replaceable')
    lines.append(f'fill ~{-FIELD} ~-1 ~{-FIELD} ~{FIELD} ~-1 ~{FIELD} minecraft:snow_block')
    return '\n'.join(lines)


def cabin(wall, door_open=False, window=False):
    """Inside x/z -3..3, y 0..2; walls at 4, roof at y 3. The heater stands against the east wall, which faces the
    Pyro's post, and the only door is in that wall too."""
    half = 'true' if door_open else 'false'
    lines = [
        'fill ~-5 ~ ~-5 ~5 ~4 ~5 minecraft:air',
        'fill ~-4 ~-1 ~-4 ~4 ~-1 ~4 minecraft:stone',
        f'fill ~-4 ~ ~-4 ~4 ~3 ~4 {wall} outline',
        'fill ~-3 ~ ~-3 ~3 ~2 ~3 minecraft:air',
        'setblock ~3 ~ ~0 frozendawn:thermal_heater[lit=true]{BurnTime:1000000}',
        f'setblock ~4 ~ ~2 minecraft:oak_door[facing=west,half=lower,hinge=left,open={half}]',
        f'setblock ~4 ~1 ~2 minecraft:oak_door[facing=west,half=upper,hinge=left,open={half}]',
    ]
    if window:
        lines.append('setblock ~4 ~1 ~0 minecraft:glass')
    return '\n'.join(lines)


SCRIPTS = {
    'load': f'''scoreboard objectives add {OBJ} dummy
scoreboard objectives remove {OBJ}_seen
scoreboard objectives add {OBJ}_seen dummy
execute unless score #stage {OBJ} matches 0.. run scoreboard players set #stage {OBJ} 0''',
    # A short wait after joining, so the field's chunks are loaded before anything is built.
    'tick': f'''execute as @a unless score @s {OBJ}_seen matches 40.. run scoreboard players add @s {OBJ}_seen 1
execute as @a if score @s {OBJ}_seen matches 40 run function {NS}:rejoin''',
    'rejoin': f'''scoreboard players set @s {OBJ}_seen 41
execute if score #stage {OBJ} matches 10.. run function {NS}:menu
execute unless score #stage {OBJ} matches 10.. run ''' + tell(
        'MACS Pyro Check: stand on open ground and click to begin. Wakes an empty Maeve, clears a snow field around you and builds a heated cabin here.',
        f'/function {NS}:setup', 'aqua'),
    'setup': f'''execute if score #stage {OBJ} matches 10.. run return 0
scoreboard players set #stage {OBJ} 10
fd postmaeve set-erased
fd postmaeve reset-erased confirm
fd world preset default
fd world set phase 6 late
fd maeve status
fd world set phase 6 mid
kill @e[type=marker,tag={OBJ}_base]
execute align xyz positioned ~.5 ~ ~.5 run summon marker ~ ~ ~ {{Tags:["{OBJ}_base"]}}
execute at {BASE} run function {NS}:field
function {NS}:thin
execute at {BASE} run spawnpoint @s ~ ~ ~
execute at {BASE} run tp @s ~ ~ ~
execute at {BASE} run fd maeve pyro seed-home
function {NS}:kit
gamemode survival @s
''' + tell('Maeve is awake and suspects you live in this cabin. She knows of no heat here yet.', None, 'aqua')
        + f'\nfunction {NS}:menu',
    'field': field(),
    'kit': '\n'.join([
        'clear @s',
        'item replace entity @s armor.head with frozendawn:eva_helmet',
        'item replace entity @s armor.chest with frozendawn:eva_chestplate',
        'item replace entity @s armor.legs with frozendawn:eva_leggings',
        'item replace entity @s armor.feet with frozendawn:eva_boots',
        'item replace entity @s hotbar.0 with minecraft:netherite_sword',
        'item replace entity @s hotbar.1 with minecraft:spyglass',
        'item replace entity @s hotbar.2 with minecraft:cooked_beef 32',
        'item replace entity @s inventory.0 with frozendawn:o2_tank_mk3',
        'item replace entity @s inventory.1 with frozendawn:o2_tank_mk3',
        'effect give @s minecraft:resistance infinite 4 true',
    ]),
    'thin': guarded(f'execute at {BASE} run function {NS}:thin_here\n'
                    + tell('Thin stone walls, door shut. Expect a faint glow on the east wall: WALL_GLOW in the dump.')
                    + f'\nfunction {NS}:menu'),
    'thin_here': cabin('minecraft:stone'),
    'open': guarded(f'execute at {BASE} run function {NS}:open_here\n'
                    + tell('Thin stone walls, east door open. Expect heat leaking out of the doorway: LEAKED in the dump.')
                    + f'\nfunction {NS}:menu'),
    'open_here': cabin('minecraft:stone', door_open=True),
    'sealed': guarded(f'execute at {BASE} run function {NS}:sealed_here\n'
                      + tell('Wool walls and roof, door shut, a glass window beside the heater. Expect nothing seen; '
                             'with no decoy you earn Covered in Mud when it leaves.')
                      + f'\nfunction {NS}:menu'),
    'sealed_here': cabin('minecraft:white_wool', window=True),
    # Magma rather than a campfire: phase 6 is vacuum, which snuffs an open flame the moment it is placed.
    'decoy': guarded(f'execute at {BASE} run setblock ~ ~ ~-14 minecraft:magma_block\n'
                     + tell('A magma decoy 14 blocks north, in the open. Expect it seen directly: DIRECT in the dump.')
                     + f'\nfunction {NS}:menu'),
    'no_decoy': guarded(f'execute at {BASE} run setblock ~ ~ ~-14 minecraft:air\nfunction {NS}:menu'),
    # Clearing the cooldown first lets a pass be repeated; it refuses while a Pyro is still out.
    # It arrives just inside the field: past its edge the terrain is natural and may be open water.
    'call': guarded(f'''fd maeve pyro clear-cooldown
execute at {BASE} positioned ~{FIELD-2} ~ ~ positioned over motion_blocking_no_leaves run fd maeve pyro roll
''' + tell('If it rolled a Pyro, it comes from the east (orange eyes), stops about 40 blocks out, stands and stares. '
           'It looks after 10 seconds, then every 5, and after a minute souls rush into it and it vanishes. '
           'Vanish skips the wait. Stay put or watch from the cabin.', None, 'aqua')),
    # Lab only: the Pyro out now skips the rest of its watch and vanishes where it stands.
    'vanish': guarded('fd maeve pyro vanish'),
    'dump': guarded('fd maeve dump'),
    'mud': guarded(f'''execute if entity @s[advancements={{frozendawn:covered_in_mud=true}}] run ''' + tell('Covered in Mud: earned.', None, 'green') + f'''
execute unless entity @s[advancements={{frozendawn:covered_in_mud=true}}] run ''' + tell('Covered in Mud: not earned.', None, 'red')),
    # Erases this test save's Maeve and wakes her empty again, so a known heat source no longer stops the next Pyro.
    'fresh': guarded(f'''advancement revoke @s only frozendawn:covered_in_mud
fd postmaeve set-erased
fd postmaeve reset-erased confirm
execute at {BASE} run fd maeve pyro seed-home
''' + tell('Maeve is empty again and suspects only this cabin.', None, 'aqua') + f'\nfunction {NS}:menu'),
    'menu': 'tellraw @s ' + MENU,
}


def covered_in_mud():
    """The test pack's filter hides every lower pack's advancements, so this world keeps its own silent copy.
    It is a root here only because its real parent, phase 6, is filtered out with the rest."""
    return {'display': {'icon': {'id': 'minecraft:mud'},
                        'title': {'translate': 'advancement.frozendawn.covered_in_mud.title'},
                        'description': {'translate': 'advancement.frozendawn.covered_in_mud.description'},
                        'background': 'minecraft:textures/block/mud.png',
                        'frame': 'task', 'show_toast': False, 'announce_to_chat': False, 'hidden': True},
            'criteria': {'impossible': {'trigger': 'minecraft:impossible'}}}


def write_functions(destination):
    functions = destination/('data/'+NS+'/function')
    functions.mkdir(parents=True, exist_ok=True)
    for name, commands in SCRIPTS.items():
        (functions/(name+'.mcfunction')).write_text(commands+'\n')


def pack(destination):
    destination.mkdir(parents=True, exist_ok=False)
    (destination/'pack.mcmeta').write_text(json.dumps(pack_metadata('MACS Pyro check; advancements hidden'))+'\n')
    write_sync_advancement(destination)
    mud = destination/'data/frozendawn/advancement/covered_in_mud.json'
    mud.parent.mkdir(parents=True)
    mud.write_text(json.dumps(covered_in_mud(), indent=2)+'\n')
    write_functions(destination)
    tags = destination/'data/minecraft/tags/function'
    tags.mkdir(parents=True)
    for name in ('load', 'tick'):
        (tags/(name+'.json')).write_text(json.dumps({'values': [NS+':'+name]})+'\n')


def prepare(template):
    destination = ROOT/'run-lab/saves'/TITLE
    if destination.exists():
        raise SystemExit(f'Refusing to overwrite {destination}')
    # The template's world lock proves it is closed; only version schema is read from it.
    with (template.parent/'session.lock').open('r+b') as lock:
        fcntl.lockf(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        source = io.BytesIO(gzip.decompress(template.read_bytes()))
        assert source.read(1) == b'\x0a'
        string(source)
        original = read(source, 10)['Data'][1]
        data = {name: copy.deepcopy(original[name]) for name in ('version', 'DataVersion', 'Version', 'WorldGenSettings')}
    worldgen = data['WorldGenSettings'][1]
    worldgen['seed'] = (4, SEED)
    worldgen['generate_features'] = (1, 1)
    worldgen['bonus_chest'] = (1, 0)
    worldgen['dimensions'][1]['minecraft:overworld'] = (10, {
        'type': (8, 'minecraft:overworld'),
        'generator': (10, {'type': (8, 'minecraft:noise'), 'settings': (8, 'minecraft:overworld'),
                           'biome_source': (10, {'type': (8, 'minecraft:multi_noise'), 'preset': (8, 'minecraft:overworld')})})})
    data.update({'LevelName': (8, TITLE), 'GameType': (3, 1), 'allowCommands': (1, 1), 'Difficulty': (1, 2),
        'DifficultyLocked': (1, 1), 'hardcore': (1, 0), 'initialized': (1, 1), 'Time': (4, 0), 'DayTime': (4, 6000),
        'LastPlayed': (4, int(time.time()*1000)), 'SpawnX': (3, 0), 'SpawnY': (3, 80), 'SpawnZ': (3, 0),
        'SpawnAngle': (5, 0.0), 'WasModded': (1, 1),
        'GameRules': (10, {'doMobSpawning': (8, 'false'), 'keepInventory': (8, 'true'),
                           'announceAdvancements': (8, 'false'), 'doDaylightCycle': (8, 'false')}),
        'DataPacks': (10, {'Enabled': (9, (8, ['vanilla', 'mod_data', 'file/'+PACK])), 'Disabled': (9, (8, []))}),
        'enabled_features': (9, (8, ['minecraft:vanilla']))})
    root = {'Data': (10, data)}
    encoded = b'\x0a'+utf('')+write(10, root)
    check = io.BytesIO(encoded); assert check.read(1) == b'\x0a'; string(check)
    assert read(check, 10) == root and check.read() == b''
    destination.mkdir(parents=True)
    (destination/'level.dat').write_bytes(gzip.compress(encoded))
    (destination/'data').mkdir()
    (destination/'data/frozendawn_apocalypse.dat').write_bytes(gzip.compress(lab_state(data['DataVersion'][1])))
    pack(destination/'datapacks'/PACK)
    print(f'Prepared {destination}; not opened.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--template', type=Path, help='level.dat of a closed world, read for its version schema only')
    parser.add_argument('--pack-only', type=Path, help='write functions only (GameTest parser input)')
    args = parser.parse_args()
    if args.pack_only:
        write_functions(args.pack_only)
    elif args.template:
        prepare(args.template)
    else:
        parser.error('Use --template or --pack-only')

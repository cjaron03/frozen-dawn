#!/usr/bin/env python3
"""Prepare a new default-terrain cave save; never copy gameplay or force spawns."""
import argparse
import copy
import fcntl
import gzip
import hashlib
import io
import json
from pathlib import Path
import time
from prepare_blind_architect_playtest import read, string, utf, write, lab_state, pack_metadata, write_sync_advancement

ROOT = Path(__file__).resolve().parents[1]
TITLE = 'Blind Architect 10 Natural Cave'
SEED = 77371337
SCRIPTS = {
    'load': 'scoreboard objectives add blind_natural dummy\nexecute unless score #stage blind_natural matches 0.. run scoreboard players set #stage blind_natural 0',
    'prepare': '''execute unless score #stage blind_natural matches 0 run return 0
execute store result score #ready blind_natural run fdlab natural_prepare
execute unless score #ready blind_natural matches 1 run return 0
scoreboard players set #stage blind_natural 9
fd world set phase 6 late
item replace entity @s armor.head with frozendawn:eva_helmet
item replace entity @s armor.chest with frozendawn:eva_chestplate
item replace entity @s armor.legs with frozendawn:eva_leggings
item replace entity @s armor.feet with frozendawn:eva_boots
item replace entity @s inventory.0 with frozendawn:o2_tank_mk3
item replace entity @s inventory.1 with frozendawn:o2_tank_mk3
item replace entity @s inventory.2 with frozendawn:o2_tank_mk3
item replace entity @s inventory.3 with frozendawn:o2_tank_mk3
item replace entity @s hotbar.0 with minecraft:iron_sword
item replace entity @s hotbar.1 with minecraft:iron_pickaxe
item replace entity @s hotbar.2 with minecraft:cooked_beef 64
item replace entity @s hotbar.3 with minecraft:stone 64
item replace entity @s hotbar.4 with minecraft:white_wool 64
item replace entity @s hotbar.5 with minecraft:torch 32
spawnpoint @s ~ ~ ~
scoreboard players set #stage blind_natural 1
tellraw @s {"text":"NATURAL CAVE READY: untouched generated terrain. Phase and survival kit supplied; no actor summoned. Wait for the start handoff.","color":"aqua"}
return 1''',
    'start': '''execute unless score #stage blind_natural matches 1 run return 0
execute store result score #access blind_natural run fdlab natural_access
execute unless score #access blind_natural matches 1 run return 0
gamerule doMobSpawning true
return run fdlab natural_start''',
    'stop': '''execute unless score #stage blind_natural matches 2 run return 0
return run fdlab natural_stop''',
    'fast_wait': '''execute unless score #stage blind_natural matches 3 run return 0
return run fdlab natural_fast_wait''',
    'fast_play': '''execute unless score #stage blind_natural matches 2 run return 0
return run fdlab natural_fast_play''',
    'fast_repeat': '''execute unless score #stage blind_natural matches 3 run return 0
return run fdlab natural_fast_repeat''',
    'arrival_play': '''execute unless score #stage blind_natural matches 3 run return 0
return run fdlab natural_arrival_play''',
}


def pack(destination, fixture=False):
    destination.mkdir(parents=True, exist_ok=False)
    if not fixture:
        (destination/'pack.mcmeta').write_text(json.dumps(pack_metadata('Natural cave observation; advancements hidden'))+'\n')
        write_sync_advancement(destination)
    functions = destination/'data/blind_natural/function'
    functions.mkdir(parents=True)
    for name, commands in SCRIPTS.items():
        (functions/(name+'.mcfunction')).write_text(commands+'\n')
    if not fixture:
        tags = destination/'data/minecraft/tags/function'
        tags.mkdir(parents=True)
        (tags/'load.json').write_text(json.dumps({'values':['blind_natural:load']})+'\n')
    else:
        import shutil
        shutil.copytree(ROOT/'src/test/resources/blind-natural-stopped-wait', destination/'data/blind_natural/qa/stopped-wait')
        shutil.copytree(ROOT/'src/test/resources/blind-natural-same-tick-arrivals', destination/'data/blind_natural/qa/same-tick-arrivals')


def prepare(template):
    destination = ROOT/'run-lab/saves'/TITLE
    if destination.exists():
        raise SystemExit(f'Refusing to overwrite {destination}')
    # A stopped client's heartbeat is stale by definition. The kernel world lock
    # is the closure check; the completed world is only a version-schema source.
    with (template.parent/'session.lock').open('r+b') as lock:
        fcntl.lockf(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        source = io.BytesIO(gzip.decompress(template.read_bytes()))
        assert source.read(1) == b'\x0a'
        string(source)
        original = read(source, 10)['Data'][1]
        data = {name:copy.deepcopy(original[name]) for name in ('version','DataVersion','Version','WorldGenSettings')}
    worldgen = data['WorldGenSettings'][1]
    worldgen['seed'] = (4, SEED)
    worldgen['generate_features'] = (1, 1)
    worldgen['bonus_chest'] = (1, 0)
    worldgen['dimensions'][1]['minecraft:overworld'] = (10, {
        'type':(8,'minecraft:overworld'),
        'generator':(10, {'type':(8,'minecraft:noise'),'settings':(8,'minecraft:overworld'),
                         'biome_source':(10,{'type':(8,'minecraft:multi_noise'),'preset':(8,'minecraft:overworld')})})})
    data.update({'LevelName':(8,TITLE),'GameType':(3,1),'allowCommands':(1,1),'Difficulty':(1,2),
        'DifficultyLocked':(1,1),'hardcore':(1,0),'initialized':(1,1),'Time':(4,0),'DayTime':(4,6000),
        'LastPlayed':(4,int(time.time()*1000)),'SpawnX':(3,0),'SpawnY':(3,80),'SpawnZ':(3,0),
        'SpawnAngle':(5,0.0),'WasModded':(1,1),
        'GameRules':(10,{'doMobSpawning':(8,'false'),'keepInventory':(8,'true'),'announceAdvancements':(8,'false')}),
        'DataPacks':(10,{'Enabled':(9,(8,['vanilla','mod_data','file/blind-natural'])),'Disabled':(9,(8,[]))}),
        'enabled_features':(9,(8,['minecraft:vanilla']))})
    root = {'Data':(10,data)}
    encoded = b'\x0a'+utf('')+write(10,root)
    check = io.BytesIO(encoded); assert check.read(1) == b'\x0a'; string(check)
    assert read(check,10) == root and check.read() == b''
    destination.mkdir(parents=True)
    (destination/'level.dat').write_bytes(gzip.compress(encoded))
    (destination/'data').mkdir()
    (destination/'data/frozendawn_apocalypse.dat').write_bytes(gzip.compress(lab_state(data['DataVersion'][1])))
    pack(destination/'datapacks/blind-natural')
    report = {'world':TITLE,'seed':SEED,'generator':'minecraft:noise / minecraft:overworld',
        'copied_gameplay_files':0,'terrain_edits':0,'summoned_actors':0,'spawn_multiplier':1,
        'setup_assistance':'Phase 6 late, EVA/O2/tools and teleport to an existing dark cave; no altered spawn rolls.',
        'pending':'Owner opens and pauses before prepare; start is separately armed.',
        'files':{str(f.relative_to(destination)):hashlib.sha256(f.read_bytes()).hexdigest()
                 for f in destination.rglob('*') if f.is_file()}}
    (destination/'natural-preparation.json').write_text(json.dumps(report,indent=2)+'\n')
    print(f'Prepared {destination}; generated terrain, no gameplay copied, not opened or started.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--template',type=Path)
    parser.add_argument('--pack-only',type=Path)
    args = parser.parse_args()
    if args.pack_only:
        pack(args.pack_only, fixture=True)
    elif args.template:
        prepare(args.template)
    else:
        parser.error('Choose --template or --pack-only')

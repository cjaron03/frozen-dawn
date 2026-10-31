#!/usr/bin/env python3
"""Create a fresh disposable operator ERASED fixture; copy immutable metadata only."""
import argparse
import copy
import gzip
import hashlib
import io
import json
from pathlib import Path
from prepare_blind_architect_playtest import encode_metadata, lab_state, pack_metadata, read, write_sync_advancement, string, utf, write

ROOT = Path(__file__).resolve().parents[1]
TITLE = 'Blind Architect 11 ERASED'
SCRIPTS = {
    'load': 'scoreboard objectives add blind_erased dummy\nexecute unless score #stage blind_erased matches 0.. run scoreboard players set #stage blind_erased 0',
    'prepare': '''execute unless score #stage blind_erased matches 0 run return 0
execute store result score #access blind_erased run fdlab erased_access
execute unless score #access blind_erased matches 1 run return 0
scoreboard players set #stage blind_erased 9
fill 0 -45 0 30 -40 10 minecraft:bedrock
fill 1 -44 1 29 -43 9 minecraft:air
fill 16 -44 1 16 -44 9 minecraft:bedrock
setblock 9 -45 5 minecraft:sea_lantern
setblock 23 -45 5 minecraft:sea_lantern
gamerule doMobSpawning false
gamerule keepInventory true
difficulty normal
fd world set phase 6 late
item replace entity @s armor.head with frozendawn:eva_helmet
item replace entity @s armor.chest with frozendawn:eva_chestplate
item replace entity @s armor.legs with frozendawn:eva_leggings
item replace entity @s armor.feet with frozendawn:eva_boots
item replace entity @s inventory.0 with frozendawn:o2_tank_mk3
item replace entity @s inventory.1 with frozendawn:o2_tank_mk3
item replace entity @s hotbar.0 with minecraft:air
tp @s 23.5 -44 5.5 90 0
summon frozendawn:architect 9.5 -44 5.5 {BlindArchitect:1b,NoAI:1b,OnGround:1b,PersistenceRequired:1b,Rotation:[-90f,0f],Tags:["blind_erased_actor"]}
gamemode survival @s
gamerule doMobSpawning true
scoreboard players set #stage blind_erased 1
tellraw @s {"text":"ERASED fixture prepared. Pause immediately and reply ready. Stay on this square during the view; transition and player-source test hit are automatic.","color":"aqua"}
return 1''',
    'start': 'execute unless score #stage blind_erased matches 1 run return 0\nreturn run fdlab erased_start',
}


def pack(destination, fixture=False):
    root = destination / 'data'
    if not fixture:
        (destination / 'pack.mcmeta').write_text(json.dumps(pack_metadata('Blind ERASED operator view'), indent=2)+'\n')
        write_sync_advancement(destination)
    folder = root / 'blind_erased/function'
    folder.mkdir(parents=True)
    for name, text in SCRIPTS.items():
        (folder / (name+'.mcfunction')).write_text(text+'\n')
    if not fixture:
        tags = root / 'minecraft/tags/function'
        tags.mkdir(parents=True)
        (tags / 'load.json').write_text(json.dumps({'values':['blind_erased:load']})+'\n')


def prepare(template):
    destination = ROOT / 'run-lab/saves' / TITLE
    if destination.exists():
        raise ValueError('Never replace an existing ERASED attempt')
    stream = io.BytesIO(gzip.decompress(template.read_bytes()))
    assert stream.read(1) == b'\x0a'
    string(stream)
    original = read(stream, 10)['Data'][1]
    base = io.BytesIO(encode_metadata(copy.deepcopy(original)))
    assert base.read(1) == b'\x0a'
    string(base)
    root = read(base, 10)
    data = root['Data'][1]
    data['LevelName'] = (8, TITLE)
    data['DataPacks'][1]['Enabled'] = (9, (8, ['vanilla','mod_data','file/blind-erased']))
    destination.mkdir(parents=True)
    (destination / 'level.dat').write_bytes(gzip.compress(b'\x0a'+utf('')+write(10,root)))
    (destination / 'data').mkdir()
    (destination / 'data/frozendawn_apocalypse.dat').write_bytes(gzip.compress(lab_state(data['DataVersion'][1])))
    folder = destination / 'datapacks/blind-erased'
    folder.mkdir(parents=True)
    pack(folder)
    report = {'world':TITLE,'operatorFixture':True,'copiedGameplayFiles':0,'copiedMetadataOnly':str(template.resolve()),
              'forcedActorPlacement':True,'terrainConstruction':'Protected room built only by approved prepare in this new world',
              'fullHearthSequenceTested':False,'status':'Not opened or prepared','files':{
                  str(p.relative_to(destination)):hashlib.sha256(p.read_bytes()).hexdigest()
                  for p in destination.rglob('*') if p.is_file()}}
    (destination / 'erased-preparation.json').write_text(json.dumps(report,indent=2)+'\n')
    print('Created fresh disposable fixture:',destination)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--template',type=Path)
    parser.add_argument('--pack-only',type=Path)
    args = parser.parse_args()
    if args.pack_only:
        args.pack_only.mkdir(parents=True,exist_ok=True)
        pack(args.pack_only,fixture=True)
    elif args.template:
        prepare(args.template)
    else:
        parser.error('Choose --template or --pack-only')

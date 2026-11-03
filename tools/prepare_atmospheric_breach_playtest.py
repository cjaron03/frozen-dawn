#!/usr/bin/env python3
"""A disposable atmospheric breach lab; never replaces an existing world."""
import argparse, gzip, hashlib, json
from pathlib import Path
import prepare_ration_warmer_playtest as seed
TITLE='Atmospheric Breach Check';PACK='atmospheric-breach-check';NS='atmospheric_breach_check'
def message(text,action=None):
    data={'text':text,'color':'aqua'}
    if action:data['clickEvent']={'action':'run_command','value':f'/function {NS}:{action}'}
    return 'tellraw @s '+json.dumps(data)
def controls():
    return '\n'.join([message('[Survival with full EVA + O2]', 'eva'),message('[Survival without EVA — suffocation check]', 'unsuited'),message('[Open the east wall — BREACH]', 'breach'),message('[Repair opening — five-second oxygen recovery]', 'seal'),message('[Creative safety]', 'safe'),message('Ordinary flames go out together. Torches stay as Spent Torches. Crouch to brace; airflow ends after two seconds. Q drops an item for the airflow check. Relight with flint and steel after recovery.'),message('Cold remains real and independent of suffocation. Use Creative safety if needed. The setup never resets after initialization.')])
def scripts():
    guard='execute unless score #built abcheck matches 1 run return 0'
    return {
    'load':'scoreboard objectives add abcheck dummy\nexecute unless score #built abcheck matches 1 run scoreboard players set #built abcheck 0',
    'tick':f'execute if score #built abcheck matches 0 as @a[limit=1] at @s run function {NS}:setup',
    'setup':'\n'.join(['execute unless score #built abcheck matches 0 run return 0','scoreboard players set #built abcheck -1','gamemode creative @s','gamerule doMobSpawning false','gamerule doDaylightCycle false','gamerule doWeatherCycle false','gamerule doFireTick false','gamerule keepInventory true','weather clear','time set noon','fd world preset default','fd world set phase 6 mid','fd world pause','fill -15 64 -15 15 64 15 minecraft:bedrock','fill -15 65 -15 15 90 15 minecraft:air','fill -4 65 -4 4 70 4 minecraft:glass','fill -3 65 -3 3 69 3 minecraft:air','setblock 0 65 -2 frozendawn:geothermal_core','setblock -2 65 0 minecraft:torch','setblock -3 67 0 minecraft:wall_torch[facing=east]','setblock 2 65 0 minecraft:campfire[lit=true]','setblock 2 65 2 minecraft:candle[lit=true]','setblock -2 65 2 minecraft:lantern','setblock 0 65 2 minecraft:furnace','item replace block 0 65 2 container.0 with minecraft:raw_iron 64','item replace block 0 65 2 container.1 with minecraft:coal 64','setblock -2 65 -2 minecraft:soul_torch','setworldspawn 0 65 0','spawnpoint @s 0 65 0','tp @s 0.5 65 0.5 -90 10','give @s minecraft:flint_and_steel','give @s minecraft:glass 16','give @s minecraft:cobblestone 16','give @s minecraft:cooked_beef 32','give @s patchouli:guide_book[patchouli:book="frozendawn:frozen_dawn_guide"]','fd world set phase 6 late','fd world pause','scoreboard players set #built abcheck 1',f'function {NS}:controls',message('READY: fresh sealed glass room with oxygen core. Read the new Atmospheric Breaches entry in the ORSA Field Manual.')]),
    'eva':'\n'.join([guard,'item replace entity @s armor.head with frozendawn:eva_helmet','item replace entity @s armor.chest with frozendawn:eva_chestplate','item replace entity @s armor.legs with frozendawn:eva_leggings','item replace entity @s armor.feet with frozendawn:eva_boots','item replace entity @s hotbar.8 with frozendawn:o2_tank_mk3','gamemode survival @s',message('Full EVA and a fresh tank for this disposable check. Drop an item, then open the breach. Watch O2 switch from ambient intake to tank use.')]),
    'unsuited':'\n'.join([guard,'item replace entity @s armor.head with minecraft:air','item replace entity @s armor.chest with minecraft:air','item replace entity @s armor.legs with minecraft:air','item replace entity @s armor.feet with minecraft:air','gamemode survival @s',message('Unsuited test: breach starts the existing ten-second suffocation buildup. Cold may also hurt. Use the safety button to end exposure.')]),
    'breach':'\n'.join([guard,'setblock 4 65 0 minecraft:air','setblock 4 66 0 minecraft:air']),
    'seal':'\n'.join([guard,'setblock 4 65 0 minecraft:glass','setblock 4 66 0 minecraft:glass',message('Seal repaired. Stay loaded for five seconds with the core, then relight spent torches, lantern and campfire with flint and steel.')]),
    'safe':'\n'.join([guard,'gamemode creative @s',message('Creative safety enabled. Scene and room-air history preserved.')]),
    'controls':controls()}
def prepare(source,dest):
    if dest.exists():raise SystemExit('Refusing to overwrite an existing save.')
    before=hashlib.sha256(source.read_bytes()).hexdigest();seed.TITLE=TITLE;seed.PACK=PACK
    raw=seed.metadata(source);dest.mkdir(parents=True);(dest/'level.dat').write_bytes(gzip.compress(raw));pack=dest/'datapacks'/PACK
    (pack/'pack.mcmeta').parent.mkdir(parents=True,exist_ok=True);(pack/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':48,'description':TITLE}})+'\n')
    for name,body in scripts().items():
        p=pack/f'data/{NS}/function/{name}.mcfunction';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body+'\n')
    for tag in ('load','tick'):
        p=pack/f'data/minecraft/tags/function/{tag}.json';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps({'values':[f'{NS}:{tag}']})+'\n')
    assert hashlib.sha256(source.read_bytes()).hexdigest()==before
    (dest/'preparation.json').write_text(json.dumps({'world':TITLE,'source_metadata':str(source.resolve()),'source_sha256':before,'copied':'fresh seed metadata only; no chunks, player inventory or mod saved data'},indent=2)+'\n')
    print(dest.resolve())
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--source',type=Path,required=True);p.add_argument('--destination',type=Path,required=True);a=p.parse_args();prepare(a.source,a.destination)

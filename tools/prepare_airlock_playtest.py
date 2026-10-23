#!/usr/bin/env python3
"""Prepare an isolated airlock lab from seed metadata, without copying saved history."""
import argparse,gzip,json,hashlib
from pathlib import Path
import prepare_ration_warmer_playtest as seed
TITLE='Airlock Check'; PACK='airlock-check';NS='airlock_check'
def tell(text,fn=None):
 value={'text':text,'color':'aqua'}
 if fn:value['clickEvent']={'action':'run_command','value':f'/function {NS}:{fn}'}
 return 'tellraw @s '+json.dumps(value)
def scripts():
 guard='execute unless score #built alcheck matches 1 run return 0'
 setup=['execute unless score #built alcheck matches 0 run return 0','scoreboard players set #built alcheck -1','gamemode creative @s','gamerule doMobSpawning false','gamerule doDaylightCycle false','gamerule doWeatherCycle false','gamerule doFireTick false','gamerule keepInventory true','weather clear','time set noon','fd world preset default','fd world set phase 6 mid','fd world pause','fill -12 64 -12 12 64 12 minecraft:bedrock','fill -12 65 -12 12 90 12 minecraft:air','fill -5 64 -3 1 68 3 minecraft:glass','fill -4 65 -2 0 67 2 minecraft:air','fill 1 64 -2 5 68 2 minecraft:glass','fill 2 65 -1 4 67 1 minecraft:air','setblock -3 65 -1 frozendawn:geothermal_core']
 for x in (1,5):
  setup += [f'setblock {x} 65 0 frozendawn:airlock_door[facing=east,half=lower]',f'setblock {x} 66 0 frozendawn:airlock_door[facing=east,half=upper]']
 setup+=['setblock 3 66 -2 frozendawn:airlock_controller[facing=south]','setblock 4 66 -2 frozendawn:manual_vent_valve[facing=south]','setblock 0 66 2 frozendawn:airlock_controller[facing=north]','setblock 6 65 1 frozendawn:airlock_controller[facing=north]','setblock 2 65 1 minecraft:torch','setblock 4 65 1 minecraft:soul_torch','setblock 3 65 1 minecraft:campfire[lit=true]','setblock -2 65 1 minecraft:torch','setblock -2 65 -2 minecraft:crafting_table','setworldspawn -1 65 0','spawnpoint @s -1 65 0','tp @s -0.5 65 0.5 -90 10','give @s patchouli:guide_book[patchouli:book="frozendawn:frozen_dawn_guide"]','give @s minecraft:flint_and_steel','give @s minecraft:iron_ingot 32','give @s minecraft:copper_ingot 16','give @s minecraft:redstone 16','give @s minecraft:glass 32','give @s frozendawn:o2_tank_mk3 4','give @s frozendawn:airlock_door 2','give @s frozendawn:airlock_controller','give @s frozendawn:manual_vent_valve','effect give @s minecraft:night_vision infinite 0 true','fd world set phase 6 late','fd world pause','scoreboard players set #built alcheck 1',f'function {NS}:controls',tell('READY: isolated injected QA scene. Base west, 27-cell chamber east, vacuum beyond. Core fills 40 O2 per second. Reserve starts empty. Use a canister on a panel for faster preparation.')]
 return {
 'load':'scoreboard objectives add alcheck dummy\nexecute unless score #built alcheck matches 1 run scoreboard players set #built alcheck 0',
 'tick':f'execute if score #built alcheck matches 0 as @a[limit=1] at @s run function {NS}:setup',
 'setup':'\n'.join(setup),
 'controls':'\n'.join([tell('[Full EVA + fresh tank]', 'eva'),tell('[Creative safety]', 'safe'),tell('[Disconnect Core: stored gas + canister check]', 'core_off'),tell('[Reconnect Core]', 'core_on'),tell('[Break chamber wall: real breach]', 'breach'),tell('[Repair chamber wall: no free refill]', 'repair'),tell('1. Enter from base through inner door; close it. Outer door should refuse while green.'),tell('2. Click controller: amber, hiss/pump, then red after four seconds. Inner refuses; outer opens. Close outer.'),tell('3. Charge with canister if needed; click to refill. Green after four seconds; inner opens, outer refuses.'),tell('4. Crouch-click panels for exact shared reserve, chamber air, cycle percent and loss. 27 cells = 2700 O2; normal recovery = 2430, loss = 270.'),tell('5. Valve: instant vent; torches stay spent, soul torch stays lit. With inner open, base vents too. EVA protects you.'),tell('6. Save/reload during a cycle to check persistence. Craft at base table: ORSA Heating -> Player-Built Airlocks, JEI R/+ transfer and shift-click output. No reset runs after setup.')]),
 'eva':'\n'.join([guard,'item replace entity @s armor.head with frozendawn:eva_helmet','item replace entity @s armor.chest with frozendawn:eva_chestplate','item replace entity @s armor.legs with frozendawn:eva_leggings','item replace entity @s armor.feet with frozendawn:eva_boots','item replace entity @s hotbar.8 with frozendawn:o2_tank_mk3','gamemode survival @s']),
 'safe':'\n'.join([guard,'gamemode creative @s']),
 'core_off':'\n'.join([guard,'setblock -3 65 -1 minecraft:air',tell('Core removed from base interior; no wall breached. Reserve remains finite. Use canisters on a panel.')]),
 'core_on':'\n'.join([guard,'setblock -3 65 -1 frozendawn:geothermal_core']),
 'breach':'\n'.join([guard,'setblock 3 66 2 minecraft:air']),
 'repair':'\n'.join([guard,'setblock 3 66 2 minecraft:glass',tell('Shell repaired. Chamber remains evacuated until you cycle it.')])}
def pack_only(dest,hooks=False):
 for name,body in scripts().items():
  p=dest/f'data/{NS}/function/{name}.mcfunction';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body+'\n')
 if hooks:
  (dest/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':48,'description':TITLE}})+'\n')
  for name in ('load','tick'):
   p=dest/f'data/minecraft/tags/function/{name}.json';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps({'values':[f'{NS}:{name}']})+'\n')
def prepare(source,dest):
 if dest.exists():raise SystemExit('Refusing to overwrite existing save.')
 before=hashlib.sha256(source.read_bytes()).hexdigest();seed.TITLE=TITLE;seed.PACK=PACK
 dest.mkdir(parents=True);(dest/'level.dat').write_bytes(gzip.compress(seed.metadata(source)))
 pack_only(dest/'datapacks'/PACK,True)
 assert hashlib.sha256(source.read_bytes()).hexdigest()==before
 (dest/'preparation.json').write_text(json.dumps({'world':TITLE,'source':str(source),'source_sha256':before,'copied':'seed metadata only; no chunks, player data or mod SavedData'},indent=2)+'\n')
 print(dest)
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--source',type=Path);p.add_argument('--destination',type=Path);p.add_argument('--pack-only',type=Path);a=p.parse_args();
 if a.pack_only:pack_only(a.pack_only)
 elif a.source and a.destination:prepare(a.source,a.destination)
 else:p.error('Use --pack-only or source and destination')

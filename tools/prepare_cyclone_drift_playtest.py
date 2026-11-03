#!/usr/bin/env python3
"""Creates a separate, guarded cyclone drift fixture from closed metadata only."""
import argparse,fcntl,gzip,io,json
from pathlib import Path
import prepare_blind_architect_playtest as nbt
from prepare_power_network_playtest import prepare,pack_only
TITLE='Cyclone Drift Check'
NS='cyclone_drift'
PACK='cyclone-drift-check'
GUARD='execute unless score #built cydrift matches 1 run return 0'
def tell(text):return 'tellraw @s '+json.dumps({'text':text,'color':'aqua'})
def row(*items):
 parts=[{'text':''}]
 for label,name in items:
  if len(parts)>1:parts.append({'text':' '})
  parts.append({'text':'['+label+']','color':'aqua','clickEvent':{'action':'run_command','value':'/function '+NS+':'+name}})
 return 'tellraw @s '+json.dumps(parts)
def scripts():
 setup=['execute unless score #built cydrift matches -1 run return 0','scoreboard players set #built cydrift -2','gamemode creative @s',
  'gamerule doMobSpawning false','gamerule doDaylightCycle false','gamerule doWeatherCycle false','gamerule doFireTick false',
  'gamerule keepInventory true','gamerule announceAdvancements false','gamerule sendCommandFeedback false','weather clear',
  'setworldspawn -6 65 -7','spawnpoint @s -6 65 -7']
 for x in range(-48,48,16):setup.append(f'fill {x} 64 -48 {x+15} 64 47 minecraft:smooth_stone_slab[type=bottom]')
 setup += ['fill -24 64 -32 8 64 31 minecraft:stone','fill -12 65 -24 -12 65 -10 minecraft:sandstone',
  'fill -12 65 -6 -12 67 8 minecraft:stone','fill -12 65 12 -12 70 26 minecraft:polished_blackstone',
  'setblock -10 65 -4 minecraft:torch','setblock -10 64 -1 minecraft:grass_block','setblock -10 65 -1 minecraft:dandelion',
  'setblock -10 65 2 minecraft:chest[facing=east]','item replace block -10 65 2 container.0 with minecraft:diamond 7',
  'setblock -9 65 5 minecraft:furnace[facing=east]','fill 10 64 -8 22 64 8 minecraft:stone',
  'fill 12 65 -6 12 67 6 minecraft:stone','fill 12 69 -8 22 69 8 minecraft:glass',
  'effect give @s minecraft:resistance infinite 4 true','effect give @s minecraft:saturation infinite 0 true',
  'item replace entity @s armor.head with frozendawn:eva_helmet','item replace entity @s armor.chest with frozendawn:eva_chestplate',
  'item replace entity @s armor.legs with frozendawn:eva_leggings','item replace entity @s armor.feet with frozendawn:eva_boots',
  'item replace entity @s hotbar.0 with minecraft:diamond_shovel','scoreboard players set #built cydrift 1',
  'function '+NS+':walls','function '+NS+':controls','return 1']
 read=[GUARD,'fd cyclone status']
 for holder,x,z in [('near',-11,0),('far',-8,0)]:
  read.append(f'scoreboard players set #{holder} cydrift 0')
  for layers in range(1,9):read.append(f'execute if block {x} 65 {z} minecraft:snow[layers={layers}] run scoreboard players set #{holder} cydrift {layers}')
 read+=['scoreboard players set #objects cydrift 0','execute if block -10 65 -4 minecraft:torch if block -10 65 -1 minecraft:dandelion if block -10 65 2 minecraft:chest if block -9 65 5 minecraft:furnace run scoreboard players set #objects cydrift 1',
  'tellraw @s [{"text":"Layers near/far: "},{"score":{"name":"#near","objective":"cydrift"}},{"text":" / "},{"score":{"name":"#far","objective":"cydrift"}},{"text":". Objects intact: "},{"score":{"name":"#objects","objective":"cydrift"}}]','return 1']
 return {
  'load':'scoreboard objectives add cydrift dummy\nexecute unless score #built cydrift matches -2..1 run scoreboard players set #built cydrift 0\nexecute if score #built cydrift matches 1 as @a run function '+NS+':controls',
  'bootstrap':'execute unless score #built cydrift matches 0 run return 0\nforceload add -48 -48 47 47\nscoreboard players set #built cydrift -1\nreturn 1',
  'tick':'execute if score #built cydrift matches 0 as @a[limit=1] run function '+NS+':bootstrap\nexecute if score #built cydrift matches -1 if loaded -48 65 -48 if loaded 47 65 47 as @a[limit=1] run function '+NS+':setup',
  'setup':'\n'.join(setup),
  'controls':'\n'.join([GUARD,tell('Drift lab: normal snowfall/freezing paused here. Wind east.'),row(('Start peak','start'),('Stop','end'),('Read','read')),row(('Walls','walls'),('Objects','objects'),('Roof','roof')),'return 1']),
  'start':'\n'.join([GUARD,'fd cyclone start catastrophic @s 0','fd cyclone stage peak 0.5',tell('Peak started. Watch the east side of the walls.'),'return 1']),
  'end':'\n'.join([GUARD,'fd cyclone end',tell('Stopped. Deposited snow stays.'),'return 1']),
  'walls':'\n'.join([GUARD,'tp @s -5.5 65 -7.5 90 15','return 1']),
  'objects':'\n'.join([GUARD,'tp @s -6.5 65 2.5 90 30','return 1']),
  'roof':'\n'.join([GUARD,'tp @s 16.5 65 2.5 90 25','return 1']),
  'read':'\n'.join(read)}
def prepare_world(source,destination):
 import tomllib
 config=Path(__file__).resolve().parents[1]/'run-lab/config/frozendawn-common.toml'
 assert tomllib.loads(config.read_text())['general']['pauseProgression'] is True,'Requires paused apocalypse progression'
 with (source.parent/'session.lock').open('rb') as lock:
  fcntl.lockf(lock,fcntl.LOCK_SH|fcntl.LOCK_NB)
  prepare(source,destination,title=TITLE,namespace=NS,pack=PACK,build_scripts=scripts,surface=64,structures=False)
 for filename in ['level.dat','data/frozendawn_apocalypse.dat']:
  p=destination/filename;s=io.BytesIO(gzip.decompress(p.read_bytes()));assert s.read(1)==b'\x0a';name=nbt.string(s);data=nbt.read(s,10)
  if filename=='level.dat':data['Data'][1].update({'DifficultyLocked':(1,1),'confirmedExperimentalSettings':(1,1),'DragonFight':(10,{}),'DayTime':(4,60*24000+6000),'SpawnX':(3,-6),'SpawnY':(3,65),'SpawnZ':(3,-7)})
  else:data['data'][1]['apocalypseTicks']=(4,60*24000)
  p.write_bytes(gzip.compress(b'\x0a'+nbt.utf(name)+nbt.write(10,data)))
 (destination/'cyclone-drift-lab.json').write_text(json.dumps({'world':TITLE,'isolation':'Only when explicitly launched via Gradle lab: pause ordinary snowfall, block freezing and vegetation decay. Production cyclone drift rules/budgets unchanged.'},indent=2)+'\n')
 p=destination/'preparation.json';report=json.loads(p.read_text());report.update({'slice':'cyclone snowdrifts','apocalypseTicks':60*24000,'fixtures':'1/3/6-high walls, torch/flower/chest with7diamonds/furnace, roofed control; Creative EVA; isolated ordinary snow/freezing/decay, production drift sampling unchanged'});p.write_text(json.dumps(report,indent=2)+'\n')
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source',type=Path);p.add_argument('--destination',type=Path);p.add_argument('--pack-only',type=Path);a=p.parse_args()
 if a.pack_only:pack_only(a.pack_only,title=TITLE,namespace=NS,build_scripts=scripts)
 elif a.source and a.destination:prepare_world(a.source,a.destination)
 else:p.error('Use --source/--destination or --pack-only')

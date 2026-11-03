#!/usr/bin/env python3
"""Fresh Cyclone Wind Check from closed metadata; never overwrite an existing replay."""
import argparse,fcntl,gzip,io,json
from pathlib import Path
import prepare_blind_architect_playtest as nbt
from prepare_power_network_playtest import prepare,pack_only
TITLE='Cyclone Wind Check'
NS='cyclone_wind'
PACK='cyclone-wind-check'
GUARD='execute unless score #built cywind matches 1 run return 0'
def tell(text):return 'tellraw @s '+json.dumps({'text':text,'color':'aqua'})
def row(*items):
 parts=[{'text':''}]
 for label,name in items:
  if len(parts)>1:parts.append({'text':' '})
  parts.append({'text':'['+label+']','color':'aqua','clickEvent':{'action':'run_command','value':'/function '+NS+':'+name}})
 return 'tellraw @s '+json.dumps(parts)
def scripts():
 setup=['execute unless score #built cywind matches -1 run return 0','scoreboard players set #built cywind -2',
  'gamemode creative @s','gamerule doMobSpawning false','gamerule doDaylightCycle false','gamerule doWeatherCycle false',
  'gamerule doFireTick false','gamerule keepInventory true','gamerule announceAdvancements false','gamerule sendCommandFeedback false',
  'weather clear','setworldspawn 16 65 0','spawnpoint @s 16 65 0']
 # Bottom slabs have ordinary ground friction and cannot support snow; surrounding snow remains natural.
 for x in range(-48,48,16):setup.append(f'fill {x} 64 -32 {x+15} 64 47 minecraft:smooth_stone_slab[type=bottom]')
 setup += ['fill -40 64 -1 40 64 1 minecraft:polished_andesite_slab[type=bottom]',
  'fill 0 65 10 0 68 16 minecraft:stone','fill 14 69 12 22 69 20 minecraft:stone',
  'fill 14 65 12 14 68 12 minecraft:stone','fill 14 65 20 14 68 20 minecraft:stone',
  'fill 22 65 12 22 68 12 minecraft:stone','fill 22 65 20 22 68 20 minecraft:stone',
  'fill 10 69 27 16 69 33 minecraft:glass',
  'setblock -2 65 -5 frozendawn:phase_barometer[facing=south]',
  'effect give @s minecraft:resistance infinite 4 true','effect give @s minecraft:saturation infinite 0 true',
  'item replace entity @s hotbar.0 with minecraft:air',
  'scoreboard players set #built cywind 1','function '+NS+':eva','function '+NS+':survival','function '+NS+':open','function '+NS+':controls','return 1']
 r={
  'load':'scoreboard objectives add cywind dummy\nexecute unless score #built cywind matches -2..1 run scoreboard players set #built cywind 0\nexecute if score #built cywind matches 1 as @a run function '+NS+':controls',
  'bootstrap':'execute unless score #built cywind matches 0 run return 0\nforceload add -48 -32 47 47\nscoreboard players set #built cywind -1\nreturn 1',
  'tick':'execute if score #built cywind matches 0 as @a[limit=1] run function '+NS+':bootstrap\nexecute if score #built cywind matches -1 if loaded -48 65 -32 if loaded 47 65 47 as @a[limit=1] run function '+NS+':setup',
  'setup':'\n'.join(setup),
  'controls':'\n'.join([GUARD,tell('Wind blows east. Face west to walk into it.'),row(('Moderate','moderate'),('Severe','severe'),('Catastrophic','catastrophic'),('Peak','peak'),('Stop','end')),row(('Open','open'),('Windbreak','windbreak'),('Roof','roof'),('More','more')),'return 1']),
  'more':'\n'.join([GUARD,row(('Glass roof','glass'),('Mobs','mobs')),row(('EVA','eva'),('Visor rig','visor'),('Creative','creative'),('Survival','survival')),row(('Read','read'),('Back','controls')),'return 1']),
  'peak':'\n'.join([GUARD,'fd cyclone stage peak 0.5','return 1']),
  'end':'\n'.join([GUARD,'fd cyclone end','return 1']),
  'read':'\n'.join([GUARD,'fd cyclone status','return 1']),
  'survival':'\n'.join([GUARD,'gamemode survival @s','return 1']),
  'creative':'\n'.join([GUARD,'gamemode creative @s','return 1']),
  'mobs':'\n'.join([GUARD,'execute if score #mobs cywind matches 1 run return 0','scoreboard players set #mobs cywind 1',
   'summon minecraft:sheep -24.5 64.5 25.5 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["wind_demo"]}',
   'summon minecraft:cow -24.5 64.5 29.5 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["wind_demo"]}',
   'tp @s -18.5 64.5 27.5 90 0',tell('Watch the sheep and cow drift east.'),'return 1'])}
 for name,x,y,z in [('open',16.5,64.5,.5),('windbreak',2.5,64.5,13.5),('roof',18.5,64.5,16.5),('glass',13.5,64.5,30.5)]:
  r[name]='\n'.join([GUARD,f'tp @s {x} {y} {z} 90 0','return 1'])
 for severity in ['moderate','severe','catastrophic']:
  r[severity]='\n'.join([GUARD,'fd cyclone start '+severity+' @s 0',tell(severity.capitalize()+' started.'),'return 1'])
 for name,head in [('eva','eva_helmet'),('visor','orsa_thermal_visor')]:
  r[name]='\n'.join([GUARD,'item replace entity @s armor.head with frozendawn:'+head,*['item replace entity @s armor.'+slot+' with frozendawn:eva_'+item for slot,item in [('chest','chestplate'),('legs','leggings'),('feet','boots')]],'return 1'])
 return r
def prepare_world(source,destination):
 config=Path(__file__).resolve().parents[1]/'run-lab/config/frozendawn-common.toml'
 import tomllib
 assert tomllib.loads(config.read_text())['general']['pauseProgression'] is True,'Requires paused apocalypse progression'
 with (source.parent/'session.lock').open('rb') as lock:
  fcntl.lockf(lock,fcntl.LOCK_SH|fcntl.LOCK_NB)
  prepare(source,destination,title=TITLE,namespace=NS,pack=PACK,build_scripts=scripts,surface=64,structures=False)
 for filename in ['level.dat','data/frozendawn_apocalypse.dat']:
  p=destination/filename;s=io.BytesIO(gzip.decompress(p.read_bytes()));assert s.read(1)==b'\x0a';name=nbt.string(s);data=nbt.read(s,10)
  if filename=='level.dat':data['Data'][1].update({'DifficultyLocked':(1,1),'confirmedExperimentalSettings':(1,1),'DragonFight':(10,{}),'DayTime':(4,60*24000+6000),'SpawnX':(3,16),'SpawnY':(3,65),'SpawnZ':(3,0)})
  else:data['data'][1]['apocalypseTicks']=(4,60*24000)
  p.write_bytes(gzip.compress(b'\x0a'+nbt.utf(name)+nbt.write(10,data)))
 p=destination/'preparation.json';report=json.loads(p.read_text());report.update({'slice':'wind movement','apocalypseTicks':60*24000,'fixtures':'ordinary-friction bottom-slab lane, upwind wall, open-sided stone and glass roofs, optional one-time no-AI mob demo; natural surrounding snow; Survival EVA/resistance, no active storm'});p.write_text(json.dumps(report,indent=2)+'\n')
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source',type=Path);p.add_argument('--destination',type=Path);p.add_argument('--pack-only',type=Path);a=p.parse_args()
 if a.pack_only:pack_only(a.pack_only,title=TITLE,namespace=NS,build_scripts=scripts)
 elif a.source and a.destination:prepare_world(a.source,a.destination)
 else:p.error('Use --source/--destination or --pack-only')

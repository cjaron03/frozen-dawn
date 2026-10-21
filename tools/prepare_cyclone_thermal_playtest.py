#!/usr/bin/env python3
"""Create a fresh cyclone thermal comparison from closed metadata; refuse existing destinations."""
import argparse,fcntl,gzip,io,json
from pathlib import Path
import prepare_blind_architect_playtest as nbt
from prepare_power_network_playtest import prepare,pack_only
TITLE='Cyclone Thermal Check'
NS='cyclone_thermal'
PACK='cyclone-thermal-check'
GUARD='execute unless score #built cythermal matches 1 run return 0'
ROOMS={'stone':(-12,'minecraft:stone'),'insulated':(0,'minecraft:white_wool'),'metal':(12,'minecraft:iron_block')}
def tell(text):return 'tellraw @s '+json.dumps({'text':text,'color':'aqua'})
def row(*items):
 parts=[{'text':''}]
 for label,name in items:
  if len(parts)>1:parts.append({'text':' '})
  parts.append({'text':'['+label+']','color':'aqua','clickEvent':{'action':'run_command','value':'/function '+NS+':'+name}})
 return 'tellraw @s '+json.dumps(parts)
def cells(x):
 items=[{'Slot':f'{i}b','id':'frozendawn:power_cell','count':1} for i in range(4)]
 return 'setblock '+str(x+1)+' 65 15 frozendawn:battery_bank{Items:['+','.join('{Slot:'+str(i)+'b,id:"frozendawn:power_cell",count:1,components:{"frozendawn:cell_joules":500000}}' for i in range(4))+']}'
def scripts():
 setup=['execute unless score #built cythermal matches -1 run return 0','scoreboard players set #built cythermal -2',
  'gamemode creative @s','gamerule doMobSpawning false','gamerule doDaylightCycle false','gamerule doWeatherCycle false',
  'gamerule doFireTick false','gamerule keepInventory true','gamerule announceAdvancements false','gamerule sendCommandFeedback false',
  'weather clear','setworldspawn 0 65 0','spawnpoint @s 0 65 0']
 for x in range(-32,33,16):setup.append(f'fill {x} 64 -16 {x+15} 64 31 minecraft:smooth_stone')
 for _,(x,material) in ROOMS.items():
  setup += [f'fill {x-2} 64 12 {x+2} 68 16 {material} hollow',
    f'setblock {x} 66 12 '+('frozendawn:insulated_glass' if material.endswith('wool') else 'minecraft:glass'),
    f'setblock {x} 65 15 frozendawn:resistive_heater[facing=north]{{PowerLimitWatts:10000}}',
    cells(x),f'setblock {x-1} 65 14 frozendawn:thermostat[facing=east]{{Target:20}}',
    f'setblock {x} 67 14 frozendawn:panel_light']
 setup += ['setblock -2 65 3 frozendawn:phase_barometer[facing=north]',
  'item replace entity @s hotbar.0 with minecraft:air','item replace entity @s hotbar.1 with frozendawn:conduit_binder',
  'item replace entity @s hotbar.2 with frozendawn:power_cell[frozendawn:cell_joules=500000]',
  'item replace entity @s hotbar.3 with frozendawn:orsa_thermal_visor',
  'scoreboard players set #built cythermal 1','function '+NS+':eva','function '+NS+':outside','function '+NS+':controls','return 1']
 result={
  'load':'scoreboard objectives add cythermal dummy\nexecute unless score #built cythermal matches -2..1 run scoreboard players set #built cythermal 0\nexecute if score #built cythermal matches 1 as @a run function '+NS+':controls',
  'bootstrap':'execute unless score #built cythermal matches 0 run return 0\nforceload add -32 -16 47 31\nscoreboard players set #built cythermal -1\nreturn 1',
  'tick':'execute if score #built cythermal matches 0 as @a[limit=1] run function '+NS+':bootstrap\nexecute if score #built cythermal matches -1 if loaded -32 65 -16 if loaded 47 65 31 as @a[limit=1] run function '+NS+':setup',
  'setup':'\n'.join(setup),
  'controls':'\n'.join([GUARD,tell('Cyclone cold: start, then Peak.'),row(('Moderate','moderate'),('Severe','severe'),('Catastrophic','catastrophic'),('Peak','peak'),('Stop','end')),
    row(('Outside','outside'),('Stone','stone'),('Insulated','insulated'),('Metal','metal')),row(('More','more')),'return 1']),
  'more':'\n'.join([GUARD,tell('Extra controls'),row(('EVA','eva'),('Heavy suit','heavy')),row(('Survival','survival'),('Creative','creative')),
    row(('Fresh cells','refill'),('Read','read'),('Back','controls')),'return 1']),
  'peak':'\n'.join([GUARD,'fd cyclone stage peak','return 1']),
  'end':'\n'.join([GUARD,'fd cyclone end','return 1']),
  'read':'\n'.join([GUARD,'fd cyclone status','return 1']),
  'outside':'\n'.join([GUARD,'execute positioned 0.5 0 0.5 positioned over motion_blocking_no_leaves run tp @s ~ ~ ~ 180 0','return 1']),
  'refill':'\n'.join([GUARD,*[f'item replace block {x+1} 65 15 container.{i} with frozendawn:power_cell[frozendawn:cell_joules=500000]' for x,_ in ROOMS.values() for i in range(4)],'return 1']),
  'survival':'\n'.join([GUARD,'gamemode survival @s','return 1']),
  'creative':'\n'.join([GUARD,'gamemode creative @s','return 1'])}
 for name,(x,_) in ROOMS.items():result[name]='\n'.join([GUARD,f'tp @s {x+.5} 65 13.5 0 0','return 1'])
 for name in ['moderate','severe','catastrophic']:
  result[name]='\n'.join([GUARD,'fd cyclone start '+name+' @s 90',tell(name.capitalize()+' started.'),'return 1'])
 for name,prefix in [('eva','eva'),('heavy','reinforced')]:
  result[name]='\n'.join([GUARD,*[f'item replace entity @s armor.{slot} with frozendawn:{prefix}_{item}' for slot,item in [('head','helmet'),('chest','chestplate'),('legs','leggings'),('feet','boots')]],'return 1'])
 return result

def block_long(x,y,z):
 value=((x&0x3ffffff)<<38)|((z&0x3ffffff)<<12)|(y&0xfff)
 return value-(1<<64) if value>=(1<<63) else value
def prepare_world(source,destination):
 with (source.parent/'session.lock').open('rb') as lock:
  fcntl.lockf(lock,fcntl.LOCK_SH|fcntl.LOCK_NB)
  prepare(source,destination,title=TITLE,namespace=NS,pack=PACK,build_scripts=scripts,surface=64,structures=False)
 for filename in ['level.dat','data/frozendawn_apocalypse.dat']:
  p=destination/filename;s=io.BytesIO(gzip.decompress(p.read_bytes()));assert s.read(1)==b'\x0a';name=nbt.string(s);data=nbt.read(s,10)
  if filename=='level.dat':
   data['Data'][1].update({'DifficultyLocked':(1,1),'confirmedExperimentalSettings':(1,1),'DragonFight':(10,{}),'DayTime':(4,60*24000+6000),'SpawnX':(3,0),'SpawnY':(3,65),'SpawnZ':(3,0)});version=data['Data'][1]['DataVersion']
  else:data['data'][1]['apocalypseTicks']=(4,60*24000)
  p.write_bytes(gzip.compress(b'\x0a'+nbt.utf(name)+nbt.write(10,data)))
 links=[]
 for x,_ in ROOMS.values():
  for dest in [(x,65,15),(x,67,14)]:links.append({'a':(4,block_long(x+1,65,15)),'b':(4,block_long(*dest))})
 network={'DataVersion':version,'data':(10,{'version':(3,1),'links':(9,(10,links))})}
 (destination/'data/frozendawn_power_network.dat').write_bytes(gzip.compress(b'\x0a'+nbt.utf('')+nbt.write(10,network)))
 config=Path(__file__).resolve().parents[1]/'run-lab/config/frozendawn-common.toml'
 import tomllib
 assert tomllib.loads(config.read_text())['general']['pauseProgression'] is True,'Replay needs paused apocalypse progression'
 p=destination/'preparation.json';report=json.loads(p.read_text());report.update({'slice':2,'apocalypseTicks':60*24000,'fixtures':'three sealed material comparison rooms, equal10kW RH-5 limits,20C targets,2MJ per room; Creative EVA; no storm until owner starts it'});p.write_text(json.dumps(report,indent=2)+'\n')
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source',type=Path);p.add_argument('--destination',type=Path);p.add_argument('--pack-only',type=Path);a=p.parse_args()
 if a.pack_only:pack_only(a.pack_only,title=TITLE,namespace=NS,build_scripts=scripts)
 elif a.source and a.destination:prepare_world(a.source,a.destination)
 else:p.error('Use --source/--destination or --pack-only')

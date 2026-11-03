#!/usr/bin/env python3
"""Fresh raised cyclone thunder platform; preserve all predecessor worlds."""
import argparse, fcntl, gzip, io, json
from pathlib import Path
import prepare_blind_architect_playtest as nbt
from prepare_power_network_playtest import prepare, pack_only
TITLE='Cyclone Thunder Check'
NS='cyclone_thunder'
PACK='cyclone-thunder-check'
GUARD='execute unless score #built cythunder matches 1 run return 0'
def tell(text): return 'tellraw @s '+json.dumps({'text':text,'color':'aqua'})
def row(*items):
 parts=[{'text':''}]
 for label,name in items:
  if len(parts)>1: parts.append({'text':' '})
  parts.append({'text':'['+label+']','color':'aqua','clickEvent':{'action':'run_command','value':'/function '+NS+':'+name}})
 return 'tellraw @s '+json.dumps(parts)
def scripts():
 setup=[GUARD.replace('1 run return 0','0 run return 0'),'scoreboard players set #built cythunder -1',
  'gamemode creative @s','gamerule doMobSpawning false','gamerule doDaylightCycle false','gamerule doWeatherCycle false',
  'gamerule keepInventory true','gamerule announceAdvancements false','gamerule sendCommandFeedback false',
  'time set noon','weather clear','fill -24 96 -24 24 96 24 minecraft:smooth_stone_slab[type=bottom]',
  'fill 10 96 -6 20 96 6 minecraft:stone','fill 10 97 -6 20 97 -6 minecraft:stone',
  'fill 10 97 6 20 97 6 minecraft:stone','fill 10 97 -6 10 100 6 minecraft:stone',
  'fill 10 101 -6 20 101 6 minecraft:glass','setworldspawn 0 97 0','spawnpoint @s 0 97 0',
  'effect give @s minecraft:resistance infinite 4 true','effect give @s minecraft:saturation infinite 0 true',
  'item replace entity @s armor.head with frozendawn:eva_helmet','item replace entity @s armor.chest with frozendawn:eva_chestplate',
  'item replace entity @s armor.legs with frozendawn:eva_leggings','item replace entity @s armor.feet with frozendawn:eva_boots',
  'scoreboard players set #built cythunder 1','function '+NS+':open','function '+NS+':controls','return 1']
 result={
  'load':'scoreboard objectives add cythunder dummy\nexecute unless score #built cythunder matches -1..1 run scoreboard players set #built cythunder 0\nexecute if score #built cythunder matches 1 as @a run function '+NS+':controls',
  'tick':'execute if score #built cythunder matches 0 as @a[limit=1] run function '+NS+':setup',
  'setup':'\n'.join(setup),
  'controls':'\n'.join([GUARD,tell('Thunder: peak 8-12s / 4-8s / 2-5s. Weather slider applies.'),
   row(('Moderate','moderate'),('Severe','severe'),('Catastrophic','catastrophic')),
   row(('Approach','approach'),('Fade','fade'),('Stop','stop'),('Open','open'),('Roof','roof'))]),
  'approach':'\n'.join([GUARD,'function '+NS+':open','fd cyclone start severe @s 0','fd cyclone stage bombogenesis 0',tell('Front approaching. Distant rolls every 50-70 seconds; occasional cloud glow.')]),
  'fade':'\n'.join([GUARD,'fd cyclone stage dissipation 0.35',tell('Fading: softer distant rolls every 30-50 seconds.')]),
  'stop':'\n'.join([GUARD,'fd cyclone end',tell('Stopped.')]),
  'open':'\n'.join([GUARD,'tp @s 0.5 96.5 0.5 90 -10']),
  'roof':'\n'.join([GUARD,'tp @s 15.5 97 0.5 90 -10'])}
 for severity in ('moderate','severe','catastrophic'):
  result[severity]='\n'.join([GUARD,'function '+NS+':open','fd cyclone start '+severity+' @s 0','fd cyclone stage peak 0.35',tell(severity.capitalize()+' peak.')])
 return result
def prepare_world(source,destination):
 import tomllib
 config=Path(__file__).resolve().parents[1]/'run-lab/config/frozendawn-common.toml'
 assert tomllib.loads(config.read_text())['general']['pauseProgression'] is True
 with (source.parent/'session.lock').open('rb') as lock:
  fcntl.lockf(lock,fcntl.LOCK_SH|fcntl.LOCK_NB)
  prepare(source,destination,title=TITLE,namespace=NS,pack=PACK,build_scripts=scripts,surface=64,structures=False)
 for filename in ('level.dat','data/frozendawn_apocalypse.dat'):
  p=destination/filename;s=io.BytesIO(gzip.decompress(p.read_bytes()));assert s.read(1)==b'\x0a';name=nbt.string(s);data=nbt.read(s,10)
  if filename=='level.dat':
   data['Data'][1].update({'DifficultyLocked':(1,1),'confirmedExperimentalSettings':(1,1),'DragonFight':(10,{}),
    'DayTime':(4,60*24000+6000),'SpawnX':(3,0),'SpawnY':(3,97),'SpawnZ':(3,0)})
  else:data['data'][1]['apocalypseTicks']=(4,60*24000)
  p.write_bytes(gzip.compress(b'\x0a'+nbt.utf(name)+nbt.write(10,data)))
 (destination/'cyclone-thunder-lab.json').write_text(json.dumps({'world':TITLE,'slice':'cosmetic thunder; raised snow-resistant floor; production weather unchanged'})+'\n')
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source',type=Path);p.add_argument('--destination',type=Path);p.add_argument('--pack-only',type=Path);a=p.parse_args()
 if a.pack_only:pack_only(a.pack_only,title=TITLE,namespace=NS,build_scripts=scripts)
 elif a.source and a.destination:prepare_world(a.source,a.destination)
 else:p.error('Use --source/--destination or --pack-only')

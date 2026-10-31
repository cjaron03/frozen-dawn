#!/usr/bin/env python3
"""Create a new normal-terrain phase 1 review, never replacing an existing save/config."""
import argparse,fcntl,gzip,io,json
from pathlib import Path
import prepare_blind_architect_playtest as nbt
from prepare_power_network_playtest import prepare,pack_only
TITLE='Phase 1 Weather Check'
NS='phase1_check'
GUARD='execute unless score #built phase1 matches 1 run return 0'
def tell(text,function=None):
 value={'text':text,'color':'aqua'}
 if function:value['clickEvent']={'action':'run_command','value':'/function '+NS+':'+function}
 return 'tellraw @s '+json.dumps(value)
def scripts():
 result={
 'load':'scoreboard objectives add phase1 dummy\nexecute unless score #built phase1 matches -2..1 run scoreboard players set #built phase1 0',
 'tick':'execute if score #built phase1 matches 0 as @a[limit=1] at @s run function '+NS+':setup',
 'setup':'\n'.join(['execute unless score #built phase1 matches 0 run return 0','scoreboard players set #built phase1 -2',
 'tag @s add phase1_lab','gamemode survival @s','gamerule doMobSpawning false','gamerule doDaylightCycle false',
 'gamerule doWeatherCycle false','gamerule doFireTick false','gamerule keepInventory true','gamerule announceAdvancements false',
 'gamerule sendCommandFeedback false','gamerule commandBlockOutput false','weather clear 1000000',
 'execute at @s positioned over motion_blocking_no_leaves run tp @s ~ ~2 ~ 180 0',
 'give @s frozendawn:phase_barometer','give @s frozendawn:thermal_heater','give @s minecraft:coal 16',
 'give @s minecraft:stone_bricks 64','give @s minecraft:glass 32','give @s minecraft:bread 32',
 'fdlab phase1 early','scoreboard players set #built phase1 1','function '+NS+':controls','return 1']),
 'controls':'\n'.join([GUARD,tell('PHASE 1 CHECK: normal terrain, Survival, held daylight/progression. Your other worlds are preserved.'),
 tell('[Early phase 1]','early'),tell('[Middle phase 1]','middle'),tell('[Late phase 1]','late'),
 tell('[Clear / quiet]','clear'),tell('[Rain / gusts]','rain'),tell('[Natural cycle]','natural'),
 tell('[Preview next weather transition in 3 seconds]','edge'),tell('[Read actual state]','read'),
 tell('[Warm breath comparison]','breath_warm'),tell('[Cold breath comparison]','breath_cold'),
 tell('Place the supplied barometer nearby. Open it for the forecast and hover WARNING for preparation advice.'),
 tell('Use the heater, coal and building materials for a real warm-shelter comparison. No arena is placed.'),'return 1']),
 'read':'\n'.join([GUARD,'fdlab phase1 status','return 1']),
 'edge':'\n'.join([GUARD,'fdlab phase1 edge','return 1']),
 'natural':'\n'.join([GUARD,'gamerule doWeatherCycle true','fdlab phase1 status','return 1'])}
 for name in ['early','middle','late']:
  result[name]='\n'.join([GUARD,'fdlab phase1 '+name,'return 1'])
 for name in ['clear','rain']:
  result[name]='\n'.join([GUARD,'gamerule doWeatherCycle false','weather '+name+' 1000000','fdlab phase1 status','return 1'])
 for name,phase in [('breath_warm','early'),('breath_cold','late')]:
  result[name]='\n'.join([GUARD,'gamerule doWeatherCycle false','weather clear 1000000','fdlab phase1 '+phase,
   tell('Selected '+name+'. Let the real temperature HUD settle; stay in Survival. No fake temperature or particles are injected.'),'return 1'])
 return result
def prepare_world(source,destination):
 root=Path(__file__).resolve().parents[1];config=root/'run-lab/config/frozendawn-common.toml'
 if config.exists():raise SystemExit('Refusing to replace existing lab config')
 with (source.parent/'session.lock').open('rb') as lock:
  fcntl.lockf(lock,fcntl.LOCK_SH|fcntl.LOCK_NB)
  prepare(source,destination,title=TITLE,namespace=NS,pack='phase1-check',build_scripts=scripts,terrain=True,structures=True)
 p=destination/'level.dat';stream=io.BytesIO(gzip.decompress(p.read_bytes()));assert stream.read(1)==b'\x0a';name=nbt.string(stream);tag=nbt.read(stream,10)
 tag['Data'][1].update({'GameType':(3,0),'initialized':(1,0),'DragonFight':(10,{}),'confirmedExperimentalSettings':(1,1),'DifficultyLocked':(1,1)})
 p.write_bytes(gzip.compress(b'\x0a'+nbt.utf(name)+nbt.write(10,tag)))
 config.parent.mkdir(parents=True,exist_ok=True);config.write_text('[general]\npauseProgression = true\n')
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source',type=Path);p.add_argument('--destination',type=Path);p.add_argument('--pack-only',type=Path);a=p.parse_args()
 if a.pack_only:pack_only(a.pack_only,title=TITLE,namespace=NS,build_scripts=scripts)
 elif a.source and a.destination:prepare_world(a.source,a.destination)
 else:p.error('Use --source/--destination or --pack-only')

#!/usr/bin/env python3
"""Fresh normal-terrain two-base world. Never overwrite an existing save."""
import argparse,copy,fcntl,gzip,hashlib,io,json,time
from pathlib import Path
import prepare_blind_architect_playtest as nbt
from prepare_power_network_playtest import pack_only
TITLE='Powered Airlock Check';NS='powered_airlock';PACK='powered-airlock-check'
def tell(text,action=None):
 d={'text':text,'color':'aqua'}
 if action:d['clickEvent']={'action':'run_command','value':'/function '+NS+':'+action}
 return 'tellraw @s '+json.dumps(d)
def scripts():
 guard='execute unless score #built pairlock matches 1 run return 0'
 setup=['execute unless score #built pairlock matches 0 run return 0','scoreboard players set #built pairlock -1','gamemode creative @s','gamerule doMobSpawning false','gamerule doDaylightCycle false','gamerule doWeatherCycle false','gamerule doFireTick false','gamerule keepInventory true','gamerule announceAdvancements false','fd world set phase 6 late','fd world pause','effect give @s minecraft:night_vision infinite 0 true','execute store result score #result pairlock run fdlab powered_airlock_setup','execute if score #result pairlock matches 1 run scoreboard players set #built pairlock 1','execute unless score #built pairlock matches 1 run return 0','give @s frozendawn:conduit_binder','give @s frozendawn:power_cell[frozendawn:cell_joules=500000] 4','give @s frozendawn:power_cell[frozendawn:cell_joules=2000] 4','give @s frozendawn:cell_dock 2','give @s frozendawn:o2_tank_mk3 4','give @s minecraft:raw_iron 64','function '+NS+':controls','return 1']
 return {'load':'scoreboard objectives add pairlock dummy\nexecute unless score #built pairlock matches -1..1 run scoreboard players set #built pairlock 0\nexecute if score #built pairlock matches 1 as @a run function '+NS+':controls','tick':'execute if score #built pairlock matches 0 as @a[limit=1] run function '+NS+':setup','setup':'\n'.join(setup),'controls':'\n'.join([guard,tell('POWERED AIRLOCK CHECK - natural cave + surface bases; Phase 6 late, Creative, Default difficulty.'),tell('[Underground cave base]','cave'),tell('[Surface base]','surface'),tell('[Test instructions]','steps'),'return 1']), 'cave':guard+'\nfdlab powered_airlock_cave\nreturn 1','surface':guard+'\nfdlab powered_airlock_surface\nreturn 1','steps':'\n'.join([guard,tell('Bind Core -> Bank -> Relay -> controller beside inner door. Core starts at20% power. Link monitor and lamp as well. Empty bank first.'),tell('Both doors closed: click controller to recover air, then charge with O2 canister and click to pressurize. Each direction needs4 powered seconds, 2kW (8kJ). Shift-click reads status. Both doors interlock during cycling.'),tell('Set Core0% and insert one2kJcell: cycle pauses after20paid ticks. Swap a full cell: resumes. No power cannot move gas; manual vent still works.'),tell('Brownout: empty bank, link cold RH-5/furnace/lamp. At10% Core the cycler gets2kW first. Below10% it waits. At sufficient power others resume. PIXIE shares highest life-support tier ahead of heat.'),tell('Repeat on surface. Save/reopen during a paused cycle: gas, reserve, links and charge persist; power grants rebuild. No setup/reset repeats.'),'return 1'])}
def prepare(source,dest):
 if dest.exists():raise SystemExit('Refusing to overwrite existing save')
 with (source.parent/'session.lock').open('r+b') as lock:
  fcntl.lockf(lock,fcntl.LOCK_EX|fcntl.LOCK_NB);raw=source.read_bytes();stream=io.BytesIO(gzip.decompress(raw));assert stream.read(1)==b'\x0a';nbt.string(stream);old=nbt.read(stream,10)['Data'][1]
 data={k:copy.deepcopy(old[k]) for k in nbt.METADATA_KEYS}
 gen=data['WorldGenSettings'][1];gen['seed']=(4,77371337);gen['generate_features']=(1,1);gen['bonus_chest']=(1,0)
 gen['dimensions'][1]['minecraft:overworld']=(10,{'type':(8,'minecraft:overworld'),'generator':(10,{'type':(8,'minecraft:noise'),'settings':(8,'minecraft:overworld'),'biome_source':(10,{'type':(8,'minecraft:multi_noise'),'preset':(8,'minecraft:overworld')})})})
 data.update({'LevelName':(8,TITLE),'GameType':(3,1),'allowCommands':(1,1),'Difficulty':(1,2),'DifficultyLocked':(1,1),'hardcore':(1,0),'initialized':(1,1),'Time':(4,0),'DayTime':(4,6000),'LastPlayed':(4,int(time.time()*1000)),'SpawnX':(3,0),'SpawnY':(3,100),'SpawnZ':(3,0),'WasModded':(1,1),'GameRules':(10,{'doMobSpawning':(8,'false'),'keepInventory':(8,'true'),'announceAdvancements':(8,'false')}),'DataPacks':(10,{'Enabled':(9,(8,['vanilla','mod_data','file/'+PACK])),'Disabled':(9,(8,[]))}),'enabled_features':(9,(8,['minecraft:vanilla']))})
 dest.mkdir(parents=True);(dest/'level.dat').write_bytes(gzip.compress(b'\x0a'+nbt.utf('')+nbt.write(10,{'Data':(10,data)})));(dest/'data').mkdir();(dest/'data/frozendawn_apocalypse.dat').write_bytes(gzip.compress(nbt.lab_state(old['DataVersion'][1])))
 pack_only(dest/'datapacks'/PACK,hooks=True,title=TITLE,namespace=NS,build_scripts=scripts)
 (dest/'preparation.json').write_text(json.dumps({'seed':77371337,'generator':'minecraft:noise / minecraft:overworld','copied_gameplay_files':0,'source_sha256':hashlib.sha256(raw).hexdigest(),'difficulty':'Default, locked','advancements':'filtered after mod_data','phase':'6 late, progression paused','fixture':'find existing cave before building, separate surface base'},indent=2)+'\n')
 print(dest.resolve())
if __name__=='__main__':
 a=argparse.ArgumentParser();a.add_argument('--source',type=Path);a.add_argument('--destination',type=Path);a.add_argument('--pack-only',type=Path);o=a.parse_args()
 if o.pack_only:pack_only(o.pack_only,title=TITLE,namespace=NS,build_scripts=scripts)
 elif o.source and o.destination:prepare(o.source,o.destination)
 else:a.error('Use source/destination or pack-only')

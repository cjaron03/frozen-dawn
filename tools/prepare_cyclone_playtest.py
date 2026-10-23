#!/usr/bin/env python3
"""Create a fresh Phase 5 storm replay from closed generator metadata; never overwrite a save."""
import argparse
import fcntl
import gzip
import io
import json
from pathlib import Path
import prepare_blind_architect_playtest as nbt
from prepare_power_network_playtest import prepare, pack_only
TITLE='Bomb Cyclone Check'
NS='cyclone_check'
PACK='cyclone-check'
GUARD='execute unless score #built cycheck matches 1 run return 0'
STAGES=['false_calm','bombogenesis','arrival','peak','dissipation','aftermath']

def tell(text,function=None):
    value={'text':text,'color':'aqua'}
    if function:value['clickEvent']={'action':'run_command','value':'/function '+NS+':'+function}
    return 'tellraw @s '+json.dumps(value)

def buttons(*items):
    parts=[{'text':''}]
    for label,function in items:
        if len(parts)>1:parts.append({'text':' ','color':'gray'})
        parts.append({'text':'['+label+']','color':'aqua',
                      'clickEvent':{'action':'run_command','value':'/function '+NS+':'+function}})
    return 'tellraw @s '+json.dumps(parts)

def scripts():
    setup=['execute unless score #built cycheck matches -1 run return 0','scoreboard players set #built cycheck -2',
           'gamemode creative @s','gamerule doMobSpawning false','gamerule doDaylightCycle false',
           'gamerule doWeatherCycle false','gamerule doFireTick false','gamerule keepInventory true',
           'gamerule announceAdvancements false','gamerule sendCommandFeedback false','gamerule commandBlockOutput false',
           'effect clear @s minecraft:night_vision']
    for x in range(-32,49,16):
        setup += [f'fill {x} 64 -48 {x+15} 64 47 minecraft:smooth_stone']
    for z in range(-40,41,8):
        setup += [f'setblock -4 65 {z} minecraft:white_concrete',f'setblock 4 65 {z} minecraft:white_concrete']
    setup += ['fill -9 65 7 -5 69 11 minecraft:stone hollow','fill -8 65 7 -6 67 7 minecraft:air',
              'setblock -7 68 9 minecraft:sea_lantern','setblock -2 65 3 frozendawn:phase_barometer[facing=north]',
              'setblock 6 65 3 frozendawn:uplink_console[facing=north]{Mode:1}',
              'setblock 12 65 3 frozendawn:uplink_dish',
              'setblock 8 65 3 frozendawn:battery_bank{Items:[{Slot:0b,id:"frozendawn:power_cell",count:1,components:{"frozendawn:cell_joules":500000}},{Slot:1b,id:"frozendawn:power_cell",count:1,components:{"frozendawn:cell_joules":500000}},{Slot:2b,id:"frozendawn:power_cell",count:1,components:{"frozendawn:cell_joules":500000}},{Slot:3b,id:"frozendawn:power_cell",count:1,components:{"frozendawn:cell_joules":500000}}]}',
              'setblock 8 65 5 frozendawn:battery_bank{Items:[{Slot:0b,id:"frozendawn:power_cell",count:1,components:{"frozendawn:cell_joules":500000}},{Slot:1b,id:"frozendawn:power_cell",count:1,components:{"frozendawn:cell_joules":500000}},{Slot:2b,id:"frozendawn:power_cell",count:1,components:{"frozendawn:cell_joules":500000}},{Slot:3b,id:"frozendawn:power_cell",count:1,components:{"frozendawn:cell_joules":500000}}]}',
              'item replace entity @s hotbar.0 with minecraft:air',
              'item replace entity @s hotbar.1 with frozendawn:blizzard_goggles',
              'item replace entity @s hotbar.2 with frozendawn:conduit_binder',
              'item replace entity @s hotbar.3 with frozendawn:power_cell[frozendawn:cell_joules=500000]',
              'item replace entity @s armor.head with frozendawn:eva_helmet',
              'item replace entity @s armor.chest with frozendawn:eva_chestplate',
              'item replace entity @s armor.legs with frozendawn:eva_leggings',
              'item replace entity @s armor.feet with frozendawn:eva_boots',
              'setworldspawn 0 65 0','spawnpoint @s 0 65 0','tp @s 0.5 65 0.5 180 0',
              'bossbar add cyclone_check:stage {"text":"Cyclone Check: baseline"}',
              'bossbar set cyclone_check:stage players @a','bossbar set cyclone_check:stage color blue',
              'bossbar set cyclone_check:stage max 6','bossbar set cyclone_check:stage value 0',
              'scoreboard players set #built cycheck 1','function '+NS+':controls','return 1']
    tick=['execute if score #built cycheck matches 0 as @a[limit=1] run function '+NS+':bootstrap',
          'execute if score #built cycheck matches -1 if loaded -32 65 -48 if loaded 63 65 47 as @a[limit=1] run function '+NS+':setup',
          'execute if score #built cycheck matches 1 run scoreboard players add #clock cycheck 1',
          'execute if score #built cycheck matches 1 if score #clock cycheck matches 20.. as @a[limit=1] run function '+NS+':display']
    display=[GUARD,'scoreboard players set #clock cycheck 0',
             'execute store result score #stage cycheck run fd cyclone stage-index',
             'bossbar set cyclone_check:stage players @a',
             'execute store result bossbar cyclone_check:stage value run scoreboard players get #stage cycheck']
    for i,name in enumerate(['Baseline / outside front']+[x.replace('_',' ').title() for x in STAGES]):
        display += [f'execute if score #stage cycheck matches {i} run bossbar set cyclone_check:stage name '+json.dumps({'text':'Cyclone Check: '+name})]
    result={
      'load':'\n'.join(['scoreboard objectives add cycheck dummy','execute unless score #built cycheck matches -2..1 run scoreboard players set #built cycheck 0',
                        'execute if score #built cycheck matches 1 as @a run function '+NS+':controls']),
      'bootstrap':'\n'.join(['execute unless score #built cycheck matches 0 run return 0','forceload add -32 -48 63 47','scoreboard players set #built cycheck -1','return 1']),
      'setup':'\n'.join(setup),'tick':'\n'.join(tick),'display':'\n'.join(display),
      'controls':'\n'.join([GUARD,
          tell('Cyclone: start a storm, then pick stages.'),
          buttons(('Moderate','moderate'),('Severe','severe'),('Catastrophic','catastrophic')),
          buttons(('Calm','false_calm'),('Buildup','bombogenesis'),('Arrival','arrival')),
          buttons(('Peak','peak'),('Fade','dissipation'),('After','aftermath')),
          buttons(('Stop','end'),('More','more')),'return 1']),
      'more':'\n'.join([GUARD,
          tell('Extra controls'),
          buttons(('Home','center'),('Downstream','downstream'),('Outside','outside')),
          buttons(('Night vision on','night_on'),('Off','night_off')),
          buttons(('Mob close','mob_close'),('Mob far','mob_far')),
          buttons(('Storm details','read'),('Back','controls')),'return 1']),
      'read':'\n'.join([GUARD,'fd cyclone status','fd cyclone plan','datapack list enabled','return 1']),
      'end':'\n'.join([GUARD,'fd cyclone end','return 1']),
      'night_on':'\n'.join([GUARD,'effect give @s minecraft:night_vision infinite 0 true','return 1']),
      'night_off':'\n'.join([GUARD,'effect clear @s minecraft:night_vision','return 1'])}
    for name,distance in [('mob_close',2),('mob_far',12)]:
        result[name]='\n'.join([GUARD,
            f'execute at @s run playsound minecraft:entity.zombie.ambient hostile @s ~ ~ ~-{distance} 1 1 0','return 1'])
    for sev in ['moderate','severe','catastrophic']:
        result[sev]='\n'.join([GUARD,'tp @s 0.5 65 0.5 180 0','fd cyclone start '+sev+' @s 90',tell(sev.capitalize()+' started. Look north.'),'return 1'])
    for stage in STAGES:
        result[stage]='\n'.join([GUARD,'fd cyclone stage '+stage+(' 0.5' if stage in ('false_calm','arrival','dissipation') else ''),'return 1'])
    for name,(x,z) in {'center':(.5,.5),'downstream':(.5,400.5),'outside':(901.5,.5)}.items():
        result[name]='\n'.join([GUARD,f'tp @s {x} 65 {z} 180 0','return 1'])
    return result

def block_long(x,y,z):return ((x&0x3ffffff)<<38)|((z&0x3ffffff)<<12)|(y&0xfff)
def prepare_world(source,destination):
    lab=Path(__file__).resolve().parents[1]/'run-lab'
    config=lab/'config/frozendawn-common.toml'
    if config.exists():
        import tomllib
        assert tomllib.loads(config.read_text())['general']['pauseProgression'] is True,'Replay requires paused progression'
    with (source.parent/'session.lock').open('rb') as lock:
        fcntl.lockf(lock,fcntl.LOCK_SH|fcntl.LOCK_NB)
        prepare(source,destination,title=TITLE,namespace=NS,pack=PACK,build_scripts=scripts,surface=64,structures=False)
    for filename in ('level.dat','data/frozendawn_apocalypse.dat'):
        f=destination/filename;stream=io.BytesIO(gzip.decompress(f.read_bytes()));assert stream.read(1)==b'\x0a';name=nbt.string(stream);data=nbt.read(stream,10)
        if filename=='level.dat':
            data['Data'][1].update({'DifficultyLocked':(1,1),'confirmedExperimentalSettings':(1,1),'DragonFight':(10,{}),
                'SpawnX':(3,0),'SpawnY':(3,65),'SpawnZ':(3,0),'DayTime':(4,60*24000+6000)})
            version=data['Data'][1]['DataVersion']
        else:data['data'][1]['apocalypseTicks']=(4,60*24000)
        f.write_bytes(gzip.compress(b'\x0a'+nbt.utf(name)+nbt.write(10,data)))
    links=[{'a':(4,block_long(6,65,3)),'b':(4,block_long(8,65,3))},{'a':(4,block_long(8,65,3)),'b':(4,block_long(8,65,5))}]
    network={'DataVersion':version,'data':(10,{'version':(3,1),'links':(9,(10,links))})}
    (destination/'data/frozendawn_power_network.dat').write_bytes(gzip.compress(b'\x0a'+nbt.utf('')+nbt.write(10,network)))
    report=destination/'preparation.json';meta=json.loads(report.read_text());meta.update({'slice':1,'apocalypseTicks':60*24000,'phase':'Phase 5, day 60; paused progression',
        'fixtures':'northbound view, local barometer, 4 MJ battery-powered Uplink Listen, marker lane, roofed shelter; no storm until owner starts it'})
    report.write_text(json.dumps(meta,indent=2)+'\n')
    if not config.exists():config.parent.mkdir(parents=True,exist_ok=True);config.write_text('[general]\npauseProgression = true\n')

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source',type=Path);p.add_argument('--destination',type=Path);p.add_argument('--pack-only',type=Path);a=p.parse_args()
    if a.pack_only:pack_only(a.pack_only,title=TITLE,namespace=NS,build_scripts=scripts)
    elif a.source and a.destination:prepare_world(a.source,a.destination)
    else:p.error('Use --source/--destination or --pack-only')

#!/usr/bin/env python3
"""Slice-one Headlamp Aim Check. Fresh metadata-only save; refuses existing worlds."""
import argparse
import fcntl
import gzip
import io
import json
import tomllib
from pathlib import Path
import prepare_blind_architect_playtest as nbt
from prepare_power_network_playtest import prepare, pack_only

TITLE='Headlamp Aim Check'
WATER_TITLE='Headlamp Water Check'
NS='headlamp_check'
PACK='headlamp-check'
GUARD='execute unless score #built hlamp matches 1 run return 0'

def tell(text,function=None):
    value={'text':text,'color':'aqua'}
    if function:value['clickEvent']={'action':'run_command','value':'/function '+NS+':'+function}
    return 'tellraw @s '+json.dumps(value)

def scripts():
    setup=['execute unless score #built hlamp matches -1 run return 0','scoreboard players set #built hlamp -2',
           'gamemode creative @s','gamerule doMobSpawning false','gamerule doDaylightCycle false',
           'gamerule doWeatherCycle false','gamerule doFireTick false','gamerule keepInventory true',
           'gamerule announceAdvancements false','gamerule sendCommandFeedback false','gamerule commandBlockOutput false',
           'time set midnight','weather clear','effect clear @s minecraft:night_vision']
    for x in range(-16,80,16):setup.append(f'fill {x} 64 -8 {x+15} 64 55 minecraft:smooth_stone')
    setup += ['fill -4 64 0 4 72 40 minecraft:deepslate_bricks hollow',
              'fill 20 64 0 32 72 28 minecraft:deepslate_bricks hollow',
              'fill 21 65 14 31 71 14 minecraft:deepslate_bricks',
              'fill 44 64 0 64 72 28 minecraft:deepslate_bricks hollow',
              'fill 54 65 1 54 71 20 minecraft:deepslate_bricks',
              'fill 18 64 36 30 88 48 minecraft:deepslate_bricks hollow',
              'fill 44 64 36 60 75 50 minecraft:deepslate_bricks hollow',
              'fill 45 65 37 59 74 49 minecraft:water']
    for z in range(6,39,2):setup.append(f'setblock -4 66 {z} minecraft:white_concrete')
    setup += ['setblock 0 66 40 minecraft:yellow_concrete','setblock 26 66 28 minecraft:yellow_concrete',
              'setblock 64 66 8 minecraft:yellow_concrete','setblock 24 88 42 minecraft:yellow_concrete',
              'item replace entity @s armor.head with frozendawn:eva_helmet',
              'item replace entity @s armor.chest with frozendawn:eva_chestplate',
              'item replace entity @s armor.legs with frozendawn:eva_leggings',
              'item replace entity @s armor.feet with frozendawn:eva_boots',
              'item replace entity @s hotbar.0 with minecraft:air',
              'item replace entity @s hotbar.1 with frozendawn:battery_lantern[minecraft:container=[{slot:0,item:{id:"frozendawn:power_cell",count:1,components:{"frozendawn:cell_joules":500000}}}],minecraft:max_stack_size=1]',
              'item replace entity @s hotbar.2 with minecraft:iron_pickaxe',
              'item replace entity @s hotbar.3 with minecraft:deepslate_bricks 64',
              'item replace entity @s hotbar.4 with minecraft:glass 32',
              'setworldspawn 0 65 5','spawnpoint @s 0 65 5','tp @s 0.5 65 5.5 0 0',
              'scoreboard players set #built hlamp 1','headlamp_prototype off','function '+NS+':controls','return 1']
    result={
      'load':'scoreboard objectives add hlamp dummy\nexecute unless score #built hlamp matches -2..1 run scoreboard players set #built hlamp 0\nexecute if score #built hlamp matches 1 as @a run function '+NS+':controls',
      'bootstrap':'\n'.join(['execute unless score #built hlamp matches 0 run return 0','forceload add -16 -16 79 63','scoreboard players set #built hlamp -1','return 1']),
      'tick':'\n'.join(['execute if score #built hlamp matches 0 as @a[limit=1] run function '+NS+':bootstrap',
                         'execute if score #built hlamp matches -1 if loaded -16 65 -8 if loaded 79 65 55 as @a[limit=1] run function '+NS+':setup']),
      'setup':'\n'.join(setup),
      'controls':'\n'.join([GUARD,tell('HEADLAMP SLICE 1: press H to toggle. Rebind Toggle Headlamp under Controls > Frozen Dawn. Free lab power; module and 50 W cell drain are slice 2.'),
            tell('[Headlamp ON]','on'),tell('[Headlamp OFF]','off'),tell('[Long tunnel]','tunnel'),
            tell('[Solid wall]','wall'),tell('[Behind wall]','behind'),tell('[Corner]','corner'),
            tell('[Vertical aim]','vertical'),tell('[Underwater]','water'),tell('[Read status]','read'),
            tell('Move and turn normally. Focused forward line: light 15 at 4 blocks, fading to 9 at 16 blocks. No head-centered glow; normal Minecraft light spill remains.'),
            tell('For overlap: select Lantern in slot 2, press F, then return to empty slot 1. The real Lantern drains its own cell. F again stows it.'),
            tell('Reopen this menu with /function headlamp_check:controls. Prototype starts OFF again after a world restart.'),'return 1']),
      'on':'\n'.join([GUARD,'headlamp_prototype on','return 1']),
      'off':'\n'.join([GUARD,'headlamp_prototype off','return 1']),
      'read':'\n'.join([GUARD,'headlamp_prototype status','return 1'])}
    views={'tunnel':((.5,65,5.5),0,0,'White marks are 2 blocks apart. Look along the tunnel, turn, walk and sprint. Compare ON/OFF.'),
           'wall':((26.5,65,7.5),0,0,'A complete solid wall is ahead at z=14. Direct projection must stop here.'),
           'behind':((26.5,65,20.5),180,0,'You are behind the wall. Move back with the Solid wall button; normal light cannot pass through the solid partition.'),
           'corner':((48.5,65,8.5),-90,0,'Wall ahead at x=54. Walk south to z>20 and around it. Sources must not project through it; ordinary light may spill around the opening.'),
           'vertical':((24.5,65,41.5),0,-70,'Look straight up, down and sideways; Creative flight allows a vertical sweep.'),
           'water':((52.5,68,42.5),0,0,'Swim and turn underwater. Toggle OFF to check cleanup. Then compare with an ON offhand Lantern; water must stay intact.')}
    for name,(p,yaw,pitch,hint) in views.items():result[name]='\n'.join([GUARD,'tp @s '+' '.join(map(str,p))+f' {yaw} {pitch}',tell(hint),tell('[Lab menu]','controls'),'return 1'])
    return result

def prepare_world(source,destination,water_check=False):
    title=WATER_TITLE if water_check else TITLE
    lab=Path(__file__).resolve().parents[1]/'run-lab'
    config=lab/'config/frozendawn-common.toml'
    local=destination.resolve().parent==(lab/'saves').resolve()
    if local and config.exists():
        assert water_check,'Fresh Headlamp checkout should not have a pre-existing lab config'
        assert tomllib.loads(config.read_text())['general']['pauseProgression'] is True,'Water retest requires paused phase progression'
    with (source.parent/'session.lock').open('rb') as lock:
        fcntl.lockf(lock,fcntl.LOCK_SH|fcntl.LOCK_NB)
        prepare(source,destination,title=title,namespace=NS,pack=PACK,build_scripts=scripts,surface=64,structures=False)
    ticks=0 if water_check else 120*24000*9//10
    for filename in ('level.dat','data/frozendawn_apocalypse.dat'):
        f=destination/filename;stream=io.BytesIO(gzip.decompress(f.read_bytes()));assert stream.read(1)==b'\x0a';name=nbt.string(stream);root=nbt.read(stream,10)
        if filename=='level.dat':root['Data'][1].update({'DifficultyLocked':(1,1),'confirmedExperimentalSettings':(1,1),'DragonFight':(10,{}),'SpawnX':(3,0),'SpawnY':(3,65),'SpawnZ':(3,5),'DayTime':(4,ticks+18000)})
        else:root['data'][1]['apocalypseTicks']=(4,ticks)
        f.write_bytes(gzip.compress(b'\x0a'+nbt.utf(name)+nbt.write(10,root)))
    report=destination/'preparation.json';v=json.loads(report.read_text());v.update({'apocalypseTicks':ticks,'water_retest':water_check,'fixtures':'long dark tunnel, solid partition, corner, vertical shaft, water chamber; prototype initially OFF'});report.write_text(json.dumps(v,indent=2)+'\n')
    if local and not config.exists():
        config.parent.mkdir(parents=True,exist_ok=True);config.write_text('[general]\npauseProgression = true\n')

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source',type=Path);p.add_argument('--destination',type=Path);p.add_argument('--pack-only',type=Path);p.add_argument('--water-check',action='store_true');a=p.parse_args()
    if a.pack_only:pack_only(a.pack_only,title=TITLE,namespace=NS,build_scripts=scripts)
    elif a.source and a.destination:prepare_world(a.source,a.destination,water_check=a.water_check)
    else:p.error('Use --source/--destination or --pack-only')

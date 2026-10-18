#!/usr/bin/env python3
"""Create a separate Battery Lantern cave lab from closed metadata; never overwrite a save."""
import argparse
import fcntl
import gzip
import io
import json
from pathlib import Path
import prepare_blind_architect_playtest as nbt
from prepare_power_network_playtest import prepare, pack_only

TITLE='Battery Lantern Check'
NS='battery_lantern_check'
PACK='battery-lantern-check'
GUARD='execute unless score #built blantern matches 1 run return 0'

def tell(text,function=None):
    value={'text':text,'color':'aqua'}
    if function:value['clickEvent']={'action':'run_command','value':'/function '+NS+':'+function}
    return 'tellraw @s '+json.dumps(value)

def scripts():
    setup=['execute unless score #built blantern matches -1 run return 0','scoreboard players set #built blantern -2',
           'gamemode creative @s','gamerule doMobSpawning false','gamerule doDaylightCycle false',
           'gamerule doWeatherCycle false','gamerule doFireTick false','gamerule keepInventory true',
           'gamerule announceAdvancements false','gamerule sendCommandFeedback false','gamerule commandBlockOutput false',
           'time set midnight','weather clear','effect clear @s minecraft:night_vision']
    for x in range(-16,64,16):setup.append(f'fill {x} 64 -8 {x+15} 64 48 minecraft:smooth_stone')
    setup += ['fill -4 64 0 4 70 44 minecraft:deepslate_bricks hollow',
              'fill 2 65 12 3 68 14 minecraft:deepslate_bricks','fill -3 65 27 -2 68 29 minecraft:deepslate_bricks',
              'setblock 0 65 3 frozendawn:battery_lantern[facing=up,enabled=false,lit=false]{Enabled:0b,Cell:{id:"frozendawn:power_cell",count:1,components:{"frozendawn:cell_joules":500}}}',
              'setblock -2 65 3 minecraft:crafting_table',
              'setblock -3 65 3 minecraft:chest{Items:[{Slot:0b,id:"minecraft:iron_ingot",count:16},{Slot:1b,id:"minecraft:glass",count:32},{Slot:2b,id:"minecraft:redstone",count:16},{Slot:3b,id:"minecraft:copper_ingot",count:16},{Slot:4b,id:"frozendawn:power_cell",count:1,components:{"frozendawn:cell_joules":500000}},{Slot:5b,id:"frozendawn:power_cell",count:1,components:{"frozendawn:cell_joules":500}}]}',
              'fill 18 65 0 18 68 2 minecraft:iron_block','fill 15 69 0 18 69 2 minecraft:iron_block',
              'fill 28 64 0 38 70 12 minecraft:deepslate_bricks hollow','fill 30 64 2 36 66 6 minecraft:glass hollow',
              'fill 31 65 3 35 66 5 minecraft:water',
              'setblock 33 65 4 frozendawn:battery_lantern[facing=up,waterlogged=true,enabled=false,lit=false]{Enabled:0b}',
              'item replace entity @s hotbar.0 with frozendawn:battery_lantern 16',
              'item replace entity @s hotbar.1 with frozendawn:power_cell[frozendawn:cell_joules=500]',
              'item replace entity @s hotbar.2 with frozendawn:power_cell[frozendawn:cell_joules=500000]',
              'item replace entity @s hotbar.3 with minecraft:iron_block 32',
              'item replace entity @s hotbar.4 with minecraft:iron_pickaxe',
              'item replace entity @s hotbar.5 with frozendawn:conduit_binder',
              'setworldspawn 0 65 7','spawnpoint @s 0 65 7','tp @s 0.5 65 7.5 180 12',
              'scoreboard players set #built blantern 1','function '+NS+':controls','return 1']
    result={
      'load':'scoreboard objectives add blantern dummy\nexecute unless score #built blantern matches -2..1 run scoreboard players set #built blantern 0\nexecute if score #built blantern matches 1 as @a run function '+NS+':controls',
      'bootstrap':'\n'.join(['execute unless score #built blantern matches 0 run return 0','forceload add -16 -16 63 63','scoreboard players set #built blantern -1','return 1']),
      'tick':'\n'.join(['execute if score #built blantern matches 0 as @a[limit=1] run function '+NS+':bootstrap',
                         'execute if score #built blantern matches -1 if loaded -16 65 -8 if loaded 63 65 48 as @a[limit=1] run function '+NS+':setup']),
      'setup':'\n'.join(setup),
      'controls':'\n'.join([GUARD,tell('BATTERY LANTERN: light 14 / 50 W. Default difficulty locked, paused late vacuum; no grid, night vision or oxygen needed.'),
                 tell('First: empty-hand turn on the lantern ahead. Its 500 J cell lasts about 10 seconds. Insert the full cell to resume.'),
                 tell('[Dark cave / charge test]','cave'),tell('[Six-face mounts]','mounts'),tell('[Waterproof test]','water'),
                 tell('[Survival pickup mode]','survival'),tell('[Creative mode]','creative'),tell('[Read diagnostics]','read'),
                 tell('Empty hand toggles; cell inserts/swaps; Shift-empty removes. F: offhand light (50 W). Shift-use in air: toggle. Survival hand breaking picks up the complete lantern.'),'return 1'])}
    views={
      'cave':((.5,65,7.5),['500 J = 10 seconds; full cell = 2h46m40s. Toggle off to pause drain. Pick up, carry, replace and save/reopen to verify charge and switch state.',
                            'The dark corridor extends south for 40 blocks. Place the lamp or press F to hold it offhand; both light at 50 W when ON. Other inventory slots do not light or drain.']),
      'mounts':((16.5,65,5.5),['Floor, ceiling, all four iron-column sides. Place a cell, turn on, then remove support in survival: one complete charged lantern should drop.']),
      'water':((33.5,65,9.5),['Insert a cell into the underwater lantern at 33,65,4, then empty-hand toggle. It should light normally. Survival break/support removal must preserve its cell and the water.'])}
    for name,(p,lines) in views.items():result[name]='\n'.join([GUARD,'tp @s '+' '.join(map(str,p))+' 180 12',*(tell(t) for t in lines),tell('[Lab menu]','controls'),'return 1'])
    result['survival']='\n'.join([GUARD,'gamemode survival @s','effect give @s minecraft:resistance infinite 4 true',tell('Survival pickup enabled. Lab damage resistance protects this vacuum test. Break the lantern by hand; collect and replace it. Creative breaking normally deletes blocks.'),'return 1'])
    result['creative']='\n'.join([GUARD,'gamemode creative @s','effect clear @s minecraft:resistance',tell('Creative mode enabled. Use survival mode for actual pickup drops.'),'return 1'])
    reads=[GUARD, 'tellraw @s '+json.dumps([{'text':'Offhand item: '},{'nbt':'Inventory[{Slot:-106b}]','entity':'@s'}]),
           'execute at @s if block ~ ~1 ~ frozendawn:held_lantern_light run '+tell('Offhand light at head: light 14'),
           'execute at @s if block ~ ~ ~ frozendawn:held_lantern_light run '+tell('Offhand light at feet: light 14')]
    for x,y,z,label in [(0,65,3,'Cave'),(33,65,4,'Water')]:
      p=f'{x} {y} {z}'
      reads.append('tellraw @s '+json.dumps([{'text':label+' cell / enabled / fractional credit: '},{'nbt':'Cell','block':p},{'text':' / '},{'nbt':'Enabled','block':p},{'text':' / '},{'nbt':'Credit','block':p}]))
      for enabled in ('true','false'):
       for lit in ('true','false'):reads.append(f'execute if block {p} frozendawn:battery_lantern[enabled={enabled},lit={lit}] run '+tell(f'{label}: enabled={enabled}, lit={lit}'))
    result['read']='\n'.join([*reads,'fd world thermal','fd world oxygen','return 1'])
    return result

def prepare_world(source,destination):
    with (source.parent/'session.lock').open('rb') as lock:
        fcntl.lockf(lock,fcntl.LOCK_SH|fcntl.LOCK_NB)
        prepare(source,destination,title=TITLE,namespace=NS,pack=PACK,build_scripts=scripts,surface=64,structures=False)
    ticks=120*24000*9//10
    for filename in ('level.dat','data/frozendawn_apocalypse.dat'):
        f=destination/filename;stream=io.BytesIO(gzip.decompress(f.read_bytes()));assert stream.read(1)==b'\x0a';name=nbt.string(stream);root=nbt.read(stream,10)
        if filename=='level.dat':root['Data'][1].update({'DifficultyLocked':(1,1),'confirmedExperimentalSettings':(1,1),'DragonFight':(10,{}),'SpawnX':(3,0),'SpawnY':(3,65),'SpawnZ':(3,7),'DayTime':(4,ticks+18000)})
        else:root['data'][1]['apocalypseTicks']=(4,ticks)
        f.write_bytes(gzip.compress(b'\x0a'+nbt.utf(name)+nbt.write(10,root)))
    report=destination/'preparation.json';v=json.loads(report.read_text());v.update({'apocalypseTicks':ticks,'phase':'paused late vacuum','fixtures':'dark cave with 500 J lantern initially off; six mounts; water pool; crafting supplies; survival pickup control'});report.write_text(json.dumps(v,indent=2)+'\n')
    lab=Path(__file__).resolve().parents[1]/'run-lab'
    if destination.resolve().parent==(lab/'saves').resolve():
        config=lab/'config/frozendawn-common.toml'
        if config.exists():
            text=config.read_text();assert text.count('pauseProgression = ')==1;config.write_text(text.replace('pauseProgression = false','pauseProgression = true'))
        else:config.parent.mkdir(parents=True,exist_ok=True);config.write_text('[general]\npauseProgression = true\n')

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source',type=Path);p.add_argument('--destination',type=Path);p.add_argument('--pack-only',type=Path);a=p.parse_args()
    if a.pack_only:pack_only(a.pack_only,title=TITLE,namespace=NS,build_scripts=scripts)
    elif a.source and a.destination:prepare_world(a.source,a.destination)
    else:p.error('Use --source/--destination or --pack-only')

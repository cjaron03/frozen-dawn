#!/usr/bin/env python3
"""Prepare a fresh Work Lamp lab from generator metadata only; never overwrite a save."""
import argparse
import fcntl
import gzip
import io
import json
from pathlib import Path

import prepare_blind_architect_playtest as nbt
from prepare_hearthstone_playtest import as_long
from prepare_power_network_playtest import prepare, pack_only

TITLE = 'Work Lamp Check'
NS = 'work_lamp_check'
PACK = 'work-lamp-check'
GUARD = 'execute unless score #built wlamp matches 1 run return 0'


def tell(text, function=None):
    value = {'text': text, 'color': 'aqua'}
    if function:
        value['clickEvent'] = {'action': 'run_command', 'value': '/function '+NS+':'+function}
    return 'tellraw @s '+json.dumps(value)


def links():
    return [((-28,65,-4),(-16,66,-4)), ((-16,66,-4),(0,66,-4)), ((0,66,-4),(0,66,0)),
            ((0,66,0),(0,66,1)), ((0,66,1),(2,69,4)), ((0,66,1),(2,66,1)),
            ((0,66,-4),(16,66,-4)), ((16,66,-4),(16,66,0)),
            ((30,65,3),(32,66,1)), ((32,66,1),(34,66,1))]


def scripts():
    setup = ['execute unless score #built wlamp matches -1 run return 0',
             'scoreboard players set #built wlamp -2', 'gamemode creative @s',
             'gamerule doMobSpawning false', 'gamerule doDaylightCycle false',
             'gamerule doWeatherCycle false', 'gamerule doFireTick false',
             'gamerule keepInventory true', 'gamerule announceAdvancements false',
             'gamerule sendCommandFeedback false', 'gamerule commandBlockOutput false',
             'time set midnight', 'weather clear']
    for x in range(-32, 64, 16):
        setup += [f'fill {x} 64 -8 {x+15} 64 12 minecraft:smooth_stone']
    setup += ['setblock -28 65 -4 frozendawn:geothermal_core{HeatShare:0,PowerShare:1,HeatBalance:0d}',
              'setblock -16 65 -4 minecraft:iron_block', 'setblock -16 66 -4 frozendawn:power_relay',
              'setblock 0 65 -4 minecraft:iron_block', 'setblock 0 66 -4 frozendawn:power_relay',
              'setblock 16 65 -4 minecraft:iron_block', 'setblock 16 66 -4 frozendawn:power_relay']
    for x in (0,16,32):
        setup += [f'fill {x-4} 64 0 {x+4} 70 8 minecraft:black_concrete hollow',
                  f'setblock {x-2} 66 1 frozendawn:thermostat[facing=south]{{Target:20}}']
    setup += ['setblock 0 66 0 frozendawn:bulkhead_feedthrough',
              'setblock 0 66 1 frozendawn:work_lamp[facing=south,enabled=true,lit=false]',
              'setblock 2 69 4 frozendawn:work_lamp[facing=down,enabled=false,lit=false]',
              'setblock 2 66 1 frozendawn:power_monitor[facing=south]',
              'setblock 16 66 0 frozendawn:panel_light',
              'setblock 30 65 3 frozendawn:cell_dock{Items:[{Slot:0b,id:"frozendawn:power_cell",count:1,components:{"frozendawn:cell_joules":500}}]}',
              'setblock 32 66 1 frozendawn:work_lamp[facing=south,enabled=false,lit=false]',
              'setblock 34 66 1 frozendawn:power_monitor[facing=south]',
              'fill 47 65 2 47 68 4 minecraft:iron_block',
              'fill 43 69 2 46 69 4 minecraft:iron_block',
              'setblock 43 65 0 minecraft:crafting_table',
              'setblock 44 65 0 minecraft:chest{Items:[{Slot:0b,id:"minecraft:iron_ingot",count:16},{Slot:1b,id:"minecraft:glass",count:16},{Slot:2b,id:"minecraft:redstone",count:16},{Slot:3b,id:"minecraft:copper_ingot",count:16}]}',
              'item replace entity @s hotbar.0 with frozendawn:conduit_binder',
              'item replace entity @s hotbar.1 with frozendawn:work_lamp 16',
              'item replace entity @s hotbar.2 with minecraft:iron_pickaxe',
              'item replace entity @s hotbar.3 with frozendawn:power_cell[frozendawn:cell_joules=500]',
              'item replace entity @s hotbar.4 with frozendawn:power_cell[frozendawn:cell_joules=500000]',
              'item replace entity @s hotbar.5 with frozendawn:cell_dock 4',
              'item replace entity @s hotbar.6 with minecraft:iron_block 32',
              'setworldspawn 0 65 4', 'spawnpoint @s 0 65 4', 'tp @s 0.5 65 4.5 180 5',
              'scoreboard players set #built wlamp 1', 'function '+NS+':controls', 'return 1']
    result = {
        'load': 'scoreboard objectives add wlamp dummy\nexecute unless score #built wlamp matches -2..1 run scoreboard players set #built wlamp 0\nexecute if score #built wlamp matches 1 as @a run function '+NS+':controls',
        'bootstrap': '\n'.join(['execute unless score #built wlamp matches 0 run return 0',
                               'forceload add -32 -16 63 15', 'scoreboard players set #built wlamp -1', 'return 1']),
        'tick': '\n'.join(['execute if score #built wlamp matches 0 as @a[limit=1] run function '+NS+':bootstrap',
                          'execute if score #built wlamp matches -1 if loaded -32 65 -8 if loaded 63 65 12 as @a[limit=1] run function '+NS+':setup']),
        'setup': '\n'.join(setup),
        'controls': '\n'.join([GUARD,
            tell('WORK LAMP CHECK: Creative / Default, paused late vacuum. No night vision. Work Lamp 50 W / light 12; Panel reference 100 W / light 15.'),
            tell('[Grid Work Lamps]', 'grid'), tell('[Panel reference]', 'panel'),
            tell('[Isolated Cell Dock]', 'cell'), tell('[Mounting / crafting]', 'mounts'),
            tell('[Grid Core]', 'core'), tell('[Read diagnostics]', 'read'), 'return 1'])}
    lamp_reads=[]
    for pos, label in (((0,66,1),'Grid wall'),((2,69,4),'Grid ceiling'),((32,66,1),'Cell wall')):
        for enabled in ('true','false'):
            for lit in ('true','false'):
                lamp_reads.append('execute if block '+' '.join(map(str,pos))+
                    f' frozendawn:work_lamp[enabled={enabled},lit={lit}] run '+
                    tell(f'{label}: enabled={enabled}, lit={lit}'))
    result['read']='\n'.join([GUARD,*lamp_reads,'fd world thermal','fd world oxygen',
                               'data get block 30 65 3','data get block -28 65 -4','return 1'])
    views = {
        'grid': ((.5,65,4.5), ['Wall lamp ahead, ceiling lamp above-right. Empty hand toggles. Binder selects/links; Shift-Binder clears. Monitor on right reports live power.',
                             'Ceiling lamp starts off: turn it on. Disconnect/reconnect either lamp to check power loss. Save/reopen to check switches, orientation and links.']),
        'panel': ((16.5,65,4.5), ['Identical room, legacy Panel Light at light 15 / 100 W. Compare brightness with the 50 W Work Lamp room.']),
        'cell': ((32.5,65,4.5), ['Work Lamp starts OFF so the installed 500 J cell stays charged. Empty-hand turn on: about 10 seconds until depletion.',
                               'Dock is inside-left. Swap the supplied full cell: light should resume without rebinding. Toggle off and confirm charge stops falling. Save/reopen too.']),
        'mounts': ((44.5,65,6.5), ['Use the iron column to test all four wall sides and the overhead plate for ceiling mounting. Remove the supporting block to drop a lamp.',
                                 'Spare docks and cells let you bind these manually. Crafting supplies are in the chest beside the table. Empty hotbar slots 8/9 are available for toggling.']),
        'core': ((-27.5,65,-1.5), ['Core is at 1% power: 200 W. Both Work Lamps plus the Panel use exactly 200 W when on.',
                               'Set power to 0% to cut grid power, then restore 1%. Cell Dock room stays independent. Core heat is zero; its oxygen range does not reach the dark rooms.'])}
    for name, (position, lines) in views.items():
        result[name] = '\n'.join([GUARD, 'tp @s '+' '.join(map(str,position))+' 180 5',
                                  *(tell(line) for line in lines), tell('[Lab menu]', 'controls'), 'return 1'])
    return result


def prepare_world(source, destination):
    with (source.parent/'session.lock').open('rb') as lock:
        fcntl.lockf(lock, fcntl.LOCK_SH | fcntl.LOCK_NB)
        prepare(source,destination,title=TITLE,namespace=NS,pack=PACK,build_scripts=scripts,surface=64,structures=False)
    ticks = 120*24000*9//10
    for filename in ('level.dat','data/frozendawn_apocalypse.dat'):
        target=destination/filename;stream=io.BytesIO(gzip.decompress(target.read_bytes()))
        assert stream.read(1)==b'\x0a'
        name=nbt.string(stream);root=nbt.read(stream,10)
        if filename=='level.dat':
            data=root['Data'][1]
            data.update({'DifficultyLocked':(1,1),'confirmedExperimentalSettings':(1,1),'DragonFight':(10,{}),
                         'SpawnX':(3,0),'SpawnY':(3,65),'SpawnZ':(3,4),'DayTime':(4,ticks+18000)})
            version=data['DataVersion']
        else:
            root['data'][1]['apocalypseTicks']=(4,ticks)
        target.write_bytes(gzip.compress(b'\x0a'+nbt.utf(name)+nbt.write(10,root)))
    entries=[{'a':(4,as_long(*a)),'b':(4,as_long(*b))} for a,b in links()]
    network={'data':(10,{'version':(3,1),'links':(9,(10,entries))}),'DataVersion':version}
    (destination/'data/frozendawn_power_network.dat').write_bytes(gzip.compress(b'\x0a'+nbt.utf('')+nbt.write(10,network)))
    report=destination/'preparation.json';meta=json.loads(report.read_text())
    meta.update({'prelinked':len(entries),'apocalypseTicks':ticks,'phase':'paused late vacuum',
                 'fixtures':'two Work Lamps on 200 W grid; Panel reference; isolated 500 J dock lamp initially off; five-face mounting board'})
    report.write_text(json.dumps(meta,indent=2)+'\n')
    lab=Path(__file__).resolve().parents[1]/'run-lab'
    if destination.resolve().parent==(lab/'saves').resolve():
        config=lab/'config/frozendawn-common.toml';config.parent.mkdir(parents=True,exist_ok=True)
        if config.exists():
            text=config.read_text();assert text.count('pauseProgression = ')==1
            config.write_text(text.replace('pauseProgression = false','pauseProgression = true'))
        else:
            config.write_text('[general]\npauseProgression = true\n')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',type=Path);parser.add_argument('--destination',type=Path);parser.add_argument('--pack-only',type=Path)
    args=parser.parse_args()
    if args.pack_only: pack_only(args.pack_only,title=TITLE,namespace=NS,build_scripts=scripts)
    elif args.source and args.destination: prepare_world(args.source,args.destination)
    else: parser.error('Use --source/--destination or --pack-only')

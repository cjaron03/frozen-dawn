#!/usr/bin/env python3
"""Fresh matching-room Core allocation replay; older saves and histories are never copied."""
import argparse
import json
from pathlib import Path
import prepare_thermal_ground_playtest as seed
NS='core_split_check'
TITLE='Core Heat O2 Check'
PACK='core-split-check'
GUARD='execute unless score #built csplit matches 1 run return 0'
def tell(text):return 'tellraw @s '+json.dumps({'text':text,'color':'aqua'})
def row(buttons):return 'tellraw @s '+json.dumps([{'text':''}]+[{'text':label+'  ','color':'aqua','clickEvent':{'action':'run_command','value':fn if fn.startswith('/') else '/function '+NS+':'+fn}} for label,fn in buttons])
def scripts():
    setup=['execute unless score #built csplit matches 0 run return 0','scoreboard players set #built csplit -1','gamemode creative @s',
        'gamerule doMobSpawning false','gamerule doDaylightCycle false','gamerule doWeatherCycle false','gamerule keepInventory true','weather clear','time set noon',
        'fd world preset default','fd world set phase 6 late','fd world pause',
        'fill -12 -34 -4 12 -28 4 minecraft:air','fill -12 -34 -4 12 -34 4 minecraft:stone','fill -1 -28 -1 1 319 1 minecraft:air']
    for x in (-8,8):
        setup += [f'fill {x-2} -33 -2 {x+2} -29 2 minecraft:white_wool hollow',f'fill {x-2} -33 -2 {x+2} -33 2 minecraft:stone',
            f'setblock {x} -32 -2 frozendawn:geothermal_core',f'setblock {x-1} -31 -1 frozendawn:oxygen_gauge[facing=south]',
            f'setblock {x+1} -31 -1 frozendawn:thermostat[facing=south]',
            f'summon minecraft:cow {x+.5} -32 1.5 {{Tags:["core_split_qa"],NoAI:1b,Invulnerable:1b,PersistenceRequired:1b}}']
    setup += ['setworldspawn -8 -32 0','spawnpoint @s -8 -32 0','tp @s -7.5 -32 .5 180 10','effect give @s minecraft:night_vision infinite 0 true',
        'give @s minecraft:glass 32','give @s minecraft:white_wool 32','give @s minecraft:obsidian 4','give @s minecraft:diamond_block 1',
        'give @s minecraft:blaze_powder 2','give @s frozendawn:thermal_core 1','give @s minecraft:nether_star 1',
        'give @s patchouli:guide_book[patchouli:book="frozendawn:frozen_dawn_guide"]',
        'scoreboard players set #panels_fixed csplit 1','scoreboard players set #outside_fixed csplit 1','scoreboard players set #built csplit 1',tell('Two matching underground rooms. Left: reference 60/40. Right: adjustable. One invulnerable cow per room uses 5 O2. Creative safety; normal loaded time.'),'function '+NS+':controls','return 1']
    def action(commands,text):return '\n'.join([GUARD,*commands,tell(text),'return 1'])
    return {
        'load':'scoreboard objectives add csplit dummy\nexecute unless score #outside_fixed csplit matches 0..1 run scoreboard players set #outside_fixed csplit 0\nexecute unless score #built csplit matches -1..1 run scoreboard players set #built csplit 0\nexecute as @a run function '+NS+':controls',
        'tick':'execute if score #built csplit matches 0 as @a[limit=1] run function '+NS+':setup',
        'setup':'\n'.join(setup),
        'controls':'\n'.join([GUARD,tell('CORE HEAT / O2 CHECK'),row([('[Left]','left'),('[Right]','right'),('[Read]','/fd world oxygen here')]),row([('[Breach right]','breach'),('[Seal right]','seal')]),tell('Keep left at 60/40. Right-click right Core: compare 30/70, then 80/20. Wait 60s for heat trends. 80/20 cannot feed the cow; stay Creative.'),'return 1']),
        'left':action(['tp @s -7.5 -32 .5 180 10'],'Reference room. Leave its Core at 60% heat / 40% O2.'),
        'right':action(['tp @s 8.5 -32 .5 180 10'],'Adjustable room. The Core slider trades heat for O2; thermostat shows room temperature.'),
        'breach':action(['setblock 10 -31 0 minecraft:air'],'Right room breached. Reference room should stay sealed. At 100% heat / 0% O2, resealing cannot restore air.'),
        'seal':action(['setblock 10 -31 0 minecraft:white_wool'],'Right wall repaired. Active O2 restores gas after the normal 5s recovery.'),
        'finish_panels':'\n'.join([GUARD,'execute if score #panels_fixed csplit matches 1 run return 0','setblock -9 -31 -2 minecraft:white_wool','setblock -7 -31 -2 minecraft:white_wool','setblock -9 -31 -1 frozendawn:oxygen_gauge[facing=south]','setblock -7 -31 -1 frozendawn:thermostat[facing=south]','setblock 7 -31 -2 minecraft:white_wool','setblock 9 -31 -2 minecraft:white_wool','setblock 7 -31 -1 frozendawn:oxygen_gauge[facing=south]','setblock 9 -31 -1 frozendawn:thermostat[facing=south]','scoreboard players set #panels_fixed csplit 1','return 1']),
        'finish_fixture':'\n'.join([GUARD,'execute if score #outside_fixed csplit matches 1 run return 0','fill -1 -28 -1 1 319 1 minecraft:air','scoreboard players set #outside_fixed csplit 1','return 1']),
        'read':'\n'.join([GUARD,'fd world oxygen here','fd world oxygen','fd world thermal','data get block -8 -32 -2 HeatShare','data get block 8 -32 -2 HeatShare','return 1'])
    }
def pack_only(destination,hooks=False):
    for name,body in scripts().items():
        p=destination/f'data/{NS}/function/{name}.mcfunction';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body+'\n')
    if hooks:
        for name in ('load','tick'):
            p=destination/f'data/minecraft/tags/function/{name}.json';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps({'values':[NS+':'+name]})+'\n')
def prepare(source,destination):
    seed.TITLE,seed.PACK=TITLE,PACK
    seed.scripts=scripts
    seed.prepare(source,destination)
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source',type=Path);p.add_argument('--destination',type=Path);p.add_argument('--pack-only',type=Path);a=p.parse_args()
    if a.pack_only:pack_only(a.pack_only)
    elif a.source and a.destination:prepare(a.source,a.destination)
    else:p.error('Use --source/--destination or --pack-only')

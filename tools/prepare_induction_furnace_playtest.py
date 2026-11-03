#!/usr/bin/env python3
"""Fresh isolated furnace lab in paused late vacuum. Refuses existing saves."""
import argparse,gzip,io,json
from pathlib import Path
from prepare_power_network_playtest import pack_only,prepare
import prepare_blind_architect_playtest as nbt
TITLE='Induction Furnace Check'
NS='induction_furnace_check'
PACK='induction-furnace-check'
GUARD='execute unless score #built ifurn matches 1 run return 0'
def tell(text,function=None):
    v={'text':text,'color':'aqua'}
    if function:v['clickEvent']={'action':'run_command','value':'/function '+NS+':'+function}
    return 'tellraw @s '+json.dumps(v)
def scripts():
    setup=['execute unless score #built ifurn matches 0 run return 0','scoreboard players set #built ifurn -1','gamemode creative @s',
        'gamerule doMobSpawning false','gamerule doDaylightCycle false','gamerule doWeatherCycle false','gamerule keepInventory true',
        'gamerule announceAdvancements false','difficulty normal','weather clear','fill -5 64 -5 19 64 39 minecraft:smooth_stone']
    for x,z,b in [(0,0,'geothermal_core'),(4,0,'power_relay'),(8,0,'induction_furnace'),
        (0,16,'geothermal_core'),(4,16,'battery_bank'),(8,16,'induction_furnace'),
        (0,32,'geothermal_core'),(4,32,'power_relay'),(8,30,'induction_furnace'),(8,34,'induction_furnace')]:
        state='[facing=south]' if b=='induction_furnace' else ''
        setup += [f'setblock {x} 65 {z} frozendawn:{b}{state}',f'setblock {x} 64 {z} minecraft:cyan_concrete']
    # Fixed fixture lighting keeps unpowered machines visible in late vacuum.
    for z in [-3,13,29,37]:setup += [f'setblock -3 64 {z} minecraft:glowstone',f'setblock 12 64 {z} minecraft:glowstone']
    setup += ['give @s frozendawn:conduit_binder','give @s minecraft:raw_iron 64','give @s minecraft:cobblestone 16',
        'give @s minecraft:beef 16','give @s frozendawn:power_cell 2','give @s frozendawn:power_cell[frozendawn:cell_joules=4000]',
        'give @s frozendawn:power_cell[frozendawn:cell_joules=20000]','give @s frozendawn:induction_furnace 2','give @s frozendawn:power_relay 4',
        'setworldspawn 2 65 4','spawnpoint @s 2 65 4','tp @s 2.5 65 4.5 180 15','scoreboard players set #built ifurn 1','function '+NS+':controls','return 1']
    result={'load':'scoreboard objectives add ifurn dummy\nexecute unless score #built ifurn matches -1..1 run scoreboard players set #built ifurn 0\nexecute if score #built ifurn matches 1 as @a run function '+NS+':controls',
        'tick':'execute if score #built ifurn matches 0 as @a[limit=1] run function '+NS+':setup','setup':'\n'.join(setup),
        'controls':'\n'.join([GUARD,tell('INDUCTION FURNACE CHECK - paused late vacuum, Creative.'),tell('[1 Smelting and power loss]','basic'),tell('[2 Battery backup]','backup'),tell('[3 Brownout]','brownout'),
            tell('Open furnace empty-handed. Input -> progress arrow -> output, no fuel slot. Binder reads kW. Ordinary recipes take about 5 seconds at power. Save/reopen a paused partial job to check progress and items.'),'return 1'])}
    for name,(z,lines) in {'basic':(0,['Bind Core x0 -> relay x4 -> furnace x8 at y65,z0. Set Core power to 25%; insert raw iron. No fuel or O2 is needed in this vacuum lab.',
        'Expect an ingot after about 5 seconds. Empty input or blocked output: 0 kW. While a new item is partway done, set Core power to 0%: arrow pauses; restore 25%: resumes.',
        'Break and replace the relay, then rebind: work returns without lost input/progress. Try cobblestone or raw beef too.']),
        'backup':(16,['Bind Core x0 -> Bank x4 -> furnace x8 at y65,z16. Insert a cell and raw iron. At 25% Core power the bank charges surplus while smelting.',
            'Set Core power to 0%: stored energy keeps smelting, using 2 kJ per second of work. Empty input or a full output stops battery draw.',
            'Quick pause test: the supplied 4 kJ cell funds 2 seconds of progress, then pauses before making an ingot. Swap the 20 kJ cell: the same job resumes. No rebinding.']),
        'brownout':(32,['Bind Core x0 -> relay x4 -> both furnaces at x8,z30/z34. Put raw iron in both.',
            'At 9% (1.8 kW), neither can smelt. At 10% (2 kW), one works. At 20% (4 kW), both work. Core allocation never moves by itself.',
            'After smelting completes, idle draw returns to zero. Save and reopen to confirm inventory, progress and links.'])}.items():
        result[name]='\n'.join([GUARD,f'tp @s 2.5 65 {z+4.5} 180 15',*(tell(v) for v in lines),'return 1'])
    return result
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source',type=Path);p.add_argument('--destination',type=Path);p.add_argument('--pack-only',type=Path);a=p.parse_args()
    if a.pack_only:pack_only(a.pack_only,title=TITLE,namespace=NS,build_scripts=scripts)
    elif a.source and a.destination:
        prepare(a.source,a.destination,title=TITLE,namespace=NS,pack=PACK,build_scripts=scripts,surface=64)
        # Only the newly created lab is moved to late vacuum. Default preset is 120 days.
        ticks=120*24000*9//10
        for filename,compound,field,value in [('level.dat','Data','DayTime',ticks+18000),('data/frozendawn_apocalypse.dat','data','apocalypseTicks',ticks)]:
            target=a.destination/filename;stream=io.BytesIO(gzip.decompress(target.read_bytes()));assert stream.read(1)==b'\x0a';name=nbt.string(stream);tag=nbt.read(stream,10);tag[compound][1][field]=(4,value)
            target.write_bytes(gzip.compress(b'\x0a'+nbt.utf(name)+nbt.write(10,tag)))
        report=a.destination/'preparation.json';data=json.loads(report.read_text());data.update({'phase':'late vacuum at 90% of Default 120-day timeline','apocalypseTicks':ticks});report.write_text(json.dumps(data,indent=2)+'\n')
        lab=Path(__file__).resolve().parents[1]/'run-lab'
        if a.destination.resolve().parent==(lab/'saves').resolve():
            config=lab/'config/frozendawn-common.toml';text=config.read_text();assert text.count('pauseProgression = ')==1;config.write_text(text.replace('pauseProgression = false','pauseProgression = true'))
    else:p.error('Use --source/--destination or --pack-only')

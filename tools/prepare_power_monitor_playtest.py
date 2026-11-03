#!/usr/bin/env python3
"""Fresh monitor check. Refuses existing saves; inherits Default and local advancement filter."""
import argparse
from pathlib import Path
from prepare_power_network_playtest import prepare,pack_only
TITLE='Power Monitor Check'
NS='power_monitor_check'
PACK='power-monitor-check'
GUARD='execute unless score #built pmonitor matches 1 run return 0'
def tell(text,function=None):
 import json
 data={'text':text,'color':'aqua'}
 if function:data['clickEvent']={'action':'run_command','value':'/function '+NS+':'+function}
 return 'tellraw @s '+json.dumps(data)
def scripts():
 setup=['execute unless score #built pmonitor matches 0 run return 0','scoreboard players set #built pmonitor -1','gamemode creative @s','gamerule doMobSpawning false','gamerule doDaylightCycle false','gamerule doWeatherCycle false','gamerule keepInventory true','gamerule announceAdvancements false','weather clear','fill -5 64 -5 20 64 25 minecraft:smooth_stone',
 'setblock 0 65 0 frozendawn:geothermal_core','setblock 4 65 0 frozendawn:power_relay','setblock 8 65 0 frozendawn:battery_bank','setblock 12 65 0 frozendawn:induction_furnace[facing=south]','setblock 12 65 4 frozendawn:induction_furnace[facing=south]','setblock 12 65 8 frozendawn:panel_light',
 'setblock 4 65 5 minecraft:stone','setblock 4 65 6 frozendawn:power_monitor[facing=south]','setblock 4 65 7 minecraft:comparator[facing=north]','fill 4 65 8 4 65 22 minecraft:redstone_wire',
 'give @s frozendawn:conduit_binder','give @s minecraft:raw_iron 64','give @s frozendawn:power_cell[frozendawn:cell_joules=500000] 4','give @s frozendawn:power_cell 4','give @s frozendawn:power_monitor 2','give @s minecraft:stone 8','setworldspawn 3 65 4','spawnpoint @s 3 65 4','tp @s 5.5 65 9.5 155 15','scoreboard players set #built pmonitor 1','function '+NS+':controls','return 1']
 result={'load':'scoreboard objectives add pmonitor dummy\nexecute unless score #built pmonitor matches -1..1 run scoreboard players set #built pmonitor 0\nexecute if score #built pmonitor matches 1 as @a run function '+NS+':controls','tick':'execute if score #built pmonitor matches 0 as @a[limit=1] run function '+NS+':setup','setup':'\n'.join(setup),'controls':'\n'.join([GUARD,tell('POWER MONITOR CHECK - Creative, paused progression.'),tell('[1 Readings and linking]','basic'),tell('[2 Brownout list]','brownout'),tell('[3 Battery and comparator]','battery'),'return 1'])}
 for name,lines in {'basic':['Bind Core -> relay -> bank -> both furnaces and lamp. Also link relay -> wall monitor beside the comparator. Set Core25%, put several raw iron in both furnaces, leave bank empty.','Open the monitor empty-handed: supply5.00kW, demand4.10kW, battery0/2000kJ, Normal and no shed devices. Screen graphic on the block indicates state; numeric readings are in the UI.','Shift-clear monitor links: Not linked and comparator0. Rebind relay -> monitor: readings return.'],
 'brownout':['Keep both furnaces stocked; bank cells must be removed for this check. Core10%: one furnace works, monitor lists the other furnace and lamp as shed.','Core20%: both furnaces work, only lamp shed. Core25%: all work, shed list empty. Core9%: both furnaces shed; lamp can still work. Core0%: red No power, all active loads shed.'],
 'battery':['With Core25%, insert one supplied500kJ cell: meter about25%, comparator4. Two charged cells: about50%, comparator8. Four: full, comparator15. Redstone line shows strength; F3 can show dust power.','Set Core0% with charged cells and stocked furnaces: cyan Battery backup, supply follows served demand, charge falls while devices run. Remove all cells: No power, all active devices listed.','If all inputs empty, demand drops to0. Pause at menu, Save and Quit and reopen: links and current readings reconstruct; no saved display grants. Breaking the supporting stone drops the monitor and clears its links.']}.items():result[name]='\n'.join([GUARD,'tp @s 5.5 65 9.5 155 15',*(tell(t) for t in lines),'return 1'])
 return result
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--source',type=Path);p.add_argument('--destination',type=Path);p.add_argument('--pack-only',type=Path);a=p.parse_args()
 if a.pack_only:pack_only(a.pack_only,title=TITLE,namespace=NS,build_scripts=scripts)
 elif a.source and a.destination:prepare(a.source,a.destination,title=TITLE,namespace=NS,pack=PACK,build_scripts=scripts,surface=64)
 else:p.error('Use --source/--destination or --pack-only')

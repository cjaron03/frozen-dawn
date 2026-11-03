#!/usr/bin/env python3
"""Fresh RH-5 Check. Never overwrite an existing save or copy owner gameplay."""
import argparse,json
from pathlib import Path
from prepare_power_network_playtest import prepare,pack_only
TITLE='RH-5 Check';NS='rh5_check';PACK='rh5-check'
GUARD='execute unless score #built rh5check matches 1 run return 0'
def tell(text,function=None):
 value={'text':text,'color':'aqua'}
 if function:value['clickEvent']={'action':'run_command','value':'/function '+NS+':'+function}
 return 'tellraw @s '+json.dumps(value)
def scripts():
 setup=['execute unless score #built rh5check matches 0 run return 0','scoreboard players set #built rh5check -1','gamemode creative @s','gamerule doMobSpawning false','gamerule doDaylightCycle false','gamerule doWeatherCycle false','gamerule doFireTick false','gamerule keepInventory true','gamerule announceAdvancements false','weather clear','fd world set phase 6 late','fd world pause',
 'fill -12 -49 -8 14 -49 8 minecraft:smooth_stone','fill 0 -49 -4 12 -44 6 frozendawn:insulated_glass hollow','fill -12 -49 -2 0 -44 2 frozendawn:insulated_glass hollow','setblock 0 -48 0 frozendawn:bulkhead_feedthrough','setblock -8 -48 0 frozendawn:geothermal_core','setblock -4 -48 0 frozendawn:battery_bank','setblock 3 -48 0 frozendawn:power_relay',
 'setblock 6 -48 0 frozendawn:resistive_heater[facing=south]','setblock 9 -48 0 frozendawn:resistive_heater[facing=south]','setblock 9 -48 3 frozendawn:induction_furnace[facing=south]','setblock 9 -47 3 frozendawn:panel_light','setblock 7 -48 5 frozendawn:thermostat[facing=north]','setblock 10 -48 5 frozendawn:power_monitor[facing=north]','setblock 5 -48 3 frozendawn:iron_thermal_heater',
 'fill 18 64 -4 28 64 5 minecraft:smooth_stone','fill 20 64 -2 26 68 3 minecraft:white_wool hollow','setblock 23 65 0 frozendawn:resistive_heater[facing=south]','setblock 22 65 1 frozendawn:cell_dock','setblock 24 65 2 frozendawn:thermostat[facing=north]',
 'give @s frozendawn:conduit_binder','give @s frozendawn:power_cell[frozendawn:cell_joules=500000] 4','give @s frozendawn:power_cell[frozendawn:cell_joules=20000] 4','give @s minecraft:coal 64','give @s minecraft:raw_iron 64','give @s frozendawn:resistive_heater 2','give @s frozendawn:insulated_glass 16','effect give @s minecraft:night_vision infinite 0 true','setworldspawn 6 -48 4','spawnpoint @s 6 -48 4','tp @s 6.5 -48 4.5 180 10','scoreboard players set #built rh5check 1','function '+NS+':controls','return 1']
 result={'load':'scoreboard objectives add rh5check dummy\nexecute unless score #built rh5check matches -1..1 run scoreboard players set #built rh5check 0\nexecute if score #built rh5check matches 1 as @a run function '+NS+':controls','tick':'execute if score #built rh5check matches 0 as @a[limit=1] run function '+NS+':setup','setup':'\n'.join(setup),'controls':'\n'.join([GUARD,tell('RH-5 CHECK - Creative, Default, paused late vacuum. Main underground room and separate surface dock room.'),tell('[1 Basic heat and target]','basic'),tell('[2 Battery and fuel backup]','backup'),tell('[3 Two heaters and brownout]','brownout'),tell('[4 Isolated cell dock]','dock'),tell('[5 Save/reopen]','persistence'),'return 1'])}
 descriptions={
 'basic':['Bind outside Core -> Bank -> Feedthrough -> inside Relay -> ONE heater at6,-48,0, panel lamp and monitor. Leave the second heater unlinked. Set Core power35%, keep bank empty and fuel heater empty.','RH-5 starts up to5kW, orange coils, Heating room. Watch room temperature rise and power throttle toward target20C. Thermostat sets the target for this room. At/above target it draws0kW; other room heat may hold it idle.','Change thermostat to30C, then10C: electric heat follows, stops above target and cannot cool. Core is in its own room, so its passive heat cannot warm this test room.'],
 'backup':['Put a charged cell in the bank, set Core power0%. Heater keeps warming and charge falls. Supplied20kJ cell makes a short depletion test; swap a full cell to resume without rebinding.','Add coal to Iron Thermal Heater at5,-48,3. When electric heat can cover demand, fuel duty stays0. On electrical failure it automatically contributes heat; it may help during a cold start if total heat demand exceeds RH-5 output.'],
 'brownout':['Let the room cool or raise thermostat target so it needs heat. Link the second RH-5 at9,-48,0 and stock the furnace. Remove bank cells and keep fuel empty.','At50% Core output the cold heaters can use10kW and the furnace waits. At65% there is13kW for both cold heaters, the2kW furnace and0.1kW lamp. Near target draw falls, so these thresholds apply only while the heaters demand full output.','Set25% to see only5kW shared by the heaters and lower-priority devices waiting. Unlink one heater: the other still uses available power. Binder/monitor show the network totals.'],
 'dock':['Surface room: bind dock22,65,1 directly to heater23,65,0. Insert a charged cell. No Core/pylon connection is allowed in this isolated dock circuit.','Below freezing the heater still works. In air it heats room air; with no air it says Warming structure and uses25% heat coupling. Temperature is the physical structure reading in that state. A cell empties quickly at5kW: full500kJ is100seconds at continuous full load.'],
 'persistence':['Note target, temperature and cell charge. Save and Quit, reopen RH-5 Check, then verify targets, cells and Binder links remain and paid heat resumes.','Electric grants and UI readings rebuild after load; no offline heat or free prepaid power is restored. Look at the steel/copper cabinet and orange active elements.']}
 for name,lines in descriptions.items():
  pos='tp @s 23.5 65 1.5 180 10' if name=='dock' else 'tp @s 6.5 -48 4.5 180 10'
  result[name]='\n'.join([GUARD,pos,*(tell(line) for line in lines),'return 1'])
 return result
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--source',type=Path);p.add_argument('--destination',type=Path);p.add_argument('--pack-only',type=Path);a=p.parse_args()
 if a.pack_only:pack_only(a.pack_only,title=TITLE,namespace=NS,build_scripts=scripts)
 elif a.source and a.destination:prepare(a.source,a.destination,title=TITLE,namespace=NS,pack=PACK,build_scripts=scripts,surface=16)
 else:p.error('Use --source/--destination or --pack-only')

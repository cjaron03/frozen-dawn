#!/usr/bin/env python3
"""New PIXIE-2 Check; never overwrites a save or copies gameplay from a prior world."""
import argparse,json
from pathlib import Path
from prepare_power_network_playtest import prepare,pack_only
TITLE='PIXIE-2 Check';NS='pixie_check';PACK='pixie-check'
GUARD='execute unless score #built pixcheck matches 1 run return 0'
def tell(text,function=None):
 value={'text':text,'color':'aqua'}
 if function:value['clickEvent']={'action':'run_command','value':'/function '+NS+':'+function}
 return 'tellraw @s '+json.dumps(value)
def scripts():
 setup=['execute unless score #built pixcheck matches 0 run return 0','scoreboard players set #built pixcheck -1','gamemode creative @s','gamerule doMobSpawning false','gamerule doDaylightCycle false','gamerule doWeatherCycle false','gamerule doFireTick false','gamerule keepInventory true','gamerule announceAdvancements false','weather clear','fd world set phase 6 late','fd world pause',
 'fill -12 -49 -8 14 -49 8 minecraft:smooth_stone','fill 0 -49 -4 12 -44 6 frozendawn:insulated_glass hollow','fill -12 -49 -2 0 -44 2 frozendawn:insulated_glass hollow','setblock 0 -48 0 frozendawn:bulkhead_feedthrough','setblock -8 -48 0 frozendawn:geothermal_core','setblock -4 -48 0 frozendawn:battery_bank','setblock 3 -48 0 frozendawn:power_relay','setblock 6 -48 0 frozendawn:pixie[facing=south]','setblock 9 -48 0 frozendawn:induction_furnace[facing=south]','setblock 9 -48 3 frozendawn:panel_light','setblock 6 -48 3 frozendawn:oxygen_reservoir{StoredOxygen:12000000L}','setblock 4 -48 5 frozendawn:oxygen_gauge[facing=north]','setblock 8 -48 5 frozendawn:power_monitor[facing=north]',
 'summon minecraft:pig 5.5 -48 2.5 {NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Tags:["pixie_qa"]}','summon minecraft:pig 7.5 -48 2.5 {NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Tags:["pixie_qa"]}',
 'fill 16 64 -4 27 64 4 minecraft:smooth_stone','fill 20 64 -2 24 68 2 minecraft:white_wool hollow','fill 14 64 -2 20 68 2 frozendawn:insulated_glass hollow','setblock 20 65 0 frozendawn:bulkhead_feedthrough','setblock 16 65 0 frozendawn:geothermal_core','setblock 22 65 -1 frozendawn:pixie[facing=south]','setblock 22 65 1 frozendawn:oxygen_reservoir','setblock 23 65 0 frozendawn:thermal_heater',
 'give @s frozendawn:conduit_binder','give @s minecraft:ice 64','give @s minecraft:packed_ice 8','give @s minecraft:blue_ice 4','give @s minecraft:snow_block 16','give @s frozendawn:power_cell[frozendawn:cell_joules=500000] 4','give @s frozendawn:power_cell[frozendawn:cell_joules=20000] 4','give @s minecraft:coal 64','give @s minecraft:raw_iron 64','give @s frozendawn:oxygen_reservoir','give @s frozendawn:pixie','give @s frozendawn:insulated_glass 16','effect give @s minecraft:night_vision infinite 0 true','setworldspawn 5 -48 4','spawnpoint @s 5 -48 4','tp @s 5.5 -48 4.5 180 10','scoreboard players set #built pixcheck 1','function '+NS+':controls','return 1']
 result={'load':'scoreboard objectives add pixcheck dummy\nexecute unless score #built pixcheck matches -1..1 run scoreboard players set #built pixcheck 0\nexecute if score #built pixcheck matches 1 as @a run function '+NS+':controls','tick':'execute if score #built pixcheck matches 0 as @a[limit=1] run function '+NS+':setup','setup':'\n'.join(setup),'controls':'\n'.join([GUARD,tell('PIXIE-2 CHECK - Creative, Default, paused late vacuum. Warm underground room and separate frozen surface room.'),tell('[1 Oxygen and simple UI]','basic'),tell('[2 Battery backup]','backup'),tell('[3 Life-support brownout]','brownout'),tell('[4 Frozen reservoir]','cold'),tell('[5 Heat and thaw]','warm'),tell('[6 Unsealed tank-only]','tank'),'return 1'])}
 descriptions={
 'basic':['Bind outside Core -> Bank -> Feedthrough -> inside Relay -> PIXIE, furnace, lamp and monitor. Set Core power25%. Core in the separate power bay does not supply the main room O2.','Put ice in PIXIE. Two QA pigs demand2.6 O2 units: expect Producing2.6/4.0,2.60kW. O2 tank starts full as a finite buffer; PIXIE replaces its output.','Stock furnace: total power demand4.70kW. Power monitor should agree. Remove demand or fill storage: idle uses0 power and no water. Check native steel/copper sight-window artwork.'],
 'backup':['Keep ice, stock furnace and install a charged bank cell. Set Core power0%: PIXIE keeps making oxygen and cells drain; monitor says Battery backup.','Use supplied20kJ cell alone for a short depletion check. PIXIE stops when it empties and keeps water/ice. Swap a full cell to resume without rebinding. O2 tank remains a separate finite breathing reserve.'],
 'brownout':['Remove bank cells. Keep PIXIE supplied and furnace stocked. Core13%=2.6kW: PIXIE fully supplies pigs; furnace and lamp wait.','Core12%=2.4kW: PIXIE has partial2.4 output and O2 is strained. Core23%=4.6kW: PIXIE and furnace work; lamp waits. Core24%=4.8kW: all work.'],
 'cold':['Surface room: Core at16,65,0 -> Feedthrough20,65,0 -> PIXIE22,65,-1. Set Core power25%, add ice and link PIXIE directly to tank22,65,1.','Below1C: Reservoir frozen; no output or ice/water consumption. Empty thermal heater at23,65,0 is intentional.'],
 'warm':['Put coal in the surface Thermal Heater at23,65,0. Leave it heating; the small insulated room must rise above1C before PIXIE resumes filling its tank.','No fake temperature command: this tests normal room heating. Once tank fills, PIXIE idles. Surface warming can take a few minutes from late-vacuum cold.'],
 'tank':['Warm main room: bind PIXIE6,-48,0 directly to oxygen tank6,-48,3. Harvest the full tank and place the supplied empty tank for this test.','Run Breach: open room O2 stays No seal; warm PIXIE says Unsealed - tank fill only and fills the explicitly linked vessel. No link or full tank means no wasted output.','Run Seal, wait for normal paid pressure recovery and check room O2 returns. Save/reopen: ice, water and links persist; output is rebuilt.']}
 for name,lines in descriptions.items():
  position='tp @s 21.5 65 .5 90 10' if name in ('cold','warm') else 'tp @s 5.5 -48 4.5 180 10'
  result[name]='\n'.join([GUARD,position,*(tell(t) for t in lines),'return 1'])
 result['breach']='\n'.join([GUARD,'setblock 12 -47 2 minecraft:air',tell('Main room opened. Tank-only output requires explicit PIXIE -> tank link and free tank space.'),'return 1'])
 result['seal']='\n'.join([GUARD,'setblock 12 -47 2 frozendawn:insulated_glass',tell('Main room resealed. Keep PIXIE warm, watered and powered; ordinary sealed-room pressure recovery should follow.'),'return 1'])
 result['tank']=result['tank'].rsplit('\nreturn 1',1)[0]+'\n'+tell('[Breach main room]','breach')+'\n'+tell('[Seal main room]','seal')+'\nreturn 1'
 return result
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--source',type=Path);p.add_argument('--destination',type=Path);p.add_argument('--pack-only',type=Path);a=p.parse_args()
 if a.pack_only:pack_only(a.pack_only,title=TITLE,namespace=NS,build_scripts=scripts)
 elif a.source and a.destination:prepare(a.source,a.destination,title=TITLE,namespace=NS,pack=PACK,build_scripts=scripts,surface=16)
 else:p.error('Use --source/--destination or --pack-only')

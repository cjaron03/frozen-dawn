#!/usr/bin/env python3
"""Fresh charged-cell loot bench. Refuse overwrites; production loot is staged once by the lab hook."""
import argparse,json
from pathlib import Path
from prepare_power_network_playtest import prepare,pack_only
TITLE='Charged Cell Salvage Check';NS='charged_cell_check';PACK='charged-cell-salvage-check'
def tell(text):return 'tellraw @s '+json.dumps({'text':text,'color':'aqua'})
def scripts():
 guard='execute unless score #built csalvage matches 1 run return 0'
 setup=['execute unless score #built csalvage matches 0 run return 0','scoreboard players set #built csalvage -1','gamemode creative @s','gamerule doMobSpawning false','gamerule doDaylightCycle false','gamerule doWeatherCycle false','gamerule doFireTick false','gamerule keepInventory true','gamerule announceAdvancements false','difficulty normal','fd world preset default','fd world pause','time set noon','weather clear','fill -9 64 -5 14 64 12 minecraft:smooth_stone','fill -9 65 -5 14 72 12 minecraft:air','execute store result score #result csalvage run fdlab charged_cell_setup','execute unless score #result csalvage matches 1 run return 0','give @s frozendawn:conduit_binder','give @s minecraft:raw_iron 32','give @s frozendawn:cell_dock 2','give @s frozendawn:panel_light 2','effect give @s minecraft:night_vision infinite 0 true','setworldspawn 2 65 3','spawnpoint @s 2 65 3','tp @s 2.5 65 3.5 180 10','fd world set phase 6 late','fd world pause','scoreboard players set #built csalvage 1','function '+NS+':controls','return 1']
 return {'load':'scoreboard objectives add csalvage dummy\nexecute unless score #built csalvage matches -1..1 run scoreboard players set #built csalvage 0\nexecute if score #built csalvage matches 1 as @a run function '+NS+':controls','tick':'execute if score #built csalvage matches 0 as @a[limit=1] run function '+NS+':setup','setup':'\n'.join(setup),'controls':'\n'.join([guard,tell('CHARGED CELL SALVAGE: staged production loot; cargo x-4, station x0, vehicle x4, surviving camp x8 at z0.'),tell('Each eligible cache has one 100–300 kJ cell (capacity 500 kJ). No Core, free charge or extra cells supplied.'),tell('Take a cell and note charge. Dock at0,65,6; lamp at4,65,6. Bind dock -> lamp, insert cell: light on and charge falls.'),tell('For quicker use, link dock -> induction furnace at8,65,6 and insert raw iron. Smelting draws 2 kW; no O2 needed.'),tell('Remove cell: devices stop. Drop/pick it up and save/reopen; remaining charge stays. Reopened looted crates stay empty.'),tell('This tests real loot providers and finite storage, not natural site discovery. No setup/reset repeats.'),'return 1'])}
if __name__=='__main__':
 a=argparse.ArgumentParser();a.add_argument('--source',type=Path);a.add_argument('--destination',type=Path);a.add_argument('--pack-only',type=Path);o=a.parse_args()
 if o.pack_only:pack_only(o.pack_only,title=TITLE,namespace=NS,build_scripts=scripts)
 elif o.source and o.destination:prepare(o.source,o.destination,title=TITLE,namespace=NS,pack=PACK,build_scripts=scripts,surface=64)
 else:a.error('Use --source/--destination or --pack-only')

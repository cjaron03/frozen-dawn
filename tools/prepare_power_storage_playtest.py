#!/usr/bin/env python3
"""Fresh storage lab: metadata only; never overwrite an existing save."""
import argparse
import json
from pathlib import Path
from prepare_power_network_playtest import pack_only, prepare
TITLE='Power Storage Check'
NS='power_storage_check'
PACK='power-storage-check'
GUARD='execute unless score #built pstore matches 1 run return 0'
def tell(text,function=None):
    v={'text':text,'color':'aqua'}
    if function:v['clickEvent']={'action':'run_command','value':'/function '+NS+':'+function}
    return 'tellraw @s '+json.dumps(v)
def scripts():
    setup=['execute unless score #built pstore matches 0 run return 0','scoreboard players set #built pstore -1',
        'gamemode creative @s','gamerule doMobSpawning false','gamerule doDaylightCycle false','gamerule doWeatherCycle false',
        'gamerule keepInventory true','gamerule announceAdvancements false','difficulty normal','time set midnight','weather clear',
        'fill -5 64 -5 25 64 29 minecraft:smooth_stone']
    for x,z,b in [(0,0,'geothermal_core'),(4,0,'battery_bank'),(8,0,'panel_light'),
        (0,12,'cell_dock'),(6,12,'panel_light'),(10,12,'power_relay'),
        (0,24,'geothermal_core'),(4,24,'battery_bank'),(8,24,'panel_light')]:
        setup += [f'setblock {x} 65 {z} frozendawn:{b}',f'setblock {x} 64 {z} minecraft:cyan_concrete']
    setup += ['give @s frozendawn:conduit_binder','give @s frozendawn:power_cell 4',
        'give @s frozendawn:power_cell[frozendawn:cell_joules=2000]','give @s frozendawn:panel_light 4',
        'give @s frozendawn:battery_bank 2','give @s frozendawn:cell_dock 2',
        'setworldspawn 2 65 4','spawnpoint @s 2 65 4','tp @s 2.5 65 4.5 180 15',
        'scoreboard players set #built pstore 1','function '+NS+':controls','return 1']
    result={'load':'scoreboard objectives add pstore dummy\nexecute unless score #built pstore matches -1..1 run scoreboard players set #built pstore 0\nexecute if score #built pstore matches 1 as @a run function '+NS+':controls',
        'tick':'execute if score #built pstore matches 0 as @a[limit=1] run function '+NS+':setup',
        'setup':'\n'.join(setup),
        'controls':'\n'.join([GUARD,tell('POWER STORAGE CHECK'),tell('[1 Charge cells]','charge'),tell('[2 Carry to an outpost]','outpost'),tell('[3 Battery backup]','backup'),
            tell('Empty hand opens bank/dock. Shift-click moves cells. Cell hover shows kJ. Binder links and reads network kJ. The 2 kJ cell is a finite 20-second depletion fixture; ordinary cells craft empty.'),'return 1'])}
    for name,(z,lines) in {'charge':(0,['Bind Core x0 -> Bank x4 -> Panel x8, at y65,z0. Insert the four empty cells; set Core power to 25%.',
        'The light uses 0.10 kW; the remaining 4.90 kW charges cells. Watch bank kJ rise. Remove a partially charged cell: its tooltip keeps the charge. Full cells stop charging.']),
        'outpost':(12,['Bind Dock x0 -> Panel x6 at y65,z12. Insert a charged cell from home: light on, kJ falls.',
            'For a quick depletion test use the supplied 2 kJ cell: about 20 seconds for one panel, then light off. Swap a charged cell in: light returns without rebinding.',
            'Trying Dock -> relay x10 (or linking the panel to that relay) is rejected: docks run devices directly.']),
        'backup':(24,['Bind Core x0 -> Bank x4 -> Panel x8 at y65,z24. Charge a cell using 25% Core power.',
            'Set Core power to 0%: bank feeds the lamp while its kJ falls. Pull its cell: lamp off. Replace the cell: on.',
            'Break a bank or dock: its cells drop with charge intact. Save and reopen to confirm cell charge, slots and links persist.'])}.items():
        result[name]='\n'.join([GUARD,f'tp @s 2.5 65 {z+4.5} 180 15',*(tell(v) for v in lines),'return 1'])
    return result
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source',type=Path);p.add_argument('--destination',type=Path);p.add_argument('--pack-only',type=Path);a=p.parse_args()
    if a.pack_only:pack_only(a.pack_only,title=TITLE,namespace=NS,build_scripts=scripts)
    elif a.source and a.destination:
        prepare(a.source,a.destination,title=TITLE,namespace=NS,pack=PACK,build_scripts=scripts,surface=64)
        lab=Path(__file__).resolve().parents[1]/'run-lab'
        if a.destination.resolve().parent==(lab/'saves').resolve():
            config=lab/'config/frozendawn-common.toml';text=config.read_text();assert text.count('pauseProgression = ')==1
            config.write_text(text.replace('pauseProgression = false','pauseProgression = true'))
    else:p.error('Use --source/--destination or --pack-only')

#!/usr/bin/env python3
"""Fresh shared-target replay using the accepted wool/glass geometry and seed metadata only."""
import argparse
from pathlib import Path
import prepare_room_heat_playtest as base


def scripts():
    functions = base.scripts()
    functions['controls'] = '\n'.join([
        base.GUARD,
        base.tell('20C control QA: visit both, Fuel both, then compare air/walls and each heater panel.'),
        base.tell('Wait for target: wool should hold it with less fuel. Heater panels show Heating/Holding and burn %.'),
        base.tell('Add second heaters: both share a 20C target. Redstone off holds fuel; resume restarts heating. No thermostat yet.'),
        base.row([('[Wool left]', 'left'), ('[Glass right]', 'right'), ('[Read air/walls]', '/fd world thermal')]),
        base.row([('[Fuel both]', 'fuel'), ('[Heaters off]', 'off'), ('[Outside]', 'outside')]),
        base.row([('[Add second heaters]', 'two'), ('[Redstone off]', 'redstone_on'), ('[Resume]', 'redstone_off')]),
        base.row([('[Read fuel]', 'fuel_read'), ('[Breach left]', 'breach'), ('[Patch left]', 'patch')])])
    functions['setup'] = functions['setup'].replace('Earlier saves are preserved.', 'Automatic 20C target enabled. Earlier saves are preserved.')
    functions['fuel'] = functions['fuel'].replace('Normal simulation now warms air and walls; compare after 30, 60 and 120 seconds.',
                                                'Automatic 20C target enabled. Compare warm-up, then holding burn rates.')
    functions['two'] = '\n'.join([base.GUARD,
        'execute if score #second rheat matches 1 run return 0',
        'setblock -7 66 0 frozendawn:thermal_heater', 'setblock 9 66 0 frozendawn:thermal_heater',
        'data merge block -7 66 0 {BurnTime:960000}', 'data merge block 9 66 0 {BurnTime:960000}',
        'scoreboard players set #second rheat 1',
        base.tell('Second heaters installed and fueled. Both rooms still share a 20C target; original fuel is untouched.')])
    functions['redstone_on'] = '\n'.join([base.GUARD,
        'setblock -8 67 0 minecraft:lever[face=floor,facing=north,powered=true]',
        'setblock 8 67 0 minecraft:lever[face=floor,facing=north,powered=true]',
        'execute if score #second rheat matches 1 run setblock -7 67 0 minecraft:lever[face=floor,facing=north,powered=true]',
        'execute if score #second rheat matches 1 run setblock 9 67 0 minecraft:lever[face=floor,facing=north,powered=true]',
        base.tell('Redstone disables all test heaters. Read fuel now and again after 30 seconds; it should stay unchanged.')])
    functions['redstone_off'] = '\n'.join([base.GUARD,
        'setblock -8 67 0 minecraft:air', 'setblock 8 67 0 minecraft:air',
        'execute if score #second rheat matches 1 run setblock -7 67 0 minecraft:air',
        'execute if score #second rheat matches 1 run setblock 9 67 0 minecraft:air',
        base.tell('Redstone removed. Existing fuel resumes heating when the room needs it.')])
    functions['fuel_read'] = '\n'.join([base.GUARD,
        'execute store result score #wool rheat run data get block -8 66 0 BurnTime',
        'execute store result score #glass rheat run data get block 8 66 0 BurnTime',
        'tellraw @s [{"text":"Original heaters — fuel units: wool "},{"score":{"name":"#wool","objective":"rheat"}},{"text":" | glass "},{"score":{"name":"#glass","objective":"rheat"}}]',
        'execute if score #second rheat matches 1 store result score #wool2 rheat run data get block -7 66 0 BurnTime',
        'execute if score #second rheat matches 1 store result score #glass2 rheat run data get block 9 66 0 BurnTime',
        'execute if score #second rheat matches 1 run tellraw @s [{"text":"Second heaters — fuel units: wool "},{"score":{"name":"#wool2","objective":"rheat"}},{"text":" | glass "},{"score":{"name":"#glass2","objective":"rheat"}}]'])
    # Off and fuel cover every installed heater without creating a new one or restoring consumed fuel.
    functions['off'] += '\nexecute if score #second rheat matches 1 run data merge block -7 66 0 {BurnTime:0}\nexecute if score #second rheat matches 1 run data merge block 9 66 0 {BurnTime:0}'
    return functions


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source', required=True, type=Path)
    p.add_argument('--destination', required=True, type=Path)
    args = p.parse_args()
    base.seed.TITLE = 'Heater Control Check'
    base.seed.PACK = 'heater-control-check'
    base.seed.NS = base.NS
    base.seed.scripts = scripts
    base.seed.prepare(args.source, args.destination)

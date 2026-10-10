#!/usr/bin/env python3
"""Fresh two-reservoir thermal comparison; seed metadata only, never overwrite prior replay."""
import argparse
import json
from pathlib import Path
import prepare_thermal_ground_playtest as seed

NS = 'room_heat_check'
GUARD = 'execute unless score #built rheat matches 1 run return 0'

def tell(text):
    return 'tellraw @s ' + json.dumps({'text': text, 'color': 'aqua'})

def row(buttons):
    return 'tellraw @s ' + json.dumps([{'text': ''}] + [
        {'text': label + '  ', 'color': 'aqua', 'clickEvent': {'action': 'run_command', 'value': name if name.startswith('/') else f'/function {NS}:{name}'}}
        for label, name in buttons])

def scripts():
    def action(commands, text):
        return '\n'.join([GUARD, *commands, tell(text)])
    setup = [
        'execute unless score #built rheat matches 0 run return 0',
        'scoreboard players set #built rheat -1', 'gamemode creative @s',
        'gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false',
        'weather clear', 'time set noon', 'fd world preset default', 'fd world set phase 6 late', 'fd world pause',
        'fill -12 60 -4 12 187 4 minecraft:air', 'fill -12 188 -4 12 319 4 minecraft:air',
    ]
    for x, block in ((-8, 'minecraft:white_wool'), (8, 'minecraft:glass')):
        setup += [f'fill {x-2} 61 -2 {x+2} 64 2 minecraft:stone',
                  f'fill {x-2} 64 -2 {x+2} 68 2 {block} hollow',
                  f'setblock {x} 66 0 frozendawn:thermal_heater',
                  f'setblock {x} 66 -2 frozendawn:geothermal_core']
    setup += ['fill -2 64 6 2 64 8 minecraft:stone', 'setworldspawn 0 65 7', 'spawnpoint @s 0 65 7',
              'tp @s .5 65 7.5 180 0', 'effect give @s minecraft:night_vision infinite 0 true',
              'give @s patchouli:guide_book[patchouli:book="frozendawn:frozen_dawn_guide"]', 'scoreboard players set #built rheat 1',
              f'function {NS}:controls', tell('QA: wool left, glass right; matching rooms with Core oxygen. Heaters start OFF. Earlier saves are preserved.')]
    return {
        'load': 'scoreboard objectives add rheat dummy\nexecute unless score #built rheat matches 1 run scoreboard players set #built rheat 0\n'
                + f'execute if score #built rheat matches 1 as @a run function {NS}:controls',
        'tick': f'execute if score #built rheat matches 0 as @a[limit=1] at @s run function {NS}:setup',
        'setup': '\n'.join(setup),
        'controls': '\n'.join([GUARD,
            tell('Visit both rooms. Fuel both; compare warming at 30, 60 and 120 unpaused seconds.'),
            tell('Off: gradual cooling. Breach/patch left: wall heat stays, air refills in five sealed seconds.'),
            tell('Fixed power for physics QA; 20C throttle is pending. Core warmth adds a HUD bonus.'),
            row([('[Wool left]', 'left'), ('[Glass right]', 'right'), ('[Read air/walls]', '/fd world thermal')]),
            row([('[Fuel both]', 'fuel'), ('[Heaters off]', 'off'), ('[Outside]', 'outside')]),
            row([('[Breach left]', 'breach'), ('[Patch left]', 'patch')])]),
        'left': action(['tp @s -8.5 65 .5 180 0'], 'Wool room. Wait for the temperature HUD, then Read air/walls.'),
        'right': action(['tp @s 7.5 65 .5 180 0'], 'Glass room. Wait for the temperature HUD, then Read air/walls.'),
        'read': action(['fd world thermal'], 'Air is the gas reservoir; walls retain heat. The HUD also includes existing local Core warmth.'),
        'fuel': action(['data merge block -8 66 0 {BurnTime:960000}', 'data merge block 8 66 0 {BurnTime:960000}'],
                       'Both matching heaters fueled. Normal simulation now warms air and walls; compare after 30, 60 and 120 seconds.'),
        'off': action(['data merge block -8 66 0 {BurnTime:0}', 'data merge block 8 66 0 {BurnTime:0}'], 'Both heaters off. Watch stored room heat cool gradually.'),
        'outside': action(['tp @s .5 65 7.5 180 0'], 'Outside observer platform; both room chunks remain loaded, with no forced chunks.'),
        'breach': action(['setblock -6 66 0 minecraft:air'], 'Left wall breached. Compare air loss with retained wall heat.'),
        'patch': action(['setblock -6 66 0 minecraft:white_wool'], 'Left wall repaired. Five sealed seconds restore Core-fed air; warm walls then reheat it.')
    }

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True, type=Path)
    parser.add_argument('--destination', required=True, type=Path)
    args = parser.parse_args()
    seed.TITLE = 'Room Heat Check'
    seed.PACK = 'room-heat-check'
    seed.NS = NS
    seed.scripts = scripts
    seed.prepare(args.source, args.destination)

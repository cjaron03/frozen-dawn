#!/usr/bin/env python3
"""Fresh owner-driven thermostat replay; seed metadata only, never overwrite history."""
import argparse
from pathlib import Path
import prepare_room_heat_playtest as base


def scripts():
    functions = base.scripts()
    panels = [
        'setblock -8 65 -1 frozendawn:thermostat[facing=south]',
        'setblock 8 65 -1 frozendawn:thermostat[facing=south]',
        'give @s frozendawn:thermostat 4',
        'give @s frozendawn:iron_thermal_heater 2',
        'give @s frozendawn:gold_thermal_heater 2',
        'give @s frozendawn:diamond_thermal_heater 2',
        'give @s frozendawn:thermal_capacitor 8',
        'give @s minecraft:coal_block 64',
    ]
    functions['setup'] = functions['setup'].replace('scoreboard players set #built rheat 1', '\n'.join(panels + ['scoreboard players set #built rheat 1']))
    functions['controls'] = '\n'.join([
        base.GUARD,
        base.tell('Thermostat QA: Fuel both, then right-click the wall panel near each Core.'),
        base.tell('Start at 20C. Compare Sensed and Room base after 30, 60 and 120 unpaused seconds.'),
        base.tell('Then try 30C and 10C. Lowering the target stops demand; stored heat must cool naturally.'),
        base.tell('Core warmth stays additive. At +25C, sensed 20C can mean air -5C. Compare HUD near the panel.'),
        base.row([('[Wool left]', 'left'), ('[Glass right]', 'right'), ('[Read air/walls]', '/fd world thermal')]),
        base.row([('[Fuel both]', 'fuel'), ('[Heaters off]', 'off'), ('[Outside]', 'outside')]),
        base.row([('[Breach left]', 'breach'), ('[Patch left]', 'patch')]),
        base.tell('Optional after target checks: place another panel in the same room. Newest wins; remove it to restore the first.'),
        base.tell('Recipe, sensed-temperature limits and comparator details are in ORSA Field Manual / Heating / ORSA Thermostat.')])
    functions['fuel'] = functions['fuel'].replace('Normal simulation now warms air and walls; compare after 30, 60 and 120 seconds.',
        'Heaters follow each room thermostat. Compare sensed temperature and holding burn after settling.')
    functions['setup'] = functions['setup'].replace('QA: wool left, glass right; matching rooms with Core oxygen. Heaters start OFF. Earlier saves are preserved.',
        'QA: fresh wool/glass rooms, Core oxygen and default 20C thermostats. Heaters start OFF. Earlier saves are preserved.')
    return functions


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True, type=Path)
    parser.add_argument('--destination', required=True, type=Path)
    args = parser.parse_args()
    base.seed.TITLE = 'Thermostat Check'
    base.seed.PACK = 'thermostat-check'
    base.seed.NS = base.NS
    base.seed.scripts = scripts
    base.seed.prepare(args.source, args.destination)

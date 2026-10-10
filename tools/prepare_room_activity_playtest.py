#!/usr/bin/env python3
"""Fresh loaded-room heater keep-alive comparison. Seed metadata only; preserves earlier saves."""
import argparse
import json
from pathlib import Path
import prepare_thermal_ground_playtest as seed

NS = 'room_activity_check'
GUARD = 'execute unless score #built ractive matches 1 run return 0'


def tell(text):
    return 'tellraw @s ' + json.dumps({'text': text, 'color': 'aqua'})


def row(buttons):
    return 'tellraw @s ' + json.dumps([{'text': ''}] + [
        {'text': label + '  ', 'color': 'aqua', 'clickEvent': {'action': 'run_command', 'value': command}}
        for label, command in buttons])


def scripts():
    def button(label, name):
        return label, f'/function {NS}:{name}'
    def action(commands, text):
        return '\n'.join([GUARD, *commands, tell(text)])
    return {
        'load': 'scoreboard objectives add ractive dummy\nexecute unless score #built ractive matches 1 run scoreboard players set #built ractive 0\n'
                + f'execute if score #built ractive matches 1 as @a run function {NS}:controls',
        'tick': '\n'.join([
            f'execute if score #built ractive matches 0 as @a[limit=1] at @s run function {NS}:setup',
            'execute if score #stage ractive matches 1 run scoreboard players add #timer ractive 1',
            'execute if score #stage ractive matches 1 if score #timer ractive matches 800.. run scoreboard players set #stage ractive 2',
            f'execute if score #stage ractive matches 2 as @a run function {NS}:ready']),
        'setup': '\n'.join([
            'execute unless score #built ractive matches 0 run return 0',
            'scoreboard players set #built ractive -1', 'gamemode creative @s',
            'gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false',
            'weather clear', 'time set noon', 'fd world preset default', 'fd world set phase 6 late', 'fd world pause',
            'fill -6 64 -3 6 319 3 minecraft:air', 'fill -5 64 -2 5 68 2 minecraft:glass hollow',
            'fill -5 64 -2 5 64 2 minecraft:stone', 'fill 0 65 -1 0 67 1 minecraft:glass',
            'setblock -3 66 0 frozendawn:thermal_heater[lit=true,glow_stage=4]{BurnTime:960000}',
            'setblock 3 66 0 frozendawn:thermal_heater', 'fill -4 64 7 4 64 10 minecraft:stone',
            'setworldspawn 0 65 8', 'spawnpoint @s 0 65 8', 'tp @s .5 65 8.5 180 0',
            'give @s minecraft:coal_block 4', 'effect give @s minecraft:night_vision infinite 0 true',
            'scoreboard players set #stage ractive 0', 'scoreboard players set #built ractive 1', f'function {NS}:controls',
            tell('QA: matching sealed rooms. Left heater is fueled, right is unlit. Both chunks stay loaded from the outside platform; no force-loading.')]),
        'controls': '\n'.join([GUARD,
            row([button('[Lit left]', 'left'), button('[Unlit right]', 'right'), button('[Read cached rooms]', 'read')]),
            row([button('[Start idle check]', 'start'), button('[Heater off]', 'off'), button('[Relight]', 'relight')]),
            tell('First visit/read both rooms and note their IDs (35 cells / 72 faces). Then Start idle check; stay outside for 800 loaded ticks (40 seconds). Read cached rooms does not renew them.'),
            tell('After READY: the lit test room (anchor -4,65,0) should remain active; the unlit test room (anchor 1,65,0) should be absent. Natural caves and the observer room are separate records.'),
            tell('Heater off repeats the wait: both TEST room records should be absent. Relight should discover the left test room without entry. Global totals may include other rooms.'),
            tell('This checks room activity only. Stored room heat and thermostat blocks are still pending.')]),
        'read': action(['fd world rooms'], 'Inspect only the test-room anchors: left -4,65,0; right 1,65,0. This read does not discover or renew any room.'),
        'left': action(['tp @s -1.5 65 .5 180 0'], 'Read the lit room and note its ID.'),
        'right': action(['tp @s 2.5 65 .5 180 0'], 'Read the unlit room and note its ID.'),
        'start': action(['tp @s .5 65 8.5 180 0', 'scoreboard players set #timer ractive 0', 'scoreboard players set #stage ractive 1'],
                        'Stay outside. Counted wait is 800 unpaused loaded ticks; no room queries are issued by this timer.'),
        'ready': action(['scoreboard players set #stage ractive 3'], 'READY: Read cached rooms. Fueled check: left test room remains active, right absent. Heater-off check: both test rooms absent. Other world rooms do not count.'),
        'off': action(['data merge block -3 66 0 {BurnTime:0}', f'function {NS}:start'], 'Heater fuel removed in this QA fixture. After READY, both TEST room records should have expired; ignore other world rooms.'),
        'relight': action(['tp @s .5 65 8.5 180 0', 'data merge block -3 66 0 {BurnTime:960000}'],
                          'Heater fueled again. Wait 2 seconds and Read cached rooms: the left test room should be active without entering it.')
    }


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True, type=Path)
    parser.add_argument('--destination', required=True, type=Path)
    args = parser.parse_args()
    seed.TITLE = 'Room Activity Check'
    seed.PACK = 'room-activity-check'
    seed.NS = NS
    seed.scripts = scripts
    seed.prepare(args.source, args.destination)

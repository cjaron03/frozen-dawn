#!/usr/bin/env python3
"""Fresh Uplink Check: the owner builds and runs an ORSA Uplink from parts.
A surface pad holds a CFU-7 reactor already Binder-linked to a relay and a Power Monitor. A roofed shed east of
the relay is where the console goes; the open pad beyond it is where the dish goes. The owner receives the salvage
parts and recipe materials, crafts the console and dish, places them, and links the console with the Binder.
Phase 6 late and paused, so Maeve is active and all three archive fragments are reachable. Creative.
Never overwrites an existing save. Usage (lab client, run-lab/ directory):
  python3 tools/prepare_uplink_playtest.py --source <any level.dat> --destination "run-lab/saves/Uplink Check"
"""
import argparse
import gzip
import io
import json
from pathlib import Path

import prepare_blind_architect_playtest as nbt
from prepare_power_network_playtest import prepare, pack_only
from prepare_hearthstone_playtest import reactor, RODS, as_long

TITLE = 'Uplink Check'
NS = 'uplink_check'
PACK = 'uplink-check'
GUARD = 'execute unless score #built uplinkcheck matches 1 run return 0'
BASE = 'tp @s 0.5 65 3.5 -90 10'
SHED = 'tp @s 3.5 65 0.5 -90 10'

# Reactor core, its controller and coolant ports; radiators sit outside to the north.
CORE = (-3, 66, -3)
CONTROLLER, PORTS = (-3, 66, -2), ((-4, 66, -3), (-2, 66, -3))
RADIATORS = [(x, 64, -10) for x in range(-8, 4)]
RELAY = (0, 68, 2)
MONITOR = (0, 66, 5)  # on the deepslate wall south of it


def links():
    result = [(PORTS[0], r) for r in RADIATORS[:6]] + [(PORTS[1], r) for r in RADIATORS[6:]]
    result += [(CONTROLLER, RELAY), (RELAY, MONITOR)]
    for a, b in result:
        assert sum((p - q) ** 2 for p, q in zip(a, b)) <= 16 ** 2, (a, b)
    return result


def network_dat(data_version):
    entries = [{'a': (4, as_long(*a)), 'b': (4, as_long(*b))} for a, b in links()]
    data = {'version': (3, 1), 'links': (9, (10, entries))}
    return b'\x0a' + nbt.utf('') + nbt.write(10, {'data': (10, data), 'DataVersion': (3, data_version)})


def tell(text, function=None, color='aqua', command=None):
    value = {'text': text, 'color': color}
    if function:
        value['clickEvent'] = {'action': 'run_command', 'value': '/function ' + NS + ':' + function}
    if command:
        value['clickEvent'] = {'action': 'run_command', 'value': command}
    return 'tellraw @s ' + json.dumps(value)


def site():
    return (['fill -10 64 -12 16 64 10 minecraft:polished_deepslate',
             'fill -1 65 6 1 67 6 minecraft:deepslate_tiles',          # monitor wall
             'setblock 0 66 5 frozendawn:power_monitor[facing=north]',
             'setblock 0 68 2 frozendawn:power_relay', 'fill 0 65 2 0 67 2 minecraft:iron_bars',  # relay on a post
             # Roofed shed for the console: open sides, solid roof. A dish placed under it has no open sky.
             'fill 3 68 -3 7 68 3 minecraft:smooth_stone_slab[type=top]',
             'fill 3 65 -3 3 67 -3 minecraft:iron_bars', 'fill 7 65 -3 7 67 -3 minecraft:iron_bars',
             'fill 3 65 3 3 67 3 minecraft:iron_bars', 'fill 7 65 3 7 67 3 minecraft:iron_bars',
             # Open-sky dish pad east of the shed, edged in cyan so it reads from the shed.
             'fill 9 64 -2 13 64 2 minecraft:smooth_stone', 'fill 9 64 -2 13 64 -2 minecraft:cyan_terracotta',
             'fill 9 64 2 13 64 2 minecraft:cyan_terracotta',
             'setblock -6 65 4 minecraft:crafting_table']
            + reactor(CORE, RODS)
            + ['fill -8 64 -10 3 64 -10 frozendawn:reactor_radiator'])


def scripts():
    setup = ['execute unless score #built uplinkcheck matches 0 run return 0',
             'scoreboard players set #built uplinkcheck -1', 'gamemode creative @s',
             'gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false',
             'gamerule doFireTick false', 'gamerule keepInventory true', 'gamerule announceAdvancements false',
             'time set noon', 'weather clear', 'fd world set phase 6 late', 'fd world pause',
             'forceload add -16 -16 24 16']
    setup += site()
    setup += ['give @s frozendawn:conduit_binder', 'give @s frozendawn:signal_processor', 'give @s frozendawn:feed_horn 2',
              'give @s minecraft:copper_ingot 4', 'give @s minecraft:glass_pane 2', 'give @s minecraft:iron_ingot 16',
              'give @s minecraft:redstone_block', 'give @s minecraft:iron_block 2', 'give @s minecraft:crafting_table',
              'effect give @s minecraft:night_vision infinite 0 true',
              'setworldspawn 0 65 3', 'spawnpoint @s 0 65 3', BASE,
              'scoreboard players set #built uplinkcheck 1', 'function ' + NS + ':controls', 'return 1']
    result = {
        'load': 'scoreboard objectives add uplinkcheck dummy\nexecute unless score #built uplinkcheck matches -1..1 run scoreboard players set #built uplinkcheck 0\nexecute if score #built uplinkcheck matches 1 as @a run function ' + NS + ':controls',
        'tick': 'execute if score #built uplinkcheck matches 0 as @a[limit=1] run function ' + NS + ':setup',
        'setup': '\n'.join(setup),
        'controls': '\n'.join([GUARD,
            tell('UPLINK CHECK - Creative, Phase 6 late, paused. The reactor, relay and Power Monitor are already linked. You build the Uplink.'),
            tell('[1 Build]', 'build'), tell('[2 Listen]', 'listen'), tell('[3 Transmit]', 'transmit'),
            tell('[4 Next day]', 'next_day'), tell('[5 Supply drop]', 'drop'), tell('[6 Abort]', 'abort'),
            tell('[Uplink dump]', command='/fd debug uplink dump', color='gray'), tell('[Base]', 'base', 'gray'), 'return 1'])}
    steps = {
        'build': (BASE, ['Start the reactor: open the CFU-7 Controller (north-west) and drag Rod insertion to 100%. The Power Monitor is on the deepslate wall.',
                         'At the crafting table, craft the Uplink Console (Signal Processor) and the Uplink Dish (Feed Horn). Recipes are in the recipe book.',
                         'Place the console under the roofed shed east of the relay. With the Binder, use the console, then the relay on its post.',
                         'Place the dish on the cyan-edged pad, within 8 blocks of the console. Try it under the shed roof first to see the no-sky refusal.']),
        'listen': (SHED, ['Open the console and press Listen (2 kW). The first broadcast arrives in a few seconds, then every 90 s, in chat within 12 blocks and in the console log.',
                          'The front screen turns cyan while listening and powered.']),
        'transmit': (SHED, ['Press Transmit: it asks SIGNAL DETECTABLE. TRANSMIT? first. Confirm. 40 kW for 3 minutes; the Power Monitor shows the draw.',
                            'Stay within 32 blocks: the printout (Archive 1 of 3) goes into your inventory. Press Transmit again: it refuses until tomorrow.']),
        'next_day': (SHED, ['Click [Sprint one day] below to pass the transmitter cooldown (24000 ticks). The world stays paused at Phase 6 late.',
                            'Then transmit again. With Maeve active, Archive 2 comes next; Archive 3 (the reply) after it. Try Boost: 60 kW, 90 s.']),
        'drop': (SHED, ['After all three fragments, a transmission prints a Resupply Plot or a Signal Plot. Hold the map: the dashed circle is the search area.',
                        'Fly there. The wreck is built once you get close enough to load it. Within 30 blocks the locator beacon pings and gives distance and sector.']),
        'abort': (SHED, ['Start a transmission, then SCRAM the reactor. Within a couple of seconds the console aborts with no reward.',
                         'The cooldown is still spent and the signal is still recorded: check [Uplink dump].']),
        'base': (BASE, [])}
    for name, (where, lines) in steps.items():
        body = [GUARD, where, *(tell(line) for line in lines)]
        if name == 'next_day':  # tick needs operator level 3, which functions lack: offer the click instead
            body.append(tell('[Sprint one day]', command='/tick sprint 24000t', color='yellow'))
        result[name] = '\n'.join([*body, 'return 1'])
    return result


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source', type=Path); p.add_argument('--destination', type=Path); p.add_argument('--pack-only', type=Path)
    a = p.parse_args()
    if a.pack_only:
        pack_only(a.pack_only, title=TITLE, namespace=NS, build_scripts=scripts); return
    if not (a.source and a.destination):
        p.error('Use --source/--destination or --pack-only')
    prepare(a.source, a.destination, title=TITLE, namespace=NS, pack=PACK, build_scripts=scripts, surface=128, structures=False)
    level = io.BytesIO(gzip.decompress((a.destination / 'level.dat').read_bytes())); level.read(1); nbt.string(level)
    version = nbt.read(level, 10)['Data'][1]['DataVersion'][1]
    (a.destination / 'data/frozendawn_power_network.dat').write_bytes(gzip.compress(network_dat(version)))
    meta = json.loads((a.destination / 'preparation.json').read_text())
    meta['prelinked'] = len(links())
    (a.destination / 'preparation.json').write_text(json.dumps(meta, indent=2) + '\n')


if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""Fresh Uplink Field Check: the ORSA Uplink in an ordinary Overworld with real monitoring stations and reactor sites.
Creative, late Phase 6, progression paused. On first join the owner is placed on the surface and given a kit chest:
a CFU-7 reactor kit (casing, core, rod drive, ports, controller, radiators, rods), relays, a Power Monitor, a Battery
Bank with cells, the Binder and the Uplink parts. The owner builds the base; transmissions then print real plots to
stations and reactor sites. Never overwrites an existing save. Usage (lab client, run-lab/ directory):
  python3 tools/prepare_uplink_world_playtest.py --source <any level.dat> --destination "run-lab/saves/Uplink Field Check"
"""
import argparse
import json
from pathlib import Path

import prepare_blind_architect_playtest as nbt
from prepare_power_network_playtest import prepare, pack_only

TITLE = 'Uplink Field Check'
NS = 'uplink_field'
PACK = 'uplink-field'
GUARD = 'execute unless score #built uplinkfield matches 1 run return 0'


def tell(text, function=None, color='aqua', command=None):
    value = {'text': text, 'color': color}
    if function:
        value['clickEvent'] = {'action': 'run_command', 'value': '/function ' + NS + ':' + function}
    if command:
        value['clickEvent'] = {'action': 'run_command', 'value': command}
    return 'tellraw @s ' + json.dumps(value)


def kit():
    """Kit chests beside the player: the reactor and grid in one, the Uplink in the other."""
    def stack(slot, item, count=1, components=''):
        return '{Slot:%db,id:"%s",count:%d%s}' % (slot, item, count, components)
    reactor = [('frozendawn:reactor_casing', 22), ('frozendawn:reactor_core', 1), ('frozendawn:control_rod_drive', 1),
               ('frozendawn:coolant_port', 2), ('frozendawn:reactor_controller', 1), ('frozendawn:reactor_radiator', 16),
               ('frozendawn:fuel_rod', 8), ('frozendawn:power_relay', 4), ('frozendawn:power_monitor', 1),
               ('frozendawn:battery_bank', 1), ('frozendawn:conduit_binder', 1), ('frozendawn:geiger_counter', 1)]
    uplink = [('frozendawn:signal_processor', 1), ('frozendawn:feed_horn', 2), ('minecraft:copper_ingot', 8),
              ('minecraft:glass_pane', 4), ('minecraft:iron_ingot', 16), ('minecraft:redstone_block', 2),
              ('minecraft:iron_block', 2), ('minecraft:crafting_table', 1), ('frozendawn:power_cell', 4)]
    unstackable = {'frozendawn:fuel_rod', 'frozendawn:power_cell', 'frozendawn:conduit_binder', 'frozendawn:geiger_counter'}
    def items(entries):
        slots = []
        for item, n in entries:
            slots += [(item, 1)] * n if item in unstackable else [(item, n)]
        assert len(slots) <= 27, len(slots)
        return ','.join(stack(i, item, n) for i, (item, n) in enumerate(slots))
    a, b = items(reactor), items(uplink)
    return [f'execute at @s run setblock ~2 ~ ~ minecraft:chest[facing=west]{{Items:[{a}]}}',
            f'execute at @s run setblock ~2 ~ ~1 minecraft:chest[facing=west]{{Items:[{b}]}}']


def scripts():
    setup = ['execute unless score #built uplinkfield matches 0 run return 0',
             'scoreboard players set #built uplinkfield -1', 'gamemode creative @s',
             'gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false',
             'gamerule doFireTick false', 'gamerule keepInventory true', 'gamerule announceAdvancements false',
             'time set noon', 'weather clear', 'fd world set phase 6 late', 'fd world pause',
             # Land on the surface wherever spawn is; the base is the owner's to place.
             'spreadplayers 0 0 0 16 false @s', 'execute at @s run setworldspawn ~ ~ ~', 'execute at @s run spawnpoint @s ~ ~ ~',
             'effect give @s minecraft:night_vision infinite 0 true']
    setup += kit()
    setup += ['scoreboard players set #built uplinkfield 1', 'function ' + NS + ':controls', 'return 1']
    result = {
        'load': 'scoreboard objectives add uplinkfield dummy\nexecute unless score #built uplinkfield matches -1..1 run scoreboard players set #built uplinkfield 0\nexecute if score #built uplinkfield matches 1 as @a run function ' + NS + ':controls',
        'tick': 'execute if score #built uplinkfield matches 0 as @a[limit=1] run function ' + NS + ':setup',
        'setup': '\n'.join(setup),
        'controls': '\n'.join([GUARD,
            tell('UPLINK FIELD CHECK - real terrain, Creative, Phase 6 late, paused. Two kit chests are beside you: reactor and grid, and the Uplink.'),
            tell('[1 Base]', 'base'), tell('[2 Transmit]', 'transmit'), tell('[3 Plots]', 'plots'),
            tell('[Sprint one day]', command='/tick sprint 24000t', color='yellow'),
            tell('[Uplink dump]', command='/fd debug uplink dump', color='gray'),
            tell('[Epigraph preview]', command='/fdpreview epigraph', color='gray'), 'return 1'])}
    steps = {
        'base': ['Build the CFU-7: a 3x3x3 casing block with the core at its centre, the rod drive at the centre of the top, coolant ports at the centres of two opposite sides and the controller at the centre of a third side.',
                 'Put the radiators outside under open sky within 16 blocks and Binder-link each to a port. Load the rods, then link the controller to a relay.',
                 'Craft and place the Uplink console and dish (dish under open sky, within 8 blocks), and link the console to the relay.',
                 'On the controller, turn on Follow and set Reserve to taste. A Battery Bank with the four cells smooths the 40 kW step.'],
        'transmit': ['Press Transmit. While the SIGNAL DETECTABLE prompt is up, the reactor ramps toward the extra draw; the prompt shows Grid ramping up until it reads Grid ready.',
                     'Confirm. Right-click the printed Relay Transcript to archive it into the ORSA Field Manual, under ORSA Intelligence, Relay Archive.'],
        'plots': ['After the three transcripts, transmissions print Resupply and Signal Plots. Signal Plots point at real, unvisited monitoring stations and reactor sites.',
                  'Fly to the circle. Stations are built as you load them; resupply drops have a locator beacon within 30 blocks.']}
    for name, lines in steps.items():
        result[name] = '\n'.join([GUARD, *(tell(line) for line in lines), 'return 1'])
    return result


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source', type=Path); p.add_argument('--destination', type=Path); p.add_argument('--pack-only', type=Path)
    a = p.parse_args()
    if a.pack_only:
        pack_only(a.pack_only, title=TITLE, namespace=NS, build_scripts=scripts); return
    if not (a.source and a.destination):
        p.error('Use --source/--destination or --pack-only')
    prepare(a.source, a.destination, title=TITLE, namespace=NS, pack=PACK, build_scripts=scripts, terrain=True)
    # Advancements stay filtered in this world, except the invisible Relay Archive unlocks the Field Manual needs.
    meta = nbt.pack_metadata(TITLE)
    meta['filter']['block'] = [{'path': 'advancement/(?!relay_archive/).*'}]
    (a.destination / 'datapacks' / PACK / 'pack.mcmeta').write_text(json.dumps(meta) + '\n')


if __name__ == '__main__':
    main()

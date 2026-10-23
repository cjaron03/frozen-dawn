#!/usr/bin/env python3
"""Fresh Merge Check: the reactor/radiation branch merged with maeve-director's power and airlock systems.
One sealed surface base holds a running CFU-7, a powered airlock, a decontamination shower and other loads on one
pre-linked network with a Power Monitor; an obsidian meltdown rig 40 blocks east checks where fallout spreads.
Never overwrites an existing save. Usage (normal Gradle client, run/ directory):
  python3 tools/prepare_merge_check_playtest.py --source <any level.dat> --destination "run/saves/Merge Check"
"""
import argparse
import gzip
import io
import json
from pathlib import Path

import prepare_blind_architect_playtest as nbt
from prepare_power_network_playtest import prepare, pack_only
from prepare_hearthstone_playtest import reactor, RODS, as_long

TITLE = 'Merge Check'
NS = 'merge_check'
PACK = 'merge-check'
GUARD = 'execute unless score #built mergecheck matches 1 run return 0'
Y = 65  # interior floor; the flat world's ground is y 63, shells sit on y 64
BASE = 'tp @s 0.5 65 3.5 180 10'
RIG = 'tp @s 40.5 65 4.5 180 10'
OPEN_ANNEX, SEALED_ANNEX = 'tp @s 49.5 65 0.5 90 10', 'tp @s 31.5 65 0.5 -90 10'

# Base: core of reactor A, its controller, ports, radiators outside to the north, loads on one relay.
CORE = (-3, 66, -3)
CONTROLLER, PORTS = (-3, 66, -2), ((-4, 66, -3), (-2, 66, -3))
RADIATORS = [(x, 64, -10) for x in range(-8, 4)]
RELAY = (0, 68, 2)
AIRLOCK_CONTROLLER = (6, 66, -1)  # on the shared wall beside the inner door: the powered cycler
SHOWER, TANK = (3, 69, -3), (4, 69, -3)
LOADS = [AIRLOCK_CONTROLLER, SHOWER, (0, 66, 5), (2, 65, 4), (-2, 65, 4), (-4, 69, 4), (4, 69, 4)]
RIG_CORE, RIG_CONTROLLER = (40, 66, -3), (40, 66, -2)


def links():
    result = [(PORTS[0], r) for r in RADIATORS[:6]] + [(PORTS[1], r) for r in RADIATORS[6:]]
    result += [(CONTROLLER, RELAY)] + [(RELAY, load) for load in LOADS]
    for a, b in result:
        assert sum((p - q) ** 2 for p, q in zip(a, b)) <= 16 ** 2, (a, b)
    return result


def network_dat(data_version):
    entries = [{'a': (4, as_long(*a)), 'b': (4, as_long(*b))} for a, b in links()]
    data = {'version': (3, 1), 'links': (9, (10, entries))}
    return b'\x0a' + nbt.utf('') + nbt.write(10, {'data': (10, data), 'DataVersion': (3, data_version)})


def tell(text, function=None, color='aqua'):
    value = {'text': text, 'color': color}
    if function:
        value['clickEvent'] = {'action': 'run_command', 'value': '/function ' + NS + ':' + function}
    return 'tellraw @s ' + json.dumps(value)


def box(x1, y1, z1, x2, y2, z2, shell):
    return [f'fill {x1} {y1} {z1} {x2} {y2} {z2} {shell} hollow']


def base():
    door = 'frozendawn:airlock_door[facing=east,half=%s]'
    return (box(-6, 64, -6, 6, 70, 6, 'frozendawn:insulated_glass')
            + [f'fill -6 64 -6 6 64 6 minecraft:polished_deepslate']
            # Airlock chamber (3x3x3 inside) east of the base, sharing its wall; Codex's authored geometry.
            + box(6, 64, -2, 10, 68, 2, 'frozendawn:insulated_glass') + ['fill 6 64 -2 10 64 2 minecraft:polished_deepslate']
            + [f'setblock 6 65 0 {door % "lower"}', f'setblock 6 66 0 {door % "upper"}',
               f'setblock 10 65 0 {door % "lower"}', f'setblock 10 66 0 {door % "upper"}',
               'setblock 8 66 -2 frozendawn:airlock_controller[facing=south]',
               'setblock 6 66 -1 frozendawn:airlock_controller[facing=east]',
               'setblock 8 66 2 frozendawn:manual_vent_valve[facing=north]', 'fill 11 64 -1 12 64 1 minecraft:smooth_stone']
            + reactor(CORE, RODS)
            + ['fill -8 64 -10 3 64 -10 frozendawn:reactor_radiator',
               'setblock 0 68 2 frozendawn:power_relay', 'setblock 3 69 -3 frozendawn:decon_shower', 'setblock 4 69 -3 minecraft:packed_ice',
               'setblock 0 66 5 frozendawn:power_monitor[facing=north]', 'setblock 2 65 4 frozendawn:induction_furnace[facing=north]',
               'setblock -2 65 4 frozendawn:resistive_heater[facing=north]', 'setblock -4 69 4 frozendawn:panel_light',
               'setblock 4 69 4 frozendawn:panel_light', 'setblock -5 65 4 frozendawn:geothermal_core'])


def rig():
    # Obsidian shell survives the hydrogen blast: open annex east (shared doorway), sealed annex west (solid wall).
    return (box(34, 64, -6, 46, 70, 6, 'minecraft:obsidian') + box(46, 64, -3, 52, 68, 3, 'minecraft:obsidian')
            + box(28, 64, -3, 34, 68, 3, 'minecraft:obsidian')
            + ['fill 46 65 -1 46 67 1 minecraft:air', 'fill 47 65 -2 51 67 2 minecraft:air', 'fill 29 65 -2 33 67 2 minecraft:air',
               'setblock 37 65 4 frozendawn:geothermal_core']
            + reactor(RIG_CORE, RODS))


def scripts():
    setup = ['execute unless score #built mergecheck matches 0 run return 0',
             'scoreboard players set #built mergecheck -1', 'gamemode creative @s',
             'gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false',
             'gamerule doFireTick false', 'gamerule keepInventory true', 'gamerule announceAdvancements false',
             'time set noon', 'weather clear', 'fd world set phase 6 late', 'fd world pause',
             'forceload add -16 -16 56 16']
    setup += base() + rig()
    setup += ['give @s frozendawn:conduit_binder', 'give @s frozendawn:geiger_counter', 'give @s frozendawn:shielded_cask',
              'give @s frozendawn:o2_tank_mk3 2', 'give @s minecraft:raw_iron 32', 'give @s frozendawn:fuel_rod 4',
              'give @s frozendawn:eva_helmet', 'give @s frozendawn:eva_chestplate', 'give @s frozendawn:eva_leggings', 'give @s frozendawn:eva_boots',
              'effect give @s minecraft:night_vision infinite 0 true',
              'setworldspawn 0 65 3', 'spawnpoint @s 0 65 3', BASE,
              'scoreboard players set #built mergecheck 1', 'function ' + NS + ':controls', 'return 1']
    result = {
        'load': 'scoreboard objectives add mergecheck dummy\nexecute unless score #built mergecheck matches -1..1 run scoreboard players set #built mergecheck 0\nexecute if score #built mergecheck matches 1 as @a run function ' + NS + ':controls',
        'tick': 'execute if score #built mergecheck matches 0 as @a[limit=1] run function ' + NS + ':setup',
        'setup': '\n'.join(setup),
        'controls': '\n'.join([GUARD,
            tell('MERGE CHECK - Creative, paused late vacuum. One base network: CFU-7, airlock, shower, furnace, heater, lights and monitor are pre-linked.'),
            tell('[1 Airlock]', 'airlock'), tell('[2 Reactor hum]', 'hum'), tell('[3 Shared network]', 'network'),
            tell('[4 Fallout spread]', 'fallout'), tell('[Base]', 'base', 'gray'), tell('[Rig]', 'rig', 'gray'),
            tell('[Open annex]', 'open_annex', 'gray'), tell('[Sealed annex]', 'sealed_annex', 'gray'), 'return 1'])}
    steps = {
        'airlock': (BASE, ['Start the reactor first (step 2) so the cycler has power. The powered controller is on the east wall beside the inner door; the chamber is beyond it.',
                           'Close both doors and click the controller: recover air, pressurize, cycle out and back in. Listen for the recorded machinery; doors interlock while it runs.',
                           'Shift-click reads status. The manual valve on the chamber south wall equalizes the inner door.']),
        'hum': (BASE, ['Open the CFU-7 Controller in the base and drag Rod insertion to 100%. The hum rises with power; radiators outside glow.',
                       'Walk out through the airlock and back, and fly 40 blocks away and back: one hum only, never stacked. The klaxon is checked at the rig (step 4).']),
        'network': (BASE, ['With the reactor at power, everything runs. Read the Power Monitor on the south wall.',
                           'Now press SCRAM. Stand under the shower (north-east corner, packed-ice tank) and start an airlock cycle.',
                           'The monitor lists what is waiting for power by name: Airlock Controller and Decontamination Shower, never one shown as the other.',
                           'Reset and restart the reactor: both resume. The cycler and the shower each draw only while in use.']),
        'fallout': (RIG, ['Obsidian rig with air from its Geothermal Core (check the air readout first). Open annex east through a doorway; sealed annex west behind solid obsidian.',
                          'Disable auto-SCRAM on its Controller, insertion 100%, step out of line of sight. Klaxon at 350C, once, not stacked; then the runaway, flash and hydrogen blast.',
                          'Afterwards: fallout fills the rig and the open annex, about 12 blocks. The sealed annex stays clean. Use [Open annex] and [Sealed annex] to look, with the Geiger Counter.']),
        'base': (BASE, []), 'rig': (RIG, []), 'open_annex': (OPEN_ANNEX, []), 'sealed_annex': (SEALED_ANNEX, [])}
    for name, (where, lines) in steps.items():
        result[name] = '\n'.join([GUARD, where, *(tell(line) for line in lines), 'return 1'])
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

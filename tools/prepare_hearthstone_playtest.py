#!/usr/bin/env python3
"""Fresh Hearthstone Check: two pre-linked CFU-7 reactors, a load network and a build kit.
Never overwrites an existing save. Usage (normal Gradle client, run/ directory):
  python3 tools/prepare_hearthstone_playtest.py --source <any level.dat> --destination "run/saves/Hearthstone Check"
"""
import argparse
import gzip
import io
import json
from pathlib import Path

import prepare_blind_architect_playtest as nbt
from prepare_power_network_playtest import prepare, pack_only

TITLE = 'Hearthstone Check'
NS = 'hearthstone_check'
PACK = 'hearthstone-check'
GUARD = 'execute unless score #built hearthstone matches 1 run return 0'
HALL = 'tp @s 7.5 52 9.5 180 10'
FIELD = 'tp @s 4.5 65 8.5 180 25'
ROD = '{Slot:%db,id:"frozendawn:fuel_rod",count:1}'
RODS = '{Items:[' + ','.join(ROD % i for i in range(4)) + ']}'

# Station A: core 4,54,4. Station B (drill): core 11,54,4. Controllers face south into the hall.
A_PORTS = ((3, 54, 4), (5, 54, 4))
B_PORTS = ((10, 54, 4), (12, 54, 4))
A_RADIATORS = [(x, 64, 4) for x in range(-1, 11)]
B_RADIATORS = [(x, 64, -1) for x in range(9, 13)]
A_CONTROLLER, RELAY = (4, 54, 5), (7, 52, 10)
LOADS = [(2, 58, 10), (7, 58, 10), (12, 58, 10), (9, 52, 13), (5, 52, 13), (12, 52, 12), (7, 54, 14), (1, 52, 9)]


def links():
    result = [(A_PORTS[0], r) for r in A_RADIATORS[:6]] + [(A_PORTS[1], r) for r in A_RADIATORS[6:]]
    result += [(B_PORTS[0], r) for r in B_RADIATORS[:2]] + [(B_PORTS[1], r) for r in B_RADIATORS[2:]]
    result += [(A_CONTROLLER, RELAY)] + [(RELAY, load) for load in LOADS]
    for a, b in result:
        assert sum((p - q) ** 2 for p, q in zip(a, b)) <= 16 ** 2, (a, b)
    return result


def as_long(x, y, z):
    value = ((x & 0x3FFFFFF) << 38) | ((z & 0x3FFFFFF) << 12) | (y & 0xFFF)
    return value - (1 << 64) if value >= 1 << 63 else value


def network_dat(data_version):
    entries = [{'a': (4, as_long(*a)), 'b': (4, as_long(*b))} for a, b in links()]
    data = {'version': (3, 1), 'links': (9, (10, entries))}
    return b'\x0a' + nbt.utf('') + nbt.write(10, {'data': (10, data), 'DataVersion': (3, data_version)})


def tell(text, function=None, color='aqua'):
    value = {'text': text, 'color': color}
    if function:
        value['clickEvent'] = {'action': 'run_command', 'value': '/function ' + NS + ':' + function}
    return 'tellraw @s ' + json.dumps(value)


def reactor(core, controller_rods):
    x, y, z = core
    return [f'fill {x-1} {y-1} {z-1} {x+1} {y+1} {z+1} frozendawn:reactor_casing',
            f'setblock {x} {y} {z} frozendawn:reactor_core',
            f'setblock {x} {y+1} {z} frozendawn:control_rod_drive',
            f'setblock {x-1} {y} {z} frozendawn:coolant_port',
            f'setblock {x+1} {y} {z} frozendawn:coolant_port',
            f'setblock {x} {y} {z+1} frozendawn:reactor_controller[facing=south]' + controller_rods]


def scripts():
    setup = ['execute unless score #built hearthstone matches 0 run return 0',
             'scoreboard players set #built hearthstone -1', 'gamemode creative @s',
             'gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false',
             'gamerule doFireTick false', 'gamerule keepInventory true', 'gamerule announceAdvancements false',
             'time set noon', 'weather clear', 'fd world set phase 6 late', 'fd world pause',
             # Sealed hall just under the surface; radiators sit on the surface within coolant reach.
             'fill 0 52 0 14 58 14 minecraft:air', 'fill 0 51 0 14 51 14 minecraft:polished_deepslate',
             'fill -1 53 12 -1 54 13 frozendawn:deepslate_uranium_ore']
    setup += reactor((4, 54, 4), RODS) + reactor((11, 54, 4), RODS)
    setup += ['fill -1 64 4 10 64 4 frozendawn:reactor_radiator', 'fill 9 64 -1 12 64 -1 frozendawn:reactor_radiator',
              'fill -1 63 3 10 63 5 minecraft:polished_deepslate', 'fill 9 63 -2 12 63 0 minecraft:polished_deepslate',
              'setblock 7 52 10 frozendawn:power_relay',
              'setblock 2 58 10 frozendawn:panel_light', 'setblock 7 58 10 frozendawn:panel_light', 'setblock 12 58 10 frozendawn:panel_light',
              'setblock 9 52 13 frozendawn:induction_furnace[facing=north]', 'setblock 5 52 13 frozendawn:battery_bank',
              'setblock 12 52 12 frozendawn:resistive_heater[facing=west]', 'setblock 7 54 14 frozendawn:power_monitor[facing=north]',
              'setblock 1 52 9 frozendawn:resistive_heater[facing=east]', 'setblock 13 52 2 frozendawn:geothermal_core',
              'give @s frozendawn:conduit_binder', 'give @s frozendawn:reactor_casing 22', 'give @s frozendawn:reactor_core',
              'give @s frozendawn:control_rod_drive', 'give @s frozendawn:coolant_port 2', 'give @s frozendawn:reactor_controller',
              'give @s frozendawn:reactor_radiator 12', 'give @s frozendawn:fuel_rod 4', 'give @s frozendawn:raw_uranium 16',
              'give @s frozendawn:uranium_pellet 8', 'give @s minecraft:raw_iron 64', 'give @s minecraft:snow 16',
              'give @s minecraft:redstone_block 2', 'effect give @s minecraft:night_vision infinite 0 true',
              'setworldspawn 7 52 9', 'spawnpoint @s 7 52 9', HALL,
              'scoreboard players set #built hearthstone 1', 'function ' + NS + ':controls', 'return 1']
    result = {
        'load': 'scoreboard objectives add hearthstone dummy\nexecute unless score #built hearthstone matches -1..1 run scoreboard players set #built hearthstone 0\nexecute if score #built hearthstone matches 1 as @a run function ' + NS + ':controls',
        'tick': 'execute if score #built hearthstone matches 0 as @a[limit=1] run function ' + NS + ':setup',
        'setup': '\n'.join(setup),
        'controls': '\n'.join([GUARD,
            tell('HEARTHSTONE CHECK - Creative, paused late vacuum. Hearthstone A (left) feeds the hall grid; Hearthstone B (right) is the drill reactor.'),
            tell('[1 Start A]', 'start'), tell('[2 Load-follow]', 'follow'), tell('[3 Radiators]', 'radiators'),
            tell('[4 Auto-SCRAM on B]', 'scram'), tell('[5 Meltdown drill on B]', 'meltdown'), tell('[6 Build your own]', 'build'),
            tell('[7 Fuel chain]', 'fuel'), tell('[8 Save/reopen]', 'persistence'),
            tell('[Hall]', 'hall', 'gray'), tell('[Radiator field]', 'field', 'gray'), 'return 1'])}
    descriptions = {
        'start': (HALL, ['Hearthstone A is the left reactor: 4 fresh rods, 12 linked radiators on the surface. Open its Controller and drag Rod insertion to 100%.',
                         'Core climbs to about 270C over a few minutes. Heat removed catches up with heat in; power out approaches about 120 kW. Radiators above start to glow.',
                         'The relay feeds three panel lights, the induction furnace, two RH-5 heaters and the battery bank. Read the Power Monitor on the back wall.',
                         'Lower insertion: heat in drops at once, the core settles cooler. SCRAM drops to decay heat (7%), fading over a day. Reset works below 450C, or at any temperature with automatic SCRAM disabled.']),
        'follow': (HALL, ['On A, click Follow: On. Insertion now tracks network demand, plus a 5 kW charging margin while the bank is not full, minus other generators.',
                          'Put raw iron in the induction furnace (2 kW) and watch insertion step up. It never asks for more heat than the radiators reject at 300C.',
                          'Dragging the slider returns to manual. Rods burn in proportion to insertion, so load-follow stretches rod life.']),
        'radiators': (FIELD, ['Blue beams are coolant links (port to radiator only); cyan beams are power. Each radiator needs one uncovered outdoor face.',
                              'Roof or bury a radiator in snow while A runs: Radiators count and heat removed drop. Running radiators glow, burn what stands on them and melt adjacent snow.']),
        'scram': (HALL, ['Hearthstone B (right) has only 4 radiators. Set its insertion to 100%. Heat in (140 kW) beats heat removed, so the core climbs.',
                         'At 350C: amber lights, klaxon, rod slots lock. At 450C automatic SCRAM trips. Decay heat continues; radiators cool it back. Redstone on the Controller also trips SCRAM.']),
        'meltdown': (HALL, ['Only on B. Click Auto: On, read both screens and hold Hold to disable for 3 s. Banner shows AUTO-SCRAM: DISABLED.',
                            'Full insertion: past 600C core damage is permanent; at 700C LOSS OF COOLANT and ESTIMATED TIME TO CORE FAILURE, about 60 s to 800C.',
                            'Press SCRAM before 800C and it always recovers. Or let it melt: the core becomes Corium, the casing scorches, ORSA signs off.']),
        'build': (HALL, ['Your inventory has a full kit. Build a 3x3x3 in the open back half of the hall:',
                         'Top: 8 casings around the Control Rod Drive. Middle: Reactor Core in the centre; on the four faces the Controller, 2 Coolant Ports and 1 casing; corners casing. Bottom: 9 casings.',
                         'The Shell line names the first wrong cell. Place radiators on the surface, then Binder: port then radiator (16 blocks).']),
        'fuel': (HALL, ['Raw Uranium refines into Uranium Concentrate only in the powered Induction Furnace: a 1,200C process at 8 kW, 30 s each. 4 concentrate craft 1 Uranium Pellet; Fuel Rod: 4 pellets, 4 iron ingots, 1 copper ingot. JEI shows all three.',
                        'Mine the deepslate uranium ore samples on the west wall: 1-2 Raw Uranium, silk touch keeps the ore. In normal worlds it only generates below Y 0.']),
        'persistence': (HALL, ['Note core temperature, insertion, Follow/Auto state and rod life. Save and Quit, reopen Hearthstone Check.',
                               'Settings, rods, core temperature and coolant links remain; heating and power resume within a second.']),
        'hall': (HALL, []), 'field': (FIELD, [])}
    for name, (where, lines) in descriptions.items():
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

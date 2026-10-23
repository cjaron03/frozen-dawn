#!/usr/bin/env python3
"""Fresh Radiation Check: separate underground stations, 24 blocks apart so their sources never overlap.
Never overwrites an existing save. Usage (normal Gradle client, run/ directory):
  python3 tools/prepare_radiation_playtest.py --source <any level.dat> --destination "run/saves/Radiation Check"
"""
import argparse
import json
from pathlib import Path

from prepare_power_network_playtest import prepare, pack_only
from prepare_hearthstone_playtest import reactor, RODS

TITLE = 'Radiation Check'
NS = 'radiation_check'
PACK = 'radiation-check'
GUARD = 'execute unless score #built radcheck matches 1 run return 0'
Y = 52
STATIONS = {'briefing': 0, 'rods': 24, 'ice': 48, 'corium': 72, 'fallout': 96, 'ore': 120, 'meltdown': 144}
VACUUM_X = 172
# Hide every advancement except the reactor and radiation ones under test, plus their parent chain.
TESTED = ['three_point_six_roentgen', 'az_5', 'elephants_foot', 'carnot_would_be_proud', 'even_the_reactor_gets_cold',
          'war_never_changes', 'was_that_the_geiger_counter']
ADVANCEMENT_FILTER = 'advancement/(?!(' + '|'.join(['root', 'phase2', 'phase3', 'phase4', 'phase5', 'phase6'] + TESTED) + r')\.json$).*'


def pack_metadata():
    return {'pack': {'pack_format': 48, 'description': TITLE},
            # One rule: Minecraft tests a filter's namespace and path patterns independently, so rules cannot be paired.
            'filter': {'block': [{'path': ADVANCEMENT_FILTER}]}}


def tell(text, function=None, color='aqua'):
    value = {'text': text, 'color': color}
    if function:
        value['clickEvent'] = {'action': 'run_command', 'value': '/function ' + NS + ':' + function}
    return 'tellraw @s ' + json.dumps(value)


def room(x, half=4, height=5):
    return [f'fill {x-half} {Y} {-half} {x+half} {Y+height-1} {half} minecraft:air',
            f'fill {x-half} {Y-1} {-half} {x+half} {Y-1} {half} minecraft:polished_deepslate']


def station_blocks():
    """What each station builds, run once on the first visit (its chunks are only loaded once the player is near)."""
    r, i, c, f, o, m = (STATIONS[k] for k in ('rods', 'ice', 'corium', 'fallout', 'ore', 'meltdown'))
    rod = '{id:"frozendawn:spent_fuel_rod",count:1}'
    return {
        'rods': room(r) + [
            f'setblock {r} {Y} -3 minecraft:chest[facing=south]{{Items:[{{Slot:13b,id:"frozendawn:spent_fuel_rod",count:2}}]}}',
            f'summon minecraft:item {r+3} {Y} -3 {{Item:{rod},PickupDelay:40,Age:-32768}}'],
        'ice': room(i) + [f'fill {i-2} {Y} -4 {i+2} {Y+3} 0 minecraft:packed_ice',
            f'setblock {i} {Y+1} -2 minecraft:chest[facing=south]{{Items:[{{Slot:13b,id:"frozendawn:spent_fuel_rod",count:4}}]}}'],
        'corium': room(c) + [f'fill {c-2} {Y} -4 {c+2} {Y+3} 0 minecraft:gray_concrete hollow', f'setblock {c} {Y+1} -2 frozendawn:corium',
            f'setblock {c} {Y+1} 0 minecraft:glass'],
        'fallout': room(f) + [f'fill {f-3} {Y} -3 {f+1} {Y} 1 frozendawn:fallout', f'fill {f+2} {Y-1} 1 {f+4} {Y-1} 3 minecraft:water'],
        'ore': room(o) + [f'fill {o-2} {Y} -5 {o+2} {Y+2} -5 frozendawn:deepslate_uranium_ore'],
        # A Geothermal Core fills the sealed room with air, so this is the air release and sounds carry.
        'meltdown': room(m, 7, 7) + reactor((m, Y + 1, -3), RODS) + [f'setblock {m+5} {Y} 4 frozendawn:geothermal_core'],
        'vacuum': reactor((VACUUM_X, 65, 0), RODS) + [f'fill {VACUUM_X-3} 63 -3 {VACUUM_X+3} 63 3 minecraft:polished_deepslate'],
    }


def scripts():
    builds = station_blocks()
    setup = ['execute unless score #built radcheck matches 0 run return 0', 'scoreboard players set #built radcheck -1',
             'gamemode creative @s', 'gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false',
             'gamerule doFireTick false', 'gamerule keepInventory true', 'gamerule announceAdvancements false',
             'time set noon', 'weather clear', 'fd world set phase 6 late', 'fd world pause', *room(STATIONS['briefing']),
             'give @s frozendawn:shielded_cask', 'give @s frozendawn:shielded_cask[frozendawn:cask_rods=4]',
             'give @s frozendawn:rad_aid 4', 'give @s frozendawn:geiger_counter', 'give @s minecraft:iron_shovel',
             'effect give @s minecraft:night_vision infinite 0 true',
             f'setworldspawn 0 {Y} 0', f'spawnpoint @s 0 {Y} 0', f'tp @s 0.5 {Y} 0.5 180 10',
             'scoreboard players set #built radcheck 1', 'function ' + NS + ':controls', 'return 1']
    result = {
        # Worlds built before stations became lazy already contain the first two stations.
        'load': 'scoreboard objectives add radcheck dummy\nexecute unless score #built radcheck matches -1..1 run scoreboard players set #built radcheck 0\n'
                'execute if score #built radcheck matches 1 unless score #lazy radcheck matches 1 run function ' + NS + ':migrate\n'
                'scoreboard players set #lazy radcheck 1\nexecute if score #built radcheck matches 1 as @a run function ' + NS + ':controls',
        'migrate': 'scoreboard players set #st_rods radcheck 1\nscoreboard players set #st_ice radcheck 1',
        'tick': 'execute if score #built radcheck matches 0 as @a[limit=1] run function ' + NS + ':setup',
        'setup': '\n'.join(setup),
        'controls': '\n'.join([GUARD,
            tell('RADIATION CHECK - stations 24 blocks apart. Creative shows symptoms without damage; switch to Survival to feel them.'),
            tell('[1 Spent rods]', 'rods'), tell('[2 Ice pool]', 'ice'), tell('[3 Corium]', 'corium'), tell('[4 Fallout]', 'fallout'),
            tell('[5 Ore]', 'ore'), tell('[6 Meltdown in air]', 'meltdown'), tell('[7 Meltdown in vacuum]', 'vacuum'),
            tell('[Survival]', 'survival', 'gray'), tell('[Creative]', 'creative', 'gray'), tell('[Briefing room]', 'briefing', 'gray'), 'return 1'])}
    r, i, c, f, o, m = (STATIONS[k] for k in ('rods', 'ice', 'corium', 'fallout', 'ore', 'meltdown'))
    texts = {
        'rods': ['A chest of 2 spent rods (left) and one dropped on the floor (right). Clicks race as you approach; chests do not shield.',
                 'Pick one up and the trefoil Radiation Sickness effect appears within seconds of the dose. Milk will not clear it.'],
        'ice': ['Four spent rods in a chest inside two layers of packed ice. Clicks should be far quieter than the bare chest.'],
        'corium': ['Corium in a concrete bunker. Walk around it: the glass window lets far more through than the concrete. Do not linger.'],
        'fallout': ['Grey fallout on the floor: dust drifts above it. Walk through without a sealed suit and you stay contaminated after leaving.',
                    'Wash it off in the water pool. Shovel the fallout up into Radioactive Ash to clean the floor (the ash itself is radioactive).'],
        'ore': ['A seam of uranium ore: faint, slow clicks only. Fine to mine and carry.'],
        'meltdown': ['Sealed room; its Geothermal Core fills it with air (check the air readout first). Disable auto-SCRAM, insertion 100%, then watch from the doorway with your back to the wall.',
                     'Past 780C it runs away in seconds: white flash, hydrogen blast, Reactor Debris thrown 6-18 blocks, heavy fallout. Stand in it and the dust hangs in the air and cakes your visor. Cask the debris.'],
        'vacuum': ['Same reactor on the surface, in vacuum. With no air to carry it, fallout only settles within about 3 blocks.']}
    for name, lines in texts.items():
        x = VACUUM_X if name == 'vacuum' else STATIONS[name]
        land = f'tp @a[x={x},y=60,z=2,distance=..12] {x}.5 65 6.5 180 10' if name == 'vacuum' else f'tp @a[x={x},y=60,z=2,distance=..12] {x}.5 {Y} 2.5 180 10'
        # Wait on the surface above the station; build once its chunks are loaded, then land.
        result[name] = '\n'.join([GUARD, f'tp @s {x}.5 66 2.5 180 30', *(tell(t) for t in lines),
                                   f'schedule function {NS}:arrive_{name} 30t replace', 'return 1'])
        result['arrive_' + name] = '\n'.join([f'execute unless score #st_{name} radcheck matches 1 run function {NS}:build_{name}', land])
        result['build_' + name] = '\n'.join([*builds[name], f'scoreboard players set #st_{name} radcheck 1'])
    result['briefing'] = '\n'.join([GUARD, f'tp @s 0.5 {Y} 0.5 180 10',
        tell('Clean room. Your bag holds an empty Shielded Cask, a loaded one (4 rods, about 1% leaks), 4 RadAid bags, a Geiger Counter (clicks only while carried) and a shovel.'), 'return 1'])
    result['survival'] = '\n'.join([GUARD, 'gamemode survival @s', 'return 1'])
    result['creative'] = '\n'.join([GUARD, 'gamemode creative @s', 'return 1'])
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
    (a.destination / 'datapacks' / PACK / 'pack.mcmeta').write_text(json.dumps(pack_metadata()) + '\n')


if __name__ == '__main__':
    main()

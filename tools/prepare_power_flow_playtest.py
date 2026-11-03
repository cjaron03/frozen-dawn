#!/usr/bin/env python3
"""Fresh isolated Core-to-relay power lab; copies metadata only and refuses existing saves."""
import argparse
import json
from pathlib import Path
from prepare_power_network_playtest import pack_only, prepare

TITLE = 'Power Flow Check'
NS = 'power_flow_check'
PACK = 'power-flow-check'
GUARD = 'execute unless score #built pflow matches 1 run return 0'


def tell(text, function=None):
    message = {'text': text, 'color': 'aqua'}
    if function:
        message['clickEvent'] = {'action':'run_command','value':'/function '+NS+':'+function}
    return 'tellraw @s '+json.dumps(message)


def scripts():
    setup = ['execute unless score #built pflow matches 0 run return 0',
        'scoreboard players set #built pflow -1', 'gamemode creative @s',
        'gamerule doMobSpawning false', 'gamerule doDaylightCycle false',
        'gamerule doWeatherCycle false', 'gamerule keepInventory true',
        'gamerule announceAdvancements false', 'difficulty normal',
        'time set midnight', 'weather clear',
        'fill -6 64 -6 36 64 38 minecraft:smooth_stone']
    for x,z,name in [(0,0,'geothermal_core'),(8,0,'power_relay'),(16,0,'power_relay'),(24,0,'panel_light'),
        (0,20,'geothermal_core'),(9,20,'power_relay'),(17,20,'panel_light'),
        (0,32,'geothermal_core'),(4,32,'power_relay'),(8,30,'panel_light'),(8,32,'panel_light'),(8,34,'panel_light')]:
        setup += [f'setblock {x} 65 {z} frozendawn:{name}',f'setblock {x} 64 {z} minecraft:cyan_concrete']
    # Restore the inside Core after the fresh room is built. Only this new world is touched.
    setup += ['fill -3 64 17 5 69 23 minecraft:glass hollow',
        'fill -3 64 17 5 64 23 minecraft:smooth_stone',
        'setblock 0 65 20 frozendawn:geothermal_core',
        'setblock 5 65 20 frozendawn:bulkhead_feedthrough',
        'give @s frozendawn:conduit_binder', 'give @s frozendawn:power_relay 8',
        'give @s frozendawn:panel_light 8','give @s frozendawn:bulkhead_feedthrough 4',
        'give @s minecraft:glass 16', 'setworldspawn 2 65 4',
        'spawnpoint @s 2 65 4', 'tp @s 2.5 65 4.5 180 15',
        'scoreboard players set #built pflow 1', 'function '+NS+':controls', 'return 1']
    result = {
        'load': 'scoreboard objectives add pflow dummy\nexecute unless score #built pflow matches -1..1 run scoreboard players set #built pflow 0\nexecute if score #built pflow matches 1 as @a run function '+NS+':controls',
        'tick': 'execute if score #built pflow matches 0 as @a[limit=1] run function '+NS+':setup',
        'setup': '\n'.join(setup),
        'controls': '\n'.join([GUARD,tell('POWER FLOW CHECK'),
            tell('[1 Core to light]', 'basic'),tell('[2 Through a sealed wall]', 'sealed'),tell('[Outside sealed wall]', 'sealed_outside'),tell('[3 Brownout]', 'brownout'),
            tell('Link with the Binder. Open the Core empty-handed to set power. Hold Binder and look at a node to read kW. Save and reopen to check your settings and links.'),'return 1'])}
    for name,(x,z,lines) in {
        'basic':(2,4,['Link Core -> relay -> relay -> Panel Light along z0, x0 / 8 / 16 / 24.',
            'Core starts at 0% power: dark. Open it empty-handed and set power to 25%: 5 kW, light on. Default turns it off.',
            'Break or Shift-clear a relay: light off. Reconnect with the Binder: light on.']),
        'sealed':(2,20,['Core inside glass at 0,65,20. Link Core -> wall feedthrough (5) -> outside relay (9) -> light (17), all at y65,z20.',
            'Set Core power to 25%. Replace the feedthrough with glass: light off. Restoring the block alone does not restore its removed incident links; relink it.',
            'Alternatively link Core directly to the outside relay through the feedthrough: replacing the wall with glass suspends that saved link, and restoring the feedthrough reconnects it.']),
        'sealed_outside':(9,22,['Outside the sealed room: bind the wall feedthrough (5,65,20) -> relay (9,65,20) -> light (17,65,20).', 'Replace the linked feedthrough with glass: light off. Restore it, then rebind its two incident links: light on.']),
        'brownout':(2,35,['Link Core at 0,65,32 -> relay at 4,65,32 -> all three lights at x8,z30 / 32 / 34.',
            'Set Core power to 1% (arrow keys adjust by 1): 0.20 kW supply / 0.30 kW demand. Two lights on, one off.',
            'Raise power to 2%: all three on. The Core keeps your chosen split; unused power is not stored.'])}.items():
        result[name]='\n'.join([GUARD,f'tp @s {x+.5} 65 {z+.5} 180 15',*(tell(line) for line in lines),'return 1'])
    return result


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',type=Path);parser.add_argument('--destination',type=Path)
    parser.add_argument('--pack-only',type=Path);args=parser.parse_args()
    if args.pack_only:
        pack_only(args.pack_only,title=TITLE,namespace=NS,build_scripts=scripts)
    elif args.source and args.destination:
        prepare(args.source,args.destination,title=TITLE,namespace=NS,pack=PACK,build_scripts=scripts,surface=64)
        # This command is a toggle, so it must not run from a world-local setup function.
        # Set the owning lab profile explicitly only when creating a save in that profile.
        lab = Path(__file__).resolve().parents[1] / 'run-lab'
        if args.destination.resolve().parent == (lab / 'saves').resolve():
            config = lab / 'config/frozendawn-common.toml'
            config.parent.mkdir(parents=True, exist_ok=True)
            if config.exists():
                text = config.read_text()
                assert text.count('pauseProgression = ') == 1, 'Inspect the lab pause setting before launch'
                updated = text.replace('pauseProgression = false', 'pauseProgression = true')
                if updated != text: config.write_text(updated)
            else: config.write_text('[general]\npauseProgression = true\n')
    else: parser.error('Use --source/--destination or --pack-only')

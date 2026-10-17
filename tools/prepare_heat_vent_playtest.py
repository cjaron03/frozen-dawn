#!/usr/bin/env python3
"""Fresh matched-room exchanger replay; copy only seed metadata, never an existing replay."""
import argparse
from pathlib import Path
import prepare_thermostat_playtest as thermostat
import prepare_room_heat_playtest as base


def scripts():
    f=thermostat.scripts()
    # Both rooms are wool: only shutter state differs during the cooling comparison.
    f['setup']=f['setup'].replace('minecraft:glass hollow','minecraft:white_wool hollow')
    additions=[
        'setblock -6 66 0 frozendawn:heat_vent[facing=east,open=false,powered=false]',
        'setblock 10 66 0 frozendawn:heat_vent[facing=east,open=false,powered=false]',
        'data merge block -8 65 -1 {Target:30}', 'data merge block 8 65 -1 {Target:30}',
        'give @s frozendawn:heat_vent 8', 'give @s minecraft:lever 8', 'give @s minecraft:stone 32',
        'give @s minecraft:torch 16',
    ]
    f['setup']=f['setup'].replace('scoreboard players set #built rheat 1','\n'.join(additions+['scoreboard players set #built rheat 1']))
    f['setup']=f['setup'].replace('QA: fresh wool/glass rooms, Core oxygen and default 20C thermostats. Heaters start OFF. Earlier saves are preserved.',
        'QA: two matching wool rooms, Core oxygen, 30C thermostats and CLOSED heat vents. Heaters start OFF.')
    f['controls']='\n'.join([base.GUARD,
        base.tell('Heat Vent Check: both rooms are identical wool rooms. Start with Fuel both; leave both vent shutters closed.'),
        base.tell('Wait 120 unpaused seconds for warming. Then Heaters off, visit left and right-click its east-wall vent once.'),
        base.tell('Keep the right vent closed. Compare room air/walls after 30 and 60 seconds; left should cool faster.'),
        base.tell('Place a torch inside each room after air is ready. Opening fins must preserve O2 and the lights, with no breach alarm.'),

        base.tell('After the first comparison: close/open the vent; block its OUTSIDE face (-5,66,0), then remove that block. Blocking disables cooling.'),
        base.tell('Sneak-place a lever on the room-side face to hold the shutter open. Remove the signal to restore the manual setting.'),
        base.tell('Manual: Heating / ORSA Heat Vent. Restore buttons with /function room_heat_check:controls.'),
        base.row([('[Left room]', 'left'),('[Right control]', 'right'),('[Read air/walls]', '/fd world thermal')]),
        base.row([('[Fuel both]', 'fuel'),('[Heaters off]', 'off'),('[Outside]', 'outside')]),
        base.row([('[Read sealed rooms]', '/fd world rooms')])])
    f['left']=f['left'].replace('Wool room. Wait for the temperature HUD, then Read air/walls.', 'Left wool room: the vent is in the EAST wall. Right-click toggles its cooling fins.')
    f['right']=f['right'].replace('Glass room. Wait for the temperature HUD, then Read air/walls.', 'Right wool room: keep its vent closed for the matched cooling comparison.')
    # Deliberate breach belongs to another replay; it must not replace a vent accidentally.
    del f['breach'];del f['patch']
    return f


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source',required=True,type=Path);p.add_argument('--destination',required=True,type=Path)
    args=p.parse_args()
    base.seed.TITLE='Heat Vent Check';base.seed.PACK='heat-vent-check';base.seed.NS=base.NS;base.seed.scripts=scripts
    base.seed.prepare(args.source,args.destination)

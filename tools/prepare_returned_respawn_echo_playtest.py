#!/usr/bin/env python3
"""Fresh Returned Echo Check: the re-levelled rings and Thaeven beds, the replacement ring and the return notice.

A flat Survival lab in Phase 0 with the world clock paused. On first join the setup force-selects the Hearths around
spawn, matures the Major Hearth to INTACT and moves spawn beside it, so the scene builds and its population arrives
while the owner watches. The first transmission is left to play naturally on the first approach. Buttons die, raise a
wall for a blocked death, give a sword, read and owe the notice, and replay the opening and the transmission.
Difficulty is locked and advancement popups are off. Never overwrites a save.
Usage (lab client, run-lab/ directory):
  python3 tools/prepare_returned_respawn_echo_playtest.py --source <any closed level.dat> --destination "run-lab/saves/Returned Echo Check"
"""
import argparse
import fcntl
import gzip
import io
import json
import math
from pathlib import Path

import prepare_blind_architect_playtest as nbt
from prepare_power_network_playtest import prepare, pack_only

TITLE = 'Returned Echo Check'
NS = 'returned_echo_check'
PACK = 'returned-echo-check'
OBJECTIVE = 'returnedecho'
GUARD = f'execute unless score #built {OBJECTIVE} matches 1 run return 0'
SEED = 77371337          # prepare_power_network_playtest's fixed lab seed
ANCHOR = (0, 65, 0)      # where the setup stands the player for force-select
SURFACE_Y = 64           # first air block above the flat lab's stone
SPAWN_OFFSET = 56        # blocks south of the Hearth center: outside the scene, outside contact range
INTACT_TICKS = 7 * 24000

MASK64 = (1 << 64) - 1


def _signed(value):
    value &= MASK64
    return value - (1 << 64) if value >> 63 else value


def _mix(value):
    """HearthSelectionPolicy.mix."""
    value &= MASK64
    value = ((value ^ (value >> 30)) * 0xBF58476D1CE4E5B9) & MASK64
    value = ((value ^ (value >> 27)) * 0x94D049BB133111EB) & MASK64
    return value ^ (value >> 31)


def _as_long(x, y, z):
    """BlockPos.asLong."""
    return ((x & 0x3FFFFFF) << 38) | (y & 0xFFF) | ((z & 0x3FFFFFF) << 12)


class _LegacyRandom:
    """Minecraft's LegacyRandomSource, the java.util.Random LCG."""
    def __init__(self, seed):
        self.seed = (_signed(seed) ^ 0x5DEECE66D) & ((1 << 48) - 1)

    def next(self, bits):
        self.seed = (self.seed * 0x5DEECE66D + 0xB) & ((1 << 48) - 1)
        value = self.seed >> (48 - bits)
        return value - (1 << bits) if bits == 32 and value >> 31 else value

    def next_double(self):
        return ((self.next(26) << 27) + self.next(27)) * 2.0 ** -53

    def next_int(self, bound):
        if bound & (bound - 1) == 0:
            return (bound * self.next(31)) >> 31
        while True:
            bits = self.next(31)
            value = bits % bound
            if bits - value + (bound - 1) < (1 << 31):
                return value


def major_center(seed=SEED, anchor=ANCHOR):
    """HearthSelectionPolicy.createPlan's Major Hearth center for this seed and anchor."""
    root = _mix(seed ^ _as_long(*anchor) ^ 0x4852454C49515553)
    random = _LegacyRandom(root ^ 0x4D414A4F525F3031)
    angle = random.next_double() * math.pi * 2.0
    radius = 512 + random.next_int(1024 - 512 + 1)
    return (anchor[0] + math.floor(math.cos(angle) * radius + 0.5),
            anchor[2] + math.floor(math.sin(angle) * radius + 0.5))


def tell(text, command=None, color='aqua'):
    value = {'text': text, 'color': color}
    if command:
        value['clickEvent'] = {'action': 'run_command', 'value': command}
    return 'tellraw @s '+json.dumps(value)


def scripts():
    cx, cz = major_center()
    sx, sz = cx, cz + SPAWN_OFFSET
    setup = [f'execute unless score #built {OBJECTIVE} matches -1 run return 0',
             f'scoreboard players set #built {OBJECTIVE} -2', 'gamemode survival @s',
             'gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false',
             'gamerule doFireTick false', 'gamerule keepInventory true', 'gamerule doImmediateRespawn false',
             'gamerule commandBlockOutput false', 'gamerule announceAdvancements false',
             'time set 6000', 'weather clear', 'fd world pause',
             'tp @s 0.5 65 0.5 180 0',
             'execute at @s run fd hearth force-select',
             f'fd hearth advance ticks {INTACT_TICKS}',
             f'setworldspawn {sx} {SURFACE_Y} {sz}', f'spawnpoint @s {sx} {SURFACE_Y} {sz}',
             f'tp @s {sx + 0.5} {SURFACE_Y} {sz + 0.5} 180 0',
             f'scoreboard players set #built {OBJECTIVE} 1', 'function '+NS+':controls', 'return 1']
    controls = [GUARD, tell('RETURNED ECHO CHECK: the Hearth is north. Let it build, then walk in.'),
                tell('[Hearth list]', '/fd hearth list', 'gray'),
                tell('[Population]', '/fd hearth population', 'gray'),
                tell('[Notice]', '/fd hearth notice', 'aqua'),
                tell('[Owe notice]', '/fd hearth notice owe', 'aqua'),
                tell('[Reset notice]', '/fd hearth notice reset confirm', 'light_purple'),
                tell('[Die]', '/function '+NS+':die', 'red'),
                tell('[Wall in front]', '/function '+NS+':wall', 'yellow'),
                tell('[Sword]', '/function '+NS+':sword', 'gold'),
                tell('[Replay transmission]', '/fd hearth transmission replay', 'green'),
                tell('[Play the opening]', '/fd payoff opening', 'green'),
                tell('[Controls]', '/function '+NS+':controls', 'gray'), 'return 1']
    return {
        'load': f'scoreboard objectives add {OBJECTIVE} dummy\nexecute unless score #built {OBJECTIVE} matches -2..1 run scoreboard players set #built {OBJECTIVE} 0\nexecute if score #built {OBJECTIVE} matches -2..-1 run scoreboard players set #built {OBJECTIVE} 0',
        'bootstrap': '\n'.join([f'execute unless score #built {OBJECTIVE} matches 0 run return 0',
                               'forceload add -16 -16 16 16', f'scoreboard players set #built {OBJECTIVE} -1', 'return 1']),
        'tick': '\n'.join([f'execute if score #built {OBJECTIVE} matches 0 as @a[limit=1] run function '+NS+':bootstrap',
                          f'execute if score #built {OBJECTIVE} matches -1 if loaded -16 65 -16 if loaded 16 65 16 as @a[limit=1] run function '+NS+':setup',
                          f'execute if score #built {OBJECTIVE} matches 1 run forceload remove -16 -16 16 16']),
        'setup': '\n'.join(setup),
        'controls': '\n'.join(controls),
        'die': '\n'.join([GUARD, 'kill @s', 'return 1']),
        # A stone wall a block in front, seven wide and four high, to hide behind.
        'wall': '\n'.join([GUARD, 'execute at @s run fill ^-3 ^ ^2 ^3 ^3 ^2 minecraft:stone', 'return 1']),
        'sword': '\n'.join([GUARD, 'give @s minecraft:netherite_sword', 'return 1'])}


def prepare_world(source, destination):
    with (source.parent/'session.lock').open('rb') as lock:
        fcntl.lockf(lock, fcntl.LOCK_SH | fcntl.LOCK_NB)
        prepare(source, destination, title=TITLE, namespace=NS, pack=PACK, build_scripts=scripts, surface=128, structures=False)
    level = destination/'level.dat'
    stream = io.BytesIO(gzip.decompress(level.read_bytes()))
    assert stream.read(1) == b'\x0a'
    nbt.string(stream)
    root = nbt.read(stream, 10)
    assert root['Data'][1]['WorldGenSettings'][1]['seed'] == (4, SEED), 'The Hearth center assumes the lab seed'
    root['Data'][1].update({'DifficultyLocked': (1, 1), 'GameType': (3, 0)})
    level.write_bytes(gzip.compress(b'\x0a'+nbt.utf('')+nbt.write(10, root)))
    cx, cz = major_center()
    report = destination/'preparation.json'
    info = json.loads(report.read_text())
    info.update({'mode': 'Survival', 'phase': '0, paused', 'majorHearthCenter': [cx, cz],
                 'spawn': [cx, SURFACE_Y, cz + SPAWN_OFFSET],
                 'fixtures': 'force-selected INTACT Major Hearth; buttons for death, a wall, a sword, the notice, '
                             'the transmission replay and the opening'})
    report.write_text(json.dumps(info, indent=2)+'\n')
    print(destination.resolve(), 'Major Hearth center', (cx, cz))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path)
    parser.add_argument('--destination', type=Path)
    parser.add_argument('--pack-only', type=Path)
    parser.add_argument('--center', action='store_true', help='Print the computed Major Hearth center and exit')
    args = parser.parse_args()
    if args.center:
        print(*major_center())
    elif args.pack_only:
        pack_only(args.pack_only, title=TITLE, namespace=NS, build_scripts=scripts)
    elif args.source and args.destination:
        prepare_world(args.source, args.destination)
    else:
        parser.error('Use --source/--destination, --pack-only or --center')

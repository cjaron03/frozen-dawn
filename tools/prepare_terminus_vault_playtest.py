#!/usr/bin/env python3
"""Prepare a fresh Terminus Vault world from generator metadata only; never overwrite a save.

One Helion site with the vault under it, 32 blocks south of spawn, placed by /fd terminus vault place. The player starts
in Survival at the site's hatch with a pickaxe and light, and finds the rest. Buttons jump to each room and to each stage
of the Watch Floor's fight, give a fighter's kit, send in a follower, and reset everything. Advancements are filtered as
in every generated playtest world, but a manual entry is locked by its advancement, so this world's pack carries silent
copies of the vault documents' own, and of Last Badge-Out; without them a document says it is archived and the manual
stays shut.
"""
import argparse
import fcntl
import gzip
import io
import json
from pathlib import Path

import prepare_blind_architect_playtest as nbt
from prepare_power_network_playtest import prepare, pack_only

TITLE = 'Terminus Watch Floor Check'
NS = 'terminus_vault_check'
PACK = 'terminus-vault-check'
GUARD = 'execute unless score #built vault matches 1 run return 0'
# The site goes 32 blocks south of the spawn on flat ground at 64: its hall floor is 24 down.
HX, HY, HZ = 0, 40, 32


def tell(text, function=None):
    value = {'text': text, 'color': 'aqua'}
    if function:
        value['clickEvent'] = {'action': 'run_command', 'value': '/function '+NS+':'+function}
    return 'tellraw @s '+json.dumps(value)


def tp(x, y, z, yaw):
    return f'tp @s {HX+x}.5 {HY+y} {HZ+z}.5 {yaw} 0'


def scripts():
    setup = ['execute unless score #built vault matches -1 run return 0',
             'scoreboard players set #built vault -2', 'gamemode survival @s',
             'gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false',
             'gamerule keepInventory true', 'gamerule sendCommandFeedback false', 'gamerule commandBlockOutput false',
             'time set noon', 'weather clear', 'clear @s',
             'item replace entity @s hotbar.0 with minecraft:diamond_pickaxe',
             'item replace entity @s hotbar.1 with minecraft:torch 64',
             'item replace entity @s hotbar.2 with minecraft:cooked_beef 32',
             'tp @s 0.5 65 0.5 0 0', 'fd terminus vault place',
             'setworldspawn 0 65 0', 'spawnpoint @s 0 65 0',
             'scoreboard players set #built vault 1', 'function '+NS+':controls', 'return 1']
    rooms = {'hall': (tp(7, 1, 0, 90), 'The hall, by the crack in the torus. The Core is the prize, and it comes out with a pickaxe.'),
             'landing': (tp(-1, -14, 0, -90), 'The landing. Door 1 is east: turn your back and listen.'),
             'ledge': (tp(5, -14, 0, 90), 'The ledge, at the lip. The far side is east. Your body must not face it; F5 twice shows your own face.'),
             'far_ledge': (tp(16, -14, 0, -90), 'The bridge\'s far ledge. The Watch Floor is east, through the open door.'),
             'catwalk': (tp(18, -14, 2, -90), 'The west catwalk: the board is at your back, the Seal Order to the north. Step off it and the fight starts.'),
             'cot': (tp(42, -14, 1, -90), 'The cot room. The last door is east, the cot along the south wall.')}
    upper, lower = tp(24, -14, -10, 0), tp(28, -23, -10, 0)
    stages = {'fight_start': (upper, 'fight start', 'Round 1, full clock. The board lights.'),
              'fight_round2': (upper, 'fight round 2', 'Round 2: one opening down, full clock.'),
              'fight_round3': (upper, 'fight round 3', 'Round 3: glyphs one at a time, pulled in order.'),
              'fight_pull1': (lower, 'fight pull 1', 'Round 1, fuses live. [Read state] gives the answer.'),
              'fight_pull3': (lower, 'fight pull 3', 'Round 3, fuses live, in order. [Read state] gives the answer.'),
              'fight_burn1': (lower, 'fight burn 1', 'First opening: fifteen seconds, a third of it at most.'),
              'fight_burn3': (lower, 'fight burn 3', 'Last opening: it can fall now.')}
    result = {
        'load': 'scoreboard objectives add vault dummy\nexecute unless score #built vault matches -2..1 run scoreboard players set #built vault 0\nexecute if score #built vault matches -2..-1 run scoreboard players set #built vault 0',
        'bootstrap': '\n'.join(['execute unless score #built vault matches 0 run return 0',
                               'forceload add -32 -16 64 96', 'scoreboard players set #built vault -1', 'return 1']),
        'tick': '\n'.join(['execute if score #built vault matches 0 as @a[limit=1] run function '+NS+':bootstrap',
                          'execute if score #built vault matches -1 if loaded -32 65 -16 if loaded 64 65 96 as @a[limit=1] run function '+NS+':setup']),
        'setup': '\n'.join(setup),
        'controls': '\n'.join([GUARD,
            tell('TERMINUS VAULT CHECK: Survival, Normal. A Helion site stands 32 blocks south: look for the mast. Find your own way down, or:'),
            tell('[The hall]', 'hall'), tell('[Landing: door 1]', 'landing'), tell('[Ledge: the bridge]', 'ledge'), tell('[Far ledge]', 'far_ledge'),
            tell('[West catwalk]', 'catwalk'), tell('[Cot room]', 'cot'), tell('[Fighter\'s kit]', 'kit'),
            tell('[Fight: round 1]', 'fight_start'), tell('[Round 2]', 'fight_round2'), tell('[Round 3]', 'fight_round3'),
            tell('[Pull: round 1]', 'fight_pull1'), tell('[Pull: round 3]', 'fight_pull3'),
            tell('[Burn: first]', 'fight_burn1'), tell('[Burn: last]', 'fight_burn3'),
            tell('[Clock to 1:00]', 'clock_60'), tell('[Clock to 0:10]', 'clock_10'),
            tell('[A husk follows you in]', 'husk'), tell('[Remove the husk]', 'unhusk'),
            tell('[Reset the vault]', 'reset'), tell('[Read state]', 'read'), tell('[Creative]', 'creative'), tell('[Survival]', 'survival'), 'return 1']),
        'husk': '\n'.join([GUARD, 'summon minecraft:husk ~ ~ ~ {Tags:["vault_follower"],PersistenceRequired:1b}',
                           tell('A husk beside you, with its wits. Doors freeze when it faces them.'), 'return 1']),
        'unhusk': '\n'.join([GUARD, 'kill @e[tag=vault_follower]', 'return 1']),
        'reset': '\n'.join([GUARD, 'fd terminus vault reset', 'clear @s frozendawn:terminus', 'return 1']),
        'read': '\n'.join([GUARD, 'fd terminus vault status', 'return 1']),
        'kit': '\n'.join([GUARD, 'item replace entity @s hotbar.3 with minecraft:diamond_sword', 'item replace entity @s hotbar.4 with minecraft:bow',
                          'item replace entity @s inventory.0 with minecraft:arrow 64', 'item replace entity @s armor.head with minecraft:diamond_helmet',
                          'item replace entity @s armor.chest with minecraft:diamond_chestplate', 'item replace entity @s armor.legs with minecraft:diamond_leggings',
                          'item replace entity @s armor.feet with minecraft:diamond_boots', tell('Diamond sword, bow, diamond armor.'), 'return 1']),
        'clock_60': '\n'.join([GUARD, 'fd terminus vault fight clock 60', 'return 1']),
        'clock_10': '\n'.join([GUARD, 'fd terminus vault fight clock 10', 'return 1']),
        'creative': '\n'.join([GUARD, 'gamemode creative @s', 'return 1']),
        'survival': '\n'.join([GUARD, 'gamemode survival @s', 'return 1'])}
    for name, (move, line) in rooms.items():
        result[name] = '\n'.join([GUARD, move, tell(line), tell('[Lab menu]', 'controls'), 'return 1'])
    for name, (move, stage, line) in stages.items():
        result[name] = '\n'.join([GUARD, move, 'fd terminus vault '+stage, tell(line), tell('[Lab menu]', 'controls'), 'return 1'])
    return result


ROOT = Path(__file__).resolve().parent.parent
ADVANCEMENTS = ROOT/'src/main/resources/data/frozendawn/advancement'


def document_advancements(pack):
    """The documents' advancements, as the mod defines them but rootless and without toasts: their parent is filtered out."""
    out = pack/'data/frozendawn/advancement'
    out.mkdir(parents=True, exist_ok=True)
    for source in [*ADVANCEMENTS.glob('found_terminus_*.json'), ADVANCEMENTS/'last_badge_out.json']:
        advancement = json.loads(source.read_text())
        del advancement['parent']
        advancement['display'].update({'background': 'minecraft:textures/block/smooth_stone.png', 'show_toast': False})
        (out/source.name).write_text(json.dumps(advancement, indent=2)+'\n')


def prepare_world(source, destination, title=TITLE):
    with (source.parent/'session.lock').open('rb') as lock:
        fcntl.lockf(lock, fcntl.LOCK_SH | fcntl.LOCK_NB)
        prepare(source, destination, title=title, namespace=NS, pack=PACK, build_scripts=scripts, surface=128, structures=False)
    document_advancements(destination/'datapacks'/PACK)
    # Lock the difficulty the shared lab chose, and give the save the empty dragon fight the client logs as missing.
    level = destination/'level.dat'
    stream = io.BytesIO(gzip.decompress(level.read_bytes()))
    assert stream.read(1) == b'\x0a'
    nbt.string(stream)
    root = nbt.read(stream, 10)
    root['Data'][1].update({'DifficultyLocked': (1, 1), 'DragonFight': (10, {})})
    level.write_bytes(gzip.compress(b'\x0a'+nbt.utf('')+nbt.write(10, root)))
    report = destination/'preparation.json'
    info = json.loads(report.read_text())
    info.update({'mode': 'Survival', 'fixtures': 'one Helion site with the Terminus Vault under it, room and fight-stage buttons, a kit, a follower husk, reset'})
    report.write_text(json.dumps(info, indent=2)+'\n')
    print(destination.resolve())


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path)
    parser.add_argument('--destination', type=Path)
    parser.add_argument('--pack-only', type=Path)
    parser.add_argument('--title', default=TITLE)
    args = parser.parse_args()
    if args.pack_only:
        pack_only(args.pack_only, title=TITLE, namespace=NS, build_scripts=scripts)
    elif args.source and args.destination:
        prepare_world(args.source, args.destination, args.title)
    else:
        parser.error('Use --source/--destination or --pack-only')

#!/usr/bin/env python3
"""Fresh Aurora S4 playtest worlds, one world each. Never overwrites a save.

Aurora Nine Check, the default: T2, the husk at the pit, the lamp and the quiet day. A flat lab in Survival with a dock,
charged and empty cells in a chest, and a Blast Pit 130 blocks east. The save only names where the pit goes: the real
placement builds it on the first ticks, so the ring the husk stands on is the real one. Boot her, finish the first
meeting, then the buttons make her Friendly, skip the days between what she asks and take you to the husk.

Aurora Sky Check, --world sky: T3, T4, T5 and what turns up in her. The same lab in Creative, since phase 6 would kill
anyone on the tower, with a stone tower to Y 110. Nine is already behind her: the save starts with T1 and T2 asked and
the sheet out of the husk, so T5 only waits for someone to read it. The pit is there for its warm ground, which is
where the cold lets her ask anything. Buttons set the sky and the score, and hand over the register and Enoch's log.

Advancements are filtered as in every generated playtest world, and the difficulty is locked.
Usage (lab client, run-lab/ directory):
  python3 tools/prepare_aurora_s4_playtest.py --source <any closed level.dat> --destination "run-lab/saves/Aurora Nine Check"
  python3 tools/prepare_aurora_s4_playtest.py --world sky --source <any closed level.dat> --destination "run-lab/saves/Aurora Sky Check"
"""
import argparse
import fcntl
import functools
import gzip
import io
import json
from pathlib import Path
from typing import NamedTuple

import prepare_blind_architect_playtest as nbt
from prepare_power_network_playtest import prepare, pack_only
from prepare_terminus_vault_playtest import ADVANCEMENTS, document_advancements


class Check(NamedTuple):
    title: str
    namespace: str
    pack: str
    objective: str


NINE = Check('Aurora Nine Check', 'aurora_nine_check', 'aurora-nine-check', 'auroranine')
SKY = Check('Aurora Sky Check', 'aurora_sky_check', 'aurora-sky-check', 'aurorasky')
CHEST = (3, 65, -4)
CHARGED = 'frozendawn:power_cell[frozendawn:cell_joules=500000]'
EMPTY = 'frozendawn:power_cell'
# Past the distance she asks from, and ground level, as the planner would store it for this flat world.
PIT = (130, 64, 0)
FRIENDLY, TRUSTED, CLOSE = 250, 500, 850
# Open crater floor, a few blocks from the first place on the ring.
BESIDE_THE_HUSK = (PIT[0]+10.5, PIT[1]-2, PIT[2]+3.5)
ABOVE_THE_PIT = (PIT[0]-24.5, PIT[1], PIT[2]+0.5)
# Half-bright stars on a 120-day world, a little under the point vacuum starts.
STARS_DAY, TOTAL_DAYS = 96, 120
TOWER_X, TOWER_TOP = 22, 109
# What Nine leaves behind her. nine_open stays off: this world has no husk to place.
AFTER_NINE = ['t1_asked', 't2_asked', 't2_heard', 'nine_seen', 'nine_sheet_taken']


def guard(check):
    return f'execute unless score #built {check.objective} matches 1 run return 0'


def tell(check, text, function=None, color='aqua', command=None):
    value = {'text': text, 'color': color}
    if function:
        value['clickEvent'] = {'action': 'run_command', 'value': '/function '+check.namespace+':'+function}
    if command:
        value['clickEvent'] = {'action': 'run_command', 'value': command}
    return 'tellraw @s '+json.dumps(value)


def nine_controls(check):
    say = functools.partial(tell, check)
    return [say('AURORA NINE CHECK: the cells are in the chest beside the dock. Boot her and finish the first meeting. [Friendly], then [Skip a day] after each thing she asks. Stay by the dock until she has told you where it is.'),
            say('[Friendly]', command=f'/fd shepherd affinity {FRIENDLY}', color='green'), say('[Skip a day]', command='/fd shepherd skipday'),
            say('[The pit]', 'to_the_pit', 'yellow'), say('[The husk]', 'to_the_husk', 'yellow'), say('[The dock]', 'to_the_dock', 'yellow')]


def sky_controls(check):
    say = functools.partial(tell, check)
    return [say('AURORA SKY CHECK: boot her and finish the first meeting. Nine is behind her and [The file] hands you the sheet. [Close] then [Skip a day] for T4; [Phase 6 mid], [Trusted], and wait on [Warm ground] for T3, then take her up the tower on a clear night. She has to be following you.'),
            say('[Trusted]', command=f'/fd shepherd affinity {TRUSTED}', color='green'), say('[Close]', command=f'/fd shepherd affinity {CLOSE}', color='green'),
            say('[Skip a day]', command='/fd shepherd skipday'), say('[Phase 6 mid]', 'stars', 'light_purple'), say('[Clear night]', 'night', 'light_purple'), say('[Noon]', 'noon', 'light_purple'),
            say('[The tower]', 'to_the_tower', 'yellow'), say('[Warm ground]', 'to_the_husk', 'yellow'), say('[The dock]', 'to_the_dock', 'yellow'),
            say('[The file]', 'the_file', 'gold'), say('[His log]', 'his_log', 'gold'),
            say('[Walls]', command='/fd shepherd witness causal', color='gray'), say('[She may speak]', command='/fd shepherd resetgap', color='gray')]


def on_the_starry_day(check, time_of_day):
    """Never time set: unless progression is paused, the apocalypse follows the day count, and that would put it back to day 0."""
    return '\n'.join([guard(check), f'fd world set day {STARS_DAY}', f'time add {time_of_day}', 'weather clear', 'return 1'])


def document(doc):
    return 'give @s frozendawn:orsa_document[minecraft:custom_data={doc_type:"'+doc+'"}]'


def scripts(check=NINE):
    objective, ns, sky = check.objective, check.namespace, check is SKY
    setup = [f'execute unless score #built {objective} matches -1 run return 0',
             f'scoreboard players set #built {objective} -2', 'gamemode creative @s' if sky else 'gamemode survival @s',
             'gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false',
             'gamerule doFireTick false', 'gamerule mobGriefing false', 'gamerule keepInventory true',
             'gamerule commandBlockOutput false', 'gamerule announceAdvancements false',
             'difficulty normal', 'time set noon', 'weather clear', 'clear @s', 'fd world pause',
             *([f'fd world set total-days {TOTAL_DAYS}'] if sky else []),
             'item replace entity @s hotbar.0 with minecraft:cooked_beef 32',
             'fill -16 64 -12 16 64 12 minecraft:polished_deepslate', 'setblock 0 65 -4 frozendawn:cs1_dock[facing=south]',
             'setblock {} {} {} minecraft:chest[facing=south]'.format(*CHEST),
             *['item replace block {} {} {} container.{} with {}'.format(*CHEST, slot, cell) for slot, cell in enumerate([CHARGED, CHARGED, EMPTY])],
             *([f'fill {TOWER_X-2} 65 -2 {TOWER_X+2} {TOWER_TOP} 2 minecraft:stone_bricks'] if sky else []),
             'setworldspawn 0 65 3', 'spawnpoint @s 0 65 3', 'tp @s 0.5 65 3.5 180 0',
             f'scoreboard players set #built {objective} 1', 'function '+ns+':controls', 'return 1']
    say = functools.partial(tell, check)
    controls = [guard(check), *(sky_controls if sky else nine_controls)(check),
                say('[Why]', command='/fd shepherd why', color='gray'), say('[Mind]', command='/fd shepherd mind', color='gray'), say('[State]', command='/fd shepherd state', color='gray'), 'return 1']
    # Enough loaded around the target for the real placement to go ahead, with nobody near it.
    area = 'forceload add {} {} {} {}'.format(PIT[0]-48, PIT[2]-48, PIT[0]+48, PIT[2]+48)
    functions = {
        'load': f'scoreboard objectives add {objective} dummy\nexecute unless score #built {objective} matches -2..1 run scoreboard players set #built {objective} 0\nexecute if score #built {objective} matches -2..-1 run scoreboard players set #built {objective} 0',
        'bootstrap': '\n'.join([f'execute unless score #built {objective} matches 0 run return 0',
                               'forceload add -32 -16 32 16', area, f'scoreboard players set #built {objective} -1', 'return 1']),
        'tick': '\n'.join([f'execute if score #built {objective} matches 0 as @a[limit=1] run function '+ns+':bootstrap',
                          f'execute if score #built {objective} matches -1 if loaded -32 65 -16 if loaded 32 65 16 as @a[limit=1] run function '+ns+':setup']),
        'setup': '\n'.join(setup),
        'controls': '\n'.join(controls),
        'to_the_pit': '\n'.join([guard(check), 'tp @s {} {} {} -90 0'.format(*ABOVE_THE_PIT), 'return 1']),
        'to_the_husk': '\n'.join([guard(check), 'tp @s {} {} {} 0 0'.format(*BESIDE_THE_HUSK), 'return 1']),
        'to_the_dock': '\n'.join([guard(check), 'tp @s 0.5 65 3.5 180 0', 'return 1'])}
    if sky:
        functions.update({
            'stars': on_the_starry_day(check, 6000), 'noon': on_the_starry_day(check, 6000), 'night': on_the_starry_day(check, 18000),
            'to_the_tower': '\n'.join([guard(check), f'tp @s {TOWER_X}.5 {TOWER_TOP+1} 0.5 -90 -30', 'return 1']),
            'the_file': '\n'.join([guard(check), document('tithonus_register'), 'return 1']),
            'his_log': '\n'.join([guard(check), document('terminus_log_last'), 'return 1'])})
    return functions


def the_register(pack):
    """The Frame Register's advancement, as the mod defines it but rootless and without a toast: its parent is filtered out."""
    out = pack/'data/frozendawn/advancement'
    out.mkdir(parents=True, exist_ok=True)
    source = ADVANCEMENTS/'found_tithonus_register.json'
    advancement = json.loads(source.read_text())
    del advancement['parent']
    advancement['display'].update({'background': 'minecraft:textures/block/smooth_stone.png', 'show_toast': False})
    (out/source.name).write_text(json.dumps(advancement, indent=2)+'\n')


def pit_planned(destination, data_version):
    """The planner's answer, already given: nothing is built here."""
    state = {'blastPitTargetX': (3, PIT[0]), 'blastPitTargetY': (3, PIT[1]), 'blastPitTargetZ': (3, PIT[2])}
    encoded = b'\x0a'+nbt.utf('')+nbt.write(10, {'data': (10, state), 'DataVersion': (3, data_version)})
    (destination/'data/frozendawn_orsa_structures.dat').write_bytes(gzip.compress(encoded))


def nine_behind_her(destination, data_version):
    """T1, T2 and the husk as they stand afterwards, in the save she will wake into. She is still dormant."""
    state = {'stage': (8, 'DORMANT'), 'flags': (9, (8, AFTER_NINE))}
    encoded = b'\x0a'+nbt.utf('')+nbt.write(10, {'data': (10, state), 'DataVersion': (3, data_version)})
    (destination/'data/frozendawn_shepherd.dat').write_bytes(gzip.compress(encoded))


def prepare_world(source, destination, check=NINE):
    with (source.parent/'session.lock').open('rb') as lock:
        fcntl.lockf(lock, fcntl.LOCK_SH | fcntl.LOCK_NB)
        prepare(source, destination, title=check.title, namespace=check.namespace, pack=check.pack, build_scripts=functools.partial(scripts, check), surface=128, structures=False)
    the_register(destination/'datapacks'/check.pack)
    if check is SKY:
        document_advancements(destination/'datapacks'/check.pack)
    level = destination/'level.dat'
    stream = io.BytesIO(gzip.decompress(level.read_bytes()))
    assert stream.read(1) == b'\x0a'
    nbt.string(stream)
    root = nbt.read(stream, 10)
    root['Data'][1].update({'DifficultyLocked': (1, 1), 'DragonFight': (10, {})})
    level.write_bytes(gzip.compress(b'\x0a'+nbt.utf('')+nbt.write(10, root)))
    data_version = root['Data'][1]['DataVersion'][1]
    pit_planned(destination, data_version)
    if check is SKY:
        nine_behind_her(destination, data_version)
    report = destination/'preparation.json'
    info = json.loads(report.read_text())
    if check is SKY:
        info.update({'mode': 'Creative', 'fixtures': 'a dock, charged and empty cells in a chest, a tower to Y '+str(TOWER_TOP+1)+', a Blast Pit planned at '+str(PIT)+' for its warm ground, T1 and T2 asked and the sheet out, buttons for the score, the sky, the tower, the register and the log'})
    else:
        info.update({'mode': 'Survival', 'fixtures': 'a dock, charged and empty cells in a chest, a Blast Pit planned at '+str(PIT)+' and built by the real placement, buttons for Friendly, a skipped day, the pit, the husk and the dock'})
    report.write_text(json.dumps(info, indent=2)+'\n')
    print(destination.resolve())


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path)
    parser.add_argument('--destination', type=Path)
    parser.add_argument('--pack-only', type=Path)
    parser.add_argument('--world', choices=['nine', 'sky'], default='nine')
    args = parser.parse_args()
    check = SKY if args.world == 'sky' else NINE
    if args.pack_only:
        pack_only(args.pack_only, title=check.title, namespace=check.namespace, build_scripts=functools.partial(scripts, check))
    elif args.source and args.destination:
        prepare_world(args.source, args.destination, check)
    else:
        parser.error('Use --source/--destination or --pack-only')

#!/usr/bin/env python3
"""Fresh, real-action travel checks. Never overwrite saves or inject tactical beliefs."""
import argparse
import gzip
import json
import shutil
import struct
from pathlib import Path
import prepare_custom_sword_playtest as base
from prepare_emergency_eva_playtest import compound_child, named

CASES = [('MACS Travel 1 Shield', 'sword', 4, 83.5),
         ('MACS Travel 2 Archer', 'sword', 5, 96.5),
         ('MACS Travel 3 Pillar', 'bow', 4, 92.5),
         ('MACS Travel 4 Mantlet', 'bow', 5, 92.5)]


def tell(text, command=None, color='aqua'):
    return base.tell(text, command, color)


def pack(destination, title, weapon, count, target_x):
    item = 'minecraft:iron_sword' if weapon == 'sword' else 'minecraft:bow'
    scripts = {
        'load': 'scoreboard objectives add travel dummy\nexecute unless score #stage travel matches -2147483648..2147483647 run scoreboard players set #stage travel 0',
        'tick': 'execute as @a unless entity @s[tag=travel_player] run function travel:join\nexecute if score #stage travel matches 2 run scoreboard players add #settle travel 1\nexecute if score #stage travel matches 2 if score #settle travel matches 2.. as @a[tag=travel_player,limit=1] run function travel:after_contact\nexecute if score #stage travel matches 3 run scoreboard players add #gap travel 1\nexecute if score #stage travel matches 3 if score #gap travel matches 620.. as @a[tag=travel_player,limit=1] run function travel:ready',
        'build': '\n'.join(['gamerule doMobSpawning false','gamerule doDaylightCycle false','gamerule doWeatherCycle false','gamerule keepInventory true','gamerule mobGriefing false','difficulty normal','weather clear','fd world preset default','fd world set phase 6 late'] + [
            cmd for cx in (0,80) for cmd in [
            f'fill {cx-16} 64 -16 {cx+20} 64 16 minecraft:bedrock',
            f'fill {cx-16} 65 -16 {cx+20} 70 16 minecraft:air',
            f'fill {cx-16} 65 -16 {cx+20} 71 -16 minecraft:bedrock',
            f'fill {cx-16} 65 16 {cx+20} 71 16 minecraft:bedrock',
            f'fill {cx-16} 65 -16 {cx-16} 71 16 minecraft:bedrock',
            f'fill {cx+20} 65 -16 {cx+20} 71 16 minecraft:bedrock',
            f'fill {cx-16} 71 -16 {cx+20} 71 16 minecraft:bedrock',
            f'setblock {cx} 64 0 minecraft:blue_concrete',
            f'setblock {cx+3} 64 0 minecraft:lime_concrete',
            f'setblock {cx+12} 64 0 minecraft:lime_concrete',
            f'setblock {cx} 64 4 frozendawn:geothermal_core',
            f'setblock {cx+8} 64 8 minecraft:sea_lantern']
            ] + ['fill 32 64 -3 38 64 3 minecraft:bedrock',
            'summon frozendawn:architect 33.5 65 0.5 {Tags:["travel_archive"],NoAI:1b,NoGravity:1b,PersistenceRequired:1b}',
            'setworldspawn 8 65 8','scoreboard players set #built travel 1']),
        'join': 'execute unless score #built travel matches 1 run return 0\ntag @s add travel_player\n' + base.SCRIPTS['kit'] + '\nitem replace entity @s hotbar.0 with ' + item + '\nitem replace entity @s hotbar.1 with minecraft:air\nitem replace entity @s hotbar.2 with minecraft:air\nitem replace entity @s hotbar.3 with minecraft:air\nitem replace entity @s hotbar.4 with minecraft:air\nspawnpoint @s 8 65 8\ntp @s 8.5 65 8.5 135 0\ngamemode survival @s\n' + tell(title + ': earn ' + str(count) + ' separate ' + weapon + ' encounters at A, then observe a fresh Architect 80 blocks away. Full EVA/protection supplied.') + '\n' + tell('[Begin real training]', 'function travel:begin', 'green'),
        'begin': 'execute unless score #stage travel matches 0 run return 0\nscoreboard players set #round travel 0\nfunction travel:witness',
        'witness': 'scoreboard players set #stage travel 1\nadvancement revoke @s only travel:contact\ntp @s 3.5 65 0.5 90 0\nsummon frozendawn:architect 0.5 65 0.5 {Tags:["travel_active"],PersistenceRequired:1b}\nfd architect record @e[tag=travel_active,limit=1] 1337\n' + tell('At training area A: ' + ('hit ONCE with the sword in hotbar 1.' if weapon=='sword' else 'SHOOT ONCE with the bow in hotbar 1.')),
        'contact': 'execute unless score #stage travel matches 1 run return 0\nscoreboard players set #stage travel 2\nscoreboard players set #settle travel 0',
        'after_contact': 'fd maeve dump\nfunction travel:archive\nscoreboard players add #round travel 1\nscoreboard players set #stage travel 3\nscoreboard players set #gap travel 0\ntp @s 8.5 65 8.5 135 0\n' + tell('Actual contact recorded. Witness archived alive. Wait 31 unpaused seconds or skip this empty gap.') + '\n' + tell('[Skip empty gap: 620 ticks]', 'tick sprint 620t', 'yellow'),
        'archive': 'execute as @e[tag=travel_active] run fd architect dump @s\nexecute as @e[tag=travel_active] run data merge entity @s {NoAI:1b,NoGravity:1b,Motion:[0.0d,0.0d,0.0d]}\ntp @e[tag=travel_active] 33.5 65 0.5\ntag @e[tag=travel_active] add travel_archive\ntag @e[tag=travel_active] remove travel_active',
        'ready': f'execute if score #round travel matches ..{count-1} run return run function travel:witness\nscoreboard players set #stage travel 4\nfd maeve dump\n' + tell('Training complete. Check confidence ' + ('0.80' if count==4 else '1.00') + ' before travel. The old build rejected this history at area B.') + '\n' + tell('[Travel 80 blocks and start fresh encounter]', 'function travel:observe', 'green'),
        'observe': 'execute unless score #stage travel matches 4 run return 0\nscoreboard players set #stage travel 5\n' + f'tp @s {target_x} 65 0.5 90 0\nsummon frozendawn:architect 80.5 65 0.5 {{Tags:["travel_active"],PersistenceRequired:1b}}\nfd architect record @e[tag=travel_active,limit=1] 1337\n' + tell('Observe BEFORE attacking. Expected: ' + title.split()[-1] + '. This fresh encounter must use the original history; do not supply a confirming hit first.') + '\n' + tell('[Capture Maeve and actor evidence]', 'function travel:status', 'yellow') + '\n' + tell('[Finish; preserve history and actors]', 'function travel:finish', 'gold'),
        'status': 'fd maeve dump\nexecute as @e[tag=travel_active] run fd architect dump @s',
        'finish': 'execute unless score #stage travel matches 5 run return 0\nfunction travel:archive\nscoreboard players set #stage travel 99\n' + tell('Stopped; actor and earned history preserved. This world does not reset an attempt.')
    }
    root=destination/'datapacks/custom-sword-check'
    (root/'data/travel/function').mkdir(parents=True)
    (root/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':48,'description':title}}))
    for name, value in scripts.items(): (root/f'data/travel/function/{name}.mcfunction').write_text(value+'\n')
    tags=root/'data/minecraft/tags/function'; tags.mkdir(parents=True)
    for name in ('load','tick'): (tags/f'{name}.json').write_text(json.dumps({'values':['travel:'+name]}))
    adv=root/'data/travel/advancement'; adv.mkdir(parents=True)
    (adv/'contact.json').write_text(json.dumps({'criteria':{'contact':{'trigger':'minecraft:player_hurt_entity','conditions':{
        'player':{'equipment':{'mainhand':{'items':[item]}}},'entity':{'type':'frozendawn:architect','nbt':'{Tags:["travel_active"]}'},
        'damage':{'dealt':{'min':.1},'blocked':False,'type':{'tags':[{'id':'minecraft:is_projectile','expected':weapon=='bow'}]}}}}},
        'rewards':{'function':'travel:contact'}}))


def metadata_title(raw, title):
    _, start, _ = compound_child(raw, 3, 'Data')
    kind, payload, end = compound_child(raw, start, 'allowCommands')
    assert kind == 1
    raw = raw[:payload] + b'\x01' + raw[end:]
    kind, payload, end = compound_child(raw, start, 'LevelName')
    assert kind == 8
    encoded = title.encode()
    return raw[:payload] + len(encoded).to_bytes(2, 'big') + encoded + raw[end:]


def validate_unplayed(prebuilt):
    if any((prebuilt / 'playerdata').glob('*.dat')):
        raise SystemExit('Prebuilt template must have no joined players')
    raw = gzip.decompress((prebuilt / 'data/frozendawn_maeve.dat').read_bytes())
    _, data, _ = compound_child(raw, 3, 'data')
    _, beliefs, _ = compound_child(raw, data, 'beliefs')
    kind, payload, _ = compound_child(raw, beliefs, 'players')
    if kind != 9 or struct.unpack('>i', raw[payload+1:payload+5])[0] != 0:
        raise SystemExit('Prebuilt template must have empty Maeve player history')
    raw = gzip.decompress((prebuilt / 'level.dat').read_bytes())
    _, data, _ = compound_child(raw, 3, 'Data')
    # A dedicated builder must be stopped before copying; a saved Player is ineligible.
    try:
        compound_child(raw, data, 'Player')
    except SystemExit:
        return
    raise SystemExit('Prebuilt template contains a singleplayer Player')


def prepare(source, saves, prebuilt=None):
    if prebuilt:
        validate_unplayed(prebuilt)
    for title, weapon, count, target_x in CASES:
        destination = saves / title
        if destination.exists():
            raise SystemExit('Refusing to overwrite ' + str(destination))
        if prebuilt:
            shutil.copytree(prebuilt, destination, ignore=shutil.ignore_patterns('session.lock'))
            shutil.rmtree(destination / 'datapacks')
            raw = gzip.decompress((destination / 'level.dat').read_bytes())
            # Remove only the copied preparation tickets, including catch-up's final ticket.
            chunks = destination / 'data/chunks.dat'
            tickets = gzip.decompress(chunks.read_bytes())
            _, data, _ = compound_child(tickets, 3, 'data')
            kind, payload, end = compound_child(tickets, data, 'Forced')
            assert kind == 12
            tickets = tickets[:payload] + struct.pack('>i', 0) + tickets[end:]
            chunks.write_bytes(gzip.compress(tickets))
        else:
            raw = base.fresh_metadata(source)
            destination.mkdir(parents=True)
        raw = metadata_title(raw, title)
        (destination / 'level.dat').write_bytes(gzip.compress(raw))
        if prebuilt:
            (destination / 'level.dat_old').write_bytes(gzip.compress(raw))
        pack(destination, title, weapon, count, target_x)
        print('Created unplayed world:', destination)
    if not prebuilt:
        print('Prebuild travel:build natively with loaded arena chunks before first join.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    inputs = parser.add_mutually_exclusive_group(required=True)
    inputs.add_argument('--source', type=Path, help='Closed level.dat metadata for fresh templates')
    inputs.add_argument('--prebuilt', type=Path, help='Stopped, natively built template with no player history')
    parser.add_argument('--saves', type=Path, required=True)
    args = parser.parse_args()
    prepare(args.source, args.saves, args.prebuilt)

#!/usr/bin/env python3
"""Fresh metadata and a guarded, actual-hit custom-sword QA datapack. No belief injection."""
import argparse
import gzip
import json
import struct
from pathlib import Path
from prepare_emergency_eva_playtest import named, payload_end, string_end, compound_child

TITLE = 'MACS Custom Sword Check'
NS = 'qsword'


def tell(message, command=None, color='aqua'):
    value = {'text': message, 'color': color}
    if command:
        value['clickEvent'] = {'action': 'run_command', 'value': '/' + command}
    return 'tellraw @s ' + json.dumps(value)


def dump():
    return tell('[Record Maeve dump]', 'fd maeve dump', 'yellow')


def fresh_metadata(source):
    raw = gzip.decompress(source.read_bytes())
    _, start, _ = compound_child(raw, 3, 'Data')
    def string(value):
        value = value.encode(); return len(value).to_bytes(2, 'big') + value
    def strings(values):
        return b'\x08' + struct.pack('>i', len(values)) + b''.join(string(v) for v in values)
    replacements = {
        'LevelName': (8, string(TITLE)), 'GameType': (3, struct.pack('>i', 1)),
        'allowCommands': (1, b'\x01'), 'Difficulty': (1, b'\x02'), 'hardcore': (1, b'\x00'),
        'DifficultyLocked': (1, b'\x00'), 'Time': (4, struct.pack('>q', 0)),
        'DayTime': (4, struct.pack('>q', 102 * 24000)),
        'SpawnX': (3, struct.pack('>i', 8)), 'SpawnY': (3, struct.pack('>i', 65)),
        'SpawnZ': (3, struct.pack('>i', 8)), 'SpawnAngle': (5, struct.pack('>f', 135)),
        'initialized': (1, b'\x01'), 'confirmedExperimentalSettings': (1, b'\x01'),
        'LastPlayed': (4, struct.pack('>q', 1791435600000)),
        'enabled_features': (9, strings(['minecraft:vanilla'])),
        'DataPacks': (10, named(9, 'Enabled', strings(['vanilla', 'mod_data', 'file/custom-sword-check']))
                      + named(9, 'Disabled', strings([])) + b'\x00'),
    }
    remove = {'Player', 'neoforge:attachments', 'NeoForgeData', 'ForgeData',
              'ScheduledEvents', 'GameRules', 'CustomBossEvents'}
    result = bytearray(b'\x0a\x00\x00\x0a\x00\x04Data')
    at = start
    while raw[at]:
        begin = at; kind = raw[at]; payload = string_end(raw, at + 1)
        key = raw[at + 3:payload].decode(); at = payload_end(raw, kind, payload)
        if key in remove: continue
        if key in replacements:
            new_kind, value = replacements.pop(key); result.extend(named(new_kind, key, value))
        else: result.extend(raw[begin:at])
    for key, (kind, value) in replacements.items(): result.extend(named(kind, key, value))
    result.extend(b'\x00\x00')
    assert payload_end(result, 10, 3) == len(result)
    return bytes(result)


WITNESS = 'summon frozendawn:architect 0.5 65 0.5 {Tags:["qs_active"],PersistenceRequired:1b,Health:200.0f,Attributes:[{Name:"minecraft:generic.max_health",Base:200.0d},{Name:"minecraft:generic.movement_speed",Base:0.0d},{Name:"minecraft:generic.knockback_resistance",Base:1.0d}]}'
SCRIPTS = {
 'load': '''scoreboard objectives add qs dummy
scoreboard objectives add qs_joined dummy
execute unless score #stage qs matches -2147483648..2147483647 run scoreboard players set #stage qs 0''',
 'tick': '''execute as @a unless score @s qs_joined matches 1 run function qsword:join
execute if score #stage qs matches 4 run scoreboard players add #timer qs 1
execute if score #stage qs matches 4 if score #timer qs matches 620.. as @a[tag=qs_player,limit=1] run function qsword:ready''',
 'build': '''gamerule doMobSpawning false
gamerule doDaylightCycle false
gamerule doWeatherCycle false
gamerule keepInventory true
gamerule mobGriefing false
weather clear
difficulty normal
fd world preset default
fd world set phase 6 late
fill -16 64 -16 16 64 16 minecraft:bedrock
fill -16 65 -16 16 70 16 minecraft:air
fill -16 65 -16 16 71 -16 minecraft:bedrock
fill -16 65 16 16 71 16 minecraft:bedrock
fill -16 65 -16 -16 71 16 minecraft:bedrock
fill 16 65 -16 16 71 16 minecraft:bedrock
fill -16 71 -16 16 71 16 minecraft:bedrock
fill 64 64 -3 70 64 3 minecraft:bedrock
setblock 0 64 0 minecraft:blue_concrete
setblock 3 64 0 minecraft:lime_concrete
setblock 8 64 8 minecraft:gold_block
setblock -8 64 -8 minecraft:sea_lantern
setblock 8 64 -8 minecraft:sea_lantern
setblock -8 64 8 minecraft:sea_lantern
setblock 8 64 8 minecraft:sea_lantern
setblock 0 64 4 frozendawn:geothermal_core
setworldspawn 8 65 8
execute unless entity @e[tag=qs_archive] run summon frozendawn:architect 67.5 65 0.5 {Tags:["qs_archive"],NoAI:1b,NoGravity:1b,PersistenceRequired:1b}
scoreboard players set #built qs 1''',
 'kit': '''clear @s
item replace entity @s armor.head with frozendawn:eva_helmet[minecraft:unbreakable={}]
item replace entity @s armor.chest with frozendawn:eva_chestplate[minecraft:unbreakable={}]
item replace entity @s armor.legs with frozendawn:eva_leggings[minecraft:unbreakable={}]
item replace entity @s armor.feet with frozendawn:eva_boots[minecraft:unbreakable={}]
item replace entity @s weapon.offhand with minecraft:air
item replace entity @s hotbar.0 with minecraft:iron_sword
item replace entity @s hotbar.1 with frozendawn:acheronite_sword
item replace entity @s hotbar.2 with frozendawn:soul_harvest_blade
item replace entity @s hotbar.3 with minecraft:iron_axe
item replace entity @s hotbar.4 with minecraft:bow
item replace entity @s hotbar.5 with minecraft:shield
item replace entity @s hotbar.6 with minecraft:cooked_beef 64
give @s minecraft:arrow 64
give @s frozendawn:o2_tank_mk3 12
effect give @s minecraft:resistance infinite 4 true
effect give @s minecraft:regeneration infinite 2 true
effect give @s minecraft:night_vision infinite 0 true
effect give @s minecraft:saturation infinite 0 true''',
 'join': '''execute unless score #built qs matches 1 run return run tellraw @s {"text":"World preparation is incomplete. Stay in Creative; ask for the build log.","color":"red"}
scoreboard players set @s qs_joined 1
tag @s add qs_player
function qsword:kit
spawnpoint @s 8 65 8
tp @s 8.5 65 8.5 135 0
gamemode survival @s
function qsword:status''',
 'begin': '''execute unless entity @s[tag=qs_player] run return 0
execute unless score #stage qs matches 0 run return run function qsword:status
scoreboard players set #stage qs 1
scoreboard players set #round qs 0
function qsword:witness
''' + tell('Damage check: hotbar 1 = iron sword. Hit ONCE. Then use Acheronite (2), then Soul-Harvest (3), following each prompt.', color='green') + '\n' + dump(),
 'witness': '''tp @s 3.5 65 0.5 90 0
advancement revoke @s only qsword:iron
advancement revoke @s only qsword:acher
advancement revoke @s only qsword:soul
advancement revoke @s only qsword:axe
advancement revoke @s only qsword:arrow
''' + WITNESS + '\nfd architect record @e[tag=qs_active,limit=1] 1337',
 'iron_hit': '''execute if score #stage qs matches 1 run function qsword:iron_done''',
 'iron_done': '''scoreboard players set #stage qs 2
''' + tell('Iron contact recorded. Dump now: expect sword evidence 1, confidence 0.20. Then hit ONCE with Acheronite in hotbar 2.') + '\n' + dump(),
 'acher_hit': '''execute if score #stage qs matches 2 run function qsword:acher_done
execute if score #stage qs matches 5 run function qsword:training_done''',
 'acher_done': '''scoreboard players set #stage qs 3
''' + tell('Acheronite contact recorded. Dump now: expect DAMAGE_SWORD, weapon=frozendawn:acheronite_sword, no contradiction. Then hit ONCE with Soul-Harvest in hotbar 3.') + '\n' + dump(),
 'soul_hit': '''execute if score #stage qs matches 3 run function qsword:soul_done
execute if score #stage qs matches 6 run function qsword:training_done''',
 'soul_done': '''scoreboard players set #stage qs 10
''' + tell('Soul-Harvest contact recorded. Dump now: expect DAMAGE_SWORD, weapon=frozendawn:soul_harvest_blade, no contradiction. Same-encounter confidence should remain 0.20.') + '\n' + dump() + '\n' + tell('[Begin three real custom-sword training encounters]', 'function qsword:train', 'green'),
 'archive': '''execute as @e[tag=qs_active] run fd architect dump @s
execute as @e[tag=qs_active] run data merge entity @s {NoAI:1b,NoGravity:1b,Motion:[0.0d,0.0d,0.0d]}
tp @e[tag=qs_active] 67.5 65 0.5
tag @e[tag=qs_active] add qs_archive
tag @e[tag=qs_active] remove qs_active''',
 'train': '''execute unless score #stage qs matches 10 run return run function qsword:status
function qsword:archive
function qsword:gap''',
 'gap': '''scoreboard players set #stage qs 4
scoreboard players set #timer qs 0
tp @s 8.5 65 8.5 135 0
''' + tell('Quiet gap: no eligible Architect is observing you. Wait 31 unpaused seconds, or click below. Time advances only during this empty gap.') + '\n' + tell('[Skip empty gap: 620 ticks]', 'tick sprint 620t', 'yellow'),
 'ready': '''execute unless score #stage qs matches 4 run return 0
execute if score #round qs matches 0 run function qsword:train_acher
execute if score #round qs matches 1 run function qsword:train_soul
execute if score #round qs matches 2 run function qsword:train_acher
execute if score #round qs matches 3 run function qsword:guard_ready
execute if score #round qs matches 4 run function qsword:axe_ready
execute if score #round qs matches 5 run function qsword:arrow_ready''',
 'train_acher': '''scoreboard players set #stage qs 5
function qsword:witness
''' + tell('Training: hit ONCE with Acheronite in hotbar 2. Each separate encounter should add 0.20 sword confidence.', color='green'),
 'train_soul': '''scoreboard players set #stage qs 6
function qsword:witness
''' + tell('Training: hit ONCE with Soul-Harvest in hotbar 3. Each separate encounter should add 0.20 sword confidence.', color='green'),
 'training_done': '''scoreboard players add #round qs 1
''' + tell('Training contact recorded. Capture the dump; the next witness waits for the empty encounter gap.') + '\n' + dump() + '\nfunction qsword:archive\nfunction qsword:gap',
 'guard_ready': '''scoreboard players set #stage qs 20
''' + tell('Shield round ready. Expected earned sword confidence: 0.80, evidence 4, contradictions 0. Record the dump before starting.') + '\n' + dump() + '\n' + tell('[Start normal Architect shield round]', 'function qsword:guard', 'green'),
 'guard': '''execute unless score #stage qs matches 20 run return run function qsword:status
scoreboard players set #stage qs 30
tp @s 3.5 65 0.5 90 0
summon frozendawn:architect -0.5 65 0.5 {Tags:["qs_active","qs_guard"],PersistenceRequired:1b}
fd architect record @e[tag=qs_active,limit=1] 1337
''' + tell('Watch before attacking. Wait for an actual raised shield, then try Acheronite (2) and Soul-Harvest (3) from the front. Dump after EACH blocked contact. Expect SHIELD_BLOCK_SWORD, no sword contradiction. Protection keeps you safe; this is a recognition check.') + '\n' + dump() + '\n' + tell('[Both shield checks recorded: proceed to axe and arrow controls]', 'function qsword:controls', 'gold'),
 'controls': '''execute unless score #stage qs matches 30 run return run function qsword:status
function qsword:archive
scoreboard players set #round qs 4
function qsword:gap''',
 'axe_ready': '''scoreboard players set #stage qs 40
function qsword:witness
''' + tell('Control: hit ONCE with the iron axe in hotbar 4. Expect NON_SWORD_MELEE and one new contradiction.', color='gold'),
 'axe_hit': '''execute unless score #stage qs matches 40 run return 0
''' + dump() + '\nfunction qsword:archive\nscoreboard players set #round qs 5\nfunction qsword:gap',
 'arrow_ready': '''scoreboard players set #stage qs 41
function qsword:witness
''' + tell('Control: SHOOT the witness using the bow in hotbar 5. The separate encounter allows a second contradiction.', color='gold'),
 'arrow_hit': '''execute unless score #stage qs matches 41 run return 0
scoreboard players set #stage qs 50
''' + tell('Arrow contact recorded. Expect DAMAGE_PROJECTILE and another sword contradiction. Capture the dump; compare actual evidence before calling this a pass.') + '\n' + dump() + '\n' + tell('[Finish and preserve actors/history]', 'function qsword:finish', 'green'),
 'finish': '''execute unless entity @s[tag=qs_player] run return 0
function qsword:archive
scoreboard players set #stage qs 99
tp @s 8.5 65 8.5 135 0
''' + tell('Stopped. Actors are archived alive and history is retained. This world does not reset completed checks. Describe what you saw and capture the dump.') + '\n' + dump(),
 'status': tell('MACS Custom Sword Check: full EVA, oxygen, protected Survival, late Phase 6. Hotbar 1 iron / 2 Acheronite / 3 Soul-Harvest / 4 axe / 5 bow. Actual evidence decides the result.') + '\n' + 'tellraw @s [{"text":"Stage: "},{"score":{"name":"#stage","objective":"qs"}}]\n' + 'execute if score #stage qs matches 0 run ' + tell('[Begin damage recognition check]', 'function qsword:begin', 'green') + '\n' + dump() + '\n' + tell('[Emergency stop; preserve history]', 'function qsword:finish', 'red'),
}


def write_pack(pack):
    (pack / 'data/qsword/function').mkdir(parents=True, exist_ok=True)
    (pack / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 48, 'description': TITLE}}))
    for name, text in SCRIPTS.items():
        (pack / f'data/qsword/function/{name}.mcfunction').write_text(text + '\n')
    tags = pack / 'data/minecraft/tags/function'; tags.mkdir(parents=True, exist_ok=True)
    for name in ('load', 'tick'):
        (tags / f'{name}.json').write_text(json.dumps({'values': ['qsword:' + name]}))
    adv = pack / 'data/qsword/advancement'; adv.mkdir(parents=True, exist_ok=True)
    for key, item in [('iron','minecraft:iron_sword'),('acher','frozendawn:acheronite_sword'),
                      ('soul','frozendawn:soul_harvest_blade'),('axe','minecraft:iron_axe'),('arrow','minecraft:bow')]:
        conditions = {'player': {'equipment': {'mainhand': {'items': [item]}}},
                      'entity': {'type': 'frozendawn:architect', 'nbt': '{Tags:["qs_active"]}'},
                      'damage': {'dealt': {'min': .1}, 'blocked': False,
                                 'type': {'tags': [{'id': 'minecraft:is_projectile', 'expected': key == 'arrow'}]}}}
        (adv / f'{key}.json').write_text(json.dumps({'criteria': {'contact': {'trigger': 'minecraft:player_hurt_entity',
            'conditions': conditions}}, 'rewards': {'function': f'qsword:{key}_hit'}}))


def prepare(source, destination):
    if destination.exists(): raise SystemExit(f'Refusing to overwrite {destination}')
    destination.mkdir(parents=True)
    (destination / 'level.dat').write_bytes(gzip.compress(fresh_metadata(source)))
    write_pack(destination / 'datapacks/custom-sword-check')
    print(f'Created fresh {destination}. Prebuild qsword:build natively before first player joins.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True, help='Closed level.dat metadata only')
    parser.add_argument('--destination', type=Path, required=True)
    args = parser.parse_args(); prepare(args.source, args.destination)

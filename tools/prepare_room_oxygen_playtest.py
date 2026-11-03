#!/usr/bin/env python3
"""O2 pool/gauge and ordinary-flame starvation replay; seed metadata only and guarded one-time setup."""
import argparse
import json
from pathlib import Path
import prepare_thermal_ground_playtest as seed
NS = 'room_oxygen_check'
GUARD = 'execute unless score #built roxygen matches 1 run return 0'
def tell(text):
    return 'tellraw @s ' + json.dumps({'text': text, 'color': 'aqua'})
def row(buttons):
    return 'tellraw @s ' + json.dumps([{'text': ''}] + [
        {'text': label+'  ', 'color': 'aqua', 'clickEvent': {'action': 'run_command', 'value': command if command.startswith('/') else f'/function {NS}:{command}'}}
        for label, command in buttons])
def scripts():
    def action(commands, text):
        return '\n'.join([GUARD, *commands, tell(text)])
    setup = [
        'execute unless score #built roxygen matches 0 run return 0',
        'scoreboard players set #built roxygen -1', 'gamemode creative @s',
        'gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false', 'gamerule keepInventory true',
        'weather clear', 'time set noon', 'fd world preset default', 'fd world set phase 6 late', 'fd world pause',
        'fill -7 64 -4 7 187 4 minecraft:air', 'fill -7 188 -4 7 319 4 minecraft:air',
        'fill -5 64 -3 5 68 3 minecraft:glass hollow', 'fill -5 64 -3 5 64 3 minecraft:stone',
        'fill 0 65 -2 0 67 2 minecraft:stone',
        'setblock 0 66 0 frozendawn:geothermal_core',
        'setblock -3 66 -2 frozendawn:oxygen_gauge[facing=south]', 'setblock 3 66 -2 frozendawn:oxygen_gauge[facing=south]',
        'setblock 0 65 -1 minecraft:furnace[facing=west]', 'setblock 0 66 -1 minecraft:furnace[facing=east]',
        'fill -2 64 7 2 64 10 minecraft:stone', 'setworldspawn 0 65 8', 'spawnpoint @s 0 65 8', 'tp @s -2.5 65 .5 180 0',
        'effect give @s minecraft:night_vision infinite 0 true', 'give @s frozendawn:oxygen_gauge 4',
        'give @s patchouli:guide_book[patchouli:book="frozendawn:frozen_dawn_guide"]',
        'give @s minecraft:flint_and_steel', 'give @s minecraft:glass 32', 'give @s minecraft:coal_block 8',
        'setblock -3 65 -3 minecraft:crafting_table', 'scoreboard players set #cow roxygen 0',
        'scoreboard players set #built roxygen 1', f'function {NS}:controls',
        tell('QA: two sealed rooms share ONE Core in the dividing wall. Fresh world; no older world data copied. Wait for SUPPORTED before adding burners.')]
    furnace_nbt = '{BurnTime:24000,CookTime:0,CookTimeTotal:200,Items:[{Slot:0b,id:"minecraft:raw_iron",count:64},{Slot:1b,id:"minecraft:coal_block",count:64}]}'
    burners = ['setblock 0 65 -1 minecraft:furnace[facing=west,lit=true]'+furnace_nbt,
               'setblock 0 66 -1 minecraft:furnace[facing=east,lit=true]'+furnace_nbt,
               'setblock 2 65 0 minecraft:candle[candles=4,lit=true]']
    burners += [f'setblock {x} 65 {z} minecraft:torch' for x,z in [(-3,-1),(-3,1),(-2,-1),(-2,1)]]
    flame_fixtures = [
        ('Torch', '-3 65 1', 'minecraft:torch'),
        ('Wall torch', '-4 66 1', 'minecraft:wall_torch[facing=east]'),
        ('Lantern', '-3 65 -1', 'minecraft:lantern'),
        ('Candles', '-2 65 1', 'minecraft:white_candle[candles=4,lit=true]'),
        ('Candle cake', '2 65 0', 'minecraft:candle_cake[lit=true]'),
        ('Campfire', '-2 65 -1', 'minecraft:campfire[lit=true]'),
        ('Fire', '-4 65 -1', 'minecraft:fire'),
        ('MiteAway', '2 65 -1', 'frozendawn:miteaway[lit=true,charged=true]')]
    flame_load = [f'function {NS}:safe', f'function {NS}:flame_low', f'function {NS}:cow',
        'execute unless score #flame_cow roxygen matches 1 run summon minecraft:cow 3.5 65 1.5 {Tags:["room_oxygen_qa_flames"],Invulnerable:1b,NoAI:1b,PersistenceRequired:1b}',
        'scoreboard players set #flame_cow roxygen 1',
        *burners[:2], 'setblock -4 64 -1 minecraft:netherrack',
        *['setblock '+pos+' '+block for _,pos,block in flame_fixtures],
        'setblock 3 65 1 minecraft:soul_torch', 'setblock 3 65 -1 minecraft:soul_campfire',
        'setblock 2 65 1 minecraft:soul_lantern', 'setblock 4 64 -1 minecraft:soul_soil',
        'setblock 4 65 -1 minecraft:soul_fire', 'give @s minecraft:flint_and_steel']
    flame_read = [GUARD]
    for label,pos,block in flame_fixtures:
        predicate=block.split('[')[0]+('[lit=true]' if 'lit=true' in block else '')
        flame_read += ['execute if block '+pos+' '+predicate+' run '+tell(label+': ON'),
                       'execute unless block '+pos+' '+predicate+' run '+tell(label+': OFF')]
    flame_read += [row([('[Back]', 'controls')])]
    return {
        'load': 'scoreboard objectives add roxygen dummy\nexecute unless score #built roxygen matches 1 run scoreboard players set #built roxygen 0\n'+f'execute if score #built roxygen matches 1 as @a run function {NS}:controls',
        'tick': f'execute if score #built roxygen matches 0 as @a[limit=1] at @s run function {NS}:setup',
        'setup': '\n'.join(setup),
        'flame_controls': '\n'.join([GUARD,
            tell('FLAME CHECK'),
            row([('[1 Load]', 'flames'), ('[2 Read]', '/fd world oxygen here')]),
            tell('Wait 25s. Ordinary flames go out; soul flames stay lit.'),
            row([('[3 Restore O2]', 'flame_support'), ('[Lights]', 'flame_read')]),
            tell('Wait 3s. Furnaces restart. Relight other flames by hand.'),
            row([('[Low O2]', 'flame_low'), ('[Furnaces]', 'read_furnaces'), ('[More]', 'more')])]),
        'controls': '\n'.join([GUARD,
            tell('O2 WARNING CHECK'),
            row([('[Creative]', 'safe'), ('[Restore O2]', 'flame_support')]),
            tell('Wait 3s for green. Keep Creative for this check.'),
            row([('[Low O2]', 'flame_low'), ('[Read]', '/fd world oxygen here')]),
            tell('Watch O2 SHORTAGE + beeps BEFORE red NO O2.'),
            row([('[Survival]', 'survival'), ('[EVA]', 'eva'), ('[Flame test]', 'flame_controls'), ('[More]', 'more')]),
            'return 1']),
        'repair_cows': '''execute unless score #built roxygen matches 1 run return 0
execute unless block -5 64 0 minecraft:stone run return 0
execute unless block 5 64 0 minecraft:stone run return 0
execute unless entity @e[type=minecraft:cow,tag=room_oxygen_qa] run summon minecraft:cow -1.5 65 1.5 {Tags:["room_oxygen_qa"],Invulnerable:1b,NoAI:1b,PersistenceRequired:1b}
execute unless entity @e[type=minecraft:cow,tag=room_oxygen_qa_flames] run summon minecraft:cow 3.5 65 1.5 {Tags:["room_oxygen_qa_flames"],Invulnerable:1b,NoAI:1b,PersistenceRequired:1b}
execute as @e[type=minecraft:cow,tag=room_oxygen_qa] run data merge entity @s {Invulnerable:1b,NoAI:1b,PersistenceRequired:1b,Health:10.0f,Motion:[0.0d,0.0d,0.0d]}
execute as @e[type=minecraft:cow,tag=room_oxygen_qa_flames] run data merge entity @s {Invulnerable:1b,NoAI:1b,PersistenceRequired:1b,Health:10.0f,Motion:[0.0d,0.0d,0.0d]}
tp @e[type=minecraft:cow,tag=room_oxygen_qa,limit=1,sort=nearest] -1.5 65 1.5
tp @e[type=minecraft:cow,tag=room_oxygen_qa_flames,limit=1,sort=nearest] 3.5 65 1.5
scoreboard players set #cow roxygen 1
scoreboard players set #flame_cow roxygen 1
scoreboard players set #cows_inside roxygen 0
execute as @e[type=minecraft:cow,x=-4,y=65,z=-2,dx=8,dy=3,dz=4] run scoreboard players add #cows_inside roxygen 1
tellraw @s [{"text": "Left QA cow: health="}, {"nbt": "Health", "entity": "@e[type=minecraft:cow,tag=room_oxygen_qa,limit=1,sort=nearest]"}, {"text": " | invulnerable="}, {"nbt": "Invulnerable", "entity": "@e[type=minecraft:cow,tag=room_oxygen_qa,limit=1,sort=nearest]"}, {"text": " | NoAI="}, {"nbt": "NoAI", "entity": "@e[type=minecraft:cow,tag=room_oxygen_qa,limit=1,sort=nearest]"}, {"text": " | position="}, {"nbt": "Pos", "entity": "@e[type=minecraft:cow,tag=room_oxygen_qa,limit=1,sort=nearest]"}]
tellraw @s [{"text": "Right QA cow: health="}, {"nbt": "Health", "entity": "@e[type=minecraft:cow,tag=room_oxygen_qa_flames,limit=1,sort=nearest]"}, {"text": " | invulnerable="}, {"nbt": "Invulnerable", "entity": "@e[type=minecraft:cow,tag=room_oxygen_qa_flames,limit=1,sort=nearest]"}, {"text": " | NoAI="}, {"nbt": "NoAI", "entity": "@e[type=minecraft:cow,tag=room_oxygen_qa_flames,limit=1,sort=nearest]"}, {"text": " | position="}, {"nbt": "Pos", "entity": "@e[type=minecraft:cow,tag=room_oxygen_qa_flames,limit=1,sort=nearest]"}]
tellraw @s [{"text": "Room cows: "}, {"score": {"name": "#cows_inside", "objective": "roxygen"}}, {"text": " (target 2)."}]
return 1''',
        'survival': action(['gamemode survival @s'], 'Survival. Creative is the safety button.'),
        'more': '\n'.join([GUARD,
            tell('EXTRA CONTROLS'),
            row([('[Left room]', 'left'), ('[Right room]', 'right')]),
            row([('[Creative]', 'safe'), ('[EVA Survival]', 'eva')]),
            row([('[Full budgets]', '/fd world oxygen'), ('[Furnaces]', 'read_furnaces')]),
            row([('[Reset burners]', 'burners'), ('[Burners off]', 'off')]),
            row([('[Breach]', 'breach'), ('[Seal]', 'patch'), ('[Back]', 'controls')])]),
        'flames': action(flame_load, 'Loaded. Wait 25 seconds, then Read.'),
        'flame_read': '\n'.join(flame_read),
        'flame_support': action(['setblock 0 67 0 frozendawn:geothermal_core', 'setblock 0 65 0 frozendawn:geothermal_core'], 'Supply: 24. Furnaces restart; other flames stay out.'),
        'flame_low': action(['setblock 0 67 0 minecraft:stone', 'setblock 0 65 0 minecraft:stone'], 'Supply: 8.'),
        'read_furnaces': '\n'.join([GUARD,
            tell('FURNACES'),
            *['\n'.join([
                'execute if data block '+pos+' FrozenDawnOxygenStarved run tellraw @s '+json.dumps([
                    {'text':label+' | PAUSED | fuel='}, {'nbt':'FrozenDawnPausedBurnTime','block':pos},
                    {'text':' | cook='}, {'nbt':'CookTime','block':pos},
                    {'text':' | queued='}, {'nbt':'Items[{Slot:1b}].count','block':pos}
                ]),
                'execute unless data block '+pos+' FrozenDawnOxygenStarved run tellraw @s '+json.dumps([
                    {'text':label+' | ACTIVE | fuel='}, {'nbt':'BurnTime','block':pos},
                    {'text':' | cook='}, {'nbt':'CookTime','block':pos},
                    {'text':' | queued='}, {'nbt':'Items[{Slot:1b}].count','block':pos}
                ])
            ]) for label,pos in [('Lower','0 65 -1'),('Upper','0 66 -1')]]]),
        'left': action(['tp @s -2.5 65 .5 180 0'], 'Left room.'),
        'right': action(['tp @s 3.5 65 .5 180 0'], 'Right room.'),
        'burners': action(burners, 'Burner fixtures replaced and fueled.'),
        'eva': action(['item replace entity @s armor.head with frozendawn:eva_helmet', 'item replace entity @s armor.chest with frozendawn:eva_chestplate', 'item replace entity @s armor.legs with frozendawn:eva_leggings', 'item replace entity @s armor.feet with frozendawn:eva_boots', 'item replace entity @s hotbar.8 with frozendawn:o2_tank_mk3', 'gamemode survival @s'], 'EVA Survival.'),
        'safe': action(['gamemode creative @s'], 'Creative.'),
        'cow': '\n'.join([GUARD,'execute if score #cow roxygen matches 1 run return 0','summon minecraft:cow -1.5 65 1.5 {Tags:["room_oxygen_qa"],Invulnerable:1b,NoAI:1b,PersistenceRequired:1b}','scoreboard players set #cow roxygen 1',tell('Cow ready (+5 demand).')]),
        'second': action(['setblock 0 67 0 frozendawn:geothermal_core'], 'Second Core added (+8 supply).'),
        'second_off': action(['setblock 0 67 0 minecraft:stone'], 'Second Core removed (-8 supply).'),
        'off': action(['setblock 0 65 -1 minecraft:furnace[facing=west]','setblock 0 66 -1 minecraft:furnace[facing=east]','setblock 2 65 0 minecraft:candle[candles=4,lit=false]',*['setblock '+str(x)+' 65 '+str(z)+' minecraft:air' for x,z in [(-3,-1),(-3,1),(-2,-1),(-2,1)]]], 'Old burner fixtures cleared.'),
        'breach': action(['setblock -5 66 0 minecraft:air'], 'Left room breached.'),
        'patch': action(['setblock -5 66 0 minecraft:glass'], 'Sealed. Wait 5 seconds.')}
def pack_only(destination, hooks=False):
    import prepare_room_oxygen_storage_playtest as storage
    storage.pack_only(destination)
    import prepare_core_split_playtest as core
    core.pack_only(destination)
    for name, body in scripts().items():
        p=destination/f'data/{NS}/function/{name}.mcfunction';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body+'\n')
    if hooks:
        (destination/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':48,'description':'Room O2 Check'}})+'\n')
        for name in ('load','tick'):
            p=destination/f'data/minecraft/tags/function/{name}.json';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps({'values':[f'{NS}:{name}']})+'\n')
if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',type=Path);parser.add_argument('--destination',type=Path)
    parser.add_argument('--pack-only',type=Path);args=parser.parse_args()
    if args.pack_only:
        pack_only(args.pack_only);raise SystemExit(0)
    if not args.source or not args.destination:parser.error('--source and --destination are required for a new save')
    seed.TITLE='Room O2 Check';seed.PACK='room-oxygen-check';seed.NS=NS;seed.scripts=scripts
    seed.prepare(args.source,args.destination)

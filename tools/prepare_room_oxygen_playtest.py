#!/usr/bin/env python3
"""Fresh observational O2 pool/gauge replay; seed metadata only and guarded one-time setup."""
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
    return {
        'load': 'scoreboard objectives add roxygen dummy\nexecute unless score #built roxygen matches 1 run scoreboard players set #built roxygen 0\n'+f'execute if score #built roxygen matches 1 as @a run function {NS}:controls',
        'tick': f'execute if score #built roxygen matches 0 as @a[limit=1] at @s run function {NS}:setup',
        'setup': '\n'.join(setup),
        'controls': '\n'.join([GUARD,
            tell('Room O2 Check: accounting only. Overload will NOT automatically extinguish flames or suffocate you in this slice.'),
            tell('1. Both panels should show SUPPLY 8 and DEMAND 0 in Creative. The room counter is 2; supply is not 16.'),
            row([('[Left panel]', 'left'), ('[Right panel]', 'right'), ('[Read budgets]', '/fd world oxygen')]),
            row([('[Add burners: +8]', 'burners'), ('[EVA Survival: +1]', 'eva'), ('[Creative safety]', 'safe')]),
            tell('2. Add burners after air is ready: 2 furnaces (6), 4 torches (1), four candles (1) = 8. Yellow NEAR CAPACITY. Survival adds 1: STRAINED at 9.'),
            row([('[One cow: +5]', 'cow'), ('[Second Core: +8]', 'second'), ('[Remove second Core]', 'second_off')]),
            row([('[Burners off]', 'off'), ('[Breach left]', 'breach'), ('[Patch left]', 'patch')]),
            tell('3. Cow is an invulnerable stationary QA animal, spawned only once. Shared supply with second Core is 16. Breach is a REAL pressure event; use Creative or EVA.'),
            tell('4. Patch and wait five sealed seconds for Core recovery. Surviving burners remain; spent torches need relighting. No old world resets or forced chunks.'),
            tell('Save/reopen after taking readings: cached telemetry starts WAITING, then reconstructs. Restore these links: /function room_oxygen_check:controls')]),
        'left': action(['tp @s -2.5 65 .5 180 0'], 'Left panel: LOCAL is one room; SUPPLY/DEMAND are the entire shared pool.'),
        'right': action(['tp @s 3.5 65 .5 180 0'], 'Right panel: the same shared supply appears here, not a second output.'),
        'burners': action(burners, 'Fresh QA burner fixtures fueled. Total combustion is 8 units; first slice does not enforce starvation.'),
        'eva': action(['item replace entity @s armor.head with frozendawn:eva_helmet', 'item replace entity @s armor.chest with frozendawn:eva_chestplate', 'item replace entity @s armor.legs with frozendawn:eva_leggings', 'item replace entity @s armor.feet with frozendawn:eva_boots', 'item replace entity @s hotbar.8 with frozendawn:o2_tank_mk3', 'gamemode survival @s'], 'Survival breathing adds 1. Full EVA provided for this cold diagnostic fixture.'),
        'safe': action(['gamemode creative @s'], 'Creative removes your breathing demand; the animal and flames still count.'),
        'cow': '\n'.join([GUARD,'execute if score #cow roxygen matches 1 run return 0','summon minecraft:cow -1.5 65 1.5 {Tags:["room_oxygen_qa"],Invulnerable:1b,NoAI:1b,PersistenceRequired:1b}','scoreboard players set #cow roxygen 1',tell('One adult cow adds 5 units; this button cannot repeatedly spawn cows.')]),
        'second': action(['setblock 0 67 0 frozendawn:geothermal_core'], 'Second shared Core adds 8 once: total supply 16.'),
        'second_off': action(['setblock 0 67 0 minecraft:stone'], 'Second Core removed; partition remains sealed. Supply returns to 8.'),
        'off': action(['setblock 0 65 -1 minecraft:furnace[facing=west]','setblock 0 66 -1 minecraft:furnace[facing=east]','setblock 2 65 0 minecraft:candle[candles=4,lit=false]',*['setblock '+str(x)+' 65 '+str(z)+' minecraft:air' for x,z in [(-3,-1),(-3,1),(-2,-1),(-2,1)]]], 'Only the QA burner fixtures are removed/emptied. Cow and player demand stay observable.'),
        'breach': action(['setblock -5 66 0 minecraft:air'], 'Left exterior opened: real breach, red NO SEAL; the sealed right room remains independent.'),
        'patch': action(['setblock -5 66 0 minecraft:glass'], 'Wall restored. Wait for normal Core air recovery; this function does not refill air directly.')}
def pack_only(destination, hooks=False):
    for name, body in scripts().items():
        p=destination/f'data/{NS}/function/{name}.mcfunction';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body+'\n')
    if hooks:
        (destination/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':48,'description':'Room O2 Check'}})+'\n')
        for name in ('load','tick'):
            p=destination/f'data/minecraft/tags/function/{name}.json';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps({'values':[f'{NS}:{name}']})+'\n')
if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--source',type=Path,required=True);parser.add_argument('--destination',type=Path,required=True);args=parser.parse_args()
    seed.TITLE='Room O2 Check';seed.PACK='room-oxygen-check';seed.NS=NS;seed.scripts=scripts
    seed.prepare(args.source,args.destination)

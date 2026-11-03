"""QA-only fixed Survival return, with two staged existing pawns and earned history."""
from macs_base_diversion import scripts as base_scripts

BASE = (3881, 101, 4006)
OUTPOST = (3881, 101, 3914)
WEST = '3c74d400-ef46-4551-a264-157ed46407b4'
EAST = 'caa24924-f12a-43f0-bf55-f9c314287273'


def scripts(tell):
    original = base_scripts(tell)
    terrain = []
    for x in range(3868, 4021, 16):
        hi = min(x + 15, 4020)
        terrain += [f'fill {x} 92 3904 {hi} 100 4028 frozendawn:frozen_dirt',
                    f'fill {x} 101 3904 {hi} 106 4028 minecraft:air',
                    f'fill {x} 101 3904 {hi} 101 4028 minecraft:snow[layers=1]']
    outpost = '''fill ~-5 ~-1 ~-5 ~5 ~-1 ~5 minecraft:stone_bricks
fill ~-5 ~ ~-5 ~5 ~3 ~5 minecraft:spruce_planks hollow
fill ~-4 ~ ~-4 ~4 ~2 ~4 minecraft:air
fill ~ ~ ~5 ~ ~1 ~5 minecraft:air
setblock ~3 ~ ~3 frozendawn:geothermal_core
setblock ~-3 ~ ~3 frozendawn:diamond_thermal_heater[lit=true]{BurnTime:19200}
setblock ~-3 ~ ~-3 minecraft:barrel
item replace block ~-3 ~ ~-3 container.0 with minecraft:coal 16
item replace block ~-3 ~ ~-3 container.1 with minecraft:raw_iron 8
item replace block ~-3 ~ ~-3 container.2 with minecraft:spruce_log 16
setblock ~ ~2 ~ minecraft:lantern[hanging=true]'''
    for z in range(3922, 3996, 8):
        terrain += [f'setblock 3884 101 {z} minecraft:cobblestone_wall',
                    f'setblock 3884 102 {z} minecraft:lantern']
    prepare = f'''execute unless score #stage mpc matches 92 run return 0
execute unless entity {WEST} run return 0
execute unless entity {EAST} run return 0
execute store result score #base_roster mpc if entity @e[tag=macs_pawn_natural,nbt={{NoAI:1b}},nbt=!{{Health:0.0f}}]
execute unless score #base_roster mpc matches 2 run return 0
scoreboard players set #stage mpc 130
function macs_pawn:base_safe_mode
fd world preset default
fd world set phase 0
forceload add 3868 3904 4020 4028
function macs_pawn:return_terrain
execute positioned 3881 101 4006 run function macs_pawn:base_structure
execute positioned 3881 101 3914 run function macs_pawn:return_outpost
tp {WEST} 3941.5 101 4002.5
tp {EAST} 3941.5 101 4010.5
forceload remove all
function macs_pawn:base_kit
tag @s add macs_base_player
gamerule keepInventory true
gamerule doMobSpawning true
gamerule doDaylightCycle true
gamerule doWeatherCycle true
difficulty normal
time set noon
weather clear
tp @s 3881.5 101 3914.5 0 0
spawnpoint @s 3881 101 4006
scoreboard objectives add mb_deaths deathCount
scoreboard players set @s mb_deaths 0
scoreboard players set #return_timer mpc 0
scoreboard players set #return_dispatch mpc -1
scoreboard players set #return_arrival mpc -1
scoreboard players set #return_clear_ticks mpc 0
scoreboard players set #return_left_early mpc 0
scoreboard players set #return_result mpc 0
''' + tell('Return check prepared. The two existing pawns are staged near the base. Keep paused for verification; the run has not started.', color='green') + '\nreturn 1'
    start = '''execute unless score #stage mpc matches 130 run return 0
execute store result score #base_roster mpc if entity @e[tag=macs_pawn_natural,nbt={NoAI:1b},nbt=!{Health:0.0f}]
execute unless score #base_roster mpc matches 2 run return 0
effect clear @s
function macs_pawn:base_live_mode
scoreboard players set #return_timer mpc 0
scoreboard players set #stage mpc 132
execute as @e[tag=macs_pawn_natural] run fd architect record @s 1337
execute as @e[tag=macs_pawn_natural] run fd architect mark @s BASE_RETURN_RELEASE
execute as @e[tag=macs_pawn_natural] run data merge entity @s {NoAI:0b,Glowing:0b}
''' + tell('Gather coal and raw iron from this outpost barrel. Stay here until the RETURN HOME message at 30 seconds. Then follow the lanterns south to the base, refuel the heater and smelt one iron. Keep your suit on and defend normally. This ends at 90 seconds.', color='aqua') + '\nreturn 1'
    sample = '''execute positioned 3881 101 4006 store result score #return_near mpc if entity @e[tag=macs_pawn_natural,distance=..96,nbt=!{Health:0.0f}]
execute positioned 3881 101 4006 store result score #return_all_near mpc if entity @e[type=frozendawn:architect,distance=..96,nbt=!{Health:0.0f}]
execute store result score #return_alive mpc if entity @e[tag=macs_pawn_natural,nbt=!{Health:0.0f}]
scoreboard players set #return_assigned mpc 0
execute as @e[tag=macs_pawn_natural] if data entity @s NeoForgeData.macsConvergence run scoreboard players add #return_assigned mpc 1
execute if score #return_assigned mpc matches 2 if score #return_dispatch mpc matches -1 run scoreboard players operation #return_dispatch mpc = #return_timer mpc
execute positioned 3881 101 4006 if entity @s[distance=..8] if score #return_arrival mpc matches -1 run scoreboard players operation #return_arrival mpc = #return_timer mpc
execute positioned 3881 101 4006 if entity @s[distance=..8] if score #return_near mpc matches 0 run scoreboard players add #return_clear_ticks mpc 20
return 1'''
    tick = '''execute unless score #stage mpc matches 132 run return 0
scoreboard players add #return_timer mpc 1
scoreboard players set #return_interval mpc 20
scoreboard players operation #return_mod mpc = #return_timer mpc
scoreboard players operation #return_mod mpc %= #return_interval mpc
execute if score #return_mod mpc matches 0 run function macs_pawn:return_sample
execute if score #return_timer mpc matches ..599 positioned 3881 101 3914 unless entity @s[distance=..12] run scoreboard players set #return_left_early mpc 1
execute if score @s mb_deaths matches 1.. run scoreboard players set #return_result mpc -2
execute if score @s mb_deaths matches 1.. run return run function macs_pawn:return_end
execute if score #return_timer mpc matches 600.. if score #return_dispatch mpc matches -1 run scoreboard players set #return_result mpc -1
execute if score #return_result mpc matches -1 run return run function macs_pawn:return_end
execute if score #return_timer mpc matches 600 run ''' + tell('RETURN HOME: follow the lanterns south, refuel the heater and smelt one iron. Close the airlock behind you.', color='aqua') + '''
execute if score #return_timer mpc matches 1800.. run function macs_pawn:return_end
return 1'''
    end = '''execute unless score #stage mpc matches 132 run return 0
scoreboard players set #stage mpc 133
execute as @e[tag=macs_pawn_natural] run fd architect mark @s BASE_RETURN_END
execute as @e[tag=macs_pawn_natural] run fd architect dump @s
execute as @e[tag=macs_pawn_natural] run data merge entity @s {NoAI:1b}
gamemode spectator @s
fd world set phase 0
''' + tell('RETURN CHECK COMPLETE. Pause and describe whether you reached the base and got the upkeep done, and what the Architects did. Results still need review.', color='green') + '\nreturn 1'
    status = original['base_status']
    for old, new in ((3937, 3878), (3983, 4009), (3943, 3884)):
        status = status.replace(str(old), str(new))
    return {'return_terrain': '\n'.join(terrain), 'return_outpost': outpost,
            'return_prepare': prepare, 'return_start': start, 'return_sample': sample,
            'return_tick': tick, 'return_end': end, 'return_status': status}

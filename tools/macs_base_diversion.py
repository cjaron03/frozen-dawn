"""QA-only Survival base replay; reuses saved natural pawns and earned history."""


def structure():
    # Relative to the base center. Native QA uses the same function at an isolated origin.
    lines = [
        'fill ~-7 ~-1 ~-7 ~7 ~4 ~7 minecraft:air',
        'fill ~-6 ~-1 ~-6 ~6 ~-1 ~6 minecraft:stone_bricks',
        'fill ~-6 ~ ~-6 ~6 ~4 ~6 minecraft:stone_bricks hollow',
        'fill ~-5 ~ ~-5 ~5 ~3 ~5 minecraft:air',
        'fill ~-6 ~1 ~-3 ~-6 ~2 ~0 frozendawn:insulated_glass',
        'fill ~6 ~1 ~-3 ~6 ~2 ~0 frozendawn:insulated_glass',
        'fill ~-3 ~1 ~-6 ~0 ~2 ~-6 frozendawn:insulated_glass',
        'fill ~-6 ~4 ~-6 ~6 ~4 ~6 minecraft:deepslate_tiles',
        'fill ~-6 ~5 ~-6 ~6 ~5 ~6 minecraft:snow[layers=2]',
        # Two enclosed airlocks; wooden doors are usable by the player and remain breachable.
    ]
    for side in (-1, 1):
        lo, hi = sorted((side * 6, side * 10))
        lines += [f'fill ~-1 ~-1 ~{lo} ~1 ~2 ~{hi} minecraft:stone_bricks hollow',
                  f'fill ~ ~ ~{lo+1} ~ ~1 ~{hi-1} minecraft:air']
        facing = 'north' if side < 0 else 'south'
        for z in (side * 6, side * 10):
            lines += [f'setblock ~ ~ ~{z} minecraft:spruce_door[facing={facing},half=lower,open=false]',
                      f'setblock ~ ~1 ~{z} minecraft:spruce_door[facing={facing},half=upper,open=false]']
    lines += [
        'setblock ~3 ~ ~3 frozendawn:geothermal_core',
        'setblock ~-3 ~ ~3 frozendawn:diamond_thermal_heater[lit=true]{BurnTime:19200}',
        'setblock ~-4 ~ ~4 minecraft:barrel',
        'item replace block ~-4 ~ ~4 container.0 with minecraft:coal 8',
        'item replace block ~-4 ~ ~4 container.1 with minecraft:cooked_beef 16',
        'item replace block ~-4 ~ ~4 container.2 with frozendawn:orsa_suit_patch_kit 4',
        'setblock ~4 ~ ~-4 minecraft:barrel',
        'setblock ~3 ~ ~-4 minecraft:crafting_table',
        'setblock ~2 ~ ~-4 minecraft:furnace[facing=south]',
        'setblock ~-4 ~ ~-4 minecraft:red_bed[facing=south,part=foot]',
        'setblock ~-4 ~ ~-3 minecraft:red_bed[facing=south,part=head]',
        'setblock ~ ~3 ~ minecraft:lantern[hanging=true]',
        'setblock ~-1 ~3 ~-4 minecraft:lantern[hanging=true]',
    ]
    # A roofed supply cache south-west, outside core oxygen range; normal snow underfoot.
    lines += [
        'fill ~-17 ~-1 ~16 ~-9 ~-1 ~22 minecraft:cobblestone',
        'fill ~-17 ~ ~16 ~-9 ~3 ~22 minecraft:spruce_planks hollow',
        'fill ~-16 ~ ~17 ~-10 ~2 ~21 minecraft:air',
        'fill ~-13 ~ ~16 ~-12 ~1 ~16 minecraft:air',
        'setblock ~-15 ~ ~20 minecraft:barrel',
        'item replace block ~-15 ~ ~20 container.0 with minecraft:coal 16',
        'item replace block ~-15 ~ ~20 container.1 with minecraft:raw_iron 8',
        'item replace block ~-15 ~ ~20 container.2 with minecraft:spruce_log 16',
        'setblock ~-11 ~ ~20 minecraft:barrel',
        'item replace block ~-11 ~ ~20 container.0 with minecraft:cobblestone 32',
        'item replace block ~-11 ~ ~20 container.1 with frozendawn:orsa_suit_patch_kit 4',
        'setblock ~-13 ~2 ~19 minecraft:lantern[hanging=true]',
    ]
    for x, z in [(-3,12), (-7,15), (-11,15)]:
        lines += [f'setblock ~{x} ~ ~{z} minecraft:cobblestone_wall',
                  f'setblock ~{x} ~1 ~{z} minecraft:lantern']
    return '\n'.join(lines)


def scripts(tell):
    return {
        'base_structure': structure(),
        'base_safe_mode': 'gamemode creative @s',
        'base_live_mode': 'fd world set phase 6 late\ngamemode survival @s',
        'base_restage': '''execute unless score #stage mpc matches 100 run return 0
function macs_pawn:base_safe_mode
function macs_pawn:base_kit
tp @s 3940.5 101 3980.5 0 0
scoreboard players set @s mb_deaths 0
return 1''',
        'base_kit': '''clear @s
effect clear @s
effect give @s minecraft:instant_health 1 5 true
effect give @s minecraft:saturation 1 5 true
item replace entity @s armor.head with frozendawn:eva_helmet
item replace entity @s armor.chest with frozendawn:eva_chestplate
item replace entity @s armor.legs with frozendawn:eva_leggings
item replace entity @s armor.feet with frozendawn:eva_boots
item replace entity @s hotbar.0 with minecraft:iron_sword
item replace entity @s hotbar.1 with minecraft:bow
item replace entity @s hotbar.2 with minecraft:iron_pickaxe
item replace entity @s hotbar.3 with minecraft:cooked_beef 16
item replace entity @s hotbar.4 with minecraft:cobblestone 32
item replace entity @s hotbar.5 with minecraft:coal 4
item replace entity @s hotbar.6 with minecraft:torch 16
item replace entity @s hotbar.7 with frozendawn:orsa_suit_patch_kit 4
item replace entity @s hotbar.8 with frozendawn:o2_tank_mk3
item replace entity @s weapon.offhand with minecraft:shield
give @s minecraft:arrow 32
fd suit punctures 0''',
        'base_prepare': '''execute unless score #stage mpc matches 92 run return 0
execute unless score #nat_station mpc matches 2 run return 0
execute unless score #nat_count mpc matches 1 run return 0
execute store result score #base_roster mpc if entity @e[tag=macs_pawn_natural,nbt={NoAI:1b},nbt=!{Health:0.0f}]
execute unless score #base_roster mpc matches 2 run return 0
scoreboard players set #stage mpc 100
function macs_pawn:base_safe_mode
fd world preset default
fd world set phase 0
gamemode spectator @s
forceload add 3916 3950 3964 4006
execute positioned 3940 101 3980 run function macs_pawn:base_structure
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
tp @s 3940.5 101 3980.5 0 0
spawnpoint @s 3940 101 3980
function macs_pawn:base_safe_mode
scoreboard objectives add mb_deaths deathCount
scoreboard players set @s mb_deaths 0
scoreboard players set #base_dispatch mpc -1
scoreboard players set #base_released mpc 0
scoreboard players set #base_contact mpc 0
scoreboard players set #base_min mpc 999
scoreboard players set #base_timer mpc 0
''' + tell('Base prepared. Keep paused while the operator checks the shelter and suit. No encounter has started.', color='green') + '\nreturn 1',
        'base_start': '''execute unless score #stage mpc matches 100 run return 0
execute store result score #base_roster mpc if entity @e[tag=macs_pawn_natural,nbt={NoAI:1b},nbt=!{Health:0.0f}]
execute unless score #base_roster mpc matches 2 run return 0
effect clear @s
function macs_pawn:base_live_mode
scoreboard players set #base_timer mpc 0
execute store result score #base_delay mpc run random value 600..1200
scoreboard players set #stage mpc 101
''' + tell('SURVIVAL SESSION: keep the suit on. Follow the lanterns south-west to the supply shed; bring supplies home, refuel the heater, smelt the iron, and use the core to replenish air. Close each airlock door behind you. Defend yourself normally. The session ends after five minutes; report any change in pressure before reading diagnostics.', color='aqua') + '\nreturn 1',
        'base_release': '''execute unless score #stage mpc matches 101 run return 0
execute if score #base_released mpc matches 1 run return 0
execute store result score #base_roster mpc if entity @e[tag=macs_pawn_natural,nbt={NoAI:1b},nbt=!{Health:0.0f}]
execute unless score #base_roster mpc matches 2 run return run function macs_pawn:base_end
scoreboard players set #base_released mpc 1
scoreboard players set #stage mpc 102
execute as @e[tag=macs_pawn_natural] run fd architect record @s 1337
execute as @e[tag=macs_pawn_natural] run fd architect mark @s BASE_SURVIVAL_RELEASE
execute as @e[tag=macs_pawn_natural] run data merge entity @s {NoAI:0b,Glowing:0b}
execute positioned 3940 101 3980 store result score #base_before mpc if entity @e[tag=macs_pawn_natural,distance=..48,nbt=!{Health:0.0f}]
return 1''',
        'base_sample': '''execute positioned 3940 101 3980 store result score #base_near mpc if entity @e[type=frozendawn:architect,distance=..48,nbt=!{Health:0.0f}]
execute positioned 3940 101 3980 store result score #base_original_near mpc if entity @e[tag=macs_pawn_natural,distance=..48,nbt=!{Health:0.0f}]
execute store result score #base_alive mpc if entity @e[tag=macs_pawn_natural,nbt=!{Health:0.0f}]
scoreboard players set #base_admitted mpc 0
execute as @e[tag=macs_pawn_natural,nbt=!{Health:0.0f}] if data entity @s NeoForgeData.macsConvergence run scoreboard players add #base_admitted mpc 1
execute if score #base_admitted mpc matches 2 if score #base_dispatch mpc matches -1 run function macs_pawn:base_admitted
execute if score #base_dispatch mpc matches 0.. if score #base_original_near mpc < #base_min mpc run scoreboard players operation #base_min mpc = #base_original_near mpc
execute store result score #base_player_x mpc run data get entity @s Pos[0]
execute store result score #base_player_z mpc run data get entity @s Pos[2]
execute store result score #base_player_health mpc run data get entity @s Health 100
return 1''',
        'base_admitted': '''scoreboard players operation #base_dispatch mpc = #base_timer mpc
scoreboard players operation #base_at_dispatch mpc = #base_original_near mpc
execute as @e[tag=macs_pawn_natural] run fd architect mark @s BASE_DIVERSION_ADMITTED
return 1''',
        'base_end': '''execute unless score #stage mpc matches 101..102 run return 0
scoreboard players set #stage mpc 103
execute as @e[tag=macs_pawn_natural] run fd architect mark @s BASE_SURVIVAL_END
execute as @e[tag=macs_pawn_natural] run fd architect dump @s
execute as @e[tag=macs_pawn_natural] run data merge entity @s {NoAI:1b}
gamemode spectator @s
fd world set phase 0
''' + tell('SESSION COMPLETE. Pause and describe what changed around the base and whether you could get more done. Death, no dispatch or no relief are valid findings. No dump command needed.', color='green') + '\nreturn 1',
        'base_status': '''execute store result score #base_health mpc run data get entity @s Health 100
execute store result score #base_food mpc run data get entity @s foodLevel
execute store result score #base_fuel mpc run data get block 3937 101 3983 BurnTime
execute store success score #base_core mpc if block 3943 101 3983 frozendawn:geothermal_core
execute store success score #base_helmet mpc if data entity @s Inventory[{Slot:103b,id:"frozendawn:eva_helmet"}]
execute store success score #base_chest mpc if data entity @s Inventory[{Slot:102b,id:"frozendawn:eva_chestplate"}]
execute store success score #base_legs mpc if data entity @s Inventory[{Slot:101b,id:"frozendawn:eva_leggings"}]
execute store success score #base_boots mpc if data entity @s Inventory[{Slot:100b,id:"frozendawn:eva_boots"}]
execute store success score #base_tank mpc if data entity @s Inventory[{Slot:8b,id:"frozendawn:o2_tank_mk3"}]
fd world status verbose
fd suit status verbose
data get entity @s Health
data get entity @s foodLevel
data get entity @s Inventory
data get block 3937 101 3983 BurnTime
data get block 3943 101 3983
return 1''',
        'base_tick': '''scoreboard players add #base_timer mpc 1
execute if score #stage mpc matches 101 if score #base_timer mpc >= #base_delay mpc run function macs_pawn:base_release
scoreboard players operation #base_mod mpc = #base_timer mpc
scoreboard players set #base_interval mpc 20
scoreboard players operation #base_mod mpc %= #base_interval mpc
execute if score #stage mpc matches 102 if score #base_mod mpc matches 0 run function macs_pawn:base_sample
scoreboard players set #base_interval mpc 200
scoreboard players operation #base_mod mpc = #base_timer mpc
scoreboard players operation #base_mod mpc %= #base_interval mpc
execute if score #stage mpc matches 102 if score #base_mod mpc matches 0 as @e[tag=macs_pawn_natural] run fd architect mark @s BASE_SURVIVAL_SAMPLE
execute if score @s mb_deaths matches 1.. run function macs_pawn:base_end
execute if score #base_timer mpc matches 6000.. run function macs_pawn:base_end''',
    }

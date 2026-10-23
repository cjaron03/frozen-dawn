"""QA-only admission-first retry: protect staging, then preserve real group travel."""


def scripts(tell):
    return {
        'base_idle_prepare': """execute unless score #stage mpc matches 92 run return 0
function macs_pawn:base_prepare
execute unless score #stage mpc matches 100 run return 0
scoreboard players set #stage mpc 110
scoreboard players set #idle_timer mpc 0
scoreboard players set #idle_result mpc 0
return 1""",
        'base_idle_status': 'return run function macs_pawn:base_status',
        'base_idle_start': """execute unless score #stage mpc matches 110 run return 0
execute store result score #base_roster mpc if entity @e[tag=macs_pawn_natural,nbt={NoAI:1b},nbt=!{Health:0.0f}]
execute unless score #base_roster mpc matches 2 run return 0
function macs_pawn:base_safe_mode
fd world set phase 6 late
execute as @e[tag=macs_pawn_natural] run fd architect record @s 1337
execute as @e[tag=macs_pawn_natural] run fd architect mark @s BASE_IDLE_RELEASE
execute as @e[tag=macs_pawn_natural] run data merge entity @s {NoAI:0b,Glowing:0b}
scoreboard players set #base_released mpc 1
scoreboard players set #idle_timer mpc 0
scoreboard players set #stage mpc 111
""" + tell('PREPARATION: remain inside the base in Creative. Do not attack or alter the pawns. They will roam at normal speed while Maeve decides. The world will freeze if a group forms, or stop after 90 seconds without one. This protected period is not the Survival test.', color='aqua') + '\nreturn 1',
        'base_idle_tick': """execute unless score #stage mpc matches 111 run return 0
scoreboard players add #idle_timer mpc 1
execute store result score #base_roster mpc if entity @e[tag=macs_pawn_natural,nbt=!{NoAI:1b},nbt=!{Health:0.0f}]
execute unless score #base_roster mpc matches 2 run return run function macs_pawn:base_idle_stop
scoreboard players set #base_admitted mpc 0
execute as @e[tag=macs_pawn_natural,nbt=!{NoAI:1b},nbt=!{Health:0.0f}] if data entity @s NeoForgeData.macsConvergence run scoreboard players add #base_admitted mpc 1
execute if score #base_admitted mpc matches 2 run return run function macs_pawn:base_idle_ready
execute if score #idle_timer mpc matches 1800.. run function macs_pawn:base_idle_stop
return 1""",
        'base_idle_ready': """execute unless score #stage mpc matches 111 run return 0
scoreboard players set #stage mpc 112
scoreboard players set #idle_result mpc 1
execute store result score #idle_cloud_tick mpc run time query gametime
execute positioned 3940 101 3980 store result score #base_at_dispatch mpc if entity @e[tag=macs_pawn_natural,distance=..48,nbt=!{Health:0.0f}]
execute as @e[tag=macs_pawn_natural] run fd architect mark @s BASE_IDLE_ADMITTED
execute as @e[tag=macs_pawn_natural] run fd architect dump @s
fdlab base_pause
""" + tell('GROUP READY. The world is frozen before travel continues. When ready, click START SURVIVAL. Keep the suit on, fetch the shed supplies, and maintain the base for five minutes. If the click disappears, use /function macs_pawn:base_idle_begin.', '/function macs_pawn:base_idle_begin', 'green') + '\nreturn 1',
        'base_idle_begin': """execute unless score #stage mpc matches 112 run return 0
execute positioned 3940 101 3980 run function macs_pawn:base_idle_go
execute unless score #stage mpc matches 102 run return 0
fdlab base_resume
return 1""",
        # Relative heater location lets the native fixture exercise the actual start safely.
        'base_idle_go': """execute unless score #stage mpc matches 112 run return 0
scoreboard players set #base_admitted mpc 0
execute as @e[tag=macs_pawn_natural,nbt=!{NoAI:1b},nbt=!{Health:0.0f}] if data entity @s NeoForgeData.macsConvergence run scoreboard players add #base_admitted mpc 1
execute unless score #base_admitted mpc matches 2 run return 0
execute unless block ~-3 ~ ~3 frozendawn:diamond_thermal_heater run return 0
data merge block ~-3 ~ ~3 {BurnTime:19200}
function macs_pawn:base_kit
effect clear @s
function macs_pawn:base_live_mode
scoreboard players set @s mb_deaths 0
scoreboard players set #base_timer mpc 0
scoreboard players set #base_dispatch mpc 0
scoreboard players set #base_min mpc 999
scoreboard players set #stage mpc 102
execute as @e[tag=macs_pawn_natural] run fd architect mark @s BASE_IDLE_SURVIVAL_START
""" + tell('SURVIVAL STARTED. Follow the south-west lanterns, collect supplies, refuel the heater, smelt iron and replenish air at the core. Defend normally. At SESSION COMPLETE, describe whether the diversion gave you useful time before reading diagnostics.', color='aqua') + '\nreturn 1',
        'base_idle_stop': """execute unless score #stage mpc matches 111 run return 0
scoreboard players set #stage mpc 113
scoreboard players set #idle_result mpc -1
execute as @e[tag=macs_pawn_natural] run fd architect mark @s BASE_IDLE_INCONCLUSIVE
execute as @e[tag=macs_pawn_natural] run fd architect dump @s
execute as @e[tag=macs_pawn_natural] run data merge entity @s {NoAI:1b}
fdlab base_pause
""" + tell('PREPARATION INCONCLUSIVE. No intact group formed within the allowed window. Stay here and report this; do not restart or reset. Survival has not started.', color='yellow') + '\nreturn 1',
        'base_idle_end': 'return run function macs_pawn:base_end',
    }

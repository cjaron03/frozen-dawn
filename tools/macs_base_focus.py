"""QA-only ordinary Survival admission check, using the preserved natural roster."""
from macs_base_diversion import scripts as base_scripts


CENTER = (3864, 101, 4048)


def terrain():
    # The earlier one-layer field allowed an artificial void death. Retain its
    # surface/snow and add ordinary diggable ground underneath in this new copy.
    lines = []
    for x in range(3830, 4065, 16):
        lines.append(f'fill {x} 92 3922 {min(x+15,4064)} 99 4090 frozendawn:frozen_dirt')
    lines.append('fill 3830 100 4034 3872 100 4074 frozendawn:frozen_dirt')
    return '\n'.join(lines)


def scripts(tell):
    original = base_scripts(tell)
    names = ('prepare', 'start', 'release', 'sample', 'admitted', 'end', 'status', 'tick')
    result = {}
    for name in names:
        text = original['base_' + name]
        for other in names:
            text = text.replace('macs_pawn:base_' + other, 'macs_pawn:focus_' + other)
        for old, new in ((3940, 3864), (3980, 4048), (3937, 3861), (3983, 4051), (3943, 3867)):
            text = text.replace(str(old), str(new))
        for old, new in ((100, 120), (101, 121), (102, 122), (103, 123)):
            # Replace scoreboard stages only; never replace world coordinates.
            text = text.replace(f'#stage mpc matches {old}', f'#stage mpc matches {new}')
            text = text.replace(f'#stage mpc {old}', f'#stage mpc {new}')
        text = text.replace('#stage mpc matches 121..102', '#stage mpc matches 121..122')
        result['focus_' + name] = text
    result['focus_terrain'] = terrain()
    result['focus_prepare'] = result['focus_prepare'].replace(
        'forceload add 3916 3950 3964 4006',
        'forceload add 3830 3922 4064 4090\nfunction macs_pawn:focus_terrain')
    result['focus_prepare'] = result['focus_prepare'].replace('return 1', 'scoreboard players set #focus_result mpc 0\nscoreboard players set #focus_at_dispatch_128 mpc -1\nreturn 1')
    # Set a living player to Survival BEFORE releasing any donor. No protected
    # admission period, artificial readiness gate, movement order or history edit.
    result['focus_start'] = """execute unless score #stage mpc matches 120 run return 0
execute store result score #base_roster mpc if entity @e[tag=macs_pawn_natural,nbt={NoAI:1b},nbt=!{Health:0.0f}]
execute unless score #base_roster mpc matches 2 run return 0
effect clear @s
function macs_pawn:base_live_mode
scoreboard players set #base_timer mpc 0
scoreboard players set #focus_result mpc 0
scoreboard players set #stage mpc 121
function macs_pawn:focus_release
execute unless score #stage mpc matches 122 run return 0
""" + tell('SURVIVAL CHECK: keep the suit on. Fetch supplies from the south-west shed, refuel the heater, smelt iron and replenish air at the core. Defend normally. This lasts at most four minutes, or stops after 90 seconds without a group. Report what happened before reading diagnostics.', color='aqua') + '\nreturn 1'
    result['focus_release'] = result['focus_release'].replace('BASE_SURVIVAL_RELEASE', 'FOCUSED_BASE_RELEASE')
    result['focus_sample'] = result['focus_sample'].replace('return 1',
        'execute positioned 3864 101 4048 store result score #focus_original_128 mpc if entity @e[tag=macs_pawn_natural,distance=..128,nbt=!{Health:0.0f}]\nreturn 1')
    result['focus_admitted'] = result['focus_admitted'].replace('return 1',
        'execute positioned 3864 101 4048 store result score #focus_at_dispatch_128 mpc if entity @e[tag=macs_pawn_natural,distance=..128,nbt=!{Health:0.0f}]\nscoreboard players set #focus_result mpc 1\nreturn 1')
    # Existing base_tick owns samples; the wrapper owns its independent timing.
    result['focus_tick'] = """execute unless score #stage mpc matches 122 run return 0
scoreboard players add #base_timer mpc 1
scoreboard players set #base_interval mpc 20
scoreboard players operation #base_mod mpc = #base_timer mpc
scoreboard players operation #base_mod mpc %= #base_interval mpc
execute if score #base_mod mpc matches 0 run function macs_pawn:focus_sample
scoreboard players set #base_interval mpc 200
scoreboard players operation #base_mod mpc = #base_timer mpc
scoreboard players operation #base_mod mpc %= #base_interval mpc
execute if score #base_mod mpc matches 0 as @e[tag=macs_pawn_natural] run fd architect mark @s FOCUSED_BASE_SAMPLE
execute if score @s mb_deaths matches 1.. run scoreboard players set #focus_result mpc -2
execute if score @s mb_deaths matches 1.. run return run function macs_pawn:focus_end
execute if score #base_timer mpc matches 1800.. if score #base_dispatch mpc matches -1 run scoreboard players set #focus_result mpc -1
execute if score #focus_result mpc matches -1 run return run function macs_pawn:focus_end
execute if score #base_timer mpc matches 4800.. run function macs_pawn:focus_end
return 1"""
    result['focus_end'] = result['focus_end'].replace('SESSION COMPLETE.', 'FOCUSED CHECK COMPLETE.').replace('BASE_SURVIVAL_END', 'FOCUSED_BASE_END')
    return result

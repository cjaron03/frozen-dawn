#!/usr/bin/env python3
"""Separate Pawn Convergence replay. Real deaths, production timing; no belief injection."""
import argparse
import gzip
import json
import shutil
from pathlib import Path


def tell(text, command=None, color="gold"):
    msg = {"text": text, "color": color}
    if command:
        msg["clickEvent"] = {"action": "run_command", "value": command}
    return "tellraw @s " + json.dumps(msg)


def build():
    lines = ["forceload add 3940 3980 4064 4028"]
    for x in range(3940, 4065, 25):
        end = min(4064, x + 24)
        lines += [f"fill {x} 100 3980 {end} 100 4028 frozendawn:frozen_dirt",
                  f"fill {x} 101 3980 {end} 113 4028 minecraft:air"]
    for x in range(3940, 4065, 4):
        layers = 1 + ((x - 3940) // 4) % 3
        lines.append(f"fill {x} 101 3980 {min(x+3,4064)} 101 4028 minecraft:snow[layers={layers}]")
    for x, z in [(3945, 3985), (3970, 4022), (4034, 3985), (4056, 4022)]:
        lines += [f"setblock {x} 102 {z} frozendawn:acheronite_crystal[age=3,buried=false,dark=false]"]
    lines += ["fill 3998 100 4000 4012 100 4008 minecraft:stone",
              "fill 3998 101 4000 4012 104 4008 minecraft:air",
              "setblock 4010 100 4004 minecraft:lapis_block",
              "setblock 3962 100 4004 minecraft:gold_block",
              "setblock 4042 100 4004 minecraft:gold_block"]
    return "\n".join(lines)


SCRIPTS = {
    "load": "scoreboard objectives add mpc dummy",
    "cleanup": """execute as @e[tag=macs_pawn_actor] run fd architect stop @s
execute as @e[tag=macs_pawn_actor] run fd architect dump @s
execute as @e[tag=macs_pawn_actor] run data merge entity @s {NoAI:1b}
kill @e[tag=macs_pawn_actor]""",
    "setup": """function macs_pawn:cleanup
function macs_pawn:load
tag @s add macs_pawn
fd postmaeve set-erased
fd postmaeve reset-erased confirm
fd world preset default
fd world set phase 6 late
fd maeve status
fd world set phase 0
gamerule doMobSpawning false
gamerule doDaylightCycle false
gamerule doWeatherCycle false
gamerule keepInventory true
weather clear
time set noon
function macs_pawn:build
gamemode survival @s
clear @s
effect give @s minecraft:resistance infinite 4 true
effect give @s minecraft:saturation infinite 0 true
effect give @s minecraft:night_vision infinite 0 true
give @s minecraft:bow
give @s minecraft:arrow 64
give @s minecraft:iron_sword
give @s minecraft:shield
scoreboard players set #round mpc 0
function macs_pawn:practice""",
    "build": build(),
    "booth": """fill 4000 101 4001 4004 103 4007 minecraft:bedrock hollow
fill 4004 102 4002 4004 102 4006 minecraft:air
fill 4001 100 4002 4003 100 4006 minecraft:stone
fill 4001 101 4002 4003 102 4006 minecraft:air""",
    "practice": """function macs_pawn:cleanup
function macs_pawn:booth
tp @s 4010.5 101 4004.5 90 0
summon frozendawn:architect 4002.5 101 4003.5 {Tags:["macs_pawn_actor","macs_pawn_practice"],PersistenceRequired:1b,Health:4.0f}
summon frozendawn:architect 4002.5 101 4005.5 {Tags:["macs_pawn_actor","macs_pawn_practice"],PersistenceRequired:1b,Health:4.0f}
execute as @e[tag=macs_pawn_practice] run fd architect record @s 1337
scoreboard players set #stage mpc 1
""" + tell("Shoot both Architects through the eye-height slot. Three rounds total; each pair belongs to one encounter. These practice actors have reduced health."),
    "gap": """scoreboard players set #stage mpc 2
scoreboard players set #timer mpc 0
scoreboard players add #round mpc 1
function macs_pawn:cleanup
""" + tell("Both deaths recorded. Skip only this EMPTY waiting gap, then wait for Ready.", "/tick sprint 620t"),
    "ready": """scoreboard players set #stage mpc 3
execute if score #round mpc matches ..2 run function macs_pawn:more
execute if score #round mpc matches 3.. run function macs_pawn:trained""",
    "more": tell("Ready: NEXT pair.", "/function macs_pawn:practice", "green"),
    "trained": tell("History checkpoint: run /fd maeve dump directly in chat. Expect one hotspot, six deaths, three completed encounters. Then /function macs_pawn:converge.", color="green"),
    "converge": """function macs_pawn:cleanup
fill 4000 101 4001 4004 103 4007 minecraft:air
tp @s 4010.5 101 4004.5 90 0
summon frozendawn:architect 3962.5 101.3 4004.5 {Tags:["macs_pawn_actor","macs_pawn_donor"],PersistenceRequired:1b}
summon frozendawn:architect 4042.5 101.3 4004.5 {Tags:["macs_pawn_actor","macs_pawn_donor"],PersistenceRequired:1b}
execute as @e[tag=macs_pawn_donor] run fd architect record @s 1337
scoreboard players set #stage mpc 4
""" + tell("Watch from blue. Describe what happens before opening the next dump. Run at normal speed: no sprint during cloud or travel. Resistance keeps this first timing trial safe."),
    "finish": """scoreboard players set #stage mpc 0
function macs_pawn:cleanup
""" + tell("Replay stopped and actor traces saved. Run /fd maeve dump directly in chat. Stopping an unfinished group is UNKNOWN, not a wipe."),
    "status": "tellraw @s [{\"text\":\"Pawn replay: completed practice encounters=\"},{\"score\":{\"name\":\"#round\",\"objective\":\"mpc\"}},{\"text\":\" stage=\"},{\"score\":{\"name\":\"#stage\",\"objective\":\"mpc\"}}]",
}
SCRIPTS["tick"] = """execute if score #stage mpc matches 1 unless entity @e[tag=macs_pawn_practice,nbt=!{Health:0.0f}] as @a[tag=macs_pawn,limit=1] run function macs_pawn:gap
execute if score #stage mpc matches 2 run scoreboard players add #timer mpc 1
execute if score #stage mpc matches 2 if score #timer mpc matches 620.. as @a[tag=macs_pawn,limit=1] run function macs_pawn:ready"""


# Data-pack-only outcome exercise. Never seeds a belief, edits a dispatch, or kills its roster by command.
SCRIPTS.update({
    "killzone_prepare": """function macs_pawn:load
tag @s add macs_pawn
scoreboard players set #kz_round mpc 0
function macs_pawn:killzone_wait""",
    "killzone_wait": """function macs_pawn:cleanup
function macs_pawn:build
fill 4006 101 4000 4014 105 4008 minecraft:bedrock hollow
fill 4007 101 4001 4013 104 4007 minecraft:air
fill 4006 102 4001 4006 102 4007 minecraft:air
setblock 4010 100 4004 minecraft:lapis_block
tp @s 4010.5 101 4004.5 90 0
gamemode survival @s
effect give @s minecraft:resistance infinite 4 true
effect give @s minecraft:saturation infinite 0 true
item replace entity @s hotbar.0 with minecraft:bow[minecraft:enchantments={levels:{"minecraft:power":5,"minecraft:infinity":1}}]
item replace entity @s hotbar.1 with minecraft:arrow 64
scoreboard players set #stage mpc 10
scoreboard players set #timer mpc 0
""" + tell("Kill-zone preparation: stay on BLUE behind the slit. The Power V bow and bunker make this an outcome test, not combat balance.") + "\n" + tell("Fast-forward the EMPTY cooldown. Wait for Sprint completed and the green practice button.", "/tick sprint 12620t"),
    "killzone_next": "scoreboard players set #stage mpc 17\n" + tell("Ready: click to supply TWO ordinary practice deaths. These refresh decaying hotspot evidence; they are not dispatched pawns.", "/function macs_pawn:killzone_practice", "green"),
    "killzone_practice": """execute unless score #stage mpc matches 17 run return 0
function macs_pawn:cleanup
function macs_pawn:booth
summon frozendawn:architect 4002.5 101 4003.5 {Tags:["macs_pawn_actor","macs_pawn_practice"],PersistenceRequired:1b,Health:4.0f}
summon frozendawn:architect 4002.5 101 4005.5 {Tags:["macs_pawn_actor","macs_pawn_practice"],PersistenceRequired:1b,Health:4.0f}
execute as @e[tag=macs_pawn_practice] run fd architect record @s 1337
scoreboard players set #stage mpc 11
""" + tell("Shoot BOTH practice Architects from BLUE through the slit. Only this pair is reduced-health. Wait for the empty-gap message."),
    "killzone_gap": """scoreboard players set #stage mpc 12
scoreboard players set #timer mpc 0
function macs_pawn:cleanup
""" + tell("Practice pair complete. Fast-forward this EMPTY quiet gap, then wait for Sprint completed and Start.", "/tick sprint 620t"),
    "killzone_ready": "scoreboard players set #stage mpc 13\n" + tell("Ready: run /fd maeve dump now to preserve the BEFORE counts, then click START. Two fresh ordinary deaths should be added; wipe streak is unchanged.", "/function macs_pawn:killzone_start", "green"),
    "killzone_start": """execute unless score #stage mpc matches 13 run return 0
function macs_pawn:cleanup
fill 4000 101 4001 4004 103 4007 minecraft:air
tp @s 4010.5 101 4004.5 90 0
summon frozendawn:architect 3962.5 101.3 4001.5 {Tags:["macs_pawn_actor","macs_pawn_kz"],PersistenceRequired:1b}
summon frozendawn:architect 3962.5 101.3 4007.5 {Tags:["macs_pawn_actor","macs_pawn_kz"],PersistenceRequired:1b}
execute as @e[tag=macs_pawn_kz] run fd architect record @s 1337
scoreboard players set #stage mpc 14
scoreboard players set #timer mpc 0
""" + tell("Wait for DISPATCH CONFIRMED before firing. Stay on BLUE, four blocks behind the slit. Do not sprint during this fight."),
    "killzone_admitted": "scoreboard players set #stage mpc 15\n" + tell("DISPATCH CONFIRMED. Destroy both full-health pawns with the bow from BLUE, before either reaches you. After both die, run /fd maeve dump and stop for review.", color="red"),
    "killzone_done": """scoreboard players set #stage mpc 16
scoreboard players add #kz_round mpc 1
execute as @e[tag=macs_pawn_kz] run fd architect stop @s
execute as @e[tag=macs_pawn_kz] run fd architect dump @s
""" + tell("Both actors died. Run /fd maeve dump directly in chat, then stop here. The dump decides WIPE versus SUCCESS/UNKNOWN; this message does not certify a wipe.", color="green"),
    "killzone_failed": "scoreboard players set #stage mpc 16\n" + tell("Replay stopped: dispatch was unavailable or interrupted. Run /fd maeve dump and report it. Do not count this as a wipe or run another group yet.", color="red"),
})
SCRIPTS["tick"] += """
execute if score #stage mpc matches 10 run scoreboard players add #timer mpc 1
execute if score #stage mpc matches 10 if score #timer mpc matches 12620.. as @a[tag=macs_pawn,limit=1] run function macs_pawn:killzone_next
execute if score #stage mpc matches 11 unless entity @e[tag=macs_pawn_practice,nbt=!{Health:0.0f}] as @a[tag=macs_pawn,limit=1] run function macs_pawn:killzone_gap
execute if score #stage mpc matches 12 run scoreboard players add #timer mpc 1
execute if score #stage mpc matches 12 if score #timer mpc matches 620.. as @a[tag=macs_pawn,limit=1] run function macs_pawn:killzone_ready
execute if score #stage mpc matches 14 run scoreboard players add #timer mpc 1
execute if score #stage mpc matches 14 run scoreboard players set #admitted mpc 0
execute if score #stage mpc matches 14 as @e[tag=macs_pawn_kz,nbt=!{Health:0.0f}] if data entity @s NeoForgeData.macsConvergence run scoreboard players add #admitted mpc 1
execute if score #stage mpc matches 14 if score #admitted mpc matches 2 as @a[tag=macs_pawn,limit=1] run function macs_pawn:killzone_admitted
execute if score #stage mpc matches 14 if score #timer mpc matches 100.. as @a[tag=macs_pawn,limit=1] run function macs_pawn:killzone_failed
execute if score #stage mpc matches 15 unless entity @e[tag=macs_pawn_kz,nbt=!{Health:0.0f}] as @a[tag=macs_pawn,limit=1] run function macs_pawn:killzone_done
execute if score #stage mpc matches 15 as @e[tag=macs_pawn_kz,nbt=!{Health:0.0f}] unless data entity @s NeoForgeData.macsConvergence as @a[tag=macs_pawn,limit=1] run function macs_pawn:killzone_failed
"""


# Field replay: real donor movement, a second actual wipe, then an unprovoked idle pawn.
SCRIPTS["killzone_prepare"] = SCRIPTS["killzone_prepare"].replace("function macs_pawn:load", "function macs_pawn:load\nscoreboard players set #field mpc 0", 1)
SCRIPTS["killzone_ready"] = "scoreboard players set #stage mpc 13\n" + "execute unless score #field mpc matches 1 run " + tell("Ready: run /fd maeve dump now to preserve the BEFORE counts, then START.", "/function macs_pawn:killzone_start", "green") + "\nexecute if score #field mpc matches 1 run function macs_pawn:field_ready"
SCRIPTS.update({
    "field_prepare": """function macs_pawn:load
tag @s add macs_pawn
scoreboard players set #field mpc 1
function macs_pawn:killzone_wait""",
    "field_ready": tell("Ready: run /fd maeve dump, then /function macs_pawn:diversion. This stages the base actors; it does not dispatch them yet.", "/function macs_pawn:diversion", "green"),
    "diversion": """execute unless score #stage mpc matches 13 run return 0
function macs_pawn:cleanup
fill 4000 101 4001 4004 103 4007 minecraft:air
fill 3952 100 3994 3974 100 3994 minecraft:lime_concrete
fill 3952 100 4016 3974 100 4016 minecraft:lime_concrete
fill 3952 100 3994 3952 100 4016 minecraft:lime_concrete
fill 3974 100 3994 3974 100 4016 minecraft:lime_concrete
fill 3952 101 3994 3974 101 3994 minecraft:air
fill 3952 101 4016 3974 101 4016 minecraft:air
fill 3952 101 3994 3952 101 4016 minecraft:air
fill 3974 101 3994 3974 101 4016 minecraft:air
setblock 3962 100 4001 minecraft:gold_block
setblock 3962 100 4007 minecraft:gold_block
gamemode spectator @s
tp @s 3982.5 111 4032.5 180 20
summon frozendawn:architect 3962.5 101.3 4001.5 {Tags:["macs_pawn_actor","macs_pawn_kz","macs_pawn_divert"],PersistenceRequired:1b,NoAI:1b,Glowing:1b}
summon frozendawn:architect 3962.5 101.3 4007.5 {Tags:["macs_pawn_actor","macs_pawn_kz","macs_pawn_divert"],PersistenceRequired:1b,NoAI:1b,Glowing:1b}
execute as @e[tag=macs_pawn_divert] run data get entity @s UUID
scoreboard players set #stage mpc 30
""" + tell("Base staged: the TWO glowing pawns in the GREEN outline are temporarily held for this preview. Stay at this camera position; you may look around. START releases their AI.") + "\n" + tell("START the 25-second diversion view. Afterward you return to BLUE to defeat this group.", "/function macs_pawn:diversion_start", "green"),
    "diversion_start": """execute unless score #stage mpc matches 30 run return 0
execute as @e[tag=macs_pawn_divert] run data merge entity @s {NoAI:0b}
execute as @e[tag=macs_pawn_divert] run fd architect record @s 1337
scoreboard players set #stage mpc 31
scoreboard players set #timer mpc 0
""" + tell("AI released. Wait for actual admission. Do not sprint or move the camera through the world."),
    "diversion_admitted": """scoreboard players set #stage mpc 32
scoreboard players set #timer mpc 0
""" + tell("Real dispatch admitted. Watch the green base area and the glowing pawns at normal speed. The same two actors must travel; no replacement actors are supplied."),
    "diversion_checkpoint": """scoreboard players set #base_count mpc 0
scoreboard players set #roster_count mpc 0
execute as @e[tag=macs_pawn_divert,x=3952,y=99,z=3994,dx=22,dy=12,dz=22] run scoreboard players add #base_count mpc 1
execute as @e[tag=macs_pawn_divert] run scoreboard players add #roster_count mpc 1
execute as @e[tag=macs_pawn_divert] run fd architect dump @s
execute as @e[tag=macs_pawn_divert] run data get entity @s UUID
execute as @e[tag=macs_pawn_divert] run data get entity @s Pos
tellraw @s [{"text":"Diversion checkpoint: base actors remaining="},{"score":{"name":"#base_count","objective":"mpc"}},{"text":"; original roster alive="},{"score":{"name":"#roster_count","objective":"mpc"}},{"text":". Remember what you saw before reading a Maeve dump."}]
gamemode survival @s
tp @s 4010.5 101 4004.5 90 0
scoreboard players set #stage mpc 15
""" + tell("Back on BLUE: now shoot BOTH dispatched pawns through the slit. Stay inside. After both die, describe the movement, run /fd maeve dump, and stop for review."),
    "avoidance": """execute unless score #stage mpc matches 16 run return 0
function macs_pawn:cleanup
gamemode spectator @s
tp @s 4038.5 110 4018.5 120 18
summon frozendawn:architect 4016.5 101.3 4006.5 {Tags:["macs_pawn_actor","macs_pawn_avoid"],PersistenceRequired:1b,Glowing:1b}
execute as @e[tag=macs_pawn_avoid] run fd architect record @s 1337
execute as @e[tag=macs_pawn_avoid] run data get entity @s Pos
scoreboard players set #stage mpc 40
scoreboard players set #timer mpc 0
""" + tell("Idle-pawn view: observe the glowing Architect for 12 seconds. Stay in spectator mode, do not attack and do not sprint. The replay supplies no movement order."),
    "avoidance_checkpoint": """scoreboard players set #stage mpc 41
execute as @e[tag=macs_pawn_avoid] run fd architect dump @s
execute as @e[tag=macs_pawn_avoid] run data get entity @s Pos
""" + tell("Observation checkpoint. The Architect remains live. Describe what it did, run /fd maeve dump, and pause for review.", color="green"),
})
SCRIPTS["tick"] += """
execute if score #stage mpc matches 31 run scoreboard players add #timer mpc 1
execute if score #stage mpc matches 31 run scoreboard players set #admitted mpc 0
execute if score #stage mpc matches 31 as @e[tag=macs_pawn_divert,nbt=!{Health:0.0f}] if data entity @s NeoForgeData.macsConvergence run scoreboard players add #admitted mpc 1
execute if score #stage mpc matches 31 if score #admitted mpc matches 2 as @a[tag=macs_pawn,limit=1] run function macs_pawn:diversion_admitted
execute if score #stage mpc matches 31 if score #timer mpc matches 100.. as @a[tag=macs_pawn,limit=1] run function macs_pawn:killzone_failed
execute if score #stage mpc matches 32 run scoreboard players add #timer mpc 1
execute if score #stage mpc matches 32 as @e[tag=macs_pawn_divert] unless data entity @s NeoForgeData.macsConvergence as @a[tag=macs_pawn,limit=1] run function macs_pawn:killzone_failed
execute if score #stage mpc matches 32 if score #timer mpc matches 500.. as @a[tag=macs_pawn,limit=1] run function macs_pawn:diversion_checkpoint
execute if score #stage mpc matches 40 run scoreboard players add #timer mpc 1
execute if score #stage mpc matches 40 if score #timer mpc matches 240.. as @a[tag=macs_pawn,limit=1] run function macs_pawn:avoidance_checkpoint
"""


# Continue the accepted two-wipe checkpoint; genuine deaths replenish pressure before cooldown expiry.
# The 3,600-tick empty gap exceeds this checkpoint's remaining 3,148 ticks. It changes no tuning.
SCRIPTS.update({
    "cooldown_prepare": """execute unless score #stage mpc matches 41 run return 0
""" + SCRIPTS["killzone_wait"].split("scoreboard players set #stage mpc 10")[0] + """tag @s add macs_pawn
scoreboard players set #stage mpc 17
function macs_pawn:killzone_practice
scoreboard players set #stage mpc 50
""" + tell("Cooldown check: shoot these TWO ordinary practice Architects from BLUE. Do not use setup. After both die, freeze and dump for review."),
    "cooldown_gap": """scoreboard players set #stage mpc 51
scoreboard players set #timer mpc 0
function macs_pawn:cleanup
""" + tell("Both ordinary deaths recorded. Run /tick freeze, then /fd maeve dump directly in chat, and stop for review. The two-wipe latch must still be present; this prompt does not certify it.", color="green"),
    "cooldown_ready": """scoreboard players set #stage mpc 52
""" + tell("Empty gap complete. Run /fd maeve dump and stop: cooldown must be zero, weight at least 3.0, and avoid=true before introducing donors.", color="green"),
    "cooldown_probe": """execute unless score #stage mpc matches 52 run return 0
function macs_pawn:cleanup
fill 4000 101 4001 4004 103 4007 minecraft:air
gamemode spectator @s
tp @s 3982.5 111 4032.5 180 20
summon frozendawn:architect 3962.5 101.3 4001.5 {Tags:["macs_pawn_actor","macs_pawn_cooldown"],PersistenceRequired:1b,Glowing:1b}
summon frozendawn:architect 3962.5 101.3 4007.5 {Tags:["macs_pawn_actor","macs_pawn_cooldown"],PersistenceRequired:1b,Glowing:1b}
execute as @e[tag=macs_pawn_cooldown] run fd architect record @s 1337
scoreboard players set #stage mpc 53
scoreboard players set #timer mpc 0
""" + tell("Two ordinary idle donors are available. Run /tick step 200t directly in chat for a ten-second normal-speed view; do not attack or sprint.", "/tick step 200t", "green"),
    "cooldown_checkpoint": """scoreboard players set #stage mpc 54
scoreboard players set #admitted mpc 0
execute as @e[tag=macs_pawn_cooldown] if data entity @s NeoForgeData.macsConvergence run scoreboard players add #admitted mpc 1
execute as @e[tag=macs_pawn_cooldown] run fd architect dump @s
execute as @e[tag=macs_pawn_cooldown] run data get entity @s UUID
execute as @e[tag=macs_pawn_cooldown] run data get entity @s Pos
""" + tell("Observation complete. Describe whether a gathering cloud or group formed, then run /fd maeve dump. The dump must show AVOID_TWO_WIPES with zero cooldown and weight above the floor.", color="green"),
})
SCRIPTS["tick"] += """
execute if score #stage mpc matches 50 unless entity @e[tag=macs_pawn_practice,nbt=!{Health:0.0f}] as @a[tag=macs_pawn,limit=1] run function macs_pawn:cooldown_gap
execute if score #stage mpc matches 51 run scoreboard players add #timer mpc 1
execute if score #stage mpc matches 51 if score #timer mpc matches 3600.. as @a[tag=macs_pawn,limit=1] run function macs_pawn:cooldown_ready
execute if score #stage mpc matches 53 run scoreboard players add #timer mpc 1
execute if score #stage mpc matches 53 if score #timer mpc matches 200.. as @a[tag=macs_pawn,limit=1] run function macs_pawn:cooldown_checkpoint
"""


# Separate disposable-world control: one six-kill fight, then real fall and lava physics.
# All waits advance the existing 600-tick quiet boundary; no death or episode is injected.
SCRIPTS.update({
    "env_setup": SCRIPTS["setup"].split("function macs_pawn:practice")[0] + """function macs_pawn:booth
tp @s 4010.5 101 4004.5 90 0
item replace entity @s hotbar.0 with minecraft:bow[minecraft:enchantments={levels:{"minecraft:power":5,"minecraft:infinity":1}}]
""" + "\n".join(f'summon frozendawn:architect {x}.5 101 {z}.5 {{Tags:["macs_pawn_actor","macs_pawn_control"],PersistenceRequired:1b,Health:4.0f}}' for x in (4001, 4003) for z in (4002, 4004, 4006)) + """
execute as @e[tag=macs_pawn_control] run fd architect record @s 1337
scoreboard players set #stage mpc 60
""" + tell("Single-fight control: from BLUE, shoot all SIX reduced-health Architects through the slit in one continuous fight. No time skips between kills. At completion, freeze and dump for review."),
    "env_control_done": """scoreboard players set #stage mpc 61
scoreboard players set #timer mpc 0
function macs_pawn:cleanup
""" + tell("All six practice actors died. Run /tick freeze, then /fd maeve dump and stop for review. This message does not certify one encounter.", color="green"),
    "env_control_ready": """scoreboard players set #stage mpc 62
""" + tell("First quiet gap complete. Run /fd maeve dump and stop. Six deaths should have completed only ONE encounter.", color="green"),
    "env_control_probe": """execute unless score #stage mpc matches 62 run return 0
function macs_pawn:cleanup
fill 4000 101 4001 4004 103 4007 minecraft:air
gamemode spectator @s
tp @s 3982.5 111 4032.5 180 20
summon frozendawn:architect 3962.5 101.3 4001.5 {Tags:["macs_pawn_actor","macs_pawn_control_donor"],PersistenceRequired:1b,Glowing:1b}
summon frozendawn:architect 3962.5 101.3 4007.5 {Tags:["macs_pawn_actor","macs_pawn_control_donor"],PersistenceRequired:1b,Glowing:1b}
execute as @e[tag=macs_pawn_control_donor] run fd architect record @s 1337
scoreboard players set #stage mpc 63
scoreboard players set #timer mpc 0
""" + tell("Two ordinary source pawns are available. Run /tick step 200t directly in chat. Watch for ten seconds at normal speed.", "/tick step 200t", "green"),
    "env_control_checkpoint": """scoreboard players set #stage mpc 64
execute as @e[tag=macs_pawn_control_donor] run fd architect dump @s
""" + tell("Control view complete. Describe what appeared, then run /fd maeve dump. Stop for review before the fall trap.", color="green"),
    "env_fall": """execute unless score #stage mpc matches 64 run return 0
function macs_pawn:cleanup
gamemode spectator @s
fill 4000 101 4001 4004 163 4007 minecraft:barrier hollow
fill 4001 101 4002 4003 162 4006 minecraft:air
fill 4000 100 4001 4004 100 4007 minecraft:stone
fill 4000 163 4001 4004 163 4007 minecraft:stone
tp @s 4016.5 120 4018.5 135 -15
summon frozendawn:architect 4001.5 161 4003.5 {Tags:["macs_pawn_actor","macs_pawn_environment"],PersistenceRequired:1b,Glowing:1b}
summon frozendawn:architect 4003.5 161 4005.5 {Tags:["macs_pawn_actor","macs_pawn_environment"],PersistenceRequired:1b,Glowing:1b}
execute as @e[tag=macs_pawn_environment] run fd architect record @s 1337
scoreboard players set #stage mpc 65
scoreboard players set #timer mpc 0
""" + tell("FALL trap: two full-health Architects are above the landing. Run /tick step 100t to watch five seconds of real gravity. Do not attack.", "/tick step 100t", "green"),
    "env_fall_done": """scoreboard players set #stage mpc 66
scoreboard players set #timer mpc 0
function macs_pawn:cleanup
""" + tell("Both fall-trap actors died. Wait for the step to finish, then run /fd maeve dump and stop for review. Expected: two additional deaths; the log must confirm fall damage.", color="green"),
    "env_fall_ready": """scoreboard players set #stage mpc 67
""" + tell("Second quiet gap complete. Dump for review: expect eight deaths and TWO completed encounters. Lava is next after review.", color="green"),
    "env_lava": """execute unless score #stage mpc matches 67 run return 0
function macs_pawn:cleanup
fill 4000 101 4001 4004 163 4007 minecraft:air
fill 4000 100 4001 4004 104 4007 minecraft:barrier hollow
fill 4000 100 4001 4004 100 4007 minecraft:stone
fill 4001 101 4002 4003 101 4006 minecraft:lava
gamemode spectator @s
tp @s 4010.5 107 4015.5 140 30
summon frozendawn:architect 4001.5 102 4003.5 {Tags:["macs_pawn_actor","macs_pawn_environment"],PersistenceRequired:1b,Glowing:1b}
summon frozendawn:architect 4003.5 102 4005.5 {Tags:["macs_pawn_actor","macs_pawn_environment"],PersistenceRequired:1b,Glowing:1b}
execute as @e[tag=macs_pawn_environment] run fd architect record @s 1337
scoreboard players set #stage mpc 68
scoreboard players set #timer mpc 0
""" + tell("LAVA trap: two full-health Architects enter a contained lava pool. Run /tick step 300t for fifteen normal-speed seconds. Do not attack. If either survives, stop and report it.", "/tick step 300t", "green"),
    "env_lava_done": """scoreboard players set #stage mpc 69
scoreboard players set #timer mpc 0
function macs_pawn:cleanup
""" + tell("Both lava-trap actors died. Wait for the step to finish, then dump and stop for review. Expected: two additional deaths; the log must confirm environmental damage.", color="green"),
    "env_lava_ready": """scoreboard players set #stage mpc 70
""" + tell("Third quiet gap complete. Dump and stop: ten deaths across THREE completed encounters should now qualify through real history.", color="green"),
    "env_converge": """execute unless score #stage mpc matches 70 run return 0
function macs_pawn:cleanup
fill 4000 101 4001 4004 104 4007 minecraft:air
gamemode spectator @s
tp @s 3982.5 111 4032.5 180 20
summon frozendawn:architect 3962.5 101.3 4001.5 {Tags:["macs_pawn_actor","macs_pawn_environment_donor"],PersistenceRequired:1b,Glowing:1b}
summon frozendawn:architect 3962.5 101.3 4007.5 {Tags:["macs_pawn_actor","macs_pawn_environment_donor"],PersistenceRequired:1b,Glowing:1b}
summon frozendawn:architect 4042.5 101.3 4004.5 {Tags:["macs_pawn_actor","macs_pawn_environment_donor"],PersistenceRequired:1b,Glowing:1b}
execute as @e[tag=macs_pawn_environment_donor] run fd architect record @s 1337
scoreboard players set #stage mpc 71
scoreboard players set #timer mpc 0
""" + tell("Positive comparison: three ordinary source pawns are available. Run /tick unfreeze directly in chat and watch at NORMAL speed. Freeze and dump when the view-complete prompt appears.", "/tick unfreeze", "green"),
    "env_admitted": """scoreboard players set #stage mpc 72
scoreboard players set #timer mpc 0
""" + tell("Actual dispatch confirmed. Watch the cloud and approach at normal speed. No time sprint during this view."),
    "env_checkpoint": """scoreboard players set #stage mpc 73
execute as @e[tag=macs_pawn_environment_donor] run fd architect dump @s
""" + tell("View complete. Run /tick freeze, describe what happened, then /fd maeve dump. This tests environmental learning, not combat balance.", color="green"),
})
SCRIPTS["tick"] += """
execute if score #stage mpc matches 60 unless entity @e[tag=macs_pawn_control,nbt=!{Health:0.0f}] as @a[tag=macs_pawn,limit=1] run function macs_pawn:env_control_done
execute if score #stage mpc matches 61 run scoreboard players add #timer mpc 1
execute if score #stage mpc matches 61 if score #timer mpc matches 620.. as @a[tag=macs_pawn,limit=1] run function macs_pawn:env_control_ready
execute if score #stage mpc matches 63 run scoreboard players add #timer mpc 1
execute if score #stage mpc matches 63 if score #timer mpc matches 200.. as @a[tag=macs_pawn,limit=1] run function macs_pawn:env_control_checkpoint
execute if score #stage mpc matches 65 unless entity @e[tag=macs_pawn_environment,nbt=!{Health:0.0f}] as @a[tag=macs_pawn,limit=1] run function macs_pawn:env_fall_done
execute if score #stage mpc matches 66 run scoreboard players add #timer mpc 1
execute if score #stage mpc matches 66 if score #timer mpc matches 620.. as @a[tag=macs_pawn,limit=1] run function macs_pawn:env_fall_ready
execute if score #stage mpc matches 68 unless entity @e[tag=macs_pawn_environment,nbt=!{Health:0.0f}] as @a[tag=macs_pawn,limit=1] run function macs_pawn:env_lava_done
execute if score #stage mpc matches 69 run scoreboard players add #timer mpc 1
execute if score #stage mpc matches 69 if score #timer mpc matches 620.. as @a[tag=macs_pawn,limit=1] run function macs_pawn:env_lava_ready
execute if score #stage mpc matches 71 run scoreboard players add #timer mpc 1
execute if score #stage mpc matches 71 run scoreboard players set #admitted mpc 0
execute if score #stage mpc matches 71 as @e[tag=macs_pawn_environment_donor] if data entity @s NeoForgeData.macsConvergence run scoreboard players add #admitted mpc 1
execute if score #stage mpc matches 71 if score #admitted mpc matches 2.. as @a[tag=macs_pawn,limit=1] run function macs_pawn:env_admitted
execute if score #stage mpc matches 71 if score #timer mpc matches 600 as @a[tag=macs_pawn,limit=1] run tellraw @s {"text":"No dispatch confirmed after thirty seconds. Freeze, dump and report; do not count this as a passed positive control.","color":"red"}
execute if score #stage mpc matches 72 run scoreboard players add #timer mpc 1
execute if score #stage mpc matches 72 if score #timer mpc matches 500.. as @a[tag=macs_pawn,limit=1] run function macs_pawn:env_checkpoint
"""


# Paired warning view. Reuses genuine saved evidence; only empty cooldown is sprinted.
def warning_terrain():
    lines = ["forceload add 3880 3980 4128 4028",
             "fill 3998 114 4000 4012 170 4008 minecraft:air"]
    for x in range(3880, 4129, 25):
        end = min(4128, x + 24)
        lines += [f"fill {x} 100 3980 {end} 100 4028 frozendawn:frozen_dirt",
                  f"fill {x} 101 3980 {end} 113 4028 minecraft:air"]
    for x in range(3880, 4129, 4):
        layers = 1 + ((x - 3880) // 4) % 3
        lines.append(f"fill {x} 101 3980 {min(x+3,4128)} 101 4028 minecraft:snow[layers={layers}]")
    lines += ["setblock 4003 100 4006 minecraft:lapis_block"]
    return "\n".join(lines)


SCRIPTS.update({
    "warning_near": "scoreboard players set #route mpc 0\nfunction macs_pawn:warning_prepare",
    "warning_far": "scoreboard players set #route mpc 1\nfunction macs_pawn:warning_prepare",
    "warning_prepare": """function macs_pawn:load
function macs_pawn:cleanup
tag @s add macs_pawn
fd world set phase 0
function macs_pawn:warning_build
gamemode spectator @s
tp @s 4003.5 124 4006.5 180 90
scoreboard players set #stage mpc 80
scoreboard players set #timer mpc 0
""" + tell("Warning comparison prepared. Your recorded hotspot is retained. Fast-forward only this EMPTY cooldown, then wait for Sprint completed and READY.", "/tick sprint 12620t"),
    "warning_build": warning_terrain(),
    "warning_ready": "scoreboard players set #stage mpc 81\n" + tell("READY. Click START, then run /tick unfreeze. Watch from here without moving or accelerating time. Describe the cloud and arrival before reading the dump.", "/function macs_pawn:warning_start", "green"),
    "warning_start": """execute unless score #stage mpc matches 81 run return 0
execute if score #route mpc matches 0 run summon frozendawn:architect 3967.5 101.3 4004.5 {Tags:["macs_pawn_actor","macs_pawn_warning"],PersistenceRequired:1b}
execute if score #route mpc matches 0 run summon frozendawn:architect 4039.5 101.3 4004.5 {Tags:["macs_pawn_actor","macs_pawn_warning"],PersistenceRequired:1b}
execute if score #route mpc matches 1 run summon frozendawn:architect 3903.5 101.3 4004.5 {Tags:["macs_pawn_actor","macs_pawn_warning"],PersistenceRequired:1b}
execute if score #route mpc matches 1 run summon frozendawn:architect 4103.5 101.3 4004.5 {Tags:["macs_pawn_actor","macs_pawn_warning"],PersistenceRequired:1b}
execute as @e[tag=macs_pawn_warning] run fd architect record @s 1337
execute as @e[tag=macs_pawn_warning] run data get entity @s UUID
execute as @e[tag=macs_pawn_warning] run data get entity @s Pos
scoreboard players set #stage mpc 82
scoreboard players set #timer mpc 0
""" + tell("Run /tick unfreeze now. Stay at this camera position and watch at NORMAL speed until VIEW COMPLETE.", "/tick unfreeze", "green"),
    "warning_admitted": """scoreboard players set #stage mpc 83
scoreboard players set #timer mpc 0
execute store result score #cloud_tick mpc run time query gametime
""",
    "warning_arrival": """scoreboard players set #stage mpc 84
scoreboard players set #timer mpc 0
execute store result score #arrival_tick mpc run time query gametime
scoreboard players operation #elapsed mpc = #arrival_tick mpc
scoreboard players operation #elapsed mpc -= #cloud_tick mpc
""",
    "warning_checkpoint": """scoreboard players set #stage mpc 85
execute as @e[tag=macs_pawn_warning] run fd architect stop @s
execute as @e[tag=macs_pawn_warning] run fd architect dump @s
execute as @e[tag=macs_pawn_warning] run data get entity @s Pos
""" + tell("VIEW COMPLETE. Run /tick freeze now. Describe the cloud and arrival first, then run /fd maeve dump and stop for review. The direct dump supplies the authoritative timing.", "/tick freeze", "green"),
    "warning_failed": "scoreboard players set #stage mpc 85\n" + tell("No complete warning view was recorded. Freeze and run /fd maeve dump; report this instead of repeating setup. Existing evidence may be below the weight floor or the route may have ended.", "/tick freeze", "red"),
})
SCRIPTS["tick"] += """
execute if score #stage mpc matches 80 run scoreboard players add #timer mpc 1
execute if score #stage mpc matches 80 if score #timer mpc matches 12620.. as @a[tag=macs_pawn,limit=1] run function macs_pawn:warning_ready
execute if score #stage mpc matches 82 run scoreboard players add #timer mpc 1
execute if score #stage mpc matches 82 run scoreboard players set #admitted mpc 0
execute if score #stage mpc matches 82 as @e[tag=macs_pawn_warning,nbt=!{Health:0.0f}] if data entity @s NeoForgeData.macsConvergence run scoreboard players add #admitted mpc 1
execute if score #stage mpc matches 82 if score #admitted mpc matches 2 as @a[tag=macs_pawn,limit=1] run function macs_pawn:warning_admitted
execute if score #stage mpc matches 82 if score #timer mpc matches 200.. as @a[tag=macs_pawn,limit=1] run function macs_pawn:warning_failed
execute if score #stage mpc matches 83 run scoreboard players add #timer mpc 1
execute if score #stage mpc matches 83 as @e[tag=macs_pawn_warning] unless data entity @s NeoForgeData.macsConvergence as @a[tag=macs_pawn,limit=1] run function macs_pawn:warning_failed
execute if score #stage mpc matches 83 positioned 4003 101 4006 if entity @e[tag=macs_pawn_warning,distance=..24] as @a[tag=macs_pawn,limit=1] run function macs_pawn:warning_arrival
execute if score #stage mpc matches 83 if score #timer mpc matches 1800.. as @a[tag=macs_pawn,limit=1] run function macs_pawn:warning_failed
execute if score #stage mpc matches 84 run scoreboard players add #timer mpc 1
execute if score #stage mpc matches 84 if score #timer mpc matches 100.. as @a[tag=macs_pawn,limit=1] run function macs_pawn:warning_checkpoint
"""


# Natural-spawner supply sample. No Architect summon, probability override or belief edit.
# Hold newly spawned actors immediately so time sprint cannot fast-forward their encounter.
def natural_terrain():
    lines = []
    for x in range(3840, 4009, 24):
        end = min(4008, x + 23)
        for z in range(3922, 4091, 64):
            zend = min(4090, z + 63)
            lines += [f"fill {x} 100 {z} {end} 100 {zend} frozendawn:frozen_dirt",
                      f"fill {x} 101 {z} {end} 113 {zend} minecraft:air"]
    for x in range(3840, 4009, 4):
        layers = 1 + ((x - 3840) // 4) % 3
        lines.append(f"fill {x} 101 3922 {min(x+3,4008)} 101 4090 minecraft:snow[layers={layers}]")
    lines += ["fill 3921 101 4004 3925 103 4008 minecraft:air",
              "setblock 3923 100 4006 minecraft:lapis_block"]
    return "\n".join(lines)


SCRIPTS.update({
    "natural_setup": """execute if entity @e[tag=macs_pawn_natural] run return 0
function macs_pawn:load
scoreboard players set #stage mpc 0
gamemode spectator @s
function macs_pawn:cleanup
tag @s add macs_pawn
fd world set phase 0
fd world preset brutal
gamerule doMobSpawning false
gamerule doDaylightCycle false
gamerule doWeatherCycle false
weather clear
effect give @s minecraft:night_vision infinite 0 true
forceload add 3840 3922 4008 4090
tp @s 3923.5 101 4006.5 90 0
scoreboard players set #nat_count mpc 0
scoreboard players set #nat_first mpc -1
scoreboard players set #nat_window mpc 1
scoreboard players set #nat_station mpc 1
scoreboard players set #stage mpc 90
scoreboard players set #timer mpc 0
""" + tell("Natural supply sample: existing Brutal spawning, five simulated minutes. New Architects are held in place for sampling. Run the sprint below; this is not a live combat or travel test.", "/tick sprint 6040t"),
    "natural_build": natural_terrain(),
    "natural_land": """execute unless block ~ ~-1 ~ minecraft:lapis_block run return 0
execute unless block ~ ~ ~ minecraft:air run return 0
execute unless block ~ ~1 ~ minecraft:air run return 0
tp @s ~ ~ ~ 90 0
gamemode creative @s
return 1
""",
    "natural_begin": """execute if score #nat_window mpc matches 1 run function macs_pawn:natural_build
execute unless score #nat_station mpc matches 2 store result score #nat_landed mpc run execute positioned 3923.5 101 4006.5 run function macs_pawn:natural_land
execute if score #nat_station mpc matches 2 store result score #nat_landed mpc run execute positioned 4000.5 101 4054.5 run function macs_pawn:natural_land
execute unless score #nat_landed mpc matches 1 run return run function macs_pawn:natural_invalid
forceload remove 3840 3922 4008 4090
tag @e[type=frozendawn:architect] add macs_natural_existing
execute store result score #nat_started mpc run time query gametime
scoreboard players set #timer mpc 0
scoreboard players set #stage mpc 91
fd world set phase 6 late
""",
    "natural_invalid": """scoreboard players set #stage mpc 98
fd world set phase 0
forceload remove 3840 3922 4008 4090
gamemode spectator @s
""" + tell("SAMPLE INVALID: the platform, player position or spawn height was outside the test area. No zero-spawn or supply conclusion is valid. Keep frozen and report this message.", color="red"),
    "natural_invalid_actor": """tag @s add macs_natural_existing
tag @s add macs_natural_invalid
data merge entity @s {NoAI:1b}
fd architect record @s 1337
fd architect dump @s
execute as @a[tag=macs_pawn,limit=1] run function macs_pawn:natural_invalid
""",
    "natural_restart": """execute unless score #stage mpc matches 92 unless score #stage mpc matches 98 run return 0
execute if entity @e[tag=macs_pawn_natural] run return 0
tag @e[type=frozendawn:architect,tag=!macs_natural_existing,x=3840,y=-64,z=3922,dx=226,dy=383,dz=198] add macs_natural_invalid
execute as @e[tag=macs_natural_invalid] run fd architect stop @s
execute as @e[tag=macs_natural_invalid] run fd architect dump @s
execute as @e[tag=macs_natural_invalid] run data merge entity @s {NoAI:1b}
kill @e[tag=macs_natural_invalid]
function macs_pawn:natural_setup
""",
    "natural_capture": """tag @s add macs_natural_existing
tag @s add macs_pawn_natural
data merge entity @s {NoAI:1b}
scoreboard players add #nat_count mpc 1
execute if score #nat_count mpc matches 1 run scoreboard players operation #nat_first mpc = #timer mpc
execute store result score @s mpc run time query gametime
fd architect record @s 1337
fd architect dump @s
""",
    "natural_checkpoint": """scoreboard players set #stage mpc 92
fd world set phase 0
execute as @e[tag=macs_pawn_natural] run fd architect stop @s
execute as @e[tag=macs_pawn_natural] run fd architect dump @s
tellraw @s [{"text":"SUPPLY SAMPLE COMPLETE. NEW natural Architects captured this window="},{"score":{"name":"#nat_count","objective":"mpc"}},{"text":"; first appeared after "},{"score":{"name":"#nat_first","objective":"mpc"}},{"text":" ticks (-1 means none). Keep them held for review."}]
""" + tell("Wait for Sprint completed, then run /fd maeve dump directly in chat. Reply done. Zero spawns is a possible result; do not summon anything or repeat setup."),
    "natural_second": """execute unless score #stage mpc matches 92 run return 0
execute unless score #nat_count mpc matches 1 run return 0
execute if score #nat_station mpc matches 2 run return 0
execute unless entity @e[tag=macs_pawn_natural,nbt=!{Health:0.0f}] run return 0
execute if entity @e[type=frozendawn:architect,x=3904,y=5,z=3958,dx=193,dy=194,dz=193] run return run tellraw @s {"text":"Second station blocked by an existing Architect. Keep frozen and report this; do not move the pawn.","color":"red"}
gamemode spectator @s
fd world set phase 0
fill 3998 101 4052 4002 103 4056 minecraft:air
setblock 4000 100 4054 minecraft:lapis_block
tp @s 4000.5 101 4054.5 90 0
scoreboard players set #nat_station mpc 2
scoreboard players set #nat_count mpc 0
scoreboard players set #nat_first mpc -1
scoreboard players add #nat_window mpc 1
scoreboard players set #stage mpc 90
scoreboard players set #timer mpc 0
""" + tell("Second sampling station. The first natural pawn remains at its birthplace, held for review. Stay on this blue marker and run the empty sampling sprint; spawn settings are unchanged.", "/tick sprint 6040t"),
    "natural_retry": """execute unless score #stage mpc matches 92 run return 0
execute unless score #nat_count mpc matches 0 run return 0
gamemode spectator @s
forceload add 3840 3922 4008 4090
scoreboard players add #nat_window mpc 1
scoreboard players set #stage mpc 90
scoreboard players set #timer mpc 0
""" + tell("Another unchanged production sampling window is ready. Run /tick sprint 6040t; zero remains a valid result.", "/tick sprint 6040t"),
})
SCRIPTS["tick"] += """
execute if score #stage mpc matches 90 run scoreboard players add #timer mpc 1
execute if score #stage mpc matches 90 if score #timer mpc matches 40.. as @a[tag=macs_pawn,limit=1] run function macs_pawn:natural_begin
execute if score #stage mpc matches 91 run scoreboard players add #timer mpc 1
execute if score #stage mpc matches 91 unless score #nat_station mpc matches 2 unless entity @a[tag=macs_pawn,x=3921,y=101,z=4004,dx=4,dy=2,dz=4] as @a[tag=macs_pawn,limit=1] run function macs_pawn:natural_invalid
execute if score #stage mpc matches 91 if score #nat_station mpc matches 2 unless entity @a[tag=macs_pawn,x=3998,y=101,z=4052,dx=4,dy=2,dz=4] as @a[tag=macs_pawn,limit=1] run function macs_pawn:natural_invalid
execute if score #stage mpc matches 91 as @e[type=frozendawn:architect,tag=!macs_natural_existing,x=3840,y=-64,z=3922,dx=226,dy=383,dz=198] unless entity @s[x=3840,y=101,z=3922,dx=226,dy=3,dz=198] run function macs_pawn:natural_invalid_actor
execute if score #stage mpc matches 91 as @e[type=frozendawn:architect,tag=!macs_natural_existing,x=3840,y=101,z=3922,dx=226,dy=3,dz=198] run function macs_pawn:natural_capture
execute if score #stage mpc matches 91 if score #timer mpc matches 6000.. as @a[tag=macs_pawn,limit=1] run function macs_pawn:natural_checkpoint
"""


# Release only the roster captured from real production births. Waiting for admission
# and observing a dispatched group have separate clocks; neither can claim success.
SCRIPTS.update({
    "natural_watch": """execute unless score #stage mpc matches 92 run return 0
execute unless score #nat_station mpc matches 2 run return 0
execute unless score #nat_count mpc matches 1 run return 0
execute store result score #nat_roster mpc if entity @e[tag=macs_pawn_natural,nbt={NoAI:1b},nbt=!{Health:0.0f}]
execute unless score #nat_roster mpc matches 2 run return 0
gamemode spectator @s
tp @s 3976.5 125 3976.5 -50 25
fd world set phase 6 late
tag @e[tag=macs_pawn_natural] remove macs_natural_arrived
execute as @e[tag=macs_pawn_natural] run fd architect record @s 1337
execute as @e[tag=macs_pawn_natural] run fd architect mark @s NATURAL_WATCH_START
execute as @e[tag=macs_pawn_natural] run data merge entity @s {NoAI:0b,Glowing:1b}
scoreboard players set #timer mpc 0
scoreboard players set #watch_result mpc 0
scoreboard players set #stage mpc 93
""" + tell("NATURAL ARRIVAL VIEW: stay at this camera and turn with your mouse. One outlined Architect starts to your east, the other west. Watch at NORMAL speed until the result prompt. No sprint or attacks.", color="aqua") + "\nreturn 1",
    "natural_watch_admitted": """execute unless score #stage mpc matches 93 run return 0
scoreboard players set #stage mpc 95
scoreboard players set #timer mpc 0
execute store result score #cloud_tick mpc run time query gametime
""" + tell("The group has been dispatched. Keep watching; this view now waits for BOTH pawns to enter the hotspot, or for an inconclusive stop.", color="aqua"),
    "natural_watch_arrived": """execute unless score #stage mpc matches 95 run return 0
scoreboard players set #stage mpc 96
scoreboard players set #watch_result mpc 1
scoreboard players set #timer mpc 0
execute store result score #arrival_tick mpc run time query gametime
scoreboard players operation #elapsed mpc = #arrival_tick mpc
scoreboard players operation #elapsed mpc -= #cloud_tick mpc
""",
    "natural_watch_end": """execute unless score #stage mpc matches 93 unless score #stage mpc matches 95 unless score #stage mpc matches 96 run return 0
scoreboard players set #stage mpc 94
execute as @e[tag=macs_pawn_natural] run fd architect mark @s NATURAL_WATCH_END
execute as @e[tag=macs_pawn_natural] run fd architect dump @s
execute as @e[tag=macs_pawn_natural] run data merge entity @s {NoAI:1b}
fd world set phase 0
""" + "\n".join([
        "execute if score #watch_result mpc matches 1 run " + tell("NATURAL VIEW COMPLETE: both original pawns entered the hotspot. They are held for review. Pause and describe what you saw; no dump command needed. Arrival is not combat success.", color="green"),
        "execute if score #watch_result mpc matches -1 run " + tell("NATURAL VIEW INCONCLUSIVE: no group was admitted during the 90-second observation. Pause and report this; do not repeat setup.", color="yellow"),
        "execute if score #watch_result mpc matches -2 run " + tell("NATURAL VIEW INCONCLUSIVE: the approach window ended before both arrivals. Pause and report what happened; no dump command needed.", color="yellow"),
        "execute if score #watch_result mpc matches -3 run " + tell("NATURAL VIEW INCONCLUSIVE: the group ended or a pawn became unavailable before both arrivals. Pause and report what happened; no dump command needed.", color="yellow"),
    ]) + "\nreturn 1",
    "natural_watch_tick": """scoreboard players add #timer mpc 1
scoreboard players set #admitted mpc 0
execute as @e[tag=macs_pawn_natural,nbt=!{Health:0.0f}] if data entity @s NeoForgeData.macsConvergence run scoreboard players add #admitted mpc 1
execute if score #stage mpc matches 93 if score #admitted mpc matches 2 run function macs_pawn:natural_watch_admitted
execute if score #stage mpc matches 93 if score #timer mpc matches 1800.. run scoreboard players set #watch_result mpc -1
execute if score #stage mpc matches 93 if score #watch_result mpc matches -1 run function macs_pawn:natural_watch_end
execute if score #stage mpc matches 95 if score #timer mpc matches 240.. positioned 4003 101 4006 as @e[tag=macs_pawn_natural,nbt=!{Health:0.0f},distance=..24] if data entity @s NeoForgeData.macsConvergence run tag @s add macs_natural_arrived
execute if score #stage mpc matches 95 store result score #arrived mpc if entity @e[tag=macs_pawn_natural,tag=macs_natural_arrived,nbt=!{Health:0.0f}]
execute if score #stage mpc matches 95 if score #arrived mpc matches 2 run function macs_pawn:natural_watch_arrived
execute if score #stage mpc matches 95 unless score #admitted mpc matches 2 run scoreboard players set #watch_result mpc -3
execute if score #stage mpc matches 95 if score #watch_result mpc matches -3 run function macs_pawn:natural_watch_end
execute if score #stage mpc matches 95 if score #timer mpc matches 2420.. run scoreboard players set #watch_result mpc -2
execute if score #stage mpc matches 95 if score #watch_result mpc matches -2 run function macs_pawn:natural_watch_end
execute if score #stage mpc matches 96 if score #timer mpc matches 100.. run function macs_pawn:natural_watch_end
""",
})
SCRIPTS["tick"] += """
execute if score #stage mpc matches 93..96 unless score #stage mpc matches 94 as @a[tag=macs_pawn,limit=1] run function macs_pawn:natural_watch_tick
"""


# Separate QA module: the survival replay preserves production code and historical evidence.
from macs_base_diversion import scripts as base_diversion_scripts
SCRIPTS.update(base_diversion_scripts(tell))
from macs_base_idle import scripts as base_idle_scripts
SCRIPTS.update(base_idle_scripts(tell))
SCRIPTS["tick"] += "execute if score #stage mpc matches 101..102 as @a[tag=macs_base_player,limit=1] run function macs_pawn:base_tick\n"

SCRIPTS["tick"] += "execute if score #stage mpc matches 111 as @a[tag=macs_base_player,limit=1] run function macs_pawn:base_idle_tick\n"


from macs_base_focus import scripts as base_focus_scripts
SCRIPTS.update(base_focus_scripts(tell))
SCRIPTS["tick"] += "execute if score #stage mpc matches 122 as @a[tag=macs_base_player,limit=1] run function macs_pawn:focus_tick\n"


def write_pack(path, game_test=False):
    functions = path / "data/macs_pawn/function"
    functions.mkdir(parents=True, exist_ok=True)
    for name, content in SCRIPTS.items():
        (functions / f"{name}.mcfunction").write_text(content + "\n")
    if not game_test:
        (path / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 48, "description": "MACS Pawn Convergence ordinary-death replay"}}))
        tags = path / "data/minecraft/tags/function"
        tags.mkdir(parents=True, exist_ok=True)
        for name in ("load", "tick"):
            (tags / f"{name}.json").write_text(json.dumps({"values": [f"macs_pawn:{name}"]}))


def prepare(source, destination, world_title="MACS Pawn Convergence"):
    if destination.exists():
        raise SystemExit(f"Refusing to overwrite {destination}")
    raw = gzip.decompress((source / "level.dat").read_bytes())
    marker = b"\x08\x00\x09LevelName"
    if raw.count(marker) != 1:
        raise SystemExit("Expected one LevelName in the closed source world")
    at = raw.index(marker) + len(marker)
    length = int.from_bytes(raw[at:at+2], "big")
    title = world_title.encode("utf-8")
    shutil.copytree(source, destination, ignore=shutil.ignore_patterns("session.lock"))
    (destination / "level.dat").write_bytes(gzip.compress(raw[:at] + len(title).to_bytes(2, "big") + title + raw[at+2+length:]))
    # Only replace datapacks inside this newly created disposable copy.
    if (destination / "datapacks").exists():
        shutil.rmtree(destination / "datapacks")
    write_pack(destination / "datapacks/macs-pawn")
    print(f"Prepared {destination}; follow its replay instructions. The setup function resets tactical history.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pack-only", type=Path)
    parser.add_argument("--source", type=Path)
    parser.add_argument("--destination", type=Path)
    parser.add_argument("--title", default="MACS Pawn Convergence")
    args = parser.parse_args()
    if args.pack_only:
        write_pack(args.pack_only, True)
    elif args.source and args.destination:
        prepare(args.source, args.destination, args.title)
    else:
        parser.error("Use --pack-only or --source/--destination")

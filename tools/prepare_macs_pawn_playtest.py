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

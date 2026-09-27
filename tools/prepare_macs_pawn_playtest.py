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


def prepare(source, destination):
    if destination.exists():
        raise SystemExit(f"Refusing to overwrite {destination}")
    raw = gzip.decompress((source / "level.dat").read_bytes())
    marker = b"\x08\x00\x09LevelName"
    if raw.count(marker) != 1:
        raise SystemExit("Expected one LevelName in the closed source world")
    at = raw.index(marker) + len(marker)
    length = int.from_bytes(raw[at:at+2], "big")
    title = b"MACS Pawn Convergence"
    shutil.copytree(source, destination, ignore=shutil.ignore_patterns("session.lock"))
    (destination / "level.dat").write_bytes(gzip.compress(raw[:at] + len(title).to_bytes(2, "big") + title + raw[at+2+length:]))
    # Only replace datapacks inside this newly created disposable copy.
    if (destination / "datapacks").exists():
        shutil.rmtree(destination / "datapacks")
    write_pack(destination / "datapacks/macs-pawn")
    print(f"Prepared {destination}; run /function macs_pawn:setup only in this new QA copy.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pack-only", type=Path)
    parser.add_argument("--source", type=Path)
    parser.add_argument("--destination", type=Path)
    args = parser.parse_args()
    if args.pack_only:
        write_pack(args.pack_only, True)
    elif args.source and args.destination:
        prepare(args.source, args.destination)
    else:
        parser.error("Use --pack-only or --source/--destination")

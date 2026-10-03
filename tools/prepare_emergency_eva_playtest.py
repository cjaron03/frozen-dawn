#!/usr/bin/env python3
"""Copy a closed lab save into an isolated emergency EVA preview; never overwrite a save."""
import argparse
import gzip
import json
import shutil
from pathlib import Path

TITLE = "Emergency EVA Respawn Lab"
SCRIPTS = {
    "load": "scoreboard objectives add eeva dummy\n",
    "setup": """execute store result score #access eeva run fdlab emergency_eva access
execute unless score #access eeva matches 1 run return 0
execute if score #stage eeva matches 1.. run return 0
gamemode creative @s
tag @s add emergency_eva_lab
clear @s
effect clear @s
gamerule keepInventory false
gamerule doMobSpawning false
gamerule doDaylightCycle false
gamerule doWeatherCycle false
weather clear
fd world set total-days 100
fd world set day 104
fill 11990 100 11990 12010 100 12010 minecraft:stone
fill 11994 101 11994 12006 106 12006 minecraft:stone
fill 11995 101 11995 12005 105 12005 minecraft:air
setblock 12000 101 11994 minecraft:iron_door[facing=south,half=lower]
setblock 12000 102 11994 minecraft:iron_door[facing=south,half=upper]
setblock 12001 102 11995 minecraft:stone_button[face=wall,facing=south]
setblock 12001 102 11993 minecraft:stone_button[face=wall,facing=north]
setblock 11997 101 12000 frozendawn:thermal_heater[lit=true]
data merge block 11997 101 12000 {BurnTime:240000}
setblock 12003 101 12000 minecraft:chest[facing=west]
item replace block 12003 101 12000 container.0 with frozendawn:eva_helmet
item replace block 12003 101 12000 container.1 with frozendawn:eva_chestplate
item replace block 12003 101 12000 container.2 with frozendawn:eva_leggings
item replace block 12003 101 12000 container.3 with frozendawn:eva_boots
item replace block 12003 101 12000 container.4 with frozendawn:o2_tank_mk3[frozendawn:o2_level=3600]
item replace block 12003 101 12000 container.5 with minecraft:bread 64
tp @s 12000.5 101 12000.5 90 0
spawnpoint @s 12000 101 12000
setworldspawn 12000 101 12000
scoreboard players set #stage eeva 1
tellraw @s {"text":"Emergency EVA preview ready. Lit heater, day 104. Click START to enter Survival and die once; then click Respawn.","color":"yellow"}
tellraw @s {"text":"[START]","color":"green","clickEvent":{"action":"run_command","value":"/function emergency_eva:start"}}
return 1
""",
    "start": """execute store result score #access eeva run fdlab emergency_eva access
execute unless score #access eeva matches 1 run return 0
execute unless entity @s[tag=emergency_eva_lab] run return 0
execute unless score #stage eeva matches 1 run return 0
scoreboard players set #stage eeva 2
gamemode survival @s
kill @s
return 1
""",
    "controls": """execute store result score #access eeva run fdlab emergency_eva access
execute unless score #access eeva matches 1 run return 0
execute unless entity @s[tag=emergency_eva_lab] run return 0
tellraw @s {"text":"[2 minutes]","color":"gold","clickEvent":{"action":"run_command","value":"/fdlab emergency_eva reserve 120"}}
tellraw @s {"text":"[1 minute]","color":"red","clickEvent":{"action":"run_command","value":"/fdlab emergency_eva reserve 60"}}
tellraw @s {"text":"[15 seconds]","color":"red","clickEvent":{"action":"run_command","value":"/fdlab emergency_eva reserve 15"}}
return 1
""",
}


def prepare(source, destination):
    if destination.exists():
        raise SystemExit(f"Refusing to overwrite {destination}")
    raw = gzip.decompress((source / "level.dat").read_bytes())
    marker = b"\x08\x00\x09LevelName"
    if raw.count(marker) != 1:
        raise SystemExit("Expected exactly one LevelName")
    at = raw.index(marker) + len(marker)
    size = int.from_bytes(raw[at:at + 2], "big")
    name = TITLE.encode()
    shutil.copytree(source, destination, ignore=shutil.ignore_patterns("session.lock"))
    (destination / "level.dat").write_bytes(gzip.compress(raw[:at] + len(name).to_bytes(2, "big") + name + raw[at + 2 + size:]))
    packs = destination / "datapacks"
    if packs.exists():
        shutil.rmtree(packs)  # Only this newly created disposable copy.
    pack = packs / "emergency-eva"
    functions = pack / "data/emergency_eva/function"
    functions.mkdir(parents=True)
    for key, script in SCRIPTS.items():
        (functions / f"{key}.mcfunction").write_text(script)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 48, "description": "Emergency EVA disposable preview"}}))
    tags = pack / "data/minecraft/tags/function"
    tags.mkdir(parents=True)
    (tags / "load.json").write_text(json.dumps({"values": ["emergency_eva:load"]}))
    print(f"Prepared {destination}. Open it and run /function emergency_eva:setup.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--destination", type=Path, required=True)
    args = parser.parse_args()
    prepare(args.source, args.destination)

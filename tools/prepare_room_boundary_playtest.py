#!/usr/bin/env python3
"""Prepare a fresh, sky-connected room-surface replay from seed metadata only."""
import argparse
import json
from pathlib import Path
import prepare_thermal_ground_playtest as seed

NS = "room_boundary_check"
GUARD = "execute unless score #built rbcheck matches 1 run return 0"


def tell(text):
    return "tellraw @s " + json.dumps({"text": text, "color": "aqua"})


def row(buttons):
    return "tellraw @s " + json.dumps([{"text": ""}] + [
        {"text": label + "  ", "color": "aqua", "clickEvent": {
            "action": "run_command", "value": command}}
        for label, command in buttons])


def scripts():
    def button(label, name):
        return label, f"/function {NS}:{name}"
    def action(commands, text):
        return "\n".join([GUARD, *commands, tell(text)])
    return {
        "load": "scoreboard objectives add rbcheck dummy\nexecute unless score #built rbcheck matches 1 run scoreboard players set #built rbcheck 0\n"
                + f"execute if score #built rbcheck matches 1 as @a run function {NS}:controls",
        "tick": f"execute if score #built rbcheck matches 0 as @a[limit=1] at @s run function {NS}:setup",
        "setup": "\n".join([
            "execute unless score #built rbcheck matches 0 run return 0",
            "scoreboard players set #built rbcheck -1", "gamemode creative @s",
            "gamerule doMobSpawning false", "gamerule doDaylightCycle false",
            "gamerule doWeatherCycle false", "weather clear", "time set noon",
            "fd world preset default", "fd world set day 120", "fd world pause",
            "fill -6 64 -3 6 319 3 minecraft:air",
            "fill -5 64 -2 5 68 2 minecraft:glass hollow",
            "fill -5 64 -2 5 64 2 minecraft:stone",
            "fill 0 65 -1 0 67 1 minecraft:glass",
            "setworldspawn -3 65 0", "spawnpoint @s -3 65 0", "tp @s -2.5 65 .5 0 0",
            "effect give @s minecraft:night_vision infinite 0 true",
            "scoreboard players set #built rbcheck 1", f"function {NS}:controls",
            tell("QA: two sealed Creative rooms, late Phase 6. Boundary faces count exposed floor, wall and ceiling sides. No O2 sources.")]),
        "controls": "\n".join([GUARD,
            row([button("[Left]", "left"), button("[Right]", "right"), ("[Read faces]", "/fd world status verbose")]),
            row([button("[Merge]", "merge"), button("[Split]", "split"), button("[Controls]", "controls")]),
            row([button("[Add pillar]", "add_pillar"), button("[Remove pillar]", "remove_pillar")]),
            row([button("[Breach]", "breach"), button("[Reseal]", "reseal")]),
            tell("Baseline each: 36 cells / 66 faces. Merged: 81 / 126. Left with one interior block: 35 / 72. Remove it before merging.")]),
        "left": action(["tp @s -2.5 65 .5 0 0"], "Click Read faces; baseline left is 36 cells / 66 faces."),
        "right": action(["tp @s 2.5 65 .5 0 0"], "Click Read faces; baseline right is 36 cells / 66 faces."),
        "merge": action(["fill 0 65 -1 0 67 1 minecraft:air"], "Both sides should read the same ID, 81 cells / 126 faces without the pillar."),
        "split": action(["tp @s -2.5 65 .5 0 0", "fill 0 65 -1 0 67 1 minecraft:glass"], "Both rooms return to 36 cells / 66 faces without the pillar."),
        "add_pillar": action(["setblock -3 66 0 minecraft:stone"], "Left loses one air cell and gains six exposed sides: 35 cells / 72 faces while partitioned."),
        "remove_pillar": action(["setblock -3 66 0 minecraft:air"], "Left returns to 36 cells / 66 faces while partitioned."),
        "breach": action(["setblock -5 65 0 minecraft:air"], "Left should report unsealed or unknown. A closed partition protects the right."),
        "reseal": action(["setblock -5 65 0 minecraft:glass"], "Left is sealed again; read faces from inside. No O2 supply is present.")
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--destination", required=True, type=Path)
    args = parser.parse_args()
    seed.TITLE = "Room Boundary Check"
    seed.PACK = "room-boundary-check"
    seed.NS = NS
    seed.scripts = scripts
    seed.prepare(args.source, args.destination)

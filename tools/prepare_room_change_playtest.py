#!/usr/bin/env python3
"""Prepare room-change diagnostics around real Core recovery and airlock devices."""
import argparse
import json
from pathlib import Path
import prepare_airlock_playtest as airlock
import prepare_thermal_ground_playtest as seed

NS = "room_change_check"
GUARD = "execute unless score #built rcchange matches 1 run return 0"


def tell(text):
    return "tellraw @s " + json.dumps({"text": text, "color": "aqua"})


def row(buttons):
    return "tellraw @s " + json.dumps([{"text": ""}] + [
        {"text": label + "  ", "color": "aqua", "clickEvent": {"action": "run_command", "value": command}}
        for label, command in buttons])


def scripts():
    airlock.NS = NS
    result = {name: body.replace("alcheck", "rcchange") for name, body in airlock.scripts().items()}
    result["setup"] = result["setup"].replace("fill -12 65 -12 12 90 12 minecraft:air",
                                           "fill -6 65 -4 6 319 4 minecraft:air")
    result["setup"] += "\n" + tell("Read the runtime change counters before and after one action. These are notification diagnostics; room heat storage is the next stage.")
    result["load"] += f"\nexecute if score #built rcchange matches 1 as @a run function {NS}:controls"
    def button(label, name):
        return label, f"/function {NS}:{name}"
    def action(commands, text):
        return "\n".join([GUARD, *commands, tell(text)])
    result.update({
        "controls": "\n".join([GUARD,
            row([button("[Base]", "base"), button("[Chamber]", "chamber"), ("[Read changes]", "/fd world status verbose")]),
            row([button("[Stone wall]", "stone"), button("[Glass wall]", "glass"), button("[Controls]", "controls")]),
            row([button("[Breach base]", "base_breach"), button("[Reseal base]", "base_reseal")]),
            tell("Stone/glass: material +1, geometry/air unchanged, room ID unchanged. Repeating the same block does nothing."),
            tell("Chamber: close both doors; put away the canister and click the real controller. Evacuation/refill: air +1 each; geometry/material stay unchanged."),
            tell("Real valve: instant air loss. Base breach/reseal: geometry changes; Core restores air after five loaded seconds. Keep inner door closed.")]),
        "base": action(["tp @s -1.5 65 .5 -90 10"], "Base selected. Read changes before swapping the marked exterior wall material."),
        "chamber": action(["tp @s 3.5 65 .5 180 10"], "Chamber selected. Empty main hand; click controller on north wall. Valve beside it. Both doors must be closed."),
        "stone": action(["setblock -2 66 -3 minecraft:stone"], "One glass boundary replaced with stone. Read changes: material increases once."),
        "glass": action(["setblock -2 66 -3 minecraft:glass"], "Boundary restored to glass. Room identity, cells and faces remain the same."),
        "base_breach": action(["setblock -2 66 3 minecraft:air"], "Base exterior breached. This intentionally evacuates the base; closed inner door protects chamber."),
        "base_reseal": action(["setblock -2 66 3 minecraft:glass"], "Base sealed. Wait five loaded seconds for real Core-fed air recovery, then Read changes.")
    })
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--destination", required=True, type=Path)
    args = parser.parse_args()
    seed.TITLE = "Room Change Check"
    seed.PACK = "room-change-check"
    seed.NS = NS
    seed.scripts = scripts
    seed.prepare(args.source, args.destination)

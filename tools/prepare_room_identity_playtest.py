#!/usr/bin/env python3
"""Fresh two-room identity replay; copies seed metadata only and refuses overwrite."""
import argparse
from pathlib import Path
import prepare_thermal_ground_playtest as seed

NS = "room_identity_check"
GUARD = "execute unless score #built richeck matches 1 run return 0"

def scripts():
    import json
    def tell(label, command=None):
        value={"text":label,"color":"aqua"}
        if command:value["clickEvent"]={"action":"run_command","value":command}
        return "tellraw @s "+json.dumps(value)
    def row(buttons):
        components=[{"text":""}]
        for label,command in buttons:
            components.append({"text":label+"  ","color":"aqua","clickEvent":{"action":"run_command","value":command}})
        return "tellraw @s "+json.dumps(components)
    result={
        "load":"scoreboard objectives add richeck dummy\nexecute unless score #built richeck matches 1 run scoreboard players set #built richeck 0\n"
               +f"execute if score #built richeck matches 1 as @a run function {NS}:controls",
        "tick":f"execute if score #built richeck matches 0 as @a[limit=1] at @s run function {NS}:setup",
        "setup":"\n".join([
            "execute unless score #built richeck matches 0 run return 0","scoreboard players set #built richeck -1",
            "gamemode creative @s","gamerule doMobSpawning false","gamerule doDaylightCycle false",
            "gamerule doWeatherCycle false","weather clear","fd world preset default","fd world set day 120","fd world pause",
            "fill -6 64 -3 6 75 3 minecraft:air","fill -5 64 -2 5 68 2 minecraft:glass hollow",
            "fill -5 64 -2 5 64 2 minecraft:stone","fill 0 65 -1 0 67 1 minecraft:glass",
            "setworldspawn -3 65 0","spawnpoint @s -3 65 0","tp @s -2.5 65 .5 0 0",
            "effect give @s minecraft:night_vision infinite 0 true","scoreboard players set #built richeck 1",
            f"function {NS}:controls",tell("QA: Creative, late Phase 6, two sealed glass rooms, no O2 sources. ID is diagnostic identity, not proof of breathable air.")]),
        "controls":"\n".join([GUARD,
            row([("[Left]",f"/function {NS}:left"),("[Right]",f"/function {NS}:right"),("[Read ID]","/fd world status verbose")]),
            row([("[Merge]",f"/function {NS}:merge"),("[Split]",f"/function {NS}:split"),("[Controls]",f"/function {NS}:controls")]),
            row([("[Fill origin]",f"/function {NS}:fill_origin"),("[Breach]",f"/function {NS}:breach"),("[Reseal]",f"/function {NS}:reseal")]),
            tell("Merge: matching IDs. Split: different IDs. Reopen: IDs persist.")]),
        "left":"\n".join([GUARD,"tp @s -2.5 65 .5 0 0",tell("Click Read room ID. Note the pressure-room ID and cell count.")]),
        "right":"\n".join([GUARD,"tp @s 2.5 65 .5 0 0",tell("Click Read room ID. Note the pressure-room ID and cell count.")]),
        "merge":"\n".join([GUARD,"fill 0 65 -1 0 67 1 minecraft:air",tell("Partition opened: read IDs from both sides; they should match.")]),
        "split":"\n".join([GUARD,"tp @s -2.5 65 .5 0 0","fill 0 65 -1 0 67 1 minecraft:glass",tell("Partition restored: read IDs on both sides; they should differ. The largest overlap keeps the parent; ties follow cell position.")]),
        "fill_origin":"\n".join([GUARD,"tp @s -1.5 65 .5 0 0","setblock -3 65 0 minecraft:stone",tell("Former left query origin replaced: read ID from this surviving cell; identity should remain.")]),
        "breach":"\n".join([GUARD,"setblock -5 65 0 minecraft:air",tell("Left exterior breached: left room should report Unsealed or unknown; a closed partition protects the right.")]),
        "reseal":"\n".join([GUARD,"setblock -5 65 0 minecraft:glass",tell("Exterior resealed: identity returns through surviving membership. Without O2 supply, trapped air stays depleted.")]),
    }
    return result

if __name__ == "__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source",required=True,type=Path)
    parser.add_argument("--destination",required=True,type=Path)
    args=parser.parse_args()
    seed.TITLE="Room Identity Check";seed.PACK="room-identity-check";seed.NS=NS;seed.scripts=scripts
    seed.prepare(args.source,args.destination)

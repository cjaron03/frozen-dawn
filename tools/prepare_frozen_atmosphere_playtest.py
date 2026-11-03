#!/usr/bin/env python3
"""Create a separate normal-terrain placement check from seed metadata only."""
import argparse
import gzip
import json
from pathlib import Path
from prepare_emergency_eva_playtest import fresh_metadata, named, payload_end, string_end

TITLE = "Frozen Atmosphere Placement Check"
NAMESPACE = "frozen_atmosphere_check"


def tell(text, command=None):
    value = {"text": text, "color": "aqua"}
    if command:
        value["clickEvent"] = {"action": "run_command", "value": "/" + command}
    return "tellraw @s " + json.dumps(value)


def metadata(source):
    raw = fresh_metadata(gzip.decompress(source.read_bytes()), TITLE)
    # Replace spawn and pack metadata without copying any terrain or saved player history.
    replacements = {"SpawnX": (3, (0).to_bytes(4, "big", signed=True)),
                    "SpawnY": (3, (65).to_bytes(4, "big", signed=True)),
                    "SpawnZ": (3, (6).to_bytes(4, "big", signed=True))}
    result = bytearray(raw[:10])
    offset = 10
    while raw[offset]:
        start = offset
        kind = raw[offset]
        end_name = string_end(raw, offset + 1)
        key = raw[offset + 3:end_name].decode()
        offset = payload_end(raw, kind, end_name)
        if key == "DataPacks":
            continue
        if key in replacements:
            new_kind, value = replacements.pop(key)
            result.extend(named(new_kind, key, value))
        else:
            result.extend(raw[start:offset])
    for key, (kind, value) in replacements.items():
        result.extend(named(kind, key, value))
    def strings(values):
        payload = b"\x08" + len(values).to_bytes(4, "big")
        for value in values:
            value = value.encode()
            payload += len(value).to_bytes(2, "big") + value
        return payload
    packs = named(9, "Enabled", strings(["vanilla", "mod_data", "file/frozen-atmosphere-check"]))
    packs += named(9, "Disabled", strings([])) + b"\x00"
    result.extend(named(10, "DataPacks", packs))
    result.extend(b"\x00\x00")
    if payload_end(result, 10, 3) != len(result):
        raise ValueError("Invalid placement-check metadata")
    return bytes(result)


def prepare(source, destination):
    if destination.exists():
        raise SystemExit("Placement check already exists; refusing to overwrite.")
    destination.mkdir(parents=True)
    (destination / "level.dat").write_bytes(gzip.compress(metadata(source / "level.dat")))
    pack = destination / "datapacks/frozen-atmosphere-check"
    def write(name, content):
        path = pack / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content + "\n")
    write("pack.mcmeta", json.dumps({"pack": {"pack_format": 48, "description": "Frozen atmosphere placement check"}}))
    for tag in ("load", "tick"):
        write(f"data/minecraft/tags/function/{tag}.json", json.dumps({"values": [f"{NAMESPACE}:{tag}"]}))
    scripts = {
        "load": "scoreboard objectives add fap dummy\nexecute unless score #built fap matches 1 run scoreboard players set #built fap 0",
        "tick": f"execute if score #built fap matches 0 as @a[limit=1] at @s run function {NAMESPACE}:setup",
        "setup": "\n".join([
            "gamemode creative @s", "gamerule doMobSpawning false", "gamerule doDaylightCycle false",
            "gamerule doWeatherCycle false", "gamerule keepInventory true", "weather clear",
            "fd world preset default", "fd world set phase 6 late", "fd world pause",
            "fill -8 64 -8 8 64 8 minecraft:bedrock", "fill -8 65 -8 8 71 8 minecraft:air",
            "fill -8 72 -8 8 72 8 minecraft:bedrock",
            "fill -8 65 -8 -8 71 8 minecraft:bedrock", "fill 8 65 -8 8 71 8 minecraft:bedrock",
            "fill -8 65 -8 8 71 -8 minecraft:bedrock", "fill -8 65 8 8 71 8 minecraft:bedrock",
            "setworldspawn 0 65 6",
            "setblock -3 64 0 minecraft:lime_concrete", "setblock 3 64 0 minecraft:cyan_concrete",
            "setblock -3 65 0 frozendawn:frozen_atmosphere[dark=false]",
            "setblock 3 65 0 frozendawn:frozen_atmosphere[dark=true]",
            "setblock -3 64 -4 minecraft:yellow_concrete", "setblock 3 64 -4 minecraft:yellow_concrete",
            "setblock -3 65 -4 frozendawn:frozen_atmosphere[dark=false]",
            "setblock 3 65 -4 frozendawn:frozen_atmosphere[dark=true]",
            "tp @s 0.5 65 6.5 180 20", "clear @s", "give @s minecraft:stone 16",
            "give @s minecraft:diamond_pickaxe", "give @s minecraft:glass 16",
            "item replace entity @s armor.head with frozendawn:eva_helmet",
            "item replace entity @s armor.chest with frozendawn:eva_chestplate",
            "item replace entity @s armor.legs with frozendawn:eva_leggings",
            "item replace entity @s armor.feet with frozendawn:eva_boots",
            "give @s frozendawn:o2_tank_mk3 4", "effect give @s minecraft:night_vision infinite 0 true",
            "scoreboard players set #built fap 1",
            tell("Place stone directly on each thin deposit over the green/cyan pads. It should fill that same block space."),
            tell("Switch to Survival before mining the two deposits over yellow pads to check shard drops."),
            tell("[Switch to Survival]", f"function {NAMESPACE}:survival"),
            tell("[Switch to Creative]", "gamemode creative")]),
        "survival": "\n".join(["gamemode survival @s", "effect give @s minecraft:resistance infinite 4 true",
            "effect give @s minecraft:regeneration infinite 4 true", "effect give @s minecraft:saturation infinite 0 true",
            tell("Survival check: placement should consume one block and fill the layer's space.")])
    }
    for name, content in scripts.items():
        write(f"data/{NAMESPACE}/function/{name}.mcfunction", content)
    print(destination.resolve())


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--destination", type=Path, required=True)
    args = parser.parse_args()
    prepare(args.source, args.destination)

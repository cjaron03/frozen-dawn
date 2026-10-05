#!/usr/bin/env python3
"""Create fresh default terrain from closed default-world metadata; never overwrite a save."""
import argparse
import gzip
import json
import struct
from pathlib import Path

TITLE = "Emergency EVA Recovery - Phase 6"
CONTINUITY_TITLE = "ORSA Continuity - Phase 6"
SCRIPTS = {
    "load": """scoreboard objectives add eeva dummy
execute unless score #stage eeva matches -2147483648..2147483647 run scoreboard players set #stage eeva 0
""",
    "tick": "execute as @a if score #stage eeva matches 0 run function emergency_eva:setup\n",
    "setup": """execute store result score #access eeva run fdlab emergency_eva access
execute unless score #access eeva matches 1 run return 0
execute unless score #stage eeva matches 0 run return 0
scoreboard players set #stage eeva -1
gamemode creative @s
tag @s add emergency_eva_lab
tellraw @s {"text":"Preparing a late Phase 6 base and a separate terrain respawn. Please wait for READY.","color":"yellow"}
gamerule keepInventory false
gamerule doMobSpawning true
gamerule doDaylightCycle true
gamerule doWeatherCycle true
difficulty normal
fd world set day 104
execute store result score #prepared eeva run fdlab emergency_eva prepare_recovery
execute unless score #prepared eeva matches 1 run tellraw @s {"text":"Preparation failed. Stay in Creative; do not start. Check the client log.","color":"red"}
execute unless score #prepared eeva matches 1 run return 0
scoreboard players set #stage eeva 1
tellraw @s {"text":"READY: ordinary EVA equipped at the base. Death will drop it here. Respawn outside and walk back to recover it. Natural spawning and weather are active.","color":"yellow"}
tellraw @s {"text":"[DIE AND WALK BACK]","color":"green","clickEvent":{"action":"run_command","value":"/function emergency_eva:start"}}
return 1
""",
    "repair": """execute store result score #access eeva run fdlab emergency_eva access
execute unless score #access eeva matches 1 run return 0
execute unless entity @s[tag=emergency_eva_lab] run return 0
execute unless score #stage eeva matches -1 run return 0
execute unless entity @s[gamemode=creative] run return 0
execute store result score #prepared eeva run fdlab emergency_eva prepare_recovery
execute unless score #prepared eeva matches 1 run return 0
scoreboard players set #stage eeva 1
tellraw @s {"text":"READY: base and outdoor respawn verified. Ordinary EVA is equipped. Your death drops will remain at the base.","color":"yellow"}
tellraw @s {"text":"[DIE AND WALK BACK]","color":"green","clickEvent":{"action":"run_command","value":"/function emergency_eva:start"}}
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


def string_end(raw, offset):
    return offset + 2 + int.from_bytes(raw[offset:offset + 2], "big")


def payload_end(raw, kind, offset):
    """Walk NBT without rewriting the template's world-generation settings."""
    if kind in (1, 2, 3, 4, 5, 6):
        return offset + {1: 1, 2: 2, 3: 4, 4: 8, 5: 4, 6: 8}[kind]
    if kind == 8:
        return string_end(raw, offset)
    if kind in (7, 11, 12):
        size = int.from_bytes(raw[offset:offset + 4], "big", signed=True)
        return offset + 4 + size * {7: 1, 11: 4, 12: 8}[kind]
    if kind == 9:
        item_kind = raw[offset]
        size = int.from_bytes(raw[offset + 1:offset + 5], "big", signed=True)
        offset += 5
        for _ in range(size):
            offset = payload_end(raw, item_kind, offset)
        return offset
    if kind == 10:
        while raw[offset]:
            child_kind = raw[offset]
            offset = payload_end(raw, child_kind, string_end(raw, offset + 1))
        return offset + 1
    raise ValueError(f"Unsupported NBT tag {kind}")


def named(kind, name, payload):
    key = name.encode()
    return bytes([kind]) + len(key).to_bytes(2, "big") + key + payload


def compound_child(raw, start, wanted):
    cursor = start
    while raw[cursor]:
        kind = raw[cursor]
        end_name = string_end(raw, cursor + 1)
        key = raw[cursor + 3:end_name].decode()
        end = payload_end(raw, kind, end_name)
        if key == wanted:
            return kind, end_name, end
        cursor = end
    raise SystemExit(f"Missing world metadata: {wanted}")


def default_overworld(raw, data_start):
    # Other mod dimensions legitimately use flat generators; inspect only the Overworld.
    cursor = data_start
    for key in ("WorldGenSettings", "dimensions", "minecraft:overworld", "generator"):
        kind, cursor, _ = compound_child(raw, cursor, key)
        if kind != 10:
            return False
    for key, expected in (("type", "minecraft:noise"), ("settings", "minecraft:overworld")):
        kind, start, end = compound_child(raw, cursor, key)
        if kind != 8 or raw[start + 2:end].decode() != expected:
            return False
    return True


def fresh_metadata(raw, title=TITLE):
    if raw[:3] != b"\x0a\x00\x00":
        raise SystemExit("Expected a Minecraft level.dat root compound")
    cursor = 3
    data_start = None
    while raw[cursor]:
        kind = raw[cursor]
        end_name = string_end(raw, cursor + 1)
        key = raw[cursor + 3:end_name].decode()
        if key == "Data" and kind == 10:
            data_start = end_name
            break
        cursor = payload_end(raw, kind, end_name)
    if data_start is None:
        raise SystemExit("Missing Minecraft level.dat Data compound")
    if not default_overworld(raw, data_start):
        raise SystemExit("Use metadata from a default terrain Overworld")
    replacement = {
        "LevelName": (8, len(title.encode()).to_bytes(2, "big") + title.encode()),
        "GameType": (3, struct.pack(">i", 1)),  # Creative during the one-time setup only.
        "allowCommands": (1, b"\x01"),
        "Difficulty": (1, b"\x02"),
        "Time": (4, struct.pack(">q", 0)),
        "DayTime": (4, struct.pack(">q", 0)),
    }
    # Only new metadata is written. No chunks, inventory, player attachment,
    # apocalypse history, mobs, lab scoreboard or structures are copied.
    remove = {"Player", "neoforge:attachments", "NeoForgeData", "ForgeData", "ScheduledEvents", "GameRules"}
    result = bytearray(b"\x0a\x00\x00\x0a\x00\x04Data")
    offset = data_start
    while raw[offset]:
        kind = raw[offset]
        start = offset
        end_name = string_end(raw, offset + 1)
        key = raw[offset + 3:end_name].decode()
        offset = payload_end(raw, kind, end_name)
        if key in remove:
            continue
        if key in replacement:
            new_kind, payload = replacement.pop(key)
            result.extend(named(new_kind, key, payload))
        else:
            result.extend(raw[start:offset])
    for key, (kind, payload) in replacement.items():
        result.extend(named(kind, key, payload))
    result.extend(b"\x00\x00")
    if payload_end(result, 10, 3) != len(result):
        raise ValueError("Invalid fresh metadata")
    return bytes(result)


def prepare(source, destination, continuity=False):
    if destination.exists():
        raise SystemExit(f"Refusing to overwrite {destination}")
    metadata = fresh_metadata(gzip.decompress((source / "level.dat").read_bytes()), CONTINUITY_TITLE if continuity else TITLE)
    destination.mkdir(parents=True)
    (destination / "level.dat").write_bytes(gzip.compress(metadata))
    pack = destination / "datapacks/emergency-eva"
    namespace = "continuity_eva" if continuity else "emergency_eva"
    functions = pack / f"data/{namespace}/function"
    functions.mkdir(parents=True)
    for key, script in SCRIPTS.items():
        (functions / f"{key}.mcfunction").write_text(script.replace("emergency_eva:", f"{namespace}:"))
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 48, "description": "Emergency EVA default-terrain recovery preview"}}))
    tags = pack / "data/minecraft/tags/function"
    tags.mkdir(parents=True)
    for name in ("load", "tick"):
        (tags / f"{name}.json").write_text(json.dumps({"values": [f"{namespace}:{name}"]}))
    print(f"Prepared fresh default terrain: {destination}. Open it and wait for READY.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True, help="Closed default-world metadata template; nothing is modified")
    parser.add_argument("--destination", type=Path, required=True)
    parser.add_argument("--continuity", action="store_true", help="Separate lost-bed Continuity Protocol preview")
    args = parser.parse_args()
    prepare(args.source, args.destination, args.continuity)

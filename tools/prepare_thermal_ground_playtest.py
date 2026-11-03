#!/usr/bin/env python3
"""Create an isolated ground-temperature lab from seed metadata, never saved terrain/history."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import prepare_ration_warmer_playtest as seed

TITLE = "Thermal Ground Check"
PACK = "thermal-ground-check"
NS = "thermal_ground_check"
GUARD = "execute unless score #built tground matches 1 run return 0"
POINTS = {"surface": 64, "shallow": 32, "zero": 0, "deep": -32, "bottom": -63}


def tell(text, function=None):
    value = {"text": text, "color": "aqua"}
    if function:
        value["clickEvent"] = {"action": "run_command", "value": f"/function {NS}:{function}"}
    return "tellraw @s " + json.dumps(value)


def scripts():
    setup = [
        "execute unless score #built tground matches 0 run return 0",
        "scoreboard players set #built tground -1", "gamemode creative @s",
        "gamerule doMobSpawning false", "gamerule doDaylightCycle false",
        "gamerule doWeatherCycle false", "gamerule keepInventory true", "weather clear", "time set noon",
        "fd world preset default", "fd world set day 120", "fd world pause",
        "fill -4 -64 -4 4 74 4 minecraft:air",
        "fill -4 -64 -4 -4 74 4 minecraft:glass", "fill 4 -64 -4 4 74 4 minecraft:glass",
        "fill -4 -64 -4 4 74 -4 minecraft:glass", "fill -4 -64 4 4 74 4 minecraft:glass",
    ]
    for y in POINTS.values():
        setup.append(f"fill -3 {y-1} -3 3 {y-1} 3 minecraft:glass")
    setup += ["setworldspawn 0 64 0", "spawnpoint @s 0 64 0", "tp @s .5 64 .5 0 0",
              "effect give @s minecraft:night_vision infinite 0 true",
              "scoreboard players set #built tground 1", f"function {NS}:controls",
              tell("QA only: fresh shaft, Creative safety, no heaters. The end stage is selected. Wait 3 seconds after each click for the HUD. Bottom stands at Y -63 because the world floor is -64.")]
    result = {
        "load": "scoreboard objectives add tground dummy\nexecute unless score #built tground matches 1 run scoreboard players set #built tground 0",
        "tick": f"execute if score #built tground matches 0 as @a[limit=1] at @s run function {NS}:setup",
        "setup": "\n".join(setup),
        "controls": "\n".join([GUARD,
            tell("[Surface Y64]", "surface"), tell("[Shallow Y32]", "shallow"),
            tell("[Y0]", "zero"), tell("[Deep Y-32]", "deep"), tell("[Bottom Y-63]", "bottom"),
            tell("[P5 start]", "p5"), tell("[P6 start]", "p6"), tell("[Vacuum]", "vacuum"), tell("[End]", "end"),
            tell("[Default]", "default"), tell("[Brutal]", "brutal"), tell("[Cinematic]", "cinematic"),
            tell("[Exact background profile]", "status"),
            tell("Above Y64 retains the old altitude curve. Below it the cold front lags; deep ground stays warmer. Roofs add the existing +5C, so stay at the center markers. Preset/stage clicks intentionally change this QA world only.")]),
        "status": "\n".join([GUARD,"fd world status verbose"]),
    }
    for name, y in POINTS.items():
        result[name] = "\n".join([GUARD, f"tp @s .5 {y} .5 0 0", "fd world status verbose"])
    for name, phase in (("p5", "5"), ("p6", "6 early"), ("vacuum", "6 late")):
        result[name] = "\n".join([GUARD, f"fd world set phase {phase}", "fd world status verbose"])
    # The smallest global duration is Brutal's 50; a larger day is clamped to the same end state.
    result["end"] = "\n".join([GUARD,"fd world set day 1000","fd world status verbose"])
    for name in ("default", "brutal", "cinematic"):
        result[name] = "\n".join([GUARD,f"fd world preset {name}","fd world set day 1000","fd world status verbose"])
    return result


def prepare(source, destination):
    if destination.exists():
        raise SystemExit("Refusing to overwrite an existing world")
    original = source.read_bytes()
    digest = hashlib.sha256(original).hexdigest()
    seed.TITLE, seed.PACK = TITLE, PACK
    metadata = seed.metadata(source)
    destination.mkdir(parents=True)
    (destination / "level.dat").write_bytes(gzip.compress(metadata))
    pack = destination / "datapacks" / PACK
    pack.mkdir(parents=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 48, "description": TITLE}})+"\n")
    for name, body in scripts().items():
        path = pack / f"data/{NS}/function/{name}.mcfunction"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(body+"\n")
    for name in ("load", "tick"):
        path = pack / f"data/minecraft/tags/function/{name}.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps({"values": [f"{NS}:{name}"]})+"\n")
    assert hashlib.sha256(source.read_bytes()).hexdigest() == digest
    (destination / "preparation.json").write_text(json.dumps({
        "world": TITLE, "source": str(source), "source_sha256": digest,
        "copied": "fresh seed metadata only; no chunks, player inventory or mod SavedData",
    }, indent=2)+"\n")
    print(destination.resolve())


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--destination", required=True, type=Path)
    args = parser.parse_args()
    prepare(args.source, args.destination)

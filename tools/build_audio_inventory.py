#!/usr/bin/env python3
from __future__ import annotations

import csv
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOUND_ROOT = ROOT / "src/main/resources/assets/frozendawn/sounds"
OUTPUT = ROOT / "docs/audio/SHIPPED_AUDIO_INVENTORY.tsv"


def generated_voice_paths() -> set[str]:
    manifest = ROOT / "tools/audio_sources/generated_voice/polly_manifest.tsv"
    paths: set[str] = set()
    for line in manifest.read_text().splitlines():
        if not line or line.startswith("#"):
            continue
        paths.add(line.split("\t", 1)[0])
    return paths


VOICE_PATHS = generated_voice_paths()
INTERFACE_PATHS = {
    "item/surveyor_lens_tick.ogg",
    "ui/orsa_awakening_ring.ogg",
    "ui/suit/leak_hiss.ogg",
    "ui/suit/oxygen_beep.ogg",
}


def classify(path: str) -> tuple[str, str, str]:
    if path in VOICE_PATHS:
        return (
            "generated_voice",
            "tools/audio_sources/generated_voice/NOTICE.md",
            "CC BY-SA 4.0",
        )
    if path in INTERFACE_PATHS:
        return (
            "interface_procedural",
            "tools/audio_sources/interface/SOURCES.md",
            "Frozen Dawn original",
        )

    prefix_groups = (
        (("entity/rimebound/",), "rimebound_procedural", "tools/audio_sources/rimebound/SOURCES.md", "Frozen Dawn original"),
        (("block/stillpoint_core/",), "stillpoint_procedural", "tools/audio_sources/stillpoint_core/SOURCES.md", "Frozen Dawn original"),
        (("entity/master_architect/", "entity/thae_iven_heart/", "entity/hearth/", "music/master_architect/", "music/heart/", "ui/thaeven_"), "master_thae_iven_owner", "tools/audio_sources/master_architect/SOURCES.md", "Frozen Dawn original"),
        (("entity/aggregate/",), "aggregate_mixed", "tools/audio_sources/aggregate/SOURCES.md", "CC0-derived"),
        (("entity/frostwrithe/",), "frostwrithe_mixed", "tools/audio_sources/frostwrithe/SOURCES.md", "CC0-derived"),
        (("player/hearthrot/", "ambient/hearthrot_rasp.ogg"), "hearthrot_mixed", "tools/audio_sources/hearthrot/SOURCES.md", "CC0-derived and original"),
        (("entity/remnant/",), "remnant_mixed", "tools/audio_sources/remnant/SOURCES.md", "CC0-derived and original"),
        (("entity/resonant/",), "resonant_mixed", "tools/audio_sources/resonant/SOURCES.md", "CC0-derived"),
        (("entity/undone_architect/",), "undone_architect_mixed", "tools/audio_sources/undone_architect/SOURCES.md", "CC0 and source-site terms"),
        (("ambient/bloom/", "block/bloom_core/", "entity/bloom_spore/", "entity/bloombound_undone/", "music/bloom/"), "bloom_mixed", "tools/audio_sources/bloom/SOURCES.md", "Inherited source terms and original"),
        (("entity/archivist/",), "archivist_mixed", "tools/audio_sources/archivist/SOURCES.md", "CC0, CC BY 4.0, and original"),
        (("entity/undone/",), "undone_procedural", "tools/audio_sources/undone/SOURCES.md", "Frozen Dawn original"),
        (("ambient/wind_post_maeve_",), "post_maeve_wind_procedural", "tools/audio_sources/post_maeve_wind/SOURCES.md", "Frozen Dawn original"),
    )
    for prefixes, group, evidence, license_name in prefix_groups:
        if path.startswith(prefixes):
            return group, evidence, license_name

    original_prefixes = (
        "ambient/",
        "blocks/",
        "music/phase",
        "music/the_summer_solstice_2077.ogg",
        "radio/",
        "terminal/",
    )
    if path.startswith(original_prefixes):
        return "baseline_owner_audio", "ASSETS.md", "Frozen Dawn original"

    raise ValueError(f"No provenance classification for {path}")


def main() -> None:
    rows = []
    for source in sorted(SOUND_ROOT.rglob("*.ogg")):
        relative = source.relative_to(SOUND_ROOT).as_posix()
        group, evidence, license_name = classify(relative)
        rows.append(
            {
                "runtime_path": f"assets/frozendawn/sounds/{relative}",
                "sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
                "provenance_group": group,
                "evidence": evidence,
                "license": license_name,
            }
        )

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT.open("w", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=rows[0].keys(),
            delimiter="\t",
            lineterminator="\n",
        )
        writer.writeheader()
        writer.writerows(rows)
    print(f"Wrote {len(rows)} shipped audio records to {OUTPUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()

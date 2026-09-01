# Generated voice provenance

**Review date:** 2026-09-01
**Status:** replaced with a reproducible, attributed local Piper pipeline

This ledger covers every shipped synthesized spoken runtime asset. The current files were
generated locally with Piper's `en_US-amy-medium` model and are listed
individually in `polly_manifest.tsv`. The model card links to the Mycroft Mimic 3
voices dataset under CC BY-SA 4.0. Required attribution, modification notice,
and the ShareAlike declaration for the processed performances are preserved in
`NOTICE.md`.

## Historical macOS pipelines

The following older scripts explicitly select the macOS `Samantha` system voice:

- `tools/generate_samantha_tts.m` (AppKit `NSSpeechSynthesizer` helper)
- `tools/generate_aggregate_suit_tts.sh`
- `tools/generate_hearthrot_sounds.sh`
- `tools/generate_remnant_radio_voices.sh` (direct `say -v Samantha`)
- `tools/audio_sources/stillpoint_core/generate_stillpoint_tts.sh` (direct
  `say -v Samantha`)

They remain as historical development tooling only. Their former runtime
outputs were replaced by commit `8978618`; do not run these scripts for a
release build. The replaced groups include:

- ORSA suit/interface: `ui/suit/aggregate_*.ogg`,
  `ui/suit/biological_activity_warning.ogg`,
  `ui/suit/bloom_contact.ogg`, `ui/suit/hearthrot_contamination.ogg`,
  `ui/suit/stillpoint_field.ogg`, and `ui/suit/undone_contact.ogg`.
- Remnant radio: `entity/remnant/radio_room.ogg`, `radio_warm.ogg`,
  `radio_alone.ogg`, and `radio_forgive.ogg`.

The ORSA radio voice bank and `martian_command_message.ogg` were likewise
reconstructed from their authoritative in-game text and replaced through the
Piper manifest.

## Replaced legacy spoken assets

The following groups previously lacked source or platform evidence and are now
covered by the current Piper manifest:

- `entity/heart_successor/voice_*.ogg` (`why`, `cold`, `hello`, `kevin`, and
  `dont`).
- `ui/orsa_awakening_voice.ogg`.
- `ui/master_architect/mind_scan.ogg`, `telemetry_mismatch.ogg`, and
  `telemetry_restored.ogg`.
- `radio/voice_*.ogg` and `radio/martian_command_message.ogg`.
- Any additional spoken file later added without a matching ledger entry.

The non-spoken `ui/thaeven_*.ogg` transition beds are not voice assets and are
documented separately in the Master Architect and Thae Iven source ledger.

## Superseded platform warning

Apple's macOS software license agreements have restricted System Voices to
use with the software and personal, non-commercial original projects, with
no permission for recording, publishing, or redistribution in public or
commercial contexts. The applicable agreement is the one in effect for the
macOS version used to create each file; the project should not ship these
performances until that permission is confirmed in writing or the files are
replaced. See Apple's license hub:
https://www.apple.com/legal/sla/.

Frozen Dawn therefore does not ship the macOS exports. The current provider,
model, dataset license, exact text, and processing paths are recorded here and
in `NOTICE.md`.

## Replacement pipeline prepared

`polly_manifest.tsv` contains the exact dialogue text for the known spoken
groups; the filename is retained for compatibility with the earlier cloud
prototype. `tools/generate_local_voice_assets.sh` is the preferred offline
replacement. It runs Piper locally with one downloaded voice model and applies
the deterministic Frozen Dawn processing profile (`orsa`, `radio`, or
`successor`). It writes only to runtime audio paths, stores no credentials, and
never downloads a model automatically. Run it with `--dry-run` before model
execution. See `tools/audio_sources/generated_voice/models/README.md` for the
model and license record.

The earlier `tools/generate_polly_voice_assets.sh` remains available as an
optional cloud comparison tool, but it is not required and should not be used
for release generation when avoiding provider billing.

The manifest now covers every known spoken runtime path, including the ORSA
awakening voice and the full Martian Command transmission. The awakening row is
reconstructed from the authoritative diagnostic strings rendered by
`OrsaAwakeningIntro`; the Martian row is the complete ordered script preserved
in `MartianCommandPacketItem`, including its improvised opening and closing.
This makes the replacement reproducible, but the awakening asset should still
be auditioned against the old file before release because the repository did
not preserve a transcript of that binary.

The following are intentionally outside the Piper replacement set because they
are not TTS performances: `ui/orsa_awakening_ring.ogg`, the four
`ui/thaeven_*.ogg` transition beds, Master Architect entity vocal effects,
Undone/creature vocal effects, and the `terminal/blackglass_segment_*.ogg`
recordings. Their procedural or project-owner provenance is documented in their
feature ledgers or `ASSETS.md`; they are not silently treated as generated
speech.

## Current release state

Use the local Piper path for all release regeneration. The processed voice OGG
files are distributed under CC BY-SA 4.0 with the attribution and modification
notice in `NOTICE.md`. Piper's GPL engine and model weights are generator-only
and are not bundled. Do not describe these exports as Samantha; they preserve
the authorized text and Frozen Dawn processing profiles, not the old voice.

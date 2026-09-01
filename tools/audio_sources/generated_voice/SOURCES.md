# Generated voice provenance

**Review date:** 2026-08-27  
**Status:** unresolved for public redistribution

This ledger covers spoken runtime assets whose performances were generated with
a system voice or whose generation source is not recorded. The dialogue text,
filtering, arrangement, and implementation may be Frozen Dawn-authored; that
does not by itself grant a right to redistribute the resulting voice
performance.

## Reproducible macOS Samantha pipelines

The following scripts explicitly select the macOS `Samantha` system voice:

- `tools/generate_samantha_tts.m` (AppKit `NSSpeechSynthesizer` helper)
- `tools/generate_aggregate_suit_tts.sh`
- `tools/generate_hearthrot_sounds.sh`
- `tools/generate_remnant_radio_voices.sh` (direct `say -v Samantha`)
- `tools/audio_sources/stillpoint_core/generate_stillpoint_tts.sh` (direct
  `say -v Samantha`)

Their generated runtime outputs include:

- ORSA suit/interface: `ui/suit/aggregate_*.ogg`,
  `ui/suit/biological_activity_warning.ogg`,
  `ui/suit/bloom_contact.ogg`, `ui/suit/hearthrot_contamination.ogg`,
  `ui/suit/stillpoint_field.ogg`, and `ui/suit/undone_contact.ogg`.
- Remnant radio: `entity/remnant/radio_room.ogg`, `radio_warm.ogg`,
  `radio_alone.ogg`, and `radio_forgive.ogg`.

The ORSA radio voice bank and `martian_command_message.ogg` are also speech
assets, but their original generator and exact voice/platform terms were not
preserved in this repository. Treat them as unresolved rather than assuming
they used Samantha.

## Legacy and unrecorded spoken assets

The following shipped groups have no source URL, generation-platform record,
or redistribution permission in this checkout:

- `entity/heart_successor/voice_*.ogg` (`why`, `cold`, `hello`, `kevin`, and
  `dont`).
- `ui/thaeven_contact.ogg`, `ui/thaeven_interrupt.ogg`,
  `ui/thaeven_orsha.ogg`, and `ui/thaeven_resolve.ogg`.
- `ui/orsa_awakening_voice.ogg`.
- `ui/master_architect/mind_scan.ogg`, `telemetry_mismatch.ogg`, and
  `telemetry_restored.ogg`.
- `radio/voice_*.ogg` and `radio/martian_command_message.ogg`.
- Any additional spoken file later added without a matching ledger entry.

No claim is made here about whether these files are human-recorded,
system-generated, AI-generated, or mixed. Their status is unresolved because
the evidence needed to make that distinction is absent.

## Rights decision needed

Apple's macOS software license agreements have restricted System Voices to
use with the software and personal, non-commercial original projects, with
no permission for recording, publishing, or redistribution in public or
commercial contexts. The applicable agreement is the one in effect for the
macOS version used to create each file; the project should not ship these
performances until that permission is confirmed in writing or the files are
replaced. See Apple's license hub:
https://www.apple.com/legal/sla/.

For every cleared replacement or permission grant, record the voice/provider,
account or license terms and version, generation date, exact text, processing
steps, and whether the processed result and raw intermediate may be published.
If a provider permits only runtime/on-device synthesis, do not export and ship
the performance in the jar.

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

The following are intentionally outside the Polly replacement set because they
are not TTS performances: `ui/orsa_awakening_ring.ogg`, the four
`ui/thaeven_*.ogg` transition beds, Master Architect entity vocal effects,
Undone/creature vocal effects, and the `terminal/blackglass_segment_*.ogg`
recordings. They require their own provenance decision if they are to ship;
they are not silently treated as Polly speech.

## Current recommendation

Use the local Piper path for development and replacement auditioning. The
`en_US-amy-medium` model has a public model card and points to a CC BY-SA 4.0
dataset license; preserve attribution and review whether the final processed
performances satisfy ShareAlike before publishing the jar. Piper's GPL engine
is generator-only and is not bundled. Do not describe the local export as the
old Samantha voice; it matches the authorized text, not the prior voice
identity. A model with clearer commercial redistribution terms or an original
human performance is still preferable for the final release.

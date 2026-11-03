# ORSA TR-0 Terminus sounds

Original synthesis by Frozen Dawn through Claude Code for Terminus slice T1, 2026-11-03 (Pacific). No sampled
recordings, voice or text-to-speech, and no third-party audio or runtime dependency. The discharge itself is silent, as
the spec has it.

Reproduce with `tools/generate_terminus_sounds.sh` (ffmpeg and oggenc). Mono 44.1 kHz Vorbis, `oggenc -q 5`, fixed
noise seed 55002.

| File | Event | Character |
| --- | --- | --- |
| `sounds/item/terminus/windup.ogg` | `item.terminus.windup` | 2 s: a low tone gliding 48 to 96 Hz with its octave beating against it, a thin glassy hiss swelling under it, stopping dead; heard to 24 blocks |

Hashes are in `docs/audio/SHIPPED_AUDIO_INVENTORY.tsv`.

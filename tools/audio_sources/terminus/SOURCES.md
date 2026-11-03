# ORSA TR-0 Terminus sounds

Original synthesis by Frozen Dawn through Claude Code for Terminus slice T1, 2026-11-03 (Pacific). No sampled
recordings, voice or text-to-speech, and no third-party audio or runtime dependency. The discharge itself is silent, as
Hyperion's deathwand is: the operator hears the world drop away instead (`TerminusHush`, no audio file).

Reproduce with `tools/generate_terminus_sounds.sh` (ffmpeg and oggenc). Mono 44.1 kHz Vorbis, `oggenc -q 5`, fixed
noise seed 55002.

| File | Event | Character |
| --- | --- | --- |
| `sounds/item/terminus/windup.ogg` | `item.terminus.windup` | 2 s (owner's pick, 2026-11-03): a slow 48 Hz heartbeat quickening from 1.2 to 6 beats a second under a faint, slightly rising 5.2 kHz whine, stopping dead; heard to 8 blocks |

Hashes are in `docs/audio/SHIPPED_AUDIO_INVENTORY.tsv`.

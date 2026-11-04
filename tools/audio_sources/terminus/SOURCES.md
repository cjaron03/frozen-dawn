# ORSA TR-0 Terminus sounds

Original synthesis by Frozen Dawn through Claude Code for Terminus slice T1, 2026-11-03 (Pacific). No sampled
recordings, voice or text-to-speech, and no third-party audio or runtime dependency. The discharge itself is silent, as
Hyperion's deathwand is: the operator hears the world drop away instead (`TerminusHush`, no audio file).

Reproduce with `tools/generate_terminus_sounds.sh` (ffmpeg and oggenc). Mono 44.1 kHz Vorbis, `oggenc -q 5`, fixed
noise seed 55002.

| File | Event | Character |
| --- | --- | --- |
| `sounds/item/terminus/windup.ogg` | `item.terminus.windup` | 2 s (owner's pick, 2026-11-03): a slow 48 Hz heartbeat quickening from 1.2 to 6 beats a second under a faint, slightly rising 5.2 kHz whine, stopping dead; heard to 8 blocks |
| `sounds/blocks/vault/grind.ogg` | `block.vault.grind` | 0.55 s, slice T2: low stone on steel, a 34 Hz rumble under shuddering brown noise, replayed every half second while a vault door or the bridge moves; heard to 24 blocks |
| `sounds/blocks/vault/pulse.ogg` | `block.vault.pulse` | 0.35 s: a 42 Hz thump falling away fast, sent on the Watch Floor at a rate that quickens with the clock |
| `sounds/blocks/vault/fuse.ogg` | `block.vault.fuse` | 0.09 s: a dry relay click, pitched by the game while a hold builds |
| `sounds/blocks/vault/wrong.ogg` | `block.vault.wrong` | 0.7 s: two falling buzzes, a wrong fuse |
| `sounds/blocks/vault/shell.ogg` | `block.vault.shell` | 1.2 s: a hiss of cold air over a falling 60 Hz clunk, the Custodian's shell |
| `sounds/blocks/vault/purge.ogg` | `block.vault.purge` | 1.6 s: a rising white rush cut dead |
| `sounds/blocks/vault/announcement.ogg` | `block.vault.announcement` | 20 s, streamed: worn tape, hiss with slow flutter, crackle, a two-tone chime; so far no voice |

Hashes are in `docs/audio/SHIPPED_AUDIO_INVENTORY.tsv`.

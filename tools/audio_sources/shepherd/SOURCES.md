# Aurora (ORSA CS-1 "Shepherd") sounds

Original synthesis by Frozen Dawn through Claude Code at the owner's request for Aurora slice S1, 2026-11-04 (Pacific).
No sampled recordings, voice or text-to-speech, and no third-party audio or runtime dependency.

Reproduce with `tools/generate_shepherd_sounds.sh` (ffmpeg and oggenc). Every file is mono 44.1 kHz Vorbis, `oggenc -q 5`.
Each sound has three auditions, written to `build/shepherd-sound-candidates`. None is final until the owner has heard them.

| File | Event | Character |
| --- | --- | --- |
| `entity/shepherd/blip.ogg` | `entity.shepherd.blip` | Short sine chirp at 1180 Hz, one per three typed characters |
| `block/cs1_dock/boot.ogg` | `block.cs1_dock.boot` | Two rising tones as the cell seats |
| `block/cs1_dock/refuse.ogg` | `block.cs1_dock.refuse` | Dry click and low thud for an empty cell |

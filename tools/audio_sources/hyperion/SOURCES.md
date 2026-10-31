# SMS-1 Hyperion audio ladder

Original synthesis by Frozen Dawn through Claude Code at the owner's request, 2026-10-30 (Pacific). No sampled
recordings, voice or text-to-speech, and no third-party audio or runtime dependency. The owner auditioned and approved
the ladder in `docs/art/hyperion-storage/audio-ladder-sketch.html` (and asked that the aftermath carry no voice-like
creaks); the shipped files re-create it with ffmpeg lavfi oscillators and seeded noise.

Reproduce with `tools/generate_hyperion_sounds.sh` (ffmpeg and oggenc). Every file is mono 44.1 kHz Vorbis, `oggenc -q 5`,
fixed noise seeds 52001 to 52007; the two loops are seamless.

| File | Event | Character |
| --- | --- | --- |
| `sounds/blocks/hyperion/hum_loop.ogg` | `blocks.hyperion_hum` | 5 s loop: 50 and 100 Hz with low-passed brown noise, pulsing at 0.8 Hz |
| `sounds/blocks/hyperion/chirp.ogg` | `blocks.hyperion_chirp` | 80 ms blip rising 2.2 to 3.0 kHz |
| `sounds/blocks/hyperion/warning_loop.ogg` | `blocks.hyperion_warning` | 0.84 s loop: 881/619 Hz soft square two-tone, low-passed |
| `sounds/blocks/hyperion/whine.ogg` | `blocks.hyperion_whine` | 25 s band-limited saw, 220 to 1800 Hz, tremolo 4 to 14 Hz |
| `sounds/blocks/hyperion/quench.ogg` | `blocks.hyperion_quench` | thump falling 90 to 28 Hz with a darkening noise burst |
| `sounds/blocks/hyperion/vent.ogg` | `blocks.hyperion_vent` | 8.5 s band-passed steam roar over a low rumble |
| `sounds/blocks/hyperion/aftermath.ogg` | `blocks.hyperion_aftermath` | 10 s: thinning ticks, inharmonic pings (1 : 2.76 : 5.4), fading hiss |
| `sounds/blocks/hyperion/all_clear.ogg` | `blocks.hyperion_all_clear` | falling 1320, 990, 660 Hz bell tones |
| `sounds/blocks/hyperion/puck_drop.ogg` | `blocks.hyperion_puck_drop` | short low clunk with a small dull ring |

Hashes are in `docs/audio/SHIPPED_AUDIO_INVENTORY.tsv`.

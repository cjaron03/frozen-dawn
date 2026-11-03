# ORSA LC-4 Sever Lash sounds

Original synthesis by Frozen Dawn through Claude Code at the owner's request for Sever Lash slice 2, 2026-11-03
(Pacific). No sampled recordings, voice or text-to-speech, and no third-party audio or runtime dependency. They replace
slice 1's placeholder vanilla extinguish hiss. After the owner's sound pass ("more dangerous") the whole set was rebuilt
on the plasma roar the owner picked from three hum auditions (arc welder, plasma roar, overload).

Reproduce with `tools/generate_sever_lash_sounds.sh` (ffmpeg and oggenc). Every file is mono 44.1 kHz Vorbis,
`oggenc -q 5`, fixed noise seeds 54003, 54004 and 54011 to 54018; the hum loop is seamless.

| File | Event | Character |
| --- | --- | --- |
| `sounds/item/sever_lash/hum_loop.ogg` | `item.sever_lash.hum` | 2 s loop: brown and pink noise roar through a phaser, tanh-saturated, over a 40 Hz sub throbbing at 7 Hz; pitch rises with heat in game |
| `sounds/item/sever_lash/start.ogg` | `item.sever_lash.start` | spark click, 35 Hz thump, the roar flaring in and settling |
| `sounds/item/sever_lash/crack.ogg` | `item.sever_lash.crack` | 2 ms snap, saturated roar burst, 45 Hz punch, falling zap |
| `sounds/item/sever_lash/sizzle.ogg` | `item.sever_lash.sizzle` | hard fry and fixed crackles over a short roar burst, saturated |
| `sounds/item/sever_lash/vent.ogg` | `item.sever_lash.vent` | 50 Hz valve slam, saturated steam blast and roar, low rumble |

Hashes are in `docs/audio/SHIPPED_AUDIO_INVENTORY.tsv`.

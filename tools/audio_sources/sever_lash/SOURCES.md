# ORSA LC-4 Sever Lash sounds

Original synthesis by Frozen Dawn through Claude Code at the owner's request for Sever Lash slice 2, 2026-11-03
(Pacific). No sampled recordings, voice or text-to-speech, and no third-party audio or runtime dependency. They replace
slice 1's placeholder vanilla extinguish hiss.

Reproduce with `tools/generate_sever_lash_sounds.sh` (ffmpeg and oggenc). Every file is mono 44.1 kHz Vorbis,
`oggenc -q 5`, fixed noise seeds 53001 to 53007; the hum loop is seamless.

| File | Event | Character |
| --- | --- | --- |
| `sounds/item/sever_lash/hum_loop.ogg` | `item.sever_lash.hum` | 2 s loop: 110 Hz buzz over 55 Hz, beating 3 Hz against 113 Hz, band-passed crackle; pitch rises with heat in game |
| `sounds/item/sever_lash/start.ogg` | `item.sever_lash.start` | click, buzz sweeping 40 to 110 Hz, band-passed whoomp |
| `sounds/item/sever_lash/crack.ogg` | `item.sever_lash.crack` | 2 ms broadband snap, noisy body, zap falling 3 kHz to 300 Hz |
| `sounds/item/sever_lash/sizzle.ogg` | `item.sever_lash.sizzle` | high fry over fixed crackles |
| `sounds/item/sever_lash/vent.ogg` | `item.sever_lash.vent` | 60 Hz valve thump, steam hiss over a low rumble |

Hashes are in `docs/audio/SHIPPED_AUDIO_INVENTORY.tsv`.

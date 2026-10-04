# Emergency EVA combined breathing

The shipped `assets/frozendawn/sounds/ambient/eva_emergency_breathing.ogg` mixes
these two individually verified CC0 1.0 Universal recordings. The creator,
license and modification notice is also included in `AUDIO_NOTICE.md`, which
is packaged in the jar. CC0 attribution is voluntary; both creators are retained.

| Recording | Creator | Source / downloaded file | License | Decision |
| --- | --- | --- | --- | --- |
| Gas Mask breath | Nuclearoid | https://freesound.org/people/Nuclearoid/sounds/435825/ / `435825_8915763-hq.mp3` | CC0 1.0 | Adopt as the mask base |
| Scared Male Heavy Breathing | casiba842 | https://freesound.org/people/casiba842/sounds/554307/ / `554307_10081166-hq.mp3` | CC0 1.0 | Adopt for intermittent strained inhalations |

License: https://creativecommons.org/publicdomain/zero/1.0/

Both individual source pages link to this dedication. The public high-quality
MP3 previews are the retained source copies, not the login-only original downloads.
They were retrieved and checked on 2026-10-04 UTC. Their source pages identify
mask breathing recorded by Nuclearoid and a performance recorded by casiba842.
The records in `source_manifest.json` retain the exact download URLs, retrieval
times, format, hashes and license evidence. Full page snapshots are preserved in
`build/emergency-eva-evidence/strained-breathing-candidates/`.

Retained inputs:

- `gas_mask_breath_cc0.mp3`, SHA-256
  `f8c6ca18865381e58ff613d23a6e1f87028628ec757b388922a289aea6a8dbb8`.
- `scared_heavy_breathing_cc0.mp3`, SHA-256
  `3a6ddf5c6a97f63e633c53f4c7dfeef1348682879c2e3071fdf076a614a15151`.

Processing alternates complete source passages in this sequence:

| Output seconds | Recording | Source seconds |
| --- | --- | --- |
| 0–4.1 | Gas Mask breath | 5.0–9.1 |
| 4.1–7.4 | Scared Male Heavy Breathing | 17.4–20.7 |
| 7.4–11.6 | Gas Mask breath | 9.1–13.3 |
| 11.6–15.0 | Scared Male Heavy Breathing | 23.3–26.7 |

Each passage has separate 100 ms entrance/exit fades. No source takes are
summed or crossfaded; one recording contributes to each output sample.
Narrow-band EQ and restrained saturation shape the final fifteen-second clip.
The client plays one continuously looping emergency sound instance, with no
overlapping clip scheduler. Loudness targets the original normal-EVA recording,
with a pre-encoding peak ceiling of 0.70. This replaces the earlier additive
mix after the owner's report that multiple breaths were audible together.

Regenerate with `python3 tools/audio_sources/emergency_eva/generate_breathing.py`
(Python standard library and FFmpeg's native Vorbis encoder). Input hashes are
checked before processing. The output is stereo Vorbis at 48 kHz with identical
left/right channels. Encoded bytes may vary by FFmpeg version.

The owner's original `ambient/eva_breathing.ogg` is only a loudness reference
and is retained unchanged, SHA-256
`d3f1dfe47be702ad567bb197adac85fad695bd587553349bba64a6a15ade0adb`.
The combined emergency performance is classified as CC0-derived, rather than
as an original owner recording. Frozen Dawn's arrangement and processing are
original project work. The individual license evidence is verified; perceived
sound quality remains an owner listening check.

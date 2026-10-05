# Emergency EVA combined breathing

The shipped `assets/frozendawn/sounds/ambient/eva_emergency_breathing.ogg` sequences
four individually verified CC0 1.0 Universal recordings. The creator,
license and modification notice is also included in `AUDIO_NOTICE.md`, which
is packaged in the jar. CC0 attribution is voluntary; all creators are retained.

| Recording | Creator | Source / downloaded file | License | Decision |
| --- | --- | --- | --- | --- |
| Gas Mask breath | Nuclearoid | https://freesound.org/people/Nuclearoid/sounds/435825/ / `435825_8915763-hq.mp3` | CC0 1.0 | Adopt as the mask base |
| Scared Male Heavy Breathing | casiba842 | https://freesound.org/people/casiba842/sounds/554307/ / `554307_10081166-hq.mp3` | CC0 1.0 | Adopt for intermittent strained inhalations |
| Strong Double Cough | qubodup | https://freesound.org/people/qubodup/sounds/743360/ / `743360_71257-hq.mp3` | CC0 1.0 | Adopt for one restrained double cough per loop |
| Male Gasp 1.wav | jawbutch | https://freesound.org/people/jawbutch/sounds/344407/ / `344407_4986214-hq.mp3` | CC0 1.0 | Adopt for recovery inhales |

License: https://creativecommons.org/publicdomain/zero/1.0/

All individual source pages link to this dedication. The public high-quality
MP3 previews are the retained source copies, not the login-only original downloads.
They were retrieved and checked on 2026-10-04 UTC. Their source pages identify
mask breathing recorded by Nuclearoid and a performance recorded by casiba842.
The two added pages identify a male gasp and male coughing twice. All sources
are rechecked before incorporation. The records in `source_manifest.json` retain the exact download URLs, retrieval
times, format, hashes and license evidence. Full page snapshots are preserved in
`build/emergency-eva-evidence/strained-breathing-candidates/` and `cough-gasp/`.

Retained inputs:

- `gas_mask_breath_cc0.mp3`, SHA-256
  `f8c6ca18865381e58ff613d23a6e1f87028628ec757b388922a289aea6a8dbb8`.
- `scared_heavy_breathing_cc0.mp3`, SHA-256
  `3a6ddf5c6a97f63e633c53f4c7dfeef1348682879c2e3071fdf076a614a15151`.
- `strong_double_cough_cc0.mp3`, SHA-256
  `1dba44a22290fd52c52b19b99619a0a72372fbc71ffeaf244157926c46870c88`.
- `male_gasp_cc0.mp3`, SHA-256
  `5d6b4cf6c9eafebd4ec7d33159629e4895ff0b7ba6ee27f2df9a8fb75223ed87`.

Processing alternates complete source passages in this sequence:

| Output seconds | Recording | Source seconds |
| --- | --- | --- |
| 0–4.1 | Gas Mask breath | 5.0–9.1 |
| 4.1–7.4 | Scared Male Heavy Breathing | 17.4–20.7 |
| 7.4–11.6 | Gas Mask breath | 9.1–13.3 |
| 11.6–12.6 | Strong Double Cough | 0.02–1.02 |
| 12.6–12.74 | Brief recovery pause | Silence |
| 12.74–13.34 | Male Gasp 1.wav | 0.03–0.63 |
| 13.34–16.74 | Scared Male Heavy Breathing | 23.3–26.7 |
| 16.74–21.24 | Gas Mask breath | 14.0–18.5 |
| 21.24–24.74 | Scared Male Heavy Breathing | 8.0–11.5 |
| 24.74–29.34 | Gas Mask breath | 18.8–23.4 |
| 29.34–29.94 | Male Gasp 1.wav | 0.03–0.63 |
| 29.94–33.24 | Scared Male Heavy Breathing | 27.0–30.3 |
| 33.24–38.04 | Gas Mask breath | 23.3–28.1 |
| 38.04–42.0 | Scared Male Heavy Breathing | 30.0–33.96 |

Breath passages have separate 80 ms entrance/exit fades; cough and gasp fades
are 15 ms and 25 ms to preserve their attacks. No source takes are
summed or crossfaded; one recording contributes to each output sample.
Common narrow-band EQ, gentle 3:1 compression above 0.08 linear amplitude and
restrained saturation shape the final 42-second clip. Cough source gain is
restrained to 80 percent of matched level; gasp source gain is 110 percent.
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
The arranged emergency performance is classified as CC0-derived, rather than
as an original owner recording. Frozen Dawn's arrangement and processing are
original project work. The individual license evidence is verified; perceived
sound quality remains an owner listening check.


## Exertion tempo variant

`ambient/eva_emergency_breathing_fast.ogg` derives only from the verified shipped
42-second sequential emergency arrangement. `generate_exertion.py` guards that
base asset hash, applies FFmpeg's pitch-preserving `atempo=1.25`, and adds separate
40 ms seam fades before native Vorbis encoding. It introduces no additional
recordings or licensing inputs. The same four CC0 creator credits apply. The
base emergency arrangement and original normal EVA recording remain unchanged.

The client chooses the faster version after sustained load, keeps it through
part of recovery using hysteresis, and fades the current vocal instance fully
out before starting the other. No vocal crossfade or additive performance is
used. Volume follows the smoothed server metabolic-load mirror. Normal EVA's
existing breathing remains separate and is not accelerated by this feature.
Perceived transitions remain an owner listening check.

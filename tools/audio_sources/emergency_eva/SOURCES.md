# Emergency EVA breathing

`ambient/eva_emergency_breathing.ogg` is processed from Jaron Cabral's original
Frozen Dawn `ambient/eva_breathing.ogg`, identified in `ASSETS.md`. The original
recording is retained unchanged, SHA-256
`d3f1dfe47be702ad567bb197adac85fad695bd587553349bba64a6a15ade0adb`.

The emergency derivative narrows the filter to 140–3000 Hz, adds a papery
1600 Hz emphasis, restrained saturation, breath-following procedural rasp and
subtle 12.5 Hz valve flutter. RMS loudness is matched to the source, with a peak
ceiling of 0.85. Recording length, breath pacing and 48 kHz sample rate are
preserved for the existing overlapping playback. The mono source is duplicated
to stereo for FFmpeg's native Vorbis encoder. No downloaded recordings,
speech models or operating-system voices are used.

Regenerate with `python3 tools/audio_sources/emergency_eva/generate_breathing.py`
(Python standard library and FFmpeg with its native Vorbis encoder). The noise seed and source
hash are fixed; encoded bytes may vary with FFmpeg versions.
Original Frozen Dawn asset terms apply, as documented in `ASSETS.md`.

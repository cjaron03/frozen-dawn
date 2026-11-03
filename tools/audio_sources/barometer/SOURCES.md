# Barometer cyclone warning — Panel buzzer

Owner selected electronic concept1 on October19,2026. The two runtime sounds reproduce that preview's original Panel buzzer: two separated rough notes for an approaching Moderate/Severe cyclone, seven faster alternating bursts for a Catastrophic cyclone. Trailing silence is shortened; audible patterns are unchanged. Normal clip1.6seconds, catastrophic3.35seconds. Both are finite positional block sounds at pitch1.

Original Frozen Dawn procedural composition; no external recording, sample library, model or outside-mod dependency. Original synthesis uses sequential290/430Hz fundamentals, harmonics, amplitude roughness, gentle saturation and short enclosure reflections. It does not use an EAS recording, Attention Signal pair or encoded broadcast headers. This source record is not legal clearance for public distribution.

Reproduce with `python3 -B tools/audio_sources/barometer/generate_barometer_audio.py`. Requires Python3, ffmpeg and oggenc. The generator writes mono24kHz Vorbis to `src/main/resources/assets/frozendawn/sounds/block/barometer/`. It matches the owner-selected preview's active-signal level, gives catastrophic bursts a modest gain increase, uses deterministic Vorbis serials and verifies actual decoder output for duration, peak headroom and smooth boundaries. Exact events, output hashes and measurements are in `manifest.json`.

| Candidate | Intended use | Provenance | Decision |
| --- | --- | --- | --- |
| Original Panel buzzer normal | Local approaching-cyclone warning | Procedurally authored for Frozen Dawn; no third-party assets | Adopt, selected by owner |
| Original Panel buzzer catastrophic | Urgent local catastrophic warning | Same original recipe with denser burst cadence | Adopt, selected by owner |

The original concept previews and manifest remain in `build/cyclone-evidence/barometer-electronic-concepts/`. No third-party attribution obligation. In-game spatial loudness and warning cadence remain owner acceptance.

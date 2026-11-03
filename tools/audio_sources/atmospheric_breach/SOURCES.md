# Atmospheric breach airflow

`assets/frozendawn/sounds/ui/suit/atmospheric_breach_whoosh.ogg` is original Frozen Dawn procedural audio, authored October 9, 2026. No external recordings or textures are incorporated. Seeded band-limited noise and a decaying envelope produce a two-second airflow burst. Generate with `python3 tools/audio_sources/atmospheric_breach/generate_whoosh.py`; FFmpeg encodes stereo Vorbis. The existing original interface oxygen beep is reused unchanged. The voice warning uses the separately attributed Piper/ORSA pipeline and is covered by the generated-voice ledger and CC BY-SA notice.

## Critical loss alarm

`ui/suit/atmospheric_breach_alarm.ogg` is original Frozen Dawn procedural audio, authored October 9, 2026. Three 200 ms oscillator pulses at 880 Hz with a 1760 Hz harmonic, separated by 120 ms gaps, use short click-free attack/release envelopes. Total duration is 0.92 seconds. Generate with `python3 tools/audio_sources/atmospheric_breach/generate_alarm.py`; FFmpeg encodes stereo Vorbis at 44.1 kHz. No third-party recording, voice or sample is used. Runtime loss warnings use this dedicated alarm at full UI gain; the existing quiet oxygen telemetry pulse remains unchanged for other features.

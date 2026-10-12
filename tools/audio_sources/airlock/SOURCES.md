# ORSA airlock machinery

Created for Frozen Dawn on 2026-10-10. Original procedural audio; no recordings,
third-party assets, generated speech, or outside source URLs. License treatment:
Frozen Dawn original, under the original-audio notice in AUDIO_NOTICE.md.

Creator: Frozen Dawn project, authored through Codex at the project owner's request.
Source: `generate_machinery.py` in this directory. Deterministic seeds and all synthesis
parameters are checked in. Run with Python 3 and oggenc (libvorbis) and ffmpeg (libmp3lame).
Output: mono 24 kHz OGG at `assets/frozendawn/sounds/block/airlock/`.
`manifest.json` records exact encoded hashes, duration and pre-encode peaks.
Encoding differences across ffmpeg versions may change hashes; preserve shipped files.

- door_open: latch impact, seal hiss, short motor, travel stop.
- door_close: motor, heavy housing impact, locking bolt, seal compression.
- pump_fill / pump_recover: 0.94-second segments; distinct fill/recovery hiss.
  One segment per chamber at elapsed 0/20/40/60; no infinite client sound loop.
- ready: wind-down and restrained two-note confirmation.
- evacuated: residual vent hiss and restrained two-note confirmation.
- refuse: locking impact and short restrained hiss.
- interrupted: motor stop and mechanical lock.
- valve: valve mechanism only; existing AtmosphericBreach owns escaping-air whoosh.

Three MP3 concept sequences are generated into the ignored build evidence folder.
They assemble the same cues used by the mod; they contain no TTS or music.

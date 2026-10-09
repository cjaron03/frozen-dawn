# Architect bow release

`entity/architect/shoot_1.ogg`, `shoot_2.ogg`, and `shoot_3.ogg` are original
procedural effects created for Frozen Dawn on 2026-09-26. They contain no sampled
recordings, extracted Minecraft audio, third-party inputs, or generated speech.

The checked-in `generate_archer_audio.py` synthesizes a plucked noise string,
short release transient, filtered wordless breath, and quiet delayed reflections.
Three fixed seeds and string frequencies provide slight variation. Each effect
is 0.62 seconds, mono, 48 kHz, encoded as Ogg Vorbis quality 4. Mono preserves
positional attenuation. PCM generation is deterministic; byte-for-byte Ogg output
also depends on the installed vorbis-tools/libvorbis versions. Encoding uses
`oggenc` with a fixed per-variant Ogg serial number, matching the repository's
existing audio pipeline.

Regenerate from the repository root:

```sh
python3 tools/audio_sources/architect/generate_archer_audio.py
python3 tools/build_audio_inventory.py
```

Rights treatment: Frozen Dawn original/procedural audio under `AUDIO_NOTICE.md`.
The file hashes are recorded in `docs/audio/SHIPPED_AUDIO_INVENTORY.tsv`.

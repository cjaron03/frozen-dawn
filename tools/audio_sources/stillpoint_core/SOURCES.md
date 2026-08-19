# Stillpoint Core Audio Sources

Both shipped sounds are original procedural assets generated for Frozen Dawn.

- `charge.ogg`: layered filtered noise, low sine pressure, and granular ice impulses generated with FFmpeg filters.
- `form.ogg`: synthesized pressure transient, low-frequency body, and receding structural tail generated with FFmpeg filters.
- `hum.ogg`: seamless procedural 55 Hz pressure fundamental with prominent 110 Hz, 220 Hz, and 440 Hz harmonics for audibility on ordinary speakers.

The intermediate WAV files were encoded as mono Vorbis with `oggenc`. No third-party recordings or external game assets are present in these files. `generate_stillpoint_audio.sh` and `generate_stillpoint_hum.sh` contain the complete deterministic synthesis pipelines.

- `enter.ogg` and `exit.ogg`: original procedural pressure sweeps made from filtered deterministic noise and synthesized low-frequency transients.

`stillpoint_field.ogg` is generated locally with macOS Samantha through Frozen Dawn's existing ORSA TTS helper, then filtered and encoded with FFmpeg. The complete command and exact spoken line are recorded in `generate_stillpoint_tts.sh`.

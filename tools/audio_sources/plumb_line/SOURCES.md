# ORSA GPR-4 Plumb Line

`block/plumb_line/pulse.ogg`, `scan.ogg`, `complete.ogg` and `abort.ogg` are original procedural effects created for
Frozen Dawn on 2026-10-17. They contain no sampled recordings, extracted Minecraft audio, third-party inputs or
generated speech.

The checked-in `generate_plumb_line_audio.py` synthesizes them from oscillators, filtered deterministic noise,
envelopes and short filtered echoes:

- **pulse** (2.6 s): a transmitter discharge crack, a saturated sub-bass thump falling from 74 to 36 Hz, gravel
  rumble and ground echoes.
- **scan** (10.2 s): 60/120/180 Hz transformer hum, cooling-fan noise, receiver sampling ticks every 0.25 s and
  faint 2.65 kHz echo pings.
- **complete** (1.4 s): three descending receiver blips, a relay release and fan spin-down.
- **abort** (1.7 s): a capacitor whine collapsing as power sags, relay chatter, and a contactor clunk that takes the
  hum with it.

Each is mono (so it attenuates positionally), 48 kHz, Ogg Vorbis quality 4, with a fixed serial number. PCM
generation is deterministic; byte-for-byte Ogg output also depends on the installed vorbis-tools/libvorbis.
Previews are written to `build/plumb-line-audio-preview/`.

Regenerate from the repository root:

```sh
python3 tools/audio_sources/plumb_line/generate_plumb_line_audio.py
python3 tools/build_audio_inventory.py
```

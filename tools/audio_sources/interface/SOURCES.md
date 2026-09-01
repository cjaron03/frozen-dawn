# Interface audio sources

The interface cues below are synthesized deterministically by
`tools/generate_interface_sounds.sh` from FFmpeg oscillators and noise sources.
No recording, generated voice, or third-party sample is used.

- `src/main/resources/assets/frozendawn/sounds/item/surveyor_lens_tick.ogg`
  is a 30-millisecond rising survey chirp.
- `src/main/resources/assets/frozendawn/sounds/ui/suit/oxygen_beep.ogg` is a
  two-part telemetry pulse.
- `src/main/resources/assets/frozendawn/sounds/ui/suit/leak_hiss.ogg` is a
  filtered white/pink-noise pressure leak controlled at runtime by the suit
  leak sound instance.
- `src/main/resources/assets/frozendawn/sounds/ui/orsa_awakening_ring.ogg` is a
  layered harmonic first-boot ring.

Frozen Dawn retains rights to the original synthesis design, arrangement,
processing, mixing, and implementation. FFmpeg is a development tool and is not
bundled in the mod jar.

# CFU-7 Hearthstone Reactor audio

All four files under `assets/frozendawn/sounds/blocks/reactor/` are original Frozen Dawn procedural audio, authored
October 15, 2026, and chosen by the owner from rendered concepts. No external recording, sample or voice is used.
Generate with `python3 tools/audio_sources/hearthstone/generate_reactor_audio.py` (pure-Python synthesis, oggenc
quality 5, 44.1 kHz mono).

- `hum.ogg`: 4 s seamless loop. 55 Hz and 55.5 Hz tones beat at 0.5 Hz over a 110/165 Hz overtone and a low rumble
  bed of 40 whole-cycle partials. The Controller loops it while running; gain and pitch rise with fission heat.
- `overheat_warning.ogg`: two-tone klaxon (440/330 Hz, odd harmonics), 2 s, repeated from 350C.
- `scram.ogg`: three falling beeps then the rods dropping (decaying 55/82 Hz thud and short noise rattle). Once per trip.
- `meltdown_alert.ogg`: rising 600-1100 Hz siren, two whoops per second, repeated from 700C (loss of coolant).

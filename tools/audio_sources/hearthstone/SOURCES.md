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
- `geiger_1..5.ogg` and `geiger_dense.ogg`: cut from a real recording, Freesound 555934 "Geiger Counter Hotspot
  (Sweeping)" by cookies+policy, CC0 1.0 public domain (https://freesound.org/people/cookies+policy/sounds/555934/),
  HQ preview SHA-256 3486cf93eeddb0a954f30eead174163825ebfd177f7c4e7d7dc2024f4918da06. Five isolated 30 ms clicks
  from the sparse tail and a 2 s crossfaded loop from the hotspot peak (about 45 clicks per second). Reproduce with
  `python3 tools/audio_sources/hearthstone/extract_geiger.py --source <preview mp3>`. Owner chose it from three CC0
  recordings.
- `dosimeter_chirp.ogg` and `dosimeter_alarm.ogg`: original procedural dosimeter alarm, a 1.1 kHz tone with a
  soft third harmonic (lowered from 3 kHz at the owner's request); a 70 ms chirp and a 1 s seamless continuous loop with a slight 8 Hz warble.

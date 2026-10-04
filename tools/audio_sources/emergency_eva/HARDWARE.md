# Emergency EVA hardware cues

These three sounds are original deterministic project synthesis. There are no
recorded inputs, third-party samples, TTS, or macOS voices. Regenerate with
`python3 tools/audio_sources/emergency_eva/generate_hardware.py` (standard
Python and FFmpeg native Vorbis encoder), seed 610104, stereo 48 kHz.

- `ambient/eva_emergency_fan.ogg`: eight-second loop of 94 Hz rotor harmonics,
  restrained bearing chatter and two small regulator ticks. Noise is tapered
  at the seam. Runtime volume 0.08–0.12, with a slight pitch reduction as the
  actual reserve drains. It ends when life support stops, when normal EVA
  takes over, when paused or when leaving the world.
- `ui/suit/emergency_regulator.ogg`: 0.65-second click/purge at issue recognition
  only when the sealed rig has life support. A small hardware cue, not a new
  leak warning or speech.
- `ui/suit/emergency_shutdown.ogg`: 1.25-second switch/purge/fan coast on the
  observed transition from positive reserve to zero. Expired login does not
  trigger the transition cue. Existing typed ORSA warning presentation reports
  actual reserve exhaustion, and the telemetry reads PACK SPENT.

The four-input CC0 sequential breathing arrangement is preserved unchanged.
There is still only one vocal loop. These quiet hardware sounds are separate
mechanical cues and never add another breath performance. Auditory acceptance
remains a player listening check.

# Room oxygen warning cues

Klaxon: “Klaxon” by InfamousLazure, https://freesound.org/people/InfamousLazure/sounds/584001/ . Creator credit: https://bsky.app/profile/infamouslazure.bsky.social . The creator describes a revved motor recording, looped and reverberated. Licensed CC0 1.0 Universal: https://creativecommons.org/publicdomain/zero/1.0/ . License and individual source-page snapshots are retained here and verified before processing on 2026-10-10 (Pacific). Official public high-quality MP3 preview: https://cdn.freesound.org/previews/584/584001_10258649-hq.mp3 ; original WAV requires account login and is not included. Local source klaxon-584001-hq.mp3. Source is a preview, not the original WAV. Artist credit retained in AUDIO_NOTICE.md despite CC0 not requiring attribution.

Superseded derivative: assets/frozendawn/sounds/block/oxygen_gauge/critical.ogg . Mono, conservative normalization, 30ms fade-in/80ms fade-out, Vorbis at 44.1kHz; no extra recording or voice. Reproduce with generate_cues.py. Source/output hashes in manifest.json.

Strained cue: original short two-note electronic chime synthesized by Frozen Dawn through Codex at the owner's request. No sampled input or TTS. Output assets/frozendawn/sounds/block/oxygen_gauge/strained.ogg . Oscillators/envelopes in generate_cues.py. Original-audio notice in AUDIO_NOTICE.md applies. Generated 2026-10-10 (Pacific).

Reuse reviewed by Codex 2026-10-10; CC0 confirmed from individual asset page and canonical deed. No runtime dependency added.

| Candidate | Use | Verified rights | Decision |
| --- | --- | --- | --- |
| InfamousLazure Klaxon, Freesound 584001 | Critical room warning | Individual source and CC0 1.0 deed preserved | Superseded October 11 by original panel double beep; retain historical source and credit |
| Frozen Dawn original two-note synthesis | STRAINED entry chime | No external samples, reproducible script | Adopt; no runtime audio dependency |

The published source-page snapshot has unrelated embedded Mapbox tokens redacted after GitHub push protection flagged the incidental page script. The audio identification, creator credit and CC0 evidence remain intact; the original snapshot and its hash are retained only in ignored local evidence. No protection bypass was used.

## Panel double beep — October 11, 2026

Owner selected the first original concept: two 170ms 880Hz oscillator pulses, 290ms apart, with a 14% second harmonic, 8ms attack and 25ms release. One 550ms mono burst ships at assets/frozendawn/sounds/block/oxygen_gauge/critical.ogg, 44.1kHz Vorbis; peak amplitude 0.32. No sampled recording, voice, or third-party dependency. Reproduce with generate_cues.py; source/output hashes in manifest.json. The original seven-second concept preview repeats the same burst every two seconds. Runtime follows the same two-second cadence during known pre-loss breathing shortage at a nearby panel, and during personal FADING/DYING after breathing protection. NO O2/NO AIR/NO SEAL alone does not trigger a critical alarm. The safety beep uses the Master channel so actual vacuum cannot mute it; pressurized but oxygen-starved gas no longer suppresses carried sounds. No speech. Existing strained cue is unchanged. The former licensed klaxon source and evidence remain here for history; the shipped critical asset contains only this original synthesis.

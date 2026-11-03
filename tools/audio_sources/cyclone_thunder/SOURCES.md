# Recorded cyclone thundersnow

Owner selected a mix of shortlist candidates 1 and 2 on 2026-10-20 and rejected candidate 3 (xUMR 486557). The owner then requested at least two more distinct effects because the mix repeated. The expanded bank uses the original blend plus two independent source strikes, listed below. Individual source pages were rechecked before download and explicitly link to CC0 1.0 Universal.

| Creator / recording | Role | Verified rights | Local evidence |
| --- | --- | --- | --- |
| [cognito perceptu — thunder sharp dry crack w rumble.wav](https://freesound.org/people/cognito%20perceptu/sounds/536901/) | Dry crack and natural rolling tail | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | licenses/536901.txt; retained public HQ MP3 preview |
| [loganzsound — Closeup Thunder Strike 01](https://freesound.org/people/loganzsound/sounds/840628/) | Close strike body and tail | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | licenses/840628.txt; retained public HQ MP3 preview |
| [seth-m — big thunder clap](https://freesound.org/people/seth-m/sounds/458015/) | Independent dry clap and tails | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | licenses/458015.txt; retained public HQ MP3 preview |
| [Joseph SARDIN — Thunder #1](https://bigsoundbank.com/thunder-s2718.html) | Independent thunderclap and tails | Individual page CC0; [site license explicitly links CC0 1.0](https://bigsoundbank.com/licenses.html) | licenses/bsb-2718.txt; retained public MP3 download |

The three Freesound inputs are public high-quality MP3 previews explicitly linked on their pages; the BigSoundBank input is its public MP3 download. None is represented as a WAV master. No account or private download endpoint was used. Exact source/download URLs, filenames, retrieval times, page/input hashes, roles and modification records are in source-ledger.json. Raw HTML is retained only in ignored build evidence; tracked license snapshots contain selected human-readable metadata and links without page scripts or tokens. CC0 requires no attribution; voluntary creator credits remain here and in AUDIO_NOTICE.md.

## Reproduction

Run `python3 -B tools/audio_sources/cyclone_thunder/generate_thunder_audio.py` with ffmpeg, ffprobe and oggenc installed. The generator verifies all four retained source hashes before processing. It aligns their strong crack onsets, blends them with different ratios and small speed variations, applies a 35 Hz high-pass and distance-dependent low-pass filters, gentle 2:1 compression, shaped fades, short edge padding and level matching. Five close, four mid and four distant variants are encoded as mono 24 kHz Vorbis using fixed serial numbers. Distant variants use filtered tails from the four current sources; rejected xUMR candidate 3 is absent. The original seven blends remain byte-identical. Each bank gives equal total selection weight to the old blend, the new seth-m strike and the new Joseph Sardin strike, so the old near-duplicates do not dominate. No synthetic thunder, extra reverb or boss recording is mixed in. Manifest records per-output recipes, hashes, decoded peak/RMS, duration, format and boundary checks.

Every clip remains under the existing 10 second lifetime even at the runtime's slowest cold-filtered pitch. The existing six-roll overlap cap, stage/severity scheduling, weather slider, shelter attenuation, flashes, camera settings and cleanup implementation are unchanged. These are audio/resource replacements only. The existing Master Architect thunder files and their boss events remain unchanged.

Automated checks establish source identity, rights records, deterministic regeneration, decoder format/headroom/edge continuity, lifetime bounds, sound registration and jar/source alignment. They do not establish perceived quality; owner audition follows in the preserved Cyclone Thunder Check save.

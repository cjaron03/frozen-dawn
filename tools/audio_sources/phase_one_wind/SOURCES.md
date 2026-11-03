# Phase 1 forest wind

The owner selected [Wind-Gusts-late-autumn.wav by BudJillett](https://freesound.org/people/BudJillett/sounds/109485/) on October 20, 2026. The source describes late-autumn wind gusts in a New England pine forest. Its individual page links to [CC0 1.0 Universal](https://creativecommons.org/publicdomain/zero/1.0/). The exact license anchor, source URL and retrieval date are preserved in [the page evidence](licenses/source-page.txt), with a [current CC0 text snapshot](licenses/cc0.txt).

The retained download is the site's **official public HQ MP3 preview**, `109485_1213320-hq.mp3`, 306.121 seconds, 48 kHz stereo. It is not the account-gated original WAV. [The source ledger](source-ledger.json) records its actual encoding, creator, source/download URLs, retrieval UTC, SHA-256, license evidence and outputs. The downloaded bytes remain unchanged under `recordings/`.

| Source | Intended use | Verified rights | Decision |
| --- | --- | --- | --- |
| BudJillett forest recording | Phase 1 rainy wind, two streaming variants | Individual CC0 1.0 dedication; no runtime dependency | Owner selected the source and accepted rainy mix, smoother shelter transitions and clearing fade in the same phase-1 world |

Run `python3 -B tools/audio_sources/phase_one_wind/generate_wind.py` with ffmpeg and oggenc. The script verifies the original hash before processing. Two 57-second trims start at 90 and 180 seconds. Processing removes sub-70 Hz rumble, rolls off above 8 kHz, applies gentle 2:1 peak compression, matches RMS with a 0.80 peak cap, and fades the first/last five seconds for the existing five-second crossfade. Outputs are mono 24 kHz Vorbis quality 5 with fixed stream serials. No synthesized sound, pitch shift or time stretch is added. Exact filters, gains, sample counts, decoded peak/RMS, encoder versions and output hashes are in [the manifest](manifest.json).

`ambient.wind_phase_one` selects these two variants. It uses the existing 40-second rainy gust envelope and Ambient volume category. Other weather sound families retain their existing recordings. Family switches stop the prior clip; clear phase 1 stops the wind. Sources, recipes and evidence remain in development files; only processed OGGs and the normal sound definitions enter the runtime assets.

CC0 does not require attribution. Voluntary credit to BudJillett and Freesound is retained here and in AUDIO_NOTICE.md. The third-party recording and derivatives remain CC0-derived; the mod's code license does not relicense the source recording. A raw source-page HTML snapshot is private ignored evidence; tracked snapshots omit forms, scripts and anonymous session tokens.

The source recording was liked by the owner. Numeric checks cover duration, stream encoding and added digital clipping; they do not establish an audible clean loop, absence of source microphone distortion/wildlife, or sufficient contrast against vanilla rain. Those remain part of the same-world listening check.

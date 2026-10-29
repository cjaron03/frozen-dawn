# Phase 2 cold wind

[winter wind 01a.aiff by klangfabrik](https://freesound.org/people/klangfabrik/sounds/117504/) is individually listed as [CC0 1.0 Universal](https://creativecommons.org/publicdomain/zero/1.0/), verified on 2026-10-20 before retrieval/processing. The creator describes variable cold wind with a slight whistle, recorded near a partially open window. Owner accepted in-world audibility, improved shelter attenuation, continuous playback follow-up, phase cutoff and save/reload. The initial brief perceived silence remains documented in the playtest handoff. Exact source and CC0 deed rechecked on 2026-10-21.

Retained unchanged official HQ MP3 preview, explicitly not the account-gated AIFF original. Source URL, creator, encoding distinction, retrieval time, hash and sanitized individual license evidence are in source-ledger.json and licenses/. CC0 requires no attribution; voluntary credit remains here and in AUDIO_NOTICE.md. licenseTextType identifies whether the saved official text is complete or a summary with canonical links.

Reproduce with `python3 -B tools/audio_sources/phase_two_wind/generate_wind.py`. Two 54-second excerpts start at 30s and 210s, receive mono conversion, light 80Hz/6kHz filtering and two-pass normalization/limiting, then encode to mono 24kHz Vorbis at original pitch. Runtime five-second equal-power overlaps supply edge fades; none are baked into the OGGs. manifest.json retains commands, levels, media, tool versions and source/output hashes. No synthesis or time stretch. Only processed OGGs and sound definitions ship in the jar.

| Candidate | Intended use | Verified rights | Decision |
| --- | --- | --- | --- |
| klangfabrik winter wind 01a | Slightly whistling cold wind, long-source variants | Exact individual CC0 anchor verified | Selected; owner listening checks accepted |
| wrinex wind MS | Open-field alternate | Individual page CC0 verified | Research only, not downloaded or shipped |
| dhallcomposer open desert wind | Sparse alternate | Search result CC0; exact page timed out | Not adopted; synthesized mouth/reverb differs from selected field recording |

License/media facts are verified and owner listening feedback is retained in `docs/phase2-wind-playtest.md`. Multiplayer listening remains unverified. No new dependency or another project's code reused.

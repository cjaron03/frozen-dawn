# Recorded cyclone wind sources

Retrieved and individually verified on 2026-10-19 UTC. All four retained source pages link to CC0 1.0 Universal: https://creativecommons.org/publicdomain/zero/1.0/. Public high-quality MP3 previews are retained byte-for-byte; these are not the original WAV masters. No login or private download was used.

| Creator and sound | Role | Verified license | Decision |
| --- | --- | --- | --- |
| [Sandy-Ogilvie — WNW Wind and Snow on Holehead Hill](https://freesound.org/people/Sandy-Ogilvie/sounds/849302/) | Deep outdoor roar and occasional gusts | CC0 1.0; individual page snapshot in `licenses/849302.txt` | Adopt with retained source and processing recipe |
| [juryduty — howling_wind.wav](https://freesound.org/people/juryduty/sounds/185070/) | Rejected first-pass howling layer; retained reference only | CC0 1.0; individual page snapshot in `licenses/185070.txt` | Not used by current shipped recipes |
| [bruno.auzet — windy roomtone ile callot](https://freesound.org/people/bruno.auzet/sounds/837996/) | Sheltered storm bed | CC0 1.0; individual page snapshot in `licenses/837996.txt` | Adopt with retained source and processing recipe |
| [bruno.auzet — gusts on roof window](https://freesound.org/people/bruno.auzet/sounds/836283/) | Irregular sheltered wind buffeting | CC0 1.0; individual page snapshot in `licenses/836283.txt` | Adopt with retained source and processing recipe |

## Processing and playback

`generate_cyclone_audio.py` verifies each downloaded input hash, trims separate regions, filters excess rumble/high-frequency hiss, compresses the outdoor roar for a steadier bed, matches levels, and forms a four-second equal-power internal seam. It encodes eight mono 24 kHz, 64-second OGG variants with deterministic Vorbis serials. `manifest.json` records every input recipe, output hash and decoded audio measurement. Requires Python 3, FFmpeg, ffprobe and oggenc.

The outdoor roar and occasional gusts come from actual wind and snow on Holehead Hill. The roar is slowed to 72 percent speed and low-pass filtered near 1.1 kHz, with gentle compression to keep it continuous. The separate gust variants use two smooth 7–11 second rises per 64-second clip, with quieter intervals. Runtime emphasizes the roar and reduces the gust/hiss layer. The rejected juryduty howling layer remains as a retained reference and is absent from current output recipes. Sheltered rumble and irregular buffeting come from bruno.auzet’s indoor wind and roof-window recordings. No new synthesis, speech, thunder or music is mixed into these assets.

Runtime uses six-second equal-power clip overlaps and gradual changes to the ordinary/storm/shelter mix. Every overlapping voice remains tracked, including retiring voices; logout/dimension changes clear all voices. Ordinary Phase 5 and post-Maeve source files are preserved unchanged. Dedicated cyclone sounds are exempt from the generic cold pitch filter, because their room coloration and playback time are already controlled by the wind mixer.

## Source and license evidence

`source-ledger.json` records creator, exact source/download URLs, actual downloaded filename/format, retrieval date, input hash, role, modifications and credit obligations. Sanitized human-readable page snapshots and raw HTML hashes are in `licenses/`; raw HTML is diagnostic evidence under ignored `build/cyclone-evidence/audio-source-pages/` to avoid tracking site tokens or scripts.

CC0 requires no attribution; voluntary creator credits remain here and in `AUDIO_NOTICE.md`. CC0 applies to the source recordings and their derived audio. Frozen Dawn’s processing and implementation remain separately authored. No outside-mod asset or runtime dependency was adopted.

## Verification limits

Automated checks establish duration, channels, sample rate, decoded peak headroom, loop-boundary continuity, asset registration and gain-envelope behavior. They do not establish perceived timbre or subjective loudness. Owner rejected the first mix and requested a deep steady blizzard roar with occasional gusts. This second mix still needs owner audition in the preserved Bomb Cyclone Check save.


## Cyclone thundersnow reuse (2026-10-20)

The initial cyclone close/mid/distant sound events reused the existing original Master Architect thunder recordings. The owner accepted audibility/flashes but requested a different timbre. Current cyclone aliases use the selected two-source CC0 recorded mix documented in `tools/audio_sources/cyclone_thunder/SOURCES.md`. Original Master Architect audio and boss sound events remain unchanged.

# Phase 1 quiet-interval nature sound research

Checked individual primary-source pages and the canonical [CC0 1.0 deed](https://creativecommons.org/publicdomain/zero/1.0/) on 2026-10-20. These are audition candidates. Official HQ MP3 previews have since been retained and normalized for owner audition as recorded below; no nature recording is installed in the mod. Titles and source descriptions do not establish listening quality, clean loops or absence of extraneous sounds. Current phase 1 build and its accepted weather behavior remain the reviewed candidate.

| Candidate | Intended use | Verified rights | Decision |
| --- | --- | --- | --- |
| [Quiet forest ambience, some distant birds — fthgurdy](https://freesound.org/people/fthgurdy/sounds/528951/) | Sparse daylight woodland phrases; 10.109 seconds | Individual asset CC0 1.0 | First audition recommendation; retain silence between phrases |
| [Forest_Birds (loop) 02.wav — VKProduktion](https://freesound.org/people/VKProduktion/sounds/231537/) | Fuller early-phase daytime woodland; 30 seconds; creator describes loopability | Individual asset CC0 1.0 | Alternate audition; spring-tagged source should thin as cold advances |
| [sfx_amb_forest_winter_night.wav — bajko](https://freesound.org/people/bajko/sounds/378048/) | Calm winter-night texture; 50.626 seconds | Individual asset CC0 1.0 | Optional night audition; actual mix still needs listening |
| [Forest after winter — dasebr](https://freesound.org/people/dasebr/sounds/212697/) | Woodland tree drips; 179.5 seconds | Individual asset CC0 1.0 | Thaw-specific only; unsuitable as a general frozen-world bed |

Recommended original integration: phase 1 clear outdoor intervals, compatible woodland biomes, daytime bird phrases with randomized quiet gaps. Fade down through rain, shelter entry and phase changes. Reduce density through phase 1 so later silence retains meaning. Underwater, other dimensions and later phases should remain quiet. A night texture would be a separately reviewed choice. Treat recording species/season and local weather as design constraints; do not invent a water source or play a spring chorus everywhere.

Reuse would follow the existing wind pipeline: preserve exact original source/format, asset-page license evidence and canonical license, source hash/retrieval date, reproducible trim/encoding script, shipped inventory and visible voluntary creator credit before processing. Original WAV availability may require account login; if an official preview is selected instead, record its exact format and URL. No attribution is required under CC0, but project provenance and creator credit will be retained.

Unknowns before implementation: owner sound preference, actual recording contents, noise/voices/mechanical intrusions, acceptable gain and repetition in the same world. This research adds no gameplay behavior or new visual/multiplayer acceptance.

## Louder audition copies

Owner reported “i can like barely hear them.” Treated this as feedback on the linked nature previews because the lab client is closed and nature audio is not yet installed. The actual official preview files measured -57.55 LUFS (fthgurdy), -49.93 LUFS (VKProduktion) and -67.66 LUFS (bajko). Individual-page CC0 links and the canonical deed were rechecked before retrieval. Unchanged original MP3 previews, creator/license records, source hashes, a reproducible two-pass loudness script and output manifest are retained in `build/phase1-evidence/nature-auditions/`. They are explicitly official previews, not original WAVs.

Comparison WAVs measure -16.26, -16.04 and -16.09 LUFS respectively, with decoded true peaks no higher than -1.98 dBTP. These stereo PCM files are for audition, not the intended game gain. No looping, pitch shift, synthesis or time stretch was applied. Owner listening preference remains pending. Production source/resources and the completed final-gate candidate remain unchanged.

## Owner selection and first in-world candidate

Owner selected fthgurdy’s first audition and authorized adding it and checking the same world. It is now integrated as occasional phase-1 daytime birds in compatible outdoor biomes, with rain/shelter fades and increasing silent gaps as the phase advances. The recording provenance and generator are tracked under `tools/audio_sources/phase_one_nature/`. Other candidates remain uninstalled. Full build passed 878 unit tests plus packaging/fixture/facade and eight gate-script controls. Candidate `fafd2da2d5709417ab4dbcf2d0fc6d00289438b973f2f89fc2b609c6c7b4c381`. Current owner in-world listening is pending; latest handoff is `docs/phase1-playtest.md`. Earlier shortlist/audition-only descriptions above are historical.

## Current scope: phases 0–1, dry forests

Owner narrowed the feature to phases 0–1 after discussing phase 2’s permanent rain. All forest/taiga/jungle tag members plus cherry groves and windswept forests are supported; plains/meadows are excluded. New phrases require no rain flag and fully cleared rain/thunder interpolation. Active phrases fade out when wet conditions begin. Full build now passes 879 unit tests, with the existing recording and level unchanged. See the current playtest handoff and `nature-dry-forests/verification.json`. Owner audibility acceptance remains pending.

## Playback startup correction

Owner reported no audible birds in a forest. The initially silent sound instance had inherited `canStartSilent=false`, causing Minecraft to skip it before its fade-in could tick. Added a nature-only sound subclass permitting silent starts. Full build now passes 881 unit tests, including actual sound-instance admission/fade/completion regressions. The source recording, level, forest/weather scope, shared wind and all resources are unchanged. Current evidence is `build/phase1-evidence/nature-silent-start/`; same-world owner audibility is pending.

## Current owner direction: replace rejected birds with soft wind

After the silent-start correction the owner rejected the bird sound character and asked to find soft wind. The earlier bird selection is superseded. Gentle CC0 wind auditions are being prepared in `build/phase1-evidence/soft-wind-auditions/`; a replacement is not yet selected or installed. Current running bird candidate must not be treated as accepted shipping content. Existing phase/weather/forest rules are retained until the replacement design is agreed.

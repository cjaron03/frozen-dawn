# Environment integration — October 12, 2026

Owner authorized three checked PR merges into feat/maeve-director, in order: atmospheric breach, thermal2.0, room O2/Core allocation. Work is isolated in environment-integration; original feature branches, root Scribe/power WIP and saved worlds remain untouched. Source branch heads and file-blob manifests plus copied unrelated WIP are retained in /private/tmp/fd-environment-integration-20261012/baseline.json and before-wip/.

## Stage 1: atmospheric breach and airlocks

Base9e31385 includes Scribe and Blind Architect. Incoming8303073 includes its Ration Warmer predecessor, vacuum combustion, pressure breach/recovery, retained spent lights, airlocks and accepted silent suit feedback. Keep full source ancestry through merge commits. Conflicts in build generators, required cases, bridge policy, language, sound definitions and mixins retain both sides; duplicate quick-play variable removed while both guest configurations stay. Exclusive one-sided changed paths are compared to the source Git blobs before delivery; shared paths receive explicit reconciliation. Native and seeded stress gates run on the final PR head, with logs/XML/fingerprints under build/environment-integration-evidence/stage1. Local source/target proof and post-merge ancestry/tree verification are retained alongside the baseline.

No client launch, saved-world preparation/reset or unrelated worktree edit is part of integration. Local accepted feature playtests remain human evidence; headless combined checks do not claim fresh rendering or guest acceptance. Branch protection requires architectVerify and architectMonkey; publish only truthful statuses for completed current-head runs and use ordinary protected PR merge. Never force-push or delete source branches.


## Stage 1 completed

PR107 merged as a885973 after exact-head23d5908 passed731 unit,377 native/all368 required, and877 combined stress/all500 seeded cases. Merged tree equals the tested PR tree; old target9e31385 and source8303073 are both ancestors. Source fingerprintd0d1eec8d0b43c4a49bc38357fa6d6a0f76a7600d74bfc0323183daaee935dab; jarbcb24f62ff77e029048642dab3da93789eda8039eac17d16d150f4d3d31b81c0. Both branch protection statuses were published only after positive artifacts; no bypass was used. Original branch heads remain unchanged.

## Stage 2: thermal2.0

Based on the actual PR107 merge, incoming c667abf adds depth cooling, canonical room activity/geometry, conserved air-and-wall heat, target throttling, Core-aware thermostats, airtight heat vents/grille, machinery sounds and related manual/replay work. Preserve the already integrated breach/airlock behavior and current Blind/Scribe registrations. Conflicts in ModSounds and sounds.json retain both Blind Architect cues and all airlock machinery cues. Exclusive one-sided paths are blob-compared before committing; shared changes are reviewed explicitly. Both protected gates run on the final stage2 PR head, with independent immutable logs/XML/traces under build/environment-integration-evidence/stage2. Human feature acceptance is inherited from its original replay; fresh combined rendering/network play is not claimed.

# The descent checkpoint

## Slice 1, the pieces: 2026-11-23

Owning worktree `/Volumes/SSD/Work/frozen-dawn-the-descent`, branch `feat/the-descent`, cut from `feat/maeve-director` at c4b155c0. Merged into `feat/maeve-director` as PR 185 (46d56c02).

Built, creative tab and `/give` only, with no recipe, loot table or world generation:
- `descent/DescentEra`: the house and the eight eras, top to bottom, each with its standing count, clip and clip length.
- Ten sounds under `sounds/block/descent/` and `sounds/ambient/descent/`, volume 0.6, heard to 8 blocks. Provenance in `tools/audio_sources/descent/SOURCES.md`.
- Nine instruments (`DescentInstrumentBlock`), each playing its clip once and then silent for the clip's length.
- Nine marks (`DescentMarkBlock`), each with a bar 10 px wide.
- Eleven painting variants (`painting_variant/descent/`), none placeable in survival. Art provenance in `tools/art_sources/descent/SOURCES.md`.
- `/fd debug descent play <clip|icicle>`, development builds only.

Verification:
- Unit tests: `DescentEraTest` and `DescentPaintingTest`.
- GameTests in `DescentGameTest`: `anInstrumentPlaysOnce`, `aReplacedInstrumentWaitsForItsClip`, `instrumentsAreNotNoteBlocks` and `everyDescentPaintingLoads`.
- Gates on the pre-review head (8286094b): `architectVerify` ran 1427 tests and `architectMonkey` 1927, both passing. Both were run again on the final head before the PR.

Owner's visual pass in "Descent Pieces Check", prepared with `python3 tools/prepare_the_descent_playtest.py --source <closed level.dat>`. The save is in this worktree's `run-lab/saves` and is git-ignored. The owner's account: "i like it!"

The Opus branch review found one Important issue: breaking and replacing a playing instrument stacked the melody. It is fixed, with a failing GameTest first.

Deferred minors, worth doing before slice 2 places templates:
- An instrument saved as `playing=true` with no scheduled tick stays silent.
- No rotate or mirror overrides.
- The cooldown has zero tick margin.
- The painting test reads only the mod's own tag files.
- A playtest comment contradicts its chat labels.

For the owner's CH-10 check on their art:
- The Glacial Maximum wall's tenth standing figure is bent or rising.
- The Four Kiloyear lyres read close to a "Y" when zoomed.
- The chamber zero scratches read close to "k" and "V".

Not in slice 1: the chambers, the archivist, the 2077 slot, chamber zero's ambience and `To You, 200,000 Years Ago` (slice 2); world generation (slice 3); the founder's Echo (slice 4). The generator `thoughts/melody/synth.py` is only in the owner's project files.

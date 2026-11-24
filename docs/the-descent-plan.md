# The Descent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task, inline. The owner avoids subagents, except the one branch review at the end. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the descent (CH-44 to CH-47), one slice at a time. Going down is going back in time: ten eras of the same melody, the same motif and the same blocked mark, counting down to the death-screen line, with chamber zero below the count. This plan builds slice 1, the pieces, creative-only. Slices 2 to 4 are outlined and each gets its own approved plan before it is cut.

**Architecture:** Slice 1 is assets and simple blocks:
- **Sounds.** The owner-approved prototype clips become the era instruments' sounds.
- **Instruments.** One block per era that plays its clip once when used.
- **Marks.** One redaction block per era, in that era's material.
- **Motifs.** Vanilla painting variants that never place themselves in survival.

Everything sits in the creative tab. Nothing generates, drops or crafts.

**Tech Stack:** Java 21, NeoForge 1.21.1, JUnit in `src/test`, GameTests in `src/gameTest`.

**Spec:** [payoff-roadmap.md](payoff-roadmap.md), branch 9. Notion: the payoff branch spec, section 6f and chamber zero. Canon: Canon Hierarchy CH-44 to CH-47 (signed off 2026-11-23), with CH-10, CH-11, CH-14, CH-18, CH-21, CH-28 and CH-32. The canon's design truth stays on Notion; this plan only says what is built.

**Status:** approved by the owner as written, 2026-11-23, Rulings 1 to 7 included. Nothing is built.

**Branch:** slice 1 is `feat/the-descent` in `/Volumes/SSD/Work/frozen-dawn-the-descent`, cut from `feat/maeve-director` once this plan has merged. The build session starts in that worktree and works there. PR into `feat/maeve-director`. The branch review runs on Opus, because the branch merges into a long-lived branch. Later slices get their own branch and worktree (`feat/the-descent-chambers`, `feat/the-descent-worldgen`, `feat/the-descent-echo`).

## Slices

1. **The pieces (this plan).** Creative tab and `/give` only:
   - the era sounds;
   - eight instrument blocks and the 1816 music box;
   - nine redaction blocks;
   - nine motif paintings;
   - chamber zero's scratches;
   - the portrait.
2. **The chambers.** Structure templates for the hidden-division site with its archivist, the house, the eight chambers, the cave and chamber zero, placed by a dev command into a test world. It also builds:
   - the 2077 slot (the CH-18 line, empty in an E11 world through the existing `PayoffState.maeveEscaped` hook);
   - chamber zero's drip and wind ambience;
   - `To You, 200,000 Years Ago`.
3. **World generation.** One descent per world, rare and deep, placed so it never breaks an existing save.
4. **The founder's Echo.** Among the Heart's Echoes, the oldest and deepest silhouette (CH-42).

`To You, 200,000 Years From Now` and E11's escape cue wait for E11 and are not in any slice.

## Global Constraints

- **No text in the descent** except, in slice 2, the archivist's blocked badge and the 2077 slot's CH-18 line, both from existing or new lang keys. In slice 1 that means:
  - no plaques, dates or names on any block, painting or tooltip;
  - item names are plain descriptions;
  - tooltips carry no lore.
- **Blocks only, never letters** (CH-10, CH-11). Every redaction is a mark exactly 10 characters wide in its era's material; no block or texture spells or hints at a letter.
- **The count** (CH-44). Each motif shows a fixed number of standing figures:

  | Era | Standing |
  |---|---|
  | Last Glacial Maximum | 10 |
  | Younger Dryas | 9 |
  | 8.2-kiloyear event | 8 |
  | 4.2-kiloyear event | 7 |
  | Bronze Age collapse | 6 |
  | 536 CE | 5 |
  | Great Famine | 4 |
  | Maunder Minimum | 3 |
  | 1816 (the sampler) | 2 |

  Chamber zero's scratches show one figure, lying flat and standing, and carry no number (CH-45).
- **No presence** (CH-14, CH-32). No motif shows anything above the figures: no light, no watcher, nothing "on the other side". Nothing implies a figure is the player.
- **Quiet** (CH-46). Every instrument is heard only up close and never over the room. The 2077 respawn ring is not touched.
- **Creative-only** (AGENTS.md). No recipe, no loot, no world generation, no random painting placement in survival.
- **Rules for every branch.** Never edit MACS. Never open the Notion page "SEALED: Sequel Seed". Cite canon by ID, and keep Notion-only notes out of code, comments, commits and the PR. New Java names, keys and files use "descent", "era" or the era's name, never "continuity".

## Rulings

1. **The eras.** New `descent/DescentEra` enum, top to bottom:

   | Era | Instrument | Mark material |
   |---|---|---|
   | `MAUNDER` | church bell | painted panel |
   | `FAMINE` | monastery bell | dark stained-glass pane |
   | `LATE_ANTIQUE` (536) | lyre | mosaic tile |
   | `BRONZE_AGE` | bronze lur | bronze inlay |
   | `FOUR_KILOYEAR` | bull-headed lyre | clay |
   | `EIGHT_KILOYEAR` | drum and clay whistle | painted plaster |
   | `YOUNGER_DRYAS` | stone whistle | carved stone |
   | `GLACIAL_MAXIMUM` | bone flute | ochre smear |

   `HOUSE_1816` (music box; mark in 1816 ink) sits above them all. Each era also holds its standing count and its sound event. Chamber zero is not an era: it has no count, no block and no instrument.
2. **The sounds.** The owner's approved prototype ("The Descent Melody" artifact) is the first in-game version.
   - The ten clips are fetched from that artifact:
     - the eight `audio/1_` to `audio/8_` era files;
     - `audio/1816_musicbox.mp3`;
     - `audio/0_chamberzero_icicle.mp3`.
   - About 1.7 MB in all. The owner's approval of this plan covers fetching exactly these ten files.
   - Each clip is converted to mono 44.1 kHz Ogg (`oggenc -q 5`) under `sounds/block/descent/`, keeping its own tempo, room and the uneven slowing the owner kept (CH-46).
   - Sound events: `block.descent.<era>` for the eight eras, `block.descent.music_box` for `HOUSE_1816`, and `ambient.descent.icicle`. Category `blocks`, volume 0.6, `attenuation_distance` 8, no subtitle text beyond a plain one per instrument ("A bell rings", "A bone flute plays" and the like), not streamed.
   - The icicle clip ships in slice 1 for the sound sign-off and is played only by a dev command until slice 2 places it.
   - `tools/audio_sources/descent/SOURCES.md` records the provenance:
     - the clips are synthesized from the respawn ring's tones;
     - the generator is `thoughts/melody/synth.py` in the owner's project files;
     - the icicle clip's wind comes from two CC0 recordings, "Winter Snowstorm Ambience" by HECKFRICKER (freesound 754256) and "strong wind on coastal path" by bruno.auzet (freesound 706471).
   - `tools/build_audio_inventory.py` gets a `block/descent/` and `ambient/descent/` class; fix its crash on the two uncategorized `block/cs1_dock/` files at the same time, so the inventory can be rebuilt.
3. **The instruments.** One block per era plus the music box:
   - `DescentInstrumentBlock` with a horizontal facing, no block entity, and a 16-pixel blocky model in that era's materials.
   - Using it plays its era's clip once at the block. It then stays silent until the clip's length has passed, tracked on the server per block position, so a player can never stack the melody.
   - It is never a redstone or note-block instrument.
   - Item name only, for example `Bone Flute`, `Music Box`.
4. **The marks.** `DescentMarkBlock`, one per era plus the 1816 ink, each a full block whose face shows a single bar exactly 10 characters wide of the era's material on that era's ground (for example an ochre smear on cave stone, a dark pane in leaded glass). Names: `Ochre Mark`, `Carved Mark`, `Clay Mark` and so on. No tooltip.
5. **The motifs.** Data-driven painting variants under `data/frozendawn/painting_variant/`, one per era (nine, the 1816 one a sampler), plus `chamber_zero_scratches` and `portrait_1816`:
   - Sizes are set in the mockup.
   - No variant is in `minecraft:placeable`, so a survival painting never rolls one.
   - The creative tab offers each as a painting stack with its variant set.
   - Their `painting.frozendawn.<id>.title` and `.author` lang values are empty strings (no names, no dates).
6. **The creative tab.** A `Descent` section at the end of the main tab, ordered top to bottom as the descent runs: music box, the eight instruments, the marks, the motifs, the scratches, the portrait.
7. **Dev command.** `/fd debug descent play <era|music_box|icicle>` plays a clip at the caller, for the sound sign-off. Dev builds only, as `/fd payoff` is.

## Review Focus

1. **The count.** A motif that shows the wrong number of standing figures breaks the whole descent. Task 1's unit test pins the counts per era. The owner checks each painting in the mockup and the pass.
2. **A letter in a mark.** Any mark or painting that reads as a letter breaks CH-10. The owner checks the mockups; no texture carries glyphs.
3. **Stacking the melody.** Spamming an instrument must never overlap the clip. Task 2's GameTest holds it.
4. **A survival painting.** A painting placed in survival must never roll a descent variant. Task 3's unit test holds the tag.
5. **Loudness.** The instruments are quiet and short-range, never over the room. The owner hears them in the pass, as in Ruling 2's levels.

---

### Task 0: Mockups and the sound sign-off

- [ ] **Step 1: Fetch** the ten clips named in Ruling 2 from the artifact into the scratchpad, convert them, and play them for the owner in descent order at Ruling 2's level, ending with the icicle. Record any change the owner asks for in Ruling 2.
- [ ] **Step 2: Draw the mockups** at 16 pixels per block, Minecraft style, nothing smoothed, in three sheets:
  - the eight instruments and the music box, as items and placed;
  - the eight marks and the 1816 ink;
  - the nine motifs, the scratches and the portrait at their painting sizes, each with its standing count labelled under it in the mockup only.
- [ ] **Step 3: Show them to the owner.** Each task's assets wait for its sheet's approval; code and tests don't.

### Task 1: Eras and sounds

**Files:**
- Create:
  - `src/main/java/com/frozendawn/descent/DescentEra.java`
  - `src/test/java/com/frozendawn/descent/DescentEraTest.java`
  - `sounds/block/descent/*.ogg`
  - `tools/audio_sources/descent/SOURCES.md`
- Modify: `init/ModSounds.java`, `assets/frozendawn/sounds.json`, `tools/build_audio_inventory.py`, `docs/audio/SHIPPED_AUDIO_INVENTORY.tsv`, the debug command class and `FrozenDawnCommandTreeTest`

**Interfaces:**
- Produces:
  - `DescentEra.values()` in descent order
  - `DescentEra.standing(): int`
  - `DescentEra.sound(): Holder<SoundEvent>`
  - `DescentEra.clipTicks(): int`

- [ ] **Step 1: Write the failing unit tests** in `DescentEraTest`:
  - `theCountFallsGoingUp`: standing counts are 3, 4, 5, 6, 7, 8, 9, 10 from `MAUNDER` to `GLACIAL_MAXIMUM`, and `HOUSE_1816` is 2.
  - `eachEraHasItsOwnQuietSound`: every era names a distinct `sounds.json` event with `attenuation_distance` 8, not streamed, under `block/descent/`.
  - `noDescentTextSaysMore`: no descent lang value contains a year, a name or "continuity", ignoring case.
- [ ] **Step 2: Run** them. Expected: FAIL.
- [ ] **Step 3: Implement** Rulings 1, 2 and 7.
- [ ] **Step 4: Run** them, and `volumedetect` on each Ogg. Expected: PASS, with no clip peaking above -6 dB.
- [ ] **Step 5: Commit** `feat: add the descent era sounds`.

### Task 2: Instruments and marks

**Files:**
- Create: `descent/DescentInstrumentBlock.java`, `descent/DescentMarkBlock.java`, their blockstates, models and textures, `src/gameTest/java/com/frozendawn/descent/DescentGameTest.java`
- Modify: `init/ModBlocks.java`, `init/ModItems.java` (block items, creative section), `assets/frozendawn/lang/en_us.json`

- [ ] **Step 1: Write the failing GameTests** in `DescentGameTest`:
  - `anInstrumentPlaysOnce`: using a bone flute twice inside its clip length plays one clip; using it again after the clip plays a second.
  - `instrumentsAreNotNoteBlocks`: powering an instrument plays nothing.
- [ ] **Step 2: Run** `./gradlew gameTestQuick -PfdTests=aninstrumentplaysonce,instrumentsarenotnoteblocks`. Expected: FAIL. (`-PfdTests` matches method names.)
- [ ] **Step 3: Implement** Rulings 3, 4 and 6, with the approved textures.
- [ ] **Step 4: Run** the GameTests. Expected: PASS.
- [ ] **Step 5: Commit** `feat: add the descent instruments and marks to the creative tab`.

### Task 3: Motifs

**Files:**
- Create: `data/frozendawn/painting_variant/*.json`, `textures/painting/descent/*.png`, `src/test/java/com/frozendawn/descent/DescentPaintingTest.java`
- Modify: `init/ModItems.java` (creative section)

- [ ] **Step 1: Write the failing unit test** `noDescentPaintingRollsInSurvival`: no file under `painting_variant/` is listed in any `minecraft:placeable` tag the mod ships, and each variant's title and author keys are empty.
- [ ] **Step 2: Run** it. Expected: FAIL.
- [ ] **Step 3: Implement** Ruling 5 with the approved art.
- [ ] **Step 4: Run** it. Expected: PASS.
- [ ] **Step 5: Commit** `feat: add the descent motifs as creative paintings`.

### Task 4: Playtest world, gate and handback

**Files:**
- Create: `tools/prepare_the_descent_playtest.py`
- Modify: `docs/payoff-roadmap.md` (branch 9 line)

- [ ] **Step 1: Write the prep script.** Save "Descent Pieces Check": flat, creative, advancement popups off and difficulty preselected, as AGENTS.md requires. Lay the eras out as a top-to-bottom row of bays, each with its instrument, mark and motif, so the owner can walk the descent in order. Add buttons for the Ruling 7 command.
- [ ] **Step 2: Run** `./gradlew architectVerify` in the background. Expected: BUILD SUCCESSFUL. Then run `./gradlew architectMonkey`, with the same expectation.
- [ ] **Step 3: Launch** with `python3 tools/prepare_the_descent_playtest.py`, then `./gradlew runClientLab -PfdLabWorld='Descent Pieces Check' --console=plain` in the background. The owner walks the row. They check:
  - each instrument's sound and how far it carries;
  - that the melody can't be stacked;
  - the marks and motifs, and the count on each motif.
- [ ] **Step 4: Dispatch the branch review** (Opus, see **Branch**) and fix real findings, each with a failing test first. Update the roadmap's branch 9 line and the checkpoint, then commit, run both gates on the final head and offer the PR. Slice 2's plan is written next, in the planning worktree.

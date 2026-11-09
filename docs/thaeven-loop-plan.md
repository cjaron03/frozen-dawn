# Thaeven Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task, inline. The owner avoids subagents, except the one branch review at the end. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build CH-37's loop. Record III's raw lines flicker once in the opening blackout. Record I gains a page that translates them, ending `Source: unresolved.`, and that line re-inks to `Source: the warm-one.` once the payoff has fired. During the Heart, the hive's overlay plays the English lines live, at four fixed beats, once each per world.

**Architecture:** There are three carriers:

- **The opening.** A render-only change in `OrsaAwakeningIntro`.
- **Record I.** A resource page, plus a second semantic key on the existing Thaeven ink lock, which the payoff unlocks.
- **The Heart.** A small server manager that records which beats have shown in `ThaevenLoreSavedData`, and sends one payload that a client overlay draws in the existing Thaeven band style.

**Tech Stack:** Java 21, NeoForge 1.21.1, JUnit in `src/test`, GameTests in `src/gameTest`.

**Spec:** [payoff-roadmap.md](payoff-roadmap.md), branch 6. Notion: the payoff branch spec, section 6e, beats 1 to 4. Canon: Canon Hierarchy CH-37, with CH-11, CH-14, CH-28 and CH-35.

**Status:** draft for the owner's approval, 2026-11-09. Nothing is built.

**Branch:** `feat/thaeven-loop` in `/Volumes/SSD/Work/frozen-dawn-thaeven-loop`, cut from `feat/maeve-director` once this plan has merged. The build session starts in that worktree and works there. PR into `feat/maeve-director`. The branch review runs on Opus, because the branch changes a save format (`ThaevenLoreSavedData`) and client/server sync (`ThaevenLoreSyncPayload`), and it merges into a long-lived branch.

## Global Constraints

- **Canon strings go in word for word.** These are the raw lines, Record I's five translated lines, the re-inked last line and the four Heart beats, as Rulings 1 to 4 give them. Every line already exists in `pattern_residue.json` (Record III), except `Source: unresolved.` and `Source: the warm-one.`. No new lexicon.
- **What the lines must not say.** No line says how or when (CH-28). None uses "you", and none uses CONTINUITY (CH-11).
- **Where the lines must not go.** None of them goes into Record V (`the_first_crossing.json`) or near Maeve's founding memory. The loop points at the warm-one, never at "something on the other side" (CH-14, CH-37).
- **Record III stays as it is.** Beat 4 needs no code: `pattern_residue.json` and its assembly are unchanged.
- **No sound of its own.** The Phase 0 flicker has none, and neither do the Heart beats. The Heart beats are overlay only, with no new screen.
- **Rules for every branch.** Never edit MACS. Never open the Notion page "SEALED: Sequel Seed". Cite canon by ID, and keep Notion-only notes out of code, comments, commits and the PR. New Java names, keys and files use "loop", never "continuity".

## Rulings

1. **Beat 1, the Phase 0 flicker.**
   - **The text.** New lang key `screen.frozendawn.awakening.raw` = `Vel-thae thaeven.`, drawn twice on two lines.
   - **When it shows.** Only in the opening, never in the replay or the boot cue. It uses the 40-tick black lead-in, the same window and the same three dropout frames as the replay's CH-18 flicker: `pendingDelayTicks` 30 to 11, about one second.
   - **How it looks.** Centered, italic, in the Thaeven overlay's text white (`0xE7F7F7`), with no `THAE IVEN` label and no band. The screen is already black.
   - **Who sees it.** It rides the opening, so every player's first awakening shows it once. A dev client that skips the opening skips it too; `/fd payoff opening` plays it.
   - **Testability.** Add a pure `static boolean rawLinesShown(boolean replay, int pendingDelayTicks)` so it can be tested.
2. **Beat 2, the Record I page.** `vel_an.json` gains a sixth page, appended after "You are not alone.", so `Source: unresolved.` is the record's last line.
   - **Raw page:** `Vel-thae thaeven.\nVel-thae thaeven.\nThae ethven.`
   - **Translated segment:** id `vel_an_06`, `revealTiming` 58, `uncertainty` true, `baseText` `The warm-one crossed here.\nThen crossed here again.\nThe body broke.\nThe returning was not.\nSource: unresolved.`
   - **The re-ink:** `semanticOverrides` `{"1": ...}` is the same five lines, except the last reads `Source: the warm-one.`.
   - **Old saves:** worlds from before this branch get the page too. It's a resource, and Record I is found before the Heart.
3. **The re-ink.**
   - **The key.** Append `PAYOFF_SOURCE` to `ThaevenSemanticKey`. Saved data keys it by name, so appending is safe. `ThaevenLoreSavedData.currentRevision(VEL_AN)` returns `semanticRevision(PAYOFF_SOURCE)`.
   - **The trigger.** When `PayoffState.onDeath` fires the payoff, it calls `ThaevenLoreManager.unlockSemantic(server, PAYOFF_SOURCE)`. An older world that never fires the payoff never re-inks.
   - **The sync.** `ThaevenLoreSyncPayload` and `ArchiveSnapshot` replace `int architectLidRevision` with `int[] currentRevisions`, indexed by record ordinal. `ThaevenLoreClientState.currentRevision(record)` reads that array, and `ThaevenLoreCommand`'s status lines read it too.
   - **The archive screen.**
     - It re-inks only the pages whose text changed: `ThaevenRecordDefinition.changedPages(int seen, int current)` lists the logical pages whose resolved text differs between the two revisions. A first view (`seen == -1`) keeps today's reveal of every page.
     - While a revision is pending, the screen opens on the first changed page, so the player watches the line change.
     - This also changes Book 2 (The Passage). Its Architect lid reveal will re-ink only page 8, its only changed page, instead of all eight.
4. **Beat 3, the Heart.** New `ThaevenLoopBeat` enum: `CROSSED`, `CROSSED_AGAIN`, `BODY_BROKE`, `RETURNING`. Lang keys:

   | Key | Text |
   |---|---|
   | `overlay.frozendawn.thaeven.loop.crossed` | `The warm-one crossed here.` |
   | `.crossed_again` | `Then crossed here again.` |
   | `.body_broke` | `The body broke.` |
   | `.pieces_remained` | `The pieces remained.` |
   | `.returning` | `The returning was not.` |

   Each beat shows once per world, only to the player who triggered it:
   - `CROSSED` on the world's first takeover start (load reaches 100%). `CROSSED_AGAIN` on the second.
   - `BODY_BROKE` follows the world's first player death inside the Heart arena. The arena counts while the major Hearth's Heart is live, in its dimension, within `HeartScavengerWaveManager.ARENA_RADIUS` (112 blocks, made public) of its anchor. The line shows on that player's next respawn, not on the death screen, so CH-18's screen stays clean. `The pieces remained.` follows 20 ticks later.
   - `RETURNING` on that same player's first re-entry into the arena after the respawn, while the Heart is still live. If the Heart falls first, it never shows.
5. **The beat overlay.**
   - **The payload.** `ThaevenLoopPayload(int beat)`, sent server to client.
   - **How it looks.** `ThaevenLoopOverlay` draws the Master Architect fourth-wall moment's band: a dark band at 0.68 of the screen height, the `THAE IVEN` label in its aqua, and the line in italic white. It holds 80 ticks, with 10-tick fades.
   - **Waiting its turn.** It waits while `OrsaAwakeningIntro` runs the opening, the replay or the boot cue, so `BODY_BROKE` lands after the boot cue's blink.
6. **The world state.**
   - **New fields.** `ThaevenLoreSavedData` gains `loopShown` (a bitmask over `ThaevenLoopBeat`), `loopBrokenOwed` (a UUID, or none) and `loopReturningOwed` (a UUID, or none). Absent tags read as nothing shown and nothing owed.
   - **The manager.** New `com.frozendawn.lore.ThaevenLoopManager` owns the triggers:
     - `onTakeoverStart(ServerPlayer)`, called where `CognitiveLoadManager.tickPlayer` raises `EVENT_TAKEOVER_START`.
     - A `LivingDeathEvent` handler at lowest priority, which ignores a cancelled event.
     - A `PlayerRespawnEvent` handler, which ignores the End exit.
     - `tick(ServerPlayer, BlockPos anchor)`, called from `CognitiveLoadManager.tick`'s player loop while the Heart is live.
   - **The pure policy.** The decisions live in `ThaevenLoopPolicy`, with no side effects: `takeoverBeat(int shownMask)` returns the next beat or none, and `inArena(double distanceSq, boolean heartLive, boolean sameDimension)`.
7. **The dev command.** `/fd lore loop status` and `/fd lore loop reset confirm` (resets the three fields) join `ThaevenLoreCommand`. `FrozenDawnCommandTreeTest` pins the new subcommands.

## Review Focus

1. **An older world.** A save from before branch 3 never fires the payoff, so its Record I must never re-ink. Its page 6 still reads `Source: unresolved.`. Task 1's GameTest holds it.
2. **Record I found after the payoff.** A player who first opens Record I after the payoff fired must see `Source: the warm-one.` with the first-view reveal, never `unresolved` first. Task 1's unit test holds `changedPages(-1, 1)`.
3. **Book 2's lid reveal.** It must still re-ink page 8, and now only page 8. Task 1's unit test holds `changedPages(0, 1)` for The Passage as `[7]`.
4. **The second player.** A beat shown to one player never shows again in that world, and only the player who died can be owed `RETURNING`. Task 3's GameTest holds both.
5. **The Heart falls mid-chain.** A player owed `RETURNING` when the Heart falls must never see it, and no beat may show in an erased world. Task 3's GameTest holds it.

---

### Task 0: Mockups

- [ ] **Step 1: Draw two frames at Minecraft GUI scale** (16 px per block, the Minecraft font, nothing smoothed):
  - The opening's black lead-in with the two raw lines (Ruling 1).
  - A Heart takeover frame with the Cognitive Load bar, the resistance bar and the beat band (Ruling 5).
- [ ] **Step 2: Show them to the owner.** Tasks 2 and 3 wait for approval; Task 1 doesn't.

### Task 1: Record I's page and the re-ink

**Files:**
- Create: `src/test/java/com/frozendawn/lore/ThaevenLoopTextTest.java`, `src/gameTest/java/com/frozendawn/lore/ThaevenLoopGameTest.java`
- Modify:
  - `assets/frozendawn/thaeven_records/vel_an.json`
  - `lore/ThaevenSemanticKey.java`
  - `data/ThaevenLoreSavedData.java` (`currentRevision`, `ArchiveSnapshot`)
  - `network/ThaevenLoreSyncPayload.java`
  - `lore/ThaevenLoreManager.java`
  - `client/ThaevenLoreClientState.java`
  - `client/ThaevenRecordDefinition.java` (`changedPages`)
  - `client/ThaevenLoreScreen.java`
  - `command/ThaevenLoreCommand.java`
  - `lore/PayoffState.java` (the unlock call)
  - `src/test/java/com/frozendawn/data/ThaevenLoreSavedDataTest.java`

**Interfaces:**
- Produces:
  - `ThaevenSemanticKey.PAYOFF_SOURCE`
  - `ThaevenRecordDefinition.changedPages(int seen, int current): List<Integer>`
  - `ThaevenLoreSyncPayload(long discoveredMask, boolean recipeDiscovered, int[] seenRevisions, int[] currentRevisions)`

- [ ] **Step 1: Write the failing unit tests.** In `ThaevenLoopTextTest`, read the JSON from `src/main/resources` with Gson:
  - `recordOneEndsOnTheLoopPage`: 6 raw and 6 translated pages. Page 6's raw text, base text and override `"1"` exactly as in Ruling 2, with `uncertainty` true.
  - `segmentResolvesTheReInk`: `resolve(0)` ends `Source: unresolved.`, and `resolve(1)` ends `Source: the warm-one.`.
  - `onlyTheChangedPageReInks`, built from the loaded JSON:
    - Record I: `changedPages(0, 1)` is `[5]`, and `changedPages(-1, 1)` is all six pages.
    - The Passage: `changedPages(0, 1)` is `[7]`.

  In `ThaevenLoreSavedDataTest`, add `payoffSourceReInksRecordOneOnly`:
  - `currentRevision(VEL_AN)` goes from 0 to 1 on `unlockSemantic(PAYOFF_SOURCE)`, and `THE_PASSAGE` stays 0.
  - `snapshot(...).currentRevisions()[VEL_AN]` is 1.
  - A save and load round trip keeps it.
- [ ] **Step 2: Run** `./gradlew test --tests 'com.frozendawn.lore.ThaevenLoopTextTest' --tests 'com.frozendawn.data.ThaevenLoreSavedDataTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** Rulings 2 and 3.
- [ ] **Step 4: Run** the same tests. Expected: PASS.
- [ ] **Step 5: Write the GameTests** in `ThaevenLoopGameTest`, batch `thaevenloop`. Fire the payoff through the qualifying-death path, as `PayoffGameTest` does, and restore the payoff flag, the saved erased flag and the world semantics afterwards.
  - `firingThePayoffReInksRecordOne`: before the death, `semanticRevision(PAYOFF_SOURCE)` is 0; after it, 1.
  - `anOlderWorldNeverReInks`: with `PayoffState.setEligibleForTest(server, false)`, the same death leaves it 0.
- [ ] **Step 6: Run** `./gradlew gameTestQuick -PfdTests=thaevenloop,payoff`. Expected: PASS.
- [ ] **Step 7: Commit** `feat: add the loop page to Record I and re-ink it after the payoff`, staging each file by name.

### Task 2: The Phase 0 flicker

**Files:**
- Create: `src/test/java/com/frozendawn/client/ThaevenLoopLookTest.java`
- Modify: `client/OrsaAwakeningIntro.java`, `assets/frozendawn/lang/en_us.json`

- [ ] **Step 1: Write the failing unit tests** in `ThaevenLoopLookTest`:
  - `rawLinesShowOnlyInTheOpeningLeadIn`: `rawLinesShown(false, t)` is true for `t` 11 to 30, except the dropout frames, and false at 31 and 10. `rawLinesShown(true, t)` is false for every `t`.
  - `rawLineIsRecordThree`: the lang value is exactly `Vel-thae thaeven.`, and it opens Record III's raw page 1.
- [ ] **Step 2: Run** `./gradlew test --tests 'com.frozendawn.client.ThaevenLoopLookTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** Ruling 1, with the look from Task 0's approved mockup.
- [ ] **Step 4: Run** the same tests and `PayoffLookTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat: flicker Record III's raw lines in the opening blackout`.

### Task 3: The Heart beats

**Files:**
- Create:
  - `src/main/java/com/frozendawn/lore/ThaevenLoopBeat.java`
  - `src/main/java/com/frozendawn/lore/ThaevenLoopPolicy.java`
  - `src/main/java/com/frozendawn/lore/ThaevenLoopManager.java`
  - `src/main/java/com/frozendawn/network/ThaevenLoopPayload.java`
  - `src/main/java/com/frozendawn/client/ThaevenLoopOverlay.java`
- Modify:
  - `homo/CognitiveLoadManager.java` (two calls)
  - `homo/HeartScavengerWaveManager.java` (`ARENA_RADIUS` public)
  - `data/ThaevenLoreSavedData.java` (Ruling 6 fields)
  - `network/ModNetworking.java`
  - `command/ThaevenLoreCommand.java`
  - `assets/frozendawn/lang/en_us.json`
  - `src/test/java/com/frozendawn/command/FrozenDawnCommandTreeTest.java`
  - Tests: `ThaevenLoopTextTest`, `ThaevenLoopGameTest`

**Interfaces:**
- Consumes: `ThaevenLoreSavedData` from Task 1.
- Produces:
  - `ThaevenLoopPolicy.takeoverBeat(int shownMask): Optional<ThaevenLoopBeat>`
  - `ThaevenLoopPolicy.inArena(double distanceSq, boolean heartLive, boolean sameDimension): boolean`
  - `ThaevenLoopManager.onTakeoverStart(ServerPlayer)`
  - `ThaevenLoopManager.tick(ServerPlayer, BlockPos)`

- [ ] **Step 1: Write the failing unit tests.**
  - `takeoverBeatsRunInOrderOnce`: an empty mask gives `CROSSED`, then `CROSSED_AGAIN`, then empty.
  - `arenaNeedsALiveHeartInItsDimension`: 112 blocks counts and 113 doesn't; a dead Heart or another dimension is false.
  - `heartLinesAreRecordThree`: each loop lang value is exact per Ruling 4, and each appears in Record III's translated text. The four that Record I page 6 shares also appear there.
- [ ] **Step 2: Run** them. Expected: FAIL.
- [ ] **Step 3: Implement** Rulings 4 to 7.
- [ ] **Step 4: Run** the unit tests. Expected: PASS.
- [ ] **Step 5: Write the GameTests** in `ThaevenLoopGameTest`. Use two mock players, A and B. Call the manager's entry points directly, with the arena given as a boolean through a package-private overload, so no live Heart is needed. Reset the loop fields before and after.
  - `takeoverBeatsShowOncePerWorld`: A's takeover marks `CROSSED`, B's marks `CROSSED_AGAIN`, and a third marks nothing.
  - `anArenaDeathOwesTheBrokenLineOnRespawn`: A dies in the arena, so A is owed `BODY_BROKE`. A's respawn sends it and owes A `RETURNING`. B's death in the arena then owes nothing.
  - `onlyTheDeadPlayerIsOwedTheReturn`: B entering the arena clears nothing. A entering shows `RETURNING` once.
  - `noBeatInAnErasedWorld`: with the saved erased flag set, takeover, death and entry all show nothing.
- [ ] **Step 6: Run** `./gradlew gameTestQuick -PfdTests=thaevenloop`. Expected: PASS.
- [ ] **Step 7: Commit** `feat: play the loop lines live during the Heart`.

### Task 4: Playtest world, gate and handback

**Files:**
- Create: `tools/prepare_thaeven_loop_playtest.py`
- Modify: `docs/payoff-roadmap.md` (branch 6 line)

- [ ] **Step 1: Write the prep script.** Save "Thaeven Loop Check": flat, survival, advancement popups off and difficulty preselected, as AGENTS.md requires. Give it chat buttons for:
  - Playing the opening (`/fd payoff opening`).
  - Granting Record I and a Thaeven Translator.
  - Firing the re-ink (`/fd lore unlock-semantic payoff_source`).
  - Starting a live Heart, the way `prepare_payoff_reveal_playtest.py` does.
  - `/fd lore loop status` and `/fd lore loop reset confirm`.
- [ ] **Step 2: Run** `./gradlew architectVerify` in the background. Expected: BUILD SUCCESSFUL. Then run `./gradlew architectMonkey`, with the same expectation.
- [ ] **Step 3: Launch** with `python3 tools/prepare_thaeven_loop_playtest.py`, then `./gradlew runClientLab -PfdLabWorld='Thaeven Loop Check' --console=plain` in the background. The owner checks:
  - The flicker in the opening's black, and that it reads as a glitch.
  - Record I's last page before the re-ink.
  - The re-ink, which runs on that page alone.
  - Book 2's lid reveal, which still works.
  - Two takeovers, which show the two crossing lines.
  - An arena death, which brings the broken lines after the boot cue.
  - Walking back in, which shows `The returning was not.`
- [ ] **Step 4: Dispatch the branch review** (Opus, see **Branch**) and fix real findings. Update the roadmap's branch 6 line, then commit and offer the PR.

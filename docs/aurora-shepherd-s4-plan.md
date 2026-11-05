# Aurora S4 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task, inline. The owner avoids subagents, except the one branch review on Opus at the end of each slice. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Her personal arc: T2 sends the player to Nine's husk at the Blast Pit, T3 takes her up to the stars, T4 tells the fear, and T5 tells her what she was, from the TITHONUS files. Bleed-through jokes run under all of it.

**Architecture:** Conversations stay data: nodes in `personal.json`, text in lang keys. `ShepherdPrompt` raises each one in turn, from a list that gains a readiness test per entry. Nine lives in one new class, `ShepherdNine`: the husk block, its placement, and what it hands over. What she feels and thinks stays in the Pattern, read through `MillerPattern` by new dialogue conditions. Only the lean is new state there.

**Tech Stack:** Java 21, NeoForge 1.21.1, JUnit in `src/test`, GameTests in `src/gameTest`.

**Spec:** [aurora-shepherd.md](aurora-shepherd.md), with [aurora-miller-pattern.md](aurora-miller-pattern.md) and [aurora-orsa-topics.md](aurora-orsa-topics.md). Notion: the Aurora page and Dialogue Script v1, Canon Hierarchy CH-29 to CH-32, and the Continuity Payoff branch spec.

**Lines:** [aurora-s4-lines.md](aurora-s4-lines.md), every line and file, for the owner to mark first.

**Mockups:** [nine-husk-mockup.png](art/aurora-shepherd/nine-husk-mockup.png), [nine-lamp-mockup.png](art/aurora-shepherd/nine-lamp-mockup.png).

**Status:** draft for the owner, 2026-11-05. T3's timing is decided; the rulings below are open. Nothing is built. Stop here for approval.

**Branch:** `feat/aurora-s4` in `/Volumes/SSD/Work/frozen-dawn-aurora-s4`, cut from `feat/maeve-director` at a0b77afa. S4b cuts `feat/aurora-s4b` after S4a merges. PR into `feat/maeve-director`.

**PR 155 has merged** (a0b77afa, 2026-11-05 15:41 UTC), so nothing here waits on it. `Attention`, `Urges` and `ShepherdWill` are on this branch.

## The one thing to decide first

**T3 cannot show stars until about day 94 of 120, while the spec has it start at phase 4, about day 41.**

Fix 5 says T3 waits out phase 5, early phase 6 and any storm. The code makes that nearly all of the game: `WeatherHandler` locks rain from phase 2 and thunder from phase 3 until phase 6 mid, and vanilla draws no stars under rain, so phase 4 has none. Phase 5 and early phase 6 draw no sky. From phase 6 mid (0.72, day 86) `renderPhase6Stars` fades them in, reaching half brightness at 0.785, about day 94. Vacuum starts at 0.85, about day 102.

| Option | What the player sees | Cost |
| --- | --- | --- |
| **A, recommended: she asks when the stars are there** | T3 opens with "The storms have stopped. I've been checking." The imagined-aurora pool carries phases 4 to 6, as built | Reverses "T3 starts at phase 4". The arc's second half lands late, and the climb is in thin air |
| B: she asks from phase 4 and waits | "Take me up", then 50 days when going up does nothing. It reads as a bug | Needs "not tonight" lines for the whole wait |
| C: a break in the storm | A rare clear night in phase 4 | Changes `WeatherHandler` and the permanent storms, and every system tuned to them |

**Decided: A, owner, 2026-11-05 ("When stars show").** She asks once the stars are there, not from phase 4. Fix 5's assumption stands: real auroras end at phase 3.

## Rulings for the owner

Approving the plan accepts these unless you say otherwise.

1. **Two slices.** S4a, Nine: the engine, T2, the husk, the lamp, the quiet day. S4b, the rest: T3, T4, T5, the files, bleed-through. Each has its own pass and Opus review, since S4b changes the Pattern's save format.
2. **T1's "Moving." changes.** The shipped line says "Green. Moving." of one frame, and T3 says "I don't even know how it moves." Proposed: "Green. Smeared, like it wouldn't hold still." It lands in S4b with T3.
3. **A real aurora with her awake.** "One frame" and "she will never see one" rest on nobody waking her before phase 4, and the vault has no such gate. Woken earlier, she can stand under the real thing. I recommend the flag `saw_aurora`, set when she is outdoors at night, near a player, with `AuroraPolicy.nightly` active and rain low. T3 then takes its two variant lines and the pool stays silent. She says nothing at the moment itself: writing that scene is a bigger decision. The other choice is to accept the hole for early wakers.
4. **Nine's lamp: C, in her handle (decided, owner, 2026-11-05).** Neither Notion nor the code said anything beyond "a keepsake item". It is a keepsake item until the player right-clicks her with it. She then takes it into her carry handle, dark, for good. Approved from [nine-lamp-mockup.png](art/aurora-shepherd/nine-lamp-mockup.png), and it adds to her approved body.
5. **Husk pose: A, sat down.** It reads as a stop, not a wreck, and it sits as Enoch sits. B is on its side. Soot, not frost, because the pit is a warm zone.
6. **Two files, and T5 opens on the second alone.** Okoye's duty log gains a posting page: Notion says the log gives his posting, and the log as built does not. The new Frame Register is behind Nine's lamp. A player who left the log in the locker still gets T5, without Enoch's beat. Requiring both would shut out anyone who missed a locker, with no hint why.
7. **"It's scrap." costs you the score, not the story.** Nothing is handed over in the scene, and the husk gives lamp and sheet to a right-click afterwards, with "...Thanks."
8. **T5's gate: above Wary, the quiet day over, the sheet read by someone near her.** No tier of its own, since the sheet is only reachable past T2's Friendly, and the lean needs a player who used her as a tool to get there. She saw the sheet come out of Nine, so asking does not break the Causal law. T5 sets no tier floor beyond that, and may come before T3 or T4; no line in it needs them.
9. **The husk is story, not world generation.** The creative-only rule covers recipes, loot and world generation. T2 is what places the husk, in a world where she is already awake, and it has no meaning otherwise. So the husk ships with T2. The block, lamp and sheet also go in the creative tab, with `/give`, and nothing gets a recipe or loot table. The log's new page is text.
10. **No HUD marker.** "Marking it" has nothing to build on, and the repo has no waypoint. She gives the ground ref as `TowerArchive` writes it, and Talk gives distance and bearing until she has seen the husk. No new screen, so no mockup.
11. **`{vault_offline_days}` is `max(2, woke day - 14)`.** The first figure is the day she woke, on the same game-time count as her `{day}` lines. Fourteen is Enoch's weeks: ruling 5 of the ORSA page has him outlast the first cold. The floor of 2 keeps "days" plural and the number positive for a very early waker. She already distrusts her clock, which covers what the arithmetic cannot.
12. **Eleven new names on the register, and Nine is Daniel Walsh.** Ordinary, none in the repo. Change any you like.
13. **What the quiet day holds back.** Everything that reaches the strip: reactions, unasked lines, nods, the recording slip. Personal prompts wait, and one already waiting does not show. Her face, the score, deeds, Follow, Lamp and the hub all work. Talk says "Not today." It runs on a cooldown in `deeds`, so `skipDay` already moves it.
14. **T3's numbers.** High is Y 110 or above with open sky at the player's feet; a tower counts. Stars means `getPhase6MidFadeProgress` at 0.5 or above. No active cyclone. Night and no rain come from `clearNight`. The cold check does not apply to the top, or T3 could never fire: at day 94 everyone outdoors is far below -10 C. Failing air still stops it. After "We don't have time." she asks again in three days.
15. **One game day between personal prompts.** Today the list would raise T2 at the same calm moment T1 ends. A list entry that is not ready is passed over, so T4 can come before T3 and needs nothing from it.
16. **"Pattern:" on her log shows before T5.** Your ruling 4 on the Miller spec keeps the blank field, and the rule on the word says never before T5. I read the field label as the one approved exception. It is M3's to build; S4 only sets `t5_done` for M3 to read.
17. **`fm.room`'s `log.other` changes,** as in the lines file, closing both wording minors S3 left for S4.
18. **"Fewer." comes from chat, once a world.** A plural after "less", as `\bless [a-z]+s\b` on the text `ShepherdReactions.chat` already sees. It echoes nothing. A false hit costs one odd line.
19. **The register plays no `READ_LOG` line.** "Read that bit again. Slower." over it would be wrong. The deed still counts; only the strip stays silent. Same for Okoye's log only if you say so: its lines stay as they are.

**Noted, not for S4:** `SelfModel.Stage` runs Recognising then Leaning, and T5 can reach Leaning first. S6 must treat Recognising as something she can reach from Leaning. The door test is unscheduled in any slice. `feat/continuity-payoff` also edits `docs/aurora-shepherd.md`, and its spec says to rebase on any Aurora branch that does.

## Global Constraints

- Every line is a draft until marked. Lines come from [aurora-s4-lines.md](aurora-s4-lines.md) as marked, not from this plan.
- She never says CONTINUITY, including labels, and never explains it or calls it a mind. No Heart, archive or Thaeven words. Maeve is absent from S4. Morrow is not a topic. Nothing comments on respawn, and a player's death is no event.
- TITHONUS, Sophie Miller and Anderson-Kind appear only in the Frame Register and `shepherd.t5.*`, with "Miller" and "pattern" about her likewise. Lang keys stay under `shepherd.*`.
- The verdict is exactly "pattern transfer only, subject not recovered". "Transfer" stays ORSA's word and is defined nowhere. Files use blank fields. `what_is_orsa.json` is not touched.
- She never says she is alive and never concludes she isn't. The fourth-wall count stays three. Her fear stays unfounded: nothing tells her she can or cannot end.
- She does not change after the Heart falls (CH-30): nothing in S4 reads it.
- `ShepherdReactions.saw` stays the door for what she sees and `MillerPattern.heard` for what she hears. The Pattern scans nothing.
- Same events at the same game times give bit-identical Pattern state. `StrictMath`, not `Math`. Time is overworld game time.
- No MACS edits. Never open the sealed Notion page. No `build.gradle` dependency. No emojis.
- Each new GameTest owns a batch, uses `ShepherdTestWorld.fresh`, `clear` and `whenTicking`, sets the clock before arming a cooldown, waits for its chunks, and goes in `config/architect-required-tests.txt` as `<lowercased name> xml`.
- One playtest script, `tools/prepare_aurora_s4_playtest.py`, with lab functions under its own namespace. Conventional Commits, staged by name. Never `./gradlew --stop`. Long builds in the background.

## Review Focus

Each has its test in the task named.

1. **T2 raised beside the pit, or the husk appearing in view.** T2 is not offered within 96 blocks of the pit, and no husk is placed within 32 blocks of a player (Task 3, `shepherdNoHuskInFrontOfAPlayer`, Task 4, `shepherdNoT2AtThePit`).
2. **The pit not built yet, or built over.** The target may be unplaced or rerolled, and a player may have built on the spot. The ground ref reads fresh, the husk waits for `isBlastPitPlaced`, and it takes the next free floor (Task 3, `shepherdHuskWaitsForThePit`, `shepherdHuskFindsFreeFloor`).
3. **Reaching the husk without her, or with her Wary.** It stays inert and the scene waits (Task 4, `shepherdTheHuskWaitsForHer`, `shepherdNoSiteSceneAtWary`).
4. **Closing the site scene early.** Esc or a hit mid-scene must neither lose the lamp nor give it twice, and the quiet day starts once (Task 4, `shepherdAClosedSiteSceneResumes`).
5. **A second player.** The sheet read by someone away from her, or T5's speaker not being who read it: the gate reads whoever is near, and the Enoch beat reads the speaker (Task 8, `shepherdT5ReadsWhoeverIsNear`).

---

## S4a: Nine

### Task 1: Dialogue arguments, conditions, and prompts in turn

**Files:**
- Modify: `shepherd/ShepherdDialogue.java`, `ShepherdTalk.java`, `ShepherdPrompt.java`, `ShepherdSavedData.java`, `network/ShepherdNodePayload.java`, `client/ShepherdScreen.java`, `miller/MillerPattern.java`
- Test: `ShepherdDialogueDataTest.java`, `ShepherdTalkGameTest.java`, `ShepherdAffinityGameTest.java`

**Interfaces:** Produces
- `ShepherdDialogue.Line` gains `List<String> args`, from a line's `"args"`. Tokens: `pit_x`, `pit_z`, `pit_blocks`, `pit_bearing`, `offline_days`. `ShepherdTalk` resolves them when it sends; `ShepherdNodePayload.Line` carries the strings; the screen passes them to `Component.translatable`.
- `ShepherdTalk.holds` gains `stage_noticing`, `stage_walls` (that stage or later), `quiet`, `tier_above_wary`, `found:<doc>` (the speaker holds `found_<doc>`) and `remembers:<kind>` (`MillerPattern.remembers`).
- `MillerPattern.stage(MinecraftServer)`, reading only.
- `ShepherdPrompt.Personal(Tier tier, String node, String asked, Ready ready)`, where `Ready` takes the server, level, data and near players. `PERSONAL` holds T1 alone at first, so behavior does not change.
- `ShepherdSavedData`: `long wokeAt()`, set at Boot, additive, `NOT_SET` when absent.
- `ShepherdPolicy.PERSONAL_GAP_TICKS = 24000`, kept as `deedReady("personal_gap")`.

- [ ] **Step 1: Write the failing tests.**
  - `everyConditionIsKnown` learns the new names and prefixes; `everyArgIsKnown` checks line tokens, and that a key's `%s` count matches.
  - `shepherdAnArgumentReachesTheScreen`: a test node with `pit_x` sends the pit's x.
  - `shepherdStageConditionsReadThePattern`, `shepherdFoundReadsTheSpeaker`.
  - `shepherdNextPersonalWaitsADay`: a second entry is not raised until 24000 ticks after the first.
  - `shepherdAnUnreadyEntryIsPassedOver`.
- [ ] **Step 2: Run them.** `./gradlew test --tests 'com.frozendawn.shepherd.*' --console=plain` and `./gradlew gameTestQuick -PfdTests=shepherd --console=plain`. Expected: fail.
- [ ] **Step 3: Implement.** The T1 withdraw rules apply to every entry.
- [ ] **Step 4: Run them.** Expected: PASS, with T1's tests unchanged.
- [ ] **Step 5: Commit.** `feat: let her lines carry arguments and read her state`.

### Task 2: The quiet day and what Nine does to her

**Files:**
- Modify: `shepherd/ShepherdAffinity.java`, `ShepherdReactions.java`, `ShepherdWill.java`, `miller/Appraisal.java`, `miller/ImpressionStore.java`
- Modify: `data/frozendawn/shepherd/dialogue/hub.json`, `en_us.json`
- Test: `AppraisalTest.java`, `ShepherdWillGameTest.java`

**Interfaces:** Consumes Task 1. Produces
- `Deed.NINE_FOUND(0, 0, Mood.SAD, List.of())`. `Appraisal.rule`: `new Rule(-0.6, 0, false, Drive.OTHERS, 0.6, Agent.WORLD)`. No impression.
- `ShepherdReactions.quiet(ShepherdSavedData, long now)` and `quietFor(ShepherdSavedData, long now)`, on `deedReady("nine_quiet")`, one day. The check sits in `speakUp`, which returns false so that once-only flags are not spent on a line nobody heard, and in `saw`, ahead of its strip.
- `ShepherdWill.RECALLS` moves to `Map.ofEntries`: `Map.of` stops at ten, and this adds four.
- Node `hub.talk.quiet` heads the Talk chain, `when: quiet`. Trigger `QUIET_OVER`, once, when it ends.

- [ ] **Step 1: Write the failing tests.**
  - `everyDeedHasARule` covers it. `nineLeavesHerSad`: from fresh, Sad, Others lower, keepsake `nine_found`.
  - `shepherdQuietHoldsTheStrip`: reaction, murmur and slip all silent, with the score and face still moving.
  - `shepherdQuietKeepsAOnceOnlyLine`: a nod held back by the quiet still plays after it.
  - `shepherdQuietHoldsPrompts`, `shepherdQuietEndsWithALine`, `shepherdSkipDayEndsTheQuiet`.
  - `shepherdQuietTalkSaysNotToday`.
- [ ] **Step 2: Run them.** Expected: fail.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: she goes quiet for a day after Nine`.

### Task 3: The husk, the lamp and the sheet

**Files:**
- Create: `shepherd/ShepherdNine.java`, `shepherd/ShepherdHuskBlock.java`, `ShepherdHuskBlockEntity.java`, `client/ShepherdHuskRenderer.java`, `item/NineLampItem.java`
- Create: `entries/orsa_intel/tithonus_register.json`, advancement `found_tithonus_register.json`, blockstate, item models, textures via `make_textures.py`
- Modify: `init/ModBlocks.java`, `ModItems.java`, `ModBlockEntities.java`, `item/OrsaDocumentItem.java`, `shepherd/ShepherdSavedData.java`, `en_us.json`, block tags `wither_immune`, `dragon_immune`
- Test: `ShepherdNineGameTest.java` (new), `ShepherdNineSpotTest.java` (new)

**Interfaces:** Consumes Task 2. Produces
- `frozendawn:cs1_husk`, `vaultSealed` properties, `FACING`, `LAMP` boolean. Drawn from `ShepherdModel` with `cs1_husk.png`, in the pose you pick. In the creative tab.
- `frozendawn:nine_lamp`, stack of 1, fire-resistant, no despawn. In the creative tab.
- `ShepherdNine.spot(BlockPos center, Predicate<BlockPos> free)`: pure, the first free of eight offsets around (9, 7), on that ring. `ShepherdNine.register()`: the sheet, `doc_type` `tithonus_register`, named "Folded Sheet".
- `ShepherdNine.tick`, own `ServerTickEvent.Post`, every 20 ticks: `nine_open` set, no `huskPos`, `OrsaStructureState.isBlastPitPlaced`, `ReadyChunks.ready`, no player within 32 of the spot. Then it places the block facing west, and stores the position.
- `ShepherdSavedData.huskPos()`, additive.
- `ShepherdNine.handOver(ServerPlayer)`: lamp and sheet, once, clearing `LAMP`, setting `nine_sheet_taken`.
- Husk use: nothing until `nine_seen`; then `handOver` with the late "...Thanks."; a charged Power Cell gets `CS1_DOCK_REFUSE` and the cell line, and is not spent.

- [ ] **Step 1: Write the failing tests.**
  - `spotAvoidsThePadTowerAndTrench`, `spotTakesTheNextFreeFloor` (JUnit).
  - `shepherdHuskIsPlacedWhenThePitLoads`, `shepherdHuskWaitsForThePit`, `shepherdHuskFindsFreeFloor`, `shepherdNoHuskInFrontOfAPlayer`, `shepherdOnlyOneHusk`.
  - `shepherdTheHuskCannotBeBroken`, `shepherdHandOverGivesOnce`, `shepherdACellIsRefusedAndKept`.
  - `theRegisterIsFiled`: `DOCS` has it, its entry and advancement exist, and it holds the verdict word for word.
- [ ] **Step 2: Run them.** Expected: fail.
- [ ] **Step 3: Implement.** The register's text is the marked draft. `READ_LOG` keeps its score for it and skips the strip (Ruling 19).
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: put Nine at the Blast Pit`.

### Task 4: T2 and the site

**Files:**
- Modify: `data/.../dialogue/personal.json`, `hub.json`, `en_us.json`, `shepherd/ShepherdPrompt.java`, `ShepherdTalk.java`
- Test: `ShepherdNineGameTest.java`, `ShepherdDialogueDataTest.java`

**Interfaces:** Consumes Tasks 1 to 3. Produces
- `PERSONAL` entry `t2.others`, Friendly, ready when the pit resolves and nobody near is within 96 blocks of it.
- `ShepherdPrompt.atNine`, beside `ask`, which stays vault-only: `huskPos` set, `nine_asked` unset, she is within 12 blocks of it, above Wary, calm. It raises `t2.site`.
- `ShepherdTalk.settle` hands over on the flag `nine_handover`, as it does for `profanity_heavy`.
- Arriving at `t2.site` sets `nine_seen`, sends `NINE_FOUND` through `saw` and starts the quiet day, once.
- Node `hub.talk.pit` in the Talk chain, `when: nine_open` and not `nine_seen`. Flags for S5: `t2_heard`.

- [ ] **Step 1: Write the failing tests.**
  - Every node walks; `shepherdT2OpensThePit`; `shepherdAskAboutTheFindReturnsToTheChoice`.
  - `shepherdNoT2AtThePit`, `shepherdTheHuskWaitsForHer`, `shepherdNoSiteSceneAtWary`.
  - `shepherdKindRepliesHandOverTheLamp`, `shepherdScrapHandsOverNothing`, `shepherdScrapStillOpensTheHusk`.
  - `shepherdAClosedSiteSceneResumes`: closing mid-scene and reopening gives one lamp and one quiet day.
  - `shepherdKindRepliesReachExploitable`: T2's replies count toward the wall M2b left waiting.
- [ ] **Step 2: Run them.** Expected: fail.
- [ ] **Step 3: Implement,** with the marked lines.
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: she tells you about the others`.

### Task 5: The lamp in her handle, the pass, the review

**Files:**
- Modify: `shepherd/ShepherdEntity.java`, `client/ShepherdModel.java`, `ShepherdRenderer.java`
- Create: `tools/prepare_aurora_s4_playtest.py`
- Modify: `docs/aurora-shepherd.md`, `config/architect-required-tests.txt`

**Interfaces:** Consumes Task 3. `nine_lamp_worn` flag, synced by entity data; right-clicking her with the lamp takes it.

- [ ] **Step 1: Write the failing test.** `shepherdSheTakesTheLamp`: the item is spent, the flag holds through a reload, and her display is unchanged.
- [ ] **Step 2: Implement it,** from the approved mockup.
- [ ] **Step 3: Write the playtest script.** Save `Aurora Nine Check`, as AGENTS.md requires: a small pit built by the real placement, buttons for Friendly, the husk, `[Skip a day]`. No edit to another feature's fixtures.
- [ ] **Step 4: Run the gate.** `./gradlew architectVerify --console=plain` in the background. Expected: PASS.
- [ ] **Step 5: Offer the owner's pass.** Launch `runClientLab -PfdLabWorld='Aurora Nine Check'` and read the log. Take the owner's description before explaining anything.
- [ ] **Step 6: Branch review on Sonnet by default,** Opus if the placement touches a shared system. Fix findings test first, then record Status and commit `docs: record Nine's handback`. Offer the PR. No merge without the owner.

---

## S4b: The sky, off, before

### Task 6: T3

**Files:**
- Modify: `shepherd/ShepherdPolicy.java`, `ShepherdPrompt.java`, `ShepherdReactions.java`, `personal.json`, `en_us.json`
- Test: `ShepherdPolicyTest.java`, `ShepherdSkyGameTest.java` (new)

**Interfaces:** Produces
- `ShepherdPolicy.SKY_HIGH_Y = 110`, `STARS_SHOW_AT = 0.5F`, `boolean starsShow(int phase, float progress)`: `isPhase6MidOrLater` and fade progress at or above it.
- `PERSONAL` entry `t3.sky`, Trusted, ready on `starsShow`. Blunt reply clears `t3_asked` and arms `deedReady("t3_again")`, three days.
- `ShepherdPrompt.atTheTop`: `t3_going`, `t3_top` unset, a near player at or above the Y with `clearNight` true, `starsShow`, `CycloneSavedData.active()` empty, `unhunted`, air not failing. No cold check. It raises `t3.top`.
- `saw_aurora` in `ShepherdReactions.look`, by Ruling 3; `imaginedAuroraShows` reads it.

- [ ] **Step 1: Write the failing tests.**
  - `starsDoNotShowBeforeHalfFade`: phases 4 and 5, early phase 6 and 0.72 false, 0.785 true.
  - `shepherdT3WaitsForTheStars`, `shepherdTheTopWaitsForAClearNight`, `shepherdTheTopIgnoresTheCold`, `shepherdTheTopWaitsOutACyclone`.
  - `shepherdBluntT3AsksAgainInThreeDays`.
  - `shepherdARealAuroraChangesTheTop`, and the pool stays silent after one.
  - `shepherdStarsLineReadsTheKeepsake`.
- [ ] **Step 2: Run them.** Expected: fail.
- [ ] **Step 3: Implement,** with T1's fix (Ruling 2) and `fm.room`'s (Ruling 17).
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: she asks you to take her up`.

### Task 7: T4

**Files:**
- Modify: `shepherd/ShepherdPrompt.java`, `ShepherdTalk.java`, `personal.json`, `en_us.json`
- Test: `ShepherdPolicyTest.java`, `ShepherdSkyGameTest.java`

**Interfaces:** Consumes Task 1. Produces `PERSONAL` entry `t4.off`, Close, always ready, and `ShepherdPolicy.offlineDays(long wokeAt)`: `max(2, day(wokeAt) - 14)`, the day as `Keepsake.day()` counts it. A save with no `wokeAt` reads 2.

- [ ] **Step 1: Write the failing tests.** `offlineDaysIsNeverBelowTwo`, `offlineDaysCountsFromTheWake`, `shepherdT4CanComeBeforeT3`, and `t4IsWordForWord` against the script's text.
- [ ] **Step 2: Run them.** Expected: fail.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: she tells you what she is afraid of`.

### Task 8: T5, the log's page and the lean

**Files:**
- Modify: `miller/SelfModel.java`, `PatternState.java`, `MillerPattern.java`, `ImpressionStore.java`, `PatternDiagnostics.java`
- Modify: `shepherd/ShepherdPrompt.java`, `ShepherdTalk.java`, `personal.json`, `en_us.json`, `entries/orsa_intel/terminus_log_last.json`
- Test: `SelfModelTest.java`, `PatternStateTest.java`, `ShepherdWillGameTest.java`, `VaultDocumentsTest.java`

**Interfaces:** Produces
- `SelfModel.Lean { NONE, SOMEONE, LEFT }`, saved. `SelfModel.lean(Lean)` sets the stage to `LEANING` and never changes an existing lean.
- `ImpressionStore.leansSomeone(UUID who)`: named by them, and `TALKS_TO_ME` at or above `USES_ME`.
- `MillerPattern.lean(MinecraftServer, UUID who)`, called on arriving at `t5.close`; `leaning(MinecraftServer)`, reading.
- `PatternState.VERSION = 3`.
- Conditions `lean_someone`, `lean_left`.
- `PERSONAL` entry `t5.before`, tier Neutral and above Wary, ready when `nine_sheet_taken`, not quiet, and someone near holds `found_tithonus_register`. Sets `t5_done`, which M3 reads.

- [ ] **Step 1: Write the failing tests.**
  - `theLeanFollowsHowSheWasTreated`, both ways. `theLeanIsSetOnce`. `aFormerSaveLoadsWithNoLean`. `theLeanSurvivesAReload`.
  - `leaningComesFromAnyStage`, including Product.
  - `shepherdT5WaitsForTheSheet`, `shepherdT5WaitsOutTheQuiet`, `shepherdNoT5AtWary`, `shepherdT5ReadsWhoeverIsNear`.
  - `shepherdEnochsBeatNeedsHisLog`.
  - `okoyesLogKeepsItsPagesAndGainsAPosting`: the existing pages unchanged, the new one last.
- [ ] **Step 2: Run them.** Expected: fail.
- [ ] **Step 3: Implement,** with the marked lines. Nothing reads the Heart.
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: she learns what was done to her`.

### Task 9: Bleed-through, recall and the lints

**Files:**
- Modify: `shepherd/ShepherdAmbient.java`, `ShepherdReactions.java`, `ShepherdWill.java`, `ambient/starter.json`, `en_us.json`
- Test: `ShepherdDialogueDataTest.java`, `ShepherdWillGameTest.java`

**Interfaces:** Produces `Trigger.BLEED`, cooldown 72000, its pool chosen by `SelfModel` stage and lean, in `moment`; `ShepherdEggs` holds "Fewer.", flag `said_fewer`, then its aside after 80 ticks. Recall lines for `nine_found`, `t3_stars`, `t4_off`, `t5_before`.

- [ ] **Step 1: Write the failing tests.**
  - `namesStayInTheirPlace`: no `shepherd.*` value outside `shepherd.t5.*` contains sophie, miller, tithonus or anderson.
  - `herWordsAboutHerselfStayClean`: the banned-word lint extends to `shepherd.bleed.*`, `shepherd.t2.*` to `shepherd.t5.*`: six, fair, rules, alive, mind, heart, archive, respawn, morrow, maeve, and continuity everywhere.
  - `shepherdBleedNeedsNoticing`, `shepherdBleedTurnsAtTheWalls`, `shepherdBleedChangesAfterT5`.
  - `shepherdFewerPlaysOnce`, `shepherdFewerEchoesNothing`.
  - `shepherdEachNewKeepsakeHasALine`.
- [ ] **Step 2: Run them.** Expected: fail.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: things turn up in her that nobody loaded`.

### Task 10: The pass and the review

**Files:**
- Modify: `tools/prepare_aurora_s4_playtest.py`, `docs/aurora-shepherd.md`, `docs/aurora-miller-pattern.md`, `config/architect-required-tests.txt`

- [ ] **Step 1: Extend the playtest.** Save `Aurora Sky Check`, with buttons for phase 6 mid, Trusted, Close, a clear night, the tall tower and the file. Preserve `Aurora Nine Check`.
- [ ] **Step 2: Run the gate.** `./gradlew architectVerify --console=plain` in the background. Expected: PASS. `architectMonkey` only if Architect code changed, and none should.
- [ ] **Step 3: Offer the owner's pass.** T3's stars at altitude, how T5 reads, whether the bleed jokes land, how often they play.
- [ ] **Step 4: Branch review on Opus,** since this changes the Pattern's save format. Fix findings test first. Record Status, commit `docs: record the arc's handback`, offer the PR. No merge without the owner.

## Sources

`ShepherdPrompt`, `ShepherdTalk`, `ShepherdReactions`, `ShepherdWill`, `SelfModel`, `PatternState`, `BlastPitPlacement`, `WeatherHandler`, `LevelRendererMixin`, `PhaseManager`, `AuroraPolicy`, `OrsaDocumentItem`, `TowerArchive` at a0b77afa. No Aurora checkpoint file exists, so the spec's and Miller spec's Status sections serve as one. The sealed page was not opened.

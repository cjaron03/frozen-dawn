# Miller Pattern M0 and M1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task, inline. The owner avoids subagents, except the one branch review at the end. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** M0 picks how typed text will be matched, from measurements. M1 gives her a saved inner state: what she saw moves drives and feelings, and her feelings choose the face she rests on.

**Architecture:** `MillerPattern` is a static facade over `PatternRuntime`, as `MaeveDirector` is over `DirectorRuntime`. The logic is pure and sits in `PatternState`, so JUnit reaches all of it. Every value is stored with the game time it was set and read in closed form, so reading never changes state and the poll's timing cannot change a result.

**Tech Stack:** Java 21, NeoForge 1.21.1, JUnit in `src/test`, GameTests in `src/gameTest`. M0 is plain Java run from the command line.

**Spec:** [aurora-miller-pattern.md](aurora-miller-pattern.md), with [aurora-shepherd.md](aurora-shepherd.md) and [aurora-orsa-topics.md](aurora-orsa-topics.md).

**Branch:** `feat/miller-pattern` in `/Volumes/SSD/Work/frozen-dawn-miller-pattern`, at 6e78abc8, cut from `feat/maeve-director` at afec4f8f. PR into `feat/maeve-director`.

## The one thing to decide first

**M1 is visible, not headless.** "Feelings choose among the moods" can only reach the screen through `ShepherdEntity.rest()`, the face she settles on every tick. Today that is always Neutral. So M1 needs one owner pass, a playtest world, and your word before I launch the client. The other choice is to compute feelings and show them only in `/fd shepherd state`, leaving her face alone until M2. I recommend wiring it now: it is one line, and it is the only way to judge the numbers below.

## Rulings for the owner

Approving the plan accepts these unless you say otherwise.

1. **Feelings set her resting face only.** Reaction faces and line moods stay authored, because a line and its mood were written together. So she reacts as now for 3 seconds, then rests on what she feels until it fades to Neutral. Wary still rests on Recording, Offline on Offline, asking on Thinking, and a conversation keeps her face.
2. **Her idle expressions and eye roll play on every resting mood (owner, 2026-11-04, reversing this plan's first draft).** Only a face she rests on gets them: Recording and Offline stay without, a conversation keeps her face as now, and a 3-second reaction has none. If an idle clashes with a mood, that is a look question for the owner's pass, not one to settle alone.
3. **Which feeling shows which face.** The strongest feeling at or above 0.30 wins; below that she is Neutral. Recording and Offline are states, not feelings, so feelings reach six of the nine `Mood` values, plus Neutral.

   | Feeling | Mood | Half-life, ticks | Why |
   | --- | --- | --- | --- |
   | Joy | Happy | 6000 | Dry: it shows less and leaves soonest |
   | Distress | Sad | 12000 | |
   | Hope | Thinking | 12000 | Matches her imagined-aurora lines |
   | Fear | Alarmed | 2400 | A stare should not last |
   | Gratitude | Amused | 24000 | Warm under it: thanks comes out as a joke |
   | Reproach | Unimpressed | 24000 | Stubborn: twice distress |
   | Guilt | Sad | 72000 | |

4. **How each deed feels.** The score table is untouched. This table is new, and each row is one line of an enum. Desirability drives joy, distress, hope and fear; praise drives gratitude and reproach, as OCC separates them. Scale is `0.5 + 0.5 * drive`, or 1 with no drive. "Prospect" means it may yet happen, so hope or fear in place of joy or distress.

   | Deed | Desirability | Praise | Prospect | Drive | Feeds it by | Rests on, at the start |
   | --- | --- | --- | --- | --- | --- | --- |
   | `WARM_SHELTER` | +0.6 | +0.3 | no | Staying On | 0.4 | Happy, 1.3 min |
   | `READ_LOG` | +0.5 | +0.2 | yes | Curiosity | 0.5 | Thinking, 3.2 min |
   | `CLEAR_NIGHT` | +0.7 | 0 | no | Sky | 0.6 | Happy, 2.4 min |
   | `ARCHIVIST_KILLED` | -0.7 | -0.6 | no | Others | 0 | Sad 4.4 min, then Unimpressed to 11.7 min |
   | `SHELTER_FROZE` | -0.3 | -0.4 | yes | none | 0 | Unimpressed, 8.3 min |
   | `AIR_FAILING` | -0.6 | 0 | yes | none | 0 | Alarmed, 2 min |
   | `LEFT_BEHIND` | -0.3 | -0.5 | no | Company | 0 | Unimpressed, about 13 min |
   | `CALLED_SHEPHERD` | -0.1 | -0.4 | no | none | 0 | Unimpressed, 8.3 min |

   `READ_LOG` is the only row whose rest differs from its authored reaction: she reacts Neutral, then thinks about it. I think the Unimpressed rows may run long. That is what the pass is for.

5. **Drives weigh events and never show on her face in M1.** All start at 0.5 and rise to a ceiling. If a high drive could sadden her, she would sit Sad from phase 2 to mid phase 6, since `CLEAR_NIGHT` cannot fire then.

   | Drive | Ceiling | Rise half-life, ticks | What feeds it in M1 |
   | --- | --- | --- | --- |
   | Company | 1.0 | 12000 | 0.01 per poll with anyone near; 0.10 per conversation |
   | Sky | 1.0 | 48000 | `CLEAR_NIGHT` |
   | Others | 0.8 | 96000 | Nothing yet |
   | Staying On | 0.8 | 48000 | `WARM_SHELTER` |
   | Curiosity | 1.0 | 24000 | `READ_LOG` |

6. **Not everything is reachable yet.** No deed has her as the cause, so guilt has no event until S4 or S5. It is built and tested with a made-up event. Others only rises. Amused needs stacked thanks, so it will be rare.
7. **Her going Offline and her curiosity looks are not events.** Neither comes through `saw`, and that door stays the only one. Conversations reach Company by the poll reading `lastTalk`, which is perception, not a new door.
8. **Nothing with the name reaches a player.** The save file is `frozendawn_shepherd_state`, not `frozendawn_miller`. Debug text says "CS-1", and a test holds that. Only Java class names carry the name, the risk the spec accepts.
9. **The spec's datamine line is not true yet.** `PatternLaws` has MACS's six names, but MACS has no such enum in code, only prose and one comment. I add ours and leave MACS alone, so the match exists once MACS gets one, which is for you and S6. The enum's only use in M1 is a footer in `explain`.
10. **New debug:** `/fd shepherd state`, `why`, and `saw <deed>`, which sends a deed through the real door for the lab. `skipday` also ages her feelings, and `replay` clears them.
11. **M0 downloads, if you approve.** Into git-ignored `build/intent-trial/`, each in its own folder, and never into `build.gradle`. Sizes below are the spec's or estimates; the report gives measured ones.
    - `model.safetensors` and `tokenizer.json`, `huggingface.co/minishlab/potion-base-8M`, about 30 MB
    - `model.onnx`, its int8 variant and `tokenizer.json`, `huggingface.co/sentence-transformers/all-MiniLM-L6-v2`, about 90 and 25 MB
    - `onnxruntime` jar, Maven Central, `com.microsoft.onnxruntime`, unmeasured
12. **Optional, and worth having:** 20 or 30 lines typed by you, as you would ask her, without seeing my phrasings. I write both the examples and the test set, which flatters every option. Yours would be the honest column. M0 does not wait for it.

## Global Constraints

- Her words stay authored. M1 adds no lines, no lang keys and no art. No language model writes anything.
- "Miller" and "pattern" as a word about her appear in no lang key, save name, command output or player text. She never says CONTINUITY.
- A player's death is never an appraisal event, and nothing comments on respawn.
- `ShepherdReactions.saw` is the one door events come through. The Pattern scans nothing of its own: it runs on the 100-tick poll and on events.
- Same events at the same game times give bit-identical state. Use `StrictMath`, never `Math`, for decay. M1 uses no randomness.
- Time is overworld game time. Affinity, tiers, their table and `ShepherdSavedData`'s format do not change.
- `MaeveDirector`'s shape: a facade that orchestrates, collaborators reached only through it, under about 300 lines.
- No MACS edits. Never open the Notion page "SEALED: Sequel Seed". No `build.gradle` dependency. No emojis.
- Each new GameTest owns a batch, uses `ShepherdTestWorld.fresh`, `clear` and `whenTicking`, sets the clock before arming a cooldown, and goes in `config/architect-required-tests.txt` as `<lowercased name> xml`.
- Conventional Commits, staged by name. Never `./gradlew --stop`. Long builds in the background.

## Review Focus

Each has its test in the task named.

1. **An existing save with her already awake.** No state file exists, and game time may be huge. She starts at the baselines with no jump in the first poll (Task 3, `aSaveWithNoStateStartsAtRest`).
2. **The clock moving backwards,** from a restored backup or a save edit. Elapsed time clamps to zero, and nothing goes negative or NaN (Task 2, `aClockThatRunsBackwardsChangesNothing`).
3. **A damaged or hand-edited save.** Values out of range or not finite load as clamped or zero; a future `dataVersion` refuses to load, as `MaeveSavedData` does (Task 3, `aDamagedSaveLoadsInRange`).
4. **A deed during a conversation.** The score lands today, so the feeling must too, and her face stays the conversation's (Task 5, `shepherdADeedDuringATalkStillFeels`).
5. **Two players.** One state for the world, so one player's cruelty shows while the other stands there. That is the spec's rule, and history must name who did it (Task 3, `historyNamesWhoDidIt`).

---

## M0

### Task 1: The intent trial

**Files:**
- Create: `tools/intent_trial/intents.json`, `tools/intent_trial/phrasings.json`, `tools/intent_trial/IntentTrial.java`
- Create: `docs/aurora-intent-trial.md`

**Interfaces:** Produces only the report. M4 reads its recommendation; M1 does not depend on it.

- [ ] **Step 1: Write the examples.** `intents.json`: the 22 topics in `aurora-orsa-topics.md`, ids from their titles, 10 phrasings each, as the spec's 8 to 15.
- [ ] **Step 2: Write the test set, separately.** `phrasings.json`, about 330 entries of `{text, expected, style}`, none copied from an example:
  - 264 in scope, 12 per topic: casual, terse, typo, long-winded, and indirect, as a player would type them.
  - 66 with `expected: null`: small talk, gibberish, "is this minecraft", "are you alive", and locked names (Sophie, TITHONUS, Anderson-Kind, Maeve), which must count as misses.
  - Weight it toward the near neighbours: "Who is Kevin?" against "Did Kevin really volunteer?", Exodus against "Is anyone still out there?", "The seventh floor" against "Who ran the vault?", and the recall date against Exodus.
- [ ] **Step 3: Fix the pass marks before running anything.** Write them at the top of the report, on the held-out half:
  - Top-1 at least 80 percent; right intent in the top two at least 90 percent.
  - Strong and wrong at most 5 percent: she answers the wrong topic, which is the worst outcome.
  - Out-of-scope reaching Strong at most 5 percent.
  - p99 under 25 ms per line, half a tick, on one thread.
  - Added jar at most 30 MB. The last built jar was 45 MB (`frozen-dawn/build/libs`, 2026-10-23).
- [ ] **Step 4: Build `IntentTrial.java`.** One file, run with `java -cp <gson>:<onnxruntime> tools/intent_trial/IntentTrial.java`, so latency is measured on the real runtime. It reads safetensors directly, and one WordPiece tokenizer serves both models if their vocabularies match; check that first. Options:
  - `potion-base-8M` at float32, float16 and int8: token lookup and mean, cosine to each intent's nearest example.
  - MiniLM by ONNX Runtime at float32 and int8.
  - Word and synonym matching: lowercased stems, a hand synonym table, TF-IDF overlap.
- [ ] **Step 5: Split and tune.** A fixed hash splits the set in half. Strong and Close thresholds come from the dev half only; every reported number comes from the other half.
- [ ] **Step 6: Run it.** Record per option and per style: top-1, top-2, Strong right, Strong wrong, Close, None, out-of-scope false Strong; load time, heap, p50 and p99; added jar, measured by compressing the real files.
- [ ] **Step 7: Check licences at each official source.** The `minishlab` card and the model2vec repository, the teacher model named on the card, the `sentence-transformers` card, ONNX Runtime's `LICENSE`, and which platforms its Maven jar ships natively. Check each against the mod's `LGPL-3.0-or-later`. The spec's listing came from a mirror, so this replaces it.
- [ ] **Step 8: Write the report.** Recommend the smallest added jar that clears every mark. If none does, say so, name the closest and what it misses; do not retune against the test half. State the limits: one author wrote both sets, one machine, and ONNX loading under NeoForge's classloader is not tested, since no `build.gradle` change is allowed here.
- [ ] **Step 9: Commit.** `git add` the four files by name; `docs: report the typed text matching trial`.

---

## M1

Package `com.frozendawn.miller`. Tests in `src/test/java/com/frozendawn/miller/`.

### Task 2: Values that fade, drives and feelings

**Files:**
- Create: `Fading.java`, `Temperament.java`, `Drives.java`, `Feelings.java`
- Test: `FadingTest.java`, `DrivesTest.java`, `FeelingsTest.java`

**Interfaces:** Produces
- `record Fading(double value, long at)`: `double toward(double target, long halfLife, long now)`, `Fading set(double value, long now)`, `Fading aged(long ticks)`. The read is `target + (value - target) * StrictMath.pow(0.5, max(0, now - at) / (double) halfLife)`.
- `Temperament`: constants `DRY = 0.8`, `WARM = 1.25`, `STUBBORN = 2.0`, `SHOWS_AT = 0.30`, `DRIVE_START = 0.5`.
- `Drives`: `enum Drive { COMPANY, SKY, OTHERS, STAYING_ON, CURIOSITY }` with ceiling and rise half-life from Ruling 5; `double value(Drive, long now)`; `void feed(Drive, double amount, long now)`, floored at 0.
- `Feelings`: `enum Feeling { JOY, DISTRESS, HOPE, FEAR, GRATITUDE, REPROACH, GUILT }` with half-life and `Mood` from Ruling 3, reproach's half-life being `STUBBORN` times distress's; `double value(Feeling, long now)`; `void raise(Feeling, double intensity, long now)`, as `v + (1 - v) * intensity`; `Mood mood(long now)`.

- [ ] **Step 1: Write the failing tests.**
  - `aValueHalvesInOneHalfLife`: value 0.8 toward 0 reads 0.4 after one half-life.
  - `readingChangesNothing`: a thousand reads leave the record equal.
  - `twoStepsEqualOne`: fading 50 ticks, storing, fading 50 equals fading 100, within 1e-12.
  - `aClockThatRunsBackwardsChangesNothing`: `now` before `at` reads the stored value.
  - `aVeryLongGapStaysFinite`: `Long.MAX_VALUE / 2` ticks reads the target.
  - `aDriveRisesToItsCeilingAndNoFurther`: for each drive, 100 half-lives reads the ceiling within 1e-9.
  - `feedingLowersADriveAndStopsAtZero`.
  - `aRaisedFeelingNeverPassesOne`: 1000 raises at 1.0 stay at or below 1.
  - `reproachOutlastsDistress`: reproach's half-life is `STUBBORN` times distress's.
  - `belowTheLineSheIsNeutral`: 0.29 gives `NEUTRAL`, 0.30 gives its mood.
  - `eachFeelingShowsItsMood`: all seven, against Ruling 3's table.
  - `theStrongestFeelingWins`, and `aTieGoesToTheEarlierFeeling`.
  - `noFeelingIsRecordingOrOffline`.
- [ ] **Step 2: Run them.** `./gradlew test --tests 'com.frozendawn.miller.*' --console=plain`. Expected: fails to compile.
- [ ] **Step 3: Implement the four classes.**
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: add the drives and feelings that fade on game time`.

### Task 3: Appraisal and saved state

**Files:**
- Create: `Appraisal.java`, `PatternState.java`
- Test: `AppraisalTest.java`, `PatternStateTest.java`

**Interfaces:** Consumes Task 2. Produces
- `Appraisal`: `enum Agent { PLAYER, WORLD, SELF }`; `record Rule(double desirability, double praise, boolean prospect, Drive drive, double feeds, Agent agent)`, `drive` nullable; `static Rule rule(Deed)` from Ruling 4; `static Map<Feeling, Double> intensities(Rule, double scale)`. Praise goes to gratitude or reproach for `PLAYER` and to guilt for `SELF`; joy multiplies by `DRY`, gratitude by `WARM`.
- `PatternState`: `void appraise(UUID who, Deed deed, long now)`, `void poll(int near, long lastTalk, long now)`, `Mood mood(long now)`, `void age(long ticks)`, `void clear()`, `List<Appraised> history()`, `CompoundTag save()`, `static PatternState load(CompoundTag)`. Keeps `heardTalk`, so a conversation counts once across a reload. History is capped at `MAX_HISTORY = 16`, newest last (Bounded).
- `record Appraised(long time, UUID who, String deed, Map<Feeling, Double> raised)`.

- [ ] **Step 1: Write the failing tests.**
  - `everyDeedHasARule`: for each `Deed`, so a new one cannot slip past.
  - `eachDeedRestsWhereTheTableSays`: from a fresh state, each deed's mood and how long it holds, against Ruling 4, within 60 seconds.
  - `aWantedThingLandsHarder`: `CLEAR_NIGHT` with Sky at 1 gives more joy than with Sky at 0.
  - `theDriveIsReadBeforeItIsFed`: the same deed in a fresh state uses 0.5, not the fed value.
  - `whoCausedItDecidesThanksOrBlame`: a `PLAYER` rule gives gratitude or reproach, `WORLD` neither, `SELF` guilt.
  - `aProspectGivesHopeOrFear`: no joy or distress.
  - `sameEventsGiveTheSameState`: one script of 200 deeds and polls, run twice; `save()` tags equal, bit for bit.
  - `readingDoesNotChangeTheResult`: the same script with `mood` and `value` reads between events gives an equal tag.
  - `sheLandsOnNeutralInTheEnd`: every deed, read 20 half-lives later, is `NEUTRAL`.
  - `saveAndLoadIsExact`: `load(save())` then the same script tail equals the unbroken run.
  - `aSaveWithNoStateStartsAtRest`: `load(new CompoundTag())`, first `poll` at game time 10,000,000, drives within 1e-9 of 0.5.
  - `aDamagedSaveLoadsInRange`: 7.0, -1 and NaN load inside [0, 1].
  - `aFutureSaveIsRefused`: `dataVersion` above the current throws `IllegalStateException`.
  - `aConversationCountsOnce`: the same `lastTalk` over three polls feeds Company once, including across a reload.
  - `companyFallsWithSomeoneNearAndRisesAlone`.
  - `historyKeepsTheLatestSixteen`, and `historyNamesWhoDidIt`: two players' deeds keep their own UUIDs.
  - `ageingMovesEveryClock`: `age(24000)` equals 24000 ticks passing.
- [ ] **Step 2: Run them.** Expected: fails to compile.
- [ ] **Step 3: Implement both classes.** Intensity is read and history written before the drive is fed.
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: appraise what she saw and keep it in a saved state`.

### Task 4: The facade, runtime and diagnostics

**Files:**
- Create: `MillerPattern.java`, `PatternRuntime.java`, `PatternSavedData.java`, `PatternDiagnostics.java`, `PatternLaws.java`
- Modify: `src/main/java/com/frozendawn/command/ShepherdDebugCommand.java`, `FrozenDawnDebugCommand.java:103`
- Test: `PatternDiagnosticsTest.java`, `PatternLawsTest.java`

**Interfaces:** Consumes Tasks 2 and 3. Produces, all `public static` on `MillerPattern`:
- `void appraise(MinecraftServer, UUID who, Deed)`; `void poll(MinecraftServer, int near, long lastTalk)`; `Mood restingMood(MinecraftServer)`
- `void skipDay(MinecraftServer)`; `void reset(MinecraftServer)`; `void onServerStopped(MinecraftServer)`
- `Snapshot snapshot(MinecraftServer)`; `List<String> diagnostics(MinecraftServer)`; `List<String> explain(MinecraftServer)`
- `record Snapshot(long now, Mood mood, Map<Feeling, Double> feelings, Map<Drive, Double> drives, List<Appraised> history)`

`PatternRuntime`: package-private; `current(server)` from an identity map, throwing off the server thread; `stopped(server)`. `PatternSavedData`: package-private, `NAME = "frozendawn_shepherd_state"`, wraps `PatternState`. `PatternLaws`: `LAGGED, LEGIBLE, EXPLOITABLE, FALLIBLE, BOUNDED, CAUSAL`, each with `meaning()`.

- [ ] **Step 1: Write the failing tests.** `PatternDiagnostics.format(Snapshot)` and `explain(Snapshot)` are pure.
  - `explainNamesTheCauseOfHerFace`: after `ARCHIVIST_KILLED`, a line holds `ARCHIVIST_KILLED`, the player's UUID and the feeling's value.
  - `explainSaysWhenThereIsNoCause`: fresh state, a line says Neutral with nothing above the line.
  - `explainSaysWhenTheCauseAgedOut`: a mood whose appraisal left the history says so, and does not guess.
  - `nothingShownCarriesHerFileName`: no line of `format` or `explain` contains "miller" or "pattern", in any case.
  - `theLawsAreMacsSix`: exactly those six names, in that order.
- [ ] **Step 2: Run them.** Expected: fails to compile.
- [ ] **Step 3: Implement the five classes.** `explain` ends with each law's `meaning()`.
- [ ] **Step 4: Add `/fd shepherd state`, `why` and `saw <deed>`,** and their help lines. `saw` calls `ShepherdReactions.saw` for the command's player, so it pays the cooldown and range like any deed.
- [ ] **Step 5: Run them.** Expected: PASS.
- [ ] **Step 6: Commit.** `feat: add the facade and diagnostics for her inner state`.

### Task 5: Through the door

**Files:**
- Modify: `src/main/java/com/frozendawn/shepherd/ShepherdReactions.java:72` (`saw`), `:174` (`tick`), `:169` (`skipDay`), `:160` (`stopping`)
- Modify: `src/main/java/com/frozendawn/shepherd/ShepherdTalk.java:270` (`replay`)
- Modify: `src/gameTest/java/com/frozendawn/shepherd/ShepherdTestWorld.java` (`clear`)
- Modify: `config/architect-required-tests.txt`
- Test: `src/gameTest/java/com/frozendawn/shepherd/ShepherdFeelingsGameTest.java`

**Interfaces:** Consumes Task 4. `saw` calls `MillerPattern.appraise(server, player.getUUID(), deed)` right after the score moves and before `ShepherdTalk.live()` returns. `tick` calls `MillerPattern.poll(server, near.size(), data.lastTalk())` after the empty-`near` return, so it runs only when she can see someone, and the closed-form read covers the time between.

- [ ] **Step 1: Write the failing GameTests.**
  - `shepherdADeedSheSawMovesHerFeelings`: `saw(WARM_SHELTER)` raises joy, and the score moves by exactly what it did before.
  - `shepherdADeedOutOfRangeFeelsNothing`: at 17 blocks, the snapshot is unchanged (Causal).
  - `shepherdAnOfflineUnitFeelsNothing`.
  - `shepherdADeedOnCooldownFeelsNothing`: the second `saw` adds no history.
  - `shepherdADeedDuringATalkStillFeels`.
  - `shepherdAPlayersDeathIsNotAnEvent`: a player beside her is killed; feelings, drives and history are unchanged.
  - `shepherdThePollFeedsCompany`: with a player near, Company is lower after two polls.
  - `shepherdFeelingsSurviveAReload`: `PatternSavedData` save and load through real `registries`, as `shepherdACooldownSurvivesAReload` does.
  - `shepherdReplayClearsHerFeelings`.
  - `shepherdASkippedDayAgesHerFeelings`.
- [ ] **Step 2: Run them.** `./gradlew gameTestQuick -PfdTests=shepherdadeed,shepherdanoffline,shepherdaplayers,shepherdthepoll,shepherdfeelings,shepherdreplayclears,shepherdaskipped --console=plain`. Expected: FAIL.
- [ ] **Step 3: Wire the five call sites,** with `MillerPattern.reset` in `ShepherdTestWorld.clear`, so no shard inherits a feeling. List the tests in `config/architect-required-tests.txt`.
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: let her feel what comes through the door`.

### Task 6: Her resting face

**Files:**
- Modify: `src/main/java/com/frozendawn/shepherd/ShepherdEntity.java:153` (`rest`, `setMood`), `ShepherdFace.java:69` (`motion`), `src/main/java/com/frozendawn/client/ShepherdRenderer.java:46`
- Test: `src/test/java/com/frozendawn/shepherd/ShepherdFaceTest.java`
- Modify: `src/gameTest/java/com/frozendawn/shepherd/ShepherdAffinityGameTest.java`, `ShepherdVaultGameTest.java`, only where Step 3 says
- Test: `ShepherdFeelingsGameTest.java`

**Interfaces:** `rest()` becomes `setMood(wary ? Mood.RECORDING : MillerPattern.restingMood(getServer()))`. It runs every tick; the read is seven `StrictMath.pow` calls, so no cache. A synced `DATA_RESTING` boolean says the face came from `rest()`: `boolean resting()` on the entity, true after `rest()`, false after any other `setMood` or a reaction. `ShepherdFace.motion(Mood, int age, int seed, boolean resting)` plays idles when `resting` or Neutral; the three-argument form stays as today, so `ShepherdScreen` and a conversation change nothing.

- [ ] **Step 1: Write the failing GameTests.**
  - `shepherdRestsOnWhatSheFeels`: `saw(ARCHIVIST_KILLED)`; after the reaction, her face is Sad.
  - `shepherdTheFeelingFadesToNeutral`: then `skipDay` twice, and her face is Neutral.
  - `shepherdAtWaryShePlaysNoFeeling`: at -300, a deed leaves her on Recording, and the feeling is still recorded.
  - `shepherdAskingStaysThinking`: a raised prompt with joy high shows Thinking.
  - `shepherdATalkKeepsHerFace`: joy high, a conversation shows the line's mood.
  - `shepherdRebootsOntoHerFeeling`.
  - `shepherdIsRestingOnlyWhenSheRests`: `resting()` is true after she settles, false during a reaction and during a talk.
  - JUnit `idlesPlayOnEveryRestingMood`: every mood but Recording and Offline shows every expression and the roll with `resting` true; `noIdlesOnRecordingOrOffline`; `noIdlesOffNeutralWhenNotResting`.
- [ ] **Step 2: Run them.** Expected: FAIL.
- [ ] **Step 3: Change `rest()`, then run every Shepherd test.** `./gradlew gameTestQuick -PfdTests=shepherd --console=plain`. Seven assertions pin Neutral: `ShepherdVaultGameTest.java:570`, `ShepherdAffinityGameTest.java:259, 358, 579, 795, 810, 827`. Those after a flinch or a glad return should still pass, since neither comes through `saw`. Any that follows a deed changes by design: move it to the feeling it now shows, naming the deed in the commit. Change no other test.
- [ ] **Step 4: Run them again.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: rest her face on what she feels`.

### Task 7: Gate, playtest, handback

**Files:**
- Create: `tools/prepare_miller_pattern_playtest.py`
- Modify: `docs/aurora-miller-pattern.md` (Status), `docs/aurora-shepherd.md` (Status)

- [ ] **Step 1: Run the full gate in the background.** `./gradlew architectVerify --console=plain`. If it prints `Waiting for another gate to finish:`, wait; do not cancel. Expected: `BUILD SUCCESSFUL`. Failures outside this branch are reported with evidence from `feat/maeve-director`, not waved through. `architectMonkey` does not apply: no Architect code changes.
- [ ] **Step 2: Branch review.** One subagent on Opus, since this changes save data and a shared system. Brief it with `afec4f8f..HEAD`, this plan, the spec and the rulings. Fix real findings, each with a failing test first; list minors as deferred; re-run the gate if code changed.
- [ ] **Step 3: Write the playtest.** Save `Aurora Feelings Check`, namespace `aurora_feelings_check`, a flat lab reusing `prepare`, `pack_only` and `document_advancements` as `prepare_aurora_affinity_playtest.py` does, with advancements filtered and difficulty preselected. Buttons: one per deed by `/fd shepherd saw`, `[Skip a day]`, `[Why]`, `[State]`, `[Wary]`, `[Neutral score]`. It never overwrites a save.
- [ ] **Step 4: Update the status sections,** with the gate's commit and test count, what is headless-verified and what needs the owner's eyes.
- [ ] **Step 5: Commit.** `docs: record the inner state's handback and its playtest`.
- [ ] **Step 6: Ask before launching.** Then `./gradlew runClientLab -PfdLabWorld='Aurora Feelings Check' --console=plain` in the background, and read the logs while the owner plays. Take the owner's description before explaining anything.
- [ ] **Step 7: Fix what the pass finds, then offer a PR into `feat/maeve-director`.** The owner approves any merge.

## Left for later

M2 onward, as the spec has it: impressions, keepsakes, the self-model, attention, urges, coherence, refusal, the recording, the thinking indicator, the log, the chat bar, intents and the crash. The spec's lints on her lines wait for the first new line.

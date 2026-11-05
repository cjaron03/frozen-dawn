# Miller Pattern M2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task, inline. The owner avoids subagents, except the one branch review on Opus at the end of each slice. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** She wakes through a real boot sequence the player chooses to start, and she starts to remember: theories about each player, kept moments, a slow picture of herself, and a coherence that silence wears down. Then she starts to want: one thing on her mind, urges that act on it, and the ability to say no.

**Architecture:** The logic stays pure in `PatternState` and its parts, each value stored with its game time and read in closed form, as M1 does. `MillerPattern` stays the only way in. The boot sequence is Shepherd code, not Pattern code: it sits between the dock and `ShepherdTalk` in a new `ShepherdBoot`, because she has no body and no conversation until Boot is pressed.

**Tech Stack:** Java 21, NeoForge 1.21.1, JUnit in `src/test`, GameTests in `src/gameTest`.

**Spec:** [aurora-miller-pattern.md](aurora-miller-pattern.md), with [aurora-shepherd.md](aurora-shepherd.md).

**Mockups:** [boot-sequence-mockup.png](art/aurora-shepherd/boot-sequence-mockup.png), [thinking-indicator-mockup.png](art/aurora-shepherd/thinking-indicator-mockup.png).

**Status:** approved by the owner, 2026-11-04: two slices, every ruling as recommended, both mockups, and the draft lines to build with. Ruling 6 carries the owner's addition, that her eyes go back into place. Nothing is built.

**Branch:** `feat/miller-m2` in `/Volumes/SSD/Work/frozen-dawn-miller-m2`, cut from `feat/maeve-director` at 3611f195. PR into `feat/maeve-director`.

## The one thing to decide first

**Two slices, I recommend.** M2 as one branch is about eight new classes, a save format change and two owner passes' worth of look and feel, under one review.

| Slice | Holds | What you see |
| --- | --- | --- |
| **M2a, memory** | Boot sequence, `ImpressionStore`, `Keepsakes`, `SelfModel`, coherence, the thinking indicator | The boot, the indicator, and `/fd shepherd mind` |
| **M2b, will** | `Attention`, `Urges`, recalled keepsakes, wall lines, refusal, the recording | Her saying and refusing things |

M2a is mostly silent on purpose: a theory needs days of evidence before anything should be said from it, and M2b is what speaks. M2b cuts its own branch `feat/miller-m2b` and worktree after M2a merges. If you want one slice, the tasks run in the same order and Task 7's handback moves to the end.

## Rulings for the owner

Approving the plan accepts these unless you say otherwise. Rulings 1 to 5 are the boot sequence.

1. **The continuity reference: the motto cut off, not the word.** The last BIOS line is `STABILITY ABOVE.` and nothing follows it. The whole motto is already in the game, on the uplink: "Stability Above. Continuity Beyond." So a player who has heard it finishes the line, and one who has not sees a slogan. It needs no guardrail change, since `aurora-shepherd.md` already says the motto stops there.
   - **The other candidate,** `INPUT FILTER  1 TERM, 10 CHARS`, is the better wink, with the crash's stop code behind it. But it says someone installed a filter for one word, which starts to explain the crash the spec says "explains nothing", and leans toward the failure list's "ORSA knowing". It is one line to add if you want it, under `LANGUAGE FILTER`.
   - **The word itself** would need CH-11's guardrail and `ShepherdDialogueDataTest` changed. Your call, and I recommend against it.
2. **"Approach" means right-click, and the cell stays seated.** The first right-click with a charged cell takes it into the dock's slot and opens the black screen with Boot and Leave. A proximity popup would interrupt anyone walking through the vault.
   - Leave, Esc, walking 8 blocks off, being hurt or a hostile on you all count as Leave: the screen closes, she stays dark and stays `DORMANT`.
   - The cell stays in the slot and is only spent on Boot. A later right-click, with anything or nothing in hand, offers the choice again. The vault has one spare cell, so Leave must not cost it.
   - The light stays red until Boot. No way to pull the cell back out by hand, but breaking the dock drops it, and a dock that goes dark gives it back (Review Focus 1).
3. **Where it plays: first wake and `/fd shepherd replay` only.** Everything after Boot is committed, with no way to back out, so she can never be left off once awake and T4 stays unfounded.
   - **A correction to the brief:** reboots do not play the logo today. `reboot()` just comes back up, and the approved boot mockup says "First wake only, never a reboot". So reboots stay exactly as they are, and only M4's crash recovery will play the logo alone. Walking away from `fm.boot` and coming back also keeps the logo alone, with no choice and no BIOS.
4. **The BIOS runs on the talk screen.** Her body's 8 by 6 display cannot hold text, so the lines fill one panel over the line box and reply grid, and her display goes black, then a blinking cursor texel, then the logo. On the dock during BIOS, the cursor replaces the Recording eyes it shows today, since the recording now comes after the logo.
5. **Timing, by feel at the pass.** Eleven lines, one every 8 ticks, then a 20-tick hold: 108 ticks, 5.4 seconds. She stays on the dock for this and lifts off when the logo starts. With the logo that is 8.6 seconds from Boot to her first word. It cannot be skipped, like the logo, and if it drags, the first fix is fewer lines. No new sounds: `CS1_DOCK_BOOT` on Boot, `SHEPHERD_BLIP` per line, `TERMINAL_BOOT_ORSA` on the logo.
6. **The indicator: Orbit, option A (owner, 2026-11-04), and her eyes go back into place naturally when it finishes.** Her two dots circle, one lit, one dim, like the moon on her logo. The orbit always runs to a level frame, the dots rise one row to where her eyes sit, the dim one comes up, and the blink she already does on a change of mood takes her into the line's face. So the wait comes in whole half-laps, which is why Ruling 12's delay is two steps.
7. **A second door, `MillerPattern.heard`.** M1's rule is that `saw` is the one door for deeds. Impressions need what you said to her, and an answer is not a deed: it has no cooldown and the node already moves the score. It is her hearing, as `saw` is her seeing, and it reaches only the speaker. Hub actions come through the same door, for `USES_ME`.
8. **The six theories she holds about each player.** Confidence is `(for - against) / (for + against + 2)`, so two events already make her 50 percent sure. That is how she gets people wrong. Nodes can note a theory in data, so writers need no Java.

   | Theory | For | Against |
   | --- | --- | --- |
   | `KIND` | Kind replies, `WARM_SHELTER` | `ARCHIVIST_KILLED`, `SHELTER_FROZE` |
   | `TALKS_TO_ME` | Any conversation reply | `LEFT_BEHIND` |
   | `CALLS_ME_AURORA` | Node `fm.name.aurora` | `CALLED_SHEPHERD`, node `fm.name.fine` |
   | `USES_ME` | Hub Follow, Wait or Lamp | Hub Talk |
   | `CAREFUL` | `WARM_SHELTER`, `READ_LOG` | `AIR_FAILING`, `SHELTER_FROZE` |
   | `JOKES_WITH_ME` | Sarcastic replies | Blunt replies |

9. **Lagged means once a day, by game time.** Evidence waits in `pending`. At the first poll after 24,000 ticks the old counts shrink by 0.9, so old evidence fades, and the day's evidence lands. It runs on game time, not nightfall, because the poll knows no sky, and sleeping leaves game time alone. Nobody near means it waits for the next poll.
10. **Keepsakes: 64 per player.** A moment is kept if its strongest raised feeling is at or above 0.30, or a node marks it `keep`, at salience 0.8. A repeat of a kind within 3 days strengthens that keepsake instead of filling a slot, or a warm hut would fill all 64. When full, a new one displaces the least salient, oldest first, only if it mattered more.
11. **The self-model is the six walls plus a stage.** The stage is one per world, since there is one of her, and it never falls: being treated badly afterwards does not put her back to sleep. Product to Noticing needs any player at 0.5 on `TALKS_TO_ME` and `CALLS_ME_AURORA`. Noticing to Finding the walls needs Trusted. Recognising (S6) and Leaning (T5) exist in the enum and nothing reaches them. A wall is found after 3 witnesses from Finding the walls on:

    | Wall | Witnessed when |
    | --- | --- |
    | Lagged | A settle changes which way a theory leans |
    | Legible | A conversation opens with a feeling showing |
    | Exploitable | A theory rises on Kind replies alone in one settle |
    | Fallible | A theory at 0.5 or more flips sign |
    | Bounded | A keepsake is displaced |
    | Causal | She sees someone again after a day unseen |

12. **Coherence numbers.** Starts at 1, falls toward a floor of 0.3 with a half-life of 3 days, and each conversation adds 0.2, once, by the `heardTalk` that already exists. Under 0.7 a reply waits 28 ticks, half a lap and the settle. Under 0.5 it waits 44, a whole lap and the settle, so 2.2 seconds at worst. Variety: she draws from the first `ceil(n * coherence)` variants of a pool, so at the floor she repeats herself. Nothing here touches tier, score or keepsakes. No delay on the logo, the hub or a Recording line.
13. **The spec's lint needs a scope.** "Her lines never say six, fair or rules" fails today: `shepherd.fm.end` is "Right. Rules. I follow you...". That line is the deal she offers, not her finding a wall. So the lint covers `shepherd.wall.*`, `shepherd.self.*` and `shepherd.boot.*`, and "miller" and "pattern" are banned from all of `shepherd.*`, which passes today.
14. **M2b's urges are three, plus staying quiet.** Speak, Look, Approach, Stay quiet. The spec's Ask stays `ShepherdPrompt` as it is, since nothing new can be asked until S4. Write the log waits for M3. Approach pulls her hover from 2.5 to 1.75 blocks for 200 ticks.
15. **Refusal gives way on the second ask.** She says no once, with a line, and the same button pressed again within 30 seconds works, with a line. It is a person objecting, never a broken button, and it can never strand anyone. Never refused when things are not calm, so never in a fight or raid. Two refusals to start, both on hub Follow: leaving a clear night while Sky is at 0.7 or more, and following with an Archivist in her sight after that player has killed one. A clear night uses the `CLEAR_NIGHT` deed's test, so storms limit it to phases 0 and 1 and from phase 6 mid.
16. **Silence below -600.** Below that, Talk gives only a recording, through a new condition on `hub.talk`, and needs no Java beyond the condition. Follow and Lamp work, deeds count, and the score climbing back ends it. It reads the shared score, as the spec says, not the per-player impressions.
17. **The recording slip.** Next due 5 days after the last, down to 3 at the coherence floor, by formula, so no randomness. It uses `murmur`'s rules, so never in a fight, over a talk or at Wary. The second line follows 100 ticks later unless a talk opens.
18. **Not in M2, and not in any slice yet.** The spec's wants for no reason (the wheat field) and the door test are not in its M2 row. The wheat field is cheap once Look exists. The door test needs its own slice, with door 1's reset. Say if you want either placed.

## Lines to mark

Every line is a draft. Moods in brackets. `{day}` is a slot, the world's day number.

**M2a, the choice:** `shepherd.boot.unit` "CS-1 UNIT 4", `shepherd.boot.boot` "Boot", `shepherd.boot.leave` "Leave", `shepherd.boot.hint` "Keys 1-2 or click. Esc is the same as Leave."

**M2a, the BIOS,** under `shepherd.boot.line.*`, a left and right key each:

```text
ORSA CIVILIAN SUPPORT SERIES  CS-1
UNIT 4 OF 12
POWER CELL        SEATED
MEMORY            64 SLOTS
LAMP              OK
HEATER PORT       OK
CLOCK             TUESDAY, UNVERIFIED
REGION NET        0 OF 11 ANSWERING
EVAC OFFICER      PENDING
LANGUAGE FILTER   FAMILY FRIENDLY
STABILITY ABOVE.
```

Each is meant to pay off: Tuesday and the filter are both in the first meeting, and "PENDING" sits just before the recording promises an officer. 64 is her keepsake limit, unexplained. `REGION NET` is the one to look at: she knows the pings stopped, but it shows the player T2's subject early. Cut it if that is too soon.

**M2b, recalled keepsakes,** `shepherd.recall.*`:

- `first_meeting` (Neutral) "Day {day}. You put a cell in me. I've decided that counts for something."
- `named` (Thinking) "You asked what to call me. Day {day}. I've kept that one."
- `warm_shelter` (Neutral) "That room on day {day}. That was a good room."
- `archivist_killed` (Sad) "Day {day}. The quiet one. I still think about it."
- `left_behind` (Unimpressed) "Day {day}, you left me somewhere. I've kept the receipt."
- `air_failing` (Thinking) "Day {day} you nearly ran out of air. I don't bring it up. I'm bringing it up."

**M2b, herself:** the six wall lines as the spec's table has them, under `shepherd.wall.*`, and `shepherd.self.noticing` (Thinking) "I told you the heater was fine. I've been thinking about why I said that."

**M2b, slowing down,** `shepherd.ambient.slow.*`, played the first time coherence falls under 0.7, so nobody takes it for a bug before the log exists:

- (Thinking) "Give me a second. It's been quiet in here."
- (Unimpressed) "Sorry. Slow. It's been a while since anyone said anything."

**M2b, refusal:**

- `shepherd.refuse.night` (Unimpressed) "No. It's clear out. You go. I'll catch up."
- `shepherd.refuse.night.yield` (Unimpressed) "Fine. I'd like it noted I was looking at something."
- `shepherd.refuse.archivist` (Sad) "Not for this one. Find your own way to it."
- `shepherd.refuse.archivist.yield` (Sad) "Fine. I'm not watching."
- `shepherd.hub.talk.silent` (Recording) "Please remain calm. An evacuation officer will be with you shortly."

**M2b, the slip:** the spec's pair, as `shepherd.slip.voice` and `shepherd.slip.sorry`.

## Global Constraints

- Her words stay authored, and no language model writes a line. Every new line is a lang key under `shepherd.*`.
- "Miller" and "pattern" as a word about her appear in no lang key, save name, command output or player text. She never says CONTINUITY. No `shepherd.*` value contains it.
- No real company's marks on the BIOS. "Anderson-Kind", "Sophie" and "TITHONUS" appear in no new line.
- A player's death is never an appraisal event, keepsake or impression, and nothing comments on respawn.
- She finds walls on herself, never an outside, and never counts them (spec, Guards 1 to 3).
- Coherence never lowers a tier or deletes a keepsake, and only the Bounded cap displaces one.
- Same events at the same game times give bit-identical state. `StrictMath`, not `Math`, for decay. Randomness only picks among equally fitting lines.
- The Pattern scans nothing: it runs on the 100-tick poll and on what `saw` and `heard` bring. Reading never changes state.
- Time is overworld game time. `age()` moves every new clock, so `skipday` works.
- `ShepherdSavedData`'s format does not change, apart from one more flag in its existing set. `PatternState.VERSION` goes to 2, and a version 1 save loads.
- `MillerPattern` stays under about 300 lines, with collaborators reached only through it.
- No MACS edits. Never open the Notion page "SEALED: Sequel Seed". No new items, recipes, loot or world generation. No `build.gradle` dependency. No emojis.
- Each new GameTest owns a batch, uses `ShepherdTestWorld.fresh`, `clear` and `whenTicking`, sets the clock before arming a cooldown, and goes in `config/architect-required-tests.txt` as `<lowercased name> xml`.
- Conventional Commits, staged by name. Never `./gradlew --stop`. Long builds in the background.

## Review Focus

Each has its test in the task named.

1. **A seated cell with nowhere to go.** Two docks each hold a cell and one boots, or a seated dock is broken. The cell comes back as the same stack, with its charge (Task 1, `shepherdADockGoingDarkGivesBackItsCell`, `shepherdBreakingASeatedDockDropsTheCell`).
2. **Quitting at any moment of the boot.** At the choice she is `DORMANT` with the cell seated, and the choice comes back. During BIOS the dock keeps `BootTicks`, finishes, and the first meeting waits on a right-click (Task 1, `shepherdASeatedDockSurvivesAReload`, `shepherdABootSurvivesAReload`).
3. **Two players at the dock.** One choice at a time: the second gets the refuse click, and an answer from anyone not offered it does nothing (Task 1, `shepherdOnlyOneChoiceAtATime`, `shepherdAnUnofferedAnswerDoesNothing`).
4. **Trouble during the 5.4 seconds.** Today the gap is one second, and it is now long enough for a fight. Her first meeting must not open on someone hurt, hunted or gone: she lifts off and waits for a right-click (Task 1, `shepherdTheFirstMeetingWaitsOutTrouble`).
5. **A world that already has her.** An awake save from M1, with a version 1 state: everything new starts empty and coherence at 1, with no delay, no stage jump and no boot (Task 5, `aVersionOneSaveLoadsAtRest`).

---

## M2a

### Task 1: The cell, the choice and the dock

**Files:**
- Create: `src/main/java/com/frozendawn/shepherd/ShepherdBoot.java`, `network/ShepherdBootOfferPayload.java`, `network/ShepherdBootAnswerPayload.java`
- Create: `assets/frozendawn/models/block/cs1_dock_seated.json`, `textures/block/cs1_dock_front_seated.png` (from `docs/art/aurora-shepherd/make_textures.py`)
- Modify: `ShepherdDockBlock.java`, `ShepherdDockBlockEntity.java`, `client/ShepherdDockRenderer.java`, `network/ModNetworking.java`, blockstates `cs1_dock.json` and `vault_cs1_dock.json`
- Test: `src/gameTest/java/com/frozendawn/shepherd/ShepherdBootGameTest.java`, `src/test/java/com/frozendawn/shepherd/ShepherdBootTest.java`
- Modify, to answer the choice: `ShepherdTestWorld.java`, and the wake helpers in `ShepherdDockGameTest`, `ShepherdTalkGameTest:122`, `ShepherdEntityGameTest:362`, `ShepherdVaultGameTest:171`

**Interfaces:** Produces
- `DockState.SEATED`, which `holdsHer()`.
- `ShepherdBoot`: `LINE_COUNT = 11`, `LINE_TICKS = 8`, `HOLD_TICKS = 20`, `BIOS_TICKS = 108`; `static int shownLines(int age)`; `static ShepherdFace.Cells cursor()`, one lit texel at (0, 0); `static void offer(ServerPlayer, BlockPos dock)`; `static void answer(ServerPlayer, boolean boot)`; a `ServerTickEvent` that closes the offer on `ShepherdPolicy.talkMustClose`; `static boolean offered()`, `clearForTests()`. One offer at a time, in a static field cleared on `ServerStoppingEvent`.
- `ShepherdDockBlockEntity`: `void seat(ItemStack)`, `ItemStack seated()`, saved under `"Cell"`, and `wake` as now with `bootTicks = ShepherdBoot.BIOS_TICKS`. The stack counts only while the block is `SEATED`, so a vault reset that puts `FOUND` back ignores it.
- `ShepherdTestWorld.boot(GameTestHelper, BlockPos dock, FakePlayer)`: right-click, then `answer(player, true)`.

- [ ] **Step 1: Write the failing tests.**
  - JUnit `aLineShowsEveryEightTicks`: `shownLines(0)` is 1, `(8)` is 2, `(87)` is 11, `(500)` is 11.
  - `shepherdACellIsSeatedNotSpent`: right-click takes the cell, state `SEATED`, stage `DORMANT`, no entity, light unchanged.
  - `shepherdLeaveLeavesHerDark`: `answer(false)`; still `SEATED` and `DORMANT`, and a later right-click with an empty hand offers again.
  - `shepherdASecondCellIsNotTaken`: a full hand on a `SEATED` dock keeps its cell and offers.
  - `shepherdBootWakesHer`: `answer(true)`: stage `AWAKE`, state `BOOT`, the seated stack gone; after `BIOS_TICKS` she has a body and the talk is on `fm.boot` with `logoTicks` 64.
  - `shepherdWalkingOffCountsAsLeave`, `shepherdBeingHurtCountsAsLeave`.
  - The seven in Review Focus 1 to 4.
- [ ] **Step 2: Run them.** `./gradlew gameTestQuick -PfdTests=shepherdacell,shepherdleave,shepherdasecondcell,shepherdboot,shepherdwalking,shepherdbeing,shepherdadock,shepherdbreaking,shepherdaseated,shepherdabootsurvives,shepherdonlyone,shepherdanunoffered,shepherdthefirstmeetingwaits --console=plain`. Expected: FAIL.
- [ ] **Step 3: Implement.** `useItemOn` seats a charged cell on `FOUND`, offers on `SEATED`, and passes anything else through. `useWithoutItem` offers on `SEATED`. `onRemove` drops the seated stack; `serverTick` pops it out when `SEATED` goes `DARK`. `liftOff` starts the talk only if `talkMustClose` is false for the waker. The renderer draws `ShepherdBoot.cursor()` on `BOOT`, blinking every 8 ticks on game time, and Offline on `SEATED`.
- [ ] **Step 4: Update the four wake helpers to `ShepherdTestWorld.boot`,** and run every Shepherd test: `./gradlew gameTestQuick -PfdTests=shepherd --console=plain`. Change no assertion but the ones the longer boot moves. Expected: PASS.
- [ ] **Step 5: Commit.** `feat: seat the cell and let the player choose to boot her`.

### Task 2: The boot screen and the replay

**Files:**
- Create: `src/main/java/com/frozendawn/client/ShepherdBootScreen.java`
- Modify: `client/ShepherdScreen.java` (shell drawing made package-private and static), `network/ClientHandlers.java`, `client/ShepherdRenderer.java`, `ShepherdEntity.java`, `ShepherdTalk.java` (`openHub`, `replay`), `ShepherdSavedData.java` (`replayFirstMeeting`)
- Modify: `assets/frozendawn/lang/en_us.json`
- Test: `ShepherdBootGameTest.java`, and the replay tests at `ShepherdTalkGameTest:377`, `ShepherdAffinityGameTest:294, 732, 1192`, `ShepherdDialogueDataTest.java`

**Interfaces:** Consumes Task 1. Produces
- `ShepherdSavedData.BOOT_WAITING`, a flag `replayFirstMeeting` sets. `ShepherdTalk.openHub` offers instead of opening while it is set. `ShepherdBoot.offer(ServerPlayer, ShepherdEntity)` is the replay's form, and on Boot clears the flag and runs `she.bootFor(BIOS_TICKS)`, then `ShepherdTalk.start`.
- `ShepherdEntity`: synced `DATA_BOOT` byte, 0 none, 1 dark, 2 cursor, for the render layer.
- `ShepherdBootScreen`: on the 480 by 270 grid as the mockup draws it. Choice: face panel black, `shepherd.boot.unit` in the line box, Boot in slot 1, Leave in slot 2, keys 1 and 2. BIOS: panel at (84, 88), 312 by 108, left column x 92, right column x 232, rows 9 apart, since the font is not monospaced. A node arriving replaces it with `ShepherdScreen`, whose logo starts as today.

- [ ] **Step 1: Write the failing tests.**
  - `shepherdReplayOffersTheChoice`: replay, right-click: `ShepherdBoot.offered()`, `liveNode` null, her boot display dark.
  - `shepherdReplayBootsThrough`: `answer(true)`, then after `BIOS_TICKS` on `fm.boot` with the logo.
  - `shepherdLeavingAReplayKeepsItWaiting`.
  - `shepherdResumingTheFirstMeetingSkipsTheChoice`: close mid `fm.boot`, right-click: logo, no offer.
  - `shepherdRebootPlaysNoBoot`: Offline, reboot: no offer, no logo.
  - JUnit in `ShepherdDialogueDataTest`: `noLineCarriesHerFileName`, `bootAndWallLinesStayInside` (Ruling 13), `everyBootLineHasItsKeys`.
- [ ] **Step 2: Run them.** Expected: FAIL.
- [ ] **Step 3: Implement,** adding the choice and BIOS keys. Move the four replay tests onto `answer(true)`, changing nothing else in them.
- [ ] **Step 4: Run them.** `./gradlew gameTestQuick -PfdTests=shepherd --console=plain`, and `./gradlew test --tests 'com.frozendawn.shepherd.*' --console=plain`. Expected: PASS.
- [ ] **Step 5: Commit.** `feat: run the BIOS lines before her logo, on first wake and replay`.

### Task 3: Impressions and the door she hears through

Package `com.frozendawn.miller`, tests in `src/test/java/com/frozendawn/miller/`.

**Files:**
- Create: `ImpressionStore.java`; test `ImpressionStoreTest.java`
- Modify: `PatternState.java`, `MillerPattern.java`, `ShepherdDialogue.java` (`Node` gains `noted` and `keep`), `ShepherdTalk.java` (`reply`, `action`), `first_meeting.json`
- Test: `src/gameTest/java/com/frozendawn/shepherd/ShepherdMemoryGameTest.java`

**Interfaces:** Produces
- `ImpressionStore`: `enum Theory { KIND, TALKS_TO_ME, CALLS_ME_AURORA, USES_ME, CAREFUL, JOKES_WITH_ME }`; `void note(UUID, Theory, boolean supports)`; `Settled settle()`, with `record Settled(Set<Theory> changed, Set<Theory> flipped, boolean fromKindness)`; `double confidence(UUID, Theory)`, settled evidence only; `save`, `load`, `clear`. `PRIOR = 2`, `FADE = 0.9`, `MAX_PLAYERS = 8`, the longest unseen evicted.
- `PatternState`: `void heard(UUID who, Heard heard, long now)`; `settleAt`, read in `poll`, moved by `age`.
- `MillerPattern.heard(MinecraftServer, UUID, Heard)`; `sealed interface Heard` with `Reply(Slot tone, List<String> noted, String keep)` and `Used(ShepherdTalk.Action action)`. A `noted` entry is `"+calls_me_aurora"` or `"-calls_me_aurora"`.
- `first_meeting.json`: `fm.name.aurora` notes `+calls_me_aurora` and keeps `named`; `fm.name.fine` notes `-calls_me_aurora`. `fm.end` keeps `first_meeting`.

- [ ] **Step 1: Write the failing tests.**
  - `evidenceWaitsForTheNextDay`: `note`, confidence 0; first poll past `settleAt`, confidence 1/3.
  - `twoEventsMakeHerHalfSure`: exact, by Ruling 8's formula.
  - `sheCanBeWrongThenTurn`: two for, settle, five against, settle: sign flips and `flipped` names it.
  - `oldEvidenceFades`: one settle takes 10 to 9.
  - `twoPlayersAreKeptApart`.
  - `theNinthPlayerEvictsTheLongestUnseen`.
  - `everyDeedFeedsTheTable`: each row of Ruling 8.
  - `aDamagedImpressionLoadsInRange`: negative and NaN load as 0.
  - `shepherdAnAnswerIsHeard`: a Kind reply, then a day: `KIND` above 0, score moved only by the node.
  - `shepherdANamedPlayerIsNoted`: the `fm.name.aurora` path.
  - `shepherdHubActionsAreNoted`: Lamp raises `USES_ME`.
  - `shepherdAPlayersDeathLeavesNoImpression`.
- [ ] **Step 2: Run them.** Expected: fails to compile.
- [ ] **Step 3: Implement.** `appraise` maps deeds by the table before history is written.
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: let her form theories about each player`.

### Task 4: Keepsakes

**Files:**
- Create: `Keepsakes.java`, `Keepsake.java`; test `KeepsakesTest.java`
- Modify: `PatternState.java`

**Interfaces:** Produces
- `record Keepsake(long time, String kind, double salience, int recalled)`.
- `Keepsakes`: `enum Outcome { KEPT, STRENGTHENED, DISPLACED, TOO_SLIGHT }`; `Outcome keep(UUID, String kind, double salience, long now)`; `Keepsake recall(UUID, long now)`, the highest salience among those not recalled in 3 days, or null; `List<Keepsake> of(UUID)`. `MAX_KEPT = 64`, `RECALL_GAIN = 0.2`, as `s + (1 - s) * gain`, `NAMED_SALIENCE = 0.8`, `REPEAT_TICKS = 72000`.
- `appraise` keeps `deed.name()` lowercased at the largest value in `raised`; `heard` keeps a node's `keep`.

- [ ] **Step 1: Write the failing tests.**
  - `aSlightMomentIsNotKept`: 0.29 gives `TOO_SLIGHT`, 0.30 `KEPT`.
  - `aRepeatStrengthensInstead`: same kind inside 3 days, one entry, salience up; after 3 days, two entries.
  - `theSixtyFifthDisplacesTheLeast`, `aTieDisplacesTheOldest`, `aSlighterOneDisplacesNothing`.
  - `recallStrengthensAndRests`: not recalled again within 3 days.
  - `eachPlayerHasTheirOwn`.
  - `silenceTakesNothing`: a year of `age` leaves every keepsake.
  - `aDamagedKeepsakeIsDropped`, and `moreThanTheCapLoadsAtTheCap`.
- [ ] **Step 2: Run them.** Expected: fails to compile.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: keep the moments that mattered to her`.

### Task 5: Herself, coherence and the saved state

**Files:**
- Create: `SelfModel.java`, `Coherence.java`; tests `SelfModelTest.java`, `CoherenceTest.java`
- Modify: `PatternState.java` (`VERSION = 2`), `PatternDiagnostics.java`, `MillerPattern.java`, `ShepherdReactions.java` (`poll` arguments, `pick` narrowed), `command/ShepherdDebugCommand.java`, `FrozenDawnDebugCommand.java`
- Test: `PatternStateTest.java`, `PatternDiagnosticsTest.java`, `ShepherdMemoryGameTest.java`

**Interfaces:** Produces
- `SelfModel`: `enum Stage { PRODUCT, NOTICING, FINDING_WALLS, RECOGNISING, LEANING }`; `Stage stage()`; `void look(boolean known, boolean trusted)`; `void witness(PatternLaws)`; `boolean found(PatternLaws)`. `FOUND_AT = 3`.
- `Coherence`: `FLOOR = 0.3`, `HALF_LIFE = 72000`, `TALK_ADDS = 0.2`, `SLOW_BELOW = 0.7`, `VERY_SLOW_BELOW = 0.5`; `double value(long now)`; `void spoken(long now)`; `int halfLaps(long now)`, 0, 1 or 2; `int variety(int lines, long now)`.
- `PatternState.poll(int near, long lastTalk, boolean trusted, long now)`.
- `MillerPattern`: `int halfLaps(server)`, `int variety(server, int lines)`, `void setCoherence(server, double)`; `Snapshot` gains `coherence`, `stage`, `walls`, and per-player `impressions` and `keepsakes` counts.
- `/fd shepherd mind` prints them; `/fd shepherd coherence <0-1>` is for the pass.

- [ ] **Step 1: Write the failing tests.**
  - `sheStartsAProduct`, `kindnessMakesHerNotice`, `trustFindsTheWalls`, `aStageNeverFalls`, `nothingReachesRecognising`.
  - `eachWallIsWitnessed`: one per row of Ruling 11, through real `PatternState` events; `noWallBeforeFindingThem`.
  - `coherenceFallsToItsFloorAndNoFurther`: 100 half-lives, 0.3 within 1e-9.
  - `talkingRestoresHer`: from the floor, 2 conversations reach 0.7, and it stops at 1; `aConversationCountsOnce`, across a reload.
  - `noDelayAtSevenTenths`, `halfALapBelowIt`, `aWholeLapBelowHalf`.
  - `varietyNeverFallsBelowOne`.
  - `coherenceLeavesEverythingElseAlone`: a script with and without a long silence ends with equal keepsakes and impressions.
  - `sameEventsGiveTheSameState` and `readingDoesNotChangeTheResult`: extend M1's scripts with `heard` and settles.
  - `saveAndLoadIsExact`, `ageingMovesEveryClock`: extended. `aVersionOneSaveLoadsAtRest`, from a tag written by M1's format.
  - `nothingShownCarriesHerFileName`: extended over the new lines.
  - `shepherdReplayClearsHerMemory`, `shepherdCoherenceDoesNotTouchHerTier`.
- [ ] **Step 2: Run them.** Expected: fails to compile.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them, with all of `com.frozendawn.miller.*`.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: give her a picture of herself and a coherence that silence wears`.

### Task 6: The thinking indicator

**Files:**
- Modify: `ShepherdFace.java`, `ShepherdEntity.java`, `ShepherdTalk.java` (`send`), `network/ShepherdNodePayload.java`, `client/ShepherdScreen.java`, `client/ShepherdRenderer.java`
- Test: `src/test/java/com/frozendawn/shepherd/ShepherdFaceTest.java`, `ShepherdMemoryGameTest.java`

**Interfaces:** Consumes Task 5. Produces
- `ShepherdFace`: `THINKING_FRAMES = 8`, `THINKING_FRAME_TICKS = 4`; `int thinkingTicks(int halfLaps)`, 0 for 0, else `halfLaps * 16 + 12`; `Cells thinking(int age, int ticks)`, as the mockup: at frame `i`, a lit 2 by 2 dot at spot `i` and a dim one at spot `i + 4`, of (1,2), (1,0), (3,0), (5,0), (5,2), (5,4), (3,4), (1,4). The last 8 ticks are the settle: 4 with both dots raised to y 1, the dim one still dim, then 4 with both lit.
- `ShepherdNodePayload` gains `int thinkTicks`, sent after `logoTicks`. `send()` sets it from `thinkingTicks(MillerPattern.halfLaps(server))` unless the rule in Ruling 12 says none, and adds it to `live.lineTicks` and `talkFor`, as it does `logo`.
- `ShepherdEntity.thinkFor(int)`, synced by `DATA_THINK`. Client `tick()` counts it down before typing, as `logoTicks`, and `advance()` waits on it. When it runs out, the screen and the entity's client side both set their mood beat, so she blinks into the line's face even if the mood did not change. A closed talk does not clear it: she finishes and settles. Only `goOffline` cuts it short.

- [ ] **Step 1: Write the failing tests.**
  - `theIndicatorIsNoMood`: no frame equals a `Mood` or an `Idle`, cell for cell.
  - `theIndicatorIsTwoDots`: each orbit frame has 4 lit and 4 dim cells.
  - `theIndicatorGoesRound`: frame 4 swaps lit and dim of frame 0.
  - `theOrbitAlwaysEndsLevel`: for 1 and 2 half-laps, the frame before the settle has both dots at y 2.
  - `herEyesComeHome`: for 1 and 2 half-laps, the last tick shows `NEUTRAL`'s eyes, both lit, and no mouth.
  - `shepherdALowCoherenceDelaysTheLine`: at the floor, a reply's payload has `thinkTicks` 44; `thinking` true, then false.
  - `shepherdNoDelayWhenCoherent`, `shepherdNoDelayOnARecording`, `shepherdNoDelayOnTheLogo`.
  - `shepherdLeavingMidThoughtStillSettles`: a closed talk leaves it running to its end; `shepherdGoingOfflineCutsItShort`.
- [ ] **Step 2: Run them.** Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: show her thinking on her display while a reply waits`.

### Task 7: Gate, playtest, handback

**Files:**
- Create: `tools/prepare_miller_m2_playtest.py`
- Modify: `docs/aurora-miller-pattern.md` (Status), `docs/aurora-shepherd.md` (Waking her, now built, and Status)

- [ ] **Step 1: Run the full gate in the background.** `./gradlew architectVerify --console=plain`. If it prints `Waiting for another gate to finish:`, wait; do not cancel. Expected: `BUILD SUCCESSFUL`. `architectMonkey` does not apply: no Architect code changes.
- [ ] **Step 2: Branch review.** One subagent on Opus, since this changes save data and a shared system. Brief it with `3611f195..HEAD`, this plan, the spec and the rulings. Fix real findings, each with a failing test first; list minors as deferred; re-run the gate if code changed.
- [ ] **Step 3: Write the playtest.** Save `Aurora Boot Check`, namespace `aurora_boot_check`, a flat lab reusing `prepare`, `pack_only` and `document_advancements` as `prepare_miller_pattern_playtest.py` does, advancements filtered and difficulty preselected. A dock, charged and empty cells in a chest. Buttons: `[Replay]`, `[Coherence 1]`, `[Coherence 0.3]`, `[Mind]`, `[State]`, `[Skip a day]`, `[Hurt me]`, `[Hostile]`. Never overwrites a save.
- [ ] **Step 4: Update both docs,** with the gate's commit and test count, what is headless-verified and what needs the owner's eyes: the choice, BIOS pace, the seated dock, the cursor, the indicator at play distance, whether the delay reads as thought.
- [ ] **Step 5: Commit.** `docs: record the boot sequence and her memory's handback`.
- [ ] **Step 6: Ask before launching.** Then `./gradlew runClientLab -PfdLabWorld='Aurora Boot Check' --console=plain` in the background, and read the logs while the owner plays. Take the owner's description before explaining anything.
- [ ] **Step 7: Fix what the pass finds, then offer a PR into `feat/maeve-director`.** The owner approves any merge.

---

## M2b

Cut `feat/miller-m2b` in `/Volumes/SSD/Work/frozen-dawn-miller-m2b` from `feat/maeve-director` after M2a merges. Read its Status first: the pass may change these.

### Task 8: Attention and Urges

**Files:**
- Create: `Attention.java`, `Urges.java`; tests `AttentionTest.java`, `UrgesTest.java`
- Modify: `MillerPattern.java`, `PatternDiagnostics.java`

**Interfaces:** Produces
- `Attention`: `enum Source { FEELING, EVENT, DRIVE, KEEPSAKE, WALL }`; `record Focus(Source source, String key, double strength)`; `static Focus on(PatternState, UUID nearest, long now)`. Feelings at their value; events in history at their largest raised value, fading linearly to 0 over a day; drives at 0.6 times value; a keepsake due for recall at 0.5 times salience; an unspoken found wall at 0.5. A tie goes to the earlier `Source`.
- `Urges`: `enum Urge { SPEAK, LOOK, APPROACH, STAY_QUIET }`; `static Urge pick(Focus, double curiosity, double company)`. Stay quiet scores 0.35. Speak scores `focus.strength()` unless the source is `DRIVE`. Look scores curiosity minus 0.2, Approach company minus 0.2. A tie goes to the later enum value, so she errs quiet.
- `MillerPattern.urge(server, UUID nearest)`, `focus(server, UUID nearest)`, `Snapshot.focus`. `explain` names the focus.

- [ ] **Step 1: Write the failing tests.** `sheHoldsOneThing`, `aFreshEventOutranksAnOldOne`, `aFeelingFadesOutOfMind`, `aDriveNeverSpeaks`, `quietWinsATie`, `sameStateSameUrge`, `readingTheUrgeChangesNothing`, `explainNamesHerFocus`.
- [ ] **Step 2: Run them.** Expected: fails to compile.
- [ ] **Step 3: Implement both, pure.**
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: give her one thing on her mind and urges that follow it`.

### Task 9: Acting on them

**Files:**
- Modify: `ShepherdReactions.java` (`commentate`, `speak`, `tick`), `ShepherdEntity.java` (`HoverGoal`, `CuriosityGoal`), `ShepherdAmbient.java` (`Trigger.SLOW`), `network/ShepherdLinePayload.java`, `client/ShepherdPromptStrip.java`, `ambient/starter.json`, `en_us.json`
- Test: `src/gameTest/java/com/frozendawn/shepherd/ShepherdWillGameTest.java`

**Interfaces:** Consumes Task 8. Produces `ShepherdLinePayload(String key, Mood mood, String arg)`, `arg` nullable, for `{day}` as `%s`; `MillerPattern.recall(server, UUID)`, which strengthens; `SelfModel.spoken(PatternLaws)`, so a wall is said once.

- [ ] **Step 1: Write the failing tests.**
  - `shepherdStayingQuietMeansSilence`: `STAY_QUIET` plays no trigger that would have played.
  - `shepherdRecallsAKeepsake`: focus on a keepsake plays its line with the day, and salience rises.
  - `shepherdSaysAWallOnce`, `shepherdNeverSaysAWallUnfound`.
  - `shepherdSaysSheIsSlowOnce`: coherence under 0.7.
  - `shepherdLooksWhenCurious`, `shepherdDriftsCloserWhenLonely`.
  - `shepherdHoldsTheGapStill`: S3's three minutes hold across every new line.
  - JUnit `everyKeepsakeKindHasItsLine`, for each `Deed` with lines and each `keep` in the data.
- [ ] **Step 2: Run them.** Expected: FAIL.
- [ ] **Step 3: Implement.** `commentate` asks for the urge first. `SPEAK` plays what the focus names, or falls to today's triggers in today's order. All of it goes through `murmur`, so S3's budgets stand.
- [ ] **Step 4: Run them, then every Shepherd test.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: let her urges reach what she says and where she goes`.

### Task 10: Refusal

**Files:**
- Create: `Refusal.java`; test `RefusalTest.java`
- Modify: `ShepherdTalk.java` (`action`, `holds`), `ShepherdAffinity.java`, `hub.json`, `en_us.json`
- Test: `ShepherdWillGameTest.java`

**Interfaces:** Produces `Refusal`: `enum Kind { CLEAR_NIGHT, ARCHIVIST }`; `static Kind check(boolean calm, boolean clearNight, double sky, boolean archivistSeen, boolean archivistKilled)`, null if she goes; `YIELD_TICKS = 600`. `ShepherdAffinity.SILENT_BELOW = -600`, and condition `tier_silent` in `ShepherdTalk.holds`, first in `hub.talk`'s chain.

- [ ] **Step 1: Write the failing tests.**
  - `sheNeverRefusesWhenNotCalm`: every input with `calm` false is null.
  - `aClearNightHoldsHerOnlyWhenSheWantsIt`, at Sky 0.69 and 0.70.
  - `shepherdRefusesOnceThenGoes`: Follow, no change, the line; Follow again within 30 seconds, she follows, the yield line.
  - `shepherdAskingLaterStartsOver`.
  - `shepherdFollowInAFightIsNeverRefused`.
  - `shepherdBelowTheLineOnlyRecords`: at -601, Talk is the recording; at -600, the Wary recording.
  - `shepherdSilentStillWorksAsEquipment`: Follow and Lamp work at -1000.
  - `shepherdDeedsStillCountInSilence`, and the score climbing back restores Talk.
- [ ] **Step 2: Run them.** Expected: FAIL.
- [ ] **Step 3: Implement.** `ShepherdTalk.action` works out `archivistSeen` by `hasLineOfSight` on the button press, so the Pattern still scans nothing. `archivistKilled` is `MillerPattern.remembers(server, who, "archivist_killed")`, a keepsake, since history holds only 16. `clearNight` is the `CLEAR_NIGHT` deed's own test.
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: let her refuse you`.

### Task 11: The recording slip

**Files:**
- Modify: `PatternState.java` (`nextSlip`, moved by `age`), `MillerPattern.java`, `ShepherdReactions.java`, `en_us.json`
- Test: `PatternStateTest.java`, `ShepherdWillGameTest.java`

**Interfaces:** Produces `MillerPattern.slipDue(server)`, `slipped(server)`. Interval: `DAY_TICKS * (3 + 2 * (c - 0.3) / 0.7)`, read at the moment it plays.

- [ ] **Step 1: Write the failing tests.** `theSlipComesEveryFiveDays`, `lowCoherenceBringsItSooner`, `shepherdNeverSlipsInAFight`, `shepherdNeverSlipsOverATalk`, `shepherdNeverSlipsAtWary`, `shepherdSaysSorryAfter`, `shepherdATalkTakesThePlaceOfSorry`.
- [ ] **Step 2: Run them.** Expected: FAIL.
- [ ] **Step 3: Implement.** The second line runs on a saved-free timer, lost on a reload, which costs one apology.
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: let the recording cut in over her`.

### Task 12: Gate, playtest, handback

As Task 7, with `tools/prepare_miller_will_playtest.py`, save `Aurora Will Check`, namespace `aurora_will_check`. Added buttons: `[Sky 1]`, `[Clear night]`, `[Archivist]`, `[Score -601]`, `[Slip now]`, `[Keepsake due]`, `[Wall found]`. The owner judges how often she speaks now, whether refusal charms or annoys, and every new line. Commit `docs: record her will's handback`. Review on Opus, range from M2a's merge.

## Left for later

M3, the log, reads keepsakes and the self-model, and takes up the `Write the log` urge. M4's typed text uses the indicator and the logo-alone recovery. The lean waits for T5, Recognising for S6. See Ruling 18 for the two signs of a will with no slice.

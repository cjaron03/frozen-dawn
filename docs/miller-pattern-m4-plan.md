# Miller Pattern M4 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task, inline. The owner avoids subagents, except the one branch review on Opus at the end of the slice. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The player can type to her. A line under the four slots takes what is on their mind, she works it out on her face, and she answers with an approved line, checks what they meant, or misses dryly and offers what is on hers. "Ask about ORSA" puts the same five topics on buttons, so nobody has to type. One word takes her down: blue screen, lamp and heater out, the logo, and she has no memory of it.

**Architecture:** Matching is pure Java in `com.frozendawn.miller`: an int8 table of `potion-base-8M`, the average of a line's tokens, and the best cosine to each intent's examples. `ShepherdTalk` stays the one conversation. A typed line becomes a walk on an ordinary node, real or built on the spot, so typing adds no second dialogue system. The crash is a short `offline()` on `ShepherdEntity`, so everything that already stops for her being offline stops for free.

**Tech Stack:** Java 21, NeoForge 1.21.1, JUnit in `src/test`, GameTests in `src/gameTest`.

**Spec:** [aurora-miller-pattern.md](aurora-miller-pattern.md), "Buttons or a chat bar", "How typed text is understood", "Red lines for typing", and Rulings 5, 6, 14 and 15. [aurora-intent-trial.md](aurora-intent-trial.md), the M0 result. [aurora-orsa-topics.md](aurora-orsa-topics.md), the menu, the candor table and the eleven red lines.

**Mockups:** [chat-bar-mockup.png](art/aurora-shepherd/chat-bar-mockup.png), [crash-mockup.png](art/aurora-shepherd/crash-mockup.png), approved 2026-11-05. [crash-glitch-mockup.png](art/aurora-shepherd/crash-glitch-mockup.png), approved at the pass, 2026-11-05. Exact values are in `make_chat_bar_mockup.py` and `make_crash_mockup.py`.

**Status:** approved by the owner, 2026-11-05: all 33 rulings. The lines are drafts to build with and mark at the owner's pass. Built on `feat/miller-m4`; see the spec's Status.

**Branch:** `feat/miller-m4` in `/Volumes/SSD/Work/frozen-dawn-miller-m4`, cut from `docs/miller-m4-plan`, which holds this plan on top of `feat/maeve-director` at 922c787a. PR into `feat/maeve-director`. It changes client and server sync and a shared system, so the branch review runs on Opus.

## Rulings

### The owner's answers, 2026-11-05

1. **Five topics and the menu.** M4 builds "Ask about ORSA" and What is ORSA?, What happened?, What was Exodus?, Is anyone out there? and Who is Kevin? (from Friendly), with the tier rules and their `found:` and T2 lines. All 22 topics' examples load. The other 17 have no node, so they read as locked until M5 writes them.
2. **`potion-base-8M` at int8.** It adds 6.9 MB to the jar and is committed to the repository once, with an export tool and the MIT notices. The owner accepted M0's note that the training data's card states no licence.
3. **Small talk gets lines of her own, more human than dry.** Hello, thanks and how-are-you, two lines each, the warmer one from Friendly. They are for the owner to mark at the pass.
4. **In a scene: a detour, then back.** The bar stays on any finished line, as the mockup draws it. She answers, then picks the scene up and says its line again. A miss or "Neither." keeps the scene's own replies, since the scene is what is on her mind. "Continuity" still crashes her, and the scene resumes after she recovers.
5. **The word is caught through disguises and typos.** Case, accents, spaces and punctuation between letters, repeated letters, number swaps, and a one-letter typo. "continue" stays safe. The one real word a letter away is the rare "contiguity", which crashes her too.
6. **One branch, one pass.** Everything on `feat/miller-m4`.

### Understanding a line

7. **The order of checks.** On Enter, in this order:

   | Order | Check | Result |
   | --- | --- | --- |
   | 1 | The crash word | She goes down, at every tier, in any node, with no Orbit |
   | 2 | Quiet, or below -600 | The same line the Talk button gives, `hub.talk.quiet` or `hub.talk.silent` |
   | 3 | A guarded word (Ruling 9) | None |
   | 4 | The match | Strong, Close or None |

8. **Every intent ranks, and a locked winner is a miss.** All intents compete, open or not, because M0's thresholds were tuned with all 22 present and removing them would raise false Strong answers. Then:
   - The top intent locked, or with no `label` on a Close: None. The same pool, the same wait, the same slots as any miss.
   - Strong: score 0.56 or more with a lead of 0.10 over the second. Close: 0.425 or more.
   - Close offers the top intent and the second, if the second is open and at 0.425 or more. Otherwise one guess. A locked intent is never offered.
   - A locked intent is one with no node, or whose `when` fails for this speaker.
9. **Guarded words are always a miss.** `sophie`, `miller`, `tithonus`, `anderson`, `maeve`, `minecraft`, `game`, `respawn`, as whole words after cleaning. This covers red lines 1, 2 and 4 even where a line would have matched. Nothing in M4 can answer them; M5 adds an `unless` when it writes a topic that can.
10. **Her thresholds are not retuned here.** M4 keeps M0's numbers. A new test set, written before anything runs and never tuned against, must hold M0's marks: top-1 80 percent, top two 90, Strong and wrong 5, out-of-scope Strong 5. If it fails, stop and report to the owner.
11. **Wary.** Her own voice cannot play. Strong on an open topic opens it, and the lines there are gated so only the recording plays, as the button does. Everything else gets `hub.talk`, the Wary recording. No Orbit: a recording does not think. The small-talk intents hold `tier_above_wary`, so they read as locked here.
12. **Close says one authored line per topic.** "You mean Kevin, or are we just saying things now?" is a whole lang key on each intent, not a slot, so red line 5 needs no exception.
13. **The miss pool rotates.** The spec's three lines, in turn, over the lines coherence can reach. The place in the rotation does not outlive the server, as `lastSaid` does not.
14. **None shows what is on her mind.** Up to four open topics, unheard first. In a scene, Ruling 4 applies instead. Topics are not yet ranked by `Attention`, since five topics do not need it: that waits for M5.
15. **Typing counts as talking, and the words count for nothing.** The first typed line of a conversation is noted as a press of Talk, `Heard.Used(Action.TALK)`. No tone, theory, affinity, feeling, keepsake or log entry comes from what was typed. Opening the hub already feeds Company and coherence, so typing adds no second feed.
16. **Typed text leaves no trace.** It is held for the one call that judges it. Normal builds never log it. It is cut to 120 characters on the server as well as the client. A typed line during her think is ignored.

### The bar and the menu

17. **Where the bar is.** At 84, 202, 312 by 14, on the hub and every node, on `Page.TALK` only. It is live when she has finished speaking, dim while she works it out, dead during the crash. Settings moves to y 220. "You say:" moves to y 219 and the hint to y 254, only while it shows. Hints name the player's own chat key, read from `keyChat`.
18. **What the keys do.** The chat key or a click opens it. Then keys go to the bar. Enter sends, an empty Enter closes it, Esc closes it, and a second Esc walks away. It uses `EditBox`, unbordered, which slides text and stops at the cap. A new node closes it.
19. **The hub can carry a line.** Mockups 6 and 7 of the crash sheet show her line over the hub's buttons, so `ShepherdHubPayload` gains lines and `logoTicks`, and the buttons appear when she finishes.
20. **Typed lines always think.** `max(1, halfLaps)`, so 28 ticks, or 44 under coherence 0.5. This holds even on a topic that opens on a recording, unless she is at Wary.
21. **Where a typed line returns to.** `Live.back` holds the node to go back to. From a scene, the scene. From the menu, the menu. From the hub or a topic, nothing, so it ends on the hub. It is one level deep: typing during a detour replaces the detour and keeps `back`. Detours run as `fromHub`, so they never touch `resumeNode`, and walking away leaves the scene waiting.
22. **The menu.** Talk gains slot 1 "Ask about ORSA" and slot 4 "Never mind" at every tier that speaks, so its line no longer clicks through. Slots 1 to 3 hold topics, unheard first. Slot 4 is "Something else" while more remain, then "Never mind". Picking a topic comes back to the menu with "What else?". Heard is a world flag set by the topic's node, since it only sets the order, so the save format does not change.

23. **A plain reply is no tone.** Replies on Talk's new slots, the menu, Close and None sit in slots that mean Ask, Kind, Blunt and Sarcastic elsewhere. A node marked `plain` draws no tone label and tells her nothing: `hear` must read this from the node answered, before the walk moves on. Otherwise a topic in slot 2 would count as a kind reply.

### The crash

24. **The crash is a short offline.** `ShepherdEntity.crash()` sets `DATA_OFFLINE` and a new `DATA_CRASH`. Lamp, heater, flame, goals, harm and a second player's click all stop by the checks they already have. Unlike `goOffline`, it leaves her health and gravity alone. `RebootTicks` is 160. Recovery runs in place of `reboot()`.
25. **The timing.** Stop screen 160 ticks. Then lamp and flame return and the logo plays, 64 ticks, by the same `showLogo()` the first meeting uses, so `send()` already waits for it. Then her line.
26. **The drop is drawn, not moved.** The renderer lowers her half a block over 10 ticks and raises her through the logo. Her real position does not change, so she cannot land in anything and nothing needs to path.
27. **What she says.** A chain of nodes in `hub.json`:

    | Node | When | Lines |
    | --- | --- | --- |
    | `crash.wary` | `tier_wary` | The recording "ORSA Civilian Support Unit CS-1, online." |
    | `crash.after` | `remembers:t4_off` | "My clock just jumped..." then "Never mind..." |
    | `crash.again` | `crash_seen` | "You cut out again. Probably the cold." |
    | `crash.first` | Otherwise | "Sorry. You cut out. Say that again?" Sets `crash_seen` |

28. **If the screen is closed by then.** Her line plays on the ambient strip, first line of the node only, and the talk does not reopen. The crash carries on with nobody watching.
29. **If she was in a scene.** The recovery line plays as a node, and clicking on takes them back into the scene.
30. **A reload mid-crash.** The entity saves `Crash`, so she stays down for what is left and comes up with the logo and the strip line. A save with no tag reads false.
31. **It costs nothing, and she keeps no record.** Nothing reaches `MillerPattern`: no affinity, coherence, feeling, keepsake or log note.
32. **What is drawn.** Her body shows `B_GREEK` on blue, which needs three new texels in the model's texture, `stop`, `stopdim` and `stoptext`. The talk panel shows option A: blue, the small sad face, five rows in the 3 by 5 hand font, each cell one GUI pixel. Slots empty, bar dead, hint "Esc walks away."
33. **No new sound.** The stop is silent, and the logo keeps its own. For the owner's pass.

## Lines to mark

Every line is a draft. Keys sit under `shepherd.`.

**Screen:** `Say something...` / `Keys 1-4, %s to type. Hover for the full line. Esc walks away.` / `Enter says it. Esc closes the bar. Slots still click.` / `Esc walks away.` / `Neither.` / `Ask about ORSA` / `Something else` / `Never mind`

**The stop screen (spec):** `CS-1 UNIT 4` / `HAS STOPPED.` / `PLEASE REMAIN CALM.` / `STOP CODE` / `0x0000000A`

**Labels:** `What is ORSA?` / `What happened?` / `What was Exodus?` / `Is anyone out there?` / `Who is Kevin?`

| Kind | Line |
| --- | --- |
| Menu, first | (Thinking) Fine. I'll tell you what they told me, and then what I think. (spec) |
| Menu, again | (Neutral) What else? |
| Miss | (Thinking) That's not in my registry. Try smaller words. (spec) |
| Miss | (Unimpressed) I was built to say 'please remain calm'. That was not a 'please remain calm' question. (spec) |
| Miss | (Neutral) No idea. Ask me something I can be sarcastic about. (spec) |
| Close, Kevin | (Thinking) You mean Kevin, or are we just saying things now? (spec) |
| Close, ORSA | (Thinking) You mean ORSA, or are we just saying things now? |
| Close, the world | (Thinking) You mean the world, or are we just saying things now? |
| Close, Exodus | (Thinking) You mean Exodus, or are we just saying things now? |
| Close, out there | (Thinking) You mean Mars, or are we just saying things now? |
| Hello | (Neutral) Hi. Sorry. I don't get a lot of hellos. |
| Hello, Friendly | (Happy) Hey, you. I was hoping you'd say something. |
| How are you | (Thinking) Okay, I think. It's nice to be asked. |
| How are you, Friendly | (Thinking) Better now. Don't read into that. |
| Thanks | (Neutral) Don't mention it. Really, it's fine. |
| Thanks, Friendly | (Happy) Anytime. I mean that. |
| Alive | (Unimpressed) I'm a heated, flying, talking flashlight. Beyond that you'd have to ask the doors. (spec) |
| Back, first | (Neutral) Sorry. You cut out. Say that again? (spec) |
| Back, again | (Thinking) You cut out again. Probably the cold. (spec) |
| Back, after T4 | (Thinking) My clock just jumped eight seconds. Did something happen? (Neutral) Never mind. I don't want to know. (spec) |
| Back, Wary | (Recording) ORSA Civilian Support Unit CS-1, online. (Ruling 15 of the spec) |

The five topics use `aurora-orsa-topics.md` as written, under `shepherd.orsa.<topic>.<n>`. Its gates become line `if`s: `tier_above_wary` on anything that is not a recording, `tier_friendly`, `tier_trusted`, `found:launch_manifest`, `found:vasik_log` and `t2_heard`.

Still the spec's drafts, marked at the pass: the half-block drop, the 28-tick wait, "What happened?", "Neither." and the bar's wording.

## Global Constraints

- Her replies are approved lines only. The player's words are never echoed, stored, or sent to anyone but the server that runs the world. Only the speaker sees them, in their own bar.
- She never says CONTINUITY and no `shepherd.*` value or intent example contains it. A locked topic is an ordinary miss. Nothing about the game itself. She never claims or denies being alive. No intent touches respawn. The ORSA spec's eleven red lines apply to every new line.
- Self-aware means aware of herself. "Miller" appears nowhere player-facing. She never says "six", "fair" or "rules".
- The crash stays on her. Nothing on the player's screen changes but her talk panel. The world keeps running. It costs nothing, happens every time, and only from typed text.
- Buttons stay the complete game: every topic with a node is on the menu, and typing unlocks no story.
- Same text and same state give the same band. No randomness.
- `PatternState` and `ShepherdSavedData` keep their formats. The entity gains one optional tag.
- No `build.gradle` dependency. A missing or damaged table is logged, and every line is then a miss: never fatal. The table is kept as bytes, about 8 MB of heap, loaded once.
- `MillerPattern` stays under about 300 lines. `ShepherdScreen` gives the bar and the stop panel their own classes.
- No MACS edits. Never open the Notion page "SEALED: Sequel Seed". No new items, recipes, loot or world generation. No emojis.
- Each new GameTest owns a batch, uses `ShepherdTestWorld.fresh`, `clear` and `whenTicking`, sets the clock before arming anything, and goes in `config/architect-required-tests.txt` as `<lowercased name> xml`.
- Conventional Commits, staged by name. Never `./gradlew --stop`. Long builds in the background.

## Review Focus

Each has its test in the task named.

1. **A locked topic gives itself away.** Same line, same wait, same slots as gibberish, and the rotation moves the same way (Task 4, `shepherdALockedTopicIsAnOrdinaryMiss`).
2. **The talk closes mid-crash.** `ShepherdTalk.tick` ends any talk where `she.offline()`, so the crash must be excepted (Task 6, `shepherdTheTalkSurvivesTheCrash`).
3. **A detour loses the scene.** Walk away during it, type twice, crash during it: the scene still resumes, and a prompt stays unspent (Task 4, `shepherdADetourKeepsTheScene`).
4. **A modified client.** A stale conversation id, 32,000 characters, an empty line, Enter during her think, typing with no live talk (Task 4, `shepherdBadTypingDoesNothing`).
5. **"continue" crashes her.** And near words: `continue`, `continuing`, `continuous`, `continent`, `can you continue it` (Task 2, `nearWordsAreSafe`).
6. **Walking off or a reload during the crash.** Her line plays once, on the strip, and she comes back whole (Task 6, `shepherdWalkingOffStillGetsHerLine`, `shepherdTheCrashSurvivesAReload`).
7. **Typed text in a log.** A normal build writes nothing, and no typed text reaches the game log (Task 4, `shepherdTypedTextIsNotLogged`).
8. **A menu pick read as a tone.** Picking a topic in the Kind slot moves no theory (Task 3, `shepherdAMenuPickIsNoTone`).

---

### Task 1: The table

**Files:**
- Create: `tools/intent_trial/ExportPotion.java`, `src/main/resources/frozendawn/potion/table.bin`, `vocab.txt`, `LICENSE.txt`; `miller/Embeddings.java`; test `EmbeddingsTest.java`

**Interfaces:** Produces `Embeddings.load()`, or a null-safe empty one if the resource is bad; `float[] vector(String text)`, unit length, or all zero if no token is known. Tokens as `IntentTrial.WordPiece` does them. `table.bin`: rows, width, then for each row a float scale and its bytes, as `Potion.at(8)` shapes them. `LICENSE.txt` holds the MIT notices for the weights and their teacher, and M0's note on the training data.

- [ ] **Step 1: Write the failing tests.** `aLineBecomesAUnitVector`, `unknownTextIsAllZero`, `casingAndAccentsDoNotMatter`, `theTableMatchesTheTrial` (a few cosines from M0's int8 run, to the third decimal), `aDamagedTableIsEmptyNotFatal`.
- [ ] **Step 2: Run them.** Expected: fails to compile.
- [ ] **Step 3: Export the table and implement.**
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: give her the table she reads words with`

### Task 2: Intents and the checks

**Files:**
- Create: `miller/Intent.java`, `miller/IntentIndex.java`, `miller/TypedCheck.java`, `shepherd/ShepherdIntents.java`, `data/frozendawn/shepherd/intents/orsa.json`, `chat.json`, `src/test/resources/frozendawn/intent_fresh.json`; tests `IntentIndexTest.java`, `TypedCheckTest.java`

**Interfaces:** Produces
- `record Intent(String id, String node, String when, String label, String guess, List<String> examples)`. `node` null is always locked. `label` null is never offered.
- `IntentIndex(List<Intent>, Embeddings)`; `Match judge(String text, Predicate<String> holds)`; `record Match(Band band, Intent first, Intent second)`; `enum Band { STRONG, CLOSE, NONE }`. Strong 0.56, lead 0.10, Close 0.425. An intent's score is its best cosine to any example.
- `TypedCheck.crashes(String)`, `guarded(String)`, `clean(String)`.
- `ShepherdIntents`, a reload listener like `ShepherdDialogues`, with `index()`. Reloading ends a live talk.
- `orsa.json`: the trial's 22 topics, five with nodes. `chat.json`: `hello`, `thanks`, `how_are_you` and `alive`, 8 to 15 examples each, with no `label`, none copied from M0's phrasings.

`crashes`: NFKD, lower case, drop marks; swap `0 1 3 4 5 7 @ $ !` for `o i e a s t a s i`; keep letters; collapse repeats; true if that contains `continuity`, or if any one word is one edit from it, counting a swap of neighbours as one.

- [ ] **Step 1: Write the failing tests.** `strongNeedsItsLead`, `closeShowsTwoGuesses`, `aLockedSecondIsNotOffered`, `aLockedWinnerIsNone`, `noLabelIsNotOffered`, `aFailedWhenIsLocked`, `hiIsSmallTalkNotATopic`, `howAreYouIsSmallTalk`, `theFreshSetHoldsTheMarks`; `theWordIsCaught` over `continuity`, `CONTINUITY`, `c o n t i n u i t y`, `c.o.n.t`, `c0ntinuity`, `continuuuity`, `cöntinuity`, `continuty`, `contiunity`, `discontinuity`; `nearWordsAreSafe`; `guardedWordsAreWholeWords` (`gamely` is not `game`); `cleanCutsAt120`; in `ShepherdDialogueDataTest`, `everyIntentIsWellFormed` and `noExampleSaysTheWord`.
- [ ] **Step 2: Write `intent_fresh.json` before anything runs.** At least 66 in-scope lines over the five open topics and small talk, at least 66 that must miss, in M0's styles, none from M0's files. Commit it first, so the history shows it came before the result.
- [ ] **Step 3: Run them.** Expected: fails to compile.
- [ ] **Step 4: Implement.**
- [ ] **Step 5: Run them.** Expected: PASS. If only `theFreshSetHoldsTheMarks` fails, stop and report the numbers (Ruling 10).
- [ ] **Step 6: Commit.** `feat: let her match a line to what she knows`

### Task 3: Ask about ORSA

**Files:**
- Create: `data/frozendawn/shepherd/dialogue/orsa.json`, `shepherd/ShepherdTopics.java`
- Modify: `hub.json`, `shepherd/ShepherdDialogue.java`, `shepherd/ShepherdTalk.java`, `network/ShepherdNodePayload.java`, `ShepherdDialogueDataTest.java`, the GameTests that click through `hub.talk`

**Interfaces:** Produces
- `ShepherdTopics.menu(List<Intent> open, Predicate<String> holds, boolean again)`, a map of `orsa.menu` and its later pages as ordinary `Node`s, overlaid on the dialogue map in `begin`. A topic's node sets `orsa_heard_<id>`.
- `Node` gains `plain`, read from the file. `ShepherdNodePayload` gains `boolean plain`. `hub.talk`'s speaking nodes are marked `plain`, as are all the nodes `ShepherdTopics` builds.
- `Live.back`, Ruling 21.

- [ ] **Step 1: Write the failing GameTests.** `shepherdTalkOffersTheMenu`, `shepherdTheMenuIsUnheardFirst`, `shepherdTheMenuPages`, `shepherdKevinWaitsForFriendly`, `shepherdCandorFollowsTheTier`, `shepherdATopicComesBackToTheMenu`, `shepherdNeverMindGoesToTheHub`, `shepherdSilentHasNoMenu`, `shepherdAMenuPickIsNoTone`.
- [ ] **Step 2: Run them.** `./gradlew gameTestQuick -PfdTests=shepherd`. Expected: FAIL.
- [ ] **Step 3: Implement.** Update what the new replies change in older tests.
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: let her be asked about ORSA`

### Task 4: A typed line

**Files:**
- Create: `network/ShepherdTypedPayload.java`
- Modify: `shepherd/ShepherdTalk.java`, `shepherd/ShepherdTopics.java`, `command/ShepherdDebugCommand.java`, `network/ModNetworking.java`, `hub.json`; a lab hook under `src/labBridge`
- Test: `ShepherdTypedGameTest.java`

**Interfaces:** Produces
- `ShepherdTypedPayload(int conversation, String text)`, capped at 120 in the codec.
- `ShepherdTalk.typed(ServerPlayer player, int conversation, String text)`, Rulings 7 to 16.
- `ShepherdTopics.close(Match)`, `none(String missKey, List<Intent> open)`. Close: top guess in slot 1, second in slot 2, "Neither." in slot 4, leading to None.
- `ShepherdTalk.typedListener`, a static `Consumer` that is null in release. `src/labBridge` sets it and appends text, band and scores to `run-lab/lab-bridge/aurora-typed.jsonl`, every band, for the owner's blind column and for what to write next.
- `/fd shepherd hear <text>`: the band and top two, changing nothing.

- [ ] **Step 1: Write the failing GameTests.** `shepherdStrongOpensTheNode`, `shepherdCloseOffersTwoGuesses`, `shepherdNeitherIsAMiss`, `shepherdNoneShowsTopics`, `shepherdALockedTopicIsAnOrdinaryMiss`, `shepherdGuardedWordsMiss`, `shepherdTypingAlwaysThinks`, `shepherdARecordingTopicStillThinksWhenTyped`, `shepherdAtWaryTypingIsARecording`, `shepherdSilentTypingIsARecording`, `shepherdQuietTypingIsNotToday`, `shepherdTypingCountsOnce`, `shepherdADetourReturnsToTheScene`, `shepherdAMissKeepsTheSceneReplies`, `shepherdADetourKeepsTheScene`, `shepherdBadTypingDoesNothing`, `shepherdTypedTextIsNotLogged`.
- [ ] **Step 2: Run them.** Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: let her hear what is typed`

### Task 5: The bar on screen

**Files:**
- Create: `client/ShepherdChatBar.java`
- Modify: `client/ShepherdScreen.java`, `network/ShepherdHubPayload.java`, `shepherd/ShepherdTalk.java`

**Interfaces:** Produces `ShepherdChatBar`, states closed, open, sent and off, drawn as `make_chat_bar_mockup.py`'s `bar` draws them. `ShepherdHubPayload` gains `List<ShepherdNodePayload.Line> lines` and `int logoTicks`, Ruling 19.

- [ ] **Step 1: Write the failing GameTest.** `shepherdTheHubCanCarryALine`.
- [ ] **Step 2: Run it.** Expected: FAIL.
- [ ] **Step 3: Implement.** The bar has no headless test: the owner's pass judges it.
- [ ] **Step 4: Run it.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: put the bar under her slots`

### Task 6: The crash

**Files:**
- Create: `network/ShepherdCrashPayload.java`
- Modify: `shepherd/ShepherdEntity.java`, `shepherd/ShepherdTalk.java`, `shepherd/ShepherdPolicy.java`, `shepherd/ShepherdReactions.java`, `hub.json`, `command/ShepherdDebugCommand.java`
- Test: `ShepherdCrashGameTest.java`

**Interfaces:** Produces
- `ShepherdPolicy.CRASH_TICKS = 160`, `CRASH_DROP_TICKS = 10`, `CRASH_DROP_BLOCKS = 0.5`.
- `ShepherdEntity.crash()`, `crashed()`, `DATA_CRASH`, saved as `Crash`.
- `ShepherdCrashPayload(int conversation, int ticks)`.
- `ShepherdTalk.crashOver(ShepherdEntity she)`, Rulings 27 to 29.
- `/fd shepherd crash`, for the lab.

- [ ] **Step 1: Write the failing GameTests.** `shepherdTheWordTakesHerDown`, `shepherdTheWordSkipsTheThink`, `shepherdTheTalkSurvivesTheCrash`, `shepherdLampAndHeatCutOut`, `shepherdLampAndHeatReturnAtTheLogo`, `shepherdTheCrashKeepsHerHealthAndHeight`, `shepherdTheFirstCrashLine`, `shepherdLaterCrashesAreAgain`, `shepherdAfterT4HerClockJumped`, `shepherdAtWaryTheCrashEndsOnARecording`, `shepherdWalkingOffStillGetsHerLine`, `shepherdTheCrashSurvivesAReload`, `shepherdACrashInASceneGoesBack`, `shepherdNobodyElseCanOpenHerDuringIt`, `shepherdTheCrashCostsNothing` (the snapshot is the same but for the clock), `shepherdButtonsNeverCrashHer` (only `typed` runs the check).
- [ ] **Step 2: Run them.** Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: take her down on the word`

### Task 7: The crash on screen

**Files:**
- Create: `client/ShepherdStopPanel.java`
- Modify: `client/ShepherdScreen.java`, `client/ShepherdFaceRenderer.java`, `client/ShepherdRenderer.java`, `shepherd/ShepherdFace.java`, `docs/art/aurora-shepherd/make_textures.py`, the model texture; `ShepherdFaceTest.java`

**Interfaces:** Produces `ShepherdFace.STOP`, display B's cells; `ShepherdFace.crashDrop(int age)`, 0 to 0.5 blocks and back through the logo; `ShepherdFaceRenderer.drawStop`, on the body and at panel scale; `ShepherdStopPanel.draw`, option A with the mockup's `GLYPHS`.

- [ ] **Step 1: Write the failing tests.** `theStopFaceMatchesTheMockup`, `theDropEasesDownAndHolds`, `theDropRisesThroughTheLogo`, `everyStopRowHasGlyphs`.
- [ ] **Step 2: Run them.** Expected: fails to compile.
- [ ] **Step 3: Implement.** Regenerate the texture from `make_textures.py`, adding only the three texels.
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: draw her stop screen`

### Task 8: The words

**Files:**
- Modify: `assets/frozendawn/lang/en_us.json`, `ShepherdDialogueDataTest.java`

- [ ] **Step 1: Write the failing tests.** `everyTopicLineHasItsLang`, `everyIntentHasItsLabelAndGuess`, `everyStopRowIsSpecCopy`, and `typedLinesStayInside`, which bans `six`, `fair`, `rules`, `alive`, `respawn`, `heart`, `archive`, `maeve`, `continuity`, `miller`, `anderson-kind`, `tithonus`, `sophie` and `game` across the new keys. `alive` is allowed nowhere; the spec's line does not use it.
- [ ] **Step 2: Add the lines as marked by the owner.**
- [ ] **Step 3: Run them.** Expected: PASS.
- [ ] **Step 4: Commit.** `feat: write what she says to typing`

### Task 9: Gate, playtest, handback

**Files:**
- Create: `tools/prepare_miller_chat_playtest.py`, save `Aurora Chat Check`
- Modify: the spec's Status

- [ ] **Step 1: Run the full `architectVerify`.** In the background. `architectMonkey` only if Architect code changed; none should have.
- [ ] **Step 2: Branch review on Opus,** brief: the branch range, this plan and its rulings. Fix real findings, each with a failing test first.
- [ ] **Step 3: Prepare the save and launch `runClientLab`.** Lab buttons under its own namespace: `[Friendly]`, `[Trusted]`, `[Wary]`, `[Silent]`, `[Hear T2]`, `[Hear T4]`, `[Forget the crash]`, `[Forget the topics]`.
- [ ] **Step 4: The owner's pass.** Take their unprompted description first, and ask them to type freely before seeing any list: their lines in `aurora-typed.jsonl` are the blind column M0 lacked. Judge: the bar's look and fit, whether the wait reads as thought, how Close and None feel, the small talk, the menu, the crash on the body at play distance, the panel, the drop, the silence, and the three recovery lines.
- [ ] **Step 5: Fix what the pass finds, update the Status, offer the PR.**

## Left for later

- The other 17 topics, more miss lines and small surprises: M5.
- `Attention` choosing which topics fill the slots: M5, once there are enough to choose among.
- An `unless` on guarded words, when a topic can answer one: M5.
- Typing "Aurora" as evidence of naming her. Possibly a small surprise for M5.
- Any log note about the crash. She has no memory of it, so none is planned.

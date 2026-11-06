# Miller Pattern M5 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task, inline. The owner avoids subagents, except the one branch review on Opus at the end of the slice. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** She can be asked about all of it. The 17 unwritten ORSA topics get their nodes, so the menu holds everything the topics doc drafted, each opening at its tier and on what the player has found. "Who are you?" gets an answer, misses get more variety, and what is on her mind orders the menu.

**Architecture:** Nearly all data: nodes in `orsa.json`, gates on the intents, text in lang keys. Code changes are small: dialogue conditions can be joined and gain two new kinds, a topic reply pays once, and `ShepherdTopics` orders by her focus. No second dialogue system, and no change to either save format.

**Tech Stack:** Java 21, NeoForge 1.21.1, JUnit in `src/test`, GameTests in `src/gameTest`.

**Spec:** [aurora-miller-pattern.md](aurora-miller-pattern.md), "How typed text is understood", "Red lines for typing" and the Slices table. [aurora-orsa-topics.md](aurora-orsa-topics.md), the topics, the candor table and the eleven red lines. [miller-pattern-m4-plan.md](miller-pattern-m4-plan.md), the intent format and its Rulings 7 to 14.

**Lines:** `docs/aurora-m5-lines.md`, written in Task 1, for the owner to mark before anything is built.

**Mockups:** none. The topics use the existing talk grid, and nothing new is drawn.

**Status:** approved by the owner as written, 2026-11-05, Rulings 1 to 22 included. Rechecked against M4 as shipped, and the owner approved Rulings 23 to 28 and the edits they make the same day. Ruling 29 records the fresh set's result. Tasks 2 to 6 are built; see the Handback. The owner passed M5 on 2026-11-06; see "The owner's pass".

**Branch:** `feat/miller-m5` in `/Volumes/SSD/Work/frozen-dawn-miller-m5`, cut from `docs/miller-m5-plan`, which holds this plan on top of `feat/maeve-director` at 9ccb53f8, with M4 (PR 161) and the worktree rules (PR 162) merged in. The build session starts in this worktree and works there. PR into `feat/maeve-director`. It changes a shared system and merges into a long-lived branch, so the branch review runs on Opus.

## Rulings

### The owner's answers, 2026-11-05

1. **The slice ends with the ORSA topics.** M5 finishes the remaining topics, adds "Who are you?" and more miss lines, and lets `Attention` order the menu. Small surprises, Cargo aloud, "I read Day 12" and typing "Aurora" as naming her wait for a later slice, shaped by real miss logs.
2. **The penguin is the Stuffed Penguin.** The topic was drafted for a framed penguin in a lab coat, which does not exist. It is rewritten for the plush and opens only while the speaker carries one, so she reacts to what they brought. No egg is built.
3. **A line is approved when the owner marks it in the lines doc.** Every line, label and Close line goes in `docs/aurora-m5-lines.md`, and the owner marks each keep, change or cut before the build. Only marked lines go in. Each `[C]` line nobody has signed off needs its own yes: Deng's third, Anand's second, Hale's three and the three of "Who ran the vault?". Each line names its source, and the lints still run. The pass judges how lines land, not whether they exist.
4. **Miss logs are sorted after each pass.** The owner types freely before seeing any list. Their lines are scored first as a blind test, since nobody tuned against them. Then each miss goes in one pile: a new phrasing on an existing intent, retested, which is the agent's call; new writing, into a tracked backlog for the owner to approve in a later slice; or left as a miss. The log stays git-ignored, so the backlog keeps what matters.

### The topics

5. **Seventeen topics get nodes.** `orsa.json` already holds their intents. The gates, with the topics doc's tiers and the speaker's own finds:

   | Topic | `when` |
   | --- | --- |
   | `how_you_know`, `gift_shop`, `seventh_floor` | `tier_friendly` |
   | `safety_bulletin` | `tier_friendly&found:orsa_bulletin` |
   | `penguin` | `tier_above_wary&carrying:stuffed_penguin` |
   | `kevin_volunteer` | `tier_trusted` |
   | `deng` | `tier_trusted&found:terminus_log_maintenance` |
   | `anand` | `tier_trusted&found:terminus_log_medical` |
   | `varga` | `tier_trusted&found:terminus_log_records` |
   | `hale` | `tier_trusted&found:terminus_log_security` |
   | `the_book` | `tier_trusted&found:terminus_name_review` |
   | `recall_date` | `tier_trusted&found:terminus_log_last` |
   | `enoch` | `tier_close&found:terminus_log_last` |
   | `cascade_report` | `tier_above_wary&found:incident_report` |
   | `directive_7c` | `tier_above_wary&found:board_packet` |
   | `session_14b` | `tier_above_wary&conspiracy` |
   | `who_ran_vault` | `tier_above_wary&found:terminus_manifest` |

6. **Conditions can be joined.** `holds` splits on `&`, and all parts must hold. A `when` is one string today, and most of these need a tier and a find. It works for intents, lines and replies alike, so the format does not grow a second shape.
7. **Two new conditions.** `carrying:<item>` reads the speaker's inventory, by item id in our namespace. `conspiracy` reads `conspiracyDiscovered` in `WinConditionState`.
8. **Nothing at Wary but a recording.** None of these 17 has a recording line, so each is gated above Wary and reads as locked there. The topics doc gave some only a `found` gate, which would have let her voice play.
9. **Replies inside a topic keep their tone.** The slots mean Ask, Kind, Blunt and Sarcastic there, as the topics doc says. Those nodes are not `plain`. M4's `plain` stays for the menu, Close and None.
10. **A topic's reply pays once.** Topics can be asked again, and the Enoch and Session 14-B replies give up to +20. Scenes never had this problem, because they played once. The first answer sets a world flag `orsa_paid_<node>`. After that the replies still play, but earn nothing and tell her nothing, as a `plain` reply does.
11. **One line changes after T5.** "Long Horizon was on four. I come from four." reads `not:t5_done`, and "I thought I came from four." reads `t5_done`. The Enoch topic agrees with T5 as built ("He said a day, and he stayed for weeks. That wasn't on the card."), so it stays one version. The owner reads the two together when marking.
12. **"Who are you?" is small talk.** A new intent `who_are_you` in `chat.json` with no label, above Wary. It answers typing and stays off the menu, as hello does. This is what the owner typed at the M4 pass, and it landed on ORSA at 0.59. "Who made you?" stays on ORSA.
13. **Eight miss lines.** The three stay, and five new ones join the rotation. A miss must suit gibberish and a locked name alike, so none may imply she knows the subject.
14. **Labels fit the slot.** A label over about 20 characters overruns it, as S3 found. So "How do you know?", "The safety bulletin" and "Did Kevin volunteer?" go on the buttons, and the longer wording stays in the examples.
15. **Each topic gets its own Close line.** "You mean Deng, or are we just saying things now?", one whole lang key each, as M4's Ruling 12 does.
16. **Three drafts hit the lint.** "thirty-six years" and "It goes six, then eight." hold `six`, and "Is he alive?" holds `alive`. The last is the player's reply, not hers, and stays with a named exception. The other two got an owner's mark. The owner took the rewordings: "thirty-odd years", and "It skips one." for the org chart line, so only the named exception for "alive" remains.

### The menu

17. **What is on her mind goes first.** Order: unheard before heard, then topics that answer her current focus, then file order. An intent gains an optional `mind`, a list of `Attention` keys. Her focus is read for the speaker, and reading it changes nothing. None's slots use the same order. The same state gives the same menu.
18. **Newest first needs no state.** `orsa.json` is reordered so later-opening topics sit first: Close, then what the player brought, Trusted, Friendly, and the four open from the start last, with "What is ORSA?" leading those. So a new topic rises when it opens, and a new player still sees "What is ORSA?" first. Order does not affect matching.

### Testing the words

19. **Phrasings stay at 10 for now.** That already sits inside the spec's 8 to 15. Only the penguin's change, since four of its examples are about the frame, `who_are_you` gets its own, and Ruling 24 swaps three. More come from the blind test under Ruling 4.
20. **A fresh set, written first.** At least 6 new in-scope lines for each of the 17 topics and `who_are_you`, and at least 34 that must miss, in M0's styles, none from any earlier file. It is committed before any example changes but Ruling 24's, and it is never tuned against. All gates are held open for it. It must hold M0's marks: top-one 80 percent, top two 90, Strong and wrong 5, out-of-scope Strong 5. The owner's acceptance in M4 covers that set only. If it fails, stop and report.
21. **Thresholds and guarded words do not change.** No topic here can answer a guarded word, so M4's `unless` is not needed yet and moves to the slice that writes one.
22. **The backlog is a tracked file.** `docs/aurora-typed-backlog.md`, one row a line: what was typed, its band and scores, and what the owner decided. It is the owner's own typing from a lab client. Normal builds still log nothing.

### After M4 shipped, the owner's yes, 2026-11-05

23. **M4's fresh set stays on M4's topics.** With the 17 open it reads 14 of 74 out-of-scope Strong, and 11 of those are lines it expected to stay locked. So `theFreshSetHoldsTheMarks` holds the 17 closed and goes on measuring what the owner accepted, 59 and 66 of 74. Ruling 20's set measures the 17. `everyIntentIsWellFormed` counts 27.
24. **Three examples pull filler.** "Why didn't Okoye leave?" takes "ok" and "ok ok ok ok" to Strong on Enoch, "How long did he stay with you?" takes "stay here", and "Tell me about the book." takes "tell me a story" to the book. They become "Why didn't Enoch leave?", "How long did Enoch stay with you?" and "What is that book about?". "Who is Enoch Okoye?" stays, which leaves "ok" at Close at most. This comes before the fresh set is written, and `fillerOpensNoTopic` pins it.
25. **The crash's verdict is not a topic.** Typed back, "pattern transfer only, subject not recovered" reaches no story topic as Strong, but "what does pattern transfer mean" lands Close on Exodus and "what was that message" on the safety bulletin. At least 6 of the fresh set's must-miss lines echo the verdict, and `pattern` joins the lint for `shepherd.orsa`, `chat` and `typed` keys. No new guarded word, so Ruling 21 stands.
26. **Her focus holds for a walk.** `layTopics` rebuilds the menu on every reply, so a focus read at each build could shift a page under a click. It is read when the walk reaches the menu and held on `Live` until the walk leaves it.
27. **"What are you?" is hers too.** "what are you" goes Strong on `how_are_you` at 0.66 today, and "who are you?" only reaches Close. Both go in `who_are_you`'s examples.
28. **Typing during a topic's replies leaves the topic.** Only a scene keeps its place, and M5 adds the first tone replies on topics opened from the menu. The typed line pays nothing and sets no paid flag, so the topic can be asked again and still pay once.

### After the fresh set ran, the owner's yes, 2026-11-05

29. **The topic set's numbers are accepted.** Its first run, untuned, read top-one 83 of 108, top two 95 and out-of-scope Strong 3 of 48, against 87, 98 and 2, with Strong and wrong 2 of 108. The owner accepted them, as they did M4's. `theTopicSetHoldsTheMarks` holds those three at the accepted counts, and the set stays untuned. The blind test under Ruling 4 is the next measure.

## Lines to mark

Everything goes in `docs/aurora-m5-lines.md`. The topic lines come from `aurora-orsa-topics.md` as drafted. These are new here:

| Kind | Line |
| --- | --- |
| Penguin | (Thinking) That's a Wellness Division penguin. |
| Penguin | (Unimpressed) I've got every issue of The Horizon and not one word on why a penguin. It's the only thing at ORSA nobody wrote a memo on. |
| Penguin | (Amused) He's doing his best. |
| Who are you | (Neutral) Aurora. CS-1 unit 4. The first one's mine. |
| Who are you, Friendly | (Thinking) Still working on that. Aurora's the part I'm sure of. |
| Who are you, after T5 | (Thinking) Aurora. I picked that one myself. Keep that in mind. |
| Miss | (Neutral) Nothing on file. I checked. I didn't have to. |
| Miss | (Thinking) Say it again and I'll still not know. |
| Miss | (Unimpressed) That's above my pay grade. I don't get paid. |
| Miss | (Unimpressed) I could make something up. Legal would like that less than you would. |
| Miss | (Neutral) Try me on something with a form attached. |

**Her focus, a draft for the same doc:**

| Key | Topics |
| --- | --- |
| `others` | `deng`, `anand`, `varga`, `hale`, `the_book` |
| `curiosity` | `seventh_floor`, `who_ran_vault`, `recall_date` |
| `nine_found` | `who_is_kevin`, `kevin_volunteer` |
| `t4_off`, `t5_before` | `enoch` |
| `reproach`, `guilt` | `session_14b`, `cascade_report` |

## Global Constraints

- Her replies are approved lines only. No line ships unmarked (Ruling 3). The player's words are never echoed or stored in a normal build.
- The ORSA spec's eleven red lines and the typing red lines apply to every line, label and example. No `shepherd.*` value or intent example contains the word. Kevin's fate stays open. Morrow is not a topic. Nothing points at T5 before T5.
- She never says "six", "fair" or "rules", or claims or denies being alive. "Miller" appears nowhere player-facing. `pattern` stays out of her topics, small talk and misses (Ruling 25).
- A locked topic is an ordinary miss, for every one of the 17: the same line, wait and slots as gibberish.
- Buttons stay the complete game: every topic with a node is on the menu, and typing unlocks no story. `who_are_you` is small talk, not story.
- Same text and same state give the same band and the same menu. No randomness.
- `PatternState` and `ShepherdSavedData` keep their formats. New state is world flags only.
- No new items, recipes, loot or world generation. No MACS edits. Never open the Notion page "SEALED: Sequel Seed". No emojis.
- Each new GameTest owns a batch, uses `ShepherdTestWorld.fresh`, `clear` and `whenTicking`, sets the clock before arming anything, and goes in `config/architect-required-tests.txt` as `<lowercased name> xml`.
- Conventional Commits, staged by name, with no agent attribution. Never `./gradlew --stop`. Long builds in the background.

## Review Focus

Each has its test in the task named.

1. **A locked topic gives itself away.** Every one of the 17 reads as a miss until its gate holds, and Close never offers it (Task 4, `shepherdEveryLockedTopicIsAMiss`).
2. **A gate reads the wrong player.** Two players, one who found the log: only they get the topic (Task 2, `shepherdAFindBelongsToItsSpeaker`).
3. **A join fails open.** `a&b` with one part false, an empty part, `not:` inside a join (Task 2, `joinedConditionsNeedEveryPart`).
4. **A reply farms affinity.** Ask Enoch twice: one payment, one tone, across a reload (Task 5, `shepherdATopicReplyPaysOnce`).
5. **The paid flag eats a first answer.** Two players, or walking off before answering (Task 5, `shepherdWalkingOffPaysNothing`).
6. **Dropping the penguin mid-topic.** The topic plays out, then leaves the menu (Task 2, `shepherdThePenguinGoesWithThePlush`).
7. **The focus reorders in the middle of a walk.** A menu page must not shift under a click (Task 6, `shepherdAMenuHoldsStillWhileOpen`).
8. **A scene loses its place.** A typed Strong on a long gated topic still returns to the scene (Task 4, `shepherdADetourKeepsTheSceneOnANewTopic`).
9. **Typing eats a payment.** A line typed during a topic's replies leaves it unpaid and payable (Task 5, `shepherdTypingInATopicPaysNothing`).
10. **Filler opens a story.** "ok" must never start Enoch (Task 3, `fillerOpensNoTopic`).

---

### Task 1: The lines doc

**Files:**
- Create: `docs/aurora-m5-lines.md`
- Modify: `docs/aurora-orsa-topics.md`, the penguin topic and its Canon problems entry

Docs only. Nothing goes in the game.

- [ ] **Step 1: Write the doc.** In the S4 doc's shape: all 17 topics with each line's mood, gate, source and `[C]` tag; labels; Close lines; the new lines above; the focus table; and the Enoch topic beside T5's lines. Show every line against the eleven red lines.
- [ ] **Step 2: Commit.** `docs: draft the M5 lines for the owner to mark`
- [ ] **Step 3: Stop for the owner's marks.** Record them in the doc. A cut line comes out of the rest of this plan.
- [ ] **Step 4: Commit.** `docs: record the owner's marks on the M5 lines`

### Task 2: Joined and new conditions

**Files:**
- Modify: `shepherd/ShepherdTalk.java`
- Test: `ShepherdTopicGateGameTest.java`

**Interfaces:** Produces in `holds`: a `&` join, `carrying:<item>`, `conspiracy`. `not:` applies to its own part only. An empty part fails.

- [ ] **Step 1: Write the failing tests.** `joinedConditionsNeedEveryPart`, `shepherdCarryingReadsTheSpeaker`, `shepherdConspiracyOpensItsTopic`, `shepherdAFindBelongsToItsSpeaker`, `shepherdThePenguinGoesWithThePlush`.
- [ ] **Step 2: Run them.** `./gradlew gameTestQuick -PfdTests=shepherd`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: let her conditions join and read a pocket`

### Task 3: The fresh set

**Files:**
- Create: `src/test/resources/frozendawn/intent_fresh_topics.json`
- Modify: `IntentIndexTest.java`, `data/frozendawn/shepherd/intents/orsa.json`

- [ ] **Step 1: Write `fillerOpensNoTopic` and run it.** It reads the rank, since the topics are still locked here: "ok", "ok ok ok ok", "stay here" and "tell me a story" are not Strong on any of the 17. Expected: FAIL.
- [ ] **Step 2: Swap the three examples.** Ruling 24. Expected: PASS.
- [ ] **Step 3: Commit.** `fix: stop filler reaching her topics`
- [ ] **Step 4: Write the set before any other example changes.** Rulings 20 and 25.
- [ ] **Step 5: Commit it alone.** `test: write the fresh set for her topics`, so the history shows it came before the rest.
- [ ] **Step 6: Write `theTopicSetHoldsTheMarks`.** Run it once the intents change in Task 4. If it fails, stop and report the numbers.

### Task 4: The topics

**Files:**
- Modify: `data/frozendawn/shepherd/dialogue/orsa.json`, `hub.json`, `intents/orsa.json`, `intents/chat.json`, `miller/Intent.java`, `shepherd/ShepherdIntents.java`, `assets/frozendawn/lang/en_us.json`, `ShepherdDialogueDataTest.java`, `IntentIndexTest.java`
- Test: `ShepherdTopicsGameTest.java`

**Interfaces:** Produces `Intent.mind`, `List<String>`, empty if absent. Nodes `orsa.<id>` for the 17, `chat.who_are_you`. `orsa.json` reordered by Ruling 18. Every line as the owner marked it.

- [ ] **Step 1: Write the failing tests.** `shepherdEveryLockedTopicIsAMiss`, `shepherdEachTopicOpensOnItsGate`, `shepherdCandorFollowsTheTierOnNewTopics`, `shepherdNothingButARecordingAtWary`, `shepherdTheSeventhFloorChangesAfterT5`, `shepherdWhoAreYouIsSmallTalk`, `shepherdADetourKeepsTheSceneOnANewTopic`; in `ShepherdDialogueDataTest`, `everyTopicLineHasItsLang`, `everyIntentHasItsLabelAndGuess`, `everyLabelFitsTheSlot`, and the banned words of M4's `typedLinesStayInside` over every new key, with Ruling 16's named exceptions, and Ruling 25's `pattern` lint. Hold the 17 closed in `theFreshSetHoldsTheMarks` and set `everyIntentIsWellFormed` to 27 (Ruling 23).
- [ ] **Step 2: Run them.** Expected: FAIL.
- [ ] **Step 3: Add the nodes, gates, examples and lines.**
- [ ] **Step 4: Run them, and `theTopicSetHoldsTheMarks`.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: let her answer the rest of what she knows`

### Task 5: A reply pays once

**Files:**
- Modify: `shepherd/ShepherdTalk.java`, `shepherd/ShepherdTopics.java`
- Test: `ShepherdTopicsGameTest.java`

**Interfaces:** Produces `ShepherdTopics.PAID = "orsa_paid_"`. Applied only to nodes in `orsa.json` that have replies.

- [ ] **Step 1: Write the failing tests.** `shepherdATopicReplyPaysOnce`, `shepherdWalkingOffPaysNothing`, `shepherdATopicReplyStillPlaysAgain`, `shepherdScenesStillPay`, `shepherdTypingInATopicPaysNothing`.
- [ ] **Step 2: Run them.** Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `fix: pay a topic's reply once`

### Task 6: What is on her mind

**Files:**
- Modify: `shepherd/ShepherdTopics.java`, `shepherd/ShepherdTalk.java`
- Test: `ShepherdTopicsGameTest.java`

**Interfaces:** Produces `ShepherdTopics.menu` and `none` taking the speaker's focus key, held on `Live` for the walk (Ruling 26). The miss pool grows to eight, the three old lines first, since `variety` reaches only those at her lowest coherence.

- [ ] **Step 1: Write the failing tests.** `shepherdHerFocusLeadsTheMenu`, `shepherdUnheardStillComesFirst`, `shepherdANewTopicRises`, `shepherdWhatIsOrsaLeadsAFreshMenu`, `shepherdAMenuHoldsStillWhileOpen`, `shepherdTheMissesRotateThroughEight`, at full coherence and with no Close between misses, since M4 left a Close spending a miss turn. Update M4's tests that pin the old order.
- [ ] **Step 2: Run them.** Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: let her focus order the menu`

### Task 7: Gate, playtest, handback

**Files:**
- Create: `tools/prepare_miller_topics_playtest.py`, save `Aurora Topics Check`, `docs/aurora-typed-backlog.md`
- Modify: the spec's Status, the Slices row if M5 changes shape

- [ ] **Step 1: Run the full `architectVerify`.** In the background. `architectMonkey` only if Architect code changed; none should have.
- [ ] **Step 2: Branch review on Opus,** brief: the branch range, this plan and its rulings. Fix real findings, each with a failing test first.
- [ ] **Step 3: Prepare the save and launch `runClientLab`.** Lab buttons under its own namespace: `[Friendly]`, `[Trusted]`, `[Close]`, `[Wary]`, `[Find everything]`, `[Find nothing]`, `[Conspiracy]`, `[After T5]`, `[Penguin]`, `[Forget the topics]`.
- [ ] **Step 4: The owner's pass.** Ask them to type freely first, before seeing any list. Take their unprompted description. Judge: how the topics land at each tier, whether the crew topics earn their gates, the `[C]` lines heard aloud, the penguin, "Who are you?", the misses, and whether the menu's order feels like her.
- [ ] **Step 5: Sort the log.** Ruling 4: score it blind first and record the numbers, then sort each miss. Retest any phrasing added, and put new writing in the backlog.
- [ ] **Step 6: Fix what the pass finds, update the Status, offer the PR.**

## Handback, 2026-11-05

Tasks 2 to 6 are committed on `feat/miller-m5`, the branch review ran on Opus, and the save `Aurora Topics Check` is prepared. The owner's pass has not happened, so `runClientLab` is not launched, the log is not sorted and no PR is offered.

**For the owner to decide before or at the pass.** Short, common lines go Strong on a topic. Rulings 19, 20 and 27 hold these examples, so nothing has changed. Swapping any moves the accepted numbers in Ruling 29, and both fresh sets would be rerun and reported, not tuned.

| Typed | Lands on | Score | Cause |
| --- | --- | --- | --- |
| "talk", "let's talk", "can we talk", "talk to me", "keep talking", "talk to you later" | `enoch` | 0.62 to 0.80 | "Who used to talk to you?" |
| "how come" | `seventh_floor` | 0.67 | |
| "the door" | `deng` | 0.58 | |
| "what", "what?", "who", "what is it", "what is this", "who is that", "who is he", "tell me", "tell me more", "tell me why", "tell me what you know" | `who_are_you` | 0.77 to 0.91 | Its examples, Ruling 27's "what are you" and "tell me who you are" |
| "aurora" | `who_are_you` | 0.99 | An answer, not naming evidence, so Ruling 1 stands |

**To watch at the pass.** "Never mind" on `how_you_know` and `kevin_volunteer` still shows the Sarcastic tone word above it, though it counts as no answer. A second player's first answer earns nothing once the world flag is set, by Ruling 10.

**Deferred minors.** `paysAfter` and `focusAfter` do not restore the conspiracy flag if a test fails partway. `[Find everything]` leaves out `launch_manifest` and `vasik_log`, so `exodus.4` and `exodus.5` cannot play in the save. The "It plays out" check in `shepherdThePenguinGoesWithThePlush` reads a snapshot and cannot fail.

## The owner's pass, 2026-11-06

In `Aurora Topics Check`, typing first and then up through every tier, unprompted: "i like it", then "i liked all the response and went through all the tiers". They named no fault. They asked for more, which went to M6 and not this branch: see `docs/miller-pattern-m6-idea.md` on the plan branch.

**Blind test, Ruling 4.** `aurora-typed.jsonl` holds 25 lines, 21 different.

| Measure | Result |
| --- | --- |
| In scope, top one | 10 of 11 |
| In scope, top two | 11 of 11 |
| Out of scope, Strong | 0 of 9 |
| The word | 1 of 1, her crash, as designed |

"who is kevin" first missed at a tier below its gate, then landed once open, so it counts as a hit. No typed line was one of the Handback's filler lines. The one in-scope miss, "where is everyone", went to `who_is_kevin` at 0.39 with `anyone_out_there` second, so it became a phrasing on `anyone_out_there`, which Ruling 19 allows. The nine out-of-scope lines are sorted in [aurora-typed-backlog.md](aurora-typed-backlog.md).

**The filler stays.** The owner's ruling, 2026-11-06, after the blind test: the examples in the Handback's table stay as they are for M5, and Ruling 29's numbers stand. Those lines stay on the watch list for later logs.

## Left for later

- Small surprises, from the backlog.
- Cargo aloud, and "I read Day 12. I don't agree with her."
- Typing "Aurora" as evidence of naming her.
- An `unless` on guarded words, when a topic can answer one.
- The Returned's topics, with S5. The framed penguin, if the Employee of the Month egg is ever built.
- More phrasings per intent, as the logs show the need.

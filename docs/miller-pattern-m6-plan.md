# Miller Pattern M6 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task, inline. The owner avoids subagents, except the one branch review on Opus at the end of the slice. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** She can tell you how to stay alive. A new Advice menu covers the manual's mobs and a dozen items: the manual's counters as a recording, then her own better way. Asked about the Returned, she projects one in the world. Her ORSA voice gets the voice trace, her face gains three expressions and moves between faces by Flow, and at Close she greets you first and calls you Cargo aloud.

**Architecture:** Topics stay data: nodes, intents and lang keys, on the M5 machinery. `Intent` gains a menu, so Advice is a second menu built by the same code. Faces stay pure logic in `ShepherdFace`, and Flow is a small client-side view over it. The projection is one new server class and one new client renderer; it closes the talk screen, so her lines run on the strip she already has. No second dialogue system, and no change to either save format.

**Tech Stack:** Java 21, NeoForge 1.21.1, JUnit in `src/test`, GameTests in `src/gameTest`.

**Spec:** [miller-pattern-m6-idea.md](miller-pattern-m6-idea.md), the owner's order, picks and the five strategies. [aurora-miller-pattern.md](aurora-miller-pattern.md), "Red lines for typing". [aurora-orsa-topics.md](aurora-orsa-topics.md), the eleven red lines. [miller-pattern-m5-plan.md](miller-pattern-m5-plan.md), the topic format and Rulings 3, 4, 19 to 21 and 25.

**Lines:** `docs/aurora-m6-lines.md`, written in Task 1, for the owner to mark before anything is built.

**Mockups:** approved 2026-11-06, in `docs/art/aurora-shepherd/`: `orsa-face-mockup.png` (A, Voice trace), `new-expressions-mockup.png` (Beaming, Flustered, Grim), `face-transitions-mockup.gif` (Flow), `worry-motion-mockup.gif`, `hologram-mockup.gif`, `flustered-motion-mockup.gif` and `caught-projection-mockup.png`. `make_m6_faces_mockup.py` holds the exact rows and frames.

**Status:** approved by the owner as written, 2026-11-06, Rulings 1 to 29 included. Rechecked against M5 as shipped, PR 164 at 517623c7. Frostmite's strategy, the twelve items and every line still get the owner's marks in Task 1.

**Branch:** `feat/miller-m6` in `/Volumes/SSD/Work/frozen-dawn-miller-m6`, cut from `feat/maeve-director` once this plan has merged. The build session starts in that worktree and works there. PR into `feat/maeve-director`. It changes a shared system, sync and rendering, and merges into a long-lived branch, so the branch review runs on Opus.

## Rulings

### The owner's answers, 2026-11-06

1. **M5 came first.** It merged as PR 164, and this plan is written against it.
2. **The voice trace replaces `RECORDING`.** A flat line at rest, moving only while the recording plays. The enum name and the JSON word stay, so every use changes at once: ORSA's answers, her Wary resting face, the slip voice and the raw fish line.
3. **Beaming, Flustered and Grim are new moods.** The rows are the mockup's. Beaming and Flustered don't blink or shift their eyes, since neither has eyes to shut.
4. **Flow replaces the blink on a change of mood.** Each texel slides to the nearest place in the new face, one texel a frame, two ticks a frame, and any left over fade. It covers her body and the talk screen, and the blink that closed a thinking wait.
5. **Worry is motion, not a new face.** It is used only in the Architect's projection. She stops, her eyes dim, her mouth shrinks, she looks at the hologram, then she comes back.
6. **At Close she greets first.** With exclamation points if warranted, calling them Cargo aloud, and "dont make it weird" on the first one. Final wording needs the owner's mark.
7. **Every Threats mob gets advice, and all three Returned variants.** Post-Maeve residents are out.
8. **She gives the manual's counters, then her own better way.** How she presents it differs for each. The five approved strategies are in the idea file.
9. **The hologram shows in the world.** The figure stands to her left. The veil lifts, her words go to the strip, and you can walk round it. Walk off and it stops.
10. **The Architect's hologram glitches** after the manual's failed captures, and stops to think, chin in hand. She comments on it a lot, and hints in her own words that the Architects stopped making the same mistakes.

### Proposed and approved, 2026-11-06

11. **Advice is its own menu.** Nineteen more topics would bury ORSA's at three a page. The hub's Talk gains "Ask for advice" in the Kind slot, beside "Ask about ORSA". `Intent` gains `menu`, default `orsa`.
12. **Twelve items, none locked.** The typed log named no items, so these are a proposal from the open entries: `thermal_heater`, `resistive_heater`, `frost_ward_torch`, `miteaway`, `insulated_glass`, `thermostat`, `airlocks`, `oxygen_reservoir`, `battery_lantern`, `emergency_light`, `geiger`, `rad_aid`. All twelve are open, so no new condition is needed, and a test holds that. A locked entry joins only with a condition of its own, in a later slice.
13. **At Wary she only plays the recording.** The counters are ORSA's words, so they come through on the voice trace. Her own way is `tier_above_wary`. That is the reason to win her over.
14. **After Maeve is erased the mob topics lock.** The advice would be wrong by then. A new condition `erased` reads `PostMaeveWorldState`, and each mob's `when` is `not:erased`, so a locked mob reads as an ordinary miss. Items stay open.
15. **A projection ends the talk.** The screen must close for you to walk, so the walk ends there, with the same call `end(true)` makes. When it finishes, right-click to talk again. The Returned's menu is a plain node with three replies, and only the three variants project.
16. **The egg plays once, on the second ask.** The nod is to Star Wars and quotes nothing. The second time any Returned is projected, at Trusted or above, the figure flickers to another shape for a moment and she says one marked line. No randomness, by M5's rule: a world flag `hologram_egg` keeps it to one. If the line isn't marked, the egg is cut.
17. **"help me" gets an answer.** It came from the owner's own log. `need_help` is small talk in `chat.json`, no label, pointing them at the Advice menu.
18. **The manual button waits.** `FieldManual.openEntry` could open a page from a topic, but nobody asked for that. It goes to "Left for later".
19. **Frostmite's strategy needs the owner's verdict,** in Task 1, like the other five: keep a lit decoy heater out past your base and MiteAway smoke over the real one. Mites skip a protected heater, a heater holds up to ten, and a latched mite sits still on 5 health.

### Testing the words

20. **A fresh set, written first.** At least 6 new in-scope lines for each new intent, and at least 34 that must miss, including "what is maeve", "who is the leader" and "who is sophie". It is committed before any example exists, never tuned against, and all gates are held open for it. It must hold M0's marks: top-one 80 percent, top two 90, Strong and wrong 5, out-of-scope Strong 5. If it fails, stop and report. Any owner's acceptance of lower numbers goes in a new ruling, as Ruling 29 of M5 did.
21. **The older sets stay on their topics.** `theFreshSetHoldsTheMarks` and `theTopicSetHoldsTheMarks` hold the new intents closed, so they go on measuring what the owner accepted. If that isn't enough to hold them, stop and report.
22. **Thresholds and guarded words do not change.** `pattern` joins the lint for `shepherd.advice` keys too, so the Architect hint can't use it.

### More of her, the owner's asks, 2026-11-06

23. **Six to eight greetings.** They rotate by the day, so the same day gives the same line.
24. **More Architect commentary.** Three or four more lines run during the hologram, with the owner's two: "Huh, it looks kind of like you" and the pickaxe line.
25. **Asking again.** One short line each for the mobs, shown once `orsa_heard_<id>` is set, in place of the opening line.
26. **Reactions, starting with the Hunter.** A Hunter dies to heater burn with her in range: she says one line, once a world. Others follow only if this lands. Nothing reacts to the player's death, by the typing red lines.
27. **She gets caught.** The owner turned down a whistling face. When she's caught, there's no new face: Flustered, her body spins away so you can't see her display, "You didn't see that!", then she peeks back with the Wink, as in `flustered-motion-mockup.gif`. Once a day. She's caught projecting something embarrassing but wholesome, the owner's words, when she thinks nobody is looking. It uses Task 7's hologram and snaps off as she spins. It shows a small you and a small her, side by side, as in `caught-projection-mockup.png`. The small you wears the follower's real skin, tinted her green. The UUID comes from `ShepherdSavedData.follower`, synced on the entity, and the client reads the skin as `ScribeChalk` does: `getPlayerInfo`, falling back to `DefaultPlayerSkin`, with slim or wide arms as `RemnantRenderer` picks them. Whoever catches her sees the follower, not themselves.

28. **"Cargo! Cargo! check this out!"** At Close, when calm, at most once every few days, she calls you on the strip and projects something stupid, using Task 7's hologram with an item model in place of a figure. The first is the Stuffed Penguin. The owner picks the rest.
29. **The Clovis Bray nod is rewritten.** The S3 rule for eggs says a nod makes sense in the world and never says "game" or a title, so the fourth-wall count stays at three. "I heard of this game" can't stay. In-world, it is an ORSA file: "ORSA had a file on a man named Clovis Bray. Heard of him?" He put minds into machines, which points at her, so it reads `t5_done` under red line 8. Once a world.

## Lines to mark

Everything goes in `docs/aurora-m6-lines.md`. These are first drafts, to show the shape. The owner's mark decides.

| Kind | Line |
| --- | --- |
| Greeting, first | (Beaming) Cargo! |
| Greeting, first | (Flustered) That's you. That's what I file you under. Don't make it weird. |
| Greeting, a day on | (Beaming) Cargo. Still breathing. Good for the paperwork. |
| Greeting, long gone | (Beaming) CARGO! You came back! I'm logging that twice! |
| Hunter | (Recording) Seal entrances with blocks, not doors. Hide backup heaters. |
| Hunter | (Thinking) Word of advice? Don't hide the heater. Show it one. |
| Hunter | (Grim) One-high window, lit heater three blocks behind it. It comes to put it out, it can't reach, and it stands there. |
| Hunter | (Sad) They were staff, once. I'd rather it was quick. |
| Mimic | (Unimpressed) The manual says it strafes. The manual never stood still. |
| Mimic | (Neutral) For five seconds it's busy being you. Don't move, and shoot it. |
| Architect | (Grim) ORSA was terrified of these guys. |
| Architect | (Neutral) The early ones wasted swings. The later ones didn't. Nobody showed them. |
| Architect, after the worry | (Neutral) So fight it at your heater, and don't do the same thing twice. |
| Architect | (Thinking) Huh, it looks kind of like you. |
| Architect | (Amused) So it's got a pickaxe like you, a sword like you and blocks like you. ORSA's scientists would faint. |
| Architect | (Grim) That's ORSA's best image. Every other capture came out worse. |
| Greeting | (Neutral) Cargo. Temperature's bad. You knew that. |
| Asking again | (Unimpressed) Still the heater. It was the heater last time. |
| Reaction, Hunter | (Sad) That worked. I said it would. |
| Caught | (Flustered) You didn't see that! |
| The gag | (Beaming) Cargo! Cargo! Check this out! |
| The nod, after T5 | (Thinking) ORSA had a file on a man named Clovis Bray. Heard of him? |

## Global Constraints

- Her replies are approved lines only. No line ships unmarked. The player's words are never echoed or stored in a normal build.
- The eleven red lines and the typing red lines apply to every line, label and example. No `shepherd.*` value or intent example contains the word. No Heart, archive, Thaeven words or Master Architect. Maeve and MACS are never named. Mars's ignorance of the Returned stays out.
- She never says "six", "fair" or "rules", or claims or denies being alive. "Miller" appears nowhere player-facing. `pattern` stays out of her advice, topics, small talk and misses.
- A locked topic is an ordinary miss.
- Buttons stay the complete game: every topic with a node is on a menu, and typing unlocks nothing the buttons don't.
- Same text and same state give the same band and the same menu. No randomness in what she says.
- `PatternState` and `ShepherdSavedData` keep their formats. New state is world flags and deed times only. A projection is never saved.
- No new items, recipes, loot or world generation. No MACS edits, and that includes `ArchitectModel`: the hologram draws from its own model. Never open the Notion page "SEALED: Sequel Seed". Never edit the main checkout. No emojis.
- Each new GameTest owns a batch, uses `ShepherdTestWorld.fresh`, `clear` and `whenTicking`, sets the clock before arming anything, and goes in `config/architect-required-tests.txt` as `<lowercased name> xml`.
- Conventional Commits, staged by name, with no agent attribution. Never `./gradlew --stop`. Long builds in the background.

## Review Focus

Each has its test in the task named.

1. **A mood changes mid-Flow.** The next flow starts from what is showing, with no jump (Task 2, `flowStartsFromWhatIsShowing`).
2. **Wary reads wrong.** At rest she shows the flat line, and it moves only while she speaks (Task 2, `theTraceRestsFlatAndMovesOnlyWhileSpeaking`).
3. **The greeting nags or eats a scene.** Once a day, never over a waiting node, never at quiet (Task 3, `shepherdGreetsOnceADay`, `shepherdAWaitingSceneBeatsTheGreeting`).
4. **Advice lies after the end.** Every mob reads as a miss once erased (Task 5, `shepherdTheMobsLockOnceErased`).
5. **A menu returns to the wrong place.** A topic opened from Advice goes back to Advice (Task 5, `shepherdAdviceReturnsToAdvice`).
6. **A projection outlives its reason.** She goes offline, the speaker leaves, is hurt, or logs out: it stops and leaves nothing behind (Task 6, `shepherdAProjectionStopsWhenItShould`).
7. **She talks over her own projection.** No murmur, no second right-click, no second projection (Task 6, `shepherdNothingInterruptsAProjection`).
8. **A locked entry sneaks in.** Every item's manual page has no advancement (Task 5, `everyAdviceEntryIsOpen`).

---

### Task 1: The lines doc

**Files:**
- Create: `docs/aurora-m6-lines.md`

Docs only. Nothing goes in the game.

- [ ] **Step 1: Write the doc.** Every greeting, label, guess, topic line, repeat line, reaction, `need_help`, the gag, the nod and the egg, each with mood, gate and source. Each mob's counters come from its manual entry. Each mob's own way comes from the idea file. Each item's lines come from its manual entry, and its tip must be checked in code as the mobs' were. Vary how each one is presented. Show every line against the red lines.
- [ ] **Step 2: Add the Frostmite strategy and the twelve items for a verdict.** Rulings 12 and 19.
- [ ] **Step 3: Commit.** `docs: draft the M6 lines for the owner to mark`
- [ ] **Step 4: Stop for the owner's marks.** Record them in the doc. A cut line or item comes out of the rest of this plan.
- [ ] **Step 5: Commit.** `docs: record the owner's marks on the M6 lines`

### Task 2: Her faces

**Files:**
- Create: `shepherd/ShepherdFlow.java`, `src/test/java/com/frozendawn/shepherd/ShepherdFlowTest.java`
- Modify: `shepherd/ShepherdFace.java`, `shepherd/ShepherdEntity.java`, `client/ShepherdRenderer.java`, `client/ShepherdFaceRenderer.java`, `client/ShepherdScreen.java`, `client/ShepherdPromptStrip.java`
- Test: `src/test/java/com/frozendawn/shepherd/ShepherdFaceTest.java`

**Interfaces:** Produces:
- `Mood.BEAMING`, `FLUSTERED`, `GRIM`, appended after `RECORDING`, and `WORRIED`, which is internal: it carries the worry timeline and is no new face, by Ruling 5. `RECORDING`'s rows become `WAVE_REST` in the mockup script.
- `ShepherdFace.trace(int age)`, returning `Cells`: the six `WAVE` frames, four ticks each.
- `ShepherdFace.worry(int age)`, returning `Cells`, and `WORRY_TICKS = 82`: Neutral 14 ticks, lids 10, small mouth 6, eyes away 32, small mouth 8, lids 12. `WORRIED`'s rows are the eyes-away frame.
- `ShepherdFlow`, one per view: `Cells shown(Cells wanted, int tick)`. A new `wanted` starts a flow from what is showing. Idle expressions and blinks pass through without one.

`MOOD_BEAT_TICKS` and every `moodBeat` go. `ShepherdFace`'s special cases for `RECORDING` stay: no mouth, no eye motion. The strip draws a still face today, so `ShepherdPromptStrip` draws `trace(age)` while a recording types, with its existing age, and the flat line once it has typed.

- [ ] **Step 1: Write the failing tests.** `theNewMoodsMatchTheMockup`, `theTraceRestsFlatAndMovesOnlyWhileSpeaking`, `theStripTraceStopsWhenTheLineHasTyped`, `worryComesBackToNeutral`, `flowEndsOnTheNewFace`, `flowMovesOneTexelAFrame`, `flowStartsFromWhatIsShowing`, `flowLeavesAnUnchangedFaceAlone`.
- [ ] **Step 2: Run them.** `./gradlew test --tests '*ShepherdF*'`. Expected: FAIL.
- [ ] **Step 3: Implement.** The matching in `flow` of `make_m6_faces_mockup.py` is the algorithm: nearest pairs first, ties in a fixed order, so it stays deterministic.
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: give her the voice trace, new faces and Flow`

### Task 3: The greeting

**Files:**
- Modify: `shepherd/ShepherdTalk.java`, `data/frozendawn/shepherd/dialogue/hub.json`, `assets/frozendawn/lang/en_us.json`
- Test: `ShepherdTalkGameTest.java`

**Interfaces:** Produces node `hub.greet`, played by `sayOnTheHub`, and condition `greet_long`. `GREET_READY = "greet"`, a deed time: ready again one `DAY_TICKS` on. `greet_long` holds at two days past ready. The first greeting sets `greeted_cargo`.

`hub.greet` holds the six to eight marked lines, one chosen by the day number, as Ruling 23 says. In `openHub`, after `waitingNode` comes back null: at `tier_close`, not `quiet`, with the deed ready, say `hub.greet`. Otherwise `showHub()`.

- [ ] **Step 1: Write the failing tests.** `shepherdGreetsFirstAtClose`, `shepherdGreetsOnceADay`, `shepherdNamesCargoOnce`, `shepherdShoutsAfterALongAbsence`, `shepherdGreetingsRotateByTheDay`, `shepherdGreetsNobodyBelowClose`, `shepherdAWaitingSceneBeatsTheGreeting`, `shepherdQuietMeansNoGreeting`.
- [ ] **Step 2: Run them.** `./gradlew gameTestQuick -PfdTests=shepherd`. Expected: FAIL.
- [ ] **Step 3: Implement,** with the lines as marked.
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: let her greet first at Close`

### Task 4: The fresh set

**Files:**
- Create: `src/test/resources/frozendawn/intent_fresh_advice.json`

- [ ] **Step 1: Write the set before any example exists.** Ruling 20.
- [ ] **Step 2: Commit it alone.** `test: write the fresh set for her advice`, so the history shows it came first.

### Task 5: The Advice menu

**Files:**
- Create: `data/frozendawn/shepherd/dialogue/advice.json`, `data/frozendawn/shepherd/intents/advice.json`
- Modify: `miller/Intent.java`, `shepherd/ShepherdTopics.java`, `shepherd/ShepherdTalk.java`, `dialogue/hub.json`, `intents/chat.json`, `assets/frozendawn/lang/en_us.json`, `ShepherdDialogueDataTest.java`, `IntentIndexTest.java`
- Test: `ShepherdAdviceGameTest.java`

**Interfaces:** Consumes the marked lines. Produces:
- `Intent.menu()`, `orsa` if absent.
- `ShepherdTopics.ADVICE = "advice.menu"`. `menu` and `none` take the root as their first argument; pages are `advice.menu.2` and so on.
- Nodes `advice.frostbitten`, `.frostmite`, `.hollow`, `.returned`, `.hunter`, `.mimic`, `.architect`, and `advice.<item>` for each marked item. Each sets `orsa_heard_<id>`. Counters use mood `recording`. Her own lines carry `"if": "tier_above_wary"`. Each mob's opening line reads `not:orsa_heard_<id>`, and its short repeat line reads `orsa_heard_<id>`.
- Condition `erased`, on `ShepherdTalk.holds`.
- `orsa.after` returns to the menu the walk came from.
- `chat.need_help`.

- [ ] **Step 1: Write the failing tests.** `shepherdAdviceOpensFromTheHub`, `shepherdAdviceReturnsToAdvice`, `shepherdWaryOnlyPlaysTheCounters`, `shepherdTheMobsLockOnceErased`, `shepherdItemsOutliveTheEnd`, `shepherdAsksAgainDifferently`, `shepherdHelpMePointsAtAdvice`; in `ShepherdDialogueDataTest`, `everyAdviceLineHasItsLang`, `everyAdviceLabelFitsTheSlot`, `everyAdviceEntryIsOpen`, and M4's banned words over every new key; in `IntentIndexTest`, `theAdviceSetHoldsTheMarks`.
- [ ] **Step 2: Run them.** Expected: FAIL.
- [ ] **Step 3: Add the code, nodes, examples and lines.** 8 to 15 phrasings an intent. No example may be copied from the fresh set.
- [ ] **Step 4: Run them, with `theFreshSetHoldsTheMarks` and `theTopicSetHoldsTheMarks`.** Expected: PASS. If a set fails, stop and report the numbers.
- [ ] **Step 5: Commit.** `feat: let her give advice from the manual`

### Task 6: The projection

**Files:**
- Create: `shepherd/ShepherdProjection.java`
- Modify: `shepherd/ShepherdDialogue.java`, `shepherd/ShepherdEntity.java`, `shepherd/ShepherdTalk.java`, `shepherd/ShepherdReactions.java` (silence during a projection), `dialogue/advice.json`
- Test: `ShepherdProjectionGameTest.java`, `ShepherdDialogueDataTest.java`

**Interfaces:** Produces:
- `Node.project()`, read from `"project"`: `hunter`, `mimic` or `architect`, or null. Task 8 adds the kinds `item` and `pair`, which no node names: its own code starts them.
- `ShepherdProjection.start(ServerPlayer player, ShepherdEntity she, String kind, List<Line> lines)`, `tick(MinecraftServer)`, `active()`, `stop()`. One at a time, kept in a static like `ShepherdTalk.live`.
- `ShepherdEntity.projection()`, an int on a new synced `DATA_PROJECTION`, not saved: 0 none, 1 to 3 the variants, 4 the egg's frame, 5 `item` and 6 `pair`, the last two added in Task 8.

Reaching a node with `project`: `end(true)`, then `start`. Each line goes to the speaker as a `ShepherdLinePayload`, and the next waits for the line's typing and `STRIP_HOLD_TICKS`. A `worried` line sets the mood, waits `WORRY_TICKS`, then sends its text as Neutral. It stops on `talkMustClose`, and when she goes offline or the speaker leaves. `murmur` and its kin stay silent while `active()`, and `openHub` does nothing. `advice.architect` carries Ruling 24's lines in its list, so they run during the hologram like the rest. Second projection of any variant at `tier_trusted` with `hologram_egg` unset plays the egg.

- [ ] **Step 1: Write the failing tests.** `shepherdAProjectionClosesTheTalkAndPlays`, `shepherdAProjectionStopsWhenItShould`, `shepherdNothingInterruptsAProjection`, `shepherdWorryHoldsTheLine`, `shepherdTheArchitectCommentsThroughout`, `shepherdTheEggPlaysOnce`, `shepherdNoProjectionSurvivesAReload`, and `everyProjectionIsOneWeDraw`.
- [ ] **Step 2: Run them.** Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: let her project the Returned`

### Task 7: The hologram

**Files:**
- Create: `client/ShepherdHologramRenderer.java`, `shepherd/ShepherdHologram.java`
- Modify: `client/ShepherdRenderer.java`
- Test: `src/test/java/com/frozendawn/shepherd/ShepherdHologramTest.java`

**Interfaces:** Consumes `ShepherdEntity.projection()`. Produces:
- `ShepherdHologram`, pure logic by tick: `tearRow`, `tearShift`, `chin(int age)`, `bars(int age)`, all from a hash, as `ShepherdGlitch` does.
- `ShepherdHologramRenderer.draw(ShepherdEntity, float partial, PoseStack, MultiBufferSource)`, called from `ShepherdRenderer.render`.

Draw a `HumanoidModel` baked from `ModelLayers.ZOMBIE`, at half size, about two blocks to her left, facing as she faces. The textures are the mob's own, tinted her green, see-through and full bright. Rays and the ring are flat quads. Scanlines are a bright band climbing the figure. Hunter turns slowly. Mimic's head copies the local player's look. Architect is dark with clean eyes, and tears, gaps and static are drawn over it, with the right arm posed to the chin while `chin` holds. This must not touch `ArchitectModel`.

- [ ] **Step 1: Write the failing tests.** `theHologramIsDeterministic`, `theArchitectStopsToThink`, `tearsStayOnTheFigure`.
- [ ] **Step 2: Run them.** Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them, then `./gradlew build`.** Expected: PASS. The look is judged at the pass, not here.
- [ ] **Step 5: Commit.** `feat: draw her hologram in the world`

### Task 8: Small moments

**Files:**
- Modify: `shepherd/ShepherdEggs.java`, `shepherd/ShepherdEntity.java`, `shepherd/ShepherdReactions.java`, `shepherd/ShepherdProjection.java`, `shepherd/ShepherdHologram.java`, `client/ShepherdHologramRenderer.java`, `client/ShepherdRenderer.java`, `shepherd/ShepherdPolicy.java`, `assets/frozendawn/lang/en_us.json`
- Test: `ShepherdMomentsGameTest.java`

**Interfaces:** Consumes Task 6's `start` and Task 7's renderer. Produces `ShepherdEntity.flusterTicks`, which turns her body by the mockup's beats; kind `item` (5), an item model; kind `pair` (6), the small you and her, with a synced `DATA_PROJECTED_PLAYER`, an optional UUID; `ShepherdPolicy.GAG_EVERY_TICKS`; world flags `reacted_hunter_burn`, `clovis_said`; deed times `caught`, `gag`.

- [ ] **Step 1: Write the failing tests.** `shepherdReactsOnceToAHunterAtTheHeater`, `shepherdIgnoresAHunterKilledAnotherWay`, `shepherdGetsCaughtOnlyWhenCalm`, `shepherdGetsCaughtOnceADay`, `shepherdCaughtShowsHerFollower`, `shepherdTheGagWaitsForCalm`, `shepherdNamesClovisOnlyAfterT5`.
- [ ] **Step 2: Run them.** Expected: FAIL.
- [ ] **Step 3: Implement,** beside the other nods in `ShepherdEggs`.
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: give her a few small moments`

### Task 9: Gate, playtest, handback

**Files:**
- Create: `tools/prepare_miller_m6_playtest.py`, save `Aurora Advice Check`, `src/labBridge/java/com/frozendawn/labbridge/LabShepherdAdvice.java`
- Modify: the spec's Status, the Slices row, `docs/aurora-typed-backlog.md`

- [ ] **Step 1: Run the full `architectVerify`.** In the background. `architectMonkey` only if Architect code changed; none should have.
- [ ] **Step 2: Branch review on Opus,** brief: the branch range, this plan and its rulings. Fix real findings, each with a failing test first.
- [ ] **Step 3: Prepare the save and launch `runClientLab`.** Lab buttons under their own namespace: `[Friendly]`, `[Trusted]`, `[Close]`, `[Wary]`, `[A day on]`, `[Long gone]`, `[Erased]`, `[Forget the advice]`, and a spawn button for each mob, so the strategies can be tried on a live one. Allow each function in `config/lab-bridge-functions.json` if the bridge asks for it.
- [ ] **Step 4: The owner's pass.** Ask them to type freely first, before seeing any list. Take their unprompted description. Judge: the voice trace, the three faces and Flow in motion, the greeting, each hologram from every side, the worry, the egg, getting caught, the gag, and whether each strategy works as she says.
- [ ] **Step 5: Sort the log.** M5's Ruling 4: score it blind, record the numbers, sort each miss.
- [ ] **Step 6: Fix what the pass finds, update the Status, offer the PR.**

## Left for later

- A locked manual entry as advice, with its own condition.
- A button that opens the manual from a topic.
- The rest of the 166 entries, as the logs ask for them.
- Small surprises, "I read Day 12. I don't agree with her.", typing "Aurora" as naming her, and an `unless` on guarded words, from M5's list.

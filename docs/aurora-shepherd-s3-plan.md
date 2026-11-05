# Aurora S3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task, inline. The owner avoids subagents, except the one branch review at the end. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Her score starts to mean something: what the player does moves it, her face and a one-line reaction show it, she goes formal at Wary, reaching Friendly earns "The name", and she warms whoever stands near her.

**Architecture:** `ShepherdAffinity` is pure logic: the tiers and the table of deeds. `ShepherdReactions` is the one server door a deed comes through, which checks she was there to see it and its cooldown, then moves the score, her face and the strip. Tier-ups are not events: `ShepherdPrompt` looks at the current tier at a calm moment and asks for the first personal conversation not yet asked, so a reload or a falling score needs no bookkeeping.

**Tech Stack:** Java 21, NeoForge 1.21.1, JUnit in `src/test`, GameTests in `src/gameTest`.

**Spec:** [aurora-shepherd.md](aurora-shepherd.md), sections "Affinity", "Behavior" (Heater) and the talk screen's "She floats up"; S1's Out list puts tiers, reactions, face feedback and the heater's warmth here. Likes, dislikes and lines come from Notion, "Aurora (ORSA CS-1 "Shepherd")" and "Aurora — Dialogue Script v1", Parts 2 and 4, read 2026-11-04.

**Branch:** `feat/aurora-affinity` in `/Volumes/SSD/Work/frozen-dawn-aurora-affinity`, cut from `origin/feat/maeve-director` at 8f8d49d2.

## Global Constraints

- In code she is Shepherd: package `com.frozendawn.shepherd`, lang `shepherd.*`, data `data/frozendawn/shepherd/`. No `shepherd.*` text contains "continuity" in any case. She never mentions respawn. No emojis.
- Affinity feedback is the face only: no text ever says she liked something. A reaction line is her talking, not a score notice.
- The strip's reaction and recording forms are `ambient-lines-mockup.png`, approved "Build as drawn". Floating up is `float-up-mockup.png`, approved with this plan. Build nothing that looks different.
- Every line is a draft until the owner marks it. New lines are listed under Rulings.
- MACS stays untouched. No item, recipe, loot or world generation changes.
- Each Shepherd GameTest owns a batch, with `ShepherdTestWorld.fresh` and `clear` around it, and starts with `ShepherdTestWorld.whenTicking`. Set the game clock before arming a cooldown. Every new GameTest goes in `config/architect-required-tests.txt` as `<lowercased name> xml`.
- Tests cover behavior, not lines. Conventional Commits, staged by file name. Never `./gradlew --stop`. Do not run Gradle while the lab client is open in this checkout, unless AGENTS.md's conditions hold.

## Rulings for the owner

Approving the plan accepts these unless you say otherwise.

1. **The deeds and their scores.** The table below. Dialogue stays as it is. At these numbers a player who keeps a warm shelter and goes out on clear nights gains about 10 a day, and each log is 10 more, so Friendly lands somewhere after 15 to 20 days of normal play. All of it lives in one enum, so tuning is one line each.

   | Deed | Score | Cooldown | Counts when | Face | Line |
   | --- | --- | --- | --- | --- | --- |
   | `WARM_SHELTER` | +5 | 1 day | Enclosed, at 10 C or more, a lit heater within 16 blocks | Happy | "Ooh. Warm. Don't tell anyone I said 'ooh'." |
   | `READ_LOG` | +10 | 1 minute | An ORSA document is archived | Neutral | "Keep reading. I want to know who signed off on this." |
   | `CLEAR_NIGHT` | +5 | 1 day | Night, no rain, open sky above the player | Happy | None: T3 says she has never seen the sky herself |
   | `ARCHIVIST_KILLED` | -25 | 1 minute | A player's kill starts its marked death | Sad | "...It was carrying something warm. Did you know that?" |
   | `SHELTER_FROZE` | -10 | 1 day | Enclosed, at -25 C or less after armor, an unlit heater within 8 blocks | Unimpressed | "You're turning a color. I don't love the color." |
   | `AIR_FAILING` | -10 | 1 day | Any suffocation stage | Alarmed | "Your oxygen is a number I'd like to be bigger." |
   | `LEFT_BEHIND` | -10 | Its own clock | Someone comes back after a full day with nobody near her | Unimpressed | New: "Oh good. You remembered where you put me." |
   | `CALLED_SHEPHERD` | -5 | 1 minute | "shepherd" in chat, any case | Unimpressed | "No." |

2. **She has to be there.** Every deed needs her awake, online and within 16 blocks of the player, the same range as a prompt: she only knows what her sensors saw. Killing an Archivist on your own costs nothing.
3. **Left out on purpose.** "Leaving the Archivist alone" has no moment to score and could be farmed by doing nothing. "Exploring ORSA sites" waits for S5's first-site topic. "ORSA cruelty" needs conversations S4 writes. Sarcasm already scores in dialogue. Hitting her scores nothing, because neither list has it.
4. **"Shelter" needs a heater.** Without one the vault would count as a freezing shelter on the walk out, so a shelter is somewhere with a heater in it.
5. **T1 "The name" comes into S3**, so a tier-up has something to play. T2 to T5 stay in S4, so Trusted and Close ask nothing yet. Its first line, "You asked about the name once.", is false if the player never asked, so asking now sets `asked_name`, and otherwise she says a new draft: "You never asked about the name. I'm telling you anyway."
6. **A tier-up looks at the score when things are calm**, not when the score crosses. If it drops below Friendly first she does not ask, and she asks when it comes back. It never replaces a place prompt.
7. **The full calm applies to tier-ups only.** The cold and O2 lines gate raising a personal conversation. `fm.doors` and `fm.exit` keep S2's calm, because the vault is cold and those moments belong to their places. The cold line is -10 C after armor, where freezing starts. The O2 line is any suffocation stage.
8. **Wary.** She rests on the Recording face, Talk gives a formal recording, and reactions become recordings on the grey strip: new "Thank you for your cooperation." for a like, "Please remain calm." for a dislike. No personal conversation is offered. Everything else keeps working: she still follows, and the score can climb back.
9. **`hub.talk` gets tier variants**, because "I'm still deciding what the hell I think of you" is wrong at Close. Wary, new: "CS-1 is available for evacuation assistance. Please state your request." (Recording). Friendly and up, new: "Nothing to report. Around here I'll take that." (Neutral).
10. **Warmth is +5 C within 4 blocks**, for players only, awake and online, lamp on or off. It is a player adjustment, not a `HeaterRegistry` heater, so Mimics and Hunters ignore it. Rooms do not warm.
11. **Floating up goes to her follower.** She drifts in front of them at eye level for as long as the strip shows, on the Thinking face, then drops back to her hover. If the only near player is not her follower, the strip shows and she stays where she is.
12. **Reactions wait for no one.** A reaction replaces whatever the strip shows, and nothing plays while the talk screen is open: the score and cooldown still land, silently.

## Review Focus

Each has its test in the task that owns the code.

- A tier crossed mid-conversation, or a score that falls before the calm moment: no prompt lands on an open screen and T1 is not lost (Task 6).
- A save reloaded inside a cooldown: the cooldown holds, so relogging cannot farm a deed (Task 5).
- A second player's deed near her: the score is shared, so it counts, and a hit on a far player delays nothing (Tasks 5, 6).
- She is Offline, or her body is unloaded, when a deed happens: nothing moves (Task 5).
- `/fd shepherd replay`: score, cooldowns and `t1_asked` all go, and a replayed world can earn T1 again (Tasks 4, 6).

---

### Task 1: A prompt survives a closed screen

Deferred minor: Esc or a hit right after the right-click loses `fm.exit` for good. It has no replies, so no `resumeNode` keeps it, and `waitingNode` has already cleared `promptNode`.

**Files:** Modify `ShepherdTalk.java`. Test `ShepherdVaultGameTest.java`, `config/architect-required-tests.txt`.

**Interfaces:** Produces `Live.fromPrompt`. `waitingNode` clears `promptNode` only when the node has gone missing; `reply` clears it after the first accepted answer of a conversation with `fromPrompt` set.

- [ ] **Step 1: Write the failing tests.** `shepherdEscKeepsTheExitLineWaiting`: raise `fm.exit`, right-click, `ShepherdTalk.close`, right-click: `liveNode()` is `fm.exit`. `shepherdAHitKeepsTheExitLineWaiting`: the same, closed by damage. `shepherdAClickSpendsTheExitLine`: right-click, reply `CONTINUE`: `promptNode()` is null and the next right-click is the hub. `shepherdAResumeLeavesThePromptWaiting`: with a `resumeNode` and a `promptNode`, answering the resumed node leaves `promptNode` set.
- [ ] **Step 2:** `./gradlew gameTestQuick -PfdTests=shepherdesckeeps,shepherdahitkeeps,shepherdaclickspends,shepherdaresume`. Expected: the first two fail.
- [ ] **Step 3:** Implement, then run the same. Expected: PASS, with `shepherdEscResumesThePrompt` still green.
- [ ] **Step 4:** Commit, `fix: keep her prompt waiting when the screen closes early`.

### Task 2: A conversation that cannot open gives way

Deferred minor: a `when` with no `else` that does not hold throws out of `new ShepherdConversation`, and `openHub` has already set `live`, so she is stuck talking to nobody until a restart. A node whose lines are all conditional and none hold throws in `showMood` the same way.

**Files:** Modify `ShepherdConversation.java`, `ShepherdTalk.java`, `ShepherdDialogues.java`. Test `ShepherdConversationTest.java`, `ShepherdTalkGameTest.java`.

**Interfaces:** Produces `static boolean ShepherdConversation.opens(Map<String, Node> nodes, String start, Predicate<String> holds)`. `ShepherdTalk.begin` becomes `boolean`; a false from it in `openHub` drops the waiting node and shows the hub, in `start` clears `live`, and in `action(TALK)` stays on the hub. A node with no lines to say shows Neutral.

- [ ] **Step 1: Write the failing tests.** JUnit `aStartThatRunsOutDoesNotOpen`, `aStartThatHoldsOpens`, `aStartThatLoopsDoesNotOpen`. GameTests `shepherdABadWhenFallsBackToTheHub`: a prompt on a node that cannot open: right-click shows the hub, `promptNode()` is null, and a second right-click works. `shepherdANodeWithNoLinesToSayDoesNotThrow`. Both swap in test nodes through a `ShepherdDialogues` seam for tests, cleared in `@AfterBatch`.
- [ ] **Step 2:** Run the new tests. Expected: FAIL with the exception.
- [ ] **Step 3:** Implement, and run them. Expected: PASS.
- [ ] **Step 4:** Commit, `fix: let a conversation that cannot open give way to the hub`.

### Task 3: She floats up to ask

Deferred minor: nothing moves her when she asks. Drawn in `float-up-mockup.png`.

**Files:** Modify `ShepherdEntity.java`, `ShepherdPrompt.java`, `ShepherdPolicy.java`. Add the mockup and its script. Test `ShepherdVaultGameTest.java`.

**Interfaces:** Produces `ShepherdPolicy.ASK_BLOCKS = 2.5`, `ASK_TICKS = 100`; `ShepherdEntity.askFor(int ticks)`, `boolean asking()`. While asking, `HoverGoal` targets the follower's eye position plus their horizontal look times `ASK_BLOCKS`, less 0.3 in height, through `roomBeside`, and she holds the Thinking face. `ShepherdPrompt.show` calls `askFor(ASK_TICKS)` on each showing, only if the follower is among `near`. A conversation opening ends it.

- [ ] **Step 1: Write the failing tests.** `shepherdSheFloatsUpWhenSheAsks`: a player facing +z, her behind them, a raised prompt: within 60 ticks she is within 1.5 blocks of the spot in front. `shepherdSheDropsBackAfterAsking`: `asking()` ends and she is back within hover range. `shepherdSheDoesNotFloatToAStranger`: a near player who is not the follower: `asking()` stays false. `shepherdRightClickingEndsTheAsk`.
- [ ] **Step 2:** Run them. Expected: FAIL, `askFor` missing.
- [ ] **Step 3:** Implement, and run them. Expected: PASS.
- [ ] **Step 4:** Commit, `feat: she floats up in front of you to ask for a minute`.

### Task 4: Tiers, and how she is at Wary

**Files:** Create `ShepherdAffinity.java`. Modify `ShepherdTalk.java` (`holds`), `ShepherdEntity.java`, `ShepherdSavedData.java`, `ShepherdDebugCommand.java`, `FrozenDawnDebugCommand.java`, `hub.json`, `en_us.json`. Test `src/test/.../ShepherdAffinityTest.java`, `ShepherdDialogueDataTest.java`, new `ShepherdAffinityGameTest.java`.

**Interfaces:** Produces `ShepherdAffinity.Tier { WARY, NEUTRAL, FRIENDLY, TRUSTED, CLOSE }`, `static Tier of(int score)`, `boolean atLeast(Tier)`; `ShepherdSavedData.tier()`; conditions `tier_wary` (only then) and `tier_friendly`, `tier_trusted`, `tier_close` (at least); `ShepherdEntity.rest()`, which sets Recording at Wary and Neutral otherwise and replaces each hard-coded Neutral in `reboot`, `end`, `showMood` and `showHub`; `/fd shepherd affinity <value>`. `hub.talk` becomes the Wary node with `else` to `hub.talk.warm`, whose `when` is `tier_friendly` and `else` is `hub.talk.plain`, which keeps today's line.

- [ ] **Step 1: Write the failing tests.** JUnit `tiersBreakAtTheSpecsLines`: -251 Wary, -250 Neutral, 249 Neutral, 250 Friendly, 499, 500 Trusted, 849, 850 Close, and the clamp ends. GameTests `shepherdAtWarySheRestsOnTheRecordingFace`, `shepherdLeavingWaryBringsHerFaceBack`, `shepherdHubTalkFollowsTheTier` (the three nodes), `shepherdReplayClearsTheScore`, `shepherdAffinityCommandSetsTheScore`.
- [ ] **Step 2:** Run them. Expected: FAIL.
- [ ] **Step 3:** Implement, and run them. Expected: PASS.
- [ ] **Step 4:** Commit, `feat: give her tiers, and a formal voice at Wary`.

### Task 5: Deeds, her face and reaction lines

**Files:** Modify `ShepherdAffinity.java`, `ShepherdSavedData.java`, `ShepherdEntity.java`, `ShepherdPolicy.java`, `client/ShepherdPromptStrip.java`, `ModNetworking.java`, `ClientHandlers.java`, `en_us.json`, `event/PlayerTickHandler.java`, `item/OrsaDocumentItem.java`, `entity/ArchivistEntity.java`. Create `ShepherdReactions.java`, `network/ShepherdLinePayload.java`. Test `ShepherdAffinityTest.java`, `ShepherdAffinityGameTest.java`, `ShepherdPolicyTest.java`.

**Interfaces:**
- `ShepherdAffinity.Deed`, one constant per row of Ruling 1, with `int score()`, `int cooldownTicks()`, `Mood mood()`, nullable `String line()`, `boolean liked()`.
- `ShepherdSavedData`: `long deedReady(String deed)`, `void deedReady(String deed, long gameTime)`, `long lastNear()`, `void lastNear(long)`, saved as `deeds` and `lastNear`. A save without them reads as all ready and not yet set. `replayFirstMeeting` and `clearForTests` clear both.
- `ShepherdReactions.saw(ServerPlayer player, Deed deed)`, the only public door. Polls every `POLL_TICKS = 100` for near players: `WARM_SHELTER`, `SHELTER_FROZE`, `AIR_FAILING`, `CLEAR_NIGHT`, `LEFT_BEHIND`. Subscribes to `ServerChatEvent` for `CALLED_SHEPHERD`.
- `PlayerTickHandler`: `public static float resolvedTemperature(ServerPlayer)` over `getFreezeResolvedTemperature`, and `public static boolean airFailing(UUID)`, true at any stage but `NONE`.
- `ShepherdEntity.react(Mood)`: `ShepherdPolicy.REACT_TICKS = 60` on that face, then `rest()`. Skipped while a talk is live.
- `ShepherdLinePayload(String key, int mood)`, and `ShepherdPromptStrip.say(String key, Mood mood)`. Rows wrap at 170 pixels. A Recording line draws the muted edge and text of the mockup's fourth strip.
- `OrsaDocumentItem.use` calls `saw(READ_LOG)` when it archives. `ArchivistEntity.beginMarkedDeath` calls `saw(ARCHIVIST_KILLED)`.

- [ ] **Step 1: Write the failing tests.** JUnit `everyDeedScoresWithinTheSpecsRange`, `everyDeedLineHasALangEntry` in `ShepherdDialogueDataTest`, `stripRowsWrapToTheBox` if the wrap lands in `ShepherdPolicy`. GameTests, each with the clock set first:
  - `shepherdADeedMovesTheScoreAndHerFace`, `shepherdACooldownStopsTheSecondDeed`, `shepherdACooldownEndsOnTime`.
  - `shepherdACooldownSurvivesAReload`: save and `load` the data, and the deed still does nothing.
  - `shepherdSheOnlyKnowsWhatSheSaw`: 17 blocks away nothing moves. `shepherdOfflineSheSeesNothing`. `shepherdAnUnloadedBodySeesNothing`.
  - `shepherdASecondPlayersDeedCounts`.
  - `shepherdAWarmShelterPleasesHer` and `shepherdAColdVaultIsNotAFrozenShelter`, with a real lit heater and with none.
  - `shepherdArchivingADocumentPleasesHer`, `shepherdKillingAnArchivistHurtsHer`, `shepherdCallingHerShepherdCosts`.
  - `shepherdComingBackAfterADayCosts`, `shepherdStayingNearCostsNothing`.
  - `shepherdNoReactionWhileTalking`: the score lands, her face does not change.
- [ ] **Step 2:** Run them. Expected: FAIL.
- [ ] **Step 3:** Implement in that order: enum, saved state, `saw`, polls, hooks, payload, strip. Run them. Expected: PASS.
- [ ] **Step 4:** Commit, `feat: her score moves with what she sees you do`. Split into a second commit for the strip if it runs long.

### Task 6: Calm, and "The name" at Friendly

Includes the deferred minor that a hit on any player delays the strip.

**Files:** Modify `ShepherdPrompt.java`, `ShepherdPolicy.java`, `first_meeting.json` (`fm.name.earn` sets `asked_name`), `en_us.json`. Create `data/frozendawn/shepherd/dialogue/personal.json`. Test `ShepherdVaultGameTest.java`, `ShepherdAffinityGameTest.java`, `ShepherdPolicyTest.java`, `ShepherdDialogueDataTest.java`.

**Interfaces:** `ShepherdPrompt.lastHurt` becomes `Map<UUID, Long>`, read for `near` only. Produces `ShepherdPolicy.COLD_LINE_C = -10`; `boolean comfortable(float coldestResolved, boolean airFailing)`; `ShepherdPrompt.PERSONAL`, an ordered list of `Personal(Tier tier, String node, String asked)` holding `(FRIENDLY, "t1.name", "t1_asked")`. After `ask`, if `promptNode` and `resumeNode` are null, no talk is live, `fm_done` holds and the view is `calm` and `comfortable`, it raises the first entry whose tier is reached and whose flag is unset. Nodes `t1.name` and `t1.why`, `t1.good`, `t1.light`, `t1.screensaver`, `t1.end`, with the script's lines, moods and scores, ending "Anyway. Back to not dying."

- [ ] **Step 1: Write the failing tests.** JUnit `comfortableNeedsWarmthAndAir` at -10 and either side. GameTests:
  - `shepherdAHitOnAFarPlayerDoesNotDelayTheStrip`, `shepherdAHitOnANearPlayerStillDelaysIt`.
  - `shepherdAtFriendlySheAsksAboutTheName`, `shepherdNotBeforeTheFirstMeetingIsDone`, `shepherdAtWarySheOffersNothing`.
  - `shepherdAFallingScoreLeavesTheNameUnasked`: at 240 nothing raises, at 260 it does.
  - `shepherdNotOverAnOpenScreen`: a score crossing Friendly during a talk raises nothing until it ends.
  - `shepherdAPlacePromptGoesFirst`: `fm.exit` waiting, T1 waits behind it.
  - `shepherdColdHoldsATierUpButNotAPlacePrompt`.
  - `shepherdTheNameKnowsIfYouAsked`: with and without `asked_name`, the first line differs.
  - `shepherdReplayEarnsTheNameAgain`.
- [ ] **Step 2:** Run them. Expected: FAIL.
- [ ] **Step 3:** Implement, and run them with `gameTestQuick -PfdTests=shepherd`. Expected: PASS.
- [ ] **Step 4:** Commit, `feat: at Friendly she tells you about the name`.

### Task 7: Her warmth

**Files:** Modify `ShepherdPolicy.java`, `event/PlayerTickHandler.java` (`applyPlayerTemperatureAdjustments`, beside the Miel line). Create `ShepherdWarmth.java`. Test `ShepherdPolicyTest.java`, `ShepherdEntityGameTest.java`.

**Interfaces:** Produces `ShepherdPolicy.WARMTH_BLOCKS = 4`, `WARMTH_C = 5f`, `float warmth(double distance, boolean offline)`; `ShepherdWarmth.of(ServerPlayer)`, zero unless she is awake, loaded and in the player's level.

- [ ] **Step 1: Write the failing tests.** JUnit `warmthStopsAtItsEdgeAndWhenOffline`. GameTests `shepherdSheWarmsANearPlayer` (`resolvedTemperature` rises by 5), `shepherdSheIsNoHeater`: `HeaterRegistry.nearby` is empty around her.
- [ ] **Step 2:** Run them. Expected: FAIL.
- [ ] **Step 3:** Implement, and run them. Expected: PASS.
- [ ] **Step 4:** Commit, `feat: she warms whoever stands near her`.

### Task 8: The playtest world

**Files:** Create `tools/prepare_aurora_affinity_playtest.py`.

Title `Aurora Affinity Check`, namespace `aurora_affinity_check`, pack `aurora-affinity-check`, its own `#built` objective. Survival, Normal, difficulty locked, advancements filtered, as AGENTS.md requires. A flat lab with a dock and a charged cell, a sealed hut with a lit heater, a sealed hut with an unlit one, and open sky. Buttons: `[Score 240]`, `[Score 260]`, `[Score -300]`, `[Score 0]`, `[Read state]`, `[An ORSA log]`, `[Summon an Archivist]`, `[Night]`, `[Skip a day]`, `[Spawn a zombie]`, `[Cold hut]`, `[Warm hut]`, `[Far away]`, `[Creative]`, `[Survival]`, `[Replay first meeting]`, `[Shepherd dump]`. Source `level.dat`: any closed save, copied with an empty `session.lock`.

- [ ] **Step 1:** Write it from `prepare_aurora_vault_playtest.py`, run it, and check the loader-selected pack stack as AGENTS.md says.
- [ ] **Step 2:** Commit, `feat: add the Aurora Affinity Check playtest world`.

### Task 9: Gates, review and the owner's pass

- [ ] **Step 1:** Update the spec: S3's section, what shipped, and the deferred minors this closed. Run the full `architectVerify` in the background. Expected: every test and required case passes.
- [ ] **Step 2:** One branch review on Opus, because the branch changes her save format and the prompt every later slice depends on. Brief it with the branch range, this plan and the rulings. Fix real findings with a failing test first, and list the rest as deferred.
- [ ] **Step 3:** Launch `./gradlew runClientLab -PfdLabWorld='Aurora Affinity Check' --console=plain` in the background and read the log while the owner plays. Take their unprompted report first. For the owner to judge: the float-up, whether reactions feel like her or nag, how the Wary hub reads, the warmth's size, whether the pace to Friendly feels right, and the new lines.
- [ ] **Step 4:** Fix what the pass finds, re-run `architectVerify`, and update Status with the owner's words.
- [ ] **Step 5:** Offer a PR into `feat/maeve-director`. `architectMonkey` has to pass on its head. Post both statuses only after the owner approves the merge. Never merge without that approval.

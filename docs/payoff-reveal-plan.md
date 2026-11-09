# Payoff Reveal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task, inline. The owner avoids subagents, except the one branch review at the end. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fire the gun payoff. On a player's first death after the Heart falls with Maeve erased, the death screen shows the CH-18 line. The field manual's first page changes (CH-19). Two locked pages open (CH-20 and Projection Review). The next respawn replays the opening triage, changed. Every other respawn gets a short boot cue.

**Architecture:** One world flag, `PayoffState`, owns the trigger and replaces the Terminus ledger's own flag. The server decides who sees what and sends three small payloads. The client draws the death line, swaps the landing text and plays the replay or the boot cue. The two book pages unlock on a hidden advancement the server grants.

**Tech Stack:** Java 21, NeoForge 1.21.1, Patchouli 1.21.1-92, JUnit in `src/test`, GameTests in `src/gameTest`.

**Spec:** [payoff-roadmap.md](payoff-roadmap.md), branch 3. Notion: the payoff branch spec, sections 1 to 5 and 6d steps 2 and 2b. Canon Hierarchy CH-11, CH-18 to CH-21, CH-28, CH-33 and CH-35.

**Status:** approved by the owner as written, 2026-11-09, Rulings 1 to 10 included. Nothing is built.

**Branch:** `feat/payoff-reveal` in `/Volumes/SSD/Work/frozen-dawn-payoff-reveal`, cut from `feat/maeve-director` once this plan has merged. The build session starts in that worktree and works there. PR into `feat/maeve-director`. The branch changes a save format and client/server sync, and it merges into a long-lived branch, so the branch review runs on Opus.

## Global Constraints

- Canon strings go in word for word: the CH-18 lines, the CH-19 landing text, the CH-20 page, the Projection Review page and the replay readout, all as Rulings 4 to 8 give them.
- The word "Continuity" appears only in the CH-18 and CH-19 lines, which reuse the manual's phrase (CH-11). No new redactions. Capitalization follows CH-33.
- Nothing says when the record was taken or by whom (CH-28). Nothing explains the reveal: no toast, no chat, no sound of its own.
- The CH-18 line sits directly above the Respawn button. Code comments, commits and the PR give no reason for the placement.
- Never edit MACS. Never open the Notion page "SEALED: Sequel Seed". Cite canon by ID; no Notion-only notes in code, comments, commits or the PR.
- New Java names, keys and files use "payoff", never "continuity", for the same reason as the branch names.

## Rulings

1. **The flag.** `com.frozendawn.lore.PayoffState`, a world `SavedData` under `frozendawn_payoff`, holds `fired`, `firedBy` (a UUID, or none) and `replayPending`. It fires on a player's `LivingDeathEvent` at lowest priority, if not cancelled, when `ReturnedHearthSavedData.maeveErased()` is true, Maeve did not escape and it hasn't fired yet. It never reverts. `fired` goes to every client on login and whenever it changes. The E11 hook `maeveEscaped(server)` moves here from `TerminusLedger` and still returns false (CH-21).
2. **The debug config never fires it.** The trigger reads the saved erased flag, never `PostMaeveWorldState.isErased`, so `debugForceMaeveErased` can't fire the payoff in any build. Playtests and GameTests set the saved state with `/fd postmaeve set true` or `PostMaeveWorldState.setForDebug`.
3. **Only new saves get the payoff (owner, 2026-11-09).** `PayoffState` is created on `ServerStartedEvent`, not lazily. When it's first created, `eligible = eligibleAt(overworld game time)`, which is true below 1200 ticks. That makes a new world eligible and a world from before this branch not. An ineligible world never fires the payoff: no line, no manual change, no pages, no replay. The Terminus works in both. `PayoffState` also keeps `terminusTurned`, which the old `frozendawn_terminus` data seeds once on creation. A qualifying death sets `terminusTurned` in every world, and also fires the payoff in an eligible one. `TerminusLedger` stops saving its own flag and becomes a view of `terminusTurned`; `setForTest` delegates. `TerminusLedgerPayload` is replaced by `PayoffStatePayload`, which carries both flags.
4. **CH-18, the death line.** Lang keys `screen.frozendawn.payoff.death_line.1` = `Continuity protocol: active.` and `.2` = `1 enrollee remaining.`. When the flag fires, the server sends `PayoffDeathLinePayload` to the dying player only. That arrives before vanilla's kill packet, which opens the death screen. The client draws both lines centered, in the death screen's text color, with the second line's bottom 4 px above the first button's top. That's Respawn, or the hardcore Spectate button. If that player logs back in while still dead and `replayPending` is set, the server sends the payload again, so a relog keeps the line. Other players see nothing on screen.
5. **CH-19, the landing text.** Lang key `book.frozendawn.frozen_dawn_guide.landing_text.fired` = `$(br2)$(br2)$(br2)$(l)Issued field survival manual.$()$(br2)Continuity protocol: active.$(br)1 enrollee remaining.$(br)Civilian edition.`. It keeps today's layout, one sentence per line. `book.json` keeps the pre-payoff text. Patchouli builds the landing text in `GuiBookLanding.init()` into its package-private field `text`. On `ScreenEvent.Init.Post` for the Frozen Dawn guide's landing, when the client flag is set, `PatchouliLandingBranding` replaces that field through a new client accessor mixin, `GuiBookLandingAccessor`. The new value is `new BookTextRenderer(landing, Component.translatable(key), 15, 43)`, the same position Patchouli uses. It reads the synced world flag, so it can't revert.
6. **CH-20 and Projection Review unlock together.** Hidden advancement `frozendawn:payoff_fired`, with no parent, no display and one `minecraft:impossible` criterion. When the flag fires, the server grants it to every online player, and on login to anyone who lacks it. New page 12 in `what_is_orsa.json`, straight after the third-file page, is locked on it. Its text, each redaction ten U+2588 characters:

```txt
A third file exists. It does not belong to a director. The name is ██████████. The role listed is ██████████. The file has a termination date.
```

   New page 8 in `projected_timeline.json`, after the sign-off, is locked on the same advancement. Its text is `$(bold)PROJECTION REVIEW$()$(br)All phases observed as forecast.$(br)Enrollees remaining at terminus: 1.$(br)Variance: 0.$(br)Projection source: enrollee record.`. Book text stays in the entry JSON like every other page, as branch 2 did. Lang keys carry only the text Java draws.
7. **The two entries show as unread.** Patchouli marks an entry unread only if it's missing from the client's viewed list, and a page unlocking doesn't change that, so most players would see nothing. When the server first grants `payoff_fired` to a player, it sends `PayoffStatePayload(fired, markNew = true)`. The client then removes `frozendawn:orsa_intel/what_is_orsa` and `frozendawn:orsa_intel/projected_timeline` from `PersistentData.data.getBookData(book).viewedEntries`, calls `PersistentData.save()`, and marks both entries' read state dirty. It's Patchouli's own marker, with no toast.
8. **The replay readout.** `OrsaAwakeningIntro`'s hardcoded strings move to lang keys: `screen.frozendawn.awakening.header` (`FIRST AWAKENING TRIAGE`), `.readout` (`SUBJECT READOUT`), `.temp_hud` (`TEMP HUD ONLINE`), `.opening.1` to `.4` (today's four lines), `.replay.2` (`TRAUMA: NONE RECORDED`) and `.replay.3` (`ENROLLEES REMAINING: 1 / AS PROJECTED`). The replay shows opening lines 1 and 4 with replay lines 2 and 3, which is CH-35's readout exactly. CH-35 changes line 2 as well as line 3. The header is the same in both.
9. **Who gets which respawn.** On `PlayerRespawnEvent`, not after the End exit, `PayoffState.cueFor(player)` returns `REPLAY` for `firedBy` while `replayPending` is set, and `BOOT` for everyone else. The server sends `RespawnCuePayload(replay)` and clears `replayPending` once the replay is sent. The replay ignores the dev property `frozendawn.dev.skipOrsaAwakening` and the client toggle. It plays the intro without the First Boot Epigraph, and the server holds the player still for it with the opening's freeze. With `doImmediateRespawn` on, the death screen never opens. The flag still fires, and the replay opens with the two CH-18 lines flickering once on the black before the logo, for about 20 ticks, centered (owner, 2026-11-09). The client decides this: it flickers when `showDeathLine()` arrived but no `DeathScreen` opened before the replay cue.
10. **The boot cue (section 5).** On `BOOT`, if the client toggle `orsa.respawnBootCue` (new, default true, in `FrozenDawnClientConfig`) is on: play `ui.orsa_awakening_ring` at volume 0.35, call `TemperatureHud.startIntroBlink()` and blink `TEMP HUD ONLINE` for 40 ticks at the intro's position. No other words. It never plays on first join, and it doesn't change after the payoff. So that it doesn't wear on a player who dies often, it plays at most once per 6000 client ticks (five minutes), tracked on the client: `bootCueDue(long now, long lastPlayed)`. A respawn inside the cooldown is silent. The replay ignores the cooldown. The owner judges the volume and the cooldown in the visual pass.

## Review Focus

1. **A relog on the death screen loses the line.** A player who fires the payoff and quits before respawning comes back dead, and the client has forgotten. Ruling 4 resends the line on login; Task 2's GameTest holds it.
2. **An old save from before this branch.** It must never fire the payoff, even when its Heart falls later. Its Terminus keeps its turn, or turns on the next qualifying death. A GameTest shard that first touches `PayoffState` late must still count as new, which is why it's created on server start. Task 1's unit test and GameTest hold it.
3. **The second player in a world.** They get no line and no replay, only the silent manual changes, including a player who was offline when it fired. Task 1's GameTest grants on login.
4. **A debug config in a real world.** `debugForceMaeveErased` set true must never fire the payoff. Task 1's GameTest sets it and checks.
5. **The End exit counts as a respawn.** Leaving the End fires `PlayerRespawnEvent` without a death. Neither cue may play. Task 4's GameTest holds `cueFor` and the handler's guard.

---

### Task 1: The shared flag and the Terminus migration

**Files:**
- Create: `src/main/java/com/frozendawn/lore/PayoffState.java`, `src/main/java/com/frozendawn/network/PayoffStatePayload.java`, `src/main/java/com/frozendawn/network/PayoffDeathLinePayload.java`, `src/main/resources/data/frozendawn/advancement/payoff_fired.json`, `src/test/java/com/frozendawn/lore/PayoffStateTest.java`, `src/gameTest/java/com/frozendawn/lore/PayoffGameTest.java`
- Modify: `defense/TerminusLedger.java` (a view, Ruling 3), `network/ModNetworking.java` (register the two payloads, drop `TerminusLedgerPayload`), `client/TerminusClient.java` (forget the payoff mirror on logout)
- Delete: `network/TerminusLedgerPayload.java`

**Interfaces:**
- Produces: `record PayoffStatePayload(boolean fired, boolean terminusTurned, boolean markNew)` (Task 3 handles `markNew`); `PayoffState.firesOnDeath(boolean savedErased, boolean maeveEscaped, boolean fired): boolean`; `eligibleAt(long gameTime): boolean`; `fired(MinecraftServer): boolean`; `terminusTurned(MinecraftServer): boolean`; `clientFired(): boolean`; `cueFor(ServerPlayer): RespawnCue` with `enum RespawnCue { BOOT, REPLAY }`; `setForTest(MinecraftServer, boolean fired)`, which also sets `eligible` and `terminusTurned` and clears `firedBy` and `replayPending`; static `create(long gameTime, boolean legacyTurned)`, so the unit test can build a new or old world's state without a server.

- [ ] **Step 1: Write the failing unit tests** in `PayoffStateTest`:
  - `firesOnlyOnceAfterErasure`: `firesOnDeath` is false for (false, false, false), true for (true, false, false), false for (true, true, false) and false for (true, false, true).
  - `onlyNewSavesAreEligible`: `eligibleAt` is true for 0 and 1199, false for 1200. `create(5_000_000, true)` gives `eligible` false, `fired` false and `terminusTurned` true. `create(0, false)` gives `eligible` true and both flags false.
  - `theAdvancementIsHidden`: `payoff_fired.json` has no `parent`, no `display`, and one criterion with trigger `minecraft:impossible`.
- [ ] **Step 2: Run** `./gradlew test --tests com.frozendawn.lore.PayoffStateTest`. Expected: FAIL, `PayoffState` is missing.
- [ ] **Step 3: Implement `PayoffState`** per Rulings 1 to 3, 4 (the payload send and the login resend) and 6 (grants on fire and on login). Rewrite `TerminusLedger` as a view. Update its class comment: it reads `PayoffState.terminusTurned`. Keep the existing `TerminusLedgerTest` passing by pointing it at `PayoffState.firesOnDeath`.
- [ ] **Step 4: Run** the unit test class and `TerminusLedgerTest`. Expected: PASS.
- [ ] **Step 5: Write the GameTests** in `PayoffGameTest`, batch `payoff`. Before the batch, record `PayoffState.fired`, the saved erased flag and the debug config value; after it, restore all three.
  - `aDeathBeforeTheHeartChangesNothing`: erased false, a mock player dies, `fired` stays false.
  - `theFirstDeathAfterErasureFiresOnce`: erased true, player A dies. Then `fired` is true, `TerminusLedger.turned` is true and A holds `payoff_fired`. Player B dies and `firedBy` is still A.
  - `aPlayerWhoWasAwayStillGetsThePages`: after firing, a new mock player logs in and holds `payoff_fired`.
  - `theDebugConfigNeverFiresIt`: saved erased false, `debugForceMaeveErased` set true, a player dies, `fired` stays false.
  - `anOldWorldNeverFires`: mark the state ineligible (a test setter), erased true, a player dies. `terminusTurned` is true, `fired` stays false, nobody holds `payoff_fired`, and `cueFor` gives `BOOT`.
- [ ] **Step 6: Run** `./gradlew gameTestQuick -PfdTests=payoff,terminus`. Expected: PASS, including the existing Terminus tests.
- [ ] **Step 7: Commit** `feat: add the shared payoff flag`, staging each file by name.

### Task 2: The CH-18 death line

**Files:**
- Create: `src/main/java/com/frozendawn/client/PayoffClient.java`
- Modify: `en_us.json` (Ruling 4's keys), `network/ModNetworking.java` (`PayoffDeathLinePayload` to `PayoffClient.showDeathLine()`), `PayoffGameTest`, `src/test/java/com/frozendawn/lore/PayoffStateTest.java`

**Interfaces:**
- Consumes: `PayoffDeathLinePayload` and the login resend from Task 1.
- Produces: `PayoffClient.showDeathLine()`. The client clears the line when the death screen closes on respawn.

- [ ] **Step 1: Write the failing tests:** unit `theDeathLineIsCanon` (both lang values exact, per Ruling 4); GameTest `theLineComesBackAfterARelog` (after A fires and before A respawns, `PayoffState.deathLineOwed(A)` is true; after A respawns, it's false; it's never true for B).
- [ ] **Step 2: Run** both. Expected: FAIL.
- [ ] **Step 3: Implement** `PayoffClient`: a `ScreenEvent.Render.Post` handler for `DeathScreen` that draws the two lines when shown, per Ruling 4. Add `PayoffState.deathLineOwed(ServerPlayer): boolean` (dead, `firedBy`, `replayPending`), which the login handler uses.
- [ ] **Step 4: Run** both. Expected: PASS.
- [ ] **Step 5: Commit** `feat: show the death-screen line once`.

### Task 3: The manual changes (CH-19, CH-20, Projection Review)

**Files:**
- Create: `src/main/java/com/frozendawn/mixin/GuiBookLandingAccessor.java`
- Modify: `frozendawn.mixins.json` (client list), `client/PatchouliLandingBranding.java`, `client/PayoffClient.java` (Ruling 7's unread marking), `en_us.json` (Ruling 5's key), `.../entries/orsa_intel/what_is_orsa.json`, `.../entries/orsa_intel/projected_timeline.json`, `PayoffStateTest`

- [ ] **Step 1: Write the failing unit tests:**
  - `theLandingChangesToCanon`: the key's value equals Ruling 5's text. With formatting codes stripped, it reads `Issued field survival manual. Continuity protocol: active. 1 enrollee remaining. Civilian edition.`.
  - `theThirdFileFootnoteFollowsTheThirdFile`: `what_is_orsa.json` has 22 pages. Page 12's text equals Ruling 6's string, with two runs of exactly ten U+2588 and none of any other length. It's locked on `frozendawn:payoff_fired`. Page 13 is the old page 12, titled `2074 — Denial`.
  - `theProjectionReviewClosesTheTimeline`: `projected_timeline.json` has 9 pages. Page 8, stripped of formatting, is CH-35's five lines, and it's locked on `frozendawn:payoff_fired`. Page 7 is unchanged.
  - `noRevealPageSaysTheWord`: neither new page contains "continuity", ignoring case.
- [ ] **Step 2: Run** them. Expected: FAIL.
- [ ] **Step 3: Spike the landing swap** (Ruling 5) in the lab client before writing the rest. If Patchouli redraws from its own `text` field after `Init.Post`, keep it. If not, stop and report to the owner before trying any other route.
- [ ] **Step 4: Make the JSON and lang edits, and the unread marking** per Rulings 6 and 7.
- [ ] **Step 5: Run** `./gradlew test --tests com.frozendawn.lore.PayoffStateTest`. Expected: PASS.
- [ ] **Step 6: Commit** `feat: change the manual when the payoff fires`.

### Task 4: The boot cue and the replay

**Files:**
- Create: `src/main/java/com/frozendawn/network/RespawnCuePayload.java`
- Modify: `client/OrsaAwakeningIntro.java` (lang keys, `startReplay()`, `startBootCue()`), `config/FrozenDawnClientConfig.java` (Ruling 10's toggle), `network/ClientHandlers.java`, `network/ModNetworking.java`, `event/WorldTickHandler.java` (extract `freezeForAwakening(ServerPlayer)` from `trySendOrsaAwakening`), `lore/PayoffState.java` (the respawn handler), `en_us.json` (Ruling 8's keys), `PayoffStateTest`, `PayoffGameTest`

**Interfaces:**
- Consumes: `PayoffState.cueFor` from Task 1.
- Produces: `OrsaAwakeningIntro.readout(boolean replay): List<String>` (the four lang keys in order) for the test; `startReplay(boolean flickerDeathLine)`; `startBootCue()`; `bootCueDue(long now, long lastPlayed): boolean`.

- [ ] **Step 1: Write the failing tests:**
  - unit `theReplayReadsAsProjected`: the lang values for `readout(true)` are CH-35's four lines in order. `readout(false)` is today's four. The header is `FIRST AWAKENING TRIAGE`. No replay value contains "continuity", ignoring case.
  - GameTest `onlyTheFiringPlayerGetsTheReplay`: after A fires, `cueFor(A)` is `REPLAY`. After A respawns it's `BOOT`, and `cueFor(B)` is always `BOOT`.
  - GameTest `leavingTheEndIsNotARespawn`: the respawn handler sends nothing when `isEndConquered` is true. Test it through a package-private `cueOnRespawn(ServerPlayer, boolean endConquered): Optional<RespawnCue>`.
  - unit `theBootCueRestsFiveMinutes`: `bootCueDue(now, last)` is true when nothing has played (`last` is `Long.MIN_VALUE`), false at `last + 5999`, true at `last + 6000`.
- [ ] **Step 2: Run** them. Expected: FAIL.
- [ ] **Step 3: Implement** per Rulings 8 to 10. The opening keeps its current timing and look. `startReplay(boolean flickerDeathLine)` is `start()` with the replay readout, plus Ruling 9's flicker when asked. `startBootCue()` checks the toggle and `bootCueDue`, runs its own 40-tick counter and draws only the blink.
- [ ] **Step 4: Run** the unit class and `./gradlew gameTestQuick -PfdTests=payoff`. Expected: PASS.
- [ ] **Step 5: Commit** `feat: replay the opening after the payoff and cue every respawn`.

### Task 5: Playtest world, gate and handback

**Files:**
- Create: `tools/prepare_payoff_reveal_playtest.py`, following `tools/prepare_payoff_clues_playtest.py` as merged
- Modify: `command/FrozenDawnDebugCommand.java` (dev-only `payoff status` and `payoff reset`, using `PayoffState.setForTest`; not registered in production), `docs/payoff-roadmap.md` (branch 3 status line)

- [ ] **Step 1: Write the prep script.** Save "Payoff Reveal Check", flat, survival, advancement popups off, difficulty preselected, as AGENTS.md requires. Chat buttons for:
  - **Give manual:** gives the guide, so the owner can read What is ORSA and the timeline first.
  - **Heart fallen:** runs `/fd postmaeve set true`.
  - **Die:** runs `/kill @s`.
  - **Reset payoff:** runs the dev reset, so the sequence can run again.
  - **Status:** prints the payoff status.
- [ ] **Step 2: Run** `./gradlew architectVerify` in the background. Expected: BUILD SUCCESSFUL. Then `./gradlew architectMonkey`, the same.
- [ ] **Step 3: Launch** with `python3 tools/prepare_payoff_reveal_playtest.py`, then `./gradlew runClientLab -PfdLabWorld='Payoff Reveal Check' --console=plain` in the background. The owner checks:
  - the line's text and position;
  - the replay;
  - the landing text, before and after a relog;
  - both new pages and the two unread markers;
  - the flicker, with `doImmediateRespawn` on after a reset;
  - the boot cue on a later death, silence on a death inside five minutes, and silence with the toggle off.
  
  Fix what the pass finds, each fix with a failing test first where a test can hold it.
- [ ] **Step 4: Dispatch the branch review** (Opus, see **Branch**), fix real findings, and update the roadmap's branch 3 line. Then commit and offer the PR.

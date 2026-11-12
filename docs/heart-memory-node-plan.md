# Heart Memory Node Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task, inline. The owner avoids subagents, except the one branch review at the end. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Heart's last memory node the player's own run, told from the hive's side. It quotes the Hearth ledgers back one line at a time, with no music, and ends on `You did.` and `Now they do not remember you.`. It is the setup for the death-screen line. Separately, the Successor's `Kevin.` stops being a random bark under combat noise: each Successor says it once, in the quiet of its first stagger.

**Architecture:** There are two carriers:

- **Node 5.** A pure, shared builder turns a small ledger snapshot into the lines to show. The server fills the snapshot from `ReturnedHearthSavedData` and the death stat, and sends it in the existing node payload. The client reveals the lines one at a time and holds the Heart's music down while they show.
- **Kevin.** `HeartSuccessorEntity` takes `Kevin.` out of its random voice pool and schedules it 30 ticks into its first stagger, or its death if it never staggers. One NBT flag per Successor.

**Tech Stack:** Java 21, NeoForge 1.21.1, JUnit in `src/test`, GameTests in `src/gameTest`.

**Spec:** [payoff-roadmap.md](payoff-roadmap.md), branch 5. Notion: the payoff branch spec, sections 6b and 5d. Canon: Canon Hierarchy CH-18 and CH-29, with CH-11, CH-28 and The Edge §13.2 and §13.13.

**Status:** approved by the owner as written, 2026-11-10, Rulings 1 to 7 included. Built 2026-11-10 in `/Volumes/SSD/Work/frozen-dawn-heart-memory-node`: the mockups (`docs/art/heart-memory-node/`, approved with the arrival line wrapped onto two rows), tests, both gates, the owner's visual pass in the save "Heart Memory Check" (`tools/prepare_heart_memory_node_playtest.py`) and an Opus branch review passed. Amendments: Ruling 7's command is `/fd debug heart memory5`, since `heart` sits under `debug`, and it gained dev-only `sample main|peaceful|massacre` previews; lines wider than 300 GUI px wrap. The review's fixes: the panel holds while the game is paused, text too faint for the font is skipped rather than drawn opaque, and the Kevin GameTest schedules every task from its body. Ruling 1 amended by the owner, 2026-11-11: any violation makes a player permanently Orsathae, so the Orsathae test would have hidden `lost` from every violent run; `massacre` is now an emptied Hearth (below). The binding of a permanently vacant resident keeps the id of the resident that left it, a new optional `vacatedEntityId` in the save. Fixture note: a debug-started Heart forms at the Hearth center's stored y, so the playtest opens a pit over it.

**Branch:** `feat/heart-memory-node` in `/Volumes/SSD/Work/frozen-dawn-heart-memory-node`, cut from `feat/maeve-director` once this plan has merged. The build session starts in that worktree and works there. PR into `feat/maeve-director`. The branch review runs on Opus, because the branch changes client/server sync (`HeartMemoryNodeEventPayload`) and merges into a long-lived branch.

## Global Constraints

- **Canon strings go in word for word.** These are the spec's six node 5 lines, the peaceful line and the massacre line, as Ruling 2 gives them. The numbers and days are the only slots. `Now they do not remember you.` keeps its existing key, `overlay.frozendawn.heart_memory.node_5.erased`.
- **Observation, never explanation.** The spec's canon check (The Edge §13.2 and §13.13): the hive watches the player come back and writes it down; it never says why or how. No line says "respawn", "enrollee" or CONTINUITY (CH-11), or when anything began (CH-28). The owner's approval of this plan records that check.
- **Kevin stays one word.** CH-29: the Successor's existing `Kevin.` is the only pointer. Same sound file, same subtitle (`Orsathae-vaen: Kevin.`); no new text, no new line.
- **All text from lang keys,** none hardcoded.
- **Rules for every branch.** Never edit MACS. Never open the Notion page "SEALED: Sequel Seed". Cite canon by ID, and keep Notion-only notes out of code, comments, commits and the PR. New Java names, keys and files use "memory" or "node", never "continuity".

## Rulings

1. **The snapshot.** New `homo/HeartMemoryLedger` record, built on the server when node 5 breaks, for the player who broke it:
   - `contactDay`: the day of `PlayerHiveMemory.firstContactGameTime`, or 0 if never.
   - `cameBack`: `totalVisits - 1`, floored at 0 (the first visit is the arrival, not a return).
   - `violationDay`: the day of the earliest `HearthContactMemory.firstViolationGameTime` across all Hearths, or 0 if none. New read-only `ReturnedHearthSavedData.firstViolationGameTime(UUID)`; no save change.
   - `casualties` and `casualtyDay`: `congregationCasualties()` and the day of `lastCongregationCasualtyGameTime`.
   - `deaths`: the vanilla `Stats.DEATHS` for the player.
   - `massacre`: the player emptied a Hearth: it had residents, every one is permanently vacant, and each vacancy was this player's congregation casualty (`ReturnedHearthSavedData.emptiedHearth(UUID)`). Amended 2026-11-11; it was permanent Orsathae.
   - A day is `floorDiv(gameTime, 24000) + 1`, never below 1, the same count Aurora's recalls use. Day 1 is the first day.
2. **The lines.** Pure `HeartMemoryLedger.lines(): List<MemoryLine>`, where `MemoryLine(String key, Object... args)`. New keys under `overlay.frozendawn.heart_memory.node_5.`:
   - `arrived`: `Day %s. You came down out of the cold. They felt your warmth before they saw you.` Shown when `contactDay` > 0.
   - `came_back`: `You came back %s times. They waited every time.` Shown when `cameBack` ≥ 2.
   - `crossed`: `Day %s. You crossed the line. They wrote it down.` Shown when `violationDay` > 0.
   - `lost`: `Day %s. %s of them did not come back.` Shown when `casualties` > 0 and not `massacre`. The count is a number word from `overlay.frozendawn.number.1` to `.12` (`One` to `Twelve`), and a numeral above twelve.
   - `lost_all`: `Day %s. None of them came back.` Replaces `lost` when `massacre`.
   - `you_did`: `You did.` Shown after `lost` or `lost_all`.
   - `watched`: `They watched you die %s times. They watched you return %s times.` The peaceful line: shown instead of `crossed`, `lost` and `you_did` when there is no violation and no casualty, and only when `deaths` ≥ 2. With exactly one death it is `watched_once`: `They watched you die once. They watched you return once.` With none, it is dropped.
   - `erased` (existing) always closes.
   The old `variant_0` to `variant_3`, `visits` and `casualties` keys go, and so does the variant logic in `HeartMemoryNodeManager.snapshotMemory`.
3. **The payload.** `HeartMemoryNodeEventPayload` replaces `memoryVariant`, `visits` and `casualties` with the seven ledger fields. Nodes 1 to 4 ignore them.
4. **The reveal.** Node 5 only. The panel draws its title, then one line every 70 ticks, each fading in over 18 ticks; earlier lines stay. After the last line it holds 100 ticks, then fades out over 35. Nodes 1 to 4 keep their 220-tick, all-at-once panel. The panel's height grows with the lines shown, so it never draws empty rows. The last line keeps the existing dimmer color.
5. **No music.** While the node 5 panel shows, `HeartBattleMusic` holds the Heart's music at zero, fading out over 20 ticks and back in over 40 after the panel ends. It is a client-side duck; the server's music state is untouched.
6. **Kevin.** In `HeartSuccessorEntity`:
   - `tickVoice` picks from the other four lines only.
   - On its first stagger, the Successor schedules `Kevin.` for 30 ticks later, after the stagger sound. If its death sequence begins first, it schedules it 30 ticks into the death instead (the death runs 60). Either way it plays once per Successor, at the existing volume 2.4 and pitch 0.86.
   - The flag is saved as NBT `KevinSpoken`, so a reload never repeats it. A new Successor generation says it again at its own first stagger.
   - `HeartSuccessorPolicy.kevinDue(boolean spoken, int staggerElapsed, int deathElapsed): boolean` holds the timing so it unit-tests.
7. **Dev command.** `/fd debug hearth heart memory5` shows the node 5 panel to the caller from their own ledger, without breaking a node. It is dev builds only, as `/fd payoff` is.

## Review Focus

1. **A player who never met a Hearth.** No contact, no visits and no deaths: the panel is the title and `Now they do not remember you.` alone. Task 1's unit test holds it.
2. **Exactly one death, one return or one casualty.** No "1 times", and no sentence that opens on a numeral. Task 1's unit test holds `watched_once` and `One of them`.
3. **A massacre run with no recorded violation.** `lost_all` and `You did.` still show. Task 1's unit test holds it.
4. **A reload mid-fight.** A Successor that already said `Kevin.` never says it again; one that hadn't still can. Task 2's GameTest holds it.
5. **The Heart's music after node 5.** The duck lets go once the panel ends, even if the player logs out mid-panel. The owner checks it in the pass; `reset()` clears the duck.

---

### Task 0: Mockup

- [ ] **Step 1: Draw the node 5 panel at Minecraft GUI scale** (16 px per block, the Minecraft font, nothing smoothed), three frames:
  - the main run, all six lines shown;
  - a peaceful run;
  - a massacre run.
- [ ] **Step 2: Show them to the owner.** Task 1's client step waits for approval; Task 1's builder and Task 2 don't.

### Task 1: Node 5 from the ledgers

**Files:**
- Create:
  - `src/main/java/com/frozendawn/homo/HeartMemoryLedger.java`
  - `src/test/java/com/frozendawn/homo/HeartMemoryLedgerTest.java`
  - `src/gameTest/java/com/frozendawn/homo/HeartMemoryNodeGameTest.java`
- Modify:
  - `homo/HeartMemoryNodeManager.java`
  - `data/ReturnedHearthSavedData.java` (`firstViolationGameTime(UUID)`, read only)
  - `network/HeartMemoryNodeEventPayload.java`
  - `client/HeartMemoryNodeClient.java`
  - `client/HeartBattleMusic.java`
  - `command/` (the Ruling 7 command) and `src/test/java/com/frozendawn/command/FrozenDawnCommandTreeTest.java`
  - `assets/frozendawn/lang/en_us.json`

**Interfaces:**
- Produces:
  - `HeartMemoryLedger(int contactDay, int cameBack, int violationDay, int casualties, int casualtyDay, int deaths, boolean massacre)`
  - `HeartMemoryLedger.lines(): List<HeartMemoryLedger.MemoryLine>`
  - `HeartMemoryLedger.day(long gameTime): int`
  - `HeartMemoryLedger.of(ServerPlayer): HeartMemoryLedger`

- [ ] **Step 1: Write the failing unit tests** in `HeartMemoryLedgerTest`:
  - `theMainRunReadsAsTheSpec`: `(4, 12, 31, 3, 38, 5, false)` gives the keys `arrived, came_back, crossed, lost, you_did, erased` in that order, with args `4`, `12`, `31`, then `38` and `Three`.
  - `aPeacefulRunCountsDeaths`: `(4, 12, 0, 0, 0, 7, false)` gives `arrived, came_back, watched, erased`; one death gives `watched_once`; no deaths drops it.
  - `aMassacreSaysNone`: `massacre` true with no violation gives `lost_all` and `you_did`, never `lost`.
  - `aStrangerGetsOnlyTheLastLine`: all zero gives `erased` alone.
  - `oneCameBackIsDropped`: `cameBack` 1 drops `came_back`.
  - `daysStartAtOne`: `day(0)` and `day(23999)` are 1, and `day(24000)` is 2.
  - `nodeFiveTextIsTheSpec`: each new lang value is exact per Ruling 2; none contains "respawn", "enrollee" or "continuity", ignoring case.
- [ ] **Step 2: Run** them. Expected: FAIL.
- [ ] **Step 3: Implement** Rulings 1 to 3, the builder, payload and server side.
- [ ] **Step 4: Run** the unit tests. Expected: PASS.
- [ ] **Step 5: Implement** Rulings 4, 5 and 7, the client side, once the owner has approved Task 0.
- [ ] **Step 6: Write the GameTest** `ledgerReadsTheHearthRecord` in `HeartMemoryNodeGameTest`: give a mock player a recorded first contact, three visits, a violation and two casualties in `ReturnedHearthSavedData`, then check `HeartMemoryLedger.of` returns their days and counts. Clear the player's records after.
- [ ] **Step 7: Run** `./gradlew gameTestQuick -PfdTests=ledgerreads`. Expected: PASS. (`-PfdTests` matches test method names, not batches.)
- [ ] **Step 8: Commit** `feat: let the last memory node quote the player's run`.

### Task 2: Kevin, heard

**Files:**
- Modify: `entity/HeartSuccessorEntity.java`, `homo/HeartSuccessorPolicy.java`
- Test: `src/test/java/com/frozendawn/homo/HeartSuccessorPolicyTest.java` (create if missing), `HeartMemoryNodeGameTest`

**Interfaces:**
- Produces: `HeartSuccessorPolicy.kevinDue(boolean spoken, int staggerElapsed, int deathElapsed): boolean` (an elapsed value of -1 means not started)

- [ ] **Step 1: Write the failing unit tests:**
  - `kevinWaitsForTheStagger`: due at stagger tick 30, not at 29, and never once spoken.
  - `kevinFallsBackToTheDeath`: with no stagger, due at death tick 30.
  - `kevinIsNotABark`: the random voice pool has four entries and no `voice_kevin`.
- [ ] **Step 2: Run** them. Expected: FAIL.
- [ ] **Step 3: Implement** Ruling 6.
- [ ] **Step 4: Run** the unit tests. Expected: PASS.
- [ ] **Step 5: Write the GameTest** `kevinOncePerSuccessor`: spawn a Successor, stagger it, and wait 31 ticks, so `KevinSpoken` is true. Save and load its NBT into a new entity, stagger that one, and it stays spoken without scheduling again.
- [ ] **Step 6: Run** `./gradlew gameTestQuick -PfdTests=kevinoncepersuccessor`. Expected: PASS.
- [ ] **Step 7: Commit** `feat: let the Successor say Kevin in the quiet`.

### Task 3: Playtest world, gate and handback

**Files:**
- Create: `tools/prepare_heart_memory_node_playtest.py`
- Modify: `docs/payoff-roadmap.md` (branch 5 line)

- [ ] **Step 1: Write the prep script.** Save "Heart Memory Check": survival, advancement popups off and difficulty preselected, as AGENTS.md requires. Give it chat buttons for:
  - `/fd debug hearth heart memory5`;
  - starting a live Heart, the way `prepare_thaeven_loop_playtest.py` does;
  - recording a contact, visits, a violation and a casualty for the caller through the existing `/fd debug hearth` commands.
- [ ] **Step 2: Run** `./gradlew architectVerify` in the background. Expected: BUILD SUCCESSFUL. Then run `./gradlew architectMonkey`, with the same expectation.
- [ ] **Step 3: Launch** with `python3 tools/prepare_heart_memory_node_playtest.py`, then `./gradlew runClientLab -PfdLabWorld='Heart Memory Check' --console=plain` in the background. The owner checks:
  - The node 5 panel, line by line: its pace, and that the music drops out and comes back.
  - A peaceful ledger and a violent one.
  - A Successor's first stagger: `Kevin.` lands in the quiet, once.
- [ ] **Step 4: Dispatch the branch review** (Opus, see **Branch**) and fix real findings, each with a failing test first. Update the roadmap's branch 5 line and the checkpoint, then commit, run both gates on the final head and offer the PR.

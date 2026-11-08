# Payoff Clues Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task, inline. The owner avoids subagents, except the one branch review at the end. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Put the gun payoff's clues into every world before the payoff exists. The forecast's enrollee line, Kevin as a registered enrollee, the 2071 signal and the uplink moved earlier. Emergency EVA text that can't be mistaken for the coming death-screen line. A manual entry for the Thaeven Translator.

**Architecture:** Text only, plus one hidden advancement. Book pages are edited in their entry JSON, HUD and toast words in `en_us.json`. Every text change is guarded by one unit test class. No Java outside `src/test`, no save format, no sync.

**Tech Stack:** Java 21, NeoForge 1.21.1, Patchouli, JUnit in `src/test`.

**Spec:** [payoff-roadmap.md](payoff-roadmap.md), branch 2. Notion: the payoff branch spec, sections 6, 6a, 6d step 1, 6h and "Supporting work" under 6e. Canon Hierarchy CH-11, CH-31, CH-33, CH-35, CH-36 and CH-39.

**Status:** drafted 2026-11-07 for the owner's approval. Nothing is built.

**Branch:** `feat/payoff-clues` in `/Volumes/SSD/Work/frozen-dawn-payoff-clues`, cut from `feat/maeve-director` once this plan has merged. The build session starts in that worktree and works there. PR into `feat/maeve-director`. It merges into a long-lived branch, so the branch review runs on Opus.

## Global Constraints

- Canon strings go in word for word: `Projected enrollees remaining at terminus: 1.`, `Kevin remains a registered enrollee. His seat is being held.`, and the 2071 fragment in Task 2.
- No new text contains the word "continuity" in any case (CH-11). No new redactions. Capitalization follows CH-33.
- Never edit MACS. Never open the Notion page "SEALED: Sequel Seed". Cite canon by ID; no Notion-only notes in code, comments, commits or the PR.
- No text explains what the clues mean.

## Rulings

1. **The forecast line opens the last page.** Page 7 of `projected_timeline.json` becomes `Projected enrollees remaining at terminus: 1.$(br2)You are the unluckiest person alive, ...` with the rest unchanged. The count comes straight before "You", and it still sits above the sign-off, as the spec asks.
2. **Kevin's line opens the second Kevin page.** Page 9 has little room left, so the line goes first on page 10: `Kevin remains a registered enrollee. His seat is being held.$(br2)If you encounter Kevin, please tell him the shuttle already left.` The held seat against the shuttle that left is the bulletin's own joke.
3. **The 2071 fragment is a new page 2 in `satellite_log.json`,** straight after the corrupted-data intro, with no title. Text, line by line with `$(br)`, the first line bold:

```txt
[FRAGMENT — TIMESTAMP INTEGRITY: VERIFIED]
Deep-band transponder signature received.
Duration: 90 minutes.
Origin: surface, below shelf depth.
Date: 2071.
Flagged: forwarded to seventh floor.
```

4. **ORSA Uplink moves to Phases 3-4,** as the last item of that list on page 3 of `start_here.json`, before the danger line. It leaves The Way Out.
5. **EVA wording (CH-36).** In `en_us.json`:

| Key | New value |
|---|---|
| `hud.frozendawn.emergency_eva.active` | `EMERGENCY EVA ONLINE` |
| `hud.frozendawn.emergency_eva.service_remaining` | `Service life: %s` |
| `tooltip.frozendawn.emergency_eva.remaining` | `Emergency service life: %s` |
| `ui.frozendawn.suit.emergency_eva_service_low` | `CP-021 // Five minutes of service left. Replacement required.` |
| `ui.frozendawn.suit.emergency_eva_reserve_low` | `CP-022 // Reserve low. Two minutes or less left.` |
| `ui.frozendawn.suit.emergency_eva_reserve_critical` | `CP-023 // Reserve critical. One minute or less left.` |
| `ui.frozendawn.suit.emergency_eva_service_reserve_low` | `CP-028 // Emergency service low. Two minutes or less of thermal power left.` |
| `ui.frozendawn.suit.emergency_eva_oxygen_depleted` | `Emergency oxygen depleted. Seek breathable air or connect a primary oxygen supply. Thermal support stays on.` |

   Key names stay, so no Java changes. CP-028 and the oxygen line weren't in the spec's list; they break the same rule, so they're in.
6. **The two voiced EVA lines stay as recorded.** `ui.suit.emergency_eva_active` ("Emergency EVA active. ...") and `ui.suit.emergency_eva_ambient` ("... remains active.") are generated voice, and their subtitles and toasts must match the audio. They play once, on equip and on breathable air, not on the HUD. The rule test names them as its only exceptions. Re-recording them would be a separate audio change.
7. **The Translator entry.** In `orsa_rd`, sortnum 7, icon `frozendawn:thaeven_translator`, name "Thaeven Translator". Page 0 is text:

```txt
Reads Thaeven writing: the marks the Returned leave on relics, walls and objects.$(br2)Keep it in your inventory, then examine the writing. What it reads is kept as a record. Use the Translator to open your records.$(br2)$(italic)The oldest writing is kept at a Hearth.$()
```

   Page 1 is `patchouli:crafting` for `frozendawn:thaeven_translator`, with the text `The ORSA Multitool is a catalyst: it stays in the grid.` The entry never names Record I and never says who built the Translator.
8. **The entry unlocks when the player knows the Translator.** New advancement `frozendawn:knows_thaeven_translator` with no parent and no `display`, so it is never shown: no tab, no toast, no chat. A display without a parent would open a new advancement tab. It has two criteria, either one enough: `minecraft:recipe_unlocked` for `frozendawn:thaeven_translator`, or `minecraft:inventory_changed` holding `frozendawn:thaeven_translator`. The second covers saves that learned the recipe before this branch, since `recipe_unlocked` fires only on a new unlock.

## Review Focus

1. **A page overflows.** Patchouli cuts text that runs off a page without warning. Pages 7 (timeline), 10 (bulletin), the new satellite page and the Translator's page 0 are checked by eye in the visual pass (Task 4).
2. **A voiced line goes out of sync.** If someone edits the two voiced subtitles, the words no longer match the audio. Task 1's test fails on any change to them.
3. **An old save never unlocks the entry.** A player who learned the recipe before the update has no new unlock to fire. Ruling 8's second criterion covers them once they hold one; Task 3's test holds both criteria.
4. **The crafting page shows the Multitool as consumed.** The catalyst lives in a custom recipe class. Page 1's text says it stays, and the visual pass checks the page renders at all.
5. **A later EVA string reintroduces the clash.** Task 1's test covers every key in the EVA and suit families, not just today's eight.

---

### Task 1: EVA wording that can't read as the death-screen line

**Files:**
- Modify: `src/main/resources/assets/frozendawn/lang/en_us.json` (Ruling 5's eight keys)
- Create: `src/test/java/com/frozendawn/lore/PayoffCluesTest.java`

**Interfaces:**
- Produces: `PayoffCluesTest` with static helpers `lang()` (the parsed `en_us.json`) and `entry(String path)` (a parsed book entry under `.../en_us/entries/`), used by Tasks 2 and 3.

- [ ] **Step 1: Write the failing test** `evaTextNeverSaysRemainingOrActive`: for every lang key starting with `hud.frozendawn.emergency_eva.`, `hud.frozendawn.continuity.`, `tooltip.frozendawn.emergency_eva.`, `ui.frozendawn.suit.emergency_eva`, `ui.frozendawn.suit.continuity` or `subtitle.frozendawn.ui.suit.emergency_eva`, the value doesn't match `(?i)\b(remaining|active)\b`. The exceptions are exactly `ui.frozendawn.suit.emergency_eva_active`, `subtitle.frozendawn.ui.suit.emergency_eva_active`, `ui.frozendawn.suit.emergency_eva_ambient` and `subtitle.frozendawn.ui.suit.emergency_eva_ambient`.
- [ ] **Step 2: Write** `voicedEvaLinesMatchTheirRecordings`: those four keys equal their current values, copied into the test.
- [ ] **Step 3: Run** `./gradlew test --tests com.frozendawn.lore.PayoffCluesTest`. Expected: step 1 fails, naming the eight keys; step 2 passes.
- [ ] **Step 4: Change the eight values** to Ruling 5's table.
- [ ] **Step 5: Run the same command.** Expected: PASS.
- [ ] **Step 6: Commit** `feat: reword Emergency EVA status text`, staging the two files by name.

### Task 2: The book clues

**Files:**
- Modify: `.../entries/orsa_intel/projected_timeline.json` (page 7), `.../entries/orsa_intel/orsa_bulletin.json` (page 10), `.../entries/orsa_intel/satellite_log.json` (new page 2), `.../entries/getting_started/start_here.json` (pages 3 and 6)
- Test: `PayoffCluesTest`

**Interfaces:**
- Consumes: `lang()` and `entry(String)` from Task 1.

- [ ] **Step 1: Write the failing tests:**
  - `theForecastCountsOne`: page 7's text starts with `Projected enrollees remaining at terminus: 1.$(br2)You are the unluckiest person alive`.
  - `kevinIsARegisteredEnrollee`: page 10's text starts with `Kevin remains a registered enrollee. His seat is being held.$(br2)`.
  - `the2071SignalWasLogged`: the satellite log has 9 pages. Page 2's text, with `$(bold)`, `$()` and `$(br)` stripped to newlines, equals Ruling 3's six lines. Page 3 is the old page 2, titled "Relay Network Status".
  - `theUplinkComesInPhasesThreeToFour`: `$(l:signals/uplink)` is on page 3 of Start Here and not on page 6.
  - `noClueSaysTheWord`: none of the new strings from Rulings 1 to 3 and 7 contains "continuity", ignoring case.
- [ ] **Step 2: Run** the test class. Expected: the five new tests fail.
- [ ] **Step 3: Make the four JSON edits** as Rulings 1 to 4 give them.
- [ ] **Step 4: Run** the test class. Expected: PASS.
- [ ] **Step 5: Commit** `feat: plant the payoff clues in the manual`.

### Task 3: The Translator's manual entry

**Files:**
- Create: `.../entries/orsa_rd/thaeven_translator.json`, `src/main/resources/data/frozendawn/advancement/knows_thaeven_translator.json`
- Test: `PayoffCluesTest`

- [ ] **Step 1: Write the failing test** `theTranslatorEntryUnlocksWhenYouKnowIt`: the entry exists, with category `frozendawn:orsa_rd` and advancement `frozendawn:knows_thaeven_translator`. Its page 1 is `patchouli:crafting` with recipe `frozendawn:thaeven_translator`. The advancement file exists with no `parent` and no `display`, and its criteria are exactly a `minecraft:recipe_unlocked` for the Translator and a `minecraft:inventory_changed` for the item, under one requirement group (either one is enough).
- [ ] **Step 2: Run** it. Expected: FAIL, the entry is missing.
- [ ] **Step 3: Write both files** per Rulings 7 and 8.
- [ ] **Step 4: Run** the test class. Expected: PASS.
- [ ] **Step 5: Commit** `feat: add the Thaeven Translator manual entry`.

### Task 4: Playtest world, gate and handback

**Files:**
- Create: `tools/prepare_payoff_clues_playtest.py`, following `tools/prepare_miller_m6_playtest.py` (title, namespace, guarded setup, chat buttons)
- Modify: `docs/payoff-roadmap.md` (branch 2 status line)

- [ ] **Step 1: Write the prep script.** Save "Payoff Clues Check", flat and creative, advancement popups off, difficulty preselected, as AGENTS.md requires. Chat buttons for:
  - **Give manual:** gives the guide.
  - **Unlock entries:** grants `found_orsa_bulletin`, `found_satellite_log` and `knows_thaeven_translator`.
  - **Give Translator:** gives one, to check that the entry unlocks from holding it, on a fresh player.
  - **Low EVA service:** gives an Emergency EVA with its service component set near five minutes, so CP-021 shows (see `ModDataComponents`).
  - **Respawn:** kills the player, so the reissued Emergency EVA's HUD shows.
- [ ] **Step 2: Run** `./gradlew architectVerify` in the background. Expected: BUILD SUCCESSFUL. Then `./gradlew architectMonkey`, the same.
- [ ] **Step 3: Launch** with `python3 tools/prepare_payoff_clues_playtest.py`, then `./gradlew runClientLab -PfdLabWorld='Payoff Clues Check' --console=plain` in the background. The owner reads the four pages and the Translator entry for overflow, the HUD and tooltip, and a CP toast. Fix what the pass finds, each fix with a failing test first where a test can hold it.
- [ ] **Step 4: Dispatch the branch review** (Opus, see **Branch**), fix real findings, and update the roadmap's branch 2 line. Then commit and offer the PR.

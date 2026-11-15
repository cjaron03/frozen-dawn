# Kevin's Blanket Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task, inline. The owner avoids subagents, except the one branch review at the end. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build CH-38. Kevin found the player: he covered them with an ORSA Thermal Blanket, left the Evacuation Notice in their hand, and kept looking. The blanket is a new item whose only text is a stitched `K.` on its tooltip. It gives +5°C while it is anywhere in the player's inventory, and nothing else. Slice 1 is creative-only. Slice 2 puts the blanket, and the Evacuation Notice it depends on, in a new player's hands on first join.

**Architecture:** One item class and one line in the player temperature sum, beside Aurora's warmth. Slice 2 adds two items to the existing first-join grant in `WorldTickHandler`.

**Tech Stack:** Java 21, NeoForge 1.21.1, JUnit in `src/test`, GameTests in `src/gameTest`.

**Spec:** [payoff-roadmap.md](payoff-roadmap.md), branch 8. Notion: the payoff branch spec, section 6g. Canon: Canon Hierarchy CH-38, with CH-29.

**Status:** drafted 2026-11-14 for the owner's approval. Nothing is built.

**Branch:** `feat/kevins-blanket` in `/Volumes/SSD/Work/frozen-dawn-kevins-blanket`, cut from `feat/maeve-director` once this plan has merged. The build session starts in that worktree and works there. PR into `feat/maeve-director`. The branch review runs on Opus, because the branch merges into a long-lived branch. It changes no save format and no sync.

## Found while planning

The Evacuation Notice has never reached a player. `StarterBooks.createEvacuationNotice()` builds it, with Kevin's handwritten last page ("If you're reading this, we missed you. ... I'm sorry. — Kevin, Logistics Division"), but nothing has called it since it was added in 74ed5579. The first-join grant (`WorldTickHandler`, flag `frozendawn:received_books`) gives only the field manual. Kevin's page exists nowhere else in the game. The spec and CH-38 both assume the player holds it ("left the Evacuation Notice in their hand"), so Ruling 4 adds it to the grant.

## Global Constraints

- **The blanket's only text is `K.`** No lore page, no Aurora line, no manual entry, no advancement. The item name is the plain `ORSA Thermal Blanket`. No letter or mark is drawn on the texture.
- **Exactly +5°C,** the same as Aurora's `ShepherdPolicy.WARMTH_C`, only while it is in the player's inventory. Nothing when it is in a container, dropped or in another player's bag. It is not a block and cannot be placed. It never changes after the Heart or CH-18.
- **Never stated:** that Kevin reached the player, covered them or left the notice (CH-38). The UN notice's `Kevin did not find you.` stays as it is.
- **Creative-only first** (AGENTS.md). Slice 1 adds the creative tab entry and `/give` only. Slice 2, the first-join grant, is built only after the owner has played slice 1 and said go. No recipe, no loot and no world generation in either slice.
- **Rules for every branch.** Never edit MACS. Never open the Notion page "SEALED: Sequel Seed". Cite canon by ID, and keep Notion-only notes out of code, comments, commits and the PR. New Java names, keys and files use "blanket", never "continuity".

## Rulings

1. **The item.** `orsa_thermal_blanket`, a plain `ThermalBlanketItem extends Item`, stack size 1. It is not a `ComfortItem` and has no sanity or comfort effect. Lang: `item.frozendawn.orsa_thermal_blanket` = `ORSA Thermal Blanket`, and `tooltip.frozendawn.orsa_thermal_blanket.stitch` = `K.`, drawn in italic dark gray, like other stitched or handwritten details. Creative tab: in the main tab, beside `MIEL_HONEY`.
2. **The warmth.** `ThermalBlanketItem.warmth(ServerPlayer): float` returns `ShepherdPolicy.WARMTH_C` (5) if `player.getInventory()` holds one anywhere (main, hotbar, offhand or armor slots), otherwise 0. Two blankets still give 5. `PlayerTickHandler.applyPlayerTemperatureAdjustments` adds it on the line after `ShepherdWarmth.of(player)`, so the two stack to +10 when both apply.
3. **The texture.** A 16 by 16 item texture, `item/orsa_thermal_blanket.png`, with an `item/generated` model: a folded emergency blanket in ORSA's silver and safety orange, with blocky folds and no smoothing. It is drawn for Task 0's mockup and approved before it ships.
4. **Slice 2, the grant (owner's go only).** In the first-join block of `WorldTickHandler`, under the same `frozendawn:received_books` flag, the player also gets `StarterBooks.createEvacuationNotice()` and one blanket, after the field manual. A player who already joined a world before this branch never gets them, so no blanket arrives out of context. When the inventory is full, each item drops at the player's feet, as `Inventory.add` leaves it.
5. **Dev command.** None needed: `/give @s frozendawn:orsa_thermal_blanket` covers slice 1, and slice 2 is checked in a fresh world.

## Review Focus

1. **The blanket in a chest.** It warms only from the player's own inventory. Task 1's GameTest holds it.
2. **Two blankets, or a blanket and Aurora.** Two blankets give 5, and the blanket and Aurora give 10. Task 1's GameTest holds the first; the second follows from Ruling 2's sum.
3. **After the Heart and the payoff.** With the saved erased flag and the payoff fired, the blanket still gives 5. Task 1's GameTest holds it.
4. **A returning player.** Slice 2 never grants anything to a player whose `received_books` flag is already set. Task 2's GameTest holds it.
5. **A full inventory on first join.** Nothing is lost: the notice and the blanket drop at the player's feet. The owner can check it in the slice 2 pass.

---

### Task 0: Mockup

- [ ] **Step 1: Draw the blanket's texture** at 16 pixels per block, in the Minecraft item style, with three views: the texture alone at 8x, the item in a hotbar slot at GUI scale 2, and its tooltip (`ORSA Thermal Blanket` over an italic `K.`).
- [ ] **Step 2: Show it to the owner.** Task 1's texture and model wait for approval; its code and tests don't.

### Task 1: Slice 1, the blanket

**Files:**
- Create:
  - `src/main/java/com/frozendawn/item/ThermalBlanketItem.java`
  - `src/main/resources/assets/frozendawn/textures/item/orsa_thermal_blanket.png`
  - `src/main/resources/assets/frozendawn/models/item/orsa_thermal_blanket.json`
  - `src/test/java/com/frozendawn/item/ThermalBlanketTextTest.java`
  - `src/gameTest/java/com/frozendawn/item/ThermalBlanketGameTest.java`
- Modify: `init/ModItems.java` (registration and creative tab), `event/PlayerTickHandler.java` (one line), `assets/frozendawn/lang/en_us.json`

**Interfaces:**
- Produces: `ThermalBlanketItem.warmth(ServerPlayer): float`

- [ ] **Step 1: Write the failing unit test** `theBlanketSaysOnlyK`: the name is `ORSA Thermal Blanket` and the tooltip value is exactly `K.`. No other `orsa_thermal_blanket` lang key exists.
- [ ] **Step 2: Run** it. Expected: FAIL.
- [ ] **Step 3: Implement** Rulings 1 and 2. Add the texture and model once the owner has approved Task 0.
- [ ] **Step 4: Run** it. Expected: PASS.
- [ ] **Step 5: Write the GameTests** in `ThermalBlanketGameTest`, with a mock player:
  - `aBlanketInTheBagWarms`: `warmth` is 5 with the blanket in the hotbar, in main inventory, and in the offhand, and 0 with an empty inventory.
  - `aBlanketElsewhereDoesNot`: `warmth` is 0 with the blanket in a chest beside the player or dropped at their feet.
  - `twoBlanketsAreOne`: two blankets give 5.
  - `itOutlastsTheHeart`: with the saved erased flag set and the payoff fired (`PayoffState.setForTest`), `warmth` is still 5. Reset both after.
- [ ] **Step 6: Run** `./gradlew gameTestQuick -PfdTests=ablanketinthebag,ablanketelsewhere,twoblanketsareone,itoutlaststheheart`. Expected: PASS. (`-PfdTests` matches method names, not batches.)
- [ ] **Step 7: Commit** `feat: add Kevin's thermal blanket to the creative tab`.

### Task 2: Slice 1 pass, then slice 2 on the owner's go

**Files:**
- Create: `tools/prepare_kevins_blanket_playtest.py`
- Modify (slice 2 only): `event/WorldTickHandler.java`, `ThermalBlanketGameTest`
- Modify: `docs/payoff-roadmap.md` (branch 8 line)

- [ ] **Step 1: Write the prep script.** Save "Kevin's Blanket Check": survival, Phase 1 cold, advancement popups off and difficulty preselected, as AGENTS.md requires, with chat buttons for `/give @s frozendawn:orsa_thermal_blanket` and for dropping it.
- [ ] **Step 2: Run** `./gradlew architectVerify` in the background. Expected: BUILD SUCCESSFUL. Then run `./gradlew architectMonkey`, with the same expectation.
- [ ] **Step 3: Launch** with `python3 tools/prepare_kevins_blanket_playtest.py`, then `./gradlew runClientLab -PfdLabWorld="Kevin's Blanket Check" --console=plain` in the background. The owner checks the texture in hand and in the hotbar, the tooltip, and the temperature readout rising by 5 with it in the bag and falling when it is dropped.
- [ ] **Step 4: Ask the owner for slice 2.** On their go only:
  - Write the failing GameTest `theFirstJoinGrantsNoticeAndBlanket`: a mock player's first login adds the Evacuation Notice and one blanket. A second login, or a player whose `received_books` flag is already set, adds nothing.
  - Run it. Expected: FAIL. Implement Ruling 4. Run it. Expected: PASS.
  - Commit `feat: hand new players Kevin's notice and blanket on first join`.
  - Give the prep script a button that opens a fresh world, and let the owner check the first join.
- [ ] **Step 5: Dispatch the branch review** (Opus, see **Branch**) and fix real findings, each with a failing test first. Update the roadmap's branch 8 line and the checkpoint, then commit, run both gates on the final head and offer the PR.

# Manual Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task, inline. The owner avoids subagents, except the one branch review at the end. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A player who types an item's name into the ORSA Field Manual's search bar finds every entry about that item. One line on Start Here tells them the search exists.

**Architecture:** Book JSON only. No new pages, no Java outside `src/test`, no save format, no sync, no custom search UI. Entries become findable by marking existing spotlight pages `link_recipe: true` and by adding `extra_recipe_mappings`, neither of which changes what a page shows.

**Tech Stack:** Patchouli 1.21.1-92 (NeoForge), JUnit in `src/test`.

**Status:** approved: the owner merged it as PR 170.

**Branch:** `feat/manual-search` in `/Volumes/SSD/Work/frozen-dawn-manual-search`, cut from `feat/maeve-director` once this plan has merged. PR into `feat/maeve-director`. It merges into a long-lived branch, so the branch review runs on Opus. Branch 3 (`feat/payoff-reveal`) is planned in parallel; this branch touches no file it plans to touch except possibly Start Here, and only page 0 here.

## How search works (checked in the 1.21.1-92 sources)

- `GuiBookEntryList` lowercases the query and keeps entries where `BookEntry.isFoundByQuery` is true: the query is a substring of the entry title, or of the hover name of a stack in the entry's `relevantStacks`. Page text is never searched.
- Only `BookEntry.addRelevantStack` fills `relevantStacks`. Its callers:
  - `crafting` / `smelting` pages, through `PageDoubleRecipeRegistry`, when `link_recipe` is true. **It defaults to true.**
  - `spotlight` pages, when `link_recipe` is true. **It defaults to false.** A spotlight without it shows the item but search can't find it.
  - `extra_recipe_mappings` in the entry root: every key is added, at the page given. **Verified: mappings count toward search.** The page must be inside the entry, or Patchouli logs a warning and skips it.
  - Entity and multiblock pages add nothing.
- The same call also registers the item for Patchouli's in-inventory recipe lookup, in a map where the last entry built wins (`BookContentsBuilder.addRecipeMapping` is a plain `put`, entries built in map order). So mapping an item that already has a home entry onto a second entry can send its lookup to the wrong place, unpredictably.

## Global Constraints

- No visible page changes except the one Start Here line.
- Map only items that have **no home entry** (no linked crafting, smelting or spotlight page anywhere else). Never map an item onto a second entry when it already has one (see Rulings 3 and 4).
- No new text contains the word "continuity" in any case.

## Rulings

1. **Spotlights that already show an item get `link_recipe: true`** where that item has no other home. Zero visual change.

| Entry | Spotlight item(s) |
|---|---|
| `advanced/rocket_assembly` | `rocket_engine` (both pages), `rocket_hull` |
| `fabricator/overview` | `fabricator` |
| `fabricator/slates` | `data_slate` |
| `getting_started/ice_fishing` | `glassblood`, `rime_hagfish`, `longsleeper` |
| `getting_started/seafood_boil` | `seafood_boil_bowl` |
| `orsa_equipment/comfort_items` | `miel_honey` |
| `icarus/overview` | `icarus_controller` |
| `icarus/fuel` | `antimatter_capsule` |
| `sfr/overview` | `sfr_controller` |

2. **Text-only multiblock entries get `extra_recipe_mappings`** for the parts they describe. None of these items has a home entry today, so today "Matter Injector" or "Deuterium" finds nothing.

| Entry | Mappings (item: page) |
|---|---|
| `icarus/getting_it` | `icarus_frame`, `icarus_matter_port`, `icarus_power_port`, `icarus_capsule_dock`, `icarus_reserve_bank`, `icarus_injector_casing`, `icarus_stabilizer_housing`: 0. `halsey_core`, `icarus_stabilizer`, `icarus_injector`, `antiparticle_foundry`: 0 |
| `icarus/fuel` | `empty_antimatter_capsule`: 1 |
| `sfr/building` | `magnet_coil`, `confinement_core`, `first_wall`, `lithium_blanket`: 0. `sfr_fuel_port`, `sfr_power_port`, `sfr_vacuum_port`, `sfr_maintenance_hatch`: 2. `high_temperature_radiator`: 3 |
| `sfr/fuel` | `deuterium_canister`, `isotope_column`: 0. `lithium_target_rod`: 1. `raw_spodumene`, `deepslate_spodumene_ore`, `pegmatite`, `lithium_carbonate`, `lithium`: 2. `helium_canister`: 3 |
| `reactor/meltdown` | add `molten_corium`: 2 (beside the existing `corium`) |
| `orsa_intel/field_documents` | `orsa_document`: 0 |

   The build step confirms each item has no crafting or smelting page elsewhere before mapping it (Task 1's test enforces it).

3. **Concept entries are left alone.** Room Temperature, Insulation, Room Breaches, Running Out of Air, Flames in a Vacuum, Airlock Oxygen, Core Upgrades, Power Basics, Radiation Basics, Shielding, Contamination, Uplink Modes, Hyperion Power/Quench/Reading, Reactor Operation/Alarms, Icarus Running/Startup/Stopping/Failure, SFR Operation, Hypothermia, Hyperthermia, Food Spoilage, Bomb Cyclones, the timeline pages and Frostbite Warning. Every item they discuss (Thermal Heater, O2 Canister, Geiger Counter, Block of Lead, Reactor Core and so on) has a home entry that search already finds, and that home links onward. Mapping those items here would steal their inventory lookup (see How search works). Example: "heater" finds Thermal Heater, Heater Upgrades and ORSA RH-5; Room Temperature is one link from Thermal Heater.
4. **Already-homed spotlights stay unlinked:** `heating/power_cell_salvage` (`power_cell` lives in Power Storage), `hyperion/overview` (`hyperion_controller` is crafted in Building It), `reactor/overview` (`reactor_controller` is crafted in Building the Shell). Their titles still match "Hyperion", "Hearthstone" and so on.
5. **`signals/uplink_rewards` stays unlinked.** Its spotlights are vanilla `filled_map`; linking would send every map's inventory lookup to Transmissions.
6. **Locked lore entries are left alone.** Patchouli never matches a locked entry, and their titles match once unlocked.
7. **Threat entries are left alone.** Entity pages add no stacks, and each title is the mob's name.
8. **Start Here, page 0** (the "Start Here" text page, the first page a new player sees). Insert before the flavor line, so the page reads:

```txt
A reading order for the whole manual. Skip what you know.$(br2)Timing varies; the $(l:getting_started/phase_timeline)Phase Timeline$(/l) shows what's coming. Key numbers: $(l:getting_started/quick_reference)Quick Reference$(/l).$(br2)Search an item's name on the front page to find every entry about it.$(br2)$(flavor)ORSA recommends reading before freezing.$()
```

   "Front page" is the book's landing page, where Patchouli puts the search bar.

## Review Focus

1. **A mapping points at a missing item or page.** Patchouli only logs a warning and skips it, so search silently misses. Task 1's test checks every key against the lang file and every page index against the entry.
2. **A mapping steals an item's lookup.** Task 1's test fails if a mapped or newly linked item also appears as a linked crafting, smelting or spotlight result in another entry.
3. **Page 0 of Start Here overflows.** Patchouli cuts text without warning. Checked by eye in Task 2.
4. **Conflict with `feat/payoff-reveal`.** If that branch edits Start Here too, whichever merges second rebases; the edits are on different pages unless payoff-reveal touches page 0.

---

### Task 1: Make the entries findable

**Files:**
- Modify: the nine entries in Ruling 1 and the six in Ruling 2, under `src/main/resources/assets/frozendawn/patchouli_books/frozen_dawn_guide/en_us/entries/`
- Create: `src/test/java/com/frozendawn/lore/ManualSearchTest.java`

- [ ] **Step 1: Write the failing test** `everyListedItemIsSearchable`: for each entry and item in Rulings 1 and 2, the entry's searchable set contains it. The searchable set is crafting and smelting results unless `link_recipe` is false, spotlight items with `link_recipe` true, and `extra_recipe_mappings` keys, the same rules as How search works.
- [ ] **Step 2: Write** `mappingsResolve`: across the whole book, every `extra_recipe_mappings` key names an item in `en_us.json` (or a `minecraft:` id) and its page is below the entry's page count.
- [ ] **Step 3: Write** `noItemHasTwoHomes`: no item added by this branch is in the searchable set of more than one entry.
- [ ] **Step 4: Run** `./gradlew test --tests com.frozendawn.lore.ManualSearchTest`. Expected: step 1 fails, naming the missing items; steps 2 and 3 pass.
- [ ] **Step 5: Make the JSON edits** per Rulings 1 and 2.
- [ ] **Step 6: Run** the same command. Expected: PASS.
- [ ] **Step 7: Commit** `feat: make manual entries findable by item name`, staging files by name.

### Task 2: Start Here line, playtest world, gate and handback

**Files:**
- Modify: `.../entries/getting_started/start_here.json` (page 0)
- Create: `tools/prepare_manual_search_playtest.py`, following `tools/prepare_payoff_clues_playtest.py`

- [ ] **Step 1: Add the Start Here line** from Ruling 8, and a test `startHereMentionsSearch` in `ManualSearchTest` that page 0 contains it. Run it red first, then green.
- [ ] **Step 2: Commit** `feat: point Start Here at the manual search`.
- [ ] **Step 3: Write the prep script.** Save "Manual Search Check", flat and creative, advancement popups off, difficulty preselected, as AGENTS.md requires. One chat button: **Give manual.**
- [ ] **Step 4: Run** `./gradlew architectVerify` in the background, then `./gradlew architectMonkey`. Expected: BUILD SUCCESSFUL for both.
- [ ] **Step 5: Launch** with `python3 tools/prepare_manual_search_playtest.py`, then `./gradlew runClientLab -PfdLabWorld='Manual Search Check' --console=plain` in the background. The owner reads Start Here page 0 and searches "injector", "deuterium", "lithium", "rocket", "honey", "fabricator", "heater" and "corium", and checks a few inventory lookups (hover an Icarus part, press the lookup key) land on the right entry. Fix what the pass finds.
- [ ] **Step 6: Dispatch the branch review** (Opus, see **Branch**), fix real findings, then offer the PR.

## As built

The owner's visual pass changed three things; the rulings above are kept as the record of the plan.

- **Front-page search bar.** Patchouli searches only inside the Entry Index, behind the last category icon, so "the front page" in Ruling 8 had no search. The owner approved a visible bar, in the nameplate style of the approved mockup, under the category icons: `client/PatchouliLandingSearch.java` draws it and opens the Entry Index search on a click or on the first letter typed, and `client/ManualSearchBarPolicy.java` places it under Patchouli's lower category rule (it hides past 12 icons). This lifts the "no Java outside `src/test`" constraint for this feature. Letter keys are held on the front page, so the inventory key no longer closes the manual there; Escape does.
- **Start Here.** Page 0 was already full (ManualLint: 14 of 14 lines), so its flavor line moved to the end of the reading list, and the new line reads "Type an item's name on the front page to find every entry about it."
- **`molten_corium`** is dropped from Ruling 2: it is a block with no item, and Patchouli rejects the mapping. `mappingsResolve` now requires an item model.
- Locked entries that are not secret, such as Rocket Assembly, still appear in search as Locked rows (Ruling 6 overstated this). The playtest world has an unlock button for it.

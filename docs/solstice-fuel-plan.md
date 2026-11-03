# SFR-1 Fuel Chain Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give SFR-1 fuel real sources: lithium from spodumene or salvage, tritium bred in the CFU-7, deuterium and lithium refining at the Isotope Column, and Helium Canisters from pumped ash. All of it on `feat/solstice-reactor`.

**Architecture:**
- Column job math and the cooldown boost are pure, unit-tested code in `com.frozendawn.power`.
- The Isotope Column is a `PoweredMachine` modeled on `InductionFurnaceBlockEntity`.
- The target rod reuses the CFU-7's rod slots and burn accounting.
- Helium rides on `FusionPhysics.Readout.deuteriumUsed`.

**Tech Stack:** Java 21, NeoForge 1.21.1, JUnit 5 (`src/test`), NeoForge GameTests (`src/gameTest`), hand-written JSON resources, Python/PIL textures, ffmpeg/oggenc sounds.

**Spec:** `docs/solstice-fuel-design.md`. Read it before any task.

## Global Constraints

- Availability is exactly the design's table. The Isotope Column is creative tab and `/give` only, with no recipe. Everything else listed as survival gets its recipe or worldgen.
- No CFU-7 heat, damage or meltdown change. Its rod slots accept a Lithium Target Rod; nothing else changes.
- Registry ids:
  - `pegmatite`, `deepslate_spodumene_ore`, `raw_spodumene`, `lithium_carbonate`, `lithium`
  - `lithium_target_rod`, `isotope_column`, `helium_canister`
  - feature `pegmatite_pod`
- Display names: "Pegmatite", "Deepslate Spodumene Ore", "Raw Spodumene", "Lithium Carbonate", "Lithium", "Lithium Target Rod", "Isotope Column", "Helium Canister".
- Every new GameTest is named `sfr...`, so `./gradlew gameTestQuick -PfdTests=sfr` runs them. Unit tests: `./gradlew test`. The gate is `./gradlew architectVerify`.
- Code style: match the surrounding code. Commits: Conventional (`feat(sfr):`), trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. No emojis.

## Review Focus

1. **A target rod finishing while the CFU-7 output holds a Shielded Cask or spent rods** waits in its slot. It never vanishes or overwrites the output. Pinned in Task 3.
2. **Reloading** keeps a part-cooked target rod, a part-done Column job, the Column's ice buffer and a pending cooldown boost. Pinned in Tasks 1, 3 and 4.
3. **The Column losing power mid-job** pauses with its progress kept. Pinned in Task 4.
4. **Packed or Blue Ice** adds its units without wasting any. A buffer that already covers several canisters stops pulling ice. Pinned in Task 1.
5. **A full Helium slot** vents the ash with no disruption and no item loss. Pinned in Task 5.

---

### Task 1: Pure fuel math

**Files:**
- Create: `src/main/java/com/frozendawn/power/IsotopeColumnLogic.java`
- Modify: `src/main/java/com/frozendawn/power/FusionPhysics.java`
- Test: `src/test/java/com/frozendawn/power/IsotopeColumnLogicTest.java`, `FusionPhysicsTest.java`

**Interfaces:**
- `IsotopeColumnLogic`:
  - constants: `DRAW_WATTS = 10000`, `ICE_PER_CANISTER = 8`, `DEUTERIUM_TICKS = 120 * 20`, `LITHIUM_TICKS = 60 * 20`, `MAX_BUFFER = ICE_PER_CANISTER * 81`
  - `enum Job { NONE, DEUTERIUM, LITHIUM }`
  - `static int iceUnits(Item)`: Ice and Snow Block 1, Packed Ice 9, Blue Ice 81, anything else 0.
  - `static boolean pullIce(int buffer, int units)`: pull only when `buffer < ICE_PER_CANISTER` and `buffer + units <= MAX_BUFFER`.
- `FusionPhysics`:
  - `boolean boostCooldown()`: true and sets `boosted` only in COOLDOWN with progress 0, or in OFF.
  - `boolean cooldownBoosted()`
  - Each cooldown second adds `(roomVacuum ? 2 : 1) * (boosted ? 2 : 1)`.
  - `boosted` clears on reaching READY or on `off(...)`, and saves as NBT `Boosted`.

- [ ] Write tests:
  - `iceUnitsCountPackedAndBlueIce`
  - `pullsIceOnlyWhenBufferIsShort`
  - `heliumHalvesCooldown`: 60 steps in air reach READY.
  - `heliumAndVacuumQuarterCooldown`: 30 steps.
  - `boostSurvivesReload`
- [ ] Run `./gradlew test`. Expect FAIL (missing symbols).
- [ ] Implement.
- [ ] Run `./gradlew test`. Expect PASS.
- [ ] Commit: `feat(sfr): Isotope Column math and helium cooldown boost`.

### Task 2: Lithium materials and pegmatite pods

**Files:**
- Modify:
  - `init/ModBlocks.java`: `PEGMATITE` (deepslate-like stone, 3/6) and `DEEPSLATE_SPODUMENE_ORE` (`DropExperienceBlock`, like `DEEPSLATE_GALENA_ORE`).
  - `init/ModItems.java`: block items; `RAW_SPODUMENE`, `LITHIUM_CARBONATE`, `LITHIUM`; creative tab entries next to galena.
  - `init/ModFeatures.java`: `PEGMATITE_POD`.
- Create:
  - `world/PegmatitePodFeature.java`: `Feature<NoneFeatureConfiguration>`.
    - It places an ellipsoid of radius 3 to 5 that replaces only `#minecraft:deepslate_ore_replaceables`.
    - About 20% of the placed blocks become spodumene ore.
    - `static int place(WorldGenLevel, BlockPos, RandomSource)` returns the count placed, for the test.
- Data:
  - worldgen `configured_feature/pegmatite_pod.json` and `placed_feature/pegmatite_pod.json`: `rarity_filter` chance 3, `in_square`, uniform height -64 to -8, `biome`.
  - `neoforge/biome_modifier/pegmatite_pod.json`: step `underground_ores`.
  - loot tables: pegmatite drops itself; the ore follows the galena alternatives with `raw_spodumene`.
  - tags: `mineable/pickaxe`; `needs_iron_tool` for the ore; `c:ores/lithium`.
  - recipes: `lithium_carbonate_from_raw_spodumene_smelting` (200 ticks, 0.7 xp) and `_blasting` (100 ticks); `lithium_from_power_cell` (shapeless, 1); `lithium_from_battery_bank` (shapeless, 4).
- Assets: blockstates, models, lang, plus `gpr.frozendawn.ore.spodumene` if the Plumb Line lists ores by tag.
- Test: `src/gameTest/java/com/frozendawn/power/SfrFuelGameTest.java`

- [ ] Write GameTests:
  - `sfrPegmatitePodHostsSpodumeneOnlyInPegmatite`: fill a 13³ volume with deepslate, call `place`, and assert every spodumene block touches pegmatite and none sits outside the pod.
  - `sfrBatterySalvageGivesLithium`: resolve both recipes through the recipe manager and assert counts 1 and 4.
  - `sfrSpodumeneSmeltsToLithiumCarbonate`
- [ ] Run `./gradlew gameTestQuick -PfdTests=sfrPegmatite,sfrBattery,sfrSpodumene`. Expect FAIL.
- [ ] Implement.
- [ ] Run again. Expect PASS.
- [ ] Commit: `feat(sfr): spodumene in pegmatite pods, lithium carbonate and battery salvage`.

### Task 3: Lithium Target Rod in the CFU-7

**Files:**
- Create: `item/LithiumTargetRodItem.java`. Durability `ReactorPhysics.ROD_LIFE_SECONDS`, no repair or enchant, tooltip key `item.frozendawn.lithium_target_rod.bred` (percent).
- Modify:
  - `init/ModItems.java`
  - `recipe/lithium_target_rod.json`: `IPI / PCP / IPI`, P = `frozendawn:lithium`.
  - `block/ReactorControllerBlockEntity.java`:
    - `static boolean target(ItemStack)` is true for an unspent target rod.
    - `burn()` ages target rods by the same `whole`.
    - `ejectSpent()` turns a used-up target rod into `TARGET_YIELD = 4` Tritium Canisters in OUTPUT when it is empty or holds Tritium Canisters with room for 4. Otherwise the rod waits.
  - `block/ReactorControllerMenu.java`: rod slots accept target rods under the same lock rule.
  - CFU-7 screen: shows target rods like loaded rods, if it lists rods.
- Test: `src/gameTest/java/com/frozendawn/power/SfrFuelGameTest.java`, reusing the CFU-7 scene helpers in `ReactorGameTest`.

- [ ] Write GameTests:
  - `sfrTargetRodCostsOneRodOfHeat`: 3 fuel rods plus 1 target rod give `rated == 3 * ROD_KW`.
  - `sfrTargetRodBreedsFourTritium`: a nearly used-up target rod in a running core leaves 4 Tritium Canisters in OUTPUT and an empty slot.
  - `sfrTargetRodWaitsForOutput`: OUTPUT holds a spent fuel rod, and the finished target rod stays in its slot.
  - `sfrTargetRodSurvivesReload`
- [ ] Run the quick loop. Expect FAIL.
- [ ] Implement.
- [ ] Run again. Expect PASS.
- [ ] Commit: `feat(sfr): Lithium Target Rod breeds tritium in a CFU-7 rod slot`.

### Task 4: Isotope Column

**Files:**
- Create:
  - `block/IsotopeColumnBlock.java`: horizontal FACING and LIT, like `InductionFurnaceBlock`.
  - `block/IsotopeColumnBlockEntity.java`: `WorldlyContainer`, `MenuProvider`, `PoweredMachine`.
    - Slots 0 (input) and 1 (output). The top and sides insert into 0; the bottom extracts from 1.
    - `canPlaceItem(0, s)` only when `iceUnits > 0` or the stack is Lithium Carbonate.
    - It pulls ice into `buffer` by `IsotopeColumnLogic.pullIce`.
    - Its job is DEUTERIUM when the buffer reaches `ICE_PER_CANISTER` or ice is waiting, and LITHIUM for Lithium Carbonate.
    - Each paid tick (`PowerNetworks.consumeMachineTick`) advances progress and adds 25% waste heat.
    - It saves `Buffer`, `Progress` and `Job`.
  - `block/IsotopeColumnMenu.java`; `client/IsotopeColumnScreen.java`, which shows the progress bar, the ice buffer and WORKING / NO POWER / IDLE.
- Modify: `ModBlocks`, `ModItems` (creative tab only), `ModBlockEntities`, `ModMenuTypes`, `ClientEvents` (screen), the server ticker hookup used by the Induction Furnace, and lang.
- Test: `SfrFuelGameTest.java`

- [ ] Write GameTests:
  - `sfrColumnMakesDeuteriumFromIce`: powered, 8 Ice gives 1 Deuterium Canister.
  - `sfrColumnPausesWithoutPower`: progress stays and nothing is consumed.
  - `sfrColumnRefinesLithium`
  - `sfrColumnRefusesJunk`: a hopper of cobblestone stays in the hopper.
  - `sfrColumnSurvivesReload`
- [ ] Run the quick loop. Expect FAIL.
- [ ] Implement.
- [ ] Run again. Expect PASS.
- [ ] Commit: `feat(sfr): Isotope Column makes deuterium from ice and refines lithium`.

### Task 5: Helium Canisters

**Files:**
- Modify:
  - `init/ModItems.java`: `HELIUM_CANISTER`, in the creative tab.
  - `block/SfrControllerBlockEntity.java`:
    - `SLOTS = 3`, `HELIUM = 2`.
    - After `step`, when `readout.deuteriumUsed() > 0 && pumping`, grow the Helium stack by that count up to its max. The rest vents.
    - `start()`: when the physics state is OFF and the Helium slot holds a canister, shrink it by one and call `physics.boostCooldown()`.
    - A new FLAGS bit `F_BOOSTED` from `cooldownBoosted()`.
  - `block/SfrControllerMenu.java`: a `HeliumSlot` that accepts only Helium Canisters; `quickMoveStack` routes Helium Canisters to it.
  - `client/SfrControllerScreen.java`: draws the slot with a label and a "BOOSTED" note during cooldown.
- Test: `SfrGameTest.java`

- [ ] Write GameTests:
  - `sfrBurnPumpsHeliumCanisters`: force one deuterium unit consumed while pumping; the Helium slot gains 1.
  - `sfrHeliumBoostsCooldown`: a canister is in the slot, Start consumes it, and READY comes after 60 s instead of 120.
  - `sfrFullHeliumSlotVents`
- [ ] Run the quick loop. Expect FAIL.
- [ ] Implement.
- [ ] Run again. Expect PASS.
- [ ] Commit: `feat(sfr): Helium Canisters from pumped ash boost the next cooldown`.

### Task 6: Art, sound and docs

**Files:**
- `tools/generate_sfr_textures.py` draws:
  - the `helium_canister` item, a brown cap and band;
  - the `lithium_target_rod` item, the fuel-rod silhouette with silver-white lithium pellets;
  - the `raw_spodumene`, `lithium_carbonate` and `lithium` items;
  - the `pegmatite` and `deepslate_spodumene_ore` blocks;
  - the `isotope_column` block faces: front, side, top and a lit front.
- `tools/generate_sfr_sounds.sh`: a `column_loop` render (a soft bubbling hum), plus the `ModSounds`, `sounds.json` and subtitle wiring. The Column plays it while LIT, via the existing looping-sound pattern used by SFR blocks.
- `docs/solstice-reactor-playtest.md`: a fuel chain section and checklist.
- `docs/macs-pawn-checkpoint.md` is not touched (unrelated).

- [ ] Generate the assets. Check that `SfrSoundAssetsTest` lists the new event and that every model and texture resolves (run the client log check in Task 7).
- [ ] Commit: `feat(sfr): fuel chain textures and Isotope Column hum`, then `docs(sfr): fuel chain playtest checklist`.

### Task 7: Verification and handoff

- [ ] `./gradlew test` and `./gradlew architectVerify` in the background. Expect BUILD SUCCESSFUL.
- [ ] Launch `./gradlew runClient --console=plain` and check the log for missing models, textures or sounds.
- [ ] Hand the owner the playtest checklist: textures, Column hum, target rod in a CFU-7, and finding a pod in a fresh world.

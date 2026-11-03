# SMS-1 Hyperion Grid Storage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A buildable, creative-only ORSA SMS-1 "Hyperion" multiblock battery on `feat/hyperion-storage`: superconducting cells, rate-capped ports, a self-powered cryocooler, a warned heat quench, the levitating-puck controller and its audio ladder.

**Architecture:**
- A pure `HyperionPhysics` class (modeled on `power/ReactorPhysics`) owns the cell temperature, cooler draw, warning stages, quench and cool-down. `HyperionControllerBlockEntity` steps it once per second from world inputs and holds the charge as a `long`.
- `HyperionMultiblock` validates the box.
- A new `EnergyStore` interface lets `PowerNetworks` treat a Battery Bank and a Hyperion the same way. Each formed Hyperion Port resolves to its controller's store, and a Hyperion's ports are joined into one network component.
- Visuals and audio read synced controller state; the server decides every cue.

**Tech Stack:** Java 21, NeoForge 1.21.1, JUnit 5 (`src/test`), NeoForge GameTests (`src/gameTest`), hand-written JSON resources, Python/PIL texture generator, ffmpeg + oggenc sound generator.

**Spec:** `docs/hyperion-storage-design.md` (commit `f2805620`). Read it before any task. This plan argues from it.

## Global Constraints

- Every new block and item is creative tab and `/give` only: no recipes, no loot beyond self-drops, no world generation.
- No changes to Battery Bank, Cell Dock or Power Cell behavior, item format or balance. Existing power GameTests must keep passing unchanged.
- A quench is loud and visual only: no damage to players, blocks, machines or room air.
- No emojis in code, comments, docs or commit messages. Conventional Commits (`feat(hyperion):`, `test(hyperion):`). Commit trailer: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Never use a `claude/` or `codex/` branch prefix.
- Match the surrounding style: dense code, terse comments, the record and enum patterns of `ReactorPhysics`, `SfrMultiblock` and `SfrPowerPortBlockEntity`.
- Packages: physics and network code in `com.frozendawn.power`; blocks, block entities and menus in `com.frozendawn.block`; screens, renderers and client sounds in `com.frozendawn.client`; registration in `com.frozendawn.init`.
- Registry ids: `hyperion_frame`, `superconducting_cell`, `hyperion_port`, `cryocooler`, `quench_vent`, `hyperion_controller`.
- Display names: "Hyperion Frame", "Superconducting Cell", "Hyperion Port", "Cryocooler", "Quench Vent", "Hyperion Controller".
- Spec values, used verbatim: cell capacity 20 MJ; 500 kW per port in each direction; quench point -180C; cells held at -200C; sides 3 to 7; exactly 1 Controller, at least 1 Port, at least 1 Cryocooler, exactly 2 Quench Vents; self-discharge 2%/h at 20C, 0.5%/h at -45C, 0.1%/h at -120C, 0 at or below -180C; warning about 15 min below 0C, about 5 min at 20C, 1 to 2 min beside an active heat source.
- Energy is `long` joules on the Hyperion and in every network sum that includes it. Never force a chunk load; never run an unbounded scan in a tick.
- Unit tests: `./gradlew test --tests 'com.frozendawn.power.Hyperion*'`. GameTests are all named `hyperion...` in batch `hyperion`, so `./gradlew gameTestQuick -PfdTests=hyperion` runs them. The gate before handing back is `./gradlew architectVerify`.

## Review Focus

1. **Building a second battery, or any part, against a charged one** must leave the first formed and charged. Once formed, a controller re-validates only its own saved box, never a new flood fill. Pinned in Task 6 (`hyperionNeighborBuildDoesNotQuench`).
2. **An explosion or several breaks in one tick** must quench once, not once per block. Pinned in Task 6 (`hyperionMultiBreakQuenchesOnce`).
3. **Breaking the Controller itself while charged** must still vent and count as a quench; breaking an empty Controller drops it quietly. Pinned in Task 6.
4. **Reloading mid-warning or mid-cool-down** keeps the cell temperature and stage and never quenches from the reload alone. Pinned in Task 1 (NBT round trip) and Task 6 (`hyperionSaveReloadMidWarningResumes`).
5. **Load-follow with a full Hyperion, an empty one and a Battery Bank on one grid** asks only for the empty one's headroom plus the bank's 5 kW margin. Pinned in Task 5.

**Known limitation, not tested headlessly:** a battery straddling a chunk border pauses while any part is unloaded (`UNLOADED`). GameTests cannot unload chunks; the owner replay checks it once by walking away and back.

**Not on this branch:** the Icarus is on `feat/icarus-reactor`, not yet in `feat/maeve-director`. The Icarus-boot replay waits for that merge; this branch's replay boots an SFR-1 instead.

---

### Task 1: HyperionPhysics

**Files:**
- Create: `src/main/java/com/frozendawn/power/HyperionPhysics.java`
- Test: `src/test/java/com/frozendawn/power/HyperionPhysicsTest.java`

**Interfaces:**
- **Produces:**
  - Constants:
    - `CELL_JOULES = 20_000_000L`, `PORT_WATTS = 500_000`
    - `QUENCH_C = -180`, `OPERATING_C = -200`
    - `COOLER_CAPACITY = 15_000` (cell-degrees above `QUENCH_C` one Cryocooler removes)
    - `HOT_NEIGHBOR_LOAD = 60_000`
    - `WARN_COLD_S = 900`, `WARN_ROOM_S = 300`, `WARN_HOT_S = 90`, `FINAL_S = 25`
    - `RECOVER_C_PER_S = 1.0`, `COOLDOWN_C_PER_S = 4.0`, `COOLDOWN_WATTS_PER_CELL = 1000`
  - `enum Stage { COLD, CHIRP, WARNING, FINAL, WARM }`. WARM means the cells are above the quench point.
  - `record Inputs(int cells, int coolers, double surroundingsC, int hotNeighbors, long cooldownJoules)`
  - `record Readout(Stage stage, double cellC, long coolerJoules, double secondsToQuench, boolean quenched, boolean allClear, int secondsInStage)`
  - Static:
    - `long capacity(int cells)`
    - `double selfDischargePercentPerHour(double surroundingsC)`: piecewise linear through (-180, 0), (-120, 0.1), (-45, 0.5), (20, 2.0); 0 at or below -180; above 20 it continues at 1.5 per 65C.
    - `long coolerJoules(long capacity, double surroundingsC)`: `round(capacity * pct / 100 / 3600)`
    - `double heatLoad(int cells, double surroundingsC, int hotNeighbors)`: `cells * max(0, surroundingsC - QUENCH_C) + hotNeighbors * HOT_NEIGHBOR_LOAD`
    - `int warningSeconds(double surroundingsC, int hotNeighbors)`: `WARN_HOT_S` with any hot neighbor; otherwise `WARN_COLD_S` at or below 0C, `WARN_ROOM_S` at or above 20C, linear between.
    - `long cooldownWatts(int cells)`
  - Instance: `HyperionPhysics()` starts at `OPERATING_C`, stage COLD. `void reset(double surroundingsC)` sets `cellC = max(OPERATING_C, surroundingsC)` and the matching stage. `Readout step(Inputs in, long stored)`. `double cellC()`. `void save(CompoundTag)` and `void load(CompoundTag)` keep `cellC`, the stage and `secondsInStage`.

`step` rules, in order:
1. If not WARM and `stored > 0`, the cooler costs `coolerJoules(capacity(cells), surroundingsC)`, reported as `Readout.coolerJoules`. If `stored` is less than that cost, the cooler takes all of `stored` (reported as `coolerJoules`) and is unpaid. A battery whose charge runs out therefore reaches 0 before its cells reach the quench point, so an outage never quenches.
2. Overloaded means `heatLoad > coolers * COOLER_CAPACITY`, or the cooler is unpaid while `heatLoad > 0`.
3. If not WARM: when overloaded, `cellC += (QUENCH_C - OPERATING_C) / warningSeconds`; otherwise `cellC = max(OPERATING_C, cellC - RECOVER_C_PER_S)`.
4. If `cellC >= QUENCH_C` and `stored > 0`: `quenched = true`, `cellC = max(surroundingsC, QUENCH_C + 20)`, stage WARM.
5. If `cellC >= QUENCH_C` and `stored == 0`: stage WARM, no quench.
6. While WARM: if `surroundingsC <= QUENCH_C`, cool by `COOLDOWN_C_PER_S` for free. Otherwise cool by `COOLDOWN_C_PER_S * min(1, cooldownJoules / cooldownWatts(cells))`. WARM ends at `cellC <= OPERATING_C`.
7. If not WARM and overloaded: `secondsToQuench = (QUENCH_C - cellC) / rate`. The stage is FINAL at or below `FINAL_S`, WARNING at or below `warningSeconds / 3`, and CHIRP otherwise. If not overloaded the stage is COLD.
8. `allClear` is true on the step a stage of CHIRP, WARNING or FINAL becomes COLD. `secondsInStage` resets on every stage change.

- [ ] **Step 1: Write the failing tests** in `HyperionPhysicsTest`:
  - `selfDischargeMatchesTheSpec`: `pct(20)==2.0`, `pct(-45)==0.5`, `pct(-120)==0.1`, `pct(-180)==0`, `pct(-273)==0`, `pct(40)` within 1e-9 of `2.0 + 1.5*20/65`. `coolerJoules(capacity(7), 20)==778`.
  - `coolerSizingMatchesTheSpec`: `heatLoad(7,20,0) <= 1*COOLER_CAPACITY`; `heatLoad(275,20,0) > 3*COOLER_CAPACITY` and `<= 4*COOLER_CAPACITY`; `heatLoad(81,0,0) <= COOLER_CAPACITY`; `heatLoad(7,20,1) > 4*COOLER_CAPACITY`.
  - `heatQuenchTakesTheWarningTime`: with 275 cells, 1 cooler and `stored = 1e9`, no step quenches before step N and step N quenches, for (surroundings, hot, N) = (-10, 0, 900), (20, 0, 300), (10, 0, 600), (-10, 1, 90).
  - `stagesFollowTheLadder`: at 20C the first overloaded step is CHIRP; the stage is WARNING once `secondsToQuench <= 100`; FINAL once `<= 25`.
  - `recoveryPlaysAllClearOnce`: overload for 100 steps, then step with 4 coolers. Exactly one Readout has `allClear`, the stage is COLD, and `cellC` falls 1C per step back to -200.
  - `emptyBatteryWarmsWithoutQuench`: 7 cells, 1 cooler, 20C, `stored = 0`. After 300 steps the stage is WARM and no Readout had `quenched`.
  - `unpaidCoolerCountsAsOverload`: 7 cells, 4 coolers, 20C, `stored = 100` (below the 778 J cost): the stage is CHIRP and `coolerJoules == 100`.
  - `drainingToEmptyNeverQuenches`: 7 cells, 1 cooler, 20C, starting at `stored = 5_000`, with the caller subtracting `coolerJoules` each step. Over 600 steps no Readout has `quenched`, and the final stage is WARM.
  - `quenchThenCoolDown`: after a quench at 20C, `cellC == 20`. With `cooldownJoules = cooldownWatts(7)` it cools 4C per step and leaves WARM within 55 steps; with half of that it cools 2C per step; at -250C surroundings it cools 4C per step with `cooldownJoules = 0`.
  - `nbtRoundTripKeepsWarning`: mid-WARNING save, load into a fresh instance; `cellC`, stage and `secondsInStage` are equal, and the next step matches the original's next step.
- [ ] **Step 2:** Run `./gradlew test --tests 'com.frozendawn.power.HyperionPhysicsTest'`. Expected: compile failure.
- [ ] **Step 3:** Implement `HyperionPhysics` per the rules above.
- [ ] **Step 4:** Run the same command. Expected: PASS.
- [ ] **Step 5:** Commit with `feat(hyperion): add Hyperion cell physics`.

### Task 2: Hyperion blocks and the box validator

**Files:**
- Create in `src/main/java/com/frozendawn/block/`:
  - `HyperionPartBlock.java`: base `Block` with `PushReaction.BLOCK`. Its `onPlace` and `onRemove` (when the block actually changes) call `HyperionControllerBlockEntity.partChanged(level, pos)`, a no-op stub until Task 4.
  - `HyperionFrameBlock`, `SuperconductingCellBlock` and `CryocoolerBlock` (plain parts)
  - `QuenchVentBlock` (`BlockStateProperties.FACING`, outward; placement: `getNearestLookingDirection().getOpposite()`)
  - `HyperionPortBlock` and `HyperionControllerBlock` (`FACING` likewise), with stubs for their block entities, completed in Task 4
  - `HyperionMultiblock.java`
- Modify:
  - `init/ModBlocks.java` and `init/ModItems.java`: block items, and creative tab entries after `SFR_POWER_PORT`
- Resources:
  - `assets/frozendawn/lang/en_us.json`: names, plus `hyperion.frozendawn.problem.<problem>` for each `Problem`
  - blockstates, models and flat placeholder textures (final art in Task 8)
  - `data/frozendawn/loot_table/blocks/*.json`: self-drops
- Test: `src/gameTest/java/com/frozendawn/power/HyperionGameTest.java` (batch `hyperion`) and `src/gameTest/java/com/frozendawn/power/HyperionScenes.java`

**Interfaces:**
- **Produces:**
  - `enum Problem { NONE, UNLOADED, TOO_LARGE, TOO_SMALL, CORNER, EDGE, FACE, INTERIOR, CONTROLLERS, PORTS, COOLERS, VENTS }`
  - `record Result(Problem problem, BlockPos wrong, BlockPos min, BlockPos max, int cells, List<BlockPos> ports, List<BlockPos> coolers, List<BlockPos> vents)` with `valid()`
  - `HyperionMultiblock.discover(Level, BlockPos controller)`: flood fill over `HyperionPartBlock`s through the 6 neighbors, then `validate` on the bounding box. Any unloaded position gives `UNLOADED`. More than 343 parts or an extent over 7 gives `TOO_LARGE`.
  - `HyperionMultiblock.validate(Level, BlockPos min, BlockPos max)`, checking cells in x, y, z order:
    - a side under 3 gives `TOO_SMALL`
    - a corner (3 extreme coordinates) must be a Frame, else `CORNER`
    - an edge (2) must be a Frame or a fitting, else `EDGE`
    - a face (1) must be a Cell or a fitting, else `FACE`
    - the interior must be Cells, else `INTERIOR`
    - fittings are Port, Cryocooler, Quench Vent and Controller
    - then the counts: exactly 1 Controller (`CONTROLLERS`), at least 1 Port (`PORTS`), at least 1 Cryocooler (`COOLERS`), exactly 2 Vents each facing away from the box (`VENTS`)
  - `HyperionScenes.build(GameTestHelper h, BlockPos min, int sx, int sy, int sz, int ports, int coolers)` returns `Built(BlockPos controller, List<BlockPos> ports, List<BlockPos> coolers, List<BlockPos> vents, BlockPos min, BlockPos max)` in absolute positions. Fittings go on non-corner edge positions, so every face stays cells.

- [ ] **Step 1: Write the failing GameTests:**
  - `hyperionShellValidatesSizesAndCountsCells`: valid with `cells` 7 (3x3x3), 81 (5x5x5), 275 (7x7x7) and 61 (3x5x7).
  - `hyperionShellNamesTheWrongCell`. Each case gives its problem, with `wrong` at the changed cell where one applies:
    - stone in a face: `FACE`
    - air in the interior: `INTERIOR`
    - a Cell at a corner: `CORNER`
    - one vent removed: `VENTS`
    - a vent turned inward: `VENTS`
    - no Cryocooler: `COOLERS`
    - a second Controller on an edge: `CONTROLLERS`
    - an 8-long box: `TOO_LARGE`
    - a 2-wide box: `TOO_SMALL`
  - `hyperionPartsRefusePistons`: every part's default state has `PushReaction.BLOCK`.
- [ ] **Step 2:** Run `./gradlew gameTestQuick -PfdTests=hyperion`. Expected: compile failure, or FAIL.
- [ ] **Step 3:** Implement the blocks, registrations, resources and `HyperionMultiblock`.
- [ ] **Step 4:** Run the same command. Expected: PASS.
- [ ] **Step 5:** Commit with `feat(hyperion): add Hyperion blocks and box validator`.

### Task 3: EnergyStore in the power network

**Files:**
- Create: `src/main/java/com/frozendawn/power/EnergyStore.java`
- Modify:
  - `block/PowerStorageBlockEntity.java`: implements `EnergyStore`, delegating to its existing `int` methods, which keep their signatures
  - `power/PowerNetworks.java`: every `PowerStorageBlockEntity` cast and `storedJoules` use (`consumeAirlockTick`, `consumeHeaterEnergy`, `consumeMachineTick`, `account`, `priorityBudget`, `storedJoules`, the `stores`/`storeEnergyTicks` pruning) goes through `EnergyStore`

**Interfaces:**
- **Produces:**
  - `interface EnergyStore`:
    - `BlockPos storeKey()`
    - `long storedEnergy()` and `long energyCapacity()`
    - `long availableEnergy()`: what may still be drawn this interval
    - `long chargeEnergy(long offered)` and `long drawEnergy(long requested)`
    - `boolean acceptsCharge()`: `isBank()` for a `PowerStorageBlockEntity`
    - `void publish(int charged, int drawn)`, `void publishMachineDraw(int joules)` and `void publishHeaterDraw(int joules)`, which already exist on the bank
  - `PowerNetworks.store(ServerLevel, BlockPos)` returns `Optional<EnergyStore>`. In this task, only a `PowerStorageBlockEntity`.
  - `PowerNetworks.stores(ServerLevel, Collection<BlockPos>)` returns `List<EnergyStore>`: loaded positions in sorted order, distinct by `storeKey`.
  - The interval budget uses `availableEnergy()`. Monitor `storedJoules` and `capacityJoules` use `storedEnergy()` and `energyCapacity()`. Duplicate-accounting guards key on `storeKey()`.

- [ ] **Step 1:** This is a refactor with no behavior change, so add no new tests. Run `./gradlew gameTestQuick -PfdTests=power,reactor,sfr,induction,acheron,workLamp,pixie,airlock,plumbLine,salvage,uplink` before the change. Expected: PASS, and record the count.
- [ ] **Step 2:** Implement the interface, the bank delegation and the `PowerNetworks` changes.
- [ ] **Step 3:** Run the same command. Expected: PASS with the same count. Then run `./gradlew architectVerify`, because every network consumer depends on these paths. Expected: PASS.
- [ ] **Step 4:** Commit with `refactor(power): route storage through EnergyStore`.

### Task 4: Controller, ports and the network

**Files:**
- Create in `block/`: `HyperionControllerBlockEntity.java` and `HyperionPortBlockEntity.java`
- Modify:
  - `init/ModBlockEntities.java`: `hyperion_controller` and `hyperion_port`
  - `power/PowerLinkRules.java`: `HyperionPortBlock` range 16
  - `power/PowerNetworks.java`:
    - `store()` resolves a `HyperionPortBlockEntity` to its formed controller
    - `account` prunes `stores` to banks or ports
    - add `hyperionFormed` and `hyperionUnformed`
    - in `update`, when rebuilding components, chain each formed Hyperion's ports together as extra edges
- Test: `HyperionGameTest.java`

**Interfaces:**
- **Consumes:** `HyperionPhysics` (Task 1), `HyperionMultiblock` (Task 2), `EnergyStore` and `PowerNetworks.store` (Task 3).
- **Produces:**
  - `HyperionControllerBlockEntity implements EnergyStore`:
    - `void second(ServerLevel l)` (public, for GameTests)
      - Unformed: `discover` each second while dirty or every 10 s.
      - Formed: `validate(min, max)` of the saved box only.
      - `UNLOADED`: return without stepping.
    - The block's server ticker calls `second` when `gameTime % 20 == 0`.
    - `HyperionMultiblock.Result shell()`
    - `long stored()` and `void setStoredForTest(long)`
    - `int portCount()` and `int rateWatts()` (`portCount * PORT_WATTS`)
    - `long chargeHeadroomWatts()`: `min(rateWatts, capacity - stored)` while charge is accepted, else 0. Task 6 adds cool-down.
    - Per-interval budgets keyed to `gameTime / 20`:
      - `availableEnergy()` is `min(stored, rateWatts - drawnThisInterval)`
      - `drawEnergy` and `chargeEnergy` are capped by the rate, capacity, stored energy and `acceptsCharge()`
      - every return is 0 while the shell is invalid
    - Saves `stored` as a `long` under `"Stored"`, the box bounds under `"Min"`/`"Max"`, and the physics.
    - `void setSurroundingsForTest(Double)`: an override field that Task 6 starts reading. `HyperionScenes.build` sets it to -250 on every Controller it builds, so Task 4 and 5 tests stay cold after Task 6 lands. Tests that need warmth set their own value.
    - `static void partChanged(Level, BlockPos)` marks dirty every controller whose saved box, inflated by 1, contains the position. It uses a per-level index of controller to box.
  - `HyperionPortBlockEntity`: `Optional<HyperionControllerBlockEntity> controller()`. It is set by the controller on formation and checked on every call (loaded, still that controller's port). It calls `PowerNetworks.storageLoaded`/`storageRemoved` from `onLoad`/`setRemoved`.
  - `PowerNetworks.hyperionFormed(ServerLevel, BlockPos controller, List<BlockPos> ports)` and `hyperionUnformed(ServerLevel, BlockPos controller)` both set `graphDirty`.

- [ ] **Step 1: Write the failing GameTests:**
  - `hyperionPortsCapTheRatePerInterval`: a formed 3x3x3 with 1 port and `setStoredForTest(10_000_000)`. In one interval, `drawEnergy(1_000_000)==500_000` and the next `drawEnergy(1)==0`. In the next interval, `drawEnergy` gives 500_000 again. With 2 ports it gives 1_000_000. Charging is symmetric and stops at capacity (140 MJ).
  - `hyperionPowersALampAndMergesNetworks`: port A links to lamp A, and port B links to lamp B. After `account`, both lamps are lit, `stored` fell by 200 J, and `PowerNetworks.component(l, lampA)` contains `lampB`.
  - `hyperionLongEnergySurvivesSaveAndMonitor`: a 7x7x7 with `setStoredForTest(5_000_000_000L)`. After `saveWithFullMetadata` and loading into a fresh block entity, `stored()==5_000_000_000L`. A linked Power Monitor reads `storedJoules==5_000_000_000L` and `capacityJoules==5_500_000_000L`.
  - `hyperionInvalidShellStopsService`: with `stored = 0`, break one cell. `availableEnergy()==0`, `acceptsCharge()==false`, and the port no longer resolves in `PowerNetworks.store`.
- [ ] **Step 2:** Run `./gradlew gameTestQuick -PfdTests=hyperion`. Expected: the new tests FAIL.
- [ ] **Step 3:** Implement the block entities and the network hooks.
- [ ] **Step 4:** Run `./gradlew gameTestQuick -PfdTests=hyperion,power`. Expected: PASS.
- [ ] **Step 5:** Commit with `feat(hyperion): Hyperion controller, ports and network storage`.

### Task 5: Reactors push to fill it

**Files:**
- Modify: `power/PowerNetworks.java`, in `loadFollowTargetWatts`
- Test: `HyperionGameTest.java`

**Interfaces:**
- **Produces:** `PowerNetworks.hyperionHeadroomWatts(ServerLevel l, BlockPos p)` returns `long`: the sum of `chargeHeadroomWatts()` over the distinct Hyperion controllers in `component(l, p)`. `loadFollowTargetWatts` adds it to the existing charge term. The `CHARGE_MARGIN_WATTS` term is unchanged.

- [ ] **Step 1: Write the failing GameTest** `hyperionLoadFollowAsksForPortHeadroom`. Build a lamp (100 W), a cold, empty 3x3x3 Hyperion with 1 port, a cold, full 3x3x3 Hyperion with 2 ports, and a Battery Bank with one empty cell, all linked. After `account`, `loadFollowTargetWatts(l, lamp, 0) == 100 + 5_000 + 500_000`. Filling the empty Hyperion and the bank gives exactly 100.
- [ ] **Step 2:** Run `./gradlew gameTestQuick -PfdTests=hyperionLoad`. Expected: FAIL.
- [ ] **Step 3:** Implement `hyperionHeadroomWatts` and the load-follow change.
- [ ] **Step 4:** Run `./gradlew gameTestQuick -PfdTests=hyperion,reactor,sfr`. Expected: PASS.
- [ ] **Step 5:** Commit with `feat(hyperion): load-following reactors charge the Hyperion`.

### Task 6: Cold, self-discharge and quench in the world

**Files:**
- Modify: `block/HyperionControllerBlockEntity.java`, `block/HyperionPartBlock.java`, `block/HyperionControllerBlock.java`
- Test: `HyperionGameTest.java`

**Interfaces:**
- **Consumes:** Tasks 1, 2 and 4.
- **Produces** on `HyperionControllerBlockEntity`:
  - `double surroundingsC(ServerLevel)`: the warmest of 6 samples, one block outside each face center, using `TemperatureManager.getLoadedTemperatureAt(l, pos, day, total)` with `ApocalypseState` day and total. The Task 4 test override takes precedence when set; null clears it.
  - `int hotNeighbors(ServerLevel)` counts the positions one block outside each face (not edges or corners) that hold:
    - a `ReactorRadiatorBlock` with `HOT`
    - a block with `TemperatureManager.getAmbientSignatureHeat(state) > 0`
    - a `ReactorControllerBlockEntity` with `powerWatts() > 0`
    - It is rescanned every 5 s and cached.
  - Each formed `second`:
    - steps the physics with `Inputs(cells, coolers.size(), surroundingsC, hotNeighbors, cooldownGranted)`
    - subtracts `min(stored, coolerJoules)`
    - on `quenched`, sets `stored = 0` and calls `quench()`
  - `void quench()`: records `lastQuenchTick`, increments the level counter, and starts the vent effects (Tasks 8 and 9).
  - `static int quenchCount(ServerLevel)`: a level-wide counter for tests and the lab.
  - `HyperionPhysics.Readout readout()`
  - While WARM: `acceptsCharge()` is true, `chargeEnergy` routes up to `cooldownWatts(cells)` per interval into `cooldownGranted` instead of `stored`, and `chargeHeadroomWatts()` returns `cooldownWatts(cells)`.
  - First formation calls `physics.reset(surroundingsC)`, so a fresh warm build starts WARM.
  - `partChanged` on a position inside a formed, charged controller's saved box quenches that controller once, then unforms it. The Controller's own removal while charged quenches at its position before it is gone. An empty structure comes apart with no quench.

- [ ] **Step 1: Write the failing GameTests.** Most drive `second(l)` in a loop with `setSurroundingsForTest`:
  - `hyperionHeatQuenchFollowsTheLadder`: 5x5x5, 1 cooler, 20C, `stored = 1e9`, cold.
    - After 299 seconds, `stored > 0` and the stage is FINAL.
    - At 300, `stored == 0` and `quenchCount` rose by 1.
  - `hyperionEnoughCoolingHoldsAndDrainsSlowly`: 3x3x3, 1 cooler, 20C, full. After 3600 seconds, the loss is within 1% of 2.8 MJ and there was no quench.
  - `hyperionOutageNeverQuenches`: 3x3x3, 1 cooler, 20C, unlinked, `stored = 1_000_000`.
    - Run seconds until `stored == 0`, then 400 more.
    - `quenchCount` is unchanged and the stage is WARM.
  - `hyperionWarmBuildCoolsOnGridBeforeCharging`: set the override to 20 before the first `second`, so the fresh build forms WARM.
    - `chargeEnergy(1_000_000)` returns at most `cooldownWatts(7)` and `stored` stays 0.
    - After 55 seconds of full grants, the stage is COLD and `chargeEnergy` stores.
  - `hyperionBreakingAChargedPartQuenches`: destroying a cell with `stored > 0` gives `stored == 0` and `quenchCount + 1`.
  - `hyperionEmptyBatteryComesApartQuietly`: with `stored == 0`, destroying a cell leaves `quenchCount` unchanged and drops a `superconducting_cell` item.
  - `hyperionControllerRemovalQuenchesOnlyWhenCharged`: a charged Controller destroyed gives `quenchCount + 1`. An empty one drops itself with no quench.
  - `hyperionMultiBreakQuenchesOnce`: three parts destroyed in the same tick give `quenchCount + 1`.
  - `hyperionNeighborBuildDoesNotQuench`: a charged 3x3x3. A second complete 3x3x3 built touching its face, plus a stray Frame against another face, leave the first formed, charged and unquenched.
  - `hyperionHotNeighborCounts`: a `ReactorRadiatorBlock` with `HOT=true` against a face gives `hotNeighbors == 1`; with `HOT=false` it gives 0.
  - `hyperionSaveReloadMidWarningResumes`: at WARNING, save and load into a fresh block entity at the same position. Stage and `cellC` are equal, and the next `second` does not quench.
- [ ] **Step 2:** Run `./gradlew gameTestQuick -PfdTests=hyperion`. Expected: the new tests FAIL.
- [ ] **Step 3:** Implement the sampling, the cooler draw, the quench and cool-down paths, and the removal hooks.
- [ ] **Step 4:** Run the same command. Expected: PASS.
- [ ] **Step 5:** Commit with `feat(hyperion): cryocooler self-discharge and quench`.

### Task 7: Controller screen

**Files:**
- Create: `block/HyperionControllerMenu.java` and `client/HyperionControllerScreen.java`
- Modify:
  - `init/ModMenuTypes.java`, plus client screen registration in `client/ClientEvents.java`
  - `block/HyperionControllerBlock.java`: right-click opens the menu
  - `block/HyperionControllerBlockEntity.java`: `getUpdateTag`/`getUpdatePacket` carry `stored`, `capacity`, the last interval's flow in and out, `rateWatts`, `cellC`, `surroundingsC`, `coolerJoules`, `stage`, `secondsToQuench`, the problem and `lastQuenchTick`. `sendBlockUpdated` runs when any of them changes, at most once per second.
  - `en_us.json`: screen labels; stage names `hyperion.frozendawn.stage.<stage>`
- Test: `src/test/java/com/frozendawn/power/HyperionLangTest.java`; `HyperionGameTest.java`

**Interfaces:**
- **Produces:** `String problemKey()` on the controller: `"hyperion.frozendawn.problem." + problem.name().toLowerCase(Locale.ROOT)`. The screen reads the client copy of the block entity; the menu carries only position and validity. Layout is the builder's call; the spec lists the contents.

- [ ] **Step 1: Write the failing tests:**
  - `HyperionLangTest.everyProblemAndStageHasText`: `en_us.json` has a non-empty value for every `Problem` and `Stage` key.
  - GameTest `hyperionControllerExplainsAnIncompleteShell`: with one vent removed, `problemKey()` is `hyperion.frozendawn.problem.vents`.
- [ ] **Step 2:** Run `./gradlew test --tests 'com.frozendawn.power.HyperionLangTest'` and `./gradlew gameTestQuick -PfdTests=hyperionController`. Expected: FAIL.
- [ ] **Step 3:** Implement the menu, the screen, the sync and the text.
- [ ] **Step 4:** Run both commands. Expected: PASS.
- [ ] **Step 5:** Commit with `feat(hyperion): Hyperion controller screen`.

### Task 8: The look: cells, puck, lights and vapour

**Files:**
- Create:
  - `tools/generate_hyperion_textures.py` (PIL, seeded, run with `python3 -I`)
  - `client/HyperionControllerRenderer.java`
  - `client/ColdVapourParticle.java`
- Modify:
  - `block/SuperconductingCellBlock.java`: `EnumProperty<Look> LOOK` with `FROSTED, PLAIN, STRESSED, CRITICAL, SCARRED`. Light level 3 for STRESSED and 7 for CRITICAL. `animateTick` emits vapour and wisps.
  - `block/HyperionControllerBlock.java` and `block/HyperionPortBlock.java`: `BooleanProperty ALERT`, light 6 while true
  - `block/HyperionControllerBlockEntity.java`: sets each cell's LOOK and the ALERT states on stage change only (flag `UPDATE_CLIENTS`); runs the vent steam for 160 ticks after `quench()`
  - `init/ModParticles.java`: `cold_vapour`; `client/ClientEvents.java`: the renderer and the particle provider
  - blockstates, models, textures and `.mcmeta` animations
- Test: `HyperionGameTest.java`

**Interfaces:**
- **Consumes:** synced state from Task 7.
- **Produces:**
  - LOOK by state:

    | State | LOOK |
    | --- | --- |
    | COLD, surroundings below 0C | FROSTED |
    | COLD, surroundings 0C or warmer | PLAIN |
    | CHIRP or WARNING | STRESSED |
    | FINAL | CRITICAL |
    | WARM after a quench, until COLD again | SCARRED |
    | WARM without a quench | PLAIN |

  - ALERT is true during CHIRP, WARNING and FINAL.
  - Look per the spec and `docs/art/hyperion-storage/cell-look-sketch.html`:
    - cells are matte charcoal ceramic with fine scratches; FROSTED adds frost along the upper edges
    - STRESSED has dull orange pulsing cracks (animated)
    - CRITICAL has white-hot spreading cracks with a fast flicker (animated)
    - SCARRED has dark crack scars
    - no cell texture shows charge
  - Particles:
    - FROSTED cells spawn `cold_vapour` from side faces; it sinks slowly, grows and fades over 60 to 120 ticks
    - STRESSED and CRITICAL cells spawn rising `ParticleTypes.WHITE_SMOKE`, more for CRITICAL
    - during the 160 vent ticks after a quench, the server sends `ParticleTypes.CLOUD` jets along each vent's FACING
  - Renderer, behind the Controller's front-face window:
    - three magnets and a charcoal puck
    - puck height runs from 1/16 to 7/16 of a block with `stored / capacity`
    - idle bob of 0.2 px
    - wobble of 0.5 px in CHIRP and WARNING; a 1.5 px shudder with tilt in FINAL
    - on a new `lastQuenchTick`, the puck falls onto the magnets over 4 ticks and stays down while `stored == 0`

- [ ] **Step 1: Write the failing GameTest** `hyperionCellsShowTheStage`. Force stages through `setSurroundingsForTest` and the Task 6 loop. Every cell's LOOK follows the table, and the Controller and ports have `ALERT` during the warning.
- [ ] **Step 2:** Run `./gradlew gameTestQuick -PfdTests=hyperionCells`. Expected: FAIL.
- [ ] **Step 3:** Implement the properties, the generator and its outputs, the renderer, the particle and the vent jets.
- [ ] **Step 4:** Run `./gradlew gameTestQuick -PfdTests=hyperion`. Expected: PASS. How it looks is for the owner's pass in Task 10.
- [ ] **Step 5:** Commit with `feat(hyperion): superconductor look, levitating puck and vapour`.

### Task 9: The audio ladder

**Files:**
- Create:
  - `tools/generate_hyperion_sounds.sh` (ffmpeg lavfi, fixed seeds, mono 44.1 kHz, `oggenc -q 5`, seamless loops: the conventions of `tools/generate_sfr_sounds.sh`)
  - `tools/audio_sources/hyperion/SOURCES.md`
  - `power/HyperionAudioCues.java` and `client/HyperionLoopSound.java`
- Modify:
  - `init/ModSounds.java` and `assets/frozendawn/sounds.json`
  - `block/HyperionControllerBlockEntity.java`: plays one-shots from the cues
  - `docs/audio/SHIPPED_AUDIO_INVENTORY.tsv`, via `python3 -I tools/build_audio_inventory.py`
- Test: `src/test/java/com/frozendawn/power/HyperionAudioCuesTest.java` and `src/test/java/com/frozendawn/power/HyperionSoundAssetsTest.java`

**Interfaces:**
- **Produces:**
  - Sound events, each its own file under `sounds/blocks/hyperion/`:
    - `blocks.hyperion_hum` (loop)
    - `blocks.hyperion_chirp`
    - `blocks.hyperion_warning` (loop)
    - `blocks.hyperion_whine` (25 s)
    - `blocks.hyperion_quench`
    - `blocks.hyperion_vent` (8.5 s steam hiss)
    - `blocks.hyperion_aftermath` (10 s)
    - `blocks.hyperion_all_clear`
    - `blocks.hyperion_puck_drop`
  - `HyperionAudioCues.Cue { CHIRP, WHINE, QUENCH, ALL_CLEAR }`
  - `EnumSet<Cue> cues(@Nullable HyperionPhysics.Readout before, HyperionPhysics.Readout after, boolean broke)`:
    - nothing when `before` is null
    - CHIRP each second where stage is CHIRP and `secondsInStage % 10 == 0`
    - WHINE on entering FINAL
    - QUENCH when `after.quenched()` or `broke`
    - ALL_CLEAR when `after.allClear()`
  - On QUENCH, the controller plays `quench` at its position, `vent` at each vent position and `puck_drop` at the Controller. It plays `aftermath` 160 ticks later.
  - Client `HyperionLoopSound`:
    - hum while formed and stored or cooling, at volume `0.6 * clamp((surroundingsC + 270) / 290, 0.02, 1)`
    - the warning loop during WARNING, its pitch rising from 1.0 to 1.6 as `secondsToQuench` falls from `warningSeconds / 3` to `FINAL_S`
    - same lifecycle as `SfrLoopSound`
  - Sound character per spec and `docs/art/hyperion-storage/audio-ladder-sketch.html`:
    - hum: 50 and 100 Hz with low-passed brown noise and a 0.8 Hz pulse
    - chirp: 2.2 to 3.0 kHz, 80 ms
    - warning: 880/620 Hz lowpassed square
    - whine: saw 220 to 1800 Hz, band-passed, tremolo 4 to 14 Hz
    - quench: 90 to 28 Hz thump and a noise burst
    - vent: band-passed noise roar decaying over 8.5 s
    - aftermath: random ticks, inharmonic metal pings (1 : 2.76 : 5.4) and a fading high hiss, with no pitch sweeps or voice-like sounds
    - all clear: 1320, 990 and 660 Hz
    - puck drop: a short low clunk

- [ ] **Step 1: Write the failing tests:**
  - `HyperionAudioCuesTest`:
    - `chirpsEveryTenSeconds`: a CHIRP stage cue at `secondsInStage` 0, 10 and 20, and none at 5
    - `whineOnceOnFinal`
    - `quenchOnQuenchOrBreak`
    - `allClearOnRecovery`
    - `nothingAfterLoad`
  - `HyperionSoundAssetsTest.everyHyperionSoundExistsAndIsMono`, modeled on `SfrSoundAssetsTest`, over exactly the nine events.
- [ ] **Step 2:** Run `./gradlew test --tests 'com.frozendawn.power.HyperionAudio*' --tests 'com.frozendawn.power.HyperionSound*'`. Expected: FAIL.
- [ ] **Step 3:** Write and run the generator, then add the events, cues, loop sound, SOURCES ledger and inventory rows.
- [ ] **Step 4:** Run the same command. Expected: PASS.
- [ ] **Step 5:** Commit with `feat(hyperion): Hyperion audio ladder`.

### Task 10: Playtest world, gate and handoff

**Files:**
- Create: `world/HyperionLabPlacement.java` and `tools/prepare_hyperion_playtest.py` (modeled on `SfrLabPlacement` and `tools/prepare_sfr_playtest.py`)
- Modify:
  - `command/FrozenDawnWorldCommand.java`: a `hyperionlab` literal next to `sfrlab`
  - `docs/power-network-checkpoint.md`: a dated section
- Test: `HyperionGameTest.java`

**Interfaces:**
- **Produces:** `HyperionLabPlacement.place(ServerLevel, BlockPos origin)` builds:
  - a 5x5x5 Hyperion, outdoors, with 2 ports and 2 Cryocoolers, half charged
  - an off SFR-1 via `SfrLabPlacement`, its Power Port linked to a Hyperion Port and with no Battery Banks, so the Hyperion pays the ignition pulse
  - a second 3x3x3 Hyperion with 1 Cryocooler inside a sealed, heated room, for the warm comparison
- The world script follows `AGENTS.md`: a world-local advancement filter after NeoForge's `mod_data`, difficulty preselected and locked, phase 0, and the clickable chat menu pattern of the SFR world (adding buttons for day/phase steps and for placing lava against the warm battery).

- [ ] **Step 1: Write the failing GameTest** `hyperionLabPlacesAValidPairAndSfr`: after `place`, both Hyperions are formed, the large one is half charged, and the SFR-1 Power Port and a Hyperion Port share a component.
- [ ] **Step 2:** Run `./gradlew gameTestQuick -PfdTests=hyperionLab`. Expected: FAIL.
- [ ] **Step 3:** Implement the placement, the command and the world script. Generate "Hyperion Check" with the script without touching existing saves.
- [ ] **Step 4:** Run `./gradlew architectVerify`. Expected: PASS, with every `hyperion` test listed once in the report.
- [ ] **Step 5:** Update the checkpoint with the branch, commit, gate result and world name. Commit with `feat(hyperion): Hyperion lab world and checkpoint`.
- [ ] **Step 6:** Before offering a PR, dispatch one Sonnet subagent to review the whole branch (`origin/feat/maeve-director..feat/hyperion-storage`), briefed with this plan, the spec and the rulings above. Fix real findings test-first; list minor ones as deferred.
- [ ] **Step 7:** Launch `./gradlew runClient --console=plain` (Java 21) from this worktree on "Hyperion Check" for the owner's visual and audio pass. Replay:
  - the SFR-1 ignites from the Hyperion
  - the outdoor and heated-room batteries look and sound different
  - lava starts a heat warning that runs to a quench
  - breaking a charged cell vents at once
  - an empty battery comes apart quietly
  - walking far enough to unload part of a battery and coming back does not quench it

  Record the owner's own words before any diagnostic explanation.

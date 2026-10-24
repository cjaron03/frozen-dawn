# SFR-1 Solstice Reactor Core Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A buildable, startable, burnable and disruptable ORSA SFR-1 fusion reactor, creative-only, on `feat/solstice-reactor`.

**Architecture:**
- A pure `FusionPhysics` class (modeled on `power/ReactorPhysics`) owns the state machine, thermal loop, fuel and disruptions. `SfrControllerBlockEntity` steps it once per second from world inputs.
- `SfrMultiblock` validates the 9x9x5 torus.
- The Power Port is the reactor's electrical node: it supplies net output and pays for draws as a `PoweredMachine`.
- The controller holds channel pressure itself and hands over to room gas only in the Phase 6 vacuum stage.

**Tech Stack:** Java 21, NeoForge 1.21.1, JUnit 5 (`src/test`), NeoForge GameTests (`src/gameTest`), hand-written JSON resources.

**Spec:** `docs/solstice-reactor-design.md` (commit `f80a7c4d`). Read it before any task. This plan argues from it.

## Global Constraints

- Every new block and item is creative tab and `/give` only: no recipes, no loot tables beyond self-drops, no world generation.
- No changes to CFU-7 behavior or balance, except that High-Temperature Radiators are not CFU-7 sinks.
- No meltdown, explosion, fallout or contamination from fusion.
- No emojis in code, comments, docs or commit messages. Conventional Commits (`feat(sfr):`, `test(sfr):`). Commit trailer: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Match the surrounding code style: dense, terse comments, the record and enum patterns from `ReactorPhysics` and `ReactorControllerBlockEntity`.
- Package layout: physics in `com.frozendawn.power`; blocks, block entities and menus in `com.frozendawn.block`; screens in `com.frozendawn.client`; registration in `com.frozendawn.init`.
- Registry ids:
  - `sfr_controller`, `sfr_fuel_port`, `sfr_power_port`, `sfr_vacuum_port`, `sfr_maintenance_hatch`
  - `magnet_coil`, `first_wall`, `lithium_blanket`, `confinement_core`, `high_temperature_radiator`
  - `deuterium_canister`, `tritium_canister`
  - The existing `coolant_port` is reused.
- Display names: "ORSA SFR-1 Solstice Reactor" (controller), "Confinement Core", "Magnet Coil", "First Wall", "Lithium Blanket", "High-Temperature Radiator", "Deuterium Canister", "Tritium Canister", "SFR-1 Fuel Port", "SFR-1 Power Port", "SFR-1 Vacuum Port", "SFR-1 Maintenance Hatch".
- Status strings, exact: `IGNITION`, `BURNING`, `DISRUPTION`, `MAGNET QUENCH`, `TORUS OPEN`, `ASH POISONED`.
- Every SFR-1 sound is new and its own: no CFU-7, airlock or vanilla sound is reused. The one exception is the Maintenance Hatch, which keeps the airlock door's sounds because it is an airlock door.
- Textures are placeholders recolored from the existing reactor textures. Final art waits for the owner's visual pass.
- Unit tests: `./gradlew test --tests 'com.frozendawn.power.FusionPhysicsTest'`. GameTests are all named `sfr...`, so `./gradlew gameTestQuick -PfdTests=sfr` runs them. The gate before handing back is `./gradlew architectVerify`.

## Review Focus

1. **Reloading a world mid-burn** must keep it BURNING with the same loop temperature, tritium and channel pressure, and must not disrupt or add wear. Pinned in Task 1 (NBT round trip) and Task 5 (controller tag round trip).
2. **A hopper feeding the Fuel Port anything but Deuterium Canisters** is refused, not swallowed. Pinned in Task 5.
3. **Mining a worn First Wall drops nothing; a fresh one drops itself.** Pinned in Task 3.
4. **A bank-only network with too little stored energy for the full 10 s pulse** aborts partway, returns to READY, spends no tritium and adds no wear. Pinned in Task 5.
5. **Load-following at zero network demand** stays lit with net output at or near zero instead of quenching. Pinned in Task 2.

**Known limitation, not tested:** outside the vacuum stage, a player standing in a pumped-down channel still breathes, because room breathing is phase-gated. This is the owner's decided trade-off for working in any phase.

---

### Task 1: FusionPhysics startup state machine

**Files:**
- Create: `src/main/java/com/frozendawn/power/FusionPhysics.java`
- Test: `src/test/java/com/frozendawn/power/FusionPhysicsTest.java`

**Interfaces:**
- **Produces:**
  - `FusionPhysics` with constants:
    - `RATED_KW=400`, `NOMINAL_C=900`, `DISRUPT_C=1100`
    - `HT_RADIATOR_KW=30`, `HT_RADIATOR_KW_PER_C=HT_RADIATOR_KW/(NOMINAL_C-ReactorPhysics.DEEP_VACUUM_C)`
    - `HOUSE_KW=40`, `ASH_PUMP_KW=5`, `COOLDOWN_KW=20`, `IGNITION_KW=150`, `PUMP_KW=10`
    - `COOLDOWN_SECONDS=120`, `IGNITION_SECONDS=10`, `ASH_FADE_SECONDS=60`
    - `VACUUM_LIMIT=0.05`, `WEAR_PER_DISRUPTION=0.05`, `MAX_IGNITION_WEAR=0.80`
    - `DEUTERIUM_SECONDS=600`, `TRITIUM_SECONDS=1200`, `TRITIUM_TANK=8`, `IGNITION_TRITIUM=1`, `BREEDING_RATIO=1.05`
    - `LOOP_HEAT_CAPACITY_KJ_PER_C=25`, `MIN_BURN=0.2`, `LOAD_FOLLOW_SLEW=0.05` (tuning; raised from 100 and 0.02 on 2026-10-24 so the loop settles in minutes)
  - `enum State { OFF, COOLDOWN, READY, IGNITION, BURNING }`
  - `enum Status { IDLE, COOLDOWN, READY, TORUS_OPEN, NO_VACUUM, NO_FUEL, WALL_WORN, IGNITION, ABORTED, BURNING, DISRUPTION, MAGNET_QUENCH, ASH_POISONED }`
  - `record Inputs(List<Double> radiatorSinks, boolean powered, double channelPressure, boolean hatchClosed, boolean shellIntact, boolean roomVacuum, int deuterium, boolean pumping, double wear, boolean redstone, double targetKw)`
    - `powered` means the previous second's `requestKw` was paid in full.
    - `targetKw` is NaN in manual mode, as in `ReactorPhysics`.
  - `record Readout(State state, Status status, double loopC, double heatKw, double removedKw, double grossKw, double netKw, double requestKw, int deuteriumUsed, double tritium, double progress, boolean disrupted)`
  - Methods:
    - `Readout step(Inputs)`
    - `void start()`, `void stop()`
    - `boolean loadFollow()`, `void setLoadFollow(boolean)`
    - `double tritium()`, `double addTritium(double units)` (returns the amount accepted, capped at `TRITIUM_TANK`), `boolean extractTritium()` (removes 1 unit if at least 1 is held)
    - `double loopC()`, `void setLoopC(double)`, `State state()`
    - `void setReadyForTest()`
    - `void save(CompoundTag)`, `void load(CompoundTag)`

- [ ] **Step 1: Write the failing tests.** Helpers mirror `ReactorPhysicsTest`. `sinks(n)` is `n` copies of `ReactorPhysics.DEEP_VACUUM_C`. `ok(n)` is `new Inputs(sinks(n), true, 0, true, true, false, 64, true, 0, false, Double.NaN)`.

```java
@Test void cooldownTakesTwoMinutesOnTwentyKw() {
    var f = new FusionPhysics(); f.start();
    var r = run(f, ok(14), 119);
    assertEquals(FusionPhysics.State.COOLDOWN, r.state()); assertEquals(20, r.requestKw(), 1e-9);
    assertEquals(FusionPhysics.State.READY, f.step(ok(14)).state());
}
@Test void cooldownHalvesInAVacuumRoom() { /* roomVacuum=true: READY after 60 steps, COOLDOWN at 59 */ }
@Test void unpaidCooldownOrReadyFallsBackToOff() { /* powered=false once during COOLDOWN -> OFF, disrupted false; same from READY */ }
@Test void ignitionChecksRefuseWithTheirStatus() {
    // From setReadyForTest() + addTritium(4), start() with one failing input each, expect state READY and:
    // hatchClosed=false -> TORUS_OPEN; channelPressure=0.06 -> NO_VACUUM; deuterium=0 -> NO_FUEL;
    // tritium 0 (no addTritium) -> NO_FUEL; wear=0.81 -> WALL_WORN. channelPressure=0.05 is accepted.
}
@Test void ignitionAt150KwBurnsAfterTenSeconds() {
    var f = new FusionPhysics(); f.setReadyForTest(); f.addTritium(4); f.start();
    var r = run(f, ok(14), 9);
    assertEquals(FusionPhysics.State.IGNITION, r.state()); assertEquals(150, r.requestKw(), 1e-9);
    r = f.step(ok(14));
    assertEquals(FusionPhysics.State.BURNING, r.state()); assertEquals(3, f.tritium(), 1e-9);
}
@Test void unpaidIgnitionAbortsWithoutDamageOrTritium() { /* powered=false at second 5 -> READY, ABORTED, disrupted false, tritium 4 */ }
@Test void saveLoadRoundTripKeepsABurn() { /* reach BURNING, save to tag, load into new FusionPhysics: state, loopC, tritium equal; next step not disrupted */ }
```

- [ ] **Step 2:** Run `./gradlew test --tests 'com.frozendawn.power.FusionPhysicsTest'`. Expected: compile failure, because `FusionPhysics` doesn't exist.
- [ ] **Step 3:** Implement the state machine in `FusionPhysics`.
  - A new `FusionPhysics` has `loopC = 20`. Outside BURNING the loop still cools through the radiators toward the mean sink.
  - `start()` begins cooldown from OFF, and tries ignition from READY. `stop()` from COOLDOWN or READY returns to OFF with no wear; while BURNING it is a disruption, the same as a redstone stop.
  - Ignition checks run in this order:
    1. hatch
    2. pressure (> `VACUUM_LIMIT` fails)
    3. deuterium
    4. tritium (< `IGNITION_TRITIUM` fails)
    5. wear (> `MAX_IGNITION_WEAR` fails)
  - `requestKw`: OFF is 0. COOLDOWN and READY are `COOLDOWN_KW`. IGNITION is `IGNITION_KW`.
  - Tritium is spent only when ignition completes.
  - Burning can be a stub here; Task 2 fills it in.
  - Persist state, loopC, the progress counters, tritium, deuterium progress, ash seconds, burn fraction and load-follow.
- [ ] **Step 4:** Run the same command. Expected: PASS.
- [ ] **Step 5:** Commit with `feat(sfr): add FusionPhysics startup state machine`.

### Task 2: FusionPhysics burn, fuel and disruptions

**Files:**
- Modify: `src/main/java/com/frozendawn/power/FusionPhysics.java`
- Test: `src/test/java/com/frozendawn/power/FusionPhysicsTest.java`

**Interfaces:**
- **Consumes:** the Task 1 API.
- **Produces:** the BURNING behavior that the controller in Task 5 relies on. `disrupted` is true only in the second a wear-adding disruption occurs. Every disruption ends in OFF.

- [ ] **Step 1: Write the failing tests.** `burning(f)` is `setReadyForTest(); addTritium(4); start();` followed by 10 `ok(14)` steps.

```java
@Test void fourteenRadiatorsCarryAFullBurn() {
    var f = new FusionPhysics(); burning(f);
    var r = run(f, ok(14), 3000);
    assertEquals(FusionPhysics.State.BURNING, r.state());
    assertTrue(r.loopC() > 820 && r.loopC() < 880, "About 848C: " + r.loopC());
    assertTrue(r.netKw() > 315 && r.netKw() < 340, "About 329 kW net: " + r.netKw());
}
@Test void tenRadiatorsOverheatPast1100AndDisrupt() { /* ok(10): ends OFF, DISRUPTION, exactly one readout with disrupted=true */ }
@Test void everyHardTriggerDisruptsAndWears() {
    // From a 3000 s burn, one second each with: redstone=true; hatchClosed=false; shellIntact=false;
    // channelPressure=0.06; deuterium=0 with deuterium progress due. Each -> OFF, DISRUPTION, disrupted=true.
}
@Test void unpaidHouseLoadShortfallQuenches() {
    // Right after ignition, setLoopC(ReactorPhysics.DEEP_VACUUM_C): next step netKw < 0 and requestKw == -netKw.
    // Next step with powered=false -> OFF, MAGNET_QUENCH, disrupted=true.
}
@Test void stoppedAshPumpFadesToAshPoisonedWithoutWear() {
    // pumping=false: heatKw at 30 s within 10% of half of the 3000 s value; at 60 s OFF, ASH_POISONED, disrupted=false.
}
@Test void breedingKeepsTheTankAndCapsAtEight() { /* tritium after 1200 s at full burn >= tritium before; after 200000 s == 8 */ }
@Test void deuteriumBurnsOneCanisterPerTenMinutesAtFullOutput() { /* sum of deuteriumUsed over 1200 s == 2 */ }
@Test void wearDeratesGrossOutput() { /* wear=0.5 grossKw within 1% of half the wear=0 grossKw at the same loopC */ }
@Test void loadFollowAtZeroDemandStaysLit() {
    // setLoadFollow(true), targetKw=0, 14 radiators, 3000 s: BURNING, netKw >= -1, disrupted never true.
}
```

- [ ] **Step 2:** Run the FusionPhysics tests. Expected: the new tests FAIL.
- [ ] **Step 3:** Implement BURNING in `step`, in this order each second:
  1. **Hard triggers:** redstone, `stop()`, hatch open, shell broken, pressure above limit. Any of these is a DISRUPTION.
  2. **Burn fraction:** slews by `LOAD_FOLLOW_SLEW` toward 1 in manual mode, or in load-follow toward `(target + HOUSE_KW + ASH_PUMP_KW) / efficiency(NOMINAL_C, meanSink)` divided by `RATED_KW`, capped at the radiator capacity at `NOMINAL_C`. The floor is `MIN_BURN`.
  3. **Heat:** `heat = RATED_KW * burn`, scaled by `(1 - ash/ASH_FADE_SECONDS)`.
  4. **Removed:** the sum over sinks of `HT_RADIATOR_KW_PER_C * max(0, loopC - sink)`.
  5. **Loop temperature:** `loopC += (heat - removed) / LOOP_HEAT_CAPACITY_KJ_PER_C`. Above `DISRUPT_C` is a DISRUPTION.
  6. **Electric output:** `gross = removed * ReactorPhysics.efficiency(loopC, meanSink) * (1 - wear)`, and `net = gross - HOUSE_KW - ASH_PUMP_KW`. If `net < 0`, then `requestKw = -net`, and an unpaid previous request is a MAGNET_QUENCH.
  7. **Fuel:** deuterium progress `+= burn/DEUTERIUM_SECONDS`. Reaching 1 with `deuterium == 0` is a DISRUPTION; otherwise set `deuteriumUsed=1`. Tritium changes by `burn/TRITIUM_SECONDS * (BREEDING_RATIO - 1)`, capped at `TRITIUM_TANK`.
  8. **Ash:** `ash++` while `!pumping`, reset to 0 while pumping. Reaching `ASH_FADE_SECONDS` is ASH_POISONED.

  When not burning, the loop still cools through the radiators.
- [ ] **Step 4:** Run the FusionPhysics tests. Expected: all PASS.
- [ ] **Step 5:** Commit with `feat(sfr): burn, fuel, breeding and disruptions in FusionPhysics`.

### Task 3: SFR blocks, items and the torus validator

**Files:**
- Create in `src/main/java/com/frozendawn/block/`:
  - `MagnetCoilBlock.java` (plain)
  - `FirstWallBlock.java` (`IntegerProperty WEAR` 0..20)
  - `SfrVacuumPortBlock.java` (`EnumProperty MODE` PUMP/VENT, horizontal FACING; using it toggles the mode)
  - `SfrHatchBlock.java` (extends `DoorBlock`; the pressure gate comes in Task 6)
  - `SfrMultiblock.java`
- Create stubs for `SfrControllerBlock`, `SfrFuelPortBlock` and `SfrPowerPortBlock`, completed in Task 5.
- Lithium Blanket and Confinement Core are plain `Block`s.
- Modify: `init/ModBlocks.java`, `init/ModItems.java` (block items, the two canister items as plain `Item`s, creative tab entries next to the reactor block)
- Resources:
  - `assets/frozendawn/lang/en_us.json`: names and status strings under `sfr.frozendawn.status.*`
  - blockstates, models and placeholder textures
  - `data/frozendawn/loot_table/blocks/*.json`: self-drops; `first_wall` drops only with the `block_state_property` condition `wear=0`
- Test: `src/gameTest/java/com/frozendawn/power/SfrGameTest.java` (batch `sfr`) and `src/gameTest/java/com/frozendawn/power/SfrScenes.java`

**Interfaces:**
- **Produces:**
  - `SfrMultiblock.center(BlockPos controller, Direction facing)` returns `controller.relative(facing.getOpposite(), 4)`. The controller sits on the r = 4 ring at Y3.
  - `SfrMultiblock.validate(Level, BlockPos controller, Direction facing)` returns a `Result`.
  - `enum Problem { NONE, COIL, FIRST_WALL, BLANKET, CORE, CHANNEL, PORTS, UNLOADED }`
  - `record Result(Problem problem, BlockPos wrong, BlockPos center, List<BlockPos> coolantPorts, BlockPos fuelPort, BlockPos powerPort, BlockPos vacuumPort, BlockPos hatch, Set<BlockPos> channel, List<BlockPos> firstWall, List<BlockPos> coils)` with `valid()`.
  - `SfrMultiblock.expected(int dx, int dy, int dz)` returns the required `Block`, or null for channel and port-eligible cells. Task 8's ghost outline uses it.
  - `SfrScenes.build(GameTestHelper h, BlockPos center)` builds a complete NORTH-facing torus and returns the controller pos. `SfrScenes.cell(h, center, dx, dy, dz)` gives absolute positions.
- **Geometry** (dy from -2 at Y1 to +2 at Y5, `r = max(|dx|, |dz|)`):

  | Cells | Required block |
  | --- | --- |
  | r ≤ 1, dy in -1..1 | Magnet Coil, except the Confinement Core at the origin |
  | r = 2, dy in -1..1 | First Wall, any WEAR |
  | r = 3, dy in -1..1 | air (the channel) |
  | r = 3, dy = ±2 | Magnet Coil |
  | r = 4, dy in -1..1, `floorMod(dx+dy+dz, 2) == 0` | Magnet Coil |
  | r = 4, dy in -1..1, otherwise | Lithium Blanket |

  - Any r = 4 cell that isn't a corner (`|dx| == |dz| == 4`) may instead hold a port.
  - The ports must total exactly 2 Coolant Ports, 1 Fuel Port, 1 Power Port, 1 Vacuum Port and 1 Hatch, whose lower half is at dy = -1 and upper half at dy = 0.
  - The controller cell is skipped.
  - Return the first wrong cell in x, y, z order. `UNLOADED` is returned before anything else is checked.

- [ ] **Step 1: Write the failing GameTests**
  - `sfrShellValidatesAndNamesTheWrongCell`: a built torus is valid with 2 coolant ports and 72 channel cells. Replacing the First Wall at (2,0,0) with stone gives `FIRST_WALL` with `wrong` at that cell. A dirt block in the channel at (3,0,0) gives `CHANNEL`. Removing the hatch gives `PORTS`.
  - `sfrWornFirstWallDropsNothing`: `Block.getDrops` for `first_wall` at `WEAR=1` is empty, and at `WEAR=0` it is one `first_wall`.
- [ ] **Step 2:** Run `./gradlew gameTestQuick -PfdTests=sfr`. Expected: compile failure, or FAIL.
- [ ] **Step 3:** Implement the blocks, registrations, resources and `SfrMultiblock.validate` per the table above.
- [ ] **Step 4:** Run `./gradlew gameTestQuick -PfdTests=sfr`. Expected: PASS.
- [ ] **Step 5:** Commit with `feat(sfr): add SFR-1 blocks, items and torus validator`.

### Task 4: High-Temperature Radiator

**Files:**
- Create: `block/HighTemperatureRadiatorBlock.java` (extends `ReactorRadiatorBlock`, so linking, sink, HOT and hot-floor behavior are inherited)
- Modify:
  - `init/ModBlocks.java`, `init/ModItems.java` and resources
  - `block/ReactorControllerBlockEntity.java:89-91`: exclude High-Temperature Radiators from CFU-7 sinks
- Test: `SfrGameTest.java`

**Interfaces:**
- **Produces:** `HighTemperatureRadiatorBlock.sinks(ServerLevel l, List<BlockPos> coolantPorts)` returns `List<BlockPos>` of linked radiators that are High-Temperature only and have a sink. Task 5 maps them through `ReactorRadiatorBlock.sink`.

- [ ] **Step 1: Write the failing GameTest** `sfrHighTemperatureRadiatorIsNotACfu7Sink`. Use a CFU-7 built as in `ReactorGameTest.build`, with one High-Temperature Radiator linked to its port, sky access, and `physics().setCoreC(300)`. After `second(level)`, `radiatorCount()==0`. Swapping in a standard radiator gives `radiatorCount()==1`.
- [ ] **Step 2:** Run `./gradlew gameTestQuick -PfdTests=sfrHigh`. Expected: FAIL.
- [ ] **Step 3:** Implement the block and the CFU-7 filter.
- [ ] **Step 4:** Run the same command. Expected: PASS. Also run `-PfdTests=reactor`. Expected: the CFU-7 tests still PASS.
- [ ] **Step 5:** Commit with `feat(sfr): add High-Temperature Radiator`.

### Task 5: Controller, Fuel Port, Power Port and the network

**Files:**
- Create in `block/`:
  - `SfrControllerBlock.java` (horizontal FACING, like `ReactorControllerBlock`)
  - `SfrControllerBlockEntity.java`
  - `SfrFuelPortBlock.java` and `SfrFuelPortBlockEntity.java` (`WorldlyContainer`, 1 slot, accepts only Deuterium Canisters)
  - `SfrPowerPortBlock.java` and `SfrPowerPortBlockEntity.java` (implements `PoweredMachine`)
  - `SfrControllerMenu.java`
- Create: `client/SfrControllerScreen.java` (readout text, Start, Stop, Load-follow and Extract buttons; layout is the builder's call)
- Modify:
  - `init/ModBlockEntities.java`, `init/ModMenuTypes.java` and client screen registration
  - `power/PowerLinkRules.java:16-33`: Power Port range 16
  - `power/PowerNetworks.java:302-306`: `supplyWatts` returns `SfrPowerPortBlockEntity.supplyWatts()`; `:139-141`: `scheduledWatts` adds `SfrPowerPortBlockEntity.scheduledWatts()`
- Test: `SfrGameTest.java`

**Interfaces:**
- **Consumes:** `FusionPhysics` (Tasks 1-2), `SfrMultiblock` (Task 3), `HighTemperatureRadiatorBlock.sinks` (Task 4).
- **Produces:**
  - `SfrControllerBlockEntity`:
    - `void second(ServerLevel)` (public, for GameTests)
    - `FusionPhysics physics()`, `FusionPhysics.Readout readout()`, `SfrMultiblock.Result shell()`
    - `double channelPressure()`, `void setChannelPressure(double)`
    - `double wear()`: the mean of First Wall WEAR divided by 20
    - `static Optional<SfrControllerBlockEntity> forHatch(ServerLevel, BlockPos hatchLower)`: a per-level map refreshed on every valid validation
  - `SfrPowerPortBlockEntity`:
    - `void publish(int requestWatts, int supplyWatts)`
    - `boolean paidFullSecond()`: true when the published request was 0, or when the last 20 ticks were all paid through `PowerNetworks.consumeMachineTick`; reading it resets the count
    - `int scheduledWatts()`: `(IGNITION_KW - COOLDOWN_KW) * 1000` while READY, else 0. `PowerNetworks.scheduledWatts` (line 139) adds it next to the Uplink's, so a load-following CFU-7 ramps before the pulse
    - `int supplyWatts()`
  - Controller container slots: 0 = Tritium Canister input (each canister loads 1 unit), 1 = Tritium Canister output (Extract moves 1 unit into a canister there).

`second(l)` order:
1. Validate. If UNLOADED, return without stepping.
2. Collect sinks.
3. `powered = powerPort.paidFullSecond()`.
4. Build Inputs:
   - `hatchClosed` from the hatch's OPEN state
   - `roomVacuum` = the controller's own room is vacuum
   - `pumping` = Vacuum Port MODE is PUMP
   - redstone from `hasNeighborSignal`
   - `targetKw` = `PowerNetworks.loadFollowTargetWatts(l, powerPort, supplyWatts)/1000.0` in load-follow mode
5. Step the physics.
6. Remove `deuteriumUsed` from the Fuel Port.
7. Publish the request and supply to the Power Port. Add `PUMP_KW` to the request while not burning, with MODE PUMP and channel pressure > 0.

- [ ] **Step 1: Write the failing GameTests**
  - `sfrIgnitesToBurningOnABankAndCreativeFuel`: build the torus; link 14 High-Temperature Radiators (sky access) and a charged bank holding at least 8 MJ to the Power Port (the 1.5 MJ pulse plus the house load while the loop warms); put 4 Deuterium Canisters in the Fuel Port; `addTritium(4)`; `setChannelPressure(0)`; `setReadyForTest()`; `start()`. Within 300 ticks the state is BURNING and tritium is 3.
  - `sfrBankTooSmallAbortsIgnitionCleanly`: the same, with a bank holding 1.0 MJ. Within 300 ticks the state is READY, the status is ABORTED, tritium is 4 and wear is 0.
  - `sfrFuelPortRefusesOtherItems`: `canPlaceItem` is false for dirt and for a Tritium Canister, and true for a Deuterium Canister.
  - `sfrControllerTagRoundTripKeepsBurning`: once BURNING, save the controller with `saveWithFullMetadata`, load it into a fresh block entity at the same position, and call `second`. It is still BURNING with `disrupted` false.
- [ ] **Step 2:** Run `./gradlew gameTestQuick -PfdTests=sfr`. Expected: the new tests FAIL.
- [ ] **Step 3:** Implement the block entities, the network hooks, the menu and the screen. Register the Power Port with `PowerNetworks.machineLoaded` and `machineRemoved` like the Induction Furnace.
- [ ] **Step 4:** Run `./gradlew gameTestQuick -PfdTests=sfr`. Expected: PASS.
- [ ] **Step 5:** Commit with `feat(sfr): SFR-1 controller, fuel and power ports`.

### Task 6: Vacuum channel: pressure, Vacuum Port and hatch

**Files:**
- Modify:
  - `block/SfrControllerBlockEntity.java` (channel pressure each second, saved)
  - `block/SfrHatchBlock.java` (pressure gate on use)
  - `airlock/AirlockSavedState.java` (add `public void setRoomGas(ServerLevel level, Set<BlockPos> cells, int total)`, which wraps `distributeRoomGas` and `syncAir`)
- Test: `SfrGameTest.java` (vacuum-stage setup and teardown as in `AirlockGameTest:28-33`)

**Interfaces:**
- **Consumes:** `SfrControllerBlockEntity.forHatch`, `AirlockSavedState.roomGas`, `receiveRoomAir`, `CombustionAtmosphere.isVacuum`.
- **Produces:** `SfrControllerBlockEntity.outsidePressure(ServerLevel)`: 1.0 outside the vacuum stage; during it, the room-gas fraction of the room in front of the Vacuum Port (0 if open to vacuum).

**Rules, applied before the physics step:**
- **Unsealed:** if the shell is invalid or the hatch is open, the channel equalizes with the outside: pressure = outside pressure.
- **Sealed, outside the vacuum stage:**
  - PUMP mode with a paid request lowers pressure by 1/150 per second, to a floor of 0.
  - VENT mode moves pressure 10% per second toward the outside pressure.
  - The removed gas is simply exhausted.
- **Sealed, during the vacuum stage:**
  - Pressure is read from `roomGas(channel)/(72*UNITS_PER_CELL)`.
  - The pump moves `72*UNITS_PER_CELL/150` units per second out with `setRoomGas` and offers them to the outer room with `receiveRoomAir`. What it refuses is lost.
  - VENT equalizes the two rooms' gas fractions at the same 10% per second.
- **Crossing into or out of the vacuum stage while sealed:** on the first second the stage differs from the last saved stage, write `setRoomGas(channel, round(pressure*72*UNITS_PER_CELL))` (entering) or keep the read fraction (leaving). Pressure never changes across the crossing.
- **Hatch:**
  - Use and redstone both go through the same gate. It opens only if the channel and outside pressures match by the airlock rule (`AirlockManager` line 296: vacuum matches only vacuum; otherwise both are positive and within 5%). If refused, notify with `AirlockStatusPayload.Status.VACUUM_DIFFERENCE` and play `AIRLOCK_REFUSE`.
  - A hatch with no controller opens freely.

- [ ] **Step 1: Write the failing GameTests**
  - `sfrChannelPumpsDownOutsideTheVacuumStage`: a sealed torus starts at pressure 1.0. With the pump paid, it is ≤ 0.05 within 145 s (driven with `second`; payment stubbed by a linked charged bank).
  - `sfrHatchRefusesAcrossAPressureDifference`: at pressure 0 with outside 1.0, using the hatch leaves it closed. After VENT runs to 1.0, it opens.
  - `sfrOpenHatchShowsTorusOpenAndBlocksIgnition`: open hatch, `setReadyForTest`, `start()`. The status is TORUS_OPEN.
  - `sfrVacuumPortExhaustsIntoTheOuterRoom`: in the vacuum stage, with a sealed 3x3x3 room in front of the Vacuum Port at 50% gas and the channel full, pumping 10 s raises the outer room's `roomGas` and lowers the channel's by the same amount, until the outer room is full.
  - `sfrPressureSurvivesEnteringTheVacuumStage`: sealed at 0.4, then switch on the vacuum stage and call `second`. `channelPressure()` stays within 0.01 of 0.4.
- [ ] **Step 2:** Run `./gradlew gameTestQuick -PfdTests=sfr`. Expected: the new tests FAIL.
- [ ] **Step 3:** Implement the rules above.
- [ ] **Step 4:** Run `./gradlew gameTestQuick -PfdTests=sfr`. Then also run `-PfdTests=airlock`. Expected: all PASS.
- [ ] **Step 5:** Commit with `feat(sfr): vacuum channel, Vacuum Port and Maintenance Hatch`.

### Task 7: Disruptions in the world

**Files:**
- Modify: `block/SfrControllerBlockEntity.java`
- Test: `SfrGameTest.java`

**Interfaces:**
- **Consumes:** `Readout.disrupted`, `Readout.status`, `Result.firstWall`, `Result.coils`.
- **Produces:** wear applied to blocks, and the quench effects.

**Behavior:**
- **On `disrupted`:** every First Wall in the shell gets `WEAR+1`, capped at 20.
- **On MAGNET_QUENCH:**
  - The quench sound is Task 9's.
  - Spawn `ParticleTypes.SNOWFLAKE` on random coil faces for 100 ticks.
  - Room O2 is not changed.
- **A broken shell while burning** reaches the physics as `shellIntact=false`, so it disrupts in the same second.

- [ ] **Step 1: Write the failing GameTests**
  - `sfrMiningAFirstWallWhileBurningDisruptsAndWears`: once BURNING (the Task 5 setup), destroy the First Wall at (2,0,0), restore it fresh, and call `second`. The state is OFF and every other First Wall has `WEAR==1`.
  - `sfrOpeningTheHatchWhileBurningDisrupts`: once BURNING, set the hatch OPEN directly with `setBlock` (bypassing the gate), then call `second`. The state is OFF, the status DISRUPTION, and every First Wall has `WEAR==1`.
  - `sfrQuenchDoesNotTouchRoomGas`: in the vacuum stage, with the reactor in a sealed 15x9x15 room, cut the bank right after ignition while the loop is cold. The status is MAGNET_QUENCH, and the room's `roomGas` is unchanged.
- [ ] **Step 2:** Run `./gradlew gameTestQuick -PfdTests=sfr`. Expected: the new tests FAIL.
- [ ] **Step 3:** Implement the behavior.
- [ ] **Step 4:** Run `./gradlew gameTestQuick -PfdTests=sfr`. Expected: PASS.
- [ ] **Step 5:** Commit with `feat(sfr): disruption wear and magnet quench effects`.

### Task 8: Ghost outline, comparator and controller screen

**Files:**
- Modify: `block/SfrControllerBlock.java`, `block/SfrControllerBlockEntity.java`, `client/SfrControllerScreen.java`

**Behavior:**
- **Using the controller with an invalid shell:**
  - It spawns `ParticleTypes.END_ROD` for 100 ticks at up to 32 wrong cells, chosen by comparing against `SfrMultiblock.expected`.
  - It shows the first wrong cell's coordinates and the expected block name in the action bar.
- **The screen shows** every field listed in the design's "Controller screen" section, and the status in the exact strings.
- **Comparator:** output is `round(loopC / DISRUPT_C * 15)`, clamped to 0-15.

- [ ] **Step 1: Write the failing GameTest** `sfrComparatorTracksLoopTemperature`. With `setLoopC(550)`, `comparator()==8`. With `setLoopC(0)`, it is 0.
- [ ] **Step 2:** Run `./gradlew gameTestQuick -PfdTests=sfrComparator`. Expected: FAIL.
- [ ] **Step 3:** Implement the behavior. The visuals have no headless test; Task 10's playtest covers them.
- [ ] **Step 4:** Run `./gradlew gameTestQuick -PfdTests=sfr`. Expected: PASS.
- [ ] **Step 5:** Commit with `feat(sfr): ghost outline, comparator and controller screen`.

### Task 9: SFR-1 running and alert sounds

**Files:**
- Create: `tools/generate_sfr_sounds.sh` (ffmpeg `lavfi` synthesis only, like `tools/generate_interface_sounds.sh`; fixed noise seeds so a rerun reproduces the same files)
- Create: `src/main/resources/assets/frozendawn/sounds/blocks/sfr/*.ogg` (output of the script, committed)
- Create: `power/SfrAudioCues.java`, `client/SfrLoopSound.java` (one tickable loop per controller and kind, following the one-instance-per-reactor and heartbeat pattern in `client/ReactorAlarmSound.java`)
- Modify: `init/ModSounds.java`, `assets/frozendawn/sounds.json`, `lang/en_us.json` (a subtitle for each), `block/SfrControllerBlockEntity.java` (sync the fields the client loops read, the same way the CFU-7 controller syncs `alarm()`), `block/SfrVacuumPortBlock.java` or its controller hook (pump loop position), `tools/build_audio_inventory.py` if it needs the new paths classified as generated, then regenerate `docs/audio/SHIPPED_AUDIO_INVENTORY.tsv`
- Test: `src/test/java/com/frozendawn/power/SfrAudioCuesTest.java`, `src/test/java/com/frozendawn/power/SfrSoundAssetsTest.java`

**Sounds** (event ids under `frozendawn:blocks.`, files under `sounds/blocks/sfr/`; the recipes are starting points, tuned by ear):

| Event | File | Kind | Plays | Character |
| --- | --- | --- | --- | --- |
| `sfr_cryo` | `cryo_loop` | loop | COOLDOWN and READY, at the controller | cryocooler: a 1 Hz soft chuff on brown noise with a faint high helium tick |
| `sfr_ignition` | `ignition` | client one-shot, cut off if ignition aborts | IGNITION | 10 s capacitor charge: a sweep from about 80 to 900 Hz with a rising whine, ending in a heavy thump |
| `sfr_burn` | `burn_loop` | loop | BURNING; volume `0.3 + 0.7 * heatKw / RATED_KW`, pitch `0.85 + 0.3 * heatKw / RATED_KW` | 40 Hz sub beating against 40.25 Hz, 80/120/160 Hz magnet hum, detuned A3/E4/A4 field choir, plasma roar breathing at 0.5 Hz, ion shimmer, sparse crackles, 1.5 Hz pulse (revised after the first playtest) |
| `sfr_pump` | `pump_loop` | loop | at the Vacuum Port while its pump draws; pitch `0.8 + 0.4 * (1 - channelPressure)` | rotary vacuum pump: a 25 Hz pulsed grind that thins as the channel empties |
| `sfr_alarm` | `alarm_loop` | loop | while `SfrAudioCues.alarm` is true | descending two-tone warning, about 1046 then 784 Hz, cycling every 1.2 s |
| `sfr_ready` | `ready` | one-shot | COOLDOWN to READY | short rising three-note chime |
| `sfr_ignited` | `ignited` | one-shot | IGNITION to BURNING | plasma bloom, rising A major bell arpeggio, held chord fading over 3.2 s (added after the first playtest) |
| `sfr_refuse` | `refuse` | one-shot | a refused Start, or an aborted ignition | low 0.6 s double buzz |
| `sfr_disruption` | `disruption` | one-shot | every disruption except MAGNET_QUENCH, and ASH_POISONED | a sharp electric crack into a falling 600 to 40 Hz plasma collapse, about 2.5 s |
| `sfr_quench` | `quench` | one-shot | MAGNET_QUENCH | a 5 s roaring cryogen vent: loud high-passed noise, fast attack, slow decay over a low rumble |

**Asset rules:**
- Every positional sound is mono (`-ac 1`); Minecraft only attenuates mono sounds by distance.
- Encode with `-c:a vorbis -strict experimental -q:a 5` at 44.1 kHz, as the existing generators do.
- Loops are seamless: tone frequencies are whole multiples of `1 / duration`, and noise beds crossfade their last 0.5 s over their first.
- Loudness: one-shot alerts are louder than loops, and the quench is the loudest SFR-1 sound.

**Interfaces:**
- **Consumes:** `FusionPhysics.Readout` (Tasks 1-2), the controller's synced state (Task 5), `channelPressure()` (Task 6).
- **Produces:**
  - `enum SfrAudioCues.Cue { READY, REFUSE, DISRUPTION, QUENCH }`
  - `static EnumSet<Cue> SfrAudioCues.cues(FusionPhysics.Readout before, FusionPhysics.Readout after)`, where `before` is null on the first second
  - `static boolean SfrAudioCues.alarm(FusionPhysics.Readout r, boolean pumping)`: true while BURNING and either `loopC >= 1000` or `!pumping` (ash building). The first playtest dropped `netKw < 0`: every warm-up runs on network power, and an uncovered shortfall quenches the same second
  - The controller plays each cue once with `level.playSound` at the controller. The client loops read the synced state.

- [ ] **Step 1: Write the failing tests**

```java
// SfrAudioCuesTest, readouts built with a small helper r(state, status, loopC, netKw, disrupted)
@Test void cooldownToReadyChimes()          { /* COOLDOWN -> READY: cues == {READY} */ }
@Test void refusedStartAndAbortBuzz()       { /* READY -> READY with TORUS_OPEN, and IGNITION -> READY with ABORTED: {REFUSE} */ }
@Test void disruptionCracksButQuenchVents() { /* disrupted DISRUPTION: {DISRUPTION}; disrupted MAGNET_QUENCH: {QUENCH} only */ }
@Test void ashPoisonedCracksWithoutWear()   { /* BURNING -> OFF with ASH_POISONED, disrupted false: {DISRUPTION} */ }
@Test void steadyBurnIsSilent()             { /* BURNING -> BURNING at 848C, net 329: cues empty, alarm false */ }
@Test void alarmAboveAThousandOrShortfall() { /* BURNING at 1000C: alarm; BURNING net -10: alarm; BURNING with pumping false: alarm; OFF at 1050C: no alarm */ }

// SfrSoundAssetsTest
@Test void everySfrSoundExistsAndIsMono() {
    // Parse src/main/resources/assets/frozendawn/sounds.json. For each key starting "blocks.sfr_":
    // every "name" resolves to an existing .ogg, and its Vorbis identification header
    // ("\u0001vorbis", then 4 version bytes) has channel count 1. The nine events above are all present.
}
```

- [ ] **Step 2:** Run `./gradlew test --tests 'com.frozendawn.power.SfrAudio*' --tests 'com.frozendawn.power.SfrSoundAssets*'`. Expected: FAIL.
- [ ] **Step 3:** Write `tools/generate_sfr_sounds.sh` and run it. Register the events, subtitles, `SfrAudioCues`, the cue playback and `SfrLoopSound`. Refresh the audio inventory.
- [ ] **Step 4:** Run the same tests, then `./gradlew gameTestQuick -PfdTests=sfr`. Expected: PASS. How they sound is the owner's call in Task 10.
- [ ] **Step 5:** Commit with `feat(sfr): add SFR-1 running and alert sounds`.

### Task 10: Verification and playtest handoff

**Files:**
- Create: `docs/solstice-reactor-playtest.md` (structure like `docs/airlock-playtest.md`)

- [ ] **Step 1:** Run `./gradlew architectVerify` in the background. Expected: BUILD SUCCESSFUL, with every GameTest passing at the normal tick rate. Report any failures with their output; do not retry them blindly.
- [ ] **Step 2:** Write the playtest checklist. It must:
  - include a creative `/give` list
  - describe the build order
  - include the expected readings at 14 High-Temperature Radiators (about 848C and 329 kW net)
  - list the things to judge by eye:
    - placeholder textures
    - ghost outline
    - quench frost
    - every SFR-1 sound by ear: cryo, ignition, burn, pump, alarm, ready, refuse, disruption, quench
    - TORUS OPEN
    - hatch refusal
  - state the known breathing limitation outside the vacuum stage
- [ ] **Step 3:** Commit with `docs(sfr): add SFR-1 core playtest checklist`.
- [ ] **Step 4:** Hand back to the owner for the visual playtest through `./gradlew runClient --console=plain` in this worktree. Do not open a PR or merge without the owner's approval.

## Addendum: overdrive and disruption mitigation (2026-10-24)

Owner request: overcharge like the CFU-7, but failing the way a fusion reactor would, with the walls melting rather than a fission meltdown.

- `FusionPhysics`: `drive` from 1 to `MAX_DRIVE = 1.5` in `DRIVE_STEP = 0.1`; `mitigation` defaults on and clamps drive to 1, and turning it on drops drive to 1. The burn goal is `drive` (load-follow clamps to it). Burn above 1 adds `(burn - 1) / OVERDRIVE_WEAR_SECONDS` (150) to a wear accumulator; each whole unit sets `Readout.worn` for one second. Above `DISRUPT_C` the status is `DISRUPTION` with mitigation on and the new `WALL_MELT` with it off. Saves without the keys load with mitigation on and drive 1.
- Controller: `worn` adds one wear step; a `WALL_MELT` disruption turns every First Wall of the last valid shell into magma (no wear step) and quenches the coils. Menu buttons 4 and 5 set mitigation; `DRIVE + steps` sets drive. Data slot `DRIVE` (percent) and flag `F_MITIGATION`.
- Screen: Drive slider and DMS button beside the tritium slots; disabling DMS takes one overlay with a 3 s hold, not the CFU-7's two.
- Sound: disabling mitigation plays a one-shot `sfr_dms_off` alert (owner revision; the first build sounded the warning loop throughout overdrive). The alarm stays loop >= 1000C or pump off. A melt-through plays the disruption and quench cues together.


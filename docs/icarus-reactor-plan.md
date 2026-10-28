# AMR-1 Icarus Reactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the optional ORSA AMR-1 "Icarus" antimatter reactor: an open Draconic-style sphere-and-stabilizer multiblock with a Halsey Cascade simulation, staged startup, CLIP, Cole Protocol, predictive warnings, instant detonation and a persistent budgeted crater.

**Architecture:** A pure `IcarusPhysics` class owns every number and state transition and is stepped once per second by the controller block entity, following `FusionPhysics` and `SfrControllerBlockEntity`. The `IcarusStructure` validator anchors on the controller, following `SfrMultiblock`, and feeds a ghost guide generalized from `SfrBlueprintRenderer`. Detonation hands a radius to `IcarusDetonation`, which applies the immediate effects and queues an `IcarusCraterData` job. A server tick processor carves that job chunk by chunk under a time budget.

**Tech Stack:** Java 21, NeoForge 1.21.1, JUnit 5 (`src/test`), NeoForge GameTests (`src/gameTest`), Python and shell asset generators in `tools/`.

**Spec:** [docs/icarus-reactor-design.md](icarus-reactor-design.md). Read it before any task. Where this plan makes a builder's call, it says so. The spec wins on intent.

## Global Constraints

- Worktree `/Volumes/SSD/Work/frozen-dawn-icarus`, branch `feat/icarus-reactor`. Never use a `codex/` or `claude/` prefix. Never merge without the owner's explicit approval.
- Use Conventional Commits (`feat(icarus): ...`, `test(icarus): ...`, `docs(icarus): ...`). No emojis anywhere.
- Icarus is never required for Exodus, Maeve, any ending or any progression item.
- Every Icarus block gets its own texture. No block reuses another block's texture. There is no observation window and no enclosed vessel.
- Every Icarus sound uses the path prefix `blocks.icarus_` and is audible in vacuum through the `SoundMuffler` exception, keeping the volume sliders and distance falloff.
- Each state is shown with real motion. Every visual follows the simulation state.
- Antimatter is counted in micro-capsules (`long`, `IcarusPhysics.CAPSULE = 1_000_000`) and used per energy produced, never per elapsed time.
- Power targets (first pass):

  | Setting | Gross | Shield draw | Net |
  |---|---|---|---|
  | Minimum (the default) | ~1 MW | ~400 kW | ~600 kW |
  | Full | ~2.5 MW | ~1.3 MW | ~1.2 MW |
  | Startup | none | ~1 MW from outside | none |

  Per capsule: about 2 h and about 3x return at Minimum, about 30 min and about 1.5x at full. The foundry spends 1.44 GJ per capsule. Icarus is always a net source.
- Explosion radius = 400 x cbrt(capsules / 64). Everything except bedrock breaks. No drops.
- An unloaded reactor is paused. Save, reload and restart never cause a failure on their own.
- Matter feed alarms and klaxons are nonverbal. The EVA suit voice relays alarms.
- Generated playtest worlds suppress advancement popups and chat awards, and preselect difficulty, per `AGENTS.md`. Never test detonation in an ordinary save.
- Fast loop: `./gradlew gameTestQuick -PfdTests=icarus`. Unit tests: `./gradlew test --tests 'com.frozendawn.power.Icarus*'`. The gate before handing back is `./gradlew architectVerify`.

## Review Focus

1. **A block placed inside the shield or a beam path while the reactor is live.** The shield vaporizes it without drops. It neither detonates the reactor nor freezes it. (Test: Task 7, `icarusShieldVaporizesIntrudingBlocks`.)
2. **TNT, a mob, a piston or `/setblock` removing a part while the core holds charge.** Any removal detonates the reactor. Pistons cannot move parts. (Test: Task 8, `icarusExplosionNextToFrameDetonates`, plus the piston assertion in the same test.)
3. **Save and reload mid-startup, mid-Cole Protocol or mid-crater.** Each resumes where it was, with no detonation and no restarted crater. (Tests: Task 3 `saveLoadRoundTripMatchesUninterruptedRun`; Task 9 `icarusCraterResumesFromSavedData`.)
4. **Two reactors inside one blast.** The second detonates as its own job. The first reactor's own parts never retrigger it. (Tests: Task 8, `icarusDetonationDoesNotRetriggerItself` and `icarusCraterChainsIntoASecondReactor`.)
5. **Chunks at the blast edge are unloaded or not yet generated.** They are carved when they load. The job finishes only when every chunk is done. (Test: Task 9, `icarusCraterSkipsUnloadedChunksUntilLoaded`.)

---

## File Map

Unless noted, all paths are under `src/main/java/com/frozendawn/`.

| Area | Files |
|---|---|
| Simulation (pure) | `power/IcarusPhysics.java`, `power/IcarusBlast.java`, `power/IcarusAudioCues.java`, `power/IcarusAdvancements.java` |
| Structure | `block/IcarusStructure.java`, `block/GhostCell.java` (moved out of `SfrMultiblock.Wrong`) |
| Blocks | `block/IcarusPartBlock.java` (shared base), `block/HalseyCoreBlock(Entity).java`, `block/IcarusStabilizerBlock(Entity).java`, `block/IcarusInjectorBlock(Entity).java`, `block/IcarusControllerBlock(Entity).java`, `block/IcarusControllerMenu.java`, `block/IcarusPowerPortBlock(Entity).java`, `block/IcarusMatterPortBlock(Entity).java`, `block/IcarusCapsuleDockBlock(Entity).java`, `block/AntiparticleFoundryBlock(Entity).java` |
| Items | `item/AntimatterCapsuleItem.java` |
| World | `world/IcarusDetonation.java`, `world/IcarusCraterData.java`, `world/IcarusCrater.java` |
| Network | `network/IcarusBlueprintPayload.java`, `network/IcarusSettingsPayload.java`, `network/IcarusBlastPayload.java` |
| Client | `client/GhostBlueprintRenderer.java` (replaces `SfrBlueprintRenderer`), `client/IcarusControllerScreen.java`, `client/HalseyCoreRenderer.java`, `client/IcarusStabilizerRenderer.java`, `client/IcarusInjectorRenderer.java`, `client/IcarusLoopSound.java`, `client/IcarusBlastEffects.java` |
| Registration | `init/ModBlocks`, `ModItems`, `ModBlockEntities`, `ModMenuTypes`, `ModSounds`, `ModDataComponents`, `ModDamageTypes`, `network/ModNetworking`, `network/ClientHandlers`, `config/FrozenDawnConfig` |
| Assets | `tools/generate_icarus_textures.py`, `tools/generate_icarus_sounds.sh`, suit lines via `tools/generate_aggregate_suit_tts.sh`, `src/main/resources/assets/frozendawn/{blockstates,models,textures,sounds}`, `sounds.json`, `lang/en_us.json`, `AUDIO_NOTICE.md` |
| Data | `src/main/resources/data/frozendawn/recipe/` (and `recipe/fabricator/`), `advancement/`, `damage_type/icarus_detonation.json` |
| Tests | `src/test/java/com/frozendawn/power/IcarusPhysicsTest.java`, `IcarusBlastTest.java`, `IcarusAudioCuesTest.java`, `IcarusAdvancementsTest.java`, `IcarusAssetsTest.java`; `src/gameTest/java/com/frozendawn/power/IcarusGameTest.java`, `IcarusScenes.java`, `IcarusCraterGameTest.java` |
| Playtest | `tools/prepare_icarus_playtest.py` |

## Geometry (builder's call, fixed here so the tests agree)

The geometry uses a canonical frame with the controller facing SOUTH (+z), the core at the origin and y pointing up. Rotate (x, z) by the controller's `FACING` for other facings. The structure is 17 x 17 x 8 of placed blocks, and the rendered shield rises above it. The spec's 19 x 19 x 13 was approximate.

| Part | Cells |
|---|---|
| Halsey Core | (0, 0, 0) |
| Matter Injector (nozzle) | (0, -5, 0) |
| Injector Casing | 3 x 3 at y = -6, abs(x) <= 1, abs(z) <= 1 (9 cells) |
| Stabilizer emitter | (±7, 0, 0), (0, 0, ±7), each `FACING` the core |
| Stabilizer Housing | 3 x 3 behind each emitter at distance 8, perpendicular to the axis, y -1..1 (36 cells) |
| Icarus Frame | the base ring max(abs(x), abs(z)) = 8 at y = -6 (64 cells); the cross arms (x, -6, 0) and (0, -6, z) for 2 <= abs(n) <= 7 (24 cells); the pillars (±8, y, 0) and (0, y, ±8) for y -5..-2 (16 cells) |
| Support blocks on the ring top, y = -5, z = 8 | matter port x = -3, capsule dock x = -2, controller x = -1, power port x = +1, reserve bank x = +2 |
| Live envelope (must be air at Start; vaporized while live) | every cell with distance <= 4.5 from the origin except the core, plus the beam cells at distance 5 and 6 on each stabilizer axis |

Controller to core: `core = controller + rotate(1, 5, -8)`.

Build stages for the ghost guide, in order:
1. frame;
2. injector casing and injector;
3. stabilizer housings and emitters;
4. Halsey Core;
5. support blocks.

---

### Task 1: IcarusPhysics startup sequence

**Files:**
- Create: `power/IcarusPhysics.java`
- Test: `src/test/java/com/frozendawn/power/IcarusPhysicsTest.java`

**Interfaces:**
- Produces:

```java
public final class IcarusPhysics {
    public static final long CAPSULE = 1_000_000L;
    public static final int MAX_CAPSULES = 64, MIN_START_CAPSULES = 2;
    public static final double MIN_OUTPUT_MW = 1.0, MAX_OUTPUT_MW = 2.5, STARTUP_DRAW_KW = 1000;
    public static final double RESERVE_MJ = 240, RESERVE_BOOT_MJ = 20;
    /** Synced by ordinal: append new values only. */
    public enum State { COLD, POWER_CHECK, RESERVE_CHARGE, STABILIZER_SPINUP, COMMISSIONING, INJECTION, PRIMING, IGNITION, RAMP,
        RUNNING, CLIPPED, COLE_RAMP_DOWN, COLE_COOL, COLE_RECOVER, COLE_DISCHARGE, DETONATED }
    public enum Status { OK, HOLD_STRUCTURE, HOLD_POWER, HOLD_CAPSULES, HOLD_MATTER, HOLD_CAPSULE_SPACE, MATTER_STARVED, RESERVE_IN_USE, SAFE_TO_DISMANTLE }
    public enum Alarm { NONE, CAUTION, WARNING, CRITICAL }
    public enum Cascade { STABLE, STRAINED, RUNAWAY }
    public record Settings(double outputMw, double shield, int feedPerMinute) {
        public static final Settings MINIMUM = new Settings(1.0, 0.6, 12); }
    /** powered: last second's request was paid. dockCharge: sealed charge the dock can inject now (whole capsules, within the cap).
     * dockEmpties: empty casings in the dock. matter: plain blocks in the matter port. */
    public record Inputs(boolean structureValid, boolean powered, long dockCharge, int dockEmpties, int matter) {}
    public record Readout(State state, Status status, int stepSecondsLeft, double grossMw, double shieldDrawMw, double netMw,
        double requestKw, double coreC, double shieldPct, double reserveMJ, long coreCharge, long injected, long recovered,
        int matterUsed, double shieldEtaS, double redLineEtaS, double reserveEtaS, double antimatterEtaS,
        Alarm alarm, Cascade cascade, boolean detonate) {}
    public boolean start(); public boolean clip(); public boolean cole(); public void settings(Settings s); public Settings settings();
    public Readout step(Inputs in); public State state(); public long coreCharge(); public boolean commissioned();
    public void save(CompoundTag t); public void load(CompoundTag t);
    public void detonated();                  // clears the charge after the controller has queued the blast
    void setCoreChargeForTest(long c); void setCommissionedForTest(boolean b);
}
```

- [ ] **Step 1: Write the failing tests**

```java
@Test void coldStartTakesTwoMinutes()           // commissioned, powered, dockCharge = 2*CAPSULE, matter = 64
    // seconds 1-10 POWER_CHECK, 11-30 RESERVE_CHARGE, 31-60 STABILIZER_SPINUP, 61-80 INJECTION,
    // 81-95 PRIMING, 96-100 IGNITION, 101-120 RAMP; the readout of second 121 has state RUNNING
@Test void firstBootAddsOneMinuteOfCommissioning()  // not commissioned: COMMISSIONING spans seconds 61-120; RUNNING on second 181; commissioned() is true afterwards
@Test void startupDrawsAboutOneMegawattDuringReserveAndStabilizerSteps()  // requestKw == 1000 during RESERVE_CHARGE, STABILIZER_SPINUP and COMMISSIONING
@Test void reserveHoldsBootChargeAfterStepTwo()      // reserveMJ at the end of RESERVE_CHARGE == RESERVE_BOOT_MJ ± 0.5
@Test void unpaidStartupHoldsInsteadOfFailing()      // powered=false during RESERVE_CHARGE: state stays, status HOLD_POWER, stepSecondsLeft unchanged; resumes when powered
@Test void invalidStructureHoldsAtPowerCheck()       // structureValid=false: POWER_CHECK, HOLD_STRUCTURE
@Test void injectionHoldsBelowTwoCapsules()          // dockCharge = CAPSULE + CAPSULE/2: INJECTION, HOLD_CAPSULES
@Test void injectionMovesDockChargeIntoCore()        // the last INJECTION second reports injected == dockCharge and coreCharge == dockCharge
@Test void primingHoldsWithoutMatter()               // matter = 0: PRIMING, HOLD_MATTER
@Test void stopBeforeInjectionReturnsCold()          // cole() during STABILIZER_SPINUP: COLD on the next step, coreCharge 0
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew test --tests 'com.frozendawn.power.IcarusPhysicsTest'`
Expected: compilation fails because `IcarusPhysics` is missing.

- [ ] **Step 3: Implement the state machine in `IcarusPhysics`**

Step durations: POWER_CHECK 10, RESERVE_CHARGE 20, STABILIZER_SPINUP 30, COMMISSIONING 60 (only when not commissioned), INJECTION 20, PRIMING 15, IGNITION 5, RAMP 20.

A hold freezes `stepSecondsLeft`. RESERVE_CHARGE charges the reserve from the grid at 1 MW (20 MJ). Injection takes `min(dockCharge, MAX_CAPSULES*CAPSULE - coreCharge)` on the step's last second.

`save`/`load` use NBT like `FusionPhysics`: state by name, plus every field this task adds.

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew test --tests 'com.frozendawn.power.IcarusPhysicsTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/frozendawn/power/IcarusPhysics.java src/test/java/com/frozendawn/power/IcarusPhysicsTest.java
git commit -m "feat(icarus): Halsey Cascade startup sequence"
```

### Task 2: Cascade, shield, reserve, fuel and warnings

**Files:**
- Modify: `power/IcarusPhysics.java`
- Test: `src/test/java/com/frozendawn/power/IcarusPhysicsTest.java`

**Interfaces:**
- Consumes: Task 1.
- Produces:
  - `public static double mjPerCapsule(double grossMw)`, which returns `7200 - 1800 * (clamp(grossMw, 1.0, 2.5) - 1.0)`;
  - `public static final double MJ_PER_MATTER_BLOCK = 5, RED_LINE_C = 6500, CRITICAL_C = 8000, AMBIENT_C = 20`;
  - `public static final double RESERVE_RECHARGE_KW = 200`.

**Builder's calls fixed here:**
- The reserve holds 240 MJ, which is 10 minutes at the Minimum shield draw. Its ETA is shorter at higher draw.
- Startup charges the first 20 MJ from the grid. The rest fills from the reactor's own surplus at 200 kW while running, which lowers net output until the reserve is full.
- The reserve never draws from the grid while it is backing the shield.
- Matter: 1 block per 5 MJ gross, which is 12 per minute at Minimum and 30 per minute (1 every 2 s) at full.
- Underfeeding caps gross output at `feedPerMinute / 12` MW. Overfeeding by 10 blocks per minute raises the equilibrium temperature by about 400 °C and wastes matter.
- Reactivity rises as the load depletes (`1 + 0.6 * used / loaded` is the suggested start). The formulas are otherwise the builder's, tuned to the tests below.

- [ ] **Step 1: Write the failing tests** (the helper `runToRunning(settings, capsules)` reuses Task 1's sequence)

```java
@Test void antimatterIsUsedPerEnergyNotTime()     // mjPerCapsule(1.0)==7200, mjPerCapsule(2.5)==4500, mjPerCapsule(0.3)==7200
@Test void minimumRunsAboutTwoHoursPerCapsule()   // MINIMUM, 3 capsules: charge used over 7200 s RUNNING == CAPSULE ± 3%
@Test void minimumReturnsAtLeastTwoPointSevenTimesTheFoundryCost()  // sum(netMw) over that run * 1 s >= 2.7 * 1440 MJ
@Test void fullOutputBurnsACapsuleInAboutThirtyMinutes()  // Settings(2.5, 1.0, 30): charge used over 1800 s == CAPSULE ± 5%
@Test void fullOutputReturnsAboutOnePointFiveTimes()      // net over that run between 1.3 and 1.7 * 1440 MJ, and always > 1440
@Test void minimumNetIsAboutSixHundredKilowatts()         // fresh core with a full reserve: netMw 0.60 ± 0.06, shieldDrawMw 0.40 ± 0.05
@Test void fullNetIsAboutOnePointTwoMegawatts()            // Settings(2.5, 1.0, 30), full reserve: netMw 1.2 ± 0.18, shieldDrawMw 1.3 ± 0.2
@Test void minimumNeverRaisesMoreThanCaution()            // MINIMUM over a whole 2-capsule load: alarm <= CAUTION, never detonate
@Test void depletionDriftForcesARetuneAtFullOutput()      // Settings(2.5, 1.0, 30), 4 capsules: alarm reaches WARNING before the load is spent
@Test void shieldDrainClimbsSteeplyAboveTheRedLine()      // a test hook reads the drain at RED_LINE_C+500, which is >= 3x the drain at RED_LINE_C-500
@Test void starvedMatterCutsOutputButNotAntimatter()      // matter=0 while RUNNING: status MATTER_STARVED, grossMw falls toward 0, coreCharge stops falling
@Test void underfeedCapsOutput()                          // Settings(2.5, 1.0, 12): grossMw settles <= 1.0 + 0.05
@Test void reserveCoversAGridFailure()                    // CLIPPED (shield on grid), powered=false: status RESERVE_IN_USE, shieldPct holds, reserveMJ falls by shieldDraw*1 s each second
@Test void reserveDoesNotRechargeFromGridWhileBacking()   // the same run with powered toggling: reserveMJ never rises in CLIPPED
@Test void shieldFailureWithChargeDetonates()             // reserve 0, powered=false, CLIPPED: the shield reaches 0, then detonate == true and state DETONATED
@Test void predictionsCountDown()                         // draining shield: shieldEtaS finite and falling ~1/s; stable: shieldEtaS == +Infinity;
                                                          // reserveEtaS == reserveMJ / deficitMW; antimatterEtaS == coreCharge / use-per-second
@Test void alarmTiers()                                   // CAUTION when any ETA < 600 or MATTER_STARVED or RESERVE_IN_USE; WARNING when any ETA < 180 or coreC >= RED_LINE_C;
                                                          // CRITICAL when shieldPct < 15 or any ETA < 60
@Test void cascadeReadout()                               // STABLE below the red line with the shield not falling; STRAINED at the red line or with the shield falling;
                                                          // RUNAWAY at CRITICAL_C or shieldPct < 15
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew test --tests 'com.frozendawn.power.IcarusPhysicsTest'`
Expected: the new tests fail.

- [ ] **Step 3: Implement the cascade, shield and reserve in `IcarusPhysics.step`**

Gross output ramps toward the target at 0.05 MW/s (RAMP reaches 1.0 MW in 20 s).

The core temperature approaches its equilibrium with a time constant of about 30 s.

The shield is charged from gross output while burning. Otherwise any deficit goes into `requestKw`. If unpaid, the reserve covers it. If the reserve is empty, the shield gets nothing.

Track charge use as `long` micro-capsules with a `double` remainder carried between seconds.

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew test --tests 'com.frozendawn.power.IcarusPhysicsTest'`
Expected: PASS. Tune the constants, not the assertions.

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(icarus): coupled cascade, shield, reserve and predictive warnings"
```

### Task 3: CLIP, Cole Protocol and persistence

**Files:**
- Modify: `power/IcarusPhysics.java`
- Test: `src/test/java/com/frozendawn/power/IcarusPhysicsTest.java`

**Interfaces:**
- Produces: `public static final long CLIP_LOSS = CAPSULE / 10`.
- Restart durations after a CLIP (builder's call, totalling the spec's 40 s): INJECTION 10, PRIMING 5, IGNITION 5, RAMP 20.
- Cole Protocol phases: COLE_RAMP_DOWN 60, COLE_COOL 60, COLE_RECOVER 40, COLE_DISCHARGE 20.

- [ ] **Step 1: Write the failing tests**

```java
@Test void clipStopsOutputWithinSeconds()           // clip() while RUNNING: grossMw == 0 within 5 steps, state CLIPPED, coreCharge fell by exactly CLIP_LOSS
@Test void clipKeepsTheShieldOnGridPower()          // CLIPPED: requestKw ~= shieldDrawMw*1000, shieldPct steady while powered
@Test void clipWithLessThanTheLossEndsSafely()      // coreCharge = CLIP_LOSS/2: after clip, coreCharge 0, then COLE_DISCHARGE, then COLD with status SAFE_TO_DISMANTLE
@Test void restartAfterClipTakesFortySeconds()      // start() from CLIPPED: INJECTION, and RUNNING on the 41st step
@Test void restartAfterClipInjectsNewDockCapsules()  // dockCharge = 3*CAPSULE at restart: coreCharge rises by min(3*CAPSULE, room)
@Test void coleProtocolTakesThreeMinutesAndRecoversEverything()
    // cole() while RUNNING with dockEmpties >= ceil(charge/CAPSULE): COLD with SAFE_TO_DISMANTLE on step 181; the recovered field over the run equals the charge at COLE_RECOVER; coreCharge 0
@Test void coleHoldsWithoutCapsuleSpace()           // dockEmpties = 0: COLE_RECOVER, HOLD_CAPSULE_SPACE; proceeds once empties are given
@Test void coleFromClippedSkipsTheRampDown()        // CLIPPED -> cole(): COLE_COOL next
@Test void antimatterIsConserved()                  // across start, run, clip, restart and cole: total injected == used + clip losses + recovered + remaining, exactly
@Test void saveLoadRoundTripMatchesUninterruptedRun()  // at RESERVE_CHARGE, RUNNING and COLE_COOL: copy via save/load, step both 300 s with the same inputs; readouts equal
@Test void firstSecondAfterLoadNeverFails()         // load a RUNNING save, step once with powered=false: no detonate, shieldPct within 1 of the saved value
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew test --tests 'com.frozendawn.power.IcarusPhysicsTest'`
Expected: the new tests fail.

- [ ] **Step 3: Implement `clip()`, `cole()`, the restart path and full `save`/`load` coverage**

`clip()` is valid from PRIMING onward and in RAMP and RUNNING. Before injection, `clip()` and `cole()` both abort to COLD. `cole()` is valid in any charged state except DETONATED.

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew test --tests 'com.frozendawn.power.IcarusPhysicsTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(icarus): CLIP, Cole Protocol and save-safe state"
```

### Task 4: Blast math

**Files:**
- Create: `power/IcarusBlast.java`
- Test: `src/test/java/com/frozendawn/power/IcarusBlastTest.java`

**Interfaces:**
- Produces:

```java
public final class IcarusBlast {
    public static double radius(long coreCharge);                         // 400 * cbrt(charge / (64 * CAPSULE))
    public static double edge(double radius, long seed, double angle);   // ragged rim, in [0.9r, r]
    public static int depth(double distance, double radius, long seed, int x, int z);  // blocks below the reference; 0 beyond the edge
    public static int rimDepth(long seed, int x, int z);                 // 4..7
    public static double meltRadius(double radius);                      // 1.5 * radius
    public static double soundDelaySeconds(double distance);             // distance / 343
    public static float damage(double distance, double radius);          // Float.MAX_VALUE inside; 40 * (1 - (d - r) / r) to 2r; 0 beyond
    public static double knockback(double distance, double radius);      // 0 inside, 4 at the edge, falling linearly to 0 at 2r
    public static boolean witness(double distance, double radius);       // r < d <= 3r
}
```

- [ ] **Step 1: Write the failing tests**

```java
@Test void radiusScalesByCubeRoot()     // radius(64*CAPSULE) == 400 ± 1e-9; radius(2*CAPSULE) == 125.99 ± 0.01; radius(0) == 0; radius(512) == 8 ± 1e-6
@Test void craterIsDeepAtTheCentreAndRaggedAtTheRim()  // depth(0, 400, s, 0, 0) == 80; depth at 0.85r >= 4; for all angles edge in [360, 400];
                                                        // depth beyond edge == 0; the same seed gives the same depth
@Test void meltRingIsOneAndAHalfRadii() // meltRadius(200) == 300
@Test void boomTravelsAtTheSpeedOfSound()  // soundDelaySeconds(686) == 2.0
@Test void damageFallsOffOutsideTheRadius()  // inside: MAX_VALUE; d = 1.5r: 20; d >= 2r: 0
@Test void witnessesStandOutsideTheRadius()  // witness(r, r) false; witness(2r, r) true; witness(3r + 1, r) false
```

- [ ] **Step 2: Run them to see them fail.** `./gradlew test --tests 'com.frozendawn.power.IcarusBlastTest'` should fail to compile.
- [ ] **Step 3: Implement `IcarusBlast`.** The depth profile is `max(rimDepth, round(r / 5 * (1 - (d / edge)^2)))` inside the edge. `edge` uses a seeded sum of 3 to 5 low-frequency sines over the angle.
- [ ] **Step 4: Run them to see them pass.** The same command should PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(icarus): blast radius and crater profile"` (add both files).

### Task 5: Capsules, foundry, blueprints and the Project Icarus slate

**Files:**
- Create: `item/AntimatterCapsuleItem.java`, `block/AntiparticleFoundryBlock.java`, `block/AntiparticleFoundryBlockEntity.java`, recipes under `src/main/resources/data/frozendawn/recipe/fabricator/` (`halsey_core.json`, `icarus_stabilizer.json`, `icarus_injector.json`, `antiparticle_foundry.json`), and `recipe/empty_antimatter_capsule.json`
- Modify: `data/FabricatorBlueprints.java` (add `"icarus"` to `SLATES`), `item/DataSlateItem.java` (the `"icarus"` tooltip), `world/HelionSiteBuilder.java` (a rare sealed capsule), `init/ModItems`, `ModBlocks`, `ModBlockEntities`, `ModDataComponents`, the creative tab and `lang/en_us.json`
- Test: `src/gameTest/java/com/frozendawn/power/IcarusGameTest.java`

**Interfaces:**
- Produces:
  - `ModDataComponents.ANTIMATTER` (`Long`, micro-capsules);
  - `ModItems.ANTIMATTER_CAPSULE` (sealed; stacks to 16; defaults to `CAPSULE`) and `ModItems.EMPTY_ANTIMATTER_CAPSULE`;
  - `AntimatterCapsuleItem.charge(ItemStack) -> long` and `AntimatterCapsuleItem.sealed(long charge) -> ItemStack`;
  - `AntiparticleFoundryBlockEntity implements PoweredMachine` with `DEMAND_WATTS = 400_000` and `SECONDS_PER_CAPSULE = 3600`, plus `void setProgressForTest(int seconds)`.
- Starting recipes (builder's call). The Fabricator ones all use `"blueprint": "icarus"`:
  - Halsey Core: 8 magnet_coil, 4 lithium_blanket, 4 helium_canister, 2 isotope_column; 60000 W, 600 s.
  - Stabilizer: 4 magnet_coil, 2 high_temperature_radiator, 2 refined_acheronite; 40000 W, 180 s.
  - Injector: 2 first_wall, 2 magnet_coil, 4 refined_acheronite; 30000 W, 120 s.
  - Antiparticle Foundry: 4 magnet_coil, 2 isotope_column, 2 helium_canister, 4 refined_acheronite; 60000 W, 300 s.
  - Empty capsule: an ordinary recipe of 4 iron ingots, 1 glass and 1 refined_acheronite, giving 2.

- [ ] **Step 1: Write the failing GameTests**

```java
@GameTest icarusSlateTeachesAllFourBlueprints     // learn("icarus"); each of the four Fabricator recipes is known; DataSlateItem tooltip names "Project Icarus"
@GameTest icarusFoundryMakesACapsuleAfterAPaidHour // a powered foundry with 1 empty capsule, setProgressForTest(3599), one paid second: the output holds 1 sealed capsule with charge CAPSULE; the empty is gone
@GameTest icarusFoundryPausesWithoutPower         // the same, unpowered for 40 ticks: no capsule; progress unchanged
```

- [ ] **Step 2: Run them to see them fail.** `./gradlew gameTestQuick -PfdTests=icarusSlate,icarusFoundry` should FAIL with the missing classes.
- [ ] **Step 3: Implement.** The foundry follows `FabricatorBlockEntity` for the paid tick, menu and item handler. A Helion site desk gets one sealed capsule with a 10% chance per site. `"icarus"` in `SLATES` makes the Helion desks and Uplink drops offer it with no other change.
- [ ] **Step 4: Run them to see them pass.** The same command should PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(icarus): antimatter capsules, Antiparticle Foundry and the Project Icarus slate"`

### Task 6: Blocks, structure validator and ghost guide

**Files:**
- Create: `block/IcarusPartBlock.java`, `block/IcarusStructure.java`, `block/GhostCell.java`, the part blocks from the File Map (block classes now, with block entity stubs where Task 7 needs them), and `network/IcarusBlueprintPayload.java`
- Create: `client/GhostBlueprintRenderer.java` (moved from `client/SfrBlueprintRenderer.java`, which is deleted)
- Modify: `block/SfrMultiblock.java` (use `GhostCell` instead of `Wrong`), `block/SfrControllerBlock.java`, `network/ClientHandlers.java`, `network/ModNetworking.java`, the registrations and `lang`
- Create: `src/gameTest/java/com/frozendawn/power/IcarusScenes.java`
- Test: `src/gameTest/java/com/frozendawn/power/IcarusGameTest.java`

**Interfaces:**
- Produces:

```java
public record GhostCell(BlockPos pos, @Nullable BlockState ghost) {}
public final class IcarusStructure {
    public enum Problem { OK, UNLOADED, FRAME, INJECTOR, STABILIZER, CORE, SUPPORT, ENVELOPE }
    public record Result(Problem problem, @Nullable BlockPos wrong, BlockPos core, BlockPos powerPort, BlockPos matterPort,
                         BlockPos capsuleDock, BlockPos reserveBank, List<BlockPos> parts, List<BlockPos> envelope) { boolean valid(); }
    public static BlockPos core(BlockPos controller, Direction facing);
    public static Result validate(Level l, BlockPos controller, Direction facing, boolean checkEnvelope);
    public static boolean contains(BlockPos controller, Direction facing, BlockPos pos);   // pos is a part cell
    public static List<GhostCell> wrongCells(Level l, BlockPos controller, Direction facing, int limit);  // first incomplete stage only
    public static Map<Block, Integer> remaining(Level l, BlockPos controller, Direction facing);
}
public abstract class IcarusPartBlock extends Block { /* PushReaction.BLOCK; onRemove calls IcarusDetonation.partRemoved (Task 8) */ }
// client
public final class GhostBlueprintRenderer { public static void show(BlockPos controller, Function<Level, List<GhostCell>> cells); public static void hide(BlockPos controller); }
public record IcarusBlueprintPayload(BlockPos controller, boolean show) implements CustomPacketPayload {}
```

- [ ] **Step 1: Write the failing GameTests** (`IcarusScenes.build(h, center)` places the full canonical structure with the controller facing SOUTH and returns the controller; a second variant facing EAST checks rotation)

```java
@GameTest icarusStructureValidatesAndNamesTheWrongCell
    // valid; core == h.absolutePos(center); parts.size() == 1+1+9+4+36+104+5; replacing a frame cell with stone -> FRAME at that cell;
    // dirt inside the envelope with checkEnvelope -> ENVELOPE; the same dirt without checkEnvelope -> valid
@GameTest icarusGhostGuideShowsOneStageAtATime
    // empty site with only a controller: wrongCells are all frame ghosts; after the frame, injector cells; remaining() counts 104 frame first
@GameTest sfrShellValidatesAndNamesTheWrongCell     // the existing test still passes after the GhostCell move (no edit)
```

- [ ] **Step 2: Run them to see them fail.** `./gradlew gameTestQuick -PfdTests=icarusStructure,icarusGhost,sfrShell` should fail to compile.
- [ ] **Step 3: Implement the validator per the Geometry section, the stage order and the renderer generalization**

The SFR's behaviour must not change: its controller calls `GhostBlueprintRenderer.show(controller, l -> SfrMultiblock.wrongCells(l, controller, facing, LIMIT))`.

Icarus blueprint toggling copies `SfrControllerBlock` and `SfrControllerBlockEntity.toggleBlueprint`.

Every part block sets `PushReaction.BLOCK` and is excluded from enderman holdables.

- [ ] **Step 4: Run them to see them pass.** The same command should PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(icarus): open multiblock, staged ghost guide shared with the SFR-1"`

### Task 7: Controller, ports and the running reactor

**Files:**
- Create or complete: `block/IcarusControllerBlockEntity.java`, `IcarusControllerMenu.java`, `IcarusPowerPortBlockEntity.java`, `IcarusMatterPortBlockEntity.java`, `IcarusCapsuleDockBlockEntity.java`, `HalseyCoreBlockEntity.java` (visual state only), `network/IcarusSettingsPayload.java`, `client/IcarusControllerScreen.java`, `init/ModMenuTypes`, and `lang`
- Test: `src/gameTest/java/com/frozendawn/power/IcarusGameTest.java`

**Interfaces:**
- Consumes: Tasks 1 to 3 and 6.
- Produces:

```java
public final class IcarusControllerBlockEntity extends BlockEntity implements MenuProvider {
    public void serverTick();                 // second(l) every 20 ticks
    public void second(ServerLevel l);       // public for GameTests
    public IcarusPhysics physics();
    public IcarusPhysics.Readout readout();
    public void partRemoved(ServerLevel l);  // Task 8 fills it in
    public Set<UUID> crew();                 // players who opened this controller; saved
    public static final int BUTTON_START = 0, BUTTON_COLE = 1, BUTTON_CLIP = 2, BUTTON_BLUEPRINT = 3;
}
public final class IcarusPowerPortBlockEntity extends BlockEntity implements PoweredMachine {
    public void publish(int requestWatts, int supplyWatts); public int supplyWatts(); public boolean paidFullSecond(); }  // a copy of SfrPowerPortBlockEntity
public final class IcarusMatterPortBlockEntity { public static boolean accepts(ItemStack s); public int count(); public void use(int blocks); }
    // accepts: s.getItem() instanceof BlockItem b && !(b.getBlock() instanceof EntityBlock); 9 slots; hopper item handler
public final class IcarusCapsuleDockBlockEntity { public long injectable(long room); public void consume(long charge); public int empties(); public void recover(long charge); }
    // 8 slots; consume empties the same whole capsules injectable() counted, in slot order; recover fills empties with full capsules, then one partial
public record IcarusSettingsPayload(BlockPos controller, int outputKw, int shieldPermille, int feedPerMinute) {}  // the server clamps to the physics ranges and checks stillValid
```

Controller second, in order:
1. Validate. Check the envelope only in COLD and POWER_CHECK. Return on UNLOADED, which is the pause.
2. While live, vaporize envelope intruders (set to air with no drops; `Clearable.tryClear` first) and hurt entities inside the shield (fire damage, 4 per second).
3. Build `Inputs` from the ports and step the physics.
4. Apply the effects: `dock.consume(injected)`, `dock.recover(recovered)`, `matter.use(matterUsed)`, and `port.publish(requestKw*1000, max(0, netMw)*1e6)`.
5. A rising redstone edge on the controller is a CLIP.
6. Sync the Halsey Core visual state (state ordinal, coreC, shieldPct, grossMw, alarm, step progress) through its update tag when it changes.

The screen has:
- state and step with the time left;
- the hold reason;
- the Halsey Cascade readout;
- gross, shield draw and net;
- core temperature against the red line;
- shield, reserve and antimatter;
- the four ETAs;
- the blast radius now and after injection (`IcarusBlast.radius`);
- Start, Cole Protocol, CLIP (sent after the button has been held for 1 s) and three settings sliders;
- remaining materials while invalid.

`ContainerData` follows the SFR's scaled shorts. An infinite ETA is sent as -1.

- [ ] **Step 1: Write the failing GameTests** (scene: `IcarusScenes.build`, plus a charged `PowerStorageBlockEntity` linked to the power port as in `SfrGameTest`; tests drive `second()` directly)

```java
@GameTest icarusColdStartExportsPowerAfterTwoMinutes  // commissioned, 2 capsules in the dock, 64 cobblestone: 121 seconds -> RUNNING; supplyWatts > 0; 2 empties in the dock
@GameTest icarusStartupHoldsWithoutGridPower         // no storage: RESERVE_CHARGE with HOLD_POWER after 40 seconds
@GameTest icarusMatterPortRejectsBlockEntityItems    // accepts(cobblestone) true; accepts(chest) false; accepts(stick) false
@GameTest icarusShieldVaporizesIntrudingBlocks       // RUNNING; place dirt at core+2 and a chest with items at core+(0,3,0): both air after one second; no ItemEntity in the AABB; still RUNNING
@GameTest icarusColeProtocolReturnsAntimatterToTheDock  // RUNNING, then BUTTON_COLE, then 181 seconds: SAFE_TO_DISMANTLE; the dock's sealed charge == the core charge at COLE_RECOVER
@GameTest icarusRedstoneClips                        // RUNNING, then power the controller: CLIPPED on the next second
```

- [ ] **Step 2: Run them to see them fail.** `./gradlew gameTestQuick -PfdTests=icarusCold,icarusStartup,icarusMatter,icarusShield,icarusCole,icarusRedstone` should FAIL.
- [ ] **Step 3: Implement the block entities, menu, screen and payload** per the interfaces above, registering the payload in `ModNetworking`.
- [ ] **Step 4: Run them to see them pass.** The same command should PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(icarus): controller, ports, dock and the running reactor"`

### Task 8: Instant detonation

**Files:**
- Create: `world/IcarusDetonation.java`, `src/main/resources/data/frozendawn/damage_type/icarus_detonation.json`
- Modify: `block/IcarusPartBlock.java`, `block/IcarusControllerBlockEntity.java`, `init/ModDamageTypes`, and `lang` (the death message)
- Test: `src/gameTest/java/com/frozendawn/power/IcarusGameTest.java`

**Interfaces:**
- Consumes: Tasks 4 and 7.
- Produces:

```java
public final class IcarusDetonation {
    /** Called from IcarusPartBlock.onRemove when the block actually changes. Finds the owning controller by scanning block entities in the chunks
     * overlapping the 17x17x8 bound around pos (no static map, so it works straight after a reload) and calls partRemoved. */
    public static void partRemoved(ServerLevel l, BlockPos pos);
    /** Kills and pushes entities, sends IcarusBlastPayload to every player in the level, grants advancements (Task 12), and queues the crater (Task 9). */
    public static void detonate(ServerLevel l, BlockPos core, double radius, Set<UUID> crew);
}
```

`IcarusControllerBlockEntity.partRemoved` and a `readout.detonate()` both do the same thing when `physics.coreCharge() > 0`:
1. Compute `radius = IcarusBlast.radius(charge)`.
2. Call `physics.detonated()` first, so the reactor's own parts never retrigger it.
3. Call `IcarusDetonation.detonate`.

With zero charge, removal only invalidates. The controller block's own `onRemove` counts as a part.

- [ ] **Step 1: Write the failing GameTests** (charge set with `setCoreChargeForTest(512)` gives radius 8, which fits the template)

```java
@GameTest icarusBreakingAFrameBlockWithChargeDetonates   // destroyBlock(frame cell): an IcarusCraterData job exists with radius 8 ± 0.01; coreCharge 0
@GameTest icarusBreakingAnEmptyReactorOnlyInvalidates    // charge 0: destroyBlock(frame cell): no job; the controller still exists
@GameTest icarusExplosionNextToFrameDetonates            // charge 512; level.explode(TNT) beside the ring: a job exists;
                                                          // also: a piston facing a housing cell does not move it (pistonPushReaction BLOCK)
@GameTest icarusDetonationDoesNotRetriggerItself          // charge 512: break one cell, then break a second part: still exactly one job
@GameTest icarusCraterChainsIntoASecondReactor            // two charged reactors inside one radius: carving a part of the second queues its own job (two jobs total)
@GameTest icarusShieldFailureDetonates                    // CLIPPED, reserve 0, unpowered: the controller queues a job within the shield's run-out
```

- [ ] **Step 2: Run them to see them fail.** `./gradlew gameTestQuick -PfdTests=icarusBreaking,icarusExplosion,icarusDetonation,icarusShieldFailure,icarusCraterChains` should FAIL.
- [ ] **Step 3: Implement it.** Entities within `radius` take `IcarusBlast.damage` from the `icarus_detonation` source. Item and experience entities are discarded. Creative and spectator players are exempt. Entities out to `2r` take the falloff damage and `IcarusBlast.knockback`.
- [ ] **Step 4: Run them to see them pass.** The same command should PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(icarus): instant detonation on part loss or shield failure"`

### Task 9: Persistent budgeted crater

**Files:**
- Create: `world/IcarusCraterData.java`, `world/IcarusCrater.java`
- Modify: `config/FrozenDawnConfig.java` (server `icarusTerrainDestruction`, default true)
- Test: `src/gameTest/java/com/frozendawn/power/IcarusCraterGameTest.java`

**Interfaces:**
- Consumes: Task 4.
- Produces:

```java
public final class IcarusCraterData extends SavedData {
    public record Job(BlockPos center, double radius, long seed, LongSet pending) {}  // pending: chunk longs within meltRadius
    public static IcarusCraterData get(ServerLevel l);
    public void queue(BlockPos center, double radius, long seed);
    public List<Job> jobs();
}
public final class IcarusCrater {   // @EventBusSubscriber, LevelTickEvent.Post on the server
    public static final long BUDGET_NANOS = 10_000_000L;
    public static int processChunk(ServerLevel l, Job job, ChunkPos c);   // returns blocks changed; public for tests
}
```

**Algorithm (the builder may optimize after measuring):**
- Each tick, take pending chunks nearest the centre first, but only those with `l.hasChunk(x, z)`. Stop when the budget runs out.
- For each column in a crater chunk, inside `edge(angle)`:
  - Set `floor = min(center.y - depth(d), surfaceY - rimDepth)`, where `surfaceY` comes from `Heightmap.Types.WORLD_SURFACE`.
  - Remove every non-air block from `floor + 1` up to the column top, except bedrock and `minBuildHeight`.
  - Call `Clearable.tryClear` on block entities first, then set the block with flags `Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS`.
  - Give the floor surface magma at a 15% chance (the hot floor).
- For columns in the melt ring out to `meltRadius`, change the top 8 blocks below the surface: snow, snow blocks and powder snow become air; ice, packed ice, blue ice and frosted ice become water.
- When the config is false, `detonate` skips `queue`.
- Remove a job when `pending` is empty.

- [ ] **Step 1: Write the failing GameTests**

```java
@GameTest icarusCraterCarvesBoundedSmallRadiusAndKeepsBedrock  // radius 8 over stone, obsidian, a chest with items and a bedrock floor: after the job ends,
                                                                // all cells inside the edge above the floor are air; bedrock remains; no ItemEntity; magma present on the floor
@GameTest icarusCraterRespectsTheBudget                        // BUDGET_NANOS forced to 0 in a test hook: at most one chunk per tick
@GameTest icarusCraterResumesFromSavedData                     // process one chunk, then save() into a new IcarusCraterData via load(): pending == the remaining chunks
@GameTest icarusCraterSkipsUnloadedChunksUntilLoaded           // a job whose pending set includes an unloaded chunk: it stays pending; the job is not removed
@GameTest icarusMeltRingThawsSnowAndIce                        // snow and ice between r and 1.5r: air and water
```

- [ ] **Step 2: Run them to see them fail.** `./gradlew gameTestQuick -PfdTests=icarusCrater,icarusMelt` should FAIL.
- [ ] **Step 3: Implement `IcarusCraterData` and `IcarusCrater`.**
- [ ] **Step 4: Run them to see them pass.** The same command should PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(icarus): persistent budgeted crater and melt ring"`

### Task 10: Blast effects for air and vacuum

**Files:**
- Create: `network/IcarusBlastPayload.java`, `client/IcarusBlastEffects.java`
- Modify: `world/IcarusDetonation.java`, `network/ModNetworking`, `network/ClientHandlers`, `config/FrozenDawnConfig.java` (client `icarusReducedFlash`, default false)

**Interfaces:**
- Produces:
  - `record IcarusBlastPayload(Vec3 center, double radius, boolean vacuum, double distance)`. The server sets `vacuum = !RoomAtmosphere.hasAir(level, player.blockPosition())` for each player.
  - The radiation burst: players within `2r` who are not immune get a one-off dose added to their `RADIATION` attachment, scaled `(1 - d / 2r)`. No fallout blocks are placed. Players wearing a sealed suit also hear `ui.suit.icarus_radiation`, the suit line from Task 11.

Client behaviour, all driven by the payload:
- **Flash:** a full-screen white overlay at a strength that falls with distance, lasting about 1.5 s. With reduced flash, it is capped at 35% opacity with no strobe. Below the overlay, a bright point in the blast direction lingers for about 5 s.
- **Air:** after `IcarusBlast.soundDelaySeconds(distance)`, play `blocks.icarus_boom`, then `blocks.icarus_rumble`. Camera shake scales with `knockback`. Rising fireball and smoke column particles are added within 2r of view distance.
- **Vacuum:** no boom. `blocks.icarus_vacuum_thud` plus `blocks.icarus_rumble` at a low pitch. Suit vibration (a short camera micro-shake, plus controller rumble if available). An expanding glowing plasma particle shell instead of the fireball.

- [ ] **Step 1: A pure test for the client timing helper.** Add `IcarusBlast.flashStrength(double distance, double radius, boolean reduced)`, a pure function, to `IcarusBlastTest`: 1.0 at d = 0, falling to at least 0.15 at 6r; never above 0.35 when reduced.
- [ ] **Step 2: Run it to see it fail.** `./gradlew test --tests 'com.frozendawn.power.IcarusBlastTest'`
- [ ] **Step 3: Implement the payload, the server send, the radiation burst and `IcarusBlastEffects`.**
- [ ] **Step 4: Run it to see it pass**, then `./gradlew compileJava`. The visual and audio result stays unverified until the owner's pass in Task 13.
- [ ] **Step 5: Commit.** `git commit -m "feat(icarus): flash, delayed boom, vacuum thud and radiation burst"`

### Task 11: Visuals, textures, sound and the suit voice

**Files:**
- Create: `client/HalseyCoreRenderer.java`, `client/IcarusStabilizerRenderer.java`, `client/IcarusInjectorRenderer.java`, `client/IcarusLoopSound.java`, `power/IcarusAudioCues.java`, `tools/generate_icarus_textures.py`, `tools/generate_icarus_sounds.sh`, and the assets
- Modify: `client/SoundMuffler.java` (the exception), `init/ModSounds`, `sounds.json`, `AUDIO_NOTICE.md`, and the client renderer registration
- Test: `src/test/java/com/frozendawn/power/IcarusAudioCuesTest.java`, `src/test/java/com/frozendawn/power/IcarusAssetsTest.java`

**Interfaces:**
- Produces:
  - `IcarusAudioCues.Cue { FRAME_HUM_ON, STABILIZER_SPINUP, SHIELD_FORM, CAPSULE_DOCK, INJECTOR_FEED, IGNITION, CLIP_SLAM, COLE_COMPLETE, ALARM_CAUTION, ALARM_WARNING, ALARM_CRITICAL }`;
  - `static EnumSet<Cue> cues(@Nullable Readout before, Readout after)`.
- Sounds (all `blocks.icarus_*`):
  - one-shots: `frame_hum`, `stabilizer_spinup`, `stabilizer_whine`, `shield_hum`, `core_roar`, `injector_feed`, `capsule_dock`, `ignition`, `clip_slam`, `cole_complete`, `alarm_caution`, `alarm_warning`, `alarm_critical`, `boom`, `rumble`, `vacuum_thud`;
  - loops: `core_roar` (pitch 0.6 to 1.6 with `coreC`), `shield_hum` (pitch with strain), `stabilizer_whine`, `frame_hum`.
- Suit lines, made with the suit TTS tool and registered as `ui.suit.icarus_*` (copy the owner may revise):

  | Line | Text |
  |---|---|
  | caution | "Icarus caution. Check the reactor." |
  | warning | "Icarus warning. Shield margin falling." |
  | critical | "Icarus critical. Shield failure imminent." |
  | radiation | "Radiation spike detected." |

  The controller relays a line when the alarm tier rises, and repeats the critical line every 60 s. It goes to online crew in the same level who are wearing a sealed suit (`SuitIntegrityHandler.isWearingSealedSuit`). Confirm that `ui.suit.` lines already pass `SoundMuffler` in vacuum, as the existing suit voice does; if not, add them beside the Icarus exception.

Visual rules from the spec:
- **Core:** about 5 blocks across. It is dark when cold, orange and churning while running, deep red with faster churn when hot, and red with a white flicker when critical. Churn and pulse rise with `grossMw` and `coreC`.
- **Shield:** about 9 blocks across. It is off or faint when cold, cyan while running and amber when hot. Critical adds cracks, arcs from core to shield and a structure shake (a client camera nudge within 24 blocks).
- **Startup:** stabilizers light one at a time during STABILIZER_SPINUP, with beams meeting at the shield. A violet speck appears at the centre during INJECTION. The matter stream rises from the injector during PRIMING. The ignition flash is followed by an orange bloom.
- **CLIP:** the stream cuts and the core dims quickly.
- **Cole Protocol:** a slow dim, then the beams retract one by one.
- **Animated parts:** stabilizer rings park when cold and spin when active.
- **Renderers** override `getRenderBoundingBox` to cover the shield. Reduced flash softens the critical flicker.

- [ ] **Step 1: Write the failing tests**

```java
@Test void cuesFollowTransitions()          // COLD->POWER_CHECK: FRAME_HUM_ON; ->STABILIZER_SPINUP: STABILIZER_SPINUP; INJECTION's last second: CAPSULE_DOCK;
                                            // ->IGNITION: IGNITION; ->CLIPPED: CLIP_SLAM; COLE_DISCHARGE->COLD: COLE_COMPLETE; alarm rise NONE->WARNING: ALARM_WARNING only
@Test void everyIcarusBlockHasUniqueTextures()  // read every blockstate and model for the Icarus blocks: each block's texture set is disjoint from every other block's and from all non-Icarus models
@Test void everyIcarusSoundHasAnAsset()     // follow SfrSoundAssetsTest: each blocks.icarus_* and ui.suit.icarus_* event in sounds.json has an existing .ogg
@Test void icarusSoundsPassTheVacuumMuffler()  // a SoundMuffler helper isVacuumExempt(ResourceLocation) returns true for frozendawn:blocks.icarus_boom (extract the predicate if needed)
```

- [ ] **Step 2: Run them to see them fail.** `./gradlew test --tests 'com.frozendawn.power.Icarus*'`
- [ ] **Step 3: Implement the renderers, loops, cues, muffler exception, texture and sound generators, suit lines and `AUDIO_NOTICE.md` entries.** Follow `generate_sfr_textures.py` and `generate_sfr_sounds.sh`. Emissive faces use animated `.mcmeta` textures.
- [ ] **Step 4: Run them to see them pass**, then `./gradlew compileJava`. Motion and sound quality are owner-verified in Task 13.
- [ ] **Step 5: Commit.** `git commit -m "feat(icarus): living core, stabilizer and injector visuals with vacuum-audible sound"`

### Task 12: Advancements

**Files:**
- Create: `power/IcarusAdvancements.java`, `src/main/resources/data/frozendawn/advancement/{e_equals_mc2,too_close_to_the_sun,clipped_wings,wake_me_when_you_need_me,continuity_beyond,the_fall_of_icarus,dont_look_up}.json`
- Modify: `IcarusControllerBlockEntity`, `IcarusDetonation`, and `lang`
- Test: `src/test/java/com/frozendawn/power/IcarusAdvancementsTest.java`

**Interfaces:**
- Produces: `static List<String> earned(@Nullable Readout before, Readout after, boolean restartedAfterCole)`, plus `static boolean continuity(ServerLevel l, BlockPos port)`. `continuity` looks for a Geothermal core, a CFU-7 Hearthstone `ReactorControllerBlockEntity` and an SFR-1 `SfrControllerBlockEntity`, all producing on one component, following `SfrAdvancements.mixedNetwork`. Every advancement is hidden and uses the code-granted pattern of `SfrAdvancements`.
- Grant rules:

  | Advancement | Granted to | When |
  |---|---|---|
  | E = mc² | crew within 64 blocks | first IGNITION |
  | Too Close to the Sun | crew within 64 blocks | `grossMw >= 2.5` |
  | Clipped Wings | crew within 64 blocks | entering CLIPPED |
  | Wake Me When You Need Me | crew within 64 blocks | IGNITION after a completed Cole Protocol |
  | Continuity Beyond | crew within 64 blocks | `continuity` is true while RUNNING |
  | The Fall of Icarus | crew online | detonation |
  | Don't Look Up | any surviving player in the level | `IcarusBlast.witness` is true at detonation |

- [ ] **Step 1: Write the failing tests.** `ignitionEarnsEmc2`, `fullOutputEarnsTooClose`, `clipEarnsClippedWings` and `coleRestartEarnsWakeMe`, using hand-built readouts.
- [ ] **Step 2: Run them to see them fail.** `./gradlew test --tests 'com.frozendawn.power.IcarusAdvancementsTest'`
- [ ] **Step 3: Implement the class, the JSON files, the grants and the lang entries.**
- [ ] **Step 4: Run them to see them pass.**
- [ ] **Step 5: Commit.** `git commit -m "feat(icarus): hidden Icarus advancements"`

### Task 13: Open check, playtest world and the gate

**Files:**
- Create: `tools/prepare_icarus_playtest.py`
- Modify: `config/architect-required-tests.txt` (append every new GameTest name, lowercased, with ` xml`, matching the existing lines), and `docs/icarus-reactor-design.md` (append a "Status" section)

- [ ] **Step 1: Open check.** List the world-generated blocks the story needs (`BlastPitPlacement`, the satellite, Helion and reactor sites, and any block with a destroy speed of -1 placed by worldgen). Report to the owner any that could not be rebuilt after a crater, and stop there. Do not add protection.
- [ ] **Step 2: Playtest world.** Modelled on `tools/prepare_sfr_playtest.py`, it creates a fresh disposable save. It has:
  - the world-local advancement filter after NeoForge's `mod_data`;
  - a preselected, locked difficulty;
  - a pre-built Icarus reactor with a charged battery bank;
  - a capsule stack, matter in a chest and an EVA suit;
  - a vacuum-phase area beside an air-phase area for the blast check.

  Verify the loader-selected pack order from `latest.log`. Preserve existing saves.
- [ ] **Step 3: Full-radius performance.** In the disposable world, through `runClientLab` and `tools/lab_client.py` per `docs/lab-command-bridge.md`, detonate a 64-capsule load. Record the ticks to finish, the worst tick time and the memory in the Status section. If the worst tick goes over 50 ms, lower `BUDGET_NANOS` or switch to section writes before handing back.
- [ ] **Step 4: Run the gate.** Run `./gradlew architectVerify` in the background. Expected: all required tests pass. Report any failure with its output, and do not retry destructive steps.
- [ ] **Step 5: Commit.** `git commit -m "test(icarus): required GameTests, playtest world and status"`
- [ ] **Step 6: Hand off.** Launch with `./gradlew runClient --console=plain` (or `runClientLab`) on the disposable save. The owner checks every state's animation, vacuum audibility, the startup and shutdown readability, the ghost guide, and a full detonation. Record their unprompted description before any diagnostic explanation. Opening a PR comes after their pass; merging needs their explicit approval.

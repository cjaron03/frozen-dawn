# SMS-1 Hyperion Silo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the shipped cube Hyperion on `feat/hyperion-storage` as the approved 3x3 silo, 4 to 8 tall, with the stacked-bar window, frosted pipes, thin floating rings, cold breath and scorch, keeping all mechanics.

**Architecture:**
- `HyperionMultiblock` is rewritten for the silo; `HyperionControllerBlockEntity` keeps its store, physics, quench and network code and gains silo facts in its synced `Snapshot`.
- A pure `power/HyperionGauge` maps synced state to what every visual shows (the spec's state table), and a pure `power/HyperionSiloLayout` holds ring, pipe and render-box geometry. Both are unit-tested; the client only draws what they say.
- `client/renderer/HyperionSiloRenderer` replaces the puck renderer; `client/HyperionSiloEffects` spawns the client particles from the existing client ticker.

**Tech Stack:** Java 21, NeoForge 1.21.1, JUnit 5 (`src/test`), NeoForge GameTests (`src/gameTest`), JSON resources, Python/PIL textures, ffmpeg + oggenc sounds.

**Spec:** `docs/hyperion-silo-design.md` (commit `ff70c290`), which replaces the Structure and Look sections of `docs/hyperion-storage-design.md`. Read both.

## Global Constraints

- Everything in the original plan's constraints still holds: creative-only, Battery Banks untouched, quench is loud and visual only, `long` joules, no forced chunk loads, no unbounded per-tick scans, the package layout, dense house style, no emojis, Conventional Commits (`feat(hyperion):`, `test(hyperion):`) with trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Spec values, verbatim: footprint 3x3; height 4 to 8; exactly 1 Controller at the bottom of the front-centre column, its facing defines the front; the front-centre column above it is cells; exactly 1 Quench Vent at top centre, jetting up; at least 1 Cryocooler in the bottom row, not the Controller's position; at least 1 Port, never in the front-centre column or the top-centre position; the centre column below the vent is cells; no Frame blocks; about 20 MJ per cell; one bar per block of height including the Controller's; one ring per two blocks of height.
- Cooler targets, verbatim: one Cryocooler holds a 3x3x4 in a 20C room; a 3x3x8 in a 20C room needs two; outdoors below 0C one holds any height.
- Textures are 16 px per block from `tools/generate_hyperion_textures.py`; geometry is blocky boxes, never smooth or rounded hulls. Bars, ring edges and lamps render at full brightness.
- Pipes and rings are rendered only: no collision, not blocks.
- Unit tests: `./gradlew test --tests 'com.frozendawn.power.Hyperion*'`. GameTests stay named `hyperion...`; iterate with `./gradlew gameTestQuick -PfdTests=hyperion`; the gate is `./gradlew architectVerify`.
- Never touch the main checkout `/Volumes/SSD/Work/frozen-dawn`; work only in `/Volumes/SSD/Work/frozen-dawn-hyperion`.

## Review Focus

1. **Rings clipping the silo's corners.** A round ring around a square silo must clear the corner edges (24 px from the axis on the diagonal is 33.9 px). Pinned in Task 7 (`ringsClearTheCorners`).
2. **A Controller in the wrong place:** facing up or down, facing into the silo, on the top layer, or at a front corner. Each must fail as CONTROLLERS naming that position. Pinned in Task 2 (`hyperionSiloNamesTheWrongPart`).
3. **The silo vanishing when the Controller block is off-screen**, looking up at the top of an 8-tall or at its rings. The render box must cover rings, pipes and vent. Pinned in Task 6 (`renderBoxCoversRingsPipesAndVent`).
4. **A silo with no ground under its pipes** (built on a ledge or floating). Pipes drop at most 3 blocks and never below a missing floor forever. Pinned in Task 8 (`pipeDropIsClamped`).
5. **Reload after a quench:** the client must still see the scorch and resting rings, not a fresh silo. Pinned in Task 4 (`hyperionSnapshotRoundTripsSiloFacts`).

**Known limitation, not tested headlessly:** how any of it looks. The owner pass in Task 10 is the only judge.

---

### Task 1: Retune the cooler for silo sizes

**Files:**
- Modify: `src/main/java/com/frozendawn/power/HyperionPhysics.java` (`COOLER_CAPACITY`)
- Test: `src/test/java/com/frozendawn/power/HyperionPhysicsTest.java` (`coolerSizingMatchesTheSpec`)

**Interfaces:** Produces `HyperionPhysics.COOLER_CAPACITY = 12_500`. A 3x3xH silo with p Ports and c Cryocoolers has `9H - 2 - p - c` cells.

- [ ] **Step 1: Rewrite the failing test**

```java
@Test void coolerSizingMatchesTheSpec() {
    assertTrue(heatLoad(32, 20, 0) <= COOLER_CAPACITY, "One cooler holds a 3x3x4 in a warm room");
    assertTrue(heatLoad(67, 20, 0) > COOLER_CAPACITY && heatLoad(67, 20, 0) <= 2 * COOLER_CAPACITY, "A 3x3x8 in a warm room needs two");
    assertTrue(heatLoad(68, 0, 0) <= COOLER_CAPACITY, "One cooler holds any height outdoors below 0C");
    assertTrue(heatLoad(32, 20, 1) > 4 * COOLER_CAPACITY, "A running heat source overwhelms a normal cooler count");
}
```

- [ ] **Step 2:** Run `./gradlew test --tests 'com.frozendawn.power.HyperionPhysicsTest'`. Expected: FAIL on "A 3x3x8 in a warm room needs two" (15,000 holds 13,400).
- [ ] **Step 3:** Set `COOLER_CAPACITY = 12_500`. Bound: 12,240 (68 cells at 0C) to 13,400 (67 at 20C).
- [ ] **Step 4:** Run the same command. Expected: PASS, every test in the class.
- [ ] **Step 5:** Commit `feat(hyperion): size the cryocooler for silos`.

### Task 2: The silo validator

**Files:**
- Modify:
  - `block/HyperionMultiblock.java` (rewrite `Problem`, `Result`, `discover`, `validate`)
  - `src/gameTest/.../HyperionScenes.java` (replace `build` with `silo`)
  - `src/gameTest/.../HyperionGameTest.java` (migrate every test to silos)
  - `world/HyperionLabPlacement.java`, `tools/prepare_hyperion_playtest.py` (silos instead of cubes)
  - `lang/en_us.json` (problem keys)
- Test: `HyperionGameTest.java`, `HyperionLangTest.java`

**Interfaces:**
- **Produces:**
  - `enum Problem { NONE, UNLOADED, FOOTPRINT, TOO_SHORT, TOO_TALL, CONTROLLERS, GAUGE, INTERIOR, WALL, PORTS, COOLERS, VENTS }`
  - `record Result(Problem problem, BlockPos wrong, BlockPos min, BlockPos max, Direction front, int cells, List<BlockPos> ports, List<BlockPos> coolers, List<BlockPos> vents)`; `vents` has one entry when valid; `front` is null unless valid; `int height()` returns `max.y - min.y + 1`.
  - `WIDTH = 3, MIN_HEIGHT = 4, MAX_HEIGHT = 8, MAX_PARTS = 72`.
  - `HyperionScenes.silo(GameTestHelper h, BlockPos min, int height, int ports, int coolers) -> Built`. The front is NORTH (low z). Placement order:
    - the Controller at `(1, 0, 0)` facing NORTH
    - the Vent at `(1, H-1, 1)` facing UP
    - Ports up the back-centre column `(1, y, 2)` from y = 0
    - Cryocoolers in the bottom row: `(0,0,1)` WEST, `(2,0,1)` EAST, `(1,0,2)` SOUTH, then the back corners
    - every other position is a cell
  - `Built` drops nothing.
- **Validation rules, in this order:**
  1. **Size:** x or z extent other than 3 fails FOOTPRINT. Height below 4 fails TOO_SHORT; above 8 fails TOO_TALL. A flood fill that grows past 3 in x or z returns FOOTPRINT, and past 8 in y returns TOO_TALL.
  2. **Loading:** any unloaded position fails UNLOADED.
  3. **Controller:** count Controllers. Unless there is exactly one, at the bottom layer, at the centre of a side, with a horizontal FACING pointing out of the box, fail CONTROLLERS with `wrong` set to the first offending one (null for none).
  4. **Each position, in x, y, z order:**
     - The centre column below the top must be a cell, else INTERIOR.
     - The top centre must be a Quench Vent facing UP, else VENTS.
     - The front-centre column above the Controller must be a cell, else GAUGE.
     - Any other position may be a cell or a Port. A Cryocooler is allowed only in the bottom row with FACING outward (horizontal). A Cryocooler elsewhere, or facing wrong, fails COOLERS. A Vent elsewhere fails VENTS. Anything else fails WALL. `wrong` is the position in every case.
  5. **Counts:** no Ports fails PORTS; no Cryocoolers fails COOLERS.

- [ ] **Step 1: Write the failing GameTests.**
  - `hyperionShellValidatesSizesAndCountsCells`, replaced: heights `{4, 32}, {6, 50}, {8, 68}`. The test builds `silo(h, MIN, H, 1, 1)` and asserts valid, the cell count, ports and coolers equal to `Built`, one vent, the box, and `front == Direction.NORTH`.
  - `hyperionSiloNamesTheWrongPart`, replacing `hyperionShellNamesTheWrongCell`. Each case starts from a fresh `silo(h, MIN, 4, 1, 1)`:

    | Change | Expected |
    | --- | --- |
    | stone at `(0, 1, 0)` | WALL at that position |
    | air at the centre `(1, 1, 1)` | INTERIOR |
    | a Port at gauge `(1, 2, 0)` | GAUGE |
    | the vent replaced by a cell | VENTS at `(1, 3, 1)` |
    | the vent facing NORTH | VENTS |
    | a second vent at `(0, 3, 0)` | VENTS at that position |
    | a Cryocooler at `(0, 2, 1)` facing WEST | COOLERS at that position |
    | the only cooler replaced by a cell | COOLERS, wrong null |
    | the only port replaced by a cell | PORTS |
    | the Controller turned to face UP | CONTROLLERS at the Controller |
    | the Controller turned to face SOUTH | CONTROLLERS at the Controller |
    | a second Controller at `(1, 3, 2)` | CONTROLLERS |
    | `silo(h, MIN, 9, 1, 1)` | TOO_TALL |
    | `silo(h, MIN, 3, 1, 1)` | TOO_SHORT |
    | an extra cell column at x = 3 | FOOTPRINT |

  - Migrate every other test: replace `build(h, min, sx, sy, sz, p, c)` with `silo(h, min, H, p, c)`, mapping 3x3x3 to 4, 5x5x5 to 6 and 7x7x7 to 8. Thermal tests that relied on one cooler holding at a mild temperature and losing at 20C now use an 8-tall with one cooler: it holds at 0C or below (68 cells: 12,240) and loses at 20C (13,600).
  - `hyperionLongEnergySurvivesSaveAndMonitor`: one silo can no longer hold 2.1 GJ. Keep the save round trip on one 8-tall with `setStoredForTest(5_000_000_000L)` (the setter does not cap). For the monitor, link two 8-tall silos, each set to its full capacity, to one Power Monitor and assert it reads 2,720,000,000 J of 2,720,000,000, past `Integer.MAX_VALUE`.
  - `hyperionLabPlacesAValidPairAndSfr`: the outdoor battery is a 3x3x6 with 2 Ports and 2 Cryocoolers (48 cells), half charged and COLD. The heated one is a 3x3x4 with 1 Cryocooler (32 cells).
  - `HyperionLangTest` already requires a key per `Problem`; add the new keys.
- [ ] **Step 2:** Run `./gradlew gameTestQuick -PfdTests=hyperion`. Expected: FAIL to compile on `silo` and the new problems.
- [ ] **Step 3: Implement the rules above.**
  - **Lang copy:**
    - footprint: "The silo must be exactly 3 by 3 blocks"
    - too_short: "Too short: the silo must be at least 4 blocks tall"
    - too_tall: "Too tall: the silo must be 8 blocks or fewer"
    - controllers: "Needs exactly 1 Hyperion Controller, at the bottom centre of a side, facing out"
    - gauge: "The column above the Controller must be Superconducting Cells"
    - interior: "The centre column must be Superconducting Cells"
    - wall: "The outside must be Superconducting Cells, Ports or Cryocoolers"
    - coolers: "Needs at least 1 Cryocooler in the bottom row, facing out"
    - vents: "Needs a Quench Vent at top centre, facing up"
    - ports: unchanged
  - Delete the corner, edge, face and too_small keys.
  - **Lab:** the outdoor silo at `origin + (1, 0, -15)` and the heated silo in the existing room, Controllers facing SOUTH. Keep the lava cup against the heated silo's east face centre and update `LAVA` in the playtest script to match.
- [ ] **Step 4:** Run `./gradlew gameTestQuick -PfdTests=hyperion` and `./gradlew test --tests 'com.frozendawn.power.Hyperion*'`. Expected: all PASS.
- [ ] **Step 5:** Commit `feat(hyperion): validate a 3x3 silo`.

### Task 3: Drop the Frame and the old cell looks

**Files:**
- Delete:
  - `block/HyperionFrameBlock.java`
  - `client/renderer/HyperionControllerRenderer.java` and its registration in `ClientEvents`
  - the Frame's blockstate, block and item models, loot table, texture, lang key and pickaxe tag entry
- Modify:
  - `init/ModBlocks.java`, `init/ModItems.java`
  - `block/SuperconductingCellBlock.java`
  - `block/HyperionControllerBlockEntity.java` (`look()`, `show()`, `broken()`)
  - `blockstates/superconducting_cell.json`
- Test: `HyperionGameTest.java`

**Interfaces:**
- **Produces:**
  - `SuperconductingCellBlock.Look { LOOSE, FORMED, SCORCHED }`, default LOOSE, light 0 in every look.
  - The cell has no `animateTick`; vapour moves to the pipes in Task 8.
  - The Controller sets FORMED on forming. It sets SCORCHED while `scarred`, and FORMED once COLD clears `scarred`. Every unform sets LOOSE, including a charged break; a broken silo is no longer a silo.
  - ALERT lamps are unchanged.
- **Ruling (agent's call left open by the spec):** the Frame block is deleted outright; the branch is unmerged and creative-only.

- [ ] **Step 1:** Replace `hyperionCellsShowTheStage` with `hyperionCellsShowFormedAndScorched`, on an 8-tall with one cooler:
  1. Built but before forming, every cell is LOOSE.
  2. Formed at -250C, every cell is FORMED and the alert is off.
  3. Charged at 20C, it chirps and the alert is on.
  4. Run to the quench: every cell is SCORCHED and the alert is off.
  5. Cooled at -250C and charged until COLD: every cell is FORMED again.
  6. Break an uncharged one: every cell is LOOSE.

  Also update `hyperionQuietBreakClearsTheWarningLook` (expect LOOSE) and drop the Frame from `hyperionBlockstatesNameRealProperties`.
- [ ] **Step 2:** Run `./gradlew gameTestQuick -PfdTests=hyperionCells`. Expected: FAIL to compile on `Look.LOOSE`.
- [ ] **Step 3:** Implement. Point the cell blockstate's `look=loose|formed|scorched` at the existing plain, plain and scarred models for now; Task 5 replaces them.
- [ ] **Step 4:** Run `./gradlew gameTestQuick -PfdTests=hyperion`. Expected: all PASS.
- [ ] **Step 5:** Commit `refactor(hyperion): drop the frame and the cube's cell looks`.

### Task 4: Silo facts in the snapshot, and the gauge

**Files:**
- Create: `power/HyperionGauge.java`
- Modify: `block/HyperionControllerBlockEntity.java` (`Snapshot`, `current()`, update tag)
- Test:
  - `src/test/java/com/frozendawn/power/HyperionGaugeTest.java`
  - `HyperionGameTest.java` (`hyperionSnapshotRoundTripsSiloFacts`)

**Interfaces:**
- **Produces:**
  - `Snapshot` gains `int height, boolean scarred, List<BlockPos> coolers` (absolute positions), synced through `getUpdateTag` and `handleUpdateTag`. `height` is 0 when unformed.
  - `HyperionGauge.Inputs(HyperionPhysics.Stage stage, boolean formed, long stored, long capacity, long flowIn, long flowOut, long rateWatts, int height, boolean scarred, long ticksSinceQuench)`. `ticksSinceQuench` is `Long.MAX_VALUE` when it never quenched.
  - `HyperionGauge.of(Inputs) -> State`
  - `record State(int bars, double lit, Colour colour, int breathing, int flickering, boolean ember, int collapseShown, Rings rings, double spin, Motion motion, Pipes pipes, Flow flow, Lamps lamps, Vent vent)`, with `breathing`, `flickering` and `collapseShown` -1 when unused.
  - `enum Colour { OFF, BLUE, AMBER, RED, DEAD }`, `enum Rings { FLOATING, RESTING }`, `enum Motion { CALM, WOBBLE, SHUDDER }`, `enum Pipes { FROSTED, DRIPPING, BARE }`, `enum Flow { NONE, IN, OUT }`, `enum Lamps { OFF, BLUE, AMBER, RED }`, `enum Vent { NONE, BREATH, WISPS, HEAVY_WISPS }`.
- **Rules:**
  - **Bars and lit:**
    - `bars = height`, and `lit = height * stored / capacity` (0 if capacity is 0).
    - `breathing = floor(lit)` when `flowIn > flowOut`, COLD and not full.
    - `flickering = ceil(lit) - 1` when `flowOut > flowIn` and `stored > 0`.
    - `collapseShown = bars - ticksSinceQuench / 3` while `ticksSinceQuench < 3 * bars`, with colour RED.
  - **By stage:**

    | Stage | Colour | Pipes | Lamps | Vent | Motion |
    | --- | --- | --- | --- | --- | --- |
    | COLD | BLUE | FROSTED | BLUE | BREATH if stored > 0 | CALM |
    | CHIRP or WARNING | AMBER | DRIPPING | AMBER | WISPS | WOBBLE |
    | FINAL | RED | DRIPPING | RED | HEAVY_WISPS | SHUDDER |
    | WARM, scarred | DEAD, ember true | BARE | OFF | NONE | |
    | WARM, not scarred | OFF | BARE | OFF | NONE | |
    | Not formed | OFF | | | | RESTING, everything else off |

  - **Rings and flow:**
    - Rings are FLOATING when formed, `stored > 0` and not WARM; otherwise RESTING.
    - `spin = 0.5 + 3.5 * min(1, max(flowIn, flowOut) / rateWatts)` turns per minute. The 0.5 is the idle spin.
    - Flow is IN or OUT by the larger of `flowIn` and `flowOut`, NONE when both are 0.

- [ ] **Step 1: Write `HyperionGaugeTest`, one test per row of the spec's state table.**
  - `coldAndFullShimmers`: 6 tall, full, no flow → 6 bars, lit 6.0, BLUE, FLOATING, FROSTED, BLUE lamps, BREATH, spin 0.5.
  - `chargingBreathesTheNextBar`: 6 tall, half, flowIn 250 kW of 500 → breathing 3, Flow IN, spin 2.25.
  - `dischargingFlickersTheTopBar`: 6 tall at 2.5 bars, flowOut > 0 → flickering 2, Flow OUT.
  - `overheatingIsAmber`: CHIRP → AMBER, DRIPPING, AMBER lamps, WISPS, WOBBLE.
  - `finalSecondsAreRed`: FINAL → RED, SHUDDER, HEAVY_WISPS.
  - `quenchCollapsesTopDown`: 8 tall, ticksSinceQuench 6 → collapseShown 6, RED. At 24 → -1 and DEAD with ember (WARM, scarred, stored 0, RESTING).
  - `freshWarmBuildIsDark`: WARM, not scarred → OFF, BARE, RESTING, Vent NONE.
  - `unformedIsOff`: formed false → OFF, RESTING.
  - `hyperionSnapshotRoundTripsSiloFacts` (GameTest): on a formed 6-tall with two coolers, after a quench, `handleUpdateTag(getUpdateTag())` on a fresh entity yields height 6, scarred true and both cooler positions.
- [ ] **Step 2:** Run `./gradlew test --tests 'com.frozendawn.power.HyperionGaugeTest'`. Expected: FAIL to compile.
- [ ] **Step 3:** Implement `HyperionGauge` (pure, no Minecraft imports) and the snapshot fields.
- [ ] **Step 4:** Run the unit tests and `./gradlew gameTestQuick -PfdTests=hyperion`. Expected: PASS.
- [ ] **Step 5:** Commit `feat(hyperion): map silo state to the gauge`.

### Task 5: Silo textures and models

**Files:**
- Modify:
  - `tools/generate_hyperion_textures.py` (rewrite its outputs)
  - Hyperion blockstates and block models
- Delete: the frosted, frosted_top, stressed, critical, controller_window and controller_inner textures and models, and their `.mcmeta` files.
- Test: `HyperionGameTest.hyperionBlockstatesNameRealProperties`, plus a new unit test `HyperionTextureAssetsTest`.

**Interfaces:**
- **Produces these textures (16x16 unless noted):**
  - `superconducting_cell_loose`: charcoal with a light top-left and dark bottom-right border, like today's plain.
  - `superconducting_cell_formed`: the same charcoal noise with no border, so neighbours join seamlessly.
  - `superconducting_cell_scorched`: formed plus two burnt vertical streaks that tile top to bottom.
  - `hyperion_controller`: formed charcoal with the window frame's bottom (the window opening, 6 px wide and centred, left dark for the renderer) and a small lamp; `hyperion_controller_alert` blinks the lamp.
  - `hyperion_port` and `_alert`: formed charcoal with the copper socket and lamp.
  - `cryocooler`: formed charcoal with a frosted pipe flange.
  - `quench_vent`: a grille top on formed charcoal sides.
  - `hyperion_ring`: dark metal.
  - `hyperion_pipe_frost` and `hyperion_pipe_bare`.
  - `hyperion_window_swirl`: 16x64, animated, tiling vertically. Frametime 2 with interpolation, blue charge with diagonal light streaks.
- **Models:** the cell blockstate maps `look=loose|formed|scorched` to the three cell models. The Controller becomes a full cube (no recess, ambient occlusion on) with its front texture.
- [ ] **Step 1:** Write `HyperionTextureAssetsTest`. Every texture named in the Hyperion block models exists on the classpath, each is 16 px wide, and the cell formed texture's border rows equal its interior noise range (no light or dark edge).
- [ ] **Step 2:** Run it. Expected: FAIL (formed texture missing).
- [ ] **Step 3:** Rewrite the generator and run `python3 tools/generate_hyperion_textures.py` (no `-I`). Update the models and blockstates.
- [ ] **Step 4:** Run the unit test and `./gradlew gameTestQuick -PfdTests=hyperionBlockstates`. Expected: PASS.
- [ ] **Step 5:** Commit `feat(hyperion): silo textures and models`.

### Task 6: The silo renderer: window, bars, trims and lamps

**Files:**
- Create:
  - `power/HyperionSiloLayout.java`
  - `client/renderer/HyperionSiloRenderer.java`
- Modify: `client/ClientEvents.java` (register the renderer)
- Test: `src/test/java/com/frozendawn/power/HyperionSiloLayoutTest.java`

**Interfaces:**
- **Consumes:** `Snapshot` (height, front from the block state), `HyperionGauge.of`.
- **Produces:**
  - `HyperionSiloLayout.renderBox(int cx, int cy, int cz, int frontX, int frontZ, int height) -> double[6]`. The inputs are the Controller position and the front direction's unit step. The box is centred on the silo axis one block behind the Controller, extends 3.5 blocks horizontally each way, and runs from `cy - 4` (pipes) to `cy + height + 2` (vent steam and rings).
  - The renderer overrides `getRenderBoundingBox` with it and `shouldRenderOffScreen` true.
- **Draws:**
  - **The window:** a 6 px wide inset strip on the front face of the gauge column, Controller block included, offset 0.001 out, floor to top.
    - The swirl texture fills it to `lit`.
    - Each block's bar is a 4 px tall emissive strip in the gauge colour.
    - The breathing bar pulses on a 20-tick sine; the flickering bar steps randomly each 3 ticks.
    - During a collapse, the bars above `collapseShown` are hidden.
    - DEAD bars are grey, with the ember a dim red on bar 0.
  - **Trims:** a 1 px dark line along the silo's 12 outer edges, offset 0.002, so the formed cells' seamless faces still read as one silo.
  - **Lamps:** four 2x2 px emissive squares on the bottom row of the front and sides, blinking every 16 ticks for AMBER and every 6 for RED.
- [ ] **Step 1:** Write `HyperionSiloLayoutTest.renderBoxCoversRingsPipesAndVent`. For a Controller at (0, 64, 0) facing north (front step 0, -1) and height 8:
  - the silo axis is x 0.5, z 1.5
  - the box contains the steam above the vent, `(0.5, 73.5, 1.5)`
  - it contains the ring outer edges, 2.375 blocks from the axis at y 70.4, in all four directions
  - it contains a west cooler's pipe end, `(-2, 61, 1.5)`, 3 blocks down
  - it contains the same points rotated for east, south and west fronts
- [ ] **Step 2:** Run `./gradlew test --tests 'com.frozendawn.power.HyperionSiloLayoutTest'`. Expected: FAIL to compile.
- [ ] **Step 3:** Implement the layout and the renderer.
- [ ] **Step 4:** Run the unit test and `./gradlew compileJava`. Expected: PASS. How it looks waits for Task 10.
- [ ] **Step 5:** Commit `feat(hyperion): draw the silo window and trims`.

### Task 7: The rings

**Files:**
- Modify:
  - `power/HyperionSiloLayout.java`
  - `client/renderer/HyperionSiloRenderer.java`
- Test: `HyperionSiloLayoutTest.java`

**Interfaces:**
- **Produces:**
  - `RING_SEGMENTS = 32, RING_INNER_PX = 36, RING_WIDTH_PX = 2, RING_TALL_PX = 2`
  - `ringCount(int height)` returns `height / 2`
  - `ringFloatY(int k, int n, int height)` returns the ring's height above the silo floor in blocks, `(k + 1) * height / (n + 1.0)`
  - `ringRestY(int k)` returns `(2 + 2 * k) / 16.0`, stacked on the base
- **Draws:**
  - Each ring is 32 dark boxes on a circle of inner radius 36 px about the silo axis, each 2 px tall and wide. A 1 px emissive line runs along the inner edge in the gauge colour. RESTING rings have a dim edge.
  - **Motion, per ring with its own phase (k x 1.9 rad):**
    - CALM: a 0.5 px bob on a 3 s sine
    - WOBBLE: tilt up to 4 degrees and a 1 px bob
    - SHUDDER: plus a 1 px horizontal jitter
    - spin follows `spin`
  - **Quench:** a new `lastQuenchTick` drops every ring from its height to `ringRestY` over 8 ticks, easing in, each ring 2 ticks after the one above. Rings lift back to `ringFloatY` over 40 ticks when FLOATING again.
- [ ] **Step 1:** Write `ringsClearTheCorners`: `RING_INNER_PX - 24 * Math.sqrt(2) >= 2`. Write `ringsSpreadUpTheSilo`:
  - `ringCount` is 2 for 4 and 4 for 8
  - every `ringFloatY` lies strictly between 0.5 and `height - 0.5`
  - consecutive rings are at least 1.3 blocks apart
  - `ringRestY` stays below 0.75 for k < 4
- [ ] **Step 2:** Run the layout test. Expected: FAIL to compile.
- [ ] **Step 3:** Implement the geometry and the ring drawing.
- [ ] **Step 4:** Run the unit test and `./gradlew compileJava`. Expected: PASS.
- [ ] **Step 5:** Commit `feat(hyperion): floating rings`.

### Task 8: Pipes, vapour, drips, breath and wisps

**Files:**
- Create: `client/HyperionSiloEffects.java`
- Modify:
  - `power/HyperionSiloLayout.java`
  - `client/renderer/HyperionSiloRenderer.java`
  - `block/HyperionControllerBlock.java` (the client ticker calls `HyperionSiloEffects.tick` alongside `HyperionLoopSound.tick`)
- Test: `HyperionSiloLayoutTest.java`

**Interfaces:**
- **Produces:**
  - `pipeDrop(int solidBelow)`: given the number of air blocks under the pipe's elbow before the first solid block (-1 when none within 3), it returns how far the pipe runs down in blocks. That is the air depth plus `5/16`, ending flush with the solid block's top. When there is no solid block within 3, it runs 1 block and ends in a flange.
- **Draws:**
  - Per cooler in `Snapshot.coolers`, a 6x6 px pipe leaves the outward face at `y + 5/16`. It runs 10 px out, elbows and drops `pipeDrop` blocks.
  - The texture follows `Pipes`: frost for FROSTED and DRIPPING, bare for BARE.
  - Emissive 2 px dashes move along it, in for Flow IN and out for OUT, at 0.5 blocks per second.
- **Effects, client side, from `HyperionGauge.State`:**
  - **FROSTED:** `COLD_VAPOUR` at each elbow, 1 in 4 ticks, sinking.
  - **DRIPPING:** `ParticleTypes.FALLING_WATER` from each elbow, 1 in 10 ticks.
  - **BREATH:** every 80 ticks, plus or minus 20 seeded per Controller, a puff of 3 `COLD_VAPOUR` from the vent top, velocity 0.04 up.
  - **WISPS:** `WHITE_SMOKE` from the vent top, 1 in 4 ticks.
  - **HEAVY_WISPS:** 1 per tick.
  - The steam blast stays in `HyperionVentJets`, and with one vent facing up it now jets straight up.
- [ ] **Step 1:** Write `pipeDropIsClamped`: `pipeDrop(0) == 5/16.0`, `pipeDrop(2) == 2 + 5/16.0`, `pipeDrop(-1) == 1.0`, and `pipeDrop(5) == 1.0` (deeper than 3 counts as none).
- [ ] **Step 2:** Run the layout test. Expected: FAIL.
- [ ] **Step 3:** Implement the layout function, the pipe drawing and the effects. The renderer finds `solidBelow` by checking up to 3 blocks under the elbow with `isSolidRender` on the client level.
- [ ] **Step 4:** Run the unit tests and `./gradlew compileJava`. Expected: PASS.
- [ ] **Step 5:** Commit `feat(hyperion): frosted pipes and vent breath`.

### Task 9: The ring clang

**Files:**
- Modify:
  - `tools/generate_hyperion_sounds.sh` (replace the puck drop with `ring_clang`)
  - `ModSounds` (`HYPERION_PUCK_DROP` becomes `HYPERION_RING_CLANG`, id `blocks.hyperion_ring_clang`)
  - `sounds.json`, the subtitle lang key, `HyperionVentJets.start`
  - `tools/audio_sources/hyperion/SOURCES.md`, `docs/audio/SHIPPED_AUDIO_INVENTORY.tsv`
- Delete: `sounds/blocks/hyperion/puck_drop.ogg`
- Test: `HyperionSoundAssetsTest`

**Interfaces:**
- **Produces:** `ring_clang.ogg`, 1.2 s, mono 44.1 kHz.
  - Three heavy metal strikes 0.06 s apart, one per falling ring.
  - Each strike is a 70 to 40 Hz thump plus inharmonic partials at 220 x (1, 2.76, 5.4), decaying at 3, 5 and 9 per second.
  - Fixed noise seed 52008. No pitch sweeps above 300 Hz and nothing voice-like.
  - Subtitle: "Hyperion rings fall".
- [ ] **Step 1:** In `HyperionSoundAssetsTest`, replace `puck_drop` with `ring_clang` in the expected list and assert `puck_drop.ogg` is gone.
- [ ] **Step 2:** Run `./gradlew test --tests 'com.frozendawn.power.HyperionSoundAssetsTest'`. Expected: FAIL.
- [ ] **Step 3:** Generate with `bash tools/generate_hyperion_sounds.sh`, then update the registry, `sounds.json`, lang, ledger and inventory (Hyperion rows only).
- [ ] **Step 4:** Run the Hyperion unit tests. Expected: PASS.
- [ ] **Step 5:** Commit `feat(hyperion): a ring clang for the quench`.

### Task 10: Lab replay, checkpoint and gates

**Files:**
- Modify: `docs/power-network-checkpoint.md`, plus `tools/prepare_hyperion_playtest.py` if the lab layout moved anything the menu points at.

- [ ] **Step 1:** Run `./gradlew test` and, in the background, `./gradlew architectVerify`. Expected: both PASS. The uplink sky-check failure from the cube branch is out of scope; if it is the only failure, record it as such and do not fix it here.
- [ ] **Step 2:** Prepare the playtest world with `python3 tools/prepare_hyperion_playtest.py`, without `-I`, preserving the existing Hyperion Check save as the script does. Launch with `./gradlew runClientLab -PfdLabWorld='Hyperion Check' --console=plain` in the background.
- [ ] **Step 3: Owner pass.** Record their unprompted description before any diagnosis. Check:
  - seams vanish and trims read
  - bars read from across the base
  - rings look good up close and far
  - pipes and vapour
  - each state in the table, including charging from the SFR-1, the lava heat warning, the quench (steam, clang, ring drop) and the scorch, then cooling back to formed
- [ ] **Step 4:** Add a checkpoint section "Hyperion silo implemented: <date>" with the commits, gate results and owner findings. Commit `docs: record the Hyperion silo pass`.

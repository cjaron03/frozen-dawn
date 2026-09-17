# Expanded Architect environments

The stress matrix now contains **396 variants across 47 scenarios**. The 23 new
scenarios add 256 variants to the previously green 140. Before the slab fix, two fresh runs produced
**366 passes, 30 failures, zero missing cases**, with the same failing variants.
The slab clearance fix described below resolves the low-ceiling and mixed-stair
findings. Fence corners, closed gates, and the slab bridge remain open.

## Coverage

Each fixed layout runs seeds 7 and 1337 at 0°, 90°, 180°, and 270°. Each random
field runs seeds 1, 7, 42, 1337, 2026, 65537, 314159, and 8675309 in all four
orientations. The original 140 variants retain their existing configuration.

All new cases require a recorded melee hit and a health decrease on an intended
villager, preserve the starting floor and Architect health, and allow zero
excavation except `gate_closed`, which permits one block. Static villagers have
AI disabled but are damageable. Scripted movement/removal happens automatically.

| Scenario | Environment / behavior exercised | Deadline (ticks) |
| --- | --- | ---: |
| `fence_gap` | Passage through a gap in a fence barrier | 600 |
| `fence_detour` | Walk around a fence barrier | 600 |
| `fence_corner` | Exit a U-shaped fence enclosure to reach a target outside its closed end | 800 |
| `gate_open` | Open fence gate collision | 600 |
| `gate_closed` | Closed gate in a fenced barrier; one excavation allowed | 800 |
| `gate_reopens` | Gate closes at 45 and opens at 100; hit after reopening | 600 |
| `slab_checkerboard` | Repeated half-block transitions | 600 |
| `slab_top_tunnel` | Top-slab ceiling collision | 600 |
| `slab_low_roof` | Bottom-slab floor and top-slab ceiling with exactly two blocks of clearance | 600 |
| `slab_stair_mix` | Ascend and descend slabs, actual stairs, and full blocks | 600 |
| `slab_fence_lane` | Raised slab footing beside fences | 600 |
| `slab_trapdoor` | Slabs and trapdoor collision shapes | 600 |
| `footing_ice` | Slippery corridor | 600 |
| `footing_honey` | Slow footing | 600 |
| `footing_soul_sand` | Reduced collision height and slow movement | 600 |
| `footing_slab_bridge` | Raised narrow slab bridge; falling off fails even without damage | 600 |
| `multi_choice` | Three villagers; ordinary target selection, hit any participant | 600 |
| `multi_crossing` | Two villagers move at tick 60; require a hit afterward | 800 |
| `multi_target_removed` | Primary disappears at 60; hit the surviving secondary afterward | 800 |
| `multi_near_enclosed` | Nearest villager is bedrock-enclosed; reach and hit the accessible secondary | 1200 |
| `field_fences` | Seeded fences and walls around a reserved clear route | 1200 |
| `field_slabs` | Seeded slab obstacles around a reserved clear route | 1200 |
| `field_mixed` | Seeded fences, walls, slabs, stairs, trapdoors, and full blocks | 1200 |

Multiple-target cases release the debug target lock and exercise production target
selection. Reports preserve participant UUIDs, roles, health, removals, verified
hits, and TARGET_CHANGE events. All 32 multiple-target variants passed. In the
seed-1337/0° enclosed case, the Architect abandons the inaccessible target and hits
the secondary at tick 702. This checks eventual recovery within the stated deadline.

Random fields reserve a three-block-wide monotonic route from one corner to the
other, then scatter obstacles outside it. Changing the seed changes terrain.
Reports include the generation recipe, reserved route, and actual terrain hash.
All 96 sampled field variants passed. This samples connected fields; it does not
exhaust arbitrary terrain or force every generated collision shape to be crossed.
The fixed layouts exercise those transitions directly.

## Repeated findings

| Scenario | Failed variants | Observed behavior |
| --- | ---: | --- |
| `fence_corner` | 8/8 | Cycles movement inside the enclosure until the 800-tick deadline |
| `gate_closed` | 8/8 | Fails to traverse the barrier, abandons the target, then times out; no excavation |
| `slab_low_roof` | 8/8 | Destroys a ceiling slab as HEAD_CLEARANCE despite the two-block opening |
| `slab_stair_mix` | 4/8 | At 90° and 270°, stalls in PEEK and misses the 600-tick deadline |
| `footing_slab_bridge` | 2/8 | At 270°, leaves the bridge at tick 47 without health loss |

First evidence: `build/architect-monkey-reports/c953dff8-f3ea-4531-bcdd-706499556577/`.
Repeat evidence: `build/architect-monkey-reports/558c3877-56e1-419a-a6e7-1679009cdb2c/`.
The repeat uses a 4096-entry buffer for expanded cases so long failures retain
their early decisions. Each directory includes a linked README, matrix, immutable
summaries and traces, gameplay XML, and source fingerprint. Build artifacts are
removed by `gradlew clean`; preserve them before cleaning.

## Watch or vary a reproduction

Restart Minecraft to load the rebuilt smoke jar, or launch `./gradlew runClientLab`
from this worktree. Use a disposable lab world with cheats. Setup and rotation
replace terrain; rotations extend around the saved origin and erase the previous
arena footprint. Remain in spectator so your player does not affect targeting.

```mcfunction
/gamemode spectator
/fd architect lab setup
/fd architect lab target static
/fd architect lab scenario slab_low_roof
/fd architect lab seed 1337
/fd architect lab rotation 0
/fd architect lab run
/tick unfreeze
```

The runner freezes and exports automatically on pass or failure. Use
`/fd architect lab inspect` for the result and `/fd architect lab dump` for a
snapshot. `/fd architect lab tp` returns to the rotated observation corner.

For randomized fields, select `field_mixed`, change the seed to any long integer,
and run again. For the bridge finding, select `footing_slab_bridge` and rotation
`270`. Scenario, seed, and rotation commands each rebuild the arena and leave ticks
frozen. To repeat unchanged settings, use `lab reset`, `lab run`, `/tick unfreeze`.
Use `/tick step 1` for individual ticks or `/tick sprint 1300` to accelerate a run.

Run the full automated matrix with `./gradlew architectMonkey --console=plain`.
Its nonzero exit currently reports the 30 findings. The separate baseline gate is
`./gradlew build gameTestGate --console=plain`. Visual replay of these new cases
has not yet been checked in a native client; automated results are headless.

## Final artifact verification

Full build passed: 491 unit tests, eight validator tests, 51 fixtures, and all
28 baseline GameTests. Baseline invocation: `cc1a1817-3419-421a-9a5c-e7dd5876c31b`.
After the in-game help update, the final stress run reproduced 366 passes and
30 failures against the packaged source fingerprint. Final evidence:
`build/architect-monkey-reports/b493cafd-aa3b-4d53-b87c-472589878d19/`.
It also preserves all three stress logs, the full build log, and the smoke-jar
SHA-256. Only the Frozen Dawn smoke jar was replaced. Restart the client to load it.
The build emits the existing Gradle deprecation warning.

## Slab clearance correction

The live seed-2026 low-roof report selected an upward HEAD_CLEARANCE breach at
40 ticks and destroyed the top ceiling slab at 79. The actor had not moved.
The two-block opening physically fits the 1.95-block Architect, but the planner
classified the bottom floor slab and top ceiling slab as occupied block cells.

ArchitectWalkGeometry now computes a supported fractional standing height and
checks the body's collision sweep for adjacent partial-surface walking transitions.
The shortcut requires centered support, no intersecting blocks or fluid/fire, and
at most 0.6 blocks of elevation change. It does not change general block breakability.
D* edge costs and step dispatch share this check. Corridor mining skips partial
standing cells with clear body space, and committed movement uses their actual
surface height. Real blocked transitions retain the existing breach handling.

The unchanged 396-case matrix improved to **378 passed / 18 failed**, with no
previously passing case failing. All eight low-roof and all eight mixed-stair
variants pass. The baseline additionally covers the user's seed-2026 LIVE target
in all four rotations, plus geometry checks for a truly lowered ceiling, a full
wall, a fence, and missing support. A second full stress run verifies the final
packaged source. Native client observation of the fix still requires a restart.

Final slab-fix confirmation: `build/architect-monkey-reports/362f05a8-5c2c-4014-a96b-3cbaedd54c77/`.
The 378/18 result repeated, all 396 case contracts match the pre-fix run, and
`before-after.json` lists the 12 resolved variants and zero regressions.
Full build: 491 unit tests, eight validator tests, 51 fixtures, and 33 baseline
GameTests passed. Baseline evidence: `c4e3b2af-4589-41e4-b0ec-c4cef90a9d5b`.
Jar and stress report source fingerprints match; smoke checksum and logs are saved
with the final report. Existing Gradle deprecation warnings remain.

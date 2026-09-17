# Architect stress testing

Current result after the slab clearance fix: **378 passed, 18 failed, zero missing**.
The original 140 cases still pass. The 256 new variants exposed five failure patterns; the slab fix resolves two. See [expanded environments](architect-monkey-environments.md) for coverage,
findings, and replay commands. Earlier sections below preserve the investigation.

This suite exercises the current WIP with awkward geometry, seeded mazes, and
scripted world/target changes. It is a discovery suite: a failed case remains a
failure with its trace, rather than being skipped or given a larger budget to pass.
The baseline regression gate and the exploratory stress gate are separate commands.

## Run the matrix

```sh
cd /Users/jaroncabral/Projects/minecraft-mod/.worktrees/architect-lab
./gradlew architectMonkey --console=plain
```

The matrix has **396 stress cases across 47 scenarios**, alongside the existing
33 baseline/harness GameTests. The original scenarios use seeds 7 and 1337 in two orientations
(0° and 90°). The eight hole scenarios use all four cardinal orientations
with both seeds (64 additional cases). The maze and the two extended corridor cases also use seeds 1 and 42.
The 23 added scenarios run in all four orientations; randomized fields use eight
seeds. The generated maze is connected by construction; its actual starting terrain hash
is saved in the report. This is a bounded reproducible sample, not exhaustive fuzzing.

Each invocation starts its own fresh world under `build/architect-monkey/run/`.
It does not rebuild the interactive arena or alter the open client world.
Results, the gameplay XML, and a readable `README.md`/`matrix.json` are preserved in
`build/architect-monkey-reports/<invocation UUID>/`. The console prints that path,
even if gameplay fails. A nonzero exit is expected while a captured finding remains
unfixed. `./gradlew architectVerify` continues to run the baseline plus full build.

## Interactive reproduction

Quit and relaunch the dev client after compiling the new cases:

```sh
./gradlew runClientLab
```

In the existing lab, use the following. `target static` resets the actor and gives
it a stationary villager; that is the mode used by this stress matrix. Hole and expanded environment cases
make that villager damageable and require a recorded Architect melee hit plus a
health decrease. Other static cases keep the villager invulnerable.

```mcfunction
/fd architect lab target static
/fd architect lab seed 1337
/fd architect lab scenario closing_passage
/gamemode spectator
/fd architect lab run
/tick unfreeze
```

The lab schedules the gate changes itself. Do not manually place/remove blocks
during the reproduction. It freezes and exports at PASS/FAIL. Use
`/fd architect lab inspect` to read the outcome; freezing alone does not mean pass.

To rerun a case:

```mcfunction
/fd architect lab reset
/fd architect lab run
/tick unfreeze
```

To try another case, replace `closing_passage` with a name below. Scenario selection
already rebuilds the arena. The longer cases can be accelerated with
`/tick sprint 1600` after `lab run` if you do not need to watch in real time.

## Scenarios and contracts

All stress cases require a living target, an undamaged Architect, an intact starting
floor, and no lava/fire exposure. Except `wall` (six) and `gate_closed` (one), they permit **zero destroyed blocks**.
For original non-hole cases, static reach means distance at most 3.1 blocks with line of sight.
Hole and expanded cases require a successful melee hit; proximity alone cannot pass. Bridge/stair
supports are separately preserved, including the slab blocks.

| Scenario | What it probes | Completion condition / deadline |
| --- | --- | --- |
| `wall` | Excavation through a three-block-thick stone wall | Reach target, at most six blocks destroyed; 600 ticks |
| `dogleg` | A tight right-angle passage with two-block headroom | Reach around the corner; 400 ticks |
| `u_detour` | Target nearby through a wall, with a long U-shaped accessible route | Follow the connected route; 600 ticks |
| `cheap_detour` | A short clear detour around a mineable deepslate barrier | Reach without excavating; 400 ticks |
| `stairs_up` | Three full-block ascents on mineable supports | Reach elevated target; 400 ticks |
| `stairs_down` | Three full-block descents in a narrow passage | Reach lower target; 400 ticks |
| `narrow_bridge` | One-block-wide raised stone bridge | Reach target without falling below bridge height; 400 ticks |
| `offset_doorways` | Alternating narrow openings in a stone-floor chamber | Reach through both openings; 500 ticks |
| `slab_steps` | Half-block collision shapes mixed with full-block steps | Reach elevated target, preserve slab/stone supports; 400 ticks |
| `vanishing_wall` | Mining target removed externally while approaching | Two-block stone gate disappears at tick 60; reach without counting/mining another block; 350 ticks |
| `target_juke` | Same target changes direction during a committed approach | Target moves at ticks 60 and 100; reach its final position; 450 ticks |
| `seeded_maze` | Connected random maze with tight turns and a distant target | Reach target without floor mining; 1600 ticks |
| `closing_passage` | A committed path becomes blocked, then becomes clear again | Bedrock gate closes at tick 45, opens at 100; reach without excavation; 400 ticks |
| `lava_detour` | A lava patch interrupts the direct route | Take a safe route around it; 400 ticks |
| `corridor_soak` | Unwanted digging after reaching an idle nearby villager | Stay in the stone corridor for 1000 ticks and still reach the target, no digging; deadline 1050 |
| `corridor_shuttle` | Repeated reversals toward the same villager in a stone corridor | Target changes ends at ticks 120, 240, 360, 480; no digging through tick 650 and reach final position; deadline 750 |

| `pit_shallow` | One-block-deep open hole | Melee hit without excavation; 600 ticks |
| `pit_direct_steps` | Three-block-deep hole with wide steps directly ahead | Melee hit without excavation; 600 ticks |
| `pit_side_steps` | Four-block-deep hole with a safe staircase at the side | Use an available route without mining or fall damage; melee hit; 600 ticks |
| `pit_corner_steps` | Diagonal approach to a three-block-deep hole with wide steps | Melee hit without excavation; 600 ticks |
| `pit_narrow_steps` | One-block-wide descending route into a narrow hole | Melee hit without excavation; 600 ticks |
| `pit_slab_ramp` | Half-block descent into a two-block-deep hole | Melee hit without excavation; 600 ticks |
| `pit_tunnel` | Clear two-block-high tunnel opening into the hole | Melee hit without excavation; 600 ticks |
| `pit_target_offset` | Target against the far side of a hole with direct steps | Melee hit without excavation; 600 ticks |

`tools/architect_fixtures.py --check` independently checks all eight hole layouts
for a connected cardinal route that fits the Architect's 0.6 × 1.95 body, uses
at most one-block height changes, and requires no excavation. The check models
slab collision height and swept headroom at transitions. This validates the test
geometry, not the production pathfinder. All surrounding hole terrain is mineable
stone so unnecessary excavation is observable.

`corridor_soak` lasts about 50 seconds at normal speed; `corridor_shuttle` lasts about
32.5 seconds. The maze can take up to 80 seconds. A scripted moving target is still
marked STATIC in the report because its autonomous AI is disabled; the runner moves
it at the documented ticks. Use STATIC for these long tests so the villager survives.

## First captured finding

The first matrix found **two failures** in `closing_passage`: both seeds at 0°.
The 90° cases passed. In each failing trace:

- Tick 45: the passage closes.
- Tick 99: the Architect begins a ceiling breach to get around the obstruction.
- Tick 100: the original passage reopens.
- Tick 129: it still finishes destroying the ceiling, failing the zero-excavation contract.

This captures a mining decision that continues after the direct passage becomes
clear. It resembles the reported unnecessary-digging symptom; it does not establish
that the user's original static-corridor incident had this exact trigger. The long
static corridor and shuttle cases passed in the first matrix.

The first evidence directory is
`build/architect-monkey-reports/3364f768-f92c-4bdb-8bd2-2db36aa2b343/`.
No production navigation/mining fix is included in these stress-test additions.
Use the trace to diagnose the obsolete breach and retain the failing test when
implementing a fix.

## Original-world capture

For a spontaneous corridor incident, record the existing Architect before it happens
using the [raw diagnostic commands](architect-lab.md#raw-entity-diagnostics).
Do not run lab setup/reset in the original corridor. Freeze and dump immediately
when the unwanted digging starts, retain the world/terrain and the report, and note
where the villager and player were. The journal's STATE rows now include target
position and health, and summaries include both actors' final positions/health.

## Verified follow-up

The final fresh-world matrix produced **70 passes and 6 failures**, with no missing
cases. The two closing-passage failures repeated at tick 129. All four wall variants
failed the no-damage contract: after scaffolding over the wall, the Architect takes
**1 point of fall damage** on descent. The new DAMAGE journal row identifies `fall`
and records the resulting health (40 to 39).

The same two findings appeared in the preceding run after the health invariant was
added. The original first run did not assert health preservation, which is why its
wall cases passed. The later failures were not intermittent regressions between
builds; the new assertion exposed previously unmeasured damage.

Final evidence: `build/architect-monkey-reports/52d607d0-d4bd-4239-8938-7a36eac871eb/`.
It includes the result matrix, immutable traces, exact source fingerprints, gameplay
XML, stress/build logs, and matching smoke-jar checksums. The full build passed with
491 unit tests, 8 gate-validator tests, 20 fixture checks, and all 28 baseline
GameTests. The stress gate remains red until these findings are resolved.

The obsolete-mining investigation should start in
`ArchitectApproachController.executeApproach` and
`ArchitectApproachBreakSupport.continueBreaking`: active mining returns before the
usual plan refresh and stale-break-target validation. This is a code-based lead;
the trace establishes the behavior, not yet a validated fix.


## Target-in-hole follow-up

Added eight hole scenarios × two seeds × four orientations: **64 new tests**.
Ran the complete 140-case stress matrix twice in fresh worlds:

| Run | Stress passed | Stress failed | Missing | New hole cases passed / failed |
| --- | ---: | ---: | ---: | --- |
| First | 116 | 24 | 0 | 46 / 18 |
| Repeat | 117 | 23 | 0 | 47 / 17 |

Both runs retained the six previously recorded failures. All eight variants of
`pit_side_steps` and all eight of `pit_corner_steps` failed by destroying one block.
The independent geometry check confirms a clear, safe staircase route in each.
For seed 1337 at 0°, side steps starts `DIG_DOWN` at tick 61 and destroys the block
at tick 91; corner steps starts at tick 90 and destroys it at tick 120. Both traces
show `SCAFFOLD_BRIDGE` immediately before the downward mining choice at the rim.
This reproduces unnecessary excavation with an available route to a lower target;
it does not establish that the original remembered incident used identical terrain.

All variants of shallow, direct steps, narrow steps, tunnel, and offset-target
cases passed both runs, with a recorded melee hit and no blocks destroyed.
`pit_slab_ramp` at 180° timed out in both seeds in the first run; seed 7 timed out
again, while seed 1337 passed the repeat. Failed traces switch from APPROACH to
PEEK at tick 64 and remain there through the 600-tick deadline without hitting.
This is an intermittent finding, not a deterministic fixed-seed guarantee. Seeds
control the actors' random sources, but UUIDs and the complete simulation state
are not identical between fresh worlds.

First evidence: `build/architect-monkey-reports/bde52def-0152-4691-97e9-a0b3694a958a/`.
Repeat evidence: `build/architect-monkey-reports/2f8c78b2-0871-4935-ae70-9098b838734a/`.
The repeat directory also preserves both stress logs, the final build/baseline log,
and the matching smoke-jar checksum. Final `build gameTestGate` passed: 491 unit
checks, 8 gate-validator tests, 28 generated fixtures, and 28 baseline GameTests.
The stress gate is still red. No navigation, excavation, or combat policy fix was
made in this follow-up; production code only gained a passive MELEE_HIT journal row.

For a manual reproduction after restarting the client, select
`/fd architect lab scenario pit_side_steps` or `pit_corner_steps`, with static
target and seed 1337, then run the lab. The default 0° orientation reproduces both.


## Descent fix

The hole fix removes the proximity-only bridge-to-DIG_DOWN override. An open
step down now executes as WALK, and both D* and vanilla navigation share a
three-block maximum fall distance. Before selecting excavation toward a nearby
lower target, the Architect prefers a complete vanilla walking path with at most
32 nodes, within eight horizontal blocks and six vertical blocks. The endpoint
must reach the target's level; blocked or positive-malus nodes and excessive
height drops reject the path. Pending mining and scaffold movement finish through
their existing handlers before this preference runs. OPEN_DESCENT records the
chosen walking route in the journal.

The original override was one cause. After removing it, the side-entry fixture
still selected a planned CORRIDOR_NODE breach because that shortcut scored below
the longer staircase. The walking-route preference addresses that second cause.
Test names, seeds, rotations, fixtures, deadlines, and invariants remain unchanged.

With the complete change, all 64 hole cases passed, including the slab ramp, and
all four wall cases passed without fall damage. The remaining failures are the two
closing-passage cases: active ceiling mining continues after the gate opens.
Those remain recorded failures and require a separate stale-mining correction.

Final confirmation repeated **138 passed / 2 failed / 0 missing**. Evidence:
`build/architect-monkey-reports/e93e4c10-dee4-4a5f-a184-540f1ccfdc92/`.
The preceding complete-fix run is
`build/architect-monkey-reports/a4b4fe77-09e9-4d02-be0f-210750053047/`.
`before-after.json` compares the final run with the prior 117/23 run and records
that all 140 names, fixtures, seeds, rotations, deadlines, excavation budgets,
and initial actor conditions match. No previously passing case failed.

Final `build gameTestGate` passed with 491 unit tests, eight gate-validator tests,
28 fixture checks, and all 28 baseline GameTests. Baseline artifact invocation:
`6ed42acd-6c05-4aea-ad02-68cbd319c13f`. The final stress directory includes the
build log and SHA-256 for the updated smoke jar. Restart the client to load it.


## Ceiling mining fix

Active mining returned before the normal plan refresh and stale-target check.
In closing_passage, a HEAD_CLEARANCE break began at tick 99, the passage reopened
at 100, and mining still completed at 129. The current approach checks for a fresh,
complete safe walking route every five ticks and again before the destructive
mining tick. The check is bounded to nearby targets (eight horizontal blocks,
six vertical blocks) and paths of at most 32 nodes. It requires an exact endpoint
at the target block, rejects blocked/hazard-cost nodes and excessive falls, and
forces a fresh navigation search instead of trusting a cached route.

When that route exists, BREAK_CANCEL records OPEN_ROUTE, the mining target and
crack overlay are released, and D* rebuilds against current terrain so it cannot
immediately reuse the old breach. Actual necessary mining still uses the existing
handlers. The retired bridge drop-in exception was also removed from stale-target
validation. No test fixtures, seeds, rotations, deadlines, or contracts changed.

Both the initial implementation and final confirmation passed **140 stress tests,
zero failures, zero missing**. Final evidence:
`build/architect-monkey-reports/8f1db492-db95-4093-93dd-77a099dbcba3/`.
Initial evidence: `build/architect-monkey-reports/3d1bba11-3683-43c5-8e18-25aacba45c0e/`.
In the final seed-1337/0-degree closing_passage trace, the passage reopens at 100,
BREAK_CANCEL occurs at 105, and the case passes at 120 with zero destroyed blocks.
All 64 hole cases still reach and hit their villager with no excavation.

The full build, 491 unit tests, eight gate-validator tests, 28 fixtures, and all
28 baseline GameTests pass. Baseline invocation:
`a9a51e2a-a7cc-4fd4-9055-308d83d16ef5`. The final stress directory preserves both
stress logs, the baseline/build log, and the matching smoke-jar checksum. The jar's
source fingerprint matches the final stress reports. Restart Minecraft to load it.

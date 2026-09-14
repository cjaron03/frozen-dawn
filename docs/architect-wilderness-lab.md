# Architect wilderness lab

See the [visual debugger guide](architect-visual-debugger.md) for routes, collision geometry, freeze/step controls, history, and visual evidence in logs and dumps.

The wilderness lab prepares a 128×128 region in its own dimension, using native
Overworld generation as the foundation. It adds Frozen Dawn blocks and connected
trails through deep snow, Acheronite crystals, frozen atmosphere deposits, a cave,
a fenced passage, a shelter, and a ravine crossing. The surrounding hills and
underground terrain remain available for exploration.

This is a landscape and navigation stress harness. Baseline scenarios use scripted
late-phase terrain. The `weather` scenario additionally runs production snow
accumulation and block freezing at a local phase-5 setting. It does not change
Overworld apocalypse progression. Existing world progression continues normally
when you unfreeze ticks, so use a disposable test world.

## Start watching

Restart the Minecraft client/server after installing this version so the new
`frozendawn:architect_wilderness_lab` dimension loads. For this development checkout,
launch with `./gradlew runClientLab` from the checkout containing the code you want to test.
For the visual debugger branch, use `.worktrees/architect-visual-debugger`.
Use a disposable world with cheats and operator permission level 2.

```mcfunction
/difficulty easy
/gamerule doMobSpawning false
/gamerule randomTickSpeed 0
/gamerule fallDamage true
/fd architect wilderness setup
```

These gamerules are recommended for isolating navigation from unrelated mobs and
random block changes; they apply to the whole world. The commands also undo the
small lab's disabled fall damage. Wilderness setup itself does not change those
gamerules or difficulty.

Wait for **Wilderness ready**. Preparation saves/restores 64 native chunks, builds
the frozen landscape, validates the guided villager's paths, and fingerprints the
result. Preparation advances while simulation ticks are frozen and can take a
while, especially on the first use. Progress appears in chat. When ready, it moves
you into Spectator at the observation point and keeps ticks frozen.

```mcfunction
/fd architect wilderness run
/tick unfreeze
```

This starts the three-minute surface scenario. Follow the named Architect and
villager in Spectator. Completion or failure freezes ticks and exports a report
automatically. Durations measure simulated ticks at 20 ticks per second; pausing
does not consume them, and a slow server takes longer in real time.

For the full thirty-minute encounter, select the scenario, wait for another
**Wilderness ready**, then start:

```mcfunction
/fd architect wilderness scenario endurance
```

```mcfunction
/fd architect wilderness run
/tick unfreeze
```

Neither actor is teleported, replaced, nor forced back into APPROACH during a run.
Setup/reset creates fresh actors. The guided target is a real villager with 100 HP,
normal collisions, gravity, jumping, navigation, and damage. The harness chooses
its destinations instead of its normal brain. Its normal walking speed lets the
Architect close the gap; a short burst after a catch creates room for another
pursuit. The first verified hit on each route leg restores the villager's health.
It is not invulnerable and can still die if trapped or repeatedly hit on one leg.

## Scenarios

| Scenario | Duration | Required encounter |
| --- | ---: | --- |
| `surface` | 3 minutes | Continuous moving pursuit around the frozen surface circuit |
| `caves` | 5 minutes | Surface pursuit plus the cave branch; the Architect must enter and leave the cave |
| `shelter_open` | 5 minutes | Moving target through open shelter entrances; any Architect block destruction fails this control |
| `shelter_breach` | 5 minutes | Entrances close after the target enters; the Architect must break in and land a verified hit |
| `changing` | 8 minutes | Gate closure with an open detour, crystal growth, snow growth, later atmosphere deposits, and loss of the crossing |
| `endurance` | 30 minutes | Changing terrain, cave traversal, shelter breach, and a target direction change during mining or scaffolding in one continuous encounter |
| `construction` | Up to 15 minutes | Six-block ascent, removed four-block crossing, closed wall, low ceiling, target movement during construction, and a verified final hit |
| `weather` | 15 minutes | Surface pursuit with production phase-5 snowfall/freezing, minute checkpoints, and confirmed snowfall mutations |
| `roam` | 10 minutes | Villager uses its normal autonomous brain; ungraded observation |
| `player` | 30 minutes | Architect pursues the operator in Survival or Adventure; ungraded observation |

The dynamic scenarios wait until the villager has cleared an obstacle and the
Architect is behind it before changing blocks. They avoid placing blocks inside
either actor. A closed fence gate leaves a second gate open. Crystals grow through
ages 0–3. The first half of a dynamic run adds three-block snow walls; the second
half adds frozen atmosphere deposits in air or thin snow. The shelter target
continues walking inside while its entrances are sealed.

The bridge is removed only after the villager crosses. Once both actors clear the
crossing, the harness restores its deck for the next lap. Other block changes and
the Architect's edits persist during the encounter. If an obstacle never gets a
safe opportunity to trigger, the result is **SCENARIO_INCOMPLETE**, rather than a
pass. A target trapped by changed terrain is reported separately.

`roam` and `player` capture what happens without asserting a scripted pass. For
`player`, select the scenario, wait for preparation, fly to a safe place in the
lab, switch to Survival or Adventure, and run. The villager is removed when that
run begins. Player health is not replenished by the harness.

## Controls

| Command | Effect |
| --- | --- |
| `/fd architect wilderness setup` | Prepare the region; first use defaults to `surface`, seed `1337` |
| `/fd architect wilderness scenario <name>` | Select a scenario and rebuild |
| `/fd architect wilderness seed <long>` | Select the overlay and actor RNG seed, then rebuild |
| `/fd architect wilderness reset` | Restore the saved native terrain and rebuild the selected scenario |
| `/fd architect wilderness run` | Start recording and enable the actors; ticks remain frozen |
| `/fd architect wilderness inspect` | Show preparation progress or status, reason, and elapsed ticks |
| `/fd architect wilderness dump` | Export a snapshot of the current or completed recording |
| `/fd architect wilderness stop` | Abort an active run, freeze ticks, and export |
| `/fd architect wilderness tp` | Return to the observation point in Spectator |
| `/fd architect wilderness leave` | Abort an active run, remove the lab actors, release its chunk tickets, and restore your original position and game mode |

Use `/tick freeze`, `/tick step 1`, or `/tick step 20` to inspect a problem without
resetting it. `dump` can be used while paused or running. `stop` and `dump` require
a recording to have started. Use `/tick unfreeze` after leaving if the world is
still paused. A small-lab run and a wilderness run cannot operate simultaneously.

Before using `leave`, other observers should exit the dimension; it tears down
the shared session. Return positions exist only for the current server session.
If you restart while inside the lab, return explicitly with
`/execute in minecraft:overworld run tp @s <x> <y> <z>` and restore your game mode.

## Results and reports

Every minute of a graded pursuit run (all except construction) must include both at least twelve blocks of horizontal target
travel and an actual Architect melee hit confirmed by health loss. Later success
cannot hide an earlier failed checkpoint. The harness also checks for prolonged
lack of progress, repeated spinning/circling, leaving the region, falling into the
crossing ravine, fire/lava, and excessive excavation/scaffolding. These are initial
thresholds to assess during visual testing, not proof that every possible AI
failure is covered.

| Status | Meaning |
| --- | --- |
| `PASSED` | All minute checkpoints and the scenario's required events completed |
| `FAILED` | An Architect assertion failed; the reason identifies the observed condition |
| `TARGET_FAILED` | Target movement, survival, or bounds failed; the Architect result is inconclusive |
| `SCENARIO_INCOMPLETE` | A required route or terrain event never completed; inconclusive |
| `OBSERVED` | Exploratory recording ended without a scripted pass claim |
| `ABORTED` | Operator reset, stopped, left, or shut down during the run |
| `HARNESS_ERROR` | An exception interrupted preparation or recording |

The report path is printed in chat. Reports live under the save's
`architect-debug/wilderness/` directory. Each export has its own immutable folder:

- `summary.json`: outcome, build fingerprint, seeds, terrain hashes, route,
  checkpoints, mutations, health, travel, stall counters, rotation warnings,
  difficulty, and relevant gamerules.
- `decisions.tsv`: Architect action, path, break choice, recovery, and harness
  events. The bounded journal holds 65,536 entries and reports discarded entries
  if it fills.
- `movement.tsv`: both actors' positions every five ticks, target health/path
  state, Architect body/head yaw and action, catches, breaks, and placements.
- `README.md`: outcome and interpretation notes.

The latest-report pointer is published only after all files are written. Peak and
95th-percentile tick-cost metrics sample the **whole server's 100-tick mean**, not
individual Architect CPU time or frame time. CPU cost is recorded for comparison;
it is not a pass/fail threshold. A harness exception marks the latest pointer as
failed; an earlier manual dump remains available in its immutable folder.

## Replay and visual checks

Native generation uses the **world seed**. The wilderness `seed` changes the frozen
overlay and actor RNG; it does not regenerate different native hills. On first
preparation, native chunk templates are saved under
`generated/frozendawn/structures/lab/wilderness_native_v1/` in the world save.
Reset restores those templates before applying the selected overlay. Keep that
save, scenario, overlay seed, build, and gamerules to reproduce a result. Use a
different world seed for a different native foundation.

`preparedTerrainSha256` describes the finished preparation. If you manually edit
blocks after **Wilderness ready**, that hash no longer describes the run's exact
starting state. Such edits are useful for exploration, but reset before comparing
scripted runs. Matching seeds do not promise identical timing across hardware,
server load, or other world activity.

For the first visual pass, check that the villager walks each route without
teleporting, the Architect catches it repeatedly, and the report distinguishes a
stuck villager from an Architect failure. Compare `shelter_open` and
`shelter_breach`, then watch `changing` before committing to `endurance`. At a
pause, watch the head turn and thinking pose, then confirm it resumes pursuit
without circling. Freeze and dump immediately when a problem appears, preserving
the scene for inspection.

This addition has received compilation and resource checks only. No simulations
or visual runs were executed during implementation.

## Snow-step target regression (recipe 2)

The seed-1337 surface run captured on 2026-09-14 stalled at waypoint 43 near
(22.5, 65, 40.3). Four decorative snow layers on the next raised trail block
made the jump physically too tall despite vanilla reporting a reachable path.
The villager bounced in place and died at tick 2419; the run was correctly
classified TARGET_FAILED.

Recipe 2 places optional snow/atmosphere decorations beside the three-block
trail lane. The target stall timer now requires at least 0.25 blocks of net
horizontal displacement to reset; vertical jumping and small collision jitter
cannot reset it. Target travel totals and minute checkpoints also exclude
vertical distance. The 160-tick warning and 400-tick target-failure thresholds
remain in place. Deliberate scenario obstacles and mutations still apply.

The physical GameTest uses the same four-layer snow/one-block-step collision
geometry and actor seed 1337: it first requires the old target to get stuck,
then relocates the decoration with the recipe's shoulder placement and requires
the same villager to cross using ordinary navigation and physics.

Restart Minecraft to load the fix, then use `/fd architect wilderness setup`
to recreate the session with the default seed 1337 and recipe 2 before starting
another run. Within an existing session, `wilderness reset` regenerates its selected seed.
Existing exports preserve the original failure and terrain fingerprint.


## Construction and production weather

After setup, select one scenario and wait for **Wilderness ready**:

```mcfunction
/fd architect wilderness scenario construction
/fd architect wilderness run
/tick unfreeze
```

Construction builds an enclosed course in the landscape. The same villager
moves through the intact course using normal navigation. The bridge is removed
only after the target clears it; the wall closes and the ceiling lowers only when both actors are clear.
Passing requires real scaffold placements and physical crossings, wall and
ceiling destruction, target movement during a construction action, and a verified
hit beyond the final obstacle. Station events and the `construction` summary
record which requirements actually happened. Missing triggers yield
`SCENARIO_INCOMPLETE`. Deliberate waits use station checks instead of the
baseline target-travel minute quota.

For weather, select `/fd architect wilderness scenario weather`, wait for
readiness, then run and unfreeze. The scenario calls the same
`SnowAccumulator` and `BlockFreezer` rules used in production, including snow
layers becoming blocks, depth limits, freezing and structural snow stress.
It uses phase 5 at progress 0.5, the configured snowfall rate, a seeded sampler,
and a fixed radius-63 footprint centred at (64,64). Spectator movement and extra
observers do not increase that workload. The existing prepared terrain remains
the starting point.

This exercises environmental block changes, not the entire apocalypse or all
client phase effects: crystal growth, late atmosphere deposition, and phase
transitions are not enabled by this scenario. Rain strength is local to the lab
and restored on stop, reset, leave, or completion. Some production stress chances
still use world randomness, so the seed does not promise identical outcomes.

Reports include weather settings, successful snowfall mutation counts, and
`weather.tsv`: 64 surface columns sampled per tick, with the latest 4096 changes
retained and a dropped-change count. These sampled net changes include AI edits;
the separate snowfall counter records successful production snow placements or
layer increments. The prepared terrain hash describes the starting world.
A weather pass also requires the normal moving-target catch checkpoints.
A snow-trapped or dead villager remains `TARGET_FAILED`, with an inconclusive
Architect result. Freeze and dump the run to preserve the obstacle and paths.

# Long Architect encounters

The stress suite contains 500 variants across 60 scenarios: the preceding 396
plus 13 new scenarios, each using seeds 7 and 1337 in all four rotations. Eleven
encounter types are accompanied by two open-route alternatives for digging.
The same fixtures and encounter scripts run in the visual lab and headless tests.

## Durations and contracts

Durations below are simulated time at 20 ticks per second. A passing run cannot
finish before its minimum duration. A broken invariant can fail immediately.
The deadline provides additional time to finish the final objective; intermediate
stages still have their own required hit checkpoints.

| Scenario | Minimum duration | Deadline | Contract |
| --- | ---: | ---: | --- |
| `scaffold_ascent` | 30 seconds / 600 ticks | 90 seconds / 1800 ticks | Build up to a seven-block platform and hit the villager; at least one scaffold placement |
| `scaffold_gap` | 30 seconds | 90 seconds | Build across a four-block gap, stay above the lower floor, and hit |
| `scaffold_interruption` | 3 minutes / 3600 ticks | 3½ minutes / 4200 ticks | Move the target after the first actual scaffold placement; pursue it, then revisit high and low destinations at ticks 1200 and 2400 |
| `scaffold_damage` | 3 minutes | 3½ minutes | Remove an actual constructed block ahead, never beneath the actor; cross successfully, then reverse twice |
| `dig_down_required` | 30 seconds | 90 seconds | Excavate below the actor into a sealed lower chamber and hit; a descending mined staircase is valid |
| `dig_down_open` | 30 seconds | 90 seconds | Same lower chamber with an exterior staircase and opening; hit with no excavation or scaffolding |
| `dig_up_required` | 30 seconds | 90 seconds | Excavate overhead and scaffold out of the lower chamber; hit the upper target |
| `dig_up_open` | 30 seconds | 90 seconds | Use the exterior staircase to the upper target with no excavation or scaffolding |
| `mixed_escape` | 3 minutes | 3½ minutes | Dig through the pocket exit, bridge the gap, climb to the target, then pursue two changes of elevation |
| `route_opens_mining` | 3 minutes | 3½ minutes | Remove the stone doorway only after mining starts; repeat after each target reversal; no completed excavation |
| `route_closes_travel` | 3 minutes | 3½ minutes | Close the direct doorway after the actor advances, leaving a detour; alternate doorways on later reversals |
| `target_turnover` | 5 minutes / 6000 ticks | 5½ minutes / 6600 ticks | Replace the villager every 600 ticks: ten targets, nine replacements, a verified hit in every stage |
| `long_pursuit` | 10 minutes / 12000 ticks | 10½ minutes / 12600 ticks | Move the same villager every 600 ticks around four destinations at two elevations; twenty successful pursuit stages |

All cases preserve the starting base floor and Architect health. Elevated gap and
mixed-escape cases also fail if the actor drops below the crossing level, even
without fall damage. Excavation budgets are 12 for required digging, six for mixed
escape, and zero otherwise. Placement budgets are 32 for focused ascent/gap/upward
digging, 96 for changing scaffold/mixed encounters, and zero for the remaining cases.
A required action must actually occur; proximity to a target cannot satisfy it.

A stage needs a recorded melee hit, a corresponding health decrease on its intended
villager, and that Architect recorded as the attacker. After verifying a hit, the
runner restores the villager's health so a long test can continue. Unexpected death
still fails. Targets remain subject to knockback, gravity, and other damage.
The runner resets fall distance when it deliberately teleports a target.
Use `target static` for the automated configuration; autonomous villager movement
in `target live` makes a different exploratory run.

No stage may go 600 ticks without useful progress while its target is unmet.
Improved distance, successful excavation, or scaffold placement count as progress;
cancelled mining does not. Placements and excavation remain bounded. Each new
stage requires a new hit; an early hit cannot pass the rest of a long run.
The Architect's AI is not reset between stages.

The two open digging alternatives also receive an independent fixture check for
a connected route with body clearance and safe height changes. The headless
turnover arenas are separated and temporarily loaded so natural target selection
cannot pick a neighboring test's villager. In the visual lab, use an empty area
and spectator mode to keep unrelated targets out of the encounter.

## Visual commands

Restart Minecraft after building, or launch the development client from this
worktree with `./gradlew runClientLab`. Use a disposable world with cheats. Lab
setup and rotation replace terrain around the saved arena origin.

```mcfunction
/gamemode spectator
/fd architect lab setup
/fd architect lab target static
/fd architect lab seed 1337
/fd architect lab rotation 0
/fd architect lab scenario scaffold_damage
/fd architect lab run
/tick unfreeze
```

This watches the full three-minute encounter unless it fails earlier. Replace
`scaffold_damage` with any name in the table. For the ten-minute run:

```mcfunction
/fd architect lab scenario long_pursuit
/fd architect lab run
/tick unfreeze
```

For accelerated execution use `/tick sprint 13000` after `lab run`. To inspect a
moment, use `/tick freeze`, `/tick step 1`, and `/fd architect lab dump`.
`/fd architect lab inspect` reports the outcome. Every terminal result freezes
and exports automatically. `lab reset`, `lab run`, and `/tick unfreeze` repeat
the same seed, rotation, scenario, and target mode.

## Reports and automation

```sh
./gradlew architectMonkey --console=plain
./gradlew build gameTestGate --console=plain
```

The monkey gate remains nonzero while any recorded finding is unresolved. Its
printed report directory contains all 500 results, immutable decision traces, and
source/terrain fingerprints. The separate baseline gate covers established
regressions and the harness.

Each lifecycle summary adds minimum duration, stage and hit counts, actual scaffold
placements, excavation direction counts, longest stall, budgets, and timestamped
world/target checkpoints. `SCAFFOLD_PLACE` is recorded only after successful
placement. `LIFECYCLE_EVENT` records scripted changes and verified stage hits.
Replan totals remain in the journal's cumulative event counters. Target-loss
checkpoints include the last damage type and attacker when available.

Lifecycle journals retain up to 65,536 entries. Always check `traceComplete` and
`dropped`; summary counters remain cumulative even if a trace overflows. Old cases
retain their previous buffer limits. Build artifacts are removed by `gradlew clean`.

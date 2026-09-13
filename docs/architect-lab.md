# Architect lab

For the larger native-terrain encounters and thirty-minute pursuit, see the
[wilderness lab guide](architect-wilderness-lab.md).

The lab and headless GameTests load the same structure templates and use the same
start procedure and assertions. Reset creates fresh actors and restores the selected
terrain. Recording begins before the Architect can move or mine.

## Start, run, repeat

Use a disposable creative world with cheats. Setup replaces a 21×16×21 region,
changes the lab world's gamerules, sets Easy difficulty, and freezes the whole server.
The first setup places the floor one block below your feet, extending east and south.
Rotation rebuilds the arena around its saved origin and clears its previous footprint;
rotated arenas can extend north or west. Reserve a disposable area on all sides.
Later setup/reset calls reuse that saved origin; they do not move the arena to you.
Keep the arena loaded: controls locate its marker among loaded entities, with one
lab per dimension. The requesting player is moved to the observation corner after
each rebuild.

```mcfunction
/fd architect lab setup
/fd architect lab scenario low_ceiling
/fd architect lab run
/tick sprint 850
```

A run freezes ticks and exports its result automatically when it passes, fails, or
reaches its deadline. You can advance more slowly with `/tick step 1` or
`/tick step 20`. Starting a run leaves ticks frozen until you advance them.

```mcfunction
/fd architect lab inspect
/fd architect lab dump
/fd architect lab reset
/fd architect lab run
/tick sprint 850
```

`reset` rebuilds the selected scenario, removes the old tagged lab actors, creates a
healthy Architect and villager, clears the previous latest-report pointer, and stays
frozen. It retains the scenario, seed, rotation, target mode, and arena origin. Resetting an
unfinished run first archives it as `ABORTED`. Untagged nearby mobs are not removed.

You can edit terrain while the run is prepared, then use `run`. The report hashes
the actual starting blocks so a hand-edited run can be distinguished from a clean
fixture. Another reset discards those terrain edits.

## Controls

| Command | Result |
| --- | --- |
| `/fd architect lab setup` | Configure the world and prepare the selected scenario; defaults to `low_ceiling` |
| `/fd architect lab scenario <name>` | Select and rebuild a scenario |
| `/fd architect lab seed <long>` | Set the RNG seed and rebuild; default `1337` |
| `/fd architect lab target static` | Rebuild with idle targets; melee-assertion cases are damageable |
| `/fd architect lab target live` | Rebuild with a moving, damageable villager |
| `/fd architect lab rotation <degrees>` | Select orientation and rebuild (choose one number) |
| `/fd architect lab tp` | Return to the rotated observation corner |
| `/fd architect lab run` | Start a fresh recording and activate the actors while ticks remain frozen |
| `/fd architect lab mark <label>` | Add a marker to the running journal |
| `/fd architect lab inspect` | Show outcome, planner state, counters, and recording status |
| `/fd architect lab dump` | Export an immutable snapshot, including during a run |
| `/fd architect lab reset` | Restore the scenario and replace both actors |

These require operator permission level 2. `/function frozendawn:lab/setup`,
`lab/run`, `lab/reset`, `lab/inspect`, `lab/dump`, `lab/scenario/<name>`, and
`lab/target/static|live` are aliases under the `frozendawn:` namespace. Use
`/function frozendawn:lab/help` for the short in-game guide and `lab/tp` to return
to the observation corner. Legacy `lab/origin`, `lab/arena`, `lab/clear`, and
`lab/spawn` now perform a full reset; `lab/record` starts a run.

## Assertions and deadlines

Every scenario preserves its starting floor and checks its excavation budget on
every simulated tick. The clearance and corridor tests use mineable stone floors. Deadlines include the Architect's initial warmup.

| Scenario | Required result | Tick limit | Maximum destroyed blocks | Automated coverage |
| --- | --- | ---: | ---: | --- |
| `clear_corridor` | Reach the static target; in live mode, land a melee hit | 220 | 0 | Static and live, seeds 1 and 1337 |
| `low_ceiling` | Open the departure ceiling and step out; preserve the raised ledge and unnecessary ceiling | 220 | 1 | Both seeds, all four rotations |
| `unreachable_target` | Open the two-block exit and leave the pocket despite the unreachable target | 300 | 2 | Both seeds |
| `sealed_pocket` | Abandon within 650 ticks, avoid reacquiring during the 200-tick retry cooldown, then let suppression expire | 850 | 0 | Both seeds |
| `wall` | Reach the target through the wall; in live mode, land a melee hit | 600 | 6 | Stress matrix, both seeds at 0°/90° |

The low-ceiling ledge is **mineable deepslate**, with a stone ceiling and floor.
The ledge costs more to excavate than the ceiling; opening the ceiling has a clear
cost advantage. Making both stone admits a competing front-breach route, so that
layout would not isolate the departure-clearance regression. Support preservation
is checked against breakable materials, not a bedrock substitute.

Static targets isolate navigation and clearance. Live targets exercise pursuit and
melee; the live automated cases currently cover the clear corridor. Seeds control
both actors' RNG, but do not promise identical traces across all worlds, entity
ordering, player interference, or future Minecraft changes.

## Reading a dump

Manual exports are under `<world>/architect-debug/`. In the dedicated dev client,
that is `run-lab/saves/<world>/architect-debug/`. Each export creates a unique
`run-<run UUID>-<suffix>/` directory containing:

- `decisions.tsv`: relative ticks and coordinates, action, selected step, waypoint,
  candidate rejection, break intent/outcome, recovery counters, and destroyed blocks.
- `summary.json`: run outcome/reason, full-run event totals and excavation count,
  seed, target mode, start positions, rotation, deadline, budget, fixture and actual
  starting-terrain hashes, version, and a source fingerprint including uncommitted code.

**Read `architect-latest.json` first.** Only `status: COMPLETE` identifies a completed
export; its `trace` and `summary` fields point to the immutable files. Verify
`traceSha256` when consuming it automatically. `COMPLETE` describes the export:
check the summary's `outcome` separately for `PASSED`, `FAILED`, `ABORTED`, or a
still-recording snapshot. A mid-run dump does not stop the run.

Reset publishes `PREPARED`; export publishes `WRITING` before writing, then
`COMPLETE` or `FAILED`. An empty journal is an export error. Old immutable reports
remain available, but the compatibility `architect-latest.tsv` and
`architect-latest.tsv.meta.txt` aliases are invalidated when preparing a new run or
exporting. Follow the JSON pointer rather than trusting an old open TSV tab.

The detailed journal retains the last **400 entries**, or **4096** for the expanded
environment cases. Lifecycle encounters retain up to **65,536** entries.
The report records the selected capacity. `dropped` and `traceComplete`
state whether it is a full trace. Summary counters remain cumulative for the entire
recording even when earlier rows are evicted. Excavation counts restart at zero for
each recording and count successful destruction, not selections, cancellations, or
blocks removed by something else. `stop`/terminal results seal the summary.

## Stress and monkey tests

The additional edge cases and their exact commands are documented in
[Architect stress testing](architect-monkey-testing.md). Run `./gradlew architectMonkey`
for the separate 500-case exploratory matrix. These cases are also available through
`/fd architect lab scenario <name>` after restarting the client with the new build.

## Automated gate

From this worktree:

```sh
./gradlew architectVerify --console=plain
```

This runs the full build, unit tests, fixture consistency checks, gate-validator
tests, and the headless gameplay gate. To rerun gameplay alone:

```sh
./gradlew gameTestGate --console=plain
```

Every gate starts a fresh world in `build/gametest/run/` and writes
`build/gametest/report.xml`. Immutable traces survive subsequent gate runs in
`build/architect-reports/<invocation UUID>/`; the gate prints the exact directory.
`gradlew clean` removes these build artifacts, so copy reports out before cleaning
if you want to retain them.

`config/architect-required-tests.txt` lists the required cases. The validator rejects
missing or duplicate required tests, empty reports, failures/errors/skips, missing
trace evidence, hash mismatches, and reports from another source fingerprint.
Harness tests cover preparation without AI, full actor/terrain reset, native command
execution and report invalidation, accurate destruction counts, bounded journals,
and diagnostic candidate selection.

The dev command `./gradlew runClientLab` uses the separate `run-lab/` directory and
loads GameTests. Release jars include the lab controls and fixtures, but exclude
GameTest classes. A passing headless gate covers the listed assertions; it does not
replace visual checks or reproduce an uncaptured field incident automatically.

## Promoting a new reproduction

1. Reset a nearby scenario, edit it into the failing layout, and reproduce it with
   recording enabled. Keep the immutable report, seed, target mode, and positions.
2. Before running destructive AI again, use a **structure block in SAVE mode** to
   capture the whole arena as `frozendawn:lab/my_repro`, size `21 16 21`, with entities
   excluded. Its origin is the `fd_lab_origin` marker's block position minus one Y:
   local Y=0 is the floor and Y=1 is feet. Place the structure block outside that
   volume and set its relative offset so the saved minimum corner matches the floor.
3. Save to disk. Minecraft 1.21.1 writes it to
   `<world>/generated/frozendawn/structures/lab/my_repro.nbt`. Copy that NBT to
   `src/main/resources/data/frozendawn/structure/lab/my_repro.nbt`.
4. Add the starting positions, expected escape/reach behavior, time limit, protected
   terrain, and excavation budget in `ArchitectLabScenario`/`ArchitectLabRun`. Add a
   GameTest and its exact name to the required-test manifest. Verify the broken
   behavior fails, then the patch passes, using fresh runs of the same fixture.

The built-in fixtures are generated by `tools/architect_fixtures.py`;
`--write` regenerates them and `--check` rejects drift. Change that generator when
editing those fixtures. Additional captured NBTs are not overwritten by it.

`/test create` builds a new native test arena; it does not capture this marker-based
lab. `/test export` converts an already saved native structure to SNBT. Neither
command creates a Java regression assertion. Saving the NBT and adding a failing
test are separate steps.

## Raw entity diagnostics

```mcfunction
/fd architect list
/fd architect record <entity> [seed]
/fd architect approach <entity> <target>
/fd architect reset <entity>
/fd architect mark <entity> <label>
/fd architect inspect <entity>
/fd architect dump <entity>
/fd architect stop <entity>
```

Use a UUID or a selector such as
`@e[type=frozendawn:architect,limit=1,sort=nearest]`. Raw `reset <entity>` clears
approach/navigation/recovery state; it does not rebuild terrain or replace actors.
Use `lab reset` for repeatable scenario runs. Raw diagnostics can capture an existing
field incident without relocating it into the lab.

Long scaffolding, digging, changing-world, and 3/5/10-minute encounters are listed
in [Lifecycle tests](architect-lifecycle-tests.md).

### Pursuit pause animation

When an ordinary Architect stops during approach, it looks toward the target or a
newly blocked corridor. After 6 still ticks (0.3 seconds), a small head tilt and
body lean ease in. After 32 still ticks (1.6 seconds), roughly one in three pauses
adds a left hand to the chin; that gesture has a 12-second cooldown. The left hand
must be empty and the chest slot unarmored for the elbow pose. Movement cancels the
pause immediately and the pose blends out. Mining, scaffolding, airborne movement,
water, drinking, melee, and Master behavior exclude the gesture.

After restarting `runClientLab`, visually replay `long_pursuit` and
`route_closes_travel`, then a complex obstacle field. Check the head/hand transitions
from the front and side, that pursuit resumes freely, and that mining/attack poses
and other nearby Architects do not retain the bent arm. Visual verification remains
pending; compilation alone cannot establish that the hand placement looks right.

Dumps now include `WALK_INVALIDATED` (target shift, obstruction, or stuck corridor),
`PLAN_WAIT` / `PLAN_READY`, and `PURSUIT_PAUSE_START` / `PURSUIT_PAUSE_END` with a cause
and still duration. Pose timing is cosmetic and never adds a planner delay.

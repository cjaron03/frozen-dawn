# MACS Pawn Convergence (§14)

Implemented on `feat/macs-pawn-convergence`, targeting `feat/maeve-director`. This is the Maeve 2 regional counter in the [source of truth](https://www.notion.so/39d7cfaa890181c1bad4f6babad80880), not the deferred raid system. Live acceptance is pending; automated results do not establish cloud readability, perceived pressure, or human multiplayer acceptance.

## Evidence, history and selection

Confirmed ordinary Architect deaths create dimension-scoped DANGER_ZONE hotspots with a fixed 24-block anchor. The hook runs after the native death event accepts death. Canceled death, heavy damage alone, despawn and chunk unload do not count. Master Architects, mind copies, Aggregate children, independent Hearth assessors/residents and NoAI fixture actors are rejected before an episode is created. Only victim UUID, dimension, location, overworld game time and local episode ID are recorded. Killer and damage cause are not Maeve knowledge. Real traps, lava and falls can therefore teach the same mistaken regional prediction as combat deaths.

**Six deaths and three completed encounters are lifetime totals for the retained hotspot.** They do not decay or reset when avoidance rearms. Eviction or ERASED discards that region's totals. Deaths and locally witnessed contact keep one region episode open; it closes after 600 quiet ticks. Multiple players in one fight do not manufacture extra encounters. The separate pressure weight decays immediately with a 24,000-tick half-life. Every eligible death adds 1.0; dispatch requires weight at least 3.0. Stale lifetime history alone cannot dispatch anything.

Selection happens once per second, after the local episode ends. Resumed observed contact before first arrival cancels the dispatch synchronously as UNKNOWN, before another pawn AI tick. StrategySelector compares CONVERGE and AVOID using the region's weight and confirmed wipe streak. The group stores its decision-time counts, weight, previous wipes and up to 16 actual supporting events, so a later dump does not substitute newer history for the original decision.

## Allocation and local execution

The first build redirects **existing loaded idle pawns only**. Ordinary natural rolls (every 200 ticks) and the existing one-within-96-blocks density check remain in place. The additional shared population ceiling is three ordinary pawns per eligible online player, minimum three and maximum twelve, identical across difficulty presets. Commands and independent encounter spawners do not gain a new group-spawn path. Existing actors above the cap are retained, and normal spawning waits for capacity.

This deliberately uses no special fill spawn: requests wait without a cloud until ordinary population supplies enough eligible donors. It avoids increasing ambient spawn probability to manufacture a showcase. This admission frequency needs live calibration, especially solo. The replay supplies normal actors for reproducibility; selection, population registration, warning and movement still use production code.

A dispatch needs two donors, or three when weight is at least 8.0. Donors must be 32–128 blocks from the historical region, loaded, healthy and idle, with no active commitment, recon mission, tower encounter or ongoing local combat. They retain their normal statistics. One group is active globally and owns one SIEGE attention slot. Its members cannot simultaneously receive individual Maeve commitments or reconnaissance. The actual frozen roster and donor coordinates are retained. While it is active, normal replacement spawns within 96 blocks of its donor positions are withheld.

Pawns wait through the complete 240-tick warning, then walk at the existing 0.16 local walking speed in bounded segments using collision-aware snow geometry. A forced early entry into the arrival region cancels the dispatch. The order contains a historical destination, not a live player position. Every ten ticks, each pawn checks at most eight local eligible players within 24 blocks and requires its own line of sight before handing off to ordinary local combat. It does not acquire a counter or scout as part of that handoff.

Travel expires after 2,400 ticks from cloud formation; no local walking progress for 100 ticks cancels sooner. After the first pawn enters the 24-block arrival region, the group remains tracked for at most 1,200 ticks. Empty-coordinate arrival never proves success. Failed routes, interruption and eviction visibly release loaded members using the existing disengagement controller. No teleport or forced chunk loading is used.

## Warning and outcomes

The broad ground gathering grows to a twelve-block radius and remains until the first arrival, including travel longer than twelve seconds. It emits at most 48 particles per coarse update, once per second (plus the initial formation). The exact nearby message is `A sudden feeling of presentiment hits you...`. It is sent only at formation to living non-spectator players within 48 blocks in the same dimension. Each player's ten-minute delivery cooldown and first-three-nearby-activations taper persist. Later activations keep the cloud.

Every actual dispatch has one result:

- **WIPE:** every roster member has a confirmed death and no member ever reached a player in locally valid combat.
- **SUCCESS:** at least one member reached an eligible visible player and attempted an in-range native melee attack. A player's actual shield block counts; health loss is not required. Subsequent casualties cannot turn that result into a wipe.
- **UNKNOWN:** unload, interruption, attention eviction, reload, invalid route or an inconclusive timeout. UNKNOWN neither increments nor resets the streak.

Two consecutive confirmed wipes latch AVOID. Success resets the streak. WIPE → UNKNOWN → WIPE still latches it; WIPE → SUCCESS → WIPE leaves one failure. Avoidance blocks further dispatch and ordinary spawn allocation there, releases idle local pawns, and supplies the existing danger-aware walking paths with that region. It preserves local self-defense in an active fight.

Avoidance clears only when decayed weight is **strictly below 3.0**. The streak resets and a new policy cycle begins; retained old outcomes are diagnostics only. Cooldown, reload and new players cannot clear it. New deaths that keep weight above the floor preserve avoidance. Clearing the latch alone never dispatches a new group. A 12,000-tick cooldown starts whenever a telegraphed activation terminates; a request that never forms a cloud consumes no cooldown.

A permanent per-entity dispatch-provenance marker survives reload and dispersal. Any later death of that pawn is excluded from every hotspot's count, weight, age, anchor and episode history, including deaths elsewhere. Confirmed casualties update only the current matching roster. Deaths after an interrupted/finished dispatch cannot retrospectively convert UNKNOWN to WIPE. This prevents the feedback loop even when an old pawn is reassigned later.

## Persistence, diagnostics and bounds

MaeveSavedData is version 8. Older active saves gain empty convergence memory. This remains separate from ReturnedHearthSavedData's permanent conduct ledger. Loading an unfinished group records UNKNOWN and retains cooldown and message expenditure; stale entity orders visibly disengage when loaded. Unloading never frees a population claim for duplicate replacements. Physical destruction does. Erasure clears and releases all region, roster, notification and population state immediately; debug reversal starts empty. The actor's non-knowledge dispatch-provenance marker remains so a later death cannot be reclassified as fresh evidence. Complete external backups restore their saved state.

All recording, selection and execution use the active/non-ERASED lifecycle. `ConvergenceLifecycle.isArchitectExistencePermanentlyEnded` is the single named E11 extension point; it returns false until that ending exists. This PR does not claim E11 is implemented.

`/fd maeve status`, `/fd maeve dump [player-or-uuid]` and explanations include global region history, exact eligibility failures, current utilities, decision-time evidence, roster, source positions, SIEGE occupancy, warning/arrival times, confirmed deaths, player-contact evidence, outcome, streak, latch, cooldown and policy cycle. Diagnostics remain read-only and erased output keeps the existing empty contract. Bounded retained evidence is labeled separately from lifetime counts; there is no unlimited death archive.

Bounds: 32 retained hotspots, 16 recent supporting deaths per hotspot and decision snapshot, eight past dispatches per hotspot, three members per dispatch, one active group, 128 notification histories, 256 deduplication IDs and 256 population claims. Hotspot eviction chooses oldest observed time, then UUID; the active hotspot is protected. Population claims are not evicted on unload. A command-created population overflow fails closed for normal spawning rather than granting more capacity. Coarse selection scans the bounded registry and loaded chunk availability only. Local path searches spend 80 D* nodes per pawn tick, with goals at most twelve blocks ahead and bounded standing-position candidates. The facade remains below its enforced 300-line limit.

Regions, roster and focus are server-wide, not per-player. All players contribute to the same region episode; nearby messages have independent persisted limits. A member can contact any eligible player it actually sees. Human multiplayer acceptance remains pending.

## Verification and first live replay

Required automated command, with existing fixtures and seeds:

```sh
./gradlew test --rerun architectVerify architectMonkey --console=plain
```

New tests cover lifetime history versus recency, single-fight deduplication, exact radius/cooldown/decay boundaries, persisted notification limits, bounded storage, outcome sequences, frozen decision provenance, old saves and erasure. Required native cases use real final deaths (including canceled events and exclusions), visible/occluded contact, physical snow travel after the full warning, actual shield-blocked melee contact, repeated wipes, regional avoidance, unloading, attention eviction/dispersal, reload and erasure. The generated replay functions are parsed at integrated-server permission level two. Existing bow, shield, pillar, mantlet, entrance, recon and snow-route regressions remain required.

The separate **MACS Pawn Convergence** world comes from a closed copy of the accepted exit world. The original save and traces remain untouched. `tools/prepare_macs_pawn_playtest.py` refuses to overwrite a destination. Its reset erases tactical history only in this disposable copy, activates through the real late-phase boundary and then lowers environmental hazards while preserving the activation latch. It includes snow layers and Acheronite crystals. Resistance makes the initial timing exercise safe; it does not establish combat balance.

In that new world, run:

```mcfunction
/reload
/function macs_pawn:setup
```

If frozen, run `/tick unfreeze` directly in chat first. Shoot both reduced-health practice actors through the booth's eye-height slot. Click the empty-gap skip, wait for Ready and click Next. Do **three pairs total**. No beliefs or death counters are injected. Each death uses native damage and each gap advances the full production 600-tick quiet interval. After the third pair, run `/fd maeve dump` directly in chat; expect six lifetime deaths, three completed encounters and recent weight above 3.0.

Then run `/function macs_pawn:converge`. Watch from blue at normal speed. The ordinary donor actors on the two gold positions must leave those positions only after the warning and converge on the remembered death region. Describe what happened before reading the next dump. Never sprint during warning, travel or combat. `/function macs_pawn:status` reports fixture stage; `/function macs_pawn:finish` preserves actor traces and stops the exercise. Stopping an unfinished group is deliberately UNKNOWN.

Remaining human acceptance: distinguish the gathering cloud from scout dissolution without coaching; short/long travel warning; repeat real pre-contact group kills and observe avoidance; test quiet decay/retry and actual thinning at a base; natural admission frequency; multiplayer pressure. Passing automation is not a merge claim. Keep the feature PR draft until the applicable §14.10 play criteria are demonstrated.

Initial full verification passed 640 JUnit tests, eight gate-harness tests, 198 native GameTests (193 explicitly required), and the unchanged 500 seeded stress cases plus the repeated 198 native cases. Review then tightened synchronous cancellation on resumed contact and persistence of population-overflow state, and retained decision-time utility totals. Required results are rerun for the committed revision; see the PR checks and its evidence bundle for the final result.

# Hearth Target Commitment — Design

Date: 2026-09-10
Status: approved, ready for implementation
Branch: spec lands on `fix/2.0.1-alpha-stabilization`; implementation on `fix/2.0.1-hearth-target-commitment`

## Problem

With two or more players near a Hearth, the Architect never finishes an
assessment. It is not slow — it never completes at all.

Both Hearth controllers re-pick the nearest player every tick and reset the
assessment counter whenever that pick changes:

```java
if (!player.getUUID().equals(assessmentTargetId)) {
    assessmentTargetId = player.getUUID();
    assessmentTicks = 0;
}
```

`ArchitectHearthResidentController.java:103-106` and
`ArchitectHearthAssessmentController.java:91-94`.

`ASSESSMENT_TICKS` is 60, and those 60 ticks must be *consecutive*. Two players
who alternate which one is marginally closer starve the counter forever. The
Architect also visibly ping-pongs, because `setLookAt` and `moveTo` both follow
the current tick's pick.

Three distinct symptoms, one cause:

1. Head snapping between players
2. Pathing thrash — walking toward whoever is momentarily nearer
3. Permanent counter starvation

## Behavior model

Modeled on Phasmophobia's Banshee. The Banshee picks one target and holds it
for the whole contract. It releases only on hard events — the target dies or
leaves the game — never because someone else got closer. During hunts it
ignores every other player entirely.

The property worth copying is precisely that: **release on events, never on
proximity.** Distance changes every tick; the events below do not. That is what
makes the fix thrash-proof rather than merely slower.

## Components

### 1. `HearthTargetPolicy` — `com.frozendawn.homo`

Pure static policy, private constructor, primitives in — matching
`HearthArchitectPolicy` and the 51 existing `*PolicyTest`-backed policy classes.

```java
public record Candidate(UUID id, int armorValue, double distanceSquared) {}
```

- `Comparator<Candidate> BY_VULNERABILITY` — armor value ascending, then
  distance squared ascending, then UUID.
- `static Optional<Candidate> mostVulnerable(Collection<Candidate>)`
- `static boolean warrantsRetarget(int committedArmor, int candidateArmor)`
- `static final int RETARGET_ARMOR_GAP = 8`

Lowest armor wins: the Architect singles out the weakest link. Ties break by
proximity, then by UUID so the result is stable and never coin-flips between
two identically equipped players.

Score input is `Player.getArmorValue()` alone. It already produces the intended
monotonic ladder across vanilla and mod armor, and any armor added later slots
in with no code change:

| Armor | Points |
|---|---|
| Unarmored | 0 |
| Leather | 7 |
| Insulated (tier 1) | 10 |
| Chain | 12 |
| Reinforced (tier 2) | 14 |
| Iron | 15 |
| EVA (tier 3) | 16 |
| Diamond / Netherite | 20 |
| Acheronite | 24 |

`RETARGET_ARMOR_GAP = 8` is roughly two tiers. Worked examples: unarmored (0)
steals from Insulated (10); leather (7) steals from Iron (15) and from EVA (16);
leather (7) does *not* steal from Reinforced (14). The constant lives in the
policy and is tunable without touching either controller.

No singleplayer branch. With one player the ordering degenerates correctly.

### 2. `ArchitectAssessmentCommitment` — `com.frozendawn.entity`

Package-private holder replacing the duplicated
`assessmentTargetId` / `assessmentTicks` field pair in both controllers.

State: `committedId`, `suspendedId`, `ticks`.

```java
@Nullable UUID resolve(List<Candidate> inRange, Set<UUID> onlinePlayerIds)
```

Taking plain records rather than `ServerPlayer` keeps the whole state machine
unit-testable with no `ServerLevel`.

Resolution order, per tick:

1. **Forget the dead.** If `committedId` or `suspendedId` is absent from
   `onlinePlayerIds`, clear it. Covers death and disconnect.
2. **Resume.** If `suspendedId` is back in range, promote it to `committedId`,
   clear the bookmark, reset ticks.
3. **Hold.** If `committedId` is in range, keep it — unless some candidate's
   armor is at least `RETARGET_ARMOR_GAP` points *below the committed
   target's*, in which case commit to that candidate and reset ticks.
4. **Suspend.** If `committedId` is online but out of range, move it to
   `suspendedId` and reset ticks.
5. **Pick.** Commit to the most vulnerable candidate in range, reset ticks.
   If none are in range, return null.

Also `advanceTicks()`, `ticks()`, `resetTicks()`, and `release()` — the last
clearing both ids, used on assessment completion and on damage.

**Distinguishing departure from death** is what closes the stall: a UUID that is
still on the server but out of range gets suspended, one that is gone gets
forgotten. Without that split, a committed target who logs out would lock the
Architect to an unreachable UUID forever while a player stood next to it.

**During suspension the Architect behaves completely normally** — it assesses
whoever else is in range, for real, to completion, and patrols when nobody is.
The bookmark has no behavior of its own; it is not observable from outside. It
matters only at the instant the old target re-enters range, at which point that
target wins immediately without being re-scored. A returning
diamond-armor player therefore reclaims the commitment from the leather-armor
player picked up in their absence.

Resuming restarts the 60 ticks rather than banking partial progress. An
interrupted scan starts over.

Only one bookmark is kept. If a second committed target departs while a
bookmark is already held, the newer one replaces it. This is a deliberate
simplification — the alternative is an unbounded queue of players who might
come back.

### 3. Release on damage

The two controllers currently have no damage gate. The post-Maeve resident path
in `ArchitectEntity.java:538-550` checks `getLastHurtByMob()` and short-circuits
before either controller runs, but that path is mutually exclusive with them.

Both controllers gain an early bail: if `getLastHurtByMob()` is a live entity,
call `release()` and return `false`, handing the tick to the combat brain. An
Architect being hit should fight, not scan. The retaliation itself already
exists at `ArchitectEntity.java:540-549`; this is about not suppressing it.

### 4. Wiring

Both controllers drop their field pair in favor of the holder, and build their
candidate list from the players they already stream over.

Each controller keeps its own watch radius. These are **different constants
with the same name**, and unifying them is out of scope:

- `ArchitectHearthResidentController` — `HearthPopulationPolicy.WATCH_DISTANCE` = 28
- `ArchitectHearthAssessmentController` — `HearthArchitectPolicy.WATCH_DISTANCE` = 32

`nearestPlayer` / `nearestVisiblePlayer` become `mostVulnerablePlayer`, and
`findHostileTarget` swaps its `.min(comparingDouble(...))` for the same
ordering, so assessment and combat single out the same player. Both
`resetAssessmentTarget()` and `resetAssessmentCycle()` delegate to the holder.

## Out of scope

- `ReturnedHearthWatchGoal.java:141-144` carries the identical bug, fed by
  vanilla `level.getNearestPlayer(...)` at `:83-87`. The Returned is untouched
  and keeps its bug; it is tracked separately.
- Unifying the two `WATCH_DISTANCE` constants.
- Any wilderness or debug file — those are WIP and owned elsewhere.
- Suspension flavor. The Banshee drops a roaming waypoint at its target's last
  known position; the Architect will not. It patrols normally and resumes when
  the player returns.

## Testing

Unit, in the house style (JUnit 5, package-matched, package-private class):

- `HearthTargetPolicyTest` — the armor ladder ordering, distance and UUID
  tie-breaks, and the retarget threshold at its boundaries.
- `ArchitectAssessmentCommitmentTest` — commit, hold, steal, suspend, resume,
  forget-on-disconnect, and specifically that a target alternating in distance
  never resets the counter.

Integration:

- `HearthTargetArbitrationGameTest` already exists and **currently fails by
  design**. Turning it green is the acceptance criterion for this work. It is
  the reason `gameTestGate` is presently red.

## Acceptance criteria

1. `HearthTargetArbitrationGameTest` passes; all five pre-existing gametests
   still pass.
2. Two players trading places no longer prevent assessment completion.
3. The Architect commits to the lower-armor player and holds through distance
   changes.
4. A committed target who leaves and returns reclaims the commitment.
5. A committed target who disconnects does not stall the Architect.
6. Damaging the Architect releases the commitment and it retaliates.

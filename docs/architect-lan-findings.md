# Architect LAN interruption audit — 2026-09-16

Observed on `feat/architect-visual-debugger`, base commit `116e0e8`, with the local
guest launch configuration. Host world: `run-lab/saves/New World`.

## Live evidence before the fix

Actor `52aedf40-e7d3-4730-99c7-7eeda655316c` (entity 104), journal
`f1f5b787-1940-40f5-a8f0-66fc30ce644a`:

- At run tick 100, it stood at `(1012.5, 103, 1010.5)` on two owned ice blocks,
  with another scaffold lift queued. Normal targeting was active (`targetLock=null`).
- Guest disconnect: tick 101 selected no target; tick 104 changed APPROACH to
  FORTIFY. It roamed to approximately `(1016.66, 101, 1004.80)` by tick 300.
- The visual frames still contained a queued scaffold marker at `(1012,104,1010)`
  throughout roaming. This is stale pending work, not merely a cached route.
- Guest reconnect: tick 301 selected the guest UUID and entered OBSERVE; tick 305
  entered APPROACH. At tick 312 it placed ice at the old column remotely.
- Frames 310 and 315 show a 7.36-block displacement from
  `(1016.3468,101,1004.9894)` to `(1012.5,104,1010.5)`.
- It subsequently finished climbing and landed three hits, but that does not make
  reconnect recovery a pass: the resumed stale scaffold teleported the actor.

Evidence directories beneath `run-lab/saves/New World/architect-debug/`:

```text
run-f1f5b787-1940-40f5-a8f0-66fc30ce644a-5007949872484684198
run-f1f5b787-1940-40f5-a8f0-66fc30ce644a-4589129185050694029
run-f1f5b787-1940-40f5-a8f0-66fc30ce644a-12461020214817658361
```

The remaining-player run (`74be4d57-46fc-4936-8134-2d87512c3e8c`) selected Dev and
made walking progress. It began with a newly spawned actor; the 100-tick scaffold
checkpoint was skipped. This proves basic acquisition of the remaining player,
not a mid-scaffold guest-to-host switch.

## Cause and correction

The pending scaffold destination and delay survived both target loss and leaving
APPROACH. The resolver placed support and teleported to its stored destination
without checking the actor's current location.

Cancel the pending scaffold on target loss and on leaving APPROACH. Additionally,
reject a pending/resolving lift unless the actor is grounded in the block directly
below its destination. Rejection occurs before any world mutation and records
`SCAFFOLD_CANCEL` with `TARGET_LOST` or `ACTOR_DISPLACED` where applicable.

Keep existing placed-ice ownership, the ordinary placement delay, and the local
one-block lift. No observation timing or player selection rules are changed.

## Verification scope

Four GameTests exercise target-loss cancellation, action-exit cancellation,
displaced pending/direct-resolution rejection, and successful local placement with
unchanged pacing and preserved ownership. These call the production lifecycle and
placement components; they do not simulate network login packets.

Against unchanged production code, the three interruption/displacement regressions
failed at their intended assertions; the normal-placement control and the existing
59 GameTests passed. With the correction, all 63 GameTests and all 498 unit tests
passed. The identical 500-case stress matrix also passed (563 GameTests total in
that server run, including the 63 regressions). Baseline evidence is in `/private/tmp/architect-scaffold-before.log` and
`/private/tmp/architect-scaffold-before.xml`; the verification run is in
`/private/tmp/architect-scaffold-after.log`.

## Live replay after the fix — passed

At 18:04–18:05, journal `b17cccbd-6974-46cb-8f6c-cd02815a0bfa` repeated the
partial scaffold, disconnect, 200-tick roam, and reconnect sequence. Its source
fingerprint `eb6db10de8bd68aed9fbf32b8332667f7911aa84b8dafaf0cc5c7a919c6a8c53`
matches the verified build.

At tick 101, `SCAFFOLD_CANCEL TARGET_LOST` retired the old `(1012,104,1010)` lift.
At tick 301 it reacquired the guest. Frames 305–335 show continuous walking toward
`(1016,101,1007)`, where it built a new six-block column starting at tick 337.
It reached and killed the guest with a recorded melee hit at tick 410, then returned
to FORTIFY. The largest displacement between five-tick reconnect samples was
1.112 blocks (ordinary movement/one-block climbing), versus 7.36 before the fix.
Actor health stayed at 40; the final stall and reinitialization counters were zero.
The guest had low health, so one killing hit is not a damage regression finding.

Final evidence:
`run-lab/saves/New World/architect-debug/run-b17cccbd-6974-46cb-8f6c-cd02815a0bfa-15471953760527150267/`.

## Subsequent remaining-player switch — passed

At 18:10, the same actor descended from its scaffold and pursued the reconnected
guest. At tick 588 the guest disconnected and TARGET_CHANGE selected Dev
(`380df991-f603-344c-a090-369bad2a924a`). At tick 600 it entered APPROACH;
after one NEAR_OLD_TARGET route reinitialization at tick 610, frames 620–640
show useful pursuit toward Dev. Health remained 40. Final walk-stuck,
committed-stall, and reinitialization counters were zero; no-progress was 8 ticks.

Evidence:
`run-lab/saves/New World/architect-debug/run-b17cccbd-6974-46cb-8f6c-cd02815a0bfa-9687691659240915469/`.

This verifies a disconnected guest being replaced by the remaining player during
ordinary pursuit. It continued the existing actor's descent and pursuit, rather
than interrupting a fresh scaffold ascent. That narrower live case remains a
coverage gap; it does not prevent proceeding to a normal-world gameplay soak.

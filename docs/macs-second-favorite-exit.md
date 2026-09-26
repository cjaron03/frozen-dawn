# MACS second-favorite exit

Implemented on `feat/macs-second-favorite-exit`, targeting `feat/maeve-director` in [PR #97](https://github.com/cjaron03/frozen-dawn/pull/97). Base: `317e5eab64bb1f93e2fd6663bb48a1e56b720f4c`, after merged PR #96. **Live acceptance is pending.** Exact automated results belong to the tested commit's PR checks and evidence bundle.

Contract: [MACS Source of Truth](https://www.notion.so/39d7cfaa890181c1bad4f6babad80880), §9.13f, with §§9.2, 9.3, 9.13a, 9.16–9.19 and 12. Progress: [implementation and acceptance](https://www.notion.so/3e77cfaa89018139839fdd58563e3ee7). The locked contract is unchanged.

## Behavior and evidence

At ordinary access confidence 0.75, an Architect can intercept a previously witnessed exit A. If it actually arrives and waits there, a witnessed outward crossing elsewhere can teach the response A → B. At **0.90 historical conditional confidence**, a later Architect can wait at B. Returning to A leaves it committed at the wrong exit. The variant consumes the same single encounter bet and one waiting Architect. Other families still compete on recovery cost, attention and prior outcomes.

A and B are explanatory labels for observed bearings and coordinates. This does not identify doors, rooms or the player's intent. A different second-most-used location, an issued order without arrival, an expired watch, a missing player, a failed damage trade or a geometry survey alone cannot train the conditional belief.

`SpatialObservations` still validates each crossing: the same eligible observer sees both sides of a continuous covered-to-open transition, within the existing time, movement, sight, loaded-space and 48-block limits. `ExitInterception` joins that accepted event to the player's active, arrived interception. The waiting Architect must remain alive, eligible, assigned to this player and within two blocks of its chosen point. Another eligible witness may supply the whole crossing; partial observations from different actors are never stitched together.

Ordinary interception supplies supporting evidence for its observed alternative and contradicts retained alternatives to the same original bearing. A second-favorite watch can be disproved by a different outward crossing, including a return to A; it cannot create another conditional level. Using B while B is guarded does not add support for a failed A interception. Hidden or inward crossings supply no conditional outcome. Effective damage, release, removal and reload cannot resume an unfinished episode, while already published evidence remains retained.

Ordinary and conditional confidence are independent. Alternate crossings can reduce A's ordinary confidence and trigger its existing cooldown. A legitimate later 0.90 conditional prediction does not also require A to remain above 0.75. Current-encounter observations cannot unlock a choice from that same encounter.

## Storage, selection and diagnostics

`EXIT_AFTER_<original bearing>_<alternative bearing>_<area token>` identifies each hypothesis. The area token belongs to the player's retained observed shelter in one dimension. It retains a fixed reference at that area's first observed centroid, so later centroid drift cannot silently reinterpret an old conditional bearing. Moving covered observations more than 32 blocks from that reference starts a fresh conditional area. The existing ordinary centroid calculation is preserved. Area eviction or reset prevents old conditional keys from resolving at a different base; this remains a lossy local model, not permanent building identity.

Conditional records share the existing 16 beliefs per player, eight provenance entries per belief and 128-player caps. Support remains +0.20, or +0.30 for an eligible active scout witness; contradiction remains −0.35. Existing per-encounter contribution deduplication, decay and deterministic eviction apply. Conditional threshold comparison tolerates only floating-point rounding at 0.90. Provenance includes the waiting actor, arrival tick, original watch location, actual outward bearing, crossing witness, encounter, dimension, crossing location and time.

`ExitCandidates` retains at most four spatial candidates, with a qualifying alternative replacing its parent slot. Eligible alternatives take precedence within the access family; cooldown or prior-outcome deferral can restore ordinary fallback. Existing total admission limits still apply. The local `ArchitectSpatialCommitment` executor handles approach, waiting, close defense, damage interruption and direct obstruction discovery. This feature does not change shields, pillars, mantlets, archery or general pathfinding.

SavedData version 7 adds observed area identity. Older saves retain their beliefs and world knowledge and begin without conditional exit history. Spent bets, cooldowns and contribution flags persist. Transient samples and active watches never resume across reload. ERASED clears the new tactical data with the existing store; the permanent violation ledger is separate. Server caches retain no world contents after shutdown.

Observation remains event-driven. There is at most one watch per shared player encounter, with bounded belief and spatial scans and one loaded-entity lookup for the waiting actor. No global player search, item polling, hidden movement inference or chunk loading is added. Masters, mind copies, Aggregate reinforcements and excluded Hearth roles do not execute this counter. The external facade is unchanged.

`/fd maeve dump` shows conditional meaning, confidence, retained causal provenance and watch context. `/fd maeve explain <EXIT_AFTER_... key>` explains its support and contradiction rules. Selection reports `WATCH_ALTERNATIVE_EXIT` and the original frozen evidence. A fixture counter is an exercise-progress counter; only the dump proves accepted belief evidence.

## Automated verification

The added unit cases cover five legitimate causal episodes with ordinary confidence recovery and cooldowns, shared witness contributions, independent and frozen thresholds, alternative-versus-parent selection, cooldown fallback, original-exit counterplay, no recursive keys, area/dimension isolation, centroid drift, caps, persistence, expired or absent arrivals and erasure.

Required native cases use the real presence event and actual entity movement. They train A, witness five failed A interceptions with B crossings, physically reach B in a later encounter, return through A, and verify damage release. Controls reject hidden, inward, unarrived and reloaded episodes. The real live functions are parsed by Minecraft and their booth/exit sight geometry is tested. Existing ordinary entrance approach and sealed-exit tests remain required.

Run the existing fixtures and seeds:

```sh
./gradlew architectVerify architectMonkey --console=plain
```

Automated tests do not establish visual readability or human multiplayer acceptance. A native two-observer case checks contribution isolation; it is not a live multiplayer claim.

## Separate live replay

`tools/prepare_macs_exit_playtest.py` clones a closed QA world to **MACS Second-favorite Exit**. It refuses to overwrite an existing destination. The original archer save and logs are preserved separately. Initialization erases tactical history only in this disposable copy, activates MACS at the real late-phase boundary, then uses the latched activation with safe combat conditions. Resistance protects the walking test; no beliefs are injected.

Start in the new world with:

```mcfunction
/reload
/function macs_exit:setup
```

Follow the prompts:

1. Stand on blue until GO, then walk east to gold and stop. Complete five distinct ordinary crossing encounters.
2. Between encounters, click **skip the EMPTY encounter gap**, wait for both Sprint completed and Ready, then click **NEXT**. This changes only empty-gap wall-clock time; production rates and active holds remain intact.
3. During an interception, stay on blue while the Architect approaches the usual gold exit. After the prompt, walk north to green. The next two gold practice crossings refresh the ordinary habit and consume its normal cooldown. Repeat until five witnessed switches have been exercised.
4. In the final encounter, watch for its approach to north/green. On the prompt, return east to gold. It should remain committed at the wrong exit until the replay pauses.
5. Describe the behavior, then run `/fd maeve dump` directly in chat. Preserve the world, dump and actor trace before resetting.

For an optional hidden-crossing control, run `/function macs_exit:hidden` at Ready after the fifth initial gold practice, before the first interception. A screen blocks its sight of the green crossing; that exercise does not count toward the five visible switches. Inspect a direct chat dump afterward. `/function macs_exit:status` reports progress, and `/function macs_exit:finish` aborts and preserves the actor trace. An approach timeout is explicitly reported as a failed exercise, not a pass.

The full replay checks both the existing usual-exit behavior and the new conditional counter. It must demonstrate actual causal provenance and exploitable wrong-exit waiting before this PR is ready to merge. No ordinary-action live acceptance has been claimed yet.

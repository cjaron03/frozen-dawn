# MACS second-favorite exit

Implemented on `feat/macs-second-favorite-exit`, targeting `feat/maeve-director` in [PR #97](https://github.com/cjaron03/frozen-dawn/pull/97). Base: `317e5eab64bb1f93e2fd6663bb48a1e56b720f4c`, after merged PR #96. **Solo live acceptance passed on 2026-09-26.** Exact automated results belong to the tested commit's PR checks and evidence bundle.

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

Required native cases use the real presence event and actual entity movement. They train A, witness five failed A interceptions with B crossings, physically reach B in a later encounter, return through A, and verify damage release. Controls reject hidden, inward, unarrived and reloaded episodes. All 28 live functions are parsed by Minecraft at the integrated server's function permission level 2. The practice regression runs five separate witnesses through real AI warmup and walking crossings, without restoring their targets between samples, and checks containment, absence of scouting and one accepted contribution per exercise. The generated completion selectors also reject a player whose bounding box touches a destination while their feet remain under the roof. Existing ordinary entrance approach and sealed-exit tests remain required.

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

If the world was left frozen, run `/tick unfreeze` directly in chat first. Tick commands require operator permission level 3 and cannot be placed inside these level-2 functions. The initial live setup exposed this difference from the GameTest server; the parser regression now checks the integrated-server permission level explicitly. Reloading the corrected datapack is sufficient; this repair does not change the client code.

Follow the prompts:

1. Stand on blue until GO, then walk east to gold and stop. Complete five distinct ordinary crossing encounters. At the practice checkpoint, run `/fd maeve dump` directly in chat and verify `RETREAT_BEARING_E` has at least 0.75 confidence before `/function macs_exit:continue`.
2. Between encounters, click **skip the EMPTY encounter gap**, wait for both Sprint completed and Ready, then click **NEXT**. This changes only empty-gap wall-clock time; production rates and active holds remain intact.
3. During an interception, stay on blue while the Architect approaches the usual gold exit. After the prompt, walk north to green. The next two gold practice crossings refresh the ordinary habit and consume its normal cooldown. Repeat until five witnessed switches have been exercised.
4. In the final encounter, watch for its approach to north/green. On the prompt, return east to gold. It should remain committed at the wrong exit until the replay pauses.
5. Describe the behavior, then run `/fd maeve dump` directly in chat. Preserve the world, dump and actor trace before resetting.

For an optional hidden-crossing control, run `/function macs_exit:hidden` at Ready after the fifth initial gold practice, before the first interception. A screen blocks its sight of the green crossing; that exercise does not count toward the five visible switches. Inspect a direct chat dump afterward. `/function macs_exit:status` reports progress, and `/function macs_exit:finish` aborts and preserves the actor trace. An approach timeout is explicitly reported as a failed exercise, not a pass.

The full replay checks both the existing usual-exit behavior and the new conditional counter. It must demonstrate actual causal provenance and exploitable wrong-exit waiting before this PR is ready to merge. The completed ordinary-action result is recorded below; hidden-crossing and reload controls have automated coverage, not additional human replay acceptance.

The first live replay exposed a fixture failure: the distant practice witness accepted reconnaissance and withdrew, so seven crossing exercises supplied only two accepted observations (0.40 confidence). A later scouting actor passed through the positional readiness box without an interception commitment; the next round timed out. The corrected booth is within the existing local-threat distance throughout the walking practice, has an eye-height sight slit and a low ceiling, and is removed for interceptions. No production learning or reconnaissance rule was changed. A manual confidence checkpoint prevents blindly advancing from the practice count.

For that preserved 0.40-confidence replay only, `/reload` followed by `/function macs_exit:repair` rebuilds the fixture and queues three additional gold practices without erasure. Skip each empty gap, then stop at the dump checkpoint. This recovery command resets exercise progress to the first interception; it does not claim that the earlier apparent interception succeeded.

The first repaired live checkpoint reached only 0.60. The witness kept tracking, but the original destination selectors could complete an exercise when the player's bounding box touched gold/green while the feet remained covered. Completion now starts farther beyond both roof edges. At an existing practice checkpoint, `/reload` then `/function macs_exit:training` repeats one ordinary crossing, preserves beliefs and returns to the checkpoint after the empty gap. Walk to the middle of gold before stopping.

After moving the completion region past the roof edge, the owner's next live crossing was accepted: EAST rose from 0.60/three supports to 0.80/four supports with no contradictions. This clears the ordinary interception threshold. At that checkpoint, the first genuine interception and conditional outcome still awaited a live dump. The purple-eye report remains a visual follow-up: renderer class files and both normal/blink textures were byte-identical to the accepted archer jar; logs confirm the earlier actor was actually scouting.

The subsequent live rounds selected ordinary `WATCH_ACCESS_POINT` with recon eyes off; EAST reached 1.00. Their north escapes still produced no NORTH or conditional belief. The original decorative posts obscured the diagonal from the executor's actual stopping position (near x=3017 for remembered x=3016), which the earlier manually positioned x=3015 witness missed. The fixture now places support posts at roof corners. The regression approaches the remembered point with real entity physics, checks for the original posts' occluded portions of the green strip, verifies all sampled lanes become visible with corner posts, then verifies an actual crossing becomes causal evidence. The original layout was not opaque from every exact lane; the live failure was sensitive to position. `/function macs_exit:resume` rebuilds only the fixture, preserves all learned beliefs, resets the uncredited alternate-exercise count, and queues a fresh interception after the usual empty gap.

The corner-post live replay produced its first verified conditional event: `EXIT_AFTER_E_N_09B170F3BF103B33AD3D03EB1811032C`, confidence 0.20, one support, zero contradictions. The same ordinary actor arrived at the EAST watch at tick 820064 and witnessed the NORTH crossing at tick 820130; provenance records `FAILED_PRIMARY_INTERCEPTION`. EAST fell from 1.00 to 0.65 with one contradiction. Trace and dump are retained in `build/macs-exit-evidence/corner-post-live-20260926-034338/`. That checkpoint validated one causal episode; the completed replay below supplies the remaining four and return-to-original counterplay.

## Completed solo acceptance — 2026-09-26

The owner reported “it did it!” after the final encounter. The direct-chat dump contains five distinct `FAILED_PRIMARY_INTERCEPTION` events, each joining an arrived EAST/GOLD watch to a witnessed NORTH/GREEN outward crossing. They supplied +0.20 each, reaching 1.00 historical conditional confidence. The next encounter selected `WATCH_ALTERNATIVE_EXIT` and sent ordinary actor `59d18fc4-c3dd-49d4-8031-73c66ab0055e` to NORTH/GREEN `(3008,101,3001)`, arriving at tick 834563. Recon eyes remained off.

The owner then returned through EAST/GOLD. The same actor witnessed that outward crossing at tick 834640; `WRONG_ALTERNATIVE` subtracted 0.35, leaving conditional confidence 0.65 with five supports and one contradiction. The trace stayed at NORTH/GREEN with the commitment active as the player moved east, through the fixture stop at tick 834675. This establishes the learned prediction and its exploitable wrong-exit commitment; the fixture pause does not establish the later natural release. Strategy performance remained UNKNOWN because this walking exercise had no damage trade.

The final dump and all six causal encounter traces are preserved in `build/macs-exit-evidence/accepted-live-20260926-035048`. The earlier fuzzy-purple-eye report remains an unresolved visual follow-up, and this solo result does not claim multiplayer or manual hidden-crossing/reload acceptance. Production behavior was unchanged by the replay repairs.

## Final recovery and persistence checks

The owner reopened the saved world before further exercise and ran a direct chat dump on 2026-09-26 at 22:44. Conditional confidence remained 0.65 with five supports, one contradiction and the same causal provenance. The policy reported `RELOAD_RELEASED` and `betUsed=true`. The preceding fixture had already stopped the actor; the new required native case separately serializes a genuinely active conditional watch and verifies that reload cannot resume it.

For one additional live recovery check, run `/reload`, then `/function macs_exit:recovery`. This preserves beliefs and queues a new ordinary EAST/GOLD watch using the retained 0.85 ordinary confidence. Skip only the empty encounter gap, then click Next. Stay on blue until prompted, walk north to green, and remain there without attacking or changing time. The real 400-tick hold runs to its deadline. The fixture saves the trace when the Architect returns within 2.8 blocks of the player, or explicitly reports a timeout after 650 ticks. A proximity stop alone is not proof: the trace and dump must show natural expiry, a cleared guard cue, ordinary pursuit, and no second commitment or scout. This tests the shared spatial executor; the conditional variant has an additional native end-to-end expiry-to-damage case.

The required `exitAlternativeExpiresIntoPursuitAndReloadPreservesDisproof` case repeats all five ordinary causal episodes, selects the alternative, observes return-to-original counterplay, and ticks actual AI through the full guard deadline. It requires subsequent pursuit and an actual melee hit without any player attack, no new commitment or scout, preserved disproof on a saved mid-hold snapshot, and a spent bet after reload. The original damage-interruption case remains required. The live recovery passed as recorded below; final revision check results belong to the PR status checks.

The live recovery follow-up also passed. The ordinary Architect arrived at tick 843546 and held through its real tick-843946 deadline (400 ticks). The dump reports `TIME_COMPLETE`, with the bet still spent. In the actor trace, `OBSERVE` changed to `APPROACH` at relative tick 472, to `ATTACK_MELEE` at 515, and recorded a melee hit at 526 without any player attack; the guard and recon flags were both false during pursuit. The replay's Resistance kept this walking test safe. This verifies physical recovery rather than only policy expiration. The direct reload dump, live recovery dump and actor trace are retained in `build/macs-exit-evidence/pre-followup-20260926-224426/`.

The expanded native gate passed all 190 cases (185 explicitly required). Its first attempts exposed test setup assumptions: NeoForge FakePlayer rejects ordinary damage, and the damageable player can lose health during the preceding training sequence. The recovery assertion now uses the existing damageable fixture and compares against health at the beginning of the hold. It still requires no damage during the hold and a real subsequent hit. Production AI was not changed for these checks. The final required gates must be reported against the committed revision.

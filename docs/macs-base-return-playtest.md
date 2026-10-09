# Controlled base return acceptance

This closes the remaining causal gap in §14.10: existing eligible pawns near a base should physically depart for a qualifying decoy and leave a useful return window. It complements the completed natural-recruitment and masked-presentation checks. Production Java, tuning, donor eligibility, detection, population and attention rules are unchanged.

## Headless comparison

Two required native cases use identical flat snow terrain, actor random seeds, a Survival participant, starting positions and a fixed player itinerary. Both start with two ordinary idle pawns about 60 blocks from the base and the player more than 96 blocks from either pawn. The control earns two real death encounters; the decoy arm earns three. Each uses ordinary deaths, quiet time and the production coordinator. No order is injected.

The player starts returning after 600 ticks and reaches the base footprint after 1,060 ticks. The control must record actual target selection and physical pursuit within 32 blocks of that footprint. The decoy arm must admit the original two UUIDs, record arrival at the decoy, move each more than 32 blocks, and keep both beyond ordinary detection for at least 500 ticks of the base window. It also checks that the donor area cannot immediately replenish those actors. Distances are fixture assertions, not new gameplay or acceptance rules. The control is open ground; it does not claim a tested breach through the live shelter.

A third required native case verifies missing-member refusal, real late-phase Survival before release, unchanged tactical history, the thirty-second admission bound, living-actor cleanup and restart refusal. All replay functions parse at the client's permission level.

## Live copy and staging

World: **MACS Base Return**, a fresh full copy of the closed Natural Supply archive (SHA-256 `840ecc814af0c5146fd526fa18dead9967a7e64d34a29410d51b0584c2862a05`). Completed worlds remain preserved. `return_prepare` requires stage92 and both living original actors held with NoAI. It builds solid ground, a heated base at (3881,101,4006), and a supply outpost at (3881,101,3914). It stages the two saved Architects at (3941.5,101,4002.5) and (3941.5,101,4010.5), about 60 blocks from the base. This is controlled positioning before the run, not a new natural-spawn claim. Their UUIDs and earned hotspot history are retained; no actor is added or killed.

Original west: `3c74d400-ef46-4551-a264-157ed46407b4`. Original east: `caa24924-f12a-43f0-bf55-f9c314287273`. Decoy anchor: (4003,101,4006). The whole route is within the ordinary twelve-chunk simulation area. Preparation removes its temporary chunk tickets.

The operator checks the loaded world, stage, roster, position, kit, core/heater and retained history while menu pause plus tick freeze prevent simulation. Preparation must run only once. Start switches to ordinary late-phase Survival before releasing either actor. There is no Creative admission period, forced assignment or pawn movement after release.

## One bounded owner pass

1. Open **MACS Base Return** and pause. The operator performs guarded preparation and records the preflight.
2. Start at the supply outpost. Collect coal and raw iron; remain there until the fixed **RETURN HOME** message at thirty seconds.
3. Follow the lanterns south to the base. Close the airlock, refuel the heater, and smelt one iron. Keep the suit on and defend normally.
4. At ninety seconds the helper holds the surviving actors, switches to Spectator and ends. Pause and describe whether the return/upkeep was possible and what the Architects did before reading diagnostics.

No group by thirty seconds or a player death ends the run as inconclusive. Leaving the outpost early is recorded and invalidates the matched itinerary. The return message uses a fixed clock; player movement remains under owner control. Admission is recorded separately from physical departure, base arrival, useful work and combat outcome. The helper does not automatically label the session a pass. End-of-test cleanup is not a wipe or combat success.

Exact-world bridge commands: `return_prepare` at92; `return_status` and `return_start` at130; `return_end` at132. Running stage132, completed stage133. Scores use `#return_*` on `mpc`: timer, dispatch, arrival, near (originals within96), all_near (all Architects within96), alive, assigned, clear_ticks, left_early, result. `clear_ticks` is coarse twenty-tick sampling while the player is within eight blocks of the base; traces and owner feedback remain authoritative.

Preserve snapshots, scores, Maeve dump, both actor traces and the owner's first description. Archive the whole world only after it closes. Do not restart, erase history or rerun the finished scenario to improve the result. This check does not authorize merge or modify the owner's baseline-reconciliation branch.


## Verified preflight

`architectVerify` passed 644 unit tests, 214 native cases/all 209 required entries, 8 harness tests and 116 replay-function parses. Control: actual targeting, nearest base distance 2.6315. Decoy: no targeting, nearest base distance 120.6029, both donors traveled over 60 blocks, first arrival tick 500, all 541 base-window ticks clear. Full evidence and SHA-256: `build/macs-pawn-evidence/base-return-20260930/verification/verification.json`. The completed live result is recorded below.


## Controlled base return result — 2026-09-30

The owner first reported “well i got the iron, but nothing happened after that,” then clarified that they smelted an ingot at the base. The first account preceded diagnostics; the clarification followed them. Useful work is confirmed; heater refueling is not claimed. The test ended normally at 90 seconds/stage 133. Production dispatched the two original controlled-position pawns after nine ticks; first arrival was at run tick 509, and both were at (4002,101,4006) by group completion at tick 1709. Both traces are complete with zero drops, no player target selection and no contact. Both finished at 40 HP, about 108.4/108.7 blocks from the base after beginning about 60 blocks away. History stays ten deaths/three encounters, zero wipes. `UNKNOWN:ENGAGEMENT_ENDED` is the combat outcome for this empty decoy, not a failed diversion.

The player reached the base at the 1,040-tick sample (52 seconds); 780 ticks of coarse samples (39 seconds) show both originals outside 96 blocks while the player was within eight blocks of the base. The early-outpost-departure flag is 1, so the strict manual itinerary was not matched; the exact departure tick is unavailable. This variation is preserved rather than erased or silently treated as an exact paired human trial. The independent matched native comparison supplies causal pressure evidence, while the live run supplies actual departure and useful smelting. Together these meet the controlled singleplayer diversion scope. Ambient frequency and human multiplayer remain unmeasured calibration/integration concerns. No automatic further replay is proposed.

Evidence: `build/macs-pawn-evidence/base-return-20260930/result/analysis.json`, original owner report, stage/scores, dump, both complete trace folders and client log. The full native pair and gate remain under `verification/`. The finished world is menu-paused and tick-frozen, with actors held. Owner may save and quit; preserve the full closed-world archive afterward. Do not run preparation, start or end again. No source change, commit, push, Notion update or merge followed the live run.


### Closed base-return archive — 2026-09-30

The owner saved and quit after the completed pass. Bridge status confirmed no loaded world before and after archival. All 868 files were checked against the archive and the source stayed unchanged. Archive: `build/macs-pawn-evidence/base-return-20260930/result/closed-base-return-world.zip`. SHA-256: `df5ddfd01d1eed5b758139c819c116d0c5d08400f1c25ac1b06b54655916ea71`. Full source hashes and metadata are in `closed-world-backup.json` beside it. No finished encounter was resumed. The client remains at the title screen.

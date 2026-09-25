# MACS PR review — defensive construction and attribution

This follow-up to [PR #94](https://github.com/cjaron03/frozen-dawn/pull/94) investigates the two retained terrain observations before further tuning. Baseline production is commit `f3e5474163387e9ac796a179d42e60cbd5aebcc2`. It also checks the review's proposed shield-result attribution failures through native damage hooks.

## Defensive construction

The round 9 journal contains four `inWall` damage events during ordinary retreat. The native reproduction places the Architect near a block edge, lets its real utility handler retreat, build cover and drink a potion, and observes five suffocation hits before the repair. A rounded cover offset can overlap the builder's actual collision box even when their block positions differ.

Tactical ice now checks the proposed cell for entities that block construction before removing plants or evicting the previous wall. The query stays within that cell, uses loaded chunks, and stops after the first qualifying entity. The fixed reproduction has no suffocation or health loss and still builds cover and heals. A separate test verifies that rejection preserves both vegetation and the old bounded wall, while a dropped item permits construction and normal eviction. Navigation scaffolds retain their separate footing rules.

The round 10 journal shows pursuit destroying the upper block of a wall 17 ticks after placing it. A flat-ground native reproduction with a cached route exposed a related failure: the Architect started mining its own new wall, canceled four ticks later, then walked around. Its own `setBlock` calls did not trigger the player-placement route notification. Construction now explicitly repairs the local cached route for the new block and any evicted block. The same reproduction immediately chooses the walking corridor, preserves both wall blocks, and reaches the player. A raised-target control also passes; it already passed before the repair, so it does not establish the exact cause of the original upper-block destruction.

These changes preserve confidence, cover cadence, commitment duration and shield execution. They grant no special immunity to Architect-built walls: genuinely obstructing cover can still be removed through ordinary pathing. There is no save-schema change, new global scan or chunk loading.

## Shield review findings

Both reported attribution failures were tested against unchanged baseline production. All five cases passed:

- Exposed shield hit by another player's axe.
- Exposed shield hit by a mob's axe.
- Death caused by another player.
- Death caused by a mob.
- Environmental suffocation death.

Each case first records six damage blocked from the intended subject, advances to an actual exposed window, then delivers unrelated native damage. The physical shield is cleared, exactly one UNKNOWN result retains the earlier blocked amount, failures remain zero, and the encounter remains spent. The existing subject-caused victory case remains in the required suite as a control.

NeoForge's final-damage callback runs inside `actuallyHurt` before the outer `hurt` method processes death. `MaeveObservationEvents` records incoming damage before calling shield interruption. `LearningCoordinator.incoming` therefore resolves unrelated damage as UNKNOWN before axe cleanup or `die` can release the shield. Later cleanup cannot score the same pending result again. No production shield change was needed for these review findings.

## Verification and evidence

The refined baseline ran 150 native cases with two expected failures: retreat suffocation and the needless fresh-cover mining attempt. The fixed run, including the additional occupancy/eviction case, passed all 151 native cases with no failures, errors or skips. All nine new cases are in `config/architect-required-tests.txt`. Full required gates run on the final commit with the unchanged stress seeds and shared fixtures; exact-commit results are published on the PR.

Local evidence is retained under `build/macs-review-evidence`: original replay-input hashes, baseline and fixed XML reports, logs, and the baseline test sources. Original rounds 9 and 10 remain in the integrated-playtest worktree's `build/macs-integrated-evidence/broader-20260923` bundle. The earlier shield live acceptance and its source hashes remain unchanged in `docs/verification/macs-calibration.json`; that acceptance does not claim a live test of this terrain repair.

## Focused live replay still required

Restart the client with the revised jar and use **MACS Camp - Brutal** with its retained history. Enter/refill the camp and start an ordinary round. Use the bow from flat ground and from a one-block rise, move around its cover, and let the Architect retreat and heal. Check for self-suffocation, needless mining of fresh cover, and continued pursuit around walls. Finish the encounter, run `/fd maeve dump` directly in chat, and preserve the journal before any reset. A clean replay can confirm the terrain repair; it is not a new difficulty calibration or blind integration result.

# MACS PR review — defensive construction and attribution

## Two-block snow cover, budget trial and pursuit recovery (2026-09-24)

The owner accepted bow-cover placement in the frozen camp, then requested two-block pillars and a larger ice pool. Ordinary Architects now share twelve tactical blocks across their existing cover actions, up from six: up to six complete two-block pillars when all capacity is available. Construction still needs its existing cooldown, movement, visibility and geometry checks. Oldest-first recycling and the commitment's free-capacity check remain in force. Masters retain six blocks, navigation scaffolds retain their separate limit, and retreat/healing keeps its existing controller and placement path. No additional per-Maeve pool or save-schema change is introduced.

Bow fortification and ranged commitments accept all vanilla snow-layer depths over supported ground, including caps on deeper full-block drifts. They preflight the entire two-block pillar before replacing snow. A lower-torso sight line must actually intersect the proposed cover; the upper torso can remain exposed on deep snow. This explicitly replaces the prior three-block extension. Solid obstacles, crystals, fluids, powder snow, unsupported gaps, entity occupancy and Stillpoint suppression remain exclusions. Candidate search remains twelve local positions without chunk loads, projectile prediction or held-item reads. The ranged hold still resolves its stance to the real snow collision surface.

The two accepted-placement replays are `c238df14-4060-4929-90e2-4371170f7f79` and `46a6c546-7ac6-4bb0-aed4-7906e3a3d9b9`. The first retained a long circling stall near `(2051,101,2029)` while pursuit repeatedly selected the cell above a four-layer snow surface. Its no-progress count reached 402 with zero replans; the later three-block pillar was built after circling had started. The second replay's retained maximum was 19 ticks. A native regression uses the closed save's local snow profile and a retained elevated goal. Baseline production failed to reach the player after 480 physical ticks and never replanned. Small nonzero velocity was suppressing progress recovery despite remaining inside the two-block progress anchor. The existing 160-tick replan deadline now overrides that velocity exemption; shorter ordinary movement keeps its grace. The fixed reproduction replans at no-progress tick 160 and reaches the player at simulation tick 213. This bounds the reproduced circle rather than claiming that every possible pathing loop is eliminated.

Native coverage checks real fortification and lower-torso arrows at all eight layer values over zero, one and two full snow blocks, real ranged-history commitments at every layer value, entity/terrain exclusions, twelve-block capacity, and oldest-first recycling after entity NBT reload. The pursuit reproduction is in the required GameTest manifest. Confidence tuning, evidence rules, construction cadence, shield execution and retreat construction logic are unchanged.

The original four-round evidence remains under `build/macs-snow-cover-evidence`. This follow-up's two replay traces, terrain extract, hash-verified closed-world backup, baseline failure and final reports are under `build/macs-ice-trial-evidence`. Fresh required-check results belong on the PR's exact commit. Live acceptance of the two-block/twelve-block trial and revised circling recovery is pending.

Continue in **MACS Frozen Camp - Brutal** with `/function macs_trial:enter`, optional `/function macs_trial:refill`, then `/function macs_trial:start`. Preserve its history and terrain. Use the bow while crossing drifts, then run `/function macs_trial:finish` and `/fd maeve dump` directly in chat. Report whether the extra retained walls improve pressure, whether upper-body shots still give counterplay, and whether a temporary stall recovers. Cover is still conditional on history, outcomes, local geometry and the finite shared pool.

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

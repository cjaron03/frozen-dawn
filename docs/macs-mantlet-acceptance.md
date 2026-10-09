# MACS advancing cover — mantlet acceptance

Implementation of [§9.13c](https://www.notion.so/39d7cfaa890181c1bad4f6babad80880), with the [implementation plan](https://www.notion.so/3e67cfaa8901816f8534e1f67fe188ea). Branch `feat/macs-mantlet`, based on integration `ee2a18b` after PR #94. The applicable solo live checks are complete; see the final acceptance record below. [PR #95](https://github.com/cjaron03/frozen-dawn/pull/95) publishes required-check results for the final commit. Live multiplayer acceptance remains deferred by the owner.

## Behavior and initial tuning

Only ordinary Architects can select this variant. The same witnessed projectile-damage belief supports both pillars and mantlet; the mantlet requires **0.90 frozen historical confidence**. Confidence from the current encounter cannot upgrade it. Below 0.90, the existing 0.75 pillar gate applies.

Historical bow evidence selects the counter. The local executor proposes a safe fixed corridor toward the subject it can currently see, at least ten horizontal blocks away and within the ordinary 48-block observation range. It retains this exact local proposal when the commitment is issued; later movement cannot rotate or replace the corridor. Hidden subjects cannot supply their current position. It preflights five screens and four short forward steps in loaded space. A screen is two columns of packed ice, each two blocks high. The direction stays fixed even if the player flanks or changes weapons.

Each block takes at least 10 ticks; screen starts are at least 80 ticks apart. A new screen sits two blocks beyond the previous one. After completing it, the Architect visibly breaks down only its tracked previous screen, restores displaced snow there, and advances slowly at up to 0.09 blocks per tick. Both flanks remain open. Waiting between mantlet construction steps uses a ready combat posture. Pillars return to local pursuit and melee immediately after placement, without a stationary thinking hold. The thinking gesture remains for deliberate positional watches, reconnaissance and actual pursuit pauses.

One bet can place at most twenty blocks in a separate mantlet pool; retiring blocks does not refund capacity. A full unused mantlet pool is required at admission. Ordinary pillars and retreat keep their existing twelve-block tactical pool. Total retained ordinary tactical plus mantlet positions are therefore bounded at thirty-two per ordinary executor. Breaking any retained screen cell ends the mantlet and immediately resumes local pursuit/melee without repair. A valid mining start on a current/building owned cell that the executor can see triggers the same handoff before destruction; it need not be hit first. Effective damage ends this commitment under the existing local-defense rule. Otherwise its twenty-second timer still runs, including construction, movement and the final hold.

Geometry failure before selection leaves pillars eligible. Failure after selection spends the bet and returns to local behavior. No re-selection into a different commitment is allowed in that encounter. Existing ordinary utility actions remain possible after the bet ends.

## Selection and learning

Choose the eligible ranged variant with the better frozen strategy effectiveness; an untried tie prefers mantlet. Pillars retain their original strategy key, while mantlet has a separate `PLAYER_PREFERS_RANGED:MANTLET` result context. This is a strategy identifier, not a new player belief or counter family. Variant selection happens before comparing recovery cost with other counter families: cheaper abandonment still takes precedence. Initial recovery costs are pillar 2, mantlet 3, and sword guard 1.5.

The existing two-failure deferral applies per strategy variant. Belief contradiction cooldown applies to the shared ranged belief and blocks both variants. Repeated arrow collisions with ice are not fabricated into damage-prevention scores: existing witnessed damage trades determine strategy results; silence or interruption remains unknown.

After pillar placement, witnessed combat continues to contribute throughout the original active commitment window even when pursuit moves the executor more than three blocks from its initial position. The same executor, subject, dimension and observation rules still apply. Deliberate stationary watches retain their three-block scoring radius. The final incoming hit is recorded before local defense ends the bet.

## Persistence, erasure and multiplayer

Maeve SavedData version 5 accepts previous versions. Old ranged strategy contexts remain pillar contexts; a missing mantlet context starts neutral. Future unsupported schema versions fail explicitly. The eight-context / eight-result bounds still apply.

The entity saves up to twenty charged `MantletIce` positions for cleanup and allowance accounting. Older entities load an empty mantlet list; older trial mantlet blocks retain their original `TacticalIce` cleanup ownership. The local screen plan is transient. Reload cannot resume it or refund the spent commitment. Its ice uses a separate entity-owned list and the existing construction cleanup path. Erasure removes variant memory and stops local execution. The permanent violation ledger is separate.

Beliefs and strategy results remain per player and dimension. An active mantlet stops on subject loss/change, unavailable executor, unsafe environment or attention eviction. One player encounter cannot buy multiple bets through additional observers. Master Architects and excluded roles keep the existing exclusion boundary. Live multiplayer acceptance is not claimed.

## Performance bounds

No chunk generation, global search, inventory scan, projectile prediction or asynchronous work. The proposal considers exactly five four-cell screens, five standing points, and short straight walks of at most three blocks. Each walk samples four times per block. Collision surface lookup checks four vertical cells. Placement and live collision validation recheck loaded state and occupancy. Placement entity queries stop at the first blocking occupant. Mining-start cues query at most sixteen living local Architects within twelve blocks. They validate server-side reach, game mode, cancellation, protection and loaded space before inspecting an active executor's at-most-eight current/building cells. A loaded-only ray to the attacked owned block must hit that cell. No miner identity or position is passed into execution, no hidden miner is acquired as a target, and no player-pattern observation is generated. Plan attempts normally use the existing once-per-second cadence; high-confidence spawn landings retry the grounded gate on the next tick.

Retained execution data is one five-screen plan, up to eight active screen cells and at most twenty displaced states. Strategy memory retains its existing caps. At each retirement, a bounded local query inspects at most thirty-three nearby arrows; more than thirty-two ends the advance with the old screen intact. Only arrows lodged in retiring ice are converted to pickup items (or discarded when pickup is disallowed); flying arrows and tridents are untouched. No arrow trajectory informs tactic selection. Sword, navigation and retreat tuning remain as before. Ordinary repeat pillar construction retains its existing movement, spacing and twelve-block pool.

## Live handoff

Open **MACS Mantlet - Normal**, then:

```mcfunction
/function macs_mantlet:setup
```

After Ready, click Practice or run `/function macs_mantlet:practice`. Shoot the Architect through the booth window once. Click the yellow empty-gap sprint, wait for both Ready and Sprint completed, then repeat. Five real projectile-damage encounters teach the historical ranged belief; no confidence injection occurs. An optional pillar comparison is available after four practice hits.

After the fifth empty gap:

```mcfunction
/fd maeve dump
```

Verify the observations, then start:

```mcfunction
/function macs_mantlet:start
```

Let it make its opening move before the first shot. Test whether the front actually screens arrows, whether you can flank it, and whether breaking a screen stops construction. Record impressions before reading another dump. Finish exports and removes the remaining actor; it is not counted as a player victory.

```mcfunction
/function macs_mantlet:finish
/fd maeve dump
```

For another encounter, preserving history:

```mcfunction
/function macs_mantlet:again
/tick sprint 620t
```

Wait for Ready and Sprint completed, then `/function macs_mantlet:start`. Never sprint an active encounter. Emergency command: `/function macs_mantlet:abort`.

## Evidence and remaining gates

The completed guard world was copied before installation to `build/macs-mantlet-evidence/pre-mantlet/`; the new trial lives in a separate worktree and save.

Automated checks cover frozen threshold boundaries, variant outcomes, legacy state, reload, budget, cadence, actual arrow collision, physical movement on snow, interruption, erasure and the live datapack's actual reward trigger. The required manifest includes the new integration tests. The development run passed 614 unit tests, 169 native GameTests (all 164 named required cases), and all 500 unchanged seeded stress cases; the stress server also repeated the 169 native tests. The native mantlet case built exactly 12 blocks in three screens, advanced about 3.87 blocks, and embedded an actual arrow in packed ice without taking damage. All eight snow depths passed. A final review moved a pre-placement block read behind the loaded-chunk check; the committed candidate receives a fresh full gate run, with exact source/artifact hashes published on its PR.

The development checks and early live revisions below are historical. The final acceptance record establishes readable advancing cover, useful frontal protection and usable counterplay. Broader terrain, multiplayer and performance testing remain distinct from a passing solo demonstration.


## First live round and transition revision

The first live round on `4d3c72a` built two screens/eight blocks. Seven ticks after retiring the first screen, the executor took one point of raw arrow damage and released the bet as `LOCAL_DEFENSE`; health became 39.18. The unobserved hit left strategy performance UNKNOWN. Budget exhaustion did not cause this release. Evidence and the closed world are preserved under `build/macs-mantlet-evidence/pre-transition-fix-2026-09-24`.

A freed embedded arrow is the working explanation, not a confirmed projectile identity. The trace did not retain IDs/trajectories, and initial retained-arrow and snow/impact-height reproductions did not reproduce the hit. The revised retirement explicitly handles lodged arrows before removing their support. Tests retain real arrows through the entire transition, verify recovery of the item and continued vulnerability to fresh shots, and check five screens, snow, independent budgets, persistence and cleanup. The twenty-block mantlet allowance was requested after this round; the original three-screen evidence above is historical. Live acceptance remains pending.

## Second live round: local orientation and cover presentation

On `5c9a016`, the mantlet proposal failed at `rangeOrBudget distance=5.0990195135927845 used=0`. The current player was visibly sixteen blocks away, with frozen ranged confidence 1.0, but geometry had used the previous fight's last shot at (2403, 101, 2411). The actor selected `HOLD_RANGED_COVER`, completed its 400-tick hold, and showed the inherited thinking gesture. The closed replay is preserved under `build/macs-mantlet-evidence/pre-visible-front-fix-2026-09-24`.

The correction separates strategic evidence from local execution geometry: history still admits the bet, but a currently visible subject supplies its initial direction and range. The local plan is frozen at admission and cannot follow a later flank or obtain a hidden target position. Mantlet construction waits now display readiness rather than the thinking gesture. The owner then removed the stationary pillar hold: the initial pillar immediately yields to ordinary pursuit and melee. The same issued bet and bounded combat-outcome window remain for learning, without restricting movement. Effective damage still records the trade and any contradiction before ending the bet; no replacement counter is purchased. The six laws and deliberate entrance/recovery/withdrawal watches remain in force. §§9.13a and 9.13d were updated and read back to verify this decision.

## Active pillar regression development

The new movement fixture initially checked arrival before the existing planner cadence had run and used the default damage-immune FakePlayer. A close-range retry exposed that fixture immunity; the final case uses the existing damageable player helper and native damage hooks. Those failed attempts are retained under `build/macs-mantlet-evidence/active-cover-development/`. The final scenario allows normal spawn settling, requires physical pursuit away from the constructed pillar, then has the player close into melee without first damaging the Architect. It requires an actual melee hit within the original outcome window, no positional holding, and no replacement bet. The snow test permits ordinary combat jumps and checks a single cover-to-combat handoff across all eight layer depths. These fixture corrections did not change production movement or attack tuning.


## Accepted advance and breach regression (2026-09-25)

On `6b1c6f6`, run `de1a0128-4885-4010-be90-9448cc98c51d` built all five screens/twenty blocks, recovered three lodged arrows during retirement, completed its 400-tick commitment and took no damage during that execution window. The owner reported that it worked well. Evidence is retained in `build/macs-mantlet-evidence/live-6b1c6f6-de1a0128/`. This supports the visible advance and frontal protection; it does not establish every counterplay case.

The next replay, `8a1ef79f-746a-4bbf-806c-ddcd61ac571b`, exposed the breach bug: `MANTLET_BREACHED` at relative tick 103 stopped construction but retained OBSERVE until a player hit at tick 203. The fix releases the spent commitment on a missing screen cell or valid visible mining start, clears its holding presentation and resumes local combat. It neither repairs the wall nor selects a new counter. The mining event carries only the attacked block into the local executor, never an unseen miner's identity or position. Unknown outcome remains unknown without a witnessed damage trade.

Native regressions require physical pursuit and a real melee hit without first damaging the builder. They also exercise the actual server mining entry point before destruction, canceled/denied/reach-restricted mining, Adventure restrictions, abort/stop messages, an occluded cell and unrelated ice. Existing seeds and fixtures remain unchanged.

The closed replay and complete world were preserved under `build/macs-mantlet-evidence/pre-breach-combat-fix-2026-09-25/`. Its observed melee contradiction lowered ranged confidence to 0.65 and is preserved. **MACS Mantlet Breach - Normal** is a separate complete copy of the earlier `pre-visible-front-fix-2026-09-24` external world backup, with its real 1.0 historical bow confidence and saved strategy history. Only its display name and current datapack differ. Its Maeve and scoreboard data match that backup byte for byte; the preparation record is `build/macs-mantlet-evidence/breach-replay-preparation.json`. This is an external saved-state restore, not an in-world belief reset or confidence injection.

For this copy, run `/function macs_mantlet:again`, then `/tick sprint 620t`. After Ready and Sprint completed, run `/function macs_mantlet:start`. Let the first wall appear, approach and start mining one of its blocks with the pickaxe **without hitting the Architect**. It should abandon construction and pursue/attack; it should not wait for a hit. Then use `/function macs_mantlet:finish` and `/fd maeve dump`. The subsequent acceptance runs below completed the remaining solo checks; multiplayer is not claimed.

## Final solo acceptance and scoring review (2026-09-25)

| Check | Evidence | Result |
| --- | --- | --- |
| Advancing front and frontal protection | `6b1c6f6`, run `de1a0128`: five screens/twenty placements, full 400-tick window, three lodged arrows recovered, no damage during execution | Owner accepted the visible advance and protection |
| Mining handoff before the builder is hit | `d99f7dd`, run `b5c0cdf9`: visible mining, release and APPROACH at tick 121; melee hit at 155; first received damage at 160 | Immediate pursuit and melee, no later screen or replacement bet |
| Intact-wall flank and damage interruption | `d99f7dd`, run `a8da8ff3`: fixed front through the flank; exposed-side arrow at 171; melee at 285; all eight placements preceded the hit | Owner and trace agree; the wall did not rotate and construction ended on damage |
| Ordinary pillars resume combat immediately | Explicit owner confirmation from prior live play | Accepted as an owner report, not attributed to the mantlet trace |

The mining and flank evidence bundles are `build/macs-mantlet-evidence/live-d99f7dd-b5c0cdf9/` and `live-d99f7dd-a8da8ff3/`. Their complete actor traces retained 266 and 66 entries respectively with no drops. Manual replay finish is not counted as a player victory. The owner's previously deferred multiplayer check remains outside this solo acceptance claim.

Final review found that active pillar pursuit still inherited the old positional scoring restriction: damage beyond three blocks of the original point was omitted. The narrow correction exempts the ranged-cover family alongside the already-mobile sword guard. The required `maeveRangedPillarImmediatelyPursuesAndAttacksWithinSameBet` native test now requires real pursuit beyond that radius, actual outgoing melee damage, a visible player counterattack there, and an exact persisted damage trade and outcome. Against the old code, it failed with 9 damage dealt and 2.58 received but a saved 0/0 UNKNOWN result. The failing XML, log and source fingerprint are preserved in `build/macs-mantlet-evidence/pillar-scoring-before/`. The same manifest entry, fixture and stress seeds are retained. This scoring correction receives fresh automated gates; it does not relabel the earlier live builds as tests of the final commit.

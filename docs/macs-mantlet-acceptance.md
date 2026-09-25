# MACS advancing cover — mantlet acceptance

Implementation of [§9.13c](https://www.notion.so/39d7cfaa890181c1bad4f6babad80880), with the [implementation plan](https://www.notion.so/3e67cfaa8901816f8534e1f67fe188ea). Branch `feat/macs-mantlet`, based on integration `ee2a18b` after PR #94. Live acceptance pending; this document does not declare the feature ready to merge.

## Behavior and initial tuning

Only ordinary Architects can select this variant. The same witnessed projectile-damage belief supports both pillars and mantlet; the mantlet requires **0.90 frozen historical confidence**. Confidence from the current encounter cannot upgrade it. Below 0.90, the existing 0.75 pillar gate applies.

Historical bow evidence selects the counter. The local executor proposes a safe fixed corridor toward the subject it can currently see, at least ten horizontal blocks away and within the ordinary 48-block observation range. It retains this exact local proposal when the commitment is issued; later movement cannot rotate or replace the corridor. Hidden subjects cannot supply their current position. It preflights five screens and four short forward steps in loaded space. A screen is two columns of packed ice, each two blocks high. The direction stays fixed even if the player flanks or changes weapons.

Each block takes at least 10 ticks; screen starts are at least 80 ticks apart. A new screen sits two blocks beyond the previous one. After completing it, the Architect visibly breaks down only its tracked previous screen, restores displaced snow there, and advances slowly at up to 0.09 blocks per tick. Both flanks remain open. Waiting between mantlet construction steps uses a ready combat posture. Pillars return to local pursuit and melee immediately after placement, without a stationary thinking hold. The thinking gesture remains for deliberate positional watches, reconnaissance and actual pursuit pauses.

One bet can place at most twenty blocks in a separate mantlet pool; retiring blocks does not refund capacity. A full unused mantlet pool is required at admission. Ordinary pillars and retreat keep their existing twelve-block tactical pool. Total retained ordinary tactical plus mantlet positions are therefore bounded at thirty-two per ordinary executor. Breaking any retained screen cell stops the advance without repair. Effective damage ends this commitment under the existing local-defense rule. Otherwise its twenty-second timer still runs, including construction, movement and the final hold.

Geometry failure before selection leaves pillars eligible. Failure after selection spends the bet and returns to local behavior. No re-selection into a different commitment is allowed in that encounter. Existing ordinary utility actions remain possible after the bet ends.

## Selection and learning

Choose the eligible ranged variant with the better frozen strategy effectiveness; an untried tie prefers mantlet. Pillars retain their original strategy key, while mantlet has a separate `PLAYER_PREFERS_RANGED:MANTLET` result context. This is a strategy identifier, not a new player belief or counter family. Variant selection happens before comparing recovery cost with other counter families: cheaper abandonment still takes precedence. Initial recovery costs are pillar 2, mantlet 3, and sword guard 1.5.

The existing two-failure deferral applies per strategy variant. Belief contradiction cooldown applies to the shared ranged belief and blocks both variants. Repeated arrow collisions with ice are not fabricated into damage-prevention scores: existing witnessed damage trades determine strategy results; silence or interruption remains unknown.

## Persistence, erasure and multiplayer

Maeve SavedData version 5 accepts previous versions. Old ranged strategy contexts remain pillar contexts; a missing mantlet context starts neutral. Future unsupported schema versions fail explicitly. The eight-context / eight-result bounds still apply.

The entity saves up to twenty charged `MantletIce` positions for cleanup and allowance accounting. Older entities load an empty mantlet list; older trial mantlet blocks retain their original `TacticalIce` cleanup ownership. The local screen plan is transient. Reload cannot resume it or refund the spent commitment. Its ice uses a separate entity-owned list and the existing construction cleanup path. Erasure removes variant memory and stops local execution. The permanent violation ledger is separate.

Beliefs and strategy results remain per player and dimension. An active mantlet stops on subject loss/change, unavailable executor, unsafe environment or attention eviction. One player encounter cannot buy multiple bets through additional observers. Master Architects and excluded roles keep the existing exclusion boundary. Live multiplayer acceptance is not claimed.

## Performance bounds

No chunk generation, global search, inventory scan, projectile prediction or asynchronous work. The proposal considers exactly five four-cell screens, five standing points, and short straight walks of at most three blocks. Each walk samples four times per block. Collision surface lookup checks four vertical cells. Placement and live collision validation recheck loaded state and occupancy. Entity queries stop at the first blocking occupant. Plan attempts normally use the existing once-per-second cadence; high-confidence spawn landings retry the grounded gate on the next tick.

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

Live acceptance must still establish readable advancing cover, useful frontal protection and usable counterplay. Broader terrain, multiplayer and performance testing remain distinct from a passing solo demonstration.


## First live round and transition revision

The first live round on `4d3c72a` built two screens/eight blocks. Seven ticks after retiring the first screen, the executor took one point of raw arrow damage and released the bet as `LOCAL_DEFENSE`; health became 39.18. The unobserved hit left strategy performance UNKNOWN. Budget exhaustion did not cause this release. Evidence and the closed world are preserved under `build/macs-mantlet-evidence/pre-transition-fix-2026-09-24`.

A freed embedded arrow is the working explanation, not a confirmed projectile identity. The trace did not retain IDs/trajectories, and initial retained-arrow and snow/impact-height reproductions did not reproduce the hit. The revised retirement explicitly handles lodged arrows before removing their support. Tests retain real arrows through the entire transition, verify recovery of the item and continued vulnerability to fresh shots, and check five screens, snow, independent budgets, persistence and cleanup. The twenty-block mantlet allowance was requested after this round; the original three-screen evidence above is historical. Live acceptance remains pending.

## Second live round: local orientation and cover presentation

On `5c9a016`, the mantlet proposal failed at `rangeOrBudget distance=5.0990195135927845 used=0`. The current player was visibly sixteen blocks away, with frozen ranged confidence 1.0, but geometry had used the previous fight's last shot at (2403, 101, 2411). The actor selected `HOLD_RANGED_COVER`, completed its 400-tick hold, and showed the inherited thinking gesture. The closed replay is preserved under `build/macs-mantlet-evidence/pre-visible-front-fix-2026-09-24`.

The correction separates strategic evidence from local execution geometry: history still admits the bet, but a currently visible subject supplies its initial direction and range. The local plan is frozen at admission and cannot follow a later flank or obtain a hidden target position. Mantlet construction waits now display readiness rather than the thinking gesture. The owner then removed the stationary pillar hold: the initial pillar immediately yields to ordinary pursuit and melee. The same issued bet and bounded combat-outcome window remain for learning, without restricting movement. Effective damage still records the trade and any contradiction before ending the bet; no replacement counter is purchased. The six laws and deliberate entrance/recovery/withdrawal watches remain in force. §§9.13a and 9.13d were updated and read back to verify this decision.

## Active pillar regression development

The new movement fixture initially checked arrival before the existing planner cadence had run and used the default damage-immune FakePlayer. A close-range retry exposed that fixture immunity; the final case uses the existing damageable player helper and native damage hooks. Those failed attempts are retained under `build/macs-mantlet-evidence/active-cover-development/`. The final scenario allows normal spawn settling, requires physical pursuit away from the constructed pillar, then has the player close into melee without first damaging the Architect. It requires an actual melee hit within the original outcome window, no positional holding, and no replacement bet. The snow test permits ordinary combat jumps and checks a single cover-to-combat handoff across all eight layer depths. These fixture corrections did not change production movement or attack tuning.

# MACS advancing cover — mantlet acceptance

Implementation of [§9.13c](https://www.notion.so/39d7cfaa890181c1bad4f6babad80880), with the [implementation plan](https://www.notion.so/3e67cfaa8901816f8534e1f67fe188ea). Branch `feat/macs-mantlet`, based on integration `ee2a18b` after PR #94. Live acceptance pending; this document does not declare the feature ready to merge.

## Behavior and initial tuning

Only ordinary Architects can select this variant. The same witnessed projectile-damage belief supports both pillars and mantlet; the mantlet requires **0.90 frozen historical confidence**. Confidence from the current encounter cannot upgrade it. Below 0.90, the existing 0.75 pillar gate applies.

The local executor proposes a safe fixed corridor toward the historical firing position, at least ten horizontal blocks away. It preflights three screens and two short forward steps in loaded space. A screen is two columns of packed ice, each two blocks high. The direction stays fixed even if the player flanks or changes weapons.

Each block takes at least 10 ticks; screen starts are at least 80 ticks apart. A new screen sits two blocks beyond the previous one. After completing it, the Architect visibly breaks down only its tracked previous screen, restores displaced snow there, and advances slowly at up to 0.09 blocks per tick. Both flanks remain open.

One bet can place at most twelve blocks. Those placements consume the existing ordinary tactical pool of twelve; retiring blocks does not refund capacity. A full unused pool is required at admission. Breaking any retained screen cell stops the advance without repair. Effective damage ends this commitment under the existing local-defense rule. Otherwise its twenty-second timer still runs, including construction, movement and the final hold.

Geometry failure before selection leaves pillars eligible. Failure after selection spends the bet and returns to local behavior. No re-selection into a different commitment is allowed in that encounter. Existing ordinary utility actions remain possible after the bet ends.

## Selection and learning

Choose the eligible ranged variant with the better frozen strategy effectiveness; an untried tie prefers mantlet. Pillars retain their original strategy key, while mantlet has a separate `PLAYER_PREFERS_RANGED:MANTLET` result context. This is a strategy identifier, not a new player belief or counter family. Variant selection happens before comparing recovery cost with other counter families: cheaper abandonment still takes precedence. Initial recovery costs are pillar 2, mantlet 3, and sword guard 1.5.

The existing two-failure deferral applies per strategy variant. Belief contradiction cooldown applies to the shared ranged belief and blocks both variants. Repeated arrow collisions with ice are not fabricated into damage-prevention scores: existing witnessed damage trades determine strategy results; silence or interruption remains unknown.

## Persistence, erasure and multiplayer

Maeve SavedData version 5 accepts previous versions. Old ranged strategy contexts remain pillar contexts; a missing mantlet context starts neutral. Future unsupported schema versions fail explicitly. The eight-context / eight-result bounds still apply.

The local screen plan is transient. Reload cannot resume it or refund the spent commitment. Its ice uses the existing entity-owned tactical list and construction cleanup. Erasure removes variant memory and stops local execution. The permanent violation ledger is separate.

Beliefs and strategy results remain per player and dimension. An active mantlet stops on subject loss/change, unavailable executor, unsafe environment or attention eviction. One player encounter cannot buy multiple bets through additional observers. Master Architects and excluded roles keep the existing exclusion boundary. Live multiplayer acceptance is not claimed.

## Performance bounds

No chunk generation, global search, inventory scan, projectile prediction or asynchronous work. The proposal considers exactly three four-cell screens, three standing points, and short straight walks of at most three blocks. Each walk samples four times per block. Collision surface lookup checks four vertical cells. Placement and live collision validation recheck loaded state and occupancy. Entity queries stop at the first blocking occupant. Plan attempts normally use the existing once-per-second cadence; high-confidence spawn landings retry the grounded gate on the next tick.

Retained execution data is one three-screen plan, up to eight active screen cells and at most twelve displaced states. Tactical ice and strategy memory use existing caps. Existing sword, pillar, navigation and retreat controllers retain their existing tuning.

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


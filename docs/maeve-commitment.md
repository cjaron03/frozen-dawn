# Maeve Slice 3: bounded commitments

Implements §§9.13a and 9.19 of [Maeve Director — Source of Truth](https://www.notion.so/39d7cfaa890181c1bad4f6babad80880). `feat/maeve-commitment` targets `feat/maeve-director`, following Slice 2's merge at `992c7b7`. The gameplay implementation and automated checks do not establish the required blind-playtest result. This slice stays unmerged until an uninformed tester notices an Architect waiting somewhere that turns out to be wrong. A developer acting as a blind tester provides useful informed feedback, but does not satisfy that criterion.

## Decision rules

A player's encounter captures a frozen copy of the two supported beliefs before its first new observation. Later observations update the live beliefs but cannot strengthen that encounter's decision basis. The frozen score still decays with overworld game time. Existing 600-tick contact expiry and shared observer identity define the encounter; the new planner can refresh contact only through the same eligible player, distance, dimension, loaded-chunk and own-line-of-sight checks as observation recording.

Below `0.75`, historical confidence only biases the existing random utility selection: ranged preference adds up to `0.40` to an available fortify score; covered recovery adds up to `0.30` to an available peek score. Each addition is scaled by confidence. Local action eligibility, randomness and hysteresis still apply.

At or above `0.75`, an ordinary Architect may choose one safe position directive. Candidates are ordered by the cost of abandoning them, then pattern name for a deterministic tie. Confidence never breaks a conflict between eligible candidates. One issued bet consumes the whole encounter's allowance, including after completion, safety release, owner death or reload. An Architect can execute only one player's directive at a time. Already observed support cannot be presented as a prediction; observing a contradiction blocks a new bet immediately and blocks that pattern in the next encounter, even if confidence remains high. These cap, precedence and cooldown rules are fixed, not difficulty settings.

Two provisional local actions use retained event coordinates:

- Ranged preference holds the Architect's present position and places two tactical ice blocks toward the last witnessed projectile origin. It requires a clear two-block escape route; its abandonment cost is two blocks.
- Covered recovery moves at most six blocks toward a point three blocks short of the last witnessed covered-use position, then watches that event coordinate. Abandonment cost is the travel distance.

The coordinate is an observed action location, not a classified room, inferred destination, health estimate or inventory scan. Real Masters continue their dedicated boss controller and may still contribute observations. Copies, Aggregate reinforcements and resident roles do not execute these directives. Full mission packets, WorldModel/spatial classification, attention allocation and §10 capabilities remain later work.

## Execution and failure

The local controller has at most 100 ticks to reach its point. On physical arrival it holds for 400 ticks, approximately 20 seconds. A witnessed contradiction preserves the hold for at least another 60 ticks, including near the normal deadline. Ordinary knockback may displace the Architect; it settles under gravity and returns to the held point. A visible nearby attacker may be struck without chasing them.

Critical own health (30% or lower), fire, water, a fall beyond two blocks, an unsafe route or unavailable cover releases the directive. Route checks accept only short, straight, level, supported paths with collision clearance. Existing tactical ice capacity and placement rules apply. No breach, scaffold or expensive path search is commissioned. Normal local behavior resumes after release. Supporting evidence remains `+0.20`, contradictions `-0.35`, with the inherited confidence clamp and 20-day grace/half-life. Timing, utility weights and authored point distances are provisional tuning defaults.

## Persistence, erasure and multiplayer

Maeve SavedData is version 2. Version 1 profiles and empty legacy worlds load; an old profile waits for the next encounter before gaining a frozen decision basis. Profiles persist the encounter basis, used allowance, already confirmed actions, current cooldown and next cooldown. Active movement does not resume after reload. The used allowance and cooldown survive, so reloading cannot buy a second attempt. Versions newer than 2 are rejected. Back up a complete world before changing development builds; older Slice 1/2 builds cannot load version 2 tactical data.

All reads and mutations go through the server-thread `MaeveDirector` facade, checked against the 300-line limit. Its immutable hints and directives contain bounded causal evidence. Entity controllers hold no belief-store reference or serialized belief copy. Authoritative erasure stops pending local movement immediately and clears the entire store, including frozen bases and cooldowns. Debug/forced erasure has the same behavior. Debug reversal starts empty. No tactical archive is written into the world; the separate permanent violation ledger remains intact. Restoring a complete external backup restores that backup's saved state.

UUID profiles isolate players. Observers of one player share one encounter allowance; different players can receive independent bets from different Architects. Removing an actor releases execution without refunding the encounter. Server shutdown drops facade caches.

## Bounds and diagnostics

The inherited limits remain 128 profiles, 16 beliefs per profile, eight provenance entries per belief and eight tracked observer contacts per player. Each profile adds at most two frozen beliefs, bounded sets of the two known patterns, two candidate explanations and one runtime directive. UUID owner lookup scans at most 128 profiles; there is no global entity query. A new local plan runs at most once per 20 ticks. It considers at most two historical hints and two local candidates. Each straight route check samples at most 14 body positions; the cover occupancy query stops at its first occupant. All required chunks must already be loaded. Contact refresh still runs once per second.

`/fd maeve dump [player-or-uuid]` and `/fd maeve explain <pattern> [player-or-uuid]` now also show the frozen basis, cooldowns, selected/rejected candidates, cost, owner, issued position, inherited event and hold/contradiction times. A missing directive is explicit. Expired state can be explained without advancing execution or changing saved data. ERASED output exposes no former tactical contents. These are operator-only diagnostics, not player telepathy or an additional persistent log.

## Verification and live handoff

Use the unchanged gate commands and seed/fixture matrix:

```sh
./gradlew architectVerify architectMonkey --console=plain
```

Policy tests cover threshold boundaries, lagged basis, an already confirmed action, cost precedence, the encounter allowance, the wrong-position beat, high-confidence contradiction cooldown, reload, decay, read-only explanations and legacy profiles. Required GameTests exercise actual damage and item completion events with physical movement, holding, knockback, multiplayer ownership, sight, erasure and removal. The manifest includes all three new commitment cases.

`tools/prepare_maeve_commitment_playtest.py` prepares a disposable copy of a **closed** Slice 2 QA world. It neither seeds beliefs nor changes the source world. In the copy, `/function maeve_playtest:setup` resets tactical memory, guides four ordinary potion completions and offers an operator click to accelerate only the 630-tick gaps between those training encounters (or waits 31 seconds normally). It then starts an ordinary mobile encounter with a fresh Architect and an actual decision journal. The tester receives neutral encounter instructions. `/function maeve_playtest:finish` pauses the actor and exports its journal without showing Maeve's explanation. Collect the tester's description before `/function maeve_playtest:inspect` reveals the diagnostic dump. The retained observations and journal must then explain the actual outcome; a prepared fixture alone is not replay evidence. The fixture explicitly loads its arena and remote waiting-platform chunks before building, shows completed-round progress, and counts consumption only during an active training round. It releases those chunk tickets on finish. To repair an already running fixture, use `/reload` followed by `/function maeve_playtest:repair`; this preserves the current counter and all actual beliefs.

Delivery evidence belongs under `build/maeve-slice3-evidence/`, with the tested source fingerprint, exact commit binding, gate logs/XML, jar hash and live evidence when available. Preserve failed attempts separately instead of overwriting them. The first development stress run recorded a first-tick floor mutation in `stairs_up_s7_r1`, before actor movement or excavation; the log and trace are retained. A later new knockback test initially failed because training-hit motion had not been settled before the measured encounter; its setup now clears that motion and waits through actual entity ticks. Final gate results and human playtest status must be reported separately.

Final automated run (2026-09-16 local): 565 unit tests, 75 regression GameTests (70 required), and 575 stress-server GameTests (500 required stress cases), all passing with none missing. Source SHA-256: `dd6124c4f2168703649f0dc874b37e7d7e35b110f689f47f424dcff9c86ecaca`. Facade: 192 lines. The first-tick terrain failure did not recur; its cause remains unproven. Live informed and strict blind outcomes are still pending.

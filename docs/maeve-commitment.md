# Maeve Slice 3: bounded commitments

Implements §§9.13a and 9.19 of [Maeve Director — Source of Truth](https://www.notion.so/39d7cfaa890181c1bad4f6babad80880). `feat/maeve-commitment` targets `feat/maeve-director`, following Slice 2's merge at `992c7b7`. The gameplay implementation and automated checks do not establish the required blind-playtest result. This slice stays unmerged until an uninformed tester notices an Architect waiting somewhere that turns out to be wrong. A developer acting as a blind tester provides useful informed feedback, but does not satisfy that criterion.

## Decision rules

A player's encounter captures a frozen copy of the two supported beliefs before its first new observation. Later observations update the live beliefs but cannot strengthen that encounter's decision basis. The frozen score still decays with overworld game time. Existing 600-tick contact expiry and shared observer identity define the encounter; the new planner can refresh contact only through the same eligible player, distance, dimension, loaded-chunk and own-line-of-sight checks as observation recording.

Below `0.75`, historical confidence only biases the existing random utility selection: ranged preference adds up to `0.40` to an available fortify score; covered recovery adds up to `0.30` to an available peek score. Each addition is scaled by confidence. Local action eligibility, randomness and hysteresis still apply.

At or above `0.75`, an ordinary Architect may choose one safe position directive. Candidates are ordered by the cost of abandoning them, then pattern name for a deterministic tie. Confidence never breaks a conflict between eligible candidates. One issued bet consumes the whole encounter's allowance, including after completion, safety release, owner death or reload. An Architect can execute only one player's directive at a time. Already observed support cannot be presented as a prediction; observing a contradiction blocks a new bet immediately and blocks that pattern in the next encounter, even if confidence remains high. These cap, precedence and cooldown rules are fixed, not difficulty settings.

Two provisional local actions use retained event coordinates:

- Ranged preference holds the Architect's present position and places two tactical ice blocks toward the last witnessed projectile origin. It requires a clear two-block escape route; its abandonment cost is two blocks.
- Covered recovery chooses the cheaper safe side of a point three blocks short of the last witnessed covered-use position, offset two blocks sideways. It requires two to six blocks of travel, then watches the event coordinate. Abandonment cost is the travel distance. A point already underfoot cannot consume the bet.

The coordinate is an observed action location, not a classified room, inferred destination, health estimate or inventory scan. Real Masters continue their dedicated boss controller and may still contribute observations. Copies, Aggregate reinforcements and resident roles do not execute these directives. Full mission packets, WorldModel/spatial classification, attention allocation and §10 capabilities remain later work.

## Execution and failure

The local controller has at most 100 ticks to reach its point. On physical arrival it holds for 400 ticks, approximately 20 seconds. A witnessed contradiction preserves the hold for at least another 60 ticks, including near the normal deadline. Ordinary knockback may displace the Architect; it settles under gravity and returns to the held point. A visible nearby attacker may be struck without chasing them.

Critical own health (30% or lower), fire, water, a fall beyond two blocks, an unsafe route or unavailable cover releases the directive. Route checks accept only short, straight, level, supported paths with collision clearance. Existing tactical ice capacity and placement rules apply. No breach, scaffold or expensive path search is commissioned. Normal local behavior resumes after release. During physical holding, a transient client flag enables the existing thinking animation over the OBSERVE stance: a blended head tilt and hand-to-chin gesture when the offhand and chest are empty. The executor turns its body and head toward the inherited event coordinate; the renderer suppresses the ordinary observation sway during the hold. Movement, knockback, safety release, expiry, removal and erasure clear the flag as appropriate. It carries no belief contents and is not serialized. Nearby defensive attacks retain their normal swing. Supporting evidence remains `+0.20`, contradictions `-0.35`, with the inherited confidence clamp and 20-day grace/half-life. Timing, utility weights and authored point distances are provisional tuning defaults.

## Persistence, erasure and multiplayer

Maeve SavedData is version 2. Version 1 profiles and empty legacy worlds load; an old profile waits for the next encounter before gaining a frozen decision basis. Profiles persist the encounter basis, used allowance, already confirmed actions, current cooldown and next cooldown. Active movement does not resume after reload. The used allowance and cooldown survive, so reloading cannot buy a second attempt. Versions newer than 2 are rejected. Back up a complete world before changing development builds; older Slice 1/2 builds cannot load version 2 tactical data.

All reads and mutations go through the server-thread `MaeveDirector` facade, checked against the 300-line limit. Its immutable hints and directives contain bounded causal evidence. Entity controllers hold no belief-store reference or serialized belief copy. Authoritative erasure stops pending local movement immediately and clears the entire store, including frozen bases and cooldowns. Debug/forced erasure has the same behavior. Debug reversal starts empty. No tactical archive is written into the world; the separate permanent violation ledger remains intact. Restoring a complete external backup restores that backup's saved state.

UUID profiles isolate players. Observers of one player share one encounter allowance; different players can receive independent bets from different Architects. Removing an actor releases execution without refunding the encounter. Server shutdown drops facade caches.

## Bounds and diagnostics

The inherited limits remain 128 profiles, 16 beliefs per profile, eight provenance entries per belief and eight tracked observer contacts per player. Each profile adds at most two frozen beliefs, bounded sets of the two known patterns, two candidate explanations and one runtime directive. UUID owner lookup scans at most 128 profiles; there is no global entity query. A new local plan runs at most once per 20 ticks. It considers at most two historical hints and two final local candidates, checking at most two lateral recovery positions to produce that pattern's candidate. Each straight route check samples at most 14 body positions; the cover occupancy query stops at its first occupant. All required chunks must already be loaded. Contact refresh still runs once per second.

`/fd maeve dump [player-or-uuid]` and `/fd maeve explain <pattern> [player-or-uuid]` now also show the frozen basis, cooldowns, selected/rejected candidates, cost, owner, issued position, inherited event and hold/contradiction times. A missing directive is explicit. Expired state can be explained without advancing execution or changing saved data. ERASED output exposes no former tactical contents. These are operator-only diagnostics, not player telepathy or an additional persistent log.

## Verification and live handoff

Use the unchanged gate commands and seed/fixture matrix:

```sh
./gradlew architectVerify architectMonkey --console=plain
```

Policy tests cover threshold boundaries, lagged basis, an already confirmed action, cost precedence, the encounter allowance, the wrong-position beat, high-confidence contradiction cooldown, reload, decay, read-only explanations and legacy profiles. Required GameTests exercise actual damage and item completion events with physical movement, holding, knockback, multiplayer ownership, sight, erasure and removal. The manifest includes all four new commitment cases.

`tools/prepare_maeve_commitment_playtest.py` prepares a disposable copy of a **closed** Slice 2 QA world. It neither seeds beliefs nor changes the source world. In the copy, `/function maeve_playtest:setup` resets tactical memory, guides four ordinary potion completions and waits 630 ticks (31.5 seconds) between those training encounters. After the gap, a player click starts an ordinary mobile encounter with a fresh Architect and an actual decision journal. The roof and its two rear supports stay in place through training and combat, giving the historical recovery point a visible landmark. The open front preserves the training observer's line of sight and the existing lateral approach path. The controlled informed check prompts one normal potion completion five seconds into the encounter, exports a checkpoint at ten seconds, and pauses/exports at thirty seconds. This is a diagnostic replay, not a blind test. `/function maeve_playtest:finish` can end early and reports whether the export actually succeeded, including actor loss. Collect the tester's description before `/function maeve_playtest:inspect` offers the direct diagnostic dump. The retained observations and journal must then explain the actual outcome; a prepared fixture alone is not replay evidence. The fixture explicitly loads its arena and remote waiting-platform chunks before building, shows completed-round progress, and counts consumption only during an active training round. It releases those chunk tickets on finish. `/function maeve_playtest:retry` reuses existing actual beliefs, clears only the disposable arena, and starts another encounter after a full contact gap; it does not edit beliefs or bypass confidence/cooldown rules. To repair an already running fixture, use `/reload` followed by `/function maeve_playtest:repair`; this preserves the current counter and all actual beliefs.

Delivery evidence belongs under `build/maeve-slice3-evidence/`, with the tested source fingerprint, exact commit binding, gate logs/XML, jar hash and live evidence when available. Preserve failed attempts separately instead of overwriting them. The first development stress run recorded a first-tick floor mutation in `stairs_up_s7_r1`, before actor movement or excavation; the log and trace are retained. A later new knockback test initially failed because training-hit motion had not been settled before the measured encounter; its setup now clears that motion and waits through actual entity ticks. Final gate results and human playtest status must be reported separately.

Initial automated run (2026-09-16 local, before the legibility repair): 565 unit tests, 75 regression GameTests (70 required), and 575 stress-server GameTests (500 required stress cases), all passing with none missing. Source SHA-256: `dd6124c4f2168703649f0dc874b37e7d7e35b110f689f47f424dcff9c86ecaca`. Facade: 192 lines. The first-tick terrain failure did not recur; its cause remains unproven. The first informed replay did not pass legibility; the strict blind outcome remains pending.

## First informed feedback and correction

The player reported no noticeable difference. The direct dump showed `WATCH_LAST_RECOVERY_POINT`, confidence 0.80, a 0.255-block candidate cost, and `started=arrived=76323`, `holdUntil=76723`, `contradicted=-1`. The actor held for 400 ticks but was already inside its arrival radius. No recovery action was witnessed in the final fight, so no recovery contradiction was recorded. The player killed the actor before exporting; the fixture incorrectly announced success despite the missing actor. Those facts do not satisfy the visible-wrong-prediction criterion.

The correction adds two bounded lateral watch options and rejects recovery positions less than two blocks away. A required GameTest starts the actor within 0.26 blocks of the former point, verifies actual movement away from the approach line, then supplies a real open-sky potion contradiction. The replay now prompts the relevant ordinary action, preserves a checkpoint before the end, and checks export success. The original response is retained unchanged in the local evidence. The revised informed result is recorded below.

After the legibility repair, the same gate command passed again: 565 unit tests, 76 regression GameTests (71 required), and 576 stress-server GameTests (500 required stress cases), with zero failures or missing cases. Source SHA-256: `996444647c27eea77637d889a159ccec706834a15d2cd061bebc1ef4b6671c82`. The revised informed replay on this source confirmed the mechanical sequence described below; it did not establish the blind exit criterion.


## Revised informed result and retained-shelter replay

The revised informed replay on commit `f3eb32d` produced a complete 160-event journal with no dropped events. The actor moved to `(209,101,206)`, arrived at tick `85823`, and held until `86223`. The player's real open-sky potion at tick `85966` lowered recovery confidence from `0.80` to `0.45` (four supports, one contradiction) and queued that pattern's next-encounter cooldown. The actor kept its position for another 257 ticks, then resumed pursuit. The direct dump and journal establish movement, the 400-tick hold, the witnessed contradiction and resumed pursuit.

The player's unedited feedback was: "ok so when i drank the potion, it just stood there? idk anything else". This establishes that the hold was noticed, but does not establish recognition of an incorrect prediction. The fixture had removed the original roof before the encounter, leaving its watch point without a visible reference. That is a plausible explanation for the poor readability, not a proven cause. No AI weights, commitment times or candidate distances change in the retained-shelter replay.

For this existing QA world, update only its datapack (the world may remain open):

```sh
python3 tools/prepare_maeve_commitment_playtest.py --update-pack 'run-lab/saves/Maeve Slice 3'
```

Then run `/reload` and `/function maeve_playtest:refresh`. This rebuilds only the disposable arena, retains the roof with two rear supports, and queues two real healing encounters. It preserves all beliefs and cooldowns. Starting from the confirmed 0.45 belief, two accepted supports in distinct encounters should produce 0.85; the next encounter should then have no recovery cooldown. Practice progression uses a hidden `consume_item` advancement to flag completion and advances on the next tick, after Maeve has recorded the original position. It does not depend on the potion statistic scoreboard. The practice counter counts item completions, not accepted observations, so verify the actual provenance and confidence with `/fd maeve dump` before claiming readiness. Wait for the start prompt, run the direct dump, and then start `/function maeve_playtest:encounter` at normal speed. This replay remains informed and guided. Report feedback before reading its final explanation.

`refresh` is specific to this known two-support recovery case. It is not a general command that guarantees commitment eligibility from arbitrary belief state. `retry` preserves the current score/cooldown and may legitimately produce no commitment. Neither command injects confidence or clears the policy. Fresh `setup` is the separate destructive reset for a disposable test world, and is not part of refreshing the existing replay.


The retained-shelter refresh exposed a separate QA progression failure after reloading: the first potion produced a real covered-use observation at tick `149130` and confidence `0.65`, while both potion-statistic objectives stayed unchanged and practice remained `0/2`. The exact cause of the statistic mismatch is not yet established. The fixture now flags completion through a hidden consumption advancement and advances on the following tick, preserving NeoForge's completion-event position. To resume that already confirmed first drink, after updating/reloading the pack, `/function maeve_playtest:consumed` advances the practice stage once. This manual recovery is justified by the recorded event; it does not manufacture or edit any belief. Do not run it to replace a missing observation.

## Blind test handoff

An uninformed tester is not currently available. This remains a merge gate; a prepared mode is not a pass. Share only [the neutral tester brief](maeve-commitment-tester.md), not this implementation document, the PR or expected behavior. Use a separate disposable QA world prepared from the closed Slice 2 fixture, then have the tester use `/function maeve_playtest:setup_blind`. It starts with empty tactical memory and four ordinary practice consumptions. All observations belong to that tester's actual player UUID. Do not transfer another player's beliefs or inject a profile.

Blind mode keeps the same shelter, rules, item loadout, 10-second checkpoint and 30-second pause, but gives no timed potion instruction during combat and no explanation of positioning. Take the tester's first open-ended description verbatim before opening diagnostics or asking targeted follow-ups. A run without a contradiction or recognizable wrong prediction is inconclusive. Only a spontaneous description of waiting somewhere that turned out wrong, backed by the event trace and dump, satisfies §9.19. Then publish required checks for the exact slice commit and merge into `feat/maeve-director`; Slice 4 follows that merge.


## Hold presentation revisions

The retained-shelter replay still read as standing and observing: "it still just stood there after drinking the potion. i think it was observting or something". The direct dump and 169-event journal confirmed movement, arrival at tick 159859, holding until 160259, an actual potion contradiction at 160009 (confidence 0.85 to 0.50), and resumed pursuit. The earlier stationary state reused the normal OBSERVE pose.

The first presentation revision added a braced stance, directing arm and body/head alignment toward the inherited point. Its informed replay on `c3d5684` produced a complete 142-event trace with zero drops and an F2 screenshot showing the pose. The dump confirmed arrival at tick 173684, holding until 174084, and a witnessed open-sky potion at 173919 that lowered confidence from 0.90 to 0.55. The actor held another 165 ticks before pursuing. The screenshot was taken before the potion completion. The user requested the existing thinking gesture instead; this feedback does not establish the blind criterion.

The current revision removes that custom guard pose. Maeve's physical hold reuses the existing OBSERVE stance and the existing head-tilt/hand-to-chin animation, including its equipment safeguards and client interpolation. Ordinary OBSERVE keeps its existing animation; pursuit pauses continue to use the same thinking helper. Body/head alignment toward the inherited point and the suppression of global observation yaw sway remain. Nearby defensive swings still take precedence.

Confidence, candidate selection, movement distance and commitment timing stay fixed. Only the transient holding boolean is synchronized; clients receive no new belief contents. Existing required integration cases verify body/head direction despite player movement, cue lifetime, recovery after knockback and immediate erasure cleanup. The replacement thinking presentation still needs an informed rendered check; the strict blind exit criterion also remains pending.

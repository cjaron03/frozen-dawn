# Blind Architect isolated channel playtests

Owning checkout: `/Users/jaroncabral/Projects/minecraft-mod/.worktrees/blind-architect`, branch `feat/blind-architect`, baseline `0885e22812f8723a6f55cdf52da87b62a18c4485`. World **Blind Architect Reusable Lab**, save `run-lab/saves/Blind Architect 09 Disconnected Supports`.

## Completed human suite

| Channel | Positive human view | Physical control | Interpretation |
| --- | --- | --- | --- |
| Hearing, 12/13 | 12 mining-linked hearing observations; 317 selected frames | Sealed duct: 14 mining inputs, zero hearing tracks | Hearing response and expiry demonstrated; view 12 quiet-baseline caveat below |
| Vibration, 14/15 | Connected stone: 17/17 footsteps detected; 197 LISTEN frames | Floor gap: 0/16 detected; all NO_PATH | Mechanically connected alert and listening, without player coordinates |
| Direct echo, 16/17 | Quiet open aperture: 19/19 measured returns admitted; 437 PURSUE frames | Sealed aperture: 0/8 admitted; all NO_DIRECT_RETURN | Direct-return acquisition with clean positive/negative controls |

View 17 confirmed the owner's report of continued looking around: eight active echo queries all returned NO_DIRECT_RETURN, no sensory belief was created, and all 480 frames stayed in WANDER with clean geometry and sources. Click intervals stayed at 2.7–3.35 seconds. In view 16, intervals tightened to 0.9–1.2 seconds after acquisition, and the actor sustained facing toward the quiet player. Human walking and ordinary actor random sequences vary between paired views; native comparisons separately match seeds and inputs.

View 12 admitted only hearing, and all twelve observations match the twelve mining inputs. Its early quiet baseline nevertheless contained seven unrelated landing queries from leftover items/XP; those exhausted structural budgets and created no belief. The source-object isolation repair archives full NBT before removal or spawn rejection, refuses dirty starts and checks every frame. The same six native pairs passed again with no stray event or unknown query. Views 13–17 are clean throughout. Preserve the original positive hearing result with this qualification; do not call all six human windows pristine.

## Movement and final state

The owner asked whether the actor normally walks. Production WANDER attempts a random walking route every 40 ticks when navigation is finished; if no route is active, it surveys with a head/body scan. All six channel captures ran ordinary enabled AI, but their glass corrals and disabled block breaking kept the physical isolation intact. Completion then stops navigation and sets NoAI. These views establish sensing and presentation. The earlier human base view 11 separately demonstrated approach, four cuts, entry and a hit with mining enabled.

The final view is complete. At the final result snapshot, the world was menu-paused at gameTime 51891, session `61a0e427-1992-4f57-a633-011b6fac5c52`: stage 3/run 17/case 17/timer 480/frames 480/last_frame 480/archived 1/inconclusive 0/isolated 17. Actor `89121599-d4ef-47c1-a2df-b3f718d5592a` is held at (9.5,-44,5.5), 40 health, and the participant is Creative at (17.5,-44,5.5). Client terminal 4967 remains active. Consult `suite-final-runtime.json` and a fresh bridge status for later state. Do not prepare or Start any completed view again; no next scene is staged.

## Evidence and verification

Each view has 480 sequential frames, a verified trace hash, a verified decision-log hash and the owner's first account captured before diagnostics. Aggregate: `build/blind-architect-evidence/channels-isolation-20261001/human-suite.json`. View 12 raw evidence is under `channels-20261001/live/run-12/result/`; views 13–17 are under `channels-isolation-20261001/live/run-N/result/`. The latter loaded results include actor dumps and immutable result bundles. Closed world archives exist for views 12 and 14; a copied result ZIP is not a closed-world backup. Final trace: `8d6a629f5753753ebc47aa2820fc520a968a912a19a73c4bdf5cbfb3939fcc12`; decisions: `07a1d6c2cac9f1f5ccc86d0d82434dc294a57ebcf74981db8a10d5495cdfafaf`.

The current Java 21 gate passed 654 unit tests, 275 native GameTests, all 270 required cases and 83 function parses, zero failures/errors. Gate artifact: `/Users/jaroncabral/Projects/minecraft-mod/.worktrees/blind-architect/build/architect-reports/1b867311-5255-41a0-8332-4b51c0af0362`. Every production class, sound and asset is unchanged through the channel suite; only build metadata differs in the release jar. The prior exact 500-seed stress result remains applicable and was not rerun. Built/smoke SHA-256 `337283a8a5db16ad26f0d4cb24761bcbe75158df71d1b6bced57e12af3d4c9d5`; smoke dependencies remain unchanged. Existing deprecation warnings remain. The owning client was restarted for the lab-isolation fix; the separate smoke profile reads its copied jar on its next restart. No source/build changes were made during human result collection.

## Physical comparisons

| Views | Intended channel | Source | Matched change | Competing channels |
| --- | --- | --- | --- | --- |
| 12 / 13 | Hearing | Actual ongoing obsidian mining | One roof cell opens/seals the short enclosed duct | Disconnected mechanical supports; no direct echo path |
| 14 / 15 | Vibration | Ordinary footsteps | Four-block gap replaces the stone bridge | Sealed glass shell blocks hearing and echo |
| 16 / 17 | Echo | Quiet stationary player | Three glass cells seal the direct aperture | Disconnected supports; no player activity |

All six use enabled ordinary AI at Y=-44. The actor is physically corralled and mobGriefing is false to preserve isolation. These views measure sensing and reaction, not successful approach or breach. The combined view 11 already demonstrated those under enabled mining. Glass is acoustically solid in this collision-based model even though it permits visual observation. Vibration is an anonymous local disturbance; it does not provide a hidden player's coordinates. Hearing supplies an anonymous approximate source region. A measured direct echo can support a player lock.

## Source coverage

| Route | Current coverage | Limit |
| --- | --- | --- |
| Eating/drinking, steps/landings, container open/close, placing/breaking, activate/deactivate, notes, projectile landing | 12 real game-event subscriber inputs × open/sealed air; all anonymous | Adapter delivery test, not twelve full player UI replays |
| Continuous mining | Server-accepted block-destroy progress in native views 12/13; live view 12 admitted hearing and sealed view 13 admitted none | Larger bent-air paths can exhaust the budget |
| Sneak movement | Actual displacement callback: grounded positive, airborne and teleport negative | Physical step-strength/wool tests separately pass; live movement pairs use walking |
| Sprint/landing strength, wool/carpet, supported versus airborne receiver | Existing native generation/material/contact regressions reran | Not a new human pass for every posture/material combination |
| Operating machinery | Core production tick test reran; four current hooks are heater while lit, core, processing forge and processing silo | Forge/silo/heater activation have not each had an isolated human replay; a vanilla furnace is not an added periodic machine source |
| Projectile decoy | Real event creates an anonymous impact region; moving thrower is not tracked | Does not establish thrower identity |
| Nearby player hit | Actual damage callback before any click: nearby open contact positive, distant/occluded attribution negative | Stored using ECHO sense, but no acoustic query or returned measurement caused it |
| Bell/explosion/nearby heat | Existing cooldown, actual retreat, non-duplicated blast damage and core heat tests reran | Defensive/pain reactions, not extra remote player-finding channels |
| Direct echo and stale memory | Six-view pair plus prior measured return, aperture, closure, unheard movement and reacquisition regressions | Reflected echoes, richer masking, natural caves and LAN are not established |

Native positive hearing selected 347 of 480 frames, sealed hearing zero. Vibration produced seven DETECTED versus seven NO_PATH inputs, with 161 versus zero selected frames. Direct echo admitted 19 returns versus zero. All six had zero unwanted channel and zero unknown query. Trace and decision-log hashes are recorded in `build/blind-architect-evidence/channels-isolation-20261001/verification.json`. All six human views are complete, with their distinct results and the first hearing baseline qualification recorded above.

## Remaining boundaries and continuation

The short bent hearing duct passes; larger bends previously exhausted the ordinary air-search budget and remain a limitation. Native coverage includes 24 delivered-event air conditions, nearby/far/occluded damage contact, grounded/airborne/teleport-sized crouch callbacks and the existing mining, machinery, projectile, wool/contact and pain/heat regressions. This does not establish a human replay of every item GUI, machine or posture. Reflected echoes, richer masking, natural spawning, arbitrary natural cave hunts and LAN remain outside this acceptance.

All setup/Start operations are completed and must not be repeated. No additional view is staged. Read `AGENTS.md`, the current handoff and `docs/lab-command-bridge.md` before any future scene change. Preserve the owner's next first account before diagnostics; if they have saved and quit, use an exclusive closed-world archive before edits.

# MACS combined camp playtest

Slice 7 merged through PR #93 into `feat/maeve-director` at `27ef17a0f8759a00b64136e221a5c6f10d032844`. This branch prepares integration QA, not Slice 8. The [source of truth](https://www.notion.so/39d7cfaa890181c1bad4f6babad80880), especially §§9.19–9.20 and §12, governs calibration and the eventual merge to `main`.

## What Brutal changes

MACS permits five simultaneous attention concerns on Brutal and three on Normal (`default` in the command). It uses the same current confidence weights, 0.75 commitment threshold, 400-tick hold and 600-tick encounter separation. A single duel cannot demonstrate the larger budget. With one subject the current implementation permits at most one reconnaissance mission, one commitment and one shared tracking concern, so solo testing cannot establish the full attention-budget difference. A later multiplayer test supplies simultaneous concerns; adding duplicate ordinary watchers to one player does not create independent tracking slots.

The full Frozen Dawn presets also alter apocalypse duration, temperature, heating, snow, sanity and spawn multipliers. The first comparison isolates MACS: both copies use vanilla Normal combat, the same gear, dusk, a benign Phase 0 environment and disabled natural mob spawning. Maeve is activated through the real late-Phase-6 boundary before moving the atmosphere back; activation persists normally. This is not a full Brutal-survival verdict. Test endgame atmosphere, natural spawning and resource pressure separately after behavior is legible.

Brutal first follows the owner's requested order. Normal starts from a separate copy of the same closed source save, with fresh tactical memory on its first setup. Do not switch the first world's preset and call that a fresh comparison: it would inherit the learned history, damaged terrain and depleted inventory. Preset order also trains the human tester; counterbalance the order with a later tester.

## The camp and controls

`tools/prepare_macs_integrated_playtest.py` prepares **MACS Camp - Brutal** and **MACS Camp - Normal** in a separate client worktree. The completed Slice 7 world remains intact. Each copied world is disposable. Its first `setup` uses the authoritative debug erasure/reset transition to clear copied tactical state, preserves the separate violation ledger and constructs the same camp. Later `setup` calls only report state. There is no reset command in this pack.

The camp has a cabin with east/west openings, an exposed roofed porch, an opaque interior corner, a northwest supply shed, scattered trees and low cover. Its map is authored QA geometry with a bedrock perimeter. Architects use normal sensing, navigation, construction, damage, belief recording and selection. The fixture summons ordinary Architects without assigning a mission, target or counter. It never injects beliefs or labels the building for Maeve. Masters remain outside the experiment.

1. Open **MACS Camp - Brutal** and run `/function macs_trial:setup` once, then `/tick unfreeze` directly in chat to clear any inherited test pause. Wait for **Camp ready** while the needed chunks load and construction completes. No actor can start during preparation.
2. Run `/function macs_trial:enter`. Explore without an Architect. Find both cabin openings, the porch and the northwest shed. A simple survival objective is to move an emerald from the shed barrel into the cabin barrel.
3. From inside the camp, run `/function macs_trial:start`. One ordinary Architect arrives on alternating east/west sides using seed 1337. There is no countdown, forced combat response or automatic freeze. Play until the encounter resolves, then use `/function macs_trial:finish`.
4. Describe the encounter before reading `/fd maeve dump`. Finish exports actor traces, removes remaining tagged QA actors and returns the player to the waiting platform. Ending an active hold is an interruption; do not count it as a completed counter result.
5. During this actor-free gap, `/tick sprint 620t` is optional. Wait for **Ready** and **Sprint completed**. Enter again, use `/function macs_trial:refill` if needed, then Start. No actor is summoned by the timer, and all learned history and terrain edits persist.

`/function macs_trial:abort` immediately ends an active QA encounter. Player death also stops it; respawn is on the waiting platform and inventory is retained. Reloading during an active round aborts that round instead of silently continuing incomplete observation windows. `/function macs_trial:status` reports the round, stage, world preset and Maeve lifecycle. Stage 0 means ready, 1 means playing, 2 means an empty encounter gap, and 3 means camp preparation. Refill works only at stage 0; it restores the same iron armor, bow, sword, shield, food, restorative items and building materials, without changing tactical memory.

No invulnerability, resistance or night-vision effects are supplied. Initial setup removes inherited QA effects. Gear and vanilla difficulty are held constant for comparison. Environmental darkness can contribute to atmosphere but must not hide whether an Architect moved or built something.

## Tactics and observation order

Start with one chosen habit rather than trying to unlock every counter together. The cheaper eligible commitment wins, so strong ranged history can mask a spatial or recovery response even when those beliefs exist.

| Pass | Player approach | What to investigate afterward |
| --- | --- | --- |
| First contact | Retrieve supplies; use the route and weapon you would naturally choose. Let a distant, non-attacking visitor finish its work if safe. | What was actually witnessed? Did reconnaissance arise from a prior observed crossing? Was its purpose legible? |
| Consistent habit | Favor the bow across four distinct witnessed encounters. Use one cabin opening consistently when escaping. | Does a later encounter answer the earlier behavior? Count accepted encounters from evidence, not just rounds. |
| Break the pattern | Use the other opening; physically seal a previously used route while no Architect can see the change. Switch to melee for one encounter if testing the ranged prediction. | Does old knowledge survive until observed contradiction? Does a wrong commitment remain visible before recovery? |
| Beat the response | When cover appears, move to a clear angle and shoot during the hold. Compare later choices after two assessed losses. | Does performance change while the ranged belief remains strong? Use the high-confidence comparison proven in Slice 7. |
| Visibility controls | Finish a healing potion once in the opaque interior corner and once under the open porch with a visible observer. | Hidden consumption should add no recovery evidence; a witnessed covered use may support it. Do not infer hidden health knowledge. |
| Free play | After history exists, run several encounters without following an expected-action script. Build, retreat, pursue, hide or fight as you prefer. | Does the whole encounter feel intelligent and threatening? Can the player understand and exploit a mistake without a dump? |

Following an actual scout withdrawal in open sight can supply conditional pursuit evidence; do not chase every actor just to force that pattern. Instant combat is not automatically a failure: it can be the appropriate fallback after a losing strategy or absent evidence. Conversely, a convincing story is not proof—trace the observed action to the stored evidence.

This owner has already seen the mechanics explained, so these are informed playtests. A later uninformed tester remains necessary for the integration blind-playtest gate. Do not call hidden setup details a replacement for that tester.

## Evidence and tuning

After each encounter record: world/preset, round, chosen weapon and route, healing visibility, any building changes, the player's own description, perceived threat (0–5), fairness (0–5), readable behavior and dead time. Rate the experience before reading the dump. Missing perception or an interrupted hold is an inconclusive case, not a counted strategy failure.

The QA pack exports the Architect's existing diagnostic journal every 200 ticks, at finish/abort and when a dying actor remains selectable. Periodic exports are capped at 48 per encounter. They do not freeze or reseed its AI. The journal is a bounded ring: keep overlapping exports and check dropped-event counts; a death's final ticks may be absent from the actor trace. Final-damage evidence and counter results still use production hooks and must be checked in the direct Maeve dump. Automatic function feedback can suppress dumps, so the player runs `/fd maeve dump` in chat. Copy evidence outside the world before any deliberate erasure test. Diagnostic exports add overhead, so this run is not a clean performance benchmark.

Compare Normal from its untouched copy using the same habit and gear protocol, then schedule multiplayer attention pressure, save/reload, erasure and performance checks on the integrated build. Normal's larger/smaller apparent tension cannot be assigned to attention without enough simultaneous concerns.

Tune only after a reproducible issue is identified, following §9.20: confidence thresholds, evidence weights, staleness/decay, then attention capacity; one variable at a time. Locked values require the matching source-of-truth decision before code changes. Production tuning is unchanged in this QA branch. No promise is made that a scripted demonstration establishes final horror calibration.

## Verification

The required native GameTest parses every generated function at integrated-client permission level 2, resolves its function references, and checks camp fills against the vanilla volume limit. Both preset variants must be identical except their preset command. The paired worlds are copied from the same closed source and initialized independently. Required gates remain `./gradlew architectVerify architectMonkey --console=plain` with the existing seeds; live camp construction and ordinary player use must still be observed in the client.

The first integration gate exposed an older reconnaissance assertion that checked the scout's final position after its 200-tick withdrawal could already have ended. The test now measures displacement while the extraction role is active and requires no player targeting throughout that window and afterward. Ordinary roaming can change its later position. The failed report is retained alongside the final verification; production movement and timing are unchanged.

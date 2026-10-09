# MACS keep-away archer

Implements [Source of Truth §9.13e](https://www.notion.so/39d7cfaa890181c1bad4f6babad80880) as a separate PR after merged mantlet #95. [Implementation and acceptance plan](https://www.notion.so/3e67cfaa89018156b69fce4104be6824). Live acceptance is pending; this is the first tuning build.

## Selection and evidence

The archer is a variant of the sword-counter family. It requires at least 0.90 historical `PLAYER_PREFERS_SWORD` confidence frozen before the encounter, eligible local sight, an empty offhand and initial distance greater than eight blocks. The distance requirement avoids equipping a ranged weapon when close combat has already started. Shield remains eligible from 0.75. Variant selection uses frozen performance; an untried archer wins a tie at the higher gate, while previous observed results can favor the shield. Existing family costs, attention admission, contradiction cooldowns and one commitment per shared player encounter still apply.

A selected archer does not inspect current weapons, inventory, health, suit punctures or attack inputs. It aims only at a visible subject. Lost sight cancels drawing; it may strafe out of locally detected cover or navigate to the last point it actually saw for at most 100 ticks, then releases the bet. It cannot transfer the quiver to another player.

## Execution and provisional tuning

The visible generated bow fires 16 ordinary physical arrows at most. Drawing takes 20 ticks, with 20 ticks between draws; velocity is 2.0 and inaccuracy 6. Preferred range is roughly 16 blocks, with ordinary navigation beyond 18 and shooting limited to 24. These are provisional execution defaults, not final balance values.

After the first live replay, the owner requested faster flight while retaining the existing spread and cadence. The 2026-09-26 tuning raises velocity from 1.6 to 2.0, scales the vanilla-style loft coefficient from 0.2 to 0.128, and scales base arrow damage by 0.8 (normally 2.0 to 1.6) to offset the velocity increase. Air drag, gravity and integer damage rounding mean damage is approximately comparable, not identical at every distance. Both combat presets retain Normal-skeleton spread; the bow aims at the subject's visible position at release without movement prediction or confidence-based accuracy boosts. Active bow execution still uses ordinary navigation without digging or bridging; full builder execution returns after ordinary combat resumes.

The existing 30–59-tick strafe cadence and safe-footing movement constrain its close movement to 0.12 blocks per tick. Inside eight blocks it adds the existing backoff motion; a player sprint can close the gap. Once per second it considers at most eight nearby standing points for existing cover and biases the same strafe toward cover between draws, away when drawing. It builds no archer pillars. Normal recovery retains its existing retreat cover.

At three blocks it releases the bow and resumes ordinary combat. After arrow 16 it reaches back for an empty quiver for 20 ticks, lowers the bow and resumes combat. Both paths spend the same bet; neither grants a fresh shield commitment. A real hit also cancels the archer for local defense after recording observed damage. Recovery can suspend execution while retaining the remaining quiver. The generated bow is removed on release, death, load and erasure; all generated arrows use `Pickup.DISALLOWED`.

The owner chose existing EVA puncture balance for initial testing. Architect-owned arrows follow `SuitIntegrityHandler` exactly: currently a 12% configured Architect chance, the existing protection rules, 300-tick grace after a puncture and existing concurrent cap. No special pause, probability reduction or per-archer puncture cap is added. The archer cannot read suit integrity. Arrows retain normal collision, shield and damage behavior.

Presentation uses the dedicated `entity.architect.shoot` event with the subtitle "Architect shoots arrow" and three short original bow-snap/soul-breath variations. Client-local arrow ticks add one small soul flame along the traveled segment and one soul particle every fourth tick, only for arrows whose locally resolved owner is an ordinary Architect. Stationary/embedded arrows emit nothing, and ordinary player/skeleton arrows are excluded. This adds no damage effects, particle network packets, global entity searches or persistent tracking. Normal particle settings and distance culling apply; if the owner is not tracked locally, the trail is omitted. Sound provenance and regeneration are recorded in `tools/audio_sources/architect/SOURCES.md`.

## Persistence, multiplayer and bounds

Maeve save schema is 6. Versions through 5 load normally; the new bounded performance key is `PLAYER_PREFERS_SWORD:ARCHER`. Old sword results remain shield results. An unfinished use becomes UNKNOWN on reload, its execution and generated bow are released, and its saved encounter bet remains spent. Loading a version newer than supported is rejected. Erasure does not archive beliefs; the permanent violation ledger is unchanged.

Each subject retains separate evidence and variant results. Only the selected ordinary Architect owns its quiver; Masters, copies, Aggregate reinforcements and Hearth roles retain their exclusions. Subject loss and role/lifecycle invalidation clear the weapon. Existing attention and 48-block observation bounds remain. Local cover scans never request chunks; projectile count is capped at 16 per commitment. No new periodic global player scan, terrain construction or movement planner is introduced.

## Live test handoff

Use the separate **MACS Keep-away Archer - Normal** world. The prior mantlet save and log are backed up under `build/macs-archer-evidence/pre-archer/` and remain in the mantlet worktree.

1. Run `/function macs_archer:setup`, then click Practice (or `/function macs_archer:practice`). Hit the training Architect once with the sword in slot 1. A blocked hit does not advance practice.
2. After each successful hit, click the yellow skip-gap message (`/tick sprint 620t`). Wait for **Ready** and **Sprint completed**, then start the next practice. Complete five actual encounters. Never sprint an active fight. Production confidence increments, deduplication and the 600-tick encounter gap are unchanged.
3. Run `/function macs_archer:start`. Begin at range briefly, then use the shield and try closing for sword combat. Report what the bow, movement and transition communicated before inspecting diagnostics.
4. Finish with `/function macs_archer:finish`, then run `/fd maeve dump` directly in chat. Actor traces are also exported every ten seconds. `/function macs_archer:abort` ends an encounter safely.

For another encounter use `/function macs_archer:again`, skip the empty gap, then start. Prior outcomes and contradictions can change the next selection. `/function macs_archer:warmup` adds one real sword encounter when training is complete. `/function macs_archer:reset` clears tactical history in this disposable world and restarts its five-hit exercise; do not use it in a valued world.

Required live checks: bow/kiting readability and usable sprint approach; real arrow obstruction by existing cover; shield counterplay; all 16 shots followed by an obvious empty transition; no generated loot; Normal/Brutal pressure comparison; actual EVA puncture/patch experience. Multiplayer is deferred by the owner (2026-09-26); it is not a completed live check. Terrain/performance soak remains separate. Automated success does not satisfy these visual and balance gates.

### EVA puncture and patch trial

Prepare **MACS Archer EVA - Normal** with `tools/prepare_macs_archer_playtest.py --eva --source <closed-completed-archer-save> --destination <new-save>`. The source is left intact, and its actual learned beliefs, five completed practice rounds and strategy results are copied. This setup does not clear or inject Maeve evidence. Use this separate world for the commands below.

1. Run `/function macs_archer:eva/setup`, then `/tick sprint 620t`. Wait for **Ready** and **Sprint completed**. This empty gap has safe air and no test actor.
2. Run `/function macs_archer:start`, then `/fd suit status verbose`. Confirm **Sealed suit: true** and **Exposure: vacuum**. The real fight has late-Phase-6 vacuum, fresh unenchanted EVA armor, one full Mk.III tank in slot 9 and eight permanent ORSA patches in slot 8. Each new fight restores the trial kit and starts intact; existing damage, oxygen, puncture, grace and patch timing rules run normally during combat.
3. Keep bow range and let some arrows hit without the shield. Avoid shooting or rushing the actor until the puncture check is complete, since either can end its ranged commitment. After the first puncture warning, use existing stone cover and hold Use with the patch for two seconds. Observe whether the window is usable and whether a hit interrupts the channel. Run `/fd suit status verbose` after patching; an intact seal is the success signal. A round with no puncture is possible at the real 12% chance and is not a failure.
4. Run `/function macs_archer:finish`, then `/fd suit status verbose` and `/fd maeve dump` directly in chat. Finish/abort removes the test actor and restores safe air but retains suit damage for inspection. The next setup/start repairs and restocks. Death also ends the trial, but the normal respawn path clears suit integrity. Use `/function macs_archer:again`, skip the empty gap and start for a repeat; do not sprint an active fight.

The puncture default is 12% for Architect-owned arrows, a 300-tick grace period, at most two open punctures, and 40 ticks for an ORSA patch. The trial does not guarantee a puncture, alter those values or stop shooting while the player patches. Existing cover may hide the player long enough for normal sight-loss fallback; record that outcome too. Puncture and actor traces, the owner's patch experience and direct suit diagnostics form the acceptance evidence.

## Verification

Policy checks exercise inclusive thresholds, frozen history, malformed variants, one bet, separate performance results and reload behavior. Native checks exercise real entity selection, finite projectile production, pickup restrictions, empty fallback, sight loss, close rush, partial snow, damage/puncture events, role separation, save/load/erasure, and the exact live datapack's parser and sword-hit advancement. Existing shield, pillar, mantlet and navigation cases remain required. The full release candidate must pass `./gradlew architectVerify architectMonkey --console=plain` with the existing fixtures and seeds; exact commit results are published on the PR.

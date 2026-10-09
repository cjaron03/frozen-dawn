# ORSA Ration Warmer

## Ownership — October 8, 2026

Owning checkout: `/Users/jaroncabral/.codex/worktrees/orsa-ration-warmer/minecraft-mod`, branch `feat/orsa-ration-warmer`. Baseline: `origin/feat/maeve-director` at `2bdc04fa90795522a3c519862fca3ad40b4916a7`, containing the three playtest fixes merged through PR #103. The root checkout's Scribe work and existing saves are separate.

Implemented in this checkout. The first full `architectVerify` pass completed with 261 native tests and zero failures. The final full gate also passed after the charge-aware JEI refinement: 665 unit tests and 261 native tests, zero failures. The owner confirmed the visual pass after the bulk/stow revision and requested commit/push. The implementation, manual, texture provenance and native regression evidence are ready for branch delivery.

## Device and accounting

The shaped recipe uses iron in the four corners, copper in the four edge centers and one redstone in the center. One reusable, nonstacking warmer is crafted empty. Shapeless crafting an existing warmer with redstone restores one charge per dust, up to four, preserving the device's other components. Redstone may be stacked in one slot or spread over up to four slots. The recipe fills only missing charges and leaves unused dust in the grid. JEI provides charge-aware one-, two-, three- and four-dust recipe variants; the stacked input also works in the 2×2 inventory grid. Both recipes have native Patchouli crafting pages directly inside the ORSA Field Manual’s Food Spoilage entry, as well as the dedicated Ration Warmer entry under Getting Started.

Main-hand warmer, offhand food, right-click once. The server consumes one charge at activation and binds the job to the exact offhand food stack, target count and dimension. Once activated, the warmer can be put away or the main-hand item/slot can change without cancelling the thaw. Only one job can be active per player. The entire stack thaws in 200 simulation ticks while movement remains possible; actionbar progress updates every ten ticks. Active thaw suppresses further food frost, so near-ruined Frozen food cannot acquire new permanent damage during heating. Existing frost-ruined damage is never removed.

Chilled/Frozen food becomes fresh; already ruined food recovers only to its damaged Frozen floor (2400 ticks), retaining its `frost_ruined` marker. After completion the food has 600 ticks before frost resumes, using the persistent Overworld game clock across dimensions. Fresh and resistant food use no charge. The device provides no body warmth, oxygen or frostbite protection.

Removing or replacing the offhand food, changing its count (including eating or splitting), changing dimension, death or logout cancels. Main-hand changes and stowing the warmer do not cancel. The activation charge is not refunded. Jobs are not saved; reconnect/restart cannot complete a cancelled or abandoned job. A completed stack's warm window survives item serialization and splitting normally.

Four charges, ten-second thawing and the thirty-second window remain first-pass values, not a completed balance judgment.

## Separate frost-cache repair

The old slot cache reset itself from stale item components every five seconds, discarding four exposure updates. It could also carry the old slot's frost to a replacement item. Stack components are now authoritative and updated once per second; inventory moves, copies and saves retain the actual value. A client-only hook suppresses hand re-equip only when frost/window components differ, retaining normal animation for slot, count and other data changes.

An unchanged-behavior baseline with extracted inventory stepping reproduced both failures: five seconds at −90°C yielded 50 ticks instead of 250, and a fresh replacement inherited a prior stack's frost. The two native tests pass after repair. Original failed XML, log and build fingerprint are preserved under `build/ration-warmer-evidence/baseline/`; the first fixed full report/log are alongside them. Baseline rates and damage/eating rules were retained; the guide's former fixed-time claims now use the actual frost thresholds and a temperature-specific example.

## Texture provenance

The owner selected the heater-brick concept. The shipped image is a transparent 32×32 sprite derived with nearest-neighbour sampling from the built-in image generation output. All three originals, pixel versions and exact prompts are preserved in [visuals/ration-warmer](visuals/ration-warmer/README.md). The unused coil/disc and folding designs remain available. No other mod's asset was used.

## Native checks and live handoff

New native checks cover actual item activation and registered server ticking; full-stack thaw at 200 ticks; movement; cold during heating; the 600-tick window boundary; fresh/resistant/empty/wrong-hand rejection; permanent damage; cancellation/replacement/splitting/logout/death/dimension change; native crafting-menu shift-click, all charge levels and capacity; metadata preservation; item save/wire codecs; charging recipe wire codec; frost accumulation/thaw/slot movement; hand-animation filtering; and parsing all ten live-world functions at permission two and checking that both book-give commands produce exactly the same actual ORSA Field Manual as the production starter-book API. Headless checks are independent of the owner's visual acceptance and a real multiplayer/restart replay.

Prepared save: `run-lab/saves/ORSA Ration Warmer Check`. It uses fresh default-terrain seed metadata from a closed Emergency EVA save. No chunks, inventories or player/attachment history are copied. `ration-warmer-preparation.json` records source hash and destination. The preparation tool refuses to overwrite an existing save.

The first join creates an outdoor bedrock pad at (0,65,6), locks late Phase 6 progression, equips ordinary EVA and oxygen tanks, supplies recipe materials and guide, disables natural mob spawning and gives QA resistance/regeneration. No heater or roof is present. Survival remains active so normal food frost applies. `rwcheck #built=1` marks preparation complete. The fixtures are explicitly injected QA state and do not establish survival balance.

Launch only from this checkout with Java 21:

```sh
JAVA_HOME=/Users/jaroncabral/.gradle/jdks/eclipse_adoptium-21-aarch64-os_x.2/jdk-21.0.10+7/Contents/Home ./gradlew runClientLab --console=plain -PfdLabWorld='ORSA Ration Warmer Check'
```

1. Right-click with the supplied warmer and 64 Frozen bread; walk while the ten-second progress fills. Expect fresh food, three remaining charges, and a thirty-second tooltip window. Wait outside until normal frost returns.
2. Open chat and use the clickable chilled, ruined, fresh and resistant fixtures. Fresh must be activated immediately before it naturally chills. Ruined food retains its damaged Frozen status; resistant food costs nothing. `/function ration_warmer_check:controls` reprints the controls.
3. Start another thaw, then switch hotbar slot or stow the warmer: the original food should finish in your offhand. Start another and remove, replace or split the food: expect cancellation, no completed thaw and one spent charge. Reconnect cannot finish that abandoned job.
4. At the crafting table (2,65,2), use JEI recipe lookup (`R`), the `+` transfer and shift-click output for assembly and recharge. Empty warmer + four dust in one stack should output four charges. A two-charge warmer with four dust should consume two and leave two. Repeat with dust across separate slots and the 2×2 inventory grid. Capacity stops at four. Check the inline Food Spoilage pages, recipes, sprite readability and charge display.

Do not overwrite this save or automatically reset an owner-visible attempt. Reopening the chat/inventory pauses singleplayer simulation; return to the game for timing checks. Report the owner's first description before diagnosing any surprising result.

Latest verified jar SHA-256 (bulk/stow revision): `fccbe6ab7650312084db6280ba05a0ba026bf28d8fc6fa71c1488fd5cba8a57f`. The smoke profile jar matches; dependency jar hashes are unchanged. Final log, XML, source fingerprint and verification summary are under `build/ration-warmer-evidence/`. Existing Gradle/NeoForge deprecation warnings remain.

Live handoff confirmed: the Java 21 Gradle client session `18896` opened **ORSA Ration Warmer Check**. Bridge session `826575a9-8217-4c3a-a9de-a8893f5da4a1` reported `rwcheck #built=1`, the player at (0.5,65,6.5), game time 13, and singleplayer menu pause. JEI registered subtypes, recipe extensions and transfer handlers without errors; the ready message appeared. The saved initial inventory contains four iron, four copper, sixteen redstone, both warmers, guide, ordinary EVA, four Mk3 tanks and 64 offhand bread. No test was advanced after setup. The owner's visual pass is pending. Read-only bridge responses and startup log are preserved in `build/ration-warmer-evidence/`. Live Gradle output: `/private/tmp/ration-warmer-client.log`; native log: `run-lab/logs/latest.log`.

Handbook follow-up: the QA pack previously used obsolete `minecraft:custom_data` to identify the Patchouli book. It now uses the registered `patchouli:book` data component. The exact-world, built=1, handbook=0 guarded repair only gives one proper manual and marks completion; it does not replace held stacks or rerun setup. Both recipes and concise warmer instructions are now included directly in Food Spoilage.

The handbook follow-up `architectVerify` pass completed with 261 native tests and zero failures; unchanged unit checks were up to date. The native book assertion compares both give-command results to `StarterBooks.createGuideBook()`. The actual paused-world repair marker is `#handbook=1`, `#built` remains 1, and the supplied-manual chat message appeared. Bridge function dispatch returned zero even though effects completed; scoreboard/chat were inspected before any further action, and no give was repeated. Report, build fingerprint, logs and responses are preserved in `build/ration-warmer-evidence/handbook-follow-up/`. The corrected smoke jar has the hash above; dependency hashes remain unchanged. Use F3+T in the active Gradle client for the new Food Spoilage pages. Restart the separate smoke client when loading the updated packaged jar.

October 8 follow-up: the owner requested uninterrupted thaw after putting the warmer away, with the original food still held offhand, and bulk redstone charging. Native ResultSlot consumption is scoped to this recipe because vanilla removes only one item per occupied crafting slot. Matching, assembly and remaining-item queries stay read-only. The per-slot plan is captured on take and respects native ordinary-click, shift-click, grid offsets and a blocked inventory. Additional native matrices cover stacked/spread dust, partial charges, excess preservation, 2×2/3×3 grids, metadata and ordinary vanilla crafting. The prior implementation source is preserved under `build/ration-warmer-evidence/bulk-revision/before/`.

Bulk/stow verification: `architectVerify` passed with 665 unit tests and 263 native tests, zero failures. The new bulk matrix covers 176 native click/shift-click cases across 2×2/3×3 grids (including grid offsets), plus full capacity, invalid ingredients, blocked inventory and ordinary crafting. A final full build passed after preserving localization formatting; every jar entry is byte-identical to the native-tested package except the formatting-equivalent `en_us.json` and its source fingerprint. Both jars and equivalence checks are recorded under `build/ration-warmer-evidence/bulk-revision/verified/`. The smoke jar matches the latest hash and all dependency hashes remain unchanged. The owner saved and quit; all 63 closed-world files were backed up and byte-verified before updating only the QA pack. Java changes require restarting through Gradle; reopening the existing save preserves its built/handbook markers and does not rerun setup. The owner accepted these revisions ("perfect") before requesting commit and push.

Patched client reopened successfully through Java 21 `runClientLab`, terminal session `18571`, bridge session `eb2b0de5-bab3-4fd7-88f3-2fb17004929f`. The existing **ORSA Ration Warmer Check** retains `#built=1` and `#handbook=1`; the startup log contains no repeated preparation READY message. Updated controls were printed without replacing inventory. Their bridge function dispatch again returned zero, so actual chat effects were checked and no function was repeated. Recipe/JEI/mixin startup shows no feature errors. Startup log and responses are preserved with the bulk revision evidence. Live Gradle log: `/private/tmp/ration-warmer-bulk-client.log`.

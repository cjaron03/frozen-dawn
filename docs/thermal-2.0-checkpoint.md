# Thermal 2.0 checkpoint

Owning checkout: `/Users/jaroncabral/.codex/worktrees/thermal-2-0/minecraft-mod`.
Branch: `thermal-2.0`. Base: `8303073fef1b8f8bd82ccb4f6f8c434cc3275d69` from the pushed atmospheric-breach branch.
Notion source: [Thermal Model and Heater Control](https://app.notion.com/p/3f47cfaa890181b9a213c52a3d2c9966), including Additions (2026-10-09).

Current acceptance: depth and room-support replays are accepted and committed through pushed checkpoint 3bb1538. The two-node thermal slice is local, uncommitted and headless verified; its fresh owner visual/balance replay is pending. See the latest section below.

## Agreed order

1. Ground temperature and a before/after depth replay.
2. Pressure integration: canonical identity, boundary faces, typed geometry/material/air-state changes and keep-alive.
3. Air/structure energy model, conservative refill/topology changes and persistence.
4. Heater control, thermostat, thermometer, pressure-preserving heat exchanger and tuning.
5. Rimewood resumes in its existing separate worktree.

## First slice

The surface profile is extracted into `SurfaceTemperatureCurve` without changing its anchors, difficulty scale or unscaled +3C false-calm rebounds. PhaseManager shares its phase bounds. Above Y 64 the existing altitude modifier remains; below it TemperatureManager samples delayed ground cooling. This automatically feeds its existing player, food, mob and loaded-only catch-up callers. Shelter, heater sources, Core sources, Blast Pit and vent warmth floors keep their existing behavior.

Fixed surface Y 64, depth capped at 128, gradient 0.30 * geothermalStrength per block, progression clamped at 1. Default/Cinematic diffusivity 8000; Brutal 12000 (first-pass 1.5x). The new common config `temperature.groundDiffusivity` is preset-managed and persisted. The existing whole-day phase clock remains the clock for temperature queries; this slice does not change phase timing. No terrain/heightmap queries are used by the background model.

The 129 x 1001 lookup interpolates depth and progress. Cold and false-calm responses are kept separately because the existing preset scales cold but not false calm. Table construction uses centered surface increments, an independently verified erfc approximation, and at most four cached diffusivities. New or existing saves deterministically derive the same curve from their current progression and config; no heat state is written by this slice. End-of-apocalypse deep refuge is intentional.

`/fd world status verbose` reports the background at Y 64, 32, 0, -32 and -64 and the actual diffusivity. These readings exclude local shelter and heat sources.

## Numerical reference

`python3 tools/thermal_ground_reference.py --reference src/test/resources/thermal/ground-reference.csv --comparison build/thermal-evidence/before-after.csv`

The reference uses Python math.erfc and 20,000 time increments, independently of the Java approximation/table. All three presets, five depths and four progression points are compared with a 0.025C tolerance. The frozen legacy phase/depth curve supplies the before column. The old Notion estimates do not reproduce the exact current surface curve: the canonical Default model ends near +9.9C at Y -64, rather than +6C. Do not silently adjust heater balance to make an approximate table pass.

## Remaining work and acceptance

Room heating and pressure hooks remain design only. Notion has been corrected so refill transfers energy from structure to air; splitting allocates existing energy without creating heat from extra faces; reload reconciles saved membership, rather than assigning everything through a single anchor. New partitions/material have an explicit initial energy budget.

Verification and owner visual acceptance must be recorded after the first-slice checks complete. No existing saved world or running client has been modified or restarted for this work.

## Prepared visual replay

New metadata-only save: `run-lab/saves/Thermal Ground Check`. Creative safety, an open-top glass-lined shaft and platforms at Y 64, 32, 0, -32 and -63. No heaters or oxygen sources. The bottom is Y -63 because a player needs a floor at the world's minimum Y -64. Click the depth, stage and preset controls; wait at least three seconds for the existing two-second temperature packet and HUD smoothing. Each preset click selects its clamped end state, and phase buttons then select P5/P6/vacuum using the existing whole-day command rounding. `/function thermal_ground_check:controls` restores the menu without resetting the scene. No bridge mutation functions have been authorized or added.

Prepare again only into a new destination: `python3 tools/prepare_thermal_ground_playtest.py --source /absolute/path/to/level.dat --destination /absolute/new/save`. The helper refuses overwrite and records the source metadata SHA-256; it copies no chunks, player inventories or mod SavedData. For an identical old-build comparison, use a separately prepared world with the same source metadata in the atmospheric-breach checkout; never replace a completed world's jar/history in place.

Launch from this checkout with Java 21: `./gradlew runClientLab --console=plain -PfdLabWorld='Thermal Ground Check'`. The current client must be safely saved/closed before changing its owning Gradle session. Owner visual acceptance remains pending.

## First regression attempt

Two Emergency EVA tests assumed bedrock-depth sealed rooms were lethally cold. The new refuge correctly invalidated those preconditions. The tests retain their protection, expiry, oxygen and service assertions but move only these cold fixtures and their players to Y 64. The failed run is preserved under `build/thermal-evidence/first-gate`. This fixture correction does not change EVA production behavior.

## Final automated verification

`./gradlew spotlessApply architectVerify --console=plain` passed: 684 unit tests, 288 native GameTests, all 283 gate-required cases/reports. Seven unit tests include 60 independent reference samples; three new native tests exercise runtime cave/shelter/loaded-only queries, unchanged surface/altitude behavior, and preset application/config persistence. Both relocated EVA fixtures pass their original protection and expiry contracts. Source fingerprint matches the verified inputs.

Source SHA-256: `12ed1dd4fb3daacc0d7d10a91ed69f8fb9e30c64c72d6e3f007e316b065d5d8d`.
Jar and installed smoke jar SHA-256: `fe619d6853430513ee9ebc132d6eddca5bfded385c1bf363b6556b60c85ec5d5`.
Evidence: `build/thermal-evidence/final/verification.json`, native `report.xml`, `ground-unit.xml`, `before-after.csv` and Gradle log. The replaced smoke jar is retained as `smoke-previous.jar`. Existing NeoForge EventBusSubscriber removal warnings and Gradle deprecations remain.

At the automated-verification checkpoint, code was uncommitted and the prepared save had not been opened. The subsequent launch and owner depth acceptance are recorded below.

## Live launch

Launched with Java 21 using `./gradlew runClientLab --console=plain -PfdLabWorld='Thermal Ground Check'`. Codex terminal session 96525; live log `/private/tmp/thermal-ground-client.log`. Bridge session `5b7c97fe-fead-4f6f-bfa4-5cae2c8f3515`. Read-only snapshot confirms player Dev at (0.5,64,0.5) in the correct world; setup scoreboard `tground #built=1`. Chat lists every depth/stage/preset control and no function parse errors were reported. Snapshot was menu/lost-focus paused, not tick-frozen. Resume the game to see the HUD update.

Evidence: `build/thermal-evidence/live-launch`. Owner visual/balance acceptance remains pending. First compare Default End at all five platforms, then hold Y0 or Y-32 and advance P5 -> P6 -> Vacuum -> End using the buttons. Preset buttons intentionally select End; switch Brutal only after the Default comparison. No existing world or other active client was closed or altered.

## Owner depth acceptance — October 9, 2026

Owner report: “ok did the depth test, looks right. check it real quick before we move on”. Preserve this as the visual depth pass; it is independent of the earlier headless checks.

Client log confirms surface -> shallow -> zero -> deep -> bottom, followed by P5/P6/vacuum/end and Default/Brutal controls at the bottom. Fresh read-only bridge snapshot confirms Thermal Ground Check, player Dev at Y -63, gameTime 1514, paused from menu/focus and not tick-frozen. Setup score remains tground #built=1. No new test errors appear in the runtime log.

Saved apocalypse state after the last pause is Brutal, 744000 ticks (day 31 of 50, progress 0.62), with matching config diffusivity 12000 and geothermalStrength 0.5. Independent numerical reference gives approximately +3.7C background at the current Y -63. This is early Phase 6; Brutal End at that depth is approximately -42.8C. The log does not capture every numeric HUD reading, so those values are reference calculations rather than newly observed HUD telemetry.

Evidence preserved in build/thermal-evidence/owner-depth-pass: acceptance.json, fresh bridge snapshot/score, full client log, config and saved apocalypse state. No player movement, phase changes, resets or replay mutations were issued during verification. Phase/preset controls were exercised at the bottom; a fixed Y0/Y-32 phase sweep and broader balance acceptance are not separately claimed. Multiplayer remains untested. Next agreed slice is pressure integration, followed by room air/structure energy.

## Depth slice commit

Committed locally on thermal-2.0 at the owner’s request after the depth visual pass. Includes the ground model, preset configuration/persistence, diagnostics, independent numerical reference, native/unit coverage, surface-cold EVA fixture corrections, QA-world helper and this checkpoint. No push or PR requested. The agreed next slice remains pressure integration; room energy and heater control follow it.

## Canonical room identity — October 9, 2026

Depth commit 842b0dd023773cbda0745f903b4b08a07c1a09e7 was pushed to origin/thermal-2.0 at the owner's request. The identity slice is subsequent, local and uncommitted work on the same branch.

RoomIdentityState saves dimension-local monotonically allocated room IDs with full prior air-cell membership in frozendawn_room_identities.dat. Existing saves initialize lazily when sealed rooms are queried. Cache eviction or rebuild no longer changes the identity. Exact unchanged memberships avoid dirtying the saved state on periodic checks. Lookup resolves numeric IDs first, avoiding repeated full-membership hashing per cell.

Reconciliation discovers all surviving sibling components before publishing new live rooms. Largest membership overlap claims the prior ID; equal overlaps use parent ID and minimum packed child cell for deterministic ties. Each parent can name only one child. Other split children receive fresh IDs; absorbed merge IDs are retired. Retired Room objects leave all live cell/wall/work indexes immediately, rather than lingering for 600 ticks. Membership, rather than a single stored origin, locates rooms after an origin is replaced or topology changes while the cache is absent.

Unknown or unloaded geometry suspends pressure queries while retaining membership for retry. Runtime inspection never loads chunks. Fully breached volumes retain dormant identity history for reseal; RoomAirState remains the independent authority on lost air. Geometry edits restart refill rather than cloning progress to multiple children. This slice does not implement heat/energy persistence or boundary-face and typed change hooks. The existing 600-tick idle cache eviction remains; lit-heater/thermostat keep-alive is a following integration task.

Seven required native regressions cover merge deduplication and one breach, split siblings and no fabricated air, removed origin with NBT reload, offline split/merge and query-order stability, material replacement/breach/reseal, unknown topology with recovery, and unequal split ownership. The first five-case run is preserved under build/thermal-evidence/identity-initial; its gate rejected two newly listed cases not included in that already-running build. All seven and all 295 native cases passed before the final numeric lookup optimization; that run is preserved under identity-before-lookup-optimization. Final verification for the optimized code follows below when complete.

Fresh metadata-only QA save: run-lab/saves/Room Identity Check, created from the closed Thermal Ground Check level.dat with source hash recorded in preparation.json. No terrain, inventories or mod SavedData were copied; the depth replay is preserved. Helper: tools/prepare_room_identity_playtest.py. Two glass rooms at surface height, Creative safety, Default End and no O2 source. Clickable owner controls merge/split, visit either side, fill the former origin and breach/reseal the exterior. Read room ID runs the actual /fd world status verbose diagnostic directly, avoiding suppressed output from datapack function command sources. No new bridge mutation functions are authorized.

Owner replay: record left/right IDs; merge and verify matching IDs on both sides; split and verify distinct IDs (one retains parent); save/quit and reopen to confirm both survive reload; replace the former origin and confirm the remaining cell keeps its identity. Breach/reseal can recover identity without restoring depleted air. Live visual/reload and multiplayer acceptance remain pending.

## Identity final verification

The final optimized source passes ./gradlew spotlessApply architectVerify --console=plain: 684 unit tests, 295 native GameTests and 290 required gate cases. All seven room-identity cases pass. Current source fingerprint matches the build; evidence is preserved at build/thermal-evidence/identity-final.

Source SHA-256: 988c795aeaf48800dd7008085c7ed80bd240b099221a2bd464b98d4e240165d2.
Jar and installed smoke jar SHA-256: 12a7e80433f581d6e5d848e134d0bffc841390e2548cbf9a0f0137f5faf5e867.
Only the smoke-test Frozen Dawn jar was replaced; its prior jar is preserved in the evidence folder. Existing NeoForge removal warnings and Gradle deprecation warnings remain. The identity implementation is local and uncommitted; the pushed depth commit remains origin/thermal-2.0. Owner live/reload and multiplayer acceptance remain pending.

## Identity live launch

Patched owning Gradle client launched with Java 21, ./gradlew runClientLab --console=plain -PfdLabWorld='Room Identity Check'. Terminal session 98251; log /private/tmp/thermal-room-identity-client.log; bridge session 0a23ce9d-1e4e-4064-9f2b-22dba5bc976d. The prior depth Gradle client had exited normally before launch.

Caught and corrected a namespace mismatch in the new QA helper/pack before the fixture ever initialized (#built=0). Preserved the original pack and diagnostic evidence, renamed only its namespace, corrected the load/tick hooks and received a successful bridge reload acknowledgement. A fresh temporary helper invocation reproduces the repaired pack byte-for-byte. This pack-only correction changes no compiled mod inputs or verification results.

Latest live snapshot confirms the correct new world, menu/focus paused, not tick-frozen and no Architects. Setup score remains 0: resume Minecraft for the first normal game tick to run the one-time Creative room fixture. No reset or duplicate setup was issued. Corrected load/tick/setup all target room_identity_check. Evidence: build/thermal-evidence/identity-live.

When ready, use Read room ID, Left/Right, Merge/Split; save/quit and reopen to compare persistence. Controls return via /function room_identity_check:controls after setup. New identity code remains local and uncommitted. Next requested slice would be boundary faces, then typed room changes and heater/thermostat keep-alive before the room-energy model.

Owner first live report: left room ID 3, right room ID 5; distinct-room check accepted. Owner could not reach Merge after verbose output pushed chat controls away. Condensed the QA controls into three button rows plus a short hint and reprint them on pack reload for an already-built fixture. Only the UI functions change; no merge/split, movement or reset is issued by this reload. Remaining merge/split/reload acceptance is pending.

## Owner merge/split log check

Actual diagnostic sequence: 16:47:24 left ID 5 / 36 cells, 16:47:30 right ID 3 / 36 cells, merge function at 16:47:33, then ID 3 / 81 cells at 16:47:35. Split function at 16:47:42 returns ID 3 / 36 cells at 16:47:43. The latest logged left/right ordering differs from the earlier owner shorthand; preserve the actual sequence rather than rewriting the original report. Merge geometry and survivor identity are confirmed at the queried position; post-merge readings on both sides and both split siblings have not yet been logged.

After the breach function, a diagnostic reports ID 3 / 855 cells. Owner clarified “yea that was me sorry lol” when this unexpected enlarged sealed volume was raised. Treat it as a modified fixture, not default-fixture breach acceptance or a proven pressure-code bug. No repairs, room edits, resets, movements or phase changes were issued during this inspection. Log and structured observations are preserved under build/thermal-evidence/identity-owner-merge-split. Next: read both split IDs, save/quit and reopen to compare the same IDs; removed-origin and live reload acceptance remain pending.

## Current split baseline for save/reopen

Owner resealed the modified room, then reported “8 and 7.” Client diagnostic log confirms right ID 7 / 36 cells at 16:50:15 and left ID 8 / 36 cells at 16:50:22. Both sides are distinct sealed volumes of the expected fixture size. These are the current baseline IDs after the owner's geometry changes; do not compare the upcoming reopen against the earlier merge ID 3. Preserved log and baseline at build/thermal-evidence/identity-owner-split-baseline.

Next owner step: save and quit, reopen Room Identity Check without additional geometry edits, then read left and right. Expected left 8, right 7, each 36 cells. Persistence acceptance remains pending; no world mutation was issued to collect this evidence.

## Owner save/reopen acceptance

Owner reported “yup. check. 8 and 7”. Verified actual server stop at 16:51:23, final saves at 16:51:24 and a new integrated-server start at 16:51:26. Bridge session changed from 0a23ce9d-1e4e-4064-9f2b-22dba5bc976d to ead0e46c-ed9a-4bb6-bf63-3a816d56c6fe. Post-reopen diagnostics show left ID 8 / 36 cells at 16:51:33 and right ID 7 / 36 cells at 16:51:35, matching the recorded pre-reopen baseline exactly. Owner live save/reopen acceptance passes.

Preserved log, current status and acceptance.json under build/thermal-evidence/identity-owner-reopen. Merge geometry, distinct split siblings and actual world save/reopen are now demonstrated live. Removed-origin and additional topology cases pass native regressions; their separate owner visual pass and multiplayer remain unperformed. No world mutations were issued during this verification. Identity changes remain local/uncommitted on thermal-2.0; the next agreed implementation is boundary faces for heat transfer.

## Boundary faces implemented and verified

Continuing after owner identity save/reopen acceptance, RoomAtmosphere.Geometry now exposes immutable BoundaryFace(airCell, outwardDirection) contacts, with adjacent wallCell derived from that pair. Every exposed side is retained even when several contacts share one physical wall block. The unique walls set remains available for pressure and airlock consumers. Only fully SEALED geometry exposes complete faces; OPEN and UNKNOWN expose none. Controlled airlock inspection retains door contacts while ordinary room geometry traverses an open door. No thermal energy model or conductance is implemented in this slice.

Five required native cases cover exact cuboid surface areas and directions, all six contacts of an interior block, merge/split shared partitions and NBT reconstruction, incomplete/open geometry, and both halves of an open airlock partition. ./gradlew spotlessApply architectVerify --console=plain passed: 684 unit tests, all 300 native GameTests and all 295 required cases. Current source matches build fingerprint 0304e8fca6e2de38335ca0cd2dee4d91da561f7aa3bdf60ec695e3bb44e12477. Jar and installed smoke jar SHA-256: 941e97778a2bd8ecc110aa60caeb758be3952f97c4e3afd1cbea48185cc1d720. Evidence and prior smoke jar: build/thermal-evidence/boundary-final. Native first-boot server.properties fallback and existing deprecation warnings precede successful startup and completion; no failing cases.

Prepared Room Boundary Check from Room Identity Check seed metadata only; copied no regions, player data or mod SavedData. Generator refuses overwrite. Its full-height clearing makes the exterior sky-connected even when seed terrain is above the fixture. Generated function bodies and load/tick hooks match the helper exactly. This preserves the owner's modified Room Identity Check unchanged.

New client launched through owning Java 21 ./gradlew runClientLab --console=plain -PfdLabWorld='Room Boundary Check', terminal session 16350, log /private/tmp/thermal-room-boundary-client.log. The old identity Gradle client had exited normally with all dimensions saved before launch. Owner boundary visual acceptance pending. Expected baseline each: 36 cells / 66 faces; merged: 81 / 126; left interior block: 35 / 72; removed block: 36 / 66. Remove the block before merging. Breach should reject a sealed left room; closed partition protects right. Read faces invokes /fd world status verbose directly. Reprint controls with /function room_boundary_check:controls.

Identity and boundary changes remain local and uncommitted on thermal-2.0 (HEAD/upstream depth commit 842b0dd). Next agreed slice: typed GEOMETRY / WALL_MATERIAL / AIR_STATE changes, followed by lit-heater/thermostat keep-alive, before the room-energy model. Physical shared-wall material capacity must be accounted once by that later model despite multiple oriented contacts.

Boundary live launch confirmed bridge session 1daa3900-3569-4423-b049-3f182986920a in the correct world. One-time fixture completed (#built=1), player Dev at (-2.5,65,0.5), menu/focus paused, not tick-frozen, no Architects. Setup/control messages loaded without function parse errors. Read-only snapshot/scores and client log are preserved at build/thermal-evidence/boundary-live. Owner can resume and click the controls; no test mutations were issued through the bridge.

## Owner first boundary check

Owner reported “ok i think it worked, but the function isnt workiung anymore sadly”. Log confirms baseline ID 2 / 36 cells / 66 faces at 23:54:57 and right ID 5 / 36 cells / 66 faces at 23:55:26. Owner added the fixed left interior block and merged without removing it. Actual merged reading ID 5 / 80 cells / 132 faces at 23:55:34 is correct: one fewer air cell and six extra block contacts relative to the unobstructed 81 / 126 room. Baseline and merged-with-block live geometry are verified; split and save/reopen remain pending.

At 23:55:37 the unknown function was room_identity_check:controls, from the previous identity world. Current world is Room Boundary Check and the correct command is /function room_boundary_check:controls. Existing current-world buttons target the correct namespace. No mod or pack fix, reload, reset, movement or fixture mutation is needed. Preserved log and observations at build/thermal-evidence/boundary-owner-first.

## Owner pillar removal and split verification

Owner reported pillar removal and split. Log confirms remove_pillar at 23:56:29, followed by ID 5 / 81 cells / 126 boundary faces at 23:56:30. Split at 23:56:37 is followed by ID 5 / 36 cells / 66 faces at 23:56:38. Fresh bridge status has the player in the left room (-2.5,65,0.5). Merged unobstructed volume and queried left split geometry pass live. Right sibling's post-split reading and boundary save/reopen remain unlogged; native regressions already cover both. Preserved client log and observations in build/thermal-evidence/boundary-owner-split. No world mutations issued for this inspection.

## Owner right sibling and actual save/reopen

Right split sibling reads ID 7 / 36 cells / 66 faces at 23:57:19, distinct from left ID 5. Server stops and completes all dimension saves at 23:57:22; new integrated server starts at 23:57:24. Post-reopen right remains ID 7 / 36 / 66 at 23:57:30. Bridge session changes to d0dd8da1-6094-4bf2-9e68-718c5ae94271. Both split geometries and right actual save/reopen pass live; left post-reopen reading remains unlogged (expected ID 5 / 36 / 66). Evidence: build/thermal-evidence/boundary-owner-reopen. No world mutation issued to inspect this result.

## Owner boundary save/reopen acceptance complete

Owner reported left ID 5 / 36 / 66. Log confirms left function and diagnostic at 23:58:02: ID 5 / 36 air cells / 66 boundary faces, in reopened bridge session d0dd8da1-6094-4bf2-9e68-718c5ae94271. Both split rooms now match their pre-save identities and geometry exactly: left 5 / 36 / 66, right 7 / 36 / 66. Owner live baseline, interior-block merged contacts, unobstructed merge, both split siblings and actual save/reopen acceptance pass. Separate live airlock/open-geometry checks and multiplayer remain unperformed; native boundary regressions pass. Updated evidence at build/thermal-evidence/boundary-owner-reopen. Changes remain local/uncommitted. Next agreed slice: typed room-change events, then heater/thermostat keep-alive.

## Typed room changes implementation

Owner requested GEOMETRY, WALL_MATERIAL and AIR_STATE notifications after the completed boundary replay. Re-fetched Thermal Model and Heater Control (including Additions 2026-10-09); contract remains the two-node air/structure model. This slice provides integration events, not temperature nodes or heat accounting. The Notion status paragraph still describes the older depth-only checkpoint; current implementation and owner evidence are tracked here.

RoomChangeEvent is a non-cancellable NeoForge server event, published after authoritative mutation. GEOMETRY carries immutable prior/current live snapshots plus prior saved memberships, including uncached/dormant overlap; incomplete inspection publishes suspension with complete=false rather than declaring a breach. Merges/splits publish only after all live room indexes are reconciled; unchanged periodic inspections are silent. WALL_MATERIAL reports airtight block replacements and every affected shared-boundary room, keeping geometry, identity and refill timing intact. Same-block cosmetic indicator changes are excluded. Thermal owns future insulation-layer indexes and conductance invalidation; this event tracks immediate pressure boundaries only.

RoomAirState binds to its owning ServerLevel at get() and publishes AIR_STATE after an actual persisted depletion-bit change. Payload contains only changed cells, immutable positions, target depletion and canonical IDs resolved from saved membership without flood-fill or chunk loads. NBT load and repeated/no-op transitions are silent. Exterior vacuum cells without room membership emit no room event. Core recovery, airlock pumps, emergency valves and breach evacuation therefore use the same authority. Runtime-only diagnostic totals appear in /fd world status verbose and reset with the cache; they are not saved thermal state.

The first verification attempt passed all five typed room cases but failed the real controller integration case: changing the panel indicator to green was also classified as WALL_MATERIAL. Preserved complete failed report, log, source fingerprint and diagnosis under build/thermal-evidence/change-first-attempt. Corrected cosmetic classification and retained the regression. Added a loaded Core refill test that swaps wall material at tick 60 and still requires recovery by tick 105. Final full verification is in progress (session 63544, /private/tmp/thermal-room-change-verify-final.log).

Prepared fresh Room Change Check from closed Room Boundary Check seed metadata only; existing worlds unchanged. New helper tools/prepare_room_change_playtest.py uses real Core-fed airlocks, clickable material replacement and base breach/reseal, with full-height sky clearing within Minecraft fill limits. All generated functions/hooks match the helper and namespace room_change_check; no saved chunks/player data/mod SavedData copied. Owner visual acceptance pending. Previous boundary Gradle client exited normally, with all dimensions saved at 23:58:57. Next after this slice: room keep-alive for lit heaters/thermostats, then conservative two-node thermal accounting.

## Typed room changes final verification

Final ./gradlew spotlessApply architectVerify --console=plain passes: 684 unit tests, all 307 native GameTests and all 302 required cases. All seven room-change tests pass, including actual controller evacuation/refill, emergency valve, incomplete-geometry suspension, no-op/cold-load silence, shared airtight material replacements and loaded Core recovery despite a wall swap at tick 60. Final source fingerprint 368b3bb88ed2d3c9b274688f28cba8ff2875253bd95a6fa21355064ba1cdc110 matches generated build info. Release jar and installed smoke jar SHA-256: f9a9e576823faca0e0c30b9a565b5767c1ee48c14d396da8e511d9ba162fcbc6. Only the Frozen Dawn smoke jar was replaced; prior jar, complete verification log/report and structured results preserved at build/thermal-evidence/change-final. Pre-existing EventBusSubscriber/Gradle warnings and native first-boot server.properties fallback remain. Owner visual acceptance pending; implementation stays local/uncommitted on thermal-2.0.

## Typed room changes live handoff

Verified patched Java 21 Gradle runClientLab launch into Room Change Check, terminal session 35475, log /private/tmp/thermal-room-change-client.log. Bridge session 1fefd359-a0b4-4720-a915-09ebdb3eef1d; one-time setup rcchange #built=1. Player Dev at (-0.5,65,0.5), gameTime 18, menu/focus paused and not tick-frozen. Generated control/setup messages load successfully; snapshot shows no Architects. Live status/scores/log preserved in build/thermal-evidence/change-live. No bridge mutation or reset was issued to collect diagnostics.

First owner step: resume, Read changes, Stone wall, Read changes. Expected: material total +1; geometry/air totals, room ID, cells and faces unchanged. Repeating Stone wall is a no-op. Then Glass wall restores the material with one additional material event. Counters are runtime totals for the dimension; compare immediately before/after each step. Controller/valve and base breach/reseal follow one at a time. Controls: /function room_change_check:controls. Separate visual acceptance pending; all code remains local/uncommitted.

## Owner stone-wall check

Owner reports stone test completed. Logs: 00:10:57 baseline ID 2 / 72 cells / 118 faces, global geometry 2 / material 0 / air 0. Stone at 00:11:02; 00:11:22 ID/cells/faces unchanged, geometry 3 / material 1 / air 0, last WALL_MATERIAL. Repeat Stone at 00:11:50; 00:12:03 readings unchanged including every counter. Material +1, no air loss, stable room geometry and same-state no-op confirmed live. Global geometry also increases once across the initial 25-second gap; counters do not record its subject/cause, so do not claim the entire interval contained no geometry notification. Native airtight-replacement regression proves material-only classification; immediate Glass wall/read is recommended to isolate the live counter delta. Preserved log and observations in build/thermal-evidence/change-owner-material. No world mutation issued during inspection.

## Owner glass restoration check

Glass function at 00:13:47 followed by 00:13:51 reading: ID 2 / 72 cells / 118 faces, geometry 12 / material 2 / air 6, last WALL_MATERIAL. Correct second material event and preserved base identity/cell/face counts confirmed. No fresh pre-swap read was recorded; the previous read was 00:12:03 (geometry 3 / material 1 / air 0), so other global geometry/air events across this interval cannot be isolated or attributed. Do not claim zero geometry/air changes for the glass interval. Evidence preserved in build/thermal-evidence/change-owner-glass. For subsequent controller/valve replay, read immediately before and after one action with both doors closed. Owner material result is confirmed; isolated counter evidence and air/control live acceptance remain incomplete. No bridge mutation issued.

## Owner controller attempt mixed with accidental breach

Owner initially reported controller test with both doors closed. Actual readings: 00:15:08 ID 2 / 72 cells / 118 faces, geometry 15 / material 2 / air 6; 00:15:36 same ID/cells/faces, geometry 17 / material 2 / air 11, last AIR_STATE. This exceeds an isolated single-cycle transition. Owner then clarified “i think cuz i accidently broke a glass block and it vented?”. Preserve that unprompted account. An accidental breach plausibly explains additional geometry and air activity, but exact sequence and every additional air event cannot be reconstructed from totals alone. Treat this attempt as mixed/inconclusive for controller-only acceptance, not an established controller bug or a passing isolated replay.

Evidence: build/thermal-evidence/change-owner-controller-mixed. No bridge mutation, repair, reset or retry issued. Next owner steps: ensure glass repaired; allow at least five loaded seconds for base Core recovery; close both airlock doors; read immediately before one empty-hand controller click and again after its four-second cycle. Expected material/geometry unchanged, air +1 for one successful evacuation or completed refill. Keep the imperfect attempt rather than resetting the world.

## Owner clean controller notification acceptance

Owner reports one repeat with no broken blocks and both doors closed. Fresh baseline at 00:17:56: ID 2 / 72 cells / 118 faces, geometry 17 / material 2 / air 11. Post-action read at 00:18:28: same ID/cells/faces, geometry 17 / material 2 / air 12, last AIR_STATE. Exactly one air notification, no geometry or material notification. Clean controller-only live notification check passes. Air transition direction is not recorded by these totals; do not claim this reading alone proves both evacuation and refill directions. Both directions already pass native integration regressions. Earlier mixed breach/controller attempt remains preserved. Evidence: build/thermal-evidence/change-owner-controller-clean. No world mutation issued during inspection. Next owner action may test the reverse controller cycle with fresh before/after readings, then emergency valve; keep both doors closed.

## Owner return controller cycle

Owner reports return-cycle total 13. Log at 00:20:42 confirms air 13, geometry 17 and material 2, with ID 2 / 72 cells / 118 faces unchanged. Both consecutive controller actions each add exactly one AIR_STATE notification without geometry/material changes. Owner performed the requested return cycle; counters do not encode gas direction, so actual evacuation/refill direction remains supported by owner account and native regressions. Updated clean-controller evidence. Next: emergency valve only when chamber indicator is green/breathable, with both doors closed; fresh before/after read should show air +1 and no geometry/material changes. An already evacuated chamber is a no-op and is not this acceptance fixture. No world mutation issued.

## Owner emergency valve acceptance

Owner explicitly reports repressurizing before dumping chamber, both doors closed. Fresh pre-valve reading at 00:22:01: chamber ID 7 / 27 cells / 54 faces, geometry 20 / material 2 / air 14. Post-valve 00:22:13: same chamber geometry and ID, geometry 20 / material 2 / air 15, last AIR_STATE. Exactly one air event for valve and no geometry/material change: clean live emergency-valve notification acceptance passes. Preparatory repressurization accounts for total 13 to 14 before the valve, per owner report. Movement/setup between prior base read and fresh chamber read changed global geometry 17 to 20; that interval is outside the isolated valve check. Evidence: build/thermal-evidence/change-owner-valve. No bridge mutation issued. Remaining focused owner check: base breach/reseal with actual Core recovery, using fresh per-action readings; native Core refill already passes. Changes remain local/uncommitted.

## Owner base breach and Core recovery acceptance

Fresh base baseline 00:23:43: ID 2 / 72 cells / 118 faces, geometry 23 / material 2 / air 16. Breached reading 00:23:49: Unsealed or unknown, geometry 24 / material 2 / air 17. Reseal function at 00:23:56; recovered reading 00:24:13: ID 2 / 72 cells / 118 faces, geometry 25 / material 2 / air 18, last AIR_STATE. Exactly one geometry/air-loss pair for breach and one geometry/air-recovery pair after reseal; material total unchanged. Actual base identity and shape return. Clean live geometry/breach/refill notification acceptance passes. This post-reseal read is 17 seconds later, so do not claim it measures exact five-second timing; native Core test verifies refill by tick 105 despite midpoint material swap.

Evidence: build/thermal-evidence/change-owner-breach-recovery. Live material notification and no-op behavior, both controller actions, emergency valve, and breach/reseal plus Core air recovery are now observed. Earlier material intervals with unrelated global deltas and mixed accidental-breach attempt remain preserved with their limitations. All seven typed regressions and full 684-unit / 307-native / 302-required gate remain passed. Multiplayer and separate typed-event reload visual pass remain unperformed (native reload coverage passes). No bridge mutation issued to inspect the owner replay. Changes remain local/uncommitted on thermal-2.0. Next agreed implementation slice: lit-heater/thermostat room keep-alive before the two-node air/structure thermal model.

## Room activity keep-alive implementation

Owner authorized the next supporting slice after the completed typed-change live replay. Re-fetched Thermal Model and Heater Control including Additions 2026-10-09. Requirement: lit-heater or thermostat rooms remain cached past the 600-tick query timeout. Thermostat block/control UI and two-node heat accounting remain future work.

RoomAtmosphere.keepAlive(BlockEntity) accepts a heartbeat from an actual loaded, nonremoved server block entity; future thermostats call it every 20 ticks while loaded, regardless of heater fuel. ThermalHeaterBlockEntity calls it every 20 game ticks only while burning, across all heater tiers. Existing room lookup uses exact source-in-cell or source-on-boundary membership, never a heating radius. Autonomous discovery processes at most two source jobs per tick, inspecting only loaded local intake cells; unsuccessful sources retry at most every 200 ticks unless geometry changes. Weak source references plus 40-tick renewable leases remove stale/removal/unloaded registrations automatically. No forced chunks, no offline fuel burning, no synthetic oxygen or new thermal bonuses. Rooms revert to the ordinary idle grace period after the source stops; the last real heartbeat or player query starts that grace; polling cannot extend it.

The 256-room ordinary cache ceiling evicts passive rooms before leased infrastructure. If all records are active, active records may exceed that soft ceiling rather than silently dropping heated rooms; per-tick discovery and geometry work remain bounded. Runtime diagnostic line reports tracked/active totals; its accessor does not query/discover/renew rooms. /fd world status verbose still queries the player's current pressure cell, so perform idle comparisons from the outside platform.

Native regression first attempt failed the long idle comparison. It incorrectly shared room_activity batch with synchronous fixtures that reset global room caches, compromising the asynchronous control. Preserved failed report/log/fingerprint and diagnosis under build/thermal-evidence/activity-first-attempt; moved the long comparison into its own room_activity_live batch. Other cases cover canonical rebind after cache reset, merge/split with two real heater sources, the future controller heartbeat hook using a clearly labeled vanilla BE fixture, unloaded/replaced source rejection, and passive cache eviction under >256 discoveries. Final rerun pending, /private/tmp/thermal-room-activity-verify-second.log, exec session 48199. Required cases use native lower-case names and xml evidence.

Prepared fresh Room Activity Check using only the closed Room Boundary Check level.dat seed metadata. No chunks, inventory or mod SavedData were copied, no previous world replaced. Helper tools/prepare_room_activity_playtest.py: matching partitioned rooms, fueled left heater at (-3,66,0), unlit right at (3,66,0); expected 35 cells/72 faces each. Start idle check teleports outside to (.5,65,8.5) and counts 800 loaded/unpaused ticks, without querying the interior. Expected outside diagnostic: one tracked/one infrastructure-active. Heater off starts a second 800-tick wait, expected zero/zero; Relight should discover the left room without entry. No force-loading. Clickable controls /function room_activity_check:controls. World is prepared but not yet launched or accepted visually.

Owner confirmed Room Change Check at title screen. Fresh bridge worldLoaded=false, normal complete save records at 00:31:06-08. Closed only the owning Gradle child PID 61925 with TERM; previous world remains saved and evidence preserved. Client restart pending final verification. Changes remain local/uncommitted on thermal-2.0, HEAD 842b0dd. No world mutations issued through the bridge.


## Room activity verification follow-up

Second attempt preserved at build/thermal-evidence/activity-second-attempt: own lit room survived, but an unrelated prior GameTest heater footprint also became active, so a whole-dimension tracked==1 assertion was invalid. Added read-only activeRooms snapshots and scoped asynchronous assertions to fixture membership/canonical ID. Cache pressure, merge/split/rebind and unloaded/replaced hook cases passed. Third attempt (build/thermal-evidence/activity-lease-failure) then exposed a real timing defect: polling renewed activeUntil from current time during the source's 40-tick heartbeat grace, effectively extending activity to eighty ticks after last renewal. Fixed renewal to use the real heartbeat timestamp; preserve a newer player-query lastUsed via max. Strengthened the long native case to require one initial geometry discovery and no eviction/re-discovery notifications across the idle period. Corrected run in progress: /private/tmp/thermal-room-activity-verify-corrected.log. Owner world remains unopened; previous world saved, client closed.

## Room activity final verification and owner handoff

Corrected full ./gradlew spotlessApply architectVerify --console=plain passes: 684 unit tests, all 311 native GameTests, all 306 required cases. All four keep-alive cases pass, including the actual loaded heater ticker, initial autonomous discovery, no eviction/re-discovery through 650 idle ticks, unlit sibling expiry, active lease cleared by fifty ticks after burnout, normal grace and eventual room eviction by tick 1320. Scope is per fixture membership/identity, preserving unrelated native heater footprints. Canonical merge/split/rebind, removed/unloaded hook rejection and >256 passive cache pressure also pass. Final diff-check clean.

Final source SHA-256 ca65f80f6eee844f6b1ef6925b7c6b839df2d7bc04d43594c293fd6a7df67e81 independently recomputed from the exact Gradle input set. Release and installed smoke jar SHA-256 a8b9471919010b26126e71fa4800324f12651a3a22daeee49bd92cd971b72bc2 match. Replaced only Frozen Dawn; prior smoke jar backed up, other dependencies untouched. Evidence: build/thermal-evidence/activity-final (verification.json, report.xml, build info, complete passing log). Preserved all prior failed attempts with diagnoses. Existing deprecation warnings and first-boot native properties fallback remain unrelated.

Launching owner Gradle runClientLab --console=plain -PfdLabWorld='Room Activity Check' with Java 21; log /private/tmp/thermal-room-activity-client.log. Fresh pack matches its helper including the ready notice for both steps. Owner: visit/read both room IDs, Start idle check, wait READY at 800 unpaused ticks and Read while outside (1 tracked / 1 active); Heater off repeats the wait (0/0); Relight, wait two seconds and Read outside (1/1 without entering). Observe and report before any additional mutations. Actual reload/chunk unload visual acceptance and multiplayer remain pending; native hook guard/cache rebind coverage is passed. No owner replay has been advanced by bridge commands. Keep-alive implemented locally, thermostat block and conservative air/structure thermal model still pending. All room-support slices remain uncommitted on thermal-2.0, depth HEAD 842b0dd unchanged.


Owner client bootstrapped successfully, exec session 33853, bridge session 950e2975-fc2e-4740-be4d-dda146d79922. Fresh world at outside platform (.5,65,8.5), built=1/stage=0, paused (not frozen); owner start not triggered. All test functions parsed; no startup function errors. Adjusted left visit to (-1.5,65,.5) so the head cannot intersect the heater at (-3,66,0). Reload completed safely with built guard, post-reload built/stage unchanged. Bootstrap evidence in activity-final. Read-only scores/status collected; the reload did not reset or advance the replay. Owner acceptance now pending.


## Owner first keep-alive replay inspection

Owner reports READY and 1 tracked / 1 active. Actual log: start 00:49:04, READY 00:49:44 (800 loaded ticks); read 00:49:50 is 4 tracked / 1 active. Off 00:50:03, READY 00:50:43, read 00:50:52 is 2 / 0. Relight 00:51:11, read 00:51:17 is 3 / 1. Player readings at outside platform resolve an additional SEALED room ID 5 / 2 cells / 10 faces. Lit-room baseline ID 1 / 35 / 72 appears earlier; no explicit unlit-right baseline or final same-ID lit-room reading. Active flag 1->0->1 is observed, but exact global 1/1 then 0/0 acceptance is inconclusive due extra tracked rooms/observer volume. Cause of the additional geometry is not established; do not assume owner edits or a pressure bug. Preserve original owner report and actual differing totals. Evidence: build/thermal-evidence/activity-owner-first. No world mutations issued. Recommend observation above the enclosure in genuinely open sky for a fresh 800-tick wait before concluding global idle expiry. Native continuous-retention/burnout cases remain passing.


## Owner open-sky keep-alive follow-up

Owner reports flying above the enclosure and waiting forty seconds. Latest read at 00:55:47 now correctly reports player pressure room Unsealed or unknown, but runtime totals remain 2 tracked / 1 infrastructure-active (geometry 13 / material 0 / air 0). Fresh bridge status confirms same owning session/world, unpaused and player at approximately (0.90,73.25,0.03). Thus heater remains active; exact global tracked=1 comparison still does not pass. The extra cached record is not identified by aggregate counters; do not claim its cause or full idle-cleanup acceptance. Preserved log and observations under build/thermal-evidence/activity-owner-open-sky. No world mutations or reset issued during inspection. Native per-fixture retention/expiry regressions remain passing; live discrepancy needs identification before a full pass claim.


## Extra tracked-room diagnosis and corrected acceptance

Owner authorized tracing the extra cache record and any necessary fix. Owner saved/quitted before restart; bridge worldLoaded=false and all dimensions saved at 00:58:26. Closed only verified Gradle child PID 70463. Complete untouched world-before backup and original log are under build/thermal-evidence/activity-room-trace. Root Scribe WIP untouched.

Added non-renewing RoomAtmosphere.cachedRoomDiagnostics: canonical ID, geometry, uncertain/active flag, idle age, last actual query age/reason/position and source lease positions. Actual pressure queries now record provenance without changing expiry semantics. /fd world rooms prints existing records only (32-line display cap); lab snapshot includes exact per-dimension cache records, bounds and loaded last-query block. Reading diagnostics neither creates geometry nor extends lastUsed. Extended the existing real-ticking-heater native test with repeated passive diagnostics at 100/300/500/600 ticks and source/provenance checks; the passive sibling still expires and heated room never evicts/re-discovers. No changes to room timeout, oxygen protection, heat or source leases.

Full spotlessApply architectVerify passed: 684 unit tests, 311 native GameTests, 306 required cases. Source SHA-256 eff0e12f7d0565cf9a3dcd85dcfb5eb8c8cf3e0984b45f1d0c638d150e84f9e7 independently recomputed against exact Gradle inputs. Release and installed smoke jar SHA-256 dad9f5198d72c378b5d1b9e6a7c56ff21e3f28fa1581acab2ecd11f58f713683 match; only Frozen Dawn replaced, prior jar preserved. Existing deprecation warnings unchanged. Reports, logs, hashes and snapshots preserved in activity-room-trace.

Reopened preserved Room Activity Check through owning Gradle runClientLab, Java21, exec 47077; log /private/tmp/thermal-room-trace-client.log. Bridge session 82db4fc7-6dc2-4d53-bbad-3c1478268a84. First snapshot tick12418 has only ID1: 35 cells/72 faces, active, sole heater source (-3,66,0); no player queries since infrastructure discovery. Second snapshot tick13240 reproduces 2 tracked / 1 active. Extra is ID2: 63 cells/108 faces, bounds (-213,-26,198)..(-208,-22,200), passive, no sources, last AIR_QUERY at (-209,-25,200), age8 ticks; loaded query block is minecraft:red_candle[candles=3,lit=true,waterlogged=false]. VacuumFlames' bounded lit-flame checks call CombustionAtmosphere -> shared room air authority, so this legitimate candle chamber is continually queried. The active test room remains ID1 without an eviction/re-discovery. The earlier aggregate-only counts cannot identify every historical extra record, but the fresh reproduced discrepancy is identified. No keep-alive leak demonstrated.

Corrected the QA helper and existing guarded datapack controls: Read cached rooms runs /fd world rooms without a player-volume query. Compare only known test anchors (left -4,65,0; right 1,65,0 using BlockPos ordering): fueled left remains infrastructure-active, unheated right expires; after off both fixture records expire; relight discovers left without entry. Global totals may legitimately include candle chambers or the observer's tiny room and are no longer declared a failure. No setup/reset, terrain edits, fuel changes, tick sprint or encounter advancement issued by bridge. Native expiry is passing; owner off/relight replay can now use the corrected scoped comparison. All changes remain local/uncommitted on thermal-2.0, HEAD842b0dd.


## Owner scoped heater-off expiry acceptance

Owner clicked off at 01:05:48, READY logged 01:06:27, read at 01:06:35. Function command output does not appear in chat log, so collected exact-world read-only cache snapshot and stage/timer scores. Both fixture memberships absent from fresh cache; source/active state and remaining unrelated records preserved under activity-room-trace/snapshot-owner-off.json and scores-owner-off.json. Clean owner heater-off scoped expiry check passes. No world mutation issued during inspection. Relight remains the next optional owner confirmation with corrected diagnostics; native autonomous re-discovery already passes.


## Owner relight autonomous discovery acceptance

Owner first report: “i think it worked”. Logs show relight 01:07:21 and read 01:07:30. Fresh exact-world non-renewing snapshot confirms ID1 restored, 35 cells/72 faces, bounds (-4,65,-1)..(-1,67,1), infrastructure-active with sole source (-3,66,0), last query INFRASTRUCTURE_DISCOVERY; no unheated-right fixture record. Player remains outside at (.5,65,8.5). Heated room autonomously rediscovered without player entry or operator geometry query. Combined fueled retention, heater-off expiry and relight recovery now accepted in owner replay, with fixture scope and earlier mixed global-count attempts preserved. Snapshot-owner-relight.json and final log in activity-room-trace. No world mutation issued during inspection. Thermostat block/control UI, two-node thermal model, actual chunk unload and multiplayer visual acceptance remain separate pending work. All room-support changes local/uncommitted on thermal-2.0.

## Keep-alive checkpoint committed and pushed — October 10, 2026

At the owner's request, canonical identities, boundary contacts, typed room changes, heater keep-alive and the accepted diagnostic replay are committed together as 3bb1538 (`feat(thermal): keep canonical heater rooms active`). Keep-alive relies on the earlier room integration. The owner approved pushing the checkpoint; HEAD and origin/thermal-2.0 match. Saved worlds and ignored replay evidence remain in this owning checkout. The root Scribe checkout is untouched.

## Two-node room heat implementation — October 10, 2026

The next local, uncommitted slice implements separate gas and physical-material energy. RoomThermalState persists complete prior cells, oriented faces, structure membership, gas presence/energy and a single reservoir per physical material block. The energy ledger accounts for heater input, signed environmental loss, incoming/vented gas and imported/removed material. Live merges and splits claim gas through full overlap; shared walls contribute capacity and energy once. New cells/material import only their ambient initial energy. Reload can discover all children of a thermal parent even when the pressure save already contains separate children; nested notifications are serialized before consuming parents.

Each loaded room integrates once per 20 ticks, with stable finite-reservoir air/wall exchange and explicit environmental debit. Heaters heat gas while present; a depleted room drops gas capacity and receives 25% heater power into walls. Vacuum felt temperature blends 20% toward wall warmth from area-weighted background. Incoming refill gas uses area-weighted outside temperature; retained wall energy pays for warming it. These coefficients are first-pass game units. Exposed vacuum contacts use a reduced, linearized radiation term; solid exterior contacts use depth/time temperature at the boundary face Y. This is a game model, not a measured physical simulation.

Tags override SoundType insulation (.1/.3/.5/.8/1). Each contact walks at most three solid layers and sums their resistance. A separate outer-layer/exterior index invalidates conductance without changing pressure geometry. UNKNOWN or any unloaded cell/material/exterior layer suspends simulation and preserves energy; no chunks are forced and no offline elapsed time is integrated. At most two saved records are considered each second for loaded-only rehydration. Geometry revalidation locates surviving air membership when construction fills the cached first cell. Mob and loaded-only catch-up temperature probes use the existing thermal cache and never discover rooms or renew pressure activity.

TemperatureManager uses room gas/radiant temperature without stacking the old flat heater bonus or +5C shelter bonus onto that model. Open camps retain the existing nearby-heater behavior. Core, vent and Blast Pit local warmth remain additive as agreed. This means the player HUD can differ from the raw air reservoir near a Core. `/fd world thermal` and bridge `roomThermal` snapshots inspect existing records without discovering rooms or advancing simulation. The ORSA Field Manual describes room warm-up, gradual cooling, insulation, retained wall heat and depleted-room operation.

Heater 20C throttling, fuel-rate UI/redstone, thermostat, thermometer and pressure-preserving heat exchanger remain the next separate slice. Existing fuel drain continues with fixed heater power in this physics test build; final fuel/warm-up balance is not accepted yet. No thermostat or throttle is claimed as implemented.

### Regression evidence and fixture corrections

The first gate passed all eight new thermal native cases but rejected two earlier instantaneous-temperature assumptions. Evidence is preserved at build/thermal-evidence/two-node-first. The EVA cold/expiry test previously fueled its room during the lighting wait at Phase 0, then jumped progression to endgame; retaining that room heat is now correct. It now selects endgame before fueling/discovering the cold room. Its actual cold damage, service expiry and vacuum protection assertions remain intact. The Pawn base staging check no longer requires an instantaneous >90C heater bonus; it requires a finite reading and Creative staging, with oxygen/kit/outdoor-vacuum/Survival/overheating checks retained.

Successful intermediate gates are preserved under two-node-before-rebind (320 native, 315 required) and two-node-before-filled-cell (321 native, 316 required). Final verification and delivery hashes follow when complete. Twelve native cases now cover live heater warm-up/burnout, gas/material ledger conservation, merge/split, older-parent reload sibling recovery, breach/refill, outer insulation, UNKNOWN/unloaded suspension, cache-only probes and persistence, depleted heater/airlock cycles, unqueried loaded-save rehydration and filled-origin automatic revalidation, and neighboring-room discovery/breach exposure. Five pure numerical tests independently cover stable exchange and budgets.

### Fresh owner replay

`tools/prepare_room_heat_playtest.py` prepared `run-lab/saves/Room Heat Check` from the closed Room Activity Check's seed metadata only. Source metadata SHA-256: 48f4ddf4c11a56d3d4c9c013b0879d391fddc604de25515c453cb9fddcebcd3f. No chunks, player inventories or mod SavedData were copied. Earlier worlds remain preserved. Generator refuses overwrite; generated functions match the helper. Room Heat Check starts Creative at late Phase 6 with two matching rooms: wool at X -8, glass at X 8. Each has one initially unlit heater and a Core for oxygen. First visit both rooms; Fuel both; compare air/wall temperature after 30, 60 and 120 seconds of normal unpaused play. Then Heaters off verifies gradual cooling. Breach/patch left verifies immediate gas loss, retained wall warmth and five-second Core recovery. Save/quit/reopen can verify persistence. No bridge mutation functions are authorized; the owner chooses the clickable steps. Read air/walls invokes `/fd world thermal` directly so function output suppression cannot hide its results. Return controls with `/function room_heat_check:controls`.

The final client launch is pending verification. Shared partition faces refresh when a neighboring room is discovered or breached; dormant open memberships do not hide a vacuum exterior. The successful filled-cell gate is preserved in two-node-before-neighbor (322 native, 317 required). Owner visual/balance, actual client reload/chunk unload and multiplayer acceptance remain pending. Headless native tests do not substitute for these passes.


### Two-node final verification

`./gradlew spotlessApply architectVerify --console=plain` passed on the current inputs: 689 unit tests, all 323 native GameTests and all 318 required cases/reports. All twelve room-heat native cases passed. Current source fingerprint matches the build: ad322233fcc2e76171d97878e372c317beba94c69667bd94bb8d2ed3b468844f. Jar and installed smoke SHA-256: b6112080976cb5d6269ca0ea6f52acea85c329b5c05b1ec6a531a7680fd32081. Evidence: build/thermal-evidence/two-node-final/verification.json, report.xml, unit XML and Gradle log. The previous smoke jar is retained byte-for-byte as smoke-previous.jar; all dependency jar hashes are unchanged. Existing NeoForge removal, Gradle deprecation and invalid historical Java-installation warnings remain. Native first-boot server.properties fallback is expected. Owner visual, actual client reload/chunk-unload and multiplayer acceptance are still pending.


### Two-node client launch

Client is running through owning Java 21 `./gradlew runClientLab --console=plain -PfdLabWorld='Room Heat Check'`. Terminal session 87923; live log /private/tmp/thermal-room-heat-client-windowed.log; bridge session 0fc99e09-2fbd-489c-830a-8dd6e12cc1ef. Exact-world read-only snapshot confirms Dev at (.5,65,7.5), menu/focus paused, not tick-frozen. Setup completed at gameTime 9 with rheat #built=1; fixture/control messages parsed and the ORSA Field Manual was supplied. Heaters start off, and no owner Fuel/Breach/Patch step has been issued by the agent. Fresh world baseline and logs are preserved in two-node-final. Resume the game for the owner replay.

Two pre-world launch attempts failed at NeoForge early display with glfwGetPrimaryMonitor=0. The display was connected but initially asleep. Waking it alone did not resolve the optional early loading window failure. Disabling only run-lab/config/fml.toml earlyWindowControl allowed the normal Gradle client to load and render the world. The original lab config and both failed startup logs are preserved in the evidence folder. This ignored local startup workaround is not a production-mod change. Prior test worlds and other worktrees were preserved.

Model code remains uncommitted on thermal-2.0, whose pushed checkpoint is 3bb1538. Owner warmth/cooling/insulation/breach/refill and reload visual acceptance, balance tuning and multiplayer remain pending. Next implementation after this pass is heater default target/throttling and controls.


### Owner first two-node warm-up comparison

Owner's unprompted observation: “yup, fueled both and waited 30 seconds. check the logs real quick. the whool heated up a lot faster”. Fuel both was invoked at 02:09:45. Before fuel both fixture air/wall temperatures were -215.8C. First logged comparison at 02:10:13 (about 28 wall-clock seconds later) reports wool air -154.9C/walls -160.6C and glass air -158.1C/walls -163.8C, both sealed with heater power 1800. Later read at 02:11:37 reports wool air +4.5C/walls -1.2C and glass air -39.6C/walls -45.4C. These later readings are not labeled as the 30-second sample; pauses and continued play affect elapsed simulation time.

Exact-world read-only snapshot at gameTime 3076, paused, confirms Room 1 is left wool (-9,65,-1)..(-7,67,1), heater (-8,66,0), and Room 3 is right glass (7,65,-1)..(9,67,1), heater (8,66,0). Both have 26 air cells, 60 contacts, air capacity 26, material capacity 876, sealed/present air and power 1800. Wool conductance .6525 versus glass 4.580357: lower environmental heat loss for the same temperature difference. Latest snapshot wool air +13.65C/walls +7.94C; glass air -34.05C/walls -39.86C. The global energy total and ledger net agree within 3.64e-7 game energy units. Snapshot and untouched log preserved under build/thermal-evidence/two-node-owner-warmup.

Owner warm-up/insulation comparison passes: equal input and capacities, progressively warmer wool room, air leading wall temperature. Only diagnostics were issued by the agent. Heater-off cooling, breach/patch/refill and owner reload acceptance remain pending. Default 20C throttle/control is still a separate unimplemented slice; this fixed-power run does not establish final balance. Model remains uncommitted on thermal-2.0 at checkpoint 3bb1538.


### Owner heater-off cooling acceptance

Owner reports: “yup turned both heaters off and waited 30 seconds. check the logs real quick, looks good? whool is much warmer and losing slower”. Heaters off logged at 02:15:56. At 02:16:29 (33 wall seconds later), wool air/walls +10.0C; glass air -63.1C/walls -63.2C, both sealed with zero heater power. At 02:16:36, wool air/walls +8.7C; glass air -69.1C/walls -69.2C. Across these seven wall seconds, wool air lost 1.3C versus glass 6.0C. These are rounded logged readings; an exact switch-off temperature was not logged, so a full initial-to-30s temperature drop is not inferred.

Read-only snapshot at gameTime 4322, unpaused, confirms same bridge session and Room Heat Check: wool air +5.790C/walls +5.768C, glass air -81.957C/walls -82.050C. Both sealed, air-present, unsuspended, power 0; unchanged fixture capacities and conductance. Global energy/ledger mismatch remains only 3.62e-7 game units. Evidence preserved in build/thermal-evidence/two-node-owner-cooling. No thermal exception appeared in the inspected log. Owner attempted an obsolete room_activity_check:controls at 02:16:13; current control command is /function room_heat_check:controls. This did not mutate the fixture.

Heater-off gradual cooling and insulation comparison pass in owner replay: warmth survives turning off, air and walls equilibrate, wool cools slower. Breach/patch/refill and reload visual acceptance remain pending. Agent issued diagnostics only. Source model remains local/uncommitted at 3bb1538 on thermal-2.0.


### Owner wool-room breach and patch recovery

Owner reports breaching and patching the wool room and waiting five seconds. Logged pre-breach read at 02:18:09 shows wool air/walls -5.9C and zero heater power. Breach left ran at 02:18:14; Patch left at 02:18:23. Next logged read at 02:18:40 (17 wall seconds after patch) shows wool sealed, zero power, air/walls -20.2C. Exact-world snapshot at gameTime8068 confirms Room1 original wool bounds/capacities, sealed, unsuspended, airPresent=true, air -31.462C/walls -31.480C after further normal cooling; ledger matches total within 4.47e-7 game units. Preserved snapshot/log in two-node-owner-breach.

Post-patch room air recovery is confirmed; much of the stored heat survived and both reservoirs continued cooling without heater input. No diagnostic reading was taken during the nine-second open interval, so the owner's live run does not independently prove immediate gas loss or quantify retained wall temperature during vacuum. Exact five-second recovery latency was not measured by the 17-second post-patch read. Those mechanics pass native tests; keep this live acceptance scope explicit. Agent issued read-only diagnostics only and did not repeat the breach or advance the replay. Actual owner save/reload acceptance remains pending.


### Owner save/quit/reopen persistence acceptance

Owner saved, quit and reopened Room Heat Check. Log confirms all dimensions saved and integrated server stopped at 02:21:51; reopening/login occurred at 02:21:57. New bridge session 27ac9419-6f30-439d-a410-8de287ad3822 confirms a fresh integrated server instance. Read-only inspection of the 02:21 saved frozendawn_room_heat.dat shows wool air -45.312C/walls -45.329C and glass air -185.865C/walls -185.885C, both sealed/air-present with capacities26/876. Shared material capacities/energies are divided by actual saved ownership when reconstructing wall temperature.

First owner read at 02:22:03, six wall seconds after login, reports wool air/walls -46.2C and glass air/walls -186.8C, both power0/sealed. Continued read at 02:22:06 wool -46.5C/-46.6C, glass -187.2C/-187.2C. Fresh snapshot gameTime10631 confirms wool air -47.760C/walls -47.777C, glass air -188.617C/walls -188.635C, both sealed, air-present, unsuspended, power0 and original capacities/conductance. Global energy ledger agrees within4.74e-7 units. Saved file, parsed values, provenance/hash, client log and snapshot preserved under two-node-owner-reload.

Owner reload persistence passes: both air and physical wall reservoirs carry through loading and resume gradual cooling, rather than resetting to the -215.8C outdoor fixture baseline. Exact offline invariance is covered by native tests; this short owner reload has elapsed loaded simulation and does not isolate an exact zero-time energy comparison. No agent mutations or replay resets issued. Owner visual warm-up, insulation, cooling, post-patch recovery and world reload are now verified with scoped evidence; open-interval air/wall readings, actual chunk-unload visual behavior, final balance and multiplayer remain separate gaps. Two-node source remains local/uncommitted on thermal-2.0 at3bb1538. Next implementation slice is default heater target/throttling and control design.


## Two-node checkpoint and default control slice — October10,2026

At the owner's request, the accepted two-node source, manual, regression cases and replay helper are committed as2be1ab2 (`feat(thermal): model conserved air and structure heat`). Exact current source fingerprint matched the already passing689-unit/323-native/318-required final gate before commit. This commit is local; no push requested.

Next uncommitted slice implements shared20C default target, actual fractional fuel debit, visible Heating/Holding burn percentage and redstone shutdown. The controller caps only heater energy; it never deletes stored heat or conceals environmental overheating. Multiple room heaters share demand; reduced vacuum delivery still costs its actual burner fuel. Frostmite and industrial costs remain separate. Fractional fuel remainder persists across reload, and remaining fuel limits maximum deliverable energy. Unknown/unloaded geometry suspends modeled fuel/heat. Open camps use background-based default demand with existing proximity falloff. Thermostat target selection10–30C remains next after this slice; no thermostat block is included yet.

Owner saved/quitted before restart; status confirms worldLoaded=false. Complete untouched Room Heat Check backup and previous-client.log preserved under build/thermal-evidence/heater-control. Only verified owning Gradle client PID83309 was stopped. Fresh Heater Control Check copies seed metadata only, reuses the accepted wool/glass geometry, and adds guarded owner-clicked controls for two heaters, redstone pause/resume and fuel reads. Original world and accepted replay history remain preserved. Current helper refuses overwrite; no bridge mutation is authorized.

The preliminary Gradle command accidentally ran in the root checkout and is not evidence for this slice. Corrected full gate runs from the owning thermal worktree; its result follows. No root source edits were intentionally made or staged.


### Default control first gate and assertion corrections

Owning full gate built successfully and passed692 unit tests, but rejected2 of328 required/native cases. Preserved log, current report, unit XML and source fingerprint under heater-control-first. New shared target/fuel-ledger, insulation comparison, redstone shutdown, actual-ticking multi-heater checks pass. Tiny-fuel energy was correctly11.25units, while the expected expression1800/(20*8) accidentally used integer division and yielded11; corrected only to double arithmetic. Existing roomactivity fixture deliberately uses Phase0 at depth~Y-60, whose ground is above20C. Automatic control correctly consumes no ordinary fuel there. Its old always-burn assertion now accepts either actual consumption or an explicitly observed>=20C room with zero burn fraction. Autonomous discovery, continuous retention, read-only lease expiry, burnout/release assertions remain intact; the new real-ticking cold-room case independently verifies demanded fuel consumption and savings. No production failure is inferred from these two obsolete/rounded expectations. Final rerun follows.


### Default control final verification and delivery

Two-node checkpoint2be1ab2 is pushed with explicit owner approval; HEAD and origin/thermal-2.0 match. The20C/throttling slice remains uncommitted. Corrected owning spotlessApply architectVerify passes692 unit tests, all328 native GameTests and all323 required cases/reports. Source fingerprint84d8a69ce774cfe92cf0e01e158658b2eb371a01387b4c3acfda462e77ad43db independently matches actual source inputs. Jar and installed smoke SHA-25676262ad9088ae4d55c734cec2b7e3e2f2af9932c905d9511f8298a9b069a06ad. Verification JSON, report, unit XML and gate log preserved in heater-control-final.

Only the smoke Frozen Dawn jar was replaced. Another ongoing slice had installed a different smoke jar since the prior thermal run; its actual8d8ee82639eb82304c21e2ffde1b66adf47e186f65f1f4eb67581328f6b5ba8d bytes were backed up as smoke-previous.jar before replacement. Dependency hashes remained unchanged. Existing NeoForge removal/Gradle deprecation/historical Java-install warnings remain; fresh native server.properties fallback is expected. Production heater source, helper and ORSA manual are ready for owner control/UI acceptance. Native checks cover actual ticking, common target, fuel-ledger relationship, same-temperature wool/glass demand, redstone heat/fuel/industrial pause and resume, persistent fractional fuel and tiny-fuel budget. Thermal/open-camp target is a game control approximation, not a physical thermostat simulator.

Client restart is authorized after owner safely saved/quitted. Launch through owning Java21 runClientLab into fresh Heater Control Check, terminal58767, log/private/tmp/thermal-heater-control-client.log. Do not infer client readiness or owner visual acceptance from the successful native gate; runtime confirmation follows.


### Heater-control client handoff

Fresh client loaded Heater Control Check through owning Gradle, bridge session9ab826a9-433d-43ec-a96d-9f9300aea884, gameTime8, menu/focus paused, not tick-frozen; Dev(.5,65,7.5). One-time setup completed and native controls parsed. Exact-world snapshot, scores and startup log preserved in heater-control-final. Heaters start off and no agent Fuel/Redstone/Second/Breach step was issued. Owner replay: visit both, fuel, wait normal unpaused warm-up until room air reaches20C, compare original heater status panels and burn percentages at the same target; then add a second heater to each room, confirm shared20C/no overshoot and lower per-heater holding demand; redstone off/read fuel, wait30seconds/read again, then resume. Use/function room_heat_check:controls to restore buttons. Raw room air is the control input; the existing Core adds a local player-HUD warmth bonus, so HUD temperature need not equal20C. Thermostat adjustable10–30C controls follow after owner default-control acceptance. This control slice is uncommitted; model2be1ab2 is pushed.


### Owner default-control temperature and display findings — October10,2026

Owner reports heater/HUD temperature disagreement and jumping Holding percentage/fuel estimate. Inspected all seven screenshots from02:37:09 through02:39:27 and a read-only exact-world snapshot at gameTime4384, paused, same bridge session. Evidence preserved under build/thermal-evidence/heater-control-owner-findings (screenshots with hashes, client.log, snapshot.json and findings.json). No replay mutations or resets issued.

Both rooms hold raw air20.0C; HUD45C is the existing additive +25C surface Core warmth. Earlier screenshot air-127.7C/HUD-103C independently matches the same bonus. This is actual player-temperature input, not a cosmetic HUD error or heater overshoot. Re-fetched Thermal Model and Heater Control in Notion: Additions explicitly keeps Core/vent/Blast Pit local terms additive. Owner recognizes that this is designed behavior. Any change to incorporate Core heat into modeled energy or adjust control targets is a deliberate design revision, not silently deleting the bonus.

Wool screenshots show Holding24%,16%,14% with runtime6h50m,10h6m,11h43m; glass shows68%,66%,64% with2h22m,2h28m,2h30m. Both decrease in demand as estimated runtime rises. Latest snapshot wool walls19.491631C/power153.502593 (8.53% nominal); glass walls16.457406C/power1070.887699 (59.49% nominal), both air20C/sealed. Walls are still finishing warm-up, so changing maintenance demand is expected. UI extrapolates remaining runtime from the latest one-second burn fraction with no averaging; this magnifies settling changes. Recommended display change is a short averaged duty sample shared by burn percentage and estimated runtime, with immediate off/redstone handling and an explicit estimate label; keep actual heat/fuel ledger unsmoothed. No evidence of increasing stored fuel or numerical heater overshoot. Glass Exposed label uses the legacy sky/shelter classifier although pressure geometry is sealed; label clarification remains separate.

No production code changed in this diagnostic turn. Default-control WIP remains uncommitted atop pushed2be1ab2. Owner temperature concern is explained; display stability, two-heater and redstone live acceptance remain pending.


### Display smoothing and thermostat direction — October10,2026

Owner authorized smoothing the displayed heater duty/runtime and requested that the upcoming thermostat account for Core heat and every heater tier/upgrade. Re-fetched Thermal Model and Heater Control: Core/vent/Blast Pit warmth is still explicitly additive. The proposed thermostat sensor therefore reads the same environmental temperature used for an unmodified player at its location, including these local terms, and reduces the connected room heaters accordingly. Personal armor, Ember and frostbite adjustments are not the room sensor. It must not recurse through its own adjusted output, conceal overheating or magically cool the room. If independent Core warmth alone exceeds the chosen target, zero heater output and an Above target status are appropriate; cooling/Core heat regulation is a separate mechanism. Because these are spatial local terms, sensing at the thermostat does not guarantee identical temperature at every point in a large room. This is design direction; no thermostat block or Core control was implemented in this display slice, and the Notion page was not edited.

Source audit confirms basic/iron/gold/diamond output35/50/65/80; capacitor applies existing1.5x integer output and doubles open-camp radius. All share ThermalHeaterBlockEntity and modeled room power uses their actual output, capacitor and Frostmite penalties. Normal refueling retains existing1x/1.5x/2x/3x tier fuel costs and phase drain. Stronger output changes available power, not room target. New required native regression mixes all eight tier/capacitor combinations, checks persisted upgrade output, common20C target/duty, summed delivered heat, exact fractional fuel ledger and status reads that do not change saved fuel. It does not claim a live visual pass for each tier or independently replay each tier's item-refueling interaction.

The authorized display change gives the status menu a five-second exponential response in simulation ticks; percentage and estimated runtime use the same averaged demand. Off/redstone and air/wall/open-camp mode changes update immediately, paused ticks do not advance averaging. Heat grants and ordinary/industrial fuel debit continue using actual demand. Runtime explicitly says est.; the Field Manual describes the short average and settling estimate. Two numerical regression cases cover settling/convergence, repeated same-tick menu reads, pause and off/resume/mode changes. Changes remain uncommitted.

Owner safely saved/quitted; bridge worldLoaded=false verified. Complete closed Heater Control Check and old client log preserved in build/thermal-evidence/heater-display-tiers before stopping only verified thermal clientPID91650. Other worktree clients were not stopped. Owning full spotlessApply architectVerify is running; final results and restarted Gradle client handoff follow.


### Display/tier final verification

Owning spotlessApply architectVerify passes694 unit tests,329 native GameTests and all324 required cases/reports, including the mixed eight-heater tier/capacitor regression. Final native fixture removes all eight heaters after assertions. First green result is preserved under heater-display-tiers-before-cleanup; a guarded copy initially found the actual jar filename differs from the old skill example (frozendawn-2.0.1-alpha.jar), before any jar/source mutation. A prematurely started redundant gate was cancelled and its partial log preserved; final complete gate above ran after cleanup. No result from that cancelled run is used as final verification.

Final source SHA-256 independently matches inputs: ce79fbdffceb952f1019d6b8ed30cba609a4b047e05b23a45eefabef0f148b77. Built and installed smoke SHA-256: 99f23a96622fb59fe87168f3d06df3820b3a2fc4426cc94de3d68f13520ac572. Only Frozen Dawn smoke jar replaced with previous bytes backed up, dependency hashes unchanged. Full log, report.xml, unit XML, build properties and verification.json in build/thermal-evidence/heater-display-tiers. Existing NeoForge/Gradle deprecation and initial GameTest server.properties fallback warnings remain. Owner display visual/redstone/two-heater replay and multiplayer remain pending. No thermostat or Core regulation implementation claimed. Restart is authorized; same preserved Heater Control Check opens through owning runClientLab next.


### Smoothed-display client handoff

Verified Gradle runClientLab has reopened the same preserved Heater Control Check. Terminal12692, log/private/tmp/thermal-heater-display-client.log, bridge session2beeb0e5-f921-4443-a59e-606bf1cd5e87, gameTime4409, paused=True, tick-frozen=False. Dev remains in the glass room at(7.3,65,1.7). Read-only restart snapshot/startup log preserved in heater-display-tiers. No Fuel, Redstone, Second, Breach or reset function issued by the agent. Owner can reopen heater panels and watch normal unpaused demand settle over10–15seconds; percentage and estimated runtime now share the five-second response. Return buttons with/function room_heat_check:controls. Original Core/HUD additive behavior remains as designed; thermostat sensor/adjustable target and any Core integration revision are next, design-only. Source changes remain uncommitted atop pushed2be1ab2.


### Heater control owner acceptance and commit

Owner reports “got it. looks good, commit the heater stuff, then lets do the thermostat next.” This accepts the observed status/control experience after the smoothed-display replay; it does not independently establish the remaining two-heater/redstone/multiplayer live steps. Verified current source fingerprint still matches the694-unit/329-native/324-required final gate. Commit includes only heater control, status, fuel persistence, manual and replay/tests/checkpoint files; tools/__pycache__ is excluded. Thermostat work begins from this checkpoint on thermal-2.0.

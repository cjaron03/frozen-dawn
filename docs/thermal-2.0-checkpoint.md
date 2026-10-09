# Thermal 2.0 checkpoint

Owning checkout: `/Users/jaroncabral/.codex/worktrees/thermal-2-0/minecraft-mod`.
Branch: `thermal-2.0`. Base: `8303073fef1b8f8bd82ccb4f6f8c434cc3275d69` from the pushed atmospheric-breach branch.
Notion source: [Thermal Model and Heater Control](https://app.notion.com/p/3f47cfaa890181b9a213c52a3d2c9966), including Additions (2026-10-09).

Current acceptance: owner visually accepted the ground-depth replay on October 9, 2026. Automated verification passed; see the owner-depth section below. The ground-depth slice is committed locally on this branch.

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

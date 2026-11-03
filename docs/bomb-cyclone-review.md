# Bomb cyclone final review — 2026-10-20

Reviewed branch `feat/bomb-cyclone`, HEAD `dad83f58a8a2eb3570756dff23a63c55e13c84fe`, including its uncommitted cyclone implementation, tests, lab fixtures and audio assets. Target reference refreshed to `origin/feat/maeve-director` at `8e07048859c617fbfc6c31cbfe4e500dc235553d`. This review does not integrate the newer target or establish that the combined tree passes.

Design source: [2026-10-18-bomb-cyclone](https://app.notion.com/p/bcd7cfaa89018210aa5d817bae698fc0), fetched for this review; page last edited 2026-10-19T06:17:28.085Z. The owner's accepted recommendations and subsequent decisions in `bomb-cyclone-checkpoint.md` take precedence over older plan details.

## Current disposition after owner decision

The owner authorized balance controls and explicitly deferred personal ear-pop audio and extra visual hooks. The eye and snow/wall thermal averaging fix remain deferred, and guide text remains owner-managed and protected. The original findings below are retained as review history rather than open requirements for this slice.

The six balance keys live in `frozendawn-common.toml` under `[cyclone]`:

```toml
[cyclone]
peakOffsetModerate = -8.0
peakOffsetSevere = -19.0
peakOffsetCatastrophic = -30.0
visibilityModerate = 8.0
visibilitySevere = 4.0
visibilityCatastrophic = 2.0
```

Offsets accept -100..0 Celsius; visibility accepts 1..128 blocks. Defaults preserve accepted balance. Server thermal sampling applies configured cold without changing the pure storm timeline, pressure, calm warmth, intensity or deep-ground model. A server-owned visibility snapshot is sent on login and regular storm sync, consumed by fog rendering, and cleared on logout; clients do not substitute their local common-config values. Goggles preserve their default20/12/8block minimum and never give less reach than a higher configured unassisted distance. Fresh Java21 architectVerify/packaging verification passed896unit tests,677native tests and all668required cases in4m15s. Non-default temperature, visibility wire roundtrip, calm/deep-ground identity, interpolation and goggles checks passed. Source SHA-256 `897dc14645bf57642c976064c4e6566c3b8789521459538a8c9008f6bbf60924`; release/smoke SHA-256 `f2a6850fccfffe7fbb450af44847360fe9d36778d42599bddef0f4c7b00934f8`. Receipt/log/report: `build/cyclone-evidence/balance-controls/`. All14guide trees and5smoke dependency jars unchanged. The original review's hashes below remain historical.

## Original review verdict

No new defect found in the accepted core storm behavior during this review. The implementation is not yet a complete match to every non-deferred requirement in the Notion plan: balance overrides and several presentation details remain absent. Resolve or explicitly defer these differences before describing the entire plan as complete. No gameplay source was changed to silently resolve them.

## Findings

1. **P2 — Planned temperature and visibility overrides are absent.** Task 3 specifies six per-severity config values and a config adapter. `FrozenDawnConfig.java:538-544` defines enable/count/catastrophic/push controls only. `CycloneController.java:54` returns the fixed policy temperature, and `CycloneVisuals.java:7` reads the fixed severity visibility. Server owners cannot tune these as specified. Add the overrides while keeping current values as defaults, apply temperature on the server, and synchronize effective visibility to clients; leaving pure policy tests on constants is appropriate. Merely adding config keys without consuming/syncing them would not close the gap.

2. **P3 — Personal ear-pop is text only.** Task 7 calls for an ear-pop sound alongside the message. `CycloneController.java:47-48` sends only the action bar, with no corresponding registered sound/asset or client playback hook. Barometer alarms are implemented and separately owner-accepted; they do not implement the personal audio cue. Add the cue or explicitly defer this presentation requirement.

3. **P3 — Some planned visual hooks are absent.** Task 6 specifies an additional cyclone frost-overlay contribution; `FrostOverlay.java:31-37` still depends only on effective temperature. Its ordinary cold vignette still works and may already be saturated in Phase 5. The surface particle/flag heading follows the storm, but `BlizzardWindHelper.java:46-55` retains ordinary surface speed rather than blending severity strength. Dense flakes, streaks, sheets, front banks and fog are implemented and owner-accepted; these omissions are spec differences, not evidence that the accepted effect failed. Preserve the accepted appearance when deciding whether these details are still wanted.

## Requirement coverage

| Area | Review result |
| --- | --- |
| Regional field and severity balance | Implemented: immutable track, 1600-block width, .2 block/tick travel, smooth edges and lifetime/collapse envelope. Default cold/visibility/pressure values match. |
| Scheduling | Implemented: maximum four, first Moderate, 2–4-day first window, 3-day separation, final-day cutoff, Catastrophic from third event, overdue remainder postponement. |
| Persistence and sync | Immutable origin/durations/target, active state, plan/index and warnings persist through NBT; wire carries storm, suppression, server time and push scale; logout clears client state. Native NBT/wire tests are not an end-to-end multiplayer acceptance claim. |
| Phase/fight boundaries | Server policy ends erased/below-Phase-5 storms, collapses in Phase 6 or when disabled, prevents starts during Master fights and suppresses active effects. No ongoing retargeting. |
| Cold and room heat | Implemented: softened surface transition, stable deep ground, physical room-boundary heat transfer, exposed heater radius and wind chill; EVA/visor cold immunity retained. Deferred averaging issue remains excluded. |
| Warnings | Local pressure/trend/severity/ETA, Uplink advisory, persisted reminder state and personal message implemented. Personal sound gap above. Catastrophic buildup deliberately extended to guarantee the agreed 60-second warning. |
| Wind | Client applies player motion once; server handles nearby loaded mobs. Horizontal drift/resistance, crouch, sprint restriction, roof/water/windbreak rules and anchored bosses present. Creative, flight and vehicles exempt as scoped. |
| Front, fog and snow | Accepted layered approaching front, severity visibility, exposure darkness, heading, bounded denser snow/streaks/sheets present. Visual hook differences above. |
| Audio and thunder | Accepted recorded beds, crossfades, mob masking, barometer buzzers and four-recording/13-variant thunder bank present. Thunder timings match all three severities; finite overlap, cleanup and flash suppression implemented. Random weighting allows immediate repeats. |
| Accessibility | Shake defaults off per owner decision; independent flash strength and vanilla Hide Lightning Flashes respected. Push scale 0..2 is the documented implemented range; zero suppresses added drift while resistance remains. |
| Snowdrifts | Loaded-only bounded sampling, per-level cap96, one-layer additions, max8 layers in one block, protected objects/roofs/water/rooms/warm zones, persistence after stop. No cyclone destruction/damage/lightning entities found; the sole cyclone block write adds snow layers. |
| Replay and handoff | Five Gradle fixtures, compact controls, native parser coverage and owner accounts preserved in checkpoint/evidence. The checkpoint serves the replay/handoff role instead of the exact standalone filename in the old plan. |

## Explicit exclusions and guide preservation

The cyclone eye, boats/elytra handling and snow/wall thermal averaging correction remain deferred. No guide authoring or replacement was performed. `git diff` contains no Patchouli guide changes. The target has newer guide changes for Cold Flora; these must survive eventual integration. A read-only scan of registered worktrees did not locate cyclone-specific guide text by that keyword; this does not contradict the owner's report that guide work was done elsewhere and is not a reason to rewrite it. Guide file hashes across all14registered worktrees are retained in `build/cyclone-evidence/final-review/guide-files-before.json` for preservation checking.

No new owner visual matrix is required by this review. Preserve accepted wind, shelter/resistance, front, 110+FPS snow, drifts, thunder/flashes/variety and Moderate/Severe/Catastrophic observations. Headless proof does not replace perception or establish natural storm arrival/multiplayer acceptance.

## Verification

Fresh Java21 `./gradlew architectVerify verifyLabBridgePackaging --console=plain` passed in2m53s:895unit tests,676native tests and all667required cases, zero failures/errors/skips. Unit tasks reused valid current-source Gradle outputs; the native server ran fresh. `git diff --check` passed. Native report and log are preserved under `build/cyclone-evidence/final-review/`. All14registered guide trees are byte-for-byte unchanged across the review. The smoke jar matches the unchanged release; its predecessor is preserved and5dependency jars are unchanged. Existing development refmap/vanilla command-ambiguity warnings and the fresh harness missing-server.properties notice did not prevent completed report/gate verification. Gameplay source fingerprint remains `2a9b5deb8f656bd0c25ca3031193fe10b5e4ec0cf95abf35d9474f7ded2a3ac1`; release jar SHA-256 `ddfeb2cbebae1c525059562761041f29f4dd3bc047e6aecfec94ae0449b0fb44`. Release inspection excludes lab/GameTest classes and generated replay functions. No implementation edits, live world commands, new launch, Notion write, commit, PR or merge were made during the review.

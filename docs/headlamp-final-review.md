# Suit Headlamp final review

Reviewed on October 18, 2026 in `.worktrees/suit-headlamp`, branch `feat/suit-headlamp`, based on `3894fc3119080ca13116461444c86e501628114e`.

The owner accepted the main equipment behavior and ended the additional manual checklist. They authorized the recipe and final code review and explicitly deferred guide text to themselves. No guide or Patchouli entry is part of this delivery.

## Recipe

Craft one initially OFF Suit Headlamp with this shaped recipe (either pair of crafting-table rows):

```text
iron    glass     iron
copper  redstone  copper
```

This uses two iron ingots, two copper ingots, one glass and one redstone. A Power Cell is supplied separately. The recipe follows the previously accepted ordinary-material acquisition rule; EVA Helmet or ORSA Thermal Visor compatibility gates actual use. The existing lighting recipe regression now resolves all three lights through the real recipe manager, including both headlamp row positions, and checks that the crafted module starts OFF without prepaid energy.

## Code review

- Equipment comes from functional Curios Helmet Mount index zero. Stored inventory/offhand copies, cosmetic slots, disabled slots and incompatible helmets cannot provide a beam or spend power. Goggles retain their separate slot.
- The server authenticates a target-free H toggle payload and owns equipment, switch and energy decisions. No player command permission is needed for normal use. Free prototype power is gated behind the lab property and two exact prototype worlds; the equipment playtest exercises the production path.
- Payment is atomic for each 2.5 J tick at 50 W, including cells with only a few joules. Hotbar/main inventory precede the offhand. The code never traverses Lantern cells or containers. The module preserves its prepaid half-joule and ON/OFF setting; missing equipment/power pauses without changing the switch. No elapsed-time/offline debit exists.
- Projection is bounded to seven forward sources over sixteen blocks, with obstruction and source-center visibility checks. Reads and cleanup use nonblocking chunk readiness. The shared manager merges headlamp/Lantern brightness, removes obsolete sources, restores source water and preserves foreign lights and occupied terrain. The existing marker expiry handles orphans.
- The physical model uses the actual parent head transform, respects supported helmets and invisibility, and remains visible while switched OFF. Rendering registrations stay in the existing client adapter; server equipment code imports no client rendering classes. Existing pose tests capture rendered vertices and check helmet/goggles clearance.
- The accepted sprite and character geometry are retained. The generated image has a provenance record. Lab-only datapacks and bridge classes remain outside the release artifact.

No unresolved implementation blocker was found. Small cleanup corrected obsolete prototype/manager comments and removed a stale lab-menu claim that the recipe was still deferred. Gameplay tuning and the accepted appearance were not changed during this review.

## Recipe-build verification and acceptance (before placement correction)

Java 21 `build architectVerify verifyLabBridgePackaging` passed **850 unit tests and 658 native cases, including all 649 required cases**, with zero failures, errors or skips. The existing recipe regression passes with the new headlamp cases. Source fingerprint: `00ee1db2495828e25fd81331fc00f773c6daf2948f1ee72f80d8200f5a2d7adc`. Jar SHA-256: `25d781de0af604176e23bf5a1099a5a180f85657d1e43f3d8c199df40a78a156`. All accepted gameplay classes and assets are byte-identical to the previous jar; runtime differences are the new recipe and build metadata. Packaging excludes lab bridge/GameTest classes and generated playtest datapacks. Only the Frozen Dawn smoke jar was replaced, with the old jar preserved and all five dependencies unchanged. Existing Gradle/toolchain warnings remain. Machine-readable proof, unit/native reports, jar comparison, source snapshot and staging receipts are in `build/headlamp-evidence/final-review/`. There is no new unrelated Architect stress run for this lighting/recipe work.

Owner acceptance covers focus and close-wall brightness; clipping, vertical aim and moving cleanup; the prototype's clean-water/Lantern coexistence; production helmet compatibility, depletion/resume, powered mount removal and re-equipping; character appearance/head motion; and both OFF/ON save/reopen behavior. The proposed extra mounted-water/coexistence, OFF cell-replacement and visor/goggles visual passes were waived, not relabeled as new passes. Native checks retain their own scope. Actual multiplayer visual acceptance remains unperformed.

## Remaining limits

Native Minecraft block light spreads laterally and can spill around openings. It does not provide a shader-defined cone. The existing sixteen-block, peak-fifteen beam is the owner-accepted tuning. Guide text is intentionally deferred to the owner. The recipe is available after the running client reloads recipes or restarts; this review does not mutate or restart the owner's open world. Commit and PR creation remain a separate user request.


## Current: placement flash correction (2026-10-18)

After reopening the recipe build, the owner reported a brief dark flash when placing nearby floor blocks, with no matching flash on breaking and no flash at the farthest pictured position. Screenshots/first account are preserved under `build/headlamp-evidence/placement-flash-investigation/`. A fresh read showed the mount remained ON with charge and no slow tick windows. The short projection could place its only emitter in the cell replaced by a crafting table; the server retargeted it on a later update. The images show the arrangement, not the transient itself.

Short obstructed projections now keep a second visible source on the same ray until the normal second sample at six blocks. Free-space range, seven-source bound and brightness remain unchanged. The light manager installs the new footprint before clearing obsolete sources. This retains ordinary terrain/water, obstruction, readiness, cleanup and payment rules. Native regression uses real crafting-table placement at horizontal distances one through five and verifies a paid light remains immediately before the next manager tick. It also checks retargeting, normal draw and saved ON behavior.

Java 21 `build architectVerify verifyLabBridgePackaging` passed **850 unit tests, 659 native/all 650 required cases**, zero failures/errors/skips. Jar SHA-256: `9c1d2250f9ffac1a50de4e60198608ae4a45cda4d641ad932f4b6f968a5eeef2`. Source fingerprint: `58677bfd84de3171005d0b0f9f49d53b0acde8fb338997598ad0f63108babd80`. Relative to the owner-tested recipe artifact, only HeadlampBeam, HeldLanternLights and build metadata changed; all equipment, rendering, recipe and other resource bytes remain identical. The first incomplete verification attempt was cancelled to cover the four-block boundary and is preserved without a passing claim. Existing toolchain warnings remain.

All 66 saved-world files were archived and hash-verified under an exclusive lock and stayed unchanged through verification. The verified smoke jar replaced only Frozen Dawn; previous jar preserved, all five dependencies unchanged. Evidence, reports, source manifest, jar comparison and staging receipts are under `build/headlamp-evidence/placement-flash-fix/`. The existing world was relaunched through Gradle and the owner confirmed that nearby crafting-table placement no longer causes a visible flash. Fresh same-session diagnostics at gameTime 26718 show no ERROR/Exception, zero slow windows in 1088 observed native windows and zero diagnostic drops. The newly delivered equipment read reports mounted ON with a compatible helmet and charged cell. All source bytes still match the verified artifact. The focused correction is accepted; see `build/headlamp-evidence/placement-flash-acceptance/summary.json`. No renewed general manual checklist or multiplayer acceptance is claimed. Guide text remains deferred to the owner.


## Protected PR delivery (2026-10-18)

Owner authorized committing the headlamp and opening a PR into `feat/maeve-director`, keeping guide text deferred. Fresh remote protection requires `architectVerify` and `architectMonkey`. The accepted source still matches fingerprint `58677bfd84de3171005d0b0f9f49d53b0acde8fb338997598ad0f63108babd80`. Existing build/native/package proof remains 850 unit and 659 native/all 650 required; the additional required `architectMonkey` run passed **1159 server cases/all 500 required seeded stress cases**, zero failures/errors/skips. Full report and source match proof are in `build/headlamp-evidence/pr-delivery/`. Guide/Patchouli files are excluded from the commit. World saves, diagnostics, generated build artifacts and Python caches are excluded. Publishing the PR does not authorize merging it.

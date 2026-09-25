# MACS post–Slice 7 findings

This follow-up targets `feat/maeve-director` after [Slice 7, PR #93](https://github.com/cjaron03/frozen-dawn/pull/93). It packages the combined camp repairs, reconnaissance presentation and evidence calibration, bow-cover tuning, and the owner-approved fifth counter: sword guard. It does not implement Slice 8's individual history, traits or forward simulation. The [Source of Truth](https://www.notion.so/39d7cfaa890181c1bad4f6babad80880), particularly §§9.13b, 9.19–9.20 and 12, remains the contract.

## Findings and resulting behavior

| Finding in ordinary play | Change and evidence | Limit of the finding |
| --- | --- | --- |
| Scouts failed to inspect an open camp doorway. | Routes now respect supported fractional floors. Inspection samples clear the full support between unequal witnessed feet heights without ignoring a sealed doorway. The mixed-floor native case failed before the repair; live round 8 subsequently reported OPEN and completed inspection. | This repairs observed geometry, not a general room model. Unloaded chunks and hidden obstructions remain unavailable. |
| Pursuit built unnecessary ice on usable ground. | Before scaffolding, a retained route refreshes a changed target goal. The native reproduction placed two unnecessary blocks before the repair. Live round 6 then recorded a goal refresh, zero scaffolds and four protective walls. | The original replay lacked cached-goal data, so the reproduction establishes a failure mechanism rather than proving its exact original cause. |
| Scouts looked like idle ordinary combatants, and could appear during fighting. | One shared survey is admitted per player encounter, before combat or commitment. Purple eyes and the existing thinking pose identify noncombat work. A timed withdrawal dissolves into a vulnerable soul cloud, exchanges visible pulses, reforms and resumes ordinary engagement. | Particles depict communication; accepted observations still publish immediately. There is no teleport, immunity, replacement entity or physical Maeve in the sky. |
| A specialized scout needed a modest evidence advantage. | An active scout assigned to the subject contributes +0.30 support versus ordinary +0.20. The live arrow check credited +0.30 once, then +0.00 verification for a second hit in the same encounter. | It cannot upgrade an already credited ordinary contribution. Departure/cloud observations receive ordinary weight. Historical encounter snapshots still prevent immediate adaptation. |
| Bow cover felt arbitrary; stationary peeking stalled pressure. | Historical ranged confidence at least 0.75 strengthens ordinary cover preference. A local lower-torso sight ray guides a grounded two-block wall; deep snow can leave upper-body shots open. Repeated fortification requires at least 120 ticks and two blocks of movement; the owner-requested twelve-block ordinary tactical pool bounds construction (Masters retain six). Ordinary PEEK ends after 30 ticks and has a 100-tick retry gap plus movement. | Cover reacts to locally visible geometry, not bow-draw input or future arrows. Actual damage interrupts a thinking hold; misses do not. Positional commitments retain their 400-tick duration. |
| Snow cover worked, but three-block pillars looked wasteful and pursuit could circle. | The owner accepted placement, requested two-block pillars and a twelve-block trial. The required native replay reproduces a snow-surface circle suppressed by the velocity exemption; the existing replan deadline now overrides that exemption. | Extra retained cover and circling recovery still need current-build live acceptance. Retreat construction logic is unchanged. See the [review evidence](macs-pr-review.md). |
| A single shield raise did not communicate sustained adaptation. | Historical sword evidence now selects a real offhand shield with recurring close-range guard and exposed attack windows. Ordinary damage briefly staggers it; axes and breakage end it. Retreat/healing suspend the same shield, then guarding resumes for the same encounter. | No replacement shield, new commitment or attention refund is granted. Local-contact expiry is 600 ticks; rear hits, startup, exposed windows and healing remain counterplay. |
| Retreat cover could overlap its builder and cause suffocation. | Tactical placement rejects occupied cells before changing vegetation or evicting previous cover. A native retreat reproduction took five suffocation hits before the fix and none afterward, while still building cover and healing. | The original round 9 trace has four hits and lacks fractional positions; the reproduction establishes a failure mechanism, not an identical replay. |
| Pursuit could try mining cover immediately after building it. | Successful defensive construction and pool eviction now refresh the builder's cached local route. The flat-ground reproduction walks around its wall without the former mining attempt; a raised-target control also preserves cover and reaches the player. | The original round 10 upper-block destruction is not exactly reproduced. Live confirmation on the revised build remains required. |

The camp clarified a distinction: confidence that a player favors bows is separate from the effectiveness of the cover counter. Failed cover can be deferred while ranged confidence remains 1.0. Likewise, a confident bow model does not resolve uncertainty about a different doorway; that doorway can still justify a precombat survey.

The informed bow playtests progressed from reports of ordinary attacks to useful defensive uncertainty, some blocked arrows, and pressure while the Architect worked around its wall. The owner rated several rounds approximately 2/5 threat and later described the flanking as frightening. Different routes, terrain and player actions prevent treating those rounds as a controlled balance comparison. Detailed chronology and trace references remain in [the camp record](macs-integrated-playtest.md).

## Live acceptance retained outside the world

| Replay | Observed result |
| --- | --- |
| Scout pulse, `a78667ce-09d1-4db8-b1c0-63eb8b18f03a` | Owner saw withdrawal, dissolution, upward and returning pulses, then ordinary engagement. Journal corroborates the surrounding lifecycle. |
| Scout credit, `ab148d40-e0a4-4611-a8d9-4cfa3b05ec96` | Real arrow during inspection credited +0.30; another arrow was freshness-only. Damage interrupted scouting and ordinary combat resumed. |
| Ground pursuit, Brutal round 6 | See the dated round 6 record for the complete exported trace: no navigation scaffolds, eight defensive wall blocks, and the changed-goal refresh. |
| Doorway inspection, `d446ae2a-8488-4b87-8e8d-a17843f91cdb` | Previously failing mixed-floor entrance reported OPEN; no combat or destruction occurred. |
| Sustained shield, `60ccc5a6-ad55-45fa-a8a0-7c70aeecc202` | Guard suspended at actor tick 292, resumed at 362 after healing, then blocked four real sword contacts for approximately 24 prevented damage. It continued raising through elapsed tick 880. Owner reported improvement and accepted the result. |

The shield replay's QA finish removed the actor. Its strategy result is UNKNOWN, not a natural combat win or loss. Earlier pulse, camp and doorway checks used their dated builds; the final regression suite covers those behaviors again. The final shield replay used the source fingerprint in [the verification record](verification/macs-calibration.json). These are informed mechanical and presentation checks, not a new blind integration test.

Full logs, closed-world backups and journals remain in the local evidence bundles referenced by the detailed documents. They are not embedded into a gameplay save as belief archives or uploaded with this change. The tracked summary retains only the facts needed to review the result.

## Compatibility, multiplayer and performance

Tactical SavedData remains version 4. New encounter admission flags, provenance weights and blocked-damage totals are optional. Legacy provenance keeps unknown weight, older results default blocked damage to zero, and old melee history is not retroactively converted into sword evidence. Reports persist; active execution does not resume after reload. Spent encounter state remains spent. Erasure clears tactical knowledge and execution immediately; the permanent violation ledger is unchanged.

Masters remain independent guardians. Player history, scout eligibility and commitments remain isolated by subject. Shared observers cannot multiply encounter contributions. New guard execution uses bounded local sight/range checks and no global polling or server cache. Cover considers at most twelve candidates; no observation loads chunks. Existing profile, belief, provenance and attention caps remain enforced. The Director facade is 297 lines under the 300-line build gate.

The final shield lifetime change leaves the verified bow geometry, tactical cadence and selection files byte-identical to the preserved pre-shield baseline; their hashes are in the verification record. Earlier bow changes in this PR are intentional and documented above.

## Verification and remaining work

Required command: `./gradlew architectVerify architectMonkey --console=plain`, using Java 21 and the existing seeds and shared fixtures. The accepted shield source passed 609 unit tests, 142 native GameTests including all 137 required entries, and 500 seeded stress cases; the stress server also repeated all 142 native tests. There were no failures, errors or skips. The [PR review follow-up](macs-pr-review.md) adds nine required native cases, bringing the native suite to 151 and required manifest to 146. The PR publishes fresh checks for its exact head; the older tracked record still identifies the source and artifact used for live shield acceptance.

Native regressions exercise actual damage/item events, terrain, projectile collision, scout admission, cloud vulnerability, repeated cover, shield blocking/knockback/durability, real retreat and potion healing, causal result scoring, save compatibility, player isolation and erasure. Exact live QA functions also load and execute through required GameTests. Python gate tests and shared-fixture checks remain part of the build.

Before final integration to `main`:

- Complete the paired Normal/Brutal comparison and a fresh blind combined encounter. These sessions do not yet establish final difficulty or horror calibration.
- Repeat live multiplayer pressure and measure performance on the current combined build; deterministic isolation checks and earlier slice LAN results are narrower evidence.
- Replay retreat and pursuit on the revised terrain build. Native regressions now prevent self-overlapping cover and needless mining after construction, but the original round 10 upper-block destruction still needs live confirmation. See the review follow-up for before/after evidence and its limits.
- Test natural spawning, endgame atmosphere and resource pressure separately from the benign Phase 0 QA camp.

Further behavior changes should address a reproducible finding, then repeat its baseline and final checks. Slice 8 remains a separate optional-depth decision once the core is stable.

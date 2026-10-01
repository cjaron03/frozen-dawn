# Pawn Convergence: acceptance and delivery review — 2026-09-30

Reviewed [PR #98](https://github.com/cjaron03/frozen-dawn/pull/98) against the current [MACS source of truth](https://www.notion.so/39d7cfaa890181c1bad4f6babad80880), especially §14 and §12. The review found no new actionable code defect. It does **not** yet approve merging: the reliable base-diversion exit remains partially demonstrated, and PR #98 remains draft pending that qualification.

## Reviewed revisions

- Target: `origin/feat/maeve-director`, `c94ddd042783c50ee4d8fee5e524600c9032ba9e`; refreshed on 2026-09-30.
- Historical PR head before this delivery: `4bdb7b5625a7d6be9b26a3a13646b9bdd6e7ee33`, open/draft. Its existing GitHub checks cover that earlier commit only.
- This delivery includes local warning refinement `3c46399`, avoidance sprint `744fec4`, the development command bridge, replay helpers, required cases and acceptance documentation. The delivery commit is recorded in `build/macs-pawn-evidence/publication-20260930/verification.json` after committing.
- Verified source fingerprint: `f1766cd44613c26e39060d4c754b4107e2183f3446be79362b411b74203b327d`. All 2,910 captured source, fixture, tool and configuration file hashes remained unchanged during the final gate.

Review covered confirmed-death hooks and role filters, persistent dispatch provenance, hotspot episodes/decay/outcomes, loaded donor selection and population accounting, attention admission/dispersal, local movement/contact, lifecycle/erasure, the development bridge and release packaging, and the required regression manifest. The ordinary combat latch, mission/commitment exclusions and own-sight contact checks enforce the idle-donor rule. No production code or tuning changed during review.

## §14.10 acceptance

| Required result | Assessment | Evidence |
| --- | --- | --- |
| One many-death fight cannot activate alone | Passed | Live single-encounter environmental control; unit episode tests and native local-contact gate. |
| Minimum warning and longer travel; unprompted visual distinction | Passed within the recorded participant scope | Accepted near/far dispatches, including 740 ticks for the long route; concealed four-view series identified 4/4 correctly. The participant knew both effects from earlier work; no claim of naive first-time recognition or future-behavior prediction. |
| Dispatched deaths cannot feed any hotspot, including reload/dispersal | Passed | Two live wipes and saved/reloaded history; native death, reload and erasure cases; persistent per-pawn provenance. |
| WIPE/SUCCESS/UNKNOWN and partial-casualty rules | Passed mechanically | Unit outcome sequences and native real death/shield-block contact; live two-wipe latch. Empty-coordinate arrival stays UNKNOWN. |
| Avoidance survives cooldown/reload; strict decay and fresh-evidence retry | Passed | Accepted cooldown probe, closed-save reload, decay below 3.0 and subsequent real-evidence retry. |
| Excluded roles, erasure and explicit future E11 check | Passed mechanically | Required role/death and erasure cases. E11 itself remains unbuilt as explicitly allowed by §14.7. |
| Environmental deaths build history | Passed | Live environmental bait; native physical trap and final fall/lava/trap death cases. |
| Decoy reliably draws pawns away from the player's base | **Partial — remaining live exit** | The field replay moved both original pawns out of their source area. The natural-arrival replay used production-born pawns. Base Bait produced a real group and useful time to refuel, but required protected admission staging; the preceding unprotected Base Diversion attempt produced ordinary combat and no dispatch. A subsequent focused Survival run admitted the same natural pawns without a Creative admission period: cloud after457ticks, first arrival320ticks later, both confirmed in the region and useful base work reported. Its base began beyond both pawns'96-block detection range, so it strengthens ordinary-Survival admission evidence without measuring removal of existing base pressure or ambient reliability. See the2026-09-30 checkpoint and focused-base evidence. |
| No convergence during the contributing local fight | Passed mechanically | Actual sight/resumed-contact cancellation case and the no-dispatch base attempt. Donors already fighting are excluded. |
| Tester describes avoided and converged kill zones | Passed | Accepted avoidance sprint and environmental convergence descriptions. |

The remaining exit comes directly from §14.10: “A decoy hotspot reliably draws pawns away from the player's base.” A new 48-block extraction rule, a mandatory naive tester, or a fixed probability target is **not** being added. Natural spawning frequency and the conservative population ceiling remain provisional calibration risks under §14.9. Human multiplayer acceptance remains deferred and is not claimed by solo evidence.

## Final verification — 2026-09-30

`./gradlew test --rerun architectVerify architectMonkey --console=plain` exited successfully. This run passed 644 unit tests, 8 gate-harness tests, 211 native GameTests and all 206 required native cases. The unchanged 500 seeded stress cases passed alongside the repeated 211 native cases: 711 successful cases in the stress server report. Both XML reports have no failures, errors or skipped cases. Shared fixtures, the Maeve facade and release packaging passed. No test was added or production behavior changed during this delivery review.

The final bundle contains console output, unit XML, both native XML reports, every required trace, the two manifests, source-file hashes and the built jar: `build/macs-pawn-evidence/publication-20260930/`. The source was verified before committing; only acceptance documentation changed afterward. The bundle's `finalCommit` identifies the resulting commit, and the stored source hashes must still match before publishing status checks for it.

Built and installed smoke jar SHA-256: `d004108c2a6fa4de2ac8187646cd07c3c744a790c564ecd961d65b7186449115`. The development bridge classes and lab resources are absent from the release jar. Existing Java-installation discovery and fresh GameTest-world startup warnings remain non-fatal; the completed reports establish the gate result.

## Delivery and remaining acceptance

The focused Survival check has been completed and preserved. Do not repeat it or create another live scenario automatically. It strengthens actual production admission and useful activity in ordinary Survival; it does not resolve the ordinary base-pressure reliability limitation in the matrix above. No actionable code defect was found that justifies changing the idle-donor rule or spawn policy during this handoff.

The owner approved publication on 2026-09-30. The implementation and QA delivery at `8d3b204` is published in PR #98 with both required checks successful. This documentation follow-up records that result; its application source is identical to the verified delivery. Retain draft status while the remaining §14.10 base-diversion qualification is resolved. The current PR body and Notion record must distinguish controlled convergence from ambient reliability. Human multiplayer remains deferred. A successful gate or replay is not merge approval; merging still needs the owner's instruction.

## Preserved live state

The client heartbeat confirmed that no world was loaded before and after the final backup. The completed **MACS Focused Base Check** world is preserved as an 858-file archive at `build/macs-pawn-evidence/focused-base-20260930/result/closed-focused-base-world.zip`; SHA-256 `ac667babe0d09779feee422dd0cdeda49fb7bfa7d1ab71848d796f0260e03881`. Every source file stayed stable and every archived file matched its source hash. The completed encounter remains at stage 123 after its 3,141-tick observation; no actor was killed, unloaded or reset to manufacture the result.

The earlier Signal Check archive and all four concealed-condition descriptions remain in `build/macs-pawn-evidence/signal-check-20260929/result/`. The failed unprotected Base Diversion attempt and protected Base Bait run remain part of the evidence. None of the completed worlds was resumed for this handoff.

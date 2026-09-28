# Pawn Convergence checkpoint — 2026-09-27

The owner ended this session after accepting the environmental bait replay. The world saved cleanly at 23:27:12. Work is parked for the next session; this is a local progress checkpoint, not merge approval.

## Owning checkout and delivery

- Worktree: `/Users/jaroncabral/.codex/worktrees/macs-pawn-convergence/minecraft-mod`.
- Branch: `feat/macs-pawn-convergence`, targeting `feat/maeve-director`.
- [PR #98](https://github.com/cjaron03/frozen-dawn/pull/98) remains open and draft. Its published head is `4bdb7b5625a7d6be9b26a3a13646b9bdd6e7ee33`; the previous local checkpoint and this checkpoint have not been pushed.
- This checkpoint includes the accepted avoidance sprint, its collision/handoff regressions, cooldown and environmental QA functions, and live acceptance records. Refresh required results for the eventual published commit before delivery.

## Completed live checks

1. Actual convergence, accepted soul cloud/action-bar presentation, minimum warning and actual player contact with a recorded SUCCESS in the earlier replay.
2. Two confirmed pre-contact group wipes latch avoidance without adding dispatched deaths to hotspot history. Save/reload retains it; below-floor decay clears the old failure cycle; fresh ordinary deaths can earn a new dispatch.
3. The same two pawns physically left the controlled source area, reducing its count from two to zero. Natural donor frequency and useful ordinary-play base relief are still separate checks.
4. Idle-pawn avoidance physically exits the region on layered snow. The owner accepted ordinary chase-speed sprinting with “better.” Only PAWN_AVOID gains this pace; blocked routes, damage, cancellation and the ten-second handoff clear it.
5. Avoidance survived cooldown expiry while weight stayed above 3.0. Two ordinary source pawns remained undispatched at weight 4.5304, cooldown zero, wipes=2 and avoid=true. The owner saw only wandering.
6. Six genuine bow deaths in one fight formed one completed encounter. With sufficient weight, zero cooldown, no avoidance and two source pawns available, no convergence occurred.
7. Two full-health actors died through actual falling physics and two through actual lava physics without a player attacker. These formed separate second and third episodes. Lava was sprinted, so only its native death recording is accepted, not normal-speed lava presentation.
8. The resulting ten deaths/three completed encounters produced an actual three-pawn dispatch at weight 9.42177497750151. Its decision history contains all six bow, two fall and two lava events. First arrival was 360 ticks (18 seconds) after the cloud. All three original UUIDs reached the historical spot during the normal-speed view. The owner recognized the bait.

## Current closed world and save state

World: **MACS Pawn Environment**. Last fixture stage: **73**; timer: **500**. The player watched in spectator mode and froze the view before the final dump.

Hotspot `b19b3ca2-0859-4523-9d95-9229dc0023f3` is anchored at (4003,101,4006). The final live dump retained ten deaths/three encounters, lastEvidence=1000715, weight 9.2663, wipes=0, avoid=false and cycle=0. Dispatch `165aab4d-eac0-4b29-9ac6-2d8cce7a672a` had all three members alive and no player contact. Regional arrival is not a combat SUCCESS.

Shutdown logged UNKNOWN/SERVER_STOPPED. Read-only inspection confirms the saved NBT contains an unfinished group, so the existing loader will release it as UNKNOWN/RELOAD_RELEASED on reopening and apply its ordinary cooldown. This is expected lifecycle handling; do not count shutdown or later cleanup as a WIPE. The saved stage and history are verified, not inferred from an older dump.

Complete closed-world backup: `build/macs-pawn-evidence/checkpoint-20260927-232915/closed-environment-world.zip`.
SHA-256: `e266486638bf1e714761117d867ec10f88785094265964beb9fd56feed2d51e6`.
The same folder contains the shutdown log, saved-NBT inspection, save hashes and previous detailed checkpoint history. These local evidence files and worlds are intentionally outside Git; keep this worktree available.

The accepted **MACS Pawn Sprint** avoidance world is preserved separately, including its full backup at `build/macs-pawn-evidence/environment-controls-20260927-230738/closed-avoidance-world.zip`. Earlier Field Checks, Resume, Warning Polish and original Convergence worlds remain separate.

## Verification

The current source passed `architectVerify`: 640 unit tests, eight gate-harness tests, 202 native GameTests, all 197 required entries, and all 51 generated functions parsed at client permission level two. The new trap regression uses full-health native gravity and lava physics. The accepted sprint runtime also passed all 500 unchanged seeded stress cases; subsequent edits added QA functions/tests and documentation, with all 1328 production class entries verified identical. Those 500 cases were not rerun for QA-only changes.

Current tested source fingerprint: `c098171a565590c301260c6e872bd4836bb9ebe0ba541659d736fc2c92a4699c`.
Built and smoke-installed jar SHA-256: `d83762efd9b40ca7fea438ef7a088b66dafe0b78fa65a43963ca45b2119367be`.
The checks preceded this local checkpoint commit; the later edits record live evidence and resume instructions. They are not fresh published checks for this commit.

Evidence roots under `build/macs-pawn-evidence/`:

- `avoidance-sprint-20260927-223933/`: automated sprint checks and accepted departure.
- `cooldown-hold-20260927-225255/`: top-up, cooldown expiry and accepted idle-donor probe.
- `environment-controls-20260927-230738/`: verification, copied-world provenance, single-fight control, fall/lava evidence and accepted three-pawn convergence.
- `checkpoint-20260927-232915/`: complete current closed save and shutdown inspection.

## Resume next session

Start in the owning worktree and inspect Git status and the latest checkpoint before launching. If reopening the preserved Environment world, run `/tick freeze` and `/fd maeve dump` before setup, cleanup or time advancement; check the expected reload release. Do not rerun `env_setup` in this accepted world, because it clears tactical history.

Next recommended work is a separate **natural-population/base-diversion test**: confirm ordinary spawning supplies eligible donors at a practical frequency, and that their diversion meaningfully reduces pressure at the base. The completed tests supplied actors for reproducibility and do not establish this. Define the observation window and compare actual source UUIDs/positions before preparing the next world. Do not fabricate history, spawn extra production reinforcements or accelerate the visible approach.

Other remaining live acceptance: short/long warning perception, unprompted distinction between the gathering cloud and reconnaissance, and multiplayer pressure. Keep PR #98 draft until the applicable acceptance criteria and final published checks are complete. No new test world or live commands for the next check have been prepared yet.

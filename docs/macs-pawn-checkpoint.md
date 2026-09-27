# Pawn Convergence checkpoint — 2026-09-27

Paused at the owner's request after the visible-avoidance replay. No further tuning, testing or merge should run until the owner resumes.

Branch: `feat/macs-pawn-convergence`, targeting `feat/maeve-director`. [PR #98](https://github.com/cjaron03/frozen-dawn/pull/98) remains draft. This checkpoint commit contains the accepted soul-only cloud, longer action-bar warning, outcome/field QA functions, required field regressions and live acceptance notes. It is a progress snapshot, not merge approval. The local checkpoint has not been pushed.

## Latest finding

The owner reported: “it just appears to be walking around.” Actor `0a400de5-719b-48e4-bac8-b52a3520079f` received `MAEVE_PAWN_AVOID` at replay tick 12. It moved from (4016, 101, 4006) to (4019, 101, 3986), leaving the 24-block region anchored at (4001, 101, 4006). Its checkpoint position is about 26.9 blocks from that anchor. At replay tick 216 it resumed ordinary action selection and selected FORTIFY with no target. Mechanical avoidance is verified, but the owner did not recognize it as purposeful avoidance. Review its presentation and transition back to local behavior before declaring the live criterion fully accepted. No behavior changes were made in response to this report.

The last direct dump at 03:18:48 local time retained fourteen deaths/seven encounters, weight 3.5798, wipes=2, avoid=true, cycle=1 and 8292 cooldown ticks. This is a historical checkpoint, not a claim about the live state after more unpaused time.

## Verified so far

- Actual convergence, warning presentation, recorded player contact and final SUCCESS outcome.
- Two actual pre-contact group wipes; dispatch deaths never refreshed hotspot history.
- Avoidance latch persistence through save/reopen, below-floor decay, and fresh-evidence retry with the old wipe penalty cleared.
- Controlled physical diversion: the same two actor UUIDs left the marked source area; source count fell from two to zero while both lived.
- Actual regional avoidance event and physical exit on snow. Human clarity remains unresolved.

The latest `architectVerify` passed 640 unit tests, eight harness tests and 200 native GameTests, including all 195 required entries and all 32 replay functions. Source SHA-256: `f6d8cf2e844d198c923bdcb93bc8a0646563630d27b3d6efd55c0801951fa1d7`. Jar SHA-256: `f08ff27f486347696881b1afa734730572cf1bb6aa1d76e54ce05408eb791503`. These gates preceded the checkpoint commit; subsequent edits were evidence documentation. The unchanged production code previously passed the 500-case stress matrix. That matrix was not repeated for the two additional QA cases. Refresh exact-commit required results before eventual publication/merge.

## Resume safely

Use `/Users/jaroncabral/.codex/worktrees/macs-pawn-convergence/minecraft-mod` and world **MACS Pawn Field Checks**. The player is in spectator observation mode and the last completed fixture stage is 41. Do not blindly rerun `avoidance`: its entry guard expects stage 16. Do not run `setup`, which erases tactical history.

First inspect the current log and working tree, then obtain `/fd maeve dump` before any new practice or time sprint. Time may have decayed the weight since this checkpoint. Review the preserved avoidance trace and the owner's legibility concern; make any next change narrow and evidence-backed. Prepare a valid replay state only after inspecting the current latch and history.

The complete earlier world is preserved in `build/macs-pawn-evidence/20260927-field/closed-before-field.zip`. The original **MACS Pawn Warning Polish** world remains separate. Logs, dumps and actor traces for the latest view are in `build/macs-pawn-evidence/20260927-field/checkpoint-avoidance/`; the field diversion is in `live-diversion/`. No full backup of the current field world was attempted while it might still be open. Save and quit it before another full-world backup or installation.

Remaining checks include visual legibility, avoidance through cooldown while weight stays above the floor, ordinary-play base relief and natural donor supply, cloud distinction without coaching, and the explicitly pending multiplayer acceptance. Preserve these limits in the PR and Notion. Do not infer merge readiness from the passing automated gate.

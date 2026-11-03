# Blind Architect cave encounter

The owner accepted the practical usefulness of the range revision during cave view 19 and requested removal of breathing. View 19 ended on the owner's explicit request after 1,287 frames (64.35 seconds), with its first account and complete trace preserved. All 210 closed-save files are archived under `build/blind-architect-evidence/remove-breath-20261001/`. The new cold-breath sound, server emitter and hearing cue are removed; the original client visual puff is restored. Echo, vibration, ordinary hearing and movement-return fixes are byte-identical to the accepted range revision. The owning client is restarted in terminal 20230 with breathing removed; startup was verified at the title screen. No new view is staged or authorized automatically.

The owner said, “honestly get rid of the breathing. i like how useful it is now. how else could we make it mroe realistic?” The trace admitted 15 echo returns up to 15.75 measured blocks, with one return pending when stopped. All 52 breathing queries found no in-range air path; no breath cue entered memory. The actor travelled 82.975 blocks, spent 702 frames with the player in zero block/sky light, and used WANDER, LISTEN, INVESTIGATE, PURSUE and SEARCH. Player health stayed at 20 and no actor break occurred. No vanilla target, journal drop, unexpected mob or source-audit failure appeared. Ordinary cave drops were recorded as intended. This accepts the owner's practical usefulness judgment for this 64-second view; it does not establish every channel, combat, natural cave or LAN case. Trace SHA-256 `55d6dbfcf3a1333cec6a9426330e2e9ae7db9576dad1305b7e28f6e552e30a94`; decisions `dd743363806046860977e3e478e365f8081d1a10003427a1c4b5dadeac61b256`. Result: `sensing-range-20261001/live/run-19/result/`.

Owning checkout: `/Users/jaroncabral/Projects/minecraft-mod/.worktrees/blind-architect`, branch `feat/blind-architect`, HEAD `0885e22812f8723a6f55cdf52da87b62a18c4485`. The current remote integration baseline `feat/maeve-director` was verified at that same commit; the local branch of that name is older. No commit, push or merge is authorized by this playtest.

The constructed cave occupies x=0–63, z=-24–31, below y=-34. Four irregular chambers connect in a loop with stone pillars, bends and dark side rooms. Sparse floor lights help orientation; the north and south chambers have zero block and sky light before the owner places lights. There is no glass corral. Stone can be mined by the actor and player. The floor is level; this does not represent every natural cave or validate natural spawning.

A fresh held BA starts at (8.5,-44,0.5). The owner starts at (23.5,-44,0.5), behind a stone pillar, with EVA armor, a full Mk.III oxygen tank, pickaxe, sword, food, torches and building blocks. Start restores health/food, enables Survival and releases ordinary AI. The controller receives no target, path, sensory input, confidence adjustment or seed from the live fixture. Night vision is cleared and Start refuses it. Other mobs are excluded from this controlled encounter; ordinary dropped objects remain active after preparation and are recorded.

Play freely for up to three minutes: move, hide, pause quietly, use cover or darkness, build or defend yourself as you choose. Torches are available if needed. There is no required action schedule. The run ends at 3,600 ticks, on either death, on a recording fault or on departure from the recording volume. It preserves the outcome, holds a surviving BA and returns the owner to Creative. The owner may pause early and request a stop. Capture completion is not a judgment that the hunt or hiding succeeded.

At **ENCOUNTER COMPLETE**, describe what happened before diagnostics are opened, then pause. Useful first impressions include whether it found the player, whether it lost the player behind cover, what clicks and movement conveyed, and any apparent stalling or unfair tracking. Preserve that account verbatim before inspecting the trace. Do not repeat setup or Start.

## Historical view 19 setup audit — completed; do not rerun

The following records the completed setup procedure. The completed view18 had to retain its original held actor, 3,600/3,600 frames, archived 1, inconclusive 0 and matching trace/decision hashes. Prepare exactly once with `blind_vibration_lab:cave_range`, verify ready stage 1/run 19/case 19/timer 0/frames 0/isolated 19 and the fresh held actor, then invoke `blind_vibration_lab:start` once. Confirm the world remains paused with stage 2 and timer/frame zero. An explicitly requested early stop uses `blind_vibration_lab:cave_range_stop` after at least one frame. Do not use the historical view18 preparation again.

## Historical view 18 setup audit — completed; do not rerun

Read current status and snapshot first. Preparation is permitted only from the intact archived view 17 with the original held actor and hashes. It replaces the fixture geometry only after the completed world is archived. The approved preparation function is:

```sh
python3 tools/lab_client.py --world 'Blind Architect Reusable Lab' function blind_vibration_lab:cave
```

Verify the new held actor, Creative participant, clean source inventory and stage 1/run 18/case 18/timer 0/frames 0/isolated 18. With the owner menu-paused and ready, start once:

```sh
python3 tools/lab_client.py --world 'Blind Architect Reusable Lab' function blind_vibration_lab:start
```

Confirm stage 2, timer/frame zero, ordinary AI enabled and menu pause still active before handing control back. An owner-requested early stop uses `blind_vibration_lab:cave_stop` at stage 2 after at least one captured frame. No automatic advance or replay exists.

## Evidence and interpretation

Preparation preserved all 189 closed-world files under an exclusive session lock with source/archive hashes verified. Archive: `build/blind-architect-evidence/cave-hide-20261001/before-world.zip`, SHA-256 `66de26173f4bcc6b6504efee19c14bd7aa1d3977ff70b6e36183becb6fd76cad`. All 106 existing WIP files were preserved separately. The original view 17 actor was held with 40 health; stage 3, 480/480 frames, archived 1 and inconclusive 0.

Each normal server tick records actor/player poses, health, ordinary navigation/mining, the selected estimate and all tracks, last measured echo/vibration, actual player block/sky light, night vision, posture, oxygen, damage source, source inventory and a read-only direct eye ray. The diagnostic eye ray is never fed to the controller. The complete production decision journal retains distinct event timing and return provenance; repeated last-result snapshots are not counted as fresh signals. Start, trace, decisions and summary live under `blind-lab-results/run-18-<actor UUID>/`, with verified hashes and no overwrite.

After the first report, compare acquisition/loss to actual source events, light and occlusion. A dark player in an open acoustic path and a player hidden behind solid cover are different physical conditions. Report observed behavior and failures without inferring that all darkness, all caves, natural spawning, persistence or LAN have passed. Preserve any failure before repairing production behavior, then rerun the same fixture or seed where applicable.

## Historical view 18 build

Java 21 `architectVerify --offline --console=plain` passed 654 unit tests, 275 native GameTests, all 270 required cases and 89 parsed functions, zero failures/errors. Native cave checks verify both zero-light rooms, connected walking routes, starting occlusion, a 600-tick ordinary WANDER trace with 8.576 blocks maximum displacement, admitted real dropped items and six independently preserved stop/termination captures. The 3,600-frame time-limit control verifies the recorder, not a simulated human hunt. All production classes, sounds and assets are byte-identical to the accepted channel-suite build. The previous 500-seed stress result remains applicable and was not rerun. Built/smoke SHA-256 `cbb9a99cef612a56481f1a5aa953b60a4c7ea3869f9e52f79106e09f1362256e`; all four smoke dependency jars are unchanged. Existing deprecation warnings remain. The owning client was restarted in terminal 52450, with a fresh title-screen heartbeat and no startup error; the separate smoke profile loads its copied jar on its next restart.

Proof: `build/blind-architect-evidence/cave-hide-20261001/verification.json`. Installation changed only eight datapack files; every gameplay file and earlier result stayed byte-identical while the save was closed.

## Historical range revision build

Java 21 `./gradlew architectVerify --offline --console=plain` passed 654 unit tests, 280 native GameTests, all 275 required cases and 91 parsed functions, with zero failures/errors. `./gradlew architectMonkey --offline --console=plain` passed all 500 stress cases plus 280 native cases (780 total), zero failures/errors/skips. All 500 prior fixtures, seeds, rotations, initial terrain hashes and scripted inputs match; all decision trace hashes and the current source fingerprint were verified. Built/smoke SHA-256 `f47849714281eaba5305abfddd014e322e8b8ce9defe07bee8c3f5cd17f568d6`. All four smoke dependencies are unchanged. All 310 packaged audio hashes match the inventory; approved click bytes are unchanged. Existing deprecation warnings remain. The separate smoke profile loads its copied jar on its next restart.

Proof and raw reports: `build/blind-architect-evidence/sensing-range-20261001/verification.json`, `native.xml`, `stress.xml`, `stress-matrix.json` and `stress-fixture-comparison.json`. Human acceptance of the changed acquisition and breath sound is pending; natural caves, live persistence and LAN remain separate validations.

## Current breathing removal

Java 21 `./gradlew architectVerify --offline --console=plain` passed 654 unit tests, 279 native GameTests, all 274 required cases and 91 parsed functions, zero failures/errors. The removed native case belongs only to the removed breathing feature; all four range regressions were rerun. The jar audit confirms every range-sensing class and approved click byte is identical to the owner-accepted build, and all 309 packaged audio files match the inventory. The prior 500 stress cases remain recorded; they were not rerun for removal of the independent breathing emitter. Built/smoke SHA-256 `fcd956fda384b4df57a81203452894263b7bfd33238bf3e720bacafd1cce5854`; all four smoke dependencies are unchanged. Existing deprecation warnings remain. The owning lab client was restarted in terminal 20230 with a verified fresh title-screen heartbeat and no startup errors. The separate smoke profile loads its copied jar after its next restart. Proof: `build/blind-architect-evidence/remove-breath-20261001/verification.json`. No view 20 is prepared.

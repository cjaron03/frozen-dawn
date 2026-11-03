# Blind Architect underground base hunt

Owning checkout: `/Users/jaroncabral/Projects/minecraft-mod/.worktrees/blind-architect`, branch `feat/blind-architect`. Open **Blind Architect Reusable Lab** (`run-lab/saves/Blind Architect 09 Disconnected Supports`).

## Current state

View 11 is complete. The owner reported being found and hit before diagnostics. The actual trace acquired at 5.6 seconds, made its first break at 11.25 seconds, entered at 21.05 seconds and landed a hit at 30.95 seconds. It made four physical cuts. The view ended at 31.05 seconds because the ten-second entry observation elapsed, two ticks after the hit. Both HEARING and ECHO guided behavior; this accepts the combined constructed-base encounter, with individual channels to be tested separately next. The owner saved/quit and the client is at the title screen. Do not prepare or Start view 11 again.

All 621 frames and trace SHA-256 `330c1bff5221e5d868b407089fdbcf630599bf04c2d9dc2afd6c51339d0e3952` were verified. Health changed from 20 to 16.826668 with damage type mob. Five admitted echoes obeyed return delay and measurement limits; no vanilla target was assigned. Isolation blocked two attempted spawns, with no unexpected mobs. The actor is held with NoAI true and 40 health. All 140 closed files were archived under an exclusive lock with source/archive hashes checked, SHA-256 `b97b2f6dc5ed101e3012939d0d8d949c508fe9c282cb2fc26412f87d61e63e68`. Evidence: `build/blind-architect-evidence/base-hunt-20261001/live/run-11/result/`. Repeated last-vibration snapshots cannot establish distinct query counts; the next capture must preserve event provenance. Natural spawning, arbitrary caves and LAN remain untested.

## Owner routine

Completed routine (audit only; do not rerun). Stay still for the first five seconds, until WORK appears. Then do normal base chores: walk between the chest, crafting table and furnace, and mine a few blocks from the iron-ore pile. Keep the low opening as prepared and stay inside until the BA reaches you. React naturally if it gets in: move or defend yourself. EVA, oxygen, a pickaxe, sword, food and building supplies are provided. Damage is ordinary Survival damage.

The view lasts at most 90 seconds. It ends ten seconds after the BA enters, or on either death; the actor is held and Creative returns. At VIEW COMPLETE, give your first description of what you saw/heard, then pause. If you die, describe that too. Capture completion does not mean the hunt succeeded. Do not restart the encounter.

## Operator setup audit — already completed; do not rerun

Read `AGENTS.md` and `lab-command-bridge.md`. Keep the same owning Java 21 lab client. Preserve status, snapshot and scores under `build/blind-architect-evidence/base-hunt-20261001/live/run-11/preflight/`. Expected completed baseline: stage 3/run 10/case 10/timer 480/frames 480/archived 1/inconclusive 0/isolated 10. Original held actor `edc30a8f-7c32-4dd6-bc36-0163dded2407` is near (10.4727526,-44,5.2768230); the participant is Creative.

```sh
python3 tools/lab_client.py status
python3 tools/lab_client.py --world 'Blind Architect Reusable Lab' snapshot
python3 tools/lab_client.py --world 'Blind Architect Reusable Lab' scores blind_vib '#stage' '#run' '#case' '#timer' '#frames' '#last_frame' '#inconclusive' '#archived' '#isolated'
python3 tools/lab_client.py --world 'Blind Architect Reusable Lab' function blind_vibration_lab:behavior_base
```

Preparation must succeed exactly once, after checking the completed trace hash. Verify stage 1/run 11/case 11/timer 0/frames 0/isolated 11, `#base_acquired`, `#base_broken`, `#base_entered`, `#base_close` all -1, `#base_end` 0. The fresh grounded actor is held at (9.5,-44,0.5), with empty memory; the player is at (22.5,-44,0.5). Preserve `blind-lab-isolation/run-11-before.json`. Start separately once while the menu still pauses simulation:

```sh
python3 tools/lab_client.py --world 'Blind Architect Reusable Lab' function blind_vibration_lab:start
```

Verify stage 2/run 11/case 11/timer 0/frames 0, AI enabled, Survival and menu pause still true. The start record is captured before release; inspect the armed snapshot for final NoAI state. The owner unpauses next. Never retry a mutation with an unknown result.

## Evidence and limits

Preserve the first report before reading diagnostics. Copy `start.json`, `trace.jsonl` and `summary.json` from `blind-lab-results/run-11-RECEIVER_UUID/` and verify the variable-length frame count and hash. Preserve isolation, status, scores, client log and loaded actor dump. If already saved/quit, archive the whole save under an exclusive lock and inspect saved scores/actor/player state. Use `#base_end` and `summary.endReason` to distinguish the time limit, entry observation, deaths and an inconclusive capture. Neither a complete trace nor a close distance automatically proves a successful encounter.

This is a constructed cave/base at Y=-44 with a controlled fresh actor outside, a connected stone floor, a real acoustic opening and enabled mining. It combines previously isolated senses with ordinary navigation/breach. No synthetic impulses, current-player target coordinates, forced route, confidence edits or accelerated ticks occur in the human run. The exact native fixture proved acquisition, physical cuts, entry and approach with a stationary protected FakePlayer. The owner performed ordinary movement/mining and reported being found and hit; the captured mob damage verifies the hit. Natural spawn timing, arbitrary caves, fully sealed bases, richer indirect echoes and LAN remain separate coverage.


## Follow-up

The combined view is complete and preserved. The owner requested independent sensing tests next; use [the channel guide](blind-architect-channel-playtests.md). Do not restart this base encounter.

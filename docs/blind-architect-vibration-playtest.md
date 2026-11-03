# Blind Architect: connected-support comparison

This is the first human view of the new structural sensor. It measures whether the same contact impulse reaches the Architect's feet through connected material. The actor is held with NoAI, and the production vibration solver is called directly. Hearing, echolocation, navigation and combat do not participate. The player remains Creative. Normal hunting acceptance follows in a separate view after the individual mechanics are established.

Owning checkout: `/Users/jaroncabral/Projects/minecraft-mod/.worktrees/blind-architect`, branch `feat/blind-architect`. Use its Gradle `runClientLab` and `tools/lab_client.py`. Do not reopen or reset Wool Echo for this comparison.

The original world06 run was inconclusive: all three impulses returned NO_RECEIVER_CONTACT because the held actor was summoned with OnGround false. Its complete closed save and first owner account are preserved in `build/blind-architect-evidence/vibration-20260930/connected-live-failure/`. World07 was never opened. Leave both original saves intact; the repaired comparison uses the fresh pair below. The generated summon payload now initializes the held receiver on the actual stone surface, and the native regression loads that same payload and ticks it through all three measurement times.

World08 has now completed successfully: the owner reported “all green,” and all3 impulses were detected with0 inconclusive samples. Its closed save and first account are preserved under `vibration-20260930/connected-pass/`. Advancement popups still appeared because NeoForge appended `mod_data` above the filter. The repaired world09 explicitly orders `vanilla`, `mod_data`, then `file/blind-architect-lab`; the actual native loader reproduces91 leaked advancement definitions with the old order and zero with the new order, preserving1360 recipes. World09 then completed with3 NO_PATH results,0 detections and0 inconclusive samples; the owner confirmed no advancement popups. Both repaired views are closed and archived. Do not repeat either completed view.

## Comparison worlds

| World | Controlled change |
| --- | --- |
| **Blind Architect 08 Connected Supports** | Completed:3/3 detections, observed all green. Closed and archived. |
| **Blind Architect 09 Disconnected Supports** | Completed:0/3 detections, all NO_PATH, no advancement popups. Closed and archived. One middle beam block was absent. |

The receiver stands at `(9.5,-44,5.5)` and the injected tool contact is the stone block at `(21,-45,5)`. The observer starts on a separate glass deck at `(15.5,-44,-8.5)`, far enough back to see both indicators together. Floating SOURCE and BA SENSOR labels explain the colors; they have no collision geometry. Both platforms are separated from the enclosure floor and walls. The observation deck and floating indicators do not bridge the supports. The same generator produces the native geometry fixture and the playable layout. The native comparison must prove positive reception in the connected layout and a completed NO_PATH result in the disconnected layout.

New saves preselect and lock the default Frozen Dawn difficulty before the first login, so the difficulty picker does not open. Their local datapack filters advancement definitions after NeoForge mod data, preventing both vanilla/mod toasts and chat awards. Correct selected-pack ordering is required; world08 exposed the earlier ordering error. Recipes and other game resources remain available. These are fresh lab settings, not copied player progression; existing saves and ordinary worlds are unchanged.

## Recorded viewing procedure

The pair below has completed. This procedure documents the original view; it is not a request to reopen or replay either save.

Open **Blind Architect 09 Disconnected Supports** in Singleplayer. Stay on the glass observation deck throughout the view. Wait for Ready, then pause and tell the operator you are ready. The operator verifies world/session/stage and starts the guarded function once. Alternatively press T and click the explicit **[START TEST]** message, or enter `/function blind_vibration_disconnected:start` yourself while Creative. Do not do both. Unpause and watch for24seconds without altering blocks or the actor.

A three-second countdown appears before each strike. Three identical normalized-strength-12 impulses are injected at five, eleven and seventeen seconds. The source indicator above the far platform flashes white and plays a presentation-only anvil sound. The receiver indicator above the Architect stays black for no reception, turns green for reception, or orange for an inconclusive measurement. Each indication lasts two seconds. Each pulse also prints its number and a plain reception result both in chat and above the hotbar: “BA received vibration,” “No vibration reached BA,” or “Test inconclusive.” A VIEW COMPLETE title marks the end. Neither marker touches the support graph. The actor intentionally stays motionless and does not click or hunt in this sensor view.

Describe the three source flashes and receiver indications before the operator reads diagnostic measurements. An absent source flash is a fixture/injection problem, not proof of disconnection. An orange indication or missing sample makes the run inconclusive. No combat, audio realism or fear acceptance is inferred from these indicators.

After the view ends, describe the three results and whether advancement popups appeared. Then Save and Quit so the operator can preserve the closed result and assess the pair. Completed worlds cannot restart. Never change a scoreboard stage or reset a save to obtain a nicer result.

## Operator controls and evidence

The owner authorized fixed replay command injection. Read current status and the exact world's snapshot and scores before Start. Expected Ready: stage1, Creative participant tagged `blind_lab_player`, exactly one living Blind actor tagged `blind_lab_actor`, NoAI true and OnGround true at the stated receiver position. Confirm the actor is supported. Start retains Creative/NoAI and runs the normal-speed timer; no tick sprint is allowed for this visible view.

```sh
python3 tools/lab_client.py status
python3 tools/lab_client.py --world 'Blind Architect 09 Disconnected Supports' snapshot
python3 tools/lab_client.py --world 'Blind Architect 09 Disconnected Supports' scores blind_lab '#stage' '#timer'
python3 tools/lab_client.py --world 'Blind Architect 09 Disconnected Supports' function blind_vibration_disconnected:start
```

Read-only result collection, after the first account:

```sh
python3 tools/lab_client.py --world 'Blind Architect 09 Disconnected Supports' scores blind_lab '#stage' '#timer' '#samples' '#detected' '#inconclusive' '#outcome' '#strength_milli' '#work' '#last_impulse'
```

These commands document the completed disconnected world09; do not invoke Start again. Optional early Finish is guarded at stage2. The bridge exposes only Start and Finish for these worlds; impulses run at their fixed timer values. The development command also checks exact world, one Creative tagged player, stage, timer, duplicate consumption, actor identity conditions/position and actual source material. No arbitrary impulse position or world-reset command was added.

Successful completion is stage3 with three samples and zero inconclusive samples. The paired mechanical expectation is three detections in08 and zero detections in09. `#outcome` is1 for reception,0 for completed NO_PATH,2 for inconclusive; `#strength_milli` is normalized strength times1000. These values are model diagnostics, not real accelerometer units.

Every impulse logs `[BlindVibrationBench]` JSON with world, actor UUID, game time, replay timer, measured execution nanoseconds, contacts, selected path, normalized strength, loss and work. Preserve the raw owner account before interpretation, bridge replies, client log and closed save under `build/blind-architect-evidence/vibration-20260930/`. The original Wool Echo archive is already retained there as `wool-echo-closed.zip` with a verified file/hash manifest.

## Next comparisons

After this pair, stage source cushioning and receiver contact separately. Then exercise real walking, crouching, flight/landing and mining, with channel attribution checked in the trace. Source cushioning is now prepared in the existing world09 save, renamed Blind Architect Reusable Lab after a complete closed backup. Follow [the reusable lab protocol](blind-architect-reusable-lab.md). Its new objective and namespace retain the historical comparison scores; each result is preserved before a guarded scene change. Human cushioning acceptance has not started. A later ordinary Survival encounter evaluates the listening scan, reacquisition, navigation and counterplay together. Echo energy/directivity/localization and hearing/masking remain later implementation stages.

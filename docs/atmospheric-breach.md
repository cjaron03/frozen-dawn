# Atmospheric breach checkpoint

October 9, 2026. Owning checkout: `/Users/jaroncabral/.codex/worktrees/rimewood/minecraft-mod`, branch `feat/atmospheric-breach`, HEAD `8cb8a1e47a6e57f63a64e72e482c549eb144efce`. Breach changes are recorded in the split commit history below. Root checkout Scribe WIP and the future Rimewood research remain separate work.

## Handbook and code cross-reference

| Contract | Before this patch | Current implementation |
| --- | --- | --- |
| Sealed room | Sky visibility rejected transparent roofs | Full glass seals geometrically; closed doors/trapdoors seal, open ones pass air |
| Oxygen sources | Core/Blast Pit radius could protect through a breach | In vacuum, source must connect to sealed, recovered room air; heat remains unchanged |
| Room recovery | Replacing a wall could immediately restore air | Depleted cells persist; connected oxygen supply needs five loaded sealed seconds |
| Breach feedback | Indexed flames could extinguish later without an event | One connected-volume extinction, venting particles, whoosh, beep, red HUD and ORSA speech |
| Torches | Extinguished block identifiers | Canonical `spent_torch`/`spent_wall_torch`; legacy registry and saved-palette aliases retained |
| Suffocation | Existing ten-second buildup | Same buildup; breach adds no direct damage or automatic suit puncture |

Updated ORSA Field Manual entries: overview, hypothermia, phase timeline, projected timeline, EVA suit, Geothermal Core, vacuum combustion, and new Heating / Atmospheric Breaches entry. Full collision blocks seal; partial blocks and leaves pass air. A double slab has full collision geometry and can seal; a single slab cannot.

## Behavior and limits

The Overworld vacuum boundary remains apocalypse progress >= 0.85. A known pressurized room opening to exterior vacuum vents once. Airflow lasts two seconds, fades, reaches at most twelve blocks, requires a clear path, and affects living entities and dropped items within the prior room. Grounded crouching reduces force to one quarter. Creative and Spectator are not pulled. There is no permanent pin.

All ordinary flames in the connected volume extinguish together. Torches stay in place, preserve wall facing, drop ordinary torch items, and can be relit with flint and steel after air returns. Lanterns remain in place too. Soul sources retain the existing supernatural exception. Furnaces preserve unspent fuel.

Without usable EVA life support, ordinary suffocation starts damaging at ten seconds (two hearts per second). Earlier lightheadedness, nausea and slowness remain. Cold is independent. A full EVA rig with a usable tank, or active Emergency EVA reserve, continues providing air. Tank intake and ambient protection cannot bypass the breach.

This is a bounded sealed/depleted state model, not gas mass or pressure simulation. Initially unknown sealed spaces are assumed to retain trapped air for save compatibility; confirmed vented cells persist across reloads. Geometry searches are bounded to 12,000 cells and loaded chunks. Unknown/oversized/unloaded geometry gives no breathable-air guarantee and does not produce a false breach burst. No chunks are forced loaded. Refill interruptions restart the five-second timer; restart also restarts an unfinished timer. Metabolic room oxygen consumption is not modeled.

## Verification and artifacts

Java 21 `./gradlew build gameTestGate --console=plain` passed: 665 unit tests and 276 native GameTests; all 271 required gate cases and reports verified. Eight new native cases cover geometry, source connection, persisted depletion/recovery, real item airflow, actual unsuited damage timing, suited tank consumption, volume extinction/legacy palettes/relighting, and native parsing of all nine fixture functions.

The first attempt passed the native tests but failed the report completeness gate because the early custom batch ran before reporter installation. Its log/report remain in `build/atmospheric-breach-evidence/attempt-1/`. Reporter installation was fixed without weakening required cases. Passing log and XML: `build/atmospheric-breach-evidence/final/`; native artifact ID `d6d78518-6576-404d-a84e-a6e3e592f2f9`.

Release jar SHA-256: `fcbdb133c30675e52e7d2bf2e77eecc97ef955dee42aa86fa2993ececf4938a1`. The smoke profile's Frozen Dawn jar matches; dependencies were preserved. Speech uses the existing Piper Amy ORSA voice and processing, documented in the generated-voice manifest and notices. The whoosh is original procedural audio; reproducible source and ledger are under `tools/audio_sources/atmospheric_breach/`.

## Current visual handoff

Fresh save **Atmospheric Breach Check**, prepared by `tools/prepare_atmospheric_breach_playtest.py`. The old **Vacuum Flame Check** was closed safely, then copied to `build/atmospheric-breach-evidence/pre-restart/`; all sixty file hashes match. No saved replay was reset.

Launched with the owning Java 21 Gradle checkout: `./gradlew runClientLab --console=plain '-PfdLabWorld=Atmospheric Breach Check'`. Agent terminal session 47469, log `/private/tmp/atmospheric-breach-client.log`. Bridge session `d6049abd-b6b8-4657-a5e0-d68f0bc28da1` confirmed exact world, setup `abcheck #built=1`, and player Dev at (0.5,65,0.5). Client reported READY. The initial chamber is sealed, with glass roof, Core, ordinary lighting, furnace and a soul torch. Late Phase 6 is paused independently of ordinary game ticks. No breach was triggered by the agent.

Owner test, using chat controls:

1. Click **Survival with full EVA + O2**; resume gameplay. Drop an item with Q inside the room.
2. Click **Open the east wall — BREACH**. Check short pull/item motion, outward venting, one extinction cue, warning beep, exact speech and red hotbar text. Crouch during another recovered-room attempt to compare bracing.
3. Click **Repair opening — five-second oxygen recovery**; return to gameplay for five loaded seconds. Relight standing/wall torches and other ordinary lights with flint and steel. Torches should remain in their original positions throughout.
4. For the separate unsuited check, use the **Survival without EVA** control in a recovered room, then breach. Expect buildup rather than immediate suffocation damage. Use **Creative safety** promptly if needed; cold can cause separate damage.

Additional breaks while already vented must not replay the burst. A new breach after actual recovery should replay the warning. Opening menus pauses ordinary simulation, including airflow and refill.

Owner visual/audio acceptance and multiplayer acceptance remain pending. Do not automatically retry bridge mutations, trigger a breach, run setup again, or erase this save to collect diagnostics. Status/snapshot/score reads are authorized; visible controls belong to the owner.

## Worktree split — October 9, 2026

Rimewood development now belongs to `/Users/jaroncabral/.codex/worktrees/rimewood-development/minecraft-mod`, branch `feat/rimewood`, based on committed vacuum-flame fix `8cb8a1e`. The approved texture package is copied under `output/rimewood-textures/v1/`; every copied file was SHA-256 verified against the original. Research was copied exactly before adding this ownership note. Originals remain preserved.

Atmospheric breach work remains in `/Users/jaroncabral/.codex/worktrees/rimewood/minecraft-mod` on branch `feat/atmospheric-breach`, including its uncommitted implementation, running Gradle client, saved worlds and verification evidence. Its historical directory name is retained to keep the active client stable. Rimewood has no breach WIP. Integrate the finished breach commit later if required.

## Owner feedback — October 9, 2026

The owner reported: “wow! works good.” Preserve this as positive visual feedback for the breach presentation; individual recovery, relighting, unsuited timing and multiplayer checks were not separately reported. The owner proposed a sealed-breach notifier and asked how room pressurization works. Recommended presentation: distinguish BREACH SEALED / OXYGEN RECOVERY IN PROGRESS from BREATHABLE AIR RESTORED, with a waiting-for-oxygen state if no connected supply exists. These recovery notices are proposed, not implemented.

## Sealed and recovery notices — October 9, 2026

Implemented the owner's requested follow-up. Depleted rooms now announce BREACH SEALED — OXYGEN SUPPLY REQUIRED when no connected supply exists, BREACH SEALED — RESTORING AIR once refill begins, and BREATHABLE AIR RESTORED after the real five-second refill completes. Messages go only to non-spectator occupants of that connected volume and are deduplicated per room transition. Initial ordinary breathable rooms do not announce fictitious repairs. Restored feedback has a quiet confirmation tone. Recovery replaces the red breach text and ORSA panel and cancels stale alarm speech. The notices do not promise warmth or repaired EVA punctures. No extra TTS performances were added.

The Core manual page now leads with “Heat reaches through walls. Oxygen needs containment.” The breach construction page correctly distinguishes single slabs from double slabs and explains the new notices. [The airlock design](airlock-design.md) records the owner's custom parts and Core-fed reserve plus backup-canister decision; those custom parts remain a separate planned feature.

Java 21 build and full gate passed: 665 unit tests, 0 failures, 0 errors; 278 native tests and all 273 required reports verified. New real-packet fixture checks no-supply/restoring/restored ordering, no polling spam, no premature success and room-local recipients. A two-door geometry fixture verifies chamber evacuation, preserved base air, isolated recovery, safe inner reopening after recovery and whole-base evacuation if both doors open. The first attempt's shared-state fixtures interfered with each other's global cache resets; separate batches resolve this. Both attempts remain under `build/breach-recovery-evidence/`.

Final jar SHA-256: `4629326201ba77d21f325e3d8af2f1ae5fcff36f2269ffa015592ff64b11d3f6`; smoke profile jar matches and dependencies remain preserved. Native report ID: `84e645fd-548b-4ee6-92b1-0e8c4a7d64f9`. Before restart the owner confirmed the title screen; the closed Atmospheric Breach Check save was copied under `build/breach-recovery-evidence/pre-restart/` with all 63 file hashes matching. The world is preserved in place; no setup/reset or automatic breach is authorized by this handoff.

Restart the same save through Java 21 `./gradlew runClientLab --console=plain '-PfdLabWorld=Atmospheric Breach Check'`. On reopening, `/function atmospheric_breach_check:controls` restores the visible chat controls without resetting the scene. Repair if the previous opening is still present, let existing recovery complete, then trigger a new breach with full EVA O2 and repair it to visually check both notices and confirmation tone.

Updated client is live in the preserved Atmospheric Breach Check save. Gradle session 13519; log `/private/tmp/breach-recovery-client.log`; bridge session `909b72d3-d3ca-4f4e-94c4-406a5e5db6c2`. The exact world was verified loaded at game tick 3991, Dev near (-1.35,65,0.22). No setup or breach was run by the agent. Recovery-notice visual acceptance remains pending.

## Helmet HUD and loss alarm follow-up — October 9, 2026

Implemented the owner's three presentation requests. A worn EVA helmet, Emergency EVA helmet, or full visor-mated EVA rig receives atmospheric messages only in the ORSA suit dialogue HUD. Without a compatible helmet, messages appear only in the action bar above the hotbar. Production loss/recovery messages never enter chat history. Equipment changes during an active notice switch its destination; an atmospheric HUD panel cannot keep rendering after helmet removal. Other dialogue is not cleared by the atmospheric-only cleanup.

Atmospheric loss uses red suit-panel text/accent or red action-bar text. A dedicated original three-pulse alarm replaces the quiet telemetry beep only for breaches. Duration: 0.920091 seconds; measured mean -11.2 dBFS and peak -5.4 dBFS versus the normal pulse's -20.3 dBFS mean. Voice starts after the alarm rather than over it. Existing oxygen telemetry audio is unchanged. Recovery has its separate quiet confirmation tone. The manual documents the routing and louder loss alarm; original synthesis source/ledger and the 313-entry shipped inventory accompany it.

Required Java 21 build and gate passed: 665 unit tests, 0 failures, 0 errors, 278 native GameTests, all 273 required reports verified. Native report: `e1962d8e-fe9a-4844-a268-e9bbaf44f05a`. Final jar SHA-256: `319b22c143078f486940a4ed84ba8fd9372e7a2182e4510822add406a44ce410`; smoke profile jar matches. Pre-existing subscriber/Gradle deprecation warnings remain. The regression gate does not prove client HUD appearance or perceived alarm loudness.

The owner confirmed the title screen before the restart. Closed save backup under `build/breach-hud-evidence/pre-restart/` has all 64 file hashes matching. Reopen the same save through Gradle, show controls with `/function atmospheric_breach_check:controls`, and compare a recovered-room breach wearing EVA against one without its helmet in Creative. Check one message destination, red loss text and stronger three-pulse beeping. Do not run setup/reset or automatically trigger either visible breach. HUD/alarm visual acceptance remains pending.

HUD/alarm client reopened successfully through Gradle session 79473, log `/private/tmp/breach-hud-client.log`. Bridge session `8917c440-28c5-4369-a481-27b21918c979` confirmed the preserved Atmospheric Breach Check world at game tick 8621 with Dev near (-0.58,65,-0.24). No scene reset or visible breach was triggered by the agent.

## Accepted presentation and next issue — October 9, 2026

The owner reported “perfect” for the helmet/action-bar routing and stronger red loss alarm, then requested split commits. The owner also reported flickering between RESTORING AIR and suffocation text above the hotbar when unsuited. Source confirms competing client atmospheric and server suffocation action-bar writers; the suffocation handler additionally emits multiple crossed thresholds in one tick. This arbitration issue is not fixed by the current commits. Recommended follow-up: one action-bar owner, a single highest-severity suffocation state, and a stable recovery-plus-danger presentation rather than hiding ongoing suffocation damage. Damage and effects remain independent.

The final recovery caption is now ATMOSPHERE RESTORED, as requested. The translation key remains unchanged and the ORSA manual uses the new caption. This is a wording change; it does not broaden the room-state simulation.

## Split commit handoff — October 9, 2026

Spent-torch compatibility: `8013331ca6e94cd33d50ce3982e018dba23dea00`. Sealed-room breach, recovery, HUD, audio, ORSA guidance and native acceptance fixtures: `44974c1dd3c1a164c63ddb13e994838d6a639b40`. The playtest preparer/handoff and planned airlock design are committed separately afterward. Rimewood research is excluded and preserved. No pushes are authorized yet.

The requested ATMOSPHERE RESTORED caption passed a full build; its release jar SHA-256 is `ebd23553a4ece6b11109398a18af1609c0bb406fded8441755e2558810ccc873` and the smoke profile jar matches. The existing Gradle client needs F3+T to reload this wording-only resource update. This does not change the earlier native gate result or fix action-bar arbitration. The owner accepted the prior helmet/alarm presentation; the newly reported unsuited flicker remains the next code fix.

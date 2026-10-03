# Emergency EVA death recovery patch

Owning checkout: `.worktrees/emergency-eva`, branch `fix/emergency-eva-respawn`, baseline `0885e22812f8723a6f55cdf52da87b62a18c4485` from freshly fetched `origin/feat/maeve-director`.

Late Phase 6 death previously dropped the survival suit and returned the player to a base that could be lethally cold even with a lit heater. The emergency kit automatically supplies missing EVA pieces on death respawn during the existing vacuum stage. A retained ordinary sealed rig with usable tank oxygen takes priority. Replaced non-emergency armor is preserved in inventory or dropped if inventory is full. End returns, login, earlier phases, Creative and Spectator do not grant a kit.

The kit uses EVA textures and leather-level armor protection. One persistent, player-bound issue ID controls all its pieces and its ten-minute oxygen/thermal service life. Any worn issued piece drains that shared reserve, including indoors; removing all pieces pauses it. Saving, relogging, dimensional travel, partial replacement and re-equipping cannot refill it. A new death invalidates the former issue, including pieces stored in chests or passed to someone else. Emergency pieces are removed from death drops and retained inventory; original equipment still follows ordinary death rules.

A complete active EVA rig with emergency pieces provides climate control and an internal air reserve. This reserve uses no refillable tank, efficiency module, patch or cartridge. Visual deterioration is separate from ordinary suit punctures, so cracks do not trigger puncture venting. Physical armor can break in combat. At reserve zero, emergency pieces cease to qualify as sealed EVA, normal cold/vacuum hazards resume, and the header reads EMERGENCY EVA DEPLETED. Equipping a complete ordinary rig restores its normal behavior. Emergency equipment grants no armor crafting advancements, recipes, repair ingredient or salvage route.

The existing EVA oxygen HUD renders emergency telemetry through the same panel, ORSA badge, borders, spacing, fonts, reserve percentage, time and warning colors. Its header is EMERGENCY EVA ACTIVE and its second label is Reserve oxygen. At zero the header changes to EMERGENCY EVA DEPLETED and the panel uses the normal vacuum color. Cracks remain at the screen edges and strengthen as the reserve drains. An activation beep plays once per issue per client session. Quiet seal creaks begin below five minutes. Beeps repeat every ten seconds below two minutes, every three seconds below one minute and every second below fifteen seconds. Existing overlay settings and hidden-HUD controls apply.

## Human recovery preview

Launch Java 21 `./gradlew runClientLab --console=plain` from the owning checkout, inside the agent terminal. Open **Emergency EVA Recovery - Phase 6**. This is fresh default terrain generated from default-world metadata, with no copied terrain, player inventory, apocalypse attachments or replay history. A guarded, one-time development setup advances the apocalypse to day 104 of 100, keeps natural mob spawning, weather and daylight active, and locates dry terrain for a small stone base and a separate outdoor respawn about 300 blocks away. Difficulty is Normal. This validates recovery in a newly advanced default world; it does not reproduce 104 days of accumulated play or the original rehearsal save.

The helper loads the room chunks before construction and verifies the floor, roof, heater and chest before teleporting. Preparation stays Creative and stops on failure. It never automatically retries a failed preparation or repeats a death. The setup and reserve shortcuts are excluded from the release jar.

1. Wait for READY and the base/respawn coordinates in chat. You start at the base wearing ordinary EVA, with a usable Mk III tank, bread and an iron sword. The heater is lit; the chest holds food and fuel. Preparation occurs once when the new world is first opened.
2. Click **DIE AND WALK BACK**, or run `/function emergency_eva:start`. This enters Survival and causes one real death. Ordinary gear drops at the base under normal death rules.
3. Click Respawn. Verify the emergency gear, shared EVA HUD, initial beep and outdoor survival. Walk back to the base coordinates in chat and retrieve your original equipment before its ordinary item despawn timer expires. Natural mobs and mod spawners can interrupt the trip.
4. Save and Quit/reopen or remove/re-equip the emergency kit to check that reserve does not refill. Replace it with recovered ordinary EVA and the O2 tank to check normal HUD and life support handover.
5. `/function emergency_eva:controls` provides optional clicks to shorten the current reserve to two minutes, one minute or fifteen seconds. Do this after recovering the original gear if you want to assess cracks, alarms and expiry.
6. At zero, expect ordinary environmental damage. A subsequent death should supply a fresh kit on Respawn. The emergency kit adds no protection against mobs camping the respawn position.

The earlier **Emergency EVA Respawn Lab** exposed a fixture error: its distant room commands ran before chunks loaded, leaving a spawn high above missing blocks. The failed save and client log are preserved in `build/emergency-eva-evidence/fall-loop/`. The new default-terrain preview replaces that floating-room handoff.

Evidence: `build/emergency-eva-evidence/`, `build/gametest/report.xml`, `build/reports/tests/test/`, and `run-lab/logs/latest.log`. Headless checks cover production respawn/damage paths and persistence. Visual/audio acceptance and multiplayer remain human checks.

## Verification checkpoint

`./gradlew architectVerify --console=plain` passed: 646 unit tests, 224 native GameTests, and all 219 required native cases/reports. All five emergency EVA tests passed, including a lit heater in a genuinely sealed, lethally cold room, real atmospheric damage before/after reserve expiry, persistence, retained equipment, old-issue rejection and death drops. The smoke-test mod jar was replaced with the verified build; SHA-256: `45e4a6c8b8a2ee88b9bdeb00cae88b16cdb4d3b3fc92ed424ba7a39e506a65ea`. The human HUD/audio pass remains pending.

Recovery follow-up: `./gradlew build compileLabBridgeJava architectVerify --console=plain` passed again after the shared HUD and terrain setup changes. The client was restarted with the new save selected through Quick Play. Initial bridge status reports no loaded world; the first live READY/floor confirmation and the owner recovery run remain pending. The branch stays local while the owner tests.

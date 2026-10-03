# Emergency EVA death recovery patch

Owning checkout: `.worktrees/emergency-eva`, branch `fix/emergency-eva-respawn`, baseline `0885e22812f8723a6f55cdf52da87b62a18c4485` from freshly fetched `origin/feat/maeve-director`.

Late Phase 6 death previously dropped the survival suit and returned the player to a base that could be lethally cold even with a lit heater. The emergency kit automatically supplies missing EVA pieces on death respawn during the existing vacuum stage. A retained ordinary sealed rig with usable tank oxygen takes priority. Replaced non-emergency armor is preserved in inventory or dropped if inventory is full. End returns, login, earlier phases, Creative and Spectator do not grant a kit.

The kit uses EVA textures and leather-level armor protection. One persistent, player-bound issue ID controls all its pieces and its ten-minute oxygen/thermal service life. Any worn issued piece drains that shared reserve, including indoors; removing all pieces pauses it. Saving, relogging, dimensional travel, partial replacement and re-equipping cannot refill it. A new death invalidates the former issue, including pieces stored in chests or passed to someone else. Emergency pieces are removed from death drops and retained inventory; original equipment still follows ordinary death rules.

A complete active EVA rig with emergency pieces provides climate control and an internal air reserve. This reserve uses no refillable tank, efficiency module, patch or cartridge. Visual deterioration is separate from ordinary suit punctures, so cracks do not trigger puncture venting. Physical armor can break in combat. At reserve zero, emergency pieces cease to qualify as sealed EVA, normal cold/vacuum hazards resume, and the header reads EMERGENCY EVA DEPLETED. Equipping a complete ordinary rig restores its normal behavior. Emergency equipment grants no armor crafting advancements, recipes, repair ingredient or salvage route.

The HUD has two lines: EMERGENCY EVA ACTIVE and Reserve oxygen: M:SS. Cracks remain at the screen edges and strengthen as the reserve drains. An activation beep plays once per issue per client session. Quiet seal creaks begin below five minutes. Beeps repeat every ten seconds below two minutes, every three seconds below one minute and every second below fifteen seconds. Text turns red below one minute; the final fifteen seconds pulse the panel edge. Existing overlay settings and hidden-HUD controls apply.

## Human preview

Launch Java 21 `./gradlew runClientLab --console=plain` from the owning checkout, inside the agent terminal. The save is `run-lab/saves/Emergency EVA Respawn Lab`, copied from the closed Blind Architect 01 Appearance save; the active Blind Architect world is preserved separately. This preview supplies a new sealed stone room, an iron door, a fuelled heater, and ordinary recovery gear in a chest. It sets day 104 of 100 and disables vanilla natural mob spawning. It does not reproduce the original rehearsal map or validate spawn camping.

1. Open **Emergency EVA Respawn Lab** and run `/function emergency_eva:setup`. Setup is guarded to this development world and refuses to run again after stage 1.
2. Click START in chat, or run `/function emergency_eva:start`. This switches to Survival and causes a real death with keepInventory false. Click Respawn.
3. Check the four issued pieces, HUD, initial beep, and survival beside the lit heater. Open the iron door to check outdoor vacuum survival.
4. Remove the kit briefly and re-equip it; its reserve must not refill. Save and Quit, then reopen, to check persistence. Retrieve ordinary EVA and its O2 tank from the chest to check handover.
5. `/function emergency_eva:controls` provides clicks to shorten the current reserve to two minutes, one minute, or fifteen seconds. These lab-only controls only shorten an existing issue. They are excluded from the release jar.
6. Observe crack strength and beep cadence. At zero, expect ordinary environmental damage. A subsequent death should supply a fresh kit on Respawn.

Evidence: `build/emergency-eva-evidence/`, `build/gametest/report.xml`, `build/reports/tests/test/`, and `run-lab/logs/latest.log`. Headless verification checks the production respawn/damage paths and persistence rules. HUD, audio perception and real multiplayer acceptance remain human checks. The emergency suit addresses environmental death loops; this patch adds no protection against mobs camping the respawn position.

## Verification checkpoint

`./gradlew architectVerify --console=plain` passed: 646 unit tests, 224 native GameTests, and all 219 required native cases/reports. All five emergency EVA tests passed, including a lit heater in a genuinely sealed, lethally cold room, real atmospheric damage before/after reserve expiry, persistence, retained equipment, old-issue rejection and death drops. The smoke-test mod jar was replaced with the verified build; SHA-256: `26ef41deb5c184851efac0e7c9270de705d93a1298385354b416cabc214e7612`. The human HUD/audio pass remains pending.

# Player-built airlock visual check

Owning checkout: `/Users/jaroncabral/.codex/worktrees/rimewood/minecraft-mod`, branch `feat/atmospheric-breach`, base `6c03101`. The folder name is historical; Rimewood development remains separate. Launch with Java 21 and `./gradlew runClientLab --console=plain`.

## Fixture

Open **Airlock Check** from Singleplayer. The disposable world is prepared by `tools/prepare_airlock_playtest.py`, from fresh seed metadata only: no copied chunks, inventory or mod history. Its setup executes once. It starts Creative at late Phase 6; the base is west, 27-cell chamber east, and exterior vacuum beyond. Glass roof/walls, a Geothermal Core, two custom doors, the in-chamber controller, two side panels and an emergency valve are already placed. Controls are clickable chat messages; `/function airlock_check:controls` reprints them without resetting anything.

The earlier Atmospheric Breach Check save is preserved with a complete matching-file backup under `build/airlock-evidence/pre-client/`. No bridge function permission was widened and no existing replay was reset.

## Owner visual sequence

1. Check all three approved designs, both door halves, item appearance and controller lamp states. Open ORSA Field Manual -> Heating -> Player-Built Airlocks. Use its native crafting pages; verify JEI R/+ transfer and shift-click output at the base crafting table.
2. Enter the chamber through the inner door and close it. Green chamber: outer door refuses with hiss and Pressure differential. Click the panel: amber/pump sound for four seconds, then red. Inner door refuses; outer door opens into vacuum. Close it behind you.
3. Use an O2 canister on a panel if reserve is short. Click to pressurize: four seconds, green, ATMOSPHERE RESTORED. Inner opens; outer refuses. Crouch-click each panel to verify the same reserve and chamber quantities.
4. Exact 27-cell requirement is 2,700 O2. A normal depressurization recovers 2,430 and loses 270 when reserve has room. A full reserve vents overflow; the status reports actual loss. Disable the Core with the clickable control to test finite backup canister operation.
5. Equip EVA before Survival exposure. Vent with inner closed: chamber torches stay spent and soul torch stays lit; base stays breathable. Refill, then open inner and operate valve: connected base vents too. Restore/close the separation before recovery.
6. Break and repair the chamber wall using its controls. Repair cannot manufacture air. Cycle to refill. Save/quit during a cycle and rejoin: partial air, reserve and progress should survive, advancing only while loaded. Review before altering the fixture further.

The chamber stores 100 units per passable cell, reserve capacity 6,400, Core refill 40 units/second, and cycles take 80 ticks. These are first-playtest values, not balance acceptance. Core oxygen remains an unlimited producer; there is no new power grid. Breaking the final controller or resizing the chamber discards reserve. Optional panels share one authority.

## Evidence and limits

The first native run completed 282 tests, with one fixture failure: its redstone source was left as a solid block directly in the outer doorway before an equal-vacuum opening assertion. The other three new airlock cases passed. The corrected test removes that obstruction after checking redstone refusal. Attempt-one report/log are preserved under `build/airlock-evidence/attempt-1/`.

Final build/native result is recorded in `build/airlock-evidence/final/verification.json` once complete. Unit tests cover every supported chamber size, 90/10 conservation, reserve overflow, per-tick reload, interruption and malformed snapshots. Native tests cover real pressure doors/redstone/direct-state guards, chamber recognition and incomplete/oversize rejection, exact canister debit, shared panels, SavedData codec, Core isolation, managed chamber recovery, real loaded pump timing, emergency connected-volume venting and spent/soul flames. Native parsing checks the actual test-world functions.

Owner visual acceptance and multiplayer acceptance are pending. Actual unload/reload operation, JEI transfer and in-game art/sound judgment remain owner checks; mathematical snapshots and headless block-state assertions do not establish those observations.

## Verified build — October 9, 2026

Java 21 `./gradlew build gameTestGate --console=plain` passed: 675 JUnit tests, 284 native GameTests and all 279 required cases/reports. Source fingerprint `f100718c9e829fe37962ed27bd2a6f46f92a140d8e2f646e85de396ccea7c2c6`; release jar SHA-256 `d726cf56246dfbb66bc9eb92725c443bb284bd1ef9a00734845e3f46106f4dbb`. Only the Frozen Dawn smoke jar was installed and hashes matched at installation. The owner's Gradle client was launched in session 78934; log `/private/tmp/airlock-client.log`. Open Airlock Check; owner visual and multiplayer acceptance remain pending.

## Owner art acceptance and feedback revision — October 9, 2026

The owner reported the installed airlock designs look great and requested small controller animations plus short, silent suit HUD status. Added a client-rendered two-pixel LED: red slow blink, amber fast blink, green ready heartbeat. No server block toggles or extra raster assets are required. Routine device packets route through the existing oxygen-priority display: helmet HUD when equipped, otherwise above the hotbar. They never schedule TTS. Progress updates once per loaded second; status strings are brief and immediately readable. Actual breach alarms retain their existing event behavior and priority. Visual acceptance of this revision remains pending.

Silent feedback revision: Java 21 `build gameTestGate` passed 677 JUnit tests, 285 native GameTests and all 280 required cases/reports. Source fingerprint `26fd7e5337feff09173033de478d6b209bb9652d8252b8bf6fcc2835759a5775`; jar SHA-256 `16839d9b5812e786110c8312f6362d010c4dc1e12b6789aa2ec587e733ca2a98`. Evidence: `build/airlock-feedback-evidence/final/verification.json`. The closed Airlock Check replay was backed up with all 65 files matching before client replacement. New client log: `/private/tmp/airlock-feedback-client.log`. Reopen the existing world; no reset is needed.

## Owner feedback acceptance — October 9, 2026

The owner reported “perfect” after the controller blink and silent HUD revision, then requested a local commit. This records visual acceptance of that revision. Multiplayer, detailed unload/reload and JEI acceptance remain separate; the headless results remain 677 JUnit tests, 285 native GameTests and all 280 required cases/reports. Active Gradle client session: 38240; log `/private/tmp/airlock-feedback-client.log`.

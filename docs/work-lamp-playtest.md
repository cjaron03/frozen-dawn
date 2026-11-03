# Work Lamp owner playtest

## Delivery review (October 18)

The owner accepted the Work Lamp and Battery Lantern replays and authorized review, commit and a PR into `feat/maeve-director`. The lighting branch is based on current Maeve Director `20ac3635339922fc47106d581806a8959cec5264`, including Uplink, Plumb Line and nonblocking power-link checks. Work Lamp monitor kind 10 preserves Uplink kind 8 and Plumb Line kind 9. Both accepted playtest worlds remain preserved; the existing lab client requires a restart to load the updated source.

Review found an identical recipe pattern for the two lights. The Lantern now puts copper between the glass and redstone beneath it; Work Lamp retains redstone between the glass and copper beneath it. Material quantities and output counts are unchanged. A required native test resolves both real crafting grids to their intended output and confirms the crafted Lantern has no free cell.

The combined regression run exposed a rejected link in the existing real-tick Induction Furnace test. That test now owns a small glass enclosure instead of relying on surrounding batch geometry, and link assertions report the rejection result. Furnace production behavior is unchanged. The failed disposable world, report and log are preserved before rerunning the gates. Final verification results and PR status are recorded in the PR and local `build/lighting-delivery-evidence` bundle. Earlier entries below are chronological replay evidence and may describe a superseded handoff state. Actual multiplayer visual acceptance remains unclaimed. Floodlight, Headlamp and Chemlights remain later slices.

Owner checkout: `/Users/jaroncabral/Projects/minecraft-mod/.worktrees/power-lighting`, branch `feat/work-lamp-floodlight`, based on Maeve Director `68d8684cb0fed02e008b63ffea86a14df921a01a`. Scope is the Work Lamp slice. Owner visual acceptance comes before the Floodlight slice.

The Work Lamp attaches to four wall faces and ceilings, emits light 12 at 50 W, and toggles with an empty main hand. Binder use in either hand selects/links or Shift-clears without toggling. The lamp has no fuel inventory, oxygen consumption, room heat or machinery sound. It uses ordinary light shedding priority, works with isolated Cell Docks and activates existing emergency backup when shed. Removing support drops it and clears its links.

## Dedicated world

The world lives at `run-lab/saves/Work Lamp Check` in the owning checkout. Only generator metadata was read from the closed main-checkout Flat template; gameplay files were not copied. Preparation refuses an existing destination. Creative / Default difficulty is locked, the late-vacuum timeline is paused at 2,592,000 ticks, mob spawning and day/weather changes are disabled, and this world's pack filters advancements after `mod_data`. Existing worlds are preserved.

Three identical opaque rooms contain a grid wall/ceiling pair, the legacy Panel Light reference, and an isolated Cell Dock lamp. The dock starts with 500 J; its lamp starts OFF to preserve that charge. The remote Core supplies 200 W and does not reach the rooms with its oxygen range. The mounting board has a column and overhead plate, spare lamps/docks/cells, plus crafting supplies. No night vision is applied. Chat links move between stations without breaking seals; there is no reset or refill command.

Prepare once with Java 21 from the owning checkout:

```sh
./gradlew prepareWorkLampPlaytest -PfdWorkLampTemplate=/Users/jaroncabral/Projects/minecraft-mod/run-lab/saves/Flat/level.dat --console=plain
```

Launch/reopen the existing world:

```sh
./gradlew runClientLab -PfdLabWorld='Work Lamp Check' --console=plain
```

## Owner replay

1. Start in the grid room. Check the wall model and brightness; empty-hand toggle off/on, then turn on the ceiling lamp. Compare with the Panel reference using chat.
2. Use the Binder on a lamp and confirm it selects rather than toggles. Shift-clear and rebind to test power loss. The front wall feedthrough is the wall lamp's original endpoint; the ceiling lamp links to the wall lamp.
3. At the mounting board, place on all four column sides and under the overhead plate, then remove support. Bind a spare dock and cell if desired. Check the ordinary recipe at the table.
4. In the dock room, turn on the wall lamp. The 500 J cell should last about ten seconds. Swap the full cell after depletion, then switch off and confirm charge stops falling.
5. Set the grid Core to 0% power and restore 1%. Both grid lamps should recover; the dock room should stay independent.
6. Save and Quit, then reopen. Verify wall/ceiling orientation, switches, links and cell charge. Report perceived behavior before diagnostic explanation.

Read-only evidence uses this checkout's `tools/lab_client.py --world 'Work Lamp Check' snapshot`, `scores wlamp '#built'`, and approved `function work_lamp_check:read`. The diagnostic function reports live switch/light states, Cell Dock contents, Core allocation and room thermal/O2 summaries. Never automatically retry a timed-out request or advance/reset the owner's replay.

## Verification and acceptance

Owner accepted save/reload on October 17: "yup everything survived the reload. check the logs and lets move on to the floodlight. what exaclty will the floodlight do?" The Work Lamp owner replay is accepted and the Floodlight slice may proceed. The client log confirms save/leave, rejoin, and a subsequent complete save/shutdown. One OpenAL `Stop: Invalid name parameter` error occurred during the first logout; there are no Work Lamp or save/load errors. The client/world are now closed by the owner; do not reopen or alter this accepted save for Floodlight fixtures. Read-only closed-save verification and logs are preserved as `build/work-lamp-evidence/reload-*`.

Closed-save read under a shared session lock confirms game tick 3305, wall facing SOUTH/enabled, ceiling facing DOWN/disabled, and 10 saved power links. The dock contains a depleted cell and its lamp is enabled. The owner reconfigured links and placed additional fixtures during the replay: the Core currently links to added lamps near `(-27,65,-1)` and `(-27,66,-1)`, and the original wall lamp links to an added lamp at `(0,67,1)`. The original demo ceiling edge is absent in this edited graph; do not interpret the original demo wall being dark as a reload regression or reset the owner's topology. Current Core power share is 36%.

Final Java 21 `build architectVerify` passed on October 17: 825 unit tests, 622 native cases, all 613 required cases; zero failures/errors/skips. This includes the Work Lamp signed-short packet check. Existing deprecation, Gradle and toolchain-discovery warnings remain. Full reports, source fingerprint and dependency-preservation hashes are in `build/work-lamp-evidence/verification.json` and `final-*`.

Release jar SHA-256: `4620c3c7a4f1933b64c903fb816d9a197e1d0b592e10e149208c0e651c72579c`. Only the Frozen Dawn smoke-profile jar was replaced; the previous jar was preserved and all five dependency jars remained byte-identical. The newly launched Gradle lab client loads the current source build. Any separately running smoke-profile client needs a restart to load that copied jar.

Owner report on October 17, preserved before follow-up diagnostics: "yup wall and ceiling toggles work. check the logs and tell me what to test next." Wall/ceiling toggle behavior is accepted. Binder routing, mounting/support removal, depletion/replacement, outage and save/reopen acceptance remain pending.

Owner clarified: "ah i already did all the clickable functions. everything worked". Accept this as the owner's successful station replay report; do not repeat the station walkthrough or infer separately measured battery timing from it. A fresh bridge status still identifies the original live session at tick 3268, menu-paused, so save/reopen acceptance remains outstanding. Next: restore Core power, leave the wall lamp ON and ceiling lamp OFF, then Save and Quit/reopen and verify switches, mounting and links. Floodlight follows this final persistence check.

Fresh follow-up Read and snapshot at game tick 3268 used the same live world/session. Read output: grid wall enabled/dark; ceiling disabled/dark; Cell Dock lamp enabled/dark. Client logs have no ERROR/Exception matches. Evidence is `build/work-lamp-evidence/wall-ceiling-accepted-*`; the fresh rendered Read is also in `/tmp/work-lamp-client.log` at 23:44:18 UTC. These current states do not certify a completed battery or outage replay. Next owner step: set Core power to 1%, return to the grid room and enable both lamps; test Binder selection, Shift-clear on the ceiling lamp and rebinding wall lamp to ceiling lamp, with switch states preserved.

Live launch: Java 21 Gradle terminal session `36951` is running this checkout's `runClientLab` with `-PfdLabWorld='Work Lamp Check'`. Bridge session `0e81b9be-8a23-405d-80bf-50c5aac75afd` confirmed the correct world, `#built wlamp = 1`, Creative Dev at `(0.5,65,4.5)`, progression paused at 2,592,000 apocalypse ticks and a sealed starting room. A fresh approved Read confirmed the grid wall lamp enabled/lit, with ceiling and Cell Dock lamps disabled/dark. Startup logs had no ERROR/Exception matches or missing Work Lamp model warnings. Evidence: `build/work-lamp-evidence/startup-*`. The client is open and menu-paused for the owner's first replay; do not reset, advance, or close it automatically.

The first complete 622-case run passed all six Work Lamp cases but hit an existing Forge Auto/Heat fixture's link assertion. That fixture moved its blocks to absolute Y=-16, above the GameTest enclosure, without owning the surrounding pressure geometry. The fixture now supplies a small sealed room, restores the original block states and reports the exact link rejection if one occurs; Forge production behavior is unchanged. The failed report, source fingerprint, log and entire disposable world (1,810 files) are preserved under `build/work-lamp-evidence/forge-fixture-failure*` before the next native run.

The first gate after that fixture fix passed all 622 native cases and all 613 required cases. A subsequent UI audit found Cell Dock flow was transported in whole 100 W steps, making a paid 50 W load display Idle. Storage menus now add remainder words while retaining the coarse words' full rack range, and the Work Lamp dock case checks a real 50 W reading through signed-short serialization. The final gate includes this display correction.

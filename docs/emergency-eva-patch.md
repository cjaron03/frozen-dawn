# Emergency EVA death recovery patch

Owning checkout: `.worktrees/emergency-eva`, branch `fix/emergency-eva-respawn`, baseline `0885e22812f8723a6f55cdf52da87b62a18c4485` from freshly fetched `origin/feat/maeve-director`.

Late Phase 6 death previously dropped the survival suit and returned the player to a base that could be lethally cold even with a lit heater. The emergency kit automatically supplies missing EVA pieces on death respawn during the existing vacuum stage. A retained ordinary sealed rig with usable tank oxygen takes priority. Replaced non-emergency armor is preserved in inventory or dropped if inventory is full. End returns, login, earlier phases, Creative and Spectator do not grant a kit.

The kit uses EVA textures and leather-level armor protection. One persistent, player-bound issue ID controls all its pieces and its ten-minute oxygen/thermal service life. Any worn issued piece drains that shared reserve, including indoors; removing all pieces pauses it. Saving, relogging, dimensional travel, partial replacement and re-equipping cannot refill it. A new death invalidates the former issue, including pieces stored in chests or passed to someone else. Emergency pieces are removed from death drops and retained inventory. Qualifying ordinary death drops now have a fifteen-minute loaded/ticking lifetime; environmental destruction and pickup remain ordinary.

A complete active EVA rig with emergency pieces provides climate control and an internal air reserve. This reserve uses no refillable tank, efficiency module, patch or cartridge. Visual deterioration is separate from ordinary suit punctures, so cracks do not trigger puncture venting. Emergency pieces cannot break from combat wear; their inventory bars follow the shared service clock. Incoming damage still applies through weak armor. At reserve zero, emergency pieces cease to qualify as sealed EVA, lose their armor attributes, and remain visibly spent. Normal cold/vacuum hazards resume, and the header reads EMERGENCY EVA DEPLETED. Equipping a complete ordinary rig restores its normal behavior. Emergency equipment grants no armor crafting advancements, recipes, repair ingredient or salvage route.

The existing EVA oxygen HUD renders emergency telemetry through the same panel, ORSA badge, borders, spacing, fonts, time and warning colors. Its header is EMERGENCY EVA ACTIVE and its second line is Reserve oxygen: M:SS, with no percentage. At zero the header changes to EMERGENCY EVA DEPLETED and the panel uses the normal vacuum color. Cracks remain at the screen edges and strengthen as the reserve drains. An activation beep plays once per issue per client session. Quiet seal creaks begin below five minutes. Beeps repeat every ten seconds below two minutes, every three seconds below one minute and every second below fifteen seconds. Existing overlay settings and hidden-HUD controls apply.

## Human recovery preview

Launch Java 21 `./gradlew runClientLab --console=plain` from the owning checkout, inside the agent terminal. Open **Emergency EVA Recovery - Phase 6**. This is fresh default terrain generated from default-world metadata, with no copied terrain, player inventory, apocalypse attachments or replay history. A guarded, one-time development setup advances the apocalypse to day 104 under the Default preset (120 days), keeps natural mob spawning, weather and daylight active, and locates dry terrain with up to four blocks of footprint slope for a supported stone base and a separate outdoor respawn about 300 blocks away. Difficulty is Normal. This validates recovery in a newly advanced default world; it does not reproduce 104 days of accumulated play or the original rehearsal save.

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

`./gradlew architectVerify --console=plain` passed: 646 unit tests, 224 native GameTests, and all 219 required native cases/reports. All five emergency EVA tests passed, including a lit heater in a genuinely sealed, lethally cold room, real atmospheric damage before/after reserve expiry, persistence, retained equipment, old-issue rejection and death drops. The smoke-test mod jar was replaced with the verified build; SHA-256: `1a0ddc321617497e9d7afcb9a1463a9b03d94e6c743b9a0606a4031956cf6256`. The human HUD/audio pass remains pending.

Recovery follow-up: `./gradlew build compileLabBridgeJava architectVerify --console=plain` passed again after the shared HUD and terrain setup changes. The client was restarted with the new save selected through Quick Play. Initial bridge status reports no loaded world; the first live READY/floor confirmation and the owner recovery run remain pending. The branch stays local while the owner tests.

Terrain preparation failure: the first default-world attempt stayed at stage -1 in Creative. The helper required a 13-block footprint with height variation <=2; saved-region inspection confirmed ordinary slopes and transformed tree cover. The closed failed world and log are archived in `build/emergency-eva-evidence/preparation-failed/`. The revised helper accepts <=4 blocks of slope, scans through canopy/snow to solid dry support, deepens the foundation, connects the north entrance to terrain with supported steps, and logs each placement failure. Three required native regressions cover the three-block-slope rejection, canopy/water behavior, and completed room placement before verification. Room construction now uses synchronous block placement, avoiding queued nested function execution. `emergency_eva:repair` is limited to this exact world at stage -1, tagged Creative participant, and stops at stage 1 without causing death. It resumes construction without resetting day, world history or entities.

Terrain-fix verification: full build and `architectVerify` passed with 646 unit tests and 227 native tests, including all 222 required cases and the three new terrain/placement regressions.

Live repair checkpoint: exact-world bridge repair succeeded once, then `#stage=1` and `#prepared=1` were confirmed. Player is at the verified base `(320.5,95,-15.5)`; forced outdoor respawn is `(0,80,0)`. The room floor, roof, heater, chest and supported entrance completed before teleport. The original natural Architect and world history were preserved. READY and the death-test button appeared in chat. Client is running in the owning checkout; survival recovery/HUD/audio acceptance remains for the owner. This Default world uses the 120-day timeline and day 104 is in the existing vacuum stage; the new-world helper retains that default timeline.

Emergency activation voice: a fresh issue plays the existing activation beep,
then after one second the Piper/ORSA voice says “Emergency EVA active. Ten
minutes of reserve life support.” The same typed suit dialogue HUD used by the other suit announcements appears alongside the voice, using the existing ORSA speaker panel, reveal speed and ten-second display duration. It does not depend on Minecraft sound subtitles being enabled. The announcement plays once per issue per client session; re-equipping does not repeat it. Reopening a partly spent reserve does
not announce a new ten minutes. Removing the kit or dying cancels pending speech; playback requires an active
sealed rig when the announcement is due. Later urgency remains the existing beeps and
cracks. Ordinary tank telemetry keeps its normal percentage display.

Timer/voice verification: full build and `architectVerify` passed (646 unit tests, 227 native cases, all 222 required cases). New OGG decodes as stereo Vorbis at 44.1 kHz, duration 4.714 seconds; the jar includes the asset, subtitle and updated Piper attribution. Existing voice OGGs were preserved. The smoke jar matches the verified build. The client is relaunched at the title screen; open the preserved recovery save and cause a fresh death/respawn to hear the activation line. Perceived HUD/audio acceptance remains pending. Pre-existing EventBusSubscriber and Gradle deprecation warnings remain.

Typed HUD follow-up: full `./gradlew build --console=plain` passed after connecting the activation line to `showSuitDialogue`. The shipped language entry matches the voice subtitle and the jar contains the shared dialogue call. The smoke jar matches SHA-256 `3fc5d2eb4159aad6e882ef4faa2c548adec8c20840f956290f5d1f72f2ab5a1b`. The closed recovery save and previous client log are backed up in `build/emergency-eva-evidence/typed-dialogue/`; human typewriter/audio acceptance remains pending. Existing Gradle deprecation warnings remain.

## ORSA Continuity Protocol — current implementation

The formal system name is ORSA Continuity Protocol. It extends the field manual's existing “Continuity protocol: active” wording without explaining what continuity means or asserting that ORSA causes resurrection. Equipment names, tooltips and a new ORSA Equipment manual entry identify the protocol. The existing Piper activation OGG is unchanged. After that announcement finishes, one typed notice explains the approximate shelter record, or the absence of one, through the existing `ORSA // SUIT AI` dialogue. That notice waits for an idle dialogue slot and does not replace a current warning.

Successful unforced bed or charged-anchor spawn updates record shelter automatically, after vanilla accepts the update. Canceled updates, resets and forced command spawns do not replace the record. Existing saves initialize from saved unforced respawn coordinates. Destroying the bed preserves the historical position. One saved, nonzero horizontal estimate offset is bounded by sixteen blocks; replacement issues and reloads do not reroll it. The exact bed position stays server-side. No destination chunks are loaded or inspected for guidance.

The recovery panel uses the same frame renderer, ORSA badge, background, font and EVA colors as the air HUD. It stacks beneath the air panel; typed dialogue stacks beneath navigation. LAST SHELTER shows POSITION DEGRADED, an eight-sector world bearing, a relative turn arrow and distance rounded to 25 blocks. Inside 32 horizontal blocks of the estimate it shows SEARCH AREA and the search radius. This area contains the recorded bed location. It reports saved coordinates and dimension instead of a misleading bearing across dimensions. Missing records are stated explicitly. Last Telemetry uses the recorded ordinary-equipment death location with horizontal distance and vertical difference; it never promises surviving items or safe terrain.

`N`, rebindable in Controls under Frozen Dawn, switches destinations. A fresh issue selects Last Telemetry. The selected destination is saved across reloads; a server-side issue check rejects stale or expired target-selection packets. Navigation ends with reserve zero or when the emergency kit is removed. Empty-handed emergency deaths preserve the original equipment target. Deaths dropping ordinary equipment establish a new target.

Ordinary items dropped by a qualifying late-Phase-6 death get one fifteen-minute lifespan in loaded/ticking chunks. Their saved age, damage, pickup and normal environmental destruction continue. Recovery drops cannot merge with younger items, because vanilla merging adopts the younger age and could otherwise refresh the window. Subsequent empty-handed deaths and replacement issues do not extend existing drops. Picked-up items follow normal inventory behavior. Earlier phases retain normal drop rules.

Emergency service bars use the existing pale blue, amber and pale red reserve colors. They snapshot the shared reserve once per second and expire immediately at zero; combat wear does not shorten it. Spent or invalid-owner carried pieces have no armor modifiers. Repair/enchantment paths cannot extend service. Inventory/tooltips and the shared air HUD use the same clock.

## Continuity playtest handoff

Open **ORSA Continuity - Phase 6**, a separate fresh default-terrain preview generated from closed default-world metadata. The original **Emergency EVA Recovery - Phase 6** is preserved in `build/emergency-eva-evidence/continuity/world-before-update`. This new preview keeps the Default 120-day timeline, day 104, Normal difficulty, natural spawning and weather. It records a real bed at the prepared base, then removes the bed. Vanilla therefore falls back to the outdoor world spawn on death while ORSA keeps the historical shelter fix. Preparation stays Creative and stops on failure; no death happens until the owner clicks **DIE AND WALK BACK** or runs `/function continuity_eva:start`.

After Respawn, inspect the ordinary activation line, service bars and matching ORSA recovery panel. Press `N` to choose Last Shelter and follow the approximate reading; the final approach becomes a 32-block visual search. Recover ordinary EVA and oxygen to check handover. Save and Quit/reopen to check reserve, estimate and destination persistence. `/function continuity_eva:controls` offers the same optional shortened-reserve buttons for expiry/alarms. The exact-world bridge allows only the corresponding start/controls/failed-preparation repair stages.

Final verification: full build and `architectVerify` passed with 650 unit tests, 233 native tests and all 228 required native cases. Six new native cases cover real spawn hooks/cancellation, bounded saved estimates, network privacy/dimensions, death clone/migration, repeat-death recovery records, saved drop expiry/merge rejection, and combat/armor/selection expiry. Three Python preparation regressions pass: default Overworld with flat mod dimensions is accepted, flat Overworld and amplified settings are rejected. The latter fixed a whole-file string guard that incorrectly rejected the mod's own unrelated flat dimension.

Final build and smoke jar SHA-256: `3ae293d11af0fef1e9f65184624b6800dcda67cd5e3320716b5683a2458dd8ee`. Evidence is in `build/emergency-eva-evidence/continuity/verification.json`, `gametest-report.xml`, `verify.log`, `preparation-tests.log` and `client-launch.log`. The distributed jar includes the new manual entry and existing voice, with no lab classes. Human visual/audio acceptance and live multiplayer remain pending. Existing Gradle deprecation warnings remain. Branch remains local while the owner tests.

Live Continuity handoff: Quick Play opened the exact new world. One-time preparation succeeded (`#stage=1`, `#prepared=1`), and the log confirmed the real bed registration/removal before READY. Player Dev is Creative at `(320.5,95,-15.5)`; outdoor world spawn is `(0,80,0)`. The client is paused in the prepared world and no death/start was dispatched. Session `65218`, launch log `build/emergency-eva-evidence/continuity/client-launch.log`, read-only snapshot `live-ready.json`, and ready-score response are preserved. Startup/world-generation heartbeat timeouts appeared in the log; subsequent fresh status, snapshot and score reads succeeded. Owner HUD/audio/recovery acceptance is still pending.

## Emergency filter breathing — initial version

Emergency life support now plays a processed derivative of the owner's existing
EVA breathing recording: narrower filter bandwidth, dry papery rasp and slight
valve flutter. The original normal-EVA recording is unchanged. Breath pacing
and the 15.808-second clip length are retained; decoded RMS is matched within
0.001 dB of the original. Rasp follows the breath rather than running as a
constant hiss. Existing Hearthrot attenuation and sever pitch cues still apply.
Changing between emergency and ordinary life support immediately fades the
current clip and starts the corresponding filter sound. Existing vacuum,
breathability and usable-air gates still determine whether breathing plays.

The source hash, deterministic processing script and original-asset terms are
recorded in `tools/audio_sources/emergency_eva/SOURCES.md`; the shipped inventory
now contains 302 records. Full build passed with 650 unit tests and existing
Gradle deprecation warnings. Packaged sound registration, original preservation,
duration, peak and loudness checks passed. Native survival tests were not rerun
for this client/audio-only change. Listening acceptance remains with the owner.
Evidence and an MP3 preview are in `build/emergency-eva-evidence/filter-breathing/`.
Build and smoke jar SHA-256:
`11b528d03c5022e38ad13af5f500dcf21f47ab3b16981aff04bb41409f5bcfb9`.
The previous client was already closed. Its saved Continuity world and client
log were backed up before relaunching at the title screen for this sound pass.
The branch remains local.

## Combined emergency breathing — first mix

The owner selected a combination of the two researched CC0 recordings. The
emergency loop now uses Nuclearoid's GP-5 gas-mask recording with three short
passages from casiba842's scared, uneven breathing performance. The mask layer
ducks during those passages to blend them into one performance. Band-limited
EQ, restrained saturation and two-second fades make a fifteen-second clip for
the existing thirteen-second start interval. The original normal EVA recording
remains the loudness reference and is unchanged.

The retained inputs, exact preview URLs, source hashes, retrieval dates,
individual CC0 evidence and deterministic processing are in
`tools/audio_sources/emergency_eva/`. The runtime asset is now classified as
CC0-derived in the inventory; both creators and license are credited in the
packaged `AUDIO_NOTICE.md`.

Full build passed with 650 unit tests and existing Gradle deprecation warnings.
Decoded duration is 15.000 seconds, RMS -23.959 dBFS and peak 0.7074. Simulated
thirteen-second overlapping playback also peaks at 0.7074. Packaged OGG bytes,
normal-recording preservation, inventory hash and both creators' credits were
verified. Human listening acceptance remains pending; native survival checks
were not rerun for this asset/notice change.

Build and smoke jar SHA-256:
`e5519285ea7771e9fc3f056f67dc07b8438c88e28369e62ccfa614c3ab442ce2`.
Evidence, build log and MP3 preview are in
`build/emergency-eva-evidence/combined-breathing/`. A fresh bridge heartbeat
confirmed the title screen before the closed Continuity world and client log
were backed up and the owning client restarted. Open **ORSA Continuity - Phase
6** to hear the emergency mix; its saved test progress is preserved. The branch
remains local while the owner tests.

## Single-performance emergency breathing — first sequential version

Owner feedback on the first mix: “it sounds like two or more breaths overlapped”.
That mix did retain mask audio beneath the strained passages, and the existing
runtime scheduler overlapped successive clips for two seconds. Both sources of
emergency overlap are removed.

The new fifteen-second asset alternates two mask passages with two strained
passages. One recording contributes at each output sample; separate 100 ms
fades soften each cut and the loop seam. Exact source windows are recorded in
the updated source ledger and generation script. The previous rejected mix is
preserved as `single-breathing/previous-overlapping-mix.ogg`.

Emergency EVA now uses one `TickableBreathingSound` instance with native looping.
Normal-EVA clips are stopped before it starts, including the previous overlap
clip; the emergency loop is stopped before handing back to normal EVA. Pausing,
leaving the world or losing life support stops the tracked instances. Hearthrot
volume attenuation/recovery rates and the sever pitch cue remain connected.
Normal EVA keeps its existing clip cadence and original recording.

Full build passed with 650 unit tests and existing Gradle deprecation warnings.
Decoded asset duration is 15.000 seconds, RMS -24.955 dBFS and peak 0.7109.
Measured sample jumps at the edits and loop seam are below 0.00004. Packaged
audio, client loop class, original-recording preservation and credits passed
verification. Listening and actual in-game playback remain owner acceptance;
native survival checks were not rerun for this client/audio-only fix.

Build and smoke jar SHA-256:
`1dab1b541d9b94e10256249954063a754213f459dfd7a8564b4fde0231fb197e`.
Evidence, preview, preserved feedback and client launch log are in
`build/emergency-eva-evidence/single-breathing/`. The fresh title-screen heartbeat
was checked before backing up the closed Continuity save and restarting only
the owning client. The branch remains local.

## Cough/gasp breathing and taped visor — first presentation

The owner requested coughing, stronger inhales and a piece of pixelated duct
tape on the first-person visor. The sound sequence is now 42 seconds: mask and
strained breathing, a restrained double cough at 11.6 seconds, a 140 ms recovery
pause, a sharper gasp at 12.74 seconds, more breathing and another gasp at 29.34
seconds. Vocals stay sequential inside the existing single looping instance.
Shared EQ and gentle compression keep the four performances close in tone and
level. Added inputs are qubodup's **Strong Double Cough** and jawbutch's **Male
Gasp 1.wav**, each verified as CC0 on its individual Freesound page. Their exact
preview URLs, copies, source hashes, retrieval times and voluntary credits are
retained with the source manifest and packaged audio notice.

The emergency helmet now shows a dull silver-gray pixel-drawn tape strip across
the upper-right visor edge, with torn ends, fibers, a dark crease and a lifted
edge. It follows the helmet's matching issue, including after reserve expiry.
It appears only in first person, follows the existing suit-overlay option and
HUD visibility/intro suppression, and does not cover the center or left-side
ORSA telemetry. Wearing emergency body pieces with a normal helmet does not
show the tape. The patch is original code-drawn art; `visor-tape-closeup.png` is
a magnified rendering of that exact pattern, not a live screenshot.

Full build passed after both changes with 650 unit tests and existing Gradle
deprecation warnings. The final audio is 42.000 seconds, RMS -23.909 dBFS and
peak 0.4984, with loop-seam sample jump below 0.00003. Packaged audio, original
normal-EVA preservation, all four creator credits and visor rendering class
were verified. Human visual/listening acceptance and live multiplayer remain
pending. Native survival checks were not rerun for this presentation change.

Build and smoke jar SHA-256:
`3578bb36195cced3a6e4a14d070cce936246e13bb843855b6f9137d8ec31bf4d`.
Evidence, source-page snapshots, audio preview and tape close-up are in
`build/emergency-eva-evidence/cough-gasp/`. The owner closed the client; its
latest Continuity save and log were preserved before relaunch at the title
screen. No test stage or reserve reset was dispatched. Branch remains local.


## RETURN ONLY suit presentation — current local patch

Owner feedback preserved before redesign: “eh it doesnt look realistic. looks
too small.” The owner requested the full presentation upgrade, reinforced
Minecraft pixel art, then asked to leave the client closed overnight.

The first-person tape now occupies 33 percent of GUI width across the
upper-right outer visor. It has torn edges, blocky folds, dirty adhesive and a
lifted end. The texture was generated with OpenAI image generation and then
revised into coarse Minecraft-style pixel art; it is retained unchanged, with
nearest-neighbor sampling explicitly enabled. This is AI-assisted art rather
than the earlier hand-coded pattern. Its source/prompt summary and hash are
in `tools/visual_sources/emergency_eva/SOURCES.md`; `ASSETS.md` distinguishes it
from owner-authored interface art. Existing matching-helmet, first-person,
overlay-option and intro/HUD visibility guards apply. Tape remains visible on
spent gear. It represents repair to the protective outer visor.

Original model geometry adds a compact stepped reserve bottle, cap, exposed
regulator, hose, straps, amber pull-tab, ORSA-blue pack-label stripe, patched
chest/arm/leg panels, boot seams, helmet collar and a small exterior visor
repair. Equipped-item identity drives third-person rendering, including remote
players, without depending on owner-only attachment sync. Both player skin
renderers get the layer. Visual fit and remote multiplayer appearance still
need owner acceptance; compilation does not validate those.

Subtle cosmetic condensation develops with sustained sprinting and rises a
little as reserve falls. It dissipates gradually. Fog is confined to the
outer 6 percent of the sides below the upper third and 9 percent of the bottom;
center view and upper-left ORSA telemetry remain clear. It is rendered only
with active life support. It imposes no new health, air or movement penalty.

The shared ORSA air panel gains a third row: RETURN ONLY // PACK ONLINE,
SEAL OPEN or PACK SPENT, based on actual reserve and full-seal state. The
existing colors, frame, badge and timer remain. Navigation and typed suit
dialogue offsets follow the panel's height, so the extra row has its own space.

A quiet eight-second procedural fan loop adds bearing chatter and occasional
regulator ticks under the existing single vocal loop. Its slight pitch change
and volume track actual reserve, with Hearthrot attenuation preserved. It
stops on loss of support, ordinary-EVA handoff, pause, death or world exit.
The 42-second CC0 cough/gasp breathing asset and original normal breathing are
byte-for-byte unchanged. Added regulator and shutdown click/purge cues are
original synthesis with no recordings or speech. Reserve exhaustion triggers
one shutdown cue and an existing-style typed warning: “Continuity reserve
exhausted. Life support offline.” An expired login does not replay exhaustion.
The existing low-reserve alarm sequence still precedes shutdown. The dependable
10-minute service window and Continuity recovery mechanics are retained.

Full Java 21 build passed: 650 unit tests, zero failures/errors/skips, plus
8 existing architect gate tests. Pre-existing EventBusSubscriber and Gradle
deprecation warnings remain. Packaged class/texture/audio bytes, subtitle
registrations, all four CC0 creator credits and normal/emergency breathing
preservation passed verification. New decoded fan peak is 0.1113, with a loop
seam jump of 0.000159; regulator peak 0.1027 and shutdown peak 0.1877.
No new unit tests were added for this cosmetic implementation. Native survival
GameTests were not rerun; the prior mechanics checkpoint remains separate.
Actual visual, listening and multiplayer acceptance remain pending.

Build and installed smoke jar SHA-256:
`3350e51f20189fd90641c26cf971d0cbff0623091db87c82ca740b2bd4c4b360`.
Only the Frozen Dawn smoke jar was replaced. Evidence and closed-world backup
are in `build/emergency-eva-evidence/return-only/`, including `verification.json`,
`build.log`, `world-before-update`, `client-before-update.log` and the previous
breathing asset. A process audit confirmed no owning lab client before backup.
The latest saved ORSA Continuity world was preserved without a reset, reserve
refresh, death command or test-stage advance. No client was launched, per the
owner's latest instruction. Branch remains local, with no push.

Next owner test: launch this checkout's Java 21 `./gradlew runClientLab
--console=plain`, open the preserved ORSA Continuity - Phase 6 world, and use
the normal agreed death/return scenario. Inspect tape in first person and the
reserve pack in third person; sprint long enough to see peripheral moisture;
check seal-open status by removing a suit piece and listen through a normal
reserve expiry. Do not reset the existing scenario automatically. This is a
future handoff, not evidence that the client has been launched or inspected.

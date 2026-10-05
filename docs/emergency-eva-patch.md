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


## Quieter ventilation, aging moisture and sparse diagnostics

Owner feedback: “make the fan less annoying, and maybe overtime add
condenstaion in the helmet, and various error messages that show up?” The owner
confirmed the title screen. Fresh bridge status verified the owning checkout
and closed world before its save/log/fan were backed up, then only that client
was stopped for the Java update.

The fan now uses soft filtered ventilation and a faint motor undertone. The
23 Hz bearing buzz, modulation and repeating regulator clicks have been
removed from the loop. Runtime volume falls from 0.08–0.12 to 0.035–0.040;
combined source/runtime RMS is 15.59 dB lower at full reserve. The decoded
8-second loop peaks at 0.04526, with a 0.00000975 seam jump. Regulator/shutdown
waveforms are unchanged; regenerated OGG container identifiers changed their
byte hashes. Both vocal recordings remain byte-for-byte unchanged.

Condensation now grows with actual reserve service age, even while walking,
using a gradual smooth curve after the first minute. Sprinting adds a smaller
extra moisture contribution. Fog depth and opacity increase along the sides
below the upper third and bottom, with sparse fixed droplets once moisture
is established. Its maximum target is 0.88; perimeter opacity stays restrained
and the central navigation view and upper-left telemetry stay clear. Active
life support, matching helmet, first-person camera, overlay option and HUD
visibility still gate the visual. No gameplay penalties were added.

New typed ORSA warning diagnostics use the current warning panel and speaker:

- CP-003: Seal open. Complete EVA rig required.
- CP-014: Visor moisture detected. Demisting capacity limited.
- CP-021: Service life below half. Replacement required.
- CP-022: Reserve low. Two minutes or less remaining.
- CP-023: Reserve critical. One minute or less remaining.

Messages report actual seal, displayed moisture or remaining-service state.
They wait for the shared suit dialogue channel, obey HUD/intro suppression,
and are acknowledged only once actually displayed. A 35-second gap limits
repetition. Reserve warnings take priority over moisture; higher reserve
severity supersedes obsolete lower-severity warnings. Seal warnings may
return after a real close/reopen, subject to the same cooldown. Rejoining a
partly spent issue does not replay past reserve thresholds; new issues reset
this policy. The existing shutdown warning remains authoritative at zero.
No extra voice recordings or alarm sounds were added for the diagnostics.

Full Java 21 build passed: 654 unit tests (including four focused diagnostic
lifecycle scenarios), zero failures/errors/skips, and 8 existing gate tests.
The new tests cover delayed acknowledgement, severity replacement, rejoin,
new issue, seal reopening, cooldown and moisture conditions. Packaged audio,
message keys and diagnostics class were verified. Pre-existing deprecation
warnings remain. Native survival tests were not rerun for this client-only
presentation update; actual visual/listening acceptance remains the owner's
playtest.

Build and installed smoke jar SHA-256:
`e12fbd552bda8e2f784568d58508e9461cfc04ed11f41c01dea095b333c58962`.
Evidence is in `build/emergency-eva-evidence/quiet-fan-diagnostics/`, including
`world-before-update`, `client-before-update.log`, `fan-before-update.ogg`,
`runtime-before.json`, `build.log` and `verification.json`. Only the Frozen
Dawn smoke jar was replaced. Restart into this owning lab checkout after
verification; open the preserved ORSA Continuity - Phase 6 world for testing.
Do not reset the reserve or advance the scenario automatically. The patch
branch remains local; no push.


## Emergency EVA exertion and recovery — current gameplay

Owner requested greater breath rate, oxygen use and visor moisture while
running, then corrected the proposed static comparison: “not 8. it will consume
more o2 at a steady rate. and slow down and then be normal.” The implementation
retains one ten-minute normal-draw reserve. There is no separate eight-minute
countdown or shortened issue lease. Its actual debit follows gradual exertion.

Server-owned metabolic load rises over 50 moving-sprint ticks (2.5 seconds) to
a steady higher draw, capped at 25 percent above normal. After running stops,
load falls over up to 200 ticks (10 seconds), then draw is exactly normal again.
Standing with a sprint flag and riding do not raise the load. The server uses
ServerPlayer's known client movement vector, not a render-frame timer. Reserve
debit uses integer fractional accounting; load and fractional debt persist in
NBT and non-death clones. Removing all issued gear still pauses reserve debit
while load recovers. Old saves lacking the new fields start at normal load,
without changing their remaining reserve. Fresh death issues reset load/debt.
Life support and item bars still terminate exactly at zero, including during
higher draw. Normal EVA oxygen mechanics are unchanged.

The emergency packet now mirrors metabolic load with the remaining reserve.
The existing timer continues to show remaining normal-draw capacity; it counts
down faster during elevated consumption and settles with the body. The shared
ORSA panel shows HIGH DRAW in its normal warning color while load is elevated,
then returns to PACK ONLINE. CP-024 adds a one-per-issue typed warning:
“Exertion elevated. Oxygen draw increased. Ease your pace to recover.” It uses
the existing idle-dialogue/cooldown policy and actual current load. Tooltips
explain the draw/recovery behavior without adding a percentage. The existing
Piper activation row alone was regenerated with en_US-amy-medium and the
orsa profile: “Emergency EVA active. Up to ten minutes of reserve life support.
Exertion increases oxygen use.” Existing Piper credits and licensing remain;
no macOS voice was used.

Breathing volume follows smoothed authoritative load. At sustained exertion,
the client switches to a pitch-preserving 1.25-tempo derivative of the existing
CC0 sequential loop; the old vocal instance fades completely out before the
new instance begins. Recovery hysteresis holds the faster performance briefly
and returns to normal as load settles. The sound instance permits silent
starts so these fade-ins can actually play. There is never an additive second
vocal performance. The normal recording, base emergency recording and quiet
fan are byte-for-byte preserved. The new exertion loop is 33.612 seconds, with
a decoded mono peak of 0.6923 and seam jump below 0.00001. The single updated
Piper line is 7.744 seconds and peaks at 0.6075. Listening transitions still
need owner acceptance.

Exertion has a much larger moisture contribution, combined with reserve age,
with the target capped at 1.0. Fog rises faster and dissipates more slowly than
oxygen draw, producing visible sweat/dampness after a run. Dense pixelated
moisture and droplets spread along the sides, bottom and upper-right visor;
a faint central film is capped at 8/255 alpha (about three percent). The
upper-left telemetry region is excluded from both film and droplet passes.
The existing camera, matching helmet, overlay, active-support and visibility
guards remain. There is no additional damage, random leak or movement penalty.
The generated tape sprite is unchanged.

Full Java 21 build passed: 657 unit tests, zero failures/errors/skips, plus
8 existing gate tests. The native Minecraft gate passed 235 GameTests and all
230 required cases/reports, including the two new server lifecycle cases.
Verified scenarios include stationary sprint flag, gradual load/extra debit,
recovery, exact normal draw after settling, persisted fractional debt, real
packet-codec roundtrip, unchanged walking budget, zero clamping, support/bar
expiry and a fresh issue. New pure tests cover interrupted runs and current
exertion warning priority. Native artifacts are in
`build/architect-reports/f39cd717-4585-4f67-858b-7d1d4095d363/`.
Pre-existing Gradle and EventBusSubscriber deprecation warnings remain. These
checks do not prove visual, auditory or live multiplayer acceptance.

Build and installed smoke jar SHA-256:
`5dfa5122db8dd793946297a3e1c57dcc6c59cdbcb13258baaaa5b6b9e8995a73`.
The client had already been closed when the owner confirmed the title screen;
a process audit found no owning client before preserving the closed world/log.
Evidence is in `build/emergency-eva-evidence/sprint-exertion/`, with the backup,
build/native logs, voice manifest, verification and subsequent client launch
log. The saved world matches its backup byte-for-byte; no reserve reset, death
command or scenario advancement was dispatched. Restart the owning Java 21 lab
client and open ORSA Continuity - Phase 6 for the owner's sprint/recovery test.
Watch HIGH DRAW, breath pace and fog during a sustained run, then stop and allow
normal draw to return while moisture dissipates more slowly. The branch remains
local; no push.

The final sound handoff also explicitly stops breathing and fan instances when
Hearthrot attenuation fully mutes suit audio. Silent fade-in support must not
cause continuous zero-volume clip restarts or small fan bursts. Partially
attenuated fan starts inherit the attenuation level. The native gate is rerun
against this final source fingerprint, with the earlier passing report retained
as `native-report-before-mute-fix.xml` in the evidence folder.


## Full-screen visor mist correction — 2026-10-05

The owner's screenshot showed checkerboard moisture, a sharp horizontal film
boundary and a large rectangular clear region around ORSA telemetry, alongside
a perceived FPS drop. Replaced the per-frame grid loops with a single cached
512x256 full-screen translucent texture. Original seeded multiscale clouding,
continuous edge falloff and softened sparse wet trails cover every pixel; the
center stays lighter and the seal edges gather denser moisture. Linear texture
sampling removes the hard cell pattern. Fog still follows the existing synced
exertion/service-age target and slow recovery, without changing reserve debit.

The visor layer now renders immediately after vanilla camera overlays, before
crosshair, health, hotbar and all ORSA instruments. Tape and crack overlays share
that material layer; the sealing progress indicator stays in the HUD layer.
Telemetry stays sharp through layer ordering, without a rectangular exclusion
mask. The pixelated tape asset and sound assets remain byte-for-byte unchanged.
Shader tint resets after mist rendering. No framebuffer blur, runtime texture
generation or per-frame random noise is added.

At an illustrative 640x345 GUI size (approximately the supplied screenshot at
GUI scale four), the former renderer visited 13,920 cells and submitted 6,426
rectangle fills at 70 percent condensation or 7,727 at maximum. The new fog
submits one textured quad regardless of GUI size. This establishes reduced
render submission work; actual before/after FPS remains unmeasured and needs
the owner's live playtest. Texture alpha is 34–158/255 at full intensity,
44/255 at center, multiplied by current condensation. Deterministic regeneration,
positive alpha everywhere, packaged resource identity and GUI layer ordering
were checked. Original generation and hash are in the visual source ledger.

Full Java 21 build passed 657 unit tests (zero failures, errors or skips) and
8 existing gate checks. Pre-existing EventBusSubscriber/Gradle deprecation
warnings remain. This client presentation change does not require repeating
the server survival gate; the prior native result is historical evidence.
Build and installed smoke jar SHA-256:
`9ab46e7f3b95e80ba0a73e6ca9063dfd9756a5a3dd8618945af3f72ff3589c48`.

The owner confirmed the title screen. Fresh bridge status confirmed the owning
checkout with no loaded world before backing up ORSA Continuity - Phase 6 and
its log. Only the verified owning lab Java process was stopped. The save matched
the backup byte-for-byte before relaunch. Evidence, backup, build/asset checks
and launch log are in `build/emergency-eva-evidence/fullscreen-fog/`. Launch uses
the owning Java 21 `runClientLab` (terminal session 76241); the owner opens the
existing world and tests sustained running, whole-screen fog, crisp instruments
and FPS. No reset, reserve refill or replay advancement was dispatched. Commit
stays local on `fix/emergency-eva-respawn`.


## Aging cooling and condition advisory — 2026-10-05

The owner approved recoverable internal heat after coolant degradation and the
exact suit-condition voice/HUD warning. A fresh issue now announces activation,
then waits for the ORSA channel to clear before delivering: “Warning. Reserve
suit exceeds rated service life. Multiple field repairs detected. Cooling and
filtration are degraded. Further failures are possible. Minimize exertion and
proceed directly to shelter.” The new Piper en_US-amy-medium recording uses the
existing ORSA profile. It is 14.733 seconds, decoded mono peak 0.5993, and its
text matches both subtitle and typed warning exactly. The amber dialogue lasts
20 seconds and shelter guidance follows afterward. New issue, death, logout,
unequipping every issued piece or reserve exhaustion cancels the queued notice;
a partly used kit does not replay the fresh-ten-minute activation promise.
All 306 prior audio exports remain byte-for-byte unchanged; the new recording
is the only added audio. License/manifest and shipped inventory include it.

An independent saved worn-time clock reaches coolant depletion at 6,000 ticks,
exactly five minutes of equipped use at normal tick rate. Extra oxygen debit
cannot prematurely advance this clock. Reduced cooling becomes progressively
less capable over the next minute (heat gain 1, then 2, then 3 units per moving
sprint tick at thirty-second intervals). Heat is bounded at 1,200; high load is
600. Once fully degraded, five seconds of sprinting from cool stays below high
heat, ten seconds reaches the warning, and twenty seconds reaches maximum.
Walking or resting removes two units per tick: maximum heat clears in thirty
seconds. Removing all issued pieces pauses both reserve and worn age while
heat recovers. A stationary sprint flag, riding or unsealed rig cannot generate
internal sprint heat. Recovery restores heat headroom, not consumed coolant.

The same cyan ORSA air panel gains one nine-pixel thermal row. It reports
COOLING NOMINAL, COOLING DEGRADED, THERMAL LOAD HIGH // EASE PACE, SEAL OPEN or
OFFLINE, using the existing muted/amber/critical palette. Navigation/dialogue
stacking follows the panel's updated bottom. CP-025 warns about reduced cooling;
CP-026 advises walking or resting when current heat is high, with a brief
existing regulator beep. Current high heat can bypass routine diagnostic gaps,
but reserve warnings retain priority. It can warn again only after genuine
recovery below half the high threshold, avoiding threshold chatter. Historical
coolant messages are skipped on login while the persistent row reports actual
condition. Environmental temperature remains environmental temperature.

Heat increases the existing full-screen condensation target and holds heavier,
faster breathing through recovery. It reuses the single-voice fade/hysteresis
path and the same cached one-quad mist, preserving the quiet fan and preventing
new vocal overlap. Heat does not inject damage, remove cold/vacuum protection,
change movement speed or add another oxygen debit. The existing exertion draw
still settles to normal. Thermal cold protection lasts until the original
reserve expires, so cooling weakness cannot recreate the respawn death trap.

Worn age and heat are stored in issue NBT, copied during non-death cloning, and
sent in the owner state packet. The changed codec requires matching updated
client/server builds. Older saves lacking age infer it once from consumed
reserve, keep the exact reserve/debt, and begin without invented heat. Fresh
recovery issues begin at zero worn age and heat. New pure checks cover actual
clock versus extra draw, short sprint tolerance, sustained accumulation/full
recovery, unequipped age pause, unsealed movement and current warning priority.
The required native case exercises server movement, save/copy, real packet
roundtrip, hot-suit cold/vacuum protection, standing versus moving, removing
gear, legacy migration and fresh issue. Existing walking-expiry and cold/vacuum
gates remain required.

Full Java 21 build passed 662 unit tests (zero failures/errors/skips) plus 8
existing gate tests. Pre-existing EventBusSubscriber/Gradle deprecation warnings
remain. Native gate result is recorded below when complete. Build and installed
smoke jar SHA-256:
`b415d39209bbdab798ea3c41c495874d79d6000556084fd4f0e2c4fae64296e1`.

The owner confirmed the title screen; fresh bridge state confirmed the owning
checkout with no loaded world. ORSA Continuity - Phase 6 and its log were backed
up before stopping only its verified lab Java process. Evidence is in
`build/emergency-eva-evidence/thermal-aging/`, including save/log, voice manifest
and validation, build/native logs and verification JSON. Preserve this world;
no reserve refill, death command, replay advance or accelerated time is
authorized as part of installation. The owner tests visuals/audio and live FPS;
native tests and packet roundtrips do not establish live multiplayer acceptance.
The branch remains local on `fix/emergency-eva-respawn`.

Native gate passed 236 GameTests and all 231 required cases/reports against
the final source fingerprint. Artifacts are in
`build/architect-reports/bf0b17e3-8caa-4e0c-9c57-c6b52ce037f8/`. The new hot-suit
case passed its real server/NBT/codec/cold/vacuum checks. Before relaunch the
saved world matched its backup byte-for-byte.

The updated Java 21 lab client is running at the title screen (terminal session
42714, fresh owning-checkout bridge heartbeat). Open ORSA Continuity - Phase 6
to continue the saved test. A fresh death-respawn issue plays activation, then
the new condition advisory and shelter guidance; the existing saved kit retains
its consumed reserve and migrated age. Owner visual/audio acceptance remains
pending. No push.


## Ambient bypass and verified primary EVA retirement — 2026-10-05

The owner approved ambient-air oxygen conservation and permanent retirement
when a complete working normal EVA rig replaces the emergency kit. Split the
saved shared budget into oxygen reserve and equipped service power. A fresh
issue has 12,000 ticks of each. Worn service decreases exactly once per tick;
oxygen alone receives the existing gradual exertion/fractional debit. After
forty consecutive safe, sealed ticks, ambient intake opens and stops all
reserve oxygen draw. Any unsafe atmosphere observation or unsealed/unworn rig
closes intake. Removing all emergency pieces pauses service and oxygen, resets
intake stability, and lets exertion/heat recover. Coolant still ages with actual
worn use and degrades after five minutes. Conserved air cannot extend thermal
service, which removes issued pieces and ends protection at service zero.

Atmosphere uses the existing server breathability cache/authority, including
normal non-vacuum conditions, supported/sealed rooms and ordinary dimensions.
No new per-tick room flood fill is added. Closure occurs on the first unsafe
observation from that shared authority; its existing sampling cadence remains.
A cold breathable room retains emergency thermal protection. Oxygen exhaustion
and service exhaustion now have distinct warning text and behavior; an empty
reserve can still leave thermal support active, while primary tanks continue
through the normal air path if present. Oxygen alarms are suppressed under
ambient intake, while low/critical service power remains legible and audible.

The ORSA panel gains one timer row: reserve oxygen time freezes under ambient
intake, service time continues counting. It reports AMBIENT AIR // RESERVE
ISOLATED and keeps the cyan/muted/amber palette, thermal row and automatic
navigation/dialogue stacking. No emergency percentages are added. Item bars
and tooltips report service power. Normal EVA HUD layout and oxygen/module
behavior take over after retirement.

Safe handoff requires every armor slot to be ordinary EVA (including the
existing valid thermal-visor rig), usable ordinary oxygen, full tier-three
protection and no puncture. A partial armor change, empty tank or leaking rig
keeps the emergency issue available. Once verified, the server records HANDOFF
permanently, clears only carried emergency pieces and sends one live notice:
“Primary EVA life support verified. Continuity reserve decommissioned.” Normal
armor/tanks are preserved. Old stored/transferred copies are invalid and cleared
when carried; storage/world chunks are never scanned or loaded for cleanup.
Expiry is also a retirement event. Existing death-drop/carried cleanup remains.

The client queues handoff independently of emergency-equipped presentation,
so its typed ORSA dialogue and existing Piper voice play above the normal suit
HUD. It waits for matching issue, full replacement rig/equipment packets and
an idle visible dialogue channel. Ambient notice waits behind activation,
condition and shelter advisories, only plays while intake is currently open,
and has a 45-second cooldown. Stale issue, death, logout, retirement and unsafe
mode transitions cancel stale notices/audio. Live notices are not replayed by
login or periodic sync. Ambient speech is “Breathable atmosphere detected.
Ambient intake enabled. Reserve oxygen isolated. Emergency thermal support
remains active.” Both new lines use local Piper en_US-amy-medium/ORSA profile;
all 307 prior audio files remain unchanged. Typed/subtitle text matches spoken
text, with EVA spelled E V A in the speech manifest for letter pronunciation.

Oxygen, service, age, heat, intake stability and retirement survive NBT/copy.
Old saves lacking oxygen inherit the exact old spent service balance without
refill or extension. The owner-state packet carries both budgets and retirement
plus a one-shot notice field; periodic/login packets carry no notice. Matching
updated client/server builds are required. The existing authorized shorten
helper now preserves age/heat/debt and can only shorten both clocks; no helper
was dispatched during installation and no test world was reset or advanced.

Full Java 21 build passed 664 unit tests with zero failures/errors/skips, plus
8 existing gate tests. Pre-existing EventBusSubscriber/Gradle deprecation
warnings remain. New pure checks cover unstable air boundaries/immediate closure
and oxygen-versus-power diagnostic priority. Existing native exertion assertions
now check oxygen cost separately from service wear; expiry checks require gear
cleanup. Required native cases cover a real sealed but cold chamber, both clocks,
NBT/legacy migration, exterior closure, conserved oxygen at service expiry, partial
changeover, empty tanks, punctures, valid handoff, regular-gear preservation,
stored-copy invalidation and a real handoff packet codec roundtrip. Native gate
result and runtime handoff are recorded below once complete.

Build and installed smoke jar SHA-256:
`20126becb1693e90ca44c437dfa420f204cef19b344c9458b539069a091bb372`.
The owner confirmed title screen; fresh bridge status confirmed the owning
checkout with no loaded world. Backed up ORSA Continuity - Phase 6 and its log,
then stopped only the verified owning lab Java process. Evidence is in
`build/emergency-eva-evidence/ambient-handoff/`, including backup, build/native
logs and voice manifest/validation. Human visual/audio/FPS and live multiplayer
acceptance remain distinct from headless verification. Branch stays local on
`fix/emergency-eva-respawn`; no push.

The first native attempt failed only the new primary-tank preservation assertion.
Minecraft inventory insertion empties the supplied ItemStack; the fixture had
inspected that consumed input rather than the stored tank. Corrected the fixture
to snapshot the inserted inventory slot and compare its actual count/components
after handoff. No gameplay implementation changed for this failure. Preserved
`native-gate.log` and `native-report-first-attempt.xml`; the final build and
native rerun use separate evidence files.

Final Java 21 build passed 664 unit tests and 8 gate tests. Final native gate
passed 238 GameTests with all 233 required cases/reports verified against
the final source. Artifacts are in
`build/architect-reports/e997ff62-f121-4b11-8737-90e7a0e5e9fb/`. The packaged
new voices match source bytes; no lab bridge or Piper models are bundled.
The final smoke jar hash matches the build hash above. Before relaunch all
71 save files matched the preserved backup byte-for-byte.

The updated owning lab client is running without a loaded world, with a fresh
bridge heartbeat for this checkout (terminal session 74221). Open the preserved
ORSA Continuity - Phase 6 save to continue testing. Visual/audio/FPS and live
multiplayer acceptance remain for the owner. No world command, reset or time
change was issued. The patch is kept local; no push.


## Fifteen-minute service and readable warnings — 2026-10-05

The owner approved fifteen minutes of equipped service power, retaining ten
minutes of reserve oxygen. Activation states both limits: “Emergency EVA active.
Ten-minute oxygen reserve. Fifteen-minute service limit.” The service timer is
hidden until five minutes remain; its row then appears in the existing ORSA
panel. Navigation/dialogue offsets follow the panel height. At one minute the
service timer turns amber and the existing typed warning gains a Piper voice:
“Emergency service power critical. Thermal support expires in one minute or
less.” This urgent service warning can bypass routine diagnostic cooldown;
critical oxygen remains first when both need reporting.

The long initial condition advisory was cut to: “Warning. Suit beyond service
life. Field repairs detected. Cooling and filters degraded. Further failures
possible.” Its measured voice duration is 9.207 seconds, with twelve seconds
reserved for its HUD dialogue before shelter guidance. Activation lasts 6.258
seconds, within its ten-second display. The shared suit dialogue previously
drew only its first three wrapped lines, permanently omitting remaining text.
It now fits up to five lines within available screen height and continues longer
text onto further pages as it types. Display duration allows the full text to
type and remain readable for at least six seconds. Existing ORSA colors, font,
branding and panel width are retained. Human visual acceptance remains pending.

Oxygen capacity is now an explicit 12,000-tick constant, independent of the
18,000-tick service maximum. Ambient bypass, progressive exertion, five-minute
coolant aging, gear retirement and normal EVA handoff remain connected to their
respective clocks. Existing save balances and accumulated age are preserved,
without refilling a partly spent kit. Pre-clock legacy age is inferred from its
original ten-minute shared budget; fresh respawn issues receive fifteen-minute
service and ten-minute oxygen. Guide text and tooltips describe these limits.

Regenerated only the activation and condition voices and added one critical
service voice with local Piper en_US-amy-medium and the existing ORSA profile.
Provenance, manifest, subtitles and shipped inventory are updated; all other
307 prior audio files remain byte-for-byte unchanged. The model stays outside
the jar. Voice peak/duration/hash evidence is in
`build/emergency-eva-evidence/service-15/voice-verification.json`.

The full Java 21 build passed 665 unit tests with zero failures/errors/skips
and 8 existing gate tests. Native cases now distinguish the real ten-minute
oxygen boundary from fifteen-minute thermal/armor expiry and verify exact
legacy balances/age. Pre-existing EventBusSubscriber/Gradle deprecation warnings
remain. The installed smoke jar matches build SHA-256:
`b109d13b01834be18d33cfab9b90d25cf2f7f7b0b1bd82a5b41a60ec62a5f171`.

The owning client was already stopped. Preserved the closed ORSA Continuity -
Phase 6 save and prior log in `build/emergency-eva-evidence/service-15/` before
runtime relaunch. No test-world command, refill, death, reset or time change is
part of installation. Native verification and runtime handoff are recorded
below once complete. The branch stays local; no push.

The first native attempt found two stale ten-minute service expectations: the
stationary sprint fixture still expected 11,900 service ticks rather than
17,900, and the five-minute item bar expected 7/13 rather than 4/13. Updated
both assertions while retaining independent ten-minute oxygen checks. The
actual oxygen/thermal expiry cases passed that attempt. No gameplay code was
changed for these failures. Preserved `native-gate.log` and
`native-report-first-attempt.xml`; final build/native logs are separate.

Final native gate passed all 238 GameTests and all 233 required cases/reports
against the final source fingerprint. Artifacts are in
`build/architect-reports/daded0ab-a0c4-4b4f-9f14-1eb9df7ec328/`. Packaged
voice/subtitle/HUD text matches the source, and all 71 player-save files match
the preserved closed-world backup before launch.

The updated Java 21 lab client is running at the title screen (terminal session
63228, fresh owning-checkout bridge heartbeat, no loaded world). Open the
preserved ORSA Continuity - Phase 6 save to continue testing. The new startup
limits and advisory play on a fresh issue; a saved partly spent kit keeps its
existing balance and does not replay a full-budget promise. Owner visual/audio
and live multiplayer acceptance remain pending. No push.


## PR publication — 2026-10-05

The owner described the latest iteration as “perfect” and authorized pushing
the patch and opening its PR. Published `fix/emergency-eva-respawn` and opened
[PR #102](https://github.com/cjaron03/frozen-dawn/pull/102) into
`feat/maeve-director`. The final gameplay commit remains
`6809233d4de86e7581d62aec0eebe7dd9d283241`; the verified gameplay sources,
assets and jar hash above are unchanged. Live multiplayer remains unverified.

GitHub reported no conflicts (MERGEABLE) but blocked merging at publication.
The target branch requires strict `architectVerify` and `architectMonkey`
commit status checks; neither had been reported on the PR. Its required
approving-review count is zero and conversation resolution is enabled. Local
headless evidence above is recorded in the PR description; missing GitHub
statuses are not represented as passing remote checks. The PR is open for
review, with its verification results, owner feedback and multiplayer gap
documented. No merge was performed as part of the request to open the PR.


## Required merge gates — 2026-10-05

The owner authorized resolving or bypassing the P0 merge blockers. The missing
checks were absent commit statuses: this repository has no configured GitHub
Actions workflows. Retained branch protection and ran both named gates locally
on published revision `4080b72bf5d06a30c28def427228991675228ac9` with Java 21:
`./gradlew test --rerun architectVerify architectMonkey --console=plain`.

Both gates passed: 665 unit tests, 8 gate-harness tests, 238 regression GameTests
with all 233 required cases/reports, and 738 stress-server GameTests containing
the unchanged 500-seed stress matrix plus 238 repeated regressions. XML reports
contain no failures/errors/skips. Regression artifacts:
`build/architect-reports/5ac2c4ab-8e63-41b3-9ef5-315be9a94e07/`. Stress artifacts:
`build/architect-monkey-reports/c70f1a74-e9b1-468f-8750-12dd50e6167d/`.

This checkpoint is a documentation-only follow-up; gameplay source, test
fixtures, seeds, build inputs and the verified jar hash are unchanged. The
required statuses are reported from actual local gate evidence on the final
published revision, after checking that only this document differs from the
tested commit. Final head, status read-back and merge outcome are kept in
`build/emergency-eva-evidence/pr-102-merge/verification.json`. The intended
merge target remains `feat/maeve-director`. The current client/save are untouched
by these separate headless test worlds. Live multiplayer remains unverified.

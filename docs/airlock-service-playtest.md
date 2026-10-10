# ORSA grille and machinery replay

Owning checkout: /Users/jaroncabral/.codex/worktrees/thermal-2-0/minecraft-mod,
branch thermal-2.0. Fresh **Airlock Service Check** generated from Heat Vent Check's
seed metadata only. Previous save preserved with a complete hash-verified backup.
Launch Java 21 ./gradlew runClientLab --console=plain -PfdLabWorld='Airlock Service Check'.
Restore the clickable controls with `/function airlock_check:controls` or
`/function room_heat_check:controls`.

1. At the west base entrance, open and close the inner door. Each click should
   produce one latch/movement or seal/locking cue, including for the clicking player.
   Enter and close the inner door. Outer door must refuse while chamber has air,
   with a restrained lock/hiss cue and silent HUD status.
2. Fill the panel with a backup O2 canister if required. Click to depressurize:
   four seconds of recovery pump, then a brief residual hiss/confirmation. Open
   and close the outer door. Close both doors and cycle again: a distinct refill
   pump, then ready confirmation. Optional panels must not double the pump.
3. Emergency valve has a hardware cue; existing breach airflow/alarm remains the
   emergency cue. Routine cycling must add no TTS. Repair and re-cycle after an
   intentional breach; a broken chamber must not continue pumping.
4. Click Service grille to visit the left wool test room. Its east wall has the
   grille: slats, bolts, lever and dim light while closed. Open it: green when heat
   can leave to colder outdoors. Block the outdoor face (-5,66,0): amber; remove
   that block: green. Close it: dim. The room, oxygen and torch must remain intact.
5. Optionally Fuel both, wait for warming, turn heaters off and compare left-open
   versus right-closed cooling. Redstone must hold fins open without overwriting
   the manual shutter. Existing heat-vent physics replay remains available.

The airlock scene is shifted 24 blocks south; base doorway x1,z24, outer x5,z24;
controller x3,y66,z22. Grille rooms remain at x-8/+8,z0. Setup is guarded and runs
only once in this new world. No active played-world reset or bridge mutation is added.

Audio: nine original procedural mono cues, deterministic source and encoded hashes
in tools/audio_sources/airlock. Three MP3 previews in build/thermal-evidence/
airlock-service/audio-previews use the actual shipped cues. No new runtime dependency.

The former self-click bug was server source-player exclusion without client playback.
Transitions now broadcast once to all nearby players after the actual state change,
retaining the actor on the game event. Redstone uses the same guarded transition;
blocked openings make no false opening sound. Cycle pump segments use one stable
loaded panel anchor per chamber. A segment is shorter than one second and cannot
continue indefinitely after interruption, unloading or death of a client session.

Headless sound-packet delivery and timing are separate from human loudness, tonal
quality and grille readability. Multiplayer playback with actual guest clients
remains a separate acceptance item.


Owner acceptance (October 10, 2026): the three concept sounds were approved,
followed by “ok it works” for the local replay. Actual guest-client playback
remains unverified. Automated gate: 695 unit tests, 339 native GameTests and
334 required cases pass on the exact final combined source.

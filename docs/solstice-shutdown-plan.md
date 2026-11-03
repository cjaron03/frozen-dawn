# SFR-1 Shutdown Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop ramps the burn down to a clean OFF with no wear; a held E-Stop and redstone stay hard stops.

**Architecture:** `FusionPhysics` gains a RAMP_DOWN state that reuses the burn step with the fuel valve closed and the burn following a fixed 20 s line. The controller, menu, screen, loop sound and cue rules treat RAMP_DOWN as a live plasma. Two new one-shots mark the start and end.

**Tech Stack:** NeoForge 1.21.1, Java 21, JUnit 5, GameTests, ffmpeg and oggenc.

**Spec:** `docs/solstice-reactor-design.md`, section "Shutdown (owner, 2026-10-25)".

## Global Constraints

- New enum values go at the end of `State` and `Status`: both are synced to the client by ordinal.
- No emojis. Conventional Commits. No diagnostic logging committed.
- Do not touch the CFU-7 screen's temperature display or the power tier order.

## Review Focus

1. Stop at a burn below 0.5: the loop sound must not jump; the ramp still lasts 20 s.
2. Save and reload mid-ramp: it resumes, with the burn recomputed rather than clamped up to MIN_BURN.
3. Magnet power shortfall during the ramp's low end: still a MAGNET QUENCH, as while burning.
4. Start pressed in the ramp's last second: BURNING at MIN_BURN, not at zero.
5. E-Stop hold released early: nothing is sent.

---

### Task 1: Physics

**Files:** `power/FusionPhysics.java`, `test/.../FusionPhysicsTest.java`

**Interfaces produced:** `State.RAMP_DOWN`; `Status.RAMP_DOWN`, `Status.SHUT_DOWN`; `RAMP_DOWN_SECONDS = 20`; `void eStop()`; `boolean plasma()` (BURNING or RAMP_DOWN). `stop()` while BURNING now starts the ramp; `start()` in RAMP_DOWN resumes BURNING at `max(burn, MIN_BURN)`.

- [x] Write the failing tests:
  - `stopRampsDownToShutDownWithoutDisruption`: from `steady()`, `stop()`. The first step is RAMP_DOWN and not disrupted. Seconds 1-19 stay RAMP_DOWN with falling heat. Second 20 is OFF, SHUT_DOWN, not disrupted.
  - `rampBurnsAndBreedsNoFuel`: `setDeuteriumProgressForTest(0.999)`, stop, then 20 steps. No step uses deuterium, and tritium is unchanged.
  - `startCancelsTheRamp`: stop, 5 steps, `start()`, step. The state is BURNING, heat > 0, and the step is not disrupted.
  - `eStopAndRedstoneDuringTheRampDisrupt`: both cause `assertDisrupts(..., DISRUPTION)`.
  - `aRampSurvivesSaveAndLoad`: stop, 5 steps, save, load into a fresh physics. The state is RAMP_DOWN, and it reaches SHUT_DOWN after exactly 15 more steps.
  - In `everyHardTriggerDisruptsAndWears`, `stopped.stop()` becomes `stopped.eStop()`.
- [x] Run `./gradlew test --tests '*FusionPhysicsTest'`. Expected: compile failure on the missing members.
- [x] Implement. `burn()` branches on RAMP_DOWN as follows:
  - goal: none; `burn = rampFrom * (1 - progress / RAMP_DOWN_SECONDS)`.
  - no fuel, breeding or overdrive accounting.
  - progress shown is `progress / RAMP_DOWN_SECONDS`.
  - at `progress >= RAMP_DOWN_SECONDS`, call `off(SHUT_DOWN)`, set `burn = MIN_BURN` and return `idle`.
  - Hard triggers, the ash rule and overtemperature are shared with BURNING.
  - Save `RampFrom`.
- [x] Run the tests. Expected: all pass.

### Task 2: Cues and alarm

**Files:** `power/SfrAudioCues.java`, `test/.../SfrAudioCuesTest.java`

- [x] Write failing tests:
  - `stopThunksAndShutdownChimes`: BURNING to RAMP_DOWN gives VALVE; RAMP_DOWN to OFF/SHUT_DOWN gives SHUTDOWN.
  - `alarmSoundsDuringTheRampWithoutThePump`.
  - `ashPoisonedFromTheRampSoundsADisruption`.
- [x] Implement: add `Cue.VALVE` and `Cue.SHUTDOWN`; the alarm and the ash rule use `plasma` states.

### Task 3: Controller, menu and screen

**Files:** `block/SfrControllerBlockEntity.java`, `block/SfrControllerMenu.java`, `client/SfrControllerScreen.java`, `lang/en_us.json`, `gameTest/.../SfrGameTest.java`

- [x] Write the failing GameTest `sfrStopRampsDownWithoutWear`: from a burning reactor, `be.stop()`, then 20 `be.second(level)` calls. The state is OFF, the status SHUT_DOWN, and every First Wall is at wear 0.
- [x] Write the failing GameTest `sfrEStopDisruptsAndWears`: `be.eStop()`, then one second. The status is DISRUPTION and the wear is 1.
- [x] Implement:
  - The controller's `burning` checks (power publish, Sound record) use `physics.plasma()`.
  - Add `eStop()`.
  - Menu `ESTOP = 6`.
  - Screen: Stop (x-2, 26 wide) and red E-Stop (x+24, 38 wide) share the Stop row. E-Stop needs a 20-tick hold with a red fill and is active only while `plasma`. The house-load line uses `plasma`.
  - Add lang strings for the RAMP DOWN state, the RAMP DOWN and SHUT DOWN statuses, E-Stop and its tooltip.

### Task 4: Sound

**Files:** `tools/generate_sfr_sounds.sh`, `sounds/blocks/sfr/{valve,shutdown}.ogg`, `sounds.json`, `init/ModSounds.java`, `client/SfrLoopSound.java`, `lang/en_us.json` (subtitles)

- [x] Synthesize:
  - `valve`: about 0.6 s; a low thud with a metallic knock.
  - `shutdown`: about 1.6 s; a soft descending two-tone chime.
- [x] Register `SFR_VALVE` and `SFR_SHUTDOWN` and play them from `playCues`.
- [x] Loop: BURN is wanted while `plasma`. During RAMP_DOWN the volume is `.3 h/h0 + .7h`, where h0 is the heat when the ramp began, so it starts at the burning level and falls to silence. (Ruling: the first draft, `min(.3 + .7h, 1.3h)`, dipped at Stop below half heat; Review Focus 1.)

### Task 5: Gates and docs

- [x] Run `./gradlew test architectVerify --console=plain` in the background. Expected: BUILD SUCCESSFUL.
- [x] Update the playtest doc and the PR body. Commit and push.

# Ending B Welcome Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task, inline. The owner avoids subagents, except the one branch review at the end. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build CH-34. The player who found the truth hears `Welcome home.` too. Ending B plays exactly what Ending A plays, up to and including the INCOMING SIGNAL panel. Then the existing compliance static and line cut in, unchanged. Then black, held for a beat. Then `The End?` alone on a card of its own, the last thing the player sees. Ending A is unchanged.

**Architecture:** A client-only change in `FrozenDawnEndingScreen`. The post-credits timing moves out of the screen into a small pure timeline, so both endings' order and timing unit-test without a screen. A dev-only command plays either ending on demand for the visual pass.

**Tech Stack:** Java 21, NeoForge 1.21.1, JUnit in `src/test`.

**Spec:** [payoff-roadmap.md](payoff-roadmap.md), branch 7. Notion: the payoff branch spec, section 6c. Canon: Canon Hierarchy CH-34, and the Endings section.

**Status:** approved by the owner as written, 2026-11-11, Rulings 1 to 5 included. Built 2026-11-11 in `/Volumes/SSD/Work/frozen-dawn-ending-b-welcome`: the mockups (`docs/art/ending-b-welcome/`), tests, both gates, the owner's visual passes in the save "Ending Welcome Check" (`tools/prepare_ending_b_welcome_playtest.py`) and an Opus branch review passed. Amendments: Ruling 4 holds each ending's credits where they were, so `theCreditsKeepTheirLength` pins each ending's own start rather than equal lengths (Ending B's credits already ran 320 ticks shorter); Ruling 5's command is typed `/fd ending play plain|truth`, since `/fd` is the debug gateway. Added by the owner during the passes, each from an approved mockup, which lifts "Ending A is unchanged" for looks only (its beats, keys and timing hold):
- A glass cockpit restyle (`EndingCockpit`): an edge-to-edge viewport with a thin frame, a T+ clock from the player's play time, a MARS target box, a label-and-value approach HUD with a closing RANGE, and translucent terminal panels sized to their text; `Welcome home.` types in behind a block cursor in both endings.
- A low orbit prelude (`EndingOrbit`, `tools/generate_frozen_earth_textures.py`): ticks 100 to 500 show a blocky frozen Earth whose limb arcs across the window, with a LIFE SUPPORT panel and a TMI burn countdown; black holds to tick 540, when the Mars approach begins. The credits still start at tick 1200, so the music keeps its sync, and Mars has 33 seconds instead of 55.
- Ending B's glitch (`EndingGlitch`): the welcome holds, then over the compliance beat's first 34 ticks, where the separate static box was, it tears, corrupts and reddens into the ACCESS CONTROL panel. The static sound and the 44-tick narration are unchanged.

The review's fixes: the starfield goes black with the Earth for the hold, the corrupt header stays on its panel, and the narration delay and the orbit times are pinned by tests. Deferred: the globe draws about one fill per cell (shade steps would let runs merge); Ending B's cursor disappears when the glitch starts; `showComplianceLine` always equals the ending; and, older than this branch, a resized ending screen never re-adds its return button, though Space, Enter and a click still return.

**Branch:** `feat/ending-b-welcome` in `/Volumes/SSD/Work/frozen-dawn-ending-b-welcome`, cut from `feat/maeve-director` once this plan has merged. The build session starts in that worktree and works there. PR into `feat/maeve-director`. The branch review runs on Opus, because the branch merges into a long-lived branch. It changes no save format and no sync.

## Global Constraints

- **No new player-facing text.** `Welcome home.` (`screen.frozendawn.ending.welcome`), the compliance line (`screen.frozendawn.ending.compliance`) and `The End?` (`screen.frozendawn.ending.truth`) are reused word for word. The compliance static, sound and narration are unchanged.
- **Ending B looks identical to Ending A up to the compliance static.** Same keys, same teal colors, same timings, same panels. The welcome must be sold before it is taken away.
- **Ending A is unchanged,** frame for frame.
- **`The End?` is never dropped,** and stays Ending B only.
- **Rules for every branch.** Never edit MACS. Never open the Notion page "SEALED: Sequel Seed". Cite canon by ID, and keep Notion-only notes out of code, comments, commits and the PR. New Java names use "ending" or "epilogue", never "continuity".

## Rulings

1. **The timeline.** New pure `client/EndingEpilogue` with `List<Beat> beats(boolean truth)`. A `Beat` is a kind (`FADE`, `COLONY`, `SIGNAL`, `COMPLIANCE`, `HOLD`, `END_CARD`) and a length in ticks. The values:
   - Both endings: `FADE` 100, `COLONY` 120 and `SIGNAL` 140, today's `POST_CREDITS_*` values.
   - Ending B adds `COMPLIANCE` 320 (today's `COMPLIANCE_TICKS`, static first), then `HOLD` 40 and `END_CARD` 120.
   - `SIGNAL` always draws `screen.frozendawn.ending.welcome` in Ending A's teal (`0xFFA7FFEA`, `0xFFD9FFF6`, `0xCC44E6A8`). The amber `truth` colors on that panel go.
   - `renderPostCredits`, `getPostCreditsDurationTicks` and `getComplianceStartTick` read the timeline instead of their own sums.
2. **The held black.** `HOLD` is plain black, 2 seconds, with nothing drawn and no sound.
3. **The card.** `END_CARD` draws `The End?` alone, centered on black, with no terminal panel, no header and no frame. It uses the old truth text color (`0xFFFFF0B0`) and fades in over 28 ticks. It does not fade out: it stays drawn after the timeline ends, under the return hint, until the screen closes, so it is the last thing the player sees.
4. **The credits keep their length.** Ending B's epilogue is 160 ticks longer, so its end tick grows by the same amount: `ENDING_COMPLETE_TICKS` becomes `endingCompleteTicks(boolean truth)`, the current value plus 160 for Ending B. The credit roll, the skip window and the music fade keep their timing in both endings.
5. **Dev command.** `/fd debug ending play plain|truth` sends the caller the ending screen through the same payload `RocketLaunchManager` builds, with the ending forced. It does not launch, move the player or change any saved state. Dev builds only, as `/fd payoff` is. Holding Q fast-forwards the credits as it does today.

## Review Focus

1. **Ending A.** Its beats, keys, colors and end tick must be exactly today's. Task 1's unit test pins them.
2. **The welcome in Ending B.** Its first three beats must equal Ending A's in kind, length, key and color. Task 1's unit test holds it.
3. **The last frame.** After the timeline ends, Ending B still shows `The End?`, and Ending A still shows its own last frame. The owner checks both in the pass.
4. **Fast-forward.** Holding Q through the credits must still land on the epilogue's first beat, never inside it. The owner checks it in the pass.
5. **The compliance narration.** It still fires once, `COMPLIANCE_TTS_DELAY_TICKS` (44) into the compliance beat, now counted from the timeline's compliance start. The owner hears it in the pass with the narrator on.

---

### Task 0: Mockup

- [ ] **Step 1: Draw Ending B's epilogue at Minecraft GUI scale** (16 px per block, the Minecraft font, nothing smoothed), as a strip of frames: the INCOMING SIGNAL panel with `Welcome home.`, the compliance static, the ACCESS CONTROL panel, the held black, and the `The End?` card. Put Ending A's signal frame beside Ending B's to show they match.
- [ ] **Step 2: Show it to the owner.** Task 1's render step waits for approval; its timeline and the command don't.

### Task 1: The epilogue

**Files:**
- Create: `src/main/java/com/frozendawn/client/EndingEpilogue.java`, `src/test/java/com/frozendawn/client/EndingEpilogueTest.java`
- Modify:
  - `client/FrozenDawnEndingScreen.java`
  - `world/RocketLaunchManager.java` (expose the payload builder with the ending forced, for the command)
  - the debug command class that holds `/fd debug`, and `src/test/java/com/frozendawn/command/FrozenDawnCommandTreeTest.java`

**Interfaces:**
- Produces:
  - `EndingEpilogue.beats(boolean truth): List<EndingEpilogue.Beat>`
  - `EndingEpilogue.length(boolean truth): int`
  - `EndingEpilogue.endingCompleteTicks(boolean truth): int`

- [ ] **Step 1: Write the failing unit tests** in `EndingEpilogueTest`:
  - `endingAIsUnchanged`: `beats(false)` is `FADE 100, COLONY 120, SIGNAL 140`, and `endingCompleteTicks(false)` is `8 * 60 * 20 + 20 * 20`.
  - `endingBWelcomesFirst`: `beats(true)` starts with the same three beats as `beats(false)`, then `COMPLIANCE 320, HOLD 40, END_CARD 120`.
  - `theCreditsKeepTheirLength`: `endingCompleteTicks(b) - length(b)` is equal for both endings.
  - `theSignalSaysWelcomeInBoth`: the signal beat's key is `screen.frozendawn.ending.welcome` and its colors are Ending A's teal for both endings.
  - `theCardSaysTheEnd`: the `END_CARD` key is `screen.frozendawn.ending.truth`, and its lang value is `The End?`.
- [ ] **Step 2: Run** them. Expected: FAIL.
- [ ] **Step 3: Implement** Rulings 1, 4 and 5: the timeline, the screen reading it, and the command. Add `/fd debug ending` to the command pin test.
- [ ] **Step 4: Run** the unit tests. Expected: PASS.
- [ ] **Step 5: Implement** Rulings 2 and 3, the held black and the card, once the owner has approved Task 0.
- [ ] **Step 6: Commit** `feat: let Ending B hear Welcome home first`.

### Task 2: Playtest world, gate and handback

**Files:**
- Create: `tools/prepare_ending_b_welcome_playtest.py`
- Modify: `docs/payoff-roadmap.md` (branch 7 line)

- [ ] **Step 1: Write the prep script.** Save "Ending Welcome Check": flat, creative, advancement popups off and difficulty preselected, as AGENTS.md requires. Give it chat buttons for `/fd debug ending play plain` and `/fd debug ending play truth`.
- [ ] **Step 2: Run** `./gradlew architectVerify` in the background. Expected: BUILD SUCCESSFUL. Then run `./gradlew architectMonkey`, with the same expectation.
- [ ] **Step 3: Launch** with `python3 tools/prepare_ending_b_welcome_playtest.py`, then `./gradlew runClientLab -PfdLabWorld='Ending Welcome Check' --console=plain` in the background. The owner holds Q through the credits and checks:
  - Ending A, unchanged.
  - Ending B: `Welcome home.` reads as warm as Ending A's, then the compliance static and line, the held black, and `The End?` alone.
  - That `The End?` stays as the last frame.
- [ ] **Step 4: Dispatch the branch review** (Opus, see **Branch**) and fix real findings, each with a failing test first. Update the roadmap's branch 7 line and the checkpoint, then commit, run both gates on the final head and offer the PR.

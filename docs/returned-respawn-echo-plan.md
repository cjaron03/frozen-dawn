# Returned Respawn Echo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task, inline. The owner avoids subagents, except the one branch review at the end. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tie the player's return to the Returned without a word of explanation. First, make the respawn ring and the Thaeven transmission sounds audible: the shipped files are 20 to 37 dB too quiet, so the opening's ring, the boot cue, the replay and every transmission have played close to silent. Then a replaced Hearth resident comes back with a faint, slowed, detuned copy of the player's respawn ring. A Hearth that watched the player die registers the anomaly in its next transmission, at most twice per world. After the Heart falls, neither happens again.

**Architecture:** One asset fix, then two carriers in the existing Hearth systems:

- **The level fix.** The ring is regenerated from its own script at the boot sting's level, and the four Thaeven beds get one flat gain each to the same peak. No code changes; every place that plays them gets louder at once.
- **The ring.** One new positional sound event that plays the shipped respawn ring file, slowed in `sounds.json`. `HearthPopulationManager` plays it inside its replacement path, so the existing erased check stops it with no new code.
- **The notice.** A fourth `ThaevenTransmissionType`, drawn by the existing transmission overlay. A small new world SavedData records who is owed it and how many times it has played.

**Tech Stack:** Java 21, NeoForge 1.21.1, JUnit in `src/test`, GameTests in `src/gameTest`.

**Spec:** [payoff-roadmap.md](payoff-roadmap.md), branch 4. Notion: the payoff branch spec, sections 5a, 5b (the revised version) and 5c. Canon: Canon Hierarchy CH-8 and CH-11, with The Edge §13.2 and §13.13.

**Status:** approved by the owner as written, 2026-11-09, Rulings 0 to 9 included. Nothing is built.

**Branch:** `feat/returned-respawn-echo` in `/Volumes/SSD/Work/frozen-dawn-returned-respawn-echo`, cut from `feat/maeve-director` once this plan has merged. The build session starts in that worktree and works there. PR into `feat/maeve-director`. The branch review runs on Opus: the branch adds a save format (`ReturnNoticeState`) and a client/server transmission type, and it merges into a long-lived branch.

## Global Constraints

- **Existing lexicon only.** `Vel-thae ethven.` already exists in `the_passage.json`, and `...Vel-thae.` is the existing `Vel-thae.` with the hesitation the neutral transmission already uses (`...thae.`). The two sensations are the existing `overlay.frozendawn.thaeven.neutral.warmth` and `.openness` values, word for word.
- **Confusion, never recognition.** No transmission uses `thaeven` (The Edge §13.2: the hive is wrong before it is right; the threshold twist waits for E4, §13.13). No line says "you", "return" or CONTINUITY (CH-11).
- **No text with the ring.** The ring has no subtitle, matching the respawn ring it copies. No chat, no toast.
- **Hearth gates stay as they are.** The notice inherits every gate `HearthTransmissionManager.tryStart` already has, including `isPermanentOrsathae`. Nothing changes in MACS.
- **Rules for every branch.** Never edit MACS. Never open the Notion page "SEALED: Sequel Seed". Cite canon by ID, and keep Notion-only notes out of code, comments, commits and the PR. New Java names, keys and files use "notice", "echo" or "replacement", never "continuity".

## Rulings

0. **The ring's level (owner, 2026-11-09).** `orsa_awakening_ring.ogg` peaks at -37.6 dB, against -8.5 dB for `orsa_boot_sting_replay.ogg` and -8.6 dB for the opening voice. The cause is in `tools/generate_interface_sounds.sh`: ffmpeg's `sine` source defaults to an amplitude of 1/8, and the mix then scales it down again. In game the opening's ring peaks near -50 dB and the boot cue's near -59 dB, so neither is audible.
   - **The fix.** In the ring's block of the script, raise the final gain so the encoded file peaks at -8.5 dB (within 0.5 dB), matching the sting. The mix, partials, timing and length stay as they are. Regenerate only the ring: `oxygen_beep.ogg` and `leak_hiss.ogg` must come out byte for byte unchanged (restore them from git if the encoder rewrites them).
   - **The record.** Rerun `tools/build_audio_inventory.py` so `docs/audio/SHIPPED_AUDIO_INVENTORY.tsv` carries the new hash. Its provenance (interface procedural, Frozen Dawn original) does not change.
   - **The code volumes stay** (opening 0.25, replay 0.15 at pitch 0.8, boot cue 0.0875) unless the owner asks otherwise in Task 0. They were set against a silent file, so the owner hears all three there before anything is built.
   - **The Thaeven transmission beds too (owner, 2026-11-09).** `ui/thaeven_contact.ogg` peaks at -38.0 dB, `thaeven_orsha.ogg` at -39.1, `thaeven_resolve.ogg` at -45.2 and `thaeven_interrupt.ogg` at -28.3. The notice plays inside a transmission, so it inherits them. They have no generator script (they are owner-authored, per `tools/audio_sources/master_architect/SOURCES.md`), so each file is decoded, given one flat gain to peak at -8.5 dB (within 0.5 dB), and re-encoded with `oggenc -q 5`, mono 44.1 kHz as now. Nothing else in the sound changes. Update their hashes in that `SOURCES.md` section and in the inventory. They also play from the Surveyor Lens, the Heart's Cognitive Load and the Heart memory nodes, so those get louder too; the owner hears them in the pass.
1. **5a, which residents ring.** Only `RETURNED` and `HUNTER` (both `ReturnedEntity`). The Mimic and the Architect come back silent: the section is "The Returned share your sound". Owner can widen this.
2. **5a, when it rings.** Only on a replacement: the spawn branch of `HearthPopulationManager.reconcile` reached with a non-null binding (the resident died and its delay ran out). A Hearth's first population, an adoption and `/fd hearth population respawn` (which clears the bindings first) never ring.
3. **5a, the sound.** New event `entity.returned.replacement_ring` in `sounds.json` and `ModSounds`, pointing at the shipped `frozendawn:ui/orsa_awakening_ring` file (mono, so it is positional), category `hostile`, not streamed, `attenuation_distance` 12. Pitch and volume live in `sounds.json` and come from the owner's pick in Task 0; the starting candidates are pitch 0.71 (slower and about six semitones down, between two clean intervals, so it reads detuned) at volume 0.2, quieter than the opening's ring. It plays once, at the new resident's feet, via `level.playSound(null, ...)`. The respawn ring file and its `ui.orsa_awakening_ring` event are not touched.
4. **5b, what counts as witnessed.** At the player's death (`LivingDeathEvent`, lowest priority, not cancelled), a living population resident of the Major Hearth (`isPopulationSource`) is within `WATCH_DISTANCE` 28 and has line of sight to the player. The player is then owed the notice if every one of these holds:
   - the world is not erased;
   - the player is not permanently Orsathae;
   - the player has finished the Architect's assessment and the first transmission at that Hearth;
   - fewer than two notices have played in this world.
5. **5b, the transmission.** New `ThaevenTransmissionType.RETURN_NOTICE(3, 200)`. In `tryStart` it ranks after the first transmission and before `HEARTH_MYTH`: once the first transmission is done, an owed player gets the notice on their next contact. Three beats, in the existing overlay, with the existing label and look:
   - ticks 0 to 59: phrase `Vel-thae ethven.`, no sensation;
   - ticks 60 to 129: phrase `...Vel-thae.`, sensation `[warmth - wrong place - wrong time - something remembered]`;
   - ticks 130 to 200: no phrase, sensation `[a question that has no word for question]`.
   New keys `overlay.frozendawn.thaeven.notice.died` and `.notice.word`; the sensations reuse the neutral keys. The overlay's `TransmissionBeat` gains a null-sensation case for the first beat.
6. **5b, spending it.** Completing the notice clears that player's debt, adds one to the world count and arms `awaitingContactExit` as the first transmission does, so the myth waits until the player leaves and comes back. When the count reaches two, every remaining debt clears. An interrupted notice stays owed. A replay (`/fd hearth transmission replay`) never plays or spends it.
7. **5b, world state.** New `homo/ReturnNoticeState` world SavedData (`frozendawn_return_notice`): `int shown` and a `Set<UUID> owed`. It is separate from `ReturnedHearthSavedData` so the Hearth ledger's format does not change. Pure decisions live in static methods so they unit-test: `owes(boolean erased, boolean permanentOrsathae, boolean firstTransmissionDone, boolean witnessed, int shown)` and `spend(int shown)`.
8. **5c.** Needs no new check. The ring sits inside `reconcile`, which returns early when erased, and transmissions cancel once erased. `ReturnNoticeState.owes` is false when erased, so no debt is made after the Heart, and a debt from before it can never play.
9. **Dev commands.** `/fd hearth notice` prints `ReturnNoticeState` (shown count, owed names). `/fd hearth notice owe` owes the notice to the caller. `/fd hearth notice reset confirm` clears both. `owe` and `reset` are dev builds only, as `/fd payoff` is. `HearthPopulationManager.statusLine()` gains `rings=<n>`.

## Review Focus

1. **A first population or a debug respawn.** Neither may ring; only a real replacement does. Task 1's unit test holds `ringsOnSpawn`, and its GameTest holds the debug path.
2. **The Heart falls with a debt open.** A player owed the notice when Maeve is erased must never see it, and the ring must never play in an erased world. Task 1's and Task 2's GameTests hold both.
3. **The third witnessed death.** After two notices, no player is owed again, even a new one. Task 2's GameTest holds it.
4. **A death the Hearth could not see.** A resident behind a wall or 29 blocks away witnesses nothing. Task 2's GameTest holds the boolean; the owner checks one blocked death in the pass.
5. **The first transmission not yet heard.** A player who dies before their first transmission is not owed, so they never get the notice before the hive has spoken to them. Task 2's unit test holds it.

---

### Task 0: The ring's level and the sound prototype

**Files:**
- Modify:
  - `tools/generate_interface_sounds.sh` (the ring's final gain)
  - `src/main/resources/assets/frozendawn/sounds/ui/orsa_awakening_ring.ogg`
  - `src/main/resources/assets/frozendawn/sounds/ui/thaeven_{contact,orsha,resolve,interrupt}.ogg`
  - `tools/audio_sources/master_architect/SOURCES.md` (the four Thaeven hashes)
  - `docs/audio/SHIPPED_AUDIO_INVENTORY.tsv`

- [ ] **Step 1: Fix the levels** per Ruling 0: the ring from its script, the four Thaeven beds by flat gain.
- [ ] **Step 2: Verify** each of the five files with `ffmpeg -i <file> -af volumedetect -f null -`. Expected: `max_volume` between -9.0 and -8.0 dB, the length unchanged to 0.05 s, and `git diff --stat` showing only the files above.
- [ ] **Step 3: Render the previews** from the fixed files into the scratchpad, at the levels the game plays them, resampled the way Minecraft's pitch does it (slower and lower together):
  - the opening at volume 0.25, the replay at pitch 0.8 and volume 0.15, and the boot cue at volume 0.0875;
  - the Returned's ring at pitch 0.67, 0.71 and 0.79, each at volume 0.2;
  - one transmission's sounds in order, at their volumes in `ThaevenTransmissionOverlay` (contact 0.88, orsha 0.82, resolve 0.72, interrupt 0.8), before and after.
- [ ] **Step 4: Play them for the owner.** Record the echo's pick in Ruling 3, and any change to the existing levels in Ruling 0. Task 1 waits for it; Task 2 doesn't. The transmission reuses the existing overlay as it is, so it needs no mockup.
- [ ] **Step 5: Commit** `fix: bring the respawn ring and the Thaeven beds up to the boot sting's level`.

### Task 1: The replacement ring

**Files:**
- Create: `src/test/java/com/frozendawn/homo/ReturnedEchoTest.java`, `src/gameTest/java/com/frozendawn/homo/ReturnedEchoGameTest.java`
- Modify: `homo/HearthPopulationPolicy.java`, `homo/HearthPopulationManager.java`, `init/ModSounds.java`, `assets/frozendawn/sounds.json`

**Interfaces:**
- Produces:
  - `HearthPopulationPolicy.ringsOnSpawn(HearthPopulationRole role, boolean replacement): boolean`
  - `HearthPopulationManager.replacementRings(): long`

- [ ] **Step 1: Write the failing unit test** `onlyAReplacedReturnedRings`: true for `RETURNED` and `HUNTER` with `replacement` true; false for every role with `replacement` false, and for `MIMIC` and `ARCHITECT` either way. And `theRingIsTheRespawnRing`: `sounds.json`'s `entity.returned.replacement_ring` names `frozendawn:ui/orsa_awakening_ring`, is not streamed, and has the Ruling 3 pitch and volume.
- [ ] **Step 2: Run** them. Expected: FAIL.
- [ ] **Step 3: Implement** Rulings 1 to 3: the policy method, the sound event, and in `reconcile` one call after `bindPopulationResident` succeeds, with `replacement = binding != null`. Count each ring for `statusLine`.
- [ ] **Step 4: Run** the unit tests. Expected: PASS.
- [ ] **Step 5: Write the GameTests** in `ReturnedEchoGameTest`, batch `returnedecho`:
  - `noRingInAnErasedWorld`: with the saved erased flag set, `reconcileNow` returns 0 and `replacementRings()` is unchanged. Clear the flag after.
  - `aDebugRespawnDoesNotRing`: `respawnForDebug` on a world with no loaded Major Hearth leaves `replacementRings()` unchanged.
- [ ] **Step 6: Run** `./gradlew gameTestQuick -PfdTests=returnedecho`. Expected: PASS.
- [ ] **Step 7: Commit** `feat: ring the respawn tone when a Returned is replaced`.

### Task 2: The notice

**Files:**
- Create: `src/main/java/com/frozendawn/homo/ReturnNoticeState.java`
- Modify:
  - `homo/ThaevenTransmissionType.java`
  - `homo/HearthTransmissionManager.java` (`tryStart` ranking, `complete`)
  - `client/ThaevenTransmissionOverlay.java` (the `RETURN_NOTICE` beats, null sensation)
  - `command/FrozenDawnHearthCommand.java`
  - `assets/frozendawn/lang/en_us.json`
  - `src/test/java/com/frozendawn/command/FrozenDawnCommandTreeTest.java`
  - Tests: `ReturnedEchoTest`, `ReturnedEchoGameTest`

**Interfaces:**
- Consumes: `HearthTransmissionManager.isPopulationSource` (make it package-private if it is not reachable).
- Produces:
  - `ReturnNoticeState.owes(boolean erased, boolean permanentOrsathae, boolean firstTransmissionDone, boolean witnessed, int shown): boolean`
  - `ReturnNoticeState.onDeath(ServerPlayer)` and a package-private `onDeath(ServerPlayer, boolean witnessed)` for tests
  - `ReturnNoticeState.isOwed(MinecraftServer, UUID): boolean`
  - `ReturnNoticeState.spend(MinecraftServer, UUID)`

- [ ] **Step 1: Write the failing unit tests.**
  - `aNoticeNeedsEveryGate`: `owes` is true only with not erased, not permanent, first transmission done, witnessed, and `shown` below 2. Each gate turned the other way makes it false.
  - `noticeLinesAreExistingWords`: `notice.died` is `Vel-thae ethven.` and appears in `the_passage.json`; `notice.word` is `...Vel-thae.`; neither value contains `thaeven`, `you` or `return`.
  - `theNoticeRanksBeforeTheMyth`: `RETURN_NOTICE` has network id 3 and duration 200, and `fromNetworkId(3)` returns it.
- [ ] **Step 2: Run** them. Expected: FAIL.
- [ ] **Step 3: Implement** Rulings 4 to 7 and 9. Add the `/fd hearth notice` branch to the command pin test.
- [ ] **Step 4: Run** the unit tests. Expected: PASS.
- [ ] **Step 5: Write the GameTests** in `ReturnedEchoGameTest`, with two mock players, A and B, calling `onDeath(player, witnessed)` and `spend` directly. Reset the state before and after.
  - `aWitnessedDeathOwesTheNotice`: A's witnessed death owes A; an unwitnessed death of B owes nothing.
  - `twoNoticesPerWorld`: spend A, owe and spend B, then A's next witnessed death owes nothing, and any open debt is cleared.
  - `noNoticeAfterTheHeart`: owe A, set the saved erased flag, and `tryStart` refuses; a witnessed death in the erased world owes nothing.
- [ ] **Step 6: Run** `./gradlew gameTestQuick -PfdTests=returnedecho`. Expected: PASS.
- [ ] **Step 7: Commit** `feat: let the Hearth notice a witnessed death`.

### Task 3: Playtest world, gate and handback

**Files:**
- Create: `tools/prepare_returned_respawn_echo_playtest.py`
- Modify: `docs/payoff-roadmap.md` (branch 4 line)

- [ ] **Step 1: Write the prep script.** Save "Returned Echo Check": survival, advancement popups off and difficulty preselected, as AGENTS.md requires, with the Major Hearth force-selected near spawn and its first transmission done, the way `prepare_payoff_reveal_playtest.py` sets up its Hearth. Give it chat buttons for `/fd hearth population`, `/fd hearth notice`, `/fd hearth notice owe` and `/fd hearth notice reset confirm`.
- [ ] **Step 2: Run** `./gradlew architectVerify` in the background. Expected: BUILD SUCCESSFUL. Then run `./gradlew architectMonkey`, with the same expectation.
- [ ] **Step 3: Launch** with `python3 tools/prepare_returned_respawn_echo_playtest.py`, then `./gradlew runClientLab -PfdLabWorld='Returned Echo Check' --console=plain` in the background. The owner checks:
  - The opening (`/fd payoff opening`) and a respawn: the ring is now heard in both, the boot cue's quietly.
  - A first transmission (`/fd hearth transmission replay`): its contact, Orsha and resolve sounds are now heard, and breaking contact plays the interrupt.
  - Killing a Returned and waiting out its 60 seconds: the replacement comes back with the slowed ring, heard only up close.
  - Dying in sight of the Hearth, respawning and walking back: the notice plays on contact.
  - A death behind cover, which owes nothing.
  - That the notice reads as confusion, not recognition.
- [ ] **Step 4: Dispatch the branch review** (Opus, see **Branch**) and fix real findings, each with a failing test first. Update the roadmap's branch 4 line, then commit and offer the PR.

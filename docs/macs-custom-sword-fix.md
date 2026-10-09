# MACS custom sword recognition — October 7, 2026

Owning checkout: `/Users/jaroncabral/.codex/worktrees/macs-custom-swords/minecraft-mod`.
Branch: `fix/macs-custom-sword-recognition`.
Baseline: freshly fetched `origin/feat/maeve-director`, `98165da84807f61aef57ef95218ffc092eb47a0b` (emergency EVA merge).

## Development client

The owner requested Gradle client launches for future iteration. Use Java 21 from this owning checkout:

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew runClient --console=plain
```

This uses the checkout's `run/` directory and development dependencies. `runClientLab` is the separate Gradle lab variant with a command bridge and `run-lab/`; see `lab-command-bridge.md` when that bridge is requested. The Gradle terminal is a log, not an in-game command console. A Java change needs a new client process. Preserve any open world before restarting.

## Defect and change

The playtest build classified Acheronite Sword damage as `WITNESSED_DAMAGE_NON_SWORD_MELEE`, contradicting an existing sword preference. Both `AcheroniteSwordItem` and its Soul-Harvest Blade subclass lacked the vanilla sword tag.

Both items now append to `minecraft:swords`. ObservationCollector accepts either that tag or a `SwordItem` subclass, matching the existing Remnant classifier. The same collector handles damage and fully blocked Architect shield contacts. Witness eligibility, evidence weights, encounter deduplication, confidence thresholds and saved history retain their existing behavior.

## Verification and evidence

The first baseline gate reproduced incorrect Acheronite classification through actual raised-shield contact. Its initial damage fixture also exposed a test timing error: clock rewinding caused vanilla sword hits to share one encounter. That fixture was corrected to advance time monotonically before the fixed run. Do not count that initial damage failure as evidence of the product defect.

Native regressions exercise six vanilla swords, both custom swords, actual damage, blocked contacts, provenance, encounter deduplication, axe contradictions and arrows while holding a sword. The registry smoke test checks both custom tag memberships and preservation of vanilla membership. Both new native regressions are required by `config/architect-required-tests.txt`.

Verification command: `./gradlew architectVerify --console=plain` with Java 21.
Evidence folder: `build/custom-sword-evidence/` (ignored local evidence).
Final results:

- Full build and all 665 JUnit tests passed; the gate harness's 8 Python tests, 64 fixture checks, facade budget and lab packaging checks passed.
- All 240 native GameTests passed, including both custom sword regressions and the loaded sword tag check.
- The manifest initially misspelled the blocked-contact regression name. After correcting it, the verifier validated all 235 required cases, decision trace hashes and current source fingerprints against the completed successful run. Since the normal task creates a fresh artifact UUID each invocation, revalidation pinned the original trace directory using the preserved `fixed/reuse-native-report.gradle` init script and excluded `runGameTestServer`. No unchanged native tests were repeated.
- Source fingerprint: `9a1cbbe3e3bf07e7de8855a98dadd53ae27895d4f9d1276e2b128d00ba9ad154`.
- Release jar: `build/libs/frozendawn-2.0.1-alpha.jar`.
- Jar SHA-256: `db933b056221cf5c60146cb148d7a5d4c9ded729237b9676cba8dcdb2a002a91`.
- The verify skill's smoke-test Frozen Dawn slot was updated and its SHA-256 matched. Dependency jars were preserved.
- The fixed development client was launched through `./gradlew runClient --console=plain`, Java 21, agent terminal session `21399`. Renderer startup completed, including texture atlases, sound engine and Patchouli resource loading. Log: `build/custom-sword-evidence/client-console.log`. The owner subsequently opened the prepared world and completed the live damage and shield checks described below.

To revalidate the preserved report without rerunning its world:

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew architectVerify -x runGameTestServer --init-script build/custom-sword-evidence/fixed/reuse-native-report.gradle --console=plain
```

For a new native test run, use the ordinary `architectVerify` command. The init script is local evidence, not a build configuration change.

The multiplayer playtest world and pinned server jar are separate from this development checkout. This fix does not rewrite already recorded beliefs. Native GameTests and the live singleplayer check establish custom sword recognition; multiplayer verification remains pending.


## Prepared visual world

Select **MACS Custom Sword Check** in the current Gradle `runClient` Singleplayer list. If the list was open during installation, return to the title screen and reopen Singleplayer. World folder: `run/saves/MACS Custom Sword Check`.

The clean template was saved at day 102 / late Phase 6, Normal difficulty, with Maeve ACTIVE and zero player profiles/beliefs. The played save now retains the actual training history. It contains a protected bedrock arena with a verified floor, roof, spawn at (8,65,8), and a geothermal core. On first entry it equips ordinary EVA, twelve fresh Mk3 oxygen tanks, both custom swords, an iron sword, axe, bow, arrows, food and a shield. Survival resistance, regeneration and night vision protect the player during this recognition check. Vanilla natural spawning and terrain griefing are disabled. The mod's separate spawning systems are unchanged; an inert archived Architect also occupies the local natural Architect density slot.

Open chat and click **Begin damage recognition check**. If the introductory prompt is missed, `/function qsword:status` shows it again. Hotbar 1 is iron, 2 Acheronite, 3 Soul-Harvest, 4 axe, and 5 bow. Each prompt requires a real player attack; advancements advance the QA stage, not the Maeve belief. Click **Record Maeve dump** after each hit to retain the actual evidence in the client log.

1. Iron, Acheronite, then Soul-Harvest against the same active witness: expect sword classification, confidence 0.20, and zero contradictions. Holding or switching alone is not evidence. The repeated custom hits verify freshness without extra same-encounter confidence.
2. Three actual custom-sword training hits, alternating Acheronite / Soul-Harvest / Acheronite, each separated by a 620-tick empty gap: expect confidence 0.80 from four supporting encounters. Click the empty-gap sprint link or wait 31 unpaused seconds. No combat is accelerated.
3. Click **Start normal Architect shield round**. The new executor has normal AI and no forced shield or injected belief. Wait for the shield to rise before attacking. Capture an Acheronite and a Soul-Harvest frontal block: expect `WITNESSED_SHIELD_BLOCK_SWORD`, without sword contradictions. Do not use the axe before capturing both. This stage requires a human visual check; its Continue button does not certify a pass.
4. The optional controls provide an axe witness and an arrow witness in separate encounters, allowing independent contradiction contributions. Capture both dumps. Emergency stop is `/function qsword:finish`.

Finished actors are moved alive to a separate archive pad with NoAI/NoGravity. They cannot continue observing. No actor cleanup kills, belief resets, synthetic damage or confidence edits are used. Completed history is preserved; the world does not overwrite a finished attempt.

Preparation used a separate temporary loopback-only native NeoForge server with the fixed jar. All 29 datapack functions and five advancement definitions loaded without parsing errors; native commands verified the arena, phase and empty active store. The server was cleanly saved and stopped before installation. The closed delivery copy has zero forced chunks (the remaining preparation catch-up ticket was cleared only in that copy). Evidence: `build/custom-sword-world-prep/console.log` and `build/custom-sword-evidence/world-preparation.json`. A clean repeat template is preserved at `build/custom-sword-evidence/world-template`; copy it to a new save name only after preserving the played attempt.

Generator: `tools/prepare_custom_sword_playtest.py`. It creates fresh metadata and the datapack from a closed level.dat, refuses to overwrite a save, and copies no source chunks or tactical data. It requires native prebuild of `qsword:build` before a player joins. The installed world has already completed that step.

## Live singleplayer checkpoint

The owner entered this world through Gradle `runClient`, exercised the first-entry EVA kit, and performed actual iron, Acheronite and Soul-Harvest attacks. The owner reported that the check looked good, then completed the requested Soul-Harvest shield contact. Read-only log and paused-save inspection confirmed:

- Both custom swords recorded `WITNESSED_DAMAGE_SWORD` with positive support in separate training encounters. Training reached confidence 0.80 from four credited encounters, with zero sword contradictions.
- A new normal Architect selected `GUARD_SWORD` from frozen historical sword confidence 0.80; the shield was not injected by the datapack.
- Actual Acheronite frontal blocking recorded `WITNESSED_SHIELD_BLOCK_SWORD weapon=frozendawn:acheronite_sword` at tick 6554, adding 0.20 support.
- Actual Soul-Harvest frontal blocking recorded `WITNESSED_SHIELD_BLOCK_SWORD weapon=frozendawn:soul_harvest_blade` at ticks 6888 and 6978. The final same-encounter verification has weight 0.00, as expected after the Acheronite block received that encounter's credit.
- The paused saved store contains sword confidence 1.00, evidence 5 and contradictions 0. The QA scoreboard is stage 30, round 3. The optional live axe and arrow controls have not been completed at this checkpoint; their native regressions passed.

The training witness moved and retreated during the replay despite its requested attribute overrides. This did not invalidate the witnessed damage, and finished actors were archived alive by the agreed functions. The arena is protected and is a recognition fixture, not evidence of encounter balance.

Evidence snapshot: `build/custom-sword-evidence/live-2026-10-07-custom-swords/`, containing the client log, paused saved Maeve and scoreboard data, and a hashed manifest. This is a checkpoint snapshot, not a closed-world backup. The active played world remains intact; no automatic continuation, reset or restart was performed. Multiplayer verification and upgrading the separate pinned playtest server remain pending.

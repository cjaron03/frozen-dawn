# Maeve Slice 4 — observed world model

Slice branch: `feat/maeve-world-model`. PR target: `feat/maeve-director`, following the merged Slice 3 (`cb4fc17`, PR #89). Contract: [Maeve Director — Source of Truth](https://www.notion.so/39d7cfaa890181c1bad4f6babad80880), §§9.3-MVP, 9.3a, 9.13a, 9.16–9.19 and 12.

Slice 4 makes the existing positioning commitment resolve to a location an Architect actually saw the player cross. A sealed entrance stays in memory until an Architect approaches and sees the obstruction. It then inspects the wall with the existing thinking pose, records the discovery, and returns to local planning. Room classification, structure graphs, missions, attention slots, and §10 capabilities remain outside this slice.

## Observation rules

An eligible ordinary or Master Architect samples its currently targeted Survival/Adventure player every 10 ticks. Selection of a target only identifies whom to check: the observer still needs its own current line of sight, the same dimension, a living player, and distance at most 48 blocks. Mind copies, Aggregate children, removed/dead observers, and NoAI actors do not report presence. Rays reject unloaded chunks before checking geometry.

An ACCESS_POINT requires the same observer to see both ends of a covered/open-sky transition in consecutive samples, separated by at most 10 ticks, four blocks of displacement, and one block vertically. Both endpoints must still be visible, and the old position's cover classification must still agree. Missing sight, a skipped sample, a distant teleport, or a server reload breaks continuity. Standing still while someone changes the roof does not create a crossing. This describes an observed sky boundary; it does not identify doors or rooms.

A covered-to-open crossing adds support for `RETREAT_BEARING_N/E/S/W`, relative to a centroid estimated from the latest 16 distinct witnessed covered positions in that dimension. Covered observations more than 32 blocks from the previous centroid start a new local estimate. Inward crossings record access locations without asserting a retreat preference. An outward crossing along another bearing contradicts existing competing bearing beliefs. Duplicate witnesses still share the existing per-player encounter and contribution limit.

DANGER_ZONE records an observer's own fatal damage or one effective hit of at least 20% of its maximum health, in an eligible player context. A currently visible player or a presence sample within 200 ticks establishes that context; the damage event itself establishes the danger. No player-health polling is used. HEAT_SOURCE starts from the existing lit-heater registry, then requires a local sight ray within 24 blocks. A registry removal does not erase an old observation. UNKNOWN is derived from 16-block cells without retained local samples, including four probes around each estimated centroid; a sampled cell means some observation occurred there, not that its entire contents are known.

## Confidence and behavior

Ordinary bearing contributions keep Slice 1's +0.20 support, −0.35 contradiction, [0,1] clamp, and one support/contradiction per pattern per encounter. Contact expires after 600 unobserved ticks. Confidence uses overworld game time, with decay beginning after 20 unverified days and a 20-day half-life. Point observations also add 0.20 at most once per encounter, refresh recency without earning duplicate confidence, and use the same decay window.

The prior encounter's bearing confidence must meet the existing 0.75 threshold. Resolve to the nearest remembered OPEN access point matching that bearing, within 32 blocks of the estimated centroid and 24 blocks of the Architect. A bearing candidate cannot bypass this resolution. The cheapest recoverable candidate wins alongside the existing ranged and recovery candidates. One commitment per encounter and contradiction cooldown still apply.

Spatial approach has a 240-tick budget. The local executor uses loaded, level, walkable cells within 24 blocks of its goal, evaluates at most 80 D* nodes per tick, and considers at most eight remembered nearby danger points. Walking within three blocks of a danger point adds cost, encouraging a short detour. These positioning routes do not build, breach, climb or jump; an unsafe/unreachable route releases to the normal Architect. This conservative first implementation can fall back on uneven ground. Once reached, the point is held for the existing 400 ticks with the thinking cue and close-range defense.

Discovery is checked every 10 ticks while executing a spatial commitment. Within four blocks of the remembered access point, the observer checks at most nine positions along the recorded crossing, at foot and head height. A solid obstruction must be directly visible. World edits alone do not update memory, and pathfinding reads do not count as discovery evidence.

Direct discovery reduces the old OPEN confidence by 0.65, replaces the point's state with a fresh BLOCKED observation at 0.90 confidence, retains bounded old/new provenance, and contradicts the associated bearing by 0.65 if that encounter's contradiction contribution is still unused. An earlier ordinary contradiction still consumes that pattern's single contradiction slot. The new obstruction remains recorded either way. The Architect looks at the discovered wall for 60 ticks, then releases with `DISCOVERY_REPLAN`; the bearing is ineligible next encounter. Discovery does not refresh player contact or manufacture a new encounter. Only a new witnessed crossing can reopen the remembered point.

## Persistence, multiplayer and bounds

The existing overworld Maeve SavedData becomes version 3. Versions 1 and 2 load with an empty spatial model; unrelated violation memory is unchanged. Location knowledge is inside each player's profile and keyed by dimension, so another player's observations cannot become that player's beliefs. Active movement never resumes across reload; encounter contribution flags, used commitment state, cooldowns, points and confidence do. Transient observer movement samples are discarded.

ERASED releases all spatial contents alongside the existing tactical store, stops the active executor, and writes no former points. Debug reversal starts empty. A complete external backup restores the state in that backup. There is no permanent in-world belief archive.

Bounds per profile: 64 labelled points, eight provenance entries per point, 128 sampled cells, four centroid dimensions, 16 covered positions per dimension, and eight transient observer samples. Existing limits remain 128 profiles, 16 beliefs per profile and eight contact UUIDs. Point/cell eviction uses oldest observation time with a stable key tie-break. Heater lookup uses the chunk index, examines at most 64 registered positions and returns at most 16; no observation or spatial route loads chunks. The facade remains below the enforced 300-line limit.

## Diagnostics and verification

`/fd maeve dump` now includes centroid estimates, labelled points, state, decayed confidence, counts, age, retained observer/encounter/action provenance, replacement evidence, and derived unknowns. `/fd maeve explain RETREAT_BEARING_E` explains the bearing and its contradiction rule. Operator permissions and explicit console subject requirements remain unchanged.

Required automated command:

```sh
./gradlew architectVerify architectMonkey --console=plain
```

New unit cases cover bearing resolution, blocked replacement and reopening, contribution deduplication across reload, decay, deterministic bounds, malformed point rejection, v2/v3 compatibility, complete erasure, and delayed discovery without encounter reset. New required GameTests cover real NeoForge presence/damage events, occlusion and continuity, duplicate observers, two isolated players, creative exclusion, reload, actual entity movement to an entrance, unseen sealing and physical discovery, inspection expiry, cooldown, heater visibility, fatal evidence, danger detours, and unloaded route rejection. The replay generator's functions are loaded by Minecraft during GameTests; a required case also verifies that the real practice booth can see a crossing.

Automated results on 2026-09-17: 572 unit tests, 82 regression GameTests (77 required), 582 stress-run GameTests (500 required seeded cases), and eight gate-harness tests all passed. The shared geometry fixtures and seeds were retained. Build fingerprint: `67be2b22cf0b756b695fce6a3522d636d6bbd8869b1c288bf87afbd4fe816019`.

Automated evidence is necessary but does not satisfy §9.19's live exit criterion. The live gate was completed on 2026-09-17; the accepted replay and direct dumps are recorded below.

## Live shelter replay

`tools/prepare_maeve_world_playtest.py` copies a CLOSED QA world to **Maeve Shelter Encounter**, refusing to overwrite a destination. It only replaces the copy's QA pack. Setup erases tactical memory through normal debug lifecycle commands; it never writes confidence or fabricated evidence. Activating Phase 6 late and moving back to Phase 0 keeps activation latched while removing environmental pressure from this controlled test.

1. Open **Maeve Shelter Encounter**, run `/function maeve_world:setup`, stand on blue, and follow the three-second prompt to walk through the east doorway onto gold. Stop briefly on gold. The protected, AI-enabled observer must actually witness both sides.
2. After each of five crossings, click the fast-forward link (`/tick sprint 640t`), then click the next crossing link (`/function maeve_world:practice`). No practice starts automatically during sprinting. The native sprint advances the unchanged 600-tick encounter gap; it never accelerates a live encounter. At the fifth crossing, click the direct `/fd maeve dump` link to inspect the accepted observations. Minecraft suppresses command feedback inside functions, so the function itself cannot capture that dump.
3. When practice completes, run `/function maeve_world:open`. Stay inside without attacks or recovery use during this comparison. Watch where the Architect positions itself. At 450 ticks (22.5 seconds) it pauses while the learned position is still held and exports the actor trace. Click the direct `/fd maeve dump` link. This prevents the later resumed attack from pushing the player through another exit and invalidating the following comparison; production hold duration remains 400 ticks.
4. Run `/function maeve_world:sealed`. This dismisses the old actor, seals the east doorway with stone, offers a direct pre-discovery dump link, and starts the quiet gap. Click `/tick sprint 640t`, then run `/function maeve_world:blocked`. Watch from the marked outside position. This replay pauses and exports the actor trace after 30 seconds. Click its direct dump link to record the discovery explanation.
5. Describe the approach, position held, inspection of the wall, and what it did afterward. Preserve `run-lab/logs/latest.log` and the actor export paths printed by `/fd architect dump`.

The completion counter only measures the exercise; it does not prove acceptance by Maeve. Before declaring the gate passed, inspect five distinct supported encounters, the selected real ACCESS_POINT, its unchanged OPEN state immediately after sealing, an actual `OBSERVED_ACCESS_OBSTRUCTION` with observer and wall position, reduced bearing confidence, the 60-tick inspection and subsequent release. If normal play does not make those events visible, fix that gap before merging. Current live result: **passed on 2026-09-17**. The open hold was visible, and the accepted sealed replay below proves actual discovery, updated knowledge and subsequent recovery.

### First shelter replay and fixture correction (2026-09-17)

The player reported that it waited at the entrance, thought for a while, then attacked, while the sealed round immediately pursued them. Open trace `cb6eb9cd-5ad7-40a6-94ef-b365890609ef` shows approach followed by OBSERVE at the east access location from run tick 118 through approximately 518, matching the 400-tick hold. At tick 596, four ticks before the old 600-tick pause, the resumed attack had pushed the player out through the west exit. The direct dump records that witnessed crossing at game tick 139470: east confidence fell from 1.00 to 0.65, and east became ineligible in the next encounter.

Sealed trace `f8fe6e6b-5e1b-4f96-9e77-5ef3bf8e0aeb` consequently contains ordinary pursuit, not a failed spatial directive. The dump confirms `NO_ELIGIBLE_SAFE_POSITION`, east confidence 0.65, and east in the current cooldown. Both east access points remained OPEN: the unseen seal did not magically invalidate them. This is useful evidence of retained knowledge and cooldown, but it does not pass the discovery exit criterion.

The fixture now ends future open comparisons earlier, reports invalid command stages instead of silently doing nothing, and prompts for direct dumps. Invoking `blocked` again after the sealed round ended was the reported no-op; it had already run once and reached stage 6. `/function maeve_world:retry_blocked` preserves the current history, reopens the doorway for one genuine extra observed crossing, dismisses the observer, seals it again and starts a fresh quiet gap. For this recorded 0.65-confidence state, one additional encounter can raise east confidence to 0.85. After `/tick sprint 640t`, inspect `/fd maeve dump` before launching `blocked` again. No thresholds, contribution values or cooldowns are changed. If later retries have different history, inspect their actual confidence instead of assuming one crossing is enough.

Local evidence: `build/maeve-slice4-evidence/first-live/` contains the captured log, both traces and their assessment. The test world contains only its normal tactical save and actor trace exports; no permanent belief archive is added inside it.

### Accepted sealed-side discovery (2026-09-17)

The user reported: “so it looked a the wall, thinked for a bit, then instanlty started attacking me”. This was an informed spatial replay; Slice 4 requires visible discovery, while the separate Slice 3 blind gate was completed before this branch began.

One additional ordinary outward crossing, witnessed at game tick 157480 by observer `94ec6a26-be19-44b2-8cae-649c1cf4464b`, raised east confidence from 0.65 to 0.85 without injecting a belief or resetting history. The pre-encounter dump retained six supporting encounters, cleared east cooldown, and still described the sealed east access at `(309,101,308)` as OPEN with confidence 0.80. The quiet gap exceeded the unchanged 600 ticks.

Accepted run: `e4564dff-dfc7-4e5a-bcbc-80379f6625ab`; actor `a8f9b532-f35c-4b4d-a5f4-af19ef438c0e`; start game tick 159480; 600 recorded ticks, 128 retained events, zero dropped. Trace SHA-256: `19c0bbb47fec46466abf485ae62b00218bb8969407ff2f503091404df100bc58`. The live source fingerprint matches the automated build above; the fixture-only correction did not change the production jar.

- At game tick 159520, it chose `WATCH_ACCESS_POINT`, east confidence 0.85, recovery cost 13, goal `(309,101,308)`, based on the earlier witnessed crossing `(307,101,308) -> (309,101,308)`.
- At tick 159580 (run tick 100), `MAEVE_ACCESS_DISCOVERY` recorded the actual obstruction at `(307,101,308)`, and the trace changed from APPROACH to OBSERVE. The actor looked at the wall; the user noticed the thinking pause.
- The policy held the inspection for 60 ticks. Ordinary approach resumed at run tick 164, ATTACK_MELEE at 184, and the first melee hit at 194. This supports the user's description of combat resuming after the brief inspection.
- The direct post-replay dump records `DISCOVERY_REPLAN`, the same selected goal and inherited observation, `ACCESS_POINT_DIRECTLY_DISPROVED`, east confidence **0.85 -> 0.20**, and contradiction count **1 -> 2**. The old OPEN confidence fell from 0.80 to 0.15; fresh replacement state is **BLOCKED at 0.90** with `OBSERVED_ACCESS_OBSTRUCTION` provenance. East is on next-encounter cooldown.

These observations complete §9.19's Slice 4 condition: commitments resolve to real access points, and sealing a side produces the §9.3a discovery event in play. After that sequence, ordinary pursuit continued toward the edge of the finite QA platform and the target left its height; that later behavior is not used to claim spatial-route coverage on arbitrary terrain.

Accepted local evidence: `build/maeve-slice4-evidence/accepted-discovery/` contains both direct dumps, the complete actor trace, build identity and assessment. Automated bounds, save compatibility, event filtering and multiplayer isolation remain covered by the required gates; this single-player replay makes no claim of a new two-client soak.

# MACS combat memory location — research and implementation handoff

Owning checkout: `/Users/jaroncabral/.codex/worktrees/macs-combat-memory/minecraft-mod`.
Branch: `fix/macs-combat-memory-location`.
Baseline: `e5d268db4a40302c3eb123603c8d71deee2fc33d`, the custom sword recognition fix, pushed to `origin/fix/macs-custom-sword-recognition`.
Research date: 2026-10-07 Pacific. Environment: Minecraft 1.21.1, NeoForge 21.1.219, Java 21, ModDev 2.0.140. No new dependency or asset is required.

## Decision

Apply the same location-independent hint admission to `PLAYER_PREFERS_SWORD` and `PLAYER_PREFERS_RANGED`, within the current dimension and only after an eligible ordinary Architect actually perceives the player locally. Retain the old evidence-location limit for all other patterns in this first fix. The production change is limited to this filter. Native comparison results and visual acceptance are recorded below.

The patch changes the historical-location filter in `CommitmentCoordinator.hints()`: admit SWORD or RANGED regardless of distance from the retained attack coordinate; otherwise preserve the existing 48-block evidence-position rule. Do not increase `ObservationCollector.RANGE`, remove current-sight eligibility, rewrite evidence positions, reset beliefs, or use current held items as strategic evidence.

## Authoritative sources checked

- [MACS source of truth](https://www.notion.so/39d7cfaa890181c1bad4f6babad80880), last edited 2026-10-06T11:45:48.565Z: six laws, §§1, 4, 9.1, 9.3a, 9.12a, 9.13a–e, 9.16, 9.19–20. Retrieved through the Notion connector; no truncation/unknown-block flags were supplied. Relevant sections and the closing page content were present. The page's build-status commit is historical and is not used as the current Git baseline.
- [MACS mantlet implementation and acceptance](https://www.notion.so/3e67cfaa8901816f8534e1f67fe188ea), last edited 2026-09-25T09:34:01.915Z: historical confidence selects the counter; current locally visible subject fixes the proposed front; the same geometry remains through flanking.
- Existing repository `docs/maeve-commitment.md`, especially the effective-damage revision: positional pillars use a historical firing origin deliberately, so changing sides can expose a wrong prediction. This is distinct from mantlet geometry. An old firing coordinate alone is not established as another bug.
- [Epic AI Perception documentation](https://dev.epicgames.com/documentation/en-us/unreal-engine/ai-perception?application_version=4.27), accessed 2026-10-07: separately exposes currently perceived actors and previously known actors, with stimulus age/location/success. This supports the general separation of current perception and retained knowledge. It does not define MACS behavior and contributes no code, dependency or asset.

## Verified implementation findings

1. `CommitmentCoordinator.hints()` first requires `eligible()` and calls `store.contact()`. `ObservationCollector.canObserve()` requires a living Survival/Adventure subject, ordinary enabled eligible observer, same level, distance <=48, loaded intervening chunks and own line of sight. The later hint filter additionally rejects historical evidence farther than 48 blocks from the current observer. This second limit applies uniformly to weapon habits and place-related patterns.
2. `CommitmentPolicy.begin()` freezes the prior beliefs before new encounter observations. `hints()` reads decayed confidence and retains the latest supporting event. Confidence is not deleted by travel. `LearningUtility` already consumes frozen same-dimension ranged history without the old-position distance filter, so the defect does not mean all adaptation or memory disappears.
3. Shields and keep-away archers propose the current actor position and execute from current local perception. Their admission is obstructed by the historical-location filter, not by a need to travel to the old attack site.
4. `ArchitectMantletController.candidate()` already requires a visible subject and creates a plan from that subject's locally seen position. It freezes the proposal for execution; later flanking does not rotate the plan. A distant historical ranged hint should not require new geometry machinery.
5. `ArchitectCommitmentController.candidates()` uses the retained projectile origin for pillars. `ArchitectCoverGeometry.find()` projects at most twelve local cells, performs local support/clearance/occupancy checks and tests the pillar against the inherited firing line. The actor's position/cover are bounded locally; this is not a travel command to the old site or a remote terrain scan. The first patch can preserve this documented wrong-direction counterplay.
6. Recovery watches and spatial exits are tied to observed places. Their existing evidence-position checks stay intact. Pursuit portability and cross-dimension generalization are outside the selected sword/bow scope.

## Reference decision

| Candidate | Intended use | Verified compatibility/rights boundary | Safe takeaway | Decision |
| --- | --- | --- | --- | --- |
| Existing MACS implementation at e5d268d | Native NeoForge code strategy | Existing Java 21 / 1.21.1 code; repository LICENSE is LGPL v3 | Preserve existing perception, frozen history and local executors; author the small filter change locally | Adopt existing project pattern |
| Mantlet contract and existing controller | Local cover planning | Already integrated in this baseline; no additional runtime | Historical behavior selects a counter; locally observed geometry is frozen once | Preserve |
| Epic AI Perception documentation | Conceptual reference only | Unreal API is not a Minecraft dependency; no external implementation or assets are copied | Current perception and known history are distinct inputs | Study only |

No external source, library or asset is adopted, and no distributable asset provenance record is needed for this patch.

## Six-law acceptance boundaries

| Law | Required evidence |
| --- | --- |
| Lagged | Fresh current attacks cannot unlock a counter in the encounter that supplies them; selection uses the frozen prior confidence. |
| Legible | Real shield/archer/pillar/mantlet execution and existing tells remain; available hints alone do not establish visible acceptance. |
| Exploitable | Weapon switches/flanking can defeat the spent choice; no instant replacement counter, cover rotation or repaired/refunded resources. |
| Fallible | Decay, contradictions, uncertainty and deliberate wrong firing-point cover remain. Travel neither confirms nor invalidates a habit. |
| Bounded | Existing attention, one bet per encounter, six candidates, coarse planning, local geometry, construction and quiver bounds remain; no global search/chunk loading. |
| Causal | Original player UUID and evidence provenance remain; current target must independently pass local sight and range checks. No server-driven target discovery or inventory scan. |

## Matching baseline and fixed verification protocol

Preserve one meaningful failing baseline regression before editing production code. Exercise real damage recording in area A, remove the old observer without erasing history, advance the normal 600-tick noncontact gap, then place a fresh eligible observer with that same player in area B farther than 48 blocks from the old evidence. Compare a nearby control.

Separate recording from counter execution assertions so a failure is attributable. Existing native scenes already offer bounded chunk tickets and automatic release, a restored clock and SavedData isolation. Use distinct fixture lanes and release all added tickets/entities/blocks on success and failure. A training site at offset 8 and new observer at offset 64 demonstrates a 56-block evidence separation inside a radius-5 loaded fixture; do not confuse this with distance to the currently visible player.

Required cases:

- Both weapon habits are available at B from preserved historical evidence, with unchanged confidence/provenance. Include the 48/49-block evidence boundary and a clearly farther site.
- Sword confidence 0.80 produces an actual shield where normal geometry admits it; 1.00 can produce a keep-away archer at normal opening range with the same finite quiver.
- Ranged confidence 0.80 produces actual local pillar construction before another arrow; 1.00 can produce a mantlet aimed at the currently visible subject, then retaining its fixed front when the player flanks.
- A pillar retains its inherited firing-side geometry even when that prediction is wrong. Do not silently change the historical-geometry contract as part of this admission fix.
- Occlusion, current player distance >48, spectator/Creative, another player UUID, excluded/NoAI observer and insufficient/decayed confidence cannot gain a commitment through the new exception. Cross-dimension historical hints retain their current exclusion.
- Recovery/exit/place hints still reject distant evidence. Travel itself adds no support or contradictions; new attacks, cooldowns, spent bets, reload and ERASED keep their existing rules.

After the baseline is reproduced, change only the historical-location filter for SWORD/RANGED, rerun the same new fixtures and existing `architectVerify` gates. Broaden testing only for changed behavior or unresolved failures. Human visual and multiplayer acceptance remain separate.

## Comparison preparation and first attempt

Seven native fixtures were added to the required gate. Each uses real witnessed damage, seed 1337, one-damage training hits in four or five encounters separated by 610 ticks, a fresh executor 56 blocks from the evidence and the same locally visible subject. Four/five encounters earn 0.80/1.00 confidence; no belief injection is used. Controls cover the 48/49 historical-distance boundary for both weapons, current sight/range, Creative/Spectator, NoAI, another UUID, 0.60 confidence and recovery-place locality.

The first baseline completed 247 tests: 242 passed and exactly five new regressions failed (shield, archer, pillar, mantlet travel and the 49-block boundary). Both control fixtures passed. Full logs, native XML, original production file and test source are preserved in `build/combat-travel-evidence/baseline/`.

The first fixed attempt carried all historical hints and passed shield, mantlet and the boundary/controls, but failed two new execution assertions. This attempt is preserved in `fixed-first-attempt/`, including its source and report. The archer fixture began at 12 blocks and ordinary approach can close inside the >8-block selection gate during the landing/planning interval; it now uses the existing archer fixture's 16-block opening. The pillar assertion incorrectly compared cover against the initial spawn coordinate; it now compares the actual committed standing point. The corrected native trace confirmed the actor committed one block east of spawn and built its historical-side cover at the original spawn X, so the original strict `< spawnX` assertion was wrong. The corrected fixtures are rerun against original production before the final fixed gate. Neither correction changes production selection or construction.

## Visual replay preparation

`tools/prepare_combat_travel_playtest.py` prepares four independent fresh worlds: **MACS Travel 1 Shield**, **MACS Travel 2 Archer**, **MACS Travel 3 Pillar**, **MACS Travel 4 Mantlet**. Two roofed heated arenas are 80 blocks apart. Full EVA, oxygen and protection are provided on first join. The owner supplies actual sword/arrow hits; each witness is archived alive after damage recording, followed by a normal 620-tick empty noncontact gap. Only this empty wait can be skipped with the provided sprint button. The fresh encounter at B begins before any confirming attack there. No finished attempt resets itself.

The prior custom-sword client and played save remain owned by the previous checkout. Launch this checkout through Java 21 `./gradlew runClientLab --console=plain` in the agent terminal, as required by the latest user-provided AGENTS instructions. Use the lab bridge for read-only state/dump checks with an exact world guard; no travel replay mutations are added to its allowlist. Native execution results do not establish human visual or multiplayer acceptance.

## Final gate and runtime handoff

The corrected old/fixed comparison is complete. The native fixture SHA-256 is identical on both sides: `3e71102761cc258d48dace47939e1e4b4c75711af5a481598e6fcc78227f92b2`. Both use seed 1337 and the same relative coordinates, training attacks, confidence and encounter gaps. Minecraft creates different absolute test origins/UUIDs between invocations; these are isolated native scenes, not a replay of identical entity IDs.

| Acceptance | Original e5d268d | Fixed source |
| --- | --- | --- |
| Sword 0.80, fresh observer 56 blocks from evidence | Hint rejected | Actual shield equipped |
| Sword 1.00, fresh observer 56 blocks from evidence, 16-block opening | Hint rejected | Actual keep-away bow equipped |
| Ranged 0.80, fresh observer 56 blocks from evidence | Hint rejected | Actual local packed-ice pillar built toward historical firing side |
| Ranged 1.00, fresh observer 56 blocks from evidence | Hint rejected | Actual local mantlet built toward visible subject; original cover retained through flank |
| 48/49-block historical boundary, sword and ranged | Hint drops at 49 | Both weapon histories remain available |
| Current perception/identity/mode/threshold and recovery-place controls | Pass | Pass |

The corrected baseline completed 247 native tests with exactly the five expected failures. Final `./gradlew architectVerify --console=plain` passed **665 unit tests, 247 native GameTests, all 242 required native cases/report checks**, existing harness/fixture checks and release-jar bridge exclusion. No unrelated native failures occurred. No movement code changed; the existing stress matrix was not rerun. Human visual and multiplayer acceptance remain pending.

Evidence: `build/combat-travel-evidence/comparison.json`, `baseline-corrected/console.log`, `baseline-corrected/report.xml`, `fixed/console.log`, `fixed/report.xml`, `fixed/architect-build.properties`, plus both earlier attempts. Final gate artifacts: `build/architect-reports/fbd0af07-3add-4376-baa8-99e4495b17cd`.

Jar SHA-256: `1f4cb4e54bf4b4b5036620d9dba8a1aaee8a6d4ae9c90c1f0168939e114180a0`. Only the Frozen Dawn smoke slot was replaced; dependency jars were preserved. The dedicated arena builder used this jar, bound only to 127.0.0.1 with an ephemeral port, accepted no player and stopped after native block checks, flush and save.

The four worlds are installed under this checkout's `run-lab/saves/`. Native prebuild verified both arenas, geothermal cores, right boundary, inert density-cap actor and stage 0. Maeve reported ACTIVE with zero profiles/beliefs, and the closed NBT confirmed zero profiles. Temporary arena tickets were removed by the server; catch-up left one ticket at shutdown, removed only from the four new unplayed copies. Every installed world was checked for zero forced tickets, zero Maeve profiles, cheats enabled, correct title and valid JSON/NBT. The original native template and preparation console remain in `build/combat-travel-world-prep/`.

The generator accepts either fresh `--source level.dat` metadata or a stopped `--prebuilt WORLD` with empty history. It refuses an existing destination. All four installed worlds share the natively verified physical layout but have independent histories and case-specific datapacks.

Client launch: Java 21 `./gradlew runClientLab --console=plain`, agent terminal session **10902**, log `build/combat-travel-evidence/client-console.log`. This is a new process loading the fixed Java classes. No prior played world was closed, copied or reset. The current bridge heartbeat verified this exact checkout with `worldLoaded: false`; its saved response is `build/combat-travel-evidence/client-status.json`. The client is ready for the owner to choose a world. No commit or push was requested.

## First human replay

1. In the new Gradle client, open **MACS Travel 1 Shield**. First join supplies EVA, oxygen, protection, iron sword and the Begin button.
2. Click **Begin real training**. Land exactly one sword hit in each prompted encounter. The witness is preserved alive and the gap runs 31 unpaused seconds; the provided sprint button skips only that empty wait. There are four encounters.
3. At training completion, inspect the dump for sword confidence 0.80, then click **Travel 80 blocks and start fresh encounter**. Watch before hitting again. A shield should appear from prior history despite the distant original evidence.
4. Give an unprompted visual account, then capture the Maeve/actor dump through the provided evidence button or exact-world read-only bridge. Preserve the attempt; do not rerun/reset this world. Proceed to Archer, Pillar and Mantlet as independent worlds after checking the first result.


## Live Shield acceptance — 2026-10-07 Pacific

Owner's first report, preserved before diagnostic explanation: **“yup. travel 1 worked check it”**. Read-only lab checks verified the exact Shield world/session `0e6ce4ae-45d1-4e7e-8b25-236262eedf64`. The owner already finished the attempt: stage 99, round 4; all six actors (inert cap, four witnesses and fresh executor) remain alive, archived with NoAI. No reset, freeze, teleport, new encounter or simulation advancement was performed by the agent.

Four real iron-sword hits in four distinct prior encounters earned confidence **0.8000**, with no sword contradictions or added support at B. Latest source event: tick 3709, anchor **(3,65,0)**. A fresh observer `430e0e42-65bf-4268-93bd-68228bc0d417` selected **GUARD_SWORD** at **(80,65,0)**, **77 blocks** from that evidence, starting/arriving at tick 4428. The retained original witness/encounter and anchor are inspectable. The actor journal records **MAEVE_SHIELD_EQUIPPED** and first **MAEVE_SHIELD_RAISE** at run tick 40, lower at 75, another raise at 125. The journal is complete (16 entries, zero dropped), with source fingerprint matching the passed fixed gate.

**Shield travel acceptance passes:** owner-reported visible behavior and actual execution from distant historical evidence agree. The final strategy-performance result is UNKNOWN after the actor was archived; received/dealt/blocked effective damage are zero. This validates admission and visible execution, not damage prevention or counter effectiveness. The NoAI archive explains `OBSERVER_UNAVAILABLE`; it is test completion, not another admission failure.

Evidence is copied under `build/combat-travel-evidence/live-shield/` (snapshot, Maeve dump, scores, export response, complete journal/summary, result JSON). The active save is preserved. Next manual replay: Save and Quit, open **MACS Travel 2 Archer**, earn five separate sword encounters to confidence 1.00, then travel and watch the bow opening before attacking. Archer, pillar and mantlet human acceptance remains pending.

## Live Archer acceptance — 2026-10-07 Pacific

Owner's first report: **“yup it starting shooting, evident by the advancement”**. Bridge status was already at the title screen (`worldLoaded: false`), so diagnostics read the closed **MACS Travel 2 Archer** save and existing exports. No world was reopened, advanced or reset. Closed scores confirm stage 99 and five completed training encounters.

Five real iron-sword encounters earned **1.00 confidence**, zero sword contradictions. Frozen baseline and final provenance agree: no supporting attack was added after travel. Latest inherited support at tick 4205 remains at **(3,65,0)**, **77 blocks** from the new actor's origin **(80,65,0)**. At run tick 40 / game tick 4920 the actor equipped a keep-away bow from frozen confidence 1.0, with a sixteen-arrow quiver and a locally visible subject at sixteen blocks. Three real **MAEVE_ARCHER_SHOT** entries occur at run ticks 60/100/140 (game ticks 4940/4980/5020), naming actual projectile UUIDs and decrementing arrows to 15/14/13. No DAMAGE event appears in the fresh executor journal. The complete trace has zero dropped entries and matches the fixed gate source fingerprint.

The saved advancement **minecraft:story/deflect_arrow**, displayed as **Not Today, Thank You**, completed at 23:58:55 Pacific; the client/server log agrees. Together with the archer journal this corroborates an actual physical projectile intercepted by the player's shield. The fifth training witness itself used the shield tier from its frozen prior 0.80 confidence before that encounter's hit brought history to 1.00; this is expected lagged selection.

**Archer travel acceptance passes.** The short replay does not establish full quiver exhaustion or strategy effectiveness; the final archived actor yields the expected UNKNOWN performance result. Evidence is preserved under `build/combat-travel-evidence/live-archer/`: complete export, closed Maeve/score data, decoded Maeve JSON, advancement state/log and result JSON. Next: **MACS Travel 3 Pillar**, four separate bow encounters, then observe the historical-side pillar at B before another arrow. Pillar and mantlet human acceptance remain pending.

## Live Pillar acceptance — 2026-10-08 Pacific

Owner's first report: **“yup, it pillared”**. Read-only bridge checks verified **MACS Travel 3 Pillar**, session `37d4f4a5-8312-4cab-a03f-3c6d47771655`, stage 99 and four training rounds. Six actors remain alive in the archive with NoAI. The agent did not advance, freeze, reset or relocate anything.

The selected **FIGHT_WITH_RANGED_COVER** used frozen **0.8000** ranged confidence from the four prior projectile encounters. Inherited support tick 3604 remains at **(3,65,0)**, **77 blocks** from the new committed point **(80,65,0)**. Selected pillar base **(79,65,0)** screens the historical west firing side, consistent with the deliberate fallible pillar contract. The actual **MAEVE_COVER_COMBAT** build-complete event is at run tick 40 / game tick **4416**; the first new arrow damage is at run tick 73 / game tick **4449**. Thus the pillar existed before a confirming attack at B, and local pursuit resumed within that same spent bet. The complete 164-entry journal has zero dropped entries and matches the fixed gate source fingerprint.

The owner subsequently shot the new executor three times. The first added one new encounter contribution, raising current ranged confidence to **1.00**; the later shots verified that encounter without more confidence weight. This later confidence is distinct from the frozen 0.80 used to choose the pillar. The retained choice remained the original pillar rather than upgrading instantly to a mantlet. Its strategy-performance result records FAILURE after effective incoming arrow damage, which is valid fallible counterplay and distinct from this successful portability/visible-build acceptance.

**Pillar travel acceptance passes.** Evidence is preserved under `build/combat-travel-evidence/live-pillar/`: snapshot, Maeve dump, scores, actor export, complete journal/summary and result JSON. The completed world remains preserved. Next manual replay: **MACS Travel 4 Mantlet**, five separate bow encounters to confidence 1.00, then observe cover construction at B before another arrow. Human mantlet acceptance is the remaining travel case.

## Live Mantlet and completed travel fix — 2026-10-08 Pacific

Owner's first report: **“yup, it started building the manlet. so the bug is patched?”**. Bridge status was already at the title screen, so diagnostics read only the closed **MACS Travel 4 Mantlet** save and retained journal. Stage 99 and five completed training rounds were confirmed. No world was reopened, reset or advanced.

Frozen ranged baseline has five supporting encounters at **1.00 confidence**. The latest inherited attack anchor remains **(3,65,-2)**, **approximately 77 blocks** from new executor origin **(80,65,0)**. Journal **MANTLET_STARTED** occurs at run tick 40, fixing a visible east front at **(82,65,0)** with five-screen/twenty-block budget; the historical firing anchor lies west. Eight actual **MANTLET_BLOCK** events build two four-block screens at X=82 and X=84, with screen-complete events at ticks 74 and 154. The first new arrow damage is at tick 176, after both screens were built. There is one start, complete trace and zero dropped entries; source fingerprint matches the fixed native gate. After damage the original counter yields to local defense; it does not issue a replacement front.

**Mantlet travel acceptance passes. All four travel counter cases now pass owner visual observation plus corresponding actual execution evidence: shield, keep-away archer, pillar, mantlet.** The exact corrected native fixtures reproduced five failures on original e5d268d and zero on the fix; the full fixed gate passed 665 unit tests and 247 native tests (242 required cases/report checks). The scoped historical-location bug is implemented and verified. Current local perception, same-dimension history, frozen lagged confidence, bounded selection and place-specific filters remain in effect.

Evidence: `build/combat-travel-evidence/live-mantlet/` contains the complete export, closed Maeve/score data, decoded Maeve JSON and result. Combined result is `build/combat-travel-evidence/comparison.json`. Human full five-screen construction was not attempted in this short replay; this does not affect admission/build-after-travel acceptance. Live dedicated multiplayer remains separate. All four completed worlds/evidence are preserved. Client remains the owning Gradle lab process at the title screen. Delivery branch: **fix/macs-combat-memory-location**. Owner authorized commit and push on 2026-10-08 Pacific. The saved source fingerprint matches the passed full gate; only documentation and the exercised replay generator changed afterward. The old dedicated playtest server still uses its pinned build. This commit includes the scoped fix, required regression cases, replay generator and this acceptance handoff; raw logs and completed worlds remain preserved locally under the documented paths.

# MACS Slice 6 — reconnaissance

MACS is the Maeve–Architect Cognitive System. This slice implements §§9.4–9.5 of [MACS — Source of Truth](https://www.notion.so/39d7cfaa890181c1bad4f6babad80880), on `feat/maeve-reconnaissance`, targeting `feat/maeve-director` after Slice 5 merged as `413f6ed` (PR #91).

**Live exit criterion remains pending.** Automated evidence cannot establish that a player understood the encounter as reconnaissance. The criterion is an unprompted description such as “that one wasn't attacking me.” Do not merge this slice before that replay is recorded and matched to its dump and actor trace.

## Behavior and knowledge

An eligible ordinary Architect that can currently see a Survival/Adventure player can receive a survey of one previously witnessed access point. The point must be in the same dimension, within 24 blocks, at least 600 overworld ticks old, and have confidence above 0.05 but below 0.75. These are recording/dispatch defaults. Unknown geometry does not create an entrance, and a current hidden target position is never an objective.

The packet freezes one entrance's inside/outside points, state, confidence and latest provenance; at most four already-observed danger points; three scored alternatives; and four orders. It contains the subject's identity, observer, encounter, dimension, issue time and 360-tick deadline. The Architect does not receive the hive database or later changes to it.

The authored alternatives are surveying the entrance, local engagement and withdrawal. Utility is tactical + survival + objective + information gain × curiosity − exposure − attention. Uncertainty supplies the expected information gain. Exposure grows with travel and a nearby or recently damaging opponent; low health favors survival. A currently eligible strong commitment favors local engagement. Difficulty changes only the existing attention capacity; it does not improve confidence, damage, speed or perception. Scores and rejected alternatives are retained with the packet.

The executor walks toward a local vantage three blocks beyond the remembered outward crossing, faces the entrance using its existing thinking pose, inspects for 100 ticks, then leaves for 200 ticks. Ordinary pursuit of that subject stays suppressed for the existing 600-tick departure window. A mission has a 360-tick deadline and an 80-tick movement-progress guard. Unsupported terrain, fire, fluids, inadequate health or an unavailable route can end it early. It uses the existing bounded walking-only D* path mode; it does not breach, scaffold, build cover or invent structures during this survey.

Actual damage interrupts the survey and returns control to local self-defense. Completed, interrupted and evicted missions delay that executor's next dispatch by 1,200 ticks. NoAI, death/removal, unload, erasure and shutdown release execution. A timeout or failed inspection does not create knowledge.

During inspection, a report describes only the short historical crossing segment that this observer can inspect from within six blocks. A visible collision in the crossing can establish BLOCKED; OPEN requires a clear view of the segment's feet/head clearance. An unrelated intervening wall yields UNSEEN. Inspection uses at most nine points with three height samples and loaded-chunk ray checks. No chunks are loaded. OPEN/BLOCKED reports carry their observer, encounter, position, time and action; they update the access point with a provisional 0.90 observation weight. They do not establish that the player prefers a retreat route or knows about a room. A changed access state records a contradiction; perception can correct either an old OPEN or BLOCKED state.

Visible player movement during inspection uses the same existing spatial-event validator. A roaming Architect's locally selected candidate now reaches that validator even though its utility controller does not set vanilla's target field. The validator still requires the Architect's own sight. The real-AI practice regression caught this missing delivery path; no debug target lock is used in this rehearsal.

## Purple eyes

The owner requested exactly one visual distinction: purple eyes, with the existing blink. A synchronized transient flag enables a render layer only for the active scout and its withdrawal. It covers only the existing two 2×3-pixel eye regions, preserving the three brightness rows and the thin closed-eye row. The layer calls the renderer's existing 97-tick blink decision; it adds no timer or animation.

Both original textures are unchanged. Model geometry, skin, clothing, equipment, movement animations, sounds and lighting behavior are unchanged by the eye cue. No glow, particles, outline or nameplate was added. Masters and their copies never receive the flag. The cue clears with mission/departure cleanup and is not saved on entities.

## Attention, multiplayer and persistence

Each reconnaissance mission consumes one shared focus slot and is the first eviction candidate. Only one survey per player may run at once. A sole passive observer switching to survey reuses its own slot and begins a new 100-tick dwell. Other observers retain shared tracking; a separate survey must then gain its own slot. Lower-priority newcomers cannot displace commitments or sieges under the owner-approved Slice 5 rule. Pressure that evicts a mature scout makes it visibly leave while retaining the unresolved access point for a later dispatch.

At most five missions exist per server, with 16 recent packet/result snapshots and 256 executor retry entries. Packets, executor references, recent mission diagnostics, eye flags and retry timing are transient. Local planning retries no faster than once per second, considers at most 64 points in the subject's existing bounded profile, and retains at most four danger hints. Mission cleanup runs once per second using loaded entity UUID lookups. Walking computation is bounded by the existing D* cell cap and 80 expansions per step.

Tactical SavedData remains version 3: completed local reports use the existing labelled-point/provenance format. Old worlds load without mission state. Reports survive reload; in-progress packets and cues do not. Authoritative ERASED, forced erasure and debug erasure clear knowledge and execution immediately; reversing a debug override starts empty. The permanent violation ledger is unchanged. A complete external backup restores its own saved observations as before.

`MaeveDirector` remains the sole external facade, below the enforced 300-line limit. Package-private `MissionPlanner`, `MissionSensing` and `StrategySelector` own dispatch, validation and scoring. The entity controller handles local movement; the renderer handles only the eye cue.

## Diagnostics and verification

Operator `/fd maeve dump [player-or-uuid]` includes active and recent missions for that subject: inherited packet, objective/orders, utility components, alternatives, report, outcome and knowledge boundaries. `/fd maeve status` includes bounded mission/attention state. The command namespace and intentional `MaeveDirector` class name remain stable. An erased dump exposes no old packet. No permanent tactical archive is created inside the world; explicit operator actor-trace exports remain QA evidence.

Required command: `./gradlew architectVerify architectMonkey --console=plain`, preserving established fixtures and seeds. Added required GameTests exercise actual autonomous survey/travel/inspection/departure, hidden versus visible obstruction, unchanged player-tendency confidence, damage interruption, shared-slot pressure, minimum dwell, isolated player knowledge, packet/report persistence boundaries, erasure, timeout, Master/NoAI exclusion, and native parsing of the live functions. Practice geometry runs real observer AI at offsets 0/7/19 through a 30-second preparation delay, a walking crossing, and five more seconds on gold. It verifies a real access-point observation, checks containment every tick, and rejects any damage without using resistance in the test.

The initial rehearsal regression failed because roaming presence never reached the existing spatial hook. Its failed log is retained at `/tmp/macs-slice6-gates.log`; the corrected required cases pass. Final delivery evidence, commit identity, test counts, fingerprints and jar hashes are retained under `build/macs-slice6-evidence/verification` and published on the draft PR. Live evidence is still outstanding.

The first live setup exposed an escapable two-block-high booth window: actor `80f3b67e-d78e-4ec0-8223-4fbcfcf9e481` climbed out and recorded 15 melee hits before the player froze the game. Its trace `30407f26-ac77-4d18-8874-2e013aa66b62` and client log are preserved under `build/macs-slice6-evidence/setup-failure`. The strengthened containment regression reproduced the escape at tick 45. The booth now has a single-block eye-level window, preserving real sight while preventing that climbing route. The first restart also exposed a parser-permission mismatch: GameTestServer accepts permission-3 `/tick` commands inside functions, while the integrated client's permission-2 compiler rejects them. The regression now reparses every generated function at permission 2; unfreezing is a direct player command. These failed setups are not evidence for the live reconnaissance exit criterion.

## Executable replay

The isolated **MACS Recon Encounter** is copied from the closed Slice 5 world. The source world is preserved. Its initial setup clears tactical memory only in the disposable copy. Repeating setup does not reset observations.

1. Open **MACS Recon Encounter** and run `/function macs_recon:setup`.
2. Stand on blue until the three-second prompt, then walk normally through the doorway to gold and stop briefly. The enclosed observer has real AI and sight; it cannot reach the player.
3. At “Crossing exercise complete,” run `/fd maeve dump` directly. It should retain a witnessed access point and one crossing, not injected knowledge.
4. Click the yellow skip-gap link, or run `/tick sprint 620t` once. The gap contains no active test actor; it preserves the production 600-tick age/encounter rules. Wait for both Ready and Sprint completed.
5. Run `/function macs_recon:start`. Stay near green initially, then move normally. Describe the encounter before reading another dump. It pauses at 460 ticks (23 seconds) and exports the actor trace; that final scripted pause is not a mission decision.
6. Run `/fd maeve dump` directly after describing it. Review `MAEVE_RECON_ASSIGNED`, `MAEVE_RECON_INSPECTING`, `MAEVE_RECON_REPORT`, `MAEVE_RECON_FINISHED` and extraction in the trace against the retained packet and world provenance.

For an interrupted rehearsal, run `/reload` after installing an updated pack, then `/function macs_recon:restart`. If the game is frozen, run `/tick unfreeze` directly in chat afterward. Restart exports the current actor trace, removes the failed actor, resets progression counters, restores the player's practice effects, and begins at the next unused site. It never erases accepted observations or reuses an already-observed entrance. After all three sites are used it removes the actor and stops, preserving evidence.

For a new physical site after a completed round, use `/function macs_recon:next_site`. For a fresh site whose entrance is sealed after the witnessed crossing, use `/function macs_recon:sealed`. Each adds one ordinary crossing and its skippable empty gap. Three separate sites are provided; `/function macs_recon:status` reports progression. Earlier accepted observations and traces remain intact. No function injects a belief, chooses a mission, fixes a target or commands a departure.

This slice surveys known uncertain access. It does not implement siege orchestration, destructive probing, room/storage classification, per-player conditional models, strategy-performance learning, individual traits, forward simulations or §10 capabilities.

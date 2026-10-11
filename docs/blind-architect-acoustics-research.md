# Blind Architect: acoustics and vibration research

Research requested September 30, 2026 Pacific. Owning checkout: `.worktrees/blind-architect`, branch `feat/blind-architect`, base/HEAD `0885e22812f8723a6f55cdf52da87b62a18c4485`. Target: Minecraft 1.21.1, NeoForge 21.1.219, Java 21. This brief proposes a revision to the current local encounter. It does not describe shipped changes or replace the implemented tuning in [the encounter design](blind-architect-design.md).

The recommended first change is mechanically connected vibration propagation. Then revise echoes to depend on returned energy, direction and uncertainty. Develop ordinary hearing separately. Each stage needs a matched comparison before combining the channels again.

## What the research supports

### Echolocation is an active measurement

Thaler and colleagues measured clicks from three expert human echolocators: emissions were approximately 3 milliseconds long, had peaks around 2–4 kHz with additional higher-frequency energy, and radiated more strongly forward. These were particular people and conditions, not universal limits. Audible clicks can support echolocation; ultrasound is not a requirement. [Primary study, PLOS Computational Biology, 2017](https://journals.plos.org/ploscompbiol/article?id=10.1371/journal.pcbi.1005670).

An experiment with eight experts found that detection and emission behavior changed with reflector angle; participants used louder/more clicks for difficult positions. Its small disk at one meter cannot establish a monster's detection radius. Our design inference is to make head orientation affect return strength and use deliberate rescanning when evidence is weak. [Primary study, Proceedings of the Royal Society B, 2018](https://durham-repository.worktribe.com/output/1344008/human-echolocators-adjust-loudness-and-number-of-clicks-for-detection-of-reflectors-at-various-azimuth-angles).

Returned sound carries temporal, spectral and intensity cues. Object size, material and shape make intensity an ambiguous range cue. A study of expert distance discrimination supports using more than loudness alone. Our model should represent detectable echoes and accurately localized echoes as different outcomes. [Primary distance study, 2019](https://pmc.ncbi.nlm.nih.gov/articles/PMC6797687/).

Wild-bat recordings show adaptive emission and sampling strategies during prey approach, including smaller sensory volumes and faster updates. They do not support an unconditional rule that louder clicks always help. This supplies a behavioral reference for broad surveying followed by more focused sampling; the Architect's anatomy and abilities remain fictional. [Primary bat study, Science Advances, 2021](https://pubmed.ncbi.nlm.nih.gov/33658207/).

### Background sound changes what can be heard

Human experiments show that masking depends on the target and masker sounds and their binaural relationship. Noise should affect signal-to-noise ratio at the receiver; simply making any source loud somewhere in the world is insufficient. Our initial approximation can use frequency bands, local noise duration and coarse arrival direction. This will not reproduce the study's full perceptual model. [Primary masking study, Neuropsychologia, 2024](https://pubmed.ncbi.nlm.nih.gov/38342179/).

Wool experiments found frequency-dependent absorption that varied with thickness and textile construction. These results support treating wool as an absorber rather than universal acoustic invisibility. They do not supply a calibrated attenuation value for a Minecraft block or foot contact. Keep airborne absorption, impact cushioning and structural transmission as separate parameters. [Primary wool study, Materials, 2022](https://pmc.ncbi.nlm.nih.gov/articles/PMC9105389/).

### Vibration is mechanically received through the body

Mouse experiments identified a pathway in which Pacinian mechanoreceptor signals from body vibration converge with auditory signals. This supports treating mechanical and airborne sensing as distinct inputs that can reinforce each other. It does not establish humanoid localization precision, a 24-meter range, or an ability to recognize a player through arbitrary terrain. [Primary study, Cell, 2025](https://pmc.ncbi.nlm.nih.gov/articles/PMC11724753/).

Substrate experiments with jumping spiders found substantially different filtering in rocks, sand and leaf litter, with leaf litter favorable for the signals tested. The lesson is dependence on substrate and frequency, not a universal material ranking. Game profiles will require explicit assumptions about damping, connections and contact. [Primary substrate study, Journal of Experimental Biology, 2004](https://pubmed.ncbi.nlm.nih.gov/15498956/).

### Propagation time is distinct from reaction time

For an ideal gas, sound speed depends on absolute temperature and gas properties. Using approximately 343 m/s at 20°C, a direct echo from 20 meters takes about 117 ms for the round trip; 24 meters takes about 140 ms. These are our calculations using `2 * distance / speed`, assuming one block equals one meter. [NASA's speed-of-sound relation](https://www.grc.nasa.gov/WWW/k-12/VirtualAero/BottleRocket/airplane/sound.html).

Store physical arrival time with sub-tick precision even though behavior updates at 20 TPS. A 50 ms time bin would correspond to about 8.6 meters of direct echo range, so tick count alone cannot supply a fine position estimate. Add a separately named processing delay for gameplay. For indirect paths, elapsed time measures traveled path length, not straight-line target distance.

Frozen Dawn's `TemperatureManager` supplies effective exposure temperature, including shelter and heat modifiers. That is not a measured gas state. This audit did not establish a pressure/composition field suitable for acoustic simulation. Do not interpret oxygen depletion as vacuum, or extrapolate the ideal-gas formula into condensed-atmosphere conditions. Atmospheric acoustics requires its own canon and model decision.

## Implementation audit before the structural revision

The following findings were checked before the structural revision. They are retained as the reason for the changes; current implementation/evidence is in [the handoff](blind-architect-agent-handoff.md).

| Current mechanism | Relevant code | Gap to address |
| --- | --- | --- |
| Half-block connected-air A* with a two-unit charge per turn | `BlindArchitectSensing.air/search` | A reachable route does not establish an audible round-trip reflection. There is no returned energy, frequency-dependent loss, reflector strength or emission direction. |
| Vibration uses whole-block A*; an air step costs 1, a solid step adds 3, wool adds 8 | `BlindArchitectSensing.vibration/search` | Air is cheaper to traverse than solid material. It permits a supposed substrate signal without a continuous mechanical connection. Source/receiver support contacts are not established. |
| Echo uncertainty/confidence depend on weighted path cost | `BlindArchitectPolicy` | Path length, loss and uncertainty are conflated. The estimate is still anchored near the hidden player's sampled feet, including around a bend. |
| A successful player query produces a delayed return after 4–8 ticks | `BlindArchitectController.click/resolve` | This is a presentation delay. Weighted turn cost is not physical travel time; the solver does not measure a reflected waveform. |
| Passive hearing requires the same connected-air route | `BlindArchitectController.emit` | Muffled transmission through a barrier and echo localization have the same binary topology. |
| Crouching quarters every structural emission from an entity | `BlindArchitectController.emit` | Crouching should alter footstep generation, not automatically reduce the mechanical energy of mining by the same factor. |
| Consistent repeated echoes add confidence up to 0.80 | `BlindSignalMemory.add` | Repetition can promote an ambiguous return without a signal-quality ceiling or independent spatial evidence. |
| Bell/explosion counter uses fixed event/range rules | `BlindArchitectController.hear/noise` | A gameplay counter is not a calibrated model of masking or auditory injury. Ordinary bell events are not scientific evidence of physical damage. |

The current solver already preserves useful constraints: loaded terrain only, bounded work, explicit budget exhaustion, collision-shape checks, frozen samples, anonymous passive signals and fresh close echoes for melee. Retain those contracts while changing propagation. A backend may inspect geometry to simulate a measurement, but the controller must receive only the information that measurement supports.

### Approved sound characterization

Read-only analysis of all six runtime throat clicks found mono 48 kHz files lasting **390–411 ms**. Their cumulative 5–95% digital-energy spans are **201–278 ms**, and **51–89%** of full-clip FFT energy falls below 1 kHz. This is a characterization of our files, not a calibrated acoustic measurement or the duration estimator used by the human study.

Evidence: `build/blind-architect-evidence/acoustics-research/audio-analysis.json` and its reproducible `analyze_clicks.py`. All six SHA-256 values match the approved selection. No audio was regenerated or modified.

Recommendation: retain the approved throat character and separately define/audition a short, broadband sensing transient, with its emission timestamp aligned to the audible attack. A roughly 3–5 ms attack is a candidate starting point inspired by human measurements, not a proven optimum. Treat the longer rasp as a vocal tail with potential masking, rather than claiming the entire 400 ms performance is a clean sonar pulse. If a clip contains several distinct clicks, model the actual pulse train. Audition any revised asset before replacing approved bytes.

## Proposed model

### Three channels with separate measurements

| Channel | Physical source and receiver | Controller receives | Main variables |
| --- | --- | --- | --- |
| Active echo | Mouth emission, reflected surface, ears | Arrival bearing, traveled range interval, return quality, possible contact region | Pulse spectrum/directivity, two-way path loss, target reflection, clutter, noise |
| Passive hearing | Footstep, tool, door, machine or impact; ears | Anonymous bearing/area with quality and age | Source spectrum, distance, occlusion, wall transmission, noise |
| Substrate vibration | Contact impulse/operating machine into a structure; body support contacts | Anonymous coarse disturbance area or direction, quality and age | Source/receiver coupling, connected material, interfaces, damping, background vibration |

A still player can reflect an active click. A still player does not generate footsteps. Sneaking reduces the footstep source but does not erase the body from echoes. Losing foot contact interrupts the proposed foot-coupled receiver/source pathway until contact resumes; it does not disable hearing. Water supports its own propagation modes, so underwater sensing should be explicitly unsupported in the first revision rather than treated as proven acoustic silence.

### Mechanically connected vibration first

Create a separate `BlindVibrationPropagation` rather than routing this sense through the current air/solid search. Resolve the actual event contact: a foot contacting its support, the block struck by a tool, an impact surface, or the operating machine's physical support. Resolve the Architect's support contacts independently.

Search face-connected material with actual collision/contact geometry. Adjacent block coordinates alone are insufficient for slabs, fences and separated shapes. Model damping and interface transfer independently from geometric length. Air nodes cannot carry the structural channel. A gap can cut this graph, while a floor/wall/foundation connection around the gap can still provide a route. Airborne sound and air-to-structure coupling are separate effects; omitting the latter initially must be documented as an approximation.

Use normalized receiver strength until source forces and sensitivity are calibrated. Conceptually:

`received = source * sourceCoupling * receiverCoupling * spreading * exp(-sum(damping * length)) * product(interfaceTransfer)`

This is a proposed game surrogate, not a solved elastic-wave equation. Material profiles must identify frequency range and assumed structure. Do not treat speed, hardness and attenuation as interchangeable. Do not borrow the airborne wool absorption coefficient as its mechanical coupling coefficient.

A single received magnitude is not enough to identify an exact source location. Return a broad estimate based on arrival/contact information; a move-and-listen behavior can collect another measurement. The source event's private server coordinates must not become an exact hunt destination merely because the graph reaches it.

### Echoes from returned sound

Create `BlindAcousticPropagation` with separate physical distance and per-band energy loss. Start with direct collision-shape rays, then bounded aperture/edge paths and a limited number of significant reflections. Count outward and return legs; the outward route alone is insufficient. Include static walls and other reflectors in the acoustic scene, so the world provides clutter rather than only making players reflect.

Use a smooth forward emission pattern rather than a hard vision cone. Head orientation at emission changes energy; a rear target may still produce a weak return. A weak return can provoke a head turn or repositioning, but steering must use received evidence rather than the hidden target's position. Approximate body reflection from exposed geometry and orientation with an authored profile. Detailed clothing/armor coefficients are not currently calibrated.

The solver reports bearing and delay intervals plus return strength relative to noise/clutter. A direct, strong return permits a narrower region; a reflected/diffracted return around a bend generally points toward an opening or ambiguous path hypothesis. Do not quantize the hidden target's true feet and call that an acoustic measurement.

Keep detection confidence separate from localization confidence. Repeated consistent samples can reduce uncertainty when they add evidence, including a changed listening angle. An unchanged ambiguous multipath pattern must retain an ambiguity ceiling. As a contact ages, its position uncertainty grows. A stored entity UUID can validate a close strike against a physically supported track, but cannot refresh unseen position.

An initial geometric solver will not reproduce phase interference, detailed diffraction, elastic-wave modes or full binaural processing. Rays alone miss wave bending: aperture/edge links need explicitly approximate frequency-dependent losses. Frozen-scene propagation is also an approximation for moving targets/doors; preserve its conservative fairness contract and test movement during return delay rather than claiming continuous-time accuracy.

### Hearing and masking afterward

Author source profiles for footsteps, landing, tools, doors and operating machines. Measure only events that actually occur. Model low/mid/high airborne bands with material thickness and transmission separate from reflection/absorption. A barrier can leave a muffled sound detectable while making precise echo localization unreliable.

Aggregate finite-duration noise at the receiver. Use its spectral overlap and coarse direction when calculating signal quality. A machine may attract the Architect through its own audible output while obscuring quieter signals; these effects should coexist. A bell can create temporary masking/startle under authored thresholds. Retain or change the present pain/damage rule as an explicit monster/gameplay choice, not as an automatic consequence of the research.

Keep server sensing authoritative. Player-facing attenuation/reverb is a later presentation task: the player should eventually hear the same important closures/openings, without making client sound settings control what the AI detects.

## Implementation choices

| Candidate | Intended use | Verified rights/compatibility | Safe takeaway | Decision |
| --- | --- | --- | --- | --- |
| Primary studies linked above | Scientific design references | Read as research; no figures, datasets, code or recordings imported | Independently author a model with explicit approximations and study limits | Study only |
| Existing Minecraft collision shapes, server events and internal Java sensing | Implementation | Existing project targets NeoForge 21.1.219/Java 21; these APIs are already used locally | Preserve loaded-only geometry, deterministic budgets and server authority | Adopt existing integration pattern |
| [Valve Steam Audio simulation API](https://valvesoftware.github.io/steam-audio/doc/capi/simulation.html) | Engine architecture reference | Official docs distinguish distance, absorption, directivity, occlusion, transmission and indirect simulation; native deployment/license/NeoForge integration not evaluated for adoption | Separate propagation outputs from behavior and audio rendering; reflections have a substantial CPU cost | Study only; no new runtime dependency |
| Current approved CC0-derived click bank | Existing presentation | Exact local hashes checked against `selection.json`; no new sourcing or rights determination performed | Keep approved character; assess a separately timed transient in a later audition | Retain current assets during research |

Proposed original modules: `BlindVibrationPropagation`, `BlindAcousticPropagation`, `BlindAcousticMaterials` and a measurement record that distinguishes physical length, loss, arrival time, bearing, uncertainty and exhaustion. Names are design suggestions, not newly implemented classes.

Integrate them at `BlindArchitectController.emit/click/resolve`; revise `BlindSignalMemory` quality accumulation and `BlindArchitectPolicy` thresholds afterward. Retain the existing MACS acoustic-only observation boundary, attention lifecycle, persistence and ERASED behavior. Avoid deriving tactical equipment knowledge from a reflector profile.

Preserve deterministic bounded work. The existing 512-node actor/query and 8,192-node level budgets already produce UNKNOWNs in the wool view. Instrument channel cost, cache invalidation, exhaustion rate and tick latency before selecting a larger model. Reserve work for active scans so passive event bursts cannot silently starve them. Exhaustion remains UNKNOWN, not a negative result. Geometry caches must invalidate on relevant block/door changes and avoid loading chunks.

## Test order: one mechanism per comparison

Tests below are proposed; their worlds and regressions are not prepared yet. Sensor-level native checks should isolate a channel first, followed by owner-visible normal behavior. Do not claim a combined detection proves either channel independently.

| Order | Matched comparison | Evidence required |
| --- | --- | --- |
| 1 | The same contact impulse on connected support versus genuinely disconnected support islands | Structural return only with a mechanical path. Audit surrounding foundations/walls to exclude a hidden connection; control airborne noise independently. |
| 2 | Same connected structure and impulse, bare contact versus wool cushion; then change only receiver contact | Source/receiver coupling changes strength. Airborne absorption remains a different experiment. |
| 3 | Same surface: still, slow steps, sprint, jump flight and landing | Actual emission/contact timing; flight loses foot coupling, landing creates an impulse. Crouched mining gets no blanket footstep discount. |
| 4 | Stationary reflector at equal range: front, side, rear; then a head scan | Smooth direction-dependent returns and acquisition based on new measurements. |
| 5 | Same target: open, partial aperture, bend, sealed barrier | Separate reachability, detectable return and usable localization; indirect estimates do not reveal hidden feet. |
| 6 | Identical geometry: reflective versus absorbing surface; then changed thickness | Measured model energy/uncertainty changes. No expectation of a universal all-or-nothing wool effect. |
| 7 | Same pulse/target with quiet versus band-overlapping noise; then spatially separated noise | Masking affects the received signal under the selected approximation; a noise source can still attract investigation. |
| 8 | Same geometry with stationary versus moving target during propagation; door closes between emission/return | Stale/ambiguous information remains bounded; no live-coordinate refresh from identity. |
| 9 | Multi-source, many actors, terrain edits, unload/reload and real LAN | Deterministic caps, useful UNKNOWN reporting, bounded tick cost, cache invalidation, lifecycle and fair server authority. |

Each result should record source/event, contact support, path topology, per-band received strength, delay, noise estimate, uncertainty, chosen behavior, work spent and UNKNOWN reason. Use identical seeds, positions and event timing within a pair. Human instructions describe the action; preserve the first account before explaining diagnostics.

The existing sealed-wall owner account supports the old negative behavior. The moving wool account supports detection in that view, not an isolated vibration result or stationary paired echo result. Appearance, started idle audio and the open-view human outcome remain separately unreported. Existing gates (652 unit/245 native) validate the current implementation and fixtures; they do not validate this proposed revision.

## Confidence and unresolved decisions

High confidence: separate channels, mechanical coupling, direction-dependent emissions, returned signal quality, masking and material-specific behavior are appropriate principles. Medium confidence: the proposed bounded graph/ray/aperture approximation can make those principles legible within this mod; performance and useful localization need measurement. Unresolved: monster hearing/contact sensitivity, material coefficients, detailed body reflection, gas state in late phases and revised audio mix.

The current 24-block limit is an authored game cap. The research does not validate reliable player detection at that range for a human mouth-click system. Preserve an exceptionally sensitive fictional creature if desired, while making its information obey the chosen physical rules. No current setting should be described as real-world calibrated.

## Concrete first implementation slice — September 30 review

This was the implementation proposal before coding; see the later handoff for delivered scope and tests. That source review confirmed the shared air/solid search and blanket crouching multiplier remain present. The existing `blindMiningVibrationAttenuatesPerSolidAndWool` test inserts barriers into an air corridor inside a stone fixture. It verifies the old cost rule, not connected mechanical supports; replace that obsolete contract with the comparisons below when implementing the new solver.

1. Introduce a typed structural event at `BlindArchitectEvents` / `BlindArchitectController.emit`: source kind, emission time, contact geometry and normalized source strength. Footsteps use the actual support under the foot footprint; landing uses the newly established contact; mining uses the struck block; a machine uses its connected body. Capture destructive impacts before the relevant block disappears. The source entity may help resolve a contact but must not become a passive track identity.
2. Resolve receiver contacts independently from the Architect's feet and collision geometry. `onGround` or `blockPosition().below()` alone cannot describe carpet, slabs, edges or multiple supports. Require a small, explicitly bounded contact tolerance and positive contact area. Source cushioning belongs to foot impacts; receiver coupling belongs to the receiving contact. Crouching while mining must retain the same tool impulse in a matched test.
3. Implement `BlindVibrationPropagation` over touching components of collision shapes. Connect neighboring shapes only where their occupied geometry meets over a face area; mere block adjacency or corner contact is insufficient under this approximation. Preserve internal disconnected components where present. A bottom slab and neighboring top slab must not gain a connection through an empty half-block. A wall or foundation detour can legitimately reconnect separated floor pieces. Collision geometry approximates mechanical structure; it does not establish real stiffness or fastening.
4. Use authored structural profiles with separate damping and interface/contact transfer. Track geometric length independently of loss. A bounded strongest-path calculation can use additive negative-log transfer losses with deterministic tie-breaking; retaining one dominant path is an explicit approximation that omits interference and summed multipath energy. Start with an explicitly named structural band, or a small fixed band set, and normalized receiver strength. Do not infer coefficients from block hardness or airborne wool absorption.
5. Return detection strength, receiver contact evidence, uncertainty and a reasoned outcome. Keep no contact, no connected route within the modeled domain, below threshold, unloaded geometry and budget exhaustion distinguishable. The last two are UNKNOWN. Bound contact resolution and shape work as well as graph expansion; a 512-node cap alone does not bound expensive geometry decomposition. Begin without a persistent cache, then profile before selecting caching or larger budgets.
6. Separate propagation diagnostics from the behavior measurement. A single passive magnitude cannot establish distance or bearing. Begin with an anonymous broad disturbance and listening/search behavior; narrower directional guidance requires an explicitly modeled receiver measurement, such as differences across supported contacts or new samples after movement. Neither quantized source coordinates nor the solver's complete route may silently become the hunting destination. Vibration alone cannot grant an identified melee lock.

The first native fixture should contain two support islands suspended inside an air clearance, with a single removable connector. Use the same injected contact impulse, supports, receiver, positions and event time in both cases. Call the structural sensor directly to exclude hearing and active echoes. Audit the entire fixture boundary, including the template floor, so removing the connector truly disconnects the islands. Require a positive connected control and a completed negative disconnected control; UNKNOWN cannot pass the negative case. Add a separate wall/foundation detour case to prove the solver does not mistake a visible gap for complete isolation.

Next, change only source cushioning on the connected structure, then only receiver contact, then partial-shape contact. After those pass, exercise real standing/walking/crouching/sprinting/flight/landing and accepted mining events with channel attribution in the trace. Separate source generation tests from propagation tests so changed impulses do not masquerade as changed material transfer. A stationary actor with no new event must not generate footsteps, and flight must not generate foot-coupled impulses.

Run the repository Java 21 verification/build workflow and the unchanged seeded stress matrix after production changes, inspect reports and source fingerprints, and refresh only the smoke Frozen Dawn jar with matching hashes. Prepare fresh owner-visible comparisons afterward. Coordinate Save and Quit, verify the closed world, and archive its save/log/evidence before any required client restart. Automated sensing proof and owner acceptance remain separate.


## October 1 implementation refinement: bounded contact search

The subsequent broad-floor replay exposed excessive search work in the strongest-path proposal above. The implementation now prioritizes a geometrically connected, fully attenuated path that already exceeds threshold. Its reported energy is a conservative value for the strongest path, not a proof of optimality. Search ordering never creates a contact or bypasses source/receiver coupling; if no valid path is completed before the unchanged budget expires, the result remains UNKNOWN. This trades an optimal diagnostic amplitude for useful positive coverage. The behavior consumes only anonymous reception and does not gain source coordinates.

A separate accounting repair credits air queries and return-path checks against their reserved share. It releases only capacity still available under the same512 actor/8192 level caps; no second allocation or persistent cache is introduced. The before/after regressions and exact broad-floor geometry are retained in the budget-fix evidence bundle. Broader live acceptance remains pending until its own first owner report and trace.


### October 1 live contact and presentation follow-up

The first wider-floor human view did not pass:9 of17 real footsteps were received and8 exhausted work, while the owner perceived little response. The BA wandered onto a four-support footprint that the earlier centered test did not represent. New regressions preserve those fractional positions and incoming-step ordering. Search guidance now includes receiver coupling and returns as soon as a valid conducting path is established. During current structural listening, active echo work leaves an unused256-unit structural share within the unchanged512 actor cap; this does not promise success after arbitrary other work exhausts the global budget.

A received scalar remains insufficient to locate a source. The revised listening presentation derives a continuous body sweep from the actor's entry orientation and episode age; refreshed footsteps extend the episode without resetting its sweep phase. It reuses the approved search accent on entry. Opposite-side equal disturbances must produce the same sweep, with no navigation or player identity. These are implementation choices for legibility and bounded attention, not measured elastic physics. Human acceptance remains pending after headless verification.

Run6 confirmed a 120-degree body response but the owner remained uncertain about its appearance. It exposed a first-input scheduling gap: a wandering scan consumed all512 work before a vibration track existed. The first-contact repair extends the same256-unit structural share to wandering scans, without adding hidden evidence or raising caps. The new native regression reproduces the original zero-work failure before the change. Run6 also contained six later events on a different floor from the stationary participant; their identities were not recorded. View7 uses a bounded lab-only mob isolation guard and records unexpected sources so contamination cannot pass silently. This does not change ordinary-world spawning. See the current handoff for verification and human status.

## October 1 direct-return echo slice

The owner approved an isolated click-to-return-to-directed-turn test after run7. The complete closed run7 save is preserved in `build/blind-architect-evidence/echo-acquisition-20261001/before-world.zip` (113 files, SHA-256 `8f5a94988012820295a988be2a21860f2724b0bf42fa96fffd7a460359afe0c1`). The first slice replaces active echoes' connected-air shortcut with direct collision-shape propagation, two-leg energy loss, a smooth authored emission pattern, body reflection and finite sampling of static clutter. The controller consumes quantized arrival bearing/range, with an explicit quality ceiling, after physical flight time plus separate processing delay.

This deliberately implements the direct-reflection comparison first. Bent-path diffraction, multiple reflections, ordinary hearing transmission and external noise masking remain unimplemented revisions. A blocked direct ray means no modeled direct return; it does not prove physical silence for all wave paths. Single-band coefficients,343m/s propagation, body scattering,12/24-degree bearing and0.75/1.5-block range resolution are authored assumptions. No measured waveform, HRTF, body-recognition classifier or clothing calibration is claimed. The approved400ms-class runtime clips are preserved as presentation; their measured durations are not used as physical pulse resolution.

Native verification passed654 unit/271 native/all266 required cases. Native comparisons keep the body pose/range fixed while changing emission direction, then compare the same return with a nearby reflective/absorbing wall, closure during delay, movement after emission, a connected bend without a direct path, and exhausted/unloaded geometry. The next human view holds a stationary participant on a separate support and records actual clicks, returns, selected evidence, head pose and isolation. Human outcome remains pending.

The old breach fixture used an overhead air opening, which required unmodeled diffraction. Its revised opening is a direct head-height slit across the wall while body clearance remains blocked. A one-cell slit exposed an aperture-search limitation; that failed attempt remains preserved. With the wider direct opening, bearing/range bins first led to an adjacent column before a later return refined the estimate, so the280-tick limit expired during a third physical cut. Its trace is preserved. The final400-tick bound retains real arrival and damage assertions at unchanged movement/mining speed; the third cut completes at295. This is native integration evidence, not human hunting acceptance.

The same 500 seeded stress cases now pass with the native suite (771 total). An initial rotated-stairs case lost three floor cells before any actor work; its unchanged initial terrain hash, failure, region data and logs are retained. The exact external writer was not identified. Stress cases now run in their own batches, separate from native tests that temporarily replace shared world state. No scenario, seed, rotation or assertion was removed. Final evidence and runtime state are in the agent handoff.

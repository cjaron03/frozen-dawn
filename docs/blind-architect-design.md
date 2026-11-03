# Blind Architect encounter and sensing design

Design direction approved by the owner September 30, 2026 Pacific. Branch `feat/blind-architect`, based on integration `0885e22`. The standalone runtime encounter is implemented locally. Numerical values below are initial tuning for focused playtests, not additions to locked canon. Automated evidence and remaining human checks are in [blind-architect-plan.md](blind-architect-plan.md).

The owner subsequently requested research into realistic echolocation and substrate vibration. [The acoustics research brief](blind-architect-acoustics-research.md) audits this initial model and proposes separate mechanically coupled vibration, returned-energy echo and passive-hearing revisions. The mechanically coupled vibration revision is implemented locally. The first direct-return echo revision passed native verification and its stationary human view demonstrated acquisition; the stationary closure view confirmed loss of fresh echoes and memory expiry but exposed an unreadable continued stare. The widening-search repair passed its matched human view: the owner recognized looking around after contact was lost, and the trace confirms widening motion with no fresh post-closure return. This accepts stationary loss-of-contact presentation; the first controlled underground-base hunt passed its human view with acquisition, four cuts, entry and a captured hit; the six isolated channel controls are complete, with the first hearing window qualified by rejected leftover-object noise. Indirect echoes and hearing/masking remain later stages. The first structural human comparison is in [the vibration protocol](blind-architect-vibration-playtest.md).

Source: [MACS §15.6](https://app.notion.com/p/39d7cfaa890181c1bad4f6babad80880). Current locked scope is one independent underground encounter. The later group siege, escorts and raid pressure/recovery remain core raid work. This draft supersedes the older September underground-sonar proposal for this variant; connected-air echoes never travel directly through rock.

## Experience

The player hears an irregular dry click beyond their torchlight. A few seconds later another comes from closer. Standing still in an open tunnel does not protect them: each click can return their shape. A sealed wall stops the echo, but mining on its far side can transmit a vibration. The player can move between clicks, lead the Architect toward a decoy, or cut off the air connection and stop making noise.

Its intelligence comes from following and testing a limited estimate. It sometimes reaches the wrong bend or breaks into an empty space. Its faster clicks tell the player that it has a better estimate. Loud digging gives warning before a barrier opens.

The visual design uses the Architect rig with no eyes or rendered nametag and a dark leather helmet. The owner requested the helmet after the first view because the whole appearance read as an Architect with its eyes removed. The helmet is a cosmetic native leather armor model/texture layer; actual head equipment takes precedence. The normal base attributes are currently 40 health and 6 attack damage; the Blind inherits normal Architect health/damage and existing difficulty modifiers. It does not gain extra health, sonic attacks, heat vision or damage scaling from this slice. Its idle pose can incline its head during a click; it turns toward a received direction before moving, rather than continuously staring at a hidden player.

## What each sense provides

| Sense | Trigger | Path | Knowledge | Player response |
| --- | --- | --- | --- | --- |
| Echolocation | The Architect clicks | Direct collision-clear outward and return legs within24 blocks | A bearing/range estimate supported by returned energy and sampled static clutter | Move after the click; leave usable range; close the return path |
| Hearing | A real noise occurs | Connected air, event-specific range up to 24 blocks | An uncertain event location, usually anonymous | Stop emitting noise; redirect it with a decoy |
| Vibration | Actual contact impulse or operating machine | Touching collision components, material damping and source/receiver coupling | Anonymous disturbance within the local structure; listening scan without a source destination | Soften steps, mechanically isolate supports, interrupt contact, pause noisy activity |

No sense grants an inventory, health reading, machine owner, room map or automatically identified projectile thrower. Server-side entity references can validate a real close contact but cannot refresh a distant estimate or convert event ownership into tactical knowledge.

The [cave range revision](blind-architect-range-revision.md) raises structural delivery to 40 blocks and checks strong mining reception at 30 connected blocks. Exposed forward echoes are tuned for 10–20 blocks within the existing 24-block query boundary. The owner removed the audible/sensed breathing experiment after view19; the original visual cold puff remains. Range values are authored, and current verification status is recorded in that revision.

## Echolocation

The current slice models direct reflections. It separates outward length, return length, emission directivity, projected exposed body area, normalized returned energy and sampled static clutter. A clear air route around a bend does not yet establish an echo: indirect propagation is later work. The existing air graph continues to serve passive hearing. No indirect echo falls back to hidden player coordinates.

Initial runtime pulse cadence:

| State | Time between clicks | Presentation |
| --- | --- | --- |
| Wandering | 2.5–3.5 seconds | Isolated dry clicks with slight irregularity |
| Investigating | 1.5–2 seconds | A quicker sequence as it approaches an estimate |
| Confident pursuit | 0.9–1.2 seconds | Urgent clicks; deliberate pauses remain between samples |
| Close combat, within 4 acoustic blocks | 0.5–0.7 seconds | Tight clicks sufficient to support local strikes |

Jitter is seeded and affects cadence only. Detection is governed by propagation, distance and signal history, rather than a random chance of finding a player who met the rules. The first click occurs after the existing spawn/load warmup; there is no silent initial acquisition.

An authored smooth power pattern is strongest in front and has a weak rear lobe. Each leg contributes spreading and air loss. Body reflectivity is an authored constant; it does not inspect inventory or armor. Five bounded static-scene rays sample nearby bearing/delay returns, with material-dependent reflection. This is a conservative geometric approximation in one band, without phase, diffraction, transmission, detailed surface scattering or a full reverberant field.

Detection requires normalized energy at least0.025 and a signal-to-noise ratio at least0.75. Usable localization requires energy at least0.10 and a ratio at least3. Strong returns use12-degree bearing bins and0.75-block range bins, confidence0.85 and a0.90 quality ceiling. Weaker detected returns use24-degree/1.5-block bins, confidence0.40 and a0.55 ceiling. Repeating the same ambiguous result cannot cross the lock threshold. These are game parameters, not measured human or animal sensitivity.

The behavior receives only the quantized arrival bearing/range estimate and uncertainty. It projects that estimate from the emitting receiver pose, then resolves approximate height against local loaded terrain. It never anchors the estimate to the sampled player's feet. Geometry and body bounds remain private propagation inputs or diagnostics. The head turns toward an admitted measurement; moving after emission cannot refresh that observation from a UUID. After forty ticks without another echo, it scans progressively farther around that frozen region using the stored uncertainty and age. The actual head orientation aims subsequent pulses. A fresh return focuses it again. After contact expiry, stationary idle scans use the actor's own entry heading; no old subject position survives as a scan target.

Physical arrival uses the frozen round-trip length divided by343m/s with sub-tick milliseconds retained. Behavior waits for the next whole tick plus a separate two-tick processing delay. Return validation checks the frozen route and bounded receiver movement without rereading the player's location. A newly closed obstruction cancels the return; incomplete work remains UNKNOWN. The speed and pulse resolution are fixed authored assumptions. The approved audio is unchanged and is not itself a calibrated simulated waveform.

Stationary bodies can return echoes. Wool carpet does not erase the body return, while a wool surface can change competing reflections. A solid closure blocks the modeled direct path. Darkness does not affect this sense.

## Hearing and decoys

Listen to real server game events and explicit emissions for sources vanilla does not cover. An event creates a point-in-time emission, never a continuously followed entity. The originating sound does not need to be played by the client for detection to work.

Initial open-air ranges:

| Emission | Maximum acoustic travel | Result |
| --- | --- | --- |
| Eating/drinking | 8 blocks | Small anonymous noise region |
| Walking footsteps | 12 blocks | Anonymous step region |
| Sprinting/jumping/landing | 20 blocks | Stronger transient movement region |
| Door operation | 16 blocks | Investigate the door region |
| Snowball/projectile impact | 20 blocks | Investigate the impact region |
| Mining/placement | 24 blocks | Investigate the changed-block region |
| Note block/piston | 24 blocks | Investigate the mechanism region |

These ranges use the same air connectivity and bend penalties as echoes. Hearing alone provides direction and an uncertain source region, with at least two blocks of uncertainty; it does not grant player identity or authorize melee. Sneaking and wool reduce the vibration channel only. They do not suppress an otherwise emitted eating, mining or mechanism noise in open air. Footstep emission must follow real movement; standing still emits no synthetic footstep, and sneak movement must not inherit vanilla's blanket vibration suppression for the air channel.

Snowballs, doors, pistons and note blocks can create a credible investigation track. Repeated noise makes an event region worth checking, but still does not confirm a player or make it a player-specific Maeve belief. At the decoy location the Architect pauses, clicks and searches locally. No echo means the expected occupant is absent: its estimate weakens and it leaves. It cannot inspect a projectile's owner and run back to the thrower.

A recent clear player echo can win over a decoy. Once that echo becomes stale, a nearby credible decoy can draw it away. This makes the sequence meaningful: break contact, then lure it; a note block is not a universal combat interrupt. Continuous mechanisms refresh one track at a capped rate rather than filling an unlimited event queue.

## Vibration through rock

`BlindVibrationPropagation` resolves the actual supporting collision shapes beneath the source and receiver, including carpet, slabs, fence height and contact across a footprint. Mining couples into the accepted struck block, machines into their body, and block-destruction events can retain the event's pre-destruction material. Air and point/edge-only adjacency do not form mechanical connections. Foundations or walls can carry a signal around a visible gap.

The solver searches touching collision components with authored low-impact-band material profiles. Received normalized strength is source strength times source/receiver coupling and exponential path loss. Geometric length, bulk damping and interface transfer are distinct; identical slab cross sections do not incur a loss merely for crossing a block boundary. Search prioritizes progress toward the receiving contacts, including their contact-patch coupling loss, and returns immediately at a fully validated path above threshold. It does not continue searching after reception has already been established. Its reported energy is conservative for the strongest path: it can understate that maximum rather than spend the work limit proving optimality. It does not model phase, resonance, summed paths, water propagation or calibrated travel time. The 24-block query domain and detection threshold0.20 are game assumptions.

Initial strengths remain walking6, sprint/landing10, mining/placement12 and operating machine10. Crouching multiplies step generation by0.25, but never tool strikes or landing solely because the player is crouched. Wool/carpet profiles independently soften foot impacts, damp transmission and change receiver coupling. No foot contact means no foot-coupled source/reception. Material constants are authored approximations and do not come from block hardness or airborne absorption.

Only an anonymous local disturbance enters behavior: receiver region with24-block uncertainty, no source UUID or measured source range. It prompts a forty-tick listening episode, refreshed by received impulses. The body sweeps continuously around its entry orientation, with an additional head scan and the approved search accent on entry; repeated steps do not restart the sweep phase. Clicks can collect separate echo evidence. The sweep receives no source bearing. It cannot authorize source-directed digging or melee. Hearing or echo evidence takes precedence over this unlocalized cue. Diagnostics retain the actual solver contacts/path and strength for testing; those do not become navigation inputs.

A Geothermal Core emits a low machine pulse every five seconds while loaded and operating. Processing machines emit only while operating. The pulse is located at the machine, and does not identify a player, owner or occupied shelter. A core can attract an investigation into an empty room. The player can separate/dampen the machine, create a nearer false source, or exploit the heat exposure; sneaking cannot silence a running core.

Mining broadcasts from actual block work at a capped rate, including progress before a successful break. Otherwise an unbroken stone wall could hide a player who is audibly mining it for seconds. A server hook needs to validate ongoing digging/actual block changes, not accept arbitrary client noise positions. The actor ignores its own clicks, footsteps and digging to avoid feeding its own pursuit loop. Nearby Blind actors' emissions cannot manufacture a player track.

## Signal memory, lock-on and loss

Keep at most four local tracks. Each stores modality, frozen region, observed time, confidence, uncertainty and expiry. Anonymous sources stay anonymous. Select a fresh confident player lock first, then actionable hearing/echo regions before unlocalized vibration; within those groups, use the nearest remembered region from the actor; ties use confidence, recency and stable ordering. A brief switching margin prevents two equal signals from making it spin. Danger reactions such as a damaging bell interrupt selection immediately.

Proposed thresholds: 0.35 starts investigation; 0.75 plus a fresh player-classified echo starts confident pursuit. These are local sensing thresholds, separate from MACS's historical 0.75 counter-commitment threshold. Anonymous noise never becomes a player-classified echo merely through repetition.

Fresh samples can direct movement for six seconds. Confidence begins dropping after two seconds without reinforcement. At six seconds, the estimate is stale and cannot authorize melee or a new confident lock. The actor may search the remembered region for another six seconds, without updating its position from the remembered subject. Then it releases the concern and wanders. Repeated failures at one anonymous mechanism create a short local investigation backoff, initially ten seconds, so a running core does not keep it stuck in a search loop.

On confident player lock, send exactly `Something has found you.` once to that player and instantly upload the justified estimate/provenance to Maeve. The message is not repeated for every click. Reacquisition can show it again only after real loss and a short message cooldown, initially ten seconds. Click captions remain `Something clicks in the dark.` A far uncertain return can be heard approaching before this notification.

Close combat needs a fresh close echo (up to eight acoustic units, at most 25 ticks old) or immediate physical contact. Turns contribute to acoustic cost; the strike additionally requires actual local range at most 2.8 blocks and line of sight. Aim/approach uses the recent estimate; the strike must overlap the player's actual local hit volume. No hit through a wall, no reach extension and no inherited live hidden-position chasing. A direct hit on the Blind can produce a close directional contact, but a projectile owner's current coordinates do not become a return.

## Digging and escape

Prefer a locally available walking route toward the estimate. A fresh localized hearing/echo estimate can justify approaching a locally inspected wall and digging toward that snapshot. Unlocalized substrate magnitude alone cannot authorize that destination. After genuine echo contact, a newly closed obstacle can also justify briefly inspecting/breaching the remembered path; blocking the air connection stops new returns, not its memory of the last one.

Start with twice the ordinary Architect break duration, including the cap. Keep visible cracks and louder deliberate tool sounds, with a tell before the first crack. Inspect only adjacent reachable obstruction cells, not a remote player-centered shelter scan. Keep normal break restrictions, server protection and mob-griefing policy.

Once the estimate is stale, allow at most the currently started block attempt, bounded by the remaining six-second search window; cancel if it cannot finish within that window. Start no new blind excavation without a fresh justified signal. Each successful opening triggers a new click rather than silently updating a hidden player's location. A sealed quiet shelter with no nearby operating machine eventually loses it; an operating machine can legitimately keep drawing it to the machine region.

## Noise pain and heat

Proposed bell response within eight propagated acoustic blocks: two health points of acoustic damage, 1.25 seconds of stun, then five seconds of strong retreat from the sound origin. A twelve-second acoustic-response cooldown prevents bell spam from stacking stun/damage or resetting the retreat timer. During cooldown, repeated bell noise cannot become a lure that cancels escape. The bell user must still survive its approach and have a connected sound path.

Explosions have the same stun/retreat response when sufficiently strong and locally propagated. Apply physical explosion damage once. A non-damaging but strong transmitted blast may cause the small acoustic hit; do not add that hit on top of an already registered physical explosion hit. Acoustic cooldown never grants immunity to normal explosion damage. Noise pain clears the pursuit estimate, and fleeing ignores new hunting tracks until its minimum escape period ends.

Heat is tested at actual local exposure. The Blind can follow a machine pulse into core heat, take damage, visibly recoil and back off. It does not route around a globally known heat field before being burned. A short remembered local hurt region can prevent immediately stepping back into the same spot; it carries no inferred player location and does not create thermal immunity. This requires a narrow core-exposure hook because the current Architect burn loop only checks Thermal Heaters.

## Spawning, attention and lifecycle

Current ambient spawn boundary: regular Architects spawn only at Y>0; Blind Architects spawn at Y<=0, including Y=0. Dark valid underground terrain at or below Y=0, 24–64 blocks from an eligible player, a 2% chance per ten-second check, at most one Blind within 96 blocks, and ten minutes of saved regional cooldown after a successful spawn. Also respect the existing global physical pawn cap, Architect configuration, difficulty and mob-spawn gates. These are ambient tuning values; fixtures should force placement explicitly and disclose it. Spawn selection gives no knowledge of the player. No spawning before Phase 6 late or after ERASED.

A wandering actor occupies population capacity but no focus slot. Investigation/hunting is one concern at the existing tracking priority and consumes shared Maeve attention; anonymous investigation needs a local actor/track concern key, without inventing a player UUID. Echo sharpening does not create additional slots. Keep existing tier capacity and five-second minimum dwell. If admission is deferred it remains wandering; if evicted, it audibly slows its clicks, cancels pursuit/digging and leaves. Observation upload records an actual return even when attention cannot admit sustained tracking; it never authorizes attention-free hunting.

Persist Blind appearance, spawn cooldown and appropriate local pain cooldown. Local contacts, pending echoes and active tracking are transient on unload/reload; the actor must acquire a new sample. Preserve physical population claims across chunk unloads. The normal sight-based observation, reconnaissance, counter and convergence-order paths must explicitly exclude the Blind; its death-region reporting remains available.

After ERASED, clear all local targets, pending returns, tracks, excavation and attention immediately. Existing Blind actors wander and click aimlessly; incoming damage does not restart hunting. No new Blind spawn. E11 will remove survivors when its authoritative lifecycle exists; the current integration only has an unimplemented E11 hook, so this draft does not claim an E11 implementation.

## Implementation boundaries and proof

Owner audio acceptance: "i like it" after hearing the throat-comparison preview.
Use the six `throat_click_*` pulses and the idle/search/lock vocal accents as the
selected runtime bank. `tools/audio_sources/blind_architect/selection.json`
records the reviewed files and hashes. In-game mixing and spatial/timing
acceptance remain pending.

Audio preparation, September 30: the owner approved CC0 human tongue-click processing, then requested a direction close to The Last of Us Clickers. [Mouth Clicks by Xemptful](https://freesound.org/people/Xemptful/sounds/370869/), [tongue click by fleurescence](https://freesound.org/people/fleurescence/sounds/573148/) and [Weird Throat Clicking by Fupicat](https://freesound.org/people/Fupicat/sounds/688417/) were acquired through their official public high-quality MP3 sharing URLs. Individual source pages and the canonical [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) page were archived before processing. The earlier six dry clicks remain for comparison. The revised audition adds six short throat pulses and three longer idle/search/lock vocal accents; all are mono 48 kHz Ogg assets. It uses the actual CC0 throat performance, without game/show recordings. The [source ledger](../tools/audio_sources/blind_architect/SOURCES.md), generators and manifests retain exact source encodings, processing, versions and hashes. The owner approved the revised audition direction; runtime event integration is complete and in-game listening remains pending. The AI will control cadence; example reels are illustrations. Extra transients within one burst decorate one sensing pulse and grant no extra returns. Longer vocal accents have separate presentation scheduling and do not emit extra sensing pulses. Kenney's [CC0 Impact Sounds](https://kenney.nl/assets/impact-sounds) remains an optional unselected foley source; none was used.

Use a saved/synced variant and a dedicated controller before normal Architect AI acquisition. Reuse physical navigation, tools, attacks and crack effects only through frozen, validated destinations. Do not implement acoustic sensing by overriding visual LOS globally. Sync only the appearance/presentation state needed for rendering, following [NeoForge's entity data guidance](https://docs.neoforged.net/docs/1.21.1/networking/entities/); server tracks remain authoritative and private.

Air propagation retains its target-directed weighted half-block search; structural vibration uses a separate touching-component graph. Candidate enumeration stops at 32 local entities and tests the nearest 16; memory holds four tracks and pending returns stop at eight. Air queries cap at512 visited nodes; structural queries cap at512 charged work units covering contact resolution, geometry and graph work. Structural controller calls reserve the unspent portion of a128-unit air-sensing share. During wandering and current vibration listening, active echo scans leave the unused portion of a256-unit structural share; structural work already spent counts toward that share. This allows a first contact to receive work before any vibration belief exists. Scans following actionable hearing/echo keep their previous policy. Other hearing and pending-return work keep their existing policy, so this is not an all-channel delivery guarantee. Air queries and return-path checks count toward that share, so already consumed air work is not reserved twice; passive hearing retains its existing work policy. Each actor has 512 units of work per server tick under an 8,192-unit level budget, including pending air-path revalidation. Geometry is sampled synchronously at emission; completed frozen paths are admitted after the 4–8 tick delay only if still open. This replaces the proposed 2,048-cell multi-tick air search so a return never combines geometry from different ticks. Budget exhaustion is an explicit unknown/miss in diagnostics. Profile coverage in spacious and winding caves before tuning; the cap does not guarantee every connected player within 24 acoustic units is returned.

Use connected-air aperture checks for doors, slabs, snow, water and vertical openings. Air paths must not corner-cut through touching solid diagonals. Differentiate acoustic aperture from actor clearance. No query loads a chunk. Start with bounded searches and no persistent acoustic world map; optimize only after performance evidence identifies a need.

Required native fixtures should prove: silent open-cave return; bent-cave return; no return through a sealed wall; post-click relocation; cancelled queued return after a door closes; uncertainty through a narrow aperture; identical impulses on connected/disconnected supports and a foundation detour; source/receiver contact and cushioning; partial slabs and fences; posture/flight/landing; actual crouched mining without a blanket discount; structural budget/unknown cases; eating noise; anonymous projectile/mechanism decoys; core pulses without inferred occupancy; local burn/backoff; bell cooldown; explosion damage deduplication; nearest confident multiplayer signal; attention deferral/eviction; death before contact; save/unload/reload; ERASED clearing and no retaliation.

The human pass must establish audible direction/range, readable cadence, adequate warning before a breach, useful movement windows, convincing false investigations and strong retreat after noise pain. Keep the first account before explaining diagnostics. The fixture can prove mechanics while the owner judges combat/audio/feel. Run `architectVerify` and the unchanged seeded `architectMonkey` matrix after implementation; the implemented slice adds 23 native regressions and six signal-memory unit cases. See the evidence handoff for actual gate results.


## Isolated channel verification, October 1

The owner accepted the combined constructed-base acquisition, breach, entry and hit. The next six human views compare hearing, substrate vibration and direct echo with matched physical controls. Their exact native fixtures pass with no competing sense or unknown query. Two larger bent-air fixtures exceeded the ordinary budget and remain a known limitation; a short enclosed duct supplies the positive hearing control. No production sensing or behavior changed. A nearby unblocked player hit provides a separate contact callback (currently represented as ECHO memory), while bell/explosion/heat are defensive triggers. Source-hook and human coverage are distinguished in [the channel guide](blind-architect-channel-playtests.md).


Actual view 12: twelve mining observations alone drove HEARING and expired visibly. No vibration or echo entered memory, but leftover dropped objects caused seven early rejected LANDING queries. This exposed a lab isolation gap, not a production sensory response change. Preserve the result and tighten object cleanup before the sealed control.


The lab now archives complete NBT for leftover items and experience before removing them, and archives newly generated scene drops before blocking their admission. Start refuses an unexpected object or additional participant, and every frame checks the source inventory. The native regression preserves old item/XP contents, blocks and preserves a new coal drop, and refuses an object moved into the area after preparation. All six native pairs pass again with clean source inventories, zero landing events, zero unknown queries and unchanged sensory decisions. This repair affects lab isolation only. The original view 12 remains preserved with its early contamination explicitly recorded.

The repair gate passed 654 unit tests, 275 native cases, all 270 required cases and 83 function parses. Every production class, sound and asset matches the prior build. Existing deprecation warnings remain. All 156 closed-save files were reverified unchanged; no datapack or gameplay file was edited. Built/smoke SHA-256 `337283a8a5db16ad26f0d4cb24761bcbe75158df71d1b6bced57e12af3d4c9d5`. Evidence: `build/blind-architect-evidence/channels-isolation-20261001/verification.json`. Prior 500-seed stress evidence is retained because production bytes are unchanged. The owning client has restarted; the separate smoke profile reads its copied jar on its next restart.


View 13 is complete. The owner reported that the looking around looked good. All 480 frames remained in WANDER with no selected signal or memory track; the head spanned 160.00012 degrees and body 156.08452 degrees. Fourteen accepted mining TOOL inputs at timers 112–242 produced no hearing memory and fourteen structural NO_PATH results. All eight echo checks returned NO_DIRECT_RETURN. Geometry and source inventories stayed clean throughout, with zero landing events, unknown queries, dropped journal entries or unexpected targets. This passes the sealed hearing control and readable unaware scanning; it does not indicate that the actor located the player. The positive hearing view 12 remains qualified by its seven early rejected landing queries; all twelve admitted observations there still match accepted mining inputs.


View 14 is complete. The owner reported a look in their general direction followed by looking all around. All 17 ordinary footsteps reached the actor through connected stone, with work 107–123 and no exhausted query. VIBRATION alone drove 197 LISTEN frames at timers 105–301 (9.85 seconds), with a 120-degree body sweep and 147.37-degree head span. All eleven echo queries returned NO_DIRECT_RETURN; no hearing track entered memory. All 480 frames have intact geometry, clean source inventories, no unexpected target and no dropped journal entry. Vibration carried an anonymous disturbance at the receiver region (10,-44,5), uncertainty 24, without player identity or coordinates. A look toward the participant during this broad scan does not establish directional localization. This passes connected-floor vibration and its visible listening response; the gap control is next.


View 15 is complete. The owner reported no reaction beyond continued looking around. All sixteen actual footsteps returned NO_PATH across the disconnected supports, with work 186–187 and a grounded receiver at every input. All eight echo checks returned NO_DIRECT_RETURN. The actor remained in WANDER with zero memory tracks for all 480 frames, scanning 160 degrees with its head. Geometry and source inventories stayed clean, with no landing event, exhausted query, unexpected target or dropped journal entry. This passes the disconnected-floor control. Together, live views 14/15 show 17/17 footstep detections and 197 LISTEN frames on connected stone versus 0/16 detections and zero LISTEN frames across the gap. Human movements and ordinary random sequences differ; native pairs separately use matched seeds and inputs.


View 16 is complete. The owner independently reported faster clicks and facing directly toward them. Nineteen direct echo queries localized the quiet stationary participant, and all nineteen measured returns were admitted after three ticks. ECHO alone drove 437 PURSUE frames from timer 44 through 480, with no hearing track or vibration query. Every track retained its actual measured region, range, uncertainty and quality bound. Click intervals tightened from 67 ticks (3.35 seconds) to 18–24 ticks (0.9–1.2 seconds); maximum facing error during the last twelve seconds was 0.000034 degrees. All 480 frames kept geometry and source inventories intact, with zero unknown query, unexpected target or dropped journal entry. This passes quiet direct-echo acquisition and its audible/visible presentation. Physical confinement and disabled mining keep approach/breach outside this view.


## Completed physical channel comparisons

| Channel | Positive human view | Physical control | Interpretation |
| --- | --- | --- | --- |
| Hearing, 12/13 | 12 mining-linked hearing observations; 317 selected frames | Sealed duct: 14 mining inputs, zero hearing tracks | Hearing response and expiry demonstrated; view 12 quiet-baseline caveat below |
| Vibration, 14/15 | Connected stone: 17/17 footsteps detected; 197 LISTEN frames | Floor gap: 0/16 detected; all NO_PATH | Mechanically connected alert and listening, without player coordinates |
| Direct echo, 16/17 | Quiet open aperture: 19/19 measured returns admitted; 437 PURSUE frames | Sealed aperture: 0/8 admitted; all NO_DIRECT_RETURN | Direct-return acquisition with clean positive/negative controls |

View 17 confirmed the owner's report of continued looking around: eight active echo queries all returned NO_DIRECT_RETURN, no sensory belief was created, and all 480 frames stayed in WANDER with clean geometry and sources. Click intervals stayed at 2.7–3.35 seconds. In view 16, intervals tightened to 0.9–1.2 seconds after acquisition, and the actor sustained facing toward the quiet player. Human walking and ordinary actor random sequences vary between paired views; native comparisons separately match seeds and inputs.

View 12 admitted only hearing, and all twelve observations match the twelve mining inputs. Its early quiet baseline nevertheless contained seven unrelated landing queries from leftover items/XP; those exhausted structural budgets and created no belief. The source-object isolation repair archives full NBT before removal or spawn rejection, refuses dirty starts and checks every frame. The same six native pairs passed again with no stray event or unknown query. Views 13–17 are clean throughout. Preserve the original positive hearing result with this qualification; do not call all six human windows pristine.

The owner asked whether the actor normally walks. Production WANDER attempts a random walking route every 40 ticks when navigation is finished; if no route is active, it surveys with a head/body scan. All six channel captures ran ordinary enabled AI, but their glass corrals and disabled block breaking kept the physical isolation intact. Completion then stops navigation and sets NoAI. These views establish sensing and presentation. The earlier human base view 11 separately demonstrated approach, four cuts, entry and a hit with mining enabled.

The short bent hearing duct passes; larger bends previously exhausted the ordinary air-search budget and remain a limitation. Native coverage includes 24 delivered-event air conditions, nearby/far/occluded damage contact, grounded/airborne/teleport-sized crouch callbacks and the existing mining, machinery, projectile, wool/contact and pain/heat regressions. This does not establish a human replay of every item GUI, machine or posture. Reflected echoes, richer masking, natural spawning, arbitrary natural cave hunts and LAN remain outside this acceptance.

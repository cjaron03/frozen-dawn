# ORSA SMS-1 "Hyperion" grid storage: design

Status: approved by the owner, 2026-10-30. Branch `feat/hyperion-storage`, cut from `origin/feat/maeve-director` at 72f6a574. No code yet. The next step after approval is an implementation plan.

## Why

Storage is far out of scale with the reactors. A Battery Bank holds 2 MJ (4 Power Cells of 500 kJ). Output runs from the Geothermal Core (20 kW) and the CFU-7 "Hearthstone" (~120 kW) up to the SFR-1 "Solstice" (~330 kW net) and the AMR-1 "Icarus" (0.6 to 1.7 MW net). The Icarus startup draws about 1 MW through its reserve-charge and stabilizer steps (about 50 s, so roughly 50 to 70 MJ). Booting it today takes a wall of 25 to 35 Battery Banks.

The owner's picture: when you build up to a fusion reactor or the Icarus, you should not keep adding battery packs. You build one giant battery and put all that power in it.

## What it is

The ORSA SMS-1 "Hyperion" (Superconducting Magnetic Storage) is a multiblock battery made of superconducting cells. The cells must be held below a quench point by a cryocooler. The cooler is the battery's self-discharge, so the battery works anywhere but rewards building outside in the cold, like the reactor radiators. If heat wins, the cells quench: the charge is lost in a loud, visible venting.

The name is a tribute to Dan Simmons' *Hyperion*: the Time Tombs sit inside a field that holds back decay, as the cold holds this battery's charge. Hyperion is also a Titan from Greek myth, which pairs with "Icarus". Only the word is borrowed; no lore text, logos or invented terms.

## Goals

- One multiblock replaces a wall of Battery Banks. It scales from 3x3x3 to 7x7x7, roughly 140 MJ to 5 GJ.
- It is useful in every phase. The cold is the upgrade: the colder the surroundings, the less the battery spends to hold its charge.
- Ports cap the charge and discharge rate, so it buffers a grid rather than acting as an instant source.
- Reactors on the same grid push to fill it.
- The quench is dramatic, readable and fair: a warning ladder before any heat quench.
- It looks like a real superconductor (see Look) and has a signature animation: a puck levitating on the controller.

## Non-goals (this slice)

- Survival recipes, loot or progression gating. This slice is creative tab and `/give` only, like the first Solstice slice, so the owner can playtest and iterate. Recipes come in a later slice (intended tier: around the Hearthstone, with cell cost gating large builds).
- Any damage from a quench: no harm to players, blocks, machines or room air.
- A safe shutdown that keeps the charge. Draining it is the only safe way to dismantle it.
- Replacing or changing Battery Banks, Cell Docks or Power Cells. They stay as they are.
- Suit relay of Hyperion alarms, and advancements.

## Anti-goals (failure even if it technically works)

- A battery that is useless until the late game because it only works below a real superconductor's critical temperature.
- A grid outage wiping out the stored power.
- A heat quench with no warning.
- A generic glowing sci-fi battery look instead of the photographic superconductor look the owner chose.
- Any voice-like sound in the audio, such as the low sweeping "creak" from the first sketch that read as a villager.
- Unbounded per-tick work, forced chunk loads, or a structure that breaks other machines or networks.

## Decisions

Each item is marked by who decided it: **owner** (stated or chose it), **agreed** (proposed by the agent and accepted by the owner) or **agent's call** (settled by the agent; the owner can still change it).

### Structure

- **Frame and cell walls (owner chose layout B).** A rectangular box. The 8 corners are frame blocks. The remaining edge positions are frame blocks or fittings. Face positions are cells or fittings. Every interior position is a cell. The frosted cells are what you see on the outside.
- **Size: each side 3 to 7 blocks (agent's call).** Cuboids are allowed, not only cubes.
- **Fittings may sit on edges as well as faces, but never corners (agent's call).** This keeps a 3x3x3 useful: with all fittings on edges it keeps 7 cells.
- **Required fittings (agent's call, from agreed parts):** exactly 1 Controller, at least 1 Port, at least 1 Cryocooler and exactly 2 Quench Vents. A Quench Vent's jet leaves from its outward face.
- **Capacity scales with cell count (agreed).** There is no higher tier; size does the scaling.
- **Suggested block names (agent's call):** Hyperion Frame, Superconducting Cell, Hyperion Port, Cryocooler, Quench Vent, Hyperion Controller.

### Power and rate

- **Each Port moves up to 500 kW in either direction (owner chose ports over size; number is agent's call).** More ports, more rate. Charging from surplus and discharging into shortfalls are automatic, like a Battery Bank today.
- **Ports are Power Network endpoints.** They link with the Conduit Binder at the normal 16-block device range and obey the existing link rules.
- **The whole Hyperion is one storage node (agent's call).** If its ports link to two otherwise separate networks, the battery joins them into one component, as a relay would.
- **Reactors push to fill it (owner).** Load-follow targets demand plus the Hyperion's remaining port charge headroom (ports x 500 kW, capped at what it can still absorb), until it is full; then they fall back to following demand. Battery Banks keep today's 5 kW charge margin.

### Cold, cooler and self-discharge

- **Superconducting cells with a quench point (owner).** Agent's call for the number: about -180C, near real high-temperature superconductors. The cooler holds the cells somewhat below it, for example -200C.
- **The cryocooler runs on the battery's own charge (agreed).** Its draw is the self-discharge (owner liked the irony: "my battery uses power to hold power"). The draw grows with how warm the surroundings are and is zero when the surroundings are at or below the quench point.
- **A grid outage never quenches it (agreed).** With no grid power it keeps itself cold from its own charge and just drains a little faster. When it runs empty, there is nothing left to lose.
- **Surroundings temperature (agent's call):** the temperature the mod already reports at the spots just outside the structure's shell, using the loaded-only query (`TemperatureManager.getLoadedTemperatureAt` or equivalent). Outdoors that follows the phase curve and weather. In a managed room it is the room temperature. Underground it is the ground temperature. The builder chooses how to combine the sampled spots (average, or weighted toward the warmest).
- **Self-discharge targets (agent's call; tune in playtest):**

  | Surroundings | A full battery loses about |
  |---|---|
  | Heated room, 20C | 2% of capacity per hour |
  | Surface at -45C (mid game) | 0.5% per hour |
  | Surface at -120C | 0.1% per hour |
  | At or below the quench point | nothing |

### Quench

- **Cause 1: heat the cooling cannot keep up with (agreed).** Each Cryocooler can remove a limited heat load. The load grows with size and with warmer surroundings. Nearby active heat sources (a running reactor, a hot radiator, a heater) add a large load. When the load exceeds what the coolers can remove, the cells warm toward the quench point.
- **Warning time (owner):** about 15 minutes from first warning to quench when the surroundings are below 0C, and faster above 0C. Agent's call for the rest of the curve: about 5 minutes in a 20C room, 1 to 2 minutes right beside an active reactor.
- **Cooler sizing targets (agent's call; tune in playtest):** one Cryocooler is enough for a 3x3x3 in a 20C room. A 7x7x7 in a 20C room needs about four. Outdoors below 0C, one covers up to about 5x5x5. Building against a running reactor overwhelms any normal cooler count.
- **Recovery (agreed in the audio ladder):** if cooling is fixed in time, the cells cool back down, the warning stops and the all-clear plays.
- **Cause 2: breaking any part of a charged structure (agreed).** Any cell, frame block or fitting removed while it holds charge quenches it at once, with no warning ladder, straight to the bang (immediacy is the agent's call). Explosions, pistons and commands count the same as a pickaxe.
- **An empty battery comes apart quietly (agreed).** Every block drops itself.
- **What a quench does (owner):** the stored charge goes to zero. It is loud and visual only, with nothing damaged. The structure stays intact and can be recharged once it has cooled back down (agent's call).
- **Warm and empty (agent's call):** a battery whose cells are above the quench point cannot hold charge. While it cools down, its cooler runs on grid power through its ports and it accepts no charge until it is cold.

### Look

The owner's reference is the classic photo of a superconductor puck levitating over a row of magnets, wrapped in rolling cold vapour. The photo is not committed; only its features below are used.

- **Cells (owner):** matte deep charcoal-black ceramic with fine scratches. In the cold, frost collects along the upper edges and cold vapour spills off and sinks. The cells do not glow or show charge.
- **Heat stress (owner):** the same black, with clear heat stress. Frost and sinking vapour are gone, hairline cracks glow dull orange and pulse, and thin wisps of steam rise instead of fall. Near quench the cracks spread and go white-hot.
- **After a quench (agent's call):** the cracks fade to dark scars as the cells cool back down.
- **Controller window (agreed, owner: "I like that"):** behind a small window, a puck levitates over magnets as a physical gauge. It floats higher the more charge is stored, bobs gently when idle, wobbles as heat builds, shudders near quench, and drops onto the magnets with a clunk on a quench.
- **Warning lights (agreed in the audio ladder):** the Controller and Ports show warning lights during the warning stage.
- **Quench venting (owner):** steam jets out of both Quench Vents for 5 to 10 seconds.
- **Reference sketches:** [docs/art/hyperion-storage/](art/hyperion-storage/). `cell-look-sketch.html` is the accepted direction (v3) and `layout-options.html` shows the layout choice (B chosen). Both are throwaway; real textures, models and particles follow the repo's art process.

### Audio

The owner heard a synthesized sketch of each cue and accepted it ([audio-ladder-sketch.html](art/hyperion-storage/audio-ladder-sketch.html)). The ladder is the owner's (chirp, then warning, then quench); the remaining stages are the agent's, and the owner accepted them.

1. **Normal:** a low cryocooler hum with a slow compressor pulse. It gets quieter as the surroundings get colder, so a battery outside in deep cold is almost silent.
2. **Chirp:** heat has started winning. A single short chirp about every 10 seconds.
3. **Warning:** about a third of the time is left. A two-tone alarm that repeats faster as the cells near the quench point.
4. **Final seconds:** a rising whine for the last 20 to 30 seconds.
5. **Quench:** a loud bang, then a roaring steam hiss positioned at each vent, 5 to 10 seconds.
6. **Aftermath:** about 10 seconds of ticks, metal pings like a cooling pipe, and a fading hiss of boil-off. No creaks or voice-like sweeps (owner).
7. **All clear:** a short falling three-note tone when cooling wins again in time.

All cues are nonverbal. Final sounds are made through the repo's procedural pipeline with a `tools/audio_sources/<feature>/SOURCES.md` ledger and entries in `docs/audio/SHIPPED_AUDIO_INVENTORY.tsv`, like the Hearthstone's.

### Controller screen

Agent's call: right-clicking the Controller opens a screen showing stored and total energy, current flow in or out, port count and rate cap, cell temperature against the quench point, surroundings temperature, cooler draw, and, while warning, the estimated time to quench. When the structure is incomplete, the Controller says why (for example "needs 2 Quench Vents").

## Constraints

- NeoForge 1.21.1, Java 21, Gradle, launched through Gradle from the owning checkout (`AGENTS.md`).
- **Energy is `long` joules.** Today energy is `int` everywhere (`PowerStorageBlockEntity`, `PowerCellItem`, the network budget), which caps out near 2.1 GJ. A 7x7x7 is about 5 GJ. The Hyperion's store and every sum that includes it (network budget, monitor readings, load-follow) must not overflow. Battery Banks and Power Cells keep their item format.
- **The charge lives on the Controller's block entity** and saves with it. Cells are plain blocks with no inventory.
- **Integrate with the one-second accounting in `PowerNetworks.account`.** Today stored energy counts in full toward each interval's budget. The Hyperion contributes at most `min(stored, ports x 500 kW x 1 s)` to discharge and accepts at most the same charge per interval.
- **Bounded work.** No forced chunk loads and no unbounded scans in a tick. Structure validation is incremental or spread across ticks. Temperature sampling uses loaded-only queries.
- **Unloaded or partly loaded structures pause (agent's call):** no drain, no warming, no quench while any part is unloaded. A 7x7x7 can straddle chunk borders.
- **Save and reload** mid-warning, mid-cool-down or mid-quench resumes where it was, with no new quench from the reload itself.
- Menu sync: vanilla container data words are 16-bit, which is why the Battery Bank screen splits values into coarse and remainder words. GJ values need the same care or a custom payload.

## Verification

- GameTests for: structure validation (sizes 3 to 7, cuboids, required fittings, corners), capacity by cell count, `long` energy past 2.1 GJ, per-port rate caps both ways, load-follow pushing to fill and falling back when full, cooler draw against surroundings temperature, the warning timer and quench, recovery and all-clear, break-quench while charged, quiet dismantle when empty, an outage never quenching, warm-and-empty cool-down, pause when unloaded, and save/reload.
- Iterate with `./gradlew gameTestQuick -PfdTests=<fragments>`; run the full `architectVerify` before handing back.
- Owner visual and audio pass in a generated playtest world that follows `AGENTS.md` (advancement popups and chat awards suppressed, difficulty preselected, ordinary saves untouched). Suggested replay: boot an Icarus from a Hyperion, compare a battery outdoors in the cold with one in a heated room, and trigger both a heat quench and a break quench. Headless checks cannot establish how the cells, the puck or the audio look and sound.

## Left to the builder

- All numbers marked agent's call: cell capacity (about 20 MJ per cell suggested), port rate, quench point, self-discharge and cooler-capacity formulas meeting the targets above, and the warning curve meeting the stated times.
- How heat sources near the structure are detected and weighted.
- Cool-down time for a warm battery (suggested: under a minute with grid power, so a fresh build is usable almost at once).
- Whether a Hyperion or Battery Banks on the same grid charge or discharge first.
- How the structure is formed and re-validated (controller-driven scan or per-block notification), within the bounded-work constraint.
- Exact particles, light levels, block models and textures, following the look above and the repo's art process.

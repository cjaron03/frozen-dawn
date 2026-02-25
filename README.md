# Frozen Dawn

A Minecraft mod where Earth becomes a rogue planet. The sun recedes, the world freezes, and survival shifts underground.

**NeoForge 1.21.1** | **Java 21**

## Lore

Frozen Dawn takes place after humanity's final attempt to stabilize Earth's climate subtly altered its orbit, triggering a slow, irreversible planetary freeze. The Orbital Resonance Stabilization Authority (ORSA) tried to correct course, but their network of 2,048 orbital adjustment nodes entered a feedback loop — each correction pushing the planet further from the sun. By the time they realized, the orbital momentum was irreversible.

ORSA lore books scattered in world structures tell the full story across 5 documents, from initial optimism to final automated distress signals.

## Overview

Over 100 in-game days, the world progresses through 6 phases of an apocalyptic freeze. Surface temperatures plummet to -273°C, water turns to blue ice, vegetation dies, lava solidifies, the sky grows dark, and ultimately the atmosphere itself freezes and collapses. Players must adapt — building Thermal Heaters, crafting insulated shelters, and ultimately constructing a Geothermal Core deep underground as their last hope for survival.

## Phases

| Phase | Days | What Happens |
|-------|------|-------------|
| **1 — Twilight** | 0–10 | Sun begins dimming, sky shifts to warm amber, rain increases |
| **2 — Cooling** | 10–22 | Water freezes, grass dies, weather locks to rain, sky desaturates |
| **3 — The Long Night** | 22–34 | Permanent storms, sand freezes, trees die, fog rolls in, cold blue sky |
| **4 — Deep Freeze** | 34–46 | Dirt and logs freeze, lava solidifies, coal ore freezes, surface is lethal |
| **5 — Eternal Winter** | 46–60 | Near-total darkness, obsidian freezes, blizzard whiteout, wind chill exhaustion |
| **6 — Atmospheric Collapse** | 60–100 | Atmosphere freezes and collapses. Stars appear on a black sky. No air to breathe. |

## Features

### Environment
- **Block transformation chains** — Grass → Dead Grass → Dirt → Frozen Dirt, Water → Ice → Packed Ice → Blue Ice, Lava → Magma → Obsidian → Frozen Obsidian, Coal Ore → Frozen Coal Ore
- **Temperature system** — Calculated from phase progression, depth (geothermal warmth), shelter, and nearby heat sources
- **Mob & player freezing** — Slowness and damage scale with cold severity; seek shelter or heat to survive
- **Atmospheric suffocation** — Phase 6 late: air supply drains outside habitable zones, custom damage type and death message
- **Wind chill exhaustion** — Phase 5+: outdoor exposure drains food (sprinting = heavy, moving = moderate, standing = light)
- **Weather control** — Rain locks in phase 2, permanent thunderstorms from phase 3
- **Snow accumulation** — Layers build up on exposed surfaces, accelerating in later phases
- **Sound muffling** — Sounds dampen below -15°C; phase 6 late: vacuum cancels all sound on surface. Wind muffles smoothly indoors with shelter creaking sounds.
- **Shelter mechanics** — Roof overhead (within 4 blocks) suppresses snow particles, muffles wind, and triggers structural creaking. In phase 5+, exposed heaters (not enclosed on all 6 faces) have halved radius.
- **False calm rebounds** — Brief temperature spikes near phase boundaries create moments of false hope

### Visual Effects
- **Sky color shifting** — Phase-dependent sky hue from warm amber (phase 1) through cold blue to pure black (phase 6)
- **Fog closure** — Visibility drops from 256 to 12 blocks in phase 5, lifts in phase 6 as atmosphere thins
- **Star rendering** — Phase 6 mid+: atmosphere collapses, revealing stars on a black sky with brightness scaling
- **Frost screen overlay** — Blue-white vignette intensifying with cold
- **Wind variation** — Snowflake particles with oscillating wind patterns, hard-capped at 8/tick
- **Brightness floors** — Prevents phase 5 from being pure black; moonlight fades gradually
- **Breath particles** — Visible below 0°C, stop in phase 6 mid+ (no atmosphere to exhale into)
- **Camera shivering** — Intensifies with cold, extreme in phase 6

### Armor System
Three tiers of insulated armor allow players to survive progressively colder surface conditions. Armor suppresses freeze effects (shivering, frost overlay, slowness, damage) when its protection is sufficient, while breath particles remain visible as a cosmetic cold indicator.

| Tier | Name | Cold Resistance | Rated For | Special |
|------|------|----------------|-----------|---------|
| **1** | Insulated Clothing | +25°C (+6.25/piece) | Phase 1–3 | Wool + leather craft |
| **2** | Heavy Insulation | +45°C (+11.25/piece) | Phase 1–4 | Tier 1 + blaze powder, reduces wind chill 50% |
| **3** | EVA Suit | +120°C (+30/piece) | Phase 1–6 | Tier 2 + Frozen Heart, negates wind chill, blocks suffocation (up to 95% phase 6) |

Armor pieces can be mixed across tiers. Full set bonuses (wind chill, suffocation) require 4 pieces of that tier or higher.

### Nether Severance
At phase 5+, dimensional links are severed. Existing nether portals break and new ones cannot be lit. Attempting to light a portal with flint & steel grants a hidden advancement: *"Huh. Thought That Works."*

### Player Agency
- **Thermal Heater** — Right-click with coal, charcoal, blaze powder, or coal blocks to fuel. Radius 7, +35°C when lit. No GUI — just right-click fuel in. Fuel does NOT burn while chunk is unloaded. Higher tiers produce more heat but burn fuel faster (1.5x/2x/3x). Phase 4+ multiplies fuel consumption further (2x/4x/8x). Geothermal Core is exempt.
- **Insulated Glass** — Transparent block that counts as shelter (roof check). Build glass greenhouses that protect from the cold.
- **Thermal Core** — Crafting component (iron + blaze powder + magma cream) used in heaters and the endgame core.
- **Frozen Coal Ore** — Coal ore freezes in phase 4+ (configurable), Y≥0 only. Drops 1 coal (no fortune), 50% chance ice shard.

### Phase 6: Atmospheric Collapse
Phase 6 is divided into three sub-stages:
- **Early (progress ≤ 0.72):** Maximum blizzard, whiteout fog, extreme wind chill
- **Mid (0.72–0.85):** Wind dies, fog lifts, sky transitions to black, stars fade in
- **Late (0.85+):** Vacuum — all sounds cancelled on surface, atmospheric suffocation drains air supply, custom death message ("%1$s suffocated in the void")

**Sound in vacuum:** Surface (Y ≥ 0) is complete silence. Sound gradually returns underground: 0% at Y=0, 100% at Y=-32. Deep underground has full sound — rock insulates from the vacuum.

**Geothermal ambience:** Below Y=0 in phase 6, a deep rumbling loop plays (vanilla basalt deltas sound), with volume scaling from 0.3 at Y=0 to 0.7 at Y=-64.

**Habitable zones:** Players near a Geothermal Core below Y=0 have their air supply restored. Everyone else suffocates.

### Endgame: Geothermal Core
The Geothermal Core is the endgame objective — a massive heat source requiring resources from every phase:
- **Below Y=0:** radius 12, +50°C (intended endgame anchor — meaningful warm bubble)
- **Above Y=0:** radius 6, +15°C (survival aid, not salvation — doesn't trivialize the apocalypse)
- **Craft chain:** Thermal Core (phase 2-3 resources) + Frozen Heart (diamond + blue ice + ice shards + frozen obsidian) → Geothermal Core (+ diamond blocks + obsidian)
- **Design philosophy:** Above Y=0 is a survival aid. Below Y=0 is the intended endgame anchor. Modpack authors should tune `heatSourceMultiplier` to adjust.

### ORSA Narrative
5 lore books found in world structures tell the story of ORSA and humanity's failed attempt to stabilize Earth's orbit:
1. **Village houses (20%)** — ORSA Outreach Pamphlet (optimism)
2. **Desert temples (18%)** — ORSA Internal Memo (denial + false hope)
3. **Mineshafts (15%)** — ORSA Field Report (escalation)
4. **Stronghold corridors (12%)** — ORSA Emergency Log (panic + irreversibility)
5. **Stronghold libraries (10%)** — ORSA Final Transmission (resignation + survival directive)

All books are discoverable without beating the game. No End Cities or Ancient Cities required.

### Other
- **12 custom blocks** — Dead Grass, Frozen Dirt, Frozen Sand, Dead/Frozen Logs, Dead/Frozen Leaves, Frozen Obsidian, Thermal Heater, Insulated Glass, Frozen Coal Ore, Geothermal Core
- **16 custom items** — Ice Shard, Thermal Core, Frozen Heart, Thermal Container, 12 armor pieces (4 per tier)
- **12 advancements** — Phase progression (6 phases), "Last Light" (Geothermal Core below Y=0), "Classified Information" (ORSA book), 3 armor tier milestones, "Huh. Thought That Works" (hidden — try to light a portal at phase 5+)
- **Patchouli guide book** — "Frozen Dawn Field Guide" given on first join. Covers survival basics, hypothermia, ORSA equipment (with crafting recipes), and lore — written in ORSA's dry, deadpan corporate tone
- **Tough As Nails integration** — Optional; syncs apocalypse temperatures with TaN's body temperature system
- **Config presets** — `/frozendawn preset default|cinematic|brutal` for quick difficulty tuning
- **Fully configurable** — 17+ config options covering temperature, features, visuals, and gameplay

## Installation

1. Install [NeoForge for Minecraft 1.21.1](https://neoforged.net/)
2. Download `frozendawn-0.9.1.jar` from [Releases](https://github.com/cjaron03/frozen-dawn/releases)
3. Drop the jar into your `.minecraft/mods/` folder
4. Launch Minecraft with the NeoForge profile

## Commands

All commands require OP level 2.

| Command | Description |
|---------|-------------|
| `/frozendawn status` | Show current day, phase, temperature, and sun state |
| `/frozendawn setday <n>` | Jump to a specific apocalypse day |
| `/frozendawn setphase <1-6> [early\|mid\|late]` | Jump to the start of a phase (sub-stages for phase 6) |
| `/frozendawn pause` | Toggle apocalypse progression |
| `/frozendawn reset` | Reset to day 0 |
| `/frozendawn preset <name>` | Apply a config preset (default, cinematic, brutal) |

## Config Presets

| Preset | Total Days | Phase 5 Temp | Geothermal | Heat Sources | Snow Rate |
|--------|-----------|-------------|------------|-------------|-----------|
| **Default** | 100 | -120°C | 1.0x | 1.0x | 1.0x |
| **Cinematic** | 200 | -80°C | 1.5x | 1.5x | 0.5x |
| **Brutal** | 50 | -160°C | 0.5x | 0.5x | 2.0x |

Presets stomp the following fields unconditionally: `totalDays`, `basePhase5Temp`, `geothermalStrength`, `heatSourceMultiplier`, `snowAccumulationRate`.

## Configuration

Edit `config/frozendawn-common.toml` after first launch.

| Setting | Default | Description |
|---------|---------|-------------|
| `totalDays` | 100 | Days until phase 5 completes (preset-managed) |
| `startingDay` | 0 | Skip ahead for testing |
| `pauseProgression` | false | Freeze at current phase |
| `basePhase5Temp` | -120 | Coldest surface temp in °C (preset-managed) |
| `geothermalStrength` | 1.0 | Depth warmth multiplier (preset-managed) |
| `heatSourceMultiplier` | 1.0 | Heat source warmth multiplier (preset-managed) |
| `enableVegetationDecay` | true | Toggle vegetation death |
| `enableMobFreezing` | true | Toggle mob/player freeze effects |
| `enableLavaFreezing` | true | Toggle lava solidification |
| `snowAccumulationRate` | 1.0 | Snow buildup speed (preset-managed) |
| `enableFuelPhaseScaling` | true | Toggle phase-based fuel consumption scaling (P4: 2x, P5: 4x, P6: 8x) |
| `enableFuelScarcity` | true | Toggle coal ore freezing |
| `fuelScarcityPhase` | 4 | Phase when coal ore starts freezing (2-5) |
| `enableLoreBooks` | true | Toggle ORSA book injection into loot |
| `enableSunShrinking` | true | Toggle sun visual shrink |
| `enableSkyDarkening` | true | Toggle progressive sky/fog darkening |
| `enableSkyColorShift` | true | Toggle phase-dependent sky hue shifting |
| `enableFrostOverlay` | true | Toggle frost screen vignette |

## Performance

- **Tick budget:** Block freezing, vegetation decay, and snow accumulation use random sampling (24 surface + 12 volume checks per player per tick) within a 64-block radius. This keeps per-tick cost constant regardless of world age.
- **Temperature scanning:** Player temperature scans a 25-block diameter cube (radius 12 for Geothermal Core detection). Mob temperature uses a reduced 7-block diameter (radius 3) with early exit on first heat source found.
- **Chunk-load safety:** Block entities (Thermal Heater) stop ticking when their chunk unloads. Fuel does not burn while unloaded.
- **Particle cap:** Weather particles are hard-capped at 8/tick regardless of phase, and respect Minecraft's particle quality setting.
- **Network:** Apocalypse state syncs to clients every 100 ticks (~5 seconds), not every tick.

## Compatibility

- **Minecraft**: 1.21.1
- **NeoForge**: 21.1.219+
- **Tough As Nails**: Optional (10.0.0+) — integrates apocalypse temperature with TaN's body temperature system
- **Dimensions**: All world systems (freezing, decay, snow, weather) operate in the overworld only. Other dimensions are unaffected.
- **Servers**: Fully server-authoritative. All temperature calculations, block transformations, and loot modifications run server-side. Client only handles visual effects (sky, particles, overlay).

## Building from Source

```bash
git clone https://github.com/cjaron03/frozen-dawn.git
cd frozen-dawn
./gradlew build
```

The built jar will be at `build/libs/frozendawn-0.9.1.jar`.

For development testing: `./gradlew runClient` launches a Minecraft instance with the mod loaded and source-level debugging.

## License

All Rights Reserved

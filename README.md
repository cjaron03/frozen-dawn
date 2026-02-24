# Frozen Dawn

A Minecraft mod where Earth becomes a rogue planet. The sun recedes, the world freezes, and survival shifts underground.

**NeoForge 1.21.1** | **Java 21**

## Lore

Frozen Dawn takes place after humanity's final attempt to stabilize Earth's climate subtly altered its orbit, triggering a slow, irreversible planetary freeze. The Orbital Resonance Stabilization Authority (ORSA) tried to correct course, but their network of 2,048 orbital adjustment nodes entered a feedback loop — each correction pushing the planet further from the sun. By the time they realized, the orbital momentum was irreversible.

ORSA lore books scattered in world structures tell the full story across 5 documents, from initial optimism to final automated distress signals.

## Overview

Over 100 in-game days, the world progresses through 5 phases of an apocalyptic freeze. Surface temperatures plummet to -120°C, water turns to blue ice, vegetation dies, lava solidifies, and the sky grows dark. Players must adapt — building Thermal Heaters, crafting insulated shelters, and ultimately constructing a Geothermal Core deep underground as their last hope for survival.

## Phases

| Phase | Days | What Happens |
|-------|------|-------------|
| **1 — Twilight** | 0–15 | Sun begins dimming, sky shifts to warm amber, rain increases |
| **2 — Cooling** | 15–35 | Water freezes, grass dies, weather locks to rain, sky desaturates |
| **3 — The Long Night** | 35–55 | Permanent storms, sand freezes, trees die, fog rolls in, cold blue sky |
| **4 — Deep Freeze** | 55–75 | Dirt and logs freeze, lava solidifies, coal ore freezes, surface is lethal |
| **5 — Eternal Winter** | 75–100 | Near-total darkness, obsidian freezes, only deep underground is warm |

## Features

### Environment
- **Block transformation chains** — Grass → Dead Grass → Dirt → Frozen Dirt, Water → Ice → Packed Ice → Blue Ice, Lava → Magma → Obsidian → Frozen Obsidian, Coal Ore → Frozen Coal Ore
- **Temperature system** — Calculated from phase progression, depth (geothermal warmth), shelter, and nearby heat sources
- **Mob & player freezing** — Slowness and damage scale with cold severity; seek shelter or heat to survive
- **Weather control** — Rain locks in phase 2, permanent thunderstorms from phase 3
- **Snow accumulation** — Layers build up on exposed surfaces, accelerating in later phases
- **False calm rebounds** — Brief temperature spikes near phase boundaries create moments of false hope

### Visual Effects
- **Sky color shifting** — Phase-dependent sky hue from warm amber (phase 1) through cold blue to near-black purple (phase 5)
- **Fog closure** — Visibility drops from 256 to 48 blocks in phases 3+
- **Frost screen overlay** — Blue-white vignette intensifying with cold
- **Wind variation** — Snowflake particles with oscillating wind patterns, hard-capped at 8/tick
- **Brightness floors** — Prevents phase 5 from being pure black; moonlight fades gradually

### Player Agency
- **Thermal Heater** — Right-click with coal, charcoal, blaze powder, or coal blocks to fuel. Radius 7, +35°C when lit. No GUI — just right-click fuel in. Fuel does NOT burn while chunk is unloaded.
- **Insulated Glass** — Transparent block that counts as shelter (roof check). Build glass greenhouses that protect from the cold.
- **Thermal Core** — Crafting component (iron + blaze powder + magma cream) used in heaters and the endgame core.
- **Frozen Coal Ore** — Coal ore freezes in phase 4+ (configurable), Y≥0 only. Drops 1 coal (no fortune), 50% chance ice shard.

### Endgame: Geothermal Core
The Geothermal Core is the endgame objective — a massive heat source requiring resources from every phase:
- **Below Y=0:** radius 12, +50°C (intended endgame anchor — meaningful warm bubble)
- **Above Y=0:** radius 6, +15°C (survival aid, not salvation — doesn't trivialize the apocalypse)
- **Craft chain:** Thermal Core (phase 2-3 resources) + Frozen Heart (nether star + blue ice + ice shards + frozen obsidian) → Geothermal Core (+ diamond blocks + obsidian)
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
- **3 custom items** — Ice Shard, Thermal Core, Frozen Heart
- **7 advancements** — Phase progression + "Last Light" (Geothermal Core placed below Y=0) + "Classified Information" (found an ORSA book)
- **Tough As Nails integration** — Optional; syncs apocalypse temperatures with TaN's body temperature system
- **Config presets** — `/frozendawn preset default|cinematic|brutal` for quick difficulty tuning
- **Fully configurable** — 16+ config options covering temperature, features, visuals, and gameplay

## Installation

1. Install [NeoForge for Minecraft 1.21.1](https://neoforged.net/)
2. Download `frozendawn-0.1.0.jar` from [Releases](https://github.com/cjaron03/frozen-dawn/releases)
3. Drop the jar into your `.minecraft/mods/` folder
4. Launch Minecraft with the NeoForge profile

## Commands

All commands require OP level 2.

| Command | Description |
|---------|-------------|
| `/frozendawn status` | Show current day, phase, temperature, and sun state |
| `/frozendawn setday <n>` | Jump to a specific apocalypse day |
| `/frozendawn setphase <1-5>` | Jump to the start of a phase |
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

The built jar will be at `build/libs/frozendawn-0.1.0.jar`.

For development testing: `./gradlew runClient` launches a Minecraft instance with the mod loaded and source-level debugging.

## License

All Rights Reserved

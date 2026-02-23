# Frozen Dawn

A Minecraft mod where Earth becomes a rogue planet. The sun recedes, the world freezes, and survival shifts underground.

**NeoForge 1.21.1** | **Java 21**

## Overview

Over 100 in-game days, the world progresses through 5 phases of an apocalyptic freeze. Surface temperatures plummet to -120°C, water turns to blue ice, vegetation dies, lava solidifies, and the sky grows dark. Players must adapt — moving underground, building near heat sources, and scavenging a dying world.

## Phases

| Phase | Days | What Happens |
|-------|------|-------------|
| **1 — Twilight** | 0–15 | Sun begins dimming, rain increases |
| **2 — Cooling** | 15–35 | Water freezes, grass dies, weather locks to rain |
| **3 — The Long Night** | 35–55 | Permanent storms, sand freezes, trees die, fog rolls in |
| **4 — Deep Freeze** | 55–75 | Dirt and logs freeze solid, lava solidifies, surface is lethal |
| **5 — Eternal Winter** | 75–100 | Near-total darkness, obsidian freezes, only deep underground is warm |

## Features

- **Block transformation chains** — Grass → Dead Grass → Dirt → Frozen Dirt, Water → Ice → Packed Ice → Blue Ice, Lava → Magma → Obsidian → Frozen Obsidian, and more
- **Temperature system** — Calculated from phase progression, depth (geothermal warmth), shelter, and nearby heat sources (campfires, furnaces, lava)
- **Mob & player freezing** — Slowness and damage scale with cold severity; seek shelter or heat to survive
- **Weather control** — Rain locks in phase 2, permanent thunderstorms from phase 3
- **Snow accumulation** — Layers build up on exposed surfaces, accelerating in later phases
- **Visual effects** — Sky darkening, fog closure, frost screen overlay, ambient snowflake particles
- **8 custom blocks** — Dead Grass, Frozen Dirt, Frozen Sand, Dead/Frozen Logs, Dead/Frozen Leaves, Frozen Obsidian
- **Ice Shard item** — Dropped from frozen blocks, craftable into Packed Ice (2×2) or Blue Ice (3×3)
- **Advancements** — Track your progression through each phase
- **Admin commands** — `/frozendawn status|setday|setphase|pause|reset`
- **Tough As Nails integration** — Optional; syncs apocalypse temperatures with TaN's body temperature system
- **Fully configurable** — Total days, temperature scaling, geothermal strength, heat source multipliers, toggle any feature

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

## Configuration

Edit `config/frozendawn-common.toml` after first launch.

| Setting | Default | Description |
|---------|---------|-------------|
| `totalDays` | 100 | Days until phase 5 completes |
| `startingDay` | 0 | Skip ahead for testing |
| `pauseProgression` | false | Freeze at current phase |
| `basePhase5Temp` | -120 | Coldest surface temp (°C) |
| `geothermalStrength` | 1.0 | Depth warmth multiplier |
| `heatSourceMultiplier` | 1.0 | Heat source warmth multiplier |
| `enableVegetationDecay` | true | Toggle vegetation death |
| `enableMobFreezing` | true | Toggle mob/player freeze effects |
| `enableLavaFreezing` | true | Toggle lava solidification |
| `snowAccumulationRate` | 1.0 | Snow buildup speed multiplier |
| `enableSunShrinking` | true | Toggle sun visual shrink |
| `enableSkyDarkening` | true | Toggle progressive sky darkening |
| `enableFrostOverlay` | true | Toggle frost screen vignette |

## Building from Source

```bash
git clone https://github.com/cjaron03/frozen-dawn.git
cd frozen-dawn
./gradlew build
```

The built jar will be at `build/libs/frozendawn-0.1.0.jar`.

For development testing: `./gradlew runClient` launches a Minecraft instance with the mod loaded and source-level debugging.

## Compatibility

- **Minecraft**: 1.21.1
- **NeoForge**: 21.1.219+
- **Tough As Nails**: Optional (10.0.0+) — integrates apocalypse temperature with TaN's body temperature system

## License

All Rights Reserved

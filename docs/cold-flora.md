# Cold Flora: Frostcap and Rimewood

Sources:
- Notion [Cold Flora: Frostcap and Rimewood](https://app.notion.com/p/3fa7cfaa890181e78af2cd1dc13e433c) (decided 2026-10-14).
- The Rimewood section of [Blind Playtest, October 7](https://app.notion.com/p/3e67cfaa89018162a39fd1aecfd63d73).
- [Nether Cutoff Substitutes](https://app.notion.com/p/3f67cfaa8901816ebefcf3914b5d5a87).

All three were fetched on 2026-10-18. Branch `feat/cold-flora`, cut from `feat/maeve-director` at `20ac3635`.

## Owner decisions, 2026-10-18

- **Both Nether substitutes are built**, the Sulfur Igniter and the Thermal Binder, so Rimewood Resin has a use.
- **The full wood family except boats.** Boats need a new entity and renderer, so they are deferred.
- **New chunks only.** Groves generate in newly explored cold terrain; already-explored saves get no salvage route.
- **No refreeze.** Thawing is one-way.

## Rimewood

**Natural spawning.** Groves of 2-4 trees appear in snowy plains, snowy taiga, ice spikes, groves and snowy slopes, in about one chunk in 32 of those biomes (`#frozendawn:has_rimewood_grove`). There are never forests.

**The tree.** A squat conifer with a 5-7 block trunk and two or three short branches, some bent upward. It has about 10 logs on average (7 at least) and a narrow, uneven canopy of needles. The leaves get proper distances, so they hold while attached and decay when cut off.

**Growth.** Plant on dirt or frozen dirt with open sky. It grows only at -20C or colder; sunlight is not needed.
- **Random ticks:** each tick advances the sapling with a 1 in 11 chance. At the default tick speed that is about 25 minutes over two stages.
- **Bone meal:** keeps vanilla's 45% chance per use (about 4.4 uses) and works only in the cold.
- **Warmth:** pauses growth without harming the sapling.
- **Saplings from leaves:** drop chances average about two saplings per tree, and Fortune raises them.

**Cold-hardy.** Living Rimewood and Frostcap carry `#frozendawn:cold_flora`. Vegetation decay (surface, volume and collapse flood fills) and epoch catch-up (vegetation, tree remnants and tree clearing) skip them. Rimewood planks are exempt from the block freezer.

**Wood family.** Logs, wood, stripped variants, planks, stairs, slab, fence, gate, door, trapdoor, button, pressure plate, sign, hanging sign, needles, sapling and a potted sapling.
- They join the vanilla wood tags, so sticks, tables, chests, tools, charcoal and fuel all accept them.
- They burn like other wood.
- Signs use the vanilla sign block entities.

**Resin.** Stripping a log or wood with an axe pops one Rimewood Resin; a stripped log gives no more.

## Frostcap

**Block.**
- A cluster grows on snow blocks, full snow layers or ice. A shelf grows on the side of a Rimewood log and falls with it.
- Three ages. A ripe cluster drops 2-3 Frozen Frostcap; a younger one drops 1.
- Light 3, no collision, no heat.

**Growth, per random tick:**
- **Above 0C:** it dies.
- **-20C to 0C:** it stalls.
- **Colder:** it ripens with chance 1/6 times the growth multiplier (1x at -20C, rising to 2x at -80C). Once ripe it spreads with chance 1/25 times the same multiplier, to a spot within 2 blocks.
- **Spread cap:** it stops spreading once five Frostcap stand in the surrounding 9x3x9 box.

**Wild patches.** From Phase 3, every 10 s each overworld player has a 1 in 30 chance (about once in five minutes) of seeding a patch.
- The patch is 3-6 caps on cold snow or ice, 24-64 blocks away.
- It only appears if no Frostcap grows within 16 blocks.

Wild Frostcap is not placed at world generation: surface temperature is still around 0C there, so the caps would die before Phase 3.

**Thaw chain.** In a player inventory above 0C, each stack counts warm seconds:
- After 300 s, Frozen Frostcap becomes Thawed; after another 300 s, Thawed becomes Mush.
- Counts are kept, and the tooltip shows the minutes left.
- Chests are not ticked, so stored caps keep as they are. This is wider than the spec's "cold-room chests", which still hold.
- Frostcap items are frost-resistant to the general food frost, which would otherwise double-count them.

| Item | Hunger | Notes |
| --- | --- | --- |
| Frozen Frostcap | 2 | Raw, safe; plants |
| Thawed Frostcap | 1 | Spoiling; does not plant |
| Frostcap Mush | 0 | Nausea 15 s; always edible |
| Frostcap Stew (3 caps + bowl) | 6 | Does not spoil; ordinary food frost applies |
| Dried Frostcap (furnace, smoker, campfire) | 3 | Stackable; frost-resistant |

**Hooks.** Frostcap is not a heat source, so a cold farm adds nothing to heat signatures (the Pyro and Maeve's heat checks). The hidden advancement "Fungus Among Us" is for eating Frostcap Mush.

## Nether substitutes

- **Sulfur Igniter:** 4 Sulfur + 1 coal. `#frozendawn:igniter` holds blaze powder or the igniter.
- **Thermal Binder:** 2 Sulfur + 2 Rimewood Resin. `#frozendawn:thermal_binder` holds magma cream or the binder.

The Thermal Core, Rocket Engine, the four reinforced armor pieces and Hot Wings now use those tags. Vanilla uses of blaze powder stay Nether-only.

## Handbook

Getting Started gains **Rimewood** and **Frostcap**. The Frostcap entry carries the ORSA note on *Cryomyces*. Advanced gains **Without the Nether**.

## Verification

- **Unit:** `ColdFloraPolicyTest` (4 cases):
  - the growth multiplier and its bounds;
  - sapling sky and cold rules;
  - sapling timing against the 20-30 minute and 4-6 bone-meal targets;
  - the one-way thaw.
- **GameTests (required, batch `cold_flora`):**
  - tree shape, logs, trunk and attached needles;
  - a sapling growing the configured tree on frozen dirt;
  - Frostcap stalling, ripening, spreading under the cap, and dying;
  - shelves on Rimewood that fall with the log;
  - one resin per log;
  - the thaw chain;
  - tags, substitute recipes and sign block entities.
- **Not covered automatically:**
  - Natural grove placement.
  - Temperature-driven random ticks: GameTest areas sit deep enough that geothermal heat keeps them warm, so tests pass the temperature explicitly.
  - Wild patch seeding.
- **Human visual pass pending:** tree and needle look, Frostcap glow, models, signs, door and handbook pages.

## Open items

- Boats and chest boats (deferred).
- No salvage route for saves explored before this build.
- Grove rarity (1 in 32 chunks), Frostcap rates and the wild-seeding rate are first-pass values for playtesting.

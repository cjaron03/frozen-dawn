# The Long Silence: Technical and Spoiler Changelog

> [!WARNING]
> This document contains direct late-game spoilers, including encounter names, outcomes, hidden advancements, mechanical counters, and operator commands. Read the public [release notes](THE_LONG_SILENCE.md) instead for a spoiler-safe overview.

## Hearth and Heart Arc

- Adds persistent Major and Minor Hearth selection, maturation, construction, resident populations, conduct memory, watchers, transmissions, survey discovery, physical boundaries, and reconciliation across unloaded chunks.
- Expands the Master Architect into a multi-stage encounter with weather authority, construction, tethers, the Fold, sky presentation, combat music, and a staged storm-death aftermath.
- Adds the Thae Iven Heart formation, Cognitive Load, memory nodes, Echo counterplay, scavenger pressure, the Successor, Maeve, and the permanent post-erasure world transition.
- Preserves field-strength consequences from resident survival and records pre-roster casualties without duplicating or resurrecting residents.

## Post-Maeve World

- Adds the irreversible world-scoped `maeveErased` authority and synchronized post-Maeve state.
- Permanently changes Hearth conduct, storm authority, transmissions, Cognitive Load, ambient weather, and resident coordination.
- Adds the damaged Moon timeline, orbital debris, and eventual ring band. The Moon's orbital decay is not presented as an effect caused by Maeve.
- Adds loaded-chunk-only Bloom growth, a major origin root, density bands, inert Acheronite, Spent and Sealed Lattice, finite Spore relays, Bloom ambience, and post-game ecology weighting.
- Adds Hearthrot as a permanent but manageable condition produced by the interaction of exterior suit colonization and a later pressure breach.

## Evolved and Post-Game Creatures

- **The Undone** and **Undone Architect** persist outside the old coordination system, including grasp, construction, accretion, and post-Maeve aggression rules.
- **The Archivist** creates shared collection sites and preserves complete item stacks without entering ordinary combat.
- **Rimebound** branches from Frostbitten encounters with burial, burrowing, lances, armor, terrain pressure, and full player encasement.
- **Resonant** branches from Hollow encounters and hunts vibration through server-authoritative sound events, phasing, breaches, and grabs.
- **Remnant** creates authored false shelters, copies the committed player's appearance and equipment, learns player attack patterns, and collapses its lure on death.
- **Frostwrithe** forms from Frostmite colonies, travels as a coordinated burrowing superorganism, mimics nearby evolved threats, and can break apart into ordinary Frostmites before regrouping.
- **Bloombound** and **The Spore** extend the Bloom's local ecology without replacing its terrain authority.

Evolution chances scale with monotonic post-Maeve age, local Bloom density, the selected difficulty preset, nearby caps, and the encounter director's pity/variety rules. Use `/fd postmaeve encounters` for the exact live probability, eligibility, cap, cooldown, and guarantee state in the current save rather than relying on a static table.

## Optional Consequence Encounter

- **The Aggregate** accumulates world-scoped pressure from eligible player-attributed Frozen Dawn kills, grows a Deposit and Ossuary, locks dominant lineages, and awakens once per world.
- Its phase reallocations inherit Rimebound, Resonant, Remnant, Frostwrithe, Architect, or Undone consequences according to accumulated lineage pressure.
- The encounter includes convergence windows, reinforcement discharge, permanent scar ownership, staged collapse, and an idempotent reward path.
- The **Stillpoint Core** creates a charged 48-block sanctuary that suppresses eligible post-Maeve ecology and Bloom growth, distorts the boundary, and muffles exterior sound. It has three Acheronite-pickaxe relocations before terminal exhaustion.

## Survival and Equipment

- Adds persistent EVA punctures, leak audio, repair kits, emergency oxygen cartridges, O2 efficiency support, trauma recovery, and module-aware tank telemetry.
- Effective base breathing capacities are 120, 240, and 360 seconds for the three supported tank tiers before applicable equipment modifiers and active leaks.
- Separates vanilla drowning bubbles from Frozen Dawn oxygen telemetry.
- Adds the reusable Thaeven Translator, The Last Witness's three finite memory charges, and post-game lattice processing.

## Thaeven Memory Records

- Adds six server-authoritative records with shared physical carriers and per-player discovery:
  - `Vel-an`
  - `The Heart Beneath`
  - `Pattern Residue`
  - `The Passage`
  - `The First Crossing`
  - `The Unthreading`
- Permanent Master defeat revises previously unresolved language without using temporary Fold deaths or failed attempts.
- Translation animates raw Thaeven into reconstructed meaning, while reduced-animation mode resolves presentation immediately.

## Hidden Advancements

The expansion includes hidden advancement moments such as `Decoherence`, `The Watched Stopped Watching...`, `...The Watching Never Stopped`, `No One Else Remembers Now`, `You Are Remembered.`, `It Kept Going`, `Selection Pressure`, `Convergence`, `Nothing Left to Become`, `The World, Held Back`, and `Now all of China knows you're here!`.

## Operator Commands

`/frozendawn` is the public read-only surface. `/fd` is the permission-level-2 shortcut for `/frozendawn debug`.

### Public diagnostics

```text
/frozendawn status [verbose]
/frozendawn locate <all|orsa|towns|vents>
/frozendawn win <status|satellite>
/frozendawn help <world|hearth|heart|postmaeve|aggregate|suit|lore>
```

### Operator categories

```text
/fd world ...
/fd hearth ...
/fd heart ...
/fd postmaeve ...
/fd aggregate ...
/fd suit ...
/fd lore ...
```

Representative status routes:

```text
/fd world status [verbose]
/fd hearth status [verbose]
/fd hearth list [verbose]
/fd heart status [verbose]
/fd postmaeve status [verbose]
/fd postmaeve bloom status
/fd postmaeve encounters
/fd aggregate status [verbose]
/fd aggregate stillpoint status [verbose]
/fd suit status [verbose]
/fd suit hearthrot status [verbose]
/fd lore status [verbose]
```

Use tab completion and `/frozendawn help <category>` for mutation routes. Persisted reset, purge, resolve, completion, and world-semantic reset actions require a final `confirm` literal. Debug output defaults to concise player-facing summaries; `verbose` exposes UUIDs, seeds, timestamps, bindings, cursors, and policy counters.

## Persistence and Performance

- Adds versioned SavedData for Hearths, the Heart, post-Maeve authority, Bloom, encounters, lore, the Archivist, Remnant lures, and the Aggregate.
- No late-game system force-loads distant chunks. Formation and migration reconcile when relevant chunks load.
- Bloom terrain work is capped at 96 edits or 1.5 ms per tick and defers during chunk catch-up bursts.
- Frozen Atmosphere catch-up, Architect pathfinding, entity caps, pity timers, rendering buffers, and audio ownership received release-polish passes.

## Runtime Dependencies

- Minecraft 1.21.1
- NeoForge 21.1.219+
- Java 21
- Patchouli 1.21.1-90+
- Curios API 9.x
- JEI 19.x client runtime
- GeckoLib 4.8.4 through compatible 4.x releases

Frozen Dawn does not bundle the dependency jars. Install the required dependencies separately on the appropriate client and server.

# Room heating and automatic cooling

The original game capacities are restored:1 per air cell and12 per tracked physical wall block. The temporary version3 values (16.6667 and250) made cold starts far too slow. Machine waste heat still enters the room at its full existing budget. Cooling controls handle excess heat rather than storing it in artificially heavy walls.

A thermostat in the same sealed room controls heaters and heat vents together. Vents open above target+0.5C, remain enabled through the hysteresis band, and close at target. Every integration substep shares one target-limited removal budget across all vents, preventing oversized automatic cooling from pulling temperature below target. Ordinary heat leakage can still make an unheated room cold. The newest thermostat wins, as for heating; removal, splits, merges and unknown/unloaded geometry retain the existing controller rules.

The thermostat takes priority over manual/redstone vent settings. Without a room thermostat, the saved manual shutter setting and redstone control work as before. Right-click on a controlled vent says Thermostat controlled. The shutter and status light follow actual control. Dim means closed, amber means open but blocked or waiting, green means heat can leave. The thermostat shows Cooling when its vents are active.

Each vent has conductance18, scaled by the existing0.35 vacuum factor. Conductances add directly: two vents provide36 before the factor. Each needs its own loaded uncovered outdoor face, facing away from the room. Blocks, roofs (including glass), reversed placement and neighboring rooms cannot serve as outlets. No oxygen is vented and no ducts are introduced.

Output depends on the temperature difference. At20C inside and-215C outside, a vacuum vent initially rejects about1.48k game heat units/s; near-273C it approaches1.85k. The room envelope also loses heat. A2kW full-PIXIE-plus-two-furnace load therefore needs late deep-vacuum conditions and adequate exterior loss for a single vent to hold near target. More insulation, warmer outdoors or more machines can require additional vents. These are game-calibrated linear conductances, not a physical spacecraft radiator simulation.

## Save compatibility

Thermal data is version4. Versions1/2 use the original air capacity; version3 used the increased air capacity. Loading scales gas energy by the corresponding capacity ratio and each material by its recorded capacity. Every gas/material temperature, room identity, membership, sealed/depleted state and historical fuel/machine ledger entry survives. A signed capacityAdjustment records the change in representation. Reload applies no second migration, offline simulation or temperature reset. Existing hot rooms stay hot and cool through actual losses.

## Verification and replay

Required native cases cover one-basic-heater cold start from-150C, retained-wall recovery after a quick real breach/reseal and authoritative refill, full PIXIE and combined furnace waste heat in a small insulated deep-vacuum room, additive vents, individual blocked faces, target protection, controller removal and reconstruction after reload. The pressure controller's refill delay is covered separately; the thermal recovery case directly invokes its authoritative refill transition. Migration checks both original and increased-capacity saves, hot/empty walls, depleted air, ledger balance and repeat reload. Unit coverage checks the cold-start energy budget and hysteresis.

The owner's complete70-file PIXIE-2 Check save and pre-change source are archived under build/power-network-evidence/pixie-2/thermostat-cooling. Preserve the existing machines and geometry. A live test needs an actual outdoor-facing vent and a same-room thermostat; underground cooling needs a suitable exposed location. Owner-led visual acceptance remains separate from the native calibration gate.

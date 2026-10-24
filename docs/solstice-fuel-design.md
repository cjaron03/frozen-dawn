# ORSA SFR-1 Solstice Reactor: fuel chain design

Status: design, not started. Drafted 2026-10-24 on `feat/solstice-reactor` at `19d391a6`, after the core loop slice (`docs/solstice-reactor-design.md`).

Source spec: Notion "ORSA SFR-1 Solstice Reactor (Fusion)", sections 4, 5a and 5b. Related: Notion "ORSA Fabricator". The owner asked for this slice scoped from Notion, with questions only where Notion leaves a gap.

## Intent

The core loop runs on creative fuel. This slice gives fusion fuel a real source that ties the tiers together:
- The ice age is the deuterium supply.
- Deep mining gives lithium.
- The CFU-7 breeds the first tritium. Fusion extends fission; it does not replace it.
- Helium ash, which the core loop only vents, comes out as canisters that speed up the next cooldown.

## Goals

- Spodumene in pegmatite pods deep in deepslate. Raw Spodumene smelts to Lithium Carbonate.
- Lithium from refining Lithium Carbonate at the Isotope Column, or salvaged from ORSA batteries.
- The Lithium Target Rod, crafted like a fuel rod, breeds Tritium Canisters in a CFU-7 rod slot.
- The Isotope Column: a 10 kW machine that makes Deuterium Canisters from ice or snow and refines Lithium Carbonate into Lithium.
- Helium Canisters from pumped ash. One loaded in the Controller halves the next cooldown.

## Non-goals (later slices)

- The Fabricator, Data Slates, Helion sites and Superconducting Coil Scrap. Survival recipes for the SFR-1 structure blocks and the Isotope Column come with them.
- Lithium Blanket damage and a breeding ratio below 1.0.
- Helium as quench gas or as an inert room gas.
- Neutron radiation, including from a CFU-7 holding a target rod.
- Advancements.

## Anti-goals

- No temporary survival recipes that a later slice would have to remove.
- No change to CFU-7 heat, damage or meltdown rules. The only CFU-7 change is that its rod slots also accept a Lithium Target Rod.
- No new way to get uranium or fuel rods.

## Availability (decided)

| Item | How to get it in this slice |
| --- | --- |
| Pegmatite, Deepslate Spodumene Ore, Raw Spodumene | World generation |
| Lithium Carbonate | Smelt or blast Raw Spodumene |
| Lithium | Isotope Column, or battery salvage (survival) |
| Lithium Target Rod | Crafting table (survival) |
| Tritium Canister | Bred in a CFU-7 (survival), or extracted from an SFR-1 tank as before |
| Deuterium Canister | Isotope Column |
| Helium Canister | Pumped from a burning SFR-1 |
| Isotope Column | Creative tab and `/give` only, until the Fabricator slice (owner call 2026-10-24) |

A survival player can mine, smelt, salvage and breed tritium. Deuterium and Column refining wait for the Fabricator, as the SFR-1 structure already does.

## Lithium

- **Pegmatite** is a coarse pale granite block that occurs only deep down.
  - It mines like deepslate and drops itself. It is a building block, with no other use.
- **Deepslate Spodumene Ore** occurs only inside pegmatite pods, never loose in deepslate.
  - It needs an iron pickaxe and drops Raw Spodumene, with Fortune, like galena.
  - Real spodumene is mined from pegmatite.
- **Pods:**
  - A custom feature places one ellipsoid pod of Pegmatite, 3 to 5 blocks in radius, replacing deepslate only.
  - About 20% of the pod's blocks are Spodumene Ore.
  - Pods appear in about 1 chunk in 3, between Y -64 and Y -8.
  - Spodumene is rarer than galena (8 veins of 8 per chunk), but each find is a pocket.
  - The builder calibrates these numbers.
- **Lithium Carbonate:** smelting or blasting Raw Spodumene, 0.7 experience. It is also the future Lithium Blanket ingredient.
- **Lithium:** the refined lithium item. It comes from:
  - the Isotope Column, 1 Lithium Carbonate to 1 Lithium;
  - salvage, by shapeless crafting: 1 Power Cell gives 1 Lithium, and 1 Battery Bank gives 4.
  - Salvage is the fast early path, but it destroys the batteries the 150 kW ignition pulse needs. Notion intends that tension.
- No brine and no salt flats, per Notion.

## Lithium Target Rod (owner call 2026-10-24: like the fuel rod)

- **Recipe:** the fuel rod shape with Lithium in place of the uranium pellets: `IPI / PCP / IPI`, where I is an iron ingot, P is Lithium and C is a copper ingot.
- **Item:** like `FuelRodItem`. Durability is `ReactorPhysics.ROD_LIFE_SECONDS`, never repaired or enchanted, and the tooltip shows the breeding progress percent.
- **In the CFU-7:**
  - It goes into any of the 4 rod slots, under the same lock rules as a fuel rod.
  - It makes no heat, so the reactor's rating drops by one rod, 35 kW. Notion: "costs 35 kW thermal while cooking."
  - It ages at the same rate as the fuel rods, so it cooks only while the core is producing heat.
- **When it is used up:**
  - It becomes 4 Tritium Canisters in the CFU-7 output slot, if that slot is empty or holds Tritium Canisters with room.
  - Otherwise it waits in its rod slot until there is room.
  - 4 units is Notion's yield: 1 for ignition plus about 3 in-game days of burn.
- **Meltdown:** a meltdown destroys target rods with everything else in the slots.

## Isotope Column

- **Block:** one directional block with a menu, a `PoweredMachine` drawing 10 kW only while working.
  - Like the Induction Furnace, 25% of the draw is waste heat into the room.
  - Power loss pauses a job; it does not reset it.
- **Slots:** one input and one output. As a `WorldlyContainer`, it is hopper-fed from the top and sides and emptied from the bottom.
- **Jobs:** chosen by what is in the input slot.

| Input | Output | Time |
| --- | --- | --- |
| Ice, Snow Block, Packed Ice, Blue Ice | 1 Deuterium Canister per 8 ice units | 120 s |
| Lithium Carbonate | 1 Lithium | 60 s |

- **Ice units:** Ice and Snow Block count 1, Packed Ice 9 and Blue Ice 81. The Column takes ice in from the input slot as units are needed.
- **Pace:** at full output an SFR-1 burns 1 Deuterium Canister per 10 minutes, so one Column keeps up with several reactors. The real limit is ice and power.
- **Refusal:** items that match no job are refused, by hoppers and in the menu.
- **Screen:**
  - the progress bar;
  - the ice-unit buffer;
  - power status: WORKING, NO POWER or IDLE.
- **Sound:** a quiet running hum. It is a new sound, rendered by the existing sound script.
- **Texture:** a tall glass column of ice-blue liquid in a steel frame, drawn by `tools/generate_sfr_textures.py`.

## Helium ash and Helium Canisters

- **Making canisters:**
  - While BURNING with the Vacuum Port pumping, each Deuterium Canister burned yields one Helium Canister. Each fusion makes one helium nucleus.
  - The canister goes into a new Helium slot in the SFR-1 Controller.
  - If that stack is full, the ash still vents, with no penalty.
- **Using them:**
  - When a cooldown starts with a Helium Canister in that slot, one is used and the cooldown runs twice as fast.
  - This stacks with the vacuum halving: a vacuum room with helium cools in 30 s instead of 120 s.
  - Cooldown power stays 20 kW, so the energy halves too.
- **Access:** the slot is filled and emptied by hand in the Controller menu, like the existing Tritium slots. The Controller has no hopper access today, and this slice does not add any.
- **Screen:** the Controller screen gains the Helium slot and shows when the next cooldown is boosted.
- **Texture:** the canister follows the Deuterium and Tritium Canister style, with a brown industrial-helium cap and band.

## Tests

- **Unit tests:**
  - Column job math: ice units, Packed and Blue Ice, Lithium Carbonate.
  - Pausing on power loss.
  - Helium yield per Deuterium Canister burned.
  - Cooldown halving, alone and stacked with vacuum.
- **GameTests (all named `sfr...`):**
  - A target rod in a running CFU-7 drops its rating by 35 kW and, once used up, leaves 4 Tritium Canisters in the output slot.
  - A target rod waits while the output holds spent fuel rods.
  - A powered Column turns ice into a Deuterium Canister. Unpowered, it pauses.
  - A hopper cannot feed the Column junk.
  - Battery salvage recipes give the stated Lithium.
  - A pegmatite pod places spodumene only inside pegmatite.
  - A burn that consumes deuterium fills the Helium slot, and a loaded canister halves the next cooldown.
  - Reloading keeps a part-cooked target rod, a part-done Column job and the ice buffer.
- **Human checks:** textures, the Column hum and finding a pod in a fresh world.

## Builder's calls already made in this document

- **Isotope Column creative-only** until the Fabricator. This follows the no-temporary-recipes anti-goal; the owner did not object.
- **Battery salvage by shapeless crafting:** 1 per Power Cell and 4 per Battery Bank. The owner did not object.
- **Spodumene placement:** spodumene only inside pegmatite pods placed by a custom feature, with the pod size, share and rarity above.
- **Ice units, Column times and helium yield:** as above, for the builder to calibrate.
- **Target rod output:** 4 canisters into the CFU-7 output slot, waiting when that slot is blocked.
- **One Helium slot** that both receives pumped ash and feeds the next cooldown.

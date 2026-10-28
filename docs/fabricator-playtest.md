# ORSA Fabricator visual check

Owning checkout: `/Volumes/SSD/Work/frozen-dawn-fabricator`, branch `feat/fabricator`, stacked on `feat/helion-sites`. Launch with Java 21 and `./gradlew runClient --console=plain`. No lab bridge function is needed. Use a new or throwaway world; the blueprint archive is saved per world.

## What changed

- **ORSA Fabricator.** A powered machine with six input slots and one output. It is crafted from iron, copper, a crafting table, an Induction Furnace and a redstone block. Link it to the grid with the Conduit Binder. A job draws the recipe's wattage (5-15 kW) and sheds a quarter of it as room heat. Losing power pauses the job; ingredients are spent only when it completes. Hoppers feed the inputs from the top and sides and pull the output from below.
- **Blueprints.** Each world has one shared archive. Right-click the Fabricator with a Data Slate to archive its blueprint; a new slate is used up, a known or unreadable one stays in hand. The rocket blueprint still comes from the Martian Command reply.
- **Printed parts.** Magnet Coils, the First Wall (also from four tiles), Lithium Blankets, the High-Temperature Radiator, the Isotope Column and the rocket's engine and hull are printed, not crafted. The SFR-1 controller and ports stay on the crafting table.
- **Slate sources.** The Helion log desk holds 2-3 distinct fusion slates. The CFU-7 shift locker holds the radiator slate. Half of Uplink resupply drops carry one slate the world has not learned.
- **Status line.** Idle, no power, working, no recipe, output full, or "blueprint not archived" when the inputs match a part the world has not learned.
- **JEI and advancements.** A Fabricator category shows each job's inputs, time, wattage and blueprint. Taking a printed part grants "Printed, Not Made"; archiving the last slate grants "Full Archive".

## Commands

```
/fd world helionsite
/fd world reactorsite
/fd world blueprint learn <id|all>
/fd world blueprint forget <id|all>
```

## Checks

1. Craft the Fabricator, place it and link it to a powered grid. Check the front face, the lit state while working, and that the GUI reads clearly.
2. Load Lithium Blanket inputs before archiving the blueprint. The status should say the blueprint is not archived and the machine should draw nothing.
3. Run `world helionsite`, climb down and take the slates from the log desk. Right-click the Fabricator with one; check the action-bar message and that the slate is used up. Try the same slate again; it should stay in hand.
4. Print a part. Check the progress arrow, the waste heat in a sealed room, and the "Printed, Not Made" toast when taking it.
5. Cut power mid-job; progress should hold. Restore it and let the job finish.
6. Open JEI on a Magnet Coil and on the Rocket Engine. Check that the Fabricator category shows counts, time, wattage and blueprint.
7. Optional: learn the remaining slates with `blueprint learn`, leaving one, then insert that slate and check the "Full Archive" toast.

Headless coverage: `FabricatorGameTest` (archive, rocket flag, slate insertion, locked status, wattage, hopper faces, save/load, reset, menu, drops, a powered job that pauses and finishes, Uplink slates, both advancements), `SfrRecipeGameTest` (table recipes, printed parts, coils need scrap, no core recipe), `HelionSiteGameTest` and the reactor-site test in `RadiationGameTest` (slates in site loot). The GUI, JEI layout, textures and toasts need a human pass.

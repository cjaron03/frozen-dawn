# ORSA Fabricator visual check

Owning checkout: `/Volumes/SSD/Work/frozen-dawn-fabricator`, branch `feat/fabricator`, stacked on `feat/helion-sites`. Launch with Java 21 and `./gradlew runClient --console=plain`. No lab bridge function is needed. Use a new or throwaway world; the blueprint archive is saved per world.

## What changed

- **ORSA Fabricator.** A powered machine with six input slots and one output. It is crafted from iron, copper, a crafting table, an Induction Furnace and a redstone block. Link it to the grid with the Conduit Binder. A job draws the recipe's wattage (5-15 kW) and sheds a quarter of it as room heat. Losing power pauses the job; ingredients are spent only when it completes. Hoppers feed the inputs from the top and sides and pull the output from below.
- **Blueprints.** Each world has one shared archive. Right-click the Fabricator with a Data Slate to archive its blueprint; a new slate is used up, a known or unreadable one stays in hand. The rocket blueprint still comes from the Martian Command reply.
- **Printed parts.** Magnet Coils, the First Wall (also from four tiles), Lithium Blankets, the High-Temperature Radiator, the Isotope Column and the rocket's engine and hull are printed, not crafted. The SFR-1 controller and ports stay on the crafting table.
- **Slate sources.** The Helion log desk holds 2-3 distinct fusion slates. The CFU-7 shift locker holds the radiator slate. Half of Uplink resupply drops carry one slate the world has not learned.
- **Status line.** Idle, no power, working, no recipe, output full, or "blueprint not archived" when the inputs match a part the world has not learned.
- **JEI and advancements.** A Fabricator category shows each job's inputs, time, wattage and blueprint. Taking a printed part grants "Printed, Not Made"; archiving the last slate grants "Full Archive".
- **SFR-1 easter eggs.** Showing the blueprint of an incomplete torus first puts "FUSION: 20 YEARS AWAY" in chat, above the parts list. While the plasma burns, the porthole on the Maintenance Hatch's upper half glows pink-violet with a slow pulse (a brighter core that swells and fades about every 1.2 s) (light level 7; dimmer, level 4, during ramp-down; dark once off). Looking through that porthole into a burning (or ramping-down) channel, from up to 24 blocks and through glass, gives faint white-blue retinal specks in the radiation eye-flash layer. The specks are visual only; no neutron dose is modelled yet.

## Commands

```
/fd world helionsite
/fd world reactorsite
/fd world sfrlab
/fd world blueprint learn <id|all>
/fd world blueprint forget <id|all>
```

## SFR-1 lab world

`tools/prepare_sfr_playtest.py --template <closed save>/level.dat` writes `run-lab/saves/SFR-1 Check`: a creative superflat at phase 0 with advancements hidden and difficulty locked on Normal. It refuses to overwrite an existing save. Open it with `./gradlew runClientLab -PfdLabWorld='SFR-1 Check' --console=plain`. On first join the player faces north and `/fd world sfrlab` builds a complete SFR-1 12 blocks ahead: fuelled, pumped down, radiators and charged banks linked, and off. The Maintenance Hatch faces the player; the Controller is on the far side. The chat menu gives the kit, day and night, and game modes. `/fd world sfrlab` also works in any world; it builds 12 blocks ahead of you.

## Checks

1. Craft the Fabricator, place it and link it to a powered grid. Check the front face, the lit state while working, and that the GUI reads clearly.
2. Load Lithium Blanket inputs before archiving the blueprint. The status should say the blueprint is not archived and the machine should draw nothing.
3. Run `world helionsite`, climb down and take the slates from the log desk. Right-click the Fabricator with one; check the action-bar message and that the slate is used up. Try the same slate again; it should stay in hand.
4. Print a part. Check the progress arrow, the waste heat in a sealed room, and the "Printed, Not Made" toast when taking it.
5. Cut power mid-job; progress should hold. Restore it and let the job finish.
6. Open JEI on a Magnet Coil and on the Rocket Engine. Check that the Fabricator category shows counts, time, wattage and blueprint.
7. Optional: learn the remaining slates with `blueprint learn`, leaving one, then insert that slate and check the "Full Archive" toast.
8. Place an SFR-1 Controller with no torus. Chat should read "FUSION: 20 YEARS AWAY" in gold as the ghost outline appears, followed by the parts list, with nothing covering it.
9. With a burning SFR-1, look straight at the hatch porthole from outside, then at the metal around it and at the lower half. Specks should appear only through the porthole and should read as faint next to radiation flashes.
10. With the same SFR-1, watch the hatch: the porthole should glow pink-violet and pulse slowly while burning, with no dark seams at the glass edges, dim during ramp-down after STOP, and go dark once off. Check it with the hatch facing each direction and from both sides, and that the glow lights the floor in front of the hatch.

Headless coverage: `FabricatorGameTest` (archive, rocket flag, slate insertion, locked status, wattage, hopper faces, save/load, reset, menu, drops, a powered job that pauses and finishes, Uplink slates, both advancements), `SfrRecipeGameTest` (table recipes, printed parts, coils need scrap, no core recipe), `SfrGameTest.onlyThePortholeShowsTheChannel` (porthole line of sight), `SfrGameTest.sfrHatchPortholeGlowsWithThePlasma` (glow state and light), `SfrGameTest.sfrLabBuildIsCompleteAndOff` (the lab torus validates, links, fuel, off), `HelionSiteGameTest` and the reactor-site test in `RadiationGameTest` (slates in site loot). The GUI, JEI layout, textures, toasts, the 20-years line, the specks, the porthole glow's look and pulse, and the lab world need a human pass.

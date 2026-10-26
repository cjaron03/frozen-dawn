# Helion sites and SFR-1 recipes visual check

Owning checkout: `/Volumes/SSD/Work/frozen-dawn-helion`, branch `feat/helion-sites`. Launch with Java 21 and `./gradlew runClient --console=plain`. No lab bridge function is needed. Use a new or throwaway world; the site commands bury structures in the terrain.

## What changed

- **ORSA Helion Confinement Test Facility.** A buried 17x17 concrete hall inside a packed-ice jacket, 24 blocks below the lowest ground of its footprint. It holds a cracked, dead tokamak laid out like the SFR-1 torus, with the world's only Confinement Core at its centre. A crack runs from the outer ring to the core. The control desk has three barrels: 16 Superconducting Coil Scrap, 12 First-Wall Tiles, and the Helion Sealed Log. An ice-plugged ladder shaft rises to an iron hatch at the surface, with a bent antenna mast beside it.
- **Placement.** One candidate per 128x128-chunk region, and half the regions roll one, so sites are rarer than CFU-7 reactor sites. A site never appears within 1,000 blocks of spawn. It needs tolerated biomes, enough depth, and clearance from other landmarks. Only new chunks are considered.
- **Looting.** Breaking the Confinement Core marks the site looted. `locate` and the Uplink then stop listing it.
- **Uplink reveal.** Each relay archive fragment also prints a Helion Facility Plot. The plot circles the nearest site no earlier fragment revealed: either a built hall that still holds its core, or a candidate no one has reached. The candidate is promised, so it is built when its chunk loads, even on ground it would otherwise reject, as long as the ground is deep enough. The console log adds a line naming the site's sector and distance. If the transmitter is away, the console holds the plot with the transcript.
- **Ground-penetrating radar.** The Plumb Line reports "ORSA Helion facility" over a site.
- **SFR-1 recipes.** Every SFR-1 part except the Confinement Core now has a crafting recipe. Magnet Coils need Superconducting Coil Scrap. The First Wall can be crafted from four First-Wall Tiles. The Isotope Column also has a recipe. The core is still only found at Helion sites.

## Commands

```
/fd world helionsite
/frozendawn locate orsa
```

`/fd world helionsite` (operator) buries a site 24 blocks ahead of you and records it as built. `/frozendawn locate orsa` (also in `locate all`) prints a "Helion Site:" line for the nearest built, unlooted site or unbuilt candidate.

## Checks

1. Run `world helionsite`, then walk to the hatch. Check that it is visible from a distance, that the antenna reads as a landmark, and that the hatch opens.
2. Mine out the two packed-ice plug blocks and climb down. Check that the ladder reaches the floor and that the hall reads as cold, abandoned and frozen in.
3. Look at the tokamak. Check that the crack reads as a breach and that the Confinement Core is visible through it.
4. Open the three desk barrels. Read the sealed log.
5. Break the core, then run `locate orsa`. The site should no longer be listed.
6. Craft a Magnet Coil from the coil scrap. Craft the First Wall from tiles. Use the recipe book to check the other SFR-1 recipes.
7. Optional: complete an Uplink transmission that delivers an archive fragment. Check that a second map arrives with the transcript, and follow its circle to a site.

Headless coverage: `HelionSiteGameTest` (contents, ice jacket, crack, shaft, looting, rarity), `SfrRecipeGameTest` (all 11 grids, coils need scrap, no core recipe), and the `UplinkGameTest` Helion reveal tests. Natural placement in real terrain, the look of the site and the plot's accuracy in the field need a human pass.

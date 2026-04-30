# Third-Party Asset Notes

This file tracks explicit third-party asset considerations for Frozen Dawn. Assets not listed here are covered by [ASSETS.md](ASSETS.md) as original Frozen Dawn project assets.

## External Runtime Dependencies

Frozen Dawn depends on Patchouli and Curios at runtime, but release builds do not bundle those dependency jars. Players should install them separately from their official distribution channels.

- Patchouli: https://www.curseforge.com/minecraft/mc-mods/patchouli
- Curios API: https://www.curseforge.com/minecraft/mc-mods/curios

## Minecraft / Mojang Assets

Frozen Dawn does not bundle Minecraft, NeoForge, Patchouli, Curios, or other third-party dependency jars inside the Frozen Dawn jar.

The replacement UI files under `src/main/resources/assets/minecraft/` are original Frozen Dawn compatibility assets documented in [ASSETS.md](ASSETS.md). They are not copied, traced, recolored, or mechanically derived from Mojang/Microsoft texture assets.

## Mars Ending Art

- `src/main/resources/assets/frozendawn/textures/gui/ending/mars_front.png`
- `src/main/resources/assets/frozendawn/textures/gui/ending/mars_right.png`
- `src/main/resources/assets/frozendawn/textures/gui/ending/mars_back.png`
- `src/main/resources/assets/frozendawn/textures/gui/ending/mars_left.png`
- `src/main/resources/assets/frozendawn/textures/gui/ending/mars_top.png`
- `src/main/resources/assets/frozendawn/textures/gui/ending/mars_bottom.png`

These Mars cubemap faces are original Frozen Dawn project art generated for the Phase 7 ending. They do not copy, trace, or derive from NASA/JPL, Ad Astra, Galacticraft, or other external texture assets.

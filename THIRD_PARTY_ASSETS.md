# Third-Party Asset Notes

This file tracks explicit third-party asset and dependency considerations for Frozen Dawn. Original project assets are identified in [ASSETS.md](ASSETS.md). Audio source rights, generated voices, and unresolved provenance checks are tracked separately in [AUDIO_NOTICE.md](AUDIO_NOTICE.md).

## External Runtime Dependencies

Frozen Dawn depends on Patchouli, Curios, JEI, and GeckoLib at runtime, but release builds do not bundle those dependency jars. Players should install them separately from their official distribution channels.

- Patchouli: https://www.curseforge.com/minecraft/mc-mods/patchouli
- Curios API: https://www.curseforge.com/minecraft/mc-mods/curios
- Just Enough Items (JEI): https://modrinth.com/mod/jei
- GeckoLib: https://modrinth.com/mod/geckolib

The current build pins GeckoLib `4.8.4` and declares a required runtime range of `[4.8.4,5)`. GeckoLib code is distributed under its own MIT license and is not relicensed by Frozen Dawn.

## Minecraft / Mojang Assets

Frozen Dawn does not bundle Minecraft, NeoForge, Patchouli, Curios, JEI, GeckoLib, or other third-party dependency jars inside the Frozen Dawn jar.

The replacement UI files under `src/main/resources/assets/minecraft/` are original Frozen Dawn compatibility assets documented in [ASSETS.md](ASSETS.md). They are not copied, traced, recolored, or mechanically derived from Mojang/Microsoft texture assets.

## Mars Ending Art

- `src/main/resources/assets/frozendawn/textures/gui/ending/mars_front.png`
- `src/main/resources/assets/frozendawn/textures/gui/ending/mars_right.png`
- `src/main/resources/assets/frozendawn/textures/gui/ending/mars_back.png`
- `src/main/resources/assets/frozendawn/textures/gui/ending/mars_left.png`
- `src/main/resources/assets/frozendawn/textures/gui/ending/mars_top.png`
- `src/main/resources/assets/frozendawn/textures/gui/ending/mars_bottom.png`

These Mars cubemap faces are original Frozen Dawn project art generated for the Phase 7 ending. They do not copy, trace, or derive from NASA/JPL, Ad Astra, Galacticraft, or other external texture assets.

## Audio Recordings and Generated Voices

Processed audio may retain obligations or restrictions from its underlying source recording even when Frozen Dawn authored the edit, arrangement, mix, script, and implementation. The authoritative per-feature provenance records live under [`tools/audio_sources`](tools/audio_sources).

Do not treat source-repository inclusion, deterministic processing, or an unlisted runtime filename as evidence that a recording is wholly owned by Frozen Dawn. See [AUDIO_NOTICE.md](AUDIO_NOTICE.md) for the release review boundary and generated-voice warning.

# Airlock textures

User-approved Frozen Dawn art, generated and edited with the built-in image tool. No third-party downloaded asset is included. The three approved concepts and their prompts are preserved under `output/airlock-concepts/v1/` (local review artifacts). `approved-atlas.png` is the derivative source used by this export; `provenance.json` records its edit prompt.

Run with Java 21: `java tools/texture_sources/airlock/ExportAirlockTextures.java .` from the owning checkout. This packages seven measured source tiles as point-sampled 16x16 PNGs. Door item geometry reuses the top and bottom textures. `sha256.json` records the source and shipped outputs. The generated atlas contains margins and unequal rows, so measured tile bounds are explicit and dimension-checked.

# Copper Rack native textures

Owner selected Copper Rack on October 14, 2026. Generated cell sprite, bank four-face atlas and dock face with built-in imagegen, using the selected concept as a reference. Preserved all generated originals. Exported six exact 16x16 RGBA PNGs by quadrant extraction/filtered downsampling plus a 12-color copper/graphite/cyan palette to preserve thin indicators, with binary alpha for the cell and opaque block faces. Previews enlarge the actual installed pixels with nearest-neighbor sampling. The cell uses the standard generated item model; bank/dock use full-cube models matching their existing collision and seal. Opposite access faces depict the same four/one cell channels; side, top and maintenance panels share the graphite/copper design. This is static casing artwork; actual inventory/charge remains in the existing screen and cell charge bar. No gameplay or saved state changed.

## Exact prompt set

### Cell

```text
Use case: precise-object-edit. Convert the POWER CELL from the supplied Copper Rack concept into ONE production Minecraft inventory sprite. Only one flat front-facing upright power cell, centered on a genuinely transparent square canvas. Its entire logical sprite canvas must be exactly 16 by 16 square pixels, enlarged evenly using nearest-neighbor pixel scaling for this output. No subpixel detail: use at most 12 colors. Copper top and bottom contact caps, charcoal graphite casing, recessed cyan vertical strip, clear black outline and upper-left pixel highlight. Cell occupies about x5..10,y1..14 of the 16x16 grid. No extra objects, text, shadow, 3D perspective, grid lines, background or antialiasing. Preserve the selected concept's industrial copper/graphite/cyan identity. This will be downsampled to a literal 16x16 Minecraft PNG, so prioritize a crisp simple silhouette.
```

### Bank atlas

```text
Use case: precise-object-edit. Convert the selected Copper Rack BATTERY BANK in the reference into a flat Minecraft block texture atlas, exactly FOUR equal square texture tiles in a 2 by 2 arrangement that fills the entire canvas edge to edge, no gaps, labels, borders between tiles or backdrop. Each tile uses an EXACT 16 by 16 logical pixel grid, enlarged evenly with nearest-neighbor scaling, no smaller details. Upper-left tile: battery-bank FRONT, opaque complete block face, charcoal graphite frame, four recessed copper-capped cells in a 2 by 2 grid, each tiny cyan vertical strip, copper corners, one cyan status pixel at bottom center. Upper-right tile: opaque matching graphite SIDE casing with chunky dark recessed ribs and copper corner brackets, NO cells, NO indicators. Lower-left tile: opaque TOP casing, dark graphite ridged panel with copper corner plates, no cells or cyan. Lower-right tile: opaque BACK casing, dark graphite access panel, four chunky corner rivets and a small copper contact block, no cells or cyan. Face-on orthographic pixel textures only, flat Minecraft sprite shading, limited copper gray cyan palette; no isometric render, no lighting bloom, no gradients, no text, no smooth material textures. The exact four quadrant images will become four independent 16x16 Minecraft textures.
```

### Dock

```text
Use case: precise-object-edit. Convert the selected Copper Rack CELL DOCK from the reference into ONE opaque front-facing square Minecraft block-face texture. Full canvas is precisely a 16 by 16 LOGICAL pixel grid, enlarged evenly, hard square pixels, no subpixel detail, no labels, no border outside the texture, no surrounding backdrop. This is the FRONT of a graphite machine housing with a single central recessed upright copper-capped power cartridge, one cyan vertical charge strip, warm copper retaining clamps and a thick graphite/copper cradle base along the bottom. Match the reference's charcoal graphite and warm copper palette and simple worn pixel highlights. Fill the face edge to edge with the housing so it can texture an ordinary Minecraft block. Exactly ONE cartridge, clearly different from the four-cell bank. Flat orthographic pixel art, no perspective, no realistic gloss, no soft gradients, no glow bloom. It will be exported as a literal 16x16 Minecraft PNG.
```

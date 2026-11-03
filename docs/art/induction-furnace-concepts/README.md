# ORSA Induction Furnace concept

Created October 14, 2026 with the built-in imagegen tool. Prompt: [prompt.txt](prompt.txt). Original generated PNG: 01-orsa-steel-copper.png. This is a concept sheet with enlarged texture plans, not exact 16x16 shipping textures. Native export must simplify and validate the pixel grid; generated labels are not a dimensions guarantee.

Owner accepted five-second smelting, pause at 0%, resume at 25%, and the simple screen. Owner rejected the current block art and requested Mekanism inspiration.

| Candidate | Intended use | Verified rights/compatibility | Takeaway | Decision |
| --- | --- | --- | --- | --- |
| Mekanism Energized Smelter, official wiki image | Visual study | Wiki image-specific reuse terms not established; no download or asset reuse. Mekanism 1.21.x repository LICENSE is MIT for software; not assumed to cover this wiki render. Reference build.gradle uses NeoForge. No dependency added. | Framed steel cube, distinct inset face, large vent bands | Study only |
| Original ORSA concept | Furnace art direction | Independently generated from a text description; no Mekanism image uploaded or sampled | Steel frame, copper accents, sealed chamber, work-state amber and cyan indicator | Concept for native authoring |

Primary references inspected:
- [Official Energized Smelter](https://wiki.aidancbrady.com/wiki/Energized_Smelter): the electric furnace counterpart. Official wiki labels its induction multiblock as [Induction Matrix](https://wiki.aidancbrady.com/wiki/Induction_Matrix), an energy store.
- [Exact image and file history](https://wiki.aidancbrady.com/wiki/File:Energized_Smelter.png): 512x512 render, current upload February 16, 2021 by YoinkusDoinkus; inspected in browser, not stored or shipped.
- [Viewed image](https://wiki.aidancbrady.com/w/images/aidancbrady/1/12/Energized_Smelter.png).
- [Repository license, 1.21.x](https://github.com/mekanism/Mekanism/blob/1.21.x/LICENSE).
- [Build, 1.21.x](https://github.com/mekanism/Mekanism/blob/1.21.x/build.gradle).
- [Native machine class, 1.21.x](https://github.com/mekanism/Mekanism/blob/1.21.x/src/main/java/mekanism/common/tile/machine/TileEntityEnergizedSmelter.java).

Implementation plan: author exact opaque 16x16 idle front, working front, side, top, back and bottom; match frame seams across faces. Bind existing facing/LIT variants through native cube models. Painted working colors track actual processing. Existing full-cube geometry and two-slot screen suit this direction. Owner selected this direction. Exact native textures and exporter are now installed; see [native/README.md](native/README.md). Human visual acceptance of the new native art is pending.

Provenance: local output docs/art/induction-furnace-concepts/01-orsa-steel-copper.png; creator built-in imagegen, prompted by Codex for Frozen Dawn; generated October 14, 2026; original format PNG; source is the local generated image recorded in manifest.json; copied without modifications; no third-party texture/model pixels incorporated; no new third-party notices needed for this independently authored concept. Native assets and processing records follow separately if this direction is selected. Concept inspected for coherent idle/working casing and broad readable details; exact resolution not claimed.

# Induction Furnace Check

Source: [Notion Power System](https://app.notion.com/p/3f57cfaa89018123a786fbb637b5ecb1), refetched October 14, including the Induction Furnace additions.

ORSA Induction Furnace accepts native SMELTING recipes and halves their cooking time (ordinary 200-tick recipes take 100 processing ticks / 5 seconds). It has two slots: input and output. No fuel or combustion oxygen is used. Status shows Idle, Waiting for power, Smelting, No furnace recipe or Output blocked. Power loss pauses progress; changing recipe input resets it. Input/output/progress and recipe XP history save together. XP and normal smelting events use the vanilla result-slot path; hoppers input from above/sides and extract below. Breaking releases input/output and unclaimed XP.

A working furnace requests 2 kW in the ordinary 20-tick network allocation. A full service allocation is required; insufficient power sheds the whole machine. Generation serves loads first, then surplus charges banks. Allocated machine capacity expires after its one-second interval. Battery deficit is debited on each actual processing tick (100 J at zero generation), so a blocked/idle/partial job cannot waste or duplicate a whole battery interval. Grants are transient and reconstructed; saved progress does not provide free energy on reload. Existing lights retain their one-second accounting. Core allocation is never changed by demand.

Each actual paid tick contributes 25 thermal energy units (500/s, representing the specified 0.5 kW waste heat). The existing thermal model is game-calibrated; it applies its usual 0.25 vacuum transfer efficiency. The one source budget is shared between connected loaded rooms, admitted before thermostat fuel decisions, and not duplicated or temperature-capped. Unmanaged/outdoor heat dissipates; no chunks are forced, no offline heat is accumulated. Blocked, idle and unpowered machines emit no new heat.

Original ORSA steel/copper 16x16 textures now replace the initial Copper Rack/vanilla blast-furnace mix. Idle and smelting states share the exact same hardware pixels; only the sealed chamber and cyan indicator change with paid work. Sources and processing are recorded in docs/art/induction-furnace-concepts/native. The directional model matches the full-block collision/seal. The screen is a compact 176x166 panel with two slots, a progress arrow and status; no configuration tabs or fuel gauge.

The fresh world is in Creative, Default preset chosen/locked, paused at late vacuum (90% of the Default 120-day timeline). A world-local pack after mod_data filters advancements and disables chat awards. Fixed glowstone floor lamps provide fixture visibility. Old power saves are preserved. Setup runs once under `#built ifurn`; never reset/recreate this save.

| Station | Action | Expected |
| --- | --- | --- |
| Basic, z0 | Bind Core x0 → relay x4 → furnace x8. Set 25%; insert raw iron. | Ingot in about 5 seconds without fuel/air. Core 0% or broken relay pauses; restore power/rebind to resume. Empty/full output draws zero. |
| Backup, z16 | Core x0 → Bank x4 → furnace x8. Insert a cell, charge at 25%, then set Core to 0%. | Stored energy runs the furnace at 2 kJ/s of actual work. The finite 4 kJ cell permits 2 seconds of progress then pauses; swapping the 20 kJ cell resumes. Idle/blocked output stops cell drain. |
| Brownout, z32 | Core x0 → relay x4 → both furnaces x8,z30/34, both with inputs. | 9%: neither; 10%: one; 20%: both. Idle demand returns to zero. |

For saved-state acceptance, pause a partial job by setting Core 0%, note input/output and arrow, then Save and Quit and reopen. Verify retained progress/items/links before restoring power. Check output extraction and dropped inputs/output as needed. Use `/function induction_furnace_check:controls` for clickable directions. The owner judges UI, visuals and actual live behavior; native tests do not establish owner/LAN acceptance.

Launch from the owning Java21 Gradle checkout with `./gradlew runClientLab --console=plain -PfdLabWorld='Induction Furnace Check'`.

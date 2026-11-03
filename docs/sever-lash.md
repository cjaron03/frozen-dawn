# ORSA LC-4 Sever Lash

Source of truth: the Notion page "ORSA LC-4 Sever Lash" (drafted 2026-10-23). First piece of the defense set; the
Proximity Tracker, Arc Fence and Sweep Turret follow. Homage to the hellwhip in Dan Simmons' *Hyperion*.

## Slices

Each slice is its own branch and PR off `feat/maeve-director`.

| # | Slice | Status |
|---|---|---|
| 1 | Lash core: item, power, swept-arc damage, heat | This branch, `feat/sever-lash` |
| 2 | Lash world effects, modules, acquisition, remaining homage | Not started |
| 3 | Proximity Tracker (handheld, then wall-mounted) | Not started |
| 4 | Arc Fence | Not started |
| 5 | Sweep Turret (needs 1 and 3) | Not started |
| 6 | Maeve interplay for all four | Not started |

## Slice 1 behavior

- Hold use to fire. Each tick pays 1250 J (25 kW) from carried Power Cells through `PowerCellItem.pay`, the same
  hotbar, main inventory, then offhand order as the Suit Headlamp. A full cell is 20 s of beam. The Lash holds no
  charge of its own; you recharge the cells at a Cell Dock.
- Reach 12. Each tick cuts every living thing between last tick's aim and this tick's (`BeamSweep`: rays at most a
  quarter block apart at full reach, at most 64 a tick). A broken or stale sweep restarts as a single ray. Blocks stop
  the beam.
- 0.3 damage a tick (6 a second). `frozendawn:sever_lash` bypasses the hurt cooldown and deals no knockback.
  Armor and shields stop half of what they normally would (`SeverLashDamageHandler`).
- Heat (`LashHeat`, on the stack, settled from game time): 6 s of continuous fire overheats it. The lockout is 4 s
  in air and 8 s where the eye has no air (`CombustionAtmosphere.canBurnAt`). Idle ticks cool it as fast as firing
  heats it in air, and at half that rate in vacuum. A locked or unpowered trigger costs nothing.
- In air the beam draws a red dust line; in vacuum only the impact shows. Overheat plays a vanilla extinguish hiss
  as a placeholder.
- Hyperion touches: serial "S/N LC-4/KSD", the "NOT FOR USE ON PERSONNEL." tooltip, the Hellwhip challenge advancement
  (three hostiles cut in one tick's sweep), and a player death message naming the hellwhip.
- For now it is in the creative tab only. Recipe and Helion/CFU-7 spawns are slice 2.

## Deferred to slice 2

- Melting, scorching and ignition. The Master Architect's Last Wall is ice and packed ice, so the melt must exempt
  Last Wall blocks for "the Last Wall stops it" to hold.
- Heat Sink, Long Focus and Wide Aperture modules; the ORSA requisition document; a proper beam render, hum and
  emitter glow; a better texture.
- The two specs disagree on the Sweep Turret's recipe: the turret page gives a crafting recipe, while the Fabricator
  page lists it as a blueprint. Settle this before slice 5.

## Verification

- JUnit: `LashHeatTest`, `BeamSweepTest`.
- GameTests (`-PfdTests=severLash`): power draw, arc-only cutting, cooldown and knockback, armor, overheat, Hellwhip.
- Not covered by a test: the shield half-block (needs a player blocking for 5+ ticks) and anything visual or audible.
  These need the owner's visual pass.

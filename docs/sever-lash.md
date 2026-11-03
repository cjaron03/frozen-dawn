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
- Reach 12. Each tick cuts every living thing between last tick's aim and this tick's (`BeamSweep`). Rays are at most
  a quarter block apart at full reach for turns up to about 75 degrees in one tick; past that, the 64-ray cap spreads
  them wider. A broken or stale sweep restarts as a single ray. Blocks stop the beam.
- 0.6 damage a tick (12 a second; owner's playtest call on 2026-11-02, double the spec's 6). `frozendawn:sever_lash` bypasses the hurt cooldown and deals no knockback.
- Owner ruling, 2026-11-02: the Lash bypasses armor entirely (`bypasses_armor`), so armor stops none of the cut and
  never wears from it. This replaces the spec's "armor blocks less than usual". Shields still stop half of what they
  would (`SeverLashDamageHandler`).
- Heat (`LashHeat`, on the stack, settled from game time): 6 s of continuous fire overheats it. A stored lockout
  further off than the vacuum maximum, which comes from carrying the Lash out of a world with a later clock, counts as
  lapsed. The lockout is 4 s
  in air and 8 s where the eye has no air (`CombustionAtmosphere.canBurnAt`). Idle ticks cool it as fast as firing
  heats it in air, and at half that rate in vacuum. A locked or unpowered trigger costs nothing.
- In air the beam draws a red dust line; in vacuum only the impact shows. Overheat plays a vanilla extinguish hiss
  as a placeholder.
- Hyperion touches: serial "S/N LC-4/KSD", the "NOT FOR USE ON PERSONNEL." tooltip, the Hellwhip challenge advancement
  (three hostiles cut in one continuous beam; letting go or overheating starts a new count), and a player death message naming the hellwhip.
- For now it is in the creative tab only. Recipe and Helion/CFU-7 spawns are slice 2.

## Deferred to slice 2

- Melting, scorching and ignition. The Master Architect's Last Wall is ice and packed ice, so the melt must exempt
  Last Wall blocks for "the Last Wall stops it" to hold.
- Heat Sink, Long Focus and Wide Aperture modules; the ORSA requisition document; a proper beam render, hum and
  emitter glow; a better texture.
- The two specs disagree on the Sweep Turret's recipe: the turret page gives a crafting recipe, while the Fabricator
  page lists it as a blueprint. Settle this before slice 5.

## Playtest world

`python3 tools/prepare_sever_lash_playtest.py --source <any save>/level.dat --destination run/saves/'Sever Lash Check'`
builds a flat firing range: Hellwhip husks, reach pigs at 12 and 15 blocks, a walled pig, bare and diamond husks, and
a sealed glass room for air vs vacuum. By the owner's ruling (2026-11-02) this world keeps advancement toasts and chat
on so Hellwhip can be seen; other generated worlds still filter them. Difficulty is Normal and locked.

## Deferred from the slice 1 branch review

- Hurt flash and hurt sound replay on every tick a target is cut, because the cooldown is bypassed. Judge by ear in
  the sound slice.
- The Lash cuts anything living except the shooter: its own mount, tamed pets, villagers. This is a design call.
- The held stack is rewritten every firing tick; check for a hand re-equip bob in the visual pass.
- `use()` checks only the lockout, so a trigger with no cells flickers the use animation.
- A player who logs out mid-beam leaves one stale entry in the aim map. It is harmless.

## Owner playtest, 2026-11-02 (Sever Lash Check, runClient, commit 48c503b7)

Unprompted report: "i like it", "no sounds though", and "it mowed down the husks and the pig". Asked which pig, the
owner said it died because they were standing too close; after respawning the targets and walking back, it was not
hit. The log shows Hellwhip earned 16 s after joining. The missing sounds are expected: slice 1 only has the
placeholder overheat hiss. Next step proposed: procedural Lash sounds and a 3D item model, in slice 2.

## Owner playtest 2, 2026-11-02 (Sever Lash Check, runClient, PR head 19809a81)

The owner asked for another cell mid-test, then reported "ngl this thing needs to do more damage lol". The owner
set 12/s. That is above the spec's Sweep Turret 8/s, so raise the turret with it in slice 5.

## Verification

- JUnit: `LashHeatTest`, `BeamSweepTest`.
- GameTests (`-PfdTests=severLash`): power draw, arc-only cutting, cooldown, knockback and stale-aim reset, armor
  bypass, no player armor wear, overheat, Hellwhip.
- Not covered by a test: the shield half-block (needs a player blocking for 5+ ticks) and anything visual or audible.
  These need the owner's visual pass.

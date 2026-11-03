# ORSA LC-4 Sever Lash

Source of truth: the Notion page "ORSA LC-4 Sever Lash" (drafted 2026-10-23). First piece of the defense set; the
Proximity Tracker, Arc Fence and Sweep Turret follow. Homage to the hellwhip in Dan Simmons' *Hyperion*.

## Slices

Each slice is its own branch and PR off `feat/maeve-director`.

| # | Slice | Status |
|---|---|---|
| 1 | Lash core, Seared, acquisition, world effects, overheat glow, hellwhip memo | This branch, `feat/sever-lash`, PR #143 open, not ready to merge |
| 2 | Lash modules (need the ORSA Fabricator, which is not built), beam render, hum, sounds, 3D model, texture | Not started |
| 3 | Proximity Tracker (handheld, then wall-mounted) | Not started |
| 4 | Arc Fence | Not started |
| 5 | Sweep Turret (needs 1 and 3) | Not started |
| 6 | Maeve interplay for all four | Not started |

The Notion Lash page also lists a Sentinel Floodlight in the defense set; no slice covers it yet. The Notion
TR-0 Terminus page (2026-11-03) is its own slice after the Sweep Turret.

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
- Seared (owner's call after playtest 3, 2026-11-03, to make the power draw pay off): every cut also sears
  (`Sear`, `SearedEffect`). A first cut sears for 2 s; each further tick of contact adds 3 ticks, so a held beam
  nets 2 ticks a tick, up to 5 s. About half a second of contact is Seared II and a second is Seared III; a fading
  burn keeps its level when cut again. Each level burns 1 a second, on the target's own clock so a held beam cannot speed it up (`frozendawn:seared`, which also bypasses armor,
  the hurt cooldown and knockback) and slows by 15%. It is heat, not fire, so it works the same in vacuum.
  Custom ember particles cool from white through orange to red; the icon and embers come from
  `docs/art/sever-lash/make_textures.py`.
- Heat (`LashHeat`, on the stack, settled from game time): 6 s of continuous fire overheats it. A stored lockout
  further off than the vacuum maximum, which comes from carrying the Lash out of a world with a later clock, counts as
  lapsed. The lockout is 4 s
  in air and 8 s where the eye has no air (`CombustionAtmosphere.canBurnAt`). Idle ticks cool it as fast as firing
  heats it in air, and at half that rate in vacuum. A locked or unpowered trigger costs nothing.
- In air the beam draws a red dust line; in vacuum only the impact shows. Overheat plays a vanilla extinguish hiss
  as a placeholder.
- Hyperion touches: serial "S/N LC-4/KSD", the "NOT FOR USE ON PERSONNEL." tooltip, the Hellwhip challenge advancement
  (three hostiles cut in one continuous beam; letting go or overheating starts a new count), and a player death message naming the hellwhip.
- For now it is in the creative tab only; see the plan below.

## Plan: finishing slice 1 on this branch (owner, 2026-11-03)

After checking the Notion spec, the owner held PR #143 until the Lash can be had and used in survival. Build these
on `feat/sever-lash`, each test-first, with `gameTestQuick -PfdTests=...` while iterating and `architectVerify`
before handing back. Verify every hook below against current code first; these are starting points, not facts.

1. **Acquisition (spec section 6).**
   - New Lash Emitter item.
   - Helion sites: 1 to 2 in the research labs, guaranteed. Start from `HelionSiteBuilder` and
     `HelionSitePlacement`.
   - CFU-7 reactor sites: about 1 in 4 get one in the maintenance bay, deterministic from the world seed. Start from
     `ReactorSiteBuilder` and `ReactorSitePlacement`.
   - Shaped crafting recipe: Lash Emitter, Acheronite Shard (lens), Power Cell, 2 iron ingots, 1 lead ingot
     (`ModItems.LEAD_INGOT`, `ACHERONITE_SHARD`, `POWER_CELL`). The crafted Lash should start cool.
   - Keep the Lash in the creative tab.
2. **Hellwhip memo (spec section 9).**
   - A rare ORSA document at a Helion site: a supervisor complains that a technician keeps calling the LC-4 a
     hellwhip, after some old book. The requisition is denied on the name.
   - Use the existing `OrsaDocumentItem`, and no book quotes.
   - The TR-0 Terminus page plans a matching "deathwand" memo, so keep the voice reusable.
3. **World effects (spec section 5).** These act where the beam lands each tick.
   - Snow and ice melt instantly. Packed and blue ice take longer: store heat per block, and let it cool.
   - Stone scorches into `ModBlocks.SCORCHED_GROUND`.
   - Where there is oxygen (`CombustionAtmosphere.canBurnAt` at the hit), fuel and wood ignite. In vacuum nothing
     burns.
   - The Master Architect's Last Wall (placed ice and packed ice) is exempt. Find how
     `MasterArchitectConstructionController` records its blocks.
   - The beam is player-fired, so respect protection and spawn rules the way player block breaking does.
   - Ice melting into water must not flood a sealed room's atmosphere bookkeeping unexpectedly. Check
     `RoomAtmosphere`.
4. **Overheat glow (spec section 4).**
   - The held Lash glows red-hot as heat nears overheat and while it is locked out, using an item model override
     on heat.
   - The extinguish hiss stays as a placeholder until the sound slice.
   - This needs the owner's visual check.
5. **Playtest world.**
   - Add stations to `tools/prepare_sever_lash_playtest.py`: a melt, scorch and ignite wall in air and in vacuum, a
     Last Wall sample, the recipe ingredients, and the emitter.
   - Deliver them as a new save or with `--pack-only`, and never overwrite an existing save.
6. **Close.**
   - Run one fresh branch review (Opus, because it changes world generation and loot), then fix its findings
     test-first.
   - Run `architectVerify`, update this doc, and push. Do not merge.

## 3D model spec (slice 2, owner liked the mockup 2026-11-03)

Mockup: `docs/art/sever-lash/model-mockup.png`, drawn by `make_model_mockup.py` at Minecraft scale (one model unit
a pixel, 16 to the block). The owner's response was "i like it". The geometry is what the model will be. The real
texture adds surface detail, the display transforms are tuned in game, and the hold figures are sketches. It is built
in slice 2.

- **Shape.** One block long, about ten elements, all axis-aligned except the fins. From the back:
  - a Power Cell port with copper contacts;
  - a 9x3x3 gunmetal body with a dark underside;
  - a lead pistol grip, raked back, behind a lead hand guard (the recipe's lead ingot);
  - two copper coil rings, 5x5, round the front of the body;
  - a 2x2 barrel ending in the Acheronite lens;
  - three cooling fins on top.
  The lens and any hot part render fullbright (NeoForge element `neoforge_data` light 15).
- **Heat states.** These are item model overrides on two client predicates: `frozendawn:heat` (0 to 1, heat over
  the overheat limit, settled from the stack's `LashHeat` and client game time) and `frozendawn:vent` (0, 0.5 or 1,
  how far the lockout has run). Slice 1's overheat glow uses the same predicates on the flat sprite, so the 3D model
  only swaps the models behind them.
  - Cool: steel fins, cyan lens.
  - Warm (from half heat): the fin tips and the barrel near the lens dull red.
  - Hot (from 80%): the fins and barrel orange and fullbright, the lens white-cyan.
  - Overheat: the lockout starts and the fins vent.
- **Overheat animation.** Vanilla element rotation only allows steps of 22.5 degrees, so the vent is three stepped
  poses rather than a smooth tween.
  - The fins lean back 22.5 degrees, then 45, over the first 4 ticks.
  - The slots under them glow white, behind a two-frame animated texture that flickers.
  - Over the lockout the fins cool from white through orange to red, then fold shut in the last 4 ticks.
  - In air, the client puts small grey smoke puffs above the vents every few ticks. In vacuum there is no smoke,
    only the glow, which matches the beam's rules.
  - The hiss stays the placeholder until the sound slice.
  - Fallback, only if the steps read as choppy: a custom item renderer (`BlockEntityWithoutLevelRenderer`) that
    tweens the fin angle. It costs more code and is not data-driven.
- **Holding.**
  - Third person, idle: carried one-handed at the hip, pointing forward.
  - Third person, firing: a custom arm pose (`IClientItemExtensions.getArmPose`). The main arm is raised level to
    the eye line along the look direction, and the off hand braces under the coils when it is empty. Other players
    see where the beam points.
  - First person: held low right, lens toward the crosshair. While firing it lifts slightly with a sub-pixel jitter
    and no recoil kick, since it is a beam. During lockout it dips and tilts away about 10 degrees, as if being
    aired out, and comes back when the lockout ends.
  - The GUI shows a three-quarter view, like the Master Architect staff.
- **Re-equip bob.** The server rewrites the held stack every firing tick, which would make a 3D model bob.
  `shouldCauseReequipAnimation` returns false when only `LashHeat` changed.
- **Open for the owner:**
  - Is the off-hand brace wanted, or one-handed only?
  - Is the smoke wanted in air?
  - Should the cell port show charge?

## Deferred to slice 2

- Heat Sink, Long Focus and Wide Aperture modules. Per the Notion Fabricator page they are Fabricator recipes
  (5 kW, 30 s), so they wait for the Fabricator.
- A proper beam render, hum, procedural sounds, a 3D item model and a better texture.
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

## Owner playtest 3, 2026-11-03 (Sever Lash Overworld, runClient, PR head 92341c77)

Unprompted report: it "still needs to get buffed" and "needs to justify the battery usage", "maybe give it a
effect". Overheat needs visuals; the owner deferred those to the texture pass. The log shows a 30 s session that
spawned at a village. The only kill was a Cartographer villager, "cut apart by a diagnostic emitter", a second
before quitting.

## Owner playtest 4, 2026-11-03 (runClient, PR head a6976673)

First pass with Seared. Unprompted report: "i like it".

## Verification

- JUnit: `LashHeatTest`, `BeamSweepTest`.
- JUnit `SearTest`: Seared build-up, cap and levels.
- GameTests (`-PfdTests=severLash,seared`): power draw, arc-only cutting, cooldown, knockback and stale-aim reset,
  armor bypass, no player armor wear, overheat, Hellwhip, Seared application and build-up, the burn after the beam
  leaves (through armor, no shove), and the slowdown.
- Not covered by a test: the shield half-block (needs a player blocking for 5+ ticks), the ember particles and icon,
  and anything else visual or audible.
  These need the owner's visual pass.

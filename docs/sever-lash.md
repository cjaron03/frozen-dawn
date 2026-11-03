# ORSA LC-4 Sever Lash

Source of truth: the Notion page "ORSA LC-4 Sever Lash" (drafted 2026-10-23). First piece of the defense set; the
Proximity Tracker, Arc Fence and Sweep Turret follow. Homage to the hellwhip in Dan Simmons' *Hyperion*.

## Slices

Each slice is its own branch and PR off `feat/maeve-director`.

| # | Slice | Status |
|---|---|---|
| 1 | Lash core, Seared, acquisition, world effects, overheat glow, hellwhip memo | This branch, `feat/sever-lash`, PR #143 open; built, awaiting the owner's playtest and merge approval |
| 2 | Beam render, hum and sounds, 3D model and texture; the Lash modules wait for the ORSA Fabricator | `feat/sever-lash-2` off `feat/sever-lash`; built, awaiting the owner's playtest |
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
- Slice 1 drew the beam as a red dust line in air, only the impact in vacuum, and played a vanilla extinguish hiss at
  overheat. Slice 2 replaces both (below).
- Hyperion touches: serial "S/N LC-4/KSD", the "NOT FOR USE ON PERSONNEL." tooltip, the Hellwhip challenge advancement
  (three hostiles cut in one continuous beam; letting go or overheating starts a new count), and a player death message naming the hellwhip.
- The held Lash glows as it heats (`LashHeat.glow` and `vent`, as the client item predicates `frozendawn:heat` and
  `frozendawn:vent`, with overrides in `models/item/sever_lash*.json`). Slice 2 moved this onto the 3D model.
  - The client settles heat on its own clock and has no vacuum reading, so in vacuum the glow fades a little early.

### Acquisition

- The Lash stays in the creative tab beside a new **Lash Emitter**, the bare salvaged diagnostic emitter.
- Every Helion site keeps one or two emitters on a diagnostics bench, a barrel at `HelionSiteBuilder.DIAGNOSTICS`
  on a short andesite bench by the south wall.
- About one CFU-7 reactor site in four keeps one in its maintenance bay, a barrel at `ReactorSiteBuilder.MAINTENANCE`
  in the hall's west corner. The bay always holds a few iron and copper ingots.
- Both rolls come from `LashSalvage`: a hash of the world seed and the site's packed position, independent of the
  builders' own random. A site always rolls the same, and adding the rolls changed no earlier layout. Sites built
  before this change are not revisited.
- Shaped recipe (`recipe/sever_lash.json`): iron, emitter and Acheronite Shard on top; lead ingot, Power Cell and
  iron below. Any cell will do, and its charge is used up in the craft. The crafted Lash carries no heat, so it
  starts cool.

### Hellwhip memo

- On about one Helion bench in four (`LashSalvage.helionMemo`), an ORSA document "Procurement Review - Req. 0447"
  (doc type `lc4_requisition_memo`).
- The plasma diagnostics supervisor denies, for the third time, a technician's requisition for six field-converted
  LC-4s. The form is fine; the technician keeps writing "hellwhip", after an old book, instead of the catalogue name.
  There are no book quotes.
- Reading it archives it: Field Manual entry "Procurement Review" and the hidden advancement "Denied on the Name".
- Reusable voice for the TR-0 deathwand memo: an ORSA PROCUREMENT REVIEW header, then From, Re and Req. lines, a
  dry account of the nickname problem, and a one-word italic Disposition with a short, deadpan note for the file.

### World effects (`LashWorldEffects`, `BlockHeat`)

These act on each block a ray lands on, once per tick however many rays hit it.
- **Snow and ice.** Snow (layers, block, powder) and plain or frosted ice flash to steam at once and leave air.
  Ruling (agent, 2026-11-03, for the owner to confirm): no meltwater. A water source would flow into a sealed room's
  air cells and reshape it. Air goes through the same block-change hooks as mining, so `RoomAtmosphere` sees an
  ordinary block removal.
- **Packed ice, blue ice and stone** give way only under a held beam.
  - The dwell is 1 s for packed ice, 2 s for blue ice, and 0.5 s for stone, which scorches into Scorched Ground.
    Stone is the tag `frozendawn:sever_lash_scorches`: overworld base stone, cobblestone and cobbled deepslate.
  - Each tick of contact adds two units of heat per block, and each idle tick sheds one, so a sweep past leaves them.
  - Heat is kept per level and position in memory, pruned once it passes 4,096 blocks. It is not saved.
  - Ruling (agent): stone needs a dwell, so one sweep cannot scorch a whole room.
- **Fire.** Where the cell the beam crossed has oxygen (`CombustionAtmosphere.canBurnAt`), flammable blocks catch:
  fire goes in that cell. Unlit campfires and candles light. In vacuum nothing burns, though heat still scorches and
  melts.
- **The Last Wall.** The Master Architect's Last Wall (`ArchitectEntity.holdsLastWall`, from the combat
  controller's placed wall blocks) neither melts nor heats. Its other constructions are not exempt.
- **Protection** is the same as a player's.
  - Melting and scorching need `level.mayInteract` and an uncancelled NeoForge block break event. That covers
    spawn protection, adventure mode and claim mods.
  - Ignition needs the player allowed at both the struck block and the fire cell. It writes through captured block
    snapshots and the place event, as item placement does, so a refused placement reverts before neighbours hear.
  - A refused block is not asked again for a second. Otherwise a beam resting on protected ground posts an event
    every tick, and the mod's own break listeners react to each one.

## Plan: finishing slice 1 on this branch (owner, 2026-11-03)

Done on 2026-11-03. The behavior is under "Slice 1 behavior" above, and the checks under "Verification". The
owner's plan covered six steps:
1. acquisition;
2. the hellwhip memo;
3. world effects;
4. the overheat glow;
5. playtest stations;
6. a branch review and `architectVerify`.

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
  the overheat limit, settled from the stack's `LashHeat` and client game time) and `frozendawn:vent` (1 for the
  first half of a lockout, 0.5 for the second, 0 otherwise). Slice 1's overheat glow uses the same predicates on the flat sprite, so the 3D model
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
  - Third person, idle: gripped one-handed in the fist, pointing forward.
  - Third person, firing: a custom arm pose (`IClientItemExtensions.getArmPose`). Both arms come up along the
    look direction with the fists together on the grip (owner ruling, playtest 6). Other players
    see where the beam points.
  - First person: held low right, lens toward the crosshair. While firing it lifts slightly with a sub-pixel jitter
    and no recoil kick, since it is a beam. During lockout it dips and tilts away about 10 degrees, as if being
    aired out, and comes back when the lockout ends.
  - The GUI shows a three-quarter view, like the Master Architect staff.
- **Re-equip bob.** The server rewrites the held stack every firing tick, which would make a 3D model bob.
  `shouldCauseReequipAnimation` returns false when only `LashHeat` changed.
- **Open for the owner:**
  - Is the smoke wanted in air?
  - Should the cell port show charge?

## Beam (slice 2, owner ruling 2026-11-03)

Mockup: `docs/art/sever-lash/beam-mockup.png` (`make_beam_mockup.py`). The owner picked **A, the whip**.
- A rendered beam, not particles: a white-hot core in an orange sheath with a violet halo, about a third of a block
  across.
- The near end follows the aim at once and the far end trails it, so a fast sweep bends into a curve. It snaps
  straight, with a crack, when the sweep stops.
- A faint red afterimage of the last few ticks' beams fades behind it.
- In vacuum only the core and the impact show.
- The bend is visual only: cutting still follows `BeamSweep`.

## Slice 2 (branch `feat/sever-lash-2`, 2026-11-03)

### Beam
- `SeverLash` sends one `LashBeamPayload` a firing tick to the shooter and every player tracking them: shooter, aim,
  length to where the last ray stops, whether it lands on a block, vacuum, and heat (0 to 1). The dust particles are
  gone. `LashBeamClient` draws it.
- Three camera-facing strips from the muzzle, each bright down the middle and clear at its edges, blended additively:
  a white core, an orange sheath and a violet halo 0.34 blocks across. The sheath and halo shimmer a little.
- The bend (`WhipBend`, pure and JUnit-tested): the near end follows the aim at once. The far end closes 40% of its
  lag a tick, so a steady sweep trails about one and a half ticks behind, and never more than 35 degrees. The
  direction turns toward the far end with the square of the distance along the beam. A turn under 1.5 degrees in a
  tick is a stop: the beam snaps straight, and cracks if it was bent 10 degrees or more. A flick of 25 degrees in one
  tick cracks on its own. Cracks are at least 6 ticks apart. The shooter's own beam follows their view each client
  tick; everyone else's follows the server's aim.
- The drawn curve stops at the first block it meets. The impact is a small flickering star with ember sparks.
- In air, a thin red afterimage of the last three ticks fades behind it. In vacuum only the core and the impact show.
- The bend is visual only: cutting still follows `BeamSweep`.
- Ruling (agent): the muzzle is placed by eye, low right of the view in first person, at the end of an arm raised
  along the aim otherwise. It may not meet the lens exactly; judge in game.

### Sounds (`tools/generate_sever_lash_sounds.sh`, sources in `tools/audio_sources/sever_lash/`)
- Server-played, heard by everyone near: a start-up strike on the first tick of a beam; a cut sizzle every quarter
  second while the beam lands on a block or cuts something (`SeverLash.SIZZLE_TICKS`); and an overheat vent (valve
  thump and steam hiss) that replaces the extinguish placeholder.
- Client-played, on every client that sees the beam: the hum loop, following the shooter, from pitch 0.75 cool to
  1.4 at overheat; and the crack from `WhipBend`.

### 3D model (`tools/build_sever_lash_models.py`, textures from `docs/art/sever-lash/make_textures.py`)
- The approved geometry, one model unit a mockup pixel, lens facing north. 16x16 material tiles under
  `textures/item/sever_lash/`, one texel a unit, replace the flat sprite and its glow overlays.
- One model a state, picked by overrides on `frozendawn:heat`, the new `frozendawn:firing` (the holder is using this
  stack) and `frozendawn:vent`:
  - cool: steel fins, cyan lens;
  - warm (from half heat): fin tips and barrel tip dull red;
  - hot (from 80%): fins red, tips and barrel orange;
  - firing, cool, warm or hot: the same, held along the raised arm in third person;
  - six vent stages (`LashHeat.ventStage`; the predicate is the stage in eighths): fins lean back 22.5 degrees for
    two ticks, then 45, white-hot over flickering white slots for the first half of the lockout, orange through the
    third quarter, red after, folding back to 22.5 and then shut in the last four ticks.
- Fullbright (`neoforge_data` light 15, unshaded): the lens always, the vent slots, and the fins and barrel tip when
  hot and through the whole vent. Ruling (agent): the red cooling fins stay fullbright too, as slice 1's cooling glow did.
- Holding (`SeverLashHold`):
  - third person idle: gripped in the fist, the body just above the hand, upright and level, lens forward;
  - third person firing: a custom arm pose (`FROZENDAWN_SEVER_LASH`, added through `META-INF/enumextensions.json`).
    Both arms come up along the look and turn in, as for a crossbow (main 0.3 rad, off 0.6 rad), so the fists meet
    side by side on the grip whatever the off hand holds. The firing model turns back 0.3 rad to point along the aim,
    and the third-person beam leaves its lens (1.16 forward, .25 up, .1 in from the shoulder pivot);
  - first person: low right, lens toward the crosshair. Firing lifts it slightly with a sub-pixel tremble and no
    kick; through a lockout it dips and tilts 10 degrees away, easing over four ticks at each end;
  - GUI: three-quarter view.
  - The third-person display transforms are solved from the render chain (`ItemInHandLayer`, the arm's `ModelPart`
    rotation, then the display transform), checked against a vanilla sword landing in the fist. Guesses from
    playtest 6's screenshots had left the Lash on its side beside the hand. The solver's reasoning is in the comment
    in `tools/build_sever_lash_models.py`.
- In air the vents put out grey smoke puffs every three ticks through the lockout. The client takes air or vacuum
  from that player's last beam.
- Re-equip bob: `SeverLashItem.shouldCauseReequipAnimation` ignores a change to `LashHeat` alone.
- Open for the owner, from the model spec: is the smoke wanted in air, and should the cell port show charge? The smoke
  is built; the cell port shows no charge. The owner asked for the two-handed firing hold in playtest 6.

### Slice 2 branch review (Opus, 2026-11-03)

Opus reviewed 267a8596..a5666787 because the branch adds client/server sync. Fixed, each with a failing test first
where one could be written:
- The firing model compared the held stack with the holder's use by identity. The heat rewrite replaces the held
  stack a client tick before the use follows, so the third-person Lash flipped between its firing and idle holds.
  `SeverLashItem.firing` now asks the hand (GameTest `severLashFiringModelSurvivesTheHeatRewrite`).
- The body's underside UV ran to 17, past its tile, sampling a neighbouring sprite. Bottom faces now read the tile's
  foot, and `SeverLashModelTest` keeps every UV within 0 to 16.
- An observer's whip stepped every client tick, so a tick with no new payload read as the sweep stopping: a false
  snap, and a crack if bent. It now steps only on ticks that bring a new aim. Client only, no test; it needs a
  second client to see.
- A hum the sound engine dropped was not restarted; it now checks the engine.

Set aside: a released or overheated beam lingers up to three client ticks plus latency, and the shooter's own beam
keeps following their view meanwhile. Judge it in the visual pass.

## Deferred to slice 3 or later

- Heat Sink, Long Focus and Wide Aperture modules. Per the Notion Fabricator page they are Fabricator recipes
  (5 kW, 30 s), so they wait for the Fabricator.
- The two specs disagree on the Sweep Turret's recipe: the turret page gives a crafting recipe, while the Fabricator
  page lists it as a blueprint. Settle this before slice 5.

## Playtest world

`python3 tools/prepare_sever_lash_playtest.py --source <any save>/level.dat --destination run/saves/<new name> --title <new name>`
builds a flat firing range: Hellwhip husks, reach pigs at 12 and 15 blocks, a walled pig, bare and diamond husks, and
a sealed glass room for air vs vacuum. Slice 1 (2026-11-03, save "Sever Lash Slice 1") adds four stations:
- **World effects** at x 90: snow, ice, packed ice, blue ice, stone, planks and a log, plus an unlit campfire and
  candle. Fire tick is off, so fire does not spread.
- **A twin wall** of ice, planks, stone and packed ice inside the sealed room, for after the vacuum button.
- **The Last Wall** at x 120: held by a NoAI, invulnerable Master Architect in glass, seeded with
  `MasterWallBlocks`, beside a plain twin. A NoAI Master runs no wall upkeep, so breaking its seam by hand does not
  drop the wall here.
- **A crafting bench** at x 105, with ingredient and memo buttons.

Slice 2 (2026-11-03, save "Sever Lash Slice 2") adds a **Beam** station at x 15: six white and light blue concrete
pillars on a ten-block arc, 100 degrees wide, to sweep across slowly and fast. Its guide covers the bend, the
afterimage, the crack and the sounds. It is in air until VACUUM is clicked, and the Room station keeps air for a
side-by-side. The Effects station's guide now describes the 3D model's heat and vent states.

A rebuild button restores every wall, and merges the wall back into the Master without killing it. By the owner's ruling (2026-11-02) this world keeps advancement toasts and chat
on so Hellwhip can be seen; other generated worlds still filter them. Difficulty is Normal and locked.

## Deferred from the slice 1 branch review

- Hurt flash and hurt sound replay on every tick a target is cut, because the cooldown is bypassed. Judge by ear in
  the slice 2 playtest, now that the sizzle plays over it.
- The Lash cuts anything living except the shooter: its own mount, tamed pets, villagers. This is a design call.
- The held stack is rewritten every firing tick; check for a hand re-equip bob in the visual pass. Slice 2 stops the
  bob when only heat changed.
- `use()` checks only the lockout, so a trigger with no cells flickers the use animation.
- A player who logs out mid-beam leaves one stale entry in the aim map. It is harmless.

## Deferred from the slice 1 finish review (Opus, 2026-11-03)

- A scorch posts a block break, so a player-placed stone block loses that status in `PlayerPlacedBlockTracker` when
  it becomes Scorched Ground. Check this if Architect or shelter logic depends on it.
- The glow of a Lash carried from a world with a later clock shows its stored heat until it next fires. The server
  settles it the same way, and the lockout already guards against this.
- The reactor maintenance bay's iron count draws on the builder's shared random, which shifts dead ground, fallout
  and debris for sites built from now on. This is harmless.
- Block heat follows the position, not the block, for up to about 40 ticks after a block is replaced.
- The recipe consumes the Power Cell whatever its charge, so a full cell's energy is lost.

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

## Owner playtest 5, 2026-11-03 (Sever Lash Slice 1, runClient, PR head a31aa458)

First pass on the slice 1 stations. Unprompted report: "all the stations pretty much passed." The owner then asked
for slice 2: the 3D model and sounds, and a beam that "screams hellwhip" instead of particles.

## Owner playtest 6, 2026-11-03 (Sever Lash Slice 2, runClient, branch head e46c87a5)

First pass on slice 2. Unprompted report: "god it looks so good".

Holds, from screenshots: the third-person Lash was "being held on the side of my hand", and "it should be like held in
my fist you know? then when you fire it, you like. hold it in both hands". The owner kept the large first-person view
("i liked how big it was"). After the solved transforms and the two-handed pose: "there we go".

## Verification

- JUnit:
  - `LashHeatTest`, including glow and vent.
  - `BeamSweepTest`.
  - `SearTest`: Seared build-up, cap and levels.
  - `BlockHeatTest`: dwell, sweep, same-tick rays and cooling.
  - `LashSalvageTest`: 1 to 2 Helion emitters, about 1 in 4 reactor bays, about 1 in 4 memos, and determinism.
  - `SeverLashRecipeTest`: ingredients, a cool result, and a unique grid.
  - `SeverLashModelTest`: every state has its model and textures, the overrides are in order with the vent stages
    last, the fins turn in vanilla steps to each stage's angle, the lens and hot parts are fullbright, and firing
    changes only the third-person hold.
  - `WhipBendTest`: a new beam is straight, a sweep trails and a faster one trails further, the cap, the snap with
    and without a crack, the flick cooldown, and the curve from muzzle to far end.
  - `LashHeatTest`: the vent stages through a lockout, in air and vacuum.
- GameTests (`-PfdTests=severLash,seared,helionDiagnostics,helionBench,reactorMaintenance`):
  - Slice 1 core: power draw, arc-only cutting, cooldown, knockback, stale-aim reset, armor bypass, no player armor
    wear, overheat, Hellwhip, and Seared application, build-up, burn and slowdown.
  - Acquisition: crafting through the recipe manager; Helion bench emitters; the memo, which archives with its
    advancement; the reactor maintenance-bay emitter.
  - World effects: snow and ice to steam; packed and blue ice dwell; stone scorch; wood ignition; the Last Wall
    exemption; adventure-mode refusal; ignition refused by a place-event claim on the fire cell; the once-a-second
    refusal memory.
  - Vacuum (batch `sever_lash_vacuum`): nothing burns, and stone still scorches.
- `architectVerify` passed on 09572595 (864 GameTests) and on 435d74e4 (865), after the review fixes.
- Slice 2: `architectVerify` passed on a5666787 (868 GameTests) and on 540ad5e2 (869), after the review fixes. The
  third-person holds changed after that; the gate has not been rerun on them yet.
- Slice 2 GameTests: one beam payload a firing tick, naming the shooter and carrying the aim, length to the wall,
  landing, air and heat; the full reach in open air; nothing while locked; vacuum flagged (batch
  `sever_lash_vacuum`); one start-up a beam; a sizzle every quarter second of contact and none into open air; one vent
  at overheat and no extinguish placeholder; and no re-equip for heat alone.
- Not covered by a test, so these need the owner's visual and listening pass:
  - slice 2: how the beam looks bent and straight, where it leaves the Lash, the afterimage and impact, the model,
    its texture and display transforms in every view, the firing and vent poses, the vent smoke, and every sound,
    including the hum's pitch and how the sizzle sits over the repeated hurt sound;
  - the shield half-block, which needs a player blocking for 5+ ticks;
  - the ember particles and icon;
  - the glow sprites in hand, and whether the fullbright layers read as hot;
  - the steam and smoke puffs;
  - the Lash Emitter icon;
  - the Field Manual page;
  - the playtest Master Architect, which is NoAI and has only been checked in a GameTest.

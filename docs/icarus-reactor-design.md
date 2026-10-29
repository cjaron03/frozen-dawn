# AMR-1 Icarus reactor design

Agreed with the owner on October 28, 2026. Branch `feat/icarus-reactor` in `/Volumes/SSD/Work/frozen-dawn-icarus`, cut from `feat/maeve-director` with `feat/fabricator` (#135) merged in. Until #135 merges, the Icarus PR also shows the Fabricator diff.

This is a clean start. Codex's uncommitted v2 in `~/.codex/worktrees/icarus-reactor-v2` is not a base; its design doc is background only. The [Notion optional spec](https://app.notion.com/p/4027cfaa890181238628da84ae61b473) is the older proposal; where it conflicts with this document, this document wins.

## Intent

Icarus is an optional, post-Solstice prestige reactor modelled on the Draconic Evolution reactor: a giant glowing sphere held in a shield by stabilizers, which you have to babysit and which can erase the landscape. It is the biggest power source in the mod and the most dangerous object in it. It is never required for Exodus, Maeve, any ending or any progression item.

The owner wants:

- something that looks and sounds alive in every state, from parked and cold to nearly exploding;
- a reactor you check on and retune, not one you stand next to;
- real net power, paid for with very expensive antimatter;
- an explosion that is genuinely extreme.

## Anti-goals (from the Codex attempts)

The owner rejected an earlier build for these, so treat each as a failure even if the code works:

- Silence. Every Icarus sound must be audible in vacuum.
- Static models. Each state needs real motion.
- An observation window or enclosed vessel. Icarus is an open structure.
- Any block reusing another block's texture. Every Icarus block gets its own art.
- Breaking a block while it runs without an explosion.
- A shutdown that is just "output off".

## Names

- **ORSA AMR-1 "Icarus"**, Antimatter Reactor Mark 1.
- **Dr. Helena Halsey**, fictional ORSA physicist (a nod to Halo's Dr. Halsey; do not use "Catherine").
- **Halsey Cascade**: the reaction. A small antimatter trickle triggers annihilation through a much larger stream of ordinary matter. Antimatter is the spark; ordinary matter is the bulk fuel. Readouts: CASCADE STABLE, CASCADE STRAINED, CASCADE RUNAWAY.
- **CLIP**: Cascade Lockout, Injection Purge. The emergency stop.
- **Cole Protocol**: the slow automatic safe shutdown (Halo nod).
- **Halsey Core**: the heart block that holds the sphere.

## Look and structure

Layout A from the mockup, a Draconic-style cross:

- The **Halsey Core** at the centre renders a sphere about 5 blocks across inside a shield shell about 9 blocks across. The sphere and shield are rendered, not placed blocks.
- **Four stabilizers** sit in a horizontal ring at core height, one per side, roughly 6 to 8 blocks out from the core. Each is a large multi-block machine that fires a beam into the shield.
- **One injector** sits under the core and feeds ordinary matter upward.
- A cheap **frame** holds it together: supports, base ring and gantry.
- Supporting blocks attached to the frame: controller, reserve bank, power port, matter input port, capsule dock.
- Footprint about 19 x 19 x 13. Exact geometry is the builder's.

Core colour follows temperature:

| State | Core | Shield |
|---|---|---|
| Cold | dark, still | off or faint |
| Running | orange, churning | cyan |
| Hot | deep red, faster churn | amber |
| Critical | red with white flicker | red, visible cracks, arcs from core to shield, structure shakes |

Every visual follows the real simulation state. Churn speed and pulse rise with output and heat. Stabilizers have moving parts (rings, emitters) that park when cold and spin when active. Startup, CLIP and Cole Protocol each have their own motion (see below). Provide a reduced-flash client setting.

## Building it

- Uses the Solstice ghost-block guide (`SfrBlueprintRenderer`): translucent ghosts for every block, ordered stages, remaining-material counts. Ghosts hide once placed.
- Cheap: frame blocks, normal crafting from materials the player makes in bulk.
- Moderate: stabilizers and injector.
- Expensive: the Halsey Core, which needs Solstice-tier parts and helium, and the antimatter.
- The Halsey Core, stabilizers, injector and Antiparticle Foundry are Fabricator blueprints, unlocked by a Project Icarus Data Slate found the same ways as existing slates (Helion sites, Uplink drops). The frame stays an ordinary recipe.
- Fully survival-buildable. No creative-only parts.

## Fuel

- **Antiparticle Foundry:** a separate machine drawing 400 kW. It makes one sealed antimatter capsule per real hour while loaded (1.44 GJ per capsule). More foundries make capsules faster if the grid can feed them.
- Rare sealed pre-collapse ORSA capsules can appear at Helion sites as lucky finds. They are never needed.
- **Ordinary matter** is any plain block item: cobblestone, dirt and so on. At full output it eats about one block every couple of seconds, less at lower settings. Hoppers or storage can feed the matter port.
- The reactor holds up to 64 capsules. Startup needs at least 2.
- Capsules loaded into the controller transfer into the core during startup step 4 and stay there as the core charge. Only core charge counts for detonation. Sealed capsules outside the core, including ones in the dock after a Cole Protocol, are inert and safe to carry. (Agent's call.)
- The controller shows the blast radius for the current core charge, so loading more is a visible choice.
- Antimatter is tracked exactly. It is used according to actual energy produced, never according to elapsed time.
- If the matter feed runs dry, the cascade starves and output falls, with a warning. No antimatter is lost. (Agent's call.)
- When the core charge is used up, the cascade stops, the shield powers down and the reactor shows SAFE TO DISMANTLE.

## Power

Targets for the first tuning pass. Playtests will move them.

| | Gross | Shield draw | Net to grid |
|---|---|---|---|
| Minimum (default setting) | ~1 MW | ~400 kW | ~600 kW |
| Full | ~2.5 MW | ~1.3 MW | ~1.2 MW |
| Startup | none | about 1 MW from outside during the reserve and stabilizer steps | needs a Solstice and batteries |

Return on antimatter: at Minimum a capsule lasts about 2 hours and returns about 3x the energy the foundry spent on it. Running harder wastes antimatter: at full output a capsule lasts about 30 minutes and returns about 1.5x. Icarus must always be a net source over a capsule's life, and must never be cheap.

For scale: Solstice is 400 kW thermal with a 40 kW house load.

## Running it

Controls are manual apart from the default Minimum setting:

- output target;
- shield strength: higher costs more power and gives more margin; lower saves power and leaves less room for error;
- matter feed rate.

The loop is Draconic-like: more output means a hotter core, a hotter core drains the shield faster, and a weaker shield needs more power. Above a red line the shield drain climbs steeply.

It drifts, so a stable setting does not stay stable forever. As antimatter is used up the core gets more reactive and runs hotter at the same settings. Changes elsewhere on the grid also change what the shield receives. The player comes back now and then to retune it.

**Warnings ahead of time.** The controller shows predictions, not just current values: time until shield failure at the current rate, time until temperature reaches red, time until the reserve bank runs out, and antimatter remaining. Alarm tiers escalate with lights and klaxons, nonverbal as the mod's rules require. While the reactor is loaded, the EVA suit relays alarms to the player wherever they are, in the existing suit voice (see the suit TTS tools in `tools/`).

**Unloaded means paused.** When the reactor's chunks unload, everything freezes: no drift, no fuel use, no failure. Save, reload and restart never cause a failure on their own.

## Startup

Automatic once Start is pressed. If infrastructure is short, it holds on the current step and says why, instead of failing.

| Step | Time | What happens |
|---|---|---|
| 1. Power bus check | 10 s | Controller lights come up, frame hums |
| 2. Reserve bank charge | 20 s | Draws about 1 MW, reserve gauges fill |
| 3. Stabilizer spin-up | 30 s | Stabilizers light one at a time, beams meet, shield forms |
| 4. Antimatter injection | 20 s | A capsule docks, a violet speck appears at the centre |
| 5. Matter priming | 15 s | Injector starts feeding |
| 6. Halsey Cascade ignition | 5 s | Flash, core blooms orange |
| 7. Ramp to Minimum | 20 s | Output climbs to the Minimum setting |

- Cold start: about 2 minutes.
- First ever boot: adds a one-time 1-minute commissioning step where the stabilizers calibrate.
- Restart after a CLIP: about 40 seconds, from step 4.

## Stopping

- **CLIP:** a held button on the controller, or a redstone signal. It cuts the matter feed, so the cascade has nothing to burn: output reaches zero within seconds and the core cools. The antimatter stays in the shield, which still needs power. Each CLIP loses some antimatter, roughly 10% of a capsule. There is no automatic CLIP; the owner chose manual only.
- **Cole Protocol:** the automatic safe shutdown, about 3 minutes. It ramps down, cools, pulls the remaining antimatter back into capsules in the dock, then powers the shield down. Only then does the controller show SAFE TO DISMANTLE. With no empty capsule space, it holds and says so.
- **Reserve bank:** an independent backup that holds the shield for about 10 minutes at the current shield draw if the grid fails. It charges during startup and does not draw from the network while it is backing it up.
- A CLIP with less than the loss amount left uses up what remains, which ends the run safely.

## Failure

- Breaking **any** reactor block while the core holds antimatter detonates instantly, whatever the state (starting, running, CLIPped, mid-Cole Protocol). No countdown.
- The shield reaching 0 with antimatter inside also detonates instantly.
- With no antimatter inside, breaking blocks just invalidates the structure. An empty reactor cannot explode.
- Draconic comparison, from its current source: its only automatic safety is a fail-safe that shuts down an idling reactor; once the shield fails, a 1 to 4 minute countdown cannot be stopped. Icarus differs deliberately: CLIP gives a real emergency stop, and failure is instant.

## Explosion

Size scales with antimatter inside: radius = 400 x cube root(capsules / 64). A full 64 capsules gives 400 blocks; a minimum 2-capsule load gives about 125.

**Terrain:**

- Everything breaks except bedrock, including obsidian, mod blocks and player builds.
- The crater is deep at the centre and shallower toward a ragged edge, at least several blocks deep even at the rim. Exact profile is the builder's, roughly radius / 5 deep at the centre.
- No block drops.
- Snow and ice melt in a wider ring beyond the crater, about 1.5x the radius. The crater floor stays hot for a while.
- A short radiation burst through the existing radiation system, with no lingering fallout.
- Process the destruction across ticks with a work budget. It must survive save and reload and resume. Unloaded chunks are processed when they load.

Air or vacuum is decided per player by the existing atmosphere at their position.

**With air:** a blinding white flash from the blast direction, visible far beyond the crater. The boom arrives after a distance-based delay at the speed of sound (about 343 blocks per second), followed by a long rumble. A shockwave pushes and shakes, strong near the edge and fading with distance. A rising fireball with a smoke and debris column.

**In vacuum:** the same flash. No boom. Ground shock comes through the suit as vibration, a muffled thud in the helmet and a low rumble. No fireball: an expanding glowing plasma and debris cloud. The suit voice warns of the radiation spike.

Entities inside the radius die. Outside it, damage and knockback fade with distance.

## Sound

Original sounds for every state and event, all audible in vacuum. Follow the Solstice exception in `SoundMuffler` (prefix `blocks.icarus_`), keeping the normal volume sliders and distance falloff.

Minimum set: frame hum, stabilizer spin-up and whine, shield hum (shifting with strain), core roar (pitch and intensity rising with heat), injector feed, capsule dock, ignition, CLIP slam, Cole Protocol completion, alarm tiers, explosion (air boom and rumble; vacuum suit thud). Document sources in `AUDIO_NOTICE.md` as for other sounds.

## Advancements

All hidden and optional. Builder may refine the list; avoid rewards for griefing other players.

- E = mc²: first Halsey Cascade ignition.
- Too Close to the Sun: reach full output.
- Clipped Wings: first CLIP.
- Wake Me When You Need Me: restart Icarus after a full Cole Protocol shutdown.
- Continuity Beyond: run Geothermal, Hearthstone, Solstice and Icarus on one grid.
- The Fall of Icarus: first detonation.
- Don't Look Up: witness a detonation from outside the blast radius.

## Builder's calls

Left to the builder, to settle sensibly and report:

- exact block geometry, recipes and material counts;
- simulation formulas, as long as they hit the power and capsule targets above;
- animation and sound design detail;
- foundry recipe and data slate rarity;
- multiplayer config, for example a server toggle for terrain destruction, defaulting on.

## Open check

There is a generated blast-pit site (`BlastPitPlacement`). If any world-generated block that the story needs turns out impossible to rebuild after a crater, stop and ask the owner. Do not silently protect it.

## Verification

- Pure tests for the simulation: fuel conservation, net-positive return per capsule, startup holds, CLIP, Cole Protocol recovery, reserve independence, pause when unloaded, and no failure from save or reload.
- GameTests for structure validation, instant detonation on block break with fuel, safe break when empty, and bounded crater processing at small scale.
- Run the full `architectVerify` before handing back.
- Owner visual and audio pass in a disposable generated world: every state's animation, vacuum audibility, startup and shutdown readability, ghost-guide clarity, and a full-size detonation. Never test detonation in an ordinary save.
- Measure full-radius crater performance separately; small-scale tests do not prove it.

## Status

Recorded 2026-10-28 on branch `feat/icarus-reactor`, before the owner's visual pass.

### Full-radius crater performance

One 64-capsule detonation (radius 400, 4596 chunks in reach) at the origin of a disposable normal-terrain world, timed by the lab-only `LabIcarusPerf` from the blast until no loaded chunk was left to carve. The crater budget is 10 ms a tick. Unloaded chunks stay pending and carve when they load, so each run counts loaded chunks only. Evidence is under `build/icarus-evidence/perf-run1` to `perf-run4`.

| Run | Change before it | Ticks to carve loaded | Pending unloaded | Worst whole tick | Worst crater phase | Peak heap |
|---|---|---|---|---|---|---|
| 1 | none | 732 | 3719 | 16 536 ms | not measured | 3185 MB |
| 2 | carve only chunks in block-ticking range | 707 | 3831 | 177 ms | not measured | 2711 MB |
| 3 | crater phase timed on its own | 665 | 3831 | 190 ms | 181 ms | 2802 MB |
| 4 | carve a column at a time | 1198 | 3831 | 762 ms | 44 ms | 1472 MB |

- Run 1: a chunk can still be cached while the server unloads it. Reading it blocked the tick until it loaded again (16.5 s). Fix: `IcarusCrater.carvable` also requires the block-ticking range.
- Run 3: the budget was checked only between chunks, and one deep chunk near the centre took about 180 ms. Fix: carving is per column, with the budget checked after each one. A part-carved chunk keeps a saved column cursor, because carving a column twice would cut deeper from the lowered surface.
- Run 4: the crater phase peaked at 44 ms, under the 50 ms limit. That figure is an upper bound, since it brackets every normal-priority level post-tick handler. The slow whole ticks happened outside the crater phase. Stack samples put them in existing code waiting on fresh-terrain chunk loads (`FrozenTownRuntime` town scans, `RocketLaunchManager.diagnosePad`, block entity ticks). Ticks of 600 ms or more also appeared before any blast in run 2.
- `detonate()` itself took 15 to 44 ms.

### Open check result

The crater clears world-generated blocks that are never rebuilt, and no protection was added. Affected sites: the satellite's black-box chest (it holds the only Transponder Schematic, which unlocks the win), the ORSA Blast Pit crate, and the lower block of the First Crossing memory wall. This is reported to the owner for a decision.

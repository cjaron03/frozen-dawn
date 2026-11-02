# SMS-1 Hyperion silo: redesign of the structure and look

Status: approved by the owner, 2026-11-02. Branch `feat/hyperion-storage`. This document replaces the **Structure** and **Look** sections of [hyperion-storage-design.md](hyperion-storage-design.md) and the parts of the shipped implementation that follow from them. Everything else in that design stays as approved and implemented: power and rate, cold and cooler draw, self-discharge, the quench rules, the audio ladder and the Controller screen.

## Why

After the first owner pass the mechanics were right but the owner did not like the look: a frosted charcoal cube with a puck in a small window. The one part they liked was the steam venting on a quench. What they want instead is a tall silo with a fill display that reads from across the base and with "cool animations still". They pointed at Draconic Evolution's Energy Core as the feel to aim for: once built, it stops reading as a pile of blocks and becomes one glowing, moving object whose motion follows what it is doing.

## What it is

A black silo, 3 blocks wide and 4 to 8 blocks tall, built from the same superconducting cells. A window of stacked blue bars runs up the front and lights from the bottom as it fills. Frosted cryo pipes come out of the base and bend into the floor, shedding cold vapour. Thin dark rings float around the silo, bobbing and spinning. The quench vent sits on top. The bars, pipes, lamps, rings and vent all change with what the battery is doing, so its state reads without opening the screen.

## Goals

- It reads as one machine, not a block pile, once formed.
- Fill level reads at a glance from across the base, from the front.
- Every state is visible from outside: cold and full, charging, discharging, overheating, quench, and recovering after a quench.
- It fits Minecraft's pixel look: 16 pixels per block, blocky geometry, no smooth or rounded hulls.
- The steam venting on a quench stays as it is now.

## Non-goals

- Any change to the mechanics beyond what the new shape forces (see Structure). Power, rate, cooling, warnings, quench causes and the screen stay.
- A wider silo or other footprints. One shape only: 3x3.
- Unlimited or very large storage. A second silo is the way to store more.
- Survival recipes (still a later slice, as before).

## Anti-goals

- Rings that look chunky or out of place. The first ring mockups were rejected for this: much wider than the silo, thick, pale steel against the black, drawn smooth. Rings stay slim, close to the silo, dark, and mostly seen through their glow.
- A copy of the Energy Core's look (a glowing orb with stabilizers). Borrow the feel only.
- A rounded or smooth hull that fights the block grid.
- Everything from the original anti-goals that still applies: no heat quench without warning, no outage wiping charge, no voice-like sounds, bounded per-tick work.

## Decisions

Marked **owner** (stated or chose it), **agreed** (proposed by the agent and accepted by the owner) or **agent's call** (the owner can still change it). Throwaway mockups are in [docs/art/hyperion-storage/silo/](art/hyperion-storage/silo/).

### Shape and size

- **A silo, about 5 or 6 tall, of the black cell material (owner).**
- **Footprint 3x3, height 4 to 8 blocks (agreed).** Height is the only size choice. Taller holds more.
- **One shape only, topping out around 1.4 GJ (agreed).** The owner asked whether 64-bit "unlimited" storage was needed; agreed it is not. Booting an Icarus takes about 70 MJ and a full Minecraft night of Icarus output is about 0.7 GJ.
- **No Frame blocks (agreed).** Every position that is not a fitting is a cell, including the corners. The Hyperion Frame block is removed from the structure (whether the block itself is deleted or kept as a decoration is the builder's call; nothing should still require it).
- **Capacity scales with cell count (unchanged).** At about 20 MJ per cell: a 3x3x4 with 4 fittings holds about 640 MJ, a 3x3x8 about 1.4 GJ.

### Fittings

- **Controller (agreed):** exactly one, at the bottom of the front-centre column. Its facing defines the front. Right-click opens the existing screen.
- **Gauge column (agreed):** the front-centre column above the Controller must be cells. The window of bars is drawn over it.
- **Quench Vent (agreed):** exactly one, at the top-centre position, jetting steam straight up. This replaces "exactly 2".
- **Cryocoolers (agreed):** at least one, in the bottom row, on the sides or back. Each sprouts a frosted pipe from its outward face that bends into the floor, so more coolers means more pipes. A bottom front corner counts as side (agent's call): any outer bottom-row position except the Controller's.
- **Ports (agreed):** at least one, on the outside at any height, never in the front-centre column or at the top-centre vent position (agent's call: the front corner columns are allowed).
- **Interior (agent's call):** the centre column, below the vent, is always cells.
- **Problem messages (agent's call):** the screen's "why it isn't formed" text follows the new rules, for example "needs a Quench Vent at top centre" or "too tall (8 max)".
- **Existing cubes (agent's call):** no migration. The branch is unmerged and creative-only, so a cube simply stops forming; the lab world is regenerated.
- **Cooler sizing (agent's call; tune in playtest):** one Cryocooler holds a 3x3x4 in a 20C room; a 3x3x8 in a 20C room needs two; outdoors below 0C one holds any height. These replace the cube-based targets; the formulas stay, only their tuning changes.

### Look

Reference mockups: `silo-states.html` (the five moments), `silo-extras.html` (height, scorch, cold breath) and `ring-pixel.html` with its GIFs (the chosen ring, option 3).

- **Seams vanish once formed (agreed, from the owner's Energy Core reference).** A formed silo shows no block grid inside its outline; only its outer edges get a border. An unformed or loose cell keeps a normal block look so a half-built silo still reads as blocks.
- **Cells are black (owner).** The old frost, glowing cracks and scars on the cell texture are dropped; the state now shows on the bars, pipes, lamps, rings and vent (agent's call).
- **Fill window (owner chose stacked bars, "C"):** short horizontal blue bars stacked up the front-centre column, one bar per block of height including the Controller's block, lighting from the bottom. The top lit bar shows the partial block. Inside the window a column of blue charge swirls upward (agreed, the blocky half of idea A); dark rungs between blocks keep the stacked-bar read.
- **Frosted cryo pipes at the base (owner chose option 2):** thick, frost-caked pipes out of each Cryocooler, bending into the floor. Cold vapour rolls off them and sinks. A ring of small status lamps runs round the base.
- **Rings (owner chose option 3, "thin dark rings"; owner: "really make the rings look good"):** slim round rings of black metal with a glowing inner edge, a few pixels out from the silo, bobbing out of step with each other. One ring per two blocks of height (agreed): 2 on a 4-tall, 4 on an 8-tall. Built from small blocky boxes at 16 pixels per block, not smooth geometry.
- **Rings as a charge cue (agent's call):** an empty silo's rings rest on the base; they lift into place once it holds charge, like the puck did.
- **Idle cold breath (owner kept):** every few seconds while cold and healthy, a small frosty puff curls out of the vent.
- **Scorched after a quench (owner kept):** burnt streaks run down from the vent and the window, the bars sit dead grey with a faint ember on the bottom one, and the pipes stay bare, until the silo is cold again.
- **Dropped (owner):** frost spreading on the floor; the levitating puck and its window; the cube; the swirling charge column in the window (owner pass 2, 2026-11-02: it read as a busy checker behind the bars).

### What each state shows

| State | Bars | Pipes and lamps | Rings | Vent |
| --- | --- | --- | --- | --- |
| Cold, holding charge | lit to the fill level, slow shimmer | frosted, vapour sinking; lamps steady blue | slow bob and spin | idle cold breath |
| Charging | next bar breathes on and off | blue pulses flow in through the pipes | faster spin, brighter edge | as cold |
| Discharging | top lit bar flickers as it drains | pulses flow out | spin by flow | as cold |
| Overheating (chirp and warning) | amber, blinking | frost melts and drips; lamps amber, blinking | wobble and tilt | wisps rise |
| Final seconds | red, blinking fast | lamps red | shudder | wisps thicken |
| Quench | flash red and collapse top to bottom | frost gone | drop and clang onto the base | steam blast, as now |
| Warm after a quench | dead grey, ember on the bottom bar; scorch streaks | bare | resting on the base | none |
| Warm, never quenched (fresh build) | dark | bare until cold | resting | none |

"Charging" and "Discharging" overlay whichever temperature state is current (agent's call). Light: bars, ring edges and lamps glow at full brightness (agent's call).

### Audio

The ladder is unchanged. The puck-drop clunk becomes a heavier ring clang (agent's call; a new procedural cue or the puck drop pitched down). The vent steam is positioned at the single top vent.

## Constraints

Unchanged from the original design: NeoForge 1.21.1, Java 21, Gradle; `long` joules; the charge on the Controller; bounded work and loaded-only temperature queries; pause while any part is unloaded; save and reload resume without a new quench. A 3x3x8 is small enough that straddling a chunk border is the only unload case.

Rendering beyond the Controller's own block (rings, window and pipes) needs a render bounding box that covers the whole silo plus the pipes and rings.

## Verification

- GameTests updated for the silo: heights 4 to 8 valid, 3 and 9 invalid; wrong footprints invalid; exactly one Controller in the front-centre bottom; exactly one Vent at top centre; Cryocoolers only in the bottom row off the front; Ports never in the front column; capacity by cell count; existing quench, recovery, break and load-follow tests kept on a silo.
- The lab world builds silos (for example a 3x3x6 outdoors and a 3x3x4 in the heated room) in place of the cubes.
- Owner visual pass: whether the seams vanish, the bars read from across the base, the rings look good up close and far away, and each state in the table reads correctly. Headless tests cannot judge any of that.

## Left to the builder

- How "seams vanish" is done (blockstate edge properties, a formed-model swap, or drawing over the cells) as long as an unformed cell still looks like a block.
- Exact ring geometry, spacing, bob and spin rates, and the drop animation, within the anti-goal above.
- Pipe routing into the floor when the ground is uneven or blocked. Pipes and rings are rendered only: no collision, not blocks.
- Particle counts, swirl speed, colours, and how the partial top bar is drawn.
- Whether the Frame block is deleted or kept, and what happens to cells' old look states.

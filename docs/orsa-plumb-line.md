# ORSA GPR-4 Plumb Line

Source: [Notion ORSA GPR-4 Plumb Line](https://app.notion.com/p/3fa7cfaa89018140a108eb49a22e7924) (Design, proposed 2026-10-14), fetched 2026-10-17. Branch `feat/gpr4-plumb-line`, cut from `feat/orsa-uplink` at `43adc96c`.

## Owner decisions, 2026-10-17

- **No Maeve hook.** A pulse is heard by Blind Architects only, through their existing air hearing and ground vibration. It adds no MACS evidence and no Uplink signal pressure.
- **10-minute triangulation memory**, held in memory per world (not saved).
- **Original sounds**, synthesized in code. See [tools/audio_sources/plumb_line/SOURCES.md](../tools/audio_sources/plumb_line/SOURCES.md).

## Block and power

`frozendawn:plumb_line` is a single, crafted block. Recipe: iron, a compass, two redstone blocks, an amethyst shard and an iron block. It is a Binder node with range 16, and the Power Monitor names it when shed. Opening it shows Survey/Deep, Target and Pulse, the last pulse's finds and a legend.

| Setting | Draw | Pulse | Radius |
| --- | --- | --- | --- |
| Survey | 30 kW | 10 s | 48 |
| Deep | 50 kW | 10 s | 80 |

A pulse starts only if generation plus stored energy covers the full draw (the same one-second budget the Uplink uses), and it draws only while pulsing. If it isn't fully served at any one-second check, it aborts with no scan. Completion or abort starts the 60 s recharge. A pulse in flight is not saved; reloading ends it.

## Scan

Rays spread evenly over a downward cone, from about 11 degrees above level to straight down (9,000 for Survey, 16,000 for Deep). They march out from the scanner in bounded slices across the 10 s pulse. They read only chunks whose data is already present ([`ReadyChunks`](../src/main/java/com/frozendawn/world/ReadyChunks.java)) and never load or generate one. Each block a ray crosses spends range:

- **Stone** is the baseline.
- **Deepslate** allows 75% range.
- **Ice, snow and frozen atmosphere** allow 150%.
- **Frozen ground:** any depth below 0C in the ground temperature model adds 25%.
- **Water** stops the ray.
- **Metal reflects:** iron, gold and copper blocks, lead, reactor casing and supply crates show as bright returns.

Because rays spread apart with distance, far targets are sampled more coarsely; together with the padding, that is the blur.

Results:
- **Ore veins, by exact block** (vanilla and Frozen Dawn, from the ore tag or `_ore` names). Ore the player can already see is skipped: the top 3 blocks of a column, or any ore with a face open to the sky. Only ore matching the **Target** is reported: All, Metals (iron, copper, gold, galena), Energy (coal, uranium) or Minerals (everything else). Veins under 3 blocks are dropped unless rare (uranium, diamond, emerald, ancient debris), and at most 24 are kept, most valuable then nearest. Shown as corner brackets in the ore's colour around an area padded 2 blocks plus extra for distance, rock crossed, and +1 on Deep, nudged off-centre deterministically. An estimate centred inside a nearer one of the same ore is dropped.
- **Voids:** connected cave air below the surface, 24 cells or more, as faint blue corner brackets; the nearest 6, on the All target only.
- **Metal:** bright white boxes; the nearest 8.
- **ORSA structures:** cyan markers for monitoring stations, reactor sites and cargo drops within the radius, from the world's structure records. These help narrow an Uplink Signal Plot.

No entities are shown. At most 96 returns, nearest first.

**Triangulation:** each vein's exact bounds are remembered for 10 minutes. A later pulse from a scanner at least 8 blocks away that hits an overlapping vein of the same type shows it as an exact box in its colour instead of brackets.

## Overlay

When the pulse completes, players within 16 blocks of the scanner receive the returns. They're drawn through terrain for 30 s, fading in over half a second and out over the last five. Returns dim with distance. The nearest 4 within 96 blocks and the one under the crosshair are labelled with where to dig from the player, such as "~ Galena (lead) · 24 down · 15 m NE"; "~" marks an untriangulated estimate.

The screen lists the last pulse's finds by ore, most valuable first, with a count and the nearest one's direction from where the player stands. Its legend shows each shape (estimated area, pinned vein, metal, ORSA site, cave) and the colour of each ore for the chosen target.

## Survey Sheet

The screen's **Print** button puts the last completed pulse on a `frozendawn:plumb_line_survey` sheet, so a player can keep it while digging down. Each copy costs 1 paper and 1 coal or charcoal (creative is free). Only the scanner's latest pulse can be printed, and that data clears with the next pulse or a reload.

Held in either hand, a sheet draws its returns exactly as the overlay does, but only within the pulse's radius plus 16 blocks of the scanner and in the same dimension. It is a snapshot that never updates, so estimates stay blurred until triangulated by later pulses. The ink fades one in-game day (20 minutes) after printing; a faded sheet draws nothing. The tooltip gives the pulse position and radius, the ink time left, and the ore found by type.

## Sound and hearing

- **Start:** `pulse` (a discharge crack and sub-bass ground thump, volume 2 Survey / 3 Deep) plus `scan` (10 s of receiver hum).
- **Completion:** `complete`. **Abort:** `scan` is stopped for players within 48 blocks and `abort` plays.
- **Blind Architects:** the start emits air hearing at 64 blocks (Deep 96) and a machine ground-vibration impulse at strength 40 (Deep 60).

## Verification

- Unit: `PlumbLinePolicyTest` (10 cases): Survey Sheet fading, range and save round trip; settings, penetration factors, blur, downward ray cone, compass, ore types and targets, baseline, clustering, overlap.
- GameTests (required): typed coal vein found while a water-sealed diamond stays hidden, plus a metal return; the Metals target keeping iron while dropping coal and caves (the surface skip needs a real world: test areas have a barrier roof); triangulation needs the 8-block baseline and tightens to the vein's box; payload round trip; Deep pulse refused without power, then drawing 50 kW, completing, recharging and refusing while recharging; printing refused without materials, then a sheet carrying the Deep pulse for 1 paper and 1 charcoal.
- Gate on 2026-10-17: all 622 required GameTests passed, with the readability changes and the Survey Sheet.
- **Human visual pass pending:** overlay readability, labels, sounds in game.

## Deviations and open items

- Camps and towers aren't marked as structures; only monitoring stations, reactor sites and cargo drops have exact recorded centres.
- Blind Architect hearing uses the existing generic machine and air paths. There's no special "repeated scans near its depth" weighting beyond what repeated pulses naturally provide.

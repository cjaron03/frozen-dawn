# ORSA TR-0 Terminus (the Deathwand)

Spec: Notion, Frozen Dawn / Backlog / "ORSA TR-0 Terminus (the Deathwand)" (drafted and decided 2026-11-03). This file
is the tracked handoff; the spec stays on Notion.

## Checkout

- Worktree `/Volumes/SSD/Work/frozen-dawn-terminus`, branch `feat/terminus`, cut 2026-11-03 from `feat/sever-lash-2`
  at bd5d048a (PR #145, stacked on #143). A PR for this branch is based on `feat/sever-lash-2` while #145 is open.
- Scheduling: the owner inserted the Terminus after Sever Lash slice 2 and before the Proximity Tracker (2026-11-03).
  The Notion page still says "after the Sweep Turret"; the owner's later word wins.

## Slices (proposed, awaiting the owner)

| # | Slice | Status |
| --- | --- | --- |
| T1 | The wand itself, creative tab and `/give` only | Proposed |
| T2 | The Terminus Vault at the first Helion site, the Unwatched Door puzzle, the vault documents | Not started |
| T3 | Maeve, Hearth and Aggregate interplay, through the shared Maeve slice | Not started (MACS is WIP) |

### T1: the wand (creative only)

- `TerminusItem`: stack 1, fireproof, cannot be destroyed or despawn. Creative tab and `/give`; no recipe, loot or
  world generation. A creative wand comes as found: 3 discharges and `Transfers logged: 1`.
- Discharge: hold use 2 s (the RadAidItem hold pattern). While charging, movement at 40%, a low rising tone heard to
  24 blocks, a pale glow at the emitter. Any damage during the wind-up cancels it and still spends the discharge.
- Cone: 12 blocks, 30 degrees total, from the eyes along the look; one AABB query on discharge, filtered by angle and
  the `frozendawn:terminus_susceptible` entity tag. Through blocks and armor, no line of sight needed.
- Effects per the spec table: susceptible minds stopped; post-Maeve evolutions, animals, Undone, Heart, Successor and
  Aggregate untouched; other bosses take 40% of max health, never a kill; the Master Architect is immune and turns its
  head to look at the wand, then resumes; the operator is never affected, other players in the cone are.
- Before the Heart falls, targets are discarded with no drops or XP, but the death event still fires. After CH-18 they
  die normally, credited to the operator.
- Cost: each discharge removes 4 max HP for good (player attachment, survives death and respawn), and 10 s of Darkness
  and Nausea.
- Presentation: no beam and no impact sound; one frame of the view desaturating, then silence.
- Tooltip: `Discharges remaining: N`, `Transfers logged: N` (becomes `Terminations logged: N` once, on the CH-18
  trigger; never in E11 worlds), `Operator exempt. Protocol active.`, `NOT FOR USE IN OCCUPIED HABITATS.` At 0 the
  wand is an inert trophy.
- Hidden advancement "Deathwand", "Nothing burned.", on discharge.
- Look: an item model mockup at Minecraft scale (16 px per block), approved by the owner before the model is built.
- GameTests from the spec's list that apply without the vault: cone hits and misses, through walls, the immunity
  table, the Master's look, operator unharmed, max health and respawn, interrupt spends, drops before and after the
  Heart, the label swap once and never in E11.
- Playtest: `tools/prepare_terminus_playtest.py`, one station per behavior.

### T2 and T3

As the spec, sections 5, 5a, 6, 8 and 9. Open: the tale logs (count and voices).

## Owner rulings

- 2026-11-03, texture: the owner asked for a really unique texture and picked **C, Reliquary** from
  `docs/art/terminus/texture-directions.png` (`make_texture_directions.py`): a caged glass shaft holding one pale mote
  per discharge left, drifting; the glass frosts on wind-up; spent is empty glass. Black knurled grip, steel collar and
  rim, a flat pale emitter face, no lens. The geometry is the one drawn there, at 16 units to the block.
- 2026-11-03, animation: the owner asked for really unique, cool animations, briefly for a beam, then withdrew the
  beam ("nvm"). The discharge keeps the spec: no beam, no impact sound, one grey frame. Approved as drawn in
  `docs/art/terminus/discharge-mockup.png` (`make_discharge_mockup.py`): during the wind-up the wand's motes slide to
  the emitter, the glass frosts, and pale specks are drawn in toward the emitter from inside the cone, thickest along
  its edge; on discharge the view goes grey for a frame; each stopped target lets a pale mote lift from its head, hang,
  and wink out.

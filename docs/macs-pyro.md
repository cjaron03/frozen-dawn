# MACS Pyro Architect

Implemented on `feat/pyro-architect`, branched from `feat/maeve-director` at `bd894d50`. Contract: [MACS Source of Truth](https://www.notion.so/39d7cfaa890181c1bad4f6babad80880), §9.4a, with §9.13a for self-defense. **Automated gates only; no human visual pass yet.**

## Owner decisions (2026-10-22)

- **Appearance like the Scribe:** a natural Architect spawn is designated as the Pyro. There is no separate spawn path, so existing population caps, Hearth exclusions and spawn rules apply. A spawn that would be a Scribe stays a Scribe.
- **Proposed defaults accepted:** thermal sight 64 blocks, a 5 in-game day cooldown, orange eyes, a hand-to-chin stare, and a hidden advancement *Covered in Mud* ("Cold rock. Nothing to see here.").

## Behavior

**Gate.** `ArchitectSpawner` asks `MaeveDirector.designatePyro` for each natural spawn that did not become a Scribe, with the player the spawn was placed near. Designation needs all of these:

- Maeve is ACTIVE and Architect existence has not ended (E11).
- Maeve suspects a home: the player's world model has a shelter centroid in this dimension.
- She knows of no heat there yet: no HEAT_SOURCE at or above the commitment threshold within 32 blocks of that centroid.
- No Pyro is active, and 5 in-game days have passed since the last one ended.

Masters, mind copies, Scribes, convergence-assigned and NoAI actors, and Creative or Spectator subjects never participate. One claim exists server-wide. It lasts at most one in-game day, so a lost Pyro cannot block the next one. The claim lives in `MaeveSavedData` under `pyro`; saves without it load empty, and the store version is unchanged. Rarity comes from the gate and the cooldown: once a player's home is warm in Maeve's memory, no Pyro is sent there.

**Local executor** (`ArchitectPyroController`):

- **Travel.** It walks to a post on a 40-block ring around the suspected home. A post must have open sky, be standable and loaded, and have no known heater within 12 blocks or heat block within 4. Posts with line of sight to the home are preferred, then the nearest. It never closes on heat. Stalling 100 ticks on the way means it watches from where it is.
- **Watch.** It stands still, faces the home and holds the hand-to-chin stare. Its first look comes after 200 ticks, which is the window to stop it before it learns anything. It then looks every 100 ticks until 1200 ticks have passed, and leaves.
- **Depart.** It walks away from the home and is discarded once no player is within 32 blocks (or 16 after 600 ticks).
- **Defense (§9.13a).** Struck by anything living, it fights that attacker until 200 ticks pass without damage, then departs. It never initiates combat.
- **Log.** Transitions log as `[MACS Pyro] <event>` (DESIGNATED, WATCH with the post and rejected ring columns, LOOK, LOOKED or COLD, DEFEND, DEFENSE_ENDED, DEPART, GONE, ENDED), so a lab pass needs no decision recording.
- **Appearance.** Orange eyes, the same 2x3-pixel recolor as the scout's purple and the Scribe's white. The stare is the right forearm raised to the chin, head slightly bowed. It is presentation only, from a synced flag set during the watch.

**Thermal sight** (`ThermalSight`, read-only). Each look reads the 32 nearest heat sources within 24 blocks horizontally and 16 vertically of the suspected home. A look never loads a chunk: if any chunk between the eyes and that area is unloaded, nothing is read.

- **What counts as heat:** whatever the thermal model counts. That is a lit thermal or resistive heater, a venting or radiating heat vent, lava, fire, magma, a lit campfire, furnace, smoker or blast furnace, and geothermal cores. Decoys fool it by design.
- **Warm air.** It spreads from each source up to 5 blocks through open cells: air, partial blocks (gaps), open doors, trapdoors and fence gates, and open or powered vents. It never passes full blocks, closed doors or glass.
- **Line of sight.** It is blocked by any collider, glass included, and ends at 64 blocks from the eyes.
- **Three ways to see heat, in order:**
  - DIRECT: the source itself is visible.
  - LEAKED: warm air is visible where it meets unwarmed open air, such as at an open door, a hole or a vent.
  - WALL_GLOW: the outer face of a thin wall right beside a source is visible. Thin means one layer with open air behind it. Wool and snow (conductance 0.1 or less), glass, and a second layer show nothing.

**Upload on sight (Lagged).** Each sighting is uploaded the moment the look is taken. It becomes a `HEAT_SOURCE` event in the subject's world model at the point seen, weighted 0.2, or 0.1 for a wall glow. Heat under a roof also adds a shelter sample, which pulls the centroid toward it. Repeated looks within one visit share an encounter, so they never stack. Killing the Pyro after a look undoes nothing; killing it before its first look leaves no trace. Later raids and the Scribe's map read these beliefs as memory, never the live world.

**Covered in Mud.** A visit whose looks all completed and saw no heat at all grants the subject the hidden advancement when the watch ends.

**ERASED and E11.** ERASED wipes the store and the claim. An active Pyro loses its order, departs without uploading, and no new one is designated. After E11 no Architect exists, so there is none.

## Lab commands (`-Dfrozendawn.labCommands=true` only)

- `fd maeve pyro seed-home`: one shelter sample where the command runs, so Maeve suspects a home there.
- `fd maeve pyro roll`: makes one natural Architect at the command position and asks the gate. If it is designated, it spawns as a Pyro. Otherwise it is not spawned. Either way the reply includes the `PYRO:` diagnostics line.
- `fd maeve pyro clear-cooldown`: clears the cooldown; it refuses while a Pyro is out.
- `fd maeve dump` shows `PYRO: decision=... cooldownRemaining=... sight=64` and `PYRO active: ...`.

## Verification

Native GameTests (`MaevePyroGameTest`, all in `config/architect-required-tests.txt`):

- the gate and single claim
- thin, double, wool and glass walls, open and closed doors
- vents, magma decoys and an unlit campfire
- the 64-block range
- the real-AI watch: it stays in the cold, uploads on its first look, and its look outlives its death
- killing it before its first look
- Covered in Mud
- defending and leaving
- leaving after ERASED
- parsing the lab world's functions

The visual pass is [macs-pyro-playtest.md](macs-pyro-playtest.md).

## Follow-ups

- No raid planner reads HEAT_SOURCE as a target yet. Today it feeds the shelter centroid, the commitment threshold check that stops the next Pyro, and the Scribe's marked map.
- When departing, the Pyro is discarded as soon as no player is within 32 blocks. A player watching from the home, 40 blocks out, may see it vanish in the open. This is to be judged in the visual pass.

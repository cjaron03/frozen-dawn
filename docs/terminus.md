# ORSA TR-0 Terminus (the Deathwand)

Spec: Notion, Frozen Dawn / Backlog / "ORSA TR-0 Terminus (the Deathwand)" (drafted and decided 2026-11-03). This file
is the tracked handoff; the spec stays on Notion.

## Checkout

- Worktree `/Volumes/SSD/Work/frozen-dawn-terminus`, branch `feat/terminus`, cut 2026-11-03 from `feat/sever-lash-2`
  at bd5d048a (PR #145, stacked on #143). A PR for this branch is based on `feat/sever-lash-2` while #145 is open.
- Scheduling: the owner inserted the Terminus after Sever Lash slice 2 and before the Proximity Tracker (2026-11-03).
  The Notion page still says "after the Sweep Turret"; the owner's later word wins.

## Slices

| # | Slice | Status |
| --- | --- | --- |
| T1 | The wand itself, creative tab and `/give` only | Built on `feat/terminus`; awaiting the owner's playtest |
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

## T1 as built (2026-11-03)

### Behavior
- `TerminusItem`: stack 1, fireproof, epic, `canBeHurtBy` always false (lava, fire, cactus, blasts), and a dropped
  wand's lifespan is `Integer.MAX_VALUE`. Its `frozendawn:terminus_log` component (`TerminusLog`: discharges, logged)
  defaults to 3 and 1, so the creative tab and `/give` both hand out a wand as found. At 0 discharges `use` fails: an
  inert trophy.
- Wind-up: `getUseDuration` 40 ticks; `finishUsingItem` discharges. On use the server plays `item.terminus.windup`
  bound to the operator, a fixed-range sound event of 24 blocks. Movement at 40%: using an item already cuts walking to
  a fifth, so `TerminusClient` doubles the input first (`MovementInputUpdateEvent`).
- Interrupt (`Terminus.onDamaged`, `LivingDamageEvent.Post` with damage over 0): the discharge is spent, the use stops
  and the tone is stopped for everyone within 32 blocks. Letting go early costs nothing.
- Discharge (`Terminus.discharge`): one `getEntitiesOfClass` over the 12-block box round the eyes, then each living
  thing whose box centre or eyes fall in the cone (`TerminusCone`: 12 blocks, 15 degrees off the look, round). No line
  of sight, no armor check. In order:
  - the Master Architect (`isMasterArchitectVisual`, so its mind copies too) is untouched and gazes (below);
  - `frozendawn:terminus_immune` (Heart, Successor, Aggregate and fragments, Undone, Rimebound, Resonant, Remnant),
    invulnerable or spectating targets: nothing;
  - players: spared if creative, if the server has PvP off, or if a teammate with friendly fire off
    (`canHarmPlayer`); otherwise stopped;
  - `c:bosses`: lose 40% of maximum health, never below 1, not logged;
  - `frozendawn:terminus_susceptible`: stopped and logged.
- Stopping, before the CH-18 trigger: `CommonHooks.onLivingDeath` fires with a `frozendawn:terminus` source naming the
  operator; unless a listener cancels it, the target is removed (`KILLED`) with no drops and no experience. Players
  always die the ordinary way. After the trigger every target dies the ordinary way: kill credit, loot and experience
  are the operator's, and the death message reads "X was stopped by Y".
- Cost: `frozendawn:terminus_toll` (player attachment, copied on death) counts discharges; a permanent `MAX_HEALTH`
  modifier `frozendawn:terminus_toll` of -4 each is reapplied on clone and login, and health is held under the new
  maximum. Then 10 s of Darkness and Nausea, and the hidden advancement Deathwand ("Nothing burned.").
- The Master's gaze (`TerminusGaze`): for 60 ticks, after its own tick, its head is turned to the wand in the
  operator's hand (`Terminus.wandPosition`: forward of the eyes, below them, to the holding side), following it if the
  operator moves. Then its own look control takes over again.
- The log's turn (`TerminusLedger`, world saved data `frozendawn_terminus`): on a player's death with Maeve erased and
  not escaped, it turns once and never back. Wands then read `Terminations logged: N`, the same N. E11 is not built;
  its flag plugs into `TerminusLedger.maeveEscaped`. CH-18 itself is not built either; when it is, the ledger should
  read its flag. The client mirror arrives on login and on the turn (`TerminusLedgerPayload`).
- Tooltip: `Discharges remaining: N`, `Transfers logged: N` or `Terminations logged: N`,
  `Operator exempt. Protocol active.`, `NOT FOR USE IN OCCUPIED HABITATS.` No Terminus text names Continuity
  (`TerminusTextTest`).

### Look and sound
- Model (`tools/build_terminus_models.py`, textures from `docs/art/terminus/make_textures.py`): the Reliquary pick.
  Pommel, black knurled grip, steel collar, a caged glass shaft, steel rim and a flat emitter face, one block long.
  Inside the glass a 1x1 channel holds one pale mote per discharge left, drifting on an interpolated four-frame
  texture, fullbright. The model is translucent. Overrides on `frozendawn:motes` (discharges, up to 3) and
  `frozendawn:windup` (0, or stage 1 to 4 through the two seconds): the motes slide to the emitter, the glass frosts in
  four steps and the face brightens to white. Spent, the channel and face go dark.
- Holding (`TerminusHold`): in the fist, emitter forward; winding up, one arm straight out along the look
  (`FROZENDAWN_TERMINUS` arm pose), turned in 0.3 rad as the Lash's grip arm is, so the wand reuses the Lash's solved
  display transforms, shifted to put its grip in the fist. In first person it lifts toward the crosshair and trembles
  more as the wind-up ends.
- Wind-up specks (`TerminusClient`, `terminus_speck`): every client draws, for every player winding up, pale specks
  drawn in to the emitter from inside the cone, two in three on its edge, from one to six a tick as it charges.
- Discharge (`TerminusDischargePayload`, to the operator and everyone tracking them): the operator's view goes grey for
  one frame (`shaders/post/terminus_grey.json`, vanilla `color_convolve` at saturation 0; skipped if another post
  effect holds the screen), and a `terminus_mote` lifts half a block from each stopped mind's head, hangs, flickers and
  winks out. At most 64 motes per discharge; the rest are stopped without one.
- Sound: only the wind-up (`tools/generate_terminus_sounds.sh`, `tools/audio_sources/terminus/SOURCES.md`): a 2 s tone
  gliding 48 to 96 Hz with its octave beating against it and a thin glassy hiss, stopping dead. The discharge is silent.

### Rulings (agent, for the owner to confirm)
- The table governs the tag: the owner's "zombies, all Architect subtypes, and players" plus the table's other rows.
  "Villagers and other human-shaped mobs" is read as villagers, wandering traders, illagers, witches and piglins;
  skeletons, strays and zombie horses have no minds. All vanilla zombie types are zombie, husk, drowned, zombie villager
  and zombified piglin.
- Other players die the ordinary way even before the Heart falls, inventory and all: they respawn, so they are not
  taken. A server with PvP off, a teammate with friendly fire off, and a creative player are spared.
- "Other boss-tagged entities" means `c:bosses` (the Wither and the Ender Dragon, and other mods' bosses); the 40% is set
  directly, so no resistance, shield or invulnerability phase changes it.
- An interrupted discharge spends the count only: no toll, no Darkness, no log. Any damage counts, the cold included.
- No toll floor beyond vanilla's 1 max health.
- The gaze lasts three seconds and follows the wand.
- Totems and protection mods do not stop a discharge; it does not pass through the damage pipeline.
- Death message: "X stopped", or "X was stopped by Y".

### Playtest
`python3 tools/prepare_terminus_playtest.py --source <a 1.21.1 save>/level.dat --destination run/saves/<new name>`
builds "Terminus Check": Survival, Normal, a station per behavior, each a gold block with its targets to the south:
cone (edge and reach husks), walls (obsidian), the untouched table, a frozen Wither, a NoAI Master Architect
side-on, sword zombies with before and after the Heart buttons (`fd terminus turned`), a magma interrupt pad and open
ground for the wind-up. `fd terminus status | restore | turned <bool>` reads and resets the playtest state; in play
neither the toll nor the turn reverses. Advancements are filtered as in every generated world.

### Verification
- JUnit: `TerminusConeTest`, `TerminusLedgerTest`, `TerminusTextTest`, `TerminusDischargePayloadTest`.
- GameTests (`-PfdTests=terminus`, batches `terminus` and `terminus_terminal`): found wand and tooltip; cannot burn or
  despawn; cone hits and misses; through walls; the immunity table; bosses at 40% and never killed; the Master's look at
  the wand and its end; operator spared, other players stopped, creative, PvP and teammates spared; the toll through
  respawn, the backlash and Deathwand; the two-second wind-up and the 24-block tone; an interrupt spends the discharge
  and letting go does not; inert at zero; no drops or experience before the Heart, with the death event naming the
  operator; an ordinary credited kill after; no turn while Maeve stands; the Terminations label.
- Needs the owner's eyes and ears: the model, textures and mote drift in every view, the frost, the holds, the specks,
  the grey frame, the target motes, the tone, and the 40% walk.

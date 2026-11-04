# ORSA TR-0 Terminus (the Deathwand)

Spec: Notion, Frozen Dawn / Backlog / "ORSA TR-0 Terminus (the Deathwand)" (drafted and decided 2026-11-03). This file
is the tracked handoff; the spec stays on Notion.

## Checkout

- Worktree `/Volumes/SSD/Work/frozen-dawn-terminus`, branch `feat/terminus`, cut 2026-11-03 from `feat/sever-lash-2`
  at bd5d048a (PR #145, stacked on #143). Both have merged into `feat/maeve-director`, so the T1 PR is based on
  `feat/maeve-director` (opened 2026-11-03 on the owner's word; the owner approves the merge).
- Scheduling: the owner inserted the Terminus after Sever Lash slice 2 and before the Proximity Tracker (2026-11-03).
  The Notion page still says "after the Sweep Turret"; the owner's later word wins.

## Slices

| # | Slice | Status |
| --- | --- | --- |
| T1 | The wand itself, creative tab and `/give` only | Built on `feat/terminus`; owner playtests passed ("good", 2026-11-03); branch review, then a PR on the owner's word |
| T2 | The Terminus Vault at the first Helion site, the Unwatched Door puzzle, the vault documents | Built on `feat/terminus-vault` (worktree `/Volumes/SSD/Work/frozen-dawn-terminus-vault`), stacked on `feat/terminus` while PR #146 is open; awaiting the owner's pass |
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

### T3

As the spec, section 6.

### T2: the vault

See "T2 as built" below.

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
- 2026-11-03, deaths: after playtest 1 ("the enemies just disappear") the owner picked "still, grey, then fall", then
  asked for it to go "like how the thanos snap is". Approved as drawn ("Build it as drawn") in
  `docs/art/terminus/stop-mockup.png` (`make_stop_mockup.py`): the body stands still and greys for half a second, then
  crumbles from the head down along a ragged edge into one-texel grey flakes that drift up and off to the right, paling
  as they go. No tip-over, no red flush, no poof.
- 2026-11-03, cost: after playtest 2 the owner found a permanent max-health loss through death too steep, and asked for
  "a unique heart debuff, like how radiation acts in fallout". Approved as drawn ("Build it as drawn") in
  `docs/art/terminus/taken-hearts-mockup.png` (`make_taken_hearts_mockup.py`): each discharge takes two hearts, shown
  at the end of the health bar as ash-grey hearts with a pale mote; they are not health; half a heart comes back every
  half Minecraft day. Death keeps the toll and its clock, so dying does not undo it. This replaces the spec's "removes
  4 max HP for good".
- 2026-11-03, reach: after playtest 3 the owner asked for a longer range and picked 24 blocks (the spec had 12). The
  cone stays 30 degrees, about 13 blocks across at its end.
- 2026-11-03, Reduced to Atoms: after playtest 3 the owner asked for a hidden advancement for three or more mobs in one
  discharge, after Thanos's line. Built as "Reduced to Atoms", "Stop three minds or more with one discharge.", a hidden
  challenge under Deathwand.
- 2026-11-03, sound: the owner asked for a better charge-up and wondered about a firing sound. None of nine synthesized
  candidates (glass, capacitor, heartbeat; snap, toll, pressure; ghost choir, whispers, wrong glass; two Sleeper-like
  crystals) clicked, nor the Warden's charge. Hyperion's deathwand is silent with an invisible beam, so the owner picked
  demo B: a faint heartbeat charge heard only close by, and on discharge the world going silent for the operator.

- 2026-11-03, the vault's hiding place: the owner asked for the room to be "very...very hidden", against the spec's
  "every player who reaches Helion sites finds it", and picked **B, under the Core** from
  `docs/art/terminus/hiding-options.png` (`make_hiding_mockup.py`): a drain grate under the Confinement Core, so nothing
  shows until the Core is out, by which time the Uplink has stopped marking the site. One clue in the game, the Drain
  Schedule in that site's desk ("Floor drains drawn: 5", "Floor drains in hall: 4").
- 2026-11-03, the rooms and contents: "Build as drawn", `docs/art/terminus/vault-rooms-mockup.png` and
  `vault-contents-mockup.png` (`make_vault_mockup.py`). That covers these agent calls, listed to the owner with the
  mockups: the bridge reaches two cells past whoever stands on it (a walkway that only froze could be walked forward);
  a fall lands in powder snow with a ladder back and costs nothing; the wand shows through the last door's viewport from
  the start, since the spec allows one change on waking; a digital clock counting back one a second from a seed-set
  number; the first site at least 27 blocks above the world floor takes the vault; four tale logs and the technician's.

## T1 as built (2026-11-03)

### Behavior
- `TerminusItem`: stack 1, fireproof, epic, `canBeHurtBy` always false (lava, fire, cactus, blasts), and a dropped
  wand's lifespan is `Integer.MAX_VALUE`. Its `frozendawn:terminus_log` component (`TerminusLog`: discharges, logged)
  defaults to 3 and 1, so the creative tab and `/give` both hand out a wand as found. At 0 discharges `use` fails: an
  inert trophy.
- Wind-up: `getUseDuration` 40 ticks; `finishUsingItem` discharges. On use the server plays `item.terminus.windup`
  bound to the operator, a fixed-range sound event of 8 blocks (attenuated over 8). Movement at 40%: using an item already cuts walking to
  a fifth, so `TerminusClient` doubles the input first (`MovementInputUpdateEvent`).
- Interrupt (`Terminus.onDamaged`, `LivingDamageEvent.Post` with damage over 0): the discharge is spent and the use
  stops. Letting go early costs nothing. However the wind-up ends short (let go, hit, slot change, death),
  `TerminusItem.onStopUsing` stops the tone for everyone within 32 blocks.
- Held use: a discharge or an interrupt puts the wand on a 20-tick cooldown, and the client will not start another
  wind-up until the use key has come up (`TerminusClient.releaseGate`), so holding the key cannot run through the
  charges.
- Discharge (`Terminus.discharge`): one `getEntitiesOfClass` over the 24-block box round the eyes, then each living
  thing whose box centre or eyes fall in the cone (`TerminusCone`: 24 blocks, 15 degrees off the look, round). No line
  of sight, no armor check. In order:
  - the Master Architect (`isMasterArchitectVisual`, so its mind copies too) is untouched and gazes (below);
  - `frozendawn:terminus_immune` (Heart, Successor, Aggregate and fragments, Undone, Rimebound, Resonant, Remnant),
    invulnerable or spectating targets: nothing;
  - players: spared if creative, if the server has PvP off, or if a teammate with friendly fire off
    (`canHarmPlayer`); otherwise stopped;
  - `c:bosses`: lose 40% of maximum health, never below 1, not logged;
  - `frozendawn:terminus_susceptible`: stopped and logged.
- Stopping: every target dies through its own `die()` with a `frozendawn:terminus` source naming the operator, so the
  death event fires once and anything that keeps a roll of the living hears of it; kill credit and stats are the
  operator's, and a player's death message reads "X was stopped by Y". Before the CH-18 trigger a non-player is also
  tagged taken: its `LivingDropsEvent` and `LivingExperienceDropEvent` are cancelled, so nothing drops. Players always
  drop. A target whose own death defers (health left above 0) is not logged.
- The body (non-players): `Terminus.holdDeath`, from a `LivingEntityMixin` hook on `tickDeath`, keeps it for 32 ticks
  instead of vanilla's 20 and then removes it with no poof, so the client can crumble it.
- Cost (owner's ruling, taken hearts): `frozendawn:terminus_taken` (player attachment `TerminusToll`, copied on death)
  holds the half hearts taken and the overworld game time the next half returns. A discharge takes 4; a clock already
  running keeps running. `Terminus.onLevelTick` returns half a heart when due and sets the next half a day (12,000
  ticks) on. A `MAX_HEALTH` modifier `frozendawn:terminus_toll` of minus the taken points is reapplied on every change,
  clone and login, and health is held under the new maximum. Then 10 s of Darkness and Nausea, the hidden advancement
  Deathwand ("Nothing burned."), and, if the discharge stopped three minds or more, the hidden challenge Reduced to
  Atoms.
- Taken hearts (`TerminusHearts`, drawn from `GuiTerminusHeartsMixin` at the end of vanilla's `Gui.renderHearts`, on its
  rows and slots): one `hud/heart/terminus_taken` sprite per taken heart after the health and absorption hearts; an odd
  toll covers the right half of vanilla's last heart, a half heart of maximum, with `terminus_taken_half`. The client
  reads the count from its own toll modifier, so no packet is needed.
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
  winks out. At most 64 minds per discharge are sent (by entity id); the rest are stopped without a mote or a crumble.
- The crumble (`TerminusCrumble`, owner's mockup): on the discharge payload each client takes the stopped bodies over.
  Ticks 0 to 10 the body greys where it stands (its death clock held at 0, so it neither tips over nor flushes red);
  ticks 10 to 30 it crumbles head first. The body is drawn again through `RenderLivingEvent.Pre` into
  `shaders/core/terminus_crumble`, on the textures of its own model, armor and held items (read from each render type
  through accessor mixins); glints are left off. The vertex color carries height from the top, progress and greyness;
  the shader greys the texel and discards it once progress passes its height plus a per-texel hash of up to a quarter
  of the body. About 140 `terminus_flake` particles for a husk leave the crumbling band, one texel across, drifting up
  and to the operator's right, paling and fading over 11 to 17 ticks. `TerminusCrumbleTest` holds the crumble inside
  the server's 32 ticks.
- Sound (owner's pick): the wind-up (`tools/generate_terminus_sounds.sh`, `tools/audio_sources/terminus/SOURCES.md`) is
  a 2 s slow 48 Hz heartbeat quickening from 1.2 to 6 beats a second under a faint, slightly rising 5.2 kHz whine,
  stopping dead, heard to 8 blocks. The discharge has no sound of its own: for the operator (`TerminusHush`, on the
  discharge payload) the listener's gain drops to nothing at once, holds 26 ticks, and fades back to the master volume
  over 24.

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
- Before the Heart falls, stops count as kills for stats and kill advancements (Monster Hunter, the Pacifist's
  tally); only loot and experience are withheld.
- Record Four still drops when the Heart is exposed: `ThaevenLoreAcquisitionHandler` spawns it straight from the death
  event, apart from loot, and the Terminus leaves it alone.
- Invulnerable mobs (`isInvulnerable`) are spared even when the operator is creative.
- The tone is stopped by name, so another operator winding up within 32 blocks is cut off too.
- Players crumble on the same clock, but vanilla removes a dead player's body at 20 ticks with its poof, partway
  through the crumble.

### Playtest
`python3 tools/prepare_terminus_playtest.py --source <a 1.21.1 save>/level.dat --destination run/saves/<new name>`
builds "Terminus Check": Survival, Normal, a station per behavior, each a gold block with its targets to the south:
cone (edge and reach husks), walls (obsidian), the untouched table, a frozen Wither, a NoAI Master Architect
side-on, sword zombies with before and after the Heart buttons (`fd terminus turned`), a magma interrupt pad and open
ground for the wind-up. `fd terminus status | restore | due | turned <bool>` reads and resets the playtest state;
`due` brings the next half heart back at once. In play the turn never reverses. The frozen Wither's boss bar does not
move (vanilla updates it from the Wither's AI), so its station has a [Wither health] button. Advancements are filtered as in every generated world except the
Terminus's two, which the owner asked to see: the world's pack carries copies, Deathwand made a root since its parent
is filtered.

### Verification
- JUnit: `TerminusConeTest`, `TerminusLedgerTest`, `TerminusTextTest`, `TerminusDischargePayloadTest`,
  `TerminusCrumbleTest`, `TerminusTollTest` (two hearts a discharge, half back each half day, the clock left running),
  `TerminusHushTest` (the silence holds, then the volume comes back whole).
- GameTests (`-PfdTests=terminus`, batches `terminus` and `terminus_terminal`): found wand and tooltip; cannot burn or
  despawn; cone hits and misses; through walls; the immunity table; bosses at 40% and never killed; the Master's look at
  the wand and its end; operator spared, other players stopped, creative, PvP and teammates spared; the toll through
  respawn, the backlash and Deathwand; the two-second wind-up and the 8-block heartbeat; an interrupt spends the discharge
  and letting go does not, each leaving the cooldown; inert at zero; before the Heart one death event naming the
  operator, a credited kill and no drops or experience; an ordinary credited kill after; either way the body stands
  past vanilla's 20 ticks and is gone by 34; the toll through the End portal's return; invulnerable mobs spared by a
  creative operator; no turn while Maeve stands; the Terminations label; 24 blocks of reach along the template's
  diagonal; Reduced to Atoms for three minds and not two; taken hearts kept through death and the End portal, and half
  a heart back when due. The ledger is pinned per batch.
- Needs the owner's eyes and ears: the model, textures and mote drift in every view, the frost, the holds, the specks,
  the grey frame, the target motes, the crumble and its flakes, the heartbeat and the silence after, the 40% walk, and
  the release gate.

## Owner playtest 1, 2026-11-03 (Terminus Check, runClient, branch head 27948f32)

Unprompted report: "wow i like it, but the enemies just disappear." The owner asked whether they should die instead,
perhaps with unique particles. Before the Heart falls they are removed outright, as the spec has it, so nothing is left
to fall over; the spec's presentation line does say "Targets drop where they stand".

Response: the crumble above, approved from its mockup and built in `3bcf2812`.

## Branch review, 2026-11-03

One subagent on Opus reviewed the branch with a fresh context: Opus because the branch adds a client/server payload,
saved world data and a player attachment, and hooks the shared death path. Fixed, each under a test written first:
- the pre-Heart stop removed the body directly and skipped `die()`, so rolls of the living never heard of it;
- held use re-triggered the wind-up every four ticks and could burn every charge (cooldown and release gate);
- the tone stopped only on release, not on a slot change or death;
- `/data`-invulnerable mobs were stopped by a creative operator;
- the gaze tick read its map before checking the side;
- tests depended on the world's ledger from earlier batches; the label test did not restore it;
- no test for the End portal's return of the toll.

Set aside: a stop that turns the ledger mid-discharge still uses the old value for the rest of that discharge
(negligible); no GameTest drives the wind-up through real ticks or the gaze against a live look control.

## Owner playtest 2, 2026-11-03 (Terminus Check 2, runClient, branch head 37443066)

Unprompted report: "looks good, but i noticed that when the mob disingirates, after it flashes and i can see the normal
death animatioin (it falls down etc) for like a split second"

Recheck after `8c41a4df` (the body held hidden until the server removes it): "that fixed it." Then: "idk a permant
helath loss is pretty steep for this, especially if it carries over from deaths dont you tihnk"

## Owner playtest 3, 2026-11-03 (Terminus Check 3, runClient, branch head 59fd7644)

Unprompted report: "huh wither doesnt  take any damange, furthermore im thinking we should make the range longer, and a
advancemnt if you like 3 or more mobs, you get a hidden advancemnet "Gone, reduced to atoms" or whatever thanos said"

Response: the Wither does take the 40% (`terminusTakesFortyPercentFromBossesAndNeverKills`), but the station's Wither is
frozen with NoAI and vanilla updates a Wither's boss bar only from its AI, so the bar stayed full. The station now has
a [Wither health] button reading its health. The reach is now 24 blocks and Reduced to Atoms is built (rulings above).

## Owner request, 2026-11-03 (Terminus Check 4, branch head b77e3c92)

"ah enable advancemnets please, and i wanna fix the sounds. i want it to have a better chargeup sound, and maybe
sometihng when it first, or is that not realistic"

## Branch review 2, 2026-11-03 (27948f32..09b5da73)

One subagent on Opus reviewed the changes since the first review with a fresh context: Opus because they change a
player attachment's save format, the discharge payload and the shared death path (a `LivingEntity` mixin). Fixed, each
under a test written first and seen to fail:
- the held death clock hooked `tickDeath` itself, which Frostbitten, Hollow, Returned and Architect override without
  calling it, so they still vanished, dissolved or fell; it now wraps the call in `baseTick`, on both sides
  (`terminusHoldsEveryStoppedBodyForTheCrumble`);
- the hush set the listener gain to exactly 0, at which vanilla skips new sounds outright, so loops and voice lines
  started in the silence were lost; it now floors at 1e-4 (`TerminusHushTest`);
- the hush touched OpenAL even when vanilla had turned sound off for want of a device; it now checks `loaded`;
- the toll clock restarted from `now`, so time away from a running server was lost; it now steps from the due time
  (`TerminusTollTest.timeAwayCountsWhenThePlayerReturns`);
- an Architect's `eyes` layer floated after the crumble took its head; it now crumbles with the body.
- (an automated review of that fix) holding the clock also skipped the Architect's own teardown: its ice stayed, its
  block breaker and tower encounter were left open. A body that tears something down implements `TerminusBody`, and
  the Terminus calls its quiet `removeStopped` instead of a bare removal; the Architect's deaths end in the same method
  (`TerminusArchitectGameTest`). Returned, Undone, Hollow and Frostbitten tear nothing down.

Set aside (minor): taken hearts past the rows vanilla reserved (absorption, more than ten slots, a toll over 19) draw
over the armor row, and the half sprite ignores the low-health jiggle; translucent bodies (Mimic, Hollow, the Architect)
turn opaque while they crumble; one crumble render type is kept per texture for the session; the playtest script borrows
the Blind Architect script's NBT helpers; saves from before `59fd7644` drop their old `terminus_toll` and are forgiven
it; Hollow and Frostbitten still give their own burst in `die()` when stopped.

## Owner playtest 4, 2026-11-03 (Terminus Check 5, runClient, branch head f85128a0)

Unprompted report: "good." Then: "whats next? the puzzle in helion? i want the room to be very...very hidden"

## T2 as built (2026-11-03)

- Keats: the four lines match Project Gutenberg #23684, *Keats: Poems Published in 1820*, word for word
  (`VaultDocumentsTest`).
- `TerminusVaultBuilder` lays the vault out relative to the hall floor centre, as the mockup: grate at (0, 1, 0) under the
  Core, ladder down to the landing, door 1, the bridge hall and pit, the cot room, the last door, the vault, floor at
  y-15. `claim` runs from `HelionSitePlacement.tickPlacement` after a site is built; `HelionSiteState.vault` is the
  world's flag. The site that may take the vault waits on 28 more blocks of chunks. `/fd terminus vault place | reset |
  status` is the debug placement.
- The rule (`VaultWatch`): a player or `terminus_susceptible` mob within 24 blocks, its head within 35 degrees of the
  target, with one clear ray. Sleepers and spectators do not count. Server only, so third-person front view needs nothing.
- `VaultMechanismBlockEntity`, one per mechanism, in the shell: door 1 opens over 80 ticks and only forward; the bridge
  lays a cell per 15 ticks while someone stands within `VaultBridge.REACH` of its end and nobody faces the gap; the last
  door opens when a 100-tick rest on the cot ends unwatched, turning the technician's head. Progress persists. A door's
  `vault_door_cell` blocks are invisible and stop you; `VaultMechanismRenderer` draws the 3x3 panel from that block's
  model, sliding. Clearance is per column, so the panel can show a sliver more gap than you can walk through.
- The cot (`VaultCotBlock`) is a bed to the entity's sleeping state and nothing else: no respawn, no night skip, any
  hour (`CanContinueSleepingEvent`). The fade is vanilla's sleep overlay, which reaches about 86% dark, not black.
- Technician: a block with a renderer, not an entity, on the player model with `textures/entity/vault_technician.png`.
- Cradle: gives the wand as found; the forks stay empty. Desk: gives the manifest and name review once. Locker: a
  vanilla barrel holding the five duty logs.
- Clock: `VaultClock`, one a second off the game clock from a seed-set start, past zero back to 99:59:59.
- Sound: `block.vault.grind`, a 0.55 s grain every half second while anything moves, heard to 24 blocks.
- Documents (`VaultDocuments`, Field Manual entries under ORSA intel): Drain Schedule, Vault Manifest, Name Review, duty
  logs of Deng, Varga, Anand, Hale and Okoye. No Continuity (`VaultDocumentsTest`).

### Rulings (agent, for the owner to confirm)
- Doors only ever open; nothing closes them in play.
- The hall stays breakable, so a player can dig to the crack as they like; only the shell cannot be mined or blasted.
- Digging down from the hall meets the shell, and a tunnel can reach the bore's ladder. It stays hidden, not sealed.
- Any mind beside the door counts, including one on the far side.
- A rest spoiled by a watcher ends as normal with the door shut; try again.

### Playtest
`python3 tools/prepare_terminus_vault_playtest.py --source <a 1.21.1 save>/level.dat --destination run/saves/<new name>`.

### Verification
- JUnit: `VaultRulesTest`, `VaultDocumentsTest`.
- GameTests (`terminus_vault`, `_bridge`, `_alone`): `TerminusVaultGameTest`. Tests of a batch stand side by side, close
  enough to watch each other's doors, which is why bodies face north and the bridge has its own batch.
- Needs the owner's eyes and ears: everything judged by look or sound, the finding of the grate, the crossing in F5, and
  the wake.

## Owner playtest 1 of T2, 2026-11-03 (Terminus Vault Check, runClientLab, branch head after b29ddf28)

Unprompted report: "ok i played it. what does the timer mean again? and honestly, it needs to be MUCH BIGGER nad harder
than this ngl. like i want more than what we just added"

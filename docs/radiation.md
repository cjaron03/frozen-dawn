# Radiation

Source: Notion "Radiation" (Power System), with the owner's decisions of 2026-10-15. Branch
`feat/hearthstone-radiation`, cut from the Hearthstone Reactor commit `c5541cdb`.

## Built

- **Dose** (`RadiationMath`, `RadiationState` attachment, `RadiationHandler`): once a second per player. Rate in RU/s
  from every source within 16 blocks; accumulated dose; about 1 RU per 10 s recovery outside radiation; resets on
  death. Symptoms follow a felt dose that lags the real one by a 30 s latent period.
- **Sickness**: stages at 100/300/600/1,000 RU per the Notion table (hunger and nausea; weakness, mining fatigue,
  halved healing; four hearts lost, no healing, occasional damage; steady lethal damage). Shown as the trefoil
  Radiation Sickness effect, reapplied every second from stored dose so milk cannot clear it.
- **Sources and shielding** (`RadiationField`): carried items (no shielding), other players, dropped items,
  containers (chests do not shield), running cores (0.1 RU/s per kW of fission heat), spent rods in a Controller,
  Corium, molten Corium, fallout and nearby uranium ore. Inverse-square from one block; one ray per source with
  per-block factors (water, ice, stone and most solid blocks x0.5; concrete, obsidian and reactor casing x0.4).
  Bounded to 48 rays per player per second; never loads chunks. Block sources persist in `RadiationSourceData`.
- **Shielded Cask**: holds four spent rods, blocks 99%, Slowness I while carried loaded; fills from a Controller's
  output slot.
- **ORSA RadAid** (renamed from the Anti-Rad Kit): orange IV bag, three seconds to hook up, flushes 200 RU over a
  minute. No recipe: medical salvage (loot placement not yet done).
- **ORSA Geiger Counter**: crafted. Clicks only while carried (muffled in the bag, crisp in hand), Poisson-random
  with a little cosmic background. Held, it reads CPM, RU/s and dose; the needle swings on a log dial and pegs
  OFF SCALE above 200 RU/s. Clicks are small-speaker Geiger-Mueller pulses; above about 15 RU/s they blur into a
  crackling roar (a Poisson train of overlapping pulses) that is full by 100 RU/s and climbs in pitch to a scream
  near 400 RU/s.
- **Eye flashes**: above about 60 RU/s white-blue specks and streaks; above about 400 RU/s full blue flashes.
- **Contamination** (`Contamination`, `FalloutBlock`): spreads through open space from the release and settles as
  a thin grey-black fallout layer with drifting ash; sealed walls stop it. Meltdown in air reaches about 12 blocks,
  in vacuum about 3; a steam explosion throws it about 8 blocks through broken walls. Grass dies, leaves fall.
  Walking through fallout without a sealed suit contaminates the player until washed in water: an infinite
  Contaminated effect (milk does not clear it), soot smudged round the edge of the view, ash flaking off for others. Shovelling it up
  gives Radioactive Ash, itself a source. Fallout is a real block (with a source index) rather than invisible
  tracked positions, so it is visible and cleanable.
- **Meltdown sequence** (`ReactorControllerBlockEntity.meltdown`, `ReactorPhysics.EXCURSION_C`): above 780C with
  automatic SCRAM off, fission runs away (x12) and covers the last 20 degrees in seconds. At 800C every player
  within 64 blocks sees a white flash scaled by distance. With air at the Controller, hydrogen from the hot
  cladding detonates (power 6, breaks blocks, opens the shell) and throws up to 8 Reactor Debris blocks 6 to 18
  blocks out; in vacuum there is no blast, the casing only scorches. Either way the core becomes molten Corium and
  heavy fallout (darker, 5x the strength of ordinary fallout, upgrades thin layers) spreads 12 blocks in air or 3
  in vacuum. Reactor Debris is a 150-strength source, hot underfoot; pick it up with a Shielded Cask that has room.
  Mining it by hand gives you the source in your inventory.
- **Airborne fallout**: where more than 10 fallout layers lie within five blocks, dust hangs in the air with a
  grey haze that thickens to about 10 blocks of visibility at 50 layers. It cakes onto a sealed EVA visor (soot
  overlay) until washed in water, and contaminates an unsealed player standing within three blocks of 10 or more
  layers even off the fallout itself. The haze is client side; contamination is server side from the source index.
- **Lead** (`ModBlocks.GALENA_ORE`, `LEAD_BLOCK`, `LeadLiningModuleItem`): galena ore (stone and deepslate,
  y -32 to 64, about as common as iron) drops Raw Galena, which smelts or blasts into Lead Ingots. A Block of Lead
  shields x0.1, the best per-block shield. The Lead-Lined EVA Module (6 lead, 2 clothing scraps, a suit patch kit)
  works like the O2 Efficiency Module: carried anywhere, it halves the whole incoming rate, including carried
  sources, but only under a full sealed EVA suit. It is heavy: Slowness I while carried, suit or not. A real lining
  that halves fission-product gamma would be several millimetres of lead and far heavier.
- **Decontamination shower** (`DeconShowerBlock`): a copper shower head that hangs under a sturdy ceiling and
  links to the power network with the Conduit Binder. When a player stands within three blocks below it, it asks
  for 3 kW (pump and heater); once granted, and with a water, ice or snow block beside or above it as its tank,
  water runs and washes off contamination (server) and visor grime (client) like a swim. It draws nothing when
  nobody is under it. A starved shower shows by name on the Power Monitor. Recipe: copper, iron and a bucket.
- **Ruined ORSA reactor site** (`ReactorSitePlacement`, `ReactorSiteBuilder`, `ReactorSiteState`): a code-built
  landmark placed like Monitoring Stations. One candidate chunk per 96x96-chunk region (about 1,500 blocks), 60%
  of regions roll one, at least 600 blocks from spawn, in newly loaded chunks only. Any land biome except
  oceans, rivers, swamps and peaks, with at most 8 blocks of slope; if the candidate spot fails, spots up to 32
  blocks away are tried, and a rejected candidate logs its reason. Sites keep 32 blocks clear of towns,
  villages and other surface structures and 64 blocks clear of built ORSA landmarks. A
  15x15 concrete hall with the roof blown open over a torn, scorched CFU-7 shell; the core melted a hole through
  the floor into a sealed basement where two Corium blocks form an elephant's foot (deadly if you stand over the
  hole). A frozen spent-fuel pool holds a barrel rack: 2-4 salvaged fuel rods, 40-80% burned, beside 2 spent rods,
  under four layers of packed ice. The control room has a Geiger Counter, RadAid, a part-charged Power Cell, a
  shift log (automatic SCRAM was disabled to keep the dorms warm) and an empty Shielded Cask. Heavy fallout inside
  and within 10 blocks, thin fallout to 16, thrown Reactor Debris, dead ground. Find one with
  `/frozendawn locate orsa`; operators can build one 24 blocks ahead with `/fd world reactorsite`.
- **Advancements** (hidden, under Phase 6): 3.6 Roentgen, AZ-5, Elephant's Foot, Carnot Would Be Proud, Even the
  Reactor Gets Cold, War Never Changes, Was That the Geiger Counter?
- **Audio**: original Geiger clicks (`tools/audio_sources/hearthstone/`).

## Not yet

A Rad-X-style pill (offered, not chosen). Partly burned rods still emit like fresh ones (strength does not
scale with burn-up); making them hotter would also change routine rod handling at the Controller.

## Radiation Check world

`python3 tools/prepare_radiation_playtest.py --source <level.dat> --destination "run/saves/<name>"` builds stations
24 blocks apart (spent rods, ice pool, Corium bunker, fallout and wash pool, ore seam, meltdown in air, meltdown
in vacuum) with clickable chat steps and Survival/Creative toggles. Its advancement filter hides every advancement
except the seven under test and their Phase 2-6 parents, in one rule: Minecraft tests a filter's namespace and
path patterns independently, so separate rules cannot be paired.

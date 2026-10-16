# ORSA CFU-7 Hearthstone Reactor

Source: Notion "ORSA CFU-7 Hearthstone Reactor" (design approved 2026-10-10, last edited 2026-10-14) under Power System.
Branch `feat/hearthstone-reactor`, cut from `feat/maeve-director` (d3e10cd9) and fast-forwarded to the pushed
`origin/feat/power-network` (716da18c), because the reactor supplies that grid. Merge the power-network PR into
maeve-director first (merge commit, not squash), then this branch.

## Built in this slice

- **Shell**: 3x3x3 multiblock. Top: 8 Reactor Casing around the Control Rod Drive. Middle: Reactor Core centre; the
  four faces hold the Controller (facing out), 2 Coolant Ports and 1 casing; corners casing. Bottom: 9 casings
  (22 total). The Controller's Shell line names the first wrong cell (`ReactorMultiblock`).
- **Fuel chain**: Deepslate Uranium Ore (Y -64 to -1, `neoforge/biome_modifier`), drops 1-2 Raw Uranium (fortune,
  silk touch). Raw Uranium refines into Uranium Concentrate only in the powered ORSA Induction Furnace (an
  induction-only recipe list, never registered with vanilla furnaces; a 1,200C high-temperature process at 8 kW for
  30 s per Raw Uranium, shown in the furnace screen and JEI; ordinary smelting stays 2 kW). 4 concentrate craft 1 Uranium
  Pellet; a Fuel Rod takes 4 pellets, 4 iron, 1 copper. Rod life is item durability in full-output seconds
  (4 in-game days). Replaces the Notion Fuel Processing Silo step (owner decision 2026-10-15). The ore model draws
  vanilla deepslate under a faintly emissive pitchblende overlay.
- **Recipes**: as the Notion table (casing x4, core needs a Thermal Core and Refined Acheronite, rod drive, port,
  controller, radiator).
- **Radiators**: linked to Coolant Ports with the Conduit Binder, port to radiator only, 16 blocks. Coolant links
  share the Binder and saved link store but never join an electrical network and skip the feedthrough rule.
  They render blue; power links keep the accepted cyan. A radiator needs one uncovered outdoor face (vent rule).
  Snow on a face covers it; running radiators glow, burn what stands on them and melt adjacent snow layers.
- **Physics** (`ReactorPhysics`, stepped once per second): 35 kW per rod at full insertion. Radiator rejection is
  0.024 kW/C x (core - outside), so 12 kW per radiator with a 300C core against -200C. Power out = radiator heat
  removed x (1 - T_cold / T_hot). Casing loses 0.02 kW/C to the room; 10% of that warms the room through the
  thermal model. Core heat capacity 84 kJ/C (LOSS OF COOLANT to failure in about 60 s at full output).
  Coolant pumps run only while the core makes heat or is above 40C, so an idle core settles to its room.
  While operating, coolant flow is throttled to hold the core between 200C and 280C (closed below, full above), so
  oversized radiators never chill a running core or cost efficiency; after a SCRAM pumps run full to cool down. A
  cold core under load-follow warms at full insertion first (Coolant "warming up").
- **States**: 350C warning (amber, klaxon, rod slots lock), 450C automatic SCRAM, 600C permanent damage (0.2%/s,
  capped at 75%; it derates electrical output only, never fission heat),
  700C LOSS OF COOLANT (radiators stop), 800C meltdown only with automatic SCRAM disabled. Manual SCRAM always
  recovers: casing loss above 700C exceeds peak decay heat.
  A trip latches until Reset; Reset is refused under redstone and, with automatic SCRAM on, at 450C or above.
- **Decay heat**: 7% of fission heat after a SCRAM or insertion cut, fading to 1% over one in-game day.
- **Cold start**: core below 0C for one in-game day freezes the coolant (no fission, no radiators); the room must
  stay at or above 5C for 3 minutes to thaw it.
- **Load-follow**: insertion slews 2%/s toward the heat that yields network demand, plus a 5 kW charging margin
  while banks are below capacity, minus other generators. Capped at what the radiators hold at 300C.
- **Controller UI**: core temperature, state, heat in/removed, power and efficiency, radiators and coolant, climb
  rate, rods and damage, load-follow target, ESTIMATED TIME TO CORE FAILURE, AUTO-SCRAM: DISABLED banner,
  insertion slider, SCRAM/Reset/Follow/Auto buttons, 4 rod slots and a spent-rod output, thermometer bar.
  Disabling automatic SCRAM takes the two Notion confirmation screens (the second a 3 s hold); the decision is
  logged. Redstone trips SCRAM; comparator output follows core temperature.
- **Meltdown**: core becomes Molten Corium, rods are lost, the rest of the casing scorches, ORSA line. Molten Corium
  sinks one meltable block every 3 s (casing, floor, stone; obsidian and bedrock hold) up to 8 deep, falls through
  air, pools into open floor beside it, and cools into solid Corium after about 90 s. Water, ice and snow it
  touches flash to steam; 4 or more blocks of water within 2 blocks cause a block-breaking steam explosion. With
  oxygen present it ignites nearby fuel; in vacuum it only glows and heats the room (Notion updated 2026-10-15).
- **Grid**: the Controller is a 16-block electrical node; its supply is the reactor's power out.

## Not in this slice (later per the Notion build order)

Radiation field and dose, Shielded Cask (spent rods currently move freely), contamination after meltdown,
salvaged fuel rods and the ruined ORSA reactor site, the five advancements, Pyro attraction to hot radiators,
ORSA Field Manual entries.

## Audio

Original procedural audio chosen by the owner from rendered concepts (`tools/audio_sources/hearthstone/`): a
throbbing running hum that rises with fission heat, a two-tone overheat klaxon from 350C, a SCRAM cue on every trip
and a failure siren from 700C. No voice.

## Choices to confirm

- Coolant links skip the bulkhead feedthrough rule (pipes treated as their own penetration).
- Coolant links are blue; power links stay cyan rather than the Notion "yellow".
- All numbers are starting values for tuning.

## Hearthstone Check world

`python3 tools/prepare_hearthstone_playtest.py --source <level.dat> --destination "run/saves/Hearthstone Check"`
builds a flat, paused late-vacuum Creative world for the normal `./gradlew runClient`. Hearthstone A has 12
radiators and feeds a relay with lights, an induction furnace, two RH-5 heaters, a battery bank and a monitor.
Hearthstone B has 4 radiators for the auto-SCRAM and meltdown drills. The player gets a full build kit.
Clickable chat steps cover start-up, load-follow, radiators, auto-SCRAM, meltdown, building, fuel chain and
save/reopen. Coolant and power links are pre-written into `frozendawn_power_network.dat`.

## Status (2026-10-15)

Owner playtest in Hearthstone Check accepted: start-up and steady state with 12 radiators, hum and alerts,
load-follow with flow control, auto-SCRAM and reset, the disable-SCRAM screens and manual recovery, meltdown with
melt-through and a steam explosion, building a reactor from the kit, induction-only refining at 8 kW and the new
ore/item art, and save/reopen. Fixes made during the playtest: villages disabled in the test world generator, a
running radiator clears frozen atmosphere, damage derates power instead of heat, coolant flow control, reset rules
and SCRAM latch text, UI layout. Java 21 `./gradlew architectVerify`: 786 unit tests, 540 native GameTests, all
531 required reports. Not yet merged; merge `feat/power-network` into `feat/maeve-director` first (merge commit).

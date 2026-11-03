# SFR-1 Solstice Reactor visual check

Owning checkout: `/Volumes/SSD/Work/frozen-dawn-solstice`, branch `feat/solstice-reactor`, cut from `feat/maeve-director` at `866ca4c4`. Launch with Java 21 and `./gradlew runClient --console=plain`. No lab bridge function is needed.

When this check was written, the core reactor blocks were creative tab and `/give` only, with no recipes, loot or Fabricator. `feat/helion-sites` adds recipes for every part except the Confinement Core, which comes from Helion sites; see [helion-sites-playtest.md](helion-sites-playtest.md). The fuel chain (below) adds spodumene world generation, Lithium Carbonate smelting, battery salvage, the Lithium Target Rod recipe and Helium Canisters; the Isotope Column is creative only. Use a Creative world you are happy to build in; no fixture world is generated.

## Give list

```
/give @s frozendawn:sfr_controller 1
/give @s frozendawn:confinement_core 1
/give @s frozendawn:magnet_coil 128
/give @s frozendawn:first_wall 48
/give @s frozendawn:lithium_blanket 48
/give @s frozendawn:coolant_port 2
/give @s frozendawn:sfr_fuel_port 1
/give @s frozendawn:sfr_power_port 1
/give @s frozendawn:sfr_vacuum_port 1
/give @s frozendawn:sfr_maintenance_hatch 1
/give @s frozendawn:high_temperature_radiator 14
/give @s frozendawn:deuterium_canister 16
/give @s frozendawn:tritium_canister 8
/give @s frozendawn:battery_bank 4
/give @s frozendawn:power_cell[frozendawn:cell_joules=500000] 16
/give @s frozendawn:conduit_binder 1
```

Exact shell: 122 coil cells and 48 Blanket cells before ports (26 solenoid, 48 caps, 48 outer ring), 48 First Wall. Each port replaces one outer-ring coil or Blanket cell; the hatch replaces two.

## Build order

The torus is 9x9 and 5 high (Y1 to Y5, middle Y3). Rings are squares measured from the center column.

1. Place the Controller where the middle of one outer face will be, facing outward. The center is 4 blocks behind it. Placing it shows the blueprint: a pulsing translucent ghost of the right block in every empty cell, a red box on any cell holding the wrong block or filling the channel, and the parts still needed in chat. Ghosts disappear as you fill them. Use the Controller (not sneaking) to hide or show it again; it hides itself once the torus is complete. Sneak-use opens the screen. Steps 2 to 5 below are what the ghosts show.
2. Center column and the ring around it (r = 0, 1), Y2 to Y4: Magnet Coils, with the Confinement Core at the very center on Y3.
3. r = 2, Y2 to Y4: First Wall.
4. r = 3, Y2 to Y4: leave as air. This is the plasma channel. Cap it with Magnet Coils at Y1 and Y5 over r = 3 only.
5. r = 4, Y2 to Y4: alternate Magnet Coil and Lithium Blanket in a checkerboard. Corners are always shell.
6. Ports and hatch: the blueprint ghosts one layout on the Controller's face (hatch two cells to one side, a Coolant Port at each end, Fuel Port over Power Port on the other side, Vacuum Port above the Controller). Any non-corner r = 4 cell at Y2 to Y4 works; a suggestion disappears once that part is anywhere on the ring. A suggested cell already holding shell keeps a red box wrapped in the part's ghost. The hatch's lower half is at Y2, upper at Y3, opening into the channel.
7. Seal: the Controller's top line reads the shell valid, or names the first wrong part.

## Linking

- Place the 14 High-Temperature Radiators within 16 blocks, each with one uncovered outdoor face. Snow covers a face.
- With the Conduit Binder, link each radiator to a Coolant Port (port to radiator; links render blue).
- Fill each battery bank with 4 charged cells (2 MJ per bank) and link the banks to the Power Port. A start needs about 2.4 MJ for cooldown and 1.5 MJ for ignition, so four banks cover two starts.
- Put Deuterium Canisters in the Fuel Port (hoppers work) and Tritium Canisters in the Controller slots (1 canister = 1 unit, tank 8).
- The Vacuum Port pumps the channel; it exhausts into the room on its outer face, and its Vent mode equalizes the channel with that room.
- In the vacuum stage the hatch will not open across a pressure difference with the room on its own outer face. A channel sealed in open vacuum starts as vacuum.

## Startup

1. Press Start. Cooldown draws 20 kW for 2 minutes, halved when the Controller's room is a vacuum. Pulling power aborts with no damage.
2. READY needs a Deuterium Canister, at least 1 tritium, channel at or below 5% pressure and the hatch closed. Otherwise it shows the reason (TORUS OPEN, NO VACUUM, NO FUEL).
3. Press Start again. Ignition draws 150 kW for 10 s and consumes 1 tritium. Any second short aborts back to READY.
4. BURNING. The 40 kW house load comes from the reactor's own output.

Expected at 14 radiators against deep vacuum (-200C), full output: loop about 848C, net about 329 kW. The burn reaches 100% about 16 s after ignition and the loop is within 5% of steady state after about 3.5 minutes (20 radiators at 150%: about 2.5 minutes). Warmer sinks read lower. During warm-up net output is negative; that is the spec's behavior.

## Overdrive

The DMS button (Disruption Mitigation System) starts On, which holds drive at 100% and ends an over-temperature plasma as an ordinary disruption. Turning it off takes a 3 s hold on a warning overlay. With it off:

- The Drive slider reaches 150% in 10% steps. Heat scales with drive; the loop runs hotter, so Carnot efficiency and net output rise.
- Every second above 100% erodes the First Wall: at 150%, one wear step every 5 minutes.
- Nothing catches an over-temperature plasma. Past 1100C the status reads WALL MELT-THROUGH, every First Wall turns to magma, the coils quench, and the torus will not seal until the wall is rebuilt. This replaces one wear step; it can happen at 100% drive too while DMS is off.
- Turning DMS back on drops drive to 100% at once; the burn slews down.

Expected against deep vacuum: 150% with 14 radiators settles near 1370C, so it melts: from a cold start the alarm sounds after about 97 s and the wall melts about 21 s later. About 19 radiators hold 150% under 1000C; 20 settle near 900C for about 518 kW net.

## Shutdown

Design: `docs/solstice-reactor-design.md`, section "Shutdown". Plan: `docs/solstice-shutdown-plan.md`.

1. While BURNING, press Stop. The fuel valve thunks shut and the state reads RAMP DOWN (cyan) with a progress percent. The burn falls on a straight line to zero over 20 s; the burn loop fades and drops in pitch with it. No fuel is used and no tritium is bred during the ramp. As gross output falls below the 40 kW house load, the network covers the rest; on a network with no other supply or stored energy the ramp ends in a MAGNET QUENCH instead, as it would while burning.
2. At 100% the shutdown chime plays and the screen reads OFF, SHUT DOWN (grey). No First Wall wear, no disruption crack.
3. Press Start during the ramp: it returns to BURNING without a new cooldown or ignition, at the burn it had reached (at least the minimum burn).
4. E-Stop (red, beside Stop) is live only while the plasma is on. Hold it for one second; a red bar fills under it. Releasing early does nothing. A full hold is a hard stop: DISRUPTION, the crack, and one wear step, as before. Redstone and opening the hatch are still hard stops, also during the ramp.

## Judge by eye and ear

1. Block textures: a clean fusion-lab look (pale ceramic and steel, ORSA blue and orange accents), each block distinct from the CFU-7 and from each other. The Deuterium and Tritium Canisters were redrawn on 2026-10-24.
2. The blueprint: ghost readability against snow and in the dark, the pulse, red boxes, the parts list in chat, toggling, and that it clears itself on completion. Judge whether it is as easy to follow as a Create or Immersive Engineering build guide.
3. The Controller screen: state, status colors, progress percent, loop, heat, power, request, radiators, fuel and channel lines.
4. TORUS OPEN with the hatch open; Start refused. Opening the hatch while burning disrupts at once.
5. Quench frost after a MAGNET QUENCH (cut the network while gross output is under 40 kW).
6. The twelve sounds, each for distinctness, loudness and distance falloff:
   - cryocooler loop during cooldown and READY
   - ignition charge and thump (cut off if ignition aborts)
   - ignition success swell and arpeggio as IGNITION turns to BURNING
   - burn loop (sub throb, magnet hum, field choir, plasma roar, ion shimmer, crackles), louder and higher with output
   - pump loop at the Vacuum Port, rising in pitch as the channel empties
   - warning alarm while burning: loop at or above 1000C or pump off. Overdrive alone is quiet. Warm-up on network power is silent.
   - READY chime; refusal buzz when you press Start and it is refused, or when ignition aborts (a standing refusal nobody pressed is silent); disruption crack; quench vent; a melt-through plays the crack and the vent together; disabling DMS plays a one-shot rising triple whoop
   - fuel valve thunk on Stop, and the two-tone shutdown chime when the ramp ends
7. Linked radiators turn HOT while the loop is at or above 150C, buried or not, and cool when it drops.
8. Breaking the Controller drops its tank as whole Tritium Canisters.
9. Overdrive: the DMS overlay and hold, its alert when the hold completes, the Drive slider, silence in overdrive below 1000C, and a melt-through (magma wall, lava particles, quench frost, both cues).

## Fuel chain

Design: `docs/solstice-fuel-design.md`. Plan: `docs/solstice-fuel-plan.md`.

```
/give @s frozendawn:isotope_column 1
/give @s frozendawn:lithium_target_rod 1
/give @s frozendawn:raw_spodumene 8
/give @s frozendawn:lithium_carbonate 8
/give @s frozendawn:helium_canister 4
/give @s minecraft:packed_ice 16
/give @s frozendawn:fuel_rod 3
```

1. Pods: create a fresh world, dig or spectate below Y -8 in deepslate, and look for pale Pegmatite pods with lilac Spodumene Ore inside. Note roughly how many chunks you searched before the first pod. Mine the ore with an iron pickaxe for Raw Spodumene.
2. Smelting: Raw Spodumene in a furnace or blast furnace gives Lithium Carbonate.
3. Salvage: a Power Cell alone in the crafting grid gives 1 Lithium; a Battery Bank gives 4.
4. Target rod: craft `iron / lithium / iron`, `lithium / copper / lithium`, `iron / lithium / iron`. It should look like a fuel rod with silver-white pellets. In a running CFU-7 with three fuel rods, the screen should rate one rod (35 kW) lower; the rod's tooltip shows breeding progress, and when used up it leaves 4 Tritium Canisters in the output slot.
5. Isotope Column: place it on a powered network. Packed Ice in the input fills the ice buffer and makes a Deuterium Canister every 120 s; Lithium Carbonate makes Lithium every 60 s. The front glass should brighten with bubbles and a lamp while working, and a quiet bubbling hum should play only then. Cut power: the screen says NO POWER, the job pauses, and the hum fades. Junk is refused by hand and by hopper.
6. Helium: with the SFR-1 burning and the Vacuum Port pumping, each Deuterium Canister burned puts a Helium Canister in the Controller's He slot (between T in and T out). Stop the reactor with helium loaded: the screen shows "He boost on Start", right-aligned in the panel, and starting the cooldown uses one canister and shows "He boost ×2"; the cooldown runs twice as fast.
7. Textures: Helium Canister (brown cap), Lithium Target Rod, Raw Spodumene, Lithium Carbonate, Lithium, Pegmatite, Deepslate Spodumene Ore and the four Isotope Column faces.

## Owner reports

- First look (2026-10-24), before the port layout ghosts: "its not clear where the port and hatch need to go."
- Pump-down (2026-10-24): "i can hear it slowly \"spinning\" up". Matches the pump loop, whose pitch rises from 0.8 to 1.2 as the channel empties.
- Cooldown (2026-10-24): "i heard the chim". The READY chime, played when cooldown finishes.
- First ignition (2026-10-24), 8 MJ banked: "huh why do i hear a warning sound?" Screenshot 2026-10-24_01.07.36: BURNING, loop 238C, heat 400 kW, removed 75.6 kW, gross 29.7 kW, net -15.3 kW, 14 HT radiators, channel 0%. The alarm is the net < 0 trigger during loop warm-up, which every normal start passes through. Owner decision: drop that trigger, play a success sound at ignition, and make the burn loop sound like fusion.
- Overcharge request (2026-10-24): "can you make it so you can overcharge the reactor like how you can on the fission reactor? also look a the ss, the Ui looks a little funky". After the proposal: "but of course dont copy it 1 to 1, this is a fusion reactor, so instead have it \"meldown\" how an actual fusion reactor woukd (i guess the walls just fucking melt or something)". Built as the DMS toggle, Drive to 150% and the First Wall melt-through above.
- Heat-up pace (2026-10-24): "ngl i wouldve thought this thing would heat up a LOT faster lol, since its plasma". Then: "i wnat it so the palyer doenst need ot wait for very long, but also not instant." Loop thermal mass cut from 100 to 25 kJ/C and burn slew raised from 2% to 5% per second; settle time drops from about 13 to about 3.5 minutes.
- Overdrive check (2026-10-24), 14 HT radiators, sink about 37C, DMS on, drive 100%: "at around 1,000C im getting a two tone alarm". This is the warning alarm's loop >= 1000C trigger; at this sink, 14 radiators settle near 1085C with Follow off.
- Overdrive alarm (2026-10-24), 28 HT radiators at 150%: "i think the overdrive warning should either not be this annoying or something, thoughts?" Then: "id say have a alert sound when you disable DMS, then get rid of the overdrive alarm". Overdrive no longer triggers the alarm; disabling DMS plays a one-shot alert.
- First melt-through (2026-10-24): "good. i made it meltdown, though only the first wall turned into magma blocks? is that realistic? or". Matches the design: WALL_MELT converts only First Wall blocks.
- First pod (2026-10-24): "it sticks out, but thats intended no?" Screenshots 2026-10-24_05.08.11 (no night vision) and 05.08.14 (night vision): an exposed pod on a deepslate cave floor, spodumene laths on the left face, 7 Raw Spodumene already mined. The contrast is intended so pods can be spotted in caves. After the note that the quartz blotches tile as a regular dot pattern: "looks good". Texture kept.
- Isotope Column (2026-10-24): "overlaps but i hear everything else. check ss". Screenshot 2026-10-24_05.33.39: the "Deuterium" job label is drawn over "Ice buffer: 0 / 8 units". The two are merged into one top line ("Deuterium · ice 0 / 8", "Lithium refining", or the buffer when idle).
- Column power and airlock (2026-10-24), unprompted: "i also noticed it that its flickering between \"no power\" and working. tried getting ti but its really really fast. theres another bug that idk if its related but whenever i open the airlock doors, only after filling the chamber, all the lights and power goes out, then it comes back on again"
  Diagnosis afterwards, two separate causes. Flicker: a machine refused every tick its network's live generation sat below the once-a-second snapshot, which a reactor's output, recomputed every tick, can do from tick to tick, even with full batteries (inferred from the code, not measured in this world). The dip is now paid from the banks (`sfrColumnRidesAGenerationDipOnItsBattery`). Airlock: opening the inner door joins the chamber to the base; a joined room too large to scan in one tick reads seal UNKNOWN for a few ticks, and every link through it was dropped as if it had failed, until some later block or chunk change re-queued it. An undecided seal now keeps the link's state and re-checks it five ticks later (`powerLinksStayLiveWhileAJoinedRoomIsStillScanning`). The small airlock scene did not reproduce the blink, so the room-size cause is inferred for this base; both need an owner recheck in a restarted client.
- Recheck in a restarted client (2026-10-25), unprompted: "ok isotope column works, but the flicker is still broken."
  Clarified right after: "ah crap sorry i wasnt clear. i mean the LIGHT flicker". So the Column works, and the airlock blackout of lights and power is still open; the room-scan fix did not remove it in this base.
- Logged reproduction (2026-10-25), unprompted: "ok even when i first loaded in, it happened. also its when i open the inner door. reproduced twice, once for when i first loaded in".
  The uncommitted per-load log showed links stable (37 active) and stored energy 0. In each blackout second the heater at 459,63,2950 sensed 11-12 C instead of 20 C, requested its full 50 kW, and the second heater at 459,63,2952 joined the same room and asked for 37-44 kW. Heaters are served before lamps, so demand reached 98-111 kW on a 78 kW supply and 14 loads were shed, including the 12 lamps (1.2 kW) and the Isotope Column (10 kW). The next second the sensed temperature was 20 C again and the lamps returned.
  Owner's reading: venting leaves the chamber at vacuum temperature, and the refilled air was cold enough to chill the base when the inner door opened. The code confirmed it: a completed airlock refill created the chamber air at the outside average temperature. Owner chose to have pumped air arrive at the temperature of its source room and to leave the load priority tiers (PIXIE, airlock, heaters, then lights) unchanged. `RoomThermalManager.refillFrom` now seeds airlock refill air at the base room's air temperature; `airlockRefillAirArrivesAtBaseTemperature` failed before the fix (chamber 11.5 C against a 20 C base) and passes after it. A genuine shortage still sheds lights below heaters by design.
  Visual recheck (2026-10-25), unprompted: "still flickered. the air in the chamber after presurizing is only like -100 for some reason." Screenshot `run/screenshots/2026-10-25_01.13.46.png` shows -99 C at "Chamber equalized".
  The refill diag showed the seed working (air 20 C at refill) but the chamber walls at -90.5 C with heat capacity 468 against 8 for the air; one second later air and walls were both -89 C. Opening the door merged the cold walls into the base, and the heaters again jumped toward 50 kW each. Before the door the single base heater drew about 5 kW; supply was 78 kW with 11.2 kW of lamps and Column.
  Owner chose heater ramp-up (option A) over warming the walls during pressurizing or slower vacuum wall cooling. `ResistiveHeaterBlockEntity.RAMP_WATTS_PER_SECOND` (5 kW/s) caps how far a heater's request can rise each second; dropping stays immediate and tier order is unchanged. `rh5RampsUpInsteadOfJumpingToFullPower` failed before (50 kW in the first second) and passes after.
  Visual recheck (2026-10-25), owner: "Lights stayed on when I opened the inner door." The airlock blackout is resolved.
- CFU-7 rods line (2026-10-25), during the target rod step: "check ss. its going off the UI." Screenshot `run/screenshots/2026-10-25_01.32.14.png`: "Rods: 3 + 1 target · 105.0 kW · Damage 0%" ran about 205 px into a 156 px panel and under the buttons. Damage now sits right-aligned under the state (NOMINAL), and the rods line keeps the count and rating.
- SFR-1 in vacuum (2026-10-25), owner request: "also make it so all SRF-1 sounds ignore vacuum". Every `blocks.sfr_` cue, including the Isotope Column hum, now skips the vacuum-stage cut in `SoundMuffler`.
  Recheck in a restarted client (2026-10-25), owner: "looks good". Then: "fuel loop is done".
- Shutdown ramp, E-Stop and the right-aligned He boost label (2026-10-25), owner: "everything looks good". Asked whether toggling the Vacuum Port should need sneak + right-click: "no for the vacuum port". Plain right-click stays.

## Known limitation

Outside the vacuum stage, a player standing in a pumped-down channel still breathes, because room breathing is phase-gated. This is the owner's decided trade-off for working in any phase.

## Evidence and limits

Headless, verified by tests: multiblock validation, the full blueprint cell list and its per-player toggle, port rules, channel pressure and pump-down into the outer room, startup sequence and aborts, ignition at 150 kW, burn physics and the 14-radiator readings, load-following, fuel burn and 1.05x breeding, disruptions and quench, saved state, cue selection and alarm rule, drive and mitigation limits, overdrive heat, efficiency and wall erosion, the unmitigated melt-through to magma, radiator HOT state, hatch-side pressure and vacuum-stage sealing, the Stop ramp-down to SHUT DOWN with no wear, fuel or breeding, Start cancelling a ramp, E-Stop and redstone as hard stops during the ramp, a ramp surviving save and load, tritium drop on break, sound state sync to the client, and that every SFR sound file is mono Vorbis and registered. Fuel chain: Column job math, ice units, power-loss pause and saved progress, hopper refusal, salvage recipes, pod placement only in deepslate with ore only inside pegmatite, target rod rating, breeding and output blocking, helium collection and venting when full, and the boosted cooldown rate.

Needs the owner: textures, ghost-block blueprint rendering, frost visuals, screen layout, the DMS overlay and Drive slider, the melt-through's look, every sound's character and mix, hatch feel, Conduit Binder linking in a real world, and the warm-up alarm ruling. Fuel chain: the owner ran the whole loop in a playtest world on 2026-10-25; how rare pods feel in a fresh world is still open (step 1 asks for a fresh world). Multiplayer is not checked.

Heat tick lag (0c2898ef): headless tests show the cached wall shares match the old map code bit for bit, and an in-game A/B on one scene ran about 20x faster. Owner recheck on the Lag Check save (2026-10-26): "it looks good. architect seemed good". The slow-tick recorder agrees: ticks over 100 ms fell from 4.9% to about 0.6%, mean tick from 22.4 ms to about 15 ms, and no slow tick came from the room heat loop. Two one-off spikes remain outside this fix: an 881 ms ThermalVentSystem volcano rebuild after rejoining, and a 347 ms room rebind on load.

SFR advancements (ac022506): owner playtest on the Lag Check save (2026-10-26): "i got pretty much all of them except for lawson". The client log shows Breakeven and Fission, Fusion, Fire on a reactor already burning when the owner joined, A Second Sun and Cold Fusion on an ignition, Mass Fusion 2 s later, and Twenty Years Away on an aborted ignition, with no errors. Lawson Criterion needs 1200 s (about 20 real minutes) of unbroken plasma; the session lasted about 4 minutes and the plasma went out between ignitions, so it was untested in game then. Owner recheck on the same save (2026-10-26) after a burn of about 20 unpaused minutes: "got lawson." The client log shows Lawson Criterion granted at 09:34:03 with no errors. Mind the Quench, Helium Voice and Borrowed Daylight wait on quench helium and the Exodus fusion tie.

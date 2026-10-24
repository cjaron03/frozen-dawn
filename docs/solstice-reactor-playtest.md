# SFR-1 Solstice Reactor visual check

Owning checkout: `/Volumes/SSD/Work/frozen-dawn-solstice`, branch `feat/solstice-reactor`, cut from `feat/maeve-director` at `866ca4c4`. Launch with Java 21 and `./gradlew runClient --console=plain`. No lab bridge function is needed.

This slice is the core reactor only: every block and item is creative tab and `/give` only, with no recipes, loot, Fabricator or Helium Canisters. Use a Creative world you are happy to build in; no fixture world is generated.

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

Expected at 14 radiators against deep vacuum (-200C), full output: loop about 848C, net about 329 kW. Warmer sinks read lower. During warm-up net output is negative; that is the spec's behavior.

## Judge by eye and ear

1. Block textures: a clean fusion-lab look (pale ceramic and steel, ORSA blue and orange accents), each block distinct from the CFU-7 and from each other. Item canisters are still placeholders.
2. The blueprint: ghost readability against snow and in the dark, the pulse, red boxes, the parts list in chat, toggling, and that it clears itself on completion. Judge whether it is as easy to follow as a Create or Immersive Engineering build guide.
3. The Controller screen: state, status colors, progress percent, loop, heat, power, request, radiators, fuel and channel lines.
4. TORUS OPEN with the hatch open; Start refused. Opening the hatch while burning disrupts at once.
5. Quench frost after a MAGNET QUENCH (cut the network while gross output is under 40 kW).
6. The ten sounds, each for distinctness, loudness and distance falloff:
   - cryocooler loop during cooldown and READY
   - ignition charge and thump (cut off if ignition aborts)
   - ignition success swell and arpeggio as IGNITION turns to BURNING
   - burn loop (sub throb, magnet hum, field choir, plasma roar, ion shimmer, crackles), louder and higher with output
   - pump loop at the Vacuum Port, rising in pitch as the channel empties
   - warning alarm while burning: loop at or above 1000C or pump off. Warm-up on network power is silent.
   - READY chime; refusal buzz when you press Start and it is refused, or when ignition aborts (a standing refusal nobody pressed is silent); disruption crack; quench vent
7. Linked radiators turn HOT while the loop is at or above 150C, buried or not, and cool when it drops.
8. Breaking the Controller drops its tank as whole Tritium Canisters.

## Owner reports

- First look (2026-10-24), before the port layout ghosts: "its not clear where the port and hatch need to go."
- Pump-down (2026-10-24): "i can hear it slowly \"spinning\" up". Matches the pump loop, whose pitch rises from 0.8 to 1.2 as the channel empties.
- Cooldown (2026-10-24): "i heard the chim". The READY chime, played when cooldown finishes.
- First ignition (2026-10-24), 8 MJ banked: "huh why do i hear a warning sound?" Screenshot 2026-10-24_01.07.36: BURNING, loop 238C, heat 400 kW, removed 75.6 kW, gross 29.7 kW, net -15.3 kW, 14 HT radiators, channel 0%. The alarm is the net < 0 trigger during loop warm-up, which every normal start passes through. Owner decision: drop that trigger, play a success sound at ignition, and make the burn loop sound like fusion.

## Known limitation

Outside the vacuum stage, a player standing in a pumped-down channel still breathes, because room breathing is phase-gated. This is the owner's decided trade-off for working in any phase.

## Evidence and limits

Headless, verified by tests: multiblock validation, the full blueprint cell list and its per-player toggle, port rules, channel pressure and pump-down into the outer room, startup sequence and aborts, ignition at 150 kW, burn physics and the 14-radiator readings, load-following, fuel burn and 1.05x breeding, disruptions and quench, saved state, cue selection and alarm rule, radiator HOT state, hatch-side pressure and vacuum-stage sealing, tritium drop on break, sound state sync to the client, and that all nine sound files are mono Vorbis and registered.

Needs the owner: textures, ghost-block blueprint rendering, frost visuals, screen layout, every sound's character and mix, hatch feel, Conduit Binder linking in a real world, and the warm-up alarm ruling. Multiplayer is not checked.

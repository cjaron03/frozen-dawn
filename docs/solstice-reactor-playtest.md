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

1. Place the Controller where the middle of one outer face will be, facing outward. The center is 4 blocks behind it.
2. Center column and the ring around it (r = 0, 1), Y2 to Y4: Magnet Coils, with the Confinement Core at the very center on Y3.
3. r = 2, Y2 to Y4: First Wall.
4. r = 3, Y2 to Y4: leave as air. This is the plasma channel. Cap it with Magnet Coils at Y1 and Y5 over r = 3 only.
5. r = 4, Y2 to Y4: alternate Magnet Coil and Lithium Blanket in a checkerboard. Corners are always shell. The Controller's ghost outline shows the expected block for any wrong cell.
6. Ports on any non-corner r = 4 cell at Y2 to Y4: 2 Coolant Ports, Fuel Port, Power Port, Vacuum Port. The Maintenance Hatch also goes on r = 4, lower half at Y2, upper at Y3, opening into the channel.
7. Seal: the Controller's top line reads the shell valid, or names the first wrong part.

## Linking

- Place the 14 High-Temperature Radiators within 16 blocks, each with one uncovered outdoor face. Snow covers a face.
- With the Conduit Binder, link each radiator to a Coolant Port (port to radiator; links render blue).
- Fill each battery bank with 4 charged cells (2 MJ per bank) and link the banks to the Power Port. A start needs about 2.4 MJ for cooldown and 1.5 MJ for ignition, so four banks cover two starts.
- Put Deuterium Canisters in the Fuel Port (hoppers work) and Tritium Canisters in the Controller slots (1 canister = 1 unit, tank 8).
- The Vacuum Port pumps the channel; it exhausts into the room on its outer face.

## Startup

1. Press Start. Cooldown draws 20 kW for 2 minutes, halved when the Controller's room is a vacuum. Pulling power aborts with no damage.
2. READY needs a Deuterium Canister, at least 1 tritium, channel at or below 5% pressure and the hatch closed. Otherwise it shows the reason (TORUS OPEN, NO VACUUM, NO FUEL).
3. Press Start again. Ignition draws 150 kW for 10 s and consumes 1 tritium. Any second short aborts back to READY.
4. BURNING. The 40 kW house load comes from the reactor's own output.

Expected at 14 radiators against deep vacuum (-200C), full output: loop about 848C, net about 329 kW. Warmer sinks read lower. During warm-up net output is negative; that is the spec's behavior.

## Judge by eye and ear

1. Placeholder textures for every SFR block and item, and the ghost outline on a wrong cell.
2. The Controller screen: state, status colors, progress percent, loop, heat, power, request, radiators, fuel and channel lines.
3. TORUS OPEN with the hatch open; Start refused. Opening the hatch while burning disrupts at once.
4. Quench frost after a MAGNET QUENCH (cut the network while gross output is under 40 kW).
5. The nine sounds, each for distinctness, loudness and distance falloff:
   - cryocooler loop during cooldown and READY
   - ignition charge and thump (cut off if ignition aborts)
   - burn loop, louder and higher with output
   - pump loop at the Vacuum Port, thinning as the channel empties
   - warning alarm while burning: loop at or above 1000C, pump off, or net negative
   - READY chime, refusal buzz, disruption crack, quench vent
6. Ruling to judge: the alarm sounds through warm-up, because net is negative then. Say if that is too much.

## Known limitation

Outside the vacuum stage, a player standing in a pumped-down channel still breathes, because room breathing is phase-gated. This is the owner's decided trade-off for working in any phase.

## Evidence and limits

Headless, verified by tests: multiblock validation and ghost cells, port rules, channel pressure and pump-down into the outer room, startup sequence and aborts, ignition at 150 kW, burn physics and the 14-radiator readings, load-following, fuel burn and 1.05x breeding, disruptions and quench, saved state, cue selection and alarm rule, sound state sync to the client, and that all nine sound files are mono Vorbis and registered.

Needs the owner: textures, outline and frost visuals, screen layout, every sound's character and mix, hatch feel, Conduit Binder linking in a real world, and the warm-up alarm ruling. Multiplayer is not checked.

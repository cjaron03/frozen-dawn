# ORSA SFR-1 Solstice Reactor: core loop design

Status: design, not started. Drafted 2026-10-23 on `feat/solstice-reactor`, cut from `origin/feat/maeve-director` at `866ca4c4`.

Source spec: Notion page "ORSA SFR-1 Solstice Reactor (Fusion)". Related: Notion "ORSA Fabricator", "ORSA CFU-7 Hearthstone Reactor". This document covers only the first slice of that spec and records the decisions made while scoping it.

## Intent

The SFR-1 is the endgame power tier after geothermal and the CFU-7. Each tier has its own risk: geothermal is safe and small, fission is strong and dangerous, and fusion is huge, safe and finicky. Fusion never melts down. When anything goes wrong, it stops itself (a disruption), and the cost is wall wear plus a full restart.

This slice delivers a reactor you can build, start, run and disrupt in a creative or test world. It also establishes the physics and block foundations that later slices add survival progression to.

## Slicing (decided)

The Notion spec is four projects. They ship in this order, each as its own branch and PR:

1. **Core reactor loop** (this document).
2. Fuel and world: Helion sites, Confinement Core loot, spodumene ore, Isotope Column, Lithium Target Rod in the CFU-7, Helium Canisters.
3. Quench gas: a per-room gas composition (O2 share, inert helium) so a quench dilutes room oxygen.
4. Extras: advancements, easter eggs, the Uplink reveal of Helion sites, the Exodus launch hook.

The ORSA Fabricator is a separate spec. It supplies survival recipes for the SFR-1 parts.

## Goals

- `FusionPhysics`: pure and unit-tested, modeled on `power/ReactorPhysics` with `Inputs` and `Readout` records.
- The SFR-1 multiblock, controller block entity, controller screen and the blocks listed below.
- The High-Temperature Radiator.
- The startup sequence, the burn, load-following, fuel burn and tritium breeding.
- The vacuum channel, with its Vacuum Port pump and Maintenance Hatch.
- Disruptions, First Wall wear and repair, and magnet-quench audio and visuals.

## Non-goals (later slices)

- Survival recipes, loot or world generation for any SFR-1 block or fuel.
- Isotope Column, spodumene, lithium items and the Lithium Target Rod.
- Helium Canisters and their halving of magnet cooldown.
- Neutron radiation, the neutron shielding table and First Wall activation. Shielded Cask storage of worn tiles.
- Quench O2 dilution and helium as an inert room gas.
- Advancements, easter eggs (including "FUSION: 20 YEARS AWAY"), the Uplink reveal and the Exodus hook.

## Anti-goals

- No meltdown, explosion, fallout or contamination from fusion, ever.
- No temporary survival recipes or loot that a later slice would have to remove.
- No changes to CFU-7 behavior or balance.

## Availability (decided)

Every block and item in this slice is creative tab and `/give` only, with no recipes and no loot: Confinement Core, Deuterium Canister, Tritium Canister, Magnet Coil, First Wall, Lithium Blanket, High-Temperature Radiator, the SFR-1 Controller and every port. Survival access comes with the Fabricator and the fuel slice.

## Structure (decided): 9x9x5

The spec's four-ring layout needs 11x11. The owner chose to keep 9x9x5 and drop the outer First Wall, so the Blanket ring faces the plasma.

Layers Y1 to Y5, with Y3 as the middle layer. Rings are square, measured as Chebyshev distance `r` from the center column.

| Ring | Y2 to Y4 | Cells per layer |
| --- | --- | --- |
| r = 0 | Confinement Core at Y3; Magnet Coil at Y2 and Y4 | 1 |
| r = 1 | Magnet Coil (Central Solenoid) | 8 |
| r = 2 | First Wall (faces plasma) | 16 |
| r = 3 | Plasma channel: air, hollow, 3 high | 24 |
| r = 4 | Lithium Blanket, with a Magnet Coil (toroidal field coil) on every other cell; ports replace cells here | 32 |

**Y1 and Y5 (builder's call within the spec):** Magnet Coil caps over the channel ring (r = 3) only, 24 per layer. Those caps form the channel's floor and ceiling. Cells at Y1 and Y5 for r ≠ 3 are not part of the structure.

**Ports on the outer face (r = 4), replacing Blanket or coil cells:**
- 1 SFR-1 Controller
- 2 Coolant Ports
- 1 Fuel Port
- 1 Power Port
- 1 Vacuum Port
- 1 Maintenance Hatch

The hatch is an airlock-style door in the r = 4 ring that opens straight into the channel. Its exact height and the rules for where ports may sit are the builder's call.

**Approximate counts:**

| Block | Count | Breakdown |
| --- | --- | --- |
| Magnet Coil | about 120 | 26 solenoid + 48 caps + about 48 toroidal |
| First Wall | 48 | |
| Lithium Blanket | about 48 | minus port cells |

These exceed the spec's rough cost of about 60 coils, 80 First Wall and 50 Blanket. The owner should check the coil count before the Fabricator prices the parts.

**Validation:** works like `ReactorMultiblock.validate` and reports the first wrong cell. The controller shows missing blocks with a ghost outline, like the airlock.

## Vacuum channel (decided)

- **Channel pressure (decided):** the controller tracks the channel's pressure itself and saves it with the controller, so the SFR-1 works in any phase and dimension. Room gas tracking (`RoomAtmosphere`, `RoomAirState`, `AirlockSavedState`) runs only during the Phase 6 vacuum stage in the Overworld (`CombustionAtmosphere.isVacuum`).
  - **Outside the vacuum stage:** the channel starts at full pressure when sealed. The pump lowers it, venting or opening the hatch raises it back, and pumped gas is simply exhausted.
  - **During the vacuum stage:** the channel's pressure comes from the room gas system (the room's gas from `AirlockSavedState.roomGas` divided by `cells * AirlockCycle.UNITS_PER_CELL`), and pumped gas goes into the outer room as described below.
  - **Phase transitions:** how pressure is handed over when the world crosses into the vacuum stage with a channel already sealed is the builder's call. It must never create or destroy a vacuum without a pump, vent or breach.
- **Vacuum Port:** has its own powered pump on the power network. It removes gas from the channel and exhausts it into the room on the port's outer face through that room's existing air-receive path. Gas that room cannot accept is lost.
  - Pump-down draw and rate are the builder's to tune. A starting point: 10 kW, emptying a full channel in about 2 to 3 minutes.
  - While burning, the pump keeps running at about 5 kW to clear helium ash.
- **Maintenance Hatch:** reuses the airlock door's seal behavior. Closed, it seals the channel as its own room. Open, the controller shows TORUS OPEN and priming is blocked.
- **Ignition** is refused above 5% of nominal pressure.
- **Built in vacuum:** a channel built in an already-vacuum area is vacuum as soon as it is sealed.
- **Breach while burning:** any breach (a mined or broken shell block, the hatch opened, pressure admitted) causes an instant disruption.
- **Helium ash:** if the pump stops while burning, the plasma fades over about 60 s and the reactor ends in ASH POISONED. This is a soft disruption with no wall damage. Ash is not stored or output in this slice.

## High-Temperature Radiator

- A new block, linked to the SFR-1 Coolant Ports the same way `ReactorRadiatorBlock` links to CFU-7 ports.
- 30 kW at a 900C loop against deep vacuum (-200C), linear in temperature difference: `30 / 1100` kW per C. About 14 are needed for 400 kW.
- Sink temperature follows the existing radiator's rules (buried or exposed).
- Standard ORSA Reactor Radiators linked to an SFR-1 do not count as sinks, since they cap at 150C. Whether a High-Temperature Radiator can serve a CFU-7 is the builder's call; the default is no.

## Startup sequence

1. **Cooldown:** the magnets draw 20 kW from the network for 2 minutes. The time halves when the controller's room is a vacuum. Losing power aborts cooldown with no damage.
2. **Fuel check:** at least one Deuterium Canister in the Fuel Port and at least 1 tritium unit in the tank. Channel at 5% pressure or below, hatch closed.
3. **Ignition pulse:** 150 kW from the network for 10 s, drawn from generation plus stored energy (see `PowerNetworks.priorityBudget`). Any second under 150 kW aborts with no damage and returns to the fuel-check state.
   - Announcing the pulse as scheduled load (as `UplinkConsoleBlockEntity.scheduledWatts` does) lets a load-following CFU-7 ramp up first.
   - Ignition consumes 1 tritium unit.
4. **Burning:** self-sustaining. The 40 kW house load (magnets and plasma heating) comes out of the reactor's own gross electric output. When gross output falls below 40 kW, the network must cover the shortfall. If it cannot, that is loss of magnet power and causes a MAGNET QUENCH.

Status strings: IGNITION, BURNING, DISRUPTION, MAGNET QUENCH, TORUS OPEN, ASH POISONED. Strings for the cooldown and ready states are the builder's call.

## Shutdown (owner, 2026-10-25)

Two stops. The owner described the controlled one: "they push a button, a audibile THUNK starts as the fuel ramps down, then the reactor sound slowly winds down", and "when it ends a shutdown chime". The hard stop keeps its own on-screen button behind a short hold.

**Stop (ramp-down), no wear:**
1. Pressing Stop while BURNING enters a new state, RAMP DOWN. The fuel valve closes with a heavy one-shot THUNK.
2. The burn falls linearly from wherever it is to zero over a fixed 20 s, whatever the starting level. Drive and load-follow are ignored during the ramp.
3. The valve is closed, so the ramp burns no deuterium or tritium and breeds none. Deuterium progress is kept for the next burn.
4. Heat, gross and net output follow the falling burn under the usual rules: the radiators keep removing loop heat, the house load still applies, and the network covers any shortfall.
5. At zero the reactor goes to OFF with a new status, SHUT DOWN, and plays a calm shutdown chime. First Wall wear is unchanged. A restart needs the full sequence: cooldown, fuel check, ignition. The owner chose OFF over READY: "a restart pays the cooldown or uses a Helium Canister."

**During the ramp:**
- Start cancels it: the reactor returns to BURNING at the current burn, but no lower than the minimum burn, and slews toward drive as usual. No ignition or tritium is needed.
- Stop again does nothing.
- Every hard trigger still applies: redstone, E-Stop, hatch open, shell broken, channel pressure, loop over 1,100C and magnet power loss. Each causes a disruption with its usual wear.
- The ash rule still applies: with the pump off, the plasma fades into ASH POISONED as it does while burning.

**E-Stop (hard stop):**
- A red E-Stop button shares a row with Stop as two half-width buttons. It needs a 1 s hold, with fill shown on the button, and is active only while BURNING or in RAMP DOWN.
- It does what Stop does today: an immediate DISRUPTION with one wear step.
- A redstone signal stays a hard stop, the same as E-Stop.
- Stop from COOLDOWN or READY is unchanged: power down to OFF, no wear.

**Sound:**
- THUNK at the start of the ramp and the chime at its end are new one-shots. They are synthesized in `tools/generate_sfr_sounds.sh` like the other SFR-1 sounds, and like them they carry through vacuum.
- The burn loop keeps playing through RAMP DOWN. Its pitch and volume fall with the burn, reaching silence at the end instead of the loop's usual floor.
- The chime is distinct from the alarm, refuse and disruption cues.

**Screen:** the state reads RAMP DOWN, with ramp progress % beside the status the way cooldown and ignition progress are shown.

**Builder's calls:** the no-pump alarm sounds during RAMP DOWN as it does while burning; no overdrive wear accrues during the ramp, since the plasma is only falling; a ramp saved mid-way resumes where it left off on load.

## Running physics

- Rated fusion heat: 400 kW thermal. The nominal core loop is 900C.
- Electric output = `heat * ReactorPhysics.efficiency(loopC, sinkC) * (1 - damage)` minus the 40 kW house load. Net output can be negative at low output or with a thin radiator field.
- Load-following like the CFU-7: the fueling rate slews toward the network target from `PowerNetworks.loadFollowTargetWatts`. The minimum sustained burn fraction and the slew rate are the builder's to tune.
- The loop temperature has a heat capacity, so it rises when heat in exceeds radiator removal. The capacity value is the builder's to tune.
- Redstone signal: a stop trigger. The comparator output scales with the loop temperature.

### Fuel (starting values from the spec; the builder calibrates)

| Value | Start |
| --- | --- |
| Deuterium burn | 1 canister per 10 min at full output, scaled by output |
| Tritium burn | 1 unit per 20 min at full output, scaled by output |
| Tritium tank | 8 units |
| Blanket breeding | 1.05x the tritium burn rate while burning |
| Ignition cost | 1 tritium unit |

- Deuterium Canisters go into the Fuel Port, which works with hoppers and pipes.
- Tritium Canisters load into the tank: 1 canister = 1 unit, builder's call.
- Surplus tritium can be extracted from the controller as Tritium Canisters.
- Fuel starvation causes a disruption.
- Blanket damage does not exist in this slice, so breeding stays at 1.05.

## Disruptions

**Triggers:**
- loss of magnet power (a MAGNET QUENCH)
- loop temperature above 1,100C
- fuel starvation
- First Wall damage above 80%
- redstone stop or E-Stop (Stop is a ramp-down; see Shutdown)
- breach of the channel while burning

**On disruption:**
- The plasma ends instantly and output drops to zero.
- First Wall takes +5% damage. ASH POISONED is the exception and does no wall damage.
- A full restart is required: cooldown, fuel check, ignition.

**First Wall wear (builder's call within the spec):**
- Wear is stored per First Wall block as a blockstate step of 0 to 20, where each step is 5%.
- Each disruption adds one step to every First Wall block in the shell.
- Reactor damage is the mean wear.
- Mining a worn block yields nothing in this slice, because activated tiles and the Shielded Cask come later. Placing a fresh First Wall block repairs that cell, so repair costs blocks.
- Ignition is refused above 80% damage.

**Magnet quench in this slice:**
- It is a disruption with loud venting audio over 5 seconds and frost particles on the coils.
- It does not change room O2. That waits for the quench gas slice.

## Controller screen

Modeled on `client/ReactorControllerScreen`. It shows:
- state and status string
- loop temperature
- gross and net electric output, and the house load
- thermal output and radiator count
- deuterium count and tritium tank level
- channel pressure %
- First Wall damage %
- cooldown and ignition progress

It also has a start control and tritium extraction. Ambient hum and alarm audio follow the CFU-7 pattern. The layout is the builder's call.

## Tests

**`FusionPhysics` unit tests:**
- ignition succeeds at 150 kW and aborts below
- loop above 1,100C disrupts
- each disruption trigger fires, and every disruption except ASH POISONED adds 5% wear
- ash fade without pumping ends in ASH POISONED with no wear
- breeding at 1.05 sustains the tritium tank
- fuel starvation disrupts
- house-load shortfall with no network cover quenches
- net output matches the Carnot efficiency at a given radiator field

**GameTests:**
- full startup to BURNING with creative fuel and a 150 kW bank
- ignition refused above 5% channel pressure, and with the hatch open
- opening the hatch while burning disrupts
- Vacuum Port pump-down exhausts gas into the outer room
- an incomplete shell reports the first missing cell

**Gates:** `./gradlew gameTestQuick -PfdTests=<fragment>` while iterating; the full `architectVerify` before handing back. The owner does a visual playtest in a generated test world per `AGENTS.md`.

## Calls settled after the builder check

- **Hatch and re-entry:** the hatch keeps the airlock door's rule that it won't open across a pressure difference. The Vacuum Port has two modes, pump and vent. Vent equalizes the channel with the room outside the port, like `ManualVentValveBlock`, so a player can repressurize, open the hatch and replace First Wall.
- **Output basis:** electric output uses the heat the radiators actually remove, as the CFU-7 does. After ignition the loop warms from cold, and the network covers the 40 kW house load until gross output passes it. A MAGNET QUENCH happens only when the network cannot cover the shortfall. An oversized radiator field lowers the equilibrium loop temperature and therefore efficiency. Fusion has no coolant freeze or thaw state.
- **Holding the magnets cold:** after cooldown the reactor is READY and keeps drawing 20 kW. Losing that power drops it back to OFF, and cooldown is needed again. An aborted ignition is not a disruption, so it returns to READY with the magnets still cold. Every disruption, ASH POISONED included, needs a full restart.
- **Sounds:** the SFR-1 gets its own running and alert sounds rather than reusing the CFU-7, airlock or vanilla ones. The Maintenance Hatch keeps the airlock door's sounds.
- **First Wall above 80%:** wear comes only from disruptions, so this is an ignition refusal, not a trigger during a burn. Wear over burn time is not in this slice.

## Builder's calls already made in this document

- Y1 and Y5 caps over the channel ring only.
- First Wall wear as a per-block blockstate.
- Worn First Wall drops nothing.
- Standard radiators do not count on an SFR-1 loop.
- 1 Tritium Canister = 1 unit.
- House load comes out of gross output; a shortfall falls to the network.
- Cooldown aborts on power loss.
- Shutdown: a fixed 20 s ramp from any burn level; no fuel burned or bred during the ramp; Start cancels a ramp without a new ignition; E-Stop hold of 1 s; redstone stays a hard stop.

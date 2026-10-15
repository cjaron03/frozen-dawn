# ORSA RH-5 Check

Source: [ORSA RH-5 Resistive Heater](https://app.notion.com/p/3f97cfaa890181a68343f28cc5e70637) and the current [Power System additions](https://app.notion.com/p/3f57cfaa89018123a786fbb637b5ecb1), fetched October 14, 2026.

Each RH-5 converts up to 5 kW to the existing Iron heater's output: 1 kJ becomes about 514 thermal energy units (1800/3.5). This slice retains current room capacities and fuel-heater calibration. The geothermal Core's future thermal-output migration and the reactor remain separate work.

The existing room solver admits electric heat before fuel. The RH-5 uses the shared 20 C default or the room thermostat target. The power allocator serves PIXIE first, permits partial heater power, then serves ordinary devices. Battery cells pay only for heat actually delivered, with cumulative rounding bounded below one joule per heater-second. Unused reservations do not drain a cell. No prepaid electrical heat or UI readings persist. The existing topology, thermostat, cell and room state provide persistence without a new save format.

In a modeled room with gas, heat enters room air; with no gas, 25% couples into the existing structure reservoir. An unsealed room retains the existing default heater target, consistent with fuel heaters. Unknown/unloaded room geometry suspends delivery. This does not force chunks, add oxygen, change the Core allocation or accumulate offline heat.

The read-only screen shows Heating room, Warming structure (no air), Waiting for power, At target, or Checking room, plus physical temperature, target and actual draw. Target changes use the existing thermostat. The opaque native model uses an original steel cabinet/copper-element arrangement, existing original induction-furnace casing and vanilla metal textures; active elements use native orange emission. The quiet vanilla beacon hum introduces no external audio. Existing machine vibration events apply while heating.

## Owner replay

Launch the owning Java 21 Gradle `runClientLab` with `-PfdLabWorld='RH-5 Check'`. The generator refuses an existing destination and imports only world-generator metadata, never prior gameplay. Default difficulty is preselected; a world-local advancement filter suppresses awards/toasts after `mod_data`.

1. **Basic and target:** bind Core → Bank → Feedthrough → Relay → one heater, lamp and monitor. Keep cells and fuel empty; use 35% Core power. Observe up to 5 kW during the cold start, rising room temperature and falling draw near target. Change thermostat to 30 C then 10 C. Check the simple screen and cabinet/coil appearance. Core passive heat is isolated in the separate power bay.
2. **Battery and fuel:** charge/insert cells, set Core power to zero, observe charge falling and continued heat. The finite 20 kJ cell gives a short depletion test. Swap a full cell without rebinding. With coal in the Iron heater, confirm electrical heat runs first when sufficient and fuel supplies a shortfall or outage. Both may contribute during a cold start.
3. **Brownout and stacking:** link the second heater while the room is cold, remove bank cells and leave fuel empty. At full heat demand, two heaters use 10 kW (50% Core); furnace/lamp require extra headroom. 65% provides 13 kW for both heaters plus furnace and lamp. Warm-room thresholds vary as heat demand falls. Verify reduced power, unlink/rebind and meaningful monitor heater labels.
4. **Isolated dock:** the surface room has a Cell Dock and RH-5. Bind them directly and insert a cell. Verify operation in the cold, finite depletion and full-cell swap. A continuously loaded 5 kW heater consumes a full 500 kJ cell in 100 seconds. Physical gas state determines whether the UI reports air heating or structure heating; a closed room can still contain trapped air.
5. **Persistence:** note target, charge and temperature, then Save and Quit/reopen. Verify retained links, thermostat target and cell charge, reconstructed readings and resumed paid heat.

Headless tests cover exact electrical/thermal budgets, duplicate guards, idle and throttling, fuel fallback, priority and partial supply, multiple heaters, airless coupling, depletion/swap, save reconstruction, signed menu transport, stale-menu invalidation and the real generated command parser. An ordinary-tick native case covers allocation/heat ordering without a PIXIE in the circuit. Human appearance, sound, perceived pacing and same-world persistence remain distinct acceptance checks.

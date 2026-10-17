# Power Flow Check

Source: [Notion Power System](https://app.notion.com/p/3f57cfaa89018123a786fbb637b5ecb1), including its October 10 additions, and the linked Room O2 Supply and Demand specification. This slice connects a Geothermal Core through relay links to ORSA Panel Lights.

Core output is a fixed budget: heat + O2 + power = 100%. Default is 60/40/0. Set power to 25% for 45/30/25 and 5 kW; 100% power produces 20 kW. The power slider preserves the chosen heat/O2 balance, including gradual dragging and saved reload. The second slider changes that balance. Network demand never changes either setting. Old saves keep their prior heat/O2 split, upgrades and inventory, with zero power.

Each Panel Light draws 0.1 kW and emits level 15 while powered, otherwise zero. It uses no fuel or room O2 and is not a heat/noise source. Networks account every 20 ticks in integer watts. Cycles cannot duplicate supply; separate connected Cores add their actual selected output. Brownout sheds whole lights in stable position order and restores them when supply returns. No power is stored in this slice.

Launch from the owning power checkout with Java 21:

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew runClientLab --console=plain -PfdLabWorld='Power Flow Check'
```

The new lab is separate from Power Network Check. Default difficulty choice is locked, progression is paused, advancement definitions and chat awards are suppressed in this save only, and it starts at night without night vision. Setup is guarded once by `#built pflow`; do not rerun or reset it. `/function power_flow_check:controls` restores its three clickable station directions.

| Station | Player action | Expected result |
| --- | --- | --- |
| Core to light | Bind Core x0 → relay x8 → relay x16 → panel x24, all y65,z0. Open Core empty-handed and set power to 25%. | Light turns on within the next one-second accounting check. Binder shows 5.00 kW supply / 0.10 kW demand / 0 kJ stored. Default or a broken/cleared relay turns it off; rebind to restore. |
| Sealed wall | At y65,z20, bind inside Core x0 → feedthrough x5 → outside relay x9 → panel x17. Set 25% inside, then use the Outside sealed wall button to bind the outside half. | Light works through the sealed wall. Replacing the linked feedthrough removes its incident links; replacing it requires rebinding. A direct saved Core-to-outside-relay edge through the feedthrough instead suspends through replacement glass, then returns when the feedthrough is restored. |
| Brownout | Bind Core x0,z32 → relay x4,z32 → panels x8,z30/32/34. Set power to 1%; arrow keys adjust by 1%. | 0.20 kW supply / 0.30 kW demand: two lights on, one off. 2% powers all three. Core heat/O2 settings do not silently change to meet load. |

Save and Quit, reopen the same world, then check surviving links and Core settings. The owner judges the Core controls, Copper Fork, target feedback and actual illumination; headless checks do not establish human or LAN acceptance.

Six new native cases cover old/new save migration and menu security, ordinary server-tick delivery and source/relay changes, brownout/cycles/multiple sources, saved feedthrough reconnection and an outdoor lamp in actual vacuum, orphaned saved lit-state recovery, and the original seven native-parsed replay functions. The passing full gate contains 772 unit cases, 489 native cases and all 480 required reports. Final compact screen/endpoint wording passed the subsequent full build. Evidence and source fingerprints are under `build/power-network-evidence/power-flow/`; earlier failed attempts are preserved there.

Owner accepted basic light-on and shutdown after relay break. The next live pass is feedthrough, then brownout. An eighth guarded helper, `power_flow_check:sealed_outside`, adds only a teleport and directions for accessing the outside half; every saved file except that helper and the controls function was preserved. Updated fixture compilation/resources passed; the electrical implementation is unchanged from the passing native gate.

On October 14, the owner accepted feedthrough shutdown when replaced with glass and recovery after rebinding, plus two lights at 1% power and all three at 2%. Alongside basic light-on and relay-break shutdown, all three planned stations have owner-reported visual passes. Same-save persistence details and LAN remain separate acceptance checks.

On October 14, the owner reopened the same save and confirmed all three brownout lights remained on and the Core retained 2% power. Same-save working-network and allocation persistence are accepted. Local station testing is complete; next is source review and committing this slice.

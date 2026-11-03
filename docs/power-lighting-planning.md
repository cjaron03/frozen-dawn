# Work Lamp, Floodlight and later Suit Headlamp planning

## Merged lighting and Headlamp branch handoff (October 18)

[PR #115](https://github.com/cjaron03/frozen-dawn/pull/115) merged into `feat/maeve-director` as `236ce9b187cbd0d35e928ade5fcfe5b0d735d75e`. The merged tree exactly matches tested PR head `1aaac072feed99c974b5e75a5766ac09ea2f7133`. Both protected checks passed: Java 21 build, 848 unit tests, 644 native regressions / 635 required reports, and 500 seeded stress cases plus repeated regressions. Review resolved the shared recipe pattern and the Work Lamp/Uplink monitor-kind collision.

The owner authorized branching for Headlamp next. Branch `feat/suit-headlamp` starts at that merge in `/Users/jaroncabral/Projects/minecraft-mod/.worktrees/suit-headlamp`. No Headlamp gameplay code has been added. The accepted planning baseline below is an inventory module requiring an EVA helmet and its own independently drained inventory Power Cell; implementation should proceed in slices after confirming the intended behavior. The Battery Lantern's existing moving-light lifecycle is available as a foundation.

The lighting checkout `/Users/jaroncabral/Projects/minecraft-mod/.worktrees/power-lighting` retains the accepted Work Lamp and Battery Lantern worlds, open lab client and delivery evidence. Preserve those saves and replay records. Earlier sections below record the design and replay history and may describe a superseded uncommitted or unmerged state.

## Delivery review (October 18)

The owner accepted the Work Lamp and Battery Lantern replays and authorized review, commit and a PR into `feat/maeve-director`. The lighting branch is based on current Maeve Director `20ac3635339922fc47106d581806a8959cec5264`, including Uplink, Plumb Line and nonblocking power-link checks. Work Lamp monitor kind 10 preserves Uplink kind 8 and Plumb Line kind 9. Both accepted playtest worlds remain preserved; the existing lab client requires a restart to load the updated source.

Review found an identical recipe pattern for the two lights. The Lantern now puts copper between the glass and redstone beneath it; Work Lamp retains redstone between the glass and copper beneath it. Material quantities and output counts are unchanged. A required native test resolves both real crafting grids to their intended output and confirms the crafted Lantern has no free cell.

The combined regression run exposed a rejected link in the existing real-tick Induction Furnace test. That test now owns a small glass enclosure instead of relying on surrounding batch geometry, and link assertions report the rejection result. Furnace production behavior is unchanged. The failed disposable world, report and log are preserved before rerunning the gates. Final verification results and PR status are recorded in the PR and local `build/lighting-delivery-evidence` bundle. Earlier entries below are chronological replay evidence and may describe a superseded handoff state. Actual multiplayer visual acceptance remains unclaimed. Floodlight, Headlamp and Chemlights remain later slices.

Owning checkout: `/Users/jaroncabral/Projects/minecraft-mod/.worktrees/power-lighting`. Branch: `feat/work-lamp-floodlight`, created from the freshly fetched `origin/feat/maeve-director` at `68d8684cb0fed02e008b63ffea86a14df921a01a`. Existing Scribe WIP and the Forge save/client remain in their original checkouts.

The owner requested a branch and Notion review, followed by questions **before implementation**, and clarified that the difficult later feature is the **Suit Headlamp**, not the Floodlight. They subsequently accepted all six recommendations and authorized the Work Lamp slice first, followed by a dedicated Gradle world and owner playtest before Floodlight work.

## Sources and confirmed requirements

- [Power System](https://www.notion.so/3f57cfaa89018123a786fbb637b5ecb1): Work Lamp is a small wall/ceiling lamp, light 12, 0.05 kW. Floodlight is directional, light up to 15, range up to 24 blocks, 0.4 kW. Both use the existing grid, remain silent and nearly heatless, work without O2, and go dark when unpowered. Lighting is shed early during brownouts. The proposed Floodlight technique is invisible vanilla light blocks along its beam, updated when blocks change.
- [Vacuum Lighting](https://www.notion.so/3fc7cfaa89018147b367f3f3839c33df): Suit Headlamp is explicitly LATER. Helmet mount powered by a cell in inventory, lighting where the player looks; proposed technique is an invisible light block following the player. Sharing drain with the suit O2 battery is optional. The page's "first two" means **Chemlights and Battery Lantern**, rather than Work Lamp and Floodlight.
- The owner subsequently added Battery Lantern implementation to this branch, ahead of Floodlight. Chemlights and Headlamp remain later slices.

## Local implementation findings

Panel Light provides the existing 100 W, light-15, no-heat/no-noise grid pattern. The compact ORSA Emergency Light provides six-face mounting and Binder interaction patterns. Power accounting, link admission, isolated dock compatibility, monitor naming and light shedding currently identify specific block classes and need explicit integration for new grid lamps.

Alarm Beacon already has a sweep solver, client visual lighting, and server placement/removal of vanilla invisible light blocks. Its block entity tracks and directly removes its own light positions. This is a useful reference, not a proven shared light-ownership service. Floodlight/headlamp overlap with each other, alarm beacons and existing light blocks must not erase someone else's light or modify occupied blocks. Obstruction changes, water, unload, source removal, restart and multiplayer need deliberate handling. Vanilla block light naturally spreads sideways; a sharply bounded spotlight is a different visual requirement from approximate illumination along a cone.

The existing suit/O2 path uses oxygen tanks and integrity/service state; it does not establish a shared inventory Power Cell battery for ordinary suit oxygen. The optional headlamp/O2 battery linkage would therefore be additional design and implementation scope.

## Agreed slices

1. **Work Lamp:** complete ordinary grid device with compact model, wall/ceiling attachment, light 12 / 50 W, binding, isolated dock operation, outage/shedding and save/reload checks. Agree crafting and on/off controls first.
2. **Floodlight:** first prove bounded light placement, overlap ownership, obstruction updates and complete cleanup in a disposable native fixture; then add the powered 400 W lamp, chosen beam shape/aim, visuals and owner acceptance. Keep technical proof and visual acceptance distinct.
3. **Portable-light progression:** retain or revise the Notion order of Chemlights, Battery Lantern, then Headlamp. This is not authorization to implement these items in the grid-light branch.
4. **Suit Headlamp:** separate movement/aim prototype using the proven light lifecycle, then helmet/module compatibility, deterministic inventory-cell drain and controls. Prove multiple players, logout/death/teleport/dimension changes and cleanup before treating it as a shippable item. Start with independent lamp power; optional shared suit power is a later slice if chosen.

## Questions answered by the owner

1. Floodlight mounting/aim: fixed six directions on floor/wall/ceiling for the first version, or adjustable aim?
2. Floodlight beam: approximate cone with normal Minecraft light spill and distance falloff, or a sharply bounded spotlight? Proposed first version is a moderately narrow cone up to 24 blocks, strongest at light 15 near the lamp, stopped by opaque obstacles.
3. Controls: empty-hand on/off toggle for each grid lamp, or automatic whenever powered? Proposed first version is manual on/off, Binder reserved for binding, no redstone controls yet.
4. Acquisition/art: ordinary iron/glass/redstone/copper crafting and existing ORSA industrial styling, or salvage-gated equipment?
5. Portable progression: keep Headlamp after Chemlights/Battery Lantern as written, or bring it forward after the two grid-light slices?
6. Later Headlamp integration: inventory module requiring an EVA helmet plus an independently drained inventory Power Cell, or a permanent helmet upgrade / shared suit-power design? Proposed first version uses an inventory module and separate lamp drain, with compatibility and charge selection specified before coding.

The owner accepted the recommended answer to all six questions: fixed six-direction Floodlight aim, moderate cone with normal light spill, empty-hand toggles/Binder binding, ordinary materials and ORSA styling, Chemlights/Battery Lantern before Headlamp, and a later inventory module requiring an EVA helmet with its own inventory Power Cell drain. On October 17 they explicitly added torch-style wall attachment to the Work Lamp; all four wall faces and ceiling attachment are included. Work Lamp floor placement is excluded by its wall/ceiling specification.

## Work Lamp implemented and owner accepted

Work Lamp uses 50 W and light 12, no oxygen/fuel consumption, room heat or sensory emission hook. The grid-light interface preserves Panel Light accounting while admitting the new lamp, isolated docks and monitor shed labels. Existing emergency lights recognize shed grid lamps. Wall/ceiling support removal drops the fixture and removes its incident links. The compact native model uses existing Minecraft textures; the recipe makes two from one iron, two glass, one redstone and one copper.

Native checks cover real five-face item placement, support removal, palette and link codecs, both-hand Binder routing, empty-hand switches, exact 50 J accounting, insufficient charge, depletion/replacement, vacuum operation, stale saved light reconciliation and emergency backup. Java 21 `build architectVerify` passed 825 unit tests and 622 native cases, including all 613 required cases. The owner accepted the station replay and save/reload on October 17; see `docs/work-lamp-playtest.md`. Source remains uncommitted on this branch; it has not been merged.

## Priority update: normal cave exploration first

The owner redirected priority after Work Lamp acceptance: "just one question for the light we just added and this. what will be practical for normal cave exploration? like a really good subsitute for torches? i wanna protize that first". Portable cave lighting now precedes Floodlight work. Preserve the accepted Work Lamp implementation and save.

The live Vacuum Lighting spec defines Battery Lantern as a placeable, freely relocatable light-14 lamp with its own Power Cell and no grid link, drawing about 50 W. A full 500 kJ cell lasts 10,000 seconds (about 2 hours 47 minutes). This is the recommended next reusable exploration light. The first slice is place-and-pick-up lighting; movement-following held/helmet light remains the later Headlamp work. Many lanterns left along a route each require a cell, so cheap route markers remain a separate need.

Chemlights are the spec's cheap thrown/placed route markers: sulfur + glass + dye makes four, light 10 for about ten minutes, then fade/expire. That is temporary breadcrumb lighting, and the short duration needs explicit balance discussion before treating it as the primary marker for long cave trips. No lifetime or brightness change is decided here. Recommended feature order is Battery Lantern, then Chemlight route marking, then Floodlight; the intended early-Chemlight/cell-Lantern/late-Headlamp survival progression remains the design baseline.

## Floodlight deferred behind portable cave lighting

The owner authorized moving on after Work Lamp acceptance and asked for the exact player behavior. The accepted scope is 400 W while enabled/powered, light up to 15 in a moderately narrow cone extending up to 24 blocks with distance falloff and ordinary Minecraft light spill. Fixed six-direction aiming and floor/wall/ceiling mounting; empty-hand toggle and Binder linking; normal grid or isolated Cell Dock; zero idle draw when switched off, vacuum-safe, no machinery noise or room-heat contribution, and existing lighting load-shedding priority. Opaque obstacles stop the affected light paths, and block changes update the illuminated footprint. A full 500 kJ cell supplies one 400 W light for about 20 minutes 50 seconds.

First technical slice: bound native light placement/update work, preserve occupied blocks, and establish shared ownership and cleanup for overlapping floodlights, pre-existing light blocks and Alarm Beacon lights. Verify power-off, removal, obstruction changes, unload/reload and restart without orphan light or erasing another source's light. Then add the device/model/recipe and a separate Gradle playtest world. Keep `Work Lamp Check` preserved. No sharp-edged shader spotlight or tracking spotlight is part of the agreed first version.

## Battery Lantern specification review and accepted implementation

The owner explicitly chose Battery Lantern first and requested Notion review followed by questions before implementation. The owner subsequently accepted all six recommendations and the drain rule, and explicitly authorized implementation, a separate Gradle test world and launch. Continue in the owning `feat/work-lamp-floodlight` checkout with the accepted Work Lamp WIP and save preserved.

Sources checked: [Vacuum Lighting](https://www.notion.so/3fc7cfaa89018147b367f3f3839c33df) and [Power System](https://www.notion.so/3f57cfaa89018123a786fbb637b5ecb1). A Notion search for Battery Lantern returned those source pages, with no separate lantern specification. Confirmed: placeable LED lantern, one physical Power Cell, no grid link, light 14, about 50 W, freely picked up/relocated, cold light invisible to Pyro, works without O2. Existing cells hold 500,000 whole joules, so 50 W means 10,000 seconds, about 2 hours 47 minutes. Placed-light first slice and later movement-following Headlamp remain the agreed distinction.

Current code: Power Cells are unstackable items with charge in an existing data component. Battery Bank/Cell Dock persist physical cells, but their removal drops contents separately. The lantern needs an explicit carried-item preservation path. Existing Oxygen Reservoir/PIXIE item components and copy-components loot provide local patterns; native tests must prove no lost/duplicated cell on pickup/support loss, hand swaps, save/reload and depletion. No grid-node registration is planned for this standalone consumer.

Recommendations accepted by the owner:

1. Mounting: floor standing, ceiling hanging and attachment to all four wall sides; recommend all six surfaces.
2. Controls: recommend empty-hand toggle; right-click with a cell inserts/swaps it; Shift-empty-hand removes the cell; action-bar charge feedback. Alternative is a one-slot menu.
3. Pickup: recommend fast hand breaking drops one lantern retaining its installed cell, exact remaining charge and switch state, including support removal; alternative is a separate dropped cell as with the dock.
4. Inventory: recommend empty lanterns stack to 16, loaded lanterns to one per slot. Proposed consumption is placed, enabled and loaded simulation time only; carried/off/unloaded lanterns do not drain or force chunks.
5. Water: recommend waterlogged placement and normal operation underwater, preserving water on removal.
6. Acquisition: recommend an inexpensive iron/glass/redstone/copper recipe making an empty lantern, with the existing cell supplied separately and a compact ORSA handle/guarded LED model.

After answers: implementation, focused persistence/energy/interaction tests and applicable existing gates, then a separate Gradle cave-light playtest world for owner acceptance. Floodlight and Headlamp follow later.

The Battery Lantern slice now implements these accepted choices. Energy is paid in alternating 3 J / 2 J server ticks, retaining a prepaid half-joule in the lantern across off periods, cell swaps, pickup and save/reload. Only the loaded block ticker consumes charge; the item has no carried drain or dynamic light. The existing physical cell is preserved inside the dropped lantern through vanilla container components. See `docs/battery-lantern-playtest.md` for current verification and active replay.

Battery Lantern handoff: Java 21 build, 825 unit tests, all 628 native cases / 619 required cases passed, including six Lantern cases. Gradle prepared a separate `Battery Lantern Check` world and launched it through the lab client. Initial charge/switch state and quiet-world settings are verified; owner visual/pickup/reload acceptance remains pending. The Work Lamp save remains closed with its preservation hashes unchanged.

## Offhand lantern extension authorized by owner

After accepting pickup/carry/replace, the owner requested shorter tooltips and active offhand lighting. This explicitly supersedes the earlier inventory rule for the offhand slot: ON + charged + offhand now emits light 14 and drains 50 W; main hand and other inventory slots stay inactive. Shift-use in air toggles the held item, preserving the same cell, switch and prepaid half-joule through placement. Tooltips are three compact lines.

A distinct invisible light block represents each active player source; one nearby loaded air/source-water cell per player, with shared positions retained for overlapping sources. It never replaces terrain or vanilla/alarm light blocks. Old positions clear on movement, stowing, depletion, death, dimension departure and logout. Scheduled self-validation removes persisted orphan markers after reload/unload; server shutdown removes loaded current markers. The existing placed-lantern behavior remains the accepted baseline.

Battery Lantern owner acceptance is complete for the core replay, including the explicitly requested active offhand extension and save/reload. Fresh logs show successful save/rejoin with no runtime/save errors. Current checked build passes 825 unit / 630 native / 621 required cases. The accepted Work Lamp and Lantern slices remain uncommitted/unmerged; delivery review and a PR into Maeve Director are the recommended next step. Headlamp remains separate: accepted baseline is an inventory module requiring an EVA helmet and its own independently drained inventory Power Cell. Its movement/aim behavior and further acceptance should be planned before implementation using the Lantern light lifecycle as a foundation.

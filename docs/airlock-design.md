# Player-built airlocks

Design agreed October 9, 2026; custom airlock implementation is now in progress. Owning pressure branch: `feat/atmospheric-breach`, checkout `/Users/jaroncabral/.codex/worktrees/rimewood/minecraft-mod`. This follows the breach and room-recovery notice patch. Rimewood remains separate.

## Parts and discovery

The owner proposed an Airlock Door, Airlock Controller and Manual Vent Valve. Players build the walls, floor and roof themselves; there is no multiblock pattern. Full glass works. Recognize a sealed connected chamber containing at most 32 passable interior cells, bounded by at least two complete airlock doors: one group leads to a breathable base volume, another to exterior vacuum. Count a two-block door once. A 3x3x3 interior is 27 cells; wall blocks are excluded. Do not read or load ungenerated chunks to discover an airlock.

The primary controller is inside the chamber. Optional side panels operate that same chamber and reserve; they do not create additional storage or separate cycles. Ambiguous or incomplete layouts report a fault rather than choosing a base arbitrarily. Remember the recognized chamber while a door is intentionally open, but invalidate it when its walls or role assignment change.

## Mechanical doors

Closed airlock doors seal through the existing geometry predicate. An attempt to open against unequal pressure is refused with a hiss and `Pressure differential`. Enforce this on hand use, redstone, upper/lower-half updates and automated opening paths. During a cycle, both door groups must remain closed. Breaking a door or another wall still produces the real breach; it cannot be prevented by a virtual lock. An Architect destroying a door remains meaningful.

Pressure equivalence alone is insufficient while a cycle is in progress: both doors remain locked. At rest, compare actual gas density on the two sides, including diluted or compressed air, with a tolerance of5percent of nominal pressure (or the larger pressure above nominal). The inner door compares the base; the outer door compares its actual surroundings. A mismatch refuses opening and directs the player to cycle the controller. The controller matches the actual base density when returning, or the actual exterior density when leaving an already matched base. It never substitutes a fixed nominal target. If both base and chamber are depleted, do not advertise a breathable return path. A failed controller must not strand a player behind an impossible software lock when both sides genuinely match pressure.

## Cycles and reserve

The owner explicitly selected a measurable Core-fed controller O2 reserve with backup canisters. Start devices empty. Core refill requires a real connected, breathable base volume with a working Core within its oxygen-support range. Supply must not pass through an unrelated solid wall or borrow an outdoor Core's radius. Canister transfers debit the existing item component by exactly the amount accepted; partial fills preserve leftovers and empty canisters.

Proposed first-pass duration: four seconds (80 ticks), within the owner's 3–4 second target. Depressurization returns 90% of the chamber's accounted air to storage and vents 10%. If storage lacks room for recovery, excess is vented and disclosed. Pressurization transfers the measured deficit needed to match the destination from storage. Chamber requirement scales with interior size; net repeat-cycle consumption is 10% when the recovered portion fits. Larger chambers need larger initial fills and lose more per trip.

The current Core is an unlimited oxygen producer. With a Core, losses therefore cost replenishment time and buffer capacity; with backup canisters, they cost finite canister oxygen. Do not claim a new electrical grid or fuel drain. Pump power/failure behavior needs an explicit implementation contract; the existing mod has no shared electricity system. A manual valve remains independent of controller operation and reserve.

First-playtest tuning: 100 O2 units per passable cell, 6,400 reserve capacity, 40 O2 units per loaded second from a connected Core, and 80 ticks per cycle. Recipes are now native shaped crafting recipes. Do not silently equate oxygen inventory units with physical liters or pressure. Add measured chamber air and cycle accounting alongside the existing sealed/depleted state; do not simulate pressure throughout the whole world for this slice.

Persist reserve, accounted chamber air, phase, elapsed time and chamber identity. No free oxygen from placing, replacing, breaking, copying, unloading or reconnecting controllers. Account initial trapped air once; a new controller cannot credit the same room's air repeatedly. Optional panels share the same authority. On interruption, retain actual transferred quantities, never refund both chamber air and reserve.

## Feedback and emergency valve

Controller indicator: red = evacuated/fault, amber = cycling, green = breathable chamber. Hiss/pump cues should communicate the four-second cycle. Reports include insufficient O2, missing base supply, open door, invalid layout and cycle completion. Chamber fires/campfires/candles extinguish through the existing combustion path; spent torches stay mounted. Soul lights remain.

Manual vent instantly dumps chamber air with zero recovery. If the inner door is open, the connected base is also exposed: the valve must not pretend to isolate nonexistent walls. Any confirmation/guard should be deliberate and clearly communicated. Emergency venting never creates a reusable air credit.

## Implementation and acceptance order

1. Finish and visually accept sealed/restoring/restored notices and the updated ORSA entry.
2. Add door pressure refusal and verify all opening paths; introduce controller detection and shared authority.
3. Add measured reserve, canister debit, Core connection, staged cycles and interrupted/reloaded accounting.
4. Add emergency valve, original sound/visual assets, recipes, JEI support and ORSA crafting pages.
5. Native and owner checks: arbitrary glass/stone chambers <=32 cells; oversize/incomplete/ambiguous rejection; outer opening preserves base; simultaneous-door and redstone refusal; correct 90% recovery/loss and size scaling; no-Core canister operation; interruptions, block replacement, reload and unload; manual vent with inner closed/open; ordinary extinction and soul exceptions; optional panels cannot duplicate gas or cycles.

The existing two-door isolation GameTest is prerequisite geometry evidence, not proof of custom airlock doors, controller cycling, reserve accounting or power failure. Keep those claims separate until implemented and tested.

## Concept-art review — October 9, 2026

The owner asked to see concept art before adding airlock assets in-game. Three enlarged pixel-art drafts are preserved under `output/airlock-concepts/v1/`: the two-block steel door, controller with red/amber/green states, and red-wheel emergency vent. `prompts-and-provenance.json` records the built-in generation prompts, original paths and file hashes. These are review concepts; native-resolution textures, block models and gameplay are not implemented by this pass. Optional side panels reuse the controller appearance.


## Approved art implementation — October 9, 2026

The owner approved all three concepts and requested implementation. The native asset set uses seven 16x16 textures exported from the built-in image editor's derivative atlas, preserved in `tools/texture_sources/airlock/`. The door uses vanilla two-half geometry, while the controller and valve use orientable cubes. All three have shaped recipes, normal JEI transfer support and native crafting pages in ORSA Field Manual -> Heating -> Player-Built Airlocks. Visual acceptance of these in-game assets is pending.

Implementation uses one dimension SavedData authority per chamber. Optional panels do not duplicate reserve or ticks. Complete pressure doors guard direct state changes as well as hand and redstone use. Initial trapped air is credited once per chamber-cell history; replacing panels and repairing shell breaches cannot repeat the credit. Breaking the last panel discards reserve; the later geometry policy below supersedes the original discard-on-edit behavior. Pumping uses stored gas without requiring a new electricity system. A missing Core stops replenishment only.

## Powered cycler implementation — October 14, 2026

The Power System's electricity contract supersedes the earlier no-grid first-playtest note. Controllers now require 2 kW only during their 80 paid pump ticks; idle costs zero. Outages pause exact gas/progress with both doors locked, then resume when payment returns. Optional panels share one selected input and one pump debit. Core/canister O2 storage remains independent of electrical power; the manual vent remains mechanical. See [Powered Airlock Check](powered-airlock-check.md) for the implementation and two-base owner replay. Previous acceptance records remain historical evidence of the gas/door behavior.


Geometry and scan policy correction: sealed edits interrupt the cycle, retain exact chamber plus pocket gas and the independent reserve, and update controller ownership/IDs. Added former-solid space reduces density; compressed gas is retained. Finite separated pocket gas survives saves and follows real refill/vent transitions. Scan work limits are uncertainty, never vacuum evidence. Full scans continue across ticks to a larger bounded cap. Confirmed sealed gas survives indeterminate rescans/reloads with a seal-unverified notice; unloaded scans suspend. A real boundary opening is treated separately, using proven sky/exterior exposure or a large reservoir's own current-phase condition. See docs/powered-airlock-check.md for exact budgets, evidence and owner checks. This supersedes the previous broad size-overflow-as-vacuum correction, which was rejected by the owner.


Feedback correction (October15): the controller uses one baked status lamp (red empty/fault, amber cycle, green full) without a second blinking render surface. Action-bar labels distinguish filling from venting. Original procedural vent hiss/fill motor and rising confirmation tones remain per-chamber; nearby machinery has quieter positional feedback within4blocks during vacuum. Pressure telemetry updates in Creative and Spectator as well as Survival, so pressurized rooms restore normal carried sound.


Internal connection recovery (October15, corrected after owner replay): an internal opening into a pressurized base conserves its existing gas and distributes it across the base, opening and chamber. Both sides retain their existing air presence rather than being marked vacuum until nominal pressure is reached. Store measured gas for the ordinary cells separately from chamber gas and pump reserve; the optional roomGas save field defaults absent for older saves. Split, compression, rejoin and reload preserve the combined amount. A real exterior connection still vents; unloaded/indeterminate geometry retains the existing policy.

Missing gas uses the existing once-per-loaded-second shared production budget, capped at40units per physical room per second. Core output reduces PIXIE demand, and accepted PIXIE top-up pays power/water and reports room supply. The base can finish its measured top-up after the wall is repaired; the isolated chamber still uses its pump. If both volumes initially have no air, geometry edits cannot turn a partial refill into completed recovery: actual production must contribute gas and the normal recovery delay must pass. Atmosphere recovery never credits nominal inventory; measured top-up continues separately. Opening a wall or reading the UI never creates gas. A resealed ordinary base with a working Core or paid PIXIE recovers through its normal five-second process regardless of the isolated chamber state; a shared nominal gas deficit cannot gate that recovery. The closed chamber still requires its pump.


## Inner-door manual equalization — October 15, 2026

Shift-right-click either half of the inner door from either side to operate a built-in manual valve, including while holding an item. Both doors must be closed, the base must be a complete loaded sealed volume, and both sides must contain measured gas. If either side is vacuum, use the controller; the manual valve cannot fill or evacuate an empty volume. The action conservatively redistributes the combined existing gas over base and chamber; their room identities and closed door geometry remain separate. It consumes no electricity or reserve gas. Deliberate operation interrupts an unfinished pump before transfer and retains the gas already moved. A short existing valve cue and one typed status confirm equalization; the player opens the matched door normally afterward. This is one immediate gameplay transfer, with no new timer or offline simulation. Exterior doors, unverified volumes and an open base reject it. No saved-format changes.

Door messages distinguish chamber/base vacuum, vacuum across the exterior door, unequal pressures, an active cycle, unverified pressure and actual opening at equal pressure or mutual vacuum. Successful opening feedback is helmet-only; without it, refusal reasons still use the action bar. They reuse the existing helmet/action-bar slot. Direct door feedback can replace completed restoration notices, while active oxygen danger retains priority. No extra screen or persistent overlay.

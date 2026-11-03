# Acheron Forge electric mode

Owner decisions: Power System in Notion, “Acheron Forge: electric mode [DECIDED 2026-10-16]”, and the October 17 implementation review. Source: https://www.notion.so/3f57cfaa89018123a786fbb637b5ecb1.

Owning checkout: `/Users/jaroncabral/Projects/minecraft-mod/.worktrees/acheron-forge-electric`, branch `feat/acheron-forge-electric`, based on `origin/feat/maeve-director` at `41c8323976cbd01e23bf2ee33646de5611cc6bed`. The owner accepted the implementation and requested a commit and PR into `feat/maeve-director` on October 17, 2026.

## Behavior

Existing forges accept ordinary Conduit Binder links, including isolated Cell Dock links. No recipe or upgrade is needed to enable electric operation. Inputs remain two Acheronite Shards for one Refined Acheronite or four Spent Lattice for one Sealed Lattice.

The menu cycles Auto / Electric / Heat. Auto prefers electricity and falls back to heat when below Y=0 with a lit Thermal Heater or Geothermal Core within eight blocks. Electric requires a full 25 kW allocation and works at any height without a nearby heat source. Heat uses the original depth and proximity requirements and requests no grid power.

Electric processing advances at 1.5 times the thermal rate: 134 server ticks (about 6.7 seconds, rounded to a whole tick) versus 200 ticks (10 seconds). It pays 1,250 J for each working tick and draws nothing while idle or blocked. A single 20 kW Geothermal Core cannot run it electrically without additional supply or storage. The ordinary grid rules allow combined geothermal generation, reactors, Battery Banks and Cell Docks. The grid sheds the forge with processing loads below life support; the Power Monitor identifies a waiting forge.

Power loss, lost heat, full output and mode changes preserve the completed fraction. A recipe change resets progress. Legacy `Progress` saves migrate to half-tick work units, and absent mode settings default to Auto. Saved inventory, mode and progress survive reloads; electrical grants are transient.

The owner-accepted heat setting is **6.25 kW of raw room heat while electrically processing**, 12.5 times the Induction Furnace's 0.5 kW. The existing room solver applies its usual vacuum efficiency. The owner chose to retain this value after the live heat comparison and realism discussion on October 17, 2026. Heat mode adds no new waste-heat source beyond its existing heater/core. Both modes retain the existing Blind Architect machinery and post-Maeve resonance hooks while processing.

## Verification and remaining acceptance

Java 21 `./gradlew architectVerify --console=plain` passed on October 17, 2026: **825 unit tests and 616 native GameTests**, with all **607 required cases and reports** verified. All seven forge cases passed, including Binder interaction through either hand. `git diff --check` passed. Compiler deprecation and development-resource warnings are pre-existing. Native coverage includes full-demand admission, multiple generators, both recipe speeds, precise cell payment, duplicate-tick protection, outages, cut links, blocked output, mode security, legacy save migration, heat fallback and paid room heat in vacuum. The existing required gate also exercises the Induction Furnace after sharing its processing-load interface with the forge.

Build source SHA-256: `3388762a176359fe2de18993b0fc0bef3fae6924f0c12ce296814354786876bf`. A separate hash calculation verified the current source matches the tested build. Jar: `build/libs/frozendawn-2.0.1-alpha.jar`, SHA-256 `b39ac0c34d4c6a48d6299ba2b1e637cd83d2b45761eafc5d5d4f25b58c427efd`. Native XML: `build/gametest/report.xml`; complete current gate log: `build/acheron-forge-binder-verification.log`. The first run's two fixture failures are preserved in `build/acheron-forge-first-report.xml`; the corrected cases explicitly exercise Y=0 and initialize the heat-source registry before checking fallback.

The owner accepted mode switching, Binder routing, grid and cell outages, save/reload persistence, and the 6.25 kW heat setting. Broader machinery-perception and multiplayer acceptance remain separate. In a disposable lab save, compare Auto / Electric / Heat, disconnect power during a partial operation, restore it, fill the output, save/reopen, and observe the room with and without cooling. Keep machinery perception and player acceptance separate from headless proof. The owner-requested disposable playtest is documented below.

## Active owner playtest — October 17

The owner-requested `Acheron Forge Modes and Heat` save uses two real reactors. Surface smelting, Binder routing, underground Auto heat fallback, Electric grid disconnect/reconnect, cell depletion/replacement, and mode/progress/inventory persistence across owner reload are accepted. The owner observed strong overheating with one vent and later installed three additional vents. Heat tuning is accepted at 6.25 kW. A Binder menu interception bug is fixed and natively verified; follow [the playtest handoff](acheron-forge-playtest.md) for the preserved save and completed owner checks.

# ORSA Uplink

Source: [Notion ORSA Uplink](https://app.notion.com/p/3fa7cfaa89018172ac2fc9aa2c7c2731) (Design, decided 2026-10-14), fetched 2026-10-17. Branch `feat/orsa-uplink`, cut from `feat/maeve-director` at `41c83239`.

## Owner decisions, 2026-10-17

- **Maeve attention is Uplink-local, outside MACS.** MACS has no attention quantity to raise (see [maeve-attention.md](maeve-attention.md)), so a transmission records a *signal site* at the dish in Uplink save data. After a lag, nearby signal pressure raises the natural Architect spawn chance. No MACS belief, world-model point, focus slot or mission is written. The Source of Truth is unchanged.
- **Archive fragments are drafted from canon** (First Boot Epigraph; Canon Hierarchy, Profit and Continuity). The text is a **draft for owner review**; see below.
- **Supply drops are new physical drops** placed with a locked map; worldgen drops are not reused.
- **The art is original**, authored in code by `docs/art/orsa-uplink/make_textures.py`.

## Construction

- **ORSA Uplink Console** (`frozendawn:uplink_console`): grid load node, Binder range 16, the same sealed-wall/feedthrough rules as other devices. Recipe: copper, glass pane, iron, **Signal Processor**, redstone block.
- **ORSA Uplink Dish** (`frozendawn:uplink_dish`): passive. It must have open sky directly above it and stand within 8 blocks of the console. The console uses the nearest dish with open sky. Recipe: iron, **Feed Horn**, iron block. The block is a 1x1 mast; a client renderer draws the reflector about two blocks wide. The console writes the dish's `mode` each second: it sweeps slowly while Listening, spins at 45°/s while transmitting and 90°/s on Boost, and eases back to its placed facing at rest.
- **Signal Processor and Feed Horn** are salvage only. Every newly built monitoring station back room (behind the terminal lock) holds one of each. Each new ruined reactor site's medical store holds a Feed Horn. Uplink resupply drops can carry either one. They have no crafting recipe.
- Mining the dish returns it so it can be moved. Explosions and storm damage destroy it with no drop, so rebuilding it needs another Feed Horn.

## Modes and power

| Mode | Draw | Duration | Notes |
| --- | --- | --- | --- |
| Off | 0 | | |
| Listen | 2 kW | continuous | One automated broadcast every 90 s while powered with a working dish: phase advisory, relay census, ambient carrier traffic. The census counts every station the relay knows from the seed within 1024 blocks of the console, built or not (candidates worldgen already rejected excluded). "Answering" counts those whose terminal is unlocked. Shown in the console log and sent to players within 12 blocks. |
| Transmit | 40 kW | 3 min | One reward on completion |
| Boost | 60 kW | 90 s | Same reward in half the time; adds 1.6 signal instead of 1.0 |

Transmit and Boost first show `SIGNAL DETECTABLE. TRANSMIT?` and need a confirmation. While the prompt is up, the console schedules its extra draw: `PowerNetworks.loadFollowTargetWatts` adds it so a CFU-7 on load-follow ramps first. The prompt shows `Grid ramping up: X / 40 kW` until it reads `Grid ready`, and Confirm only enables then. Nothing is served or charged while armed. An unanswered prompt lapses after 60 s and releases the scheduled load. See also the reactor's Reserve setting ([hearthstone-reactor.md](hearthstone-reactor.md)). The handshake starts only in the overworld, with a dish under open sky, after the daily cooldown, and when generation plus stored energy covers the full draw beyond every other load. A refusal spends nothing. Starting spends the 24000-tick cooldown, counted in game time so a stopped daylight cycle cannot stall it, and records the signal. The console reads the one-second grid allocation. If it is not fully served, or the dish is lost, the handshake aborts with no reward and the signal stays recorded. Uplink loads use the ordinary non-life-support priority. The Power Monitor lists a shed console by name.

## Rewards

A due archive fragment always comes first. Otherwise the console picks by weight: supply drop 3, station map 2, and whichever came last has its weight halved. When no undiscovered site is in range, it gives a supply drop. A transmitter within 32 blocks receives the printout directly. Otherwise the console holds it for the next player who opens it, and drops it if broken.

- **Archive:** three ordered fragments, shared per world. #1 comes on the first transmission. #2 needs Maeve to have activated (latched, read through `MaeveDirector.hasActivated`). #3 needs Phase 6 and is delivered as the human reply. Each prints as an **ORSA Relay Transcript** (an ORSA document, `doc_type` `relay_archive_N`). Right-clicking it archives it, granting the invisible advancement `frozendawn:relay_archive/N` (no display, toast or chat), which unlocks that fragment's pages in the Field Manual entry *ORSA Intelligence > Relay Archive*. The text lives only in that entry. The console's Archive tab lists the fragments received so far and points to the manual. Owner decision 2026-10-17, replacing written books.
- **Supply drop:** target 150-300 blocks away. The locked *ORSA Resupply Plot* draws a 60-80 block dashed circle that always contains the whole crash site, plus the console marker. The wreck is built only when ordinary play has loaded its area; nothing is force-loaded. Its crates hold a salvaged power cell plus the usual cell, two O2 cartridges, food, and one rare part (Signal Processor, Feed Horn or Thermal Core). Within 30 blocks the locator beacon pings, with a sound and an action-bar distance and compass sector. It goes quiet once a player comes within 4 blocks.
- **Station map:** finds the nearest monitoring station or ruined reactor site candidate that no player has loaded yet, 200-900 blocks away, using seed positions only. The locked *ORSA Signal Plot* draws a 96-block region with labels such as `ORSA signal, sector NE, ~300 m`. The site is *promised*: when its area loads, it is built even on ground worldgen would normally decline. The plots are telemetry maps with no terrain.

## Signal pressure

Each start adds 1.0 (Boost 1.6) to the signal site at the dish. Sites within 32 blocks merge, the score caps at 4.0, and it decays with a three-day half-life. Pressure takes effect only after a 2400-tick lag. While a player is within 96 blocks of a site, the natural Architect spawn chance is multiplied by `1 + 0.75 × pressure` (up to 4×), still under the existing 0.8 cap and the one-per-96-blocks density cap. Natural Architect spawning starts at Phase 6 and is unaffected after Maeve's erasure, so early transmissions are remembered but have no visible effect until then. `/fd debug uplink dump` (operator) lists archive progress, cooldown, sites, pressure, lag remaining and drops.

## Deviations and open items

- **Plumb Line narrowing** is not implemented because the GPR-4 Plumb Line does not exist yet. Plots narrow only through exploration.
- **Maeve destroying the dish:** the dish is an ordinary breakable block, and no Architect behavior targets it. Storm wear is implemented: during a blizzard, each linked dish has a 2% chance per minute of being torn away.
- **Archive text:** the owner read all three transcripts in play on 2026-10-17 and approved #3, the Cydonia reply ("it hits the lore great"). The canon notes below still apply. The drafts avoid explaining Continuity and never call it a mind. The word appears only in the Epigraph's council name and the existing motto. Fragment #3 assumes a Cydonia relay night operator answering, with an ORSA badge flagged by an unnamed active protocol. Please confirm this fits CH-12 (the colony "never looked back") or redirect.
- Promised sites skip the biome and slope checks. Spawn-distance limits still apply.

## Verification

- Unit: `UplinkPolicyTest` (9 cases): modes, cooldown, archive pacing, weighted rotation, lag/decay/cap, sectors, circle containment, map scale.
- Native GameTests (required in `config/architect-required-tests.txt`): listen draw, dish range and sky refusals, budget refusal, shed monitor row; abort on battery exhaustion with cooldown and lagged signal still recorded; boost completion across a reload with archive #1 printed and the second attempt refused by cooldown; plot printing and saved-data round trip.
- Gate: `./gradlew architectVerify --console=plain` passed on 2026-10-17: 834 unit tests, 614 GameTests, all 605 required cases verified (including the reactor radiator frost fix). `PowerCellSalvageGameTest` now checks the Uplink resupply profile separately, since it is not a worldgen cache and carries two cells. `architectMonkey` was not run: no Architect AI, pathing or MACS code changed. The only Architect change is the spawn-chance multiplier.
- **Human visual pass pending.** Console/dish readability, warning prompt, log, listen chat and plots in game.

## Playtest worlds

- `tools/prepare_uplink_playtest.py`, **Uplink Check**: flat, with a pre-linked reactor. You build the Uplink.
- `tools/prepare_uplink_world_playtest.py`, **Uplink Field Check**: real Overworld terrain (via the shared helper's `terrain=True`), Creative, Phase 6 late and paused, with two kit chests. Advancements are filtered except `relay_archive/*`, so the Field Manual unlocks work.

## Checkpoint

2026-10-17: committed on `feat/orsa-uplink`; headless gate green. Owner playtest in `run-lab/saves/Uplink Check` (`tools/prepare_uplink_playtest.py`): build, Listen, a 3-minute transmission and Archive 1 confirmed; the bigger spinning dish was accepted. The console screen overlap was fixed after the first session. The session exposed radiators buried by frozen atmosphere, fixed separately as `fix(reactor)`. Open: how archive fragments are delivered (currently written books), the reactor's response to a sudden 40 kW step, and a playtest in a normal-terrain world with real monitoring stations.

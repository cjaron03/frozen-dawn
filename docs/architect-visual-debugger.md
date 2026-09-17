# Architect visual debugger

The normal lab, wilderness lab, and ordinary world Architects share an operator-only visual debugger. It shows the server's current observations: vanilla actor and target paths, the committed D* walking corridor and waypoint, move-controller destination, collision shapes, mining/scaffold choices, combat range, recovery counters, and recent decisions. Cyan and pink trails make stalled movement and circling easier to see.

## Start and inspect

Launch this checkout with `./gradlew runClientLab` and use a disposable world with cheats. Prepare a lab before enabling its debugger:

```mcfunction
/fd architect lab setup
/fd architect lab scenario low_ceiling
/fd architect lab debug on
/fd architect lab run
/fd architect debug step 40
/fd architect debug inspect
/fd architect debug dump
```

Lab setup freezes simulation. `step` captures every stepped tick (up to 200 per command); normal playback samples every five game ticks. `resume` runs normally, and `freeze` pauses simulation again. The debugger follows replacement lab actors after reset or scenario changes. Enable it before running to retain the failure's path before lab cleanup stops navigation.

For wilderness, wait for preparation to finish:

```mcfunction
/fd architect wilderness setup
/fd architect wilderness debug on
/fd architect wilderness run
/fd architect debug log on
/fd architect debug resume
```

The wilderness HUD also shows the guided target's waypoint, lap, hit count and stall counter. Its pink waypoint and separate target navigation path distinguish target-harness failures from Architect pursuit failures. Roam/player scenarios have no guided waypoint.

For a nearby ordinary Architect, use `/fd architect debug on`, or select one explicitly with `/fd architect debug on <entity>`. The automatic selector prefers the prepared lab in your dimension, then the nearest Architect within 96 blocks. Explicit selection follows that entity only. Master Architect visuals are excluded. Observation does not force a target, reset AI, reseed randomness, or start the separate raw decision recorder. Use `/fd architect record <entity>` if you also want that recorder; visual events and geometry are captured independently.

## Controls

All controls below are available under `/fd architect debug`, `/fd architect lab debug`, and `/fd architect wilderness debug`. One observer selects one Architect; different operators can inspect different actors. Permission level 2 is required.

| Suffix | Effect |
| --- | --- |
| `on [entity]` / `off` | Subscribe or unsubscribe. Off removes the overlay; existing evidence remains exportable. Capture stops when the last observer leaves. |
| `routes on/off` | Native paths, committed corridor, waypoint and steering destination. |
| `geometry on/off` | Actor/target bounds, actual nearby collision shapes, support/mining/scaffold/candidate blocks and attack range. |
| `hud on/off` | State, distances, eligibility, navigation and recovery counters. |
| `trails on/off` | Recent actor and target movement. |
| `labels on/off` | Extra path-node and candidate labels; selected actor/target and active step/mining labels remain with their layers. |
| `log on/off` | Write a concise `[ArchitectVisual]` state line to the server log every 100 game ticks. Inspect, mark, dump and on/off also log. |
| `freeze` / `step [ticks]` / `resume` | Control the server simulation. This affects the whole server, like native `/tick` commands. |
| `history <ticksAgo>` / `live` | View a retained earlier observation, or return to the latest one. |
| `mark <label>` | Attach a marker to the current recording, including while frozen. |
| `inspect` | Print and log the displayed snapshot's state and retained history range. |
| `dump` | Export decisions, visual data, and the observer's displayed view. |

Changing dimension, disconnecting, or losing permission unsubscribes the observer. Returning to a world requires enabling the debugger again. Native `/tick freeze`, `/tick step` and `/tick unfreeze` remain compatible; use `debug step` for guaranteed per-tick visual capture.

## Reading the visuals and reports

Native path nodes use Mojang's gradient line with red next-node and blue other-node boxes. The additional committed corridor is cyan; its small cyan box is the selected walking waypoint. The move-controller direction is green, D* step gold, mining orange, scaffold green, support blue, excluded candidates red, and evaluated candidates purple. Break/place counts in the HUD count events observed while visual capture was enabled; the ordinary journal keeps its separate run totals. Candidate labels retain reasons such as `NOT_EVALUATED_AFTER_SELECTION`, so unevaluated options are distinguishable from rejected ones.

The range sphere uses the same 2.8-block entity-position distance as the melee hit check. Being in range alone does not prove a hit: the HUD also reports action, line of sight, backoff and swing state. `COMMIT_NOT_SAMPLED` means the production melee commitment check was not observed this tick or the previous tick. Inspection never reruns that check, since it can create a path. `MELEE_HIT` events and target health remain the evidence that an attack landed.

`debug dump`, normal lab dumps, wilderness dumps, and terminal lab exports include these files when visual evidence exists:

- `visual-snapshot.json`: latest captured server state; terminal captures precede navigation cleanup.
- `visual-history.jsonl`: retained snapshots, one JSON object per line.
- `visual-events.tsv`: bounded decision/candidate/marker history with absolute game ticks and relative run ticks.
- `visual-view.json`: added by `debug dump`; exact observer selection, visible layers, freeze state and historical frame.
- `summary.json`: capture limits, retained/dropped counts, build fingerprint and SHA-256 for every attachment.

Reports are written under the world's `architect-debug/run-.../` directory. In the development client this is `run-lab/saves/<world>/architect-debug/`. Follow `architect-latest.json` only when its status is `COMPLETE`; it identifies an immutable export. Server logs are in `run-lab/logs/latest.log` for the integrated development server, or `logs/latest.log` on a dedicated server.

History retains 120 frames (about 30 seconds at normal sampling), 512 events and 64 trail points. Repeated captures at the same frozen tick replace that frame. Each route is limited to 64 nodes around its current cursor; collision geometry to 96 shapes and markers to 24. Truncation is explicit in the data/HUD. Rendering stops beyond 128 blocks from the selected actor. Capture is transient and opt-in; reconnecting does not reload prior captures.

Historical mode overlays saved observations on **current terrain**, not a terrain replay. It remains fixed at the selected tick while simulation may continue; freeze first when investigating a failure. Requests older than retained history fail explicitly. Enabling only after completion cannot recover a path already cleared by cleanup. Reset/start a new run for new evidence.

## Mojang runtime integration

This implementation calls the Minecraft 1.21.1 runtime's `PathfindingRenderer.renderPath`, `DebugRenderer.renderFilledBox`, `DebugRenderer.renderFloatingText`, and `LevelRenderer.renderLineBox`. It adds a NeoForge render hook and a bounded, server-to-observer snapshot payload. No Mojang source classes are copied into the mod and no new rendering dependency is introduced.

The release runtime's `DebugPackets.sendPathFindingPacket` is empty, and its ordinary debug render loop does not invoke the path renderer. Also, `Path.writeToStream` requires optional debug data that ordinary navigation paths may not contain. The payload therefore copies actual path nodes and reconstructs the client render path, using the same immutable snapshot schema as the dump. It does not claim to expose vanilla open/closed search sets or all D* explored cells.

## Validation

The required regression gate includes two dedicated debugger GameTests: passive inspection preserves live navigation and AI randomness, terminal snapshots survive cleanup, fractional slab collision is retained, target navigation stays separate, and the real network codec round-trips the same snapshot used by reports. Unit tests cover bounded retention, same-tick replacement, history expiry, terminal sealing/reset, escaped markers, and attachment integrity.

Run `./gradlew build gameTestGate --console=plain`. Live rendering checks additionally require Minecraft: inspect the low-ceiling lab while stepping, toggle the layers/off, inspect a retained historical frame, and repeat in the wilderness dimension. Compare the displayed tick and entity IDs against `visual-view.json` and the log's `[ArchitectVisual]` entries.

Validation on 2026-09-13: the build and unit tests passed, and the required gate passed all 37 GameTests / 32 named Architect cases. Live overlay and multiplayer visual checks remain unverified: the attempted client launch reported `Failed to locate a primary monitor` / `glfwGetPrimaryMonitor failed`. This is a display-environment limitation, not a passing render check.

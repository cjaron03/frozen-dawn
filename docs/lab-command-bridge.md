# Gradle lab command bridge

The development client accepts a small set of local commands through `tools/lab_client.py`. This removes repeated chat typing during diagnostics and replay preparation. The player still opens worlds, plays encounters and judges visuals. It does not automate the Minecraft UI.

## Launch and identify

Run from the checkout that owns the save and current implementation:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew runClientLab --console=plain
```

Add `-PfdLabWorld='World Name'` to open an existing save in `run-lab/saves` directly (Minecraft quick play; it does not create worlds). Use the agent's terminal inside Codex. Keep its session and log path for later inspection. Only `runClientLab` enables the bridge. The ordinary launcher, guest client and dedicated server do not enable it. A Java change requires a client restart; a datapack edit can use `reload`. Back up a closed world before installing a build that changes its replay.

```sh
python3 tools/lab_client.py status
python3 tools/lab_client.py --world 'MACS Pawn Natural Supply' snapshot
python3 tools/lab_client.py --world 'MACS Pawn Natural Supply' maeve-dump
python3 tools/lab_client.py --world 'MACS Pawn Natural Supply' scores mpc '#stage' '#nat_count' '#nat_station'
```

Status verifies the checkout and a recent heartbeat. At the title screen it reports `worldLoaded: false`. Every other command requires the expected world name or exact save path; it cannot silently select another world. Snapshot reports actual player positions, loaded Architect UUIDs/positions/NoAI, game time, menu pause, freeze, step and sprint state. Entity queries are capped at 16,384 loaded entities and 128 Architects; truncation is explicit, and no chunks are loaded.

## Operations

| CLI operation | Use |
| --- | --- |
| `status` | Read current bridge heartbeat, checkout and world session. |
| `snapshot` | Loaded world/player/Architect diagnostic state. |
| `maeve-status` | Capture the real `/fd maeve status` output. |
| `maeve-dump [--player NAME_OR_UUID]` | Capture the real player dump. The sole online player is the default; multiple players require an explicit subject. |
| `architect-dump UUID` | Export that loaded Architect's real debug trace and return its path. |
| `scores OBJECTIVE HOLDER...` | Read up to 64 scores; absent values are null. Quote holders beginning with `#`. |
| `reload` | Reload the currently selected resource/datapack set, acknowledging completion of the reload future. |
| `function NAMESPACE:NAME [--player NAME_OR_UUID]` | Run an explicitly approved replay function at permission two as that player, after checking its world/stage guards. |
| `freeze` / `unfreeze` | Control simulation freeze. Freeze also stops a pending step/sprint. |
| `step TICKS` / `sprint TICKS` | Start 1–24,000 ticks from a frozen world with no existing step/sprint. Poll status for completion. |

Example approved-function syntax:

```sh
python3 tools/lab_client.py --world 'MACS Pawn Natural Supply' function macs_pawn:natural_retry
```

This example is valid only when stage=92 and the last window captured zero actors. **Do not run it in the present two-actor checkpoint.** The policy deliberately rejects `natural_second` once station two has already been used, preventing the confusing repeat that previously returned zero in chat.

`macs_pawn:natural_watch` is approved for **MACS Pawn Natural Arrival**, the separate complete pre-view backup copy (stage 92, station 2, count 1). It requires both original living actors to be held, places the observer between them at (3976.5,125,3976.5), releases their AI, and waits at normal speed. Admission has a 90-second bound. Once both pawns receive a convergence assignment, a fresh clock waits for both to enter the 24-block hotspot, followed by five seconds to watch. A lost assignment or a 121-second approach bound produces an inconclusive result. Stage 94 holds the actors for review. It does not summon or teleport an Architect, inject hotspot history, or force convergence. Arrival is recorded separately from combat success; test cleanup is inconclusive and never a wipe. The completed Natural Supply attempt is preserved and its older fixed 60-second helper must not be reused.

```sh
python3 tools/lab_client.py --world 'MACS Pawn Natural Arrival' snapshot
python3 tools/lab_client.py --world 'MACS Pawn Natural Arrival' scores mpc '#stage' '#timer' '#nat_count' '#nat_station' '#admitted' '#arrived' '#watch_result'
python3 tools/lab_client.py --world 'MACS Pawn Natural Arrival' function macs_pawn:natural_watch
```

Verify the snapshot and stage first. Prepare and unfreeze only while menu pause still holds simulation, so returning to the game starts the visible view. Do not run generic `macs_pawn:setup` in this saved-history replay.

Only functions listed in `config/lab-bridge-functions.json` can run. Review a function's actual effects and the agreed replay before adding it. Add its exact world and required scoreboard values; do not add a wildcard or expose arbitrary command strings. Reset/erasure, kill, summon and arbitrary NBT mutation are not general bridge operations. A function can itself change state, so inclusion requires intentional review; the bridge is not a sandbox for untrusted datapacks.

## Current Survival base replay

For **MACS Base Diversion**, follow [the base replay protocol](macs-base-diversion-playtest.md). Exact-world guarded functions are `base_prepare` (stage 92), `base_status`, `base_restage` and `base_start` (stage 100), and `base_end` (stage 102), all in `macs_pawn`. The base is a separate saved-history copy; do not run generic setup or the Natural Arrival helper there. The owner performs five minutes of ordinary upkeep under real late-phase hazards. A hidden QA lead-in releases the saved natural actors; Maeve alone decides dispatch. Do not disclose the sampled delay or treat the protected lead-in as a pressure baseline.

## Admission-first base retry

**MACS Base Bait** is a separate complete backup copy. Exact-world approved functions are `base_idle_prepare` at stage 92, `base_idle_status` and `base_idle_start` at stage 110, `base_idle_begin` at stage 112, and `base_idle_end` at stage 102. Preparation stays Creative while the saved natural pawns roam at normal speed. A real two-member assignment freezes the world and waits for the owner's explicit Start click. No admission within 90 seconds is inconclusive. Start enables the ordinary five-minute Survival protocol. See the base replay guide for evidence and limitations. Never run generic setup, restore history in-place, or replay start at a later stage. A reload after GROUP READY needs inspection because production releases unfinished groups.

The lab-only `fdlab base_pause` / `fdlab base_resume` commands let permission-two replay functions pause exactly at the handoff. They require the tagged replay participant, the matching ready/inconclusive/live stage, and the opted-in **MACS Base Bait** client (or an isolated native GameTest fixture). They expose no arbitrary command execution and are excluded from the release jar with the rest of `src/labBridge`. This avoids raising datapack function permissions.

## Results and limits

Each request and response stays under `run-lab/lab-bridge/`. The CLI prints the response path on stderr and JSON on stdout. Preserve relevant responses in the replay evidence folder. `ok` describes transport/dispatch completion; `commandSuccess` and `commandResult` describe the actual command. `commandOutcome` distinguishes SUCCESS, FAILED and ZERO_RESULT; the last two make the CLI exit with code 3. Zero is not treated as proof that the intended step happened. Even command success is not a gameplay pass: inspect the resulting stage and actors. Functions can send HUD/chat messages directly to players; those remain in the client log rather than the captured command-source output.

Tick step/sprint replies acknowledge that work started. They do not claim it completed. Menu or lost-focus pause can keep simulation paused; the bridge can still read diagnostics. Return to the game to resume a visible encounter. Sprint only empty waits already approved in the test protocol; preserve production confidence/decay/cooldown values and do not accelerate the behavior being visually assessed.

Requests expire after at most 30 wall-clock seconds. World path and a fresh per-load session nonce are checked on the server thread immediately before execution. Files are claimed before execution and retained, so an ID cannot execute twice across restarts. If a process dies between mutation and response, the outcome is unknown: inspect state and logs, **never retry automatically**. The CLI also refuses stale heartbeat, wrong checkout/world and mismatched response content. Responses and claims remain as local evidence; archive them while the client is closed if the folder grows large.

There is no socket, exposed port, RCON password or stdin injection. The files are restricted to the local owner on POSIX. Any process with the owner's filesystem access is trusted; this is a local development interface, not a security boundary against other code running as that user.

## Code and verification

The implementation is in `src/labBridge`, a separate development source set. `LabBridgeClient` requires the development environment and the explicit lab property; only integrated-server lifecycle events bind a world. All world operations use the server thread. File polling runs independently of simulation ticks, including frozen/paused play. The distributed jar contains none of these classes; `verifyLabBridgePackaging` is part of `check`.

`architectVerify` covers protocol expiry/duplicates/closed-world rejection, function policy, real Minecraft command output, stage rejection, zero results and frozen diagnostics. Human perception remains a separate live criterion. Run `python3 tools/lab_client.py --help` for current syntax.

## Moving conversations

The root `AGENTS.md` points here and to the current replay checkpoint. Keep the same worktree until its ignored saves and evidence are no longer needed. A new conversation in another checkout will not have uncommitted files or this client's worlds. Start that conversation with:

> Resume Frozen Dawn from `/Users/jaroncabral/.codex/worktrees/macs-pawn-convergence/minecraft-mod`. Read `AGENTS.md`, `docs/lab-command-bridge.md` and the latest section of `docs/macs-pawn-checkpoint.md` before running commands.

This is repository guidance, not a promise that chat memory transfers. Commit the guide and implementation together when delivering the authorized change; preserve local evidence separately.


## Masked presentation check

For **MACS Signal Check**, read [the viewing protocol](macs-signal-playtest.md). Approved functions are `macs_signal:prepare` at `msignal #stage=0`, `macs_signal:next` at stage1 and `macs_signal:continue` at stage3. Neutral stage/round/timer scores can be read throughout. The lab-native world guard also rejects setup functions in other worlds before mutation. The owner requested concealed conditions; preserve each first account and do not inspect the order file or provide diagnostic explanations until all views are complete. This is production presentation playback, not live dispatch/learning evidence.


## Focused base check

For **MACS Focused Base Check**, follow the latest section of [the base protocol](macs-base-diversion-playtest.md). Approved `macs_pawn` functions are `focus_prepare` at stage92, `focus_status` and `focus_start` at120, and `focus_end` at122. Start enables real late-phase Survival before releasing either saved natural pawn. No protected admission handoff is used. The ninety-second no-admission bound and four-minute total bound run at normal speed. Capture a first description at stage123 before revealing diagnostics. Never run setup, move donors or repeat start after the stage changes.


## Controlled base return

For **MACS Base Return**, follow [the controlled return protocol](macs-base-return-playtest.md). Exact-world `macs_pawn:return_prepare` requires stage92 and stages the two original living pawns before the run; `return_status` and `return_start` require130; `return_end` requires132. The owner gathers supplies for thirty seconds, then walks back at normal speed for upkeep. Start enables Survival before AI release. No admission by thirty seconds or death is inconclusive; total duration is ninety seconds. This is a new controlled-position copy with retained earned history. Never use its staging function in a completed world.


## Scribe check

For **MACS Scribe Check**, follow [the Scribe protocol](macs-scribe-playtest.md). No `macs_scribe` functions are bridge-approved: the owner advances by clicking chat links, and each function only acts at its own `msc #stage`. Agents may read `scores msc`, `maeve-dump` and `architect-dump`. `setup` erases and restores Maeve in this world only; never run it in another save.

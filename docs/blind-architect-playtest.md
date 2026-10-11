# Blind Architect first client check

The combined first view has ended. The owner requested one mechanic per run and a leather helmet. Use [the isolated viewing protocol](blind-architect-isolated-playtests.md) next, starting with **Blind Architect 01 Appearance**; the instructions below retain the original combined-view history and full coverage checklist.

Owning branch: `feat/blind-architect`, checkout `/Users/jaroncabral/Projects/minecraft-mod/.worktrees/blind-architect`. Launch through `runClientLab` as described in [the implementation handoff](blind-architect-plan.md). The owner opens the world and judges the encounter. No human pass is claimed yet.

A fresh **Blind Architect Lab** save is prepared under `run-lab/saves/`. Open it from **Singleplayer** in this branch's Gradle lab client. Preparation automatically builds the enclosed chamber, supplies EVA armor, a filled Mk3 oxygen tank, food and tools, sets real Phase 6 late, and stages one Blind Architect with AI disabled. Click **Start** in the chat when ready (`/function blind_lab:start` also works).

Start switches you to Survival before releasing ordinary Blind AI. It uses normal damage and sensing/mining timing. The view ends after 90 seconds, death, or your Finish click (`/function blind_lab:finish`). End disables that actor's AI and returns you to Creative, preserving its health, position, world and recording. It cannot restart a completed view. No history reset or command-kill cleanup occurs.

This is a controlled summoned-actor fixture with supplied armor/O2, disabled natural mobs and a bedrock outer shell over a stone floor. It does not measure natural spawn frequency. You can construct interior stone/wool barriers with the supplied materials. Keep subtitles enabled and use headphones if available; report your first account before diagnostic explanations.

The save contains freshly generated world metadata (seed `77371337`) and a local fixture datapack. Only generator/version schema came from a closed native test world's metadata: no terrain, player data, entities, scores or Maeve history were copied. `blind-lab-preparation.json` records the prepared file hashes. `tools/prepare_blind_architect_playtest.py` refuses an existing destination and requires the owning client to have no world loaded. Its functions require the development-only exact-world guard; the bridge also enforces the exact world and stage. The native gate parses all five functions and checks that setup refuses other worlds before staging changes.

The first world-list attempt failed because preparation omitted the separate lowercase Anvil `version` field (19133), despite retaining `DataVersion` (3955). The failed save/logs are preserved under `build/blind-architect-evidence/world-load-failure/`. The repair adds only that field and updates the hash manifest; the tool now retains and validates it. A new native regression uses Minecraft's real world-list reader to accept generated metadata and reproduce corruption when the field is removed. Return to the title screen and reopen Singleplayer to refresh a cached failed entry; the metadata repair needs no Gradle client restart. Actual in-world setup/visual acceptance remains pending.

Keep the first view to roughly 60–90 seconds, respecting your oxygen/temperature HUD. The actor should have no visible eyes or nametag, emit positional throat clicks, investigate, then sharpen its estimate as it closes. Standing still or sneaking in connected air can still return an echo. There is no Creative detection. The actor is staged with AI disabled before Start; it has no preseeded player knowledge.

## Owner walkthrough for the prepared first view

1. Use the already-running Gradle lab client. Open **Singleplayer → Blind Architect Lab** and wait for the aqua **Ready** chat message. You should be in Creative inside a stone-floored chamber, wearing EVA armor, with oxygen, food and tools. If setup is missing, report it before starting.
2. Enable **Options → Accessibility Settings → Show Subtitles**, use headphones if available, and make sure **Master Volume** and **Hostile Creatures** are audible. Put the supplied bell on the floor before starting so it is ready to use.
3. Open chat and click the Ready message, or type `/function blind_lab:start`. Confirm that you enter Survival and the actor starts moving.
4. Stand still for the first click, then crouch and move sideways. Walk, then briefly sprint. Turn away and listen for where the clicks come from. Avoid standing still through repeated attacks; this encounter uses real damage.
5. Let it approach once. Observe the face, click cadence, acquisition message and attack warning. Try a sword hit and a nearby bell ring if you have room. These are observations of a combined encounter, not isolated detection or cooldown proofs.
6. Click the encounter chat message or type `/function blind_lab:finish` when done. Death or the 90-second limit also ends it. Describe what happened before reading debug output.

Report: **what you heard; when you thought it found you; whether its movement/attacks felt fair; what the bell did; anything confusing or broken.** A short recording is useful if convenient, but is not required.

Do not try the remaining checklist after Finish: the actor is held and this view cannot restart. Keep this save as evidence. Only the first view is currently prepared. Each controlled comparison below needs its own fresh fixture; terrain, starting positions and equipment should be staged before releasing AI. LAN and lifecycle views need their own suitable fixture rather than the single-player first-view functions.

## Full acceptance checklist after the first account

Use normal simulation speed. Record each condition as pass, partial, fail or inconclusive, with what you actually saw. Stop after unexpected behavior so the save and trace can be preserved. The agent handles fixture preparation and post-view diagnostics; the owner performs the visible actions.

| Check | Owner action in a separately prepared view | Expected result / limit |
| --- | --- | --- |
| Face and positional audio | Look at the head from several angles, then turn your back as it moves. Compare far, searching and close pursuit. In a presentation view, also check a renamed actor and its death. | Opaque blind face, no visible eyes or nametag; clicks track its location and become more frequent as it closes. Judge repetition, volume and warning readability. |
| Stillness, crouching and wool | In matching open-air views, stay still, crouch along stone, then crouch along wool/carpet. | All remain eligible for echolocation. Wool and crouching attenuate vibration; they do not make a player invisible to an unobstructed echo. |
| Echo timing and lost contact | Change position immediately after a click; later enter an isolated chamber and remain quiet. | It can act on an earlier sampled region until another valid return. After losing fresh evidence it searches and eventually disengages; it should not continuously follow the hidden current position. Precise timing needs the trace. |
| Bend versus sealed wall | Compare a connected bent tunnel with a fully sealed chamber. Stage the geometry before Start. | An audible path around a bend can return an echo within range. A sealed wall blocks new echoes. Existing evidence can still justify a temporary search or breach. |
| Mining and vibration | From a sealed chamber, mine briefly, then stop. Repeat with controlled distances, thicker stone and wool attenuation. | Received noise leads to investigation of a region. Thick barriers/wool can suppress it. Silence supplies no fresh location. Geometry and trace must distinguish air noise from vibration. |
| Anonymous decoys | After confident player contact has expired, throw a snowball into a connected empty side chamber. In other views, operate a door, note block or piston. | It can inspect the sound region. Projectile ownership must not reveal the thrower's current position. A decoy need not override an existing confident player echo. |
| Breaching and route handoff | Make noise behind a breakable stone partition, then observe the approach and breach from a safe opening. | Audible tell, slower breaking than the ordinary Architect, then movement through the opening. Protected blocks and disabled mob griefing need separate controls; spinning or repeatedly selecting a cleared breach fails. |
| Melee fairness | Let it close with a fresh echo, then compare with an obstructed or stale-contact view. | Actual close, unobstructed fresh contact permits damage. Hearing an anonymous decoy alone does not permit melee against an unseen player. Judge whether attacks give a usable warning. |
| Bell and explosion response | Ring a nearby connected bell once, then repeat during cooldown. In a separate blast fixture, trigger an explosion and observe the survivor. | Brief pain/stun followed by movement away; bell spam cannot indefinitely extend the stun. Explosion response must not add a duplicate explosion-damage event; confirm damage with diagnostics. |
| Machinery and heat | Operate a real machine in an otherwise quiet fixture. For the heat view, observe approach to an operating core/heater. | Actual machine noise can attract investigation. Close heat exposure burns and causes retreat; attraction alone is not proof of a heat response. |
| Save and chunk lifecycle | After a recorded pursuit, save/quit and reopen that copy. In another view leave far enough to unload it, then return. | Blind appearance persists; fresh targeting must be reacquired after reload. No duplicate actors or lost population claim. Inspect state after the owner's first account. |
| Multiplayer and Maeve | In a separate LAN fixture, use two real Survival players at different acoustic positions; alternate quiet/noisy movement and cover. | Fair acoustic targeting and readable switching. Dumps must verify bounded shared attention and acoustic-only knowledge; the first-view helper requires one player and cannot run this test. |
| Natural spawning | In a separate ordinary late-Phase-6 Survival save, explore loaded, completely dark caves below Y=0 with mob spawning enabled. Log observation time and eligible conditions. | Genuine natural arrivals and acceptable performance. The staged lab has natural spawning disabled. A short no-spawn session is inconclusive because spawning is probabilistic. |
| ERASED lifecycle | Only in a designated disposable lifecycle copy, observe existing Blind actors across the real ERASED transition, including pending contact. | Existing contacts/pursuit clear; surviving actors wander/click and do not resume hunting after being hurt; new spawning stops. Preserve the original save. Future E11 behavior is outside this implementation. |

The existing headless gates cover many mechanical assertions; they do not replace these human audio, visual, combat, persistence and real LAN checks. The first prepared chamber alone cannot establish full acceptance.

Read-only evidence after your first account:

```mcfunction
/fd architect dump @e[tag=blind_lab_actor,limit=1]
/fd maeve status
```

For the agent, run `python3 tools/lab_client.py status`, then `python3 tools/lab_client.py --world 'Blind Architect Lab' snapshot` and documented dumps. Preserve replies and logs under `build/blind-architect-evidence/`. The Gradle terminal cannot execute chat commands. The agent has not opened or started this world. A passing transport reply is not visual acceptance. Post-ERASED behavior and a second real LAN player need separate owner-approved copies/replays; never erase this active world to obtain proof.

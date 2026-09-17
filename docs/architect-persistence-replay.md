# Architect persistence and target-loss replay

These are manual checks using ordinary entities in the existing lab templates. Do not use `/fd architect lab run`, reset, or wilderness commands for these checks: the small lab aborts and pauses its actors on server shutdown, and wilderness setup holds chunk tickets. Neither behavior should be confused with Architect persistence.

Use the current `feat/architect-visual-debugger` build and a NEW Creative Superflat world with cheats, named `Architect Persistence QA`. Work in the Overworld. The commands below replace only the test volume at 1000,100,1000 in that disposable world. Keep only one tagged test actor at a time. No new mod build is required for this checklist.

## 1. Prepare a scaffolding interruption

Enter each command separately:

```mcfunction
/difficulty normal
/gamerule doMobSpawning false
/gamerule mobGriefing true
/gamerule fallDamage true
/gamerule spectatorsGenerateChunks true
/tick freeze
/gamemode spectator
/tp @s 1010 110 1010
/place template frozendawn:lab/scaffold_ascent 1000 100 1000
/summon minecraft:villager 1015.5 108 1010.5 {Tags:["fd_persist_target"],PersistenceRequired:1b,NoAI:1b}
/summon frozendawn:architect 1005.5 101 1010.5 {Tags:["fd_persist_actor"],PersistenceRequired:1b}
/fd architect approach @e[tag=fd_persist_actor,limit=1] @e[tag=fd_persist_target,limit=1]
/fd architect record @e[tag=fd_persist_actor,limit=1] 7
/fd architect debug on @e[tag=fd_persist_actor,limit=1]
/tick step 80
```

If placement reports an unloaded position, allow the destination to load and retry only the `/place template` command before summoning anything. Tick stepping pauses again automatically. Inspect the scene and use `/tick step 10` until the first ice block has been placed and the Architect is still partway up. Do not allow the full climb to finish before interrupting it.

Capture the pre-interruption evidence:

```mcfunction
/data get entity @e[tag=fd_persist_actor,limit=1] UUID
/data get entity @e[tag=fd_persist_actor,limit=1] NoAI
/data get entity @e[tag=fd_persist_actor,limit=1] ScaffoldIce
/fd architect inspect @e[tag=fd_persist_actor,limit=1]
/fd architect dump @e[tag=fd_persist_actor,limit=1]
```

Expected checkpoint: one Architect, AI enabled (`NoAI` absent or `0b`), a nonempty owned-ice list, and partially built ice remaining in the world. Minecraft normally omits `NoAI` when false, so `Found no elements matching NoAI` is expected. The commands print the evidence into the client log. Preserve the dump path.

## 2. Save and reload

While frozen, use Save and Quit to Title, then reopen the SAME world. Immediately freeze again, since normal ticking may resume during loading:

```mcfunction
/tick freeze
/data get entity @e[tag=fd_persist_actor,limit=1] UUID
/data get entity @e[tag=fd_persist_actor,limit=1] NoAI
/data get entity @e[tag=fd_persist_actor,limit=1] ScaffoldIce
/fd architect inspect @e[tag=fd_persist_actor,limit=1]
/fd architect dump @e[tag=fd_persist_actor,limit=1]
/fd architect debug on @e[tag=fd_persist_actor,limit=1]
/fd architect dump @e[tag=fd_persist_actor,limit=1]
/tick unfreeze
```

Do NOT repeat summon, place-template, approach, or reset after reopening. Those would replace the entity or reset the behavior we are trying to test. Dump before restarting any recording: `/fd architect record` clears the current journal, which may already contain recovery decisions from ticks during loading. Attaching the debugger does not reset the AI. If inspection shows recording is disabled, start it after preserving the first dump.

Check the same UUID survived, AI remains enabled, saved ice still exists and is owned, and the Architect resumes useful pursuit/building and reaches the target. Additional ice may already have been placed if the world ticked before you froze it again. A short load delay is expected: the implementation waits 40 ticks before reevaluation/path work. Exact mining progress and a cached path need not survive, but the entity must recover naturally. Ordinary hesitation is acceptable; endless retries, duplicate entities, lost ownership, crashes, or a permanent freeze are findings.

Freeze and dump after recovery, or after roughly 30 seconds without useful progress:

```mcfunction
/tick freeze
/fd architect inspect @e[tag=fd_persist_actor,limit=1]
/fd architect dump @e[tag=fd_persist_actor,limit=1]
```

The target has ordinary health and can die after successful attacks. Confirm a recorded melee hit and its cause rather than assuming every target death means failure. There is no automatic lab `PASSED` banner in this unmanaged arena.

## 3. Repeat while mining

Start a fresh case only after capturing the previous case. Use the roofed `route_opens_mining` template: its bedrock ceiling and side walls prevent scaffolding around the stone plug. The ordinary `wall` template permits a valid scaffold route and does not guarantee mining. Loading this template directly does not activate the managed lab events that open the route. Freeze, remove only these tagged test entities, and restore the fixture:

```mcfunction
/tick freeze
/kill @e[tag=fd_persist_actor]
/kill @e[tag=fd_persist_target]
/tick step 40
```

Wait for the 40 stepped ticks to finish before entering the next commands. This lets death cleanup remove the old actor and its ice before the new fixture is placed.

```mcfunction
/place template frozendawn:lab/route_opens_mining 1000 100 1000
/summon minecraft:villager 1016.5 101 1010.5 {Tags:["fd_persist_target"],PersistenceRequired:1b,NoAI:1b}
/summon frozendawn:architect 1004.5 101 1010.5 {Tags:["fd_persist_actor"],PersistenceRequired:1b}
/fd architect approach @e[tag=fd_persist_actor,limit=1] @e[tag=fd_persist_target,limit=1]
/fd architect record @e[tag=fd_persist_actor,limit=1] 7
/fd architect debug on @e[tag=fd_persist_actor,limit=1]
/tick step 40
```

Use `/tick step 1` until inspection says `mining` has a block target or the breaking overlay appears. Capture the same checkpoint (an empty or absent `ScaffoldIce` list is expected in the mining case), save/quit/reopen, then follow section 2 without reissuing approach. Starting the block's mining animation again is fine; losing the ability to continue is not.

## 4. Actual chunk unloading

Prepare another fresh partial scaffold or mining case. Record the UUID, the numeric entity ID printed by inspect, and a dump while frozen. Do this in the disposable Overworld arena, not the wilderness dimension. Verify the chunk has no forced ticket:

```mcfunction
/forceload query 1000 1000
```

The expected response is that the chunk is not marked for force loading. Then leave the area and allow server ticks to continue:

```mcfunction
/tp @s 5100 110 5100
/tick unfreeze
```

Wait about 30 seconds in the running world, without opening the pause menu. Check:

```mcfunction
/execute unless entity @e[tag=fd_persist_actor] run say Persistence actor is no longer selectable in loaded entities
```

If it is still selectable, wait longer and inspect chunk-loading conditions; do not claim unloading passed. Freeze before returning:

```mcfunction
/tick freeze
/tp @s 1010 110 1010
```

Once terrain has loaded, run the post-reload inspection/record/debug/dump commands from section 2, then unfreeze. The UUID should match, while a changed numeric entity ID supplies evidence that an entity instance was loaded again. If the same instance remained throughout, record the unload check as inconclusive. Require resumed useful behavior and intact scaffold ownership.

## 5. Target death and replacement

Prepare a fresh partial scaffold case. Capture a dump, then while frozen remove its target and provide another reachable target on the ground:

```mcfunction
/kill @e[tag=fd_persist_target]
/summon minecraft:villager 1005.5 101 1015.5 {Tags:["fd_persist_target"],PersistenceRequired:1b,NoAI:1b}
/tick unfreeze
```

Do not force approach again. The old target lock should clear because that entity is gone. Observe whether the Architect returns to sensible behavior and naturally acquires a valid target, rather than remaining committed to the dead UUID. Freeze and dump after the response. Observation/hesitation is permitted; an impossible target or permanent retry loop is not.

A player disconnect is a separate multiplayer check. It needs a server that stays running and preferably an observer keeping the arena loaded, so a missing player is not conflated with world shutdown or chunk unloading. Singleplayer Save and Quit does not establish disconnect handling. Schedule that after the local persistence cases have been inspected.

## Evidence and result boundary

Use `run-lab/logs/latest.log` and the dump directories printed under `run-lab/saves/Architect Persistence QA/architect-debug/`. Save pre-interruption dumps before exiting because the in-memory decision journal is not persisted. Keep both UUID and numeric ID in the comparison. Preserve the existing post-load journal with a dump before starting a fresh recording, if one is needed.

This checklist has been checked against the current command registrations, template geometry, persistence code, and shutdown hooks. For each new build, record which cases were actually replayed and retain their dumps. A stress-suite pass alone does not establish persistence or unloading outcomes. The multiplayer interruption evidence is recorded separately in `architect-lan-findings.md`.

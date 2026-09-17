# Architect normal-world LAN soak

Run one bounded 20–30 minute session after the deterministic and interruption
checks. This exercises production terrain, weather, player construction, and
natural targeting together. It provides gameplay evidence, not proof that every
possible situation is covered. Deliberate observation pauses are expected.

## Launch and connect

If both development clients are already open on the verified build, reuse them.
Otherwise run each command in a separate Terminal, from this worktree:

```sh
cd /Users/jaroncabral/Projects/minecraft-mod/.worktrees/architect-visual-debugger
./gradlew runClientLab --console=plain
```

```sh
cd /Users/jaroncabral/Projects/minecraft-mod/.worktrees/architect-visual-debugger
./gradlew runClientLabGuest --console=plain
```

On the host create a NEW Creative world with cheats, default terrain (not
Superflat), named `Architect Gameplay Soak`. Stay in the Overworld. Publish:

```mcfunction
/publish true survival 25565
```

On the guest join the discovered LAN entry in Multiplayer. Use LAN discovery for
these development identities, as described in `architect-lan-replay.md`.
All commands below are entered by the host. Confirm `/list` includes Dev and
ArchitectGuest.

## Prepare the session

Choose a small area with slopes, trees, and room to build a shelter. While in
Creative, equip both players with food, tools, building materials, and suitable
Frozen Dawn cold-protection gear using the inventory. Build an ordinary shelter
with a door and a short elevated platform nearby. Phase 5 is severe: cold damage
and player deaths are gameplay outcomes to record, not automatically AI failures.

```mcfunction
/gamemode creative Dev
/gamemode creative ArchitectGuest
/difficulty normal
/gamerule mobGriefing true
/gamerule keepInventory true
/fd world set phase 5
/fd world status verbose
/seed
```

Phase 5 runs production snow accumulation and freezing around players. Allow
several minutes for snow to develop; a rendered storm alone does not establish
snow accumulation. Keep the existing snowfall configuration. The status and seed
commands leave context in the host log. Do not use lab/wilderness setup commands.

Stand on clear, solid ground where the Architect should begin, then run once:

```mcfunction
/tick freeze
/summon frozendawn:architect ~ ~ ~ {Tags:["fd_soak_actor"],PersistenceRequired:1b}
/fd architect record @e[tag=fd_soak_actor,limit=1]
/fd architect debug on @e[tag=fd_soak_actor,limit=1]
/fd architect mark @e[tag=fd_soak_actor,limit=1] soak_start
/fd architect dump @e[tag=fd_soak_actor,limit=1]
```

While still Creative, move both players roughly 20–30 blocks from the actor onto
safe ground. Then:

```mcfunction
/gamemode survival Dev
/gamemode survival ArchitectGuest
/tick unfreeze
```

Let it acquire players naturally. Do not force approach, reset its AI, or restart
recording during this encounter. The actor may change targets as players move.

## Play three stages

1. **First 5–10 minutes:** travel around the local hills, trees, and shelter.
   Stay near enough to observe the actor. Let snow build up. Verify useful pursuit
   resumes after its observation periods.
2. **Next 5–10 minutes:** use the shelter and platform. Place and remove ordinary
   blocks, open and close the door, and change routes while it approaches, mines,
   or builds. Let it choose its own solution.
3. **Final 5–10 minutes:** separate the players by about 20–40 blocks, then regroup.
   Disconnect the guest once while the host stays nearby in Survival. After
   observing the response, reconnect the guest through LAN discovery and continue.
   With two clients on one Mac, park one player somewhere observable while moving
   the other. There is no need for simultaneous keyboard input.

At each stage boundary, mark and dump (change the label to `terrain_done`,
`construction_done`, or `reconnect_done`):

```mcfunction
/fd architect mark @e[tag=fd_soak_actor,limit=1] terrain_done
/fd architect inspect @e[tag=fd_soak_actor,limit=1]
/fd architect dump @e[tag=fd_soak_actor,limit=1]
```

The debugger has rolling buffers. These dumps preserve recent behavior at each
checkpoint, not every moment of a 30-minute session. Capture anything suspicious
immediately. A short video helps identify visible movement between trace samples.

## If something looks wrong

Freeze before changing the scene, then describe it in the marker:

```mcfunction
/tick freeze
/fd architect mark @e[tag=fd_soak_actor,limit=1] unexpected_behavior_describe_here
/fd architect inspect @e[tag=fd_soak_actor,limit=1]
/fd architect dump @e[tag=fd_soak_actor,limit=1]
```

Tell Codex what happened and to inspect the logs/dumps. Leave it frozen if the
issue needs investigation; otherwise `/tick unfreeze` continues. Capture before
killing, replacing, unloading, or resetting the actor. If a player dies, respawn
and record the cause. If the actor dies or disappears, preserve the logs and last
dump; end that encounter rather than silently treating a replacement as the same
actor.

## Finish and assess

```mcfunction
/tick freeze
/fd architect mark @e[tag=fd_soak_actor,limit=1] soak_end
/fd architect inspect @e[tag=fd_soak_actor,limit=1]
/fd architect dump @e[tag=fd_soak_actor,limit=1]
```

Host evidence: `run-lab/logs/latest.log` and
`run-lab/saves/Architect Gameplay Soak/architect-debug/`.
Guest evidence: `runs/architect-guest/logs/latest.log`.

Review for unexplained teleports, permanently stuck actions, stale targets after
disconnect, repeated ineffective retries, crashes, and sustained server lag.
Hesitation alone is not a failure; check the action and subsequent progress.
An uneventful session supports stability for these tested conditions. Keep exact
unreplayed cases listed as gaps rather than claiming universal stability.

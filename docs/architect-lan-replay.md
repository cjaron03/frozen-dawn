# Two local players: Architect LAN replay

Use the `feat/architect-visual-debugger` worktree. The host is `runClientLab`
(`Dev`, game folder `run-lab`); the guest is `runClientLabGuest`
(`ArchitectGuest`, game folder `runs/architect-guest`). Both load the same source
and dependencies. These are development identities for local testing.

## Connect

Keep the current host open in the disposable `New World` save. If necessary,
launch it from this worktree with `./gradlew runClientLab --console=plain`.
In host chat:

```mcfunction
/tick freeze
/gamemode spectator
/gamerule spectatorsGenerateChunks true
/publish true survival 25565
```

If already published, keep the current LAN session. In a second Terminal:

```sh
cd /Users/jaroncabral/Projects/minecraft-mod/.worktrees/architect-visual-debugger
./gradlew runClientLabGuest --console=plain
```

In the guest window choose Multiplayer, accept the multiplayer notice if shown,
wait for the LAN world entry, and join that entry. Use LAN discovery rather than
Direct Connect: development sessions have no normal launcher authentication token,
and the client handles authentication failures differently for LAN entries.
If macOS requests local-network permission, allow it for the client.
Do not open the host save from the guest window.

On the host, `/list` must show both `Dev` and `ArchitectGuest`. All subsequent
commands are entered by the host. The guest only disconnects/rejoins when directed.
LAN servers continue ticking when their host opens a menu; use `/tick freeze`.

## Fresh scaffold checkpoint (repeat before each disconnect case)

```mcfunction
/tick freeze
/gamemode spectator @s
/kill @e[tag=fd_persist_actor]
/kill @e[tag=fd_persist_target]
/kill @e[tag=fd_lan_actor]
/tick step 40
```

Wait three seconds for cleanup. Missing old entities are harmless. Then:

```mcfunction
/difficulty normal
/gamerule doMobSpawning false
/gamerule mobGriefing true
/gamerule fallDamage true
/tp @s 1010 110 1010
/place template frozendawn:lab/scaffold_ascent 1000 100 1000
/gamemode survival ArchitectGuest
/tp ArchitectGuest 1015.5 108 1010.5
/summon frozendawn:architect 1005.5 101 1010.5 {Tags:["fd_lan_actor"],PersistenceRequired:1b}
/fd architect approach @e[tag=fd_lan_actor,limit=1] ArchitectGuest
/fd architect stop @e[tag=fd_lan_actor,limit=1]
/fd architect record @e[tag=fd_lan_actor,limit=1] 7
/fd architect debug on @e[tag=fd_lan_actor,limit=1]
/tick step 100
```

`approach` establishes the initial action; `stop` releases its debug target lock
and stops recording; `record` starts a fresh journal. Normal targeting is active
before the simulation advances. Keep the guest stationary on the platform.
Wait six seconds, then capture:

```mcfunction
/data get entity @e[tag=fd_lan_actor,limit=1] ScaffoldIce
/fd architect inspect @e[tag=fd_lan_actor,limit=1]
/fd architect dump @e[tag=fd_lan_actor,limit=1]
```

Require a nonempty ice list, an actor partway up, a living guest, and
`targetLock=null`. If not at that checkpoint, stop and inspect before proceeding.

## A. Disconnect with no other eligible target

Keep the host in Spectator. While frozen, use Escape > Disconnect in the GUEST
window only. Keep the guest application open. On the host:

```mcfunction
/list
/fd architect mark @e[tag=fd_lan_actor,limit=1] guest_disconnected_no_replacement
/tick step 200
```

The list should contain only Dev. Wait eleven seconds, then:

```mcfunction
/fd architect inspect @e[tag=fd_lan_actor,limit=1]
/fd architect dump @e[tag=fd_lan_actor,limit=1]
```

Expected: target clears, mining/navigation commitments are cleared on target loss,
and the actor enters roam/ruin behavior without an endless retry loop. Some movement
toward its last known target position is allowed. The same actor remains loaded.

## B. Reconnect the player

Rejoin the LAN entry in the guest window. Leave the guest in Survival; host remains
Spectator. The world is still frozen from stepping. On the host:

```mcfunction
/list
/tp ArchitectGuest 1015.5 108 1010.5
/fd architect mark @e[tag=fd_lan_actor,limit=1] guest_reconnected
/tick step 200
```

Wait eleven seconds, then:

```mcfunction
/fd architect inspect @e[tag=fd_lan_actor,limit=1]
/fd architect dump @e[tag=fd_lan_actor,limit=1]
```

Expected: normal player reacquisition and an OBSERVE restart. Do not issue
`approach` or reset the recorder here. A long observation pause is intentional;
the trace can prove reacquisition even if pursuit has not resumed yet.

## C. Disconnect while another survival player remains

With the guest connected/alive, repeat the entire fresh scaffold checkpoint above.
If the guest died, respawn before setting up. While frozen at the new checkpoint:

```mcfunction
/tp @s 1005.5 101 1015.5
/gamemode survival @s
```

Disconnect the guest window. On the host:

```mcfunction
/list
/fd architect mark @e[tag=fd_lan_actor,limit=1] guest_disconnected_host_available
/tick step 60
```

Wait four seconds, then:

```mcfunction
/fd architect inspect @e[tag=fd_lan_actor,limit=1]
/fd architect dump @e[tag=fd_lan_actor,limit=1]
```

Expected: selected target changes to Dev and useful pursuit resumes without a
forced approach command. Capture this short window before melee can kill the
host. After the dump, return the host to Spectator:

```mcfunction
/gamemode spectator @s
```

## Evidence

Host: `run-lab/logs/latest.log` and `run-lab/saves/New World/architect-debug/`.
Guest: `runs/architect-guest/logs/latest.log`. Inspect TARGET_CHANGE, target UUIDs,
OBSERVE restart, progress, and actor health. A successful build or launch-file
preparation does not establish that these live multiplayer cases passed.

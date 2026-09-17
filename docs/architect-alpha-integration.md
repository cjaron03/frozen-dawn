# Architect alpha integration — 2026-09-16

The complete Architect stability branch was merged into alpha through PR #84,
superseding the earlier lab-only PR #82. The target-commitment branch from PR #83
is integrated with both development guest launch configurations preserved.

## Behavior reconciliation

An automatic merge selected the committed player before applying the approach
retry cooldown. A committed but unreachable player therefore hid another eligible
player. The new `roamingCommitmentHonorsApproachCooldown` GameTest reproduced that
failure against the uncorrected merge; the other 66 GameTests passed.

Roaming selection now applies suppression to candidate scoring, remembered
retaliation, and the commitment presence set. Suppression forgets that commitment
rather than giving it the return priority reserved for a distance departure.
Normal commitment, distance bookmarks, and armor-based retargeting remain intact.

Three required integration GameTests exercise cooldown selection, two-player
lifecycle changes, and disconnect/rejoin with a displaced pending scaffold. They
use actual server-level players with no-op network handlers and production
selection/placement routines. They do not emulate login packets or establish a
rendered LAN replay. The existing Hearth two-player arbitration case is now also
explicitly required by the gate.

## Automated verification

`./gradlew architectVerify architectMonkey --console=plain` passed on source
fingerprint `b078cab27781874ad7015f4bdae93c134221dbd22275be8ff0a48fdd65badf5d`:
535 unit tests, 67 GameTests with all 62 required cases, and all 500 stress cases
(567 total GameTests in the stress server). No failures or skipped unit tests.

The pre-fix gate failed only the new suppression/commitment regression. Evidence:
`/private/tmp/architect-alpha-before.log`, `/private/tmp/architect-alpha-before.xml`,
and `/private/tmp/architect-alpha-after.log`. Stress reports are preserved under
`build/architect-monkey-reports/bbb0bdab-b740-41bd-961f-3011187cce16/`.

The built and installed Frozen Dawn smoke jars both have SHA-256
`c60ed479650c2163dce595169d24e2a03f1eb34381d7864ece6d106d20f255db`.
Existing Gradle deprecation/toolchain-discovery warnings remain. Restart clients
before using the new jar. Other smoke-profile dependencies were left intact.

## Combined-build LAN replay

Status: both cases passed in the live LAN replay on 2026-09-16, 19:36–19:39
local time. The actual world was `New World`. All four immutable dumps match the
verified source fingerprint above and report zero dropped journal entries.
Host and guest logs confirm real disconnect/rejoin events, with no errors or
exceptions and normal shutdown. The commands below remain available for repeats.

### Recorded live results

A used actor `d3b247bd-27cc-41cc-8269-1441230470a3` and journal
`71ae346a-714a-4cf1-9449-6f52c94fc954`. At tick 100 it was partway up the scaffold,
with two owned ice blocks and a pending lift. Guest loss canceled that lift with
`SCAFFOLD_CANCEL TARGET_LOST` at tick 101. The actor roamed away, reacquired the
returning guest at tick 301, walked to a new column, built six new steps, and
reached melee at tick 403. It killed the guest at tick 445. The largest movement
between five-tick samples after reconnect was 1.036 blocks; no remote lift back
to the old scaffold appeared. Health remained 40 throughout the 0–500 tick trace.

B used a fresh actor `686110c3-3631-4986-9950-1f721011777b` and journal
`6b734244-3c89-4709-9f4a-f73dec12d227`. At tick 100 it also had two ice blocks and
a pending lift. After guest disconnect, it selected the remaining Survival host
at tick 101 without a forced target lock. It completed one valid local scaffold
step at tick 108, then descended from Y=104 to Y=101 and pursued the host. This
local step occurred at its current column; it was not a displaced scaffold lift.
By tick 160 it had moved from (1012.5, 103, 1010.5) to approximately
(1005.083, 101, 1012.852). Health remained 40, with no stuck counter or planner
reinitialization at the end. This verifies the previously missing fresh
mid-scaffold switch to another remaining player.

Evidence directories under `run-lab/saves/New World/architect-debug/`:

- A checkpoint: `run-71ae346a-714a-4cf1-9449-6f52c94fc954-12172080028184028077`
- A after roaming: `run-71ae346a-714a-4cf1-9449-6f52c94fc954-13881331785233401908`
- A complete: `run-71ae346a-714a-4cf1-9449-6f52c94fc954-4380873556777933621`
- B complete: `run-6b734244-3c89-4709-9f4a-f73dec12d227-274014921546627413`

These results complete the two combined-build LAN checks. They do not establish
a full uninterrupted 20–30-minute soak or resolve earlier snowy-shelter encounters
whose dumps lacked their ending checkpoints.

### Launch for a repeat

Close both old Minecraft development clients normally. Launch these from separate
Terminal windows (the directory is now the alpha integration worktree):

Host:

```sh
cd /Users/jaroncabral/Projects/minecraft-mod/.worktrees/2.0.1-integration
./gradlew runClientLab --console=plain
```

Guest:

```sh
cd /Users/jaroncabral/Projects/minecraft-mod/.worktrees/2.0.1-integration
./gradlew runClientLabGuest --console=plain
```

On the host create a new Creative Superflat world with cheats named
`Architect Alpha LAN QA`. In host chat:

```mcfunction
/publish true survival 25565
```

In the guest window, join the discovered LAN entry from Multiplayer. Use LAN
discovery for these development identities. All remaining commands are entered
by the host. Keep the guest alive; respawn in its window if it died.

## Fresh checkpoint — repeat before A and B

```mcfunction
/tick freeze
/gamemode spectator Dev
/gamerule spectatorsGenerateChunks true
/difficulty normal
/gamerule doMobSpawning false
/gamerule mobGriefing true
/gamerule fallDamage true
/fd world set phase 0
/kill @e[tag=fd_alpha_actor]
/tick step 40
```

Wait three seconds for old-actor cleanup. The missing-entity message on the first
run is expected. Then:

```mcfunction
/tp Dev 1010 110 1010
```

Wait for terrain to load, then:

```mcfunction
/place template frozendawn:lab/scaffold_ascent 1000 100 1000
/gamemode survival ArchitectGuest
/tp ArchitectGuest 1015.5 108 1010.5
/summon frozendawn:architect 1005.5 101 1010.5 {Tags:["fd_alpha_actor"],PersistenceRequired:1b}
/fd architect approach @e[tag=fd_alpha_actor,limit=1] ArchitectGuest
/fd architect stop @e[tag=fd_alpha_actor,limit=1]
/fd architect record @e[tag=fd_alpha_actor,limit=1] 7
/fd architect debug on @e[tag=fd_alpha_actor,limit=1]
/tick step 100
```

Wait six seconds, then:

```mcfunction
/data get entity @e[tag=fd_alpha_actor,limit=1] ScaffoldIce
/fd architect inspect @e[tag=fd_alpha_actor,limit=1]
/fd architect dump @e[tag=fd_alpha_actor,limit=1]
```

Require a living guest, a partially built scaffold, and `targetLock=null`.
`approach` only starts the fixture; `stop` immediately releases its target lock,
so all subsequent selection uses the integrated commitment policy. Do not restart
recording until the next fresh case.

## A. Disconnect, roam, reconnect

Keep Dev in Spectator. Disconnect the GUEST window while ticks are frozen. On the
host:

```mcfunction
/list
/fd architect mark @e[tag=fd_alpha_actor,limit=1] alpha_guest_disconnected
/tick step 200
```

Wait eleven seconds, then:

```mcfunction
/fd architect dump @e[tag=fd_alpha_actor,limit=1]
```

Rejoin the LAN entry in the guest window. On the host:

```mcfunction
/tp ArchitectGuest 1015.5 108 1010.5
/fd architect mark @e[tag=fd_alpha_actor,limit=1] alpha_guest_rejoined
/tick step 200
```

Wait eleven seconds, then:

```mcfunction
/fd architect inspect @e[tag=fd_alpha_actor,limit=1]
/fd architect dump @e[tag=fd_alpha_actor,limit=1]
```

Expected: losing the only target cancels the queued lift. Rejoining leads to
natural reacquisition and useful pursuit from the actor's current location, with
no jump back to its former scaffold. A guest death after reaching melee is a
possible successful pursuit outcome; capture the dump before replacing anything.

## B. Remaining player during a fresh scaffold ascent

Reconnect/respawn the guest and repeat the entire fresh checkpoint above. While
frozen partway up the new scaffold:

```mcfunction
/tp Dev 1005.5 101 1015.5
/gamemode survival Dev
```

Disconnect the guest window. On the host:

```mcfunction
/list
/fd architect mark @e[tag=fd_alpha_actor,limit=1] alpha_guest_disconnected_host_available
/tick step 60
```

Wait four seconds, then:

```mcfunction
/fd architect inspect @e[tag=fd_alpha_actor,limit=1]
/fd architect dump @e[tag=fd_alpha_actor,limit=1]
/gamemode spectator Dev
```

Expected: the disconnected commitment is forgotten, Dev becomes the target, and
useful pursuit begins without a forced approach or remote scaffold lift.

The new policy holds an eligible target through mere distance swaps. A much
weaker player can still take priority; that is intentional. A returning player
with equal armor should not automatically reclaim a commitment lost by logging
out (distance-only departures have different bookmark behavior).

## Evidence

Host log: `run-lab/logs/latest.log` in the integration worktree.
Dumps: `run-lab/saves/Architect Alpha LAN QA/architect-debug/`.
Guest log: `runs/architect-guest/logs/latest.log`.

Freeze and dump immediately if anything looks wrong. The ordinary journal is a
rolling buffer, so a start-only dump cannot establish how the encounter ended.
Preserve both successful and failed attempts, and record live results here only
after inspecting the combined-build evidence.

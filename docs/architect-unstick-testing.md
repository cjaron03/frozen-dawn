# Architect unstick recovery smoke test

Branch: `fix/architect-unstick-deadlock` in `.worktrees/2.0.1-integration`.
Build: `./gradlew build`. Server regression gate: `./gradlew gameTestGate`.

Launch directly from the worktree (no jar copying needed):

```sh
cd /Users/jaroncabral/Projects/minecraft-mod/.worktrees/2.0.1-integration
./gradlew runClient
```

The terminal running Gradle shows the development client's logs. This launch uses
the worktree's development profile, which can have different saves from your
regular Minecraft launcher profile.

Restart Minecraft after installing the new jar. The smoke profile uses
`~/Library/Application Support/minecraft-frozendawn-alpha-smoke/mods/frozendawn-window-blizzard-experiment.jar`.

## Original reproduction

Repeat the one-block-gap setup that previously froze the Architect. Use Survival
so it targets you, or use Creative with a nearby villager as the target. Keep the
target stationary while watching the Architect and the client/server log.

Expected behavior:

- A low ceiling over the departure cell must be mined before a step-up. In the
  reported incident at `(-10, 63, 116)`, the roof was at `(-10, 65, 116)`; the old
  candidate scan only checked the empty head cell at Y=64. D* also misclassified
  the jump as WALK. The planner now includes departure clearance in its cost and
  dispatches a ceiling breach, preserving the ledge's support block.
- After 40 active approach ticks without progress, local recovery gets an
  opportunity even when planning or fallback movement keeps resetting local
  stuck tracking. `APPROACH_LOCAL_RECOVERY` identifies this attempt. Normal
  movement and an active mining target are not interrupted. Every 160 stalled
  approach ticks, this recovery can request a local replan.
- A cancelled or completed-but-unsuccessful break logs `APPROACH_BREAK_FAILED` with
  the entity ID, exact candidate and exclusion count. Selecting a block is not a failure.
- Failed A and B stay excluded together. Failing the foot-level wall does not
  exclude the separate headroom block. Both immediate and corridor choices honor exclusions.
- Once the stuck tracker reaches 48 ticks without an active break, replanning takes
  priority over selecting another candidate. It clears local exclusions.
- Recovery does not last forever: four replans without meaningful displacement, or
  600 active APPROACH ticks without meaningful displacement, produces `APPROACH_ABANDON`.
  The reason is `REPEATED_REPLAN_NO_PROGRESS` or `NO_PROGRESS_TIMEOUT`.
- `APPROACH_NO_PROGRESS` appears every 100 active approach ticks (about five seconds
  at 20 TPS), including while mining or following a fallback path.
- The abandoned target is ignored for 200 ticks (about ten seconds). Roaming runs
  during this interval; another eligible target can still be selected.

Meaningful displacement is at least two blocks from the progress anchor. One-cell
ping-pong, jumping in place, local stuck resets, successful breaks and replans do
not reset the global timer. Actual displacement or a new target does. These are
server ticks: lag or time spent in another action makes wall-clock time longer.

## Deterministic give-up check

Use a disposable creative test world: these commands replace a small area near
`0 64 0`. Set difficulty to Normal and stay nearby in Creative. The villager is
the target, so you can observe without being attacked.

```mcfunction
/difficulty normal
/gamemode creative
/tp @s 5 68 5
/fill -2 63 -2 10 63 5 minecraft:bedrock
/fill 0 64 0 2 66 2 minecraft:bedrock
/fill 1 64 1 1 65 1 minecraft:air
/summon minecraft:villager 7.5 64 1.5 {NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Tags:["unstick_target"]}
/summon frozendawn:architect 1.5 64 1.5 {PersistenceRequired:1b,HasObserved:1b,CurrentAction:1,Tags:["unstick_test"]}
```

The Architect is physically sealed in a two-block-tall bedrock pocket. It cannot
walk out, but it must stop approaching the villager and log abandonment within the
recovery budget. Roaming cannot move it through bedrock; this test checks the state
transition and cooldown, not physical escape from an impossible enclosure.

After abandonment, open the east side during the cooldown:

```mcfunction
/fill 2 64 1 2 65 1 minecraft:air
```

Let the cooldown expire. The same target becomes eligible again and the Architect
should leave through the opening and resume pursuit. It must not keep an old
failure exclusion or remain permanently passive.

Clean up only the test entities:

```mcfunction
/kill @e[tag=unstick_test]
/kill @e[tag=unstick_target]
```

## Normal behavior controls

1. On flat open ground, chase a Survival player over at least ten blocks. Movement
   should continue without abandonment or floor mining.
2. Put a normal stone or dirt wall between the Architect and its target. It should
   complete mining, open headroom and continue chasing. A long, legitimate break
   (up to the existing 300-tick mining cap) must be allowed to finish.
3. Reproduce the one-block gap with a second player watching. Breaks, movement and
   abandonment should agree on both clients; read the server log for decisions.

## Log filter

Run from a terminal against the smoke profile:

```sh
tail -f "$HOME/Library/Application Support/minecraft-frozendawn-alpha-smoke/logs/latest.log" |
  rg --line-buffered 'APPROACH_(BREAK_FAILED|NO_PROGRESS|LOCAL_RECOVERY|ABANDON)|D\* BREACH|WALK stuck|Lost target|reacquired'
```

For a dedicated server, use that server's `logs/latest.log` instead.

## Build verification

Full build: 483 unit tests, zero failures or skips. Server gate: 9 GameTests, zero failures. Coverage includes low-ceiling physical
escape without mining the ledge, local escape during an incomplete route, and
sealed-pocket abandonment/cooldown. The low-ceiling reproduction failed before
the clearance fix, returning a WALK step while the Architect stayed below the ledge.
The client reproduction and multiplayer controls still need manual verification.
Compilation reported existing deprecated API warnings.

The smoke-test jar was replaced and its SHA-256 matched the built jar:

```text
93224386f44fea1078a4697666d2506942dfdd5555c8660c1221e5663d98d8b5
```

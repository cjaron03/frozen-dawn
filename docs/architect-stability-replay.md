# Architect bridge and scaffold replay

Use the `feat/architect-visual-debugger` checkout. Restart the client after rebuilding; a running client does not reload Java changes.

Verified: 498 unit tests and all 50 regression GameTests passed. The same 500-case stress matrix passed 498 cases, including all eight slab bridge and all eight scaffold interruption variants. The two remaining failures are the existing gate-reopening target-death cases (`gate_reopens_s7_r1`, `gate_reopens_s1337_r2`); the overall stress command therefore still fails.

## Automated checks

```sh
cd /Users/jaroncabral/Projects/minecraft-mod/.worktrees/architect-visual-debugger
./gradlew build gameTestGate --console=plain
./gradlew architectMonkey --console=plain
```

The required gate includes the two formerly failing slab-bridge seeds at rotation 270, scaffold interruption at rotations 0 and 90, and a mining-safety test that removes the landing after reclamation begins. The stress matrix repeats both scenarios across seeds 7 and 1337 and all four rotations. Check the case results, not just Gradle's exit status: unrelated stress findings remain failures.

## Visual replay

Launch from that same terminal directory:

```sh
./gradlew runClientLab
```

Use a disposable lab world with cheats and an empty area for the 21×16×21 arena. Stop any active wilderness run before creating the small lab. Enter the following commands separately in Minecraft.

### Slab bridge, original failing orientation

```mcfunction
/fd architect lab setup
/gamerule fallDamage true
/fd architect lab scenario footing_slab_bridge
/fd architect lab target static
/fd architect lab seed 7
/fd architect lab rotation 270
/fd architect lab tp
/fd architect debug on
/fd architect lab run
/tick unfreeze
```

Expected: the Architect turns onto the bridge, stays on the half-slab surface, reaches the villager and lands a hit. The lab should report `PASSED` and freeze automatically. No bridge blocks should be mined. For the other original seed, run `/fd architect lab seed 1337`, then `/fd architect debug on`, `/fd architect lab run`, and `/tick unfreeze`.

### Interrupted scaffolding and return descent

After the bridge run finishes:

```mcfunction
/fd architect lab scenario scaffold_interruption
/fd architect lab seed 7
/fd architect lab rotation 0
/fd architect lab tp
/fd architect debug on
/fd architect lab run
/tick unfreeze
```

Allow three minutes of game time (3600 ticks at the normal tick rate). The target first moves to ground level during construction. At tick 1200 it moves back to the platform; at tick 2400 it returns to ground level. The Architect must reach and hit it in every stage, including descending past the ice it built. It may mine its own scaffold where a solid landing exists immediately below; it must take no damage and excavate no original terrain. Expect `PASSED` at tick 3600. Replay with `/fd architect lab rotation 90`, then enable debug and run/unfreeze again. Seeds 7 and 1337 and rotations 180/270 are also covered by the stress matrix.

## Evidence

```mcfunction
/fd architect lab inspect
/fd architect lab dump
```

The dump command prints its report directory. With `runClientLab`, lab reports live under `run-lab/saves/<world>/architect-debug/`. Reports include `SCAFFOLD_RECLAIM` decisions, `reclaimedScaffoldBlocks`, `terrainDestroyedBlocks`, and lifecycle checkpoints with `targetFrozenTicks`. Target freezing is reset only when the harness moves it into a new stage; ordinary combat freezing is unchanged.

If something looks wrong, use `/fd architect lab mark <description>`, `/tick freeze`, then `/fd architect lab dump` while the run is active. Preserve the printed report path.

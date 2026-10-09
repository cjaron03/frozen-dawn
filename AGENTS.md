# Frozen Dawn agent handoff

Use Conventional Commit messages (`feat:`, `fix:`, `docs:`). Do not create branches or PRs with a `codex/` prefix.

Before working, identify the owning checkout, branch, HEAD and current changes. Preserve unrelated work, saved worlds and replay evidence. Run the applicable existing verification gates; distinguish headless checks from a human visual pass.

## Gradle lab client and commands

For local Minecraft iteration, always launch through Gradle from the owning checkout inside the agent's terminal, using Java 21. The owner's default is `./gradlew runClient --console=plain`. For a replay that requires the local lab command bridge, read [docs/lab-command-bridge.md](docs/lab-command-bridge.md) and use the Gradle `runClientLab` variant with `tools/lab_client.py` for authorized diagnostics, reloads and approved test functions. The normal `runClient` does not enable that bridge. The Gradle terminal itself is not an in-game console. Do not launch the normal Minecraft Launcher or an external Terminal app as a substitute.

The owner authorized agents to collect Maeve/Architect dumps and diagnostic state. The owner performs visual playtests and reports perceived behavior. Preserve their unprompted description before revealing a diagnostic explanation. Mutating test steps must match the agreed replay and active world. Never automatically retry a timed-out request, erase history, run a reset, or advance a visible encounter merely to collect evidence.

## Current MACS work

Read the latest sections of [docs/macs-pawn-checkpoint.md](docs/macs-pawn-checkpoint.md) before resuming Pawn Convergence. They identify the owning worktree, active save, actor UUIDs, evidence, remaining acceptance and branch/PR status. The checkpoint is historical evidence: verify current files and runtime before using it. Do not infer merge approval from a successful replay.

Update the repository checkpoint when handing work back or changing the active replay. Keep personal memory separate from the tracked handoff; do not depend on chat history alone.

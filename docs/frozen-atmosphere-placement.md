# Frozen atmosphere placement fix

## Checkout and scope

Owning checkout: `/Users/jaroncabral/.codex/worktrees/frozen-atmosphere-placement/minecraft-mod`.
Branch: `fix/frozen-atmosphere-placement`, based on completed/pushed `acc268e15ac1d4a92cecc36f76080171b9f133cc` (`fix/macs-combat-memory-location`), which includes the custom-sword fix. The root Scribe work and all completed saves are preserved.

The thin deposit previously occupied an unreplaceable block cell. A top-face block-item click therefore placed stone in the next cell above it. The production change adds `.replaceable()` to the registered frozen-atmosphere properties. Ordinary placement now fills the deposit's cell, like a single snow layer. Bright and DARK states share this rule. Replacement does not award a shard; ordinary mining retains its existing shard loot. Food, timber supply, Emergency EVA speech and catch-up are outside this fix.

## Native before and after

Three integration cases use actual ItemStack/UseOnContext placement into a real ServerLevel: Creative and Survival each cover bright and DARK deposits, placement height, item consumption and no replacement drops. The third case checks shard loot from both mined states. They are required in the existing gate manifest.

The unchanged production baseline ran all 250 native cases and failed only the two new placement cases: expected Stone at relative (3,1,3), got Frozen Atmosphere. The mining control passed. Baseline logs/XML/fingerprint are retained under `build/frozen-atmosphere-placement-evidence/baseline/`.

The first fixed attempt passed Survival placement and mining; Creative placement reached the correct cell but its consumption assertion failed. Vanilla makeMockPlayer(CREATIVE) overrides isCreative without setting the infinite-materials abilities. The fixture now calls GameType.updatePlayerAbilities to match a real Creative player. That failed attempt remains under `fixed-fixture-attempt/`; the production fix stayed one flag. The baseline's wrong-cell failures precede and are independent of the consumption assertion.

Final verification: Java 21 `./gradlew architectVerify --console=plain` passed 665 unit cases and all 250 native GameTests, including all 245 required cases/report checks, with no failures, errors or skips. Owner visual placement acceptance is confirmed below; dedicated multiplayer was not exercised.

## Visual handoff

Prepared separate **Frozen Atmosphere Placement Check** under this checkout's `run-lab/saves`. `tools/prepare_frozen_atmosphere_playtest.py` uses only default-terrain seed metadata; no existing chunks, player inventories, learned state or debug history are copied. It refuses to overwrite an existing save. First join builds a sheltered cold-room fixture once, with late Phase 6, progression paused, mob spawning disabled, a standard EVA/O2 kit and clear placement pads. This is a controlled placement fixture, not a natural survival/balance test.

Launch from this checkout using Java 21 `./gradlew runClientLab --console=plain` in the agent terminal. Open the named world from Singleplayer. Walk close to the green/cyan pads and click a thin deposit's top face with stone: stone should fill the same cell, without a one-block lift. Check one pad in Creative, then use the chat's Switch to Survival control for the other; Survival consumes one block. Mine the two deposits on yellow pads with the diamond pickaxe in Survival and collect the shards. Creative mining deliberately produces no loot.

The separate world was prepared for the completed owner pass. No operator reset or repeat is automatic. Preserve the owner's first report before interpreting diagnostics. Client launch session, complete gates, smoke hash and live result will be recorded below.

## Verified build — 2026-10-08 Pacific

Full gate and both baseline/fixed native artifacts are retained under `build/frozen-atmosphere-placement-evidence/`. `verification.json` records the counts, all three new cases, comparison and package identity. The corrected fixture is green; no production changes followed the passing gate. Only the smoke Frozen Dawn jar was replaced, with every dependency jar hash unchanged. Jar SHA-256: `1822bd528879a280e73aea6b36ffd7ed8bcad68e8a9075cae898c7dcabc80ef8`. Pre-existing EventBusSubscriber deprecation/development warnings remain. Owner authorized commit and push to this branch after the successful visual pass.

## Patched client launched — 2026-10-08 Pacific

Java 21 Gradle `runClientLab` is running in agent terminal session88257. Fresh bridge status verified the owning frozen-atmosphere checkout at the title screen, with no world loaded. Client log: `run-lab/logs/latest.log`. The old inspection client saved all dimensions and exited successfully; the unrelated Blind Architect client was not touched. Select **Frozen Atmosphere Placement Check** for the controlled owner pass. The client already has the new classes; a further restart is not required. Owner result is recorded below.

## Owner acceptance and delivery authorization — 2026-10-08 Pacific

Owner first report: **“perfect. it works.”** They then requested **“commit and push to that branch.”** Placement is accepted visually; no additional test or reset was performed. The owner did not separately describe each mode or loot pickup, so the detailed mode/loot coverage remains the recorded native evidence. All played worlds and both the failing baseline and corrected fixed attempts remain preserved locally.

Delivery includes the single production property, three required integration cases, separate visual-world generator and this handoff. Source and test files are unchanged since the passing 665-unit/250-native architectVerify run. Only acceptance documentation and ignored verification metadata changed afterward. Delivery branch remains `fix/frozen-atmosphere-placement`; it has not been merged into the integration branch by this task.

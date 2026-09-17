# Maeve Slice 2: belief explanations

Implements developer observability from [Maeve Director — Source of Truth](https://www.notion.so/39d7cfaa890181c1bad4f6babad80880), §§9.15 and 9.19. Branch `feat/maeve-diagnostics` targets `feat/maeve-director`. Slice 1 was merged through [PR #86](https://github.com/cjaron03/frozen-dawn/pull/86).

## Commands

Permission level 2 is required. Existing status and raw dump commands remain available:

```text
/fd maeve status
/fd maeve dump [online-player-name-or-uuid]
/fd maeve explain PLAYER_PREFERS_RANGED [online-player-name-or-uuid]
/fd maeve explain PLAYER_USES_RECOVERY_UNDER_COVER [online-player-name-or-uuid]
```

An omitted subject selects the invoking player. Console use requires an explicit subject. Offline profiles can be selected by UUID; online names resolve through the server player list. Pattern names are case-insensitive. Tab completion suggests the two implemented patterns and any additional retained patterns belonging to the invoking player. A valid stored pattern without an authored description still exposes its actual score and event codes, with its missing semantic description stated explicitly.

Each explanation answers:

- **What she believes:** the hypothesis and current confidence. Confidence is a heuristic score, not a calibrated probability.
- **Why:** contributing encounter counts and retained supporting observations, with action, observer UUID, shared encounter UUID, dimension, player position and overworld game tick.
- **What contradicts it:** the contradictory-action rule and actual retained contradictory events. A rule is labeled separately from an event that really happened.
- **What is uncertain:** confirmation age, never-confirmed or stale state, missing older history, and the limits of the observation. Recovery use reveals neither hidden health nor healing effectiveness nor room identity.
- **How the score was obtained:** the stored score and update tick, support/contradiction increments, clamp, decay grace, effective decay start, elapsed ticks and half-life calculation.

Director objectives, selected strategies and Architect packets are explicitly marked as unimplemented. No gameplay system consumes these beliefs yet.

## Scoring and history

The recording defaults remain support `+0.20`, contradiction `-0.35`, clamped to `[0,1]`. Each player/pattern/encounter contributes at most once per polarity, across all observers. Repeated support refreshes confirmation and replaces that encounter's retained support without adding confidence or count. Encounters expire after 600 ticks without qualifying local contact.

The explanation uses the same stored score and timestamps as recording. Effective decay begins at `max(last score update, last confirmation + 480000)`. At current overworld game time, elapsed decay ticks are clamped to zero; current confidence is `stored * 0.5^(elapsed / 480000)`. The 480,000-tick grace and half-life each equal 20 Minecraft days. A never-confirmed belief exposes its saved `-1` confirmation sentinel and is labeled unconfirmed. Repeated reads do not materialize or compound decay.

Each belief keeps at most eight provenance entries. Counts can exceed the retained history. Explanations report how many counted contributions have lost their details; they cannot reconstruct every historical score change after eviction. No discarded events are invented and no additional history is collected.

## Save compatibility, erasure and multiplayer

Save schema remains version 1. Added snapshot fields are derived from existing NBT fields and are never serialized separately. Existing Slice 1 worlds load without migration. The same authoritative phase, activation, debug and forced-erasure rules apply. ERASED output contains only lifecycle and zero counts; debug reversal starts with an empty record. Empty profiles and missing patterns produce an explicit empty result. No explanation or archive is stored in the world. Operator chat and normal logs may retain command output under their existing logging rules.

All access goes through `MaeveDirector` on the server thread. Collaborators remain package-private; the facade remains subject to its 300-line Gradle check. Immutable snapshots do not expose the mutable belief store. A diagnostic read can perform the existing lifecycle synchronization, including required erasure, but does not otherwise alter confidence, contribution limits, timestamps, contacts or persistent data.

Player selection is by UUID; one player's explanation cannot include another player's provenance. Commands are operator-only and do not broadcast to other operators. No network protocol or client rendering changes are needed.

## Performance

Explanations run on demand. No new tick work, entity queries, line-of-sight checks or chunk loads are added. A subject snapshot visits at most 16 beliefs with eight events each, then formats one belief into at most 40 lines. Existing status, dump and subject suggestions remain bounded by the 128-profile storage cap (plus the online player list for name suggestions). Every result list is immutable. Existing recording/query bounds are documented in [Slice 1](maeve-belief-store.md).

## Verification and replay

Run the existing seeds and fixtures:

```sh
./gradlew architectVerify architectMonkey --console=plain
```

Unit checks cover explanations of both polarities, score decay after a stale contradiction, unchanged NBT across repeated reads, replaced support, truncated history, never-confirmed recovery, unknown/missing patterns, isolated player selection and ERASED output. The required GameTest manifest adds `maeveExplainCommandsTraceEventsAndRespectErasure`: it invokes actual damage/item completion hooks, then the registered Brigadier command through player and console sources, checking permission denial, subject handling, causal provenance, hidden recovery exclusion, read-only persistence, erasure and debug reversal.

For the Slice 2 exit check, reopen the saved normal-play encounter from Slice 1 using this build. Run both explanation commands above and compare the action, observer, encounter, position and tick against that encounter's raw dump. Finish recovery under open sky with a visible real Architect to obtain a contradiction, then explain recovery again. The output must distinguish observed support, the new contradiction and the resulting confidence, and must describe the current uncertainty. Delivery evidence records the actual build, commands and results; automated success is not presented as a live client replay.

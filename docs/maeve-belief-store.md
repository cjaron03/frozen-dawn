# Maeve Slice 1: observation and tactical memory

Implements recording from [Maeve Director — Source of Truth](https://www.notion.so/39d7cfaa890181c1bad4f6babad80880), especially §§4, 9.1, 9.3a and 9.16–9.19. Branch `feat/maeve-belief-store` targets `feat/maeve-director`. No AI behavior consumes these beliefs.

## Observation rules

| Pattern | Supporting action | Contradictory action |
| --- | --- | --- |
| `PLAYER_PREFERS_RANGED` | Player-owned projectile actually damages the observing Architect | Player directly damages that Architect with melee |
| `PLAYER_USES_RECOVERY_UNDER_COVER` | Player finishes drinking a healing/regeneration potion, or eating a golden/enchanted golden apple, where `canSeeSky` is false | The same completed action where `canSeeSky` is true |

Combat uses NeoForge `LivingDamageEvent.Post` and positive final damage. Recovery uses `LivingEntityUseItemEvent.Finish`. Canceled or ineffective damage, interrupted use, milk, unrelated potions, and passive regeneration supply no evidence. Recovery means an observed item action; it makes no claim about the player's health or whether healing was needed or effective. Sky visibility describes the action position, not a room or shelter model.

The player must be alive in Survival or Adventure. The real, enabled Architect must be in the same dimension, within 48 blocks, and have its own current line of sight to the player. Only ordinary Architects qualify. Masters are independent guardians and, along with mind copies and Aggregate reinforcements, do not report tactical observations (guardian decision, 2026-09-17). Combat accepts a valid final hit while its victim is dying. Evidence is published immediately and remains after the witness dies. Recovery may have several witnesses, but that does not multiply its contribution.

No observation reads inventories, health polling, or the Architect's target-position memory. Line-of-sight checks first reject paths across unloaded chunks. Recovery queries only loaded local entities. Observation never requests a chunk load.

## Encounters and confidence

Encounters belong to player UUIDs and are shared by all observers of that player. A new encounter starts after 600 overworld game ticks without qualifying local contact. Existing witness contacts are checked once per second. Loaded witnesses must still pass the same range, dimension, player eligibility and sight checks. Death does not retract their earlier evidence.

Each pattern earns at most one supporting and one contradictory contribution per encounter, including after a save/reload. Ordinary support adds `0.20`; eligible active-scout behavioral support adds `0.30` under the [mission rules](macs-reconnaissance.md#soul-pulse-and-scout-evidence-calibration-2026-09-23). Contradiction subtracts `0.35`; confidence stays in `[0, 1]`. Repeated supporting actions in the same encounter refresh confirmation time without adding confidence or evidence count. The credited event remains alongside the latest zero-weight `VERIFY` entry within the history cap; later scout evidence cannot upgrade an ordinary contribution in the same encounter. Contradictions do not restart the confirmation grace period.

After 20 unverified Minecraft days (480,000 ticks), confidence decays exponentially with a provisional 20-day half-life. Time comes from overworld **game time**, independently of daylight commands and the apocalypse timeline. Decay is evaluated on read and materialized before a new observation, so repeated diagnostic reads cannot compound it. These are recording defaults; the modest active-scout adjustment is the owner-approved trial described above.

Each belief retains its pattern, confidence, evidence/contradiction counts, confirmation/observation times, encounter contribution flags and up to eight provenance records. Provenance includes observer UUID, shared encounter UUID, dimension, player block position, game tick, observed action, polarity and optional nominal confidence weight. Active mission actions include the mission UUID; old provenance with no saved weight remains explicitly unknown. Counts can exceed the retained history; the dump explicitly shows retained provenance rather than claiming an unlimited archive.

## Persistence, lifecycle and erasure

`com.frozendawn.maeve.MaeveDirector` is the package's sole public entry point. Collaborators are package-private. The existing Gradle `check` path enforces its 300-line maximum. Events, commands and lifecycle integrations call the facade; external systems cannot obtain the store.

The overworld saves version 1 data in `data/frozendawn_maeve.dat`. Worlds without that file start empty. The existing `ReturnedHearthSavedData` violation ledger remains separate. Unknown future tactical schemas are rejected on load.

Activation uses `PhaseManager.isVacuumActive` (Phase 6 late). The activation flag persists, so moving the apocalypse timeline backward does not put Maeve back to sleep. Before activation, no tactical store is allocated.

The authoritative `PostMaeveWorldState.markErased` operation immediately clears and releases the tactical store and contact state, and marks its SavedData dirty. Saving ERASED data contains no former beliefs or contacts. Every facade access synchronizes with authoritative world state, so an interrupted save with an ERASED ledger and stale tactical file cannot expose old beliefs. Repeated erasure is harmless. Debug erasure, debug reversal and the forced-erased setting clear the same state. Reversing an override starts with empty tactical memory. Server shutdown drops the per-server facade cache.

In-world erasure/reset cannot recover deleted beliefs. Restoring a **complete external world backup** restores that backup's saved state. The mod writes no erasure marker outside the world and creates no permanent belief archive inside it. Ordinary save durability still applies: a crash before either authoritative state is saved is not a completed disk transaction.

## Multiplayer and performance bounds

Players have isolated UUID profiles; online names are only a diagnostic convenience. Encounters and limits persist across reconnects/reloads. All mutations run on the server thread. Returned snapshots and lists are immutable.

Storage holds at most 128 player profiles, 16 beliefs per profile and eight provenance entries per belief. Profiles evict by oldest local observation/contact time, then UUID; beliefs evict by oldest observation time, then pattern name. Both tie-breaks are deterministic. The two initial patterns leave room for later recording additions without changing these bounds.

At most eight witness UUID/dimension contacts are retained per player, with no entity references. Once per second, contact refresh performs at most 1,024 entity lookups/LOS checks. A recovery event queries at most 16 local Architect candidates and checks at most 16 sightlines, ordered by UUID among the returned candidates. Crowds beyond these bounds may lose witnesses; they cannot increase contribution counts. Combat checks only the damaged Architect. At 48 blocks, each LOS preflight checks at most a 4×4 chunk rectangle.

## Operator diagnostics

Permission level 2 is required:

```text
/fd maeve status
/fd maeve dump
/fd maeve dump <online-player-name-or-uuid>
```

An omitted dump subject selects the invoking player. Console invocation must name a subject. Status reports lifecycle and bounded counts. A dump includes confidence, counts, confirmation age and every retained provenance entry. Empty/ERASED state reveals no former tactical contents. Commands print to operator chat/console; they do not create a world archive. Server/client logs can retain command output under ordinary logging rules.

## Verification and replay

Required command, using the existing seeds and fixtures:

```sh
./gradlew architectVerify architectMonkey --console=plain
```

The required manifest includes four actual event integration cases: witnessed damage and isolated players; completed visible recovery; excluded Masters/projections/reinforcements versus ordinary witnesses; and lifecycle erasure with unchanged permanent conduct. The tests invoke Minecraft's real damage and item-use completion paths. They cover cancellation, occlusion, range, duplicate witnesses, fatal hits, phase regression, reload deduplication, stale saved tactical data and debug/forced erasure. Unit tests cover confidence, decay, encounter persistence, deterministic caps and NBT boundaries. Replacing SavedData and dropping the facade cache tests shutdown cleanup without retaining stale instance state.

The fixtures acquire their own isolated chunk tickets and wait for entity sections to finish loading before changing shared SavedData. Tickets are released on success or failure. This avoids terrain being present while local entity queries still cannot see newly added witnesses. The recovery fixture also waits for Minecraft's actual lighting worker after roof edits before asserting `canSeeSky`; it does not substitute a mocked cover result.

For the live exit check, launch `./gradlew runClientLab` from this worktree. Use a disposable world, activate Phase 6 late, and inspect `/fd maeve status`. After activation, the phase may be moved backward to avoid vacuum hazards; sticky activation is part of this slice. Use a real Architect with AI enabled and a Survival player within 48 blocks. Perform ordinary projectile/melee and recovery actions, then inspect the dump. Finish a recovery action behind an opaque wall before that polarity has contributed to the encounter, verify no evidence, then repeat with a clear sightline. This distinguishes occlusion from encounter deduplication. Match each retained entry to the player action, observer, position and game time.

Fresh automated and live replay results are recorded with the slice's delivery evidence. Automated success alone does not satisfy the live exit criterion.

# MACS second-favorite exit — implementation plan

Status: **planned; gameplay implementation and acceptance pending**. Branch: `feat/macs-second-favorite-exit`; PR target: `feat/maeve-director`. Base: `317e5eab64bb1f93e2fd6663bb48a1e56b720f4c`, after merged PR #96. This initial change contains the plan only.

Contract: [MACS Source of Truth](https://www.notion.so/39d7cfaa890181c1bad4f6babad80880), §9.13f, with §§9.2, 9.3, 9.13a, 9.16–9.19 and 12. Progress and acceptance: [Notion implementation plan](https://www.notion.so/3e77cfaa89018139839fdd58563e3ee7). The proposals below implement the existing contract; they do not amend its locked rules.

## Player-facing result

Maeve learns the usual exit A. When an Architect actually waits at A and an eligible observer sees the player leave through B instead, that episode can teach a conditional response. At **0.90 historical conditional confidence**, a later Architect can wait at B. Returning to A leaves it guarding the wrong exit.

A and B are explanatory labels for observed access bearings and coordinates. The system does not identify named doors or rooms. This is the higher-confidence variant of access interception, with the same single commitment and one waiting Architect. Ordinary interception retains its 0.75 gate. Other eligibility, attention and prior-outcome checks still apply.

## Existing implementation to reuse

| Existing code | Reuse |
| --- | --- |
| `SpatialObservations`, `WorldModel` | Continuous witnessed crossings, observed shelter centroid, access bearings, known open points and local obstruction discovery |
| `BeliefStore`, `BeliefPolicy` | Confidence, decay, per-encounter contribution limits, bounded provenance and deterministic eviction |
| `LearningCoordinator` | Conditional-observation lifecycle and inconclusive outcomes; its current episodes concern withdrawal/pursuit only |
| `CommitmentPolicy`, `CounterVariantPolicy`, `CommitmentCoordinator` | Frozen history, one encounter bet, variant selection, cooldowns, performance and attention admission |
| `ArchitectSpatialCommitment` | Local approach, arrival, waiting, close defense and discovery of an obstructed remembered exit |
| `MaeveSavedData`, diagnostics | Persistence, lifecycle erasure, operator dump and explanation |

The missing relationship is **failed interception at A followed by a witnessed alternative B**. A second-most-used bearing or an ordinary crossing at B does not establish that relationship. No new animation or general pathfinding rewrite is planned.

## Implementation sequence

1. **Capture the causal episode.** Open a bounded episode only when an ordinary access-interception Architect arrives and begins its watch at A. Retain the player, encounter, actor, dimension, observed shelter context and original access target. A qualifying outward crossing at another bearing B during that active watch supplies both the failed-interception outcome and its alternative. The same eligible observer must see both ends of the crossing. A second eligible observer may report that complete crossing, but observations from different actors cannot be joined to reconstruct hidden movement. Resolve the causal event before ordinary bearing updates discard or contradict its context.
2. **Store the conditional belief.** Retain the A-to-B relation, confidence, counts and both interception/crossing provenance. Conditional beliefs share the existing 16-belief profile limit, eight-entry provenance limit, deterministic eviction and encounter deduplication. Reuse production scoring and decay. Scope each relation to the observed shelter area and dimension, with explicit invalidation of transient context when that local shelter estimate resets. A centroid shift or a move to another base must not reinterpret old evidence as a different pair of exits.
3. **Select and execute the variant.** Freeze eligible conditional beliefs at encounter start. At 0.90, offer the alternative within the access family and resolve its bearing through remembered local open points. Preserve candidate bounds, recoverability, attention, cooldowns and performance selection. Below 0.90, only the ordinary 0.75 access interception can qualify. The conditional gate uses its own history; it does not require A's current ordinary confidence to remain at 0.75 after repeated observed escapes. Carry the original A context in the directive so execution and outcome reporting never infer it from the player's current position. Reuse the spatial executor and existing interruption rules.
4. **Explain, verify and replay.** Extend dump/explain output to show the frozen A-to-B hypothesis, actual watch arrival, crossing observer and times, confidence and rejection reason. Add save migration, automated cases and an isolated live fixture. Keep the facade below its enforced 300-line limit by placing implementation in package-private collaborators.

## Evidence and counterplay boundaries

Only ordinary first-level interception opens a new A-to-B learning episode. A second-favorite watch can be confirmed or disproved, but cannot create a B-to-C conditional chain or train an equivalent recursive counter through another key. An observed escape through A or another exit contradicts the prediction. Returning to A must leave the Architect at the wrong place under the existing spatial commitment rules; it cannot immediately switch to A or buy another bet.

An order without arrival, an approach failure, expired watch, player disappearance, generic failed damage trade or map survey alone is not evidence that the player switched exits. Release, damage interruption, death, attention loss and reload close an unfinished episode as inconclusive. An already published valid crossing remains evidence. Hidden crossings and inward crossings do not support the alternative-exit prediction. Existing observer eligibility, loaded-space, distance and sight checks remain authoritative; Masters and other excluded roles do not participate.

Keep ordinary bearing confidence, conditional confidence and strategy performance distinct. Outward crossings at B currently contradict the ordinary A bearing and can cause cooldowns. Preserve the episode's original A context through that update. Prove that repeated legitimate episodes can reach the new gate under existing learning rates and cooldowns; do not bypass them to make the fixture progress.

## Persistence and performance

The current save version is 6. Version any added persistent schema explicitly; earlier saves begin with no conditional exit history while retaining existing beliefs and violation memory. Persist encounter contribution limits and spent commitments. Do not resume transient crossing samples or active learning episodes after reload. ERASED clears all new tactical records and caches alongside the existing store, with no archive inside the world.

Keep at most one active exit episode per player's shared encounter, bounded by the existing player/contact limits. Update it on actual watch lifecycle and accepted crossing events. Resolve candidates through the existing bounded spatial model, never global entity scans or chunk loading. Conditional variants must not silently expand the candidate cap or crowd every ordinary candidate out of its bounded list.

## Required verification before readiness

- Causal learning: arrived A watch plus witnessed outward B crossing; hidden/inward crossings, approach failure and survey-only observations rejected; duplicate observers deduplicated; player, dimension and shelter isolation.
- Policy: 0.90 boundary and 0.75 fallback, history frozen before contact, no same-encounter unlock, one bet/actor, competing alternatives, cooldown and performance handling, no recursive learning and original-exit counterplay.
- Lifecycle: old-save compatibility, NBT round trips, persisted contribution limits, reload without a synthetic crossing or renewed bet, deterministic eviction, erasure and unchanged violation memory.
- Native integration: real presence hooks, actual spatial arrival/wait, wrong-exit observation, damage release, locally discovered blocked alternatives, role exclusions and regression coverage for existing counter families. Add the cases to the required GameTest manifest.
- Run `./gradlew architectVerify architectMonkey --console=plain` with the existing seeds and fixtures on the implementation commit, and publish fresh required-check results for that commit.

The live acceptance sequence is: establish A through ordinary crossings; witness an escape through B during an actual A interception; inspect its causal provenance; repeat qualifying encounters until historical conditional confidence permits a later B watch; then return to A and demonstrate the exploitable wrong prediction. Include a hidden-crossing control. Preserve first impressions, direct chat dumps, actor traces and the save.

Production confidence and cooldown values stay in effect. Only empty encounter gaps may use tick sprint. If a separate seeded fixture is useful to inspect B waiting immediately, label that as execution evidence; it cannot substitute for learning through ordinary actions. Test commands and a new client build will be supplied when the fixture exists. There are no new gameplay test results in this planning commit.

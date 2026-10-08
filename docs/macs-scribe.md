# MACS Scribe Architect

Implemented on `claude/confident-euler-s2rm17`, branched from `feat/maeve-director` at `98165da` (after PR #102). Contract: [MACS Source of Truth](https://www.notion.so/39d7cfaa890181c1bad4f6babad80880), §9.4b, with §§4, 9.1, 9.3, 9.13a, 9.15, 9.16a and 9.18. **Automated gates only; no human visual pass yet.**

## Owner decisions (2026-10-06)

- **Records outlive ERASED.** §9.4b wins over the §9.18 line that no item may display former belief state. ERASED still wipes the belief store completely; dropped records and maps keep their own frozen copy and stay readable.
- **Appearance:** a natural Architect spawn is designated as the Scribe. No separate spawn path, so existing population caps, Hearth exclusions and spawn rules apply.
- **Lexicon:** new Thaeven roots are proposed below and in the Notion lexicon, marked as proposed for review.

## Behavior

**Gate.** `ArchitectSpawner` asks `MaeveDirector.designateScribe` for each natural spawn, with the player the spawn was placed near. Designation needs ACTIVE Maeve, at least **3** beliefs about that player at current confidence ≥ 0.75 (count provisional), no active Scribe, and **3 in-game days** since the last one ended. Masters, mind copies, Hearth roles, Aggregate children, NoAI actors and Creative/Spectator subjects never participate. One claim exists server-wide.

**Watch target (Causal).** Chosen from the world model only: the strongest OPEN access point within 32 blocks of the observed shelter centroid (`OPENING`), else the centroid (`SHELTER`), else no point (`ROUTE`: it watches from where it stands). No player position enters the order.

**Local executor** (`ArchitectScribeController`):
- Walks out to a post on a 20-block ring around the target: open sky, standable, loaded, no known heater within 8 blocks, preferring line of sight. Walking (travel, flight, departure) uses the scouts' and pawns' observed-walk planner in 12-block segments, so snow layers and one-block steps are walkable; it never places or breaks blocks. Stalling 100 ticks means it watches from where it is. The watch decision logs the post and why ring columns were rejected.
- Watches for 2400 ticks (2 min), facing the subject when it can see them within 48 blocks, otherwise the remembered point. Then it departs and is discarded once no player is within 32 blocks (or 16 after 600 ticks).
- Flees any visible Survival/Adventure player within 12 blocks, and anyone who strikes it from range. It never initiates combat.
- **Cornered** (struck within 3.5 blocks with no safe heading away, or while flight has stalled for 30 ticks) it defends itself under the §9.13a local-defense exception: ordinary combat against that attacker until 200 ticks pass without damage, then it resumes fleeing. It still holds the slate.
- White eyes (same 2x3-pixel recolor as the scout's purple) and a held slate: the only Architect holding something that is not a weapon.

**No new evidence.** Watching adds no observations and takes no focus slot, counter-family slot, commitment, reconnaissance mission or convergence assignment. Once struck it is an ordinary Architect being hit: existing combat/danger observation applies to it like any other.

## Drops (on death)

`MaeveDirector.scribeNotes` reads the store once inside the death-loot path; the items keep a copy and never consult it again.

**Record** (`frozendawn:scribe_record`, data component `frozendawn:scribe_record`):
- At most 5 beliefs, highest current confidence first (ties by pattern key), floor 0.20 (one witnessed support). Wrong beliefs are written exactly as held.
- Headed `Vel-thae.`; never the username. Field-note register, no address.
- Confidence is phrasing: ≥ 0.90 repeats the verb (`Ka vel-an. Vel-an.` → "Carries a blade. Always."), 0.75–0.90 is flat, below 0.75 leaves the transmission open (`Mor vel-thaeven…` → "Mends beneath cover. Perhaps.").
- Only authored patterns are written; an unknown future pattern is omitted rather than guessed.
- Use: with a Thaeven Translator in the inventory, each reconstruction is shown above its raw line; without one, raw Thaeven only, and the translator recipe is discovered (same as carriers). Lines fade in unless reduced ink animation is enabled.

| Pattern | Thaeven | Translation |
| --- | --- | --- |
| `PLAYER_PREFERS_SWORD` | Ka vel-an. | Carries a blade. |
| `PLAYER_PREFERS_RANGED` | Eth orren. | Strikes from afar. |
| `PLAYER_PURSUES_WITHDRAWING_ARCHITECT` | Vesh-thae senn. | Follows the one who withdraws. |
| `PLAYER_USES_RECOVERY_UNDER_COVER` | Mor vel-thaeven. | Mends beneath cover. |
| `RETREAT_BEARING_E` | Vel-sorr aren thaeven. | Leaves by the east opening. |
| `EXIT_AFTER_W_E_*` | Eth-sorr aren vaen. Vel-sorr aren thaeven. | Watched at the west opening, leaves by the east. |

**Marked map** ("Marked Map", a locked vanilla `filled_map`): scale 1, centered on the observed shelter centroid (else the death position), painted once from already-loaded chunks with vanilla surface shading; no chunk is loaded for it and unloaded areas stay blank. Up to 8 marks per label from the world model: OPEN access points as blue pointers facing outward along the witnessed crossing, DANGER_ZONE as red crosses, HEAT_SOURCE as red points. Legend lore: openings, heat, losses. Locked, so neither terrain nor marks update.

## Proposed Thaeven roots

Grammar stays short, verb last, no tense. Leaving is written with *thaeven* (to return): the lexicon notes Thaeven has no word for going somewhere new.

| Root | Meaning | Sensation |
| --- | --- | --- |
| sorr | side; quarter; the way a thing faces | turning the head toward |
| aren | opening; a way through a wall | moving air; light through a crack |
| ka | edge; blade | cold metal; a line that parts things |
| senn | to follow; to walk in another's steps | footsteps landing in your own |
| mor | beneath; enclosed; under stone | weight overhead; stillness; no sky |

Compounds of existing roots: *Maeve-sorr* north (the cold quarter), *Vel-sorr* east (the warm quarter, where light returns), *Vesh-sorr* south (the high quarter, where the sun stands), *Eth-sorr* west (the void quarter), *Vesh-thae* the departing one, *vel-thaeven* to mend (warmth returns), *vel-an* to carry (hold what's left), *eth orren* to strike from afar (take across the gap).

## Lifecycle, persistence and diagnostics

- ERASED ends the claim immediately: a living Scribe walks away and is discarded without drops; killing it after ERASED drops nothing. Existing records and maps are untouched. Gone after E11 through the shared `ConvergenceLifecycle.isArchitectExistencePermanentlyEnded` hook (E11 itself is not built).
- `MaeveSavedData` version 9 adds the erasable `scribe` tag (active claim, last end time). Older saves load with no history. The entity carries `macsScribe` in persistent data. Reload restarts the local watch; the claim's one-day lifetime (24000 ticks) bounds the whole appearance, and an expired claim starts the cooldown from its lifetime end.
- `/fd maeve dump` adds `SCRIBE` lines: last decision (designation, gate refusal reason, end), cooldown remaining, active claim with watch label/point and expiry. Actor journals record `MAEVE_SCRIBE_WATCH`, `_CORNERED`, `_DEFENSE_ENDED`, `_DEPART`, `_GONE`.
- `MaeveDirector` remains the only external entry point (277 lines, below the 300-line check).

## Verification

- Unit: `ScribeRecordTest` (ordering, five-line cap, floor, phrasing tiers, verb-last spatial lines, unknown patterns omitted, wrong beliefs as held, gate/cooldown/lifecycle, claim expiry and reload, map mark selection and bounds).
- Required native GameTests (`MaeveScribeGameTest`): gate and single claim with Master/Creative/ERASED refusals and no post-erasure drops; real death drops with exact record lines, locked centered map with three marks and legend, unchanged after new evidence and ERASED; real AI watch, flee on approach, keeps fleeing when struck from range, defends when cornered; leaves after ERASED without drops.
- `./gradlew build gameTestGate --console=plain` passed on 2026-10-06: 672 unit tests (665 before this slice), every `check` task including the facade budget, and 242 GameTests with all 237 required cases verified (baseline 238/233). SavedData version tests now pin version 9. `./gradlew architectMonkey` passed: 500 unchanged seeded stress cases plus the 242 native cases repeated (742 GameTests).

## Pending

- Design question: full-block drifts (early phase 6 snow, up to three blocks) have no observed-walk route, so a Scribe boxed in by them cannot travel or flee and watches or defends where it stands. Scouts and pawns share this limit. Climbing or digging would be new behavior; not added without an owner decision.
- Human visual pass ([MACS Scribe Check](macs-scribe-playtest.md)): white eyes and slate in hand, watch/flee readability, record screen with and without the translator, map legibility.
- Calibration (§9.20): gate count 3, cooldown 3 days, lifetime 1 day, watch 2 minutes, ring 20, flee 12.
- §17.7 `Remembered.` lines wait for Maeve 4 long-term memory, which is not built.
- Owner review of the proposed roots, and of the §9.18 wording now that §9.4b records persist.

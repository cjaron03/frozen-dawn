# MACS Scribe Architect

Implemented on `feat/scribe-architect`, branched from `feat/maeve-director` at `98165da` (after PR #102). Contract: [MACS Source of Truth](https://www.notion.so/39d7cfaa890181c1bad4f6babad80880), §9.4b, with §§4, 9.1, 9.3, 9.13a, 9.15, 9.16a and 9.18. **Automated gates only; no human visual pass yet.**

## Owner decisions (2026-10-06)

- **Records outlive ERASED.** §9.4b wins over the §9.18 line that no item may display former belief state. ERASED still wipes the belief store completely; dropped records and maps keep their own frozen copy and stay readable.
- **Appearance:** a natural Architect spawn is designated as the Scribe. No separate spawn path, so existing population caps, Hearth exclusions and spawn rules apply.
- **Lexicon:** new Thaeven roots are proposed below and in the Notion lexicon, marked as proposed for review.

## Owner decisions (2026-10-08): frequency

- **Cooldown 5 in-game days** after a Scribe ends, on every preset. Maeve stays awake from her activation until ERASED, so there is no closing window; the rate is per day once she is awake.
- **Bad luck protection.** Three confident beliefs stay the preferred route. A natural spawn refused only for too few confident beliefs is a **miss** when Maeve holds at least one belief about that player at the record floor (0.20). Miss *n* designates anyway with chance *n*/8, so the eighth is guaranteed. Every designation, by either route, resets the count. Misses build from natural Architect spawns, not calendar time, and never during the cooldown or while a Scribe is active.
- **Inconclusive lines.** A belief seen both ways is written as unsettled rather than left out: at least one support, contradictions not outnumbering supports, below 0.75. Unseen beliefs are never written, and contradictions that outnumber supports are settled against, not split.
- Expected rate once the gate or the pity can open: about one Scribe every 5.5 days for a player with steady habits, about one every 9 days on Default (6.5 on Brutal, where Architects come more often) for a player whose habits keep changing. Estimates from the spawner, not measured.

## Behavior

**Gate.** `ArchitectSpawner` asks `MaeveDirector.designateScribe` for each natural spawn, with the player the spawn was placed near. Designation needs ACTIVE Maeve, at least **3** beliefs about that player at current confidence ≥ 0.75 (count provisional), no active Scribe, and **5 in-game days** since the last one ended. Below three, bad luck protection may designate it anyway (see the 2026-10-08 decisions); the miss count is world-wide and saved with the claim. Masters, mind copies, Hearth roles, Aggregate children, NoAI actors and Creative/Spectator subjects never participate. One claim exists server-wide.

**Watch target (Causal).** Chosen from the world model only: the strongest OPEN access point within 32 blocks of the observed shelter centroid (`OPENING`), else the centroid (`SHELTER`), else no point (`ROUTE`: it watches from where it stands). No player position enters the order.

**Local executor** (`ArchitectScribeController`):
- Walks out to a post on a 20-block ring around the target: open sky, standable, loaded, no known heater within 8 blocks, preferring line of sight.
- With no remembered opening or shelter (`ROUTE`), it keeps a stand-off from the subject instead (owner, 2026-10-08): it stays put within 20 blocks, otherwise walks to a 16-block ring around them (cover allowed, so woods do not strand it), and closes in again whenever they draw more than 28 blocks away. Closing in keeps the same 2-minute watch clock.
- Walking (travel, flight, departure) uses ordinary mob navigation, the same the Architect's own retreat uses, at about 2.8 blocks a second walking and 4.3 fleeing (owner, 2026-10-08: the pawns' observed-walk planner refuses full-block steps, so on hilly ground the Scribe moved a step at a time). Flight and departure head for a reachable spot about 16 blocks away, re-chosen every two seconds. It never places or breaks blocks. Stalling 100 ticks on the way to a post means it watches from where it is.
- Transitions log as `[MACS Scribe] <event>` in the server log (WATCH with the post and rejected ring columns, FLEE, FLEE_TURN, CLOSE_IN, CORNERED, DEFENSE_ENDED, DEPART, GONE), so a lab pass needs no decision recording.
- Watches for 2400 ticks (2 min), facing the subject when it can see them within 48 blocks, otherwise the remembered point. Then it departs and is discarded once no player is within 32 blocks (or 16 after 600 ticks).
- Flees any visible Survival/Adventure player within 12 blocks, any within 4 blocks whether seen or not, and anyone who strikes it from range. A flight that stalls for 30 ticks turns 45, then 90, then 135 degrees off straight-away, either side. It never initiates combat.
- **Cornered** (struck within 3.5 blocks with no neighbouring step away it can take, or while flight has stalled for 30 ticks) it defends itself under the §9.13a local-defense exception: ordinary combat against that attacker until 200 ticks pass without damage, then it resumes fleeing. It still holds the slate.
- White eyes (same 2x3-pixel recolor as the scout's purple) and a held slate: the only Architect holding something that is not a weapon.
- While watching from its post it writes: slate raised in the right hand and tilted face-up, the jointed left hand scratching across it in three bursts per 6-second cycle, head bowed, glancing up at the subject for about a second at the start of each cycle. Each burst plays a quiet scratch (`frozendawn:entity.architect.scribe_write`, the vanilla cartography scribble at 0.35 volume and 0.85 pitch, subtitle "Architect scratches on a slate"). Walking, fleeing and defending drop the pose. Presentation only: a synced flag from the watch phase, client-side timing, no evidence.

**No new evidence.** Watching adds no observations and takes no focus slot, counter-family slot, commitment, reconnaissance mission or convergence assignment. Once struck it is an ordinary Architect being hit: existing combat/danger observation applies to it like any other.

## Drops (on death)

`MaeveDirector.scribeNotes` reads the store once inside the death-loot path; the items keep a copy and never consult it again.

**Record** (`frozendawn:scribe_record`, data component `frozendawn:scribe_record`):
- At most 5 beliefs, highest current confidence first (ties by pattern key), floor 0.20 (one witnessed support). Wrong beliefs are written exactly as held.
- Headed `Vel-thae.`; never the username. Field-note register, no address.
- Confidence is phrasing: ≥ 0.90 repeats the verb (`Ka vel-an. Vel-an.` → "Carries a blade. Always."), 0.75–0.90 is flat, below 0.75 leaves the transmission open (`Mor vel-thaeven…` → "Mends beneath cover. Perhaps."). A split belief (seen both ways, below 0.75) is marked with the proposed root *liss* (`Vel-sorr aren thaeven. Liss.` → "Leaves by the east opening. Unsettled."), and is written even below the 0.20 floor.
- Only authored patterns are written; an unknown future pattern is omitted rather than guessed.
- Use: with a Thaeven Translator in the inventory, each reconstruction is shown above its raw line; without one, raw Thaeven only, and the translator recipe is discovered (same as carriers). The page is a wood-framed slate in chalk. Each note is scratched in left to right with the Scribe's writing sound; with the translator its Thaeven then fractures into the reconstruction through the archive's ink (shared `ThaevenInk`). Certainty sets the pace: Always settles fast, plain lines at the normal pace, Perhaps slowly and keeps faint ghosts, Unsettled never stops wavering. A click finishes it; reduced ink animation shows it settled and silent. The subject's face is sketched in chalk in the corner from their own skin (the observed player's, kept by UUID, never the username; the default skin their UUID picks when this client has not seen them). Without a translator each line also gets a chalk pictogram of what it is about (sword, bow, heart for recovery, chevrons for pursuit, a doorway with the exit arrow); certainty and conclusions stay in Thaeven. Records dropped before the subject was kept still read, without a portrait.

| Pattern | Thaeven | Translation |
| --- | --- | --- |
| `PLAYER_PREFERS_SWORD` | Ka vel-an. | Carries a blade. |
| `PLAYER_PREFERS_RANGED` | Eth orren. | Strikes from afar. |
| `PLAYER_PURSUES_WITHDRAWING_ARCHITECT` | Vesh-thae senn. | Follows the one who withdraws. |
| `PLAYER_USES_RECOVERY_UNDER_COVER` | Mor vel-thaeven. | Mends beneath cover. |
| `RETREAT_BEARING_E` | Vel-sorr aren thaeven. | Leaves by the east opening. |
| `EXIT_AFTER_W_E_*` | Eth-sorr aren vaen. Vel-sorr aren thaeven. | Watched at the west opening, leaves by the east. |

**Marked map** ("Marked Map", a locked vanilla `filled_map`): zoomed to fit its marks (4, 2 or 1 pixels per block, past vanilla's closest scale each block painted as a square; vanilla scale 1 when even 1 would cut a mark off), centered on the observed shelter centroid (else the death position), painted once from already-loaded chunks with vanilla surface shading; no chunk is loaded for it and unloaded areas stay blank. Up to 8 marks per label from the world model: OPEN access points as blue pointers facing outward along the witnessed crossing, DANGER_ZONE as red crosses, HEAT_SOURCE as red points. Legend lore: openings, heat, losses. Locked, so neither terrain nor marks update.

## Proposed Thaeven roots

Grammar stays short, verb last, no tense. Leaving is written with *thaeven* (to return): the lexicon notes Thaeven has no word for going somewhere new.

| Root | Meaning | Sensation |
| --- | --- | --- |
| sorr | side; quarter; the way a thing faces | turning the head toward |
| aren | opening; a way through a wall | moving air; light through a crack |
| ka | edge; blade | cold metal; a line that parts things |
| senn | to follow; to walk in another's steps | footsteps landing in your own |
| mor | beneath; enclosed; under stone | weight overhead; stillness; no sky |
| liss | unsettled; seen two ways at once (proposed 2026-10-08) | two sets of footprints leaving the same door |

Compounds of existing roots: *Maeve-sorr* north (the cold quarter), *Vel-sorr* east (the warm quarter, where light returns), *Vesh-sorr* south (the high quarter, where the sun stands), *Eth-sorr* west (the void quarter), *Vesh-thae* the departing one, *vel-thaeven* to mend (warmth returns), *vel-an* to carry (hold what's left), *eth orren* to strike from afar (take across the gap).

## Lifecycle, persistence and diagnostics

- ERASED ends the claim immediately: a living Scribe walks away and is discarded without drops; killing it after ERASED drops nothing. Existing records and maps are untouched. Gone after E11 through the shared `ConvergenceLifecycle.isArchitectExistencePermanentlyEnded` hook (E11 itself is not built).
- `MaeveSavedData` version 9 adds the erasable `scribe` tag (active claim, last end time, miss count; older saves start at 0 misses). Older saves load with no history. The entity carries `macsScribe` in persistent data. Reload restarts the local watch; the claim's one-day lifetime (24000 ticks) bounds the whole appearance, and an expired claim starts the cooldown from its lifetime end.
- `/fd maeve dump` adds `SCRIBE` lines: last decision (designation with `by=GATE` or `by=PITY_n/8`, `MISS n/8`, gate refusal reason, end), cooldown remaining, `misses=n/8`, active claim with watch label/point and expiry. `/fd maeve confidence <pattern>` (player only, read-only) prints one belief's current confidence and returns its whole percent, 0 when unknown, for `execute store`. Actor journals record `MAEVE_SCRIBE_WATCH`, `_CORNERED`, `_DEFENSE_ENDED`, `_DEPART`, `_GONE`.
- `MaeveDirector` remains the only external entry point (277 lines, below the 300-line check).

## Verification

- Unit: `ScribeRecordTest` (ordering, five-line cap, floor, phrasing tiers, verb-last spatial lines, unknown patterns omitted, wrong beliefs as held, split beliefs inconclusive and unseen ones omitted, pity chance and reload, gate/cooldown/lifecycle, claim expiry and reload, map mark selection and bounds).
- Required native GameTests (`MaeveScribeGameTest`): bad luck protection (no miss while nothing is known, designation by the eighth miss, reset, inconclusive line in the drop, no misses in cooldown); gate and single claim with Master/Creative/ERASED refusals and no post-erasure drops; real death drops with exact record lines, locked centered map with three marks zoomed to fit and legend, unchanged after new evidence and ERASED; real AI watch, flee on approach, keeps fleeing when struck from range, defends when cornered; closes from 40 blocks to a stand-off without a remembered place; flees off a ledge through woods; leaves after ERASED without drops.
- `./gradlew build gameTestGate --console=plain` passed on 2026-10-08 after bad luck protection, the 5-day cooldown and inconclusive lines: 674 unit tests and 248 GameTests with all 241 required cases verified.
- It passed earlier on 2026-10-07 after the Scribe Check fixes and the `/fd maeve confidence` command: 672 unit tests (665 before this slice), every `check` task including the facade budget, and 246 GameTests with all 240 required cases verified (baseline 238/233). SavedData version tests now pin version 9. `./gradlew architectMonkey` passed on 2026-10-06 (not rerun for the fixes): 500 unchanged seeded stress cases plus the 242 native cases repeated (742 GameTests).

## Pending

- Design question: drifts taller than one block (early phase 6 snow, up to three blocks) still have no route, so a Scribe boxed in by them cannot travel or flee and watches or defends where it stands. Scouts and pawns share the planner's stricter limit. Climbing higher or digging would be new behavior; not added without an owner decision.
- Human visual pass ([MACS Scribe Check](macs-scribe-playtest.md)): white eyes and slate in hand, watch/flee readability, record screen with and without the translator, map legibility.
- Calibration (§9.20): gate count 3, cooldown 5 days, pity 8 misses, lifetime 1 day, watch 2 minutes, ring 20, stand-off 16 (settled within 20, closes in beyond 28), flee 12, notice 4.
- §17.7 `Remembered.` lines wait for Maeve 4 long-term memory, which is not built.
- Owner review of the proposed roots (including *liss*), and of the §9.18 wording now that §9.4b records persist.

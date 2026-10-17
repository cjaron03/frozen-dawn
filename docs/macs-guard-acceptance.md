# Sword-guard acceptance before advancing cover

The owner accepted the solo sword guard and snow-pursuit repairs after the 2026-09-24 21:35 replay. Natural sword counterplay, raised/opening axe disable, post-heal behavior and changed-tactic carryover are documented below. The final build also exercises active-guard mining, walking after a canceled breach and refreshing a stale raised goal: nine completed breaks, five walking cancellations, maximum 45 stalled approach ticks and no prolonged stuck rescue. The trace supports the reported improvement, not a guarantee of zero possible navigation stalls. Multiplayer remains deferred at the owner's request. The siege mantlet has not started.

## Prepared worlds

Eight separate copies preserve the same actual saved sword history: confidence 1.00, 11 supporting observations, one contradiction. Their complete Maeve SavedData is byte-identical before play. Each case starts independently, so axe or bow contradictions from an earlier case do not suppress another case's opening. Within a world, repeated encounters retain all resulting learning and failures.

| World suffix, available as Brutal and Normal | Exercise | Evidence to capture |
| --- | --- | --- |
| `MACS Guard 1 Sword - Brutal` | Sword-only damage; bait blocks, punish openings and flank. Allow retreat/healing when the fight permits, then try to defeat it naturally. | Real blocks, readable attack openings, brief stagger with re-guard, lowered shield during retreat/drinking, re-guard after healing, and an actual player win. |
| `MACS Guard 2 Raised axe - Brutal` | Confirm a frontal sword block, then strike the raised shield with the axe. Continue fighting. | Raised-contact disable and no shield return during that encounter, including after recovery if reached. |
| `MACS Guard 3 Exposed axe - Brutal` | Confirm guarding, then land an axe hit during a lowered-shield opening. Continue fighting. | Exposed-contact disable and no shield return during that encounter. |
| `MACS Guard 4 Change tactics - Brutal` | Approach to bait guarding, then switch to bow, distance and flanking. Repeat in this world to follow the new evidence. | It commits from prior sword history rather than reading a held weapon; witnessed contradictions affect later decisions without a free same-encounter replacement bet. |

Use the same numbered worlds ending in `Normal` for the matched comparison. Begin with case 1 Brutal and review the result before moving on. A death, abort, absent guard, or inaccessible flank is useful feedback, not automatically a pass. The native suites cover durability breakage, erasure/reload cleanup, attribution and other safety cases; they do not replace these live legibility and counterplay checks.

After the Normal victory, the owner requested avoiding duplicate axe cases across presets. Run the remaining axe mechanics in Normal: raised first, exposed second, using their independent starting saves. Both paths, shield timing and ordinary combat attributes share the same implementation across Default and Brutal. A successful Normal run therefore validates that shared mechanic; record the actual played preset rather than claiming a second Brutal replay. Separate preset-pressure or multiplayer testing is not supplied by this isolated duel.

Preset comparison limit: ordinary Architect base health, attack damage and shield cadence are identical under Default and Brutal. Both fixtures also use vanilla Normal difficulty. Maeve's attention capacity changes from five to three slots, while this isolated duel controls atmosphere and spawn pressure. This checks guard behavior under the Default preset; it should not be presented as a reduced-damage duel. The first Brutal sword-only retry had usable openings according to the owner but ended in player death; its clean Normal comparison retains the same baseline and also removes the axe from its kit.

## Commands

Open the chosen world, then run:

```mcfunction
/function macs_guard:accept/setup
```

Wait for **Ready**, then click Start or run:

```mcfunction
/function macs_guard:accept/start
```

Setup builds a fixed late-phase terrain sample with layered snow, deeper drifts, frozen logs and crystals. The waiting platform is outside the arena. Nothing spawns until Start. Both presets use iron armor, sword in slot 1, axe in slot 2, bow in slot 3, shield in slot 4, healing potions and food. The existing phase-zero atmosphere keeps environmental hazards out of the combat comparison. There is no combat resistance, automatic freeze, combat time limit, or changed production timing. Normal uses the mod's `default` preset; vanilla difficulty is Normal in both copies.

After the fight, describe what happened **before reading diagnostics**, then run directly in chat:

```mcfunction
/fd maeve dump
/function macs_guard:accept/status
```

Include whether you won or died, whether guarding forced a different approach, and any seemingly unfair block, missed axe disable, spinning or idle behavior. After a completed round, this returns you to the waiting platform:

```mcfunction
/function macs_guard:accept/finish
```

Finish refuses to remove an active opponent. For an emergency stop:

```mcfunction
/function macs_guard:accept/abort
```

An abort is explicitly recorded before cleanup and never counts as a counterplay victory. Player death is also recorded before actor cleanup. A disappearing actor without the player-kill advancement is marked unknown. Actor traces are exported every five seconds during a fight and at its outcome where the actor remains selectable; direct Maeve dumps are still required because function feedback is suppressed by Minecraft.

For a further encounter **with the same world's evolved history**:

```mcfunction
/function macs_guard:accept/repeat
/tick sprint 620t
```

Wait for **Sprint completed** and **Ready**, then Start. Only this empty interval is accelerated. For the next independent case, save and open its separate world instead. Do not use the old training/finish commands in these acceptance copies.

## Preservation and verification

`build/macs-guard-acceptance-evidence/baseline` contains the closed original save, previous client log and complete file hashes. `preparations.json` records all eight copies and confirms that only LevelName and the QA datapack changed during copying. The original sword encounter and completed camp saves remain intact. Production Java code is unchanged for this setup.

The exact generated functions are included in native GameTest resources. The required acceptance regression checks that Finish cannot interrupt a fight, a real fatal attack fires the player-kill advancement and publishes Maeve evidence, and cleanup cannot overwrite an abort even with recent player kill credit. These checks validate the replay's accounting. Current live results are recorded below.

The prepared revision passed `./gradlew architectVerify architectMonkey --console=plain`: 609 unit tests, eight gate-harness tests, 157 native GameTests (all 152 required manifest entries), and the unchanged 500 seeded stress cases. The stress server also repeated the 157 native cases. Source SHA-256: `0ac2e2cf59e2abd6aed528263708c47481a8a781b689c24b15d9162c07542aa5`. Built and installed smoke-jar SHA-256: `90ea6673fcb070529d103f52d52c5fa48290e7175641493b28c1a2faedc2de59`. Reports and the tested working patch are in `build/macs-guard-acceptance-evidence/verification`; these are QA changes on top of `a12da50`, not a new published PR commit. The fresh server's missing initial `server.properties` warning was followed by successful startup; expected negative-fixture diagnostics are retained in the log. No verification failures or skipped tests occurred.

## Live acceptance results (2026-09-24)

The first Brutal round included an accidental raised-shield axe hit. It disabled the guard, and the player died 78 ticks later. This verifies immediate raised-axe disable but does not yet verify its persistence beyond the initial 100-tick disable interval. The clean Brutal sword-only retry ended in player death after 885 active ticks. Its complete trace contained eight raises, seven blocks and three effective incoming hits; the owner reported that the openings felt usable.

The Normal sword-only round ended in a natural player victory after 789 active ticks (39.45 seconds). The owner used their own shield, which is permitted: the restriction concerns damage weapons. Saved statistics increased by ten iron-sword uses, eight shield uses and one mob kill, with no added axe/bow uses or deaths. The final observed hit was an iron-sword attack. The native player-kill reward, death log and direct Maeve dump agree: `OWNER_KILLED`, strategy `FAILURE`, sword confidence still 1.00. The Architect's tactic retained 18 prevented damage, 53.77029 received damage and 31.320004 dealt damage.

The periodic trace shows guard suspension for retreat at actor tick 347, resumption at tick 438, and two real shield blocks after healing. Its last export ends at tick 697; the final 92 ticks are supported by the death log, player statistics and direct Maeve dump rather than a complete final actor trace. The logs and exports are preserved in `build/macs-guard-acceptance-evidence/live-20260924-194511-normal-victory`.

The Normal natural sword-counterplay victory check is passed. A separate Brutal victory was not observed and is not required merely to duplicate the shared duel mechanics. Both axe results follow below; changed-tactic carryover still needs its live check. Multiplayer remains deferred. These results do not establish a damage difference between presets or prove that every balance concern is resolved.

For the raised-axe run, confirm a real frontal sword block, land one axe contact on a clearly raised shield, then return to the sword. Keep engaging nearby for at least 10–15 unpaused seconds, using your own shield and healing as needed. This exceeds the initial 100-tick (five-second) disable and gives the guard opportunities to return if the encounter lock were broken. If it retreats, allow recovery and approach again. Preserve a natural outcome and direct dump. The prior accidental axe run ended in player death only 78 ticks after disable, so it did not establish this persistence.

The dedicated Normal raised-axe replay (`09744c11-31ec-4faa-9173-9d4ee4ac0257`) recorded a frontal sword block at tick 79, a raised iron-axe block and disable at tick 137, then continued close combat until player death at tick 500. No shield re-equip, raise or resume occurred during the intervening 363 ticks (18.15 seconds). Maeve retained `SHIELD_DISABLED`, a failed tactic and sword confidence 0.65. This passes raised-axe disable and persistence beyond the initial five-second interval; the Architect did not heal during this run, so post-disable healing remains unexercised. The complete trace, direct dump and owner feedback are preserved in `build/macs-guard-acceptance-evidence/live-20260924-195450-raised-axe`. The owner asked whether the shield should recover after a cooldown; no behavior or source-of-truth change has been authorized or made.

The dedicated Normal exposed-axe replay (`70ff5854-0d14-49c2-aea0-da038d0f3732`) recorded a sword block at tick 95, shield lowering at tick 195, then a witnessed iron-axe hit and `SHIELD_DISABLED` at tick 207. The axe landed 12 ticks (0.6 seconds) into the opening. No re-equip, raise or resume followed during the remaining 294 ticks (14.7 seconds), ending in player death at tick 501 and subsequent QA actor cleanup. The direct dump retained the disabled commitment, strategy `FAILURE` and sword confidence 0.65. The owner reported, "done. looks like it worked".

This replay also exercised retreat after the disable: retreat began at tick 403, the log recorded potion drinking, and melee resumed at tick 479. Healing completion is inferred from the health increase from 3.7448115 at tick 474 to 21.36 after a nine-damage contact at tick 486, consistent with the production potion restoring health to 30 before damage mitigation. The shield remained absent through that sequence. The resumed melee observation lasted only 22 ticks (1.1 seconds) before player death; this is not a long post-heal combat sample. All 263 trace rows were retained without drops. Six exports, the direct dump, owner feedback, findings and hashes are preserved in `build/macs-guard-acceptance-evidence/live-20260924-200628-exposed-axe`.

Exposed-axe disable and persistence are passed. The remaining solo case is `MACS Guard 4 Change tactics - Normal`, whose untouched saved tactical memory still matches the baseline. Confirm guarding, then switch to bow attacks and distance or flanking; review the first encounter before repeating in that same world to assess changed evidence and the next decision. Multiplayer remains deferred.

The first Normal changed-tactic replay (`9c00606f-1ab6-4c0d-b31e-ce7ace2687d2`) ended in a natural bow kill. The owner found it very easy and reported getting stuck. The trace confirms the historical sword commitment at tick 40, a real sword block at tick 88, and arrow damage beginning at tick 145. The same shield stance resumed after recovery at tick 333. The direct dump records `OWNER_KILLED`, strategy `FAILURE`, sword confidence falling from 1.00 to 0.65, and ranged confidence rising from 0.00 to 0.20. No immediate replacement ranged commitment was issued. This supports the Lagged and Exploitable rules, while the next encounter's decision is still unverified.

The ease of this fight is not a clean balance result. The actor repeatedly selected snow breaches that were released after one tick; logs identify `minecraft:snow`, including `(2221,101,2223)`, initially seven layers in the installed fixture. Its no-progress counter reached 289 ticks. State samples put it in the same rounded block `(2222,101,2223)` from ticks 380 through 740, including long periods of lowered-shield approach. It later moved again and destroyed three blocks. The exact cancellation cause needs reproduction before a fix or a clean pursuit pass can be claimed. Preserve this world's learned evidence for the later carryover check.

Eleven periodic exports and the direct dump are retained in `build/macs-guard-acceptance-evidence/live-20260924-201102-change-tactics`, with the installed arena function, owner feedback, findings and hashes. Earlier exports recover the 51 old rows dropped by the last export, yielding 451 distinct rows through tick 1087. The final 105 ticks through the fatal arrow at tick 1192 are supported by the death log and dump rather than a complete actor trace. Remaining solo work is the snow-pursuit regression and the next-encounter changed-tactic check.

## Sword-guard snow pursuit correction

The owner requested a surgical correction after the changed-tactic replay. `ArchitectCommitmentController` cleared the queued mining target before calling the shield executor every tick, including when the shield was lowered and the executor yielded to ordinary pursuit. The next approach tick therefore treated valid snow breaches as canceled failures. This was an interaction with the active sword stance, not a new snow-mining policy.

The correction calls the shield executor first and clears mining only when it actually takes over for raised guarding. Other commitments retain their existing mining cancellation. No confidence, cadence, shield durability, axe termination, retreat cover or bow-pillar values change. The required native regression reconstructs the recorded snow heights and retreat wall, trains through real sword damage, and requires the same sword commitment to finish obstructing terrain breaks and reach its target. Before the correction, it failed after 300 ticks with zero destroyed blocks and repeated one-tick releases at the observed breach.

The closed `MACS Guard 4 Change tactics - Normal` save, client log and pre-change working patch are backed up under `build/macs-snow-breach-evidence/baseline`. Its saved tactical memory hash remains `9cf490eeed8635364eedb213072dbc184ac877863a862c62001dd81f87e01061`; the next encounter must use this evolved history rather than reset it. Before/after verification is recorded in that evidence directory. Live acceptance of the correction and next-encounter carryover remains pending.

With the correction, the regression reaches the player in 104 ticks and completes three terrain breaks, retaining the same sword commitment. Both the native and stress servers reproduced that result. `./gradlew architectVerify architectMonkey --console=plain` passed: 609 unit tests, eight gate-harness tests, 158 native GameTests (all 153 required cases), and the unchanged 500 seeded stress cases. The stress server also repeated all 158 native tests. No failures or skips occurred in the corrected runs. Gradle emitted its existing deprecation notice; the native logs retain expected negative-fixture diagnostics.

Verified source SHA-256: `92d905a4296fc1779e481f08d5bfa672f80e0f7512c757d76ecbffc91577dbf9`. Built and installed smoke jar SHA-256: `c6eb9b37c82c9180c15d0bd3e76a0b4c3d7427b7f4b009da65488cf460c6f25c`. Reports, the expected pre-fix failure, the exact working patch and hashes are in `build/macs-snow-breach-evidence`. These are uncommitted changes on `a12da50`, not checks for a new published commit.

For the next live encounter, open the same `MACS Guard 4 Change tactics - Normal` world and run `/function macs_guard:accept/repeat`, then `/tick sprint 620t`. Wait for both Ready and Sprint completed, then run `/function macs_guard:accept/start`. Use the bow and move across the snow; avoid sword/axe damage during this check. The preserved sword confidence is 0.65 and ranged confidence is 0.20, so the old sword bet is below threshold and a ranged counter is not yet justified. Describe pursuit and any stalls before running `/fd maeve dump` and `/function macs_guard:accept/status`. Only the empty encounter gap is accelerated.

The corrected-client follow-up (`562619c0-69c0-4ff8-9b85-b04bcd93dde4`) used the commands in the correct order, including waiting for Sprint completed before Start, and ended in a natural bow kill at tick 725. The owner asked whether they had made a mistake. The retained in-game prompt still asked for guard baiting before switching weapons, conflicting with the bow-only follow-up instructions. The player landed three initial melee hits, confirmed as sword use by Maeve provenance, before arrow damage began at tick 435. This is a mixed-weapon replay, not a bow-only trial. No reset or replay erasure is needed.

Historical carryover is verified: the dump froze sword confidence at 0.65 and ranged confidence at 0.20, issued no shield, and retained `betUsed=false`. Reconnaissance was assigned at tick 40 to an uncertain access point with confidence 0.20, then ended as `LOCAL_DEFENSE / UNREPORTED` on the first sword hit at tick 99. That witnessed sword hit received the existing +0.30 reconnaissance weight; later arrow evidence left sword confidence at 0.60 and ranged confidence at 0.20. The earlier wording that implied the whole opening must be ordinary combat was too broad: below-threshold weapon beliefs prevent those counters, but other unresolved evidence may still admit reconnaissance before combat.

The recorded no-progress counter peaked at 26 ticks; a snow breach completed in four ticks. No prolonged stall is recorded. Seven exports cover through tick 688 with no dropped rows; the last 37 ticks are supported by the death log and direct dump. This does not exercise the corrected shield/mining interaction live because no shield was issued. That interaction has the before/after native regression, while corrected guard-active live acceptance remains open. Evidence is in `build/macs-snow-breach-evidence/live-20260924-204510-carryover`.

The progressed world's `accept/case` text now gives the bow-only follow-up instructions and allows for pre-combat reconnaissance. Its original and corrected text are preserved with the replay; `/reload` is needed to load the correction into the currently running world. Other initial-case worlds and production behavior are unchanged. Next-encounter carryover is complete; the remaining solo live check is pursuit on snow with an active sword guard. Multiplayer remains deferred.

## Guard-active snow replay, 2026-09-24 20:54

The owner repeated `MACS Guard 1 Sword - Normal` and reported, "while it did spin and get stuck, it corrected". Run `7b968c7d-f4a1-418a-a1cc-ba31366eeb87` used the verified corrected source (`92d905a4296fc1779e481f08d5bfa672f80e0f7512c757d76ecbffc91577dbf9`). The shield was issued once at tick 40 from historical sword confidence 1.00, raised nine times, and recorded three actual frontal blocks in the retained trace. Six snow breaches completed in four ticks each while the same sword commitment remained active. The actor resumed melee and guarding after the terrain stalls, suspended guarding during retreat, healed, and resumed the same shield at tick 1623. A natural player kill at tick 1985 ended the encounter. The brief Creative/Survival toggle occurred before Start; no combat sprint or game-mode change is recorded during the fight.

This supplies live coverage of the targeted shield/mining fix, but not clean terrain acceptance. The first stall oscillated around `(2225,101,2209)` while pursuing a raised waypoint and recovered after a forced route refresh. Two later snow breaches were canceled explicitly by `OPEN_ROUTE nodes=7` at ticks 417 and 590. Their old BREACH steps then remained selected with no mining target until the 160-tick recovery refresh. The first was subsequently mined at tick 574; the second recovered onto a walking route. The maximum recorded no-progress counter was 181 ticks (9.05 seconds). These are distinct from the former unconditional per-tick shield cancellation. Route handoff and stale-step recovery need a focused reproduction before any further production change; simply reducing the timeout would not establish the cause.

Nineteen exports recover all 914 journal rows through tick 1895 (400 retained plus 514 rolled out of the last export); the final 90 ticks are supported by the natural-death log and direct Maeve dump. The replay, installed arena functions, hashes and findings are preserved in `build/macs-snow-breach-evidence/live-20260924-205441-guard-snow`. No runtime or fixture behavior was changed while reviewing this run. Guard-active mining and learned carryover are verified; the remaining terrain issue and deferred multiplayer check must stay visible in PR acceptance.

## Narrow open-route handoff correction

The native replay reproduces the canceled-breach stall in the same acceptance snow field: the actor starts at `(2218,101,2210)` and the visible player is at `(2212,101,2212)`. The nearby player is essential to enter the existing eight-block cancellation check. Before the fix, the six-layer snow breach was canceled for `OPEN_ROUTE`, then the actor selected that blacklisted BREACH again without a mining target. It failed to reach the player through 120 ticks and had completed no breaks. The retained failing XML contains only this regression failure; an earlier exploratory run used a more distant target, did not reproduce this handoff, and also reported a separate wilderness blocked-jump assertion failure.

The correction retains the already accepted navigation path as transient approach state. Approach lets that path continue before redispatching D* steps. Its ownership ends when navigation stops or replaces it, the target changes identity or block position, or another break takes over. A moved target refreshes the planning goal. Mining is canceled only after navigation accepts the safe bounded route. Existing path admission, guard cadence, mining failure budgets, recovery deadlines, cover and confidence values are unchanged.

The failing stationary-target case now reaches the player in 42 ticks without the prolonged-stall replan. A second required case relocates the player immediately after route acceptance and reaches the new position in 27 ticks, retaining the same sword commitment. The earlier shield/mining regression still reaches its target in 104 ticks and completes three breaks. This correction targets the two canceled-breach stalls; the initial raised-waypoint oscillation in the live replay remains a separate observation, not a claimed fix.

For live verification, open `MACS Guard Snow Handoff - Normal`, run `/function macs_guard:accept/setup`, wait for Ready, then `/function macs_guard:accept/start`. The independent world is copied from the preserved actual sword-history baseline; its tactical SavedData is byte-identical (`b30013ea76f761828dbe0ffa5b9bfce490ec9c2ff3ebd3a88dcd46057f2bc3fc`). The kit omits axes and bows for this sword-only case. Confirm a real shield block, back across uneven snow, then let the actor pursue. Describe stalls and recovery before running `/fd maeve dump` and `/function macs_guard:accept/status`. No combat timing is accelerated. The completed Normal world and its learning are backed up in `build/macs-open-route-evidence/baseline`; this new live replay is pending.

Verification passed with `./gradlew architectVerify architectMonkey --console=plain`: 609 unit tests, eight gate-harness tests, 160 native GameTests (all 155 required), and all 500 unchanged seeded stress cases. The stress server repeated all 160 native cases, reproducing the same 42/27/104-tick results. No failures or skips occurred in the final run. Source SHA-256: `8f6cf24938b48a24e333e0c08b58e61fb242e0553961cc85c59c9cd3b6ce93f5`. Build jar SHA-256: `aad75fd57be25f6730845d6549753e59e06cb473bfda5ddb667997f40fa8efbd`. Evidence and before/after reports are in `build/macs-open-route-evidence`. Existing Gradle deprecation and development refmap warnings remain; expected negative-fixture diagnostics are retained. These are local working-tree checks on `a12da50`, not a newly published PR commit or a completed live replay.

## Follow-up snow replay, 2026-09-24 21:13

The owner reported two recovered spins in `MACS Guard Snow Handoff - Normal`, run `0885e626-8c9f-4f4f-b065-1ea4e91ee1a6`, on verified source `8f6cf24938b48a24e333e0c08b58e61fb242e0553961cc85c59c9cd3b6ce93f5`. Eighteen exports recover all 900 journal rows through the player's fatal hit at tick 1722. The same historical sword commitment issued one shield, raised it twelve times and recorded six frontal blocks. All eight selected snow breaks completed; there were no released breaks or open-route cancellation events. This supports the previous guard/mining correction, but does not directly exercise the latest canceled-breach handoff in live play.

Both remaining loops retained an old raised planning goal after the player moved. Near `(2224,102,2231)`, the actor pursued waypoint `(2224,103,2231)` toward old goal `(2217,103,2226)` until a forced refresh at tick 434 selected the current player position `(2214,101,2222)`. The no-progress counter peaked at 172 ticks; a snow breach completed at tick 445. Near `(2207,101,2207)`, it pursued waypoint `(2207,102,2207)` toward old goal `(2212,103,2206)` until tick 1491 refreshed the goal to `(2223,101,2207)`. That no-progress episode peaked at 168 ticks; melee and guarding resumed afterward. Recovery worked, but roughly eight-second loops are still a terrain defect. A focused stale-raised-goal reproduction is the next step, rather than declaring smooth pursuit passed or changing shield balance.

The player ran the old `/function macs_guard:finish` shortly before death. Its training-stage condition was false (`mg` was set to 4 by acceptance setup), so it did not stop this fight. The acceptance emergency stop is `/function macs_guard:accept/abort`; `/function macs_guard:accept/finish` is only for returning after a completed fight. Player death was recorded before actor cleanup and remains a loss, not an Architect kill. Evidence is in `build/macs-open-route-evidence/live-20260924-211324-snow`. No production or world behavior changed during this review.

## Stalled elevated-goal refresh

The owner requested a narrow waypoint correction. The first retained raised-goal loop reproduces in the acceptance snow fixture with the historical sword guard active. On the original logic it still fails to reach the player after 240 ticks, peaks at 173 no-progress ticks, and requires the existing `WALK_STUCK` rescue. The second location reaches the player in 73 ticks in isolation, so it is retained as a control; that simplified fixture does not reproduce the second live loop's full route history.

The production change is confined to `ArchitectApproachPlanningSupport`. After 40 active approach ticks without leaving the existing progress anchor, an old goal above the current target and more than six blocks from it becomes stale. The existing refresh path retires the committed corridor and rebuilds toward the current target with the same planning and recovery budgets. Its journal labels the reason `STALLED_OLD_ELEVATION` and records both goals. Moving pursuit, same-height goal reuse and small target shifts retain the previous policy. Shield cadence, confidence, cover placement, ice budgets and retreat behavior are not retuned.

Both live episodes meet that condition in retained state samples: tick 320 versus the old forced replan at 434, and tick 1380 versus 1491. This establishes that the new trigger covers the recorded geometry; it does not establish a future dispatch tick or substitute for another live replay. The required native cases allow normal shield pauses while requiring arrival, less than 80 stalled approach ticks, no prolonged `WALK_STUCK` rescue, and the same sword commitment. An initial 150-tick arrival deadline stopped during a legitimate shield raise after the corrected actor had already escaped the loop; its failed report is preserved. The final 240-tick fixture was rerun on the original code and still failed.

The closed `MACS Guard Snow Handoff - Normal` world, original logs, prior jar and complete working patch are backed up in `build/macs-waypoint-evidence/baseline`. Repeat in that same world to preserve its learned history: `/function macs_guard:accept/repeat`, then `/tick sprint 620t`, wait for Ready and Sprint completed, then `/function macs_guard:accept/start`. Use the sword for damage, confirm guarding, and back across the uneven snow. Describe any spins before running `/fd maeve dump` and `/function macs_guard:accept/status`. Use `/function macs_guard:accept/abort` if an emergency stop is needed. Smooth live pursuit and deferred multiplayer acceptance remain open.

Final verification passed with `./gradlew architectVerify architectMonkey --console=plain`: 609 unit tests, eight gate-harness tests, 162 native GameTests (all 157 required), and all 500 unchanged seeded stress cases. The stress server repeated all 162 native cases. The formerly failing case reaches the player in 185 ticks, including the normal shield pause, with peak stalled time 48 ticks and one completed terrain break. It refreshes at 40 stalled ticks and never needs the prolonged rescue. The second-location control remains 73 ticks with a peak of eight stalled ticks. The previous handoff cases retain 42/27-tick arrival and the shield/mining case retains 104 ticks and three breaks. Native and stress results agree; no failures or skips occurred in the final run.

Verified source SHA-256: `ac1305fd1edcb6b1d312c7a86ce6cd4dc0b65a93c81adc2ce5ac865d1ee8d8d8`. Built and installed smoke jar SHA-256: `1110097c8c4dde40773140b93bc5b1a7de57c0fd632cd184992c1665ee578356`. Existing Gradle deprecation and development refmap warnings remain. The before/after reports, unchanged stress matrix, exact working patch and replay-trigger analysis are in `build/macs-waypoint-evidence`. These are local working-tree checks on `a12da50`, not a new published commit. The client was relaunched for a live repeat; smooth visual pursuit remains to be checked.

## Accepted solo follow-up, 2026-09-24 21:35

The owner reported, "there we go. better", and accepted both sword guard and the shared Architect pathfinding repairs. Run `3a287804-9d5d-4bbf-885d-01fb81144183` used verified source `ac1305fd1edcb6b1d312c7a86ce6cd4dc0b65a93c81adc2ce5ac865d1ee8d8d8`. Ten exports recover all 608 journal rows through the player's fatal hit at tick 962, including the 208 rows rolled out of the final 400-row buffer. The actor retained one historical sword commitment at confidence 1.00, raised the shield six times, and blocked two contacts for 12 prevented damage. No healing occurred in this round; earlier accepted replays cover that behavior.

Nine terrain breaks completed. Five breaches yielded to accepted walking routes, so this round also exercised the previous route-handoff change. At tick 237 the new `STALLED_OLD_ELEVATION` refresh fired after 40 stalled ticks: the old goal was `(2227,103,2227)` and the current target was `(2226,101,2218)`. The nearby snow break completed four ticks later and pursuit continued. Maximum stalled time was 45 ticks (2.25 seconds), compared with 172 in the preceding live replay; no `WALK_STUCK` rescue occurred. These are informed runs with different player movement, not a controlled timing benchmark. The native before/after fixture provides the controlled regression.

The player died naturally at tick 962 before QA removed the actor; Maeve recorded strategy SUCCESS. This is acceptance of pursuit and guarding, not an additional player victory or difficulty calibration. The final dump retained sword confidence 1.00 with 13 supporting encounters and one contradiction. Preserved logs, functions, all exports and hashes are in `build/macs-waypoint-evidence/live-20260924-213553-accepted`; the tracked [verification summary](verification/macs-guard-acceptance.json) preserves reviewable facts. Solo guard/terrain acceptance is complete. Current-build multiplayer/performance, fresh blind combined play and survival pressure remain broader integration work.

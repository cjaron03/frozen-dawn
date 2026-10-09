# MACS Scribe Check

Owner playtest world for the Scribe Architect ([macs-scribe.md](macs-scribe.md), §9.4b). Every belief is earned in play and the Scribe is an ordinary natural spawn; nothing is injected. The owner clicks green chat links to move between steps. No lab-bridge functions are approved for this world: agents read `scores msc`, `maeve-dump` and `architect-dump` only.

## Prepare

Create a superflat Survival world in `run-lab/saves` (any name), close it, then:

```
python3 tools/prepare_macs_scribe_playtest.py --source "run-lab/saves/<flat world>" --destination "run-lab/saves/MACS Scribe Check"
./gradlew runClientLab --console=plain -PfdLabWorld='MACS Scribe Check'
```

In game, click the setup link in chat (or run `/function macs_scribe:setup`). Setup erases and restores Maeve (empty memory), builds the arena, and starts round 1. Clicking `setup` again later starts over from nothing. After editing the preparer, `--update-pack "run-lab/saves/MACS Scribe Check/datapacks/macs-scribe"` and `/reload` refresh the functions without touching the world. Each world load or `/reload` reprints the current step and its links, so a relaunch never strands the owner.

## Arena

Flat ground with a stone roof (394..406, center lapis) and an open east side. A gold pad sits just outside it (407..410). A heater is west at 390, and a bedrock cage with a head-height slit is under the east roof. The waiting pad is the emerald block at 360. Mob spawning, daylight and weather are off; the player is in Survival with Resistance V, Saturation and Night Vision. The await step adds a full EVA suit: in mid phase 6 it is climate-controlled (no freezing or wind chill), and there is no vacuum yet, so no oxygen is needed.

## Steps

| Stage | Owner does | Expect |
| --- | --- | --- |
| 10 | Under the roof: drink the potion, hit the caged witness once through the slit with the sword, walk out east onto gold and stand there. | "Round recorded" after about a second on gold. |
| 20-21 | Click the sprint link (640 ticks: encounters need a quiet gap), then NEXT. | Four rounds total. |
| 22 | Read the three percentages, then click the one link offered. | "Maeve's habits: sword 80%, recovery under cover 80%, east retreat 80%" (`/fd maeve confidence`, rounded down). All three at 75% or more offers AWAIT only; otherwise ONE MORE ROUND only. |
| 30 | In the EVA suit, stay under the roof and click the 6000-tick sprint until an Architect appears (2% per 10 s). Phase 6 mid: spawns on, no snowfall. | "A Scribe was designated and is held." A red "ordinary Architect" message means the gate failed: report the dump. |
| 31-40 | Wait for any sprint to end, click SHOW. CHECK 1: white eyes, slate in hand. CHECK 2: it walks about 20 blocks out from the east opening, then writes on its slate (head bowed, quiet scratching) and glances up at you every few seconds. | The watch lasts 2 minutes of game time, then it walks away. |
| 50 | CHECK 3: walk toward it. | Within about 12 blocks it turns and runs, never attacks. It returns to a post once you back off. |
| 60 | CHECK 4: chase and kill it (Speed II given). | Struck from range it keeps running; hit up close with no way out, it fights back holding the slate. |
| 70 | CHECK 5: pick up the drops and use the record. | Raw Thaeven headed `Vel-thae.`; the translator recipe is discovered. If it left instead, the red message offers a start-over link. |
| 75 | CHECK 6: use the record again (translator given). CHECK 7: hold the map. | English above each Thaeven line, no numbers. Locked "Marked Map" centered on the roof: blue pointer at the east opening facing out, red point at the heater, red cross at the cage; legend in the tooltip. |
| 80 | CHECK 8: Maeve ERASED. Reread the record and look at the map. | Both unchanged; the dump shows no beliefs. |
| 90 | Restore. | Empty, awake Maeve. |

## Notes

- Wrong-stage clicks print status and the current step's links; nothing advances or rebuilds.
- `/fd maeve confidence <pattern>` is read-only: it prints one belief and returns its confidence as a whole percent for functions.
- After a Scribe ends, the next needs 5 in-game days; `setup` is the quick way to try again.
- The await uses mid phase 6 on purpose: early phase 6 snowfall during long sprints buries the arena in full-block drifts, and the Scribe's no-dig walk cannot cross them (an open design question, below).

## Headless runs (2026-10-07/08, agent, not a visual pass)

Xvfb client with real keyboard and mouse input; the server ran at about 9 TPS.

- Setup, arena and caged witness built as described.
- Practice: sword and recovery reached 0.80 after four rounds in both runs. In the second run the witness missed one east exit (`RETREAT_BEARING_E` 0.60); one extra round raised it to 0.80.
- Await: a natural Architect was designated on the first or second sprint (`watch=OPENING@408,-60,400`) and held.
- CHECK 1 by screenshot: white eyes, slate in hand.
- CHECK 2: the first run exposed the Scribe watching from its spawn on snow layers; the walker now uses the observed-walk planner and the Scribe walked 22 blocks to its post. The second run stalled on full-block drifts left by the first and watched from there, which led to the snow fixes above.
- Both watches ended before the flee check because headless steps were slow. Departure, discard and the "left without dying" message worked.
- Not seen live: flee, cornered defense, drops, record screens, map, ERASED snapshot. Required GameTests cover them; they remain for the owner's pass.

## Owner pass 1 (2026-10-07, local lab client)

Owner's unprompted report, recorded before any diagnosis: "yea it keeps saying that "one is missing etc" and also when we wait for the scribe, i need a EVA suit". Earlier, after a relaunch: "im just in the world. theres no clicking or anything. i think we gotta restart".

Cause and fix: the checkpoint always printed the "one is lower" line, so an unneeded fifth round was played (all three were already 0.80 after four); it now shows the real percentages and offers only the matching link. The await left the player unprotected in phase 6 cold; it now equips a full EVA suit. Links printed only when a step began; a world load or `/reload` now reprints them.

## Owner pass 2 (2026-10-07, local lab client, build b18a41a)

Owner's report: "yea im still freezing to death, and no scribes that i see." Then, before any diagnosis was shared: "nevermind it worked! but the test enviromet seems janky you know? i want it more natural, and still have the clicking though, but we should test another situation". Asked what felt janky, the owner selected all four offered options: the superflat arena, teleports and effects, sprinting time, and too much chat. The owner chose a natural survival base as the next situation.

State at the report (snapshot, game time 52536): stage 30 after five rounds, one Architect tagged `msc_scribe` loaded at (409.5, -60, 351.5). SWORD and RECOVERY_UNDER_COVER at 1.00. The cold is explained by the world having entered stage 30 before the EVA fix, so the new await never ran; the rejoin prompt told the player to keep a suit they did not have. Not fixed in this world: the next check replaces it.

## MACS Scribe Base (natural variant)

Requested after owner pass 2. `tools/prepare_macs_scribe_base.py --source <closed normal world> --destination "run-lab/saves/MACS Scribe Base"` copies a normally generated world, enables commands and installs the `macs_scribe_base` pack (scoreboard `msb`). The first copy is from the owner's new world "scribe test" (spawn 0, 71, 0), which stays untouched.

- **Begin** (link on join): empties and wakes Maeve, sets phase 6 mid (cold, natural Architect spawns, no snowfall), keeps inventory, and builds a 9x9 cabin where spawn meets the surface. The cabin has one door (east), window strips on the other walls, a lit heater, a bed and a chest with the EVA suit, a Sharpness V netherite sword, a shield, nine healing potions, food and torches. No teleports, effects, held mobs or time skips. Day, weather and mob spawning stay natural.
- **CALL AN ARCHITECT**: summons one ordinary Architect 50 blocks east on the surface (west in the first build; changed during owner pass 3) and sends it at the player. The player shows the habits while it can see them: sword hits, drinking inside the cabin, leaving through the east door. The round ends when it dies; after a 32 s quiet gap the real confidences print with another CALL link, until all three reach 75%. Golden apples and regeneration potions also count as recovery, so eating one outside works against "under cover".
- **Await**: nothing is called. The Scribe is only ever a natural spawn (2% per 10 s at the default preset, about 8 minutes on average). It is not held, so its two-minute watch runs on its own clock. An ordinary natural Architect after the gate prints one red line with the dump link.
- **Checks**: kill it, read the record without and then with the translator, check the map, erase Maeve, re-read, restore. One chat line per step, and the current step reprints on rejoin or `/reload`.

The native gate parses all 30 functions at permission level 2 (`scribeBaseFunctionsParseAtPermissionTwo`).

## Owner pass 3 (2026-10-07, MACS Scribe Base, local lab client)

Owner, after six rounds: "question, does it need to be all at 75%? or what. cause ngl, its going up and down etc". Dump: SWORD 1.00, RECOVERY_UNDER_COVER 0.60, RETREAT_BEARING_E 0.25 with WITNESSED_OTHER_RETREAT_BEARING contradictions (-0.35 each) from crossings at (-6, -4), (-3, -6) and (-9, -1). Causes: the pack demanded those three habits while the real gate counts any three beliefs at 0.75 or more, and the roof's one-block eave plus nearby trees gave covered-to-open crossings on the north and west sides. The owner kept the gate count at 3 ("keep 3, ill play the rounds"). Fix applied live with the owner's approval: a one-time `trim` (flush roof, leaves cleared within 16 blocks).

Owner, after the trim: "yea leaves east is going down, drinking potion is at 80% now though". Dump: RECOVERY 0.80, RETREAT_BEARING_E 0.10; one more west crossing at (-7, 72, -4) to (-9, 71, -2) at tick 13240, so shade remains west of the pad. Called Architects now come from the east, so the fight stays on the door side.

Owner, two rounds after the east-call change: "yea idk what im doing, its still at 10%". Dump at about tick 16240: no retreat crossing of any bearing since tick 13240, so the trim held, but no east exit was witnessed either. The archer response (KEEP_AWAY_ARCHER, preferred range 16) keeps the called Architect back, and conditional windows read "local sight or executor unavailable". Guidance given: wait inside until the Architect is close and in view of the east door, drink, then step out east while it watches, then fight.

Gate reached (2026-10-08 lab log, client clock): habits readout at 00:03:10; natural spawn designated as Scribe at 00:03:33, 54 blocks out, watch=OPENING@(-6, 71, -4); slain by the owner at 00:04:38 near (-39, 72, -31); `ENDED reason=KILLED` at 00:04:40; owner ran `translate` at 00:04:57. Owner: "we got it". Log items to review with the owner's own description first: at 00:04:30 the Scribe logged `RETALIATION` at distance 3.2 and then the ordinary RETREAT routine (ice walls, healing potion); a `Failed to parse map banner: 'Not a list: null'` warning at the drop; the map's watch mark is the west opening (-6, 71, -4), while the pack text promises a mark at the east door.

## Scribe Base luck pass (2026-10-08)

For bad luck protection and inconclusive lines (owner decision 2026-10-08). A fresh `MACS Scribe Luck` save from the same pack, played from a separate default-config play checkout so the SSD clone's Film config is not rewritten by `setup`. At the readout below three, a new link (`wait`, stage 22 only) skips to await. Ordinary natural Architects there are misses; `/fd maeve dump` shows `misses=n/8` and the Scribe's `by=PITY_n/8`. Leaving by the east door one round and another way the next splits the exit belief, which the record writes as "Unsettled."

Owner observation, 2026-10-08: a natural Architect became a Scribe on the first miss (`by=PITY_1/8`) while a `/tick sprint` was running, so its two-minute watch passed in about 12 real seconds and it left. Waiting out the cooldown by sprint was slow and drew constant late-phase mobs. Two findings: designation skips creative players entirely (no miss is counted), and a living ordinary Architect within 96 blocks blocks every natural spawn. The pack now offers the wait link again after a Scribe leaves, and marks every ordinary Architect with glowing and a chat line while it waits.

## Scribe Base quick mode (lab client only)

For checking the Scribe by eye without days of play. The lab client and the GameTest server set `-Dfrozendawn.labCommands=true`, which adds `/fd maeve scribe seed | roll | clear-cooldown`; production never registers them.

- **QUICK** (offered beside the begin link) wakes an empty Maeve, builds the cabin, gives Resistance, and seeds a fixed belief set through the real store: sword 5/0 (Always), east exit 4/0 (plain), recovery 2/0 (Perhaps), west exit 2/2 (Unsettled), ranged 0/3 (never written). Two are confident, so every roll goes to bad luck protection.
- **ROLL** decides one natural Architect spawn 50 blocks east of the cabin through the same designation call as `ArchitectSpawner`. A miss prints `misses=n/8` and never enters the world; a Scribe spawns and the pack announces it. The real cooldown applies.
- **AGAIN** (after it leaves, or after RESTORE) clears the cooldown only; after RESTORE it seeds again.
- Limits: this proves the watch, flee, writing pose and sound, record lines and the erase invariants. It does not prove natural timing (GameTests and the luck pass do), and the record lines can differ from the seed because Maeve keeps observing during the encounter.

## Owner quick pass 1 (2026-10-08, MACS Scribe Quick, build 16f2364)

Owner, unprompted: "i saw it looking at me, writing on the slate, and when i attacked it fought back". Log (client clock): QUICK seeded at 19:15:12; the first ROLL designated a Scribe `by=PITY_1/8` at 19:15:40; at 19:16:26 it logged `RETALIATION` at distance 2.6 against the owner, then the ordinary RETREAT routine (three ice walls, healing potion); slain by the owner at 19:16:34; `ENDED reason=KILLED`. Record lines: sword (Always), east exit (Unsettled), recovery and north exit (Perhaps). The fight moved the seed: east took a contradiction (4/1, split), west a third (2/3, dropped), north a first support. The map carried 8 marks (openings, heat, losses) learned during the watch. The `Failed to parse map banner: 'Not a list: null'` warning recurred at the drop. Open: whether a Scribe should fight back once struck rather than only flee.

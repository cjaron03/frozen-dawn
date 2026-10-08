# MACS Scribe Check

Owner playtest world for the Scribe Architect ([macs-scribe.md](macs-scribe.md), §9.4b). Every belief is earned in play and the Scribe is an ordinary natural spawn; nothing is injected. The owner clicks green chat links to move between steps. No lab-bridge functions are approved for this world: agents read `scores msc`, `maeve-dump` and `architect-dump` only.

## Prepare

Create a superflat Survival world in `run-lab/saves` (any name), close it, then:

```
python3 tools/prepare_macs_scribe_playtest.py --source "run-lab/saves/<flat world>" --destination "run-lab/saves/MACS Scribe Check"
./gradlew runClientLab --console=plain -PfdLabWorld='MACS Scribe Check'
```

Run `/function macs_scribe:setup` once in game. Setup erases and restores Maeve (empty memory), builds the arena, and starts round 1. Clicking `setup` again later starts over from nothing. After editing the preparer, `--update-pack "run-lab/saves/MACS Scribe Check/datapacks/macs-scribe"` and `/reload` refresh the functions without touching the world.

## Arena

Flat ground with a stone roof (394..406, center lapis) and an open east side. A gold pad sits just outside it (407..410). A heater is west at 390, and a bedrock cage with a head-height slit is under the east roof. The waiting pad is the emerald block at 360. Mob spawning, daylight and weather are off; the player is in Survival with Resistance IV, Saturation and Night Vision.

## Steps

| Stage | Owner does | Expect |
| --- | --- | --- |
| 10 | Under the roof: drink the potion, hit the caged witness once through the slit with the sword, walk out east onto gold and stand there. | "Round recorded" after about a second on gold. |
| 20-21 | Click the sprint link (640 ticks: encounters need a quiet gap), then NEXT. | Four rounds total. |
| 22 | Dump, then AWAIT. If one of the three is below 0.75 (the witness missed a step), click ONE MORE ROUND first. | Three beliefs at 0.80: `PLAYER_PREFERS_SWORD`, `PLAYER_USES_RECOVERY_UNDER_COVER`, `RETREAT_BEARING_E`. |
| 30 | Stay under the roof and click the 6000-tick sprint until an Architect appears (2% per 10 s). Phase 6 mid: spawns on, no snowfall. | "A Scribe was designated and is held." A red "ordinary Architect" message means the gate failed: report the dump. |
| 31-40 | Wait for any sprint to end, click SHOW. CHECK 1: white eyes, slate in hand. CHECK 2: it walks about 20 blocks out from the east opening and stares at you. | The watch lasts 2 minutes of game time, then it walks away. |
| 50 | CHECK 3: walk toward it. | Within about 12 blocks it turns and runs, never attacks. It returns to a post once you back off. |
| 60 | CHECK 4: chase and kill it (Speed II given). | Struck from range it keeps running; hit up close with no way out, it fights back holding the slate. |
| 70 | CHECK 5: pick up the drops and use the record. | Raw Thaeven headed `Vel-thae.`; the translator recipe is discovered. If it left instead, the red message offers a start-over link. |
| 75 | CHECK 6: use the record again (translator given). CHECK 7: hold the map. | English above each Thaeven line, no numbers. Locked "Marked Map" centered on the roof: blue pointer at the east opening facing out, red point at the heater, red cross at the cage; legend in the tooltip. |
| 80 | CHECK 8: Maeve ERASED. Reread the record and look at the map. | Both unchanged; the dump shows no beliefs. |
| 90 | Restore. | Empty, awake Maeve. |

## Notes

- Wrong-stage clicks print status only; nothing advances or rebuilds.
- After a Scribe ends, the next needs 3 in-game days; `setup` is the quick way to try again.
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

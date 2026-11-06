# Miller Pattern M6: the idea

**Status:** the owner's idea and its order, recorded 2026-11-06 during the M5 pass. Not a plan. No mockups, lines or tasks yet: the owner said to finish M5 first.

## Order

1. **M5 finishes first:** the owner's pass, the log sort, the PR and the merge. M6 is planned against M5 as shipped.
2. **A new face for ORSA-scripted answers,** replacing the `RECORDING` face (two dim eyes). The owner wants "something besides just those two eyes".
3. **At Close, she greets the player first on right-click,** with exclamation points if warranted, calls them Cargo aloud, and says "dont make it weird". Those are the owner's words; the final line needs their mark.
4. **Two or three more facial expressions.**
5. **Several more responses, for the Frozen Dawn mobs marked in the manual.** The Threats section has Frostbitten, Frostmite, Hollow and Returned. The M5 plan left the Returned's topics for S5, so the owner must decide that one.
6. **Asking her for advice on items listed in the manual.** Suggested start: the mobs plus about a dozen items, chosen from the owner's typed log, not all 166 entries.

## Facts for the later plan

Read on `feat/miller-m5`; recheck them against M5 as shipped.

- Faces are the 8x6 `Mood` rows in `src/main/java/com/frozendawn/shepherd/ShepherdFace.java`.
- Seven other moods already exist beyond `RECORDING` and `OFFLINE`, and the owner may not have seen them yet.
- Right-click enters at `ShepherdTalk.openHub`.
- 40 of the 166 manual entries are locked behind advancements, so advice needs a new condition so she doesn't spoil locked entries.
- `FieldManual.openEntry` can open a page.
- More topics crowd the intent index, which stands at 83 of 108 top-one on the held-out set (Ruling 29 of `docs/miller-pattern-m5-plan.md` on `feat/miller-m5`).

## Rules for later steps

- Faces need a mockup the owner approves, at 16 pixels per block.
- Only lines the owner marks go in the game.
- Never edit MACS.
- Never open the Notion page "SEALED: Sequel Seed".
- Never edit the main checkout.

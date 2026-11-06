# Miller Pattern M6: the idea

**Status:** the owner's idea and its order, recorded 2026-11-06 during the M5 pass. Not a plan. The owner asked to start the face mockups early and picked from them the same day. No lines or tasks yet: the rest waits for M5.

## Order

1. **M5 finishes first:** the owner's pass, the log sort, the PR and the merge. M6 is planned against M5 as shipped.
2. **A new face for ORSA-scripted answers,** replacing the `RECORDING` face (two dim eyes). The owner wants "something besides just those two eyes". Picked 2026-11-06: A, Voice trace, in `docs/art/aurora-shepherd/orsa-face-mockup.png`. A flat line at rest, moving only while the recording plays.
3. **At Close, she greets the player first on right-click,** with exclamation points if warranted, calls them Cargo aloud, and says "dont make it weird". Those are the owner's words; the final line needs their mark.
4. **Two or three more facial expressions.** Picked 2026-11-06: Beaming, Flustered and Grim, in `docs/art/aurora-shepherd/new-expressions-mockup.png`. The owner also asked for smooth transitions between her faces and the voice trace, and picked Flow in `docs/art/aurora-shepherd/face-transitions-mockup.gif`: texels slide to their new places over 2 to 4 ticks, replacing the blink on a mood change.
5. **Several more responses, for the Frozen Dawn mobs marked in the manual.** The Threats section has Frostbitten, Frostmite, Hollow and Returned. The M5 plan left the Returned's topics for later; the owner put them in M6 on 2026-11-06. Their idea: asked about the Returned, she projects a hologram of it, "kinda like Star Wars", with an easter egg on it. It needs a mockup, and any egg line needs their mark. Scope, the owner's words: "i want all returned, but dont do any post maeve ones." So all three variants are in, and the post-Maeve hearth residents (`PostMaeveWorldState`) are out.
   - **The Architect's hologram** looks as it does in the manual: "buggy as hell, glitches etc", after the failed captures (`architect_capture_1` to `3` in `textures/gui/patchouli`). "maybe have it think or sometihng": the owner's idea, to pin down in the mockup. This is a visual only; it touches no Architect AI.
   - **She comments on the Architect a lot,** with "ORSA was terrified of this guys" as the owner's example. The final lines need their mark.
   - **For all the Returned she gives practical advice** on their weaknesses and strategies, from the Counters in the manual's `threats/returned` entry. That entry has no advancement lock.
6. **Asking her for advice on items listed in the manual.** Suggested start: the mobs plus about a dozen items, chosen from the owner's typed log, not all 166 entries.

## Facts for the later plan

Read on `feat/miller-m5`; recheck them against M5 as shipped.

- Faces are the 8x6 `Mood` rows in `src/main/java/com/frozendawn/shepherd/ShepherdFace.java`.
- Seven other moods already exist beyond `RECORDING` and `OFFLINE`, and the owner may not have seen them yet.
- Right-click enters at `ShepherdTalk.openHub`.
- 40 of the 166 manual entries are locked behind advancements, so advice needs a new condition so she doesn't spoil locked entries.
- `FieldManual.openEntry` can open a page.
- `VaultRecordingRenderer` already draws a see-through figure, and `ReturnedRenderer` has the Returned's model, so the hologram has a starting point.
- `docs/aurora-shepherd.md` says she feels guilt about the Returned, so the egg must not undercut that.
- More topics crowd the intent index, which stands at 83 of 108 top-one on the held-out set (Ruling 29 of `docs/miller-pattern-m5-plan.md` on `feat/miller-m5`).

## Rules for later steps

- Faces need a mockup the owner approves, at 16 pixels per block.
- Only lines the owner marks go in the game.
- Never edit MACS.
- Never open the Notion page "SEALED: Sequel Seed".
- Never edit the main checkout.

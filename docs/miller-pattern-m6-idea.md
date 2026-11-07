# Miller Pattern M6: the idea

**Status:** built on `feat/miller-m6` and passed by the owner, 2026-11-06. The owner's idea and its order, recorded 2026-11-06 during the M5 pass. The owner's picks and strategies, kept as the spec for [miller-pattern-m6-plan.md](miller-pattern-m6-plan.md), which the owner approved the same day.

## Order

1. **M5 finishes first:** done, merged as PR 164. M6 is planned against M5 as shipped.
2. **A new face for ORSA-scripted answers,** replacing the `RECORDING` face (two dim eyes). The owner wants "something besides just those two eyes". Picked 2026-11-06: A, Voice trace, in `docs/art/aurora-shepherd/orsa-face-mockup.png`. A flat line at rest, moving only while the recording plays.
3. **At Close, she greets the player first on right-click,** with exclamation points if warranted, calls them Cargo aloud, and says "dont make it weird". Those are the owner's words; the final line needs their mark.
4. **Two or three more facial expressions.** Picked 2026-11-06: Beaming, Flustered and Grim, in `docs/art/aurora-shepherd/new-expressions-mockup.png`. The owner also asked for smooth transitions between her faces and the voice trace, and picked Flow in `docs/art/aurora-shepherd/face-transitions-mockup.gif`: texels slide to their new places over 2 to 4 ticks, replacing the blink on a mood change.
5. **Several more responses, for the Frozen Dawn mobs marked in the manual.** The Threats section has Frostbitten, Frostmite, Hollow and Returned. The M5 plan left the Returned's topics for later; the owner put them in M6 on 2026-11-06. Their idea: asked about the Returned, she projects a hologram of it, "kinda like Star Wars", with an easter egg on it. It shows in the world, the owner's pick: the veil lifts, the talk panel drops to her strip, and the figure stands to her left so she can look at it. See `docs/art/aurora-shepherd/hologram-mockup.gif`, approved by the owner 2026-11-06. It draws a stand-in figure; the build uses the real models. Any egg line needs their mark. Scope, the owner's words: "i want all returned, but dont do any post maeve ones." So all three variants are in, and the post-Maeve hearth residents (`PostMaeveWorldState`) are out.
   - **The Architect's hologram** looks as it does in the manual: "buggy as hell, glitches etc", after the failed captures (`architect_capture_1` to `3` in `textures/gui/patchouli`). "maybe have it think or sometihng": the owner's idea, to pin down in the mockup. This is a visual only; it touches no Architect AI.
   - **She comments on the Architect a lot,** with "ORSA was terrified of this guys" as the owner's example. The final lines need their mark.
   - **For all the Returned she gives practical advice** on their weaknesses and strategies. First the Counters from the manual's `threats/returned` entry, which has no advancement lock, then her own better way: "word of advice? do this instead" is the owner's example. How she presents it differs for each Returned; she doesn't say "word of advice" every time.
   - **Her strategy has to be really good,** "another reason to boot her up". Each one must beat the manual's counter in play, so the plan checks it against the mob's actual behavior in code and in a playtest, not just on paper.
6. **Asking her for advice on items listed in the manual.** Suggested start: the mobs plus about a dozen items, chosen from the owner's typed log, not all 166 entries.

## Facts for the later plan

Rechecked against M5 as shipped, PR 164.

- Faces are the 8x6 `Mood` rows in `src/main/java/com/frozendawn/shepherd/ShepherdFace.java`.
- Seven other moods already exist beyond `RECORDING` and `OFFLINE`, and the owner may not have seen them yet.
- Right-click enters at `ShepherdTalk.openHub`.
- 40 of the 166 manual entries are locked behind advancements, so advice needs a new condition so she doesn't spoil locked entries.
- `FieldManual.openEntry` can open a page.
- `VaultRecordingRenderer` already draws a see-through figure, and `ReturnedRenderer` has the Returned's model, so the hologram has a starting point.
- `docs/aurora-shepherd.md` says she feels guilt about the Returned, so the egg must not undercut that.
- More topics crowd the intent index, which stands at 83 of 108 top-one on the held-out set (Ruling 29 of `docs/miller-pattern-m5-plan.md`).

## Her strategies, for the owner's verdict

Drafted 2026-11-06 from the code on `feat/miller-m5`. The owner says which work; only those go on to become lines. The owner approved all five the same day: Hunter, Mimic and Architect ("wow those strats WORK"), then Frostbitten and Hollow.

- **Hunter: show it a heater.** The manual says hide backup heaters. Instead, set a lit heater 3 blocks behind a one-high window, on the Hunter's own level. `ReturnedExtinguishHeaterGoal` outranks its melee, needs only line of sight, and must get within 2 blocks to put the heater out. The heater burn reaches 4 blocks. It stands at the window burning, at about 4.5 a second plus fire, against 50 health. It scans every 10 to 20 seconds, within 16 blocks.
- **Mimic: stand still and shoot.** For its 5 seconds of copying, it takes your velocity and doesn't fight back, and a hit doesn't end the phase. Stand still and it stands still; walk at it and it backs away at your speed. Below 20 of 40 health it runs, and it never heals. If you stay within 16 blocks it copies you again, so a second free window can finish it.
- **Architect: fight it at your heater, and don't be a pattern.** An ordinary Architect doesn't avoid heat, so within 4 blocks of a lit heater it takes the same burn against 40 health. The manual says press the attack while it heals, but it records chasing a withdrawal, swords and ranged damage as patterns and answers them. It drinks once a minute, so let it drink, then force the next retreat inside that minute. Mix weapons.

- **Frostbitten: close in.** The manual says avoid melee, but its hand only gives Slowness I. The freeze comes from the snowball: 3 damage, Slowness II and 100 freeze. It throws only between 8 and 16 blocks, every 3 seconds, and melee outranks throwing, so it likely throws mostly when it can't reach you. Get inside 8 blocks and stay on its level. It's slow, and fire does double, so a heater does 4 a second against 30 health. Unconfirmed: it sinks in water and freezes the water around it, so a two-deep trench may entomb it, and a one-deep moat just becomes its bridge.
- **Hollow: let it grab you.** The manual says run. It isn't hunting you: it has no player target, and it drifts at random at about 0.6 blocks a second. A grab ends on any swing, even empty-handed, before the first ice at half a second, and then it can't grab again for 30 seconds. That's 30 free seconds on 20 health. A Frost Ward Torch only sends it away, with no Frozen Breath. A lit heater does 4 a second within 8 blocks, so it dies in about 5 seconds, and any damage also breaks a grab.

**The Architect worry.** The manual says Architects later in the event wasted fewer strikes and that each variant keeps something the last one learned. The owner wants her to hint that ORSA noticed this, "over time, the architects stopped making the same mistakes", in her own words, with her face actually worrying. The owner turned down three drawn worried faces and chose motion: no new face. Mid-line she stops, her eyes dim and slide to the hologram, and her mouth shrinks, as in `docs/art/aurora-shepherd/worry-motion-mockup.gif`, which the owner approved.

Her advice must not name Maeve or MACS. The manual already says each Architect keeps what the last one learned, so she can go that far. `ArchitectThinkingController` has a hand-to-chin pose the hologram could use for "have it think".

## Rules for later steps

- Faces need a mockup the owner approves, at 16 pixels per block.
- Only lines the owner marks go in the game.
- Never edit MACS.
- Never open the Notion page "SEALED: Sequel Seed".
- Never edit the main checkout.

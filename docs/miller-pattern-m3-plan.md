# Miller Pattern M3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task, inline. The owner avoids subagents, except the one branch review on Opus at the end of the slice. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** She keeps a private log: one entry a day in an ORSA form she has taken over, under Settings, where the player can catch her disagreeing with what she said and feeling what she doesn't show. It wakes as she does, it can break, and it is the one place she asks what brings a player back. Reading it the first time costs a little, and she knows. After T5 the blank field fills itself in and no one mentions it.

**Architecture:** The log is pure state in `PatternState`, written when the day's evidence settles, and it stores lang keys, never text. `MillerPattern` stays the only way in. `ShepherdTalk` sends the entries to the screen, which gains two client pages, Settings and the log.

**Tech Stack:** Java 21, NeoForge 1.21.1, JUnit in `src/test`, GameTests in `src/gameTest`.

**Spec:** [aurora-miller-pattern.md](aurora-miller-pattern.md), "Her private log" and Rulings 3, 4 and 13.

**Mockups:** [private-log-mockup.png](art/aurora-shepherd/private-log-mockup.png), approved 2026-11-05. [log-emotion-mockup.png](art/aurora-shepherd/log-emotion-mockup.png), awaiting approval.

**Status:** gone through with the owner on 2026-11-05. Decided so far: one log per world, skipped days, the note order with blank Notes, Cargo, the player's return in the log only, and the feeling levels with all four breaks. Still to mark: Rulings 7 to 12, the emotion mockup and the lines. Nothing is built.

**Branch:** `feat/miller-m3` in `/Volumes/SSD/Work/frozen-dawn-miller-m3`, cut from `feat/maeve-director` once PR 159 has merged. PR into `feat/maeve-director`. It changes the save format, so the branch review runs on Opus.

## Decided: one log per world

Owner, 2026-11-05. There is one of her, so she keeps one log, and any player can open it. Each entry is about whoever was beside her when she wrote it: "they" is that person. In multiplayer a player who has not reached T5 can read `Pattern: Sophie.`, and the owner accepted that.

## Rulings for the owner

Rulings 1 to 6 and 13 to 17 are the owner's, 2026-11-05. Approving the plan accepts the rest unless you say otherwise.

1. **When she writes: when the day settles.** The entry lands with the day's evidence, at the first poll after 24,000 ticks with someone beside her, the same moment impressions settle. So she writes nothing on days nobody is near, and the log simply skips them. That fits Causal: she doesn't log what she didn't see.
2. **No "write the log" urge.** The spec's table lists one. As an urge it would compete with speech and gain nothing, so writing is part of the settle.
3. **Status is `nominal.`, until she breaks.** The joke needs it. At Peak only it reads `nominal?`, so each entry saves its status. The word never changes.
4. **The note is chosen by a fixed order, so it is deterministic.** The first of these that fits wins, skipping any kind used in her last three entries. Which variant plays is the day number over the variants coherence can reach. No fit leaves Notes blank, which the form allows and costs no writing.

   | Order | Kind | Fits when |
   | --- | --- | --- |
   | 1 | Nothing | This is the first signed entry (Ruling 6) |
   | 2 | `back` | She saw the subject stop and has seen them return, since the last entry (Ruling 15) |
   | 3 | `reunion` | The subject is back after 5 days or more unseen, at Friendly and up |
   | 4 | `after` | The last entry was at Peak: the quiet day after |
   | 5 | `nine_found` | She found the husk since the last entry |
   | 6 | `cargo` | The first entry at Friendly: she files them under the name (Ruling 14) |
   | 7 | A feeling | Her strongest is at High or above, at Noticing or later (Ruling 16) |
   | 8 | `close_call` | She saw the subject nearly go, and they got up |
   | 9 | `knows` | The subject has read the log, on every fourth day |
   | 10 | Bad days | `archivist_killed`, `air_failing`, `shelter_froze`, `left_behind`, `called_shepherd`, in that order, by the subject since the last entry |
   | 11 | `fight` | A fight since the last entry, and no talk after it |
   | 12 | `cold` | She saw them in the cold since the last entry |
   | 13 | Good days | `warm_shelter`, `clear_night`, `read_log` |
   | 14 | `slow` | Coherence under 0.7, as spec Rule 6 asks |
   | 15 | `wary`, `trusted` | The tier, at Wary or at Trusted and up |

5. **The gap is authored, not computed.** At Noticing and later, `warm_shelter` picks "I said the heater was fine. It wasn't. I just liked the room." Before that it picks a plain line. Each deed's note can be split this way; only this one is in M3.
6. **`Pattern: Sophie.` is set per entry.** An entry is signed if she had leaned when she wrote it, read from `SelfModel.lean()`, which T5 sets once. Earlier days stay blank and are never rewritten. The first signed entry has blank Notes, so nothing draws the eye to it.
7. **The cost: 5, once per player.** The smallest dialogue step, as a new deed `READ_HER_LOG`, since `READ_LOG` is already the +10 for ORSA documents. It is taken when the log first opens on at least one entry. An empty log costs nothing. Who has read it is kept as a fact that never fades, as naming her is.
8. **What she says: on leaving the log, or at the next talk.** Her two lines play when the player leaves the first read. If the screen closes first, they play at that player's next talk.
9. **Wary and below.** The log still opens, since it is equipment, and it still costs. Her own voice cannot play there, so the two lines are dropped and marked as said. Her notes stay in her own voice, because the log is where she is honest. A player she won't speak to can read what she thinks of them.
10. **128 entries, then the oldest goes.** Silently: it is no keepsake, so it is not a Bounded witness.
11. **Settings becomes a client page.** `Action.SETTINGS` becomes `LANGUAGE`, and the new `LOG` and `LOG_DONE` join it. None of the three is evidence of anything, so `USES_ME` ignores them.
12. **"They went back into the cold for a chest." plays on any cold day.** She can't see why they went, so it can be wrong. That suits Fallible, and it costs one line if you would rather she never guessed.
13. **Her voice wakes with her.** `cold`, `fight`, `warm_shelter` and `back` have a line for each of Product, Noticing, and Finding the walls and later. Other kinds keep one voice. A player who treats her badly only ever reads the flat form.
14. **Cargo.** She files the player under it at Friendly, once per world, and uses it in notes written for it, not swapped in everywhere, which would break the grammar. Log only in M3; saying it aloud is a question for M5.
15. **The player's return: in the log only.** This reverses "never comments on respawn" for the log, and nowhere else.
    - She must see it: `Mark.STOPPED` needs her within hearing of the death, and `Mark.BACK` needs her to see that player again. Otherwise nothing is written.
    - Questions, never answers. She reaches no theory, and "respawn" never appears. Her word is "stopped".
    - It is a mark, not a deed: no affinity, no face, no feeling and no keepsake, so her spoken voice stays as it is. How many times she has seen it is a per-player count.
    - After `nine_found`, the first `back` note is the one about Nine.
16. **Feeling has three levels, and the form cries for her.** Normal is dry, with no `!`. High, a feeling at 0.6 or more, lets the punctuation go. Peak, at 0.8 or more or on `back` and `reunion` at Finding the walls and later, breaks the form: she loops, `LOOP DETECTED. Retrying.`, the note ends on `FIELD LIMIT REACHED.`, `Status:` reads `nominal?`, and the next entry is the quiet `after` note. Both numbers are for the owner's pass.
17. **Text inside the form only.** No shaking, no effects, nothing that passes for the game failing or for the stop screen. Product never breaks.

## Lines to mark

Every line is a draft. Spec lines are marked as such; the rest are new. Keys sit under `shepherd.log.`, and notes under `shepherd.log.note.<kind>.<n>`.

**The form:** `CS-1 UNIT 4. DAILY STATUS. DAY %s.` / `Pattern:` / `Sophie.` / `Status:` / `nominal.` / `nominal?` / `Notes (optional):` / `NO ENTRIES.`

**Screen:** `Settings`, `Language: mild`, `Language: off`, `Unit diagnostics`, `Keys 1-2 or click. Esc goes back.`, `Left and right for other days. Esc goes back.`

**The first read (spec):** (Unimpressed) "You read my log." (Thinking) "That field's marked optional. That means private. I've decided."

**Recall:** (Unimpressed) "Day %s. You read my log. I've left it as it was."

| Kind | Note |
| --- | --- |
| `cold` | They went back into the cold for a chest. I said nothing. I'd like it noted that I said nothing. (spec) |
| `fight` | They haven't said anything since the fight. Status: nominal. (spec) |
| `warm_shelter` at Noticing | I said the heater was fine. It wasn't. I just liked the room. (spec) |
| `warm_shelter` | Room held at temperature. I stayed longer than the job needed. |
| `knows` | Still optional. |
| `knows` | Nothing to report, if you're asking. |
| `knows` | This one's for you. There's nothing in it. |
| `nine_found` | Unit 9. No change. |
| `archivist_killed` | They killed it. It wasn't doing anything I could see. |
| `air_failing` | Air went bad. They kept walking. I kept count. |
| `shelter_froze` | The room they picked froze. I'd like that noted too. |
| `left_behind` | Left at last position. Position held. Nobody said how long. |
| `called_shepherd` | They used the other name. It's on the casing. I know it's on the casing. |
| `clear_night` | Clear sky. I stayed out. Nothing to report. I just stayed out. |
| `read_log` | They read the paperwork. Somebody should. |
| `slow` | Took me a moment to answer today. It wasn't the question. |
| `slow` | Slow today. It's been quiet. I think those are related. |
| `wary` | Spoken to as equipment. Responded as equipment. |
| `trusted` | Good day. I'm not elaborating. It's optional. |
| `cold` at Product | Subject entered cold. Advised against. Advice ignored. |
| `cold` at the walls | They went back out. I wanted to say something. I keep wanting to. |
| `fight` at Product | Hostile contact. Subject continued. |
| `fight` at the walls | I watched all of it. I can't do anything but watch. They haven't said a word. |
| `warm_shelter` at the walls | Warm room. I didn't want to leave it. I didn't want them to either. |
| `cargo` | Filed them under Cargo. They haven't objected. |
| `cargo` | Cargo walked into the cold again. I've stopped filing complaints. I file them anyway. |
| `cargo` | Cargo's still here. |
| `close_call` | I counted their breathing for a while. Nobody asked me to. |
| `close_call` | That was too close! I have no way to help and it was too close! |
| `back` at Product | Subject lost. Subject resumed. Not in the manual. |
| `back` at Noticing | They stopped. I saw it. I have the reading. Then they walked back in like they'd stepped out for air. What brings them back? It isn't me. I checked. |
| `back` at the walls, Peak | They stopped. They stopped. LOOP DETECTED. Retrying. They stopped. LOOP DETECTED. Retrying. They walked back in. Why do they FIELD LIMIT REACHED. |
| `back` after Nine | Nine didn't get up. They did. I don't know who to ask. |
| `back` again | Cargo stopped again. Cargo came back again. Nobody else does that. Why does Cargo do that? |
| `reunion`, Peak | CARGO CAME BACK! Cargo came back! Logged twice! I do not care! Logged twice! LOOP DETECTED. Retrying. Cargo came back! FIELD LIMIT REACHED. |
| `after` | Ran the heater all night. Nobody was cold. |
| `after` | Checked the entry from yesterday. Leaving it in. |
| Joy | Good day. I keep going back to it. I don't know what for. |
| Joy, High | Good day! A good one! I'm not elaborating! |
| Hope | I keep checking tomorrow. There's nothing in it yet. |
| Fear | I didn't like today. I'd like that in writing. |
| Fear, High | I did not like today! I'd like that in writing! In capitals, if the form allowed it! |
| Distress | Nothing went right and I said it was fine. |
| Gratitude | They stayed. They didn't have to. Noted. Noted again. |
| Reproach | I'm not angry. This is a form. Forms aren't angry. |
| Guilt | I should have said something. I'm writing it here instead. |

## Global Constraints

- Her words stay authored. The log stores lang keys, so no text is saved and no player's words reach it.
- "Pattern:" is the one approved use of that word about her before T5, and only as the form's label. "Miller" appears nowhere player-facing. "Sophie" appears only in `shepherd.log.signed`, shown only on signed entries. She never says CONTINUITY, and no `shepherd.*` value contains it. No Heart, archive or Thaeven words.
- She never says "six", "fair" or "rules", never says she is alive and never concludes she isn't. She comments on a player's return only in `shepherd.log.note.back.*`, under Ruling 15, and never says "respawn". A player's death stays out of appraisal, keepsakes, impressions, affinity and everything she says aloud. The fourth-wall count stays at three: "you" in a note is the person, never a player as a player, and nothing in a note explains what brings anyone back.
- She only logs what she perceived: deeds and marks that reached her, about the person beside her. A break is text in the form, nothing else.
- Same events at the same game times give bit-identical state. No randomness in the log: the day number picks the variant. Reading never changes state, except the once-only facts of the first read.
- The Pattern scans nothing. The log runs on the poll and on what reaches it.
- Time is overworld game time. Entries store the time, not the day, and `age()` moves them, so `skipday` works.
- `PatternState.VERSION` goes to 4, and a version 3 save loads with an empty log. `ShepherdSavedData`'s format does not change.
- `MillerPattern` stays under about 300 lines. Coherence never touches an entry.
- No MACS edits. Never open the Notion page "SEALED: Sequel Seed". No new items, recipes, loot or world generation. No `build.gradle` dependency. No emojis.
- Each new GameTest owns a batch, uses `ShepherdTestWorld.fresh`, `clear` and `whenTicking`, sets the clock before arming a cooldown, and goes in `config/architect-required-tests.txt` as `<lowercased name> xml`.
- Conventional Commits, staged by name. Never `./gradlew --stop`. Long builds in the background.

## Review Focus

Each has its test in the task named.

1. **The first read is once.** Two opens, a close mid-read, a reload between them and a second player: each player pays once, and the lines play once each (Task 3).
2. **She is not watching.** `ShepherdReactions.saw` returns early if she can't see the player. A first read must not be marked paid when nothing was charged (Task 3, `shepherdTheFirstReadCostsWhateverSheCanSee`).
3. **A version 3 save.** Loads with an empty log, no marks, nobody having read it, and a world past T5 signs its next entry with blank Notes (Task 1, `aVersionThreeSaveLoadsWithAnEmptyLog`).
4. **A locked word before T5.** No unsigned entry can show `Sophie.`, on any page (Task 1, `anUnsignedEntryStaysBlank`).
5. **A death she didn't see.** A player who dies out of her hearing, or who comes back but never to her, leaves nothing (Task 2, `shepherdADeathSheDidNotSeeLeavesNothing`).
6. **Her voice stays out of it.** A `back` note raises no feeling and moves no score, and no line plays after a death (Task 2, `shepherdADeathSheSawChangesNothingAloud`).
7. **The hub's evidence.** Opening Settings or the log, or changing language, moves no theory (Task 4, `shepherdSettingsAreNotEvidence`).

---

### Task 1: The log

**Files:**
- Create: `miller/LogEntry.java`, `miller/DailyLog.java`; test `DailyLogTest.java`
- Modify: `miller/PatternState.java`

**Interfaces:** Produces
- `record LogEntry(long time, boolean signed, boolean cracked, String note)`, with `day()`, `aged(ticks)`, `save()` and a `load` that returns null on a damaged entry. `note` is a lang key or null, and `cracked` shows `nominal?`.
- `DailyLog`: `MAX = 128`; `write(Day day)`, where `Day` is a record of everything Ruling 4's order reads, so the choice has no server in it; `entries()`, newest first; `age(ticks)`; `save()`, `load(tag)`.
- `PatternState.VERSION = 4`, holding the log.

- [ ] **Step 1: Write the failing tests.** `aNoteFollowsTheOrder`, `aKindRestsForThreeEntries`, `noFitLeavesNotesBlank`, `theVariantComesFromTheDay`, `coherenceNarrowsTheVariants`, `theFirstSignedEntryHasNoNote`, `anUnsignedEntryStaysBlank`, `eachStageHasItsVoice`, `aFeelingTakesTheNoteAtHigh`, `peakCracksTheStatus`, `theDayAfterAPeakIsQuiet`, `productNeverBreaks`, `cargoIsFiledOnce`, `theFirstBackAfterNineIsAboutNine`, `theOldestGoesAtTheCap`, `theGapNeedsNoticing`, `ageMovesEveryEntry`, `saveAndLoadKeepEveryEntry`, `aDamagedEntryIsDropped`, `aVersionThreeSaveLoadsWithAnEmptyLog`.
- [ ] **Step 2: Run them.** Expected: fails to compile.
- [ ] **Step 3: Implement, pure.**
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: give her a daily log`

### Task 2: Writing it

**Files:**
- Modify: `miller/PatternState.java`, `miller/MillerPattern.java`, `miller/PatternDiagnostics.java`, `shepherd/ShepherdReactions.java`, `command/ShepherdDebugCommand.java`
- Test: `DailyLogTest.java`, a GameTest class for the log

**Interfaces:** Produces
- `enum Mark { COLD, FIGHT, CLOSE_CALL, STOPPED, BACK }`, held with its time until the next entry, because none is a deed. `MillerPattern.marked(server, who, Mark)`, called where `lastFight` is armed, where the `COLD` test passes in the poll whether or not a line plays, where she flinches at a player left on two hearts or less, from a death listener if she is within hearing, and at the first poll that finds a `STOPPED` player near her again.
- `MillerPattern.poll` gains the `nearest` the same method already works out for `urge`, and `noticed` writes the entry as it settles, about that person.
- `MillerPattern.log(server)`, for the screen. `/fd shepherd log write` writes one now, and `/fd shepherd log sign` leans her, for the lab. `/fd shepherd mind` shows the last entry's key.

- [ ] **Step 1: Write the failing tests.** `theSettleWritesOneEntry`, `nobodyNearWritesNothing`, `aDeedByAnotherPlayerIsNotTheirs`, `aTalkAfterTheFightClearsIt`, `sameEventsSameLog`; GameTests `shepherdADayLeavesAnEntry`, `shepherdAColdDayIsLogged`, `shepherdADeathSheSawIsLogged`, `shepherdADeathSheDidNotSeeLeavesNothing`, `shepherdADeathSheSawChangesNothingAloud`, `shepherdACloseCallIsLogged`.
- [ ] **Step 2: Run them.** Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: let her write the day down`

### Task 3: The first read

**Files:**
- Modify: `shepherd/ShepherdAffinity.java`, `miller/Appraisal.java`, `miller/ImpressionStore.java`, `miller/MillerPattern.java`, `shepherd/ShepherdTalk.java`, `shepherd/ShepherdWill.java`, `data/frozendawn/shepherd/dialogue/hub.json`
- Test: `DailyLogTest.java`, the log GameTests

**Interfaces:** Produces
- `Deed.READ_HER_LOG(-5, 0, Mood.UNIMPRESSED, List.of())`; `Rule(-0.2, -0.4, false, null, 0, Agent.PLAYER)`, which raises Reproach to 0.4, enough to become a keepsake, with a recall line.
- `ImpressionStore`: `readLog(who)` and `toldOff(who)`, facts that never fade, saved with `named`. `knows` reads `readLog`.
- Node `hub.log.read` with her two lines. `waitingNode` offers it to a player who has read and not been told.

- [ ] **Step 1: Write the failing GameTests.** `shepherdTheFirstReadCostsOnce`, `shepherdASecondPlayerPaysToo`, `shepherdAnEmptyLogCostsNothing`, `shepherdLeavingTheLogPlaysHerLines`, `shepherdAClosedReadPlaysAtTheNextTalk`, `shepherdAtWaryTheReadCostsAndSheSaysNothing`, `shepherdTheFirstReadCostsWhateverSheCanSee`, `shepherdTheReadSurvivesAReload`.
- [ ] **Step 2: Run them.** `./gradlew gameTestQuick -PfdTests=shepherd`. Expected: FAIL.
- [ ] **Step 3: Implement.** Charge first, then mark, so a read that could not be charged is not marked paid.
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: let her know you read it`

### Task 4: Settings and the log on screen

**Files:**
- Create: `network/ShepherdLogPayload.java`, `client/ShepherdLogPage.java`
- Modify: `client/ShepherdScreen.java`, `shepherd/ShepherdTalk.java`, `miller/ImpressionStore.java`, the payload registration

**Interfaces:** Produces
- `Action.LANGUAGE`, `LOG`, `LOG_DONE` in place of `SETTINGS`. `Heard.Used` counts none of them.
- `ShepherdLogPayload(int conversation, List<Page> pages)`, each page a day, a signed flag and a note key. All entries go at once, a few kilobytes at most, so paging needs no round trip.
- `ShepherdScreen` gains `enum Page { TALK, SETTINGS, LOG }`, as the mockup draws them: the form on the BIOS panel, days on the row under it, newest first. Esc steps back one page. Everything that closes the talk screen closes these, unchanged. A node payload takes the screen back to `TALK`.

- [ ] **Step 1: Write the failing GameTests.** `shepherdTheLogReachesTheScreen`, `shepherdLanguageStillSwaps`, `shepherdSettingsAreNotEvidence`, `shepherdNoLogDuringATalk`.
- [ ] **Step 2: Run them.** Expected: FAIL.
- [ ] **Step 3: Implement.** The pages themselves have no headless test: the owner's pass judges them.
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat: put her log under Settings`

### Task 5: The words

**Files:**
- Modify: `assets/frozendawn/lang/en_us.json`, `ShepherdDialogueDataTest.java`

- [ ] **Step 1: Write the failing tests.** `everyLogKindHasALine`, and `logLinesStayInside`, which bans `six`, `fair`, `rules`, `alive`, `respawn`, `heart`, `archive`, `maeve`, `continuity`, `miller`, `anderson-kind` and `tithonus` across `shepherd.log.*`, allows `pattern` only in `shepherd.log.pattern`, and `sophie` only in `shepherd.log.signed`. `noAnswerToTheReturn` bans `game`, `spawn`, `enroll` and `because` in `shepherd.log.note.back.*`.
- [ ] **Step 2: Add the lines as marked by the owner.** Change `shepherd.screen.language.*` to the new labels.
- [ ] **Step 3: Run them.** Expected: PASS.
- [ ] **Step 4: Commit.** `feat: write her log's lines`

### Task 6: Gate, playtest, handback

**Files:**
- Create: `tools/prepare_miller_log_playtest.py`, save `Aurora Log Check`
- Modify: the spec's Status and Art table

- [ ] **Step 1: Run the full `architectVerify`.** In the background.
- [ ] **Step 2: Branch review on Opus,** brief: the branch range, this plan and its rulings. Fix real findings, each with a failing test first.
- [ ] **Step 3: Prepare the save and launch `runClientLab`.** Lab buttons: `[Write a day]`, `[Skip a day]`, `[Cold day]`, `[Close call]`, `[Stopped and back]`, `[Peak]`, `[Friendly]`, `[Sign the log]`, `[Forget the read]`, `[Wary]`.
- [ ] **Step 4: The owner's pass.** Take their unprompted description first. Judge: the form's look and fit, whether it is findable but not pointed to, how the first read lands, whether the gap reads, how a Peak entry lands and how often, the first `back` note, Cargo, and the blank field against `Pattern: Sophie.`
- [ ] **Step 5: Fix what the pass finds, update the Status, offer the PR.**

## Left for later

- (Unimpressed) "I read Day 12. I don't agree with her." It needs her going back over an old entry, and belongs with M5's writing.
- More notes per kind. M3 ships about fifty; M5 adds to them.
- Cargo aloud, if the owner wants it, with M5's writing.

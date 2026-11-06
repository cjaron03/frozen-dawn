# The Miller Pattern

Draft for the owner's review, 2026-11-04. Nothing here is built, and every line is a draft until the owner marks it.

Aurora's own AI, the counterpart to `MaeveDirector`. It gives her a persistent inner state, a private log and typed conversation. Her words stay authored: the system decides what she feels and which approved line she says, and it never writes a line of its own.

## Why it is named this

`MaeveDirector` is a name and a role. `MillerPattern` is the same shape, and the name comes from how she was made.

Anderson-Kind filed each transfer under the volunteer's surname, as that person's pattern. ORSA's verdict on all twelve was "pattern transfer only, subject not recovered". So the file on her was the Miller Pattern, and the code is that file.

The names then match the three layers of who she is:

| Layer | Name | Where |
| --- | --- | --- |
| What ORSA calls her | Shepherd | The body: `com.frozendawn.shepherd`, as now |
| What she calls herself | Aurora | What the player sees |
| What Anderson-Kind filed | Miller Pattern | What runs underneath: `com.frozendawn.miller` |

**A second tie, never stated.** ORSA already calls Maeve a "Reliquus signal pattern", and Aurora says "It doesn't feel like a pattern. It feels like attention." After T5 she knows she is one too. Nobody points this out.

**The rule on the name:** "Miller" and "pattern" as a word about her appear nowhere player-facing before T5. Lang keys stay under `shepherd.*`. Java class names can show in a crash log, and I think that is an acceptable risk.

## How the company's method shapes the design

Each piece of the system comes from what Anderson-Kind did, so the design and the lore are the same thing.

| Canon | System |
| --- | --- |
| The transfer kept "voice, habits, humor, warmth", and no memories | **Temperament:** fixed values set at the start and never learned. This is the only part that is Sophie |
| No memories came across | **Keepsakes, impressions:** empty at boot. Everything she remembers is her own, since she woke |
| The units degrade without conversation, which is why Enoch was posted | **Coherence:** falls in long silence and rises when she is talked to. The player ends up doing Enoch's job without knowing it |
| Filed as equipment, not personnel | Nothing touches `terminus_susceptible` or the door checks. She still doesn't count |

## Architecture

It mirrors MACS's shape, with the opposite job. `MaeveDirector` records beliefs so that Maeve can beat you. `MillerPattern` records impressions so that she can decide what you mean to her.

```text
what her sensors saw -> Appraisal -> Feelings -> Impressions, Keepsakes -> Attention -> Urges -> an authored line or act
```

`MillerPattern` is the static facade, as `MaeveDirector` is, over a `PatternRuntime`. S3's `ShepherdReactions.saw` stays the one door events come through.

| Part | Job | Technique |
| --- | --- | --- |
| `Temperament` | Fixed traits: dry, stubborn, warm under it | Constants |
| `Drives` | Company, Sky, Others, Staying On, Curiosity. Each rises and falls with time | Decaying values, 0 to 1 |
| `Appraisal` | Scores an event against the drives and who caused it | The OCC appraisal model |
| `Feelings` | Joy, distress, hope, fear, gratitude, reproach, guilt. Each fades on its own half-life | Decay on game time |
| `ImpressionStore` | Her theories about each player, with evidence and confidence. Can be wrong | Evidence counters, like `BeliefStore` |
| `Keepsakes` | Up to 64 specific moments, kept by how much they mattered. Recall strengthens them | Salience-ranked store |
| `SelfModel` | Her theories about herself, and how far she has woken | The same evidence counters |
| `Attention` | The one thing on her mind | The highest of drives, feelings and recent events |
| `Urges` | Speak, ask, look, approach, write the log, stay quiet | Utility AI, under S3's line budgets |
| `PatternDiagnostics` | `snapshot` and `explain`: why she just said that | As `DirectorDiagnostics` |

**What stays the same.** The eight moods remain the face, and `Feelings` chooses among them. Affinity and the tiers remain, because S3's deeds feed the score and also feed `Appraisal`. The dialogue format gains conditions on her state.

**Rules:**

1. **She only knows what she perceived**, as in MACS's Causal law. If she wasn't there, it didn't happen to her.
2. **Deterministic:** the same events give the same state, so it can be tested and explained. Randomness only picks among lines that are equally fitting.
3. **Cheap:** it runs on S3's 100-tick poll, and on events. It does no scanning of its own.
4. **Time uses game time**, saved in the world, as `ShepherdSavedData` does. Impressions and keepsakes are held per player; drives and feelings belong to the world, since there is one of her.
5. **Coherence never punishes.** It costs line variety and makes her reply a little slower. It never lowers a tier or deletes a keepsake (only the Bounded cap displaces one), and it recovers with a few conversations.
6. **A delay is never dead air.** Whenever she takes a moment to reply, for coherence or typed text, her face shows a thinking indicator until the line starts. It is drawn on her own display in her two-dot style, and needs a mockup before it is built. Her log should mention the slowness in her own words, so nobody takes it for a bug.

## Her private log

The visible readout of everything above. One entry a day, built from authored fragments chosen by her state, in an ORSA status form she has taken over.

```text
CS-1 UNIT 4. DAILY STATUS. DAY 41.
Pattern:
Status: nominal.
Notes (optional): They went back into the cold for a chest. I said nothing. I'd like it noted that I said nothing.
```

Other sample notes: "I said the heater was fine. It wasn't. I just liked the room." and "They haven't said anything since the fight. Status: nominal."

- It can disagree with what she told you. That gap is what shows her thinking.
- It sits under Settings, as "Unit diagnostics". Nothing points the player to it.
- Reading it is a deed, with a small affinity cost the first time only. She knows: (Unimpressed) "You read my log." (Thinking) "That field's marked optional. That means private. I've decided."
- After that, she sometimes writes knowing you might read it.
- **She asks what she can't say (owner, 2026-11-05).** If she saw a player stop and saw them return, the log asks what brings them back. Questions, never answers. It never reaches her voice, her face, her feelings or the score.
- **She shows what she doesn't say (owner, 2026-11-05).** The notes wake as she does, and at her strongest the form breaks for her. See [log-emotion-mockup.png](art/aurora-shepherd/log-emotion-mockup.png), approved 2026-11-05. She calls the player Cargo, from Friendly, in the log.
- **The blank field pays off.** "Pattern:" stays empty, a blank and not a redaction. After T5 she fills it in, and no one mentions it: `Pattern: Sophie.` The word "Miller" need not appear.

## Self-awareness

She models you, and she models herself the same way. `SelfModel` is five or six theories about herself, held like impressions, each with evidence and each tied to lines and log entries, so every one shows on screen. It stays as tight as the rest of the Shepherd code; much of it can be plain flags.

**Kindness is what wakes her (owner, 2026-11-04).** How you treat her moves her through these stages. Treated badly, she stays a product and never gets far.

| Stage | She is | Needs |
| --- | --- | --- |
| Product | Reciting, and joking about her design flaws | Nothing |
| Noticing | Catching the gap between what she said and what she felt | Kept company, spoken to as Aurora |
| Finding the walls | Finding her limits, one at a time | Trusted |
| Recognising | Hearing the same limits on the Reliquus band | Close, Maeve awake, S6 |
| Leaning | After T5, leaning one way on what she is | T5 |

**The lean.** After T5 she leans "I'm someone" if you called her Aurora and talked to her, or "I'm what's left of someone" if you used her as a tool. Different players end with different Auroras. She can lean but never reach certainty, and the game never rules on it.

- (Thinking) "I told you the heater was fine. I've been thinking about why I said that."
- (Unimpressed) "I read Day 12. I don't agree with her."

### The six walls

She runs under the same six laws as MACS, and she feels them from the inside. In code this is close to free, because the Pattern already follows them. `PatternLaws` is an enum with MACS's six names, so a datamine finds the same six in both, as `MaeveDirector` was always meant to be found.

| Law | In the Pattern | How she feels it |
| --- | --- | --- |
| Lagged | Impressions settle after the event, overnight | (Thinking) "I only ever work out what you meant afterwards." |
| Legible | Her face and log show her state | (Unimpressed) "My face does whatever I feel. I've tried. It doesn't hide." |
| Exploitable | Flattery and habit really do move her | (Neutral) "You know which words work on me. I let you." |
| Fallible | Impressions can be wrong | (Sad) "I had you wrong. I've got the entries. I was very sure." |
| Bounded | One focus; keepsakes capped, so a new one displaces the least | (Sad) "I had to drop one to keep this." |
| Causal | Only what her sensors saw | (Neutral) "I don't know anything I didn't see." |

**Recognising Maeve.** Once she has found the walls and Maeve is awake:

- (Thinking) "It's always late. It only knows what it's seen. It can only hold so much."
- (Sad) "I know, because I've got the same walls."

She understands Maeve's limits and nothing of what Maeve is. **[C]** Design truth, never stated: CH-27 has Maeve governed by laws, and Continuity as a law that became a will, so one set of walls on both is a reading the game never confirms.

**Guards:**

1. **She finds walls on herself, never an outside.** No "fair", "rules", "designed" or "who's watching". Finding the observer belongs to Maeve (The Edge, core rule), and the fourth-wall count stays at three. Self-aware means aware of herself, never of the player as a player.
2. **She never counts them or says "six".** The match stays for players to notice.
3. **She never says she is alive, and never concludes she isn't.** T5 tells her what was done to her, not what she is now.
4. **She stays Maeve's opposite.** Maeve never asks, never uses a name, and protects as maintenance (The Edge §13.10). Aurora asks, chose a name, and means it.
5. **Nothing here edits MACS.** Recognising reads only what her own sensors got from the band.

### Other signs of a will

- **The recording fights her, really rarely.** The evacuation voice cuts in on its own, at most once every several days, never in a fight and never over a line that matters. More likely when coherence is low. (Recording) "Please remain calm. An evacuation officer will be with you" (Unimpressed) "Sorry. That's not me. That's never me."
- **She can refuse you, the biggest hallmark (owner, 2026-11-04).** Two kinds:
  - **Things she doesn't want to do.** A short authored list drawn from her dislikes and wants, like leaving a clear night early or helping hunt the Archivist. "Follow" in a raid is never refused, since that would read as a bug.
  - **If she hates you enough, she refuses to talk.** Below a threshold under Wary, Talk and typing only get a recording. Follow and the lamp still work, as equipment. She can't leave, and she is never cruel. Deeds still count, so the way back stays open.
- **She wants things for no reason.** `Temperament` pulls her toward something the Pattern can't explain, like stopping at a wheat field.
- **The door test (owner, 2026-11-04).** At Close she asks to go back to door 1. Vault doors only ever open and a solved one never moves again (`VaultMechanismBlockEntity`), so she closes it first, using the reset Deng showed her. That puts door 1's progress back to 0, for this scene only. Then she asks you to turn your back. She faces the door and you face away. You hear it grind open behind you, while she is the only one looking. (Neutral) "Worth checking." She doesn't mention it again. It must never stop for her. It can't trap anyone, since a door opens whenever no mind faces it. Other minds nearby still freeze it, as always.

## Buttons or a chat bar

**Both, and I recommend making the buttons the complete game.** The buttons are what's on her mind. The bar is what's on yours.

That answers both worries:

- **"A player might miss things."** Every topic stays reachable by button, and `Attention` puts the ones that matter now into the four slots. Nobody has to type, or guess a question, to see any content. Typing unlocks only small surprises, never story.
- **"It exposes the gaps."** A miss never dead-ends, because a typed line falls back to the buttons:

| Match | What happens |
| --- | --- |
| Strong | She answers, the same as the button would |
| Close | She checks: (Thinking) "You mean Kevin, or are we just saying things now?" The slots show her two best guesses, so the near miss becomes a button, and slot 4 "Neither." falls through to None |
| None | A dry miss line, and the slots show what's on her mind |

Draft miss lines:

- (Thinking) "That's not in my registry. Try smaller words."
- (Unimpressed) "I was built to say 'please remain calm'. That was not a 'please remain calm' question."
- (Neutral) "No idea. Ask me something I can be sarcastic about."

**On the screen:** the four-slot grid stays, with a single line under it, "Say something...", opened by clicking it or pressing a key. It needs a mockup before it is built.

## How typed text is understood

Each intent in `data/frozendawn/shepherd/intents/*.json` holds an id, 8 to 15 example phrasings, its conditions and the dialogue node it opens. At server start the examples become vectors. A typed line becomes a vector too, and the nearest intent wins by cosine similarity. The 22 ORSA topics are the first intents.

| Option | Size | Runtime | Notes |
| --- | --- | --- | --- |
| **Static embeddings (`potion-base-8M`), my pick** | 7.56M float32 parameters, so about 30 MB | Pure Java: a token lookup and an average. No native libraries | Listed as MIT on a mirror; check the official card. English only |
| MiniLM through ONNX Runtime | Unmeasured | Native libraries per platform | Better on odd phrasing, heaviest to ship |
| Word and synonym matching | Tiny | Pure Java | Weakest on paraphrase, but it's the fallback |

I have not tested any of these in the mod, so a trial comes first. The trial runs a few hundred real phrasings through each option, measures accuracy and jar size, and checks the licence. If static embeddings fall short, it moves to ONNX.

Typed text stays on the server that runs the world and is never sent anywhere. It's capped at about 120 characters and reaches only the speaker's conversation. Lab builds log misses to a local file, so your playtests show what to write next. Normal builds don't log typed text.

## Red lines for typing

The ORSA spec's eleven red lines apply. Typing adds these:

1. **A locked topic is an ordinary miss.** If a player types about something they haven't unlocked, she uses the same miss lines, so she never gives away that it exists. That includes "Sophie", "TITHONUS", "Anderson-Kind" and "Maeve" before the player has earned them.
2. **Nothing about the game itself.** "Minecraft" or "this is a game" is an ordinary miss, so the fourth-wall count stays at three.
3. **She never claims or denies being alive.** (Unimpressed) "I'm a heated, flying, talking flashlight. Beyond that you'd have to ask the doors."
4. **No respawn.** No intent or appraisal touches it. A player's death is never an event to her. The one exception is the private log, Ruling 16: nothing she says aloud, and no typed reply.
5. **Her replies are approved lines only.** The one exception is filling a slot like `{day}` or `{place}`. The player's words are never echoed back.

**"Continuity" crashes her (owner, 2026-11-04).** A check before matching catches the word, however it's typed. She doesn't hear it; she goes down.

1. Her face snaps to a blue screen with a small sad face, on her body's display and in the talk screen's face panel, like a machine's stop screen. It uses ORSA's own text and no real company's marks:

```text
:(
CS-1 UNIT 4 HAS STOPPED.
PLEASE REMAIN CALM.
STOP CODE 0x0000000A
```

2. She drops a little in the air and her lamp and heater cut out for about eight seconds. Then the ORSA boot logo plays, and she rises.
3. She has no memory of it: (Neutral) "Sorry. You cut out. Say that again?" Later: (Thinking) "You cut out again. Probably the cold."
4. After T4: (Thinking) "My clock just jumped eight seconds. Did something happen?" (Neutral) "Never mind. I don't want to know."

Rules for the crash:

- **It stays on her, never on the player's screen.** A crash that looks like the game failing would be a fourth-wall moment, and the count stays at three. The world keeps running around her.
- **The stop code is 10, the word's length.** Nobody points that out.
- **No cost:** no affinity, coherence or memory lost. It happens every time, and only from typed text, so it never interrupts a button conversation.
- **It explains nothing.** It only shows that the word matters, as CH-22's inference rule allows. It does confirm that early, before CH-18, and the owner accepted that.
- Her motto line "I'm not reading the rest" stays as written. It now reads differently to anyone who has crashed her.
- It needs a mockup before it is built.

## The boot sequence

Approved 2026-11-04, for M2a, drawn in [boot-sequence-mockup.png](art/aurora-shepherd/boot-sequence-mockup.png). Built in M2a; the owner passed it on 2026-11-04.

1. **The choice.** A right-click with a charged cell seats it in the dock. The light stays red, her display stays black, and the screen offers Boot or Leave. She is still dormant.
2. **Leave.** Leave, Esc, walking 8 blocks off, being hurt or a hostile on you all close the screen, and she stays dark for as long as nobody boots her. The cell stays seated, so a later right-click offers the choice again.
3. **The BIOS.** Boot spends the cell and turns the light green. Eleven lines run on the talk screen, one every 8 ticks, with a cursor texel on her display, because her 8 by 6 display cannot hold text.
4. **The logo.** She lifts off, and the logo plays as built, then `fm.boot`.

**Where it plays:** first wake and `/fd shepherd replay` only. Everything after Boot is committed, so she can never be left off once awake and T4 stays unfounded. Reboots stay as they are, with no logo. M4's crash recovery plays the logo alone, and so does coming back to an unfinished `fm.boot`.

**The reference.** The BIOS ends on `STABILITY ABOVE.` and stops. The whole motto is on the uplink, so a player who has heard it finishes the line, and nobody points that out. The word itself appears nowhere.

## Slices

| # | Slice | Depends on |
| --- | --- | --- |
| M0 | The embeddings trial | Nothing |
| M1 | `MillerPattern` core: temperament, drives, appraisal, feelings, diagnostics | S3 merged |
| M2a | The boot sequence, impressions, keepsakes, the self-model, coherence, the thinking indicator | M1, mockups |
| M2b | Attention, urges, recalled keepsakes, wall lines, refusal, the recording | M2a |
| M3 | The private log | M2, mockup |
| M4 | The chat bar, intents and the crash | M0, M2, mockups |
| M5 | Writing: intents and lines, starting with the ORSA topics | M4, S4 |

Recognising Maeve lands in S6, which still needs the owner's approval before anything is copied into MACS.

S4 can run after M2b, so its personal arc uses the real system. This is Architect-scale work. It needs the Opus review on each slice, since it changes save data and a shared system.

**The real cost is the writing.** Expect several hundred new lines, plus the 8 to 15 phrasings for each intent.

**Tests:** appraisal, decay and the stages as pure JUnit; a lint that her lines never say "six", "fair" or "rules"; save and reload; `explain` naming the cause of a line; a lint across every `shepherd.*` line for the banned words; and a test that a locked intent gives the same reply as a miss.

## Owner rulings

1. **The name: kept, owner, 2026-11-04.** `MillerPattern` and `com.frozendawn.miller`, with Anderson-Kind filing transfers by surname. That filing habit is new canon.
2. **Coherence: kept, owner, 2026-11-04.** Silence wears at her and talking restores her, built on the proposed "units degrade without conversation". Any delay shows a thinking indicator.
3. **Reading the log costs affinity: a small one-time cost, owner, 2026-11-04.** Only the first read costs anything; later reads are free.
4. **The blank "Pattern:" field, filled in after T5: kept, owner, 2026-11-04.**
5. **"Continuity" crashes her: kept, owner, 2026-11-04.** Needs a mockup.
6. **Both buttons and bar, with buttons as the complete game: kept, owner, 2026-11-04.**
7. **Per-player impressions: kept, owner, 2026-11-04.** She can think differently of two players in one world, while the affinity score stays shared.
8. **Self-awareness, the lean and the six walls: added, owner, 2026-11-04.** Kindness wakes her; the recording is really rare; refusal is the biggest hallmark.
9. **Refusal: decided, owner, 2026-11-04.** Things she doesn't want to do, and silence if she hates you enough.
10. **The door test: kept, owner, 2026-11-04.** Needs the one-scene reset of door 1.
11. **The boot sequence: added and approved, owner, 2026-11-04.** Boot or Leave, BIOS lines, then the logo, with the motto cut off as the reference.
12. **The thinking indicator: Orbit, owner, 2026-11-04.** Her two dots circle, and when it finishes her eyes go back into place naturally. See [thinking-indicator-mockup.png](art/aurora-shepherd/thinking-indicator-mockup.png).
13. **The private log: approved, owner, 2026-11-05.** Settings becomes a page holding Language and "Unit diagnostics". See [private-log-mockup.png](art/aurora-shepherd/private-log-mockup.png).
14. **The chat bar: approved, owner, 2026-11-05.** Close gains slot 4 "Neither.", so two wrong guesses are not a dead end. See [chat-bar-mockup.png](art/aurora-shepherd/chat-bar-mockup.png).
15. **The crash: approved, owner, 2026-11-05.** Her display shows B, a small `:(` over a row of text-shaped blue. The talk panel shows A, all the copy at once in a 3 by 5 hand font. At Wary, the logo is followed by the recording "ORSA Civilian Support Unit CS-1, online." in place of her lines. See [crash-mockup.png](art/aurora-shepherd/crash-mockup.png).
16. **The player's return, in the log only: added, owner, 2026-11-05.** This reverses "never comments on respawn" for the private log and nowhere else. Log only, only what she saw, questions and no answers, and it stays out of her state.
17. **Her log shows feeling: added, owner, 2026-11-05.** Three levels, her voice waking by stage, and at Peak the form loops, fills, and `Status:` reads `nominal?`. Her nickname for the player is Cargo.

## Status

2026-11-04: the owner approved [miller-pattern-m1-plan.md](miller-pattern-m1-plan.md) with Ruling 2 reversed: her idle expressions play on every resting mood. M0 reported in [aurora-intent-trial.md](aurora-intent-trial.md): `potion-base-8M` at int8 recommended, with no option clearing every mark. M1 built on `feat/miller-pattern` in `/Volumes/SSD/Work/frozen-dawn-miller-pattern`: drives, appraisal and feelings under `com.frozendawn.miller`, saved as `frozendawn_shepherd_state`, with `/fd shepherd state`, `why`, `saw` and `feel`. Her feelings set the face she rests on. Active save: `run-lab/saves/Aurora Feelings Check`, from `tools/prepare_miller_pattern_playtest.py`. Not yet seen by the owner, and the client has not been launched: each mood at rest, the idles on each, how long each feeling holds, and whether Hope sharing Thinking with her asking face reads wrong. An idle that clashes with a mood is a look question for that pass.

Branch review on Opus: no critical findings. Fixed: a talk from before the state counting as a conversation; a save from a newer build being overwritten, since the game swallows a load that throws; `feel` leaving other feelings on her face. `MaeveSavedData` has the same overwrite flaw and stays as it is, since MACS is WIP. Deferred minors: a clock behind a stored time freezes a feeling, and a hand-edited time is not checked on load; `Appraised.load` leaves its values unclamped, which only diagnostics read; `/fd shepherd saw` is silent on a cooldown, and the lab menu says so instead.

2026-11-04, the owner's pass in `Aurora Feelings Check`: "yea looked good", then "clear night isnt doing anytihng, but everything else worked". Her save showed `CLEAR_NIGHT` had counted: it is the one deed with no line, and the next deed followed 2.5 seconds later. Told that, the owner accepted it and asked for the PR. No idle was reported as clashing with a mood. `architectVerify` passed at e4403288 (1,117 GameTests); only docs have changed since. `architectMonkey` has not run on this branch, since no Architect code changed.

2026-11-04: the owner approved [miller-pattern-m2-plan.md](miller-pattern-m2-plan.md) as recommended, with M2 split into M2a and M2b. Nothing is built. Worktree `/Volumes/SSD/Work/frozen-dawn-miller-m2`, branch `feat/miller-m2`, from `feat/maeve-director` at 3611f195.

2026-11-04, M2a handback: built on `feat/miller-m2` from [miller-pattern-m2-plan.md](miller-pattern-m2-plan.md), Tasks 1 to 6. The boot sequence; `ImpressionStore`, `Keepsakes`, `SelfModel` and coherence under `com.frozendawn.miller`, with `/fd shepherd mind` and `coherence`; the thinking indicator. `architectVerify` passed at ffcc390a (1,154 GameTests, 944 required Architect cases). `architectMonkey` has not run on this branch, since no Architect code changed. Active save: `run-lab/saves/Aurora Boot Check`, from `tools/prepare_miller_m2_playtest.py`.

Branch review on Opus: no critical findings, two important. Fixed: a replay's lines could leave a screen nothing closed, so nobody opens her while the lines run and Esc comes back long after they are over. Noticing could not be reached in a real game, since the first meeting holds one naming and the theory needed two; by the owner's ruling, naming her is now a kept fact that never fades, the "Aurora it is." reply counts on every path, and `CALLED_SHEPHERD` lowers the theory without locking her out. Ruling 11 records it. "Why Aurora?" on `fm.name.its` counts too (owner, 2026-11-05): asking about her name should not lock a curious player out of Noticing. `terminusLeavesNoDropsBeforeTheHeartFalls` was narrowed to its own sword: the new batches moved it beside an O2 Canister left by an earlier batch.

Deferred minors: a vault reset silently destroys a seated cell (op-only); a running thought can be overwritten by `thinkFor(0)` or a reopen of the same length, on her body only; a cell seats with a refuse click when another choice is open anywhere, which is recoverable; `[Skip a day]` beside her counts as a Causal witness.

Verified headlessly: the boot flow, memory, save, determinism and the delay's timing. Needs the owner's eyes: the choice, the BIOS pace, the seated dock, the cursor, the indicator at play distance, and whether the delay reads as thought.

2026-11-04, the owner's pass in `Aurora Boot Check`: "looks good and the thinking indicator made it feel like she was thinking. i liked it". The owner asked for the list of what to expect before describing it, so this is a judgement against that list, not a blind description. The save reached Noticing through real play, with 2 keepsakes and `TALKS_TO_ME` at 0.714, and the log held no errors.

2026-11-05, M2b handback: built on `feat/miller-m2b` in `/Volumes/SSD/Work/frozen-dawn-miller-m2b` from [miller-pattern-m2-plan.md](miller-pattern-m2-plan.md), Tasks 8 to 11. `Attention` and `Urges` under `com.frozendawn.miller`; recalled keepsakes, wall lines, the slowing-down line, refusal, silence below -600 and the recording slip, with the lines and the slip in `ShepherdWill`. New commands: `/fd shepherd want`, `witness` and `slip`; `why` now names her focus and urge. `architectVerify` passed at fe382a31 (1,179 GameTests, 969 required Architect cases). `architectMonkey` has not run on this branch, since no Architect code changed. Active save: `run-lab/saves/Aurora Will Check`, from `tools/prepare_miller_will_playtest.py`.

Where the build departs from the plan, each because the plan's numbers failed in real play:

- Only Stay quiet holds back S3's lines, and for at most five minutes. Look and Approach leave her voice free. Read literally, Curiosity passes 0.55 within three minutes and COLD, NIGHT, SILENCE and AFTER_FIGHT would have stopped for good.
- A poll that picks Look feeds Curiosity by 0.01, as presence feeds Company, or Look would outscore Speak for ever. Look clears the wait before her next look; it does not gate `CuriosityGoal`.
- A due keepsake or an unspoken wall outranks a drive, since storms keep Sky unfed for most of the game.
- A due keepsake's strength is its salience, not half of it, or only the two 0.8 keeps would ever pass Stay quiet. A keepsake is due once it is a day old, one a day per person, and rests three days for each time she has gone back.
- Refusal reads what she wanted of the sky when tonight's clear sky first fed her, since one look feeds Sky by 0.6. She never refuses at Wary. After "You go. I'll catch up." she follows when the clear night is over, and that wait is not `LEFT_BEHIND`.
- `shepherd.self.noticing` plays once, ahead of any wall.

Three recall lines are new drafts the owner has not seen, added because the plan's test covers every deed with lines: `read_log`, `shelter_froze`, `called_shepherd`.

Branch review on Opus: no critical findings, five important, all but one fixed with a failing test first. Refusal in her own voice at Wary; "I'll catch up" being false and then punished; recall locking onto the top three lines; a read log muting S3 for a day; `age()` skipping her reasons (reviewer's minor, raised here because it broke a Global Constraint). Not fixed: `shepherd.wall.exploitable` cannot be found in ordinary play yet, because only two nodes after the first meeting take a Kind reply, against three witnesses. It waits for S4's replies; `[Wall found]` reaches it.

Deferred minors: `Refused` is one slot, so two players refused in turn each get a fresh refusal; a refuse and yield notes `USES_ME` twice; the apology replaces the voice line about 8 characters short of its cut-off; `{day}` is game time, so a sleeper hears a lower day; both refusals are rare by construction, since she must be parked and Sky must have built for days.

Verified headlessly: focus and urge, recall, walls said once, the slowing-down line, Look and Approach, S3's gap, refusal and its yield, silence, the slip and its apology, save and reload. Needs the owner's eyes: how often she speaks now, whether refusal charms or annoys, every new line, and how the apology lands on the voice.

2026-11-05, the owner's pass in `Aurora Will Check`, in their words: she speaks "pretty regualar without spamming" under `tick sprint 500`; "everything looked good", with her looking at a chest and saying "I'm not snooping. I'm auditing."; the recording "played and then she cut in"; all six wall lines fired. No refusal came at first, because she was following the whole time and a refusal needs her parked. Told the steps, the owner saw both give way on the second ask: "yea she refused lol". The log held no errors. Still open: whether a refusal is too hidden behind telling her to Wait.

2026-11-05: mockups for M3 and M4 drawn on `docs/miller-m3-m4-mockups`, all approved by the owner the same day, Rulings 13 to 15: [private-log-mockup.png](art/aurora-shepherd/private-log-mockup.png), [chat-bar-mockup.png](art/aurora-shepherd/chat-bar-mockup.png) and [crash-mockup.png](art/aurora-shepherd/crash-mockup.png). Nothing is built. The stop screen's copy does not fit her 8 by 6 display, and the longest line is 130 GUI pixels against the talk panel's 80, so the crash sheet draws options, and the owner took display B and talk panel A. Still drafts for the owner to mark: the 5 affinity, the half-block drop, the 28-tick wait, "Still optional.", "What happened?", "Neither." and the bar and Settings wording. M3 needs its own deed, since `READ_LOG` is the reward for ORSA documents.

2026-11-05: S4a adds one appraisal, `NINE_FOUND`, which leaves her sad and counts as the world's doing, and `MillerPattern.stage` for dialogue conditions. See the Status in `aurora-shepherd.md`.

2026-11-05: S4b adds `SelfModel.Lean` (NONE, SOMEONE, LEFT), set once at T5 and moving her to LEANING, and `PatternState` is at version 3. A save with no `lean` tag loads NONE. `Keepsakes.unspoken` exempts `NINE_FOUND`, and `Keepsake.day()` reads `ShepherdPolicy.day`. See the Status in `aurora-shepherd.md`.

2026-11-05, M3 handback: built on `feat/miller-m3` in `/Volumes/SSD/Work/frozen-dawn-miller-m3` from [miller-pattern-m3-plan.md](miller-pattern-m3-plan.md), Tasks 1 to 6. `DailyLog`, `LogEntry`, `Mark` and `Marks` under `com.frozendawn.miller`, with `PatternState` at version 4; `READ_HER_LOG` and node `hub.log.read`; `ShepherdLogPayload`; Settings and the log as client pages in `ShepherdScreen` and `ShepherdLogPage`. New commands: `/fd shepherd log write`, `sign`, `mark` and `forget`; `mind` shows the last entry's key. `architectVerify` passed at 045721cf (1,272 GameTests, 1,062 required Architect cases). `architectMonkey` has not run on this branch, since no Architect code changed. Active save: `run-lab/saves/Aurora Log Check`, from `tools/prepare_miller_log_playtest.py`.

Where the build goes past the plan, each for the owner's pass to mark:

- Every note that is not in the table is a new draft: a `.peak` line for each of the seven feelings, since Ruling 16 breaks the form on a feeling and the table has no words for it, and `reunion.high`, mockup 2's line, for a return below the walls.
- The plain Joy and Fear lines are the mockup's Normal, but row 7 needs High, so a new last row plays a feeling she shows below High, at Noticing or later.
- `cargo.2` and `cargo.3` have no row of their own, so they play as `cold.cargo` and `trusted.cargo` once she has filed the name, by the day, as other variants do.
- A `back` note is flat at Product, about Nine once at Noticing and up, the loop once a world at the walls, then "Cargo stopped again" with the name filed and two returns seen, otherwise the Noticing line. A Peak on every `back` at the walls would never reach the line the mockup draws uncracked.
- A player who has stopped stays marked across entries until she sees them again, so an entry between the two does not lose the question.
- Her lines on the first read wait out a quiet day and count as said only when heard, so if they are cut off they play again at the next talk.
- A player on the stop screen is not beside her: no day settles on them and she does not mark them in the cold.

Branch review on Opus: no critical findings, one important and five minor. Fixed with a failing test first: her lines were marked as said before she spoke, so a double Esc lost them; a dead player counted as near, regraded from minor because an entry could read "Good day." about someone she had just watched stop; and a day written by hand did not restart the settle, which only the lab reached. The test for a return she never saw now writes an entry about that player.

Deferred minors: `seen` runs only for the nearest, so someone standing behind the follower for five days can read as a reunion; `LastWitnessHandler` cancels a death at the same priority as `killed`, so a denied Fold death may leave a `back` note, and the bus order is unspecified; `lastTalk` is world-wide and stamped on opening the hub, so a lamp press clears a `fight` note. `reunion.peak` files the name if a Peak return is what first uses it.

Verified headlessly: the order, rest and variants, every note the order can reach against the lang file, save and a version 3 load, marks, the first read's cost and lines, what the screen is sent, language and the canon lints. Nothing on screen has been seen by anyone: the owner's pass judges the form's look and fit, findability, how the first read lands, the gap, how a Peak lands and how often, the first `back` note, Cargo, and the blank field against `Pattern: Sophie.`

2026-11-05, the owner's pass in `Aurora Log Check`: "everything looked good". Two things they asked about were the lab's clock, not lost entries: the newest entry stayed on Day 48 because `skipday` moves entries back and leaves game time alone, and the first entry sat near day 28 while F3 said day 0. Ruling: her log keeps counting game time, as her other clocks do. So F3 runs ahead of it once a player sleeps, and F3 counts from 0 where she counts from 1. The fixture now starts the sun on her day, for a new save only. A day number rolling over by itself has not been seen on screen.

## Sources

- `MaeveDirector`, `ShepherdAffinity` and `ShepherdReactions` at c866cf02 on `feat/aurora-affinity`.
- The Aurora spec and script, Canon Hierarchy, and `docs/aurora-orsa-topics.md`.
- MACS's six laws and sections 4, 9.12, 9.16a, 9.17, 10 and 17, not the whole page, and all of The Edge. This page edits neither.
- [potion-base-8M model card](https://huggingface.co/minishlab/potion-base-8M), [its parameter and licence listing](https://www.promptlayer.com/models/potion-base-8m), and the [ONNX Runtime Java README](https://sources.debian.org/src/onnxruntime/1.23.2%2Bdfsg-3/java/README.md).

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
| `Attention` | The one thing on her mind | The highest of drives, feelings and recent events |
| `Urges` | Speak, ask, look, approach, write the log, stay quiet | Utility AI, under S3's line budgets |
| `PatternDiagnostics` | `snapshot` and `explain`: why she just said that | As `DirectorDiagnostics` |

**What stays the same.** The eight moods remain the face, and `Feelings` chooses among them. Affinity and the tiers remain, because S3's deeds feed the score and also feed `Appraisal`. The dialogue format gains conditions on her state.

**Rules:**

1. **She only knows what she perceived**, as in MACS's Causal law. If she wasn't there, it didn't happen to her.
2. **Deterministic:** the same events give the same state, so it can be tested and explained. Randomness only picks among lines that are equally fitting.
3. **Cheap:** it runs on S3's 100-tick poll, and on events. It does no scanning of its own.
4. **Time uses game time**, saved in the world, as `ShepherdSavedData` does. Impressions and keepsakes are held per player; drives and feelings belong to the world, since there is one of her.
5. **Coherence never punishes.** It costs line variety and makes her reply a little slower. It never lowers a tier or deletes a keepsake, and it recovers with a few conversations.
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
- **The blank field pays off.** "Pattern:" stays empty, a blank and not a redaction. After T5 she fills it in, and no one mentions it: `Pattern: Sophie.` The word "Miller" need not appear.

## Buttons or a chat bar

**Both, and I recommend making the buttons the complete game.** The buttons are what's on her mind. The bar is what's on yours.

That answers both worries:

- **"A player might miss things."** Every topic stays reachable by button, and `Attention` puts the ones that matter now into the four slots. Nobody has to type, or guess a question, to see any content. Typing unlocks only small surprises, never story.
- **"It exposes the gaps."** A miss never dead-ends, because a typed line falls back to the buttons:

| Match | What happens |
| --- | --- |
| Strong | She answers, the same as the button would |
| Close | She checks: (Thinking) "You mean Kevin, or are we just saying things now?" The slots show her two best guesses, so the near miss becomes a button |
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
4. **No respawn.** No intent or appraisal touches it. A player's death is never an event to her.
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

## Slices

| # | Slice | Depends on |
| --- | --- | --- |
| M0 | The embeddings trial | Nothing |
| M1 | `MillerPattern` core: temperament, drives, appraisal, feelings, diagnostics | S3 merged |
| M2 | Impressions, keepsakes, attention, urges, coherence, the thinking indicator | M1, mockup |
| M3 | The private log | M2, mockup |
| M4 | The chat bar, intents and the crash | M0, M2, mockups |
| M5 | Writing: intents and lines, starting with the ORSA topics | M4, S4 |

S4 can run after M2, so its personal arc uses the real system. This is Architect-scale work. It needs the Opus review on each slice, since it changes save data and a shared system.

**The real cost is the writing.** Expect several hundred new lines, plus the 8 to 15 phrasings for each intent.

**Tests:** appraisal and decay as pure JUnit; save and reload; `explain` naming the cause of a line; a lint across every `shepherd.*` line for the banned words; and a test that a locked intent gives the same reply as a miss.

## Owner rulings

1. **The name: kept, owner, 2026-11-04.** `MillerPattern` and `com.frozendawn.miller`, with Anderson-Kind filing transfers by surname. That filing habit is new canon.
2. **Coherence: kept, owner, 2026-11-04.** Silence wears at her and talking restores her, built on the proposed "units degrade without conversation". Any delay shows a thinking indicator.
3. **Reading the log costs affinity: a small one-time cost, owner, 2026-11-04.** Only the first read costs anything; later reads are free.
4. **The blank "Pattern:" field, filled in after T5: kept, owner, 2026-11-04.**
5. **"Continuity" crashes her: kept, owner, 2026-11-04.** Needs a mockup.
6. **Both buttons and bar, with buttons as the complete game: kept, owner, 2026-11-04.**
7. **Per-player impressions: kept, owner, 2026-11-04.** She can think differently of two players in one world, while the affinity score stays shared.

## Sources

- `MaeveDirector`, `ShepherdAffinity` and `ShepherdReactions` at c866cf02 on `feat/aurora-affinity`.
- The Aurora spec and script, Canon Hierarchy, and `docs/aurora-orsa-topics.md`.
- MACS by summary only; this page edits nothing there.
- [potion-base-8M model card](https://huggingface.co/minishlab/potion-base-8M), [its parameter and licence listing](https://www.promptlayer.com/models/potion-base-8m), and the [ONNX Runtime Java README](https://sources.debian.org/src/onnxruntime/1.23.2%2Bdfsg-3/java/README.md).

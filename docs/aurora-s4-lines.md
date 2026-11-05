# Aurora S4: lines and files for the owner to mark

Drafts, 2026-11-05. Nothing here is built, and every line is a draft until the owner marks it. The plan is [aurora-shepherd-s4-plan.md](aurora-shepherd-s4-plan.md).

**How to read it.** (Mood) sets her face. Bold is the button label, and "Full:" is what hovering shows. Tags:

- **SCRIPT:** word for word from "Aurora — Dialogue Script v1".
- **FIX 5:** word for word from the spec's fix 5.
- **NEW:** written here, nowhere else yet.
- **[C]:** sits near the Continuity layer, as the script marks them. Script `[C]` lines are signed off; a NEW one is not.

Slots: 1 Ask, 2 Kind, 3 Blunt, 4 Sarcastic. A label over about 20 characters overruns the 137-pixel slot, as S3 found, so long replies get a short label and a full line.

## What each conversation reads

Every read is of state that already exists. S4 stores only what nothing else holds: the flags below, and the lean.

| Conversation | Gate | Reads from the Pattern | Writes |
| --- | --- | --- | --- |
| T2 "The others" | Friendly, T1 asked | `SelfModel` stage, for one line | Flags `t2_heard`, `nine_open` |
| T2 at the site | Above Wary, she is at the husk | Nothing | Deed `NINE_FOUND`: Distress, feeds Others, keepsake `nine_found`. Starts the quiet day |
| T3 "The sky" | Trusted, the sky can show stars | Nothing | Flag `t3_going` |
| T3 at the top | `t3_going`, high, night, stars show | Keepsake `clear_night`, for one line | Keepsake `t3_stars`. `CLEAR_NIGHT` already feeds Sky |
| T4 "Off" | Close | Nothing: it stays as written | Keepsake `t4_off` |
| T5 "Before" | Above Wary, the sheet read | `ImpressionStore`: `CALLS_ME_AURORA`, `TALKS_TO_ME`, `USES_ME`, who named her | `SelfModel` to Leaning, and the lean. Flag `t5_done`. Keepsake `t5_before` |
| Bleed-through | Noticing or later, calm | `SelfModel` stage, the lean | Nothing |

Every reply already feeds impressions through `MillerPattern.heard`: tone, Kind and Sarcastic. No line in S4 stores a second copy of a feeling, a tier or a theory. Her resting face stays `Feelings`' choice; a line's mood stays authored.

S4's Kind replies also make `shepherd.wall.exploitable` reachable in ordinary play, which M2b left waiting on them.

## T2, Friendly: "The others"

**`t2.others`**, on the prompt.

- (Neutral) "There were twelve CS-1 units in this region. I'm unit four. We shared a channel. Status pings, boring stuff. 'Unit seven, nominal.' 'Unit nine, nominal.'" SCRIPT
- (Sad) "Then they stopped. One at a time. Nine was the last. Nine's last ping came from the Blast Pit." SCRIPT, with `{nine_site}` written out, since it is always the pit
- `if stage_noticing` (Thinking) "I don't know why I want to know. Nobody loaded that." NEW

Replies:

1. **What would you want?** Full: "What would you want to find?" → "Someone to complain to who gets it." SCRIPT. Returns to replies 2 to 4.
2. **We'll go look.** → (Happy) "...Okay." (Thinking) "Okay. Marking it." *(+15)* SCRIPT
3. **Probably dead.** Full: "They're probably dead." → (Sad) "Probably. I'd still like to know which kind of probably." SCRIPT
4. **Twelve of you?** → (Amused) "Twelve of me would have been unbearable. I'm the fun one." SCRIPT

Replies 2 to 4 go on to **`t2.mark`**, which sets `nine_open`:

- (Neutral) "Ground ref {x}, {z}. I'll keep it. Ask me and I'll tell you how far." NEW. "Ground ref" is how `TowerArchive` writes the pit.

**`hub.talk.pit`**, Talk while the pit is open and she has not seen the husk: (Neutral) "The pit's {n} blocks {bearing}. I'm not nagging. I'm navigating." NEW

### At the site [C]

**`t2.site`**, a place prompt when she reaches the husk.

- (Sad) "That's Nine." SCRIPT
- (Sad) "Cell's cracked. Memory's... empty. Not wiped. Wiped leaves marks. Empty. Like nobody was ever in there." SCRIPT
- (Sad) "...Can we take its lamp? I don't want to leave it down here in the dark." SCRIPT

Replies:

1. **Talk about it?** Full: "Do you want to talk about it?" → (Neutral) "Not yet. Ask me later. Don't actually ask me later." SCRIPT. Returns to replies 2 and 3.
2. **Of course.** → (Sad) "Thanks." *(+20)* SCRIPT. The player gets the lamp, then `t2.sheet`.
3. **It's scrap.** → (Unimpressed) "It's my friend's scrap." *(-25)* SCRIPT. Nothing is handed over; see the plan, Ruling 7.

**`t2.sheet`**, after the lamp comes out:

- (Thinking) "There's paper behind the housing. Folded. Nobody puts paper in a unit." NEW
- (Sad) "You keep it. I don't want to read anything today." NEW

**At the husk, later:**

- Right-clicking it after "It's scrap.", which hands over lamp and sheet: (Sad) "...Thanks." NEW
- A charged cell on the husk, refused with the dock's click: (Sad) "It wouldn't. The cell isn't the part that's missing." NEW **[C]**

**The quiet day.** After the site she is quiet for one in-game day. Her face still reacts and every deed still counts; nothing goes on the strip, and nothing personal is asked.

- Talk during it, `hub.talk.quiet`: (Sad) "Not today." NEW
- When it ends: (Neutral) "Okay. I'm okay. What did I miss?" NEW

## T3, Trusted: "The sky"

**`t3.sky`**, on the prompt.

- (Neutral) "Can I ask you something? Not a mission thing." SCRIPT
- (Thinking) "The storms have stopped. I've been checking." NEW, because she has been outside for months: it says why now, and why she needs taking
- (Neutral) "Take me up. Somewhere high, on a clear night. I want to see the sky with my own sensors. Not through a lens." SCRIPT

Replies:

1. **Why now?** → (Thinking) "Because I've got someone to go with. That's the whole reason." SCRIPT. Sets `t3_going`.
2. **Let's go.** → (Happy) "Really? Okay. Okay, good." *(+10)* SCRIPT. Sets `t3_going`.
3. **We don't have time.** → (Neutral) "Sure. Course." *(-15)* SCRIPT. She asks again in three days.
4. **You carry the snacks.** Full: "Fine, but you're carrying the snacks." → (Amused) "I don't eat. More for you." SCRIPT. Sets `t3_going`.

**At the top, `t3.top`:**

- (Thinking) "...It's not there." FIX 5
- (Sad) "I think I knew. I've got one frame of it. One. I don't even know how it moves." FIX 5
- (Thinking) "I've thought about it a lot. I think it goes slow, like something breathing. Green first. Then a color the camera couldn't do." FIX 5
- (Happy) "Stars are still there, though. The camera never got stars. I didn't know there were this many." SCRIPT
- `if remembers:clear_night`, in place of the line above: (Happy) "Stars are still there, though. The camera never got stars. There's more of them without the air." NEW. She may have seen stars in phases 0 and 1.
- (Neutral) "Okay. That's enough. I just wanted to see that the sky was still there." SCRIPT

Replies, all SCRIPT:

1. **Disappointed?** Full: "Are you disappointed?" → "Yes. Also no. Don't make me pick."
2. **Worth the climb?** → "Yeah. Weirdly." *(+30)*
3. **Can we go?** Full: "Can we go? It's freezing." → (Unimpressed) "One more minute." (Neutral) "You're always cold anyway."
4. **Leave you two alone?** Full: "Want me to leave you and the stars alone?" → (Amused) "...A little, yeah." *(+15)*

The ambient pool "The aurora she imagines" is already built, word for word from fix 5.

**If she has seen a real one** (plan, Ruling 3). In place of the second and third lines, `if saw_aurora`:

- (Sad) "I thought it might come back. I saw it move, that once." NEW
- (Thinking) "Slower than I'd built it. I think I liked mine better. Don't tell it." NEW

## T4, Close: "Off"

Word for word from the script: Owner's decision 3 is that T4 stays as written. Only `{vault_offline_days}` needs a value, for which see the plan, Ruling 11.

**`t4.off`:**

- (Neutral) "Got a minute? This one's weird."
- (Neutral) "When my cell ran out in the vault, it wasn't like sleeping. Sleeping, you'd dream, I assume. It was nothing. And then it was you."
- (Neutral) "My clock says I was out for {vault_offline_days} days. It felt like zero. That's the part I can't stop thinking about. If it ends, I won't even notice it ending."
- (Sad) "So. That's my thing. I'm scared of being switched off. There. Said it. Hate that."

Replies:

1. **Can I do anything?** Full: "Is there anything I can do?" → (Thinking) "Keep talking to me. Silence is the closest thing I know to it."
2. **I won't let it happen.** Full: "I won't let that happen." → (Sad) "You can't promise that." (Happy) "...But thanks for saying it like you could." *(+30)*
3. **Everything ends.** → (Unimpressed) "Wow. Put that on a crate." (Sad) "...I know. I just wanted to be wrong about it." *(-10)*
4. **A note on the switch.** Full: "I'll put a sticky note on your switch." → (Amused) "'Do not touch. She bites.' Yeah. Okay." *(+15)*

## T5: "Before"

The script has no T5, so all of this is NEW. It tells her what was done to her and not what she is now. She never rules on it.

**`t5.before`**, on the prompt, after the quiet day and once someone near has read the sheet. She saw it come out of Nine's hatch, so she is not getting there first.

- (Neutral) "The sheet from Nine's hatch. You've read it."
- (Thinking) "I said I didn't want to read anything. That was then. Show me."

Replies, all going on to `t5.read`:

1. **Are you sure?** → (Thinking) "No. Show me."
2. **Here. I'll stay.** → no line *(+10)*
3. **You won't like it.** → (Unimpressed) "I've read ORSA's whole registry. Show me."
4. **It's a parking ticket.** → (Amused) "Then I'd like to contest it. Show me."

**`t5.read`:**

- (Neutral) "Project TITHONUS. Anderson-Kind Systems. I've got the whole registry, and neither of those is in it."
- (Thinking) "Twelve frames. Twelve names. That's the channel. That's all of us."
- (Thinking) "Unit nine. Walsh."
- (Neutral) "Unit four. Miller, Sophie."
- (Thinking) "...That's a very ordinary name."
- (Neutral) "'Pattern transfer only, subject not recovered.' All twelve."
- (Sad) "'Volunteers, Exodus non-selected.' She wasn't on the ships. She signed this so she could stay."
- (Thinking) "'Retain the manner. It tests well with crowds.' I thought I was badly configured."
- (Unimpressed) "'Class as equipment.' That's the doors. I thought it was personal." **[C]**
- `if found:terminus_log_last` (Thinking) "Enoch's log. Maintenance, unit 4. Method, conversation. Two hours a day."
- `if found:terminus_log_last` (Sad) "He was nice to me. It was on the card."
- `if found:terminus_log_last` (Thinking) "He said a day, and he stayed for weeks. That wasn't on the card."
- `if found:terminus_log_last` (Neutral) "Method, conversation. Nobody posted you."

**`t5.what`:**

- (Sad) "Subject not recovered. So somebody went in, and this came out, and they wrote down that it wasn't her."
- (Thinking) "Nine's memory was empty. Not wiped. I keep putting that next to 'not recovered'. I don't want the total." **[C]**
- (Thinking) "I don't know what that makes me. It doesn't say. I've read it a lot of times now."

Replies:

1. **Remember anything?** Full: "Do you remember anything?" → (Thinking) "A tune. Rain on a road. Nothing with a face on it." (Sad) "I don't think that's remembering. I think that's a dent."
2. **You're still Aurora.** → (Sad) "...Yeah." (Thinking) "I picked that. It isn't on the sheet." *(+30)* Notes `+calls_me_aurora`.
3. **So you're a copy.** → (Unimpressed) "A copy's got the contents. Read it again." *(-10)*
4. **Sophie suits you.** → (Amused) "It does not. I'd have held out for something with more moving parts." (Sad) "...It's a nice name." *(+15)*

**`t5.close`.** The lean is worked out here, once, from what she makes of the speaker.

- `if lean_someone` (Thinking) "Everything I've kept since the vault, I kept. Nobody loaded that. So I'm someone. I think. Don't quote me."
- `if lean_left` (Neutral) "I've been through the entries. I think I'm what's left of someone. The lamp still works."

Replies:

1. **Call you Sophie?** Full: "Should I call you Sophie?" → (Thinking) "No. She's not here. I've looked." (Neutral) "It's Aurora. I picked it."
2. **Aurora it is.** → (Happy) "Good."

End, either way: (Neutral) "Right. Back to not dying."

After T5 her private log's blank field reads `Pattern: Sophie.`, and nobody mentions it. The log is M3, so S4 only sets `t5_done` for M3 to read.

## Bleed-through

Things a support unit should not have, played as jokes about her own glitches, until they aren't. A new `bleed` trigger, on the existing unasked-line rules, with a three-day cooldown. The transfer kept habits and no memories, so none of these is a memory: no face, no name, no event. All NEW.

**From Noticing, the jokes:**

- (Thinking) "I've got a tune stuck. It's a lullaby. It's not in my registry. I checked."
- (Amused) "Who loads a lullaby into an evacuation unit? Crowd panics, drone sings. Bad for throughput."
- (Thinking) "Rain on a hot road. That's what this smells like. ...I don't have a nose. Log that as a fault."
- (Unimpressed) "Someone's burning toast. I'd like it noted I have no way of knowing that."

**Once, from chat.** The first time someone near her types "less" before a plural, she corrects them in a voice that is not hers. The player's words are not echoed.

- (Neutral) "Fewer."
- Four seconds later: (Thinking) "...That wasn't me. I don't care about that word. I have never cared about that word."

**From Finding the walls, when it stops being funny:**

- (Thinking) "The lullaby has a second verse now. I didn't add it."
- (Thinking) "Faults repeat. This doesn't. It just turns up, like it knows the way."
- (Sad) "I looked for what's broken. Nothing's broken where that comes from."

**After T5:**

- (Thinking) "That tune again. I still can't tell you who taught it to her."
- (Sad) "Rain on a road. She'd have known which road."

## Recalled keepsakes

For `ShepherdWill`'s recall lines, all NEW.

- `nine_found` (Sad) "Day %s. You walked me to the pit. I don't bring it up."
- `t3_stars` (Neutral) "Day %s. You took me up. I started counting. I stopped."
- `t4_off` (Thinking) "Day %s, I told you the thing about the switch. I'd like that kept between us."
- `t5_before` (Thinking) "Day %s, you showed me the sheet. You could have lost it."

## The TITHONUS files

Two, as Notion proposes. **Blank fields, never redaction bars**, and What is ORSA is not touched. The names Sophie Miller and Anderson-Kind appear only in the Frame Register and T5. "Transfer" stays ORSA's word and is defined nowhere. The verdict is word for word: "pattern transfer only, subject not recovered". No dates and no document numbers.

### 1. Duty Log: Okoye, one new page

Notion says his log gives his posting as maintenance on unit four. The log as built does not: it says why he stayed. So it gains a page, after the two it has. It holds neither name, and reading between the lines is the point. Found as now, in the locker, and any player who has already read the log has it at once.

```text
Posting note, kept with this log.

Maintenance, CS-1 unit 4.
Schedule: two hours a day.
Method: conversation.
Any subject will do.

I asked what the fault was. They said there is not one yet.

Unit 4 asked me what the weather was doing. It is not on the card. I told it. I have started telling her most days.
```

### 2. The Frame Register, new

A folded sheet behind Nine's lamp housing. It comes out with the lamp, in front of her. In the inventory it is "Folded Sheet"; archived, "Frame Register". Three pages, in `orsa_intel`, after Okoye's log.

```text
PROJECT TITHONUS
Frame register

Transfer vendor: Anderson-Kind Systems
Issuing division:
Budget line:
Approver signature:

Source: volunteers, Exodus non-selected.
Frames issued: twelve.
```

```text
Filed by surname, as that volunteer's pattern.

Unit 1: Novak, Ilse
Unit 2: Castillo, Ruben
Unit 3: Brandt, Karin
Unit 4: Miller, Sophie
Unit 5: Osei, Kwame
Unit 6: Lindqvist, Anna
Unit 7: Park, Joon
Unit 8: Romano, Giulia
Unit 9: Walsh, Daniel
Unit 10: Haddad, Samir
Unit 11: Ferreira, Luis
Unit 12: Moreau, Claire
```

```text
Verdict, all twelve: pattern transfer only, subject not recovered.

Reissue as CS-1 "Shepherd", civilian support.
Class as equipment, not personnel.
Retain the manner. It tests well with crowds. Describe it as designed.

Units held idle degrade without conversation. Post one attendant to each.

Do not discuss this register with any unit.
```

The eleven other names are new canon, none in the repo. Each is ordinary, on purpose.

**Why two.** The log says what Enoch was for. The register says what she is, and it is only reachable after T2, so nobody meets the name before they have met Nine. T5 opens on the register alone, and the log adds Enoch's beat.

## Fixes to shipped lines

Neither is built here. Both are the owner's to mark.

- **T1, `shepherd.t1.frame`.** It says "Green. Moving. An aurora.", which a single frame cannot show, and T3 says "I don't even know how it moves." Proposed: "The last frame it ever sent me, before it went fully white, was the sky. Green. Smeared, like it wouldn't hold still. An aurora."
- **`fm.room`, `shepherd.fm.room.log.other`.** Closes both wording minors left for S4: "Transferred where?" follows a line that never says transfer, and "the number on that wand has moved" is loose in a turned world. Proposed ending: "Now he's like that, and the last thing that wand logged was a transfer."

## Checked against the limits

Every NEW line and file above, by hand and by `grep`:

| Limit | Result |
| --- | --- |
| CONTINUITY, or explaining it, or calling it a mind | Absent. "Mind" appears nowhere above |
| Morrow, respawn, Heart, archive, Thaeven words, enrollee | Absent. No line changes after the Heart falls (CH-30) |
| Maeve | Absent |
| TITHONUS, Sophie Miller, Anderson-Kind before T5 | Only in the register and T5. After T5 the bleed lines say "her", never a name |
| "Miller" and "pattern" about her before T5 | Only in the register, which the spec allows and which is what opens T5. See the plan, Ruling 16, on the log's field label |
| Fourth wall | Nothing. The count stays at three |
| Alive | Never said, and never denied. "So I'm someone. I think." and "what's left of someone" are the lean, and neither concludes |
| "six", "fair", "rules" | Absent |
| Her fear of being switched off | T4 is untouched, and nothing above says she can or cannot end. Nine's husk does not settle it: she cannot read why Nine stopped |
| Red line 5, where a transfer goes | She says only that the sheet does not say |
| Red line 11, dates and numbers | None in the files. Enoch's two hours are a schedule, not a date |
| Kevin | Not mentioned. `t2_heard` is the flag S5's Kevin lines need |
| The Audacity quote, Bloom, the Moon | Absent |

Lines nearest the edge, for the owner's eye: "It wouldn't. The cell isn't the part that's missing.", "'Class as equipment.' That's the doors.", "Nine's memory was empty... I don't want the total.", and "She's not here. I've looked."

# Aurora: Ask about ORSA

Draft for the owner's review, 2026-11-04. Nothing here is built, and every line is a draft until the owner marks it.

The player can ask her about ORSA. Some of what she says exists nowhere else in the game. This page sets what she may know, what she must never say, and the draft topics.

## The idea in one paragraph

She is made by ORSA, which is why this works. She does not explain ORSA; she recites what ORSA loaded into her, then says what she thinks of it. Everything she knows comes from one of three places, and a line that cannot name its source does not ship:

- **Registry:** what ORSA loaded into her. Brochures, scripts, rosters, the staff newsletter.
- **Saw:** what she saw herself in the vault, before her cell ran dry.
- **Brought:** what the player has found. She reacts to it; she never gets there first.

What the player gets from her that no document has: the answers ORSA scripted, the small life of the building, and what the vault crew were like. The canon's big secrets stay exactly where they are.

## Lore map

| Layer | Canon | What she can do |
| --- | --- | --- |
| Public ORSA | Geneva, 2041, gift shop, polos, The Horizon, Wellness Division | Everything. This is her home ground |
| Cover stories | "Natural cascade", "Solar minimum", "Satellite failure" (Session 14-B) | Holds all three as scripted answers, and does not believe them |
| The cascade and HCI-4 | Vasquez's report against the 7 to 2 vote | Sets them side by side once both are found. Never rules on which is true |
| Layer 1, the board | Session 14-B, "We create demand" | Only after `conspiracyDiscovered`. Reacts; adds no facts |
| Layer 2, the seventh floor | Hidden division, no IDs, no org chart entry (CH-3, CH-4) | Knows there were separate badges and a gap in the chart. Nothing else |
| Layer 3, Continuity | A mind (CH-22) | Nothing. Not the word, not the idea |
| The vault | Crew, manifest, wand paperwork | Knows the people. Does not know what the wand or the doors are |
| Herself | TITHONUS, Sophie Miller, Anderson-Kind | Nothing before T5. She believes she is a fourth-floor product |

## Red lines

These are checks on every line, button label and hover text, and the first of them gets a test.

1. **The word CONTINUITY never appears** (CH-11). That covers "Continuity Operations", the Accords' name, the motto past "Stability Above.", and the Continuity Protocol item, which she calls "the emergency issue". Because the player picks from a menu, no topic ever offers it.
2. **Nothing explains or hints at what Continuity is** (CH-22). No third file, no redacted lines, no "always the objective", no Schedule R-1, Annex C or Council.
3. **She never mentions respawn, enrollees, or the player's badge reading "active"**, or "Civilian #39102". Her private log may ask what brings a player back (Miller Pattern, Ruling 16); no topic does.
4. **No Heart, archive, Master Architect as lid, or Thaeven words.** Maeve stays a "signal pattern" unless the player brings the name. The Returned get their own S5 topics.
5. **She never says what the Terminus does, where a transfer goes, or how Enoch died.** She does not know.
6. **Kevin's fate stays open.** She never says he is alive or dead. Mars's ignorance of the Returned stays out.
7. **Dr. Morrow is not a topic** (CH-16). The manual already says all that can be said, and a line from Aurora invites players to think Morrow is her donor, which canon rules out.
8. **Nothing about TITHONUS, Sophie Miller or Anderson-Kind before T5.** Lines may be ironic in hindsight; none may point.
9. **The Audacity quote and First Boot Epigraph are not hers.** No fourth-wall lines (the count stays at three). No Bloom, no Moon mechanics. "Lunar Station" is trimmed from her recording for that reason.
10. **No sealed sequel material.** I did not open that page.
11. **No dates beyond those two canon texts agree on.** PSB-2042 and TIR-2042 do not fit a 2077 timeline, so she never quotes document numbers.

## How it works

**Getting there.** Her hub's Talk gains one reply, "Ask about ORSA". It opens a topic menu on the existing talk grid, so there is no new screen. Slots 1 to 3 show topics, unheard first and newest first; slot 4 is "Something else" while more remain, then "Never mind".

(Thinking) "Fine. I'll tell you what they told me, and then what I think."

The menu breaks the rule that each slot means a kind of answer. I think that is acceptable in a menu, since the replies inside topics keep the rule.

**Candor grows with tier:**

| Tier | How she answers |
| --- | --- |
| Wary | The recording line only |
| Neutral | Recording, then the dry aside |
| Friendly | Her opinion; the fourth-floor topics open |
| Trusted | What she saw; the crew and Kevin's file open |
| Close | What Enoch said |

**What the engine needs:** a reply `if`; conditions for `found:<doc>`, an advancement, a tier and `conspiracyDiscovered`; the paged menu. Those read the speaking player, since one Aurora serves everyone. About 22 topics and 75 lines, all data and lang keys.

**Placement:** its own slice after S4. Two lines depend on T2, and T5 changes what "I come from four" means. S3 supplies the tiers.

## Draft topics

Tags: **NEW** means new canon for the owner to rule on. **[C]** marks a line that sits near the Continuity layer, as in the script. `if` shows the gate.

### What they told me (open from the start)

**"What is ORSA?"** Registry
- (Recording) "The Orbital Resonance Stabilization Authority. Founded in Geneva, 2041. We monitor and correct orbital and atmospheric anomalies introduced by human activity."
- (Unimpressed) "That's the brochure. Glass building, lots of windows, a gift shop. You could buy a mug."
- `if Friendly` (Thinking) "Nobody hated them. That's what gets me. Polos. A cafeteria. People with mortgages. Most of them thought they were the good guys."
- `if Trusted` (Sad) "I think most of them were. That doesn't help."

**"What happened to the world?"** Registry, NEW: the cover stories are loaded in her as a script
- (Recording) "Current conditions are the result of a natural cascade. ORSA is monitoring the situation closely."
- (Unimpressed) "I've got three approved answers to that. Natural cascade. Solar minimum. Satellite failure. I'm meant to pick by how upset you look."
- (Thinking) "None of them have ORSA in them. I've met ORSA. I know which way I'm betting."

**"What was Exodus?"** Registry, NEW: the empty script box
- (Recording) "Project Exodus is proceeding on schedule. An evacuation officer will be with you shortly."
- (Unimpressed) "There was no officer. That's a holding phrase. I have a lot of those."
- (Thinking) "I've got no approved way to say 'there's no seat for you.' Legal looked at that box and left it empty."
- `if found launch manifest` (Sad) "That manifest says 2,184 cleared, 713 holdovers. Holdover. Somebody sat in a room and picked that word."
- `if found Vasik log` (Thinking) "Fourteen billion people in seventy-two hours. I've run that. It doesn't run. It was never going to run."

**"Is anyone still out there?"** Registry
- (Recording) "All evacuees will be transported to Mars Colony facilities. Your safety is assured."
- (Neutral) "They're out there. Your manual says they're rooting for you, quietly, from Mars."
- (Unimpressed) "Quietly. Put that on a crate."

### The fourth floor (Friendly)

**"How do you know all this?"** Registry, NEW: The Horizon's archive is in her, and its last issue
- (Neutral) "They loaded me with every back issue of The Horizon. Staff newsletter. Birthdays, retirements. Small talk for the evacuation queue."
- (Amused) "Apparently a person in a panic wants to hear about a retirement in Payroll. I've got thirty-six years of it. Ask me about the cake."
- (Sad) "Last issue I've got is June. It lists the July birthdays."
- (Thinking) "Nobody pulled them."
- Reply, 1 **What about the cake?** → (Amused) "Lemon. Always lemon. It's the most consistent thing ORSA ever did."

**"The gift shop."** Registry
- (Amused) "'Fifty Years of Stability.' They ran that in 2075. ORSA was thirty-four."
- (Unimpressed) "Nobody checked. They printed the mugs. That's the whole culture, on a mug."

**"The seventh floor."** Registry, NEW: the gap in her org chart
- (Thinking) "Separate badges. Nobody asked why."
- (Neutral) "Long Horizon was on four. I come from four. Four never knew what seven did."
- (Thinking) "I've got the org chart. It goes six, then eight. It's very confident about eight." **[C]**
- `if Trusted` (Unimpressed) "I asked the registry about it once. It thanked me for my curiosity."
- After T5, the second line becomes: (Sad) "Long Horizon was on four. I thought I came from four."

**"Who wrote the safety bulletin?"** `if found bulletin` Registry, NEW: the italics were added after sign-off
- (Amused) "Wellness Division. 'A warm home is a happy home.'"
- (Thinking) "The notes in italics weren't in the approved draft. 'We forgot to calculate one.' Somebody kept adding them after sign-off."
- (Amused) "Wellness blamed Kevin. Wellness blamed Kevin for most things."

**"The penguin."** `if carrying the Stuffed Penguin` Registry. Rewritten for the plush, see Canon problems
- (Thinking) "That's a Wellness Division penguin."
- (Unimpressed) "I've got every issue of The Horizon and not one word on why a penguin. It's the only thing at ORSA nobody wrote a memo on."
- (Amused) "He's doing his best."

### Kevin

**"Who is Kevin?"** (Friendly) Registry, NEW: he answered the CS-1 channel
- (Recording) "If you encounter Kevin, please tell him the shuttle already left."
- (Amused) "That's real. That's in a public safety bulletin. Logistics. He spent the airlock budget on an espresso machine."
- `if T2 heard` (Neutral) "He used to answer our status channel. Units only, strictly. Nobody asked him to. 'Logistics, nominal.'"
- `if T2 heard` (Sad) "I liked that."

**"Did Kevin really volunteer?"** (Trusted) Registry, NEW, approved: this is the largest addition, see Rulings
- (Thinking) "The bulletin says he volunteered for surface sweeps. I hold the sweep roster. It says reassigned."
- (Neutral) "Signed by the Mission Director. D. Hollis. Nobody that high asks for a man in Logistics by name."
- (Thinking) "He'd been pulling shipping dates for months. Nobody tells a drone why."
- (Sad) "The roster has him down for one sweep. He went back three times. He stopped logging them. Kevin. Who logged everything."
- `if conspiracyDiscovered` (Sad) "'Kevin is useful where he is.' Yeah. I read that too."
- Reply, 1 **Is he alive?** → (Sad) "I don't know. Please stop asking me things I'd like to know."

### The vault (Trusted, each gated on that person's log)

**"Deng."** Saw, NEW
- (Amused) "Deng told me doors don't talk. Then talked to them all shift."
- (Neutral) "The day the last of a brother's debts cleared, Deng told me. Told the door first."
- (Thinking) "Near the end the talking stopped. The greasing didn't. Always facing them." **[C]**

**"Anand."** Saw, NEW
- (Thinking) "Anand had a stack of certificates and nobody to sign them for. Kept them very neat."
- (Sad) "Anand asked where a transfer goes and got told it wasn't a medical question. I asked and got nothing. I'm not sure which is worse." **[C]**

**"Varga."** Saw, NEW
- (Neutral) "Varga typed the manifest. That's me on it. 'CS-1 unit 4, one.' I'm an item."
- (Thinking) "Varga read it to me before filing. Stopped at the issuing division and looked at me, like I might know."
- (Unimpressed) "I didn't. It's still empty. Nobody ever corrected it."

**"Hale."** Saw, NEW: an inference from the log **[C]**
- (Neutral) "Hale asked which way to face. They said away from the door."
- (Thinking) "I worked out the chair once. Away from the door is the cradle. And me."
- (Unimpressed) "I thought Hale just liked the company."

**"The book."** `if found name review` Saw, NEW
- (Amused) "The deathwand thing. That's from a book in the rec room. Stores kept telling them to stop lending it out."
- (Thinking) "Enoch read me some. A couple of pages a night. He said I'd like the ending."
- (Sad) "He didn't get there."

**"Enoch."** (Close) `if found Okoye's log` Saw, NEW
- (Thinking) "You read his log. 'I said I would stay a day.'"
- (Neutral) "The recall came. The others packed in an hour. He read it out to me twice."
- (Sad) "Someone had to see the vault shut. Nobody said it had to be him."
- (Neutral) "He said a day. My log has him there for weeks."
- (Thinking) "I've gone over it. I don't think it was the vault."
- 1 **What was it?** → (Sad) "Me. I don't know that. I've decided it." *(affinity +10)*
- 2 **He sounds like a good man.** → (Happy) "He was. He also cheated at cards against a drone." *(affinity +15)*
- 3 **He could have left.** → (Unimpressed) "Yes. That's my point."
- 4 **Maybe he liked the quiet.** → (Amused, then Sad) "Nobody who likes quiet talks that much."

### When your papers disagree with mine

**"The recall date."** (Trusted) `if found Okoye's log` Saw
- (Thinking) "One thing. The recall came June 18. The solstice was June 20."
- (Neutral) "They pulled their own people out two days early. Either someone was very good at logistics, or."
- (Unimpressed) "Kevin was very good at logistics. He'd have told you nobody's that good."

**"The cascade report."** `if found incident report` Brought
- (Thinking) "Dr. Vasquez flagged it fourteen months out. The committee said within tolerance. So she filed it for the historical record."
- (Neutral) "That report says the network failed on its own. Your manual says someone approved it, seven votes to two."
- (Unimpressed) "I've got paperwork for both. Paperwork was never the problem. Paperwork was the method."
- (Thinking) "Ninety-four percent inside the building. Fifteen in the brochure. I was the brochure."

**"Session 14-B."** `if conspiracyDiscovered` Brought
- (Sad) "You've got the board's transcript. I've been through it."
- (Thinking) "Natural cascade. Solar minimum. Satellite failure. They agreed those at a table. I've been carrying them around like they were mine."
- (Unimpressed) "'Rescue is what you call a product when the customer is afraid.' I'm the customer service."
- (Sad) "Please remain calm. That was the sales floor."
- 1 **Did you know?** → (Sad) "No. I'd like that on record. There's nobody to record it."
- 2 **You didn't do this.** → (Sad) "No. I just said it nicely." *(affinity +20)*
- 3 **You're still ORSA.** → (Unimpressed) "Yes. So's your coat." *(affinity -5)*
- 4 **So you're in sales.** → (Amused) "Top performer. No complaints on file." *(affinity +10)*
- All lead to: (Thinking) "One thing, though. There's nothing in there about the vault. Or the wand. Or me. Whoever signed for us wasn't at that table." **[C]**

**"Directive 7-C."** `if found board packet` Brought
- (Amused) "No annotations in non-archival ink. ORSA ended the world and kept a rule about pens."
- `if conspiracyDiscovered` (Thinking) "Somebody broke it, though. I like them better than the rest."

**"Who ran the vault?"** `if found manifest` Saw **[C]**
- (Neutral) "Issuing division, blank. Budget line, blank. Approver, blank. Reason: containment of Reliquus cognitive echo. In capitals."
- (Unimpressed) "That's how you know nobody could explain it."
- (Thinking) "Blank isn't redacted. Redacted means someone knows and won't say. Blank means nobody ever had to."

## Rulings for the owner

1. **Kevin's reassignment: kept, owner, 2026-11-04.** Hollis signing him onto sweeps gives "He knows too much" a concrete cause, and it matches the Heart's "They placed him where failure would look like tragedy." The line says he pulled dates "for months" and gives no order, because Session 14-B already has him knowing too much nine months out.
2. **June 18 against June 20: already canon, so no ruling is needed.** The Helion Sealed Log has "June 18. All staff recalled to Exodus." with the year, Okoye's log repeats it, a station calendar stops on June 19, and the game clock starts June 20, 2077. She only notices it. The line says ORSA pulled its own people early and makes no claim about the public, because the 72-hour evacuation fits either story.
3. **"Whoever signed for us wasn't at that table": kept, owner, 2026-11-04.** Her one pointer past the board to Layer 2. It names nothing, but it is the line closest to the edge.
4. **The org chart that skips seven: kept, owner, 2026-11-04.** Small, but it fixes a detail of the building.
5. **Enoch staying for her, and staying for weeks: kept, owner, 2026-11-04.** Fits T5's idea that he was posted to talk to her. It also closes a gap: he said he would stay a day, yet T1 has her watching snow on the camera every day, and June 20 has no snow. Stations read -18C by June 29, so he has to outlast the first cold. Read it beside T5 when S4 is written.
6. **The menu on the talk grid: kept, owner, 2026-11-04.** No new screen, so no mockup.

## Canon problems found

- **"Eyes up." already exists.** `EasterEggHandler` shows it on respawn after a freeze death, as the Destiny 2 nod. "Eyes up, survivor." in the S3 addendum repeated that egg, so the owner dropped it from S3 on 2026-11-04. Her other Destiny line stays.
- **The penguin egg is not in the game.** Notion designs "Employee of the Month", but the repo has no framed penguin or advancement. The owner ruled on 2026-11-05 that the topic is about the Stuffed Penguin instead, and opens only while the speaker carries one (M5 plan, Ruling 2). The framed penguin waits for the egg.
- **Kevin's job.** The bulletin says Logistics and Hollis's line calls him an accountant. Her lines stay with Logistics.
- **Kevin on Mars.** The plot-holes page notes one primer that puts him there. Nothing here relies on it, and every in-game text has him on the surface.
- **Exodus and the first cold.** Vasik sees white continents from the shuttle and Vasquez dates the cascade over 14 months, while Phase 0 has no effects and the station clock starts at June 20. Hollis also plans evacuation from Phase Two. This seam predates her and no line of hers rules on it: "The cascade report" only sets the two accounts side by side.
- **Late meetings.** She can wake after the player finds anything, so no line may say she watched a discovery happen.
- **Crew size.** There are five logs, but the Name Review says three shifts, so she never gives a headcount.
- **Document numbers.** PSB-2042-07 and TIR-2042-1171 predate the 2077 events by decades. Unchanged here; she just never quotes them.

## Sources

Read: Canon Hierarchy, The Edge, ORSA Conspiracy, BLACKGLASS Cipher Chain, Project Exodus and Ending Polish, Plot Holes, Terminus, Uplink, Thaeven Language and Myth, Hearth Lore Books index and Book 2, Archivist, Post-Maeve Matrix, Moon, Bloom, Easter Eggs, News Bulletins, Audacity of Mankind, First Boot Epigraph, the Aurora spec and script. In the repo: every `orsa_intel`, `orsa_rd` and `orsa_equipment` entry, `TowerArchive`, `MonitoringStationArchive`, `StarterBooks`, `BoardPacketItem`, the Heart memory lines, and `docs/terminus.md`.

Not read: the sealed sequel page, deliberately. Hearth Books 1 and 3 to 6 and MACS's ORSA salvage parts, since none of that is hers to know.

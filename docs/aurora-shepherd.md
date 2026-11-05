# Aurora, ORSA CS-1 "Shepherd"

Companion and dialogue system. Slice S1, creative only, merged as PR 149. Slice S2, in the vault, merged as PR 151. Slice S3, affinity, is built on `feat/aurora-affinity`; see Status. PR 147 (the Terminus Vault) merged on 2026-11-04 as b333ece9, so the vault is on `feat/maeve-director`.

Sources: Notion, Frozen Dawn / Backlog, "Aurora (ORSA CS-1 "Shepherd") — Companion and Dialogue System" and its child "Aurora — Dialogue Script v1" (both 2026-11-04). Those pages hold the character and every line, and carry the fixes below as of 2026-11-04. This file is the tracked handoff: what was settled against the built game, the art, and the slices.

Every line of dialogue is a draft until the owner marks it.

## What she is

Aurora is a talking ORSA drone the player can befriend. ORSA built her as CS-1 "Shepherd", a civilian evacuation assistant for Project Exodus, unit four of twelve in the region. She thinks "Shepherd" is a stupid name and calls herself Aurora. She has feelings, wants and a dry sense of humor. She cannot be destroyed.

The player finds her dead in her dock in the Terminus Vault, beside the technician who used to talk to her, and wakes her with a Power Cell. From then on she follows, lights the way, warms the player a little, and calls out what her sensors see. Conversation works like Fallout 4: she speaks, you pick one of four replies, she reacts, and a hidden affinity score opens personal conversations over time.

What the player can learn, if they find the files, is that she was a person. See Origin below.

### Goals

- A character the player wants to keep near them. Her humor carries most of this, and the short ambient lines carry most of the humor.
- Dialogue that lives in data files, so lines can be written without touching Java.
- Real help: every weakness callout is the true counter for that mob, and she only reports what her own sensors saw.

### Not goals

- She is not a narrator and does not explain the world's mysteries. She has opinions about them.
- She does not replace the Archivist as the quiet non-hostile presence.
- No voice acting. Her voice is typed text with a synthesized blip.
- No fourth-wall moment. The count stays at three (The Edge, section 13.8).

### What would make this a failure

- She names or explains Continuity, or anything she says reads as ORSA knowing what the seventh floor did.
- A line states something the game contradicts. The eight fixes below all came from that.
- Her moods cannot be told apart at play distance. Faces are the only affinity feedback.

## Owner's decisions

From Notion, locked 2026-11-04:

- Physical bot. Female. Feelings and wants. Two points for eyes and a moving mouth. Clickable dialogue buttons.
- Fixed name Aurora, chosen by her. Dry humor. Cannot be destroyed. Dialogue modeled on Fallout 4.
- Woken by installing a Power Cell. Found in the Terminus Vault, in the room with the TR-0 Terminus, as its evacuation guide, left behind when the staff evacuated.
- Never comments on respawn.
- Affinity feedback is the face only. No "Aurora liked that." text.
- One Aurora per world, shared. Working default: she follows whoever powered her on, and anyone can talk to her.
- Swearing is mild by default, heavy by a one-time choice at first power-on, changeable later from her hub.
- She feels guilt about the Returned. The other Shepherd unit in T2 is a husk. After Maeve is erased she goes quiet for a while.
- Raid theft by Maeve is deferred.
- Origin: she was a real person with a very ordinary name, she volunteered, her fear of being switched off stays unfounded and she can never know it, all twelve units are uploads, and it was a joint operation with a partner corporation.

From this design, 2026-11-04:

- Body: A, Lantern. Face, talk screen and dock approved "Build as drawn" from the mockups below.
- All eight fixes below accepted ("Yes, take them all").
- One charged Power Cell in the vault locker. This reverses the last rooms' "no new loot" for this one item.
- Her name was Sophie Miller. The partner corporation is Anderson-Kind Systems. Nine's site is the Blast Pit. All `[C]` lines are signed off.

## How the two Auroras relate

The sky effect and the character share a word on purpose. She named herself after the last frame her camera sent, an aurora, and the player will have seen the same lights in phases 0 to 3. So:

- **In the game**, both are "aurora". No text distinguishes them, and nobody explains the link until T1.
- **In the code**, the character is "Shepherd" everywhere: package `com.frozendawn.shepherd`, entity `frozendawn:cs1_unit`, block `frozendawn:cs1_dock`, data under `data/frozendawn/shepherd/`, lang keys `shepherd.*`, debug `/fd shepherd dump`, playtest `tools/prepare_shepherd_playtest.py`. `com.frozendawn.aurora` and `AuroraStatePayload` stay the sky. This replaces the Notion spec's `data/frozendawn/aurora/dialogue/` and `aurora.*` keys. It is the one place ORSA's name is the right one: the code is ORSA's paperwork.
- The entity's display name is "Aurora" once she has told you, which `fm.name` does on every path.

## Art

All at 16 texels to the block, in `docs/art/aurora-shepherd/`, each from its `make_*.py`.

| Mockup | Shows | Status |
| --- | --- | --- |
| `body-directions.png` | Lantern, Beacon, Stray | A, Lantern, picked 2026-11-04 |
| `face-moods-mockup.png` | Eight moods, mouth frames, blink, recording, at play size | Approved "Build as drawn" |
| `talk-screen-mockup.png` | Face, typed line, four slots, hover line | Approved "Build as drawn" |
| `dock-mockup.png` | Found, cell in, awake; vault plan | Approved "Build as drawn" |
| `flame-and-faces-mockup.png` | Pilot-blue flame under the heater port; Amused, Sad, Alarmed, Thinking redrawn | Approved: flame B, all four faces; Unimpressed and Happy stay as first drawn |
| `boot-and-idle-mockup.png` | First-boot ORSA logo on her display and talk screen; five idle expressions and the rare eye roll | Approved: logo B in her green, build all |
| `ambient-lines-mockup.png` | Ambient line, callout, "Got a minute?", recording voice during play | Approved "Build as drawn" |
| `boot-sequence-mockup.png` | The cell seated, Boot and Leave, BIOS lines, logo and first line, frame by frame | Approved 2026-11-04, for M2a; replaces `dock-mockup.png`'s cell-in state |
| `thinking-indicator-mockup.png` | Three indicators on her display, and how her eyes settle | Approved: A, Orbit, with her eyes going back into place |
| `private-log-mockup.png` | M3: Settings as a page, "Unit diagnostics", the log before and after T5, her lines on the first read | Approved 2026-11-05, with Settings as a page |
| `chat-bar-mockup.png` | M4: "Say something..." under the grid, typing, Orbit in place, the Strong, Close and None outcomes, the hub | Approved 2026-11-05, with "Neither." added to Close |
| `crash-mockup.png` | M4: the stop screen, three options on her display and two on the talk panel, the world frames, the logo and her lines after | Approved 2026-11-05: display B, talk panel A |

**Body.** About 12 texels wide, 10 tall and 10 deep, 18 wide across the ducts, with a carry handle on top. Front display 8 by 6 texels, lamp strip above it, heater grille underneath, a duct each side. She hovers about one block off the ground.

**Face.** Aurora green on black, the color of the sky effect. Every mood is lit texels on the same 8 by 6 grid, and the talk screen shows those same 48 texels large. Moods: Neutral, Amused, Unimpressed, Happy, Sad, Alarmed, Thinking, Offline. The mouth steps through shut, open and wide while text types and shuts on punctuation. The eyes blink idly. The recording voice has dimmed, fixed eyes and no mouth, and she falls back on it at the Wary tier.

**Dock.** One block, 16 texels wide, with her resting on it. Red light and empty slot when found; green light and the recording when the cell goes in; empty dock once she lifts off.

**Ambient lines.** A strip centered above the action bar, because the temperature and air panels hold the top left and status effects the top right. It uses the `OrsaHudPanel` colors with her face where the badge goes, a green edge, amber for a callout. Heard within 16 blocks. A line types out, holds 4 seconds and fades over 1 (my call). One at a time, and a callout replaces whatever is showing.

**Still owed, each before its slice:** Nine's husk, Nine's lamp, and the hub if it differs from the talk grid.

## The eight fixes

The script was written against the Terminus spec and predates the vault as built. Lines below are drafts.

1. **The technician is Enoch Okoye.** The duty log's "Okoye, A." becomes "Okoye, E." The name note's "Enoch" survives (taken, not killed; the Watchers). "Vale" goes: it collided with Chairman M. Vale of Session 14-B. The Keats line stays on the manifest.
2. **`fm.room`.** He is seated in the vault, not on the cot, which is the room before. Her last log becomes "him sitting right there, talking to me about nothing". His own log ends "I am going to lie down now", after her cell ran dry, so both hold. "The thing in the cradle says" gets a variant, "the thing in your hand says", because the player may already have the wand.
3. **`fm.doors`.** Nothing can stop for her on the way out. She remembers instead, triggered at the last door: (Thinking) "He'd look at a door and it stopped. I'd look at a door and it kept going. Apparently I don't count." The replies stand. It agrees with Okoye's "The doors stopped for me when the others left" and with `VaultWatch`.
4. **`fm.architect`.** The interrupt is cut. An Architect will not realistically follow the player that far. Only the "Before we go up" line stays, at the vault exit.
5. **T3 "The sky".** The owner assumes she will never see a real aurora, so she imagines one instead.
   - She has one frame and does not know how it moves. The lines at the top become: (Thinking) "...It's not there." (Sad) "I think I knew. I've got one frame of it. One. I don't even know how it moves." (Thinking) "I've thought about it a lot. I think it goes slow, like something breathing. Green first. Then a color the camera couldn't do." The stars lines follow unchanged.
   - A new ambient pool from phase 4 on, on clear nights: "I've decided it moves slowly. Nobody can correct me. That's the upside." / "In my version there's a sound. There wouldn't be. It's my version." / "One frame. You'd be amazed what you can build from one frame."
   - T3 and that pool start at phase 4, which is what keeps the assumption true: `AuroraPolicy` runs real auroras only in phases 0 to 3.
   - She needs a sky she can see, so T3 waits out phase 5 and early phase 6 (`PhaseManager.isBlizzardActive`) and any storm, and she asks again. Phase 6 mid onward draws stars on black.
6. **Late meetings.** She can be woken after anything has already happened, so every one-time topic gets a late variant, fired at the next calm moment. Maeve wakes: (Thinking) "There's something on the Reliquus band. All of them at once, like one ear. It was already doing that when you woke me." The Moon and post-Maeve silence need the same treatment. A topic whose moment passed and has no variant does not fire.
7. **Her presence in the vault.** Nothing there mentions her. Hale's "nothing down here but the cradle and us" gains the unit, the Vault Manifest gains an item row for CS-1 unit 4, and the locker gains the spare cell. All three are edits to vault files, and land in S2.
8. **Notion cleanup, done 2026-11-04.** T3 no longer ends on an aurora, and the personal conversations are five with T5. The Terminus note about "exactly two immunities" was stale, and the answer stands without it: she is on neither `terminus_susceptible` nor `terminus_immune`, so she is no watcher and the wand does nothing to her. The Mimic callout said it vanishes when you lose sight of it, which `MimicEntity` does not do; see Callouts.

## Waking her

The dock holds her as a block until she wakes. Right-clicking it with a Power Cell holding any charge (`PowerCellItem.energy` above zero) seats the cell, and the player chooses Boot or Leave on a black screen. Leave keeps her dark with the cell in the slot. Boot spends it, turns the light green and runs the BIOS lines, then the logo and `fm.boot`. An empty cell is refused with a dry click. A spent cell is gone: no charge bar and no upkeep, so she can never be switched off by neglect, which is what keeps T4 unfounded. A dock that goes dark gives its seated cell back. See "The boot sequence" in [aurora-miller-pattern.md](aurora-miller-pattern.md).

In the vault the dock stands at hall-relative (103, floor + 1, -3), beside the technician at (102, -3), facing south. The locker's spare is the cell he never got to fit, and a player can use their own instead. Two gold puzzle doors shut behind the player and open from the vault side, so anyone without a cell would otherwise redo both rooms at low health.

The vault is the only way to meet her, by the owner's decision. Its hiding place means some players never will, and most will meet her late, which is why fix 6 matters and why the pre-Maeve callouts may be heard less than the rest.

## The talk screen

Right-click opens her hub; the world keeps running behind it. Layout as the mockup: face on top, her line typed below it, four fixed slots as a 2 by 2 grid, keys 1 to 4 or click.

| Slot | Tone |
| --- | --- |
| 1 | Ask: a question, or tell me more |
| 2 | Kind: agree, warm, supportive |
| 3 | Blunt: decline, practical, cold |
| 4 | Sarcastic: joke back |

Each button shows its tone over a short label, and hovering shows the full line the player will say. Not every node fills all four.

The hub uses the same grid: Talk, Follow or Wait here, Lamp on or off, What do you see? Settings, which holds the swearing choice, is a small button beneath it.

My calls:

- Text types at 2 ticks a character, with a blip every 3. A click finishes the line; a second click moves on.
- The screen closes on Esc, on taking damage, on a hostile targeting the speaker within 12 blocks, or on the speaker moving more than 8 blocks from her.
- One conversation at a time. A second player gets her Unimpressed face and the screen does not open.
- She is silent in the world while the screen is open.
- A conversation she starts never opens by itself, except the first boot. She floats up, shows "Got a minute?" and waits for a right-click. `fm.doors` and the exit line work this way too, so S2 needs the on-screen form of her ambient lines.

## Dialogue data

Trees in `data/frozendawn/shepherd/dialogue/*.json`, text in lang keys, ambient pools in a separate file with trigger, cooldown, mood and variants. The node format is the Notion spec's, with keys under `shepherd.` and `lines` in place of one line, because a node can change mood partway:

```json
{
  "id": "fm.name",
  "lines": [{ "line": "shepherd.fm.name", "mood": "neutral" }],
  "responses": [
    { "slot": "ask", "label": "shepherd.fm.name.why.label", "full": "shepherd.fm.name.why.full", "next": "fm.name.why" },
    { "slot": "kind", "label": "shepherd.fm.name.call.label", "full": "shepherd.fm.name.call.full", "next": "fm.name.aurora", "affinity": 10 }
  ]
}
```

A node may also carry conditions (tier, flags, phase), a `set` list of flags, and a `heavy` line key for the swearing swap. The script has labels for most replies but full lines for few; writing those is part of each slice.

The server owns the conversation: it loads the trees with a reload listener (the mod has none yet), sends a node, and takes back only a slot number. The client holds no tree and cannot skip one.

## Affinity

Hidden score, -1000 to 1000, starting at 0. Wary below -250, Neutral, Friendly at 250, Trusted at 500, Close at 850. Dialogue moves it by about 5 to 25. Actions move it too, with a cooldown per action type. Likes and dislikes are as in Notion. A tier up triggers "Got a minute?" at the next calm moment. At Wary she does not leave: she gets quiet and formal and speaks in recordings.

One shared score per world (the working default in Notion). "Calm" means no hostile within 24 blocks, no damage in the last 30 seconds, and the player above the cold and O2 warning lines (my call).

## What she knows and when she speaks

**Knows:** her registry, which is fourth-floor ORSA paperwork; the status pings of the other units until they stopped; her own clock, which she distrusts; what her sensors see now.

**Does not know:** what the Terminus does, anything of the seventh floor, her own origin until the player finds the files, and that she cannot be destroyed. She does not read Maeve's mind: she reads bodies, heat and behavior.

**Speaks** in conversations, ambient lines with triggers and cooldowns, one-line reactions and callouts.

**Is silent** while the talk screen is open, for one in-game day after Nine's husk, for a while after Maeve is erased, and mostly at the Wary tier. She never mentions respawn.

## Conversations

Hub, five personal conversations, topic conversations from world events, ambient lines with triggers and cooldowns, one-line reactions, and weakness callouts. Personal arc: T1 Friendly "The name", T2 Friendly "The others", T3 Trusted "The sky", T4 Close "Off", T5 "Before", which needs the TITHONUS files. v1 writing target is 150 to 250 lines.

**Nine's site** is the Blast Pit. There is exactly one per world, it is guaranteed, and it is where Exodus actually left from, so `{nine_site}` always resolves. No new structure: the husk is placed at the pit when T2 starts or when that area next loads.

Callout rules stand as in the script: line of sight within about 32 blocks, or the behavior in front of her. First sighting gets the full line, later ones the short. Each line must match the mob's current counter, so each is checked against that mob's code in its slice.

The Mimic's, rewritten from `MimicEntity` and `MimicCombatBehavior`. It stands still and stares; a player within 16 blocks starts 5 seconds of copying their movement and facing, then it attacks hard. Left alone for 20 to 30 seconds it burrows away and is gone. Hit while staring, it goes straight to the fight. At half health it turns back to shadow and runs for the dark, then returns to staring. It takes no freezing damage, 50 percent extra from fire, and burns within 4 blocks of a heater. So:

- Full: "Mimic. It's standing still because it's watching you. Get close and it copies you for five seconds, then it stops being polite. Back off and it digs out. If you have to fight, fire."
- Copying: "It's got your walk. You have five seconds."
- Running: "It's gone for the dark. It's not done."
- Short: "Mimic. Keep your distance."

## Behavior

My calls, all the builder's to refine:

- **Entity.** `frozendawn:cs1_unit`, about 0.75 wide and 0.8 tall, flying. The repo has no flying mob yet, so `FlyingMoveControl` and `FlyingPathNavigation` come new. Never despawns.
- **Follow.** Hovers near the follower and teleports to them past 24 blocks in the same dimension. Stays if they change dimension or log out, and anyone may tell her to follow. Dying changes nothing and she says nothing about it.
- **Cannot be destroyed.** 40 health. Lethal damage of any kind, `/kill` and the void all send her Offline instead of `die()`: she drops, eyes off, ignored by mobs, and reboots after 2 minutes with a line. The void puts her back at her follower.
- **Lamp.** Reuses the moving-light ownership in `HeldLanternLights`.
- **Heater.** A small warmth near her, added where player temperature is worked out. Not a `HeaterRegistry` heater: Mimics and Hunters react to those.
- **State.** `ShepherdSavedData` on the overworld: dormant or awake, entity UUID, last position, follower, affinity, flags, nodes seen, swearing setting. The world's truth is here, not on the entity, so she survives anything that happens to the entity.
- **Maeve.** No MACS code or page changes while MACS is WIP. Sensor reports and Maeve noticing her are specced in Notion and go into MACS only with the owner's approval.

## Origin: Project TITHONUS

As in Notion. Short form: a seventh-floor project with Anderson-Kind Systems to upload the left-behind into frost-proof bodies. It failed: each unit got the shape of a person, voice and warmth, with no memories, and ORSA logged "pattern transfer only, subject not recovered". The survivors were rebadged as CS-1 "Shepherd", the warmth passed off as design, and they were classed as equipment, which is why the doors do not count her. Okoye was posted to the vault to talk to her, because the units degrade without conversation.

She knows none of this at first. Bleed-through comes first, played as jokes about her own glitches. T5 "Before" opens only if the player finds the files.

Her name was Sophie Miller, a very ordinary name on purpose. Anderson-Kind built the transfer process and the frames; the seventh floor supplied volunteers, unauditable funding and the cover-up. Both names appear only in the TITHONUS files and T5.

Design-only, never in the game: a machine cannot move a mind, because minds do not end and Continuity keeps them. That is why Nine's memory is empty, not wiped.

## Canon guardrails

- She is a fourth-floor ORSA product. She knows nothing of the seventh floor. She must not explain Continuity or call it a mind (CH-10, CH-22). No line, label or document of hers contains the word (CH-11); the motto line stops at "Stability Above."
- No new redactions in What is ORSA. TITHONUS documents use blank fields.
- The division stays unreachable (CH-17). CH-16 is untouched: her donor is not Morrow.
- Maeve is "Reliquus signal pattern" to her until the player brings the name.
- The `[C]` lines in the script are questions and observations only. The owner signed off all of them on 2026-11-04.
- Dressing and names are ours: Fallout 4 lends the shape of the dialogue only.

## Slices

Each is playable, on one branch per slice from `feat/maeve-director`, in a worktree at `/Volumes/SSD/Work/frozen-dawn-aurora-shepherd`. Cut from an up-to-date `origin/feat/maeve-director`.

| # | Slice | Needs first |
| --- | --- | --- |
| S1 | Engine, creative only | The owner's word |
| S2 | In the vault | S1 |
| S3 | Affinity | S1 |
| S4 | Personal arc, Nine's husk, TITHONUS files | S2, S3, husk and lamp mockups |
| S5 | Ambient lines, topics, callouts | S2, 100 or more lines |
| S6 | Maeve integration | Owner's approval to copy into MACS |

### S1: engine, creative only

Creative tab and `/give` only: no recipe, loot or world generation, and no change to the vault.

In: the dock and its three states; waking with a cell; the `cs1_unit` entity with follow, wait, hover and lamp; Offline and reboot; one Aurora per world, a second dock staying dark while she is awake; the hub and talk screen; moods, typing, blips; the JSON loader and server-driven conversation; saved state; the swearing choice; a first-meeting tree of `fm.boot`, `fm.filter`, `fm.name` and `fm.end`. Dialogue affinity values are stored and do nothing yet.

Out: `fm.room`, `fm.doors` and the exit line, which need the vault; tiers, reactions and face feedback; the heater's warmth, which comes with S3; ambient lines; callouts.

New sound: blips, a boot chime, the refused click. Each is judged by ear, so the owner hears candidates before they are final.

Playtest: `tools/prepare_shepherd_playtest.py`, one flat world, generated as AGENTS.md requires, with docks, spare cells and an empty cell in a chest.

### S2: in the vault

The dock at its place, sealed like the vault's other fittings and left as dressing once she is awake; the locker's spare cell; Okoye's initial, Hale's line and the manifest row; `fm.room` with its wand variant, `fm.doors` and the exit line; the "Got a minute?" prompt. `/fd terminus vault reset` puts the dock back but leaves her saved state alone. Check `VaultDocumentsTest` and `TerminusVaultGameTest` for anything that pins those texts or the locker's contents.

### S3: affinity

Built on `feat/aurora-affinity`, from [aurora-shepherd-s3-plan.md](aurora-shepherd-s3-plan.md) and its addendum [aurora-shepherd-s3-lively.md](aurora-shepherd-s3-lively.md).

Her score now means something. `ShepherdAffinity` holds the tiers and the table of deeds, and `ShepherdReactions.saw` is the one door a deed comes through: she must be awake, loaded, online and within 16 blocks of the player, and the deed's cooldown, kept in saved state, must have run. Then the score moves, her face reacts for three seconds, and the strip says her line. Nothing plays while the talk screen is open, though the score and cooldown still land. Every cooldown runs on game time, so sleeping does not shorten one.

At Wary she rests on the Recording face, Talk gives a formal recording, reactions are the grey recording strip, and she offers nothing personal. Her face settles every tick, so any road across the Wary line shows.

A tier-up is not an event. `ShepherdPrompt` looks at the score at a calm moment, with no hostile near, nobody near hurt lately, nobody near at -10 C or colder after armor, and nobody short of air, and raises the first personal conversation not yet asked. Only T1 "The name" exists so far. If a waiting T1 drops below Friendly, or a place prompt replaces it, it is taken back and asked again later. `fm.doors` and `fm.exit` keep S2's calm.

She floats up in front of her follower on the Thinking face for 100 ticks when the strip shows, going round their shoulder if she is behind them. She gives +5 C within 4 blocks, as a player adjustment: rooms do not warm, and she is no `HeaterRegistry` heater.

Closed from S2's deferred minors: Esc or a hit losing `fm.exit`; a bad `when` throwing on the right-click; nothing moving her when she asks; a hit on any player delaying the strip. Still deferred: the two wording minors for S4, `fm.room` playing away from him, and the `shows()` GameTest timeouts.

The addendum makes her lively between deeds. Reactions have variants and never repeat their last line. She watches a visible hostile within 16 blocks, flinches when someone near is hurt, and is glad when you come back after a minute. When her follower stands still for 15 seconds she drifts to look at a chest, heater, terminal or passive animal within 8 blocks, and comes straight back if they move or a hostile shows.

`ShepherdReactions.murmur` is the door for what she says unasked: idle commentary from `data/frozendawn/shepherd/ambient/`, each trigger with its own cooldown and a three-minute gap across all of them. Never before the first meeting is done, at Wary, over a conversation or over her asking, and only when calm. A reaction ignores the gap but restarts it. A fight line lapses after two minutes, and a player's death drops it, since she never mentions a respawn. From phase 4 she imagines the aurora at night under open sky, outside a blizzard: rain cannot be the test, since `WeatherHandler` locks storms on from phase 2 to phase 6 mid.

`ShepherdEggs` holds the nods to other games, the raw fish payoff for "Or what, Shepherd?", and the foreshadow by a Hearth. Once-only ones keep a flag, set only when the line played; daily ones a cooldown. The nods about trouble skip the calm test and the resupply code skips the gap too. The foreshadow needs only that nobody near is hunted or hurt: a Hearth's own residents would otherwise keep it from ever being calm. Bleed-through lines stay in S4.

Debug: `/fd shepherd affinity <value>`, `skipday`, `resetgap` and `hearthhere`. Playtest: `tools/prepare_aurora_affinity_playtest.py`.

## Verification

Tests cover behavior, not lines.

- JUnit: every tree parses; every `next` resolves; every key has a lang entry; no `shepherd.*` value contains "continuity" in any case; no node has two responses in a slot.
- GameTests: a charged cell wakes her and is spent; an empty cell is refused; a second dock stays dark; lethal damage, `/kill` and the void leave her Offline and she reboots; she is no `VaultWatch` watcher; a Terminus discharge leaves her untouched; saved state survives a reload; damage closes a conversation; a client cannot answer a node it was not sent.
- Then the full `architectVerify`, and `architectMonkey` if the slice touches Architect AI, which S1 to S5 should not.
- Owner's visual pass, per slice: mood legibility at play distance above all, then typing speed, the blip, how she hovers and follows.

## Open

Nothing until a slice starts.

## Plan

S1 is planned in [aurora-shepherd-plan.md](aurora-shepherd-plan.md), S2 in [aurora-shepherd-s2-plan.md](aurora-shepherd-s2-plan.md), S3 in [aurora-shepherd-s3-plan.md](aurora-shepherd-s3-plan.md) with the addendum [aurora-shepherd-s3-lively.md](aurora-shepherd-s3-lively.md).

## Status

2026-11-04: design written, all five mockups approved. PR 147 merged and the owner said to start.

2026-11-04: S1 built on `feat/aurora-shepherd` in `/Volumes/SSD/Work/frozen-dawn-aurora-shepherd`. `architectVerify` passed at 64c461c2, after the branch review's three fixes. Active save: `run-lab/saves/Shepherd Check`, from `tools/prepare_shepherd_playtest.py`. Branch review done on Opus. Waiting on the owner's visual pass; `architectMonkey` has not run and is due before any merge.

Draft for the owner to mark: `hub.talk`, the one line her hub's Talk has in S1, in a mild and a heavy form, and the two reply labels "Why Aurora?" and "Aurora it is." that the script's "If the player asks why" needs. The blip, boot chime and refuse click are auditions: `tools/generate_shepherd_sounds.sh` also writes three of each to `build/shepherd-sound-candidates`.

2026-11-04, after the first visual pass: the owner reported the first meeting and replay look good and asked for a flame under her heater port and livelier faces. Added the flicker that sputters out now and then (none Offline or on the dock), the four redrawn moods, idle motion in `ShepherdFace.motion` (random blinks, double blinks, side glances, per-mood habits), a three-tick eyes-shut beat when her mood changes, her eyes following the hovered reply, and her body display showing her mood, blink and moving mouth. Her mouth on the body runs on a fixed 80 ticks per line, since the server does not hold the text. Tests cover the motion limits and when her mouth stops; how it all looks still needs the owner's pass.

2026-11-04, the owner's pass on the flame and faces: "looks good". At 27948d03 `architectVerify` passed (973 tests). `architectMonkey` failed on one test outside this feature, `asecondpressplaysthetakeandtheprojectorshowsit`, which failed 2 of the 4 monkey runs on this branch and has not been run there on `feat/maeve-director`. `airlockrepairthenpumpkeepsbaseairandsource` failed the three `architectVerify` runs before this one on this branch and passed on director's one run, but the `airlock_native` batch fails by itself on director under `gameTestQuick`, with no Shepherd tests in the run: the core supplied air and the room never filled, and the oxygen system had counted 15 to 19 rooms instead of 1. Exact cause not found. Two Shepherd test faults this turned up are fixed: the dock test ended mid-boot and built a body in the next batch's world, and tests now wait for her chunk to tick. The whole-branch review predates the replay, flame and face commits.

2026-11-04, handback: a second branch review covered the replay, flame and face commits. It found one more dock test ending mid-boot, fixed in 7a46f6c5, where `architectVerify` passed. Deferred minors: her face and flame still draw while she is invisible; during the first boot line her body shows Unimpressed with a moving mouth while the talk screen shows Recording, which needs the client to report its line or an owner ruling; her eyes follow the cursor onto the hub's empty slot and continue-only lines; idle motion on the talk screen starts the same way every conversation.

2026-11-04, the owner's rulings on the deferred minors: her face and flame drawing while invisible stays deferred; her eyes following the cursor onto empty slots stays as it is, because the owner likes it. New on PR 149: on `fm.boot` her display and the talk screen show the ORSA logo for 64 ticks with the terminal's boot sound, in her own green, and the line waits for it. Her body now moves through a node's moods at the same 80 ticks per line, so it shows Recording during the recording line. On Neutral, while quiet, she shows five idle expressions and, about once in ten minutes, an eye roll. The talk screen's idle motion is seeded by the conversation. `/fd shepherd replay` also replays the logo, and walking away from `fm.boot` and coming back shows it again.

2026-11-04: the owner's pass on the boot logo and idle expressions: "looks good". `architectVerify` passed at a1ac8f32 (976 tests). S1 is ready for the owner's merge decision on PR 149; S2 waits for it.

2026-11-04: S1 merged as PR 149 (b82a0bc2). S2 built on `feat/aurora-vault` in `/Volumes/SSD/Work/frozen-dawn-aurora-vault`, from [aurora-shepherd-s2-plan.md](aurora-shepherd-s2-plan.md); the owner approved the plan and its ten rulings. The dock in the vault is `vault_cs1_dock`, the same dock under the vault's sealed properties, and waking on it sets `woke_in_vault`. Dialogue nodes can carry `when` and `else`, and lines `if`. `ShepherdPrompt` holds one waiting conversation in saved state, and `ShepherdPromptStrip` draws it. Calm in S2 is only no hostile within 24 blocks and no damage in 30 seconds. Active save: `run-lab/saves/Aurora Vault Check`, from `tools/prepare_aurora_vault_playtest.py`. Not yet seen by the owner, and the client has not been launched: how the dock and strip look, the prompt's rhythm and the draft lines all need the visual pass.

Branch review on Opus: one real finding, the dock missing from `wither_immune` and `dragon_immune`, fixed with a failing test first. Deferred minors for the owner: "Transferred where?" is still offered after the third log line, which never says transfer; Esc or a hit right after the right-click loses `fm.exit` for good, since it has no replies to resume on; `fm.room` plays wherever she is if the first meeting is resumed away from him; a hit on any player delays the strip, not just one near her; nothing moves her when she asks, though the spec says she floats up; "the number on that wand has moved" is loose in a turned world where the wand still reads 1; a bad `when` with no `else` in an edited datapack throws on the right-click, since only JUnit guards the shipped data; the `shows()` GameTests would time out if a mob escaped a neighbouring test.

2026-11-04: the owner's pass on S2 in `Aurora Vault Check`: "wow ok it worked great." The saved state after the pass shows her awake with `woke_in_vault`, `fm_done`, `doors_asked` and `exit_asked` set, and the client log has no errors. The owner reached the landing by its button, not on foot. The deferred minors above stand. S2 is ready for a PR into `feat/maeve-director`; `architectMonkey` has not run on it.

2026-11-04: S2 merged as PR 151 (8f8d49d2). S3 built on `feat/aurora-affinity` in `/Volumes/SSD/Work/frozen-dawn-aurora-affinity`, from [aurora-shepherd-s3-plan.md](aurora-shepherd-s3-plan.md); the owner approved the plan and its twelve rulings. Active save: `run-lab/saves/Aurora Affinity Check`, from `tools/prepare_aurora_affinity_playtest.py`. Not yet seen by the owner, and the client has not been launched: the float-up, the reaction and recording strips, the Wary hub, the warmth's size, the pace to Friendly and every new line need the visual pass. No GameTest reaches `AIR_FAILING`, because the suffocation stage has no seam. New drafts for the owner to mark: `hub.talk`'s Wary and warm lines, the two Wary recordings, "Oh good. You remembered where you put me.", "You never asked about the name. I'm telling you anyway.", and two short reply labels, "Just a light in the sky." and "A screensaver?", with the script's words on hover because its labels overrun the slot.

Found while building, not fixed: `gameTestQuick` sometimes hangs after "All N required tests passed", spinning in `ChunkMap.processUnloads` in `stopServer`. It happens without this slice's float-up code, on 2 of 5 runs of `shepherdthepromptstops`, `shepherdahostile` and `shepherdholdsstation` at 9663ced5. `shepherdHoldsStationBesideHerFollower` also timed out once running alone on a fresh server: its 200-tick timeout leaves little room after `whenTicking`.

2026-11-04, the addendum: Tasks 7A to 7E and the playtest additions are built. Not yet seen by the owner: also judge whether curiosity charms or gets in the way, how often she talks, the variants, the nods and whether the foreshadow lands. The lab has no real Hearth: `[Hearth wall]` plays the line, and which Hearth records count has no test and no pass yet. `CLEAR_NIGHT` keeps its no-rain test, so storms mean it only plays in phases 0 and 1 and from phase 6 mid.

2026-11-04, branch review on Opus: no critical findings. Fixed: a T1 closed early from its unasked opening, which played and scored twice and could not be withdrawn; a fight line after a death or long after the fight; the foreshadow held back by a Hearth's residents; unasked lines over her asking or before she has introduced herself; `[Skip a day]` now reaches the silence lines. For the owner: `CLEAR_NIGHT` being rain-only halves the passive rate to Friendly from about day 15, which the lab cannot show.

2026-11-04, the owner's visual pass on the Aurora Affinity Check: first meeting and nods "looks good"; at Friendly "she walked up to me and told me why she chose auroa"; Wary "gives me like a cold repsonse"; curiosity "she did, and then gave me a eyebrow rasie lol", and "the sheep thing worked"; "hut worked fine"; warmth "worked"; the blizzard "stayed silent"; how often she talks "seems ok, but i kept dying lol". The aurora, fight and swarm lines played. The zombies burned at noon before she noticed them, so the lab's now wear helmets.

2026-11-04: the inner state under her is built on `feat/miller-pattern`; see the Status of [aurora-miller-pattern.md](aurora-miller-pattern.md). Her resting face now shows what she feels instead of always Neutral, and her idles play on every resting mood. Affinity, tiers and `ShepherdSavedData` are unchanged.

2026-11-04: the owner approved the M2 plan, [miller-pattern-m2-plan.md](miller-pattern-m2-plan.md), which adds the boot sequence in slice M2a. Nothing is built.

2026-11-04: the boot sequence built in M2a on `feat/miller-m2`, with the seated dock and the thinking indicator. `architectVerify` passed at ffcc390a (1,154 GameTests). Active save: `run-lab/saves/Aurora Boot Check`. Headless only so far; the owner's pass is next. Details in the Status of [aurora-miller-pattern.md](aurora-miller-pattern.md).

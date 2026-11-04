# Aurora, ORSA CS-1 "Shepherd"

Companion and dialogue system. Design only: no game code exists, and none starts until the owner confirms PR 147 (the Terminus Vault) has merged.

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
- **In the code**, the character is "Shepherd" everywhere: package `com.frozendawn.shepherd`, entity `frozendawn:cs1_unit`, block `frozendawn:cs1_dock`, data under `data/frozendawn/shepherd/`, lang keys `shepherd.*`, debug `/fd debug shepherd`, playtest `tools/prepare_shepherd_playtest.py`. `com.frozendawn.aurora` and `AuroraStatePayload` stay the sky. This replaces the Notion spec's `data/frozendawn/aurora/dialogue/` and `aurora.*` keys. It is the one place ORSA's name is the right one: the code is ORSA's paperwork.
- The entity's display name is "Aurora" once she has told you, which `fm.name` does on every path.

## Art

All at 16 texels to the block, in `docs/art/aurora-shepherd/`, each from its `make_*.py`.

| Mockup | Shows | Status |
| --- | --- | --- |
| `body-directions.png` | Lantern, Beacon, Stray | A, Lantern, picked 2026-11-04 |
| `face-moods-mockup.png` | Eight moods, mouth frames, blink, recording, at play size | Approved "Build as drawn" |
| `talk-screen-mockup.png` | Face, typed line, four slots, hover line | Approved "Build as drawn" |
| `dock-mockup.png` | Found, cell in, awake; vault plan | Approved "Build as drawn" |
| `ambient-lines-mockup.png` | Ambient line, callout, "Got a minute?", recording voice during play | Waiting for the owner |

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
7. **Her presence in the vault.** Nothing there mentions her. Hale's "nothing down here but the cradle and us" gains the unit, the Vault Manifest gains an item row for CS-1 unit 4, and the locker gains the spare cell. All three are edits to vault files, so they wait for PR 147.
8. **Notion cleanup, done 2026-11-04.** T3 no longer ends on an aurora, and the personal conversations are five with T5. The Terminus note about "exactly two immunities" was stale, and the answer stands without it: she is on neither `terminus_susceptible` nor `terminus_immune`, so she is no watcher and the wand does nothing to her. The Mimic callout said it vanishes when you lose sight of it, which `MimicEntity` does not do; see Callouts.

## Waking her

The dock holds her as a block until she wakes. Right-clicking it with a Power Cell holding any charge (`PowerCellItem.energy` above zero) spends the cell, turns the light green and opens the talk screen on `fm.boot`. An empty cell is refused with a dry click. The cell is gone: no charge bar and no upkeep, so she can never be switched off by neglect, which is what keeps T4 unfounded.

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

Trees in `data/frozendawn/shepherd/dialogue/*.json`, text in lang keys, ambient pools in a separate file with trigger, cooldown, mood and variants. The node format is the Notion spec's, with keys under `shepherd.`:

```json
{
  "id": "fm.name",
  "line": "shepherd.fm.name",
  "mood": "neutral",
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

Each is playable, on one branch per slice from `feat/maeve-director`, in a worktree at `/Volumes/SSD/Work/frozen-dawn-aurora-shepherd`. Nothing starts until the owner confirms PR 147 has merged.

| # | Slice | Needs first |
| --- | --- | --- |
| S1 | Engine, creative only | PR 147 merged |
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

## Verification

Tests cover behavior, not lines.

- JUnit: every tree parses; every `next` resolves; every key has a lang entry; no `shepherd.*` value contains "continuity" in any case; no node has two responses in a slot.
- GameTests: a charged cell wakes her and is spent; an empty cell is refused; a second dock stays dark; lethal damage, `/kill` and the void leave her Offline and she reboots; she is no `VaultWatch` watcher; a Terminus discharge leaves her untouched; saved state survives a reload; damage closes a conversation; a client cannot answer a node it was not sent.
- Then the full `architectVerify`, and `architectMonkey` if the slice touches Architect AI, which S1 to S5 should not.
- Owner's visual pass, per slice: mood legibility at play distance above all, then typing speed, the blip, how she hovers and follows.

## Open

The ambient lines mockup, for the owner's approval. Nothing else until a slice starts.

## Status

2026-11-04: design written, four of five mockups approved, no code. Waiting on PR 147, then the owner's word to start S1.

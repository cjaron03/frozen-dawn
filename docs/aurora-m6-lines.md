# Aurora M6: lines for the owner to mark

Drafted 2026-11-06. The owner answered the five questions the same day, each as recommended. Nothing here is built, and only lines the owner marks go in (Global Constraints). The plan is [miller-pattern-m6-plan.md](miller-pattern-m6-plan.md), and the strategies come from [miller-pattern-m6-idea.md](miller-pattern-m6-idea.md).

**How to read it.** (Mood) sets her face. Bold is the button label. `if` is a line's own gate, on top of the topic's. Tags:

- **MANUAL:** the manual's entry, its bullets joined into sentences. These play as a recording, on the voice trace.
- **IDEA:** from an approved strategy in the idea file, put in her voice.
- **PLAN:** drafted in the plan, under "Lines to mark".
- **OWNER:** the owner's own words.
- **NEW:** written here, nowhere else yet.

**Gates.** A (Recording) line plays at every tier. Every other line in a topic also carries `tier_above_wary`, by Ruling 13, so at Wary she only plays the recording. That includes each mob's `open` and `again` lines, which the plan gates only on `orsa_heard_<id>`. Each mob topic's `when` is `not:erased` (Ruling 14). Items have no `when`.

**Source.** Every advice line is Registry: ORSA's manual and field data, loaded into her. Her own way is what she thinks of it. The greetings and the moments are hers.

## Questions the owner answered

Each took the recommendation, 2026-11-06.

1. **Frostmite's strategy (Ruling 19).** It holds in code, with three limits. See "Frostmite, for a verdict". Answer: keep, tested on a live swarm at the pass.
2. **The twelve items (Ruling 12).** See "The twelve items, for a verdict". Answer: keep all twelve. The thermostat's tip is the weakest.
3. **What the egg turns into (Ruling 16).** The figure "flickers to another shape", and no mockup drew that shape. A look needs an approved mockup. Answer: a small hooded figure on one knee, in her green, standing in for the figure that asks for help. It is no film character and carries no marks. Left as is on 2026-11-06, with a floating file number, 66, added at the owner's ask. The number is drawn on the hologram and never said, so the guard on "six" stays absolute. Drawn in `art/aurora-shepherd/egg-mockup.png` and `egg-mockup.gif`, for approval before Task 6.
4. **The gag's other items (Ruling 28).** The Stuffed Penguin is the first and the rest are yours. Answer: the penguin alone for now.
5. **Seven greetings.** Ruling 23 asks for six to eight, and there are seven below, on top of the first and the long-gone lines. Answer: all seven stay.

## Frostmite, for a verdict

**The strategy.** Keep a lit decoy heater out past your base and MiteAway smoke over the real one. Checked against `FrostmiteEntity`, `FrostmiteSpawner`, `MiteAwayRegistry`, `ThermalHeaterBlockEntity`, `VacuumFlames` and `CombustionAtmosphere`.

| Claim | Result |
| --- | --- |
| Mites go for a heater before you | True. A valid heater within 18 blocks clears the player as a target. A player only counts within 14 |
| They skip a protected heater | True. A heater under smoke is passed over, and a smoked mite is pushed out to 11 blocks from the coil |
| A heater holds ten | True. An eleventh goes back to choosing a player. Natural spawns stop at 5 near you in Phase 5 and 8 in Phase 6, by default |
| A latched mite is easy to kill | True, near enough. It circles the heater half a block out, on 5 health, and doesn't come for you. Any hit knocks it off |
| It works without air | Half. The decoy burns in vacuum, because a Thermal Heater has no air check. MiteAway is a flame: once vacuum arrives it lights only inside your seal. The smoke still reaches 8 blocks through walls |
| It beats the manual | Yes. The manual's way has you guarding the heater you need. Hers leaves that one alone and puts the swarm where it costs you nothing |

**The limits.**

1. **It has to be a Thermal Heater,** in the open, where a mite can walk to it, and more than 8 blocks from the coil. An RH-5 draws nothing. I wrote twelve blocks into her line.
2. **The bait costs fuel.** Burning in the cold, it takes 8 units a tick in Phase 6 and 4 in Phase 5. A coal block lasts about 25 minutes in Phase 6. Ten mites zero its warmth, so it burns only their drain, and a coal block lasts about 20 minutes. Plain coal lasts about 2 minutes.
3. **A Hunter can walk up and put it out,** since it's in the open. That wipes the fuel. Her line says so.

Also, in code: with a full EVA suit or better, mites won't target you at all, which is more than the manual's "blocks the drain". Walk into smoke and latched mites let go within 1.5 seconds. Stand within 4.5 blocks of an open heater and they leave you for it after 2 seconds.

## The twelve items, for a verdict

All twelve entries have no advancement, and their categories have no lock either. Each tip below is checked in the code named. None is in the manual as she puts it.

| Item | Her tip | Checked in | Result |
| --- | --- | --- | --- |
| `thermal_heater` | Nine coal in a block burn like ten | `ThermalHeaterBlock`: coal 24,000, block 240,000 | True |
| | A Hunter wipes the fuel, so keep a spare on a lever | `extinguish` zeroes the burn time. `isLit` is false under redstone, so it leaves `HeaterRegistry`, and the Hunter and mite checks need it lit | True. Not tested for Architects, so her line names only Hunters and mites |
| `resistive_heater` | Mites and Hunters ignore it, and it burns nothing | Only `ThermalHeaterBlockEntity` registers, and the mob burns all read `HeaterRegistry` | True |
| `frost_ward_torch` | Sixteen out, but four up or down | `HollowEntity`: the scan runs 16 across, 4 up and down | True |
| | Burns in vacuum, costs no oxygen | `VacuumFlames.isOrdinaryFlame` doesn't list it, and `combustionDemand` is 0 | True |
| `miteaway` | Walk into smoke and they let go | `MITEAWAY_DISENGAGE_DELAY` is 30 ticks | True |
| | Smoke goes through walls | `MiteAwayRegistry.isProtected` measures distance only | True |
| | Needs air, and breathes like you | `canBurnAt`, and `combustionDemand` is 1 against a player's 1 | True |
| `insulated_glass` | Stone leaks eight times as fast | `RoomInsulation`: insulator .1, stone .8, glass 1 | True |
| `thermostat` | A comparator and a lamp show the room is failing | `comparator()` is the sensed temperature over two, 0 to 15 | True, and the weakest of the twelve. Turning it down saves little: about 7 percent at -120 outside |
| `airlocks` | A Hunter can't open one | `AirlockDoorBlock` is `BlockSetType.IRON`, and `OpenDoorGoal` only opens wooden doors. `ReturnedEntity` has no door-breaking goal | True for a Hunter. Architects still break things |
| `oxygen_reservoir` | A cow breathes like five of you | `animalDemand`: cow 5, chicken .07. A lit furnace is 3, a player 1 | True. 1,200 over 6 is 200 seconds |
| `battery_lantern` | A Hunter can't break it | `isBreakableLightSource` has no case for it. It extends `Block` | True. Panel and emergency lights are safe the same way |
| | Needs no air | Not an ordinary flame | True |
| `emergency_light` | Red means the grid is short, and the RH-5 is next | It lights when a `GridLight` is shed, and `PowerNetworks` serves heaters first | True |
| `geiger` | Twice the distance, a quarter of the rate | `RadiationMath.rate` is inverse-square | True |
| | You feel it thirty seconds late, first sickness at 100 | `LATENT_SECONDS` 30, `STAGE_I` 100 | True |
| `rad_aid` | Get out first: about three RU a second | 200 over 60, against the field's rate in `nextDose` | True |
| | A second bag restarts the minute | `finishUsingItem` sets `treatmentSeconds` to 60 | True |

**Where the manual and the code disagree.** Reported, not changed: M6 doesn't edit the manual. The Hunter entry says "Breaks torches and lights", but it leaves grid lights and the Battery Lantern alone. It also says "Opens and breaks wooden doors", but no goal breaks one. Her lines rely on the first and say nothing of the second.

## Labels

| Key | Label | Length | Tag |
| --- | --- | --- | --- |
| `shepherd.talk.ask_advice` | Ask for advice | 14 | PLAN |
| `frostbitten` | Frostbitten | 11 | NEW |
| `frostmite` | Frostmites | 10 | NEW |
| `hollow` | Hollows | 7 | NEW |
| `returned` | The Returned | 12 | NEW |
| `hunter` | Hunter | 6 | NEW |
| `mimic` | Mimic | 5 | NEW |
| `architect` | Architect | 9 | NEW |
| `thermal_heater` | Thermal Heater | 14 | NEW |
| `resistive_heater` | RH-5 Heater | 11 | NEW |
| `frost_ward_torch` | Frost Ward Torch | 16 | NEW |
| `miteaway` | MiteAway | 8 | NEW |
| `insulated_glass` | Insulated Glass | 15 | NEW |
| `thermostat` | Thermostat | 10 | NEW |
| `airlocks` | Airlocks | 8 | NEW |
| `oxygen_reservoir` | O2 Reservoir | 12 | NEW |
| `battery_lantern` | Battery Lantern | 15 | NEW |
| `emergency_light` | Emergency Light | 15 | NEW |
| `geiger` | Geiger Counter | 14 | NEW |
| `rad_aid` | RadAid | 6 | NEW |

## The greeting

At Close, not `quiet`, once a day (Ruling 6). The first plays once, setting `greeted_cargo`. Long gone beats the rotation. The rotation picks by the day number, so the same day gives the same line (Ruling 23).

- `greet.first.1` (Beaming) "Cargo!" PLAN
- `greet.first.2` (Flustered) "That's you. That's what I file you under. Don't make it weird." PLAN, OWNER's words at the end
- `greet.long` `if greet_long` (Beaming) "CARGO! You came back! I'm logging that twice!" PLAN

Rotation:

1. `greet.1` (Beaming) "Cargo. Still breathing. Good for the paperwork." PLAN
2. `greet.2` (Neutral) "Cargo. Temperature's bad. You knew that." PLAN
3. `greet.3` (Amused) "Cargo. I kept your spot warm. I'm a heater. It's not a big gesture." NEW
4. `greet.4` (Beaming) "Cargo! Right on time! I don't know for what." NEW
5. `greet.5` (Unimpressed) "Cargo. You've got frost on you. No, everywhere." NEW
6. `greet.6` (Happy) "There you are. Cargo, present and accounted for." NEW
7. `greet.7` (Thinking) "Cargo. I ran the numbers on today. Let's not." NEW

## The Advice menu

- `advice.menu.first` (Thinking) "Fine. ORSA's answer first, then the one that works." NEW
- `advice.menu.again` (Neutral) "What's trying to kill you now?" NEW

At Wary the menu plays the shipped recording, as ORSA's does.

## The mobs

`open` plays the first time, `again` every time after, in its place (Ruling 25). The rest play each time.

### Frostbitten

**`frostbitten`**

- `frostbitten.open` `if not:orsa_heard_frostbitten` (Neutral) "Frostbitten. ORSA's notes first. They wrote these from a long way off." NEW
- `frostbitten.again` `if orsa_heard_frostbitten` (Unimpressed) "Still slow. Still throwing things." NEW
- `frostbitten.1` (Recording) "Threat: moderate. Weak to fire and Acheronite weapons. Avoid melee unless you enjoy hypothermia." MANUAL
- `frostbitten.2` (Unimpressed) "Avoid melee. Its hand only slows you a little. The freeze is in the snowball." IDEA
- `frostbitten.3` (Neutral) "It only throws from eight to sixteen blocks out. Get inside eight and stay on its level." IDEA
- `frostbitten.4` (Grim) "It's slow, and fire does double. By a lit heater that's four a second against thirty health." IDEA

### Frostmites

**`frostmite`**

- `frostmite.open` `if not:orsa_heard_frostmite` (Thinking) "Frostmites. One's a nuisance. You won't get one." NEW
- `frostmite.again` `if orsa_heard_frostmite` (Amused) "Still the worse heater." NEW
- `frostmite.1` (Recording) "Don't let them stack. A lit heater draws them off you; then guard it. A full EVA suit blocks the drain on you. MiteAway smoke keeps them out of a room." MANUAL
- `frostmite.2` (Amused) "Guard it? Build them a worse one." NEW
- `frostmite.3` (Neutral) "Lit heater out in the open, twelve blocks past your base. MiteAway over the real one. They skip anything under smoke and go for the bait." IDEA
- `frostmite.4` (Grim) "Ten fit on a heater, and each one just sits there on five health. Walk up and swat them." IDEA
- `frostmite.5` (Unimpressed) "Feed it a coal block. The bait isn't free, and a Hunter can still walk up and put it out." NEW

### Hollows

**`hollow`**

- `hollow.open` `if not:orsa_heard_hollow` (Thinking) "Hollow. ORSA wrote this one with the lights on." NEW
- `hollow.again` `if orsa_heard_hollow` (Neutral) "Swing. Then hit it. That's still the whole plan." NEW
- `hollow.1` (Recording) "Swing while grabbed to break free. Fire and Acheronite weapons do full damage. Lit heaters burn them within 8 blocks. If you hear breathing behind you and nobody is there: run." MANUAL
- `hollow.2` (Unimpressed) "Please don't run. It isn't hunting you. It drifts, slower than you walk." IDEA
- `hollow.3` (Neutral) "Let it grab you and swing. Any swing, even empty-handed, before the first ice at half a second. Then it can't grab you for thirty seconds." IDEA
- `hollow.4` (Grim) "That's thirty free seconds on twenty health. Near a lit heater it's gone in five." IDEA
- `hollow.5` (Thinking) "A Frost Ward Torch only sends it away, and then you don't get the Frozen Breath." IDEA

### The Returned

**`returned`**. A plain node with three replies, and only they project (Ruling 15).

- `returned.open` `if not:orsa_heard_returned` (Sad) "The Returned. Three kinds. I'll put them up where you can see them." NEW
- `returned.again` `if orsa_heard_returned` (Neutral) "Which one?" NEW
- `returned.1` (Recording) "Threat: severe. Former staff and civilians who froze and didn't stay dead. Three variants." MANUAL

Replies: 1 **Hunter**, 2 **Mimic**, 3 **Architect**, 4 **Never mind**, the shipped label.

**`hunter`**, `project: hunter`.

- `hunter.open` `if not:orsa_heard_hunter` (Neutral) "Hunter. ORSA's answer first." NEW
- `hunter.again` `if orsa_heard_hunter` (Unimpressed) "Still the heater. It was the heater last time." PLAN
- `hunter.1` (Recording) "Seal entrances with blocks, not doors. Hide backup heaters. Frost Ward Torches can't be broken. Fire and Acheronite weapons work." MANUAL
- `hunter.2` (Thinking) "Word of advice? Don't hide the heater. Show it one." PLAN, OWNER's opening
- `hunter.3` (Grim) "One-high window, lit heater three blocks behind it, on its level. It comes to put it out, it can't reach, and it stands there." PLAN
- `hunter.4` (Neutral) "It burns at about four and a half a second, plus the fire, against fifty health." IDEA
- `hunter.5` (Sad) "They were staff, once. I'd rather it was quick." PLAN

**`mimic`**, `project: mimic`.

- `mimic.open` `if not:orsa_heard_mimic` (Thinking) "Mimic. People took it for a shadow. Ask Agent Torres's partner." NEW
- `mimic.again` `if orsa_heard_mimic` (Unimpressed) "Stand still. Shoot. We've been over this." NEW
- `mimic.1` (Recording) "It retreats to darkness when hurt, and resets after 15 seconds out of sight. Fire: +50% damage. A figure still there after 10 seconds is not a shadow. Shadows have no health bar." MANUAL
- `mimic.2` (Unimpressed) "The manual says it strafes. The manual never stood still." PLAN
- `mimic.3` (Neutral) "For five seconds it's busy being you. Don't move, and shoot it. A hit doesn't end it." PLAN, extended from IDEA
- `mimic.4` (Amused) "Walk at it and it backs away at your speed. It's very committed." IDEA
- `mimic.5` (Grim) "Under twenty health it runs, and it never heals. Stay within sixteen blocks and it copies you again. That's your second five seconds." IDEA
- `mimic.6` (Amused) "Go on, look around. That's a recording, and it's still doing it." NEW. Its head copies the player's look (Task 7).

**`architect`**, `project: architect`. Ruling 24's lines run in the list, during the hologram.

- `architect.open` `if not:orsa_heard_architect` (Grim) "ORSA was terrified of these guys." PLAN, OWNER's words
- `architect.again` `if orsa_heard_architect` (Unimpressed) "Same picture. It's still the best one they've got." NEW
- `architect.1` (Recording) "Acheronite Block walls cannot be broken. Force it to scaffold. Fire deals 1.5x damage. Interrupt its healing by pressing the attack. Build layered defenses." MANUAL
- `architect.2` (Grim) "That's ORSA's best image. Every other capture came out worse." PLAN
- `architect.3` (Thinking) "Huh, it looks kind of like you." OWNER
- `architect.4` (Amused) "So it's got a pickaxe like you, a sword like you and blocks like you. ORSA's scientists would faint." OWNER
- `architect.5` (Thinking) "When it stops like that, it isn't stuck. Nobody at ORSA liked that part." NEW. The stop is on the hologram's own clock, so the line needn't land on it.
- `architect.6` (Neutral) "The early ones wasted swings. The later ones didn't. Nobody showed them." PLAN
- `architect.7` (Worried, then Neutral) "So fight it at your heater, and don't do the same thing twice." PLAN. The worry runs first, by Ruling 5.
- `architect.8` (Unimpressed) "The manual says press the attack while it heals. It writes that down. It answers anything you repeat." IDEA
- `architect.9` (Neutral) "It only drinks once a minute. Let it drink, then force the next retreat inside that minute. Mix your weapons." IDEA
- `architect.10` (Grim) "It won't step back from a heater. Four blocks out it burns like the rest, against forty health." IDEA

## The items

`.1` is the recording. No repeat lines (Ruling 25 is mobs only).

**`thermal_heater`**

- `thermal_heater.1` (Recording) "Burns fuel to heat a sealed room or an open camp. Right-click with coal, charcoal, blaze powder, coal blocks or Cryo Fuel. Redstone stops it and pauses fuel use." MANUAL
- `thermal_heater.2` (Thinking) "Nine coal in a block burn like ten. Block your coal." NEW
- `thermal_heater.3` (Neutral) "When a Hunter puts one out, the fuel goes too. All of it. Keep a spare with a lever on it. Off, it burns nothing, and Hunters and mites ignore it." NEW

**`resistive_heater`**

- `resistive_heater.1` (Recording) "Electric room heat. No fuel, oxygen or Thermal Core. In vacuum only 25% of the heat you pay for reaches the walls." MANUAL
- `resistive_heater.2` (Amused) "Mites don't swarm it and a Hunter won't touch it. To them it's furniture." NEW
- `resistive_heater.3` (Unimpressed) "It won't burn anything either. Everything I told you about fighting at a heater means the fuel kind." NEW

**`frost_ward_torch`**

- `frost_ward_torch.1` (Recording) "Hollows within 16 blocks flee. Returned Hunters can't break it. Normal torches only keep Hollows off at close range. Ring your base with these." MANUAL
- `frost_ward_torch.2` (Thinking) "That's sixteen out, but only four up or down. A second floor gets its own ring." NEW
- `frost_ward_torch.3` (Amused) "It burns in vacuum and costs your room no oxygen. It's ghost breath. I've stopped asking." NEW

**`miteaway`**

- `miteaway.1` (Recording) "Radius: 8 blocks. Burn time: 10 minutes. Right-click with an empty hand to snuff it and save the rest of the burn. Frostmites only. MiteAway is not a pesticide." MANUAL
- `miteaway.2` (Neutral) "Caught some? Walk into the smoke. They let go in under two seconds." NEW
- `miteaway.3` (Thinking) "The smoke goes through walls. A coil inside covers eight blocks of outside." NEW
- `miteaway.4` (Unimpressed) "It's still a flame. With no air outside it only lights inside your seal, and in there it breathes like one of you." NEW

**`insulated_glass`**

- `insulated_glass.1` (Recording) "A window that insulates like wool. Plain glass seals air but leaks heat. Insulate the floor, walls and roof too." MANUAL
- `insulated_glass.2` (Thinking) "Stone leaks eight times as fast. Nobody said it had to be a window." NEW

**`thermostat`**

- `thermostat.1` (Recording) "Sets the temperature of a sealed room. 10-30°C, in 5°C steps, for every heater in the room. After a breach it shows No seal; heaters fall back to 20°C until you repair." MANUAL
- `thermostat.2` (Neutral) "Put a comparator on it. Signal ten is twenty degrees. Run it to a lamp by the door and you'll know the room's failing before you walk in." NEW

**`airlocks`**

- `airlocks.1` (Recording) "Lets you leave a sealed base without venting it. Two complete Airlock Doors, at most 32 open interior blocks, one controller in a wall, facing the chamber. Close both doors, then right-click the controller." MANUAL
- `airlocks.2` (Thinking) "The manual wants blocks, not doors, against a Hunter. These count as blocks. It can't open one." NEW

**`oxygen_reservoir`**

- `oxygen_reservoir.1` (Recording) "Stores spare oxygen for a sealed room. It makes none. 1,200 player-seconds: one player for 20 minutes. A breach shuts the valve; contents stay inside." MANUAL
- `oxygen_reservoir.2` (Amused) "Twenty minutes is one of you. A cow breathes like five. You and a cow get three minutes and change. Eat the cow." NEW
- `oxygen_reservoir.3` (Thinking) "A chicken's next to nothing. A lit furnace is three of you. Check what's breathing before you check the tank." NEW

**`battery_lantern`**

- `battery_lantern.1` (Recording) "A cave light that runs on one Power Cell. It draws 50 W while on: a full cell lasts about 2 h 47 min. Lit in your offhand only; press F to move it there." MANUAL
- `battery_lantern.2` (Amused) "A Hunter breaks torches and lanterns because it knows what they are. It doesn't know what this is." NEW
- `battery_lantern.3` (Neutral) "It doesn't need air, either. Your torches will." NEW

**`emergency_light`**

- `emergency_light.1` (Recording) "Off while the grid is healthy, charging its own battery for free. When normal lights lose power, it glows dim red for up to 10 minutes." MANUAL, with "level 6" trimmed
- `emergency_light.2` (Thinking) "The grid cuts lights before heat. So red doesn't mean the power's out. It means it's running out, and your RH-5 is next." NEW

**`geiger`**

- `geiger.1` (Recording) "Clicks while carried. Hold it to read counts, rate and dose. Above about 15 RU/s the clicks blur into a roar. If it screams, leave." MANUAL
- `geiger.2` (Neutral) "Leave which way? Any way. Twice the distance is a quarter of the rate." NEW
- `geiger.3` (Thinking) "Watch the dose, not how you feel. You feel it about thirty seconds late, and the first sickness starts at a hundred RU." NEW

**`rad_aid`**

- `rad_aid.1` (Recording) "An IV drip that flushes 200 RU of dose over one minute. It takes 3 seconds to hook up. It can't be crafted. Look for it in ORSA medical salvage." MANUAL
- `rad_aid.2` (Neutral) "Get out first. It only takes off about three RU a second, and anything stronger wins." NEW
- `rad_aid.3` (Unimpressed) "One at a time. A second bag restarts the minute, and you lose what's left of the first." NEW

## Close lines

Each is one whole lang key, `shepherd.advice.<id>.guess`, in the shape M4 shipped. All NEW.

| Topic | Line |
| --- | --- |
| `frostbitten` | You mean the Frostbitten, or are we just saying things now? |
| `frostmite` | You mean the Frostmites, or are we just saying things now? |
| `hollow` | You mean the Hollows, or are we just saying things now? |
| `returned` | You mean the Returned, or are we just saying things now? |
| `hunter` | You mean the Hunter, or are we just saying things now? |
| `mimic` | You mean the Mimic, or are we just saying things now? |
| `architect` | You mean the Architect, or are we just saying things now? |
| `thermal_heater` | You mean the heater, or are we just saying things now? |
| `resistive_heater` | You mean the RH-5, or are we just saying things now? |
| `frost_ward_torch` | You mean the Frost Ward Torch, or are we just saying things now? |
| `miteaway` | You mean MiteAway, or are we just saying things now? |
| `insulated_glass` | You mean the glass, or are we just saying things now? |
| `thermostat` | You mean the thermostat, or are we just saying things now? |
| `airlocks` | You mean the airlock, or are we just saying things now? |
| `oxygen_reservoir` | You mean the reservoir, or are we just saying things now? |
| `battery_lantern` | You mean the lantern, or are we just saying things now? |
| `emergency_light` | You mean the emergency light, or are we just saying things now? |
| `geiger` | You mean the Geiger counter, or are we just saying things now? |
| `rad_aid` | You mean RadAid, or are we just saying things now? |

## "Help me"

Typed only, above Wary, with no label and no Close line (Ruling 17). All NEW.

- `chat.need_help` (Neutral) "Ask me for advice. Literally. Say what's trying to kill you."
- `chat.need_help.warm` `if tier_friendly` (Happy) "Always. Ask me for advice, and tell me what's trying to kill you."

## Small moments

- **Reaction,** `reacted_hunter_burn`, once a world (Ruling 26). (Sad) "That worked. I said it would." PLAN
- **Caught,** once a day (Ruling 27). (Flustered) "You didn't see that!" PLAN
- **The gag,** at Close, when calm (Ruling 28). (Beaming) "Cargo! Cargo! Check this out!" PLAN, OWNER's words
- **The gag, the penguin.** (Amused) "Look at him. Still doing his best." NEW. It calls back `penguin.3`.
- **The nod,** `if t5_done`, once a world (Ruling 29). (Thinking) "ORSA had a file on a man named Clovis Bray. Heard of him?" PLAN
- **The egg,** at Trusted, on the second projection, once (Ruling 16). (Flustered) "Sorry. Wrong file. That one wasn't for you." NEW. It quotes nothing, and it laughs at her own mistake, not at the Returned. Waits on the egg's mockup.

## Checked against the limits

Every line, label and Close line above, by hand and by a script running `typedLinesStayInside`'s banned words with `pattern` added, and the 20-character label limit.

| Red line or limit | Result |
| --- | --- |
| 1. CONTINUITY | Absent |
| 2. Hinting at what it is | Absent |
| 3. Respawn, enrollees, the badge | Absent. The manual's ID badge line is left out. `greet.long` says "You came back", about an absence, and that is all |
| 4. Heart, archive, the lid, Thaeven words | Absent. The manual's "ORSA does not believe it is the last variant" is left out, so nothing points at a later one |
| 5. What the Terminus does, how Enoch died | Absent |
| 6. Kevin's fate, Mars and the Returned | Absent |
| 7. Morrow | Absent |
| 8. TITHONUS, Sophie Miller, Anderson-Kind before T5 | Absent. The nod is gated on `t5_done` |
| 9. Audacity, Epigraph, fourth wall, Bloom, the Moon | Absent. The count stays at three. The nod and the egg stay in the world |
| 10. Sealed sequel material | Not read and not used |
| 11. Dates, document numbers | Absent |
| Maeve, MACS, Master Architect | Absent |
| "six", "fair", "rules" | Absent. "sixteen" is another word and the lint passes it. "level 6" is trimmed from `emergency_light.1` |
| Alive | Absent |
| "Miller", "pattern", "game" | Absent |
| The player's death | Absent. "Still breathing" is a joke about paperwork |
| Her guilt about the Returned | Kept. `returned.open` and `hunter.5` are Sad, and the gag and the egg don't touch them |
| Labels over 20 characters | None |

Lines nearest the edge, to listen for at the pass: `architect.3` and `architect.4` (yours, and the closest thing here to saying what the player is), `architect.6`, `architect.5`, `greet.long` and `returned.1`, which says "didn't stay dead" in ORSA's voice.

## The owner's marks

2026-11-06: the five questions, each as recommended. The lines themselves are not yet marked.

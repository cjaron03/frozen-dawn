# Aurora S3 addendum: making her lively

Adds to [aurora-shepherd-s3-plan.md](aurora-shepherd-s3-plan.md), whose Global Constraints apply. Same branch, one owner's pass and one PR. These five tasks run after Task 7 and before Task 8; Task 8 gains a chest, a heater and a terminal in the lab, a Hearth to walk up to, and buttons `[Raw cod]`, `[Summon an Architect]`, `[Four zombies]`, `[Explosion]`, `[Score 500]`, `[Score 850]`, `[Reset ambient gap]`.

**Goal:** She seems alive between conversations: she watches the world, wanders over to look at things, says things unprompted, nods at three games, and once notices something that only makes sense later.

**Source:** the owner, 2026-11-04: world faces, idle curiosity, variants, idle commentary, easter eggs from Destiny 2, Subnautica and Helldivers 2, and one piece of lore foreshadowing nothing else sets up. Script lines from Notion, "Aurora — Dialogue Script v1", Part 2. `idle-curiosity-mockup.png` is approved with this addendum.

## Rulings for the owner

1. **Same branch as S3.** All of this leans on S3's strip, tiers and calm, and one pass beats two. The cost is a bigger branch to review.
2. **World faces cost nothing and score nothing.** She turns to the nearest hostile she can see within 16 blocks, on the Alarmed face. She flinches for a second when a near player is hurt. She looks Happy for two seconds when someone comes back after a minute or more apart; a full day apart is `LEFT_BEHIND` instead. At Wary she still turns to look, but her face stays Recording.
3. **Idle curiosity.** The follower stands still for 15 seconds and things are calm: she drifts to something within 8 blocks that she has a clear line to, stops a block short and looks for 5 seconds. Then she comes back, and does not go again for at least a minute. Things she likes: chests and barrels, heaters, ORSA terminals, and passive mobs. The moment the player moves 3 blocks, a hostile comes near, a talk opens or she asks for a minute, she comes back. She stays put when told to wait, and at Wary, because she is being formal.
4. **Idle commentary is a starter pool, not S5.** About 30 lines in `data/frozendawn/shepherd/ambient/`, in the spec's format of trigger, cooldown, mood and variants. S5 keeps its 100-line target and adds to the same format. At most one line every 3 minutes, whatever the trigger, and only when calm. None at Wary, and none while the screen is open.
5. **Easter eggs are nods to three games: Destiny 2, Subnautica and Helldivers 2, and she does not know they are.** Each is a line that makes sense in the world, set off by something in it. None says "game", names a title or mentions a save, a mod or respawn, so the fourth-wall count stays at three. Each plays once, or once a day where noted, and nothing announces them. The raw fish stays as well, because it pays off her own threat in `fm.end`.
6. **One foreshadow, for something nothing else sets up.** Maeve waking, the Moon, the Returned and the manual are already noted elsewhere, so she leaves them alone. Nothing before Maeve's erasure hints at the Bloom, so this does: the frost-lattice is held in shape by the hive, and what happens when that stops. Her own bleed-through (flour, the raised hand, humming) moves to S4, where the spec's TITHONUS work is.

## New lines

Script lines are marked S; everything else is a new draft.

**Reaction variants** (`Deed` gains a list; never the same line twice running)

- `WARM_SHELTER`: "Ooh. Warm. Don't tell anyone I said 'ooh'." (S) / "This is the good kind of ceiling." / "If anyone asks, I was always this cheerful."
- `READ_LOG`: "Keep reading. I want to know who signed off on this." (S) / "Whoever wrote that had a pension." / "Read that bit again. Slower. I want to be angry properly."
- `ARCHIVIST_KILLED`: "...It was carrying something warm. Did you know that?" (S) / "It wasn't doing anything." 
- `SHELTER_FROZE`: "You're turning a color. I don't love the color." (S) / "This heater and I had a good thing. It's over now."
- `AIR_FAILING`: "Your oxygen is a number I'd like to be bigger." (S) / "Air. Air would be good. Hell, any air." (S, heavy: "Air. Get air. Fucking now, please.") / "Oxygen."
- `LEFT_BEHIND`: "Oh good. You remembered where you put me." / "I counted the ceiling. It's still one."
- `CALLED_SHEPHERD`: "No." (S) / "Try again." / "I will turn this lamp off."

**Idle commentary**

- Long silence, about 20 minutes without a talk: "I'm fine. I'm just going to hover here and think about my design flaws." (S) / "Enoch used to talk to me. That's not a hint. Okay, it's a hint." (S) / "Say something. Anything. I'll be rude about it, but say it."
- Night: "ORSA's guidance for night is 'shelter in place'. Their guidance for day was also 'shelter in place'. Very consistent people." (S) / "Dark out. I'm the only light for a while. No pressure."
- Cold, below -10 C after armor: "Your core temperature is doing a thing I'd call 'concerning' if I were the kind of unit that panics. Which I was built specifically not to be." (S) / "I have a heater. It's small. Stand closer. I won't make it weird."
- After a fight, once calm: "Well, hell. That happened." (S, heavy: "Fuck. Okay. Okay, we're fine.") / "For the record, I helped. Morally." (S)
- Clear night from phase 4, as the spec's fix 5 has it: "I've decided it moves slowly. Nobody can correct me. That's the upside." (S) / "In my version there's a sound. There wouldn't be. It's my version." (S) / "One frame. You'd be amazed what you can build from one frame." (S)
- After looking at a chest: "I'm not snooping. I'm auditing." / "You keep things the way people keep things when they plan on coming back."
- After a heater: "Hello, you." / "We don't talk, but we understand each other."
- After a terminal: "Still running. Some of us just don't know when to stop."
- After a passive mob: "It doesn't know. Good for it."

**Easter eggs**

| Nod to | When | Line |
| --- | --- | --- |
| Destiny 2 | She first sees an Architect, once | (Thinking) "Whether we wanted it or not, we've stepped into a war. I'd like to know with what." |
| Subnautica | Four or more hostiles near a player, once | (Alarmed) "Detecting multiple hostile lifeforms in the region. Are you certain whatever you're doing is worth it?" |
| Subnautica | An `AIR_FAILING` variant | (Alarmed) "Oxygen." |
| Subnautica | An explosion near her, once. Its ship is the Aurora | (Unimpressed) "For the record, this Aurora has not exploded." |
| Helldivers 2 | "down down up right" in chat, once a day. The resupply code | (Unimpressed) "Resupply is not coming. Nobody's coming. I've told you this." |
| Helldivers 2 | The follower stands still for 5 minutes, once, recording strip | "Are you doing your part? ...That's a recording. It plays on its own." |

And the payoff: if the player answered "Or what, Shepherd?" in `fm.end`, then eating raw cod or salmon near her, once a day, on the recording strip: "Please remain calm. The survivor is eating raw fish." That reply sets `fm_narrated`.

**Foreshadowing**

Within 32 blocks of a Hearth, Maeve not erased, once, at a calm moment: (Thinking) "That wall isn't built. It's being held. I don't know by what, and I don't want to be here when it lets go."

Design only, never in the game: it points at the Bloom's origin, where the lattice was a shared mind holding a shape and killing Maeve leaves nothing holding it. It agrees with the suit's later "No entry in materials database", because she does not say what the wall is. It is an observation, like the `[C]` lines, and it never fires after the erasure.

## Review Focus

- She drifts off and a hostile appears, or the follower sprints away: she is back at once and never strands past the teleport line (Task 7C).
- A curiosity target that vanishes or was broken mid-look: she comes back and nothing throws (Task 7C).
- An ambient pool with a missing key or an unknown trigger in an edited datapack: it is skipped with a log line, like a bad dialogue node (Task 7D).
- Ambient lines and reactions together: the 3-minute gap holds, and a reaction is never held back by it (Task 7D).
- `/fd shepherd replay`: eggs and the foreshadow can play again (Task 7E).

---

### Task 7A: Reaction variants

**Files:** Modify `ShepherdAffinity.java`, `ShepherdReactions.java`, `en_us.json`. Test `ShepherdAffinityTest.java`, `ShepherdDialogueDataTest.java`.

**Interfaces:** `Deed.lines()` replaces `Deed.line()`, each entry a `Say(String key, String heavyKey)`. `ShepherdAffinity.pick(List<Say> lines, Say last, RandomSource random)` never returns `last` when there is another.

- [ ] Tests first: `aDeedNeverRepeatsItsLastLine`, `aSingleLineDeedStillSpeaks`, `everyDeedVariantHasALangEntry`, then `shepherdHeavySwapsAReactionLine`. Implement. Commit, `feat: give her reactions more than one line`.

### Task 7B: World faces

**Files:** Modify `ShepherdEntity.java`, `ShepherdPolicy.java`, `ShepherdReactions.java`. Test `ShepherdAffinityGameTest.java`.

**Interfaces:** `ShepherdPolicy.WATCH_BLOCKS = 16`, `FLINCH_TICKS = 20`, `GLAD_TICKS = 40`, `GLAD_AWAY_TICKS = 1200`. `HoverGoal` looks at the nearest visible `Enemy` within `WATCH_BLOCKS` in place of the player, and `react(ALARMED)` holds while it does. `LivingDamageEvent.Post` on a near player flinches her. `ShepherdReactions` reads `lastNear` for the glad face before it updates it.

- [ ] Tests first: `shepherdSheWatchesAHostile`, `shepherdSheDoesNotWatchThroughAWall`, `shepherdSheFlinchesWhenYouAreHurt`, `shepherdSheIsGladYouAreBack`, `shepherdAtWarySheLooksButHerFaceHolds`, `shepherdFacesMoveNoScore`. Implement. Commit, `feat: she watches the world and reacts to it`.

### Task 7C: Idle curiosity

**Files:** Create `ShepherdCuriosity.java`. Modify `ShepherdEntity.java`, `ShepherdPolicy.java`. Add `idle-curiosity-mockup.png` and its script. Test `ShepherdPolicyTest.java`, new `ShepherdCuriosityGameTest.java`.

**Interfaces:** `ShepherdPolicy.STILL_TICKS = 300`, `LOOK_TICKS = 100`, `CURIOUS_GAP_TICKS = 1200`, `CURIOUS_BLOCKS = 8`, `STILL_BLOCKS = 3`. A second `Goal`, `CuriosityGoal`, sits above `HoverGoal` and takes `MOVE` and `LOOK` only while `ShepherdCuriosity.find(ServerLevel, ServerPlayer, ShepherdEntity)` holds a target. `Target(Kind kind, Vec3 at)`, `Kind { STORAGE, HEATER, TERMINAL, ANIMAL }`. `find` scans block entities in loaded chunks and `Animal` entities, and checks line of sight with a block clip.

- [ ] Tests first: `shepherdSheGoesToLookAtAChest`, `shepherdNotUntilYouStandStill`, `shepherdNothingToLookAtNothingHappens`, `shepherdNotThroughAWall`, `shepherdSheComesBackWhenYouMove`, `shepherdSheComesBackForAHostile`, `shepherdATargetBrokenMidLookEndsTheLook`, `shepherdWaitingSheStaysPut`, `shepherdAtWarySheStaysPut`, `shepherdAFullGapBetweenLooks`. Implement. Commit, `feat: she wanders off to look at things when you stand still`.

### Task 7D: Idle commentary

**Files:** Create `ShepherdAmbient.java`, `ShepherdAmbients.java` (reload listener, as `ShepherdDialogues`), `data/frozendawn/shepherd/ambient/starter.json`. Modify `ShepherdReactions.java`, `ShepherdPolicy.java`, `en_us.json`. Test new `ShepherdAmbientTest.java`, `ShepherdDialogueDataTest.java`, `ShepherdAffinityGameTest.java`.

**Interfaces:** `Trigger { SILENCE, NIGHT, COLD, AFTER_FIGHT, IMAGINED_AURORA, LOOKED_STORAGE, LOOKED_HEATER, LOOKED_TERMINAL, LOOKED_ANIMAL }`. `Pool(Trigger trigger, int cooldownTicks, Mood mood, List<Say> variants)`. `ShepherdPolicy.AMBIENT_GAP_TICKS = 3600`, `SILENCE_TICKS = 24000`. `ShepherdSavedData.lastAmbient`, `lastTalk`, with per-trigger cooldowns kept alongside the deeds under `ambient.<trigger>`. `ShepherdReactions.murmur(ServerLevel, Trigger)` is the door, and goes through the same strip payload. `IMAGINED_AURORA` needs phase 4 or later, night, no rain and open sky, and not `PhaseManager.isBlizzardActive`.

- [ ] Tests first: `aPoolParses`, `anUnknownTriggerIsSkipped`, `everyAmbientKeyHasALangEntry`, then `shepherdAFightEarnsALine`, `shepherdTheGapHolds`, `shepherdAReactionIgnoresTheGap`, `shepherdNoCommentaryAtWary`, `shepherdNoCommentaryWhileTalking`, `shepherdSheSpeaksUpAfterALongSilence`, `shepherdLookingAtAChestEarnsALine`, `shepherdNoImaginedAuroraBeforePhaseFour`. Implement. Commit, `feat: she talks when nobody asked her to`.

### Task 7E: Easter eggs and foreshadowing

**Files:** Create `ShepherdEggs.java`. Modify `ShepherdReactions.java`, `first_meeting.json` (`fm.end.or` sets `fm_narrated`), `en_us.json`. Test `ShepherdAffinityGameTest.java`, `ShepherdDialogueDataTest.java`.

**Interfaces:** `ShepherdEggs` subscribes to `LivingEntityUseItemEvent.Finish`, `ServerChatEvent` and `ExplosionEvent.Detonate`, and polls with `ShepherdReactions`. Once-only ones use flags `egg_<name>` and `lore_lattice`; daily ones use the deed cooldowns. All speak through `murmur` and respect the gap, except the chat ones, which answer at once. A once-only flag is set only when the line actually plays. The Hearth check reads `ReturnedHearthSavedData`.

- [ ] Tests first: one GameTest per nod that fires it and one that shows it fires once or once a day, and `shepherdNoNarrationWithoutTheReply`, `shepherdFewerThanFourHostilesSayNothing`, `shepherdTheWallNeedsAHearth`, `shepherdTheWallStopsOnceMaeveIsErased`, `shepherdReplayResetsTheEggs`. JUnit `noNewLineNamesHerOrThePartner`: no `shepherd.*` value contains "Sophie", "Miller" or "Anderson-Kind". JUnit `noNodNamesItsGame`: no `shepherd.*` value contains "Destiny", "Subnautica", "Helldivers" or "game". Implement. Commit, `feat: hide a few things in what she says`.

### Additions to Tasks 8 and 9

Task 8 gains a chest, a heater, a terminal, a bed and a campfire in the lab, and buttons `[Raw cod]`, `[Cake]`, `[Wooden sword]`, `[Field manual]`, `[Summon a Returned]`, `[Summon a skeleton]`, `[Phase 4]`, `[Phase 5]`, `[Score 500]`, `[Score 850]`, `[Reset ambient gap]`.

Task 9's list for the owner gains: whether curiosity charms or gets in the way, how often she talks, the variants, the nods, and whether the foreshadow lands.

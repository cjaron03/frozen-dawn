# Aurora S2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task, inline. The owner avoids subagents, except the one branch review at the end. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Aurora found where the story puts her: dead in a sealed dock beside Enoch Okoye in the Terminus Vault, woken with the spare cell from the locker, with the first meeting's vault lines and two "Got a minute?" conversations on the way out.

**Architecture:** A second block, `vault_cs1_dock`, is the same dock under the vault's sealed properties, and waking on it is what marks her as met in the vault. Dialogue gains the conditions the spec already allows for: a node can stand aside for another, and a line can depend on the world. A new `ShepherdPrompt` holds one waiting conversation in saved state, and the client draws it as the approved strip.

**Tech Stack:** Java 21, NeoForge 1.21.1, JUnit in `src/test`, GameTests in `src/gameTest`.

**Spec:** [aurora-shepherd.md](aurora-shepherd.md), section "S2: in the vault", fixes 1, 2, 3, 4 and 7, and "The talk screen". Lines come from Notion, "Aurora — Dialogue Script v1", Part 1, read 2026-11-04.

**Branch:** `feat/aurora-vault` in `/Volumes/SSD/Work/frozen-dawn-aurora-vault`, cut from `origin/feat/maeve-director` at b82a0bc2.

## Global Constraints

- In code she is Shepherd: package `com.frozendawn.shepherd`, lang `shepherd.*`, data `data/frozendawn/shepherd/`. No `shepherd.*` text contains "continuity" in any case. She never mentions respawn. No emojis.
- No new mockup is needed. The dock, its place and all its states are `dock-mockup.png`; the strip is the "She wants to talk" panel of `ambient-lines-mockup.png`. Both are approved "Build as drawn". Build nothing that looks different from them.
- Every line is a draft until the owner marks it. New lines this plan writes are listed under Rulings.
- Each Shepherd GameTest owns a batch, with `ShepherdTestWorld.fresh` and `clear` around it, and starts with `ShepherdTestWorld.whenTicking`. No test ends while a dock is booting. Every new GameTest goes in `config/architect-required-tests.txt` as `<lowercased name> xml`.
- Do not run Gradle while the lab client is open in this checkout, unless AGENTS.md's conditions hold.
- Tests cover behavior, not lines. Conventional Commits, staged by file name. Never `./gradlew --stop`.

## Rulings for the owner

Approving the plan accepts these unless you say otherwise.

1. **`fm.end` stays the close of the first conversation:** `fm.name`, then `fm.room`, then `fm.end`. The script lists it after `fm.doors` and `fm.exit`, but those are now separate prompts on the walk out, and "You don't call me Shepherd. Deal?" should not wait 100 blocks. So "Let's go see if the sky's still there" comes before the walk.
2. **A third `fm.room` log line.** The script's two forms quote "Transfers logged: 1". That is false if the wand has been used, or the world is past the Heart and the tooltip reads "Terminations", or someone else has it. In those cases she says a new draft: "My last log is him sitting right there, talking to me about nothing. Then my cell ran dry. Now he's like that, and the number on that wand has moved."
3. **Prompt rhythm.** The strip types, holds 4 seconds and fades over 1, as the spec has it. It comes back after 60 seconds, three showings in all, then stops nagging. She still waits: the next right-click plays the conversation instead of the hub.
4. **One prompt at a time.** If `fm.doors` is still waiting when you reach the exit, the exit line replaces it and `fm.doors` is lost. Its moment passed.
5. **Calm, for now, is the first two of the spec's tests:** no hostile within 24 blocks and no damage in 30 seconds. I found no single server-side cold or O2 warning reading, and the vault may sit under the cold line for good in late phases, so a prompt might never show. The other two tests can come with S3 or S5.
6. **After `/fd terminus vault reset` with her awake, the dock stands Dark, not Found.** She cannot be in two places, and her saved state stays alone. The reset cannot be used to wake her twice; a second run of the wake needs a fresh save.
7. **The reset leaves the locker alone,** as it does today. The playtest world has a button for a charged cell.
8. **The spare cell is fully charged,** 500,000 J, in locker slot 14, carrying on the logs' every-other-slot pattern. A player can take it and spend it elsewhere.
9. **Saves with a vault already built** have no dock and no spare. The reset adds the dock; nothing adds the cell. No retrofit is planned; say if a save you care about needs one.
10. **Draft text to mark:** Hale, "There is nothing down here but the cradle, that CS-1 unit and us."; manifest row, "Item: CS-1 unit 4, one"; "Got a minute?" and "Right-click her" as drawn; the third log line in ruling 2. The script's "(Happy, then Unimpressed)" reply is split into two lines so her face can change: "...That was very sweet." / "And I'm going to pretend it didn't happen."

## Review Focus

Each has its test in the owning task.

1. She is woken late: the wand already used, or the world past the Heart. No line may quote a log the game contradicts. Task 4.
2. The player presses Esc on `fm.room` or `fm.doors`, or runs `/fd shepherd replay`: she resumes the same node, and a replay still reaches `fm.room`. Tasks 4 and 5.
3. She is Offline, told to wait or more than 16 blocks away when the player crosses a trigger: no prompt fires, and none is used up. Task 5.
4. The reset runs mid-boot, or with her awake: one Aurora, no flash of her body on the dock, saved state unchanged. Task 1.
5. A player ignores a prompt, or reloads the world while one waits: it stops nagging, survives the reload and still plays on a right-click. Task 5.

---

### Task 1: The dock, the spare cell and the reset

**Files:**
- Modify: `src/main/java/com/frozendawn/init/ModBlocks.java`, `init/ModBlockEntities.java`
- Modify: `src/main/java/com/frozendawn/vault/TerminusVaultBuilder.java`
- Modify: `src/main/java/com/frozendawn/command/TerminusDebugCommand.java` (the reset's message only)
- Create: `src/main/resources/assets/frozendawn/blockstates/vault_cs1_dock.json`, the same variants as `cs1_dock.json` and the same models
- Modify: `src/main/resources/assets/frozendawn/lang/en_us.json` if vault blocks carry a name there
- Test: `src/gameTest/java/com/frozendawn/vault/TerminusVaultGameTest.java`, new `src/gameTest/java/com/frozendawn/shepherd/ShepherdVaultGameTest.java`

**Interfaces:**
- Produces: `ModBlocks.VAULT_CS1_DOCK`, a `ShepherdDockBlock` built from `vaultSealed(MapColor.METAL).sound(SoundType.METAL).noOcclusion()`, registered next to `VAULT_DESK`, with no item. `ModBlockEntities.CS1_DOCK` accepts both blocks.
- Produces: `TerminusVaultBuilder.DOCK = new BlockPos(103, FLOOR + 1, -3)`, `SPARE_CELL_SLOT = 14`, and `public static void dock(ServerLevel l, BlockPos at)`, which sets the block facing south, `DARK` if `ShepherdSavedData` says `AWAKE`, otherwise `FOUND`. The state is chosen here, not left to the entity's next tick, so she never flashes on the dock.

- [ ] **Step 1: Write the failing tests.** In `theVaultStandsUnderTheCoreOncePerWorld`, assert the block at `hall.offset(DOCK)` is `VAULT_CS1_DOCK`, facing south, state `FOUND`, with `getDestroySpeed < 0` and explosion resistance over 1000; and that the locker's slot `SPARE_CELL_SLOT` is a Power Cell with `PowerCellItem.energy == PowerCellItem.CAPACITY_JOULES`, with the document count unchanged. In `ShepherdVaultGameTest`:
  - All of these set `hall = h.absolutePos(spot).subtract(DOCK)` and build the dock with `TerminusVaultBuilder.dock`, not a whole vault: a dock 100 blocks from the test area would never tick. Each batch runs alone, so what `reset` writes at its other offsets lands in no neighbour.
  - `shepherdVaultResetPutsTheDockBack`: dormant, set the dock `EMPTY`, call `reset`, assert `FOUND`.
  - `shepherdVaultResetLeavesHerSavedStateAlone`: stage `AWAKE` with affinity 40, a flag and a follower. After `reset` the dock is `DARK` and all four values are unchanged.
  - `shepherdVaultResetDuringBootStillBringsHerUp`: wake on the dock, `reset` during `BOOT`, and wait until exactly one `cs1_unit` exists and the dock is out of `BOOT`.
  - `shepherdEmptyVaultDockRefusesACell`: a charged cell on an `EMPTY` vault dock is not spent.
- [ ] **Step 2:** `./gradlew gameTestQuick -PfdTests=shepherdvault,thevaultstands`. Expected: they fail to compile, then fail on the missing dock.
- [ ] **Step 3:** Add the block, the blockstate, `DOCK`, `dock(...)`. In `vault(...)`, call `dock` and set `PowerCellItem.charged(PowerCellItem.CAPACITY_JOULES)` in the locker. In `reset(...)`, call `dock`. Change the reset's message to add "dock back".
- [ ] **Step 4:** Run the same command. Expected: pass.
- [ ] **Step 5:** Add the four test names to `config/architect-required-tests.txt`. Commit, `feat: seal Aurora's dock and the spare cell into the vault`.

### Task 2: The vault's paper

**Files:**
- Modify: `.../patchouli_books/frozen_dawn_guide/en_us/entries/orsa_intel/terminus_log_last.json`, `terminus_log_security.json`, `terminus_manifest.json`
- Test: `src/test/java/com/frozendawn/vault/VaultDocumentsTest.java`

The existing `theManifestLeavesItsBlanksBlank` stays as it is: the new row goes straight after the Terminus row, above the blanks. `make_vault_branding_mockup.py` is a past mockup and stays.

- [ ] **Step 1: Write the failing tests:** `theLastLogIsEnochs` asserts page 0 contains "Okoye, E." and not "Okoye, A."; `theManifestListsTheUnit` asserts page 1 contains "Item: TR-0 Terminus, one$(br)Item: CS-1 unit 4, one$(br)Issuing division:"; `haleCountsTheUnit` asserts the security log's page 1 contains "CS-1 unit".
- [ ] **Step 2:** `./gradlew test --tests '*VaultDocumentsTest'`. Expected: three failures.
- [ ] **Step 3:** Make the three edits, Hale's and the row as in ruling 10.
- [ ] **Step 4:** Run it again. Expected: pass.
- [ ] **Step 5:** Commit, `feat: put Aurora in the vault's paperwork`.

### Task 3: Dialogue conditions and the vault's nodes

**Files:**
- Modify: `src/main/java/com/frozendawn/shepherd/ShepherdDialogue.java`, `ShepherdConversation.java`
- Modify: `src/main/resources/data/frozendawn/shepherd/dialogue/first_meeting.json`, `assets/frozendawn/lang/en_us.json`
- Test: `src/test/java/com/frozendawn/shepherd/ShepherdConversationTest.java`, `ShepherdDialogueDataTest.java`

The new nodes go in `first_meeting.json`, because `ShepherdConversationTest` loads that file alone.

**Interfaces:**
- Produces: `record Line(String key, String heavyKey, Mood mood, String condition)`, JSON `"if"`, null for always.
- Produces: `record Node(String id, List<Line> lines, List<Response> responses, String next, List<String> set, String when, String otherwise)`, JSON `"when"` and `"else"`. Entering a node whose `when` does not hold goes on to its `else`, as if it had never been there; its `set` is not earned.
- Produces: `new ShepherdConversation(Map<String, Node> nodes, String start, Predicate<String> holds)`, the start node going through the same rule, and `List<Line> lines()`, the current node's lines whose condition holds. The existing test calls pass `c -> false`.
- Produces, in the data: conditions `woke_in_vault`, `wand_cradle`, `wand_hand`, `wand_other`; flag `fm_done`, set by the three `fm.end.*` leaves.

Nodes, with the script's moods and scores:

| Node | Lines | Replies |
| --- | --- | --- |
| `fm.room`, `when` `woke_in_vault`, `else` `fm.end` | Sad "Don't step on him. That's Enoch. He was nice to me."; then one of three, Sad: `wand_cradle` "My last log is him sitting right there, talking to me about nothing. Then my cell ran dry. Now he's like that, and the thing in the cradle says 'Transfers logged: 1'."; `wand_hand`, the same with "the thing in your hand says"; `wand_other`, ruling 2 | ask "Transferred where?" sets `asked_transfer`; kind "I'm sorry." +10; blunt "What does the wand do?"; sarcastic "Did you two fall out?" -10. All continue to `fm.end` after her answer |
| `fm.doors` | Thinking "He'd look at a door and it stopped. I'd look at a door and it kept going. Apparently I don't count." | ask "Count as what?"; kind "You count to me." +10; sarcastic "Don't take it personally." Each ends on her answer |
| `fm.exit` | Neutral "Before we go up. There are things up there that take buildings apart. Tall. Blue eyes. Very good at their jobs. I'll tell you what I see." | None |

Her answers to each reply are in Notion, Part 1, word for word, except the split in ruling 10. `fm.name.aurora`, `fm.name.its` and `fm.name.earn` now lead to `fm.room` instead of `fm.end`.

- [ ] **Step 1: Write the failing tests.** In `ShepherdConversationTest`: `aNodeThatDoesNotHoldGivesWayToItsElse` (from `fm.name.earn` with nothing holding, a click lands on `fm.end`); `aNodeThatHoldsIsEntered` (with `woke_in_vault` it lands on `fm.room`); `lineConditionsPickOneLogLine` (each of the three wand conditions gives two lines, the second that condition's); `anElseIsNotEarned` (a skipped node's `set` is absent from `flags()`). In `ShepherdDialogueDataTest`: extend `everyNextResolves` to `else`, extend `everyPathEnds` to walk `else`, and add `everyWhenHasAnElse` and `everyConditionIsKnown` (only the four names above or a flag some node sets).
- [ ] **Step 2:** `./gradlew test --tests '*Shepherd*'`. Expected: fail to compile.
- [ ] **Step 3:** Parse the new fields, change `move` and the constructor, add `lines()`, then add the nodes and lang keys `shepherd.fm.room.*`, `shepherd.fm.doors.*`, `shepherd.fm.exit`. Change `ShepherdTalk` only as far as it needs to compile, with `c -> false`.
- [ ] **Step 4:** Run it again. Expected: pass, with S1's tests unchanged apart from the new constructor argument.
- [ ] **Step 5:** Commit, `feat: let her dialogue depend on the world, and write the vault's nodes`.

### Task 4: `fm.room` in the world

**Files:**
- Modify: `src/main/java/com/frozendawn/shepherd/ShepherdTalk.java`, `ShepherdDockBlockEntity.java`, `ShepherdSavedData.java`
- Create: `src/main/java/com/frozendawn/shepherd/ShepherdWand.java`
- Test: new `src/test/java/com/frozendawn/shepherd/ShepherdWandTest.java`, `ShepherdVaultGameTest.java`, `ShepherdTalkGameTest.java` (its two `payload` calls)

**Interfaces:**
- Consumes: the conversation and conditions from Task 3; `HelionSiteState.vault()`, `TerminusVaultBuilder.CRADLE`, `VaultCradleBlockEntity.holding()`, `TerminusLedger.turned(server)`, `ModDataComponents.TERMINUS_LOG`.
- Produces: `ShepherdWand.Where {CRADLE, HAND, OTHER}` and `static Where where(boolean cradled, TerminusLog inHand, boolean turned)`, where `inHand` may be null. `CRADLE` if cradled and not turned; `HAND` if `inHand.logged() == 1` and not turned; else `OTHER`. A wand held by the speaker counts anywhere in their inventory.
- Produces: `ShepherdSavedData.WOKE_IN_VAULT = "woke_in_vault"`, set in `ShepherdDockBlockEntity.wake` when the block is `VAULT_CS1_DOCK`. `replayFirstMeeting` keeps it through its clear.
- Changes: `ShepherdTalk.payload(int conversation, Node node, List<Line> lines, boolean heavy, int logoTicks)`. Every use of `current().lines()` in `ShepherdTalk` becomes `talk.lines()`, or her body's mood walks lines the client never got.

- [ ] **Step 1: Write the failing tests.** `ShepherdWandTest`: the found wand in the cradle or hand gives `CRADLE` and `HAND`; a log of 2 gives `OTHER`; turned gives `OTHER` for both; nothing cradled and nothing held gives `OTHER`. GameTests:
  - `shepherdVaultDockLeadsToTheRoom`: wake on `VAULT_CS1_DOCK`, answer through to `fm.name.its`, take "Aurora it is", assert `liveNode` is `fm.room`, then that any reply reaches `fm.end`.
  - `shepherdCreativeDockSkipsTheRoom`: the same on `CS1_DOCK` reaches `fm.end` directly.
  - `shepherdTheWandInHandChangesTheLine`: speaker holds a found Terminus; the node's second line is the hand key.
  - `shepherdAUsedWandIsNotQuoted`: speaker holds `new TerminusLog(2, 2)`; the second line is the other key.
  - `shepherdEscResumesTheRoom`: close on `fm.room`, right-click, assert `fm.room`.
  - `shepherdReplayStillReachesTheRoom`: `/fd shepherd replay` after a vault wake keeps `woke_in_vault`.
- [ ] **Step 2:** `./gradlew test --tests '*ShepherdWandTest'` and `./gradlew gameTestQuick -PfdTests=shepherdvaultdock,shepherdcreativedock,shepherdthewand,shepherdausedwand,shepherdescresumestheroom,shepherdreplaystill`. Expected: fail.
- [ ] **Step 3:** Add `private static boolean holds(ServerPlayer player, String condition)` to `ShepherdTalk`: the three wand names through `ShepherdWand`, everything else through `data.flag`. Pass it to the conversation in `begin`. Set the flag in `wake`, keep it in `replayFirstMeeting`, thread `lines()` through.
- [ ] **Step 4:** Run the same commands, then `-PfdTests=shepherd`. Expected: pass.
- [ ] **Step 5:** Add the test names to the required list. Commit, `feat: she talks about Enoch when woken in the vault`.

### Task 5: The prompt, on the server

**Files:**
- Create: `src/main/java/com/frozendawn/shepherd/ShepherdPrompt.java`, `src/main/java/com/frozendawn/network/ShepherdPromptPayload.java`
- Modify: `ShepherdPolicy.java`, `ShepherdSavedData.java`, `ShepherdTalk.java`, `network/ModNetworking.java`, `network/ClientHandlers.java` (an empty handler until Task 6)
- Test: `src/test/java/com/frozendawn/shepherd/ShepherdPolicyTest.java`, `ShepherdVaultGameTest.java`

**Interfaces:**
- Produces, in `ShepherdPolicy`: `HEARD_BLOCKS = 16`, `CALM_HOSTILE_BLOCKS = 24`, `CALM_HURT_TICKS = 600`, `PROMPT_REPEAT_TICKS = 1200`, `PROMPT_SHOWS = 3`; `boolean calm(double nearestHostile, int ticksSinceHurt)`; `boolean atLastDoor(int x, int y, int z)`, hall-relative, x 96 to 100, z -3 to 3, y `FLOOR + 1` to `CEILING - 1`; `boolean atVaultExit(int x, int y, int z)`, x and z -2 to 2, y `FLOOR + 1` to 0, which is the landing and the ladder. Use only `TerminusVaultBuilder`'s int constants, so JUnit does not load the class.
- Produces, in `ShepherdSavedData`: `String promptNode()` and its setter, saved, cleared by `replayFirstMeeting` and `clearForTests`, shown in `describe()`.
- Produces: `ShepherdPrompt`, an `@EventBusSubscriber` on `ServerTickEvent.Post` and `LivingDamageEvent.Post`, with `static final String DOORS = "fm.doors", EXIT = "fm.exit"`, the saved flags `doors_asked` and `exit_asked`, and `static int shows()`, `static void repeatEvery(int ticks)` and `static void clearForTests()` as seams. `ShepherdTestWorld.clear` calls the latter.
- Produces: `ShepherdPromptPayload()`, a unit payload to the client, like `ShepherdClosePayload`.

Every 5 ticks, 1.4 blocks at a sprint so nobody crosses a box unseen: nothing happens unless she is `AWAKE`, `woke_in_vault` and `fm_done` hold, the world has a vault, and she is loaded in the overworld and not Offline. A non-spectator player within `HEARD_BLOCKS` of her inside a box, whose flag is unset, sets the flag, sets `promptNode` and resets the showings. Later replaces earlier (ruling 4). Requiring `fm_done` means a first meeting left open cannot be interrupted, and a moment missed that way does not fire.

Showing: while `promptNode` is set, `shows < PROMPT_SHOWS`, the next-show tick has passed, nobody is talking and she is not Offline, then if the nearest player within `HEARD_BLOCKS` is `calm`, send the payload to every player within `HEARD_BLOCKS` and count it. The count and clocks are not saved, so a reload shows it afresh. `ServerStoppingEvent` clears them.

`ShepherdTalk.openHub` order: the resume node, then `promptNode`, cleared as it begins, then the hub. A prompt beginning sets `resumeNode` the usual way, so Esc on `fm.doors` resumes it for free.

- [ ] **Step 1: Write the failing tests.** In `ShepherdPolicyTest`: `calmNeedsNoHostileAndNoRecentHurt` at 24 and 600 and either side of each; `theDoorBoxCoversTheCotRoomSide` and `theExitBoxCoversTheLadder`, with an inside and an outside point for each edge. GameTests, with the world's vault set by `setVaultForTest` to a hall placed so the box lands in the test area, restored in `@AfterBatch`:
  - `shepherdAtTheLastDoorSheAsks`: awake, vault flags set, player in the box and she beside them: `promptNode` is `fm.doors` and `shows()` is 1.
  - `shepherdAsksAboutTheDoorOnce`: after it is taken, the box again raises nothing.
  - `shepherdAtTheExitTheLineReplacesTheDoors`: an untaken `fm.doors`, then the exit box: `promptNode` is `fm.exit`.
  - `shepherdRightClickTakesThePrompt`: `openHub` shows `fm.doors`, `promptNode` is null, and a second right-click gets the hub.
  - `shepherdEscResumesThePrompt`: close on `fm.doors`, right-click, assert `fm.doors`.
  - `shepherdOfflineOrAwaySheDoesNotAsk`: Offline in the box, then alone in the box with her 20 blocks off: no prompt, flag unset. Back in the box beside her, it fires.
  - `shepherdACreativeWakeNeverAsks`: `woke_in_vault` unset, in the box: nothing.
  - `shepherdAnOpenFirstMeetingIsNotInterrupted`: `fm_done` unset, in the box: nothing.
  - `shepherdAHostileDelaysThePrompt`: a zombie within 24: `promptNode` set, `shows()` 0; zombie gone: 1.
  - `shepherdThePromptStopsAtThree`: `repeatEvery(5)`, assert `shows()` stops at 3, and `openHub` still plays it.
  - `shepherdThePromptSurvivesAReload`: `save` and `load` keep `promptNode`.
- [ ] **Step 2:** `./gradlew test --tests '*ShepherdPolicyTest'` and `./gradlew gameTestQuick -PfdTests=shepherdatthe,shepherdasks,shepherdrightclick,shepherdescresumestheprompt,shepherdoffline,shepherdacreative,shepherdanopen,shepherdahostile,shepherdtheprompt`. Expected: fail.
- [ ] **Step 3:** Implement as above.
- [ ] **Step 4:** Run the same commands, then `-PfdTests=shepherd`. Expected: pass.
- [ ] **Step 5:** Add the test names to the required list. Commit, `feat: she asks for a minute at the last door and the vault exit`.

### Task 6: The strip

**Files:**
- Create: `src/main/java/com/frozendawn/client/ShepherdPromptStrip.java`
- Modify: `client/OrsaHudPanel.java`, `client/ClientEvents.java`, `network/ClientHandlers.java`, `shepherd/ShepherdPolicy.java`, `assets/frozendawn/lang/en_us.json`
- Test: `src/test/java/com/frozendawn/shepherd/ShepherdPolicyTest.java`

**Interfaces:**
- Produces, in `ShepherdPolicy`: `STRIP_HOLD_TICKS = 80`, `STRIP_FADE_TICKS = 20`; `int typedChars(int age, int length)`; `float stripAlpha(int age, int length)`, 1 until typing and hold are over, falling to 0 across the fade.
- Produces: `OrsaHudPanel.frame(g, x, y, width, height, accent, border)`, the panel without its badge. `draw` calls it and adds the badge.
- Produces: `ShepherdPromptStrip.show()`, `clear()` and `render(GuiGraphics, DeltaTracker)`, on a layer from `registerAboveAll`.

As `ambient-lines-mockup.png` draws it: 214 wide, 30 tall, centered, its bottom 54 pixels above the screen's, background `OrsaHudPanel.BACKGROUND`, a 2-pixel `0xFF7DF2C0` edge, her Thinking face at scale 3 through `ShepherdFaceRenderer.draw`, `shepherd.prompt` typed in `VALUE_COLOR`, and `shepherd.prompt.hint` under it in `MUTED_COLOR` once typing ends. A blip every `BLIP_EVERY` characters, as on the talk screen. Age runs on game time, so a paused game holds it. `handleShepherdNode` and `handleShepherdHub` call `clear()`. Nothing draws under `hideGui`.

- [ ] **Step 1: Write the failing tests:** `theStripTypesAtTalkSpeed` and `theStripHoldsThenFades`, at the edges of each stage for a 13-character line.
- [ ] **Step 2:** `./gradlew test --tests '*ShepherdPolicyTest'`. Expected: fail.
- [ ] **Step 3:** Implement. Add `shepherd.prompt`, "Got a minute?", and `shepherd.prompt.hint`, "Right-click her".
- [ ] **Step 4:** Run it again, then `./gradlew build --console=plain`. Expected: pass.
- [ ] **Step 5:** Commit, `feat: draw "Got a minute?" as her strip above the hotbar`.

### Task 7: The playtest world and handoff docs

**Files:**
- Create: `tools/prepare_aurora_vault_playtest.py`
- Modify: `docs/aurora-shepherd.md`, `docs/terminus.md`

**Interfaces:**
- Consumes: `prepare`, `pack_only` from `prepare_power_network_playtest`, and `document_advancements` from `prepare_terminus_vault_playtest`, called unchanged, so the manual opens on the three edited documents. No edits to either file.

Title `Aurora Vault Check`, namespace `aurora_vault_check`, pack `aurora-vault-check`, its own `#built` objective. Survival, Normal, difficulty locked, advancements filtered, as AGENTS.md requires. One site through `/fd terminus vault place`, 32 blocks south of a flat spawn, forceloaded out to x 144. The player starts in the cot room with no cell, so the first run opens the last door the way the game does, on the cot.

Buttons: `[Cot room]`, `[The vault]`, `[Morgue]`, `[Landing: the way up]`, `[A charged cell]`, `[An empty cell]`, `[Night]`, `[Spawn a zombie]`, `[Creative]`, `[Survival]`, `[Reset the vault]`, `[Replay first meeting]`, `[Shepherd dump]`, `[Read state]`. Never overwrites a save.

- [ ] **Step 1:** Write the script. Run `python3 tools/prepare_aurora_vault_playtest.py --source <level.dat> --destination "run-lab/saves/Aurora Vault Check"`. Expected: the save exists, with `preparation.json`.
- [ ] **Step 2:** In `aurora-shepherd.md`, mark S2 built in the opening line and the Status, record the rulings, and fix the Plan section. In `terminus.md`, add the dock and the spare to the vault's list.
- [ ] **Step 3:** Commit, `feat: add the Aurora Vault Check playtest world`.

### Task 8: Gates, review and the owner's pass

- [ ] **Step 1:** `./gradlew architectVerify --console=plain`, in the background. Expected: passes, every required test run once.
- [ ] **Step 2:** One branch review, on **Opus**, because this adds saved state and touches the talk loop other slices depend on. Brief: range `origin/feat/maeve-director..HEAD`, this plan, the spec, the rulings. Fix real findings with a failing test first, list minor ones as deferred, re-run `architectVerify`.
- [ ] **Step 3:** Launch `./gradlew runClientLab -PfdLabWorld='Aurora Vault Check' --console=plain` in the background and read the log while the owner plays. Take their unprompted report first. For the owner to judge: how the dock reads beside Okoye and in the dim room, whether the spare is findable, the flow from `fm.name` through `fm.room` to `fm.end`, the strip's place and rhythm, the marked lines.
- [ ] **Step 4:** Fix what the pass finds, and re-run `architectVerify`. Update the Status with the owner's words.
- [ ] **Step 5:** Offer a PR into `feat/maeve-director`. `architectMonkey` has to pass on the PR head for branch protection. Post both statuses only after the owner approves the merge. Never merge without that approval.

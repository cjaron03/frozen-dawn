# Aurora S1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task, inline. The owner avoids subagents, except the one branch review at the end. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A creative-only Aurora: a dock you wake with a Power Cell, a flying companion that follows and cannot be destroyed, and a Fallout-style talk screen playing the first-meeting tree from data files.

**Architecture:** World truth lives in `ShepherdSavedData`; the entity is a body that can be rebuilt from it. The server owns every conversation: it loads JSON trees, sends one node at a time, and takes back only a slot number. Pure logic (`ShepherdPolicy`, the parser, `ShepherdConversation`) sits apart from Minecraft so JUnit can reach it.

**Tech Stack:** Java 21, NeoForge 1.21.1, JUnit in `src/test`, GameTests in `src/gameTest`.

**Spec:** [aurora-shepherd.md](aurora-shepherd.md), slice S1. Lines come from Notion, "Aurora — Dialogue Script v1", Part 1. Art is `docs/art/aurora-shepherd/`, all approved.

## Global Constraints

- Creative tab and `/give` only: no recipe, loot or world generation, and no edit to any vault file.
- In code she is Shepherd: package `com.frozendawn.shepherd`, entity `frozendawn:cs1_unit`, block `frozendawn:cs1_dock`, data `data/frozendawn/shepherd/`, lang `shepherd.*`, debug `/fd shepherd dump`. `com.frozendawn.aurora` stays the sky. Her display name is "Aurora".
- No `shepherd.*` text contains the word "continuity", any case. She never mentions respawn. No emojis.
- Build what the mockups show: `talk-screen-mockup.png`, `face-moods-mockup.png`, `dock-mockup.png`. The strip in `ambient-lines-mockup.png` is S2.
- Tests cover behavior, not lines. Add every new GameTest to `config/architect-required-tests.txt`.
- Conventional Commits, staged by file name. Never `./gradlew --stop`.

## Review Focus

Each has its test in the owning task.

1. The entity is gone (`/kill @e` handled, but also a discard or a lost chunk): one Aurora must come back, never zero and never two. Task 4.
2. The player presses Esc halfway through the first meeting: she must pick up where she stopped, not replay or skip. Task 5.
3. `/reload`, or a logout, in the middle of a conversation: the conversation ends cleanly and a late reply is ignored. Task 5.
4. A dormant dock is broken in creative: she stays dormant, and another dock can wake her. Task 3.
5. Two players right-click her in the same tick: one conversation, the other gets the Unimpressed face. Task 5.

---

### Task 0: Worktree

- [ ] **Step 1:** `git fetch origin`, then check `git merge-base --is-ancestor origin/feat/maeve-director docs/aurora-shepherd`. If the director has moved on, rebase `docs/aurora-shepherd` onto it first.
- [ ] **Step 2:** `git worktree add /Volumes/SSD/Work/frozen-dawn-aurora-shepherd -b feat/aurora-shepherd docs/aurora-shepherd`, so the design rides with the code. All work happens there, on Java 21.

### Task 1: Dialogue data, parser and first-meeting tree

Refines the spec's node format: a node holds `lines`, because `fm.boot` changes mood mid-node, and a node with no responses continues to `next` on a click.

**Files:**
- Create: `src/main/java/com/frozendawn/shepherd/ShepherdDialogue.java`, `ShepherdFace.java`
- Create: `src/main/resources/data/frozendawn/shepherd/dialogue/first_meeting.json`
- Modify: `src/main/resources/assets/frozendawn/lang/en_us.json`
- Test: `src/test/java/com/frozendawn/shepherd/ShepherdDialogueDataTest.java`

**Interfaces:**
- Produces: `ShepherdFace.Mood` enum `NEUTRAL, AMUSED, UNIMPRESSED, HAPPY, SAD, ALARMED, THINKING, OFFLINE, RECORDING`, with `boolean lit(int x, int y)` and `boolean dim(int x, int y)` on the 8 by 6 grid, cells copied from `make_face_and_talk_mockup.py`.
- Produces: in `ShepherdDialogue`, `record Line(String key, String heavyKey, Mood mood)`, `enum Slot {ASK, KIND, BLUNT, SARCASTIC}`, `record Response(Slot slot, String label, String full, String next, int affinity, List<String> set)`, `record Node(String id, List<Line> lines, List<Response> responses, String next, List<String> set)`, `static List<Node> parse(JsonElement file)`.

```json
{ "id": "fm.boot", "lines": [
    { "line": "shepherd.fm.boot.recording", "mood": "recording" },
    { "line": "shepherd.fm.boot.hi", "mood": "unimpressed" } ],
  "responses": [
    { "slot": "ask", "label": "shepherd.fm.boot.who.label", "full": "shepherd.fm.boot.who.full", "next": "fm.boot.who" } ] }
```

- [ ] **Step 1: Write the failing test.** Reading every file in the dialogue folder and `en_us.json` from the classpath:
  - `everyNextResolves`: each `next` is a node id, and `fm.boot` exists.
  - `everyKeyHasText`: each line, heavy, label and full key is in the lang file.
  - `noSlotTwice`: no node has two responses in a slot.
  - `nothingNamesTheWord`: no `shepherd.*` value contains "continuity", lower-cased.
  - `everyPathEnds`: walking every branch from `fm.boot` reaches a node with no responses and no `next`, and every path passes `fm.name` and `fm.filter`.
- [ ] **Step 2:** `./gradlew test --tests '*ShepherdDialogueDataTest' --console=plain`. Expected: FAIL, no such class.
- [ ] **Step 3:** Implement `ShepherdFace` and `ShepherdDialogue`. `parse` throws `IllegalArgumentException` naming the node on an unknown mood or slot.
- [ ] **Step 4:** Write `first_meeting.json` and its lang keys from Script v1, Part 1: `fm.boot`, `fm.filter`, `fm.name`, `fm.end` only, in that order, skipping `fm.room`, `fm.doors` and `fm.exit`. Each spoken reply is its own node. Affinity values go in as written. `fm.filter` sets `profanity_mild` or `profanity_heavy`, and its Ask reply loops back to it. Where the script gives only a label, the full line is the label, except the spec's "Hi. Are you okay?" and "Great. A talking flashlight."
- [ ] **Step 5:** Run the test. Expected: PASS.
- [ ] **Step 6:** Commit: `feat: add Aurora's dialogue format and first-meeting tree`.

### Task 2: Policy, conversation and saved state

**Files:**
- Create: `src/main/java/com/frozendawn/shepherd/ShepherdPolicy.java`, `ShepherdConversation.java`, `ShepherdSavedData.java`
- Create: `src/main/java/com/frozendawn/command/ShepherdDebugCommand.java`
- Modify: `src/main/java/com/frozendawn/command/FrozenDawnDebugCommand.java` (help topic "shepherd", as "uplink")
- Test: `src/test/java/com/frozendawn/shepherd/ShepherdConversationTest.java`, `ShepherdPolicyTest.java`

**Interfaces:**
- Produces: `ShepherdPolicy` constants `MAX_HEALTH = 40`, `TELEPORT_BLOCKS = 24`, `REBOOT_TICKS = 2400`, `TALK_RANGE = 8`, `DANGER_RANGE = 12`, `TICKS_PER_CHAR = 2`, `BLIP_EVERY = 3`, and `static boolean talkMustClose(double distance, boolean hurt, double nearestHostileOnSpeaker)`.
- Produces: `ShepherdConversation(Map<String, Node> nodes, String start)` with `Node current()`, `boolean answer(int slotOrContinue)` where `-1` means continue, `int affinityDelta()`, `Set<String> flags()`, `boolean finished()`. It returns false and changes nothing on a slot the node does not offer.
- Produces: `ShepherdSavedData.get(MinecraftServer)`, as `UplinkSavedData`. Fields and accessors: `Stage stage()` (`DORMANT`, `AWAKE`), `UUID entity()`, `ResourceKey<Level> dimension()`, `BlockPos lastPos()`, `UUID follower()`, `boolean waiting()`, `boolean lamp()`, `int affinity()`, `boolean flag(String)`, `String resumeNode()`, `boolean heavy()`, each with a setter that calls `setDirty()`. `describe()` for the dump. Affinity clamps to -1000..1000.

- [ ] **Step 1: Write the failing tests.**
  - `answerMovesToTheNextNode`, `slotNotOfferedChangesNothing`, `continueOnlyWorksOnANodeWithoutResponses`.
  - `affinityAddsAcrossAPath`: `fm.boot` Kind then `fm.name` Kind totals 25.
  - `talkClosesOnDistanceHurtOrDanger`: closes at distance over 8, when hurt, when a hostile on the speaker is within 12; stays open at exactly 8 and at 13.
  - `savedDataRoundTrips`: every field survives `save` and `load`.
- [ ] **Step 2:** Run them. Expected: FAIL.
- [ ] **Step 3:** Implement the three classes and `/fd shepherd dump` (operator, read-only).
- [ ] **Step 4:** Run them. Expected: PASS.
- [ ] **Step 5:** Commit: `feat: add Aurora's conversation logic and saved state`.

### Task 3: The dock

**Files:**
- Create: `src/main/java/com/frozendawn/shepherd/ShepherdDockBlock.java`, `ShepherdDockBlockEntity.java`
- Create: `assets/frozendawn/blockstates/cs1_dock.json`, its block and item models, `docs/art/aurora-shepherd/make_textures.py` and the textures it writes
- Modify: `init/ModBlocks.java`, `init/ModBlockEntities.java`, `init/ModItems.java` (block item and creative tab beside `POWER_CELL`), lang
- Test: `src/gameTest/java/com/frozendawn/shepherd/ShepherdDockGameTest.java`

**Interfaces:**
- Consumes: `ShepherdSavedData`, `PowerCellItem.energy(ItemStack)`.
- Produces: block property `STATE`, enum `DockState {FOUND, BOOT, EMPTY, DARK}`. `FOUND` is red light and empty slot, `BOOT` green with the cell in, `EMPTY` is the bare dock, `DARK` a dock placed while she is awake. Geometry is `make_dock_mockup.dock()`.
- Produces: `ShepherdDockBlockEntity.wake(ServerPlayer)`: spends the cell, sets `BOOT`, and 20 ticks later calls `ShepherdEntity.spawnFrom(dock, player)` (Task 4), sets `EMPTY` and opens `fm.boot` (Task 5). Until those tasks land it stops at `BOOT` and sets `stage` to `AWAKE`.

- [ ] **Step 1: Write the failing GameTests,** batch `shepherd`, setting up with a fresh `stage` as a shard may be of any age:
  - `chargedCellWakesAndIsSpent`: a survival player with one cell at 1000 J; afterwards the stack is empty and `stage` is `AWAKE`.
  - `emptyCellIsRefused`: a cell at 0 J stays in hand, `stage` stays `DORMANT`, state stays `FOUND`.
  - `secondDockStaysDark`: placed while `AWAKE`, it reads `DARK` and refuses a charged cell.
  - `brokenDormantDockLeavesHerDormant`: destroy a `FOUND` dock; `stage` is `DORMANT`, and a new dock reads `FOUND` and wakes.
- [ ] **Step 2:** `./gradlew gameTestQuick -PfdTests=shepherd --console=plain`. Expected: FAIL.
- [ ] **Step 3:** Implement. Empty cell plays `block.cs1_dock.refuse`. The Lantern on a `FOUND` or `BOOT` dock is drawn by a block entity renderer in Task 6; until then the dock shows alone.
- [ ] **Step 4:** Run them. Expected: PASS.
- [ ] **Step 5:** Commit: `feat: add the CS-1 dock`.

### Task 4: The unit

**Files:**
- Create: `src/main/java/com/frozendawn/shepherd/ShepherdEntity.java`, `ShepherdWatch.java`, `ShepherdLamp.java`
- Modify: `init/ModEntities.java`, `init/ModEntityAttributes.java`, `world/HeldLanternLights.java`
- Test: `src/gameTest/java/com/frozendawn/shepherd/ShepherdEntityGameTest.java`

**Interfaces:**
- Produces: `ShepherdEntity extends PathfinderMob`, `MobCategory.MISC`, sized 0.75 by 0.8, `fireImmune`, no gravity, `FlyingMoveControl` and `FlyingPathNavigation`. Synced `Mood mood()` and `boolean offline()`. `static ShepherdEntity spawnFrom(BlockPos dock, ServerPlayer waker)`, `void goOffline()`, `void setMood(Mood)`.
- Produces: `ShepherdWatch`, a server tick that once a second finds her by `ShepherdSavedData.entity()`, records `lastPos`, and when `stage` is `AWAKE`, her last chunk is loaded and no such entity exists, respawns her there.
- Produces: `ShepherdLamp.collect(ServerLevel, Map<BlockPos, Integer> desired)`, called in `HeldLanternLights.tickLevel` beside `SuitHeadlamp.collect`, level 14 at her position while `lamp()` is on and she is not offline.

- [ ] **Step 1: Write the failing GameTests:**
  - `lethalDamageGoesOfflineNotDead`, `killCommandGoesOfflineNotDead`, `voidReturnsHerToHerFollower`: `isAlive()` stays true, `offline()` is true, gravity on, the entity is the same UUID.
  - `rebootsAfterTwoMinutes`: set `REBOOT_TICKS` through a test seam of 40; she comes back online at full health, gravity off. Arm the clock after setting game time.
  - `followsPastTheTeleportLine`: follower 30 blocks off in the same dimension; she is within 5 next second. `waitHereHolds`: with `waiting`, she does not move.
  - `aDiscardedBodyComesBack`: `discard()` her; within 40 ticks exactly one `cs1_unit` exists at `lastPos`.
  - `notAWatcher`: `VaultWatch` does not count her facing a target. `terminusLeavesHerAlone`: a discharge with her in the cone leaves her online at full health.
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement. Lethal `hurt`, `kill()` and `onBelowWorld` all route to `goOffline()`, never `die()`; below the world she first teleports to her follower. Offline ignores all damage. She hovers about one block off the ground, 2.5 blocks from the follower, persists, and stays behind when the follower changes dimension or logs out. Add her to neither Terminus tag. Keep the block-position lamp clear of the dock block.
- [ ] **Step 4:** Run. Expected: PASS.
- [ ] **Step 5:** Commit: `feat: add Aurora's body, following and reboot`.

### Task 5: Server conversation and networking

**Files:**
- Create: `src/main/java/com/frozendawn/shepherd/ShepherdDialogues.java` (a `SimpleJsonResourceReloadListener` on `shepherd/dialogue`), `ShepherdTalk.java`
- Create: `network/ShepherdNodePayload.java`, `ShepherdHubPayload.java`, `ShepherdReplyPayload.java`, `ShepherdActionPayload.java`, `ShepherdClosePayload.java`
- Modify: `network/ModNetworking.java`, the mod's event wiring for `AddReloadListenerEvent` (the first in the mod)
- Test: `src/gameTest/java/com/frozendawn/shepherd/ShepherdTalkGameTest.java`

**Interfaces:**
- Produces: `ShepherdTalk.openHub(ServerPlayer, ShepherdEntity)`, `start(ServerPlayer, String node)`, `reply(ServerPlayer, int conversation, int slot)`, `action(ServerPlayer, int conversation, Action)`, `close(ServerPlayer)`, with `enum Action {TALK, FOLLOW, LAMP, SETTINGS}`. One live conversation in the world, with a rising id.
- Produces: to client, `ShepherdNodePayload(int conversation, String node, List<Line> lines, List<Reply> replies, boolean canContinue)`, `ShepherdHubPayload(int conversation, boolean waiting, boolean lamp, boolean heavy)`, `ShepherdClosePayload()`. To server, `ShepherdReplyPayload(int conversation, int slot)`, `ShepherdActionPayload(int conversation, Action action)`, `ShepherdClosePayload()`.

- [ ] **Step 1: Write the failing GameTests:**
  - `wakingOpensTheFirstMeeting`: after `wake`, the live node is `fm.boot`.
  - `aReplyForAnotherConversationIsIgnored`, `aSlotNotOfferedIsIgnored`: no state changes.
  - `escResumesTheFirstMeeting`: close at `fm.name`; the next right-click resumes `fm.name` and not the hub. After `fm.end` a right-click opens the hub.
  - `damageClosesAConversation`, `walkingAwayClosesIt`, `reloadEndsItCleanly`, `logoutEndsIt`.
  - `aSecondPlayerGetsUnimpressed`: no second conversation, and her mood is `UNIMPRESSED` for 40 ticks.
  - `affinityAndFlagsSave`: a finished path leaves the score and `profanity_heavy` in `ShepherdSavedData`, and `heavy()` swaps the key sent.
  - `settingsTogglesSwearing`, `followAndLampActionsSave`.
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement. Right-click (`mobInteract`) calls `openHub`. A tick checks `ShepherdPolicy.talkMustClose`, with `LivingDamageEvent.Post` feeding `hurt`. Each node sent sets her mood to its last line's. Replies are sent as slot numbers only. Resume writes `resumeNode` on each node until `fm.end`. Wire `wake` to `start(player, "fm.boot")`.
- [ ] **Step 4:** Run. Expected: PASS.
- [ ] **Step 5:** Commit: `feat: run Aurora's conversations on the server`.

### Task 6: What the player sees and hears

**Files:**
- Create: `client/ShepherdModel.java`, `ShepherdRenderer.java`, `ShepherdDockRenderer.java`, `ShepherdFaceRenderer.java`, `ShepherdScreen.java`
- Create: `sounds/` entries for `entity.shepherd.blip`, `block.cs1_dock.boot`, `block.cs1_dock.refuse`
- Modify: `client/ClientEvents.java` (renderers, layer), `network/ClientHandlers.java`, `init/ModSounds.java`, `sounds.json`, `docs/art/aurora-shepherd/make_textures.py`

**Interfaces:**
- Consumes: the payloads of Task 5, `ShepherdFace.Mood`, `ShepherdPolicy` timing.
- Produces: `ShepherdFaceRenderer.draw(GuiGraphics, Mood, int mouthFrame, boolean blink, int x, int y, int scale)` for the screen, and `drawOnModel(PoseStack, MultiBufferSource, Mood, int mouthFrame, boolean blink)`, full bright, for the entity and the dock.

No automated test: this is judged by eye and ear.

- [ ] **Step 1:** Model the Lantern from `make_body_directions.lantern()`, box for box, and draw it on `FOUND` and `BOOT` docks (Offline and Recording faces).
- [ ] **Step 2:** `ShepherdScreen`, non-pausing, as `talk-screen-mockup.png`: face on top at scale 10, typed line, 2 by 2 slots with tone over label, hover shows the full line, keys 1 to 4, Esc closes. The hub is the same grid (Talk, Follow or Wait here, Lamp) with a small Settings button beneath it. "What do you see?" joins in S5. A click finishes a typing line; a second moves to the node's next line, then continues.
- [ ] **Step 3:** Mouth steps shut, open, wide while typing and shuts on punctuation. Idle blink. Recording has dimmed eyes and no mouth. A blip every `BLIP_EVERY` characters.
- [ ] **Step 4:** Make the three sounds with `tools/generate_shepherd_sounds.sh`, on the pattern of `tools/generate_sever_lash_sounds.sh`, and offer the owner candidates by ear before calling them final.
- [ ] **Step 5:** `./gradlew build --console=plain`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 6:** Commit: `feat: draw Aurora and her talk screen`.

### Task 7: Playtest, gates and handback

**Files:**
- Create: `tools/prepare_shepherd_playtest.py`
- Modify: `config/architect-required-tests.txt`, `docs/aurora-shepherd.md`

- [ ] **Step 1:** `tools/prepare_shepherd_playtest.py`, world **Shepherd Check**: flat, Creative, three docks, a chest with charged cells and one empty cell, and a stretch to walk her along. As AGENTS.md requires, advancement popups suppressed, difficulty preselected, never overwriting a save. Base it on `prepare_uplink_playtest.py`.
- [ ] **Step 2:** `./gradlew architectVerify --console=plain`, in the background. Expected: every test passes and every required case is verified. Run `architectMonkey` before any merge: branch protection needs both statuses on the exact head.
- [ ] **Step 3:** Record the result, active save and head in `docs/aurora-shepherd.md`. Commit: `docs: record Aurora S1 as built`.
- [ ] **Step 4:** Branch review: one subagent on Opus, because the branch adds a save format and client/server sync. Brief it with the branch range, `docs/aurora-shepherd.md`, this plan and the owner's rulings. Fix real findings yourself, each with a failing test first; list minor ones as deferred.
- [ ] **Step 5:** Launch `./gradlew runClientLab -PfdLabWorld='Shepherd Check' --console=plain` in the background and read the log while the owner plays. Their unprompted description comes first. Mood legibility at play distance is the first thing to ask about.
- [ ] **Step 6:** Fix what the pass finds, then offer a PR into `feat/maeve-director`. The owner approves every merge.

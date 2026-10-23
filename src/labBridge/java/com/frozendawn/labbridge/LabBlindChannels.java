package com.frozendawn.labbridge;

import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.blind.BlindSignalMemory.Sense;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.ScoreHolder;

/** Passive channel and posture comparisons. Geometry isolates senses; controller settings stay ordinary. */
public final class LabBlindChannels {
    private static final Gson JSON = new Gson();
    private static final String ACTOR_KEY = "BlindLabChannelActor";
    private static final AABB ROOM = new AABB(0, -49, -12, 31, -37, 11);
    private static final Vec3 ACTOR_START = new Vec3(9.5, -44, 5.5);
    private static final Vec3 PLAYER_START = new Vec3(17.5, -44, 5.5);
    private static final int LIMIT = 480;
    private LabBlindChannels() { }

    private static int score(CommandSourceStack source, String holder) {
        var objective = source.getServer().getScoreboard().getObjective("blind_vib");
        if (objective == null) return -1;
        var value = source.getServer().getScoreboard().getPlayerScoreInfo(ScoreHolder.forNameOnly(holder), objective);
        return value == null ? -1 : value.value();
    }
    private static void score(CommandSourceStack source, String holder, int value) {
        var board = source.getServer().getScoreboard();
        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder), board.getObjective("blind_vib")).set(value);
    }
    private static ServerPlayer participant(CommandSourceStack source) {
        int run = score(source, "#run");
        return LabBlindWorkbench.access(source) == 1 && ((run >= 12 && run <= 17) || (run >= 20 && run <= 35)) && score(source, "#case") == run
                && source.getEntity() instanceof ServerPlayer p && p.getTags().contains("blind_lab_player") ? p : null;
    }
    private static ArchitectEntity actor(CommandSourceStack source, ServerPlayer player) {
        if (!player.getPersistentData().hasUUID(ACTOR_KEY)) return null;
        var entity = source.getLevel().getEntity(player.getPersistentData().getUUID(ACTOR_KEY));
        return entity instanceof ArchitectEntity a && a.isBlindArchitect() && a.getTags().contains("blind_lab_actor") ? a : null;
    }
    private static Path directory(CommandSourceStack source, ServerPlayer player) {
        return source.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-lab-results/run-"
                + score(source, "#run") + "-" + player.getPersistentData().getUUID(ACTOR_KEY));
    }
    private static boolean geometry(CommandSourceStack source) {
        int originalRun = score(source, "#run"); int run = originalRun;
        if (run >= 34) run=run==34?14:15; else if (run >= 32) run = run == 32 ? 12 : 13; else if (run == 31) run = 16; else if (run >= 22) run = 14; else if (run >= 20) run = 16;
        var level = source.getLevel();
        if (level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return false;
        for (int x = 8; x <= 11; x++) for (int z = 3; z <= 7; z++) {
            if (!level.getBlockState(new BlockPos(x, -45, z)).is(Blocks.STONE)) return false;
            for (int y = -44; y <= -42; y++) {
                var expected = Blocks.GLASS;
                if ((run <= 13 || originalRun >= 34 ? x == 9 && z == 5 : x >= 9 && x <= 10 && z >= 4 && z <= 6) && y <= -43) expected = Blocks.AIR;
                if (x == 9 && z == 5 && y == -42) expected = Blocks.SEA_LANTERN;
                if (run <= 13 && x == 9 && z == 5 && y == -42) expected = run == 12 ? Blocks.AIR : Blocks.GLASS;
                if (run == 16 && x == 11 && z >= 4 && z <= 6 && y == -43)
                    expected = originalRun == 31 && !echoOpen(score(source, "#timer")) ? Blocks.GLASS : Blocks.AIR;
                if (!level.getBlockState(new BlockPos(x, y, z)).is(expected)) return false;
            }
        }
        for (int x = 12; x <= 19; x++) for (int z = 3; z <= 7; z++) {
            var expected = (run <= 13 ? x >= 13 && x <= 16 : x >= 16 || run == 14) ? Blocks.STONE : Blocks.AIR;
            if (originalRun == 30 && x >= 16) expected = Blocks.WHITE_WOOL;
            if (originalRun == 31) expected = Blocks.AIR;
            if (!level.getBlockState(new BlockPos(x, -45, z)).is(expected)) return false;
        }
        if (run <= 13) for (int x = 9; x <= 12; x++) for (int y = -42; y <= -40; y++) for (int z = 4; z <= 6; z++) {
            var expected = y == -41 && z == 5 || run == 12 && x == 9 && y == -42 && z == 5 ? Blocks.AIR : Blocks.GLASS;
            if (!level.getBlockState(new BlockPos(x, y, z)).is(expected)) return false;
        }
        return (originalRun<34 || level.getBlockState(new BlockPos(18,-43,5)).is(Blocks.OBSIDIAN)
                && level.getBlockState(new BlockPos(18,-44,5)).is(Blocks.OBSIDIAN)) && (run > 13 || (level.getBlockState(new BlockPos(14, -43, 5)).is(Blocks.OBSIDIAN)
                && level.getBlockState(new BlockPos(14, -44, 5)).is(Blocks.OBSIDIAN)));
    }
    private static Vec3 playerStart(int run) { return run <= 13 || run >= 32 && run <= 33 ? new Vec3(13.5, -44, 5.5) : PLAYER_START; }
    private static boolean crouched(int run) { return run < 29 && (run % 2 == 1 || run == 24 || run == 26 || run == 28); }
    private static Sense channel(int run) { return run >= 34 ? Sense.VIBRATION : run <= 13 || run >= 32 ? Sense.HEARING : (run <= 15 || run >= 22 && run <= 30) ? Sense.VIBRATION : Sense.ECHO; }
    private static boolean echoOpen(int timer) { return timer <= 120 || timer > 360; }
    private static String echoPhase(int timer) { return timer <= 80 ? "OPEN_STILL" : timer <= 120 ? "OPEN_MOVE"
            : timer <= 300 ? "COVERED_MOVE" : timer <= 360 ? "COVERED_STILL" : "REOPENED_STILL"; }
    private static JsonObject frame(CommandSourceStack source, ServerPlayer player, ArchitectEntity actor) {
        var value = new JsonObject(); int run = score(source, "#run");
        value.addProperty("run", run); value.addProperty("case", run); value.addProperty("channel", channel(run).name());
        value.addProperty("timer", score(source, "#timer")); value.addProperty("gameTime", source.getLevel().getGameTime());
        value.addProperty("actor", player.getPersistentData().getUUID(ACTOR_KEY).toString());
        value.addProperty("actorPresent", actor != null);
        if (actor != null) {
            value.addProperty("actorAlive", actor.isAlive()); value.addProperty("actorHealth", actor.getHealth());
            value.add("actorPosition", JSON.toJsonTree(actor.position())); value.addProperty("actorOnGround", actor.onGround());
            value.addProperty("actorNoAi", actor.isNoAi()); value.addProperty("headYaw", actor.getYHeadRot());
            value.addProperty("bodyYaw", actor.yBodyRot); value.addProperty("navigationDone", actor.getNavigation().isDone());
            value.addProperty("diagnostics", actor.blindController().diagnostics());
            value.add("selected", JSON.toJsonTree(actor.blindController().signal()));
            value.add("tracks", JSON.toJsonTree(actor.blindController().tracks()));
            value.add("vibration", JSON.toJsonTree(actor.blindController().lastVibration()));
            value.add("echo", JSON.toJsonTree(actor.blindController().lastEcho()));
            value.addProperty("vanillaTargetPresent", actor.getTarget() != null);
            value.addProperty("journalEntries", actor.decisionJournal().entries().size());
            value.addProperty("journalDropped", actor.decisionJournal().dropped());
        }
        value.add("playerPosition", JSON.toJsonTree(player.position()));
        value.addProperty("playerHealth", player.getHealth()); value.addProperty("playerAlive", player.isAlive());
        value.addProperty("playerOnGround", player.onGround()); value.addProperty("playerSprinting", player.isSprinting());
        value.addProperty("playerSneaking", player.isShiftKeyDown());
        if (run >= 20) {
            value.addProperty("playerPose", player.getPose().name());
            value.addProperty("playerHeight", player.getBbHeight());
            value.addProperty("expectedPosture", crouched(run) ? "CROUCHING" : "STANDING");
            value.addProperty("postureScored", score(source, "#timer") > 80);
        }
        if (run >= 22 && run <= 30) {
            value.addProperty("walkingWindow", score(source, "#timer") > 80 && score(source, "#timer") <= 240);
            value.addProperty("walkingDistance", player.getPersistentData().getDouble("BlindLabWalkingDistance"));
            if (run >= 29) value.addProperty("walkingSurface", run == 30 ? "WHITE_WOOL" : "STONE");
        }
        if (run == 31) {
            value.addProperty("echoPhase", echoPhase(score(source, "#timer")));
            value.addProperty("apertureOpen", echoOpen(score(source, "#timer")));
            value.addProperty("playerFlying", player.getAbilities().flying);
            value.addProperty("playerMayFly", player.getAbilities().mayfly);
            value.addProperty("playerNoGravity", player.isNoGravity());
            value.addProperty("movingDistance", player.getPersistentData().getDouble("BlindLabWalkingDistance"));
        }
        value.addProperty("playerMode", player.gameMode.getGameModeForPlayer().getName());
        if (run >= 25 && player.getPersistentData().hasUUID("BlindLabInputProbe"))
            value.addProperty("inputProbe", player.getPersistentData().getUUID("BlindLabInputProbe").toString());
        value.add("isolation", LabBlindIsolation.snapshot(source.getLevel()));
        value.addProperty("geometryIntact", geometry(source));
        value.addProperty("inconclusiveFrames", score(source, "#inconclusive"));
        return value;
    }
    public static int start(CommandSourceStack source) {
        var player = participant(source);
        if (player == null || !(player.isCreative() || score(source,"#run")>=34 && player.gameMode.getGameModeForPlayer()==GameType.SURVIVAL) || score(source, "#stage") != 1
                || score(source, "#timer") != 0 || score(source, "#frames") != 0 || !geometry(source)
                || player.position().distanceToSqr(playerStart(score(source, "#run"))) > .01
                || score(source, "#run") >= 32 && Math.abs(player.getY() + 44) > .0001 || !LabBlindIsolation.enabled(source.getLevel())
                || !LabBlindIsolation.unrelated(source.getLevel()).isEmpty() || !LabBlindIsolation.sourcesClean(source.getLevel())
                || com.frozendawn.event.MobFreezeHandler.getFullSetTier(player) != 3
                || player.getInventory().items.stream().noneMatch(stack -> stack.getItem() instanceof com.frozendawn.item.O2TankItem
                    && stack.getOrDefault(com.frozendawn.init.ModDataComponents.O2_LEVEL.get(), 0) >= LIMIT)) return 0;
        var actors = source.getLevel().getEntitiesOfClass(ArchitectEntity.class, ROOM);
        if (actors.size() != 1) return 0;
        var actor = actors.getFirst();
        if (!actor.isAlive() || !actor.isBlindArchitect() || !actor.getTags().contains("blind_lab_actor")
                || !actor.isNoAi() || !actor.onGround() || actor.position().distanceToSqr(ACTOR_START) > .001
                || !actor.blindController().tracks().isEmpty()) return 0;
        if (score(source, "#run") >= 25 && (!player.getPersistentData().hasUUID("BlindLabInputProbe")
                || !player.getPersistentData().hasUUID("BlindLabInputActor")
                || !player.getPersistentData().getUUID("BlindLabInputActor").equals(actor.getUUID()))) return 0;
        if(score(source,"#run")>=34 && (!player.onGround() || !player.getPersistentData().hasUUID("BlindLabGroundCheck")
                || !LabGroundCheck.retained(source.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-lab-ground"),
                    player.getPersistentData().getUUID("BlindLabGroundCheck"),player.getUUID(),actor.getUUID(),score(source,"#run")))) return 0;
        player.getPersistentData().putUUID(ACTOR_KEY, actor.getUUID());
        if (score(source, "#run") >= 22) {
            player.getPersistentData().putDouble("BlindLabWalkingDistance", 0);
            player.getPersistentData().putDouble("BlindLabWalkingX", player.getX());
            player.getPersistentData().putDouble("BlindLabWalkingZ", player.getZ());
        }
        if (score(source, "#run") == 31) {
            player.getPersistentData().putBoolean("BlindLabEchoOldNoGravity", player.isNoGravity());
            player.setNoGravity(true); player.getAbilities().flying = true;
            player.getAbilities().mayfly = true; player.setOnGround(false); player.onUpdateAbilities();
        }
        try {
            var directory = directory(source, player); Files.createDirectories(directory);
            var initial = frame(source, player, actor);
            initial.addProperty("world", source.getServer().getWorldData().getLevelName());
            Files.writeString(directory.resolve("start.json"), JSON.toJson(initial) + "\n", StandardOpenOption.CREATE_NEW);
            Files.writeString(directory.resolve("trace.jsonl"), "", StandardOpenOption.CREATE_NEW);
        } catch (java.io.IOException error) {
            source.sendFailure(Component.literal("Channel evidence setup failed. Preserve this scene; do not retry.")); return 0;
        }
        // Diagnostics only: null seed preserves the ordinary random sequence.
        actor.startDecisionRecording(null); actor.decisionJournal().useExtendedLabBuffer();
        score(source, "#stage", 2); score(source, "#last_frame", 0);
        player.getPersistentData().putLong("BlindLabChannelLastTick", source.getLevel().getGameTime() - 1);
        player.setHealth(player.getMaxHealth()); player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5); if(player.isCreative()) player.setGameMode(GameType.SURVIVAL);
        if (score(source, "#run") >= 32 && score(source,"#run")<=33) {
            // The checked stone platform actually touches the feet at y-44; clear the old hover flag before tick1.
            player.setNoGravity(false); player.setOnGround(true); player.getAbilities().flying = false; player.getAbilities().mayfly = false;
            player.onUpdateAbilities();
        }
        actor.setNoAi(false);
        if (score(source, "#run") == 31) {
            player.getAbilities().mayfly = true; player.getAbilities().flying = true;
            player.setOnGround(false); player.onUpdateAbilities();
        }
        return 1;
    }
    public static int tick(CommandSourceStack source) {
        var player = participant(source);
        if (player == null || score(source, "#stage") != 2 || !player.getPersistentData().hasUUID(ACTOR_KEY)) return 0;
        long now = source.getLevel().getGameTime();
        if (now <= player.getPersistentData().getLong("BlindLabChannelLastTick")) return 0;
        player.getPersistentData().putLong("BlindLabChannelLastTick", now);
        int timer = score(source, "#timer") + 1, run = score(source, "#run");
        if (timer < 1 || timer > LIMIT) return 0;
        score(source, "#timer", timer); var actor = actor(source, player);
        // The lab shutter changes geometry, never the controller's contact or pending returns.
        if (run == 31 && (timer == 121 || timer == 361)) {
            for (int z = 4; z <= 6; z++) source.getLevel().setBlock(new BlockPos(11, -43, z),
                    (timer == 121 ? Blocks.GLASS : Blocks.AIR).defaultBlockState(), 3);
        }
        boolean invalid = !geometry(source) || !LabBlindIsolation.enabled(source.getLevel())
                || !LabBlindIsolation.unrelated(source.getLevel()).isEmpty() || !LabBlindIsolation.sourcesClean(source.getLevel()) || player.isCreative() || player.isSpectator()
                || actor == null || !actor.isAlive() || !player.isAlive() || !ROOM.contains(player.position())
                || !ROOM.contains(actor.position()) || actor.isNoAi() || actor.getTarget() != null;
        if (actor != null && (actor.blindController().tracks().stream().anyMatch(t -> t.sense() != channel(run))
                || actor.decisionJournal().dropped() > 0)) invalid = true;
        if (run <= 13 || run >= 32 || (run >= 16 && run <= 21)) invalid |= player.position().distanceToSqr(playerStart(score(source, "#run"))) > .04;
        else invalid |= player.getX() < 16.3 || player.getX() > 19.7 || player.getZ() < 3.3 || player.getZ() > 7.7
                || Math.abs(player.getY() + 44) > .05;
        // Give the human four seconds to establish the requested posture after unpausing.
        if (run >= 20 && run <= 30 && timer > 80) invalid |= player.getPose() != (crouched(run)
                ? net.minecraft.world.entity.Pose.CROUCHING : net.minecraft.world.entity.Pose.STANDING)
                || player.isShiftKeyDown() != crouched(run);
        if (run >= 22 && run <= 31) {
            var data = player.getPersistentData();
            double distance = Math.hypot(player.getX() - data.getDouble("BlindLabWalkingX"),
                    player.getZ() - data.getDouble("BlindLabWalkingZ"));
            data.putDouble("BlindLabWalkingX", player.getX()); data.putDouble("BlindLabWalkingZ", player.getZ());
            if (timer > 80 && timer <= (run == 31 ? 300 : 240))
                data.putDouble("BlindLabWalkingDistance", data.getDouble("BlindLabWalkingDistance") + distance);
            // An eight-second movement window, then one second to react to STOP.
            invalid |= player.isSprinting() || distance > .6;
            if (timer <= 80) invalid |= player.position().distanceToSqr(PLAYER_START) > .04;
            if (timer > (run == 31 ? 320 : 260)) invalid |= distance > .025;
            if (run == 31) invalid |= !player.getAbilities().flying || !player.getAbilities().mayfly
                    || !player.isNoGravity() || player.onGround() || player.isShiftKeyDown();
            if (run <= 30 && timer == LIMIT) invalid |= data.getDouble("BlindLabWalkingDistance") < 2 || actor == null
                    || actor.decisionJournal().entries().stream().noneMatch(e -> e.event().equals("BLIND_VIBRATION") && e.detail().contains("kind=FOOTSTEP"))
                    || actor.decisionJournal().entries().stream().anyMatch(e -> e.event().equals("BLIND_VIBRATION") && !e.detail().contains("kind=FOOTSTEP"));
            if (run == 31 && timer == LIMIT) invalid |= data.getDouble("BlindLabWalkingDistance") < 2;
        }
        if (run >= 32) {
            invalid |= !player.onGround() || player.isShiftKeyDown() || player.isSprinting();
            if (timer == LIMIT) invalid |= actor == null || actor.decisionJournal().entries().stream()
                    .noneMatch(e -> e.event().equals("BLIND_VIBRATION") && e.detail().contains("kind=TOOL"));
        }
        if (invalid) score(source, "#inconclusive", score(source, "#inconclusive") + 1);
        var value = frame(source, player, actor);
        try {
            Files.writeString(directory(source, player).resolve("trace.jsonl"), JSON.toJson(value) + "\n", StandardOpenOption.APPEND);
            score(source, "#frames", score(source, "#frames") + 1); score(source, "#last_frame", timer);
        } catch (java.io.IOException error) {
            score(source, "#inconclusive", score(source, "#inconclusive") + 1);
            return finish(source, player, actor, value);
        }
        if (timer == LIMIT || actor == null || !actor.isAlive() || !player.isAlive()) return finish(source, player, actor, value);
        return 1;
    }
    private static String hash(Path path) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
    private static int finish(CommandSourceStack source, ServerPlayer player, ArchitectEntity actor, JsonObject summary) {
        summary.addProperty("frames", score(source, "#frames"));
        summary.addProperty("complete", score(source, "#frames") == LIMIT && score(source, "#inconclusive") == 0);
        if (actor != null) summary.addProperty("receiverNbt", actor.saveWithoutId(new net.minecraft.nbt.CompoundTag()).toString());
        if(score(source,"#run")>=34 && summary.get("complete").getAsBoolean()) {
            try {
                var frames=Files.readAllLines(directory(source,player).resolve("trace.jsonl")).stream()
                        .map(line->com.google.gson.JsonParser.parseString(line).getAsJsonObject()).toList();
                var decisions=new java.util.ArrayList<JsonObject>();
                JSON.toJsonTree(actor.decisionJournal().entries()).getAsJsonArray().forEach(entry->decisions.add(entry.getAsJsonObject()));
                if(!LabHearingWindow.qualifiesMiningView(summary,frames,decisions)) {
                    score(source,"#inconclusive",score(source,"#inconclusive")+1);
                    summary.addProperty("inconclusiveFrames",score(source,"#inconclusive"));summary.addProperty("complete",false);
                    summary.addProperty("miningWindowQualified",false);
                } else summary.addProperty("miningWindowQualified",true);
            } catch(Exception failure) {
                score(source,"#inconclusive",score(source,"#inconclusive")+1);summary.addProperty("inconclusiveFrames",score(source,"#inconclusive"));
                summary.addProperty("complete",false);summary.addProperty("miningWindowQualified",false);
            }
        }
        score(source, "#stage", 3);
        if (actor != null) { actor.getNavigation().stop(); actor.setNoAi(true); }
        player.setGameMode(GameType.CREATIVE);
        if (score(source, "#run") == 31) {
            player.setNoGravity(player.getPersistentData().getBoolean("BlindLabEchoOldNoGravity"));
            player.getPersistentData().remove("BlindLabEchoOldNoGravity");
            player.getAbilities().flying = true; player.onUpdateAbilities();
        }
        try {
            var directory = directory(source, player);
            if (actor != null) {
                Files.writeString(directory.resolve("decisions.json"), JSON.toJson(actor.decisionJournal().entries()) + "\n", StandardOpenOption.CREATE_NEW);
                summary.addProperty("decisionsSha256", hash(directory.resolve("decisions.json")));
                actor.decisionJournal().finish(source.getLevel().getGameTime(), "CAPTURED", "Channel view ended");
            }
            summary.addProperty("traceSha256", hash(directory.resolve("trace.jsonl")));
            Files.writeString(directory.resolve("summary.json"), JSON.toJson(summary) + "\n", StandardOpenOption.CREATE_NEW);
            score(source, "#archived", summary.get("complete").getAsBoolean() ? 1 : 0); return 1;
        } catch (Exception error) {
            source.sendFailure(Component.literal("Channel archive failed. Preserve this scene.")); return 0;
        }
    }
    public static int archived(CommandSourceStack source) { return captured(source, true); }
    public static int capturedForInput(CommandSourceStack source) {
        return score(source, "#run") == 24 ? captured(source, false) : 0;
    }
    public static int retryReady(CommandSourceStack source) {
        return score(source, "#run") == 23 ? captured(source, false) : 0;
    }
    public static int inputRetryReady(CommandSourceStack source) {
        return score(source, "#run") == 24 ? captured(source, false) : 0;
    }
    /** The corrected replay may replace only a fully retained inconclusive input-recorded run25. */
    public static int fixedRetryReady(CommandSourceStack source) { return recordedRetryReady(source, 25); }
    public static int crouchReadyRetry(CommandSourceStack source) { return recordedRetryReady(source, 26); }
    /** Repeat only a retained inconclusive27 whose live readiness and input capture are complete. */
    public static int crouchRepeatReady(CommandSourceStack source) {
        return recordedRetryReady(source, 27) == 1 ? readinessArchive(source, 27) : 0;
    }
    private static int readinessArchive(CommandSourceStack source, int previousRun) {
        var player = participant(source);
        try {
            var root = source.getServer().getWorldPath(LevelResource.ROOT);
            var inputDirectory = root.resolve("blind-lab-input/probe-" + player.getPersistentData().getUUID("BlindLabInputProbe"));
            var inputStart = com.google.gson.JsonParser.parseString(Files.readString(inputDirectory.resolve("start.json"))).getAsJsonObject();
            var inputSummary = com.google.gson.JsonParser.parseString(Files.readString(inputDirectory.resolve("summary.json"))).getAsJsonObject();
            if (!inputSummary.get("captureComplete").getAsBoolean() || inputSummary.get("dropped").getAsInt() != 0
                    || inputSummary.get("clientFrames").getAsInt() != LIMIT) return 0;
            var check = inputStart.getAsJsonObject("crouchCheck");
            var checkId = java.util.UUID.fromString(check.get("check").getAsString());
            var readyDirectory = root.resolve("blind-lab-ready/check-" + checkId);
            var readyStart = com.google.gson.JsonParser.parseString(Files.readString(readyDirectory.resolve("start.json"))).getAsJsonObject();
            var readySummary = com.google.gson.JsonParser.parseString(Files.readString(readyDirectory.resolve("summary.json"))).getAsJsonObject();
            var readyInitial = readyStart.getAsJsonObject("crouchCheck");
            for (var state : java.util.List.of(check, readySummary)) {
                if (!state.get("check").getAsString().equals(checkId.toString())
                        || !state.get("reason").getAsString().equals("CROUCH_CONFIRMED")
                        || state.get("viewRun").getAsInt() != previousRun || state.get("stableTicks").getAsInt() != 40
                        || state.get("sampledTicks").getAsInt() < 40 || state.get("sampledTicks").getAsInt() > 2400
                        || !state.get("actor").equals(inputStart.get("actor"))
                        || !state.get("player").equals(inputStart.get("player"))
                        || !state.get("session").equals(inputStart.get("session"))) return 0;
            }
            if (!readyInitial.get("check").getAsString().equals(checkId.toString())
                    || !readyInitial.get("actor").equals(check.get("actor"))
                    || !readyInitial.get("player").equals(check.get("player"))
                    || !readyInitial.get("session").equals(check.get("session"))
                    || !hash(readyDirectory.resolve("server.jsonl")).equals(readySummary.get("serverSha256").getAsString())) return 0;
            return 1;
        } catch (Exception error) { return 0; }
    }
    /** Advance from a verified stone walking window and complete bound input; preserve the full-view outcome. */
    public static int materialReady(CommandSourceStack source) {
        int previousRun = score(source, "#run");
        if (previousRun != 28 && previousRun != 29) return 0;
        boolean complete = score(source, "#archived") == 1;
        if (previousRun == 28 && !complete || recordedViewReady(source, previousRun, complete) != 1) return 0;
        var player = participant(source); var actor = actor(source, player);
        if (actor == null || !actor.isAlive() || !actor.isNoAi()) return 0;
        try {
            var inputDirectory = source.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("blind-lab-input/probe-" + player.getPersistentData().getUUID("BlindLabInputProbe"));
            var state = com.google.gson.JsonParser.parseString(Files.readString(inputDirectory.resolve("summary.json"))).getAsJsonObject();
            if (!state.get("captureComplete").getAsBoolean() || state.get("dropped").getAsInt() != 0
                    || state.get("clientFrames").getAsInt() != LIMIT) return 0;
            if (previousRun == 28) return readinessArchive(source, 28);
            var replayDirectory = directory(source, player);
            var replay = com.google.gson.JsonParser.parseString(Files.readString(replayDirectory.resolve("summary.json"))).getAsJsonObject();
            var frames = Files.readAllLines(replayDirectory.resolve("trace.jsonl")).stream()
                    .map(line -> com.google.gson.JsonParser.parseString(line).getAsJsonObject()).toList();
            var decisions = new java.util.ArrayList<JsonObject>();
            com.google.gson.JsonParser.parseString(Files.readString(replayDirectory.resolve("decisions.json"))).getAsJsonArray()
                    .forEach(entry -> decisions.add(entry.getAsJsonObject()));
            return LabFootstepWindow.qualifies(replay, frames, decisions) ? 1 : 0;
        } catch (Exception error) { return 0; }
    }
    /** A separate mechanic may follow an archived inconclusive material view without rewriting it. */
    public static int echoMotionReady(CommandSourceStack source) {
        int previousRun = score(source, "#run");
        if ((previousRun != 30 && previousRun != 31)
                || recordedViewReady(source, previousRun, score(source, "#archived") == 1) != 1
                || previousRun == 31 && !LabBlindIsolation.confirmedEchoIsolation(source)) return 0;
        var player = participant(source); var actor = actor(source, player);
        if (actor == null || !actor.isAlive() || !actor.isNoAi()) return 0;
        try {
            var directory = source.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("blind-lab-input/probe-" + player.getPersistentData().getUUID("BlindLabInputProbe"));
            var state = com.google.gson.JsonParser.parseString(Files.readString(directory.resolve("summary.json"))).getAsJsonObject();
            int clientFrames = state.get("clientFrames").getAsInt();
            return state.get("captureComplete").getAsBoolean() && state.get("dropped").getAsInt() == 0
                    && clientFrames >= LIMIT && clientFrames <= 1024
                    && Files.readAllLines(directory.resolve("client.jsonl")).size() == clientFrames
                    && Files.readAllLines(directory.resolve("server.jsonl")).size() == LIMIT ? 1 : 0;
        } catch (Exception error) { return 0; }
    }
    /** New hearing transition qualifies independent clocks; it never relabels old echo repeat readiness. */
    public static int hearingReady(CommandSourceStack source) {
        int run = score(source, "#run");
        if ((run != 31 && run != 32) || recordedViewReady(source, run, true) != 1) return 0;
        var player = participant(source); var actor = actor(source, player);
        if (actor == null || !actor.isAlive() || !actor.isNoAi() || !LabBlindIsolation.enabled(source.getLevel())
                || !LabBlindIsolation.sourcesClean(source.getLevel()) || !LabBlindIsolation.unrelated(source.getLevel()).isEmpty()
                || run == 31 && !LabBlindIsolation.confirmedEchoIsolation(source)) return 0;
        try {
            var directory = source.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("blind-lab-input/probe-" + player.getPersistentData().getUUID("BlindLabInputProbe"));
            var start = com.google.gson.JsonParser.parseString(Files.readString(directory.resolve("start.json"))).getAsJsonObject();
            var state = com.google.gson.JsonParser.parseString(Files.readString(directory.resolve("summary.json"))).getAsJsonObject();
            var server = Files.readAllLines(directory.resolve("server.jsonl")).stream()
                    .map(line -> com.google.gson.JsonParser.parseString(line).getAsJsonObject()).toList();
            var client = Files.readAllLines(directory.resolve("client.jsonl")).stream()
                    .map(line -> com.google.gson.JsonParser.parseString(line).getAsJsonObject()).toList();
            var keys = Files.readAllLines(directory.resolve("keys.jsonl")).stream()
                    .map(line -> com.google.gson.JsonParser.parseString(line).getAsJsonObject()).toList();
            return LabInputTimeline.qualifies(start, state, server, client, keys) ? 1 : 0;
        } catch (Exception failure) { return 0; }
    }
    /** Narrow qualification of the retained legacy32; ordinary complete-predecessor guards stay strict. */
    public static int hearingQualifiedReady(CommandSourceStack source) {
        if (score(source, "#run") != 32 || recordedViewReady(source, 32, false) != 1) return 0;
        return qualifiedMiningWindow(source,false);
    }
    public static int miningReady(CommandSourceStack source) {
        int run=score(source,"#run");
        var player=participant(source); var actor=player==null?null:actor(source,player);
        if(run==34 && (actor==null || !player.getPersistentData().hasUUID("BlindLabGroundCheck")
                || !LabGroundCheck.retained(source.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-lab-ground"),
                    player.getPersistentData().getUUID("BlindLabGroundCheck"),player.getUUID(),actor.getUUID(),run))) return 0;
        return (run==33 || run==34) && recordedViewReady(source,run,run==34)==1 ? qualifiedMiningWindow(source,true):0;
    }
    public static int decoyReady(CommandSourceStack source) {
        if(score(source,"#run")!=35 || recordedViewReady(source,35,true)!=1)return 0;
        var p=participant(source);var a=actor(source,p);
        return a!=null && p.getPersistentData().hasUUID("BlindLabGroundCheck")
                && LabGroundCheck.retained(source.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-lab-ground"),
                    p.getPersistentData().getUUID("BlindLabGroundCheck"),p.getUUID(),a.getUUID(),35)
                ? qualifiedMiningWindow(source,true):0;
    }
    private static int qualifiedMiningWindow(CommandSourceStack source,boolean mining) {
        var player = participant(source); var actor = actor(source, player);
        if (actor == null || !actor.isAlive() || !actor.isNoAi() || !LabBlindIsolation.enabled(source.getLevel())
                || !LabBlindIsolation.sourcesClean(source.getLevel()) || !LabBlindIsolation.unrelated(source.getLevel()).isEmpty()) return 0;
        try {
            var input = source.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("blind-lab-input/probe-" + player.getPersistentData().getUUID("BlindLabInputProbe"));
            var start = com.google.gson.JsonParser.parseString(Files.readString(input.resolve("start.json"))).getAsJsonObject();
            var state = com.google.gson.JsonParser.parseString(Files.readString(input.resolve("summary.json"))).getAsJsonObject();
            java.util.function.Function<Path,java.util.List<JsonObject>> rows = file -> {
                try { return Files.readAllLines(file).stream().map(line -> com.google.gson.JsonParser.parseString(line).getAsJsonObject()).toList(); }
                catch (Exception failure) { return java.util.List.of(); }
            };
            if (!LabInputTimeline.qualifies(start,state,rows.apply(input.resolve("server.jsonl")),
                    rows.apply(input.resolve("client.jsonl")),rows.apply(input.resolve("keys.jsonl")))) return 0;
            var replay = directory(source, player);
            var summary = com.google.gson.JsonParser.parseString(Files.readString(replay.resolve("summary.json"))).getAsJsonObject();
            var decisions = new java.util.ArrayList<JsonObject>();
            com.google.gson.JsonParser.parseString(Files.readString(replay.resolve("decisions.json"))).getAsJsonArray()
                    .forEach(entry -> decisions.add(entry.getAsJsonObject()));
            return (mining ? (score(source,"#run")==35 ? LabHearingWindow.qualifiesMiningView(summary,rows.apply(replay.resolve("trace.jsonl")),decisions) : LabHearingWindow.qualifiesMiningPredecessor(summary,rows.apply(replay.resolve("trace.jsonl")),decisions))
                    : LabHearingWindow.qualifies(summary,rows.apply(replay.resolve("trace.jsonl")),decisions)) ? 1 : 0;
        } catch (Exception failure) { return 0; }
    }
    private static int recordedRetryReady(CommandSourceStack source, int previousRun) {
        return recordedViewReady(source, previousRun, false);
    }
    private static int recordedViewReady(CommandSourceStack source, int previousRun, boolean complete) {
        if (score(source, "#run") != previousRun || captured(source, complete) != 1) return 0;
        var player = participant(source);
        try {
            var replay = com.google.gson.JsonParser.parseString(Files.readString(directory(source, player).resolve("summary.json"))).getAsJsonObject();
            var replayStart = com.google.gson.JsonParser.parseString(Files.readString(directory(source, player).resolve("start.json"))).getAsJsonObject();
            String probe = replay.get("inputProbe").getAsString();
            if (!java.util.UUID.fromString(probe).equals(player.getPersistentData().getUUID("BlindLabInputProbe"))
                    || !probe.equals(replayStart.get("inputProbe").getAsString())) return 0;
            var inputDirectory = source.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("blind-lab-input/probe-" + probe);
            var start = com.google.gson.JsonParser.parseString(Files.readString(inputDirectory.resolve("start.json"))).getAsJsonObject();
            var summary = com.google.gson.JsonParser.parseString(Files.readString(inputDirectory.resolve("summary.json"))).getAsJsonObject();
            if (!probe.equals(start.get("probe").getAsString()) || !probe.equals(summary.get("probe").getAsString())
                    || !start.get("actor").getAsString().equals(replay.get("actor").getAsString())
                    || start.get("viewRun").getAsInt() != previousRun || start.get("serverTickLimit").getAsInt() != LIMIT
                    || summary.get("serverTickLimit").getAsInt() != LIMIT || summary.get("serverFrames").getAsInt() != LIMIT
                    || summary.get("clientFrames").getAsInt() <= 0 || !summary.get("reason").getAsString().equals("TIME_LIMIT")
                    || summary.get("active").getAsBoolean()
                    || !start.get("session").equals(summary.get("session"))
                    || !player.getUUID().toString().equals(start.get("player").getAsString())
                    || !start.get("player").equals(summary.get("player"))) return 0;
            // The old recorder's overflow remains an incomplete capture. Retain and verify
            // its existing bytes rather than treating missing key edges as a complete record.
            for (String name : java.util.List.of("server.jsonl", "client.jsonl", "keys.jsonl"))
                if (!hash(inputDirectory.resolve(name)).equals(summary.getAsJsonObject("hashes").get(name).getAsString())) return 0;
            return 1;
        } catch (Exception error) { return 0; }
    }
    /** Read-only preflight before the runtime attaches the passive recorder and calls ordinary Start. */
    public static java.util.UUID inputViewActor(CommandSourceStack source) {
        var player = participant(source);
        if (player == null || !(player.isCreative() || score(source,"#run")>=34 && player.gameMode.getGameModeForPlayer()==GameType.SURVIVAL) || (score(source, "#run") < 25 || score(source, "#run") > 35) || score(source, "#stage") != 1
                || score(source, "#timer") != 0 || score(source, "#frames") != 0 || !geometry(source)) return null;
        var actors = source.getLevel().getEntitiesOfClass(ArchitectEntity.class, ROOM);
        if (actors.size() != 1) return null;
        var actor = actors.getFirst();
        return actor.isAlive() && actor.isBlindArchitect() && actor.isNoAi() && actor.onGround()
                && actor.getTags().contains("blind_lab_actor") && actor.position().distanceToSqr(ACTOR_START) < .001
                && actor.blindController().tracks().isEmpty() ? actor.getUUID() : null;
    }
    private static int captured(CommandSourceStack source, boolean complete) {
        var player = participant(source);
        if (player == null || !player.isCreative() || score(source, "#stage") != 3 || score(source, "#timer") != LIMIT
                || score(source, "#frames") != LIMIT || score(source, "#last_frame") != LIMIT
                || score(source, "#archived") != (complete ? 1 : 0)
                || (complete ? score(source, "#inconclusive") != 0 : score(source, "#inconclusive") <= 0)) return 0;
        var actor = actor(source, player);
        // An owner may remove a held actor after capture. Validate the archived identity;
        // any surviving actor must still be held before the next scene may replace it.
        if (!player.getPersistentData().hasUUID(ACTOR_KEY)
                || (actor != null && actor.isAlive() && !actor.isNoAi())) return 0;
        try {
            var directory = directory(source, player);
            var summary = com.google.gson.JsonParser.parseString(Files.readString(directory.resolve("summary.json"))).getAsJsonObject();
            var start = com.google.gson.JsonParser.parseString(Files.readString(directory.resolve("start.json"))).getAsJsonObject();
            return summary.get("complete").getAsBoolean() == complete && summary.get("frames").getAsInt() == LIMIT
                    && summary.get("inconclusiveFrames").getAsInt() == score(source, "#inconclusive")
                    && summary.get("run").getAsInt() == score(source, "#run")
                    && start.get("actor").getAsString().equals(player.getPersistentData().getUUID(ACTOR_KEY).toString())
                    && summary.get("actor").getAsString().equals(player.getPersistentData().getUUID(ACTOR_KEY).toString())
                    && start.get("world").getAsString().equals(source.getServer().getWorldData().getLevelName())
                    && summary.get("traceSha256").getAsString().equals(hash(directory.resolve("trace.jsonl")))
                    && summary.get("decisionsSha256").getAsString().equals(hash(directory.resolve("decisions.json"))) ? 1 : 0;
        } catch (Exception error) { return 0; }
    }
}

package com.frozendawn.labbridge;

import com.frozendawn.entity.ArchitectEntity;
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
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.ScoreHolder;

/** Passive capture of one controlled underground base encounter. No sensory inputs or AI orders. */
public final class LabBlindBase {
    private static final Gson JSON = new Gson();
    private static final String ACTOR_KEY = "BlindLabBaseActor";
    private static final AABB ROOM = new AABB(0, -49, -12, 31, -37, 11);
    private static final Vec3 ACTOR_START = new Vec3(9.5, -44, .5);
    private static final Vec3 PLAYER_START = new Vec3(22.5, -44, .5);
    private static final int LIMIT = 1800;
    private LabBlindBase() { }

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
        return LabBlindWorkbench.access(source) == 1 && score(source, "#run") == 11 && score(source, "#case") == 11
                && source.getEntity() instanceof ServerPlayer p && p.getTags().contains("blind_lab_player") ? p : null;
    }
    private static ArchitectEntity actor(CommandSourceStack source, ServerPlayer player) {
        if (!player.getPersistentData().hasUUID(ACTOR_KEY)) return null;
        var entity = source.getLevel().getEntity(player.getPersistentData().getUUID(ACTOR_KEY));
        return entity instanceof ArchitectEntity a && a.isBlindArchitect() && a.getTags().contains("blind_lab_actor") ? a : null;
    }
    private static Path directory(CommandSourceStack source, ServerPlayer player) {
        return source.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-lab-results/run-11-"
                + player.getPersistentData().getUUID(ACTOR_KEY));
    }
    private static boolean insideBase(Vec3 position) {
        return position.x > 16.3 && position.x < 26.7 && position.z > -6.7 && position.z < 5.7
                && position.y >= -44.1 && position.y < -40;
    }
    private static boolean readyGeometry(CommandSourceStack source) {
        var level = source.getLevel();
        for (int z = -4; z <= 2; z++) {
            var slit = level.getBlockState(new BlockPos(15, -43, z));
            if (!slit.is(Blocks.STONE_SLAB) || slit.getValue(SlabBlock.TYPE) != SlabType.BOTTOM
                    || !level.getBlockState(new BlockPos(15, -44, z)).is(Blocks.STONE_BRICKS)) return false;
        }
        return level.getBlockState(new BlockPos(9, -45, 0)).is(Blocks.STONE)
                && level.getBlockState(new BlockPos(22, -45, 0)).is(Blocks.STONE)
                && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }
    private static boolean environmentReady(ServerPlayer player) {
        return com.frozendawn.event.MobFreezeHandler.getFullSetTier(player) == 3
                && player.getInventory().items.stream().anyMatch(stack -> stack.getItem() instanceof com.frozendawn.item.O2TankItem
                        && stack.getOrDefault(com.frozendawn.init.ModDataComponents.O2_LEVEL.get(), 0) >= LIMIT);
    }
    private static JsonObject frame(CommandSourceStack source, ServerPlayer player, ArchitectEntity actor) {
        var value = new JsonObject();
        value.addProperty("run", 11); value.addProperty("case", 11);
        value.addProperty("timer", score(source, "#timer")); value.addProperty("gameTime", source.getLevel().getGameTime());
        value.addProperty("actor", player.getPersistentData().getUUID(ACTOR_KEY).toString());
        value.addProperty("actorPresent", actor != null);
        if (actor != null) {
            value.addProperty("actorAlive", actor.isAlive()); value.addProperty("actorHealth", actor.getHealth());
            value.add("actorPosition", JSON.toJsonTree(actor.position())); value.addProperty("actorOnGround", actor.onGround());
            value.addProperty("actorNoAi", actor.isNoAi()); value.addProperty("actorYaw", actor.getYRot());
            value.addProperty("headYaw", actor.getYHeadRot()); value.addProperty("bodyYaw", actor.yBodyRot);
            value.addProperty("navigationDone", actor.getNavigation().isDone());
            value.addProperty("successfulBreaks", actor.successfulBreakCount());
            value.addProperty("mining", actor.isMiningBlock());
            value.addProperty("diagnostics", actor.blindController().diagnostics());
            value.add("selected", JSON.toJsonTree(actor.blindController().signal()));
            value.add("tracks", JSON.toJsonTree(actor.blindController().tracks()));
            value.add("vibration", JSON.toJsonTree(actor.blindController().lastVibration()));
            value.add("echo", JSON.toJsonTree(actor.blindController().lastEcho()));
            value.addProperty("actorInsideBase", insideBase(actor.position()));
            value.addProperty("distanceToPlayer", actor.distanceTo(player));
            value.addProperty("vanillaTargetPresent", actor.getTarget() != null);
        }
        value.add("playerPosition", JSON.toJsonTree(player.position()));
        value.addProperty("playerHealth", player.getHealth()); value.addProperty("playerAlive", player.isAlive());
        value.addProperty("playerOnGround", player.onGround()); value.addProperty("playerSprinting", player.isSprinting());
        value.addProperty("playerSneaking", player.isShiftKeyDown()); value.addProperty("playerInsideBase", insideBase(player.position()));
        value.addProperty("playerMode", player.gameMode.getGameModeForPlayer().getName());
        value.addProperty("playerArmorTier", com.frozendawn.event.MobFreezeHandler.getFullSetTier(player));
        value.addProperty("playerOxygenUnits", player.getInventory().items.stream()
                .filter(stack -> stack.getItem() instanceof com.frozendawn.item.O2TankItem)
                .mapToInt(stack -> stack.getOrDefault(com.frozendawn.init.ModDataComponents.O2_LEVEL.get(), 0)).sum());
        if (player.getLastDamageSource() != null) value.addProperty("playerLastDamageType", player.getLastDamageSource().getMsgId());
        value.add("isolation", LabBlindIsolation.snapshot(source.getLevel()));
        value.addProperty("mobGriefing", source.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING));
        var milestones = new JsonObject();
        for (String name : java.util.List.of("#base_acquired", "#base_broken", "#base_entered", "#base_close"))
            milestones.addProperty(name.substring(6), score(source, name));
        value.add("milestones", milestones);
        return value;
    }
    public static int start(CommandSourceStack source) {
        var player = participant(source);
        if (player == null || !player.isCreative() || score(source, "#stage") != 1
                || score(source, "#timer") != 0 || score(source, "#frames") != 0 || !readyGeometry(source) || !environmentReady(player)
                || player.position().distanceToSqr(PLAYER_START) > .01 || !LabBlindIsolation.enabled(source.getLevel())
                || !LabBlindIsolation.unrelated(source.getLevel()).isEmpty()) return 0;
        var actors = source.getLevel().getEntitiesOfClass(ArchitectEntity.class, ROOM);
        if (actors.size() != 1) return 0;
        var actor = actors.getFirst();
        if (!actor.isAlive() || !actor.isBlindArchitect() || !actor.getTags().contains("blind_lab_actor")
                || !actor.isNoAi() || !actor.onGround() || actor.position().distanceToSqr(ACTOR_START) > .001
                || !actor.blindController().tracks().isEmpty()) return 0;
        player.getPersistentData().putUUID(ACTOR_KEY, actor.getUUID());
        try {
            var directory = directory(source, player); Files.createDirectories(directory);
            var initial = frame(source, player, actor);
            initial.addProperty("world", source.getServer().getWorldData().getLevelName());
            initial.addProperty("playerBeforeStart", player.saveWithoutId(new net.minecraft.nbt.CompoundTag()).toString());
            Files.writeString(directory.resolve("start.json"), JSON.toJson(initial) + "\n", StandardOpenOption.CREATE_NEW);
            Files.writeString(directory.resolve("trace.jsonl"), "", StandardOpenOption.CREATE_NEW);
        } catch (java.io.IOException error) {
            source.sendFailure(Component.literal("Base evidence setup failed. Preserve the scene; do not retry.")); return 0;
        }
        score(source, "#stage", 2); score(source, "#timer", 0); score(source, "#frames", 0);
        score(source, "#last_frame", 0);
        score(source, "#inconclusive", 0); score(source, "#archived", 0); score(source, "#base_end", 0);
        player.getPersistentData().putLong("BlindLabBaseLastTick", source.getLevel().getGameTime() - 1);
        player.setHealth(player.getMaxHealth()); player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5); player.setGameMode(GameType.SURVIVAL); actor.setNoAi(false);
        return 1;
    }
    public static int tick(CommandSourceStack source) {
        var player = participant(source);
        if (player == null || score(source, "#stage") != 2 || !player.getPersistentData().hasUUID(ACTOR_KEY)) return 0;
        long now = source.getLevel().getGameTime();
        if (now <= player.getPersistentData().getLong("BlindLabBaseLastTick")) return 0;
        player.getPersistentData().putLong("BlindLabBaseLastTick", now);
        int timer = score(source, "#timer") + 1;
        if (timer < 1 || timer > LIMIT) return 0;
        score(source, "#timer", timer);
        var actor = actor(source, player);
        if (actor != null) {
            var signal = actor.blindController().signal();
            if (signal != null && signal.sense() != com.frozendawn.entity.blind.BlindSignalMemory.Sense.VIBRATION
                    && score(source, "#base_acquired") < 0) score(source, "#base_acquired", timer);
            if (actor.successfulBreakCount() > 0 && score(source, "#base_broken") < 0) score(source, "#base_broken", timer);
            if (insideBase(actor.position()) && score(source, "#base_entered") < 0) score(source, "#base_entered", timer);
            if (actor.distanceTo(player) <= 3 && score(source, "#base_close") < 0) score(source, "#base_close", timer);
        }
        boolean invalid = !LabBlindIsolation.enabled(source.getLevel()) || !LabBlindIsolation.unrelated(source.getLevel()).isEmpty()
                || player.isCreative() || player.isSpectator() || !ROOM.contains(player.position())
                || actor == null || !ROOM.contains(actor.position());
        if (invalid) score(source, "#inconclusive", score(source, "#inconclusive") + 1);
        var frame = frame(source, player, actor);
        try {
            Files.writeString(directory(source, player).resolve("trace.jsonl"), JSON.toJson(frame) + "\n", StandardOpenOption.APPEND);
            score(source, "#frames", score(source, "#frames") + 1);
            score(source, "#last_frame", timer);
        } catch (java.io.IOException error) {
            score(source, "#inconclusive", score(source, "#inconclusive") + 1);
            source.sendFailure(Component.literal("Base trace write failed; preserving the encounter."));
            return finish(source, player, actor, frame, "EVIDENCE_FAILURE", 5);
        }
        if (!player.isAlive()) return finish(source, player, actor, frame, "PLAYER_DIED", 2);
        if (actor != null && !actor.isAlive()) return finish(source, player, actor, frame, "ACTOR_DIED", 3);
        if (invalid) return finish(source, player, actor, frame, "INCONCLUSIVE", 4);
        int entered = score(source, "#base_entered");
        if (entered >= 0 && timer - entered >= 200) return finish(source, player, actor, frame, "ENTRY_OBSERVATION_COMPLETE", 1);
        if (timer == LIMIT) return finish(source, player, actor, frame, "TIME_LIMIT", 1);
        return 1;
    }
    public static int archived(CommandSourceStack source) {
        var player = participant(source);
        if (player == null || !player.isCreative() || score(source, "#stage") != 3
                || score(source, "#archived") != 1 || score(source, "#inconclusive") != 0) return 0;
        var actor = actor(source, player);
        if (actor == null || !actor.isAlive() || !actor.isNoAi()) return 0;
        try {
            var directory = directory(source, player);
            var summary = com.google.gson.JsonParser.parseString(Files.readString(directory.resolve("summary.json"))).getAsJsonObject();
            var start = com.google.gson.JsonParser.parseString(Files.readString(directory.resolve("start.json"))).getAsJsonObject();
            String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(directory.resolve("trace.jsonl"))));
            return summary.get("complete").getAsBoolean() && summary.get("frames").getAsInt() == score(source, "#frames")
                    && summary.get("timer").getAsInt() == score(source, "#timer") && score(source, "#timer") > 0
                    && summary.get("actor").getAsString().equals(actor.getUUID().toString())
                    && start.get("world").getAsString().equals(source.getServer().getWorldData().getLevelName())
                    && summary.get("traceSha256").getAsString().equals(hash) ? 1 : 0;
        } catch (Exception error) { return 0; }
    }
    private static int finish(CommandSourceStack source, ServerPlayer player, ArchitectEntity actor,
                              JsonObject summary, String reason, int result) {
        summary.addProperty("endReason", reason); summary.addProperty("frames", score(source, "#frames"));
        summary.addProperty("complete", score(source, "#frames") == score(source, "#timer") && score(source, "#inconclusive") == 0);
        if (actor != null) summary.addProperty("receiverNbt", actor.saveWithoutId(new net.minecraft.nbt.CompoundTag()).toString());
        // Capture the actual outcome before holding the actor and returning the owner to Creative.
        score(source, "#stage", 3); score(source, "#base_end", result);
        if (actor != null) { actor.getNavigation().stop(); actor.setNoAi(true); }
        player.setGameMode(GameType.CREATIVE);
        try {
            var directory = directory(source, player);
            summary.addProperty("traceSha256", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(directory.resolve("trace.jsonl")))));
            Files.writeString(directory.resolve("summary.json"), JSON.toJson(summary) + "\n", StandardOpenOption.CREATE_NEW);
            score(source, "#archived", summary.get("complete").getAsBoolean() ? 1 : 0);
            return 1;
        } catch (Exception error) {
            source.sendFailure(Component.literal("Base archive failed. Preserve this scene.")); return 0;
        }
    }
}

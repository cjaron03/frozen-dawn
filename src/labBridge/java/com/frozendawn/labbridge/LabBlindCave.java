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
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.ScoreHolder;

/** Passive three-minute cave encounter. Ordinary sensing, movement, mining and combat; no AI orders. */
public final class LabBlindCave {
    private static final Gson JSON = new Gson();
    private static final String ACTOR_KEY = "BlindLabCaveActor";
    private static final AABB ROOM = new AABB(0, -49, -24, 64, -33, 32);
    private static final Vec3 ACTOR_START = new Vec3(8.5, -44, .5);
    private static final Vec3 PLAYER_START = new Vec3(23.5, -44, .5);
    private static final int LIMIT = 3600;
    private LabBlindCave() { }

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
        return LabBlindWorkbench.access(source) == 1 && score(source, "#run") >= 18 && score(source, "#run") <= 19 && score(source, "#case") == score(source, "#run")
                && source.getEntity() instanceof ServerPlayer p && p.getTags().contains("blind_lab_player") ? p : null;
    }
    private static ArchitectEntity actor(CommandSourceStack source, ServerPlayer player) {
        if (!player.getPersistentData().hasUUID(ACTOR_KEY)) return null;
        var entity = source.getLevel().getEntity(player.getPersistentData().getUUID(ACTOR_KEY));
        return entity instanceof ArchitectEntity a && a.isBlindArchitect() && a.getTags().contains("blind_lab_actor") ? a : null;
    }
    private static Path directory(CommandSourceStack source, ServerPlayer player) {
        return source.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-lab-results/run-" + score(source,"#run") + "-"
                + player.getPersistentData().getUUID(ACTOR_KEY));
    }
    private static boolean readyGeometry(CommandSourceStack source) {
        var level = source.getLevel();
        // Starting cover, clear floor and dark branches are checked before release.
        for (int x : new int[]{8, 23, 32, 43, 29}) {
            int z = x == 32 ? -17 : x == 29 ? 20 : 0;
            if (!level.getBlockState(new BlockPos(x, -45, z)).is(Blocks.STONE)
                    || !level.getBlockState(new BlockPos(x, -44, z)).isAir()
                    || !level.getBlockState(new BlockPos(x, -43, z)).isAir()
                    || !level.getBlockState(new BlockPos(x, -34, z)).is(Blocks.STONE)) return false;
        }
        return level.getBlockState(new BlockPos(15, -43, 0)).is(Blocks.STONE)
                && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }
    private static int light(ServerPlayer player, net.minecraft.world.level.LightLayer layer) {
        return player.serverLevel().getBrightness(layer, BlockPos.containing(player.getEyePosition()));
    }
    private static boolean environmentReady(ServerPlayer player) {
        return com.frozendawn.event.MobFreezeHandler.getFullSetTier(player) == 3
                && player.getInventory().items.stream().anyMatch(stack -> stack.getItem() instanceof com.frozendawn.item.O2TankItem
                        && stack.getOrDefault(com.frozendawn.init.ModDataComponents.O2_LEVEL.get(), 0) >= LIMIT);
    }
    private static JsonObject frame(CommandSourceStack source, ServerPlayer player, ArchitectEntity actor) {
        var value = new JsonObject();
        value.addProperty("run", score(source,"#run")); value.addProperty("case", score(source,"#case"));
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
            value.addProperty("distanceToPlayer", actor.distanceTo(player));
            value.addProperty("vanillaTargetPresent", actor.getTarget() != null);
            value.addProperty("journalEntries", actor.decisionJournal().entries().size());
            value.addProperty("journalDropped", actor.decisionJournal().dropped());
            // Read-only geometry truth for interpreting the recorded sensory estimates afterwards.
            value.addProperty("diagnosticDirectEyeRay", source.getLevel().clip(new net.minecraft.world.level.ClipContext(
                    actor.getEyePosition(), player.getEyePosition(), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, actor)).getType() == net.minecraft.world.phys.HitResult.Type.MISS);
        }
        value.add("playerPosition", JSON.toJsonTree(player.position()));
        value.addProperty("playerBlockLight", light(player, net.minecraft.world.level.LightLayer.BLOCK));
        value.addProperty("playerSkyLight", light(player, net.minecraft.world.level.LightLayer.SKY));
        value.addProperty("playerNightVision", player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION));
        value.addProperty("northBlockLight", source.getLevel().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, new BlockPos(32,-43,-17)));
        value.addProperty("southBlockLight", source.getLevel().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, new BlockPos(29,-43,20)));
        value.addProperty("playerHealth", player.getHealth()); value.addProperty("playerAlive", player.isAlive());
        value.addProperty("playerOnGround", player.onGround()); value.addProperty("playerSprinting", player.isSprinting());
        value.addProperty("playerSneaking", player.isShiftKeyDown());
        value.addProperty("playerMode", player.gameMode.getGameModeForPlayer().getName());
        value.addProperty("playerArmorTier", com.frozendawn.event.MobFreezeHandler.getFullSetTier(player));
        value.addProperty("playerOxygenUnits", player.getInventory().items.stream()
                .filter(stack -> stack.getItem() instanceof com.frozendawn.item.O2TankItem)
                .mapToInt(stack -> stack.getOrDefault(com.frozendawn.init.ModDataComponents.O2_LEVEL.get(), 0)).sum());
        if (player.getLastDamageSource() != null) value.addProperty("playerLastDamageType", player.getLastDamageSource().getMsgId());
        value.add("isolation", LabBlindIsolation.snapshot(source.getLevel()));
        value.addProperty("mobGriefing", source.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING));
        return value;
    }
    public static int load(CommandSourceStack source) {
        var player = participant(source);
        if (player == null || !player.isCreative() || score(source, "#stage") != 9) return 0;
        for (int x=0; x<=3; x++) for (int z=-2; z<=1; z++) source.getLevel().getChunk(x,z);
        return 1;
    }
    public static int start(CommandSourceStack source) {
        var player = participant(source);
        if (player == null || !player.isCreative() || score(source, "#stage") != 1
                || score(source, "#timer") != 0 || score(source, "#frames") != 0 || !readyGeometry(source) || !environmentReady(player)
                || player.position().distanceToSqr(PLAYER_START) > .01 || !LabBlindIsolation.enabled(source.getLevel())
                || !LabBlindIsolation.unrelated(source.getLevel()).isEmpty() || !LabBlindIsolation.sourcesClean(source.getLevel())
                || player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)) {
            String reason="Cave not ready: stage="+score(source,"#stage")+" geometry="+readyGeometry(source)
                    +" equipped="+(player!=null&&environmentReady(player))+" nightVision="+(player!=null&&player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION))
                    +" player="+(player==null?"absent":player.position()+" "+player.gameMode.getGameModeForPlayer())
                    +" isolation="+LabBlindIsolation.snapshot(source.getLevel());
            com.mojang.logging.LogUtils.getLogger().warn("[BlindLabCave] {}",reason);
            source.sendFailure(Component.literal(reason));return 0;
        }
        var actors = source.getLevel().getEntitiesOfClass(ArchitectEntity.class, ROOM);
        if (actors.size() != 1) {source.sendFailure(Component.literal("Cave requires exactly one actor; found "+actors.size()));return 0;}
        var actor = actors.getFirst();
        if (!actor.isAlive() || !actor.isBlindArchitect() || !actor.getTags().contains("blind_lab_actor")
                || !actor.isNoAi() || !actor.onGround() || actor.position().distanceToSqr(ACTOR_START) > .001
                || !actor.blindController().tracks().isEmpty()) {
            String reason="Cave actor is not ready: position="+actor.position()+" grounded="+actor.onGround()+" held="+actor.isNoAi()+" tracks="+actor.blindController().tracks();
            com.mojang.logging.LogUtils.getLogger().warn("[BlindLabCave] {}",reason);
            source.sendFailure(Component.literal(reason));return 0;
        }
        player.getPersistentData().putUUID(ACTOR_KEY, actor.getUUID());
        try {
            var directory = directory(source, player); Files.createDirectories(directory);
            var initial = frame(source, player, actor);
            initial.addProperty("world", source.getServer().getWorldData().getLevelName());
            initial.addProperty("playerBeforeStart", player.saveWithoutId(new net.minecraft.nbt.CompoundTag()).toString());
            Files.writeString(directory.resolve("start.json"), JSON.toJson(initial) + "\n", StandardOpenOption.CREATE_NEW);
            Files.writeString(directory.resolve("trace.jsonl"), "", StandardOpenOption.CREATE_NEW);
        } catch (java.io.IOException error) {
            source.sendFailure(Component.literal("Cave evidence setup failed. Preserve the scene; do not retry.")); return 0;
        }
        actor.startDecisionRecording(null); actor.decisionJournal().useEnduranceLabBuffer();
        score(source, "#stage", 2); score(source, "#timer", 0); score(source, "#frames", 0);
        score(source, "#last_frame", 0);
        score(source, "#inconclusive", 0); score(source, "#archived", 0); score(source, "#cave_end", 0);
        player.getPersistentData().putLong("BlindLabCaveLastTick", source.getLevel().getGameTime() - 1);
        player.setHealth(player.getMaxHealth()); player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5); player.setGameMode(GameType.SURVIVAL); actor.setNoAi(false);
        return 1;
    }
    public static int tick(CommandSourceStack source) {
        var player = participant(source);
        if (player == null || score(source, "#stage") != 2 || !player.getPersistentData().hasUUID(ACTOR_KEY)) return 0;
        long now = source.getLevel().getGameTime();
        if (now <= player.getPersistentData().getLong("BlindLabCaveLastTick")) return 0;
        player.getPersistentData().putLong("BlindLabCaveLastTick", now);
        int timer = score(source, "#timer") + 1;
        if (timer < 1 || timer > LIMIT) return 0;
        score(source, "#timer", timer);
        var actor = actor(source, player);
        boolean invalid = !LabBlindIsolation.enabled(source.getLevel()) || !LabBlindIsolation.unrelated(source.getLevel()).isEmpty()
                || player.isCreative() || player.isSpectator() || !ROOM.contains(player.position())
                || actor == null || !ROOM.contains(actor.position())
                || !source.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                || (actor != null && (actor.isNoAi() || actor.getTarget() != null || actor.decisionJournal().dropped() > 0));
        if (invalid) score(source, "#inconclusive", score(source, "#inconclusive") + 1);
        var frame = frame(source, player, actor);
        try {
            Files.writeString(directory(source, player).resolve("trace.jsonl"), JSON.toJson(frame) + "\n", StandardOpenOption.APPEND);
            score(source, "#frames", score(source, "#frames") + 1);
            score(source, "#last_frame", timer);
        } catch (java.io.IOException error) {
            score(source, "#inconclusive", score(source, "#inconclusive") + 1);
            source.sendFailure(Component.literal("Cave trace write failed; preserving the encounter."));
            return finish(source, player, actor, frame, "EVIDENCE_FAILURE", 5);
        }
        if (!player.isAlive()) return finish(source, player, actor, frame, "PLAYER_DIED", 2);
        if (actor != null && !actor.isAlive()) return finish(source, player, actor, frame, "ACTOR_DIED", 3);
        if (invalid) return finish(source, player, actor, frame, "INCONCLUSIVE", 4);
        if (timer == LIMIT) return finish(source, player, actor, frame, "TIME_LIMIT", 1);
        return 1;
    }
    /** An explicit owner stop preserves the run; it is not a success/failure judgment. */
    public static int stop(CommandSourceStack source) {
        var player = participant(source);
        if (player == null || score(source, "#stage") != 2 || score(source, "#frames") < 1) return 0;
        var actor = actor(source, player);
        return finish(source, player, actor, frame(source, player, actor), "OWNER_STOPPED", 6);
    }
    public static int archived(CommandSourceStack source) {
        var player = participant(source);
        if (player == null || !player.isCreative() || score(source, "#stage") != 3
                || score(source, "#archived") != 1 || score(source, "#inconclusive") != 0) return 0;
        var actor = actor(source, player);
        // An owner may remove a held actor after capture. Validate the archived identity;
        // any surviving actor must still be held before the next scene may replace it.
        if (!player.getPersistentData().hasUUID(ACTOR_KEY)
                || (actor != null && actor.isAlive() && !actor.isNoAi())) return 0;
        try {
            var directory = directory(source, player);
            var summary = com.google.gson.JsonParser.parseString(Files.readString(directory.resolve("summary.json"))).getAsJsonObject();
            var start = com.google.gson.JsonParser.parseString(Files.readString(directory.resolve("start.json"))).getAsJsonObject();
            String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(directory.resolve("trace.jsonl"))));
            return summary.get("complete").getAsBoolean() && summary.get("frames").getAsInt() == score(source, "#frames")
                    && summary.get("timer").getAsInt() == score(source, "#timer") && score(source, "#timer") > 0
                    && summary.get("actor").getAsString().equals(player.getPersistentData().getUUID(ACTOR_KEY).toString())
                    && start.get("world").getAsString().equals(source.getServer().getWorldData().getLevelName())
                    && summary.get("traceSha256").getAsString().equals(hash)
                    && score(source,"#last_frame")==score(source,"#frames") && score(source,"#timer")<=LIMIT
                    && summary.get("decisionsSha256").getAsString().equals(java.util.HexFormat.of().formatHex(
                            java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(directory.resolve("decisions.json"))))) ? 1 : 0;
        } catch (Exception error) { return 0; }
    }
    private static int finish(CommandSourceStack source, ServerPlayer player, ArchitectEntity actor,
                              JsonObject summary, String reason, int result) {
        summary.addProperty("endReason", reason); summary.addProperty("frames", score(source, "#frames"));
        summary.addProperty("complete", score(source, "#frames") == score(source, "#timer") && score(source, "#inconclusive") == 0);
        if (actor != null) summary.addProperty("receiverNbt", actor.saveWithoutId(new net.minecraft.nbt.CompoundTag()).toString());
        // Capture the actual outcome before holding the actor and returning the owner to Creative.
        score(source, "#stage", 3); score(source, "#cave_end", result);
        if (actor != null) { actor.getNavigation().stop(); actor.setNoAi(true); }
        player.setGameMode(GameType.CREATIVE);
        try {
            var directory = directory(source, player);
            if (actor != null) {
                Files.writeString(directory.resolve("decisions.json"), JSON.toJson(actor.decisionJournal().entries()) + "\n", StandardOpenOption.CREATE_NEW);
                summary.addProperty("decisionsSha256", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(directory.resolve("decisions.json")))));
                actor.decisionJournal().finish(source.getLevel().getGameTime(), "CAPTURED", reason);
            }
            summary.addProperty("traceSha256", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(directory.resolve("trace.jsonl")))));
            Files.writeString(directory.resolve("summary.json"), JSON.toJson(summary) + "\n", StandardOpenOption.CREATE_NEW);
            score(source, "#archived", summary.get("complete").getAsBoolean() ? 1 : 0);
            return 1;
        } catch (Exception error) {
            source.sendFailure(Component.literal("Cave archive failed. Preserve this scene.")); return 0;
        }
    }
}

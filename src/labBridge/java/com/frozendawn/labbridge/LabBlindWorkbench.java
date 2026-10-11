package com.frozendawn.labbridge;

import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.blind.BlindVibrationPropagation;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.ScoreHolder;

/** Guarded source-cushion and receiver-contact views in one save. Lab-only; never part of the release mod. */
public final class LabBlindWorkbench {
    private static final String OBJECTIVE = "blind_vib";
    private static final AABB ROOM = new AABB(0, -49, -12, 31, -37, 11);
    private static final BlockPos SOURCE = new BlockPos(21, -45, 5);
    private static final Vec3 RECEIVER = new Vec3(9.5, -44, 5.5);
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final com.google.gson.Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private LabBlindWorkbench() { }

    public static int access(CommandSourceStack source) {
        return LabBlindReplay.allowed(source, "vibration_lab")
                && source.getServer().getScoreboard().getObjective(OBJECTIVE) != null ? 1 : 0;
    }
    private static boolean operator(CommandSourceStack source) {
        return LabBlindReplay.allowed(source, "vibration_lab")
                && source.getEntity() instanceof ServerPlayer player && player.isCreative()
                && player.getTags().contains("blind_lab_player");
    }
    private static int score(CommandSourceStack source, String holder) {
        var board = source.getServer().getScoreboard(); var objective = board.getObjective(OBJECTIVE);
        if (objective == null) return -1;
        var value = board.getPlayerScoreInfo(ScoreHolder.forNameOnly(holder), objective);
        return value == null ? -1 : value.value();
    }
    private static void score(CommandSourceStack source, String holder, int value) {
        var board = source.getServer().getScoreboard();
        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder), board.getObjective(OBJECTIVE)).set(value);
    }
    private static ArchitectEntity actor(CommandSourceStack source) {
        var actors = source.getLevel().getEntitiesOfClass(ArchitectEntity.class, ROOM);
        if (actors.size() != 1) return null;
        var actor = actors.getFirst();
        boolean lifted = score(source, "#case") == 3;
        Vec3 expected = lifted ? RECEIVER.add(0, .5, 0) : RECEIVER;
        boolean heldContact = lifted ? !actor.onGround() && actor.isNoGravity() : actor.onGround();
        return actor.isAlive() && actor.isBlindArchitect() && actor.isNoAi() && heldContact
                && actor.getTags().contains("blind_lab_actor") && actor.position().distanceToSqr(expected) < .0001
                ? actor : null;
    }
    public static int check(CommandSourceStack source) {
        if (!operator(source) || actor(source) == null) return 0;
        int scenario = score(source, "#case"), run = score(source, "#run");
        var level = source.getLevel();
        if (scenario < 1 || scenario > 3 || run != scenario || !level.hasChunkAt(SOURCE)
                || !level.getBlockState(SOURCE).is(Blocks.STONE)) return 0;
        return (scenario != 2 ? level.getBlockState(SOURCE.above()).isAir()
                : level.getBlockState(SOURCE.above()).is(Blocks.WHITE_CARPET)) ? 1 : 0;
    }
    public static int clearScene(CommandSourceStack source) {
        if (!operator(source) || score(source, "#stage") != 9
                || score(source, "#run") < 1 || score(source, "#run") > 53) return 0;
        var bounds = (score(source,"#run")==19 || score(source,"#run")==20) ? new AABB(0,-49,-24,64,-33,32) : ROOM;
        var actors = source.getLevel().getEntitiesOfClass(ArchitectEntity.class, bounds);
        if (actors.stream().anyMatch(a -> !a.isNoAi() || !a.getTags().contains("blind_lab_actor"))) return 0;
        // Only fixture actors/labels in the bounded room. No kill drops or unrelated entities.
        source.getLevel().getEntities((net.minecraft.world.entity.Entity)null, bounds,
                e -> e.getTags().contains("blind_lab_actor") || e.getTags().contains("blind_lab_label"))
                .forEach(net.minecraft.world.entity.Entity::discard);
        return 1;
    }

    /** Calibrated player-sized foot contact, not an actual walking animation/event. */
    public static BlindVibrationPropagation.Impulse footImpulse(ServerLevel level, BlockPos stone) {
        boolean carpet = level.getBlockState(stone.above()).is(Blocks.WHITE_CARPET);
        var foot = new Vec3(stone.getX() + .5, stone.getY() + 1 + (carpet ? .0625 : 0), stone.getZ() + .5);
        return new BlindVibrationPropagation.Impulse(BlindVibrationPropagation.Kind.FOOTSTEP, foot,
                new AABB(foot.x - .3, foot.y, foot.z - .3, foot.x + .3, foot.y + 1.8, foot.z + .3),
                true, null, null, 6, level.getGameTime());
    }
    private static Path directory(CommandSourceStack source, ArchitectEntity actor) {
        return source.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-lab-results")
                .resolve("run-" + score(source, "#run") + "-" + actor.getUUID());
    }
    private static void writeNew(Path path, JsonObject value) throws java.io.IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, JSON.toJson(value) + "\n", StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }
    public static int sample(CommandSourceStack source) {
        int timer = score(source, "#timer");
        if (check(source) != 1 || score(source, "#stage") != 2 || score(source, "#last_impulse") >= timer
                || (timer != 100 && timer != 220 && timer != 340)) return 0;
        var actor = actor(source); var level = source.getLevel();
        int pulse = timer == 100 ? 1 : timer == 220 ? 2 : 3;
        // Consume before I/O/effects. An interrupted sample stays incomplete and cannot be retried.
        score(source, "#last_impulse", timer);
        var input = footImpulse(level, SOURCE);
        long start = System.nanoTime();
        var result = BlindVibrationPropagation.receive(level, input, actor, 512);
        long elapsed = System.nanoTime() - start;
        boolean lifted = score(source, "#case") == 3;
        boolean negative = result.outcome() == (lifted ? BlindVibrationPropagation.Outcome.NO_RECEIVER_CONTACT
                : BlindVibrationPropagation.Outcome.BELOW_THRESHOLD);
        int outcome = result.detected() ? 1 : negative ? 0 : 2;
        var record = new JsonObject();
        record.addProperty("world", source.getServer().getWorldData().getLevelName());
        record.addProperty("actor", actor.getUUID().toString()); record.addProperty("run", score(source, "#run"));
        record.addProperty("case", score(source, "#case")); record.addProperty("pulse", pulse);
        record.addProperty("gameTime", level.getGameTime()); record.addProperty("timer", timer); record.addProperty("nanos", elapsed);
        record.addProperty("receiverOnGround", actor.onGround());
        record.addProperty("receiverNoGravity", actor.isNoGravity()); record.add("receiverPosition", JSON.toJsonTree(actor.position()));
        record.add("input", JSON.toJsonTree(input));
        record.add("measurement", JSON.toJsonTree(result));
        try { writeNew(directory(source, actor).resolve("pulse-" + pulse + ".json"), record); }
        catch (java.io.IOException error) {
            LOGGER.error("Could not preserve Blind workbench sample; run is incomplete", error);
            source.sendFailure(Component.literal("Evidence write failed. Preserve this scene; do not restart."));
            score(source, "#inconclusive", score(source, "#inconclusive") + 1);
            return 0;
        }
        score(source, "#samples", score(source, "#samples") + 1);
        score(source, "#detected", score(source, "#detected") + (result.detected() ? 1 : 0));
        score(source, "#inconclusive", score(source, "#inconclusive") + (outcome == 2 ? 1 : 0));
        score(source, "#outcome", outcome); score(source, "#strength_milli", (int)Math.round(result.received() * 1000));
        score(source, "#work", result.work());
        level.setBlock(new BlockPos(21, -40, 5), Blocks.SEA_LANTERN.defaultBlockState(), 3);
        level.setBlock(new BlockPos(9, -40, 5), (outcome == 1 ? Blocks.LIME_CONCRETE
                : outcome == 0 ? Blocks.BLACK_CONCRETE : Blocks.ORANGE_CONCRETE).defaultBlockState(), 3);
        level.playSound(null, SOURCE, SoundEvents.STONE_STEP, SoundSource.BLOCKS, .8F, 1F);
        var message = Component.literal("Footstep " + pulse + "/3: " + (outcome == 1 ? "BA received vibration."
                : outcome == 0 ? (lifted ? "No reception: BA feet have no support contact." : "Below the BA sensor threshold.") : "Test inconclusive."))
                .withStyle(outcome == 1 ? net.minecraft.ChatFormatting.GREEN : outcome == 0
                        ? net.minecraft.ChatFormatting.WHITE : net.minecraft.ChatFormatting.GOLD);
        var player = (ServerPlayer)source.getEntity(); player.displayClientMessage(message, false); player.displayClientMessage(message, true);
        LOGGER.info("[BlindWorkbench] {}", new com.google.gson.Gson().toJson(record));
        return 1;
    }
    private static JsonObject pulseHashes(Path directory) throws Exception {
        var hashes = new JsonObject();
        for (int pulse = 1; pulse <= 3; pulse++) {
            String name = "pulse-" + pulse + ".json";
            hashes.addProperty(name, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(directory.resolve(name)))));
        }
        return hashes;
    }
    public static int archive(CommandSourceStack source) {
        if (!operator(source) || score(source, "#stage") != 3 || actor(source) == null) return 0;
        var actor = actor(source); var directory = directory(source, actor);
        try {
            var summary = new JsonObject();
            summary.addProperty("actor", actor.getUUID().toString()); summary.addProperty("world", source.getServer().getWorldData().getLevelName());
            summary.addProperty("gameTime", source.getLevel().getGameTime());
            var scores = new JsonObject();
            for (var holder : java.util.List.of("#run", "#case", "#stage", "#timer", "#samples", "#detected", "#inconclusive", "#last_impulse"))
                scores.addProperty(holder, score(source, holder));
            summary.add("scores", scores);
            summary.addProperty("receiverNbt", actor.saveWithoutId(new net.minecraft.nbt.CompoundTag()).toString());
            summary.addProperty("playerPosition", source.getEntity().position().toString());
            boolean complete = score(source, "#timer") == 480 && score(source, "#samples") == 3 && score(source, "#inconclusive") == 0;
            summary.addProperty("complete", complete);
            if (complete) summary.add("pulseHashes", pulseHashes(directory));
            writeNew(directory.resolve("summary.json"), summary);
            score(source, "#archived", complete ? 1 : 0);
            return 1;
        } catch (Exception error) {
            LOGGER.error("Could not archive Blind workbench; next scene remains locked", error);
            source.sendFailure(Component.literal("Archive failed. Preserve this scene; next is locked.")); return 0;
        }
    }
    public static int archived(CommandSourceStack source) {
        if (!operator(source) || score(source, "#stage") != 3 || score(source, "#archived") != 1 || actor(source) == null) return 0;
        var actor = actor(source); var directory = directory(source, actor);
        try {
            var summary = JsonParser.parseString(Files.readString(directory.resolve("summary.json"))).getAsJsonObject();
            return summary.get("complete").getAsBoolean() && summary.get("actor").getAsString().equals(actor.getUUID().toString())
                    && summary.getAsJsonObject("scores").get("#run").getAsInt() == score(source, "#run")
                    && summary.getAsJsonObject("pulseHashes").equals(pulseHashes(directory)) ? 1 : 0;
        } catch (Exception error) { return 0; }
    }
}

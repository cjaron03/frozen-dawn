package com.frozendawn.labbridge;

import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.world.BlindArchitectSpawner;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.scores.ScoreHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Passive observation of production spawning in an untouched generated cave. Lab only. */
@EventBusSubscriber(modid = "frozendawn")
public final class LabBlindNatural {
    public static final String WORLD = "Blind Architect 10 Natural Cave";
    private static final Map<UUID, MobSpawnType> origins = new HashMap<>();
    private static final Map<UUID, ArchitectEntity> witnesses = new LinkedHashMap<>();
    private static ServerLevel observing;
    private static UUID participant;
    private static long began;
    private static long lastFrame = Long.MIN_VALUE;
    private static Path directory;
    private static int arrivals;
    private static int blindArrivals, confirmedBlindArrivals;
    private static LabNaturalWait fastWait;
    private static String announcedWaitPhase = "";
    private LabBlindNatural() { }

    private static int stage(ServerLevel level) {
        var objective = level.getScoreboard().getObjective("blind_natural");
        if (objective == null) return -1;
        var value = level.getScoreboard().getPlayerScoreInfo(ScoreHolder.forNameOnly("#stage"), objective);
        return value == null ? -1 : value.value();
    }
    private static void stage(ServerLevel level, int value) {
        level.getScoreboard().getOrCreatePlayerScore(ScoreHolder.forNameOnly("#stage"),
                level.getScoreboard().getObjective("blind_natural")).set(value);
    }
    private static boolean allowed(CommandSourceStack source) {
        return LabBlindReplay.allowed(source, "natural") && source.getEntity() instanceof ServerPlayer;
    }
    public static int access(CommandSourceStack source) { return allowed(source) ? 1 : 0; }
    private static boolean floor(ServerLevel level, BlockPos pos) {
        return pos.getY() <= 0 && level.hasChunkAt(pos) && level.isInWorldBounds(pos)
                && level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                && level.getMaxLocalRawBrightness(pos) == 0;
    }
    private static int nearbyFloors(ServerLevel level, BlockPos center) {
        int found = 0;
        for (int dx = -64; dx <= 64; dx += 8) for (int dz = -64; dz <= 64; dz += 8) {
            int distance = dx * dx + dz * dz;
            if (distance < 24 * 24 || distance > 64 * 64) continue;
            for (int dy = -12; dy <= 12; dy++) if (floor(level, center.offset(dx, dy, dz))) found++;
        }
        return found;
    }
    public static int prepare(CommandSourceStack source) {
        var level = source.getLevel();
        if (!allowed(source) || stage(level) != 0 || !(source.getEntity() instanceof ServerPlayer player)
                || !player.isCreative() || player.getTags().contains("blind_natural_used")) return 0;
        for (var entity : level.getAllEntities()) if (entity instanceof ArchitectEntity) {
            source.sendFailure(Component.literal("An Architect already exists. Preserve this save; do not reset it."));
            return 0;
        }
        var root = source.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-natural");
        if (Files.exists(root)) return 0;
        BlockPos best = null;
        int bestCount = 0, inspected = 0;
        var origin = player.blockPosition();
        search: for (int dx = -96; dx <= 96; dx += 4) for (int dz = -96; dz <= 96; dz += 4)
            for (int y = -52; y <= -12; y++) {
                var pos = new BlockPos(origin.getX() + dx, y, origin.getZ() + dz);
                if (!floor(level, pos)) continue;
                int count = nearbyFloors(level, pos);
                if (count > bestCount) { best = pos; bestCount = count; }
                if (++inspected >= 32 || bestCount >= 48) break search;
            }
        var before = new JsonObject();
        before.addProperty("world", WORLD); before.addProperty("seed", level.getSeed());
        before.addProperty("gameTime", level.getGameTime()); before.addProperty("inspectedCaves", inspected);
        before.addProperty("sampledEligibleFloors", bestCount);
        before.addProperty("playerBefore", player.saveWithoutId(new CompoundTag()).toString());
        before.addProperty("terrainEdits", 0); before.addProperty("forcedArrivals", 0);
        before.addProperty("cave", best == null ? "none" : best.toShortString());
        try {
            Files.createDirectory(root);
            Files.writeString(root.resolve("preparation.json"), before + "\n", StandardOpenOption.CREATE_NEW);
        } catch (Exception e) { source.sendFailure(Component.literal("Preparation evidence failed; preserve the scene: " + e)); return 0; }
        if (best == null || bestCount < 12) {
            stage(level, 9);
            source.sendFailure(Component.literal("No suitable loaded natural cave found. This save is preserved; no terrain was changed."));
            return 0;
        }
        player.addTag("blind_natural_used");
        player.teleportTo(level, best.getX() + .5, best.getY(), best.getZ() + .5, 0, 0);
        source.sendSuccess(() -> Component.literal("Natural cave located. Terrain unchanged; Survival and normal spawning start only when armed."), false);
        return 1;
    }
    public static int start(CommandSourceStack source) {
        var level = source.getLevel();
        if (!allowed(source) || stage(level) != 1 || !(source.getEntity() instanceof ServerPlayer player)
                || !player.isCreative() || !player.getTags().contains("blind_natural_used")
                || !floor(level, player.blockPosition()) || !BlindArchitectSpawner.enabled(level)
                || FrozenDawnConfig.MOB_SPAWN_MULTIPLIER.get() != 1.0
                || FrozenDawnConfig.DEBUG_FORCE_MAEVE_ERASED.get()) return 0;
        directory = source.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-natural");
        try {
            var start = sample(level, player);
            start.addProperty("terrainEdits", 0); start.addProperty("forcedArrivals", 0);
            start.addProperty("spawnMultiplier", FrozenDawnConfig.MOB_SPAWN_MULTIPLIER.get());
            start.addProperty("checkTicks", 200); start.addProperty("rollChance", .02);
            Files.writeString(directory.resolve("start.json"), start + "\n", StandardOpenOption.CREATE_NEW);
        } catch (Exception e) { source.sendFailure(Component.literal("Start evidence failed; do not retry: " + e)); return 0; }
        fastWait = null;
        origins.clear(); witnesses.clear(); arrivals = blindArrivals = confirmedBlindArrivals = 0;
        participant = player.getUUID(); began = level.getGameTime(); lastFrame = Long.MIN_VALUE; observing = level;
        stage(level, 2); player.setGameMode(GameType.SURVIVAL);
        source.sendSuccess(() -> Component.literal("NATURAL CAVE RUNNING: explore normally. Spawns are rare; no arrival is inconclusive. Pause and report if you encounter a BA or want to stop."), false);
        return 1;
    }
    /** One-use continuation of the preserved empty attempt; no player or terrain reset. */
    public static int fastWait(CommandSourceStack source) { return armFastWait(source, false); }
    public static int fastRepeat(CommandSourceStack source) { return armFastWait(source, true); }
    private static int armFastWait(CommandSourceStack source, boolean repeat) {
        var level = source.getLevel(); var player = source.getPlayer(); var manager = source.getServer().tickRateManager();
        if (!allowed(source) || stage(level) != 3 || observing != null || player == null || !player.isAlive()
                || player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL || !player.getTags().contains("blind_natural_used")
                || (repeat ? !player.getTags().contains("blind_natural_fast_used") || player.getTags().contains("blind_natural_fast_repeat_used")
                           : player.getTags().contains("blind_natural_fast_used")) || !floor(level, player.blockPosition())
                || !BlindArchitectSpawner.enabled(level) || FrozenDawnConfig.MOB_SPAWN_MULTIPLIER.get() != 1.0
                || FrozenDawnConfig.DEBUG_FORCE_MAEVE_ERASED.get() || (!repeat && manager.isFrozen())
                || (repeat && !source.getServer().isPaused()) || manager.isSprinting()
                || manager.isSteppingForward() || manager.tickrate() != 20.0f) return 0;
        for (var entity : level.getAllEntities()) if (entity instanceof ArchitectEntity) return 0;
        var root = source.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-natural");
        var destination = root.resolve(repeat ? "fastwait-repeat1" : "fastwait");
        if (repeat && !LabNaturalRepeatProof.verify(root, player.getUUID())) return 0;
        try {
            var oldStart = JsonParser.parseString(Files.readString(root.resolve("start.json"))).getAsJsonObject();
            var endings = Files.readAllLines(root.resolve("end.jsonl"));
            if (endings.size() != 1 || !oldStart.get("player").getAsString().equals(player.getUUID().toString())) return 0;
            var oldEnd = JsonParser.parseString(endings.getFirst()).getAsJsonObject();
            if (!oldEnd.get("outcome").getAsString().equals("INCONCLUSIVE_NO_BLIND_ARRIVAL")
                    || oldEnd.get("arrivals").getAsInt() != 0) return 0;
            var before = sample(level, player);
            before.addProperty("repeat", repeat);
            if (repeat) {
                var predecessor = JsonParser.parseString(Files.readAllLines(root.resolve("fastwait/end.jsonl")).getFirst()).getAsJsonObject();
                if (player.getHealth() != predecessor.get("health").getAsFloat()
                        || !player.position().toString().equals(predecessor.get("position").getAsString())) return 0;
                before.addProperty("previousFastWaitEnd", Files.readString(root.resolve("fastwait/end.jsonl")));
                before.addProperty("exactEightFilePredecessorVerified", true);
            }
            before.addProperty("playerBefore", player.saveWithoutId(new CompoundTag()).toString());
            before.addProperty("previousStart", Files.readString(root.resolve("start.json")));
            before.addProperty("previousEnd", Files.readString(root.resolve("end.jsonl")));
            before.addProperty("spawnMultiplier", 1.0); before.addProperty("rollChance", .02); before.addProperty("checkTicks", 200);
            before.addProperty("waitingLimit", 24000); before.addProperty("acceleratedWaiting", true);
            before.addProperty("terrainEdits", 0); before.addProperty("forcedArrivals", 0);
            Files.createDirectory(destination);
            Files.writeString(destination.resolve("continuation.json"), before + "\n", StandardOpenOption.CREATE_NEW);
        } catch (Exception e) { source.sendFailure(Component.literal("Fast-wait evidence refused; preserve this attempt, do not retry: " + e)); return 0; }
        directory = destination; participant = player.getUUID(); began = level.getGameTime(); lastFrame = Long.MIN_VALUE; observing = level;
        origins.clear(); witnesses.clear(); arrivals = blindArrivals = confirmedBlindArrivals = 0;
        player.addTag(repeat ? "blind_natural_fast_repeat_used" : "blind_natural_fast_used"); stage(level, 2);
        // A stopped empty wait may persist frozen on reload. Only this verified
        // repeat arm restores the ordinary settling clock while owner-paused.
        if (repeat) manager.setFrozen(false);
        fastWait = new LabNaturalWait(level, player, 24000, row -> write("waiting.jsonl", row));
        announcedWaitPhase = "";
        source.sendSuccess(() -> Component.literal("FAST WAIT ARMED: close the menu and stay still. At ARRIVAL HELD, pause and reply ready. Spawn odds unchanged; normal play awaits a separate release."), false);
        return 1;
    }
    public static int fastPlay(CommandSourceStack source) {
        if (!allowed(source) || observing != source.getLevel() || stage(observing) != 2
                || fastWait == null || !fastWait.play(source.getPlayer())) return 0;
        began = observing.getGameTime();
        source.getPlayer().connection.send(new net.minecraft.network.protocol.game.ClientboundClearTitlesPacket(true));
        source.sendSuccess(() -> Component.literal("NORMAL CAVE PLAY: explore normally. Pause and describe your encounter when done."), false);
        return 1;
    }
    /** Resume the two archived natural arrivals, held before saved actor loading. */
    public static int arrivalPlay(CommandSourceStack source) {
        var level = source.getLevel(); var player = source.getPlayer();
        var hold = LabNaturalArrivalHold.current(source.getServer());
        if (!allowed(source) || stage(level) != 3 || observing != null || player == null || hold == null
                || !source.getServer().isPaused() || player.getTags().contains("blind_natural_arrival_play_used")
                || !player.getTags().contains("blind_natural_fast_repeat_used") || !floor(level, player.blockPosition())
                || !BlindArchitectSpawner.enabled(level) || FrozenDawnConfig.MOB_SPAWN_MULTIPLIER.get() != 1.0
                || FrozenDawnConfig.DEBUG_FORCE_MAEVE_ERASED.get() || !hold.ready(player)) return 0;
        var root = source.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-natural");
        var destination = root.resolve("arrival-play");
        try {
            var before = sample(level, player);
            before.addProperty("continuationOf", "fastwait-repeat1");
            before.addProperty("previousEnd", Files.readString(root.resolve("fastwait-repeat1/end.jsonl")));
            before.addProperty("playerBefore", player.saveWithoutId(new CompoundTag()).toString());
            before.addProperty("exactNativePredecessorVerified", true);
            before.addProperty("carriedNativeArrivals", 2); before.addProperty("carriedNaturalBlindArrivals", 1);
            before.addProperty("spawnMultiplier", 1.0); before.addProperty("terrainEdits", 0); before.addProperty("forcedArrivals", 0);
            Files.writeString(destination.resolve("start.json"), before + "\n", StandardOpenOption.CREATE_NEW);
        } catch (Exception e) { source.sendFailure(Component.literal("Arrival continuation evidence refused; preserve the held scene: " + e)); return 0; }
        directory = destination; participant = player.getUUID(); began = level.getGameTime(); lastFrame = Long.MIN_VALUE;
        origins.clear(); witnesses.clear(); arrivals = 2; blindArrivals = confirmedBlindArrivals = 1;
        for (var entity : level.getAllEntities()) if (entity instanceof ArchitectEntity actor) {
            witnesses.put(actor.getUUID(), actor);
            actor.startDecisionRecording(null); actor.decisionJournal().useEnduranceLabBuffer();
        }
        player.addTag("blind_natural_arrival_play_used"); stage(level, 2); observing = level; fastWait = null;
        if (!hold.release(player)) {
            stage(level, 3); observing = null;
            source.sendFailure(Component.literal("Release receipt failed; actor clock remains held. Preserve this attempt; do not retry.")); return 0;
        }
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundClearTitlesPacket(true));
        source.sendSuccess(() -> Component.literal("NORMAL CAVE PLAY: the naturally spawned BA is ready. Explore normally; pause and describe your encounter when done."), false);
        return 1;
    }
    private static JsonObject sample(ServerLevel level, ServerPlayer player) {
        var row = new JsonObject(); row.addProperty("gameTime", level.getGameTime()); row.addProperty("world", WORLD);
        row.addProperty("phase", ApocalypseState.get(level.getServer()).getPhase());
        row.addProperty("progress", ApocalypseState.get(level.getServer()).getProgress());
        row.addProperty("doMobSpawning", level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING));
        row.addProperty("difficulty", level.getDifficulty().getSerializedName());
        row.addProperty("frozen", level.getServer().tickRateManager().isFrozen());
        row.addProperty("sprinting", level.getServer().tickRateManager().isSprinting());
        if (fastWait != null) { row.addProperty("waitingPhase", fastWait.phase()); row.addProperty("waitingReason", fastWait.reason()); }
        if (player != null) {
            row.addProperty("player", player.getUUID().toString()); row.addProperty("position", player.position().toString());
            row.addProperty("health", player.getHealth()); row.addProperty("gameMode", player.gameMode.getGameModeForPlayer().getName());
            row.addProperty("blockLight", level.getMaxLocalRawBrightness(player.blockPosition()));
        }
        var actors = new com.google.gson.JsonArray();
        for (var entity : level.getAllEntities()) if (entity instanceof ArchitectEntity actor) {
            var a = new JsonObject(); a.addProperty("uuid", actor.getUUID().toString()); a.addProperty("blind", actor.isBlindArchitect());
            a.addProperty("position", actor.position().toString()); a.addProperty("health", actor.getHealth());
            a.addProperty("target", actor.getTarget() == null ? "" : actor.getTarget().getUUID().toString());
            if (actor.isBlindArchitect()) { a.addProperty("diagnostics", actor.blindController().diagnostics()); a.addProperty("tracks", actor.blindController().tracks().toString()); }
            actors.add(a);
        }
        row.add("architects", actors); return row;
    }
    private static boolean write(String file, JsonObject row) {
        try { Files.writeString(directory.resolve(file), row + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND); return true; }
        catch (Exception e) {
            System.err.println("[LabBlindNatural] Evidence failed; preserve the save: " + e);
            if (observing != null) {
                if (fastWait != null && !fastWait.playing()) {
                    var manager = observing.getServer().tickRateManager();
                    if (manager.isSprinting() && !manager.stopSprinting()) manager.checkShouldSprintThisTick();
                    manager.stopStepping(); manager.setFrozen(true);
                }
                stage(observing, 9);
                var player = observing.getServer().getPlayerList().getPlayer(participant);
                if (player != null) player.sendSystemMessage(Component.literal("Natural observer failed. Pause and preserve this attempt."));
            }
            observing = null; return false;
        }
    }
    @SubscribeEvent public static void finalized(FinalizeSpawnEvent e) {
        if (observing != null && e.getLevel() == observing && e.getEntity() instanceof ArchitectEntity)
            origins.put(e.getEntity().getUUID(), e.getSpawnType());
    }
    @SubscribeEvent public static void joined(EntityJoinLevelEvent e) {
        if (observing == null || e.getLevel() != observing || !(e.getEntity() instanceof ArchitectEntity actor)) return;
        if (fastWait != null) fastWait.joining(actor);
        if (observing == null) return;
        var row = sample(observing, observing.getServer().getPlayerList().getPlayer(participant));
        var origin = origins.remove(actor.getUUID());
        row.addProperty("spawnType", origin == null ? "UNCONFIRMED" : origin.name()); row.addProperty("arrival", actor.getUUID().toString());
        row.addProperty("blind", actor.isBlindArchitect()); row.addProperty("nbt", actor.saveWithoutId(new CompoundTag()).toString());
        if (!write("arrivals.jsonl", row)) return;
        arrivals++; witnesses.put(actor.getUUID(), actor);
        if (actor.isBlindArchitect()) {
            blindArrivals++;
            if (origin == MobSpawnType.NATURAL) confirmedBlindArrivals++;
        }
        // A null seed records decisions without changing the actor's RNG or ordinary AI.
        actor.startDecisionRecording(null);
        actor.decisionJournal().useEnduranceLabBuffer();
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Post e) {
        if (observing == null || e.getLevel() != observing) return;
        var level = observing;
        var player = level.getServer().getPlayerList().getPlayer(participant);
        if (level.getGameTime() % 20 == 0 && level.getGameTime() != lastFrame) {
            lastFrame = level.getGameTime();
            if (!write("frames.jsonl", sample(level, player))) return;
        }
        if (player == null || !player.isAlive() || (fastWait == null || fastWait.playing()) && level.getGameTime() - began >= 24000) finish(level, player);
    }
    @SubscribeEvent public static void waitingTick(ServerTickEvent.Post e) {
        if (observing == null || fastWait == null || observing.getServer() != e.getServer()) return;
        var level = observing; var player = e.getServer().getPlayerList().getPlayer(participant);
        fastWait.confirmInsertion();
        if (observing == null) return;
        if (!e.getServer().isPaused()) fastWait.tick(player, player != null && player.serverLevel() == level
                && e.getServer().getPlayerList().getPlayerCount() == 1 && player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL
                && BlindArchitectSpawner.enabled(level) && FrozenDawnConfig.MOB_SPAWN_MULTIPLIER.get() == 1.0
                && !FrozenDawnConfig.DEBUG_FORCE_MAEVE_ERASED.get() && floor(level, player.blockPosition()));
        if (observing == null || fastWait.phase().equals(announcedWaitPhase)) return;
        announcedWaitPhase = fastWait.phase();
        if (player != null && fastWait.held()) {
            String cue = fastWait.arrivalConfirmed() ? "ARRIVAL HELD: pause and reply ready. Do not move until normal-play release."
                    : "WAIT STOPPED: " + fastWait.reason() + ". Pause and preserve this attempt.";
            player.sendSystemMessage(Component.literal(cue));
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(0, 1200, 0));
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal(
                    fastWait.arrivalConfirmed() ? "ARRIVAL HELD" : "WAIT STOPPED")));
        }
    }
    private static void finish(ServerLevel level, ServerPlayer player) {
        if (fastWait != null && !fastWait.playing() && !fastWait.held()) fastWait.hold("OBSERVATION_STOPPED");
        if (observing == null) return;
        var result = sample(level, player); result.addProperty("arrivals", arrivals);
        result.addProperty("blindArrivals", blindArrivals); result.addProperty("confirmedNaturalBlindArrivals", confirmedBlindArrivals);
        result.addProperty("outcome", blindArrivals == 0 ? "INCONCLUSIVE_NO_BLIND_ARRIVAL"
                : confirmedBlindArrivals == 0 ? "INCONCLUSIVE_UNCONFIRMED_BLIND_ORIGIN" : "OWNER_ACCOUNT_AND_DIAGNOSTICS_PENDING");
        result.addProperty("elapsedTicks", level.getGameTime() - began);
        var journals = new com.google.gson.JsonArray();
        for (var actor : witnesses.values()) {
            var journal = new JsonObject(); journal.addProperty("uuid", actor.getUUID().toString());
            journal.addProperty("dropped", actor.decisionJournal().dropped());
            journal.addProperty("decisions", actor.decisionJournal().tsv());
            journal.addProperty("nbt", actor.saveWithoutId(new CompoundTag()).toString()); journals.add(journal);
        }
        result.add("journals", journals);
        if (!write("end.jsonl", result)) return;
        stage(level, 3); observing = null; origins.clear();
        if (player != null) player.sendSystemMessage(Component.literal("Natural observation ended. Pause and describe what happened. The cave and actors remain unchanged."));
    }
    public static int stop(CommandSourceStack source) {
        if (!allowed(source) || observing != source.getLevel() || stage(observing) != 2) return 0;
        finish(observing, source.getPlayer()); return 1;
    }
    @SubscribeEvent public static void closing(ServerStoppingEvent e) {
        if (observing != null && observing.getServer() == e.getServer())
            finish(observing, e.getServer().getPlayerList().getPlayer(participant));
        witnesses.clear(); origins.clear();
        fastWait = null;
    }
}

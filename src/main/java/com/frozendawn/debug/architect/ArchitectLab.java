package com.frozendawn.debug.architect;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.Difficulty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;

/** Thin manual controls around the same fixtures, start transaction and assertions as GameTests. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class ArchitectLab {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ServerLevel, ArchitectLabRun> RUNS = new HashMap<>();
    private static final Map<ServerLevel, CommandSourceStack> OWNERS = new HashMap<>();
    private ArchitectLab() { }

    public static boolean isRunning(net.minecraft.server.MinecraftServer server) {
        return RUNS.values().stream().anyMatch(r -> r.level.getServer() == server && r.status() == ArchitectLabRun.Status.RUNNING);
    }

    public static Path reports(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("architect-debug");
    }

    /** Functions suppress normal command feedback, so explicitly deliver the lab's result. */
    public static void reply(CommandSourceStack source, String message) {
        LOGGER.info("[ArchitectLab] {}", message);
        if (source.getEntity() instanceof ServerPlayer player) player.sendSystemMessage(Component.literal("[lab] " + message));
        else source.sendSuccess(() -> Component.literal("[lab] " + message), false);
    }

    public static int command(CommandSourceStack source, String operation, String argument) {
        ServerLevel level = source.getLevel();
        try {
            if (ArchitectWildernessLab.isActive(source.getServer()) && !java.util.Set.of("dump", "inspect", "mark").contains(operation))
                throw new IllegalStateException("Stop the wilderness run before changing the small lab");
            if (operation.equals("setup")) {
                freeze(level);
                Marker marker = marker(level, true, BlockPos.containing(source.getPosition()));
                if (!marker.getPersistentData().contains("scenario")) marker.getPersistentData().putString("scenario", "low_ceiling");
                validateOrigin(level, marker.blockPosition().below());
                configure(level);
                return reset(source, marker);
            }
            Marker marker = marker(level, false, null);
            CompoundTag data = marker.getPersistentData();
            if (operation.equals("scenario")) {
                data.putString("scenario", ArchitectLabScenario.named(argument).id);
                return reset(source, marker);
            }
            if (operation.equals("seed")) { data.putLong("seed", Long.parseLong(argument)); return reset(source, marker); }
            if (operation.equals("rotation")) {
                int degrees = Integer.parseInt(argument);
                if (degrees < 0 || degrees > 270 || degrees % 90 != 0) throw new IllegalArgumentException("Use 0, 90, 180 or 270");
                data.putInt("rotation", degrees);
                return reset(source, marker);
            }
            if (operation.equals("target")) { data.putBoolean("live", argument.equals("live")); return reset(source, marker); }
            if (operation.equals("reset")) return reset(source, marker);
            ArchitectLabRun run = RUNS.get(level);
            if (run == null) throw new IllegalStateException("Run /fd architect lab reset to prepare this saved lab");
            switch (operation) {
                case "tp" -> {
                    if (!(source.getEntity() instanceof ServerPlayer player)) throw new IllegalStateException("A player must use lab tp");
                    Vec3 observation = run.frame.position(new Vec3(18.5, 1, 18.5));
                    player.teleportTo(level, observation.x, observation.y, observation.z, java.util.Set.of(), player.getYRot(), player.getXRot());
                }
                case "run" -> {
                    freeze(level);
                    run.begin();
                    OWNERS.put(level, source);
                    reply(source, "Run " + run.id + " recording from tick 0. Use /tick step 1 or /tick sprint " + run.scenario.timeout + ".");
                }
                case "dump" -> reply(source, "Exported run " + run.id + " to " + export(run));
                case "inspect" -> reply(source, run.id + " " + run.scenario.id + " " + run.status() + ": " + run.reason() + "\n" + run.architect.inspectDecisions());
                case "mark" -> {
                    if (run.status() != ArchitectLabRun.Status.RUNNING) throw new IllegalStateException("Start a run before adding a marker");
                    run.architect.recordDecision("MARK", null, argument.substring(0, Math.min(200, argument.length())));
                    reply(source, "Marked run " + run.id);
                }
                default -> throw new IllegalArgumentException("Unknown lab operation " + operation);
            }
            return 1;
        } catch (IOException | IllegalArgumentException | IllegalStateException error) {
            if (operation.equals("dump")) {
                try { ArchitectDebugReports.invalidate(reports(level), "unknown", "FAILED", error.getMessage()); }
                catch (IOException statusError) { LOGGER.error("Could not invalidate failed export", statusError); }
            }
            reply(source, "FAILED: " + error.getMessage());
            return 0;
        }
    }

    private static int reset(CommandSourceStack source, Marker marker) throws IOException {
        ServerLevel level = source.getLevel();
        BlockPos origin = marker.blockPosition().below();
        validateOrigin(level, origin);
        CompoundTag data = marker.getPersistentData();
        String scenario = data.getString("scenario");
        if (scenario.isEmpty()) scenario = "low_ceiling";
        ArchitectLabScenario selected = ArchitectLabScenario.named(scenario);
        long seed = data.contains("seed") ? data.getLong("seed") : 1337;
        freeze(level);
        ArchitectLabRun old = RUNS.get(level);
        if (old != null) {
            if (old.status() == ArchitectLabRun.Status.RUNNING) {
                old.finish(ArchitectLabRun.Status.ABORTED, "Lab reset before completion");
                export(old);
            }
        }
        ArchitectDebugReports.invalidate(reports(level), "pending", "RESETTING", "Rebuilding lab");
        RUNS.remove(level);
        OWNERS.remove(level);
        if (old != null) old.dispose();
        int degrees = data.getInt("rotation");
        Rotation rotation = Rotation.values()[degrees / 90];
        var oldFrame = new ArchitectLabFrame(origin, Rotation.values()[data.getInt("placedRotation") / 90]);
        var newFrame = new ArchitectLabFrame(origin, rotation);
        AABB oldBounds = oldFrame.bounds().inflate(1), newBounds = newFrame.bounds().inflate(1);
        // Dispose only tagged lab entities inside this arena, including actors from a saved
        // session. Never kill nearby production Architects or unrelated item drops.
        AABB bounds = new AABB(Vec3.atLowerCornerOf(origin.offset(-21, 0, -21)), Vec3.atLowerCornerOf(origin.offset(21, 16, 21))).inflate(1);
        for (var entity : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, bounds,
                e -> e.getTags().contains("fd_lab") && e != marker
                        && (oldBounds.intersects(e.getBoundingBox()) || newBounds.intersects(e.getBoundingBox())))) {
            if (entity instanceof ArchitectEntity actor) actor.discardLabActor(); else entity.discard();
        }
        if (data.getInt("placedRotation") != degrees) {
            for (int x = 0; x <= 20; x++) for (int y = 0; y < 16; y++) for (int z = 0; z <= 20; z++) {
                level.setBlockAndUpdate(oldFrame.block(new BlockPos(x, y, z)), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
        }
        var run = ArchitectLabRun.prepare(level, selected,
                newFrame, seed, data.getBoolean("live"), true);
        data.putInt("placedRotation", degrees);
        RUNS.put(level, run);
        OWNERS.put(level, source);
        ArchitectDebugReports.invalidate(reports(level), run.id.toString(), "PREPARED", "Recording has not started");
        if (source.getEntity() instanceof ServerPlayer player) {
            Vec3 observation = run.frame.position(new Vec3(18.5, 1, 18.5));
            player.teleportTo(level, observation.x, observation.y, observation.z, java.util.Set.of(), player.getYRot(), player.getXRot());
        }
        reply(source, "Reset " + scenario + "; new actors, original terrain, seed " + seed + ", rotation " + degrees + ". Frozen. Start with /fd architect lab run.");
        return 1;
    }

    private static void validateOrigin(ServerLevel level, BlockPos origin) {
        if (origin.getY() < level.getMinBuildHeight() || origin.getY() + 16 > level.getMaxBuildHeight()) {
            throw new IllegalArgumentException("The 16-block-high lab does not fit here; choose a lower/higher setup position");
        }
    }

    /** Shared by the direct command and function alias. Use a disposable lab world. */
    private static void configure(ServerLevel level) {
        var server = level.getServer();
        var rules = level.getGameRules();
        for (var key : java.util.List.of(GameRules.RULE_DAYLIGHT, GameRules.RULE_WEATHER_CYCLE,
                GameRules.RULE_DOMOBSPAWNING, GameRules.RULE_DOINSOMNIA, GameRules.RULE_DO_PATROL_SPAWNING,
                GameRules.RULE_DO_TRADER_SPAWNING, GameRules.RULE_DOFIRETICK, GameRules.RULE_MOBGRIEFING,
                GameRules.RULE_DOBLOCKDROPS, GameRules.RULE_DOENTITYDROPS, GameRules.RULE_FALL_DAMAGE,
                GameRules.RULE_NATURAL_REGENERATION)) rules.getRule(key).set(false, server);
        for (var key : java.util.List.of(GameRules.RULE_KEEPINVENTORY, GameRules.RULE_SHOWDEATHMESSAGES,
                GameRules.RULE_SENDCOMMANDFEEDBACK, GameRules.RULE_COMMANDBLOCKOUTPUT,
                GameRules.RULE_LOGADMINCOMMANDS)) rules.getRule(key).set(true, server);
        rules.getRule(GameRules.RULE_RANDOMTICKING).set(0, server);
        server.setDifficulty(Difficulty.EASY, true);
        level.setDayTime(6000);
        level.setWeatherParameters(1_000_000, 0, false, false);
    }

    private static Marker marker(ServerLevel level, boolean create, BlockPos position) {
        var markers = level.getEntities(EntityType.MARKER, m -> m.getTags().contains("fd_lab_origin"));
        if (markers.size() > 1) throw new IllegalStateException("Multiple lab origins in this dimension; keep one origin before resetting");
        if (!markers.isEmpty()) return markers.getFirst();
        if (!create) throw new IllegalStateException("Create the lab first with /function frozendawn:lab/setup");
        validateOrigin(level, position.below());
        Marker marker = EntityType.MARKER.create(level);
        if (marker == null) throw new IllegalStateException("Could not create lab origin");
        marker.addTag("fd_lab_origin");
        marker.setPos(position.getX(), position.getY(), position.getZ());
        level.addFreshEntity(marker);
        return marker;
    }

    private static Path export(ArchitectLabRun run) throws IOException {
        return ArchitectDebugReports.export(reports(run.level), run.architect.decisionJournal(), run.context());
    }

    private static void freeze(ServerLevel level) {
        var manager = level.getServer().tickRateManager();
        if (manager.isSprinting()) manager.stopSprinting();
        if (manager.isSteppingForward()) manager.stopStepping();
        manager.setFrozen(true);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        for (var run : RUNS.values()) {
            if (run.level.getServer() != event.getServer() || run.status() != ArchitectLabRun.Status.RUNNING) continue;
            run.tick();
            if (!run.done()) continue;
            freeze(run.level);
            CommandSourceStack source = OWNERS.get(run.level);
            try { reply(source, run.status() + ": " + run.reason() + "; report " + export(run)); }
            catch (IOException error) { reply(source, "EXPORT FAILED for run " + run.id + ": " + error.getMessage()); }
        }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        RUNS.entrySet().removeIf(entry -> {
            var run = entry.getValue();
            if (run.level.getServer() != event.getServer()) return false;
            if (run.status() == ArchitectLabRun.Status.RUNNING) {
                run.finish(ArchitectLabRun.Status.ABORTED, "Server stopped before completion");
                try { export(run); } catch (IOException error) { LOGGER.error("Failed to export interrupted lab", error); }
            }
            OWNERS.remove(entry.getKey());
            return true;
        });
    }
}

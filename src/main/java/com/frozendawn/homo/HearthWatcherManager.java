package com.frozendawn.homo;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.entity.ReturnedEntity;
import com.frozendawn.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Spawns exactly one persistent observer for each reconciled TRACE Hearth.
 * Missing watchers are not recreated after death; later conduct logic owns that outcome.
 */
public final class HearthWatcherManager {
    private static final long CHECK_INTERVAL_TICKS = 40L;
    private static final double ADOPTION_RADIUS = 48.0D;

    private static long watchersSpawned;
    private static long watchersAdopted;

    private HearthWatcherManager() {
    }

    public static void tick(ServerLevel level) {
        if (level.dimension() != ServerLevel.OVERWORLD
                || level.getGameTime() % CHECK_INTERVAL_TICKS != 0L) {
            return;
        }
        reconcile(level);
    }

    public static int reconcileNow(ServerLevel level) {
        return reconcile(level);
    }

    public static DebugRespawnResult respawnForDebug(
            ServerLevel level, HearthSelectionPolicy.HearthType type) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        ReturnedHearthSavedData.HearthRecord hearth = data.hearth(type).orElse(null);
        if (hearth == null || !level.isLoaded(hearth.center())) {
            return new DebugRespawnResult(false, 0, 0);
        }

        AABB area = new AABB(hearth.center()).inflate(ADOPTION_RADIUS);
        int removed = 0;
        for (ReturnedEntity watcher : level.getEntitiesOfClass(ReturnedEntity.class, area,
                returned -> returned.isBoundToHearth(hearth.id()))) {
            watcher.discard();
            removed++;
        }
        data.clearWatcherBindingForDebug(hearth.id());
        int spawned = reconcile(level);
        return new DebugRespawnResult(true, removed, spawned);
    }

    public static String statusLine() {
        return "spawned=" + watchersSpawned + " adopted=" + watchersAdopted;
    }

    public static void reset() {
        watchersSpawned = 0L;
        watchersAdopted = 0L;
    }

    private static int reconcile(ServerLevel level) {
        if (!FrozenDawnConfig.ENABLE_RETURNED.get()) {
            return 0;
        }

        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        int created = 0;
        for (ReturnedHearthSavedData.HearthRecord hearth : data.hearths()) {
            if (!HearthWatcherPolicy.canHostWatcher(hearth)
                    || hearth.watcherSpawned()
                    || !level.isLoaded(hearth.center())) {
                continue;
            }

            ReturnedEntity existing = findExistingWatcher(level, hearth);
            if (existing != null) {
                if (data.bindWatcher(hearth.id(), existing.getUUID(), HearthWatcherPolicy.PROFILE)) {
                    watchersAdopted++;
                }
                continue;
            }

            BlockPos spawnPos = findSpawnPosition(level, hearth);
            if (spawnPos == null) {
                continue;
            }

            ReturnedEntity watcher = ModEntities.RETURNED.get().create(
                    level, null, spawnPos, MobSpawnType.EVENT, true, false);
            if (watcher == null) {
                continue;
            }
            watcher.bindToHearth(hearth.id(), hearth.center(),
                    HearthWatcherPolicy.textureVariant(hearth.layoutSeed()));
            if (!level.addFreshEntity(watcher)) {
                continue;
            }

            if (data.bindWatcher(hearth.id(), watcher.getUUID(), HearthWatcherPolicy.PROFILE)) {
                watchersSpawned++;
                created++;
                FrozenDawn.LOGGER.info("Spawned Hearth-bound Returned {} for {} Hearth {} at ({}, {}, {})",
                        watcher.getUUID().toString().substring(0, 8),
                        hearth.type().name().toLowerCase(),
                        hearth.id().toString().substring(0, 8),
                        spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
            }
        }
        return created;
    }

    private static ReturnedEntity findExistingWatcher(
            ServerLevel level, ReturnedHearthSavedData.HearthRecord hearth) {
        AABB area = new AABB(hearth.center()).inflate(ADOPTION_RADIUS);
        return level.getEntitiesOfClass(ReturnedEntity.class, area,
                        returned -> returned.isAlive() && returned.isBoundToHearth(hearth.id()))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private static BlockPos findSpawnPosition(
            ServerLevel level, ReturnedHearthSavedData.HearthRecord hearth) {
        for (BlockPos offset : HearthWatcherPolicy.spawnOffsets(hearth.layoutSeed())) {
            int x = hearth.center().getX() + offset.getX();
            int z = hearth.center().getZ() + offset.getZ();
            BlockPos probe = new BlockPos(x, hearth.center().getY(), z);
            if (!level.isLoaded(probe)) {
                continue;
            }
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, probe);
            if (validSpawn(level, surface)) {
                return surface;
            }
        }
        return null;
    }

    private static boolean validSpawn(ServerLevel level, BlockPos pos) {
        if (pos.getY() < 60
                || !level.isLoaded(pos)
                || !level.isLoaded(pos.above())
                || !level.isLoaded(pos.below())
                || (!level.canSeeSky(pos) && !level.canSeeSky(pos.above()))) {
            return false;
        }

        BlockPos below = pos.below();
        BlockState ground = level.getBlockState(below);
        return ground.isSolidRender(level, below)
                && emptyCollision(level, pos)
                && emptyCollision(level, pos.above());
    }

    private static boolean emptyCollision(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty()
                && state.getCollisionShape(level, pos).isEmpty();
    }

    public record DebugRespawnResult(boolean hearthLoaded, int removed, int spawned) {
    }
}

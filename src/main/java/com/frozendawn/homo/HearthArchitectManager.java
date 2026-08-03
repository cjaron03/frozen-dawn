package com.frozendawn.homo;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Reconciles the single persistent Architect assessor owned by the Major Hearth.
 */
public final class HearthArchitectManager {
    private static final long CHECK_INTERVAL_TICKS = 40L;
    private static final double ADOPTION_RADIUS = 56.0D;

    private static long assessorsSpawned;
    private static long assessorsAdopted;
    private static long assessmentsCompleted;
    private static String lastFailure = "none";

    private HearthArchitectManager() {
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

    public static DebugRespawnResult respawnForDebug(ServerLevel level) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        ReturnedHearthSavedData.HearthRecord hearth = data
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        if (hearth == null || !level.isLoaded(hearth.center())) {
            return new DebugRespawnResult(false, 0, 0);
        }

        AABB area = new AABB(hearth.center()).inflate(ADOPTION_RADIUS);
        int removed = 0;
        for (ArchitectEntity architect : level.getEntitiesOfClass(ArchitectEntity.class, area,
                candidate -> candidate.isBoundToHearthAssessor(hearth.id()))) {
            architect.discard();
            removed++;
        }
        data.clearArchitectAssessorBindingForDebug(hearth.id());
        int spawned = reconcile(level);
        return new DebugRespawnResult(true, removed, spawned);
    }

    public static void recordCompletedAssessment() {
        assessmentsCompleted++;
    }

    public static String statusLine() {
        return "spawned=" + assessorsSpawned
                + " adopted=" + assessorsAdopted
                + " assessments=" + assessmentsCompleted
                + " lastFailure=" + lastFailure;
    }

    public static void reset() {
        assessorsSpawned = 0L;
        assessorsAdopted = 0L;
        assessmentsCompleted = 0L;
        lastFailure = "none";
    }

    private static int reconcile(ServerLevel level) {
        if (PostMaeveWorldState.isErased(level)) {
            return 0;
        }
        if (!FrozenDawnConfig.ENABLE_ARCHITECT.get()) {
            return 0;
        }

        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        ReturnedHearthSavedData.HearthRecord hearth = data
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        if (hearth == null || !HearthArchitectPolicy.canHostAssessor(hearth)
                || hearth.architectAssessorSpawned() || !level.isLoaded(hearth.center())) {
            return 0;
        }

        ArchitectEntity existing = findExistingAssessor(level, hearth);
        if (existing != null) {
            if (data.bindArchitectAssessor(
                    hearth.id(), existing.getUUID(), HearthArchitectPolicy.PROFILE)) {
                assessorsAdopted++;
            }
            return 0;
        }

        SpawnPoint spawnPoint = findSpawnPosition(level, hearth);
        if (spawnPoint == null) {
            lastFailure = "no collision-safe surface near Major Hearth";
            return 0;
        }
        ArchitectEntity architect = ModEntities.ARCHITECT.get().create(
                level, null, spawnPoint.blockPos(), MobSpawnType.EVENT, true, false);
        if (architect == null) {
            lastFailure = "entity creation returned null";
            return 0;
        }
        architect.moveTo(
                spawnPoint.blockPos().getX() + 0.5D,
                spawnPoint.y(),
                spawnPoint.blockPos().getZ() + 0.5D,
                architect.getYRot(),
                architect.getXRot());
        architect.bindToHearthAssessor(hearth.id(), hearth.center(),
                HearthArchitectPolicy.textureVariant(hearth.layoutSeed()));
        if (!level.addFreshEntity(architect)) {
            lastFailure = "level rejected entity insertion";
            return 0;
        }
        if (!data.bindArchitectAssessor(
                hearth.id(), architect.getUUID(), HearthArchitectPolicy.PROFILE)) {
            architect.discard();
            lastFailure = "SavedData binding rejected spawned assessor";
            return 0;
        }

        assessorsSpawned++;
        lastFailure = "none";
        FrozenDawn.LOGGER.info(
                "Spawned Hearth Architect assessor {} for Major Hearth {} at ({}, {}, {})",
                shortId(architect.getUUID()), shortId(hearth.id()),
                spawnPoint.blockPos().getX(), String.format("%.3f", spawnPoint.y()),
                spawnPoint.blockPos().getZ());
        return 1;
    }

    private static ArchitectEntity findExistingAssessor(
            ServerLevel level, ReturnedHearthSavedData.HearthRecord hearth) {
        AABB area = new AABB(hearth.center()).inflate(ADOPTION_RADIUS);
        return level.getEntitiesOfClass(ArchitectEntity.class, area,
                        candidate -> candidate.isAlive()
                                && candidate.isBoundToHearthAssessor(hearth.id()))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private static SpawnPoint findSpawnPosition(
            ServerLevel level, ReturnedHearthSavedData.HearthRecord hearth) {
        for (BlockPos offset : HearthArchitectPolicy.spawnOffsets(hearth.layoutSeed())) {
            BlockPos probe = hearth.center().offset(offset);
            if (!level.isLoaded(probe)) {
                continue;
            }
            BlockPos surface = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, probe);
            SpawnPoint spawnPoint = resolveSpawnPoint(level, surface);
            if (spawnPoint != null) {
                return spawnPoint;
            }
        }
        return null;
    }

    private static SpawnPoint resolveSpawnPoint(ServerLevel level, BlockPos pos) {
        if (pos.getY() < 60 || !level.isLoaded(pos) || !level.isLoaded(pos.above())
                || !level.isLoaded(pos.below())) {
            return null;
        }

        double standingY;
        BlockState atSurface = level.getBlockState(pos);
        if (atSurface.is(Blocks.SNOW)) {
            if (!supportsSnowSurface(level, pos)) {
                return null;
            }
            VoxelShape snowShape = atSurface.getCollisionShape(level, pos);
            if (snowShape.isEmpty()) {
                return null;
            }
            standingY = pos.getY() + snowShape.max(Direction.Axis.Y);
        } else if (atSurface.getCollisionShape(level, pos).isEmpty()
                && atSurface.getFluidState().isEmpty()) {
            BlockPos below = pos.below();
            BlockState ground = level.getBlockState(below);
            if (ground.is(Blocks.SNOW)) {
                if (!supportsSnowSurface(level, below)) {
                    return null;
                }
                VoxelShape snowShape = ground.getCollisionShape(level, below);
                if (snowShape.isEmpty()) {
                    return null;
                }
                standingY = below.getY() + snowShape.max(Direction.Axis.Y);
            } else if (ground.isFaceSturdy(level, below, Direction.UP)) {
                standingY = pos.getY();
            } else {
                return null;
            }
        } else {
            return null;
        }

        standingY += 0.001D;
        double centerX = pos.getX() + 0.5D;
        double centerZ = pos.getZ() + 0.5D;
        AABB bounds = new AABB(
                centerX - 0.3D, standingY, centerZ - 0.3D,
                centerX + 0.3D, standingY + 1.95D, centerZ + 0.3D);
        return level.noCollision(bounds) ? new SpawnPoint(pos.immutable(), standingY) : null;
    }

    private static boolean supportsSnowSurface(ServerLevel level, BlockPos snowPos) {
        BlockPos supportPos = snowPos.below();
        return level.isLoaded(supportPos)
                && level.getBlockState(supportPos).isFaceSturdy(level, supportPos, Direction.UP);
    }

    private static String shortId(java.util.UUID id) {
        return id.toString().substring(0, 8);
    }

    public record DebugRespawnResult(boolean hearthLoaded, int removed, int spawned) {
    }

    private record SpawnPoint(BlockPos blockPos, double y) {
    }
}

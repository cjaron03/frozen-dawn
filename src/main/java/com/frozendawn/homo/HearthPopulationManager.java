package com.frozendawn.homo;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.MimicEntity;
import com.frozendawn.entity.ReturnedEntity;
import com.frozendawn.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Reconciles persistent INTACT-Hearth residents without loading chunks.
 */
public final class HearthPopulationManager {
    private static final double ADOPTION_RADIUS = 16.0D;
    private static final double DEBUG_REMOVAL_RADIUS = 64.0D;

    private static long residentsSpawned;
    private static long residentsAdopted;
    private static long residentsLost;
    private static String lastFailure = "none";

    private HearthPopulationManager() {
    }

    public static void tick(ServerLevel level) {
        if (level.dimension() != ServerLevel.OVERWORLD
                || level.getGameTime() % HearthPopulationPolicy.CHECK_INTERVAL_TICKS != 0L) {
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

        AABB area = new AABB(hearth.center()).inflate(DEBUG_REMOVAL_RADIUS);
        int removed = 0;
        for (ReturnedEntity returned : level.getEntitiesOfClass(ReturnedEntity.class, area,
                candidate -> candidate.isHearthPopulationResident()
                        && candidate.isBoundToHearth(hearth.id()))) {
            returned.discard();
            removed++;
        }
        for (MimicEntity mimic : level.getEntitiesOfClass(MimicEntity.class, area,
                candidate -> candidate.isBoundToHearthPopulation(hearth.id()))) {
            mimic.discard();
            removed++;
        }
        for (ArchitectEntity architect : level.getEntitiesOfClass(ArchitectEntity.class, area,
                candidate -> candidate.isBoundToHearthPopulation(hearth.id()))) {
            architect.discard();
            removed++;
        }
        data.clearPopulationBindingsForDebug(hearth.id());
        int spawned = reconcile(level);
        return new DebugRespawnResult(true, removed, spawned);
    }

    public static void recordResidentDeath(ServerLevel level, UUID hearthId,
                                           HearthPopulationRole role, UUID entityId) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        if (!data.markPopulationResidentMissing(
                hearthId, role, entityId, level.getGameTime())) {
            return;
        }
        residentsLost++;
        FrozenDawn.LOGGER.info(
                "Hearth resident {} ({}) lost at Hearth {}; replacement eligible in {} ticks",
                shortId(entityId), role.serializedName(), shortId(hearthId),
                HearthPopulationPolicy.RESPAWN_DELAY_TICKS);
    }

    public static String statusLine() {
        return "spawned=" + residentsSpawned
                + " adopted=" + residentsAdopted
                + " lost=" + residentsLost
                + " lastFailure=" + lastFailure;
    }

    public static void reset() {
        residentsSpawned = 0L;
        residentsAdopted = 0L;
        residentsLost = 0L;
        lastFailure = "none";
    }

    private static int reconcile(ServerLevel level) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        ReturnedHearthSavedData.HearthRecord hearth = data
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        if (hearth == null || !HearthPopulationPolicy.canHostPopulation(hearth)
                || !level.isLoaded(hearth.center())) {
            return 0;
        }

        int created = 0;
        for (HearthPopulationRole role : HearthPopulationRole.values()) {
            if (!roleEnabled(role)) {
                continue;
            }
            BlockPos anchor = HearthPopulationPolicy.anchor(hearth, role);
            if (!populationAreaLoaded(level, anchor)) {
                continue;
            }

            ReturnedHearthSavedData.HearthResidentBinding binding =
                    hearth.populationResident(role).orElse(null);
            if (binding != null && binding.entityId().isPresent()) {
                UUID boundId = binding.entityId().orElseThrow();
                Entity bound = level.getEntity(boundId);
                if (isCorrectResident(bound, hearth.id(), role)) {
                    continue;
                }
                // A persistent entity may currently live in an unloaded neighboring chunk.
                // Only confirmed death clears this binding; debug respawn handles corruption.
                continue;
            }

            Entity existing = findExistingResident(level, hearth.id(), role, anchor);
            if (existing != null) {
                if (data.bindPopulationResident(hearth.id(), role, existing.getUUID())) {
                    residentsAdopted++;
                }
                continue;
            }
            if (binding != null && !HearthPopulationPolicy.isReplacementReady(
                    binding.respawnAfterGameTime(), level.getGameTime())) {
                continue;
            }

            SpawnPoint spawnPoint = findSpawnPoint(level, anchor);
            if (spawnPoint == null) {
                lastFailure = role.serializedName() + " anchor has no collision-safe floor";
                continue;
            }
            Entity resident = createResident(level, hearth, role, spawnPoint);
            if (resident == null) {
                continue;
            }
            if (!data.bindPopulationResident(hearth.id(), role, resident.getUUID())) {
                resident.discard();
                lastFailure = role.serializedName() + " SavedData binding rejected";
                continue;
            }

            created++;
            residentsSpawned++;
            lastFailure = "none";
            FrozenDawn.LOGGER.info(
                    "Spawned {} Hearth resident {} for Major Hearth {} at ({}, {}, {})",
                    role.serializedName(), shortId(resident.getUUID()), shortId(hearth.id()),
                    spawnPoint.blockPos().getX(), String.format("%.3f", spawnPoint.y()),
                    spawnPoint.blockPos().getZ());
        }
        return created;
    }

    @Nullable
    private static Entity createResident(ServerLevel level,
                                         ReturnedHearthSavedData.HearthRecord hearth,
                                         HearthPopulationRole role, SpawnPoint spawnPoint) {
        Mob resident = switch (role) {
            case RETURNED, HUNTER -> ModEntities.RETURNED.get().create(
                    level, null, spawnPoint.blockPos(), MobSpawnType.EVENT, true, false);
            case MIMIC -> ModEntities.MIMIC.get().create(
                    level, null, spawnPoint.blockPos(), MobSpawnType.EVENT, true, false);
            case ARCHITECT -> ModEntities.ARCHITECT.get().create(
                    level, null, spawnPoint.blockPos(), MobSpawnType.EVENT, true, false);
        };
        if (resident == null) {
            lastFailure = role.serializedName() + " entity creation returned null";
            return null;
        }

        resident.moveTo(
                spawnPoint.blockPos().getX() + 0.5D,
                spawnPoint.y(),
                spawnPoint.blockPos().getZ() + 0.5D,
                resident.getYRot(), resident.getXRot());
        if (!level.noCollision(resident)) {
            lastFailure = role.serializedName() + " spawn collision changed before insertion";
            return null;
        }

        int textureVariant = HearthPopulationPolicy.textureVariant(
                hearth.layoutSeed(), role);
        switch (role) {
            case RETURNED, HUNTER -> ((ReturnedEntity) resident).bindToHearthPopulation(
                    hearth.id(), role, spawnPoint.blockPos(), textureVariant);
            case MIMIC -> ((MimicEntity) resident).bindToHearthPopulation(
                    hearth.id(), spawnPoint.blockPos());
            case ARCHITECT -> ((ArchitectEntity) resident).bindToHearthPopulation(
                    hearth.id(), spawnPoint.blockPos(), textureVariant);
        }
        if (!level.addFreshEntity(resident)) {
            lastFailure = role.serializedName() + " level rejected entity insertion";
            return null;
        }
        return resident;
    }

    @Nullable
    private static Entity findExistingResident(ServerLevel level, UUID hearthId,
                                               HearthPopulationRole role, BlockPos anchor) {
        AABB area = new AABB(anchor).inflate(ADOPTION_RADIUS);
        return switch (role) {
            case RETURNED, HUNTER -> level.getEntitiesOfClass(ReturnedEntity.class, area,
                            candidate -> candidate.isAlive()
                                    && candidate.isBoundToHearthPopulation(hearthId, role))
                    .stream().findFirst().orElse(null);
            case MIMIC -> level.getEntitiesOfClass(MimicEntity.class, area,
                            candidate -> candidate.isAlive()
                                    && candidate.isBoundToHearthPopulation(hearthId))
                    .stream().findFirst().orElse(null);
            case ARCHITECT -> level.getEntitiesOfClass(ArchitectEntity.class, area,
                            candidate -> candidate.isAlive()
                                    && candidate.isBoundToHearthPopulation(hearthId))
                    .stream().findFirst().orElse(null);
        };
    }

    private static boolean isCorrectResident(@Nullable Entity entity, UUID hearthId,
                                             HearthPopulationRole role) {
        if (entity == null || !entity.isAlive()) {
            return false;
        }
        return switch (role) {
            case RETURNED, HUNTER -> entity instanceof ReturnedEntity returned
                    && returned.isBoundToHearthPopulation(hearthId, role);
            case MIMIC -> entity instanceof MimicEntity mimic
                    && mimic.isBoundToHearthPopulation(hearthId);
            case ARCHITECT -> entity instanceof ArchitectEntity architect
                    && architect.isBoundToHearthPopulation(hearthId);
        };
    }

    private static boolean populationAreaLoaded(ServerLevel level, BlockPos anchor) {
        for (int xOffset = -16; xOffset <= 16; xOffset += 16) {
            for (int zOffset = -16; zOffset <= 16; zOffset += 16) {
                if (!level.isLoaded(anchor.offset(xOffset, 0, zOffset))) {
                    return false;
                }
            }
        }
        return true;
    }

    @Nullable
    private static SpawnPoint findSpawnPoint(ServerLevel level, BlockPos anchor) {
        int[] verticalOffsets = {0, 1, -1, 2, -2};
        for (BlockPos local : HearthPopulationPolicy.localSpawnOffsets()) {
            for (int yOffset : verticalOffsets) {
                SpawnPoint point = resolveSpawnPoint(level, anchor.offset(local).above(yOffset));
                if (point != null) {
                    return point;
                }
            }
        }
        return null;
    }

    @Nullable
    private static SpawnPoint resolveSpawnPoint(ServerLevel level, BlockPos pos) {
        if (pos.getY() < 60 || !level.isLoaded(pos) || !level.isLoaded(pos.above())
                || !level.isLoaded(pos.below())) {
            return null;
        }

        double standingY;
        BlockState atFeet = level.getBlockState(pos);
        if (atFeet.is(Blocks.SNOW)) {
            if (!supportsSnowSurface(level, pos)) {
                return null;
            }
            VoxelShape snow = atFeet.getCollisionShape(level, pos);
            if (snow.isEmpty()) {
                return null;
            }
            standingY = pos.getY() + snow.max(Direction.Axis.Y);
        } else if (atFeet.getFluidState().isEmpty()
                && atFeet.getCollisionShape(level, pos).isEmpty()) {
            BlockPos below = pos.below();
            BlockState floor = level.getBlockState(below);
            if (!floor.isFaceSturdy(level, below, Direction.UP)) {
                return null;
            }
            standingY = pos.getY();
        } else {
            return null;
        }

        if (!level.getBlockState(pos.above()).getFluidState().isEmpty()
                || !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
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
        BlockPos support = snowPos.below();
        return level.isLoaded(support)
                && level.getBlockState(support).isFaceSturdy(level, support, Direction.UP);
    }

    private static boolean roleEnabled(HearthPopulationRole role) {
        return switch (role) {
            case RETURNED, HUNTER -> FrozenDawnConfig.ENABLE_RETURNED.get();
            case MIMIC -> FrozenDawnConfig.ENABLE_MIMIC.get();
            case ARCHITECT -> FrozenDawnConfig.ENABLE_ARCHITECT.get();
        };
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    public record DebugRespawnResult(boolean hearthLoaded, int removed, int spawned) {
    }

    private record SpawnPoint(BlockPos blockPos, double y) {
    }
}

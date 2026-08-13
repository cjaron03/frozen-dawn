package com.frozendawn.world.remnant;

import com.frozendawn.block.FuelProcessingSiloMultiblock;
import com.frozendawn.data.PlayerPlacedBlockTracker;
import com.frozendawn.data.RemnantLureSavedData;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.homo.HearthProtectionPolicy;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.world.ChunkCatchUpManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.level.ServerLevel;

public final class RemnantPlacementPolicy {
    private static final int PLAYER_BUFFER = 24;
    private static final TagKey<net.minecraft.world.level.block.Block> LURE_PROTECTED =
            TagKey.create(Registries.BLOCK,
                    ResourceLocation.fromNamespaceAndPath("frozendawn", "remnant_lure_protected"));

    private RemnantPlacementPolicy() {
    }

    public static Result validate(ServerLevel level, BlockPos origin,
                                  RemnantLureTemplate template, int rotation) {
        int radius = template.radius();
        int minChunkX = (origin.getX() - radius) >> 4;
        int maxChunkX = (origin.getX() + radius) >> 4;
        int minChunkZ = (origin.getZ() - radius) >> 4;
        int maxChunkZ = (origin.getZ() + radius) >> 4;
        if (minChunkX != maxChunkX || minChunkZ != maxChunkZ) {
            return Result.reject("template would cross a chunk boundary");
        }
        if (!level.hasChunk(minChunkX, minChunkZ)) {
            return Result.reject("template chunk is not loaded");
        }
        PlayerPlacedBlockTracker playerBlocks = PlayerPlacedBlockTracker.get(level.getServer());
        if (playerBlocks.hasPlayerPlacedWithin(
                origin, radius + PLAYER_BUFFER, template.height() + PLAYER_BUFFER)) {
            return Result.reject("player construction is within the 24-block safety buffer");
        }
        ReturnedHearthSavedData hearths = ReturnedHearthSavedData.get(level.getServer());
        AABB volume = new AABB(origin.getX() - radius, origin.getY() - 1,
                origin.getZ() - radius, origin.getX() + radius + 1,
                origin.getY() + template.height() + 1, origin.getZ() + radius + 1);
        if (!level.getEntitiesOfClass(LivingEntity.class, volume,
                LivingEntity::isAlive).isEmpty()) {
            return Result.reject("a living entity occupies the footprint");
        }
        Result terrain = validateWalkableShelf(level, origin, template, rotation);
        if (!terrain.accepted()) return terrain;
        for (RemnantLureSavedData.LureRecord lure
                : RemnantLureSavedData.get(level.getServer()).lures()) {
            if (lure.state() != com.frozendawn.entity.RemnantState.RESOLVED
                    && lure.origin().distSqr(origin) < 96L * 96L) {
                return Result.reject("another Remnant lure is too close");
            }
        }
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = 0; y < template.height(); y++) {
                    Result mutable = validateMutablePosition(level, hearths, playerBlocks,
                            origin.offset(x, y, z));
                    if (!mutable.accepted()) return mutable;
                }
            }
        }
        return Result.accept();
    }

    private static Result validateMutablePosition(ServerLevel level,
                                                   ReturnedHearthSavedData hearths,
                                                   PlayerPlacedBlockTracker playerBlocks,
                                                   BlockPos pos) {
        if (!level.isLoaded(pos)) return Result.reject("part of the footprint is unloaded");
        if (HearthProtectionPolicy.isEnvironmentalMutationProtected(hearths, pos)) {
            return Result.reject("footprint intersects a Hearth");
        }
        if (ChunkCatchUpManager.isBloomOrsaProtected(level, pos)
                || FuelProcessingSiloMultiblock.isProtectedFromEnvironmentalDeposit(level, pos)) {
            return Result.reject("footprint intersects protected ORSA infrastructure");
        }
        BlockState existing = level.getBlockState(pos);
        if (playerBlocks.isPlayerPlaced(pos)) {
            return Result.reject("footprint intersects player-placed block at "
                    + pos.toShortString());
        }
        if (existing.is(LURE_PROTECTED)
                || existing.is(ModBlocks.SEALED_LATTICE.get())
                || existing.is(ModBlocks.LAUNCH_PAD.get())
                || existing.is(ModBlocks.TRANSPONDER.get())
                || existing.is(ModBlocks.ROCKET_ENGINE.get())
                || existing.is(ModBlocks.ROCKET_HULL.get())
                || existing.is(ModBlocks.ROCKET_FIN.get())
                || existing.is(ModBlocks.ROCKET_NOSE_CONE.get())) {
            return Result.reject("footprint intersects progression infrastructure");
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) return Result.reject("footprint contains a block entity");
        if (!existing.getFluidState().isEmpty()) {
            return Result.reject("footprint contains fluid at " + pos.toShortString()
                    + " (" + BuiltInRegistries.BLOCK.getKey(existing.getBlock()) + ")");
        }
        return Result.accept();
    }

    private static Result validateWalkableShelf(ServerLevel level, BlockPos origin,
                                                 RemnantLureTemplate template, int rotation) {
        int radius = template.radius();
        int minSurface = Integer.MAX_VALUE;
        int maxSurface = Integer.MIN_VALUE;
        int wellSupported = 0;
        int samples = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos column = origin.offset(x, 0, z);
                int surface = walkableSurfaceY(level, column);
                if (surface == Integer.MIN_VALUE) {
                    return Result.reject("footprint has no sturdy terrain near "
                            + column.getX() + ", " + column.getZ());
                }
                minSurface = Math.min(minSurface, surface);
                maxSurface = Math.max(maxSurface, surface);
                if (Math.abs(surface - origin.getY()) <= 1) wellSupported++;
                samples++;
            }
        }
        if (maxSurface - minSurface > 3
                || minSurface < origin.getY() - 2
                || maxSurface > origin.getY() + 2
                || wellSupported * 4 < samples * 3) {
            return Result.reject("terrain shelf is too steep or unsupported (surface "
                    + minSurface + ".." + maxSurface + ", floor " + origin.getY() + ")");
        }

        int previousSurface = origin.getY();
        for (int step = 1; step <= 4; step++) {
            BlockPos local = new BlockPos(0, 0, -radius - step);
            BlockPos approach = origin.offset(RemnantLureTemplate.rotate(local, rotation));
            int surface = walkableSurfaceY(level, approach);
            if (surface == Integer.MIN_VALUE || Math.abs(surface - previousSurface) > 1) {
                return Result.reject("entrance has no walkable approach near "
                        + approach.getX() + ", " + approach.getZ());
            }
            previousSurface = surface;
        }
        return Result.accept();
    }

    private static int walkableSurfaceY(ServerLevel level, BlockPos column) {
        if (!level.hasChunk(column.getX() >> 4, column.getZ() >> 4)) {
            return Integer.MIN_VALUE;
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                column.getX(), column.getZ());
        for (int feetY = top; feetY >= top - 8; feetY--) {
            BlockPos ground = new BlockPos(column.getX(), feetY - 1, column.getZ());
            BlockState state = level.getBlockState(ground);
            if (!state.getFluidState().isEmpty()) return Integer.MIN_VALUE;
            if (state.isFaceSturdy(level, ground, net.minecraft.core.Direction.UP)) {
                return feetY;
            }
        }
        return Integer.MIN_VALUE;
    }

    public record Result(boolean accepted, String reason) {
        public static Result accept() { return new Result(true, "placement accepted"); }
        public static Result reject(String reason) { return new Result(false, reason); }
    }
}

package com.frozendawn.aggregate;

import com.frozendawn.data.PlayerPlacedBlockTracker;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.homo.HearthProtectionPolicy;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.world.ChunkCatchUpManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/** Deterministic authored modules for the Deposit and permanent Ossuary scar. */
public final class AggregateOssuaryBuilder {
    private static final int FINAL_RADIUS = 13;

    private AggregateOssuaryBuilder() {
    }

    public static boolean validAnchor(ServerLevel level, BlockPos center) {
        if (!loadedSquare(level, center, FINAL_RADIUS + 2)
                || center.distSqr(level.getSharedSpawnPos()) < 256L * 256L
                || nearPlayerRespawn(level, center)
                || PlayerPlacedBlockTracker.get(level.getServer())
                .hasPlayerPlacedWithin(center, 40, 24)) {
            return false;
        }
        ReturnedHearthSavedData hearths = ReturnedHearthSavedData.get(level.getServer());
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int sturdy = 0;
        for (int x = -FINAL_RADIUS; x <= FINAL_RADIUS; x += 4) {
            for (int z = -FINAL_RADIUS; z <= FINAL_RADIUS; z += 4) {
                BlockPos sample = surface(level, center.offset(x, 0, z));
                if (sample == null || protectedAt(level, hearths, sample)
                        || !level.getFluidState(sample).isEmpty()
                        || !level.getFluidState(sample.below()).isEmpty()) {
                    return false;
                }
                minY = Math.min(minY, sample.getY());
                maxY = Math.max(maxY, sample.getY());
                if (level.getBlockState(sample.below()).isFaceSturdy(
                        level, sample.below(), Direction.UP)) sturdy++;
            }
        }
        AABB volume = new AABB(center).inflate(FINAL_RADIUS, 8.0D, FINAL_RADIUS);
        return maxY - minY <= 6 && sturdy >= 36
                && level.getEntitiesOfClass(LivingEntity.class, volume,
                LivingEntity::isAlive).isEmpty();
    }

    private static boolean nearPlayerRespawn(ServerLevel level, BlockPos center) {
        for (var player : level.players()) {
            BlockPos respawn = player.getRespawnPosition();
            if (respawn != null && center.distSqr(respawn) < 128L * 128L) return true;
        }
        return false;
    }

    public static void buildStage(
            ServerLevel level, AggregateSavedData data, AggregateStage stage) {
        BlockPos anchor = data.ossuaryPos().orElse(null);
        if (anchor == null) return;
        RandomSource random = RandomSource.create(data.ossuarySeed() ^ stage.ordinal());
        if (stage == AggregateStage.DEPOSIT) {
            buildDeposit(level, data, anchor, random);
        } else if (stage == AggregateStage.OSSUARY) {
            buildOssuary(level, data, anchor, random);
        } else if (stage == AggregateStage.GESTATION
                || stage == AggregateStage.AWAKENING_ELIGIBLE) {
            buildGestation(level, data, anchor, random);
        }
    }

    private static void buildDeposit(ServerLevel level, AggregateSavedData data,
                                     BlockPos anchor, RandomSource random) {
        for (int i = 0; i < 28; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = random.nextDouble() * 3.5D;
            BlockPos target = surface(level, anchor.offset(
                    Mth.floor(Math.cos(angle) * radius), 0,
                    Mth.floor(Math.sin(angle) * radius)));
            placeOwned(level, data, target,
                    i % 5 == 0 ? ModBlocks.AGGREGATE_RESIDUE.get().defaultBlockState()
                            : ModBlocks.AGGREGATE_MASS.get().defaultBlockState());
        }
    }

    private static void buildOssuary(ServerLevel level, AggregateSavedData data,
                                     BlockPos anchor, RandomSource random) {
        buildDeposit(level, data, anchor, random);
        for (int arc = 0; arc < 7; arc++) {
            double baseAngle = (Math.PI * 2.0D * arc / 7.0D)
                    + random.nextDouble() * 0.35D;
            int radius = 7 + random.nextInt(6);
            int height = 4 + random.nextInt(6);
            for (int step = 0; step <= 12; step++) {
                double t = step / 12.0D;
                double lateral = (t - 0.5D) * radius * 1.8D;
                int x = Mth.floor(Math.cos(baseAngle) * lateral);
                int z = Mth.floor(Math.sin(baseAngle) * lateral);
                int y = Mth.floor(Math.sin(t * Math.PI) * height);
                BlockPos base = surface(level, anchor.offset(x, 0, z));
                if (base == null) continue;
                BlockState rib = ModBlocks.AGGREGATE_RIB.get().defaultBlockState()
                        .setValue(RotatedPillarBlock.AXIS,
                                Math.abs(Math.cos(baseAngle)) > 0.7D
                                        ? Direction.Axis.X : Direction.Axis.Z);
                placeOwned(level, data, base.above(y), rib);
                if ((step & 3) == 0) placeOwned(level, data, base.above(Math.max(0, y - 1)),
                        ModBlocks.AGGREGATE_MASS.get().defaultBlockState());
            }
        }
        for (int i = 0; i < 56; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int radius = 5 + random.nextInt(8);
            BlockPos base = surface(level, anchor.offset(
                    Mth.floor(Math.cos(angle) * radius), 0,
                    Mth.floor(Math.sin(angle) * radius)));
            if (base != null) placeOwned(level, data, base,
                    ModBlocks.AGGREGATE_RESIDUE.get().defaultBlockState());
        }
    }

    private static void buildGestation(ServerLevel level, AggregateSavedData data,
                                       BlockPos anchor, RandomSource random) {
        buildOssuary(level, data, anchor, random);
        for (int y = 0; y <= 5; y++) {
            int radius = Math.max(1, 4 - y / 2);
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + z * z > radius * radius + random.nextInt(3)) continue;
                    placeOwned(level, data, anchor.offset(x, y, z),
                            ModBlocks.AGGREGATE_MASS.get().defaultBlockState());
                }
            }
        }
    }

    private static void placeOwned(ServerLevel level, AggregateSavedData data,
                                   BlockPos pos, BlockState state) {
        if (pos == null || !level.isLoaded(pos)) return;
        BlockState current = level.getBlockState(pos);
        if (!current.canBeReplaced() && !current.is(ModBlocks.AGGREGATE_RESIDUE.get())
                && !current.is(ModBlocks.AGGREGATE_MASS.get())
                && !current.is(ModBlocks.AGGREGATE_RIB.get())) return;
        if (PlayerPlacedBlockTracker.get(level.getServer()).isPlayerPlaced(pos)
                || protectedAt(level, ReturnedHearthSavedData.get(level.getServer()), pos)) {
            return;
        }
        level.setBlock(pos, state, 2);
        data.addOssuaryBlock(pos);
    }

    private static BlockPos surface(ServerLevel level, BlockPos column) {
        if (!level.hasChunkAt(column)) return null;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                column.getX(), column.getZ());
        if (y <= level.getMinBuildHeight() + 2 || y >= level.getMaxBuildHeight() - 12) {
            return null;
        }
        return new BlockPos(column.getX(), y, column.getZ());
    }

    private static boolean loadedSquare(ServerLevel level, BlockPos center, int radius) {
        return level.hasChunkAt(center.offset(radius, 0, radius))
                && level.hasChunkAt(center.offset(radius, 0, -radius))
                && level.hasChunkAt(center.offset(-radius, 0, radius))
                && level.hasChunkAt(center.offset(-radius, 0, -radius));
    }

    private static boolean protectedAt(
            ServerLevel level, ReturnedHearthSavedData hearths, BlockPos pos) {
        return HearthProtectionPolicy.isEnvironmentalMutationProtected(hearths, pos)
                || ChunkCatchUpManager.isBloomOrsaProtected(level, pos)
                || level.getBlockState(pos).is(ModBlocks.SEALED_LATTICE.get())
                || level.getFluidState(pos).is(Fluids.LAVA)
                || level.getFluidState(pos).is(Fluids.WATER);
    }

    public static List<BlockPos> debugPreview(ServerLevel level, BlockPos center) {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = -FINAL_RADIUS; x <= FINAL_RADIUS; x += 4) {
            for (int z = -FINAL_RADIUS; z <= FINAL_RADIUS; z += 4) {
                BlockPos pos = surface(level, center.offset(x, 0, z));
                if (pos != null) positions.add(pos);
            }
        }
        return positions;
    }
}

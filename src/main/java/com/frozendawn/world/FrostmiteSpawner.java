package com.frozendawn.world;

import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.entity.FrostmiteEntity;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class FrostmiteSpawner {

    private FrostmiteSpawner() {}

    public static void tick(ServerLevel level, int currentPhase, float progress) {
        if (currentPhase < 5) return;
        if (!FrozenDawnConfig.ENABLE_FROSTMITE.get()) return;

        long gameTick = level.getGameTime();
        if (gameTick % 160 != 0) return;

        float mobMult = (float) FrozenDawnConfig.MOB_SPAWN_MULTIPLIER.get().doubleValue();
        float spawnChance = currentPhase >= 6 ? Math.min(0.36f, 0.18f * mobMult) : Math.min(0.20f, 0.10f * mobMult);
        int maxNearby = currentPhase >= 6 ? Mth.clamp(Mth.ceil(8 * mobMult), 6, 16) : Mth.clamp(Mth.ceil(5 * mobMult), 4, 10);
        int maxGroup = currentPhase >= 6 ? Mth.clamp(Mth.ceil(2.5f * mobMult), 3, 5) : Mth.clamp(Mth.ceil(1.5f * mobMult), 2, 3);

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            if (player.isCreative()) continue;
            if (level.random.nextFloat() > spawnChance) continue;

            int nearby = level.getEntitiesOfClass(FrostmiteEntity.class, player.getBoundingBox().inflate(32.0)).size();
            if (nearby >= maxNearby) continue;

            BlockPos center = findSpawnPos(level, player, level.random);
            if (center == null) continue;

            int groupSize = Math.min(maxGroup, maxNearby - nearby);
            groupSize = 1 + level.random.nextInt(groupSize);
            spawnCluster(level, center, groupSize, MobSpawnType.NATURAL);
        }
    }

    public static void trySpawnInfestedBreak(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        if (!FrozenDawnConfig.ENABLE_FROSTMITE.get()) return;
        if (player == null || player.isCreative() || player.isSpectator()) return;

        int phase = com.frozendawn.phase.FrozenDawnPhaseTracker.getPhase();
        if (phase < 4) return;
        if (!isInfestedBreakBlock(state)) return;

        float chance = phase >= 6 ? 0.24f : phase == 5 ? 0.16f : 0.12f;
        if (level.random.nextFloat() > chance) return;

        int count = phase >= 6 ? 1 + level.random.nextInt(3) : 1 + level.random.nextInt(2);
        spawnCluster(level, pos.above(), count, MobSpawnType.TRIGGERED);
    }

    private static boolean isInfestedBreakBlock(BlockState state) {
        return state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.BLUE_ICE)
                || state.is(ModBlocks.FROZEN_OBSIDIAN.get())
                || state.is(ModBlocks.FROZEN_COAL_ORE.get());
    }

    private static int spawnCluster(ServerLevel level, BlockPos origin, int count, MobSpawnType spawnType) {
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            BlockPos spawnPos = findNearbySpawnPos(level, origin, level.random);
            if (spawnPos == null) spawnPos = origin;
            FrostmiteEntity mite = ModEntities.FROSTMITE.get().create(level, null, spawnPos, spawnType, true, false);
            if (mite != null) {
                mite.setDeltaMovement(
                        (level.random.nextDouble() - 0.5) * 0.15,
                        0.08 + level.random.nextDouble() * 0.08,
                        (level.random.nextDouble() - 0.5) * 0.15
                );
                level.addFreshEntity(mite);
                spawned++;
            }
        }
        return spawned;
    }

    private static BlockPos findSpawnPos(ServerLevel level, ServerPlayer player, RandomSource random) {
        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double dist = 18 + random.nextInt(15);
            int x = Mth.floor(player.getX() + Math.cos(angle) * dist);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * dist);
            int baseY = player.blockPosition().getY() + random.nextIntBetweenInclusive(-4, 4);

            for (int dy = 4; dy >= -4; dy--) {
                BlockPos candidate = new BlockPos(x, baseY + dy, z);
                if (isValidSpawnPos(level, candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static BlockPos findNearbySpawnPos(ServerLevel level, BlockPos origin, RandomSource random) {
        for (int attempt = 0; attempt < 6; attempt++) {
            BlockPos candidate = origin.offset(
                    random.nextIntBetweenInclusive(-2, 2),
                    random.nextIntBetweenInclusive(-1, 1),
                    random.nextIntBetweenInclusive(-2, 2)
            );
            if (isValidSpawnPos(level, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isValidSpawnPos(ServerLevel level, BlockPos candidate) {
        if (!level.isInWorldBounds(candidate)) return false;
        if (!level.getBlockState(candidate).isAir()) return false;
        if (!level.getBlockState(candidate.above()).isAir()) return false;
        BlockPos below = candidate.below();
        return level.getBlockState(below).isSolidRender(level, below);
    }

    public static void reset() {
        FrostmiteEntity.resetAttachmentTracking();
    }
}

package com.frozendawn.world;

import com.frozendawn.block.AcheroniteCrystalBlock;
import com.frozendawn.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Handles Acheronite Crystal formation and growth in phase 5+.
 *
 * Formation: crystals spawn on top of frozen substrates (frozen dirt, frozen sand,
 * frozen obsidian, blue ice, packed ice) when the temperature is below -80C.
 *
 * Growth: existing crystals advance through stages 0-3 over time.
 * Only stage 3 (full cluster) drops shards when mined.
 *
 * Runs on the same staggered tick as BlockFreezer (alternating ticks).
 */
public final class AcheroniteGrowth {

    private AcheroniteGrowth() {}

    private static final int FORMATION_RADIUS = 48;
    private static final int GROWTH_RADIUS = 24;
    private static final float FORMATION_TEMP_THRESHOLD = -60f;

    // Phase-scaled values
    private static final int P5_FORMATION_CHECKS = 4;
    private static final float P5_FORMATION_CHANCE = 0.03f;   // 3% — rare, exciting find
    private static final int P5_GROWTH_CHECKS = 8;
    private static final float P5_GROWTH_CHANCE = 0.15f;      // slow growth

    private static final int P6_FORMATION_CHECKS = 10;
    private static final float P6_FORMATION_CHANCE = 0.06f;   // 6% — landscape fills in
    private static final int P6_GROWTH_CHECKS = 20;
    private static final float P6_GROWTH_CHANCE = 0.35f;      // fast growth

    public static void tick(ServerLevel level, int phase, float progress, int currentDay, int totalDays) {
        if (phase < 5) return;

        boolean isPhase6 = phase >= 6;
        int formationChecks = isPhase6 ? P6_FORMATION_CHECKS : P5_FORMATION_CHECKS;
        float formationChance = isPhase6 ? P6_FORMATION_CHANCE : P5_FORMATION_CHANCE;
        int growthChecks = isPhase6 ? P6_GROWTH_CHECKS : P5_GROWTH_CHECKS;
        float growthChance = isPhase6 ? P6_GROWTH_CHANCE : P5_GROWTH_CHANCE;

        RandomSource random = level.getRandom();

        for (ServerPlayer player : level.players()) {
            BlockPos origin = player.blockPosition();
            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

            // Surface formation: target the surface using heightmap
            for (int i = 0; i < formationChecks; i++) {
                int x = origin.getX() + random.nextInt(FORMATION_RADIUS * 2 + 1) - FORMATION_RADIUS;
                int z = origin.getZ() + random.nextInt(FORMATION_RADIUS * 2 + 1) - FORMATION_RADIUS;
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                mutable.set(x, surfaceY, z);
                if (!level.isLoaded(mutable)) continue;

                // Scan down through snow/air to find the actual ground
                for (int dy = 0; dy <= 6; dy++) {
                    mutable.set(x, surfaceY - dy, z);
                    BlockState at = level.getBlockState(mutable);
                    if (at.isAir() || at.is(Blocks.SNOW) || at.is(Blocks.SNOW_BLOCK)) continue;

                    // Found a solid block — check if it's a valid substrate
                    if (!isValidSubstrate(at)) break;

                    // Check air above for crystal placement
                    BlockPos crystalPos = mutable.above();
                    BlockState aboveState = level.getBlockState(crystalPos);
                    if (!aboveState.isAir() && !aboveState.is(Blocks.SNOW)) break;

                    float temp = TemperatureManager.getTemperatureAt(level, crystalPos, currentDay, totalDays);
                    if (temp > FORMATION_TEMP_THRESHOLD) break;
                    if (random.nextFloat() >= formationChance) break;

                    // Clear snow at crystal position if needed, then place
                    if (aboveState.is(Blocks.SNOW)) {
                        level.destroyBlock(crystalPos, false);
                    }
                    level.setBlock(crystalPos,
                            ModBlocks.ACHERONITE_CRYSTAL.get().defaultBlockState()
                                    .setValue(AcheroniteCrystalBlock.AGE, 0), 3);
                    clearSnowAround(level, crystalPos, 2);
                    break;
                }
            }

            // Underground formation: random Y scan for cave crystals
            for (int i = 0; i < 2; i++) {
                int x = origin.getX() + random.nextInt(FORMATION_RADIUS * 2 + 1) - FORMATION_RADIUS;
                int z = origin.getZ() + random.nextInt(FORMATION_RADIUS * 2 + 1) - FORMATION_RADIUS;
                int y = random.nextIntBetweenInclusive(level.getMinBuildHeight() + 1, 0);
                mutable.set(x, y, z);
                if (!level.isLoaded(mutable)) continue;

                BlockState state = level.getBlockState(mutable);
                if (!state.isAir()) continue;

                BlockState belowState = level.getBlockState(mutable.below());
                if (!isValidSubstrate(belowState)) continue;

                float temp = TemperatureManager.getTemperatureAt(level, mutable, currentDay, totalDays);
                if (temp > FORMATION_TEMP_THRESHOLD) continue;
                if (random.nextFloat() >= formationChance) continue;

                level.setBlock(mutable.immutable(),
                        ModBlocks.ACHERONITE_CRYSTAL.get().defaultBlockState()
                                .setValue(AcheroniteCrystalBlock.AGE, 0), 3);
            }

            // Surface growth: scan down from heightmap to find existing crystals
            for (int i = 0; i < growthChecks; i++) {
                int x = origin.getX() + random.nextInt(GROWTH_RADIUS * 2 + 1) - GROWTH_RADIUS;
                int z = origin.getZ() + random.nextInt(GROWTH_RADIUS * 2 + 1) - GROWTH_RADIUS;
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                // Scan down through snow/air to find crystals
                for (int dy = 0; dy <= 8; dy++) {
                    mutable.set(x, surfaceY - dy, z);
                    if (!level.isLoaded(mutable)) break;

                    BlockState state = level.getBlockState(mutable);
                    if (!state.is(ModBlocks.ACHERONITE_CRYSTAL.get())) {
                        if (!state.isAir() && !state.is(Blocks.SNOW) && !state.is(Blocks.SNOW_BLOCK)) break;
                        continue;
                    }

                    int age = state.getValue(AcheroniteCrystalBlock.AGE);
                    if (age >= 3) break;

                    float temp = TemperatureManager.getTemperatureAt(level, mutable, currentDay, totalDays);
                    if (temp > FORMATION_TEMP_THRESHOLD) break;
                    if (random.nextFloat() >= growthChance) break;

                    BlockPos crystalPos = mutable.immutable();
                    level.setBlock(crystalPos,
                            state.setValue(AcheroniteCrystalBlock.AGE, age + 1), 3);
                    clearSnowAround(level, crystalPos, 2);
                    break;
                }
            }
        }
    }

    /** Clears snow layers and snow blocks within a horizontal radius around a crystal. */
    private static void clearSnowAround(ServerLevel level, BlockPos center, int radius) {
        BlockPos.MutableBlockPos check = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                // Check at crystal Y and one above (snow could be at either level)
                for (int dy = 0; dy <= 1; dy++) {
                    check.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState s = level.getBlockState(check);
                    if (s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK)) {
                        level.destroyBlock(check.immutable(), false);
                    }
                }
            }
        }
    }

    private static boolean isValidSubstrate(BlockState state) {
        return state.is(ModBlocks.FROZEN_DIRT.get())
                || state.is(ModBlocks.FROZEN_SAND.get())
                || state.is(ModBlocks.FROZEN_OBSIDIAN.get())
                || state.is(Blocks.BLUE_ICE)
                || state.is(Blocks.PACKED_ICE);
    }
}

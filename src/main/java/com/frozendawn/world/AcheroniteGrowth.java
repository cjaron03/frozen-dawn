package com.frozendawn.world;

import com.frozendawn.block.AcheroniteCrystalBlock;
import com.frozendawn.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

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
    private static final int LOCAL_FORMATION_DENSITY_RADIUS = 6;
    private static final int LOCAL_FORMATION_DENSITY_CAP = 8;
    private static final long FORMATION_CHUNK_COOLDOWN_TICKS = 600L; // 30s
    private static final int COOLDOWN_PRUNE_INTERVAL_TICKS = 200;

    private static final Map<Long, Long> formationChunkCooldowns = new HashMap<>();

    // Phase-scaled values
    private static final int P5_FORMATION_CHECKS = 4;
    private static final float P5_FORMATION_CHANCE = 0.03f;   // 3% — rare, exciting find
    private static final int P5_GROWTH_CHECKS = 8;
    private static final float P5_GROWTH_CHANCE = 0.15f;      // slow growth

    private static final int P6_FORMATION_CHECKS = 10;
    private static final float P6_FORMATION_CHANCE = 0.06f;   // 6% — landscape fills in
    private static final int P6_GROWTH_CHECKS = 20;
    private static final float P6_GROWTH_CHANCE = 0.35f;      // fast growth

    public static void reset() {
        formationChunkCooldowns.clear();
    }

    public static void tick(ServerLevel level, int phase, float progress, int currentDay, int totalDays) {
        if (phase < 5) return;

        boolean isPhase6 = phase >= 6;
        int formationChecks = isPhase6 ? P6_FORMATION_CHECKS : P5_FORMATION_CHECKS;
        float formationChance = isPhase6 ? P6_FORMATION_CHANCE : P5_FORMATION_CHANCE;
        int growthChecks = isPhase6 ? P6_GROWTH_CHECKS : P5_GROWTH_CHECKS;
        float growthChance = isPhase6 ? P6_GROWTH_CHANCE : P5_GROWTH_CHANCE;
        long gameTime = level.getGameTime();

        if (gameTime % COOLDOWN_PRUNE_INTERVAL_TICKS == 0) {
            pruneExpiredChunkCooldowns(gameTime);
        }

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
                if (ThermalVentRegistry.isVolcanicField(level, mutable)) continue;

                // Scan down through snow/air to find the actual ground
                for (int dy = 0; dy <= 6; dy++) {
                    mutable.set(x, surfaceY - dy, z);
                    if (ThermalVentRegistry.isVolcanicField(level, mutable)) break;
                    BlockState at = level.getBlockState(mutable);
                    if (at.isAir() || at.is(Blocks.SNOW) || at.is(Blocks.SNOW_BLOCK)) continue;

                    // Found a solid block — check if it's a valid substrate
                    if (!isValidSubstrate(at)) break;

                    // Check air above for crystal placement
                    BlockPos crystalPos = mutable.above();
                    BlockState aboveState = level.getBlockState(crystalPos);
                    if (!aboveState.isAir() && !aboveState.is(Blocks.SNOW) && !aboveState.is(Blocks.SNOW_BLOCK)) {
                        break;
                    }
                    FormationSkipReason skipReason = getFormationSkipReason(level, crystalPos, gameTime);
                    if (skipReason != FormationSkipReason.NONE) {
                        break;
                    }

                    float temp = TemperatureManager.getTemperatureAt(level, crystalPos, currentDay, totalDays);
                    if (temp > FORMATION_TEMP_THRESHOLD) break;
                    if (random.nextFloat() >= formationChance) break;

                    boolean buried = aboveState.is(Blocks.SNOW) || aboveState.is(Blocks.SNOW_BLOCK);

                    if (buried) {
                        level.destroyBlock(crystalPos, false);
                    }
                    level.setBlock(crystalPos,
                            ModBlocks.ACHERONITE_CRYSTAL.get().defaultBlockState()
                                    .setValue(AcheroniteCrystalBlock.AGE, 0)
                                    .setValue(AcheroniteCrystalBlock.BURIED, buried), 3);
                    markFormationAt(crystalPos, gameTime);
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
                if (ThermalVentRegistry.isVolcanicField(level, mutable)) continue;

                BlockState state = level.getBlockState(mutable);
                if (!state.isAir()) continue;

                BlockState belowState = level.getBlockState(mutable.below());
                if (!isValidSubstrate(belowState)) continue;
                FormationSkipReason skipReason = getFormationSkipReason(level, mutable, gameTime);
                if (skipReason != FormationSkipReason.NONE) {
                    continue;
                }

                float temp = TemperatureManager.getTemperatureAt(level, mutable, currentDay, totalDays);
                if (temp > FORMATION_TEMP_THRESHOLD) continue;
                if (random.nextFloat() >= formationChance) continue;

                level.setBlock(mutable.immutable(),
                        ModBlocks.ACHERONITE_CRYSTAL.get().defaultBlockState()
                                .setValue(AcheroniteCrystalBlock.AGE, 0)
                                .setValue(AcheroniteCrystalBlock.BURIED, false), 3);
                markFormationAt(mutable, gameTime);
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
                    if (ThermalVentRegistry.isVolcanicField(level, mutable)) break;

                    BlockState state = level.getBlockState(mutable);
                    if (!state.is(ModBlocks.ACHERONITE_CRYSTAL.get())) {
                        if (!state.isAir() && !state.is(Blocks.SNOW) && !state.is(Blocks.SNOW_BLOCK)) break;
                        continue;
                    }

                    int age = state.getValue(AcheroniteCrystalBlock.AGE);
                    boolean buried = state.getValue(AcheroniteCrystalBlock.BURIED);
                    if (buried && !AcheroniteCrystalBlock.hasSnowCover(level, mutable)) {
                        state = state.setValue(AcheroniteCrystalBlock.BURIED, false);
                        level.setBlock(mutable.immutable(), state, 3);
                        buried = false;
                    }

                    if (age >= 3) break;

                    float temp = TemperatureManager.getTemperatureAt(level, mutable, currentDay, totalDays);
                    if (temp > FORMATION_TEMP_THRESHOLD) break;
                    if (random.nextFloat() >= growthChance) break;

                    BlockPos crystalPos = mutable.immutable();
                    int nextAge = Math.min(3, age + 1);
                    BlockState nextState = buried
                            ? state.setValue(AcheroniteCrystalBlock.AGE, nextAge)
                                    .setValue(AcheroniteCrystalBlock.BURIED, nextAge < 3)
                            : state.setValue(AcheroniteCrystalBlock.AGE, nextAge);

                    if (nextAge == 3 && buried && promoteMatureCrystal(level, crystalPos, nextState)) {
                        break;
                    }

                    level.setBlock(crystalPos, nextState, 3);
                    break;
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

    private static FormationSkipReason getFormationSkipReason(ServerLevel level, BlockPos pos, long gameTime) {
        if (isChunkOnFormationCooldown(pos, gameTime)) {
            return FormationSkipReason.COOLDOWN;
        }
        if (countNearbyCrystals(level, pos, LOCAL_FORMATION_DENSITY_RADIUS, LOCAL_FORMATION_DENSITY_CAP)
                >= LOCAL_FORMATION_DENSITY_CAP) {
            return FormationSkipReason.DENSITY;
        }
        return FormationSkipReason.NONE;
    }

    private static boolean isChunkOnFormationCooldown(BlockPos pos, long gameTime) {
        long chunkKey = ChunkPos.asLong(pos);
        Long cooldownEnd = formationChunkCooldowns.get(chunkKey);
        if (cooldownEnd == null) {
            return false;
        }
        if (cooldownEnd <= gameTime) {
            formationChunkCooldowns.remove(chunkKey);
            return false;
        }
        return true;
    }

    private static void markFormationAt(BlockPos pos, long gameTime) {
        long chunkKey = ChunkPos.asLong(pos);
        formationChunkCooldowns.put(chunkKey, gameTime + FORMATION_CHUNK_COOLDOWN_TICKS);
    }

    private static void pruneExpiredChunkCooldowns(long gameTime) {
        if (formationChunkCooldowns.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<Long, Long>> iterator = formationChunkCooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() <= gameTime) {
                iterator.remove();
            }
        }
    }

    private static int countNearbyCrystals(ServerLevel level, BlockPos pos, int radius, int cap) {
        int count = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (!level.getBlockState(cursor).is(ModBlocks.ACHERONITE_CRYSTAL.get())) {
                        continue;
                    }

                    count++;
                    if (count >= cap) {
                        return count;
                    }
                }
            }
        }
        return count;
    }

    private enum FormationSkipReason {
        NONE,
        DENSITY,
        COOLDOWN
    }

    private static boolean promoteMatureCrystal(ServerLevel level, BlockPos crystalPos, BlockState matureState) {
        int currentDepth = AcheroniteCrystalBlock.getSnowSupportDepth(level, crystalPos);
        int desiredDepth = Math.min(
                3,
                SnowAccumulator.getLocalSnowDepthForCrystal(level, crystalPos)
        );
        if (currentDepth >= desiredDepth) {
            return false;
        }

        BlockPos currentPos = crystalPos;
        while (currentDepth < desiredDepth) {
            BlockPos abovePos = currentPos.above();
            BlockState aboveState = level.getBlockState(abovePos);
            if (!aboveState.isAir() && !aboveState.is(Blocks.SNOW) && !aboveState.is(Blocks.SNOW_BLOCK)) {
                return false;
            }

            if (!aboveState.isAir()) {
                level.destroyBlock(abovePos, false);
            }

            level.setBlock(currentPos, Blocks.SNOW_BLOCK.defaultBlockState(), 3);
            level.setBlock(abovePos, matureState.setValue(AcheroniteCrystalBlock.BURIED, false), 3);
            currentPos = abovePos;
            currentDepth++;
        }
        return true;
    }
}

package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.FuelProcessingSiloMultiblock;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.ChunkEpochState;
import com.frozendawn.data.MonitoringStationState;
import com.frozendawn.data.OrsaStructureState;
import com.frozendawn.data.PlayerPlacedBlockTracker;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.phase.PhaseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies coarse apocalypse epochs to naturally loaded chunks.
 *
 * This is not a missed-random-tick simulator. It makes stale/new chunks visually
 * believable for the current phase while staying inside a bounded server budget.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class ChunkCatchUpManager {
    public static final int TRANSFORM_VERSION = 3;

    private static final long NORMAL_BUDGET_NANOS = 2_500_000L;
    private static final long BURST_BUDGET_NANOS = 5_000_000L;
    private static final long PREWARM_BUDGET_NANOS = 8_000_000L;
    private static final int NORMAL_BLOCK_BUDGET = 16_384;
    private static final int BURST_BLOCK_BUDGET = 49_152;
    private static final int PREWARM_BLOCK_BUDGET = 49_152;
    private static final int FRESH_BURST_THRESHOLD = 64;
    private static final int NEAR_BURST_THRESHOLD = 48;
    private static final int NEAR_BURST_RADIUS_CHUNKS = 12;
    private static final int SURFACE_COLUMN_DEPTH = 48;
    private static final int TREE_CLEAR_DEPTH = 96;
    private static final int VOLUME_SAMPLES_PER_CHUNK = 768;
    private static final float FROZEN_ATMOSPHERE_TEMP = -150.0f;

    private static final Set<Long> pendingChunks = ConcurrentHashMap.newKeySet();
    private static final Set<Long> freshChunks = ConcurrentHashMap.newKeySet();
    private static boolean initialPrewarmDone;
    private static long processedChunks;
    private static long completedChunks;
    private static long lastTickNanos;
    private static long maxTickNanos;
    private static int lastTickBlocks;
    private static CatchUpMode lastMode = CatchUpMode.NORMAL;

    private ChunkCatchUpManager() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != ServerLevel.OVERWORLD) {
            return;
        }

        ApocalypseState apocalypse = ApocalypseState.get(level.getServer());
        int phase = apocalypse.getPhase();
        if (phase < 2) {
            return;
        }

        int chunkX = event.getChunk().getPos().x;
        int chunkZ = event.getChunk().getPos().z;
        ChunkEpochState state = ChunkEpochState.get(level.getServer());
        int currentDay = apocalypse.getCurrentDay();

        if (event.isNewChunk() || state.needsCatchUp(chunkX, chunkZ, TRANSFORM_VERSION, currentDay)) {
            long key = ChunkEpochState.pack(chunkX, chunkZ);
            pendingChunks.add(key);
            if (event.isNewChunk()) {
                freshChunks.add(key);
            }
        }
    }

    public static void tick(ServerLevel level, ApocalypseState apocalypse) {
        if (level.dimension() != ServerLevel.OVERWORLD || level.players().isEmpty() || pendingChunks.isEmpty()) {
            return;
        }
        if (apocalypse.getPhase() < 2) {
            return;
        }

        CatchUpMode mode = selectMode(level);
        boolean prewarm = mode == CatchUpMode.PREWARM;
        long budgetNanos = mode.budgetNanos;
        int blockBudget = mode.blockBudget;
        long start = System.nanoTime();
        int blocks = 0;

        ChunkEpochState epochState = ChunkEpochState.get(level.getServer());
        while (!pendingChunks.isEmpty()
                && blocks < blockBudget
                && System.nanoTime() - start < budgetNanos) {
            long key = selectNextChunk(level);
            if (key == Long.MIN_VALUE) {
                break;
            }

            int chunkX = ChunkEpochState.unpackChunkX(key);
            int chunkZ = ChunkEpochState.unpackChunkZ(key);
            if (!isChunkLoaded(level, chunkX, chunkZ)) {
                pendingChunks.remove(key);
                freshChunks.remove(key);
                continue;
            }

            ChunkEpochState.Record record = epochState.getOrCreate(chunkX, chunkZ);
            if (isProtectedOrsaChunk(level, chunkX, chunkZ)) {
                record.complete(TRANSFORM_VERSION, apocalypse.getCurrentDay(),
                        apocalypse.getPhase(), apocalypse.getProgress(), level.getGameTime());
                pendingChunks.remove(key);
                freshChunks.remove(key);
                epochState.setDirty();
                continue;
            }
            if (record.complete()
                    && record.transformVersion() >= TRANSFORM_VERSION
                    && record.targetDay() >= apocalypse.getCurrentDay()) {
                pendingChunks.remove(key);
                freshChunks.remove(key);
                continue;
            }
            if (record.complete()
                    || record.transformVersion() < TRANSFORM_VERSION
                    || record.targetDay() < apocalypse.getCurrentDay()) {
                record.begin(TRANSFORM_VERSION, apocalypse.getCurrentDay(),
                        apocalypse.getPhase(), apocalypse.getProgress());
            }

            SliceResult result = processSlice(level, apocalypse, record, blockBudget - blocks,
                    start, budgetNanos);
            blocks += result.processed();
            processedChunks++;

            if (result.complete()) {
                record.complete(TRANSFORM_VERSION, apocalypse.getCurrentDay(),
                        apocalypse.getPhase(), apocalypse.getProgress(), level.getGameTime());
                pendingChunks.remove(key);
                freshChunks.remove(key);
                completedChunks++;
            }
            epochState.setDirty();
        }

        if (prewarm) {
            initialPrewarmDone = true;
        }
        lastTickNanos = System.nanoTime() - start;
        maxTickNanos = Math.max(maxTickNanos, lastTickNanos);
        lastTickBlocks = blocks;
        lastMode = mode;
    }

    private static SliceResult processSlice(ServerLevel level, ApocalypseState apocalypse,
                                            ChunkEpochState.Record record, int blockBudget,
                                            long startNanos, long budgetNanos) {
        int passIndex = record.passIndex();
        int cursor = record.cursor();
        int processed = 0;

        while (passIndex < Pass.values().length
                && processed < blockBudget
                && System.nanoTime() - startNanos < budgetNanos) {
            Pass pass = Pass.values()[passIndex];
            int max = pass.maxCursor(level);
            while (cursor < max
                    && processed < blockBudget
                    && System.nanoTime() - startNanos < budgetNanos) {
                applyPass(level, apocalypse, record.chunkX(), record.chunkZ(), pass, cursor);
                cursor++;
                processed++;
            }

            if (cursor >= max) {
                passIndex++;
                cursor = 0;
            }
        }

        record.advance(passIndex, cursor, level.getGameTime());
        return new SliceResult(passIndex >= Pass.values().length, processed);
    }

    private static void applyPass(ServerLevel level, ApocalypseState apocalypse, int chunkX, int chunkZ,
                                  Pass pass, int cursor) {
        int x = (chunkX << 4) + (cursor & 15);
        int z = (chunkZ << 4) + ((cursor >> 4) & 15);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        switch (pass) {
            case SURFACE -> {
                BlockPos ground = SurfaceColumnScanner.findGroundBelowCover(
                        level, x, z, SurfaceColumnScanner.DEFAULT_MAX_SCAN_DEPTH);
                if (ground == null) {
                    return;
                }
                applySurfaceCatchUp(level, apocalypse, ground);
                applySnowCatchUp(level, apocalypse, ground, chunkX, chunkZ);
            }
            case TREE_CLEAR -> {
                int column = cursor & 255;
                int columnX = (chunkX << 4) + (column & 15);
                int columnZ = (chunkZ << 4) + ((column >> 4) & 15);
                clearTreeColumn(level, apocalypse, columnX, columnZ);
            }
            case SURFACE_COLUMN -> {
                int column = cursor & 255;
                int columnX = (chunkX << 4) + (column & 15);
                int columnZ = (chunkZ << 4) + ((column >> 4) & 15);
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, columnX, columnZ);
                int offset = (cursor >> 8) - 8;
                mutable.set(columnX, surfaceY + offset, columnZ);
                if (!level.isLoaded(mutable)) {
                    return;
                }
                BlockPos pos = mutable.immutable();
                applyVegetationCatchUp(level, apocalypse, pos, chunkX, chunkZ);
                applyVolumeCatchUp(level, apocalypse, pos);
            }
            case VOLUME_SAMPLE -> {
                RandomSource random = randomFor(level, chunkX, chunkZ,
                        new BlockPos(chunkX, cursor, chunkZ), 0xC0A75EED);
                int sampleX = (chunkX << 4) + random.nextInt(16);
                int sampleZ = (chunkZ << 4) + random.nextInt(16);
                int y = random.nextIntBetweenInclusive(level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
                mutable.set(sampleX, y, sampleZ);
                if (!level.isLoaded(mutable)) {
                    return;
                }
                applyVolumeCatchUp(level, apocalypse, mutable.immutable());
            }
            case COAL_SAMPLE -> {
                RandomSource random = randomFor(level, chunkX, chunkZ,
                        new BlockPos(chunkX, cursor, chunkZ), 0xC041C0DE);
                int sampleX = (chunkX << 4) + random.nextInt(16);
                int sampleZ = (chunkZ << 4) + random.nextInt(16);
                int y = random.nextIntBetweenInclusive(Math.max(0, level.getMinBuildHeight()), level.getMaxBuildHeight() - 1);
                mutable.set(sampleX, y, sampleZ);
                if (!level.isLoaded(mutable)) {
                    return;
                }
                applyVolumeCatchUp(level, apocalypse, mutable.immutable());
            }
            case ATMOSPHERE -> {
                if (!PhaseManager.isVacuumActive(apocalypse.getPhase(), apocalypse.getProgress())) {
                    return;
                }
                BlockPos support = SurfaceColumnScanner.findSnowSupportBelowCover(
                        level, x, z, SurfaceColumnScanner.DEFAULT_MAX_SCAN_DEPTH);
                if (support == null) {
                    return;
                }
                applyAtmosphereCatchUp(level, apocalypse, support, chunkX, chunkZ);
            }
        }
    }

    private static void applySurfaceCatchUp(ServerLevel level, ApocalypseState apocalypse, BlockPos pos) {
        if (!canMutate(level, pos)) {
            return;
        }

        int phase = apocalypse.getPhase();
        float progress = apocalypse.getProgress();
        BlockState state = level.getBlockState(pos);

        if (PhaseManager.isVacuumActive(phase, progress)
                && level.canSeeSky(pos.above())
                && (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK))) {
            setEpochBlock(level, pos, Blocks.ICE.defaultBlockState());
            return;
        }
        if (state.is(Blocks.FARMLAND) && phase >= 2) {
            setEpochBlock(level, pos, Blocks.DIRT.defaultBlockState());
            return;
        }
        if (state.is(Blocks.DIRT_PATH) && phase >= 2) {
            setEpochBlock(level, pos, Blocks.DIRT.defaultBlockState());
            return;
        }
        if ((state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)) && phase >= 2) {
            setEpochBlock(level, pos, ModBlocks.DEAD_GRASS_BLOCK.get().defaultBlockState());
            return;
        }
        if (state.is(Blocks.MOSS_BLOCK) && phase >= 2) {
            setEpochBlock(level, pos, Blocks.DIRT.defaultBlockState());
            return;
        }
        if (state.is(ModBlocks.DEAD_GRASS_BLOCK.get()) && phase >= 3) {
            setEpochBlock(level, pos, Blocks.DIRT.defaultBlockState());
            return;
        }
        if ((state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.MUD)) && phase >= 4) {
            setEpochBlock(level, pos, ModBlocks.FROZEN_DIRT.get().defaultBlockState());
            return;
        }
        if ((state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)) && phase >= 3) {
            setEpochBlock(level, pos, ModBlocks.FROZEN_SAND.get().defaultBlockState());
        }
    }

    private static void applyTreeClearCatchUp(ServerLevel level, ApocalypseState apocalypse, BlockPos pos) {
        int phase = apocalypse.getPhase();
        if (phase < 3) {
            return;
        }

        BlockState state = level.getBlockState(pos);
        if (!isTreeClearCandidate(state, phase) || !canMutate(level, pos)) {
            return;
        }
        if (phase >= 5 && isTreeRemnant(state)) {
            setEpochBlock(level, pos, Blocks.AIR.defaultBlockState());
            return;
        }
        if (state.is(BlockTags.LEAVES) || state.is(ModBlocks.DEAD_LEAVES.get())) {
            setEpochBlock(level, pos, phase >= 4 ? Blocks.AIR.defaultBlockState()
                    : ModBlocks.DEAD_LEAVES.get().defaultBlockState());
        }
    }

    private static void clearTreeColumn(ServerLevel level, ApocalypseState apocalypse, int x, int z) {
        if (apocalypse.getPhase() < 3) {
            return;
        }

        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        int minY = Math.max(level.getMinBuildHeight(), surfaceY - TREE_CLEAR_DEPTH);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int y = surfaceY; y >= minY; y--) {
            mutable.set(x, y, z);
            if (!level.isLoaded(mutable)) {
                return;
            }
            applyTreeClearCatchUp(level, apocalypse, mutable.immutable());
        }
    }

    private static void applyVegetationCatchUp(ServerLevel level, ApocalypseState apocalypse,
                                               BlockPos pos, int chunkX, int chunkZ) {
        int phase = apocalypse.getPhase();
        BlockState state = level.getBlockState(pos);
        RandomSource random = randomFor(level, chunkX, chunkZ, pos, 0x51DABEEF);

        if (!isVegetationCandidate(state, phase) || !canMutate(level, pos)) {
            return;
        }

        if (state.is(BlockTags.FLOWERS) && phase >= 2) {
            replacePlantWithDeadBush(level, pos, state);
            return;
        }
        if ((state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN) || state.is(BlockTags.SAPLINGS)) && phase >= 2) {
            setEpochBlock(level, pos, Blocks.DEAD_BUSH.defaultBlockState());
            return;
        }
        if ((state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN)) && phase >= 2) {
            replacePlantWithDeadBush(level, pos, state);
            return;
        }
        if (state.getBlock() instanceof CropBlock && phase >= 3) {
            setEpochBlock(level, pos, Blocks.AIR.defaultBlockState());
            return;
        }
        if (state.is(Blocks.DEAD_BUSH) && phase >= 3) {
            setEpochBlock(level, pos, Blocks.AIR.defaultBlockState());
            return;
        }
        if ((state.is(Blocks.SUGAR_CANE) || state.is(Blocks.BAMBOO) || state.is(Blocks.CACTUS)) && phase >= 2) {
            setEpochBlock(level, pos, Blocks.AIR.defaultBlockState());
            return;
        }
        if ((state.is(Blocks.KELP)
                || state.is(Blocks.KELP_PLANT)
                || state.is(Blocks.SEAGRASS)
                || state.is(Blocks.TALL_SEAGRASS)
                || state.is(Blocks.LILY_PAD)) && phase >= 2) {
            setEpochBlock(level, pos, Blocks.AIR.defaultBlockState());
            return;
        }
        if ((state.is(Blocks.VINE)
                || state.is(Blocks.CAVE_VINES)
                || state.is(Blocks.CAVE_VINES_PLANT)
                || state.is(Blocks.HANGING_ROOTS)) && phase >= 3) {
            setEpochBlock(level, pos, Blocks.AIR.defaultBlockState());
            return;
        }
        if (state.is(BlockTags.LEAVES) && phase >= 2) {
            float chance = switch (phase) {
                case 2 -> 0.20f;
                case 3 -> 0.55f;
                case 4 -> 0.85f;
                default -> 0.98f;
            };
            if (random.nextFloat() < chance) {
                setEpochBlock(level, pos, phase >= 5 ? Blocks.AIR.defaultBlockState()
                        : ModBlocks.DEAD_LEAVES.get().defaultBlockState());
            }
            return;
        }
        if (state.is(ModBlocks.DEAD_LEAVES.get()) && phase >= 3) {
            setEpochBlock(level, pos, phase >= 5 || random.nextFloat() < 0.60f
                    ? Blocks.AIR.defaultBlockState()
                    : state);
            return;
        }
        if (state.is(BlockTags.LOGS) && phase >= 3) {
            if (phase >= 5) {
                setEpochBlock(level, pos, Blocks.AIR.defaultBlockState());
                return;
            }
            Direction.Axis axis = state.hasProperty(RotatedPillarBlock.AXIS)
                    ? state.getValue(RotatedPillarBlock.AXIS)
                    : Direction.Axis.Y;
            BlockState replacement = ModBlocks.DEAD_LOG.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
            setEpochBlock(level, pos, replacement);
            return;
        }
        if ((state.is(ModBlocks.DEAD_LOG.get()) || state.is(ModBlocks.FROZEN_LOG.get())) && phase >= 5) {
            setEpochBlock(level, pos, Blocks.AIR.defaultBlockState());
        }
    }

    private static void applyVolumeCatchUp(ServerLevel level, ApocalypseState apocalypse, BlockPos pos) {
        int phase = apocalypse.getPhase();
        float progress = apocalypse.getProgress();
        BlockState state = level.getBlockState(pos);

        if (!isVolumeCandidate(state, phase) || !canMutate(level, pos)) {
            return;
        }
        if (isFreezeImmune(level, pos, state)) {
            return;
        }
        if (state.is(Blocks.WATER) && phase >= 2) {
            setEpochBlock(level, pos, Blocks.ICE.defaultBlockState());
            return;
        }
        if (state.is(Blocks.ICE) && phase >= 3) {
            setEpochBlock(level, pos, Blocks.PACKED_ICE.defaultBlockState());
            return;
        }
        if (state.is(Blocks.PACKED_ICE) && phase >= 4) {
            setEpochBlock(level, pos, Blocks.BLUE_ICE.defaultBlockState());
            return;
        }
        if (FrozenDawnConfig.ENABLE_LAVA_FREEZING.get()) {
            if (state.is(Blocks.LAVA) && phase >= 3) {
                setEpochBlock(level, pos, Blocks.MAGMA_BLOCK.defaultBlockState());
                return;
            }
            if (state.is(Blocks.MAGMA_BLOCK) && phase >= 4) {
                setEpochBlock(level, pos, Blocks.OBSIDIAN.defaultBlockState());
                return;
            }
            if (state.is(Blocks.OBSIDIAN) && phase >= 4) {
                setEpochBlock(level, pos, ModBlocks.FROZEN_OBSIDIAN.get().defaultBlockState());
                return;
            }
        }
        if (FrozenDawnConfig.ENABLE_FUEL_SCARCITY.get()
                && phase >= FrozenDawnConfig.FUEL_SCARCITY_PHASE.get()
                && pos.getY() >= 0
                && (state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE))) {
            setEpochBlock(level, pos, ModBlocks.FROZEN_COAL_ORE.get().defaultBlockState());
        }
    }

    private static void applySnowCatchUp(ServerLevel level, ApocalypseState apocalypse,
                                         BlockPos supportPos, int chunkX, int chunkZ) {
        int phase = apocalypse.getPhase();
        float progress = apocalypse.getProgress();
        if (phase < 2 || PhaseManager.isVacuumActive(phase, progress)) {
            return;
        }
        if (BlastPitWarmZoneRegistry.isInsideWarmZone(level, supportPos.above())
                || ThermalVentRegistry.isVolcanicField(level, supportPos.above())) {
            return;
        }

        BlockPos snowPos = supportPos.above();
        if (!canMutate(level, snowPos) || !isOpenToSnow(level, snowPos) || !canPlaceSnowOn(level, supportPos)) {
            return;
        }

        RandomSource random = randomFor(level, chunkX, chunkZ, snowPos, 0x5A10C0DE);
        int targetUnits = targetSnowUnits(phase, progress, random);
        if (targetUnits <= 0) {
            return;
        }

        BlockPos.MutableBlockPos cursor = snowPos.mutable();
        int remaining = targetUnits;
        int maxDepth = phase >= 5 ? 3 : 1;
        for (int depth = 0; depth < maxDepth && remaining > 0; depth++) {
            BlockState at = level.getBlockState(cursor);
            if (!at.isAir() && !at.is(Blocks.SNOW) && !at.is(Blocks.SNOW_BLOCK)) {
                return;
            }
            if (!canMutate(level, cursor)) {
                return;
            }

            if (remaining >= 8 && phase >= 5) {
                setEpochBlock(level, cursor.immutable(), Blocks.SNOW_BLOCK.defaultBlockState());
                remaining -= 8;
            } else {
                int layers = Math.max(1, Math.min(7, remaining));
                setEpochBlock(level, cursor.immutable(),
                        Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers));
                remaining = 0;
            }
            cursor.move(Direction.UP);
        }
    }

    private static void applyAtmosphereCatchUp(ServerLevel level, ApocalypseState apocalypse,
                                               BlockPos supportPos, int chunkX, int chunkZ) {
        BlockPos placePos = supportPos.above();
        if (!canMutate(level, placePos)
                || BlastPitWarmZoneRegistry.isInsideWarmZone(level, placePos)
                || ThermalVentRegistry.isVolcanicField(level, placePos)
                || FuelProcessingSiloMultiblock.isProtectedFromEnvironmentalDeposit(level, placePos)
                || !level.canSeeSky(placePos)
                || !level.getBlockState(supportPos).isFaceSturdy(level, supportPos, Direction.UP)) {
            return;
        }

        BlockState at = level.getBlockState(placePos);
        if (!at.isAir() && !at.is(Blocks.SNOW) && !at.is(Blocks.SNOW_BLOCK)) {
            return;
        }

        float temp = TemperatureManager.getTemperatureAt(level, placePos,
                apocalypse.getCurrentDay(), apocalypse.getTotalDays());
        if (temp > FROZEN_ATMOSPHERE_TEMP) {
            return;
        }

        RandomSource random = randomFor(level, chunkX, chunkZ, placePos, 0xA7105000);
        float chance = 0.06f + (apocalypse.getProgress() - PhaseManager.PHASE6_VACUUM_START) * 0.35f;
        if (random.nextFloat() > Math.max(0.04f, Math.min(0.18f, chance))) {
            return;
        }
        setEpochBlock(level, placePos, ModBlocks.FROZEN_ATMOSPHERE.get().defaultBlockState());
    }

    private static int targetSnowUnits(int phase, float progress, RandomSource random) {
        return switch (phase) {
            case 2 -> 1 + random.nextInt(2);
            case 3 -> 2 + random.nextInt(3);
            case 4 -> 4 + random.nextInt(5);
            case 5 -> 12 + random.nextInt(11);
            default -> progress < PhaseManager.PHASE6_MID_START
                    ? 16 + random.nextInt(9)
                    : 8 + random.nextInt(9);
        };
    }

    private static boolean isOpenToSnow(ServerLevel level, BlockPos snowPos) {
        BlockPos.MutableBlockPos cursor = snowPos.mutable();
        while (true) {
            BlockState state = level.getBlockState(cursor);
            if (!state.is(Blocks.SNOW) && !state.is(Blocks.SNOW_BLOCK)) {
                break;
            }
            cursor.move(Direction.UP);
        }
        if (level.canSeeSky(cursor)) {
            return true;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = snowPos.relative(direction);
            if (level.getBlockState(neighbor).isAir() && level.canSeeSky(neighbor)) {
                return true;
            }
        }
        return false;
    }

    private static boolean canPlaceSnowOn(ServerLevel level, BlockPos supportPos) {
        BlockState support = level.getBlockState(supportPos);
        if (support.is(Blocks.ICE)
                || support.is(Blocks.PACKED_ICE)
                || support.is(Blocks.BLUE_ICE)
                || support.is(Blocks.FROSTED_ICE)
                || support.is(ModBlocks.ACHERONITE_CRYSTAL.get())) {
            return false;
        }
        return SurfaceColumnScanner.canSupportSnow(level, supportPos, support);
    }

    private static void replacePlantWithDeadBush(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof DoublePlantBlock) {
            boolean upper = state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER;
            BlockPos upperPos = upper ? pos : pos.above();
            BlockPos lowerPos = upper ? pos.below() : pos;
            if (canMutate(level, upperPos)) {
                setEpochBlock(level, upperPos, Blocks.AIR.defaultBlockState());
            }
            if (canMutate(level, lowerPos)) {
                setEpochBlock(level, lowerPos, Blocks.DEAD_BUSH.defaultBlockState());
            }
        } else {
            setEpochBlock(level, pos, Blocks.DEAD_BUSH.defaultBlockState());
        }
    }

    private static void setEpochBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).equals(state)) {
            return;
        }
        if (level.setBlock(pos, state, 3)) {
            ApocalypseState.get(level.getServer()).recordFrozenBlock();
        }
    }

    private static boolean canMutate(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        PlayerPlacedBlockTracker tracker = PlayerPlacedBlockTracker.get(level.getServer());
        return !tracker.isPlayerPlaced(pos)
                && !FuelProcessingSiloMultiblock.isProtectedFromEnvironmentalDeposit(level, pos)
                && !BlastPitWarmZoneRegistry.isInsideWarmZone(level, pos)
                && !ThermalVentRegistry.isVolcanicField(level, pos);
    }

    private static boolean isFreezeImmune(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(ModBlocks.THERMAL_VENT_POOL.get())
                || state.is(ModBlocks.VENT_LAVA.get())
                || state.is(ModBlocks.SULFUR_CRUST.get())
                || state.is(ModBlocks.HYDROTHERMAL_ROCK.get())
                || state.is(ModBlocks.SCORCHED_GROUND.get())
                || state.is(ModBlocks.VOLCANIC_ASH.get())) {
            return true;
        }
        return ThermalVentRegistry.isFreezeProtected(level, pos)
                || ThermalVentRegistry.isVolcanicField(level, pos);
    }

    private static RandomSource randomFor(ServerLevel level, int chunkX, int chunkZ, BlockPos pos, int salt) {
        long seed = level.getSeed();
        seed ^= (long) chunkX * 0x9E3779B97F4A7C15L;
        seed ^= (long) chunkZ * 0xC2B2AE3D27D4EB4FL;
        seed ^= pos.asLong() * 0x165667B19E3779F9L;
        seed ^= (long) salt * 0x85EBCA77C2B2AE63L;
        seed ^= (seed >>> 33);
        seed *= 0xff51afd7ed558ccdL;
        seed ^= (seed >>> 33);
        seed *= 0xc4ceb9fe1a85ec53L;
        seed ^= (seed >>> 33);
        return RandomSource.create(seed);
    }

    private static long selectNextChunk(ServerLevel level) {
        long bestKey = Long.MIN_VALUE;
        double bestScore = Double.MAX_VALUE;
        for (long key : pendingChunks) {
            int chunkX = ChunkEpochState.unpackChunkX(key);
            int chunkZ = ChunkEpochState.unpackChunkZ(key);
            double score = nearestPlayerDistanceSq(level, chunkX, chunkZ);
            if (freshChunks.contains(key)) {
                score -= 1_000_000.0D;
            }
            if (score < bestScore) {
                bestScore = score;
                bestKey = key;
            }
        }
        return bestKey;
    }

    private static CatchUpMode selectMode(ServerLevel level) {
        if (!initialPrewarmDone) {
            return CatchUpMode.PREWARM;
        }
        if (freshChunks.size() >= FRESH_BURST_THRESHOLD
                || countNearPendingChunks(level, NEAR_BURST_RADIUS_CHUNKS) >= NEAR_BURST_THRESHOLD) {
            return CatchUpMode.BURST;
        }
        return CatchUpMode.NORMAL;
    }

    private static int countNearPendingChunks(ServerLevel level, int radiusChunks) {
        int radiusBlocks = radiusChunks * 16;
        double maxDistanceSq = (double) radiusBlocks * radiusBlocks;
        int count = 0;
        for (long key : pendingChunks) {
            int chunkX = ChunkEpochState.unpackChunkX(key);
            int chunkZ = ChunkEpochState.unpackChunkZ(key);
            if (nearestPlayerDistanceSq(level, chunkX, chunkZ) <= maxDistanceSq) {
                count++;
                if (count >= NEAR_BURST_THRESHOLD) {
                    return count;
                }
            }
        }
        return count;
    }

    private static double nearestPlayerDistanceSq(ServerLevel level, int chunkX, int chunkZ) {
        double centerX = (chunkX << 4) + 8.0D;
        double centerZ = (chunkZ << 4) + 8.0D;
        double best = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - centerX;
            double dz = player.getZ() - centerZ;
            best = Math.min(best, dx * dx + dz * dz);
        }
        return best;
    }

    private static boolean isChunkLoaded(ServerLevel level, int chunkX, int chunkZ) {
        return level.isLoaded(new BlockPos((chunkX << 4) + 8, level.getMinBuildHeight(), (chunkZ << 4) + 8));
    }

    private static boolean isProtectedOrsaChunk(ServerLevel level, int chunkX, int chunkZ) {
        OrsaStructureState orsaState = OrsaStructureState.get(level.getServer());
        if (orsaState.isCampBuilt(chunkX, chunkZ)) {
            return true;
        }
        BlockPos blastPit = orsaState.getBlastPitPos();
        if (blastPit != null && (blastPit.getX() >> 4) == chunkX && (blastPit.getZ() >> 4) == chunkZ) {
            return true;
        }
        for (OrsaStructureState.TowerRecord tower : orsaState.getTowers()) {
            BlockPos anchor = tower.anchorPos();
            if (anchor != null && (anchor.getX() >> 4) == chunkX && (anchor.getZ() >> 4) == chunkZ) {
                return true;
            }
        }

        MonitoringStationState stationState = MonitoringStationState.get(level.getServer());
        if (stationState.isStationBuilt(chunkX, chunkZ)) {
            return true;
        }
        for (BlockPos center : stationState.getBuiltStationCenters()) {
            if (Math.abs((center.getX() >> 4) - chunkX) <= 1
                    && Math.abs((center.getZ() >> 4) - chunkZ) <= 1) {
                return true;
            }
        }
        return false;
    }

    public static String statusLine(ServerLevel level) {
        ChunkEpochState state = ChunkEpochState.get(level.getServer());
        return "queued=" + pendingChunks.size()
                + " fresh=" + freshChunks.size()
                + " records=" + state.recordCount()
                + " version=" + TRANSFORM_VERSION
                + " mode=" + lastMode.label
                + " slices=" + processedChunks
                + " completed=" + completedChunks
                + " lastMs=" + String.format("%.3f", lastTickNanos / 1_000_000.0D)
                + " lastBlocks=" + lastTickBlocks
                + " maxMs=" + String.format("%.3f", maxTickNanos / 1_000_000.0D);
    }

    public static void reset() {
        pendingChunks.clear();
        freshChunks.clear();
        initialPrewarmDone = false;
        processedChunks = 0;
        completedChunks = 0;
        lastTickNanos = 0;
        maxTickNanos = 0;
        lastTickBlocks = 0;
        lastMode = CatchUpMode.NORMAL;
    }

    private enum Pass {
        SURFACE,
        TREE_CLEAR,
        SURFACE_COLUMN,
        VOLUME_SAMPLE,
        COAL_SAMPLE,
        ATMOSPHERE;

        private int maxCursor(ServerLevel level) {
            return switch (this) {
                case SURFACE, ATMOSPHERE -> 16 * 16;
                case TREE_CLEAR -> 16 * 16;
                case SURFACE_COLUMN -> 16 * 16 * SURFACE_COLUMN_DEPTH;
                case VOLUME_SAMPLE, COAL_SAMPLE -> VOLUME_SAMPLES_PER_CHUNK;
            };
        }
    }

    private enum CatchUpMode {
        NORMAL("normal", NORMAL_BUDGET_NANOS, NORMAL_BLOCK_BUDGET),
        BURST("burst", BURST_BUDGET_NANOS, BURST_BLOCK_BUDGET),
        PREWARM("prewarm", PREWARM_BUDGET_NANOS, PREWARM_BLOCK_BUDGET);

        private final String label;
        private final long budgetNanos;
        private final int blockBudget;

        CatchUpMode(String label, long budgetNanos, int blockBudget) {
            this.label = label;
            this.budgetNanos = budgetNanos;
            this.blockBudget = blockBudget;
        }
    }

    private static boolean isVegetationCandidate(BlockState state, int phase) {
        if (phase < 2) {
            return false;
        }
        return state.is(BlockTags.FLOWERS)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.FERN)
                || state.is(BlockTags.SAPLINGS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.SUGAR_CANE)
                || state.is(Blocks.BAMBOO)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.KELP)
                || state.is(Blocks.KELP_PLANT)
                || state.is(Blocks.SEAGRASS)
                || state.is(Blocks.TALL_SEAGRASS)
                || state.is(Blocks.LILY_PAD)
                || state.is(Blocks.DEAD_BUSH)
                || state.is(BlockTags.LEAVES)
                || state.is(ModBlocks.DEAD_LEAVES.get())
                || state.is(BlockTags.LOGS)
                || state.is(Blocks.VINE)
                || state.is(Blocks.CAVE_VINES)
                || state.is(Blocks.CAVE_VINES_PLANT)
                || state.is(Blocks.HANGING_ROOTS)
                || state.getBlock() instanceof CropBlock;
    }

    private static boolean isTreeRemnant(BlockState state) {
        return state.is(BlockTags.LEAVES)
                || state.is(ModBlocks.DEAD_LEAVES.get())
                || state.is(BlockTags.LOGS)
                || state.is(ModBlocks.DEAD_LOG.get())
                || state.is(ModBlocks.FROZEN_LOG.get());
    }

    private static boolean isTreeClearCandidate(BlockState state, int phase) {
        if (phase < 3) {
            return false;
        }
        if (phase >= 5) {
            return isTreeRemnant(state);
        }
        return state.is(BlockTags.LEAVES) || state.is(ModBlocks.DEAD_LEAVES.get());
    }

    private static boolean isVolumeCandidate(BlockState state, int phase) {
        if (phase < 2) {
            return false;
        }
        return state.is(Blocks.WATER)
                || state.is(Blocks.ICE)
                || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.BLUE_ICE)
                || state.is(Blocks.LAVA)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.OBSIDIAN)
                || state.is(Blocks.COAL_ORE)
                || state.is(Blocks.DEEPSLATE_COAL_ORE);
    }

    private record SliceResult(boolean complete, int processed) {
    }
}

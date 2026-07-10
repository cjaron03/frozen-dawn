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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
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

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    public static final int TRANSFORM_VERSION = 4;

    private static final long NORMAL_BUDGET_NANOS = 2_500_000L;
    private static final long BURST_BUDGET_NANOS = 5_000_000L;
    private static final long PREWARM_BUDGET_NANOS = 8_000_000L;
    private static final int NORMAL_BLOCK_BUDGET = 16_384;
    private static final int BURST_BLOCK_BUDGET = 49_152;
    private static final int PREWARM_BLOCK_BUDGET = 49_152;
    private static final int NORMAL_EDIT_BUDGET = 384;
    private static final int BURST_EDIT_BUDGET = 768;
    private static final int PREWARM_EDIT_BUDGET = 1_024;
    private static final int FRESH_BURST_THRESHOLD = 64;
    private static final int NEAR_BURST_THRESHOLD = 48;
    private static final int NEAR_BURST_RADIUS_CHUNKS = 12;
    private static final int SURFACE_COLUMN_DEPTH = 48;
    private static final int TREE_CLEAR_DEPTH = 96;
    private static final int MAX_CATCH_UP_SNOW_DEPTH = 3;
    private static final int MAX_CATCH_UP_SNOW_UNITS = MAX_CATCH_UP_SNOW_DEPTH * 8;
    private static final int VOLUME_SAMPLES_PER_CHUNK = 768;
    private static final int PROTECTION_CELL_SIZE = 8;
    private static final int SILO_PROTECTION_SCAN_XZ_RADIUS = 3;
    private static final int SILO_PROTECTION_SCAN_Y_RADIUS = 4;
    private static final float FROZEN_ATMOSPHERE_TEMP = -150.0f;
    private static final long SLOW_TRACE_THRESHOLD_NANOS = 50_000_000L;
    private static final int EPOCH_SET_BLOCK_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static final Set<Long> pendingChunks = ConcurrentHashMap.newKeySet();
    private static final Set<Long> freshChunks = ConcurrentHashMap.newKeySet();
    private static boolean initialPrewarmDone;
    private static long processedChunks;
    private static long completedChunks;
    private static long lastTickNanos;
    private static long maxTickNanos;
    private static long lastModeNanos;
    private static long maxModeNanos;
    private static long lastSelectNanos;
    private static long maxSelectNanos;
    private static long lastBookkeepingNanos;
    private static long maxBookkeepingNanos;
    private static long lastProcessNanos;
    private static long maxProcessNanos;
    private static int lastTickBlocks;
    private static int lastTickEdits;
    private static int lastEditBudget = NORMAL_EDIT_BUDGET;
    private static int lastChunksTouched;
    private static CatchUpMode lastMode = CatchUpMode.NORMAL;
    private static CatchUpTrace activeTrace;

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

        long modeStart = System.nanoTime();
        CatchUpMode mode = selectMode(level);
        long modeNanos = System.nanoTime() - modeStart;
        boolean prewarm = mode == CatchUpMode.PREWARM;
        long budgetNanos = mode.budgetNanos;
        int blockBudget = mode.blockBudget;
        TickEditBudget editBudget = new TickEditBudget(mode.editBudget);
        long start = System.nanoTime();
        int blocks = 0;
        int chunksTouched = 0;
        long selectNanos = 0;
        long bookkeepingNanos = 0;
        long processNanos = 0;
        CatchUpTrace trace = new CatchUpTrace();
        MutationProtectionContext protectionContext = new MutationProtectionContext(level);

        long bookkeepingStart = System.nanoTime();
        ChunkEpochState epochState = ChunkEpochState.get(level.getServer());
        bookkeepingNanos += System.nanoTime() - bookkeepingStart;
        activeTrace = trace;
        try {
            while (!pendingChunks.isEmpty()
                    && blocks < blockBudget
                    && System.nanoTime() - start < budgetNanos) {
                long selectStart = System.nanoTime();
                long key = selectNextChunk(level);
                selectNanos += System.nanoTime() - selectStart;
                if (key == Long.MIN_VALUE) {
                    break;
                }
                chunksTouched++;

                int chunkX = ChunkEpochState.unpackChunkX(key);
                int chunkZ = ChunkEpochState.unpackChunkZ(key);
                if (!isChunkLoaded(level, chunkX, chunkZ)) {
                    pendingChunks.remove(key);
                    freshChunks.remove(key);
                    continue;
                }

                bookkeepingStart = System.nanoTime();
                ChunkEpochState.Record record = epochState.getOrCreate(chunkX, chunkZ);
                if (isProtectedOrsaChunk(level, chunkX, chunkZ)) {
                    record.complete(TRANSFORM_VERSION, apocalypse.getCurrentDay(),
                            apocalypse.getPhase(), apocalypse.getProgress(), level.getGameTime());
                    pendingChunks.remove(key);
                    freshChunks.remove(key);
                    epochState.setDirty();
                    bookkeepingNanos += System.nanoTime() - bookkeepingStart;
                    continue;
                }
                if (record.complete()
                        && record.transformVersion() >= TRANSFORM_VERSION
                        && record.targetDay() >= apocalypse.getCurrentDay()) {
                    pendingChunks.remove(key);
                    freshChunks.remove(key);
                    bookkeepingNanos += System.nanoTime() - bookkeepingStart;
                    continue;
                }
                if (record.complete()
                        || record.transformVersion() < TRANSFORM_VERSION
                        || record.targetDay() < apocalypse.getCurrentDay()) {
                    record.begin(TRANSFORM_VERSION, apocalypse.getCurrentDay(),
                            apocalypse.getPhase(), apocalypse.getProgress());
                }
                bookkeepingNanos += System.nanoTime() - bookkeepingStart;

                long processStart = System.nanoTime();
                SliceResult result = processSlice(level, apocalypse, record, blockBudget - blocks, editBudget,
                        protectionContext, start, budgetNanos);
                processNanos += System.nanoTime() - processStart;
                blocks += result.processed();
                processedChunks++;

                bookkeepingStart = System.nanoTime();
                if (result.complete()) {
                    record.complete(TRANSFORM_VERSION, apocalypse.getCurrentDay(),
                            apocalypse.getPhase(), apocalypse.getProgress(), level.getGameTime());
                    pendingChunks.remove(key);
                    freshChunks.remove(key);
                    completedChunks++;
                }
                epochState.setDirty();
                bookkeepingNanos += System.nanoTime() - bookkeepingStart;
            }
        } finally {
            activeTrace = null;
        }

        if (prewarm) {
            initialPrewarmDone = true;
        }
        if (editBudget.edits() > 0) {
            apocalypse.recordFrozenBlocks(editBudget.edits());
        }
        lastTickNanos = System.nanoTime() - start;
        maxTickNanos = Math.max(maxTickNanos, lastTickNanos);
        lastModeNanos = modeNanos;
        maxModeNanos = Math.max(maxModeNanos, modeNanos);
        lastSelectNanos = selectNanos;
        maxSelectNanos = Math.max(maxSelectNanos, selectNanos);
        lastBookkeepingNanos = bookkeepingNanos;
        maxBookkeepingNanos = Math.max(maxBookkeepingNanos, bookkeepingNanos);
        lastProcessNanos = processNanos;
        maxProcessNanos = Math.max(maxProcessNanos, processNanos);
        lastTickBlocks = blocks;
        lastTickEdits = editBudget.edits();
        lastEditBudget = mode.editBudget;
        lastChunksTouched = chunksTouched;
        lastMode = mode;
        if (lastTickNanos >= SLOW_TRACE_THRESHOLD_NANOS) {
            FrozenDawn.LOGGER.warn("[ChunkCatchUpTrace] slow tick mode={} queue={} fresh={} chunks={} blocks={} edits={} "
                            + "tickMs={} processMs={} setBlockMs={} setCalls={} setSuccess={} slowest={} passes={} surface={} replacements={}",
                    mode.label,
                    pendingChunks.size(),
                    freshChunks.size(),
                    chunksTouched,
                    blocks,
                    editBudget.edits(),
                    millis(lastTickNanos),
                    millis(processNanos),
                    millis(trace.setBlockNanos),
                    trace.setBlockCalls,
                    trace.setBlockSuccesses,
                    trace.slowestSummary(),
                    trace.passSummary(),
                    trace.surfaceSummary(),
                    trace.replacementSummary());
        }
    }

    private static SliceResult processSlice(ServerLevel level, ApocalypseState apocalypse,
                                            ChunkEpochState.Record record, int blockBudget, TickEditBudget editBudget,
                                            MutationProtectionContext protectionContext, long startNanos,
                                            long budgetNanos) {
        int passIndex = record.passIndex();
        int cursor = record.cursor();
        int processed = 0;

        try {
            while (passIndex < Pass.values().length
                    && processed < blockBudget
                    && System.nanoTime() - startNanos < budgetNanos) {
                Pass pass = Pass.values()[passIndex];
                int max = pass.maxCursor(level);
                while (cursor < max
                        && processed < blockBudget
                        && System.nanoTime() - startNanos < budgetNanos) {
                    CatchUpTrace trace = activeTrace;
                    long applyStart = trace != null ? System.nanoTime() : 0L;
                    applyPass(level, apocalypse, record.chunkX(), record.chunkZ(), pass, cursor, editBudget,
                            protectionContext);
                    if (trace != null) {
                        trace.recordApply(record.chunkX(), record.chunkZ(), pass, cursor,
                                System.nanoTime() - applyStart);
                    }
                    cursor++;
                    processed++;
                }

                if (cursor >= max) {
                    passIndex++;
                    cursor = 0;
                }
            }
        } catch (EditBudgetExceeded ignored) {
        }

        record.advance(passIndex, cursor, level.getGameTime());
        return new SliceResult(passIndex >= Pass.values().length, processed);
    }

    private static void applyPass(ServerLevel level, ApocalypseState apocalypse, int chunkX, int chunkZ,
                                  Pass pass, int cursor, TickEditBudget editBudget,
                                  MutationProtectionContext protectionContext) {
        int x = (chunkX << 4) + (cursor & 15);
        int z = (chunkZ << 4) + ((cursor >> 4) & 15);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        switch (pass) {
            case TREE_CLEAR -> {
                int column = cursor & 255;
                int columnX = (chunkX << 4) + (column & 15);
                int columnZ = (chunkZ << 4) + ((column >> 4) & 15);
                clearTreeColumn(level, apocalypse, columnX, columnZ, editBudget, protectionContext);
            }
            case DETACHED_SNOW -> {
                int column = cursor & 255;
                int columnX = (chunkX << 4) + (column & 15);
                int columnZ = (chunkZ << 4) + ((column >> 4) & 15);
                reconcileDetachedSnowColumn(level, columnX, columnZ, editBudget, protectionContext);
            }
            case SURFACE -> {
                CatchUpTrace trace = activeTrace;
                long scanStart = trace != null ? System.nanoTime() : 0L;
                BlockPos ground = SurfaceColumnScanner.findGroundBelowCover(
                        level, x, z, SurfaceColumnScanner.DEFAULT_MAX_SCAN_DEPTH);
                if (trace != null) {
                    trace.recordSurface(SurfacePart.GROUND_SCAN, System.nanoTime() - scanStart);
                }
                if (ground == null) {
                    return;
                }
                long surfaceStart = trace != null ? System.nanoTime() : 0L;
                applySurfaceCatchUp(level, apocalypse, ground, editBudget, protectionContext);
                if (trace != null) {
                    trace.recordSurface(SurfacePart.SURFACE_BLOCK, System.nanoTime() - surfaceStart);
                }
                long snowStart = trace != null ? System.nanoTime() : 0L;
                applySnowCatchUp(level, apocalypse, ground, chunkX, chunkZ, editBudget, protectionContext);
                if (trace != null) {
                    trace.recordSurface(SurfacePart.SNOW, System.nanoTime() - snowStart);
                }
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
                applyVegetationCatchUp(level, apocalypse, pos, chunkX, chunkZ, editBudget, protectionContext);
                applyVolumeCatchUp(level, apocalypse, pos, editBudget, protectionContext);
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
                applyVolumeCatchUp(level, apocalypse, mutable.immutable(), editBudget, protectionContext);
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
                applyVolumeCatchUp(level, apocalypse, mutable.immutable(), editBudget, protectionContext);
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
                applyAtmosphereCatchUp(level, apocalypse, support, chunkX, chunkZ, editBudget, protectionContext);
            }
        }
    }

    private static void applySurfaceCatchUp(ServerLevel level, ApocalypseState apocalypse, BlockPos pos,
                                            TickEditBudget editBudget, MutationProtectionContext protectionContext) {
        int phase = apocalypse.getPhase();
        float progress = apocalypse.getProgress();
        BlockState state = level.getBlockState(pos);
        BlockState replacement = null;

        CatchUpTrace trace = activeTrace;
        if (PhaseManager.isVacuumActive(phase, progress)
                && (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK))) {
            long skyStart = trace != null ? System.nanoTime() : 0L;
            boolean skyVisible = level.canSeeSky(pos.above());
            if (trace != null) {
                trace.recordSurface(SurfacePart.SURFACE_SKY_CHECK, System.nanoTime() - skyStart);
            }
            if (skyVisible) {
                replacement = Blocks.ICE.defaultBlockState();
            }
        } else if ((state.is(Blocks.FARMLAND) || state.is(Blocks.DIRT_PATH)) && phase >= 2) {
            replacement = Blocks.DIRT.defaultBlockState();
        } else if ((state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)) && phase >= 2) {
            replacement = ModBlocks.DEAD_GRASS_BLOCK.get().defaultBlockState();
        } else if (state.is(Blocks.MOSS_BLOCK) && phase >= 2) {
            replacement = Blocks.DIRT.defaultBlockState();
        } else if (state.is(ModBlocks.DEAD_GRASS_BLOCK.get()) && phase >= 3) {
            replacement = Blocks.DIRT.defaultBlockState();
        } else if ((state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.MUD)) && phase >= 4) {
            replacement = ModBlocks.FROZEN_DIRT.get().defaultBlockState();
        } else if ((state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)) && phase >= 3) {
            replacement = ModBlocks.FROZEN_SAND.get().defaultBlockState();
        }

        if (replacement == null) {
            return;
        }

        long mutateStart = trace != null ? System.nanoTime() : 0L;
        boolean mutable = canMutate(level, pos, protectionContext);
        if (trace != null) {
            trace.recordSurface(SurfacePart.SURFACE_MUTATE_CHECK, System.nanoTime() - mutateStart);
        }
        if (mutable) {
            setEpochBlock(level, pos, replacement, editBudget);
        }
    }

    private static void applyTreeClearCatchUp(ServerLevel level, ApocalypseState apocalypse, BlockPos pos,
                                              TickEditBudget editBudget, MutationProtectionContext protectionContext) {
        int phase = apocalypse.getPhase();
        if (phase < 3) {
            return;
        }

        BlockState state = level.getBlockState(pos);
        if (!isTreeClearCandidate(state, phase) || !canMutate(level, pos, protectionContext)) {
            return;
        }
        if (phase >= 5 && isTreeRemnant(state)) {
            setEpochBlock(level, pos, Blocks.AIR.defaultBlockState(), editBudget);
            return;
        }
        if (state.is(BlockTags.LEAVES) || state.is(ModBlocks.DEAD_LEAVES.get())) {
            setEpochBlock(level, pos, phase >= 4 ? Blocks.AIR.defaultBlockState()
                    : ModBlocks.DEAD_LEAVES.get().defaultBlockState(), editBudget);
        }
    }

    private static void clearTreeColumn(ServerLevel level, ApocalypseState apocalypse, int x, int z,
                                        TickEditBudget editBudget, MutationProtectionContext protectionContext) {
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
            applyTreeClearCatchUp(level, apocalypse, mutable.immutable(), editBudget, protectionContext);
        }
    }

    private static void reconcileDetachedSnowColumn(ServerLevel level, int x, int z,
                                                     TickEditBudget editBudget,
                                                     MutationProtectionContext protectionContext) {
        int topY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        if (topY < level.getMinBuildHeight()) {
            return;
        }

        int minY = Math.max(level.getMinBuildHeight(), topY - TREE_CLEAR_DEPTH);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        List<BlockPos> detachedSnow = new ArrayList<>();
        int detachedUnits = 0;

        for (int y = topY; y >= minY; y--) {
            mutable.set(x, y, z);
            if (!level.isLoaded(mutable)) {
                return;
            }

            BlockState state = level.getBlockState(mutable);
            if ((!state.is(Blocks.SNOW) && !state.is(Blocks.SNOW_BLOCK))
                    || !SurfaceColumnScanner.isDetachedSnow(level, mutable)
                    || !canMutate(level, mutable, protectionContext)) {
                continue;
            }

            detachedSnow.add(mutable.immutable());
            detachedUnits += snowUnits(state);
        }

        if (detachedSnow.isEmpty()) {
            return;
        }

        // Keep removal and collapse in the same budget slice so a retry cannot
        // lose the snow after the unsupported canopy cap has been cleared.
        editBudget.requireCapacity(detachedSnow.size() + MAX_CATCH_UP_SNOW_DEPTH);
        for (BlockPos snowPos : detachedSnow) {
            setEpochBlock(level, snowPos, Blocks.AIR.defaultBlockState(), editBudget);
        }

        collapseSnowToGround(level, x, z, detachedUnits, editBudget, protectionContext);
    }

    private static void collapseSnowToGround(ServerLevel level, int x, int z, int detachedUnits,
                                             TickEditBudget editBudget,
                                             MutationProtectionContext protectionContext) {
        BlockPos supportPos = SurfaceColumnScanner.findSnowSupportBelowCover(level, x, z, TREE_CLEAR_DEPTH);
        if (supportPos == null || !canPlaceSnowOn(level, supportPos)) {
            return;
        }

        BlockPos baseSnowPos = supportPos.above();
        if (BlastPitWarmZoneRegistry.isInsideWarmZone(level, baseSnowPos)
                || ThermalVentRegistry.isVolcanicField(level, baseSnowPos)
                || !isOpenToSnow(level, baseSnowPos)) {
            return;
        }

        int existingUnits = 0;
        BlockPos.MutableBlockPos cursor = baseSnowPos.mutable();
        for (int depth = 0; depth < MAX_CATCH_UP_SNOW_DEPTH; depth++) {
            BlockState state = level.getBlockState(cursor);
            if (state.is(Blocks.SNOW_BLOCK)) {
                existingUnits += 8;
                cursor.move(Direction.UP);
                continue;
            }
            if (state.is(Blocks.SNOW)) {
                existingUnits += state.getValue(SnowLayerBlock.LAYERS);
            }
            break;
        }

        int remaining = Math.min(MAX_CATCH_UP_SNOW_UNITS, existingUnits + detachedUnits);
        cursor.set(baseSnowPos);
        for (int depth = 0; depth < MAX_CATCH_UP_SNOW_DEPTH && remaining > 0; depth++) {
            BlockState current = level.getBlockState(cursor);
            if (!current.isAir() && !current.is(Blocks.SNOW) && !current.is(Blocks.SNOW_BLOCK)) {
                return;
            }
            if (!canMutate(level, cursor, protectionContext)) {
                return;
            }

            BlockState target;
            if (remaining >= 8) {
                target = Blocks.SNOW_BLOCK.defaultBlockState();
                remaining -= 8;
            } else {
                target = Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, remaining);
                remaining = 0;
            }
            setEpochBlock(level, cursor.immutable(), target, editBudget);
            cursor.move(Direction.UP);
        }
    }

    private static int snowUnits(BlockState state) {
        if (state.is(Blocks.SNOW_BLOCK)) {
            return 8;
        }
        if (state.is(Blocks.SNOW)) {
            return state.getValue(SnowLayerBlock.LAYERS);
        }
        return 0;
    }

    private static void applyVegetationCatchUp(ServerLevel level, ApocalypseState apocalypse,
                                               BlockPos pos, int chunkX, int chunkZ, TickEditBudget editBudget,
                                               MutationProtectionContext protectionContext) {
        int phase = apocalypse.getPhase();
        BlockState state = level.getBlockState(pos);
        RandomSource random = randomFor(level, chunkX, chunkZ, pos, 0x51DABEEF);

        if (!isVegetationCandidate(state, phase) || !canMutate(level, pos, protectionContext)) {
            return;
        }

        if (state.is(BlockTags.FLOWERS) && phase >= 2) {
            replacePlantWithDeadBush(level, pos, state, editBudget, protectionContext);
            return;
        }
        if ((state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN) || state.is(BlockTags.SAPLINGS)) && phase >= 2) {
            setEpochBlock(level, pos, Blocks.DEAD_BUSH.defaultBlockState(), editBudget);
            return;
        }
        if ((state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN)) && phase >= 2) {
            replacePlantWithDeadBush(level, pos, state, editBudget, protectionContext);
            return;
        }
        if (state.getBlock() instanceof CropBlock && phase >= 3) {
            setEpochBlock(level, pos, Blocks.AIR.defaultBlockState(), editBudget);
            return;
        }
        if (state.is(Blocks.DEAD_BUSH) && phase >= 3) {
            setEpochBlock(level, pos, Blocks.AIR.defaultBlockState(), editBudget);
            return;
        }
        if ((state.is(Blocks.SUGAR_CANE) || state.is(Blocks.BAMBOO) || state.is(Blocks.CACTUS)) && phase >= 2) {
            setEpochBlock(level, pos, Blocks.AIR.defaultBlockState(), editBudget);
            return;
        }
        if ((state.is(Blocks.KELP)
                || state.is(Blocks.KELP_PLANT)
                || state.is(Blocks.SEAGRASS)
                || state.is(Blocks.TALL_SEAGRASS)
                || state.is(Blocks.LILY_PAD)) && phase >= 2) {
            setEpochBlock(level, pos, Blocks.AIR.defaultBlockState(), editBudget);
            return;
        }
        if ((state.is(Blocks.VINE)
                || state.is(Blocks.CAVE_VINES)
                || state.is(Blocks.CAVE_VINES_PLANT)
                || state.is(Blocks.HANGING_ROOTS)) && phase >= 3) {
            setEpochBlock(level, pos, Blocks.AIR.defaultBlockState(), editBudget);
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
                        : ModBlocks.DEAD_LEAVES.get().defaultBlockState(), editBudget);
            }
            return;
        }
        if (state.is(ModBlocks.DEAD_LEAVES.get()) && phase >= 3) {
            setEpochBlock(level, pos, phase >= 5 || random.nextFloat() < 0.60f
                    ? Blocks.AIR.defaultBlockState()
                    : state, editBudget);
            return;
        }
        if (state.is(BlockTags.LOGS) && phase >= 3) {
            if (phase >= 5) {
                setEpochBlock(level, pos, Blocks.AIR.defaultBlockState(), editBudget);
                return;
            }
            Direction.Axis axis = state.hasProperty(RotatedPillarBlock.AXIS)
                    ? state.getValue(RotatedPillarBlock.AXIS)
                    : Direction.Axis.Y;
            BlockState replacement = ModBlocks.DEAD_LOG.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
            setEpochBlock(level, pos, replacement, editBudget);
            return;
        }
        if ((state.is(ModBlocks.DEAD_LOG.get()) || state.is(ModBlocks.FROZEN_LOG.get())) && phase >= 5) {
            setEpochBlock(level, pos, Blocks.AIR.defaultBlockState(), editBudget);
        }
    }

    private static void applyVolumeCatchUp(ServerLevel level, ApocalypseState apocalypse, BlockPos pos,
                                           TickEditBudget editBudget, MutationProtectionContext protectionContext) {
        int phase = apocalypse.getPhase();
        float progress = apocalypse.getProgress();
        BlockState state = level.getBlockState(pos);

        if (!isVolumeCandidate(state, phase) || !canMutate(level, pos, protectionContext)) {
            return;
        }
        if (isFreezeImmune(level, pos, state)) {
            return;
        }
        if (state.is(Blocks.WATER) && phase >= 2) {
            setEpochBlock(level, pos, Blocks.ICE.defaultBlockState(), editBudget);
            return;
        }
        if (state.is(Blocks.ICE) && phase >= 3) {
            setEpochBlock(level, pos, Blocks.PACKED_ICE.defaultBlockState(), editBudget);
            return;
        }
        if (state.is(Blocks.PACKED_ICE) && phase >= 4) {
            setEpochBlock(level, pos, Blocks.BLUE_ICE.defaultBlockState(), editBudget);
            return;
        }
        if (FrozenDawnConfig.ENABLE_LAVA_FREEZING.get()) {
            if (state.is(Blocks.LAVA) && phase >= 3) {
                setEpochBlock(level, pos, Blocks.MAGMA_BLOCK.defaultBlockState(), editBudget);
                return;
            }
            if (state.is(Blocks.MAGMA_BLOCK) && phase >= 4) {
                setEpochBlock(level, pos, Blocks.OBSIDIAN.defaultBlockState(), editBudget);
                return;
            }
            if (state.is(Blocks.OBSIDIAN) && phase >= 4) {
                setEpochBlock(level, pos, ModBlocks.FROZEN_OBSIDIAN.get().defaultBlockState(), editBudget);
                return;
            }
        }
        if (FrozenDawnConfig.ENABLE_FUEL_SCARCITY.get()
                && phase >= FrozenDawnConfig.FUEL_SCARCITY_PHASE.get()
                && pos.getY() >= 0
                && (state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE))) {
            setEpochBlock(level, pos, ModBlocks.FROZEN_COAL_ORE.get().defaultBlockState(), editBudget);
        }
    }

    private static void applySnowCatchUp(ServerLevel level, ApocalypseState apocalypse,
                                         BlockPos supportPos, int chunkX, int chunkZ, TickEditBudget editBudget,
                                         MutationProtectionContext protectionContext) {
        int phase = apocalypse.getPhase();
        float progress = apocalypse.getProgress();
        if (phase < 2 || PhaseManager.isVacuumActive(phase, progress)) {
            return;
        }
        CatchUpTrace trace = activeTrace;
        BlockPos snowPos = supportPos.above();

        long protectedStart = trace != null ? System.nanoTime() : 0L;
        boolean protectedArea = BlastPitWarmZoneRegistry.isInsideWarmZone(level, snowPos)
                || ThermalVentRegistry.isVolcanicField(level, snowPos);
        if (trace != null) {
            trace.recordSurface(SurfacePart.SNOW_PROTECTION_CHECK, System.nanoTime() - protectedStart);
        }
        if (protectedArea) {
            return;
        }

        long mutateStart = trace != null ? System.nanoTime() : 0L;
        boolean mutable = canMutate(level, snowPos, protectionContext);
        if (trace != null) {
            trace.recordSurface(SurfacePart.SNOW_MUTATE_CHECK, System.nanoTime() - mutateStart);
        }
        if (!mutable) {
            return;
        }

        long openStart = trace != null ? System.nanoTime() : 0L;
        boolean openToSnow = isOpenToSnow(level, snowPos);
        if (trace != null) {
            trace.recordSurface(SurfacePart.SNOW_OPEN_CHECK, System.nanoTime() - openStart);
        }
        if (!openToSnow) {
            return;
        }

        long supportStart = trace != null ? System.nanoTime() : 0L;
        boolean canPlaceSnow = canPlaceSnowOn(level, supportPos);
        if (trace != null) {
            trace.recordSurface(SurfacePart.SNOW_SUPPORT_CHECK, System.nanoTime() - supportStart);
        }
        if (!canPlaceSnow) {
            return;
        }

        long targetStart = trace != null ? System.nanoTime() : 0L;
        RandomSource random = randomFor(level, chunkX, chunkZ, snowPos, 0x5A10C0DE);
        int targetUnits = targetSnowUnits(phase, progress, random);
        if (trace != null) {
            trace.recordSurface(SurfacePart.SNOW_TARGET, System.nanoTime() - targetStart);
        }
        if (targetUnits <= 0) {
            return;
        }

        long placeStart = trace != null ? System.nanoTime() : 0L;
        BlockPos.MutableBlockPos cursor = snowPos.mutable();
        int remaining = targetUnits;
        int maxDepth = phase >= 5 ? MAX_CATCH_UP_SNOW_DEPTH : 1;
        try {
            for (int depth = 0; depth < maxDepth && remaining > 0; depth++) {
                BlockState at = level.getBlockState(cursor);
                if (!at.isAir() && !at.is(Blocks.SNOW) && !at.is(Blocks.SNOW_BLOCK)) {
                    return;
                }
                if (!canMutate(level, cursor, protectionContext)) {
                    return;
                }

                if (remaining >= 8 && phase >= 5) {
                    setEpochBlock(level, cursor.immutable(), Blocks.SNOW_BLOCK.defaultBlockState(), editBudget);
                    remaining -= 8;
                } else {
                    int layers = Math.max(1, Math.min(7, remaining));
                    setEpochBlock(level, cursor.immutable(),
                            Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers), editBudget);
                    remaining = 0;
                }
                cursor.move(Direction.UP);
            }
        } finally {
            if (trace != null) {
                trace.recordSurface(SurfacePart.SNOW_PLACE_LOOP, System.nanoTime() - placeStart);
            }
        }
    }

    private static void applyAtmosphereCatchUp(ServerLevel level, ApocalypseState apocalypse,
                                               BlockPos supportPos, int chunkX, int chunkZ, TickEditBudget editBudget,
                                               MutationProtectionContext protectionContext) {
        BlockPos placePos = supportPos.above();
        if (!canMutate(level, placePos, protectionContext)
                || BlastPitWarmZoneRegistry.isInsideWarmZone(level, placePos)
                || ThermalVentRegistry.isVolcanicField(level, placePos)
                || protectionContext.isFuelSiloProtected(placePos)
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
        setEpochBlock(level, placePos, ModBlocks.FROZEN_ATMOSPHERE.get().defaultBlockState(), editBudget);
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
        // Chunk catch-up can run over many fresh columns at once. Avoid canSeeSky
        // here because it routes through sky-light queries and can stall on load.
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, snowPos.getX(), snowPos.getZ());
        return snowPos.getY() >= surfaceY - 1;
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

    private static void replacePlantWithDeadBush(ServerLevel level, BlockPos pos, BlockState state,
                                                 TickEditBudget editBudget,
                                                 MutationProtectionContext protectionContext) {
        if (state.getBlock() instanceof DoublePlantBlock) {
            boolean upper = state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER;
            BlockPos upperPos = upper ? pos : pos.above();
            BlockPos lowerPos = upper ? pos.below() : pos;
            if (canMutate(level, upperPos, protectionContext)) {
                setEpochBlock(level, upperPos, Blocks.AIR.defaultBlockState(), editBudget);
            }
            if (canMutate(level, lowerPos, protectionContext)) {
                setEpochBlock(level, lowerPos, Blocks.DEAD_BUSH.defaultBlockState(), editBudget);
            }
        } else {
            setEpochBlock(level, pos, Blocks.DEAD_BUSH.defaultBlockState(), editBudget);
        }
    }

    private static void setEpochBlock(ServerLevel level, BlockPos pos, BlockState state, TickEditBudget editBudget) {
        if (level.getBlockState(pos).equals(state)) {
            return;
        }
        editBudget.reserve();
        CatchUpTrace trace = activeTrace;
        long setBlockStart = trace != null ? System.nanoTime() : 0L;
        boolean changed = level.setBlock(pos, state, EPOCH_SET_BLOCK_FLAGS);
        if (trace != null) {
            trace.recordSetBlock(state, System.nanoTime() - setBlockStart, changed);
        }
        if (changed) {
            editBudget.recordEdit();
        }
    }

    private static boolean canMutate(ServerLevel level, BlockPos pos, MutationProtectionContext protectionContext) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        return !protectionContext.isPlayerPlaced(pos)
                && !protectionContext.isFuelSiloProtected(pos)
                && !BlastPitWarmZoneRegistry.isInsideWarmZone(level, pos)
                && !ThermalVentRegistry.isVolcanicField(level, pos);
    }

    private static final class MutationProtectionContext {
        private final ServerLevel level;
        private final Map<Long, Boolean> nearbyPlayerPlacedCells = new HashMap<>();
        private final Map<Long, Boolean> fuelSiloProtectedPositions = new HashMap<>();
        private PlayerPlacedBlockTracker tracker;

        private MutationProtectionContext(ServerLevel level) {
            this.level = level;
        }

        private boolean isPlayerPlaced(BlockPos pos) {
            return tracker().isPlayerPlaced(pos);
        }

        private boolean isFuelSiloProtected(BlockPos pos) {
            if (!mightHaveNearbyPlayerPlacedBlock(pos)) {
                return false;
            }
            BlockPos immutable = pos.immutable();
            return fuelSiloProtectedPositions.computeIfAbsent(immutable.asLong(),
                    ignored -> FuelProcessingSiloMultiblock.isProtectedFromEnvironmentalDeposit(level, immutable));
        }

        private boolean mightHaveNearbyPlayerPlacedBlock(BlockPos pos) {
            int cellX = Math.floorDiv(pos.getX(), PROTECTION_CELL_SIZE);
            int cellY = Math.floorDiv(pos.getY(), PROTECTION_CELL_SIZE);
            int cellZ = Math.floorDiv(pos.getZ(), PROTECTION_CELL_SIZE);
            long key = packProtectionCell(cellX, cellY, cellZ);
            return nearbyPlayerPlacedCells.computeIfAbsent(key,
                    ignored -> scanExpandedProtectionCellForPlayerPlaced(cellX, cellY, cellZ));
        }

        private boolean scanExpandedProtectionCellForPlayerPlaced(int cellX, int cellY, int cellZ) {
            int minX = cellX * PROTECTION_CELL_SIZE - SILO_PROTECTION_SCAN_XZ_RADIUS;
            int minY = cellY * PROTECTION_CELL_SIZE - SILO_PROTECTION_SCAN_Y_RADIUS;
            int minZ = cellZ * PROTECTION_CELL_SIZE - SILO_PROTECTION_SCAN_XZ_RADIUS;
            int maxX = minX + PROTECTION_CELL_SIZE + SILO_PROTECTION_SCAN_XZ_RADIUS * 2 - 1;
            int maxY = minY + PROTECTION_CELL_SIZE + SILO_PROTECTION_SCAN_Y_RADIUS * 2 - 1;
            int maxZ = minZ + PROTECTION_CELL_SIZE + SILO_PROTECTION_SCAN_XZ_RADIUS * 2 - 1;

            PlayerPlacedBlockTracker blockTracker = tracker();
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        cursor.set(x, y, z);
                        if (blockTracker.isPlayerPlaced(cursor.asLong())) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private PlayerPlacedBlockTracker tracker() {
            if (tracker == null) {
                tracker = PlayerPlacedBlockTracker.get(level.getServer());
            }
            return tracker;
        }

        private static long packProtectionCell(int x, int y, int z) {
            return ((long) x & 0x3FFFFFFL) << 38
                    | ((long) z & 0x3FFFFFFL) << 12
                    | ((long) y & 0xFFFL);
        }
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
                + " lastMs=" + millis(lastTickNanos)
                + " lastBlocks=" + lastTickBlocks
                + " lastEdits=" + lastTickEdits
                + " editCap=" + lastEditBudget
                + " chunks=" + lastChunksTouched
                + " profMs=m" + millis(lastModeNanos)
                + "/s" + millis(lastSelectNanos)
                + "/b" + millis(lastBookkeepingNanos)
                + "/p" + millis(lastProcessNanos)
                + " maxMs=" + millis(maxTickNanos)
                + " maxProfMs=m" + millis(maxModeNanos)
                + "/s" + millis(maxSelectNanos)
                + "/b" + millis(maxBookkeepingNanos)
                + "/p" + millis(maxProcessNanos);
    }

    private static String millis(long nanos) {
        return String.format("%.3f", nanos / 1_000_000.0D);
    }

    public static void reset() {
        pendingChunks.clear();
        freshChunks.clear();
        initialPrewarmDone = false;
        processedChunks = 0;
        completedChunks = 0;
        lastTickNanos = 0;
        maxTickNanos = 0;
        lastModeNanos = 0;
        maxModeNanos = 0;
        lastSelectNanos = 0;
        maxSelectNanos = 0;
        lastBookkeepingNanos = 0;
        maxBookkeepingNanos = 0;
        lastProcessNanos = 0;
        maxProcessNanos = 0;
        lastTickBlocks = 0;
        lastTickEdits = 0;
        lastEditBudget = NORMAL_EDIT_BUDGET;
        lastChunksTouched = 0;
        lastMode = CatchUpMode.NORMAL;
    }

    private enum Pass {
        // Tree removal must happen before snow reconciliation and surface fill.
        TREE_CLEAR,
        DETACHED_SNOW,
        SURFACE,
        SURFACE_COLUMN,
        VOLUME_SAMPLE,
        COAL_SAMPLE,
        ATMOSPHERE;

        private int maxCursor(ServerLevel level) {
            return switch (this) {
                case TREE_CLEAR, DETACHED_SNOW, SURFACE, ATMOSPHERE -> 16 * 16;
                case SURFACE_COLUMN -> 16 * 16 * SURFACE_COLUMN_DEPTH;
                case VOLUME_SAMPLE, COAL_SAMPLE -> VOLUME_SAMPLES_PER_CHUNK;
            };
        }
    }

    private enum SurfacePart {
        GROUND_SCAN("ground"),
        SURFACE_BLOCK("surface"),
        SURFACE_MUTATE_CHECK("surfaceMutate"),
        SURFACE_SKY_CHECK("surfaceSky"),
        SNOW("snow"),
        SNOW_PROTECTION_CHECK("snowProtect"),
        SNOW_MUTATE_CHECK("snowMutate"),
        SNOW_OPEN_CHECK("snowOpen"),
        SNOW_SUPPORT_CHECK("snowSupport"),
        SNOW_TARGET("snowTarget"),
        SNOW_PLACE_LOOP("snowLoop");

        private final String label;

        SurfacePart(String label) {
            this.label = label;
        }
    }

    private enum CatchUpMode {
        NORMAL("normal", NORMAL_BUDGET_NANOS, NORMAL_BLOCK_BUDGET, NORMAL_EDIT_BUDGET),
        BURST("burst", BURST_BUDGET_NANOS, BURST_BLOCK_BUDGET, BURST_EDIT_BUDGET),
        PREWARM("prewarm", PREWARM_BUDGET_NANOS, PREWARM_BLOCK_BUDGET, PREWARM_EDIT_BUDGET);

        private final String label;
        private final long budgetNanos;
        private final int blockBudget;
        private final int editBudget;

        CatchUpMode(String label, long budgetNanos, int blockBudget, int editBudget) {
            this.label = label;
            this.budgetNanos = budgetNanos;
            this.blockBudget = blockBudget;
            this.editBudget = editBudget;
        }
    }

    private static final class CatchUpTrace {
        private final EnumMap<Pass, Long> passNanos = new EnumMap<>(Pass.class);
        private final EnumMap<Pass, Integer> passCalls = new EnumMap<>(Pass.class);
        private final EnumMap<SurfacePart, Long> surfaceNanos = new EnumMap<>(SurfacePart.class);
        private final EnumMap<SurfacePart, Integer> surfaceCalls = new EnumMap<>(SurfacePart.class);
        private final LinkedHashMap<String, Integer> replacements = new LinkedHashMap<>();
        private long setBlockNanos;
        private int setBlockCalls;
        private int setBlockSuccesses;
        private long slowestApplyNanos;
        private int slowestChunkX;
        private int slowestChunkZ;
        private int slowestCursor;
        private Pass slowestPass;

        private void recordApply(int chunkX, int chunkZ, Pass pass, int cursor, long nanos) {
            passNanos.merge(pass, nanos, Long::sum);
            passCalls.merge(pass, 1, Integer::sum);
            if (nanos > slowestApplyNanos) {
                slowestApplyNanos = nanos;
                slowestChunkX = chunkX;
                slowestChunkZ = chunkZ;
                slowestCursor = cursor;
                slowestPass = pass;
            }
        }

        private void recordSetBlock(BlockState state, long nanos, boolean changed) {
            setBlockNanos += nanos;
            setBlockCalls++;
            if (changed) {
                setBlockSuccesses++;
                String key = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                replacements.merge(key, 1, Integer::sum);
            }
        }

        private void recordSurface(SurfacePart part, long nanos) {
            surfaceNanos.merge(part, nanos, Long::sum);
            surfaceCalls.merge(part, 1, Integer::sum);
        }

        private String slowestSummary() {
            if (slowestPass == null) {
                return "none";
            }
            return slowestPass
                    + "@chunk=" + slowestChunkX + "," + slowestChunkZ
                    + " cursor=" + slowestCursor
                    + " applyMs=" + millis(slowestApplyNanos);
        }

        private String passSummary() {
            StringBuilder builder = new StringBuilder();
            for (Pass pass : Pass.values()) {
                long nanos = passNanos.getOrDefault(pass, 0L);
                int calls = passCalls.getOrDefault(pass, 0);
                if (calls == 0) {
                    continue;
                }
                if (!builder.isEmpty()) {
                    builder.append(',');
                }
                builder.append(pass.name())
                        .append('=')
                        .append(millis(nanos))
                        .append("ms/")
                        .append(calls);
            }
            return builder.isEmpty() ? "none" : builder.toString();
        }

        private String surfaceSummary() {
            StringBuilder builder = new StringBuilder();
            for (SurfacePart part : SurfacePart.values()) {
                long nanos = surfaceNanos.getOrDefault(part, 0L);
                int calls = surfaceCalls.getOrDefault(part, 0);
                if (calls == 0) {
                    continue;
                }
                if (!builder.isEmpty()) {
                    builder.append(',');
                }
                builder.append(part.label)
                        .append('=')
                        .append(millis(nanos))
                        .append("ms/")
                        .append(calls);
            }
            return builder.isEmpty() ? "none" : builder.toString();
        }

        private String replacementSummary() {
            if (replacements.isEmpty()) {
                return "none";
            }
            StringBuilder builder = new StringBuilder();
            replacements.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(8)
                    .forEach(entry -> {
                        if (!builder.isEmpty()) {
                            builder.append(',');
                        }
                        builder.append(entry.getKey()).append('=').append(entry.getValue());
                    });
            return builder.toString();
        }
    }

    private static final class TickEditBudget {
        private final int limit;
        private int edits;

        private TickEditBudget(int limit) {
            this.limit = limit;
        }

        private void reserve() {
            if (edits >= limit) {
                throw EditBudgetExceeded.INSTANCE;
            }
        }

        private void requireCapacity(int additionalEdits) {
            if (additionalEdits < 0 || edits + additionalEdits > limit) {
                throw EditBudgetExceeded.INSTANCE;
            }
        }

        private void recordEdit() {
            edits++;
        }

        private int edits() {
            return edits;
        }
    }

    private static final class EditBudgetExceeded extends RuntimeException {
        private static final EditBudgetExceeded INSTANCE = new EditBudgetExceeded();

        private EditBudgetExceeded() {
            super(null, null, false, false);
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

package com.frozendawn.homo;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.FuelProcessingSiloMultiblock;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.ChunkEpochState;
import com.frozendawn.data.MonitoringStationState;
import com.frozendawn.data.OrsaStructureState;
import com.frozendawn.data.PlayerPlacedBlockTracker;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.world.BlastPitWarmZoneRegistry;
import com.frozendawn.world.ChunkCatchUpManager;
import com.frozendawn.world.ThermalVentRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.frozendawn.init.ModItems;
import com.frozendawn.item.OrsaIdBadgeItem;

/**
 * Reconciles mathematically matured Hearth records into bounded physical scenes.
 * It never loads chunks and applies only a small number of idempotent edits per tick.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class HearthReconciliationManager {
    private static final long TICK_BUDGET_NANOS = 2_000_000L;
    private static final int EDIT_BUDGET = 24;
    private static final int SURFACE_CANDIDATES_PER_TICK = 6;
    private static final long SURFACE_RETRY_DELAY_TICKS = 200L;
    private static final int STRUCTURE_CLEARANCE_RADIUS = 96;
    private static final int SURFACE_SCAN_DEPTH = 48;
    private static final int ENTITY_BLOCK_RETRY_LIMIT = 60;
    private static final int SET_BLOCK_RETRY_LIMIT = 8;
    private static final int SKIP_LOG_LIMIT = 12;
    private static final int SET_BLOCK_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Integer> surfaceSearchCursors = new HashMap<>();
    private static final Map<UUID, SurfaceCandidate> surfaceFallbacks = new HashMap<>();
    private static final Map<UUID, Long> retryAfter = new HashMap<>();
    private static final Map<UUID, String> waitReasons = new HashMap<>();
    private static final Map<PlacementFailureKey, Integer> placementFailures = new HashMap<>();
    private static final Map<UUID, Integer> skippedLogCounts = new HashMap<>();

    private static long completedScenes;
    private static long skippedPlacements;
    private static long lastTickNanos;
    private static int lastTickEdits;
    private static int lastTickPieces;
    private static int lastTickSkipped;

    private HearthReconciliationManager() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide()
                || !(event.getLevel() instanceof ServerLevel level)
                || level.dimension() != ServerLevel.OVERWORLD) {
            return;
        }
        queueNearChunk(level, event.getChunk().getPos().x, event.getChunk().getPos().z);
    }

    public static void tick(ServerLevel level, ApocalypseState apocalypse) {
        if (level.dimension() != ServerLevel.OVERWORLD) {
            return;
        }

        long gameTime = level.getGameTime();
        if (gameTime % 20L == 0L) {
            queueEligibleLoadedHearths(level);
        }
        if (pending.isEmpty()) {
            lastTickNanos = 0L;
            lastTickEdits = 0;
            lastTickPieces = 0;
            lastTickSkipped = 0;
            return;
        }

        long start = System.nanoTime();
        int edits = 0;
        int pieces = 0;
        int skipped = 0;
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());

        for (UUID id : List.copyOf(pending)) {
            if (edits >= EDIT_BUDGET || System.nanoTime() - start >= TICK_BUDGET_NANOS) {
                break;
            }
            ReturnedHearthSavedData.HearthRecord hearth = data.hearth(id).orElse(null);
            if (hearth == null || !HearthReconciliationPolicy.needsReconciliation(hearth)) {
                removePending(id);
                continue;
            }
            if (retryAfter.getOrDefault(id, 0L) > gameTime) {
                waitReasons.putIfAbsent(id, "retry");
                continue;
            }
            waitReasons.remove(id);

            HearthReconciliationPolicy.StructurePlan plan =
                    HearthReconciliationPolicy.desiredPlan(hearth);
            if (plan == null) {
                removePending(id);
                continue;
            }
            List<HearthStructurePlacement> layout = layoutFor(hearth, plan.stage());

            if (!hearth.surfaceResolved()) {
                SurfaceResolution resolution = resolveSurface(
                        level, hearth, layout, plan.footprintRadius(),
                        plan.maxSurfaceVariance(), start);
                if (resolution.center() == null) {
                    if (resolution.exhausted()) {
                        retryAfter.put(id, gameTime + SURFACE_RETRY_DELAY_TICKS);
                        surfaceSearchCursors.remove(id);
                        waitReasons.put(id, "surface-retry");
                    } else {
                        waitReasons.put(id, "surface-search");
                    }
                    continue;
                }
                data.resolveSurface(id, resolution.center());
                surfaceSearchCursors.remove(id);
                surfaceFallbacks.remove(id);
                retryAfter.remove(id);
                hearth = data.hearth(id).orElseThrow();
                FrozenDawn.LOGGER.info("Resolved {} Hearth {} surface at ({}, {}, {}){}",
                        hearth.type().name().toLowerCase(), shortId(id),
                        hearth.center().getX(), hearth.center().getY(), hearth.center().getZ(),
                        resolution.fallback() ? " using rugged-terrain fallback" : "");
            }

            if (!footprintLoaded(level, hearth.center(), plan.footprintRadius())) {
                waitReasons.put(id, "footprint-unloaded");
                continue;
            }
            if (!footprintCaughtUp(level, apocalypse, hearth.center(),
                    plan.footprintRadius())) {
                waitReasons.put(id, "chunk-catch-up");
                continue;
            }

            int cursor = Math.min(HearthReconciliationPolicy.resumeCursor(hearth), layout.size());
            while (cursor < layout.size()
                    && edits < EDIT_BUDGET
                    && System.nanoTime() - start < TICK_BUDGET_NANOS) {
                HearthStructurePlacement placement = layout.get(cursor);
                BlockPos target = hearth.center().offset(placement.offset());
                if (!level.isLoaded(target)) {
                    waitReasons.put(id, "target-unloaded@" + formatPos(target));
                    break;
                }

                BlockState desired = stateFor(placement);
                BlockState existing = level.getBlockState(target);
                if (placement.piece() == HearthStructurePiece.CLEAR_TRANSIENT
                        && !isTransientSurface(existing)) {
                    clearPlacementFailure(id, plan.version(), cursor);
                    cursor++;
                    pieces++;
                    continue;
                }
                if (placement.piece() == HearthStructurePiece.FOUNDATION_SUPPORT
                        && hasSolidFoundation(level, target, existing)) {
                    clearPlacementFailure(id, plan.version(), cursor);
                    cursor++;
                    pieces++;
                    continue;
                }
                if (needsPlacement(existing, desired)) {
                    if (!desired.getCollisionShape(level, target).isEmpty()
                            && !level.getEntitiesOfClass(
                                    LivingEntity.class, new AABB(target)).isEmpty()) {
                        int attempts = recordPlacementFailure(
                                id, plan.version(), cursor, PlacementFailureKind.ENTITY);
                        if (!shouldSkipAfterFailure(PlacementFailureKind.ENTITY, attempts)) {
                            waitReasons.put(id, "entity@" + formatPos(target)
                                    + "#" + attempts);
                            break;
                        }
                        skipped += skipBlockedPlacement(
                                id, plan, cursor, placement, target, existing,
                                PlacementFailureKind.ENTITY, attempts);
                        cursor++;
                        pieces++;
                        continue;
                    }
                    if (!canReplace(level, target, existing, placement)) {
                        if (isClearancePiece(placement.piece())
                                || isOptionalTerrainDecoration(placement.piece())
                                && isNaturalSettlementObstruction(existing)) {
                            clearPlacementFailure(id, plan.version(), cursor);
                            cursor++;
                            pieces++;
                            continue;
                        }
                        skipped += skipBlockedPlacement(
                                id, plan, cursor, placement, target, existing,
                                PlacementFailureKind.PERMANENT_BLOCKER, 1);
                        cursor++;
                        pieces++;
                        continue;
                    }
                    if (!level.setBlock(target, desired, SET_BLOCK_FLAGS)) {
                        int attempts = recordPlacementFailure(
                                id, plan.version(), cursor, PlacementFailureKind.SET_BLOCK);
                        if (!shouldSkipAfterFailure(PlacementFailureKind.SET_BLOCK, attempts)) {
                            waitReasons.put(id, "set-failed@" + formatPos(target)
                                    + "#" + attempts);
                            break;
                        }
                        skipped += skipBlockedPlacement(
                                id, plan, cursor, placement, target, existing,
                                PlacementFailureKind.SET_BLOCK, attempts);
                        cursor++;
                        pieces++;
                        continue;
                    }
                    edits++;
                }
                clearPlacementFailure(id, plan.version(), cursor);
                if (placement.piece() == HearthStructurePiece.ORSA_CRATE) {
                    initializeOrsaCrateLoot(
                            level, target, hearth.layoutSeed(), plan.stage());
                } else if (placement.piece() == HearthStructurePiece.PROTECTED_CHEST) {
                    initializeFormedLoot(level, target, hearth.layoutSeed());
                } else if (placement.piece() == HearthStructurePiece.SACRED_CHEST) {
                    initializeIntactLoot(level, target, hearth.layoutSeed());
                }
                cursor++;
                pieces++;
            }

            boolean complete = cursor >= layout.size();
            data.recordStructureProgress(id, plan.version(), cursor,
                    complete ? plan.stage() : hearth.structureStageApplied(), complete);
            if (complete) {
                completedScenes++;
                removePending(id);
                FrozenDawn.LOGGER.info("Completed {} Hearth {} with {} planned pieces",
                        plan.stage().name().toLowerCase(), shortId(id), layout.size());
            } else if (!waitReasons.containsKey(id)) {
                waitReasons.put(id, "budget@cursor=" + cursor);
            }
        }

        lastTickNanos = System.nanoTime() - start;
        lastTickEdits = edits;
        lastTickPieces = pieces;
        lastTickSkipped = skipped;
        skippedPlacements += skipped;
    }

    public static int queueAll(ServerLevel level) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        int before = pending.size();
        for (ReturnedHearthSavedData.HearthRecord hearth : data.hearths()) {
            if (HearthReconciliationPolicy.needsReconciliation(hearth)) {
                pending.add(hearth.id());
                retryAfter.remove(hearth.id());
            }
        }
        return Math.max(0, pending.size() - before);
    }

    public static String statusLine() {
        return "queued=" + pending.size()
                + " completed=" + completedScenes
                + " lastMs=" + String.format("%.3f", lastTickNanos / 1_000_000.0D)
                + " lastEdits=" + lastTickEdits
                + " lastPieces=" + lastTickPieces
                + " lastSkipped=" + lastTickSkipped
                + " skipped=" + skippedPlacements
                + " waiting=" + waitingSummary()
                + " tracePlan=" + HearthReconciliationPolicy.TRACE_PLAN_VERSION
                + " formedPlan=" + HearthReconciliationPolicy.FORMED_PLAN_VERSION
                + " intactPlan=" + HearthReconciliationPolicy.INTACT_PLAN_VERSION;
    }

    public static void reset() {
        pending.clear();
        surfaceSearchCursors.clear();
        surfaceFallbacks.clear();
        retryAfter.clear();
        waitReasons.clear();
        placementFailures.clear();
        skippedLogCounts.clear();
        completedScenes = 0L;
        skippedPlacements = 0L;
        lastTickNanos = 0L;
        lastTickEdits = 0;
        lastTickPieces = 0;
        lastTickSkipped = 0;
    }

    private static void queueNearChunk(ServerLevel level, int chunkX, int chunkZ) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        int range = (HearthReconciliationPolicy.CANDIDATE_SEARCH_RADIUS
                + HearthReconciliationPolicy.INTACT_FOOTPRINT_RADIUS + 15) >> 4;
        for (ReturnedHearthSavedData.HearthRecord hearth : data.hearths()) {
            if (!HearthReconciliationPolicy.needsReconciliation(hearth)) {
                continue;
            }
            int hearthChunkX = hearth.center().getX() >> 4;
            int hearthChunkZ = hearth.center().getZ() >> 4;
            if (Math.abs(hearthChunkX - chunkX) <= range
                    && Math.abs(hearthChunkZ - chunkZ) <= range) {
                pending.add(hearth.id());
            }
        }
    }

    private static void queueEligibleLoadedHearths(ServerLevel level) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        for (ReturnedHearthSavedData.HearthRecord hearth : data.hearths()) {
            if (HearthReconciliationPolicy.needsReconciliation(hearth)
                    && centerAreaLoaded(level, hearth.center())) {
                pending.add(hearth.id());
            }
        }
    }

    private static SurfaceResolution resolveSurface(
            ServerLevel level, ReturnedHearthSavedData.HearthRecord hearth,
            List<HearthStructurePlacement> layout, int footprintRadius,
            int maxSurfaceVariance,
            long tickStartNanos) {
        List<BlockPos> offsets = HearthReconciliationPolicy.candidateOffsets(hearth.layoutSeed());
        int cursor = surfaceSearchCursors.getOrDefault(hearth.id(), 0);
        int tested = 0;
        while (cursor < offsets.size()
                && tested < SURFACE_CANDIDATES_PER_TICK
                && System.nanoTime() - tickStartNanos < TICK_BUDGET_NANOS) {
            BlockPos offset = offsets.get(cursor++);
            tested++;
            BlockPos horizontal = hearth.center().offset(offset.getX(), 0, offset.getZ());
            SurfaceCandidate candidate = evaluateCandidate(
                    level, hearth, horizontal, layout, footprintRadius,
                    maxSurfaceVariance);
            if (candidate == null) {
                continue;
            }
            if (candidate.strict()) {
                surfaceSearchCursors.put(hearth.id(), cursor);
                return new SurfaceResolution(candidate.center(), false, false);
            }
            surfaceFallbacks.merge(hearth.id(), candidate,
                    (first, second) -> first.variance() <= second.variance() ? first : second);
        }
        surfaceSearchCursors.put(hearth.id(), cursor);
        boolean exhausted = cursor >= offsets.size();
        SurfaceCandidate fallback = exhausted ? surfaceFallbacks.get(hearth.id()) : null;
        if (fallback != null) {
            return new SurfaceResolution(fallback.center(), true, true);
        }
        return new SurfaceResolution(null, exhausted, false);
    }

    private static SurfaceCandidate evaluateCandidate(
            ServerLevel level, ReturnedHearthSavedData.HearthRecord hearth,
            BlockPos horizontal, List<HearthStructurePlacement> layout,
            int footprintRadius, int maxSurfaceVariance) {
        if (!footprintLoaded(level, horizontal, footprintRadius)) {
            return null;
        }

        int minHeight = Integer.MAX_VALUE;
        int maxHeight = Integer.MIN_VALUE;
        for (HearthStructurePlacement placement : layout) {
            if (placement.offset().getY() != -1) {
                continue;
            }
            int x = horizontal.getX() + placement.offset().getX();
            int z = horizontal.getZ() + placement.offset().getZ();
            int height = stableSurfaceHeight(level, x, z);
            if (height == Integer.MIN_VALUE) {
                return null;
            }
            minHeight = Math.min(minHeight, height);
            maxHeight = Math.max(maxHeight, height);
        }
        if (minHeight == Integer.MAX_VALUE
                || maxHeight < level.getMinBuildHeight() + 4
                || maxHeight > level.getMaxBuildHeight() - 4) {
            return null;
        }

        int variance = maxHeight - minHeight;
        int centerY = fallbackSurfaceY(minHeight, maxHeight, maxSurfaceVariance);
        BlockPos center = new BlockPos(horizontal.getX(), centerY, horizontal.getZ());
        if (protectedSite(level, center, layout, footprintRadius)
                || !layoutReplaceable(level, center, layout)) {
            return null;
        }
        return new SurfaceCandidate(center, variance, variance <= maxSurfaceVariance);
    }

    static int fallbackSurfaceY(int minHeight, int maxHeight, int maxSurfaceVariance) {
        return Math.min(maxHeight, minHeight + Math.max(0, maxSurfaceVariance));
    }

    private static boolean protectedSite(ServerLevel level, BlockPos center,
                                         List<HearthStructurePlacement> layout,
                                         int footprintRadius) {
        OrsaStructureState orsa = OrsaStructureState.get(level.getServer());
        if (orsa.findTowerNear(center, STRUCTURE_CLEARANCE_RADIUS) != null
                || flatDistanceWithin(orsa.getBlastPitPos(), center, STRUCTURE_CLEARANCE_RADIUS)
                || flatDistanceWithin(orsa.getBlastPitTargetPos(), center, STRUCTURE_CLEARANCE_RADIUS)) {
            return true;
        }

        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (orsa.isCampBuilt(centerChunkX + dx, centerChunkZ + dz)) {
                    return true;
                }
            }
        }

        MonitoringStationState stations = MonitoringStationState.get(level.getServer());
        for (BlockPos station : stations.getBuiltStationCenters()) {
            if (flatDistanceWithin(station, center, STRUCTURE_CLEARANCE_RADIUS)) {
                return true;
            }
        }

        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        if (data.transponderAnchor().filter(anchor ->
                flatDistanceWithin(anchor, center, STRUCTURE_CLEARANCE_RADIUS)).isPresent()) {
            return true;
        }

        PlayerPlacedBlockTracker tracker = PlayerPlacedBlockTracker.get(level.getServer());
        for (int x = center.getX() - footprintRadius;
             x <= center.getX() + footprintRadius; x++) {
            for (int z = center.getZ() - footprintRadius;
                 z <= center.getZ() + footprintRadius; z++) {
                for (int y = center.getY() - 5; y <= center.getY() + 3; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (tracker.isPlayerPlaced(pos)) {
                        return true;
                    }
                }
            }
        }

        for (HearthStructurePlacement placement : layout) {
            BlockPos pos = center.offset(placement.offset());
            if (BlastPitWarmZoneRegistry.isInsideWarmZone(level, pos)
                    || ThermalVentRegistry.isVolcanicField(level, pos)
                    || level.getBlockEntity(pos) != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean layoutReplaceable(ServerLevel level, BlockPos center,
                                             List<HearthStructurePlacement> layout) {
        for (HearthStructurePlacement placement : layout) {
            BlockPos pos = center.offset(placement.offset());
            BlockState state = level.getBlockState(pos);
            if (!allowsFluidReplacement(state, placement)) {
                return false;
            }
            if (placement.piece() == HearthStructurePiece.CLEAR_TRANSIENT
                    && !isTransientSurface(state)) {
                continue;
            }
            if (!canReplaceNatural(state, placement)) {
                return false;
            }
        }
        return true;
    }

    private static boolean canReplace(ServerLevel level, BlockPos pos, BlockState state,
                                      HearthStructurePlacement placement) {
        if (!allowsFluidReplacement(state, placement)
                || PlayerPlacedBlockTracker.get(level.getServer()).isPlayerPlaced(pos)
                || FuelProcessingSiloMultiblock.isProtectedFromEnvironmentalDeposit(level, pos)
                || BlastPitWarmZoneRegistry.isInsideWarmZone(level, pos)
                || ThermalVentRegistry.isVolcanicField(level, pos)
                || level.getBlockEntity(pos) != null && !isHearthSceneBlock(state)) {
            return false;
        }
        return canReplaceNatural(state, placement);
    }

    static boolean allowsFluidReplacement(BlockState state, HearthStructurePiece piece) {
        return state.getFluidState().isEmpty()
                || piece == HearthStructurePiece.FOUNDATION_SUPPORT
                && (state.getFluidState().getType() == Fluids.WATER
                || state.getFluidState().getType() == Fluids.FLOWING_WATER);
    }

    static boolean allowsFluidReplacement(BlockState state, HearthStructurePlacement placement) {
        if (allowsFluidReplacement(state, placement.piece())) {
            return true;
        }
        if (placement.offset().getY() != -1
                || state.getFluidState().getType() != Fluids.WATER
                && state.getFluidState().getType() != Fluids.FLOWING_WATER) {
            return false;
        }
        return switch (placement.piece()) {
            case PACKED_ICE_LOWER, FROZEN_PLANKS, FROZEN_STONE_BRICKS -> true;
            default -> false;
        };
    }

    static boolean needsPlacement(BlockState existing, BlockState desired) {
        return !existing.equals(desired);
    }

    static boolean isClearancePiece(HearthStructurePiece piece) {
        return switch (piece) {
            case CLEAR_TRANSIENT, CLEAR_SETTLEMENT, CLEAR_PLATFORM, CLEAR_LEGACY -> true;
            default -> false;
        };
    }

    static boolean isOptionalTerrainDecoration(HearthStructurePiece piece) {
        return piece == HearthStructurePiece.SNOW_MARKER
                || piece == HearthStructurePiece.FROZEN_ATMOSPHERE;
    }

    static int failureAttemptLimit(PlacementFailureKind kind) {
        return switch (kind) {
            case ENTITY -> ENTITY_BLOCK_RETRY_LIMIT;
            case SET_BLOCK -> SET_BLOCK_RETRY_LIMIT;
            case PERMANENT_BLOCKER -> 1;
        };
    }

    static boolean shouldSkipAfterFailure(PlacementFailureKind kind, int attempts) {
        return attempts >= failureAttemptLimit(kind);
    }

    private static int recordPlacementFailure(
            UUID hearthId,
            int planVersion,
            int cursor,
            PlacementFailureKind kind) {
        PlacementFailureKey key = new PlacementFailureKey(hearthId, planVersion, cursor, kind);
        return placementFailures.merge(key, 1, Integer::sum);
    }

    private static void clearPlacementFailure(UUID hearthId, int planVersion, int cursor) {
        placementFailures.keySet().removeIf(key -> key.hearthId().equals(hearthId)
                && key.planVersion() == planVersion
                && key.cursor() == cursor);
    }

    private static int skipBlockedPlacement(
            UUID hearthId,
            HearthReconciliationPolicy.StructurePlan plan,
            int cursor,
            HearthStructurePlacement placement,
            BlockPos target,
            BlockState existing,
            PlacementFailureKind kind,
            int attempts) {
        clearPlacementFailure(hearthId, plan.version(), cursor);
        int logCount = skippedLogCounts.merge(hearthId, 1, Integer::sum);
        if (logCount <= SKIP_LOG_LIMIT) {
            FrozenDawn.LOGGER.warn(
                    "Hearth {} {} plan {} skipped {} at cursor {} ({}) after {} {} attempt(s) "
                            + "over {}; reconciliation will continue",
                    shortId(hearthId), plan.stage().name().toLowerCase(), plan.version(),
                    placement.piece().name().toLowerCase(), cursor, formatPos(target), attempts,
                    kind.name().toLowerCase(), BuiltInRegistries.BLOCK.getKey(existing.getBlock()));
        } else if (logCount == SKIP_LOG_LIMIT + 1) {
            FrozenDawn.LOGGER.warn(
                    "Hearth {} has more than {} degraded placements; suppressing further "
                            + "per-block warnings while reconciliation continues",
                    shortId(hearthId), SKIP_LOG_LIMIT);
        }
        return 1;
    }

    static boolean canReplaceNatural(BlockState state,
                                     HearthStructurePlacement placement) {
        if (placement.piece() == HearthStructurePiece.FOUNDATION_SUPPORT) {
            return allowsFluidReplacement(state, placement.piece());
        }
        if (placement.piece() == HearthStructurePiece.CLEAR_TRANSIENT) {
            return state.isAir() || isTransientSurface(state);
        }
        if (placement.piece() == HearthStructurePiece.CLEAR_SETTLEMENT) {
            return state.isAir() || isTransientSurface(state)
                    || isHearthSceneBlock(state)
                    || isNaturalSettlementObstruction(state);
        }
        if (isTransientSurface(state)) {
            return true;
        }
        if ((placement.piece() == HearthStructurePiece.CLEAR_PLATFORM
                || placement.piece() == HearthStructurePiece.CLEAR_LEGACY)
                && isHearthSceneBlock(state)) {
            return true;
        }
        if (isHearthSceneBlock(state)) {
            return true;
        }
        if (isNaturalSettlementObstruction(state)) {
            return true;
        }
        if (placement.offset().getY() == -1) {
            return state.canBeReplaced() || isNaturalSettlementObstruction(state);
        }
        return state.isAir() || state.canBeReplaced();
    }

    private static boolean isHearthSceneBlock(BlockState state) {
        return state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.FURNACE)
                || state.is(Blocks.CHEST)
                || state.is(Blocks.SPRUCE_DOOR)
                || state.is(Blocks.GRAY_BED)
                || state.is(ModBlocks.FROZEN_PLANKS.get())
                || state.is(ModBlocks.FROZEN_STONE_BRICKS.get())
                || state.is(ModBlocks.FROZEN_ATMOSPHERE.get())
                || state.is(ModBlocks.HEARTH_BOUNDARY_MARKER.get())
                || state.is(ModBlocks.ORSA_SUPPLY_CRATE.get());
    }

    private static BlockState stateFor(HearthStructurePlacement placement) {
        return switch (placement.piece()) {
            case FOUNDATION_SUPPORT, PACKED_ICE_LOWER -> Blocks.PACKED_ICE.defaultBlockState();
            case CLEAR_TRANSIENT, CLEAR_SETTLEMENT, CLEAR_PLATFORM, CLEAR_LEGACY ->
                    Blocks.AIR.defaultBlockState();
            case SNOW_MARKER -> Blocks.SNOW.defaultBlockState()
                    .setValue(SnowLayerBlock.LAYERS, Math.max(1, Math.min(8, placement.variant())));
            case COLD_CAMPFIRE -> Blocks.CAMPFIRE.defaultBlockState()
                    .setValue(CampfireBlock.LIT, false)
                    .setValue(CampfireBlock.FACING, placement.facing());
            case COLD_FURNACE -> Blocks.FURNACE.defaultBlockState()
                    .setValue(FurnaceBlock.LIT, false)
                    .setValue(FurnaceBlock.FACING, placement.facing());
            case ORSA_CRATE -> ModBlocks.ORSA_SUPPLY_CRATE.get().defaultBlockState()
                    .setValue(BarrelBlock.FACING, placement.facing());
            case FROZEN_PLANKS -> ModBlocks.FROZEN_PLANKS.get().defaultBlockState();
            case FROZEN_STONE_BRICKS -> ModBlocks.FROZEN_STONE_BRICKS.get().defaultBlockState();
            case FROZEN_ATMOSPHERE -> ModBlocks.FROZEN_ATMOSPHERE.get().defaultBlockState();
            // Kept in layouts only long enough to clear markers from experimental saves.
            case BOUNDARY_MARKER -> Blocks.AIR.defaultBlockState();
            case PROTECTED_CHEST, SACRED_CHEST -> Blocks.CHEST.defaultBlockState()
                    .setValue(ChestBlock.FACING, placement.facing())
                    .setValue(ChestBlock.TYPE, ChestType.SINGLE)
                    .setValue(ChestBlock.WATERLOGGED, false);
            case DOOR_LOWER, DOOR_UPPER -> Blocks.SPRUCE_DOOR.defaultBlockState()
                    .setValue(DoorBlock.FACING, placement.facing())
                    .setValue(DoorBlock.HALF, placement.piece() == HearthStructurePiece.DOOR_LOWER
                            ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER)
                    .setValue(DoorBlock.HINGE, placement.variant() == 0
                            ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT)
                    .setValue(DoorBlock.OPEN, false)
                    .setValue(DoorBlock.POWERED, false);
            case BED_FOOT, BED_HEAD -> Blocks.GRAY_BED.defaultBlockState()
                    .setValue(BedBlock.FACING, placement.facing())
                    .setValue(BedBlock.PART, placement.piece() == HearthStructurePiece.BED_FOOT
                            ? BedPart.FOOT : BedPart.HEAD)
                    .setValue(BedBlock.OCCUPIED, false);
        };
    }

    private static boolean hasSolidFoundation(ServerLevel level, BlockPos pos,
                                              BlockState state) {
        if (isTransientSurface(state)) {
            return false;
        }
        return state.getFluidState().isEmpty()
                && !state.canBeReplaced()
                && !state.getCollisionShape(level, pos).isEmpty();
    }

    private static int stableSurfaceHeight(ServerLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        int floor = Math.max(level.getMinBuildHeight(), top - SURFACE_SCAN_DEPTH);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = top; y >= floor; y--) {
            cursor.set(x, y, z);
            BlockState state = level.getBlockState(cursor);
            if (state.isAir() || isTransientSurface(state)) {
                continue;
            }
            return state.isFaceSturdy(level, cursor, Direction.UP)
                    ? y + 1 : Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }

    private static boolean isTransientSurface(BlockState state) {
        return state.is(Blocks.SNOW)
                || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(ModBlocks.FROZEN_ATMOSPHERE.get())
                || state.is(ModBlocks.ACHERONITE_CRYSTAL.get());
    }

    private static boolean isNaturalSettlementObstruction(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.DIRT)
                || state.is(BlockTags.SAND)
                || state.is(BlockTags.LOGS)
                || state.is(BlockTags.LEAVES)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.ICE)
                || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.BLUE_ICE)
                || state.is(ModBlocks.DEAD_GRASS_BLOCK.get())
                || state.is(ModBlocks.FROZEN_DIRT.get())
                || state.is(ModBlocks.FROZEN_SAND.get())
                || state.is(ModBlocks.FROZEN_COBBLESTONE.get());
    }

    private static boolean footprintLoaded(ServerLevel level, BlockPos center, int radius) {
        int minChunkX = (center.getX() - radius) >> 4;
        int maxChunkX = (center.getX() + radius) >> 4;
        int minChunkZ = (center.getZ() - radius) >> 4;
        int maxChunkZ = (center.getZ() + radius) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                BlockPos probe = new BlockPos((chunkX << 4) + 8, center.getY(), (chunkZ << 4) + 8);
                if (!level.isLoaded(probe)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean footprintCaughtUp(ServerLevel level, ApocalypseState apocalypse,
                                             BlockPos center, int radius) {
        ChunkEpochState epochs = ChunkEpochState.get(level.getServer());
        int minChunkX = (center.getX() - radius) >> 4;
        int maxChunkX = (center.getX() + radius) >> 4;
        int minChunkZ = (center.getZ() - radius) >> 4;
        int maxChunkZ = (center.getZ() + radius) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                ChunkEpochState.Record record = epochs.get(chunkX, chunkZ);
                if (record == null || !record.complete()
                        || record.transformVersion() < ChunkCatchUpManager.TRANSFORM_VERSION
                        || record.targetPhase() < apocalypse.getPhase()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<HearthStructurePlacement> layoutFor(
            ReturnedHearthSavedData.HearthRecord hearth,
            ReturnedHearthSavedData.HearthStage stage) {
        if (stage == ReturnedHearthSavedData.HearthStage.INTACT
                && hearth.type() == HearthSelectionPolicy.HearthType.MAJOR) {
            return IntactHearthLayout.create(hearth.layoutSeed(), hearth.type());
        }
        if (stage.ordinal() >= ReturnedHearthSavedData.HearthStage.FORMED.ordinal()) {
            return FormedHearthLayout.create(hearth.layoutSeed(), hearth.type());
        }
        return TraceHearthLayout.create(hearth.layoutSeed(), hearth.type());
    }

    private static void initializeFormedLoot(ServerLevel level, BlockPos pos, long layoutSeed) {
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest) || !chest.isEmpty()) {
            return;
        }

        RandomSource random = RandomSource.create(layoutSeed ^ pos.asLong()
                ^ 0x464F524D45445F4CL);
        int[] slots = {3, 7, 11, 15, 20};
        chest.setItem(slots[0], new ItemStack(ModItems.TATTERED_CLOTHING_SCRAP.get(),
                2 + random.nextInt(3)));
        chest.setItem(slots[1], new ItemStack(Items.CHARCOAL, 3 + random.nextInt(5)));
        chest.setItem(slots[2], new ItemStack(ModItems.ICE_SHARD.get(),
                2 + random.nextInt(4)));
        if (random.nextFloat() < 0.55F) {
            chest.setItem(slots[3], new ItemStack(ModItems.FROZEN_HEART.get()));
        }
        if (random.nextFloat() < 0.35F) {
            chest.setItem(slots[4], new ItemStack(Items.IRON_INGOT, 1 + random.nextInt(3)));
        }
        chest.setChanged();
    }

    private static void initializeOrsaCrateLoot(
            ServerLevel level,
            BlockPos pos,
            long layoutSeed,
            ReturnedHearthSavedData.HearthStage stage) {
        if (!(level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel)
                || !barrel.isEmpty()) {
            return;
        }

        RandomSource random = RandomSource.create(
                layoutSeed ^ pos.asLong() ^ 0x4F5253415F435241L
                        ^ ((long) stage.ordinal() << 48));
        int[] slots = {4, 13, 22, 10, 16, 1, 25, 7};
        List<ItemStack> loot = new java.util.ArrayList<>();
        loot.add(new ItemStack(ModItems.TATTERED_CLOTHING_SCRAP.get(),
                1 + random.nextInt(3)));
        loot.add(new ItemStack(Items.CHARCOAL, 2 + random.nextInt(4)));
        loot.add(new ItemStack(ModItems.ICE_SHARD.get(), 1 + random.nextInt(4)));

        if (stage.ordinal() >= ReturnedHearthSavedData.HearthStage.FORMED.ordinal()) {
            loot.add(random.nextBoolean()
                    ? new ItemStack(ModItems.ORSA_SUIT_PATCH_KIT.get())
                    : new ItemStack(ModItems.EMERGENCY_O2_CARTRIDGE.get()));
            if (random.nextFloat() < 0.45F) {
                loot.add(OrsaIdBadgeItem.createNamed(
                        layoutSeed ^ pos.asLong(), random.nextInt(8)));
            }
            if (random.nextFloat() < 0.35F) {
                loot.add(new ItemStack(ModItems.CRYO_FUEL.get(),
                        1 + random.nextInt(3)));
            }
        }
        if (stage == ReturnedHearthSavedData.HearthStage.INTACT) {
            if (random.nextFloat() < 0.28F) {
                loot.add(new ItemStack(ModItems.ROCKET_FUEL_CELL.get()));
            }
            if (random.nextFloat() < 0.18F) {
                loot.add(new ItemStack(ModItems.ORSA_TRAUMA_KIT.get()));
            }
        }

        for (int index = 0; index < loot.size() && index < slots.length; index++) {
            barrel.setItem(slots[index], loot.get(index));
        }
        barrel.setChanged();
    }

    private static void initializeIntactLoot(ServerLevel level, BlockPos pos, long layoutSeed) {
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest) || !chest.isEmpty()) {
            return;
        }

        RandomSource random = RandomSource.create(layoutSeed ^ pos.asLong()
                ^ 0x494E544143545F4CL);
        int[] slots = {1, 3, 5, 7, 9, 11, 13, 15, 17, 19, 21, 23, 25, 0, 8, 18, 26, 4};
        chest.setItem(slots[0], new ItemStack(ModItems.TATTERED_CLOTHING_SCRAP.get(),
                4 + random.nextInt(5)));
        chest.setItem(slots[1], new ItemStack(ModItems.FROZEN_ATMOSPHERE_SHARD.get(),
                3 + random.nextInt(5)));
        chest.setItem(slots[2], new ItemStack(ModItems.FROZEN_HEART.get(),
                1 + random.nextInt(2)));
        chest.setItem(slots[3], new ItemStack(ModItems.ORSA_SUIT_PATCH_KIT.get(),
                1 + random.nextInt(2)));
        chest.setItem(slots[4], new ItemStack(ModItems.EMERGENCY_O2_CARTRIDGE.get(),
                1 + random.nextInt(2)));
        chest.setItem(slots[5], new ItemStack(ModItems.ORSA_TRAUMA_KIT.get()));
        chest.setItem(slots[6], new ItemStack(ModItems.BLACKGLASS_ARCHIVE_CARD.get()));
        chest.setItem(slots[7], OrsaIdBadgeItem.createNamed(layoutSeed, 0));
        chest.setItem(slots[8], OrsaIdBadgeItem.createNamed(layoutSeed, 1));

        ItemStack rocketPart = switch (random.nextInt(4)) {
            case 0 -> new ItemStack(ModItems.ROCKET_ENGINE.get());
            case 1 -> new ItemStack(ModItems.ROCKET_FIN.get());
            case 2 -> new ItemStack(ModItems.ROCKET_HULL.get());
            default -> new ItemStack(ModItems.ROCKET_NOSE_CONE.get());
        };
        chest.setItem(slots[9], rocketPart);
        chest.setItem(slots[10], new ItemStack(ModItems.ROCKET_FUEL_CELL.get(),
                1 + random.nextInt(2)));
        chest.setItem(slots[11], new ItemStack(ModItems.CRYO_FUEL.get(),
                2 + random.nextInt(3)));

        if (random.nextFloat() < 0.70F) {
            chest.setItem(slots[12], new ItemStack(ModItems.O2_EFFICIENCY_MODULE.get()));
        }
        if (random.nextFloat() < 0.60F) {
            chest.setItem(slots[13], new ItemStack(ModItems.THERMAL_CAPACITOR.get()));
        }
        if (random.nextFloat() < 0.40F) {
            chest.setItem(slots[14], new ItemStack(ModItems.REFINED_ACHERONITE.get()));
        }
        if (random.nextFloat() < 0.30F) {
            chest.setItem(slots[15], new ItemStack(ModItems.ARCHITECT_SOUL.get()));
        }
        if (random.nextFloat() < 0.50F) {
            chest.setItem(slots[16], new ItemStack(ModItems.MIRRORED_FRAGMENT.get()));
        }
        if (random.nextFloat() < 0.55F) {
            chest.setItem(slots[17], random.nextBoolean()
                    ? new ItemStack(ModItems.LIQUID_OXIDIZER.get(), 2 + random.nextInt(3))
                    : new ItemStack(ModItems.SOLID_PROPELLANT.get(), 3 + random.nextInt(4)));
        }
        chest.setChanged();
    }

    private static boolean centerAreaLoaded(ServerLevel level, BlockPos center) {
        return level.isLoaded(new BlockPos(center.getX(), level.getMinBuildHeight(), center.getZ()));
    }

    private static boolean flatDistanceWithin(BlockPos first, BlockPos second, int radius) {
        if (first == null) {
            return false;
        }
        long dx = (long) first.getX() - second.getX();
        long dz = (long) first.getZ() - second.getZ();
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    private static void removePending(UUID id) {
        pending.remove(id);
        surfaceSearchCursors.remove(id);
        surfaceFallbacks.remove(id);
        retryAfter.remove(id);
        waitReasons.remove(id);
        placementFailures.keySet().removeIf(key -> key.hearthId().equals(id));
        skippedLogCounts.remove(id);
    }

    private static String waitingSummary() {
        if (pending.isEmpty()) {
            return "none";
        }
        return pending.stream()
                .sorted()
                .map(id -> shortId(id) + ":" + waitReasons.getOrDefault(id, "queued"))
                .reduce((first, second) -> first + "," + second)
                .orElse("none");
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "/" + pos.getY() + "/" + pos.getZ();
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    enum PlacementFailureKind {
        ENTITY,
        SET_BLOCK,
        PERMANENT_BLOCKER
    }

    private record PlacementFailureKey(
            UUID hearthId,
            int planVersion,
            int cursor,
            PlacementFailureKind kind) {
    }

    private record SurfaceCandidate(BlockPos center, int variance, boolean strict) {
    }

    private record SurfaceResolution(BlockPos center, boolean exhausted, boolean fallback) {
    }
}

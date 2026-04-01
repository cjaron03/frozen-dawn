package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.FrozenTownState;
import com.frozendawn.block.AlarmBeaconBlock;
import com.frozendawn.block.OrsaFlagBlock;
import com.frozendawn.block.OrsaFlagBlockEntity;
import com.frozendawn.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static net.minecraft.core.component.DataComponents.CUSTOM_NAME;
import static net.minecraft.core.component.DataComponents.LORE;
import static net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class FrozenTownRuntime {

    private static final ResourceKey<Structure> FROZEN_TOWN = ResourceKey.create(
            Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "frozen_town")
    );

    private static final Set<Long> pendingTownChunks = ConcurrentHashMap.newKeySet();
    private static final float EXTRA_WALL_ALARM_CHANCE = 0.34f;
    private static final int BASE_WALL_ALARM_COUNT = 5;
    private static final int MAX_WALL_ALARM_COUNT = 6;
    private static final int WALL_ALARM_MIN_RADIUS = 9;
    private static final int WALL_ALARM_MAX_RADIUS = 24;
    private static final int WALL_ALARM_MIN_SEPARATION = 8;
    private static final int CENTRAL_WALL_ALARM_EXCLUSION_RADIUS = 8;

    private FrozenTownRuntime() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != ServerLevel.OVERWORLD) {
            return;
        }

        ChunkPos chunkPos = event.getChunk().getPos();
        if (chunkHasFrozenTown(level, chunkPos)) {
            pendingTownChunks.add(packChunkPos(chunkPos.x, chunkPos.z));
        }
    }

    public static void tickProcessing(ServerLevel level) {
        if (level.players().isEmpty() || pendingTownChunks.isEmpty()) {
            return;
        }

        FrozenTownState state = FrozenTownState.get(level.getServer());
        for (Long packed : Set.copyOf(pendingTownChunks)) {
            int chunkX = unpackChunkX(packed);
            int chunkZ = unpackChunkZ(packed);
            if (!level.isLoaded(new BlockPos(chunkX << 4, level.getMinBuildHeight(), chunkZ << 4))) {
                continue;
            }

            LevelChunk chunk = level.getChunk(chunkX, chunkZ);
            if (state.isChunkProcessed(chunkX, chunkZ)) {
                ensureTownAlarms(level, chunk);
                pendingTownChunks.remove(packed);
                continue;
            }

            if (!chunkHasFrozenTown(level, new ChunkPos(chunkX, chunkZ))) {
                state.markChunkProcessed(chunkX, chunkZ);
                pendingTownChunks.remove(packed);
                continue;
            }

            processTownChunk(level, chunk);
            state.markChunkProcessed(chunkX, chunkZ);
            pendingTownChunks.remove(packed);
        }
    }

    public static boolean isInsideFrozenTown(ServerLevel level, BlockPos pos) {
        Structure structure = getFrozenTownStructure(level);
        if (structure == null) {
            return false;
        }
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, structure);
        return start != null && start.isValid();
    }

    public static boolean shouldSuppressHostileSpawn(ServerLevel level, BlockPos pos, boolean naturalSpawn, Object entity) {
        return naturalSpawn && entity instanceof Enemy && isInsideFrozenTown(level, pos);
    }

    public static void reset() {
        pendingTownChunks.clear();
    }

    private static void processTownChunk(ServerLevel level, LevelChunk chunk) {
        ensureTownAlarms(level, chunk);
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity instanceof BarrelBlockEntity barrel) {
                fillTownContainer(level, barrel);
            } else if (blockEntity instanceof SignBlockEntity sign) {
                updateTownSign(level, sign);
            }
        }
    }

    private static void ensureTownAlarms(ServerLevel level, LevelChunk chunk) {
        Set<BlockPos> flagPositions = new LinkedHashSet<>();
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity instanceof OrsaFlagBlockEntity flag) {
                flagPositions.add(flag.getBlockPos().immutable());
            }
        }
        scanForTownFlags(level, chunk.getPos(), flagPositions);
        for (BlockPos flagPos : flagPositions) {
            BlockState state = level.getBlockState(flagPos);
            if (state.is(ModBlocks.ORSA_FLAG.get())) {
                ensureTownAlarm(level, flagPos, state);
                ensureWallMountedTownAlarms(level, flagPos);
            }
        }
    }

    private static void fillTownContainer(ServerLevel level, BarrelBlockEntity barrel) {
        Component customName = barrel.getCustomName();
        if (customName == null || !barrel.isEmpty()) {
            return;
        }

        String role = customName.getString();
        RandomSource random = RandomSource.create(level.getSeed() ^ barrel.getBlockPos().asLong() ^ role.hashCode());
        List<ItemStack> items = switch (role) {
            case "Kitchen Pantry", "Hall Closet", "Basement Storage", "Apartment Cupboard" -> createResidentialLoot(random);
            case "Grocery Shelf", "Cold Case" -> createGroceryLoot(random);
            case "Hardware Shelf", "Tool Cage" -> createHardwareLoot(random);
            case "Fuel Locker", "Garage Stock" -> createGasStationLoot(random);
            case "Medicine Cabinet", "Back Room Stock" -> createPharmacyLoot(random);
            case "Church Office", "Offering Plate" -> createChurchLoot(random);
            case "School Supplies", "Library Cart" -> createSchoolLoot(random);
            case "Town Records", "Filing Cabinet" -> createTownHallLoot(level, barrel.getBlockPos(), random);
            case "Fire Locker" -> createFireStationLoot(random);
            default -> List.of();
        };

        for (int slot = 0; slot < items.size() && slot < barrel.getContainerSize(); slot++) {
            barrel.setItem(slot, items.get(slot));
        }
        barrel.setChanged();
    }

    private static void updateTownSign(ServerLevel level, SignBlockEntity sign) {
        String marker = sign.getFrontText().getMessage(0, false).getString();
        if (!"EVAC NOTICE".equals(marker)) {
            return;
        }

        CampDirectiveHelper.CampDirective directive = CampDirectiveHelper.findNearestCamp(level, sign.getBlockPos());
        if (directive == null) {
            return;
        }

        sign.updateText(text -> text
                .setMessage(0, Component.literal("REPORT TO"))
                .setMessage(1, Component.literal("CAMP " + directive.designation()))
                .setMessage(2, Component.literal("X:" + directive.pos().getX()))
                .setMessage(3, Component.literal("Z:" + directive.pos().getZ())), true);
        sign.setChanged();
    }

    private static void ensureTownAlarm(ServerLevel level, BlockPos flagPos, BlockState flagState) {
        for (BlockPos scanPos : BlockPos.betweenClosed(flagPos.offset(-4, -1, -4), flagPos.offset(4, 2, 4))) {
            if (level.getBlockState(scanPos).is(ModBlocks.ALARM_BEACON.get())) {
                return;
            }
        }

        Direction facing = flagState.hasProperty(OrsaFlagBlock.FACING)
                ? flagState.getValue(OrsaFlagBlock.FACING)
                : Direction.NORTH;
        Direction[] directions = {
                facing.getCounterClockWise(),
                facing.getClockWise(),
                facing.getOpposite(),
                facing
        };

        for (int distance = 1; distance <= 2; distance++) {
            for (Direction direction : directions) {
                BlockPos candidatePos = flagPos.relative(direction, distance);
                if (placeTownAlarm(level, candidatePos, direction.getOpposite())) {
                    return;
                }
            }
        }

        for (Direction first : directions) {
            for (Direction second : directions) {
                if (first == second || first == second.getOpposite()) {
                    continue;
                }
                BlockPos candidatePos = flagPos.relative(first).relative(second);
                if (placeTownAlarm(level, candidatePos, facing)) {
                    return;
                }
            }
        }
    }

    private static boolean placeTownAlarm(ServerLevel level, BlockPos pos, Direction facing) {
        if (!canPlaceTownAlarm(level, pos)) {
            return false;
        }
        level.setBlock(pos, ModBlocks.ALARM_BEACON.get().defaultBlockState()
                .setValue(AlarmBeaconBlock.FACING, facing), 3);
        return true;
    }

    private static void scanForTownFlags(ServerLevel level, ChunkPos chunkPos, Set<BlockPos> out) {
        int minX = chunkPos.getMinBlockX() - 8;
        int maxX = chunkPos.getMaxBlockX() + 8;
        int minZ = chunkPos.getMinBlockZ() - 8;
        int maxZ = chunkPos.getMaxBlockZ() + 8;
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!level.hasChunkAt(new BlockPos(x, minY, z))) {
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    if (level.getBlockState(cursor).is(ModBlocks.ORSA_FLAG.get())) {
                        out.add(cursor.immutable());
                    }
                }
            }
        }
    }

    private static boolean canPlaceTownAlarm(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockState above = level.getBlockState(pos.above());
        return (state.isAir() || state.canBeReplaced())
                && (above.isAir() || above.canBeReplaced())
                && level.getFluidState(pos).isEmpty()
                && level.getFluidState(pos.above()).isEmpty()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    private static void ensureWallMountedTownAlarms(ServerLevel level, BlockPos flagPos) {
        clearCentralWallAlarms(level, flagPos);

        BoundingBox townBounds = getFrozenTownBounds(level, flagPos);
        int existing = countNearbyWallAlarms(level, townBounds, flagPos);
        List<WallAlarmAnchor> anchors = collectWallAlarmAnchors(level, flagPos, townBounds);
        int desired = desiredWallAlarmCount(level, flagPos);
        if (existing >= desired) {
            return;
        }

        for (WallAlarmAnchor anchor : anchors) {
            if (existing >= desired) {
                return;
            }

            WallAlarmCandidate candidate = findWallAlarmCandidate(level, flagPos, anchor, townBounds);
            if (candidate == null) {
                continue;
            }

            placeWallAlarm(level, candidate);
            existing++;
        }

        while (existing < desired) {
            WallAlarmCandidate fallback = findWallAlarmCandidate(level, flagPos, null, townBounds);
            if (fallback == null) {
                return;
            }

            placeWallAlarm(level, fallback);
            existing++;
        }
    }

    private static int desiredWallAlarmCount(ServerLevel level, BlockPos flagPos) {
        RandomSource random = RandomSource.create(level.getSeed() ^ flagPos.asLong() ^ 0x57A11F2DL);
        int desired = BASE_WALL_ALARM_COUNT;
        if (random.nextFloat() < EXTRA_WALL_ALARM_CHANCE) {
            desired++;
        }
        return Math.min(desired, MAX_WALL_ALARM_COUNT);
    }

    private static int countNearbyWallAlarms(ServerLevel level, @Nullable BoundingBox townBounds, BlockPos flagPos) {
        int count = 0;
        if (townBounds != null) {
            for (ChunkPos chunkPos : townBounds.intersectingChunks().toList()) {
                BlockPos chunkOrigin = new BlockPos(chunkPos.getMinBlockX(), level.getMinBuildHeight(), chunkPos.getMinBlockZ());
                if (!level.hasChunkAt(chunkOrigin)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity.getBlockState().is(ModBlocks.WALL_ALARM_BEACON.get())
                            && contains(townBounds, blockEntity.getBlockPos())) {
                        count++;
                    }
                }
            }
            return count;
        }

        for (BlockPos scanPos : BlockPos.betweenClosed(flagPos.offset(-WALL_ALARM_MAX_RADIUS, -2, -WALL_ALARM_MAX_RADIUS),
                flagPos.offset(WALL_ALARM_MAX_RADIUS, 8, WALL_ALARM_MAX_RADIUS))) {
            if (level.getBlockState(scanPos).is(ModBlocks.WALL_ALARM_BEACON.get())) {
                count++;
            }
        }
        return count;
    }

    private static void clearCentralWallAlarms(ServerLevel level, BlockPos flagPos) {
        List<BlockPos> toRemove = new ArrayList<>();
        for (BlockPos scanPos : BlockPos.betweenClosed(
                flagPos.offset(-CENTRAL_WALL_ALARM_EXCLUSION_RADIUS, -2, -CENTRAL_WALL_ALARM_EXCLUSION_RADIUS),
                flagPos.offset(CENTRAL_WALL_ALARM_EXCLUSION_RADIUS, 8, CENTRAL_WALL_ALARM_EXCLUSION_RADIUS))) {
            if (level.getBlockState(scanPos).is(ModBlocks.WALL_ALARM_BEACON.get())) {
                toRemove.add(scanPos.immutable());
            }
        }

        for (BlockPos pos : toRemove) {
            level.removeBlock(pos, false);
        }
    }

    @Nullable
    private static WallAlarmCandidate findWallAlarmCandidate(ServerLevel level, BlockPos flagPos,
                                                             @Nullable WallAlarmAnchor anchor,
                                                             @Nullable BoundingBox townBounds) {
        WallAlarmCandidate best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        int minX = anchor != null ? anchor.pos().getX() - anchor.searchRadius() : flagPos.getX() - WALL_ALARM_MAX_RADIUS;
        int maxX = anchor != null ? anchor.pos().getX() + anchor.searchRadius() : flagPos.getX() + WALL_ALARM_MAX_RADIUS;
        int minZ = anchor != null ? anchor.pos().getZ() - anchor.searchRadius() : flagPos.getZ() - WALL_ALARM_MAX_RADIUS;
        int maxZ = anchor != null ? anchor.pos().getZ() + anchor.searchRadius() : flagPos.getZ() + WALL_ALARM_MAX_RADIUS;
        int minY = anchor != null ? Math.max(flagPos.getY() + 1, anchor.pos().getY() - 1) : flagPos.getY() + 1;
        int maxY = anchor != null ? Math.min(flagPos.getY() + 7, anchor.pos().getY() + 3) : flagPos.getY() + 6;
        if (townBounds != null) {
            minX = Math.max(minX, townBounds.minX());
            maxX = Math.min(maxX, townBounds.maxX());
            minZ = Math.max(minZ, townBounds.minZ());
            maxZ = Math.min(maxZ, townBounds.maxZ());
            minY = Math.max(minY, townBounds.minY());
            maxY = Math.min(maxY, townBounds.maxY());
        }

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.hasChunkAt(pos)) {
                        continue;
                    }

                    double horizontalDistance = Math.hypot(x - flagPos.getX(), z - flagPos.getZ());
                    if (horizontalDistance < WALL_ALARM_MIN_RADIUS) {
                        continue;
                    }
                    if (townBounds == null && horizontalDistance > WALL_ALARM_MAX_RADIUS) {
                        continue;
                    }
                    if (anchor != null && pos.distSqr(anchor.pos()) > anchor.searchRadius() * anchor.searchRadius()) {
                        continue;
                    }

                    for (Direction facing : Direction.Plane.HORIZONTAL) {
                        if (!canPlaceWallAlarm(level, pos, facing)) {
                            continue;
                        }
                        if (hasWallAlarmNear(level, pos, WALL_ALARM_MIN_SEPARATION)) {
                            continue;
                        }

                        double alignment = centerFacingAlignment(flagPos, pos, facing);
                        double minAlignment = anchor != null ? 0.45 : 0.12;
                        if (alignment < minAlignment) {
                            continue;
                        }

                        int openness = forwardAirDepth(level, pos, facing, 3);
                        if (openness < 2) {
                            continue;
                        }

                        BlockPos anchorPos = pos.relative(facing.getOpposite());
                        BlockState anchorState = level.getBlockState(anchorPos);
                        double targetDistance = anchor != null ? 14.0 : 16.0;
                        double score = openness * 3.0
                                + alignment * 6.0
                                - Math.abs(horizontalDistance - targetDistance) * 0.35
                                - Math.abs(y - (flagPos.getY() + 3)) * 0.4
                                + facadeBonus(anchorState);
                        if (anchor != null) {
                            double anchorDistance = Math.hypot(pos.getX() - anchor.pos().getX(), pos.getZ() - anchor.pos().getZ());
                            score += anchor.priority() * 3.2
                                    - anchorDistance * 0.9
                                    - Math.abs(y - anchor.pos().getY()) * 0.3;
                        }

                        if (score > bestScore) {
                            bestScore = score;
                            best = new WallAlarmCandidate(pos.immutable(), facing, score);
                        }
                    }
                }
            }
        }

        return best;
    }

    private static void placeWallAlarm(ServerLevel level, WallAlarmCandidate candidate) {
        level.setBlock(candidate.pos(), ModBlocks.WALL_ALARM_BEACON.get().defaultBlockState()
                .setValue(AlarmBeaconBlock.FACING, candidate.facing()), 3);
    }

    private static boolean hasWallAlarmNear(ServerLevel level, BlockPos pos, int radius) {
        for (BlockPos scanPos : BlockPos.betweenClosed(pos.offset(-radius, -2, -radius), pos.offset(radius, 6, radius))) {
            if (level.getBlockState(scanPos).is(ModBlocks.WALL_ALARM_BEACON.get())) {
                return true;
            }
        }
        return false;
    }

    private static List<WallAlarmAnchor> collectWallAlarmAnchors(ServerLevel level, BlockPos flagPos,
                                                                 @Nullable BoundingBox townBounds) {
        List<WallAlarmAnchor> anchors = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        if (townBounds != null) {
            townBounds.intersectingChunks().forEach(chunkPos -> collectWallAlarmAnchorsFromChunk(level, chunkPos, townBounds, seen, anchors));
        } else {
            int chunkRadius = (WALL_ALARM_MAX_RADIUS + 15) >> 4;
            int centerChunkX = flagPos.getX() >> 4;
            int centerChunkZ = flagPos.getZ() >> 4;
            for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
                for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                    collectWallAlarmAnchorsFromChunk(level, new ChunkPos(chunkX, chunkZ), null, seen, anchors);
                }
            }
        }

        anchors.sort((left, right) -> {
            int priorityCompare = Integer.compare(right.priority(), left.priority());
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            return Double.compare(right.pos().distSqr(flagPos), left.pos().distSqr(flagPos));
        });
        return anchors;
    }

    private static void collectWallAlarmAnchorsFromChunk(ServerLevel level, ChunkPos chunkPos,
                                                         @Nullable BoundingBox townBounds,
                                                         Set<Long> seen,
                                                         List<WallAlarmAnchor> anchors) {
        BlockPos chunkOrigin = new BlockPos(chunkPos.getMinBlockX(), level.getMinBuildHeight(), chunkPos.getMinBlockZ());
        if (!level.hasChunkAt(chunkOrigin)) {
            return;
        }

        LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (townBounds != null && !contains(townBounds, blockEntity.getBlockPos())) {
                continue;
            }

            WallAlarmAnchor anchor = wallAlarmAnchorFor(blockEntity);
            if (anchor == null || !seen.add(blockEntity.getBlockPos().asLong())) {
                continue;
            }
            anchors.add(new WallAlarmAnchor(blockEntity.getBlockPos().immutable(), anchor.priority(), anchor.searchRadius()));
        }
    }

    @Nullable
    private static WallAlarmAnchor wallAlarmAnchorFor(BlockEntity blockEntity) {
        if (blockEntity instanceof BarrelBlockEntity barrel) {
            Component customName = barrel.getCustomName();
            if (customName == null) {
                return null;
            }
            return wallAlarmAnchorForRole(customName.getString(), barrel.getBlockPos());
        }
        if (blockEntity instanceof SignBlockEntity sign) {
            String line0 = sign.getFrontText().getMessage(0, false).getString();
            if ("Town Hall".equalsIgnoreCase(line0)) {
                return new WallAlarmAnchor(sign.getBlockPos().immutable(), 10, 8);
            }
            if ("Fire Station".equalsIgnoreCase(line0)) {
                return new WallAlarmAnchor(sign.getBlockPos().immutable(), 9, 8);
            }
        }
        return null;
    }

    @Nullable
    private static WallAlarmAnchor wallAlarmAnchorForRole(String role, BlockPos pos) {
        return switch (role) {
            case "Town Records", "Filing Cabinet" -> new WallAlarmAnchor(pos.immutable(), 10, 8);
            case "School Supplies", "Library Cart" -> new WallAlarmAnchor(pos.immutable(), 9, 8);
            case "Fire Locker" -> new WallAlarmAnchor(pos.immutable(), 9, 8);
            case "Church Office", "Offering Plate" -> new WallAlarmAnchor(pos.immutable(), 8, 8);
            case "Fuel Locker", "Garage Stock" -> new WallAlarmAnchor(pos.immutable(), 8, 9);
            case "Medicine Cabinet", "Back Room Stock" -> new WallAlarmAnchor(pos.immutable(), 7, 8);
            case "Hardware Shelf", "Tool Cage" -> new WallAlarmAnchor(pos.immutable(), 6, 8);
            case "Grocery Shelf", "Cold Case" -> new WallAlarmAnchor(pos.immutable(), 5, 8);
            case "Apartment Cupboard" -> new WallAlarmAnchor(pos.immutable(), 4, 8);
            case "Basement Storage" -> new WallAlarmAnchor(pos.immutable(), 3, 8);
            default -> null;
        };
    }

    private static boolean canPlaceWallAlarm(ServerLevel level, BlockPos pos, Direction facing) {
        BlockState state = level.getBlockState(pos);
        if (!(state.isAir() || state.canBeReplaced()) || !level.getFluidState(pos).isEmpty()) {
            return false;
        }

        BlockPos anchorPos = pos.relative(facing.getOpposite());
        BlockState anchorState = level.getBlockState(anchorPos);
        if (!anchorState.isFaceSturdy(level, anchorPos, facing) || !isWallAlarmMountBlock(anchorState)) {
            return false;
        }

        BlockPos frontPos = pos.relative(facing);
        return (level.getBlockState(frontPos).isAir() || level.getBlockState(frontPos).canBeReplaced())
                && level.getFluidState(frontPos).isEmpty();
    }

    private static double centerFacingAlignment(BlockPos center, BlockPos pos, Direction facing) {
        double dx = center.getX() - pos.getX();
        double dz = center.getZ() - pos.getZ();
        double length = Math.hypot(dx, dz);
        if (length < 0.001) {
            return 0.0;
        }
        return ((dx / length) * facing.getStepX()) + ((dz / length) * facing.getStepZ());
    }

    private static int forwardAirDepth(ServerLevel level, BlockPos pos, Direction facing, int depth) {
        int clear = 0;
        BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int i = 1; i <= depth; i++) {
            cursor.set(pos).move(facing, i);
            BlockState state = level.getBlockState(cursor);
            if (!(state.isAir() || state.canBeReplaced()) || !level.getFluidState(cursor).isEmpty()) {
                break;
            }
            clear++;
        }
        return clear;
    }

    private static double facadeBonus(BlockState state) {
        if (state.is(Blocks.POLISHED_BLACKSTONE_BRICKS)
                || state.is(Blocks.DEEPSLATE_BRICKS)
                || state.is(Blocks.STONE_BRICKS)
                || state.is(Blocks.STONE)
                || state.is(Blocks.SMOOTH_STONE)) {
            return 1.6;
        }
        if (state.is(Blocks.SPRUCE_PLANKS)
                || state.is(Blocks.OAK_PLANKS)
                || state.is(Blocks.DARK_OAK_PLANKS)
                || state.is(Blocks.BRICKS)) {
            return 1.0;
        }
        return 0.0;
    }

    private static boolean isWallAlarmMountBlock(BlockState state) {
        return facadeBonus(state) > 0.0;
    }

    private record WallAlarmCandidate(BlockPos pos, Direction facing, double score) {
    }

    private record WallAlarmAnchor(BlockPos pos, int priority, int searchRadius) {
    }

    @Nullable
    private static Structure getFrozenTownStructure(ServerLevel level) {
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(FROZEN_TOWN.location());
    }

    @Nullable
    private static BoundingBox getFrozenTownBounds(ServerLevel level, BlockPos pos) {
        Structure structure = getFrozenTownStructure(level);
        if (structure == null) {
            return null;
        }
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, structure);
        return start != null && start.isValid() ? start.getBoundingBox() : null;
    }

    private static boolean contains(BoundingBox bounds, BlockPos pos) {
        return pos.getX() >= bounds.minX() && pos.getX() <= bounds.maxX()
                && pos.getY() >= bounds.minY() && pos.getY() <= bounds.maxY()
                && pos.getZ() >= bounds.minZ() && pos.getZ() <= bounds.maxZ();
    }

    private static boolean chunkHasFrozenTown(ServerLevel level, ChunkPos chunkPos) {
        Structure structure = getFrozenTownStructure(level);
        if (structure == null) {
            return false;
        }
        return !level.structureManager().startsForStructure(chunkPos, candidate -> candidate == structure).isEmpty();
    }

    private static List<ItemStack> createResidentialLoot(RandomSource random) {
        List<ItemStack> items = new ArrayList<>();
        items.add(switch (random.nextInt(4)) {
            case 0 -> createWrittenBook("Grocery List", "M. Hale",
                    "milk, eggs, bread, batteries, extra blankets\n\ncold snap coming",
                    "Don't forget lamp oil if the power cuts again.");
            case 1 -> createWrittenBook("Diary Entry", "Lena",
                    "Nov 3\n\nPower went out again. Third time this week.",
                    "ORSA says the grid is being prioritized for essential services. We're not essential apparently.");
            case 2 -> createWrittenBook("Love Letter", "M",
                    "I know you're worried. I am too.",
                    "But we've survived worse than a cold winter. I'll be home Thursday. Keep the fireplace going.");
            default -> createWrittenBook("Homework", "Elliot",
                    "Write 3 sentences about what you want to be when you grow up.",
                    "I want to be an astronaut so I can live on Mars.");
        });
        items.add(new ItemStack(Items.BREAD, 1 + random.nextInt(3)));
        items.add(new ItemStack(Items.APPLE, 1 + random.nextInt(3)));
        items.add(new ItemStack(Items.POTATO, 2 + random.nextInt(4)));
        if (random.nextBoolean()) {
            items.add(new ItemStack(Items.COAL, 1 + random.nextInt(4)));
        }
        if (random.nextBoolean()) {
            items.add(new ItemStack(Items.STRING, 1 + random.nextInt(3)));
        }
        if (random.nextInt(4) == 0) {
            items.add(random.nextBoolean() ? new ItemStack(Items.IRON_AXE) : new ItemStack(Items.IRON_SHOVEL));
        }
        return items;
    }

    private static List<ItemStack> createGroceryLoot(RandomSource random) {
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(Items.BREAD, 2 + random.nextInt(3)));
        items.add(new ItemStack(Items.POTATO, 4 + random.nextInt(4)));
        items.add(new ItemStack(Items.CARROT, 2 + random.nextInt(4)));
        items.add(new ItemStack(Items.GLASS_BOTTLE, 2 + random.nextInt(3)));
        if (random.nextBoolean()) {
            items.add(new ItemStack(Items.APPLE, 2 + random.nextInt(2)));
        }
        return items;
    }

    private static List<ItemStack> createHardwareLoot(RandomSource random) {
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(Items.IRON_INGOT, 2 + random.nextInt(4)));
        items.add(random.nextBoolean() ? new ItemStack(Items.IRON_PICKAXE) : new ItemStack(Items.IRON_AXE));
        items.add(new ItemStack(Items.REDSTONE, 2 + random.nextInt(5)));
        items.add(new ItemStack(Items.COBBLESTONE, 8 + random.nextInt(8)));
        items.add(new ItemStack(Items.OAK_PLANKS, 8 + random.nextInt(8)));
        return items;
    }

    private static List<ItemStack> createGasStationLoot(RandomSource random) {
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(Items.COAL, 4 + random.nextInt(5)));
        if (random.nextBoolean()) {
            items.add(new ItemStack(Items.COAL_BLOCK, 1 + random.nextInt(2)));
        }
        if (random.nextInt(3) == 0) {
            items.add(new ItemStack(Items.BLAZE_POWDER, 1 + random.nextInt(2)));
        }
        items.add(createPaperNote("Pump Service Slip",
                "Road access suspended pending ice clearance.",
                "Customer fuel limits remain in effect."));
        return items;
    }

    private static List<ItemStack> createPharmacyLoot(RandomSource random) {
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(Items.GLASS_BOTTLE, 2 + random.nextInt(3)));
        items.add(new ItemStack(Items.SPIDER_EYE, 1 + random.nextInt(2)));
        items.add(new ItemStack(Items.SUGAR, 1 + random.nextInt(3)));
        if (random.nextInt(5) == 0) {
            items.add(new ItemStack(Items.GOLDEN_APPLE));
        }
        return items;
    }

    private static List<ItemStack> createChurchLoot(RandomSource random) {
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(Items.SOUL_TORCH, 2 + random.nextInt(3)));
        items.add(new ItemStack(Items.GOLD_NUGGET, 2 + random.nextInt(4)));
        items.add(createWrittenBook("Sermon Notes", "Pastor Elian",
                "The cold is not mercy, but it is also not the end.",
                "Keep the candles lit for those who left after dusk."));
        return items;
    }

    private static List<ItemStack> createSchoolLoot(RandomSource random) {
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(Items.PAPER, 3 + random.nextInt(5)));
        items.add(new ItemStack(Items.BOOK, 2 + random.nextInt(3)));
        items.add(new ItemStack(switch (random.nextInt(4)) {
            case 0 -> Items.BLUE_DYE;
            case 1 -> Items.RED_DYE;
            case 2 -> Items.YELLOW_DYE;
            default -> Items.GREEN_DYE;
        }, 1 + random.nextInt(2)));
        items.add(createWrittenBook("Homework", "Mira",
                "My town has a church and a grocery store.",
                "I want to be an astronaut so I can live on Mars."));
        return items;
    }

    private static List<ItemStack> createTownHallLoot(ServerLevel level, BlockPos pos, RandomSource random) {
        List<ItemStack> items = new ArrayList<>();
        CampDirectiveHelper.CampDirective directive = CampDirectiveHelper.findNearestCamp(level, pos);
        if (directive != null) {
            items.add(createPaperNote("Evacuation Notice",
                    "ALL RESIDENTS: Report to ORSA Field Camp " + directive.designation() + ".",
                    String.format(Locale.US, "Bring one bag per person. X:%d / Z:%d", directive.pos().getX(), directive.pos().getZ())));
        }
        items.add(new ItemStack(Items.PAPER, 2 + random.nextInt(4)));
        items.add(new ItemStack(Items.MAP));
        items.add(createPaperNote("Utility Ledger",
                "Heating oil allotment reduced again.",
                "Mayor signed the final revision without comment."));
        return items;
    }

    private static List<ItemStack> createFireStationLoot(RandomSource random) {
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(Items.LEATHER, 2 + random.nextInt(2)));
        items.add(new ItemStack(Items.COAL, 2 + random.nextInt(4)));
        if (random.nextBoolean()) {
            items.add(new ItemStack(Items.IRON_AXE));
        }
        return items;
    }

    private static ItemStack createPaperNote(String title, String... lines) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(CUSTOM_NAME, Component.literal(title));
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(Component.literal(line));
        }
        stack.set(LORE, new ItemLore(lore));
        return stack;
    }

    private static ItemStack createWrittenBook(String title, String author, String... pages) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(CUSTOM_NAME, Component.literal(title));
        List<Filterable<Component>> content = new ArrayList<>();
        for (String page : pages) {
            content.add(Filterable.passThrough(Component.literal(page)));
        }
        book.set(WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough(title),
                author,
                0,
                content,
                true
        ));
        return book;
    }

    private static long packChunkPos(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private static int unpackChunkX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackChunkZ(long packed) {
        return (int) packed;
    }
}

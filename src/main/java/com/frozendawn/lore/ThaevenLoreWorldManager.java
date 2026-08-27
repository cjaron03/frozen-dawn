package com.frozendawn.lore;

import com.frozendawn.data.PlayerPlacedBlockTracker;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.data.ThaevenLoreSavedData;
import com.frozendawn.homo.HearthSelectionPolicy;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;

/** Bounded, idempotent placement of shared lore carriers in loaded chunks. */
public final class ThaevenLoreWorldManager {
    private static final int CARRIER_CLEARANCE_RADIUS = 3;
    private static final int CARRIER_CLEARANCE_HEIGHT = 7;
    private static final int UNTHREADING_MIN_RADIUS = 28;
    private static final int UNTHREADING_MAX_RADIUS = 44;
    private static final double VESSEL_SEPARATION_SQR = 24.0D * 24.0D;
    private static final int SET_FLAGS = Block.UPDATE_CLIENTS
            | Block.UPDATE_KNOWN_SHAPE;

    private ThaevenLoreWorldManager() {
    }

    public static void tick(ServerLevel level) {
        if (level.dimension() != ServerLevel.OVERWORLD
                || level.getGameTime() % 20L != 0L) {
            return;
        }
        ThaevenLoreSavedData lore = ThaevenLoreSavedData.get(level.getServer());
        lore.ensureHeartScarAnchor(level.getServer());
        ReturnedHearthSavedData hearthData =
                ReturnedHearthSavedData.get(level.getServer());
        for (ReturnedHearthSavedData.HearthRecord hearth : hearthData.hearths()) {
            if (!hearth.structurePlaced()
                    || hearth.stage().ordinal()
                    < ReturnedHearthSavedData.HearthStage.FORMED.ordinal()
                    || !level.isLoaded(hearth.center())) {
                continue;
            }
            reconcileRelic(level, lore, hearth);
            if (hearth.type() == HearthSelectionPolicy.HearthType.MAJOR) {
                reconcileMemoryWall(level, lore, hearth);
                if (level.getGameTime() % 100L == 0L) {
                    reconcileHumanCarrier(level, lore, hearth);
                }
            }
        }
        if (hearthData.maeveErased()) {
            lore.heartScarAnchor().ifPresent(anchor -> {
                if (anchor.dimension() == level.dimension()) {
                    reconcileUnthreading(level, lore, anchor.pos());
                }
            });
        }
        reconcileCarrierVisibility(level, lore);
    }

    /** Prevents Bloom and atmospheric deposits from swallowing shared records. */
    public static boolean protectsCarrier(ServerLevel level, BlockPos pos) {
        ThaevenLoreSavedData lore = ThaevenLoreSavedData.get(level.getServer());
        return lore.firstCrossingVesselPos()
                .filter(carrier -> isInsideClearance(carrier, pos)).isPresent()
                || lore.unthreadingVesselPos()
                .filter(carrier -> isInsideClearance(carrier, pos)).isPresent()
                || lore.velAnRelicPositions().stream()
                .anyMatch(carrier -> isInsideClearance(carrier, pos));
    }

    private static void reconcileCarrierVisibility(
            ServerLevel level, ThaevenLoreSavedData lore) {
        if (level.getGameTime() % 100L == 0L) {
            lore.firstCrossingVesselPos().ifPresent(pos -> {
                clearGrowthAround(level, pos);
                BlockPos upper = pos.above();
                if (!level.getBlockState(upper).is(
                        ModBlocks.VEL_AN_MEMORY_WALL.get())
                        && canRepairThrough(level.getBlockState(upper))) {
                    level.setBlock(upper, ModBlocks.VEL_AN_MEMORY_WALL.get()
                            .defaultBlockState(), SET_FLAGS);
                }
            });
            lore.unthreadingVesselPos().ifPresent(
                    pos -> clearGrowthAround(level, pos));
            for (BlockPos pos : lore.velAnRelicPositions()) {
                clearGrowthAround(level, pos);
            }
        }
        if (level.getGameTime() % 20L == 0L) {
            lore.firstCrossingVesselPos().ifPresent(
                    pos -> emitCarrierMarker(level, pos, 2.25D));
            for (BlockPos pos : lore.velAnRelicPositions()) {
                emitCarrierMarker(level, pos, 1.25D);
            }
        }
    }

    private static void clearGrowthAround(ServerLevel level, BlockPos center) {
        if (!level.isLoaded(center)) return;
        for (int dx = -CARRIER_CLEARANCE_RADIUS;
             dx <= CARRIER_CLEARANCE_RADIUS; dx++) {
            for (int dz = -CARRIER_CLEARANCE_RADIUS;
                 dz <= CARRIER_CLEARANCE_RADIUS; dz++) {
                for (int dy = 0; dy <= CARRIER_CLEARANCE_HEIGHT; dy++) {
                    BlockPos check = center.offset(dx, dy, dz);
                    if (!level.isLoaded(check)) continue;
                    if (isObscuringGrowth(level.getBlockState(check))) {
                        level.removeBlock(check, false);
                    }
                }
            }
        }
    }

    private static boolean isInsideClearance(BlockPos carrier, BlockPos pos) {
        return Math.abs(carrier.getX() - pos.getX())
                <= CARRIER_CLEARANCE_RADIUS
                && Math.abs(carrier.getZ() - pos.getZ())
                <= CARRIER_CLEARANCE_RADIUS
                && pos.getY() >= carrier.getY()
                && pos.getY() <= carrier.getY() + CARRIER_CLEARANCE_HEIGHT;
    }

    private static boolean canRepairThrough(
            net.minecraft.world.level.block.state.BlockState state) {
        return state.isAir() || isObscuringGrowth(state)
                || state.is(net.minecraft.world.level.block.Blocks.SNOW)
                || state.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK);
    }

    private static boolean isObscuringGrowth(
            net.minecraft.world.level.block.state.BlockState state) {
        return state.is(ModBlocks.BLOOM_MASS.get())
                || state.is(ModBlocks.BLOOM_CRUST.get())
                || state.is(ModBlocks.BLOOM_TIP.get())
                || state.is(ModBlocks.BLOOM_CORE.get())
                || state.is(ModBlocks.FROZEN_ATMOSPHERE.get());
    }

    private static void emitCarrierMarker(
            ServerLevel level, BlockPos pos, double height) {
        if (!level.isLoaded(pos)) return;
        level.sendParticles(ParticleTypes.GLOW,
                pos.getX() + 0.5D, pos.getY() + height,
                pos.getZ() + 0.5D, 2,
                0.22D, 0.35D, 0.22D, 0.005D);
    }

    private static void reconcileRelic(
            ServerLevel level, ThaevenLoreSavedData lore,
            ReturnedHearthSavedData.HearthRecord hearth) {
        Optional<BlockPos> bound = lore.velAnRelic(hearth.id());
        if (bound.isPresent()) {
            BlockPos existing = bound.get();
            if (!level.isLoaded(existing)) {
                return;
            }
            if (level.getBlockState(existing).is(ModBlocks.VEL_AN_RELIC.get())) {
                return;
            }
            clearGrowthAround(level, existing);
            lore.clearVelAnRelic(hearth.id());
            if (placeIfSafe(level, existing, ModBlocks.VEL_AN_RELIC.get()
                    .defaultBlockState())) {
                lore.bindVelAnRelic(hearth.id(), existing);
                return;
            }
        }
        findSurface(level, hearth.center(), 4, 11).ifPresent(pos -> {
            if (placeIfSafe(level, pos, ModBlocks.VEL_AN_RELIC.get()
                    .defaultBlockState())) {
                lore.bindVelAnRelic(hearth.id(), pos);
            }
        });
    }

    private static void reconcileMemoryWall(
            ServerLevel level, ThaevenLoreSavedData lore,
            ReturnedHearthSavedData.HearthRecord hearth) {
        if (lore.firstCrossingVesselPos().isPresent()) {
            return;
        }
        findSurface(level, hearth.center(), 8, 16).ifPresent(pos -> {
            if (isSafeAir(level, pos) && isSafeAir(level, pos.above())
                    && placeIfSafe(level, pos,
                    ModBlocks.VEL_AN_MEMORY_WALL.get().defaultBlockState())) {
                boolean placedTop = level.setBlock(pos.above(),
                        ModBlocks.VEL_AN_MEMORY_WALL.get().defaultBlockState(),
                        SET_FLAGS);
                if (placedTop && level.getBlockState(pos.above()).is(
                        ModBlocks.VEL_AN_MEMORY_WALL.get())) {
                    lore.setFirstCrossingVesselPos(pos);
                } else if (level.getBlockState(pos).is(
                        ModBlocks.VEL_AN_MEMORY_WALL.get())) {
                    level.removeBlock(pos, false);
                }
            }
        });
    }

    private static void reconcileUnthreading(
            ServerLevel level, ThaevenLoreSavedData lore, BlockPos anchor) {
        BlockPos existing = lore.unthreadingVesselPos().orElse(null);
        if (existing != null) {
            if (!level.isLoaded(existing)) return;
            BlockPos crossing = lore.firstCrossingVesselPos().orElse(null);
            boolean tooClose = crossing != null
                    && existing.distSqr(crossing) < VESSEL_SEPARATION_SQR;
            boolean missing = !level.getBlockState(existing).is(
                    ModBlocks.UNTHREADING_VESSEL.get());
            if (!tooClose && !missing) return;
            if (!missing) level.removeBlock(existing, false);
            lore.clearUnthreadingVesselPos();
        }
        if (!level.isLoaded(anchor)) {
            return;
        }
        findUnthreadingSite(level, lore, anchor).ifPresent(pos -> {
            clearGrowthAround(level, pos);
            if (level.getBlockState(pos).is(
                    net.minecraft.world.level.block.Blocks.SNOW)
                    || level.getBlockState(pos).is(
                    net.minecraft.world.level.block.Blocks.SNOW_BLOCK)) {
                level.removeBlock(pos, false);
            }
            if (placeIfSafe(level, pos,
                    ModBlocks.UNTHREADING_VESSEL.get().defaultBlockState())) {
                lore.setUnthreadingVesselPos(pos);
                level.playSound(null, pos, SoundEvents.SCULK_CATALYST_BLOOM,
                        SoundSource.BLOCKS, 1.1F, 0.62F);
            }
        });
    }

    private static Optional<BlockPos> findUnthreadingSite(
            ServerLevel level, ThaevenLoreSavedData lore, BlockPos anchor) {
        long seed = anchor.asLong() ^ 0x554E544852454144L;
        BlockPos crossing = lore.firstCrossingVesselPos().orElse(null);
        PlayerPlacedBlockTracker tracker = PlayerPlacedBlockTracker.get(
                level.getServer());
        for (int attempt = 0; attempt < 64; attempt++) {
            int radius = UNTHREADING_MIN_RADIUS + Math.floorMod(
                    (int) (seed >> (attempt & 31)),
                    UNTHREADING_MAX_RADIUS - UNTHREADING_MIN_RADIUS + 1);
            double angle = attempt * 2.399963229728653D
                    + (seed & 1023L) / 1023.0D;
            int x = anchor.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = anchor.getZ() + (int) Math.round(Math.sin(angle) * radius);
            int top = level.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            for (int y = top; y >= Math.max(level.getMinBuildHeight(),
                    top - 40); y--) {
                BlockPos support = new BlockPos(x, y, z);
                if (!level.isLoaded(support)) break;
                net.minecraft.world.level.block.state.BlockState state =
                        level.getBlockState(support);
                if (state.isAir() || isObscuringGrowth(state)
                        || state.is(net.minecraft.world.level.block.Blocks.SNOW)
                        || state.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK)) {
                    continue;
                }
                BlockPos place = support.above();
                if (crossing != null
                        && place.distSqr(crossing) < VESSEL_SEPARATION_SQR) {
                    break;
                }
                if (!state.isCollisionShapeFullBlock(level, support)
                        || tracker.isPlayerPlaced(support)
                        || tracker.isPlayerPlaced(place)
                        || !canRepairThrough(level.getBlockState(place))) {
                    break;
                }
                return Optional.of(place.immutable());
            }
        }
        return Optional.empty();
    }

    private static void reconcileHumanCarrier(
            ServerLevel level, ThaevenLoreSavedData lore,
            ReturnedHearthSavedData.HearthRecord hearth) {
        if (lore.humanCarrierCratePos().isPresent()) {
            return;
        }
        BlockPos center = hearth.center();
        List<CrateInventory> crates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-20, -5, -20),
                center.offset(20, 8, 20))) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (!level.getBlockState(pos).is(ModBlocks.ORSA_SUPPLY_CRATE.get())) {
                continue;
            }
            BlockEntity entity = level.getBlockEntity(pos);
            if (!(entity instanceof Container container)) {
                continue;
            }
            crates.add(new CrateInventory(pos.immutable(), container, entity));
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                if (container.getItem(slot).is(ModItems.HUMAN_CARRIER.get())) {
                    lore.markHumanCarrierReconciled(pos);
                    return;
                }
            }
        }
        if (crates.isEmpty()) {
            return;
        }
        CrateInventory target = crates.get(0);
        int targetSlot = -1;
        for (int slot = 0; slot < target.container().getContainerSize(); slot++) {
            if (target.container().getItem(slot).isEmpty()) {
                targetSlot = slot;
                break;
            }
        }
        if (targetSlot < 0) {
            targetSlot = target.container().getContainerSize() - 1;
            ItemStack displaced = target.container().removeItemNoUpdate(
                    targetSlot);
            if (!displaced.isEmpty()) {
                Block.popResource(level, target.pos().above(), displaced);
            }
        }
        target.container().setItem(targetSlot,
                new ItemStack(ModItems.HUMAN_CARRIER.get()));
        target.entity().setChanged();
        lore.markHumanCarrierReconciled(target.pos());
    }

    private record CrateInventory(
            BlockPos pos, Container container, BlockEntity entity) {
    }

    private static Optional<BlockPos> findSurface(
            ServerLevel level, BlockPos center, int minRadius, int maxRadius) {
        long seed = center.asLong() ^ 0x5448414556454EL;
        for (int attempt = 0; attempt < 32; attempt++) {
            int radius = minRadius + Math.floorMod((int) (seed >> attempt),
                    Math.max(1, maxRadius - minRadius + 1));
            double angle = attempt * 2.399963229728653D;
            int x = center.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * radius);
            BlockPos pos = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, center.getY(), z));
            if (level.isLoaded(pos) && isSafeAir(level, pos)
                    && level.getBlockState(pos.below()).isCollisionShapeFullBlock(
                    level, pos.below())) {
                return Optional.of(pos.immutable());
            }
        }
        return Optional.empty();
    }

    private static boolean placeIfSafe(
            ServerLevel level, BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state) {
        if (!isSafeAir(level, pos)) {
            return false;
        }
        level.setBlock(pos, state, SET_FLAGS);
        return level.getBlockState(pos).is(state.getBlock());
    }

    private static boolean isSafeAir(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).isAir()
                && !PlayerPlacedBlockTracker.get(level.getServer())
                .isPlayerPlaced(pos);
    }
}

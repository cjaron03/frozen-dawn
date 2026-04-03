package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.OrsaStructureState;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Deferred placement of the guaranteed ORSA Blast Pit landmark.
 * Mirrors the safe placement pattern used by the satellite.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class BlastPitPlacement {

    private static final int OUTER_RADIUS = 20;
    private static final int CRATER_CLEAR_HEIGHT = 12;
    private static final int DRY_BUFFER = 12;
    private static volatile boolean pendingPlacement;

    private BlastPitPlacement() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != ServerLevel.OVERWORLD) {
            return;
        }

        OrsaStructureState state = OrsaStructureState.get(level.getServer());
        if (state.isBlastPitPlaced()) {
            return;
        }
        BlockPos target = state.getBlastPitTargetPos();
        if (target == null) {
            return;
        }
        if (event.getChunk().getPos().x == (target.getX() >> 4) && event.getChunk().getPos().z == (target.getZ() >> 4)) {
            pendingPlacement = true;
        }
    }

    public static void tickPlacement(ServerLevel overworld) {
        if (overworld.players().isEmpty()) {
            return;
        }
        OrsaStructureState state = OrsaStructureState.get(overworld.getServer());
        BlockPos targetPos = state.getBlastPitTargetPos();
        if (state.isBlastPitPlaced()) {
            if (targetPos != null) {
                BlastPitWarmZoneRegistry.register(overworld, targetPos);
                if (overworld.getGameTime() % 10L == 0L) {
                    meltColdDeposition(overworld, targetPos);
                }
            }
            return;
        }

        if (targetPos != null && isPlacementAreaLoaded(overworld, targetPos)) {
            pendingPlacement = true;
        }
        if (!pendingPlacement) {
            return;
        }
        if (targetPos == null) {
            return;
        }
        if (!isPlacementAreaLoaded(overworld, targetPos)) {
            return;
        }
        if (!isValidBlastPitPlacementSite(overworld, targetPos)) {
            rerollInvalidBlastPitTarget(overworld, state, targetPos, "overlaps Frozen Town exclusion");
            return;
        }
        placeStructure(overworld, targetPos);
        state.setBlastPitPos(targetPos);
        state.setBlastPitPlaced(true);
        pendingPlacement = false;
        BlastPitWarmZoneRegistry.register(overworld, targetPos);
        FrozenDawn.LOGGER.info("ORSA Blast Pit placed at ({}, {}, {})", targetPos.getX(), targetPos.getY(), targetPos.getZ());
    }

    private static boolean isValidBlastPitPlacementSite(ServerLevel level, BlockPos target) {
        return isPlacementAreaLoaded(level, target)
                && !FrozenTownPlacementGuard.overlapsFrozenTownExclusion(level, target, OUTER_RADIUS);
    }

    private static void rerollInvalidBlastPitTarget(ServerLevel level, OrsaStructureState state, BlockPos target, String reason) {
        FrozenDawn.LOGGER.warn("Blast Pit target at ({}, {}, {}) {}. Rerolling before placement.",
                target.getX(), target.getY(), target.getZ(), reason);
        pendingPlacement = false;
        BlastPitPlanner.reroll(level);
    }

    public static BlockPos ensureBlastPitResolved(ServerLevel overworld) {
        OrsaStructureState state = OrsaStructureState.get(overworld.getServer());
        return state.getBlastPitPos() != null ? state.getBlastPitPos() : state.getBlastPitTargetPos();
    }

    /**
     * First-pass blast pit: a crater with scorched center, partial perimeter, and a lore chest.
     * Warm-zone block integration will be layered onto the scorched terrain.
     */
    private static void placeStructure(ServerLevel level, BlockPos center) {
        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();

        for (int dx = -OUTER_RADIUS; dx <= OUTER_RADIUS; dx++) {
            for (int dz = -OUTER_RADIUS; dz <= OUTER_RADIUS; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > OUTER_RADIUS + 0.45) {
                    continue;
                }

                int floorY = cy;
                if (dist <= 4.5) {
                    floorY = cy - 5;
                } else if (dist <= 9.5) {
                    floorY = cy - 4;
                } else if (dist <= 14.0) {
                    floorY = cy - 3;
                } else if (dist <= 17.5) {
                    floorY = cy - 2;
                } else if (dist <= 19.5) {
                    floorY = cy - 1;
                }

                for (int y = cy + CRATER_CLEAR_HEIGHT; y >= floorY; y--) {
                    level.removeBlock(new BlockPos(cx + dx, y, cz + dz), false);
                }

                BlockPos floorPos = new BlockPos(cx + dx, floorY, cz + dz);
                BlockPos belowPos = floorPos.below();
                if (!level.getBlockState(belowPos).isSolid()) {
                    level.setBlock(belowPos, Blocks.DEEPSLATE.defaultBlockState(), 3);
                }

                level.setBlock(floorPos, floorStateFor(dist), 3);

                int rimHeight = rimHeightFor(dx, dz, dist);
                if (rimHeight > 0) {
                    int rimBaseY = Math.max(cy, floorY + 1);
                    for (int rim = 0; rim < rimHeight; rim++) {
                        level.setBlock(new BlockPos(cx + dx, rimBaseY + rim, cz + dz), rimStateFor(dist), 3);
                    }
                }
            }
        }

        placeInfrastructure(level, center.below(4));
        meltColdDeposition(level, center);
    }

    private static void placeInfrastructure(ServerLevel level, BlockPos center) {
        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();

        // Scorched service pad
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                level.setBlock(new BlockPos(cx + dx, cy, cz + dz), ModBlocks.SCORCHED_GROUND.get().defaultBlockState(), 3);
            }
        }

        // Launch mount / flame trench
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlock(new BlockPos(cx + dx, cy + 1, cz + dz), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 3);
            }
        }
        for (int dz = -5; dz <= 5; dz++) {
            level.setBlock(new BlockPos(cx, cy, cz + dz), Blocks.BLACKSTONE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(cx, cy - 1, cz + dz), ModBlocks.SCORCHED_GROUND.get().defaultBlockState(), 3);
        }
        for (int cornerX : new int[]{-3, 3}) {
            for (int cornerZ : new int[]{-3, 3}) {
                for (int y = 1; y <= 3; y++) {
                    level.setBlock(new BlockPos(cx + cornerX, cy + y, cz + cornerZ), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 3);
                }
            }
        }

        placeLaunchTower(level, center);

        level.setBlock(new BlockPos(cx - 6, cy + 1, cz + 3), Blocks.DAMAGED_ANVIL.defaultBlockState(), 3);
        level.setBlock(new BlockPos(cx + 6, cy + 1, cz - 3), Blocks.BLAST_FURNACE.defaultBlockState(), 3);
        level.setBlock(new BlockPos(cx + 4, cy + 1, cz + 5), Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.defaultBlockState(), 3);
        level.setBlock(new BlockPos(cx - 4, cy + 1, cz + 4), Blocks.REDSTONE_WIRE.defaultBlockState(), 3);
        level.setBlock(new BlockPos(cx - 2, cy + 1, cz - 4), Blocks.SMITHING_TABLE.defaultBlockState(), 3);

        // Lore / salvage supply crate
        BlockPos cratePos = new BlockPos(cx, cy + 1, cz - 2);
        BlockState crateState = ModBlocks.ORSA_SUPPLY_CRATE.get().defaultBlockState()
                .setValue(BarrelBlock.FACING, Direction.SOUTH)
                .setValue(BarrelBlock.OPEN, false);
        level.setBlock(cratePos, crateState, 3);
        if (level.getBlockEntity(cratePos) instanceof BarrelBlockEntity crate) {
            crate.setItem(4, createLaunchManifestDocument());
            crate.setItem(10, new ItemStack(Items.IRON_INGOT, 10));
            crate.setItem(12, new ItemStack(Items.COPPER_INGOT, 8));
            crate.setItem(14, new ItemStack(Items.REDSTONE, 8));
            crate.setItem(16, new ItemStack(Items.BLAZE_POWDER, 8));
            crate.setItem(22, new ItemStack(Items.OBSIDIAN, 6));
            crate.setItem(24, new ItemStack(ModItems.THERMAL_CORE.get(), 1));
        }
    }

    private static BlockState floorStateFor(double dist) {
        if (dist <= 7.0) {
            return ModBlocks.SCORCHED_GROUND.get().defaultBlockState();
        }
        if (dist <= 12.5) {
            return Blocks.BLACKSTONE.defaultBlockState();
        }
        return dist <= 17.5 ? Blocks.PACKED_MUD.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
    }

    private static BlockState rimStateFor(double dist) {
        return dist > 11.5 ? ModBlocks.SCORCHED_GROUND.get().defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
    }

    private static int rimHeightFor(int dx, int dz, double dist) {
        if (dist < 17.5 || dist > OUTER_RADIUS + 0.2) {
            return 0;
        }
        int hash = Math.floorMod(dx * 31 + dz * 17, 7);
        if (dist > 19.1) {
            return hash <= 2 ? 2 : 1;
        }
        return hash <= 2 ? 1 : 0;
    }

    private static void placeLaunchTower(ServerLevel level, BlockPos center) {
        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();

        int towerMinX = cx - 11;
        int towerMaxX = cx - 7;
        int towerMinZ = cz - 3;
        int towerMaxZ = cz + 3;
        int towerTop = cy + 20;

        clearAirspace(level, towerMinX - 2, towerTop + 2, towerMinZ - 2, cx + 4, cy + 1, towerMaxZ + 2);

        // Tower legs and spine
        for (int y = 1; y <= 20; y++) {
            level.setBlock(new BlockPos(towerMinX, cy + y, towerMinZ), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(towerMinX, cy + y, towerMaxZ), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(towerMaxX, cy + y, towerMinZ), Blocks.IRON_BARS.defaultBlockState(), 3);
            level.setBlock(new BlockPos(towerMaxX, cy + y, towerMaxZ), Blocks.IRON_BARS.defaultBlockState(), 3);
        }

        // Truss braces
        for (int y = 2; y <= 19; y += 3) {
            for (int z = towerMinZ; z <= towerMaxZ; z++) {
                level.setBlock(new BlockPos(towerMinX + 1, cy + y, z), Blocks.IRON_BARS.defaultBlockState(), 3);
                level.setBlock(new BlockPos(towerMinX + 2, cy + y + 1, z), Blocks.IRON_BARS.defaultBlockState(), 3);
                level.setBlock(new BlockPos(towerMinX + 3, cy + y + 2, z), Blocks.IRON_BARS.defaultBlockState(), 3);
            }
        }

        // Platform decks
        for (int platformY : new int[]{4, 9, 14, 18}) {
            for (int x = towerMinX; x <= towerMaxX; x++) {
                for (int z = towerMinZ; z <= towerMaxZ; z++) {
                    level.setBlock(new BlockPos(x, cy + platformY, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 3);
                }
            }
            for (int z = towerMinZ - 1; z <= towerMaxZ + 1; z++) {
                level.setBlock(new BlockPos(towerMaxX, cy + platformY + 1, z), Blocks.IRON_BARS.defaultBlockState(), 3);
            }
        }

        // Service arms reaching toward the launch mount
        placeServiceArm(level, new BlockPos(towerMaxX, cy + 5, cz), 7, 0);
        placeServiceArm(level, new BlockPos(towerMaxX, cy + 10, cz - 1), 8, 0);
        placeServiceArm(level, new BlockPos(towerMaxX, cy + 15, cz + 1), 6, 1);

        // Hammerhead crane / top boom
        for (int x = towerMinX - 1; x <= towerMinX + 2; x++) {
            level.setBlock(new BlockPos(x, towerTop + 1, cz), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 3);
        }
        for (int x = towerMaxX; x <= cx + 1; x++) {
            level.setBlock(new BlockPos(x, towerTop, cz - 2), Blocks.IRON_BARS.defaultBlockState(), 3);
        }
        level.setBlock(new BlockPos(cx + 1, towerTop + 1, cz - 2), Blocks.LIGHTNING_ROD.defaultBlockState(), 3);

        // Utility hut at tower base
        for (int x = towerMinX - 2; x <= towerMinX + 1; x++) {
            for (int z = towerMinZ; z <= towerMaxZ; z++) {
                level.setBlock(new BlockPos(x, cy + 1, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 3);
            }
        }
        for (int y = 2; y <= 4; y++) {
            for (int z = towerMinZ; z <= towerMaxZ; z++) {
                level.setBlock(new BlockPos(towerMinX - 2, cy + y, z), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 3);
                level.setBlock(new BlockPos(towerMinX + 1, cy + y, z), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 3);
            }
            for (int x = towerMinX - 2; x <= towerMinX + 1; x++) {
                level.setBlock(new BlockPos(x, cy + y, towerMinZ), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, cy + y, towerMaxZ), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 3);
            }
        }
        for (int x = towerMinX - 2; x <= towerMinX + 1; x++) {
            for (int z = towerMinZ; z <= towerMaxZ; z++) {
                level.setBlock(new BlockPos(x, cy + 5, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 3);
            }
        }
        level.setBlock(new BlockPos(towerMinX - 1, cy + 2, cz), Blocks.BLAST_FURNACE.defaultBlockState(), 3);
        level.setBlock(new BlockPos(towerMinX, cy + 2, cz + 1), Blocks.REDSTONE_WIRE.defaultBlockState(), 3);
    }

    private static void placeServiceArm(ServerLevel level, BlockPos start, int length, int zOffset) {
        for (int step = 0; step < length; step++) {
            BlockPos deck = start.offset(step, 0, zOffset);
            level.setBlock(deck, Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 3);
            if (step > 0 && step < length - 1) {
                level.setBlock(deck.above(), Blocks.IRON_BARS.defaultBlockState(), 3);
            }
        }
        BlockPos nose = start.offset(length - 1, 0, zOffset);
        level.setBlock(nose.below(), Blocks.CHAIN.defaultBlockState(), 3);
        level.setBlock(nose.below(2), Blocks.SOUL_LANTERN.defaultBlockState(), 3);
    }

    private static void meltColdDeposition(ServerLevel level, BlockPos center) {
        int radius = BlastPitWarmZoneRegistry.getRadius() + 2;
        int radiusSq = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((dx * dx) + (dz * dz) > radiusSq) {
                    continue;
                }

                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                int topY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;
                int bottomY = center.getY() - 8;
                for (int y = topY; y >= bottomY; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)
                            || state.is(Blocks.POWDER_SNOW) || state.is(ModBlocks.FROZEN_ATMOSPHERE.get())) {
                        level.destroyBlock(pos, false);
                        continue;
                    }
                    if (!state.isAir()) {
                        break;
                    }
                }
            }
        }
    }

    private static void clearAirspace(ServerLevel level, int minX, int maxY, int minZ, int maxX, int minY, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = maxY; y >= minY; y--) {
                    level.removeBlock(new BlockPos(x, y, z), false);
                }
            }
        }
    }

    private static boolean isPlacementAreaLoaded(ServerLevel level, BlockPos target) {
        int radius = OUTER_RADIUS + DRY_BUFFER + 8;
        int minX = target.getX() - radius;
        int maxX = target.getX() + radius;
        int minZ = target.getZ() - radius;
        int maxZ = target.getZ() + radius;

        for (int x = minX; x <= maxX; x += 16) {
            for (int z = minZ; z <= maxZ; z += 16) {
                if (!level.isLoaded(new BlockPos(x, level.getMinBuildHeight(), z))) {
                    return false;
                }
            }
        }

        return level.isLoaded(new BlockPos(maxX, level.getMinBuildHeight(), maxZ));
    }

    private static ItemStack createLaunchManifestDocument() {
        ItemStack doc = new ItemStack(ModItems.ORSA_DOCUMENT.get());
        doc.set(DataComponents.CUSTOM_NAME, Component.literal("ORSA Launch Manifest"));

        CompoundTag tag = new CompoundTag();
        tag.putString("doc_type", "launch_manifest");
        doc.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return doc;
    }
}

package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.TowerAntennaConsoleBlockEntity;
import com.frozendawn.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

public final class TowerStructureBuilder {

    private static final ResourceLocation TEMPLATE_ID =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "comm_tower");

    // The saved tower template is authored around the original tower anchor at local (9, 0, 9).
    private static final BlockPos TEMPLATE_CENTER_OFFSET = new BlockPos(9, 0, 9);
    private static final int MIN_X = -9;
    private static final int MAX_X = 20;
    private static final int MIN_Z = -9;
    private static final int MAX_Z = 9;
    private static final int APRON = 2;
    private static final int CLEAR_HEIGHT = 56;

    private TowerStructureBuilder() {
    }

    public static void place(ServerLevel level, BlockPos center, long towerId) {
        StructureTemplate template = level.getStructureManager().get(TEMPLATE_ID)
                .orElseThrow(() -> new IllegalStateException("Missing communication tower template: " + TEMPLATE_ID));

        BlockPos origin = center.subtract(TEMPLATE_CENTER_OFFSET);
        int clearHeight = Math.max(CLEAR_HEIGHT, template.getSize().getY() + 8);

        clearStructureVolume(level, center, clearHeight);
        clearEntities(level, structureBounds(center, clearHeight));
        prepareSite(level, center);

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setIgnoreEntities(false)
                .setKnownShape(true);
        RandomSource random = RandomSource.create(level.getSeed() ^ towerId ^ center.asLong());
        template.placeInWorld(level, origin, origin, settings, random, 2);

        bindTowerConsole(level, origin, template, settings, towerId);
    }

    private static void bindTowerConsole(ServerLevel level, BlockPos origin, StructureTemplate template,
                                         StructurePlaceSettings settings, long towerId) {
        boolean foundConsole = false;
        for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(origin, settings, ModBlocks.TOWER_ANTENNA_CONSOLE.get())) {
            if (level.getBlockEntity(info.pos()) instanceof TowerAntennaConsoleBlockEntity console) {
                console.setTowerId(towerId);
                console.setChanged();
                foundConsole = true;
            }
        }

        if (!foundConsole) {
            FrozenDawn.LOGGER.warn("Communication tower template placed without a tower console at ({}, {}, {})",
                    origin.getX(), origin.getY(), origin.getZ());
        }
    }

    private static void prepareSite(ServerLevel level, BlockPos center) {
        int minX = center.getX() + MIN_X - APRON;
        int maxX = center.getX() + MAX_X + APRON;
        int minZ = center.getZ() + MIN_Z - APRON;
        int maxZ = center.getZ() + MAX_Z + APRON;
        int baseY = center.getY();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int targetY = inMainFootprint(center, x, z) ? baseY : baseY - 1;
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

                for (int y = surfaceY; y > targetY; y--) {
                    BlockPos pos = new BlockPos(x, y - 1, z);
                    if (shouldClearForTower(level.getBlockState(pos))) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }

                for (int y = Math.min(surfaceY, targetY); y <= targetY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (y == targetY) {
                        level.setBlock(pos, inMainFootprint(center, x, z)
                                ? Blocks.POLISHED_DEEPSLATE.defaultBlockState()
                                : Blocks.COBBLED_DEEPSLATE.defaultBlockState(), 3);
                    } else {
                        level.setBlock(pos, Blocks.DEEPSLATE.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private static boolean inMainFootprint(BlockPos center, int x, int z) {
        return x >= center.getX() + MIN_X
                && x <= center.getX() + MAX_X
                && z >= center.getZ() + MIN_Z
                && z <= center.getZ() + MAX_Z;
    }

    private static void clearEntities(ServerLevel level, AABB box) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box)) {
            item.discard();
        }
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box)) {
            mob.discard();
        }
        for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, box)) {
            frame.discard();
        }
    }

    private static void clearStructureVolume(ServerLevel level, BlockPos center, int clearHeight) {
        int minX = center.getX() + MIN_X - APRON;
        int maxX = center.getX() + MAX_X + APRON;
        int minZ = center.getZ() + MIN_Z - APRON;
        int maxZ = center.getZ() + MAX_Z + APRON;
        int minY = center.getY();
        int maxY = center.getY() + clearHeight;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (shouldClearForTower(level.getBlockState(pos))) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private static AABB structureBounds(BlockPos center, int clearHeight) {
        return new AABB(
                center.getX() + MIN_X - APRON,
                center.getY() - 4,
                center.getZ() + MIN_Z - APRON,
                center.getX() + MAX_X + APRON + 1,
                center.getY() + clearHeight + 1,
                center.getZ() + MAX_Z + APRON + 1
        );
    }

    private static boolean shouldClearForTower(BlockState state) {
        return !state.isAir()
                && !state.is(Blocks.BEDROCK)
                && !state.is(Blocks.REINFORCED_DEEPSLATE)
                && !state.is(Blocks.END_PORTAL_FRAME)
                && !state.is(Blocks.END_PORTAL);
    }
}

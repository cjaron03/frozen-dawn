package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.ThermalHeaterBlock;
import com.frozendawn.block.ThermalHeaterBlockEntity;
import com.frozendawn.block.TowerAntennaConsoleBlock;
import com.frozendawn.block.TowerAntennaConsoleBlockEntity;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

public final class TowerStructureBuilder {

    private static final int HUB_HALF = 8;
    private static final int HUB_HEIGHT = 5;
    private static final int CONNECTOR_HALF_Z = 2;
    private static final int TOWER_OFFSET_X = HUB_HALF + 8;
    private static final int TOWER_HALF = 3;
    private static final int HATCH_HALF = 1;
    private static final int SCAFFOLD_HALF = 0;
    private static final int TOWER_HEIGHT = 28;
    private static final int PLATFORM_INTERVAL = 6;
    private static final int TOP_ROOM_HALF = 4;
    private static final int TOP_ROOM_HEIGHT = 4;
    private static final int APRON = 2;
    private static final int CLEAR_HEIGHT = 60;

    private TowerStructureBuilder() {
    }

    public static void place(ServerLevel level, BlockPos center, long towerId) {
        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();
        int tx = cx + TOWER_OFFSET_X;
        int antennaStartY = cy + TOWER_HEIGHT + TOP_ROOM_HEIGHT + 2;

        clearStructureVolume(level, cx, cy, cz, tx);
        clearEntities(level, structureBounds(cx, cy, cz, tx));
        prepareSite(level, cx, cy, cz, tx);
        clearEntities(level, structureBounds(cx, cy, cz, tx));

        buildOperationsHub(level, cx, cy, cz);
        buildConnector(level, cx, cy, cz, tx);
        buildTowerMast(level, cy, cz, tx);
        buildTopControlRoom(level, cy, cz, tx, towerId);
        buildAntenna(level, tx, cz, antennaStartY);

        placePenguinDisplay(level, new BlockPos(cx - HUB_HALF + 2, cy + 1, cz - HUB_HALF + 1));
        clearEntities(level, structureBounds(cx, cy, cz, tx));
    }

    private static void prepareSite(ServerLevel level, int cx, int cy, int cz, int tx) {
        int minX = siteMinX(cx);
        int maxX = siteMaxX(tx);
        int minZ = siteMinZ(cz);
        int maxZ = siteMaxZ(cz);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int targetY;
                if (inMainFootprint(x, z, cx, cz, tx)) {
                    targetY = cy;
                } else if (inApron(x, z, cx, cz, tx)) {
                    targetY = cy - 1;
                } else {
                    continue;
                }

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
                        level.setBlock(pos, inMainFootprint(x, z, cx, cz, tx)
                                ? Blocks.POLISHED_DEEPSLATE.defaultBlockState()
                                : Blocks.COBBLED_DEEPSLATE.defaultBlockState(), 3);
                    } else {
                        level.setBlock(pos, Blocks.DEEPSLATE.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private static void buildOperationsHub(ServerLevel level, int cx, int cy, int cz) {
        int minX = cx - HUB_HALF;
        int maxX = cx + HUB_HALF;
        int minZ = cz - HUB_HALF;
        int maxZ = cz + HUB_HALF;
        int roofY = cy + HUB_HEIGHT + 1;

        fillRect(level, minX, maxX, cy, minZ, maxZ, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        fillRect(level, minX, maxX, roofY, minZ, maxZ, Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());
        carveAir(level, minX + 1, maxX - 1, cy + 1, roofY - 1, minZ + 1, maxZ - 1);

        buildWallShell(level, minX, maxX, minZ, maxZ, cy + 1, cy + HUB_HEIGHT, true);
        carveWindow(level, cx - 3, cx + 3, cy + 2, cy + 4, minZ);
        carveWindow(level, cx - 3, cx + 3, cy + 2, cy + 4, maxZ);
        carveVerticalWindow(level, minX, cz - 3, cz + 3, cy + 2, cy + 4);

        carveAir(level, maxX, maxX, cy + 1, cy + 3, cz - 2, cz + 2);
        carveAir(level, cx - 1, cx, cy + 1, cy + 3, maxZ, maxZ);
        placeDoor(level, new BlockPos(cx - 1, cy + 1, maxZ), Direction.SOUTH, DoorHingeSide.LEFT);
        placeDoor(level, new BlockPos(cx, cy + 1, maxZ), Direction.SOUTH, DoorHingeSide.RIGHT);

        for (int x = cx - 2; x <= cx + 1; x++) {
            level.setBlock(new BlockPos(x, cy, maxZ + 1), Blocks.POLISHED_DEEPSLATE_SLAB.defaultBlockState(), 3);
        }

        placeCrate(level, new BlockPos(minX + 2, cy + 1, minZ + 2), Direction.SOUTH);
        placeCrate(level, new BlockPos(maxX - 2, cy + 1, maxZ - 2), Direction.WEST);
        fillPrimaryCrate(level, new BlockPos(minX + 2, cy + 1, minZ + 2));
        fillSecondaryCrate(level, new BlockPos(maxX - 2, cy + 1, maxZ - 2));

        placeLitHeater(level, new BlockPos(minX + 2, cy + 1, maxZ - 2), 48000);
        placeLitHeater(level, new BlockPos(maxX - 3, cy + 1, minZ + 2), 48000);
        level.setBlock(new BlockPos(maxX - 3, cy + 1, minZ + 4), Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
        level.setBlock(new BlockPos(maxX - 3, cy + 1, minZ + 6), Blocks.BLAST_FURNACE.defaultBlockState(), 3);
        level.setBlock(new BlockPos(maxX - 3, cy + 1, minZ + 8), Blocks.HOPPER.defaultBlockState(), 3);
    }

    private static void buildConnector(ServerLevel level, int cx, int cy, int cz, int tx) {
        int minX = cx + HUB_HALF + 1;
        int maxX = tx - TOWER_HALF - 1;
        int minZ = cz - CONNECTOR_HALF_Z;
        int maxZ = cz + CONNECTOR_HALF_Z;
        int roofY = cy + HUB_HEIGHT;

        fillRect(level, minX, maxX, cy, minZ, maxZ, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        carveAir(level, minX, maxX, cy + 1, roofY - 1, minZ + 1, maxZ - 1);

        for (int y = cy + 1; y <= roofY; y++) {
            for (int x = minX; x <= maxX; x++) {
                level.setBlock(new BlockPos(x, y, minZ), ModBlocks.INSULATED_GLASS.get().defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, y, maxZ), ModBlocks.INSULATED_GLASS.get().defaultBlockState(), 3);
            }
        }
        fillRect(level, minX, maxX, roofY, minZ, maxZ, Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());

        for (int x = minX; x <= maxX; x++) {
            if ((x - minX) % 2 == 0) {
                level.setBlock(new BlockPos(x, cy + 1, minZ + 1), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, cy + 1, maxZ - 1), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState(), 3);
            }
        }
    }

    private static void buildTowerMast(ServerLevel level, int cy, int cz, int tx) {
        int minX = tx - TOWER_HALF;
        int maxX = tx + TOWER_HALF;
        int minZ = cz - TOWER_HALF;
        int maxZ = cz + TOWER_HALF;
        int topY = cy + TOWER_HEIGHT;
        int frameTopY = topY + TOP_ROOM_HEIGHT + 4;

        fillRect(level, minX, maxX, cy, minZ, maxZ, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        carveAir(level, minX + 1, maxX - 1, cy + 1, frameTopY, minZ + 1, maxZ - 1);

        for (int y = cy + 1; y <= frameTopY; y++) {
            placeLeg(level, minX, y, minZ);
            placeLeg(level, minX, y, maxZ);
            placeLeg(level, maxX, y, minZ);
            placeLeg(level, maxX, y, maxZ);
        }

        for (int ringY = cy + 2; ringY <= frameTopY; ringY += PLATFORM_INTERVAL) {
            buildMastRing(level, tx, cz, ringY);
        }

        int scaffoldTopY = topY + TOP_ROOM_HEIGHT;
        for (int x = tx - SCAFFOLD_HALF; x <= tx + SCAFFOLD_HALF; x++) {
            for (int z = cz - SCAFFOLD_HALF; z <= cz + SCAFFOLD_HALF; z++) {
                for (int y = cy + 1; y <= scaffoldTopY; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.SCAFFOLDING.defaultBlockState(), 3);
                }
            }
        }

        for (int y = cy + 4; y < topY; y += PLATFORM_INTERVAL) {
            buildTowerPlatform(level, tx, cz, y);
        }
    }

    private static void buildTowerPlatform(ServerLevel level, int tx, int cz, int y) {
        int minX = tx - TOWER_HALF;
        int maxX = tx + TOWER_HALF;
        int minZ = cz - TOWER_HALF;
        int maxZ = cz + TOWER_HALF;

        for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                if (Math.abs(x - tx) <= HATCH_HALF && Math.abs(z - cz) <= HATCH_HALF) {
                    continue;
                }
                level.setBlock(new BlockPos(x, y, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 3);
            }
        }

        for (int x = minX; x <= maxX; x++) {
            level.setBlock(new BlockPos(x, y + 1, minZ), Blocks.IRON_BARS.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x, y + 1, maxZ), Blocks.IRON_BARS.defaultBlockState(), 3);
        }
        for (int z = minZ + 1; z <= maxZ - 1; z++) {
            level.setBlock(new BlockPos(minX, y + 1, z), Blocks.IRON_BARS.defaultBlockState(), 3);
            level.setBlock(new BlockPos(maxX, y + 1, z), Blocks.IRON_BARS.defaultBlockState(), 3);
        }
    }

    private static void buildTopControlRoom(ServerLevel level, int cy, int cz, int tx, long towerId) {
        int floorY = cy + TOWER_HEIGHT;
        int roofY = floorY + TOP_ROOM_HEIGHT + 1;
        int minX = tx - TOP_ROOM_HALF;
        int maxX = tx + TOP_ROOM_HALF;
        int minZ = cz - TOP_ROOM_HALF;
        int maxZ = cz + TOP_ROOM_HALF;

        buildRoomFloor(level, tx, cz, floorY, minX, maxX, minZ, maxZ);
        fillRect(level, minX, maxX, roofY, minZ, maxZ, Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());
        carveAir(level, tx - HATCH_HALF, tx + HATCH_HALF, floorY, roofY, cz - HATCH_HALF, cz + HATCH_HALF);
        carveAir(level, minX + 1, maxX - 1, floorY + 1, roofY - 1, minZ + 1, maxZ - 1);
        buildWallShell(level, minX, maxX, minZ, maxZ, floorY + 1, floorY + TOP_ROOM_HEIGHT, false);
        carveWindow(level, tx - 2, tx + 2, floorY + 2, floorY + 3, minZ);
        carveWindow(level, tx - 2, tx + 2, floorY + 2, floorY + 3, maxZ);
        carveVerticalWindow(level, minX, cz - 2, cz + 2, floorY + 2, floorY + 3);
        carveVerticalWindow(level, maxX, cz - 2, cz + 2, floorY + 2, floorY + 3);
        buildControlRoomSupports(level, tx, cz, floorY);

        BlockPos consoleBase = new BlockPos(tx + 2, floorY + 1, cz);
        level.setBlock(consoleBase.west(), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 3);
        level.setBlock(consoleBase.south(), Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState(), 3);
        level.setBlock(consoleBase, ModBlocks.TOWER_ANTENNA_CONSOLE.get().defaultBlockState()
                .setValue(TowerAntennaConsoleBlock.FACING, Direction.WEST), 3);
        if (level.getBlockEntity(consoleBase) instanceof TowerAntennaConsoleBlockEntity console) {
            console.setTowerId(towerId);
        }

        placeCrate(level, new BlockPos(tx - 3, floorY + 1, cz + 2), Direction.NORTH);
        fillSecondaryCrate(level, new BlockPos(tx - 3, floorY + 1, cz + 2));
        placeLitHeater(level, new BlockPos(tx - 3, floorY + 1, cz - 2), 36000);
    }

    private static void buildAntenna(ServerLevel level, int tx, int cz, int startY) {
        for (int x = tx - 2; x <= tx + 2; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                if (Math.abs(x - tx) == 2 || Math.abs(z - cz) == 2) {
                    level.setBlock(new BlockPos(x, startY - 1, z), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 3);
                }
            }
        }

        for (int x = tx - 3; x <= tx + 3; x++) {
            level.setBlock(new BlockPos(x, startY, cz - 3), Blocks.IRON_BARS.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x, startY, cz + 3), Blocks.IRON_BARS.defaultBlockState(), 3);
        }
        for (int z = cz - 2; z <= cz + 2; z++) {
            level.setBlock(new BlockPos(tx - 3, startY, z), Blocks.IRON_BARS.defaultBlockState(), 3);
            level.setBlock(new BlockPos(tx + 3, startY, z), Blocks.IRON_BARS.defaultBlockState(), 3);
        }

        for (int y = startY + 1; y <= startY + 12; y++) {
            level.setBlock(new BlockPos(tx, y, cz), Blocks.LIGHTNING_ROD.defaultBlockState(), 3);
        }
    }

    private static void buildWallShell(ServerLevel level, int minX, int maxX, int minZ, int maxZ, int minY, int maxY, boolean centerWindow) {
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                level.setBlock(new BlockPos(x, y, minZ), centerWindow && Math.abs(x - ((minX + maxX) / 2)) <= 3
                        ? ModBlocks.INSULATED_GLASS.get().defaultBlockState()
                        : Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, y, maxZ), centerWindow && Math.abs(x - ((minX + maxX) / 2)) <= 3
                        ? ModBlocks.INSULATED_GLASS.get().defaultBlockState()
                        : Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 3);
            }
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(minX, y, z), centerWindow && Math.abs(z - ((minZ + maxZ) / 2)) <= 3
                        ? ModBlocks.INSULATED_GLASS.get().defaultBlockState()
                        : Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 3);
                level.setBlock(new BlockPos(maxX, y, z), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 3);
            }
        }
    }

    private static void carveWindow(ServerLevel level, int minX, int maxX, int minY, int maxY, int z) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                level.setBlock(new BlockPos(x, y, z), ModBlocks.INSULATED_GLASS.get().defaultBlockState(), 3);
            }
        }
    }

    private static void carveVerticalWindow(ServerLevel level, int x, int minZ, int maxZ, int minY, int maxY) {
        for (int z = minZ; z <= maxZ; z++) {
            for (int y = minY; y <= maxY; y++) {
                level.setBlock(new BlockPos(x, y, z), ModBlocks.INSULATED_GLASS.get().defaultBlockState(), 3);
            }
        }
    }

    private static boolean inMainFootprint(int x, int z, int cx, int cz, int tx) {
        boolean hub = x >= cx - HUB_HALF - 1 && x <= cx + HUB_HALF + 1
                && z >= cz - HUB_HALF - 1 && z <= cz + HUB_HALF + 1;
        boolean connector = x >= cx + HUB_HALF + 1 && x <= tx - TOWER_HALF - 1
                && z >= cz - CONNECTOR_HALF_Z - 1 && z <= cz + CONNECTOR_HALF_Z + 1;
        boolean tower = x >= tx - TOWER_HALF - 1 && x <= tx + TOWER_HALF + 1
                && z >= cz - TOWER_HALF - 1 && z <= cz + TOWER_HALF + 1;
        return hub || connector || tower;
    }

    private static boolean inApron(int x, int z, int cx, int cz, int tx) {
        return x >= siteMinX(cx) && x <= siteMaxX(tx)
                && z >= siteMinZ(cz) && z <= siteMaxZ(cz);
    }

    private static AABB structureBounds(int cx, int cy, int cz, int tx) {
        return new AABB(cx - HUB_HALF - 6, cy - 4, cz - HUB_HALF - 6,
                tx + TOWER_HALF + 6, cy + CLEAR_HEIGHT, cz + HUB_HALF + 6);
    }

    private static void placePenguinDisplay(ServerLevel level, BlockPos displayBase) {
        BlockPos backingPos = displayBase.above().north();
        BlockPos framePos = backingPos.south();

        level.setBlock(displayBase, Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 3);
        level.setBlock(displayBase.south(), Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState(), 3);
        level.setBlock(backingPos, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), 3);
        level.setBlock(backingPos.above(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 3);
        level.setBlock(backingPos.west(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 3);
        level.setBlock(backingPos.east(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 3);
        level.setBlock(framePos, Blocks.AIR.defaultBlockState(), 3);

        AABB frameBox = new AABB(displayBase.getX() - 1, displayBase.getY(), displayBase.getZ() - 1,
                displayBase.getX() + 2, displayBase.getY() + 3, displayBase.getZ() + 2);
        for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, frameBox)) {
            frame.discard();
        }

        ItemFrame frame = new ItemFrame(level, framePos, Direction.SOUTH);
        frame.setItem(new ItemStack(ModItems.STUFFED_PENGUIN.get()), false);
        frame.setInvulnerable(true);
        frame.setCustomName(Component.literal("Employee of the Month"));
        frame.setCustomNameVisible(true);
        if (frame.survives()) {
            level.addFreshEntity(frame);
        } else {
            FrozenDawn.LOGGER.warn("Failed to place penguin display frame at {}", framePos);
        }
    }

    private static void fillRect(ServerLevel level, int minX, int maxX, int y, int minZ, int maxZ, BlockState state) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(x, y, z), state, 3);
            }
        }
    }

    private static void placeLeg(ServerLevel level, int x, int y, int z) {
        level.setBlock(new BlockPos(x, y, z), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 3);
    }

    private static void buildMastRing(ServerLevel level, int tx, int cz, int y) {
        int minX = tx - TOWER_HALF;
        int maxX = tx + TOWER_HALF;
        int minZ = cz - TOWER_HALF;
        int maxZ = cz + TOWER_HALF;

        for (int x = minX + 1; x < maxX; x++) {
            level.setBlock(new BlockPos(x, y, minZ), Blocks.IRON_BARS.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x, y, maxZ), Blocks.IRON_BARS.defaultBlockState(), 3);
        }
        for (int z = minZ + 1; z < maxZ; z++) {
            level.setBlock(new BlockPos(minX, y, z), Blocks.IRON_BARS.defaultBlockState(), 3);
            level.setBlock(new BlockPos(maxX, y, z), Blocks.IRON_BARS.defaultBlockState(), 3);
        }
    }

    private static void buildFaceBraces(ServerLevel level, int tx, int cz, int startY) {
        int minX = tx - TOWER_HALF;
        int maxX = tx + TOWER_HALF;
        int minZ = cz - TOWER_HALF;
        int maxZ = cz + TOWER_HALF;

        for (int step = 1; step <= 4; step++) {
            int y = startY + step;
            level.setBlock(new BlockPos(minX + step, y, minZ), Blocks.IRON_BARS.defaultBlockState(), 3);
            level.setBlock(new BlockPos(maxX - step, y, minZ), Blocks.IRON_BARS.defaultBlockState(), 3);
            level.setBlock(new BlockPos(minX + step, y, maxZ), Blocks.IRON_BARS.defaultBlockState(), 3);
            level.setBlock(new BlockPos(maxX - step, y, maxZ), Blocks.IRON_BARS.defaultBlockState(), 3);
            level.setBlock(new BlockPos(minX, y, minZ + step), Blocks.IRON_BARS.defaultBlockState(), 3);
            level.setBlock(new BlockPos(minX, y, maxZ - step), Blocks.IRON_BARS.defaultBlockState(), 3);
            level.setBlock(new BlockPos(maxX, y, minZ + step), Blocks.IRON_BARS.defaultBlockState(), 3);
            level.setBlock(new BlockPos(maxX, y, maxZ - step), Blocks.IRON_BARS.defaultBlockState(), 3);
        }
    }

    private static void buildRoomFloor(ServerLevel level, int tx, int cz, int y, int minX, int maxX, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (Math.abs(x - tx) <= HATCH_HALF && Math.abs(z - cz) <= HATCH_HALF) {
                    continue;
                }
                level.setBlock(new BlockPos(x, y, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 3);
            }
        }
    }

    private static void buildControlRoomSupports(ServerLevel level, int tx, int cz, int floorY) {
        int minX = tx - TOP_ROOM_HALF;
        int maxX = tx + TOP_ROOM_HALF;
        int minZ = cz - TOP_ROOM_HALF;
        int maxZ = cz + TOP_ROOM_HALF;
        int supportBaseY = floorY - 3;

        for (int y = supportBaseY; y < floorY; y++) {
            placeLeg(level, minX, y, minZ);
            placeLeg(level, minX, y, maxZ);
            placeLeg(level, maxX, y, minZ);
            placeLeg(level, maxX, y, maxZ);
        }

        for (int y = supportBaseY + 1; y < floorY; y++) {
            level.setBlock(new BlockPos(tx, y, minZ), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState(), 3);
            level.setBlock(new BlockPos(tx, y, maxZ), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState(), 3);
            level.setBlock(new BlockPos(minX, y, cz), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState(), 3);
            level.setBlock(new BlockPos(maxX, y, cz), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState(), 3);
        }

        level.setBlock(new BlockPos(minX + 1, floorY - 1, minZ + 1), Blocks.POLISHED_BLACKSTONE_STAIRS.defaultBlockState()
                .setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.SOUTH), 3);
        level.setBlock(new BlockPos(minX + 1, floorY - 1, maxZ - 1), Blocks.POLISHED_BLACKSTONE_STAIRS.defaultBlockState()
                .setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.NORTH), 3);
        level.setBlock(new BlockPos(maxX - 1, floorY - 1, minZ + 1), Blocks.POLISHED_BLACKSTONE_STAIRS.defaultBlockState()
                .setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.WEST), 3);
        level.setBlock(new BlockPos(maxX - 1, floorY - 1, maxZ - 1), Blocks.POLISHED_BLACKSTONE_STAIRS.defaultBlockState()
                .setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.WEST), 3);
    }

    private static void buildAntennaCrossarm(ServerLevel level, int tx, int cz, int y, int radius) {
        for (int offset = -radius; offset <= radius; offset++) {
            if (offset == 0) {
                continue;
            }
            level.setBlock(new BlockPos(tx + offset, y, cz), Blocks.IRON_BARS.defaultBlockState(), 3);
            level.setBlock(new BlockPos(tx, y, cz + offset), Blocks.IRON_BARS.defaultBlockState(), 3);
        }
    }

    private static void placeDoor(ServerLevel level, BlockPos pos, Direction facing, DoorHingeSide hinge) {
        BlockState lower = Blocks.IRON_DOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.FACING, facing)
                .setValue(net.minecraft.world.level.block.DoorBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(net.minecraft.world.level.block.DoorBlock.HINGE, hinge)
                .setValue(net.minecraft.world.level.block.DoorBlock.OPEN, false);
        BlockState upper = lower.setValue(net.minecraft.world.level.block.DoorBlock.HALF, DoubleBlockHalf.UPPER);
        level.setBlock(pos, lower, 3);
        level.setBlock(pos.above(), upper, 3);
    }

    private static void placeCrate(ServerLevel level, BlockPos pos, Direction facing) {
        level.setBlock(pos, ModBlocks.ORSA_SUPPLY_CRATE.get().defaultBlockState()
                .setValue(BarrelBlock.FACING, facing)
                .setValue(BarrelBlock.OPEN, false), 3);
    }

    private static void fillPrimaryCrate(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof BarrelBlockEntity crate)) {
            return;
        }
        crate.setItem(1, createDocument("orsa_bulletin", "ORSA Relay Maintenance Bulletin"));
        crate.setItem(4, new ItemStack(ModItems.ACHERONITE_SHARD.get(), 1));
        crate.setItem(10, new ItemStack(ModItems.REINFORCED_HELMET.get()));
        crate.setItem(12, new ItemStack(ModItems.FROZEN_MEAT.get(), 4));
        crate.setItem(14, new ItemStack(Items.COOKED_BEEF, 5));
        crate.setItem(19, new ItemStack(ModItems.ICE_SHARD.get(), 8));
        crate.setItem(22, new ItemStack(Items.REDSTONE, 10));
        crate.setItem(25, new ItemStack(Items.COPPER_INGOT, 8));
    }

    private static void fillSecondaryCrate(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof BarrelBlockEntity crate)) {
            return;
        }
        crate.setItem(2, new ItemStack(ModItems.INSULATED_BOOTS.get()));
        crate.setItem(6, new ItemStack(Items.COAL, 18));
        crate.setItem(11, new ItemStack(Items.LEATHER, 8));
        crate.setItem(15, new ItemStack(ModItems.THERMAL_CORE.get(), 1));
        crate.setItem(20, new ItemStack(Items.BREAD, 6));
        crate.setItem(24, new ItemStack(Items.IRON_INGOT, 12));
    }

    private static void placeLitHeater(ServerLevel level, BlockPos pos, int fuelTicks) {
        level.setBlock(pos, ModBlocks.THERMAL_HEATER.get().defaultBlockState()
                .setValue(ThermalHeaterBlock.LIT, false)
                .setValue(ThermalHeaterBlock.GLOW_STAGE, 0), 3);
        if (level.getBlockEntity(pos) instanceof ThermalHeaterBlockEntity heater) {
            heater.addFuel(fuelTicks);
        }
    }

    private static ItemStack createDocument(String docType, String name) {
        ItemStack stack = new ItemStack(ModItems.ORSA_DOCUMENT.get());
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        CompoundTag tag = new CompoundTag();
        tag.putString("doc_type", docType);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static void clearEntities(ServerLevel level, AABB box) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box)) {
            item.discard();
        }
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box)) {
            mob.discard();
        }
    }

    private static void clearStructureVolume(ServerLevel level, int cx, int cy, int cz, int tx) {
        int minX = siteMinX(cx);
        int maxX = siteMaxX(tx);
        int minZ = siteMinZ(cz);
        int maxZ = siteMaxZ(cz);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = cy; y <= cy + CLEAR_HEIGHT; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (shouldClearForTower(state)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private static boolean shouldClearForTower(BlockState state) {
        return !state.isAir()
                && !state.is(Blocks.BEDROCK)
                && !state.is(Blocks.REINFORCED_DEEPSLATE)
                && !state.is(Blocks.END_PORTAL_FRAME)
                && !state.is(Blocks.END_PORTAL);
    }

    private static void carveAir(ServerLevel level, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static int siteMinX(int cx) {
        return cx - HUB_HALF - APRON;
    }

    private static int siteMaxX(int tx) {
        return Math.max(tx + TOWER_HALF + APRON, tx + TOP_ROOM_HALF);
    }

    private static int siteMinZ(int cz) {
        return cz - HUB_HALF - APRON;
    }

    private static int siteMaxZ(int cz) {
        return cz + HUB_HALF + APRON;
    }
}

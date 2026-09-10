package com.frozendawn.debug.architect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Random obstacles around a guaranteed clear walking route, with replayable local coordinates. */
public final class ArchitectLabObstacleField {
    private ArchitectLabObstacleField() { }

    public static List<BlockPos> place(ServerLevel level, ArchitectLabFrame frame,
            ArchitectLabScenario scenario, long seed) {
        RandomSource random = RandomSource.create(seed);
        var route = new ArrayList<BlockPos>();
        var reserved = new HashSet<BlockPos>();
        int x = 3, z = 3;
        route.add(new BlockPos(x, 1, z));
        while (x < 17 || z < 17) {
            if (x < 17 && (z == 17 || random.nextBoolean())) x++; else z++;
            route.add(new BlockPos(x, 1, z));
        }
        // Leave a full cell beside the route: fence/wall connections cannot pinch it closed.
        for (BlockPos cell : route) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            reserved.add(cell.offset(dx, 0, dz));
        }
        for (x = 2; x <= 18; x++) for (z = 2; z <= 18; z++) {
            BlockPos pos = new BlockPos(x, 1, z);
            if (reserved.contains(pos) || random.nextFloat() > 0.65f) continue;
            BlockState block;
            if (scenario == ArchitectLabScenario.FIELD_FENCES) {
                block = random.nextBoolean() ? Blocks.OAK_FENCE.defaultBlockState() : Blocks.COBBLESTONE_WALL.defaultBlockState();
            } else if (scenario == ArchitectLabScenario.FIELD_SLABS) {
                block = Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,
                        random.nextBoolean() ? SlabType.BOTTOM : SlabType.TOP);
            } else {
                block = switch (random.nextInt(6)) {
                    case 0 -> Blocks.OAK_FENCE.defaultBlockState();
                    case 1 -> Blocks.COBBLESTONE_WALL.defaultBlockState();
                    case 2 -> Blocks.STONE_SLAB.defaultBlockState();
                    case 3 -> Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,
                            Direction.from2DDataValue(random.nextInt(4)));
                    case 4 -> Blocks.OAK_TRAPDOOR.defaultBlockState();
                    default -> Blocks.STONE.defaultBlockState();
                };
            }
            level.setBlockAndUpdate(frame.block(pos), block.rotate(frame.rotation()));
        }
        return List.copyOf(route);
    }
}

package com.frozendawn.world;

import com.frozendawn.block.HyperionControllerBlockEntity;
import com.frozendawn.block.HyperionFacingPartBlock;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.power.HyperionPhysics;
import com.frozendawn.power.PowerLinkRules;
import com.frozendawn.power.PowerNetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** The SMS-1 Hyperion playtest scene, all north of origin (the player's feet), within 10 blocks east or west and 20
 * north so it fits one GameTest template:
 * - an off, fuelled SFR-1 with no Battery Banks, its Power Port linked to the outdoor battery, which pays the ignition;
 * - a 3x3x6 outdoor silo with 2 Ports and 2 Cryocoolers, precooled and half charged;
 * - a 3x3x4 silo with 1 Port and 1 Cryocooler in a closed stone room warmed by a campfire, half charged, for the warm
 *   look and sound, with a glass cup against its east face where one lava source starts a heat warning.
 * Both Controllers face south toward the player; Ports and Cryocoolers face out of their sides, the Vents up. */
public final class HyperionLabPlacement {
    public record Lab(BlockPos outdoor, BlockPos heated, BlockPos sfrPowerPort, BlockPos lava, int failedLinks) {}
    private HyperionLabPlacement() {}

    public static Lab place(ServerLevel level, BlockPos origin) {
        // The torus's bottom ring stands in the ground, so its Hatch's lower half is at foot level, as sfrlab does it.
        var sfr = SfrLabPlacement.build(level, origin.offset(-5, 1, -8), false);
        var sfrPowerPort = origin.offset(-5, 1, -8).offset(2, 0, -4);

        var bigMin = origin.offset(1, 0, -15);
        var outdoor = silo(level, bigMin, 6);
        var port = bigMin.offset(0, 2, 1);
        fit(level, port, ModBlocks.HYPERION_PORT.get(), Direction.WEST);
        fit(level, bigMin.offset(2, 2, 1), ModBlocks.HYPERION_PORT.get(), Direction.EAST);
        fit(level, bigMin.offset(0, 0, 1), ModBlocks.CRYOCOOLER.get(), Direction.WEST);
        fit(level, bigMin.offset(2, 0, 1), ModBlocks.CRYOCOOLER.get(), Direction.EAST);

        // The heated room: stone brick walls and roof around a 5x5x6 inside, an iron door with buttons, glass to look in.
        var room = origin.offset(4, 0, -8);
        for (var p : BlockPos.betweenClosed(room, room.offset(6, 6, 6))) {
            int x = p.getX() - room.getX(), y = p.getY() - room.getY(), z = p.getZ() - room.getZ();
            boolean wall = x == 0 || x == 6 || z == 0 || z == 6 || y == 6;
            level.setBlock(p, (wall ? Blocks.STONE_BRICKS : Blocks.AIR).defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        for (int x : new int[]{1, 2, 4, 5}) for (int y = 1; y <= 3; y++) level.setBlock(room.offset(x, y, 6), ModBlocks.INSULATED_GLASS.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        var door = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        level.setBlock(room.offset(3, 0, 6), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        level.setBlock(room.offset(3, 1, 6), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        level.setBlock(room.offset(2, 1, 7), Blocks.STONE_BUTTON.defaultBlockState().setValue(net.minecraft.world.level.block.ButtonBlock.FACING, Direction.SOUTH), Block.UPDATE_CLIENTS);
        level.setBlock(room.offset(4, 1, 5), Blocks.STONE_BUTTON.defaultBlockState().setValue(net.minecraft.world.level.block.ButtonBlock.FACING, Direction.NORTH), Block.UPDATE_CLIENTS);
        level.setBlock(room.offset(1, 0, 1), Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_CLIENTS);

        var smallMin = room.offset(2, 0, 2);
        var heated = silo(level, smallMin, 4);
        fit(level, smallMin.offset(0, 1, 1), ModBlocks.HYPERION_PORT.get(), Direction.WEST);
        fit(level, smallMin.offset(0, 0, 1), ModBlocks.CRYOCOOLER.get(), Direction.WEST);
        // The lava cup: east of the east face's cell at y 1, walled so a source there stays put.
        var lava = smallMin.offset(3, 1, 1);
        for (var p : new BlockPos[]{lava.below(), lava.north(), lava.south()}) level.setBlock(p, Blocks.GLASS.defaultBlockState(), Block.UPDATE_CLIENTS);

        int failed = sfr.failedLinks();
        if (PowerNetworks.link(level, port, sfrPowerPort) != PowerLinkRules.Result.OK) failed++;
        charge(level, outdoor); charge(level, heated);
        return new Lab(outdoor, heated, sfrPowerPort, lava, failed);
    }

    /** A 3x3 silo of cells with its Controller at the bottom of the south-centre column and its Vent at top centre;
     * Ports and Cryocoolers replace cells after. Returns the Controller. */
    private static BlockPos silo(ServerLevel level, BlockPos min, int height) {
        for (var p : BlockPos.betweenClosed(min, min.offset(2, height - 1, 2))) level.setBlock(p, ModBlocks.SUPERCONDUCTING_CELL.get().defaultBlockState(), Block.UPDATE_ALL);
        var controller = min.offset(1, 0, 2);
        fit(level, controller, ModBlocks.HYPERION_CONTROLLER.get(), Direction.SOUTH);
        fit(level, min.offset(1, height - 1, 1), ModBlocks.QUENCH_VENT.get(), Direction.UP);
        return controller;
    }
    private static void fit(ServerLevel level, BlockPos pos, Block block, Direction facing) {
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(HyperionFacingPartBlock.FACING)) state = state.setValue(HyperionFacingPartBlock.FACING, facing);
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }
    /** Form now, start cold whatever the weather, and hold half the capacity. */
    private static void charge(ServerLevel level, BlockPos controller) {
        if (!(level.getBlockEntity(controller) instanceof HyperionControllerBlockEntity c)) return;
        c.second(level);
        if (!c.shell().valid()) return;
        c.physics().reset(HyperionPhysics.OPERATING_C);
        c.setStoredForTest(c.capacity() / 2);
    }
}

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
 * - a 5x5x5 outdoor battery with 2 Ports and 2 Cryocoolers, precooled and half charged, its Controller facing south;
 * - a 3x3x3 battery with 1 Cryocooler in a closed stone room warmed by a campfire, half charged, for the warm look and
 *   sound, with a glass cup against its east face where one lava source starts a heat warning.
 * Facing fittings face out of their box: the Controller and Ports south toward the player, the Vents up. */
public final class HyperionLabPlacement {
    public record Lab(BlockPos outdoor, BlockPos heated, BlockPos sfrPowerPort, BlockPos lava, int failedLinks) {}
    private HyperionLabPlacement() {}

    public static Lab place(ServerLevel level, BlockPos origin) {
        // The torus's bottom ring stands in the ground, so its Hatch's lower half is at foot level, as sfrlab does it.
        var sfr = SfrLabPlacement.build(level, origin.offset(-5, 1, -8), false);
        var sfrPowerPort = origin.offset(-5, 1, -8).offset(2, 0, -4);

        var bigMin = origin.offset(1, 0, -15);
        shell(level, bigMin, 5);
        var outdoor = bigMin.offset(2, 1, 4);
        fit(level, outdoor, ModBlocks.HYPERION_CONTROLLER.get(), Direction.SOUTH);
        var port = bigMin.offset(1, 0, 4);
        fit(level, port, ModBlocks.HYPERION_PORT.get(), Direction.SOUTH);
        fit(level, bigMin.offset(3, 0, 4), ModBlocks.HYPERION_PORT.get(), Direction.SOUTH);
        fit(level, bigMin.offset(1, 4, 4), ModBlocks.CRYOCOOLER.get(), Direction.UP);
        fit(level, bigMin.offset(3, 4, 4), ModBlocks.CRYOCOOLER.get(), Direction.UP);
        fit(level, bigMin.offset(0, 4, 2), ModBlocks.QUENCH_VENT.get(), Direction.UP);
        fit(level, bigMin.offset(4, 4, 2), ModBlocks.QUENCH_VENT.get(), Direction.UP);

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
        shell(level, smallMin, 3);
        var heated = smallMin.offset(1, 1, 2);
        fit(level, heated, ModBlocks.HYPERION_CONTROLLER.get(), Direction.SOUTH);
        fit(level, smallMin.offset(1, 0, 2), ModBlocks.HYPERION_PORT.get(), Direction.SOUTH);
        fit(level, smallMin.offset(1, 2, 2), ModBlocks.CRYOCOOLER.get(), Direction.UP);
        fit(level, smallMin.offset(0, 2, 1), ModBlocks.QUENCH_VENT.get(), Direction.UP);
        fit(level, smallMin.offset(2, 2, 1), ModBlocks.QUENCH_VENT.get(), Direction.UP);
        // The lava cup: east of the east face's center cell, walled so a source there stays put.
        var lava = smallMin.offset(3, 1, 1);
        for (var p : new BlockPos[]{lava.below(), lava.north(), lava.south()}) level.setBlock(p, Blocks.GLASS.defaultBlockState(), Block.UPDATE_CLIENTS);

        int failed = sfr.failedLinks();
        if (PowerNetworks.link(level, port, sfrPowerPort) != PowerLinkRules.Result.OK) failed++;
        charge(level, outdoor); charge(level, heated);
        return new Lab(outdoor, heated, sfrPowerPort, lava, failed);
    }

    /** A box of side size: Frame on edges and corners, cells elsewhere; fittings replace some after. */
    private static void shell(ServerLevel level, BlockPos min, int size) {
        for (var p : BlockPos.betweenClosed(min, min.offset(size - 1, size - 1, size - 1))) {
            int x = p.getX() - min.getX(), y = p.getY() - min.getY(), z = p.getZ() - min.getZ(), n = size - 1;
            int extremes = (x == 0 || x == n ? 1 : 0) + (y == 0 || y == n ? 1 : 0) + (z == 0 || z == n ? 1 : 0);
            level.setBlock(p, (extremes >= 2 ? ModBlocks.HYPERION_FRAME : ModBlocks.SUPERCONDUCTING_CELL).get().defaultBlockState(), Block.UPDATE_ALL);
        }
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

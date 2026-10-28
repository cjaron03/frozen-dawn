package com.frozendawn.world;

import com.frozendawn.block.PowerStorageBlockEntity;
import com.frozendawn.block.SfrControllerBlock;
import com.frozendawn.block.SfrControllerBlockEntity;
import com.frozendawn.block.SfrFuelPortBlockEntity;
import com.frozendawn.block.SfrMultiblock;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModItems;
import com.frozendawn.item.PowerCellItem;
import com.frozendawn.power.PowerLinkRules;
import com.frozendawn.power.PowerNetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** A complete, fuelled, pumped-down SFR-1 that is off, for playtests: Start is the first thing that happens to it.
 * The torus faces NORTH, with the Controller and the Fuel and Power Ports on the north side and the Hatch on the south. */
public final class SfrLabPlacement {
    public static final int BANKS = 4, RADIATORS = 14;
    private SfrLabPlacement() {}

    public record Lab(BlockPos controller, int failedLinks) {}

    /** Builds the torus around center, with radiators above it and charged banks to the north-west. */
    public static Lab build(ServerLevel level, BlockPos center) {
        for (int dx = -4; dx <= 4; dx++) for (int dy = -2; dy <= 2; dy++) for (int dz = -4; dz <= 4; dz++) {
            Block block = SfrMultiblock.expected(dx, dy, dz);
            if (block == null) block = SfrMultiblock.shell(dx, dy, dz);
            set(level, center.offset(dx, dy, dz), block == null ? Blocks.AIR.defaultBlockState() : block.defaultBlockState());
        }
        set(level, center.offset(-4, 0, 0), ModBlocks.COOLANT_PORT.get().defaultBlockState());
        set(level, center.offset(4, 0, 0), ModBlocks.COOLANT_PORT.get().defaultBlockState());
        set(level, center.offset(-2, 0, -4), ModBlocks.SFR_FUEL_PORT.get().defaultBlockState());
        set(level, center.offset(2, 0, -4), ModBlocks.SFR_POWER_PORT.get().defaultBlockState());
        set(level, center.offset(0, 0, 4), ModBlocks.SFR_VACUUM_PORT.get().defaultBlockState());
        var hatch = ModBlocks.SFR_MAINTENANCE_HATCH.get().defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        set(level, center.offset(2, -1, 4), hatch.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(level, center.offset(2, 0, 4), hatch.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        var controller = center.offset(0, 0, -4);
        set(level, controller, ModBlocks.SFR_CONTROLLER.get().defaultBlockState().setValue(SfrControllerBlock.FACING, Direction.NORTH));

        int failed = 0;
        var ports = new BlockPos[] { center.offset(-4, 0, 0), center.offset(4, 0, 0) };
        int n = 0;
        for (int dx = -4; dx <= 4 && n < RADIATORS; dx += 2) for (int dz = -4; dz <= 4 && n < RADIATORS; dz += 4) {
            var radiator = center.offset(dx, 4, dz);
            level.setBlock(radiator, ModBlocks.HIGH_TEMPERATURE_RADIATOR.get().defaultBlockState(), Block.UPDATE_ALL);
            if (PowerNetworks.link(level, ports[n++ % 2], radiator) != PowerLinkRules.Result.OK) failed++;
        }
        var powerPort = center.offset(2, 0, -4);
        for (int i = 0; i < BANKS; i++) {
            var bank = center.offset(-6 + i * 2, -1, -9);
            level.setBlock(bank, ModBlocks.BATTERY_BANK.get().defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(bank) instanceof PowerStorageBlockEntity store)
                for (int cell = 0; cell < store.getContainerSize(); cell++) store.setItem(cell, PowerCellItem.charged(PowerCellItem.CAPACITY_JOULES));
            if (PowerNetworks.link(level, bank, powerPort) != PowerLinkRules.Result.OK) failed++;
        }
        if (level.getBlockEntity(center.offset(-2, 0, -4)) instanceof SfrFuelPortBlockEntity fuel)
            fuel.setItem(0, new ItemStack(ModItems.DEUTERIUM_CANISTER.get(), 4));
        if (level.getBlockEntity(controller) instanceof SfrControllerBlockEntity be) { be.physics().addTritium(4); be.setChannelPressure(0); }
        return new Lab(controller, failed);
    }

    /** No neighbour or shape updates, so a door half never pops while its partner is placed. */
    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }
}

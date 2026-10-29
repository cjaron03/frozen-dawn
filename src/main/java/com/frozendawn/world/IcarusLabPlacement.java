package com.frozendawn.world;

import com.frozendawn.block.IcarusStructure;
import com.frozendawn.block.PowerStorageBlockEntity;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.item.PowerCellItem;
import com.frozendawn.power.PowerLinkRules;
import com.frozendawn.power.PowerNetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** A complete, cold, uncommissioned AMR-1 Icarus for playtests, with no capsules loaded: the player docks them and
 * presses Start. A block of charged banks beside the frame pays a commissioning start and a restart after the Cole
 * Protocol, and a chest by the Matter Port holds matter. */
public final class IcarusLabPlacement {
    /** A 6 x 6 x 4 block of banks at 2 MJ each: 288 MJ against the 260 MJ two starts draw at 1 MW. */
    public static final int BANKS_X = 6, BANKS_Z = 6, BANKS_Y = 4;
    private IcarusLabPlacement() {}

    public record Lab(BlockPos controller, BlockPos matterChest, int failedLinks) {}

    /** Builds the reactor around core with its Controller facing facing; the banks stand outside the frame on the
     * Controller's left, chained to one another and to the Power Port. */
    public static Lab build(ServerLevel level, BlockPos core, Direction facing) {
        var controller = IcarusStructure.place(level, core, facing);
        var powerPort = IcarusStructure.cell(core, facing, 1, IcarusStructure.BOTTOM + 1, IcarusStructure.HALF);
        int failed = 0;
        BlockPos previous = powerPort;
        for (int y = 0; y < BANKS_Y; y++) for (int z = 0; z < BANKS_Z; z++) for (int x = 0; x < BANKS_X; x++) {
            var bank = IcarusStructure.cell(core, facing, 10 + x, IcarusStructure.BOTTOM + 1 + y, 2 - z);
            level.setBlock(bank, ModBlocks.BATTERY_BANK.get().defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(bank) instanceof PowerStorageBlockEntity store)
                for (int cell = 0; cell < store.getContainerSize(); cell++) store.setItem(cell, PowerCellItem.charged(PowerCellItem.CAPACITY_JOULES));
            if (PowerNetworks.link(level, previous, bank) != PowerLinkRules.Result.OK) failed++;
            previous = bank;
        }
        var chest = IcarusStructure.cell(core, facing, -5, IcarusStructure.BOTTOM + 1, IcarusStructure.HALF + 2);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(chest) instanceof ChestBlockEntity box)
            for (int slot = 0; slot < 9; slot++) box.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        return new Lab(controller, chest, failed);
    }
}

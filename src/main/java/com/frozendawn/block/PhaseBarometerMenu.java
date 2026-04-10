package com.frozendawn.block;

import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class PhaseBarometerMenu extends AbstractContainerMenu {

    private final BlockPos barometerPos;

    public PhaseBarometerMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        super(ModMenuTypes.PHASE_BAROMETER.get(), containerId);
        this.barometerPos = buf.readBlockPos();
    }

    public PhaseBarometerMenu(int containerId, PhaseBarometerBlockEntity entity) {
        super(ModMenuTypes.PHASE_BAROMETER.get(), containerId);
        this.barometerPos = entity.getBlockPos();
    }

    public BlockPos getBarometerPos() {
        return barometerPos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(barometerPos).is(ModBlocks.PHASE_BAROMETER.get())
                && player.distanceToSqr(
                barometerPos.getX() + 0.5,
                barometerPos.getY() + 0.5,
                barometerPos.getZ() + 0.5
        ) <= 64.0;
    }
}

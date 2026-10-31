package com.frozendawn.block;

import com.frozendawn.init.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Read-only: the screen draws from the client copy of the Controller, synced once per second. No slots, no buttons. */
public final class HyperionControllerMenu extends AbstractContainerMenu {
    public static final int WIDTH = 230, HEIGHT = 150;
    private final BlockPos pos;
    public HyperionControllerMenu(int id, Inventory inv, FriendlyByteBuf buf) { this(id, buf.readBlockPos()); }
    public HyperionControllerMenu(int id, BlockPos pos) { super(ModMenuTypes.HYPERION_CONTROLLER.get(), id); this.pos = pos.immutable(); }
    public BlockPos pos() { return pos; }
    @Override public ItemStack quickMoveStack(Player p, int slot) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player p) {
        return !p.isSpectator() && p.level().getBlockEntity(pos) instanceof HyperionControllerBlockEntity && p.distanceToSqr(pos.getCenter()) <= 64;
    }
}

package com.frozendawn.block;

import com.frozendawn.init.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Input and output slots over the player inventory. The input takes only what the Column has a job for. */
public final class IsotopeColumnMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData data;
    private final @Nullable BlockPos pos;

    public IsotopeColumnMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, new SimpleContainer(2), new SimpleContainerData(IsotopeColumnBlockEntity.DATA), buf == null ? null : buf.readBlockPos());
    }
    public IsotopeColumnMenu(int id, Inventory inv, IsotopeColumnBlockEntity entity) { this(id, inv, entity, entity.data(), entity.getBlockPos()); }
    private IsotopeColumnMenu(int id, Inventory inv, Container container, ContainerData data, @Nullable BlockPos pos) {
        super(ModMenuTypes.ISOTOPE_COLUMN.get(), id);
        this.container = container; this.data = data; this.pos = pos;
        addSlot(new Slot(container, IsotopeColumnBlockEntity.INPUT, 44, 36) { @Override public boolean mayPlace(ItemStack s) { return IsotopeColumnBlockEntity.accepts(s); } });
        addSlot(new Slot(container, IsotopeColumnBlockEntity.OUTPUT, 116, 36) { @Override public boolean mayPlace(ItemStack s) { return false; } });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inv, 9 + row * 9 + col, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, 142));
        addDataSlots(data);
    }
    public ContainerData data() { return data; }
    public @Nullable BlockPos pos() { return pos; }
    @Override public boolean stillValid(Player p) { return container.stillValid(p); }
    @Override public ItemStack quickMoveStack(Player p, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        var s = slot.getItem(); var copy = s.copy();
        if (index < 2) { if (!moveItemStackTo(s, 2, slots.size(), true)) return ItemStack.EMPTY; }
        else if (IsotopeColumnBlockEntity.accepts(s)) { if (!moveItemStackTo(s, 0, 1, false)) return ItemStack.EMPTY; }
        else if (index < 29) { if (!moveItemStackTo(s, 29, 38, false)) return ItemStack.EMPTY; }
        else if (!moveItemStackTo(s, 2, 29, false)) return ItemStack.EMPTY;
        if (s.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(p, s); return copy;
    }
}

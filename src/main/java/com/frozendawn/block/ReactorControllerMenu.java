package com.frozendawn.block;

import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModMenuTypes;
import com.frozendawn.power.ReactorPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Four rod slots and one spent-rod output. Rod slots lock above 350C. Controls are menu buttons:
 * 0 SCRAM, 1 reset, 2 load-follow toggle, 3 enable auto-SCRAM, 4 disable auto-SCRAM (after both confirmations),
 * 100-200 manual insertion percent. */
public final class ReactorControllerMenu extends AbstractContainerMenu {
    public static final int SCRAM = 0, RESET = 1, LOAD_FOLLOW = 2, AUTO_ON = 3, AUTO_OFF = 4, RESERVE = 5, INSERTION = 100;
    public static final int WIDTH = 276, HEIGHT = 264, ROD_Y = 150, INV_Y = 182, INV_X = 57;
    private final Container container;
    private final ContainerData data;
    private final @Nullable ReactorControllerBlockEntity entity;
    private final @Nullable BlockPos pos;

    public ReactorControllerMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, new SimpleContainer(ReactorControllerBlockEntity.SLOTS), new SimpleContainerData(ReactorControllerBlockEntity.DATA), null, buf == null ? null : buf.readBlockPos());
    }
    public ReactorControllerMenu(int id, Inventory inv, ReactorControllerBlockEntity entity) {
        this(id, inv, new Wrapper(entity), entity.data(), entity, entity.getBlockPos());
    }
    private ReactorControllerMenu(int id, Inventory inv, Container container, ContainerData data, @Nullable ReactorControllerBlockEntity entity, @Nullable BlockPos pos) {
        super(ModMenuTypes.REACTOR_CONTROLLER.get(), id);
        this.container = container; this.data = data; this.entity = entity; this.pos = pos;
        for (int i = 0; i < ReactorPhysics.ROD_SLOTS; i++) addSlot(new RodSlot(container, i, 12 + i * 22, ROD_Y));
        addSlot(new OutputSlot(container, ReactorControllerBlockEntity.OUTPUT, 112, ROD_Y));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, INV_X + col * 18, INV_Y + 58));
        addDataSlots(data);
    }
    public ContainerData data() { return data; }
    public @Nullable BlockPos pos() { return pos; }
    public boolean flag(int bit) { return (data.get(ReactorControllerBlockEntity.FLAGS) & bit) != 0; }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (entity == null || player.isSpectator() || !stillValid(player)) return false;
        if (id >= INSERTION && id <= INSERTION + 100) { entity.setInsertion((id - INSERTION) / 100.0); return true; }
        switch (id) {
            case SCRAM -> { if (!entity.physics().scrammed() && player instanceof net.minecraft.server.level.ServerPlayer sp)
                    com.frozendawn.radiation.ReactorAdvancements.grant(sp, "az_5"); entity.manualScram(); }
            case RESET -> { return entity.reset(); }
            case LOAD_FOLLOW -> entity.toggleLoadFollow();
            case RESERVE -> entity.cycleReserve();
            case AUTO_ON -> entity.enableAutoScram();
            case AUTO_OFF -> entity.disableAutoScram(player);
            default -> { return false; }
        }
        return true;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        var slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        var stack = slot.getItem(); var result = stack.copy();
        int own = ReactorControllerBlockEntity.SLOTS;
        if (index < own) { if (!moveItemStackTo(stack, own, slots.size(), true)) return ItemStack.EMPTY; }
        else if (!moveItemStackTo(stack, 0, ReactorPhysics.ROD_SLOTS, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return result;
    }
    @Override public boolean stillValid(Player player) { return container.stillValid(player); }

    private final class RodSlot extends Slot {
        RodSlot(Container c, int i, int x, int y) { super(c, i, x, y); }
        @Override public boolean mayPlace(ItemStack s) { return s.is(ModItems.FUEL_ROD.get()) && !flag(ReactorControllerBlockEntity.F_LOCKED); }
        @Override public boolean mayPickup(Player p) { return !flag(ReactorControllerBlockEntity.F_LOCKED); }
        @Override public int getMaxStackSize() { return 1; }
    }
    private static final class OutputSlot extends Slot {
        OutputSlot(Container c, int i, int x, int y) { super(c, i, x, y); }
        /** Take-only for spent rods; a Shielded Cask can sit here to catch them. */
        @Override public boolean mayPlace(ItemStack s) { return s.is(ModItems.SHIELDED_CASK.get()) && !hasItem(); }
        @Override public int getMaxStackSize(ItemStack s) { return s.is(ModItems.SHIELDED_CASK.get()) ? 1 : super.getMaxStackSize(s); }
    }
    private record Wrapper(ReactorControllerBlockEntity entity) implements Container {
        @Override public int getContainerSize() { return ReactorControllerBlockEntity.SLOTS; }
        @Override public boolean isEmpty() { return entity.items().stream().allMatch(ItemStack::isEmpty); }
        @Override public ItemStack getItem(int i) { return entity.items().get(i); }
        @Override public ItemStack removeItem(int i, int n) { var r = net.minecraft.world.ContainerHelper.removeItem(entity.items(), i, n); if (!r.isEmpty()) entity.setChanged(); return r; }
        @Override public ItemStack removeItemNoUpdate(int i) { return net.minecraft.world.ContainerHelper.takeItem(entity.items(), i); }
        @Override public void setItem(int i, ItemStack s) { entity.items().set(i, s); entity.setChanged(); }
        @Override public void setChanged() { entity.setChanged(); }
        @Override public boolean stillValid(Player p) { return entity.stillValid(p); }
        @Override public void clearContent() { entity.items().clear(); }
    }
}

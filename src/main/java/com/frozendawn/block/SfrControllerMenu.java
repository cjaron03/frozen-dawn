package com.frozendawn.block;

import com.frozendawn.init.ModItems;
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

/** Tritium Canister input and output, and the Helium Canister slot. Controls are menu buttons: 0 Start, 1 Stop, 2 load-follow toggle, 3 Extract,
 * 4 and 5 disruption mitigation on and off, and DRIVE plus the drive steps above 100%. */
public final class SfrControllerMenu extends AbstractContainerMenu {
    public static final int START = 0, STOP = 1, LOAD_FOLLOW = 2, EXTRACT = 3, MITIGATION_ON = 4, MITIGATION_OFF = 5, ESTOP = 6, DRIVE = 10;
    public static final int DRIVE_STEPS = (int) Math.round((com.frozendawn.power.FusionPhysics.MAX_DRIVE - 1) / com.frozendawn.power.FusionPhysics.DRIVE_STEP);
    public static final int WIDTH = 256, HEIGHT = 250, SLOT_Y = 136, INV_Y = 168, INV_X = 47;
    private final Container container;
    private final ContainerData data;
    private final @Nullable SfrControllerBlockEntity entity;
    private final @Nullable BlockPos pos;

    public SfrControllerMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, new SimpleContainer(SfrControllerBlockEntity.SLOTS), new SimpleContainerData(SfrControllerBlockEntity.DATA), null, buf == null ? null : buf.readBlockPos());
    }
    public SfrControllerMenu(int id, Inventory inv, SfrControllerBlockEntity entity) {
        this(id, inv, new Wrapper(entity), entity.data(), entity, entity.getBlockPos());
    }
    private SfrControllerMenu(int id, Inventory inv, Container container, ContainerData data, @Nullable SfrControllerBlockEntity entity, @Nullable BlockPos pos) {
        super(ModMenuTypes.SFR_CONTROLLER.get(), id);
        this.container = container; this.data = data; this.entity = entity; this.pos = pos;
        addSlot(new TritiumSlot(container, SfrControllerBlockEntity.TRITIUM_IN, 12, SLOT_Y));
        addSlot(new TritiumSlot(container, SfrControllerBlockEntity.TRITIUM_OUT, 56, SLOT_Y));
        addSlot(new HeliumSlot(container, SfrControllerBlockEntity.HELIUM, 34, SLOT_Y));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, INV_X + col * 18, INV_Y + 58));
        addDataSlots(data);
    }
    public ContainerData data() { return data; }
    public @Nullable BlockPos pos() { return pos; }
    public boolean flag(int bit) { return (data.get(SfrControllerBlockEntity.FLAGS) & bit) != 0; }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (entity == null || player.isSpectator() || !stillValid(player)) return false;
        switch (id) {
            case START -> entity.start();
            case STOP -> entity.stop();
            case ESTOP -> entity.eStop();
            case LOAD_FOLLOW -> entity.toggleLoadFollow();
            case EXTRACT -> { return entity.extract(); }
            case MITIGATION_ON -> entity.setMitigation(true);
            case MITIGATION_OFF -> entity.setMitigation(false);
            default -> {
                if (id < DRIVE || id > DRIVE + DRIVE_STEPS) return false;
                entity.setDriveSteps(id - DRIVE);
            }
        }
        return true;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        var slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem(); var result = stack.copy();
        int own = SfrControllerBlockEntity.SLOTS;
        if (index < own) { if (!moveItemStackTo(stack, own, slots.size(), true)) return ItemStack.EMPTY; }
        else {
            int target = stack.is(ModItems.HELIUM_CANISTER.get()) ? SfrControllerBlockEntity.HELIUM : SfrControllerBlockEntity.TRITIUM_IN;
            if (!moveItemStackTo(stack, target, target + 1, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return result;
    }
    @Override public boolean stillValid(Player player) { return container.stillValid(player); }

    private static final class TritiumSlot extends Slot {
        TritiumSlot(Container c, int i, int x, int y) { super(c, i, x, y); }
        @Override public boolean mayPlace(ItemStack s) { return s.is(ModItems.TRITIUM_CANISTER.get()); }
    }
    private static final class HeliumSlot extends Slot {
        HeliumSlot(Container c, int i, int x, int y) { super(c, i, x, y); }
        @Override public boolean mayPlace(ItemStack s) { return s.is(ModItems.HELIUM_CANISTER.get()); }
    }
    private record Wrapper(SfrControllerBlockEntity entity) implements Container {
        @Override public int getContainerSize() { return SfrControllerBlockEntity.SLOTS; }
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

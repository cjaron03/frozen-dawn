package com.frozendawn.block;

import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModMenuTypes;
import com.frozendawn.item.O2TankItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class GeothermalCoreMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerData data;

    /** Client constructor (from network). */
    public GeothermalCoreMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, new SimpleContainer(4), new SimpleContainerData(3));
    }

    /** Server constructor. */
    public GeothermalCoreMenu(int containerId, Inventory playerInv, GeothermalCoreBlockEntity entity) {
        this(containerId, playerInv, new CoreContainer(entity), entity.getData());
    }

    private GeothermalCoreMenu(int containerId, Inventory playerInv, Container container, ContainerData data) {
        super(ModMenuTypes.GEOTHERMAL_CORE.get(), containerId);
        this.container = container;
        this.data = data;

        // 3 upgrade slots + 1 O2 tank refill slot
        addSlot(new UpgradeSlot(container, 0, 26, 22));   // Range
        addSlot(new UpgradeSlot(container, 1, 26, 46));   // Temp
        addSlot(new UpgradeSlot(container, 2, 26, 70));   // O2
        addSlot(new O2TankSlot(container, 3, 80, 94));    // O2 Tank Refill

        // Player inventory (3 rows) — shifted down for refill row
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 130 + row * 18));
            }
        }
        // Hotbar
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 188));
        }

        addDataSlots(data);
    }

    public ContainerData getData() {
        return data;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < 4) {
                // From container slots to player inventory
                if (!moveItemStackTo(stack, 4, 40, true)) return ItemStack.EMPTY;
            } else {
                // From player inventory to container slots
                if (!moveItemStackTo(stack, 0, 4, false)) return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    private static class UpgradeSlot extends Slot {
        public UpgradeSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return switch (getContainerSlot()) {
                case 0 -> stack.is(Items.OBSIDIAN) || stack.is(Items.DIAMOND_BLOCK);
                case 1 -> stack.is(Items.BLAZE_POWDER) || stack.is(ModItems.THERMAL_CORE.get());
                case 2 -> stack.is(Items.NETHER_STAR);
                default -> false;
            };
        }

        @Override
        public int getMaxStackSize() {
            return 64;
        }
    }

    private static class O2TankSlot extends Slot {
        public O2TankSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof O2TankItem;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /**
     * Wraps the block entity's item list as a Container for menu access.
     */
    private static class CoreContainer implements Container {
        private final GeothermalCoreBlockEntity entity;

        CoreContainer(GeothermalCoreBlockEntity entity) {
            this.entity = entity;
        }

        @Override public int getContainerSize() { return 4; }
        @Override public boolean isEmpty() { return entity.getItems().stream().allMatch(ItemStack::isEmpty); }
        @Override public ItemStack getItem(int slot) { return entity.getItems().get(slot); }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack result = ContainerHelper.removeItem(entity.getItems(), slot, amount);
            if (!result.isEmpty()) entity.setChanged();
            return result;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return ContainerHelper.takeItem(entity.getItems(), slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            entity.getItems().set(slot, stack);
            entity.setChanged();
        }

        @Override public void setChanged() { entity.setChanged(); }

        @Override
        public boolean stillValid(Player player) {
            return entity.getLevel() != null &&
                    player.distanceToSqr(entity.getBlockPos().getX() + 0.5,
                            entity.getBlockPos().getY() + 0.5,
                            entity.getBlockPos().getZ() + 0.5) <= 64.0;
        }

        @Override public void clearContent() { entity.getItems().clear(); }
    }
}

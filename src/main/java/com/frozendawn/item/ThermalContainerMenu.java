package com.frozendawn.item;

import com.frozendawn.init.ModMenuTypes;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;

public class ThermalContainerMenu extends AbstractContainerMenu {

    private final SimpleContainer container;
    private final int containerSlot;

    /** Client constructor (from network). */
    public ThermalContainerMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        super(ModMenuTypes.THERMAL_CONTAINER.get(), containerId);
        this.containerSlot = buf.readInt();
        this.container = new SimpleContainer(ThermalContainerItem.SLOTS);
        loadFromStack(getContainerStack(playerInv.player));
        setupSlots(playerInv);
    }

    /** Server constructor. */
    public ThermalContainerMenu(int containerId, Inventory playerInv, ItemStack containerStack, int containerSlot) {
        super(ModMenuTypes.THERMAL_CONTAINER.get(), containerId);
        this.containerSlot = containerSlot;
        this.container = new SimpleContainer(ThermalContainerItem.SLOTS);
        loadFromStack(containerStack);
        setupSlots(playerInv);
    }

    private void setupSlots(Inventory playerInv) {
        // 8 food-only slots in a centered row
        for (int i = 0; i < ThermalContainerItem.SLOTS; i++) {
            addSlot(new FoodSlot(container, i, 17 + i * 18, 20));
        }
        // Player inventory (3 rows)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 51 + row * 18));
            }
        }
        // Hotbar — lock the slot holding this container
        for (int col = 0; col < 9; col++) {
            if (col == containerSlot) {
                addSlot(new Slot(playerInv, col, 8 + col * 18, 109) {
                    @Override public boolean mayPickup(Player p) { return false; }
                    @Override public boolean mayPlace(ItemStack s) { return false; }
                });
            } else {
                addSlot(new Slot(playerInv, col, 8 + col * 18, 109));
            }
        }
    }

    private void loadFromStack(ItemStack stack) {
        if (stack == null) return;
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            NonNullList<ItemStack> items = NonNullList.withSize(ThermalContainerItem.SLOTS, ItemStack.EMPTY);
            contents.copyInto(items);
            for (int i = 0; i < ThermalContainerItem.SLOTS; i++) {
                container.setItem(i, items.get(i));
            }
        }
    }

    private ItemStack getContainerStack(Player player) {
        if (containerSlot == 40) return player.getOffhandItem();
        return player.getInventory().getItem(containerSlot);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) {
            ItemStack containerStack = getContainerStack(player);
            if (containerStack != null && containerStack.getItem() instanceof ThermalContainerItem) {
                saveToStack(containerStack);
            }
        }
    }

    private void saveToStack(ItemStack stack) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            items.add(container.getItem(i));
        }
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            int containerEnd = ThermalContainerItem.SLOTS;
            if (index < containerEnd) {
                // From container to player inventory
                if (!moveItemStackTo(stack, containerEnd, containerEnd + 36, true))
                    return ItemStack.EMPTY;
            } else {
                // From player to container — block nesting
                if (stack.getItem() instanceof ThermalContainerItem) return ItemStack.EMPTY;
                if (!moveItemStackTo(stack, 0, containerEnd, false))
                    return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        ItemStack stack = getContainerStack(player);
        return stack != null && stack.getItem() instanceof ThermalContainerItem;
    }

    /** Only allows food items (no thermal containers). */
    private static class FoodSlot extends Slot {
        public FoodSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.has(DataComponents.FOOD) && !(stack.getItem() instanceof ThermalContainerItem);
        }
    }
}

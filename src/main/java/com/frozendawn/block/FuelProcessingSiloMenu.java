package com.frozendawn.block;

import com.frozendawn.init.ModMenuTypes;
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

import java.util.List;

public class FuelProcessingSiloMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData data;

    public FuelProcessingSiloMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, new SimpleContainer(5), new SimpleContainerData(9));
    }

    public FuelProcessingSiloMenu(int containerId, Inventory playerInv, FuelProcessingSiloBlockEntity entity) {
        this(containerId, playerInv, new SiloContainer(entity), entity.getData());
    }

    private FuelProcessingSiloMenu(int containerId, Inventory playerInv, Container container, ContainerData data) {
        super(ModMenuTypes.FUEL_PROCESSING_SILO.get(), containerId);
        this.container = container;
        this.data = data;

        addSlot(new Slot(container, 0, 22, 27));
        addSlot(new Slot(container, 1, 40, 27));
        addSlot(new Slot(container, 2, 22, 45));
        addSlot(new Slot(container, 3, 40, 45));
        addSlot(new OutputSlot(container, FuelProcessingSiloBlockEntity.OUTPUT_SLOT, 137, 37));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 133 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 191));
        }

        addDataSlots(data);
    }

    public ContainerData getData() {
        return data;
    }

    public List<ItemStack> getInputStacks() {
        return List.of(
                slots.get(0).getItem(),
                slots.get(1).getItem(),
                slots.get(2).getItem(),
                slots.get(3).getItem()
        );
    }

    public ItemStack getOutputStack() {
        return slots.get(4).getItem();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        result = stack.copy();
        if (index < 5) {
            if (!moveItemStackTo(stack, 5, 41, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, 4, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    private static class OutputSlot extends Slot {
        public OutputSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    private static class SiloContainer implements Container {
        private final FuelProcessingSiloBlockEntity entity;

        SiloContainer(FuelProcessingSiloBlockEntity entity) {
            this.entity = entity;
        }

        @Override
        public int getContainerSize() {
            return entity.getItems().size();
        }

        @Override
        public boolean isEmpty() {
            return entity.getItems().stream().allMatch(ItemStack::isEmpty);
        }

        @Override
        public ItemStack getItem(int slot) {
            return entity.getItems().get(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack result = ContainerHelper.removeItem(entity.getItems(), slot, amount);
            if (!result.isEmpty()) {
                entity.setChanged();
            }
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

        @Override
        public void setChanged() {
            entity.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return entity.getLevel() != null && player.distanceToSqr(
                    entity.getBlockPos().getX() + 0.5D,
                    entity.getBlockPos().getY() + 0.5D,
                    entity.getBlockPos().getZ() + 0.5D
            ) <= 64.0D;
        }

        @Override
        public void clearContent() {
            entity.getItems().clear();
        }
    }
}

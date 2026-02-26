package com.frozendawn.block;

import com.frozendawn.init.ModItems;
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

/**
 * Menu for the Acheron Forge. 2 slots: input (shards) and output (refined).
 */
public class AcheronForgeMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerData data;

    /** Client constructor (from network). */
    public AcheronForgeMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, new SimpleContainer(2), new SimpleContainerData(3));
    }

    /** Server constructor. */
    public AcheronForgeMenu(int containerId, Inventory playerInv, AcheronForgeBlockEntity entity) {
        this(containerId, playerInv, new ForgeContainer(entity), entity.getData());
    }

    private AcheronForgeMenu(int containerId, Inventory playerInv, Container container, ContainerData data) {
        super(ModMenuTypes.ACHERON_FORGE.get(), containerId);
        this.container = container;
        this.data = data;

        // Input slot (shards only)
        addSlot(new ShardSlot(container, 0, 56, 35));
        // Output slot (extraction only)
        addSlot(new OutputSlot(container, 1, 116, 35));

        // Player inventory (3 rows)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // Hotbar
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }

        addDataSlots(data);
    }

    public ContainerData getData() { return data; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < 2) {
                // From forge to player
                if (!moveItemStackTo(stack, 2, 38, true)) return ItemStack.EMPTY;
            } else {
                // From player to forge input only
                if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
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

    private static class ShardSlot extends Slot {
        public ShardSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(ModItems.ACHERONITE_SHARD.get());
        }
    }

    private static class OutputSlot extends Slot {
        public OutputSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false; // output only
        }
    }

    private static class ForgeContainer implements Container {
        private final AcheronForgeBlockEntity entity;

        ForgeContainer(AcheronForgeBlockEntity entity) {
            this.entity = entity;
        }

        @Override public int getContainerSize() { return 2; }
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

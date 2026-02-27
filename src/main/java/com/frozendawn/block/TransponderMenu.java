package com.frozendawn.block;

import com.frozendawn.init.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for the ORSA Transponder. No inventory slots — status display only.
 * Syncs transponder state via ContainerData.
 * Button ID 0 = toggle pause/resume.
 *
 * Data indices:
 *   0 = state (0=IDLE, 1=BROADCASTING, 2=COMPLETE, 3=PAUSED)
 *   1 = broadcast progress percent (0-100)
 *   2 = minutes remaining
 *   3 = shaft clear (0/1)
 *   4 = core nearby (0/1)
 *   5 = depth ok (0/1)
 *   6 = schematic unlocked (0/1)
 */
public class TransponderMenu extends AbstractContainerMenu {

    private final ContainerData data;
    private final TransponderBlockEntity entity; // null on client

    /** Client constructor (from network). */
    public TransponderMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        super(ModMenuTypes.TRANSPONDER.get(), containerId);
        this.data = new SimpleContainerData(7);
        this.entity = null;
        addDataSlots(data);
    }

    /** Server constructor. */
    public TransponderMenu(int containerId, TransponderBlockEntity entity) {
        super(ModMenuTypes.TRANSPONDER.get(), containerId);
        this.data = entity.getMenuData();
        this.entity = entity;
        addDataSlots(data);
    }

    public ContainerData getData() { return data; }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0 && entity != null) {
            entity.togglePause();
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}

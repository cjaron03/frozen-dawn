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
 * Menu for Thermal Heaters. No inventory slots — status display only.
 * Syncs heater state via ContainerData.
 *
 * Data indices:
 *   0 = burn ETA minutes (at current consumption rate)
 *   1 = is lit (0/1)
 *   2 = sheltered (0/1)
 */
public class ThermalHeaterMenu extends AbstractContainerMenu {

    private final ContainerData data;

    /** Client constructor (from network). */
    public ThermalHeaterMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, new SimpleContainerData(3));
    }

    /** Server constructor. */
    public ThermalHeaterMenu(int containerId, ThermalHeaterBlockEntity entity) {
        this(containerId, entity.getMenuData());
    }

    private ThermalHeaterMenu(int containerId, ContainerData data) {
        super(ModMenuTypes.THERMAL_HEATER.get(), containerId);
        this.data = data;
        addDataSlots(data);
    }

    public ContainerData getData() { return data; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}

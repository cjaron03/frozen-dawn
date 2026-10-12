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
 *   0 = estimated burn ETA minutes (at averaged ordinary consumption rate)
 *   1 = is lit (0/1)
 *   2 = sheltered (0/1)
 *   3 = industrial drain active (0/1)
 *   4 = averaged ordinary burn percentage (display only)
 *   5 = redstone disabled (0/1)
 *   6 = control mode (open camp / air / walls)
 *   8 = selected target in Celsius (thermostat or default)
 *   7 = controlled temperature in tenths Celsius (-32768 = unavailable)
 */
public class ThermalHeaterMenu extends AbstractContainerMenu {

    private final ContainerData data;

    /** Client constructor (from network). */
    public ThermalHeaterMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, new SimpleContainerData(9));
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

package com.frozendawn.power;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Isotope Column numbers. Ice is counted in units (Ice and Snow Block 1, Packed Ice 9, Blue Ice 81), and 8 units
 * make one Deuterium Canister. The Column takes ice in only while the buffer is short of a canister. */
public final class IsotopeColumnLogic {
    public static final int DRAW_WATTS = 10000, ICE_PER_CANISTER = 8;
    public static final int DEUTERIUM_TICKS = 120 * 20, LITHIUM_TICKS = 60 * 20;
    public static final int MAX_BUFFER = ICE_PER_CANISTER * 81;

    public enum Job { NONE, DEUTERIUM, LITHIUM }

    private IsotopeColumnLogic() {}

    public static int iceUnits(Item item) {
        if (item == Items.ICE || item == Items.SNOW_BLOCK) return 1;
        if (item == Items.PACKED_ICE) return 9;
        if (item == Items.BLUE_ICE) return 81;
        return 0;
    }
    public static boolean pullIce(int buffer, int units) {
        return units > 0 && buffer < ICE_PER_CANISTER && buffer + units <= MAX_BUFFER;
    }
}

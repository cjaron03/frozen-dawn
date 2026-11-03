package com.frozendawn.power;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class IsotopeColumnLogicTest {
    @Test void iceUnitsCountPackedAndBlueIce() {
        assertEquals(1, IsotopeColumnLogic.iceUnits(Items.ICE));
        assertEquals(1, IsotopeColumnLogic.iceUnits(Items.SNOW_BLOCK));
        assertEquals(9, IsotopeColumnLogic.iceUnits(Items.PACKED_ICE));
        assertEquals(81, IsotopeColumnLogic.iceUnits(Items.BLUE_ICE));
        assertEquals(0, IsotopeColumnLogic.iceUnits(Items.SNOWBALL));
        assertEquals(0, IsotopeColumnLogic.iceUnits(Items.DIRT));
    }

    @Test void pullsIceOnlyWhenBufferIsShort() {
        assertTrue(IsotopeColumnLogic.pullIce(0, 81));
        assertTrue(IsotopeColumnLogic.pullIce(7, 1));
        assertFalse(IsotopeColumnLogic.pullIce(8, 1), "A full canister's worth is already buffered");
        assertFalse(IsotopeColumnLogic.pullIce(IsotopeColumnLogic.MAX_BUFFER - 80, 81), "Never overflows the buffer");
        assertEquals(648, IsotopeColumnLogic.MAX_BUFFER);
    }
}

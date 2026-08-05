package com.frozendawn.bloom;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BloomOriginRootPolicyTest {
    @Test
    void originRootIsDeterministicBoundedAndVisiblyDistinct() {
        List<BloomOriginRootPolicy.Placement> first = BloomOriginRootPolicy.layout(417L);
        List<BloomOriginRootPolicy.Placement> repeated = BloomOriginRootPolicy.layout(417L);
        List<BloomOriginRootPolicy.Placement> different = BloomOriginRootPolicy.layout(418L);

        assertEquals(first, repeated);
        assertNotEquals(first, different);
        assertTrue(first.size() >= 220);
        assertTrue(first.stream().allMatch(placement ->
                Math.abs(placement.offset().getX()) <= BloomOriginRootPolicy.MAX_RADIUS
                        && Math.abs(placement.offset().getZ())
                        <= BloomOriginRootPolicy.MAX_RADIUS
                        && placement.offset().getY() >= 0
                        && placement.offset().getY() <= BloomOriginRootPolicy.MAX_HEIGHT));
        assertEquals(3L, first.stream().filter(placement ->
                placement.material() == BloomOriginRootPolicy.Material.CORE).count());
        assertTrue(first.stream().filter(placement ->
                placement.material() == BloomOriginRootPolicy.Material.TIP).count() >= 8L);
        assertTrue(first.stream().anyMatch(placement ->
                Math.max(Math.abs(placement.offset().getX()),
                        Math.abs(placement.offset().getZ())) >= 10));
        assertTrue(first.stream().anyMatch(placement -> placement.offset().getY() >= 19));
        for (int index = 1; index < first.size(); index++) {
            assertTrue(first.get(index - 1).offset().getY()
                    <= first.get(index).offset().getY());
        }
    }

    @Test
    void formationWaitsForSeedAndReservesOnlyTheUpperIrregularCanopy() {
        assertFalse(BloomOriginRootPolicy.canForm(
                BloomOriginRootPolicy.FORMATION_DELAY_TICKS, false));
        assertFalse(BloomOriginRootPolicy.canForm(
                BloomOriginRootPolicy.FORMATION_DELAY_TICKS - 1, true));
        assertTrue(BloomOriginRootPolicy.canForm(
                BloomOriginRootPolicy.FORMATION_DELAY_TICKS, true));

        assertFalse(BloomOriginRootPolicy.reservesCrownClearance(
                91L, new net.minecraft.core.BlockPos(0, 8, 0)));
        assertTrue(BloomOriginRootPolicy.reservesCrownClearance(
                91L, new net.minecraft.core.BlockPos(0, 18, 0)));
        assertFalse(BloomOriginRootPolicy.reservesCrownClearance(
                91L, new net.minecraft.core.BlockPos(12, 18, 12)));
    }
}

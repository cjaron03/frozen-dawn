package com.frozendawn.bloom;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BloomSporePolicyTest {
    @Test
    void spawnRollAndEscapeThresholdsAreExact() {
        assertTrue(BloomSporePolicy.shouldSpawn(0.0D));
        assertTrue(BloomSporePolicy.shouldSpawn(0.024999D));
        assertFalse(BloomSporePolicy.shouldSpawn(0.025D));
        assertFalse(BloomSporePolicy.shouldSpawn(-0.1D));
        assertEquals(2, BloomSporePolicy.sourceActiveCap(false));
        assertEquals(1, BloomSporePolicy.sourceActiveCap(true));
        assertEquals(4, BloomSporePolicy.GLOBAL_ACTIVE_CAP);

        assertFalse(BloomSporePolicy.escaped(115.99D, 16.0D));
        assertTrue(BloomSporePolicy.escaped(116.0D, 16.0D));
        assertEquals(48, BloomSporePolicy.COLLAPSE_TICKS);
        assertEquals(10, BloomSporePolicy.COLLAPSE_IMPACT_TICKS);
        assertEquals(10, BloomSporePolicy.ROOT_SHOCK_TICKS);
        assertEquals(3, BloomSporePolicy.IMMEDIATE_ROOT_TIPS);
        assertEquals(12, BloomSporePolicy.ROOT_PATCH_INTERVAL);
        assertEquals(8, BloomSporePolicy.CORPSE_STRIKE_COOLDOWN_TICKS);
    }

    @Test
    void finitePatchMaturesOnlyAcrossOneLoadedDay() {
        assertEquals(0, BloomSporePolicy.desiredGrowthCursor(0L));
        assertEquals(BloomSporePolicy.SATELLITE_COLUMNS / 2,
                BloomSporePolicy.desiredGrowthCursor(
                        BloomSporePolicy.FORMATION_TICKS / 2L));
        assertEquals(BloomSporePolicy.SATELLITE_COLUMNS,
                BloomSporePolicy.desiredGrowthCursor(BloomSporePolicy.FORMATION_TICKS));
        assertEquals(BloomSporePolicy.SATELLITE_COLUMNS,
                BloomSporePolicy.desiredGrowthCursor(Long.MAX_VALUE));
        assertEquals(3L * BloomGrowthPolicy.DAY_TICKS, BloomSporePolicy.RELAY_TICKS);
    }

    @Test
    void columnPermutationVisitsEveryCellExactlyOnce() {
        Set<Integer> visited = new HashSet<>();
        for (int cursor = 0; cursor < BloomSporePolicy.SATELLITE_COLUMNS; cursor++) {
            int column = BloomSporePolicy.permutedColumn(cursor, 938_471L);
            assertTrue(column >= 0 && column < BloomSporePolicy.SATELLITE_COLUMNS);
            visited.add(column);
        }
        assertEquals(BloomSporePolicy.SATELLITE_COLUMNS, visited.size());
    }

    @Test
    void acceptedColumnsStayCircularSparseAndFourBlocksHigh() {
        long seed = 0x4F11A7E5L;
        int accepted = 0;
        for (int z = -BloomSporePolicy.SATELLITE_RADIUS;
             z <= BloomSporePolicy.SATELLITE_RADIUS; z++) {
            for (int x = -BloomSporePolicy.SATELLITE_RADIUS;
                 x <= BloomSporePolicy.SATELLITE_RADIUS; x++) {
                if (!BloomSporePolicy.acceptsColumn(seed, x, z)) {
                    continue;
                }
                accepted++;
                assertTrue(x * x + z * z <= BloomSporePolicy.SATELLITE_RADIUS
                        * BloomSporePolicy.SATELLITE_RADIUS);
                int height = BloomSporePolicy.columnHeight(seed, x, z);
                assertTrue(height >= 1 && height <= 4);
            }
        }
        // A deterministic sample should remain near the specified 20% surface coverage.
        assertTrue(accepted >= 125 && accepted <= 200, "accepted=" + accepted);
    }
}

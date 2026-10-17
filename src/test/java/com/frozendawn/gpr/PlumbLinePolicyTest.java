package com.frozendawn.gpr;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.gpr.PlumbLinePolicy.Medium;
import com.frozendawn.gpr.PlumbLinePolicy.Setting;
import com.frozendawn.gpr.PlumbLineScan.Kind;
import com.frozendawn.gpr.PlumbLineScan.Return;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class PlumbLinePolicyTest {
    @Test void settingsMatchSpec() {
        assertEquals(30_000, Setting.SURVEY.watts); assertEquals(48, Setting.SURVEY.radius);
        assertEquals(50_000, Setting.DEEP.watts); assertEquals(80, Setting.DEEP.radius);
        assertEquals(200, PlumbLinePolicy.PULSE_TICKS); assertEquals(1200, PlumbLinePolicy.COOLDOWN_TICKS);
        assertEquals(600, PlumbLinePolicy.OVERLAY_TICKS); assertEquals(16, PlumbLinePolicy.VIEW_RANGE);
        assertEquals(Setting.SURVEY, Setting.byId(42));
    }

    @Test void penetrationMatchesSpec() {
        double range = 48;
        assertEquals(48, range / PlumbLinePolicy.cost(Medium.STONE, false), 1e-9, "Stone is the baseline");
        assertEquals(36, range / PlumbLinePolicy.cost(Medium.DEEPSLATE, false), 1e-9, "Deepslate reaches 75%");
        assertEquals(72, range / PlumbLinePolicy.cost(Medium.ICE, false), 1e-9, "Ice and snow reach 150%");
        assertEquals(60, range / PlumbLinePolicy.cost(Medium.STONE, true), 1e-9, "Frozen ground adds 25%");
        assertTrue(Double.isInfinite(PlumbLinePolicy.cost(Medium.WATER, false)), "Water blocks the signal");
        assertTrue(Double.isInfinite(PlumbLinePolicy.cost(Medium.METAL, true)), "Metal reflects, even frozen");
    }

    @Test void blurGrowsWithDistanceRockAndDeep() {
        assertTrue(PlumbLinePolicy.pad(4, 0, Setting.SURVEY) >= 2 && PlumbLinePolicy.pad(4, 0, Setting.SURVEY) < 3, "Near returns pad 2-3 blocks");
        assertTrue(PlumbLinePolicy.pad(60, 0, Setting.SURVEY) > PlumbLinePolicy.pad(10, 0, Setting.SURVEY));
        assertTrue(PlumbLinePolicy.pad(20, 20, Setting.SURVEY) > PlumbLinePolicy.pad(20, 0, Setting.SURVEY));
        assertTrue(PlumbLinePolicy.pad(20, 0, Setting.DEEP) > PlumbLinePolicy.pad(20, 0, Setting.SURVEY));
    }

    @Test void raysAreUnitAndCoverBothHemispheres() {
        int up = 0, down = 0, n = 2000;
        for (int i = 0; i < n; i++) {
            var d = PlumbLinePolicy.direction(i, n);
            assertEquals(1, Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]), 1e-9);
            if (d[1] > 0) up++; else down++;
        }
        assertEquals(n / 2, up, 2); assertEquals(n / 2, down, 2);
    }

    @Test void triangulationNeedsABaseline() {
        assertFalse(PlumbLinePolicy.baseline(0, 0, 0, 5, 0, 5));
        assertTrue(PlumbLinePolicy.baseline(0, 0, 0, 8, 0, 0));
    }

    @Test void clustersSeparateTypesAndGaps() {
        var labels = new HashMap<Long, String>(); var cells = new HashSet<Long>();
        for (int x = 0; x < 3; x++) { long k = BlockPos.asLong(x, 10, 0); cells.add(k); labels.put(k, "minecraft:coal_ore"); }
        long gap = BlockPos.asLong(4, 10, 0); cells.add(gap); labels.put(gap, "minecraft:coal_ore"); // within 2: same vein
        long far = BlockPos.asLong(12, 10, 0); cells.add(far); labels.put(far, "minecraft:coal_ore");
        long iron = BlockPos.asLong(1, 11, 0); cells.add(iron); labels.put(iron, "minecraft:iron_ore");
        var veins = PlumbLineScan.cluster(cells, labels, 2);
        assertEquals(3, veins.size(), "One coal vein bridging a 1-block gap, a separate far coal, and the iron by type");
        var coal = veins.stream().filter(v -> v.label().equals("minecraft:coal_ore") && v.cells().size() == 4).findFirst().orElseThrow();
        assertEquals(0, coal.minX()); assertEquals(4, coal.maxX());
    }

    @Test void overlapAllowsOneBlockOfSlack() {
        var a = new Return(Kind.ORE, "x", 0, 0, 0, 2, 2, 2, true);
        assertTrue(PlumbLineMemory.overlaps(a, new Return(Kind.ORE, "x", 3, 0, 0, 5, 2, 2, true)));
        assertFalse(PlumbLineMemory.overlaps(a, new Return(Kind.ORE, "x", 5, 0, 0, 7, 2, 2, true)));
        assertTrue(Set.of(a).contains(a));
    }
}

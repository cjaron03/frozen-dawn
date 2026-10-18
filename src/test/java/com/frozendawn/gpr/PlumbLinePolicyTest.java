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

    @Test void raysAreUnitAndPointIntoTheGround() {
        int down = 0, n = 2000;
        for (int i = 0; i < n; i++) {
            var d = PlumbLinePolicy.direction(i, n);
            assertEquals(1, Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]), 1e-9);
            assertTrue(d[1] <= PlumbLinePolicy.CONE_TOP, "No ray climbs above the cone");
            if (d[1] < 0) down++;
        }
        assertEquals(n / 1.2, down, 3, "Down to straight below, spread evenly");
    }

    @Test void compassPointsNorthAtMinusZ() {
        assertEquals(0, PlumbLinePolicy.compass(0, -10));
        assertEquals(1, PlumbLinePolicy.compass(7, -7));
        assertEquals(2, PlumbLinePolicy.compass(10, 0));
        assertEquals(4, PlumbLinePolicy.compass(0, 10));
        assertEquals(6, PlumbLinePolicy.compass(-10, 0));
        assertEquals(7, PlumbLinePolicy.compass(-7, -7));
    }

    @Test void oresShareTypesAcrossVariantsAndFallBackForUnknown() {
        assertEquals("iron", PlumbLineOres.of("minecraft:deepslate_iron_ore").key());
        assertEquals("coal", PlumbLineOres.of("frozendawn:frozen_coal_ore").key());
        assertEquals(PlumbLineOres.Group.METALS, PlumbLineOres.of("frozendawn:deepslate_galena_ore").group());
        assertEquals(PlumbLineOres.Group.ENERGY, PlumbLineOres.of("frozendawn:deepslate_uranium_ore").group());
        assertEquals(3, PlumbLineOres.of("minecraft:ancient_debris").tier());
        var unknown = PlumbLineOres.of("othermod:tin_ore");
        assertFalse(unknown.known()); assertEquals("tin", unknown.key());
        assertTrue(PlumbLineOres.Target.ALL.matches(PlumbLineOres.Group.ENERGY));
        assertFalse(PlumbLineOres.Target.METALS.matches(PlumbLineOres.Group.ENERGY));
        assertEquals(PlumbLineOres.Target.ALL, PlumbLineOres.Target.MINERALS.next());
    }

    @Test void surveySheetFadesAfterADayAndDrawsOnlyInRange() {
        var sheet = new PlumbLineSurvey("minecraft:overworld", new BlockPos(100, 64, 100), 48, 1_000,
                java.util.List.of(new Return(Kind.ORE, "frozendawn:deepslate_galena_ore", 90, 20, 95, 93, 23, 98, false)));
        assertFalse(sheet.faded(1_000 + PlumbLineSurvey.INK_TICKS - 1));
        assertTrue(sheet.faded(1_000 + PlumbLineSurvey.INK_TICKS), "Ink fades one day after printing");
        assertTrue(sheet.inRange("minecraft:overworld", 100 + 48 + 15, 100));
        assertFalse(sheet.inRange("minecraft:overworld", 100 + 48 + 17, 100), "Radius plus a 16-block margin");
        assertFalse(sheet.inRange("minecraft:the_nether", 100, 100));
        var json = PlumbLineSurvey.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, sheet).getOrThrow();
        assertEquals(sheet, PlumbLineSurvey.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow(), "Sheet survives saving");
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

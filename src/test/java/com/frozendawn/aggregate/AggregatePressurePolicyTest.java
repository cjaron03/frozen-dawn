package com.frozendawn.aggregate;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AggregatePressurePolicyTest {
    @Test
    void pressureThresholdsAdvanceThroughEveryStructuralStage() {
        assertEquals(AggregateStage.DORMANT, AggregatePressurePolicy.stageFor(59.99D));
        assertEquals(AggregateStage.RESIDUE, AggregatePressurePolicy.stageFor(60.0D));
        assertEquals(AggregateStage.DEPOSIT, AggregatePressurePolicy.stageFor(140.0D));
        assertEquals(AggregateStage.OSSUARY, AggregatePressurePolicy.stageFor(240.0D));
        assertEquals(AggregateStage.GESTATION, AggregatePressurePolicy.stageFor(340.0D));
        assertEquals(AggregateStage.AWAKENING_ELIGIBLE,
                AggregatePressurePolicy.stageFor(400.0D));
    }

    @Test
    void traitsLockDeterministicallyAndStopAtThree() {
        EnumMap<AggregateLineage, Double> values = AggregatePressurePolicy.emptyLineages();
        values.put(AggregateLineage.RESONANT, 80.0D);
        values.put(AggregateLineage.RIMEBOUND, 70.0D);
        values.put(AggregateLineage.ARCHITECT, 60.0D);
        values.put(AggregateLineage.REMNANT, 50.0D);
        List<AggregateLineage> first = AggregatePressurePolicy.lockTraits(values, 42L);
        assertEquals(List.of(AggregateLineage.RESONANT, AggregateLineage.RIMEBOUND,
                AggregateLineage.ARCHITECT), first);
        assertEquals(first, AggregatePressurePolicy.lockTraits(values, 42L));
    }

    @Test
    void dominanceRequiresMoreThanHalfOfCountedMass() {
        EnumMap<AggregateLineage, Double> values = AggregatePressurePolicy.emptyLineages();
        values.put(AggregateLineage.UNDONE, 51.0D);
        values.put(AggregateLineage.NORMAL, 49.0D);
        assertEquals(AggregateLineage.UNDONE, AggregatePressurePolicy.dominant(values));
        values.put(AggregateLineage.NORMAL, 51.0D);
        assertNull(AggregatePressurePolicy.dominant(values));
    }

    @Test
    void growthAdvancesAtMostOneStagePerMinecraftDay() {
        assertEquals(AggregateStage.RESIDUE, AggregatePressurePolicy.nextGrowthStage(
                AggregateStage.DORMANT, AggregateStage.AWAKENING_ELIGIBLE, 12L, 11L));
        assertEquals(AggregateStage.DORMANT, AggregatePressurePolicy.nextGrowthStage(
                AggregateStage.DORMANT, AggregateStage.AWAKENING_ELIGIBLE, 11L, 11L));
        assertEquals(AggregateStage.OSSUARY, AggregatePressurePolicy.nextGrowthStage(
                AggregateStage.OSSUARY, AggregateStage.OSSUARY, 12L, 11L));
    }
}

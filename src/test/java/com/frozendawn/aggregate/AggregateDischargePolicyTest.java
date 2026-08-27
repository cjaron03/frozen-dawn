package com.frozendawn.aggregate;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregateDischargePolicyTest {
    private static final List<AggregateLineage> TRAITS = List.of(
            AggregateLineage.RIMEBOUND,
            AggregateLineage.RESONANT,
            AggregateLineage.REMNANT);

    @Test
    void firstDischargeUsesOnlyThePrimaryLineage() {
        assertEquals(List.of(AggregateLineage.RIMEBOUND),
                AggregateDischargePolicy.lineagesForWave(
                        TRAITS, false, AggregateDischargePolicy.PRIMARY_WAVE));
    }

    @Test
    void secondDischargeMixesRemainingLineagesWithoutDominance() {
        assertEquals(List.of(AggregateLineage.RESONANT, AggregateLineage.REMNANT),
                AggregateDischargePolicy.lineagesForWave(
                        TRAITS, false, AggregateDischargePolicy.SECONDARY_WAVE));
    }

    @Test
    void dominantUpgradeKeepsSecondDischargeFocused() {
        assertEquals(List.of(AggregateLineage.RESONANT),
                AggregateDischargePolicy.lineagesForWave(
                        TRAITS, true, AggregateDischargePolicy.SECONDARY_WAVE));
    }

    @Test
    void exposedCoreRequiresMeaningfulCommittedDamage() {
        assertEquals(38.5F, AggregateDischargePolicy.interruptThreshold(
                700.0F, AggregateDischargePolicy.PRIMARY_WAVE), 0.001F);
        assertEquals(45.5F, AggregateDischargePolicy.interruptThreshold(
                700.0F, AggregateDischargePolicy.SECONDARY_WAVE), 0.001F);
        assertEquals(24.0F, AggregateDischargePolicy.interruptThreshold(
                100.0F, AggregateDischargePolicy.PRIMARY_WAVE), 0.001F);
    }

    @Test
    void bodyMassThinsTwiceAndThenStops() {
        assertEquals(1.0F, AggregateDischargePolicy.massScaleForScars(0));
        assertEquals(0.90F, AggregateDischargePolicy.massScaleForScars(1));
        assertEquals(0.79F, AggregateDischargePolicy.massScaleForScars(2));
        assertEquals(0.79F, AggregateDischargePolicy.massScaleForScars(20));
        assertTrue(AggregateDischargePolicy.substantialCap() <= 4);
    }

    @Test
    void dischargeProducesAVisibleButBoundedWave() {
        assertEquals(2, AggregateDischargePolicy.substantialBodiesPerLineage());
        assertEquals(4, AggregateDischargePolicy.frostwritheFragmentCount(false));
        assertEquals(5, AggregateDischargePolicy.frostwritheFragmentCount(true));
    }
}

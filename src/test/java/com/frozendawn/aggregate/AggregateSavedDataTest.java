package com.frozendawn.aggregate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregateSavedDataTest {
    @Test
    void coreRewardCanOnlyBeClaimedOnce() {
        AggregateSavedData data = new AggregateSavedData();
        assertTrue(data.claimCoreReward());
        assertFalse(data.claimCoreReward());
    }

    @Test
    void stillpointRadiusUsesInclusiveSquaredDistance() {
        BlockPos anchor = BlockPos.ZERO;
        assertTrue(StillpointPolicy.isWithinRadius(anchor, new BlockPos(48, 0, 0), 48));
        assertFalse(StillpointPolicy.isWithinRadius(anchor, new BlockPos(49, 0, 0), 48));
    }

    @Test
    void stillpointChargeCompletesExactlyAtEightyTicks() {
        assertFalse(StillpointPolicy.chargeComplete(100L, 179L, 80));
        assertTrue(StillpointPolicy.chargeComplete(100L, 180L, 80));
        assertFalse(StillpointPolicy.chargeComplete(-1L, 999L, 80));
    }

    @Test
    void stillpointDetectsFastCrossingWithBothEndpointsOutside() {
        BlockPos anchor = BlockPos.ZERO;
        assertTrue(StillpointPolicy.segmentEnters(anchor,
                new Vec3(-60.0D, 0.5D, 0.5D),
                new Vec3(60.0D, 0.5D, 0.5D), 48.0D));
        assertFalse(StillpointPolicy.segmentEnters(anchor,
                new Vec3(-60.0D, 60.0D, 0.5D),
                new Vec3(60.0D, 60.0D, 0.5D), 48.0D));
    }

    @Test
    void stillpointClampReturnsTheSourceSideOfTheShell() {
        Vec3 clamped = StillpointPolicy.clampOutside(BlockPos.ZERO,
                new Vec3(-60.0D, 0.5D, 0.5D), 48.0D);
        assertTrue(clamped.x < 0.0D);
        assertEquals(48.35D, clamped.distanceTo(BlockPos.ZERO.getCenter()), 0.0001D);
    }

    @Test
    void resolvedAuthorityRejectsAllFuturePressure() {
        AggregateSavedData data = new AggregateSavedData();
        data.resolve();
        assertFalse(data.addPressure(new AggregatePressurePolicy.Contribution(
                25.0D, AggregateLineage.UNDONE)));
    }

    @Test
    void fightSnapshotRetainsLastKnownPositionForRecovery() {
        AggregateSavedData data = new AggregateSavedData();
        BlockPos position = new BlockPos(120, 74, -36);

        data.beginFight(java.util.UUID.randomUUID(), 1, 700.0F, position);

        assertEquals(position, data.fightPosition().orElseThrow());
    }

    @Test
    void dischargeReservationIsStableAndCannotDuplicate() {
        AggregateSavedData data = new AggregateSavedData();
        data.beginFight(UUID.randomUUID(), 1, 700.0F, BlockPos.ZERO);

        assertTrue(data.reserveDischarge(AggregateDischargePolicy.PRIMARY_WAVE,
                List.of(AggregateLineage.RIMEBOUND),
                List.of(new BlockPos(4, 64, 4)), 100L));
        assertFalse(data.reserveDischarge(AggregateDischargePolicy.PRIMARY_WAVE,
                List.of(AggregateLineage.RIMEBOUND),
                List.of(new BlockPos(6, 64, 6)), 101L));
        assertEquals(1, data.reinforcements().size());
        assertEquals(data.fightId().orElseThrow(),
                data.reinforcements().getFirst().fightId());
        assertEquals(AggregateReinforcementState.PENDING,
                data.reinforcements().getFirst().state());
    }

    @Test
    void interruptedDischargeStaysSpentButCancelsBodies() {
        AggregateSavedData data = new AggregateSavedData();
        data.beginFight(UUID.randomUUID(), 1, 700.0F, BlockPos.ZERO);
        data.reserveDischarge(AggregateDischargePolicy.SECONDARY_WAVE,
                List.of(AggregateLineage.RESONANT, AggregateLineage.REMNANT),
                List.of(new BlockPos(4, 64, 4), new BlockPos(-4, 64, -4)), 100L);

        data.cancelPendingReinforcements(AggregateDischargePolicy.SECONDARY_WAVE);

        assertTrue(data.dischargeSpent(AggregateDischargePolicy.SECONDARY_WAVE));
        assertEquals(1, data.dischargeScars());
        assertTrue(data.pendingReinforcements(
                AggregateDischargePolicy.SECONDARY_WAVE).isEmpty());
        assertTrue(data.reinforcements().stream().allMatch(record ->
                record.state() == AggregateReinforcementState.CANCELLED));
    }

    @Test
    void debugDischargeResetMakesTheVisualTestRepeatable() {
        AggregateSavedData data = new AggregateSavedData();
        data.beginFight(UUID.randomUUID(), 1, 700.0F, BlockPos.ZERO);
        data.reserveDischarge(AggregateDischargePolicy.PRIMARY_WAVE,
                List.of(AggregateLineage.RIMEBOUND),
                List.of(new BlockPos(4, 64, 4)), 100L);
        data.reserveDischarge(AggregateDischargePolicy.SECONDARY_WAVE,
                List.of(AggregateLineage.RESONANT),
                List.of(new BlockPos(-4, 64, -4)), 100L);

        data.debugResetDischarges();

        assertFalse(data.dischargeSpent(AggregateDischargePolicy.PRIMARY_WAVE));
        assertFalse(data.dischargeSpent(AggregateDischargePolicy.SECONDARY_WAVE));
        assertTrue(data.reinforcements().isEmpty());
    }

    @Test
    void debugRearmAllowsAResolvedEncounterToSpawnAgain() {
        AggregateSavedData data = new AggregateSavedData();
        BlockPos ossuary = new BlockPos(40, 70, -20);
        data.setOssuary(ossuary, 42L);
        data.beginFight(UUID.randomUUID(), 1, 700.0F, ossuary);
        assertTrue(data.claimCoreReward());
        data.resolve();

        data.debugRearmFight();

        assertFalse(data.resolved());
        assertFalse(data.fightStarted());
        assertTrue(data.ossuaryPos().isPresent());
        assertEquals(ossuary, data.ossuaryPos().orElseThrow());
        assertTrue(data.claimCoreReward());
    }
}

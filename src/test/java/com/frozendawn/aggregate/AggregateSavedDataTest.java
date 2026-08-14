package com.frozendawn.aggregate;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

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
}

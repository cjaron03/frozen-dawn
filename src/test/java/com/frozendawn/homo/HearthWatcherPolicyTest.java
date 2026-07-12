package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HearthWatcherPolicyTest {

    @Test
    void onlyReconciledTraceHearthsCanHostWatchers() {
        ReturnedHearthSavedData state = selectedState();
        ReturnedHearthSavedData.HearthRecord major = major(state);
        assertFalse(HearthWatcherPolicy.canHostWatcher(major));

        state.advanceMaturationForDebug(HearthMaturationPolicy.TRACE_START_TICKS, 1000L);
        state.resolveSurface(major.id(), new BlockPos(major.center().getX(), 70, major.center().getZ()));
        state.recordStructureProgress(major.id(), HearthReconciliationPolicy.TRACE_PLAN_VERSION,
                100, ReturnedHearthSavedData.HearthStage.TRACE, true);

        assertTrue(HearthWatcherPolicy.canHostWatcher(major));
    }

    @Test
    void spawnRingIsDeterministicAndStaysDistant() {
        List<BlockPos> first = HearthWatcherPolicy.spawnOffsets(12345L);
        List<BlockPos> second = HearthWatcherPolicy.spawnOffsets(12345L);

        assertEquals(first, second);
        assertEquals(HearthWatcherPolicy.SPAWN_ATTEMPTS, first.size());
        for (BlockPos offset : first) {
            double distance = Math.sqrt((double) offset.getX() * offset.getX()
                    + (double) offset.getZ() * offset.getZ());
            assertTrue(distance >= HearthWatcherPolicy.MIN_SPAWN_RADIUS - 1.0D);
            assertTrue(distance <= HearthWatcherPolicy.MAX_SPAWN_RADIUS + 1.0D);
        }
    }

    @Test
    void retreatAndHomeGatesUseStrictBoundaries() {
        assertTrue(HearthWatcherPolicy.shouldRetreat(8.99D * 8.99D));
        assertFalse(HearthWatcherPolicy.shouldRetreat(9.0D * 9.0D));
        assertFalse(HearthWatcherPolicy.shouldReturnHome(20.0D * 20.0D));
        assertTrue(HearthWatcherPolicy.shouldReturnHome(20.01D * 20.01D));
    }

    @Test
    void textureVariantIsStableAndValid() {
        int variant = HearthWatcherPolicy.textureVariant(8675309L);
        assertEquals(variant, HearthWatcherPolicy.textureVariant(8675309L));
        assertTrue(variant >= 0 && variant < 5);
    }

    @Test
    void onlyOrdinaryReturnedProactivelyTargetPlayers() {
        assertTrue(HearthWatcherPolicy.canProactivelyTargetPlayer(false,
                ReturnedHearthSavedData.HiveRelationship.NEUTRAL));
        assertFalse(HearthWatcherPolicy.canProactivelyTargetPlayer(true,
                ReturnedHearthSavedData.HiveRelationship.NEUTRAL));
        assertFalse(HearthWatcherPolicy.canProactivelyTargetPlayer(true,
                ReturnedHearthSavedData.HiveRelationship.SUSPICIOUS));
        assertTrue(HearthWatcherPolicy.canProactivelyTargetPlayer(true,
                ReturnedHearthSavedData.HiveRelationship.ORSATHAE));
    }

    private static ReturnedHearthSavedData selectedState() {
        ReturnedHearthSavedData state = new ReturnedHearthSavedData();
        BlockPos anchor = new BlockPos(12, 70, -24);
        state.rememberTransponderAnchor(anchor);
        state.applySelectionPlan(HearthSelectionPolicy.createPlan(998877L, anchor), 1000L);
        return state;
    }

    private static ReturnedHearthSavedData.HearthRecord major(ReturnedHearthSavedData state) {
        return state.hearth(HearthSelectionPolicy.HearthType.MAJOR).orElseThrow();
    }
}

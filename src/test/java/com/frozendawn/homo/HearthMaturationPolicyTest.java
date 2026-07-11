package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HearthMaturationPolicyTest {

    @Test
    void majorHearthAdvancesAcrossAllAuthoredStages() {
        assertMajorStage(0L, ReturnedHearthSavedData.HearthStage.PLANNED);
        assertMajorStage(HearthMaturationPolicy.TRACE_START_TICKS - 1L,
                ReturnedHearthSavedData.HearthStage.PLANNED);
        assertMajorStage(HearthMaturationPolicy.TRACE_START_TICKS,
                ReturnedHearthSavedData.HearthStage.TRACE);
        assertMajorStage(HearthMaturationPolicy.FORMED_START_TICKS - 1L,
                ReturnedHearthSavedData.HearthStage.TRACE);
        assertMajorStage(HearthMaturationPolicy.FORMED_START_TICKS,
                ReturnedHearthSavedData.HearthStage.FORMED);
        assertMajorStage(HearthMaturationPolicy.INTACT_START_TICKS - 1L,
                ReturnedHearthSavedData.HearthStage.FORMED);
        assertMajorStage(HearthMaturationPolicy.INTACT_START_TICKS,
                ReturnedHearthSavedData.HearthStage.INTACT);
    }

    @Test
    void minorHearthCapsAtFormed() {
        assertEquals(ReturnedHearthSavedData.HearthStage.PLANNED,
                HearthMaturationPolicy.stageFor(HearthSelectionPolicy.HearthType.MINOR, -1L));
        assertEquals(ReturnedHearthSavedData.HearthStage.TRACE,
                HearthMaturationPolicy.stageFor(HearthSelectionPolicy.HearthType.MINOR,
                        HearthMaturationPolicy.TRACE_START_TICKS));
        assertEquals(ReturnedHearthSavedData.HearthStage.FORMED,
                HearthMaturationPolicy.stageFor(HearthSelectionPolicy.HearthType.MINOR,
                        HearthMaturationPolicy.FORMED_START_TICKS));
        assertEquals(ReturnedHearthSavedData.HearthStage.FORMED,
                HearthMaturationPolicy.stageFor(HearthSelectionPolicy.HearthType.MINOR,
                        Long.MAX_VALUE));
    }

    private static void assertMajorStage(long maturity,
                                         ReturnedHearthSavedData.HearthStage expected) {
        assertEquals(expected, HearthMaturationPolicy.stageFor(
                HearthSelectionPolicy.HearthType.MAJOR, maturity));
    }
}

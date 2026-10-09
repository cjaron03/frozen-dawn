package com.frozendawn.client;

import com.frozendawn.event.SuffocationStage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphericActionBarPolicyTest {
    private static final String RESTORING = "ui.frozendawn.room.sealed_restoring";
    private static final String RESTORED = "ui.frozendawn.room.air_restored";

    @Test void restoringRetainsDangerAtEverySeverity() {
        for (var stage : SuffocationStage.values()) {
            var notice = AtmosphericActionBarPolicy.select(RESTORING, false, stage);
            assertEquals(RESTORING, notice.primaryKey());
            assertEquals(stage != SuffocationStage.NONE, notice.danger());
            assertEquals(stage == SuffocationStage.NONE ? null : "ui.frozendawn.room.still_suffocating", notice.secondaryKey());
        }
    }
    @Test void packetOrderCannotFlashSuccessOverOngoingDanger() {
        var notice = AtmosphericActionBarPolicy.select(RESTORED, false, SuffocationStage.DYING);
        assertEquals(SuffocationStage.DYING.translationKey(), notice.primaryKey());
        assertTrue(notice.danger());
        notice = AtmosphericActionBarPolicy.select(RESTORED, false, SuffocationStage.NONE);
        assertEquals(RESTORED, notice.primaryKey());
        assertFalse(notice.danger());
    }
    @Test void expiredRoomBannerKeepsHighestDangerUntilServerClearsIt() {
        var notice = AtmosphericActionBarPolicy.select(null, false, SuffocationStage.FADING);
        assertEquals(SuffocationStage.FADING.translationKey(), notice.primaryKey());
        assertTrue(notice.danger());
        assertNull(AtmosphericActionBarPolicy.select(null, false, SuffocationStage.NONE));
    }
    @Test void helmetRoutesRoomNoticeAwayFromActionBarWithoutHidingDanger() {
        assertNull(AtmosphericActionBarPolicy.select(RESTORING, true, SuffocationStage.NONE));
        assertEquals(SuffocationStage.FADING.translationKey(),
                AtmosphericActionBarPolicy.select(RESTORING, true, SuffocationStage.FADING).primaryKey());
    }
    @Test void warningThresholdsKeepOriginalTimingsAndChooseOnlyHighest() {
        assertEquals(SuffocationStage.NONE, SuffocationStage.fromTicks(29));
        assertEquals(SuffocationStage.LIGHTHEADED, SuffocationStage.fromTicks(30));
        assertEquals(SuffocationStage.LIGHTHEADED, SuffocationStage.fromTicks(79));
        assertEquals(SuffocationStage.NAUSEA, SuffocationStage.fromTicks(80));
        assertEquals(SuffocationStage.NAUSEA, SuffocationStage.fromTicks(139));
        assertEquals(SuffocationStage.FADING, SuffocationStage.fromTicks(140));
        assertEquals(SuffocationStage.FADING, SuffocationStage.fromTicks(199));
        assertEquals(SuffocationStage.DYING, SuffocationStage.fromTicks(200));
    }
}

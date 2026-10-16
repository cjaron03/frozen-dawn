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
    @Test void routineAirlockStatusCannotDisplaceAnyActiveOxygenWarning() {
        for (var stage:SuffocationStage.values()) {
            assertFalse(AtmosphericActionBarPolicy.allowsDeviceNotice(true,stage));
            assertEquals(stage==SuffocationStage.NONE,AtmosphericActionBarPolicy.allowsDeviceNotice(false,stage));
        }
    }
    @Test void airlockStatusRoutesToHelmetAndFallbackActionBar() {
        String key="message.frozendawn.airlock.pressurizing";
        assertNull(AtmosphericActionBarPolicy.select(key,true,SuffocationStage.NONE));
        assertEquals(key,AtmosphericActionBarPolicy.select(key,false,SuffocationStage.NONE).primaryKey());
    }
    @Test void doorReasonsShareOneHelmetOrActionBarSlotAndPreserveOxygenDanger() {
        for(var status:new com.frozendawn.network.AirlockStatusPayload.Status[]{
                com.frozendawn.network.AirlockStatusPayload.Status.INNER_DIFFERENTIAL,
                com.frozendawn.network.AirlockStatusPayload.Status.CHAMBER_VACUUM,
                com.frozendawn.network.AirlockStatusPayload.Status.BASE_VACUUM,
                com.frozendawn.network.AirlockStatusPayload.Status.PRESSURE_UNKNOWN,
                com.frozendawn.network.AirlockStatusPayload.Status.VALVE_EQUALIZED,
                com.frozendawn.network.AirlockStatusPayload.Status.DOOR_OPEN_VACUUM,
                com.frozendawn.network.AirlockStatusPayload.Status.DOOR_OPEN_MATCHED}) {
            var fallback=AtmosphericActionBarPolicy.select(status.key(),false,SuffocationStage.NONE);
            if(status.opensDoor())assertNull(fallback,"Successful opening stays silent");
            else {assertEquals(status.key(),fallback.primaryKey());assertNull(fallback.secondaryKey());}
            assertNull(AtmosphericActionBarPolicy.select(status.key(),true,SuffocationStage.NONE),"Helmet receives the same typed reason without a duplicate action bar");
            assertFalse(AtmosphericActionBarPolicy.allowsDeviceNotice(false,SuffocationStage.FADING),"Door feedback never replaces suffocation danger");
        }
    }

    @Test void doorOpeningStaysSilentWithEitherHelmetStateAndRetainsEveryOxygenWarning() {
        for(var status:new com.frozendawn.network.AirlockStatusPayload.Status[]{
                com.frozendawn.network.AirlockStatusPayload.Status.DOOR_OPEN_VACUUM,
                com.frozendawn.network.AirlockStatusPayload.Status.DOOR_OPEN_MATCHED}) {
            for(boolean helmet:new boolean[]{false,true}) {
                assertNull(AtmosphericActionBarPolicy.select(status.key(),helmet,SuffocationStage.NONE),"Opening feedback stays silent when wearing, adding or removing the helmet");
                for(var stage:SuffocationStage.values())if(stage!=SuffocationStage.NONE) {
                    var notice=AtmosphericActionBarPolicy.select(status.key(),helmet,stage);
                    assertEquals(stage.translationKey(),notice.primaryKey());assertTrue(notice.danger());
                }
            }
        }
    }

    @Test void interactiveDoorReasonReplacesOnlyCompletedRoomNotice() {
        assertTrue(AtmosphericActionBarPolicy.allowsDoorNotice(RESTORED,SuffocationStage.NONE));
        assertTrue(AtmosphericActionBarPolicy.allowsDoorNotice("ui.frozendawn.room.seal_verified",SuffocationStage.NONE));
        assertFalse(AtmosphericActionBarPolicy.allowsDeviceNotice(true,SuffocationStage.NONE));
        for(String key:new String[]{RESTORING,"ui.frozendawn.room.sealed_no_supply","ui.frozendawn.suit.atmospheric_breach"})
            assertFalse(AtmosphericActionBarPolicy.allowsDoorNotice(key,SuffocationStage.NONE));
        for(var stage:SuffocationStage.values())if(stage!=SuffocationStage.NONE)
            assertFalse(AtmosphericActionBarPolicy.allowsDoorNotice(RESTORED,stage));
    }

}

package com.frozendawn.cyclone;

import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CycloneThunderTest {
    private static CycloneStorm storm() {
        return CycloneStorm.roll(23, CycloneSeverity.CATASTROPHIC, 0, 0, 1000, new UUID(1, 2), 0);
    }
    private static CycloneSample sample(CycloneStage stage, float intensity) {
        return new CycloneSample(stage, intensity, 0, 0, 900);
    }
    @Test void cadenceBoundsAndInactiveStagesAcrossAllSeverities() {
        var r = new Random(1);
        for (var severity : CycloneSeverity.values()) for (var stage : CycloneStage.values()) {
            int[] bounds = switch (stage) {
                case BOMBOGENESIS -> new int[]{1000,1400}; case ARRIVAL -> new int[]{200,400};
                case DISSIPATION -> new int[]{600,1000};
                case PEAK -> switch(severity) { case MODERATE -> new int[]{160,240}; case SEVERE -> new int[]{80,160}; case CATASTROPHIC -> new int[]{40,100}; };
                default -> new int[]{Integer.MAX_VALUE,Integer.MAX_VALUE};
            };
            for(int i=0;i<1000;i++) {
                int d=CycloneThunder.nextDelay(stage,severity,r);
                assertTrue(d>=bounds[0] && d<=bounds[1]);
            }
        }
    }
    @Test void stageCuesCannotFlashTheWorldAtDistanceOrDuringDissipation() {
        var r=new Random(9);boolean close=false,front=false;
        for(int i=0;i<1000;i++) {
            var bomb=CycloneThunder.choose(CycloneStage.BOMBOGENESIS,CycloneSeverity.SEVERE,0,r);
            assertEquals(CycloneThunder.Band.DISTANT,bomb.band());front|=bomb.frontFlash();
            var fade=CycloneThunder.choose(CycloneStage.DISSIPATION,CycloneSeverity.CATASTROPHIC,.9f,r);
            assertEquals(CycloneThunder.Band.DISTANT,fade.band());assertFalse(fade.frontFlash());
            var peak=CycloneThunder.choose(CycloneStage.PEAK,CycloneSeverity.CATASTROPHIC,1,r);
            close|=peak.band()==CycloneThunder.Band.CLOSE;assertFalse(peak.frontFlash());
            assertNotEquals(CycloneThunder.Band.CLOSE,CycloneThunder.choose(CycloneStage.PEAK,CycloneSeverity.CATASTROPHIC,.1f,r).band());
            assertEquals(CycloneThunder.Cue.NONE,CycloneThunder.choose(CycloneStage.AFTERMATH,CycloneSeverity.CATASTROPHIC,1,r));
        }
        assertTrue(close && front);
    }
    @Test void repeatedTicksDoNotDuplicateAndStageChangesWaitForTheirOwnCadence() {
        var schedule=new CycloneThunder.Schedule();var storm=storm();var peak=sample(CycloneStage.PEAK,1);
        int events=0;long last=-1000;
        for(int t=0;t<5000;t++) {
            var cue=schedule.tick(storm,peak,t,true);
            if(cue.band()!=CycloneThunder.Band.NONE) {assertTrue(t-last>=40);last=t;events++;}
            assertEquals(CycloneThunder.Cue.NONE,schedule.tick(storm,peak,t,true));
        }
        assertTrue(events>=49 && events<=125);
        for(int t=5000;t<6000;t++) assertEquals(CycloneThunder.Cue.NONE,schedule.tick(storm,sample(CycloneStage.BOMBOGENESIS,0),t,true));
    }
    @Test void stormEndSuppressionReentryAndClockJumpsNeverReplayBacklog() {
        var schedule=new CycloneThunder.Schedule();var storm=storm();var peak=sample(CycloneStage.PEAK,1);
        assertEquals(CycloneThunder.Cue.NONE,schedule.tick(storm,peak,0,true));
        assertEquals(CycloneThunder.Cue.NONE,schedule.tick(storm,peak,100000,true));
        assertEquals(CycloneThunder.Cue.NONE,schedule.tick(null,CycloneSample.NONE,100001,true));
        assertEquals(CycloneThunder.Cue.NONE,schedule.tick(storm,peak,100002,true));
        assertEquals(CycloneThunder.Cue.NONE,schedule.tick(storm,peak,100003,false));
        assertEquals(CycloneThunder.Cue.NONE,schedule.tick(storm,peak,100004,true));
        assertEquals(CycloneThunder.Cue.NONE,schedule.tick(storm,peak,1,true));
        schedule.clear();assertEquals(CycloneThunder.Cue.NONE,schedule.tick(storm,peak,100000,true));
    }
    @Test void repeatedSyncDoesNotResetScheduleAndNewStormDoes() {
        var a=new CycloneThunder.Schedule();var b=new CycloneThunder.Schedule();var storm=storm();var peak=sample(CycloneStage.PEAK,1);
        for(int t=0;t<500;t++) assertEquals(a.tick(storm,peak,t,true),b.tick(CycloneStorm.load(storm.save()),peak,t,true));
        assertEquals(CycloneThunder.Cue.NONE,a.tick(storm.atStage(CycloneStage.PEAK,9000),peak,500,true));
    }
    @Test void smallNetworkClockCorrectionsCannotStarveOrDuplicateThunder() {
        var stable=new CycloneThunder.Schedule();var corrected=new CycloneThunder.Schedule();
        var storm=storm();var peak=sample(CycloneStage.PEAK,1);int events=0;
        for(int t=0;t<5000;t++) {
            var expected=stable.tick(storm,peak,t,true);
            assertEquals(expected,corrected.tick(storm,peak,t,true));
            if(expected.band()!=CycloneThunder.Band.NONE)events++;
            if(t%20==0)assertEquals(CycloneThunder.Cue.NONE,corrected.tick(storm,peak,t-2,true));
        }
        assertTrue(events>49);
    }
    @Test void indoorSoundIsMuffledAndTrackAndUndergroundFadeReachZero() {
        assertEquals(1,CycloneThunder.gain(CycloneStage.PEAK,1,1,1,1));
        assertEquals(.25,CycloneThunder.gain(CycloneStage.PEAK,1,1,1,0));
        assertEquals(.25,CycloneThunder.gain(CycloneStage.PEAK,.25f,1,1,1));
        assertEquals(0,CycloneThunder.gain(CycloneStage.BOMBOGENESIS,0,0,1,1));
        assertEquals(0,CycloneThunder.gain(CycloneStage.PEAK,1,1,0,1));
        assertEquals(.5,CycloneThunder.gain(CycloneStage.BOMBOGENESIS,0,.5f,1,1));
    }
}

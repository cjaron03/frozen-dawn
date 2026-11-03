package com.frozendawn.cyclone;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CyclonePolicyTest {
    private static final UUID TARGET=new UUID(1,2);
    private static CycloneStorm storm(CycloneSeverity severity) {
        return new CycloneStorm(9,severity,0,0,0,1000,TARGET,4800,3600,3000,7200,4800,1800,-1);
    }
    @Test void targetSeesEveryStageInOrderAndNoStormAfterEnd() {
        var s=storm(CycloneSeverity.SEVERE);
        for(var st:CycloneStage.values())if(st!=CycloneStage.NONE)
            assertEquals(st,CycloneField.sample(s,0,0,s.startGameTime()+s.offset(st)+10).stage());
        assertEquals(CycloneSample.NONE,CycloneField.sample(s,0,0,s.endGameTime()));
        assertEquals(CycloneSample.NONE,CycloneField.sample(s,0,0,999));
    }
    @Test void regionalFrontMovesWithoutRetargeting() {
        var s=storm(CycloneSeverity.SEVERE);long peak=s.startGameTime()+s.offset(CycloneStage.PEAK)+100;
        assertEquals(CycloneStage.PEAK,CycloneField.sample(s,0,0,peak).stage());
        assertNotEquals(CycloneStage.PEAK,CycloneField.sample(s,1000,0,peak).stage());
        assertEquals(CycloneStage.PEAK,CycloneField.sample(s,1000,0,peak+5000).stage());
        assertEquals(CycloneSample.NONE,CycloneField.sample(s,0,801,peak));
        assertEquals(CycloneSample.NONE,CycloneField.sample(s,-2000,0,peak));
        assertEquals(TARGET,s.target());assertEquals(0,s.targetX());
    }
    @Test void lateralEdgeIsContinuousAndOutsideIsIdentity() {
        var s=storm(CycloneSeverity.SEVERE);long peak=s.startGameTime()+s.offset(CycloneStage.PEAK)+100;
        assertEquals(1,CycloneField.sample(s,0,680,peak).intensity(),1e-6);
        assertEquals(.5,CycloneField.sample(s,0,740,peak).intensity(),1e-6);
        assertEquals(0,CycloneField.sample(s,0,800,peak).intensity(),1e-6);
        assertEquals(1000,CycloneField.sample(s,0,800,peak).pressureHpa());
    }
    @Test void peakAndCalmBalanceValuesMatchAndGogglesDoNotEraseWhiteout() {
        for(var severity:CycloneSeverity.values()) {
            var s=storm(severity);var peak=CycloneField.sample(s,0,0,1000+s.offset(CycloneStage.PEAK)+100);
            var calm=CycloneField.sample(s,0,0,3400);
            assertEquals(severity.peakOffset,peak.temperatureOffset());assertEquals(severity.pressure,peak.pressureHpa());
            assertEquals(15,calm.temperatureOffset());
            assertEquals(severity.visibility,CycloneVisuals.visibility(peak,severity,12,false));
            assertEquals(severity.gogglesVisibility,CycloneVisuals.visibility(peak,severity,32,true));
            assertEquals(56,CycloneVisuals.visibility(calm,severity,12,false));
            assertEquals(12,CycloneVisuals.visibility(CycloneSample.NONE,severity,12,false));
        }
    }
    @Test void configuredVisibilityInterpolatesAndNeverPenalizesGoggles() {
        var peak = new CycloneSample(CycloneStage.PEAK, 1, 0, -19, 933);
        var arrival = new CycloneSample(CycloneStage.ARRIVAL, .5f, 0, -9.5f, 966.5f);
        var calm = new CycloneSample(CycloneStage.FALSE_CALM, 0, 1, 15, 1000);
        assertEquals(6, CycloneVisuals.visibility(peak, 6, 12, 18, false));
        assertEquals(12, CycloneVisuals.visibility(arrival, 6, 12, 18, false));
        assertEquals(12, CycloneVisuals.visibility(peak, 6, 12, 18, true));
        assertEquals(40, CycloneVisuals.visibility(peak, 40, 12, 18, true));
        assertEquals(56, CycloneVisuals.visibility(calm, 6, 12, 18, false));
        assertEquals(18.123f, CycloneVisuals.visibility(CycloneSample.NONE, 6, 12, 18.123f, false), 0);
        assertThrows(IllegalArgumentException.class, () -> new CycloneVisuals.Peaks(Float.NaN, 4, 2));
        assertThrows(IllegalArgumentException.class, () -> new CycloneVisuals.Peaks(8, 0, 2));
    }
    @Test void collapseHasOneLinearEnvelopeAndEndsEvenInPhaseSix() {
        var s=storm(CycloneSeverity.SEVERE);long t=1000+s.offset(CycloneStage.PEAK)+100;
        var c=s.withCollapse(t);
        assertEquals(.5,CycloneField.sample(c,0,0,t+600).intensity(),1e-6);
        assertEquals(CycloneSample.NONE,CycloneField.sample(c,0,0,t+1200));
        assertEquals(CycloneTransitions.Action.END,CycloneTransitions.decide(6,false,true,true,false,Optional.of(c),t+1200,true,false));
    }
    @Test void phaseAndFightTransitionsDoNotSpawnOrRestartStorms() {
        var active=Optional.of(storm(CycloneSeverity.SEVERE));
        assertEquals(CycloneTransitions.Action.COLLAPSE,CycloneTransitions.decide(6,false,true,true,false,active,2000,true,false));
        assertEquals(CycloneTransitions.Action.END,CycloneTransitions.decide(5,true,true,true,false,active,2000,true,false));
        assertEquals(CycloneTransitions.Action.END,CycloneTransitions.decide(4,false,true,true,false,active,2000,true,false));
        assertEquals(CycloneTransitions.Action.NONE,CycloneTransitions.decide(5,false,true,true,true,Optional.empty(),2000,false,false));
        assertEquals(CycloneTransitions.Action.NONE,CycloneTransitions.decide(5,false,true,true,true,Optional.empty(),2000,true,true));
        assertEquals(CycloneTransitions.Action.NONE,CycloneTransitions.decide(5,false,true,true,true,active,2000,true,false));
        assertEquals(CycloneTransitions.Action.START,CycloneTransitions.decide(5,false,true,true,true,Optional.empty(),2000,true,false));
        assertEquals(CycloneSeverity.SEVERE,CycloneTransitions.effectiveSeverity(CycloneSeverity.CATASTROPHIC,false));
    }
    @Test void plannerRespectsWindowsSpacingCountAndFirstStormAcrossSeeds() {
        long p5=56*24000L,p6=72*24000L;
        for(int seed=0;seed<1000;seed++) {
            var plan=CyclonePlanner.plan(p5,p6,seed,4,true,.05);
            assertTrue(plan.size()==3 || plan.size()==4);assertEquals(CycloneSeverity.MODERATE,plan.getFirst().severity());
            assertNotEquals(CycloneSeverity.CATASTROPHIC,plan.get(1).severity());
            assertTrue(plan.getFirst().startApocalypseTick()>=p5+48000 && plan.getFirst().startApocalypseTick()<=p5+96000);
            assertTrue(plan.getLast().startApocalypseTick()<=p6-24000);
            for(int i=1;i<plan.size();i++)assertTrue(plan.get(i).startApocalypseTick()-plan.get(i-1).startApocalypseTick()>=72000);
            assertEquals(plan,CyclonePlanner.plan(p5,p6,seed,4,true,.05));
            assertTrue(CyclonePlanner.plan(p5,p6,seed,4,false,1).stream().noneMatch(p->p.severity()==CycloneSeverity.CATASTROPHIC));
        }
        assertTrue(CyclonePlanner.plan(0,48000,1,4,true,.05).isEmpty());
        assertTrue(CyclonePlanner.plan(p5,p6,1,0,true,.05).isEmpty());
    }
    @Test void overdueStartsPostponeRemainingStormsInsteadOfReplayingBacklog() {
        var p=CyclonePlanner.plan(0,1000000,9,4,true,.05);
        var delayed=CyclonePlanner.postpone(p,1,800000);
        assertEquals(p.getFirst(),delayed.getFirst());
        assertTrue(delayed.get(1).startApocalypseTick()>=872000);
        for(int i=2;i<delayed.size();i++)assertTrue(delayed.get(i).startApocalypseTick()-delayed.get(i-1).startApocalypseTick()>=72000);
    }
    @Test void forecastsAreRegionalAndPressureTrendsFallThenRecover() {
        var s=storm(CycloneSeverity.SEVERE);
        assertTrue(CycloneForecast.read(s,0,0,1001).etaSeconds()>=300);
        assertFalse(CycloneForecast.read(s,0,900,1001).visible());
        assertTrue(CycloneForecast.read(s,0,0,7000).trendHpaPerMin()<0);
        assertTrue(CycloneForecast.read(s,0,0,1000+s.offset(CycloneStage.DISSIPATION)+100).trendHpaPerMin()>0);
        assertFalse(CycloneForecast.read(s.withCollapse(2000),0,0,2001).visible());
    }
    @Test void catastrophicAlwaysHasAtLeastSixtySecondsAfterItsFirstWarning() {
        for(int seed=0;seed<500;seed++) {
            var s=CycloneStorm.roll(seed,CycloneSeverity.CATASTROPHIC,0,0,1000,TARGET,0);
            assertTrue(s.offset(CycloneStage.ARRIVAL)-CycloneForecast.earPopTick(s)>=1200);
            assertFalse(CycloneForecast.read(s,0,0,999+s.calmTicks()).visible());
            assertTrue(CycloneForecast.read(s,0,0,1000+s.calmTicks()).visible());
        }
    }
    @Test void stageJumpPreservesTrackIdentityAndDurations() {
        var s=storm(CycloneSeverity.SEVERE);var jumped=s.atStage(CycloneStage.PEAK,50000);
        assertEquals(CycloneStage.PEAK,CycloneField.sample(jumped,0,0,50000).stage());
        assertEquals(s.seed(),jumped.seed());assertEquals(s.target(),jumped.target());assertEquals(s.totalTicks(),jumped.totalTicks());
    }
}

package com.frozendawn.cyclone;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CycloneThermalTest {
    static CycloneSample peak(){return new CycloneSample(CycloneStage.PEAK,1,0,-30,895);}
    @Test void inactiveWindIsExactlyTheExistingConductanceAndHeaterReach() {
        for(double k:new double[]{.025,.075,.2,.25,.02})assertEquals(k,CycloneThermal.windFilmConductance(k,0),0);
        assertEquals(1,CycloneThermal.windChillMultiplier(CycloneSample.NONE,CycloneSeverity.CATASTROPHIC));
        assertEquals(.6f,CycloneThermal.exposedHeaterRadiusFactor(CycloneSample.NONE,CycloneSeverity.CATASTROPHIC));
    }
    @Test void windStripsTheOutsideFilmRatherThanMultiplyingAllInsulationLoss() {
        assertTrue(CycloneThermal.windFilmConductance(.025,.7f)/.025<1.1);
        assertEquals(2,CycloneThermal.windFilmConductance(.25,1)/.25,1e-8);
        double stone=CycloneThermal.windFilmConductance(.2,.7f)/.2;
        assertTrue(stone>1.2&&stone<1.5);
        double layered=.25/(1/.8+1/.1);
        assertTrue(CycloneThermal.windFilmConductance(layered,1)/layered<1.06);
    }
    @Test void shallowTransitionIsSmoothAndDeepGroundIsUnaffected() {
        assertEquals(0,CycloneThermal.surfaceWeight(-64));assertEquals(0,CycloneThermal.surfaceWeight(48));
        assertEquals(.5,CycloneThermal.surfaceWeight(56),1e-8);assertEquals(1,CycloneThermal.surfaceWeight(64));
        assertEquals(1,CycloneThermal.surfaceWeight(200));
        assertTrue(CycloneThermal.surfaceWeight(63.99)> .9999);
        for(int y=49;y<=64;y++)assertTrue(CycloneThermal.surfaceWeight(y)>=CycloneThermal.surfaceWeight(y-1));
    }
    @Test void fullStrengthChillAndExposedHeaterReachMatchSeverity() {
        assertEquals(1.4,CycloneThermal.windChillMultiplier(peak(),CycloneSeverity.MODERATE),1e-6);
        assertEquals(2.1,CycloneThermal.windChillMultiplier(peak(),CycloneSeverity.SEVERE),1e-6);
        assertEquals(3.2,CycloneThermal.windChillMultiplier(peak(),CycloneSeverity.CATASTROPHIC),1e-6);
        assertEquals(.5,CycloneThermal.exposedHeaterRadiusFactor(peak(),CycloneSeverity.MODERATE),1e-6);
        assertEquals(.4,CycloneThermal.exposedHeaterRadiusFactor(peak(),CycloneSeverity.SEVERE),1e-6);
        assertEquals(.3,CycloneThermal.exposedHeaterRadiusFactor(peak(),CycloneSeverity.CATASTROPHIC),1e-6);
    }
    @Test void thermalMassLosesMoreHeatInStormAndInsulationReducesThatCost() {
        double capacity=1000,baseline=com.frozendawn.thermal.RoomHeatMath.reservoirLoss(capacity,20,-80,.25,10);
        double storm=com.frozendawn.thermal.RoomHeatMath.reservoirLoss(capacity,20,-110,CycloneThermal.windFilmConductance(.25,1),10);
        double insulated=com.frozendawn.thermal.RoomHeatMath.reservoirLoss(capacity,20,-110,CycloneThermal.windFilmConductance(.025,1),10);
        assertTrue(storm>baseline*2);assertTrue(insulated<storm*.1);
    }
}

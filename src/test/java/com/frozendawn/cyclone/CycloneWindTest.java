package com.frozendawn.cyclone;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CycloneWindTest {
    static final CycloneSample PEAK=new CycloneSample(CycloneStage.PEAK,1,0,-30,895);
    @Test void noStormAndCompleteShelterAreExactIdentity() {
        var v=new Vec3(-.2,.37,.1);
        assertSame(v,CycloneWind.adjust(v,CycloneSample.NONE,CycloneSeverity.CATASTROPHIC,0,false,1,1,1));
        assertSame(v,CycloneWind.adjust(v,PEAK,CycloneSeverity.CATASTROPHIC,0,false,0,1,1));
    }
    @Test void directionalProjectionPreservesVerticalAndCrosswindMotion() {
        var v=new Vec3(-.2,.37,.1);var m=CycloneWind.adjust(v,PEAK,CycloneSeverity.CATASTROPHIC,0,false,1,1,1);
        assertEquals(-.2*.25+.04,m.x,1e-8);assertEquals(.37,m.y,0);assertEquals(.1,m.z,1e-8);
        var north=CycloneWind.adjust(new Vec3(.1,.37,.2),PEAK,CycloneSeverity.SEVERE,(float)(-Math.PI/2),true,1,1,1);
        assertEquals(.1,north.x,1e-7);assertEquals(.2*.45,north.z,1e-7);
    }
    @Test void crouchingStopsAddedDriftButKeepsHeadwindResistance() {
        assertEquals(Vec3.ZERO,CycloneWind.adjust(Vec3.ZERO,PEAK,CycloneSeverity.SEVERE,0,true,1,1,1));
        assertEquals(-.2*.45,CycloneWind.adjust(new Vec3(-.2,0,0),PEAK,CycloneSeverity.SEVERE,0,true,1,1,1).x,1e-7);
    }
    @Test void scaleZeroAndPartialArrivalDoNotAddUnboundedDrift() {
        assertEquals(Vec3.ZERO,CycloneWind.drift(PEAK,CycloneSeverity.CATASTROPHIC,0,false,1,1,0));
        var half=new CycloneSample(CycloneStage.ARRIVAL,.5f,0,-15,940);
        assertEquals(.02,CycloneWind.drift(half,CycloneSeverity.CATASTROPHIC,0,false,1,1,1).x,1e-8);
        assertEquals(.02,CycloneWind.drift(PEAK,CycloneSeverity.CATASTROPHIC,0,false,.5f,1,1).x,1e-8);
    }
    @Test void sprintBlockIsDirectionalAndRespectsSeverityCoverAndThreshold() {
        assertTrue(CycloneWind.blocksSprint(new Vec3(-1,0,0),PEAK,CycloneSeverity.SEVERE,0,1));
        assertFalse(CycloneWind.blocksSprint(new Vec3(1,0,0),PEAK,CycloneSeverity.SEVERE,0,1));
        assertFalse(CycloneWind.blocksSprint(new Vec3(0,0,1),PEAK,CycloneSeverity.CATASTROPHIC,0,1));
        assertFalse(CycloneWind.blocksSprint(new Vec3(-1,0,0),PEAK,CycloneSeverity.MODERATE,0,1));
        assertFalse(CycloneWind.blocksSprint(new Vec3(-1,0,0),PEAK,CycloneSeverity.SEVERE,0,0));
    }
    @Test void gustUsesSimulationTimeAndHasBoundedSmoothPeriodicMotion() {
        for(long seed:new long[]{Long.MIN_VALUE,-1,0,3,Long.MAX_VALUE}) {
            int period=60+(int)Math.floorMod(seed,81L);
            for(long t=0;t<2000;t++) {
                float g=CycloneWind.gust(t,seed);assertTrue(g>=.75f&&g<=1.25f);
                assertEquals(g,CycloneWind.gust(t+period,seed),1e-6);
                assertTrue(Math.abs(g-CycloneWind.gust(t+1,seed))<.03);
            }
        }
    }
    @Test void postTravelWindStillAllowsWalkingIntoTheStrongestGust() {
        // Ordinary stone ground: input acceleration0.1 and horizontal damping0.91*0.6.
        // The sprint guard caps forward input at0.79; vanilla travel precedes our Post hook.
        for(var severity:CycloneSeverity.values()) {
            double velocity=0,distance=0;
            for(int t=0;t<200;t++) {
                double moved=velocity-.1*.79;distance+=moved;
                velocity=CycloneWind.adjust(new Vec3(moved*.546,0,0),PEAK,severity,0,false,1,1.25f,1).x;
            }
            assertTrue(distance< -2,"Walking still makes headway at "+severity+": "+distance);
        }
    }
    @Test void playerYawProjectionMatchesVanillaAxes() {
        assertEquals(-1,CycloneWind.worldInput(0,1,90).x,1e-7);
        assertEquals(1,CycloneWind.worldInput(0,1,0).z,1e-7);
        assertEquals(1,CycloneWind.worldInput(1,0,0).x,1e-7);
    }
}

package com.frozendawn.oxygen;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FurnaceOxygenPolicyTest {
    @Test void transientOverloadAndUnknownSamplesNeverSpendTheGrace() {
        var p=new FurnaceOxygenPolicy();
        for(int t=0;t<200;t+=20)assertFalse(p.shouldShed(1,t,true,true));
        assertFalse(p.shouldShed(1,200,false,true));
        for(int t=220;t<420;t+=20)assertFalse(p.shouldShed(1,t,true,true));
        assertTrue(p.shouldShed(1,420,true,true));
        assertFalse(p.shouldShed(1,440,true,false));
        assertFalse(p.shouldShed(1,460,true,true));
    }
    @Test void unloadedTimeAndClockRollbackDoNotCauseInstantShutdownOrRestart() {
        var p=new FurnaceOxygenPolicy();
        assertFalse(p.shouldShed(1,0,true,true));
        assertFalse(p.shouldShed(1,20,true,true));
        assertFalse(p.shouldShed(1,1000,true,true));
        assertFalse(p.shouldShed(1,900,true,true));
        assertFalse(p.canResume(5,0,true));
        assertFalse(p.canResume(5,20,true));
        assertFalse(p.canResume(5,1000,true));
        assertFalse(p.canResume(5,1020,true));
        assertTrue(p.canResume(5,1040,true));
    }
    @Test void recoveryRequiresSustainedSpareCapacityAndTopologyChangesRearmTimers() {
        var p=new FurnaceOxygenPolicy();
        assertFalse(p.canResume(9,0,true));assertFalse(p.canResume(9,20,true));
        assertFalse(p.canResume(9,40,false));assertFalse(p.canResume(9,60,true));
        assertFalse(p.canResume(9,80,true));assertTrue(p.canResume(9,100,true));
        for(int t=0;t<=200;t+=20)p.shouldShed(1,t,true,true);
        p.reset();assertFalse(p.shouldShed(1,220,true,true));assertFalse(p.canResume(9,120,true));
    }
}

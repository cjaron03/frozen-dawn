package com.frozendawn.oxygen;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
class BreathingOxygenPolicyTest {
    @Test void graceThenAllFlamesFirst() {
        var p=new BreathingOxygenPolicy();
        for(int t=0;t<=300;t+=20) assertFalse(p.update(1,t,true,true,true,false,false));
        assertTrue(p.update(1,320,true,true,false,false,false));
    }
    @Test void breathingGraceIsTenSeconds() {
        var p=new BreathingOxygenPolicy();
        for(int t=0;t<200;t+=20) assertFalse(p.update(1,t,true,true,false,false,false));
        assertTrue(p.update(1,200,true,true,false,false,false));
    }
    @Test void unknownAndGapsNeverAccumulateGraceOrRestoreSavedLatch() {
        var p=new BreathingOxygenPolicy();
        p.update(1,0,true,true,false,false,false);p.update(1,180,true,true,false,false,false);
        assertFalse(p.update(1,200,true,true,false,false,false));
        assertTrue(p.update(2,0,false,false,false,true,true));
        assertTrue(p.update(2,200,true,false,false,true,true));
        assertTrue(p.update(2,220,true,false,false,true,true));
        assertFalse(p.update(2,240,true,false,false,true,true));
    }
    @Test void recoveryNeedsStableFullSupportAndDoesNotFlicker() {
        var p=new BreathingOxygenPolicy();
        assertTrue(p.update(1,0,true,false,false,true,true));
        assertTrue(p.update(1,20,true,false,false,false,true));
        assertTrue(p.update(1,40,true,false,false,true,true));
        assertTrue(p.update(1,60,true,false,false,true,true));
        assertFalse(p.update(1,80,true,false,false,true,true));
    }
    @Test void poolChangesRestartGraceButCannotCreateOxygen() {
        var p=new BreathingOxygenPolicy();
        for(int t=0;t<200;t+=20)p.update(1,t,true,true,false,false,false);
        p.resetPool(1);
        assertFalse(p.update(1,200,true,true,false,false,false));
        assertTrue(p.update(1,220,true,false,false,true,true));
    }
    @Test void independentPoolAndUnknownRecovery() {
        var p=new BreathingOxygenPolicy();
        p.update(1,0,true,false,false,true,true);p.update(2,0,true,false,false,true,true);
        assertTrue(p.update(1,20,false,false,false,true,true));
        p.update(2,20,true,false,false,true,true);
        assertFalse(p.update(2,40,true,false,false,true,true));
        assertTrue(p.update(1,40,true,false,false,true,true));
        p.retain(Set.of(2L));
        assertTrue(p.update(1,60,true,false,false,true,true));
    }
}

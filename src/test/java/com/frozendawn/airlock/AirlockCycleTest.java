package com.frozendawn.airlock;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class AirlockCycleTest {
    private static void finish(AirlockCycle c) {for(int i=0;i<80;i++)c.tick();}
    @Test void everySupportedVolumeConservesGasAndLosesExactlyTenPercent() {
        for(int cells=1;cells<=32;cells++) {
            var c=new AirlockCycle(cells,0);int capacity=c.capacity();
            assertEquals(capacity,c.fill(capacity));assertTrue(c.start(true));finish(c);
            assertTrue(c.breathable());assertEquals(0,c.reserve());
            assertTrue(c.start(false));finish(c);
            assertEquals(0,c.air());assertEquals(capacity*9/10,c.reserve());assertEquals(capacity/10,c.lost());
            assertFalse(c.start(true)); // The real loss must be replenished.
            c.fill(capacity/10);assertTrue(c.start(true));finish(c);assertTrue(c.breathable());
        }
    }
    @Test void reloadAtEveryTickPreservesExactAccounting() {
        for(boolean press:new boolean[]{true,false}) {
            var c=new AirlockCycle(27,press?0:2700);if(press)c.fill(2700);assertTrue(c.start(press));
            for(int i=0;i<80;i++) {var before=c.snapshot();c=AirlockCycle.restore(27,before);assertArrayEquals(before,c.snapshot());c.tick();}
            assertArrayEquals(c.snapshot(),AirlockCycle.restore(27,c.snapshot()).snapshot());
            assertEquals(press?2700:0,c.air());assertEquals(press?0:2430,c.reserve());
        }
    }
    @Test void overflowIsRealLossAndCanistersCannotOverfill() {
        var c=new AirlockCycle(32,3200);assertEquals(6400,c.fill(Integer.MAX_VALUE));assertEquals(0,c.fill(1));
        assertTrue(c.start(false));finish(c);assertEquals(3200,c.lost());assertEquals(6400,c.reserve());
        c.vent();assertEquals(6400,c.reserve());c.discardReserve();assertEquals(0,c.reserve());
    }
    @Test void interruptionDoesNotRefundOrCreateBreathablePartialAir() {
        var c=new AirlockCycle(12,0);c.fill(1200);c.start(true);for(int i=0;i<31;i++)c.tick();
        int air=c.air(),reserve=c.reserve();c.interrupt();assertFalse(c.breathable());
        assertEquals(air,c.air());assertEquals(reserve,c.reserve());assertEquals(1200,air+reserve);
        c=AirlockCycle.restore(12,c.snapshot());assertEquals(air,c.air());c.start(true);finish(c);
        assertTrue(c.breathable());assertEquals(0,c.reserve());
    }
    @Test void malformedSaveCannotResumeAnImpossibleCredit() {
        var c=new AirlockCycle(4,0);assertFalse(c.start(true));
        assertEquals(0,AirlockCycle.restore(4,new int[]{400,6401,400,0,0,0,0,0,0}).reserve());
        c.fill(400);c.start(true);var data=c.snapshot();data[6]=399;
        assertFalse(AirlockCycle.restore(4,data).cycling());
    }
    @Test void sealedResizeConservesUnitsAndChangesDensity() {
        var c=AirlockCycle.reconfigured(28,2700,1200);
        assertEquals(27.0/28,c.density(),1e-12);assertEquals(3900,c.air()+c.reserve());
        assertArrayEquals(c.snapshot(),AirlockCycle.restore(28,c.snapshot()).snapshot());
    }
    @Test void compressionAndReloadCannotDiscardOverpressureGas() {
        var c=AirlockCycle.reconfigured(26,2700,0);
        assertEquals(2700,c.air());assertTrue(c.density()>1);
        c=AirlockCycle.restore(26,c.snapshot());assertEquals(2700,c.air());assertFalse(c.start(true));assertTrue(c.start(false));
        finish(c);assertEquals(2430,c.reserve());assertEquals(270,c.lost());
    }
    @Test void measuredTargetsConserveGasAndResumeAtEveryTick() {
        for(int target:new int[]{730,2700,3300})for(boolean fill:new boolean[]{true,false}) {
            var c=AirlockCycle.reconfigured(27,fill?0:3700,fill?6400:0);
            int before=c.air()+c.reserve();assertTrue(c.startTo(target));
            for(int tick=0;tick<80;tick++) {
                var data=c.snapshot();c=AirlockCycle.restore(27,data);assertArrayEquals(data,c.snapshot());
                assertTrue(c.cycling());c.tick();
            }
            assertEquals(target,c.air());assertFalse(c.cycling());
            assertEquals(before,c.air()+c.reserve()+c.lost());
        }
    }
}

package com.frozendawn.airlock;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class AirlockPressureTest {
    @Test void vacuumNeverMatchesAnyPositivePressure() {
        assertTrue(AirlockManager.matchingPressure(0,0));
        for(double pressure:new double[]{1e-9,.001,.03,.05,.1,1,2}){
            assertFalse(AirlockManager.matchingPressure(0,pressure));
            assertFalse(AirlockManager.matchingPressure(pressure,0));
        }
    }
    @Test void toleranceScalesWithMeasuredPressureIncludingThinAirAndOverpressure() {
        for(double pressure:new double[]{1e-6,.01,.03,.5,1,2,8}){
            assertTrue(AirlockManager.matchingPressure(pressure,pressure));
            assertTrue(AirlockManager.matchingPressure(pressure,pressure*.95));
            assertTrue(AirlockManager.matchingPressure(pressure*.95,pressure));
            assertFalse(AirlockManager.matchingPressure(pressure,pressure*.94));
            assertFalse(AirlockManager.matchingPressure(pressure*.94,pressure));
        }
        assertFalse(AirlockManager.matchingPressure(.01,.03));
    }
    @Test void unknownAndNonfinitePressureCannotUnlockDoors() {
        for(double invalid:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}){
            assertFalse(AirlockManager.matchingPressure(invalid,1));
            assertFalse(AirlockManager.matchingPressure(1,invalid));
            assertFalse(AirlockManager.matchingPressure(invalid,invalid));
        }
    }
}

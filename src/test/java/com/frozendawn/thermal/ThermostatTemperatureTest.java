package com.frozendawn.thermal;
import com.frozendawn.world.TemperatureManager.LocalThermalTerms;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ThermostatTemperatureTest {
    @Test void localBonusAndTemperatureFloorsNeverInventCooling(){
        var core=new LocalThermalTerms(25,Double.NEGATIVE_INFINITY,0);
        assertEquals(-5,core.baseTarget(20));assertEquals(20,core.apply(core.baseTarget(20)));
        var vent=new LocalThermalTerms(10,24,3);assertEquals(Double.NEGATIVE_INFINITY,vent.baseTarget(20));assertEquals(27,vent.apply(-100));
        assertEquals(30,vent.apply(vent.baseTarget(30)));
    }
}

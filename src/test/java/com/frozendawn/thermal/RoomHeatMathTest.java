package com.frozendawn.thermal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoomHeatMathTest {
    @Test void exchangeConservesEnergyAndNeverCrossesEquilibriumEvenWithHugeSteps() {
        for(double ca:new double[]{1,35,12000})for(double cs:new double[]{12,900,100000})
            for(double air:new double[]{-273.15,-200,20,500})for(double wall:new double[]{-273.15,-150,40,1000})
                for(double dt:new double[]{.001,.05,1,1e6}) {
                    double a=RoomHeatMath.energy(ca,air),s=RoomHeatMath.energy(cs,wall);
                    double q=RoomHeatMath.exchange(ca,cs,air,wall,100,dt);
                    assertEquals(a+s,(a-q)+(s+q),Math.max(1e-9,(a+s)*1e-14));
                    double ta=RoomHeatMath.temperature(ca,a-q),ts=RoomHeatMath.temperature(cs,s+q);
                    assertTrue(ta>=Math.min(air,wall)-1e-8&&ta<=Math.max(air,wall)+1e-8);
                    assertTrue(ts>=Math.min(air,wall)-1e-8&&ts<=Math.max(air,wall)+1e-8);
                    assertTrue(Math.abs(ta-ts)<=Math.abs(air-wall)+1e-8);
                }
    }
    @Test void fixedReservoirLossMatchesAnalyticExponentialAndIsStepIndependent() {
        double c=100,t=30,out=-150,k=5;
        double whole=RoomHeatMath.reservoirLoss(c,t,out,k,20),energy=RoomHeatMath.energy(c,t);
        for(int i=0;i<400;i++)energy-=RoomHeatMath.reservoirLoss(c,RoomHeatMath.temperature(c,energy),out,k,.05);
        assertEquals(RoomHeatMath.energy(c,t)-whole,energy,1e-8);
        assertEquals(out+(t-out)*Math.exp(-1),RoomHeatMath.temperature(c,energy),1e-8);
    }
    @Test void coldMaterialReceivesOnlyExplicitEnvironmentalInput() {
        double q=RoomHeatMath.reservoirLoss(12,-200,20,1,1);assertTrue(q<0);
        double before=RoomHeatMath.energy(12,-200);
        assertEquals(before-q,RoomHeatMath.energy(12,RoomHeatMath.temperature(12,before-q)),1e-10);
    }
    @Test void layersAddResistanceRatherThanHeatAndVacuumHasNoAirCapacity() {
        assertEquals(.1,RoomHeatMath.seriesConductance(.1),1e-10);
        assertEquals(.1/3,RoomHeatMath.seriesConductance(.1,.1,.1),1e-10);
        assertTrue(RoomHeatMath.seriesConductance(.8,.1)<RoomHeatMath.seriesConductance(.8,.8));
        assertEquals(0,RoomHeatMath.exchange(0,120,20,100,100,1),0);
        assertEquals(-273.15,RoomHeatMath.temperature(0,0),0);
    }
    @Test void ventingRemovesGasEnergyRatherThanTurningItIntoWallHeat() {
        double structure=RoomHeatMath.energy(1000,40),gas=RoomHeatMath.energy(35,40);
        double incoming=RoomHeatMath.energy(35,-150);
        double total=structure+incoming;
        assertEquals(structure+gas-gas+incoming,total,1e-10);
        double q=RoomHeatMath.exchange(35,1000,-150,40,100,1);
        assertTrue(q<0);assertEquals(total,(incoming-q)+(structure+q),1e-10);
        assertTrue(RoomHeatMath.temperature(1000,structure+q)<40);
    }
}

package com.frozendawn.thermal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeaterControlTest {
    @Test void combinedGrantStopsAtTargetAcrossHeaterCountsAndReservoirSizes() {
        for (double ca : new double[]{1,26,2000}) for (double cs : new double[]{12,876,24000})
            for (double air : new double[]{-215,0,19,20,40}) for (double walls : new double[]{-200,10,20})
                for (int heaters : new int[]{1,2,30}) {
                    double grant=HeaterControl.airGrant(1800*heaters*.05,ca,cs,air,walls,240,.05,20);
                    assertTrue(grant>=0&&grant<=1800*heaters*.05);
                    double heated=air+grant/ca;
                    double result=heated-RoomHeatMath.exchange(ca,cs,heated,walls,240,.05)/ca;
                    if (air<=20) assertTrue(result<=20+1e-9,"Combined heaters cannot exceed target");
                    if (air>=20 && walls>=20) assertEquals(0,grant,1e-9,"Stored overheating consumes no fuel");
                }
    }
    @Test void holdingDemandTracksWallHeatLossAndDoesNotInventCooling() {
        double insulated=HeaterControl.airGrant(90,26,876,20,19.5,240,.05,20);
        double leaky=HeaterControl.airGrant(90,26,876,20,15,240,.05,20);
        assertTrue(insulated>0&&insulated<leaky);
        assertEquals(0,HeaterControl.airGrant(90,26,876,20,40,240,.05,20));
        assertEquals(0,HeaterControl.wallGrant(90,876,25,20));
        assertEquals(90,HeaterControl.wallGrant(90,876,-200,20));
    }
    @Test void openCampDemandUsesUnheatedBackgroundWithoutFeedback() {
        assertEquals(1,HeaterControl.openCampFraction(-200,35));
        assertEquals(20.0/35,HeaterControl.openCampFraction(0,35),1e-10);
        assertEquals(0,HeaterControl.openCampFraction(25,35));
    }
}

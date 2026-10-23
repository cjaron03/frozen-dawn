package com.frozendawn.phase;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhaseOneWeatherPolicyTest {
    @Test void windowsGraduallyGetWetterWithoutLeavingApprovedRanges() {
        for (int step=0; step<=100; step++) {
            float progress=0.05F+0.07F*step/100;
            for (int sample=0; sample<100; sample++) {
                double roll=PhaseOneWeatherPolicy.roll(471,sample,31);
                int clear=PhaseOneWeatherPolicy.durationTicks(progress,false,roll);
                int rain=PhaseOneWeatherPolicy.durationTicks(progress,true,roll);
                assertTrue(clear>=4*1200 && clear<=12*1200);
                assertTrue(rain>=2*1200 && rain<=6*1200);
                if(step<100) {
                    float later=0.05F+0.07F*(step+1)/100;
                    assertTrue(PhaseOneWeatherPolicy.durationTicks(later,false,roll)<=clear);
                    assertTrue(PhaseOneWeatherPolicy.durationTicks(later,true,roll)>=rain);
                }
            }
        }
        assertEquals(9600,PhaseOneWeatherPolicy.durationTicks(.05F,false,0));
        assertEquals(14400,PhaseOneWeatherPolicy.durationTicks(.05F,false,1));
        assertEquals(4800,PhaseOneWeatherPolicy.durationTicks(.12F,false,0));
        assertEquals(8400,PhaseOneWeatherPolicy.durationTicks(.12F,false,1));
    }
    @Test void thunderIsRareAndTheSavedSequenceIsReproducible() {
        int storms=0;
        for(int i=0;i<10000;i++) {
            double roll=PhaseOneWeatherPolicy.roll(77371337,i,0x5448554e444552L);
            assertEquals(roll,PhaseOneWeatherPolicy.roll(77371337,i,0x5448554e444552L));
            if(PhaseOneWeatherPolicy.thunder(roll))storms++;
        }
        assertTrue(storms>400 && storms<600,"approximately five percent of rain windows");
    }
    @Test void clearWeatherIsQuietAndRainGustsAreSoftAndContinuous() {
        for(int tick=0;tick<1600;tick++) {
            assertEquals(0,PhaseOneWeatherPolicy.windVolume(.1F,0,tick));
            float volume=PhaseOneWeatherPolicy.windVolume(.1F,1,tick);
            assertTrue(volume>0 && volume<.2F);
            assertTrue(Math.abs(volume-PhaseOneWeatherPolicy.windVolume(.1F,1,tick+1))<.002F);
        }
        assertTrue(PhaseOneWeatherPolicy.windVolume(.1F,1,400)>PhaseOneWeatherPolicy.windVolume(.1F,1,0)*3);
    }
}

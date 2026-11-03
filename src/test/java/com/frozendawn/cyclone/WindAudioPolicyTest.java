package com.frozendawn.cyclone;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WindAudioPolicyTest {
    static CycloneSample peak(){return new CycloneSample(CycloneStage.PEAK,1,0,-19,933);}
    @Test void normalAndStormCrossfadeHaveConstantPowerAndExactEndpoints() {
        for(int i=0;i<=1000;i++){float f=i/1000f,a=WindAudioPolicy.normalWeight(f),b=WindAudioPolicy.stormWeight(f);assertEquals(1,a*a+b*b,1e-6);}
        assertEquals(1,WindAudioPolicy.normalWeight(0));assertEquals(0,WindAudioPolicy.stormWeight(0));
        assertEquals(1,WindAudioPolicy.stormWeight(1));
    }
    @Test void consecutiveClipsHaveNoGapOrGainSpikeAtTheirOverlap() {
        int duration=1280,overlap=120,start=duration-overlap;
        for(int t=start;t<duration;t++) {
            float a=WindAudioPolicy.envelope(t,duration,overlap),b=WindAudioPolicy.envelope(t-start,duration,overlap);
            assertEquals(1,a*a+b*b,1e-6,"overlap tick "+t);
        }
        assertEquals(0,WindAudioPolicy.envelope(0,duration,overlap));
        assertEquals(0,WindAudioPolicy.envelope(duration,duration,overlap));
    }
    @Test void abruptPeakEndAndShelterChangesSlewRatherThanCuttingAudio() {
        var mix=new WindAudioPolicy.Mix();float old=mix.normal();
        for(int i=0;i<120;i++){mix.tick(peak(),CycloneSeverity.SEVERE,1);assertTrue(Math.abs(old-mix.normal())<.02);old=mix.normal();}
        assertTrue(mix.roar()>.5);float outside=mix.snow();
        mix.tick(peak(),CycloneSeverity.SEVERE,0);assertTrue(mix.snow()>outside*.95);
        for(int i=0;i<120;i++)mix.tick(peak(),CycloneSeverity.SEVERE,0);
        assertEquals(0,mix.snow(),1e-6);assertTrue(mix.room()>.5);
        for(int i=0;i<240;i++)mix.tick(CycloneSample.NONE,CycloneSeverity.SEVERE,1);
        assertEquals(1,mix.normal());assertEquals(0,mix.snow(),1e-6);assertEquals(0,mix.roar(),1e-6);assertEquals(0,mix.room(),1e-6);
    }
    @Test void falseCalmIsQuietButDoesNotBecomeADeadSilentGap() {
        var mix=new WindAudioPolicy.Mix();var calm=new CycloneSample(CycloneStage.FALSE_CALM,0,1,15,1000);
        for(int i=0;i<180;i++)mix.tick(calm,CycloneSeverity.MODERATE,1);
        assertEquals(.1,mix.normal(),1e-6);assertTrue(mix.roar()>0);assertEquals(0,mix.snow());
    }
}

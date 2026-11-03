package com.frozendawn.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ColdBreathPolicyTest {
    @Test void warmthCreativeSubmersionAndVacuumSuppressBreath() {
        assertTrue(ColdBreathPolicy.visible(-.1F,false,false,false));
        assertFalse(ColdBreathPolicy.visible(0,false,false,false));
        assertFalse(ColdBreathPolicy.visible(-10,true,false,false));
        assertFalse(ColdBreathPolicy.visible(-10,false,true,false));
        assertFalse(ColdBreathPolicy.visible(-10,false,false,true));
        assertFalse(ColdBreathPolicy.visible(Float.NaN,false,false,false));
    }
    @Test void wispsDevelopAndDissolveWithoutAPopOrOpaqueCloud() {
        assertEquals(0,ColdBreathPolicy.opacity(0));
        assertEquals(0,ColdBreathPolicy.opacity(1));
        for(int i=0;i<=100;i++) {
            float life=i/100F;
            assertTrue(ColdBreathPolicy.opacity(life)>=0 && ColdBreathPolicy.opacity(life)<=.30F);
            assertTrue(ColdBreathPolicy.size(life)>=.035F && ColdBreathPolicy.size(life)<=.156F);
            if(i>0)assertTrue(Math.abs(ColdBreathPolicy.opacity(life)-ColdBreathPolicy.opacity(life-.01F))<.025F);
        }
        // The wisp remains visible into its tail and becomes negligible before removal.
        assertTrue(ColdBreathPolicy.opacity(.7F)>.05F);
        assertTrue(ColdBreathPolicy.opacity(.7F)<ColdBreathPolicy.opacity(.2F));
        assertTrue(ColdBreathPolicy.opacity(.9F)<.03F);
        assertTrue(ColdBreathPolicy.opacity(.99F)<.001F);
        float previous=ColdBreathPolicy.opacity(.24F);
        for(int i=25;i<=100;i++) {
            float alpha=ColdBreathPolicy.opacity(i/100F);
            assertTrue(alpha<=previous, "Dissolution must not brighten again");
            previous=alpha;
        }
        assertTrue(ColdBreathPolicy.interval(-40)<ColdBreathPolicy.interval(-1));
        assertTrue(ColdBreathPolicy.wisps(-40)>ColdBreathPolicy.wisps(-1));
    }
}

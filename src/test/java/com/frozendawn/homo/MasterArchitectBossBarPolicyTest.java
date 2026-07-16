package com.frozendawn.homo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterArchitectBossBarPolicyTest {

    @Test
    void onlyProvocationRevealsTheBar() {
        assertFalse(MasterArchitectBossBarPolicy.shouldReveal(false, false));
        assertTrue(MasterArchitectBossBarPolicy.shouldReveal(true, false));
        assertTrue(MasterArchitectBossBarPolicy.shouldReveal(false, true));
    }

    @Test
    void healthProgressIncludesHealingAndStaysBounded() {
        assertEquals(0.25F, MasterArchitectBossBarPolicy.progress(75.0F, 300.0F));
        assertEquals(0.50F, MasterArchitectBossBarPolicy.progress(150.0F, 300.0F));
        assertEquals(1.0F, MasterArchitectBossBarPolicy.progress(400.0F, 300.0F));
        assertEquals(0.0F, MasterArchitectBossBarPolicy.progress(-1.0F, 300.0F));
        assertEquals(0.0F, MasterArchitectBossBarPolicy.progress(10.0F, 0.0F));
    }
}

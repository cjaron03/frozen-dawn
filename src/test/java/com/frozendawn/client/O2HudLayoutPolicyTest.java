package com.frozendawn.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class O2HudLayoutPolicyTest {

    @Test
    void usesNormalRowWhenVanillaAirMeterIsHidden() {
        assertEquals(191, O2HudLayoutPolicy.baseY(240, false));
    }

    @Test
    void stacksAboveVanillaAirMeterWhileItIsVisible() {
        assertEquals(181, O2HudLayoutPolicy.baseY(240, true));
    }
}

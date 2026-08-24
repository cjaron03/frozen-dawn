package com.frozendawn.client;

/** Keeps Frozen Dawn's O2 row clear of vanilla survival HUD rows. */
public final class O2HudLayoutPolicy {

    static final int DEFAULT_BOTTOM_OFFSET = 49;
    static final int VANILLA_AIR_ROW_HEIGHT = 10;

    private O2HudLayoutPolicy() {
    }

    public static int baseY(int screenHeight, boolean vanillaAirMeterVisible) {
        return screenHeight
                - DEFAULT_BOTTOM_OFFSET
                - (vanillaAirMeterVisible ? VANILLA_AIR_ROW_HEIGHT : 0);
    }
}

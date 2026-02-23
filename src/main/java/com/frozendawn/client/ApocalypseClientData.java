package com.frozendawn.client;

import com.frozendawn.network.ApocalypseDataPayload;

/**
 * Client-side cache of apocalypse state received from the server.
 * Updated via network packets; read by rendering classes.
 */
public final class ApocalypseClientData {

    private static int phase = 0;
    private static float progress = 0f;
    private static float tempOffset = 0f;
    private static float sunScale = 1f;
    private static float sunBrightness = 1f;
    private static float skyLight = 1f;

    private ApocalypseClientData() {}

    public static void update(ApocalypseDataPayload payload) {
        phase = payload.phase();
        progress = payload.progress();
        tempOffset = payload.tempOffset();
        sunScale = payload.sunScale();
        sunBrightness = payload.sunBrightness();
        skyLight = payload.skyLight();
    }

    public static int getPhase() { return phase; }
    public static float getProgress() { return progress; }
    public static float getTempOffset() { return tempOffset; }
    public static float getSunScale() { return sunScale; }
    public static float getSunBrightness() { return sunBrightness; }
    public static float getSkyLight() { return skyLight; }
}

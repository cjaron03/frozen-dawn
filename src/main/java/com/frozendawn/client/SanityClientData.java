package com.frozendawn.client;

/**
 * Client-side cache of the player's sanity stage received from the server.
 * Updated via SanityStagePayload; read by SanityEffects for rendering.
 */
public final class SanityClientData {

    private static int stage = 0;

    private SanityClientData() {}

    public static void setStage(int s) { stage = s; }
    public static int getStage() { return stage; }

    public static void reset() { stage = 0; }
}

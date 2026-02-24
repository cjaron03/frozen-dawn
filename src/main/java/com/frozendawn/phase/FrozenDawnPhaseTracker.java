package com.frozendawn.phase;

/**
 * Simple shared phase tracker readable from both client and server threads.
 * Updated by WorldTickHandler (server) and ApocalypseClientData (client).
 * Used by the Biome mixin to force snow precipitation in phase 3+.
 */
public final class FrozenDawnPhaseTracker {

    private static volatile int currentPhase = 0;

    private FrozenDawnPhaseTracker() {}

    public static void setPhase(int phase) {
        currentPhase = phase;
    }

    public static int getPhase() {
        return currentPhase;
    }
}

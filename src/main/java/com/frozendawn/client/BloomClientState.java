package com.frozendawn.client;

import com.frozendawn.bloom.BloomBand;
import com.frozendawn.network.BloomStatePayload;

public final class BloomClientState {
    private static float density;
    private static BloomBand band = BloomBand.FRONTIER;

    private BloomClientState() {
    }

    public static void update(BloomStatePayload payload) {
        density = Math.max(0.0F, Math.min(1.0F, payload.density()));
        int ordinal = Math.max(0, Math.min(BloomBand.values().length - 1, payload.band()));
        band = BloomBand.values()[ordinal];
    }

    public static float density() {
        return density;
    }

    public static BloomBand band() {
        return band;
    }

    public static float windMultiplier() {
        if (band != BloomBand.CORE) {
            return 1.0F;
        }
        return Math.max(0.0F, 1.0F - density * 1.6F);
    }

    public static void reset() {
        density = 0.0F;
        band = BloomBand.FRONTIER;
    }
}

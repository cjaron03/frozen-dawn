package com.frozendawn.client;

/** Low, broad swells for the persistent cold of phase 2, in every biome and at night too. */
final class PhaseTwoWindPolicy {
    private PhaseTwoWindPolicy() { }

    static float gain(int phase, float progress, long gameTime, float exposure, boolean suppressed) {
        if (phase != 2 || suppressed || !Float.isFinite(progress) || !Float.isFinite(exposure)) return 0;
        float local = Math.clamp((progress - .12F) / .10F, 0, 1);
        double swell = Math.sin(Math.floorMod(gameTime, 1100L) * Math.PI * 2 / 1100.0);
        float outside = .26F + .04F * local + .025F * (float) swell;
        return outside * (.07F + .93F * Math.clamp(exposure, 0, 1));
    }
}

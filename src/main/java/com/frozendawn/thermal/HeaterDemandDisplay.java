package com.frozendawn.thermal;

/** Display-only demand averaging. Never used by heat grants or fuel debits. */
public final class HeaterDemandDisplay {
    private static final double RESPONSE_TICKS = 100.0;
    private long lastTick = Long.MIN_VALUE;
    private int lastMode = -1;
    private boolean wasEnabled;
    private double fraction;

    public double sample(long tick, double actual, boolean enabled, int mode) {
        actual = Double.isFinite(actual) ? Math.clamp(actual, 0, 1) : 0;
        if (!enabled) {
            fraction = 0;
        } else if (!wasEnabled || lastMode != mode || tick < lastTick) {
            fraction = actual;
        } else if (tick > lastTick) {
            double weight = -Math.expm1(-(tick - lastTick) / RESPONSE_TICKS);
            fraction += weight * (actual - fraction);
        }
        lastTick = tick;
        lastMode = mode;
        wasEnabled = enabled;
        return fraction;
    }
}

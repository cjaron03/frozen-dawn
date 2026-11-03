package com.frozendawn.data;

/** Debounced ambient intake; any unsafe observation closes it on that tick. */
public final class EmergencyEvaAirIntake {
    public static final int STABLE_TICKS = 2 * 20;
    private int stableTicks;
    public EmergencyEvaAirIntake(int stableTicks) {
        this.stableTicks = Math.clamp(stableTicks, 0, STABLE_TICKS);
    }
    public int stableTicks() { return stableTicks; }
    public boolean isOpen() { return stableTicks == STABLE_TICKS; }
    public void tick(boolean breathable) {
        stableTicks = breathable ? Math.min(STABLE_TICKS, stableTicks + 1) : 0;
    }
}

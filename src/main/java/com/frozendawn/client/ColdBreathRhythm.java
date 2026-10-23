package com.frozendawn.client;

/** Client-only exhale timing; suppression and logout discard pending breath and exertion. */
final class ColdBreathRhythm {
    private float cooldown;
    private int exhaleTicks;
    private int exertionTicks;

    void reset() {
        cooldown = 0;
        exhaleTicks = 0;
        exertionTicks = 0;
    }

    float exertion() { return exertionTicks / 80F; }

    boolean tick(float temperature, boolean visible, boolean running) {
        if (!visible) {
            reset();
            return false;
        }
        // Build over one second of running and recover over four seconds of ordinary play.
        exertionTicks = Math.clamp(exertionTicks + (running ? 4 : -1), 0, 80);
        cooldown -= ColdBreathPolicy.cadence(exertion());
        if (cooldown <= 0) {
            exhaleTicks = ColdBreathPolicy.wisps(temperature, exertion()) * 2;
            cooldown += ColdBreathPolicy.interval(temperature);
        }
        return exhaleTicks > 0 && exhaleTicks-- % 2 == 0;
    }
}

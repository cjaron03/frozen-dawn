package com.frozendawn.client;

/** Roof-edge audio easing for ordinary phase 1; advances only during unpaused client ticks. */
final class PhaseOneWindExposure {
    private boolean initialized;
    private float position;

    float tick(boolean exposed) {
        if (!initialized) {
            position = exposed ? 1 : 0;
            initialized = true;
        } else if (exposed) {
            position = Math.min(1, position + 1F / 40);
        } else {
            position = Math.max(0, position - 1F / 50);
        }
        return position * position * (3 - 2 * position);
    }

    void reset() {
        initialized = false;
        position = 0;
    }
}

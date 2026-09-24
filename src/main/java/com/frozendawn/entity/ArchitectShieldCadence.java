package com.frozendawn.entity;

import java.util.function.DoubleSupplier;

/** Local, bounded combat choices. No attack, item-selection or belief inputs. */
final class ArchitectShieldCadence {
    static final int GUARD_TICKS = 35, OPEN_TICKS = 25, STAGGER_TICKS = 20, DECISION_TICKS = 10;
    static final double RAISE_RANGE = 4.5, LOWER_RANGE = 6;
    private boolean guarding, guardedOnce, skippedLast;
    private long guardUntil, exposedUntil, nextDecision;

    boolean tick(long now, boolean visible, double distanceSquared, DoubleSupplier random) {
        if (guarding) {
            if (now < guardUntil && visible && distanceSquared <= LOWER_RANGE * LOWER_RANGE) return true;
            guarding = false;
            exposedUntil = now + OPEN_TICKS;
            nextDecision = exposedUntil;
        }
        if (now < exposedUntil || now < nextDecision) return false;
        nextDecision = now + DECISION_TICKS;
        if (!visible || distanceSquared > RAISE_RANGE * RAISE_RANGE) return false;
        // Sometimes choose another attack window, but never skip two in a row.
        if (guardedOnce && !skippedLast && random.getAsDouble() < .3) {
            skippedLast = true;
            exposedUntil = now + OPEN_TICKS;
            nextDecision = exposedUntil;
            return false;
        }
        guarding = true; guardedOnce = true; skippedLast = false;
        guardUntil = now + GUARD_TICKS;
        return true;
    }

    void stagger(long now) {
        guarding = false;
        exposedUntil = Math.max(exposedUntil, now + STAGGER_TICKS);
        nextDecision = exposedUntil;
    }

    void clear() {
        guarding = guardedOnce = skippedLast = false;
        guardUntil = exposedUntil = nextDecision = 0;
    }
}

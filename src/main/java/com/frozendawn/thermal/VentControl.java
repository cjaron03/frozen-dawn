package com.frozendawn.thermal;

/** Half-degree hysteresis; the shared room budget stops cooling at target. */
public final class VentControl {
    private VentControl() {}
    public static boolean demand(boolean open,double temperature,double target) {
        return temperature>target+.5||open&&temperature>target;
    }
}

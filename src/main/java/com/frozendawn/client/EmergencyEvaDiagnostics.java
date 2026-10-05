package com.frozendawn.client;

/** Sparse, state-driven diagnostics; callers acknowledge only messages actually displayed. */
final class EmergencyEvaDiagnostics {
    private static final int MESSAGE_GAP = 35 * 20;
    private static final int HALF_SERVICE = 5 * 60 * 20;
    private static final int LOW_RESERVE = 2 * 60 * 20;
    private static final int CRITICAL_RESERVE = 60 * 20;
    private int cooldown;
    private boolean halfReported;
    private boolean lowReported;
    private boolean criticalReported;
    private boolean moistureReported;
    private boolean sealReported;
    private boolean exertionReported;
    private boolean coolingReported;
    private boolean thermalReported;

    enum Message {
        SEAL_OPEN("seal_open"), CRITICAL("reserve_critical"), LOW("reserve_low"),
        SERVICE("service_low"), EXERTION("exertion"), MOISTURE("visor_moisture"),
        COOLING("cooling_degraded"), THERMAL("thermal_high");
        private final String key;
        Message(String suffix) { key = "ui.frozendawn.suit.emergency_eva_" + suffix; }
        String key() { return key; }
    }

    void reset(int reserveTicks) {
        reset(reserveTicks, com.frozendawn.data.EmergencyEvaThermal.COOLANT_LIFE_TICKS);
    }

    void reset(int reserveTicks, int wornTicks) {
        // Do not dump historical threshold messages when rejoining a spent issue.
        halfReported = reserveTicks <= HALF_SERVICE;
        lowReported = reserveTicks <= LOW_RESERVE;
        criticalReported = reserveTicks <= CRITICAL_RESERVE;
        moistureReported = false;
        sealReported = false;
        exertionReported = false;
        coolingReported = wornTicks >= com.frozendawn.data.EmergencyEvaThermal.COOLANT_LIFE_TICKS;
        thermalReported = false;
        cooldown = 15 * 20;
    }

    void tick(boolean sealed) {
        tick(sealed, 0);
    }

    void tick(boolean sealed, int thermalLoad) {
        if (cooldown > 0) cooldown--;
        if (sealed) sealReported = false;
        // A repeat heat warning requires genuine recovery below half the high
        // threshold, avoiding threshold chatter during a continuous hot spell.
        if (thermalLoad < com.frozendawn.data.EmergencyEvaThermal.HIGH_HEAT / 2) thermalReported = false;
    }

    Message pending(int reserveTicks, boolean sealed, boolean visibleMoisture) {
        return pending(reserveTicks, sealed, visibleMoisture, false);
    }

    Message pending(int reserveTicks, boolean sealed, boolean visibleMoisture, boolean highDraw) {
        return pending(reserveTicks, sealed, visibleMoisture, highDraw, false, false);
    }

    Message pending(int reserveTicks, boolean sealed, boolean visibleMoisture, boolean highDraw,
                    boolean coolingDegraded, boolean highHeat) {
        // Heat can rise faster than routine maintenance messages. A newly high
        // load may bypass their gap, but never displaces a reserve emergency.
        if (reserveTicks <= 0 || cooldown > 0 && !(sealed && highHeat && !thermalReported)) return null;
        if (!sealed && !sealReported) return Message.SEAL_OPEN;
        if (!sealed) return null;
        if (reserveTicks <= CRITICAL_RESERVE && !criticalReported) return Message.CRITICAL;
        if (reserveTicks <= LOW_RESERVE && !lowReported) return Message.LOW;
        if (highHeat && !thermalReported) return Message.THERMAL;
        if (coolingDegraded && !coolingReported) return Message.COOLING;
        if (reserveTicks <= HALF_SERVICE && !halfReported) return Message.SERVICE;
        if (highDraw && !exertionReported) return Message.EXERTION;
        if (visibleMoisture && !moistureReported) return Message.MOISTURE;
        return null;
    }

    void acknowledge(Message message) {
        switch (message) {
            case SEAL_OPEN -> sealReported = true;
            case CRITICAL -> { criticalReported = true; lowReported = true; halfReported = true; }
            case LOW -> { lowReported = true; halfReported = true; }
            case SERVICE -> halfReported = true;
            case EXERTION -> exertionReported = true;
            case MOISTURE -> moistureReported = true;
            case COOLING -> coolingReported = true;
            case THERMAL -> thermalReported = true;
        }
        cooldown = MESSAGE_GAP;
    }
}

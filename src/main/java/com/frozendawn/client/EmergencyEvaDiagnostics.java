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

    enum Message {
        SEAL_OPEN("seal_open"), CRITICAL("reserve_critical"), LOW("reserve_low"),
        SERVICE("service_low"), MOISTURE("visor_moisture");
        private final String key;
        Message(String suffix) { key = "ui.frozendawn.suit.emergency_eva_" + suffix; }
        String key() { return key; }
    }

    void reset(int reserveTicks) {
        // Do not dump historical threshold messages when rejoining a spent issue.
        halfReported = reserveTicks <= HALF_SERVICE;
        lowReported = reserveTicks <= LOW_RESERVE;
        criticalReported = reserveTicks <= CRITICAL_RESERVE;
        moistureReported = false;
        sealReported = false;
        cooldown = 15 * 20;
    }

    void tick(boolean sealed) {
        if (cooldown > 0) cooldown--;
        if (sealed) sealReported = false;
    }

    Message pending(int reserveTicks, boolean sealed, boolean visibleMoisture) {
        if (reserveTicks <= 0 || cooldown > 0) return null;
        if (!sealed && !sealReported) return Message.SEAL_OPEN;
        if (!sealed) return null;
        if (reserveTicks <= CRITICAL_RESERVE && !criticalReported) return Message.CRITICAL;
        if (reserveTicks <= LOW_RESERVE && !lowReported) return Message.LOW;
        if (reserveTicks <= HALF_SERVICE && !halfReported) return Message.SERVICE;
        if (visibleMoisture && !moistureReported) return Message.MOISTURE;
        return null;
    }

    void acknowledge(Message message) {
        switch (message) {
            case SEAL_OPEN -> sealReported = true;
            case CRITICAL -> { criticalReported = true; lowReported = true; halfReported = true; }
            case LOW -> { lowReported = true; halfReported = true; }
            case SERVICE -> halfReported = true;
            case MOISTURE -> moistureReported = true;
        }
        cooldown = MESSAGE_GAP;
    }
}

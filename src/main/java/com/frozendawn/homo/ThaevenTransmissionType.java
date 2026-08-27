package com.frozendawn.homo;

/**
 * First-contact Thaeven fragments selected by the Architect's assessment.
 */
public enum ThaevenTransmissionType {
    VEL_THAE(0, 180),
    ORSHA_RECOGNITION(1, 220),
    HEARTH_MYTH(2, 300);

    private final int networkId;
    private final int durationTicks;

    ThaevenTransmissionType(int networkId, int durationTicks) {
        this.networkId = networkId;
        this.durationTicks = durationTicks;
    }

    public int networkId() {
        return networkId;
    }

    public int durationTicks() {
        return durationTicks;
    }

    public int minimumCompletionTicks() {
        return Math.max(1, durationTicks - 20);
    }

    public static ThaevenTransmissionType fromAssessment(boolean orsaDetected) {
        return orsaDetected ? ORSHA_RECOGNITION : VEL_THAE;
    }

    public static ThaevenTransmissionType fromNetworkId(int networkId) {
        for (ThaevenTransmissionType type : values()) {
            if (type.networkId == networkId) {
                return type;
            }
        }
        return VEL_THAE;
    }
}

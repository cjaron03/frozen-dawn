package com.frozendawn.cyclone;

public final class CycloneVisuals {
    /** Immutable server visibility snapshot, independent of a client's local common config. */
    public record Peaks(float moderate, float severe, float catastrophic) {
        public static final Peaks DEFAULT = new Peaks(8, 4, 2);
        public Peaks {
            for (float value : new float[]{moderate, severe, catastrophic})
                if (!Float.isFinite(value) || value < 1 || value > 128)
                    throw new IllegalArgumentException("Cyclone visibility must be finite and within 1..128 blocks");
        }
        public float forSeverity(CycloneSeverity severity) {
            return switch (severity) {
                case MODERATE -> moderate;
                case SEVERE -> severe;
                case CATASTROPHIC -> catastrophic;
            };
        }
    }

    private CycloneVisuals() {}
    public static float visibility(CycloneSample sample, CycloneSeverity severity, float baseline, boolean goggles) {
        return visibility(sample, severity.visibility, severity.gogglesVisibility, baseline, goggles);
    }
    public static float visibility(CycloneSample sample, float peak, float gogglesPeak, float baseline, boolean goggles) {
        if (sample.stage() == CycloneStage.NONE) return baseline;
        float calm = baseline + (56 - baseline) * sample.calm();
        // Increasing unassisted visibility must never make wearing goggles a disadvantage.
        float target = goggles ? Math.max(peak, gogglesPeak) : peak;
        return calm + (target - calm) * sample.intensity();
    }
}

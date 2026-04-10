package com.frozendawn.barometer;

public final class PhaseBarometerForecasts {

    private static final float[] PHASE_STARTS = {0.0f, 0.05f, 0.12f, 0.22f, 0.34f, 0.46f, 0.60f};
    private static final float[] PHASE_ENDS = {0.05f, 0.12f, 0.22f, 0.34f, 0.46f, 0.60f, 1.00f};
    private static final float PHASE6_START = 0.60f;
    private static final float PHASE6_MID_START = 0.72f;
    private static final float PHASE6_VACUUM_START = 0.85f;
    private static final float STABLE_THRESHOLD = 0.40f;
    private static final float DETERIORATING_THRESHOLD = 0.70f;
    private static final float TRANSITION_THRESHOLD = 0.88f;

    private PhaseBarometerForecasts() {
    }

    public static PhaseBarometerSnapshot evaluate(int phase, float progress) {
        int clampedPhase = clamp(phase, 0, 6);
        float clampedProgress = clamp(progress, 0.0f, 1.0f);

        if (clampedPhase < 6) {
            float localProgress = normalize(clampedProgress, PHASE_STARTS[clampedPhase], PHASE_ENDS[clampedPhase]);
            ForecastBand band = bandForLocalProgress(localProgress);
            return new PhaseBarometerSnapshot(
                    clampedPhase,
                    phaseName(clampedPhase),
                    band,
                    nextPhaseState(clampedPhase),
                    warningFor(clampedPhase, band),
                    localProgress
            );
        }

        if (clampedProgress < PHASE6_MID_START) {
            float localProgress = normalize(clampedProgress, PHASE6_START, PHASE6_MID_START);
            ForecastBand band = bandForLocalProgress(localProgress);
            return new PhaseBarometerSnapshot(
                    6,
                    phaseName(6),
                    band,
                    UpcomingState.ATMOSPHERIC_THINNING,
                    BarometerWarning.ATMOSPHERIC_THINNING_DETECTED,
                    localProgress
            );
        }
        if (clampedProgress < PHASE6_VACUUM_START) {
            float localProgress = normalize(clampedProgress, PHASE6_MID_START, PHASE6_VACUUM_START);
            ForecastBand band = bandForLocalProgress(localProgress);
            return new PhaseBarometerSnapshot(
                    6,
                    phaseName(6),
                    band,
                    UpcomingState.VACUUM_ONSET,
                    BarometerWarning.VACUUM_CONDITIONS_APPROACHING,
                    localProgress
            );
        }

        return new PhaseBarometerSnapshot(
                6,
                phaseName(6),
                ForecastBand.COLLAPSE_UNDERWAY,
                UpcomingState.TERMINAL_CONDITIONS,
                BarometerWarning.VACUUM_CONDITIONS_ACTIVE,
                1.0f
        );
    }

    public static String phaseName(int phase) {
        return switch (phase) {
            case 0 -> "Normal";
            case 1 -> "Twilight";
            case 2 -> "Cooling";
            case 3 -> "The Long Night";
            case 4 -> "Deep Freeze";
            case 5 -> "Eternal Winter";
            default -> "Atmospheric Collapse";
        };
    }

    public static ForecastBand bandForLocalProgress(float localProgress) {
        if (localProgress < STABLE_THRESHOLD) {
            return ForecastBand.STABLE;
        }
        if (localProgress < DETERIORATING_THRESHOLD) {
            return ForecastBand.DETERIORATING;
        }
        if (localProgress < TRANSITION_THRESHOLD) {
            return ForecastBand.TRANSITION_LIKELY_SOON;
        }
        return ForecastBand.IMMINENT;
    }

    public static float normalize(float value, float min, float max) {
        if (max <= min) {
            return 1.0f;
        }
        return clamp((value - min) / (max - min), 0.0f, 1.0f);
    }

    private static UpcomingState nextPhaseState(int phase) {
        return switch (phase) {
            case 0 -> UpcomingState.PHASE_1;
            case 1 -> UpcomingState.PHASE_2;
            case 2 -> UpcomingState.PHASE_3;
            case 3 -> UpcomingState.PHASE_4;
            case 4 -> UpcomingState.PHASE_5;
            default -> UpcomingState.PHASE_6;
        };
    }

    private static BarometerWarning warningFor(int phase, ForecastBand band) {
        if (phase == 4 && (band == ForecastBand.TRANSITION_LIKELY_SOON || band == ForecastBand.IMMINENT)) {
            return BarometerWarning.NETHER_SEVERANCE_RISK;
        }
        if (phase == 5) {
            return BarometerWarning.BLIZZARD_INTENSIFYING;
        }
        return BarometerWarning.NONE;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}

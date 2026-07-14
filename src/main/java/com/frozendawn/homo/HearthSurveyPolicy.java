package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import net.minecraft.util.Mth;

/**
 * Pure range and signal-strength rules for ORSA Hearth survey pings.
 */
public final class HearthSurveyPolicy {
    public static final SignalProfile STANDARD = new SignalProfile(1_280, 640, 256, 96);
    public static final SignalProfile CALIBRATED = new SignalProfile(2_560, 1_280, 384, 128);
    private static final int GEIGER_FAR_MEAN_INTERVAL_TICKS = 42;
    private static final int GEIGER_NEAR_MEAN_INTERVAL_TICKS = 3;

    private HearthSurveyPolicy() {
    }

    public static boolean emitsSignal(ReturnedHearthSavedData.HearthStage stage) {
        return stage != ReturnedHearthSavedData.HearthStage.PLANNED;
    }

    public static float intrinsicStrength(HearthSelectionPolicy.HearthType type,
                                          ReturnedHearthSavedData.HearthStage stage) {
        float stageStrength = switch (stage) {
            case PLANNED -> 0.0F;
            case TRACE -> 0.52F;
            case FORMED -> 0.78F;
            case INTACT -> 1.0F;
        };
        if (type == HearthSelectionPolicy.HearthType.MINOR) {
            stageStrength *= 0.82F;
        }
        return Mth.clamp(stageStrength, 0.0F, 1.0F);
    }

    public static SignalBand bandFor(double horizontalDistance, SignalProfile profile,
                                     boolean discovered) {
        if (horizontalDistance < 0.0D || horizontalDistance > profile.maximumRange()) {
            return SignalBand.NONE;
        }
        if (discovered) {
            return SignalBand.CATALOGUED;
        }
        if (horizontalDistance <= profile.catalogueRange()) {
            return SignalBand.LOCK;
        }
        if (horizontalDistance <= profile.fragmentRange()) {
            return SignalBand.FRAGMENT;
        }
        if (horizontalDistance <= profile.carrierRange()) {
            return SignalBand.CARRIER;
        }
        return SignalBand.STATIC;
    }

    public static float observedStrength(float intrinsicStrength, double horizontalDistance,
                                         SignalProfile profile) {
        if (intrinsicStrength <= 0.0F || horizontalDistance > profile.maximumRange()) {
            return 0.0F;
        }
        double normalizedDistance = Mth.clamp(horizontalDistance / profile.maximumRange(), 0.0D, 1.0D);
        float attenuation = (float) (1.0D - normalizedDistance * 0.72D);
        return Mth.clamp(intrinsicStrength * attenuation, 0.0F, 1.0F);
    }

    public static float proximity(double horizontalDistance, SignalProfile profile) {
        double normalizedDistance = Mth.clamp(horizontalDistance / profile.maximumRange(), 0.0D, 1.0D);
        return (float) (1.0D - normalizedDistance);
    }

    public static int geigerMeanIntervalTicks(float proximity) {
        float clamped = Mth.clamp(proximity, 0.0F, 1.0F);
        float smoothed = clamped * clamped * (3.0F - 2.0F * clamped);
        return Mth.clamp(
                Math.round(Mth.lerp(
                        smoothed,
                        GEIGER_FAR_MEAN_INTERVAL_TICKS,
                        GEIGER_NEAR_MEAN_INTERVAL_TICKS)),
                GEIGER_NEAR_MEAN_INTERVAL_TICKS,
                GEIGER_FAR_MEAN_INTERVAL_TICKS
        );
    }

    public static int sampleGeigerIntervalTicks(float proximity, float uniformSample) {
        int meanInterval = geigerMeanIntervalTicks(proximity);
        double sample = Mth.clamp(uniformSample, 0.0F, 0.9999F);
        int interval = (int) Math.round(-Math.log1p(-sample) * meanInterval);
        return Mth.clamp(interval, 1, meanInterval * 3);
    }

    public enum SignalBand {
        NONE,
        STATIC,
        CARRIER,
        FRAGMENT,
        LOCK,
        CATALOGUED
    }

    public record SignalProfile(int maximumRange, int carrierRange,
                                int fragmentRange, int catalogueRange) {
        public SignalProfile {
            if (maximumRange <= 0
                    || carrierRange <= 0 || carrierRange > maximumRange
                    || fragmentRange <= 0 || fragmentRange > carrierRange
                    || catalogueRange <= 0 || catalogueRange > fragmentRange) {
                throw new IllegalArgumentException("Hearth survey ranges must descend toward catalogue range");
            }
        }
    }
}

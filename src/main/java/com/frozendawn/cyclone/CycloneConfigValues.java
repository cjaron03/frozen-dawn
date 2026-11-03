package com.frozendawn.cyclone;

import com.frozendawn.config.FrozenDawnConfig;

/** Server-owned balance overrides; pure storm policy retains its original defaults. */
public final class CycloneConfigValues {
    private CycloneConfigValues() {}

    public static float peakOffset(CycloneSeverity severity) {
        return (switch (severity) {
            case MODERATE -> FrozenDawnConfig.CYCLONE_PEAK_OFFSET_MODERATE;
            case SEVERE -> FrozenDawnConfig.CYCLONE_PEAK_OFFSET_SEVERE;
            case CATASTROPHIC -> FrozenDawnConfig.CYCLONE_PEAK_OFFSET_CATASTROPHIC;
        }).get().floatValue();
    }

    public static CycloneVisuals.Peaks visibility() {
        return new CycloneVisuals.Peaks(
                FrozenDawnConfig.CYCLONE_VISIBILITY_MODERATE.get().floatValue(),
                FrozenDawnConfig.CYCLONE_VISIBILITY_SEVERE.get().floatValue(),
                FrozenDawnConfig.CYCLONE_VISIBILITY_CATASTROPHIC.get().floatValue());
    }

    public static CycloneSample sample(CycloneStorm storm, double x, double z, double time) {
        var sample = CycloneField.sample(storm, x, z, time);
        if (sample.stage() == CycloneStage.NONE) return sample;
        return new CycloneSample(sample.stage(), sample.intensity(), sample.calm(),
                sample.calm() * 15 + sample.intensity() * peakOffset(storm.severity()), sample.pressureHpa());
    }
}

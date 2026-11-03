package com.frozendawn.client;

import com.frozendawn.phase.PhaseOneWeatherPolicy;

/** Soft clear-day woodland wind in phases 0-1, quieter as phase 1 grows colder. */
final class PhaseOneNaturePolicy {
    private PhaseOneNaturePolicy() { }

    static boolean eligible(int phase, boolean woodland, boolean suppressed) {
        return phase >= 0 && phase <= 1 && woodland && !suppressed;
    }

    static boolean dry(boolean raining, float rain, float thunder) {
        return !raining && rain <= 0 && thunder <= 0;
    }

    static float volume(float progress, long dayTime, float exposure) {
        long day = Math.floorMod(dayTime, 24000L);
        float daylight = Math.clamp(Math.min(day / 1000F, (13000 - day) / 1500F), 0, 1);
        return .35F * (1 - .20F * PhaseOneWeatherPolicy.localProgress(progress))
                * daylight * Math.clamp(exposure, 0, 1);
    }

    /** Actual silent gap after a phrase, rather than a continuously repeating bed. */
    static int gapTicks(float progress, float roll) {
        float cold = PhaseOneWeatherPolicy.localProgress(progress);
        return Math.round(20 * (8 + 4 * cold + 8 * Math.clamp(roll, 0, 1)));
    }
}

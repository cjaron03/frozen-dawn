package com.frozendawn.event;

import net.minecraft.server.level.ServerLevel;

/**
 * Locks weather state based on apocalypse phase.
 *
 * Phase 1: Normal + more frequent rain/snow (reduced clear periods)
 * Phase 2: Locked rain (snow in cold biomes)
 * Phase 3: Permanent thunderstorm (snowstorm)
 * Phase 4-5: Permanent thunderstorm (blizzard/whiteout)
 *
 * Called from WorldTickHandler each tick.
 */
public final class WeatherHandler {

    private WeatherHandler() {}

    /** Maximum ticks of clear weather allowed in phase 1 (~2.5 minutes). */
    private static final int PHASE1_MAX_CLEAR_TICKS = 3000;
    /** Duration of forced rain in phase 1 (~4 minutes). */
    private static final int PHASE1_RAIN_DURATION = 5000;
    /** Duration used for locked weather in phase 2+ (~14 hours, effectively permanent). */
    private static final int LOCKED_DURATION = 1_000_000;

    private static int clearTickCounter = 0;

    public static void tick(ServerLevel overworld, int phase) {
        if (phase <= 0) return;

        switch (phase) {
            case 1 -> handlePhase1(overworld);
            default -> handleLockedWeather(overworld, phase);
        }
    }

    /**
     * Phase 1: Allow natural weather cycling but reduce clear periods.
     * If clear weather persists too long, force rain.
     */
    private static void handlePhase1(ServerLevel overworld) {
        if (!overworld.isRaining()) {
            clearTickCounter++;
            if (clearTickCounter > PHASE1_MAX_CLEAR_TICKS) {
                overworld.setWeatherParameters(0, PHASE1_RAIN_DURATION, true, false);
                clearTickCounter = 0;
            }
        } else {
            clearTickCounter = 0;
        }
    }

    /**
     * Phase 2+: Lock weather to rain (phase 2) or thunderstorm (phase 3+).
     * Phase 5: Force permanent midnight — sun and moon are gone, only darkness.
     */
    private static void handleLockedWeather(ServerLevel overworld, int phase) {
        boolean wantThunder = phase >= 3;

        boolean needsUpdate = !overworld.isRaining() || (wantThunder && !overworld.isThundering());

        if (needsUpdate) {
            overworld.setWeatherParameters(0, LOCKED_DURATION, true, wantThunder);
        }

        // Phase 5: lock time to midnight (18000 ticks = midnight)
        // The thunderstorm + permanent night = no sun, no moon, no clouds visible
        if (phase >= 5) {
            long dayTime = overworld.getDayTime() % 24000;
            if (dayTime < 14000 || dayTime > 22000) {
                overworld.setDayTime((overworld.getDayTime() / 24000) * 24000 + 18000);
            }
        }
    }
}

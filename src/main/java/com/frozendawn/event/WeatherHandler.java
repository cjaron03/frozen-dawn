package com.frozendawn.event;

import com.frozendawn.phase.PhaseManager;
import com.frozendawn.data.PhaseOneWeatherState;
import com.frozendawn.homo.PostMaeveWorldState;
import net.minecraft.server.level.ServerLevel;

/**
 * Locks weather state based on apocalypse phase.
 *
 * Phase 1: Variable clear/rain windows, gradually wetter; rare thunder
 * Phase 2: Locked rain (snow in cold biomes)
 * Phase 3: Permanent thunderstorm (snowstorm)
 * Phase 4-5: Permanent thunderstorm (blizzard/whiteout)
 * Phase 6 early: Extreme thunderstorm (worse than phase 5)
 * Phase 6 mid+: Clear weather (atmosphere thinning — no moisture)
 *
 * Note: this handler intentionally does not mutate dayTime for Phase 5 night
 * presentation. Apocalypse progression is derived from absolute world time, so
 * client sky/fog rendering owns the dark visual treatment instead.
 *
 * Called from WorldTickHandler each tick.
 */
public final class WeatherHandler {

    private WeatherHandler() {}

    private static final int LOCKED_DURATION = 1_000_000;

    /** Phase 1 scheduling now belongs to each world's saved data. */
    public static void reset() { }

    public static void tick(ServerLevel overworld, int phase, float progress) {
        if (phase != 1 || PostMaeveWorldState.isErased(overworld)) {
            var previous = overworld.getDataStorage().get(PhaseOneWeatherState.FACTORY, PhaseOneWeatherState.NAME);
            if (previous != null) previous.leave();
        }
        if (PostMaeveWorldState.isErased(overworld)) {
            if (overworld.getLevelData().isRaining() || overworld.getLevelData().isThundering()) {
                overworld.setWeatherParameters(LOCKED_DURATION, 0, false, false);
            }
            return;
        }
        if (phase <= 0) return;

        switch (phase) {
            case 1 -> PhaseOneWeatherState.get(overworld).tick(overworld, progress);
            default -> handleLockedWeather(overworld, phase, progress);
        }
    }

    /**
     * Phase 2+: Lock weather to rain (phase 2) or thunderstorm (phase 3+).
     * Phase 6 mid+ (progress >= 0.72): Clear weather — atmosphere too thin for precipitation.
     */
    private static void handleLockedWeather(ServerLevel overworld, int phase, float progress) {
        // Phase 6 mid+: atmosphere thinning, clear weather
        if (PhaseManager.isPhase6MidOrLater(phase, progress)) {
            if (overworld.getLevelData().isRaining()) {
                overworld.setWeatherParameters(LOCKED_DURATION, 0, false, false);
            }
        } else {
            boolean wantThunder = phase >= 3;
            boolean needsUpdate = !overworld.getLevelData().isRaining() || overworld.getLevelData().isThundering() != wantThunder;

            if (needsUpdate) {
                overworld.setWeatherParameters(0, LOCKED_DURATION, true, wantThunder);
            }
        }
    }
}

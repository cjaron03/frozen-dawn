package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;

/**
 * Pure stage thresholds for the world-level Returned Hearth clock.
 */
public final class HearthMaturationPolicy {
    public static final long MINECRAFT_DAY_TICKS = 24000L;
    public static final long TRACE_START_TICKS = MINECRAFT_DAY_TICKS;
    public static final long FORMED_START_TICKS = 3L * MINECRAFT_DAY_TICKS;
    public static final long INTACT_START_TICKS = 7L * MINECRAFT_DAY_TICKS;

    private HearthMaturationPolicy() {
    }

    public static ReturnedHearthSavedData.HearthStage stageFor(
            HearthSelectionPolicy.HearthType type, long maturityTicks) {
        long maturity = Math.max(0L, maturityTicks);
        if (type == HearthSelectionPolicy.HearthType.MAJOR
                && maturity >= INTACT_START_TICKS) {
            return ReturnedHearthSavedData.HearthStage.INTACT;
        }
        if (maturity >= FORMED_START_TICKS) {
            return ReturnedHearthSavedData.HearthStage.FORMED;
        }
        if (maturity >= TRACE_START_TICKS) {
            return ReturnedHearthSavedData.HearthStage.TRACE;
        }
        return ReturnedHearthSavedData.HearthStage.PLANNED;
    }
}

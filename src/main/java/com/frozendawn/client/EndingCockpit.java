package com.frozendawn.client;

import java.util.List;
import java.util.Locale;

/** The glass cockpit's readouts on the ending screen: the mission clock, the closing range and the typed signal. */
public final class EndingCockpit {
    public static final int START_RANGE_KM = 96_400;
    public static final int TICKS_PER_LETTER = 2;

    public static final String VIEWPORT = "screen.frozendawn.ending.viewport";
    public static final String MARS = "screen.frozendawn.ending.mars";
    public static final String GRAVITY_LABEL = "screen.frozendawn.ending.vector.gravity_label";
    public static final String GRAVITY_VALUE = "screen.frozendawn.ending.vector.gravity_value";
    public static final String CORRIDOR_LABEL = "screen.frozendawn.ending.vector.corridor_label";
    public static final String CORRIDOR_VALUE = "screen.frozendawn.ending.vector.corridor_value";
    public static final String RANGE_LABEL = "screen.frozendawn.ending.vector.range_label";
    public static final String RANGE_VALUE = "screen.frozendawn.ending.vector.range_value";
    public static final String CAPTURE_LABEL = "screen.frozendawn.ending.vector.capture_label";
    public static final String CAPTURE_VALUE = "screen.frozendawn.ending.vector.capture_value";
    public static final List<String> LANG_KEYS = List.of(VIEWPORT, MARS, GRAVITY_LABEL, GRAVITY_VALUE,
            CORRIDOR_LABEL, CORRIDOR_VALUE, RANGE_LABEL, RANGE_VALUE, CAPTURE_LABEL, CAPTURE_VALUE);

    private EndingCockpit() {
    }

    /** The player's own play time as a mission clock, in whole seconds. */
    public static String missionClock(long playTicks) {
        long seconds = Math.max(0L, playTicks) / 20L;
        return String.format(Locale.ROOT, "T+ %d:%02d:%02d", seconds / 3600L, seconds / 60L % 60L, seconds % 60L);
    }

    /** Range to Mars as the approach plays: the full range at its start, zero as the credits begin. */
    public static int rangeKm(float approachProgress) {
        float remaining = 1.0F - Math.max(0.0F, Math.min(1.0F, approachProgress));
        return Math.round(START_RANGE_KM * remaining * remaining);
    }

    public static String groupThousands(int value) {
        return String.format(Locale.ROOT, "%,d", value).replace(',', ' ');
    }

    /** How many letters of a signal have typed in, this many ticks into its panel. */
    public static int typedLength(int ticksIntoPanel, int letters) {
        return Math.max(0, Math.min(letters, ticksIntoPanel / TICKS_PER_LETTER));
    }
}

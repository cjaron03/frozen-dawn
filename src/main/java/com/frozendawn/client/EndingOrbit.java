package com.frozendawn.client;

import java.util.List;
import java.util.Locale;

/**
 * The ending's low orbit prelude: the frozen Earth under the window before the burn for Mars. Times are ending-screen
 * ticks; the globe is orthographic, in units of its radius, with x right and y up the screen.
 */
public final class EndingOrbit {
    /** The orbit view fades in as the opening black ends. */
    public static final int EARTH_START = 5 * 20;
    /** The burn for Mars fires here; the view fades to black over the next two seconds. */
    public static final int BURN_TICK = 23 * 20;
    public static final int EARTH_END = 25 * 20;
    /** A held black, then the Mars approach. */
    public static final int MARS_START = 27 * 20;

    public static final double SUB_POINT_LATITUDE = -20.0;
    private static final double START_LONGITUDE = 8.0;
    private static final double TURN_DEGREES = 15.0;
    private static final float FADE_IN_TICKS = 70.0F;

    public static final String TITLE = "screen.frozendawn.ending.life_support.title";
    public static final String O2_LABEL = "screen.frozendawn.ending.life_support.o2_label";
    public static final String O2_VALUE = "screen.frozendawn.ending.life_support.o2_value";
    public static final String CABIN_LABEL = "screen.frozendawn.ending.life_support.cabin_label";
    public static final String CABIN_VALUE = "screen.frozendawn.ending.life_support.cabin_value";
    public static final String CO2_LABEL = "screen.frozendawn.ending.life_support.co2_label";
    public static final String CO2_VALUE = "screen.frozendawn.ending.life_support.co2_value";
    public static final String HULL_LABEL = "screen.frozendawn.ending.life_support.hull_label";
    public static final String HULL_VALUE = "screen.frozendawn.ending.life_support.hull_value";
    public static final String BURN_LABEL = "screen.frozendawn.ending.life_support.burn_label";
    public static final String BURN_VALUE = "screen.frozendawn.ending.life_support.burn_value";
    public static final List<String> LANG_KEYS = List.of(TITLE, O2_LABEL, O2_VALUE, CABIN_LABEL, CABIN_VALUE,
            CO2_LABEL, CO2_VALUE, HULL_LABEL, HULL_VALUE, BURN_LABEL, BURN_VALUE);

    private EndingOrbit() {
    }

    /** The orbit view's opacity: in after the opening black, held, then out once the burn fires. */
    public static float earthAlpha(float tick) {
        float in = smooth((tick - EARTH_START) / FADE_IN_TICKS);
        float out = 1.0F - smooth((tick - BURN_TICK) / (float) (EARTH_END - BURN_TICK));
        return in * out;
    }

    /** The longitude under the camera: the globe turns west to east through the prelude. */
    public static double longitude(float tick) {
        double progress = Math.max(0.0, Math.min(1.0, (tick - EARTH_START) / (double) (EARTH_END - EARTH_START)));
        return START_LONGITUDE + TURN_DEGREES * progress;
    }

    /** Time left to the burn, as M:SS. */
    public static String burnClock(float tick) {
        int seconds = (int) Math.ceil(Math.max(0.0F, BURN_TICK - tick) / 20.0F);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    /**
     * The latitude and longitude under a point of the globe's disk, with the given sub-point at its center, or null
     * past the limb.
     */
    public static double[] surface(double x, double y, double subLatitude, double subLongitude) {
        double rr = x * x + y * y;
        if (rr > 1.0) {
            return null;
        }
        double z = Math.sqrt(1.0 - rr);
        double tilt = Math.toRadians(subLatitude);
        double north = y * Math.cos(tilt) + z * Math.sin(tilt);
        double toward = -y * Math.sin(tilt) + z * Math.cos(tilt);
        double latitude = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, north))));
        double longitude = Math.toDegrees(Math.atan2(x, toward)) + subLongitude;
        return new double[] {latitude, longitude};
    }

    private static float smooth(float value) {
        float t = Math.max(0.0F, Math.min(1.0F, value));
        return t * t * (3.0F - 2.0F * t);
    }
}

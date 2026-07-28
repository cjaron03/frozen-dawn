package com.frozendawn.homo;

/** Pure tuning and threshold policy for the Heart's mental pressure. */
public final class CognitiveLoadPolicy {
    public static final float MAX_LOAD = 100.0F;
    public static final float O2_FREEZE_THRESHOLD = 25.0F;
    public static final float MEMORY_FAILURE_THRESHOLD = 50.0F;
    public static final float INPUT_DELAY_THRESHOLD = 75.0F;
    public static final float MICRO_LAPSE_THRESHOLD = 90.0F;
    public static final float TAKEOVER_THRESHOLD = 100.0F;
    public static final double EFFECT_RADIUS = 112.0D;
    public static final float MAX_DESCENT_BLOCKS = 18.0F;
    public static final double TERMINAL_DAMAGE_RADIUS = 16.0D;
    public static final int TERMINAL_DAMAGE_INTERVAL_TICKS = 10;
    public static final double TERMINAL_PULL_ACCELERATION = 0.035D;
    public static final double TERMINAL_MAX_PULL_SPEED = 0.24D;
    public static final float BREAKOUT_REQUIRED_TICKS = 60.0F;
    public static final float BREAKOUT_RESISTANCE_THRESHOLD = 0.68F;
    public static final float BREAKOUT_DECAY_PER_TICK = 1.5F;
    public static final float BREAKOUT_RELEASE_LOAD = 62.0F;

    private static final float PASSIVE_RECOVERY_PER_TICK = 0.006F;
    private static final float SHELTER_RECOVERY_PER_TICK = 0.030F;
    private static final float HEAT_RECOVERY_PER_TICK = 0.045F;
    private static final float COMFORT_RECOVERY_PER_TICK = 0.060F;

    private CognitiveLoadPolicy() {
    }

    public enum Relief {
        NONE,
        SHELTER,
        HEAT,
        COMFORT
    }

    public enum Stage {
        NONE,
        CONTACT,
        SPREADING,
        SATURATION,
        FREAK_OUT
    }

    public static Stage stage(float load) {
        if (load <= 0.001F) {
            return Stage.NONE;
        }
        if (load < O2_FREEZE_THRESHOLD) {
            return Stage.CONTACT;
        }
        if (load < MEMORY_FAILURE_THRESHOLD) {
            return Stage.SPREADING;
        }
        if (load < INPUT_DELAY_THRESHOLD) {
            return Stage.SATURATION;
        }
        return Stage.FREAK_OUT;
    }

    public static float nextLoad(
            float current,
            float proximity,
            boolean hasLineOfSight,
            Relief relief,
            float fieldStrength) {
        float delta;
        if (relief != Relief.NONE) {
            delta = -recoveryRate(relief);
        } else if (hasLineOfSight && proximity > 0.0F) {
            float field = 0.55F + 0.45F * clamp01(fieldStrength);
            float pressure = 0.009F + 0.026F
                    * (float) Math.pow(clamp01(proximity), 1.4D);
            delta = pressure * field;
        } else {
            delta = -PASSIVE_RECOVERY_PER_TICK;
        }
        return clamp(current + delta, 0.0F, MAX_LOAD);
    }

    public static float proximity(double distance) {
        return clamp01((float) (1.0D - distance / EFFECT_RADIUS));
    }

    public static float heartDescentBlocks(float load) {
        float t = clamp01(load / MAX_LOAD);
        float eased = t * t * (3.0F - 2.0F * t);
        return MAX_DESCENT_BLOCKS * eased;
    }

    public static int watcherCount(float load) {
        if (load < O2_FREEZE_THRESHOLD) {
            return 0;
        }
        if (load < MEMORY_FAILURE_THRESHOLD) {
            return 4;
        }
        if (load < INPUT_DELAY_THRESHOLD) {
            return 7;
        }
        if (load < MICRO_LAPSE_THRESHOLD) {
            return 10;
        }
        return 14;
    }

    public static int inputDelayTicks(float load) {
        if (load < INPUT_DELAY_THRESHOLD) {
            return 0;
        }
        float t = clamp01((load - INPUT_DELAY_THRESHOLD)
                / (MAX_LOAD - INPUT_DELAY_THRESHOLD));
        return Math.round(4.0F + 2.0F * t);
    }

    public static float terminalDamage(double horizontalDistance) {
        if (horizontalDistance > TERMINAL_DAMAGE_RADIUS) {
            return 0.0F;
        }
        float proximity = clamp01((float) (1.0D
                - horizontalDistance / TERMINAL_DAMAGE_RADIUS));
        return 2.0F + 6.0F * proximity;
    }

    public static float nextBreakoutTicks(float current, float resistance) {
        float delta = resistance >= BREAKOUT_RESISTANCE_THRESHOLD
                ? 1.0F : -BREAKOUT_DECAY_PER_TICK;
        return clamp(current + delta, 0.0F, BREAKOUT_REQUIRED_TICKS);
    }

    public static float breakoutProgress(float breakoutTicks) {
        return clamp01(breakoutTicks / BREAKOUT_REQUIRED_TICKS);
    }

    private static float recoveryRate(Relief relief) {
        return switch (relief) {
            case COMFORT -> COMFORT_RECOVERY_PER_TICK;
            case HEAT -> HEAT_RECOVERY_PER_TICK;
            case SHELTER -> SHELTER_RECOVERY_PER_TICK;
            case NONE -> PASSIVE_RECOVERY_PER_TICK;
        };
    }

    private static float clamp01(float value) {
        return clamp(value, 0.0F, 1.0F);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}

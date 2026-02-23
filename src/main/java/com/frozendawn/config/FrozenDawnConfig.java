package com.frozendawn.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class FrozenDawnConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // General
    public static final ModConfigSpec.IntValue TOTAL_DAYS;
    public static final ModConfigSpec.IntValue STARTING_DAY;
    public static final ModConfigSpec.BooleanValue PAUSE_PROGRESSION;

    // Temperature
    public static final ModConfigSpec.IntValue BASE_PHASE5_TEMP;
    public static final ModConfigSpec.DoubleValue GEOTHERMAL_STRENGTH;
    public static final ModConfigSpec.DoubleValue HEAT_SOURCE_MULTIPLIER;

    // Gameplay
    public static final ModConfigSpec.BooleanValue ENABLE_VEGETATION_DECAY;
    public static final ModConfigSpec.BooleanValue ENABLE_MOB_FREEZING;
    public static final ModConfigSpec.BooleanValue ENABLE_LAVA_FREEZING;
    public static final ModConfigSpec.DoubleValue SNOW_ACCUMULATION_RATE;

    // Client
    public static final ModConfigSpec.BooleanValue ENABLE_SUN_SHRINKING;
    public static final ModConfigSpec.BooleanValue ENABLE_SKY_DARKENING;
    public static final ModConfigSpec.BooleanValue ENABLE_FROST_OVERLAY;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("general");
        TOTAL_DAYS = BUILDER
                .comment("Total in-game days until phase 5 is complete")
                .defineInRange("totalDays", 100, 10, 1000);
        STARTING_DAY = BUILDER
                .comment("Skip ahead to this day for testing (0 = normal start)")
                .defineInRange("startingDay", 0, 0, 1000);
        PAUSE_PROGRESSION = BUILDER
                .comment("Freeze apocalypse at current phase")
                .define("pauseProgression", false);
        BUILDER.pop();

        BUILDER.push("temperature");
        BASE_PHASE5_TEMP = BUILDER
                .comment("Coldest surface temperature at phase 5 completion (Celsius)")
                .defineInRange("basePhase5Temp", -120, -200, 0);
        GEOTHERMAL_STRENGTH = BUILDER
                .comment("Multiplier for depth-based warmth")
                .defineInRange("geothermalStrength", 1.0, 0.0, 5.0);
        HEAT_SOURCE_MULTIPLIER = BUILDER
                .comment("Multiplier for heat source warmth")
                .defineInRange("heatSourceMultiplier", 1.0, 0.0, 5.0);
        BUILDER.pop();

        BUILDER.push("gameplay");
        ENABLE_VEGETATION_DECAY = BUILDER
                .comment("Enable vegetation freezing and decay")
                .define("enableVegetationDecay", true);
        ENABLE_MOB_FREEZING = BUILDER
                .comment("Enable mob freezing on cold surfaces")
                .define("enableMobFreezing", true);
        ENABLE_LAVA_FREEZING = BUILDER
                .comment("Enable lava freezing in later phases")
                .define("enableLavaFreezing", true);
        SNOW_ACCUMULATION_RATE = BUILDER
                .comment("Multiplier for snow accumulation speed")
                .defineInRange("snowAccumulationRate", 1.0, 0.0, 10.0);
        BUILDER.pop();

        BUILDER.push("client");
        ENABLE_SUN_SHRINKING = BUILDER
                .comment("Enable sun shrinking visual effect")
                .define("enableSunShrinking", true);
        ENABLE_SKY_DARKENING = BUILDER
                .comment("Enable progressive sky darkening")
                .define("enableSkyDarkening", true);
        ENABLE_FROST_OVERLAY = BUILDER
                .comment("Enable frost screen overlay in cold areas")
                .define("enableFrostOverlay", true);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}

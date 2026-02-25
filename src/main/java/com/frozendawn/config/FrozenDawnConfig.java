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
    public static final ModConfigSpec.BooleanValue ENABLE_FUEL_SCARCITY;
    public static final ModConfigSpec.IntValue FUEL_SCARCITY_PHASE;
    public static final ModConfigSpec.BooleanValue ENABLE_FUEL_PHASE_SCALING;
    public static final ModConfigSpec.BooleanValue ENABLE_LORE_BOOKS;

    // Client
    public static final ModConfigSpec.BooleanValue ENABLE_SUN_SHRINKING;
    public static final ModConfigSpec.BooleanValue ENABLE_SKY_DARKENING;
    public static final ModConfigSpec.BooleanValue ENABLE_FROST_OVERLAY;
    public static final ModConfigSpec.BooleanValue ENABLE_SKY_COLOR_SHIFT;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("general");
        TOTAL_DAYS = BUILDER
                .comment("Total in-game days until phase 5 is complete.",
                        "Preset-managed: overwritten by /frozendawn preset command.")
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
                .comment("Coldest surface temperature at phase 5 completion (Celsius).",
                        "Preset-managed: overwritten by /frozendawn preset command.")
                .defineInRange("basePhase5Temp", -120, -200, 0);
        GEOTHERMAL_STRENGTH = BUILDER
                .comment("Multiplier for depth-based warmth.",
                        "Preset-managed: overwritten by /frozendawn preset command.",
                        "Higher values = more warmth underground. Affects Geothermal Core effectiveness.")
                .defineInRange("geothermalStrength", 1.0, 0.0, 5.0);
        HEAT_SOURCE_MULTIPLIER = BUILDER
                .comment("Multiplier for heat source warmth (campfires, furnaces, Thermal Heaters, Geothermal Core).",
                        "Preset-managed: overwritten by /frozendawn preset command.",
                        "Performance note: higher values don't affect scan radius, only warmth output.")
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
                .comment("Multiplier for snow accumulation speed.",
                        "Preset-managed: overwritten by /frozendawn preset command.",
                        "Performance note: higher values increase snow layer updates per tick.")
                .defineInRange("snowAccumulationRate", 1.0, 0.0, 10.0);
        ENABLE_FUEL_SCARCITY = BUILDER
                .comment("Enable coal ore freezing into Frozen Coal Ore in later phases.",
                        "When enabled, surface coal ore (Y >= 0) converts to Frozen Coal Ore,",
                        "which drops less coal and has a chance to drop ice shards.")
                .define("enableFuelScarcity", true);
        FUEL_SCARCITY_PHASE = BUILDER
                .comment("Phase at which coal ore begins freezing (requires enableFuelScarcity = true)")
                .defineInRange("fuelScarcityPhase", 4, 2, 5);
        ENABLE_FUEL_PHASE_SCALING = BUILDER
                .comment("Enable phase-based fuel consumption scaling for Thermal Heaters.",
                        "Phases 1-3: 1x, Phase 4: 2x, Phase 5: 4x, Phase 6: 8x.",
                        "Stacks with tier consumption rate. Geothermal Core is exempt.",
                        "Disable to remove fuel logistics pressure without affecting cold.")
                .define("enableFuelPhaseScaling", true);
        ENABLE_LORE_BOOKS = BUILDER
                .comment("Enable ORSA lore books in structure loot tables.",
                        "When enabled, written books containing the ORSA narrative",
                        "are injected into village, temple, mineshaft, and stronghold chests.")
                .define("enableLoreBooks", true);
        BUILDER.pop();

        BUILDER.push("client");
        ENABLE_SUN_SHRINKING = BUILDER
                .comment("Enable sun shrinking visual effect")
                .define("enableSunShrinking", true);
        ENABLE_SKY_DARKENING = BUILDER
                .comment("Enable progressive sky darkening and fog")
                .define("enableSkyDarkening", true);
        ENABLE_FROST_OVERLAY = BUILDER
                .comment("Enable frost screen overlay in cold areas")
                .define("enableFrostOverlay", true);
        ENABLE_SKY_COLOR_SHIFT = BUILDER
                .comment("Enable phase-dependent sky color shifting.",
                        "Shifts sky from warm amber (phase 1) to deep purple-black (phase 5).")
                .define("enableSkyColorShift", true);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}

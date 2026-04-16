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
    public static final ModConfigSpec.BooleanValue ENABLE_SANITY;
    public static final ModConfigSpec.DoubleValue SANITY_SPEED_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue ENABLE_NATURAL_PASSIVE_SPAWN_SUPPRESSION;
    public static final ModConfigSpec.BooleanValue ENABLE_NATURAL_HOSTILE_SPAWN_SUPPRESSION;
    public static final ModConfigSpec.DoubleValue MOB_SPAWN_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue ENABLE_FROSTBITTEN;
    public static final ModConfigSpec.BooleanValue ENABLE_FROSTMITE;
    public static final ModConfigSpec.BooleanValue ENABLE_HOLLOW;
    public static final ModConfigSpec.BooleanValue ENABLE_RETURNED;
    public static final ModConfigSpec.BooleanValue ENABLE_MIMIC;
    public static final ModConfigSpec.BooleanValue ENABLE_ARCHITECT;
    public static final ModConfigSpec.BooleanValue ENABLE_LORE_BOOKS;
    public static final ModConfigSpec.BooleanValue ENABLE_WIN_CONDITION;
    public static final ModConfigSpec.IntValue BROADCAST_TICKS;
    public static final ModConfigSpec.IntValue TEST_BROADCAST_TICKS_OVERRIDE;
    public static final ModConfigSpec.BooleanValue TEST_ALLOW_REPEAT_LAUNCHES;

    // Client
    public static final ModConfigSpec.BooleanValue ENABLE_SKY_DARKENING;
    public static final ModConfigSpec.BooleanValue ENABLE_FROST_OVERLAY;
    public static final ModConfigSpec.BooleanValue ENABLE_SKY_COLOR_SHIFT;
    public static final ModConfigSpec.BooleanValue ENABLE_SANITY_CAMERA;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("general");
        TOTAL_DAYS = BUILDER
                .comment("Total in-game days until phase 6 atmospheric collapse is complete.",
                        "Fresh worlds start on the Default preset unless a different preset is selected.",
                        "Preset-managed: overwritten by /frozendawn preset command.")
                .defineInRange("totalDays", 120, 10, 1000);
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
                .comment("Environment and survival pressure.",
                        "Enable vegetation freezing and decay")
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
        ENABLE_SANITY = BUILDER
                .comment("Enable the isolation/sanity system (psychological effects in prolonged isolation)")
                .define("enableSanity", true);
        SANITY_SPEED_MULTIPLIER = BUILDER
                .comment("Multiplier for sanity degradation speed.",
                        "Preset-managed: overwritten by /frozendawn preset command.")
                .defineInRange("sanitySpeedMultiplier", 1.0, 0.0, 10.0);
        ENABLE_NATURAL_PASSIVE_SPAWN_SUPPRESSION = BUILDER
                .comment("Natural spawn suppression and hostile roster.",
                        "Suppress natural passive spawns in the Overworld from phase 4 onward.",
                        "Affects only natural passive categories (land animals and peaceful water life).",
                        "Does not affect breeding, spawn eggs, scripted spawns, or structure spawns.")
                .define("enableNaturalPassiveSpawnSuppression", true);
        ENABLE_NATURAL_HOSTILE_SPAWN_SUPPRESSION = BUILDER
                .comment("Suppress natural non-Frozen-Dawn hostile spawns in the Overworld from phase 4 onward.",
                        "Frozen Dawn hostile spawners still run normally.",
                        "Does not affect spawn eggs, scripted spawns, or structure spawns.")
                .define("enableNaturalHostileSpawnSuppression", true);
        MOB_SPAWN_MULTIPLIER = BUILDER
                .comment("Multiplier for Frozen Dawn hostile spawn rates (Frostbitten, Frostmites, Hollow, Returned, Mimics, Architects).",
                        "Preset-managed: overwritten by /frozendawn preset command.",
                        "Affects spawn chance, density caps, and group size where applicable.")
                .defineInRange("mobSpawnMultiplier", 1.0, 0.0, 5.0);
        ENABLE_FROSTBITTEN = BUILDER
                .comment("Enable Frostbitten mob spawning in Phase 4+.",
                        "Reanimated frost-covered humanoids that emerge from frozen ground.")
                .define("enableFrostbitten", true);
        ENABLE_FROSTMITE = BUILDER
                .comment("Enable Frostmite spawning and infestations in late phases.",
                        "Tiny heat-draining ice parasites that swarm players and heaters.")
                .define("enableFrostmite", true);
        ENABLE_HOLLOW = BUILDER
                .comment("Enable Hollow mob spawning in Phase 5+.",
                        "Translucent vapor entities that suppress sound and entomb players in ice.")
                .define("enableHollow", true);
        ENABLE_RETURNED = BUILDER
                .comment("Enable Returned mob spawning in Phase 6+.",
                        "Intelligent reanimated ORSA personnel that open doors, break lights, and extinguish heaters.")
                .define("enableReturned", true);
        ENABLE_MIMIC = BUILDER
                .comment("Enable Returned Mimic spawning in Phase 6+ (progress > 50%).",
                        "Disguises itself as a shadow figure, then attacks when approached.")
                .define("enableMimic", true);
        ENABLE_ARCHITECT = BUILDER
                .comment("Enable Returned Architect spawning in Phase 6+.",
                        "Intelligent mob that breaks through player-built walls and scaffolds over defenses.")
                .define("enableArchitect", true);
        ENABLE_LORE_BOOKS = BUILDER
                .comment("World narrative and endgame progression.",
                        "Enable ORSA lore books in structure loot tables.",
                        "When enabled, written books containing the ORSA narrative",
                        "are injected into village, temple, mineshaft, and stronghold chests.")
                .define("enableLoreBooks", true);
        ENABLE_WIN_CONDITION = BUILDER
                .comment("Enable the win condition system (crashed satellite, transponder, broadcast).",
                        "When disabled: satellite doesn't spawn, compass spins, schematic doesn't exist.",
                        "For endless survival players who prefer no endgame goal.")
                .define("enableWinCondition", true);
        BROADCAST_TICKS = BUILDER
                .comment("Duration of the transponder broadcast in ticks.",
                        "Preset-managed: overwritten by /frozendawn preset command.",
                        "Default 120000 (~5 in-game days). Brutal: 192000. Cinematic: 72000.")
                .defineInRange("broadcastTicks", 120000, 6000, 480000);
        TEST_BROADCAST_TICKS_OVERRIDE = BUILDER
                .comment("TESTING ONLY: override transponder broadcast time in ticks.",
                        "Set to 0 to use the preset-managed broadcastTicks value.",
                        "100 ticks = 5 seconds.")
                .defineInRange("testBroadcastTicksOverride", 0, 0, 480000);
        TEST_ALLOW_REPEAT_LAUNCHES = BUILDER
                .comment("TESTING ONLY: allow repeated rocket launches in the same world.",
                        "When true, the final rocket package is not consumed as a one-shot world state.",
                        "Disable this before shipping the ending.")
                .define("testAllowRepeatLaunches", false);
        BUILDER.pop();

        BUILDER.push("client");
        ENABLE_SKY_DARKENING = BUILDER
                .comment("Enable progressive sky darkening and fog")
                .define("enableSkyDarkening", true);
        ENABLE_FROST_OVERLAY = BUILDER
                .comment("Enable frost screen overlay in cold areas")
                .define("enableFrostOverlay", true);
        ENABLE_SKY_COLOR_SHIFT = BUILDER
                .comment("Enable phase-dependent sky color shifting.",
                        "Shifts sky from warm amber (phase 1) through cold blue to black during phase 6 atmospheric collapse.")
                .define("enableSkyColorShift", true);
        ENABLE_SANITY_CAMERA = BUILDER
                .comment("Enable subtle camera effects from the sanity system.",
                        "Disable if you experience motion sickness. Audio and visual effects still play.")
                .define("enableSanityCameraEffects", true);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}

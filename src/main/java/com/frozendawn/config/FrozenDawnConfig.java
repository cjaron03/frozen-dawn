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
    public static final ModConfigSpec.IntValue MIND_HEAL_TIER_TWO_SECONDS;
    public static final ModConfigSpec.IntValue MIND_HEAL_TIER_THREE_SECONDS;
    public static final ModConfigSpec.IntValue BRUTAL_MIND_HEAL_TIER_TWO_SECONDS;
    public static final ModConfigSpec.IntValue BRUTAL_MIND_HEAL_TIER_THREE_SECONDS;
    public static final ModConfigSpec.DoubleValue MIND_HEAL_TIER_TWO_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue MIND_HEAL_TIER_THREE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue BRUTAL_MIND_HEAL_TIER_THREE_MULTIPLIER;
    public static final ModConfigSpec.IntValue MASTER_AURA_RADIUS;
    public static final ModConfigSpec.DoubleValue MASTER_AURA_TEMP_OFFSET_PER_TIER;
    public static final ModConfigSpec.IntValue MASTER_AURA_T2_STRIKE_MIN_SECONDS;
    public static final ModConfigSpec.IntValue MASTER_AURA_T2_STRIKE_MAX_SECONDS;
    public static final ModConfigSpec.IntValue MASTER_AURA_T3_STRIKE_MIN_SECONDS;
    public static final ModConfigSpec.IntValue MASTER_AURA_T3_STRIKE_MAX_SECONDS;
    public static final ModConfigSpec.IntValue MASTER_AURA_T2_ARC_SECONDS;
    public static final ModConfigSpec.IntValue MASTER_AURA_T3_ARC_SECONDS;
    public static final ModConfigSpec.DoubleValue MASTER_AURA_INFRASOUND_GAIN;
    public static final ModConfigSpec.DoubleValue MASTER_AURA_STORM_RESPONSE_SECONDS;
    public static final ModConfigSpec.DoubleValue SUIT_PUNCTURE_MASTER_CHANCE;
    public static final ModConfigSpec.DoubleValue SUIT_PUNCTURE_ARCHITECT_CHANCE;
    public static final ModConfigSpec.DoubleValue SUIT_PUNCTURE_MIMIC_AMBUSH_CHANCE;
    public static final ModConfigSpec.DoubleValue SUIT_PUNCTURE_PHYSICAL_CHANCE;
    public static final ModConfigSpec.DoubleValue SUIT_PUNCTURE_FALL_CHANCE_PER_BLOCK;
    public static final ModConfigSpec.IntValue SUIT_PUNCTURE_GRACE_TICKS;
    public static final ModConfigSpec.IntValue SUIT_PUNCTURE_MAX_CONCURRENT;
    public static final ModConfigSpec.IntValue SUIT_PUNCTURE_VENT_SECONDS;
    public static final ModConfigSpec.IntValue SUIT_REPRESSURIZE_PER_TICK;
    public static final ModConfigSpec.IntValue IMPROVISED_PATCH_DURATION_TICKS;
    public static final ModConfigSpec.IntValue ORSA_PATCH_DURATION_TICKS;
    public static final ModConfigSpec.DoubleValue IMPROVISED_PATCH_DEGRADE_CHANCE;
    public static final ModConfigSpec.IntValue IMPROVISED_PATCH_MIN_SEAL_SECONDS;
    public static final ModConfigSpec.IntValue IMPROVISED_PATCH_MAX_SEAL_SECONDS;
    public static final ModConfigSpec.DoubleValue UNDONE_SPAWN_CHANCE_PER_CHECK;
    public static final ModConfigSpec.DoubleValue UNDONE_ARCHITECT_SPAWN_CHANCE_PER_CHECK;
    public static final ModConfigSpec.DoubleValue BLOOMBOUND_UNDONE_SPAWN_CHANCE_PER_CHECK;
    public static final ModConfigSpec.DoubleValue ARCHIVIST_SPAWN_CHANCE_PER_CHECK;
    public static final ModConfigSpec.BooleanValue ENABLE_RIMEBOUND;
    public static final ModConfigSpec.DoubleValue RIMEBOUND_EVOLUTION_SHARE_MULTIPLIER;
    public static final ModConfigSpec.IntValue RIMEBOUND_NEARBY_CAP;
    public static final ModConfigSpec.BooleanValue ENABLE_RESONANT;
    public static final ModConfigSpec.DoubleValue RESONANT_EVOLUTION_SHARE_MULTIPLIER;
    public static final ModConfigSpec.IntValue RESONANT_NEARBY_CAP;
    public static final ModConfigSpec.BooleanValue ENABLE_FROSTWRITHE;
    public static final ModConfigSpec.DoubleValue FROSTWRITHE_EVOLUTION_SHARE_MULTIPLIER;
    public static final ModConfigSpec.IntValue FROSTWRITHE_NEARBY_CAP;
    public static final ModConfigSpec.BooleanValue ENABLE_AGGREGATE;
    public static final ModConfigSpec.IntValue AGGREGATE_RESIDUE_PRESSURE;
    public static final ModConfigSpec.IntValue AGGREGATE_DEPOSIT_PRESSURE;
    public static final ModConfigSpec.IntValue AGGREGATE_OSSUARY_PRESSURE;
    public static final ModConfigSpec.IntValue AGGREGATE_GESTATION_PRESSURE;
    public static final ModConfigSpec.IntValue AGGREGATE_AWAKENING_PRESSURE;
    public static final ModConfigSpec.IntValue AGGREGATE_CINEMATIC_HEALTH;
    public static final ModConfigSpec.IntValue AGGREGATE_NORMAL_HEALTH;
    public static final ModConfigSpec.IntValue AGGREGATE_BRUTAL_HEALTH;
    public static final ModConfigSpec.IntValue AGGREGATE_CINEMATIC_OVERFED_CAP;
    public static final ModConfigSpec.IntValue AGGREGATE_NORMAL_OVERFED_CAP;
    public static final ModConfigSpec.IntValue AGGREGATE_BRUTAL_OVERFED_CAP;
    public static final ModConfigSpec.DoubleValue AGGREGATE_TWO_PLAYER_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue AGGREGATE_THREE_PLAYER_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue AGGREGATE_FOUR_PLAYER_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue AGGREGATE_FIVE_PLAYER_MULTIPLIER;
    public static final ModConfigSpec.IntValue STILLPOINT_RADIUS;
    public static final ModConfigSpec.DoubleValue POST_MAEVE_AMBIENT_VOLUME_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue DEBUG_FORCE_MAEVE_ERASED;

    // Client
    public static final ModConfigSpec.BooleanValue ENABLE_SKY_DARKENING;
    public static final ModConfigSpec.BooleanValue ENABLE_FROST_OVERLAY;
    public static final ModConfigSpec.BooleanValue ENABLE_SKY_COLOR_SHIFT;
    public static final ModConfigSpec.BooleanValue ENABLE_FLOOD_HUD_FADE;
    public static final ModConfigSpec.BooleanValue ENABLE_FLOOD_SCREEN_EFFECTS;
    public static final ModConfigSpec.BooleanValue ENABLE_COGNITIVE_LOAD_EFFECTS;
    public static final ModConfigSpec.DoubleValue MIND_OVERRIDE_INTENSITY;
    public static final ModConfigSpec.DoubleValue MASTER_AURA_FLASH_INTENSITY;
    public static final ModConfigSpec.DoubleValue MASTER_AURA_PARTICLE_DENSITY;
    public static final ModConfigSpec.BooleanValue ENABLE_MASTER_SKY_FACE;
    public static final ModConfigSpec.DoubleValue MASTER_SKY_FACE_OPACITY;
    public static final ModConfigSpec.DoubleValue MASTER_SKY_FACE_SCALE;
    public static final ModConfigSpec.IntValue MASTER_SKY_FACE_GAZE_SECONDS;
    public static final ModConfigSpec.IntValue MASTER_SKY_FACE_FADE_IN_SECONDS;
    public static final ModConfigSpec.IntValue MASTER_SKY_FACE_FADE_OUT_SECONDS;
    public static final ModConfigSpec.IntValue MASTER_SKY_FACE_RENDER_DISTANCE;
    public static final ModConfigSpec.BooleanValue ENABLE_POST_MAEVE_MOON;
    public static final ModConfigSpec.DoubleValue POST_MAEVE_DEBRIS_DENSITY;
    public static final ModConfigSpec.BooleanValue ENABLE_SUIT_PUNCTURE_OVERLAY;

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
        MIND_HEAL_TIER_TWO_SECONDS = BUILDER
                .comment("Seconds before Thae Iven healing escalates to Tier 2.",
                        "Cinematic never escalates beyond Tier 1.")
                .defineInRange("mindHealTierTwoSeconds", 90, 15, 600);
        MIND_HEAL_TIER_THREE_SECONDS = BUILDER
                .comment("Seconds before Thae Iven healing escalates to Tier 3.")
                .defineInRange("mindHealTierThreeSeconds", 180, 30, 1200);
        BRUTAL_MIND_HEAL_TIER_TWO_SECONDS = BUILDER
                .comment("Brutal seconds before Thae Iven healing reaches Tier 2.")
                .defineInRange("brutalMindHealTierTwoSeconds", 60, 10, 600);
        BRUTAL_MIND_HEAL_TIER_THREE_SECONDS = BUILDER
                .comment("Brutal seconds before Thae Iven healing reaches Tier 3.")
                .defineInRange("brutalMindHealTierThreeSeconds", 120, 20, 1200);
        MIND_HEAL_TIER_TWO_MULTIPLIER = BUILDER
                .comment("Thae Iven Tier 2 healing-rate multiplier.")
                .defineInRange("mindHealTierTwoMultiplier", 2.0D, 1.0D, 8.0D);
        MIND_HEAL_TIER_THREE_MULTIPLIER = BUILDER
                .comment("Default Thae Iven Tier 3 healing-rate multiplier.")
                .defineInRange("mindHealTierThreeMultiplier", 3.5D, 1.0D, 10.0D);
        BRUTAL_MIND_HEAL_TIER_THREE_MULTIPLIER = BUILDER
                .comment("Brutal Thae Iven Tier 3 healing-rate multiplier.")
                .defineInRange("brutalMindHealTierThreeMultiplier", 4.0D, 1.0D, 12.0D);
        MASTER_AURA_RADIUS = BUILDER
                .comment("Radius of the Master Architect's non-damaging Hearth aura.")
                .defineInRange("masterAuraRadius", 160, 48, 512);
        MASTER_AURA_TEMP_OFFSET_PER_TIER = BUILDER
                .comment("Ambient Celsius offset for each aura step above Passive.",
                        "Tier 2 applies one step; Tier 3 applies two steps.")
                .defineInRange("masterAuraTempOffsetPerTier", -8.0D, -40.0D, 0.0D);
        MASTER_AURA_T2_STRIKE_MIN_SECONDS = BUILDER
                .comment("Minimum seconds between Noticed aura lightning events.")
                .defineInRange("masterAuraTier2StrikeMinSeconds", 10, 3, 120);
        MASTER_AURA_T2_STRIKE_MAX_SECONDS = BUILDER
                .comment("Maximum seconds between Noticed aura lightning events.")
                .defineInRange("masterAuraTier2StrikeMaxSeconds", 20, 4, 180);
        MASTER_AURA_T3_STRIKE_MIN_SECONDS = BUILDER
                .comment("Minimum seconds between fight aura lightning events.")
                .defineInRange("masterAuraTier3StrikeMinSeconds", 3, 1, 60);
        MASTER_AURA_T3_STRIKE_MAX_SECONDS = BUILDER
                .comment("Maximum seconds between fight aura lightning events.")
                .defineInRange("masterAuraTier3StrikeMaxSeconds", 7, 2, 90);
        MASTER_AURA_T2_ARC_SECONDS = BUILDER
                .comment("Average seconds between Noticed aura particle arcs.")
                .defineInRange("masterAuraTier2ArcSeconds", 10, 2, 60);
        MASTER_AURA_T3_ARC_SECONDS = BUILDER
                .comment("Average seconds between fight aura particle arcs.")
                .defineInRange("masterAuraTier3ArcSeconds", 3, 1, 30);
        MASTER_AURA_INFRASOUND_GAIN = BUILDER
                .comment("Maximum gain of the Master Architect's 36 Hz presence hum.")
                .defineInRange("masterAuraInfrasoundGain", 0.55D, 0.0D, 1.5D);
        MASTER_AURA_STORM_RESPONSE_SECONDS = BUILDER
                .comment("Seconds for the distant storm column to grow toward a new tier.")
                .defineInRange("masterAuraStormResponseSeconds", 5.0D, 0.5D, 30.0D);
        SUIT_PUNCTURE_MASTER_CHANCE = BUILDER
                .comment("Chance that a Master Architect melee hit punctures a sealed EVA suit.")
                .defineInRange("suitPunctureMasterChance", 0.10D, 0.0D, 1.0D);
        SUIT_PUNCTURE_ARCHITECT_CHANCE = BUILDER
                .comment("Chance that a base Architect melee hit punctures a sealed EVA suit.")
                .defineInRange("suitPunctureArchitectChance", 0.33D, 0.0D, 1.0D);
        SUIT_PUNCTURE_MIMIC_AMBUSH_CHANCE = BUILDER
                .comment("Chance that a Mimic's first ambush hit punctures a sealed EVA suit.")
                .defineInRange("suitPunctureMimicAmbushChance", 0.65D, 0.0D, 1.0D);
        SUIT_PUNCTURE_PHYSICAL_CHANCE = BUILDER
                .comment("Baseline puncture chance for whitelisted physical damage.")
                .defineInRange("suitPuncturePhysicalChance", 0.20D, 0.0D, 1.0D);
        SUIT_PUNCTURE_FALL_CHANCE_PER_BLOCK = BUILDER
                .comment("Puncture chance added per block of fall distance, capped at 60%.")
                .defineInRange("suitPunctureFallChancePerBlock", 0.03D, 0.0D, 0.20D);
        SUIT_PUNCTURE_GRACE_TICKS = BUILDER
                .comment("Ticks after a puncture during which another puncture cannot occur.")
                .defineInRange("suitPunctureGraceTicks", 100, 1, 1200);
        SUIT_PUNCTURE_MAX_CONCURRENT = BUILDER
                .comment("Maximum concurrent EVA suit punctures. Hard-capped at two.")
                .defineInRange("suitPunctureMaxConcurrent", 2, 1, 2);
        SUIT_PUNCTURE_VENT_SECONDS = BUILDER
                .comment("Seconds for one puncture to vent a full carried O2 reserve.")
                .defineInRange("suitPunctureVentSeconds", 80, 20, 300);
        SUIT_REPRESSURIZE_PER_TICK = BUILDER
                .comment("O2 restored per tick while an intact EVA suit is in breathable air.")
                .defineInRange("suitRepressurizePerTick", 1, 0, 20);
        IMPROVISED_PATCH_DURATION_TICKS = BUILDER
                .comment("Use duration of the improvised clothing-scrap patch.")
                .defineInRange("improvisedPatchDurationTicks", 60, 20, 200);
        ORSA_PATCH_DURATION_TICKS = BUILDER
                .comment("Use duration of the permanent ORSA suit patch kit.")
                .defineInRange("orsaPatchDurationTicks", 40, 10, 120);
        IMPROVISED_PATCH_DEGRADE_CHANCE = BUILDER
                .comment("Chance that an improvised patch eventually reopens in vacuum.")
                .defineInRange("improvisedPatchDegradeChance", 0.25D, 0.0D, 1.0D);
        IMPROVISED_PATCH_MIN_SEAL_SECONDS = BUILDER
                .comment("Minimum lifetime of a degrading improvised seal.")
                .defineInRange("improvisedPatchMinSealSeconds", 60, 10, 1200);
        IMPROVISED_PATCH_MAX_SEAL_SECONDS = BUILDER
                .comment("Maximum lifetime of a degrading improvised seal.")
                .defineInRange("improvisedPatchMaxSealSeconds", 120, 10, 1800);
        UNDONE_SPAWN_CHANCE_PER_CHECK = BUILDER
                .comment("Chance per eligible player every 200 ticks to spawn one Undone after Maeve is erased.")
                .defineInRange("undoneSpawnChancePerCheck", 0.011D, 0.0D, 1.0D);
        UNDONE_ARCHITECT_SPAWN_CHANCE_PER_CHECK = BUILDER
                .comment("Chance per eligible player every 600 ticks to spawn one Undone Architect after Maeve is erased.")
                .defineInRange("undoneArchitectSpawnChancePerCheck", 0.025D, 0.0D, 1.0D);
        BLOOMBOUND_UNDONE_SPAWN_CHANCE_PER_CHECK = BUILDER
                .comment("Chance per eligible player every 200 ticks to spawn one Bloombound Undone near Core-density Bloom.")
                .defineInRange("bloomboundUndoneSpawnChancePerCheck", 0.018D, 0.0D, 1.0D);
        ARCHIVIST_SPAWN_CHANCE_PER_CHECK = BUILDER
                .comment("Chance per eligible loaded 32x32-chunk region every 600 ticks to spawn one Archivist.")
                .defineInRange("archivistSpawnChancePerCheck", 0.004D, 0.0D, 1.0D);
        ENABLE_RIMEBOUND = BUILDER
                .comment("Enable post-Maeve Frostbitten evolution into Rimebound encounters.")
                .define("enableRimebound", true);
        RIMEBOUND_EVOLUTION_SHARE_MULTIPLIER = BUILDER
                .comment("Multiplier for the Rimebound share of Frostbitten spawn rolls.")
                .defineInRange("rimeboundEvolutionShareMultiplier", 1.0D, 0.0D, 4.0D);
        RIMEBOUND_NEARBY_CAP = BUILDER
                .comment("Maximum ordinary Rimebound within 64 blocks.")
                .defineInRange("rimeboundNearbyCap", 2, 1, 8);
        ENABLE_RESONANT = BUILDER
                .comment("Enable post-Maeve Hollow evolution into Resonant encounters.")
                .define("enableResonant", true);
        RESONANT_EVOLUTION_SHARE_MULTIPLIER = BUILDER
                .comment("Multiplier for the Resonant share of Hollow spawn rolls.")
                .defineInRange("resonantEvolutionShareMultiplier", 1.0D, 0.0D, 4.0D);
        RESONANT_NEARBY_CAP = BUILDER
                .comment("Maximum ordinary Resonants within 64 blocks.")
                .defineInRange("resonantNearbyCap", 2, 1, 8);
        ENABLE_FROSTWRITHE = BUILDER
                .comment("Enable post-Maeve Frostmite colony formation into Frostwrithe encounters.")
                .define("enableFrostwrithe", true);
        FROSTWRITHE_EVOLUTION_SHARE_MULTIPLIER = BUILDER
                .comment("Multiplier for the Frostwrithe share of successful Frostmite spawn rolls.")
                .defineInRange("frostwritheEvolutionShareMultiplier", 1.0D, 0.0D, 4.0D);
        FROSTWRITHE_NEARBY_CAP = BUILDER
                .comment("Maximum assembled Frostwrithe within 64 blocks.")
                .defineInRange("frostwritheNearbyCap", 1, 1, 4);
        ENABLE_AGGREGATE = BUILDER
                .comment("Enable the optional once-per-world post-Maeve Aggregate ecology.")
                .define("enableAggregate", true);
        AGGREGATE_RESIDUE_PRESSURE = BUILDER
                .comment("Convergence pressure required before Aggregate residue appears.")
                .defineInRange("aggregateResiduePressure", 60, 1, 100_000);
        AGGREGATE_DEPOSIT_PRESSURE = BUILDER
                .comment("Convergence pressure required to form the Deposit.")
                .defineInRange("aggregateDepositPressure", 140, 1, 100_000);
        AGGREGATE_OSSUARY_PRESSURE = BUILDER
                .comment("Convergence pressure required to form the Ossuary.")
                .defineInRange("aggregateOssuaryPressure", 240, 1, 100_000);
        AGGREGATE_GESTATION_PRESSURE = BUILDER
                .comment("Convergence pressure required to begin gestation.")
                .defineInRange("aggregateGestationPressure", 340, 1, 100_000);
        AGGREGATE_AWAKENING_PRESSURE = BUILDER
                .comment("Convergence pressure required to arm Aggregate awakening.")
                .defineInRange("aggregateAwakeningPressure", 400, 1, 100_000);
        AGGREGATE_CINEMATIC_HEALTH = BUILDER
                .defineInRange("aggregateCinematicHealth", 500, 1, 100_000);
        AGGREGATE_NORMAL_HEALTH = BUILDER
                .defineInRange("aggregateNormalHealth", 700, 1, 100_000);
        AGGREGATE_BRUTAL_HEALTH = BUILDER
                .defineInRange("aggregateBrutalHealth", 1_000, 1, 100_000);
        AGGREGATE_CINEMATIC_OVERFED_CAP = BUILDER
                .defineInRange("aggregateCinematicOverfedCap", 650, 1, 100_000);
        AGGREGATE_NORMAL_OVERFED_CAP = BUILDER
                .defineInRange("aggregateNormalOverfedCap", 900, 1, 100_000);
        AGGREGATE_BRUTAL_OVERFED_CAP = BUILDER
                .defineInRange("aggregateBrutalOverfedCap", 1_300, 1, 100_000);
        AGGREGATE_TWO_PLAYER_MULTIPLIER = BUILDER
                .defineInRange("aggregateTwoPlayerMultiplier", 1.4D, 1.0D, 10.0D);
        AGGREGATE_THREE_PLAYER_MULTIPLIER = BUILDER
                .defineInRange("aggregateThreePlayerMultiplier", 1.8D, 1.0D, 10.0D);
        AGGREGATE_FOUR_PLAYER_MULTIPLIER = BUILDER
                .defineInRange("aggregateFourPlayerMultiplier", 2.2D, 1.0D, 10.0D);
        AGGREGATE_FIVE_PLAYER_MULTIPLIER = BUILDER
                .defineInRange("aggregateFivePlayerMultiplier", 2.6D, 1.0D, 10.0D);
        STILLPOINT_RADIUS = BUILDER
                .comment("Quiet radius created by the placed Stillpoint Core.")
                .defineInRange("stillpointRadius", 48, 8, 256);
        POST_MAEVE_AMBIENT_VOLUME_MULTIPLIER = BUILDER
                .comment("Permanent ambient-wind volume multiplier after Maeve is erased.")
                .defineInRange("postMaeveAmbientVolumeMultiplier", 0.45D, 0.0D, 1.0D);
        DEBUG_FORCE_MAEVE_ERASED = BUILDER
                .comment("DEBUG ONLY: treat Maeve as erased without changing saved world state.")
                .define("debugForceMaeveErased", false);
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
        ENABLE_FLOOD_HUD_FADE = BUILDER
                .comment("Fade survival HUD elements during the Master Architect Flood finale.",
                        "Disable for accessibility; gameplay effects and the crosshair remain unchanged.")
                .define("enableFloodHudFade", true);
        ENABLE_FLOOD_SCREEN_EFFECTS = BUILDER
                .comment("Enable fog, vignette, memory pulses, and FOV compression in Thae Iven.",
                        "Disable for photosensitivity while preserving the fight and its hotbar.")
                .define("enableFloodScreenEffects", true);
        ENABLE_COGNITIVE_LOAD_EFFECTS = BUILDER
                .comment("Enable Cognitive Load watcher, telemetry, screen, and audio effects.",
                        "Disable for accessibility; Load mechanics and its HUD bar remain active.")
                .define("enableCognitiveLoadEffects", true);
        MIND_OVERRIDE_INTENSITY = BUILDER
                .comment("Scales Thae Iven screen effects without changing movement or damage.")
                .defineInRange("mindOverrideIntensity", 1.0D, 0.0D, 1.0D);
        MASTER_AURA_FLASH_INTENSITY = BUILDER
                .comment("Enables the vanilla-style Master aura lightning flash above zero.",
                        "Set to zero to disable it for photosensitivity.")
                .defineInRange("masterAuraFlashIntensity", 1.0D, 0.0D, 1.0D);
        MASTER_AURA_PARTICLE_DENSITY = BUILDER
                .comment("Scales visual-only Master aura particles without changing mechanics.")
                .defineInRange("masterAuraParticleDensity", 1.0D, 0.0D, 1.0D);
        ENABLE_MASTER_SKY_FACE = BUILDER
                .comment("Render the distant Master Architect face above an awakened Major Hearth.")
                .define("enableMasterSkyFace", true);
        MASTER_SKY_FACE_OPACITY = BUILDER
                .comment("Maximum opacity of the Master Architect sky face.")
                .defineInRange("masterSkyFaceOpacity", 0.82D, 0.0D, 1.0D);
        MASTER_SKY_FACE_SCALE = BUILDER
                .comment("Scale of the Master Architect sky face beacon.")
                .defineInRange("masterSkyFaceScale", 1.0D, 0.25D, 3.0D);
        MASTER_SKY_FACE_GAZE_SECONDS = BUILDER
                .comment("Average seconds between rare sky-face glances toward the player.")
                .defineInRange("masterSkyFaceGazeSeconds", 55, 10, 600);
        MASTER_SKY_FACE_FADE_IN_SECONDS = BUILDER
                .comment("Seconds for newly awakened sky-face features to fully resolve.")
                .defineInRange("masterSkyFaceFadeInSeconds", 90, 5, 300);
        MASTER_SKY_FACE_FADE_OUT_SECONDS = BUILDER
                .comment("Seconds for sky-face features to recede after de-escalation.")
                .defineInRange("masterSkyFaceFadeOutSeconds", 120, 5, 600);
        MASTER_SKY_FACE_RENDER_DISTANCE = BUILDER
                .comment("Maximum horizontal distance for the sky-face beacon.",
                        "It recedes with true distance and fades near the configured boundary.")
                .defineInRange("masterSkyFaceRenderDistance", 2500, 128, 8000);
        ENABLE_POST_MAEVE_MOON = BUILDER
                .comment("Render the damaged Moon and orbital debris after Maeve is erased.")
                .define("enablePostMaeveMoon", true);
        POST_MAEVE_DEBRIS_DENSITY = BUILDER
                .comment("Scales visual-only post-Maeve orbital debris without changing progression.")
                .defineInRange("postMaeveDebrisDensity", 1.0D, 0.0D, 1.0D);
        ENABLE_SUIT_PUNCTURE_OVERLAY = BUILDER
                .comment("Show red viewport cracks while the equipped EVA suit is punctured.")
                .define("enableSuitPunctureOverlay", true);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}

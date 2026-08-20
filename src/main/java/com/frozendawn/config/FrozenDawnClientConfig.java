package com.frozendawn.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only presentation settings kept out of the shared gameplay config. */
public final class FrozenDawnClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_STILLPOINT_FIELD_EFFECTS;
    public static final ModConfigSpec.DoubleValue STILLPOINT_DISTORTION_INTENSITY;
    public static final ModConfigSpec.DoubleValue STILLPOINT_PARTICLE_DENSITY;
    public static final ModConfigSpec.BooleanValue ENABLE_STILLPOINT_AUDIO_MUFFLING;
    public static final ModConfigSpec.BooleanValue REDUCED_THAEVEN_INK_ANIMATION;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("stillpoint");
        ENABLE_STILLPOINT_FIELD_EFFECTS = BUILDER
                .comment("Render the depth-aware Stillpoint sanctuary boundary.",
                        "Disabling this never disables server-side protection.")
                .define("enableFieldEffects", true);
        STILLPOINT_DISTORTION_INTENSITY = BUILDER
                .comment("Scales Stillpoint refraction and inverted-mirror color shift.")
                .defineInRange("distortionIntensity", 1.0D, 0.0D, 1.0D);
        STILLPOINT_PARTICLE_DENSITY = BUILDER
                .comment("Scales client-only Stillpoint charge and boundary particles.")
                .defineInRange("particleDensity", 1.0D, 0.0D, 1.0D);
        ENABLE_STILLPOINT_AUDIO_MUFFLING = BUILDER
                .comment("Muffle positional sounds originating outside an active Stillpoint field.")
                .define("enableAudioMuffling", true);
        BUILDER.pop();
        BUILDER.push("thaevenLore");
        REDUCED_THAEVEN_INK_ANIMATION = BUILDER
                .comment("Resolve translator ink immediately without changing lore unlocks.")
                .define("reducedInkAnimation", false);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private FrozenDawnClientConfig() {
    }
}

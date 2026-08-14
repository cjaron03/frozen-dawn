package com.frozendawn.config;

/** Difficulty scaling for post-Maeve evolution shares, separate from parent spawn pressure. */
public final class PostMaeveEvolutionDifficulty {
    private PostMaeveEvolutionDifficulty() {
    }

    public static double evolutionMultiplier() {
        return evolutionMultiplier(ConfigPresets.detectCurrentPreset());
    }

    public static double evolutionMultiplier(ConfigPresets preset) {
        if (preset == ConfigPresets.CINEMATIC) return 0.65D;
        if (preset == ConfigPresets.BRUTAL) return 1.35D;
        return 1.0D;
    }

    public static double remnantMultiplier() {
        return remnantMultiplier(ConfigPresets.detectCurrentPreset());
    }

    public static double remnantMultiplier(ConfigPresets preset) {
        if (preset == ConfigPresets.CINEMATIC) return 0.8D;
        if (preset == ConfigPresets.BRUTAL) return 1.15D;
        return 1.0D;
    }
}

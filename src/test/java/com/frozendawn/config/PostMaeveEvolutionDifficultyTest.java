package com.frozendawn.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostMaeveEvolutionDifficultyTest {
    @Test
    void scalesLineageEvolutionByPreset() {
        assertEquals(0.65D, PostMaeveEvolutionDifficulty.evolutionMultiplier(
                ConfigPresets.CINEMATIC), 0.0001D);
        assertEquals(1.0D, PostMaeveEvolutionDifficulty.evolutionMultiplier(
                ConfigPresets.DEFAULT), 0.0001D);
        assertEquals(1.35D, PostMaeveEvolutionDifficulty.evolutionMultiplier(
                ConfigPresets.BRUTAL), 0.0001D);
        assertEquals(1.0D, PostMaeveEvolutionDifficulty.evolutionMultiplier(null), 0.0001D);
    }

    @Test
    void scalesRemnantMoreGently() {
        assertEquals(0.8D, PostMaeveEvolutionDifficulty.remnantMultiplier(
                ConfigPresets.CINEMATIC), 0.0001D);
        assertEquals(1.0D, PostMaeveEvolutionDifficulty.remnantMultiplier(
                ConfigPresets.DEFAULT), 0.0001D);
        assertEquals(1.15D, PostMaeveEvolutionDifficulty.remnantMultiplier(
                ConfigPresets.BRUTAL), 0.0001D);
        assertEquals(1.0D, PostMaeveEvolutionDifficulty.remnantMultiplier(null), 0.0001D);
    }
}

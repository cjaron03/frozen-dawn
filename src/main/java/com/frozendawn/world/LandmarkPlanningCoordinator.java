package com.frozendawn.world;

import net.minecraft.server.level.ServerLevel;

/** Runs deterministic landmark searches within one shared server-tick budget. */
public final class LandmarkPlanningCoordinator {
    private LandmarkPlanningCoordinator() {
    }

    public static void tick(ServerLevel overworld) {
        LandmarkPlanningBudget budget = LandmarkPlanningBudget.start();
        BlastPitPlanner.ensurePlanned(overworld, budget);
        TowerPlanner.ensurePlanned(overworld, budget);
    }
}

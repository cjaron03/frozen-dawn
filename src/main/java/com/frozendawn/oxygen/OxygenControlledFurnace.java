package com.frozendawn.oxygen;

/** Implemented on vanilla furnace, blast furnace and smoker block entities. */
public interface OxygenControlledFurnace {
    boolean frozendawn$isOxygenStarved();
    void frozendawn$starveForOxygen();
    void frozendawn$resumeForOxygen();
}

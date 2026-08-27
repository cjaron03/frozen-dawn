package com.frozendawn.entity.architect;

/**
 * Centralizes state-only mutations for action transitions.
 */
public final class ArchitectActionTransitionSupport {

    private ArchitectActionTransitionSupport() {
    }

    public static void onLeaveObserve(ArchitectObservationMemory observationMemory) {
        observationMemory.setObserveTicks(0);
        observationMemory.setObserveTargetTicks(0);
    }

    public static void onLeaveApproach(ArchitectApproachState approachState) {
        approachState.unreachableTicks = 0;
        approachState.ceilingBreachPos = null;
        approachState.stepOffTarget = null;
        approachState.stepOffStart = null;
    }

    public static void onEnterRetreat(ArchitectCombatState combatState) {
        combatState.retreatPhase = 0;
        combatState.retreatCoverBuilt = 0;
        combatState.retreatStartPosition = null;
        combatState.retreatRunTicks = 0;
    }

    public static void onLeaveRetreat(ArchitectCombatState combatState) {
        combatState.retreatStartPosition = null;
        combatState.retreatRunTicks = 0;
    }

    public static void primeMeleeHandoffState(
            ArchitectBrainState brainState,
            ArchitectApproachState approachState,
            int meleeCommitTicks
    ) {
        brainState.setMeleeCommitTicks(Math.max(brainState.getMeleeCommitTicks(), meleeCommitTicks));
        approachState.ceilingBreachPos = null;
    }
}

package com.frozendawn.maeve;

import com.frozendawn.entity.ArchitectEntity;

final class LearningUtility {
    private LearningUtility() { }
    static MaeveDirector.UtilityBias bias(CommitmentPolicy state, ArchitectEntity actor, long now) {
        if (state == null) return MaeveDirector.UtilityBias.NONE;
        var original = state.utilityBias(now);
        String dimension = actor.level().dimension().location().toString();
        var performance = state.performance();
        double pursue = state.hints(now).stream().filter(h -> h.pattern().equals(BeliefStore.PURSUIT)
                && h.evidence().dimension().equals(dimension)).mapToDouble(MaeveDirector.CommitmentHint::confidence).findFirst().orElse(0);
        // The conditional weak bias applies only while already withdrawing; it cannot manufacture a retreat.
        double cover = actor.getBrainAction() == ArchitectEntity.ACTION_RETREAT ? .3 * pursue : 0;
        return new MaeveDirector.UtilityBias((float) (original.fortify() * performance.multiplier(BeliefStore.RANGED, dimension)
                + cover * performance.multiplier(BeliefStore.PURSUIT, dimension)),
                (float) (original.peek() * performance.multiplier(BeliefStore.RECOVERY, dimension)));
    }
}

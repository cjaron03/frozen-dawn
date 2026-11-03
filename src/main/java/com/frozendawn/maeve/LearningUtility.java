package com.frozendawn.maeve;

import com.frozendawn.entity.ArchitectEntity;

final class LearningUtility {
    private LearningUtility() { }
    static MaeveDirector.UtilityBias bias(CommitmentPolicy state, ArchitectEntity actor, long now) {
        return bias(state, actor.level().dimension().location().toString(), actor.getBrainAction() == ArchitectEntity.ACTION_RETREAT, now);
    }

    static MaeveDirector.UtilityBias bias(CommitmentPolicy state, String dimension, boolean withdrawing, long now) {
        if (state == null) return MaeveDirector.UtilityBias.NONE;
        var original = state.utilityBias(now);
        var performance = state.performance();
        var hints = state.hints(now);
        double ranged = hints.stream().filter(h -> h.pattern().equals(BeliefStore.RANGED)
                && h.evidence().dimension().equals(dimension)).mapToDouble(MaeveDirector.CommitmentHint::confidence).findFirst().orElse(0);
        double pursue = hints.stream().filter(h -> h.pattern().equals(BeliefStore.PURSUIT)
                && h.evidence().dimension().equals(dimension)).mapToDouble(MaeveDirector.CommitmentHint::confidence).findFirst().orElse(0);
        // The conditional weak bias applies only while already withdrawing; it cannot manufacture a retreat.
        double cover = withdrawing ? .3 * pursue : 0;
        // Only the frozen history can strengthen cover. Keep the old weak preference
        // during a prior contradiction's cooldown or a failed counter's deferral.
        boolean strongRanged = ranged >= CommitmentPolicy.THRESHOLD && !state.blocked().contains(BeliefStore.RANGED)
                && !performance.deferred(BeliefStore.RANGED, dimension);
        double fortify = (original.fortify() + (strongRanged ? .3 * ranged : 0))
                * performance.multiplier(BeliefStore.RANGED, dimension)
                + cover * performance.multiplier(BeliefStore.PURSUIT, dimension);
        return new MaeveDirector.UtilityBias((float) Math.min(strongRanged ? .7 : .4, fortify),
                (float) (original.peek() * performance.multiplier(BeliefStore.RECOVERY, dimension)));
    }
}

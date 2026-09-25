package com.frozendawn.maeve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Variant choice stays inside one ranged-cover family and uses only frozen results. */
final class RangedCoverPolicy {
    static final double MANTLET_THRESHOLD = .90;
    static final String MANTLET = "PLAYER_PREFERS_RANGED:MANTLET";
    private RangedCoverPolicy() { }

    static String key(MaeveDirector.PositionCandidate c) { return c.advancingCover() ? MANTLET : c.pattern(); }
    static String key(MaeveDirector.PositionDirective d) { return d.advancingCover() ? MANTLET : d.pattern(); }
    static boolean validKey(String key) { return MANTLET.equals(key) || BeliefDescriptions.patterns().contains(key); }

    static List<MaeveDirector.PositionCandidate> options(CommitmentPolicy policy,
            List<MaeveDirector.PositionCandidate> candidates, long now, List<String> reasons) {
        var valid = new ArrayList<MaeveDirector.PositionCandidate>();
        var ranged = new ArrayList<MaeveDirector.PositionCandidate>();
        var hint = policy.hints(now).stream().filter(h -> h.pattern().equals(BeliefStore.RANGED)).findFirst();
        for (var c : candidates.stream().limit(6).toList()) {
            if (!Double.isFinite(c.recoveryCost()) || c.recoveryCost() < 0) continue;
            if (c.advancingCover() && (!c.pattern().equals(BeliefStore.RANGED) || c.cover() == null || c.spatial() != null
                    || hint.isEmpty() || hint.get().confidence() < MANTLET_THRESHOLD)) {
                reasons.add(key(c) + " BELOW_MANTLET_THRESHOLD_OR_INVALID"); continue;
            }
            if (!c.pattern().equals(BeliefStore.RANGED) || hint.isEmpty()
                    || !policy.ineligible(c.pattern(), now).equals("ELIGIBLE")) { valid.add(c); continue; }
            String dimension = hint.get().evidence().dimension();
            if (policy.performance().deferred(key(c), dimension)) {
                reasons.add(key(c) + " RECENT_COUNTER_FAILURES"); continue;
            }
            ranged.add(c);
        }
        if (!ranged.isEmpty()) {
            String dimension = hint.orElseThrow().evidence().dimension();
            var chosen = ranged.stream().max(Comparator
                    .comparingDouble((MaeveDirector.PositionCandidate c) -> policy.performance().multiplier(key(c), dimension))
                    .thenComparing(MaeveDirector.PositionCandidate::advancingCover)
                    .thenComparing(Comparator.comparingDouble(MaeveDirector.PositionCandidate::recoveryCost).reversed())).orElseThrow();
            valid.add(chosen);
            for (var c : ranged) if (c != chosen) reasons.add(key(c) + " OTHER_RANGED_VARIANT_PREFERRED frozenMultiplier="
                    + policy.performance().multiplier(key(c), dimension));
        }
        return valid;
    }
}

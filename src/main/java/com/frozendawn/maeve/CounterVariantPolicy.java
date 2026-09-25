package com.frozendawn.maeve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Variant choice stays inside its counter family and uses only frozen results. */
final class CounterVariantPolicy {
    static final double MANTLET_THRESHOLD = .90, ARCHER_THRESHOLD = .90;
    static final String MANTLET = "PLAYER_PREFERS_RANGED:MANTLET";
    static final String ARCHER = "PLAYER_PREFERS_SWORD:ARCHER";
    private CounterVariantPolicy() { }

    static String key(MaeveDirector.PositionCandidate c) { return c.keepAwayArcher() ? ARCHER : c.advancingCover() ? MANTLET : c.pattern(); }
    static String key(MaeveDirector.PositionDirective d) { return d.keepAwayArcher() ? ARCHER : d.advancingCover() ? MANTLET : d.pattern(); }
    static boolean validKey(String key) { return ARCHER.equals(key) || MANTLET.equals(key) || BeliefDescriptions.patterns().contains(key); }

    static List<MaeveDirector.PositionCandidate> options(CommitmentPolicy policy,
            List<MaeveDirector.PositionCandidate> candidates, long now, List<String> reasons) {
        var valid = new ArrayList<MaeveDirector.PositionCandidate>();
        for (var c : candidates.stream().limit(6).toList()) {
            if (!Double.isFinite(c.recoveryCost()) || c.recoveryCost() < 0) continue;
            var hint = policy.hints(now).stream().filter(h -> h.pattern().equals(c.pattern())).findFirst();
            if (c.advancingCover() && (!c.pattern().equals(BeliefStore.RANGED) || c.cover() == null || c.spatial() != null
                    || hint.isEmpty() || hint.get().confidence() < MANTLET_THRESHOLD)) {
                reasons.add(key(c) + " BELOW_MANTLET_THRESHOLD_OR_INVALID"); continue;
            }
            if (c.keepAwayArcher() && (!c.pattern().equals(BeliefStore.SWORD) || c.advancingCover() || c.cover() != null
                    || c.spatial() != null || hint.isEmpty() || hint.get().confidence() < ARCHER_THRESHOLD)) {
                reasons.add(key(c) + " BELOW_ARCHER_THRESHOLD_OR_INVALID"); continue;
            }
            valid.add(c);
        }
        for (String family : List.of(BeliefStore.RANGED, BeliefStore.SWORD)) {
            var hint = policy.hints(now).stream().filter(h -> h.pattern().equals(family)).findFirst();
            if (hint.isEmpty() || !policy.ineligible(family, now).equals("ELIGIBLE")) continue;
            String dimension = hint.get().evidence().dimension();
            var variants = valid.stream().filter(c -> c.pattern().equals(family)).toList();
            valid.removeAll(variants);
            var eligible = variants.stream().filter(c -> {
                if (!policy.performance().deferred(key(c), dimension)) return true;
                reasons.add(key(c) + " RECENT_COUNTER_FAILURES"); return false;
            }).toList();
            if (eligible.isEmpty()) continue;
            var chosen = eligible.stream().max(Comparator
                    .comparingDouble((MaeveDirector.PositionCandidate c) -> policy.performance().multiplier(key(c), dimension))
                    .thenComparing(c -> c.advancingCover() || c.keepAwayArcher())
                    .thenComparing(Comparator.comparingDouble(MaeveDirector.PositionCandidate::recoveryCost).reversed())).orElseThrow();
            valid.add(chosen);
            for (var c : eligible) if (c != chosen) reasons.add(key(c) + " OTHER_" + (family.equals(BeliefStore.RANGED) ? "RANGED" : "SWORD")
                    + "_VARIANT_PREFERRED frozenMultiplier=" + policy.performance().multiplier(key(c), dimension));
        }
        return valid;
    }
}

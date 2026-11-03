package com.frozendawn.maeve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Keep the access family within its existing four candidate slots. */
final class ExitCandidates {
    private ExitCandidates() { }

    private static boolean eligible(MaeveDirector.PositionCandidate candidate, CommitmentPolicy policy, long now) {
        return policy.ineligible(candidate.pattern(), now).equals("ELIGIBLE") && policy.hints(now).stream()
                .filter(h -> h.pattern().equals(candidate.pattern()))
                .anyMatch(h -> !policy.performance().deferred(candidate.pattern(), h.evidence().dimension()));
    }

    static List<MaeveDirector.PositionCandidate> bound(List<MaeveDirector.PositionCandidate> candidates, CommitmentPolicy policy, long now) {
        var result = new ArrayList<MaeveDirector.PositionCandidate>();
        for (String bearing : WorldModel.BEARINGS) {
            var conditional = candidates.stream().filter(c -> {
                var pair = ExitPrediction.parse(c.pattern());
                return pair != null && pair.from().equals(bearing) && eligible(c, policy, now);
            }).min(Comparator.comparingDouble(MaeveDirector.PositionCandidate::recoveryCost)
                    .thenComparing(MaeveDirector.PositionCandidate::pattern));
            if (conditional.isPresent()) result.add(conditional.get());
            else candidates.stream().filter(c -> c.pattern().equals(bearing)).findFirst().ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    static List<MaeveDirector.PositionCandidate> prefer(List<MaeveDirector.PositionCandidate> candidates,
                                                       CommitmentPolicy policy, long now, List<String> reasons) {
        // A qualifying escalation is the access-family choice. Other families still
        // compete on recovery cost; this never purchases another commitment.
        boolean escalation = candidates.stream().anyMatch(c -> ExitPrediction.parse(c.pattern()) != null && eligible(c, policy, now));
        if (!escalation) return candidates;
        return candidates.stream().filter(c -> {
            if (!WorldModel.BEARINGS.contains(c.pattern())) return true;
            reasons.add(c.pattern() + " HISTORICAL_ALTERNATIVE_PREFERRED"); return false;
        }).toList();
    }
}

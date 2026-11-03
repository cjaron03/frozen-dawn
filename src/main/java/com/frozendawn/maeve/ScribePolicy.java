package com.frozendawn.maeve;

import java.util.List;

/** §9.4b Scribe tunables. The gate count is provisional (owner candidate: 3); calibration belongs to §9.20. */
final class ScribePolicy {
    static final int GATE_BELIEFS = 3;
    static final double CONFIDENT = CommitmentPolicy.THRESHOLD;
    static final int MAX_NOTES = 5;
    /** One witnessed support. Below this a pattern is not held strongly enough to write down. */
    static final double FLOOR = BeliefPolicy.SUPPORT;
    static final double ALWAYS = .90;
    static final int MAX_MARKS_PER_LABEL = 8;
    /** One Scribe at a time; the next may be designated five in-game days after the last one ended (owner, 2026-10-08). */
    static final long COOLDOWN = 5 * 24000L;
    /**
     * Bad luck protection (owner, 2026-10-08): a natural spawn refused only for too few confident beliefs is a miss
     * when Maeve knows at least one thing about the subject. Miss n designates with chance n/PITY_MISSES.
     */
    static final int PITY_MISSES = 8;
    /** A claim that never resolves (unloaded, lost) still ends, so it cannot block the next Scribe forever. */
    static final long LIFETIME = 24000L;

    private ScribePolicy() { }

    static long confident(List<MaeveDirector.BeliefSnapshot> beliefs) {
        return beliefs.stream().filter(b -> b.confidence() >= CONFIDENT).count();
    }

    static String gate(String lifecycle, List<MaeveDirector.BeliefSnapshot> beliefs, boolean active, long lastEnded, long now) {
        if (!"ACTIVE".equals(lifecycle)) return "LIFECYCLE_" + lifecycle;
        if (active) return "SCRIBE_ALREADY_ACTIVE";
        if (lastEnded >= 0 && now >= lastEnded && now - lastEnded < COOLDOWN) return "COOLDOWN";
        return confident(beliefs) >= GATE_BELIEFS ? "ELIGIBLE" : "TOO_FEW_CONFIDENT_BELIEFS";
    }

    /** Something held at one witnessed support or more; a miss needs at least this. */
    static boolean known(List<MaeveDirector.BeliefSnapshot> beliefs) {
        return beliefs.stream().anyMatch(b -> b.confidence() >= FLOOR);
    }

    /** {@code misses} already counts this spawn; {@code roll} is uniform in [0, 1). */
    static boolean pity(int misses, double roll) {
        return misses >= PITY_MISSES || roll < (double) misses / PITY_MISSES;
    }

    /** Seen both ways and not confident: at least one support, and contradictions that have not outnumbered them. */
    static boolean split(MaeveDirector.BeliefSnapshot belief) {
        return belief.contradictions() > 0 && belief.evidence() >= belief.contradictions() && belief.confidence() < CONFIDENT;
    }

    /** Phrasing carries confidence; the record never contains a number. */
    static String certainty(MaeveDirector.BeliefSnapshot belief) {
        return split(belief) ? "INCONCLUSIVE" : certainty(belief.confidence());
    }

    static String certainty(double confidence) {
        return confidence >= ALWAYS ? "ALWAYS" : confidence >= CONFIDENT ? "FLAT" : "HEDGED";
    }
}

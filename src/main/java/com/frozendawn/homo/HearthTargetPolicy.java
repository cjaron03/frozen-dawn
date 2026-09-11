package com.frozendawn.homo;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Comparator;
import java.util.UUID;

/**
 * Pure target-selection rules for the Hearth Architect.
 *
 * <p>The Architect singles out the weakest player present rather than the
 * nearest one. Ordering is armor value ascending, so the least protected player
 * wins; ties break by proximity and then by UUID so the choice stays stable
 * from tick to tick instead of flickering between equally equipped players.
 */
public final class HearthTargetPolicy {
    /**
     * Armor points a newcomer must undercut the committed target by before it
     * is worth abandoning an assessment in progress. Roughly two armor tiers.
     */
    public static final int RETARGET_ARMOR_GAP = 8;

    public static final Comparator<Candidate> BY_VULNERABILITY =
            Comparator.comparingInt(Candidate::armorValue)
                    .thenComparingDouble(Candidate::distanceSquared)
                    .thenComparing(Candidate::id);

    private HearthTargetPolicy() {
    }

    /** A player the Architect could commit to, reduced to the values selection needs. */
    public record Candidate(UUID id, int armorValue, double distanceSquared) {
    }

    @Nullable
    public static Candidate mostVulnerable(Collection<Candidate> candidates) {
        return candidates.stream().min(BY_VULNERABILITY).orElse(null);
    }

    public static boolean warrantsRetarget(int committedArmor, int candidateArmor) {
        return committedArmor - candidateArmor >= RETARGET_ARMOR_GAP;
    }
}

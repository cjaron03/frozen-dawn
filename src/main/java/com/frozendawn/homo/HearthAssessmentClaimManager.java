package com.frozendawn.homo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Keeps the two Architects of one Major Hearth from assessing the same player.
 *
 * <p>A Major INTACT Hearth hosts two distinct Architect entities: the assessor
 * ({@code architectAssessorEntityId}) and the population resident
 * ({@link HearthPopulationRole#ARCHITECT}). Both score their watch radius with
 * {@link HearthTargetPolicy#BY_VULNERABILITY}, which is deterministic, so left to
 * themselves they always converge on the same weakest player. The saved data stays
 * correct — {@code recordArchitectAssessment} is idempotent per (player, hearth) —
 * but one Architect burns its 60 assessment ticks for nothing while a second player
 * standing in range is approached by nobody.
 *
 * <p>Claims are advisory. A claim only diverts the second Architect when another
 * candidate is actually available; with a single player present it falls through to
 * the same target and behaves exactly as it did before this class existed.
 *
 * <p>Entries are keyed by Architect rather than by Hearth. One slot per Hearth would
 * have the two Architects overwrite each other every tick; one slot per Architect lets
 * each own its own lease, and a Hearth holds at most two.
 */
public final class HearthAssessmentClaimManager {

    /**
     * How long a claim outlives its last refresh.
     *
     * <p>Claims are refreshed every tick an Architect resolves a target, so this only
     * matters when one stops resolving: killed, chunk-unloaded, or walking home. Five
     * seconds is far longer than any gap between refreshes and short enough that a dead
     * Architect stops reserving a player almost immediately.
     */
    private static final long CLAIM_TTL_TICKS = 100L;

    private static final Map<UUID, Claim> CLAIMS = new HashMap<>();

    private static long claimsGranted;
    private static long divertsCaused;

    private HearthAssessmentClaimManager() {
    }

    /**
     * Whether a different Architect at this same Hearth currently holds {@code playerId}.
     *
     * <p>Per-Hearth rather than global on purpose: {@code recordArchitectAssessment} is
     * keyed by (player, hearth), so two Architects at two different Hearths assessing one
     * player are recording different facts, not duplicating one.
     */
    public static boolean claimedByOther(
            long gameTime, UUID hearthId, UUID architectId, UUID playerId) {
        if (hearthId == null || architectId == null || playerId == null) {
            return false;
        }
        expireStaleClaims(gameTime);
        for (Map.Entry<UUID, Claim> entry : CLAIMS.entrySet()) {
            if (entry.getKey().equals(architectId)) {
                continue;
            }
            Claim claim = entry.getValue();
            if (claim.hearthId().equals(hearthId) && claim.playerId().equals(playerId)) {
                return true;
            }
        }
        return false;
    }

    /** Takes or refreshes this Architect's claim on {@code playerId}. */
    public static void claim(
            long gameTime, UUID hearthId, UUID architectId, UUID playerId) {
        if (hearthId == null || architectId == null || playerId == null) {
            return;
        }
        Claim previous = CLAIMS.get(architectId);
        if (previous == null || !previous.playerId().equals(playerId)) {
            claimsGranted++;
        }
        CLAIMS.put(architectId, new Claim(
                gameTime + CLAIM_TTL_TICKS, hearthId, playerId));
    }

    /** Drops this Architect's claim immediately rather than waiting for the lease. */
    public static void release(UUID architectId) {
        if (architectId != null) {
            CLAIMS.remove(architectId);
        }
    }

    /**
     * The claimable subset of {@code candidates}, or all of them when the claims would
     * leave this Architect with nothing.
     *
     * <p>The fallthrough is what keeps single-player behaviour unchanged: the lone player
     * is claimed by the other Architect, filtering empties the list, and this hands back
     * the unfiltered list so the target is picked exactly as it always was.
     */
    public static <T> List<T> filterClaimed(
            long gameTime,
            UUID hearthId,
            UUID architectId,
            List<T> candidates,
            Function<T, UUID> idOf) {
        if (hearthId == null || architectId == null || candidates.size() < 2) {
            return candidates;
        }
        List<T> unclaimed = new ArrayList<>(candidates.size());
        for (T candidate : candidates) {
            if (!claimedByOther(gameTime, hearthId, architectId, idOf.apply(candidate))) {
                unclaimed.add(candidate);
            }
        }
        if (unclaimed.isEmpty() || unclaimed.size() == candidates.size()) {
            return candidates;
        }
        divertsCaused++;
        return unclaimed;
    }

    private static void expireStaleClaims(long gameTime) {
        Iterator<Map.Entry<UUID, Claim>> claims = CLAIMS.entrySet().iterator();
        while (claims.hasNext()) {
            if (gameTime > claims.next().getValue().expiresAtGameTime()) {
                claims.remove();
            }
        }
    }

    public static String statusLine() {
        return "held=" + CLAIMS.size()
                + " granted=" + claimsGranted
                + " diverts=" + divertsCaused;
    }

    public static void reset() {
        CLAIMS.clear();
        claimsGranted = 0L;
        divertsCaused = 0L;
    }

    private record Claim(long expiresAtGameTime, UUID hearthId, UUID playerId) {
    }
}

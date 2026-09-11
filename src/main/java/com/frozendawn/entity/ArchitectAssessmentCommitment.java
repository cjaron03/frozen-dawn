package com.frozendawn.entity;

import com.frozendawn.homo.HearthTargetPolicy;
import com.frozendawn.homo.HearthTargetPolicy.Candidate;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Holds the Architect to one assessment target until something real releases it.
 *
 * <p>Distance changes every tick, so a selector that re-picks the nearest player
 * each tick can never finish a 60-tick assessment with two players trading
 * places. This commits to a target instead and only lets go on hard events: the
 * target leaves the watch radius, logs out, is badly outclassed by a far weaker
 * newcomer, or the Architect takes damage.
 *
 * <p>A target that walks out of range is remembered as a bookmark rather than
 * forgotten. If it comes back it reclaims the commitment immediately, without
 * being re-scored against whoever the Architect picked up meanwhile. Only one
 * bookmark is kept — a second departure replaces the first.
 *
 * <p>Only distance earns a bookmark. A target who died, logged out or switched
 * to creative is dropped without one and is re-scored normally on return: death
 * changes the very armor being scored, and a creative toggle should not buy
 * priority over the players who stayed.
 */
final class ArchitectAssessmentCommitment {
    @Nullable
    private UUID committedId;
    @Nullable
    private UUID suspendedId;
    private int ticks;

    /**
     * @param candidates    players inside the watch radius this tick
     * @param targetableIds every player this Architect may target at all, in or out
     *                      of range — the alive, non-creative, non-spectator set,
     *                      not merely everyone connected
     * @return the player to assess, or null when nobody is in range
     */
    @Nullable
    UUID resolve(List<Candidate> candidates, Set<UUID> targetableIds) {
        forgetUntargetablePlayers(targetableIds);

        if (suspendedId != null && contains(candidates, suspendedId)) {
            return commitTo(suspendedId);
        }

        Candidate committed = find(candidates, committedId);
        if (committed != null) {
            Candidate challenger = HearthTargetPolicy.mostVulnerable(candidates);
            if (challenger != null && HearthTargetPolicy.warrantsRetarget(
                    committed.armorValue(), challenger.armorValue())) {
                return commitTo(challenger.id());
            }
            return committedId;
        }

        if (committedId != null) {
            suspendedId = committedId;
            committedId = null;
            ticks = 0;
        }

        Candidate pick = HearthTargetPolicy.mostVulnerable(candidates);
        return pick == null ? null : commitTo(pick.id());
    }

    /**
     * Hard-commits to one target, bypassing vulnerability scoring entirely.
     *
     * <p>Used for retaliation: whoever just hit the Architect becomes the
     * commitment outright, rather than being thrown back into the pool and
     * scored against everyone else in range.
     */
    void commitToTarget(UUID id) {
        commitTo(id);
    }

    /** Drops the commitment and the bookmark outright, as on taking damage. */
    void release() {
        committedId = null;
        suspendedId = null;
        ticks = 0;
    }

    int advanceTicks() {
        return ++ticks;
    }

    int ticks() {
        return ticks;
    }

    void resetTicks() {
        ticks = 0;
    }

    @Nullable
    UUID committedId() {
        return committedId;
    }

    @Nullable
    UUID suspendedId() {
        return suspendedId;
    }

    /**
     * Drops any id that is no longer targetable — dead, disconnected, or switched
     * to creative. Deliberately not a suspend: see the class javadoc.
     */
    private void forgetUntargetablePlayers(Set<UUID> targetableIds) {
        if (committedId != null && !targetableIds.contains(committedId)) {
            committedId = null;
            ticks = 0;
        }
        if (suspendedId != null && !targetableIds.contains(suspendedId)) {
            suspendedId = null;
        }
    }

    private UUID commitTo(UUID id) {
        committedId = id;
        if (id.equals(suspendedId)) {
            suspendedId = null;
        }
        ticks = 0;
        return id;
    }

    @Nullable
    private static Candidate find(List<Candidate> candidates, @Nullable UUID id) {
        if (id == null) {
            return null;
        }
        for (Candidate candidate : candidates) {
            if (candidate.id().equals(id)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean contains(List<Candidate> candidates, UUID id) {
        return find(candidates, id) != null;
    }
}

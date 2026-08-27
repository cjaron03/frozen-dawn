package com.frozendawn.homo;

/** Pure roster and casualty rules for the Master Architect encounter. */
public final class HearthCombatRosterPolicy {
    public static final int MAJOR_CONGREGATION_SIZE = 6;
    public static final int MIN_DISPATCHED = 2;
    public static final int MAX_DISPATCHED = 3;

    private HearthCombatRosterPolicy() {
    }

    public static boolean canFreezeMajorRoster(
            int candidateCount, int livingCandidateCount, int spentCount) {
        int accountedSlots = Math.max(0, candidateCount) + Math.max(0, spentCount);
        int resolvedSlots = Math.max(0, livingCandidateCount) + Math.max(0, spentCount);
        return accountedSlots >= MAJOR_CONGREGATION_SIZE
                && resolvedSlots >= MAJOR_CONGREGATION_SIZE;
    }

    public static int dispatchedCount(int livingResidents) {
        if (livingResidents <= 1) {
            return 0;
        }
        if (livingResidents < 4) {
            return 1;
        }
        return livingResidents >= 6 ? MAX_DISPATCHED : MIN_DISPATCHED;
    }

    public static int dispatchedCountWithReserve(
            int livingResidents, int reservedBystanders) {
        int availableAfterReserve = Math.max(0,
                livingResidents - Math.max(0, reservedBystanders));
        return Math.min(dispatchedCount(livingResidents), availableAfterReserve);
    }

    public static boolean canAttack(HearthEncounterRole role) {
        return role == HearthEncounterRole.DISPATCHED
                || role == HearthEncounterRole.RESERVED;
    }

    public static boolean canBecomeTether(HearthEncounterRole role) {
        return role == HearthEncounterRole.RESERVED;
    }

    public static boolean recordsPermanentCasualty(
            HearthEncounterRole role, boolean directlyAttributedToPlayer) {
        return directlyAttributedToPlayer
                && role != HearthEncounterRole.DISPATCHED
                && role != HearthEncounterRole.RESERVED;
    }

    public static boolean suppressReplacement(
            boolean rosterInitialized, boolean masterAlive) {
        return rosterInitialized && masterAlive;
    }
}

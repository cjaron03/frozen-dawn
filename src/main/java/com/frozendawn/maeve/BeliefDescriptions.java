package com.frozendawn.maeve;

import java.util.List;

/** Authored meanings describe observations only, never inferred health or spatial knowledge. */
final class BeliefDescriptions {
    private BeliefDescriptions() { }

    static List<String> patterns() {
        return List.of(BeliefStore.RANGED, BeliefStore.RECOVERY);
    }

    static String meaning(String pattern) {
        return switch (pattern) {
            case BeliefStore.RANGED -> "This player prefers ranged attacks against Architects.";
            case BeliefStore.RECOVERY -> "This player uses recovery items at positions without open sky.";
            default -> "No authored description is registered for this stored pattern.";
        };
    }

    static String support(String pattern) {
        return switch (pattern) {
            case BeliefStore.RANGED -> "A witnessed player-owned projectile damages the observing Architect.";
            case BeliefStore.RECOVERY -> "Witnessed completion of a healing/regeneration potion or golden/enchanted golden apple where canSeeSky=false.";
            default -> "No supporting-action rule is registered for this stored pattern.";
        };
    }

    static String contradiction(String pattern) {
        return switch (pattern) {
            case BeliefStore.RANGED -> "Witnessed direct player melee damage to the observing Architect.";
            case BeliefStore.RECOVERY -> "Witnessed completion of the same recovery items where canSeeSky=true.";
            default -> "No contradictory-action rule is registered for this stored pattern.";
        };
    }

    static String limitation(String pattern) {
        return switch (pattern) {
            case BeliefStore.RANGED -> "Local witnessed attacks cannot establish the player's overall combat habits.";
            case BeliefStore.RECOVERY -> "Item use does not reveal hidden health, healing effectiveness, or room identity.";
            default -> "Interpret the retained action codes directly; no semantic interpretation is available.";
        };
    }
}

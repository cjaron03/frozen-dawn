package com.frozendawn.maeve;

import java.util.List;

/** Authored meanings describe observations only, never inferred health or room identity. */
final class BeliefDescriptions {
    private BeliefDescriptions() { }

    static List<String> patterns() {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(BeliefStore.RANGED, BeliefStore.RECOVERY, BeliefStore.PURSUIT, BeliefStore.SWORD),
                WorldModel.BEARINGS.stream()).toList();
    }

    static boolean known(String pattern) { return patterns().contains(pattern) || ExitPrediction.parse(pattern) != null; }

    static String meaning(String pattern) {
        var exit = ExitPrediction.parse(pattern);
        if (exit != null) return "After interception at " + exit.from() + ", this player visibly escapes toward " + exit.to() + " in observed shelter " + exit.area() + ".";
        if (WorldModel.BEARINGS.contains(pattern)) return "This player emerges toward " + pattern.substring("RETREAT_BEARING_".length())
                + " relative to the centroid of locally observed covered positions.";
        return switch (pattern) {
            case BeliefStore.SWORD -> "This player prefers direct sword attacks against Architects.";
            case BeliefStore.PURSUIT -> "When an Architect visibly withdraws, this player follows it.";
            case BeliefStore.RANGED -> "This player prefers ranged attacks against Architects.";
            case BeliefStore.RECOVERY -> "This player uses recovery items at positions without open sky.";
            default -> "No authored description is registered for this stored pattern.";
        };
    }

    static String support(String pattern) {
        if (ExitPrediction.parse(pattern) != null) return "An ordinary interception has arrived; an eligible witness sees a continuous outward crossing at the alternative. No recursive learning.";
        if (WorldModel.BEARINGS.contains(pattern)) return "The same Architect sees a continuous covered-to-open crossing along this bearing.";
        return switch (pattern) {
            case BeliefStore.SWORD -> "A witnessed direct sword attack damages the Architect or is stopped by its raised shield.";
            case BeliefStore.PURSUIT -> "The same Architect sees itself withdraw at least two blocks and the player follow at least two blocks during a continuous five-second local observation.";
            case BeliefStore.RANGED -> "A witnessed player-owned projectile damages the observing Architect.";
            case BeliefStore.RECOVERY -> "Witnessed completion of a healing/regeneration potion or golden/enchanted golden apple where canSeeSky=false.";
            default -> "No supporting-action rule is registered for this stored pattern.";
        };
    }

    static String contradiction(String pattern) {
        if (ExitPrediction.parse(pattern) != null) return "A different outward response during an arrived primary watch, or a witnessed escape elsewhere while guarding the predicted alternative.";
        if (WorldModel.BEARINGS.contains(pattern)) return "A witnessed crossing along another bearing, or direct sight of an obstructed remembered crossing.";
        return switch (pattern) {
            case BeliefStore.SWORD -> "A witnessed projectile or direct non-sword melee attack damages the Architect or meets its raised shield.";
            case BeliefStore.PURSUIT -> "During the whole visible withdrawal window the player does not follow; a lost sightline is inconclusive.";
            case BeliefStore.RANGED -> "Witnessed direct player melee damage to the observing Architect.";
            case BeliefStore.RECOVERY -> "Witnessed completion of the same recovery items where canSeeSky=true.";
            default -> "No contradictory-action rule is registered for this stored pattern.";
        };
    }

    static String limitation(String pattern) {
        if (ExitPrediction.parse(pattern) != null) return "One conditional level, scoped to one observed shelter. No disappearance inference or immediate retargeting; a return to the original exit beats the prediction.";
        if (WorldModel.BEARINGS.contains(pattern)) return "The shelter centroid is an estimate from observed covered positions; unseen routes and block changes remain unknown.";
        return switch (pattern) {
            case BeliefStore.SWORD -> "Only the weapon used for a witnessed attack is read; low ranged confidence and held items are not sword evidence.";
            case BeliefStore.PURSUIT -> "A sampled movement response does not reveal intent, hidden health, or a future input.";
            case BeliefStore.RANGED -> "Local witnessed attacks cannot establish the player's overall combat habits.";
            case BeliefStore.RECOVERY -> "Item use does not reveal hidden health, healing effectiveness, or room identity.";
            default -> "Interpret the retained action codes directly; no semantic interpretation is available.";
        };
    }
}

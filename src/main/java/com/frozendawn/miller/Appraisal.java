package com.frozendawn.miller;

import com.frozendawn.miller.Drives.Drive;
import com.frozendawn.miller.Feelings.Feeling;
import com.frozendawn.shepherd.ShepherdAffinity.Deed;
import java.util.EnumMap;
import java.util.Map;

/**
 * Scores what she saw, as the OCC model does: desirability against her wants gives joy, distress, hope or fear, and
 * praise of whoever caused it gives gratitude, reproach or guilt. A player's death has no rule, and never will.
 */
final class Appraisal {
    enum Agent {
        PLAYER,
        WORLD,
        SELF
    }

    /** {@code prospect} means it may yet happen. {@code drive} may be null: then it lands at full weight whatever she wants. */
    record Rule(double desirability, double praise, boolean prospect, Drive drive, double feeds, Agent agent) { }

    private Appraisal() { }

    static Rule rule(Deed deed) {
        return switch (deed) {
            case WARM_SHELTER -> new Rule(0.6, 0.3, false, Drive.STAYING_ON, 0.4, Agent.PLAYER);
            case READ_LOG -> new Rule(0.5, 0.2, true, Drive.CURIOSITY, 0.5, Agent.PLAYER);
            case CLEAR_NIGHT -> new Rule(0.7, 0, false, Drive.SKY, 0.6, Agent.WORLD);
            case ARCHIVIST_KILLED -> new Rule(-0.7, -0.6, false, Drive.OTHERS, 0, Agent.PLAYER);
            case SHELTER_FROZE -> new Rule(-0.3, -0.4, true, null, 0, Agent.PLAYER);
            case AIR_FAILING -> new Rule(-0.6, 0, true, null, 0, Agent.WORLD);
            case LEFT_BEHIND -> new Rule(-0.3, -0.5, false, Drive.COMPANY, 0, Agent.PLAYER);
            case CALLED_SHEPHERD -> new Rule(-0.1, -0.4, false, null, 0, Agent.PLAYER);
        };
    }

    /** What she wants makes it matter more: half weight when the want is fed, full when it is not. */
    static double scale(Rule rule, Drives drives, long now) {
        return rule.drive() == null ? 1 : 0.5 + 0.5 * drives.value(rule.drive(), now);
    }

    static Map<Feeling, Double> intensities(Rule rule, double scale) {
        var feelings = new EnumMap<Feeling, Double>(Feeling.class);
        double good = rule.desirability() * scale, praise = rule.praise() * scale;
        if (good > 0) {
            if (rule.prospect()) feelings.put(Feeling.HOPE, good);
            else feelings.put(Feeling.JOY, good * Temperament.DRY);
        }
        if (good < 0) feelings.put(rule.prospect() ? Feeling.FEAR : Feeling.DISTRESS, -good);
        if (rule.agent() == Agent.PLAYER && praise > 0) feelings.put(Feeling.GRATITUDE, praise * Temperament.WARM);
        if (rule.agent() == Agent.PLAYER && praise < 0) feelings.put(Feeling.REPROACH, -praise);
        if (rule.agent() == Agent.SELF && praise < 0) feelings.put(Feeling.GUILT, -praise);
        feelings.replaceAll((feeling, intensity) -> Math.min(1, intensity));
        return feelings;
    }
}

package com.frozendawn.miller;

import com.frozendawn.miller.Attention.Focus;
import com.frozendawn.miller.Attention.Source;

/** What she wants to do about it, by plain utility. S3's budgets still decide whether she gets to. */
public final class Urges {
    /** A tie goes to the later, so she errs quiet. */
    public enum Urge {
        SPEAK,
        LOOK,
        APPROACH,
        STAY_QUIET
    }

    static final double QUIET = 0.35;
    private static final double WANT_HOLDS_BACK = 0.2;

    private Urges() { }

    static Urge pick(Focus focus, double curiosity, double company) {
        var pick = Urge.STAY_QUIET;
        double best = QUIET;
        double look = curiosity - WANT_HOLDS_BACK, approach = company - WANT_HOLDS_BACK;
        if (approach > best) {
            pick = Urge.APPROACH;
            best = approach;
        }
        if (look > best) {
            pick = Urge.LOOK;
            best = look;
        }
        return focus.source() != Source.DRIVE && focus.strength() > best ? Urge.SPEAK : pick;
    }
}

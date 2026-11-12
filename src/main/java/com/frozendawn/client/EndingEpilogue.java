package com.frozendawn.client;

import java.util.ArrayList;
import java.util.List;

/**
 * The beats after the credits, in order. Both endings play the same welcome (CH-34); the truth
 * ending then adds the compliance cut, a held black and the closing card.
 */
public final class EndingEpilogue {
    private static final int BASE_ENDING_COMPLETE_TICKS = 8 * 60 * 20 + 20 * 20;
    private static final int CARD_FADE_IN_TICKS = 28;

    private static final int TEAL_TITLE = 0xFFA7FFEA;
    private static final int TEAL_BODY = 0xFFD9FFF6;
    private static final int TEAL_ACCENT = 0xCC44E6A8;

    private static final List<Beat> WELCOME = List.of(
            new Beat(Kind.FADE, 5 * 20, null, 0, 0, 0),
            new Beat(Kind.COLONY, 6 * 20, "screen.frozendawn.ending.mars_colony", TEAL_TITLE, TEAL_BODY, TEAL_ACCENT),
            new Beat(Kind.SIGNAL, 7 * 20, "screen.frozendawn.ending.welcome", TEAL_TITLE, TEAL_BODY, TEAL_ACCENT));

    private static final List<Beat> TRUTH_TAIL = List.of(
            new Beat(Kind.COMPLIANCE, 16 * 20, "screen.frozendawn.ending.compliance",
                    0xFFFF8B8B, 0xFFFFC0C0, 0xCCFF5C5C),
            new Beat(Kind.HOLD, 2 * 20, null, 0, 0, 0),
            new Beat(Kind.END_CARD, 6 * 20, "screen.frozendawn.ending.truth", 0, 0xFFFFF0B0, 0));

    private static final List<Beat> PLAIN = WELCOME;
    private static final List<Beat> TRUTH;

    static {
        List<Beat> truth = new ArrayList<>(WELCOME);
        truth.addAll(TRUTH_TAIL);
        TRUTH = List.copyOf(truth);
    }

    public enum Kind { FADE, COLONY, SIGNAL, COMPLIANCE, HOLD, END_CARD }

    /** One beat: its kind, length, the lang key it draws (or null) and its panel colors. */
    public record Beat(Kind kind, int ticks, String key, int titleColor, int bodyColor, int accentColor) {
    }

    private EndingEpilogue() {
    }

    public static List<Beat> beats(boolean truth) {
        return truth ? TRUTH : PLAIN;
    }

    public static int length(boolean truth) {
        int total = 0;
        for (Beat beat : beats(truth)) {
            total += beat.ticks();
        }
        return total;
    }

    /** Ticks into the epilogue at which the first beat of this kind starts, or -1 if the ending has none. */
    public static int startOf(boolean truth, Kind kind) {
        int start = 0;
        for (Beat beat : beats(truth)) {
            if (beat.kind() == kind) {
                return start;
            }
            start += beat.ticks();
        }
        return -1;
    }

    /** Whether the welcome stays up to the end of its beat: in Ending B it holds until it glitches into compliance. */
    public static boolean signalHolds(boolean truth) {
        return truth;
    }

    /** The End? card's opacity: in over 28 ticks, then held, past its beat, until the screen closes. */
    public static float cardAlpha(int ticksIntoCard) {
        return Math.max(0.0F, Math.min(1.0F, ticksIntoCard / (float) CARD_FADE_IN_TICKS));
    }

    /**
     * The tick the ending screen can be left. The truth ending's hold and card add to its end rather than
     * shortening the credits, so the credit roll, skip window and music fade start where they did.
     */
    public static int endingCompleteTicks(boolean truth) {
        if (!truth) {
            return BASE_ENDING_COMPLETE_TICKS;
        }
        int added = 0;
        for (Beat beat : TRUTH_TAIL) {
            if (beat.kind() == Kind.HOLD || beat.kind() == Kind.END_CARD) {
                added += beat.ticks();
            }
        }
        return BASE_ENDING_COMPLETE_TICKS + added;
    }
}

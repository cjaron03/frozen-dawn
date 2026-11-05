package com.frozendawn.miller;

/** The things she will not do. She says no once, and the same ask again soon after works: a person objecting, never a broken button. */
public final class Refusal {
    public enum Kind {
        CLEAR_NIGHT,
        ARCHIVIST
    }

    public static final int YIELD_TICKS = 600;
    private static final double SKY_HOLDS_HER = 0.7;

    private Refusal() { }

    /** Null if she goes. */
    public static Kind check(boolean calm, boolean clearNight, double sky, boolean archivistSeen, boolean archivistKilled) {
        if (!calm) return null;
        if (clearNight && sky >= SKY_HOLDS_HER) return Kind.CLEAR_NIGHT;
        return archivistSeen && archivistKilled ? Kind.ARCHIVIST : null;
    }
}

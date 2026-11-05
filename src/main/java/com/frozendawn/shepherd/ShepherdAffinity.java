package com.frozendawn.shepherd;

/** What her score means, as pure logic. */
public final class ShepherdAffinity {
    private static final int FRIENDLY_AT = 250, TRUSTED_AT = 500, CLOSE_AT = 850, WARY_BELOW = -250;

    private ShepherdAffinity() {}

    public enum Tier {
        WARY,
        NEUTRAL,
        FRIENDLY,
        TRUSTED,
        CLOSE;

        public boolean atLeast(Tier tier) {
            return compareTo(tier) >= 0;
        }
    }

    public static Tier of(int score) {
        if (score < WARY_BELOW) return Tier.WARY;
        if (score >= CLOSE_AT) return Tier.CLOSE;
        if (score >= TRUSTED_AT) return Tier.TRUSTED;
        return score >= FRIENDLY_AT ? Tier.FRIENDLY : Tier.NEUTRAL;
    }
}

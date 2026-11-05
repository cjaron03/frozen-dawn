package com.frozendawn.shepherd;

import com.frozendawn.shepherd.ShepherdFace.Mood;

/** What her score means and what moves it, as pure logic. */
public final class ShepherdAffinity {
    private static final int FRIENDLY_AT = 250, TRUSTED_AT = 500, CLOSE_AT = 850, WARY_BELOW = -250;

    private static final int MINUTE = 1200, DAY = ShepherdPolicy.DAY_TICKS;
    /** At Wary a reaction is a recording. Which one depends only on whether she minded. */
    public static final String WARY_LIKED = "shepherd.react.wary.liked", WARY_DISLIKED = "shepherd.react.wary.disliked";

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

    /** The things she notices. {@code line} is a lang key, or null where she keeps it to herself. */
    public enum Deed {
        WARM_SHELTER(5, DAY, Mood.HAPPY, "shepherd.react.warm_shelter"),
        READ_LOG(10, MINUTE, Mood.NEUTRAL, "shepherd.react.read_log"),
        CLEAR_NIGHT(5, DAY, Mood.HAPPY, null),
        ARCHIVIST_KILLED(-25, MINUTE, Mood.SAD, "shepherd.react.archivist_killed"),
        SHELTER_FROZE(-10, DAY, Mood.UNIMPRESSED, "shepherd.react.shelter_froze"),
        AIR_FAILING(-10, DAY, Mood.ALARMED, "shepherd.react.air_failing"),
        /** Its cooldown is the day it takes to earn. */
        LEFT_BEHIND(-10, 0, Mood.UNIMPRESSED, "shepherd.react.left_behind"),
        CALLED_SHEPHERD(-5, MINUTE, Mood.UNIMPRESSED, "shepherd.react.called_shepherd");

        private final int score, cooldownTicks;
        private final Mood mood;
        private final String line;

        Deed(int score, int cooldownTicks, Mood mood, String line) {
            this.score = score;
            this.cooldownTicks = cooldownTicks;
            this.mood = mood;
            this.line = line;
        }

        public int score() { return score; }
        public int cooldownTicks() { return cooldownTicks; }
        public Mood mood() { return mood; }
        public String line() { return line; }
        public boolean liked() { return score > 0; }
    }
}

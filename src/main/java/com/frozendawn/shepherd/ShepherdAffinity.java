package com.frozendawn.shepherd;

import com.frozendawn.shepherd.ShepherdFace.Mood;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;

/** What her score means and what moves it, as pure logic. */
public final class ShepherdAffinity {
    private static final int FRIENDLY_AT = 250, TRUSTED_AT = 500, CLOSE_AT = 850, WARY_BELOW = -250;
    /** Below this she gives you only a recording. It reads the shared score, and the score climbing back ends it. */
    public static final int SILENT_BELOW = -600;

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

    /** One thing she can say: a lang key, and a heavier one for when swearing is on, which may be null. */
    public record Say(String key, String heavyKey) {
        public Say(String key) {
            this(key, null);
        }

        public String key(boolean heavy) {
            return heavy && heavyKey != null ? heavyKey : key;
        }
    }

    /** One of {@code lines}, but not {@code last} while there is another. Null if there is nothing to say. */
    public static Say pick(List<Say> lines, Say last, RandomSource random) {
        if (lines.isEmpty()) return null;
        var choices = lines.stream().filter(say -> !say.equals(last)).toList();
        return choices.isEmpty() ? lines.getFirst() : choices.get(random.nextInt(choices.size()));
    }

    private static List<Say> keys(String deed, int variants) {
        var lines = new ArrayList<Say>();
        for (int i = 1; i <= variants; i++) lines.add(new Say("shepherd.react." + deed + "." + i));
        return List.copyOf(lines);
    }

    /** The things she notices. A deed with no lines she keeps to herself. */
    public enum Deed {
        WARM_SHELTER(5, DAY, Mood.HAPPY, keys("warm_shelter", 3)),
        READ_LOG(10, MINUTE, Mood.NEUTRAL, keys("read_log", 3)),
        CLEAR_NIGHT(5, DAY, Mood.HAPPY, List.of()),
        ARCHIVIST_KILLED(-25, MINUTE, Mood.SAD, keys("archivist_killed", 2)),
        SHELTER_FROZE(-10, DAY, Mood.UNIMPRESSED, keys("shelter_froze", 2)),
        AIR_FAILING(-10, DAY, Mood.ALARMED, List.of(new Say("shepherd.react.air_failing.1"),
                new Say("shepherd.react.air_failing.2", "shepherd.react.air_failing.2.heavy"), new Say("shepherd.react.air_failing.3"))),
        /** Its cooldown is the day it takes to earn. */
        LEFT_BEHIND(-10, 0, Mood.UNIMPRESSED, keys("left_behind", 2)),
        CALLED_SHEPHERD(-5, MINUTE, Mood.UNIMPRESSED, keys("called_shepherd", 3));

        private final int score, cooldownTicks;
        private final Mood mood;
        private final List<Say> lines;

        Deed(int score, int cooldownTicks, Mood mood, List<Say> lines) {
            this.score = score;
            this.cooldownTicks = cooldownTicks;
            this.mood = mood;
            this.lines = lines;
        }

        public int score() { return score; }
        public int cooldownTicks() { return cooldownTicks; }
        public Mood mood() { return mood; }
        public List<Say> lines() { return lines; }
        public boolean liked() { return score > 0; }
    }
}

package com.frozendawn.miller;

import net.minecraft.nbt.CompoundTag;

/**
 * Silence wears at her and talking restores her, as the units always did, which is why someone was posted to talk to
 * this one. It costs her line variety and a moment before she answers. It never touches a tier or a keepsake.
 */
final class Coherence {
    static final double FLOOR = 0.3, TALK_ADDS = 0.2, SLOW_BELOW = 0.7, VERY_SLOW_BELOW = 0.5;
    static final long HALF_LIFE = 72000;

    /** Null until she first notices anything, so a world older than this state does not arrive worn down. */
    private Fading level;

    void begin(long now) {
        if (level == null) level = new Fading(1, now);
    }

    /** At the moment it was set it is that value exactly, so a threshold means what it says. */
    double value(long now) {
        if (level == null) return 1;
        return now <= level.at() ? level.value() : level.toward(FLOOR, HALF_LIFE, now);
    }

    void set(double value, long now) {
        level = new Fading(Math.max(FLOOR, Math.min(1, value)), now);
    }

    /** One conversation, counted once by the state's {@code heardTalk}. */
    void spoken(long now) {
        set(value(now) + TALK_ADDS, now);
    }

    /** How long a reply waits, in half laps of the indicator: 0, 1 or 2. */
    int halfLaps(long now) {
        double value = value(now);
        return value >= SLOW_BELOW ? 0 : value >= VERY_SLOW_BELOW ? 1 : 2;
    }

    /** How many of a pool's first variants she can reach. At the floor she repeats herself. */
    int variety(int lines, long now) {
        return Math.min(lines, Math.max(1, (int) Math.ceil(lines * value(now))));
    }

    void age(long ticks) {
        if (level != null) level = level.aged(ticks);
    }

    void clear() {
        level = null;
    }

    CompoundTag save() {
        return level == null ? new CompoundTag() : level.save();
    }

    static Coherence load(CompoundTag tag) {
        var coherence = new Coherence();
        if (!tag.contains("value")) return coherence;
        var saved = Fading.load(tag);
        coherence.set(saved.value(), saved.at());
        return coherence;
    }
}

package com.frozendawn.defense;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What the Terminus has taken from its operator, in half hearts, and the game time the next half comes back. A
 * discharge takes two hearts; half a heart returns every half Minecraft day, through death and respawn alike.
 */
public record TerminusToll(int taken, long returnsAt) {
    public static final int TAKEN_PER_DISCHARGE = 4;
    public static final long HALF_HEART_TICKS = 12_000;
    public static final TerminusToll NONE = new TerminusToll(0, 0);
    public static final Codec<TerminusToll> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("taken").forGetter(TerminusToll::taken),
            Codec.LONG.fieldOf("returns_at").forGetter(TerminusToll::returnsAt)).apply(i, TerminusToll::new));

    /** Two more hearts taken at {@code now}; a clock already running keeps running. */
    public TerminusToll discharge(long now) {
        return new TerminusToll(taken + TAKEN_PER_DISCHARGE, taken > 0 ? returnsAt : now + HALF_HEART_TICKS);
    }

    /** Half a heart back if one is due at {@code now}, and the next due half a day on. */
    public TerminusToll tick(long now) {
        if (taken <= 0 || now < returnsAt) return this;
        return taken == 1 ? NONE : new TerminusToll(taken - 1, now + HALF_HEART_TICKS);
    }
}

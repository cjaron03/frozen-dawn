package com.frozendawn.miller;

import net.minecraft.nbt.CompoundTag;

/**
 * A value and the game time it was set. It is read in closed form, so reading never changes it and how often anyone
 * looks cannot change a result. {@link StrictMath} keeps the same events giving the same bits on every machine.
 */
record Fading(double value, long at) {
    double toward(double target, long halfLife, long now) {
        return target + (value - target) * StrictMath.pow(0.5, Math.max(0, now - at) / (double) halfLife);
    }

    Fading set(double value, long now) {
        return new Fading(value, now);
    }

    /** As if {@code ticks} more had passed. */
    Fading aged(long ticks) {
        return new Fading(value, at - ticks);
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putDouble("value", value);
        tag.putLong("at", at);
        return tag;
    }

    /** A damaged save still loads between 0 and 1. */
    static Fading load(CompoundTag tag) {
        double value = tag.getDouble("value");
        return new Fading(Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0, tag.getLong("at"));
    }
}

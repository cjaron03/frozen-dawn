package com.frozendawn.miller;

import net.minecraft.nbt.CompoundTag;

/** One moment she held on to. {@code recalled} counts how often she has gone back to it, and {@code recalledAt} means nothing until she has. */
public record Keepsake(long time, String kind, double salience, int recalled, long recalledAt) {
    Keepsake strengthened(double gain) {
        return new Keepsake(time, kind, salience + (1 - salience) * gain, recalled, recalledAt);
    }

    Keepsake recalled(double gain, long now) {
        return new Keepsake(time, kind, salience + (1 - salience) * gain, recalled + 1, now);
    }

    Keepsake aged(long ticks) {
        return new Keepsake(time - ticks, kind, salience, recalled, recalled > 0 ? recalledAt - ticks : recalledAt);
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putLong("time", time);
        tag.putString("kind", kind);
        tag.putDouble("salience", salience);
        tag.putInt("recalled", recalled);
        tag.putLong("recalledAt", recalledAt);
        return tag;
    }

    /** Null if the entry is too damaged to say what it was or how much it mattered. */
    static Keepsake load(CompoundTag tag) {
        double salience = tag.getDouble("salience");
        if (tag.getString("kind").isEmpty() || !Double.isFinite(salience) || salience <= 0 || salience > 1) return null;
        return new Keepsake(tag.getLong("time"), tag.getString("kind"), salience, Math.max(0, tag.getInt("recalled")), tag.getLong("recalledAt"));
    }
}

package com.frozendawn.miller;

import com.frozendawn.shepherd.ShepherdFace.Mood;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;

/** What she feels, the OCC model's seven that apply to her. Each fades on its own half-life of game time. */
public final class Feelings {
    private static final long DISTRESS_HALF_LIFE = 12000;

    public enum Feeling {
        JOY(6000, Mood.HAPPY),
        DISTRESS(DISTRESS_HALF_LIFE, Mood.SAD),
        HOPE(12000, Mood.THINKING),
        FEAR(2400, Mood.ALARMED),
        GRATITUDE(24000, Mood.AMUSED),
        REPROACH((long) (Temperament.STUBBORN * DISTRESS_HALF_LIFE), Mood.UNIMPRESSED),
        GUILT(72000, Mood.SAD);

        private final long halfLife;
        private final Mood mood;

        Feeling(long halfLife, Mood mood) {
            this.halfLife = halfLife;
            this.mood = mood;
        }

        public long halfLife() { return halfLife; }
        public Mood mood() { return mood; }
    }

    private final Map<Feeling, Fading> values = new EnumMap<>(Feeling.class);

    public double value(Feeling feeling, long now) {
        var value = values.get(feeling);
        return value == null ? 0 : value.toward(0, feeling.halfLife, now);
    }

    void raise(Feeling feeling, double intensity, long now) {
        double value = value(feeling, now);
        values.put(feeling, new Fading(value + (1 - value) * intensity, now));
    }

    /** The feeling her face would show, or null when nothing reaches it. A tie goes to the earlier feeling. */
    Feeling strongest(long now) {
        Feeling strongest = null;
        double most = 0;
        for (Feeling feeling : Feeling.values()) {
            double value = value(feeling, now);
            if (value >= Temperament.SHOWS_AT && value > most) {
                strongest = feeling;
                most = value;
            }
        }
        return strongest;
    }

    Mood mood(long now) {
        var strongest = strongest(now);
        return strongest == null ? Mood.NEUTRAL : strongest.mood;
    }

    void age(long ticks) {
        values.replaceAll((feeling, value) -> value.aged(ticks));
    }

    void clear() {
        values.clear();
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        values.forEach((feeling, value) -> tag.put(feeling.name(), value.save()));
        return tag;
    }

    static Feelings load(CompoundTag tag) {
        var feelings = new Feelings();
        for (Feeling feeling : Feeling.values()) if (tag.contains(feeling.name())) feelings.values.put(feeling, Fading.load(tag.getCompound(feeling.name())));
        return feelings;
    }
}

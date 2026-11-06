package com.frozendawn.miller;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * What she saw that only the log hears about, held with its time until the next entry, and when she last saw each
 * person. Nothing here reaches her feelings, her impressions or her voice. A return is only a return if she saw them
 * stop, so one who has stopped stays marked across entries until she sees them again.
 */
final class Marks {
    private static final class Person {
        final Map<Mark, Long> held = new EnumMap<>(Mark.class);
        int backs;
        /** Null until she has seen them. */
        Long seenAt;

        boolean stopped() {
            Long stopped = held.get(Mark.STOPPED), back = held.get(Mark.BACK);
            return stopped != null && (back == null || back <= stopped);
        }
    }

    /** Longest unseen first, as {@link ImpressionStore} holds them, and bounded the same. */
    private final Map<UUID, Person> people = new LinkedHashMap<>();

    private Person person(UUID who) {
        var person = people.remove(who);
        if (person == null) {
            person = new Person();
            if (people.size() == ImpressionStore.MAX_PLAYERS) people.remove(people.keySet().iterator().next());
        }
        people.put(who, person);
        return person;
    }

    void mark(UUID who, Mark mark, long now) {
        if (mark == Mark.BACK) {
            var person = people.get(who);
            if (person == null || !person.stopped()) return;
            person.backs++;
        }
        person(who).held.put(mark, now);
    }

    void seen(UUID who, long now) {
        person(who).seenAt = now;
    }

    /** What is held about them. A talk after the fight clears it: they said something. */
    Set<Mark> of(UUID who, long lastTalk) {
        var person = people.get(who);
        Set<Mark> marks = EnumSet.noneOf(Mark.class);
        if (person == null) return marks;
        marks.addAll(person.held.keySet());
        if (marks.contains(Mark.FIGHT) && lastTalk > person.held.get(Mark.FIGHT)) marks.remove(Mark.FIGHT);
        return marks;
    }

    int backs(UUID who) {
        var person = people.get(who);
        return person == null ? 0 : person.backs;
    }

    /** How long since she last saw them, or 0 if she never has. */
    long unseen(UUID who, long now) {
        var person = people.get(who);
        return person == null || person.seenAt == null ? 0 : now - person.seenAt;
    }

    /** The entry took them. Someone she saw stop and has not seen since stays stopped. */
    void written() {
        for (var person : people.values()) {
            Long stopped = person.stopped() ? person.held.get(Mark.STOPPED) : null;
            person.held.clear();
            if (stopped != null) person.held.put(Mark.STOPPED, stopped);
        }
    }

    void age(long ticks) {
        for (var person : people.values()) {
            person.held.replaceAll((mark, time) -> time - ticks);
            if (person.seenAt != null) person.seenAt -= ticks;
        }
    }

    void clear() {
        people.clear();
    }

    CompoundTag save() {
        var list = new ListTag();
        people.forEach((who, person) -> {
            var tag = new CompoundTag();
            tag.putUUID("who", who);
            var held = new CompoundTag();
            person.held.forEach((mark, time) -> held.putLong(mark.name(), time));
            tag.put("held", held);
            tag.putInt("backs", person.backs);
            if (person.seenAt != null) tag.putLong("seenAt", person.seenAt);
            list.add(tag);
        });
        var tag = new CompoundTag();
        tag.put("people", list);
        return tag;
    }

    static Marks load(CompoundTag tag) {
        var marks = new Marks();
        for (Tag entry : tag.getList("people", Tag.TAG_COMPOUND)) {
            var saved = (CompoundTag) entry;
            if (!saved.hasUUID("who")) continue;
            var person = marks.person(saved.getUUID("who"));
            var held = saved.getCompound("held");
            for (Mark mark : Mark.values()) if (held.contains(mark.name(), Tag.TAG_LONG)) person.held.put(mark, held.getLong(mark.name()));
            person.backs = Math.max(0, saved.getInt("backs"));
            if (saved.contains("seenAt", Tag.TAG_LONG)) person.seenAt = saved.getLong("seenAt");
        }
        return marks;
    }
}

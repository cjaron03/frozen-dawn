package com.frozendawn.maeve;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** §9.12a: server-wide concurrency, deterministic eviction, no spendable resource. */
final class AttentionManager {
    static final long MIN_DWELL = 100;
    static final int MAX_EVENTS = 16;
    enum Kind { RECONNAISSANCE, PASSIVE_TRACKING, ACTIVE_COMMITMENT, SIEGE }
    record Key(Kind kind, UUID subject) { }
    record Slot(Key key, long admittedAt) { }
    record Event(long time, String action, Key concern, Key replacement) { }
    record Result(boolean admitted, String reason) { }
    private final Map<Key, Slot> slots = new LinkedHashMap<>();
    private final ArrayDeque<Event> events = new ArrayDeque<>();
    private final Map<Key, String> deferred = new LinkedHashMap<>();
    private int capacity = 3;

    static int capacity(String preset) {
        return "cinematic".equalsIgnoreCase(preset) ? 2 : "brutal".equalsIgnoreCase(preset) ? 5 : 3;
    }

    Result request(Key key, long now, Consumer<Key> visibleEviction) {
        if (slots.containsKey(key)) return new Result(true, "ALREADY_FOCUSED");
        if (slots.size() > capacity) return defer(key, now, "TIER_CHANGE_DRAINING");
        if (slots.size() >= capacity) {
            Slot victim = victim(now);
            if (victim == null) return defer(key, now, "MINIMUM_DWELL");
            // The executor must be released before its slot can be reused. Failure leaves occupancy intact.
            visibleEviction.accept(victim.key());
            slots.remove(victim.key());
            event(new Event(now, "EVICTED", victim.key(), key));
        }
        slots.put(key, new Slot(key, now));
        deferred.remove(key);
        event(new Event(now, "ADMITTED", key, null));
        return new Result(true, "ADMITTED");
    }

    void resize(int capacity, long now, Consumer<Key> visibleEviction) {
        this.capacity = Math.clamp(capacity, 2, 5);
        while (slots.size() > this.capacity) {
            Slot victim = victim(now);
            if (victim == null) break;
            visibleEviction.accept(victim.key()); slots.remove(victim.key());
            event(new Event(now, "TIER_REDUCED", victim.key(), null));
        }
    }

    private Slot victim(long now) {
        return slots.values().stream()
                .filter(s -> now - s.admittedAt() >= MIN_DWELL)
                .min(Comparator.comparingInt((Slot s) -> s.key().kind().ordinal())
                        .thenComparingLong(Slot::admittedAt).thenComparing(s -> s.key().subject())).orElse(null);
    }

    void release(Key key, long now) {
        if (slots.remove(key) != null) event(new Event(now, "COMPLETED", key, null));
    }

    /** One executor changing tasks does not manufacture a second concern or reset its dwell. */
    boolean replace(Key previous, Key next, long now) {
        Slot slot = slots.remove(previous);
        if (slot == null) return false;
        slots.put(next, new Slot(next, slot.admittedAt()));
        event(new Event(now, "PROMOTED", previous, next));
        return true;
    }

    boolean contains(Key key) { return slots.containsKey(key); }
    int capacity() { return capacity; }
    List<Slot> slots() { return List.copyOf(slots.values()); }
    List<Event> events() { return List.copyOf(events); }
    void clear() { slots.clear(); events.clear(); deferred.clear(); }
    private Result defer(Key key, long now, String reason) {
        if (deferred.size() >= MAX_EVENTS && !deferred.containsKey(key)) deferred.remove(deferred.keySet().iterator().next());
        if (!reason.equals(deferred.put(key, reason))) event(new Event(now, "DEFERRED_" + reason, key, null));
        return new Result(false, reason);
    }
    private void event(Event event) {
        if (events.size() == MAX_EVENTS) events.removeFirst();
        events.addLast(event);
    }
}
